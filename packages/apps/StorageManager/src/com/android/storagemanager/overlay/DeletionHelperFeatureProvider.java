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

package com.android.storagemanager.overlay;

import android.content.Context;
import com.android.storagemanager.deletionhelper.DeletionType;

/**
 * Feature provider for the manual deletion helper.
 */
public interface DeletionHelperFeatureProvider {
    /** Creates a {@link DeletionType} for clearing out stored photos and videos on the device. */
    DeletionType createPhotoVideoDeletionType(Context context, int thresholdType);

    /**
     * Returns how many days back the deletionType with this threshold will look for photos.
     *
     * @param thresholdType the threshold type.
     * @return an int representing the number of days.
     */
    int getDaysToKeep(int thresholdType);
}
