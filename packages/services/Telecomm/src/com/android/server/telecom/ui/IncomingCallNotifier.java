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

package com.android.server.telecom.ui;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telecom.Log;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.ArraySet;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.telecom.Call;
import com.android.server.telecom.CallState;
import com.android.server.telecom.CallsManagerListenerBase;
import com.android.server.telecom.HandoverState;
import com.android.server.telecom.R;
import com.android.server.telecom.TelecomBroadcastIntentProcessor;
import com.android.server.telecom.components.TelecomBroadcastReceiver;

import java.util.Optional;
import java.util.Set;

/**
 * Manages the display of an incoming call UX when a new ringing self-managed call is added, and
 * there is an ongoing call in another {@link android.telecom.PhoneAccount}.
 */
public class IncomingCallNotifier extends CallsManagerListenerBase {

    public interface IncomingCallNotifierFactory {
        IncomingCallNotifier make(Context context, CallsManagerProxy mCallsManagerProxy);
    }

    /**
     * Eliminates strict dependency between this class and CallsManager.
     */
    public interface CallsManagerProxy {
        boolean hasCallsForOtherPhoneAccount(PhoneAccountHandle phoneAccountHandle);
        int getNumCallsForOtherPhoneAccount(PhoneAccountHandle phoneAccountHandle);
        Call getActiveCall();
    }

    // Notification for incoming calls. This is interruptive and will show up as a HUN.
    @VisibleForTesting
    public static final int NOTIFICATION_INCOMING_CALL = 1;
    @VisibleForTesting
    public static final String NOTIFICATION_TAG = IncomingCallNotifier.class.getSimpleName();


    public final Call.ListenerBase mCallListener = new Call.ListenerBase() {
        @Override
        public void onCallerInfoChanged(Call call) {
            if (mIncomingCall != call) {
                return;
            }
            showIncomingCallNotification(mIncomingCall);
        }
    };

    private final Context mContext;
    private final NotificationManager mNotificationManager;
    private final Set<Call> mCalls = new ArraySet<>();
    private CallsManagerProxy mCallsManagerProxy;

    // The current incoming call we are displaying UX for.
    private Call mIncomingCall;

    public IncomingCallNotifier(Context context) {
        mContext = context;
        mNotificationManager =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    public void setCallsManagerProxy(CallsManagerProxy callsManagerProxy) {
        mCallsManagerProxy = callsManagerProxy;
    }

    public Call getIncomingCall() {
        return mIncomingCall;
    }

    @Override
    public void onCallAdded(Call call) {
        if (!mCalls.contains(call)) {
            mCalls.add(call);
        }

        updateIncomingCall();
    }

    @Override
    public void onCallRemoved(Call call) {
        if (mCalls.contains(call)) {
            mCalls.remove(call);
        }

        updateIncomingCall();
    }

    @Override
    public void onCallStateChanged(Call call, int oldState, int newState) {
        updateIncomingCall();
    }

    /**
     * Determines which call is the active ringing call at this time and triggers the display of the
     * UI.
     */
    private void updateIncomingCall() {
        Optional<Call> incomingCallOp = mCalls.stream()
                .filter(call -> call.isSelfManaged() && call.isIncoming() &&
                        call.getState() == CallState.RINGING &&
                        call.getHandoverState() == HandoverState.HANDOVER_NONE)
                .findFirst();
        Call incomingCall = incomingCallOp.orElse(null);
        if (incomingCall != null && mCallsManagerProxy != null &&
                !mCallsManagerProxy.hasCallsForOtherPhoneAccount(
                        incomingCallOp.get().getTargetPhoneAccount())) {
            // If there is no calls in any other ConnectionService, we can rely on the
            // third-party app to display its own incoming call UI.
            incomingCall = null;
        }

        Log.i(this, "updateIncomingCall: foundIncomingcall = %s", incomingCall);

        boolean hadIncomingCall = mIncomingCall != null;
        boolean hasIncomingCall = incomingCall != null;
        if (incomingCall != mIncomingCall) {
            Call previousIncomingCall = mIncomingCall;
            mIncomingCall = incomingCall;

            if (hasIncomingCall && !hadIncomingCall) {
                mIncomingCall.addListener(mCallListener);
                showIncomingCallNotification(mIncomingCall);
            } else if (hadIncomingCall && !hasIncomingCall) {
                previousIncomingCall.removeListener(mCallListener);
                hideIncomingCallNotification();
            }
        }
    }

    private void showIncomingCallNotification(Call call) {
        Log.i(this, "showIncomingCallNotification showCall = %s", call);

        Notification.Builder builder = getNotificationBuilder(call,
                mCallsManagerProxy.getActiveCall());
        mNotificationManager.notify(NOTIFICATION_TAG, NOTIFICATION_INCOMING_CALL, builder.build());
    }

    private void hideIncomingCallNotification() {
        Log.i(this, "hideIncomingCallNotification");
        mNotificationManager.cancel(NOTIFICATION_TAG, NOTIFICATION_INCOMING_CALL);
    }

    private String getNotificationName(Call call) {
        String name = "";
        if (call.getCallerDisplayNamePresentation() == TelecomManager.PRESENTATION_ALLOWED) {
            name = call.getCallerDisplayName();
        }
        if (TextUtils.isEmpty(name)) {
            name = call.getName();
        }

        if (TextUtils.isEmpty(name)) {
            name = call.getPhoneNumber();
        }
        return name;
    }

    private Notification.Builder getNotificationBuilder(Call incomingCall, Call ongoingCall) {
        // Change the notification app name to "Android System" to sufficiently distinguish this
        // from the phone app's name.
        Bundle extras = new Bundle();
        extras.putString(Notification.EXTRA_SUBSTITUTE_APP_NAME, mContext.getString(
                com.android.internal.R.string.android_system_label));

        Intent answerIntent = new Intent(
                TelecomBroadcastIntentProcessor.ACTION_ANSWER_FROM_NOTIFICATION, null, mContext,
                TelecomBroadcastReceiver.class);
        Intent rejectIntent = new Intent(
                TelecomBroadcastIntentProcessor.ACTION_REJECT_FROM_NOTIFICATION, null, mContext,
                TelecomBroadcastReceiver.class);

        String nameOrNumber = getNotificationName(incomingCall);
        CharSequence viaApp = incomingCall.getTargetPhoneAccountLabel();
        boolean isIncomingVideo = VideoProfile.isVideo(incomingCall.getVideoState());
        boolean isOngoingVideo = ongoingCall != null ?
                VideoProfile.isVideo(ongoingCall.getVideoState()) : false;
        int numOtherCalls = ongoingCall != null ?
                mCallsManagerProxy.getNumCallsForOtherPhoneAccount(
                        incomingCall.getTargetPhoneAccount()) : 1;

        // Build the "IncomingApp call from John Smith" message.
        CharSequence incomingCallText;
        if (isIncomingVideo) {
            incomingCallText = mContext.getString(R.string.notification_incoming_video_call, viaApp,
                    nameOrNumber);
        } else {
            incomingCallText = mContext.getString(R.string.notification_incoming_call, viaApp,
                    nameOrNumber);
        }

        // Build the "Answering will end your OtherApp call" line.
        CharSequence disconnectText;
        if (ongoingCall != null && ongoingCall.isSelfManaged()) {
            CharSequence ongoingApp = ongoingCall.getTargetPhoneAccountLabel();
            // For an ongoing self-managed call, we use a message like:
            // "Answering will end your OtherApp call".
            if (numOtherCalls > 1) {
                // Multiple ongoing calls in the other app, so don't bother specifing whether it is
                // a video call or audio call.
                disconnectText = mContext.getString(R.string.answering_ends_other_calls,
                        ongoingApp);
            } else if (isOngoingVideo) {
                disconnectText = mContext.getString(R.string.answering_ends_other_video_call,
                        ongoingApp);
            } else {
                disconnectText = mContext.getString(R.string.answering_ends_other_call, ongoingApp);
            }
        } else {
            // For an ongoing managed call, we use a message like:
            // "Answering will end your ongoing call".
            if (numOtherCalls > 1) {
                // Multiple ongoing manage calls, so don't bother specifing whether it is a video
                // call or audio call.
                disconnectText = mContext.getString(R.string.answering_ends_other_managed_calls);
            } else if (isOngoingVideo) {
                disconnectText = mContext.getString(
                        R.string.answering_ends_other_managed_video_call);
            } else {
                disconnectText = mContext.getString(R.string.answering_ends_other_managed_call);
            }
        }

        final Notification.Builder builder = new Notification.Builder(mContext);
        builder.setOngoing(true);
        builder.setExtras(extras);
        builder.setPriority(Notification.PRIORITY_HIGH);
        builder.setCategory(Notification.CATEGORY_CALL);
        builder.setContentTitle(incomingCallText);
        builder.setContentText(disconnectText);
        builder.setSmallIcon(R.drawable.ic_phone);
        builder.setChannelId(NotificationChannelManager.CHANNEL_ID_INCOMING_CALLS);
        // Ensures this is a heads up notification.  A heads-up notification is typically only shown
        // if there is a fullscreen intent.  However since this notification doesn't have that we
        // will use this trick to get it to show as one anyways.
        builder.setVibrate(new long[0]);
        builder.setColor(mContext.getResources().getColor(R.color.theme_color));
        builder.addAction(
                R.anim.on_going_call,
                getActionText(R.string.answer_incoming_call, R.color.notification_action_answer),
                PendingIntent.getBroadcast(mContext, 0, answerIntent,
                        PendingIntent.FLAG_CANCEL_CURRENT));
        builder.addAction(
                R.drawable.ic_close_dk,
                getActionText(R.string.decline_incoming_call, R.color.notification_action_decline),
                PendingIntent.getBroadcast(mContext, 0, rejectIntent,
                        PendingIntent.FLAG_CANCEL_CURRENT));
        return builder;
    }

    private CharSequence getActionText(int stringRes, int colorRes) {
        CharSequence string = mContext.getText(stringRes);
        if (string == null) {
            return "";
        }
        Spannable spannable = new SpannableString(string);
        spannable.setSpan(
                    new ForegroundColorSpan(mContext.getColor(colorRes)), 0, spannable.length(), 0);
        return spannable;
    }
}
