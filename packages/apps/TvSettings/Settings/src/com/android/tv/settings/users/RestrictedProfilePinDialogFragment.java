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
 * limitations under the License
 */

package com.android.tv.settings.users;

import android.app.Fragment;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.PreferenceManager;

import com.android.tv.settings.dialog.PinDialogFragment;

public class RestrictedProfilePinDialogFragment extends PinDialogFragment {

    public interface Callback extends ResultListener {
        void saveLockPassword(String pin, int quality);
        boolean checkPassword(String password, int userId);
        boolean hasLockscreenSecurity();
    }

    private static final String PREF_DISABLE_PIN_UNTIL =
            "RestrictedProfileActivity$RestrictedProfilePinDialogFragment.disable_pin_until";

    public static RestrictedProfilePinDialogFragment newInstance(@PinDialogType int type) {
        RestrictedProfilePinDialogFragment fragment = new RestrictedProfilePinDialogFragment();
        final Bundle b = new Bundle(1);
        b.putInt(PinDialogFragment.ARG_TYPE, type);
        fragment.setArguments(b);
        return fragment;
    }

    /**
     * Returns the time until we should disable the PIN dialog (because the user input wrong
     * PINs repeatedly).
     */
    public static long getDisablePinUntil(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getLong(
                PREF_DISABLE_PIN_UNTIL, 0);
    }

    /**
     * Saves the time until we should disable the PIN dialog (because the user input wrong PINs
     * repeatedly).
     */
    public static void setDisablePinUntil(Context context, long timeMillis) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putLong(
                PREF_DISABLE_PIN_UNTIL, timeMillis).apply();
    }

    @Override
    public long getPinDisabledUntil() {
        return getDisablePinUntil(getActivity());
    }

    @Override
    public void setPinDisabledUntil(long retryDisableTimeout) {
        setDisablePinUntil(getActivity(), retryDisableTimeout);
    }

    @Override
    public void setPin(String pin) {
        Callback callback = null;

        Fragment f = getTargetFragment();
        if (f instanceof Callback) {
            callback = (Callback) f;
        }

        if (callback == null && getActivity() instanceof Callback) {
            callback = (Callback) getActivity();
        }

        if (callback != null) {
            callback.saveLockPassword(pin, DevicePolicyManager.PASSWORD_QUALITY_SOMETHING);
        }
    }

    @Override
    public boolean isPinCorrect(String pin) {
        Callback callback = null;

        Fragment f = getTargetFragment();
        if (f instanceof Callback) {
            callback = (Callback) f;
        }

        if (callback == null && getActivity() instanceof Callback) {
            callback = (Callback) getActivity();
        }

        if (callback != null) {
            UserInfo myUserInfo = UserManager.get(getActivity()).getUserInfo(UserHandle.myUserId());
            return myUserInfo != null &&
                    callback.checkPassword(pin, myUserInfo.restrictedProfileParentId);
        }
        return false;
    }

    @Override
    public boolean isPinSet() {
        Callback callback = null;

        Fragment f = getTargetFragment();
        if (f instanceof Callback) {
            callback = (Callback) f;
        }

        if (callback == null && getActivity() instanceof Callback) {
            callback = (Callback) getActivity();
        }

        if (callback != null) {
            UserInfo myUserInfo = UserManager.get(getActivity()).getUserInfo(UserHandle.myUserId());
            return (myUserInfo != null && myUserInfo.isRestricted()) ||
                    callback.hasLockscreenSecurity();
        } else {
            throw new IllegalStateException("Can't call isPinSet when not attached");
        }
    }
}
