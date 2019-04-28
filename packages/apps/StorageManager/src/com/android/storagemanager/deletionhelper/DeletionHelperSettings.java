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

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.storage.StorageManager;
import android.support.annotation.VisibleForTesting;
import android.support.v14.preference.PreferenceFragment;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.util.Preconditions;
import com.android.settingslib.HelpUtils;
import com.android.settingslib.applications.AppUtils;
import com.android.storagemanager.ButtonBarProvider;
import com.android.storagemanager.R;
import com.android.storagemanager.overlay.DeletionHelperFeatureProvider;
import com.android.storagemanager.overlay.FeatureFactory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Settings screen for the deletion helper, which manually removes data which is not recently used.
 */
public class DeletionHelperSettings extends PreferenceFragment
        implements DeletionType.FreeableChangedListener, View.OnClickListener {
    public static final boolean COUNT_UNCHECKED = true;
    public static final boolean COUNT_CHECKED_ONLY = false;

    protected static final String APPS_KEY = "apps_group";
    protected static final String KEY_DOWNLOADS_PREFERENCE = "delete_downloads";
    protected static final String KEY_PHOTOS_VIDEOS_PREFERENCE = "delete_photos";
    protected static final String KEY_GAUGE_PREFERENCE = "deletion_gauge";

    private static final String THRESHOLD_KEY = "threshold_key";
    private static final int DOWNLOADS_LOADER_ID = 1;
    private static final int NUM_DELETION_TYPES = 3;
    private static final long UNSET = -1;

    private List<DeletionType> mDeletableContentList;
    private AppDeletionPreferenceGroup mApps;
    @VisibleForTesting AppDeletionType mAppBackend;
    private DownloadsDeletionPreferenceGroup mDownloadsPreference;
    private DownloadsDeletionType mDownloadsDeletion;
    private PhotosDeletionPreference mPhotoPreference;
    private Preference mGaugePreference;
    private DeletionType mPhotoVideoDeletion;
    private Button mCancel, mFree;
    private DeletionHelperFeatureProvider mProvider;
    private int mThresholdType;
    private LoadingSpinnerController mLoadingController;

    public static DeletionHelperSettings newInstance(int thresholdType) {
        DeletionHelperSettings instance = new DeletionHelperSettings();
        Bundle bundle = new Bundle(1);
        bundle.putInt(THRESHOLD_KEY, thresholdType);
        instance.setArguments(bundle);
        return instance;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.deletion_helper_list);
        mThresholdType = getArguments().getInt(THRESHOLD_KEY, AppsAsyncLoader.NORMAL_THRESHOLD);
        mApps = (AppDeletionPreferenceGroup) findPreference(APPS_KEY);
        mPhotoPreference = (PhotosDeletionPreference) findPreference(KEY_PHOTOS_VIDEOS_PREFERENCE);
        mProvider = FeatureFactory.getFactory(getActivity()).getDeletionHelperFeatureProvider();
        mLoadingController = new LoadingSpinnerController((DeletionHelperActivity) getActivity());
        if (mProvider != null) {
            mPhotoVideoDeletion =
                    mProvider.createPhotoVideoDeletionType(getContext(), mThresholdType);
        }

        HashSet<String> checkedApplications = null;
        if (savedInstanceState != null) {
            checkedApplications =
                    (HashSet<String>) savedInstanceState.getSerializable(
                            AppDeletionType.EXTRA_CHECKED_SET);
        }
        mAppBackend = new AppDeletionType(this, checkedApplications, mThresholdType);
        mAppBackend.registerView(mApps);
        mAppBackend.registerFreeableChangedListener(this);
        mApps.setDeletionType(mAppBackend);

        mDeletableContentList = new ArrayList<>(NUM_DELETION_TYPES);

        mGaugePreference = findPreference(KEY_GAUGE_PREFERENCE);
        Activity activity = getActivity();
        if (activity != null && mGaugePreference != null) {
            Intent intent = activity.getIntent();
            if (intent != null) {
                CharSequence gaugeTitle =
                        getGaugeString(getContext(), intent, activity.getCallingPackage());
                if (gaugeTitle != null) {
                    mGaugePreference.setTitle(gaugeTitle);
                } else {
                    getPreferenceScreen().removePreference(mGaugePreference);
                }
            }
        }
    }

    protected static CharSequence getGaugeString(
            Context context, Intent intent, String packageName) {
        Preconditions.checkNotNull(intent);
        long requestedBytes = intent.getLongExtra(StorageManager.EXTRA_REQUESTED_BYTES, UNSET);
        if (requestedBytes > 0) {
            CharSequence callerLabel =
                    AppUtils.getApplicationLabel(context.getPackageManager(), packageName);
            // I really hope this isn't the case, but I can't ignore the possibility that we cannot
            // determine what app the referrer is.
            if (callerLabel == null) {
                return null;
            }
            return context.getString(
                    R.string.app_requesting_space,
                    callerLabel,
                    Formatter.formatFileSize(context, requestedBytes));
        }
        return null;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        initializeButtons();
        setHasOptionsMenu(true);
        Activity activity = getActivity();
        if (activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(
                    new String[] {Manifest.permission.READ_EXTERNAL_STORAGE},
                    0);
        }

        if (mProvider != null && mPhotoVideoDeletion != null) {
            mPhotoPreference.setDaysToKeep(mProvider.getDaysToKeep(mThresholdType));
            mPhotoPreference.registerFreeableChangedListener(this);
            mPhotoPreference.registerDeletionService(mPhotoVideoDeletion);
            mDeletableContentList.add(mPhotoVideoDeletion);
        } else {
            getPreferenceScreen().removePreference(mPhotoPreference);
            mPhotoPreference.setEnabled(false);
        }

        String[] uncheckedFiles = null;
        if (savedInstanceState != null) {
            uncheckedFiles =
                    savedInstanceState.getStringArray(
                            DownloadsDeletionType.EXTRA_UNCHECKED_DOWNLOADS);
        }
        mDownloadsPreference =
                (DownloadsDeletionPreferenceGroup) findPreference(KEY_DOWNLOADS_PREFERENCE);
        mDownloadsDeletion = new DownloadsDeletionType(getActivity(), uncheckedFiles);
        mDownloadsPreference.registerFreeableChangedListener(this);
        mDownloadsPreference.registerDeletionService(mDownloadsDeletion);
        mDeletableContentList.add(mDownloadsDeletion);
        if (isEmptyState()) {
            setupEmptyState();
        }
        mDeletableContentList.add(mAppBackend);
        updateFreeButtonText();
    }

    @VisibleForTesting
    void setupEmptyState() {
        final PreferenceScreen screen = getPreferenceScreen();
        if (mDownloadsPreference != null) {
            mDownloadsPreference.setChecked(false);
            screen.removePreference(mDownloadsPreference);
        }
        screen.removePreference(mApps);

        // Nulling out the downloads preferences means we won't accidentally delete what isn't
        // visible.
        mDownloadsDeletion = null;
        mDownloadsPreference = null;
    }

    private boolean isEmptyState() {
        // We know we are in the empty state if our loader is not using a threshold.
        return mThresholdType == AppsAsyncLoader.NO_THRESHOLD;
    }

    @Override
    public void onResume() {
        super.onResume();

        mLoadingController.initializeLoading(getListView());

        for (int i = 0, size = mDeletableContentList.size(); i < size; i++) {
            mDeletableContentList.get(i).onResume();
        }

        if (mDownloadsDeletion != null
                && getActivity().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED) {
            getLoaderManager().initLoader(DOWNLOADS_LOADER_ID, new Bundle(), mDownloadsDeletion);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        for (int i = 0, size = mDeletableContentList.size(); i < size; i++) {
            mDeletableContentList.get(i).onPause();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        for (int i = 0, size = mDeletableContentList.size(); i < size; i++) {
            mDeletableContentList.get(i).onSaveInstanceStateBundle(outState);
        }
    }

    @Override
    public void onFreeableChanged(int numItems, long bytesFreeable) {
        if (numItems > 0 || bytesFreeable > 0 || allTypesEmpty()) {
            if (mLoadingController != null) {
                mLoadingController.onCategoryLoad();
            }
        }

        // bytesFreeable is the number of bytes freed by a single deletion type. If it is non-zero,
        // there is stuff to free and we can enable it. If it is zero, though, we still need to get
        // getTotalFreeableSpace to check all deletion types.
        if (mFree != null) {
            mFree.setEnabled(bytesFreeable != 0 || getTotalFreeableSpace(COUNT_CHECKED_ONLY) != 0);
        }
        updateFreeButtonText();

        // Transition to empty state if all types have reported there is nothing to delete. Skip
        // the transition if we are already in no threshold mode
        if (allTypesEmpty() && !isEmptyState()) {
            startEmptyState();
        }
    }

    private boolean allTypesEmpty() {
        return mAppBackend.isEmpty()
                && (mDownloadsDeletion == null || mDownloadsDeletion.isEmpty())
                && (mPhotoVideoDeletion == null || mPhotoVideoDeletion.isEmpty());
    }

    private void startEmptyState() {
        if (getActivity() instanceof DeletionHelperActivity) {
            DeletionHelperActivity activity = (DeletionHelperActivity) getActivity();
            activity.setIsEmptyState(true /* isEmptyState */);
        }
    }

    /** Clears out the selected apps and data from the device and closes the fragment. */
    protected void clearData() {
        // This should be fine as long as there is only one extra deletion feature.
        // In the future, this should be done in an async queue in order to not
        // interfere with the simultaneous PackageDeletionTask.
        if (mPhotoPreference != null && mPhotoPreference.isChecked()) {
            mPhotoVideoDeletion.clearFreeableData(getActivity());
        }
        if (mDownloadsPreference != null) {
            mDownloadsDeletion.clearFreeableData(getActivity());
        }
        if (mAppBackend != null) {
            mAppBackend.clearFreeableData(getActivity());
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.next_button) {
            ConfirmDeletionDialog dialog =
                    ConfirmDeletionDialog.newInstance(getTotalFreeableSpace(COUNT_CHECKED_ONLY));
            // The 0 is a placeholder for an optional result code.
            dialog.setTargetFragment(this, 0);
            dialog.show(getFragmentManager(), ConfirmDeletionDialog.TAG);
            MetricsLogger.action(getContext(), MetricsEvent.ACTION_DELETION_HELPER_CLEAR);
        } else {
            MetricsLogger.action(getContext(), MetricsEvent.ACTION_DELETION_HELPER_CANCEL);
            getActivity().finish();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[],
                                           int[] grantResults) {
        if (requestCode == 0) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mDownloadsDeletion.onResume();
                getLoaderManager().initLoader(DOWNLOADS_LOADER_ID, new Bundle(),
                        mDownloadsDeletion);
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater) {
        Activity activity = getActivity();
        String mHelpUri = getResources().getString(R.string.help_uri_deletion_helper);
        if (mHelpUri != null && activity != null) {
            HelpUtils.prepareHelpMenuItem(activity, menu, mHelpUri, getClass().getName());
        }
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        return view;
    }

    @VisibleForTesting
    void setDownloadsDeletionType(DownloadsDeletionType downloadsDeletion) {
        mDownloadsDeletion = downloadsDeletion;
    }

    private void initializeButtons() {
        ButtonBarProvider activity = (ButtonBarProvider) getActivity();
        activity.getButtonBar().setVisibility(View.VISIBLE);

        mCancel = activity.getSkipButton();
        mCancel.setText(R.string.cancel);
        mCancel.setOnClickListener(this);
        mCancel.setVisibility(View.VISIBLE);

        mFree = activity.getNextButton();
        mFree.setText(R.string.storage_menu_free);
        mFree.setOnClickListener(this);
        mFree.setEnabled(false);
    }

    private void updateFreeButtonText() {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        mFree.setText(
                String.format(
                        activity.getString(R.string.deletion_helper_free_button),
                        Formatter.formatFileSize(
                                activity, getTotalFreeableSpace(COUNT_CHECKED_ONLY))));
    }

    private long getTotalFreeableSpace(boolean countUnchecked) {
        long freeableSpace = 0;
        freeableSpace += mAppBackend.getTotalAppsFreeableSpace(countUnchecked);
        if (mPhotoPreference != null) {
            freeableSpace += mPhotoPreference.getFreeableBytes(countUnchecked);
        }
        if (mDownloadsPreference != null) {
            freeableSpace += mDownloadsDeletion.getFreeableBytes(countUnchecked);
        }
        return freeableSpace;
    }

}
