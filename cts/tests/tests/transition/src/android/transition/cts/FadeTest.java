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
package android.transition.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.animation.Animator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.transition.Fade;
import android.transition.Transition;
import android.transition.TransitionListenerAdapter;
import android.transition.TransitionManager;
import android.transition.TransitionValues;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * This tests the public API for Fade. The alpha cannot be easily tested as part of CTS,
 * so those are implementation tests.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class FadeTest extends BaseTransitionTest {
    private static final int WINDOW_SIZE = 10;
    public static final double CONSTANT_L = 254;
    public static final double CONSTANT_K1 = 0.00001;
    public static final double CONSTANT_K2 = 0.00003;
    public static final double CONSTANT_C1 = Math.pow(CONSTANT_L * CONSTANT_K1, 2);
    public static final double CONSTANT_C2 = Math.pow(CONSTANT_L * CONSTANT_K2, 2);
    Fade mFade;

    @Override
    @Before
    public void setup() {
        super.setup();
        resetTransition();
    }

    private void resetTransition() {
        mFade = new Fade();
        mFade.setDuration(200);
        mTransition = mFade;
        resetListener();
    }

    @Test
    public void testMode() throws Throwable {
        // Should animate in and out by default
        enterScene(R.layout.scene4);
        startTransition(R.layout.scene1);
        verify(mListener, never()).onTransitionEnd(any());
        waitForEnd(400);

        resetListener();
        startTransition(R.layout.scene4);
        verify(mListener, never()).onTransitionEnd(any());
        waitForEnd(400);

        // Now only animate in
        mFade = new Fade(Fade.IN);
        mTransition = mFade;
        resetListener();
        startTransition(R.layout.scene1);
        verify(mListener, never()).onTransitionEnd(any());
        waitForEnd(400);

        // No animation since it should only animate in
        resetListener();
        startTransition(R.layout.scene4);
        waitForEnd(0);

        // Now animate out, but no animation should happen since we're animating in.
        mFade = new Fade(Fade.OUT);
        mTransition = mFade;
        resetListener();
        startTransition(R.layout.scene1);
        waitForEnd(0);

        // but it should animate out
        resetListener();
        startTransition(R.layout.scene4);
        verify(mListener, never()).onTransitionEnd(any());
        waitForEnd(400);
    }

    @Test
    public void testFadeOut() throws Throwable {
        enterScene(R.layout.scene4);
    }

    @Test
    public void testFadeOutTransition() throws Throwable {
        enterScene(R.layout.scene1);

        final View redSquare = mActivity.findViewById(R.id.redSquare);
        final Bitmap redSquareBitmap = createViewBitmap(redSquare);
        final FrameLayout container = new FrameLayout(mActivity);
        final ViewGroup sceneRoot = mActivity.findViewById(R.id.holder);

        final int startWidth = redSquare.getWidth();
        final int startHeight = redSquare.getHeight();

        final CountDownLatch onDisappearCalled = new CountDownLatch(1);

        Fade fadeOut = new Fade(Fade.MODE_OUT) {
            @Override
            public Animator onDisappear(ViewGroup sceneRoot, View view,
                    TransitionValues startValues,
                    TransitionValues endValues) {
                assertNotSame(view, redSquare);
                assertEquals(startWidth, view.getWidth());
                assertEquals(startHeight, view.getHeight());
                assertTrue(view instanceof ImageView);
                ImageView imageView = (ImageView) view;
                BitmapDrawable bitmapDrawable = (BitmapDrawable) imageView.getDrawable();
                Bitmap bitmap = bitmapDrawable.getBitmap();
                Bitmap copy = bitmap.copy(Bitmap.Config.ARGB_8888, false);
                Bitmap expected = redSquareBitmap.copy(Bitmap.Config.ARGB_8888, false);
                verifySimilar(expected, copy, 0.95);
                onDisappearCalled.countDown();
                return super.onDisappear(sceneRoot, view, startValues, endValues);
            }
        };
        fadeOut.setDuration(20);

        final CountDownLatch endLatch = new CountDownLatch(1);
        fadeOut.addListener(new TransitionListenerAdapter() {
            @Override
            public void onTransitionEnd(Transition transition) {
                endLatch.countDown();
            }
        });


        mActivityRule.runOnUiThread(() -> {
            TransitionManager.beginDelayedTransition(sceneRoot, fadeOut);
            sceneRoot.removeView(redSquare);
            container.addView(redSquare);
        });

        // Should only take 20ms, but no need to rush here
        assertTrue(endLatch.await(1, TimeUnit.SECONDS));
        assertTrue(onDisappearCalled.await(0, TimeUnit.SECONDS));
    }

    private Bitmap createViewBitmap(View view) {
        int bitmapWidth = view.getWidth();
        int bitmapHeight = view.getHeight();
        Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        view.draw(canvas);
        return bitmap;
    }

    /**
     * From {@link android.uirendering.cts.bitmapcomparers.MSSIMComparer}
     */
    private static void verifySimilar(Bitmap expected, Bitmap real, double threshold) {
        assertEquals(expected.getWidth(), real.getWidth());
        assertEquals(expected.getHeight(), real.getHeight());

        double ssimTotal = 0;
        int windows = 0;

        for (int y = 0; y < expected.getHeight(); y += WINDOW_SIZE) {
            y = Math.min(y, expected.getHeight() - WINDOW_SIZE);

            for (int x = 0; x < expected.getWidth(); x += WINDOW_SIZE) {
                x = Math.min(x, expected.getWidth() - WINDOW_SIZE);

                if (isWindowWhite(expected, x, y) && isWindowWhite(real, x, y)) {
                    continue;
                }
                windows++;
                double[] means = getMeans(expected, real, x, y);
                double meanX = means[0];
                double meanY = means[1];
                double[] variances = getVariances(expected, real, meanX, meanY, x, y);
                double varX = variances[0];
                double varY = variances[1];
                double stdBoth = variances[2];
                double ssim = ssim(meanX, meanY, varX, varY, stdBoth);
                ssimTotal += ssim;
            }
        }
        assertTrue(ssimTotal >= threshold);
    }

    /**
     * This method will find the mean of a window in both sets of pixels. The return is an array
     * where the first double is the mean of the first set and the second double is the mean of the
     * second set.
     */
    private static double[] getMeans(Bitmap bitmap1, Bitmap bitmap2, int xStart, int yStart) {
        double avg0 = 0;
        double avg1 = 0;
        for (int y = 0; y < WINDOW_SIZE; y++) {
            for (int x = 0; x < WINDOW_SIZE; x++) {
                avg0 += getIntensity(bitmap1.getPixel(xStart + x, yStart + y));
                avg1 += getIntensity(bitmap2.getPixel(xStart + x, yStart + y));
            }
        }
        avg0 /= WINDOW_SIZE * WINDOW_SIZE;
        avg1 /= WINDOW_SIZE * WINDOW_SIZE;
        return new double[]{avg0, avg1};
    }

    /**
     * Finds the variance of the two sets of pixels, as well as the covariance of the windows. The
     * return value is an array of doubles, the first is the variance of the first set of pixels,
     * the second is the variance of the second set of pixels, and the third is the covariance.
     */
    private static double[] getVariances(Bitmap bitmap1, Bitmap bitmap2, double mean0, double mean1,
            int xStart, int yStart) {
        double var0 = 0;
        double var1 = 0;
        double varBoth = 0;
        for (int y = 0; y < WINDOW_SIZE; y++) {
            for (int x = 0; x < WINDOW_SIZE; x++) {
                double v0 = getIntensity(bitmap1.getPixel(xStart + x, yStart + y)) - mean0;
                double v1 = getIntensity(bitmap2.getPixel(xStart + x, yStart + y)) - mean1;
                var0 += v0 * v0;
                var1 += v1 * v1;
                varBoth += v0 * v1;
            }
        }
        var0 /= (WINDOW_SIZE * WINDOW_SIZE) - 1;
        var1 /= (WINDOW_SIZE * WINDOW_SIZE) - 1;
        varBoth /= (WINDOW_SIZE * WINDOW_SIZE) - 1;
        return new double[]{var0, var1, varBoth};
    }

    /**
     * Gets the intensity of a given pixel in RGB using luminosity formula
     *
     * l = 0.21R' + 0.72G' + 0.07B'
     *
     * The prime symbols dictate a gamma correction of 1.
     */
    private static double getIntensity(int pixel) {
        final double gamma = 1;
        double l = 0;
        l += (0.21f * Math.pow(Color.red(pixel) / 255f, gamma));
        l += (0.72f * Math.pow(Color.green(pixel) / 255f, gamma));
        l += (0.07f * Math.pow(Color.blue(pixel) / 255f, gamma));
        return l;
    }

    private static boolean isWindowWhite(Bitmap bitmap, int xStart, int yStart) {
        for (int y = 0; y < WINDOW_SIZE; y++) {
            for (int x = 0; x < WINDOW_SIZE; x++) {
                if (bitmap.getPixel(xStart + x, yStart + y) != Color.WHITE) {
                    return false;
                }
            }
        }
        return true;
    }

    private static double ssim(double muX, double muY, double sigX, double sigY, double sigXY) {
        double ssim = (((2 * muX * muY) + CONSTANT_C1) * ((2 * sigXY) + CONSTANT_C2));
        double denom = ((muX * muX) + (muY * muY) + CONSTANT_C1)
                * (sigX + sigY + CONSTANT_C2);
        ssim /= denom;
        return ssim;
    }
}

