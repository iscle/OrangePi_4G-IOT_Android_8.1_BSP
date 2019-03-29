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

import static com.android.compatibility.common.util.CtsMockitoUtils.within;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.compatibility.common.util.CtsTouchUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class View_UsingViewsTest {
    /**
     * country of Argentina
     */
    private static final String ARGENTINA = "Argentina";

    /**
     * country of America
     */
    private static final String AMERICA = "America";

    /**
     * country of China
     */
    private static final String CHINA = "China";

    /**
     * the symbol of Argentina is football
     */
    private static final String ARGENTINA_SYMBOL = "football";

    /**
     * the symbol of America is basketball
     */
    private static final String AMERICA_SYMBOL = "basketball";

    /**
     * the symbol of China is table tennis
     */
    private static final String CHINA_SYMBOL = "table tennis";

    private Instrumentation mInstrumentation;
    private Activity mActivity;

    private EditText mEditText;
    private Button mButtonOk;
    private Button mButtonCancel;
    private TextView mSymbolTextView;
    private TextView mWarningTextView;

    @Rule
    public ActivityTestRule<UsingViewsCtsActivity> mActivityRule =
            new ActivityTestRule<>(UsingViewsCtsActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();

        mEditText = (EditText) mActivity.findViewById(R.id.entry);
        mButtonOk = (Button) mActivity.findViewById(R.id.ok);
        mButtonCancel = (Button) mActivity.findViewById(R.id.cancel);
        mSymbolTextView = (TextView) mActivity.findViewById(R.id.symbolball);
        mWarningTextView = (TextView) mActivity.findViewById(R.id.warning);
    }

    @UiThreadTest
    @Test
    public void testSetProperties() {
        // setClickable, setOnClickListener
        mButtonOk.setClickable(true);
        assertTrue(mButtonOk.isClickable());

        View.OnClickListener okButtonListener = spy(new MockOnClickOkListener());
        mButtonOk.setOnClickListener(okButtonListener);

        mButtonOk.performClick();
        verify(okButtonListener, times(1)).onClick(mButtonOk);

        mButtonCancel.setClickable(false);
        assertFalse(mButtonCancel.isClickable());

        View.OnClickListener cancelButtonListener = mock(View.OnClickListener.class);
        doAnswer((InvocationOnMock invocation) -> {
            mEditText.setText(null);
            return null;
        }).when(cancelButtonListener).onClick(any(View.class));
        mButtonCancel.setOnClickListener(cancelButtonListener);
        assertTrue(mButtonCancel.isClickable());

        mButtonCancel.performClick();
        verify(cancelButtonListener, times(1)).onClick(mButtonCancel);

        // setDrawingCacheEnabled, setDrawingCacheQuality, setDrawingCacheBackgroundColor,
        mEditText.setDrawingCacheEnabled(true);
        assertTrue(mEditText.isDrawingCacheEnabled());

        // the default quality is auto
        assertEquals(View.DRAWING_CACHE_QUALITY_AUTO, mEditText.getDrawingCacheQuality());
        mEditText.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_LOW);
        assertEquals(View.DRAWING_CACHE_QUALITY_LOW, mEditText.getDrawingCacheQuality());
        mEditText.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
        assertEquals(View.DRAWING_CACHE_QUALITY_HIGH, mEditText.getDrawingCacheQuality());

        mEditText.setDrawingCacheBackgroundColor(Color.GREEN);
        assertEquals(Color.GREEN, mEditText.getDrawingCacheBackgroundColor());

        // create the cache
        Bitmap b = mEditText.getDrawingCache();
        assertNotNull(b);
        assertEquals(mEditText.getHeight(), b.getHeight());
        assertEquals(mEditText.getWidth(), b.getWidth());
        assertEquals(Color.GREEN, b.getPixel(0, 0));

        // setDrawingCacheEnabled to false
        mEditText.setDrawingCacheEnabled(false);
        assertFalse(mEditText.isDrawingCacheEnabled());

        mEditText.setDrawingCacheBackgroundColor(Color.YELLOW);
        assertEquals(Color.YELLOW, mEditText.getDrawingCacheBackgroundColor());

        // build drawable cache
        mEditText.buildDrawingCache();
        b = mEditText.getDrawingCache();
        assertNotNull(b);
        assertEquals(mEditText.getHeight(), b.getHeight());
        assertEquals(mEditText.getWidth(), b.getWidth());
        assertEquals(Color.YELLOW, b.getPixel(0, 0));
        mEditText.destroyDrawingCache();

        // setDuplicateParentStateEnabled
        TextView v = new TextView(mActivity);
        v.setSingleLine(); // otherwise the multiline state interferes with theses tests
        v.setEnabled(false);
        v.setText("Test setDuplicateParentStateEnabled");

        v.setDuplicateParentStateEnabled(false);
        assertFalse(v.isDuplicateParentStateEnabled());

        RelativeLayout parent = (RelativeLayout) mEditText.getParent();
        parent.addView(v);

        assertFalse(parent.getDrawableState().length == v.getDrawableState().length);
        parent.removeView(v);

        v.setDuplicateParentStateEnabled(true);
        assertTrue(v.isDuplicateParentStateEnabled());

        parent.addView(v);
        v.refreshDrawableState();

        assertArrayEquals(parent.getDrawableState(), v.getDrawableState());
        parent.removeView(v);

        // setEnabled
        mWarningTextView.setEnabled(false);
        assertFalse(mWarningTextView.isEnabled());

        mWarningTextView.setEnabled(true);
        assertTrue(mWarningTextView.isEnabled());

        // setFadingEdgeLength, setVerticalFadingEdgeEnabled and
        // setHorizontalFadingEdgeEnabled(boolean)
        mWarningTextView.setVerticalFadingEdgeEnabled(true);
        assertTrue(mWarningTextView.isVerticalFadingEdgeEnabled());
        mWarningTextView.setFadingEdgeLength(10);

        mSymbolTextView.setHorizontalFadingEdgeEnabled(true);
        assertTrue(mSymbolTextView.isHorizontalFadingEdgeEnabled());
        mSymbolTextView.setFadingEdgeLength(100);

        // setFocusable and setFocusableInTouchMode
        mButtonCancel.setFocusable(false);
        assertFalse(mButtonCancel.isFocusable());
        assertFalse(mButtonCancel.isFocusableInTouchMode());

        mButtonCancel.setFocusable(true);
        assertTrue(mButtonCancel.isFocusable());
        assertFalse(mButtonCancel.isFocusableInTouchMode());

        mButtonCancel.setFocusableInTouchMode(true);
        assertTrue(mButtonCancel.isFocusable());
        assertTrue(mButtonCancel.isFocusableInTouchMode());

        mButtonOk.setFocusable(false);
        assertFalse(mButtonOk.isFocusable());
        assertFalse(mButtonOk.isFocusableInTouchMode());

        mButtonOk.setFocusableInTouchMode(true);
        assertTrue(mButtonOk.isFocusable());
        assertTrue(mButtonOk.isFocusableInTouchMode());

        // setHorizontalScrollBarEnabled and setVerticalScrollBarEnabled
        // both two bar is not drawn by default
        assertFalse(parent.isHorizontalScrollBarEnabled());
        assertFalse(parent.isVerticalScrollBarEnabled());

        parent.setHorizontalScrollBarEnabled(true);
        assertTrue(parent.isHorizontalScrollBarEnabled());

        parent.setVerticalScrollBarEnabled(true);
        assertTrue(parent.isVerticalScrollBarEnabled());

        // setId
        assertEquals(View.NO_ID, parent.getId());
        assertEquals(R.id.entry, mEditText.getId());
        assertEquals(R.id.symbolball, mSymbolTextView.getId());

        mSymbolTextView.setId(0x5555);
        assertEquals(0x5555, mSymbolTextView.getId());
        TextView t = (TextView) parent.findViewById(0x5555);
        assertSame(mSymbolTextView, t);

        mSymbolTextView.setId(R.id.symbolball);
        assertEquals(R.id.symbolball, mSymbolTextView.getId());
    }

    @UiThreadTest
    @Test
    public void testSetFocus() {
        boolean focusWasOnEditText = mEditText.hasFocus();

        View.OnFocusChangeListener editListener = mock(View.OnFocusChangeListener.class);
        View.OnFocusChangeListener okListener = mock(View.OnFocusChangeListener.class);
        View.OnFocusChangeListener cancelListener = mock(View.OnFocusChangeListener.class);
        View.OnFocusChangeListener symbolListener = mock(View.OnFocusChangeListener.class);
        View.OnFocusChangeListener warningListener = mock(View.OnFocusChangeListener.class);

        mEditText.setOnFocusChangeListener(editListener);
        mButtonOk.setOnFocusChangeListener(okListener);
        mButtonCancel.setOnFocusChangeListener(cancelListener);
        mSymbolTextView.setOnFocusChangeListener(symbolListener);
        mWarningTextView.setOnFocusChangeListener(warningListener);

        mSymbolTextView.setText(ARGENTINA_SYMBOL);
        mWarningTextView.setVisibility(View.VISIBLE);

        assertTrue(mEditText.requestFocus());
        assertTrue(mEditText.hasFocus());
        assertFalse(mButtonOk.hasFocus());
        assertFalse(mButtonCancel.hasFocus());
        assertFalse(mSymbolTextView.hasFocus());
        assertFalse(mWarningTextView.hasFocus());

        if (!focusWasOnEditText) {
            verify(editListener, times(1)).onFocusChange(mEditText, true);
        }
        verifyZeroInteractions(okListener);
        verifyZeroInteractions(cancelListener);
        verifyZeroInteractions(symbolListener);
        verifyZeroInteractions(warningListener);

        // set ok button to focus
        reset(editListener);
        assertTrue(mButtonOk.requestFocus());
        assertTrue(mButtonOk.hasFocus());
        verify(okListener, times(1)).onFocusChange(mButtonOk, true);
        assertFalse(mEditText.hasFocus());
        verify(editListener, times(1)).onFocusChange(mEditText, false);
        verifyZeroInteractions(cancelListener);
        verifyZeroInteractions(symbolListener);
        verifyZeroInteractions(warningListener);

        // set cancel button to focus
        reset(okListener);
        reset(editListener);
        assertTrue(mButtonCancel.requestFocus());
        assertTrue(mButtonCancel.hasFocus());
        verify(cancelListener, times(1)).onFocusChange(mButtonCancel, true);
        assertFalse(mButtonOk.hasFocus());
        verify(okListener, times(1)).onFocusChange(mButtonOk, false);
        verifyZeroInteractions(editListener);
        verifyZeroInteractions(symbolListener);
        verifyZeroInteractions(warningListener);

        // set symbol text to focus
        mSymbolTextView.setFocusable(true);
        assertTrue(mSymbolTextView.requestFocus());
        assertTrue(mSymbolTextView.hasFocus());
        verify(symbolListener, times(1)).onFocusChange(mSymbolTextView, true);
        assertFalse(mButtonCancel.hasFocus());
        verify(cancelListener, times(1)).onFocusChange(mButtonCancel, false);
        verifyZeroInteractions(okListener);
        verifyZeroInteractions(editListener);
        verifyZeroInteractions(warningListener);

        // set warning text to focus
        mWarningTextView.setFocusable(true);
        assertTrue(mWarningTextView.requestFocus());
        assertTrue(mWarningTextView.hasFocus());
        verify(warningListener, times(1)).onFocusChange(mWarningTextView, true);
        assertFalse(mSymbolTextView.hasFocus());
        verify(symbolListener, times(1)).onFocusChange(mSymbolTextView, false);
        verifyZeroInteractions(editListener);
        verifyZeroInteractions(okListener);
        verifyZeroInteractions(cancelListener);

        // set edit text to focus
        assertTrue(mEditText.requestFocus());
        assertTrue(mEditText.hasFocus());
        verify(editListener, times(1)).onFocusChange(mEditText, true);
        assertFalse(mWarningTextView.hasFocus());
        verify(warningListener, times(1)).onFocusChange(mWarningTextView, false);
        verifyZeroInteractions(cancelListener);
        verifyZeroInteractions(symbolListener);
        verifyZeroInteractions(okListener);
    }

    @Test
    public void testSetupListeners() throws Throwable {
        // set ok button OnClick listener
        mButtonOk.setClickable(true);
        assertTrue(mButtonOk.isClickable());

        View.OnClickListener okButtonListener = spy(new MockOnClickOkListener());
        mButtonOk.setOnClickListener(okButtonListener);

        // set cancel button OnClick listener
        mButtonCancel.setClickable(true);
        assertTrue(mButtonCancel.isClickable());

        View.OnClickListener cancelButtonListener = mock(View.OnClickListener.class);
        doAnswer((InvocationOnMock invocation) -> {
            mEditText.setText(null);
            return null;
        }).when(cancelButtonListener).onClick(any(View.class));
        mButtonCancel.setOnClickListener(cancelButtonListener);

        // set edit text OnLongClick listener
        mEditText.setLongClickable(true);
        assertTrue(mEditText.isLongClickable());

        final View.OnLongClickListener onLongClickListener =
                mock(View.OnLongClickListener.class);
        mEditText.setOnLongClickListener(onLongClickListener);

        // long click the edit text
        mInstrumentation.waitForIdleSync();
        CtsTouchUtils.emulateLongPressOnViewCenter(mInstrumentation, mEditText);
        verify(onLongClickListener, within(1000)).onLongClick(mEditText);

        // click the Cancel button
        mActivityRule.runOnUiThread(() -> mEditText.setText("Germany"));
        mInstrumentation.waitForIdleSync();

        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mButtonCancel);
        assertEquals("", mEditText.getText().toString());

        // click the OK button
        mActivityRule.runOnUiThread(() -> mEditText.setText(ARGENTINA));
        mInstrumentation.waitForIdleSync();

        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mButtonOk);
        assertEquals(ARGENTINA_SYMBOL, mSymbolTextView.getText().toString());

        mActivityRule.runOnUiThread(() -> mEditText.setText(AMERICA));
        mInstrumentation.waitForIdleSync();

        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mButtonOk);
        assertEquals(AMERICA_SYMBOL, mSymbolTextView.getText().toString());

        mActivityRule.runOnUiThread(() -> mEditText.setText(CHINA));
        mInstrumentation.waitForIdleSync();

        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mButtonOk);
        assertEquals(CHINA_SYMBOL, mSymbolTextView.getText().toString());

        mActivityRule.runOnUiThread(() -> mEditText.setText("Unknown"));
        mInstrumentation.waitForIdleSync();

        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mButtonOk);
        assertEquals(View.VISIBLE, mWarningTextView.getVisibility());
    }

    @UiThreadTest
    @Test
    public void testSetVisibility() {
        mActivity.setContentView(R.layout.view_visibility_layout);

        View v1 = mActivity.findViewById(R.id.textview1);
        View v2 = mActivity.findViewById(R.id.textview2);
        View v3 = mActivity.findViewById(R.id.textview3);

        assertNotNull(v1);
        assertNotNull(v2);
        assertNotNull(v3);

        assertEquals(View.VISIBLE, v1.getVisibility());
        assertEquals(View.INVISIBLE, v2.getVisibility());
        assertEquals(View.GONE, v3.getVisibility());

        v1.setVisibility(View.GONE);
        assertEquals(View.GONE, v1.getVisibility());

        v2.setVisibility(View.VISIBLE);
        assertEquals(View.VISIBLE, v2.getVisibility());

        v3.setVisibility(View.INVISIBLE);
        assertEquals(View.INVISIBLE, v3.getVisibility());
    }

    protected class MockOnClickOkListener implements OnClickListener {
        private boolean showPicture(String country) {
            if (ARGENTINA.equals(country)) {
                mSymbolTextView.setText(ARGENTINA_SYMBOL);
                return true;
            } else if (AMERICA.equals(country)) {
                mSymbolTextView.setText(AMERICA_SYMBOL);
                return true;
            } else if (CHINA.equals(country)) {
                mSymbolTextView.setText(CHINA_SYMBOL);
                return true;
            }

            return false;
        }

        public void onClick(View v) {
            String country = mEditText.getText().toString();
            if (!showPicture(country)) {
                mWarningTextView.setVisibility(View.VISIBLE);
            } else if (View.VISIBLE == mWarningTextView.getVisibility()) {
                mWarningTextView.setVisibility(View.INVISIBLE);
            }
        }
    }
}
