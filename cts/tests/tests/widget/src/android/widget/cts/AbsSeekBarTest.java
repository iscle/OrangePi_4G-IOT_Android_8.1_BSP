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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.widget.AbsSeekBar;
import android.widget.SeekBar;
import android.widget.cts.util.TestUtils;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.WidgetTestUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.List;

/**
 * Test {@link AbsSeekBar}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class AbsSeekBarTest {
    private Instrumentation mInstrumentation;
    private Activity mActivity;

    @Rule
    public ActivityTestRule<AbsSeekBarCtsActivity> mActivityRule =
            new ActivityTestRule<>(AbsSeekBarCtsActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
    }

    @Test
    public void testConstructor() {
        new MyAbsSeekBar(mActivity);

        new MyAbsSeekBar(mActivity, null);

        new MyAbsSeekBar(mActivity, null, android.R.attr.progressBarStyle);

        new MyAbsSeekBar(mActivity, null, 0, android.R.style.Widget_Material_Light_ProgressBar);
    }

    @Test
    public void testAccessThumbOffset() {
        AbsSeekBar myAbsSeekBar = new MyAbsSeekBar(mActivity);
        final int positive = 5;
        final int negative = -5;
        final int zero = 0;

        myAbsSeekBar.setThumbOffset(positive);
        assertEquals(positive, myAbsSeekBar.getThumbOffset());

        myAbsSeekBar.setThumbOffset(zero);
        assertEquals(zero, myAbsSeekBar.getThumbOffset());

        myAbsSeekBar.setThumbOffset(negative);
        assertEquals(negative, myAbsSeekBar.getThumbOffset());
    }

    @Test
    public void testAccessThumb() {
        // Both are pointing to the same object. This works around current limitation in CTS
        // coverage report tool for properly reporting coverage of base class method calls.
        final MyAbsSeekBar myAbsSeekBar = new MyAbsSeekBar(mActivity);
        final AbsSeekBar absSeekBar = myAbsSeekBar;

        Drawable drawable1 = mActivity.getDrawable(R.drawable.scenery);
        Drawable drawable2 = mActivity.getDrawable(R.drawable.pass);

        assertFalse(myAbsSeekBar.verifyDrawable(drawable1));
        assertFalse(myAbsSeekBar.verifyDrawable(drawable2));

        absSeekBar.setThumb(drawable1);
        assertSame(drawable1, absSeekBar.getThumb());
        assertTrue(myAbsSeekBar.verifyDrawable(drawable1));
        assertFalse(myAbsSeekBar.verifyDrawable(drawable2));

        absSeekBar.setThumb(drawable2);
        assertSame(drawable2, absSeekBar.getThumb());
        assertFalse(myAbsSeekBar.verifyDrawable(drawable1));
        assertTrue(myAbsSeekBar.verifyDrawable(drawable2));
    }

    @Test
    public void testAccessTickMark() {
        // Both are pointing to the same object. This works around current limitation in CTS
        // coverage report tool for properly reporting coverage of base class method calls.
        final MyAbsSeekBar myAbsSeekBar = new MyAbsSeekBar(mActivity);
        final AbsSeekBar absSeekBar = myAbsSeekBar;

        Drawable drawable1 = mActivity.getDrawable(R.drawable.black);
        Drawable drawable2 = mActivity.getDrawable(R.drawable.black);

        assertFalse(myAbsSeekBar.verifyDrawable(drawable1));
        assertFalse(myAbsSeekBar.verifyDrawable(drawable2));

        absSeekBar.setTickMark(drawable1);
        assertSame(drawable1, absSeekBar.getTickMark());
        assertTrue(myAbsSeekBar.verifyDrawable(drawable1));
        assertFalse(myAbsSeekBar.verifyDrawable(drawable2));

        absSeekBar.setTickMark(drawable2);
        assertSame(drawable2, absSeekBar.getTickMark());
        assertFalse(myAbsSeekBar.verifyDrawable(drawable1));
        assertTrue(myAbsSeekBar.verifyDrawable(drawable2));
    }

    @Test
    public void testDrawableStateChanged() {
        MyAbsSeekBar myAbsSeekBar = new MyAbsSeekBar(mActivity);
        Drawable mockProgressDrawable = spy(new ColorDrawable(Color.YELLOW));
        myAbsSeekBar.setProgressDrawable(mockProgressDrawable);

        ArgumentCaptor<Integer> alphaCaptor = ArgumentCaptor.forClass(Integer.class);
        myAbsSeekBar.setEnabled(false);
        myAbsSeekBar.drawableStateChanged();
        verify(mockProgressDrawable, atLeastOnce()).setAlpha(alphaCaptor.capture());
        // Verify that the last call to setAlpha was with argument 0x00
        List<Integer> alphaCaptures = alphaCaptor.getAllValues();
        assertTrue(!alphaCaptures.isEmpty());
        assertEquals(Integer.valueOf(0x00), alphaCaptures.get(alphaCaptures.size() - 1));

        alphaCaptor = ArgumentCaptor.forClass(Integer.class);
        myAbsSeekBar.setEnabled(true);
        myAbsSeekBar.drawableStateChanged();
        verify(mockProgressDrawable, atLeastOnce()).setAlpha(alphaCaptor.capture());
        // Verify that the last call to setAlpha was with argument 0xFF
        alphaCaptures = alphaCaptor.getAllValues();
        assertTrue(!alphaCaptures.isEmpty());
        assertEquals(Integer.valueOf(0xFF), alphaCaptures.get(alphaCaptures.size() - 1));
    }

    @Test
    public void testVerifyDrawable() {
        MyAbsSeekBar myAbsSeekBar = new MyAbsSeekBar(mActivity);
        Drawable drawable1 = mActivity.getDrawable(R.drawable.scenery);
        Drawable drawable2 = mActivity.getDrawable(R.drawable.pass);
        Drawable drawable3 = mActivity.getDrawable(R.drawable.blue);
        Drawable drawable4 = mActivity.getDrawable(R.drawable.black);

        assertFalse(myAbsSeekBar.verifyDrawable(drawable1));
        assertFalse(myAbsSeekBar.verifyDrawable(drawable2));
        assertFalse(myAbsSeekBar.verifyDrawable(drawable3));
        assertFalse(myAbsSeekBar.verifyDrawable(drawable4));

        myAbsSeekBar.setThumb(drawable1);
        assertTrue(myAbsSeekBar.verifyDrawable(drawable1));
        assertFalse(myAbsSeekBar.verifyDrawable(drawable2));
        assertFalse(myAbsSeekBar.verifyDrawable(drawable3));
        assertFalse(myAbsSeekBar.verifyDrawable(drawable4));

        myAbsSeekBar.setThumb(drawable2);
        assertFalse(myAbsSeekBar.verifyDrawable(drawable1));
        assertTrue(myAbsSeekBar.verifyDrawable(drawable2));
        assertFalse(myAbsSeekBar.verifyDrawable(drawable3));
        assertFalse(myAbsSeekBar.verifyDrawable(drawable4));

        myAbsSeekBar.setBackgroundDrawable(drawable2);
        myAbsSeekBar.setProgressDrawable(drawable3);
        myAbsSeekBar.setIndeterminateDrawable(drawable4);
        assertFalse(myAbsSeekBar.verifyDrawable(drawable1));
        assertTrue(myAbsSeekBar.verifyDrawable(drawable2));
        assertTrue(myAbsSeekBar.verifyDrawable(drawable3));
        assertTrue(myAbsSeekBar.verifyDrawable(drawable4));
    }

    @Test
    public void testAccessKeyProgressIncrement() throws Throwable {
        // AbsSeekBar is an abstract class, use its subclass: SeekBar to do this test.
        mActivityRule.runOnUiThread(() -> mActivity.setContentView(R.layout.seekbar_layout));
        mInstrumentation.waitForIdleSync();

        final SeekBar seekBar = (SeekBar) mActivity.findViewById(R.id.seekBar);
        final int keyProgressIncrement = 2;
        mActivityRule.runOnUiThread(() -> {
            seekBar.setKeyProgressIncrement(keyProgressIncrement);
            seekBar.setFocusable(true);
            seekBar.requestFocus();
        });
        PollingCheck.waitFor(1000, seekBar::hasWindowFocus);
        assertEquals(keyProgressIncrement, seekBar.getKeyProgressIncrement());

        int oldProgress = seekBar.getProgress();
        KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT);
        mInstrumentation.sendKeySync(keyEvent);
        assertEquals(oldProgress + keyProgressIncrement, seekBar.getProgress());
        oldProgress = seekBar.getProgress();
        keyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT);
        mInstrumentation.sendKeySync(keyEvent);
        assertEquals(oldProgress - keyProgressIncrement, seekBar.getProgress());
    }

    @Test
    public void testAccessMax() {
        AbsSeekBar myAbsSeekBar = new MyAbsSeekBar(mActivity, null, R.style.TestProgressBar);

        int progress = 10;
        myAbsSeekBar.setProgress(progress);
        int max = progress + 1;
        myAbsSeekBar.setMax(max);
        assertEquals(max, myAbsSeekBar.getMax());
        assertEquals(progress, myAbsSeekBar.getProgress());
        assertEquals(1, myAbsSeekBar.getKeyProgressIncrement());

        max = progress - 1;
        myAbsSeekBar.setMax(max);
        assertEquals(max, myAbsSeekBar.getMax());
        assertEquals(max, myAbsSeekBar.getProgress());
        assertEquals(1, myAbsSeekBar.getKeyProgressIncrement());

        int keyProgressIncrement = 10;
        myAbsSeekBar.setKeyProgressIncrement(keyProgressIncrement);
        assertEquals(keyProgressIncrement, myAbsSeekBar.getKeyProgressIncrement());
        max = (keyProgressIncrement - 1) * 20;
        myAbsSeekBar.setMax(max);
        assertEquals(keyProgressIncrement, myAbsSeekBar.getKeyProgressIncrement());
        max = (keyProgressIncrement + 1) * 20;
        myAbsSeekBar.setMax(max);
        assertEquals(keyProgressIncrement + 1, myAbsSeekBar.getKeyProgressIncrement());
    }

    @UiThreadTest
    @Test
    public void testThumbTint() {
        AbsSeekBar inflatedView = (AbsSeekBar) mActivity.findViewById(R.id.thumb_tint);

        assertEquals("Thumb tint inflated correctly",
                Color.WHITE, inflatedView.getThumbTintList().getDefaultColor());
        assertEquals("Thumb tint mode inflated correctly",
                PorterDuff.Mode.SRC_OVER, inflatedView.getThumbTintMode());

        Drawable mockThumb = spy(new ColorDrawable(Color.BLUE));

        inflatedView.setThumb(mockThumb);
        verify(mockThumb, times(1)).setTintList(TestUtils.colorStateListOf(Color.WHITE));

        reset(mockThumb);
        inflatedView.setThumbTintList(ColorStateList.valueOf(Color.RED));
        verify(mockThumb, times(1)).setTintList(TestUtils.colorStateListOf(Color.RED));

        inflatedView.setThumbTintMode(PorterDuff.Mode.DST_ATOP);
        assertEquals("Thumb tint mode changed correctly",
                PorterDuff.Mode.DST_ATOP, inflatedView.getThumbTintMode());

        reset(mockThumb);
        inflatedView.setThumb(null);
        inflatedView.setThumb(mockThumb);
        verify(mockThumb, times(1)).setTintList(TestUtils.colorStateListOf(Color.RED));
    }

    @UiThreadTest
    @Test
    public void testTickMarkTint() {
        AbsSeekBar inflatedView = (AbsSeekBar) mActivity.findViewById(R.id.tick_mark_tint);

        assertEquals("TickMark tint inflated correctly",
                Color.WHITE, inflatedView.getTickMarkTintList().getDefaultColor());
        assertEquals("TickMark tint mode inflated correctly",
                PorterDuff.Mode.SRC_OVER, inflatedView.getTickMarkTintMode());

        Drawable mockTickMark = spy(new ColorDrawable(Color.BLUE));

        inflatedView.setTickMark(mockTickMark);
        verify(mockTickMark, times(1)).setTintList(TestUtils.colorStateListOf(Color.WHITE));

        reset(mockTickMark);
        inflatedView.setTickMarkTintList(ColorStateList.valueOf(Color.RED));
        verify(mockTickMark, times(1)).setTintList(TestUtils.colorStateListOf(Color.RED));

        inflatedView.setTickMarkTintMode(PorterDuff.Mode.DARKEN);
        assertEquals("TickMark tint mode changed correctly",
                PorterDuff.Mode.DARKEN, inflatedView.getTickMarkTintMode());

        reset(mockTickMark);
        inflatedView.setTickMark(null);
        inflatedView.setTickMark(mockTickMark);
        verify(mockTickMark, times(1)).setTintList(TestUtils.colorStateListOf(Color.RED));
    }

    @Test
    public void testAccessSplitTrack() throws Throwable {
        AbsSeekBar inflatedView = (AbsSeekBar) mActivity.findViewById(R.id.tick_mark_tint);

        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, inflatedView,
                () -> inflatedView.setSplitTrack(true));
        assertTrue(inflatedView.getSplitTrack());

        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, inflatedView,
                () -> inflatedView.setSplitTrack(false));
        assertFalse(inflatedView.getSplitTrack());
    }

    private static class MyAbsSeekBar extends AbsSeekBar {
        public MyAbsSeekBar(Context context) {
            super(context);
        }

        public MyAbsSeekBar(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public MyAbsSeekBar(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }

        public MyAbsSeekBar(Context context, AttributeSet attrs, int defStyle, int defStyleRes) {
            super(context, attrs, defStyle, defStyleRes);
        }

        @Override
        protected void drawableStateChanged() {
            super.drawableStateChanged();
        }

        @Override
        protected boolean verifyDrawable(Drawable who) {
            return super.verifyDrawable(who);
        }
    }
}
