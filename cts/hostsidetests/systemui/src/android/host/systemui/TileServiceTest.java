/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package android.host.systemui;

/**
 * Tests the lifecycle of a TileService by adding/removing it and opening/closing
 * the notification/settings shade through adb commands.
 */
public class TileServiceTest extends BaseTileServiceTest {

    private static final String SERVICE = "TestTileService";

    public static final String ACTION_START_ACTIVITY =
            "android.sysui.testtile.action.START_ACTIVITY";

    public static final String START_ACTIVITY_AND_COLLAPSE =
            "am broadcast -a " + ACTION_START_ACTIVITY;

    public TileServiceTest() {
        super(SERVICE);
    }

    public void testAddTile() throws Exception {
        if (!supported()) return;
        addTile();
        // Verify that the service starts up and gets a onTileAdded callback.
        assertTrue(waitFor("onCreate"));
        assertTrue(waitFor("onTileAdded"));
        assertTrue(waitFor("onDestroy"));
    }

    public void testRemoveTile() throws Exception {
        if (!supported()) return;
        addTile();
        // Verify that the service starts up and gets a onTileAdded callback.
        assertTrue(waitFor("onCreate"));
        assertTrue(waitFor("onTileAdded"));
        assertTrue(waitFor("onDestroy"));

        remTile();
        assertTrue(waitFor("onTileRemoved"));
    }

    public void testListeningNotifications() throws Exception {
        if (!supported()) return;
        addTile();
        assertTrue(waitFor("onDestroy"));

        // Open the notification shade and make sure the tile gets a chance to listen.
        openNotifications();
        assertTrue(waitFor("onStartListening"));
        // Collapse the shade and make sure the listening ends.
        collapse();
        assertTrue(waitFor("onStopListening"));
    }

    public void testListeningSettings() throws Exception {
        if (!supported()) return;
        addTile();
        assertTrue(waitFor("onDestroy"));

        // Open the quick settings and make sure the tile gets a chance to listen.
        openSettings();
        assertTrue(waitFor("onStartListening"));
        // Collapse the shade and make sure the listening ends.
        collapse();
        assertTrue(waitFor("onStopListening"));
    }

    public void testCantAddDialog() throws Exception {
        if (!supported()) return;
        addTile();
        assertTrue(waitFor("onDestroy"));

        // Wait for the tile to be added.
        assertTrue(waitFor("onTileAdded"));

        // Open the quick settings and make sure the tile gets a chance to listen.
        openSettings();
        assertTrue(waitFor("onStartListening"));

        // Try to open a dialog, verify it doesn't happen.
        showDialog();
        assertTrue(waitFor("handleShowDialog"));
        assertTrue(waitFor("onWindowAddFailed"));
    }

    public void testClick() throws Exception {
        if (!supported()) return;
        addTile();
        // Wait for the tile to be added.
        assertTrue(waitFor("onTileAdded"));

        // Open the quick settings and make sure the tile gets a chance to listen.
        openSettings();
        assertTrue(waitFor("onStartListening"));

        // Click on the tile and verify it happens.
        clickTile();
        assertTrue(waitFor("onClick"));

        // Verify the state that gets dumped during a click.
        // Device is expected to be unlocked and unsecure during CTS.
        // The unlock callback should be triggered immediately.
        assertTrue(waitFor("is_secure_false"));
        assertTrue(waitFor("is_locked_false"));
        assertTrue(waitFor("unlockAndRunRun"));
    }

    public void testClickAndShowDialog() throws Exception {
        if (!supported()) return;
        addTile();
        assertTrue(waitFor("onDestroy"));

        // Open the quick settings and make sure the tile gets a chance to listen.
        openSettings();
        assertTrue(waitFor("onStartListening"));

        // Click on the tile and verify it happens.
        clickTile();
        assertTrue(waitFor("onClick"));

        // Try to open a dialog, verify it doesn't happen.
        showDialog();
        assertTrue(waitFor("handleShowDialog"));
        assertTrue(waitFor("onWindowFocusChanged_true"));
    }

    public void testStartActivity() throws Exception {
        if (!supported()) return;
        addTile();
        // Wait for the tile to be added.
        assertTrue(waitFor("onTileAdded"));

        // Open the quick settings and make sure the tile gets a chance to listen.
        openSettings();
        assertTrue(waitFor("onStartListening"));

        // Trigger the startActivityAndCollapse call and verify we get taken out of listening
        // because the shade gets collapsed.
        getDevice().executeShellCommand(START_ACTIVITY_AND_COLLAPSE);
        assertTrue(waitFor("onStopListening"));
    }

}
