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

import static com.android.compatibility.common.util.CtsMockitoUtils.within;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.transition.Slide;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class SlideEdgeTest extends BaseTransitionTest  {
    private static final Object[][] sSlideEdgeArray = {
            { Gravity.START, "START" },
            { Gravity.END, "END" },
            { Gravity.LEFT, "LEFT" },
            { Gravity.TOP, "TOP" },
            { Gravity.RIGHT, "RIGHT" },
            { Gravity.BOTTOM, "BOTTOM" },
    };

    @Test
    public void testSetSide() throws Throwable {
        for (int i = 0; i < sSlideEdgeArray.length; i++) {
            int slideEdge = (Integer) (sSlideEdgeArray[i][0]);
            String edgeName = (String) (sSlideEdgeArray[i][1]);
            Slide slide = new Slide(slideEdge);
            assertEquals("Edge not set properly in constructor " + edgeName,
                    slideEdge, slide.getSlideEdge());

            slide = new Slide();
            slide.setSlideEdge(slideEdge);
            assertEquals("Edge not set properly with setter " + edgeName,
                    slideEdge, slide.getSlideEdge());
        }
    }

    @Test
    public void testSlideOut() throws Throwable {
        for (int i = 0; i < sSlideEdgeArray.length; i++) {
            final int slideEdge = (Integer) (sSlideEdgeArray[i][0]);
            final Slide slide = new Slide(slideEdge);
            final Transition.TransitionListener listener =
                    mock(Transition.TransitionListener.class);
            slide.addListener(listener);

            mActivityRule.runOnUiThread(() -> mActivity.setContentView(R.layout.scene1));
            mInstrumentation.waitForIdleSync();

            final View redSquare = mActivity.findViewById(R.id.redSquare);
            final View greenSquare = mActivity.findViewById(R.id.greenSquare);
            final View hello = mActivity.findViewById(R.id.hello);
            final ViewGroup sceneRoot = (ViewGroup) mActivity.findViewById(R.id.holder);

            mActivityRule.runOnUiThread(() -> {
                TransitionManager.beginDelayedTransition(sceneRoot, slide);
                redSquare.setVisibility(View.INVISIBLE);
                greenSquare.setVisibility(View.INVISIBLE);
                hello.setVisibility(View.INVISIBLE);
            });
            verify(listener, within(1000)).onTransitionStart(any());
            verify(listener, never()).onTransitionEnd(any());
            assertEquals(View.VISIBLE, redSquare.getVisibility());
            assertEquals(View.VISIBLE, greenSquare.getVisibility());
            assertEquals(View.VISIBLE, hello.getVisibility());

            float redStartX = redSquare.getTranslationX();
            float redStartY = redSquare.getTranslationY();

            Thread.sleep(200);
            verifyTranslation(slideEdge, redSquare);
            verifyTranslation(slideEdge, greenSquare);
            verifyTranslation(slideEdge, hello);

            final float redMidX = redSquare.getTranslationX();
            final float redMidY = redSquare.getTranslationY();

            switch (slideEdge) {
                case Gravity.LEFT:
                case Gravity.START:
                    assertTrue(
                            "isn't sliding out to left. Expecting " + redStartX + " > " + redMidX,
                            redStartX > redMidX);
                    break;
                case Gravity.RIGHT:
                case Gravity.END:
                    assertTrue(
                            "isn't sliding out to right. Expecting " + redStartX + " < " + redMidX,
                            redStartX < redMidX);
                    break;
                case Gravity.TOP:
                    assertTrue("isn't sliding out to top. Expecting " + redStartY + " > " + redMidY,
                            redStartY > redSquare.getTranslationY());
                    break;
                case Gravity.BOTTOM:
                    assertTrue(
                            "isn't sliding out to bottom. Expecting " + redStartY + " < " + redMidY,
                            redStartY < redSquare.getTranslationY());
                    break;
            }
            verify(listener, within(1000)).onTransitionEnd(any());
            mInstrumentation.waitForIdleSync();

            verifyNoTranslation(redSquare);
            verifyNoTranslation(greenSquare);
            verifyNoTranslation(hello);
            assertEquals(View.INVISIBLE, redSquare.getVisibility());
            assertEquals(View.INVISIBLE, greenSquare.getVisibility());
            assertEquals(View.INVISIBLE, hello.getVisibility());
        }
    }

    @Test
    public void testSlideIn() throws Throwable {
        for (int i = 0; i < sSlideEdgeArray.length; i++) {
            final int slideEdge = (Integer) (sSlideEdgeArray[i][0]);
            final Slide slide = new Slide(slideEdge);
            final Transition.TransitionListener listener =
                    mock(Transition.TransitionListener.class);
            slide.addListener(listener);

            mActivityRule.runOnUiThread(() -> mActivity.setContentView(R.layout.scene1));
            mInstrumentation.waitForIdleSync();

            final View redSquare = mActivity.findViewById(R.id.redSquare);
            final View greenSquare = mActivity.findViewById(R.id.greenSquare);
            final View hello = mActivity.findViewById(R.id.hello);
            final ViewGroup sceneRoot = (ViewGroup) mActivity.findViewById(R.id.holder);

            mActivityRule.runOnUiThread(() -> {
                redSquare.setVisibility(View.INVISIBLE);
                greenSquare.setVisibility(View.INVISIBLE);
                hello.setVisibility(View.INVISIBLE);
            });
            mInstrumentation.waitForIdleSync();

            // now slide in
            mActivityRule.runOnUiThread(() -> {
                TransitionManager.beginDelayedTransition(sceneRoot, slide);
                redSquare.setVisibility(View.VISIBLE);
                greenSquare.setVisibility(View.VISIBLE);
                hello.setVisibility(View.VISIBLE);
            });
            verify(listener, within(1000)).onTransitionStart(any());

            verify(listener, never()).onTransitionEnd(any());
            assertEquals(View.VISIBLE, redSquare.getVisibility());
            assertEquals(View.VISIBLE, greenSquare.getVisibility());
            assertEquals(View.VISIBLE, hello.getVisibility());

            final float redStartX = redSquare.getTranslationX();
            final float redStartY = redSquare.getTranslationY();

            Thread.sleep(200);
            verifyTranslation(slideEdge, redSquare);
            verifyTranslation(slideEdge, greenSquare);
            verifyTranslation(slideEdge, hello);
            final float redMidX = redSquare.getTranslationX();
            final float redMidY = redSquare.getTranslationY();

            switch (slideEdge) {
                case Gravity.LEFT:
                case Gravity.START:
                    assertTrue(
                            "isn't sliding in from left. Expecting " + redStartX + " < " + redMidX,
                            redStartX < redMidX);
                    break;
                case Gravity.RIGHT:
                case Gravity.END:
                    assertTrue(
                            "isn't sliding in from right. Expecting " + redStartX + " > " + redMidX,
                            redStartX > redMidX);
                    break;
                case Gravity.TOP:
                    assertTrue(
                            "isn't sliding in from top. Expecting " + redStartY + " < " + redMidY,
                            redStartY < redSquare.getTranslationY());
                    break;
                case Gravity.BOTTOM:
                    assertTrue("isn't sliding in from bottom. Expecting " + redStartY + " > "
                                    + redMidY,
                            redStartY > redSquare.getTranslationY());
                    break;
            }
            verify(listener, within(1000)).onTransitionEnd(any());
            mInstrumentation.waitForIdleSync();

            verifyNoTranslation(redSquare);
            verifyNoTranslation(greenSquare);
            verifyNoTranslation(hello);
            assertEquals(View.VISIBLE, redSquare.getVisibility());
            assertEquals(View.VISIBLE, greenSquare.getVisibility());
            assertEquals(View.VISIBLE, hello.getVisibility());
        }
    }

    private void verifyTranslation(int slideEdge, View view) {
        switch (slideEdge) {
            case Gravity.LEFT:
            case Gravity.START:
                assertTrue(view.getTranslationX() < 0);
                assertEquals(0f, view.getTranslationY(), 0.01f);
                break;
            case Gravity.RIGHT:
            case Gravity.END:
                assertTrue(view.getTranslationX() > 0);
                assertEquals(0f, view.getTranslationY(), 0.01f);
                break;
            case Gravity.TOP:
                assertTrue(view.getTranslationY() < 0);
                assertEquals(0f, view.getTranslationX(), 0.01f);
                break;
            case Gravity.BOTTOM:
                assertTrue(view.getTranslationY() > 0);
                assertEquals(0f, view.getTranslationX(), 0.01f);
                break;
        }
    }

    private void verifyNoTranslation(View view) {
        assertEquals(0f, view.getTranslationX(), 0.01f);
        assertEquals(0f, view.getTranslationY(), 0.01f);
    }
}

