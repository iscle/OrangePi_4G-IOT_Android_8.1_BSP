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

package com.android.tv.settings.accounts;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Fragment;
import android.os.Bundle;
import android.text.TextUtils;

import com.android.tv.settings.BaseSettingsFragment;
import com.android.tv.settings.TvSettingsActivity;

/**
 * Displays the sync settings for a given account.
 */
public class AccountSyncActivity extends TvSettingsActivity {

    public static final String EXTRA_ACCOUNT = "account_name";

    @Override
    protected Fragment createSettingsFragment() {
        String accountName = getIntent().getStringExtra(EXTRA_ACCOUNT);
        Account account = null;
        if (!TextUtils.isEmpty(accountName)) {
            // Search for the account.
            for (Account candidateAccount : AccountManager.get(this).getAccounts()) {
                if (candidateAccount.name.equals(accountName)) {
                    account = candidateAccount;
                    break;
                }
            }
        }

        return SettingsFragment.newInstance(account);
    }

    public static class SettingsFragment extends BaseSettingsFragment {
        private static final String ARG_ACCOUNT = "account";

        public static SettingsFragment newInstance(Account account) {
            final Bundle b = new Bundle(1);
            b.putParcelable(ARG_ACCOUNT, account);
            final SettingsFragment f = new SettingsFragment();
            f.setArguments(b);
            return f;
        }

        @Override
        public void onPreferenceStartInitialScreen() {
            final Account account = getArguments().getParcelable(ARG_ACCOUNT);
            startPreferenceFragment(AccountSyncFragment.newInstance(account));
        }
    }
}
