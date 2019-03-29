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
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.transition.Explode;
import android.transition.TransitionManager;
import android.view.View;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class ExplodeTest extends BaseTransitionTest {
    Explode mExplode;

    @Override
    @Before
    public void setup() {
        super.setup();
        resetTransition();
    }

    private void resetTransition() {
        mExplode = new Explode();
        mTransition = mExplode;
        resetListener();
    }

    @Test
    public void testExplode() throws Throwable {
        enterScene(R.layout.scene10);
        final View redSquare = mActivity.findViewById(R.id.redSquare);
        final View greenSquare = mActivity.findViewById(R.id.greenSquare);
        final View blueSquare = mActivity.findViewById(R.id.blueSquare);
        final View yellowSquare = mActivity.findViewById(R.id.yellowSquare);

        mActivityRule.runOnUiThread(() -> {
            TransitionManager.beginDelayedTransition(mSceneRoot, mTransition);
            redSquare.setVisibility(View.INVISIBLE);
            greenSquare.setVisibility(View.INVISIBLE);
            blueSquare.setVisibility(View.INVISIBLE);
            yellowSquare.setVisibility(View.INVISIBLE);
        });
        waitForStart();
        verify(mListener, never()).onTransitionEnd(any());
        assertEquals(View.VISIBLE, redSquare.getVisibility());
        assertEquals(View.VISIBLE, greenSquare.getVisibility());
        assertEquals(View.VISIBLE, blueSquare.getVisibility());
        assertEquals(View.VISIBLE, yellowSquare.getVisibility());
        float redStartX = redSquare.getTranslationX();
        float redStartY = redSquare.getTranslationY();

        Thread.sleep(100);
        verifyTranslation(redSquare, true, true);
        verifyTranslation(greenSquare, false, true);
        verifyTranslation(blueSquare, false, false);
        verifyTranslation(yellowSquare, true, false);
        assertTrue(redStartX > redSquare.getTranslationX()); // moving left
        assertTrue(redStartY > redSquare.getTranslationY()); // moving up
        waitForEnd(400);

        verifyNoTranslation(redSquare);
        verifyNoTranslation(greenSquare);
        verifyNoTranslation(blueSquare);
        verifyNoTranslation(yellowSquare);
        assertEquals(View.INVISIBLE, redSquare.getVisibility());
        assertEquals(View.INVISIBLE, greenSquare.getVisibility());
        assertEquals(View.INVISIBLE, blueSquare.getVisibility());
        assertEquals(View.INVISIBLE, yellowSquare.getVisibility());
    }

    @Test
    public void testImplode() throws Throwable {
        enterScene(R.layout.scene10);
        final View redSquare = mActivity.findViewById(R.id.redSquare);
        final View greenSquare = mActivity.findViewById(R.id.greenSquare);
        final View blueSquare = mActivity.findViewById(R.id.blueSquare);
        final View yellowSquare = mActivity.findViewById(R.id.yellowSquare);

        mActivityRule.runOnUiThread(() -> {
            redSquare.setVisibility(View.INVISIBLE);
            greenSquare.setVisibility(View.INVISIBLE);
            blueSquare.setVisibility(View.INVISIBLE);
            yellowSquare.setVisibility(View.INVISIBLE);
        });
        mInstrumentation.waitForIdleSync();

        mActivityRule.runOnUiThread(() -> {
            TransitionManager.beginDelayedTransition(mSceneRoot, mTransition);
            redSquare.setVisibility(View.VISIBLE);
            greenSquare.setVisibility(View.VISIBLE);
            blueSquare.setVisibility(View.VISIBLE);
            yellowSquare.setVisibility(View.VISIBLE);
        });
        waitForStart();

        assertEquals(View.VISIBLE, redSquare.getVisibility());
        assertEquals(View.VISIBLE, greenSquare.getVisibility());
        assertEquals(View.VISIBLE, blueSquare.getVisibility());
        assertEquals(View.VISIBLE, yellowSquare.getVisibility());
        float redStartX = redSquare.getTranslationX();
        float redStartY = redSquare.getTranslationY();

        Thread.sleep(100);
        verifyTranslation(redSquare, true, true);
        verifyTranslation(greenSquare, false, true);
        verifyTranslation(blueSquare, false, false);
        verifyTranslation(yellowSquare, true, false);
        assertTrue(redStartX < redSquare.getTranslationX()); // moving right
        assertTrue(redStartY < redSquare.getTranslationY()); // moving down
        waitForEnd(400);

        verifyNoTranslation(redSquare);
        verifyNoTranslation(greenSquare);
        verifyNoTranslation(blueSquare);
        verifyNoTranslation(yellowSquare);
        assertEquals(View.VISIBLE, redSquare.getVisibility());
        assertEquals(View.VISIBLE, greenSquare.getVisibility());
        assertEquals(View.VISIBLE, blueSquare.getVisibility());
        assertEquals(View.VISIBLE, yellowSquare.getVisibility());
    }

    private void verifyTranslation(View view, boolean goLeft, boolean goUp) {
        float translationX = view.getTranslationX();
        float translationY = view.getTranslationY();

        if (goLeft) {
            assertTrue(translationX < 0);
        } else {
            assertTrue(translationX > 0);
        }

        if (goUp) {
            assertTrue(translationY < 0);
        } else {
            assertTrue(translationY > 0);
        }
    }

    private void verifyNoTranslation(View view) {
        assertEquals(0f, view.getTranslationX(), 0.0f);
        assertEquals(0f, view.getTranslationY(), 0.0f);
    }
}

