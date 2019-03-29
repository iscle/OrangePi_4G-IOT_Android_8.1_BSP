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
import android.transition.ChangeTransform;
import android.transition.TransitionManager;
import android.view.View;
import android.view.ViewGroup;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class ChangeTransformTest extends BaseTransitionTest {
    ChangeTransform mChangeTransform;

    @Override
    @Before
    public void setup() {
        super.setup();
        resetChangeBoundsTransition();
    }

    private void resetChangeBoundsTransition() {
        mChangeTransform = new ChangeTransform();
        mTransition = mChangeTransform;
        resetListener();
    }

    @Test
    public void testTranslation() throws Throwable {
        enterScene(R.layout.scene1);

        final View redSquare = mActivity.findViewById(R.id.redSquare);

        mActivityRule.runOnUiThread(() -> {
            TransitionManager.beginDelayedTransition(mSceneRoot, mChangeTransform);
            redSquare.setTranslationX(500);
            redSquare.setTranslationY(600);
        });
        waitForStart();

        verify(mListener, never()).onTransitionEnd(any()); // still running
        // There is no way to validate the intermediate matrix because it uses
        // hidden properties of the View to execute.
        waitForEnd(800);
        assertEquals(500f, redSquare.getTranslationX(), 0.0f);
        assertEquals(600f, redSquare.getTranslationY(), 0.0f);
    }

    @Test
    public void testRotation() throws Throwable {
        enterScene(R.layout.scene1);

        final View redSquare = mActivity.findViewById(R.id.redSquare);

        mActivityRule.runOnUiThread(() -> {
            TransitionManager.beginDelayedTransition(mSceneRoot, mChangeTransform);
            redSquare.setRotation(45);
        });
        waitForStart();

        verify(mListener, never()).onTransitionEnd(any()); // still running
        // There is no way to validate the intermediate matrix because it uses
        // hidden properties of the View to execute.
        waitForEnd(800);
        assertEquals(45f, redSquare.getRotation(), 0.0f);
    }

    @Test
    public void testScale() throws Throwable {
        enterScene(R.layout.scene1);

        final View redSquare = mActivity.findViewById(R.id.redSquare);

        mActivityRule.runOnUiThread(() -> {
            TransitionManager.beginDelayedTransition(mSceneRoot, mChangeTransform);
            redSquare.setScaleX(2f);
            redSquare.setScaleY(3f);
        });
        waitForStart();

        verify(mListener, never()).onTransitionEnd(any()); // still running
        // There is no way to validate the intermediate matrix because it uses
        // hidden properties of the View to execute.
        waitForEnd(800);
        assertEquals(2f, redSquare.getScaleX(), 0.0f);
        assertEquals(3f, redSquare.getScaleY(), 0.0f);
    }

    @Test
    public void testReparent() throws Throwable {
        assertEquals(true, mChangeTransform.getReparent());
        enterScene(R.layout.scene5);
        startTransition(R.layout.scene9);
        verify(mListener, never()).onTransitionEnd(any()); // still running
        waitForEnd(800);

        resetListener();
        mChangeTransform.setReparent(false);
        assertEquals(false, mChangeTransform.getReparent());
        startTransition(R.layout.scene5);
        waitForEnd(0); // no transition to run because reparent == false
    }

    @Test
    public void testReparentWithOverlay() throws Throwable {
        assertEquals(true, mChangeTransform.getReparentWithOverlay());
        enterScene(R.layout.scene5);
        startTransition(R.layout.scene9);
        verify(mListener, never()).onTransitionEnd(any()); // still running
        mActivityRule.runOnUiThread(() -> {
            View view = new View(mActivity);
            view.setRight(100);
            view.setBottom(100);
            mSceneRoot.getOverlay().add(view);
            ViewGroup container = (ViewGroup) view.getParent();
            assertEquals(2, container.getChildCount());
            mSceneRoot.getOverlay().remove(view);
            assertTrue(mActivity.findViewById(R.id.text).getVisibility() != View.VISIBLE);
        });
        waitForEnd(800);

        mChangeTransform.setReparentWithOverlay(false);
        assertEquals(false, mChangeTransform.getReparentWithOverlay());
        resetListener();
        startTransition(R.layout.scene5);
        verify(mListener, never()).onTransitionEnd(any()); // still running
        mActivityRule.runOnUiThread(() -> {
            View view = new View(mActivity);
            view.setRight(100);
            view.setBottom(100);
            mSceneRoot.getOverlay().add(view);
            ViewGroup container = (ViewGroup) view.getParent();
            assertEquals(1, container.getChildCount());
            mSceneRoot.getOverlay().remove(view);
            assertEquals(View.VISIBLE, mActivity.findViewById(R.id.text).getVisibility());
        });
        waitForEnd(800);
    }
}

