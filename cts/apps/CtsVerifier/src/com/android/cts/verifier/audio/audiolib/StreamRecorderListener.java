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

package com.android.cts.verifier.audio.audiolib;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

public class StreamRecorderListener extends Handler {
    @SuppressWarnings("unused")
    private static final String TAG = "StreamRecorderListener";

    public static final int MSG_START = 0;
    public static final int MSG_BUFFER_FILL = 1;
    public static final int MSG_STOP = 2;

    public StreamRecorderListener(Looper looper) {
        super(looper);
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_START:
            case MSG_BUFFER_FILL:
            case MSG_STOP:
                break;
        }
    }
}
