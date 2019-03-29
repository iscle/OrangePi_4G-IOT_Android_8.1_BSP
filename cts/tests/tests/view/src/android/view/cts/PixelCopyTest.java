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

package android.view.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.Instrumentation;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.os.Debug;
import android.os.Debug.MemoryInfo;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.Half;
import android.util.Log;
import android.view.PixelCopy;
import android.view.Surface;
import android.view.View;
import android.view.Window;

import com.android.compatibility.common.util.SynchronousPixelCopy;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.model.Statement;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class PixelCopyTest {
    private static final String TAG = "PixelCopyTests";

    @Rule
    public ActivityTestRule<PixelCopyGLProducerCtsActivity> mGLSurfaceViewActivityRule =
            new ActivityTestRule<>(PixelCopyGLProducerCtsActivity.class, false, false);

    @Rule
    public ActivityTestRule<PixelCopyVideoSourceActivity> mVideoSourceActivityRule =
            new ActivityTestRule<>(PixelCopyVideoSourceActivity.class, false, false);

    @Rule
    public ActivityTestRule<PixelCopyViewProducerActivity> mWindowSourceActivityRule =
            new ActivityTestRule<>(PixelCopyViewProducerActivity.class, false, false);

    @Rule
    public ActivityTestRule<PixelCopyWideGamutViewProducerActivity>
            mWideGamutWindowSourceActivityRule = new ActivityTestRule<>(
                    PixelCopyWideGamutViewProducerActivity.class, false, false);

    @Rule
    public SurfaceTextureRule mSurfaceRule = new SurfaceTextureRule();

    private Instrumentation mInstrumentation;
    private SynchronousPixelCopy mCopyHelper;

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        assertNotNull(mInstrumentation);
        mCopyHelper = new SynchronousPixelCopy();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullDest() {
        Bitmap dest = null;
        mCopyHelper.request(mSurfaceRule.getSurface(), dest);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRecycledDest() {
        Bitmap dest = Bitmap.createBitmap(5, 5, Config.ARGB_8888);
        dest.recycle();
        mCopyHelper.request(mSurfaceRule.getSurface(), dest);
    }

    @Test
    public void testNoSourceData() {
        Bitmap dest = Bitmap.createBitmap(5, 5, Bitmap.Config.ARGB_8888);
        int result = mCopyHelper.request(mSurfaceRule.getSurface(), dest);
        assertEquals(PixelCopy.ERROR_SOURCE_NO_DATA, result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptySourceRectSurface() {
        Bitmap dest = Bitmap.createBitmap(5, 5, Bitmap.Config.ARGB_8888);
        mCopyHelper.request(mSurfaceRule.getSurface(), new Rect(), dest);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptySourceRectWindow() {
        Bitmap dest = Bitmap.createBitmap(5, 5, Bitmap.Config.ARGB_8888);
        mCopyHelper.request(mock(Window.class), new Rect(), dest);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidSourceRectSurface() {
        Bitmap dest = Bitmap.createBitmap(5, 5, Bitmap.Config.ARGB_8888);
        mCopyHelper.request(mSurfaceRule.getSurface(), new Rect(10, 10, 0, 0), dest);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidSourceRectWindow() {
        Bitmap dest = Bitmap.createBitmap(5, 5, Bitmap.Config.ARGB_8888);
        mCopyHelper.request(mock(Window.class), new Rect(10, 10, 0, 0), dest);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNoDecorView() {
        Bitmap dest = Bitmap.createBitmap(5, 5, Bitmap.Config.ARGB_8888);
        Window mockWindow = mock(Window.class);
        mCopyHelper.request(mockWindow, dest);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNoViewRoot() {
        Bitmap dest = Bitmap.createBitmap(5, 5, Bitmap.Config.ARGB_8888);
        Window mockWindow = mock(Window.class);
        View view = new View(mInstrumentation.getTargetContext());
        when(mockWindow.peekDecorView()).thenReturn(view);
        mCopyHelper.request(mockWindow, dest);
    }

    private PixelCopyGLProducerCtsActivity waitForGlProducerActivity() {
        CountDownLatch swapFence = new CountDownLatch(2);

        PixelCopyGLProducerCtsActivity activity =
                mGLSurfaceViewActivityRule.launchActivity(null);
        activity.setSwapFence(swapFence);

        try {
            while (!swapFence.await(5, TimeUnit.MILLISECONDS)) {
                activity.getView().requestRender();
            }
        } catch (InterruptedException ex) {
            fail("Interrupted, error=" + ex.getMessage());
        }
        return activity;
    }

    @Test
    public void testGlProducerFullsize() {
        PixelCopyGLProducerCtsActivity activity = waitForGlProducerActivity();
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        int result = mCopyHelper.request(activity.getView(), bitmap);
        assertEquals("Fullsize copy request failed", PixelCopy.SUCCESS, result);
        assertEquals(100, bitmap.getWidth());
        assertEquals(100, bitmap.getHeight());
        assertEquals(Config.ARGB_8888, bitmap.getConfig());
        assertBitmapQuadColor(bitmap,
                Color.RED, Color.GREEN, Color.BLUE, Color.BLACK);
    }

    @Test
    public void testGlProducerCropTopLeft() {
        PixelCopyGLProducerCtsActivity activity = waitForGlProducerActivity();
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        int result = mCopyHelper.request(activity.getView(), new Rect(0, 0, 50, 50), bitmap);
        assertEquals("Scaled copy request failed", PixelCopy.SUCCESS, result);
        assertBitmapQuadColor(bitmap,
                Color.RED, Color.RED, Color.RED, Color.RED);
    }

    @Test
    public void testGlProducerCropCenter() {
        PixelCopyGLProducerCtsActivity activity = waitForGlProducerActivity();
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        int result = mCopyHelper.request(activity.getView(), new Rect(25, 25, 75, 75), bitmap);
        assertEquals("Scaled copy request failed", PixelCopy.SUCCESS, result);
        assertBitmapQuadColor(bitmap,
                Color.RED, Color.GREEN, Color.BLUE, Color.BLACK);
    }

    @Test
    public void testGlProducerCropBottomHalf() {
        PixelCopyGLProducerCtsActivity activity = waitForGlProducerActivity();
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        int result = mCopyHelper.request(activity.getView(), new Rect(0, 50, 100, 100), bitmap);
        assertEquals("Scaled copy request failed", PixelCopy.SUCCESS, result);
        assertBitmapQuadColor(bitmap,
                Color.BLUE, Color.BLACK, Color.BLUE, Color.BLACK);
    }

    @Test
    public void testGlProducerCropClamping() {
        PixelCopyGLProducerCtsActivity activity = waitForGlProducerActivity();
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        int result = mCopyHelper.request(activity.getView(), new Rect(50, -50, 150, 50), bitmap);
        assertEquals("Scaled copy request failed", PixelCopy.SUCCESS, result);
        assertBitmapQuadColor(bitmap,
                Color.GREEN, Color.GREEN, Color.GREEN, Color.GREEN);
    }

    @Test
    public void testGlProducerScaling() {
        // Since we only sample mid-pixel of each qudrant, filtering
        // quality isn't tested
        PixelCopyGLProducerCtsActivity activity = waitForGlProducerActivity();
        Bitmap bitmap = Bitmap.createBitmap(20, 20, Config.ARGB_8888);
        int result = mCopyHelper.request(activity.getView(), bitmap);
        assertEquals("Scaled copy request failed", PixelCopy.SUCCESS, result);
        // Make sure nothing messed with the bitmap
        assertEquals(20, bitmap.getWidth());
        assertEquals(20, bitmap.getHeight());
        assertEquals(Config.ARGB_8888, bitmap.getConfig());
        assertBitmapQuadColor(bitmap,
                Color.RED, Color.GREEN, Color.BLUE, Color.BLACK);
    }

    @Test
    public void testReuseBitmap() {
        // Since we only sample mid-pixel of each qudrant, filtering
        // quality isn't tested
        PixelCopyGLProducerCtsActivity activity = waitForGlProducerActivity();
        Bitmap bitmap = Bitmap.createBitmap(20, 20, Config.ARGB_8888);
        int result = mCopyHelper.request(activity.getView(), bitmap);
        // Make sure nothing messed with the bitmap
        assertEquals(20, bitmap.getWidth());
        assertEquals(20, bitmap.getHeight());
        assertEquals(Config.ARGB_8888, bitmap.getConfig());
        assertBitmapQuadColor(bitmap,
                Color.RED, Color.GREEN, Color.BLUE, Color.BLACK);
        int generationId = bitmap.getGenerationId();
        result = mCopyHelper.request(activity.getView(), bitmap);
        // Make sure nothing messed with the bitmap
        assertEquals(20, bitmap.getWidth());
        assertEquals(20, bitmap.getHeight());
        assertEquals(Config.ARGB_8888, bitmap.getConfig());
        assertBitmapQuadColor(bitmap,
                Color.RED, Color.GREEN, Color.BLUE, Color.BLACK);
        assertNotEquals(generationId, bitmap.getGenerationId());
    }

    private Window waitForWindowProducerActivity() {
        PixelCopyViewProducerActivity activity =
                mWindowSourceActivityRule.launchActivity(null);
        activity.waitForFirstDrawCompleted(3, TimeUnit.SECONDS);
        return activity.getWindow();
    }

    private Rect makeWindowRect(int left, int top, int right, int bottom) {
        Rect r = new Rect(left, top, right, bottom);
        mWindowSourceActivityRule.getActivity().normalizedToSurface(r);
        return r;
    }

    @Test
    public void testWindowProducer() {
        Bitmap bitmap;
        Window window = waitForWindowProducerActivity();
        PixelCopyViewProducerActivity activity = mWindowSourceActivityRule.getActivity();
        do {
            Rect src = makeWindowRect(0, 0, 100, 100);
            bitmap = Bitmap.createBitmap(src.width(), src.height(), Config.ARGB_8888);
            int result = mCopyHelper.request(window, src, bitmap);
            assertEquals("Fullsize copy request failed", PixelCopy.SUCCESS, result);
            assertEquals(Config.ARGB_8888, bitmap.getConfig());
            assertBitmapQuadColor(bitmap,
                    Color.RED, Color.GREEN, Color.BLUE, Color.BLACK);
            assertBitmapEdgeColor(bitmap, Color.YELLOW);
        } while (activity.rotate());
    }

    @Test
    public void testWindowProducerCropTopLeft() {
        Window window = waitForWindowProducerActivity();
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        PixelCopyViewProducerActivity activity = mWindowSourceActivityRule.getActivity();
        do {
            int result = mCopyHelper.request(window, makeWindowRect(0, 0, 50, 50), bitmap);
            assertEquals("Scaled copy request failed", PixelCopy.SUCCESS, result);
            assertBitmapQuadColor(bitmap,
                    Color.RED, Color.RED, Color.RED, Color.RED);
        } while (activity.rotate());
    }

    @Test
    public void testWindowProducerCropCenter() {
        Window window = waitForWindowProducerActivity();
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        PixelCopyViewProducerActivity activity = mWindowSourceActivityRule.getActivity();
        do {
            int result = mCopyHelper.request(window, makeWindowRect(25, 25, 75, 75), bitmap);
            assertEquals("Scaled copy request failed", PixelCopy.SUCCESS, result);
            assertBitmapQuadColor(bitmap,
                    Color.RED, Color.GREEN, Color.BLUE, Color.BLACK);
        } while (activity.rotate());
    }

    @Test
    public void testWindowProducerCropBottomHalf() {
        Window window = waitForWindowProducerActivity();
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        PixelCopyViewProducerActivity activity = mWindowSourceActivityRule.getActivity();
        do {
            int result = mCopyHelper.request(window, makeWindowRect(0, 50, 100, 100), bitmap);
            assertEquals("Scaled copy request failed", PixelCopy.SUCCESS, result);
            assertBitmapQuadColor(bitmap,
                    Color.BLUE, Color.BLACK, Color.BLUE, Color.BLACK);
        } while (activity.rotate());
    }

    @Test
    public void testWindowProducerScaling() {
        // Since we only sample mid-pixel of each qudrant, filtering
        // quality isn't tested
        Window window = waitForWindowProducerActivity();
        Bitmap bitmap = Bitmap.createBitmap(20, 20, Config.ARGB_8888);
        PixelCopyViewProducerActivity activity = mWindowSourceActivityRule.getActivity();
        do {
            int result = mCopyHelper.request(window, bitmap);
            assertEquals("Scaled copy request failed", PixelCopy.SUCCESS, result);
            // Make sure nothing messed with the bitmap
            assertEquals(20, bitmap.getWidth());
            assertEquals(20, bitmap.getHeight());
            assertEquals(Config.ARGB_8888, bitmap.getConfig());
            assertBitmapQuadColor(bitmap,
                    Color.RED, Color.GREEN, Color.BLUE, Color.BLACK);
        } while (activity.rotate());
    }

    @Test
    public void testWindowProducerCopyToRGBA16F() {
        Window window = waitForWindowProducerActivity();
        PixelCopyViewProducerActivity activity = mWindowSourceActivityRule.getActivity();

        Bitmap bitmap;
        do {
            Rect src = makeWindowRect(0, 0, 100, 100);
            bitmap = Bitmap.createBitmap(src.width(), src.height(), Config.RGBA_F16);
            int result = mCopyHelper.request(window, src, bitmap);
            // On OpenGL ES 2.0 devices a copy to RGBA_F16 can fail because there's
            // not support for float textures
            if (result != PixelCopy.ERROR_DESTINATION_INVALID) {
                assertEquals("Fullsize copy request failed", PixelCopy.SUCCESS, result);
                assertEquals(Config.RGBA_F16, bitmap.getConfig());
                assertBitmapQuadColor(bitmap,
                        Color.RED, Color.GREEN, Color.BLUE, Color.BLACK);
                assertBitmapEdgeColor(bitmap, Color.YELLOW);
            }
        } while (activity.rotate());
    }

    private Window waitForWideGamutWindowProducerActivity() {
        PixelCopyWideGamutViewProducerActivity activity =
                mWideGamutWindowSourceActivityRule.launchActivity(null);
        activity.waitForFirstDrawCompleted(3, TimeUnit.SECONDS);
        return activity.getWindow();
    }

    private Rect makeWideGamutWindowRect(int left, int top, int right, int bottom) {
        Rect r = new Rect(left, top, right, bottom);
        mWideGamutWindowSourceActivityRule.getActivity().offsetForContent(r);
        return r;
    }

    @Test
    public void testWideGamutWindowProducerCopyToRGBA8888() {
        Window window = waitForWideGamutWindowProducerActivity();
        assertEquals(
                ActivityInfo.COLOR_MODE_WIDE_COLOR_GAMUT, window.getAttributes().getColorMode());

        // Early out if the device does not support wide color gamut rendering
        if (!window.isWideColorGamut()) {
            return;
        }

        PixelCopyWideGamutViewProducerActivity activity =
                mWideGamutWindowSourceActivityRule.getActivity();

        Bitmap bitmap;
        do {
            Rect src = makeWideGamutWindowRect(0, 0, 128, 128);
            bitmap = Bitmap.createBitmap(src.width(), src.height(), Config.ARGB_8888);
            int result = mCopyHelper.request(window, src, bitmap);

            assertEquals("Fullsize copy request failed", PixelCopy.SUCCESS, result);
            assertEquals(Config.ARGB_8888, bitmap.getConfig());

            assertEquals("Top left", Color.RED, bitmap.getPixel(32, 32));
            assertEquals("Top right", Color.GREEN, bitmap.getPixel(96, 32));
            assertEquals("Bottom left", Color.BLUE, bitmap.getPixel(32, 96));
            assertEquals("Bottom right", Color.YELLOW, bitmap.getPixel(96, 96));
        } while (activity.rotate());
    }

    @Test
    public void testWideGamutWindowProducerCopyToRGBA16F() {
        Window window = waitForWideGamutWindowProducerActivity();
        assertEquals(
                ActivityInfo.COLOR_MODE_WIDE_COLOR_GAMUT, window.getAttributes().getColorMode());

        // Early out if the device does not support wide color gamut rendering
        if (!window.isWideColorGamut()) {
            return;
        }

        PixelCopyWideGamutViewProducerActivity activity =
                mWideGamutWindowSourceActivityRule.getActivity();

        Bitmap bitmap;
        int i = 0;
        do {
            Rect src = makeWideGamutWindowRect(0, 0, 128, 128);
            bitmap = Bitmap.createBitmap(src.width(), src.height(), Config.RGBA_F16);
            int result = mCopyHelper.request(window, src, bitmap);

            assertEquals("Fullsize copy request failed", PixelCopy.SUCCESS, result);
            assertEquals(Config.RGBA_F16, bitmap.getConfig());

            ByteBuffer dst = ByteBuffer.allocateDirect(bitmap.getAllocationByteCount());
            bitmap.copyPixelsToBuffer(dst);
            dst.rewind();
            dst.order(ByteOrder.LITTLE_ENDIAN);

            // ProPhoto RGB red in scRGB-nl
            assertEqualsRgba16f("Top left",     bitmap, 32, 32, dst,  1.36f, -0.52f, -0.09f, 1.0f);
            // ProPhoto RGB green in scRGB-nl
            assertEqualsRgba16f("Top right",    bitmap, 96, 32, dst, -0.87f,  1.10f, -0.43f, 1.0f);
            // ProPhoto RGB blue in scRGB-nl
            assertEqualsRgba16f("Bottom left",  bitmap, 32, 96, dst, -0.59f, -0.04f,  1.07f, 1.0f);
            // ProPhoto RGB yellow in scRGB-nl
            assertEqualsRgba16f("Bottom right", bitmap, 96, 96, dst,  1.12f,  1.00f, -0.44f, 1.0f);
        } while (activity.rotate());
    }

    private static void assertEqualsRgba16f(String message, Bitmap bitmap, int x, int y,
            ByteBuffer dst, float r, float g, float b, float a) {
        int index = y * bitmap.getRowBytes() + (x << 3);
        short cR = dst.getShort(index);
        short cG = dst.getShort(index + 2);
        short cB = dst.getShort(index + 4);
        short cA = dst.getShort(index + 6);

        assertEquals(message, r, Half.toFloat(cR), 0.01);
        assertEquals(message, g, Half.toFloat(cG), 0.01);
        assertEquals(message, b, Half.toFloat(cB), 0.01);
        assertEquals(message, a, Half.toFloat(cA), 0.01);
    }

    private void runGcAndFinalizersSync() {
        final CountDownLatch fence = new CountDownLatch(1);
        new Object() {
            @Override
            protected void finalize() throws Throwable {
                try {
                    fence.countDown();
                } finally {
                    super.finalize();
                }
            }
        };
        try {
            do {
                Runtime.getRuntime().gc();
                Runtime.getRuntime().runFinalization();
            } while (!fence.await(100, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
        Runtime.getRuntime().gc();
    }

    private void assertNotLeaking(int iteration, MemoryInfo start, MemoryInfo end) {
        Debug.getMemoryInfo(end);
        if (end.getTotalPss() - start.getTotalPss() > 2000 /* kB */) {
            runGcAndFinalizersSync();
            Debug.getMemoryInfo(end);
            if (end.getTotalPss() - start.getTotalPss() > 2000 /* kB */) {
                // Guarded by if so we don't continually generate garbage for the
                // assertion string.
                assertEquals("Memory leaked, iteration=" + iteration,
                        start.getTotalPss(), end.getTotalPss(),
                        2000 /* kb */);
            }
        }
    }

    @Test
    @LargeTest
    public void testNotLeaking() {
        try {
            CountDownLatch swapFence = new CountDownLatch(2);

            PixelCopyGLProducerCtsActivity activity =
                    mGLSurfaceViewActivityRule.launchActivity(null);
            activity.setSwapFence(swapFence);

            while (!swapFence.await(5, TimeUnit.MILLISECONDS)) {
                activity.getView().requestRender();
            }

            // Test a fullsize copy
            Bitmap bitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);

            MemoryInfo meminfoStart = new MemoryInfo();
            MemoryInfo meminfoEnd = new MemoryInfo();

            for (int i = 0; i < 1000; i++) {
                if (i == 2) {
                    // Not really the "start" but by having done a couple
                    // we've fully initialized any state that may be required,
                    // so memory usage should be stable now
                    runGcAndFinalizersSync();
                    Debug.getMemoryInfo(meminfoStart);
                }
                if (i % 100 == 5) {
                    assertNotLeaking(i, meminfoStart, meminfoEnd);
                }
                int result = mCopyHelper.request(activity.getView(), bitmap);
                assertEquals("Copy request failed", PixelCopy.SUCCESS, result);
                // Make sure nothing messed with the bitmap
                assertEquals(100, bitmap.getWidth());
                assertEquals(100, bitmap.getHeight());
                assertEquals(Config.ARGB_8888, bitmap.getConfig());
                assertBitmapQuadColor(bitmap,
                        Color.RED, Color.GREEN, Color.BLUE, Color.BLACK);
            }

            assertNotLeaking(1000, meminfoStart, meminfoEnd);

        } catch (InterruptedException e) {
            fail("Interrupted, error=" + e.getMessage());
        }
    }

    @Test
    public void testVideoProducer() throws InterruptedException {
        PixelCopyVideoSourceActivity activity =
                mVideoSourceActivityRule.launchActivity(null);
        if (!activity.canPlayVideo()) {
            Log.i(TAG, "Skipping testVideoProducer, video codec isn't supported");
            return;
        }
        // This returns when the video has been prepared and playback has
        // been started, it doesn't necessarily means a frame has actually been
        // produced. There sadly isn't a callback for that.
        // So we'll try for up to 900ms after this event to acquire a frame, otherwise
        // it's considered a timeout.
        activity.waitForPlaying();
        assertTrue("Failed to start video playback", activity.canPlayVideo());
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        int copyResult = PixelCopy.ERROR_SOURCE_NO_DATA;
        for (int i = 0; i < 30; i++) {
            copyResult = mCopyHelper.request(activity.getVideoView(), bitmap);
            if (copyResult != PixelCopy.ERROR_SOURCE_NO_DATA) {
                break;
            }
            Thread.sleep(30);
        }
        assertEquals(PixelCopy.SUCCESS, copyResult);
        // A large threshold is used because decoder accuracy is covered in the
        // media CTS tests, so we are mainly interested in verifying that rotation
        // and YUV->RGB conversion were handled properly.
        assertBitmapQuadColor(bitmap, Color.RED, Color.GREEN, Color.BLUE, Color.BLACK, 30);

        // Test that cropping works.
        copyResult = mCopyHelper.request(activity.getVideoView(), new Rect(0, 0, 50, 50), bitmap);
        assertEquals("Scaled copy request failed", PixelCopy.SUCCESS, copyResult);
        assertBitmapQuadColor(bitmap,
                Color.RED, Color.RED, Color.RED, Color.RED, 30);

        copyResult = mCopyHelper.request(activity.getVideoView(), new Rect(25, 25, 75, 75), bitmap);
        assertEquals("Scaled copy request failed", PixelCopy.SUCCESS, copyResult);
        assertBitmapQuadColor(bitmap,
                Color.RED, Color.GREEN, Color.BLUE, Color.BLACK, 30);

        copyResult = mCopyHelper.request(activity.getVideoView(), new Rect(0, 50, 100, 100), bitmap);
        assertEquals("Scaled copy request failed", PixelCopy.SUCCESS, copyResult);
        assertBitmapQuadColor(bitmap,
                Color.BLUE, Color.BLACK, Color.BLUE, Color.BLACK, 30);

        // Test that clamping works
        copyResult = mCopyHelper.request(activity.getVideoView(), new Rect(50, -50, 150, 50), bitmap);
        assertEquals("Scaled copy request failed", PixelCopy.SUCCESS, copyResult);
        assertBitmapQuadColor(bitmap,
                Color.GREEN, Color.GREEN, Color.GREEN, Color.GREEN, 30);
    }

    private static int getPixelFloatPos(Bitmap bitmap, float xpos, float ypos) {
        return bitmap.getPixel((int) (bitmap.getWidth() * xpos), (int) (bitmap.getHeight() * ypos));
    }

    private void assertBitmapQuadColor(Bitmap bitmap, int topLeft, int topRight,
                int bottomLeft, int bottomRight) {
        // Just quickly sample 4 pixels in the various regions.
        assertEquals("Top left " + Integer.toHexString(topLeft) + ", actual= "
                + Integer.toHexString(getPixelFloatPos(bitmap, .25f, .25f)),
                topLeft, getPixelFloatPos(bitmap, .25f, .25f));
        assertEquals("Top right", topRight, getPixelFloatPos(bitmap, .75f, .25f));
        assertEquals("Bottom left", bottomLeft, getPixelFloatPos(bitmap, .25f, .75f));
        assertEquals("Bottom right", bottomRight, getPixelFloatPos(bitmap, .75f, .75f));
    }

    private void assertBitmapQuadColor(Bitmap bitmap, int topLeft, int topRight,
            int bottomLeft, int bottomRight, int threshold) {
        // Just quickly sample 4 pixels in the various regions.
        assertTrue("Top left", pixelsAreSame(topLeft, getPixelFloatPos(bitmap, .25f, .25f),
                threshold));
        assertTrue("Top right", pixelsAreSame(topRight, getPixelFloatPos(bitmap, .75f, .25f),
                threshold));
        assertTrue("Bottom left", pixelsAreSame(bottomLeft, getPixelFloatPos(bitmap, .25f, .75f),
                threshold));
        assertTrue("Bottom right", pixelsAreSame(bottomRight, getPixelFloatPos(bitmap, .75f, .75f),
                threshold));
    }

    private void assertBitmapEdgeColor(Bitmap bitmap, int edgeColor) {
        // Just quickly sample a few pixels on the edge and assert
        // they are edge color, then assert that just inside the edge is a different color
        assertBitmapColor("Top edge", bitmap, edgeColor, bitmap.getWidth() / 2, 0);
        assertBitmapNotColor("Top edge", bitmap, edgeColor, bitmap.getWidth() / 2, 1);

        assertBitmapColor("Left edge", bitmap, edgeColor, 0, bitmap.getHeight() / 2);
        assertBitmapNotColor("Left edge", bitmap, edgeColor, 1, bitmap.getHeight() / 2);

        assertBitmapColor("Bottom edge", bitmap, edgeColor,
                bitmap.getWidth() / 2, bitmap.getHeight() - 1);
        assertBitmapNotColor("Bottom edge", bitmap, edgeColor,
                bitmap.getWidth() / 2, bitmap.getHeight() - 2);

        assertBitmapColor("Right edge", bitmap, edgeColor,
                bitmap.getWidth() - 1, bitmap.getHeight() / 2);
        assertBitmapNotColor("Right edge", bitmap, edgeColor,
                bitmap.getWidth() - 2, bitmap.getHeight() / 2);
    }

    private boolean pixelsAreSame(int ideal, int given, int threshold) {
        int error = Math.abs(Color.red(ideal) - Color.red(given));
        error += Math.abs(Color.green(ideal) - Color.green(given));
        error += Math.abs(Color.blue(ideal) - Color.blue(given));
        return (error < threshold);
    }

    private void assertBitmapColor(String debug, Bitmap bitmap, int color, int x, int y) {
        int pixel = bitmap.getPixel(x, y);
        if (!pixelsAreSame(color, pixel, 10)) {
            fail(debug + "; expected=" + Integer.toHexString(color) + ", actual="
                    + Integer.toHexString(pixel));
        }
    }

    private void assertBitmapNotColor(String debug, Bitmap bitmap, int color, int x, int y) {
        int pixel = bitmap.getPixel(x, y);
        if (pixelsAreSame(color, pixel, 10)) {
            fail(debug + "; actual=" + Integer.toHexString(pixel)
                    + " shouldn't have matched " + Integer.toHexString(color));
        }
    }

    private static class SurfaceTextureRule implements TestRule {
        private SurfaceTexture mSurfaceTexture = null;
        private Surface mSurface = null;

        private void createIfNecessary() {
            mSurfaceTexture = new SurfaceTexture(false);
            mSurface = new Surface(mSurfaceTexture);
        }

        public Surface getSurface() {
            createIfNecessary();
            return mSurface;
        }

        @Override
        public Statement apply(Statement base, Description description) {
            return new CreateSurfaceTextureStatement(base);
        }

        private class CreateSurfaceTextureStatement extends Statement {

            private final Statement mBase;

            public CreateSurfaceTextureStatement(Statement base) {
                mBase = base;
            }

            @Override
            public void evaluate() throws Throwable {
                try {
                    mBase.evaluate();
                } finally {
                    try {
                        if (mSurface != null) mSurface.release();
                    } catch (Throwable t) {}
                    try {
                        if (mSurfaceTexture != null) mSurfaceTexture.release();
                    } catch (Throwable t) {}
                }
            }
        }
    }
}
