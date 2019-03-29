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
package android.uirendering.cts.testclasses;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyFloat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.support.test.filters.LargeTest;
import android.support.test.filters.MediumTest;
import android.support.test.filters.Suppress;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.uirendering.cts.R;
import android.uirendering.cts.bitmapverifiers.PerPixelBitmapVerifier;
import android.uirendering.cts.testinfrastructure.MaterialActivity;
import android.uirendering.cts.util.BitmapAsserter;
import android.widget.EdgeEffect;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class EdgeEffectTests {

    private static final int WIDTH = 90;
    private static final int HEIGHT = 90;

    @Rule
    public TestName name = new TestName();

    @Rule
    public ActivityTestRule<MaterialActivity> mActivityRule = new ActivityTestRule<>(
            MaterialActivity.class);

    private BitmapAsserter mBitmapAsserter = new BitmapAsserter(this.getClass().getSimpleName(),
            name.getMethodName());

    interface EdgeEffectInitializer {
        void initialize(EdgeEffect edgeEffect);
    }

    private Activity getActivity() {
        return mActivityRule.getActivity();
    }

    @Before
    public void setUp() {
        mBitmapAsserter.setUp(getActivity());
    }

    private static class EdgeEffectValidator extends PerPixelBitmapVerifier {
        public int matchedColorCount;

        private int mInverseColorMask;
        private int mColorMask;

        public EdgeEffectValidator(int drawColor) {
            mColorMask = drawColor & 0x00FFFFFF;
            mInverseColorMask = ~(drawColor & 0x00FFFFFF);
        }

        @Override
        protected boolean verifyPixel(int x, int y, int observedColor) {
            if ((observedColor & mColorMask) != 0) {
                matchedColorCount++;
            }
            return (observedColor & mInverseColorMask) == 0xFF000000;
        }
    }

    private void assertEdgeEffect(EdgeEffectInitializer initializer) {
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.BLACK);
        EdgeEffect edgeEffect = new EdgeEffect(getActivity());
        edgeEffect.setSize(WIDTH, HEIGHT);
        edgeEffect.setColor(Color.RED);
        assertEquals(Color.RED, edgeEffect.getColor());
        initializer.initialize(edgeEffect);
        edgeEffect.draw(canvas);

        EdgeEffectValidator verifier = new EdgeEffectValidator(edgeEffect.getColor());
        mBitmapAsserter.assertBitmapIsVerified(bitmap, verifier,
                name.getMethodName(), "EdgeEffect doesn't match expected");
        assertTrue(verifier.matchedColorCount > 0);
    }

    @Test
    public void testOnPull() {
        assertEdgeEffect(edgeEffect -> {
            edgeEffect.onPull(1);
        });
    }

    @Test
    public void testSetSize() {
        assertEdgeEffect(edgeEffect -> {
            edgeEffect.setSize(70, 70);
            edgeEffect.onPull(1);
        });
    }

    @Test
    public void testSetColor() {
        assertEdgeEffect(edgeEffect -> {
            edgeEffect.setColor(Color.GREEN);
            assertEquals(Color.GREEN, edgeEffect.getColor());
            edgeEffect.onPull(1);
        });
    }

    @Test
    public void testOnPullWithDisplacement() {
        assertEdgeEffect(edgeEffect -> {
            edgeEffect.onPull(1, 0);
        });

        assertEdgeEffect(edgeEffect -> {
            edgeEffect.onPull(1, 1);
        });
    }

    @Test
    public void testIsFinished() {
        EdgeEffect effect = new EdgeEffect(getActivity());
        assertTrue(effect.isFinished());
        effect.onPull(0.5f);
        assertFalse(effect.isFinished());
    }

    @Test
    public void testFinish() {
        EdgeEffect effect = new EdgeEffect(getActivity());
        effect.onPull(1);
        effect.finish();
        assertTrue(effect.isFinished());

        effect.onAbsorb(1000);
        effect.finish();
        assertFalse(effect.draw(new Canvas()));
    }

    @Test
    public void testGetColor() {
        EdgeEffect effect = new EdgeEffect(getActivity());
        effect.setColor(Color.GREEN);
        assertEquals(Color.GREEN, effect.getColor());
    }

    @Test
    public void testGetMaxHeight() {
        EdgeEffect edgeEffect = new EdgeEffect(getActivity());
        edgeEffect.setSize(200, 200);
        assertTrue(edgeEffect.getMaxHeight() <= 200 * 2 + 1);
        edgeEffect.setSize(200, 0);
        assertEquals(0, edgeEffect.getMaxHeight());
    }

    private interface AlphaVerifier {
        void verify(int oldAlpha, int newAlpha);
    }

    // validates changes to the alpha of draw commands produced by EdgeEffect
    // over the course of an animation
    private void verifyAlpha(EdgeEffectInitializer initializer, AlphaVerifier alphaVerifier) {
        Canvas canvas = mock(Canvas.class);
        ArgumentCaptor<Paint> captor = ArgumentCaptor.forClass(Paint.class);
        EdgeEffect edgeEffect = new EdgeEffect(getActivity());
        edgeEffect.setSize(200, 200);
        initializer.initialize(edgeEffect);
        edgeEffect.draw(canvas);
        verify(canvas).drawCircle(anyFloat(), anyFloat(), anyFloat(), captor.capture());
        int oldAlpha = captor.getValue().getAlpha();
        for (int i = 0; i < 3; i++) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                fail();
            }
            canvas = mock(Canvas.class);
            edgeEffect.draw(canvas);
            verify(canvas).drawCircle(anyFloat(), anyFloat(), anyFloat(), captor.capture());
            int newAlpha = captor.getValue().getAlpha();
            alphaVerifier.verify(oldAlpha, newAlpha);
            oldAlpha = newAlpha;
        }
    }

    @LargeTest
    @Test
    public void testOnAbsorb() {
        verifyAlpha(edgeEffect -> {
            edgeEffect.onAbsorb(10000);
        }, ((oldAlpha, newAlpha) -> {
            assertTrue("Alpha should grow", oldAlpha < newAlpha);
        }));
    }

    @Test
    public void testOnRelease() {
        verifyAlpha(edgeEffect -> {
            edgeEffect.onPull(1);
            edgeEffect.onRelease();
        }, ((oldAlpha, newAlpha) -> {
            assertTrue("Alpha should decrease", oldAlpha > newAlpha);
        }));
    }

}
