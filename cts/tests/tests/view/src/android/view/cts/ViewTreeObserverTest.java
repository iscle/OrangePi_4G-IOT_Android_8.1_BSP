/*
 * Copyright (C) 2008 The Android Open Source Project
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

import static com.android.compatibility.common.util.CtsMockitoUtils.within;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.android.compatibility.common.util.CtsTouchUtils;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class ViewTreeObserverTest {
    private static int TIMEOUT_MS = 2000;

    private Instrumentation mInstrumentation;
    private Activity mActivity;
    private ViewTreeObserver mViewTreeObserver;

    private LinearLayout mLinearLayout;
    private Button mButton;

    @Rule
    public ActivityTestRule<MockActivity> mActivityRule =
            new ActivityTestRule<>(MockActivity.class);

    @Before
    public void setup() throws Throwable {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
        PollingCheck.waitFor(mActivity::hasWindowFocus);
        layout(R.layout.viewtreeobserver_layout);

        mLinearLayout = (LinearLayout) mActivity.findViewById(R.id.linearlayout);
        mButton = (Button) mActivity.findViewById(R.id.button1);
    }

    private void layout(final int layoutId) throws Throwable {
        mActivityRule.runOnUiThread(() -> mActivity.setContentView(layoutId));
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testAddOnGlobalFocusChangeListener() throws Throwable {
        final View view1 = mActivity.findViewById(R.id.view1);
        final View view2 = mActivity.findViewById(R.id.view2);

        mActivityRule.runOnUiThread(view1::requestFocus);

        mViewTreeObserver = mLinearLayout.getViewTreeObserver();
        final ViewTreeObserver.OnGlobalFocusChangeListener listener =
                mock(ViewTreeObserver.OnGlobalFocusChangeListener.class);
        mViewTreeObserver.addOnGlobalFocusChangeListener(listener);

        mActivityRule.runOnUiThread(view2::requestFocus);
        mInstrumentation.waitForIdleSync();
        verify(listener, within(TIMEOUT_MS)).onGlobalFocusChanged(view1, view2);
    }

    @Test
    public void testAddOnGlobalLayoutListener() {
        mViewTreeObserver = mLinearLayout.getViewTreeObserver();

        final ViewTreeObserver.OnGlobalLayoutListener listener =
                mock(ViewTreeObserver.OnGlobalLayoutListener.class);
        mViewTreeObserver.addOnGlobalLayoutListener(listener);
        mViewTreeObserver.dispatchOnGlobalLayout();
        verify(listener, times(1)).onGlobalLayout();
    }

    @Test
    public void testAddOnPreDrawListener() {
        mViewTreeObserver = mLinearLayout.getViewTreeObserver();

        final ViewTreeObserver.OnPreDrawListener listener =
                mock(ViewTreeObserver.OnPreDrawListener.class);
        mViewTreeObserver.addOnPreDrawListener(listener);
        mViewTreeObserver.dispatchOnPreDraw();
        verify(listener, times(1)).onPreDraw();
    }

    @Test
    public void testAddOnDrawListener() {
        mViewTreeObserver = mLinearLayout.getViewTreeObserver();

        final ViewTreeObserver.OnDrawListener listener =
                mock(ViewTreeObserver.OnDrawListener.class);
        mViewTreeObserver.addOnDrawListener(listener);
        mViewTreeObserver.dispatchOnDraw();
        verify(listener, times(1)).onDraw();
    }

    @Test(expected=IllegalStateException.class)
    public void testRemoveOnDrawListenerInDispatch() {
        final View view = new View(mActivity);
        mViewTreeObserver = view.getViewTreeObserver();

        final ViewTreeObserver.OnDrawListener listener =
                new ViewTreeObserver.OnDrawListener() {
                    @Override
                    public void onDraw() {
                        mViewTreeObserver.removeOnDrawListener(this);
                    }
                };
        mViewTreeObserver.addOnDrawListener(listener);
        mViewTreeObserver.dispatchOnDraw();
    }

    @Test
    public void testAddOnTouchModeChangeListener() throws Throwable {
        // let the button be touch mode.
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mButton);

        mViewTreeObserver = mButton.getViewTreeObserver();

        final ViewTreeObserver.OnTouchModeChangeListener listener =
                mock(ViewTreeObserver.OnTouchModeChangeListener.class);
        mViewTreeObserver.addOnTouchModeChangeListener(listener);

        mActivityRule.runOnUiThread(mButton::requestFocusFromTouch);
        mInstrumentation.waitForIdleSync();

        verify(listener, within(TIMEOUT_MS)).onTouchModeChanged(anyBoolean());
    }

    @Test
    public void testIsAlive() {
        mViewTreeObserver = mLinearLayout.getViewTreeObserver();
        assertTrue(mViewTreeObserver.isAlive());
    }

    @LargeTest
    @Test
    public void testRemoveGlobalOnLayoutListener() {
        mViewTreeObserver = mLinearLayout.getViewTreeObserver();

        final ViewTreeObserver.OnGlobalLayoutListener listener =
                mock(ViewTreeObserver.OnGlobalLayoutListener.class);
        mViewTreeObserver.addOnGlobalLayoutListener(listener);
        mViewTreeObserver.dispatchOnGlobalLayout();
        verify(listener, times(1)).onGlobalLayout();

        reset(listener);
        mViewTreeObserver.removeGlobalOnLayoutListener(listener);
        mViewTreeObserver.dispatchOnGlobalLayout();
        // Since we've unregistered our listener, we expect it to not be called even after
        // we've waited for a couple of seconds
        SystemClock.sleep(TIMEOUT_MS);
        verifyZeroInteractions(listener);
    }

    @LargeTest
    @Test
    public void testRemoveOnGlobalLayoutListener() {
        mViewTreeObserver = mLinearLayout.getViewTreeObserver();

        final ViewTreeObserver.OnGlobalLayoutListener listener =
                mock(ViewTreeObserver.OnGlobalLayoutListener.class);
        mViewTreeObserver.addOnGlobalLayoutListener(listener);
        mViewTreeObserver.dispatchOnGlobalLayout();
        verify(listener, times(1)).onGlobalLayout();

        reset(listener);
        mViewTreeObserver.removeOnGlobalLayoutListener(listener);
        mViewTreeObserver.dispatchOnGlobalLayout();
        // Since we've unregistered our listener, we expect it to not be called even after
        // we've waited for a couple of seconds
        SystemClock.sleep(TIMEOUT_MS);
        verifyZeroInteractions(listener);
    }

    @LargeTest
    @Test
    public void testRemoveOnGlobalFocusChangeListener() throws Throwable {
        final View view1 = mActivity.findViewById(R.id.view1);
        final View view2 = mActivity.findViewById(R.id.view2);

        mActivityRule.runOnUiThread(view1::requestFocus);

        mViewTreeObserver = mLinearLayout.getViewTreeObserver();
        final ViewTreeObserver.OnGlobalFocusChangeListener listener =
                mock(ViewTreeObserver.OnGlobalFocusChangeListener.class);
        mViewTreeObserver.addOnGlobalFocusChangeListener(listener);
        mActivityRule.runOnUiThread(view2::requestFocus);
        mInstrumentation.waitForIdleSync();
        verify(listener, within(TIMEOUT_MS)).onGlobalFocusChanged(view1, view2);

        reset(listener);
        mViewTreeObserver.removeOnGlobalFocusChangeListener(listener);
        mActivityRule.runOnUiThread(view1::requestFocus);
        mInstrumentation.waitForIdleSync();
        // Since we've unregistered our listener, we expect it to not be called even after
        // we've waited for a couple of seconds
        SystemClock.sleep(TIMEOUT_MS);
        verifyZeroInteractions(listener);
    }

    @LargeTest
    @Test
    public void testRemoveOnPreDrawListener() {
        mViewTreeObserver = mLinearLayout.getViewTreeObserver();

        final ViewTreeObserver.OnPreDrawListener listener =
                mock(ViewTreeObserver.OnPreDrawListener.class);
        mViewTreeObserver.addOnPreDrawListener(listener);
        mViewTreeObserver.dispatchOnPreDraw();
        verify(listener, times(1)).onPreDraw();

        reset(listener);
        mViewTreeObserver.removeOnPreDrawListener(listener);
        mViewTreeObserver.dispatchOnPreDraw();
        // Since we've unregistered our listener, we expect it to not be called even after
        // we've waited for a couple of seconds
        SystemClock.sleep(TIMEOUT_MS);
        verifyZeroInteractions(listener);
    }

    @LargeTest
    @Test
    public void testRemoveOnTouchModeChangeListener() throws Throwable {
        // let the button be touch mode.
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mButton);

        mViewTreeObserver = mButton.getViewTreeObserver();

        final ViewTreeObserver.OnTouchModeChangeListener listener =
                mock(ViewTreeObserver.OnTouchModeChangeListener.class);
        mViewTreeObserver.addOnTouchModeChangeListener(listener);
        mActivityRule.runOnUiThread(mButton::requestFocusFromTouch);
        mInstrumentation.waitForIdleSync();

        verify(listener, within(TIMEOUT_MS)).onTouchModeChanged(anyBoolean());

        reset(listener);
        mViewTreeObserver.removeOnTouchModeChangeListener(listener);
        mActivityRule.runOnUiThread(mButton::requestFocusFromTouch);
        mInstrumentation.waitForIdleSync();

        // Since we've unregistered our listener we expect it to not be called even after
        // we've waited for a couple of seconds
        SystemClock.sleep(TIMEOUT_MS);
        verifyZeroInteractions(listener);
    }

    @LargeTest
    @Test
    public void testAccessOnScrollChangedListener() throws Throwable {
        layout(R.layout.scrollview_layout);
        final ScrollView scrollView = (ScrollView) mActivity.findViewById(R.id.scroll_view);

        mViewTreeObserver = scrollView.getViewTreeObserver();

        final ViewTreeObserver.OnScrollChangedListener listener =
                mock(ViewTreeObserver.OnScrollChangedListener.class);
        mViewTreeObserver.addOnScrollChangedListener(listener);

        mActivityRule.runOnUiThread(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        mInstrumentation.waitForIdleSync();
        verify(listener, within(TIMEOUT_MS)).onScrollChanged();

        reset(listener);

        mViewTreeObserver.removeOnScrollChangedListener(listener);
        mActivityRule.runOnUiThread(() -> scrollView.fullScroll(View.FOCUS_UP));
        // Since we've unregistered our listener, we expect it to not be called even after
        // we've waited for a couple of seconds
        SystemClock.sleep(TIMEOUT_MS);
        verifyZeroInteractions(listener);
    }
}
