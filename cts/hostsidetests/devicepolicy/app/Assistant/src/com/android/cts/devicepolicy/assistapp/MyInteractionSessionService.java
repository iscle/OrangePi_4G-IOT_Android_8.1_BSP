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

package com.android.cts.devicepolicy.assistapp;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;
import android.service.voice.VoiceInteractionSessionService;
import android.util.Log;

public class MyInteractionSessionService extends VoiceInteractionSessionService {
    private static final String TAG = "DevicePolicyAssistTest";
    private static final String ACTION_HANDLE_SCREENSHOT =
            "voice_interaction_session_service.handle_screenshot";
    private static final String KEY_HAS_SCREENSHOT = "has_screenshot";

    @Override
    public VoiceInteractionSession onNewSession(Bundle args) {
        return new MainInteractionSession(this);
    }

    public static class MainInteractionSession extends VoiceInteractionSession {

        public MainInteractionSession(Context context) {
            super(context);
        }

        @Override
        public void onHandleScreenshot(Bitmap screenshot) {
            Log.d(TAG, "onHandleScreenshot() called with: screenshot = [" + screenshot + "]");
            Intent intent = new Intent(ACTION_HANDLE_SCREENSHOT);
            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            intent.putExtra(KEY_HAS_SCREENSHOT, screenshot != null);
            getContext().sendBroadcast(intent);
            finish();
        }
    }
}