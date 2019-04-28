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

import android.os.Bundle;
import android.os.Parcel;
import android.test.AndroidTestCase;
import android.test.MoreAsserts;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.managedprovisioning.model.PackageDownloadInfo;

import junit.framework.Assert;

import java.lang.Exception;

/** Tests for {@link PackageDownloadInfo} */
public class PackageDownloadInfoTest extends AndroidTestCase {
    private static final String TEST_DOWNLOAD_LOCATION =
            "http://example/dpc.apk";
    private static final String TEST_COOKIE_HEADER =
            "Set-Cookie: sessionToken=foobar; Expires=Thu, 18 Feb 2016 23:59:59 GMT";
    private static final byte[] TEST_PACKAGE_CHECKSUM = new byte[] { '1', '2', '3', '4', '5' };
    private static final byte[] TEST_SIGNATURE_CHECKSUM = new byte[] { '5', '4', '3', '2', '1' };
    private static final int TEST_MIN_SUPPORT_VERSION = 7689;
    private static final boolean TEST_CHECKSUM_SUPPORT_SHA1 = true;

    @SmallTest
    public void testBuilderWriteAndReadBack() {
        // WHEN a PackageDownloadInfo object is constructed with some test parameters.
        PackageDownloadInfo downloadInfo = PackageDownloadInfo.Builder.builder()
                .setLocation(TEST_DOWNLOAD_LOCATION)
                .setCookieHeader(TEST_COOKIE_HEADER)
                .setPackageChecksum(TEST_PACKAGE_CHECKSUM)
                .setSignatureChecksum(TEST_SIGNATURE_CHECKSUM)
                .setMinVersion(TEST_MIN_SUPPORT_VERSION)
                .build();
        // THEN the same set of values are obtained in the object.
        assertEquals(TEST_DOWNLOAD_LOCATION, downloadInfo.location);
        assertEquals(TEST_COOKIE_HEADER, downloadInfo.cookieHeader);
        assertEquals(TEST_PACKAGE_CHECKSUM, downloadInfo.packageChecksum);
        assertEquals(TEST_SIGNATURE_CHECKSUM, downloadInfo.signatureChecksum);
        assertEquals(TEST_MIN_SUPPORT_VERSION, downloadInfo.minVersion);
    }

    @SmallTest
    public void testFailToConstructPackageInfoWithDownloadLocationWithoutChecksum() {
        // WHEN the PackageDownloadInfo is constructed with a download location but without any
        // checksum.
        try {
            PackageDownloadInfo downloadInfo = PackageDownloadInfo.Builder.builder()
                    .setLocation(TEST_DOWNLOAD_LOCATION)
                    .build();
            fail("Checksum is mandatory.");
        } catch (IllegalArgumentException e) {
            // THEN PackageDownloadInfo is failed to construct due to the missing checksum.
        }
    }

    @SmallTest
    public void testFailToConstructPackageInfoWithoutDownloadLocation() {
        // WHEN the PackageDownloadInfo is constructed without any download location.
        try {
            PackageDownloadInfo downloadInfo = PackageDownloadInfo.Builder.builder()
                    .setPackageChecksum(TEST_PACKAGE_CHECKSUM)
                    .build();
            fail("Download location is mandatory.");
        } catch (IllegalArgumentException e) {
            // THEN PackageDownloadInfo fails to construct due to the missing download location.
        }
    }

    @SmallTest
    public void testConstructPackageInfoWithDownloadLocationAndPackageChecksum() {
        // WHEN the PackageDownloadInfo is constructed with a download location and a package
        // checksum.
        PackageDownloadInfo downloadInfo = PackageDownloadInfo.Builder.builder()
                .setLocation(TEST_DOWNLOAD_LOCATION)
                .setPackageChecksum(TEST_PACKAGE_CHECKSUM)
                .build();
        // THEN the PackageDownloadInfo is constructed with the following values.
        assertEquals(TEST_DOWNLOAD_LOCATION, downloadInfo.location);
        assertEquals(TEST_PACKAGE_CHECKSUM, downloadInfo.packageChecksum);
    }

    @SmallTest
    public void testConstructPackageInfoWithDownloadLocationAndSignatureChecksum() {
        // WHEN the PackageDownloadInfo is constructed with a download location and a signature
        // checksum.
        PackageDownloadInfo downloadInfo = PackageDownloadInfo.Builder.builder()
                .setLocation(TEST_DOWNLOAD_LOCATION)
                .setSignatureChecksum(TEST_SIGNATURE_CHECKSUM)
                .build();
        // THEN the PackageDownloadInfo is constructed with the following values.
        assertEquals(TEST_DOWNLOAD_LOCATION, downloadInfo.location);
        assertEquals(TEST_SIGNATURE_CHECKSUM, downloadInfo.signatureChecksum);
    }

    @SmallTest
    public void testEquals() {
        // GIVEN 2 PackageDownloadInfo objects are constructed with the same set of parameters.
        PackageDownloadInfo downloadInfo1 = PackageDownloadInfo.Builder.builder()
                .setLocation(TEST_DOWNLOAD_LOCATION)
                .setCookieHeader(TEST_COOKIE_HEADER)
                .setPackageChecksum(TEST_PACKAGE_CHECKSUM)
                .setSignatureChecksum(TEST_SIGNATURE_CHECKSUM)
                .setMinVersion(TEST_MIN_SUPPORT_VERSION)
                .build();
        PackageDownloadInfo downloadInfo2 = PackageDownloadInfo.Builder.builder()
                .setLocation(TEST_DOWNLOAD_LOCATION)
                .setCookieHeader(TEST_COOKIE_HEADER)
                .setPackageChecksum(TEST_PACKAGE_CHECKSUM)
                .setSignatureChecksum(TEST_SIGNATURE_CHECKSUM)
                .setMinVersion(TEST_MIN_SUPPORT_VERSION)
                .build();
        // WHEN comparing these two objects.
        // THEN they are equal.
        assertEquals(downloadInfo1, downloadInfo2);
    }

    @SmallTest
    public void testNotEquals() {
        // GIVEN 2 PackageDownloadInfo objects are constructed with the different set of parameters.
        PackageDownloadInfo downloadInfo1 = PackageDownloadInfo.Builder.builder()
                .setLocation(TEST_DOWNLOAD_LOCATION)
                .setCookieHeader(TEST_COOKIE_HEADER)
                .setPackageChecksum(TEST_PACKAGE_CHECKSUM)
                .setSignatureChecksum(TEST_SIGNATURE_CHECKSUM)
                .setMinVersion(TEST_MIN_SUPPORT_VERSION)
                .build();
        PackageDownloadInfo downloadInfo2 = PackageDownloadInfo.Builder.builder()
                .setLocation("http://a/b/")
                .setCookieHeader(TEST_COOKIE_HEADER)
                .setPackageChecksum(TEST_PACKAGE_CHECKSUM)
                .setSignatureChecksum(TEST_SIGNATURE_CHECKSUM)
                .setMinVersion(TEST_MIN_SUPPORT_VERSION)
                .build();
        // WHEN comparing these two objects.
        // THEN they are not equal.
        MoreAsserts.assertNotEqual(downloadInfo1, downloadInfo2);
    }

    @SmallTest
    public void testParceable() {
        // GIVEN a PackageDownloadInfo object.
        PackageDownloadInfo expectedDownloadInfo = PackageDownloadInfo.Builder.builder()
                .setLocation(TEST_DOWNLOAD_LOCATION)
                .setCookieHeader(TEST_COOKIE_HEADER)
                .setPackageChecksum(TEST_PACKAGE_CHECKSUM)
                .setSignatureChecksum(TEST_SIGNATURE_CHECKSUM)
                .setMinVersion(TEST_MIN_SUPPORT_VERSION)
                .build();

        // WHEN the PackageDownloadInfo is written to parcel and then read back.
        Parcel parcel = Parcel.obtain();
        expectedDownloadInfo.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        PackageDownloadInfo actualDownloadInfo =
                PackageDownloadInfo.CREATOR.createFromParcel(parcel);

        // THEN the same PackageDownloadInfo is obtained.
        assertEquals(expectedDownloadInfo, actualDownloadInfo);
    }
}
