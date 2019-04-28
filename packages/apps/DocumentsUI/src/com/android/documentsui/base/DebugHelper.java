/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.documentsui.base;

import android.util.Log;
import android.util.Pair;

import com.android.documentsui.Injector;
import com.android.documentsui.R;

/**
 * Debug menu tools requested by QA Fred.
 */
public class DebugHelper {

    private static final String TAG = "DebugHelper";

    private static final int[][] sCode = new int[][] {
            {19, 19, 20, 20, 21, 22, 21, 22, 30, 29},
            {51, 51, 47, 47, 29, 32, 29, 32, 30, 29}
    };

    private static final int[][] sColors = new int[][] {
            {0xFFDB3236, 0xFFB71C1C},
            {0xFF3cba54, 0xFF1B5E20},
            {0xFFf4c20d, 0xFFF9A825},
            {0xFF4885ed, 0xFF0D47A1}
    };

    @SuppressWarnings("unchecked")
    private static final Pair<String, Integer>[] sMessages = new Pair[]{
            new Pair<>("Woof Woof", R.drawable.debug_msg_1),
            new Pair<>("ワンワン", R.drawable.debug_msg_2)
    };

    private final Injector<?> mInjector;

    private boolean mDebugEnabled;
    private long mLastTime;
    private int mPosition;
    private int mCodeIndex;
    private int mColorIndex;
    private int mMessageIndex;

    public DebugHelper(Injector<?> injector) {
        mInjector = injector;
    }

    public int[] getNextColors() {
        assert (mInjector.features.isDebugSupportEnabled());

        if (mColorIndex == sColors.length) {
            mColorIndex = 0;
        }

        return sColors[mColorIndex++];
    }

    public Pair<String, Integer> getNextMessage() {
        assert (mInjector.features.isDebugSupportEnabled());

        if (mMessageIndex == sMessages.length) {
            mMessageIndex = 0;
        }

        return sMessages[mMessageIndex++];
    }

    public void debugCheck(long time, int keyCode) {
        if (time == mLastTime) {
            return;
        }
        mLastTime = time;

        if (mPosition == 0) {
            for (int i = 0; i < sCode.length; i++) {
                if (keyCode == sCode[i][0]) {
                    mCodeIndex = i;
                    break;
                }
            }
        }

        if (keyCode == sCode[mCodeIndex][mPosition]) {
            mPosition++;
        } else if (mPosition  > 2 || (mPosition == 2 && keyCode != sCode[mCodeIndex][0])) {
            mPosition = 0;
        }

        if (mPosition == sCode[mCodeIndex].length) {
            mPosition = 0;
            toggleDebugMode();
        }
    }

    public void toggleDebugMode() {
        mDebugEnabled = !mDebugEnabled;
        // Actions is content-scope, so it can technically be null, though
        // not likely.
        if (mInjector.actions != null) {
            mInjector.actions.setDebugMode(mDebugEnabled);
        }

        if (Shared.VERBOSE) {
            Log.v(TAG, "Debug mode " + (mDebugEnabled ? "on" : "off"));
        }
    }
}
