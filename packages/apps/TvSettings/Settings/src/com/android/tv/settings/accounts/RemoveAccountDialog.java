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
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.app.ActivityManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.support.v17.leanback.widget.GuidanceStylist;
import android.support.v17.leanback.widget.GuidedAction;
import android.util.Log;
import android.widget.Toast;

import com.android.tv.settings.R;

import java.io.IOException;
import java.util.List;

/**
 * OK / Cancel dialog.
 */
public class RemoveAccountDialog extends Activity implements AccountManagerCallback<Bundle> {

    private static final String TAG = "RemoveAccountDialog";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            GuidedStepFragment.addAsRoot(this, RemoveAccountFragment.newInstance(
                    getIntent().getStringExtra(AccountSyncActivity.EXTRA_ACCOUNT)),
                    android.R.id.content);
        }
    }

    @Override
    public void run(AccountManagerFuture<Bundle> future) {
        if (!isResumed()) {
            if (!isFinishing()) {
                finish();
            }
            return;
        }
        try {
            if (!future.getResult().getBoolean(AccountManager.KEY_BOOLEAN_RESULT)) {
                // Wasn't removed, toast this.
                Toast.makeText(this, R.string.account_remove_failed,
                        Toast.LENGTH_LONG)
                        .show();
            }
        } catch (OperationCanceledException | AuthenticatorException | IOException e) {
            Log.e(TAG, "Could not remove", e);
        }
        finish();
    }

    public static class RemoveAccountFragment extends GuidedStepFragment {
        private static final String ARG_ACCOUNT_NAME = "accountName";
        private static final int ID_OK = 1;
        private static final int ID_CANCEL = 0;
        private String mAccountName;
        private boolean mIsRemoving;

        public static RemoveAccountFragment newInstance(String accountName) {
            final RemoveAccountFragment f = new RemoveAccountFragment();
            final Bundle b = new Bundle(1);
            b.putString(ARG_ACCOUNT_NAME, accountName);
            f.setArguments(b);
            return f;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            mAccountName = getArguments().getString(ARG_ACCOUNT_NAME);

            super.onCreate(savedInstanceState);
        }

        @NonNull
        @Override
        public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
            return new GuidanceStylist.Guidance(
                    getString(R.string.account_remove),
                    null,
                    mAccountName,
                    getActivity().getDrawable(R.drawable.ic_delete_132dp));
        }

        @Override
        public void onGuidedActionClicked(GuidedAction action) {
            final RemoveAccountDialog activity = (RemoveAccountDialog) getActivity();
            if (action.getId() == ID_OK) {
                if (ActivityManager.isUserAMonkey()) {
                    // Don't let the monkey remove accounts.
                    activity.finish();
                    return;
                }
                // Block this from happening more than once.
                if (mIsRemoving) {
                    return;
                }
                mIsRemoving = true;
                AccountManager manager = AccountManager.get(activity.getApplicationContext());
                Account account = null;
                for (Account accountLoop : manager.getAccounts()) {
                    if (accountLoop.name.equals(mAccountName)) {
                        account = accountLoop;
                        break;
                    }
                }
                manager.removeAccount(account, activity, activity, new Handler());
            } else {
                activity.finish();
            }
        }

        @Override
        public void onCreateActions(@NonNull List<GuidedAction> actions,
                Bundle savedInstanceState) {
            actions.add(new GuidedAction.Builder()
                    .id(ID_CANCEL)
                    .title(getString(android.R.string.cancel))
                    .build());
            actions.add(new GuidedAction.Builder()
                    .id(ID_OK)
                    .title(getString(android.R.string.ok))
                    .build());
        }
    }
}
