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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Parcelable;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.AttributeSet;
import android.util.StateSet;
import android.util.Xml;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.cts.util.TestUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;

/**
 * Test {@link CompoundButton}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class CompoundButtonTest  {
    private Instrumentation mInstrumentation;
    private Activity mActivity;
    private CompoundButton mCompoundButton;

    @Rule
    public ActivityTestRule<CompoundButtonCtsActivity> mActivityRule =
            new ActivityTestRule<>(CompoundButtonCtsActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
        mCompoundButton = (CompoundButton) mActivity.findViewById(R.id.compound_button);
    }

    @Test
    public void testConstructor() {
        XmlPullParser parser = mActivity.getResources().getXml(R.layout.compoundbutton_layout);
        AttributeSet mAttrSet = Xml.asAttributeSet(parser);

        new MockCompoundButton(mActivity, mAttrSet, 0);
        new MockCompoundButton(mActivity, mAttrSet);
        new MockCompoundButton(mActivity);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorWithNullContext1() {
        new MockCompoundButton(null);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorWithNullContext2() {
        new MockCompoundButton(null, null);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorWithNullContext3() {
        new MockCompoundButton(null, null, -1);
    }

    @UiThreadTest
    @Test
    public void testAccessChecked() {
        CompoundButton.OnCheckedChangeListener mockCheckedChangeListener =
                mock(CompoundButton.OnCheckedChangeListener.class);
        mCompoundButton.setOnCheckedChangeListener(mockCheckedChangeListener);
        assertFalse(mCompoundButton.isChecked());
        verifyZeroInteractions(mockCheckedChangeListener);

        mCompoundButton.setChecked(true);
        assertTrue(mCompoundButton.isChecked());
        verify(mockCheckedChangeListener, times(1)).onCheckedChanged(mCompoundButton, true);

        reset(mockCheckedChangeListener);
        mCompoundButton.setChecked(true);
        assertTrue(mCompoundButton.isChecked());
        verifyZeroInteractions(mockCheckedChangeListener);

        mCompoundButton.setChecked(false);
        assertFalse(mCompoundButton.isChecked());
        verify(mockCheckedChangeListener, times(1)).onCheckedChanged(mCompoundButton, false);
    }

    @UiThreadTest
    @Test
    public void testSetOnCheckedChangeListener() {
        CompoundButton.OnCheckedChangeListener mockCheckedChangeListener =
                mock(CompoundButton.OnCheckedChangeListener.class);
        mCompoundButton.setOnCheckedChangeListener(mockCheckedChangeListener);
        assertFalse(mCompoundButton.isChecked());
        verifyZeroInteractions(mockCheckedChangeListener);

        mCompoundButton.setChecked(true);
        verify(mockCheckedChangeListener, times(1)).onCheckedChanged(mCompoundButton, true);

        // set null
        mCompoundButton.setOnCheckedChangeListener(null);
        reset(mockCheckedChangeListener);
        mCompoundButton.setChecked(false);
        verifyZeroInteractions(mockCheckedChangeListener);
    }

    @UiThreadTest
    @Test
    public void testToggle() {
        assertFalse(mCompoundButton.isChecked());

        mCompoundButton.toggle();
        assertTrue(mCompoundButton.isChecked());

        mCompoundButton.toggle();
        assertFalse(mCompoundButton.isChecked());

        mCompoundButton.setChecked(true);
        mCompoundButton.toggle();
        assertFalse(mCompoundButton.isChecked());
    }

    @UiThreadTest
    @Test
    public void testPerformClick() {
        assertFalse(mCompoundButton.isChecked());

        // performClick without OnClickListener will return false.
        assertFalse(mCompoundButton.performClick());
        assertTrue(mCompoundButton.isChecked());

        assertFalse(mCompoundButton.performClick());
        assertFalse(mCompoundButton.isChecked());

        // performClick with OnClickListener will return true.
        mCompoundButton.setOnClickListener((view) -> {});
        assertTrue(mCompoundButton.performClick());
        assertTrue(mCompoundButton.isChecked());

        assertTrue(mCompoundButton.performClick());
        assertFalse(mCompoundButton.isChecked());
    }

    @UiThreadTest
    @Test
    public void testDrawableStateChanged() {
        MockCompoundButton compoundButton = new MockCompoundButton(mActivity);
        assertFalse(compoundButton.isChecked());
        // drawableStateChanged without any drawables.
        compoundButton.drawableStateChanged();

        // drawableStateChanged when CheckMarkDrawable is not null.
        Drawable drawable = mActivity.getDrawable(R.drawable.statelistdrawable);
        compoundButton.setButtonDrawable(drawable);
        drawable.setState(null);
        assertNull(drawable.getState());

        compoundButton.drawableStateChanged();
        assertNotNull(drawable.getState());
        assertSame(compoundButton.getDrawableState(), drawable.getState());
    }

    @UiThreadTest
    @Test
    public void testSetButtonDrawableByDrawable() {
        // set null drawable
        mCompoundButton.setButtonDrawable(null);
        assertNull(mCompoundButton.getButtonDrawable());

        // set drawable when button is GONE
        mCompoundButton.setVisibility(View.GONE);
        Drawable firstDrawable = mActivity.getDrawable(R.drawable.scenery);
        firstDrawable.setVisible(true, false);
        assertEquals(StateSet.WILD_CARD, firstDrawable.getState());

        mCompoundButton.setButtonDrawable(firstDrawable);
        assertSame(firstDrawable, mCompoundButton.getButtonDrawable());
        assertFalse(firstDrawable.isVisible());

        // update drawable when button is VISIBLE
        mCompoundButton.setVisibility(View.VISIBLE);
        Drawable secondDrawable = mActivity.getDrawable(R.drawable.pass);
        secondDrawable.setVisible(true, false);
        assertEquals(StateSet.WILD_CARD, secondDrawable.getState());

        mCompoundButton.setButtonDrawable(secondDrawable);
        assertSame(secondDrawable, mCompoundButton.getButtonDrawable());
        assertTrue(secondDrawable.isVisible());
        // the firstDrawable is not active.
        assertFalse(firstDrawable.isVisible());
    }

    @UiThreadTest
    @Test
    public void testSetButtonDrawableById() {
        // resId is 0
        mCompoundButton.setButtonDrawable(0);

        // set drawable
        mCompoundButton.setButtonDrawable(R.drawable.scenery);

        // set the same drawable again
        mCompoundButton.setButtonDrawable(R.drawable.scenery);

        // update drawable
        mCompoundButton.setButtonDrawable(R.drawable.pass);
    }

    @Test
    public void testOnCreateDrawableState() {
        // compoundButton is not checked, append 0 to state array.
        MockCompoundButton compoundButton = new MockCompoundButton(mActivity);
        int[] state = compoundButton.onCreateDrawableState(0);
        assertEquals(0, state[state.length - 1]);

        // compoundButton is checked, append R.attr.state_checked to state array.
        compoundButton.setChecked(true);
        int[] checkedState = compoundButton.onCreateDrawableState(0);
        assertEquals(state[0], checkedState[0]);
        assertEquals(android.R.attr.state_checked,
                checkedState[checkedState.length - 1]);

        // compoundButton is not checked again.
        compoundButton.setChecked(false);
        state = compoundButton.onCreateDrawableState(0);
        assertEquals(0, state[state.length - 1]);
    }

    @Test
    public void testOnDraw() {
        int viewHeight;
        int drawableWidth;
        int drawableHeight;
        Rect bounds;
        Drawable drawable;
        Canvas canvas = new Canvas(android.graphics.Bitmap.createBitmap(100, 100,
                android.graphics.Bitmap.Config.ARGB_8888));
        MockCompoundButton compoundButton;

        // onDraw when there is no drawable
        compoundButton = new MockCompoundButton(mActivity);
        compoundButton.onDraw(canvas);

        // onDraw when Gravity.TOP, it's default.
        compoundButton = new MockCompoundButton(mActivity);
        drawable = mActivity.getDrawable(R.drawable.scenery);
        compoundButton.setButtonDrawable(drawable);
        viewHeight = compoundButton.getHeight();
        drawableWidth = drawable.getIntrinsicWidth();
        drawableHeight = drawable.getIntrinsicHeight();

        compoundButton.onDraw(canvas);
        bounds = drawable.copyBounds();
        assertEquals(0, bounds.left);
        assertEquals(drawableWidth, bounds.right);
        assertEquals(0, bounds.top);
        assertEquals(drawableHeight, bounds.bottom);

        // onDraw when Gravity.BOTTOM
        compoundButton.setGravity(Gravity.BOTTOM);
        compoundButton.onDraw(canvas);
        bounds = drawable.copyBounds();
        assertEquals(0, bounds.left);
        assertEquals(drawableWidth, bounds.right);
        assertEquals(viewHeight - drawableHeight, bounds.top);
        assertEquals(viewHeight, bounds.bottom);

        // onDraw when Gravity.CENTER_VERTICAL
        compoundButton.setGravity(Gravity.CENTER_VERTICAL);
        compoundButton.onDraw(canvas);
        bounds = drawable.copyBounds();
        assertEquals(0, bounds.left);
        assertEquals(drawableWidth, bounds.right);
        assertEquals( (viewHeight - drawableHeight) / 2, bounds.top);
        assertEquals( (viewHeight - drawableHeight) / 2 + drawableHeight, bounds.bottom);
    }

    @UiThreadTest
    @Test
    public void testAccessInstanceState() {
        Parcelable state;

        assertFalse(mCompoundButton.isChecked());
        assertFalse(mCompoundButton.getFreezesText());

        state = mCompoundButton.onSaveInstanceState();
        assertNotNull(state);
        assertFalse(mCompoundButton.getFreezesText());

        mCompoundButton.setChecked(true);

        mCompoundButton.onRestoreInstanceState(state);
        assertFalse(mCompoundButton.isChecked());
        assertTrue(mCompoundButton.isLayoutRequested());
    }

    @Test
    public void testVerifyDrawable() {
        MockCompoundButton compoundButton = new MockCompoundButton(mActivity);
        Drawable drawable = mActivity.getDrawable(R.drawable.scenery);

        assertTrue(compoundButton.verifyDrawable(null));
        assertFalse(compoundButton.verifyDrawable(drawable));

        compoundButton.setButtonDrawable(drawable);
        assertTrue(compoundButton.verifyDrawable(null));
        assertTrue(compoundButton.verifyDrawable(drawable));
    }

    @UiThreadTest
    @Test
    public void testButtonTint() {
        CompoundButton tintedButton = (CompoundButton) mActivity.findViewById(R.id.button_tint);

        assertEquals("Button tint inflated correctly",
                Color.WHITE, tintedButton.getButtonTintList().getDefaultColor());
        assertEquals("Button tint mode inflated correctly",
                PorterDuff.Mode.SRC_OVER, tintedButton.getButtonTintMode());

        Drawable mockDrawable = spy(new ColorDrawable(Color.GREEN));

        mCompoundButton.setButtonDrawable(mockDrawable);
        // No button tint applied by default
        verify(mockDrawable, never()).setTintList(any(ColorStateList.class));

        mCompoundButton.setButtonTintList(ColorStateList.valueOf(Color.WHITE));
        // Button tint applied when setButtonTintList() called after setButton()
        verify(mockDrawable, times(1)).setTintList(TestUtils.colorStateListOf(Color.WHITE));

        reset(mockDrawable);
        mCompoundButton.setButtonDrawable(null);
        mCompoundButton.setButtonDrawable(mockDrawable);
        // Button tint applied when setButtonTintList() called before setButton()
        verify(mockDrawable, times(1)).setTintList(TestUtils.colorStateListOf(Color.WHITE));
    }

    public static final class MockCompoundButton extends CompoundButton {
        public MockCompoundButton(Context context) {
            super(context);
        }

        public MockCompoundButton(Context context, AttributeSet attrs) {
            super(context, attrs, 0);
        }

        public MockCompoundButton(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }

        @Override
        protected void drawableStateChanged() {
            super.drawableStateChanged();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
        }

        @Override
        protected int[] onCreateDrawableState(int extraSpace) {
            return super.onCreateDrawableState(extraSpace);
        }

        @Override
        protected boolean verifyDrawable(Drawable who) {
            return super.verifyDrawable(who);
        }
    }
}
