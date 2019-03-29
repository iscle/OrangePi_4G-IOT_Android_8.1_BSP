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
import static org.junit.Assert.fail;

import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.Color;
import android.graphics.cts.R;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable.ConstantState;
import android.graphics.drawable.DrawableContainer.DrawableContainerState;
import android.graphics.drawable.StateListDrawable;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.StateSet;
import android.util.Xml;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class StateListDrawableTest {
    private MockStateListDrawable mMockDrawable;

    private StateListDrawable mDrawable;

    private Resources mResources;

    private DrawableContainerState mDrawableContainerState;

    @Before
    public void setup() {
        // While the two fields point to the same object, the second one is there to
        // workaround the bug in CTS coverage tool that is not recognizing calls on
        // subclasses.
        mDrawable = mMockDrawable = new MockStateListDrawable();
        mDrawableContainerState = (DrawableContainerState) mMockDrawable.getConstantState();
        mResources = InstrumentationRegistry.getTargetContext().getResources();
    }

    @Test
    public void testStateListDrawable() {
        new StateListDrawable();
        // Check the values set in the constructor
        assertNotNull(new StateListDrawable().getConstantState());
        assertTrue(new MockStateListDrawable().hasCalledOnStateChanged());
    }

    @Test
    public void testAddState() {
        assertEquals(0, mDrawableContainerState.getChildCount());

        // nothing happens if drawable is null
        mMockDrawable.reset();
        mDrawable.addState(StateSet.WILD_CARD, null);
        assertEquals(0, mDrawableContainerState.getChildCount());
        assertFalse(mMockDrawable.hasCalledOnStateChanged());

        // call onLevelChanged to assure that the correct drawable is selected.
        mMockDrawable.reset();
        mDrawable.addState(StateSet.WILD_CARD, new ColorDrawable(Color.YELLOW));
        assertEquals(1, mDrawableContainerState.getChildCount());
        assertTrue(mMockDrawable.hasCalledOnStateChanged());

        mMockDrawable.reset();
        mDrawable.addState(
                new int[] { android.R.attr.state_focused, - android.R.attr.state_selected },
                new ColorDrawable(Color.YELLOW));
        assertEquals(2, mDrawableContainerState.getChildCount());
        assertTrue(mMockDrawable.hasCalledOnStateChanged());

        // call onLevelChanged will not throw NPE here because the first drawable with wild card
        // state is matched first. There is no chance that other drawables will be matched.
        mMockDrawable.reset();
        mDrawable.addState(null, new ColorDrawable(Color.YELLOW));
        assertEquals(3, mDrawableContainerState.getChildCount());
        assertTrue(mMockDrawable.hasCalledOnStateChanged());
    }

    @Test
    public void testIsStateful() {
        assertTrue(new StateListDrawable().isStateful());
    }

    @Test
    public void testOnStateChange() {
        mMockDrawable.addState(
                new int[] { android.R.attr.state_focused, - android.R.attr.state_selected },
                new ColorDrawable(Color.YELLOW));
        mMockDrawable.addState(StateSet.WILD_CARD, new ColorDrawable(Color.YELLOW));
        mMockDrawable.addState(StateSet.WILD_CARD, new ColorDrawable(Color.YELLOW));

        // the method is not called if same state is set
        mMockDrawable.reset();
        mMockDrawable.setState(mMockDrawable.getState());
        assertFalse(mMockDrawable.hasCalledOnStateChanged());

        // the method is called if different state is set
        mMockDrawable.reset();
        mMockDrawable.setState(
                new int[] { android.R.attr.state_focused, - android.R.attr.state_selected });
        assertTrue(mMockDrawable.hasCalledOnStateChanged());

        mMockDrawable.reset();
        mMockDrawable.setState(null);
        assertTrue(mMockDrawable.hasCalledOnStateChanged());

        // check that correct drawable is selected.
        mMockDrawable.onStateChange(
                new int[] { android.R.attr.state_focused, - android.R.attr.state_selected });
        assertSame(mMockDrawable.getCurrent(), mDrawableContainerState.getChildren()[0]);

        assertFalse(mMockDrawable.onStateChange(new int[] { android.R.attr.state_focused }));
        assertSame(mMockDrawable.getCurrent(), mDrawableContainerState.getChildren()[0]);

        assertTrue(mMockDrawable.onStateChange(StateSet.WILD_CARD));
        assertSame(mMockDrawable.getCurrent(), mDrawableContainerState.getChildren()[1]);

        // null state will match the wild card
        assertFalse(mMockDrawable.onStateChange(null));
        assertSame(mMockDrawable.getCurrent(), mDrawableContainerState.getChildren()[1]);
    }

    @Test
    public void testOnStateChangeWithWildCardAtFirst() {
        mMockDrawable.addState(StateSet.WILD_CARD, new ColorDrawable(Color.YELLOW));
        mMockDrawable.addState(
                new int[] { android.R.attr.state_focused, - android.R.attr.state_selected },
                new ColorDrawable(Color.YELLOW));

        // matches the first wild card although the second one is more accurate
        mMockDrawable.onStateChange(
                new int[] { android.R.attr.state_focused, - android.R.attr.state_selected });
        assertSame(mMockDrawable.getCurrent(), mDrawableContainerState.getChildren()[0]);
    }

    @Test
    public void testOnStateChangeWithNullStateSet() {
        assertEquals(0, mDrawableContainerState.getChildCount());
        try {
            mMockDrawable.addState(null, new ColorDrawable(Color.YELLOW));
            fail("Should throw NullPointerException.");
        } catch (NullPointerException e) {
        }
        assertEquals(1, mDrawableContainerState.getChildCount());

        try {
            mMockDrawable.onStateChange(StateSet.WILD_CARD);
            fail("Should throw NullPointerException.");
        } catch (NullPointerException e) {
        }
    }

    @Test
    public void testPreloadDensity() throws XmlPullParserException, IOException {
        verifyPreloadDensityTestForDrawable(R.drawable.state_list_density, false);
    }

    @Test
    public void testPreloadDensityConstantSize() throws XmlPullParserException, IOException {
        verifyPreloadDensityTestForDrawable(R.drawable.state_list_density_constant_size, true);
    }

    private void verifyPreloadDensityTestForDrawable(int drawableResId, boolean isConstantSize)
            throws XmlPullParserException, IOException {
        final Resources res = mResources;
        final int densityDpi = res.getConfiguration().densityDpi;
        try {
            verifyPreloadDensityTestForDrawableInner(res, densityDpi, drawableResId, isConstantSize);
        } finally {
            DrawableTestUtils.setResourcesDensity(res, densityDpi);
        }
    }

    private void verifyPreloadDensityTestForDrawableInner(Resources res, int densityDpi,
            int drawableResId, boolean isConstantSize) throws XmlPullParserException, IOException {
        // Capture initial state at default density.
        final XmlResourceParser parser = getResourceParser(drawableResId);
        final StateListDrawable preloadedDrawable = new StateListDrawable();
        preloadedDrawable.inflate(res, parser, Xml.asAttributeSet(parser));
        final ConstantState preloadedConstantState = preloadedDrawable.getConstantState();
        preloadedDrawable.selectDrawable(0);
        final int tempWidth0 = preloadedDrawable.getIntrinsicWidth();
        preloadedDrawable.selectDrawable(1);
        final int tempWidth1 = preloadedDrawable.getIntrinsicWidth();

        // Pick comparison widths based on constant size.
        final int origWidth0;
        final int origWidth1;
        if (isConstantSize) {
            origWidth0 = Math.max(tempWidth0, tempWidth1);
            origWidth1 = origWidth0;
        } else {
            origWidth0 = tempWidth0;
            origWidth1 = tempWidth1;
        }

        // Set density to half of original.
        DrawableTestUtils.setResourcesDensity(res, densityDpi / 2);
        final StateListDrawable halfDrawable =
                (StateListDrawable) preloadedConstantState.newDrawable(res);
        // NOTE: densityDpi may not be an even number, so account for *actual* scaling in asserts
        final float approxHalf = (float)(densityDpi / 2) / densityDpi;
        halfDrawable.selectDrawable(0);
        assertEquals(Math.round(origWidth0 * approxHalf), halfDrawable.getIntrinsicWidth());
        halfDrawable.selectDrawable(1);
        assertEquals(Math.round(origWidth1 * approxHalf), halfDrawable.getIntrinsicWidth());

        // Set density to double original.
        DrawableTestUtils.setResourcesDensity(res, densityDpi * 2);
        final StateListDrawable doubleDrawable =
                (StateListDrawable) preloadedConstantState.newDrawable(res);
        doubleDrawable.selectDrawable(0);
        assertEquals(origWidth0 * 2, doubleDrawable.getIntrinsicWidth());
        doubleDrawable.selectDrawable(1);
        assertEquals(origWidth1 * 2, doubleDrawable.getIntrinsicWidth());

        // Restore original configuration and metrics.
        DrawableTestUtils.setResourcesDensity(res, densityDpi);
        final StateListDrawable origDrawable =
                (StateListDrawable) preloadedConstantState.newDrawable(res);
        origDrawable.selectDrawable(0);
        assertEquals(origWidth0, origDrawable.getIntrinsicWidth());
        origDrawable.selectDrawable(1);
        assertEquals(origWidth1, origDrawable.getIntrinsicWidth());
    }

    @Test
    public void testInflate() throws XmlPullParserException, IOException {
        XmlResourceParser parser = getResourceParser(R.xml.selector_correct);

        mMockDrawable.reset();
        mMockDrawable.inflate(mResources, parser, Xml.asAttributeSet(parser));
        // android:visible="false"
        assertFalse(mMockDrawable.isVisible());
        // android:constantSize="true"
        assertTrue(mDrawableContainerState.isConstantSize());
        // android:variablePadding="true"
        assertNull(mDrawableContainerState.getConstantPadding());
        assertTrue(mMockDrawable.hasCalledOnStateChanged());
        assertEquals(2, mDrawableContainerState.getChildCount());
        // check the android:state_* by calling setState
        mMockDrawable.setState(
                new int[]{ android.R.attr.state_focused, - android.R.attr.state_pressed });
        assertSame(mMockDrawable.getCurrent(), mDrawableContainerState.getChildren()[0]);
        mMockDrawable.setState(StateSet.WILD_CARD);
        assertSame(mMockDrawable.getCurrent(), mDrawableContainerState.getChildren()[1]);

        mMockDrawable = new MockStateListDrawable();
        mDrawableContainerState = (DrawableContainerState) mMockDrawable.getConstantState();
        assertNull(mMockDrawable.getCurrent());
        mMockDrawable.reset();
        assertTrue(mMockDrawable.isVisible());
        parser = getResourceParser(R.xml.selector_missing_selector_attrs);
        mMockDrawable.inflate(mResources, parser, Xml.asAttributeSet(parser));
        // use current the visibility
        assertTrue(mMockDrawable.isVisible());
        // default value of android:constantSize is false
        assertFalse(mDrawableContainerState.isConstantSize());
        // default value of android:variablePadding is false
        // TODO: behavior of mDrawableContainerState.getConstantPadding() when variablePadding is
        // false is undefined
        //assertNotNull(mDrawableContainerState.getConstantPadding());
        assertTrue(mMockDrawable.hasCalledOnStateChanged());
        assertEquals(1, mDrawableContainerState.getChildCount());
        mMockDrawable.setState(
                new int[]{ - android.R.attr.state_pressed, android.R.attr.state_focused });
        assertSame(mMockDrawable.getCurrent(), mDrawableContainerState.getChildren()[0]);
        mMockDrawable.setState(StateSet.WILD_CARD);
        assertNull(mMockDrawable.getCurrent());

        parser = getResourceParser(R.xml.selector_missing_item_drawable);
        try {
            mMockDrawable.inflate(mResources, parser, Xml.asAttributeSet(parser));
            fail("Should throw XmlPullParserException if drawable of item is missing");
        } catch (XmlPullParserException e) {
        }
    }

    @Test(expected=NullPointerException.class)
    public void testInflateWithNullResources() throws XmlPullParserException, IOException {
        XmlResourceParser parser = getResourceParser(R.xml.level_list_correct);
        // Should throw NullPointerException if resource is null
        mMockDrawable.inflate(null, parser, Xml.asAttributeSet(parser));
    }

    @Test(expected=NullPointerException.class)
    public void testInflateWithNullParser() throws XmlPullParserException, IOException {
        XmlResourceParser parser = getResourceParser(R.xml.level_list_correct);
        // Should throw NullPointerException if parser is null
        mMockDrawable.inflate(mResources, null, Xml.asAttributeSet(parser));
    }

    @Test(expected=NullPointerException.class)
    public void testInflateWithNullAttrSet() throws XmlPullParserException, IOException {
        XmlResourceParser parser = getResourceParser(R.xml.level_list_correct);
        // Should throw NullPointerException if AttributeSet is null
        mMockDrawable.inflate(mResources, parser, null);
    }

    @Test
    public void testMutate() {
        StateListDrawable d1 =
            (StateListDrawable) mResources.getDrawable(R.drawable.statelistdrawable);
        StateListDrawable d2 =
            (StateListDrawable) mResources.getDrawable(R.drawable.statelistdrawable);
        StateListDrawable d3 =
            (StateListDrawable) mResources.getDrawable(R.drawable.statelistdrawable);

        // StateListDrawable mutates its children when jumping to a new drawable
        d1.getCurrent().setAlpha(100);
        assertEquals(100, ((BitmapDrawable) d1.getCurrent()).getPaint().getAlpha());
        assertEquals(255, ((BitmapDrawable) d2.getCurrent()).getPaint().getAlpha());
        assertEquals(255, ((BitmapDrawable) d3.getCurrent()).getPaint().getAlpha());

        d1.mutate();

        // TODO: add verification

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

    // Since Mockito can't mock or spy on protected methods, we have a custom extension
    // of StateListDrawable to track calls to protected onStateChange method.
    private class MockStateListDrawable extends StateListDrawable {
        private boolean mHasCalledOnStateChanged;

        public boolean hasCalledOnStateChanged() {
            return mHasCalledOnStateChanged;
        }

        public void reset() {
            mHasCalledOnStateChanged = false;
        }

        @Override
        protected boolean onStateChange(int[] stateSet) {
            boolean result = super.onStateChange(stateSet);
            mHasCalledOnStateChanged = true;
            return result;
        }
    }
}
