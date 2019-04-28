/*
 * Copyright (C) 2015 The Android Open Source Project
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
package android.car.content.pm;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.content.pm.Signature;
import android.os.Parcel;
import android.os.Parcelable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;

/**
 * Parcelable to hold information on app blocking whitelist or blacklist for a package.
 * @hide
 */
@SystemApi
public class AppBlockingPackageInfo implements Parcelable {

    /** Package name for the package to block or allow. */
    public final String packageName;

    /** Represents system app which does not need {@link #signature}. */
    public static final int FLAG_SYSTEM_APP = 0x1;
    /** Blacklist or whitelist every Activities in the package. When this is set,
     *  {@link #activities} may be null. */
    public static final int FLAG_WHOLE_ACTIVITY = 0x2;
    /** @hide */
    @IntDef(flag = true,
            value = {FLAG_SYSTEM_APP, FLAG_WHOLE_ACTIVITY})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ConstrcutorFlags {}

    /**
     * flags to give additional information on the package.
     * @see #FLAG_SYSTEM_APP
     * @see #FLAG_WHOLE_ACTIVITY
     */
    public final int flags;

    /**
     * Package version should be bigger than this to block or allow.
     * (package version > minRevisionCode)
     * 0 means do not care min version.
     */
    public final int minRevisionCode;

    /**
     * Package version should be smaller than this to block or allow.
     * (package version < minRevisionCode)
     * 0 means do not care max version.
     */
    public final int maxRevisionCode;

    /**
     * Signature of package. This can be null if target package is from system so that package
     * name is enough to uniquely identify it (= {@link #flags} having {@link #FLAG_SYSTEM_APP}.
     * Matching any member of array is considered as matching package.
     */
    public final Signature[] signatures;

    /** List of activities (full class name). This can be null if Activity is not blocked or
     *  allowed. Additionally, {@link #FLAG_WHOLE_ACTIVITY} set in {@link #flags} shall have
     *  null for this. */
    public final String[] activities;


    public AppBlockingPackageInfo(String packageName, int minRevisionCode, int maxRevisionCode,
            @ConstrcutorFlags int flags, @Nullable Signature[] signatures,
            @Nullable String[] activities) {
        if (packageName == null) {
            throw new IllegalArgumentException("packageName cannot be null");
        }
        this.packageName = packageName;
        this.flags = flags;
        this.minRevisionCode = minRevisionCode;
        this.maxRevisionCode = maxRevisionCode;
        this.signatures = signatures;
        this.activities = activities;
        verify();
    }

    public AppBlockingPackageInfo(Parcel in) {
        packageName = in.readString();
        flags = in.readInt();
        minRevisionCode= in.readInt();
        maxRevisionCode = in.readInt();
        signatures = in.createTypedArray(Signature.CREATOR);
        activities = in.createStringArray();
        verify();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(packageName);
        dest.writeInt(this.flags);
        dest.writeInt(minRevisionCode);
        dest.writeInt(maxRevisionCode);
        dest.writeTypedArray(signatures, 0);
        dest.writeStringArray(activities);
    }

    public static final Parcelable.Creator<AppBlockingPackageInfo> CREATOR
            = new Parcelable.Creator<AppBlockingPackageInfo>() {
        public AppBlockingPackageInfo createFromParcel(Parcel in) {
            return new AppBlockingPackageInfo(in);
        }

        public AppBlockingPackageInfo[] newArray(int size) {
            return new AppBlockingPackageInfo[size];
        }
    };

    /** @hide */
    public void verify() throws IllegalArgumentException {
        if (signatures == null && (flags & FLAG_SYSTEM_APP) == 0) {
            throw new IllegalArgumentException(
                    "Only system package with FLAG_SYSTEM_APP can have null signatures");
        }
    }

    /** @hide */
    public boolean isActivityCovered(String className) {
        if ((flags & FLAG_WHOLE_ACTIVITY) != 0) {
            return true;
        }
        if (activities == null) {
            return false;
        }
        for (String activityName : activities) {
            if (activityName.equals(className)) {
                return true;
            }
        }
        return false;
    }



    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(activities);
        result = prime * result + flags;
        result = prime * result + maxRevisionCode;
        result = prime * result + minRevisionCode;
        result = prime * result + ((packageName == null) ? 0 : packageName.hashCode());
        result = prime * result + Arrays.hashCode(signatures);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        AppBlockingPackageInfo other = (AppBlockingPackageInfo) obj;
        if (!Arrays.equals(activities, other.activities)) {
            return false;
        }
        if (flags != other.flags) {
            return false;
        }
        if (maxRevisionCode != other.maxRevisionCode) {
            return false;
        }
        if (minRevisionCode != other.minRevisionCode) {
            return false;
        }
        if (packageName == null) {
            if (other.packageName != null)
                return false;
        } else if (!packageName.equals(other.packageName))
            return false;
        if (!Arrays.equals(signatures, other.signatures)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "AppBlockingPackageInfo [packageName=" + packageName + ", flags=" + flags
                + ", minRevisionCode=" + minRevisionCode + ", maxRevisionCode=" + maxRevisionCode
                + ", signatures=" + Arrays.toString(signatures) + ", activities="
                + Arrays.toString(activities) + "]";
    }
}
