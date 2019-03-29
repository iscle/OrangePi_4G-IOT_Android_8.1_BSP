/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.os.health.cts;

import android.content.Context;
import android.os.Parcel;
import android.os.Process;
import android.os.health.SystemHealthManager;
import android.os.health.HealthStats;
import android.test.suitebuilder.annotation.SmallTest;
import android.test.InstrumentationTestCase;

import junit.framework.Assert;

/**
 * Provides test cases for android.os.health.TimerStat.
 */
public class SystemHealthManagerTest extends InstrumentationTestCase {
    /**
     * Tests that takeMyUidSnapshot returns a HealthStats object.
     */
    @SmallTest
    public void testTakeMyUidSnapshot() throws Exception {
        final Context context = getInstrumentation().getTargetContext();
        final SystemHealthManager healthy = SystemHealthManager.from(context);

        Assert.assertNotNull(healthy.takeMyUidSnapshot());
    }

    /**
     * Tests that takeUidSnapshot with this uid returns a HealthStats object.
     */
    @SmallTest
    public void testTakeUidSnapshotWithMe() throws Exception {
        final Context context = getInstrumentation().getTargetContext();
        final SystemHealthManager healthy = SystemHealthManager.from(context);

        Assert.assertNotNull(healthy.takeUidSnapshot(Process.myUid()));
    }

    /**
     * Tests that takeUidSnapshot on the system process throws a SecurityException.
     */
    @SmallTest
    public void testTakeMyUidSnapshotWithSystem() throws Exception {
        final Context context = getInstrumentation().getTargetContext();
        final SystemHealthManager healthy = SystemHealthManager.from(context);

        boolean threw = false;
        try {
            healthy.takeUidSnapshot(Process.SYSTEM_UID);
        } catch (SecurityException ex) {
            threw = true;
        }

        Assert.assertTrue(threw);
    }

    /**
     * Tests that takeUidSnapshots with an empty array returns an empty array.
     */
    @SmallTest
    public void testTakeUidSnapshotsWithEmptyArray() throws Exception {
        final Context context = getInstrumentation().getTargetContext();
        final SystemHealthManager healthy = SystemHealthManager.from(context);

        final HealthStats[] result = healthy.takeUidSnapshots(new int[0]);
        Assert.assertEquals(0, result.length);
    }

    /**
     * Tests that takeUidSnapshots with this uid returns a HealthStats object.
     */
    @SmallTest
    public void testTakeUidSnapshotsWithMe() throws Exception {
        final Context context = getInstrumentation().getTargetContext();
        final SystemHealthManager healthy = SystemHealthManager.from(context);

        final HealthStats[] result = healthy.takeUidSnapshots(new int[] {
                    Process.myUid(),
                    Process.myUid(),
                });
        Assert.assertEquals(2, result.length);
        Assert.assertNotNull(result[0]);
        Assert.assertNotNull(result[1]);
    }

    /**
     * Tests that takeUidSnapshot on the system process throws a SecurityException.
     */
    @SmallTest
    public void testTakeMyUidSnapshotsWithSystem() throws Exception {
        final Context context = getInstrumentation().getTargetContext();
        final SystemHealthManager healthy = SystemHealthManager.from(context);

        boolean threw = false;
        try {
            healthy.takeUidSnapshots(new int[] {
                        Process.myUid(),
                        Process.SYSTEM_UID,
                    });
        } catch (SecurityException ex) {
            threw = true;
        }

        Assert.assertTrue(threw);
    }
}

