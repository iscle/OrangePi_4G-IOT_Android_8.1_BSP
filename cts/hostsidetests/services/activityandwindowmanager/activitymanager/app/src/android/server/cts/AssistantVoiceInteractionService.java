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
 * limitations under the License
 */

package android.server.cts;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.service.voice.VoiceInteractionService;
import android.util.Log;

public class AssistantVoiceInteractionService extends VoiceInteractionService {

    private static final String TAG = AssistantVoiceInteractionService.class.getSimpleName();

    private boolean mReady;

    @Override
    public void onReady() {
        super.onReady();
        mReady = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isActiveService(this, new ComponentName(this, getClass()))) {
            Log.wtf(TAG, "**** Not starting AssistantVoiceInteractionService because" +
                    " it is not set as the current voice interaction service");
            stopSelf();
            return START_NOT_STICKY;
        }
        if (mReady) {
            Bundle extras = intent.getExtras() != null ? intent.getExtras() : new Bundle();
            showSession(extras, 0);
        }
        return START_NOT_STICKY;
    }

    /**
     * Starts the assistant voice interaction service, which initiates a new session that starts
     * the assistant activity.
     */
    public static void launchAssistantActivity(Context context, Bundle extras) {
        Intent i = new Intent(context, AssistantVoiceInteractionService.class);
        if (extras != null) {
            i.putExtras(extras);
        }
        context.startService(i);
    }
}
