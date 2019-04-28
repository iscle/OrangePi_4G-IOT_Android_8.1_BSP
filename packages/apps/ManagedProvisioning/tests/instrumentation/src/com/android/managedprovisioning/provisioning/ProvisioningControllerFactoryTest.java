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

package com.android.managedprovisioning.provisioning;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;

import com.android.managedprovisioning.model.ProvisioningParams;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link ProvisioningControllerFactory}.
 */
@SmallTest
public class ProvisioningControllerFactoryTest {
    private static final ComponentName ADMIN = new ComponentName("com.test.admin", ".Receiver");
    private static final ProvisioningParams PROFILE_OWNER_PARAMS = new ProvisioningParams.Builder()
            .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
            .setDeviceAdminComponentName(ADMIN)
            .build();
    private static final ProvisioningParams DEVICE_OWNER_PARAMS = new ProvisioningParams.Builder()
            .setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE)
            .setDeviceAdminComponentName(ADMIN)
            .build();

    private ProvisioningControllerFactory mFactory = new ProvisioningControllerFactory();
    @Mock private ProvisioningControllerCallback mCallback;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testProfileOwner() {
        // WHEN calling the factory with a set of profile owner params
        AbstractProvisioningController controller =
                mFactory.createProvisioningController(InstrumentationRegistry.getTargetContext(),
                        PROFILE_OWNER_PARAMS, mCallback);

        // THEN the controller should be a profile owner controller
        assertTrue(controller instanceof ProfileOwnerProvisioningController);
    }

    @Test
    public void testDeviceOwner() {
        // WHEN calling the factory with a set of device owner params
        AbstractProvisioningController controller =
                mFactory.createProvisioningController(InstrumentationRegistry.getTargetContext(),
                        DEVICE_OWNER_PARAMS, mCallback);

        // THEN the controller should be a device owner controller
        assertTrue(controller instanceof DeviceOwnerProvisioningController);
    }
}
