/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.documentsui.base;

import android.annotation.BoolRes;
import android.content.Context;
import android.content.res.Resources;
import android.os.UserManager;
import android.util.SparseBooleanArray;

import com.android.documentsui.R;

/**
 * Provides access to feature flags configured in config.xml.
 */
public interface Features {

    // technically we want to check >= O, but we'd need to patch back the O version code :|
    public static final boolean OMC_RUNTIME =
            android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.N_MR1;

    boolean isArchiveCreationEnabled();
    boolean isCommandInterceptorEnabled();
    boolean isContentPagingEnabled();
    boolean isContentRefreshEnabled();
    boolean isDebugSupportEnabled();
    boolean isFoldersInSearchResultsEnabled();
    boolean isGestureScaleEnabled();
    boolean isInspectorEnabled();
    boolean isJobProgressDialogEnabled();
    boolean isLaunchToDocumentEnabled();
    boolean isNotificationChannelEnabled();
    boolean isOverwriteConfirmationEnabled();
    boolean isRemoteActionsEnabled();
    boolean isSystemKeyboardNavigationEnabled();
    boolean isVirtualFilesSharingEnabled();


    /**
     * Call this to force-enable any particular feature known by this instance.
     * Note that all feature may not support being enabled at runtime as
     * they may depend on runtime initialization guarded by feature check.
     *
     * <p>Feature changes will be persisted across activities, but not app restarts.
     *
     * @param feature int reference to a boolean feature resource.
     */
    void forceFeature(@BoolRes int feature, boolean enabled);

    public static Features create(Context context) {
        return new RuntimeFeatures(context.getResources(), UserManager.get(context));
    }

    final class RuntimeFeatures implements Features {

        private final SparseBooleanArray mDebugEnabled = new SparseBooleanArray();

        private final Resources mRes;
        private final UserManager mUserMgr;

        public RuntimeFeatures(Resources resources, UserManager userMgr) {
            mRes = resources;
            mUserMgr = userMgr;
        }

        @Override
        public void forceFeature(@BoolRes int feature, boolean enabled) {
            mDebugEnabled.put(feature, enabled);
        }

        private boolean isEnabled(@BoolRes int feature) {
            return mDebugEnabled.get(feature, mRes.getBoolean(feature));
        }

        @Override
        public boolean isArchiveCreationEnabled() {
            return isEnabled(R.bool.feature_archive_creation);
        }

        @Override
        public boolean isCommandInterceptorEnabled() {
            assert(isDebugPolicyEnabled());
            return isEnabled(R.bool.feature_command_interceptor);
        }

        @Override
        public boolean isContentPagingEnabled() {
            return isEnabled(R.bool.feature_content_paging);
        }

        @Override
        public boolean isContentRefreshEnabled() {
            return isEnabled(R.bool.feature_content_refresh);
        }

        private boolean isDebugPolicyEnabled() {
            return !mUserMgr.hasUserRestriction(UserManager.DISALLOW_DEBUGGING_FEATURES);
        }

        @Override
        public boolean isDebugSupportEnabled() {
            return isDebugPolicyEnabled() && isFunPolicyEnabled();
        }

        @Override
        public boolean isFoldersInSearchResultsEnabled() {
            return isEnabled(R.bool.feature_folders_in_search_results);
        }

        private boolean isFunPolicyEnabled() {
            return !mUserMgr.hasUserRestriction(UserManager.DISALLOW_FUN);
        }

        @Override
        public boolean isGestureScaleEnabled() {
            return isEnabled(R.bool.feature_gesture_scale);
        }

        public boolean isInspectorEnabled() {
            return isEnabled(R.bool.feature_inspector);
        }

        @Override
        public boolean isJobProgressDialogEnabled() {
            return isEnabled(R.bool.feature_job_progress_dialog);
        }

        @Override
        public boolean isLaunchToDocumentEnabled() {
            return isEnabled(R.bool.feature_launch_to_document);
        }

        @Override
        public boolean isNotificationChannelEnabled() {
            return isEnabled(R.bool.feature_notification_channel);
        }

        @Override
        public boolean isOverwriteConfirmationEnabled() {
            return isEnabled(R.bool.feature_overwrite_confirmation);
        }

        @Override
        public boolean isRemoteActionsEnabled() {
            return isEnabled(R.bool.feature_remote_actions);
        }

        @Override
        public boolean isSystemKeyboardNavigationEnabled() {
            return isEnabled(R.bool.feature_system_keyboard_navigation);
        }

        @Override
        public boolean isVirtualFilesSharingEnabled() {
            return isEnabled(R.bool.feature_virtual_files_sharing);
        }
    }
}
