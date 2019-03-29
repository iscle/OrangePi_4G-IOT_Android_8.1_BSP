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

package android.cts.backup;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetCleaner;
import com.android.tradefed.targetprep.TargetSetupError;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tradedfed target preparer for the backup tests.
 * Enables backup before all the tests and selects local transport.
 * Reverts to the original state after all the tests are executed.
 */
@OptionClass(alias = "backup-preparer")
public class BackupPreparer implements ITargetCleaner {
    @Option(name="enable-backup-if-needed", description=
            "Enable backup before all the tests and return to the original state after.")
    private boolean mEnableBackup = true;

    @Option(name="select-local-transport", description=
            "Select local transport before all the tests and return to the original transport "
                    + "after.")
    private boolean mSelectLocalTransport = true;

    /** Value of PackageManager.FEATURE_BACKUP */
    private static final String FEATURE_BACKUP = "android.software.backup";

    private static final String LOCAL_TRANSPORT =
            "android/com.android.internal.backup.LocalTransport";

    private static final int BACKUP_PROVISIONING_TIMEOUT_SECONDS = 30;
    private static final int BACKUP_PROVISIONING_POLL_INTERVAL_SECONDS = 1;

    private boolean mIsBackupSupported;
    private boolean mWasBackupEnabled;
    private String mOldTransport;
    private ITestDevice mDevice;

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        mDevice = device;

        mIsBackupSupported = mDevice.hasFeature("feature:" + FEATURE_BACKUP);

        // In case the device was just rebooted, wait for the broadcast queue to get idle to avoid
        // any interference from services doing backup clean up on reboot.
        waitForBroadcastIdle();

        if (mIsBackupSupported) {
            // Enable backup and select local backup transport
            if (!hasBackupTransport(LOCAL_TRANSPORT)) {
                throw new TargetSetupError("Device should have LocalTransport available",
                        device.getDeviceDescriptor());
            }
            if (mEnableBackup) {
                CLog.i("Enabling backup on %s", mDevice.getSerialNumber());
                mWasBackupEnabled = enableBackup(true);
                CLog.d("Backup was enabled? : %s", mWasBackupEnabled);
                if (mSelectLocalTransport) {
                    CLog.i("Selecting local transport on %s", mDevice.getSerialNumber());
                    mOldTransport = setBackupTransport(LOCAL_TRANSPORT);
                    CLog.d("Old transport : %s", mOldTransport);
                }
                waitForBackupInitialization();
            }
        }
    }

    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e)
            throws DeviceNotAvailableException {
        mDevice = device;

        if (mIsBackupSupported) {
            if (mEnableBackup) {
                CLog.i("Returning backup to it's previous state on %s", mDevice.getSerialNumber());
                enableBackup(mWasBackupEnabled);
                if (mSelectLocalTransport) {
                    CLog.i("Returning selected transport to it's previous value on %s",
                            mDevice.getSerialNumber());
                    setBackupTransport(mOldTransport);
                }
            }
        }
    }

    // Copied over from BackupQuotaTest
    private boolean hasBackupTransport(String transport) throws DeviceNotAvailableException {
        String output = mDevice.executeShellCommand("bmgr list transports");
        for (String t : output.split(" ")) {
            if (transport.equals(t.trim())) {
                return true;
            }
        }
        return false;
    }

    // Copied over from BackupQuotaTest
    private boolean enableBackup(boolean enable) throws DeviceNotAvailableException {
        boolean previouslyEnabled;
        String output = mDevice.executeShellCommand("bmgr enabled");
        Pattern pattern = Pattern.compile("^Backup Manager currently (enabled|disabled)$");
        Matcher matcher = pattern.matcher(output.trim());
        if (matcher.find()) {
            previouslyEnabled = "enabled".equals(matcher.group(1));
        } else {
            throw new RuntimeException("non-parsable output setting bmgr enabled: " + output);
        }

        mDevice.executeShellCommand("bmgr enable " + enable);
        return previouslyEnabled;
    }

    // Copied over from BackupQuotaTest
    private String setBackupTransport(String transport) throws DeviceNotAvailableException {
        String output = mDevice.executeShellCommand("bmgr transport " + transport);
        Pattern pattern = Pattern.compile("\\(formerly (.*)\\)$");
        Matcher matcher = pattern.matcher(output);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            throw new RuntimeException("non-parsable output setting bmgr transport: " + output);
        }
    }

    private void waitForBackupInitialization()
        throws TargetSetupError, DeviceNotAvailableException {
        long tryUntilNanos = System.nanoTime()
            + TimeUnit.SECONDS.toNanos(BACKUP_PROVISIONING_TIMEOUT_SECONDS);
        while (System.nanoTime() < tryUntilNanos) {
            String output = mDevice.executeShellCommand("dumpsys backup");
            if (output.matches("(?s)"  // DOTALL
                + "^Backup Manager is .* not pending init.*")) {
                return;
            }
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(BACKUP_PROVISIONING_POLL_INTERVAL_SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new TargetSetupError("Timed out waiting for backup initialization",
            mDevice.getDeviceDescriptor());
    }

    // Copied over from BaseDevicePolicyTest
    private void waitForBroadcastIdle() throws DeviceNotAvailableException, TargetSetupError {
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        try {
            // we allow 20 min for the command to complete and 10 min for the command to start to
            // output something
            mDevice.executeShellCommand(
                    "am wait-for-broadcast-idle", receiver, 20, 10, TimeUnit.MINUTES, 0);
        } finally {
            String output = receiver.getOutput();
            CLog.d("Output from 'am wait-for-broadcast-idle': %s", output);
            if (!output.contains("All broadcast queues are idle!")) {
                // the call most likely failed we should fail the test
                throw new TargetSetupError("'am wait-for-broadcase-idle' did not complete.",
                        mDevice.getDeviceDescriptor());
                // TODO: consider adding a reboot or recovery before failing if necessary
            }
        }
    }

}
