/*
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.car.stream;

import android.app.PendingIntent;
import android.graphics.Bitmap;
import android.os.Bundle;

/**
 * An extension to {@link StreamCard} that holds data specific to current call events.
 */
public class CurrentCallExtension extends StreamCardExtension {
    private static final int INVALID_CALL_STATE = -1;

    private static final String MUTE_ACTION_KEY = "mute_action";
    private static final String UNMUTE_ACTION_KEY = "unmute_action";
    private static final String ACCEPT_CALL_ACTION_KEY = "accept_call_action";
    private static final String HANGUP_CALL_ACTION_KEY = "hangup_call_action";
    private static final String CALL_START_TIME_KEY_KEY = "call_start_time";
    private static final String CALL_STATE_KEY = "call_state";
    private static final String IS_MUTED_KEY = "is_muted";
    private static final String DISPLAY_NAME_KEY = "display_name";
    private static final String CONTACT_PHOTO_KEY = "contact_photo";

    private long mCallStartTime;
    private String mDisplayName;
    private int mCallState = INVALID_CALL_STATE;
    private boolean mIsMuted;

    private Bitmap mContactPhoto;

    private PendingIntent mMuteAction;
    private PendingIntent mUnMuteAction;
    private PendingIntent mAcceptCallAction;
    private PendingIntent mHangupCallAction;

    public static final Creator<CurrentCallExtension> CREATOR
            = new BundleableCreator<>(CurrentCallExtension.class);

    public CurrentCallExtension() {}

    public CurrentCallExtension(
            long callStartTime,
            String displayName,
            int callState,
            boolean isMuted,
            Bitmap contactPhoto,
            PendingIntent muteAction,
            PendingIntent unMuteAction,
            PendingIntent acceptCallAction,
            PendingIntent hangupCallAction) {
        mCallStartTime = callStartTime;
        mDisplayName = displayName;
        mCallState = callState;
        mIsMuted = isMuted;
        mContactPhoto = contactPhoto;
        mMuteAction = muteAction;
        mUnMuteAction = unMuteAction;
        mAcceptCallAction = acceptCallAction;
        mHangupCallAction = hangupCallAction;
    }

    @Override
    protected void writeToBundle(Bundle bundle) {
        bundle.putString(DISPLAY_NAME_KEY, mDisplayName);
        bundle.putInt(CALL_STATE_KEY, mCallState);
        bundle.putBoolean(IS_MUTED_KEY, mIsMuted);
        bundle.putParcelable(CONTACT_PHOTO_KEY, mContactPhoto);
        bundle.putLong(CALL_START_TIME_KEY_KEY, mCallStartTime);

        bundle.putParcelable(MUTE_ACTION_KEY, mMuteAction);
        bundle.putParcelable(UNMUTE_ACTION_KEY, mUnMuteAction);
        bundle.putParcelable(ACCEPT_CALL_ACTION_KEY, mAcceptCallAction);
        bundle.putParcelable(HANGUP_CALL_ACTION_KEY, mHangupCallAction);
    }

    @Override
    protected void readFromBundle(Bundle bundle) {
        mDisplayName = bundle.getString(DISPLAY_NAME_KEY);
        mCallState = bundle.getInt(CALL_STATE_KEY, INVALID_CALL_STATE);

        mIsMuted = bundle.getBoolean(IS_MUTED_KEY);
        mContactPhoto = bundle.getParcelable(CONTACT_PHOTO_KEY);
        mCallStartTime = bundle.getLong(CALL_START_TIME_KEY_KEY);

        mMuteAction = bundle.getParcelable(MUTE_ACTION_KEY);
        mUnMuteAction = bundle.getParcelable(UNMUTE_ACTION_KEY);
        mAcceptCallAction = bundle.getParcelable(ACCEPT_CALL_ACTION_KEY);
        mHangupCallAction = bundle.getParcelable(HANGUP_CALL_ACTION_KEY);
    }

    public String getDisplayName() {
        return mDisplayName;
    }

    public PendingIntent getHangupCallAction() {
        return mHangupCallAction;
    }

    public PendingIntent getAcceptCallAction() {
        return mAcceptCallAction;
    }

    public PendingIntent getUnMuteAction() {
        return mUnMuteAction;
    }

    public PendingIntent getMuteAction() {
        return mMuteAction;
    }

    public int getCallState() {
        return mCallState;
    }

    public long getCallStartTime() {
        return mCallStartTime;
    }

    public boolean isMuted() {
        return mIsMuted;
    }

    public Bitmap getContactPhoto() {
        return mContactPhoto;
    }
}
