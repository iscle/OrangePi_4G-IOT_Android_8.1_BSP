/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package android.server.cts;

import com.android.ddmlib.IShellOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;

import java.awt.Rectangle;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.lang.System;
import junit.framework.Assert;

import static android.server.cts.StateLogger.logE;

// Parses a trace of surface commands from the WM (in real time)
// and dispenses them via the SurfaceObserver interface.
//
// Data enters through addOutput
public class SurfaceTraceReceiver implements IShellOutputReceiver {
    final SurfaceObserver mObserver;

    private State mState = State.CMD;
    private String mCurrentWindowName = null;
    private int mArgPosition = 0;
    private float[] mTmpFloats = new float[10];
    private int[] mTmpInts = new int[10];
    private Rectangle.Float mTmpRect = new Rectangle.Float();
    private byte[] mUnprocessedBytes = new byte[16384];
    private byte[] mFullData = new byte[32768];
    private int mUnprocessedBytesLength;

    private boolean mCancelled = false;

    interface SurfaceObserver {
        default void setAlpha(String windowName, float alpha) {}
        default void setLayer(String windowName, int layer) {}
        default void setPosition(String windowName, float x, float y) {}
        default void setSize(String widnowName, int width, int height) {}
        default void setLayerStack(String windowName, int layerStack) {}
        default void setMatrix(String windowName, float dsdx, float dtdx, float dsdy, float dtdy) {}
        default void setCrop(String windowName, Rectangle.Float crop) {}
        default void setFinalCrop(String windowName, Rectangle.Float finalCrop) {}
        default void hide(String windowName) {}
        default void show(String windowName) {}
        default void setGeometryAppliesWithResize(String windowName) {}
        default void openTransaction() {}
        default void closeTransaction() {}
    };

    enum State {
        CMD,
        SET_ALPHA,
        SET_LAYER,
        SET_POSITION,
        SET_SIZE,
        SET_CROP,
        SET_FINAL_CROP,
        SET_LAYER_STACK,
        SET_MATRIX,
        HIDE,
        SHOW,
        GEOMETRY_APPLIES_WITH_RESIZE
    };

    SurfaceTraceReceiver(SurfaceObserver observer) {
        mObserver = observer;
    }

    // Reset state and prepare to accept a new command.
    void nextCmd(DataInputStream d) {
        mState = State.CMD;
        mCurrentWindowName = null;
        mArgPosition = 0;

        try {
            // Consume the sigil
            d.readByte();
            d.readByte();
            d.readByte();
            d.readByte();
        } catch (Exception e) {
            logE("Exception consuming sigil: " + e);
        }
    }

    // When the command parsing functions below are called, the window name
    // will already be parsed. The responsibility of these functions
    // is to parse other arguments 1 by 1 and accumlate them until the appropriate number
    // is reached. At that point the parser should emit an event to the observer and
    // call nextCmd
    void parseAlpha(DataInputStream d) throws IOException {
        float alpha = d.readFloat();
        mObserver.setAlpha(mCurrentWindowName, alpha);
        nextCmd(d);
    }

    void parseLayer(DataInputStream d) throws IOException {
        int layer = d.readInt();
        mObserver.setLayer(mCurrentWindowName, layer);
        nextCmd(d);
    }

    void parsePosition(DataInputStream d) throws IOException {
        mTmpFloats[mArgPosition] = d.readFloat();
        mArgPosition++;
        if (mArgPosition == 2)  {
            mObserver.setPosition(mCurrentWindowName, mTmpFloats[0], mTmpFloats[1]);
            nextCmd(d);
        }
    }

    void parseSize(DataInputStream d) throws IOException {
        mTmpInts[mArgPosition] = d.readInt();
        mArgPosition++;
        if (mArgPosition == 2) {
            mObserver.setSize(mCurrentWindowName, mTmpInts[0], mTmpInts[1]);
            nextCmd(d);
        }
    }

    // Careful Android rectangle rep is top-left-right-bottom awt is top-left-width-height
    void parseCrop(DataInputStream d) throws IOException {
        mTmpFloats[mArgPosition] = d.readFloat();
        mArgPosition++;
        if (mArgPosition == 4) {
            mTmpRect.setRect(mTmpFloats[0], mTmpFloats[1], mTmpFloats[2]-mTmpFloats[0],
                    mTmpFloats[3]-mTmpFloats[1]);
            mObserver.setCrop(mCurrentWindowName, mTmpRect);
            nextCmd(d);
        }
    }

    void parseFinalCrop(DataInputStream d) throws IOException {
        mTmpFloats[mArgPosition] = d.readInt();
        mArgPosition++;
        if (mArgPosition == 4) {
            mTmpRect.setRect(mTmpFloats[0], mTmpFloats[1], mTmpFloats[2]-mTmpFloats[0],
                    mTmpFloats[3]-mTmpFloats[1]);
            mObserver.setFinalCrop(mCurrentWindowName, mTmpRect);
            nextCmd(d);
        }
    }

    void parseLayerStack(DataInputStream d) throws IOException {
        int layerStack = d.readInt();
        mObserver.setLayerStack(mCurrentWindowName, layerStack);
        nextCmd(d);
    }

    void parseSetMatrix(DataInputStream d) throws IOException {
        mTmpFloats[mArgPosition] = d.readFloat();
        mArgPosition++;
        if (mArgPosition == 4) {
            mObserver.setMatrix(mCurrentWindowName, mTmpFloats[0],
                    mTmpFloats[1], mTmpFloats[2], mTmpFloats[3]);
            nextCmd(d);
        }
    }

    void parseHide(DataInputStream d) throws IOException {
        mObserver.hide(mCurrentWindowName);
        nextCmd(d);
    }

    void parseShow(DataInputStream d) throws IOException {
        mObserver.show(mCurrentWindowName);
        nextCmd(d);
    }

    void parseGeometryAppliesWithResize(DataInputStream d) throws IOException {
        mObserver.setGeometryAppliesWithResize(mCurrentWindowName);
        nextCmd(d);
    }

    public int indexAfterLastSigil(byte[] data, int offset, int length) {
        int idx = offset + length - 1;
        int sigilsNeeded = 4;
        byte sigil = (byte)0xfc;
        while (idx > offset) {
            if (data[idx] == sigil) {
                sigilsNeeded--;
                if (sigilsNeeded == 0) {
                    return idx+4;
                }
            } else {
                sigilsNeeded = 4;
            }
            idx--;
        }
        return idx; // idx == offset at this point
    }

    // The tricky bit here is ADB may break up our words, and not send us complete messages,
    // or even complete integers! To ensure we process the data in appropciate chunks,
    // We look for a sigil (0xfcfcfcfc) and only process data when it ends in as igil.
    // Otherwise we save it and wait to receive a sigil, then process the merged data.
    public void addOutput(byte[] data, int offset, int length) {
        byte[] combinedData = data;

        // First we have to merge any unprocessed bytes from the last call in to
        // a combined array.
        if (mUnprocessedBytesLength > 0) {
            System.arraycopy(mUnprocessedBytes, 0, mFullData, 0, mUnprocessedBytesLength);
            System.arraycopy(data, offset, mFullData, mUnprocessedBytesLength, length);
            combinedData = mFullData;
            length = mUnprocessedBytesLength + length;
            offset = 0;
            mUnprocessedBytesLength = 0;
        }

        // Now we find the last sigil in our combined array. Everything before this index is
        // a properly terminated message ready to be parsed.
        int completedIndex = indexAfterLastSigil(combinedData, offset, length);
        // If there are any bytes left after the last sigil, save them for next time.
        if (completedIndex != length + offset) {
            mUnprocessedBytesLength = (length+offset)-(completedIndex);
            System.arraycopy(combinedData, completedIndex,
                    mUnprocessedBytes, 0, mUnprocessedBytesLength);
        }
        //  If there was no sigil, we have nothing to process yet.
        if (completedIndex <= offset) {
            return;
        }
        ByteArrayInputStream b = new ByteArrayInputStream(combinedData, offset, completedIndex - offset);
        DataInputStream d = new DataInputStream(b);

        // We may not receive an entire message at once (for example we may receive
        // a command without its arguments), so we track our current state, over multiple
        // addOutput calls. When we are in State.CMD it means we next expect a new command.
        // If we are not expecting a command, then all commands with arguments, begin with
        // a window name. Once we have the window name, individual parseAlpha,
        // parseLayer, etc...statements will parse command arguments one at a time. Once
        // the appropriate number of arguments is collected the observer will be invoked
        // and the state reset. For commands which have no arguments (e.g. open/close transaction),
        // parseCmd can emit the observer event and call nextCmd() right away.
        try {
            while (b.available() > 0) {
                if (mState != State.CMD && mCurrentWindowName == null) {
                    mCurrentWindowName = d.readUTF();
                    if (b.available() == 0) {
                        return;
                    }
                }
                switch (mState) {
                case CMD: {
                    String cmd = d.readUTF();
                    parseCmd(d, cmd);
                    break;
                }
                case SET_ALPHA: {
                    parseAlpha(d);
                    break;
                }
                case SET_LAYER: {
                    parseLayer(d);
                    break;
                }
                case SET_POSITION: {
                    parsePosition(d);
                    break;
                }
                case SET_SIZE: {
                    parseSize(d);
                    break;
                }
                case SET_CROP: {
                    parseCrop(d);
                    break;
                }
                case SET_FINAL_CROP: {
                    parseFinalCrop(d);
                    break;
                }
                case SET_LAYER_STACK: {
                    parseLayerStack(d);
                    break;
                }
                case SET_MATRIX: {
                    parseSetMatrix(d);
                    break;
                }
                case HIDE: {
                    parseHide(d);
                    break;
                }
                case SHOW: {
                    parseShow(d);
                    break;
                }
                case GEOMETRY_APPLIES_WITH_RESIZE: {
                    parseGeometryAppliesWithResize(d);
                    break;
                }
                }
            }
        } catch (Exception e) {
            logE("Error in surface trace receiver: " + e.toString());
        }
    }

    void parseCmd(DataInputStream d, String cmd) {
        switch (cmd) {
        case "Alpha":
            mState = State.SET_ALPHA;
            break;
        case "Layer":
            mState = State.SET_LAYER;
            break;
        case "Position":
            mState = State.SET_POSITION;
            break;
        case "Size":
            mState = State.SET_SIZE;
            break;
        case "Crop":
            mState = State.SET_CROP;
            break;
        case "FinalCrop":
            mState = State.SET_FINAL_CROP;
            break;
        case "LayerStack":
            mState = State.SET_LAYER_STACK;
            break;
        case "Matrix":
            mState = State.SET_MATRIX;
            break;
        case "Hide":
            mState = State.HIDE;
            break;
        case "Show":
            mState = State.SHOW;
            break;
        case "GeometryAppliesWithResize":
            mState = State.GEOMETRY_APPLIES_WITH_RESIZE;
            break;
        case "OpenTransaction":
            mObserver.openTransaction();
            nextCmd(d);
            break;
        case "CloseTransaction":
            mObserver.closeTransaction();
            nextCmd(d);
            break;
        default:
            Assert.fail("Unexpected surface command: " + cmd);
            break;
        }
    }

    @Override
    public void flush() {
    }

    void cancel() {
        mCancelled = true;
    }

    @Override
    public boolean isCancelled() {
        return mCancelled;
    }
}
