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
import android.os.Bundle;
import android.support.annotation.IntDef;

/**
 * Helper for the Deletion Helper which can query, clear out, and visualize deletable data.
 * This could represent a helper for deleting photos, downloads, movies, etc.
 */
public interface DeletionType {

    @IntDef({LoadingStatus.LOADING, LoadingStatus.COMPLETE, LoadingStatus.EMPTY})
    @interface LoadingStatus {
        /** Loading is still in progress. */
        int LOADING = 0;
        /** Loading was completed and deletable content was found. */
        int COMPLETE = 1;
        /** Loading was completed and no deletable content was found. */
        int EMPTY = 2;
    }

    /**
     * Registers a callback to call when the amount of freeable space is updated.
     * @param listener A callback.
     */
    void registerFreeableChangedListener(FreeableChangedListener listener);

    /**
     * Resumes an operation, intended to be called when the deletion fragment resumes.
     */
    void onResume();

    /**
     * Pauses the feature's operations, intended to be called when the deletion fragment is paused.
     */
    void onPause();

    void onSaveInstanceStateBundle(Bundle savedInstanceState);

    /**
     * Asynchronously free up the freeable information for the feature.
     */
    void clearFreeableData(Activity activity);

    /** @return The number of items found that are available for deletion. */
    int getContentCount();

    /** @return The loading status of this deletion type. Can be any of {@link LoadingStatus}. */
    @LoadingStatus
    int getLoadingStatus();

    /**
     * Convenience method for checking if the loading status is {@link LoadingStatus#COMPLETE}.
     *
     * @return Whether the loading status is currently {@link LoadingStatus#COMPLETE} as a boolean.
     */
    default boolean isComplete() {
        return getLoadingStatus() == LoadingStatus.COMPLETE;
    }

    /**
     * Convenience method for checking if the loading status is {@link LoadingStatus#EMPTY}.
     *
     * @return Whether the loading status is currently {@link LoadingStatus#EMPTY} as a boolean.
     */
    default boolean isEmpty() {
        return getLoadingStatus() == LoadingStatus.EMPTY;
    }

    /**
     * @param loadingStatus The state to set the deletion type to. Can be any of {@link
     *     LoadingStatus}.
     */
    void setLoadingStatus(@LoadingStatus int loadingStatus);

    /**
     * Callback interface to listen for when a deletion feature's amount of freeable space updates.
     */
    interface FreeableChangedListener {
        void onFreeableChanged(int numItems, long bytesFreeable);
    }

    /**
     * Updates the loading status of the deletion type based on whether content is available to
     * delete or not.
     */
    default void updateLoadingStatus() {
        if (getContentCount() == 0) {
            setLoadingStatus(LoadingStatus.EMPTY);
        } else {
            setLoadingStatus(LoadingStatus.COMPLETE);
        }
    }
}
