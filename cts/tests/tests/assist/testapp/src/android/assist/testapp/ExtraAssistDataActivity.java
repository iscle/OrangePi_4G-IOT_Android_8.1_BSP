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
package android.assist.testapp;

import android.app.Activity;
import android.app.assist.AssistContent;
import android.assist.common.Utils;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * Test the onProvideAssistData and onProvideAssistContent methods activities may override to
 * provide extra information to the assistant. Verify that the data passed from the activity matches
 * the data received in {@link android.service.voice.VoiceInteractionSession}.
 */
public class ExtraAssistDataActivity extends Activity {
    private static final String TAG = "ExtraAssistDataActivity";
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onProvideAssistData(Bundle data) {
        super.onProvideAssistData(data);
        Log.i(TAG, "onProvideAssistData");
        Utils.addExtraAssistDataToBundle(data);
    }

    @Override
    public void onProvideAssistContent(AssistContent outContent) {
        super.onProvideAssistContent(outContent);
        Log.i(TAG, "onProvideAssistContent");
        try {
            outContent.setStructuredData(Utils.getStructuredJSON());
        } catch (Exception e) {
            Log.i(TAG, "Failed to get Structured JSON to put into the AssistContent.");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        sendBroadcast(new Intent(Utils.APP_3P_HASRESUMED));
    }
}