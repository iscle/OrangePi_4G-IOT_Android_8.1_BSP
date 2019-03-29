/*
 * Copyright (C) 2015 The Android Open Source Project
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.graphics.Rect;
import android.os.SystemClock;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.transition.Scene;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.view.View;
import android.view.ViewTreeObserver;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class TransitionManagerTest extends BaseTransitionTest {
    @Test
    public void testBeginDelayedTransition() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            TransitionManager.beginDelayedTransition(mSceneRoot, mTransition);
            View view = mActivity.getLayoutInflater().inflate(R.layout.scene1, mSceneRoot,
                    false);
            mSceneRoot.addView(view);
        });

        waitForStart();
        waitForEnd(800);
        verify(mListener, never()).onTransitionResume(any());
        verify(mListener, never()).onTransitionPause(any());
        verify(mListener, never()).onTransitionCancel(any());
        ArgumentCaptor<Transition> transitionArgumentCaptor =
                ArgumentCaptor.forClass(Transition.class);
        verify(mListener, times(1)).onTransitionStart(transitionArgumentCaptor.capture());
        assertEquals(TestTransition.class, transitionArgumentCaptor.getValue().getClass());
        assertTrue(mTransition != transitionArgumentCaptor.getValue());
        mActivityRule.runOnUiThread(() -> {
            assertNotNull(mActivity.findViewById(R.id.redSquare));
            assertNotNull(mActivity.findViewById(R.id.greenSquare));
        });
    }

    @Test
    public void testDefaultBeginDelayedTransition() throws Throwable {
        enterScene(R.layout.scene1);
        final CountDownLatch startLatch = new CountDownLatch(1);
        mSceneRoot.getViewTreeObserver().addOnPreDrawListener(
                new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        mSceneRoot.getViewTreeObserver().removeOnPreDrawListener(this);
                        startLatch.countDown();
                        return true;
                    }
                });
        mActivityRule.runOnUiThread(() -> TransitionManager.beginDelayedTransition(mSceneRoot));
        enterScene(R.layout.scene6);
        assertTrue(startLatch.await(500, TimeUnit.MILLISECONDS));
        ensureRedSquareIsMoving();
        endTransition();
    }

    private void ensureRedSquareIsMoving() throws InterruptedException {
        final View view = mActivity.findViewById(R.id.redSquare);
        assertNotNull(view);
        // We should see a ChangeBounds on redSquare
        final Rect position = new Rect(view.getLeft(), view.getTop(), view.getRight(),
                view.getBottom());
        final CountDownLatch latch = new CountDownLatch(1);
        view.postOnAnimationDelayed(() -> {
            Rect next = new Rect(view.getLeft(), view.getTop(), view.getRight(),
                    view.getBottom());
            assertTrue(!next.equals(position));
            latch.countDown();
        }, 20);
        assertTrue(latch.await(500, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testGo() throws Throwable {
        startTransition(R.layout.scene1);
        waitForStart();
        waitForEnd(800);

        verify(mListener, never()).onTransitionResume(any());
        verify(mListener, never()).onTransitionPause(any());
        verify(mListener, never()).onTransitionCancel(any());
        ArgumentCaptor<Transition> transitionArgumentCaptor =
                ArgumentCaptor.forClass(Transition.class);
        verify(mListener, times(1)).onTransitionStart(transitionArgumentCaptor.capture());
        assertEquals(TestTransition.class, transitionArgumentCaptor.getValue().getClass());
        assertTrue(mTransition != transitionArgumentCaptor.getValue());
        mActivityRule.runOnUiThread(() -> {
            assertNotNull(mActivity.findViewById(R.id.redSquare));
            assertNotNull(mActivity.findViewById(R.id.greenSquare));
        });
    }

    @Test
    public void testDefaultGo() throws Throwable {
        enterScene(R.layout.scene1);
        final CountDownLatch startLatch = new CountDownLatch(1);
        mSceneRoot.getViewTreeObserver().addOnPreDrawListener(
                new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        mSceneRoot.getViewTreeObserver().removeOnPreDrawListener(this);
                        startLatch.countDown();
                        return true;
                    }
                });
        final Scene scene6 = loadScene(R.layout.scene6);
        mActivityRule.runOnUiThread(() -> TransitionManager.go(scene6));
        assertTrue(startLatch.await(500, TimeUnit.MILLISECONDS));
        ensureRedSquareIsMoving();
        endTransition();
    }

    @Test
    public void testSetTransition1() throws Throwable {
        final TransitionManager transitionManager = new TransitionManager();

        mActivityRule.runOnUiThread(() -> {
            Scene scene = Scene.getSceneForLayout(mSceneRoot, R.layout.scene1, mActivity);
            transitionManager.setTransition(scene, mTransition);
            transitionManager.transitionTo(scene);
        });

        waitForStart();
        waitForEnd(800);
        verify(mListener, never()).onTransitionResume(any());
        verify(mListener, never()).onTransitionPause(any());
        verify(mListener, never()).onTransitionCancel(any());
        ArgumentCaptor<Transition> transitionArgumentCaptor =
                ArgumentCaptor.forClass(Transition.class);
        verify(mListener, times(1)).onTransitionStart(transitionArgumentCaptor.capture());
        assertEquals(TestTransition.class, transitionArgumentCaptor.getValue().getClass());
        assertTrue(mTransition != transitionArgumentCaptor.getValue());
        mActivityRule.runOnUiThread(() -> {
            reset(mListener);
            assertNotNull(mActivity.findViewById(R.id.redSquare));
            assertNotNull(mActivity.findViewById(R.id.greenSquare));
            Scene scene = Scene.getSceneForLayout(mSceneRoot, R.layout.scene2, mActivity);
            transitionManager.transitionTo(scene);
        });
        SystemClock.sleep(50);
        verify(mListener, never()).onTransitionStart(any());
        endTransition();
    }

    @Test
    public void testSetTransition2() throws Throwable {
        final TransitionManager transitionManager = new TransitionManager();
        final Scene[] scenes = new Scene[3];

        mActivityRule.runOnUiThread(() -> {
            scenes[0] = Scene.getSceneForLayout(mSceneRoot, R.layout.scene1, mActivity);
            scenes[1] = Scene.getSceneForLayout(mSceneRoot, R.layout.scene2, mActivity);
            scenes[2] = Scene.getSceneForLayout(mSceneRoot, R.layout.scene3, mActivity);
            transitionManager.setTransition(scenes[0], scenes[1], mTransition);
            transitionManager.transitionTo(scenes[0]);
        });
        SystemClock.sleep(100);
        verify(mListener, never()).onTransitionStart(any());

        mActivityRule.runOnUiThread(() -> transitionManager.transitionTo(scenes[1]));

        waitForStart();
        waitForEnd(800);
        verify(mListener, never()).onTransitionResume(any());
        verify(mListener, never()).onTransitionPause(any());
        verify(mListener, never()).onTransitionCancel(any());
        ArgumentCaptor<Transition> transitionArgumentCaptor =
                ArgumentCaptor.forClass(Transition.class);
        verify(mListener, times(1)).onTransitionStart(transitionArgumentCaptor.capture());
        assertEquals(TestTransition.class, transitionArgumentCaptor.getValue().getClass());
        assertTrue(mTransition != transitionArgumentCaptor.getValue());
        mActivityRule.runOnUiThread(() -> {
            reset(mListener);
            transitionManager.transitionTo(scenes[2]);
        });
        SystemClock.sleep(50);
        verify(mListener, never()).onTransitionStart(any());
        endTransition();
    }

    @Test
    public void testEndTransitions() throws Throwable {
        mTransition.setDuration(400);

        startTransition(R.layout.scene1);
        waitForStart();
        endTransition();
        waitForEnd(400);
    }

    @Test
    public void testEndTransitionsBeforeStarted() throws Throwable {
        mTransition.setDuration(400);

        mActivityRule.runOnUiThread(() -> {
            Scene scene = Scene.getSceneForLayout(mSceneRoot, R.layout.scene1, mActivity);
            TransitionManager.go(scene, mTransition);
            TransitionManager.endTransitions(mSceneRoot);
        });
        SystemClock.sleep(100);
        verify(mListener, never()).onTransitionStart(any());
        SystemClock.sleep(10);
        verify(mListener, never()).onTransitionEnd(any());
    }
}

