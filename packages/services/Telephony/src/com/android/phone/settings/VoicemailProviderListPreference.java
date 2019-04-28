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

package com.android.phone.settings;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.preference.ListPreference;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;

import com.android.internal.telephony.Phone;
import com.android.phone.PhoneGlobals;
import com.android.phone.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VoicemailProviderListPreference extends ListPreference {
    private static final boolean DBG = (PhoneGlobals.DBG_LEVEL >= 2);
    private static final String LOG_TAG = VoicemailProviderListPreference.class.getSimpleName();

    // Key identifying the default voice mail provider
    public static final String DEFAULT_KEY = "";

    public class VoicemailProvider {
        public String name;
        public Intent intent;

        public VoicemailProvider(String name, Intent intent) {
            this.name = name;
            this.intent = intent;
        }

        public String toString() {
            return "[ Name: " + name + ", Intent: " + intent + " ]";
        }
    }

    private Phone mPhone;

    /**
     * Data about discovered voice mail settings providers.
     * Is populated by querying which activities can handle ACTION_CONFIGURE_VOICEMAIL.
     * They key in this map is package name + activity name.
     * We always add an entry for the default provider with a key of empty
     * string and intent value of null.
     * @see #initVoicemailProviders()
     */
    private final Map<String, VoicemailProvider> mVmProvidersData =
            new HashMap<String, VoicemailProvider>();


    public VoicemailProviderListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void init(Phone phone, Intent intent) {
        mPhone = phone;

        initVoicemailProviders(intent);
    }

    /**
     * Enumerates existing VM providers and puts their data into the list and populates
     * the preference list objects with their names.
     * In case we are called with ACTION_ADD_VOICEMAIL intent the intent may have
     * an extra string called IGNORE_PROVIDER_EXTRA with "package.activityName" of the provider
     * which should be hidden when we bring up the list of possible VM providers to choose.
     */
    private void initVoicemailProviders(Intent activityIntent) {
        if (DBG) log("initVoicemailProviders()");

        String providerToIgnore = null;
        String action = activityIntent.getAction();
        if (!TextUtils.isEmpty(action)
                && action.equals(VoicemailSettingsActivity.ACTION_ADD_VOICEMAIL)
                && activityIntent.hasExtra(VoicemailSettingsActivity.IGNORE_PROVIDER_EXTRA)) {
            // Remove this provider from the list.
            if (DBG) log("Found ACTION_ADD_VOICEMAIL.");
            providerToIgnore =
                    activityIntent.getStringExtra(VoicemailSettingsActivity.IGNORE_PROVIDER_EXTRA);
            VoicemailProviderSettingsUtil.delete(mPhone.getContext(), providerToIgnore);
        }

        mVmProvidersData.clear();

        List<String> entries = new ArrayList<String>();
        List<String> values = new ArrayList<String>();

        // Add default voicemail provider.
        final String myCarrier =
                mPhone.getContext().getResources().getString(R.string.voicemail_default);
        mVmProvidersData.put(VoicemailProviderListPreference.DEFAULT_KEY,
                new VoicemailProvider(myCarrier, null));
        entries.add(myCarrier);
        values.add(VoicemailProviderListPreference.DEFAULT_KEY);

        // Add other voicemail providers.
        PackageManager pm = mPhone.getContext().getPackageManager();
        Intent intent = new Intent(VoicemailSettingsActivity.ACTION_CONFIGURE_VOICEMAIL);
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);
        for (int i = 0; i < resolveInfos.size(); i++) {
            final ResolveInfo ri= resolveInfos.get(i);
            final ActivityInfo currentActivityInfo = ri.activityInfo;
            final String key = currentActivityInfo.name;

            if (key.equals(providerToIgnore)) {
                continue;
            }

            if (DBG) log("Loading key: " + key);
            CharSequence label = ri.loadLabel(pm);
            if (TextUtils.isEmpty(label)) {
                Log.w(LOG_TAG, "Adding voicemail provider with no name for display.");
            }
            String nameForDisplay = (label != null) ? label.toString() : "";
            Intent providerIntent = new Intent();
            providerIntent.setAction(VoicemailSettingsActivity.ACTION_CONFIGURE_VOICEMAIL);
            providerIntent.setClassName(currentActivityInfo.packageName, currentActivityInfo.name);
            VoicemailProvider vmProvider = new VoicemailProvider(nameForDisplay, providerIntent);

            if (DBG) log("Store VoicemailProvider. Key: " + key + " -> " + vmProvider.toString());
            mVmProvidersData.put(key, vmProvider);
            entries.add(vmProvider.name);
            values.add(key);
        }

        setEntries(entries.toArray(new String[0]));
        setEntryValues(values.toArray(new String[0]));
    }

    @Override
    public String getValue() {
        final String providerKey = super.getValue();
        return (providerKey != null) ? providerKey : DEFAULT_KEY;
    }

    public VoicemailProvider getVoicemailProvider(String key) {
        return mVmProvidersData.get(key);
    }

    public boolean hasMoreThanOneVoicemailProvider() {
        return mVmProvidersData.size() > 1;
    }

    private static void log(String msg) {
        Log.d(LOG_TAG, msg);
    }
}
