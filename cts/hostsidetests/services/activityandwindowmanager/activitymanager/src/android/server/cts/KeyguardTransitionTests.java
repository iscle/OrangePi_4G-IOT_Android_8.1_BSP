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
 * limitations under the License
 */

package android.server.cts;

import static android.server.cts.WindowManagerState.TRANSIT_ACTIVITY_OPEN;
import static android.server.cts.WindowManagerState.TRANSIT_KEYGUARD_GOING_AWAY;
import static android.server.cts.WindowManagerState.TRANSIT_KEYGUARD_GOING_AWAY_ON_WALLPAPER;
import static android.server.cts.WindowManagerState.TRANSIT_KEYGUARD_OCCLUDE;
import static android.server.cts.WindowManagerState.TRANSIT_KEYGUARD_UNOCCLUDE;

/**
 * Build: mmma -j32 cts/hostsidetests/services
 * Run: cts/hostsidetests/services/activityandwindowmanager/util/run-test CtsServicesHostTestCases android.server.cts.KeyguardTransitionTests
 */
public class KeyguardTransitionTests extends ActivityManagerTestBase {

    @Override
    public void setUp() throws Exception {
        super.setUp();

        // Set screen lock (swipe)
        setLockDisabled(false);
    }

    public void testUnlock() throws Exception {
        if (!isHandheld()) {
            return;
        }
        launchActivity("TestActivity");
        gotoKeyguard();
        unlockDevice();
        mAmWmState.computeState(mDevice, new String[] { "TestActivity"} );
        assertEquals("Picked wrong transition", TRANSIT_KEYGUARD_GOING_AWAY,
                mAmWmState.getWmState().getLastTransition());
    }

    public void testUnlockWallpaper() throws Exception {
        if (!isHandheld()) {
            return;
        }
        launchActivity("WallpaperActivity");
        gotoKeyguard();
        unlockDevice();
        mAmWmState.computeState(mDevice, new String[] { "WallpaperActivity"} );
        assertEquals("Picked wrong transition", TRANSIT_KEYGUARD_GOING_AWAY_ON_WALLPAPER,
                mAmWmState.getWmState().getLastTransition());
    }

    public void testOcclude() throws Exception {
        if (!isHandheld()) {
            return;
        }
        gotoKeyguard();
        launchActivity("ShowWhenLockedActivity");
        mAmWmState.computeState(mDevice, new String[] { "ShowWhenLockedActivity"} );
        assertEquals("Picked wrong transition", TRANSIT_KEYGUARD_OCCLUDE,
                mAmWmState.getWmState().getLastTransition());
    }

    public void testUnocclude() throws Exception {
        if (!isHandheld()) {
            return;
        }
        launchActivity("ShowWhenLockedActivity");
        gotoKeyguard();
        launchActivity("TestActivity");
        mAmWmState.waitForKeyguardShowingAndNotOccluded(mDevice);
        mAmWmState.computeState(mDevice, null);
        assertEquals("Picked wrong transition", TRANSIT_KEYGUARD_UNOCCLUDE,
                mAmWmState.getWmState().getLastTransition());
    }

    public void testNewActivityDuringOccluded() throws Exception {
        if (!isHandheld()) {
            return;
        }
        launchActivity("ShowWhenLockedActivity");
        gotoKeyguard();
        launchActivity("ShowWhenLockedWithDialogActivity");
        mAmWmState.computeState(mDevice, new String[] { "ShowWhenLockedWithDialogActivity" });
        assertEquals("Picked wrong transition", TRANSIT_ACTIVITY_OPEN,
                mAmWmState.getWmState().getLastTransition());
    }

    public void testOccludeManifestAttr() throws Exception {
         if (!isHandheld()) {
             return;
         }

         String activityName = "ShowWhenLockedAttrActivity";

         gotoKeyguard();
         final String logSeparator = clearLogcat();
         launchActivity(activityName);
         mAmWmState.computeState(mDevice, new String[] {activityName});
         assertEquals("Picked wrong transition", TRANSIT_KEYGUARD_OCCLUDE,
                 mAmWmState.getWmState().getLastTransition());
         assertSingleLaunch(activityName, logSeparator);
    }

    public void testOccludeAttrRemove() throws Exception {
        if (!isHandheld()) {
            return;
        }

        String activityName = "ShowWhenLockedAttrRemoveAttrActivity";

        gotoKeyguard();
        String logSeparator = clearLogcat();
        launchActivity(activityName);
        mAmWmState.computeState(mDevice, new String[] {activityName});
        assertEquals("Picked wrong transition", TRANSIT_KEYGUARD_OCCLUDE,
                mAmWmState.getWmState().getLastTransition());
        assertSingleLaunch(activityName, logSeparator);

        gotoKeyguard();
        logSeparator = clearLogcat();
        launchActivity(activityName);
        mAmWmState.computeState(mDevice, new String[] {activityName});
        assertSingleStartAndStop(activityName, logSeparator);
    }

    public void testNewActivityDuringOccludedWithAttr() throws Exception {
        if (!isHandheld()) {
            return;
        }

        String activityName1 = "ShowWhenLockedAttrActivity";
        String activityName2 = "ShowWhenLockedAttrWithDialogActivity";

        launchActivity(activityName1);
        gotoKeyguard();
        launchActivity(activityName2);
        mAmWmState.computeState(mDevice, new String[] { activityName2 });
        assertEquals("Picked wrong transition", TRANSIT_ACTIVITY_OPEN,
                mAmWmState.getWmState().getLastTransition());
    }

}
