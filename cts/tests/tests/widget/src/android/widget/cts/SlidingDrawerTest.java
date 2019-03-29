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

package android.widget.cts;

import static com.android.compatibility.common.util.CtsMockitoUtils.within;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.app.Activity;
import android.app.Instrumentation;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.AttributeSet;
import android.util.Xml;
import android.view.View;
import android.widget.ImageView;
import android.widget.SlidingDrawer;
import android.widget.TextView;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/**
 * Test {@link SlidingDrawer}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class SlidingDrawerTest {
    private static final long TEST_TIMEOUT = 5000L;

    private Instrumentation mInstrumentation;
    private Activity mActivity;
    private SlidingDrawer mDrawer;

    @Rule
    public ActivityTestRule<SlidingDrawerCtsActivity> mActivityRule =
            new ActivityTestRule<>(SlidingDrawerCtsActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
        mDrawer = (SlidingDrawer) mActivity.findViewById(R.id.drawer);
    }

    @UiThreadTest
    @Test
    public void testConstructor() throws XmlPullParserException, IOException {
        XmlPullParser parser = mActivity.getResources().getLayout(R.layout.sliding_drawer_layout);
        AttributeSet attrs = Xml.asAttributeSet(parser);

        try {
            new SlidingDrawer(mActivity, attrs);
            fail("did not throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            new SlidingDrawer(mActivity, attrs, 0);
            fail("did not throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testGetHandle() {
        View handle = mDrawer.getHandle();
        assertTrue(handle instanceof ImageView);
        assertEquals(R.id.handle, handle.getId());
    }

    @Test
    public void testGetContent() {
        View content = mDrawer.getContent();
        assertTrue(content instanceof TextView);
        assertEquals(R.id.content, content.getId());
    }

    @UiThreadTest
    @Test
    public void testOpenAndClose() {
        View content = mDrawer.getContent();
        assertFalse(mDrawer.isOpened());
        assertEquals(View.GONE, content.getVisibility());

        mDrawer.open();
        assertTrue(mDrawer.isOpened());
        assertEquals(View.VISIBLE, content.getVisibility());

        mDrawer.close();
        assertFalse(mDrawer.isOpened());
        assertEquals(View.GONE, content.getVisibility());
    }

    @Test
    public void testAnimateOpenAndClose() throws Throwable {
        View content = mDrawer.getContent();
        assertFalse(mDrawer.isMoving());
        assertFalse(mDrawer.isOpened());
        assertEquals(View.GONE, content.getVisibility());

        mActivityRule.runOnUiThread(mDrawer::animateOpen);
        assertTrue(mDrawer.isMoving());
        assertEquals(View.GONE, content.getVisibility());

        PollingCheck.waitFor(() -> !mDrawer.isMoving());
        PollingCheck.waitFor(mDrawer::isOpened);
        assertEquals(View.VISIBLE, content.getVisibility());

        mActivityRule.runOnUiThread(mDrawer::animateClose);
        assertTrue(mDrawer.isMoving());
        assertEquals(View.GONE, content.getVisibility());

        PollingCheck.waitFor(() -> !mDrawer.isMoving());
        PollingCheck.waitFor(() -> !mDrawer.isOpened());
        assertEquals(View.GONE, content.getVisibility());
    }

    @Test
    public void testAnimateToggle() throws Throwable {
        View content = mDrawer.getContent();
        assertFalse(mDrawer.isMoving());
        assertFalse(mDrawer.isOpened());
        assertEquals(View.GONE, content.getVisibility());

        mActivityRule.runOnUiThread(mDrawer::animateToggle);
        assertTrue(mDrawer.isMoving());
        assertEquals(View.GONE, content.getVisibility());

        PollingCheck.waitFor(() -> !mDrawer.isMoving());
        PollingCheck.waitFor(mDrawer::isOpened);
        assertEquals(View.VISIBLE, content.getVisibility());

        mActivityRule.runOnUiThread(mDrawer::animateToggle);
        assertTrue(mDrawer.isMoving());
        assertEquals(View.GONE, content.getVisibility());

        PollingCheck.waitFor(() -> !mDrawer.isMoving());
        PollingCheck.waitFor(() -> !mDrawer.isOpened());
        assertEquals(View.GONE, content.getVisibility());
    }

    @UiThreadTest
    @Test
    public void testToggle() {
        View content = mDrawer.getContent();
        assertFalse(mDrawer.isOpened());
        assertEquals(View.GONE, content.getVisibility());

        mDrawer.toggle();
        assertTrue(mDrawer.isOpened());
        assertEquals(View.VISIBLE, content.getVisibility());

        mDrawer.toggle();
        assertFalse(mDrawer.isOpened());
        assertEquals(View.GONE, content.getVisibility());
    }

    @UiThreadTest
    @Test
    public void testLockAndUnlock() {
        View handle = mDrawer.getHandle();
        View content = mDrawer.getContent();
        assertFalse(mDrawer.isOpened());
        assertEquals(View.GONE, content.getVisibility());

        handle.performClick();
        assertTrue(mDrawer.isOpened());
        assertEquals(View.VISIBLE, content.getVisibility());

        handle.performClick();
        assertFalse(mDrawer.isOpened());
        assertEquals(View.GONE, content.getVisibility());

        mDrawer.lock();
        handle.performClick();
        assertFalse(mDrawer.isOpened());
        assertEquals(View.GONE, content.getVisibility());

        mDrawer.unlock();
        handle.performClick();
        assertTrue(mDrawer.isOpened());
        assertEquals(View.VISIBLE, content.getVisibility());
    }

    @UiThreadTest
    @Test
    public void testSetOnDrawerOpenListener() {
        SlidingDrawer.OnDrawerOpenListener mockOpenListener =
                mock(SlidingDrawer.OnDrawerOpenListener.class);
        mDrawer.setOnDrawerOpenListener(mockOpenListener);

        verifyZeroInteractions(mockOpenListener);

        mDrawer.open();
        verify(mockOpenListener, times(1)).onDrawerOpened();
    }

    @UiThreadTest
    @Test
    public void testSetOnDrawerCloseListener() {
        SlidingDrawer.OnDrawerCloseListener mockCloseListener =
                mock(SlidingDrawer.OnDrawerCloseListener.class);
        mDrawer.setOnDrawerCloseListener(mockCloseListener);

        verifyZeroInteractions(mockCloseListener);

        mDrawer.open();
        verifyZeroInteractions(mockCloseListener);

        mDrawer.close();
        verify(mockCloseListener, times(1)).onDrawerClosed();
    }

    @UiThreadTest
    @Test
    public void testSetOnDrawerScrollListener() {
        final SlidingDrawer drawer = (SlidingDrawer) mActivity.findViewById(R.id.drawer);

        final SlidingDrawer.OnDrawerScrollListener mockScrollListener =
                mock(SlidingDrawer.OnDrawerScrollListener.class);
        drawer.setOnDrawerScrollListener(mockScrollListener);

        drawer.animateOpen();

        verify(mockScrollListener, within(TEST_TIMEOUT)).onScrollStarted();
        verify(mockScrollListener, within(TEST_TIMEOUT)).onScrollEnded();

        InOrder inOrder = inOrder(mockScrollListener);
        inOrder.verify(mockScrollListener).onScrollStarted();
        inOrder.verify(mockScrollListener).onScrollEnded();
        verifyNoMoreInteractions(mockScrollListener);
    }
}
