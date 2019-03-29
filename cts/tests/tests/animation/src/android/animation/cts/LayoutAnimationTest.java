/*
 * Copyright (C) 2012 The Android Open Source Project
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
package android.animation.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.animation.Animator;
import android.animation.LayoutTransition;
import android.animation.LayoutTransition.TransitionListener;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.TimeInterpolator;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.widget.Button;
import android.widget.LinearLayout;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class LayoutAnimationTest {
    private LayoutAnimationActivity mActivity;
    private LayoutTransition mLayoutTransition;
    private LinearLayout mView;
    private Button mButton;

    @Rule
    public ActivityTestRule<LayoutAnimationActivity> mActivityRule =
            new ActivityTestRule<>(LayoutAnimationActivity.class);

    @Before
    public void setup() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(true);
        mActivity = mActivityRule.getActivity();
        mView = (LinearLayout) mActivity.findViewById(R.id.container);
        mButton = (Button)mActivity.findViewById(R.id.button1);
        mLayoutTransition = new LayoutTransition();
    }

    @Test
    public void testAddTransitionListener() throws Throwable {
        MyTransitionListener listener = new MyTransitionListener();
        assertNull(mLayoutTransition.getTransitionListeners());
        mLayoutTransition.addTransitionListener(listener);

        List<TransitionListener> layoutTransitionList = mLayoutTransition.getTransitionListeners();
        TransitionListener actualListener = layoutTransitionList.get(0);
        assertEquals(1, layoutTransitionList.size());
        assertEquals(listener, actualListener);
    }

    @Test
    public void testIsRunning() throws Throwable {
        setDefaultTransition();
        assertFalse(mLayoutTransition.isRunning());
        clickButton();
        assertTrue(mLayoutTransition.isRunning());
    }

    @Test
    public void testIsChangingLayout() throws Throwable {
        long duration = 2000l;
        mView.setLayoutTransition(mLayoutTransition);
        mLayoutTransition.setDuration(duration);
        mLayoutTransition.setInterpolator(LayoutTransition.CHANGE_APPEARING,
                new AccelerateInterpolator());

        assertFalse(mLayoutTransition.isChangingLayout());
        clickButton();
        assertTrue(mLayoutTransition.isChangingLayout());
    }

    @Test
    public void testSetDuration() {
        long duration = 1000l;
        mLayoutTransition.setDuration(duration);

        assertEquals(duration, mLayoutTransition.getDuration(LayoutTransition.APPEARING));
        assertEquals(duration, mLayoutTransition.getDuration(LayoutTransition.CHANGE_APPEARING));
        assertEquals(duration,
                     mLayoutTransition.getDuration(LayoutTransition.CHANGE_DISAPPEARING));
        assertEquals(duration, mLayoutTransition.getDuration(LayoutTransition.DISAPPEARING));
    }

    @Test
    public void testSetDurationForTransitionType() {
        long duration = 1000l;
        mLayoutTransition.setDuration(LayoutTransition.APPEARING, duration);
        assertEquals(duration, mLayoutTransition.getDuration(LayoutTransition.APPEARING));
    }

    @Test
    public void testSetInterpolator() {
        TimeInterpolator interpolator = new AccelerateInterpolator();
        mLayoutTransition.setInterpolator(LayoutTransition.APPEARING, interpolator);
        assertEquals(interpolator, mLayoutTransition.getInterpolator(
                LayoutTransition.APPEARING));
    }

    @Test
    public void testSetAnimator() {
        float startAlpha = 0.0f;
        float endAlpha = 0.5f;
        PropertyValuesHolder pvhAlpha = PropertyValuesHolder.ofFloat("alpha", startAlpha,
                endAlpha);
        ObjectAnimator appearingAnimator = ObjectAnimator.ofPropertyValuesHolder(
                (Object) null, pvhAlpha);
        appearingAnimator.setInterpolator(new AccelerateInterpolator());
        mLayoutTransition.setAnimator(LayoutTransition.APPEARING, appearingAnimator);
        assertEquals(appearingAnimator, mLayoutTransition.getAnimator(LayoutTransition.APPEARING));
    }

    @Test
    public void testAnimationWithAnimator() throws Throwable {
        MyTransitionListener listener = new MyTransitionListener();
        mLayoutTransition.addTransitionListener(listener);
        mLayoutTransition.setAnimateParentHierarchy(false);
        long duration = 2000;
        mView.setLayoutTransition(mLayoutTransition);
        mLayoutTransition.setDuration(duration);
        float startAlpha = 0.0f;
        float endAlpha = 0.5f;
        PropertyValuesHolder pvhAlpha = PropertyValuesHolder.ofFloat("alpha", startAlpha,
                endAlpha);
        ObjectAnimator appearingAnimator = ObjectAnimator.ofPropertyValuesHolder(
                (Object) null, pvhAlpha);
        appearingAnimator.setInterpolator(new AccelerateInterpolator());

        mLayoutTransition.setAnimator(LayoutTransition.APPEARING, appearingAnimator);

        List<Float> alphaList = new LinkedList<>();
        clickButton();
        while (listener.mTransition) {
            float alpha = mActivity.getLastButton().getAlpha();
            alphaList.add(alpha);
            SystemClock.sleep(200);
        }
        Iterator<Float> iterator = alphaList.iterator();
        float lastValue = 0.0f;
        while(iterator.hasNext()){
            float alphaValue = iterator.next();
            assertTrue(alphaValue >= lastValue);
            assertTrue(alphaValue >= startAlpha);
            assertTrue(alphaValue <= endAlpha);
            lastValue = alphaValue;
        }
    }

    @Test
    public void testStartDelay() {
        long delay = 100l;
        int transitionType = LayoutTransition.APPEARING;
        mLayoutTransition.setStartDelay(transitionType, delay);
        assertEquals(delay, mLayoutTransition.getStartDelay(transitionType));
    }

    @Test
    public void testSetStagger() {
        long duration = 100;
        int transitionType = LayoutTransition.CHANGE_APPEARING;
        mLayoutTransition.setStagger(transitionType, duration);
        assertEquals(duration, mLayoutTransition.getStagger(transitionType));
    }

    private void setDefaultTransition() {
        long duration = 1000;
        mView.setLayoutTransition(mLayoutTransition);
        mLayoutTransition.setDuration(duration);
        mLayoutTransition.setInterpolator(LayoutTransition.APPEARING,
                new AccelerateInterpolator());
    }

    private void clickButton() throws Throwable {
        mActivityRule.runOnUiThread(mButton::callOnClick);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    class MyTransitionListener implements LayoutTransition.TransitionListener {
        ViewGroup mContainer;
        View mView;
        int mTransitionType;
        boolean mTransition = false;
        public void endTransition(LayoutTransition transition,
                ViewGroup container, View view, int transitionType) {
            this.mContainer = container;
            this.mView = view;
            this.mTransitionType = transitionType;
            mTransition = false;
        }

        public void startTransition(LayoutTransition transition,
                ViewGroup container, View view, int transitionType) {
            this.mContainer = container;
            this.mView = view;
            this.mTransitionType = transitionType;
            mTransition = true;
        }
    }

    class MyLayoutTransition extends LayoutTransition {
        boolean mAddChild = false;
        boolean mHideChild = false;
        boolean mRemoveChild = false;
        boolean mShowChild = false;
        boolean mSetAnimator = false;
        boolean mRemoveListener = false;
        boolean isChangingLayout = false;

        @Override
        public void addChild(ViewGroup parent, View child) {
            super.addChild(parent, child);
            mAddChild = true;
        }

        @Override
        public void hideChild(ViewGroup parent, View child) {
            super.hideChild(parent, child);
            mHideChild = true;
        }

        @Override
        public boolean isChangingLayout() {
            return super.isChangingLayout();
        }

        @Override
        public boolean isRunning() {
            isChangingLayout =  true;
            return super.isRunning();
        }

        @Override
        public void removeChild(ViewGroup parent, View child) {
            super.removeChild(parent, child);
            mRemoveChild = true;
        }

        @Override
        public void removeTransitionListener(TransitionListener listener) {
            super.removeTransitionListener(listener);
            mRemoveListener = true;
        }

        @Override
        public void setAnimator(int transitionType, Animator animator) {
            super.setAnimator(transitionType, animator);
            mSetAnimator = true;
        }

        @Override
        public void showChild(ViewGroup parent, View child) {
            super.showChild(parent, child);
            mShowChild = true;
        }
    }
}

