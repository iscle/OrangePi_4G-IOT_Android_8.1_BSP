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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.IPackageManager;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;

import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.task.nonrequiredapps.SystemAppsSnapshot;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit-tests for {@link SystemAppsSnapshot}.
 */
@SmallTest
public class SystemAppsSnapshotTest {
    private static final String TEST_PACKAGE_NAME_1 = "com.test.packagea";
    private static final String TEST_PACKAGE_NAME_2 = "com.test.packageb";
    private static final int TEST_USER_ID = 123;

    @Mock private IPackageManager mockIPackageManager;
    @Mock private Context mContext;
    @Mock private Utils mUtils;
    private SystemAppsSnapshot mSystemAppsSnapshot;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mContext.getFilesDir())
                .thenReturn(InstrumentationRegistry.getTargetContext().getFilesDir());

        mSystemAppsSnapshot = new SystemAppsSnapshot(mContext, mockIPackageManager, mUtils);
    }

    @After
    public void tearDown() {
        File systemAppsFile = SystemAppsSnapshot.getSystemAppsFile(
                InstrumentationRegistry.getTargetContext(), TEST_USER_ID);
        if (systemAppsFile.exists()) {
            systemAppsFile.delete();
        }
    }

    @Test
    public void testHasSnapshot() throws Exception {
        // GIVEN a number of installed system apps
        setCurrentSystemApps(TEST_PACKAGE_NAME_1, TEST_PACKAGE_NAME_2);

        // THEN hasSnapshot should return false before the first snapshot is taken
        assertFalse(mSystemAppsSnapshot.hasSnapshot(TEST_USER_ID));

        // WHEN taking a snapshot
        mSystemAppsSnapshot.takeNewSnapshot(TEST_USER_ID);

        // THEN hasSnapshot should return true
        assertTrue(mSystemAppsSnapshot.hasSnapshot(TEST_USER_ID));
    }

    @Test
    public void testGetSnapshot() throws Exception {
        // GIVEN a number of installed system apps
        setCurrentSystemApps(TEST_PACKAGE_NAME_1, TEST_PACKAGE_NAME_2);

        // THEN getSnapshot should return an empty set
        assertTrue(mSystemAppsSnapshot.getSnapshot(TEST_USER_ID).isEmpty());

        // WHEN taking a snapshot
        mSystemAppsSnapshot.takeNewSnapshot(TEST_USER_ID);

        // THEN hasSnapshot should return true
        assertSetEquals(mSystemAppsSnapshot.getSnapshot(TEST_USER_ID),
                TEST_PACKAGE_NAME_1, TEST_PACKAGE_NAME_2);
    }

    private void setCurrentSystemApps(String... packages) throws Exception {
        when(mUtils.getCurrentSystemApps(mockIPackageManager, TEST_USER_ID))
                .thenReturn(new HashSet<>(Arrays.asList(packages)));
    }

    private void assertSetEquals(Collection<String> result, String... expected) {
        assertEquals(expected.length, result.size());
        for (String name : expected) {
            assertTrue(result.contains(name));
        }
    }
}
