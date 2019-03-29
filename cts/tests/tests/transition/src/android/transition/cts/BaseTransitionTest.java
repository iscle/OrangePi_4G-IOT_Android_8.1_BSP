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

import static com.android.compatibility.common.util.CtsMockitoUtils.within;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.app.Instrumentation;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;
import android.transition.Scene;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.transition.TransitionValues;
import android.transition.Visibility;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.android.compatibility.common.util.WidgetTestUtils;

import org.junit.Before;
import org.junit.Rule;

import java.util.ArrayList;

public abstract class BaseTransitionTest {
    protected Instrumentation mInstrumentation;
    protected TransitionActivity mActivity;
    protected FrameLayout mSceneRoot;
    private float mAnimatedValue;
    protected ArrayList<View> mTargets = new ArrayList<>();
    protected Transition mTransition;
    protected Transition.TransitionListener mListener;

    @Rule
    public ActivityTestRule<TransitionActivity> mActivityRule =
            new ActivityTestRule<>(TransitionActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mInstrumentation.setInTouchMode(false);
        mActivity = mActivityRule.getActivity();
        mSceneRoot = (FrameLayout) mActivity.findViewById(R.id.container);
        mTargets.clear();
        mTransition = new TestTransition();
        mListener = mock(Transition.TransitionListener.class);
        mTransition.addListener(mListener);
    }

    protected void waitForStart() throws InterruptedException {
        waitForStart(mListener);
    }

    protected static void waitForStart(Transition.TransitionListener listener) {
        verify(listener, within(4000)).onTransitionStart(any());
    }

    protected void waitForEnd(long waitMillis) {
        waitForEnd(mListener, waitMillis);
        mInstrumentation.waitForIdleSync();
    }

    protected static void waitForEnd(Transition.TransitionListener listener, long waitMillis) {
        if (waitMillis == 0) {
            verify(listener, times(1)).onTransitionEnd(any());
        } else {
            verify(listener, within(waitMillis)).onTransitionEnd(any());
        }
    }

    protected View loadLayout(final int layout) throws Throwable {
        View[] root = new View[1];

        mActivityRule.runOnUiThread(
                () -> root[0] = mActivity.getLayoutInflater().inflate(layout, mSceneRoot, false));

        return root[0];
    }

    protected Scene loadScene(final View layout) throws Throwable {
        final Scene[] scene = new Scene[1];
        mActivityRule.runOnUiThread(() -> scene[0] = new Scene(mSceneRoot, layout));

        return scene[0];
    }

    protected Scene loadScene(final int layoutId) throws Throwable {
        final Scene scene[] = new Scene[1];
        mActivityRule.runOnUiThread(
                () -> scene[0] = Scene.getSceneForLayout(mSceneRoot, layoutId, mActivity));
        return scene[0];
    }

    protected void startTransition(final int layoutId) throws Throwable {
        startTransition(loadScene(layoutId));
    }

    protected void startTransition(final Scene scene) throws Throwable {
        mActivityRule.runOnUiThread(() -> TransitionManager.go(scene, mTransition));
        waitForStart();
    }

    protected void endTransition() throws Throwable {
        mActivityRule.runOnUiThread(() -> TransitionManager.endTransitions(mSceneRoot));
    }

    protected void enterScene(final int layoutId) throws Throwable {
        enterScene(loadScene(layoutId));
    }

    protected void enterScene(final Scene scene) throws Throwable {
        WidgetTestUtils.runOnMainAndLayoutSync(mActivityRule, scene::enter, false);
    }

    protected void exitScene(final Scene scene) throws Throwable {
        mActivityRule.runOnUiThread(scene::exit);
        mInstrumentation.waitForIdleSync();
    }

    protected void resetListener() {
        mTransition.removeListener(mListener);
        mListener = mock(Transition.TransitionListener.class);
        mTransition.addListener(mListener);
    }

    public void setAnimatedValue(float animatedValue) {
        mAnimatedValue = animatedValue;
    }

    public class TestTransition extends Visibility {

        public TestTransition() {
        }

        @Override
        public Animator onAppear(ViewGroup sceneRoot, View view, TransitionValues startValues,
                TransitionValues endValues) {
            mTargets.add(endValues.view);
            return ObjectAnimator.ofFloat(BaseTransitionTest.this, "animatedValue", 0, 1);
        }

        @Override
        public Animator onDisappear(ViewGroup sceneRoot, View view, TransitionValues startValues,
                TransitionValues endValues) {
            mTargets.add(startValues.view);
            return ObjectAnimator.ofFloat(BaseTransitionTest.this, "animatedValue", 1, 0);
        }
    }
}
