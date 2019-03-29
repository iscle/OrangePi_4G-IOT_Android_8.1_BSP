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

package android.graphics.drawable.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.Color;
import android.graphics.cts.R;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.DrawableContainer.DrawableContainerState;
import android.graphics.drawable.LevelListDrawable;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.Xml;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class LevelListDrawableTest {
    private MockLevelListDrawable mLevelListDrawable;

    private Resources mResources;

    private DrawableContainerState mDrawableContainerState;

    @Before
    public void setup() {
        mResources = InstrumentationRegistry.getTargetContext().getResources();
        mLevelListDrawable = new MockLevelListDrawable();
        mDrawableContainerState = (DrawableContainerState) mLevelListDrawable.getConstantState();
    }

    @Test
    public void testLevelListDrawable() {
        new LevelListDrawable();
        // Check the values set in the constructor
        assertNotNull(new LevelListDrawable().getConstantState());
        assertTrue(new MockLevelListDrawable().hasCalledOnLevelChanged());
    }

    @Test
    public void testAddLevel() {
        assertEquals(0, mDrawableContainerState.getChildCount());

        // nothing happens if drawable is null
        mLevelListDrawable.reset();
        mLevelListDrawable.addLevel(0, 0, null);
        assertEquals(0, mDrawableContainerState.getChildCount());
        assertFalse(mLevelListDrawable.hasCalledOnLevelChanged());

        // call onLevelChanged to assure that the correct drawable is selected.
        mLevelListDrawable.reset();
        mLevelListDrawable.addLevel(Integer.MAX_VALUE, Integer.MIN_VALUE,
                new ColorDrawable(Color.GREEN));
        assertEquals(1, mDrawableContainerState.getChildCount());
        assertTrue(mLevelListDrawable.hasCalledOnLevelChanged());

        mLevelListDrawable.reset();
        mLevelListDrawable.addLevel(Integer.MIN_VALUE, Integer.MAX_VALUE,
                new ColorDrawable(Color.RED));
        assertEquals(2, mDrawableContainerState.getChildCount());
        assertTrue(mLevelListDrawable.hasCalledOnLevelChanged());
    }

    @Test
    public void testOnLevelChange() {
        mLevelListDrawable.addLevel(0, 0, new ColorDrawable(Color.BLUE));
        mLevelListDrawable.addLevel(0, 0, new ColorDrawable(Color.MAGENTA));
        mLevelListDrawable.addLevel(0, 10, new ColorDrawable(Color.YELLOW));

        // the method is not called if same level is set
        mLevelListDrawable.reset();
        mLevelListDrawable.setLevel(mLevelListDrawable.getLevel());
        assertFalse(mLevelListDrawable.hasCalledOnLevelChanged());

        // the method is called if different level is set
        mLevelListDrawable.reset();
        mLevelListDrawable.setLevel(mLevelListDrawable.getLevel() - 1);
        assertTrue(mLevelListDrawable.hasCalledOnLevelChanged());

        // check that correct drawable is selected.
        assertTrue(mLevelListDrawable.onLevelChange(10));
        assertSame(mLevelListDrawable.getCurrent(), mDrawableContainerState.getChildren()[2]);

        assertFalse(mLevelListDrawable.onLevelChange(5));
        assertSame(mLevelListDrawable.getCurrent(), mDrawableContainerState.getChildren()[2]);

        assertTrue(mLevelListDrawable.onLevelChange(0));
        assertSame(mLevelListDrawable.getCurrent(), mDrawableContainerState.getChildren()[0]);

        assertTrue(mLevelListDrawable.onLevelChange(100));
        assertNull(mLevelListDrawable.getCurrent());

        assertFalse(mLevelListDrawable.onLevelChange(101));
        assertNull(mLevelListDrawable.getCurrent());
    }

    @Test
    public void testInflateResources() throws XmlPullParserException, IOException {
        getResourceParser(R.xml.level_list_correct);
        getResourceParser(R.xml.level_list_missing_item_drawable);
    }

    @Test
    public void testInflate() throws XmlPullParserException, IOException {
        XmlResourceParser parser = getResourceParser(R.xml.level_list_correct);

        mLevelListDrawable.reset();
        mLevelListDrawable.inflate(mResources, parser, Xml.asAttributeSet(parser));
        assertTrue(mLevelListDrawable.hasCalledOnLevelChanged());
        assertEquals(2, mDrawableContainerState.getChildCount());
        // check the android:minLevel and android:maxLevel by calling setLevel
        mLevelListDrawable.setLevel(200);
        assertSame(mLevelListDrawable.getCurrent(), mDrawableContainerState.getChildren()[0]);
        mLevelListDrawable.setLevel(201);
        assertSame(mLevelListDrawable.getCurrent(), mDrawableContainerState.getChildren()[1]);

        mLevelListDrawable.setLevel(0);
        assertNull(mLevelListDrawable.getCurrent());
        mLevelListDrawable.reset();
        parser = getResourceParser(R.xml.level_list_missing_item_minlevel_maxlevel);
        mLevelListDrawable.inflate(mResources, parser, Xml.asAttributeSet(parser));
        assertTrue(mLevelListDrawable.hasCalledOnLevelChanged());
        assertEquals(3, mDrawableContainerState.getChildCount());
        // default value of android:minLevel and android:maxLevel are both 0
        assertSame(mLevelListDrawable.getCurrent(), mDrawableContainerState.getChildren()[2]);
        mLevelListDrawable.setLevel(1);
        assertNull(mLevelListDrawable.getCurrent());
    }

    @Test(expected=XmlPullParserException.class)
    public void testInflateMissingContent() throws XmlPullParserException, IOException {
        XmlResourceParser parser = getResourceParser(R.xml.level_list_missing_item_drawable);
        // Should throw XmlPullParserException if drawable of item is missing
        mLevelListDrawable.inflate(mResources, parser, Xml.asAttributeSet(parser));
    }

    @Test(expected=NullPointerException.class)
    public void testInflateWithNullResources() throws XmlPullParserException, IOException {
        XmlResourceParser parser = getResourceParser(R.xml.level_list_correct);
        // Should throw NullPointerException if resource is null
        mLevelListDrawable.inflate(null, parser, Xml.asAttributeSet(parser));
    }


    @Test(expected=NullPointerException.class)
    public void testInflateWithNullParser() throws XmlPullParserException, IOException {
        XmlResourceParser parser = getResourceParser(R.xml.level_list_correct);
        // Should throw NullPointerException if parser is null
        mLevelListDrawable.inflate(mResources, null, Xml.asAttributeSet(parser));
    }

    @Test(expected=NullPointerException.class)
    public void testInflateWithNullAttrSet() throws XmlPullParserException, IOException {
        XmlResourceParser parser = getResourceParser(R.xml.level_list_correct);
        // Should throw NullPointerException if AttributeSet is null
        mLevelListDrawable.inflate(mResources, parser, null);
    }

    @Test
    public void testMutate() throws InterruptedException {
        LevelListDrawable d1 =
            (LevelListDrawable) mResources.getDrawable(R.drawable.levellistdrawable);
        LevelListDrawable d2 =
            (LevelListDrawable) mResources.getDrawable(R.drawable.levellistdrawable);
        LevelListDrawable d3 =
            (LevelListDrawable) mResources.getDrawable(R.drawable.levellistdrawable);

        // the state does not appear to be shared before calling mutate()
        d1.addLevel(100, 200, mResources.getDrawable(R.drawable.testimage));
        assertEquals(3, ((DrawableContainerState) d1.getConstantState()).getChildCount());
        assertEquals(2, ((DrawableContainerState) d2.getConstantState()).getChildCount());
        assertEquals(2, ((DrawableContainerState) d3.getConstantState()).getChildCount());

        // simply call mutate to make sure no exception is thrown
        d1.mutate();
    }

    private XmlResourceParser getResourceParser(int resId) throws XmlPullParserException,
            IOException {
        XmlResourceParser parser = mResources.getXml(resId);
        int type;
        while ((type = parser.next()) != XmlPullParser.START_TAG
                && type != XmlPullParser.END_DOCUMENT) {
            // Empty loop
        }
        return parser;
    }

    private class MockLevelListDrawable extends LevelListDrawable {
        private boolean mHasCalledOnLevelChanged;

        public boolean hasCalledOnLevelChanged() {
            return mHasCalledOnLevelChanged;
        }

        public void reset() {
            mHasCalledOnLevelChanged = false;
        }

        @Override
        protected boolean onLevelChange(int level) {
            boolean result = super.onLevelChange(level);
            mHasCalledOnLevelChanged = true;
            return result;
        }
    }
}
