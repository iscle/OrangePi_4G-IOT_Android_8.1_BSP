/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.phone;

import android.app.ActionBar;
import android.app.Activity;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.euicc.EuiccManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TabHost;

import com.android.ims.ImsConfig;
import com.android.ims.ImsManager;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyIntents;
import com.android.phone.settings.PhoneAccountSettingsFragment;
import com.android.settingslib.RestrictedLockUtils;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * "Mobile network settings" screen.  This screen lets you
 * enable/disable mobile data, and control data roaming and other
 * network-specific mobile data features.  It's used on non-voice-capable
 * tablets as well as regular phone devices.
 *
 * Note that this Activity is part of the phone app, even though
 * you reach it from the "Wireless & Networks" section of the main
 * Settings app.  It's not part of the "Call settings" hierarchy that's
 * available from the Phone app (see CallFeaturesSetting for that.)
 */

public class MobileNetworkSettings extends Activity  {

    private enum TabState {
        NO_TABS, UPDATE, DO_NOTHING
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.network_setting);

        FragmentManager fragmentManager = getFragmentManager();
        Fragment fragment = fragmentManager.findFragmentById(R.id.network_setting_content);
        if (fragment == null) {
            fragmentManager.beginTransaction()
                    .add(R.id.network_setting_content, new MobileNetworkFragment())
                    .commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int itemId = item.getItemId();
        switch (itemId) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }


    /**
     * Whether to show the entry point to eUICC settings.
     *
     * <p>We show the entry point on any device which supports eUICC as long as either the eUICC
     * was ever provisioned (that is, at least one profile was ever downloaded onto it), or if
     * the user has enabled development mode.
     */
    public static boolean showEuiccSettings(Context context) {
        EuiccManager euiccManager =
                (EuiccManager) context.getSystemService(Context.EUICC_SERVICE);
        if (!euiccManager.isEnabled()) {
            return false;
        }
        ContentResolver cr = context.getContentResolver();
        return Settings.Global.getInt(cr, Settings.Global.EUICC_PROVISIONED, 0) != 0
                || Settings.Global.getInt(
                cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0;
    }

    /**
     * Whether to show the Enhanced 4G LTE settings.
     *
     * <p>We show this settings if the VoLTE can be enabled by this device and the carrier app
     * doesn't set {@link CarrierConfigManager.KEY_HIDE_ENHANCED_4G_LTE_BOOL} to false.
     */
    public static boolean hideEnhanced4gLteSettings(Context context,
                PersistableBundle carrierConfig) {
        return !(ImsManager.isVolteEnabledByPlatform(context)
            && ImsManager.isVolteProvisionedOnDevice(context))
            || carrierConfig.getBoolean(
            CarrierConfigManager.KEY_HIDE_ENHANCED_4G_LTE_BOOL);

    }

    public static class MobileNetworkFragment extends PreferenceFragment implements
            Preference.OnPreferenceChangeListener, RoamingDialogFragment.RoamingDialogListener {

        // debug data
        private static final String LOG_TAG = "NetworkSettings";
        private static final boolean DBG = true;
        public static final int REQUEST_CODE_EXIT_ECM = 17;

        // Number of active Subscriptions to show tabs
        private static final int TAB_THRESHOLD = 2;

        // Number of last phone number digits shown in Euicc Setting tab
        private static final int NUM_LAST_PHONE_DIGITS = 4;

        // fragment tag for roaming data dialog
        private static final String ROAMING_TAG = "RoamingDialogFragment";

        //String keys for preference lookup
        private static final String BUTTON_PREFERED_NETWORK_MODE = "preferred_network_mode_key";
        private static final String BUTTON_ROAMING_KEY = "button_roaming_key";
        private static final String BUTTON_CDMA_LTE_DATA_SERVICE_KEY = "cdma_lte_data_service_key";
        private static final String BUTTON_ENABLED_NETWORKS_KEY = "enabled_networks_key";
        private static final String BUTTON_4G_LTE_KEY = "enhanced_4g_lte";
        private static final String BUTTON_CELL_BROADCAST_SETTINGS = "cell_broadcast_settings";
        private static final String BUTTON_CARRIER_SETTINGS_KEY = "carrier_settings_key";
        private static final String BUTTON_CDMA_SYSTEM_SELECT_KEY = "cdma_system_select_key";
        private static final String BUTTON_CARRIER_SETTINGS_EUICC_KEY =
                "carrier_settings_euicc_key";
        private static final String BUTTON_WIFI_CALLING_KEY = "wifi_calling_key";
        private static final String BUTTON_VIDEO_CALLING_KEY = "video_calling_key";
        private static final String BUTTON_MOBILE_DATA_ENABLE_KEY = "mobile_data_enable";
        private static final String BUTTON_DATA_USAGE_KEY = "data_usage_summary";
        private static final String BUTTON_ADVANCED_OPTIONS_KEY = "advanced_options";
        private static final String CATEGORY_CALLING_KEY = "calling";
        private static final String CATEGORY_GSM_APN_EXPAND_KEY = "category_gsm_apn_key";
        private static final String CATEGORY_CDMA_APN_EXPAND_KEY = "category_cdma_apn_key";

        private final BroadcastReceiver mPhoneChangeReceiver = new PhoneChangeReceiver();

        static final int preferredNetworkMode = Phone.PREFERRED_NT_MODE;

        //Information about logical "up" Activity
        private static final String UP_ACTIVITY_PACKAGE = "com.android.settings";
        private static final String UP_ACTIVITY_CLASS =
                "com.android.settings.Settings$WirelessSettingsActivity";

        //Information that needs to save into Bundle.
        private static final String EXPAND_ADVANCED_FIELDS = "expand_advanced_fields";

        private SubscriptionManager mSubscriptionManager;
        private TelephonyManager mTelephonyManager;

        //UI objects
        private AdvancedOptionsPreference mAdvancedOptions;
        private ListPreference mButtonPreferredNetworkMode;
        private ListPreference mButtonEnabledNetworks;
        private RestrictedSwitchPreference mButtonDataRoam;
        private SwitchPreference mButton4glte;
        private Preference mLteDataServicePref;
        private Preference mEuiccSettingsPref;
        private PreferenceCategory mCallingCategory;
        private Preference mWiFiCallingPref;
        private SwitchPreference mVideoCallingPref;
        private NetworkSelectListPreference mButtonNetworkSelect;
        private MobileDataPreference mMobileDataPref;
        private DataUsagePreference mDataUsagePref;

        private static final String iface = "rmnet0"; //TODO: this will go away
        private List<SubscriptionInfo> mActiveSubInfos;

        private UserManager mUm;
        private Phone mPhone;
        private MyHandler mHandler;
        private boolean mOkClicked;
        private boolean mExpandAdvancedFields;

        // We assume the the value returned by mTabHost.getCurrentTab() == slotId
        private TabHost mTabHost;

        //GsmUmts options and Cdma options
        GsmUmtsOptions mGsmUmtsOptions;
        CdmaOptions mCdmaOptions;

        private Preference mClickedPreference;
        private boolean mShow4GForLTE;
        private boolean mIsGlobalCdma;
        private boolean mUnavailable;

        private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
            /*
             * Enable/disable the 'Enhanced 4G LTE Mode' when in/out of a call
             * and depending on TTY mode and TTY support over VoLTE.
             * @see android.telephony.PhoneStateListener#onCallStateChanged(int,
             * java.lang.String)
             */
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                if (DBG) log("PhoneStateListener.onCallStateChanged: state=" + state);

                Activity activity = getActivity();
                if (activity == null) {
                    return;
                }

                int subId = mPhone != null
                        ? mPhone.getSubId() : SubscriptionManager.INVALID_SUBSCRIPTION_ID;
                PersistableBundle carrierConfig =
                        PhoneGlobals.getInstance().getCarrierConfigForSubId(subId);
                boolean enabled = is4gLtePrefEnabled(activity.getApplicationContext(),
                        carrierConfig);
                Preference pref = getPreferenceScreen().findPreference(BUTTON_4G_LTE_KEY);
                if (pref != null) pref.setEnabled(enabled && hasActiveSubscriptions());

            }
        };

        /**
         * Service connection code for the NetworkQueryService.
         * Handles the work of binding to a local object so that we can make
         * the appropriate service calls.
         */

        /** Local service interface */
        private INetworkQueryService mNetworkQueryService = null;

        private void setNetworkQueryService() {
            mButtonNetworkSelect = (NetworkSelectListPreference) getPreferenceScreen()
                    .findPreference(NetworkOperators.BUTTON_NETWORK_SELECT_KEY);
            if (mButtonNetworkSelect != null) {
                mButtonNetworkSelect.setNetworkQueryService(mNetworkQueryService);
            }

        }
        /** Service connection */
        private final ServiceConnection mNetworkQueryServiceConnection = new ServiceConnection() {

            /** Handle the task of binding the local object to the service */
            public void onServiceConnected(ComponentName className, IBinder service) {
                if (DBG) log("connection created, binding local service.");
                mNetworkQueryService = ((NetworkQueryService.LocalBinder) service).getService();
                setNetworkQueryService();
            }

            /** Handle the task of cleaning up the local binding */
            public void onServiceDisconnected(ComponentName className) {
                if (DBG) log("connection disconnected, cleaning local binding.");
                mNetworkQueryService = null;
                setNetworkQueryService();
            }
        };

        private void bindNetworkQueryService() {
            getContext().startService(new Intent(getContext(), NetworkQueryService.class));
            getContext().bindService(new Intent(getContext(), NetworkQueryService.class).setAction(
                        NetworkQueryService.ACTION_LOCAL_BINDER),
                        mNetworkQueryServiceConnection, Context.BIND_AUTO_CREATE);
        }

        private void unbindNetworkQueryService() {
            // unbind the service.
            getContext().unbindService(mNetworkQueryServiceConnection);
        }

        @Override
        public void onPositiveButtonClick(DialogFragment dialog) {
            mPhone.setDataRoamingEnabled(true);
            mButtonDataRoam.setChecked(true);
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            if (getListView() != null) {
                getListView().setDivider(null);
            }
        }

        /**
         * Invoked on each preference click in this hierarchy, overrides
         * PreferenceActivity's implementation.  Used to make sure we track the
         * preference click events.
         */
        @Override
        public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
                                             Preference preference) {
            sendMetricsEventPreferenceClicked(preferenceScreen, preference);

            /** TODO: Refactor and get rid of the if's using subclasses */
            final int phoneSubId = mPhone.getSubId();
            if (preference.getKey().equals(BUTTON_4G_LTE_KEY)) {
                return true;
            } else if (mGsmUmtsOptions != null &&
                    mGsmUmtsOptions.preferenceTreeClick(preference) == true) {
                return true;
            } else if (mCdmaOptions != null &&
                    mCdmaOptions.preferenceTreeClick(preference) == true) {
                if (mPhone.isInEcm()) {

                    mClickedPreference = preference;

                    // In ECM mode launch ECM app dialog
                    startActivityForResult(
                            new Intent(TelephonyIntents.ACTION_SHOW_NOTICE_ECM_BLOCK_OTHERS, null),
                            REQUEST_CODE_EXIT_ECM);
                }
                return true;
            } else if (preference == mButtonPreferredNetworkMode) {
                //displays the value taken from the Settings.System
                int settingsNetworkMode = android.provider.Settings.Global.getInt(
                        mPhone.getContext().getContentResolver(),
                        android.provider.Settings.Global.PREFERRED_NETWORK_MODE + phoneSubId,
                        preferredNetworkMode);
                mButtonPreferredNetworkMode.setValue(Integer.toString(settingsNetworkMode));
                return true;
            } else if (preference == mLteDataServicePref) {
                String tmpl = android.provider.Settings.Global.getString(
                        getActivity().getContentResolver(),
                        android.provider.Settings.Global.SETUP_PREPAID_DATA_SERVICE_URL);
                if (!TextUtils.isEmpty(tmpl)) {
                    String imsi = mTelephonyManager.getSubscriberId();
                    if (imsi == null) {
                        imsi = "";
                    }
                    final String url = TextUtils.isEmpty(tmpl) ? null
                            : TextUtils.expandTemplate(tmpl, imsi).toString();
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                } else {
                    android.util.Log.e(LOG_TAG, "Missing SETUP_PREPAID_DATA_SERVICE_URL");
                }
                return true;
            }  else if (preference == mButtonEnabledNetworks) {
                int settingsNetworkMode = android.provider.Settings.Global.getInt(
                        mPhone.getContext().getContentResolver(),
                        android.provider.Settings.Global.PREFERRED_NETWORK_MODE + phoneSubId,
                        preferredNetworkMode);
                mButtonEnabledNetworks.setValue(Integer.toString(settingsNetworkMode));
                return true;
            } else if (preference == mButtonDataRoam) {
                // Do not disable the preference screen if the user clicks Data roaming.
                return true;
            } else if (preference == mEuiccSettingsPref) {
                Intent intent = new Intent(EuiccManager.ACTION_MANAGE_EMBEDDED_SUBSCRIPTIONS);
                startActivity(intent);
                return true;
            } else if (preference == mWiFiCallingPref || preference == mVideoCallingPref
                    || preference == mMobileDataPref || preference == mDataUsagePref) {
                return false;
            } else if (preference == mAdvancedOptions) {
                mExpandAdvancedFields = true;
                updateBody();
                return true;
            } else {
                // if the button is anything but the simple toggle preference,
                // we'll need to disable all preferences to reject all click
                // events until the sub-activity's UI comes up.
                preferenceScreen.setEnabled(false);
                // Let the intents be launched by the Preference manager
                return false;
            }
        }

        private final SubscriptionManager.OnSubscriptionsChangedListener
                mOnSubscriptionsChangeListener
                = new SubscriptionManager.OnSubscriptionsChangedListener() {
            @Override
            public void onSubscriptionsChanged() {
                if (DBG) log("onSubscriptionsChanged:");
                initializeSubscriptions();
            }
        };

        private void initializeSubscriptions() {
            final Activity activity = getActivity();
            if (activity == null || activity.isDestroyed()) {
                // Process preferences in activity only if its not destroyed
                return;
            }
            int currentTab = 0;
            if (DBG) log("initializeSubscriptions:+");

            // Before updating the the active subscription list check
            // if tab updating is needed as the list is changing.
            List<SubscriptionInfo> sil = mSubscriptionManager.getActiveSubscriptionInfoList();
            MobileNetworkSettings.TabState state = isUpdateTabsNeeded(sil);

            // Update to the active subscription list
            mActiveSubInfos.clear();
            if (sil != null) {
                mActiveSubInfos.addAll(sil);
                // If there is only 1 sim then currenTab should represent slot no. of the sim.
                if (sil.size() == 1) {
                    currentTab = sil.get(0).getSimSlotIndex();
                }
            }

            switch (state) {
                case UPDATE: {
                    if (DBG) log("initializeSubscriptions: UPDATE");
                    currentTab = mTabHost != null ? mTabHost.getCurrentTab() : 0;

                    mTabHost = (TabHost) getActivity().findViewById(android.R.id.tabhost);
                    mTabHost.setup();

                    // Update the tabName. Since the mActiveSubInfos are in slot order
                    // we can iterate though the tabs and subscription info in one loop. But
                    // we need to handle the case where a slot may be empty.

                    Iterator<SubscriptionInfo> siIterator = mActiveSubInfos.listIterator();
                    SubscriptionInfo si = siIterator.hasNext() ? siIterator.next() : null;
                    for (int simSlotIndex = 0; simSlotIndex  < mActiveSubInfos.size();
                         simSlotIndex++) {
                        String tabName;
                        if (si != null && si.getSimSlotIndex() == simSlotIndex) {
                            // Slot is not empty and we match
                            tabName = String.valueOf(si.getDisplayName());
                            si = siIterator.hasNext() ? siIterator.next() : null;
                        } else {
                            // Slot is empty, set name to unknown
                            tabName = getResources().getString(R.string.unknown);
                        }
                        if (DBG) {
                            log("initializeSubscriptions:tab=" + simSlotIndex + " name=" + tabName);
                        }

                        mTabHost.addTab(buildTabSpec(String.valueOf(simSlotIndex), tabName));
                    }

                    mTabHost.setOnTabChangedListener(mTabListener);
                    mTabHost.setCurrentTab(currentTab);
                    break;
                }
                case NO_TABS: {
                    if (DBG) log("initializeSubscriptions: NO_TABS");

                    if (mTabHost != null) {
                        mTabHost.clearAllTabs();
                        mTabHost = null;
                    }
                    break;
                }
                case DO_NOTHING: {
                    if (DBG) log("initializeSubscriptions: DO_NOTHING");
                    if (mTabHost != null) {
                        currentTab = mTabHost.getCurrentTab();
                    }
                    break;
                }
            }
            updatePhone(currentTab);
            updateBody();
            if (DBG) log("initializeSubscriptions:-");
        }

        private MobileNetworkSettings.TabState isUpdateTabsNeeded(List<SubscriptionInfo> newSil) {
            TabState state = MobileNetworkSettings.TabState.DO_NOTHING;
            if (newSil == null) {
                if (mActiveSubInfos.size() >= TAB_THRESHOLD) {
                    if (DBG) log("isUpdateTabsNeeded: NO_TABS, size unknown and was tabbed");
                    state = MobileNetworkSettings.TabState.NO_TABS;
                }
            } else if (newSil.size() < TAB_THRESHOLD && mActiveSubInfos.size() >= TAB_THRESHOLD) {
                if (DBG) log("isUpdateTabsNeeded: NO_TABS, size went to small");
                state = MobileNetworkSettings.TabState.NO_TABS;
            } else if (newSil.size() >= TAB_THRESHOLD && mActiveSubInfos.size() < TAB_THRESHOLD) {
                if (DBG) log("isUpdateTabsNeeded: UPDATE, size changed");
                state = MobileNetworkSettings.TabState.UPDATE;
            } else if (newSil.size() >= TAB_THRESHOLD) {
                Iterator<SubscriptionInfo> siIterator = mActiveSubInfos.iterator();
                for(SubscriptionInfo newSi : newSil) {
                    SubscriptionInfo curSi = siIterator.next();
                    if (!newSi.getDisplayName().equals(curSi.getDisplayName())) {
                        if (DBG) log("isUpdateTabsNeeded: UPDATE, new name="
                                + newSi.getDisplayName());
                        state = MobileNetworkSettings.TabState.UPDATE;
                        break;
                    }
                }
            }
            if (DBG) {
                Log.i(LOG_TAG, "isUpdateTabsNeeded:- " + state
                        + " newSil.size()=" + ((newSil != null) ? newSil.size() : 0)
                        + " mActiveSubInfos.size()=" + mActiveSubInfos.size());
            }
            return state;
        }

        private TabHost.OnTabChangeListener mTabListener = new TabHost.OnTabChangeListener() {
            @Override
            public void onTabChanged(String tabId) {
                if (DBG) log("onTabChanged:");
                // The User has changed tab; update the body.
                updatePhone(Integer.parseInt(tabId));
                updateBody();
            }
        };

        private void updatePhone(int slotId) {
            final SubscriptionInfo sir = mSubscriptionManager
                    .getActiveSubscriptionInfoForSimSlotIndex(slotId);
            if (sir != null) {
                mPhone = PhoneFactory.getPhone(
                        SubscriptionManager.getPhoneId(sir.getSubscriptionId()));
            }
            if (mPhone == null) {
                // Do the best we can
                mPhone = PhoneGlobals.getPhone();
            }
            Log.i(LOG_TAG, "updatePhone:- slotId=" + slotId + " sir=" + sir);
        }

        private TabHost.TabContentFactory mEmptyTabContent = new TabHost.TabContentFactory() {
            @Override
            public View createTabContent(String tag) {
                return new View(mTabHost.getContext());
            }
        };

        private TabHost.TabSpec buildTabSpec(String tag, String title) {
            return mTabHost.newTabSpec(tag).setIndicator(title).setContent(
                    mEmptyTabContent);
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);

            // If advanced fields are already expanded, we save it and expand it
            // when it's re-created.
            outState.putBoolean(EXPAND_ADVANCED_FIELDS, mExpandAdvancedFields);
        }

        @Override
        public void onCreate(Bundle icicle) {
            Log.i(LOG_TAG, "onCreate:+");
            super.onCreate(icicle);

            final Activity activity = getActivity();
            if (activity == null || activity.isDestroyed()) {
                Log.e(LOG_TAG, "onCreate:- with no valid activity.");
                return;
            }

            mHandler = new MyHandler();
            mUm = (UserManager) activity.getSystemService(Context.USER_SERVICE);
            mSubscriptionManager = SubscriptionManager.from(activity);
            mTelephonyManager = (TelephonyManager) activity.getSystemService(
                            Context.TELEPHONY_SERVICE);

            if (icicle != null) {
                mExpandAdvancedFields = icicle.getBoolean(EXPAND_ADVANCED_FIELDS, false);
            }

            bindNetworkQueryService();

            if (mUm.hasUserRestriction(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS)) {
                mUnavailable = true;
                activity.setContentView(R.layout.telephony_disallowed_preference_screen);
                return;
            }

            addPreferencesFromResource(R.xml.network_setting_fragment);

            mButton4glte = (SwitchPreference)findPreference(BUTTON_4G_LTE_KEY);
            mButton4glte.setOnPreferenceChangeListener(this);

            mCallingCategory = (PreferenceCategory) findPreference(CATEGORY_CALLING_KEY);
            mWiFiCallingPref = findPreference(BUTTON_WIFI_CALLING_KEY);
            mVideoCallingPref = (SwitchPreference) findPreference(BUTTON_VIDEO_CALLING_KEY);
            mMobileDataPref = (MobileDataPreference) findPreference(BUTTON_MOBILE_DATA_ENABLE_KEY);
            mDataUsagePref = (DataUsagePreference) findPreference(BUTTON_DATA_USAGE_KEY);

            try {
                Context con = activity.createPackageContext("com.android.systemui", 0);
                int id = con.getResources().getIdentifier("config_show4GForLTE",
                        "bool", "com.android.systemui");
                mShow4GForLTE = con.getResources().getBoolean(id);
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(LOG_TAG, "NameNotFoundException for show4GFotLTE");
                mShow4GForLTE = false;
            }

            //get UI object references
            PreferenceScreen prefSet = getPreferenceScreen();

            mButtonDataRoam = (RestrictedSwitchPreference) prefSet.findPreference(
                    BUTTON_ROAMING_KEY);
            mButtonPreferredNetworkMode = (ListPreference) prefSet.findPreference(
                    BUTTON_PREFERED_NETWORK_MODE);
            mButtonEnabledNetworks = (ListPreference) prefSet.findPreference(
                    BUTTON_ENABLED_NETWORKS_KEY);
            mAdvancedOptions = (AdvancedOptionsPreference) prefSet.findPreference(
                    BUTTON_ADVANCED_OPTIONS_KEY);
            mButtonDataRoam.setOnPreferenceChangeListener(this);

            mLteDataServicePref = prefSet.findPreference(BUTTON_CDMA_LTE_DATA_SERVICE_KEY);

            mEuiccSettingsPref = prefSet.findPreference(BUTTON_CARRIER_SETTINGS_EUICC_KEY);
            mEuiccSettingsPref.setOnPreferenceChangeListener(this);

            // Initialize mActiveSubInfo
            int max = mSubscriptionManager.getActiveSubscriptionInfoCountMax();
            mActiveSubInfos = new ArrayList<SubscriptionInfo>(max);

            IntentFilter intentFilter = new IntentFilter(
                    TelephonyIntents.ACTION_RADIO_TECHNOLOGY_CHANGED);
            activity.registerReceiver(mPhoneChangeReceiver, intentFilter);

            Log.i(LOG_TAG, "onCreate:-");
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            return inflater.inflate(com.android.internal.R.layout.common_tab_settings,
                    container, false);
        }

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);
            initializeSubscriptions();
        }

        private class PhoneChangeReceiver extends BroadcastReceiver {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.i(LOG_TAG, "onReceive:");
                // When the radio changes (ex: CDMA->GSM), refresh all options.
                updateBody();
            }
        }

        @Override
        public void onDestroy() {
            unbindNetworkQueryService();
            super.onDestroy();
            if (getActivity() != null) {
                getActivity().unregisterReceiver(mPhoneChangeReceiver);
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            Log.i(LOG_TAG, "onResume:+");

            if (mUnavailable) {
                Log.i(LOG_TAG, "onResume:- ignore mUnavailable == false");
                return;
            }

            final Activity activity = getActivity();
            if (activity == null || activity.isDestroyed()) {
                Log.e(LOG_TAG, "onResume:- with no valid activity.");
                return;
            }
            // upon resumption from the sub-activity, make sure we re-enable the
            // preferences.
            getPreferenceScreen().setEnabled(true);

            // Set UI state in onResume because a user could go home, launch some
            // app to change this setting's backend, and re-launch this settings app
            // and the UI state would be inconsistent with actual state
            mButtonDataRoam.setChecked(mPhone.getDataRoamingEnabled());

            if (getPreferenceScreen().findPreference(BUTTON_PREFERED_NETWORK_MODE) != null
                    || getPreferenceScreen().findPreference(BUTTON_ENABLED_NETWORKS_KEY) != null)  {
                updatePreferredNetworkUIFromDb();
            }

            if (ImsManager.isVolteEnabledByPlatform(activity)
                    && ImsManager.isVolteProvisionedOnDevice(activity)) {
                mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
            }

            // NOTE: Buttons will be enabled/disabled in mPhoneStateListener
            boolean enh4glteMode = ImsManager.isEnhanced4gLteModeSettingEnabledByUser(activity)
                    && ImsManager.isNonTtyOrTtyOnVolteEnabled(activity);
            mButton4glte.setChecked(enh4glteMode);

            // Video calling and WiFi calling state might have changed.
            updateCallingCategory();

            mSubscriptionManager.addOnSubscriptionsChangedListener(mOnSubscriptionsChangeListener);

            Log.i(LOG_TAG, "onResume:-");

        }

        private boolean hasActiveSubscriptions() {
            return mActiveSubInfos.size() > 0;
        }

        private void updateBodyBasicFields(Activity activity, PreferenceScreen prefSet,
                int phoneSubId, boolean hasActiveSubscriptions) {
            Context context = activity.getApplicationContext();

            ActionBar actionBar = activity.getActionBar();
            if (actionBar != null) {
                // android.R.id.home will be triggered in onOptionsItemSelected()
                actionBar.setDisplayHomeAsUpEnabled(true);
            }

            prefSet.addPreference(mMobileDataPref);
            prefSet.addPreference(mButtonDataRoam);
            prefSet.addPreference(mDataUsagePref);

            // Customized preferences needs to be initialized with subId.
            mMobileDataPref.initialize(phoneSubId);
            mDataUsagePref.initialize(phoneSubId);

            mMobileDataPref.setEnabled(hasActiveSubscriptions);
            mButtonDataRoam.setEnabled(hasActiveSubscriptions);
            mDataUsagePref.setEnabled(hasActiveSubscriptions);

            // Initialize states of mButtonDataRoam.
            mButtonDataRoam.setChecked(mPhone.getDataRoamingEnabled());
            mButtonDataRoam.setDisabledByAdmin(false);
            if (mButtonDataRoam.isEnabled()) {
                if (RestrictedLockUtils.hasBaseUserRestriction(context,
                        UserManager.DISALLOW_DATA_ROAMING, UserHandle.myUserId())) {
                    mButtonDataRoam.setEnabled(false);
                } else {
                    mButtonDataRoam.checkRestrictionAndSetDisabled(
                            UserManager.DISALLOW_DATA_ROAMING);
                }
            }
        }

        private void updateBody() {
            final Activity activity = getActivity();
            final PreferenceScreen prefSet = getPreferenceScreen();
            final int phoneSubId = mPhone.getSubId();
            final boolean hasActiveSubscriptions = hasActiveSubscriptions();

            if (activity == null || activity.isDestroyed()) {
                Log.e(LOG_TAG, "updateBody with no valid activity.");
                return;
            }

            if (prefSet == null) {
                Log.e(LOG_TAG, "updateBody with no null prefSet.");
                return;
            }

            prefSet.removeAll();

            updateBodyBasicFields(activity, prefSet, phoneSubId, hasActiveSubscriptions);

            if (mExpandAdvancedFields) {
                updateBodyAdvancedFields(activity, prefSet, phoneSubId, hasActiveSubscriptions);
            } else {
                prefSet.addPreference(mAdvancedOptions);
            }
        }

        private void updateBodyAdvancedFields(Activity activity, PreferenceScreen prefSet,
                int phoneSubId, boolean hasActiveSubscriptions) {
            boolean isLteOnCdma = mPhone.getLteOnCdmaMode() == PhoneConstants.LTE_ON_CDMA_TRUE;

            if (DBG) {
                log("updateBody: isLteOnCdma=" + isLteOnCdma + " phoneSubId=" + phoneSubId);
            }

            prefSet.addPreference(mButtonPreferredNetworkMode);
            prefSet.addPreference(mButtonEnabledNetworks);
            prefSet.addPreference(mButton4glte);

            if (showEuiccSettings(getActivity())) {
                prefSet.addPreference(mEuiccSettingsPref);
                String spn = mTelephonyManager.getSimOperatorName();
                if (TextUtils.isEmpty(spn)) {
                    mEuiccSettingsPref.setSummary(null);
                } else {
                    mEuiccSettingsPref.setSummary(spn);
                }
            }

            int settingsNetworkMode = android.provider.Settings.Global.getInt(
                    mPhone.getContext().getContentResolver(),
                    android.provider.Settings.Global.PREFERRED_NETWORK_MODE + phoneSubId,
                    preferredNetworkMode);

            PersistableBundle carrierConfig =
                    PhoneGlobals.getInstance().getCarrierConfigForSubId(mPhone.getSubId());
            mIsGlobalCdma = isLteOnCdma
                    && carrierConfig.getBoolean(CarrierConfigManager.KEY_SHOW_CDMA_CHOICES_BOOL);
            if (carrierConfig.getBoolean(
                    CarrierConfigManager.KEY_HIDE_CARRIER_NETWORK_SETTINGS_BOOL)) {
                prefSet.removePreference(mButtonPreferredNetworkMode);
                prefSet.removePreference(mButtonEnabledNetworks);
                prefSet.removePreference(mLteDataServicePref);
            } else if (carrierConfig.getBoolean(CarrierConfigManager
                    .KEY_HIDE_PREFERRED_NETWORK_TYPE_BOOL)
                    && !mPhone.getServiceState().getRoaming()
                    && mPhone.getServiceState().getDataRegState()
                    == ServiceState.STATE_IN_SERVICE) {
                prefSet.removePreference(mButtonPreferredNetworkMode);
                prefSet.removePreference(mButtonEnabledNetworks);

                final int phoneType = mPhone.getPhoneType();
                if (phoneType == PhoneConstants.PHONE_TYPE_CDMA) {
                    updateCdmaOptions(this, prefSet, mPhone);
                } else if (phoneType == PhoneConstants.PHONE_TYPE_GSM) {
                    updateGsmUmtsOptions(this, prefSet, phoneSubId, mNetworkQueryService);
                } else {
                    throw new IllegalStateException("Unexpected phone type: " + phoneType);
                }
                // Since pref is being hidden from user, set network mode to default
                // in case it is currently something else. That is possible if user
                // changed the setting while roaming and is now back to home network.
                settingsNetworkMode = preferredNetworkMode;
            } else if (carrierConfig.getBoolean(
                    CarrierConfigManager.KEY_WORLD_PHONE_BOOL) == true) {
                prefSet.removePreference(mButtonEnabledNetworks);
                // set the listener for the mButtonPreferredNetworkMode list preference so we can issue
                // change Preferred Network Mode.
                mButtonPreferredNetworkMode.setOnPreferenceChangeListener(this);

                updateCdmaOptions(this, prefSet, mPhone);
                updateGsmUmtsOptions(this, prefSet, phoneSubId, mNetworkQueryService);
            } else {
                prefSet.removePreference(mButtonPreferredNetworkMode);
                final int phoneType = mPhone.getPhoneType();
                if (phoneType == PhoneConstants.PHONE_TYPE_CDMA) {
                    int lteForced = android.provider.Settings.Global.getInt(
                            mPhone.getContext().getContentResolver(),
                            android.provider.Settings.Global.LTE_SERVICE_FORCED + mPhone.getSubId(),
                            0);

                    if (isLteOnCdma) {
                        if (lteForced == 0) {
                            mButtonEnabledNetworks.setEntries(
                                    R.array.enabled_networks_cdma_choices);
                            mButtonEnabledNetworks.setEntryValues(
                                    R.array.enabled_networks_cdma_values);
                        } else {
                            switch (settingsNetworkMode) {
                                case Phone.NT_MODE_CDMA:
                                case Phone.NT_MODE_CDMA_NO_EVDO:
                                case Phone.NT_MODE_EVDO_NO_CDMA:
                                    mButtonEnabledNetworks.setEntries(
                                            R.array.enabled_networks_cdma_no_lte_choices);
                                    mButtonEnabledNetworks.setEntryValues(
                                            R.array.enabled_networks_cdma_no_lte_values);
                                    break;
                                case Phone.NT_MODE_GLOBAL:
                                case Phone.NT_MODE_LTE_CDMA_AND_EVDO:
                                case Phone.NT_MODE_LTE_CDMA_EVDO_GSM_WCDMA:
                                case Phone.NT_MODE_LTE_ONLY:
                                    mButtonEnabledNetworks.setEntries(
                                            R.array.enabled_networks_cdma_only_lte_choices);
                                    mButtonEnabledNetworks.setEntryValues(
                                            R.array.enabled_networks_cdma_only_lte_values);
                                    break;
                                default:
                                    mButtonEnabledNetworks.setEntries(
                                            R.array.enabled_networks_cdma_choices);
                                    mButtonEnabledNetworks.setEntryValues(
                                            R.array.enabled_networks_cdma_values);
                                    break;
                            }
                        }
                    }
                    updateCdmaOptions(this, prefSet, mPhone);

                } else if (phoneType == PhoneConstants.PHONE_TYPE_GSM) {
                    if (isSupportTdscdma()) {
                        mButtonEnabledNetworks.setEntries(
                                R.array.enabled_networks_tdscdma_choices);
                        mButtonEnabledNetworks.setEntryValues(
                                R.array.enabled_networks_tdscdma_values);
                    } else if (!carrierConfig.getBoolean(CarrierConfigManager.KEY_PREFER_2G_BOOL)
                            && !getResources().getBoolean(R.bool.config_enabled_lte)) {
                        mButtonEnabledNetworks.setEntries(
                                R.array.enabled_networks_except_gsm_lte_choices);
                        mButtonEnabledNetworks.setEntryValues(
                                R.array.enabled_networks_except_gsm_lte_values);
                    } else if (!carrierConfig.getBoolean(CarrierConfigManager.KEY_PREFER_2G_BOOL)) {
                        int select = (mShow4GForLTE == true) ?
                                R.array.enabled_networks_except_gsm_4g_choices
                                : R.array.enabled_networks_except_gsm_choices;
                        mButtonEnabledNetworks.setEntries(select);
                        mButtonEnabledNetworks.setEntryValues(
                                R.array.enabled_networks_except_gsm_values);
                    } else if (!getResources().getBoolean(R.bool.config_enabled_lte)) {
                        mButtonEnabledNetworks.setEntries(
                                R.array.enabled_networks_except_lte_choices);
                        mButtonEnabledNetworks.setEntryValues(
                                R.array.enabled_networks_except_lte_values);
                    } else if (mIsGlobalCdma) {
                        mButtonEnabledNetworks.setEntries(
                                R.array.enabled_networks_cdma_choices);
                        mButtonEnabledNetworks.setEntryValues(
                                R.array.enabled_networks_cdma_values);
                    } else {
                        int select = (mShow4GForLTE == true) ? R.array.enabled_networks_4g_choices
                                : R.array.enabled_networks_choices;
                        mButtonEnabledNetworks.setEntries(select);
                        mButtonEnabledNetworks.setEntryValues(
                                R.array.enabled_networks_values);
                    }
                    updateGsmUmtsOptions(this, prefSet, phoneSubId, mNetworkQueryService);
                } else {
                    throw new IllegalStateException("Unexpected phone type: " + phoneType);
                }
                if (isWorldMode()) {
                    mButtonEnabledNetworks.setEntries(
                            R.array.preferred_network_mode_choices_world_mode);
                    mButtonEnabledNetworks.setEntryValues(
                            R.array.preferred_network_mode_values_world_mode);
                }
                mButtonEnabledNetworks.setOnPreferenceChangeListener(this);
                if (DBG) log("settingsNetworkMode: " + settingsNetworkMode);
            }

            final boolean missingDataServiceUrl = TextUtils.isEmpty(
                    android.provider.Settings.Global.getString(activity.getContentResolver(),
                            android.provider.Settings.Global.SETUP_PREPAID_DATA_SERVICE_URL));
            if (!isLteOnCdma || missingDataServiceUrl) {
                prefSet.removePreference(mLteDataServicePref);
            } else {
                android.util.Log.d(LOG_TAG, "keep ltePref");
            }

            if (hideEnhanced4gLteSettings(getActivity(), carrierConfig)) {
                Preference pref = prefSet.findPreference(BUTTON_4G_LTE_KEY);
                if (pref != null) {
                    prefSet.removePreference(pref);
                }
            }

            updateCallingCategory();

            // Enable link to CMAS app settings depending on the value in config.xml.
            final boolean isCellBroadcastAppLinkEnabled = activity.getResources().getBoolean(
                    com.android.internal.R.bool.config_cellBroadcastAppLinks);
            if (!mUm.isAdminUser() || !isCellBroadcastAppLinkEnabled
                    || mUm.hasUserRestriction(UserManager.DISALLOW_CONFIG_CELL_BROADCASTS)) {
                PreferenceScreen root = getPreferenceScreen();
                Preference ps = findPreference(BUTTON_CELL_BROADCAST_SETTINGS);
                if (ps != null) {
                    root.removePreference(ps);
                }
            }

            // Get the networkMode from Settings.System and displays it
            mButtonEnabledNetworks.setValue(Integer.toString(settingsNetworkMode));
            mButtonPreferredNetworkMode.setValue(Integer.toString(settingsNetworkMode));
            UpdatePreferredNetworkModeSummary(settingsNetworkMode);
            UpdateEnabledNetworksValueAndSummary(settingsNetworkMode);
            // Display preferred network type based on what modem returns b/18676277
            mPhone.setPreferredNetworkType(settingsNetworkMode, mHandler
                    .obtainMessage(MyHandler.MESSAGE_SET_PREFERRED_NETWORK_TYPE));

            /**
             * Enable/disable depending upon if there are any active subscriptions.
             *
             * I've decided to put this enable/disable code at the bottom as the
             * code above works even when there are no active subscriptions, thus
             * putting it afterwards is a smaller change. This can be refined later,
             * but you do need to remember that this all needs to work when subscriptions
             * change dynamically such as when hot swapping sims.
             */
            boolean canChange4glte = is4gLtePrefEnabled(activity.getApplicationContext(),
                    carrierConfig);
            boolean useVariant4glteTitle = carrierConfig.getBoolean(
                    CarrierConfigManager.KEY_ENHANCED_4G_LTE_TITLE_VARIANT_BOOL);
            int enhanced4glteModeTitleId = useVariant4glteTitle ?
                    R.string.enhanced_4g_lte_mode_title_variant :
                    R.string.enhanced_4g_lte_mode_title;

            mButtonPreferredNetworkMode.setEnabled(hasActiveSubscriptions);
            mButtonEnabledNetworks.setEnabled(hasActiveSubscriptions);
            mButton4glte.setTitle(enhanced4glteModeTitleId);
            mButton4glte.setEnabled(hasActiveSubscriptions && canChange4glte);
            mLteDataServicePref.setEnabled(hasActiveSubscriptions);
            Preference ps;
            PreferenceScreen root = getPreferenceScreen();
            ps = findPreference(BUTTON_CELL_BROADCAST_SETTINGS);
            if (ps != null) {
                ps.setEnabled(hasActiveSubscriptions);
            }
            ps = findPreference(CATEGORY_GSM_APN_EXPAND_KEY);
            if (ps != null) {
                ps.setEnabled(hasActiveSubscriptions);
            }
            ps = findPreference(CATEGORY_CDMA_APN_EXPAND_KEY);
            if (ps != null) {
                ps.setEnabled(hasActiveSubscriptions);
            }
            ps = findPreference(NetworkOperators.CATEGORY_NETWORK_OPERATORS_KEY);
            if (ps != null) {
                ps.setEnabled(hasActiveSubscriptions);
            }
            ps = findPreference(BUTTON_CARRIER_SETTINGS_KEY);
            if (ps != null) {
                ps.setEnabled(hasActiveSubscriptions);
            }
            ps = findPreference(BUTTON_CDMA_SYSTEM_SELECT_KEY);
            if (ps != null) {
                ps.setEnabled(hasActiveSubscriptions);
            }
            ps = findPreference(CATEGORY_CALLING_KEY);
            if (ps != null) {
                ps.setEnabled(hasActiveSubscriptions);
            }
        }

        @Override
        public void onPause() {
            super.onPause();
            if (DBG) log("onPause:+");

            mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);

            mSubscriptionManager
                    .removeOnSubscriptionsChangedListener(mOnSubscriptionsChangeListener);
            if (DBG) log("onPause:-");
        }

        /**
         * Implemented to support onPreferenceChangeListener to look for preference
         * changes specifically on CLIR.
         *
         * @param preference is the preference to be changed, should be mButtonCLIR.
         * @param objValue should be the value of the selection, NOT its localized
         * display value.
         */
        public boolean onPreferenceChange(Preference preference, Object objValue) {
            final int phoneSubId = mPhone.getSubId();
            if (preference == mButtonPreferredNetworkMode) {
                //NOTE onPreferenceChange seems to be called even if there is no change
                //Check if the button value is changed from the System.Setting
                mButtonPreferredNetworkMode.setValue((String) objValue);
                int buttonNetworkMode;
                buttonNetworkMode = Integer.parseInt((String) objValue);
                int settingsNetworkMode = android.provider.Settings.Global.getInt(
                        mPhone.getContext().getContentResolver(),
                        android.provider.Settings.Global.PREFERRED_NETWORK_MODE + phoneSubId,
                        preferredNetworkMode);
                if (buttonNetworkMode != settingsNetworkMode) {
                    int modemNetworkMode;
                    // if new mode is invalid ignore it
                    switch (buttonNetworkMode) {
                        case Phone.NT_MODE_WCDMA_PREF:
                        case Phone.NT_MODE_GSM_ONLY:
                        case Phone.NT_MODE_WCDMA_ONLY:
                        case Phone.NT_MODE_GSM_UMTS:
                        case Phone.NT_MODE_CDMA:
                        case Phone.NT_MODE_CDMA_NO_EVDO:
                        case Phone.NT_MODE_EVDO_NO_CDMA:
                        case Phone.NT_MODE_GLOBAL:
                        case Phone.NT_MODE_LTE_CDMA_AND_EVDO:
                        case Phone.NT_MODE_LTE_GSM_WCDMA:
                        case Phone.NT_MODE_LTE_CDMA_EVDO_GSM_WCDMA:
                        case Phone.NT_MODE_LTE_ONLY:
                        case Phone.NT_MODE_LTE_WCDMA:
                        case Phone.NT_MODE_TDSCDMA_ONLY:
                        case Phone.NT_MODE_TDSCDMA_WCDMA:
                        case Phone.NT_MODE_LTE_TDSCDMA:
                        case Phone.NT_MODE_TDSCDMA_GSM:
                        case Phone.NT_MODE_LTE_TDSCDMA_GSM:
                        case Phone.NT_MODE_TDSCDMA_GSM_WCDMA:
                        case Phone.NT_MODE_LTE_TDSCDMA_WCDMA:
                        case Phone.NT_MODE_LTE_TDSCDMA_GSM_WCDMA:
                        case Phone.NT_MODE_TDSCDMA_CDMA_EVDO_GSM_WCDMA:
                        case Phone.NT_MODE_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA:
                            // This is one of the modes we recognize
                            modemNetworkMode = buttonNetworkMode;
                            break;
                        default:
                            loge("Invalid Network Mode (" +buttonNetworkMode+ ") chosen. Ignore.");
                            return true;
                    }

                    android.provider.Settings.Global.putInt(
                            mPhone.getContext().getContentResolver(),
                            android.provider.Settings.Global.PREFERRED_NETWORK_MODE + phoneSubId,
                            buttonNetworkMode );
                    //Set the modem network mode
                    mPhone.setPreferredNetworkType(modemNetworkMode, mHandler
                            .obtainMessage(MyHandler.MESSAGE_SET_PREFERRED_NETWORK_TYPE));
                }
            } else if (preference == mButtonEnabledNetworks) {
                mButtonEnabledNetworks.setValue((String) objValue);
                int buttonNetworkMode;
                buttonNetworkMode = Integer.parseInt((String) objValue);
                if (DBG) log("buttonNetworkMode: " + buttonNetworkMode);
                int settingsNetworkMode = android.provider.Settings.Global.getInt(
                        mPhone.getContext().getContentResolver(),
                        android.provider.Settings.Global.PREFERRED_NETWORK_MODE + phoneSubId,
                        preferredNetworkMode);
                if (buttonNetworkMode != settingsNetworkMode) {
                    int modemNetworkMode;
                    // if new mode is invalid ignore it
                    switch (buttonNetworkMode) {
                        case Phone.NT_MODE_WCDMA_PREF:
                        case Phone.NT_MODE_GSM_ONLY:
                        case Phone.NT_MODE_LTE_GSM_WCDMA:
                        case Phone.NT_MODE_LTE_CDMA_EVDO_GSM_WCDMA:
                        case Phone.NT_MODE_CDMA:
                        case Phone.NT_MODE_CDMA_NO_EVDO:
                        case Phone.NT_MODE_LTE_CDMA_AND_EVDO:
                        case Phone.NT_MODE_TDSCDMA_ONLY:
                        case Phone.NT_MODE_TDSCDMA_WCDMA:
                        case Phone.NT_MODE_LTE_TDSCDMA:
                        case Phone.NT_MODE_TDSCDMA_GSM:
                        case Phone.NT_MODE_LTE_TDSCDMA_GSM:
                        case Phone.NT_MODE_TDSCDMA_GSM_WCDMA:
                        case Phone.NT_MODE_LTE_TDSCDMA_WCDMA:
                        case Phone.NT_MODE_LTE_TDSCDMA_GSM_WCDMA:
                        case Phone.NT_MODE_TDSCDMA_CDMA_EVDO_GSM_WCDMA:
                        case Phone.NT_MODE_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA:
                            // This is one of the modes we recognize
                            modemNetworkMode = buttonNetworkMode;
                            break;
                        default:
                            loge("Invalid Network Mode (" +buttonNetworkMode+ ") chosen. Ignore.");
                            return true;
                    }

                    UpdateEnabledNetworksValueAndSummary(buttonNetworkMode);

                    android.provider.Settings.Global.putInt(
                            mPhone.getContext().getContentResolver(),
                            android.provider.Settings.Global.PREFERRED_NETWORK_MODE + phoneSubId,
                            buttonNetworkMode );
                    //Set the modem network mode
                    mPhone.setPreferredNetworkType(modemNetworkMode, mHandler
                            .obtainMessage(MyHandler.MESSAGE_SET_PREFERRED_NETWORK_TYPE));
                }
            } else if (preference == mButton4glte) {
                SwitchPreference enhanced4gModePref = (SwitchPreference) preference;
                boolean enhanced4gMode = !enhanced4gModePref.isChecked();
                enhanced4gModePref.setChecked(enhanced4gMode);
                ImsManager.setEnhanced4gLteModeSetting(getActivity(),
                        enhanced4gModePref.isChecked());
            } else if (preference == mButtonDataRoam) {
                if (DBG) log("onPreferenceTreeClick: preference == mButtonDataRoam.");

                //normally called on the toggle click
                if (!mButtonDataRoam.isChecked()) {
                    // First confirm with a warning dialog about charges
                    mOkClicked = false;
                    RoamingDialogFragment fragment = new RoamingDialogFragment();
                    fragment.show(getFragmentManager(), ROAMING_TAG);
                    // Don't update the toggle unless the confirm button is actually pressed.
                    return false;
                } else {
                    mPhone.setDataRoamingEnabled(false);
                }
                return true;
            } else if (preference == mVideoCallingPref) {
                // If mButton4glte is not checked, mVideoCallingPref should be disabled.
                // So it only makes sense to call phoneMgr.enableVideoCalling if it's checked.
                if (mButton4glte.isChecked()) {
                    PhoneGlobals.getInstance().phoneMgr.enableVideoCalling((boolean) objValue);
                    return true;
                } else {
                    loge("mVideoCallingPref should be disabled if mButton4glte is not checked.");
                    mVideoCallingPref.setEnabled(false);
                    return false;
                }
            }

            updateBody();
            // always let the preference setting proceed.
            return true;
        }

        private boolean is4gLtePrefEnabled(Context context, PersistableBundle carrierConfig) {
            return (mTelephonyManager.getCallState() == TelephonyManager.CALL_STATE_IDLE)
                    && ImsManager.isNonTtyOrTtyOnVolteEnabled(context)
                    && carrierConfig.getBoolean(
                            CarrierConfigManager.KEY_EDITABLE_ENHANCED_4G_LTE_BOOL);
        }

        private class MyHandler extends Handler {

            static final int MESSAGE_SET_PREFERRED_NETWORK_TYPE = 0;

            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MESSAGE_SET_PREFERRED_NETWORK_TYPE:
                        handleSetPreferredNetworkTypeResponse(msg);
                        break;
                }
            }

            private void handleSetPreferredNetworkTypeResponse(Message msg) {
                final Activity activity = getActivity();
                if (activity == null || activity.isDestroyed()) {
                    // Access preferences of activity only if it is not destroyed
                    // or if fragment is not attached to an activity.
                    return;
                }

                AsyncResult ar = (AsyncResult) msg.obj;
                final int phoneSubId = mPhone.getSubId();

                if (ar.exception == null) {
                    int networkMode;
                    if (getPreferenceScreen().findPreference(
                            BUTTON_PREFERED_NETWORK_MODE) != null)  {
                        networkMode =  Integer.parseInt(mButtonPreferredNetworkMode.getValue());
                        android.provider.Settings.Global.putInt(
                                mPhone.getContext().getContentResolver(),
                                android.provider.Settings.Global.PREFERRED_NETWORK_MODE
                                        + phoneSubId,
                                networkMode );
                    }
                    if (getPreferenceScreen().findPreference(BUTTON_ENABLED_NETWORKS_KEY) != null) {
                        networkMode = Integer.parseInt(mButtonEnabledNetworks.getValue());
                        android.provider.Settings.Global.putInt(
                                mPhone.getContext().getContentResolver(),
                                android.provider.Settings.Global.PREFERRED_NETWORK_MODE
                                        + phoneSubId,
                                networkMode );
                    }
                } else {
                    Log.i(LOG_TAG, "handleSetPreferredNetworkTypeResponse:" +
                            "exception in setting network mode.");
                    updatePreferredNetworkUIFromDb();
                }
            }
        }

        private void updatePreferredNetworkUIFromDb() {
            final int phoneSubId = mPhone.getSubId();

            int settingsNetworkMode = android.provider.Settings.Global.getInt(
                    mPhone.getContext().getContentResolver(),
                    android.provider.Settings.Global.PREFERRED_NETWORK_MODE + phoneSubId,
                    preferredNetworkMode);

            if (DBG) {
                log("updatePreferredNetworkUIFromDb: settingsNetworkMode = " +
                        settingsNetworkMode);
            }

            UpdatePreferredNetworkModeSummary(settingsNetworkMode);
            UpdateEnabledNetworksValueAndSummary(settingsNetworkMode);
            // changes the mButtonPreferredNetworkMode accordingly to settingsNetworkMode
            mButtonPreferredNetworkMode.setValue(Integer.toString(settingsNetworkMode));
        }

        private void UpdatePreferredNetworkModeSummary(int NetworkMode) {
            switch(NetworkMode) {
                case Phone.NT_MODE_TDSCDMA_GSM_WCDMA:
                case Phone.NT_MODE_TDSCDMA_GSM:
                case Phone.NT_MODE_WCDMA_PREF:
                    mButtonPreferredNetworkMode.setSummary(
                            R.string.preferred_network_mode_wcdma_perf_summary);
                    break;
                case Phone.NT_MODE_GSM_ONLY:
                    mButtonPreferredNetworkMode.setSummary(
                            R.string.preferred_network_mode_gsm_only_summary);
                    break;
                case Phone.NT_MODE_TDSCDMA_WCDMA:
                case Phone.NT_MODE_WCDMA_ONLY:
                    mButtonPreferredNetworkMode.setSummary(
                            R.string.preferred_network_mode_wcdma_only_summary);
                    break;
                case Phone.NT_MODE_GSM_UMTS:
                    mButtonPreferredNetworkMode.setSummary(
                            R.string.preferred_network_mode_gsm_wcdma_summary);
                    break;
                case Phone.NT_MODE_CDMA:
                    switch (mPhone.getLteOnCdmaMode()) {
                        case PhoneConstants.LTE_ON_CDMA_TRUE:
                            mButtonPreferredNetworkMode.setSummary(
                                    R.string.preferred_network_mode_cdma_summary);
                            break;
                        case PhoneConstants.LTE_ON_CDMA_FALSE:
                        default:
                            mButtonPreferredNetworkMode.setSummary(
                                    R.string.preferred_network_mode_cdma_evdo_summary);
                            break;
                    }
                    break;
                case Phone.NT_MODE_CDMA_NO_EVDO:
                    mButtonPreferredNetworkMode.setSummary(
                            R.string.preferred_network_mode_cdma_only_summary);
                    break;
                case Phone.NT_MODE_EVDO_NO_CDMA:
                    mButtonPreferredNetworkMode.setSummary(
                            R.string.preferred_network_mode_evdo_only_summary);
                    break;
                case Phone.NT_MODE_LTE_TDSCDMA:
                case Phone.NT_MODE_LTE_ONLY:
                    mButtonPreferredNetworkMode.setSummary(
                            R.string.preferred_network_mode_lte_summary);
                    break;
                case Phone.NT_MODE_LTE_TDSCDMA_GSM:
                case Phone.NT_MODE_LTE_TDSCDMA_GSM_WCDMA:
                case Phone.NT_MODE_LTE_GSM_WCDMA:
                    mButtonPreferredNetworkMode.setSummary(
                            R.string.preferred_network_mode_lte_gsm_wcdma_summary);
                    break;
                case Phone.NT_MODE_LTE_CDMA_AND_EVDO:
                    mButtonPreferredNetworkMode.setSummary(
                            R.string.preferred_network_mode_lte_cdma_evdo_summary);
                    break;
                case Phone.NT_MODE_TDSCDMA_ONLY:
                    mButtonPreferredNetworkMode.setSummary(
                            R.string.preferred_network_mode_tdscdma_summary);
                    break;
                case Phone.NT_MODE_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA:
                case Phone.NT_MODE_LTE_CDMA_EVDO_GSM_WCDMA:
                    if (mPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA ||
                            mIsGlobalCdma ||
                            isWorldMode()) {
                        mButtonPreferredNetworkMode.setSummary(
                                R.string.preferred_network_mode_global_summary);
                    } else {
                        mButtonPreferredNetworkMode.setSummary(
                                R.string.preferred_network_mode_lte_summary);
                    }
                    break;
                case Phone.NT_MODE_TDSCDMA_CDMA_EVDO_GSM_WCDMA:
                case Phone.NT_MODE_GLOBAL:
                    mButtonPreferredNetworkMode.setSummary(
                            R.string.preferred_network_mode_cdma_evdo_gsm_wcdma_summary);
                    break;
                case Phone.NT_MODE_LTE_TDSCDMA_WCDMA:
                case Phone.NT_MODE_LTE_WCDMA:
                    mButtonPreferredNetworkMode.setSummary(
                            R.string.preferred_network_mode_lte_wcdma_summary);
                    break;
                default:
                    mButtonPreferredNetworkMode.setSummary(
                            R.string.preferred_network_mode_global_summary);
            }
        }

        private void UpdateEnabledNetworksValueAndSummary(int NetworkMode) {
            switch (NetworkMode) {
                case Phone.NT_MODE_TDSCDMA_WCDMA:
                case Phone.NT_MODE_TDSCDMA_GSM_WCDMA:
                case Phone.NT_MODE_TDSCDMA_GSM:
                    mButtonEnabledNetworks.setValue(
                            Integer.toString(Phone.NT_MODE_TDSCDMA_GSM_WCDMA));
                    mButtonEnabledNetworks.setSummary(R.string.network_3G);
                    break;
                case Phone.NT_MODE_WCDMA_ONLY:
                case Phone.NT_MODE_GSM_UMTS:
                case Phone.NT_MODE_WCDMA_PREF:
                    if (!mIsGlobalCdma) {
                        mButtonEnabledNetworks.setValue(
                                Integer.toString(Phone.NT_MODE_WCDMA_PREF));
                        mButtonEnabledNetworks.setSummary(R.string.network_3G);
                    } else {
                        mButtonEnabledNetworks.setValue(
                                Integer.toString(Phone.NT_MODE_LTE_CDMA_EVDO_GSM_WCDMA));
                        mButtonEnabledNetworks.setSummary(R.string.network_global);
                    }
                    break;
                case Phone.NT_MODE_GSM_ONLY:
                    if (!mIsGlobalCdma) {
                        mButtonEnabledNetworks.setValue(
                                Integer.toString(Phone.NT_MODE_GSM_ONLY));
                        mButtonEnabledNetworks.setSummary(R.string.network_2G);
                    } else {
                        mButtonEnabledNetworks.setValue(
                                Integer.toString(Phone.NT_MODE_LTE_CDMA_EVDO_GSM_WCDMA));
                        mButtonEnabledNetworks.setSummary(R.string.network_global);
                    }
                    break;
                case Phone.NT_MODE_LTE_GSM_WCDMA:
                    if (isWorldMode()) {
                        mButtonEnabledNetworks.setSummary(
                                R.string.preferred_network_mode_lte_gsm_umts_summary);
                        controlCdmaOptions(false);
                        controlGsmOptions(true);
                        break;
                    }
                case Phone.NT_MODE_LTE_ONLY:
                case Phone.NT_MODE_LTE_WCDMA:
                    if (!mIsGlobalCdma) {
                        mButtonEnabledNetworks.setValue(
                                Integer.toString(Phone.NT_MODE_LTE_GSM_WCDMA));
                        mButtonEnabledNetworks.setSummary((mShow4GForLTE == true)
                                ? R.string.network_4G : R.string.network_lte);
                    } else {
                        mButtonEnabledNetworks.setValue(
                                Integer.toString(Phone.NT_MODE_LTE_CDMA_EVDO_GSM_WCDMA));
                        mButtonEnabledNetworks.setSummary(R.string.network_global);
                    }
                    break;
                case Phone.NT_MODE_LTE_CDMA_AND_EVDO:
                    if (isWorldMode()) {
                        mButtonEnabledNetworks.setSummary(
                                R.string.preferred_network_mode_lte_cdma_summary);
                        controlCdmaOptions(true);
                        controlGsmOptions(false);
                    } else {
                        mButtonEnabledNetworks.setValue(
                                Integer.toString(Phone.NT_MODE_LTE_CDMA_AND_EVDO));
                        mButtonEnabledNetworks.setSummary(R.string.network_lte);
                    }
                    break;
                case Phone.NT_MODE_TDSCDMA_CDMA_EVDO_GSM_WCDMA:
                    mButtonEnabledNetworks.setValue(
                            Integer.toString(Phone.NT_MODE_TDSCDMA_CDMA_EVDO_GSM_WCDMA));
                    mButtonEnabledNetworks.setSummary(R.string.network_3G);
                    break;
                case Phone.NT_MODE_CDMA:
                case Phone.NT_MODE_EVDO_NO_CDMA:
                case Phone.NT_MODE_GLOBAL:
                    mButtonEnabledNetworks.setValue(
                            Integer.toString(Phone.NT_MODE_CDMA));
                    mButtonEnabledNetworks.setSummary(R.string.network_3G);
                    break;
                case Phone.NT_MODE_CDMA_NO_EVDO:
                    mButtonEnabledNetworks.setValue(
                            Integer.toString(Phone.NT_MODE_CDMA_NO_EVDO));
                    mButtonEnabledNetworks.setSummary(R.string.network_1x);
                    break;
                case Phone.NT_MODE_TDSCDMA_ONLY:
                    mButtonEnabledNetworks.setValue(
                            Integer.toString(Phone.NT_MODE_TDSCDMA_ONLY));
                    mButtonEnabledNetworks.setSummary(R.string.network_3G);
                    break;
                case Phone.NT_MODE_LTE_TDSCDMA_GSM:
                case Phone.NT_MODE_LTE_TDSCDMA_GSM_WCDMA:
                case Phone.NT_MODE_LTE_TDSCDMA:
                case Phone.NT_MODE_LTE_TDSCDMA_WCDMA:
                case Phone.NT_MODE_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA:
                case Phone.NT_MODE_LTE_CDMA_EVDO_GSM_WCDMA:
                    if (isSupportTdscdma()) {
                        mButtonEnabledNetworks.setValue(
                                Integer.toString(Phone.NT_MODE_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA));
                        mButtonEnabledNetworks.setSummary(R.string.network_lte);
                    } else {
                        if (isWorldMode()) {
                            controlCdmaOptions(true);
                            controlGsmOptions(false);
                        }
                        mButtonEnabledNetworks.setValue(
                                Integer.toString(Phone.NT_MODE_LTE_CDMA_EVDO_GSM_WCDMA));
                        if (mPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA ||
                                mIsGlobalCdma ||
                                isWorldMode()) {
                            mButtonEnabledNetworks.setSummary(R.string.network_global);
                        } else {
                            mButtonEnabledNetworks.setSummary((mShow4GForLTE == true)
                                    ? R.string.network_4G : R.string.network_lte);
                        }
                    }
                    break;
                default:
                    String errMsg = "Invalid Network Mode (" + NetworkMode + "). Ignore.";
                    loge(errMsg);
                    mButtonEnabledNetworks.setSummary(errMsg);
            }
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            switch(requestCode) {
                case REQUEST_CODE_EXIT_ECM:
                    Boolean isChoiceYes = data.getBooleanExtra(
                            EmergencyCallbackModeExitDialog.EXTRA_EXIT_ECM_RESULT, false);
                    if (isChoiceYes) {
                        // If the phone exits from ECM mode, show the CDMA Options
                        mCdmaOptions.showDialog(mClickedPreference);
                    } else {
                        // do nothing
                    }
                    break;

                default:
                    break;
            }
        }

        private void updateWiFiCallState() {
            if (mWiFiCallingPref == null || mCallingCategory == null) {
                return;
            }

            boolean removePref = false;
            final PhoneAccountHandle simCallManager =
                    TelecomManager.from(getContext()).getSimCallManager();

            if (simCallManager != null) {
                Intent intent = PhoneAccountSettingsFragment.buildPhoneAccountConfigureIntent(
                        getContext(), simCallManager);
                if (intent != null) {
                    PackageManager pm = mPhone.getContext().getPackageManager();
                    List<ResolveInfo> resolutions = pm.queryIntentActivities(intent, 0);
                    if (!resolutions.isEmpty()) {
                        mWiFiCallingPref.setTitle(resolutions.get(0).loadLabel(pm));
                        mWiFiCallingPref.setSummary(null);
                        mWiFiCallingPref.setIntent(intent);
                    } else {
                        removePref = true;
                    }
                } else {
                    removePref = true;
                }
            } else if (!ImsManager.isWfcEnabledByPlatform(mPhone.getContext())
                    || !ImsManager.isWfcProvisionedOnDevice(mPhone.getContext())) {
                removePref = true;
            } else {
                int resId = com.android.internal.R.string.wifi_calling_off_summary;
                if (ImsManager.isWfcEnabledByUser(mPhone.getContext())) {
                    boolean isRoaming = mButtonDataRoam.isChecked();
                    int wfcMode = ImsManager.getWfcMode(mPhone.getContext(), isRoaming);
                    switch (wfcMode) {
                        case ImsConfig.WfcModeFeatureValueConstants.WIFI_ONLY:
                            resId = com.android.internal.R.string.wfc_mode_wifi_only_summary;
                            break;
                        case ImsConfig.WfcModeFeatureValueConstants.CELLULAR_PREFERRED:
                            resId = com.android.internal.R.string
                                    .wfc_mode_cellular_preferred_summary;
                            break;
                        case ImsConfig.WfcModeFeatureValueConstants.WIFI_PREFERRED:
                            resId = com.android.internal.R.string.wfc_mode_wifi_preferred_summary;
                            break;
                        default:
                            if (DBG) log("Unexpected WFC mode value: " + wfcMode);
                    }
                }
                mWiFiCallingPref.setSummary(resId);
            }

            if (removePref) {
                mCallingCategory.removePreference(mWiFiCallingPref);
            } else {
                mCallingCategory.addPreference(mWiFiCallingPref);
            }
        }

        private void updateVideoCallState() {
            if (mVideoCallingPref == null || mCallingCategory == null) {
                return;
            }

            PersistableBundle carrierConfig = PhoneGlobals.getInstance()
                    .getCarrierConfigForSubId(mPhone.getSubId());

            boolean removePref = false;

            if (ImsManager.isVtEnabledByPlatform(mPhone.getContext())
                    && ImsManager.isVtProvisionedOnDevice(mPhone.getContext())
                    && (carrierConfig.getBoolean(
                            CarrierConfigManager.KEY_IGNORE_DATA_ENABLED_CHANGED_FOR_VIDEO_CALLS)
                            || mPhone.mDcTracker.isDataEnabled())) {
                boolean enhanced4gLteEnabled = mButton4glte.isChecked();
                mVideoCallingPref.setEnabled(enhanced4gLteEnabled);
                boolean currentValue = enhanced4gLteEnabled
                        ? PhoneGlobals.getInstance().phoneMgr.isVideoCallingEnabled(
                        getContext().getOpPackageName()) : false;
                mVideoCallingPref.setChecked(currentValue);
                if (enhanced4gLteEnabled) {
                    mVideoCallingPref.setOnPreferenceChangeListener(this);
                }
            } else {
                removePref = true;
            }

            if (removePref) {
                mCallingCategory.removePreference(mVideoCallingPref);
            } else {
                mCallingCategory.addPreference(mVideoCallingPref);
            }
        }

        private void updateCallingCategory() {
            if (mCallingCategory == null) {
                return;
            }

            updateWiFiCallState();
            updateVideoCallState();

            // If all items in calling category is removed, we remove it from
            // the screen. Otherwise we'll see title of the category but nothing
            // is in there.
            if (mCallingCategory.getPreferenceCount() == 0) {
                getPreferenceScreen().removePreference(mCallingCategory);
            } else {
                getPreferenceScreen().addPreference(mCallingCategory);
            }
        }

        private static void log(String msg) {
            Log.d(LOG_TAG, msg);
        }

        private static void loge(String msg) {
            Log.e(LOG_TAG, msg);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            final int itemId = item.getItemId();
            if (itemId == android.R.id.home) {  // See ActionBar#setDisplayHomeAsUpEnabled()
                // Commenting out "logical up" capability. This is a workaround for issue 5278083.
                //
                // Settings app may not launch this activity via UP_ACTIVITY_CLASS but the other
                // Activity that looks exactly same as UP_ACTIVITY_CLASS ("SubSettings" Activity).
                // At that moment, this Activity launches UP_ACTIVITY_CLASS on top of the Activity.
                // which confuses users.
                // TODO: introduce better mechanism for "up" capability here.
            /*Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName(UP_ACTIVITY_PACKAGE, UP_ACTIVITY_CLASS);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);*/
                getActivity().finish();
                return true;
            }
            return super.onOptionsItemSelected(item);
        }

        private boolean isWorldMode() {
            boolean worldModeOn = false;
            final String configString = getResources().getString(R.string.config_world_mode);

            if (!TextUtils.isEmpty(configString)) {
                String[] configArray = configString.split(";");
                // Check if we have World mode configuration set to True only or config is set to True
                // and SIM GID value is also set and matches to the current SIM GID.
                if (configArray != null &&
                        ((configArray.length == 1 && configArray[0].equalsIgnoreCase("true"))
                                || (configArray.length == 2 && !TextUtils.isEmpty(configArray[1])
                                && mTelephonyManager != null
                                && configArray[1].equalsIgnoreCase(
                                        mTelephonyManager.getGroupIdLevel1())))) {
                    worldModeOn = true;
                }
            }

            Log.d(LOG_TAG, "isWorldMode=" + worldModeOn);

            return worldModeOn;
        }

        private void controlGsmOptions(boolean enable) {
            PreferenceScreen prefSet = getPreferenceScreen();
            if (prefSet == null) {
                return;
            }

            updateGsmUmtsOptions(this, prefSet, mPhone.getSubId(), mNetworkQueryService);

            PreferenceCategory apnExpand =
                    (PreferenceCategory) prefSet.findPreference(CATEGORY_GSM_APN_EXPAND_KEY);
            PreferenceCategory networkOperatorCategory =
                    (PreferenceCategory) prefSet.findPreference(
                            NetworkOperators.CATEGORY_NETWORK_OPERATORS_KEY);
            Preference carrierSettings = prefSet.findPreference(BUTTON_CARRIER_SETTINGS_KEY);
            if (apnExpand != null) {
                apnExpand.setEnabled(isWorldMode() || enable);
            }
            if (networkOperatorCategory != null) {
                if (enable) {
                    networkOperatorCategory.setEnabled(true);
                } else {
                    prefSet.removePreference(networkOperatorCategory);
                }
            }
            if (carrierSettings != null) {
                prefSet.removePreference(carrierSettings);
            }
        }

        private void controlCdmaOptions(boolean enable) {
            PreferenceScreen prefSet = getPreferenceScreen();
            if (prefSet == null) {
                return;
            }
            updateCdmaOptions(this, prefSet, mPhone);
            CdmaSystemSelectListPreference systemSelect =
                    (CdmaSystemSelectListPreference)prefSet.findPreference
                            (BUTTON_CDMA_SYSTEM_SELECT_KEY);
            if (systemSelect != null) {
                systemSelect.setEnabled(enable);
            }
        }

        private boolean isSupportTdscdma() {
            if (getResources().getBoolean(R.bool.config_support_tdscdma)) {
                return true;
            }

            String operatorNumeric = mPhone.getServiceState().getOperatorNumeric();
            String[] numericArray = getResources().getStringArray(
                    R.array.config_support_tdscdma_roaming_on_networks);
            if (numericArray.length == 0 || operatorNumeric == null) {
                return false;
            }
            for (String numeric : numericArray) {
                if (operatorNumeric.equals(numeric)) {
                    return true;
                }
            }
            return false;
        }

        private void sendMetricsEventPreferenceClicked(
                PreferenceScreen preferenceScreen, Preference preference) {
            if (preference == mMobileDataPref) {
                MetricsLogger.action(getContext(),
                        MetricsEvent.ACTION_MOBILE_NETWORK_MOBILE_DATA_TOGGLE,
                        ((MobileDataPreference) preference).mChecked);
            } else if (preference == mDataUsagePref) {
                MetricsLogger.action(getContext(),
                        MetricsEvent.ACTION_MOBILE_NETWORK_DATA_USAGE);
            }
            // TODO: add Metrics constants for other preferences and send events here accordingly.
        }

        private void updateGsmUmtsOptions(PreferenceFragment prefFragment,
                PreferenceScreen prefScreen, final int subId, INetworkQueryService queryService) {
            // We don't want to re-create GsmUmtsOptions if already exists. Otherwise, the
            // preferences inside it will also be re-created which causes unexpected behavior.
            // For example, the open dialog gets dismissed or detached after pause / resume.
            if (mGsmUmtsOptions == null) {
                mGsmUmtsOptions = new GsmUmtsOptions(prefFragment, prefScreen, subId, queryService);
            } else {
                mGsmUmtsOptions.update(subId, queryService);
            }
        }

        private void updateCdmaOptions(PreferenceFragment prefFragment, PreferenceScreen prefScreen,
                Phone phone) {
            // We don't want to re-create CdmaOptions if already exists. Otherwise, the preferences
            // inside it will also be re-created which causes unexpected behavior. For example,
            // the open dialog gets dismissed or detached after pause / resume.
            if (mCdmaOptions == null) {
                mCdmaOptions = new CdmaOptions(prefFragment, prefScreen, phone);
            } else {
                mCdmaOptions.update(phone);
            }
        }
    }
}

