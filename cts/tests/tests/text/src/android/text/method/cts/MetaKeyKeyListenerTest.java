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

package android.text.method.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.Editable;
import android.text.Selection;
import android.text.Spannable;
import android.text.Spanned;
import android.text.method.DateKeyListener;
import android.text.method.MetaKeyKeyListener;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link MetaKeyKeyListener}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class MetaKeyKeyListenerTest extends KeyListenerTestCase {
    @Test
    public void testPressKey() {
        final CharSequence str = "123456";
        final MetaKeyKeyListener numberKeyListener = new DateKeyListener();
        final View view = new ImageView(mInstrumentation.getTargetContext());
        final Editable content = Editable.Factory.getInstance().newEditable(str);

        content.setSpan(Selection.SELECTION_START, 0, 0, Spanned.SPAN_POINT_POINT);
        content.setSpan(Selection.SELECTION_END, 0, 0, Spanned.SPAN_POINT_POINT);
        numberKeyListener.onKeyDown(view, content, KeyEvent.KEYCODE_0,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_0));
        assertEquals('0', content.charAt(0));

        content.setSpan(Selection.SELECTION_START, 1, 1, Spanned.SPAN_POINT_POINT);
        content.setSpan(Selection.SELECTION_END, 1, 1, Spanned.SPAN_POINT_POINT);
        numberKeyListener.onKeyDown(view, content, KeyEvent.KEYCODE_2,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_2));
        assertEquals('2', content.charAt(1));

        content.setSpan(Selection.SELECTION_START, 3, 3, Spanned.SPAN_POINT_POINT);
        content.setSpan(Selection.SELECTION_END, 3, 3, Spanned.SPAN_POINT_POINT);
        numberKeyListener.onKeyDown(view, content, KeyEvent.KEYCODE_3,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_3));
        assertEquals('3', content.charAt(3));
    }

    @Test
    public void testReleaseKey() {
        final CharSequence str = "123456";
        final MetaKeyKeyListener numberKeyListener = new DateKeyListener();
        final View view = new ImageView(mInstrumentation.getTargetContext());
        final Editable content = Editable.Factory.getInstance().newEditable(str);

        content.setSpan(Selection.SELECTION_START, 0, 0, Spanned.SPAN_POINT_POINT);
        content.setSpan(Selection.SELECTION_END, 0, 0, Spanned.SPAN_POINT_POINT);
        numberKeyListener.onKeyUp(view, content, KeyEvent.KEYCODE_0,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_0));
        assertEquals(str.charAt(0), content.charAt(0));

        content.setSpan(Selection.SELECTION_START, 1, 1, Spanned.SPAN_POINT_POINT);
        content.setSpan(Selection.SELECTION_END, 1, 1, Spanned.SPAN_POINT_POINT);
        numberKeyListener.onKeyUp(view, content, KeyEvent.KEYCODE_2,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_2));
        assertEquals(str.charAt(1), content.charAt(1));

        content.setSpan(Selection.SELECTION_START, 3, 3, Spanned.SPAN_POINT_POINT);
        content.setSpan(Selection.SELECTION_END, 3, 3, Spanned.SPAN_POINT_POINT);
        numberKeyListener.onKeyUp(view, content, KeyEvent.KEYCODE_3,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_3));
        assertEquals(str.charAt(3), content.charAt(3));
    }

    @Test
    public void testAdjustMetaAfterKeypress() {
        CharSequence str = "123456";
        Spannable content = Editable.Factory.getInstance().newEditable(str);
        content.setSpan(Selection.SELECTION_START, 0, 0, Spanned.SPAN_POINT_POINT);
        int len = str.length(); // for one line less than 100
        content.setSpan( Selection.SELECTION_END, len, len, Spanned.SPAN_POINT_POINT);
        MetaKeyKeyListener.adjustMetaAfterKeypress(content);
        assertEquals(Spanned.SPAN_POINT_POINT, content.getSpanFlags(Selection.SELECTION_START));
        assertEquals(Spanned.SPAN_POINT_POINT, content.getSpanFlags(Selection.SELECTION_END));

        str = "abc";
        content = Editable.Factory.getInstance().newEditable(str);
        content.setSpan(Selection.SELECTION_START, 0, 0, Spanned.SPAN_POINT_POINT);
        len = str.length(); // for one line less than 100
        content.setSpan( Selection.SELECTION_END, len, len, Spanned.SPAN_POINT_POINT);
        MetaKeyKeyListener.adjustMetaAfterKeypress(content);
        assertEquals(Spanned.SPAN_POINT_POINT, content.getSpanFlags(Selection.SELECTION_START));
        assertEquals(Spanned.SPAN_POINT_POINT, content.getSpanFlags(Selection.SELECTION_END));

        str = "#@%#$^%^";
        content = Editable.Factory.getInstance().newEditable(str);
        content.setSpan(Selection.SELECTION_START, 0, 0, Spanned.SPAN_POINT_POINT);
        len = str.length(); // for one line less than 100
        content.setSpan( Selection.SELECTION_END, len, len, Spanned.SPAN_POINT_POINT);
        MetaKeyKeyListener.adjustMetaAfterKeypress(content);
        assertEquals(Spanned.SPAN_POINT_POINT, content.getSpanFlags(Selection.SELECTION_START));
        assertEquals(Spanned.SPAN_POINT_POINT, content.getSpanFlags(Selection.SELECTION_END));
    }

    @Test
    public void testAdjustMetaAfterKeypress2() {
        long state = MetaKeyKeyListener.adjustMetaAfterKeypress(MetaKeyKeyListener.META_SHIFT_ON);
        assertEquals(MetaKeyKeyListener.META_SHIFT_ON, state);

        state = MetaKeyKeyListener.adjustMetaAfterKeypress(MetaKeyKeyListener.META_ALT_ON);
        assertEquals(MetaKeyKeyListener.META_ALT_ON, state);

        state = MetaKeyKeyListener.adjustMetaAfterKeypress(MetaKeyKeyListener.META_SYM_ON);
        assertEquals(MetaKeyKeyListener.META_SYM_ON, state);

        state = MetaKeyKeyListener.adjustMetaAfterKeypress(0);
        assertEquals(0, state);
    }

    @Test
    public void testResetMetaState() {
        CharSequence str = "123456";
        Spannable text = Editable.Factory.getInstance().newEditable(str);
        text.setSpan(Selection.SELECTION_START, 0, 0, Spanned.SPAN_POINT_POINT);
        text.setSpan(Selection.SELECTION_END, str.length(), str.length(), Spanned.SPAN_POINT_POINT);
        MetaKeyKeyListener.resetMetaState(text);
        assertEquals(Spanned.SPAN_POINT_POINT, text.getSpanFlags(Selection.SELECTION_START));
        assertEquals(Spanned.SPAN_POINT_POINT, text.getSpanFlags(Selection.SELECTION_END));

        str = "abc";
        text = Editable.Factory.getInstance().newEditable(str);
        text.setSpan(Selection.SELECTION_START, 0, 0, Spanned.SPAN_POINT_POINT);
        text.setSpan(Selection.SELECTION_END, str.length(), str.length(), Spanned.SPAN_POINT_POINT);
        MetaKeyKeyListener.resetMetaState(text);
        assertEquals(Spanned.SPAN_POINT_POINT, text.getSpanFlags(Selection.SELECTION_START));
        assertEquals(Spanned.SPAN_POINT_POINT, text.getSpanFlags(Selection.SELECTION_END));

        str = "#@%#$^%^";
        text = Editable.Factory.getInstance().newEditable(str);
        text.setSpan(Selection.SELECTION_START, 0, 0, Spanned.SPAN_POINT_POINT);
        text.setSpan(Selection.SELECTION_END, str.length(), str.length(), Spanned.SPAN_POINT_POINT);
        MetaKeyKeyListener.resetMetaState(text);
        assertEquals(Spanned.SPAN_POINT_POINT, text.getSpanFlags(Selection.SELECTION_START));
        assertEquals(Spanned.SPAN_POINT_POINT, text.getSpanFlags(Selection.SELECTION_END));
    }

    @Test
    public void testGetMetaState() {
        assertEquals(0, MetaKeyKeyListener.getMetaState("123456"));
        assertEquals(0, MetaKeyKeyListener.getMetaState("abc"));
        assertEquals(0, MetaKeyKeyListener.getMetaState("@#$$#^$^"));

        assertEquals(0,
                     MetaKeyKeyListener.getMetaState("123456"),
                     MetaKeyKeyListener.META_SHIFT_ON);
        assertEquals(0,
                     MetaKeyKeyListener.getMetaState("abc"),
                     MetaKeyKeyListener.META_ALT_ON);
        assertEquals(0,
                     MetaKeyKeyListener.getMetaState("@#$$#^$^"),
                     MetaKeyKeyListener.META_SYM_ON);

        assertEquals(0, MetaKeyKeyListener.getMetaState("123456", 0));
        assertEquals(0, MetaKeyKeyListener.getMetaState("abc", -1));
        assertEquals(0, MetaKeyKeyListener.getMetaState("@#$$#^$^", Integer.MAX_VALUE));

        assertEquals(0,
                MetaKeyKeyListener.getMetaState("123456", MetaKeyKeyListener.META_SHIFT_ON));
        assertEquals(0, MetaKeyKeyListener.getMetaState("abc", MetaKeyKeyListener.META_ALT_ON));
        assertEquals(0,
                MetaKeyKeyListener.getMetaState("@#$$#^$^", MetaKeyKeyListener.META_SYM_ON));
    }

    @Test
    public void testGetMetaState2() {
        assertEquals(0, MetaKeyKeyListener.getMetaState(0));
        assertEquals(MetaKeyKeyListener.META_SHIFT_ON,
                MetaKeyKeyListener.getMetaState(MetaKeyKeyListener.META_SHIFT_ON));
        assertEquals(MetaKeyKeyListener.META_CAP_LOCKED,
                MetaKeyKeyListener.getMetaState(MetaKeyKeyListener.META_CAP_LOCKED));

        assertEquals(0, MetaKeyKeyListener.getMetaState(0, MetaKeyKeyListener.META_SYM_ON));
        assertEquals(1, MetaKeyKeyListener.getMetaState(MetaKeyKeyListener.META_SYM_ON,
                MetaKeyKeyListener.META_SYM_ON));
        assertEquals(2, MetaKeyKeyListener.getMetaState(MetaKeyKeyListener.META_SYM_LOCKED,
                MetaKeyKeyListener.META_SYM_ON));
    }

    @Test
    public void testGetMetaState_withCharSequenceAndKeyEvent() {
        KeyEvent event = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_0, 0,
                KeyEvent.META_SHIFT_MASK);

        assertEquals(KeyEvent.META_SHIFT_MASK, MetaKeyKeyListener.getMetaState(null, event));
        assertEquals(KeyEvent.META_SHIFT_MASK, MetaKeyKeyListener.getMetaState("", event));
    }

    @Test
    public void testGetMetaState_withCharSequenceAndMetaAndKeyEvent() {
        KeyEvent event = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_0, 0,
                KeyEvent.META_CTRL_ON);

        assertEquals(0, MetaKeyKeyListener.getMetaState("", MetaKeyKeyListener.META_SHIFT_ON,
                event));

        event = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_0, 0,
                KeyEvent.META_SHIFT_ON);

        assertEquals(1, MetaKeyKeyListener.getMetaState("", MetaKeyKeyListener.META_SHIFT_ON,
                event));

        event = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_0, 0,
                MetaKeyKeyListener.META_SYM_LOCKED);

        assertEquals(2, MetaKeyKeyListener.getMetaState("", MetaKeyKeyListener.META_SYM_ON,
                event));

        assertEquals(2, MetaKeyKeyListener.getMetaState(null, MetaKeyKeyListener.META_SYM_ON,
                event));
    }

    @Test
    public void testIsMetaTracker() {
        assertFalse(MetaKeyKeyListener.isMetaTracker("123456", new Object()));
        assertFalse(MetaKeyKeyListener.isMetaTracker("abc", new Object()));
        assertFalse(MetaKeyKeyListener.isMetaTracker("@#$$#^$^", new Object()));
    }

    @Test
    public void testIsSelectingMetaTracker() {
        assertFalse(MetaKeyKeyListener.isSelectingMetaTracker("123456", new Object()));
        assertFalse(MetaKeyKeyListener.isSelectingMetaTracker("abc", new Object()));
        assertFalse(MetaKeyKeyListener.isSelectingMetaTracker("@#$$#^$^", new Object()));
    }

    @Test
    public void testResetLockedMeta() {
        MockMetaKeyKeyListener mockMetaKeyKeyListener = new MockMetaKeyKeyListener();

        MockSpannable str = spy(new MockSpannable());
        str.setSpan(new Object(), 0, 0, Spannable.SPAN_MARK_MARK
                | (4 << Spannable.SPAN_USER_SHIFT));
        verify(str, never()).removeSpan(any());
        mockMetaKeyKeyListener.callResetLockedMeta(str);
        verify(str, atLeastOnce()).removeSpan(any());

        str = spy(new MockSpannable());
        str.setSpan(new Object(), 0, 0, Spannable.SPAN_MARK_POINT);
        verify(str, never()).removeSpan(any());
        mockMetaKeyKeyListener.callResetLockedMeta(str);
        verify(str, never()).removeSpan(any());

        try {
            mockMetaKeyKeyListener.callResetLockedMeta(null);
            fail("should throw NullPointerException.");
        } catch (NullPointerException e) {
        }
    }

    @Test
    public void testResetLockedMeta2() {
        long state = MetaKeyKeyListener.resetLockedMeta(MetaKeyKeyListener.META_CAP_LOCKED);
        assertEquals(0, state);

        state = MetaKeyKeyListener.resetLockedMeta(MetaKeyKeyListener.META_SHIFT_ON);
        assertEquals(MetaKeyKeyListener.META_SHIFT_ON, state);

        state = MetaKeyKeyListener.resetLockedMeta(MetaKeyKeyListener.META_ALT_LOCKED);
        assertEquals(0, state);

        state = MetaKeyKeyListener.resetLockedMeta(MetaKeyKeyListener.META_ALT_ON);
        assertEquals(MetaKeyKeyListener.META_ALT_ON, state);

        state = MetaKeyKeyListener.resetLockedMeta(MetaKeyKeyListener.META_SYM_LOCKED);
        assertEquals(0, state);

        state = MetaKeyKeyListener.resetLockedMeta(MetaKeyKeyListener.META_SYM_ON);
        assertEquals(MetaKeyKeyListener.META_SYM_ON, state);
    }

    @Test
    public void testClearMetaKeyState() {
        final MetaKeyKeyListener numberKeyListener = new DateKeyListener();
        CharSequence str = "123456";
        Editable text = Editable.Factory.getInstance().newEditable(str);
        text.setSpan(Selection.SELECTION_START, 0, 0, Spanned.SPAN_POINT_POINT);
        text.setSpan(Selection.SELECTION_END, str.length(), str.length(), Spanned.SPAN_POINT_POINT);
        numberKeyListener.clearMetaKeyState(null, text, MetaKeyKeyListener.META_SHIFT_ON);
        assertEquals(Spanned.SPAN_POINT_POINT, text.getSpanFlags(Selection.SELECTION_START));
        assertEquals(Spanned.SPAN_POINT_POINT, text.getSpanFlags(Selection.SELECTION_END));

        str = "abc";
        text = Editable.Factory.getInstance().newEditable(str);
        text.setSpan(Selection.SELECTION_START, 0, 0, Spanned.SPAN_POINT_POINT);
        text.setSpan(Selection.SELECTION_END, str.length(), str.length(), Spanned.SPAN_POINT_POINT);
        numberKeyListener.clearMetaKeyState(null, text, MetaKeyKeyListener.META_ALT_ON);
        assertEquals(Spanned.SPAN_POINT_POINT, text.getSpanFlags(Selection.SELECTION_START));
        assertEquals(Spanned.SPAN_POINT_POINT, text.getSpanFlags(Selection.SELECTION_END));

        str = "#@%#$^%^";
        text = Editable.Factory.getInstance().newEditable(str);
        text.setSpan(Selection.SELECTION_START, 0, 0, Spanned.SPAN_POINT_POINT);
        text.setSpan(Selection.SELECTION_END, str.length(), str.length(), Spanned.SPAN_POINT_POINT);
        numberKeyListener.clearMetaKeyState(null, text, MetaKeyKeyListener.META_SYM_ON);
        assertEquals(Spanned.SPAN_POINT_POINT, text.getSpanFlags(Selection.SELECTION_START));
        assertEquals(Spanned.SPAN_POINT_POINT, text.getSpanFlags(Selection.SELECTION_END));
    }

    @Test
    public void testClearMetaKeyState2() {
        CharSequence str = "123456";
        Editable text = Editable.Factory.getInstance().newEditable(str);
        text.setSpan(Selection.SELECTION_START, 0, 0, Spanned.SPAN_POINT_POINT);
        text.setSpan(Selection.SELECTION_END, str.length(), str.length(), Spanned.SPAN_POINT_POINT);
        MetaKeyKeyListener.clearMetaKeyState(text, MetaKeyKeyListener.META_SHIFT_ON);
        assertEquals(Spanned.SPAN_POINT_POINT, text.getSpanFlags(Selection.SELECTION_START));
        assertEquals(Spanned.SPAN_POINT_POINT, text.getSpanFlags(Selection.SELECTION_END));

        str = "abc";
        text = Editable.Factory.getInstance().newEditable(str);
        text.setSpan(Selection.SELECTION_START, 0, 0, Spanned.SPAN_POINT_POINT);
        text.setSpan(Selection.SELECTION_END, str.length(), str.length(), Spanned.SPAN_POINT_POINT);
        MetaKeyKeyListener.clearMetaKeyState(text, MetaKeyKeyListener.META_ALT_ON);
        assertEquals(Spanned.SPAN_POINT_POINT, text.getSpanFlags(Selection.SELECTION_START));
        assertEquals(Spanned.SPAN_POINT_POINT, text.getSpanFlags(Selection.SELECTION_END));

        str = "#@%#$^%^";
        text = Editable.Factory.getInstance().newEditable(str);
        text.setSpan(Selection.SELECTION_START, 0, 0, Spanned.SPAN_POINT_POINT);
        text.setSpan(Selection.SELECTION_END, str.length(), str.length(), Spanned.SPAN_POINT_POINT);
        MetaKeyKeyListener.clearMetaKeyState(text, MetaKeyKeyListener.META_SYM_ON);
        assertEquals(Spanned.SPAN_POINT_POINT, text.getSpanFlags(Selection.SELECTION_START));
        assertEquals(Spanned.SPAN_POINT_POINT, text.getSpanFlags(Selection.SELECTION_END));
    }

    @Test
    public void testClearMetaKeyState3() {
        final MetaKeyKeyListener metaKeyKeyListener = new MetaKeyKeyListener() {};
        long state = metaKeyKeyListener.clearMetaKeyState(MetaKeyKeyListener.META_CAP_LOCKED,
                MetaKeyKeyListener.META_SHIFT_ON);
        assertEquals(0, state);

        state = metaKeyKeyListener.clearMetaKeyState(MetaKeyKeyListener.META_SHIFT_ON,
                MetaKeyKeyListener.META_SHIFT_ON);
        assertEquals(MetaKeyKeyListener.META_SHIFT_ON, state);

        state = metaKeyKeyListener.clearMetaKeyState(MetaKeyKeyListener.META_ALT_LOCKED,
                MetaKeyKeyListener.META_ALT_ON);
        assertEquals(0, state);

        state = metaKeyKeyListener.clearMetaKeyState(MetaKeyKeyListener.META_ALT_ON,
                MetaKeyKeyListener.META_ALT_ON);
        assertEquals(MetaKeyKeyListener.META_ALT_ON, state);

        state = metaKeyKeyListener.clearMetaKeyState(MetaKeyKeyListener.META_SYM_LOCKED,
                MetaKeyKeyListener.META_SYM_ON);
        assertEquals(0, state);

        state = metaKeyKeyListener.clearMetaKeyState(MetaKeyKeyListener.META_SYM_ON,
                MetaKeyKeyListener.META_SYM_ON);
        assertEquals(MetaKeyKeyListener.META_SYM_ON, state);
    }

    @Test
    public void testHandleKeyDown() {
        KeyEvent fullEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT,
                0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0);
        long state = MetaKeyKeyListener.handleKeyDown(MetaKeyKeyListener.META_CAP_LOCKED,
                KeyEvent.KEYCODE_SHIFT_LEFT, fullEvent);
        assertEquals(0, state);
    }

    @Test
    public void testHandleKeyUp() {
        KeyEvent fullEvent = new KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT,
                0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0);
        long state = MetaKeyKeyListener.handleKeyUp(MetaKeyKeyListener.META_CAP_LOCKED,
                KeyEvent.KEYCODE_SHIFT_LEFT, fullEvent);
        assertEquals(0, state);
    }

    /**
     * A mocked {@link android.text.method.MetaKeyKeyListener} for testing purposes.
     *
     * Allows {@link MetaKeyKeyListenerTest} to call
     * {@link android.text.method.MetaKeyKeyListener.resetLockedMeta(Spannable)}.
     */
    private class MockMetaKeyKeyListener extends MetaKeyKeyListener {
        public void callResetLockedMeta(Spannable content) {
            MetaKeyKeyListener.resetLockedMeta(content);
        }
    }

    /**
     * A mocked {@link android.text.Spannable} for testing purposes.
     */
    public static class MockSpannable implements Spannable {
        private int mFlags;

        public void setSpan(Object what, int start, int end, int flags) {
            mFlags = flags;
        }

        public void removeSpan(Object what) {
        }

        public <T> T[] getSpans(int start, int end, Class<T> type) {
            return null;
        }

        public int getSpanStart(Object tag) {
            return 0;
        }

        public int getSpanEnd(Object tag) {
            return 0;
        }

        public int getSpanFlags(Object tag) {
            return mFlags;
        }

        @SuppressWarnings("unchecked")
        public int nextSpanTransition(int start, int limit, Class type) {
            return 0;
        }

        public char charAt(int index) {
            return 0;
        }

        public int length() {
            return 0;
        }

        public CharSequence subSequence(int start, int end) {
            return null;
        }
    }
}
