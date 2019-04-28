/*
 * Copyright 2014, The Android Open Source Project
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
package com.android.managedprovisioning.task;

import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_DOWNLOAD_PACKAGE_TASK_MS;
import static com.android.internal.util.Preconditions.checkNotNull;

import android.app.DownloadManager;
import android.app.DownloadManager.Query;
import android.app.DownloadManager.Request;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.Globals;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.PackageDownloadInfo;
import com.android.managedprovisioning.model.ProvisioningParams;

import java.io.File;

/**
 * Downloads the management app apk from the url provided by {@link PackageDownloadInfo#location}.
 * The location of the downloaded file can be read via {@link #getDownloadedPackageLocation()}.
 */
public class DownloadPackageTask extends AbstractProvisioningTask {
    public static final int ERROR_DOWNLOAD_FAILED = 0;
    public static final int ERROR_OTHER = 1;

    private BroadcastReceiver mReceiver;
    private final DownloadManager mDownloadManager;
    private final String mPackageName;
    private final PackageDownloadInfo mPackageDownloadInfo;
    private long mDownloadId;

    private final Utils mUtils;

    private String mDownloadLocationTo; //local file where the package is downloaded.
    private boolean mDoneDownloading;

    public DownloadPackageTask(
            Context context,
            ProvisioningParams provisioningParams,
            Callback callback) {
        this(new Utils(), context, provisioningParams, callback);
    }

    @VisibleForTesting
    DownloadPackageTask(
            Utils utils,
            Context context,
            ProvisioningParams provisioningParams,
            Callback callback) {
        super(context, provisioningParams, callback);

        mUtils = checkNotNull(utils);
        mDownloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        mDownloadManager.setAccessFilename(true);
        mPackageName = provisioningParams.inferDeviceAdminPackageName();
        mPackageDownloadInfo = checkNotNull(provisioningParams.deviceAdminDownloadInfo);
    }

    @Override
    public int getStatusMsgId() {
        return R.string.progress_download;
    }

    @Override
    public void run(int userId) {
        startTaskTimer();
        if (!mUtils.packageRequiresUpdate(mPackageName, mPackageDownloadInfo.minVersion,
                mContext)) {
            // Do not log time if package is already on device and does not require an update, as
            // that isn't useful.
            success();
            return;
        }
        if (!mUtils.isConnectedToNetwork(mContext)) {
            ProvisionLogger.loge("DownloadPackageTask: not connected to the network, can't download"
                    + " the package");
            error(ERROR_OTHER);
            return;
        }
        mReceiver = createDownloadReceiver();
        // register the receiver on the worker thread to avoid threading issues with respect to
        // the location variable
        mContext.registerReceiver(mReceiver,
                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                null,
                new Handler(Looper.myLooper()));

        if (Globals.DEBUG) {
            ProvisionLogger.logd("Starting download from " + mPackageDownloadInfo.location);
        }

        Request request = new Request(Uri.parse(mPackageDownloadInfo.location));

        // Note that the apk may not actually be downloaded to this path. This could happen if
        // this file already exists.
        String path = mContext.getExternalFilesDir(null)
                + "/download_cache/managed_provisioning_downloaded_app.apk";
        File downloadedFile = new File(path);
        downloadedFile.getParentFile().mkdirs(); // If the folder doesn't exists it is created
        request.setDestinationUri(Uri.fromFile(downloadedFile));

        if (mPackageDownloadInfo.cookieHeader != null) {
            request.addRequestHeader("Cookie", mPackageDownloadInfo.cookieHeader);
            if (Globals.DEBUG) {
                ProvisionLogger.logd("Downloading with http cookie header: "
                        + mPackageDownloadInfo.cookieHeader);
            }
        }
        mDownloadId = mDownloadManager.enqueue(request);
    }

    @Override
    protected int getMetricsCategory() {
        return PROVISIONING_DOWNLOAD_PACKAGE_TASK_MS;
    }

    private BroadcastReceiver createDownloadReceiver() {
        return new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
                    Query q = new Query();
                    q.setFilterById(mDownloadId);
                    Cursor c = mDownloadManager.query(q);
                    if (c.moveToFirst()) {
                        int columnIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
                        if (DownloadManager.STATUS_SUCCESSFUL == c.getInt(columnIndex)) {
                            mDownloadLocationTo = c.getString(
                                    c.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME));
                            c.close();
                            onDownloadSuccess();
                        } else if (DownloadManager.STATUS_FAILED == c.getInt(columnIndex)) {
                            int reason = c.getColumnIndex(DownloadManager.COLUMN_REASON);
                            c.close();
                            onDownloadFail(reason);
                        }
                    }
                }
            }
        };
    }

    /**
     * For a successful download, check that the downloaded file is the expected file.
     * If the package hash is provided then that is used, otherwise a signature hash is used.
     */
    private void onDownloadSuccess() {
        if (mDoneDownloading) {
            // DownloadManager can send success more than once. Only act first time.
            return;
        }

        ProvisionLogger.logd("Downloaded succesfully to: " + mDownloadLocationTo);
        mDoneDownloading = true;
        stopTaskTimer();
        success();
    }

    public String getDownloadedPackageLocation() {
        return mDownloadLocationTo;
    }

    private void onDownloadFail(int errorCode) {
        ProvisionLogger.loge("Downloading package failed.");
        ProvisionLogger.loge("COLUMN_REASON in DownloadManager response has value: "
                + errorCode);
        error(ERROR_DOWNLOAD_FAILED);
    }

    public void cleanUp() {
        if (mReceiver != null) {
            //Unregister receiver.
            mContext.unregisterReceiver(mReceiver);
            mReceiver = null;
        }

        boolean removeSuccess = mDownloadManager.remove(mDownloadId) == 1;
        if (removeSuccess) {
            ProvisionLogger.logd("Successfully removed installer file.");
        } else {
            ProvisionLogger.loge("Could not remove installer file.");
            // Ignore this error. Failing cleanup should not stop provisioning flow.
        }
    }
}
