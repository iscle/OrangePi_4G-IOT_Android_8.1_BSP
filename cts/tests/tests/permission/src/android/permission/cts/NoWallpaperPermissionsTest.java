/*
 * Copyright (C) 2017 The Android Open Source Project
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


package android.permission.cts;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.PowerManager;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static android.app.WallpaperManager.FLAG_SYSTEM;
import static android.app.WallpaperManager.FLAG_LOCK;

/**
 * Verify that Wallpaper-related operations enforce the correct permissions.
 */
public class NoWallpaperPermissionsTest extends AndroidTestCase {
    private WallpaperManager mWM;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mWM = (WallpaperManager) mContext.getSystemService(Context.WALLPAPER_SERVICE);
    }

    /**
     * Verify that the setResource(...) methods enforce the SET_WALLPAPER permission
     */
    @SmallTest
    public void testSetResource() throws IOException {
        if (wallpaperNotSupported()) {
            return;
        }

        try {
            mWM.setResource(R.drawable.robot);
            fail("WallpaperManager.setResource(id) did not enforce SET_WALLPAPER");
        } catch (SecurityException expected) { /* expected */ }

        try {
            mWM.setResource(R.drawable.robot, FLAG_LOCK);
            fail("WallpaperManager.setResource(id, which) did not enforce SET_WALLPAPER");
        } catch (SecurityException expected) { /* expected */ }
    }

    /**
     * Verify that the setBitmap(...) methods enforce the SET_WALLPAPER permission
     */
    @SmallTest
    public void testSetBitmap() throws IOException  {
        if (wallpaperNotSupported()) {
            return;
        }

        Bitmap b = Bitmap.createBitmap(160, 120, Bitmap.Config.RGB_565);

        try {
            mWM.setBitmap(b);
            fail("setBitmap(b) did not enforce SET_WALLPAPER");
        } catch (SecurityException expected) { /* expected */ }

        try {
            mWM.setBitmap(b, null, false);
            fail("setBitmap(b, crop, allowBackup) did not enforce SET_WALLPAPER");
        } catch (SecurityException expected) { /* expected */ }

        try {
            mWM.setBitmap(b, null, false, FLAG_SYSTEM);
            fail("setBitmap(b, crop, allowBackup, which) did not enforce SET_WALLPAPER");
        } catch (SecurityException expected) { /* expected */ }
    }

    /**
     * Verify that the setStream(...) methods enforce the SET_WALLPAPER permission
     */
    @SmallTest
    public void testSetStream() throws IOException  {
        if (wallpaperNotSupported()) {
            return;
        }

        ByteArrayInputStream stream = new ByteArrayInputStream(new byte[32]);

        try {
            mWM.setStream(stream);
            fail("setStream(stream) did not enforce SET_WALLPAPER");
        } catch (SecurityException expected) { /* expected */ }

        try {
            mWM.setStream(stream, null, false);
            fail("setStream(stream, crop, allowBackup) did not enforce SET_WALLPAPER");
        } catch (SecurityException expected) { /* expected */ }

        try {
            mWM.setStream(stream, null, false, FLAG_LOCK);
            fail("setStream(stream, crop, allowBackup, which) did not enforce SET_WALLPAPER");
        } catch (SecurityException expected) { /* expected */ }
    }

    /**
     * Verify that the clearWallpaper(...) methods enforce the SET_WALLPAPER permission
     */
    @SmallTest
    public void testClearWallpaper() throws IOException  {
        if (wallpaperNotSupported()) {
            return;
        }

        try {
            mWM.clear();
            fail("clear() did not enforce SET_WALLPAPER");
        } catch (SecurityException expected) { /* expected */ }

        try {
            mWM.clear(FLAG_SYSTEM);
            fail("clear(which) did not enforce SET_WALLPAPER");
        } catch (SecurityException expected) { /* expected */ }
    }

    /**
     * Verify that reading the current wallpaper requires READ_EXTERNAL_STORAGE
     */
    @SmallTest
    public void testReadWallpaper() {
        if (wallpaperNotSupported()) {
            return;
        }

        try {
            /* ignore result */ mWM.getFastDrawable();
            fail("getFastDrawable() did not enforce READ_EXTERNAL_STORAGE");
        } catch (SecurityException expected) { /* expected */ }

        try {
            /* ignore result */ mWM.peekFastDrawable();
            fail("peekFastDrawable() did not enforce READ_EXTERNAL_STORAGE");
        } catch (SecurityException expected) { /* expected */ }

        try {
            /* ignore result */ mWM.getWallpaperFile(FLAG_SYSTEM);
            fail("getWallpaperFile(FLAG_SYSTEM) did not enforce READ_EXTERNAL_STORAGE");
        } catch (SecurityException expected) { /* expected */ }
    }

    // ---------- Utility methods ----------

    private boolean wallpaperNotSupported() {
        return !(mWM.isWallpaperSupported() && mWM.isSetWallpaperAllowed());
    }
}
