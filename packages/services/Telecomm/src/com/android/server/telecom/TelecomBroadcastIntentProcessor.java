/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.server.telecom;

import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.telecom.Log;

import com.android.server.telecom.ui.ConfirmCallDialogActivity;

public final class TelecomBroadcastIntentProcessor {
    /** The action used to send SMS response for the missed call notification. */
    public static final String ACTION_SEND_SMS_FROM_NOTIFICATION =
            "com.android.server.telecom.ACTION_SEND_SMS_FROM_NOTIFICATION";

    /** The action used to call a handle back for the missed call notification. */
    public static final String ACTION_CALL_BACK_FROM_NOTIFICATION =
            "com.android.server.telecom.ACTION_CALL_BACK_FROM_NOTIFICATION";

    /** The action used to clear missed calls. */
    public static final String ACTION_CLEAR_MISSED_CALLS =
            "com.android.server.telecom.ACTION_CLEAR_MISSED_CALLS";

    /**
     * The action used to answer the current incoming call displayed by
     * {@link com.android.server.telecom.ui.IncomingCallNotifier}.
     */
    public static final String ACTION_ANSWER_FROM_NOTIFICATION =
            "com.android.server.telecom.ACTION_ANSWER_FROM_NOTIFICATION";

    /**
     * The action used to reject the current incoming call displayed by
     * {@link com.android.server.telecom.ui.IncomingCallNotifier}.
     */
    public static final String ACTION_REJECT_FROM_NOTIFICATION =
            "com.android.server.telecom.ACTION_REJECT_FROM_NOTIFICATION";

    /**
     * The action used to proceed with a call being confirmed via
     * {@link com.android.server.telecom.ui.ConfirmCallDialogActivity}.
     */
    public static final String ACTION_PROCEED_WITH_CALL =
            "com.android.server.telecom.PROCEED_WITH_CALL";

    /**
     * The action used to cancel a call being confirmed via
     * {@link com.android.server.telecom.ui.ConfirmCallDialogActivity}.
     */
    public static final String ACTION_CANCEL_CALL =
            "com.android.server.telecom.CANCEL_CALL";

    public static final String EXTRA_USERHANDLE = "userhandle";

    private final Context mContext;
    private final CallsManager mCallsManager;

    public TelecomBroadcastIntentProcessor(Context context, CallsManager callsManager) {
        mContext = context;
        mCallsManager = callsManager;
    }

    public void processIntent(Intent intent) {
        String action = intent.getAction();

        if (ACTION_SEND_SMS_FROM_NOTIFICATION.equals(action) ||
                ACTION_CALL_BACK_FROM_NOTIFICATION.equals(action) ||
                ACTION_CLEAR_MISSED_CALLS.equals(action)) {
            Log.v(this, "Action received: %s.", action);
            UserHandle userHandle = intent.getParcelableExtra(EXTRA_USERHANDLE);
            if (userHandle == null) {
                Log.d(this, "user handle can't be null, not processing the broadcast");
                return;
            }

            MissedCallNotifier missedCallNotifier = mCallsManager.getMissedCallNotifier();

            // Send an SMS from the missed call notification.
            if (ACTION_SEND_SMS_FROM_NOTIFICATION.equals(action)) {
                // Close the notification shade and the notification itself.
                closeSystemDialogs(mContext);
                missedCallNotifier.clearMissedCalls(userHandle);

                Intent callIntent = new Intent(Intent.ACTION_SENDTO, intent.getData());
                callIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivityAsUser(callIntent, userHandle);

                // Call back recent caller from the missed call notification.
            } else if (ACTION_CALL_BACK_FROM_NOTIFICATION.equals(action)) {
                // Close the notification shade and the notification itself.
                closeSystemDialogs(mContext);
                missedCallNotifier.clearMissedCalls(userHandle);

                Intent callIntent = new Intent(Intent.ACTION_CALL, intent.getData());
                callIntent.setFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                mContext.startActivityAsUser(callIntent, userHandle);

                // Clear the missed call notification and call log entries.
            } else if (ACTION_CLEAR_MISSED_CALLS.equals(action)) {
                missedCallNotifier.clearMissedCalls(userHandle);
            }
        } else if (ACTION_ANSWER_FROM_NOTIFICATION.equals(action)) {
            Log.startSession("TBIP.aAFM");
            try {
                // Answer the current ringing call.
                Call incomingCall = mCallsManager.getIncomingCallNotifier().getIncomingCall();
                if (incomingCall != null) {
                    mCallsManager.answerCall(incomingCall, incomingCall.getVideoState());
                }
            } finally {
                Log.endSession();
            }
        } else if (ACTION_REJECT_FROM_NOTIFICATION.equals(action)) {
            Log.startSession("TBIP.aRFM");
            try {

                // Reject the current ringing call.
                Call incomingCall = mCallsManager.getIncomingCallNotifier().getIncomingCall();
                if (incomingCall != null) {
                    mCallsManager.rejectCall(incomingCall, false /* isRejectWithMessage */, null);
                }
            } finally {
                Log.endSession();
            }
        } else if (ACTION_PROCEED_WITH_CALL.equals(action)) {
            Log.startSession("TBIP.aPWC");
            try {
                String callId = intent.getStringExtra(
                        ConfirmCallDialogActivity.EXTRA_OUTGOING_CALL_ID);
                mCallsManager.confirmPendingCall(callId);
            } finally {
                Log.endSession();
            }
        } else if (ACTION_CANCEL_CALL.equals(action)) {
            Log.startSession("TBIP.aCC");
            try {
                String callId = intent.getStringExtra(
                        ConfirmCallDialogActivity.EXTRA_OUTGOING_CALL_ID);
                mCallsManager.cancelPendingCall(callId);
            } finally {
                Log.endSession();
            }
        }
    }

    /**
     * Closes open system dialogs and the notification shade.
     */
    private void closeSystemDialogs(Context context) {
        Intent intent = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        context.sendBroadcastAsUser(intent, UserHandle.ALL);
    }
}
