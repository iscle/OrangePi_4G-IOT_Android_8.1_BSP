/*
 * Copyright 2017, The Android Open Source Project
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
package com.android.managedprovisioning.parser;

import static com.android.managedprovisioning.common.StoreUtils.DIR_PROVISIONING_PARAMS_FILE_CACHE;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.Nullable;

import com.android.managedprovisioning.common.StoreUtils;

import java.io.File;

/**
 * parser for {@link EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_ICON_URI} into a local file
 */
public class DeviceAdminIconParser {
    private static final String FILE_PREFIX = "device_admin_icon_";
    private final Context mContext;
    private final File mFileIcon;

    public DeviceAdminIconParser(Context context, long provisioningId) {
        mContext = context;
        mFileIcon =  new File(new File(mContext.getFilesDir(), DIR_PROVISIONING_PARAMS_FILE_CACHE),
                FILE_PREFIX + provisioningId);
    }

    /**
     * @return absolute path of the local file cache of the icon. Otherwise, return null.
     */
    @Nullable
    public String parse(Uri uri) {
        if (uri == null) {
            return null;
        }

        mFileIcon.getParentFile().mkdirs();
        boolean success = StoreUtils.copyUriIntoFile(mContext.getContentResolver(), uri, mFileIcon);
        return success ? mFileIcon.getAbsolutePath() : null;
    }
}
