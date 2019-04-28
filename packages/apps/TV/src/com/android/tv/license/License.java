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
 * limitations under the License
 */

package com.android.tv.license;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Container class to store the name of a library and the filename of its associated license file.
 */
public final class License implements Comparable<License>, Parcelable {
    // Name of the third-party library.
    private final String mLibraryName;
    // Byte offset in the file to the start of the license text.
    private final long mLicenseOffset;
    // Byte length of the license text.
    private final int mLicenseLength;
    // Path to the archive that has bundled licenses.
    // Empty string if the license is bundled in the apk itself.
    private final String mPath;

    /**
     * Create an object representing a stored license. The text for all licenses is stored in a
     * single file, so the offset and length describe this license's position within the file.
     *
     * @param path a path to an .apk-compatible archive that contains the license. An empty string
     *     in case the license is contained within the app itself.
     */
    static License create(String libraryName, long licenseOffset, int licenseLength, String path) {
        return new License(libraryName, licenseOffset, licenseLength, path);
    }

    public static final Parcelable.Creator<License> CREATOR =
            new Parcelable.Creator<License>() {
                @Override
                public License createFromParcel(Parcel in) {
                    return new License(in);
                }

                @Override
                public License[] newArray(int size) {
                    return new License[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mLibraryName);
        dest.writeLong(mLicenseOffset);
        dest.writeInt(mLicenseLength);
        dest.writeString(mPath);
    }

    @Override
    public int compareTo(License o) {
        return mLibraryName.compareToIgnoreCase(o.getLibraryName());
    }

    @Override
    public String toString() {
        return getLibraryName();
    }

    private License(String libraryName, long licenseOffset, int licenseLength, String path) {
        this.mLibraryName = libraryName;
        this.mLicenseOffset = licenseOffset;
        this.mLicenseLength = licenseLength;
        this.mPath = path;
    }

    private License(Parcel in) {
        mLibraryName = in.readString();
        mLicenseOffset = in.readLong();
        mLicenseLength = in.readInt();
        mPath = in.readString();
    }

    String getLibraryName() {
        return mLibraryName;
    }

    long getLicenseOffset() {
        return mLicenseOffset;
    }

    int getLicenseLength() {
        return mLicenseLength;
    }

    public String getPath() {
        return mPath;
    }
}
