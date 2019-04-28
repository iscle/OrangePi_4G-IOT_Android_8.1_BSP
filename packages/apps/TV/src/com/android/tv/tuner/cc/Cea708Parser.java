/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License.
 */

package com.android.tv.tuner.cc;

import android.os.SystemClock;
import android.support.annotation.IntDef;
import android.util.Log;
import android.util.SparseIntArray;

import com.android.tv.tuner.data.Cea708Data;
import com.android.tv.tuner.data.Cea708Data.CaptionColor;
import com.android.tv.tuner.data.Cea708Data.CaptionEvent;
import com.android.tv.tuner.data.Cea708Data.CaptionPenAttr;
import com.android.tv.tuner.data.Cea708Data.CaptionPenColor;
import com.android.tv.tuner.data.Cea708Data.CaptionPenLocation;
import com.android.tv.tuner.data.Cea708Data.CaptionWindow;
import com.android.tv.tuner.data.Cea708Data.CaptionWindowAttr;
import com.android.tv.tuner.data.Cea708Data.CcPacket;
import com.android.tv.tuner.util.ByteArrayBuffer;

import java.io.UnsupportedEncodingException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.TreeSet;

/**
 * A class for parsing CEA-708, which is the standard for closed captioning for ATSC DTV.
 *
 * <p>ATSC DTV closed caption data are carried on picture user data of video streams.
 * This class starts to parse from picture user data payload, so extraction process of user_data
 * from video streams is up to outside of this code.
 *
 * <p>There are 4 steps to decode user_data to provide closed caption services.
 *
 * <h3>Step 1. user_data -&gt; CcPacket ({@link #parseClosedCaption} method)</h3>
 *
 * <p>First, user_data consists of cc_data packets, which are 3-byte segments. Here, CcPacket is a
 * collection of cc_data packets in a frame along with same presentation timestamp. Because cc_data
 * packets must be reassembled in the frame display order, CcPackets are reordered.
 *
 * <h3>Step 2. CcPacket -&gt; DTVCC packet ({@link #parseCcPacket} method)</h3>
 *
 * <p>Each cc_data packet has a one byte for declaring a type of itself and data validity, and the
 * subsequent two bytes for input data of a DTVCC packet. There are 4 types for cc_data packet.
 * We're interested in DTVCC_PACKET_START(type 3) and DTVCC_PACKET_DATA(type 2). Each DTVCC packet
 * begins with DTVCC_PACKET_START(type 3) and the following cc_data packets which has
 * DTVCC_PACKET_DATA(type 2) are appended into the DTVCC packet being assembled.
 *
 * <h3>Step 3. DTVCC packet -&gt; Service Blocks ({@link #parseDtvCcPacket} method)</h3>
 *
 * <p>A DTVCC packet consists of multiple service blocks. Each service block represents a caption
 * track and has a service number, which ranges from 1 to 63, that denotes caption track identity.
 * In here, we listen at most one chosen caption track by {@link #mListenServiceNumber}.
 * Otherwise, just skip the other service blocks.
 *
 * <h3>Step 4. Interpreting Service Block Data ({@link #parseServiceBlockData}, {@code parseXX},
 * and {@link #parseExt1} methods)</h3>
 *
 * <p>Service block data is actual caption stream. it looks similar to telnet. It uses most parts of
 * ASCII table and consists of specially defined commands and some ASCII control codes which work
 * in a behavior slightly different from their original purpose. ASCII control codes and caption
 * commands are explicit instructions that control the state of a closed caption service and the
 * other ASCII and text codes are implicit instructions that send their characters to buffer.
 *
 * <p>There are 4 main code groups and 4 extended code groups. Both the range of code groups are the
 * same as the range of a byte.
 *
 * <p>4 main code groups: C0, C1, G0, G1
 * <br>4 extended code groups: C2, C3, G2, G3
 *
 * <p>Each code group has its own handle method. For example, {@link #parseC0} handles C0 code group
 * and so on. And {@link #parseServiceBlockData} method maps a stream on the main code groups while
 * {@link #parseExt1} method maps on the extended code groups.
 *
 * <p>The main code groups:
 * <ul>
 * <li>C0 - contains modified ASCII control codes. It is not intended by CEA-708 but Korea TTA
 *      standard for ATSC CC uses P16 character heavily, which is unclear entity in CEA-708 doc,
 *      even for the alphanumeric characters instead of ASCII characters.</li>
 * <li>C1 - contains the caption commands. There are 3 categories of a caption command.</li>
 * <ul>
 * <li>Window commands: The window commands control a caption window which is addressable area being
 *                  with in the Safe title area. (CWX, CLW, DSW, HDW, TGW, DLW, SWA, DFX)</li>
 * <li>Pen commands: Th pen commands control text style and location. (SPA, SPC, SPL)</li>
 * <li>Job commands: The job commands make a delay and recover from the delay. (DLY, DLC, RST)</li>
 * </ul>
 * <li>G0 - same as printable ASCII character set except music note character.</li>
 * <li>G1 - same as ISO 8859-1 Latin 1 character set.</li>
 * </ul>
 * <p>Most of the extended code groups are being skipped.
 *
 */
public class Cea708Parser {
    private static final String TAG = "Cea708Parser";
    private static final boolean DEBUG = false;

    // According to CEA-708B, the maximum value of closed caption bandwidth is 9600bps.
    private static final int MAX_ALLOCATED_SIZE = 9600 / 8;
    private static final String MUSIC_NOTE_CHAR = new String(
            "\u266B".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

    // The following values are denoting the type of closed caption data.
    // See CEA-708B section 4.4.1.
    private static final int CC_TYPE_DTVCC_PACKET_START = 3;
    private static final int CC_TYPE_DTVCC_PACKET_DATA = 2;

    // The following values are defined in CEA-708B Figure 4 and 6.
    private static final int DTVCC_MAX_PACKET_SIZE = 64;
    private static final int DTVCC_PACKET_SIZE_SCALE_FACTOR = 2;
    private static final int DTVCC_EXTENDED_SERVICE_NUMBER_POINT = 7;

    // The following values are for seeking closed caption tracks.
    private static final int DISCOVERY_PERIOD_MS = 10000; // 10 sec
    private static final int DISCOVERY_NUM_BYTES_THRESHOLD = 10; // 10 bytes
    private static final int DISCOVERY_CC_SERVICE_NUMBER_START = 1; // CC1
    private static final int DISCOVERY_CC_SERVICE_NUMBER_END = 4; // CC4

    private final ByteArrayBuffer mDtvCcPacket = new ByteArrayBuffer(MAX_ALLOCATED_SIZE);
    private final TreeSet<CcPacket> mCcPackets = new TreeSet<>();
    private final StringBuffer mBuffer = new StringBuffer();
    private final SparseIntArray mDiscoveredNumBytes = new SparseIntArray(); // per service number
    private long mLastDiscoveryLaunchedMs = SystemClock.elapsedRealtime();
    private int mCommand = 0;
    private int mListenServiceNumber = 0;
    private boolean mDtvCcPacking = false;
    private boolean mFirstServiceNumberDiscovered;

    // Assign a dummy listener in order to avoid null checks.
    private OnCea708ParserListener mListener = new OnCea708ParserListener() {
        @Override
        public void emitEvent(CaptionEvent event) {
            // do nothing
        }

        @Override
        public void discoverServiceNumber(int serviceNumber) {
            // do nothing
        }
    };

    /**
     * {@link Cea708Parser} emits caption event of three different types.
     * {@link OnCea708ParserListener#emitEvent} is invoked with the parameter
     * {@link CaptionEvent} to pass all the results to an observer of the decoding process.
     *
     * <p>{@link CaptionEvent#type} determines the type of the result and
     * {@link CaptionEvent#obj} contains the output value of a caption event.
     * The observer must do the casting to the corresponding type.
     *
     * <ul><li>{@code CAPTION_EMIT_TYPE_BUFFER}: Passes a caption text buffer to a observer.
     * {@code obj} must be of {@link String}.</li>
     *
     * <li>{@code CAPTION_EMIT_TYPE_CONTROL}: Passes a caption character control code to a observer.
     * {@code obj} must be of {@link Character}.</li>
     *
     * <li>{@code CAPTION_EMIT_TYPE_CLEAR_COMMAND}: Passes a clear command to a observer.
     * {@code obj} must be {@code NULL}.</li></ul>
     */
    @IntDef({CAPTION_EMIT_TYPE_BUFFER, CAPTION_EMIT_TYPE_CONTROL, CAPTION_EMIT_TYPE_COMMAND_CWX,
        CAPTION_EMIT_TYPE_COMMAND_CLW, CAPTION_EMIT_TYPE_COMMAND_DSW, CAPTION_EMIT_TYPE_COMMAND_HDW,
        CAPTION_EMIT_TYPE_COMMAND_TGW, CAPTION_EMIT_TYPE_COMMAND_DLW, CAPTION_EMIT_TYPE_COMMAND_DLY,
        CAPTION_EMIT_TYPE_COMMAND_DLC, CAPTION_EMIT_TYPE_COMMAND_RST, CAPTION_EMIT_TYPE_COMMAND_SPA,
        CAPTION_EMIT_TYPE_COMMAND_SPC, CAPTION_EMIT_TYPE_COMMAND_SPL, CAPTION_EMIT_TYPE_COMMAND_SWA,
        CAPTION_EMIT_TYPE_COMMAND_DFX})
    @Retention(RetentionPolicy.SOURCE)
    public @interface CaptionEmitType {}
    public static final int CAPTION_EMIT_TYPE_BUFFER = 1;
    public static final int CAPTION_EMIT_TYPE_CONTROL = 2;
    public static final int CAPTION_EMIT_TYPE_COMMAND_CWX = 3;
    public static final int CAPTION_EMIT_TYPE_COMMAND_CLW = 4;
    public static final int CAPTION_EMIT_TYPE_COMMAND_DSW = 5;
    public static final int CAPTION_EMIT_TYPE_COMMAND_HDW = 6;
    public static final int CAPTION_EMIT_TYPE_COMMAND_TGW = 7;
    public static final int CAPTION_EMIT_TYPE_COMMAND_DLW = 8;
    public static final int CAPTION_EMIT_TYPE_COMMAND_DLY = 9;
    public static final int CAPTION_EMIT_TYPE_COMMAND_DLC = 10;
    public static final int CAPTION_EMIT_TYPE_COMMAND_RST = 11;
    public static final int CAPTION_EMIT_TYPE_COMMAND_SPA = 12;
    public static final int CAPTION_EMIT_TYPE_COMMAND_SPC = 13;
    public static final int CAPTION_EMIT_TYPE_COMMAND_SPL = 14;
    public static final int CAPTION_EMIT_TYPE_COMMAND_SWA = 15;
    public static final int CAPTION_EMIT_TYPE_COMMAND_DFX = 16;

    public interface OnCea708ParserListener {
        void emitEvent(CaptionEvent event);
        void discoverServiceNumber(int serviceNumber);
    }

    public void setListener(OnCea708ParserListener listener) {
        if (listener != null) {
            mListener = listener;
        }
    }

    public void clear() {
        mDtvCcPacket.clear();
        mCcPackets.clear();
        mBuffer.setLength(0);
        mDiscoveredNumBytes.clear();
        mCommand = 0;
        mDtvCcPacking = false;
    }

    public void setListenServiceNumber(int serviceNumber) {
        mListenServiceNumber = serviceNumber;
    }

    private void emitCaptionEvent(CaptionEvent captionEvent) {
        // Emit the existing string buffer before a new event is arrived.
        emitCaptionBuffer();
        mListener.emitEvent(captionEvent);
    }

    private void emitCaptionBuffer() {
        if (mBuffer.length() > 0) {
            mListener.emitEvent(new CaptionEvent(CAPTION_EMIT_TYPE_BUFFER, mBuffer.toString()));
            mBuffer.setLength(0);
        }
    }

    // Step 1. user_data -> CcPacket ({@link #parseClosedCaption} method)
    public void parseClosedCaption(ByteBuffer data, long framePtsUs) {
        int ccCount = data.limit() / 3;
        byte[] ccBytes = new byte[3 * ccCount];
        for (int i = 0; i < 3 * ccCount; i++) {
            ccBytes[i] = data.get(i);
        }
        CcPacket ccPacket = new CcPacket(ccBytes, ccCount, framePtsUs);
        mCcPackets.add(ccPacket);
    }

    public boolean processClosedCaptions(long framePtsUs) {
        // To get the sorted cc packets that have lower frame pts than current frame pts,
        // the following offset divides off the lower side of the packets.
        CcPacket offsetPacket = new CcPacket(new byte[0], 0, framePtsUs);
        offsetPacket = mCcPackets.lower(offsetPacket);
        boolean processed = false;
        if (offsetPacket != null) {
            while (!mCcPackets.isEmpty() && offsetPacket.compareTo(mCcPackets.first()) >= 0) {
                CcPacket packet = mCcPackets.pollFirst();
                parseCcPacket(packet);
                processed = true;
            }
        }
        return processed;
    }

    // Step 2. CcPacket -> DTVCC packet ({@link #parseCcPacket} method)
    private void parseCcPacket(CcPacket ccPacket) {
        // For the details of cc packet, see ATSC TSG-676 - Table A8.
        byte[] bytes = ccPacket.bytes;
        int pos = 0;
        for (int i = 0; i < ccPacket.ccCount; ++i) {
            boolean ccValid = (bytes[pos] & 0x04) != 0;
            int ccType = bytes[pos] & 0x03;

            // The dtvcc should be considered complete:
            // - if either ccValid is set and ccType is 3
            // - or ccValid is clear and ccType is 2 or 3.
            if (ccValid) {
                if (ccType == CC_TYPE_DTVCC_PACKET_START) {
                    if (mDtvCcPacking) {
                        parseDtvCcPacket(mDtvCcPacket.buffer(), mDtvCcPacket.length());
                        mDtvCcPacket.clear();
                    }
                    mDtvCcPacking = true;
                    mDtvCcPacket.append(bytes[pos + 1]);
                    mDtvCcPacket.append(bytes[pos + 2]);
                } else if (mDtvCcPacking && ccType == CC_TYPE_DTVCC_PACKET_DATA) {
                    mDtvCcPacket.append(bytes[pos + 1]);
                    mDtvCcPacket.append(bytes[pos + 2]);
                }
            } else {
                if ((ccType == CC_TYPE_DTVCC_PACKET_START || ccType == CC_TYPE_DTVCC_PACKET_DATA)
                        && mDtvCcPacking) {
                    mDtvCcPacking = false;
                    parseDtvCcPacket(mDtvCcPacket.buffer(), mDtvCcPacket.length());
                    mDtvCcPacket.clear();
                }
            }
            pos += 3;
        }
    }

    // Step 3. DTVCC packet -> Service Blocks ({@link #parseDtvCcPacket} method)
    private void parseDtvCcPacket(byte[] data, int limit) {
        // For the details of DTVCC packet, see CEA-708B Figure 4.
        int pos = 0;
        int packetSize = data[pos] & 0x3f;
        if (packetSize == 0) {
            packetSize = DTVCC_MAX_PACKET_SIZE;
        }
        int calculatedPacketSize = packetSize * DTVCC_PACKET_SIZE_SCALE_FACTOR;
        if (limit != calculatedPacketSize) {
            return;
        }
        ++pos;
        int len = pos + calculatedPacketSize;
        while (pos < len) {
            // For the details of Service Block, see CEA-708B Figure 5 and 6.
            int serviceNumber = (data[pos] & 0xe0) >> 5;
            int blockSize = data[pos] & 0x1f;
            ++pos;
            if (serviceNumber == DTVCC_EXTENDED_SERVICE_NUMBER_POINT) {
                serviceNumber = (data[pos] & 0x3f);
                ++pos;

                // Return if invalid service number
                if (serviceNumber < DTVCC_EXTENDED_SERVICE_NUMBER_POINT) {
                    return;
                }
            }
            if (pos + blockSize > limit) {
                return;
            }

            // Send parsed service number in order to find unveiled closed caption tracks which
            // are not specified in any ATSC PSIP sections. Since some broadcasts send empty closed
            // caption tracks, it detects the proper closed caption tracks by counting the number of
            // bytes sent with the same service number during a discovery period.
            // The viewer in most TV sets chooses between CC1, CC2, CC3, CC4 to view different
            // language captions. Therefore, only CC1, CC2, CC3, CC4 are allowed to be reported.
            if (blockSize > 0 && serviceNumber >= DISCOVERY_CC_SERVICE_NUMBER_START
                    && serviceNumber <= DISCOVERY_CC_SERVICE_NUMBER_END) {
                mDiscoveredNumBytes.put(
                        serviceNumber, blockSize + mDiscoveredNumBytes.get(serviceNumber, 0));
            }
            if (mLastDiscoveryLaunchedMs + DISCOVERY_PERIOD_MS < SystemClock.elapsedRealtime()
                    || !mFirstServiceNumberDiscovered) {
                for (int i = 0; i < mDiscoveredNumBytes.size(); ++i) {
                    int discoveredNumBytes = mDiscoveredNumBytes.valueAt(i);
                    if (discoveredNumBytes >= DISCOVERY_NUM_BYTES_THRESHOLD) {
                        int discoveredServiceNumber = mDiscoveredNumBytes.keyAt(i);
                        mListener.discoverServiceNumber(discoveredServiceNumber);
                        mFirstServiceNumberDiscovered = true;
                    }
                }
                mDiscoveredNumBytes.clear();
                mLastDiscoveryLaunchedMs = SystemClock.elapsedRealtime();
            }

            // Skip current service block if either there is no block data or the service number
            // is not same as listening service number.
            if (blockSize == 0 || serviceNumber != mListenServiceNumber) {
                pos += blockSize;
                continue;
            }

            // From this point, starts to read DTVCC coding layer.
            // First, identify code groups, which is defined in CEA-708B Section 7.1.
            int blockLimit = pos + blockSize;
            while (pos < blockLimit) {
                pos = parseServiceBlockData(data, pos);
            }

            // Emit the buffer after reading codes.
            emitCaptionBuffer();
            pos = blockLimit;
        }
    }

    // Step 4. Main code groups
    private int parseServiceBlockData(byte[] data, int pos) {
        // For the details of the ranges of DTVCC code groups, see CEA-708B Table 6.
        mCommand = data[pos] & 0xff;
        ++pos;
        if (mCommand == Cea708Data.CODE_C0_EXT1) {
            pos = parseExt1(data, pos);
        } else if (mCommand >= Cea708Data.CODE_C0_RANGE_START
                && mCommand <= Cea708Data.CODE_C0_RANGE_END) {
            pos = parseC0(data, pos);
        } else if (mCommand >= Cea708Data.CODE_C1_RANGE_START
                && mCommand <= Cea708Data.CODE_C1_RANGE_END) {
            pos = parseC1(data, pos);
        } else if (mCommand >= Cea708Data.CODE_G0_RANGE_START
                && mCommand <= Cea708Data.CODE_G0_RANGE_END) {
            pos = parseG0(data, pos);
        } else if (mCommand >= Cea708Data.CODE_G1_RANGE_START
                && mCommand <= Cea708Data.CODE_G1_RANGE_END) {
            pos = parseG1(data, pos);
        }
        return pos;
    }

    private int parseC0(byte[] data, int pos) {
        // For the details of C0 code group, see CEA-708B Section 7.4.1.
        // CL Group: C0 Subset of ASCII Control codes
        if (mCommand >= Cea708Data.CODE_C0_SKIP2_RANGE_START
                && mCommand <= Cea708Data.CODE_C0_SKIP2_RANGE_END) {
            if (mCommand == Cea708Data.CODE_C0_P16) {
                // TODO : P16 escapes next two bytes for the large character maps.(no standard rule)
                // TODO : For korea broadcasting, express whole letters by using this.
                try {
                    if (data[pos] == 0) {
                        mBuffer.append((char) data[pos + 1]);
                    } else {
                        String value = new String(
                                Arrays.copyOfRange(data, pos, pos + 2),
                                "EUC-KR");
                        mBuffer.append(value);
                    }
                } catch (UnsupportedEncodingException e) {
                    Log.e(TAG, "P16 Code - Could not find supported encoding", e);
                }
            }
            pos += 2;
        } else if (mCommand >= Cea708Data.CODE_C0_SKIP1_RANGE_START
                && mCommand <= Cea708Data.CODE_C0_SKIP1_RANGE_END) {
            ++pos;
        } else {
            // NUL, BS, FF, CR interpreted as they are in ASCII control codes.
            // HCR moves the pen location to th beginning of the current line and deletes contents.
            // FF clears the screen and moves the pen location to (0,0).
            // ETX is the NULL command which is used to flush text to the current window when no
            // other command is pending.
            switch (mCommand) {
                case Cea708Data.CODE_C0_NUL:
                    break;
                case Cea708Data.CODE_C0_ETX:
                    emitCaptionEvent(new CaptionEvent(CAPTION_EMIT_TYPE_CONTROL, (char) mCommand));
                    break;
                case Cea708Data.CODE_C0_BS:
                    emitCaptionEvent(new CaptionEvent(CAPTION_EMIT_TYPE_CONTROL, (char) mCommand));
                    break;
                case Cea708Data.CODE_C0_FF:
                    emitCaptionEvent(new CaptionEvent(CAPTION_EMIT_TYPE_CONTROL, (char) mCommand));
                    break;
                case Cea708Data.CODE_C0_CR:
                    mBuffer.append('\n');
                    break;
                case Cea708Data.CODE_C0_HCR:
                    emitCaptionEvent(new CaptionEvent(CAPTION_EMIT_TYPE_CONTROL, (char) mCommand));
                    break;
                default:
                    break;
            }
        }
        return pos;
    }

    private int parseC1(byte[] data, int pos) {
        // For the details of C1 code group, see CEA-708B Section 8.10.
        // CR Group: C1 Caption Control Codes
        switch (mCommand) {
            case Cea708Data.CODE_C1_CW0:
            case Cea708Data.CODE_C1_CW1:
            case Cea708Data.CODE_C1_CW2:
            case Cea708Data.CODE_C1_CW3:
            case Cea708Data.CODE_C1_CW4:
            case Cea708Data.CODE_C1_CW5:
            case Cea708Data.CODE_C1_CW6:
            case Cea708Data.CODE_C1_CW7: {
                // SetCurrentWindow0-7
                int windowId = mCommand - Cea708Data.CODE_C1_CW0;
                emitCaptionEvent(new CaptionEvent(CAPTION_EMIT_TYPE_COMMAND_CWX, windowId));
                if (DEBUG) {
                    Log.d(TAG, String.format("CaptionCommand CWX windowId: %d", windowId));
                }
                break;
            }

            case Cea708Data.CODE_C1_CLW: {
                // ClearWindows
                int windowBitmap = data[pos] & 0xff;
                ++pos;
                emitCaptionEvent(new CaptionEvent(CAPTION_EMIT_TYPE_COMMAND_CLW, windowBitmap));
                if (DEBUG) {
                    Log.d(TAG, String.format("CaptionCommand CLW windowBitmap: %d", windowBitmap));
                }
                break;
            }

            case Cea708Data.CODE_C1_DSW: {
                // DisplayWindows
                int windowBitmap = data[pos] & 0xff;
                ++pos;
                emitCaptionEvent(new CaptionEvent(CAPTION_EMIT_TYPE_COMMAND_DSW, windowBitmap));
                if (DEBUG) {
                    Log.d(TAG, String.format("CaptionCommand DSW windowBitmap: %d", windowBitmap));
                }
                break;
            }

            case Cea708Data.CODE_C1_HDW: {
                // HideWindows
                int windowBitmap = data[pos] & 0xff;
                ++pos;
                emitCaptionEvent(new CaptionEvent(CAPTION_EMIT_TYPE_COMMAND_HDW, windowBitmap));
                if (DEBUG) {
                    Log.d(TAG, String.format("CaptionCommand HDW windowBitmap: %d", windowBitmap));
                }
                break;
            }

            case Cea708Data.CODE_C1_TGW: {
                // ToggleWindows
                int windowBitmap = data[pos] & 0xff;
                ++pos;
                emitCaptionEvent(new CaptionEvent(CAPTION_EMIT_TYPE_COMMAND_TGW, windowBitmap));
                if (DEBUG) {
                    Log.d(TAG, String.format("CaptionCommand TGW windowBitmap: %d", windowBitmap));
                }
                break;
            }

            case Cea708Data.CODE_C1_DLW: {
                // DeleteWindows
                int windowBitmap = data[pos] & 0xff;
                ++pos;
                emitCaptionEvent(new CaptionEvent(CAPTION_EMIT_TYPE_COMMAND_DLW, windowBitmap));
                if (DEBUG) {
                    Log.d(TAG, String.format("CaptionCommand DLW windowBitmap: %d", windowBitmap));
                }
                break;
            }

            case Cea708Data.CODE_C1_DLY: {
                // Delay
                int tenthsOfSeconds = data[pos] & 0xff;
                ++pos;
                emitCaptionEvent(new CaptionEvent(CAPTION_EMIT_TYPE_COMMAND_DLY, tenthsOfSeconds));
                if (DEBUG) {
                    Log.d(TAG, String.format("CaptionCommand DLY %d tenths of seconds",
                            tenthsOfSeconds));
                }
                break;
            }
            case Cea708Data.CODE_C1_DLC: {
                // DelayCancel
                emitCaptionEvent(new CaptionEvent(CAPTION_EMIT_TYPE_COMMAND_DLC, null));
                if (DEBUG) {
                    Log.d(TAG, "CaptionCommand DLC");
                }
                break;
            }

            case Cea708Data.CODE_C1_RST: {
                // Reset
                emitCaptionEvent(new CaptionEvent(CAPTION_EMIT_TYPE_COMMAND_RST, null));
                if (DEBUG) {
                    Log.d(TAG, "CaptionCommand RST");
                }
                break;
            }

            case Cea708Data.CODE_C1_SPA: {
                // SetPenAttributes
                int textTag = (data[pos] & 0xf0) >> 4;
                int penSize = data[pos] & 0x03;
                int penOffset = (data[pos] & 0x0c) >> 2;
                boolean italic = (data[pos + 1] & 0x80) != 0;
                boolean underline = (data[pos + 1] & 0x40) != 0;
                int edgeType = (data[pos + 1] & 0x38) >> 3;
                int fontTag = data[pos + 1] & 0x7;
                pos += 2;
                emitCaptionEvent(new CaptionEvent(CAPTION_EMIT_TYPE_COMMAND_SPA,
                        new CaptionPenAttr(penSize, penOffset, textTag, fontTag, edgeType,
                                underline, italic)));
                if (DEBUG) {
                    Log.d(TAG, String.format(
                            "CaptionCommand SPA penSize: %d, penOffset: %d, textTag: %d, "
                                    + "fontTag: %d, edgeType: %d, underline: %s, italic: %s",
                            penSize, penOffset, textTag, fontTag, edgeType, underline, italic));
                }
                break;
            }

            case Cea708Data.CODE_C1_SPC: {
                // SetPenColor
                int opacity = (data[pos] & 0xc0) >> 6;
                int red = (data[pos] & 0x30) >> 4;
                int green = (data[pos] & 0x0c) >> 2;
                int blue = data[pos] & 0x03;
                CaptionColor foregroundColor = new CaptionColor(opacity, red, green, blue);
                ++pos;
                opacity = (data[pos] & 0xc0) >> 6;
                red = (data[pos] & 0x30) >> 4;
                green = (data[pos] & 0x0c) >> 2;
                blue = data[pos] & 0x03;
                CaptionColor backgroundColor = new CaptionColor(opacity, red, green, blue);
                ++pos;
                red = (data[pos] & 0x30) >> 4;
                green = (data[pos] & 0x0c) >> 2;
                blue = data[pos] & 0x03;
                CaptionColor edgeColor = new CaptionColor(
                        CaptionColor.OPACITY_SOLID, red, green, blue);
                ++pos;
                emitCaptionEvent(new CaptionEvent(CAPTION_EMIT_TYPE_COMMAND_SPC,
                        new CaptionPenColor(foregroundColor, backgroundColor, edgeColor)));
                if (DEBUG) {
                    Log.d(TAG, String.format(
                            "CaptionCommand SPC foregroundColor %s backgroundColor %s edgeColor %s",
                            foregroundColor, backgroundColor, edgeColor));
                }
                break;
            }

            case Cea708Data.CODE_C1_SPL: {
                // SetPenLocation
                // column is normally 0-31 for 4:3 formats, and 0-41 for 16:9 formats
                int row = data[pos] & 0x0f;
                int column = data[pos + 1] & 0x3f;
                pos += 2;
                emitCaptionEvent(new CaptionEvent(CAPTION_EMIT_TYPE_COMMAND_SPL,
                        new CaptionPenLocation(row, column)));
                if (DEBUG) {
                    Log.d(TAG, String.format("CaptionCommand SPL row: %d, column: %d",
                            row, column));
                }
                break;
            }

            case Cea708Data.CODE_C1_SWA: {
                // SetWindowAttributes
                int opacity = (data[pos] & 0xc0) >> 6;
                int red = (data[pos] & 0x30) >> 4;
                int green = (data[pos] & 0x0c) >> 2;
                int blue = data[pos] & 0x03;
                CaptionColor fillColor = new CaptionColor(opacity, red, green, blue);
                int borderType = (data[pos + 1] & 0xc0) >> 6 | (data[pos + 2] & 0x80) >> 5;
                red = (data[pos + 1] & 0x30) >> 4;
                green = (data[pos + 1] & 0x0c) >> 2;
                blue = data[pos + 1] & 0x03;
                CaptionColor borderColor = new CaptionColor(
                        CaptionColor.OPACITY_SOLID, red, green, blue);
                boolean wordWrap = (data[pos + 2] & 0x40) != 0;
                int printDirection = (data[pos + 2] & 0x30) >> 4;
                int scrollDirection = (data[pos + 2] & 0x0c) >> 2;
                int justify = (data[pos + 2] & 0x03);
                int effectSpeed = (data[pos + 3] & 0xf0) >> 4;
                int effectDirection = (data[pos + 3] & 0x0c) >> 2;
                int displayEffect = data[pos + 3] & 0x3;
                pos += 4;
                emitCaptionEvent(new CaptionEvent(CAPTION_EMIT_TYPE_COMMAND_SWA,
                        new CaptionWindowAttr(fillColor, borderColor, borderType, wordWrap,
                                printDirection, scrollDirection, justify,
                                effectDirection, effectSpeed, displayEffect)));
                if (DEBUG) {
                    Log.d(TAG, String.format(
                            "CaptionCommand SWA fillColor: %s, borderColor: %s, borderType: %d"
                                    + "wordWrap: %s, printDirection: %d, scrollDirection: %d, "
                                    + "justify: %s, effectDirection: %d, effectSpeed: %d, "
                                    + "displayEffect: %d",
                            fillColor, borderColor, borderType, wordWrap, printDirection,
                            scrollDirection, justify, effectDirection, effectSpeed, displayEffect));
                }
                break;
            }

            case Cea708Data.CODE_C1_DF0:
            case Cea708Data.CODE_C1_DF1:
            case Cea708Data.CODE_C1_DF2:
            case Cea708Data.CODE_C1_DF3:
            case Cea708Data.CODE_C1_DF4:
            case Cea708Data.CODE_C1_DF5:
            case Cea708Data.CODE_C1_DF6:
            case Cea708Data.CODE_C1_DF7: {
                // DefineWindow0-7
                int windowId = mCommand - Cea708Data.CODE_C1_DF0;
                boolean visible = (data[pos] & 0x20) != 0;
                boolean rowLock = (data[pos] & 0x10) != 0;
                boolean columnLock = (data[pos] & 0x08) != 0;
                int priority = data[pos] & 0x07;
                boolean relativePositioning = (data[pos + 1] & 0x80) != 0;
                int anchorVertical = data[pos + 1] & 0x7f;
                int anchorHorizontal = data[pos + 2] & 0xff;
                int anchorId = (data[pos + 3] & 0xf0) >> 4;
                int rowCount = data[pos + 3] & 0x0f;
                int columnCount = data[pos + 4] & 0x3f;
                int windowStyle = (data[pos + 5] & 0x38) >> 3;
                int penStyle = data[pos + 5] & 0x07;
                pos += 6;
                emitCaptionEvent(new CaptionEvent(CAPTION_EMIT_TYPE_COMMAND_DFX,
                        new CaptionWindow(windowId, visible, rowLock, columnLock, priority,
                                relativePositioning, anchorVertical, anchorHorizontal, anchorId,
                                rowCount, columnCount, penStyle, windowStyle)));
                if (DEBUG) {
                    Log.d(TAG, String.format(
                            "CaptionCommand DFx windowId: %d, priority: %d, columnLock: %s, "
                                    + "rowLock: %s, visible: %s, anchorVertical: %d, "
                                    + "relativePositioning: %s, anchorHorizontal: %d, "
                                    + "rowCount: %d, anchorId: %d, columnCount: %d, penStyle: %d, "
                                    + "windowStyle: %d",
                            windowId, priority, columnLock, rowLock, visible, anchorVertical,
                            relativePositioning, anchorHorizontal, rowCount, anchorId, columnCount,
                            penStyle, windowStyle));
                }
                break;
            }

            default:
                break;
        }
        return pos;
    }

    private int parseG0(byte[] data, int pos) {
        // For the details of G0 code group, see CEA-708B Section 7.4.3.
        // GL Group: G0 Modified version of ANSI X3.4 Printable Character Set (ASCII)
        if (mCommand == Cea708Data.CODE_G0_MUSICNOTE) {
            // Music note.
            mBuffer.append(MUSIC_NOTE_CHAR);
        } else {
            // Put ASCII code into buffer.
            mBuffer.append((char) mCommand);
        }
        return pos;
    }

    private int parseG1(byte[] data, int pos) {
        // For the details of G0 code group, see CEA-708B Section 7.4.4.
        // GR Group: G1 ISO 8859-1 Latin 1 Characters
        // Put ASCII Extended character set into buffer.
        mBuffer.append((char) mCommand);
        return pos;
    }

    // Step 4. Extended code groups
    private int parseExt1(byte[] data, int pos) {
        // For the details of EXT1 code group, see CEA-708B Section 7.2.
        mCommand = data[pos] & 0xff;
        ++pos;
        if (mCommand >= Cea708Data.CODE_C2_RANGE_START
                && mCommand <= Cea708Data.CODE_C2_RANGE_END) {
            pos = parseC2(data, pos);
        } else if (mCommand >= Cea708Data.CODE_C3_RANGE_START
                && mCommand <= Cea708Data.CODE_C3_RANGE_END) {
            pos = parseC3(data, pos);
        } else if (mCommand >= Cea708Data.CODE_G2_RANGE_START
                && mCommand <= Cea708Data.CODE_G2_RANGE_END) {
            pos = parseG2(data, pos);
        } else if (mCommand >= Cea708Data.CODE_G3_RANGE_START
                && mCommand <= Cea708Data.CODE_G3_RANGE_END) {
            pos = parseG3(data ,pos);
        }
        return pos;
    }

    private int parseC2(byte[] data, int pos) {
        // For the details of C2 code group, see CEA-708B Section 7.4.7.
        // Extended Miscellaneous Control Codes
        // C2 Table : No commands as of CEA-708B. A decoder must skip.
        if (mCommand >= Cea708Data.CODE_C2_SKIP0_RANGE_START
                && mCommand <= Cea708Data.CODE_C2_SKIP0_RANGE_END) {
            // Do nothing.
        } else if (mCommand >= Cea708Data.CODE_C2_SKIP1_RANGE_START
                && mCommand <= Cea708Data.CODE_C2_SKIP1_RANGE_END) {
            ++pos;
        } else if (mCommand >= Cea708Data.CODE_C2_SKIP2_RANGE_START
                && mCommand <= Cea708Data.CODE_C2_SKIP2_RANGE_END) {
            pos += 2;
        } else if (mCommand >= Cea708Data.CODE_C2_SKIP3_RANGE_START
                && mCommand <= Cea708Data.CODE_C2_SKIP3_RANGE_END) {
            pos += 3;
        }
        return pos;
    }

    private int parseC3(byte[] data, int pos) {
        // For the details of C3 code group, see CEA-708B Section 7.4.8.
        // Extended Control Code Set 2
        // C3 Table : No commands as of CEA-708B. A decoder must skip.
        if (mCommand >= Cea708Data.CODE_C3_SKIP4_RANGE_START
                && mCommand <= Cea708Data.CODE_C3_SKIP4_RANGE_END) {
            pos += 4;
        } else if (mCommand >= Cea708Data.CODE_C3_SKIP5_RANGE_START
                && mCommand <= Cea708Data.CODE_C3_SKIP5_RANGE_END) {
            pos += 5;
        }
        return pos;
    }

    private int parseG2(byte[] data, int pos) {
        // For the details of C3 code group, see CEA-708B Section 7.4.5.
        // Extended Control Code Set 1(G2 Table)
        switch (mCommand) {
            case Cea708Data.CODE_G2_TSP:
                // TODO : TSP is the Transparent space
                break;
            case Cea708Data.CODE_G2_NBTSP:
                // TODO : NBTSP is Non-Breaking Transparent Space.
                break;
            case Cea708Data.CODE_G2_BLK:
                // TODO : BLK indicates a solid block which fills the entire character block
                // TODO : with a solid foreground color.
                break;
            default:
                break;
        }
        return pos;
    }

    private int parseG3(byte[] data, int pos) {
        // For the details of C3 code group, see CEA-708B Section 7.4.6.
        // Future characters and icons(G3 Table)
        if (mCommand == Cea708Data.CODE_G3_CC) {
            // TODO : [CC] icon with square corners
        }

        // Do nothing
        return pos;
    }
}
