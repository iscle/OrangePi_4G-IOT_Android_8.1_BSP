/*
 * Copyright (C) 2009 The Android Open Source Project
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

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.app.Instrumentation;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.MotionEvent;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class TouchDelegateTest {
    private Instrumentation mInstrumentation;
    private Activity mActivity;
    private Button mButton;

    @Rule
    public ActivityTestRule<MockActivity> mActivityRule =
            new ActivityTestRule<>(MockActivity.class);

    @Before
    public void setup() throws Throwable {
        mActivity = mActivityRule.getActivity();
        mInstrumentation = InstrumentationRegistry.getInstrumentation();

        mButton = new Button(mActivity);
        mActivityRule.runOnUiThread(() -> mActivity.addContentView(
                mButton, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)));
        mInstrumentation.waitForIdleSync();
    }

    @UiThreadTest
    @Test
    public void testOnTouchEvent() {
        // test callback of onTouchEvent
        final View view = new View(mActivity);
        final TouchDelegate touchDelegate = mock(TouchDelegate.class);
        view.setTouchDelegate(touchDelegate);

        final int xInside = (mButton.getLeft() + mButton.getRight()) / 3;
        final int yInside = (mButton.getTop() + mButton.getBottom()) / 3;

        view.onTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, xInside, yInside, 0));
        verify(touchDelegate, times(1)).onTouchEvent(any(MotionEvent.class));
    }
}
