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

import android.content.Context;
import android.support.v7.preference.Preference;
import android.text.format.DateUtils;

import com.android.storagemanager.utils.IconProvider;

import java.io.File;

/**
 * DownloadsFilePreference is a preference representing a file in the Downloads folder with a
 * checkbox that represents if the file should be deleted.
 */
public class DownloadsFilePreference extends NestedDeletionPreference {
    private File mFile;

    public DownloadsFilePreference(Context context, File file, IconProvider iconProvider) {
        super(context);
        mFile = file;
        setKey(mFile.getPath());
        setTitle(file.getName());
        setItemSize(file.length());
        setSummary(
                DateUtils.formatDateTime(
                        context, mFile.lastModified(), DateUtils.FORMAT_SHOW_DATE));
        setIcon(iconProvider.loadMimeIcon(IconProvider.getMimeType(mFile)));

        // We turn off persistence because we need the file preferences to reset their check when
        // you return to the view.
        setPersistent(false);
    }

    public File getFile() {
        return mFile;
    }

    @Override
    public int compareTo(Preference other) {
        if (other == null) {
            return 1;
        }

        if (other instanceof DownloadsFilePreference) {
            File otherFile = ((DownloadsFilePreference) other).getFile();
            File file = getFile();
            // Note: The order is reversed in this comparison because we want the value to be less
            // than 0 if we're bigger. Long.compare returns less than 0 if first < second.
            int comparison = Long.compare(otherFile.length(), file.length());
            if (comparison == 0) {
                comparison = file.compareTo(otherFile);
            }
            return comparison;
        } else {
            // If a non-DownloadsFilePreference appears, consider ourselves to be greater.
            // This means if a non-DownloadsFilePreference sneaks into a DownloadsPreferenceGroup
            // then the DownloadsFilePreference will appear higher.
            return 1;
        }
    }
}
