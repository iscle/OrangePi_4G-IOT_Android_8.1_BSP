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

import static com.android.managedprovisioning.task.DownloadPackageTask.ERROR_DOWNLOAD_FAILED;
import static com.android.managedprovisioning.task.DownloadPackageTask.ERROR_OTHER;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Matchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.DownloadManager;
import android.app.DownloadManager.Query;
import android.app.DownloadManager.Request;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.MatrixCursor;
import android.os.Handler;
import android.os.Looper;
import android.support.test.filters.FlakyTest;
import android.support.test.filters.SmallTest;

import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.PackageDownloadInfo;
import com.android.managedprovisioning.model.ProvisioningParams;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
@FlakyTest // TODO: http://b/34117742
public class DownloadPackageTaskTest {
    @Mock private Context mContext;
    @Mock private AbstractProvisioningTask.Callback mCallback;
    @Mock private DownloadManager mDownloadManager;
    @Mock private Utils mUtils;

    private static final String TEST_PACKAGE_NAME = "sample.package.name";
    private static final String TEST_PACKAGE_LOCATION = "http://www.some.uri.com";
    private static final String TEST_LOCAL_FILENAME = "/local/filename";
    private static final int TEST_USER_ID = 123;
    private static final byte[] TEST_SIGNATURE = new byte[] {'a', 'b', 'c', 'd'};

    private static final long TEST_DOWNLOAD_ID = 1234;
    private static final int PACKAGE_VERSION = 43;
    private static final PackageDownloadInfo TEST_DOWNLOAD_INFO = new PackageDownloadInfo.Builder()
            .setLocation(TEST_PACKAGE_LOCATION)
            .setSignatureChecksum(TEST_SIGNATURE)
            .setMinVersion(PACKAGE_VERSION)
            .build();
    private static final ProvisioningParams PARAMS = new ProvisioningParams.Builder()
            .setDeviceAdminPackageName(TEST_PACKAGE_NAME)
            .setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE)
            .setDeviceAdminDownloadInfo(TEST_DOWNLOAD_INFO)
            .build();

    private DownloadPackageTask mTask;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mContext.getSystemService(Context.DOWNLOAD_SERVICE)).thenReturn(mDownloadManager);
        when(mUtils.packageRequiresUpdate(TEST_PACKAGE_NAME, PACKAGE_VERSION, mContext))
                .thenReturn(true);

        mTask = new DownloadPackageTask(
                mUtils,
                mContext,
                PARAMS,
                mCallback);
    }

    @Test
    public void testAlreadyInstalled() throws Exception {
        // GIVEN the package is already installed, with the right version
        when(mUtils.packageRequiresUpdate(TEST_PACKAGE_NAME, PACKAGE_VERSION, mContext))
                .thenReturn(false);

        // WHEN running the download package task
        runTask();

        // THEN we get a success callback directly
        verifyOnTaskFinished(null);
        verifyNoMoreInteractions(mCallback);
    }

    @Test
    public void testNotConnected() throws Exception {
        // GIVEN we're not connected to a network
        doReturn(false).when(mUtils).isConnectedToNetwork(mContext);

        // WHEN running the download package task
        runTask();

        // THEN we get an error callback
        verify(mCallback).onError(mTask, ERROR_OTHER);
        verifyNoMoreInteractions(mCallback);
    }

    @Test
    public void testDownloadFailed() throws Exception {
        // GIVEN the download succeeds
        mockSuccessfulDownload(DownloadManager.STATUS_FAILED);

        // WHEN running the download package task
        runTask();

        // THEN a download receiver was registered
        BroadcastReceiver receiver = verifyDownloadReceiver();

        // WHEN invoking download complete
        receiver.onReceive(mContext, new Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        // THEN we get a success callback
        verify(mCallback).onError(mTask, ERROR_DOWNLOAD_FAILED);
        verifyNoMoreInteractions(mCallback);
    }

    @Test
    public void testDownloadSucceeded() throws Exception {
        // GIVEN the download succeeds
        mockSuccessfulDownload(DownloadManager.STATUS_SUCCESSFUL);

        // WHEN running the download package task
        runTask();

        // THEN a download receiver was registered
        BroadcastReceiver receiver = verifyDownloadReceiver();

        // WHEN invoking download complete
        receiver.onReceive(mContext, new Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        // THEN we get a success callback
        verifyOnTaskFinished(TEST_LOCAL_FILENAME);
        verifyNoMoreInteractions(mCallback);
    }

    /** Test that it works fine even if DownloadManager sends the broadcast twice */
    @Test
    public void testSendBroadcastTwice() throws Exception {
        // GIVEN the download succeeds
        mockSuccessfulDownload(DownloadManager.STATUS_SUCCESSFUL);

        // WHEN running the download package task
        runTask();

        // THEN a download receiver was registered
        BroadcastReceiver receiver = verifyDownloadReceiver();

        // WHEN invoking download complete twice
        receiver.onReceive(mContext, new Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        receiver.onReceive(mContext, new Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        // THEN we still get only one success callback
        verifyOnTaskFinished(TEST_LOCAL_FILENAME);
        verifyNoMoreInteractions(mCallback);
    }

    private void mockSuccessfulDownload(int downloadStatus) {
        doReturn(true).when(mUtils).isConnectedToNetwork(any(Context.class));
        when(mDownloadManager.enqueue(any(Request.class))).thenReturn(TEST_DOWNLOAD_ID);
        MatrixCursor cursor = new MatrixCursor(new String[]{
                DownloadManager.COLUMN_STATUS,
                DownloadManager.COLUMN_LOCAL_FILENAME});
        cursor.addRow(new Object[]{downloadStatus, TEST_LOCAL_FILENAME});
        when(mDownloadManager.query(any(Query.class))).thenReturn(cursor);
    }

    private BroadcastReceiver verifyDownloadReceiver() {
        verify(mDownloadManager).setAccessFilename(true);
        ArgumentCaptor<BroadcastReceiver> receiverCaptor = ArgumentCaptor.forClass(
                BroadcastReceiver.class);
        ArgumentCaptor<IntentFilter> filterCaptor = ArgumentCaptor.forClass(
                IntentFilter.class);
        verify(mContext).registerReceiver(
                receiverCaptor.capture(),
                filterCaptor.capture(),
                nullable(String.class),
                any(Handler.class));
        assertEquals(filterCaptor.getValue().getAction(0),
                DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        return receiverCaptor.getValue();
    }

    private void verifyOnTaskFinished(String location) {
        verify(mCallback).onSuccess(mTask);
        assertEquals(location, mTask.getDownloadedPackageLocation());
    }

    private void runTask() {
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        mTask.run(TEST_USER_ID);
    }
}
