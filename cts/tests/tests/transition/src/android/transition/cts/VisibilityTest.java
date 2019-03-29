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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.animation.Animator;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.transition.TransitionManager;
import android.transition.TransitionValues;
import android.transition.Visibility;
import android.view.View;
import android.view.ViewGroup;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class VisibilityTest extends BaseTransitionTest {
    Visibility mVisibilityTransition;

    @Override
    @Before
    public void setup() {
        super.setup();
        mVisibilityTransition = (Visibility) mTransition;
    }

    @Test
    public void testMode() throws Throwable {
        assertEquals(Visibility.MODE_IN | Visibility.MODE_OUT, mVisibilityTransition.getMode());

        // Should animate in and out
        enterScene(R.layout.scene4);
        startTransition(R.layout.scene1);
        verify(mListener, never()).onTransitionEnd(any());
        waitForEnd(800);

        resetListener();
        startTransition(R.layout.scene4);
        verify(mListener, never()).onTransitionEnd(any());
        waitForEnd(800);

        // Now only animate in
        resetListener();
        mVisibilityTransition.setMode(Visibility.MODE_IN);
        startTransition(R.layout.scene1);
        verify(mListener, never()).onTransitionEnd(any());
        waitForEnd(800);

        // No animation since it should only animate in
        resetListener();
        startTransition(R.layout.scene4);
        waitForEnd(0);

        // Now animate out, but no animation should happen since we're animating in.
        resetListener();
        mVisibilityTransition.setMode(Visibility.MODE_OUT);
        startTransition(R.layout.scene1);
        waitForEnd(0);

        // but it should animate out
        resetListener();
        startTransition(R.layout.scene4);
        verify(mListener, never()).onTransitionEnd(any());
        waitForEnd(800);
    }

    @Test
    public void testIsVisible() throws Throwable {
        assertFalse(mVisibilityTransition.isVisible(null));

        enterScene(R.layout.scene1);
        final View redSquare = mActivity.findViewById(R.id.redSquare);
        TransitionValues visibleValues = new TransitionValues();
        visibleValues.view = redSquare;
        mTransition.captureStartValues(visibleValues);

        assertTrue(mVisibilityTransition.isVisible(visibleValues));
        mActivityRule.runOnUiThread(() -> redSquare.setVisibility(View.INVISIBLE));
        mInstrumentation.waitForIdleSync();
        TransitionValues invisibleValues = new TransitionValues();
        invisibleValues.view = redSquare;
        mTransition.captureStartValues(invisibleValues);
        assertFalse(mVisibilityTransition.isVisible(invisibleValues));

        mActivityRule.runOnUiThread(() -> redSquare.setVisibility(View.GONE));
        mInstrumentation.waitForIdleSync();
        TransitionValues goneValues = new TransitionValues();
        goneValues.view = redSquare;
        mTransition.captureStartValues(goneValues);
        assertFalse(mVisibilityTransition.isVisible(goneValues));
    }

    @Test
    public void testOnAppear() throws Throwable {
        enterScene(R.layout.scene4);
        AppearTransition transition = new AppearTransition();
        mTransition = transition;
        resetListener();
        startTransition(R.layout.scene5);
        assertTrue(transition.onAppearCalled.await(500, TimeUnit.MILLISECONDS));
        // No need to end the transition since AppearTransition doesn't create
        // any animators.
    }

    @Test
    public void testOnDisppear() throws Throwable {
        // First, test with overlay
        enterScene(R.layout.scene5);
        DisappearTransition transition = new DisappearTransition(true);
        mTransition = transition;
        resetListener();
        startTransition(R.layout.scene4);
        assertTrue(transition.onDisppearCalled.await(500, TimeUnit.MILLISECONDS));
        // No need to end the transition since DisappearTransition doesn't create
        // any animators.

        // Next test without overlay
        enterScene(R.layout.scene5);
        transition = new DisappearTransition(false);
        mTransition = transition;
        resetListener();
        final View text = mActivity.findViewById(R.id.text);
        mActivityRule.runOnUiThread(() -> {
            TransitionManager.beginDelayedTransition(mSceneRoot, mTransition);
            text.setVisibility(View.GONE);
        });
        assertTrue(transition.onDisppearCalled.await(500, TimeUnit.MILLISECONDS));
        // No need to end the transition since DisappearTransition doesn't create
        // any animators.
    }

    static class AppearTransition extends Visibility {
        private View mExpectedView;
        public CountDownLatch onAppearCalled = new CountDownLatch(1);
        @Override
        public Animator onAppear(ViewGroup sceneRoot, TransitionValues startValues,
                int startVisibility, TransitionValues endValues, int endVisibility) {
            assertNotNull(endValues);
            mExpectedView = endValues.view;
            return super.onAppear(sceneRoot, startValues, startVisibility, endValues,
                    endVisibility);
        }

        @Override
        public Animator onAppear(ViewGroup sceneRoot, View view, TransitionValues startValues,
                TransitionValues endValues) {
            assertSame(mExpectedView, view);
            onAppearCalled.countDown();
            return null;
        }
    }

    static class DisappearTransition extends Visibility {
        private View mExpectedView;
        private final boolean mExpectingOverlay;
        public CountDownLatch onDisppearCalled = new CountDownLatch(1);

        public DisappearTransition(boolean expectingOverlay) {
            mExpectingOverlay = expectingOverlay;
        }

        @Override
        public Animator onDisappear(ViewGroup sceneRoot, TransitionValues startValues,
                int startVisibility, TransitionValues endValues, int endVisibility) {
            assertNotNull(startValues);
            if (mExpectingOverlay) {
                assertNull(endValues);
                mExpectedView = null;
            } else {
                assertNotNull(endValues);
                mExpectedView = endValues.view;
            }
            return super.onDisappear(sceneRoot, startValues, startVisibility, endValues,
                    endVisibility);
        }

        @Override
        public Animator onDisappear(ViewGroup sceneRoot, View view, TransitionValues startValues,
                TransitionValues endValues) {
            assertNotNull(view);
            if (mExpectedView != null) {
                assertSame(mExpectedView, view);
            }
            onDisppearCalled.countDown();
            return null;
        }
    }
}

