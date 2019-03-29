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

package android.backup.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/*
 * Broadcast receiver pinged in order to make sure the app progressed from
 * the stopped state after being installed, so that backup can be done.
 */
public class WakeUpReceiver extends BroadcastReceiver {

    private static final String TAG = "WakeUpReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "android.backup.app should no longer be in the stopped state");
    }
}
