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

package android.widget.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.AttributeSet;
import android.util.Xml;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.ViewSwitcher;
import android.widget.ViewSwitcher.ViewFactory;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;

/**
 * Test {@link ViewSwitcher}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class ViewSwitcherTest {
    private Context mContext;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getTargetContext();
    }

    @Test
    public void testConstructor() {
        new ViewSwitcher(mContext);

        new ViewSwitcher(mContext, null);

        XmlPullParser parser = mContext.getResources().getXml(R.layout.viewswitcher_layout);
        AttributeSet attrs = Xml.asAttributeSet(parser);
        new ViewSwitcher(mContext, attrs);
    }

    @Test
    public void testSetFactory() {
        final ViewSwitcher viewSwitcher = new ViewSwitcher(mContext);

        MockViewFactory factory = new MockViewFactory();
        viewSwitcher.setFactory(factory);
        assertTrue(factory.hasMakeViewCalled());
    }

    @Test
    public void testReset() {
        final ViewSwitcher viewSwitcher = new ViewSwitcher(mContext);

        ListView lv1 = new ListView(mContext);
        ListView lv2 = new ListView(mContext);
        assertEquals(View.VISIBLE, lv1.getVisibility());
        assertEquals(View.VISIBLE, lv2.getVisibility());
        viewSwitcher.addView(lv1, 0);
        viewSwitcher.addView(lv2, 1);

        viewSwitcher.reset();
        assertEquals(View.GONE, lv1.getVisibility());
        assertEquals(View.GONE, lv2.getVisibility());
    }

    @Test
    public void testGetNextView() {
        final ViewSwitcher viewSwitcher = new ViewSwitcher(mContext);

        ListView lv1 = new ListView(mContext);
        ListView lv2 = new ListView(mContext);
        viewSwitcher.addView(lv1, 0, new ViewGroup.LayoutParams(20, 25));
        assertSame(lv1, viewSwitcher.getChildAt(0));
        assertNull(viewSwitcher.getNextView());

        viewSwitcher.addView(lv2, 1, new ViewGroup.LayoutParams(20, 25));
        assertSame(lv2, viewSwitcher.getChildAt(1));
        assertSame(lv2, viewSwitcher.getNextView());

        viewSwitcher.setDisplayedChild(1);
        assertSame(lv1, viewSwitcher.getNextView());

        try {
            ListView lv3 = new ListView(mContext);
            viewSwitcher.addView(lv3, 2, null);
            fail("Should throw IllegalStateException here.");
        } catch (IllegalStateException e) {
        }
    }

    private class MockViewFactory implements ViewFactory {
        private boolean mMakeViewCalled = false;

        public View makeView() {
            mMakeViewCalled = true;
            return new ListView(mContext);
        }

        public boolean hasMakeViewCalled() {
            return mMakeViewCalled;
        }
    }
}
