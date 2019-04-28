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
 * limitations under the License.
 */

package com.android.storagemanager.deletionhelper;

import android.app.Activity;
import android.app.LoaderManager;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Loader;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.storage.VolumeInfo;
import android.util.ArraySet;
import android.util.Log;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settingslib.applications.PackageManagerWrapperImpl;
import com.android.settingslib.applications.StorageStatsSource;
import com.android.storagemanager.deletionhelper.AppsAsyncLoader.AppFilter;
import com.android.storagemanager.deletionhelper.AppsAsyncLoader.PackageInfo;
import java.util.HashSet;
import java.util.List;

/**
 * AppDeletionType provides a list of apps which have not been used for a while on the system. It
 * also provides the functionality to clear out these apps.
 */
public class AppDeletionType
        implements LoaderManager.LoaderCallbacks<List<PackageInfo>>, DeletionType {
    public static final String EXTRA_CHECKED_SET = "checkedSet";
    private static final String TAG = "AppDeletionType";
    private static final int LOADER_ID = 25;
    public static final String THRESHOLD_TYPE_KEY = "threshold_type";
    public static final int BUNDLE_CAPACITY = 1;

    private FreeableChangedListener mListener;
    private AppListener mAppListener;
    private HashSet<String> mCheckedApplications;
    private Context mContext;
    private int mThresholdType;
    private List<PackageInfo> mApps;
    private int mLoadingStatus;

    public AppDeletionType(
            DeletionHelperSettings fragment,
            HashSet<String> checkedApplications,
            int thresholdType) {
        mLoadingStatus = LoadingStatus.LOADING;
        mThresholdType = thresholdType;
        mContext = fragment.getContext();
        if (checkedApplications != null) {
            mCheckedApplications = checkedApplications;
        } else {
            mCheckedApplications = new HashSet<>();
        }
        Bundle bundle = new Bundle(BUNDLE_CAPACITY);
        bundle.putInt(THRESHOLD_TYPE_KEY, mThresholdType);
        // NOTE: This is not responsive to package changes. Bug filed for seeing if feature is
        // necessary b/35065979
        fragment.getLoaderManager().initLoader(LOADER_ID, bundle, this);
    }

    @Override
    public void registerFreeableChangedListener(FreeableChangedListener listener) {
        mListener = listener;
    }

    @Override
    public void onResume() {

    }

    @Override
    public void onPause() {

    }

    @Override
    public void onSaveInstanceStateBundle(Bundle savedInstanceState) {
        savedInstanceState.putSerializable(EXTRA_CHECKED_SET, mCheckedApplications);
    }

    @Override
    public void clearFreeableData(Activity activity) {
        if (mApps == null) {
            return;
        }

        ArraySet<String> apps = new ArraySet<>();
        for (PackageInfo app : mApps) {
            final String packageName = app.packageName;
            if (mCheckedApplications.contains(packageName)) {
                apps.add(packageName);
            }
        }
        // TODO: If needed, add an action on the callback.
        PackageDeletionTask task = new PackageDeletionTask(activity.getPackageManager(), apps,
                new PackageDeletionTask.Callback() {
                    @Override
                    public void onSuccess() {
                    }

                    @Override
                    public void onError() {
                        Log.e(TAG, "An error occurred while uninstalling packages.");
                        MetricsLogger.action(activity,
                                MetricsEvent.ACTION_DELETION_HELPER_APPS_DELETION_FAIL);
                    }
                });

        task.run();
    }

    /**
     * Registers a preference group view to notify when the app list changes.
     */
    public void registerView(AppDeletionPreferenceGroup preference) {
        mAppListener = preference;
    }

    /**
     * Set a package to be checked for deletion, if the apps are cleared.
     * @param packageName The name of the package to potentially delete.
     * @param isChecked Whether or not the package should be deleted.
     */
    public void setChecked(String packageName, boolean isChecked) {
        if (isChecked) {
            mCheckedApplications.add(packageName);
        } else {
            mCheckedApplications.remove(packageName);
        }
        maybeNotifyListener();
    }

    /**
     * Returns an amount of clearable app data.
     * @param countUnchecked If unchecked applications should be counted for size purposes.
     */
    public long getTotalAppsFreeableSpace(boolean countUnchecked) {
        long freeableSpace = 0;
        if (mApps != null) {
            for (int i = 0, size = mApps.size(); i < size; i++) {
                final PackageInfo app = mApps.get(i);
                long appSize = app.size;
                final String packageName = app.packageName;
                // If the appSize is negative, it is either an unknown size or an error occurred.
                if ((countUnchecked || mCheckedApplications.contains(packageName)) && appSize > 0) {
                    freeableSpace += appSize;
                }
            }
        }

        return freeableSpace;
    }

    /**
     * Returns if a given package is slated for deletion.
     * @param packageName The name of the package to check.
     */
    public boolean isChecked(String packageName) {
        return mCheckedApplications.contains(packageName);
    }

    private AppFilter getFilter(int mThresholdType) {
        switch (mThresholdType) {
            case AppsAsyncLoader.NO_THRESHOLD:
                return AppsAsyncLoader.FILTER_NO_THRESHOLD;
            case AppsAsyncLoader.NORMAL_THRESHOLD:
            default:
                return AppsAsyncLoader.FILTER_USAGE_STATS;
        }
    }

    private void maybeNotifyListener() {
        if (mListener != null) {
            mListener.onFreeableChanged(
                    mApps.size(),
                    getTotalAppsFreeableSpace(DeletionHelperSettings.COUNT_CHECKED_ONLY));
        }
    }

    public long getDeletionThreshold() {
        switch (mThresholdType) {
            case AppsAsyncLoader.NO_THRESHOLD:
                // The threshold is actually Long.MIN_VALUE but we don't want to display that to
                // the user.
                return 0;
            case AppsAsyncLoader.NORMAL_THRESHOLD:
            default:
                return AppsAsyncLoader.UNUSED_DAYS_DELETION_THRESHOLD;
        }
    }

    @Override
    public int getLoadingStatus() {
        return mLoadingStatus;
    }

    @Override
    public int getContentCount() {
        return mApps.size();
    }

    @Override
    public void setLoadingStatus(@LoadingStatus int loadingStatus) {
        mLoadingStatus = loadingStatus;
    }

    @Override
    public Loader<List<PackageInfo>> onCreateLoader(int id, Bundle args) {
        return new AppsAsyncLoader.Builder(mContext)
                .setUid(UserHandle.myUserId())
                .setUuid(VolumeInfo.ID_PRIVATE_INTERNAL)
                .setStorageStatsSource(new StorageStatsSource(mContext))
                .setPackageManager(new PackageManagerWrapperImpl(mContext.getPackageManager()))
                .setUsageStatsManager(
                        (UsageStatsManager) mContext.getSystemService(Context.USAGE_STATS_SERVICE))
                .setFilter(
                        getFilter(
                                args.getInt(THRESHOLD_TYPE_KEY, AppsAsyncLoader.NORMAL_THRESHOLD)))
                .build();
    }

    @Override
    public void onLoadFinished(Loader<List<PackageInfo>> loader, List<PackageInfo> data) {
        mApps = data;
        updateLoadingStatus();
        maybeNotifyListener();
        mAppListener.onAppRebuild(mApps);
    }

    @Override
    public void onLoaderReset(Loader<List<PackageInfo>> loader) {}

    /**
     * An interface for listening for when the app list has been rebuilt.
     */
    public interface AppListener {
        /**
         * Callback to be called once the app list is rebuilt.
         *
         * @param apps A list of eligible, clearable AppEntries.
         */
        void onAppRebuild(List<PackageInfo> apps);
    }
}
