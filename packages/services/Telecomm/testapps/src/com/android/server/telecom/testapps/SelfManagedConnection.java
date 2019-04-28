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

package com.android.server.telecom.testapps;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.Connection;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.VideoProfile;

import com.android.server.telecom.testapps.R;

/**
 * Sample self-managed {@link Connection} for a self-managed {@link ConnectionService}.
 * <p>
 * See {@link android.telecom} for more information on self-managed {@link ConnectionService}s.
 */
public class SelfManagedConnection extends Connection {
    public static class Listener {
        public void onConnectionStateChanged(SelfManagedConnection connection) {}
        public void onConnectionRemoved(SelfManagedConnection connection) {}
    }

    public static final String EXTRA_PHONE_ACCOUNT_HANDLE =
            "com.android.server.telecom.testapps.extra.PHONE_ACCOUNT_HANDLE";
    public static final String CALL_NOTIFICATION = "com.android.server.telecom.testapps.CALL";

    private static int sNextCallId = 1;

    private final int mCallId;
    private final Context mContext;
    private final SelfManagedCallList mCallList;
    private final MediaPlayer mMediaPlayer;
    private final boolean mIsIncomingCall;
    private boolean mIsIncomingCallUiShowing;
    private Listener mListener;
    private boolean mIsHandover;

    SelfManagedConnection(SelfManagedCallList callList, Context context, boolean isIncoming) {
        mCallList = callList;
        mMediaPlayer = createMediaPlayer(context);
        mIsIncomingCall = isIncoming;
        mContext = context;
        mCallId = sNextCallId++;
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    /**
     * Handles updates to the audio state of the connection.
     * @param state The new connection audio state.
     */
    @Override
    public void onCallAudioStateChanged(CallAudioState state) {
        mCallList.notifyCallModified();
    }

    @Override
    public void onShowIncomingCallUi() {
        if (isHandover()) {
            return;
        }
        // Create the fullscreen intent used to show the fullscreen incoming call UX.
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.setFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION | Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClass(mContext, IncomingSelfManagedCallActivity.class);
        intent.putExtra(IncomingSelfManagedCallActivity.EXTRA_CALL_ID, mCallId);
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 1, intent, 0);

        // Build the notification as an ongoing high priority item.
        final Notification.Builder builder = new Notification.Builder(mContext);
        builder.setOngoing(true);
        builder.setPriority(Notification.PRIORITY_HIGH);

        // Set up the main intent to send the user to the incoming call screen.
        builder.setContentIntent(pendingIntent);
        builder.setFullScreenIntent(pendingIntent, true);

        // Setup notification content.
        builder.setSmallIcon(R.drawable.ic_android_black_24dp);
        builder.setContentTitle("Incoming call...");
        builder.setContentText("Incoming test call from " + getAddress());

        // Setup answer and reject call button
        final Intent answerIntent = new Intent(
                SelfManagedCallNotificationReceiver.ACTION_ANSWER_CALL, null, mContext,
                SelfManagedCallNotificationReceiver.class);
        answerIntent.putExtra(IncomingSelfManagedCallActivity.EXTRA_CALL_ID, mCallId);
        final Intent rejectIntent = new Intent(
                SelfManagedCallNotificationReceiver.ACTION_REJECT_CALL, null, mContext,
                SelfManagedCallNotificationReceiver.class);
        rejectIntent.putExtra(IncomingSelfManagedCallActivity.EXTRA_CALL_ID, mCallId);

        builder.addAction(
                new Notification.Action.Builder(
                        Icon.createWithResource(mContext, R.drawable.ic_android_black_24dp),
                        "Answer",
                        PendingIntent.getBroadcast(mContext, 0, answerIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT))
                        .build());
        builder.addAction(
                new Notification.Action.Builder(
                        Icon.createWithResource(mContext, R.drawable.ic_android_black_24dp),
                        "Reject",
                        PendingIntent.getBroadcast(mContext, 0, rejectIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT))
                        .build());

        NotificationManager notificationManager = mContext.getSystemService(
                NotificationManager.class);
        notificationManager.notify(CALL_NOTIFICATION, mCallId, builder.build());
    }

    @Override
    public void onHold() {
        setOnHold();
    }

    @Override
    public void onUnhold() {
        setActive();
    }

    @Override
    public void onAnswer(int videoState) {
        setConnectionActive();
    }

    @Override
    public void onAnswer() {
        onAnswer(VideoProfile.STATE_AUDIO_ONLY);
    }

    @Override
    public void onReject() {
        setConnectionDisconnected(DisconnectCause.REJECTED);
    }

    @Override
    public void onDisconnect() {
        setConnectionDisconnected(DisconnectCause.LOCAL);
    }

    public void setConnectionActive() {
        mMediaPlayer.start();
        setActive();
        if (mListener != null ) {
            mListener.onConnectionStateChanged(this);
        }
    }

    public void setConnectionHeld() {
        mMediaPlayer.pause();
        setOnHold();
        if (mListener != null ) {
            mListener.onConnectionStateChanged(this);
        }
    }

    public void setConnectionDisconnected(int cause) {
        mMediaPlayer.stop();
        setDisconnected(new DisconnectCause(cause));
        destroy();
        if (mListener != null ) {
            mListener.onConnectionRemoved(this);
        }
    }

    public void setIsIncomingCallUiShowing(boolean showing) {
        mIsIncomingCallUiShowing = showing;
    }

    public boolean isIncomingCallUiShowing() {
        return mIsIncomingCallUiShowing;
    }

    public boolean isIncomingCall() {
        return mIsIncomingCall;
    }

    public int getCallId() {
        return mCallId;
    }

    public void setIsHandover(boolean isHandover) {
        mIsHandover = isHandover;
    }

    public boolean isHandover() {
        return mIsHandover;
    }

    private MediaPlayer createMediaPlayer(Context context) {
        int audioToPlay = (Math.random() > 0.5f) ? R.raw.sample_audio : R.raw.sample_audio2;
        MediaPlayer mediaPlayer = MediaPlayer.create(context, audioToPlay);
        mediaPlayer.setLooping(true);
        return mediaPlayer;
    }
}
