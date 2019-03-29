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
 * Tests the differences in behavior between tiles in TileService#TILE_MODE_PASSIVE
 * and TileService#TILE_MODE_ACTIVE.
 */
public class ActiveTileServiceTest extends BaseTileServiceTest {
    // Constants for generating commands below.
    private static final String SERVICE = "TestActiveTileService";

    private static final String ACTION_REQUEST_LISTENING =
            "android.sysui.testtile.REQUEST_LISTENING";

    private static final String REQUEST_LISTENING = "am broadcast -a " + ACTION_REQUEST_LISTENING
            + " " + PACKAGE;

    public ActiveTileServiceTest() {
        super(SERVICE);
    }

    public void testNotListening() throws Exception {
        if (!supported()) return;
        addTile();
        assertTrue(waitFor("onDestroy"));

        // Open quick settings and verify that this service doesn't get put in
        // a listening state since its an active tile.
        openSettings();
        assertFalse(waitFor("onStartListening"));
    }

    public void testRequestListening() throws Exception {
        if (!supported()) return;
        addTile();
        assertTrue(waitFor("onDestroy"));

        // Request the listening state and verify that it gets an onStartListening.
        getDevice().executeShellCommand(REQUEST_LISTENING);
        assertTrue(waitFor("requestListeningState"));
        assertTrue(waitFor("onStartListening"));
    }

    public void testClick() throws Exception {
        if (!supported()) return;
        addTile();
        assertTrue(waitFor("onDestroy"));

        // Open the quick settings.
        openSettings();

        // Click on the tile and verify it happens.
        clickTile();
        assertTrue(waitFor("onStartListening"));
        assertTrue(waitFor("onClick"));
    }

}
