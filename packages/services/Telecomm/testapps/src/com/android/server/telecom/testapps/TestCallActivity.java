/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.server.telecom.testapps;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.VideoProfile;

/**
 * This activity exists in order to add an icon to the launcher. This activity has no UI of its own
 * and instead starts the notification for {@link TestConnectionService} via
 * {@link CallServiceNotifier}. After triggering a notification update, this activity immediately
 * finishes.
 *
 * To directly trigger a new incoming call, use the following adb command:
 *
 * adb shell am start -a android.telecom.testapps.ACTION_START_INCOMING_CALL -d "tel:123456789"
 */
public class TestCallActivity extends Activity {

    public static final String ACTION_NEW_INCOMING_CALL =
            "android.telecom.testapps.ACTION_START_INCOMING_CALL";

    /*
     * Action to exercise TelecomManager.addNewUnknownCall().
     */
    public static final String ACTION_NEW_UNKNOWN_CALL =
            "android.telecom.testapps.ACTION_NEW_UNKNOWN_CALL";

    /*
     * Hang up any test incoming calls, to simulate the user missing a call.
     */
    public static final String ACTION_HANGUP_CALLS =
            "android.telecom.testapps.ACTION_HANGUP_CALLS";

    public static final String ACTION_SEND_UPGRADE_REQUEST =
            "android.telecom.testapps.ACTION_SEND_UPGRADE_REQUEST";

    static final String ACTION_RTT_CALL =
            "android.telecom.testapps.ACTION_RTT_CALL";

    public static final String ACTION_REMOTE_RTT_UPGRADE =
            "android.telecom.testapps.ACTION_REMOTE_RTT_UPGRADE";

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        final Intent intent = getIntent();
        final String action = intent != null ? intent.getAction() : null;
        final Uri data = intent != null ? intent.getData() : null;
        if (ACTION_NEW_INCOMING_CALL.equals(action) && data != null) {
            CallNotificationReceiver.sendIncomingCallIntent(this, data,
                    VideoProfile.STATE_AUDIO_ONLY);
        } else if (ACTION_NEW_UNKNOWN_CALL.equals(action) && data != null) {
            CallNotificationReceiver.addNewUnknownCall(this, data, intent.getExtras());
        } else if (ACTION_HANGUP_CALLS.equals(action)) {
            CallNotificationReceiver.hangupCalls(this);
        } else if (ACTION_RTT_CALL.equals(action)) {
            CallNotificationReceiver.sendIncomingRttCallIntent(
                    this, data, VideoProfile.STATE_AUDIO_ONLY);
        } else if (ACTION_SEND_UPGRADE_REQUEST.equals(action)) {
            CallNotificationReceiver.sendUpgradeRequest(this, data);
        } else if (ACTION_REMOTE_RTT_UPGRADE.equals(action)) {
            CallNotificationReceiver.remoteRttUpgrade(this);
        } else {
            CallServiceNotifier.getInstance().updateNotification(this);
        }
        finish();
    }
}
