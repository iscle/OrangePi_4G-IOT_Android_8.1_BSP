/*
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.car.stream.media;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.MediaDescription;
import android.service.media.MediaBrowserService;

import java.util.List;
import java.util.Objects;

/**
 * Media related utility functions
 */
public final class MediaUtils {
    /**
     * @return True if the two media descriptions are the same.
     */
    public static boolean isSameMediaDescription(MediaDescription description1,
            MediaDescription description2) {
        if ((description1 == null) && (description2 == null)) {
            return true;
        }

        if (description1 != null && description2 != null) {
            return Objects.equals(description1.getTitle(), description2.getTitle())
                    && Objects.equals(description1.getSubtitle(), description2.getSubtitle());
        }
        return false;
    }

    /**
     * @return The component name of the {@link MediaBrowserService} for the given package name.
     */
    public static ComponentName getMediaBrowserService(String packageName,
            Context context) {
        Intent intent = new Intent(MediaBrowserService.SERVICE_INTERFACE);
        List<ResolveInfo> mediaApps = context.getPackageManager()
                .queryIntentServices(intent, PackageManager.GET_RESOLVED_FILTER);

        for (int i = 0; i < mediaApps.size(); i++) {
            ResolveInfo info = mediaApps.get(i);
            if (packageName.equals(info.serviceInfo.packageName)) {
                return new ComponentName(packageName, info.serviceInfo.name /* className */);
            }
        }
        return null;
    }
}
