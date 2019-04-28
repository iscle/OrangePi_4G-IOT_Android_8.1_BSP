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
 * limitations under the License
 */

package com.android.tv.settings.accounts;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SyncAdapterType;
import android.content.SyncInfo;
import android.content.SyncStatusInfo;
import android.content.SyncStatusObserver;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.support.v17.preference.LeanbackPreferenceFragment;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceGroup;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;

import com.android.settingslib.accounts.AuthenticatorHelper;
import com.android.tv.settings.R;

import com.google.android.collect.Lists;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AccountSyncFragment extends LeanbackPreferenceFragment implements
        AuthenticatorHelper.OnAccountsUpdateListener {
    private static final String TAG = "AccountSyncFragment";

    private static final String ARG_ACCOUNT = "account";
    private static final String KEY_REMOVE_ACCOUNT = "remove_account";
    private static final String KEY_SYNC_NOW = "sync_now";
    private static final String KEY_SYNC_ADAPTERS = "sync_adapters";

    private Object mStatusChangeListenerHandle;
    private UserHandle mUserHandle;
    private Account mAccount;
    private ArrayList<SyncAdapterType> mInvisibleAdapters = Lists.newArrayList();

    private PreferenceGroup mSyncCategory;

    private final Handler mHandler = new Handler();
    private SyncStatusObserver mSyncStatusObserver = new SyncStatusObserver() {
        public void onStatusChanged(int which) {
            mHandler.post(new Runnable() {
                public void run() {
                    if (isResumed()) {
                        onSyncStateUpdated();
                    }
                }
            });
        }
    };
    private AuthenticatorHelper mAuthenticatorHelper;

    public static AccountSyncFragment newInstance(Account account) {
        final Bundle b = new Bundle(1);
        prepareArgs(b, account);
        final AccountSyncFragment f = new AccountSyncFragment();
        f.setArguments(b);
        return f;
    }

    public static void prepareArgs(Bundle b, Account account) {
        b.putParcelable(ARG_ACCOUNT, account);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mUserHandle = new UserHandle(UserHandle.myUserId());
        mAccount = getArguments().getParcelable(ARG_ACCOUNT);
        mAuthenticatorHelper = new AuthenticatorHelper(getActivity(), mUserHandle, this);

        super.onCreate(savedInstanceState);

        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "Got account: " + mAccount);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        mStatusChangeListenerHandle = ContentResolver.addStatusChangeListener(
                ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE
                        | ContentResolver.SYNC_OBSERVER_TYPE_STATUS
                        | ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS,
                mSyncStatusObserver);
        onSyncStateUpdated();
        mAuthenticatorHelper.listenToAccountUpdates();
        mAuthenticatorHelper.updateAuthDescriptions(getActivity());
    }

    @Override
    public void onResume() {
        super.onResume();
        mHandler.post(() -> onAccountsUpdate(mUserHandle));
    }

    @Override
    public void onStop() {
        super.onStop();
        ContentResolver.removeStatusChangeListener(mStatusChangeListenerHandle);
        mAuthenticatorHelper.stopListeningToAccountUpdates();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.account_preference, null);

        getPreferenceScreen().setTitle(mAccount.name);

        final Preference removeAccountPref = findPreference(KEY_REMOVE_ACCOUNT);
        removeAccountPref.setIntent(new Intent(getActivity(), RemoveAccountDialog.class)
                .putExtra(AccountSyncActivity.EXTRA_ACCOUNT, mAccount.name));

        mSyncCategory = (PreferenceGroup) findPreference(KEY_SYNC_ADAPTERS);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference instanceof SyncStateSwitchPreference) {
            SyncStateSwitchPreference syncPref = (SyncStateSwitchPreference) preference;
            String authority = syncPref.getAuthority();
            Account account = syncPref.getAccount();
            final int userId = mUserHandle.getIdentifier();
            if (syncPref.isOneTimeSyncMode()) {
                requestOrCancelSync(account, authority, true);
            } else {
                boolean syncOn = syncPref.isChecked();
                boolean oldSyncState = ContentResolver.getSyncAutomaticallyAsUser(account,
                        authority, userId);
                if (syncOn != oldSyncState) {
                    // if we're enabling sync, this will request a sync as well
                    ContentResolver.setSyncAutomaticallyAsUser(account, authority, syncOn, userId);
                    // if the master sync switch is off, the request above will
                    // get dropped.  when the user clicks on this toggle,
                    // we want to force the sync, however.
                    if (!ContentResolver.getMasterSyncAutomaticallyAsUser(userId) || !syncOn) {
                        requestOrCancelSync(account, authority, syncOn);
                    }
                }
            }
            return true;
        } else if (TextUtils.equals(preference.getKey(), KEY_SYNC_NOW)) {
            boolean syncActive = !ContentResolver.getCurrentSyncsAsUser(
                    mUserHandle.getIdentifier()).isEmpty();
            if (syncActive) {
                cancelSyncForEnabledProviders();
            } else {
                startSyncForEnabledProviders();
            }
            return true;
        } else {
            return super.onPreferenceTreeClick(preference);
        }
    }

    private void startSyncForEnabledProviders() {
        requestOrCancelSyncForEnabledProviders(true /* start them */);
        final Activity activity = getActivity();
        if (activity != null) {
            activity.invalidateOptionsMenu();
        }
    }

    private void cancelSyncForEnabledProviders() {
        requestOrCancelSyncForEnabledProviders(false /* cancel them */);
        final Activity activity = getActivity();
        if (activity != null) {
            activity.invalidateOptionsMenu();
        }
    }

    private void requestOrCancelSyncForEnabledProviders(boolean startSync) {
        // sync everything that the user has enabled
        int count = mSyncCategory.getPreferenceCount();
        for (int i = 0; i < count; i++) {
            Preference pref = mSyncCategory.getPreference(i);
            if (! (pref instanceof SyncStateSwitchPreference)) {
                continue;
            }
            SyncStateSwitchPreference syncPref = (SyncStateSwitchPreference) pref;
            if (!syncPref.isChecked()) {
                continue;
            }
            requestOrCancelSync(syncPref.getAccount(), syncPref.getAuthority(), startSync);
        }
        // plus whatever the system needs to sync, e.g., invisible sync adapters
        if (mAccount != null) {
            for (SyncAdapterType syncAdapter : mInvisibleAdapters) {
                requestOrCancelSync(mAccount, syncAdapter.authority, startSync);
            }
        }
    }

    private void requestOrCancelSync(Account account, String authority, boolean flag) {
        if (flag) {
            Bundle extras = new Bundle();
            extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
            ContentResolver.requestSyncAsUser(account, authority, mUserHandle.getIdentifier(),
                    extras);
        } else {
            ContentResolver.cancelSyncAsUser(account, authority, mUserHandle.getIdentifier());
        }
    }

    private boolean isSyncing(List<SyncInfo> currentSyncs, Account account, String authority) {
        for (SyncInfo syncInfo : currentSyncs) {
            if (syncInfo.account.equals(account) && syncInfo.authority.equals(authority)) {
                return true;
            }
        }
        return false;
    }

    private boolean accountExists(Account account) {
        if (account == null) return false;

        Account[] accounts = AccountManager.get(getActivity()).getAccountsByTypeAsUser(
                account.type, mUserHandle);
        for (final Account other : accounts) {
            if (other.equals(account)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onAccountsUpdate(UserHandle userHandle) {
        if (!isResumed()) {
            return;
        }
        if (!accountExists(mAccount)) {
            // The account was deleted
            if (!getFragmentManager().popBackStackImmediate()) {
                getActivity().finish();
            }
            return;
        }
        updateAccountSwitches();
        onSyncStateUpdated();
    }

    private void onSyncStateUpdated() {
        // iterate over all the preferences, setting the state properly for each
        final int userId = mUserHandle.getIdentifier();
        List<SyncInfo> currentSyncs = ContentResolver.getCurrentSyncsAsUser(userId);
//        boolean syncIsFailing = false;

        // Refresh the sync status switches - some syncs may have become active.
        updateAccountSwitches();

        for (int i = 0, count = mSyncCategory.getPreferenceCount(); i < count; i++) {
            Preference pref = mSyncCategory.getPreference(i);
            if (! (pref instanceof SyncStateSwitchPreference)) {
                continue;
            }
            SyncStateSwitchPreference syncPref = (SyncStateSwitchPreference) pref;

            String authority = syncPref.getAuthority();
            Account account = syncPref.getAccount();

            SyncStatusInfo status = ContentResolver.getSyncStatusAsUser(account, authority, userId);
            boolean syncEnabled = ContentResolver.getSyncAutomaticallyAsUser(account, authority,
                    userId);
            boolean authorityIsPending = status != null && status.pending;
            boolean initialSync = status != null && status.initialize;

            boolean activelySyncing = isSyncing(currentSyncs, account, authority);
            boolean lastSyncFailed = status != null
                    && status.lastFailureTime != 0
                    && status.getLastFailureMesgAsInt(0)
                    != ContentResolver.SYNC_ERROR_SYNC_ALREADY_IN_PROGRESS;
            if (!syncEnabled) lastSyncFailed = false;
//            if (lastSyncFailed && !activelySyncing && !authorityIsPending) {
//                syncIsFailing = true;
//            }
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "Update sync status: " + account + " " + authority +
                        " active = " + activelySyncing + " pend =" +  authorityIsPending);
            }

            final long successEndTime = (status == null) ? 0 : status.lastSuccessTime;
            if (!syncEnabled) {
                syncPref.setSummary(R.string.sync_disabled);
            } else if (activelySyncing) {
                syncPref.setSummary(R.string.sync_in_progress);
            } else if (successEndTime != 0) {
                final String timeString = DateUtils.formatDateTime(getActivity(), successEndTime,
                        DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME);
                syncPref.setSummary(getResources().getString(R.string.last_synced, timeString));
            } else {
                syncPref.setSummary("");
            }
            int syncState = ContentResolver.getIsSyncableAsUser(account, authority, userId);

            syncPref.setActive(activelySyncing && (syncState >= 0) &&
                    !initialSync);
            syncPref.setPending(authorityIsPending && (syncState >= 0) &&
                    !initialSync);

            syncPref.setFailed(lastSyncFailed);
            final boolean oneTimeSyncMode = !ContentResolver.getMasterSyncAutomaticallyAsUser(
                    userId);
            syncPref.setOneTimeSyncMode(oneTimeSyncMode);
            syncPref.setChecked(oneTimeSyncMode || syncEnabled);
        }
    }

    private void updateAccountSwitches() {
        mInvisibleAdapters.clear();

        SyncAdapterType[] syncAdapters = ContentResolver.getSyncAdapterTypesAsUser(
                mUserHandle.getIdentifier());
        ArrayList<String> authorities = new ArrayList<>(syncAdapters.length);
        for (SyncAdapterType sa : syncAdapters) {
            // Only keep track of sync adapters for this account
            if (!sa.accountType.equals(mAccount.type)) continue;
            if (sa.isUserVisible()) {
                if (Log.isLoggable(TAG, Log.VERBOSE)) {
                    Log.v(TAG, "updateAccountSwitches: added authority " + sa.authority
                            + " to accountType " + sa.accountType);
                }
                authorities.add(sa.authority);
            } else {
                // keep track of invisible sync adapters, so sync now forces
                // them to sync as well.
                mInvisibleAdapters.add(sa);
            }
        }

        mSyncCategory.removeAll();
        final List<Preference> switches = new ArrayList<>(authorities.size());

        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "looking for sync adapters that match account " + mAccount);
        }
        for (final String authority : authorities) {
            // We could check services here....
            int syncState = ContentResolver.getIsSyncableAsUser(mAccount, authority,
                    mUserHandle.getIdentifier());
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "  found authority " + authority + " " + syncState);
            }
            if (syncState > 0) {
                final Preference pref = createSyncStateSwitch(mAccount, authority);
                switches.add(pref);
            }
        }

        Collections.sort(switches);
        for (final Preference pref : switches) {
            mSyncCategory.addPreference(pref);
        }
    }

    private Preference createSyncStateSwitch(Account account, String authority) {
        final Context themedContext = getPreferenceManager().getContext();
        SyncStateSwitchPreference preference =
                new SyncStateSwitchPreference(themedContext, account, authority);
        preference.setPersistent(false);
        final PackageManager packageManager = getActivity().getPackageManager();
        final ProviderInfo providerInfo = packageManager.resolveContentProviderAsUser(
                authority, 0, mUserHandle.getIdentifier());
        if (providerInfo == null) {
            return null;
        }
        CharSequence providerLabel = providerInfo.loadLabel(packageManager);
        if (TextUtils.isEmpty(providerLabel)) {
            Log.e(TAG, "Provider needs a label for authority '" + authority + "'");
            return null;
        }
        String title = getString(R.string.sync_item_title, providerLabel);
        preference.setTitle(title);
        preference.setKey(authority);
        return preference;
    }

}
