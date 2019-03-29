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
 * limitations under the License
 */

package com.android.cts.managedprofile;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.test.AndroidTestCase;


public class CameraPolicyTest extends AndroidTestCase {

    protected static final String MANAGED_PROFILE_PKG = "com.android.cts.managedprofile";

    private static final String PRIMARY_ADMIN_RECEIVER_TEST_CLASS =
            MANAGED_PROFILE_PKG + ".PrimaryUserDeviceAdmin";

    private static final String MANAGED_PROFILE_ADMIN_RECEIVER_TEST_CLASS =
            MANAGED_PROFILE_PKG + ".BaseManagedProfileTest$BasicAdminReceiver";

    private DevicePolicyManager mDevicePolicyManager;

    private CameraManager mCameraManager;

    private ComponentName mPrimaryAdminComponent;

    private ComponentName mManagedProfileAdminComponent;

    private HandlerThread mBackgroundThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDevicePolicyManager = (DevicePolicyManager) getContext()
                .getSystemService(Context.DEVICE_POLICY_SERVICE);
        mCameraManager = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
        mPrimaryAdminComponent = new ComponentName(MANAGED_PROFILE_PKG,
                PRIMARY_ADMIN_RECEIVER_TEST_CLASS);
        mManagedProfileAdminComponent = new ComponentName(MANAGED_PROFILE_PKG,
                MANAGED_PROFILE_ADMIN_RECEIVER_TEST_CLASS);
        startBackgroundThread();
    }

    @Override
    protected void tearDown() throws Exception {
        stopBackgroundThread();
        super.tearDown();
    }

    public void testDisableCameraInManagedProfile() throws Exception {
        mDevicePolicyManager.setCameraDisabled(mManagedProfileAdminComponent, true);
        assertTrue(mDevicePolicyManager.getCameraDisabled(mManagedProfileAdminComponent));
        assertTrue(mDevicePolicyManager.getCameraDisabled(null));
        checkCanOpenCamera(false);
    }

    public void testEnableCameraInManagedProfile() throws Exception {
        mDevicePolicyManager.setCameraDisabled(mManagedProfileAdminComponent, false);
        assertFalse(mDevicePolicyManager.getCameraDisabled(mManagedProfileAdminComponent));
        assertFalse(mDevicePolicyManager.getCameraDisabled(null));
        checkCanOpenCamera(true);
    }

    public void testDisableCameraInPrimaryProfile() throws Exception {
        mDevicePolicyManager.setCameraDisabled(mPrimaryAdminComponent, true);
        assertTrue(mDevicePolicyManager.getCameraDisabled(mPrimaryAdminComponent));
        assertTrue(mDevicePolicyManager.getCameraDisabled(null));
        checkCanOpenCamera(false);
    }

    public void testEnableCameraInPrimaryProfile() throws Exception {
        mDevicePolicyManager.setCameraDisabled(mPrimaryAdminComponent, false);
        assertFalse(mDevicePolicyManager.getCameraDisabled(mPrimaryAdminComponent));
        assertFalse(mDevicePolicyManager.getCameraDisabled(null));
        checkCanOpenCamera(true);
    }

    public void testIsCameraEnabledInPrimaryProfile() throws Exception {
        assertFalse(mDevicePolicyManager.getCameraDisabled(mPrimaryAdminComponent));
        assertFalse(mDevicePolicyManager.getCameraDisabled(null));
        checkCanOpenCamera(true);
    }

    public void testIsCameraEnabledInManagedProfile() throws Exception {
        assertFalse(mDevicePolicyManager.getCameraDisabled(mManagedProfileAdminComponent));
        assertFalse(mDevicePolicyManager.getCameraDisabled(null));
        checkCanOpenCamera(true);
    }

    /**
     * Beginning with Android 7.0, the camera restriction policy isn't kept in the
     * system property("sys.secpolicy.camera.off_<userId>") anymore, CameraService
     * now checks if camera is disabled via AppOpsService.
     *
     * The propagation of camera retriction policy from DevicePolicyManagerService
     * to UserManagerService and then finally to AppOpsService is NOT synchronous,
     * so here #blockUntilOpenCamera is called many times until policy is enforced
     * or timed out.
     *
     * @see android.app.AppOpsManager#checkOp(String, int, String)
     * @see android.app.AppOpsManager.OnOpChangedListener
     */
    private void checkCanOpenCamera(boolean canOpen) throws Exception {
        int retries = 10;
        boolean successToOpen = !canOpen;
        while (successToOpen != canOpen && retries > 0) {
            retries--;
            Thread.sleep(500);
            successToOpen = CameraUtils
                    .blockUntilOpenCamera(mCameraManager, mBackgroundHandler);
        }
        assertEquals(String.format("Timed out waiting the value to change to %b (actual=%b)",
                    canOpen, successToOpen), canOpen, successToOpen);
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
