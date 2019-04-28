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

package com.android.managedprovisioning.finalization;

import static android.app.admin.DeviceAdminReceiver.ACTION_PROFILE_PROVISIONING_COMPLETE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISIONING_SUCCESSFUL;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE;
import static com.android.managedprovisioning.TestUtils.createTestAdminExtras;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.managedprovisioning.TestUtils;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link FinalizationController}.
 */
public class FinalizationControllerTest extends AndroidTestCase {
    private static final UserHandle MANAGED_PROFILE_USER_HANDLE = UserHandle.of(123);
    private static final String TEST_MDM_PACKAGE_NAME = "mdm.package.name";
    private static final String TEST_MDM_ADMIN_RECEIVER = TEST_MDM_PACKAGE_NAME + ".AdminReceiver";
    private static final ComponentName TEST_MDM_ADMIN = new ComponentName(TEST_MDM_PACKAGE_NAME,
            TEST_MDM_ADMIN_RECEIVER);
    private static final PersistableBundle TEST_MDM_EXTRA_BUNDLE = createTestAdminExtras();

    @Mock private Context mContext;
    @Mock private Utils mUtils;
    @Mock private SettingsFacade mSettingsFacade;
    @Mock private UserProvisioningStateHelper mHelper;

    private FinalizationController mController;

    @Override
    public void setUp() throws Exception {
        // this is necessary for mockito to work
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().toString());
        MockitoAnnotations.initMocks(this);
        when(mUtils.findDeviceAdmin(null, TEST_MDM_ADMIN, mContext))
                .thenReturn(TEST_MDM_ADMIN);
        when(mUtils.canResolveIntentAsUser(any(Context.class), any(Intent.class), anyInt()))
                .thenReturn(true);
        when(mContext.getFilesDir()).thenReturn(getContext().getFilesDir());

        mController = new FinalizationController(mContext, mUtils, mSettingsFacade, mHelper);
    }

    @Override
    public void tearDown() throws Exception {
        mController.loadProvisioningParamsAndClearFile();
    }

    @SmallTest
    public void testInitiallyDone_alreadyCalled() {
        // GIVEN that provisioningInitiallyDone has already been called
        when(mHelper.isStateUnmanagedOrFinalized()).thenReturn(false);
        final ProvisioningParams params = createProvisioningParams(
                ACTION_PROVISION_MANAGED_PROFILE);

        // WHEN calling provisioningInitiallyDone
        mController.provisioningInitiallyDone(params);

        // THEN nothing should happen
        verify(mHelper, never()).markUserProvisioningStateInitiallyDone(params);
        verify(mHelper, never()).markUserProvisioningStateFinalized(params);
    }

    @SmallTest
    public void testFinalized_alreadyCalled() {
        // GIVEN that provisioningInitiallyDone has already been called
        when(mHelper.isStateUnmanagedOrFinalized()).thenReturn(true);
        final ProvisioningParams params = createProvisioningParams(
                ACTION_PROVISION_MANAGED_PROFILE);

        // WHEN calling provisioningFinalized
        mController.provisioningFinalized();

        // THEN nothing should happen
        verify(mHelper, never()).markUserProvisioningStateInitiallyDone(params);
        verify(mHelper, never()).markUserProvisioningStateFinalized(params);
    }

    @SmallTest
    public void testFinalized_noParamsStored() {
        // GIVEN that the user provisioning state is correct
        when(mHelper.isStateUnmanagedOrFinalized()).thenReturn(false);

        // WHEN calling provisioningFinalized
        mController.provisioningFinalized();

        // THEN nothing should happen
        verify(mHelper, never())
                .markUserProvisioningStateInitiallyDone(any(ProvisioningParams.class));
        verify(mHelper, never()).markUserProvisioningStateFinalized(any(ProvisioningParams.class));
    }

    @SmallTest
    public void testManagedProfileAfterSuw() {
        // GIVEN that provisioningInitiallyDone has never been called
        when(mHelper.isStateUnmanagedOrFinalized()).thenReturn(true);
        // GIVEN that we've provisioned a managed profile after SUW
        final ProvisioningParams params = createProvisioningParams(
                ACTION_PROVISION_MANAGED_PROFILE);
        when(mSettingsFacade.isUserSetupCompleted(mContext)).thenReturn(true);
        when(mUtils.getManagedProfile(mContext))
                .thenReturn(MANAGED_PROFILE_USER_HANDLE);

        // WHEN calling provisioningInitiallyDone
        mController.provisioningInitiallyDone(params);

        // THEN the user provisioning state should be marked as initially done
        verify(mHelper).markUserProvisioningStateInitiallyDone(params);

        // THEN provisioning successful intent should be sent to the dpc.
        verifyDpcLaunchedForUser(MANAGED_PROFILE_USER_HANDLE);

        // THEN an ordered broadcast should be sent to the DPC
        verifyOrderedBroadcast();
    }

    @SmallTest
    public void testManagedProfileDuringSuw() {
        // GIVEN that provisioningInitiallyDone has never been called
        when(mHelper.isStateUnmanagedOrFinalized()).thenReturn(true);
        // GIVEN that we've provisioned a managed profile after SUW
        final ProvisioningParams params = createProvisioningParams(
                ACTION_PROVISION_MANAGED_PROFILE);
        when(mSettingsFacade.isUserSetupCompleted(mContext)).thenReturn(false);
        when(mUtils.getManagedProfile(mContext))
                .thenReturn(MANAGED_PROFILE_USER_HANDLE);

        // WHEN calling provisioningInitiallyDone
        mController.provisioningInitiallyDone(params);

        // THEN the user provisioning state should be marked as initially done
        verify(mHelper).markUserProvisioningStateInitiallyDone(params);
        // THEN the provisioning params have been stored and will be read in provisioningFinalized

        // GIVEN that the provisioning state is now incomplete
        when(mHelper.isStateUnmanagedOrFinalized()).thenReturn(false);

        // WHEN calling provisioningFinalized
        mController.provisioningFinalized();

        // THEN the user provisioning state is finalized
        verify(mHelper).markUserProvisioningStateFinalized(params);

        // THEN provisioning successful intent should be sent to the dpc.
        verifyDpcLaunchedForUser(MANAGED_PROFILE_USER_HANDLE);

        // THEN an ordered broadcast should be sent to the DPC
        verifyOrderedBroadcast();
    }

    @SmallTest
    public void testDeviceOwner() {
        // GIVEN that provisioningInitiallyDone has never been called
        when(mHelper.isStateUnmanagedOrFinalized()).thenReturn(true);
        // GIVEN that we've provisioned a managed profile after SUW
        final ProvisioningParams params = createProvisioningParams(
                ACTION_PROVISION_MANAGED_DEVICE);
        when(mSettingsFacade.isUserSetupCompleted(mContext)).thenReturn(false);

        // WHEN calling provisioningInitiallyDone
        mController.provisioningInitiallyDone(params);

        // THEN the user provisioning state should be marked as initially done
        verify(mHelper).markUserProvisioningStateInitiallyDone(params);
        // THEN the provisioning params have been stored and will be read in provisioningFinalized

        // GIVEN that the provisioning state is now incomplete
        when(mHelper.isStateUnmanagedOrFinalized()).thenReturn(false);

        // WHEN calling provisioningFinalized
        mController.provisioningFinalized();

        // THEN the user provisioning state is finalized
        verify(mHelper).markUserProvisioningStateFinalized(params);

        // THEN provisioning successful intent should be sent to the dpc.
        verifyDpcLaunchedForUser(UserHandle.of(UserHandle.myUserId()));

        // THEN a broadcast was sent to the primary user
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).sendBroadcast(intentCaptor.capture());

        // THEN the intent should be ACTION_PROFILE_PROVISIONING_COMPLETE
        assertEquals(ACTION_PROFILE_PROVISIONING_COMPLETE, intentCaptor.getValue().getAction());
        // THEN the intent should be sent to the admin receiver
        assertEquals(TEST_MDM_ADMIN, intentCaptor.getValue().getComponent());
        // THEN the admin extras bundle should contain mdm extras
        assertExtras(intentCaptor.getValue());
    }

    private void verifyOrderedBroadcast() {
        // THEN an ordered broadcast should be sent to the DPC
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).sendOrderedBroadcastAsUser(
                intentCaptor.capture(),
                eq(MANAGED_PROFILE_USER_HANDLE),
                eq(null),
                any(BroadcastReceiver.class),
                eq(null),
                eq(Activity.RESULT_OK),
                eq(null),
                eq(null));
        // THEN the intent should be ACTION_PROFILE_PROVISIONING_COMPLETE
        assertEquals(ACTION_PROFILE_PROVISIONING_COMPLETE, intentCaptor.getValue().getAction());
        // THEN the intent should be sent to the admin receiver
        assertEquals(TEST_MDM_ADMIN, intentCaptor.getValue().getComponent());
        // THEN the admin extras bundle should contain mdm extras
        assertExtras(intentCaptor.getValue());
    }

    private void verifyDpcLaunchedForUser(UserHandle userHandle) {
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).startActivityAsUser(intentCaptor.capture(), eq(userHandle));
        // THEN the intent should be ACTION_PROVISIONING_SUCCESSFUL
        assertEquals(ACTION_PROVISIONING_SUCCESSFUL, intentCaptor.getValue().getAction());
        // THEN the intent should only be sent to the dpc
        assertEquals(TEST_MDM_PACKAGE_NAME, intentCaptor.getValue().getPackage());
        // THEN the admin extras bundle should contain mdm extras
        assertExtras(intentCaptor.getValue());
    }

    private void assertExtras(Intent intent) {
        TestUtils.bundleEquals(TEST_MDM_EXTRA_BUNDLE,
                (PersistableBundle) intent.getExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE));
    }

    private ProvisioningParams createProvisioningParams(String action) {
        return new ProvisioningParams.Builder()
                .setDeviceAdminComponentName(TEST_MDM_ADMIN)
                .setProvisioningAction(action)
                .setAdminExtrasBundle(TEST_MDM_EXTRA_BUNDLE)
                .build();
    }
}
