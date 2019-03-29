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

package android.app.cts;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

import android.app.stubs.R;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class WallpaperManagerTest {

    private static final boolean DEBUG = false;
    private static final String TAG = "WallpaperManagerTest";

    private WallpaperManager mWallpaperManager;
    private Context mContext;
    private Handler mHandler;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext = InstrumentationRegistry.getTargetContext();
        mWallpaperManager = WallpaperManager.getInstance(mContext);
        final HandlerThread handlerThread = new HandlerThread("TestCallbacks");
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper());
    }

    @Test
    public void setBitmapTest() {
        Bitmap tmpWallpaper = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(tmpWallpaper);
        canvas.drawColor(Color.RED);

        try {
            int which = WallpaperManager.FLAG_SYSTEM;
            mWallpaperManager.setBitmap(tmpWallpaper);
            int oldWallpaperId = mWallpaperManager.getWallpaperId(which);
            canvas.drawColor(Color.GREEN);
            mWallpaperManager.setBitmap(tmpWallpaper);
            int newWallpaperId = mWallpaperManager.getWallpaperId(which);
            Assert.assertNotEquals(oldWallpaperId, newWallpaperId);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            tmpWallpaper.recycle();
        }
    }

    @Test
    public void setResourceTest() {
        try {
            int which = WallpaperManager.FLAG_SYSTEM;
            int oldWallpaperId = mWallpaperManager.getWallpaperId(which);
            mWallpaperManager.setResource(R.drawable.robot);
            int newWallpaperId = mWallpaperManager.getWallpaperId(which);
            Assert.assertNotEquals(oldWallpaperId, newWallpaperId);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void wallpaperChangedBroadcastTest() {
        Bitmap tmpWallpaper = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(tmpWallpaper);
        canvas.drawColor(Color.BLACK);

        CountDownLatch latch = new CountDownLatch(1);
        mContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                latch.countDown();
            }
        }, new IntentFilter(Intent.ACTION_WALLPAPER_CHANGED));

        try {
            mWallpaperManager.setBitmap(tmpWallpaper);

            // Wait for up to 5 sec since this is an async call.
            // Should fail if Intent.ACTION_WALLPAPER_CHANGED isn't delivered.
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Intent.ACTION_WALLPAPER_CHANGED not received.");
            }
        } catch (InterruptedException | IOException e) {
            throw new AssertionError("Intent.ACTION_WALLPAPER_CHANGED not received.");
        } finally {
            tmpWallpaper.recycle();
        }
    }

    @Test
    public void wallpaperClearBroadcastTest() {
        CountDownLatch latch = new CountDownLatch(1);
        mContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                latch.countDown();
            }
        }, new IntentFilter(Intent.ACTION_WALLPAPER_CHANGED));

        try {
            mWallpaperManager.clear(WallpaperManager.FLAG_LOCK | WallpaperManager.FLAG_SYSTEM);

            // Wait for 5 sec since this is an async call.
            // Should fail if Intent.ACTION_WALLPAPER_CHANGED isn't delivered.
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Intent.ACTION_WALLPAPER_CHANGED not received.");
            }
        } catch (InterruptedException | IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void invokeOnColorsChangedListenerTest_systemOnly() {
        int both = WallpaperManager.FLAG_LOCK | WallpaperManager.FLAG_SYSTEM;
        // Expect both since the first step is to migrate the current wallpaper
        // to the lock screen.
        verifyColorListenerInvoked(WallpaperManager.FLAG_SYSTEM, both);
    }

    @Test
    public void invokeOnColorsChangedListenerTest_lockOnly() {
        verifyColorListenerInvoked(WallpaperManager.FLAG_LOCK, WallpaperManager.FLAG_LOCK);
    }

    @Test
    public void invokeOnColorsChangedListenerTest_both() {
        int both = WallpaperManager.FLAG_LOCK | WallpaperManager.FLAG_SYSTEM;
        verifyColorListenerInvoked(both, both);
    }

    @Test
    public void invokeOnColorsChangedListenerTest_clearLock() throws IOException {
        verifyColorListenerInvokedClearing(WallpaperManager.FLAG_LOCK);
    }

    @Test
    public void invokeOnColorsChangedListenerTest_clearSystem() throws IOException {
        verifyColorListenerInvokedClearing(WallpaperManager.FLAG_SYSTEM);
    }

    /**
     * Removing a listener should not invoke it anymore
     */
    @Test
    public void addRemoveOnColorsChangedListenerTest_onlyInvokeAdded() throws IOException {
        ensureCleanState();

        final CountDownLatch latch = new CountDownLatch(1);
        WallpaperManager.OnColorsChangedListener counter = (colors, whichWp) -> latch.countDown();

        // Add and remove listener
        WallpaperManager.OnColorsChangedListener listener = getTestableListener();
        mWallpaperManager.addOnColorsChangedListener(listener, mHandler);
        mWallpaperManager.removeOnColorsChangedListener(listener);

        // Verify that the listener is not called
        mWallpaperManager.addOnColorsChangedListener(counter, mHandler);
        try {
            mWallpaperManager.setResource(R.drawable.robot);
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Registered listener not invoked");
            }
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
        verify(listener, never()).onColorsChanged(any(WallpaperColors.class), anyInt());
        mWallpaperManager.removeOnColorsChangedListener(counter);
    }

    /**
     * Suggesting desired dimensions is only a hint to the system that can be ignored.
     *
     * Test if the desired minimum width or height the WallpaperManager returns
     * is greater than 0. If so, then we check whether that the size is at least the
     * as big as the screen.
     */
    @Test
    public void suggestDesiredDimensionsTest() {
        final Point min = getScreenSize();
        final int w = min.x * 3;
        final int h = min.y * 2;
        assertDesiredMinimum(new Point(min.x / 2, min.y / 2), min);

        assertDesiredMinimum(new Point(w, h), min);

        assertDesiredMinimum(new Point(min.x / 2, h), min);

        assertDesiredMinimum(new Point(w, min.y / 2), min);
    }

    private void assertDesiredMinimum(Point suggestedSize, Point minSize) {
        mWallpaperManager.suggestDesiredDimensions(suggestedSize.x, suggestedSize.y);
        Point actualSize = new Point(mWallpaperManager.getDesiredMinimumWidth(),
                mWallpaperManager.getDesiredMinimumHeight());
        if (actualSize.x > 0 || actualSize.y > 0) {
            if ((actualSize.x < minSize.x || actualSize.y < minSize.y)) {
                throw new AssertionError("Expected at least x: " + minSize.x + " y: "
                        + minSize.y + ", got x: " + actualSize.x +
                        " y: " + actualSize.y);
            }
        }
    }

    private Point getScreenSize() {
        WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        Display d = wm.getDefaultDisplay();
        Point p = new Point();
        d.getRealSize(p);
        return p;
    }

    /**
     * Helper to set a listener and verify if it was called with the same flags.
     * Executes operation synchronously.
     *
     * @param which FLAG_LOCK, FLAG_SYSTEM or a combination of both.
     */
    private void verifyColorListenerInvoked(int which, int whichExpected) {
        ensureCleanState();
        int expected = 0;
        if ((whichExpected & WallpaperManager.FLAG_LOCK) != 0) expected++;
        if ((whichExpected & WallpaperManager.FLAG_SYSTEM) != 0) expected++;
        ArrayList<Integer> received = new ArrayList<>();

        final CountDownLatch latch = new CountDownLatch(expected);
        Handler handler = new Handler(Looper.getMainLooper());

        WallpaperManager.OnColorsChangedListener listener = getTestableListener();
        WallpaperManager.OnColorsChangedListener counter = (colors, whichWp) -> {
            handler.post(()-> {
                received.add(whichWp);
                boolean ok = false;
                if ((whichWp & WallpaperManager.FLAG_LOCK) != 0 &&
                        (whichExpected & WallpaperManager.FLAG_LOCK) != 0) {
                    latch.countDown();
                    ok = true;
                }
                if ((whichWp & WallpaperManager.FLAG_SYSTEM) != 0 &&
                        (whichExpected & WallpaperManager.FLAG_SYSTEM) != 0) {
                    latch.countDown();
                    ok = true;
                }
                if (!ok) {
                    throw new AssertionError("Unexpected which flag: " + whichWp +
                            " should be: " + whichExpected);
                }
            });
        };

        mWallpaperManager.addOnColorsChangedListener(listener, mHandler);
        mWallpaperManager.addOnColorsChangedListener(counter, mHandler);

        try {
            mWallpaperManager.setResource(R.drawable.robot, which);
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Didn't receive all color events. Expected: " +
                        whichExpected + " received: " + received);
            }
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }

        mWallpaperManager.removeOnColorsChangedListener(listener);
        mWallpaperManager.removeOnColorsChangedListener(counter);
    }

    /**
     * Helper to clear a wallpaper synchronously.
     *
     * @param which FLAG_LOCK, FLAG_SYSTEM or a combination of both.
     */
    private void verifyColorListenerInvokedClearing(int which) {
        ensureCleanState();

        final CountDownLatch latch = new CountDownLatch(1);

        WallpaperManager.OnColorsChangedListener listener = getTestableListener();
        WallpaperManager.OnColorsChangedListener counter = (colors, whichWp) -> {
            latch.countDown();
        };

        mWallpaperManager.addOnColorsChangedListener(listener, mHandler);
        mWallpaperManager.addOnColorsChangedListener(counter, mHandler);

        try {
            mWallpaperManager.clear(which);
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }

        verify(listener, atLeast(1))
                .onColorsChanged(nullable(WallpaperColors.class), anyInt());

        mWallpaperManager.removeOnColorsChangedListener(listener);
        mWallpaperManager.removeOnColorsChangedListener(counter);
    }

    /**
     * Helper method to make sure a wallpaper is set for both FLAG_SYSTEM and FLAG_LOCK
     * and its callbacks were already called. Necessary to cleanup previous tests states.
     *
     * This is necessary to avoid race conditions between tests
     */
    private void ensureCleanState() {
        Bitmap bmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        // We expect 5 events to happen when we change a wallpaper:
        // • Wallpaper changed
        // • System colors are null
        // • Lock colors are null
        // • System colors are known
        // • Lock colors are known
        final int expectedEvents = 5;
        CountDownLatch latch = new CountDownLatch(expectedEvents);
        if (DEBUG) {
            Log.d("WP", "Started latch expecting: " + latch.getCount());
        }
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                latch.countDown();
                if (DEBUG) {
                    Log.d("WP", "broadcast state count down: " + latch.getCount());
                }
            }
        };
        WallpaperManager.OnColorsChangedListener callback = (colors, which) -> {
            if ((which & WallpaperManager.FLAG_LOCK) != 0) {
                latch.countDown();
            }
            if ((which & WallpaperManager.FLAG_SYSTEM) != 0) {
                latch.countDown();
            }
            if (DEBUG) {
                Log.d("WP", "color state count down: " + which + " - " + colors);
            }
        };
        mContext.registerReceiver(receiver, new IntentFilter(Intent.ACTION_WALLPAPER_CHANGED));
        mWallpaperManager.addOnColorsChangedListener(callback, mHandler);

        try {
            mWallpaperManager.setBitmap(bmp);

            // Wait for up to 10 sec since this is an async call.
            // Will pass as soon as the expected callbacks are executed.
            latch.await(10, TimeUnit.SECONDS);
            if (latch.getCount() != 0) {
                Log.w(TAG, "Did not receive all events! This is probably a bug.");
            }
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException("Can't ensure a clean state.");
        } finally {
            mContext.unregisterReceiver(receiver);
            mWallpaperManager.removeOnColorsChangedListener(callback);
            bmp.recycle();
        }
    }

    public WallpaperManager.OnColorsChangedListener getTestableListener() {
        // Unfortunately mockito cannot mock anonymous classes or lambdas.
        return spy(new TestableColorListener());
    }

    public class TestableColorListener implements WallpaperManager.OnColorsChangedListener {
        @Override
        public void onColorsChanged(WallpaperColors colors, int which) {
        }
    }
}