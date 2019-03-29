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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.app.Activity;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.AttributeSet;
import android.util.Xml;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewStub;
import android.widget.LinearLayout;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.xmlpull.v1.XmlPullParser;

/**
 * Test {@link ViewStub}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class ViewStubTest {
    private Activity mActivity;

    @Rule
    public ActivityTestRule<ViewStubCtsActivity> mActivityRule =
            new ActivityTestRule<>(ViewStubCtsActivity.class);

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
    }

    @Test
    public void testConstructor() {
        XmlPullParser parser = mActivity.getResources().getXml(R.layout.viewstub_layout);
        AttributeSet attrs = Xml.asAttributeSet(parser);
        assertNotNull(attrs);

        new ViewStub(mActivity);

        new ViewStub(mActivity, 10);

        new ViewStub(mActivity, attrs);

        new ViewStub(mActivity, attrs, 30);
    }

    @Test
    public void testDraw() {
        ViewStub viewStub = new ViewStub(mActivity);
        // if the function draw() does not throw any exception,
        // we think it is right, because it's an empty method.
        viewStub.draw(null);
    }

    @UiThreadTest
    @Test
    public void testSetVisibility() {
        final ViewStub viewStub1 = (ViewStub) mActivity.findViewById(R.id.viewstub);
        final ViewStub.OnInflateListener listener = mock(ViewStub.OnInflateListener.class);
        viewStub1.setOnInflateListener(listener);
        verifyZeroInteractions(listener);
        assertNotNull(viewStub1.getParent());

        // set GONE
        viewStub1.setVisibility(View.GONE);
        assertEquals(View.GONE, viewStub1.getVisibility());
        // does not call inflate
        verifyZeroInteractions(listener);
        assertNotNull(viewStub1.getParent());

        // set VISIBLE
        viewStub1.setVisibility(View.VISIBLE);
        assertEquals(View.VISIBLE, viewStub1.getVisibility());
        // assure the inflate is called
        ArgumentCaptor<View> inflatedViewCaptor = ArgumentCaptor.forClass(View.class);
        verify(listener, times(1)).onInflate(eq(viewStub1), inflatedViewCaptor.capture());
        // We're expecting inflatedId attribute on ViewStub to take precedence over the
        // id attribute defined on the inflated view
        assertEquals(R.id.inflated_id, inflatedViewCaptor.getValue().getId());
        assertNull(viewStub1.getParent());

        // set INVISIBLE when parent is null
        final ViewStub viewStub2 = new ViewStub(mActivity);
        assertNull(viewStub2.getParent());
        try {
            viewStub2.setVisibility(View.INVISIBLE);
            fail("should throw IllegalStateException");
        } catch (IllegalStateException e) {
        }
        assertEquals(View.INVISIBLE, viewStub2.getVisibility());
    }

    @Test
    public void testAccessLayoutResource() {
        ViewStub viewStub = new ViewStub(mActivity);

        viewStub.setLayoutResource(R.layout.viewstub_layout);
        assertEquals(R.layout.viewstub_layout, viewStub.getLayoutResource());

        viewStub.setLayoutResource(0);
        assertEquals(0, viewStub.getLayoutResource());

        viewStub.setLayoutResource(-1);
        assertEquals(-1, viewStub.getLayoutResource());
    }

    @Test
    public void testViewStubHasNoDimensions() {
        ViewStub viewStub = new ViewStub(mActivity);

        viewStub.forceLayout();
        viewStub.measure(200, 300);
        assertEquals(0, viewStub.getMeasuredWidth());
        assertEquals(0, viewStub.getMeasuredHeight());

        viewStub.measure(100, 200);
        assertEquals(0, viewStub.getMeasuredWidth());
        assertEquals(0, viewStub.getMeasuredHeight());
    }

    @UiThreadTest
    @Test
    public void testSetOnInflateListener() {
        final ViewStub viewStub = (ViewStub) mActivity.findViewById(R.id.viewstub);
        final ViewStub.OnInflateListener listener = mock(ViewStub.OnInflateListener.class);

        viewStub.setOnInflateListener(listener);
        verifyZeroInteractions(listener);
        final View inflated = viewStub.inflate();
        verify(listener, times(1)).onInflate(viewStub, inflated);
    }

    @UiThreadTest
    @Test
    public void testSetOnInflateListenerError() {
        final ViewStub viewStub = (ViewStub) mActivity.findViewById(R.id.viewstub);

        viewStub.setOnInflateListener(null);
        viewStub.inflate();
    }

    @Test
    public void testAccessInflatedId() {
        ViewStub viewStub = new ViewStub(mActivity);
        assertEquals("Default ViewStub inflated ID is View.NO_ID",
                View.NO_ID, viewStub.getInflatedId());

        viewStub.setInflatedId(R.id.inflated_id);
        assertEquals("Set ViewStub inflated ID to package resource ID",
                R.id.inflated_id, viewStub.getInflatedId());

        viewStub.setInflatedId(View.NO_ID);
        assertEquals("Set ViewStub inflated ID to View.NO_ID",
                View.NO_ID, viewStub.getInflatedId());
    }

    @UiThreadTest
    @Test
    public void testInflate() {
        final ViewStub viewStub = (ViewStub) mActivity.findViewById(R.id.viewstub);
        final ViewParent vsParent = viewStub.getParent();
        final ViewStub.OnInflateListener listener = mock(ViewStub.OnInflateListener.class);

        viewStub.setOnInflateListener(listener);
        verifyZeroInteractions(listener);
        assertNotNull(vsParent);

        View view = viewStub.inflate();
        assertNotNull(view);
        assertTrue(view instanceof LinearLayout);
        assertEquals(viewStub.getLayoutParams().width, view.getLayoutParams().width);
        assertEquals(viewStub.getLayoutParams().height, view.getLayoutParams().height);
        assertNull(viewStub.getParent());
        assertSame(vsParent, view.getParent());
        assertEquals(R.id.inflated_id, view.getId());
        verify(listener, times(1)).onInflate(viewStub, view);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testInflateErrorInvalidLayoutResource() {
        final ViewStub viewStub = (ViewStub) mActivity.findViewById(R.id.viewstub);

        // mLayoutResource is 0
        viewStub.setLayoutResource(0);
        viewStub.inflate();
    }

    @Test(expected=IllegalStateException.class)
    public void testInflateErrorNullParent() {
        // parent is null
        ViewStub stub = new ViewStub(mActivity);
        assertNull(stub.getParent());
        stub.inflate();
    }
}
