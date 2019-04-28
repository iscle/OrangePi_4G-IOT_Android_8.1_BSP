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

import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_INSTALL_PACKAGE_TASK_MS;
import static com.android.internal.util.Preconditions.checkNotNull;

import android.content.Context;
import android.content.pm.IPackageInstallObserver;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.text.TextUtils;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.model.ProvisioningParams;

import java.io.File;

/**
 * Installs the management app apk from a download location provided by
 * {@link DownloadPackageTask#getDownloadedPackageLocation()}.
 */
public class InstallPackageTask extends AbstractProvisioningTask {
    public static final int ERROR_PACKAGE_INVALID = 0;
    public static final int ERROR_INSTALLATION_FAILED = 1;

    private final SettingsFacade mSettingsFacade;
    private final DownloadPackageTask mDownloadPackageTask;

    private PackageManager mPm;
    private boolean mInitialPackageVerifierEnabled;

    /**
     * Create an InstallPackageTask. When run, this will attempt to install the device admin package
     * if it is non-null.
     *
     * {@see #run(String, String)} for more detail on package installation.
     */
    public InstallPackageTask(
            DownloadPackageTask downloadPackageTask,
            Context context,
            ProvisioningParams params,
            Callback callback) {
        this(new SettingsFacade(), downloadPackageTask, context, params, callback);
    }

    @VisibleForTesting
    InstallPackageTask(
            SettingsFacade settingsFacade,
            DownloadPackageTask downloadPackageTask,
            Context context,
            ProvisioningParams params,
            Callback callback) {
        super(context, params, callback);

        mPm = context.getPackageManager();
        mSettingsFacade = checkNotNull(settingsFacade);
        mDownloadPackageTask = checkNotNull(downloadPackageTask);
    }

    @Override
    public int getStatusMsgId() {
        return R.string.progress_install;
    }

    /**
     * Installs a package. The package will be installed from the given location if one is provided.
     * If a null or empty location is provided, and the package is installed for a different user,
     * it will be enabled for the calling user. If the package location is not provided and the
     * package is not installed for any other users, this task will produce an error.
     *
     * Errors will be indicated if a downloaded package is invalid, or installation fails.
     */
    @Override
    public void run(int userId) {
        startTaskTimer();
        String packageLocation = mDownloadPackageTask.getDownloadedPackageLocation();
        String packageName = mProvisioningParams.inferDeviceAdminPackageName();

        ProvisionLogger.logi("Installing package");
        mInitialPackageVerifierEnabled = mSettingsFacade.isPackageVerifierEnabled(mContext);
        if (TextUtils.isEmpty(packageLocation)) {
            // Do not log time if not installing any package, as that isn't useful.
            success();
            return;
        }

        // Temporarily turn off package verification.
        mSettingsFacade.setPackageVerifierEnabled(mContext, false);

        // Allow for replacing an existing package.
        // Needed in case this task is performed multiple times.
        mPm.installPackage(Uri.parse("file://" + packageLocation),
                new PackageInstallObserver(packageName, packageLocation),
                /* flags */ PackageManager.INSTALL_REPLACE_EXISTING,
                mContext.getPackageName());
    }

    @Override
    protected int getMetricsCategory() {
        return PROVISIONING_INSTALL_PACKAGE_TASK_MS;
    }

    private class PackageInstallObserver extends IPackageInstallObserver.Stub {
        private final String mPackageName;
        private final String mPackageLocation;

        public PackageInstallObserver(String packageName, String packageLocation) {
            mPackageName = packageName;
            mPackageLocation = packageLocation;
        }

        @Override
        public void packageInstalled(String packageName, int returnCode) {
            mSettingsFacade.setPackageVerifierEnabled(mContext, mInitialPackageVerifierEnabled);
            if (packageName != null && !packageName.equals(mPackageName))  {
                ProvisionLogger.loge("Package doesn't have expected package name.");
                error(ERROR_PACKAGE_INVALID);
                return;
            }
            if (returnCode == PackageManager.INSTALL_SUCCEEDED) {
                ProvisionLogger.logd("Package " + mPackageName + " is succesfully installed.");
                stopTaskTimer();
                success();
            } else if (returnCode == PackageManager.INSTALL_FAILED_VERSION_DOWNGRADE) {
                ProvisionLogger.logd("Current version of " + mPackageName
                        + " higher than the version to be installed. It was not reinstalled.");
                // If the package is already at a higher version: success.
                // Do not log time if package is already at a higher version, as that isn't useful.
                success();
            } else {
                ProvisionLogger.logd(
                        "Installing package " + mPackageName + " failed.");
                ProvisionLogger.logd(
                        "Errorcode returned by IPackageInstallObserver = " + returnCode);
                error(ERROR_INSTALLATION_FAILED);
            }
            // remove the file containing the apk in order not to use too much space.
            new File(mPackageLocation).delete();
        }
    }
}
