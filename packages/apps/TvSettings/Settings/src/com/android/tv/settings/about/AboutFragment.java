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

package com.android.tv.settings.about;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.SELinux;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserManager;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v17.preference.LeanbackPreferenceFragment;
import android.support.v17.preference.LeanbackSettingsFragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.telephony.CarrierConfigManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.android.internal.telephony.TelephonyProperties;
import com.android.settingslib.DeviceInfoUtils;
import com.android.tv.settings.LongClickPreference;
import com.android.tv.settings.MainFragment;
import com.android.tv.settings.PreferenceUtils;
import com.android.tv.settings.R;
import com.android.tv.settings.name.DeviceManager;

public class AboutFragment extends LeanbackPreferenceFragment implements
        LongClickPreference.OnLongClickListener {
    private static final String TAG = "AboutFragment";

    private static final String KEY_MANUAL = "manual";
    private static final String KEY_REGULATORY_INFO = "regulatory_info";
    private static final String KEY_SYSTEM_UPDATE_SETTINGS = "system_update_settings";
    private static final String PROPERTY_URL_SAFETYLEGAL = "ro.url.safetylegal";
    private static final String PROPERTY_SELINUX_STATUS = "ro.build.selinux";
    private static final String KEY_KERNEL_VERSION = "kernel_version";
    private static final String KEY_BUILD_NUMBER = "build_number";
    private static final String KEY_DEVICE_MODEL = "device_model";
    private static final String KEY_SELINUX_STATUS = "selinux_status";
    private static final String KEY_BASEBAND_VERSION = "baseband_version";
    private static final String KEY_FIRMWARE_VERSION = "firmware_version";
    private static final String KEY_SECURITY_PATCH = "security_patch";
    private static final String KEY_UPDATE_SETTING = "additional_system_update_settings";
    private static final String KEY_EQUIPMENT_ID = "fcc_equipment_id";
    private static final String PROPERTY_EQUIPMENT_ID = "ro.ril.fccid";
    private static final String KEY_DEVICE_FEEDBACK = "device_feedback";
    private static final String KEY_SAFETY_LEGAL = "safetylegal";
    private static final String KEY_DEVICE_NAME = "device_name";
    private static final String KEY_RESTART = "restart";
    private static final String KEY_TUTORIALS = "tutorials";

    static final int TAPS_TO_BE_A_DEVELOPER = 7;

    long[] mHits = new long[3];
    int mDevHitCountdown;
    Toast mDevHitToast;

    private UserManager mUm;

    private final BroadcastReceiver mDeviceNameReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshDeviceName();
        }
    };

    public static AboutFragment newInstance() {
        return new AboutFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mUm = UserManager.get(getActivity());

        super.onCreate(savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.device_info_settings, null);
        final PreferenceScreen screen = getPreferenceScreen();

        refreshDeviceName();
        final Preference deviceNamePref = findPreference(KEY_DEVICE_NAME);
        PreferenceUtils.resolveSystemActivityOrRemove(getActivity(), screen, deviceNamePref, 0);

        final Preference firmwareVersionPref = findPreference(KEY_FIRMWARE_VERSION);
        firmwareVersionPref.setSummary(Build.VERSION.RELEASE);
        firmwareVersionPref.setEnabled(true);

        final Preference securityPatchPref = findPreference(KEY_SECURITY_PATCH);
        final String patch = DeviceInfoUtils.getSecurityPatch();
        if (!TextUtils.isEmpty(patch)) {
            securityPatchPref.setSummary(patch);
        } else {
            removePreference(securityPatchPref);
        }

        final LongClickPreference restartPref = (LongClickPreference) findPreference(KEY_RESTART);
        restartPref.setLongClickListener(this);

        findPreference(KEY_BASEBAND_VERSION).setSummary(
                getSystemPropertySummary(TelephonyProperties.PROPERTY_BASEBAND_VERSION));
        findPreference(KEY_DEVICE_MODEL).setSummary(Build.MODEL + DeviceInfoUtils.getMsvSuffix());
        findPreference(KEY_EQUIPMENT_ID)
                .setSummary(getSystemPropertySummary(PROPERTY_EQUIPMENT_ID));

        final Preference buildNumberPref = findPreference(KEY_BUILD_NUMBER);
        buildNumberPref.setSummary(Build.DISPLAY);
        buildNumberPref.setEnabled(true);
        findPreference(KEY_KERNEL_VERSION).setSummary(DeviceInfoUtils.getFormattedKernelVersion());

        final Preference selinuxPref = findPreference(KEY_SELINUX_STATUS);
        if (!SELinux.isSELinuxEnabled()) {
            selinuxPref.setSummary(R.string.selinux_status_disabled);
        } else if (!SELinux.isSELinuxEnforced()) {
            selinuxPref.setSummary(R.string.selinux_status_permissive);
        }

        // Remove selinux information if property is not present
        if (TextUtils.isEmpty(SystemProperties.get(PROPERTY_SELINUX_STATUS))) {
            removePreference(selinuxPref);
        }

        // Remove Safety information preference if PROPERTY_URL_SAFETYLEGAL is not set
        if (TextUtils.isEmpty(SystemProperties.get(PROPERTY_URL_SAFETYLEGAL))) {
            removePreference(findPreference(KEY_SAFETY_LEGAL));
        }

        // Remove Equipment id preference if FCC ID is not set by RIL
        if (TextUtils.isEmpty(SystemProperties.get(PROPERTY_EQUIPMENT_ID))) {
            removePreference(findPreference(KEY_EQUIPMENT_ID));
        }

        // Remove Baseband version if wifi-only device
        if (isWifiOnly(getActivity())) {
            removePreference(findPreference(KEY_BASEBAND_VERSION));
        }

        // Dont show feedback option if there is no reporter.
        if (TextUtils.isEmpty(DeviceInfoUtils.getFeedbackReporterPackage(getActivity()))) {
            removePreference(findPreference(KEY_DEVICE_FEEDBACK));
        }

        final Preference updateSettingsPref = findPreference(KEY_SYSTEM_UPDATE_SETTINGS);
        if (mUm.isAdminUser()) {
            PreferenceUtils.resolveSystemActivityOrRemove(getActivity(), screen,
                    updateSettingsPref, PreferenceUtils.FLAG_SET_TITLE);
        } else if (updateSettingsPref != null) {
            // Remove for secondary users
            removePreference(updateSettingsPref);
        }

        // Read platform settings for additional system update setting
        if (!getResources().getBoolean(R.bool.config_additional_system_update_setting_enable)) {
            removePreference(findPreference(KEY_UPDATE_SETTING));
        }

        // Remove manual entry if none present.
        if (!getResources().getBoolean(R.bool.config_show_manual)) {
            removePreference(findPreference(KEY_MANUAL));
        }

        // Remove regulatory information if none present.
        final Preference regulatoryPref = findPreference(KEY_REGULATORY_INFO);
        PreferenceUtils.resolveSystemActivityOrRemove(getActivity(), screen, regulatoryPref, 0);
    }

    private void removePreference(@Nullable Preference preference) {
        if (preference != null) {
            getPreferenceScreen().removePreference(preference);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        refreshDeviceName();

        LocalBroadcastManager.getInstance(getContext()).registerReceiver(mDeviceNameReceiver,
                new IntentFilter(DeviceManager.ACTION_DEVICE_NAME_UPDATE));
    }

    @Override
    public void onResume() {
        super.onResume();
        final boolean developerEnabled = Settings.Global.getInt(getActivity().getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                android.os.Build.TYPE.equals("eng") ? 1 : 0) == 1;
        mDevHitCountdown = developerEnabled ? -1 : TAPS_TO_BE_A_DEVELOPER;
        mDevHitToast = null;
        updateTutorials();
    }

    @Override
    public void onStop() {
        super.onStop();
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mDeviceNameReceiver);
    }

    private void refreshDeviceName() {
        final Preference deviceNamePref = findPreference(KEY_DEVICE_NAME);
        if (deviceNamePref != null) {
            deviceNamePref.setSummary(DeviceManager.getDeviceName(getActivity()));
        }
    }

    @Override
    public boolean onPreferenceLongClick(Preference preference) {
        if (TextUtils.equals(preference.getKey(), KEY_RESTART)) {
            if (getCallbackFragment() instanceof LeanbackSettingsFragment) {
                LeanbackSettingsFragment callback =
                        (LeanbackSettingsFragment) getCallbackFragment();
                callback.startImmersiveFragment(
                        RebootConfirmFragment.newInstance(true /* safeMode */));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        switch (preference.getKey()) {
            case KEY_FIRMWARE_VERSION:
                System.arraycopy(mHits, 1, mHits, 0, mHits.length - 1);
                mHits[mHits.length - 1] = SystemClock.uptimeMillis();
                if (mHits[0] >= (SystemClock.uptimeMillis() - 500)) {
                    if (mUm.hasUserRestriction(UserManager.DISALLOW_FUN)) {
                        Log.d(TAG, "Sorry, no fun for you!");
                        return false;
                    }

                    Intent intent = new Intent(Intent.ACTION_MAIN);
                    intent.setClassName("android",
                            com.android.internal.app.PlatLogoActivity.class.getName());
                    try {
                        startActivity(intent);
                    } catch (Exception e) {
                        Log.e(TAG, "Unable to start activity " + intent.toString());
                    }
                }
                break;
            case KEY_BUILD_NUMBER:
                // Don't enable developer options for secondary users.
                if (!mUm.isAdminUser())
                    return true;

                if (mUm.hasUserRestriction(UserManager.DISALLOW_DEBUGGING_FEATURES))
                    return true;

                if (mDevHitCountdown > 0) {
                    mDevHitCountdown--;
                    if (mDevHitCountdown == 0) {
                        Settings.Global.putInt(getActivity().getContentResolver(),
                                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1);
                        if (mDevHitToast != null) {
                            mDevHitToast.cancel();
                        }
                        mDevHitToast = Toast.makeText(getActivity(), R.string.show_dev_on,
                                Toast.LENGTH_LONG);
                        mDevHitToast.show();
                        // This is good time to index the Developer Options
//                    Index.getInstance(
//                            getActivity().getApplicationContext()).updateFromClassNameResource(
//                            DevelopmentSettings.class.getName(), true, true);

                    } else if (mDevHitCountdown > 0
                            && mDevHitCountdown < (TAPS_TO_BE_A_DEVELOPER - 2)) {
                        if (mDevHitToast != null) {
                            mDevHitToast.cancel();
                        }
                        mDevHitToast = Toast
                                .makeText(getActivity(), getResources().getQuantityString(
                                        R.plurals.show_dev_countdown, mDevHitCountdown,
                                        mDevHitCountdown),
                                        Toast.LENGTH_SHORT);
                        mDevHitToast.show();
                    }
                } else if (mDevHitCountdown < 0) {
                    if (mDevHitToast != null) {
                        mDevHitToast.cancel();
                    }
                    mDevHitToast = Toast.makeText(getActivity(), R.string.show_dev_already,
                            Toast.LENGTH_LONG);
                    mDevHitToast.show();
                }
                break;
            case KEY_DEVICE_FEEDBACK:
                sendFeedback();
                break;
            case KEY_SYSTEM_UPDATE_SETTINGS:
                CarrierConfigManager configManager = (CarrierConfigManager)
                        getActivity().getSystemService(Context.CARRIER_CONFIG_SERVICE);
                PersistableBundle b = configManager.getConfig();
                if (b != null &&
                        b.getBoolean(CarrierConfigManager.KEY_CI_ACTION_ON_SYS_UPDATE_BOOL)) {
                    ciActionOnSysUpdate(b);
                }
                break;
        }
        return super.onPreferenceTreeClick(preference);
    }

    /**
     * Trigger client initiated action (send intent) on system update
     */
    private void ciActionOnSysUpdate(PersistableBundle b) {
        String intentStr = b.getString(CarrierConfigManager.
                KEY_CI_ACTION_ON_SYS_UPDATE_INTENT_STRING);
        if (!TextUtils.isEmpty(intentStr)) {
            String extra = b.getString(CarrierConfigManager.
                    KEY_CI_ACTION_ON_SYS_UPDATE_EXTRA_STRING);
            String extraVal = b.getString(CarrierConfigManager.
                    KEY_CI_ACTION_ON_SYS_UPDATE_EXTRA_VAL_STRING);

            Intent intent = new Intent(intentStr);
            if (!TextUtils.isEmpty(extra)) {
                intent.putExtra(extra, extraVal);
            }
            Log.d(TAG, "ciActionOnSysUpdate: broadcasting intent " + intentStr +
                    " with extra " + extra + ", " + extraVal);
            getActivity().getApplicationContext().sendBroadcast(intent);
        }
    }

    private String getSystemPropertySummary(String property) {
        return SystemProperties.get(property,
                getResources().getString(R.string.device_info_default));
    }

    public static boolean isWifiOnly(Context context) {
        ConnectivityManager cm = (ConnectivityManager)context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        return (!cm.isNetworkSupported(ConnectivityManager.TYPE_MOBILE));
    }

    private void sendFeedback() {
        String reporterPackage = DeviceInfoUtils.getFeedbackReporterPackage(getActivity());
        if (TextUtils.isEmpty(reporterPackage)) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_BUG_REPORT);
        intent.setPackage(reporterPackage);
        startActivityForResult(intent, 0);
    }

    private void updateTutorials() {
        final Preference deviceTutorialsPref = findPreference(KEY_TUTORIALS);
        if (deviceTutorialsPref != null) {
            final ResolveInfo info = MainFragment.systemIntentIsHandled(getContext(),
                    deviceTutorialsPref.getIntent());
            deviceTutorialsPref.setVisible(info != null);
            if (info != null) {
                deviceTutorialsPref.setTitle(info.loadLabel(getContext().getPackageManager()));
            }
        }
    }
}
