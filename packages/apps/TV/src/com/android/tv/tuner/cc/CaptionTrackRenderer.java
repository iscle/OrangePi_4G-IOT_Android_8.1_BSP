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

import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;

import com.android.tv.tuner.data.Cea708Data.CaptionEvent;
import com.android.tv.tuner.data.Cea708Data.CaptionPenAttr;
import com.android.tv.tuner.data.Cea708Data.CaptionPenColor;
import com.android.tv.tuner.data.Cea708Data.CaptionPenLocation;
import com.android.tv.tuner.data.Cea708Data.CaptionWindow;
import com.android.tv.tuner.data.Cea708Data.CaptionWindowAttr;
import com.android.tv.tuner.data.nano.Track.AtscCaptionTrack;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Decodes and renders CEA-708.
 */
public class CaptionTrackRenderer implements Handler.Callback {
    // TODO: Remaining works
    // CaptionTrackRenderer does not support the full spec of CEA-708. The remaining works are
    // described in the follows.
    // C0 Table: Backspace, FF, and HCR are not supported. The rule for P16 is not standardized but
    //           it is handled as EUC-KR charset for korea broadcasting.
    // C1 Table: All styles of windows and pens except underline, italic, pen size, and pen offset
    //           specified in CEA-708 are ignored and this follows system wide cc preferences for
    //           look and feel. SetPenLocation is not implemented.
    // G2 Table: TSP, NBTSP and BLK are not supported.
    // Text/commands: Word wrapping, fonts, row and column locking are not supported.

    private static final String TAG = "CaptionTrackRenderer";
    private static final boolean DEBUG = false;

    private static final long DELAY_IN_MILLIS = TimeUnit.MILLISECONDS.toMillis(100);

    // According to CEA-708B, there can exist up to 8 caption windows.
    private static final int CAPTION_WINDOWS_MAX = 8;
    private static final int CAPTION_ALL_WINDOWS_BITMAP = 255;

    private static final int MSG_DELAY_CANCEL = 1;
    private static final int MSG_CAPTION_CLEAR = 2;

    private static final long CAPTION_CLEAR_INTERVAL_MS = 60000;

    private final CaptionLayout mCaptionLayout;
    private boolean mIsDelayed = false;
    private CaptionWindowLayout mCurrentWindowLayout;
    private final CaptionWindowLayout[] mCaptionWindowLayouts =
            new CaptionWindowLayout[CAPTION_WINDOWS_MAX];
    private final ArrayList<CaptionEvent> mPendingCaptionEvents = new ArrayList<>();
    private final Handler mHandler;

    public CaptionTrackRenderer(CaptionLayout captionLayout) {
        mCaptionLayout = captionLayout;
        mHandler = new Handler(this);
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_DELAY_CANCEL:
                delayCancel();
                return true;
            case MSG_CAPTION_CLEAR:
                clearWindows(CAPTION_ALL_WINDOWS_BITMAP);
                return true;
        }
        return false;
    }

    public void start(AtscCaptionTrack captionTrack) {
        if (captionTrack == null) {
            stop();
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "Start captionTrack " + captionTrack.language);
        }
        reset();
        mCaptionLayout.setCaptionTrack(captionTrack);
        mCaptionLayout.setVisibility(View.VISIBLE);
    }

    public void stop() {
        if (DEBUG) {
            Log.d(TAG, "Stop captionTrack");
        }
        mCaptionLayout.setVisibility(View.INVISIBLE);
        mHandler.removeMessages(MSG_CAPTION_CLEAR);
    }

    public void processCaptionEvent(CaptionEvent event) {
        if (mIsDelayed) {
            mPendingCaptionEvents.add(event);
            return;
        }
        switch (event.type) {
            case Cea708Parser.CAPTION_EMIT_TYPE_BUFFER:
                sendBufferToCurrentWindow((String) event.obj);
                break;
            case Cea708Parser.CAPTION_EMIT_TYPE_CONTROL:
                sendControlToCurrentWindow((char) event.obj);
                break;
            case Cea708Parser.CAPTION_EMIT_TYPE_COMMAND_CWX:
                setCurrentWindowLayout((int) event.obj);
                break;
            case Cea708Parser.CAPTION_EMIT_TYPE_COMMAND_CLW:
                clearWindows((int) event.obj);
                break;
            case Cea708Parser.CAPTION_EMIT_TYPE_COMMAND_DSW:
                displayWindows((int) event.obj);
                break;
            case Cea708Parser.CAPTION_EMIT_TYPE_COMMAND_HDW:
                hideWindows((int) event.obj);
                break;
            case Cea708Parser.CAPTION_EMIT_TYPE_COMMAND_TGW:
                toggleWindows((int) event.obj);
                break;
            case Cea708Parser.CAPTION_EMIT_TYPE_COMMAND_DLW:
                deleteWindows((int) event.obj);
                break;
            case Cea708Parser.CAPTION_EMIT_TYPE_COMMAND_DLY:
                delay((int) event.obj);
                break;
            case Cea708Parser.CAPTION_EMIT_TYPE_COMMAND_DLC:
                delayCancel();
                break;
            case Cea708Parser.CAPTION_EMIT_TYPE_COMMAND_RST:
                reset();
                break;
            case Cea708Parser.CAPTION_EMIT_TYPE_COMMAND_SPA:
                setPenAttr((CaptionPenAttr) event.obj);
                break;
            case Cea708Parser.CAPTION_EMIT_TYPE_COMMAND_SPC:
                setPenColor((CaptionPenColor) event.obj);
                break;
            case Cea708Parser.CAPTION_EMIT_TYPE_COMMAND_SPL:
                setPenLocation((CaptionPenLocation) event.obj);
                break;
            case Cea708Parser.CAPTION_EMIT_TYPE_COMMAND_SWA:
                setWindowAttr((CaptionWindowAttr) event.obj);
                break;
            case Cea708Parser.CAPTION_EMIT_TYPE_COMMAND_DFX:
                defineWindow((CaptionWindow) event.obj);
                break;
        }
    }

    // The window related caption commands
    private void setCurrentWindowLayout(int windowId) {
        if (windowId < 0 || windowId >= mCaptionWindowLayouts.length) {
            return;
        }
        CaptionWindowLayout windowLayout = mCaptionWindowLayouts[windowId];
        if (windowLayout == null) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "setCurrentWindowLayout to " + windowId);
        }
        mCurrentWindowLayout = windowLayout;
    }

    // Each bit of windowBitmap indicates a window.
    // If a bit is set, the window id is the same as the number of the trailing zeros of the bit.
    private ArrayList<CaptionWindowLayout> getWindowsFromBitmap(int windowBitmap) {
        ArrayList<CaptionWindowLayout> windows = new ArrayList<>();
        for (int i = 0; i < CAPTION_WINDOWS_MAX; ++i) {
            if ((windowBitmap & (1 << i)) != 0) {
                CaptionWindowLayout windowLayout = mCaptionWindowLayouts[i];
                if (windowLayout != null) {
                    windows.add(windowLayout);
                }
            }
        }
        return windows;
    }

    private void clearWindows(int windowBitmap) {
        if (windowBitmap == 0) {
            return;
        }
        for (CaptionWindowLayout windowLayout : getWindowsFromBitmap(windowBitmap)) {
            windowLayout.clear();
        }
    }

    private void displayWindows(int windowBitmap) {
        if (windowBitmap == 0) {
            return;
        }
        for (CaptionWindowLayout windowLayout : getWindowsFromBitmap(windowBitmap)) {
            windowLayout.show();
        }
    }

    private void hideWindows(int windowBitmap) {
        if (windowBitmap == 0) {
            return;
        }
        for (CaptionWindowLayout windowLayout : getWindowsFromBitmap(windowBitmap)) {
            windowLayout.hide();
        }
    }

    private void toggleWindows(int windowBitmap) {
        if (windowBitmap == 0) {
            return;
        }
        for (CaptionWindowLayout windowLayout : getWindowsFromBitmap(windowBitmap)) {
            if (windowLayout.isShown()) {
                windowLayout.hide();
            } else {
                windowLayout.show();
            }
        }
    }

    private void deleteWindows(int windowBitmap) {
        if (windowBitmap == 0) {
            return;
        }
        for (CaptionWindowLayout windowLayout : getWindowsFromBitmap(windowBitmap)) {
            windowLayout.removeFromCaptionView();
            mCaptionWindowLayouts[windowLayout.getCaptionWindowId()] = null;
        }
    }

    public void clear() {
        mHandler.sendEmptyMessage(MSG_CAPTION_CLEAR);
    }

    public void reset() {
        mCurrentWindowLayout = null;
        mIsDelayed = false;
        mPendingCaptionEvents.clear();
        for (int i = 0; i < CAPTION_WINDOWS_MAX; ++i) {
            if (mCaptionWindowLayouts[i] != null) {
                mCaptionWindowLayouts[i].removeFromCaptionView();
            }
            mCaptionWindowLayouts[i] = null;
        }
        mCaptionLayout.setVisibility(View.INVISIBLE);
        mHandler.removeMessages(MSG_CAPTION_CLEAR);
    }

    private void setWindowAttr(CaptionWindowAttr windowAttr) {
        if (mCurrentWindowLayout != null) {
            mCurrentWindowLayout.setWindowAttr(windowAttr);
        }
    }

    private void defineWindow(CaptionWindow window) {
        if (window == null) {
            return;
        }
        int windowId = window.id;
        if (windowId < 0 || windowId >= mCaptionWindowLayouts.length) {
            return;
        }
        CaptionWindowLayout windowLayout = mCaptionWindowLayouts[windowId];
        if (windowLayout == null) {
            windowLayout = new CaptionWindowLayout(mCaptionLayout.getContext());
        }
        windowLayout.initWindow(mCaptionLayout, window);
        mCurrentWindowLayout = mCaptionWindowLayouts[windowId] = windowLayout;
    }

    // The job related caption commands
    private void delay(int tenthsOfSeconds) {
        if (tenthsOfSeconds < 0 || tenthsOfSeconds > 255) {
            return;
        }
        mIsDelayed = true;
        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_DELAY_CANCEL),
                tenthsOfSeconds * DELAY_IN_MILLIS);
    }

    private void delayCancel() {
        mIsDelayed = false;
        processPendingBuffer();
    }

    private void processPendingBuffer() {
        for (CaptionEvent event : mPendingCaptionEvents) {
            processCaptionEvent(event);
        }
        mPendingCaptionEvents.clear();
    }

    // The implicit write caption commands
    private void sendControlToCurrentWindow(char control) {
        if (mCurrentWindowLayout != null) {
            mCurrentWindowLayout.sendControl(control);
        }
    }

    private void sendBufferToCurrentWindow(String buffer) {
        if (mCurrentWindowLayout != null) {
            mCurrentWindowLayout.sendBuffer(buffer);
            mHandler.removeMessages(MSG_CAPTION_CLEAR);
            mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_CAPTION_CLEAR),
                    CAPTION_CLEAR_INTERVAL_MS);
        }
    }

    // The pen related caption commands
    private void setPenAttr(CaptionPenAttr attr) {
        if (mCurrentWindowLayout != null) {
            mCurrentWindowLayout.setPenAttr(attr);
        }
    }

    private void setPenColor(CaptionPenColor color) {
        if (mCurrentWindowLayout != null) {
            mCurrentWindowLayout.setPenColor(color);
        }
    }

    private void setPenLocation(CaptionPenLocation location) {
        if (mCurrentWindowLayout != null) {
            mCurrentWindowLayout.setPenLocation(location.row, location.column);
        }
    }
}
