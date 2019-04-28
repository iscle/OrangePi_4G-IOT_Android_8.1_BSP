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

package com.android.managedprovisioning.task;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE;
import static com.android.managedprovisioning.task.VerifyPackageTask.ERROR_DEVICE_ADMIN_MISSING;
import static com.android.managedprovisioning.task.VerifyPackageTask.ERROR_HASH_MISMATCH;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.PackageDownloadInfo;
import com.android.managedprovisioning.model.ProvisioningParams;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link VerifyPackageTask}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class VerifyPackageTaskTest {

    private static final String TEST_PACKAGE_NAME = "sample.package.name";
    private static final String TEST_ADMIN_NAME = TEST_PACKAGE_NAME + ".DeviceAdmin";
    private static final String TEST_PACKAGE_LOCATION = "http://www.some.uri.com";
    private static final String TEST_LOCAL_FILENAME = "/local/filename";
    private static final int TEST_USER_ID = 123;
    private static final byte[] TEST_BAD_HASH = new byte[] { 'b', 'a', 'd' };
    private static final byte[] TEST_PACKAGE_CHECKSUM_HASH = new byte[] { '1', '2', '3', '4', '5' };
    private static final byte[] TEST_SIGNATURE_HASH = new byte[] {'a', 'b', 'c', 'd'};
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[] {};
    private static final Signature[] TEST_SIGNATURES = new Signature[] { new Signature("1986") };

    @Mock private Context mContext;
    @Mock private DownloadPackageTask mDownloadPackageTask;
    @Mock private AbstractProvisioningTask.Callback mCallback;
    @Mock private PackageManager mPackageManager;
    @Mock private Utils mUtils;
    @Mock private PackageInfo mPackageInfo;

    private AbstractProvisioningTask mTask;

    @Before
    public void setUp() throws Exception {
        // This is necessary for mockito to work
        MockitoAnnotations.initMocks(this);

        when(mContext.getPackageManager()).thenReturn(mPackageManager);

        mPackageInfo.packageName = TEST_PACKAGE_NAME;
        mPackageInfo.signatures = TEST_SIGNATURES;

        when(mPackageManager.getPackageArchiveInfo(TEST_LOCAL_FILENAME,
                PackageManager.GET_SIGNATURES | PackageManager.GET_RECEIVERS))
                .thenReturn(mPackageInfo);

        when(mDownloadPackageTask.getDownloadedPackageLocation()).thenReturn(TEST_LOCAL_FILENAME);

        when(mUtils.findDeviceAdminInPackageInfo(TEST_PACKAGE_NAME, null, mPackageInfo))
                .thenReturn(new ComponentName(TEST_PACKAGE_NAME, TEST_ADMIN_NAME));
    }

    @Test
    public void testDownloadLocationNull() {
        // GIVEN that the download package location is null
        when(mDownloadPackageTask.getDownloadedPackageLocation()).thenReturn(null);

        // WHEN running the VerifyPackageTask
        runWithDownloadInfo(TEST_PACKAGE_CHECKSUM_HASH, EMPTY_BYTE_ARRAY, false);

        // THEN success should be called
        verify(mCallback).onSuccess(mTask);
        verifyNoMoreInteractions(mCallback);
    }

    @Test
    public void testMissingDeviceAdminComponent() {
        // GIVEN that the device admin component cannot be found
        when(mUtils.findDeviceAdminInPackageInfo(TEST_PACKAGE_NAME, null, mPackageInfo))
                .thenReturn(null);

        // WHEN running the VerifyPackageTask
        runWithDownloadInfo(TEST_PACKAGE_CHECKSUM_HASH, EMPTY_BYTE_ARRAY, false);

        // THEN an error should be reported
        verify(mCallback).onError(mTask, ERROR_DEVICE_ADMIN_MISSING);
        verifyNoMoreInteractions(mCallback);
    }

    @Test
    public void testPackageChecksumSha256_success() throws Exception {
        // GIVEN the hash of the downloaded file matches the parameter value
        when(mUtils.computeHashOfFile(TEST_LOCAL_FILENAME, Utils.SHA256_TYPE))
                .thenReturn(TEST_PACKAGE_CHECKSUM_HASH);

        // WHEN running the VerifyPackageTask
        runWithDownloadInfo(TEST_PACKAGE_CHECKSUM_HASH, EMPTY_BYTE_ARRAY, false);

        // THEN success should be called
        verify(mCallback).onSuccess(mTask);
        verifyNoMoreInteractions(mCallback);
    }

    @Test
    public void testPackageChecksumSha1_success() throws Exception {
        // GIVEN the hash of the downloaded file matches the parameter value in Sha1
        when(mUtils.computeHashOfFile(TEST_LOCAL_FILENAME, Utils.SHA1_TYPE))
                .thenReturn(TEST_PACKAGE_CHECKSUM_HASH);

        // WHEN running the VerifyPackageTask
        runWithDownloadInfo(TEST_PACKAGE_CHECKSUM_HASH, EMPTY_BYTE_ARRAY, true);

        // THEN success should be called
        verify(mCallback).onSuccess(mTask);
        verifyNoMoreInteractions(mCallback);
    }

    @Test
    public void testPackageChecksumSha1_failure() throws Exception {
        // GIVEN the hash of the downloaded file does no match the parameter value in Sha1
        when(mUtils.computeHashOfFile(TEST_LOCAL_FILENAME, Utils.SHA1_TYPE))
                .thenReturn(TEST_BAD_HASH);

        // WHEN running the VerifyPackageTask
        runWithDownloadInfo(TEST_PACKAGE_CHECKSUM_HASH, EMPTY_BYTE_ARRAY, true);

        // THEN hash mismatch error should be called
        verify(mCallback).onError(mTask, ERROR_HASH_MISMATCH);
        verifyNoMoreInteractions(mCallback);
    }

    @Test
    public void testSignatureHash_success() throws Exception {
        // GIVEN the hash of the signature matches the parameter value
        when(mUtils.computeHashOfByteArray(TEST_SIGNATURES[0].toByteArray()))
                .thenReturn(TEST_SIGNATURE_HASH);

        // WHEN running the VerifyPackageTask
        runWithDownloadInfo(EMPTY_BYTE_ARRAY, TEST_SIGNATURE_HASH, true);

        // THEN success should be called
        verify(mCallback).onSuccess(mTask);
        verifyNoMoreInteractions(mCallback);
    }

    @Test
    public void testSignatureHash_failure() throws Exception {
        // GIVEN the hash of the signature does not match the parameter value
        when(mUtils.computeHashOfByteArray(TEST_SIGNATURES[0].toByteArray()))
                .thenReturn(TEST_BAD_HASH);

        // WHEN running the VerifyPackageTask
        runWithDownloadInfo(EMPTY_BYTE_ARRAY, TEST_SIGNATURE_HASH, true);

        // THEN hash mismatch error should be called
        verify(mCallback).onError(mTask, ERROR_HASH_MISMATCH);
        verifyNoMoreInteractions(mCallback);
    }

    @Test
    public void testSignatureHash_noSignature() throws Exception {
        // GIVEN the package has no signature
        mPackageInfo.signatures = null;

        // WHEN running the VerifyPackageTask
        runWithDownloadInfo(EMPTY_BYTE_ARRAY, TEST_SIGNATURE_HASH, true);

        // THEN hash mismatch error should be called
        verify(mCallback).onError(mTask, ERROR_HASH_MISMATCH);
        verifyNoMoreInteractions(mCallback);
    }

    @Test
    public void testSignatureHash_digestFailure() throws Exception {
        // GIVEN the package has no signature
        when(mUtils.computeHashOfByteArray(any(byte[].class))).thenReturn(null);

        // WHEN running the VerifyPackageTask
        runWithDownloadInfo(EMPTY_BYTE_ARRAY, TEST_SIGNATURE_HASH, true);

        // THEN hash mismatch error should be called
        verify(mCallback).onError(mTask, ERROR_HASH_MISMATCH);
        verifyNoMoreInteractions(mCallback);
    }

    private void runWithDownloadInfo(byte[] packageChecksum, byte[] signatureChecksum,
            boolean supportsSha1) {
        PackageDownloadInfo downloadInfo = new PackageDownloadInfo.Builder()
                .setLocation(TEST_PACKAGE_LOCATION)
                .setPackageChecksum(packageChecksum)
                .setSignatureChecksum(signatureChecksum)
                .setPackageChecksumSupportsSha1(supportsSha1)
                .build();
        ProvisioningParams params = new ProvisioningParams.Builder()
                .setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE)
                .setDeviceAdminPackageName(TEST_PACKAGE_NAME)
                .setDeviceAdminDownloadInfo(downloadInfo)
                .build();
        mTask = new VerifyPackageTask(mUtils, mDownloadPackageTask, mContext, params, mCallback);
        mTask.run(TEST_USER_ID);
    }
}
