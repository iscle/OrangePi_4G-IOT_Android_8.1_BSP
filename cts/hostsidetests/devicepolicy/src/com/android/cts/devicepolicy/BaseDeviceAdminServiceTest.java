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
 * limitations under the License.
 */
package com.android.cts.devicepolicy;

import com.android.tradefed.log.LogUtil.CLog;

import java.util.concurrent.TimeUnit;

public abstract class BaseDeviceAdminServiceTest extends BaseDevicePolicyTest {
    protected static final String OWNER_PKG = "com.android.cts.deviceadminservice";
    protected static final String OWNER_PKG_B = "com.android.cts.deviceadminserviceb";

    protected static final String OWNER_APK_1 = "CtsDeviceAdminService1.apk";
    protected static final String OWNER_APK_2 = "CtsDeviceAdminService2.apk";
    protected static final String OWNER_APK_3 = "CtsDeviceAdminService3.apk";
    protected static final String OWNER_APK_4 = "CtsDeviceAdminService4.apk";

    protected static final String OWNER_APK_B = "CtsDeviceAdminServiceB.apk";

    protected static final String ADMIN_RECEIVER_TEST_CLASS = ".MyOwner";

    protected static final String OWNER_COMPONENT = OWNER_PKG + "/" + ADMIN_RECEIVER_TEST_CLASS;
    protected static final String OWNER_SERVICE = OWNER_PKG + "/.MyService";
    protected static final String OWNER_SERVICE2 = OWNER_PKG + "/.MyService2";

    protected static final String OWNER_COMPONENT_B = OWNER_PKG_B + "/"
            + OWNER_PKG + ADMIN_RECEIVER_TEST_CLASS;
    protected static final String OWNER_SERVICE_B = OWNER_PKG_B + "/"
            + OWNER_PKG + ".MyService";

    private static final int TIMEOUT_SECONDS = 3 * 60;

    private boolean mMultiUserSupported;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mMultiUserSupported = getMaxNumberOfUsersSupported() > 1 && getDevice().getApiLevel() >= 21;
    }

    @Override
    protected void tearDown() throws Exception {
        if (isTestEnabled()) {
            removeAdmin(OWNER_COMPONENT, getUserId());
            removeAdmin(OWNER_COMPONENT_B, getUserId());
            getDevice().uninstallPackage(OWNER_PKG);
            getDevice().uninstallPackage(OWNER_PKG_B);
        }
        super.tearDown();
    }

    protected abstract int getUserId();

    protected abstract boolean isTestEnabled();

    protected void executeDeviceTestMethod(String className, String testName) throws Exception {
        runDeviceTestsAsUser(OWNER_PKG, className, testName, getUserId());
    }

    protected interface RunnableWithThrowable {
        void run() throws Throwable;
    }

    protected void withRetry(RunnableWithThrowable test) throws Throwable {
        final long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);

        Thread.sleep(500);

        Throwable lastThrowable = null;
        while (System.nanoTime() < until) {
            try {
                test.run();
                // Pass, return.
                return;
            } catch (Throwable th) {
                lastThrowable = th;
            }
            Thread.sleep(3000);
        }
        if (lastThrowable != null) {
            throw lastThrowable;
        }
        fail("Internal error: test didn't run, exception not thrown.");
    }

    protected abstract void setAsOwnerOrFail(String component) throws Exception;

    public void testAll() throws Throwable {
        if (!isTestEnabled()) {
            return;
        }

        // Install
        CLog.i("Installing apk1...");
        installAppAsUser(OWNER_APK_1, getUserId());

        CLog.i("Making it a device/profile owner...");
        setAsOwnerOrFail(OWNER_COMPONENT);

        withRetry(() -> assertServiceBound(OWNER_SERVICE));

        // Remove admin.
        CLog.i("Removing admin...");
        removeAdmin(OWNER_COMPONENT, getUserId());
        withRetry(() -> assertServiceNotBound(OWNER_SERVICE));

        // Overwrite -> update.
        CLog.i("Re-installing apk1...");
        installAppAsUser(OWNER_APK_1, getUserId());

        CLog.i("Making it a device/profile owner...");
        setAsOwnerOrFail(OWNER_COMPONENT);
        withRetry(() -> assertServiceBound(OWNER_SERVICE));

        CLog.i("Installing apk2...");
        installAppAsUser(OWNER_APK_2, getUserId());
        withRetry(() -> assertServiceBound(OWNER_SERVICE)); // Should still be bound.

        // Service exported -> not bound.
        CLog.i("Installing apk3...");
        installAppAsUser(OWNER_APK_3, getUserId());
        withRetry(() -> assertServiceNotBound(OWNER_SERVICE));

        // Recover.
        CLog.i("Installing apk2 again...");
        installAppAsUser(OWNER_APK_2, getUserId());
        withRetry(() -> assertServiceBound(OWNER_SERVICE));

        // Multiple service found -> not bound.
        CLog.i("Installing apk4...");
        installAppAsUser(OWNER_APK_4, getUserId());
        withRetry(() -> assertServiceNotBound(OWNER_SERVICE));
        withRetry(() -> assertServiceNotBound(OWNER_SERVICE2));

        // Disable service1 -> now there's only one service, so should be bound.
        CLog.i("Running testDisableService1...");
        executeDeviceTestMethod(".ComponentController", "testDisableService1");
        withRetry(() -> assertServiceNotBound(OWNER_SERVICE));
        withRetry(() -> assertServiceBound(OWNER_SERVICE2));

        CLog.i("Running testDisableService2...");
        executeDeviceTestMethod(".ComponentController", "testDisableService2");
        withRetry(() -> assertServiceNotBound(OWNER_SERVICE));
        withRetry(() -> assertServiceNotBound(OWNER_SERVICE2));

        CLog.i("Running testEnableService1...");
        executeDeviceTestMethod(".ComponentController", "testEnableService1");
        withRetry(() -> assertServiceBound(OWNER_SERVICE));
        withRetry(() -> assertServiceNotBound(OWNER_SERVICE2));

        CLog.i("Running testEnableService2...");
        executeDeviceTestMethod(".ComponentController", "testEnableService2");
        withRetry(() -> assertServiceNotBound(OWNER_SERVICE));
        withRetry(() -> assertServiceNotBound(OWNER_SERVICE2));

        // Remove admin.
        CLog.i("Removing admin again...");
        removeAdmin(OWNER_COMPONENT, getUserId());
        withRetry(() -> assertServiceNotBound(OWNER_SERVICE));

        // Retry with package 1 and remove admin.
        CLog.i("Installing apk1 again...");
        installAppAsUser(OWNER_APK_1, getUserId());

        CLog.i("Making it a device/profile owner again...");
        setAsOwnerOrFail(OWNER_COMPONENT);
        withRetry(() -> assertServiceBound(OWNER_SERVICE));

        CLog.i("Removing admin again...");
        removeAdmin(OWNER_COMPONENT, getUserId());
        withRetry(() -> assertServiceNotBound(OWNER_SERVICE));

        // Now install package B and make it the owner.  OWNER_APK_1 still exists, but it shouldn't
        // interfere.
        CLog.i("Installing apk B...");
        installAppAsUser(OWNER_APK_B, getUserId());

        CLog.i("Making it a device/profile owner...");
        setAsOwnerOrFail(OWNER_COMPONENT_B);
        withRetry(() -> assertServiceNotBound(OWNER_SERVICE));
        withRetry(() -> assertServiceBound(OWNER_SERVICE_B));
    }

    private String rumpDumpSysService(String component) throws Exception {
        final String command = "dumpsys activity services " + component;
        final String commandOutput = getDevice().executeShellCommand(command);
        CLog.d("Output for command " + command + ":\n" + commandOutput);
        return commandOutput;
    }

    private void assertServiceBound(String component) throws Exception {
        final String commandOutput = rumpDumpSysService(component);
        for (String line : commandOutput.split("\r*\n")) {
            if (line.contains("ConnectionRecord") && line.contains(component)) {
                return;
            }
        }
        fail("Service " + OWNER_SERVICE + " not bound.  Output was:\n" + commandOutput);
    }

    private void assertServiceNotBound(String component) throws Exception {
        final String commandOutput = rumpDumpSysService(component);
        for (String line : commandOutput.split("\r*\n")) {
            if (line.contains("ConnectionRecord") && line.contains(component)) {
                fail("Service " + OWNER_SERVICE + " is bound.  Output was:\n" + commandOutput);
            }
        }
    }

/* When the service is bound, "dumpsys activity services" shows something like this:
  * ServiceRecord{1525afe u0 com.android.cts.deviceadminservice/.MyService}
    intent={cmp=com.android.cts.deviceadminservice/.MyService}
    packageName=com.android.cts.deviceadminservice
    processName=com.android.cts.deviceadminservice
    baseDir=/data/app/com.android.cts.deviceadminservice-kXKTlCILmDfib2P76FI75A==/base.apk
    dataDir=/data/user/0/com.android.cts.deviceadminservice
    app=ProcessRecord{205d686 22751:com.android.cts.deviceadminservice/u0a143}
    createTime=-3s957ms startingBgTimeout=--
    lastActivity=-3s927ms restartTime=-3s927ms createdFromFg=true
    Bindings:
    * IntentBindRecord{57d5b47 CREATE}:
      intent={cmp=com.android.cts.deviceadminservice/.MyService}
      binder=android.os.BinderProxy@819af74
      requested=true received=true hasBound=true doRebind=false
      * Client AppBindRecord{e1d3c9d ProcessRecord{a8162c0 757:system/1000}}
        Per-process Connections:
          ConnectionRecord{10ab6b9 u0 CR FGS com.android.cts.deviceadminservice/.MyService:@51e9080}
    All Connections:
      ConnectionRecord{10ab6b9 u0 CR FGS com.android.cts.deviceadminservice/.MyService:@51e9080}

  * ConnectionRecord{10ab6b9 u0 CR FGS com.android.cts.deviceadminservice/.MyService:@51e9080}
    binding=AppBindRecord{e1d3c9d com.android.cts.deviceadminservice/.MyService:system}
    conn=android.app.LoadedApk$ServiceDispatcher$InnerConnection@51e9080 flags=0x4000001
 */
}
