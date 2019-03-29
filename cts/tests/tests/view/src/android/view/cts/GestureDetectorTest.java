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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class GestureDetectorTest {

    private static final float X_3F = 3.0f;
    private static final float Y_4F = 4.0f;

    private GestureDetectorCtsActivity mActivity;
    private GestureDetector mGestureDetector;
    private GestureDetector.SimpleOnGestureListener mListener;

    private long mDownTime;
    private long mEventTime;
    private MotionEvent mButtonPressPrimaryMotionEvent;
    private MotionEvent mButtonPressSecondaryMotionEvent;

    @Rule
    public ActivityTestRule<GestureDetectorCtsActivity> mActivityRule =
            new ActivityTestRule<>(GestureDetectorCtsActivity.class);

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
        mGestureDetector = mActivity.getGestureDetector();
        mListener = mActivity.getListener();

        mDownTime = SystemClock.uptimeMillis();
        mEventTime = SystemClock.uptimeMillis();
        mButtonPressPrimaryMotionEvent = MotionEvent.obtain(mDownTime, mEventTime,
                MotionEvent.ACTION_BUTTON_PRESS, X_3F, Y_4F, 0);
        mButtonPressPrimaryMotionEvent.setActionButton(MotionEvent.BUTTON_STYLUS_PRIMARY);

        mButtonPressSecondaryMotionEvent = MotionEvent.obtain(mDownTime, mEventTime,
                MotionEvent.ACTION_BUTTON_PRESS, X_3F, Y_4F, 0);
        mButtonPressSecondaryMotionEvent.setActionButton(MotionEvent.BUTTON_SECONDARY);
    }

    @UiThreadTest
    @Test
    public void testConstructor() {
        new GestureDetector(
                mActivity, new SimpleOnGestureListener(), new Handler(Looper.getMainLooper()));
        new GestureDetector(mActivity, new SimpleOnGestureListener());
        new GestureDetector(new SimpleOnGestureListener(), new Handler(Looper.getMainLooper()));
        new GestureDetector(new SimpleOnGestureListener());
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testConstructorNullListener() {
        new GestureDetector(null);
    }

    @Test
    public void testLongpressEnabled() {
        mGestureDetector.setIsLongpressEnabled(true);
        assertTrue(mGestureDetector.isLongpressEnabled());
        mGestureDetector.setIsLongpressEnabled(false);
        assertFalse(mGestureDetector.isLongpressEnabled());
    }

    @Test
    public void testOnSetContextClickListener() {
        mGestureDetector.setContextClickListener(null);
        mGestureDetector.onGenericMotionEvent(mButtonPressPrimaryMotionEvent);
        verify(mListener, never()).onContextClick(any(MotionEvent.class));

        mGestureDetector.setContextClickListener(mListener);
        mGestureDetector.onGenericMotionEvent(mButtonPressPrimaryMotionEvent);
        verify(mListener, times(1)).onContextClick(mButtonPressPrimaryMotionEvent);
    }

    @Test
    public void testOnContextClick() {
        mListener.onContextClick(mButtonPressPrimaryMotionEvent);
        verify(mListener, times(1)).onContextClick(mButtonPressPrimaryMotionEvent);

        mGestureDetector.onGenericMotionEvent(mButtonPressSecondaryMotionEvent);
        verify(mListener, times(1)).onContextClick(mButtonPressSecondaryMotionEvent);
    }

    @Test
    public void testOnGenericMotionEvent() {
        mGestureDetector.onGenericMotionEvent(mButtonPressPrimaryMotionEvent);
        verify(mListener, times(1)).onContextClick(mButtonPressPrimaryMotionEvent);
    }
}
