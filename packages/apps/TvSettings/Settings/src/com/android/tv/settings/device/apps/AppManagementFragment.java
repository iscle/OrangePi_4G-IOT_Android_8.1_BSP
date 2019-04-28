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

package com.android.tv.settings.device.apps;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.IPackageDataObserver;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.support.annotation.NonNull;
import android.support.v17.preference.LeanbackPreferenceFragment;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.android.settingslib.applications.ApplicationsState;
import com.android.tv.settings.R;

import java.util.ArrayList;

public class AppManagementFragment extends LeanbackPreferenceFragment {
    private static final String TAG = "AppManagementFragment";

    private static final String ARG_PACKAGE_NAME = "packageName";

    private static final String KEY_VERSION = "version";
    private static final String KEY_OPEN = "open";
    private static final String KEY_FORCE_STOP = "forceStop";
    private static final String KEY_UNINSTALL = "uninstall";
    private static final String KEY_ENABLE_DISABLE = "enableDisable";
    private static final String KEY_APP_STORAGE = "appStorage";
    private static final String KEY_CLEAR_DATA = "clearData";
    private static final String KEY_CLEAR_CACHE = "clearCache";
    private static final String KEY_CLEAR_DEFAULTS = "clearDefaults";
    private static final String KEY_NOTIFICATIONS = "notifications";
    private static final String KEY_PERMISSIONS = "permissions";

    // Result code identifiers
    private static final int REQUEST_UNINSTALL = 1;
    private static final int REQUEST_MANAGE_SPACE = 2;
    private static final int REQUEST_UNINSTALL_UPDATES = 3;

    private PackageManager mPackageManager;
    private String mPackageName;
    private ApplicationsState mApplicationsState;
    private ApplicationsState.Session mSession;
    private ApplicationsState.AppEntry mEntry;
    private final ApplicationsState.Callbacks mCallbacks = new ApplicationsStateCallbacks();

    private ForceStopPreference mForceStopPreference;
    private UninstallPreference mUninstallPreference;
    private EnableDisablePreference mEnableDisablePreference;
    private AppStoragePreference mAppStoragePreference;
    private ClearDataPreference mClearDataPreference;
    private ClearCachePreference mClearCachePreference;
    private ClearDefaultsPreference mClearDefaultsPreference;
    private NotificationsPreference mNotificationsPreference;

    private final Handler mHandler = new Handler();
    private Runnable mBailoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (isResumed() && !getFragmentManager().popBackStackImmediate()) {
                getActivity().onBackPressed();
            }
        }
    };

    public static void prepareArgs(@NonNull Bundle args, String packageName) {
        args.putString(ARG_PACKAGE_NAME, packageName);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mPackageName = getArguments().getString(ARG_PACKAGE_NAME);

        final Activity activity = getActivity();
        mPackageManager = activity.getPackageManager();
        mApplicationsState = ApplicationsState.getInstance(activity.getApplication());
        mSession = mApplicationsState.newSession(mCallbacks);
        mEntry = mApplicationsState.getEntry(mPackageName, UserHandle.myUserId());

        super.onCreate(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        mSession.resume();

        if (mEntry == null) {
            Log.w(TAG, "App not found, trying to bail out");
            navigateBack();
        }

        if (mClearDefaultsPreference != null) {
            mClearDefaultsPreference.refresh();
        }
        if (mEnableDisablePreference != null) {
            mEnableDisablePreference.refresh();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mSession.pause();
        mHandler.removeCallbacks(mBailoutRunnable);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (mEntry == null) {
            return;
        }
        switch (requestCode) {
            case REQUEST_UNINSTALL:
                final int deleteResult = data != null
                        ? data.getIntExtra(Intent.EXTRA_INSTALL_RESULT, 0) : 0;
                if (deleteResult == PackageManager.DELETE_SUCCEEDED) {
                    final int userId =  UserHandle.getUserId(mEntry.info.uid);
                    mApplicationsState.removePackage(mPackageName, userId);
                    navigateBack();
                } else {
                    Log.e(TAG, "Uninstall failed with result " + deleteResult);
                }
                break;
            case REQUEST_MANAGE_SPACE:
                mClearDataPreference.setClearingData(false);
                if(resultCode == Activity.RESULT_OK) {
                    final int userId = UserHandle.getUserId(mEntry.info.uid);
                    mApplicationsState.requestSize(mPackageName, userId);
                } else {
                    Log.w(TAG, "Failed to clear data!");
                }
                break;
            case REQUEST_UNINSTALL_UPDATES:
                mUninstallPreference.refresh();
                break;
        }
    }

    private void navigateBack() {
        // need to post this to avoid recursing in the fragment manager.
        mHandler.removeCallbacks(mBailoutRunnable);
        mHandler.post(mBailoutRunnable);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        final Intent intent = preference.getIntent();
        if (intent != null) {
            try {
                if (preference.equals(mUninstallPreference)) {
                    startActivityForResult(intent, mUninstallPreference.canUninstall()
                            ? REQUEST_UNINSTALL : REQUEST_UNINSTALL_UPDATES);
                } else {
                    startActivity(intent);
                }
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "Could not find activity to launch", e);
                Toast.makeText(getContext(), R.string.device_apps_app_management_not_available,
                        Toast.LENGTH_SHORT).show();
            }
            return true;
        } else {
            return super.onPreferenceTreeClick(preference);
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        final Context themedContext = getPreferenceManager().getContext();
        final PreferenceScreen screen =
                getPreferenceManager().createPreferenceScreen(themedContext);
        screen.setTitle(getAppName());
        setPreferenceScreen(screen);

        updatePrefs();
    }

    private void updatePrefs() {
        if (mEntry == null) {
            final PreferenceScreen screen = getPreferenceScreen();
            screen.removeAll();
            return;
        }
        final Context themedContext = getPreferenceManager().getContext();

        // Version
        Preference versionPreference = findPreference(KEY_VERSION);
        if (versionPreference == null) {
            versionPreference = new Preference(themedContext);
            versionPreference.setKey(KEY_VERSION);
            replacePreference(versionPreference);
            versionPreference.setSelectable(false);
        }
        versionPreference.setTitle(getString(R.string.device_apps_app_management_version,
                mEntry.getVersion(getActivity())));
        versionPreference.setSummary(mPackageName);

        // Open
        Preference openPreference = findPreference(KEY_OPEN);
        if (openPreference == null) {
            openPreference = new Preference(themedContext);
            openPreference.setKey(KEY_OPEN);
            replacePreference(openPreference);
        }
        Intent appLaunchIntent =
                mPackageManager.getLeanbackLaunchIntentForPackage(mEntry.info.packageName);
        if (appLaunchIntent == null) {
            appLaunchIntent = mPackageManager.getLaunchIntentForPackage(mEntry.info.packageName);
        }
        if (appLaunchIntent != null) {
            openPreference.setIntent(appLaunchIntent);
            openPreference.setTitle(R.string.device_apps_app_management_open);
            openPreference.setVisible(true);
        } else {
            openPreference.setVisible(false);
        }

        // Force stop
        if (mForceStopPreference == null) {
            mForceStopPreference = new ForceStopPreference(themedContext, mEntry);
            mForceStopPreference.setKey(KEY_FORCE_STOP);
            replacePreference(mForceStopPreference);
        } else {
            mForceStopPreference.setEntry(mEntry);
        }

        // Uninstall
        if (mUninstallPreference == null) {
            mUninstallPreference = new UninstallPreference(themedContext, mEntry);
            mUninstallPreference.setKey(KEY_UNINSTALL);
            replacePreference(mUninstallPreference);
        } else {
            mUninstallPreference.setEntry(mEntry);
        }

        // Disable/Enable
        if (mEnableDisablePreference == null) {
            mEnableDisablePreference = new EnableDisablePreference(themedContext, mEntry);
            mEnableDisablePreference.setKey(KEY_ENABLE_DISABLE);
            replacePreference(mEnableDisablePreference);
        } else {
            mEnableDisablePreference.setEntry(mEntry);
        }

        // Storage used
        if (mAppStoragePreference == null) {
            mAppStoragePreference = new AppStoragePreference(themedContext, mEntry);
            mAppStoragePreference.setKey(KEY_APP_STORAGE);
            replacePreference(mAppStoragePreference);
        } else {
            mAppStoragePreference.setEntry(mEntry);
        }

        // Clear data
        if (mClearDataPreference == null) {
            mClearDataPreference = new ClearDataPreference(themedContext, mEntry);
            mClearDataPreference.setKey(KEY_CLEAR_DATA);
            replacePreference(mClearDataPreference);
        } else {
            mClearDataPreference.setEntry(mEntry);
        }

        // Clear cache
        if (mClearCachePreference == null) {
            mClearCachePreference = new ClearCachePreference(themedContext, mEntry);
            mClearCachePreference.setKey(KEY_CLEAR_CACHE);
            replacePreference(mClearCachePreference);
        } else {
            mClearCachePreference.setEntry(mEntry);
        }

        // Clear defaults
        if (mClearDefaultsPreference == null) {
            mClearDefaultsPreference = new ClearDefaultsPreference(themedContext, mEntry);
            mClearDefaultsPreference.setKey(KEY_CLEAR_DEFAULTS);
            replacePreference(mClearDefaultsPreference);
        } else {
            mClearDefaultsPreference.setEntry(mEntry);
        }

        // Notifications
        if (mNotificationsPreference == null) {
            mNotificationsPreference = new NotificationsPreference(themedContext, mEntry);
            mNotificationsPreference.setKey(KEY_NOTIFICATIONS);
            replacePreference(mNotificationsPreference);
        } else {
            mNotificationsPreference.setEntry(mEntry);
        }

        // Permissions
        Preference permissionsPreference = findPreference(KEY_PERMISSIONS);
        if (permissionsPreference == null) {
            permissionsPreference = new Preference(themedContext);
            permissionsPreference.setKey(KEY_PERMISSIONS);
            permissionsPreference.setTitle(R.string.device_apps_app_management_permissions);
            replacePreference(permissionsPreference);
        }
        permissionsPreference.setIntent(new Intent(Intent.ACTION_MANAGE_APP_PERMISSIONS)
                .putExtra(Intent.EXTRA_PACKAGE_NAME, mPackageName));
    }

    private void replacePreference(Preference preference) {
        final String key = preference.getKey();
        if (TextUtils.isEmpty(key)) {
            throw new IllegalArgumentException("Can't replace a preference without a key");
        }
        final Preference old = findPreference(key);
        if (old != null) {
            getPreferenceScreen().removePreference(old);
        }
        getPreferenceScreen().addPreference(preference);
    }

    public String getAppName() {
        if (mEntry == null) {
            return null;
        }
        mEntry.ensureLabel(getActivity());
        return mEntry.label;
    }

    public Drawable getAppIcon() {
        if (mEntry == null) {
            return null;
        }
        mApplicationsState.ensureIcon(mEntry);
        return mEntry.icon;
    }

    public void clearData() {
        mClearDataPreference.setClearingData(true);
        String spaceManagementActivityName = mEntry.info.manageSpaceActivityName;
        if (spaceManagementActivityName != null) {
            if (!ActivityManager.isUserAMonkey()) {
                Intent intent = new Intent(Intent.ACTION_DEFAULT);
                intent.setClassName(mEntry.info.packageName, spaceManagementActivityName);
                startActivityForResult(intent, REQUEST_MANAGE_SPACE);
            }
        } else {
            ActivityManager am = (ActivityManager) getActivity().getSystemService(
                    Context.ACTIVITY_SERVICE);
            boolean success = am.clearApplicationUserData(
                    mEntry.info.packageName, new IPackageDataObserver.Stub() {
                        public void onRemoveCompleted(
                                final String packageName, final boolean succeeded) {
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    mClearDataPreference.setClearingData(false);
                                    if (succeeded) {
                                        dataCleared(true);
                                    } else {
                                        dataCleared(false);
                                    }
                                }
                            });
                        }
                    });
            if (!success) {
                mClearDataPreference.setClearingData(false);
                dataCleared(false);
            }
        }
        mClearDataPreference.refresh();
    }

    private void dataCleared(boolean succeeded) {
        if (succeeded) {
            final int userId =  UserHandle.getUserId(mEntry.info.uid);
            mApplicationsState.requestSize(mPackageName, userId);
        } else {
            Log.w(TAG, "Failed to clear data!");
            mClearDataPreference.refresh();
        }
    }

    public void clearCache() {
        mClearCachePreference.setClearingCache(true);
        mPackageManager.deleteApplicationCacheFiles(mEntry.info.packageName,
                new IPackageDataObserver.Stub() {
                    public void onRemoveCompleted(final String packageName,
                            final boolean succeeded) {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mClearCachePreference.setClearingCache(false);
                                cacheCleared(succeeded);
                            }
                        });
                    }
                });
        mClearCachePreference.refresh();
    }

    private void cacheCleared(boolean succeeded) {
        if (succeeded) {
            final int userId =  UserHandle.getUserId(mEntry.info.uid);
            mApplicationsState.requestSize(mPackageName, userId);
        } else {
            Log.w(TAG, "Failed to clear cache!");
            mClearCachePreference.refresh();
        }
    }

    private class ApplicationsStateCallbacks implements ApplicationsState.Callbacks {

        @Override
        public void onRunningStateChanged(boolean running) {
            if (mForceStopPreference != null) {
                mForceStopPreference.refresh();
            }
        }

        @Override
        public void onPackageListChanged() {
            if (mEntry == null) {
                return;
            }
            final int userId = UserHandle.getUserId(mEntry.info.uid);
            mEntry = mApplicationsState.getEntry(mPackageName, userId);
            if (mEntry == null) {
                navigateBack();
            }
            updatePrefs();
        }

        @Override
        public void onRebuildComplete(ArrayList<ApplicationsState.AppEntry> apps) {}

        @Override
        public void onPackageIconChanged() {}

        @Override
        public void onPackageSizeChanged(String packageName) {
            if (mAppStoragePreference == null) {
                // Nothing to do here.
                return;
            }
            mAppStoragePreference.refresh();
            mClearDataPreference.refresh();
            mClearCachePreference.refresh();
        }

        @Override
        public void onAllSizesComputed() {
            if (mAppStoragePreference == null) {
                // Nothing to do here.
                return;
            }
            mAppStoragePreference.refresh();
            mClearDataPreference.refresh();
            mClearCachePreference.refresh();
        }

        @Override
        public void onLauncherInfoChanged() {
            updatePrefs();
        }

        @Override
        public void onLoadEntriesCompleted() {
            if (mAppStoragePreference == null) {
                // Nothing to do here.
                return;
            }
            mAppStoragePreference.refresh();
            mClearDataPreference.refresh();
            mClearCachePreference.refresh();
        }
    }
}
