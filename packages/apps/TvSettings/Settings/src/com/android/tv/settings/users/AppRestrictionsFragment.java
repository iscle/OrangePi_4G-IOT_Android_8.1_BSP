/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.app.Activity;
import android.app.AppGlobals;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.RestrictionEntry;
import android.content.RestrictionsManager;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.UserInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.support.annotation.NonNull;
import android.support.v14.preference.MultiSelectListPreference;
import android.support.v14.preference.SwitchPreference;
import android.support.v17.preference.LeanbackPreferenceFragment;
import android.support.v4.util.ArrayMap;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceGroup;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.PreferenceViewHolder;
import android.util.Log;
import android.view.View;
import android.widget.Checkable;
import android.widget.CompoundButton;
import android.widget.Switch;

import com.android.settingslib.users.AppRestrictionsHelper;
import com.android.tv.settings.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

public class AppRestrictionsFragment extends LeanbackPreferenceFragment implements
        Preference.OnPreferenceChangeListener,
        AppRestrictionsHelper.OnDisableUiForPackageListener {

    private static final String TAG = AppRestrictionsFragment.class.getSimpleName();

    private static final boolean DEBUG = false;

    private static final String PKG_PREFIX = "pkg_";
    private static final String ACTIVITY_PREFIX = "activity_";

    private static final Drawable BLANK_DRAWABLE = new ColorDrawable(Color.TRANSPARENT);

    private PackageManager mPackageManager;
    private UserManager mUserManager;
    private IPackageManager mIPm;
    private UserHandle mUser;
    private PackageInfo mSysPackageInfo;

    private AppRestrictionsHelper mHelper;

    private PreferenceGroup mAppList;

    private static final int MAX_APP_RESTRICTIONS = 100;

    private static final String DELIMITER = ";";

    /** Key for extra passed in from calling fragment for the userId of the user being edited */
    private static final String EXTRA_USER_ID = "user_id";

    /** Key for extra passed in from calling fragment to indicate if this is a newly created user */
    private static final String EXTRA_NEW_USER = "new_user";

    private boolean mFirstTime = true;
    private boolean mNewUser;
    private boolean mAppListChanged;
    private boolean mRestrictedProfile;

    private static final int CUSTOM_REQUEST_CODE_START = 1000;
    private int mCustomRequestCode = CUSTOM_REQUEST_CODE_START;

    private Map<Integer, String> mCustomRequestMap = new ArrayMap<>();

    private AsyncTask mAppLoadingTask;

    private BroadcastReceiver mUserBackgrounding = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Update the user's app selection right away without waiting for a pause
            // onPause() might come in too late, causing apps to disappear after broadcasts
            // have been scheduled during user startup.
            if (mAppListChanged) {
                if (DEBUG) Log.d(TAG, "User backgrounding, update app list");
                mHelper.applyUserAppsStates(AppRestrictionsFragment.this);
                if (DEBUG) Log.d(TAG, "User backgrounding, done updating app list");
            }
        }
    };

    private BroadcastReceiver mPackageObserver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            onPackageChanged(intent);
        }
    };

    private static class AppRestrictionsPreference extends PreferenceGroup {
        private final Listener mListener = new Listener();
        private ArrayList<RestrictionEntry> mRestrictions;
        private boolean mImmutable;
        private boolean mChecked;
        private boolean mCheckedSet;

        AppRestrictionsPreference(Context context) {
            super(context, null, 0, R.style.LeanbackPreference_SwitchPreference);
        }

        void setRestrictions(ArrayList<RestrictionEntry> restrictions) {
            this.mRestrictions = restrictions;
        }

        void setImmutable(boolean immutable) {
            this.mImmutable = immutable;
        }

        boolean isImmutable() {
            return mImmutable;
        }

        ArrayList<RestrictionEntry> getRestrictions() {
            return mRestrictions;
        }

        public void setChecked(boolean checked) {
            // Always persist/notify the first time; don't assume the field's default of false.
            final boolean changed = mChecked != checked;
            if (changed || !mCheckedSet) {
                mChecked = checked;
                mCheckedSet = true;
                persistBoolean(checked);
                if (changed) {
                    notifyDependencyChange(shouldDisableDependents());
                    notifyChanged();
                    notifyHierarchyChanged();
                }
            }
        }

        @Override
        public int getPreferenceCount() {
            if (isChecked()) {
                return super.getPreferenceCount();
            } else {
                return 0;
            }
        }

        public boolean isChecked() {
            return mChecked;
        }

        @Override
        public void onBindViewHolder(PreferenceViewHolder holder) {
            super.onBindViewHolder(holder);
            View switchView = holder.findViewById(android.R.id.switch_widget);
            syncSwitchView(switchView);
        }

        private void syncSwitchView(View view) {
            if (view instanceof Switch) {
                final Switch switchView = (Switch) view;
                switchView.setOnCheckedChangeListener(null);
            }
            if (view instanceof Checkable) {
                ((Checkable) view).setChecked(mChecked);
            }
            if (view instanceof Switch) {
                final Switch switchView = (Switch) view;
                switchView.setOnCheckedChangeListener(mListener);
            }
        }

        private class Listener implements CompoundButton.OnCheckedChangeListener {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (!callChangeListener(isChecked)) {
                    // Listener didn't like it, change it back.
                    // CompoundButton will make sure we don't recurse.
                    buttonView.setChecked(!isChecked);
                    return;
                }

                AppRestrictionsPreference.this.setChecked(isChecked);
            }
        }
    }

    public static void prepareArgs(@NonNull Bundle bundle, int userId, boolean newUser) {
        bundle.putInt(EXTRA_USER_ID, userId);
        bundle.putBoolean(EXTRA_NEW_USER, newUser);
    }

    public static AppRestrictionsFragment newInstance(int userId, boolean newUser) {
        final Bundle args = new Bundle(2);
        prepareArgs(args, userId, newUser);
        AppRestrictionsFragment fragment = new AppRestrictionsFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mUser = new UserHandle(savedInstanceState.getInt(EXTRA_USER_ID));
        } else {
            Bundle args = getArguments();
            if (args != null) {
                if (args.containsKey(EXTRA_USER_ID)) {
                    mUser = new UserHandle(args.getInt(EXTRA_USER_ID));
                }
                mNewUser = args.getBoolean(EXTRA_NEW_USER, false);
            }
        }

        if (mUser == null) {
            mUser = android.os.Process.myUserHandle();
        }

        mHelper = new AppRestrictionsHelper(getContext(), mUser);
        mHelper.setLeanback(true);
        mPackageManager = getActivity().getPackageManager();
        mIPm = AppGlobals.getPackageManager();
        mUserManager = (UserManager) getActivity().getSystemService(Context.USER_SERVICE);
        mRestrictedProfile = mUserManager.getUserInfo(mUser.getIdentifier()).isRestricted();
        try {
            mSysPackageInfo = mPackageManager.getPackageInfo("android",
                    PackageManager.GET_SIGNATURES);
        } catch (PackageManager.NameNotFoundException nnfe) {
            Log.e(TAG, "Could not find system package signatures", nnfe);
        }
        mAppList = getAppPreferenceGroup();
        mAppList.setOrderingAsAdded(false);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        final PreferenceScreen screen = getPreferenceManager()
                .createPreferenceScreen(getPreferenceManager().getContext());
        screen.setTitle(R.string.restricted_profile_configure_apps_title);
        setPreferenceScreen(screen);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(EXTRA_USER_ID, mUser.getIdentifier());
    }

    @Override
    public void onResume() {
        super.onResume();

        getActivity().registerReceiver(mUserBackgrounding,
                new IntentFilter(Intent.ACTION_USER_BACKGROUND));
        IntentFilter packageFilter = new IntentFilter();
        packageFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        packageFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packageFilter.addDataScheme("package");
        getActivity().registerReceiver(mPackageObserver, packageFilter);

        mAppListChanged = false;
        if (mAppLoadingTask == null || mAppLoadingTask.getStatus() == AsyncTask.Status.FINISHED) {
            mAppLoadingTask = new AppLoadingTask().execute();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mNewUser = false;
        getActivity().unregisterReceiver(mUserBackgrounding);
        getActivity().unregisterReceiver(mPackageObserver);
        if (mAppListChanged) {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    mHelper.applyUserAppsStates(AppRestrictionsFragment.this);
                    return null;
                }
            }.execute();
        }
    }

    private void onPackageChanged(Intent intent) {
        String action = intent.getAction();
        String packageName = intent.getData().getSchemeSpecificPart();
        // Package added, check if the preference needs to be enabled
        AppRestrictionsPreference pref = (AppRestrictionsPreference)
                findPreference(getKeyForPackage(packageName));
        if (pref == null) return;

        if ((Intent.ACTION_PACKAGE_ADDED.equals(action) && pref.isChecked())
                || (Intent.ACTION_PACKAGE_REMOVED.equals(action) && !pref.isChecked())) {
            pref.setEnabled(true);
        }
    }

    private PreferenceGroup getAppPreferenceGroup() {
        return getPreferenceScreen();
    }

    @Override
    public void onDisableUiForPackage(String packageName) {
        AppRestrictionsPreference pref = (AppRestrictionsPreference) findPreference(
                getKeyForPackage(packageName));
        if (pref != null) {
            pref.setEnabled(false);
        }
    }

    private class AppLoadingTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            mHelper.fetchAndMergeApps();
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            populateApps();
        }
    }

    private boolean isPlatformSigned(PackageInfo pi) {
        return (pi != null && pi.signatures != null &&
                mSysPackageInfo.signatures[0].equals(pi.signatures[0]));
    }

    private boolean isAppEnabledForUser(PackageInfo pi) {
        if (pi == null) return false;
        final int flags = pi.applicationInfo.flags;
        final int privateFlags = pi.applicationInfo.privateFlags;
        // Return true if it is installed and not hidden
        return ((flags& ApplicationInfo.FLAG_INSTALLED) != 0
                && (privateFlags&ApplicationInfo.PRIVATE_FLAG_HIDDEN) == 0);
    }

    private void populateApps() {
        final Context context = getActivity();
        if (context == null) return;
        final PackageManager pm = mPackageManager;
        final int userId = mUser.getIdentifier();

        // Check if the user was removed in the meantime.
        if (getExistingUser(mUserManager, mUser) == null) {
            return;
        }
        mAppList.removeAll();
        addLocationAppRestrictionsPreference();
        Intent restrictionsIntent = new Intent(Intent.ACTION_GET_RESTRICTION_ENTRIES);
        final List<ResolveInfo> receivers = pm.queryBroadcastReceivers(restrictionsIntent, 0);
        for (AppRestrictionsHelper.SelectableAppInfo app : mHelper.getVisibleApps()) {
            String packageName = app.packageName;
            if (packageName == null) continue;
            final boolean isSettingsApp = packageName.equals(context.getPackageName());
            AppRestrictionsPreference p =
                    new AppRestrictionsPreference(getPreferenceManager().getContext());
            final boolean hasSettings = resolveInfoListHasPackage(receivers, packageName);
            if (isSettingsApp) {
                // Settings app should be available to restricted user
                mHelper.setPackageSelected(packageName, true);
                continue;
            }
            PackageInfo pi = null;
            try {
                pi = mIPm.getPackageInfo(packageName,
                        PackageManager.MATCH_ANY_USER
                                | PackageManager.GET_SIGNATURES, userId);
            } catch (RemoteException e) {
                // Ignore
            }
            if (pi == null) {
                continue;
            }
            if (mRestrictedProfile && isAppUnsupportedInRestrictedProfile(pi)) {
                continue;
            }
            p.setIcon(app.icon != null ? app.icon.mutate() : null);
            p.setChecked(false);
            p.setTitle(app.activityName);
            p.setKey(getKeyForPackage(packageName));
            p.setPersistent(false);
            p.setOnPreferenceChangeListener(this);
            p.setSummary(getPackageSummary(pi, app));
            if (pi.requiredForAllUsers || isPlatformSigned(pi)) {
                p.setChecked(true);
                p.setImmutable(true);
                // If the app is required and has no restrictions, skip showing it
                if (!hasSettings) continue;
            } else if (!mNewUser && isAppEnabledForUser(pi)) {
                p.setChecked(true);
            }
            if (app.masterEntry == null && hasSettings) {
                requestRestrictionsForApp(packageName, p);
            }
            if (app.masterEntry != null) {
                p.setImmutable(true);
                p.setChecked(mHelper.isPackageSelected(packageName));
            }
            p.setOrder(MAX_APP_RESTRICTIONS * (mAppList.getPreferenceCount() + 2));
            mHelper.setPackageSelected(packageName, p.isChecked());
            mAppList.addPreference(p);
        }
        mAppListChanged = true;
        // If this is the first time for a new profile, install/uninstall default apps for profile
        // to avoid taking the hit in onPause(), which can cause race conditions on user switch.
        if (mNewUser && mFirstTime) {
            mFirstTime = false;
            mHelper.applyUserAppsStates(this);
        }
    }

    private String getPackageSummary(PackageInfo pi, AppRestrictionsHelper.SelectableAppInfo app) {
        // Check for 3 cases:
        // - Slave entry that can see primary user accounts
        // - Slave entry that cannot see primary user accounts
        // - Master entry that can see primary user accounts
        // Otherwise no summary is returned
        if (app.masterEntry != null) {
            if (mRestrictedProfile && pi.restrictedAccountType != null) {
                return getString(R.string.app_sees_restricted_accounts_and_controlled_by,
                        app.masterEntry.activityName);
            }
            return getString(R.string.user_restrictions_controlled_by,
                    app.masterEntry.activityName);
        } else if (pi.restrictedAccountType != null) {
            return getString(R.string.app_sees_restricted_accounts);
        }
        return null;
    }

    private static boolean isAppUnsupportedInRestrictedProfile(PackageInfo pi) {
        return pi.requiredAccountType != null && pi.restrictedAccountType == null;
    }

    private void addLocationAppRestrictionsPreference() {
        AppRestrictionsPreference p =
                new AppRestrictionsPreference(getPreferenceManager().getContext());
        String packageName = getContext().getPackageName();
        p.setIcon(R.drawable.ic_location_on);
        p.setKey(getKeyForPackage(packageName));
        ArrayList<RestrictionEntry> restrictions = RestrictionUtils.getRestrictions(
                getActivity(), mUser);
        RestrictionEntry locationRestriction = restrictions.get(0);
        p.setTitle(locationRestriction.getTitle());
        p.setRestrictions(restrictions);
        p.setSummary(locationRestriction.getDescription());
        p.setChecked(locationRestriction.getSelectedState());
        p.setPersistent(false);
        p.setOrder(MAX_APP_RESTRICTIONS);
        mAppList.addPreference(p);
    }

    private String getKeyForPackage(String packageName) {
        return PKG_PREFIX + packageName;
    }

    private String getKeyForPackageActivity(String packageName) {
        return ACTIVITY_PREFIX + packageName;
    }

    private String getPackageFromKey(String key) {
        if (key.startsWith(PKG_PREFIX)) {
            return key.substring(PKG_PREFIX.length());
        } else if (key.startsWith(ACTIVITY_PREFIX)) {
            return key.substring(ACTIVITY_PREFIX.length());
        } else {
            throw new IllegalArgumentException("Tried to extract package from wrong key: " + key);
        }
    }

    private boolean resolveInfoListHasPackage(List<ResolveInfo> receivers, String packageName) {
        for (ResolveInfo info : receivers) {
            if (info.activityInfo.packageName.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    private void updateAllEntries(String prefKey, boolean checked) {
        for (int i = 0; i < mAppList.getPreferenceCount(); i++) {
            Preference pref = mAppList.getPreference(i);
            if (pref instanceof AppRestrictionsPreference) {
                if (prefKey.equals(pref.getKey())) {
                    ((AppRestrictionsPreference) pref).setChecked(checked);
                }
            }
        }
    }

    private void assertSafeToStartCustomActivity(Intent intent, String packageName) {
        // Activity can be started if it belongs to the same app
        if (intent.getPackage() != null && intent.getPackage().equals(packageName)) {
            return;
        }
        // Activity can be started if intent resolves to multiple activities
        List<ResolveInfo> resolveInfos = AppRestrictionsFragment.this.mPackageManager
                .queryIntentActivities(intent, 0 /* no flags */);
        if (resolveInfos.size() != 1) {
            return;
        }
        // Prevent potential privilege escalation
        ActivityInfo activityInfo = resolveInfos.get(0).activityInfo;
        if (!packageName.equals(activityInfo.packageName)) {
            throw new SecurityException("Application " + packageName
                    + " is not allowed to start activity " + intent);
        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference instanceof AppRestrictionsPreference) {
            AppRestrictionsPreference pref = (AppRestrictionsPreference) preference;
            if (!pref.isImmutable()) {
                pref.setChecked(!pref.isChecked());
                final String packageName = getPackageFromKey(pref.getKey());
                // Settings/Location is handled as a top-level entry
                if (packageName.equals(getActivity().getPackageName())) {
                    pref.getRestrictions().get(0).setSelectedState(pref.isChecked());
                    RestrictionUtils.setRestrictions(getActivity(), pref.getRestrictions(), mUser);
                    return true;
                }
                mHelper.setPackageSelected(packageName, pref.isChecked());
                mAppListChanged = true;
                // If it's not a restricted profile, apply the changes immediately
                if (!mRestrictedProfile) {
                    mHelper.applyUserAppState(packageName, pref.isChecked(), this);
                }
                updateAllEntries(pref.getKey(), pref.isChecked());
            }
            return true;
        } else if (preference.getIntent() != null) {
            assertSafeToStartCustomActivity(preference.getIntent(),
                    getPackageFromKey(preference.getKey()));
            startActivityForResult(preference.getIntent(),
                    generateCustomActivityRequestCode(preference));
            return true;
        } else {
            return super.onPreferenceTreeClick(preference);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        if (key != null && key.contains(DELIMITER)) {
            StringTokenizer st = new StringTokenizer(key, DELIMITER);
            final String packageName = st.nextToken();
            final String restrictionKey = st.nextToken();
            AppRestrictionsPreference appPref = (AppRestrictionsPreference)
                    mAppList.findPreference(getKeyForPackage(packageName));
            ArrayList<RestrictionEntry> restrictions = appPref.getRestrictions();
            if (restrictions != null) {
                for (RestrictionEntry entry : restrictions) {
                    if (entry.getKey().equals(restrictionKey)) {
                        switch (entry.getType()) {
                            case RestrictionEntry.TYPE_BOOLEAN:
                                entry.setSelectedState((Boolean) newValue);
                                break;
                            case RestrictionEntry.TYPE_CHOICE:
                            case RestrictionEntry.TYPE_CHOICE_LEVEL:
                                ListPreference listPref = (ListPreference) preference;
                                entry.setSelectedString((String) newValue);
                                String readable = findInArray(entry.getChoiceEntries(),
                                        entry.getChoiceValues(), (String) newValue);
                                listPref.setSummary(readable);
                                break;
                            case RestrictionEntry.TYPE_MULTI_SELECT:
                                // noinspection unchecked
                                Set<String> set = (Set<String>) newValue;
                                String [] selectedValues = new String[set.size()];
                                set.toArray(selectedValues);
                                entry.setAllSelectedStrings(selectedValues);
                                break;
                            default:
                                continue;
                        }
                        mUserManager.setApplicationRestrictions(packageName,
                                RestrictionsManager.convertRestrictionsToBundle(restrictions),
                                mUser);
                        break;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Send a broadcast to the app to query its restrictions
     * @param packageName package name of the app with restrictions
     * @param preference the preference item for the app toggle
     */
    private void requestRestrictionsForApp(String packageName,
            AppRestrictionsPreference preference) {
        Bundle oldEntries =
                mUserManager.getApplicationRestrictions(packageName, mUser);
        Intent intent = new Intent(Intent.ACTION_GET_RESTRICTION_ENTRIES);
        intent.setPackage(packageName);
        intent.putExtra(Intent.EXTRA_RESTRICTIONS_BUNDLE, oldEntries);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        getActivity().sendOrderedBroadcast(intent, null,
                new RestrictionsResultReceiver(packageName, preference),
                null, Activity.RESULT_OK, null, null);
    }

    private class RestrictionsResultReceiver extends BroadcastReceiver {

        private static final String CUSTOM_RESTRICTIONS_INTENT = Intent.EXTRA_RESTRICTIONS_INTENT;
        private final String mPackageName;
        private final AppRestrictionsPreference mPreference;

        RestrictionsResultReceiver(String packageName, AppRestrictionsPreference preference) {
            super();
            mPackageName = packageName;
            mPreference = preference;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle results = getResultExtras(true);
            final ArrayList<RestrictionEntry> restrictions = results.getParcelableArrayList(
                    Intent.EXTRA_RESTRICTIONS_LIST);
            Intent restrictionsIntent = results.getParcelable(CUSTOM_RESTRICTIONS_INTENT);
            if (restrictions != null && restrictionsIntent == null) {
                onRestrictionsReceived(mPreference, restrictions);
                if (mRestrictedProfile) {
                    mUserManager.setApplicationRestrictions(mPackageName,
                            RestrictionsManager.convertRestrictionsToBundle(restrictions), mUser);
                }
            } else if (restrictionsIntent != null) {
                mPreference.setRestrictions(null);
                mPreference.removeAll();
                final Preference p = new Preference(mPreference.getContext());
                p.setKey(getKeyForPackageActivity(mPackageName));
                p.setIcon(BLANK_DRAWABLE);
                p.setTitle(R.string.restricted_profile_customize_restrictions);
                p.setIntent(restrictionsIntent);
                mPreference.addPreference(p);
            } else {
                Log.e(TAG, "No restrictions returned from " + mPackageName);
            }
        }
    }

    private void onRestrictionsReceived(AppRestrictionsPreference preference,
            ArrayList<RestrictionEntry> restrictions) {
        // Remove any earlier restrictions
        preference.removeAll();
        // Non-custom-activity case - expand the restrictions in-place
        int count = 1;
        final Context themedContext = getPreferenceManager().getContext();
        for (RestrictionEntry entry : restrictions) {
            Preference p = null;
            switch (entry.getType()) {
                case RestrictionEntry.TYPE_BOOLEAN:
                    p = new SwitchPreference(themedContext);
                    p.setTitle(entry.getTitle());
                    p.setSummary(entry.getDescription());
                    ((SwitchPreference)p).setChecked(entry.getSelectedState());
                    break;
                case RestrictionEntry.TYPE_CHOICE:
                case RestrictionEntry.TYPE_CHOICE_LEVEL:
                    p = new ListPreference(themedContext);
                    p.setTitle(entry.getTitle());
                    String value = entry.getSelectedString();
                    if (value == null) {
                        value = entry.getDescription();
                    }
                    p.setSummary(findInArray(entry.getChoiceEntries(), entry.getChoiceValues(),
                            value));
                    ((ListPreference)p).setEntryValues(entry.getChoiceValues());
                    ((ListPreference)p).setEntries(entry.getChoiceEntries());
                    ((ListPreference)p).setValue(value);
                    ((ListPreference)p).setDialogTitle(entry.getTitle());
                    break;
                case RestrictionEntry.TYPE_MULTI_SELECT:
                    p = new MultiSelectListPreference(themedContext);
                    p.setTitle(entry.getTitle());
                    ((MultiSelectListPreference)p).setEntryValues(entry.getChoiceValues());
                    ((MultiSelectListPreference)p).setEntries(entry.getChoiceEntries());
                    HashSet<String> set = new HashSet<>();
                    Collections.addAll(set, entry.getAllSelectedStrings());
                    ((MultiSelectListPreference)p).setValues(set);
                    ((MultiSelectListPreference)p).setDialogTitle(entry.getTitle());
                    break;
                case RestrictionEntry.TYPE_NULL:
                default:
            }
            if (p != null) {
                p.setPersistent(false);
                p.setOrder(preference.getOrder() + count);
                // Store the restrictions key string as a key for the preference
                p.setKey(getPackageFromKey(preference.getKey()) + DELIMITER + entry.getKey());
                preference.addPreference(p);
                p.setOnPreferenceChangeListener(AppRestrictionsFragment.this);
                p.setIcon(BLANK_DRAWABLE);
                count++;
            }
        }
        preference.setRestrictions(restrictions);
        if (count == 1 // No visible restrictions
                && preference.isImmutable()
                && preference.isChecked()) {
            // Special case of required app with no visible restrictions. Remove it
            mAppList.removePreference(preference);
        }
    }

    /**
     * Generates a request code that is stored in a map to retrieve the associated
     * AppRestrictionsPreference.
     */
    private int generateCustomActivityRequestCode(Preference preference) {
        mCustomRequestCode++;
        final String key = getKeyForPackage(getPackageFromKey(preference.getKey()));
        mCustomRequestMap.put(mCustomRequestCode, key);
        return mCustomRequestCode;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        AppRestrictionsPreference pref =
                (AppRestrictionsPreference) findPreference(mCustomRequestMap.get(requestCode));
        if (pref == null) {
            Log.w(TAG, "Unknown requestCode " + requestCode);
            return;
        }

        if (resultCode == Activity.RESULT_OK) {
            String packageName = getPackageFromKey(pref.getKey());
            ArrayList<RestrictionEntry> list =
                    data.getParcelableArrayListExtra(Intent.EXTRA_RESTRICTIONS_LIST);
            Bundle bundle = data.getBundleExtra(Intent.EXTRA_RESTRICTIONS_BUNDLE);
            if (list != null) {
                // If there's a valid result, persist it to the user manager.
                pref.setRestrictions(list);
                mUserManager.setApplicationRestrictions(packageName,
                        RestrictionsManager.convertRestrictionsToBundle(list), mUser);
            } else if (bundle != null) {
                // If there's a valid result, persist it to the user manager.
                mUserManager.setApplicationRestrictions(packageName, bundle, mUser);
            }
        }
        // Remove request from the map
        mCustomRequestMap.remove(requestCode);
    }

    private String findInArray(String[] choiceEntries, String[] choiceValues,
            String selectedString) {
        for (int i = 0; i < choiceValues.length; i++) {
            if (choiceValues[i].equals(selectedString)) {
                return choiceEntries[i];
            }
        }
        return selectedString;
    }

    /**
     * Queries for the UserInfo of a user. Returns null if the user doesn't exist (was removed).
     * @param userManager Instance of UserManager
     * @param checkUser The user to check the existence of.
     * @return UserInfo of the user or null for non-existent user.
     */
    private static UserInfo getExistingUser(UserManager userManager, UserHandle checkUser) {
        final List<UserInfo> users = userManager.getUsers(true /* excludeDying */);
        final int checkUserId = checkUser.getIdentifier();
        for (UserInfo user : users) {
            if (user.id == checkUserId) {
                return user;
            }
        }
        return null;
    }
}
