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

package com.android.managedprovisioning.task.nonrequiredapps;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.support.test.filters.SmallTest;

import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link NonRequiredAppsLogic}.
 */
@SmallTest
public class NonRequiredAppsLogicTest {
    private static final String TEST_DPC_PACKAGE_NAME = "dpc.package.name";
    private static final int TEST_USER_ID = 123;
    private static final String[] APPS = {
            "app.a", "app.b", "app.c", "app.d", "app.e", "app.f", "app.g", "app.h",
    };
    private static final int[] SNAPSHOT_APPS = {0, 1, 2, 3};
    private static final int[] SYSTEM_APPS = {0, 1, 4, 5};
    private static final int[] BLACKLIST_APPS = {0, 2, 4, 6};

    @Mock private PackageManager mPackageManager;
    @Mock private IPackageManager mIPackageManager;
    @Mock private SystemAppsSnapshot mSnapshot;
    @Mock private OverlayPackagesProvider mProvider;
    @Mock private Utils mUtils;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testGetSystemAppsToRemove_NewLeave() throws Exception {
        // GIVEN that a new profile is being created and that system apps should not be deleted
        final NonRequiredAppsLogic logic = createLogic(true, true);
        // GIVEN that a combination of apps is present
        initializeApps();

        // THEN getSystemAppsToRemove should be empty
        assertTrue(logic.getSystemAppsToRemove(TEST_USER_ID).isEmpty());
    }

    @Test
    public void testGetSystemAppsToRemove_NewDelete() throws Exception {
        // GIVEN that a new profile is being created and that system apps should be deleted
        final NonRequiredAppsLogic logic = createLogic(true, false);
        // GIVEN that a combination of apps is present
        initializeApps();

        // THEN getSystemAppsToRemove should return a non-empty list with the only app to removed
        // being the one that is a current system app and non required
        assertEquals(getSetFromArray(new int[] { 0, 4 }),
                logic.getSystemAppsToRemove(TEST_USER_ID));
    }

    @Test
    public void testGetSystemAppsToRemove_OtaLeave() throws Exception {
        // GIVEN that an OTA occurs and that system apps should not be deleted (indicated by the
        // fact that no snapshot currently exists)
        final NonRequiredAppsLogic logic = createLogic(false, false);
        // GIVEN that a combination of apps is present
        initializeApps();
        // GIVEN that no snapshot currently exists
        when(mSnapshot.hasSnapshot(TEST_USER_ID)).thenReturn(false);

        // THEN getSystemAppsToRemove should be empty
        assertTrue(logic.getSystemAppsToRemove(TEST_USER_ID).isEmpty());
    }

    @Test
    public void testGetSystemAppsToRemove_OtaDelete() throws Exception {
        // GIVEN that an OTA occurs and that system apps should be deleted (indicated by the fact
        // that a snapshot currently exists)
        final NonRequiredAppsLogic logic = createLogic(false, false);
        // GIVEN that a combination of apps is present
        initializeApps();

        // THEN getSystemAppsToRemove should return a non-empty list with the only app to removed
        // being the one that is a current system app, non required and not in the last
        // snapshot.
        assertEquals(Collections.singleton(APPS[4]), logic.getSystemAppsToRemove(TEST_USER_ID));
    }

    @Test
    public void testMaybeTakeSnapshot_NewLeave() {
        // GIVEN that a new profile is being created and that system apps should not be deleted
        final NonRequiredAppsLogic logic = createLogic(true, true);

        // WHEN calling maybeTakeSystemAppsSnapshot
        logic.maybeTakeSystemAppsSnapshot(TEST_USER_ID);

        // THEN no snapshot should be taken
        verify(mSnapshot, never()).takeNewSnapshot(anyInt());
    }

    @Test
    public void testMaybeTakeSnapshot_NewDelete() {
        // GIVEN that a new profile is being created and that system apps should be deleted
        final NonRequiredAppsLogic logic = createLogic(true, false);

        // WHEN calling maybeTakeSystemAppsSnapshot
        logic.maybeTakeSystemAppsSnapshot(TEST_USER_ID);

        // THEN a snapshot should be taken
        verify(mSnapshot).takeNewSnapshot(TEST_USER_ID);
    }

    @Test
    public void testMaybeTakeSnapshot_OtaLeave() {
        // GIVEN that an OTA occurs and that system apps should not be deleted (indicated by the
        // fact that no snapshot currently exists)
        final NonRequiredAppsLogic logic = createLogic(false, false);
        when(mSnapshot.hasSnapshot(TEST_USER_ID)).thenReturn(false);

        // WHEN calling maybeTakeSystemAppsSnapshot
        logic.maybeTakeSystemAppsSnapshot(TEST_USER_ID);

        // THEN no snapshot should be taken
        verify(mSnapshot, never()).takeNewSnapshot(anyInt());
    }

    @Test
    public void testMaybeTakeSnapshot_OtaDelete() {
        // GIVEN that an OTA occurs and that system apps should be deleted (indicated by the fact
        // that a snapshot currently exists)
        final NonRequiredAppsLogic logic = createLogic(false, false);
        when(mSnapshot.hasSnapshot(TEST_USER_ID)).thenReturn(true);

        // WHEN calling maybeTakeSystemAppsSnapshot
        logic.maybeTakeSystemAppsSnapshot(TEST_USER_ID);

        // THEN a snapshot should be taken
        verify(mSnapshot).takeNewSnapshot(TEST_USER_ID);
    }

    private void initializeApps() throws Exception {
        setCurrentSystemApps(getSetFromArray(SYSTEM_APPS));
        setLastSnapshot(getSetFromArray(SNAPSHOT_APPS));
        setNonRequiredApps(getSetFromArray(BLACKLIST_APPS));
    }

    private void setCurrentSystemApps(Set<String> set) {
        when(mUtils.getCurrentSystemApps(mIPackageManager, TEST_USER_ID)).thenReturn(set);
    }

    private void setLastSnapshot(Set<String> set) {
        when(mSnapshot.getSnapshot(TEST_USER_ID)).thenReturn(set);
        when(mSnapshot.hasSnapshot(TEST_USER_ID)).thenReturn(true);
    }

    private void setNonRequiredApps(Set<String> set) {
        when(mProvider.getNonRequiredApps(TEST_USER_ID)).thenReturn(set);
    }

    private Set<String> getSetFromArray(int[] ids) {
        Set<String> set = new HashSet<>(ids.length);
        for (int id : ids) {
            set.add(APPS[id]);
        }
        return set;
    }

    private NonRequiredAppsLogic createLogic(boolean newProfile, boolean leaveSystemAppsEnabled) {
        ProvisioningParams params = new ProvisioningParams.Builder()
                .setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE)
                .setDeviceAdminPackageName(TEST_DPC_PACKAGE_NAME)
                .setLeaveAllSystemAppsEnabled(leaveSystemAppsEnabled)
                .build();
        return new NonRequiredAppsLogic(
                mPackageManager,
                mIPackageManager,
                newProfile,
                params,
                mSnapshot,
                mProvider,
                mUtils);
    }
}
