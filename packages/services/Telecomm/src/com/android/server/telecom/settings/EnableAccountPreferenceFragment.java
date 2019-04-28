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

package com.android.server.telecom.settings;

import android.content.Context;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;

import com.android.server.telecom.R;

import java.util.List;

/**
 * Lists all call-capable {@link PhoneAccount}s which are not SIM-based and provides a settings
 * for enabling and disabling each of the accounts.  Only enabled accounts will be (1) listed as
 * options with which to place a call and (2) capable of receiving incoming calls through the
 * default dialer UI.
 */
public class EnableAccountPreferenceFragment extends PreferenceFragment {

    private final class AccountSwitchPreference extends SwitchPreference {
        private final PhoneAccount mAccount;

        public AccountSwitchPreference(Context context, PhoneAccount account) {
            super(context);
            mAccount = account;

            setTitle(account.getLabel());
            setSummary(account.getShortDescription());
            Icon icon = account.getIcon();
            if (icon != null) {
                setIcon(icon.loadDrawable(context));
            }
            setChecked(account.isEnabled());
        }

        /** ${inheritDoc} */
        @Override
        protected void onClick() {
            super.onClick();

            mTelecomManager.enablePhoneAccount(mAccount.getAccountHandle(), isChecked());
        }
    }

    private TelecomManager mTelecomManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mTelecomManager = TelecomManager.from(getActivity());
    }


    @Override
    public void onResume() {
        super.onResume();

        PreferenceScreen screen = getPreferenceScreen();
        if (screen != null) {
            screen.removeAll();
        }

        addPreferencesFromResource(R.xml.enable_account_preference);
        screen = getPreferenceScreen();

        List<PhoneAccountHandle> accountHandles =
                mTelecomManager.getCallCapablePhoneAccounts(true /* includeDisabledAccounts */);

        Context context = getActivity();
        for (PhoneAccountHandle handle : accountHandles) {
            PhoneAccount account = mTelecomManager.getPhoneAccount(handle);
            if (account != null) {
                final boolean isSimAccount =
                        0 != (account.getCapabilities() & PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION);
                if (!isSimAccount) {
                    screen.addPreference(new AccountSwitchPreference(context, account));
                }
            }
        }
    }
}
