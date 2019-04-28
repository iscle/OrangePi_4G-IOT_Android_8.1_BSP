/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.services.telephony.sip;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.sip.SipErrorCode;
import android.net.sip.SipException;
import android.net.sip.SipManager;
import android.net.sip.SipProfile;
import android.net.sip.SipRegistrationListener;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.Process;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.android.phone.R;

/**
 * The PreferenceActivity class for managing sip profile preferences.
 */
public class SipSettings extends PreferenceActivity {
    public static final String SIP_SHARED_PREFERENCES = "SIP_PREFERENCES";

    static final String KEY_SIP_PROFILE = "sip_profile";
    static final int REQUEST_ADD_OR_EDIT_SIP_PROFILE = 1;

    private static final String PREFIX = "[SipSettings] ";
    private static final boolean VERBOSE = false; /* STOP SHIP if true */
    private static final int MENU_ADD_ACCOUNT = Menu.FIRST;
    private static final String PREF_SIP_LIST = "sip_account_list";

    private PackageManager mPackageManager;
    private SipManager mSipManager;
    private SipProfileDb mProfileDb;
    private SipProfile mProfile; // profile that's being edited
    private PreferenceCategory mSipListContainer;
    private Map<String, SipPreference> mSipPreferenceMap;
    private List<SipProfile> mSipProfileList;
    private SipPreferences mSipPreferences;
    private int mUid = Process.myUid();

    private class SipPreference extends Preference {
        SipProfile mProfile;
        SipPreference(Context c, SipProfile p) {
            super(c);
            setProfile(p);
        }

        SipProfile getProfile() {
            return mProfile;
        }

        void setProfile(SipProfile p) {
            mProfile = p;
            setTitle(getProfileName(p));
            updateSummary(mSipPreferences.isReceivingCallsEnabled()
                    ? getString(R.string.registration_status_checking_status)
                    : getString(R.string.registration_status_not_receiving));
        }

        void updateSummary(String registrationStatus) {
            int profileUid = mProfile.getCallingUid();
            if (VERBOSE) {
                log("SipPreference.updateSummary, profile uid: " + profileUid +
                        " registration: " + registrationStatus +
                        " status: " + registrationStatus);
            }
            String summary = "";
            if ((profileUid > 0) && (profileUid != mUid)) {
                // from third party apps
                summary = getString(R.string.third_party_account_summary,
                        getPackageNameFromUid(profileUid));
            } else {
                summary = registrationStatus;
            }
            setSummary(summary);
        }
    }

    private String getPackageNameFromUid(int uid) {
        try {
            String[] pkgs = mPackageManager.getPackagesForUid(uid);
            ApplicationInfo ai = mPackageManager.getApplicationInfo(pkgs[0], 0);
            return ai.loadLabel(mPackageManager).toString();
        } catch (PackageManager.NameNotFoundException e) {
            log("getPackageNameFromUid, cannot find name of uid: " + uid + ", exception: " + e);
        }
        return "uid:" + uid;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSipManager = SipManager.newInstance(this);
        mSipPreferences = new SipPreferences(this);
        mProfileDb = new SipProfileDb(this);

        mPackageManager = getPackageManager();
        setContentView(R.layout.sip_settings_ui);
        addPreferencesFromResource(R.xml.sip_setting);
        mSipListContainer = (PreferenceCategory) findPreference(PREF_SIP_LIST);

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateProfilesStatus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterForContextMenu(getListView());
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode,
            final Intent intent) {
        if (resultCode != RESULT_OK && resultCode != RESULT_FIRST_USER) return;
        new Thread() {
            @Override
            public void run() {
                try {
                    if (mProfile != null) {
                        if (VERBOSE) log("onActivityResult, remove: " + mProfile.getProfileName());
                        deleteProfile(mProfile);
                    }

                    SipProfile profile = intent.getParcelableExtra(KEY_SIP_PROFILE);
                    if (resultCode == RESULT_OK) {
                        if (VERBOSE) log("onActivityResult, new: " + profile.getProfileName());
                        addProfile(profile);
                    }
                    updateProfilesStatus();
                } catch (IOException e) {
                    log("onActivityResult, can not handle the profile:  " + e);
                }
            }
        }.start();
    }

    private void updateProfilesStatus() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    retrieveSipLists();
                } catch (Exception e) {
                    log("updateProfilesStatus, exception: " + e);
                }
            }
        }).start();
    }

    private String getProfileName(SipProfile profile) {
        String profileName = profile.getProfileName();
        if (TextUtils.isEmpty(profileName)) {
            profileName = profile.getUserName() + "@" + profile.getSipDomain();
        }
        return profileName;
    }

    private void retrieveSipLists() {
        mSipPreferenceMap = new LinkedHashMap<String, SipPreference>();
        mSipProfileList = mProfileDb.retrieveSipProfileList();
        processActiveProfilesFromSipService();
        Collections.sort(mSipProfileList, new Comparator<SipProfile>() {
            @Override
            public int compare(SipProfile p1, SipProfile p2) {
                return getProfileName(p1).compareTo(getProfileName(p2));
            }

            public boolean equals(SipProfile p) {
                // not used
                return false;
            }
        });
        mSipListContainer.removeAll();
        if (mSipProfileList.isEmpty()) {
            getPreferenceScreen().removePreference(mSipListContainer);
        } else {
            getPreferenceScreen().addPreference(mSipListContainer);
            for (SipProfile p : mSipProfileList) {
                addPreferenceFor(p);
            }
        }

        if (!mSipPreferences.isReceivingCallsEnabled()) return;
        for (SipProfile p : mSipProfileList) {
            if (mUid == p.getCallingUid()) {
                try {
                    mSipManager.setRegistrationListener(
                            p.getUriString(), createRegistrationListener());
                } catch (SipException e) {
                    log("retrieveSipLists, cannot set registration listener: " + e);
                }
            }
        }
    }

    private void processActiveProfilesFromSipService() {
        SipProfile[] activeList = {};
        try {
            activeList = mSipManager.getListOfProfiles();
        } catch (SipException e) {
            log("SipManager could not retrieve SIP profiles: " + e);
        }
        for (SipProfile activeProfile : activeList) {
            SipProfile profile = getProfileFromList(activeProfile);
            if (profile == null) {
                mSipProfileList.add(activeProfile);
            } else {
                profile.setCallingUid(activeProfile.getCallingUid());
            }
        }
    }

    private SipProfile getProfileFromList(SipProfile activeProfile) {
        for (SipProfile p : mSipProfileList) {
            if (p.getUriString().equals(activeProfile.getUriString())) {
                return p;
            }
        }
        return null;
    }

    private void addPreferenceFor(SipProfile p) {
        String status;
        if (VERBOSE) log("addPreferenceFor, profile uri: " + p.getUri());
        SipPreference pref = new SipPreference(this, p);
        mSipPreferenceMap.put(p.getUriString(), pref);
        mSipListContainer.addPreference(pref);

        pref.setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference pref) {
                        handleProfileClick(((SipPreference) pref).mProfile);
                        return true;
                    }
                });
    }

    private void handleProfileClick(final SipProfile profile) {
        int uid = profile.getCallingUid();
        if (uid == mUid || uid == 0) {
            startSipEditor(profile);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.alert_dialog_close)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setPositiveButton(R.string.close_profile,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int w) {
                                deleteProfile(profile);
                                unregisterProfile(profile);
                            }
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void unregisterProfile(final SipProfile p) {
        // run it on background thread for better UI response
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mSipManager.close(p.getUriString());
                } catch (Exception e) {
                    log("unregisterProfile, unregister failed, SipService died? Exception: " + e);
                }
            }
        }, "unregisterProfile").start();
    }

    void deleteProfile(SipProfile p) {
        mSipProfileList.remove(p);
        SipPreference pref = mSipPreferenceMap.remove(p.getUriString());
        if (pref != null) {
            mSipListContainer.removePreference(pref);
        }
    }

    private void addProfile(SipProfile p) throws IOException {
        try {
            mSipManager.setRegistrationListener(p.getUriString(),
                    createRegistrationListener());
        } catch (Exception e) {
            log("addProfile, cannot set registration listener: " + e);
        }
        mSipProfileList.add(p);
        addPreferenceFor(p);
    }

    private void startSipEditor(final SipProfile profile) {
        mProfile = profile;
        Intent intent = new Intent(this, SipEditor.class);
        intent.putExtra(KEY_SIP_PROFILE, (Parcelable) profile);
        startActivityForResult(intent, REQUEST_ADD_OR_EDIT_SIP_PROFILE);
    }

    private void showRegistrationMessage(final String profileUri,
            final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                SipPreference pref = mSipPreferenceMap.get(profileUri);
                if (pref != null) {
                    pref.updateSummary(message);
                }
            }
        });
    }

    private SipRegistrationListener createRegistrationListener() {
        return new SipRegistrationListener() {
            @Override
            public void onRegistrationDone(String profileUri, long expiryTime) {
                showRegistrationMessage(profileUri, getString(
                        R.string.registration_status_done));
            }

            @Override
            public void onRegistering(String profileUri) {
                showRegistrationMessage(profileUri, getString(
                        R.string.registration_status_registering));
            }

            @Override
            public void onRegistrationFailed(String profileUri, int errorCode,
                    String message) {
                switch (errorCode) {
                    case SipErrorCode.IN_PROGRESS:
                        showRegistrationMessage(profileUri, getString(
                                R.string.registration_status_still_trying));
                        break;
                    case SipErrorCode.INVALID_CREDENTIALS:
                        showRegistrationMessage(profileUri, getString(
                                R.string.registration_status_invalid_credentials));
                        break;
                    case SipErrorCode.SERVER_UNREACHABLE:
                        showRegistrationMessage(profileUri, getString(
                                R.string.registration_status_server_unreachable));
                        break;
                    case SipErrorCode.DATA_CONNECTION_LOST:
                        if (SipManager.isSipWifiOnly(getApplicationContext())){
                            showRegistrationMessage(profileUri, getString(
                                    R.string.registration_status_no_wifi_data));
                        } else {
                            showRegistrationMessage(profileUri, getString(
                                    R.string.registration_status_no_data));
                        }
                        break;
                    case SipErrorCode.CLIENT_ERROR:
                        showRegistrationMessage(profileUri, getString(
                                R.string.registration_status_not_running));
                        break;
                    default:
                        showRegistrationMessage(profileUri, getString(
                                R.string.registration_status_failed_try_later,
                                message));
                }
            }
        };
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuItem addAccountMenuItem = menu.add(0, MENU_ADD_ACCOUNT, 0, R.string.add_sip_account);
        addAccountMenuItem.setIcon(R.drawable.ic_add_24dp);
        addAccountMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(MENU_ADD_ACCOUNT).setEnabled(SipUtil.isPhoneIdle(this));
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        switch (itemId) {
            case MENU_ADD_ACCOUNT: {
                startSipEditor(null);
                return true;
            }
            case android.R.id.home: {
                onBackPressed();
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private static void log(String msg) {
        Log.d(SipUtil.LOG_TAG, PREFIX + msg);
    }
}
