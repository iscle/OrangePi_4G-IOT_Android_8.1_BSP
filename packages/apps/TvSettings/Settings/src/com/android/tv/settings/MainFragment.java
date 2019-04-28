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

package com.android.tv.settings;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.support.v17.preference.LeanbackPreferenceFragment;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceGroup;
import android.telephony.SignalStrength;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;

import com.android.settingslib.accounts.AuthenticatorHelper;
import com.android.tv.settings.accessories.AccessoryUtils;
import com.android.tv.settings.accessories.BluetoothAccessoryFragment;
import com.android.tv.settings.accounts.AccountSyncFragment;
import com.android.tv.settings.accounts.AddAccountWithTypeActivity;
import com.android.tv.settings.connectivity.ConnectivityListener;
import com.android.tv.settings.device.sound.SoundFragment;
import com.android.tv.settings.system.SecurityFragment;

import java.util.ArrayList;
import java.util.Set;

public class MainFragment extends LeanbackPreferenceFragment {
    private static final String TAG = "MainFragment";

    private static final String KEY_DEVELOPER = "developer";
    private static final String KEY_LOCATION = "location";
    private static final String KEY_SECURITY = "security";
    private static final String KEY_USAGE = "usageAndDiag";
    private static final String KEY_ADD_ACCOUNT = "add_account";
    private static final String KEY_ACCESSORIES = "accessories";
    private static final String KEY_PERSONAL = "personal";
    private static final String KEY_ADD_ACCESSORY = "add_accessory";
    private static final String KEY_NETWORK = "network";
    private static final String KEY_INPUTS = "inputs";
    private static final String KEY_SOUNDS = "sound_effects";
    private static final String KEY_GOOGLE_SETTINGS = "google_settings";
    private static final String KEY_HOME_SETTINGS = "home";
    private static final String KEY_CAST_SETTINGS = "cast";
    private static final String KEY_SPEECH_SETTINGS = "speech";
    private static final String KEY_SEARCH_SETTINGS = "search";
    private static final String KEY_ACCOUNTS_CATEGORY = "accounts";

    private AuthenticatorHelper mAuthenticatorHelper;
    private BluetoothAdapter mBtAdapter;
    private ConnectivityListener mConnectivityListener;

    private boolean mInputSettingNeeded;

    private Preference mDeveloperPref;
    private PreferenceGroup mAccessoriesGroup;
    private PreferenceGroup mAccountsGroup;
    private Preference mAddAccessory;
    private Preference mNetworkPref;
    private Preference mSoundsPref;

    private final BroadcastReceiver mBCMReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateAccessories();
        }
    };

    public static MainFragment newInstance() {
        return new MainFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mAuthenticatorHelper = new AuthenticatorHelper(getContext(),
                new UserHandle(UserHandle.myUserId()), userHandle -> updateAccounts());
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        mConnectivityListener = new ConnectivityListener(getContext(), this::updateWifi);

        final TvInputManager manager = (TvInputManager) getContext().getSystemService(
                Context.TV_INPUT_SERVICE);
        if (manager != null) {
            for (final TvInputInfo input : manager.getTvInputList()) {
                if (input.isPassthroughInput()) {
                    mInputSettingNeeded = true;
                }
            }
        }
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        if (isRestricted()) {
            setPreferencesFromResource(R.xml.restricted_prefs, null);
        } else {
            setPreferencesFromResource(R.xml.main_prefs, null);
        }
        mDeveloperPref = findPreference(KEY_DEVELOPER);
        mAccessoriesGroup = (PreferenceGroup) findPreference(KEY_ACCESSORIES);
        mAddAccessory = findPreference(KEY_ADD_ACCESSORY);
        mNetworkPref = findPreference(KEY_NETWORK);
        mSoundsPref = findPreference(KEY_SOUNDS);
        mAccountsGroup = (PreferenceGroup) findPreference(KEY_ACCOUNTS_CATEGORY);

        final Preference inputPref = findPreference(KEY_INPUTS);
        if (inputPref != null) {
            inputPref.setVisible(mInputSettingNeeded);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        mAuthenticatorHelper.listenToAccountUpdates();

        IntentFilter btChangeFilter = new IntentFilter();
        btChangeFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        btChangeFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        btChangeFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        getContext().registerReceiver(mBCMReceiver, btChangeFilter);
        mConnectivityListener.start();
    }

    @Override
    public void onResume() {
        super.onResume();

        updateAccounts();
        updateAccessories();
        updateDeveloperOptions();
        updateSounds();
        updateGoogleSettings();

        hideIfIntentUnhandled(findPreference(KEY_HOME_SETTINGS));
        hideIfIntentUnhandled(findPreference(KEY_CAST_SETTINGS));
        hideIfIntentUnhandled(findPreference(KEY_USAGE));
        hideIfIntentUnhandled(findPreference(KEY_SPEECH_SETTINGS));
        hideIfIntentUnhandled(findPreference(KEY_SEARCH_SETTINGS));
    }

    @Override
    public void onStop() {
        super.onStop();
        mAuthenticatorHelper.stopListeningToAccountUpdates();
        getContext().unregisterReceiver(mBCMReceiver);
        mConnectivityListener.stop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mConnectivityListener != null) {
            mConnectivityListener.destroy();
        }
    }

    private void hideIfIntentUnhandled(Preference preference) {
        if (preference == null) {
            return;
        }
        preference.setVisible(systemIntentIsHandled(getContext(), preference.getIntent()) != null);
    }

    private boolean isRestricted() {
        return SecurityFragment.isRestrictedProfileInEffect(getContext());
    }

    private void updateAccounts() {
        if (mAccountsGroup == null) {
            return;
        }

        final Set<String> touchedAccounts = new ArraySet<>(mAccountsGroup.getPreferenceCount());

        final AccountManager am = AccountManager.get(getContext());
        final AuthenticatorDescription[] authTypes = am.getAuthenticatorTypes();
        final ArrayList<String> allowableAccountTypes = new ArrayList<>(authTypes.length);
        final Context themedContext = getPreferenceManager().getContext();

        for (AuthenticatorDescription authDesc : authTypes) {
            final Context targetContext;
            try {
                targetContext = getContext().createPackageContext(authDesc.packageName, 0);
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "Authenticator description with bad package name", e);
                continue;
            } catch (SecurityException e) {
                Log.e(TAG, "Security exception loading package resources", e);
                continue;
            }

            // Main title text comes from the authenticator description (e.g. "Google").
            String authTitle = null;
            try {
                authTitle = targetContext.getString(authDesc.labelId);
                if (TextUtils.isEmpty(authTitle)) {
                    authTitle = null;  // Handled later when we add the row.
                }
            } catch (Resources.NotFoundException e) {
                Log.e(TAG, "Authenticator description with bad label id", e);
            }

            // There exist some authenticators which aren't intended to be user-facing.
            // If the authenticator doesn't have a title or an icon, don't present it to
            // the user as an option.
            if (authTitle != null || authDesc.iconId != 0) {
                allowableAccountTypes.add(authDesc.type);
            }

            Account[] accounts = am.getAccountsByType(authDesc.type);
            if (accounts == null || accounts.length == 0) {
                continue;  // No point in continuing; there aren't any accounts to show.
            }

            // Icon URI to be displayed for each account is based on the type of authenticator.
            Drawable authImage = null;
            try {
                authImage = targetContext.getDrawable(authDesc.iconId);
            } catch (Resources.NotFoundException e) {
                Log.e(TAG, "Authenticator has bad resources", e);
            }

            // Display an entry for each installed account we have.
            for (final Account account : accounts) {
                final String key = "account_pref:" + account.type + ":" + account.name;
                Preference preference = findPreference(key);
                if (preference == null) {
                    preference = new Preference(themedContext);
                }
                preference.setTitle(authTitle != null ? authTitle : account.name);
                preference.setIcon(authImage);
                preference.setSummary(authTitle != null ? account.name : null);
                preference.setFragment(AccountSyncFragment.class.getName());
                AccountSyncFragment.prepareArgs(preference.getExtras(), account);

                touchedAccounts.add(key);
                preference.setKey(key);

                mAccountsGroup.addPreference(preference);
            }
        }

        for (int i = 0; i < mAccountsGroup.getPreferenceCount();) {
            final Preference preference = mAccountsGroup.getPreference(i);
            final String key = preference.getKey();
            if (touchedAccounts.contains(key) || TextUtils.equals(KEY_ADD_ACCOUNT, key)) {
                i++;
            } else {
                mAccountsGroup.removePreference(preference);
            }
        }

        // Never allow restricted profile to add accounts.
        final Preference addAccountPref = findPreference(KEY_ADD_ACCOUNT);
        if (addAccountPref != null) {
            addAccountPref.setOrder(Integer.MAX_VALUE);
            if (isRestricted()) {
                addAccountPref.setVisible(false);
            } else {
                Intent i = new Intent().setComponent(new ComponentName("com.android.tv.settings",
                        "com.android.tv.settings.accounts.AddAccountWithTypeActivity"));
                i.putExtra(AddAccountWithTypeActivity.EXTRA_ALLOWABLE_ACCOUNT_TYPES_STRING_ARRAY,
                        allowableAccountTypes.toArray(new String[allowableAccountTypes.size()]));

                // If there are available account types, show the "add account" button.
                addAccountPref.setVisible(!allowableAccountTypes.isEmpty());
                addAccountPref.setIntent(i);
            }
        }
    }

    private void updateAccessories() {
        if (mAccessoriesGroup == null) {
            return;
        }

        if (mBtAdapter == null) {
            mAccessoriesGroup.setVisible(false);
            mAccessoriesGroup.removeAll();
            return;
        }

        final Set<BluetoothDevice> bondedDevices = mBtAdapter.getBondedDevices();
        if (bondedDevices == null) {
            mAccessoriesGroup.setVisible(false);
            mAccessoriesGroup.removeAll();
            return;
        }

        final Context themedContext = getPreferenceManager().getContext();

        final Set<String> touchedKeys = new ArraySet<>(bondedDevices.size() + 1);
        if (mAddAccessory != null) {
            touchedKeys.add(mAddAccessory.getKey());
        }

        for (final BluetoothDevice device : bondedDevices) {
            final String deviceAddress = device.getAddress();
            if (TextUtils.isEmpty(deviceAddress)) {
                Log.w(TAG, "Skipping mysteriously empty bluetooth device");
                continue;
            }

            final String desc = device.isConnected() ? getString(R.string.accessory_connected) :
                    null;
            final String key = "BluetoothDevice:" + deviceAddress;
            touchedKeys.add(key);
            Preference preference = mAccessoriesGroup.findPreference(key);
            if (preference == null) {
                preference = new Preference(themedContext);
                preference.setKey(key);
            }
            final String deviceName = device.getAliasName();
            preference.setTitle(deviceName);
            preference.setSummary(desc);
            final int deviceImgId = AccessoryUtils.getImageIdForDevice(device);
            preference.setIcon(deviceImgId);
            preference.setFragment(BluetoothAccessoryFragment.class.getName());
            BluetoothAccessoryFragment.prepareArgs(
                    preference.getExtras(),
                    deviceAddress,
                    deviceName,
                    deviceImgId);
            mAccessoriesGroup.addPreference(preference);
        }

        for (int i = 0; i < mAccessoriesGroup.getPreferenceCount();) {
            final Preference preference = mAccessoriesGroup.getPreference(i);
            if (touchedKeys.contains(preference.getKey())) {
                i++;
            } else {
                mAccessoriesGroup.removePreference(preference);
            }
        }
    }

    private void updateDeveloperOptions() {
        if (mDeveloperPref == null) {
            return;
        }

        final boolean developerEnabled = PreferenceUtils.isDeveloperEnabled(getContext());
        mDeveloperPref.setVisible(developerEnabled);
    }

    private void updateSounds() {
        if (mSoundsPref == null) {
            return;
        }

        mSoundsPref.setIcon(SoundFragment.getSoundEffectsEnabled(getContext().getContentResolver())
                ? R.drawable.ic_volume_up : R.drawable.ic_volume_off);
    }

    private void updateWifi() {
        if (mNetworkPref == null) {
            return;
        }

        mNetworkPref.setTitle(mConnectivityListener.isEthernetAvailable()
                ? R.string.connectivity_network : R.string.connectivity_wifi);

        if (mConnectivityListener.isCellConnected()) {
            final int signal = mConnectivityListener.getCellSignalStrength();
            switch (signal) {
                case SignalStrength.SIGNAL_STRENGTH_GREAT:
                    mNetworkPref.setIcon(R.drawable.ic_cell_signal_4_white);
                    break;
                case SignalStrength.SIGNAL_STRENGTH_GOOD:
                    mNetworkPref.setIcon(R.drawable.ic_cell_signal_3_white);
                    break;
                case SignalStrength.SIGNAL_STRENGTH_MODERATE:
                    mNetworkPref.setIcon(R.drawable.ic_cell_signal_2_white);
                    break;
                case SignalStrength.SIGNAL_STRENGTH_POOR:
                    mNetworkPref.setIcon(R.drawable.ic_cell_signal_1_white);
                    break;
                case SignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN:
                default:
                    mNetworkPref.setIcon(R.drawable.ic_cell_signal_0_white);
                    break;
            }
        } else if (mConnectivityListener.isEthernetConnected()) {
            mNetworkPref.setIcon(R.drawable.ic_ethernet_white);
        } else if (mConnectivityListener.isWifiConnected()) {
            final int signal = mConnectivityListener.getWifiSignalStrength(5);
            switch (signal) {
                case 4:
                    mNetworkPref.setIcon(R.drawable.ic_wifi_signal_4_white);
                    break;
                case 3:
                    mNetworkPref.setIcon(R.drawable.ic_wifi_signal_3_white);
                    break;
                case 2:
                    mNetworkPref.setIcon(R.drawable.ic_wifi_signal_2_white);
                    break;
                case 1:
                    mNetworkPref.setIcon(R.drawable.ic_wifi_signal_1_white);
                    break;
                case 0:
                default:
                    mNetworkPref.setIcon(R.drawable.ic_wifi_signal_0_white);
                    break;
            }
        } else {
            // TODO: get a not connected icon
            mNetworkPref.setIcon(R.drawable.ic_wifi_signal_4_white);
        }
    }

    private void updateGoogleSettings() {
        final Preference googleSettingsPref = findPreference(KEY_GOOGLE_SETTINGS);
        if (googleSettingsPref != null) {
            final ResolveInfo info = systemIntentIsHandled(getContext(),
                    googleSettingsPref.getIntent());
            googleSettingsPref.setVisible(info != null);
            if (info != null && info.activityInfo != null) {
                googleSettingsPref.setIcon(
                    info.activityInfo.loadIcon(getContext().getPackageManager()));
                googleSettingsPref.setTitle(
                    info.activityInfo.loadLabel(getContext().getPackageManager()));
            }

            final Preference speechPref = findPreference(KEY_SPEECH_SETTINGS);
            if (speechPref != null) {
                speechPref.setVisible(info == null);
            }
            final Preference searchPref = findPreference(KEY_SEARCH_SETTINGS);
            if (searchPref != null) {
                searchPref.setVisible(info == null);
            }
        }
    }

    /**
     * Returns the ResolveInfo for the system activity that matches given intent filter or null if
     * no such activity exists.
     * @param context Context of the caller
     * @param intent The intent matching the desired system app
     * @return ResolveInfo of the matching activity or null if no match exists
     */
    public static ResolveInfo systemIntentIsHandled(Context context, Intent intent) {
        if (intent == null) {
            return null;
        }

        final PackageManager pm = context.getPackageManager();

        for (ResolveInfo info : pm.queryIntentActivities(intent, 0)) {
            if (info.activityInfo != null
                    && (info.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM)
                    == ApplicationInfo.FLAG_SYSTEM) {
                return info;
            }
        }
        return null;
    }
}
