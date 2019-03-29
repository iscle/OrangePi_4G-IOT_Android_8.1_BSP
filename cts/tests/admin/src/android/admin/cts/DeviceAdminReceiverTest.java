/*
 * Copyright (C) 2011 The Android Open Source Project
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

package android.admin.cts;

import static android.app.admin.DeviceAdminReceiver.ACTION_PASSWORD_CHANGED;
import static android.app.admin.DeviceAdminReceiver.ACTION_PASSWORD_FAILED;
import static android.app.admin.DeviceAdminReceiver.ACTION_PASSWORD_SUCCEEDED;
import static android.app.admin.DeviceAdminReceiver.ACTION_PASSWORD_EXPIRING;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Process;
import android.os.UserHandle;
import android.platform.test.annotations.Presubmit;
import android.test.AndroidTestCase;
import android.util.Log;

import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.compat.ArgumentMatcher;

public class DeviceAdminReceiverTest extends AndroidTestCase {

    private static final String TAG = DeviceAdminReceiverTest.class.getSimpleName();
    private static final String DISABLE_WARNING = "Disable Warning";
    private static final String BUGREPORT_HASH = "f4k3h45h";
    private static final long NETWORK_LOGS_TOKEN = (123L << 40L);
    private static final int NETWORK_LOGS_COUNT = (321 << 20);
    private static final UserHandle USER = Process.myUserHandle();

    private static final String ACTION_BUGREPORT_SHARING_DECLINED =
            "android.app.action.BUGREPORT_SHARING_DECLINED";
    private static final String ACTION_BUGREPORT_FAILED = "android.app.action.BUGREPORT_FAILED";
    private static final String ACTION_BUGREPORT_SHARE =
            "android.app.action.BUGREPORT_SHARE";
    private static final String ACTION_SECURITY_LOGS_AVAILABLE
            = "android.app.action.SECURITY_LOGS_AVAILABLE";
    private static final String EXTRA_BUGREPORT_FAILURE_REASON =
            "android.app.extra.BUGREPORT_FAILURE_REASON";
    private static final String EXTRA_BUGREPORT_HASH = "android.app.extra.BUGREPORT_HASH";

    private static final String ACTION_NETWORK_LOGS_AVAILABLE
            = "android.app.action.NETWORK_LOGS_AVAILABLE";
    private static final String EXTRA_NETWORK_LOGS_TOKEN =
            "android.app.extra.EXTRA_NETWORK_LOGS_TOKEN";
    private static final String EXTRA_NETWORK_LOGS_COUNT =
            "android.app.extra.EXTRA_NETWORK_LOGS_COUNT";

    @Spy
    public DeviceAdminReceiver mReceiver;
    private boolean mDeviceAdmin;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mReceiver = new DeviceAdminReceiver();
        mDeviceAdmin =
                mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN);
        MockitoAnnotations.initMocks(this);
    }

    @Presubmit
    public void testOnReceivePasswordChanged() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testOnReceivePasswordChanged");
            return;
        }
        mReceiver.onReceive(mContext, new Intent(DeviceAdminReceiver.ACTION_PASSWORD_CHANGED)
                .putExtra(Intent.EXTRA_USER, USER));
        verify(mReceiver).onPasswordChanged(any(), actionEq(ACTION_PASSWORD_CHANGED), eq(USER));
        verify(mReceiver).onPasswordChanged(any(), actionEq(ACTION_PASSWORD_CHANGED));
    }

    @Presubmit
    public void testOnReceivePasswordFailed() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testOnReceivePasswordFailed");
        }
        mReceiver.onReceive(mContext, new Intent(DeviceAdminReceiver.ACTION_PASSWORD_FAILED)
                .putExtra(Intent.EXTRA_USER, USER));
        verify(mReceiver).onPasswordFailed(any(), actionEq(ACTION_PASSWORD_FAILED), eq(USER));
        verify(mReceiver).onPasswordFailed(any(), actionEq(ACTION_PASSWORD_FAILED));
    }

    @Presubmit
    public void testOnReceivePasswordSucceeded() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testOnReceivePasswordSucceeded");
        }
        mReceiver.onReceive(mContext, new Intent(DeviceAdminReceiver.ACTION_PASSWORD_SUCCEEDED)
                .putExtra(Intent.EXTRA_USER, USER));
        verify(mReceiver).onPasswordSucceeded(any(), actionEq(ACTION_PASSWORD_SUCCEEDED), eq(USER));
        verify(mReceiver).onPasswordSucceeded(any(), actionEq(ACTION_PASSWORD_SUCCEEDED));
    }

    @Presubmit
    public void testOnReceivePasswordExpiring() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testOnReceivePasswordExpiring");
        }
        mReceiver.onReceive(mContext, new Intent(DeviceAdminReceiver.ACTION_PASSWORD_EXPIRING)
                .putExtra(Intent.EXTRA_USER, USER));
        verify(mReceiver).onPasswordExpiring(any(), actionEq(ACTION_PASSWORD_EXPIRING), eq(USER));
        verify(mReceiver).onPasswordExpiring(any(), actionEq(ACTION_PASSWORD_EXPIRING));
    }

    @Presubmit
    public void testOnReceiveEnabled() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testOnReceiveEnabled");
            return;
        }
        mReceiver.onReceive(mContext, new Intent(DeviceAdminReceiver.ACTION_DEVICE_ADMIN_ENABLED));
        verify(mReceiver).onEnabled(
                any(), actionEq(DeviceAdminReceiver.ACTION_DEVICE_ADMIN_ENABLED));
    }

    @Presubmit
    public void testOnReceiveDisabled() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testOnReceiveDisabled");
            return;
        }
        mReceiver.onReceive(mContext, new Intent(DeviceAdminReceiver.ACTION_DEVICE_ADMIN_DISABLED));
        verify(mReceiver).onDisabled(
                any(), actionEq(DeviceAdminReceiver.ACTION_DEVICE_ADMIN_DISABLED));
    }

    @Presubmit
    public void testOnReceiveBugreportSharingDeclined() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testOnReceiveBugreportSharingDeclined");
            return;
        }
        mReceiver.onReceive(mContext, new Intent(ACTION_BUGREPORT_SHARING_DECLINED));
        verify(mReceiver).onBugreportSharingDeclined(
                any(), actionEq(ACTION_BUGREPORT_SHARING_DECLINED));
    }

    @Presubmit
    public void testOnReceiveBugreportFailed() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testOnReceiveBugreportFailed");
            return;
        }
        Intent bugreportFailedIntent = new Intent(ACTION_BUGREPORT_FAILED);
        bugreportFailedIntent.putExtra(EXTRA_BUGREPORT_FAILURE_REASON,
                DeviceAdminReceiver.BUGREPORT_FAILURE_FAILED_COMPLETING);
        mReceiver.onReceive(mContext, bugreportFailedIntent);
        verify(mReceiver).onBugreportFailed(any(), actionEq(ACTION_BUGREPORT_FAILED),
                eq(DeviceAdminReceiver.BUGREPORT_FAILURE_FAILED_COMPLETING));
    }

    @Presubmit
    public void testOnReceiveBugreportShared() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testOnReceiveBugreportShared");
            return;
        }
        Intent bugreportSharedIntent = new Intent(ACTION_BUGREPORT_SHARE);
        bugreportSharedIntent.putExtra(EXTRA_BUGREPORT_HASH, BUGREPORT_HASH);
        mReceiver.onReceive(mContext, bugreportSharedIntent);
        verify(mReceiver).onBugreportShared(
                any(), actionEq(ACTION_BUGREPORT_SHARE), eq(BUGREPORT_HASH));
    }

    @Presubmit
    public void testOnReceiveSecurityLogsAvailable() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testOnReceiveSecurityLogsAvailable");
            return;
        }
        mReceiver.onReceive(mContext, new Intent(ACTION_SECURITY_LOGS_AVAILABLE));
        verify(mReceiver).onSecurityLogsAvailable(any(), actionEq(ACTION_SECURITY_LOGS_AVAILABLE));
    }

    @Presubmit
    public void testOnReceiveNetworkLogsAvailable() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testOnReceiveNetworkLogsAvailable");
            return;
        }
        Intent networkLogsAvailableIntent = new Intent(ACTION_NETWORK_LOGS_AVAILABLE);
        networkLogsAvailableIntent.putExtra(EXTRA_NETWORK_LOGS_TOKEN, NETWORK_LOGS_TOKEN);
        networkLogsAvailableIntent.putExtra(EXTRA_NETWORK_LOGS_COUNT, NETWORK_LOGS_COUNT);
        mReceiver.onReceive(mContext, networkLogsAvailableIntent);
        verify(mReceiver).onNetworkLogsAvailable(any(), actionEq(ACTION_NETWORK_LOGS_AVAILABLE),
                eq(NETWORK_LOGS_TOKEN), eq(NETWORK_LOGS_COUNT));
    }

    // TODO: replace with inline argThat(x → e.equals(x.getAction())) when mockito is updated.
    private Intent actionEq(final String expected) {
        return argThat(new ArgumentMatcher<Intent>() {
            @Override
            public boolean matchesObject(Object argument) {
                return expected.equals(((Intent) argument).getAction());
            }
        });
    }
}
