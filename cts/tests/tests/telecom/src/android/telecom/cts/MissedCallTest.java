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
 * limitations under the License.
 */

package android.telecom.cts;

import android.content.Intent;
import android.telecom.Call;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.TelecomManager;
import android.telecom.cts.MockMissedCallNotificationReceiver.IntentListener;
import android.util.Log;

public class MissedCallTest extends BaseTelecomTestWithMockServices {

    TestUtils.InvokeCounter mShowMissedCallNotificationIntentCounter =
            new TestUtils.InvokeCounter("ShowMissedCallNotificationIntent");

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getContext();

        MockMissedCallNotificationReceiver.setIntentListener(new IntentListener() {
            @Override
            public void onIntentReceived(Intent intent) {
                Log.i(TestUtils.TAG, intent.toString());
                if (TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION
                        .equals(intent.getAction())) {
                    mShowMissedCallNotificationIntentCounter.invoke();
                }
            }
        });
    }

    @Override
    public void tearDown() throws Exception {
        MockMissedCallNotificationReceiver.setIntentListener(null);
        super.tearDown();
    }

    public void testMissedCall_NotifyDialer() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);

        addAndVerifyNewIncomingCall(createTestNumber(), null);
        final MockConnection connection = verifyConnectionForIncomingCall();

        final MockInCallService inCallService = mInCallCallbacks.getService();

        final Call call = inCallService.getLastCall();

        assertCallState(call, Call.STATE_RINGING);
        assertConnectionState(connection, Connection.STATE_RINGING);

        connection.setDisconnected(new DisconnectCause(DisconnectCause.MISSED));
        connection.destroy();
        mShowMissedCallNotificationIntentCounter.waitForCount(1);
    }

}
