/*
 * Copyright 2016, The Android Open Source Project
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

package com.android.managedprovisioning.model;

import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_COOKIE_HEADER;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_MINIMUM_VERSION_CODE;
import static com.android.internal.util.Preconditions.checkNotNull;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.PersistableBundle;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import com.android.internal.annotations.Immutable;
import com.android.managedprovisioning.common.PersistableBundlable;
import com.android.managedprovisioning.common.StoreUtils;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

/**
 * Stores the device admin package download information.
 */
@Immutable
public final class PackageDownloadInfo extends PersistableBundlable {
    public static final byte[] DEFAULT_PACKAGE_CHECKSUM = new byte[0];
    public static final byte[] DEFAULT_SIGNATURE_CHECKSUM = new byte[0];
    public static final boolean DEFAULT_PACKAGE_CHECKSUM_SUPPORTS_SHA1 = false;
    // Always download packages if no minimum version given.
    public static final int DEFAULT_MINIMUM_VERSION = Integer.MAX_VALUE;

    private static final String TAG_PROVISIONING_DEVICE_ADMIN_SUPPORT_SHA1_PACKAGE_CHECKSUM
            = "supports-sha1-checksum";

    public static final Parcelable.Creator<PackageDownloadInfo> CREATOR
            = new Parcelable.Creator<PackageDownloadInfo>() {
        @Override
        public PackageDownloadInfo createFromParcel(Parcel in) {
            return new PackageDownloadInfo(in);
        }

        @Override
        public PackageDownloadInfo[] newArray(int size) {
            return new PackageDownloadInfo[size];
        }
    };

    /**
     * Url where the package (.apk) can be downloaded from. {@code null} if there is no download
     * location specified.
     */
    public final String location;
    /** Cookie header for http request. */
    @Nullable
    public final String cookieHeader;
    /**
     * One of the following two checksums should be non empty. SHA-256 or SHA-1 hash of the
     * .apk file, or empty array if not used.
     */
    public final byte[] packageChecksum;
    /** SHA-256 hash of the signature in the .apk file, or empty array if not used. */
    public final byte[] signatureChecksum;
    /** Minimum supported version code of the downloaded package. */
    public final int minVersion;
    /**
     * If this is false, packageChecksum can only be SHA-256 hash, otherwise SHA-1 is also
     * supported.
     */
    public final boolean packageChecksumSupportsSha1;

    private PackageDownloadInfo(Builder builder) {
        location = builder.mLocation;
        cookieHeader = builder.mCookieHeader;
        packageChecksum = checkNotNull(builder.mPackageChecksum, "package checksum can't be null");
        signatureChecksum = checkNotNull(builder.mSignatureChecksum,
                "signature checksum can't be null");
        minVersion = builder.mMinVersion;
        packageChecksumSupportsSha1 = builder.mPackageChecksumSupportsSha1;

        validateFields();
    }

    private PackageDownloadInfo(Parcel in) {
        this(createBuilderFromPersistableBundle(
                PersistableBundlable.getPersistableBundleFromParcel(in)));
    }

    private void validateFields() {
        if (TextUtils.isEmpty(location)) {
            throw new IllegalArgumentException("Download location must not be empty.");
        }
        if (packageChecksum.length == 0 && signatureChecksum.length == 0) {
            throw new IllegalArgumentException("Package checksum or signature checksum must be "
                    + "provided.");
        }
    }

    /* package */ static PackageDownloadInfo fromPersistableBundle(PersistableBundle bundle) {
        return createBuilderFromPersistableBundle(bundle).build();
    }

    private static Builder createBuilderFromPersistableBundle(PersistableBundle bundle) {
        Builder builder = new Builder();
        builder.setMinVersion(bundle.getInt(EXTRA_PROVISIONING_DEVICE_ADMIN_MINIMUM_VERSION_CODE));
        builder.setLocation(bundle.getString(
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION));
        builder.setCookieHeader(bundle.getString(
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_COOKIE_HEADER));
        builder.setPackageChecksum(StoreUtils.stringToByteArray(bundle.getString(
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM)));
        builder.setSignatureChecksum(StoreUtils.stringToByteArray(bundle.getString(
                EXTRA_PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM)));
        builder.setPackageChecksumSupportsSha1(bundle.getBoolean(
                TAG_PROVISIONING_DEVICE_ADMIN_SUPPORT_SHA1_PACKAGE_CHECKSUM));
        return builder;
    }

    @Override
    public PersistableBundle toPersistableBundle() {
        final PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(EXTRA_PROVISIONING_DEVICE_ADMIN_MINIMUM_VERSION_CODE,
                minVersion);
        bundle.putString(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION, location);
        bundle.putString(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_COOKIE_HEADER,
                cookieHeader);
        bundle.putString(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM,
                StoreUtils.byteArrayToString(packageChecksum));
        bundle.putString(EXTRA_PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM,
                StoreUtils.byteArrayToString(signatureChecksum));
        bundle.putBoolean(TAG_PROVISIONING_DEVICE_ADMIN_SUPPORT_SHA1_PACKAGE_CHECKSUM,
                packageChecksumSupportsSha1);
        return bundle;
    }

    public final static class Builder {
        private String mLocation;
        private String mCookieHeader;
        private byte[] mPackageChecksum = DEFAULT_PACKAGE_CHECKSUM;
        private byte[] mSignatureChecksum = DEFAULT_SIGNATURE_CHECKSUM;
        private int mMinVersion = DEFAULT_MINIMUM_VERSION;
        private boolean mPackageChecksumSupportsSha1 = DEFAULT_PACKAGE_CHECKSUM_SUPPORTS_SHA1;

        public Builder setLocation(String location) {
            mLocation = location;
            return this;
        }

        public Builder setCookieHeader(String cookieHeader) {
            mCookieHeader = cookieHeader;
            return this;
        }

        public Builder setPackageChecksum(byte[] packageChecksum) {
            mPackageChecksum = packageChecksum;
            return this;
        }

        public Builder setSignatureChecksum(byte[] signatureChecksum) {
            mSignatureChecksum = signatureChecksum;
            return this;
        }

        public Builder setMinVersion(int minVersion) {
            mMinVersion = minVersion;
            return this;
        }

        // TODO: remove once SHA-1 is fully deprecated.
        public Builder setPackageChecksumSupportsSha1(boolean packageChecksumSupportsSha1) {
            mPackageChecksumSupportsSha1 = packageChecksumSupportsSha1;
            return this;
        }

        public PackageDownloadInfo build() {
            return new PackageDownloadInfo(this);
        }

        public static Builder builder() {
            return new Builder();
        }
    }
}
