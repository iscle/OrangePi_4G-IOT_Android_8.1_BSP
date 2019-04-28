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
package com.android.documentsui.testing;

import android.annotation.BoolRes;

import com.android.documentsui.base.Features;

public class TestFeatures implements Features {

    public boolean archiveCreation = true;
    public boolean commandProcessor = true;
    public boolean contentPaging = true;
    public boolean contentRefresh = true;
    public boolean debugSupport = true;
    public boolean foldersInSearchResults = true;
    public boolean gestureScale = true;
    public boolean inspector = true;
    public boolean jobProgressDialog = false;
    public boolean launchToDocument = true;
    public boolean notificationChannel = true;
    public boolean overwriteConfirmation = true;
    public boolean remoteActions = true;
    public boolean systemKeyboardNavigation = true;
    public boolean virtualFilesSharing = true;

    @Override
    public boolean isArchiveCreationEnabled() {
        return archiveCreation;
    }

    @Override
    public boolean isCommandInterceptorEnabled() {
        return commandProcessor;
    }

    @Override
    public boolean isContentPagingEnabled() {
        return contentPaging;
    }

    @Override
    public boolean isContentRefreshEnabled() {
        return contentRefresh;
    }

    @Override
    public boolean isDebugSupportEnabled() {
        return debugSupport;
    }

    @Override
    public boolean isFoldersInSearchResultsEnabled() {
        return foldersInSearchResults;
    }

    @Override
    public boolean isJobProgressDialogEnabled() {
        return jobProgressDialog;
    }

    @Override
    public boolean isGestureScaleEnabled() {
        return gestureScale;
    }

    @Override
    public boolean isInspectorEnabled() {
        return inspector;
    }

    @Override
    public boolean isLaunchToDocumentEnabled() {
        return launchToDocument;
    }

    @Override
    public boolean isNotificationChannelEnabled() {
        return notificationChannel;
    }

    @Override
    public boolean isOverwriteConfirmationEnabled() {
        return overwriteConfirmation;
    }

    @Override
    public boolean isRemoteActionsEnabled() {
        return remoteActions;
    }

    @Override
    public boolean isSystemKeyboardNavigationEnabled() {
        return systemKeyboardNavigation;
    }

    @Override
    public boolean isVirtualFilesSharingEnabled() {
        return virtualFilesSharing;
    }

    @Override
    public void forceFeature(@BoolRes int feature, boolean enabled) {
        throw new UnsupportedOperationException("Implement as needed.");
    }
}
