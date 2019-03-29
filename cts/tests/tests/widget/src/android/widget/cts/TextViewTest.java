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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.refEq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.Instrumentation.ActivityMonitor;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.icu.lang.UCharacter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.LocaleList;
import android.os.Looper;
import android.os.Parcelable;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.MediumTest;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.text.TextWatcher;
import android.text.method.ArrowKeyMovementMethod;
import android.text.method.DateKeyListener;
import android.text.method.DateTimeKeyListener;
import android.text.method.DialerKeyListener;
import android.text.method.DigitsKeyListener;
import android.text.method.KeyListener;
import android.text.method.LinkMovementMethod;
import android.text.method.MovementMethod;
import android.text.method.PasswordTransformationMethod;
import android.text.method.QwertyKeyListener;
import android.text.method.SingleLineTransformationMethod;
import android.text.method.TextKeyListener;
import android.text.method.TextKeyListener.Capitalize;
import android.text.method.TimeKeyListener;
import android.text.method.TransformationMethod;
import android.text.style.ClickableSpan;
import android.text.style.ImageSpan;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;
import android.text.util.Linkify;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.textclassifier.TextClassification;
import android.view.textclassifier.TextClassifier;
import android.view.textclassifier.TextSelection;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Scroller;
import android.widget.TextView;
import android.widget.TextView.BufferType;
import android.widget.cts.util.TestUtils;

import com.android.compatibility.common.util.CtsKeyEventUtil;
import com.android.compatibility.common.util.CtsTouchUtils;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.WidgetTestUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.util.Arrays;
import java.util.Locale;

/**
 * Test {@link TextView}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class TextViewTest {
    private Instrumentation mInstrumentation;
    private Activity mActivity;
    private TextView mTextView;
    private TextView mSecondTextView;

    private static final String LONG_TEXT = "This is a really long string which exceeds "
            + "the width of the view. New devices have a much larger screen which "
            + "actually enables long strings to be displayed with no fading. "
            + "I have made this string longer to fix this case. If you are correcting "
            + "this text, I would love to see the kind of devices you guys now use!";
    private static final long TIMEOUT = 5000;

    private static final int SMARTSELECT_START = 0;
    private static final int SMARTSELECT_END = 40;
    private static final TextClassifier FAKE_TEXT_CLASSIFIER = new TextClassifier() {
        @Override
        public TextSelection suggestSelection(
                CharSequence text, int start, int end, LocaleList locales) {
            return new TextSelection.Builder(SMARTSELECT_START, SMARTSELECT_END).build();
        }

        @Override
        public TextClassification classifyText(
                CharSequence text, int start, int end, LocaleList locales) {
            return new TextClassification.Builder().build();
        }
    };
    private static final int CLICK_TIMEOUT = ViewConfiguration.getDoubleTapTimeout() + 50;

    private CharSequence mTransformedText;
    private Handler mHandler = new Handler(Looper.getMainLooper());

    @Rule
    public ActivityTestRule<TextViewCtsActivity> mActivityRule =
            new ActivityTestRule<>(TextViewCtsActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
        PollingCheck.waitFor(mActivity::hasWindowFocus);
    }

    /**
     * Promotes the TextView to editable and places focus in it to allow simulated typing. Used in
     * test methods annotated with {@link UiThreadTest}.
     */
    private void initTextViewForTyping() {
        mTextView = findTextView(R.id.textview_text);
        mTextView.setKeyListener(QwertyKeyListener.getInstance(false, Capitalize.NONE));
        mTextView.setText("", BufferType.EDITABLE);
        mTextView.requestFocus();
        // Disable smart selection
        mTextView.setTextClassifier(TextClassifier.NO_OP);
    }

    /**
     * Used in test methods that can not entirely be run on the UiThread (e.g: tests that need to
     * emulate touches and/or key presses).
     */
    private void initTextViewForTypingOnUiThread() throws Throwable {
        mActivityRule.runOnUiThread(this::initTextViewForTyping);
        mInstrumentation.waitForIdleSync();
    }

    @UiThreadTest
    @Test
    public void testConstructorOnUiThread() {
        verifyConstructor();
    }

    @Test
    public void testConstructorOffUiThread() {
        verifyConstructor();
    }

    private void verifyConstructor() {
        new TextView(mActivity);
        new TextView(mActivity, null);
        new TextView(mActivity, null, android.R.attr.textViewStyle);
        new TextView(mActivity, null, 0, android.R.style.Widget_DeviceDefault_TextView);
        new TextView(mActivity, null, 0, android.R.style.Widget_DeviceDefault_Light_TextView);
        new TextView(mActivity, null, 0, android.R.style.Widget_Material_TextView);
        new TextView(mActivity, null, 0, android.R.style.Widget_Material_Light_TextView);
    }

    @UiThreadTest
    @Test
    public void testAccessText() {
        TextView tv = findTextView(R.id.textview_text);

        String expected = mActivity.getResources().getString(R.string.text_view_hello);
        tv.setText(expected);
        assertEquals(expected, tv.getText().toString());

        tv.setText(null);
        assertEquals("", tv.getText().toString());
    }

    @UiThreadTest
    @Test
    public void testGetLineHeight() {
        mTextView = new TextView(mActivity);
        assertTrue(mTextView.getLineHeight() > 0);

        mTextView.setLineSpacing(1.2f, 1.5f);
        assertTrue(mTextView.getLineHeight() > 0);
    }

    @Test
    public void testGetLayout() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            mTextView = findTextView(R.id.textview_text);
            mTextView.setGravity(Gravity.CENTER);
        });
        mInstrumentation.waitForIdleSync();
        assertNotNull(mTextView.getLayout());

        TestLayoutRunnable runnable = new TestLayoutRunnable(mTextView) {
            public void run() {
                // change the text of TextView.
                mTextView.setText("Hello, Android!");
                saveLayout();
            }
        };
        mActivityRule.runOnUiThread(runnable);
        mInstrumentation.waitForIdleSync();
        assertNull(runnable.getLayout());
        assertNotNull(mTextView.getLayout());
    }

    @Test
    public void testAccessKeyListener() throws Throwable {
        mActivityRule.runOnUiThread(() -> mTextView = findTextView(R.id.textview_text));
        mInstrumentation.waitForIdleSync();

        assertNull(mTextView.getKeyListener());

        final KeyListener digitsKeyListener = DigitsKeyListener.getInstance();

        mActivityRule.runOnUiThread(() -> mTextView.setKeyListener(digitsKeyListener));
        mInstrumentation.waitForIdleSync();
        assertSame(digitsKeyListener, mTextView.getKeyListener());

        final QwertyKeyListener qwertyKeyListener
                = QwertyKeyListener.getInstance(false, Capitalize.NONE);
        mActivityRule.runOnUiThread(() -> mTextView.setKeyListener(qwertyKeyListener));
        mInstrumentation.waitForIdleSync();
        assertSame(qwertyKeyListener, mTextView.getKeyListener());
    }

    @Test
    public void testAccessMovementMethod() throws Throwable {
        final CharSequence LONG_TEXT = "Scrolls the specified widget to the specified "
                + "coordinates, except constrains the X scrolling position to the horizontal "
                + "regions of the text that will be visible after scrolling to "
                + "the specified Y position.";
        final int selectionStart = 10;
        final int selectionEnd = LONG_TEXT.length();
        final MovementMethod movementMethod = ArrowKeyMovementMethod.getInstance();
        mActivityRule.runOnUiThread(() -> {
            mTextView = findTextView(R.id.textview_text);
            mTextView.setMovementMethod(movementMethod);
            mTextView.setText(LONG_TEXT, BufferType.EDITABLE);
            Selection.setSelection((Editable) mTextView.getText(),
                    selectionStart, selectionEnd);
            mTextView.requestFocus();
        });
        mInstrumentation.waitForIdleSync();

        assertSame(movementMethod, mTextView.getMovementMethod());
        assertEquals(selectionStart, Selection.getSelectionStart(mTextView.getText()));
        assertEquals(selectionEnd, Selection.getSelectionEnd(mTextView.getText()));
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_SHIFT_LEFT,
                KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_DPAD_UP);
        // the selection has been removed.
        assertEquals(selectionStart, Selection.getSelectionStart(mTextView.getText()));
        assertEquals(selectionStart, Selection.getSelectionEnd(mTextView.getText()));

        mActivityRule.runOnUiThread(() -> {
            mTextView.setMovementMethod(null);
            Selection.setSelection((Editable) mTextView.getText(),
                    selectionStart, selectionEnd);
            mTextView.requestFocus();
        });
        mInstrumentation.waitForIdleSync();

        assertNull(mTextView.getMovementMethod());
        assertEquals(selectionStart, Selection.getSelectionStart(mTextView.getText()));
        assertEquals(selectionEnd, Selection.getSelectionEnd(mTextView.getText()));
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_SHIFT_LEFT,
                KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_DPAD_UP);
        // the selection will not be changed.
        assertEquals(selectionStart, Selection.getSelectionStart(mTextView.getText()));
        assertEquals(selectionEnd, Selection.getSelectionEnd(mTextView.getText()));
    }

    @UiThreadTest
    @Test
    public void testLength() {
        mTextView = findTextView(R.id.textview_text);

        String content = "This is content";
        mTextView.setText(content);
        assertEquals(content.length(), mTextView.length());

        mTextView.setText("");
        assertEquals(0, mTextView.length());

        mTextView.setText(null);
        assertEquals(0, mTextView.length());
    }

    @UiThreadTest
    @Test
    public void testAccessGravity() {
        mActivity.setContentView(R.layout.textview_gravity);

        mTextView = findTextView(R.id.gravity_default);
        assertEquals(Gravity.TOP | Gravity.START, mTextView.getGravity());

        mTextView = findTextView(R.id.gravity_bottom);
        assertEquals(Gravity.BOTTOM | Gravity.START, mTextView.getGravity());

        mTextView = findTextView(R.id.gravity_right);
        assertEquals(Gravity.TOP | Gravity.RIGHT, mTextView.getGravity());

        mTextView = findTextView(R.id.gravity_center);
        assertEquals(Gravity.CENTER, mTextView.getGravity());

        mTextView = findTextView(R.id.gravity_fill);
        assertEquals(Gravity.FILL, mTextView.getGravity());

        mTextView = findTextView(R.id.gravity_center_vertical_right);
        assertEquals(Gravity.CENTER_VERTICAL | Gravity.RIGHT, mTextView.getGravity());

        mTextView.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        assertEquals(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, mTextView.getGravity());
        mTextView.setGravity(Gravity.FILL);
        assertEquals(Gravity.FILL, mTextView.getGravity());
        mTextView.setGravity(Gravity.CENTER);
        assertEquals(Gravity.CENTER, mTextView.getGravity());

        mTextView.setGravity(Gravity.NO_GRAVITY);
        assertEquals(Gravity.TOP | Gravity.START, mTextView.getGravity());

        mTextView.setGravity(Gravity.RIGHT);
        assertEquals(Gravity.TOP | Gravity.RIGHT, mTextView.getGravity());

        mTextView.setGravity(Gravity.FILL_VERTICAL);
        assertEquals(Gravity.FILL_VERTICAL | Gravity.START, mTextView.getGravity());

        //test negative input value.
        mTextView.setGravity(-1);
        assertEquals(-1, mTextView.getGravity());
    }

    @Retention(SOURCE)
    @IntDef({EditorInfo.IME_ACTION_UNSPECIFIED, EditorInfo.IME_ACTION_NONE,
            EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_SEARCH, EditorInfo.IME_ACTION_SEND,
            EditorInfo.IME_ACTION_NEXT, EditorInfo.IME_ACTION_DONE, EditorInfo.IME_ACTION_PREVIOUS})
    private @interface ImeOptionAction {}

    @Retention(SOURCE)
    @IntDef(flag = true,
            value = {EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING,
                    EditorInfo.IME_FLAG_NO_FULLSCREEN, EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS,
                    EditorInfo.IME_FLAG_NAVIGATE_NEXT, EditorInfo.IME_FLAG_NO_EXTRACT_UI,
                    EditorInfo.IME_FLAG_NO_ACCESSORY_ACTION, EditorInfo.IME_FLAG_NO_ENTER_ACTION,
                    EditorInfo.IME_FLAG_FORCE_ASCII})
    private @interface ImeOptionFlags {}

    private static void assertImeOptions(TextView textView,
            @ImeOptionAction int expectedImeOptionAction,
            @ImeOptionFlags int expectedImeOptionFlags) {
        final int actualAction = textView.getImeOptions() & EditorInfo.IME_MASK_ACTION;
        final int actualFlags = textView.getImeOptions() & ~EditorInfo.IME_MASK_ACTION;
        assertEquals(expectedImeOptionAction, actualAction);
        assertEquals(expectedImeOptionFlags, actualFlags);
    }

    @UiThreadTest
    @Test
    public void testImeOptions() {
        mActivity.setContentView(R.layout.textview_imeoptions);

        // Test "normal" to be a synonym EditorInfo.IME_NULL
        assertEquals(EditorInfo.IME_NULL,
                mActivity.<TextView>findViewById(R.id.textview_imeoption_normal).getImeOptions());

        // Test EditorInfo.IME_ACTION_*
        assertImeOptions(
                mActivity.findViewById(R.id.textview_imeoption_action_unspecified),
                EditorInfo.IME_ACTION_UNSPECIFIED, 0);
        assertImeOptions(
                mActivity.findViewById(R.id.textview_imeoption_action_none),
                EditorInfo.IME_ACTION_NONE, 0);
        assertImeOptions(
                mActivity.findViewById(R.id.textview_imeoption_action_go),
                EditorInfo.IME_ACTION_GO, 0);
        assertImeOptions(
                mActivity.findViewById(R.id.textview_imeoption_action_search),
                EditorInfo.IME_ACTION_SEARCH, 0);
        assertImeOptions(
                mActivity.findViewById(R.id.textview_imeoption_action_send),
                EditorInfo.IME_ACTION_SEND, 0);
        assertImeOptions(
                mActivity.findViewById(R.id.textview_imeoption_action_next),
                EditorInfo.IME_ACTION_NEXT, 0);
        assertImeOptions(
                mActivity.findViewById(R.id.textview_imeoption_action_done),
                EditorInfo.IME_ACTION_DONE, 0);
        assertImeOptions(
                mActivity.findViewById(R.id.textview_imeoption_action_previous),
                EditorInfo.IME_ACTION_PREVIOUS, 0);

        // Test EditorInfo.IME_FLAG_*
        assertImeOptions(
                mActivity.findViewById(R.id.textview_imeoption_no_personalized_learning),
                EditorInfo.IME_ACTION_UNSPECIFIED,
                EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        assertImeOptions(
                mActivity.findViewById(R.id.textview_imeoption_no_fullscreen),
                EditorInfo.IME_ACTION_UNSPECIFIED,
                EditorInfo.IME_FLAG_NO_FULLSCREEN);
        assertImeOptions(
                mActivity.findViewById(R.id.textview_imeoption_navigation_previous),
                EditorInfo.IME_ACTION_UNSPECIFIED,
                EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS);
        assertImeOptions(
                mActivity.findViewById(R.id.textview_imeoption_navigation_next),
                EditorInfo.IME_ACTION_UNSPECIFIED,
                EditorInfo.IME_FLAG_NAVIGATE_NEXT);
        assertImeOptions(
                mActivity.findViewById(R.id.textview_imeoption_no_extract_ui),
                EditorInfo.IME_ACTION_UNSPECIFIED,
                EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        assertImeOptions(
                mActivity.findViewById(R.id.textview_imeoption_no_accessory_action),
                EditorInfo.IME_ACTION_UNSPECIFIED,
                EditorInfo.IME_FLAG_NO_ACCESSORY_ACTION);
        assertImeOptions(
                mActivity.findViewById(R.id.textview_imeoption_no_enter_action),
                EditorInfo.IME_ACTION_UNSPECIFIED,
                EditorInfo.IME_FLAG_NO_ENTER_ACTION);
        assertImeOptions(
                mActivity.findViewById(R.id.textview_imeoption_force_ascii),
                EditorInfo.IME_ACTION_UNSPECIFIED,
                EditorInfo.IME_FLAG_FORCE_ASCII);

        // test action + multiple flags
        assertImeOptions(
                mActivity.findViewById(
                        R.id.textview_imeoption_action_go_nagivate_next_no_extract_ui_force_ascii),
                EditorInfo.IME_ACTION_GO,
                EditorInfo.IME_FLAG_NAVIGATE_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI
                        | EditorInfo.IME_FLAG_FORCE_ASCII);
    }

    @Test
    public void testAccessAutoLinkMask() throws Throwable {
        mTextView = findTextView(R.id.textview_text);
        final CharSequence text1 =
                new SpannableString("URL: http://www.google.com. mailto: account@gmail.com");
        mActivityRule.runOnUiThread(() -> {
            mTextView.setAutoLinkMask(Linkify.ALL);
            mTextView.setText(text1, BufferType.EDITABLE);
        });
        mInstrumentation.waitForIdleSync();
        assertEquals(Linkify.ALL, mTextView.getAutoLinkMask());

        Spannable spanString = (Spannable) mTextView.getText();
        URLSpan[] spans = spanString.getSpans(0, spanString.length(), URLSpan.class);
        assertNotNull(spans);
        assertEquals(2, spans.length);
        assertEquals("http://www.google.com", spans[0].getURL());
        assertEquals("mailto:account@gmail.com", spans[1].getURL());

        final CharSequence text2 =
            new SpannableString("name: Jack. tel: +41 44 800 8999");
        mActivityRule.runOnUiThread(() -> {
            mTextView.setAutoLinkMask(Linkify.PHONE_NUMBERS);
            mTextView.setText(text2, BufferType.EDITABLE);
        });
        mInstrumentation.waitForIdleSync();
        assertEquals(Linkify.PHONE_NUMBERS, mTextView.getAutoLinkMask());

        spanString = (Spannable) mTextView.getText();
        spans = spanString.getSpans(0, spanString.length(), URLSpan.class);
        assertNotNull(spans);
        assertEquals(1, spans.length);
        assertEquals("tel:+41448008999", spans[0].getURL());

        layout(R.layout.textview_autolink);
        // 1 for web, 2 for email, 4 for phone, 7 for all(web|email|phone)
        assertEquals(0, getAutoLinkMask(R.id.autolink_default));
        assertEquals(Linkify.WEB_URLS, getAutoLinkMask(R.id.autolink_web));
        assertEquals(Linkify.EMAIL_ADDRESSES, getAutoLinkMask(R.id.autolink_email));
        assertEquals(Linkify.PHONE_NUMBERS, getAutoLinkMask(R.id.autolink_phone));
        assertEquals(Linkify.ALL, getAutoLinkMask(R.id.autolink_all));
        assertEquals(Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES,
                getAutoLinkMask(R.id.autolink_compound1));
        assertEquals(Linkify.WEB_URLS | Linkify.PHONE_NUMBERS,
                getAutoLinkMask(R.id.autolink_compound2));
        assertEquals(Linkify.EMAIL_ADDRESSES | Linkify.PHONE_NUMBERS,
                getAutoLinkMask(R.id.autolink_compound3));
        assertEquals(Linkify.PHONE_NUMBERS | Linkify.ALL,
                getAutoLinkMask(R.id.autolink_compound4));
    }

    @UiThreadTest
    @Test
    public void testAccessTextSize() {
        DisplayMetrics metrics = mActivity.getResources().getDisplayMetrics();

        mTextView = new TextView(mActivity);
        mTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, 20f);
        assertEquals(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, 20f, metrics),
                mTextView.getTextSize(), 0.01f);

        mTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f);
        assertEquals(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20f, metrics),
                mTextView.getTextSize(), 0.01f);

        mTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        assertEquals(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 20f, metrics),
                mTextView.getTextSize(), 0.01f);

        // setTextSize by default unit "sp"
        mTextView.setTextSize(20f);
        assertEquals(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 20f, metrics),
                mTextView.getTextSize(), 0.01f);

        mTextView.setTextSize(200f);
        assertEquals(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 200f, metrics),
                mTextView.getTextSize(), 0.01f);
    }

    @UiThreadTest
    @Test
    public void testAccessTextColor() {
        mTextView = new TextView(mActivity);

        mTextView.setTextColor(Color.GREEN);
        assertEquals(Color.GREEN, mTextView.getCurrentTextColor());
        assertSame(ColorStateList.valueOf(Color.GREEN), mTextView.getTextColors());

        mTextView.setTextColor(Color.BLACK);
        assertEquals(Color.BLACK, mTextView.getCurrentTextColor());
        assertSame(ColorStateList.valueOf(Color.BLACK), mTextView.getTextColors());

        mTextView.setTextColor(Color.RED);
        assertEquals(Color.RED, mTextView.getCurrentTextColor());
        assertSame(ColorStateList.valueOf(Color.RED), mTextView.getTextColors());

        // using ColorStateList
        // normal
        ColorStateList colors = new ColorStateList(new int[][] {
                new int[] { android.R.attr.state_focused}, new int[0] },
                new int[] { Color.rgb(0, 255, 0), Color.BLACK });
        mTextView.setTextColor(colors);
        assertSame(colors, mTextView.getTextColors());
        assertEquals(Color.BLACK, mTextView.getCurrentTextColor());

        // exceptional
        try {
            mTextView.setTextColor(null);
            fail("Should thrown exception if the colors is null");
        } catch (NullPointerException e){
        }
    }

    @Test
    public void testGetTextColor() {
        // TODO: How to get a suitable TypedArray to test this method.

        try {
            TextView.getTextColor(mActivity, null, -1);
            fail("There should be a NullPointerException thrown out.");
        } catch (NullPointerException e) {
        }
    }

    @Test
    public void testAccessHighlightColor() throws Throwable {
        final TextView textView = (TextView) mActivity.findViewById(R.id.textview_text);

        mActivityRule.runOnUiThread(() -> {
            textView.setTextIsSelectable(true);
            textView.setText("abcd", BufferType.EDITABLE);
            textView.setHighlightColor(Color.BLUE);
        });
        mInstrumentation.waitForIdleSync();

        assertTrue(textView.isTextSelectable());
        assertEquals(Color.BLUE, textView.getHighlightColor());

        // Long click on the text selects all text and shows selection handlers. The view has an
        // attribute layout_width="wrap_content", so clicked location (the center of the view)
        // should be on the text.
        CtsTouchUtils.emulateLongPressOnViewCenter(mInstrumentation, textView);

        // At this point the entire content of our TextView should be selected and highlighted
        // with blue. Now change the highlight to red while the selection is still on.
        mActivityRule.runOnUiThread(() -> textView.setHighlightColor(Color.RED));
        mInstrumentation.waitForIdleSync();

        assertEquals(Color.RED, textView.getHighlightColor());
        assertTrue(TextUtils.equals("abcd", textView.getText()));

        // Remove the selection
        mActivityRule.runOnUiThread(() -> Selection.removeSelection((Spannable) textView.getText()));
        mInstrumentation.waitForIdleSync();

        // And switch highlight to green after the selection has been removed
        mActivityRule.runOnUiThread(() -> textView.setHighlightColor(Color.GREEN));
        mInstrumentation.waitForIdleSync();

        assertEquals(Color.GREEN, textView.getHighlightColor());
        assertTrue(TextUtils.equals("abcd", textView.getText()));
    }

    @UiThreadTest
    @Test
    public void testSetShadowLayer() {
        // test values
        final MockTextView mockTextView = new MockTextView(mActivity);

        mockTextView.setShadowLayer(1.0f, 0.3f, 0.4f, Color.CYAN);
        assertEquals(Color.CYAN, mockTextView.getShadowColor());
        assertEquals(0.3f, mockTextView.getShadowDx(), 0.0f);
        assertEquals(0.4f, mockTextView.getShadowDy(), 0.0f);
        assertEquals(1.0f, mockTextView.getShadowRadius(), 0.0f);

        // shadow is placed to the left and below the text
        mockTextView.setShadowLayer(1.0f, 0.3f, 0.3f, Color.CYAN);
        assertTrue(mockTextView.isPaddingOffsetRequired());
        assertEquals(0, mockTextView.getLeftPaddingOffset());
        assertEquals(0, mockTextView.getTopPaddingOffset());
        assertEquals(1, mockTextView.getRightPaddingOffset());
        assertEquals(1, mockTextView.getBottomPaddingOffset());

        // shadow is placed to the right and above the text
        mockTextView.setShadowLayer(1.0f, -0.8f, -0.8f, Color.CYAN);
        assertTrue(mockTextView.isPaddingOffsetRequired());
        assertEquals(-1, mockTextView.getLeftPaddingOffset());
        assertEquals(-1, mockTextView.getTopPaddingOffset());
        assertEquals(0, mockTextView.getRightPaddingOffset());
        assertEquals(0, mockTextView.getBottomPaddingOffset());

        // no shadow
        mockTextView.setShadowLayer(0.0f, 0.0f, 0.0f, Color.CYAN);
        assertFalse(mockTextView.isPaddingOffsetRequired());
        assertEquals(0, mockTextView.getLeftPaddingOffset());
        assertEquals(0, mockTextView.getTopPaddingOffset());
        assertEquals(0, mockTextView.getRightPaddingOffset());
        assertEquals(0, mockTextView.getBottomPaddingOffset());
    }

    @UiThreadTest
    @Test
    public void testSetSelectAllOnFocus() {
        mActivity.setContentView(R.layout.textview_selectallonfocus);
        String content = "This is the content";
        String blank = "";
        mTextView = findTextView(R.id.selectAllOnFocus_default);
        mTextView.setText(blank, BufferType.SPANNABLE);
        // change the focus
        findTextView(R.id.selectAllOnFocus_dummy).requestFocus();
        assertFalse(mTextView.isFocused());
        mTextView.requestFocus();
        assertTrue(mTextView.isFocused());

        assertEquals(-1, mTextView.getSelectionStart());
        assertEquals(-1, mTextView.getSelectionEnd());

        mTextView.setText(content, BufferType.SPANNABLE);
        mTextView.setSelectAllOnFocus(true);
        // change the focus
        findTextView(R.id.selectAllOnFocus_dummy).requestFocus();
        assertFalse(mTextView.isFocused());
        mTextView.requestFocus();
        assertTrue(mTextView.isFocused());

        assertEquals(0, mTextView.getSelectionStart());
        assertEquals(content.length(), mTextView.getSelectionEnd());

        Selection.setSelection((Spannable) mTextView.getText(), 0);
        mTextView.setSelectAllOnFocus(false);
        // change the focus
        findTextView(R.id.selectAllOnFocus_dummy).requestFocus();
        assertFalse(mTextView.isFocused());
        mTextView.requestFocus();
        assertTrue(mTextView.isFocused());

        assertEquals(0, mTextView.getSelectionStart());
        assertEquals(0, mTextView.getSelectionEnd());

        mTextView.setText(blank, BufferType.SPANNABLE);
        mTextView.setSelectAllOnFocus(true);
        // change the focus
        findTextView(R.id.selectAllOnFocus_dummy).requestFocus();
        assertFalse(mTextView.isFocused());
        mTextView.requestFocus();
        assertTrue(mTextView.isFocused());

        assertEquals(0, mTextView.getSelectionStart());
        assertEquals(blank.length(), mTextView.getSelectionEnd());

        Selection.setSelection((Spannable) mTextView.getText(), 0);
        mTextView.setSelectAllOnFocus(false);
        // change the focus
        findTextView(R.id.selectAllOnFocus_dummy).requestFocus();
        assertFalse(mTextView.isFocused());
        mTextView.requestFocus();
        assertTrue(mTextView.isFocused());

        assertEquals(0, mTextView.getSelectionStart());
        assertEquals(0, mTextView.getSelectionEnd());
    }

    @UiThreadTest
    @Test
    public void testGetPaint() {
        mTextView = new TextView(mActivity);
        TextPaint tp = mTextView.getPaint();
        assertNotNull(tp);

        assertEquals(mTextView.getPaintFlags(), tp.getFlags());
    }

    @UiThreadTest
    @Test
    public void testAccessLinksClickable() {
        mActivity.setContentView(R.layout.textview_hint_linksclickable_freezestext);

        mTextView = findTextView(R.id.hint_linksClickable_freezesText_default);
        assertTrue(mTextView.getLinksClickable());

        mTextView = findTextView(R.id.linksClickable_true);
        assertTrue(mTextView.getLinksClickable());

        mTextView = findTextView(R.id.linksClickable_false);
        assertFalse(mTextView.getLinksClickable());

        mTextView.setLinksClickable(false);
        assertFalse(mTextView.getLinksClickable());

        mTextView.setLinksClickable(true);
        assertTrue(mTextView.getLinksClickable());

        assertNull(mTextView.getMovementMethod());

        final CharSequence text = new SpannableString("name: Jack. tel: +41 44 800 8999");

        mTextView.setAutoLinkMask(Linkify.PHONE_NUMBERS);
        mTextView.setText(text, BufferType.EDITABLE);

        // Movement method will be automatically set to LinkMovementMethod
        assertTrue(mTextView.getMovementMethod() instanceof LinkMovementMethod);
    }

    @UiThreadTest
    @Test
    public void testAccessHintTextColor() {
        mTextView = new TextView(mActivity);
        // using int values
        // normal
        mTextView.setHintTextColor(Color.GREEN);
        assertEquals(Color.GREEN, mTextView.getCurrentHintTextColor());
        assertSame(ColorStateList.valueOf(Color.GREEN), mTextView.getHintTextColors());

        mTextView.setHintTextColor(Color.BLUE);
        assertSame(ColorStateList.valueOf(Color.BLUE), mTextView.getHintTextColors());
        assertEquals(Color.BLUE, mTextView.getCurrentHintTextColor());

        mTextView.setHintTextColor(Color.RED);
        assertSame(ColorStateList.valueOf(Color.RED), mTextView.getHintTextColors());
        assertEquals(Color.RED, mTextView.getCurrentHintTextColor());

        // using ColorStateList
        // normal
        ColorStateList colors = new ColorStateList(new int[][] {
                new int[] { android.R.attr.state_focused}, new int[0] },
                new int[] { Color.rgb(0, 255, 0), Color.BLACK });
        mTextView.setHintTextColor(colors);
        assertSame(colors, mTextView.getHintTextColors());
        assertEquals(Color.BLACK, mTextView.getCurrentHintTextColor());

        // exceptional
        mTextView.setHintTextColor(null);
        assertNull(mTextView.getHintTextColors());
        assertEquals(mTextView.getCurrentTextColor(), mTextView.getCurrentHintTextColor());
    }

    @UiThreadTest
    @Test
    public void testAccessLinkTextColor() {
        mTextView = new TextView(mActivity);
        // normal
        mTextView.setLinkTextColor(Color.GRAY);
        assertSame(ColorStateList.valueOf(Color.GRAY), mTextView.getLinkTextColors());
        assertEquals(Color.GRAY, mTextView.getPaint().linkColor);

        mTextView.setLinkTextColor(Color.YELLOW);
        assertSame(ColorStateList.valueOf(Color.YELLOW), mTextView.getLinkTextColors());
        assertEquals(Color.YELLOW, mTextView.getPaint().linkColor);

        mTextView.setLinkTextColor(Color.WHITE);
        assertSame(ColorStateList.valueOf(Color.WHITE), mTextView.getLinkTextColors());
        assertEquals(Color.WHITE, mTextView.getPaint().linkColor);

        ColorStateList colors = new ColorStateList(new int[][] {
                new int[] { android.R.attr.state_expanded}, new int[0] },
                new int[] { Color.rgb(0, 255, 0), Color.BLACK });
        mTextView.setLinkTextColor(colors);
        assertSame(colors, mTextView.getLinkTextColors());
        assertEquals(Color.BLACK, mTextView.getPaint().linkColor);

        mTextView.setLinkTextColor(null);
        assertNull(mTextView.getLinkTextColors());
        assertEquals(Color.BLACK, mTextView.getPaint().linkColor);
    }

    @UiThreadTest
    @Test
    public void testAccessPaintFlags() {
        mTextView = new TextView(mActivity);
        assertEquals(Paint.DEV_KERN_TEXT_FLAG | Paint.EMBEDDED_BITMAP_TEXT_FLAG
                | Paint.ANTI_ALIAS_FLAG, mTextView.getPaintFlags());

        mTextView.setPaintFlags(Paint.UNDERLINE_TEXT_FLAG | Paint.FAKE_BOLD_TEXT_FLAG);
        assertEquals(Paint.UNDERLINE_TEXT_FLAG | Paint.FAKE_BOLD_TEXT_FLAG,
                mTextView.getPaintFlags());

        mTextView.setPaintFlags(Paint.STRIKE_THRU_TEXT_FLAG | Paint.LINEAR_TEXT_FLAG);
        assertEquals(Paint.STRIKE_THRU_TEXT_FLAG | Paint.LINEAR_TEXT_FLAG,
                mTextView.getPaintFlags());
    }

    @Test
    public void testHeight() throws Throwable {
        mTextView = findTextView(R.id.textview_text);
        final int originalHeight = mTextView.getHeight();

        // test setMaxHeight
        int newHeight = originalHeight + 1;
        setMaxHeight(newHeight);
        assertEquals(originalHeight, mTextView.getHeight());
        assertEquals(newHeight, mTextView.getMaxHeight());

        newHeight = originalHeight - 1;
        setMaxHeight(newHeight);
        assertEquals(newHeight, mTextView.getHeight());
        assertEquals(newHeight, mTextView.getMaxHeight());

        newHeight = -1;
        setMaxHeight(newHeight);
        assertEquals(0, mTextView.getHeight());
        assertEquals(newHeight, mTextView.getMaxHeight());

        newHeight = Integer.MAX_VALUE;
        setMaxHeight(newHeight);
        assertEquals(originalHeight, mTextView.getHeight());
        assertEquals(newHeight, mTextView.getMaxHeight());

        // test setMinHeight
        newHeight = originalHeight + 1;
        setMinHeight(newHeight);
        assertEquals(newHeight, mTextView.getHeight());
        assertEquals(newHeight, mTextView.getMinHeight());

        newHeight = originalHeight - 1;
        setMinHeight(newHeight);
        assertEquals(originalHeight, mTextView.getHeight());
        assertEquals(newHeight, mTextView.getMinHeight());

        newHeight = -1;
        setMinHeight(newHeight);
        assertEquals(originalHeight, mTextView.getHeight());
        assertEquals(newHeight, mTextView.getMinHeight());

        // reset min and max height
        setMinHeight(0);
        setMaxHeight(Integer.MAX_VALUE);

        // test setHeight
        newHeight = originalHeight + 1;
        setHeight(newHeight);
        assertEquals(newHeight, mTextView.getHeight());
        assertEquals(newHeight, mTextView.getMaxHeight());
        assertEquals(newHeight, mTextView.getMinHeight());

        newHeight = originalHeight - 1;
        setHeight(newHeight);
        assertEquals(newHeight, mTextView.getHeight());
        assertEquals(newHeight, mTextView.getMaxHeight());
        assertEquals(newHeight, mTextView.getMinHeight());

        newHeight = -1;
        setHeight(newHeight);
        assertEquals(0, mTextView.getHeight());
        assertEquals(newHeight, mTextView.getMaxHeight());
        assertEquals(newHeight, mTextView.getMinHeight());

        setHeight(originalHeight);
        assertEquals(originalHeight, mTextView.getHeight());
        assertEquals(originalHeight, mTextView.getMaxHeight());
        assertEquals(originalHeight, mTextView.getMinHeight());

        // setting max/min lines should cause getMaxHeight/getMinHeight to return -1
        setMaxLines(2);
        assertEquals("Setting maxLines should return -1 fir maxHeight",
                -1, mTextView.getMaxHeight());

        setMinLines(1);
        assertEquals("Setting minLines should return -1 for minHeight",
                -1, mTextView.getMinHeight());
    }

    @Test
    public void testSetMaxLines_toZero_shouldNotDisplayAnyLines() throws Throwable {
        mTextView = findTextView(R.id.textview_text);
        mActivityRule.runOnUiThread(() -> {
            mTextView.setPadding(0, 0, 0, 0);
            mTextView.setText("Single");
            mTextView.setMaxLines(0);
        });
        mInstrumentation.waitForIdleSync();

        final int expectedHeight = mTextView.getTotalPaddingBottom()
                + mTextView.getTotalPaddingTop();

        assertEquals(expectedHeight, mTextView.getHeight());

        mActivityRule.runOnUiThread(() -> mTextView.setText("Two\nLines"));
        mInstrumentation.waitForIdleSync();
        assertEquals(expectedHeight, mTextView.getHeight());

        mActivityRule.runOnUiThread(() -> mTextView.setTextIsSelectable(true));
        mInstrumentation.waitForIdleSync();
        assertEquals(expectedHeight, mTextView.getHeight());
    }

    @Test
    public void testWidth() throws Throwable {
        mTextView = findTextView(R.id.textview_text);
        int originalWidth = mTextView.getWidth();

        int newWidth = mTextView.getWidth() / 8;
        setWidth(newWidth);
        assertEquals(newWidth, mTextView.getWidth());
        assertEquals(newWidth, mTextView.getMaxWidth());
        assertEquals(newWidth, mTextView.getMinWidth());

        // Min Width
        newWidth = originalWidth + 1;
        setMinWidth(newWidth);
        assertEquals(1, mTextView.getLineCount());
        assertEquals(newWidth, mTextView.getWidth());
        assertEquals(newWidth, mTextView.getMinWidth());

        newWidth = originalWidth - 1;
        setMinWidth(originalWidth - 1);
        assertEquals(2, mTextView.getLineCount());
        assertEquals(newWidth, mTextView.getWidth());
        assertEquals(newWidth, mTextView.getMinWidth());

        // Width
        newWidth = originalWidth + 1;
        setWidth(newWidth);
        assertEquals(1, mTextView.getLineCount());
        assertEquals(newWidth, mTextView.getWidth());
        assertEquals(newWidth, mTextView.getMaxWidth());
        assertEquals(newWidth, mTextView.getMinWidth());

        newWidth = originalWidth - 1;
        setWidth(newWidth);
        assertEquals(2, mTextView.getLineCount());
        assertEquals(newWidth, mTextView.getWidth());
        assertEquals(newWidth, mTextView.getMaxWidth());
        assertEquals(newWidth, mTextView.getMinWidth());

        // setting ems should cause getMaxWidth/getMinWidth to return -1
        setEms(1);
        assertEquals("Setting ems should return -1 for maxWidth", -1, mTextView.getMaxWidth());
        assertEquals("Setting ems should return -1 for maxWidth", -1, mTextView.getMinWidth());
    }

    @Test
    public void testSetMinEms() throws Throwable {
        mTextView = findTextView(R.id.textview_text);
        assertEquals(1, mTextView.getLineCount());

        final int originalWidth = mTextView.getWidth();
        final int originalEms = originalWidth / mTextView.getLineHeight();

        setMinEms(originalEms + 1);
        assertEquals((originalEms + 1) * mTextView.getLineHeight(), mTextView.getWidth());
        assertEquals(-1, mTextView.getMinWidth());
        assertEquals(originalEms + 1, mTextView.getMinEms());

        setMinEms(originalEms - 1);
        assertEquals(originalWidth, mTextView.getWidth());
        assertEquals(-1, mTextView.getMinWidth());
        assertEquals(originalEms - 1, mTextView.getMinEms());

        setMinWidth(1);
        assertEquals(-1, mTextView.getMinEms());
    }

    @Test
    public void testSetMaxEms() throws Throwable {
        mTextView = findTextView(R.id.textview_text);
        assertEquals(1, mTextView.getLineCount());

        final int originalWidth = mTextView.getWidth();
        final int originalEms = originalWidth / mTextView.getLineHeight();

        setMaxEms(originalEms + 1);
        assertEquals(1, mTextView.getLineCount());
        assertEquals(originalWidth, mTextView.getWidth());
        assertEquals(-1, mTextView.getMaxWidth());
        assertEquals(originalEms + 1, mTextView.getMaxEms());

        setMaxEms(originalEms - 1);
        assertTrue(1 < mTextView.getLineCount());
        assertEquals((originalEms - 1) * mTextView.getLineHeight(), mTextView.getWidth());
        assertEquals(-1, mTextView.getMaxWidth());
        assertEquals(originalEms - 1, mTextView.getMaxEms());

        setMaxWidth(originalWidth);
        assertEquals(-1, mTextView.getMaxEms());
    }

    @Test
    public void testSetEms() throws Throwable {
        mTextView = findTextView(R.id.textview_text);
        assertEquals("check height", 1, mTextView.getLineCount());
        final int originalWidth = mTextView.getWidth();
        final int originalEms = originalWidth / mTextView.getLineHeight();

        setEms(originalEms + 1);
        assertEquals(1, mTextView.getLineCount());
        assertEquals((originalEms + 1) * mTextView.getLineHeight(), mTextView.getWidth());
        assertEquals(-1, mTextView.getMinWidth());
        assertEquals(-1, mTextView.getMaxWidth());
        assertEquals(originalEms + 1, mTextView.getMinEms());
        assertEquals(originalEms + 1, mTextView.getMaxEms());

        setEms(originalEms - 1);
        assertTrue((1 < mTextView.getLineCount()));
        assertEquals((originalEms - 1) * mTextView.getLineHeight(), mTextView.getWidth());
        assertEquals(-1, mTextView.getMinWidth());
        assertEquals(-1, mTextView.getMaxWidth());
        assertEquals(originalEms - 1, mTextView.getMinEms());
        assertEquals(originalEms - 1, mTextView.getMaxEms());
    }

    @Test
    public void testSetLineSpacing() throws Throwable {
        mActivityRule.runOnUiThread(() -> mTextView = new TextView(mActivity));
        mInstrumentation.waitForIdleSync();
        int originalLineHeight = mTextView.getLineHeight();

        // normal
        float add = 1.2f;
        float mult = 1.4f;
        setLineSpacing(add, mult);
        assertEquals(Math.round(originalLineHeight * mult + add), mTextView.getLineHeight());
        add = 0.0f;
        mult = 1.4f;
        setLineSpacing(add, mult);
        assertEquals(Math.round(originalLineHeight * mult + add), mTextView.getLineHeight());

        // abnormal
        add = -1.2f;
        mult = 1.4f;
        setLineSpacing(add, mult);
        assertEquals(Math.round(originalLineHeight * mult + add), mTextView.getLineHeight());
        add = -1.2f;
        mult = -1.4f;
        setLineSpacing(add, mult);
        assertEquals(Math.round(originalLineHeight * mult + add), mTextView.getLineHeight());
        add = 1.2f;
        mult = 0.0f;
        setLineSpacing(add, mult);
        assertEquals(Math.round(originalLineHeight * mult + add), mTextView.getLineHeight());

        // edge
        add = Float.MIN_VALUE;
        mult = Float.MIN_VALUE;
        setLineSpacing(add, mult);
        assertEquals(Math.round(originalLineHeight * mult + add), mTextView.getLineHeight());

        // edge case where the behavior of Math.round() deviates from
        // FastMath.round(), requiring us to use an explicit 0 value
        add = Float.MAX_VALUE;
        mult = Float.MAX_VALUE;
        setLineSpacing(add, mult);
        assertEquals(0, mTextView.getLineHeight());
    }

    @Test
    public void testSetElegantLineHeight() throws Throwable {
        mTextView = findTextView(R.id.textview_text);
        assertFalse(mTextView.getPaint().isElegantTextHeight());
        mActivityRule.runOnUiThread(() -> {
            mTextView.setWidth(mTextView.getWidth() / 3);
            mTextView.setPadding(1, 2, 3, 4);
            mTextView.setGravity(Gravity.BOTTOM);
        });
        mInstrumentation.waitForIdleSync();

        int oldHeight = mTextView.getHeight();
        mActivityRule.runOnUiThread(() -> mTextView.setElegantTextHeight(true));
        mInstrumentation.waitForIdleSync();

        assertTrue(mTextView.getPaint().isElegantTextHeight());
        assertTrue(mTextView.getHeight() > oldHeight);

        mActivityRule.runOnUiThread(() -> mTextView.setElegantTextHeight(false));
        mInstrumentation.waitForIdleSync();
        assertFalse(mTextView.getPaint().isElegantTextHeight());
        assertTrue(mTextView.getHeight() == oldHeight);
    }

    @Test
    public void testAccessFreezesText() throws Throwable {
        layout(R.layout.textview_hint_linksclickable_freezestext);

        mTextView = findTextView(R.id.hint_linksClickable_freezesText_default);
        assertFalse(mTextView.getFreezesText());

        mTextView = findTextView(R.id.freezesText_true);
        assertTrue(mTextView.getFreezesText());

        mTextView = findTextView(R.id.freezesText_false);
        assertFalse(mTextView.getFreezesText());

        mTextView.setFreezesText(false);
        assertFalse(mTextView.getFreezesText());

        final CharSequence text = "Hello, TextView.";
        mActivityRule.runOnUiThread(() -> mTextView.setText(text));
        mInstrumentation.waitForIdleSync();

        final URLSpan urlSpan = new URLSpan("ctstest://TextView/test");
        // TODO: How to simulate the TextView in frozen icicles.
        ActivityMonitor am = mInstrumentation.addMonitor(MockURLSpanTestActivity.class.getName(),
                null, false);

        mActivityRule.runOnUiThread(() -> {
            Uri uri = Uri.parse(urlSpan.getURL());
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            mActivity.startActivity(intent);
        });

        Activity newActivity = am.waitForActivityWithTimeout(TIMEOUT);
        assertNotNull(newActivity);
        newActivity.finish();
        mInstrumentation.removeMonitor(am);
        // the text of TextView is removed.
        mTextView = findTextView(R.id.freezesText_false);

        assertEquals(text.toString(), mTextView.getText().toString());

        mTextView.setFreezesText(true);
        assertTrue(mTextView.getFreezesText());

        mActivityRule.runOnUiThread(() -> mTextView.setText(text));
        mInstrumentation.waitForIdleSync();
        // TODO: How to simulate the TextView in frozen icicles.
        am = mInstrumentation.addMonitor(MockURLSpanTestActivity.class.getName(),
                null, false);

        mActivityRule.runOnUiThread(() -> {
            Uri uri = Uri.parse(urlSpan.getURL());
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            mActivity.startActivity(intent);
        });

        Activity oldActivity = newActivity;
        while (true) {
            newActivity = am.waitForActivityWithTimeout(TIMEOUT);
            assertNotNull(newActivity);
            if (newActivity != oldActivity) {
                break;
            }
        }
        newActivity.finish();
        mInstrumentation.removeMonitor(am);
        // the text of TextView is still there.
        mTextView = findTextView(R.id.freezesText_false);
        assertEquals(text.toString(), mTextView.getText().toString());
    }

    @UiThreadTest
    @Test
    public void testSetEditableFactory() {
        mTextView = new TextView(mActivity);
        String text = "sample";

        final Editable.Factory mockEditableFactory = spy(new Editable.Factory());
        doCallRealMethod().when(mockEditableFactory).newEditable(any(CharSequence.class));
        mTextView.setEditableFactory(mockEditableFactory);

        mTextView.setText(text);
        verify(mockEditableFactory, never()).newEditable(any(CharSequence.class));

        reset(mockEditableFactory);
        mTextView.setText(text, BufferType.SPANNABLE);
        verify(mockEditableFactory, never()).newEditable(any(CharSequence.class));

        reset(mockEditableFactory);
        mTextView.setText(text, BufferType.NORMAL);
        verify(mockEditableFactory, never()).newEditable(any(CharSequence.class));

        reset(mockEditableFactory);
        mTextView.setText(text, BufferType.EDITABLE);
        verify(mockEditableFactory, times(1)).newEditable(text);

        mTextView.setKeyListener(DigitsKeyListener.getInstance());
        reset(mockEditableFactory);
        mTextView.setText(text, BufferType.EDITABLE);
        verify(mockEditableFactory, times(1)).newEditable(text);

        try {
            mTextView.setEditableFactory(null);
            fail("The factory can not set to null!");
        } catch (NullPointerException e) {
        }
    }

    @UiThreadTest
    @Test
    public void testSetSpannableFactory() {
        mTextView = new TextView(mActivity);
        String text = "sample";

        final Spannable.Factory mockSpannableFactory = spy(new Spannable.Factory());
        doCallRealMethod().when(mockSpannableFactory).newSpannable(any(CharSequence.class));
        mTextView.setSpannableFactory(mockSpannableFactory);

        mTextView.setText(text);
        verify(mockSpannableFactory, never()).newSpannable(any(CharSequence.class));

        reset(mockSpannableFactory);
        mTextView.setText(text, BufferType.EDITABLE);
        verify(mockSpannableFactory, never()).newSpannable(any(CharSequence.class));

        reset(mockSpannableFactory);
        mTextView.setText(text, BufferType.NORMAL);
        verify(mockSpannableFactory, never()).newSpannable(any(CharSequence.class));

        reset(mockSpannableFactory);
        mTextView.setText(text, BufferType.SPANNABLE);
        verify(mockSpannableFactory, times(1)).newSpannable(text);

        mTextView.setMovementMethod(LinkMovementMethod.getInstance());
        reset(mockSpannableFactory);
        mTextView.setText(text, BufferType.NORMAL);
        verify(mockSpannableFactory, times(1)).newSpannable(text);

        try {
            mTextView.setSpannableFactory(null);
            fail("The factory can not set to null!");
        } catch (NullPointerException e) {
        }
    }

    @UiThreadTest
    @Test
    public void testTextChangedListener() {
        mTextView = new TextView(mActivity);
        MockTextWatcher watcher0 = new MockTextWatcher();
        MockTextWatcher watcher1 = new MockTextWatcher();

        mTextView.addTextChangedListener(watcher0);
        mTextView.addTextChangedListener(watcher1);

        watcher0.reset();
        watcher1.reset();
        mTextView.setText("Changed");
        assertTrue(watcher0.hasCalledBeforeTextChanged());
        assertTrue(watcher0.hasCalledOnTextChanged());
        assertTrue(watcher0.hasCalledAfterTextChanged());
        assertTrue(watcher1.hasCalledBeforeTextChanged());
        assertTrue(watcher1.hasCalledOnTextChanged());
        assertTrue(watcher1.hasCalledAfterTextChanged());

        watcher0.reset();
        watcher1.reset();
        // BeforeTextChanged and OnTextChanged are called though the strings are same
        mTextView.setText("Changed");
        assertTrue(watcher0.hasCalledBeforeTextChanged());
        assertTrue(watcher0.hasCalledOnTextChanged());
        assertTrue(watcher0.hasCalledAfterTextChanged());
        assertTrue(watcher1.hasCalledBeforeTextChanged());
        assertTrue(watcher1.hasCalledOnTextChanged());
        assertTrue(watcher1.hasCalledAfterTextChanged());

        watcher0.reset();
        watcher1.reset();
        // BeforeTextChanged and OnTextChanged are called twice (The text is not
        // Editable, so in Append() it calls setText() first)
        mTextView.append("and appended");
        assertTrue(watcher0.hasCalledBeforeTextChanged());
        assertTrue(watcher0.hasCalledOnTextChanged());
        assertTrue(watcher0.hasCalledAfterTextChanged());
        assertTrue(watcher1.hasCalledBeforeTextChanged());
        assertTrue(watcher1.hasCalledOnTextChanged());
        assertTrue(watcher1.hasCalledAfterTextChanged());

        watcher0.reset();
        watcher1.reset();
        // Methods are not called if the string does not change
        mTextView.append("");
        assertFalse(watcher0.hasCalledBeforeTextChanged());
        assertFalse(watcher0.hasCalledOnTextChanged());
        assertFalse(watcher0.hasCalledAfterTextChanged());
        assertFalse(watcher1.hasCalledBeforeTextChanged());
        assertFalse(watcher1.hasCalledOnTextChanged());
        assertFalse(watcher1.hasCalledAfterTextChanged());

        watcher0.reset();
        watcher1.reset();
        mTextView.removeTextChangedListener(watcher1);
        mTextView.setText(null);
        assertTrue(watcher0.hasCalledBeforeTextChanged());
        assertTrue(watcher0.hasCalledOnTextChanged());
        assertTrue(watcher0.hasCalledAfterTextChanged());
        assertFalse(watcher1.hasCalledBeforeTextChanged());
        assertFalse(watcher1.hasCalledOnTextChanged());
        assertFalse(watcher1.hasCalledAfterTextChanged());
    }

    @UiThreadTest
    @Test
    public void testSetTextKeepState1() {
        mTextView = new TextView(mActivity);

        String longString = "very long content";
        String shortString = "short";

        // selection is at the exact place which is inside the short string
        mTextView.setText(longString, BufferType.SPANNABLE);
        Selection.setSelection((Spannable) mTextView.getText(), 3);
        mTextView.setTextKeepState(shortString);
        assertEquals(shortString, mTextView.getText().toString());
        assertEquals(3, mTextView.getSelectionStart());
        assertEquals(3, mTextView.getSelectionEnd());

        // selection is at the exact place which is outside the short string
        mTextView.setText(longString);
        Selection.setSelection((Spannable) mTextView.getText(), shortString.length() + 1);
        mTextView.setTextKeepState(shortString);
        assertEquals(shortString, mTextView.getText().toString());
        assertEquals(shortString.length(), mTextView.getSelectionStart());
        assertEquals(shortString.length(), mTextView.getSelectionEnd());

        // select the sub string which is inside the short string
        mTextView.setText(longString);
        Selection.setSelection((Spannable) mTextView.getText(), 1, 4);
        mTextView.setTextKeepState(shortString);
        assertEquals(shortString, mTextView.getText().toString());
        assertEquals(1, mTextView.getSelectionStart());
        assertEquals(4, mTextView.getSelectionEnd());

        // select the sub string which ends outside the short string
        mTextView.setText(longString);
        Selection.setSelection((Spannable) mTextView.getText(), 2, shortString.length() + 1);
        mTextView.setTextKeepState(shortString);
        assertEquals(shortString, mTextView.getText().toString());
        assertEquals(2, mTextView.getSelectionStart());
        assertEquals(shortString.length(), mTextView.getSelectionEnd());

        // select the sub string which is outside the short string
        mTextView.setText(longString);
        Selection.setSelection((Spannable) mTextView.getText(),
                shortString.length() + 1, shortString.length() + 3);
        mTextView.setTextKeepState(shortString);
        assertEquals(shortString, mTextView.getText().toString());
        assertEquals(shortString.length(), mTextView.getSelectionStart());
        assertEquals(shortString.length(), mTextView.getSelectionEnd());
    }

    @UiThreadTest
    @Test
    public void testGetEditableText() {
        TextView tv = findTextView(R.id.textview_text);

        String text = "Hello";
        tv.setText(text, BufferType.EDITABLE);
        assertEquals(text, tv.getText().toString());
        assertTrue(tv.getText() instanceof Editable);
        assertEquals(text, tv.getEditableText().toString());

        tv.setText(text, BufferType.SPANNABLE);
        assertEquals(text, tv.getText().toString());
        assertTrue(tv.getText() instanceof Spannable);
        assertNull(tv.getEditableText());

        tv.setText(null, BufferType.EDITABLE);
        assertEquals("", tv.getText().toString());
        assertTrue(tv.getText() instanceof Editable);
        assertEquals("", tv.getEditableText().toString());

        tv.setText(null, BufferType.SPANNABLE);
        assertEquals("", tv.getText().toString());
        assertTrue(tv.getText() instanceof Spannable);
        assertNull(tv.getEditableText());
    }

    @UiThreadTest
    @Test
    public void testSetText2() {
        String string = "This is a test for setting text content by char array";
        char[] input = string.toCharArray();
        TextView tv = findTextView(R.id.textview_text);

        tv.setText(input, 0, input.length);
        assertEquals(string, tv.getText().toString());

        tv.setText(input, 0, 5);
        assertEquals(string.substring(0, 5), tv.getText().toString());

        try {
            tv.setText(input, -1, input.length);
            fail("Should throw exception if the start position is negative!");
        } catch (IndexOutOfBoundsException exception) {
        }

        try {
            tv.setText(input, 0, -1);
            fail("Should throw exception if the length is negative!");
        } catch (IndexOutOfBoundsException exception) {
        }

        try {
            tv.setText(input, 1, input.length);
            fail("Should throw exception if the end position is out of index!");
        } catch (IndexOutOfBoundsException exception) {
        }

        tv.setText(input, 1, 0);
        assertEquals("", tv.getText().toString());
    }

    @UiThreadTest
    @Test
    public void testSetText1() {
        mTextView = findTextView(R.id.textview_text);

        String longString = "very long content";
        String shortString = "short";

        // selection is at the exact place which is inside the short string
        mTextView.setText(longString, BufferType.SPANNABLE);
        Selection.setSelection((Spannable) mTextView.getText(), 3);
        mTextView.setTextKeepState(shortString, BufferType.EDITABLE);
        assertTrue(mTextView.getText() instanceof Editable);
        assertEquals(shortString, mTextView.getText().toString());
        assertEquals(shortString, mTextView.getEditableText().toString());
        assertEquals(3, mTextView.getSelectionStart());
        assertEquals(3, mTextView.getSelectionEnd());

        mTextView.setText(shortString, BufferType.EDITABLE);
        assertTrue(mTextView.getText() instanceof Editable);
        assertEquals(shortString, mTextView.getText().toString());
        assertEquals(shortString, mTextView.getEditableText().toString());
        // there is no selection.
        assertEquals(-1, mTextView.getSelectionStart());
        assertEquals(-1, mTextView.getSelectionEnd());

        // selection is at the exact place which is outside the short string
        mTextView.setText(longString);
        Selection.setSelection((Spannable) mTextView.getText(), longString.length());
        mTextView.setTextKeepState(shortString, BufferType.EDITABLE);
        assertTrue(mTextView.getText() instanceof Editable);
        assertEquals(shortString, mTextView.getText().toString());
        assertEquals(shortString, mTextView.getEditableText().toString());
        assertEquals(shortString.length(), mTextView.getSelectionStart());
        assertEquals(shortString.length(), mTextView.getSelectionEnd());

        mTextView.setText(shortString, BufferType.EDITABLE);
        assertTrue(mTextView.getText() instanceof Editable);
        assertEquals(shortString, mTextView.getText().toString());
        assertEquals(shortString, mTextView.getEditableText().toString());
        // there is no selection.
        assertEquals(-1, mTextView.getSelectionStart());
        assertEquals(-1, mTextView.getSelectionEnd());

        // select the sub string which is inside the short string
        mTextView.setText(longString);
        Selection.setSelection((Spannable) mTextView.getText(), 1, shortString.length() - 1);
        mTextView.setTextKeepState(shortString, BufferType.EDITABLE);
        assertTrue(mTextView.getText() instanceof Editable);
        assertEquals(shortString, mTextView.getText().toString());
        assertEquals(shortString, mTextView.getEditableText().toString());
        assertEquals(1, mTextView.getSelectionStart());
        assertEquals(shortString.length() - 1, mTextView.getSelectionEnd());

        mTextView.setText(shortString, BufferType.EDITABLE);
        assertTrue(mTextView.getText() instanceof Editable);
        assertEquals(shortString, mTextView.getText().toString());
        assertEquals(shortString, mTextView.getEditableText().toString());
        // there is no selection.
        assertEquals(-1, mTextView.getSelectionStart());
        assertEquals(-1, mTextView.getSelectionEnd());

        // select the sub string which ends outside the short string
        mTextView.setText(longString);
        Selection.setSelection((Spannable) mTextView.getText(), 2, longString.length());
        mTextView.setTextKeepState(shortString, BufferType.EDITABLE);
        assertTrue(mTextView.getText() instanceof Editable);
        assertEquals(shortString, mTextView.getText().toString());
        assertEquals(shortString, mTextView.getEditableText().toString());
        assertEquals(2, mTextView.getSelectionStart());
        assertEquals(shortString.length(), mTextView.getSelectionEnd());

        mTextView.setText(shortString, BufferType.EDITABLE);
        assertTrue(mTextView.getText() instanceof Editable);
        assertEquals(shortString, mTextView.getText().toString());
        assertEquals(shortString, mTextView.getEditableText().toString());
        // there is no selection.
        assertEquals(-1, mTextView.getSelectionStart());
        assertEquals(-1, mTextView.getSelectionEnd());

        // select the sub string which is outside the short string
        mTextView.setText(longString);
        Selection.setSelection((Spannable) mTextView.getText(),
                shortString.length() + 1, shortString.length() + 3);
        mTextView.setTextKeepState(shortString, BufferType.EDITABLE);
        assertTrue(mTextView.getText() instanceof Editable);
        assertEquals(shortString, mTextView.getText().toString());
        assertEquals(shortString, mTextView.getEditableText().toString());
        assertEquals(shortString.length(), mTextView.getSelectionStart());
        assertEquals(shortString.length(), mTextView.getSelectionEnd());

        mTextView.setText(shortString, BufferType.EDITABLE);
        assertTrue(mTextView.getText() instanceof Editable);
        assertEquals(shortString, mTextView.getText().toString());
        assertEquals(shortString, mTextView.getEditableText().toString());
        // there is no selection.
        assertEquals(-1, mTextView.getSelectionStart());
        assertEquals(-1, mTextView.getSelectionEnd());
    }

    @UiThreadTest
    @Test
    public void testSetText3() {
        TextView tv = findTextView(R.id.textview_text);

        int resId = R.string.text_view_hint;
        String result = mActivity.getResources().getString(resId);

        tv.setText(resId);
        assertEquals(result, tv.getText().toString());

        try {
            tv.setText(-1);
            fail("Should throw exception with illegal id");
        } catch (NotFoundException e) {
        }
    }

    @Test
    public void testSetTextUpdatesHeightAfterRemovingImageSpan() throws Throwable {
        // Height calculation had problems when TextView had width: match_parent
        final int textViewWidth = ViewGroup.LayoutParams.MATCH_PARENT;
        final Spannable text = new SpannableString("some text");
        final int spanHeight = 100;

        // prepare TextView, width: MATCH_PARENT
        mActivityRule.runOnUiThread(() -> mTextView = new TextView(mActivity));
        mInstrumentation.waitForIdleSync();
        mTextView.setSingleLine(true);
        mTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 2);
        mTextView.setPadding(0, 0, 0, 0);
        mTextView.setIncludeFontPadding(false);
        mTextView.setText(text);
        final FrameLayout layout = new FrameLayout(mActivity);
        final ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(textViewWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        layout.addView(mTextView, layoutParams);
        layout.setLayoutParams(layoutParams);
        mActivityRule.runOnUiThread(() -> mActivity.setContentView(layout));
        mInstrumentation.waitForIdleSync();

        // measure height of text with no span
        final int heightWithoutSpan = mTextView.getHeight();
        assertTrue("Text height should be smaller than span height",
                heightWithoutSpan < spanHeight);

        // add ImageSpan to text
        Drawable drawable = mInstrumentation.getContext().getDrawable(R.drawable.scenery);
        drawable.setBounds(0, 0, spanHeight, spanHeight);
        ImageSpan span = new ImageSpan(drawable);
        text.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        mActivityRule.runOnUiThread(() -> mTextView.setText(text));
        mInstrumentation.waitForIdleSync();

        // measure height with span
        final int heightWithSpan = mTextView.getHeight();
        assertTrue("Text height should be greater or equal than span height",
                heightWithSpan >= spanHeight);

        // remove the span
        text.removeSpan(span);
        mActivityRule.runOnUiThread(() -> mTextView.setText(text));
        mInstrumentation.waitForIdleSync();

        final int heightAfterRemoveSpan = mTextView.getHeight();
        assertEquals("Text height should be same after removing the span",
                heightWithoutSpan, heightAfterRemoveSpan);
    }

    @Test
    public void testRemoveSelectionWithSelectionHandles() throws Throwable {
        initTextViewForTypingOnUiThread();

        assertFalse(mTextView.isTextSelectable());
        mActivityRule.runOnUiThread(() -> {
            mTextView.setTextIsSelectable(true);
            mTextView.setText("abcd", BufferType.EDITABLE);
        });
        mInstrumentation.waitForIdleSync();
        assertTrue(mTextView.isTextSelectable());

        // Long click on the text selects all text and shows selection handlers. The view has an
        // attribute layout_width="wrap_content", so clicked location (the center of the view)
        // should be on the text.
        CtsTouchUtils.emulateLongPressOnViewCenter(mInstrumentation, mTextView);

        mActivityRule.runOnUiThread(() -> Selection.removeSelection((Spannable) mTextView.getText()));
        mInstrumentation.waitForIdleSync();

        assertTrue(TextUtils.equals("abcd", mTextView.getText()));
    }

    @Test
    public void testUndo_insert() throws Throwable {
        initTextViewForTypingOnUiThread();

        // Type some text.
        CtsKeyEventUtil.sendString(mInstrumentation, mTextView, "abc");
        mActivityRule.runOnUiThread(() -> {
            // Precondition: The cursor is at the end of the text.
            assertEquals(3, mTextView.getSelectionStart());

            // Undo removes the typed string in one step.
            mTextView.onTextContextMenuItem(android.R.id.undo);
            assertEquals("", mTextView.getText().toString());
            assertEquals(0, mTextView.getSelectionStart());

            // Redo restores the text and cursor position.
            mTextView.onTextContextMenuItem(android.R.id.redo);
            assertEquals("abc", mTextView.getText().toString());
            assertEquals(3, mTextView.getSelectionStart());

            // Undoing the redo clears the text again.
            mTextView.onTextContextMenuItem(android.R.id.undo);
            assertEquals("", mTextView.getText().toString());

            // Undo when the undo stack is empty does nothing.
            mTextView.onTextContextMenuItem(android.R.id.undo);
            assertEquals("", mTextView.getText().toString());
        });
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testUndo_delete() throws Throwable {
        initTextViewForTypingOnUiThread();

        // Simulate deleting text and undoing it.
        CtsKeyEventUtil.sendString(mInstrumentation, mTextView, "xyz");
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_DEL,
                KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_DEL);
        mActivityRule.runOnUiThread(() -> {
            // Precondition: The text was actually deleted.
            assertEquals("", mTextView.getText().toString());
            assertEquals(0, mTextView.getSelectionStart());

            // Undo restores the typed string and cursor position in one step.
            mTextView.onTextContextMenuItem(android.R.id.undo);
            assertEquals("xyz", mTextView.getText().toString());
            assertEquals(3, mTextView.getSelectionStart());

            // Redo removes the text in one step.
            mTextView.onTextContextMenuItem(android.R.id.redo);
            assertEquals("", mTextView.getText().toString());
            assertEquals(0, mTextView.getSelectionStart());

            // Undoing the redo restores the text again.
            mTextView.onTextContextMenuItem(android.R.id.undo);
            assertEquals("xyz", mTextView.getText().toString());
            assertEquals(3, mTextView.getSelectionStart());

            // Undoing again undoes the original typing.
            mTextView.onTextContextMenuItem(android.R.id.undo);
            assertEquals("", mTextView.getText().toString());
            assertEquals(0, mTextView.getSelectionStart());
        });
        mInstrumentation.waitForIdleSync();
    }

    // Initialize the text view for simulated IME typing. Must be called on UI thread.
    private InputConnection initTextViewForSimulatedIme() {
        mTextView = findTextView(R.id.textview_text);
        return initTextViewForSimulatedIme(mTextView);
    }

    private InputConnection initTextViewForSimulatedIme(TextView textView) {
        textView.setKeyListener(QwertyKeyListener.getInstance(false, Capitalize.NONE));
        textView.setText("", BufferType.EDITABLE);
        return textView.onCreateInputConnection(new EditorInfo());
    }

    // Simulates IME composing text behavior.
    private void setComposingTextInBatch(InputConnection input, CharSequence text) {
        input.beginBatchEdit();
        input.setComposingText(text, 1);  // Leave cursor at end.
        input.endBatchEdit();
    }

    @UiThreadTest
    @Test
    public void testUndo_imeInsertLatin() {
        InputConnection input = initTextViewForSimulatedIme();

        // Simulate IME text entry behavior. The Latin IME enters text by replacing partial words,
        // such as "c" -> "ca" -> "cat" -> "cat ".
        setComposingTextInBatch(input, "c");
        setComposingTextInBatch(input, "ca");

        // The completion and space are added in the same batch.
        input.beginBatchEdit();
        input.commitText("cat", 1);
        input.commitText(" ", 1);
        input.endBatchEdit();

        // The repeated replacements undo in a single step.
        mTextView.onTextContextMenuItem(android.R.id.undo);
        assertEquals("", mTextView.getText().toString());
    }

    @UiThreadTest
    @Test
    public void testUndo_imeInsertJapanese() {
        InputConnection input = initTextViewForSimulatedIme();

        // The Japanese IME does repeated replacements of Latin characters to hiragana to kanji.
        final String HA = "\u306F";  // HIRAGANA LETTER HA
        final String NA = "\u306A";  // HIRAGANA LETTER NA
        setComposingTextInBatch(input, "h");
        setComposingTextInBatch(input, HA);
        setComposingTextInBatch(input, HA + "n");
        setComposingTextInBatch(input, HA + NA);

        // The result may be a surrogate pair. The composition ends in the same batch.
        input.beginBatchEdit();
        input.commitText("\uD83C\uDF37", 1);  // U+1F337 TULIP
        input.setComposingText("", 1);
        input.endBatchEdit();

        // The repeated replacements are a single undo step.
        mTextView.onTextContextMenuItem(android.R.id.undo);
        assertEquals("", mTextView.getText().toString());
    }

    @UiThreadTest
    @Test
    public void testUndo_imeInsertAndDeleteLatin() {
        InputConnection input = initTextViewForSimulatedIme();

        setComposingTextInBatch(input, "t");
        setComposingTextInBatch(input, "te");
        setComposingTextInBatch(input, "tes");
        setComposingTextInBatch(input, "test");
        setComposingTextInBatch(input, "tes");
        setComposingTextInBatch(input, "te");
        setComposingTextInBatch(input, "t");

        input.beginBatchEdit();
        input.setComposingText("", 1);
        input.finishComposingText();
        input.endBatchEdit();

        mTextView.onTextContextMenuItem(android.R.id.undo);
        assertEquals("test", mTextView.getText().toString());
        mTextView.onTextContextMenuItem(android.R.id.undo);
        assertEquals("", mTextView.getText().toString());
    }

    @UiThreadTest
    @Test
    public void testUndo_imeAutoCorrection() {
        mTextView = findTextView(R.id.textview_text);
        TextView spiedTextView = spy(mTextView);
        InputConnection input = initTextViewForSimulatedIme(spiedTextView);

        // Start typing a composition.
        setComposingTextInBatch(input, "t");
        setComposingTextInBatch(input, "te");
        setComposingTextInBatch(input, "teh");

        CorrectionInfo correctionInfo = new CorrectionInfo(0, "teh", "the");
        reset(spiedTextView);
        input.beginBatchEdit();
        // Auto correct "teh" to "the".
        assertTrue(input.commitCorrection(correctionInfo));
        input.commitText("the", 1);
        input.endBatchEdit();

        verify(spiedTextView, times(1)).onCommitCorrection(refEq(correctionInfo));

        assertEquals("the", spiedTextView.getText().toString());
        spiedTextView.onTextContextMenuItem(android.R.id.undo);
        assertEquals("teh", spiedTextView.getText().toString());
        spiedTextView.onTextContextMenuItem(android.R.id.undo);
        assertEquals("", spiedTextView.getText().toString());
    }

    @UiThreadTest
    @Test
    public void testUndo_imeAutoCompletion() {
        mTextView = findTextView(R.id.textview_text);
        TextView spiedTextView = spy(mTextView);
        InputConnection input = initTextViewForSimulatedIme(spiedTextView);

        // Start typing a composition.
        setComposingTextInBatch(input, "a");
        setComposingTextInBatch(input, "an");
        setComposingTextInBatch(input, "and");

        CompletionInfo completionInfo = new CompletionInfo(0, 0, "android");
        reset(spiedTextView);
        input.beginBatchEdit();
        // Auto complete "and" to "android".
        assertTrue(input.commitCompletion(completionInfo));
        input.commitText("android", 1);
        input.endBatchEdit();

        verify(spiedTextView, times(1)).onCommitCompletion(refEq(completionInfo));

        assertEquals("android", spiedTextView.getText().toString());
        spiedTextView.onTextContextMenuItem(android.R.id.undo);
        assertEquals("", spiedTextView.getText().toString());
    }

    @UiThreadTest
    @Test
    public void testUndo_imeCancel() {
        InputConnection input = initTextViewForSimulatedIme();
        mTextView.setText("flower");

        // Start typing a composition.
        final String HA = "\u306F";  // HIRAGANA LETTER HA
        setComposingTextInBatch(input, "h");
        setComposingTextInBatch(input, HA);
        setComposingTextInBatch(input, HA + "n");

        // Cancel the composition.
        setComposingTextInBatch(input, "");

        mTextView.onTextContextMenuItem(android.R.id.undo);
        assertEquals(HA + "n" + "flower", mTextView.getText().toString());
        mTextView.onTextContextMenuItem(android.R.id.redo);
        assertEquals("flower", mTextView.getText().toString());
    }

    @UiThreadTest
    @Test
    public void testUndo_imeEmptyBatch() {
        InputConnection input = initTextViewForSimulatedIme();
        mTextView.setText("flower");

        // Send an empty batch edit. This happens if the IME is hidden and shown.
        input.beginBatchEdit();
        input.endBatchEdit();

        // Undo and redo do nothing.
        mTextView.onTextContextMenuItem(android.R.id.undo);
        assertEquals("flower", mTextView.getText().toString());
        mTextView.onTextContextMenuItem(android.R.id.redo);
        assertEquals("flower", mTextView.getText().toString());
    }

    @Test
    public void testUndo_setText() throws Throwable {
        initTextViewForTypingOnUiThread();

        // Create two undo operations, an insert and a delete.
        CtsKeyEventUtil.sendString(mInstrumentation, mTextView, "xyz");
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_DEL,
                KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_DEL);
        mActivityRule.runOnUiThread(() -> {
            // Calling setText() clears both undo operations, so undo doesn't happen.
            mTextView.setText("Hello", BufferType.EDITABLE);
            mTextView.onTextContextMenuItem(android.R.id.undo);
            assertEquals("Hello", mTextView.getText().toString());

            // Clearing text programmatically does not undo either.
            mTextView.setText("", BufferType.EDITABLE);
            mTextView.onTextContextMenuItem(android.R.id.undo);
            assertEquals("", mTextView.getText().toString());
        });
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testRedo_setText() throws Throwable {
        initTextViewForTypingOnUiThread();

        // Type some text. This creates an undo entry.
        CtsKeyEventUtil.sendString(mInstrumentation, mTextView, "abc");
        mActivityRule.runOnUiThread(() -> {
            // Undo the typing to create a redo entry.
            mTextView.onTextContextMenuItem(android.R.id.undo);

            // Calling setText() clears the redo stack, so redo doesn't happen.
            mTextView.setText("Hello", BufferType.EDITABLE);
            mTextView.onTextContextMenuItem(android.R.id.redo);
            assertEquals("Hello", mTextView.getText().toString());
        });
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testUndo_directAppend() throws Throwable {
        initTextViewForTypingOnUiThread();

        // Type some text.
        CtsKeyEventUtil.sendString(mInstrumentation, mTextView, "abc");
        mActivityRule.runOnUiThread(() -> {
            // Programmatically append some text.
            mTextView.append("def");
            assertEquals("abcdef", mTextView.getText().toString());

            // Undo removes the append as a separate step.
            mTextView.onTextContextMenuItem(android.R.id.undo);
            assertEquals("abc", mTextView.getText().toString());

            // Another undo removes the original typing.
            mTextView.onTextContextMenuItem(android.R.id.undo);
            assertEquals("", mTextView.getText().toString());
        });
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testUndo_directInsert() throws Throwable {
        initTextViewForTypingOnUiThread();

        // Type some text.
        CtsKeyEventUtil.sendString(mInstrumentation, mTextView, "abc");
        mActivityRule.runOnUiThread(() -> {
            // Directly modify the underlying Editable to insert some text.
            // NOTE: This is a violation of the API of getText() which specifies that the
            // returned object should not be modified. However, some apps do this anyway and
            // the framework needs to handle it.
            Editable text = (Editable) mTextView.getText();
            text.insert(0, "def");
            assertEquals("defabc", mTextView.getText().toString());

            // Undo removes the insert as a separate step.
            mTextView.onTextContextMenuItem(android.R.id.undo);
            assertEquals("abc", mTextView.getText().toString());

            // Another undo removes the original typing.
            mTextView.onTextContextMenuItem(android.R.id.undo);
            assertEquals("", mTextView.getText().toString());
        });
        mInstrumentation.waitForIdleSync();
    }

    @UiThreadTest
    @Test
    public void testUndo_noCursor() {
        initTextViewForTyping();

        // Append some text to create an undo operation. There is no cursor present.
        mTextView.append("cat");

        // Place the cursor at the end of the text so the undo will have to change it.
        Selection.setSelection((Spannable) mTextView.getText(), 3);

        // Undo the append. This should not crash, despite not having a valid cursor
        // position in the undo operation.
        mTextView.onTextContextMenuItem(android.R.id.undo);
    }

    @Test
    public void testUndo_textWatcher() throws Throwable {
        initTextViewForTypingOnUiThread();

        // Add a TextWatcher that converts the text to spaces on each change.
        mTextView.addTextChangedListener(new ConvertToSpacesTextWatcher());

        // Type some text.
        CtsKeyEventUtil.sendString(mInstrumentation, mTextView, "abc");
        mActivityRule.runOnUiThread(() -> {
            // TextWatcher altered the text.
            assertEquals("   ", mTextView.getText().toString());

            // Undo reverses both changes in one step.
            mTextView.onTextContextMenuItem(android.R.id.undo);
            assertEquals("", mTextView.getText().toString());
        });
        mInstrumentation.waitForIdleSync();
    }

    @UiThreadTest
    @Test
    public void testUndo_textWatcherDirectAppend() {
        initTextViewForTyping();

        // Add a TextWatcher that converts the text to spaces on each change.
        mTextView.addTextChangedListener(new ConvertToSpacesTextWatcher());

        // Programmatically append some text. The TextWatcher changes it to spaces.
        mTextView.append("abc");
        assertEquals("   ", mTextView.getText().toString());

        // Undo reverses both changes in one step.
        mTextView.onTextContextMenuItem(android.R.id.undo);
        assertEquals("", mTextView.getText().toString());
    }

    @Test
    public void testUndo_shortcuts() throws Throwable {
        initTextViewForTypingOnUiThread();

        // Type some text.
        CtsKeyEventUtil.sendString(mInstrumentation, mTextView, "abc");
        mActivityRule.runOnUiThread(() -> {
            // Pressing Control-Z triggers undo.
            KeyEvent control = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z, 0,
                    KeyEvent.META_CTRL_LEFT_ON);
            assertTrue(mTextView.onKeyShortcut(KeyEvent.KEYCODE_Z, control));
            assertEquals("", mTextView.getText().toString());

            // Pressing Control-Shift-Z triggers redo.
            KeyEvent controlShift = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z,
                    0, KeyEvent.META_CTRL_LEFT_ON | KeyEvent.META_SHIFT_LEFT_ON);
            assertTrue(mTextView.onKeyShortcut(KeyEvent.KEYCODE_Z, controlShift));
            assertEquals("abc", mTextView.getText().toString());
        });
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testUndo_saveInstanceState() throws Throwable {
        initTextViewForTypingOnUiThread();

        // Type some text to create an undo operation.
        CtsKeyEventUtil.sendString(mInstrumentation, mTextView, "abc");
        mActivityRule.runOnUiThread(() -> {
            // Parcel and unparcel the TextView.
            Parcelable state = mTextView.onSaveInstanceState();
            mTextView.onRestoreInstanceState(state);
        });
        mInstrumentation.waitForIdleSync();

        // Delete a character to create a new undo operation.
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_DEL);
        mActivityRule.runOnUiThread(() -> {
            assertEquals("ab", mTextView.getText().toString());

            // Undo the delete.
            mTextView.onTextContextMenuItem(android.R.id.undo);
            assertEquals("abc", mTextView.getText().toString());

            // Undo the typing, which verifies that the original undo operation was parceled
            // correctly.
            mTextView.onTextContextMenuItem(android.R.id.undo);
            assertEquals("", mTextView.getText().toString());

            // Parcel and unparcel the undo stack (which is empty but has been used and may
            // contain other state).
            Parcelable state = mTextView.onSaveInstanceState();
            mTextView.onRestoreInstanceState(state);
        });
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testUndo_saveInstanceStateEmpty() throws Throwable {
        initTextViewForTypingOnUiThread();

        // Type and delete to create two new undo operations.
        CtsKeyEventUtil.sendString(mInstrumentation, mTextView, "a");
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_DEL);
        mActivityRule.runOnUiThread(() -> {
            // Empty the undo stack then parcel and unparcel the TextView. While the undo
            // stack contains no operations it may contain other state.
            mTextView.onTextContextMenuItem(android.R.id.undo);
            mTextView.onTextContextMenuItem(android.R.id.undo);
            Parcelable state = mTextView.onSaveInstanceState();
            mTextView.onRestoreInstanceState(state);
        });
        mInstrumentation.waitForIdleSync();

        // Create two more undo operations.
        CtsKeyEventUtil.sendString(mInstrumentation, mTextView, "b");
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_DEL);
        mActivityRule.runOnUiThread(() -> {
            // Verify undo still works.
            mTextView.onTextContextMenuItem(android.R.id.undo);
            assertEquals("b", mTextView.getText().toString());
            mTextView.onTextContextMenuItem(android.R.id.undo);
            assertEquals("", mTextView.getText().toString());
        });
        mInstrumentation.waitForIdleSync();
    }

    @UiThreadTest
    @Test
    public void testCopyAndPaste() {
        initTextViewForTyping();

        mTextView.setText("abcd", BufferType.EDITABLE);
        mTextView.setSelected(true);

        // Copy "bc".
        Selection.setSelection((Spannable) mTextView.getText(), 1, 3);
        mTextView.onTextContextMenuItem(android.R.id.copy);

        // Paste "bc" between "b" and "c".
        Selection.setSelection((Spannable) mTextView.getText(), 2, 2);
        mTextView.onTextContextMenuItem(android.R.id.paste);
        assertEquals("abbccd", mTextView.getText().toString());

        // Select entire text and paste "bc".
        Selection.selectAll((Spannable) mTextView.getText());
        mTextView.onTextContextMenuItem(android.R.id.paste);
        assertEquals("bc", mTextView.getText().toString());
    }

    @Test
    public void testCopyAndPaste_byKey() throws Throwable {
        initTextViewForTypingOnUiThread();

        // Type "abc".
        CtsKeyEventUtil.sendString(mInstrumentation, mTextView, "abc");
        mActivityRule.runOnUiThread(() -> {
            // Select "bc"
            Selection.setSelection((Spannable) mTextView.getText(), 1, 3);
        });
        mInstrumentation.waitForIdleSync();
        // Copy "bc"
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_COPY);

        mActivityRule.runOnUiThread(() -> {
            // Set cursor between 'b' and 'c'.
            Selection.setSelection((Spannable) mTextView.getText(), 2, 2);
        });
        mInstrumentation.waitForIdleSync();
        // Paste "bc"
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_PASTE);
        assertEquals("abbcc", mTextView.getText().toString());

        mActivityRule.runOnUiThread(() -> {
            Selection.selectAll((Spannable) mTextView.getText());
            KeyEvent copyWithMeta = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_COPY, 0, KeyEvent.META_SHIFT_LEFT_ON);
            // Shift + copy doesn't perform copy.
            mTextView.onKeyDown(KeyEvent.KEYCODE_COPY, copyWithMeta);
            Selection.setSelection((Spannable) mTextView.getText(), 0, 0);
            mTextView.onTextContextMenuItem(android.R.id.paste);
            assertEquals("bcabbcc", mTextView.getText().toString());

            Selection.selectAll((Spannable) mTextView.getText());
            copyWithMeta = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_COPY, 0,
                    KeyEvent.META_CTRL_LEFT_ON);
            // Control + copy doesn't perform copy.
            mTextView.onKeyDown(KeyEvent.KEYCODE_COPY, copyWithMeta);
            Selection.setSelection((Spannable) mTextView.getText(), 0, 0);
            mTextView.onTextContextMenuItem(android.R.id.paste);
            assertEquals("bcbcabbcc", mTextView.getText().toString());

            Selection.selectAll((Spannable) mTextView.getText());
            copyWithMeta = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_COPY, 0,
                    KeyEvent.META_SHIFT_LEFT_ON | KeyEvent.META_CTRL_LEFT_ON);
            // Control + Shift + copy doesn't perform copy.
            mTextView.onKeyDown(KeyEvent.KEYCODE_COPY, copyWithMeta);
            Selection.setSelection((Spannable) mTextView.getText(), 0, 0);
            mTextView.onTextContextMenuItem(android.R.id.paste);
            assertEquals("bcbcbcabbcc", mTextView.getText().toString());
        });
        mInstrumentation.waitForIdleSync();
    }

    @UiThreadTest
    @Test
    public void testCutAndPaste() {
        initTextViewForTyping();

        mTextView.setText("abcd", BufferType.EDITABLE);
        mTextView.setSelected(true);

        // Cut "bc".
        Selection.setSelection((Spannable) mTextView.getText(), 1, 3);
        mTextView.onTextContextMenuItem(android.R.id.cut);
        assertEquals("ad", mTextView.getText().toString());

        // Cut "ad".
        Selection.setSelection((Spannable) mTextView.getText(), 0, 2);
        mTextView.onTextContextMenuItem(android.R.id.cut);
        assertEquals("", mTextView.getText().toString());

        // Paste "ad".
        mTextView.onTextContextMenuItem(android.R.id.paste);
        assertEquals("ad", mTextView.getText().toString());
    }

    @Test
    public void testCutAndPaste_byKey() throws Throwable {
        initTextViewForTypingOnUiThread();

        // Type "abc".
        CtsKeyEventUtil.sendString(mInstrumentation, mTextView, "abc");
        mActivityRule.runOnUiThread(() -> {
            // Select "bc"
            Selection.setSelection((Spannable) mTextView.getText(), 1, 3);
        });
        mInstrumentation.waitForIdleSync();
        // Cut "bc"
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_CUT);

        mActivityRule.runOnUiThread(() -> {
            assertEquals("a", mTextView.getText().toString());
            // Move cursor to the head
            Selection.setSelection((Spannable) mTextView.getText(), 0, 0);
        });
        mInstrumentation.waitForIdleSync();
        // Paste "bc"
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_PASTE);
        assertEquals("bca", mTextView.getText().toString());

        mActivityRule.runOnUiThread(() -> {
            Selection.selectAll((Spannable) mTextView.getText());
            KeyEvent cutWithMeta = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_CUT, 0, KeyEvent.META_SHIFT_LEFT_ON);
            // Shift + cut doesn't perform cut.
            mTextView.onKeyDown(KeyEvent.KEYCODE_CUT, cutWithMeta);
            assertEquals("bca", mTextView.getText().toString());

            cutWithMeta = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CUT, 0,
                    KeyEvent.META_CTRL_LEFT_ON);
            // Control + cut doesn't perform cut.
            mTextView.onKeyDown(KeyEvent.KEYCODE_CUT, cutWithMeta);
            assertEquals("bca", mTextView.getText().toString());

            cutWithMeta = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CUT, 0,
                    KeyEvent.META_SHIFT_LEFT_ON | KeyEvent.META_CTRL_LEFT_ON);
            // Control + Shift + cut doesn't perform cut.
            mTextView.onKeyDown(KeyEvent.KEYCODE_CUT, cutWithMeta);
            assertEquals("bca", mTextView.getText().toString());
        });
        mInstrumentation.waitForIdleSync();
    }

    private static boolean hasSpansAtMiddleOfText(final TextView textView, final Class<?> type) {
        final Spannable spannable = (Spannable)textView.getText();
        final int at = spannable.length() / 2;
        return spannable.getSpans(at, at, type).length > 0;
    }

    @UiThreadTest
    @Test
    public void testCutAndPaste_withAndWithoutStyle() {
        initTextViewForTyping();

        mTextView.setText("example", BufferType.EDITABLE);
        mTextView.setSelected(true);

        // Set URLSpan.
        final Spannable spannable = (Spannable) mTextView.getText();
        spannable.setSpan(new URLSpan("http://example.com"), 0, spannable.length(), 0);
        assertTrue(hasSpansAtMiddleOfText(mTextView, URLSpan.class));

        // Cut entire text.
        Selection.selectAll((Spannable) mTextView.getText());
        mTextView.onTextContextMenuItem(android.R.id.cut);
        assertEquals("", mTextView.getText().toString());

        // Paste without style.
        mTextView.onTextContextMenuItem(android.R.id.pasteAsPlainText);
        assertEquals("example", mTextView.getText().toString());
        // Check that the text doesn't have URLSpan.
        assertFalse(hasSpansAtMiddleOfText(mTextView, URLSpan.class));

        // Paste with style.
        Selection.selectAll((Spannable) mTextView.getText());
        mTextView.onTextContextMenuItem(android.R.id.paste);
        assertEquals("example", mTextView.getText().toString());
        // Check that the text has URLSpan.
        assertTrue(hasSpansAtMiddleOfText(mTextView, URLSpan.class));
    }

    @UiThreadTest
    @Test
    public void testSaveInstanceState() {
        // should save text when freezesText=true
        TextView originalTextView = new TextView(mActivity);
        final String text = "This is a string";
        originalTextView.setText(text);
        originalTextView.setFreezesText(true);  // needed to actually save state
        Parcelable state = originalTextView.onSaveInstanceState();

        TextView restoredTextView = new TextView(mActivity);
        restoredTextView.onRestoreInstanceState(state);
        assertEquals(text, restoredTextView.getText().toString());
    }

    @UiThreadTest
    @Test
    public void testOnSaveInstanceState_whenFreezesTextIsFalse() {
        final String text = "This is a string";
        { // should not save text when freezesText=false
            // prepare TextView for before saveInstanceState
            TextView textView1 = new TextView(mActivity);
            textView1.setFreezesText(false);
            textView1.setText(text);

            // prepare TextView for after saveInstanceState
            TextView textView2 = new TextView(mActivity);
            textView2.setFreezesText(false);

            textView2.onRestoreInstanceState(textView1.onSaveInstanceState());

            assertEquals("", textView2.getText().toString());
        }

        { // should not save text even when textIsSelectable=true
            // prepare TextView for before saveInstanceState
            TextView textView1 = new TextView(mActivity);
            textView1.setFreezesText(false);
            textView1.setTextIsSelectable(true);
            textView1.setText(text);

            // prepare TextView for after saveInstanceState
            TextView textView2 = new TextView(mActivity);
            textView2.setFreezesText(false);
            textView2.setTextIsSelectable(true);

            textView2.onRestoreInstanceState(textView1.onSaveInstanceState());

            assertEquals("", textView2.getText().toString());
        }
    }

    @UiThreadTest
    @SmallTest
    @Test
    public void testOnSaveInstanceState_doesNotSaveSelectionWhenDoesNotExist() {
        // prepare TextView for before saveInstanceState
        final String text = "This is a string";
        TextView textView1 = new TextView(mActivity);
        textView1.setFreezesText(true);
        textView1.setText(text);

        // prepare TextView for after saveInstanceState
        TextView textView2 = new TextView(mActivity);
        textView2.setFreezesText(true);

        textView2.onRestoreInstanceState(textView1.onSaveInstanceState());

        assertEquals(-1, textView2.getSelectionStart());
        assertEquals(-1, textView2.getSelectionEnd());
    }

    @UiThreadTest
    @SmallTest
    @Test
    public void testOnSaveInstanceState_doesNotRestoreSelectionWhenTextIsAbsent() {
        // prepare TextView for before saveInstanceState
        final String text = "This is a string";
        TextView textView1 = new TextView(mActivity);
        textView1.setFreezesText(false);
        textView1.setTextIsSelectable(true);
        textView1.setText(text);
        Selection.setSelection((Spannable) textView1.getText(), 2, text.length() - 2);

        // prepare TextView for after saveInstanceState
        TextView textView2 = new TextView(mActivity);
        textView2.setFreezesText(false);
        textView2.setTextIsSelectable(true);

        textView2.onRestoreInstanceState(textView1.onSaveInstanceState());

        assertEquals("", textView2.getText().toString());
        //when textIsSelectable, selection start and end are initialized to 0
        assertEquals(0, textView2.getSelectionStart());
        assertEquals(0, textView2.getSelectionEnd());
    }

    @UiThreadTest
    @SmallTest
    @Test
    public void testOnSaveInstanceState_savesSelectionWhenExists() {
        final String text = "This is a string";
        // prepare TextView for before saveInstanceState
        TextView textView1 = new TextView(mActivity);
        textView1.setFreezesText(true);
        textView1.setTextIsSelectable(true);
        textView1.setText(text);
        Selection.setSelection((Spannable) textView1.getText(), 2, text.length() - 2);

        // prepare TextView for after saveInstanceState
        TextView textView2 = new TextView(mActivity);
        textView2.setFreezesText(true);
        textView2.setTextIsSelectable(true);

        textView2.onRestoreInstanceState(textView1.onSaveInstanceState());

        assertEquals(textView1.getSelectionStart(), textView2.getSelectionStart());
        assertEquals(textView1.getSelectionEnd(), textView2.getSelectionEnd());
    }

    @UiThreadTest
    @Test
    public void testSetText() {
        TextView tv = findTextView(R.id.textview_text);

        int resId = R.string.text_view_hint;
        String result = mActivity.getResources().getString(resId);

        tv.setText(resId, BufferType.EDITABLE);
        assertEquals(result, tv.getText().toString());
        assertTrue(tv.getText() instanceof Editable);

        tv.setText(resId, BufferType.SPANNABLE);
        assertEquals(result, tv.getText().toString());
        assertTrue(tv.getText() instanceof Spannable);

        try {
            tv.setText(-1, BufferType.EDITABLE);
            fail("Should throw exception with illegal id");
        } catch (NotFoundException e) {
        }
    }

    @UiThreadTest
    @Test
    public void testAccessHint() {
        mActivity.setContentView(R.layout.textview_hint_linksclickable_freezestext);

        mTextView = findTextView(R.id.hint_linksClickable_freezesText_default);
        assertNull(mTextView.getHint());

        mTextView = findTextView(R.id.hint_blank);
        assertEquals("", mTextView.getHint());

        mTextView = findTextView(R.id.hint_string);
        assertEquals(mActivity.getResources().getString(R.string.text_view_simple_hint),
                mTextView.getHint());

        mTextView = findTextView(R.id.hint_resid);
        assertEquals(mActivity.getResources().getString(R.string.text_view_hint),
                mTextView.getHint());

        mTextView.setHint("This is hint");
        assertEquals("This is hint", mTextView.getHint().toString());

        mTextView.setHint(R.string.text_view_hello);
        assertEquals(mActivity.getResources().getString(R.string.text_view_hello),
                mTextView.getHint().toString());

        // Non-exist resid
        try {
            mTextView.setHint(-1);
            fail("Should throw exception if id is illegal");
        } catch (NotFoundException e) {
        }
    }

    @Test
    public void testAccessError() throws Throwable {
        mTextView = findTextView(R.id.textview_text);
        assertNull(mTextView.getError());

        final String errorText = "Oops! There is an error";

        mActivityRule.runOnUiThread(() -> mTextView.setError(null));
        mInstrumentation.waitForIdleSync();
        assertNull(mTextView.getError());

        final Drawable icon = TestUtils.getDrawable(mActivity, R.drawable.failed);
        mActivityRule.runOnUiThread(() -> mTextView.setError(errorText, icon));
        mInstrumentation.waitForIdleSync();
        assertEquals(errorText, mTextView.getError().toString());
        // can not check whether the drawable is set correctly

        mActivityRule.runOnUiThread(() -> mTextView.setError(null, null));
        mInstrumentation.waitForIdleSync();
        assertNull(mTextView.getError());

        mActivityRule.runOnUiThread(() -> {
            mTextView.setKeyListener(DigitsKeyListener.getInstance(""));
            mTextView.setText("", BufferType.EDITABLE);
            mTextView.setError(errorText);
            mTextView.requestFocus();
        });
        mInstrumentation.waitForIdleSync();

        assertEquals(errorText, mTextView.getError().toString());

        CtsKeyEventUtil.sendString(mInstrumentation, mTextView, "a");
        // a key event that will not change the TextView's text
        assertEquals("", mTextView.getText().toString());
        // The icon and error message will not be reset to null
        assertEquals(errorText, mTextView.getError().toString());

        mActivityRule.runOnUiThread(() -> {
            mTextView.setKeyListener(DigitsKeyListener.getInstance());
            mTextView.setText("", BufferType.EDITABLE);
            mTextView.setError(errorText);
            mTextView.requestFocus();
        });
        mInstrumentation.waitForIdleSync();

        CtsKeyEventUtil.sendString(mInstrumentation, mTextView, "1");
        // a key event cause changes to the TextView's text
        assertEquals("1", mTextView.getText().toString());
        // the error message and icon will be cleared.
        assertNull(mTextView.getError());
    }

    @Test
    public void testAccessFilters() throws Throwable {
        final InputFilter[] expected = { new InputFilter.AllCaps(),
                new InputFilter.LengthFilter(2) };

        final QwertyKeyListener qwertyKeyListener
                = QwertyKeyListener.getInstance(false, Capitalize.NONE);
        mActivityRule.runOnUiThread(() -> {
            mTextView = findTextView(R.id.textview_text);
            mTextView.setKeyListener(qwertyKeyListener);
            mTextView.setText("", BufferType.EDITABLE);
            mTextView.setFilters(expected);
            mTextView.requestFocus();
        });
        mInstrumentation.waitForIdleSync();

        assertSame(expected, mTextView.getFilters());

        CtsKeyEventUtil.sendString(mInstrumentation, mTextView, "a");
        // the text is capitalized by InputFilter.AllCaps
        assertEquals("A", mTextView.getText().toString());
        CtsKeyEventUtil.sendString(mInstrumentation, mTextView, "b");
        // the text is capitalized by InputFilter.AllCaps
        assertEquals("AB", mTextView.getText().toString());
        CtsKeyEventUtil.sendString(mInstrumentation, mTextView, "c");
        // 'C' could not be accepted, because there is a length filter.
        assertEquals("AB", mTextView.getText().toString());

        try {
            mTextView.setFilters(null);
            fail("Should throw IllegalArgumentException!");
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testGetFocusedRect() throws Throwable {
        Rect rc = new Rect();

        // Basic
        mActivityRule.runOnUiThread(() -> mTextView = new TextView(mActivity));
        mInstrumentation.waitForIdleSync();
        mTextView.getFocusedRect(rc);
        assertEquals(mTextView.getScrollX(), rc.left);
        assertEquals(mTextView.getScrollX() + mTextView.getWidth(), rc.right);
        assertEquals(mTextView.getScrollY(), rc.top);
        assertEquals(mTextView.getScrollY() + mTextView.getHeight(), rc.bottom);

        // Single line
        mTextView = findTextView(R.id.textview_text);
        mTextView.getFocusedRect(rc);
        assertEquals(mTextView.getScrollX(), rc.left);
        assertEquals(mTextView.getScrollX() + mTextView.getWidth(), rc.right);
        assertEquals(mTextView.getScrollY(), rc.top);
        assertEquals(mTextView.getScrollY() + mTextView.getHeight(), rc.bottom);

        mActivityRule.runOnUiThread(() -> {
            final SpannableString text = new SpannableString(mTextView.getText());
            mTextView.setTextIsSelectable(true);
            mTextView.setText(text);
            Selection.setSelection((Spannable) mTextView.getText(), 3, 13);
        });
        mInstrumentation.waitForIdleSync();
        mTextView.getFocusedRect(rc);
        assertNotNull(mTextView.getLayout());
        /* Cursor coordinates from getPrimaryHorizontal() may have a fractional
         * component, while the result of getFocusedRect is in int coordinates.
         * It's not practical for these to match exactly, so we compare that the
         * integer components match - there can be a fractional pixel
         * discrepancy, which should be okay for all practical applications. */
        assertEquals((int) mTextView.getLayout().getPrimaryHorizontal(3), rc.left);
        assertEquals((int) mTextView.getLayout().getPrimaryHorizontal(13), rc.right);
        assertEquals(mTextView.getLayout().getLineTop(0), rc.top);
        assertEquals(mTextView.getLayout().getLineBottom(0), rc.bottom);

        mActivityRule.runOnUiThread(() -> {
            final SpannableString text = new SpannableString(mTextView.getText());
            mTextView.setTextIsSelectable(true);
            mTextView.setText(text);
            Selection.setSelection((Spannable) mTextView.getText(), 13, 3);
        });
        mInstrumentation.waitForIdleSync();
        mTextView.getFocusedRect(rc);
        assertNotNull(mTextView.getLayout());
        assertEquals((int) mTextView.getLayout().getPrimaryHorizontal(3) - 2, rc.left);
        assertEquals((int) mTextView.getLayout().getPrimaryHorizontal(3) + 2, rc.right);
        assertEquals(mTextView.getLayout().getLineTop(0), rc.top);
        assertEquals(mTextView.getLayout().getLineBottom(0), rc.bottom);

        // Multi lines
        mTextView = findTextView(R.id.textview_text_two_lines);
        mTextView.getFocusedRect(rc);
        assertEquals(mTextView.getScrollX(), rc.left);
        assertEquals(mTextView.getScrollX() + mTextView.getWidth(), rc.right);
        assertEquals(mTextView.getScrollY(), rc.top);
        assertEquals(mTextView.getScrollY() + mTextView.getHeight(), rc.bottom);

        mActivityRule.runOnUiThread(() -> {
            final SpannableString text = new SpannableString(mTextView.getText());
            mTextView.setTextIsSelectable(true);
            mTextView.setText(text);
            Selection.setSelection((Spannable) mTextView.getText(), 2, 4);
        });
        mInstrumentation.waitForIdleSync();
        mTextView.getFocusedRect(rc);
        assertNotNull(mTextView.getLayout());
        assertEquals((int) mTextView.getLayout().getPrimaryHorizontal(2), rc.left);
        assertEquals((int) mTextView.getLayout().getPrimaryHorizontal(4), rc.right);
        assertEquals(mTextView.getLayout().getLineTop(0), rc.top);
        assertEquals(mTextView.getLayout().getLineBottom(0), rc.bottom);

        mActivityRule.runOnUiThread(() -> {
            final SpannableString text = new SpannableString(mTextView.getText());
            mTextView.setTextIsSelectable(true);
            mTextView.setText(text);
            // cross the "\n" and two lines
            Selection.setSelection((Spannable) mTextView.getText(), 2, 10);
        });
        mInstrumentation.waitForIdleSync();
        mTextView.getFocusedRect(rc);
        Path path = new Path();
        mTextView.getLayout().getSelectionPath(2, 10, path);
        RectF rcf = new RectF();
        path.computeBounds(rcf, true);
        assertNotNull(mTextView.getLayout());
        assertEquals(rcf.left - 1, (float) rc.left, 0.0f);
        assertEquals(rcf.right + 1, (float) rc.right, 0.0f);
        assertEquals(mTextView.getLayout().getLineTop(0), rc.top);
        assertEquals(mTextView.getLayout().getLineBottom(1), rc.bottom);

        // Exception
        try {
            mTextView.getFocusedRect(null);
            fail("Should throw NullPointerException!");
        } catch (NullPointerException e) {
        }
    }

    @Test
    public void testGetLineCount() throws Throwable {
        mActivityRule.runOnUiThread(() -> mTextView = findTextView(R.id.textview_text));
        mInstrumentation.waitForIdleSync();
        // this is an one line text with default setting.
        assertEquals(1, mTextView.getLineCount());

        // make it multi-lines
        setMaxWidth(mTextView.getWidth() / 3);
        assertTrue(1 < mTextView.getLineCount());

        // make it to an one line
        setMaxWidth(Integer.MAX_VALUE);
        assertEquals(1, mTextView.getLineCount());

        // set min lines don't effect the lines count for actual text.
        setMinLines(12);
        assertEquals(1, mTextView.getLineCount());

        mActivityRule.runOnUiThread(() -> mTextView = new TextView(mActivity));
        mInstrumentation.waitForIdleSync();
        // the internal Layout has not been built.
        assertNull(mTextView.getLayout());
        assertEquals(0, mTextView.getLineCount());
    }

    @Test
    public void testGetLineBounds() throws Throwable {
        Rect rc = new Rect();
        mActivityRule.runOnUiThread(() -> mTextView = new TextView(mActivity));
        mInstrumentation.waitForIdleSync();
        assertEquals(0, mTextView.getLineBounds(0, null));

        assertEquals(0, mTextView.getLineBounds(0, rc));
        assertEquals(0, rc.left);
        assertEquals(0, rc.right);
        assertEquals(0, rc.top);
        assertEquals(0, rc.bottom);

        mTextView = findTextView(R.id.textview_text);
        assertEquals(mTextView.getBaseline(), mTextView.getLineBounds(0, null));

        assertEquals(mTextView.getBaseline(), mTextView.getLineBounds(0, rc));
        assertEquals(0, rc.left);
        assertEquals(mTextView.getWidth(), rc.right);
        assertEquals(0, rc.top);
        assertEquals(mTextView.getHeight(), rc.bottom);

        mActivityRule.runOnUiThread(() -> {
            mTextView.setPadding(1, 2, 3, 4);
            mTextView.setGravity(Gravity.BOTTOM);
        });
        mInstrumentation.waitForIdleSync();
        assertEquals(mTextView.getBaseline(), mTextView.getLineBounds(0, rc));
        assertEquals(mTextView.getTotalPaddingLeft(), rc.left);
        assertEquals(mTextView.getWidth() - mTextView.getTotalPaddingRight(), rc.right);
        assertEquals(mTextView.getTotalPaddingTop(), rc.top);
        assertEquals(mTextView.getHeight() - mTextView.getTotalPaddingBottom(), rc.bottom);
    }

    @Test
    public void testGetBaseLine() throws Throwable {
        mActivityRule.runOnUiThread(() -> mTextView = new TextView(mActivity));
        mInstrumentation.waitForIdleSync();
        assertEquals(-1, mTextView.getBaseline());

        mTextView = findTextView(R.id.textview_text);
        assertEquals(mTextView.getLayout().getLineBaseline(0), mTextView.getBaseline());

        mActivityRule.runOnUiThread(() -> {
            mTextView.setPadding(1, 2, 3, 4);
            mTextView.setGravity(Gravity.BOTTOM);
        });
        mInstrumentation.waitForIdleSync();
        int expected = mTextView.getTotalPaddingTop() + mTextView.getLayout().getLineBaseline(0);
        assertEquals(expected, mTextView.getBaseline());
    }

    @Test
    public void testPressKey() throws Throwable {
        initTextViewForTypingOnUiThread();

        CtsKeyEventUtil.sendString(mInstrumentation, mTextView, "a");
        assertEquals("a", mTextView.getText().toString());
        CtsKeyEventUtil.sendString(mInstrumentation, mTextView, "b");
        assertEquals("ab", mTextView.getText().toString());
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_DEL);
        assertEquals("a", mTextView.getText().toString());
    }

    @Test
    public void testKeyNavigation() throws Throwable {
        initTextViewForTypingOnUiThread();
        mActivityRule.runOnUiThread(() -> {
            mActivity.findViewById(R.id.textview_singleLine).setFocusableInTouchMode(true);
            mActivity.findViewById(R.id.textview_text_two_lines).setFocusableInTouchMode(true);
            mTextView.setMovementMethod(ArrowKeyMovementMethod.getInstance());
            mTextView.setText("abc");
            mTextView.setFocusableInTouchMode(true);
        });

        mTextView.requestFocus();
        mInstrumentation.waitForIdleSync();
        assertTrue(mTextView.isFocused());

        // Pure-keyboard arrows should not cause focus to leave the textfield
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTextView, KeyEvent.KEYCODE_DPAD_UP);
        mInstrumentation.waitForIdleSync();
        assertTrue(mTextView.isFocused());

        // Non-pure-keyboard arrows, however, should.
        int dpadRemote = InputDevice.SOURCE_DPAD | InputDevice.SOURCE_KEYBOARD;
        sendSourceKeyDownUp(mInstrumentation, mTextView, KeyEvent.KEYCODE_DPAD_UP, dpadRemote);
        mInstrumentation.waitForIdleSync();
        assertFalse(mTextView.isFocused());

        sendSourceKeyDownUp(mInstrumentation, mTextView, KeyEvent.KEYCODE_DPAD_DOWN, dpadRemote);
        mInstrumentation.waitForIdleSync();
        assertTrue(mTextView.isFocused());

        // Tab should
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTextView, KeyEvent.KEYCODE_TAB);
        mInstrumentation.waitForIdleSync();
        assertFalse(mTextView.isFocused());
    }

    private void sendSourceKeyDownUp(Instrumentation instrumentation, View targetView, int key,
            int source) {
        KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, key);
        event.setSource(source);
        CtsKeyEventUtil.sendKey(instrumentation, targetView, event);
        event = new KeyEvent(KeyEvent.ACTION_UP, key);
        event.setSource(source);
        CtsKeyEventUtil.sendKey(instrumentation, targetView, event);
    }

    @Test
    public void testSetIncludeFontPadding() throws Throwable {
        mTextView = findTextView(R.id.textview_text);
        assertTrue(mTextView.getIncludeFontPadding());
        mActivityRule.runOnUiThread(() -> {
            mTextView.setWidth(mTextView.getWidth() / 3);
            mTextView.setPadding(1, 2, 3, 4);
            mTextView.setGravity(Gravity.BOTTOM);
        });
        mInstrumentation.waitForIdleSync();

        int oldHeight = mTextView.getHeight();
        mActivityRule.runOnUiThread(() -> mTextView.setIncludeFontPadding(false));
        mInstrumentation.waitForIdleSync();

        assertTrue(mTextView.getHeight() < oldHeight);
        assertFalse(mTextView.getIncludeFontPadding());
    }

    @UiThreadTest
    @Test
    public void testScroll() {
        mTextView = new TextView(mActivity);

        assertEquals(0, mTextView.getScrollX());
        assertEquals(0, mTextView.getScrollY());

        //don't set the Scroller, nothing changed.
        mTextView.computeScroll();
        assertEquals(0, mTextView.getScrollX());
        assertEquals(0, mTextView.getScrollY());

        //set the Scroller
        Scroller s = new Scroller(mActivity);
        assertNotNull(s);
        s.startScroll(0, 0, 320, 480, 0);
        s.abortAnimation();
        s.forceFinished(false);
        mTextView.setScroller(s);

        mTextView.computeScroll();
        assertEquals(320, mTextView.getScrollX());
        assertEquals(480, mTextView.getScrollY());
    }

    @Test
    public void testDebug() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            mTextView = new TextView(mActivity);
            mTextView.debug(0);
            mTextView.setText("Hello!");
        });
        mInstrumentation.waitForIdleSync();

        layout(mTextView);
        mTextView.debug(1);
    }

    @UiThreadTest
    @Test
    public void testSelection() throws Throwable {
        mTextView = new TextView(mActivity);
        String text = "This is the content";
        mTextView.setText(text, BufferType.SPANNABLE);
        assertFalse(mTextView.hasSelection());

        Selection.selectAll((Spannable) mTextView.getText());
        assertEquals(0, mTextView.getSelectionStart());
        assertEquals(text.length(), mTextView.getSelectionEnd());
        assertTrue(mTextView.hasSelection());

        int selectionStart = 5;
        int selectionEnd = 7;
        Selection.setSelection((Spannable) mTextView.getText(), selectionStart);
        assertEquals(selectionStart, mTextView.getSelectionStart());
        assertEquals(selectionStart, mTextView.getSelectionEnd());
        assertFalse(mTextView.hasSelection());

        Selection.setSelection((Spannable) mTextView.getText(), selectionStart, selectionEnd);
        assertEquals(selectionStart, mTextView.getSelectionStart());
        assertEquals(selectionEnd, mTextView.getSelectionEnd());
        assertTrue(mTextView.hasSelection());
    }

    @Test
    public void testOnSelectionChangedIsTriggeredWhenSelectionChanges() throws Throwable {
        final String text = "any text";
        mActivityRule.runOnUiThread(() -> mTextView = spy(new MockTextView(mActivity)));
        mInstrumentation.waitForIdleSync();
        mTextView.setText(text, BufferType.SPANNABLE);

        // assert that there is currently no selection
        assertFalse(mTextView.hasSelection());

        // select all
        Selection.selectAll((Spannable) mTextView.getText());
        // After selectAll OnSelectionChanged should have been called
        ((MockTextView) verify(mTextView, times(1))).onSelectionChanged(0, text.length());

        reset(mTextView);
        // change selection
        Selection.setSelection((Spannable) mTextView.getText(), 1, 5);
        ((MockTextView) verify(mTextView, times(1))).onSelectionChanged(1, 5);

        reset(mTextView);
        // clear selection
        Selection.removeSelection((Spannable) mTextView.getText());
        ((MockTextView) verify(mTextView, times(1))).onSelectionChanged(-1, -1);
    }

    @UiThreadTest
    @Test
    public void testAccessEllipsize() {
        mActivity.setContentView(R.layout.textview_ellipsize);

        mTextView = findTextView(R.id.ellipsize_default);
        assertNull(mTextView.getEllipsize());

        mTextView = findTextView(R.id.ellipsize_none);
        assertNull(mTextView.getEllipsize());

        mTextView = findTextView(R.id.ellipsize_start);
        assertSame(TruncateAt.START, mTextView.getEllipsize());

        mTextView = findTextView(R.id.ellipsize_middle);
        assertSame(TruncateAt.MIDDLE, mTextView.getEllipsize());

        mTextView = findTextView(R.id.ellipsize_end);
        assertSame(TruncateAt.END, mTextView.getEllipsize());

        mTextView.setEllipsize(TextUtils.TruncateAt.START);
        assertSame(TextUtils.TruncateAt.START, mTextView.getEllipsize());

        mTextView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        assertSame(TextUtils.TruncateAt.MIDDLE, mTextView.getEllipsize());

        mTextView.setEllipsize(TextUtils.TruncateAt.END);
        assertSame(TextUtils.TruncateAt.END, mTextView.getEllipsize());

        mTextView.setEllipsize(null);
        assertNull(mTextView.getEllipsize());

        mTextView.setWidth(10);
        mTextView.setEllipsize(TextUtils.TruncateAt.START);
        mTextView.setText("ThisIsAVeryLongVeryLongVeryLongVeryLongVeryLongWord");
        mTextView.invalidate();

        assertSame(TextUtils.TruncateAt.START, mTextView.getEllipsize());
        // there is no method to check if '...yLongVeryLongWord' is painted in the screen.
    }

    @Test
    public void testEllipsizeAndMaxLinesForSingleLine() throws Throwable {
        // no maxline or ellipsize set, single line text
        final TextView tvNoMaxLine = new TextView(mActivity);
        tvNoMaxLine.setLineSpacing(0, 1.5f);
        tvNoMaxLine.setText("a");

        // maxline set, no ellipsize, text with two lines
        final TextView tvEllipsizeNone = new TextView(mActivity);
        tvEllipsizeNone.setMaxLines(1);
        tvEllipsizeNone.setLineSpacing(0, 1.5f);
        tvEllipsizeNone.setText("a\na");

        // maxline set, ellipsize end, text with two lines
        final TextView tvEllipsizeEnd = new TextView(mActivity);
        tvEllipsizeEnd.setEllipsize(TruncateAt.END);
        tvEllipsizeEnd.setMaxLines(1);
        tvEllipsizeEnd.setLineSpacing(0, 1.5f);
        tvEllipsizeEnd.setText("a\na");

        final FrameLayout layout = new FrameLayout(mActivity);
        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        layout.addView(tvEllipsizeEnd, layoutParams);
        layout.addView(tvEllipsizeNone, layoutParams);
        layout.addView(tvNoMaxLine, layoutParams);

        mActivityRule.runOnUiThread(() -> mActivity.setContentView(layout,
                new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT)));
        mInstrumentation.waitForIdleSync();

        assertEquals(tvEllipsizeEnd.getHeight(), tvEllipsizeNone.getHeight());

        assertEquals(tvEllipsizeEnd.getHeight(), tvNoMaxLine.getHeight());

        assertEquals(tvEllipsizeEnd.getLayout().getLineBaseline(0),
                tvEllipsizeNone.getLayout().getLineBaseline(0));

        assertEquals(tvEllipsizeEnd.getLayout().getLineBaseline(0),
                tvNoMaxLine.getLayout().getLineBaseline(0));
    }

    @Test
    public void testEllipsizeAndMaxLinesForMultiLine() throws Throwable {
        // no maxline, no ellipsize, text with two lines
        final TextView tvNoMaxLine = new TextView(mActivity);
        tvNoMaxLine.setLineSpacing(0, 1.5f);
        tvNoMaxLine.setText("a\na");

        // maxline set, no ellipsize, text with three lines
        final TextView tvEllipsizeNone = new TextView(mActivity);
        tvEllipsizeNone.setMaxLines(2);
        tvEllipsizeNone.setLineSpacing(0, 1.5f);
        tvEllipsizeNone.setText("a\na\na");

        // maxline set, ellipsize end, text with three lines
        final TextView tvEllipsizeEnd = new TextView(mActivity);
        tvEllipsizeEnd.setEllipsize(TruncateAt.END);
        tvEllipsizeEnd.setMaxLines(2);
        tvEllipsizeEnd.setLineSpacing(0, 1.5f);
        tvEllipsizeEnd.setText("a\na\na");

        final FrameLayout layout = new FrameLayout(mActivity);
        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);

        layout.addView(tvNoMaxLine, layoutParams);
        layout.addView(tvEllipsizeEnd, layoutParams);
        layout.addView(tvEllipsizeNone, layoutParams);

        mActivityRule.runOnUiThread(() ->  mActivity.setContentView(layout,
                new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT)));
        mInstrumentation.waitForIdleSync();

        assertEquals(tvEllipsizeEnd.getHeight(), tvEllipsizeNone.getHeight());

        assertEquals(tvEllipsizeEnd.getHeight(), tvNoMaxLine.getHeight());

        for (int i = 0; i < tvEllipsizeEnd.getLineCount(); i++) {
            assertEquals("Should have the same baseline for line " + i,
                    tvEllipsizeEnd.getLayout().getLineBaseline(i),
                    tvEllipsizeNone.getLayout().getLineBaseline(i));

            assertEquals("Should have the same baseline for line " + i,
                    tvEllipsizeEnd.getLayout().getLineBaseline(i),
                    tvNoMaxLine.getLayout().getLineBaseline(i));
        }
    }

    @Test
    public void testEllipsizeAndMaxLinesForHint() throws Throwable {
        // no maxline, no ellipsize, hint with two lines
        final TextView tvTwoLines = new TextView(mActivity);
        tvTwoLines.setLineSpacing(0, 1.5f);
        tvTwoLines.setHint("a\na");

        // no maxline, no ellipsize, hint with three lines
        final TextView tvThreeLines = new TextView(mActivity);
        tvThreeLines.setLineSpacing(0, 1.5f);
        tvThreeLines.setHint("a\na\na");

        // maxline set, ellipsize end, hint with three lines
        final TextView tvEllipsizeEnd = new TextView(mActivity);
        tvEllipsizeEnd.setEllipsize(TruncateAt.END);
        tvEllipsizeEnd.setMaxLines(2);
        tvEllipsizeEnd.setLineSpacing(0, 1.5f);
        tvEllipsizeEnd.setHint("a\na\na");

        // maxline set, no ellipsize, hint with three lines
        final TextView tvEllipsizeNone = new TextView(mActivity);
        tvEllipsizeNone.setMaxLines(2);
        tvEllipsizeNone.setLineSpacing(0, 1.5f);
        tvEllipsizeNone.setHint("a\na\na");

        final FrameLayout layout = new FrameLayout(mActivity);
        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);

        layout.addView(tvTwoLines, layoutParams);
        layout.addView(tvEllipsizeEnd, layoutParams);
        layout.addView(tvEllipsizeNone, layoutParams);
        layout.addView(tvThreeLines, layoutParams);

        mActivityRule.runOnUiThread(() ->  mActivity.setContentView(layout,
                new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT)));
        mInstrumentation.waitForIdleSync();

        assertEquals("Non-ellipsized hint should not crop text at maxLines",
                tvThreeLines.getHeight(), tvEllipsizeNone.getHeight());

        assertEquals("Ellipsized hint should crop text at maxLines",
                tvTwoLines.getHeight(), tvEllipsizeEnd.getHeight());
    }

    @UiThreadTest
    @Test
    public void testAccessCursorVisible() {
        mTextView = new TextView(mActivity);

        mTextView.setCursorVisible(true);
        assertTrue(mTextView.isCursorVisible());
        mTextView.setCursorVisible(false);
        assertFalse(mTextView.isCursorVisible());
    }

    @UiThreadTest
    @Test
    public void testPerformLongClick() {
        mTextView = findTextView(R.id.textview_text);
        mTextView.setText("This is content");

        View.OnLongClickListener mockOnLongClickListener = mock(View.OnLongClickListener.class);
        when(mockOnLongClickListener.onLongClick(any(View.class))).thenReturn(Boolean.TRUE);

        View.OnCreateContextMenuListener mockOnCreateContextMenuListener =
                mock(View.OnCreateContextMenuListener.class);
        doAnswer((InvocationOnMock invocation) -> {
            ((ContextMenu) invocation.getArguments() [0]).add("menu item");
            return null;
        }).when(mockOnCreateContextMenuListener).onCreateContextMenu(
                any(ContextMenu.class), any(View.class), any());

        mTextView.setOnLongClickListener(mockOnLongClickListener);
        mTextView.setOnCreateContextMenuListener(mockOnCreateContextMenuListener);
        assertTrue(mTextView.performLongClick());
        verify(mockOnLongClickListener, times(1)).onLongClick(mTextView);
        verifyZeroInteractions(mockOnCreateContextMenuListener);

        reset(mockOnLongClickListener);
        when(mockOnLongClickListener.onLongClick(any(View.class))).thenReturn(Boolean.FALSE);
        assertTrue(mTextView.performLongClick());
        verify(mockOnLongClickListener, times(1)).onLongClick(mTextView);
        verify(mockOnCreateContextMenuListener, times(1)).onCreateContextMenu(
                any(ContextMenu.class), eq(mTextView), any());

        reset(mockOnCreateContextMenuListener);
        mTextView.setOnLongClickListener(null);
        doNothing().when(mockOnCreateContextMenuListener).onCreateContextMenu(
                any(ContextMenu.class), any(View.class), any());
        assertFalse(mTextView.performLongClick());
        verifyNoMoreInteractions(mockOnLongClickListener);
        verify(mockOnCreateContextMenuListener, times(1)).onCreateContextMenu(
                any(ContextMenu.class), eq(mTextView), any());
    }

    @UiThreadTest
    @Test
    public void testTextAttr() {
        mTextView = findTextView(R.id.textview_textAttr);
        // getText
        assertEquals(mActivity.getString(R.string.text_view_hello), mTextView.getText().toString());

        // getCurrentTextColor
        assertEquals(mActivity.getResources().getColor(R.drawable.black),
                mTextView.getCurrentTextColor());
        assertEquals(mActivity.getResources().getColor(R.drawable.red),
                mTextView.getCurrentHintTextColor());
        assertEquals(mActivity.getResources().getColor(R.drawable.red),
                mTextView.getHintTextColors().getDefaultColor());
        assertEquals(mActivity.getResources().getColor(R.drawable.blue),
                mTextView.getLinkTextColors().getDefaultColor());

        // getTextScaleX
        assertEquals(1.2f, mTextView.getTextScaleX(), 0.01f);

        // setTextScaleX
        mTextView.setTextScaleX(2.4f);
        assertEquals(2.4f, mTextView.getTextScaleX(), 0.01f);

        mTextView.setTextScaleX(0f);
        assertEquals(0f, mTextView.getTextScaleX(), 0.01f);

        mTextView.setTextScaleX(- 2.4f);
        assertEquals(- 2.4f, mTextView.getTextScaleX(), 0.01f);

        // getTextSize
        assertEquals(20f, mTextView.getTextSize(), 0.01f);

        // getTypeface
        // getTypeface will be null if android:typeface is set to normal,
        // and android:style is not set or is set to normal, and
        // android:fontFamily is not set
        assertNull(mTextView.getTypeface());

        mTextView.setTypeface(Typeface.DEFAULT);
        assertSame(Typeface.DEFAULT, mTextView.getTypeface());
        // null type face
        mTextView.setTypeface(null);
        assertNull(mTextView.getTypeface());

        // default type face, bold style, note: the type face will be changed
        // after call set method
        mTextView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        assertSame(Typeface.BOLD, mTextView.getTypeface().getStyle());

        // null type face, BOLD style
        mTextView.setTypeface(null, Typeface.BOLD);
        assertSame(Typeface.BOLD, mTextView.getTypeface().getStyle());

        // old type face, null style
        mTextView.setTypeface(Typeface.DEFAULT, 0);
        assertEquals(Typeface.NORMAL, mTextView.getTypeface().getStyle());
    }

    @UiThreadTest
    @Test
    public void testAppend() {
        mTextView = new TextView(mActivity);

        // 1: check the original length, should be blank as initialised.
        assertEquals(0, mTextView.getText().length());

        // 2: append a string use append(CharSquence) into the original blank
        // buffer, check the content. And upgrading it to BufferType.EDITABLE if it was
        // not already editable.
        assertFalse(mTextView.getText() instanceof Editable);
        mTextView.append("Append.");
        assertEquals("Append.", mTextView.getText().toString());
        assertTrue(mTextView.getText() instanceof Editable);

        // 3: append a string from 0~3.
        mTextView.append("Append", 0, 3);
        assertEquals("Append.App", mTextView.getText().toString());
        assertTrue(mTextView.getText() instanceof Editable);

        // 4: append a string from 0~0, nothing will be append as expected.
        mTextView.append("Append", 0, 0);
        assertEquals("Append.App", mTextView.getText().toString());
        assertTrue(mTextView.getText() instanceof Editable);

        // 5: append a string from -3~3. check the wrong left edge.
        try {
            mTextView.append("Append", -3, 3);
            fail("Should throw StringIndexOutOfBoundsException");
        } catch (StringIndexOutOfBoundsException e) {
        }

        // 6: append a string from 3~10. check the wrong right edge.
        try {
            mTextView.append("Append", 3, 10);
            fail("Should throw StringIndexOutOfBoundsException");
        } catch (StringIndexOutOfBoundsException e) {
        }

        // 7: append a null string.
        try {
            mTextView.append(null);
            fail("Should throw NullPointerException");
        } catch (NullPointerException e) {
        }
    }

    @UiThreadTest
    @Test
    public void testAppend_doesNotAddLinksWhenAppendedTextDoesNotContainLinks() {
        mTextView = new TextView(mActivity);
        mTextView.setAutoLinkMask(Linkify.ALL);
        mTextView.setText("text without URL");

        mTextView.append(" another text without URL");

        Spannable text = (Spannable) mTextView.getText();
        URLSpan[] urlSpans = text.getSpans(0, text.length(), URLSpan.class);
        assertEquals("URLSpan count should be zero", 0, urlSpans.length);
        assertEquals("text without URL another text without URL", text.toString());
    }

    @UiThreadTest
    @Test
    public void testAppend_doesNotAddLinksWhenAutoLinkIsNotEnabled() {
        mTextView = new TextView(mActivity);
        mTextView.setText("text without URL");

        mTextView.append(" text with URL http://android.com");

        Spannable text = (Spannable) mTextView.getText();
        URLSpan[] urlSpans = text.getSpans(0, text.length(), URLSpan.class);
        assertEquals("URLSpan count should be zero", 0, urlSpans.length);
        assertEquals("text without URL text with URL http://android.com", text.toString());
    }

    @UiThreadTest
    @Test
    public void testAppend_addsLinksWhenAutoLinkIsEnabled() {
        mTextView = new TextView(mActivity);
        mTextView.setAutoLinkMask(Linkify.ALL);
        mTextView.setText("text without URL");

        mTextView.append(" text with URL http://android.com");

        Spannable text = (Spannable) mTextView.getText();
        URLSpan[] urlSpans = text.getSpans(0, text.length(), URLSpan.class);
        assertEquals("URLSpan count should be one after appending a URL", 1, urlSpans.length);
        assertEquals("URLSpan URL should be same as the appended URL",
                urlSpans[0].getURL(), "http://android.com");
        assertEquals("text without URL text with URL http://android.com", text.toString());
    }

    @UiThreadTest
    @Test
    public void testAppend_addsLinksEvenWhenThereAreUrlsSetBefore() {
        mTextView = new TextView(mActivity);
        mTextView.setAutoLinkMask(Linkify.ALL);
        mTextView.setText("text with URL http://android.com/before");

        mTextView.append(" text with URL http://android.com");

        Spannable text = (Spannable) mTextView.getText();
        URLSpan[] urlSpans = text.getSpans(0, text.length(), URLSpan.class);
        assertEquals("URLSpan count should be two after appending another URL", 2, urlSpans.length);
        assertEquals("First URLSpan URL should be same",
                urlSpans[0].getURL(), "http://android.com/before");
        assertEquals("URLSpan URL should be same as the appended URL",
                urlSpans[1].getURL(), "http://android.com");
        assertEquals("text with URL http://android.com/before text with URL http://android.com",
                text.toString());
    }

    @UiThreadTest
    @Test
    public void testAppend_setsMovementMethodWhenTextContainsUrlAndAutoLinkIsEnabled() {
        mTextView = new TextView(mActivity);
        mTextView.setAutoLinkMask(Linkify.ALL);
        mTextView.setText("text without a URL");

        mTextView.append(" text with a url: http://android.com");

        assertNotNull("MovementMethod should not be null when text contains url",
                mTextView.getMovementMethod());
        assertTrue("MovementMethod should be instance of LinkMovementMethod when text contains url",
                mTextView.getMovementMethod() instanceof LinkMovementMethod);
    }

    @UiThreadTest
    @Test
    public void testAppend_addsLinksWhenTextIsSpannableAndContainsUrlAndAutoLinkIsEnabled() {
        mTextView = new TextView(mActivity);
        mTextView.setAutoLinkMask(Linkify.ALL);
        mTextView.setText("text without a URL");

        mTextView.append(new SpannableString(" text with a url: http://android.com"));

        Spannable text = (Spannable) mTextView.getText();
        URLSpan[] urlSpans = text.getSpans(0, text.length(), URLSpan.class);
        assertEquals("URLSpan count should be one after appending a URL", 1, urlSpans.length);
        assertEquals("URLSpan URL should be same as the appended URL",
                urlSpans[0].getURL(), "http://android.com");
    }

    @UiThreadTest
    @Test
    public void testAppend_addsLinkIfAppendedTextCompletesPartialUrlAtTheEndOfExistingText() {
        mTextView = new TextView(mActivity);
        mTextView.setAutoLinkMask(Linkify.ALL);
        mTextView.setText("text with a partial url android.");

        mTextView.append("com");

        Spannable text = (Spannable) mTextView.getText();
        URLSpan[] urlSpans = text.getSpans(0, text.length(), URLSpan.class);
        assertEquals("URLSpan count should be one after appending to partial URL",
                1, urlSpans.length);
        assertEquals("URLSpan URL should be same as the appended URL",
                urlSpans[0].getURL(), "http://android.com");
    }

    @UiThreadTest
    @Test
    public void testAppend_addsLinkIfAppendedTextUpdatesUrlAtTheEndOfExistingText() {
        mTextView = new TextView(mActivity);
        mTextView.setAutoLinkMask(Linkify.ALL);
        mTextView.setText("text with a url http://android.com");

        mTextView.append("/textview");

        Spannable text = (Spannable) mTextView.getText();
        URLSpan[] urlSpans = text.getSpans(0, text.length(), URLSpan.class);
        assertEquals("URLSpan count should still be one after extending a URL", 1, urlSpans.length);
        assertEquals("URLSpan URL should be same as the new URL",
                urlSpans[0].getURL(), "http://android.com/textview");
    }

    @UiThreadTest
    @Test
    public void testGetLetterSpacing_returnsValueThatWasSet() {
        mTextView = new TextView(mActivity);
        mTextView.setLetterSpacing(2f);
        assertEquals("getLetterSpacing should return the value that was set",
                2f, mTextView.getLetterSpacing(), 0.0f);
    }

    @Test
    public void testSetLetterSpacingChangesTextWidth() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            mTextView = new TextView(mActivity);
            mTextView.setText("aa");
            mTextView.setLetterSpacing(0f);
            mTextView.setTextSize(8f);
        });
        mInstrumentation.waitForIdleSync();

        final FrameLayout layout = new FrameLayout(mActivity);
        final ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        layout.addView(mTextView, layoutParams);
        layout.setLayoutParams(layoutParams);

        mActivityRule.runOnUiThread(() -> mActivity.setContentView(layout));
        mInstrumentation.waitForIdleSync();

        // measure text with zero letter spacing
        final float zeroSpacing = mTextView.getLayout().getLineWidth(0);

        mActivityRule.runOnUiThread(() -> mTextView.setLetterSpacing(1f));
        mInstrumentation.waitForIdleSync();

        // measure text with single letter spacing
        final float singleSpacing = mTextView.getLayout().getLineWidth(0);

        mActivityRule.runOnUiThread(() -> mTextView.setLetterSpacing(2f));
        mInstrumentation.waitForIdleSync();

        // measure text with double letter spacing
        final float doubleSpacing = mTextView.getLayout().getLineWidth(0);

        assertEquals("Double spacing should have two times the spacing of single spacing",
                doubleSpacing - zeroSpacing, 2f * (singleSpacing - zeroSpacing), 1f);
    }

    @UiThreadTest
    @Test
    public void testGetFontFeatureSettings_returnsValueThatWasSet() {
        mTextView = new TextView(mActivity);
        mTextView.setFontFeatureSettings("\"smcp\" on");
        assertEquals("getFontFeatureSettings should return the value that was set",
                "\"smcp\" on", mTextView.getFontFeatureSettings());
    }

    @UiThreadTest
    @Test
    public void testSetGetFontVariationSettings() {
        mTextView = new TextView(mActivity);
        Context context = InstrumentationRegistry.getTargetContext();
        Typeface typeface = Typeface.createFromAsset(context.getAssets(), "multiaxis.ttf");
        mTextView.setTypeface(typeface);

        // multiaxis.ttf supports "aaaa", "BBBB", "a b ", " C D" axes.

        // The default variation settings should be null.
        assertNull(mTextView.getFontVariationSettings());

        final String[] invalidFormatSettings = {
                "invalid syntax",
                "'aaa' 1.0",  // tag is not 4 ascii chars
        };
        for (String settings : invalidFormatSettings) {
            try {
                mTextView.setFontVariationSettings(settings);
                fail();
            } catch (IllegalArgumentException e) {
                // pass.
            }
            assertNull("Must not change settings for " + settings,
                    mTextView.getFontVariationSettings());
        }

        final String[] nonEffectiveSettings = {
                "'bbbb' 1.0",  // unsupported tag
                "'    ' 1.0",  // unsupported tag
                "'AAAA' 0.7",  // unsupported tag (case sensitive)
                "' a b' 1.3",  // unsupported tag (white space should not be ignored)
                "'C D ' 1.3",  // unsupported tag (white space should not be ignored)
                "'bbbb' 1.0, 'cccc' 2.0",  // none of them are supported.
        };

        for (String notEffectiveSetting : nonEffectiveSettings) {
            assertFalse("Must return false for " + notEffectiveSetting,
                    mTextView.setFontVariationSettings(notEffectiveSetting));
            assertNull("Must not change settings for " + notEffectiveSetting,
                    mTextView.getFontVariationSettings());
        }

        String retainSettings = "'aaaa' 1.0";
        assertTrue(mTextView.setFontVariationSettings(retainSettings));
        for (String notEffectiveSetting : nonEffectiveSettings) {
            assertFalse(mTextView.setFontVariationSettings(notEffectiveSetting));
            assertEquals("Must not change settings for " + notEffectiveSetting,
                    retainSettings, mTextView.getFontVariationSettings());
        }

        // At least one axis is supported, the settings should be applied.
        final String[] effectiveSettings = {
                "'aaaa' 1.0",  // supported tag
                "'a b ' .7",  // supported tag (contains whitespace)
                "'aaaa' 1.0, 'BBBB' 0.5",  // both are supported
                "'aaaa' 1.0, ' C D' 0.5",  // both are supported
                "'aaaa' 1.0, 'bbbb' 0.4",  // 'bbbb' is unspported.
        };

        for (String effectiveSetting : effectiveSettings) {
            assertTrue(mTextView.setFontVariationSettings(effectiveSetting));
            assertEquals(effectiveSetting, mTextView.getFontVariationSettings());
        }

        mTextView.setFontVariationSettings("");
        assertNull(mTextView.getFontVariationSettings());
    }

    @Test
    public void testGetOffsetForPositionSingleLineLtr() throws Throwable {
        // asserts getOffsetPosition returns correct values for a single line LTR text
        final String text = "aaaaa";

        mActivityRule.runOnUiThread(() -> {
            mTextView = new TextView(mActivity);
            mTextView.setText(text);
            mTextView.setTextSize(8f);
            mTextView.setSingleLine(true);
        });
        mInstrumentation.waitForIdleSync();

        // add a compound drawable to TextView to make offset calculation more interesting
        final Drawable drawable = TestUtils.getDrawable(mActivity, R.drawable.red);
        drawable.setBounds(0, 0, 10, 10);
        mTextView.setCompoundDrawables(drawable, drawable, drawable, drawable);

        final FrameLayout layout = new FrameLayout(mActivity);
        final ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        layout.addView(mTextView, layoutParams);
        layout.setLayoutParams(layoutParams);

        mActivityRule.runOnUiThread(() -> mActivity.setContentView(layout));
        mInstrumentation.waitForIdleSync();

        final float horizontalPosFix = (float) Math.ceil(
                mTextView.getPaint().measureText("a") * 2f / 3f);
        final int paddingTop = mTextView.getTotalPaddingTop();
        final int paddingLeft = mTextView.getTotalPaddingLeft();

        final int firstOffset = 0;
        final int lastOffset = text.length() - 1;
        final int midOffset = text.length() / 2;

        // left edge of view
        float x = 0f;
        float y = mTextView.getHeight() / 2f + paddingTop;
        assertEquals(firstOffset, mTextView.getOffsetForPosition(x, y));

        // right edge of text
        x = mTextView.getLayout().getLineWidth(0) + paddingLeft - horizontalPosFix;
        assertEquals(lastOffset, mTextView.getOffsetForPosition(x, y));

        // right edge of view
        x = mTextView.getWidth();
        assertEquals(lastOffset + 1, mTextView.getOffsetForPosition(x, y));

        // left edge of view - out of bounds
        x = -1f;
        assertEquals(firstOffset, mTextView.getOffsetForPosition(x, y));

        // horizontal center of text
        x = mTextView.getLayout().getLineWidth(0) / 2f + paddingLeft - horizontalPosFix;
        assertEquals(midOffset, mTextView.getOffsetForPosition(x, y));
    }

    @Test
    public void testGetOffsetForPositionMultiLineLtr() throws Throwable {
        final String line = "aaa\n";
        final String threeLines = line + line + line;
        mActivityRule.runOnUiThread(() -> {
            mTextView = new TextView(mActivity);
            mTextView.setText(threeLines);
            mTextView.setTextSize(8f);
            mTextView.setLines(2);
        });
        mInstrumentation.waitForIdleSync();

        // add a compound drawable to TextView to make offset calculation more interesting
        final Drawable drawable = TestUtils.getDrawable(mActivity, R.drawable.red);
        drawable.setBounds(0, 0, 10, 10);
        mTextView.setCompoundDrawables(drawable, drawable, drawable, drawable);

        final FrameLayout layout = new FrameLayout(mActivity);
        final ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        layout.addView(mTextView, layoutParams);
        layout.setLayoutParams(layoutParams);

        mActivityRule.runOnUiThread(() -> mActivity.setContentView(layout));
        mInstrumentation.waitForIdleSync();

        final Rect lineBounds = new Rect();
        mTextView.getLayout().getLineBounds(0, lineBounds);

        final float horizontalPosFix = (float) Math.ceil(
                mTextView.getPaint().measureText("a") * 2f / 3f);
        final int paddingTop = mTextView.getTotalPaddingTop();
        final int paddingLeft = mTextView.getTotalPaddingLeft();

        // left edge of view at first line
        float x = 0f;
        float y = lineBounds.height() / 2f + paddingTop;
        assertEquals(0, mTextView.getOffsetForPosition(x, y));

        // right edge of view at first line
        x = mTextView.getWidth() - 1f;
        assertEquals(line.length() - 1, mTextView.getOffsetForPosition(x, y));

        // update lineBounds to be the second line
        mTextView.getLayout().getLineBounds(1, lineBounds);
        y = lineBounds.top + lineBounds.height() / 2f + paddingTop;

        // left edge of view at second line
        x = 0f;
        assertEquals(line.length(), mTextView.getOffsetForPosition(x, y));

        // right edge of text at second line
        x = mTextView.getLayout().getLineWidth(1) + paddingLeft - horizontalPosFix;
        assertEquals(line.length() + line.length() - 1, mTextView.getOffsetForPosition(x, y));

        // right edge of view at second line
        x = mTextView.getWidth() - 1f;
        assertEquals(line.length() + line.length() - 1, mTextView.getOffsetForPosition(x, y));

        // horizontal center of text at second line
        x = mTextView.getLayout().getLineWidth(1) / 2f + paddingLeft - horizontalPosFix;
        // second line mid offset should not include next line, therefore subtract one
        assertEquals(line.length() + (line.length() - 1) / 2, mTextView.getOffsetForPosition(x, y));
    }

    @Test
    public void testGetOffsetForPositionMultiLineRtl() throws Throwable {
        final String line = "\u0635\u0635\u0635\n";
        final String threeLines = line + line + line;
        mActivityRule.runOnUiThread(() -> {
            mTextView = new TextView(mActivity);
            mTextView.setText(threeLines);
            mTextView.setTextSize(8f);
            mTextView.setLines(2);
        });
        mInstrumentation.waitForIdleSync();

        // add a compound drawable to TextView to make offset calculation more interesting
        final Drawable drawable = TestUtils.getDrawable(mActivity, R.drawable.red);
        drawable.setBounds(0, 0, 10, 10);
        mTextView.setCompoundDrawables(drawable, drawable, drawable, drawable);

        final FrameLayout layout = new FrameLayout(mActivity);
        final LayoutParams layoutParams = new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT);
        layout.addView(mTextView, layoutParams);
        layout.setLayoutParams(layoutParams);

        mActivityRule.runOnUiThread(() -> mActivity.setContentView(layout));
        mInstrumentation.waitForIdleSync();

        final Rect lineBounds = new Rect();
        mTextView.getLayout().getLineBounds(0, lineBounds);

        final float horizontalPosFix = (float) Math.ceil(
                mTextView.getPaint().measureText("\u0635") * 2f / 3f);
        final int paddingTop = mTextView.getTotalPaddingTop();
        final int paddingRight = mTextView.getTotalPaddingRight();

        // right edge of view at first line
        float x = mTextView.getWidth() - 1f;
        float y = lineBounds.height() / 2f + paddingTop;
        assertEquals(0, mTextView.getOffsetForPosition(x, y));

        // left edge of view at first line
        x = 0f;
        assertEquals(line.length() - 1, mTextView.getOffsetForPosition(x, y));

        // update lineBounds to be the second line
        mTextView.getLayout().getLineBounds(1, lineBounds);
        y = lineBounds.top + lineBounds.height() / 2f + paddingTop;

        // right edge of view at second line
        x = mTextView.getWidth() - 1f;
        assertEquals(line.length(), mTextView.getOffsetForPosition(x, y));

        // left edge of view at second line
        x = 0f;
        assertEquals(line.length() + line.length() - 1, mTextView.getOffsetForPosition(x, y));

        // left edge of text at second line
        x = mTextView.getWidth() - (mTextView.getLayout().getLineWidth(1) + paddingRight
                - horizontalPosFix);
        assertEquals(line.length() + line.length() - 1, mTextView.getOffsetForPosition(x, y));

        // horizontal center of text at second line
        x = mTextView.getWidth() - (mTextView.getLayout().getLineWidth(1) / 2f + paddingRight
                - horizontalPosFix);
        // second line mid offset should not include next line, therefore subtract one
        assertEquals(line.length() + (line.length() - 1) / 2, mTextView.getOffsetForPosition(x, y));
    }

    @UiThreadTest
    @Test
    public void testIsTextSelectable_returnsFalseByDefault() {
        final TextView textView = new TextView(mActivity);
        textView.setText("any text");
        assertFalse(textView.isTextSelectable());
    }

    @UiThreadTest
    @Test
    public void testIsTextSelectable_returnsTrueIfSetTextIsSelectableCalledWithTrue() {
        final TextView textView = new TextView(mActivity);
        textView.setText("any text");
        textView.setTextIsSelectable(true);
        assertTrue(textView.isTextSelectable());
    }

    @UiThreadTest
    @Test
    public void testSetIsTextSelectable() {
        final TextView textView = new TextView(mActivity);

        assertFalse(textView.isTextSelectable());
        assertFalse(textView.isFocusable());
        assertFalse(textView.isFocusableInTouchMode());
        assertFalse(textView.isClickable());
        assertFalse(textView.isLongClickable());

        textView.setTextIsSelectable(true);

        assertTrue(textView.isTextSelectable());
        assertTrue(textView.isFocusable());
        assertTrue(textView.isFocusableInTouchMode());
        assertTrue(textView.isClickable());
        assertTrue(textView.isLongClickable());
        assertNotNull(textView.getMovementMethod());
    }

    @Test
    public void testAccessTransformationMethod() throws Throwable {
        // check the password attribute in xml
        mTextView = findTextView(R.id.textview_password);
        assertNotNull(mTextView);
        assertSame(PasswordTransformationMethod.getInstance(),
                mTextView.getTransformationMethod());

        // check the singleLine attribute in xml
        mTextView = findTextView(R.id.textview_singleLine);
        assertNotNull(mTextView);
        assertSame(SingleLineTransformationMethod.getInstance(),
                mTextView.getTransformationMethod());

        final QwertyKeyListener qwertyKeyListener = QwertyKeyListener.getInstance(false,
                Capitalize.NONE);
        final TransformationMethod method = PasswordTransformationMethod.getInstance();
        // change transformation method by function
        mActivityRule.runOnUiThread(() -> {
            mTextView.setKeyListener(qwertyKeyListener);
            mTextView.setTransformationMethod(method);
            mTransformedText = method.getTransformation(mTextView.getText(), mTextView);

            mTextView.requestFocus();
        });
        mInstrumentation.waitForIdleSync();
        assertSame(PasswordTransformationMethod.getInstance(),
                mTextView.getTransformationMethod());

        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, "H E 2*L O");
        mActivityRule.runOnUiThread(() -> mTextView.append(" "));
        mInstrumentation.waitForIdleSync();

        // It will get transformed after a while
        // We're waiting for transformation to "******"
        PollingCheck.waitFor(TIMEOUT, () -> mTransformedText.toString()
                .equals("\u2022\u2022\u2022\u2022\u2022\u2022"));

        // set null
        mActivityRule.runOnUiThread(() -> mTextView.setTransformationMethod(null));
        mInstrumentation.waitForIdleSync();
        assertNull(mTextView.getTransformationMethod());
    }

    @UiThreadTest
    @Test
    public void testCompound() {
        mTextView = new TextView(mActivity);
        int padding = 3;
        Drawable[] drawables = mTextView.getCompoundDrawables();
        assertNull(drawables[0]);
        assertNull(drawables[1]);
        assertNull(drawables[2]);
        assertNull(drawables[3]);

        // test setCompoundDrawablePadding and getCompoundDrawablePadding
        mTextView.setCompoundDrawablePadding(padding);
        assertEquals(padding, mTextView.getCompoundDrawablePadding());

        // using resid, 0 represents null
        mTextView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.start, R.drawable.pass,
                R.drawable.failed, 0);
        drawables = mTextView.getCompoundDrawables();

        // drawableLeft
        WidgetTestUtils.assertEquals(TestUtils.getBitmap(mActivity, R.drawable.start),
                ((BitmapDrawable) drawables[0]).getBitmap());
        // drawableTop
        WidgetTestUtils.assertEquals(TestUtils.getBitmap(mActivity, R.drawable.pass),
                ((BitmapDrawable) drawables[1]).getBitmap());
        // drawableRight
        WidgetTestUtils.assertEquals(TestUtils.getBitmap(mActivity, R.drawable.failed),
                ((BitmapDrawable) drawables[2]).getBitmap());
        // drawableBottom
        assertNull(drawables[3]);

        Drawable left = TestUtils.getDrawable(mActivity, R.drawable.blue);
        Drawable right = TestUtils.getDrawable(mActivity, R.drawable.yellow);
        Drawable top = TestUtils.getDrawable(mActivity, R.drawable.red);

        // using drawables directly
        mTextView.setCompoundDrawablesWithIntrinsicBounds(left, top, right, null);
        drawables = mTextView.getCompoundDrawables();

        // drawableLeft
        assertSame(left, drawables[0]);
        // drawableTop
        assertSame(top, drawables[1]);
        // drawableRight
        assertSame(right, drawables[2]);
        // drawableBottom
        assertNull(drawables[3]);

        // check compound padding
        assertEquals(mTextView.getPaddingLeft() + padding + left.getIntrinsicWidth(),
                mTextView.getCompoundPaddingLeft());
        assertEquals(mTextView.getPaddingTop() + padding + top.getIntrinsicHeight(),
                mTextView.getCompoundPaddingTop());
        assertEquals(mTextView.getPaddingRight() + padding + right.getIntrinsicWidth(),
                mTextView.getCompoundPaddingRight());
        assertEquals(mTextView.getPaddingBottom(), mTextView.getCompoundPaddingBottom());

        // set bounds to drawables and set them again.
        left.setBounds(0, 0, 1, 2);
        right.setBounds(0, 0, 3, 4);
        top.setBounds(0, 0, 5, 6);
        // usinf drawables
        mTextView.setCompoundDrawables(left, top, right, null);
        drawables = mTextView.getCompoundDrawables();

        // drawableLeft
        assertSame(left, drawables[0]);
        // drawableTop
        assertSame(top, drawables[1]);
        // drawableRight
        assertSame(right, drawables[2]);
        // drawableBottom
        assertNull(drawables[3]);

        // check compound padding
        assertEquals(mTextView.getPaddingLeft() + padding + left.getBounds().width(),
                mTextView.getCompoundPaddingLeft());
        assertEquals(mTextView.getPaddingTop() + padding + top.getBounds().height(),
                mTextView.getCompoundPaddingTop());
        assertEquals(mTextView.getPaddingRight() + padding + right.getBounds().width(),
                mTextView.getCompoundPaddingRight());
        assertEquals(mTextView.getPaddingBottom(), mTextView.getCompoundPaddingBottom());
    }

    @UiThreadTest
    @Test
    public void testGetCompoundDrawablesRelative() {
        // prepare textview
        mTextView = new TextView(mActivity);

        // prepare drawables
        final Drawable start = TestUtils.getDrawable(mActivity, R.drawable.blue);
        final Drawable end = TestUtils.getDrawable(mActivity, R.drawable.yellow);
        final Drawable top = TestUtils.getDrawable(mActivity, R.drawable.red);
        final Drawable bottom = TestUtils.getDrawable(mActivity, R.drawable.black);
        assertNotNull(start);
        assertNotNull(end);
        assertNotNull(top);
        assertNotNull(bottom);

        Drawable[] drawables = mTextView.getCompoundDrawablesRelative();
        assertNotNull(drawables);
        assertEquals(4, drawables.length);
        assertNull(drawables[0]);
        assertNull(drawables[1]);
        assertNull(drawables[2]);
        assertNull(drawables[3]);

        mTextView.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        mTextView.setCompoundDrawablesRelative(start, top, end, bottom);
        drawables = mTextView.getCompoundDrawablesRelative();

        assertNotNull(drawables);
        assertEquals(4, drawables.length);
        assertSame(start, drawables[0]);
        assertSame(top, drawables[1]);
        assertSame(end, drawables[2]);
        assertSame(bottom, drawables[3]);

        mTextView.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        mTextView.setCompoundDrawablesRelative(start, top, end, bottom);
        drawables = mTextView.getCompoundDrawablesRelative();

        assertNotNull(drawables);
        assertEquals(4, drawables.length);
        assertSame(start, drawables[0]);
        assertSame(top, drawables[1]);
        assertSame(end, drawables[2]);
        assertSame(bottom, drawables[3]);

        mTextView.setCompoundDrawablesRelative(null, null, null, null);
        drawables = mTextView.getCompoundDrawablesRelative();

        assertNotNull(drawables);
        assertEquals(4, drawables.length);
        assertNull(drawables[0]);
        assertNull(drawables[1]);
        assertNull(drawables[2]);
        assertNull(drawables[3]);
    }

    @Test
    public void testSingleLine() throws Throwable {
        mActivityRule.runOnUiThread(() -> mTextView = new TextView(mActivity));
        mInstrumentation.waitForIdleSync();

        setSpannableText(mTextView, "This is a really long sentence"
                + " which can not be placed in one line on the screen.");

        // Narrow layout assures that the text will get wrapped.
        final FrameLayout innerLayout = new FrameLayout(mActivity);
        innerLayout.setLayoutParams(new ViewGroup.LayoutParams(100, 100));
        innerLayout.addView(mTextView);

        final FrameLayout layout = new FrameLayout(mActivity);
        layout.addView(innerLayout);

        mActivityRule.runOnUiThread(() -> {
            mActivity.setContentView(layout);
            mTextView.setSingleLine(true);
        });
        mInstrumentation.waitForIdleSync();

        assertEquals(SingleLineTransformationMethod.getInstance(),
                mTextView.getTransformationMethod());

        int singleLineWidth = 0;
        int singleLineHeight = 0;

        if (mTextView.getLayout() != null) {
            singleLineWidth = mTextView.getLayout().getWidth();
            singleLineHeight = mTextView.getLayout().getHeight();
        }

        mActivityRule.runOnUiThread(() -> mTextView.setSingleLine(false));
        mInstrumentation.waitForIdleSync();
        assertEquals(null, mTextView.getTransformationMethod());

        if (mTextView.getLayout() != null) {
            assertTrue(mTextView.getLayout().getHeight() > singleLineHeight);
            assertTrue(mTextView.getLayout().getWidth() < singleLineWidth);
        }

        // same behaviours as setSingLine(true)
        mActivityRule.runOnUiThread(mTextView::setSingleLine);
        mInstrumentation.waitForIdleSync();
        assertEquals(SingleLineTransformationMethod.getInstance(),
                mTextView.getTransformationMethod());

        if (mTextView.getLayout() != null) {
            assertEquals(singleLineHeight, mTextView.getLayout().getHeight());
            assertEquals(singleLineWidth, mTextView.getLayout().getWidth());
        }
    }

    @UiThreadTest
    @Test
    public void testAccessMaxLines() {
        mTextView = findTextView(R.id.textview_text);
        mTextView.setWidth((int) (mTextView.getPaint().measureText(LONG_TEXT) / 4));
        mTextView.setText(LONG_TEXT);

        final int maxLines = 2;
        assertTrue(mTextView.getLineCount() > maxLines);

        mTextView.setMaxLines(maxLines);
        mTextView.requestLayout();

        assertEquals(2, mTextView.getMaxLines());
        assertEquals(-1, mTextView.getMaxHeight());
        assertTrue(mTextView.getHeight() <= maxLines * mTextView.getLineHeight());
    }

    @UiThreadTest
    @Test
    public void testHyphenationNotHappen_frequencyNone() {
        final int[] BREAK_STRATEGIES = {
            Layout.BREAK_STRATEGY_SIMPLE, Layout.BREAK_STRATEGY_HIGH_QUALITY,
            Layout.BREAK_STRATEGY_BALANCED };

        mTextView = findTextView(R.id.textview_text);

        for (int breakStrategy : BREAK_STRATEGIES) {
            for (int charWidth = 10; charWidth < 120; charWidth += 5) {
                // Change the text view's width to charWidth width.
                final String substring = LONG_TEXT.substring(0, charWidth);
                mTextView.setWidth((int) Math.ceil(mTextView.getPaint().measureText(substring)));

                mTextView.setText(LONG_TEXT);
                mTextView.setBreakStrategy(breakStrategy);

                mTextView.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);

                mTextView.requestLayout();
                mTextView.onPreDraw();  // For freezing the layout.
                Layout layout = mTextView.getLayout();

                final int lineCount = layout.getLineCount();
                for (int line = 0; line < lineCount; ++line) {
                    final int lineEnd = layout.getLineEnd(line);
                    // In any width, any break strategy, hyphenation should not happen if
                    // HYPHENATION_FREQUENCY_NONE is specified.
                    assertTrue(lineEnd == LONG_TEXT.length() ||
                            Character.isWhitespace(LONG_TEXT.charAt(lineEnd - 1)));
                }
            }
        }
    }

    @UiThreadTest
    @Test
    public void testHyphenationNotHappen_breakStrategySimple() {
        final int[] HYPHENATION_FREQUENCIES = {
            Layout.HYPHENATION_FREQUENCY_NORMAL, Layout.HYPHENATION_FREQUENCY_FULL,
            Layout.HYPHENATION_FREQUENCY_NONE };

        mTextView = findTextView(R.id.textview_text);

        for (int hyphenationFrequency: HYPHENATION_FREQUENCIES) {
            for (int charWidth = 10; charWidth < 120; charWidth += 5) {
                // Change the text view's width to charWidth width.
                final String substring = LONG_TEXT.substring(0, charWidth);
                mTextView.setWidth((int) Math.ceil(mTextView.getPaint().measureText(substring)));

                mTextView.setText(LONG_TEXT);
                mTextView.setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);

                mTextView.setHyphenationFrequency(hyphenationFrequency);

                mTextView.requestLayout();
                mTextView.onPreDraw();  // For freezing the layout.
                Layout layout = mTextView.getLayout();

                final int lineCount = layout.getLineCount();
                for (int line = 0; line < lineCount; ++line) {
                    final int lineEnd = layout.getLineEnd(line);
                    // In any width, any hyphenation frequency, hyphenation should not happen if
                    // BREAK_STRATEGY_SIMPLE is specified.
                    assertTrue(lineEnd == LONG_TEXT.length() ||
                            Character.isWhitespace(LONG_TEXT.charAt(lineEnd - 1)));
                }
            }
        }
    }

    @UiThreadTest
    @Test
    public void testSetMaxLinesException() {
        mTextView = new TextView(mActivity);
        mActivity.setContentView(mTextView);
        mTextView.setWidth(mTextView.getWidth() >> 3);
        mTextView.setMaxLines(-1);
    }

    @Test
    public void testAccessMinLines() throws Throwable {
        mTextView = findTextView(R.id.textview_text);
        setWidth(mTextView.getWidth() >> 3);
        int originalLines = mTextView.getLineCount();

        setMinLines(originalLines - 1);
        assertTrue((originalLines - 1) * mTextView.getLineHeight() <= mTextView.getHeight());
        assertEquals(originalLines - 1, mTextView.getMinLines());
        assertEquals(-1, mTextView.getMinHeight());

        setMinLines(originalLines + 1);
        assertTrue((originalLines + 1) * mTextView.getLineHeight() <= mTextView.getHeight());
        assertEquals(originalLines + 1, mTextView.getMinLines());
        assertEquals(-1, mTextView.getMinHeight());
    }

    @Test
    public void testSetLines() throws Throwable {
        mTextView = findTextView(R.id.textview_text);
        // make it multiple lines
        setWidth(mTextView.getWidth() >> 3);
        int originalLines = mTextView.getLineCount();

        setLines(originalLines - 1);
        assertTrue((originalLines - 1) * mTextView.getLineHeight() <= mTextView.getHeight());

        setLines(originalLines + 1);
        assertTrue((originalLines + 1) * mTextView.getLineHeight() <= mTextView.getHeight());
    }

    @UiThreadTest
    @Test
    public void testSetLinesException() {
        mTextView = new TextView(mActivity);
        mActivity.setContentView(mTextView);
        mTextView.setWidth(mTextView.getWidth() >> 3);
        mTextView.setLines(-1);
    }

    @UiThreadTest
    @Test
    public void testGetExtendedPaddingTop() {
        mTextView = findTextView(R.id.textview_text);
        // Initialized value
        assertEquals(0, mTextView.getExtendedPaddingTop());

        // After Set a Drawable
        final Drawable top = TestUtils.getDrawable(mActivity, R.drawable.red);
        top.setBounds(0, 0, 100, 10);
        mTextView.setCompoundDrawables(null, top, null, null);
        assertEquals(mTextView.getCompoundPaddingTop(), mTextView.getExtendedPaddingTop());

        // Change line count
        mTextView.setLines(mTextView.getLineCount() - 1);
        mTextView.setGravity(Gravity.BOTTOM);

        assertTrue(mTextView.getExtendedPaddingTop() > 0);
    }

    @UiThreadTest
    @Test
    public void testGetExtendedPaddingBottom() {
        mTextView = findTextView(R.id.textview_text);
        // Initialized value
        assertEquals(0, mTextView.getExtendedPaddingBottom());

        // After Set a Drawable
        final Drawable bottom = TestUtils.getDrawable(mActivity, R.drawable.red);
        bottom.setBounds(0, 0, 100, 10);
        mTextView.setCompoundDrawables(null, null, null, bottom);
        assertEquals(mTextView.getCompoundPaddingBottom(), mTextView.getExtendedPaddingBottom());

        // Change line count
        mTextView.setLines(mTextView.getLineCount() - 1);
        mTextView.setGravity(Gravity.CENTER_VERTICAL);

        assertTrue(mTextView.getExtendedPaddingBottom() > 0);
    }

    @Test
    public void testGetTotalPaddingTop() throws Throwable {
        mTextView = findTextView(R.id.textview_text);
        // Initialized value
        assertEquals(0, mTextView.getTotalPaddingTop());

        // After Set a Drawable
        final Drawable top = TestUtils.getDrawable(mActivity, R.drawable.red);
        top.setBounds(0, 0, 100, 10);
        mActivityRule.runOnUiThread(() -> {
            mTextView.setCompoundDrawables(null, top, null, null);
            mTextView.setLines(mTextView.getLineCount() - 1);
            mTextView.setGravity(Gravity.BOTTOM);
        });
        mInstrumentation.waitForIdleSync();
        assertEquals(mTextView.getExtendedPaddingTop(), mTextView.getTotalPaddingTop());

        // Change line count
        setLines(mTextView.getLineCount() + 1);
        int expected = mTextView.getHeight()
                - mTextView.getExtendedPaddingBottom()
                - mTextView.getLayout().getLineTop(mTextView.getLineCount());
        assertEquals(expected, mTextView.getTotalPaddingTop());
    }

    @Test
    public void testGetTotalPaddingBottom() throws Throwable {
        mTextView = findTextView(R.id.textview_text);
        // Initialized value
        assertEquals(0, mTextView.getTotalPaddingBottom());

        // After Set a Drawable
        final Drawable bottom = TestUtils.getDrawable(mActivity, R.drawable.red);
        bottom.setBounds(0, 0, 100, 10);
        mActivityRule.runOnUiThread(() -> {
            mTextView.setCompoundDrawables(null, null, null, bottom);
            mTextView.setLines(mTextView.getLineCount() - 1);
            mTextView.setGravity(Gravity.CENTER_VERTICAL);
        });
        mInstrumentation.waitForIdleSync();
        assertEquals(mTextView.getExtendedPaddingBottom(), mTextView.getTotalPaddingBottom());

        // Change line count
        setLines(mTextView.getLineCount() + 1);
        int expected = ((mTextView.getHeight()
                - mTextView.getExtendedPaddingBottom()
                - mTextView.getExtendedPaddingTop()
                - mTextView.getLayout().getLineBottom(mTextView.getLineCount())) >> 1)
                + mTextView.getExtendedPaddingBottom();
        assertEquals(expected, mTextView.getTotalPaddingBottom());
    }

    @UiThreadTest
    @Test
    public void testGetTotalPaddingLeft() {
        mTextView = findTextView(R.id.textview_text);
        // Initialized value
        assertEquals(0, mTextView.getTotalPaddingLeft());

        // After Set a Drawable
        Drawable left = TestUtils.getDrawable(mActivity, R.drawable.red);
        left.setBounds(0, 0, 10, 100);
        mTextView.setCompoundDrawables(left, null, null, null);
        mTextView.setGravity(Gravity.RIGHT);
        assertEquals(mTextView.getCompoundPaddingLeft(), mTextView.getTotalPaddingLeft());

        // Change width
        mTextView.setWidth(Integer.MAX_VALUE);
        assertEquals(mTextView.getCompoundPaddingLeft(), mTextView.getTotalPaddingLeft());
    }

    @UiThreadTest
    @Test
    public void testGetTotalPaddingRight() {
        mTextView = findTextView(R.id.textview_text);
        // Initialized value
        assertEquals(0, mTextView.getTotalPaddingRight());

        // After Set a Drawable
        Drawable right = TestUtils.getDrawable(mActivity, R.drawable.red);
        right.setBounds(0, 0, 10, 100);
        mTextView.setCompoundDrawables(null, null, right, null);
        mTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        assertEquals(mTextView.getCompoundPaddingRight(), mTextView.getTotalPaddingRight());

        // Change width
        mTextView.setWidth(Integer.MAX_VALUE);
        assertEquals(mTextView.getCompoundPaddingRight(), mTextView.getTotalPaddingRight());
    }

    @UiThreadTest
    @Test
    public void testGetUrls() {
        mTextView = new TextView(mActivity);

        URLSpan[] spans = mTextView.getUrls();
        assertEquals(0, spans.length);

        String url = "http://www.google.com";
        String email = "name@gmail.com";
        String string = url + " mailto:" + email;
        SpannableString spannable = new SpannableString(string);
        spannable.setSpan(new URLSpan(url), 0, url.length(), 0);
        mTextView.setText(spannable, BufferType.SPANNABLE);
        spans = mTextView.getUrls();
        assertEquals(1, spans.length);
        assertEquals(url, spans[0].getURL());

        spannable.setSpan(new URLSpan(email), 0, email.length(), 0);
        mTextView.setText(spannable, BufferType.SPANNABLE);

        spans = mTextView.getUrls();
        assertEquals(2, spans.length);
        assertEquals(url, spans[0].getURL());
        assertEquals(email, spans[1].getURL());

        // test the situation that param what is not a URLSpan
        spannable.setSpan(new Object(), 0, 9, 0);
        mTextView.setText(spannable, BufferType.SPANNABLE);
        spans = mTextView.getUrls();
        assertEquals(2, spans.length);
    }

    @UiThreadTest
    @Test
    public void testSetPadding() {
        mTextView = new TextView(mActivity);

        mTextView.setPadding(0, 1, 2, 4);
        assertEquals(0, mTextView.getPaddingLeft());
        assertEquals(1, mTextView.getPaddingTop());
        assertEquals(2, mTextView.getPaddingRight());
        assertEquals(4, mTextView.getPaddingBottom());

        mTextView.setPadding(10, 20, 30, 40);
        assertEquals(10, mTextView.getPaddingLeft());
        assertEquals(20, mTextView.getPaddingTop());
        assertEquals(30, mTextView.getPaddingRight());
        assertEquals(40, mTextView.getPaddingBottom());
    }

    @UiThreadTest
    @Test
    public void testDeprecatedSetTextAppearance() {
        mTextView = new TextView(mActivity);

        mTextView.setTextAppearance(mActivity, R.style.TextAppearance_All);
        assertEquals(mActivity.getResources().getColor(R.drawable.black),
                mTextView.getCurrentTextColor());
        assertEquals(20f, mTextView.getTextSize(), 0.01f);
        assertEquals(Typeface.BOLD, mTextView.getTypeface().getStyle());
        assertEquals(mActivity.getResources().getColor(R.drawable.red),
                mTextView.getCurrentHintTextColor());
        assertEquals(mActivity.getResources().getColor(R.drawable.blue),
                mTextView.getLinkTextColors().getDefaultColor());

        mTextView.setTextAppearance(mActivity, R.style.TextAppearance_Colors);
        assertEquals(mActivity.getResources().getColor(R.drawable.black),
                mTextView.getCurrentTextColor());
        assertEquals(mActivity.getResources().getColor(R.drawable.blue),
                mTextView.getCurrentHintTextColor());
        assertEquals(mActivity.getResources().getColor(R.drawable.yellow),
                mTextView.getLinkTextColors().getDefaultColor());

        mTextView.setTextAppearance(mActivity, R.style.TextAppearance_NotColors);
        assertEquals(17f, mTextView.getTextSize(), 0.01f);
        assertEquals(Typeface.NORMAL, mTextView.getTypeface().getStyle());

        mTextView.setTextAppearance(mActivity, R.style.TextAppearance_Style);
        assertEquals(null, mTextView.getTypeface());
    }

    @UiThreadTest
    @Test
    public void testSetTextAppearance() {
        mTextView = new TextView(mActivity);

        mTextView.setTextAppearance(R.style.TextAppearance_All);
        assertEquals(mActivity.getResources().getColor(R.drawable.black),
                mTextView.getCurrentTextColor());
        assertEquals(20f, mTextView.getTextSize(), 0.01f);
        assertEquals(Typeface.BOLD, mTextView.getTypeface().getStyle());
        assertEquals(mActivity.getResources().getColor(R.drawable.red),
                mTextView.getCurrentHintTextColor());
        assertEquals(mActivity.getResources().getColor(R.drawable.blue),
                mTextView.getLinkTextColors().getDefaultColor());
        assertEquals(mActivity.getResources().getColor(R.drawable.yellow),
                mTextView.getHighlightColor());

        mTextView.setTextAppearance(R.style.TextAppearance_Colors);
        assertEquals(mActivity.getResources().getColor(R.drawable.black),
                mTextView.getCurrentTextColor());
        assertEquals(mActivity.getResources().getColor(R.drawable.blue),
                mTextView.getCurrentHintTextColor());
        assertEquals(mActivity.getResources().getColor(R.drawable.yellow),
                mTextView.getLinkTextColors().getDefaultColor());
        assertEquals(mActivity.getResources().getColor(R.drawable.red),
                mTextView.getHighlightColor());

        mTextView.setTextAppearance(R.style.TextAppearance_NotColors);
        assertEquals(17f, mTextView.getTextSize(), 0.01f);
        assertEquals(Typeface.NORMAL, mTextView.getTypeface().getStyle());

        mTextView.setTextAppearance(R.style.TextAppearance_Style);
        assertEquals(null, mTextView.getTypeface());
    }

    @UiThreadTest
    @Test
    public void testAccessCompoundDrawableTint() {
        mTextView = new TextView(mActivity);

        ColorStateList colors = ColorStateList.valueOf(Color.RED);
        mTextView.setCompoundDrawableTintList(colors);
        mTextView.setCompoundDrawableTintMode(PorterDuff.Mode.XOR);
        assertSame(colors, mTextView.getCompoundDrawableTintList());
        assertEquals(PorterDuff.Mode.XOR, mTextView.getCompoundDrawableTintMode());

        // Ensure the tint is preserved across drawable changes.
        mTextView.setCompoundDrawablesRelative(null, null, null, null);
        assertSame(colors, mTextView.getCompoundDrawableTintList());
        assertEquals(PorterDuff.Mode.XOR, mTextView.getCompoundDrawableTintMode());

        mTextView.setCompoundDrawables(null, null, null, null);
        assertSame(colors, mTextView.getCompoundDrawableTintList());
        assertEquals(PorterDuff.Mode.XOR, mTextView.getCompoundDrawableTintMode());

        ColorDrawable dr1 = new ColorDrawable(Color.RED);
        ColorDrawable dr2 = new ColorDrawable(Color.GREEN);
        ColorDrawable dr3 = new ColorDrawable(Color.BLUE);
        ColorDrawable dr4 = new ColorDrawable(Color.YELLOW);
        mTextView.setCompoundDrawables(dr1, dr2, dr3, dr4);
        assertSame(colors, mTextView.getCompoundDrawableTintList());
        assertEquals(PorterDuff.Mode.XOR, mTextView.getCompoundDrawableTintMode());
    }

    @Test
    public void testSetHorizontallyScrolling() throws Throwable {
        // make the text view has more than one line
        mTextView = findTextView(R.id.textview_text);
        setWidth(mTextView.getWidth() >> 1);
        assertTrue(mTextView.getLineCount() > 1);

        setHorizontallyScrolling(true);
        assertEquals(1, mTextView.getLineCount());

        setHorizontallyScrolling(false);
        assertTrue(mTextView.getLineCount() > 1);
    }

    @Test
    public void testComputeHorizontalScrollRange() throws Throwable {
        mActivityRule.runOnUiThread(() -> mTextView = new MockTextView(mActivity));
        mInstrumentation.waitForIdleSync();
        // test when layout is null
        assertNull(mTextView.getLayout());
        assertEquals(mTextView.getWidth(),
                ((MockTextView) mTextView).computeHorizontalScrollRange());

        mActivityRule.runOnUiThread(() -> ((MockTextView) mTextView).setFrame(0, 0, 40, 50));
        mInstrumentation.waitForIdleSync();
        assertEquals(mTextView.getWidth(),
                ((MockTextView) mTextView).computeHorizontalScrollRange());

        // set the layout
        layout(mTextView);
        assertEquals(mTextView.getLayout().getWidth(),
                ((MockTextView) mTextView).computeHorizontalScrollRange());
    }

    @Test
    public void testComputeVerticalScrollRange() throws Throwable {
        mActivityRule.runOnUiThread(() -> mTextView = new MockTextView(mActivity));
        mInstrumentation.waitForIdleSync();

        // test when layout is null
        assertNull(mTextView.getLayout());
        assertEquals(0, ((MockTextView) mTextView).computeVerticalScrollRange());

        mActivityRule.runOnUiThread(() -> ((MockTextView) mTextView).setFrame(0, 0, 40, 50));
        mInstrumentation.waitForIdleSync();
        assertEquals(mTextView.getHeight(), ((MockTextView) mTextView).computeVerticalScrollRange());

        //set the layout
        layout(mTextView);
        assertEquals(mTextView.getLayout().getHeight(),
                ((MockTextView) mTextView).computeVerticalScrollRange());
    }

    @Test
    public void testDrawableStateChanged() throws Throwable {
        mActivityRule.runOnUiThread(() -> mTextView = spy(new MockTextView(mActivity)));
        mInstrumentation.waitForIdleSync();
        reset(mTextView);
        mTextView.refreshDrawableState();
        ((MockTextView) verify(mTextView, times(1))).drawableStateChanged();
    }

    @UiThreadTest
    @Test
    public void testGetDefaultEditable() {
        mTextView = new MockTextView(mActivity);

        //the TextView#getDefaultEditable() does nothing, and always return false.
        assertFalse(((MockTextView) mTextView).getDefaultEditable());
    }

    @UiThreadTest
    @Test
    public void testGetDefaultMovementMethod() {
        MockTextView textView = new MockTextView(mActivity);

        //the TextView#getDefaultMovementMethod() does nothing, and always return null.
        assertNull(textView.getDefaultMovementMethod());
    }

    @UiThreadTest
    @Test
    public void testSetFrame() {
        MockTextView textView = new MockTextView(mActivity);

        //Assign a new size to this view
        assertTrue(textView.setFrame(0, 0, 320, 480));
        assertEquals(0, textView.getLeft());
        assertEquals(0, textView.getTop());
        assertEquals(320, textView.getRight());
        assertEquals(480, textView.getBottom());

        //Assign a same size to this view
        assertFalse(textView.setFrame(0, 0, 320, 480));

        //negative input
        assertTrue(textView.setFrame(-1, -1, -1, -1));
        assertEquals(-1, textView.getLeft());
        assertEquals(-1, textView.getTop());
        assertEquals(-1, textView.getRight());
        assertEquals(-1, textView.getBottom());
    }

    @Test
    public void testMarquee() throws Throwable {
        // Both are pointing to the same object. This works around current limitation in CTS
        // coverage report tool for properly reporting coverage of base class method calls.
        mActivityRule.runOnUiThread(() -> {
            mSecondTextView = new MockTextView(mActivity);

            mTextView = mSecondTextView;
            mTextView.setText(LONG_TEXT);
            mTextView.setSingleLine();
            mTextView.setEllipsize(TruncateAt.MARQUEE);
            mTextView.setLayoutParams(new LayoutParams(100, 100));
        });
        mInstrumentation.waitForIdleSync();

        final FrameLayout layout = new FrameLayout(mActivity);
        layout.addView(mTextView);

        // make the fading to be shown
        mTextView.setHorizontalFadingEdgeEnabled(true);

        mActivityRule.runOnUiThread(() -> mActivity.setContentView(layout));
        mInstrumentation.waitForIdleSync();

        TestSelectedRunnable runnable = new TestSelectedRunnable(mTextView) {
            public void run() {
                mTextView.setMarqueeRepeatLimit(-1);
                // force the marquee to start
                saveIsSelected1();
                mTextView.setSelected(true);
                saveIsSelected2();
            }
        };
        mActivityRule.runOnUiThread(runnable);

        // wait for the marquee to run
        // fading is shown on both sides if the marquee runs for a while
        PollingCheck.waitFor(TIMEOUT, () ->
                ((MockTextView) mSecondTextView).getLeftFadingEdgeStrength() > 0.0f
                        && ((MockTextView) mSecondTextView).getRightFadingEdgeStrength() > 0.0f);

        // wait for left marquee to fully apply
        PollingCheck.waitFor(TIMEOUT, () ->
                ((MockTextView) mSecondTextView).getLeftFadingEdgeStrength() > 0.99f);

        assertFalse(runnable.getIsSelected1());
        assertTrue(runnable.getIsSelected2());
        assertEquals(-1, mTextView.getMarqueeRepeatLimit());

        runnable = new TestSelectedRunnable(mTextView) {
            public void run() {
                mTextView.setMarqueeRepeatLimit(0);
                // force the marquee to stop
                saveIsSelected1();
                mTextView.setSelected(false);
                saveIsSelected2();
                mTextView.setGravity(Gravity.LEFT);
            }
        };
        // force the marquee to stop
        mActivityRule.runOnUiThread(runnable);
        mInstrumentation.waitForIdleSync();
        assertTrue(runnable.getIsSelected1());
        assertFalse(runnable.getIsSelected2());
        assertEquals(0.0f, ((MockTextView) mSecondTextView).getLeftFadingEdgeStrength(), 0.01f);
        assertTrue(((MockTextView) mSecondTextView).getRightFadingEdgeStrength() > 0.0f);
        assertEquals(0, mTextView.getMarqueeRepeatLimit());

        mActivityRule.runOnUiThread(() -> mTextView.setGravity(Gravity.RIGHT));
        mInstrumentation.waitForIdleSync();
        assertTrue(((MockTextView) mSecondTextView).getLeftFadingEdgeStrength() > 0.0f);
        assertEquals(0.0f, ((MockTextView) mSecondTextView).getRightFadingEdgeStrength(), 0.01f);

        mActivityRule.runOnUiThread(() -> mTextView.setGravity(Gravity.CENTER_HORIZONTAL));
        mInstrumentation.waitForIdleSync();
        // there is no left fading (Is it correct?)
        assertEquals(0.0f, ((MockTextView) mSecondTextView).getLeftFadingEdgeStrength(), 0.01f);
        assertTrue(((MockTextView) mSecondTextView).getRightFadingEdgeStrength() > 0.0f);
    }

    @UiThreadTest
    @Test
    public void testGetMarqueeRepeatLimit() {
        final TextView textView = new TextView(mActivity);

        textView.setMarqueeRepeatLimit(10);
        assertEquals(10, textView.getMarqueeRepeatLimit());
    }

    @UiThreadTest
    @Test
    public void testAccessInputExtras() throws XmlPullParserException, IOException {
        mTextView = new TextView(mActivity);
        mTextView.setText(null, BufferType.EDITABLE);
        mTextView.setInputType(InputType.TYPE_CLASS_TEXT);

        // do not create the extras
        assertNull(mTextView.getInputExtras(false));

        // create if it does not exist
        Bundle inputExtras = mTextView.getInputExtras(true);
        assertNotNull(inputExtras);
        assertTrue(inputExtras.isEmpty());

        // it is created already
        assertNotNull(mTextView.getInputExtras(false));

        try {
            mTextView.setInputExtras(R.xml.input_extras);
            fail("Should throw NullPointerException!");
        } catch (NullPointerException e) {
        }
    }

    @UiThreadTest
    @Test
    public void testAccessContentType() {
        mTextView = new TextView(mActivity);
        mTextView.setText(null, BufferType.EDITABLE);
        mTextView.setKeyListener(null);
        mTextView.setTransformationMethod(null);

        mTextView.setInputType(InputType.TYPE_CLASS_DATETIME
                | InputType.TYPE_DATETIME_VARIATION_NORMAL);
        assertEquals(InputType.TYPE_CLASS_DATETIME
                | InputType.TYPE_DATETIME_VARIATION_NORMAL, mTextView.getInputType());
        assertTrue(mTextView.getKeyListener() instanceof DateTimeKeyListener);

        mTextView.setInputType(InputType.TYPE_CLASS_DATETIME
                | InputType.TYPE_DATETIME_VARIATION_DATE);
        assertEquals(InputType.TYPE_CLASS_DATETIME
                | InputType.TYPE_DATETIME_VARIATION_DATE, mTextView.getInputType());
        assertTrue(mTextView.getKeyListener() instanceof DateKeyListener);

        mTextView.setInputType(InputType.TYPE_CLASS_DATETIME
                | InputType.TYPE_DATETIME_VARIATION_TIME);
        assertEquals(InputType.TYPE_CLASS_DATETIME
                | InputType.TYPE_DATETIME_VARIATION_TIME, mTextView.getInputType());
        assertTrue(mTextView.getKeyListener() instanceof TimeKeyListener);

        mTextView.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        assertEquals(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED, mTextView.getInputType());
        assertSame(mTextView.getKeyListener(),
                DigitsKeyListener.getInstance(null, true, true));

        mTextView.setInputType(InputType.TYPE_CLASS_PHONE);
        assertEquals(InputType.TYPE_CLASS_PHONE, mTextView.getInputType());
        assertTrue(mTextView.getKeyListener() instanceof DialerKeyListener);

        mTextView.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        assertEquals(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT, mTextView.getInputType());
        assertSame(mTextView.getKeyListener(), TextKeyListener.getInstance(true, Capitalize.NONE));

        mTextView.setSingleLine();
        assertTrue(mTextView.getTransformationMethod() instanceof SingleLineTransformationMethod);
        mTextView.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        assertEquals(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, mTextView.getInputType());
        assertSame(mTextView.getKeyListener(),
                TextKeyListener.getInstance(false, Capitalize.CHARACTERS));
        assertNull(mTextView.getTransformationMethod());

        mTextView.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        assertEquals(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_WORDS, mTextView.getInputType());
        assertSame(mTextView.getKeyListener(),
                TextKeyListener.getInstance(false, Capitalize.WORDS));
        assertTrue(mTextView.getTransformationMethod() instanceof SingleLineTransformationMethod);

        mTextView.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        assertEquals(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, mTextView.getInputType());
        assertSame(mTextView.getKeyListener(),
                TextKeyListener.getInstance(false, Capitalize.SENTENCES));

        mTextView.setInputType(InputType.TYPE_NULL);
        assertEquals(InputType.TYPE_NULL, mTextView.getInputType());
        assertTrue(mTextView.getKeyListener() instanceof TextKeyListener);
    }

    @UiThreadTest
    @Test
    public void testAccessRawContentType() {
        mTextView = new TextView(mActivity);
        mTextView.setText(null, BufferType.EDITABLE);
        mTextView.setKeyListener(null);
        mTextView.setTransformationMethod(null);

        mTextView.setRawInputType(InputType.TYPE_CLASS_DATETIME
                | InputType.TYPE_DATETIME_VARIATION_NORMAL);
        assertEquals(InputType.TYPE_CLASS_DATETIME
                | InputType.TYPE_DATETIME_VARIATION_NORMAL, mTextView.getInputType());
        assertNull(mTextView.getTransformationMethod());
        assertNull(mTextView.getKeyListener());

        mTextView.setRawInputType(InputType.TYPE_CLASS_DATETIME
                | InputType.TYPE_DATETIME_VARIATION_DATE);
        assertEquals(InputType.TYPE_CLASS_DATETIME
                | InputType.TYPE_DATETIME_VARIATION_DATE, mTextView.getInputType());
        assertNull(mTextView.getTransformationMethod());
        assertNull(mTextView.getKeyListener());

        mTextView.setRawInputType(InputType.TYPE_CLASS_DATETIME
                | InputType.TYPE_DATETIME_VARIATION_TIME);
        assertEquals(InputType.TYPE_CLASS_DATETIME
                | InputType.TYPE_DATETIME_VARIATION_TIME, mTextView.getInputType());
        assertNull(mTextView.getTransformationMethod());
        assertNull(mTextView.getKeyListener());

        mTextView.setRawInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        assertEquals(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED, mTextView.getInputType());
        assertNull(mTextView.getTransformationMethod());
        assertNull(mTextView.getKeyListener());

        mTextView.setRawInputType(InputType.TYPE_CLASS_PHONE);
        assertEquals(InputType.TYPE_CLASS_PHONE, mTextView.getInputType());
        assertNull(mTextView.getTransformationMethod());
        assertNull(mTextView.getKeyListener());

        mTextView.setRawInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        assertEquals(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT, mTextView.getInputType());
        assertNull(mTextView.getTransformationMethod());
        assertNull(mTextView.getKeyListener());

        mTextView.setSingleLine();
        assertTrue(mTextView.getTransformationMethod() instanceof SingleLineTransformationMethod);
        mTextView.setRawInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        assertEquals(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, mTextView.getInputType());
        assertTrue(mTextView.getTransformationMethod() instanceof SingleLineTransformationMethod);
        assertNull(mTextView.getKeyListener());

        mTextView.setRawInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        assertEquals(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_WORDS, mTextView.getInputType());
        assertTrue(mTextView.getTransformationMethod() instanceof SingleLineTransformationMethod);
        assertNull(mTextView.getKeyListener());

        mTextView.setRawInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        assertEquals(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, mTextView.getInputType());
        assertTrue(mTextView.getTransformationMethod() instanceof SingleLineTransformationMethod);
        assertNull(mTextView.getKeyListener());

        mTextView.setRawInputType(InputType.TYPE_NULL);
        assertTrue(mTextView.getTransformationMethod() instanceof SingleLineTransformationMethod);
        assertNull(mTextView.getKeyListener());
    }

    @UiThreadTest
    @Test
    public void testVerifyDrawable() {
        mTextView = new MockTextView(mActivity);

        Drawable d = TestUtils.getDrawable(mActivity, R.drawable.pass);
        assertFalse(((MockTextView ) mTextView).verifyDrawable(d));

        mTextView.setCompoundDrawables(null, d, null, null);
        assertTrue(((MockTextView ) mTextView).verifyDrawable(d));
    }

    @Test
    public void testAccessPrivateImeOptions() {
        mTextView = findTextView(R.id.textview_text);
        assertNull(mTextView.getPrivateImeOptions());

        mTextView.setPrivateImeOptions("com.example.myapp.SpecialMode=3");
        assertEquals("com.example.myapp.SpecialMode=3", mTextView.getPrivateImeOptions());

        mTextView.setPrivateImeOptions(null);
        assertNull(mTextView.getPrivateImeOptions());
    }

    @Test
    public void testSetOnEditorActionListener() {
        mTextView = findTextView(R.id.textview_text);

        final TextView.OnEditorActionListener mockOnEditorActionListener =
                mock(TextView.OnEditorActionListener.class);
        verifyZeroInteractions(mockOnEditorActionListener);

        mTextView.setOnEditorActionListener(mockOnEditorActionListener);
        verifyZeroInteractions(mockOnEditorActionListener);

        mTextView.onEditorAction(EditorInfo.IME_ACTION_DONE);
        verify(mockOnEditorActionListener, times(1)).onEditorAction(mTextView,
                EditorInfo.IME_ACTION_DONE, null);
    }

    @Test
    public void testAccessImeOptions() {
        mTextView = findTextView(R.id.textview_text);
        assertEquals(EditorInfo.IME_NULL, mTextView.getImeOptions());

        mTextView.setImeOptions(EditorInfo.IME_ACTION_GO);
        assertEquals(EditorInfo.IME_ACTION_GO, mTextView.getImeOptions());

        mTextView.setImeOptions(EditorInfo.IME_ACTION_DONE);
        assertEquals(EditorInfo.IME_ACTION_DONE, mTextView.getImeOptions());

        mTextView.setImeOptions(EditorInfo.IME_NULL);
        assertEquals(EditorInfo.IME_NULL, mTextView.getImeOptions());
    }

    @Test
    public void testAccessImeActionLabel() {
        mTextView = findTextView(R.id.textview_text);
        assertNull(mTextView.getImeActionLabel());
        assertEquals(0, mTextView.getImeActionId());

        mTextView.setImeActionLabel("pinyin", 1);
        assertEquals("pinyin", mTextView.getImeActionLabel().toString());
        assertEquals(1, mTextView.getImeActionId());
    }

    @UiThreadTest
    @Test
    public void testAccessImeHintLocales() {
        final TextView textView = new TextView(mActivity);
        textView.setText("", BufferType.EDITABLE);
        textView.setKeyListener(null);
        textView.setRawInputType(InputType.TYPE_CLASS_TEXT);
        assertNull(textView.getImeHintLocales());
        {
            final EditorInfo editorInfo = new EditorInfo();
            textView.onCreateInputConnection(editorInfo);
            assertNull(editorInfo.hintLocales);
        }

        final LocaleList localeList = LocaleList.forLanguageTags("en-PH,en-US");
        textView.setImeHintLocales(localeList);
        assertEquals(localeList, textView.getImeHintLocales());
        {
            final EditorInfo editorInfo = new EditorInfo();
            textView.onCreateInputConnection(editorInfo);
            assertEquals(localeList, editorInfo.hintLocales);
        }
    }

    @UiThreadTest
    @Test
    public void testSetImeHintLocalesChangesInputType() {
        final TextView textView = new TextView(mActivity);
        textView.setText("", BufferType.EDITABLE);

        textView.setInputType(InputType.TYPE_CLASS_NUMBER);
        assertEquals(InputType.TYPE_CLASS_NUMBER, textView.getInputType());

        final LocaleList localeList = LocaleList.forLanguageTags("fa-IR");
        textView.setImeHintLocales(localeList);
        final int textType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL;
        // Setting IME hint locales to Persian must change the input type to a full text IME,
        // since the typical number input IME may not have localized digits.
        assertEquals(textType, textView.getInputType());

        // Changing the input type to datetime should keep the full text IME, since the IME hint
        // is still set to Persian, which needs advanced input.
        final int dateType = InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE;
        textView.setInputType(dateType);
        assertEquals(textType, textView.getInputType());

        // Changing the input type to number password should keep the full text IME, since the IME
        // hint is still set to Persian, which needs advanced input. But it also needs to set the
        // text password flag.
        final int numberPasswordType = InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_VARIATION_PASSWORD;
        final int textPasswordType = InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD;
        textView.setInputType(numberPasswordType);
        assertEquals(textPasswordType, textView.getInputType());

        // Setting the IME hint locales to null should reset the type to number password, since we
        // no longer need internationalized input.
        textView.setImeHintLocales(null);
        assertEquals(numberPasswordType, textView.getInputType());
    }

    @UiThreadTest
    @Test
    public void testSetImeHintLocalesDoesntLoseInputType() {
        final TextView textView = new TextView(mActivity);
        textView.setText("", BufferType.EDITABLE);
        final int inputType = InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        textView.setInputType(inputType);
        textView.setImeHintLocales(new LocaleList(Locale.US));
        assertEquals(inputType, textView.getInputType());
    }

    @UiThreadTest
    @Test
    public void testSetExtractedText() {
        mTextView = findTextView(R.id.textview_text);
        assertEquals(mActivity.getResources().getString(R.string.text_view_hello),
                mTextView.getText().toString());

        ExtractedText et = new ExtractedText();

        // Update text and selection.
        et.text = "test";
        et.selectionStart = 0;
        et.selectionEnd = 2;

        mTextView.setExtractedText(et);
        assertEquals("test", mTextView.getText().toString());
        assertEquals(0, mTextView.getSelectionStart());
        assertEquals(2, mTextView.getSelectionEnd());

        // Use partialStartOffset and partialEndOffset
        et.partialStartOffset = 2;
        et.partialEndOffset = 3;
        et.text = "x";
        et.selectionStart = 2;
        et.selectionEnd = 3;

        mTextView.setExtractedText(et);
        assertEquals("text", mTextView.getText().toString());
        assertEquals(2, mTextView.getSelectionStart());
        assertEquals(3, mTextView.getSelectionEnd());

        // Update text with spans.
        final SpannableString ss = new SpannableString("ex");
        ss.setSpan(new UnderlineSpan(), 0, 2, 0);
        ss.setSpan(new URLSpan("ctstest://TextView/test"), 1, 2, 0);

        et.text = ss;
        et.partialStartOffset = 1;
        et.partialEndOffset = 3;
        mTextView.setExtractedText(et);

        assertEquals("text", mTextView.getText().toString());
        final Editable editable = mTextView.getEditableText();
        final UnderlineSpan[] underlineSpans = mTextView.getEditableText().getSpans(
                0, editable.length(), UnderlineSpan.class);
        assertEquals(1, underlineSpans.length);
        assertEquals(1, editable.getSpanStart(underlineSpans[0]));
        assertEquals(3, editable.getSpanEnd(underlineSpans[0]));

        final URLSpan[] urlSpans = mTextView.getEditableText().getSpans(
                0, editable.length(), URLSpan.class);
        assertEquals(1, urlSpans.length);
        assertEquals(2, editable.getSpanStart(urlSpans[0]));
        assertEquals(3, editable.getSpanEnd(urlSpans[0]));
        assertEquals("ctstest://TextView/test", urlSpans[0].getURL());
    }

    @Test
    public void testMoveCursorToVisibleOffset() throws Throwable {
        mTextView = findTextView(R.id.textview_text);

        // not a spannable text
        mActivityRule.runOnUiThread(() -> assertFalse(mTextView.moveCursorToVisibleOffset()));
        mInstrumentation.waitForIdleSync();

        // a selection range
        final String spannableText = "text";
        mActivityRule.runOnUiThread(() ->  mTextView = new TextView(mActivity));
        mInstrumentation.waitForIdleSync();

        mActivityRule.runOnUiThread(
                () -> mTextView.setText(spannableText, BufferType.SPANNABLE));
        mInstrumentation.waitForIdleSync();
        Selection.setSelection((Spannable) mTextView.getText(), 0, spannableText.length());

        assertEquals(0, mTextView.getSelectionStart());
        assertEquals(spannableText.length(), mTextView.getSelectionEnd());
        mActivityRule.runOnUiThread(() -> assertFalse(mTextView.moveCursorToVisibleOffset()));
        mInstrumentation.waitForIdleSync();

        // a spannable without range
        mActivityRule.runOnUiThread(() -> {
            mTextView = findTextView(R.id.textview_text);
            mTextView.setText(spannableText, BufferType.SPANNABLE);
        });
        mInstrumentation.waitForIdleSync();

        mActivityRule.runOnUiThread(() -> assertTrue(mTextView.moveCursorToVisibleOffset()));
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testIsInputMethodTarget() throws Throwable {
        mTextView = findTextView(R.id.textview_text);
        assertFalse(mTextView.isInputMethodTarget());

        assertFalse(mTextView.isFocused());
        mActivityRule.runOnUiThread(() -> {
            mTextView.setFocusable(true);
            mTextView.requestFocus();
         });
        mInstrumentation.waitForIdleSync();
        assertTrue(mTextView.isFocused());

        PollingCheck.waitFor(mTextView::isInputMethodTarget);
    }

    @UiThreadTest
    @Test
    public void testBeginEndBatchEditAreNotCalledForNonEditableText() {
        final TextView mockTextView = spy(new TextView(mActivity));

        // TextView should not call onBeginBatchEdit or onEndBatchEdit during initialization
        verify(mockTextView, never()).onBeginBatchEdit();
        verify(mockTextView, never()).onEndBatchEdit();


        mockTextView.beginBatchEdit();
        // Since TextView doesn't support editing, the callbacks should not be called
        verify(mockTextView, never()).onBeginBatchEdit();
        verify(mockTextView, never()).onEndBatchEdit();

        mockTextView.endBatchEdit();
        // Since TextView doesn't support editing, the callbacks should not be called
        verify(mockTextView, never()).onBeginBatchEdit();
        verify(mockTextView, never()).onEndBatchEdit();
    }

    @Test
    public void testBeginEndBatchEditCallbacksAreCalledForEditableText() throws Throwable {
        mActivityRule.runOnUiThread(() -> mTextView = spy(new TextView(mActivity)));
        mInstrumentation.waitForIdleSync();

        final FrameLayout layout = new FrameLayout(mActivity);
        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        layout.addView(mTextView, layoutParams);
        layout.setLayoutParams(layoutParams);

        mActivityRule.runOnUiThread(() -> mActivity.setContentView(layout));
        mInstrumentation.waitForIdleSync();

        mActivityRule.runOnUiThread(() -> {
            mTextView.setKeyListener(QwertyKeyListener.getInstance(false, Capitalize.NONE));
            mTextView.setText("", BufferType.EDITABLE);
            mTextView.requestFocus();
        });
        mInstrumentation.waitForIdleSync();

        reset(mTextView);
        assertTrue(mTextView.hasFocus());
        verify(mTextView, never()).onBeginBatchEdit();
        verify(mTextView, never()).onEndBatchEdit();

        mTextView.beginBatchEdit();

        verify(mTextView, times(1)).onBeginBatchEdit();
        verify(mTextView, never()).onEndBatchEdit();

        reset(mTextView);
        mTextView.endBatchEdit();
        verify(mTextView, never()).onBeginBatchEdit();
        verify(mTextView, times(1)).onEndBatchEdit();
    }

    @UiThreadTest
    @Test
    public void testBringPointIntoView() throws Throwable {
        mTextView = findTextView(R.id.textview_text);
        assertFalse(mTextView.bringPointIntoView(1));

        mTextView.layout(0, 0, 100, 100);
        assertFalse(mTextView.bringPointIntoView(2));
    }

    @Test
    public void testCancelLongPress() {
        mTextView = findTextView(R.id.textview_text);
        CtsTouchUtils.emulateLongPressOnViewCenter(mInstrumentation, mTextView);
        mTextView.cancelLongPress();
    }

    @UiThreadTest
    @Test
    public void testClearComposingText() {
        mTextView = findTextView(R.id.textview_text);
        mTextView.setText("Hello world!", BufferType.SPANNABLE);
        Spannable text = (Spannable) mTextView.getText();

        assertEquals(-1, BaseInputConnection.getComposingSpanStart(text));
        assertEquals(-1, BaseInputConnection.getComposingSpanStart(text));

        BaseInputConnection.setComposingSpans((Spannable) mTextView.getText());
        assertEquals(0, BaseInputConnection.getComposingSpanStart(text));
        assertEquals(0, BaseInputConnection.getComposingSpanStart(text));

        mTextView.clearComposingText();
        assertEquals(-1, BaseInputConnection.getComposingSpanStart(text));
        assertEquals(-1, BaseInputConnection.getComposingSpanStart(text));
    }

    @UiThreadTest
    @Test
    public void testComputeVerticalScrollExtent() {
        mTextView = new MockTextView(mActivity);
        assertEquals(0, ((MockTextView) mTextView).computeVerticalScrollExtent());

        Drawable d = TestUtils.getDrawable(mActivity, R.drawable.pass);
        mTextView.setCompoundDrawables(null, d, null, d);

        assertEquals(0, ((MockTextView) mTextView).computeVerticalScrollExtent());
    }

    @UiThreadTest
    @Test
    public void testDidTouchFocusSelect() {
        mTextView = new EditText(mActivity);
        assertFalse(mTextView.didTouchFocusSelect());

        mTextView.setFocusable(true);
        mTextView.requestFocus();
        assertTrue(mTextView.didTouchFocusSelect());
    }

    @Test
    public void testSelectAllJustAfterTap() throws Throwable {
        // Prepare an EditText with focus.
        mActivityRule.runOnUiThread(() -> {
            // Make a dummy focusable so that initial focus doesn't go to our test textview
            LinearLayout top = new LinearLayout(mActivity);
            TextView dummy = new TextView(mActivity);
            dummy.setFocusableInTouchMode(true);
            top.addView(dummy, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            mTextView = new EditText(mActivity);
            top.addView(mTextView, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            mActivity.setContentView(top);

            assertFalse(mTextView.didTouchFocusSelect());
            mTextView.setFocusable(true);
            mTextView.requestFocus();
            assertTrue(mTextView.didTouchFocusSelect());

            mTextView.setText("Hello, World.", BufferType.SPANNABLE);
        });
        mInstrumentation.waitForIdleSync();

        // Tap the view to show InsertPointController.
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mTextView);
        // bad workaround for waiting onStartInputView of LeanbackIme.apk done
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Execute SelectAll context menu.
        mActivityRule.runOnUiThread(() -> mTextView.onTextContextMenuItem(android.R.id.selectAll));
        mInstrumentation.waitForIdleSync();

        // The selection must be whole of the text contents.
        assertEquals(0, mTextView.getSelectionStart());
        assertEquals("Hello, World.", mTextView.getText().toString());
        assertEquals(mTextView.length(), mTextView.getSelectionEnd());
    }

    @UiThreadTest
    @Test
    public void testExtractText() {
        mTextView = new TextView(mActivity);

        ExtractedTextRequest request = new ExtractedTextRequest();
        ExtractedText outText = new ExtractedText();

        request.token = 0;
        request.flags = 10;
        request.hintMaxLines = 2;
        request.hintMaxChars = 20;
        assertTrue(mTextView.extractText(request, outText));

        mTextView = findTextView(R.id.textview_text);
        assertTrue(mTextView.extractText(request, outText));

        assertEquals(mActivity.getResources().getString(R.string.text_view_hello),
                outText.text.toString());

        // Tests for invalid arguments.
        assertFalse(mTextView.extractText(request, null));
        assertFalse(mTextView.extractText(null, outText));
        assertFalse(mTextView.extractText(null, null));
    }

    @UiThreadTest
    @Test
    public void testTextDirectionDefault() {
        TextView tv = new TextView(mActivity);
        assertEquals(View.TEXT_DIRECTION_INHERIT, tv.getRawTextDirection());
    }

    @UiThreadTest
    @Test
    public void testSetGetTextDirection() {
        TextView tv = new TextView(mActivity);

        tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG, tv.getRawTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        assertEquals(View.TEXT_DIRECTION_ANY_RTL, tv.getRawTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_INHERIT);
        assertEquals(View.TEXT_DIRECTION_INHERIT, tv.getRawTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_LTR);
        assertEquals(View.TEXT_DIRECTION_LTR, tv.getRawTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_RTL);
        assertEquals(View.TEXT_DIRECTION_RTL, tv.getRawTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        assertEquals(View.TEXT_DIRECTION_LOCALE, tv.getRawTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_LTR);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG_LTR, tv.getRawTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG_RTL, tv.getRawTextDirection());
    }

    @UiThreadTest
    @Test
    public void testGetResolvedTextDirectionLtr() {
        TextView tv = new TextView(mActivity);
        tv.setText("this is a test");

        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        assertEquals(View.TEXT_DIRECTION_ANY_RTL, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_INHERIT);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_LTR);
        assertEquals(View.TEXT_DIRECTION_LTR, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_RTL);
        assertEquals(View.TEXT_DIRECTION_RTL, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        assertEquals(View.TEXT_DIRECTION_LOCALE, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_LTR);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG_LTR, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG_RTL, tv.getTextDirection());
    }

    @UiThreadTest
    @Test
    public void testGetResolvedTextDirectionLtrWithInheritance() {
        LinearLayout ll = new LinearLayout(mActivity);
        ll.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);

        TextView tv = new TextView(mActivity);
        tv.setText("this is a test");
        ll.addView(tv);

        tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        assertEquals(View.TEXT_DIRECTION_ANY_RTL, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_INHERIT);
        assertEquals(View.TEXT_DIRECTION_ANY_RTL, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_LTR);
        assertEquals(View.TEXT_DIRECTION_LTR, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_RTL);
        assertEquals(View.TEXT_DIRECTION_RTL, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        assertEquals(View.TEXT_DIRECTION_LOCALE, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_LTR);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG_LTR, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG_RTL, tv.getTextDirection());
    }

    @UiThreadTest
    @Test
    public void testGetResolvedTextDirectionRtl() {
        TextView tv = new TextView(mActivity);
        tv.setText("\u05DD\u05DE"); // hebrew

        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        assertEquals(View.TEXT_DIRECTION_ANY_RTL, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_INHERIT);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_LTR);
        assertEquals(View.TEXT_DIRECTION_LTR, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_RTL);
        assertEquals(View.TEXT_DIRECTION_RTL, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        assertEquals(View.TEXT_DIRECTION_LOCALE, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_LTR);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG_LTR, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG_RTL, tv.getTextDirection());
    }

    @UiThreadTest
    @Test
    public void testGetResolvedTextDirectionRtlWithInheritance() {
        LinearLayout ll = new LinearLayout(mActivity);
        ll.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);

        TextView tv = new TextView(mActivity);
        tv.setText("\u05DD\u05DE"); // hebrew
        ll.addView(tv);

        tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        assertEquals(View.TEXT_DIRECTION_ANY_RTL, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_INHERIT);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_LTR);
        assertEquals(View.TEXT_DIRECTION_LTR, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_RTL);
        assertEquals(View.TEXT_DIRECTION_RTL, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        assertEquals(View.TEXT_DIRECTION_LOCALE, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_LTR);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG_LTR, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG_RTL, tv.getTextDirection());

        // Force to RTL text direction on the layout
        ll.setTextDirection(View.TEXT_DIRECTION_RTL);

        tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        assertEquals(View.TEXT_DIRECTION_ANY_RTL, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_INHERIT);
        assertEquals(View.TEXT_DIRECTION_RTL, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_LTR);
        assertEquals(View.TEXT_DIRECTION_LTR, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_RTL);
        assertEquals(View.TEXT_DIRECTION_RTL, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        assertEquals(View.TEXT_DIRECTION_LOCALE, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_LTR);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG_LTR, tv.getTextDirection());

        tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG_RTL, tv.getTextDirection());
    }

    @UiThreadTest
    @Test
    public void testResetTextDirection() {
        LinearLayout ll = (LinearLayout) mActivity.findViewById(R.id.layout_textviewtest);
        TextView tv = (TextView) mActivity.findViewById(R.id.textview_rtl);

        ll.setTextDirection(View.TEXT_DIRECTION_RTL);
        tv.setTextDirection(View.TEXT_DIRECTION_INHERIT);
        assertEquals(View.TEXT_DIRECTION_RTL, tv.getTextDirection());

        // No reset when we remove the view
        ll.removeView(tv);
        assertEquals(View.TEXT_DIRECTION_RTL, tv.getTextDirection());

        // Reset is done when we add the view
        ll.addView(tv);
        assertEquals(View.TEXT_DIRECTION_FIRST_STRONG, tv.getTextDirection());
    }

    @UiThreadTest
    @Test
    public void testTextDirectionFirstStrongLtr() {
        {
            // The first directional character is LTR, the paragraph direction is LTR.
            LinearLayout ll = new LinearLayout(mActivity);

            TextView tv = new TextView(mActivity);
            tv.setText("this is a test");
            ll.addView(tv);

            tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_LTR);
            assertEquals(View.TEXT_DIRECTION_FIRST_STRONG_LTR, tv.getTextDirection());

            tv.onPreDraw();  // For freezing layout.
            Layout layout = tv.getLayout();
            assertEquals(Layout.DIR_LEFT_TO_RIGHT, layout.getParagraphDirection(0));
        }
        {
            // The first directional character is RTL, the paragraph direction is RTL.
            LinearLayout ll = new LinearLayout(mActivity);

            TextView tv = new TextView(mActivity);
            tv.setText("\u05DD\u05DE"); // Hebrew
            ll.addView(tv);

            tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_LTR);
            assertEquals(View.TEXT_DIRECTION_FIRST_STRONG_LTR, tv.getTextDirection());

            tv.onPreDraw();  // For freezing layout.
            Layout layout = tv.getLayout();
            assertEquals(Layout.DIR_RIGHT_TO_LEFT, layout.getParagraphDirection(0));
        }
        {
            // The first directional character is not a strong directional character, the paragraph
            // direction is LTR.
            LinearLayout ll = new LinearLayout(mActivity);

            TextView tv = new TextView(mActivity);
            tv.setText("\uFFFD");  // REPLACEMENT CHARACTER. Neutral direction.
            ll.addView(tv);

            tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_LTR);
            assertEquals(View.TEXT_DIRECTION_FIRST_STRONG_LTR, tv.getTextDirection());

            tv.onPreDraw();  // For freezing layout.
            Layout layout = tv.getLayout();
            assertEquals(Layout.DIR_LEFT_TO_RIGHT, layout.getParagraphDirection(0));
        }
    }

    @UiThreadTest
    @Test
    public void testTextDirectionFirstStrongRtl() {
        {
            // The first directional character is LTR, the paragraph direction is LTR.
            LinearLayout ll = new LinearLayout(mActivity);

            TextView tv = new TextView(mActivity);
            tv.setText("this is a test");
            ll.addView(tv);

            tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);
            assertEquals(View.TEXT_DIRECTION_FIRST_STRONG_RTL, tv.getTextDirection());

            tv.onPreDraw();  // For freezing layout.
            Layout layout = tv.getLayout();
            assertEquals(Layout.DIR_LEFT_TO_RIGHT, layout.getParagraphDirection(0));
        }
        {
            // The first directional character is RTL, the paragraph direction is RTL.
            LinearLayout ll = new LinearLayout(mActivity);

            TextView tv = new TextView(mActivity);
            tv.setText("\u05DD\u05DE"); // Hebrew
            ll.addView(tv);

            tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);
            assertEquals(View.TEXT_DIRECTION_FIRST_STRONG_RTL, tv.getTextDirection());

            tv.onPreDraw();  // For freezing layout.
            Layout layout = tv.getLayout();
            assertEquals(Layout.DIR_RIGHT_TO_LEFT, layout.getParagraphDirection(0));
        }
        {
            // The first directional character is not a strong directional character, the paragraph
            // direction is RTL.
            LinearLayout ll = new LinearLayout(mActivity);

            TextView tv = new TextView(mActivity);
            tv.setText("\uFFFD");  // REPLACEMENT CHARACTER. Neutral direction.
            ll.addView(tv);

            tv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);
            assertEquals(View.TEXT_DIRECTION_FIRST_STRONG_RTL, tv.getTextDirection());

            tv.onPreDraw();  // For freezing layout.
            Layout layout = tv.getLayout();
            assertEquals(Layout.DIR_RIGHT_TO_LEFT, layout.getParagraphDirection(0));
        }
    }

    @UiThreadTest
    @Test
    public void testTextLocales() {
        TextView tv = new TextView(mActivity);
        assertEquals(Locale.getDefault(), tv.getTextLocale());
        assertEquals(LocaleList.getDefault(), tv.getTextLocales());

        tv.setTextLocale(Locale.CHINESE);
        assertEquals(Locale.CHINESE, tv.getTextLocale());
        assertEquals(new LocaleList(Locale.CHINESE), tv.getTextLocales());

        tv.setTextLocales(LocaleList.forLanguageTags("en,ja"));
        assertEquals(Locale.forLanguageTag("en"), tv.getTextLocale());
        assertEquals(LocaleList.forLanguageTags("en,ja"), tv.getTextLocales());

        try {
            tv.setTextLocale(null);
            fail("Setting the text locale to null should throw");
        } catch (Throwable e) {
            assertEquals(IllegalArgumentException.class, e.getClass());
        }

        try {
            tv.setTextLocales(null);
            fail("Setting the text locales to null should throw");
        } catch (Throwable e) {
            assertEquals(IllegalArgumentException.class, e.getClass());
        }

        try {
            tv.setTextLocales(new LocaleList());
            fail("Setting the text locale to an empty list should throw");
        } catch (Throwable e) {
            assertEquals(IllegalArgumentException.class, e.getClass());
        }
    }

    @UiThreadTest
    @Test
    public void testAllCaps_Localization() {
        final String testString = "abcdefghijklmnopqrstuvwxyz i\u0307\u0301 άέήίΐόύΰώάυ ή";

        // Capital "i" in Turkish and Azerbaijani is different from English, Lithuanian has special
        // rules for uppercasing dotted i with accents, and Greek has complex capitalization rules.
        final Locale[] testLocales = {
            new Locale("az", "AZ"),  // Azerbaijani
            new Locale("tr", "TR"),  // Turkish
            new Locale("lt", "LT"),  // Lithuanian
            new Locale("el", "GR"),  // Greek
            Locale.US,
        };

        final TextView tv = new TextView(mActivity);
        tv.setAllCaps(true);
        for (Locale locale: testLocales) {
            tv.setTextLocale(locale);
            assertEquals("Locale: " + locale.getDisplayName(),
                         UCharacter.toUpperCase(locale, testString),
                         tv.getTransformationMethod().getTransformation(testString, tv).toString());
        }
    }

    @UiThreadTest
    @Test
    public void testAllCaps_SpansArePreserved() {
        final Locale greek = new Locale("el", "GR");
        final String lowerString = "ι\u0301ριδα";  // ίριδα with first letter decomposed
        final String upperString = "ΙΡΙΔΑ";  // uppercased
        // expected lowercase to uppercase index map
        final int[] indexMap = {0, 1, 1, 2, 3, 4, 5};
        final int flags = Spanned.SPAN_INCLUSIVE_INCLUSIVE;

        final TextView tv = new TextView(mActivity);
        tv.setTextLocale(greek);
        tv.setAllCaps(true);

        final Spannable source = new SpannableString(lowerString);
        source.setSpan(new Object(), 0, 1, flags);
        source.setSpan(new Object(), 1, 2, flags);
        source.setSpan(new Object(), 2, 3, flags);
        source.setSpan(new Object(), 3, 4, flags);
        source.setSpan(new Object(), 4, 5, flags);
        source.setSpan(new Object(), 5, 6, flags);
        source.setSpan(new Object(), 0, 2, flags);
        source.setSpan(new Object(), 1, 3, flags);
        source.setSpan(new Object(), 2, 4, flags);
        source.setSpan(new Object(), 0, 6, flags);
        final Object[] sourceSpans = source.getSpans(0, source.length(), Object.class);

        final CharSequence transformed =
                tv.getTransformationMethod().getTransformation(source, tv);
        assertTrue(transformed instanceof Spanned);
        final Spanned result = (Spanned) transformed;

        assertEquals(upperString, transformed.toString());
        final Object[] resultSpans = result.getSpans(0, result.length(), Object.class);
        assertEquals(sourceSpans.length, resultSpans.length);
        for (int i = 0; i < sourceSpans.length; i++) {
            assertSame(sourceSpans[i], resultSpans[i]);
            final Object span = sourceSpans[i];
            assertEquals(indexMap[source.getSpanStart(span)], result.getSpanStart(span));
            assertEquals(indexMap[source.getSpanEnd(span)], result.getSpanEnd(span));
            assertEquals(source.getSpanFlags(span), result.getSpanFlags(span));
        }
    }

    @UiThreadTest
    @Test
    public void testTextAlignmentDefault() {
        TextView tv = new TextView(mActivity);
        assertEquals(View.TEXT_ALIGNMENT_GRAVITY, tv.getRawTextAlignment());
        // resolved default text alignment is GRAVITY
        assertEquals(View.TEXT_ALIGNMENT_GRAVITY, tv.getTextAlignment());
    }

    @UiThreadTest
    @Test
    public void testSetGetTextAlignment() {
        TextView tv = new TextView(mActivity);

        tv.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        assertEquals(View.TEXT_ALIGNMENT_GRAVITY, tv.getRawTextAlignment());

        tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        assertEquals(View.TEXT_ALIGNMENT_CENTER, tv.getRawTextAlignment());

        tv.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
        assertEquals(View.TEXT_ALIGNMENT_TEXT_START, tv.getRawTextAlignment());

        tv.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_END);
        assertEquals(View.TEXT_ALIGNMENT_TEXT_END, tv.getRawTextAlignment());

        tv.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        assertEquals(View.TEXT_ALIGNMENT_VIEW_START, tv.getRawTextAlignment());

        tv.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
        assertEquals(View.TEXT_ALIGNMENT_VIEW_END, tv.getRawTextAlignment());
    }

    @UiThreadTest
    @Test
    public void testGetResolvedTextAlignment() {
        TextView tv = new TextView(mActivity);

        assertEquals(View.TEXT_ALIGNMENT_GRAVITY, tv.getTextAlignment());

        // Test center alignment first so that we dont hit the default case
        tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        assertEquals(View.TEXT_ALIGNMENT_CENTER, tv.getTextAlignment());

        // Test the default case too
        tv.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        assertEquals(View.TEXT_ALIGNMENT_GRAVITY, tv.getTextAlignment());

        tv.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
        assertEquals(View.TEXT_ALIGNMENT_TEXT_START, tv.getTextAlignment());

        tv.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_END);
        assertEquals(View.TEXT_ALIGNMENT_TEXT_END, tv.getTextAlignment());

        tv.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        assertEquals(View.TEXT_ALIGNMENT_VIEW_START, tv.getTextAlignment());

        tv.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
        assertEquals(View.TEXT_ALIGNMENT_VIEW_END, tv.getTextAlignment());
    }

    @UiThreadTest
    @Test
    public void testGetResolvedTextAlignmentWithInheritance() {
        LinearLayout ll = new LinearLayout(mActivity);
        ll.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);

        TextView tv = new TextView(mActivity);
        ll.addView(tv);

        // check defaults
        assertEquals(View.TEXT_ALIGNMENT_GRAVITY, tv.getRawTextAlignment());
        assertEquals(View.TEXT_ALIGNMENT_GRAVITY, tv.getTextAlignment());

        // set inherit and check that child is following parent
        tv.setTextAlignment(View.TEXT_ALIGNMENT_INHERIT);
        assertEquals(View.TEXT_ALIGNMENT_INHERIT, tv.getRawTextAlignment());

        ll.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        assertEquals(View.TEXT_ALIGNMENT_CENTER, tv.getTextAlignment());

        ll.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
        assertEquals(View.TEXT_ALIGNMENT_TEXT_START, tv.getTextAlignment());

        ll.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_END);
        assertEquals(View.TEXT_ALIGNMENT_TEXT_END, tv.getTextAlignment());

        ll.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        assertEquals(View.TEXT_ALIGNMENT_VIEW_START, tv.getTextAlignment());

        ll.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
        assertEquals(View.TEXT_ALIGNMENT_VIEW_END, tv.getTextAlignment());

        // now get rid of the inheritance but still change the parent
        tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);

        ll.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        assertEquals(View.TEXT_ALIGNMENT_CENTER, tv.getTextAlignment());

        ll.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
        assertEquals(View.TEXT_ALIGNMENT_CENTER, tv.getTextAlignment());

        ll.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_END);
        assertEquals(View.TEXT_ALIGNMENT_CENTER, tv.getTextAlignment());

        ll.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        assertEquals(View.TEXT_ALIGNMENT_CENTER, tv.getTextAlignment());

        ll.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
        assertEquals(View.TEXT_ALIGNMENT_CENTER, tv.getTextAlignment());
    }

    @UiThreadTest
    @Test
    public void testResetTextAlignment() {
        LinearLayout ll = (LinearLayout) mActivity.findViewById(R.id.layout_textviewtest);
        TextView tv = (TextView) mActivity.findViewById(R.id.textview_rtl);

        ll.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        tv.setTextAlignment(View.TEXT_ALIGNMENT_INHERIT);
        assertEquals(View.TEXT_ALIGNMENT_CENTER, tv.getTextAlignment());

        // No reset when we remove the view
        ll.removeView(tv);
        assertEquals(View.TEXT_ALIGNMENT_CENTER, tv.getTextAlignment());

        // Reset is done when we add the view
        // Default text alignment is GRAVITY
        ll.addView(tv);
        assertEquals(View.TEXT_ALIGNMENT_GRAVITY, tv.getTextAlignment());
    }

    @UiThreadTest
    @Test
    public void testDrawableResolution() {
        // Case 1.1: left / right drawable defined in default LTR mode
        TextView tv = (TextView) mActivity.findViewById(R.id.textview_drawable_1_1);
        TestUtils.verifyCompoundDrawables(tv, R.drawable.icon_blue, R.drawable.icon_red,
                R.drawable.icon_green, R.drawable.icon_yellow);
        TestUtils.verifyCompoundDrawablesRelative(tv, -1, -1,
                R.drawable.icon_green, R.drawable.icon_yellow);

        // Case 1.2: left / right drawable defined in default RTL mode
        tv = (TextView) mActivity.findViewById(R.id.textview_drawable_1_2);
        TestUtils.verifyCompoundDrawables(tv, R.drawable.icon_blue, R.drawable.icon_red,
                R.drawable.icon_green, R.drawable.icon_yellow);
        TestUtils.verifyCompoundDrawablesRelative(tv, -1, -1,
                R.drawable.icon_green, R.drawable.icon_yellow);

        // Case 2.1: start / end drawable defined in LTR mode
        tv = (TextView) mActivity.findViewById(R.id.textview_drawable_2_1);
        TestUtils.verifyCompoundDrawables(tv, R.drawable.icon_blue, R.drawable.icon_red,
                R.drawable.icon_green, R.drawable.icon_yellow);
        TestUtils.verifyCompoundDrawablesRelative(tv, R.drawable.icon_blue, R.drawable.icon_red,
                R.drawable.icon_green, R.drawable.icon_yellow);

        // Case 2.2: start / end drawable defined in RTL mode
        tv = (TextView) mActivity.findViewById(R.id.textview_drawable_2_2);
        TestUtils.verifyCompoundDrawables(tv, R.drawable.icon_red, R.drawable.icon_blue,
                R.drawable.icon_green, R.drawable.icon_yellow);
        TestUtils.verifyCompoundDrawablesRelative(tv, R.drawable.icon_blue, R.drawable.icon_red,
                R.drawable.icon_green, R.drawable.icon_yellow);

        // Case 3.1: left / right / start / end drawable defined in LTR mode
        tv = (TextView) mActivity.findViewById(R.id.textview_drawable_3_1);
        TestUtils.verifyCompoundDrawables(tv, R.drawable.icon_blue, R.drawable.icon_red,
                R.drawable.icon_green, R.drawable.icon_yellow);
        TestUtils.verifyCompoundDrawablesRelative(tv, R.drawable.icon_blue, R.drawable.icon_red,
                R.drawable.icon_green, R.drawable.icon_yellow);

        // Case 3.2: left / right / start / end drawable defined in RTL mode
        tv = (TextView) mActivity.findViewById(R.id.textview_drawable_3_2);
        TestUtils.verifyCompoundDrawables(tv, R.drawable.icon_red, R.drawable.icon_blue,
                R.drawable.icon_green, R.drawable.icon_yellow);
        TestUtils.verifyCompoundDrawablesRelative(tv, R.drawable.icon_blue, R.drawable.icon_red,
                R.drawable.icon_green, R.drawable.icon_yellow);

        // Case 4.1: start / end drawable defined in LTR mode inside a layout
        // that defines the layout direction
        tv = (TextView) mActivity.findViewById(R.id.textview_drawable_4_1);
        TestUtils.verifyCompoundDrawables(tv, R.drawable.icon_blue, R.drawable.icon_red,
                R.drawable.icon_green, R.drawable.icon_yellow);
        TestUtils.verifyCompoundDrawablesRelative(tv, R.drawable.icon_blue, R.drawable.icon_red,
                R.drawable.icon_green, R.drawable.icon_yellow);

        // Case 4.2: start / end drawable defined in RTL mode inside a layout
        // that defines the layout direction
        tv = (TextView) mActivity.findViewById(R.id.textview_drawable_4_2);
        TestUtils.verifyCompoundDrawables(tv, R.drawable.icon_red, R.drawable.icon_blue,
                R.drawable.icon_green, R.drawable.icon_yellow);
        TestUtils.verifyCompoundDrawablesRelative(tv, R.drawable.icon_blue, R.drawable.icon_red,
                R.drawable.icon_green, R.drawable.icon_yellow);

        // Case 5.1: left / right / start / end drawable defined in LTR mode inside a layout
        // that defines the layout direction
        tv = (TextView) mActivity.findViewById(R.id.textview_drawable_5_1);
        TestUtils.verifyCompoundDrawables(tv, R.drawable.icon_blue, R.drawable.icon_red,
                R.drawable.icon_green, R.drawable.icon_yellow);
        TestUtils.verifyCompoundDrawablesRelative(tv, R.drawable.icon_blue, R.drawable.icon_red,
                R.drawable.icon_green, R.drawable.icon_yellow);

        // Case 5.2: left / right / start / end drawable defined in RTL mode inside a layout
        // that defines the layout direction
        tv = (TextView) mActivity.findViewById(R.id.textview_drawable_5_2);
        TestUtils.verifyCompoundDrawables(tv, R.drawable.icon_red, R.drawable.icon_blue,
                R.drawable.icon_green, R.drawable.icon_yellow);
        TestUtils.verifyCompoundDrawablesRelative(tv, R.drawable.icon_blue, R.drawable.icon_red,
                R.drawable.icon_green, R.drawable.icon_yellow);
    }

    @UiThreadTest
    @Test
    public void testDrawableResolution2() {
        // Case 1.1: left / right drawable defined in default LTR mode
        TextView tv = (TextView) mActivity.findViewById(R.id.textview_drawable_1_1);
        TestUtils.verifyCompoundDrawables(tv, R.drawable.icon_blue, R.drawable.icon_red,
                R.drawable.icon_green, R.drawable.icon_yellow);

        tv.setCompoundDrawables(null, null,
                TestUtils.getDrawable(mActivity, R.drawable.icon_yellow), null);
        TestUtils.verifyCompoundDrawables(tv, -1, R.drawable.icon_yellow, -1, -1);

        tv = (TextView) mActivity.findViewById(R.id.textview_drawable_1_2);
        TestUtils.verifyCompoundDrawables(tv, R.drawable.icon_blue, R.drawable.icon_red,
                R.drawable.icon_green, R.drawable.icon_yellow);

        tv.setCompoundDrawables(TestUtils.getDrawable(mActivity, R.drawable.icon_yellow), null,
                null, null);
        TestUtils.verifyCompoundDrawables(tv, R.drawable.icon_yellow, -1, -1, -1);

        tv = (TextView) mActivity.findViewById(R.id.textview_ltr);
        TestUtils.verifyCompoundDrawables(tv, -1, -1, -1, -1);

        tv.setCompoundDrawables(TestUtils.getDrawable(mActivity, R.drawable.icon_blue), null,
                TestUtils.getDrawable(mActivity, R.drawable.icon_red), null);
        TestUtils.verifyCompoundDrawables(tv, R.drawable.icon_blue, R.drawable.icon_red, -1, -1);

        tv.setCompoundDrawablesRelative(TestUtils.getDrawable(mActivity, R.drawable.icon_yellow),
                null, null, null);
        TestUtils.verifyCompoundDrawables(tv, R.drawable.icon_yellow, -1, -1, -1);
    }

    @Test
    public void testCompoundAndTotalPadding() {
        final Resources res = mActivity.getResources();
        final int drawablePadding = res.getDimensionPixelSize(R.dimen.textview_drawable_padding);
        final int paddingLeft = res.getDimensionPixelSize(R.dimen.textview_padding_left);
        final int paddingRight = res.getDimensionPixelSize(R.dimen.textview_padding_right);
        final int paddingTop = res.getDimensionPixelSize(R.dimen.textview_padding_top);
        final int paddingBottom = res.getDimensionPixelSize(R.dimen.textview_padding_bottom);
        final int iconSize = TestUtils.dpToPx(mActivity, 32);

        final TextView textViewLtr = (TextView) mActivity.findViewById(
                R.id.textview_compound_drawable_ltr);
        final int combinedPaddingLeftLtr = paddingLeft + drawablePadding + iconSize;
        final int combinedPaddingRightLtr = paddingRight + drawablePadding + iconSize;
        assertEquals(combinedPaddingLeftLtr, textViewLtr.getCompoundPaddingLeft());
        assertEquals(combinedPaddingLeftLtr, textViewLtr.getCompoundPaddingStart());
        assertEquals(combinedPaddingLeftLtr, textViewLtr.getTotalPaddingLeft());
        assertEquals(combinedPaddingLeftLtr, textViewLtr.getTotalPaddingStart());
        assertEquals(combinedPaddingRightLtr, textViewLtr.getCompoundPaddingRight());
        assertEquals(combinedPaddingRightLtr, textViewLtr.getCompoundPaddingEnd());
        assertEquals(combinedPaddingRightLtr, textViewLtr.getTotalPaddingRight());
        assertEquals(combinedPaddingRightLtr, textViewLtr.getTotalPaddingEnd());
        assertEquals(paddingTop + drawablePadding + iconSize,
                textViewLtr.getCompoundPaddingTop());
        assertEquals(paddingBottom + drawablePadding + iconSize,
                textViewLtr.getCompoundPaddingBottom());

        final TextView textViewRtl = (TextView) mActivity.findViewById(
                R.id.textview_compound_drawable_rtl);
        final int combinedPaddingLeftRtl = paddingLeft + drawablePadding + iconSize;
        final int combinedPaddingRightRtl = paddingRight + drawablePadding + iconSize;
        assertEquals(combinedPaddingLeftRtl, textViewRtl.getCompoundPaddingLeft());
        assertEquals(combinedPaddingLeftRtl, textViewRtl.getCompoundPaddingEnd());
        assertEquals(combinedPaddingLeftRtl, textViewRtl.getTotalPaddingLeft());
        assertEquals(combinedPaddingLeftRtl, textViewRtl.getTotalPaddingEnd());
        assertEquals(combinedPaddingRightRtl, textViewRtl.getCompoundPaddingRight());
        assertEquals(combinedPaddingRightRtl, textViewRtl.getCompoundPaddingStart());
        assertEquals(combinedPaddingRightRtl, textViewRtl.getTotalPaddingRight());
        assertEquals(combinedPaddingRightRtl, textViewRtl.getTotalPaddingStart());
        assertEquals(paddingTop + drawablePadding + iconSize,
                textViewRtl.getCompoundPaddingTop());
        assertEquals(paddingBottom + drawablePadding + iconSize,
                textViewRtl.getCompoundPaddingBottom());
    }

    @UiThreadTest
    @Test
    public void testSetGetBreakStrategy() {
        TextView tv = new TextView(mActivity);

        final PackageManager pm = mInstrumentation.getTargetContext().getPackageManager();

        // The default value is from the theme, here the default is BREAK_STRATEGY_HIGH_QUALITY for
        // TextView except for Android Wear. The default value for Android Wear is
        // BREAK_STRATEGY_BALANCED.
        if (pm.hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            // Android Wear
            assertEquals(Layout.BREAK_STRATEGY_BALANCED, tv.getBreakStrategy());
        } else {
            // All other form factor.
            assertEquals(Layout.BREAK_STRATEGY_HIGH_QUALITY, tv.getBreakStrategy());
        }

        tv.setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);
        assertEquals(Layout.BREAK_STRATEGY_SIMPLE, tv.getBreakStrategy());

        tv.setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY);
        assertEquals(Layout.BREAK_STRATEGY_HIGH_QUALITY, tv.getBreakStrategy());

        tv.setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED);
        assertEquals(Layout.BREAK_STRATEGY_BALANCED, tv.getBreakStrategy());

        EditText et = new EditText(mActivity);

        // The default value is from the theme, here the default is BREAK_STRATEGY_SIMPLE for
        // EditText.
        assertEquals(Layout.BREAK_STRATEGY_SIMPLE, et.getBreakStrategy());

        et.setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);
        assertEquals(Layout.BREAK_STRATEGY_SIMPLE, et.getBreakStrategy());

        et.setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY);
        assertEquals(Layout.BREAK_STRATEGY_HIGH_QUALITY, et.getBreakStrategy());

        et.setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED);
        assertEquals(Layout.BREAK_STRATEGY_BALANCED, et.getBreakStrategy());
    }

    @UiThreadTest
    @Test
    public void testSetGetHyphenationFrequency() {
        TextView tv = new TextView(mActivity);

        assertEquals(Layout.HYPHENATION_FREQUENCY_NORMAL, tv.getHyphenationFrequency());

        tv.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);
        assertEquals(Layout.HYPHENATION_FREQUENCY_NONE, tv.getHyphenationFrequency());

        tv.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL);
        assertEquals(Layout.HYPHENATION_FREQUENCY_NORMAL, tv.getHyphenationFrequency());

        tv.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_FULL);
        assertEquals(Layout.HYPHENATION_FREQUENCY_FULL, tv.getHyphenationFrequency());
    }

    @UiThreadTest
    @Test
    public void testSetGetJustify() {
        TextView tv = new TextView(mActivity);

        assertEquals(Layout.JUSTIFICATION_MODE_NONE, tv.getJustificationMode());
        tv.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD);
        assertEquals(Layout.JUSTIFICATION_MODE_INTER_WORD, tv.getJustificationMode());
        tv.setJustificationMode(Layout.JUSTIFICATION_MODE_NONE);
        assertEquals(Layout.JUSTIFICATION_MODE_NONE, tv.getJustificationMode());
    }

    @Test
    public void testJustificationByStyle() {
        TextView defaultTv = findTextView(R.id.textview_justification_default);
        TextView noneTv = findTextView(R.id.textview_justification_none);
        TextView interWordTv = findTextView(R.id.textview_justification_inter_word);

        assertEquals(Layout.JUSTIFICATION_MODE_NONE, defaultTv.getJustificationMode());
        assertEquals(Layout.JUSTIFICATION_MODE_NONE, noneTv.getJustificationMode());
        assertEquals(Layout.JUSTIFICATION_MODE_INTER_WORD, interWordTv.getJustificationMode());
    }

    @Test
    public void testSetAndGetCustomSelectionActionModeCallback() throws Throwable {
        final String text = "abcde";
        mActivityRule.runOnUiThread(() -> {
            mTextView = new EditText(mActivity);
            mActivity.setContentView(mTextView);
            mTextView.setText(text, BufferType.SPANNABLE);
            mTextView.setTextIsSelectable(true);
            mTextView.requestFocus();
            mTextView.setSelected(true);
            mTextView.setTextClassifier(TextClassifier.NO_OP);
        });
        mInstrumentation.waitForIdleSync();

        // Check default value.
        assertNull(mTextView.getCustomSelectionActionModeCallback());

        final ActionMode.Callback mockActionModeCallback = mock(ActionMode.Callback.class);
        when(mockActionModeCallback.onCreateActionMode(any(ActionMode.class), any(Menu.class))).
                thenReturn(Boolean.FALSE);
        mTextView.setCustomSelectionActionModeCallback(mockActionModeCallback);
        assertEquals(mockActionModeCallback,
                mTextView.getCustomSelectionActionModeCallback());

        mActivityRule.runOnUiThread(() -> {
            // Set selection and try to start action mode.
            final Bundle args = new Bundle();
            args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0);
            args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, text.length());
            mTextView.performAccessibilityAction(
                    AccessibilityNodeInfo.ACTION_SET_SELECTION, args);
        });
        mInstrumentation.waitForIdleSync();

        verify(mockActionModeCallback, times(1)).onCreateActionMode(
                any(ActionMode.class), any(Menu.class));

        mActivityRule.runOnUiThread(() -> {
            // Remove selection and stop action mode.
            mTextView.onTextContextMenuItem(android.R.id.copy);
        });
        mInstrumentation.waitForIdleSync();

        // Action mode was blocked.
        verify(mockActionModeCallback, never()).onDestroyActionMode(any(ActionMode.class));

        // Reset and reconfigure callback.
        reset(mockActionModeCallback);
        when(mockActionModeCallback.onCreateActionMode(any(ActionMode.class), any(Menu.class))).
                thenReturn(Boolean.TRUE);
        assertEquals(mockActionModeCallback, mTextView.getCustomSelectionActionModeCallback());

        mActivityRule.runOnUiThread(() -> {
            // Set selection and try to start action mode.
            final Bundle args = new Bundle();
            args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0);
            args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, text.length());
            mTextView.performAccessibilityAction(
                    AccessibilityNodeInfo.ACTION_SET_SELECTION, args);

        });
        mInstrumentation.waitForIdleSync();

        verify(mockActionModeCallback, times(1)).onCreateActionMode(
                any(ActionMode.class), any(Menu.class));

        mActivityRule.runOnUiThread(() -> {
            // Remove selection and stop action mode.
            mTextView.onTextContextMenuItem(android.R.id.copy);
        });
        mInstrumentation.waitForIdleSync();

        // Action mode was started
        verify(mockActionModeCallback, times(1)).onDestroyActionMode(any(ActionMode.class));
    }

    @UiThreadTest
    @Test
    public void testSetAndGetCustomInsertionActionMode() {
        initTextViewForTyping();
        // Check default value.
        assertNull(mTextView.getCustomInsertionActionModeCallback());

        final ActionMode.Callback mockActionModeCallback = mock(ActionMode.Callback.class);
        when(mockActionModeCallback.onCreateActionMode(any(ActionMode.class), any(Menu.class))).
                thenReturn(Boolean.FALSE);
        mTextView.setCustomInsertionActionModeCallback(mockActionModeCallback);
        assertEquals(mockActionModeCallback, mTextView.getCustomInsertionActionModeCallback());
        // TODO(Bug: 22033189): Tests the set callback is actually used.
    }

    @UiThreadTest
    @Test
    public void testRespectsViewFocusability() {
        TextView v = (TextView) mActivity.findViewById(R.id.textview_singleLine);
        assertFalse(v.isFocusable());
        // TextView used to set focusable to true or false verbatim which would break the following.
        v.setClickable(true);
        assertTrue(v.isFocusable());
    }

    @Test
    public void testTextShadows() throws Throwable {
        final TextView textViewWithConfiguredShadow =
                (TextView) mActivity.findViewById(R.id.textview_with_shadow);
        assertEquals(1.0f, textViewWithConfiguredShadow.getShadowDx(), 0.0f);
        assertEquals(2.0f, textViewWithConfiguredShadow.getShadowDy(), 0.0f);
        assertEquals(3.0f, textViewWithConfiguredShadow.getShadowRadius(), 0.0f);
        assertEquals(Color.GREEN, textViewWithConfiguredShadow.getShadowColor());

        final TextView textView = (TextView) mActivity.findViewById(R.id.textview_text);
        assertEquals(0.0f, textView.getShadowDx(), 0.0f);
        assertEquals(0.0f, textView.getShadowDy(), 0.0f);
        assertEquals(0.0f, textView.getShadowRadius(), 0.0f);

        mActivityRule.runOnUiThread(() -> textView.setShadowLayer(5.0f, 3.0f, 4.0f, Color.RED));
        mInstrumentation.waitForIdleSync();
        assertEquals(3.0f, textView.getShadowDx(), 0.0f);
        assertEquals(4.0f, textView.getShadowDy(), 0.0f);
        assertEquals(5.0f, textView.getShadowRadius(), 0.0f);
        assertEquals(Color.RED, textView.getShadowColor());
    }

    @Test
    public void testFontFeatureSettings() throws Throwable {
        final TextView textView = (TextView) mActivity.findViewById(R.id.textview_text);
        assertTrue(TextUtils.isEmpty(textView.getFontFeatureSettings()));

        mActivityRule.runOnUiThread(() -> textView.setFontFeatureSettings("smcp"));
        mInstrumentation.waitForIdleSync();
        assertEquals("smcp", textView.getFontFeatureSettings());

        mActivityRule.runOnUiThread(() -> textView.setFontFeatureSettings("frac"));
        mInstrumentation.waitForIdleSync();
        assertEquals("frac", textView.getFontFeatureSettings());
    }

    private static class SoftInputResultReceiver extends ResultReceiver {
        private boolean mIsDone;
        private int mResultCode;

        public SoftInputResultReceiver(Handler handler) {
            super(handler);
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            mResultCode = resultCode;
            mIsDone = true;
        }

        public void reset() {
            mIsDone = false;
        }
    }

    @Test
    public void testAccessShowSoftInputOnFocus() throws Throwable {
        if (!mActivity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_INPUT_METHODS)) {
            return;
        }

        // prepare a test Layout
        // will add an focusable TextView so that EditText will not get focus at activity start
        final TextView textView = new TextView(mActivity);
        textView.setFocusable(true);
        textView.setFocusableInTouchMode(true);
        // EditText to test
        final EditText editText = new EditText(mActivity);
        editText.setShowSoftInputOnFocus(true);
        editText.setFocusable(true);
        editText.setFocusableInTouchMode(true);
        // prepare and set the layout
        final LinearLayout layout = new LinearLayout(mActivity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(textView, new ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));
        layout.addView(editText, new ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));
        mActivityRule.runOnUiThread(() -> mActivity.setContentView(layout,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT)));
        mInstrumentation.waitForIdleSync();

        assertTrue(editText.getShowSoftInputOnFocus());

        // And emulate click on it
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, editText);

        // Verify that input method manager is active and accepting text
        final InputMethodManager imManager = (InputMethodManager) mActivity
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        PollingCheck.waitFor(imManager::isActive);
        assertTrue(imManager.isAcceptingText());
        assertTrue(imManager.isActive(editText));

        // Since there is no API to check that soft input is showing, we're going to ask
        // the input method manager to show soft input, passing our custom result receiver.
        // We're expecting to get UNCHANGED_SHOWN, indicating that the soft input was already
        // showing before showSoftInput was called.
        SoftInputResultReceiver receiver = new SoftInputResultReceiver(mHandler);
        imManager.showSoftInput(editText, 0, receiver);
        PollingCheck.waitFor(() -> receiver.mIsDone);
        assertEquals(InputMethodManager.RESULT_UNCHANGED_SHOWN, receiver.mResultCode);

        // Close soft input
        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);

        // Reconfigure our edit text to not show soft input on focus
        mActivityRule.runOnUiThread(() -> editText.setShowSoftInputOnFocus(false));
        mInstrumentation.waitForIdleSync();
        assertFalse(editText.getShowSoftInputOnFocus());

        // Emulate click on it
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, editText);

        // Ask input method manager to show soft input again. This time we're expecting to get
        // SHOWN, indicating that the soft input was not showing before showSoftInput was called.
        receiver.reset();
        imManager.showSoftInput(editText, 0, receiver);
        PollingCheck.waitFor(() -> receiver.mIsDone);
        assertEquals(InputMethodManager.RESULT_SHOWN, receiver.mResultCode);

        // Close soft input
        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
    }

    @Test
    public void testIsSuggestionsEnabled() throws Throwable {
        mTextView = findTextView(R.id.textview_text);

        // Anything without InputType.TYPE_CLASS_TEXT doesn't have suggestions enabled
        mActivityRule.runOnUiThread(() -> mTextView.setInputType(InputType.TYPE_CLASS_DATETIME));
        assertFalse(mTextView.isSuggestionsEnabled());

        mActivityRule.runOnUiThread(() -> mTextView.setInputType(InputType.TYPE_CLASS_PHONE));
        assertFalse(mTextView.isSuggestionsEnabled());

        mActivityRule.runOnUiThread(() -> mTextView.setInputType(InputType.TYPE_CLASS_NUMBER));
        assertFalse(mTextView.isSuggestionsEnabled());

        // From this point our text view has InputType.TYPE_CLASS_TEXT

        // Anything with InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS doesn't have suggestions enabled
        mActivityRule.runOnUiThread(
                () -> mTextView.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
        assertFalse(mTextView.isSuggestionsEnabled());

        mActivityRule.runOnUiThread(
                () -> mTextView.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL |
                                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
        assertFalse(mTextView.isSuggestionsEnabled());

        mActivityRule.runOnUiThread(
                () -> mTextView.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS |
                                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
        assertFalse(mTextView.isSuggestionsEnabled());

        // Otherwise suggestions are enabled for specific type variations enumerated in the
        // documentation of TextView.isSuggestionsEnabled
        mActivityRule.runOnUiThread(
                () -> mTextView.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL));
        assertTrue(mTextView.isSuggestionsEnabled());

        mActivityRule.runOnUiThread(
                () -> mTextView.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT));
        assertTrue(mTextView.isSuggestionsEnabled());

        mActivityRule.runOnUiThread(
                () -> mTextView.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE));
        assertTrue(mTextView.isSuggestionsEnabled());

        mActivityRule.runOnUiThread(
                () -> mTextView.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE));
        assertTrue(mTextView.isSuggestionsEnabled());

        mActivityRule.runOnUiThread(
                () -> mTextView.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT));
        assertTrue(mTextView.isSuggestionsEnabled());

        // and not on any other type variation
        mActivityRule.runOnUiThread(
                () -> mTextView.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS));
        assertFalse(mTextView.isSuggestionsEnabled());

        mActivityRule.runOnUiThread(
                () -> mTextView.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_FILTER));
        assertFalse(mTextView.isSuggestionsEnabled());

        mActivityRule.runOnUiThread(
                () -> mTextView.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD));
        assertFalse(mTextView.isSuggestionsEnabled());

        mActivityRule.runOnUiThread(
                () -> mTextView.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME));
        assertFalse(mTextView.isSuggestionsEnabled());

        mActivityRule.runOnUiThread(
                () -> mTextView.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PHONETIC));
        assertFalse(mTextView.isSuggestionsEnabled());

        mActivityRule.runOnUiThread(
                () -> mTextView.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS));
        assertFalse(mTextView.isSuggestionsEnabled());

        mActivityRule.runOnUiThread(
                () -> mTextView.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI));
        assertFalse(mTextView.isSuggestionsEnabled());

        mActivityRule.runOnUiThread(
                () -> mTextView.setInputType(
                        InputType.TYPE_CLASS_TEXT |
                                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD));
        assertFalse(mTextView.isSuggestionsEnabled());

        mActivityRule.runOnUiThread(
                () -> mTextView.setInputType(
                        InputType.TYPE_CLASS_TEXT |
                                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS));
        assertFalse(mTextView.isSuggestionsEnabled());

        mActivityRule.runOnUiThread(
                () -> mTextView.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD));
        assertFalse(mTextView.isSuggestionsEnabled());
    }

    @Test
    public void testAccessLetterSpacing() throws Throwable {
        mTextView = findTextView(R.id.textview_text);
        assertEquals(0.0f, mTextView.getLetterSpacing(), 0.0f);

        final CharSequence text = mTextView.getText();
        final int textLength = text.length();

        // Get advance widths of each character at the default letter spacing
        final float[] initialWidths = new float[textLength];
        mTextView.getPaint().getTextWidths(text.toString(), initialWidths);

        // Get advance widths of each character at letter spacing = 1.0f
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, mTextView,
                () -> mTextView.setLetterSpacing(1.0f));
        assertEquals(1.0f, mTextView.getLetterSpacing(), 0.0f);
        final float[] singleWidths = new float[textLength];
        mTextView.getPaint().getTextWidths(text.toString(), singleWidths);

        // Get advance widths of each character at letter spacing = 2.0f
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, mTextView,
                () -> mTextView.setLetterSpacing(2.0f));
        assertEquals(2.0f, mTextView.getLetterSpacing(), 0.0f);
        final float[] doubleWidths = new float[textLength];
        mTextView.getPaint().getTextWidths(text.toString(), doubleWidths);

        // Since letter spacing setter treats the parameter as EM units, and we don't have
        // a way to convert EMs into pixels, go over the three arrays of advance widths and
        // test that the extra advance width at letter spacing 2.0f is double the extra
        // advance width at letter spacing 1.0f.
        for (int i = 0; i < textLength; i++) {
            float singleWidthDelta = singleWidths[i] - initialWidths[i];
            float doubleWidthDelta = doubleWidths[i] - initialWidths[i];
            assertEquals("At index " + i + " initial is " + initialWidths[i] +
                ", single is " + singleWidths[i] + " and double is " + doubleWidths[i],
                    singleWidthDelta * 2.0f, doubleWidthDelta, 0.05f);
        }
    }

    @Test
    public void testTextIsSelectableFocusAndOnClick() throws Throwable {
        // Prepare a focusable TextView with an onClickListener attached.
        final View.OnClickListener mockOnClickListener = mock(View.OnClickListener.class);
        final int safeDoubleTapTimeout = ViewConfiguration.getDoubleTapTimeout() + 1;
        mActivityRule.runOnUiThread(() -> {
            // set up a dummy focusable so that initial focus doesn't go to our test textview
            LinearLayout top = new LinearLayout(mActivity);
            TextView dummy = new TextView(mActivity);
            dummy.setFocusableInTouchMode(true);
            top.addView(dummy, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            mTextView = new TextView(mActivity);
            mTextView.setText("...text 11:11. some more text is in here...");
            mTextView.setFocusable(true);
            mTextView.setOnClickListener(mockOnClickListener);
            top.addView(mTextView, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            mActivity.setContentView(top);
        });
        mInstrumentation.waitForIdleSync();
        assertTrue(mTextView.isFocusable());
        assertFalse(mTextView.isTextSelectable());
        assertFalse(mTextView.isFocusableInTouchMode());
        assertFalse(mTextView.isFocused());
        assertFalse(mTextView.isInTouchMode());

        // First tap on the view triggers onClick() but does not focus the TextView.
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mTextView);
        SystemClock.sleep(safeDoubleTapTimeout);
        assertTrue(mTextView.isInTouchMode());
        assertFalse(mTextView.isFocused());
        verify(mockOnClickListener, times(1)).onClick(mTextView);
        reset(mockOnClickListener);
        // So does the second tap.
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mTextView);
        SystemClock.sleep(safeDoubleTapTimeout);
        assertTrue(mTextView.isInTouchMode());
        assertFalse(mTextView.isFocused());
        verify(mockOnClickListener, times(1)).onClick(mTextView);

        mActivityRule.runOnUiThread(() -> mTextView.setTextIsSelectable(true));
        mInstrumentation.waitForIdleSync();
        assertTrue(mTextView.isFocusable());
        assertTrue(mTextView.isTextSelectable());
        assertTrue(mTextView.isFocusableInTouchMode());
        assertFalse(mTextView.isFocused());

        // First tap on the view focuses the TextView but does not trigger onClick().
        reset(mockOnClickListener);
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mTextView);
        SystemClock.sleep(safeDoubleTapTimeout);
        assertTrue(mTextView.isInTouchMode());
        assertTrue(mTextView.isFocused());
        verify(mockOnClickListener, never()).onClick(mTextView);
        reset(mockOnClickListener);
        // The second tap triggers onClick() and keeps the focus.
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mTextView);
        SystemClock.sleep(safeDoubleTapTimeout);
        assertTrue(mTextView.isInTouchMode());
        assertTrue(mTextView.isFocused());
        verify(mockOnClickListener, times(1)).onClick(mTextView);
    }

    private void verifyGetOffsetForPosition(final int x, final int y) {
        final int actual = mTextView.getOffsetForPosition(x, y);

        final Layout layout = mTextView.getLayout();
        if (layout == null) {
            assertEquals("For [" + x + ", " + y + "]", -1, actual);
            return;
        }

        // Get the line which corresponds to the Y position
        final int line = layout.getLineForVertical(y + mTextView.getScrollY());
        // Get the offset in that line that corresponds to the X position
        final int expected = layout.getOffsetForHorizontal(line, x + mTextView.getScrollX());
        assertEquals("For [" + x + ", " + y + "]", expected, actual);
    }

    @Test
    public void testGetOffsetForPosition() throws Throwable {
        mTextView = findTextView(R.id.textview_text);
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, mTextView, () -> {
            mTextView.setText(LONG_TEXT);
            mTextView.setPadding(0, 0, 0, 0);
        });

        assertNotNull(mTextView.getLayout());
        final int viewWidth = mTextView.getWidth();
        final int viewHeight = mTextView.getHeight();
        final int lineHeight = mTextView.getLineHeight();

        verifyGetOffsetForPosition(0, 0);
        verifyGetOffsetForPosition(0, viewHeight / 2);
        verifyGetOffsetForPosition(viewWidth / 3, lineHeight / 2);
        verifyGetOffsetForPosition(viewWidth / 2, viewHeight / 2);
        verifyGetOffsetForPosition(viewWidth, viewHeight);
    }

    @UiThreadTest
    @Test
    public void testOnResolvePointerIcon() throws InterruptedException {
        final TextView selectableTextView = findTextView(R.id.textview_pointer);
        final MotionEvent event = createMouseHoverEvent(selectableTextView);

        // A selectable view shows the I beam
        selectableTextView.setTextIsSelectable(true);

        assertEquals(PointerIcon.getSystemIcon(mActivity, PointerIcon.TYPE_TEXT),
                selectableTextView.onResolvePointerIcon(event, 0));
        selectableTextView.setTextIsSelectable(false);

        // A clickable view shows the hand
        selectableTextView.setLinksClickable(true);
        SpannableString builder = new SpannableString("hello world");
        selectableTextView.setText(builder, BufferType.SPANNABLE);
        Spannable text = (Spannable) selectableTextView.getText();
        text.setSpan(
                new ClickableSpan() {
                    @Override
                    public void onClick(View widget) {

                    }
                }, 0, text.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);

        assertEquals(PointerIcon.getSystemIcon(mActivity, PointerIcon.TYPE_HAND),
                selectableTextView.onResolvePointerIcon(event, 0));

        // A selectable & clickable view shows hand
        selectableTextView.setTextIsSelectable(true);

        assertEquals(PointerIcon.getSystemIcon(mActivity, PointerIcon.TYPE_HAND),
                selectableTextView.onResolvePointerIcon(event, 0));

        // An editable view shows the I-beam
        final TextView editableTextView = new EditText(mActivity);

        assertEquals(PointerIcon.getSystemIcon(mActivity, PointerIcon.TYPE_TEXT),
                editableTextView.onResolvePointerIcon(event, 0));
    }

    @Test
    public void testClickableSpanOnClickSingleTapInside() throws Throwable {
        ClickableSpanTestDetails spanDetails = prepareAndRetrieveClickableSpanDetails();
        CtsTouchUtils.emulateTapOnView(mInstrumentation, mTextView, spanDetails.mXPosInside,
                spanDetails.mYPosInside);
        verify(spanDetails.mClickableSpan, times(1)).onClick(mTextView);
    }

    @Test
    public void testClickableSpanOnClickDoubleTapInside() throws Throwable {
        ClickableSpanTestDetails spanDetails = prepareAndRetrieveClickableSpanDetails();
        CtsTouchUtils.emulateDoubleTapOnView(mInstrumentation, mTextView, spanDetails.mXPosInside,
                spanDetails.mYPosInside);
        verify(spanDetails.mClickableSpan, times(2)).onClick(mTextView);
    }

    @Test
    public void testClickableSpanOnClickSingleTapOutside() throws Throwable {
        ClickableSpanTestDetails spanDetails = prepareAndRetrieveClickableSpanDetails();
        CtsTouchUtils.emulateTapOnView(mInstrumentation, mTextView, spanDetails.mXPosOutside,
                spanDetails.mYPosOutside);
        verify(spanDetails.mClickableSpan, never()).onClick(mTextView);
    }

    @Test
    public void testClickableSpanOnClickDragOutside() throws Throwable {
        ClickableSpanTestDetails spanDetails = prepareAndRetrieveClickableSpanDetails();
        final int[] viewOnScreenXY = new int[2];
        mTextView.getLocationOnScreen(viewOnScreenXY);

        SparseArray<Point> swipeCoordinates = new SparseArray<>();
        swipeCoordinates.put(0, new Point(viewOnScreenXY[0] + spanDetails.mXPosOutside,
                viewOnScreenXY[1] + spanDetails.mYPosOutside));
        swipeCoordinates.put(1, new Point(viewOnScreenXY[0] + spanDetails.mXPosOutside + 50,
                viewOnScreenXY[1] + spanDetails.mYPosOutside + 50));
        CtsTouchUtils.emulateDragGesture(mInstrumentation, swipeCoordinates);
        verify(spanDetails.mClickableSpan, never()).onClick(mTextView);
    }

    @UiThreadTest
    @Test
    public void testOnInitializeA11yNodeInfo_populatesHintTextProperly() {
        final TextView textView = new TextView(mActivity);
        textView.setText("", BufferType.EDITABLE);
        final String hintText = "Hint text";
        textView.setHint(hintText);
        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
        textView.onInitializeAccessibilityNodeInfo(info);
        assertTrue("Hint text flag set incorrectly for accessibility", info.isShowingHintText());
        assertTrue("Hint text not showing as accessibility text",
                TextUtils.equals(hintText, info.getText()));
        assertTrue("Hint text not provided to accessibility",
                TextUtils.equals(hintText, info.getHintText()));

        final String nonHintText = "Something else";
        textView.setText(nonHintText, BufferType.EDITABLE);
        textView.onInitializeAccessibilityNodeInfo(info);
        assertFalse("Hint text flag set incorrectly for accessibility", info.isShowingHintText());
        assertTrue("Text not provided to accessibility",
                TextUtils.equals(nonHintText, info.getText()));
        assertTrue("Hint text not provided to accessibility",
                TextUtils.equals(hintText, info.getHintText()));
    }

    @Test
    public void testAutosizeWithMaxLines_shouldNotThrowException() throws Throwable {
        // the layout contains an instance of CustomTextViewWithTransformationMethod
        final TextView textView = (TextView) mActivity.getLayoutInflater()
                .inflate(R.layout.textview_autosize_maxlines, null);
        assertTrue(textView instanceof CustomTextViewWithTransformationMethod);
        assertEquals(1, textView.getMaxLines());
        assertEquals(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM, textView.getAutoSizeTextType());
        assertTrue(textView.getTransformationMethod() instanceof SingleLineTransformationMethod);
    }

    public static class CustomTextViewWithTransformationMethod extends TextView {
        public CustomTextViewWithTransformationMethod(Context context) {
            super(context);
            init();
        }

        public CustomTextViewWithTransformationMethod(Context context,
                @Nullable AttributeSet attrs) {
            super(context, attrs);
            init();
        }

        public CustomTextViewWithTransformationMethod(Context context,
                @Nullable AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);
            init();
        }

        public CustomTextViewWithTransformationMethod(Context context,
                @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
            super(context, attrs, defStyleAttr, defStyleRes);
            init();
        }

        private void init() {
            setTransformationMethod(new SingleLineTransformationMethod());
        }
    }

    @Test
    public void testAutoSizeCallers_setText() throws Throwable {
        final TextView autoSizeTextView = prepareAndRetrieveAutoSizeTestData(
                R.id.textview_autosize_uniform, false);

        // Configure layout params and auto-size both in pixels to dodge flakiness on different
        // devices.
        final LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                200, 200);
        mActivityRule.runOnUiThread(() -> {
            autoSizeTextView.setLayoutParams(layoutParams);
            autoSizeTextView.setAutoSizeTextTypeUniformWithConfiguration(
                    1, 5000, 1, TypedValue.COMPLEX_UNIT_PX);
        });
        mInstrumentation.waitForIdleSync();

        final String initialText = "13characters ";
        final StringBuilder textToSet = new StringBuilder().append(initialText);
        float initialSize = 0;

        // As we add characters the text size shrinks.
        for (int i = 0; i < 10; i++) {
            mActivityRule.runOnUiThread(() ->
                    autoSizeTextView.setText(textToSet.toString()));
            mInstrumentation.waitForIdleSync();
            float expectedLargerSize = autoSizeTextView.getTextSize();
            if (i == 0) {
                initialSize = expectedLargerSize;
            }

            textToSet.append(initialText);
            mActivityRule.runOnUiThread(() ->
                    autoSizeTextView.setText(textToSet.toString()));
            mInstrumentation.waitForIdleSync();

            assertTrue(expectedLargerSize >= autoSizeTextView.getTextSize());
        }
        assertTrue(initialSize > autoSizeTextView.getTextSize());

        initialSize = Integer.MAX_VALUE;
        // As we remove characters the text size expands.
        for (int i = 9; i >= 0; i--) {
            mActivityRule.runOnUiThread(() ->
                    autoSizeTextView.setText(textToSet.toString()));
            mInstrumentation.waitForIdleSync();
            float expectedSmallerSize = autoSizeTextView.getTextSize();
            if (i == 9) {
                initialSize = expectedSmallerSize;
            }

            textToSet.replace((textToSet.length() - initialText.length()), textToSet.length(), "");
            mActivityRule.runOnUiThread(() ->
                    autoSizeTextView.setText(textToSet.toString()));
            mInstrumentation.waitForIdleSync();

            assertTrue(autoSizeTextView.getTextSize() >= expectedSmallerSize);
        }
        assertTrue(autoSizeTextView.getTextSize() > initialSize);
    }

    @Test
    public void testAutoSize_setEllipsize() throws Throwable {
        final TextView textView = (TextView) mActivity.findViewById(
                R.id.textview_autosize_uniform_predef_sizes);
        final int initialAutoSizeType = textView.getAutoSizeTextType();
        final int initialMinTextSize = textView.getAutoSizeMinTextSize();
        final int initialMaxTextSize = textView.getAutoSizeMaxTextSize();
        final int initialAutoSizeGranularity = textView.getAutoSizeStepGranularity();
        final int initialSizes = textView.getAutoSizeTextAvailableSizes().length;

        assertEquals(null, textView.getEllipsize());
        // Verify styled attributes.
        assertEquals(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM, initialAutoSizeType);
        assertNotEquals(-1, initialMinTextSize);
        assertNotEquals(-1, initialMaxTextSize);
        // Because this TextView has been configured to use predefined sizes.
        assertEquals(-1, initialAutoSizeGranularity);
        assertNotEquals(0, initialSizes);

        final TextUtils.TruncateAt newEllipsizeValue = TextUtils.TruncateAt.END;
        mActivityRule.runOnUiThread(() ->
                textView.setEllipsize(newEllipsizeValue));
        mInstrumentation.waitForIdleSync();
        assertEquals(newEllipsizeValue, textView.getEllipsize());
        // Beside the ellipsis no auto-size attribute has changed.
        assertEquals(initialAutoSizeType, textView.getAutoSizeTextType());
        assertEquals(initialMinTextSize, textView.getAutoSizeMinTextSize());
        assertEquals(initialMaxTextSize, textView.getAutoSizeMaxTextSize());
        assertEquals(initialAutoSizeGranularity, textView.getAutoSizeStepGranularity());
        assertEquals(initialSizes, textView.getAutoSizeTextAvailableSizes().length);
    }

    @Test
    public void testEllipsize_setAutoSize() throws Throwable {
        TextView textView = findTextView(R.id.textview_text);
        final TextUtils.TruncateAt newEllipsizeValue = TextUtils.TruncateAt.END;
        mActivityRule.runOnUiThread(() ->
                textView.setEllipsize(newEllipsizeValue));
        mInstrumentation.waitForIdleSync();
        assertEquals(newEllipsizeValue, textView.getEllipsize());
        assertEquals(TextView.AUTO_SIZE_TEXT_TYPE_NONE, textView.getAutoSizeTextType());
        assertEquals(-1, textView.getAutoSizeMinTextSize());
        assertEquals(-1, textView.getAutoSizeMaxTextSize());
        assertEquals(-1, textView.getAutoSizeStepGranularity());
        assertEquals(0, textView.getAutoSizeTextAvailableSizes().length);

        mActivityRule.runOnUiThread(() ->
                textView.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM));
        mInstrumentation.waitForIdleSync();
        assertEquals(newEllipsizeValue, textView.getEllipsize());
        assertEquals(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM, textView.getAutoSizeTextType());
        // The auto-size defaults have been used.
        assertNotEquals(-1, textView.getAutoSizeMinTextSize());
        assertNotEquals(-1, textView.getAutoSizeMaxTextSize());
        assertNotEquals(-1, textView.getAutoSizeStepGranularity());
        assertNotEquals(0, textView.getAutoSizeTextAvailableSizes().length);
    }

    @Test
    public void testAutoSizeCallers_setTransformationMethod() throws Throwable {
        final TextView autoSizeTextView = prepareAndRetrieveAutoSizeTestData(
                R.id.textview_autosize_uniform, false);
        // Mock transformation method to return the duplicated input text in order to measure
        // auto-sizing.
        TransformationMethod duplicateTextTransformationMethod = mock(TransformationMethod.class);
        when(duplicateTextTransformationMethod
                .getTransformation(any(CharSequence.class), any(View.class)))
                .thenAnswer(invocation -> {
                    CharSequence source = (CharSequence) invocation.getArguments()[0];
                    return new StringBuilder().append(source).append(source).toString();
                });

        mActivityRule.runOnUiThread(() ->
                autoSizeTextView.setTransformationMethod(null));
        mInstrumentation.waitForIdleSync();
        final float initialTextSize = autoSizeTextView.getTextSize();
        mActivityRule.runOnUiThread(() ->
                autoSizeTextView.setTransformationMethod(duplicateTextTransformationMethod));
        mInstrumentation.waitForIdleSync();

        assertTrue(autoSizeTextView.getTextSize() < initialTextSize);
    }

    @Test
    public void testAutoSizeCallers_setCompoundDrawables() throws Throwable {
        final TextView autoSizeTextView = prepareAndRetrieveAutoSizeTestData(
                R.id.textview_autosize_uniform, false);
        final float initialTextSize = autoSizeTextView.getTextSize();
        Drawable drawable = TestUtils.getDrawable(mActivity, R.drawable.red);
        drawable.setBounds(0, 0, autoSizeTextView.getWidth() / 3, autoSizeTextView.getHeight() / 3);
        mActivityRule.runOnUiThread(() ->
                autoSizeTextView.setCompoundDrawables(drawable, drawable, drawable, drawable));
        mInstrumentation.waitForIdleSync();

        assertTrue(autoSizeTextView.getTextSize() < initialTextSize);
    }

    @Test
    public void testAutoSizeCallers_setCompoundDrawablesRelative() throws Throwable {
        final TextView autoSizeTextView = prepareAndRetrieveAutoSizeTestData(
                R.id.textview_autosize_uniform, false);
        final float initialTextSize = autoSizeTextView.getTextSize();
        Drawable drawable = TestUtils.getDrawable(mActivity, R.drawable.red);
        drawable.setBounds(0, 0, autoSizeTextView.getWidth() / 3, autoSizeTextView.getHeight() / 3);
        mActivityRule.runOnUiThread(() -> autoSizeTextView.setCompoundDrawablesRelative(
                drawable, drawable, drawable, drawable));
        mInstrumentation.waitForIdleSync();

        assertTrue(autoSizeTextView.getTextSize() < initialTextSize);
    }

    @Test
    public void testAutoSizeCallers_setCompoundDrawablePadding() throws Throwable {
        final TextView autoSizeTextView = prepareAndRetrieveAutoSizeTestData(
                R.id.textview_autosize_uniform, false);
        // Prepare a larger layout in order not to hit the min value easily.
        mActivityRule.runOnUiThread(() -> {
            autoSizeTextView.setWidth(autoSizeTextView.getWidth() * 2);
            autoSizeTextView.setHeight(autoSizeTextView.getHeight() * 2);
        });
        mInstrumentation.waitForIdleSync();
        // Setup the drawables before setting their padding in order to modify the available
        // space and trigger a resize.
        Drawable drawable = TestUtils.getDrawable(mActivity, R.drawable.red);
        drawable.setBounds(0, 0, autoSizeTextView.getWidth() / 4, autoSizeTextView.getHeight() / 4);
        mActivityRule.runOnUiThread(() -> autoSizeTextView.setCompoundDrawables(
                drawable, drawable, drawable, drawable));
        mInstrumentation.waitForIdleSync();
        final float initialTextSize = autoSizeTextView.getTextSize();
        mActivityRule.runOnUiThread(() -> autoSizeTextView.setCompoundDrawablePadding(
                autoSizeTextView.getCompoundDrawablePadding() + 10));
        mInstrumentation.waitForIdleSync();

        assertTrue(autoSizeTextView.getTextSize() < initialTextSize);
    }

    @Test
    public void testAutoSizeCallers_setPadding() throws Throwable {
        final TextView autoSizeTextView = prepareAndRetrieveAutoSizeTestData(
                R.id.textview_autosize_uniform, false);
        final float initialTextSize = autoSizeTextView.getTextSize();
        mActivityRule.runOnUiThread(() -> autoSizeTextView.setPadding(
                autoSizeTextView.getWidth() / 3, autoSizeTextView.getHeight() / 3,
                autoSizeTextView.getWidth() / 3, autoSizeTextView.getHeight() / 3));
        mInstrumentation.waitForIdleSync();

        assertTrue(autoSizeTextView.getTextSize() < initialTextSize);
    }

    @Test
    public void testAutoSizeCallers_setPaddingRelative() throws Throwable {
        final TextView autoSizeTextView = prepareAndRetrieveAutoSizeTestData(
                R.id.textview_autosize_uniform, false);
        final float initialTextSize = autoSizeTextView.getTextSize();

        mActivityRule.runOnUiThread(() -> autoSizeTextView.setPaddingRelative(
                autoSizeTextView.getWidth() / 3, autoSizeTextView.getHeight() / 3,
                autoSizeTextView.getWidth() / 3, autoSizeTextView.getHeight() / 3));
        mInstrumentation.waitForIdleSync();

        assertTrue(autoSizeTextView.getTextSize() < initialTextSize);
    }

    @Test
    public void testAutoSizeCallers_setTextScaleX() throws Throwable {
        final TextView autoSizeTextView = prepareAndRetrieveAutoSizeTestData(
                R.id.textview_autosize_uniform, false);
        final float initialTextSize = autoSizeTextView.getTextSize();

        mActivityRule.runOnUiThread(() ->
                autoSizeTextView.setTextScaleX(autoSizeTextView.getTextScaleX() * 4.5f));
        mInstrumentation.waitForIdleSync();
        final float changedTextSize = autoSizeTextView.getTextSize();

        assertTrue(changedTextSize < initialTextSize);

        mActivityRule.runOnUiThread(() ->
                autoSizeTextView.setTextScaleX(autoSizeTextView.getTextScaleX()));
        mInstrumentation.waitForIdleSync();

        assertEquals(changedTextSize, autoSizeTextView.getTextSize(), 0f);
    }

    @Test
    public void testAutoSizeCallers_setTypeface() throws Throwable {
        final TextView autoSizeTextView = prepareAndRetrieveAutoSizeTestData(
                R.id.textview_autosize_uniform, false);
        mActivityRule.runOnUiThread(() ->
                autoSizeTextView.setText("The typeface change needs a bit more text then "
                        + "the default used for this batch of tests in order to get to resize text."
                        + " The resize function is always called but even with different typefaces "
                        + "there may not be a need to resize text because it just fits. The longer "
                        + "the text, the higher the chance for a resize. And here is yet another "
                        + "sentence to make sure this test is not flaky. Not flaky at all."));
        mInstrumentation.waitForIdleSync();
        final float initialTextSize = autoSizeTextView.getTextSize();

        mActivityRule.runOnUiThread(() -> {
            Typeface differentTypeface = Typeface.MONOSPACE;
            if (autoSizeTextView.getTypeface() == Typeface.MONOSPACE) {
                differentTypeface = Typeface.SANS_SERIF;
            }
            autoSizeTextView.setTypeface(differentTypeface);
        });
        mInstrumentation.waitForIdleSync();
        final float changedTextSize = autoSizeTextView.getTextSize();

        // Don't really know if it is larger or smaller (depends on the typeface chosen above),
        // but it should definitely have changed.
        assertNotEquals(initialTextSize, changedTextSize, 0f);

        mActivityRule.runOnUiThread(() ->
                autoSizeTextView.setTypeface(autoSizeTextView.getTypeface()));
        mInstrumentation.waitForIdleSync();

        assertEquals(changedTextSize, autoSizeTextView.getTextSize(), 0f);
    }

    @Test
    public void testAutoSizeCallers_setLetterSpacing() throws Throwable {
        final TextView autoSizeTextView = prepareAndRetrieveAutoSizeTestData(
                R.id.textview_autosize_uniform, false);
        final float initialTextSize = autoSizeTextView.getTextSize();

        mActivityRule.runOnUiThread(() ->
                // getLetterSpacing() could return 0, make sure there is enough of a difference to
                // trigger auto-size.
                autoSizeTextView.setLetterSpacing(
                        autoSizeTextView.getLetterSpacing() * 1.5f + 4.5f));
        mInstrumentation.waitForIdleSync();
        final float changedTextSize = autoSizeTextView.getTextSize();

        assertTrue(changedTextSize < initialTextSize);

        mActivityRule.runOnUiThread(() ->
                autoSizeTextView.setLetterSpacing(autoSizeTextView.getLetterSpacing()));
        mInstrumentation.waitForIdleSync();

        assertEquals(changedTextSize, autoSizeTextView.getTextSize(), 0f);
    }

    @Test
    public void testAutoSizeCallers_setHorizontallyScrolling() throws Throwable {
        final TextView autoSizeTextView = prepareAndRetrieveAutoSizeTestData(
                R.id.textview_autosize_uniform, false);
        // Verify that we do not have horizontal scrolling turned on.
        assertTrue(!autoSizeTextView.getHorizontallyScrolling());

        final float initialTextSize = autoSizeTextView.getTextSize();
        mActivityRule.runOnUiThread(() -> autoSizeTextView.setHorizontallyScrolling(true));
        mInstrumentation.waitForIdleSync();
        assertTrue(autoSizeTextView.getTextSize() > initialTextSize);

        mActivityRule.runOnUiThread(() -> autoSizeTextView.setHorizontallyScrolling(false));
        mInstrumentation.waitForIdleSync();
        assertEquals(initialTextSize, autoSizeTextView.getTextSize(), 0f);
    }

    @Test
    public void testAutoSizeCallers_setMaxLines() throws Throwable {
        final TextView autoSizeTextView = prepareAndRetrieveAutoSizeTestData(
                R.id.textview_autosize_uniform, false);
        // Configure layout params and auto-size both in pixels to dodge flakiness on different
        // devices.
        final LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                200, 200);
        final String text = "one\ntwo\nthree\nfour\nfive\nsix\nseven\neight\nnine\nten";
        mActivityRule.runOnUiThread(() -> {
            autoSizeTextView.setLayoutParams(layoutParams);
            autoSizeTextView.setAutoSizeTextTypeUniformWithConfiguration(
                    1 /* autoSizeMinTextSize */,
                    5000 /* autoSizeMaxTextSize */,
                    1 /* autoSizeStepGranularity */,
                    TypedValue.COMPLEX_UNIT_PX);
            autoSizeTextView.setText(text);
        });
        mInstrumentation.waitForIdleSync();

        float initialSize = 0;
        for (int i = 1; i < 10; i++) {
            final int maxLines = i;
            mActivityRule.runOnUiThread(() -> autoSizeTextView.setMaxLines(maxLines));
            mInstrumentation.waitForIdleSync();
            float expectedSmallerSize = autoSizeTextView.getTextSize();
            if (i == 1) {
                initialSize = expectedSmallerSize;
            }

            mActivityRule.runOnUiThread(() -> autoSizeTextView.setMaxLines(maxLines + 1));
            mInstrumentation.waitForIdleSync();
            assertTrue(expectedSmallerSize <= autoSizeTextView.getTextSize());
        }
        assertTrue(initialSize < autoSizeTextView.getTextSize());

        initialSize = Integer.MAX_VALUE;
        for (int i = 10; i > 1; i--) {
            final int maxLines = i;
            mActivityRule.runOnUiThread(() -> autoSizeTextView.setMaxLines(maxLines));
            mInstrumentation.waitForIdleSync();
            float expectedLargerSize = autoSizeTextView.getTextSize();
            if (i == 10) {
                initialSize = expectedLargerSize;
            }

            mActivityRule.runOnUiThread(() -> autoSizeTextView.setMaxLines(maxLines - 1));
            mInstrumentation.waitForIdleSync();
            assertTrue(expectedLargerSize >= autoSizeTextView.getTextSize());
        }
        assertTrue(initialSize > autoSizeTextView.getTextSize());
    }

    @Test
    public void testAutoSizeCallers_setMaxHeight() throws Throwable {
        final TextView autoSizeTextView = prepareAndRetrieveAutoSizeTestData(
                R.id.textview_autosize_uniform, true);
        // Do not force exact height only.
        final LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                200,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        mActivityRule.runOnUiThread(() -> autoSizeTextView.setLayoutParams(layoutParams));
        mInstrumentation.waitForIdleSync();
        final float initialTextSize = autoSizeTextView.getTextSize();
        mActivityRule.runOnUiThread(() -> autoSizeTextView.setMaxHeight(
                autoSizeTextView.getHeight() / 4));
        mInstrumentation.waitForIdleSync();

        assertTrue(autoSizeTextView.getTextSize() < initialTextSize);
    }

    @Test
    public void testAutoSizeCallers_setHeight() throws Throwable {
        final TextView autoSizeTextView = prepareAndRetrieveAutoSizeTestData(
                R.id.textview_autosize_uniform, true);
        // Do not force exact height only.
        final LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                200,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        mActivityRule.runOnUiThread(() -> autoSizeTextView.setLayoutParams(layoutParams));
        mInstrumentation.waitForIdleSync();
        final float initialTextSize = autoSizeTextView.getTextSize();
        mActivityRule.runOnUiThread(() -> autoSizeTextView.setHeight(
                autoSizeTextView.getHeight() / 4));
        mInstrumentation.waitForIdleSync();

        assertTrue(autoSizeTextView.getTextSize() < initialTextSize);
    }

    @Test
    public void testAutoSizeCallers_setLines() throws Throwable {
        final TextView autoSizeTextView = prepareAndRetrieveAutoSizeTestData(
                R.id.textview_autosize_uniform, false);
        final float initialTextSize = autoSizeTextView.getTextSize();
        mActivityRule.runOnUiThread(() -> autoSizeTextView.setLines(1));
        mInstrumentation.waitForIdleSync();

        assertTrue(autoSizeTextView.getTextSize() < initialTextSize);
    }

    @Test
    public void testAutoSizeCallers_setMaxWidth() throws Throwable {
        final TextView autoSizeTextView = prepareAndRetrieveAutoSizeTestData(
                R.id.textview_autosize_uniform, true);
        // Do not force exact width only.
        final LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                200);
        mActivityRule.runOnUiThread(() -> autoSizeTextView.setLayoutParams(layoutParams));
        mInstrumentation.waitForIdleSync();
        final float initialTextSize = autoSizeTextView.getTextSize();
        mActivityRule.runOnUiThread(() -> autoSizeTextView.setMaxWidth(
                autoSizeTextView.getWidth() / 4));
        mInstrumentation.waitForIdleSync();

        assertTrue(autoSizeTextView.getTextSize() != initialTextSize);
    }

    @Test
    public void testAutoSizeCallers_setWidth() throws Throwable {
        final TextView autoSizeTextView = prepareAndRetrieveAutoSizeTestData(
                R.id.textview_autosize_uniform, true);
        // Do not force exact width only.
        final LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                200);
        mActivityRule.runOnUiThread(() -> autoSizeTextView.setLayoutParams(layoutParams));
        mInstrumentation.waitForIdleSync();

        final float initialTextSize = autoSizeTextView.getTextSize();
        mActivityRule.runOnUiThread(() -> autoSizeTextView.setWidth(
                autoSizeTextView.getWidth() / 4));
        mInstrumentation.waitForIdleSync();

        assertTrue(autoSizeTextView.getTextSize() != initialTextSize);
    }

    @Test
    public void testAutoSizeCallers_setLineSpacing() throws Throwable {
        final TextView autoSizeTextView = prepareAndRetrieveAutoSizeTestData(
                R.id.textview_autosize_uniform, false);
        final float initialTextSize = autoSizeTextView.getTextSize();

        mActivityRule.runOnUiThread(() -> autoSizeTextView.setLineSpacing(
                autoSizeTextView.getLineSpacingExtra() * 4,
                autoSizeTextView.getLineSpacingMultiplier() * 4));
        mInstrumentation.waitForIdleSync();
        final float changedTextSize = autoSizeTextView.getTextSize();

        assertTrue(changedTextSize < initialTextSize);

        mActivityRule.runOnUiThread(() -> autoSizeTextView.setLineSpacing(
                autoSizeTextView.getLineSpacingExtra(),
                autoSizeTextView.getLineSpacingMultiplier()));
        mInstrumentation.waitForIdleSync();

        assertEquals(changedTextSize, autoSizeTextView.getTextSize(), 0f);
    }

    @Test
    public void testAutoSizeCallers_setTextSizeIsNoOp() throws Throwable {
        final TextView autoSizeTextView = prepareAndRetrieveAutoSizeTestData(
                R.id.textview_autosize_uniform, false);
        final float initialTextSize = autoSizeTextView.getTextSize();

        mActivityRule.runOnUiThread(() -> autoSizeTextView.setTextSize(
                initialTextSize + 123f));
        mInstrumentation.waitForIdleSync();

        assertEquals(initialTextSize, autoSizeTextView.getTextSize(), 0f);
    }

    @Test
    public void testAutoSizeCallers_setHeightForOneLineText() throws Throwable {
        final TextView autoSizeTextView = (TextView) mActivity.findViewById(
                R.id.textview_autosize_basic);
        assertEquals(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM, autoSizeTextView.getAutoSizeTextType());
        final float initialTextSize = autoSizeTextView.getTextSize();
        mActivityRule.runOnUiThread(() -> autoSizeTextView.setHeight(
                autoSizeTextView.getHeight() * 3));
        mInstrumentation.waitForIdleSync();

        assertTrue(autoSizeTextView.getTextSize() > initialTextSize);
    }

    @Test
    public void testAutoSizeUniform_obtainStyledAttributes() {
        DisplayMetrics metrics = mActivity.getResources().getDisplayMetrics();
        TextView autoSizeTextViewUniform = (TextView) mActivity.findViewById(
                R.id.textview_autosize_uniform);

        // The size has been set to 50dp in the layout but this being an AUTO_SIZE_TEXT_TYPE_UNIFORM
        // TextView, the size is considered max size thus the value returned by getSize() in this
        // case should be lower than the one set (given that there is not much available space and
        // the font size is very high). In theory the values could be equal for a different TextView
        // configuration.
        final float sizeSetInPixels = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 50f, metrics);
        assertTrue(autoSizeTextViewUniform.getTextSize() < sizeSetInPixels);
    }

    @Test
    public void testAutoSizeUniform_obtainStyledAttributesUsingPredefinedSizes() {
        DisplayMetrics m = mActivity.getResources().getDisplayMetrics();
        final TextView autoSizeTextViewUniform = (TextView) mActivity.findViewById(
                R.id.textview_autosize_uniform_predef_sizes);

        // In arrays.xml predefined the step sizes as: 10px, 10dp, 10sp, 10pt, 10in and 10mm.
        // TypedValue can not use the math library and instead naively ceils the value by adding
        // 0.5f when obtaining styled attributes. Check TypedValue#complexToDimensionPixelSize(...)
        int[] expectedSizesInPx = new int[] {
                (int) (0.5f + TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, 10f, m)),
                (int) (0.5f + TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10f, m)),
                (int) (0.5f + TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f, m)),
                (int) (0.5f + TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PT, 10f, m)),
                (int) (0.5f + TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_IN, 10f, m)),
                (int) (0.5f + TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, 10f, m))};

        boolean containsValueFromExpectedSizes = false;
        int textSize = (int) autoSizeTextViewUniform.getTextSize();
        for (int i = 0; i < expectedSizesInPx.length; i++) {
            if (expectedSizesInPx[i] == textSize) {
                containsValueFromExpectedSizes = true;
                break;
            }
        }
        assertTrue(containsValueFromExpectedSizes);
    }

    @Test
    public void testAutoSizeUniform_obtainStyledAttributesPredefinedSizesFiltering() {
        TextView autoSizeTextViewUniform = (TextView) mActivity.findViewById(
                R.id.textview_autosize_uniform_predef_sizes_redundant_values);

        // In arrays.xml predefined the step sizes as: 40px, 10px, 10px, 10px, 0dp.
        final int[] expectedSizes = new int[] {10, 40};
        assertArrayEquals(expectedSizes, autoSizeTextViewUniform.getAutoSizeTextAvailableSizes());
    }

    @Test
    public void testAutoSizeUniform_predefinedSizesFilteringAndSorting() throws Throwable {
        mTextView = findTextView(R.id.textview_text);
        assertEquals(TextView.AUTO_SIZE_TEXT_TYPE_NONE, mTextView.getAutoSizeTextType());

        final int[] predefinedSizes = new int[] {400, 0, 10, 40, 10, 10, 0, 0};
        mActivityRule.runOnUiThread(() -> mTextView.setAutoSizeTextTypeUniformWithPresetSizes(
                predefinedSizes, TypedValue.COMPLEX_UNIT_PX));
        mInstrumentation.waitForIdleSync();
        assertArrayEquals(new int[] {10, 40, 400}, mTextView.getAutoSizeTextAvailableSizes());
    }

    @Test(expected = NullPointerException.class)
    public void testAutoSizeUniform_predefinedSizesNullArray() throws Throwable {
        mTextView = findTextView(R.id.textview_text);
        assertEquals(TextView.AUTO_SIZE_TEXT_TYPE_NONE, mTextView.getAutoSizeTextType());

        final int[] predefinedSizes = null;
        mActivityRule.runOnUiThread(() -> mTextView.setAutoSizeTextTypeUniformWithPresetSizes(
                predefinedSizes, TypedValue.COMPLEX_UNIT_PX));
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testAutoSizeUniform_predefinedSizesEmptyArray() throws Throwable {
        mTextView = findTextView(R.id.textview_text);
        assertEquals(TextView.AUTO_SIZE_TEXT_TYPE_NONE, mTextView.getAutoSizeTextType());

        mActivityRule.runOnUiThread(() ->
                mTextView.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM));
        mInstrumentation.waitForIdleSync();

        final int[] defaultSizes = mTextView.getAutoSizeTextAvailableSizes();
        assertNotNull(defaultSizes);
        assertTrue(defaultSizes.length > 0);

        final int[] predefinedSizes = new int[0];
        mActivityRule.runOnUiThread(() -> mTextView.setAutoSizeTextTypeUniformWithPresetSizes(
                predefinedSizes, TypedValue.COMPLEX_UNIT_PX));
        mInstrumentation.waitForIdleSync();

        final int[] newSizes = mTextView.getAutoSizeTextAvailableSizes();
        assertNotNull(defaultSizes);
        assertArrayEquals(defaultSizes, newSizes);
    }

    @Test
    public void testAutoSizeUniform_buildsSizes() throws Throwable {
        TextView autoSizeTextViewUniform = (TextView) mActivity.findViewById(
                R.id.textview_autosize_uniform);

        // Verify that the interval limits are both included.
        mActivityRule.runOnUiThread(() -> autoSizeTextViewUniform
                .setAutoSizeTextTypeUniformWithConfiguration(10, 20, 2,
                        TypedValue.COMPLEX_UNIT_PX));
        mInstrumentation.waitForIdleSync();
        assertArrayEquals(
                new int[] {10, 12, 14, 16, 18, 20},
                autoSizeTextViewUniform.getAutoSizeTextAvailableSizes());

        mActivityRule.runOnUiThread(() -> autoSizeTextViewUniform
                .setAutoSizeTextTypeUniformWithConfiguration(
                        autoSizeTextViewUniform.getAutoSizeMinTextSize(),
                        19,
                        autoSizeTextViewUniform.getAutoSizeStepGranularity(),
                        TypedValue.COMPLEX_UNIT_PX));
        mInstrumentation.waitForIdleSync();
        assertArrayEquals(
                new int[] {10, 12, 14, 16, 18},
                autoSizeTextViewUniform.getAutoSizeTextAvailableSizes());

        mActivityRule.runOnUiThread(() -> autoSizeTextViewUniform
                .setAutoSizeTextTypeUniformWithConfiguration(
                        autoSizeTextViewUniform.getAutoSizeMinTextSize(),
                        21,
                        autoSizeTextViewUniform.getAutoSizeStepGranularity(),
                        TypedValue.COMPLEX_UNIT_PX));
        mInstrumentation.waitForIdleSync();
        assertArrayEquals(
                new int[] {10, 12, 14, 16, 18, 20},
                autoSizeTextViewUniform.getAutoSizeTextAvailableSizes());
    }

    @Test
    public void testAutoSizeUniform_getSetAutoSizeTextDefaults() {
        final TextView textView = new TextView(mActivity);
        assertEquals(TextView.AUTO_SIZE_TEXT_TYPE_NONE, textView.getAutoSizeTextType());
        // Min/Max/Granularity values for auto-sizing are 0 because they are not used.
        assertEquals(-1, textView.getAutoSizeMinTextSize());
        assertEquals(-1, textView.getAutoSizeMaxTextSize());
        assertEquals(-1, textView.getAutoSizeStepGranularity());

        textView.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM);
        assertEquals(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM, textView.getAutoSizeTextType());
        // Min/Max default values for auto-sizing XY have been loaded.
        final int minSize = textView.getAutoSizeMinTextSize();
        final int maxSize = textView.getAutoSizeMaxTextSize();
        assertTrue(0 < minSize);
        assertTrue(minSize < maxSize);
        assertNotEquals(0, textView.getAutoSizeStepGranularity());

        textView.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE);
        assertEquals(TextView.AUTO_SIZE_TEXT_TYPE_NONE, textView.getAutoSizeTextType());
        // Min/Max values for auto-sizing XY have been cleared.
        assertEquals(-1, textView.getAutoSizeMinTextSize());
        assertEquals(-1, textView.getAutoSizeMaxTextSize());
        assertEquals(-1, textView.getAutoSizeStepGranularity());
    }

    @Test
    public void testAutoSizeUniform_getSetAutoSizeStepGranularity() {
        final TextView textView = new TextView(mActivity);
        assertEquals(TextView.AUTO_SIZE_TEXT_TYPE_NONE, textView.getAutoSizeTextType());
        final int initialValue = -1;
        assertEquals(initialValue, textView.getAutoSizeStepGranularity());

        textView.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM);
        assertEquals(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM, textView.getAutoSizeTextType());
        final int defaultValue = 1; // 1px.
        // If the auto-size type is AUTO_SIZE_TEXT_TYPE_UNIFORM then it means textView went through
        // the auto-size setup and given that 0 is an invalid value it changed it to the default.
        assertEquals(defaultValue, textView.getAutoSizeStepGranularity());

        final int newValue = 33;
        textView.setAutoSizeTextTypeUniformWithConfiguration(
                textView.getAutoSizeMinTextSize(),
                textView.getAutoSizeMaxTextSize(),
                newValue,
                TypedValue.COMPLEX_UNIT_PX);
        assertEquals(newValue, textView.getAutoSizeStepGranularity());
    }

    @Test
    public void testAutoSizeUniform_getSetAutoSizeMinTextSize() {
        final TextView textView = new TextView(mActivity);
        textView.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM);
        assertEquals(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM, textView.getAutoSizeTextType());
        final int minSize = textView.getAutoSizeMinTextSize();
        assertNotEquals(0, minSize);
        final int maxSize = textView.getAutoSizeMaxTextSize();
        assertNotEquals(0, maxSize);

        // This is just a test check to verify the next assertions. If this fails it is a problem
        // of this test setup (we need at least 2 units).
        assertTrue((maxSize - minSize) > 1);
        final int newMinSize = maxSize - 1;
        textView.setAutoSizeTextTypeUniformWithConfiguration(
                newMinSize,
                textView.getAutoSizeMaxTextSize(),
                textView.getAutoSizeStepGranularity(),
                TypedValue.COMPLEX_UNIT_PX);

        assertEquals(newMinSize, textView.getAutoSizeMinTextSize());
        // Max size has not changed.
        assertEquals(maxSize, textView.getAutoSizeMaxTextSize());

        textView.setAutoSizeTextTypeUniformWithConfiguration(
                newMinSize,
                newMinSize + 10,
                textView.getAutoSizeStepGranularity(),
                TypedValue.COMPLEX_UNIT_SP);

        // It does not matter which unit has been used to set the min size, the getter always
        // returns it in pixels.
        assertEquals(Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, newMinSize,
                mActivity.getResources().getDisplayMetrics())), textView.getAutoSizeMinTextSize());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAutoSizeUniform_throwsException_whenMaxLessThanMin() {
        final TextView textView = new TextView(mActivity);
        textView.setAutoSizeTextTypeUniformWithConfiguration(
                10, 9, 1, TypedValue.COMPLEX_UNIT_SP);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAutoSizeUniform_throwsException_minLessThanZero() {
        final TextView textView = new TextView(mActivity);
        textView.setAutoSizeTextTypeUniformWithConfiguration(
                -1, 9, 1, TypedValue.COMPLEX_UNIT_SP);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAutoSizeUniform_throwsException_maxLessThanZero() {
        final TextView textView = new TextView(mActivity);
        textView.setAutoSizeTextTypeUniformWithConfiguration(
                10, -1, 1, TypedValue.COMPLEX_UNIT_SP);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAutoSizeUniform_throwsException_granularityLessThanZero() {
        final TextView textView = new TextView(mActivity);
        textView.setAutoSizeTextTypeUniformWithConfiguration(
                10, 20, -1, TypedValue.COMPLEX_UNIT_SP);
    }

    @Test
    public void testAutoSizeUniform_equivalentConfigurations() throws Throwable {
        final DisplayMetrics dm = mActivity.getResources().getDisplayMetrics();
        final int minTextSize = 10;
        final int maxTextSize = 20;
        final int granularity = 2;
        final int unit = TypedValue.COMPLEX_UNIT_SP;

        final TextView granularityTextView = new TextView(mActivity);
        granularityTextView.setAutoSizeTextTypeUniformWithConfiguration(
                minTextSize, maxTextSize, granularity, unit);

        final TextView presetTextView = new TextView(mActivity);
        presetTextView.setAutoSizeTextTypeUniformWithPresetSizes(
                new int[] {minTextSize, 12, 14, 16, 18, maxTextSize}, unit);

        // The TextViews have been configured differently but the end result should be nearly
        // identical.
        final int expectedAutoSizeType = TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM;
        assertEquals(expectedAutoSizeType, granularityTextView.getAutoSizeTextType());
        assertEquals(expectedAutoSizeType, presetTextView.getAutoSizeTextType());

        final int expectedMinTextSizeInPx = Math.round(
                TypedValue.applyDimension(unit, minTextSize, dm));
        assertEquals(expectedMinTextSizeInPx, granularityTextView.getAutoSizeMinTextSize());
        assertEquals(expectedMinTextSizeInPx, presetTextView.getAutoSizeMinTextSize());

        final int expectedMaxTextSizeInPx = Math.round(
                TypedValue.applyDimension(unit, maxTextSize, dm));
        assertEquals(expectedMaxTextSizeInPx, granularityTextView.getAutoSizeMaxTextSize());
        assertEquals(expectedMaxTextSizeInPx, presetTextView.getAutoSizeMaxTextSize());

        // Configured with granularity.
        assertEquals(Math.round(TypedValue.applyDimension(unit, granularity, dm)),
                granularityTextView.getAutoSizeStepGranularity());
        // Configured with preset values, there is no granularity.
        assertEquals(-1, presetTextView.getAutoSizeStepGranularity());

        // Both TextViews generate exactly the same sizes in pixels to choose from when auto-sizing.
        assertArrayEquals("Expected the granularity and preset configured auto-sized "
                + "TextViews to have identical available sizes for auto-sizing."
                + "\ngranularity sizes: "
                + Arrays.toString(granularityTextView.getAutoSizeTextAvailableSizes())
                + "\npreset sizes: "
                + Arrays.toString(presetTextView.getAutoSizeTextAvailableSizes()),
                granularityTextView.getAutoSizeTextAvailableSizes(),
                presetTextView.getAutoSizeTextAvailableSizes());

        final String someText = "This is a string";
        final LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                200, 200);
        // Configure identically and attach to layout.
        mActivityRule.runOnUiThread(() -> {
            granularityTextView.setLayoutParams(layoutParams);
            presetTextView.setLayoutParams(layoutParams);

            LinearLayout ll = mActivity.findViewById(R.id.layout_textviewtest);
            ll.removeAllViews();
            ll.addView(granularityTextView);
            ll.addView(presetTextView);

            granularityTextView.setText(someText);
            presetTextView.setText(someText);
        });
        mInstrumentation.waitForIdleSync();

        assertEquals(granularityTextView.getTextSize(), presetTextView.getTextSize(), 0f);
    }

    @Test
    public void testAutoSizeUniform_getSetAutoSizeMaxTextSize() {
        final TextView textView = new TextView(mActivity);
        textView.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM);
        assertEquals(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM, textView.getAutoSizeTextType());
        final int minSize = textView.getAutoSizeMinTextSize();
        assertNotEquals(0, minSize);
        final int maxSize = textView.getAutoSizeMaxTextSize();
        assertNotEquals(0, maxSize);

        final int newMaxSize = maxSize + 11;
        textView.setAutoSizeTextTypeUniformWithConfiguration(
                textView.getAutoSizeMinTextSize(),
                newMaxSize,
                textView.getAutoSizeStepGranularity(),
                TypedValue.COMPLEX_UNIT_PX);

        assertEquals(newMaxSize, textView.getAutoSizeMaxTextSize());
        // Min size has not changed.
        assertEquals(minSize, textView.getAutoSizeMinTextSize());
        textView.setAutoSizeTextTypeUniformWithConfiguration(
                textView.getAutoSizeMinTextSize(),
                newMaxSize,
                textView.getAutoSizeStepGranularity(),
                TypedValue.COMPLEX_UNIT_SP);
        // It does not matter which unit has been used to set the max size, the getter always
        // returns it in pixels.
        assertEquals(Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, newMaxSize,
                mActivity.getResources().getDisplayMetrics())), textView.getAutoSizeMaxTextSize());
    }

    @Test
    public void testAutoSizeUniform_autoSizeCalledWhenTypeChanged() throws Throwable {
        mTextView = findTextView(R.id.textview_text);
        // Make sure we pick an already inflated non auto-sized text view.
        assertEquals(TextView.AUTO_SIZE_TEXT_TYPE_NONE, mTextView.getAutoSizeTextType());
        // Set the text size to a very low value in order to prepare for auto-size.
        final int customTextSize = 3;
        mActivityRule.runOnUiThread(() ->
                mTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, customTextSize));
        mInstrumentation.waitForIdleSync();
        assertEquals(customTextSize, mTextView.getTextSize(), 0f);
        mActivityRule.runOnUiThread(() ->
                mTextView.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM));
        mInstrumentation.waitForIdleSync();
        // The size of the text should have changed.
        assertNotEquals(customTextSize, mTextView.getTextSize(), 0f);
    }

    @Test
    public void testSmartSelection() throws Throwable {
        mTextView = findTextView(R.id.textview_text);
        String text = "The president-elect, Filip, is coming to town tomorrow.";
        int startIndex = text.indexOf("president");
        int endIndex = startIndex + "president".length();
        initializeTextForSmartSelection(text);

        // Long-press for smart selection. Expect smart selection.
        Point offset = getCenterPositionOfTextAt(mTextView, startIndex, endIndex);
        emulateLongPressOnView(mTextView, offset.x, offset.y);
        PollingCheck.waitFor(() -> mTextView.getSelectionStart() == SMARTSELECT_START
                && mTextView.getSelectionEnd() == SMARTSELECT_END);
        // TODO: Test the floating toolbar content.
    }

    private boolean isWatch() {
        return (mActivity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_TYPE_WATCH) == Configuration.UI_MODE_TYPE_WATCH;
    }

    @Test
    public void testSmartSelection_dragSelection() throws Throwable {
        if (isWatch()) {
            return;
        }
        mTextView = findTextView(R.id.textview_text);
        String text = "The president-elect, Filip, is coming to town tomorrow.";
        int startIndex = text.indexOf("is coming to town");
        int endIndex = startIndex + "is coming to town".length();
        initializeTextForSmartSelection(text);

        Point start = getCenterPositionOfTextAt(mTextView, startIndex, startIndex);
        Point end = getCenterPositionOfTextAt(mTextView, endIndex, endIndex);
        int[] viewOnScreenXY = new int[2];
        mTextView.getLocationOnScreen(viewOnScreenXY);
        int startX = start.x + viewOnScreenXY[0];
        int startY = start.y + viewOnScreenXY[1];
        int offsetX = end.x - start.x;

        // Perform drag selection.
        CtsTouchUtils.emulateLongPressAndDragGesture(
                mInstrumentation, startX, startY, offsetX, 0 /* offsetY */);

        // No smart selection on drag selection.
        assertEquals(startIndex, mTextView.getSelectionStart());
        assertEquals(endIndex, mTextView.getSelectionEnd());
    }

    @Test
    public void testSmartSelection_resetSelection() throws Throwable {
        mTextView = findTextView(R.id.textview_text);
        String text = "The president-elect, Filip, is coming to town tomorrow.";
        int startIndex = text.indexOf("president");
        int endIndex = startIndex + "president".length();
        initializeTextForSmartSelection(text);

        // Long-press for smart selection. Expect smart selection.
        Point offset = getCenterPositionOfTextAt(mTextView, startIndex, endIndex);
        emulateLongPressOnView(mTextView, offset.x, offset.y);
        PollingCheck.waitFor(() -> mTextView.getSelectionStart() == SMARTSELECT_START
                && mTextView.getSelectionEnd() == SMARTSELECT_END);

        // Tap to reset selection. Expect tapped word to be selected.
        startIndex = text.indexOf("Filip");
        endIndex = startIndex + "Filip".length();
        offset = getCenterPositionOfTextAt(mTextView, startIndex, endIndex);
        emulateClickOnView(mTextView, offset.x, offset.y);
        final int selStart = startIndex;
        final int selEnd = endIndex;
        PollingCheck.waitFor(() -> mTextView.getSelectionStart() == selStart
                && mTextView.getSelectionEnd() == selEnd);

        // Tap one more time to dismiss the selection.
        emulateClickOnView(mTextView, offset.x, offset.y);
        assertFalse(mTextView.hasSelection());
    }

    @Test
    public void testFontResources_setInXmlFamilyName() {
        mTextView = findTextView(R.id.textview_fontresource_fontfamily);
        Typeface expected = mActivity.getResources().getFont(R.font.samplefont);

        assertEquals(expected, mTextView.getTypeface());
    }

    @Test
    public void testFontResourcesXml_setInXmlFamilyName() {
        mTextView = findTextView(R.id.textview_fontxmlresource_fontfamily);
        Typeface expected = mActivity.getResources().getFont(R.font.samplexmlfont);

        assertEquals(expected, mTextView.getTypeface());
    }

    @Test
    public void testFontResources_setInXmlStyle() {
        mTextView = findTextView(R.id.textview_fontresource_style);
        Typeface expected = mActivity.getResources().getFont(R.font.samplefont);

        assertEquals(expected, mTextView.getTypeface());
    }

    @Test
    public void testFontResourcesXml_setInXmlStyle() {
        mTextView = findTextView(R.id.textview_fontxmlresource_style);
        Typeface expected = mActivity.getResources().getFont(R.font.samplexmlfont);

        assertEquals(expected, mTextView.getTypeface());
    }

    @Test
    public void testFontResources_setInXmlTextAppearance() {
        mTextView = findTextView(R.id.textview_fontresource_textAppearance);
        Typeface expected = mActivity.getResources().getFont(R.font.samplefont);

        assertEquals(expected, mTextView.getTypeface());
    }

    @Test
    public void testFontResourcesXml_setInXmlWithStyle() {
        mTextView = findTextView(R.id.textview_fontxmlresource_fontfamily);
        Typeface expected = mActivity.getResources().getFont(R.font.samplexmlfont);

        assertEquals(expected, mTextView.getTypeface());

        mTextView = findTextView(R.id.textview_fontxmlresource_withStyle);

        Typeface resultTypeface = mTextView.getTypeface();
        assertNotEquals(resultTypeface, expected);
        assertEquals(Typeface.create(expected, Typeface.ITALIC), resultTypeface);
        assertEquals(Typeface.ITALIC, resultTypeface.getStyle());
    }

    @Test
    public void testFontResourcesXml_setInXmlTextAppearance() {
        mTextView = findTextView(R.id.textview_fontxmlresource_textAppearance);
        Typeface expected = mActivity.getResources().getFont(R.font.samplexmlfont);

        assertEquals(expected, mTextView.getTypeface());
    }

    @Test
    @MediumTest
    public void testFontResourcesXml_restrictedContext()
            throws PackageManager.NameNotFoundException {
        Context restrictedContext = mActivity.createPackageContext(mActivity.getPackageName(),
                Context.CONTEXT_RESTRICTED);
        LayoutInflater layoutInflater = (LayoutInflater) restrictedContext.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        View root = layoutInflater.inflate(R.layout.textview_restricted_layout, null);

        mTextView = root.findViewById(R.id.textview_fontresource_fontfamily);
        assertEquals(Typeface.DEFAULT, mTextView.getTypeface());
        mTextView = root.findViewById(R.id.textview_fontxmlresource_fontfamily);
        assertEquals(Typeface.DEFAULT, mTextView.getTypeface());
        mTextView = root.findViewById(R.id.textview_fontxmlresource_nonFontReference);
        assertEquals(Typeface.DEFAULT, mTextView.getTypeface());
        mTextView = root.findViewById(R.id.textview_fontresource_style);
        assertEquals(Typeface.DEFAULT, mTextView.getTypeface());
        mTextView = root.findViewById(R.id.textview_fontxmlresource_style);
        assertEquals(Typeface.DEFAULT, mTextView.getTypeface());
        mTextView = root.findViewById(R.id.textview_fontresource_textAppearance);
        assertEquals(Typeface.DEFAULT, mTextView.getTypeface());
        mTextView = root.findViewById(R.id.textview_fontxmlresource_textAppearance);
        assertEquals(Typeface.DEFAULT, mTextView.getTypeface());
    }

    private void initializeTextForSmartSelection(CharSequence text) throws Throwable {
        assertTrue(text.length() >= SMARTSELECT_END);
        mActivityRule.runOnUiThread(() -> {
            mTextView.setTextIsSelectable(true);
            mTextView.setText(text);
            mTextView.setTextClassifier(FAKE_TEXT_CLASSIFIER);
            mTextView.requestFocus();
        });
        mInstrumentation.waitForIdleSync();
    }

    private void emulateClickOnView(View view, int offsetX, int offsetY) {
        CtsTouchUtils.emulateTapOnView(mInstrumentation, view, offsetX, offsetY);
        SystemClock.sleep(CLICK_TIMEOUT);
    }

    private void emulateLongPressOnView(View view, int offsetX, int offsetY) {
        CtsTouchUtils.emulateLongPressOnView(mInstrumentation, view, offsetX, offsetY);
        // TODO: Ideally, we shouldn't have to wait for a click timeout after a long-press but it
        // seems like we have a minor bug (call it inconvenience) in TextView that requires this.
        SystemClock.sleep(CLICK_TIMEOUT);
    }

    /**
     * Some TextView attributes require non-fixed width and/or layout height. This function removes
     * all other existing views from the layout leaving only one auto-size TextView (for exercising
     * the auto-size behavior) which has been set up to suit the test needs.
     *
     * @param viewId The id of the view to prepare.
     * @param shouldWrapLayoutContent Specifies if the layout params should wrap content
     *
     * @return a TextView configured for auto size tests.
     */
    private TextView prepareAndRetrieveAutoSizeTestData(final int viewId,
            final boolean shouldWrapLayoutContent) throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            LinearLayout ll = (LinearLayout) mActivity.findViewById(R.id.layout_textviewtest);
            TextView targetedTextView = (TextView) mActivity.findViewById(viewId);
            ll.removeAllViews();
            ll.addView(targetedTextView);
        });
        mInstrumentation.waitForIdleSync();

        final TextView textView = (TextView) mActivity.findViewById(viewId);
        if (shouldWrapLayoutContent) {
            // Do not force exact width or height.
            final LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            mActivityRule.runOnUiThread(() -> {
                textView.setLayoutParams(layoutParams);
            });
            mInstrumentation.waitForIdleSync();
        }

        return textView;
    }

    /**
     * Removes all existing views from the layout and adds a basic TextView (for exercising the
     * ClickableSpan onClick() behavior) in order to prevent scrolling. Adds a ClickableSpan to the
     * TextView and returns the ClickableSpan and position details about it to be used in individual
     * tests.
     */
    private ClickableSpanTestDetails prepareAndRetrieveClickableSpanDetails() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            LinearLayout ll = (LinearLayout) mActivity.findViewById(R.id.layout_textviewtest);
            ll.removeAllViews();
            mTextView = new TextView(mActivity);
            ll.addView(mTextView);
        });
        mInstrumentation.waitForIdleSync();

        ClickableSpan mockTextLink = mock(ClickableSpan.class);
        StringBuilder textViewContent = new StringBuilder();
        String clickableString = "clickMe!";
        textViewContent.append(clickableString);
        final int startPos = 0;

        // Insert more characters to make some room for swiping.
        for (int i = 0; i < 200; i++) {
            textViewContent.append(" text");
        }
        SpannableString spannableString = new SpannableString(textViewContent);
        final int endPos = clickableString.length();
        spannableString.setSpan(mockTextLink, startPos, endPos, 0);
        mActivityRule.runOnUiThread(() -> {
            mTextView.setText(spannableString);
            mTextView.setMovementMethod(LinkMovementMethod.getInstance());
        });
        mInstrumentation.waitForIdleSync();

        return new ClickableSpanTestDetails(mockTextLink, mTextView, startPos, endPos);
    }

    private static final class ClickableSpanTestDetails {
        ClickableSpan mClickableSpan;
        int mXPosInside;
        int mYPosInside;
        int mXPosOutside;
        int mYPosOutside;

        private int mStartCharPos;
        private int mEndCharPos;
        private TextView mParent;

        ClickableSpanTestDetails(ClickableSpan clickableSpan, TextView parent,
                int startCharPos, int endCharPos) {
            mClickableSpan = clickableSpan;
            mParent = parent;
            mStartCharPos = startCharPos;
            mEndCharPos = endCharPos;

            calculatePositions();
        }

        private void calculatePositions() {
            int xStart = (int) mParent.getLayout().getPrimaryHorizontal(mStartCharPos, true);
            int xEnd = (int) mParent.getLayout().getPrimaryHorizontal(mEndCharPos, true);
            int line = mParent.getLayout().getLineForOffset(mEndCharPos);
            int yTop = mParent.getLayout().getLineTop(line);
            int yBottom = mParent.getLayout().getLineBottom(line);

            mXPosInside = (xStart + xEnd) / 2;
            mYPosInside = (yTop + yBottom) / 2;
            mXPosOutside = xEnd + 1;
            mYPosOutside = yBottom + 1;
        }
    }

    private MotionEvent createMouseHoverEvent(View view) {
        final int[] xy = new int[2];
        view.getLocationOnScreen(xy);
        final int viewWidth = view.getWidth();
        final int viewHeight = view.getHeight();
        float x = xy[0] + viewWidth / 2.0f;
        float y = xy[1] + viewHeight / 2.0f;
        long eventTime = SystemClock.uptimeMillis();
        MotionEvent.PointerCoords[] pointerCoords = new MotionEvent.PointerCoords[1];
        pointerCoords[0] = new MotionEvent.PointerCoords();
        pointerCoords[0].x = x;
        pointerCoords[0].y = y;
        final int[] pointerIds = new int[1];
        pointerIds[0] = 0;
        return MotionEvent.obtain(0, eventTime, MotionEvent.ACTION_HOVER_MOVE, 1, pointerIds,
                pointerCoords, 0, 0, 0, 0, 0, InputDevice.SOURCE_MOUSE, 0);
    }

    private void layout(final TextView textView) throws Throwable {
        mActivityRule.runOnUiThread(() -> mActivity.setContentView(textView));
        mInstrumentation.waitForIdleSync();
    }

    private void layout(final int layoutId) throws Throwable {
        mActivityRule.runOnUiThread(() -> mActivity.setContentView(layoutId));
        mInstrumentation.waitForIdleSync();
    }

    private TextView findTextView(int id) {
        return (TextView) mActivity.findViewById(id);
    }

    private int getAutoLinkMask(int id) {
        return findTextView(id).getAutoLinkMask();
    }

    private void setMaxLines(final int lines) throws Throwable {
        mActivityRule.runOnUiThread(() -> mTextView.setMaxLines(lines));
        mInstrumentation.waitForIdleSync();
    }

    private void setMaxWidth(final int pixels) throws Throwable {
        mActivityRule.runOnUiThread(() -> mTextView.setMaxWidth(pixels));
        mInstrumentation.waitForIdleSync();
    }

    private void setMinWidth(final int pixels) throws Throwable {
        mActivityRule.runOnUiThread(() -> mTextView.setMinWidth(pixels));
        mInstrumentation.waitForIdleSync();
    }

    private void setMaxHeight(final int pixels) throws Throwable {
        mActivityRule.runOnUiThread(() -> mTextView.setMaxHeight(pixels));
        mInstrumentation.waitForIdleSync();
    }

    private void setMinHeight(final int pixels) throws Throwable {
        mActivityRule.runOnUiThread(() -> mTextView.setMinHeight(pixels));
        mInstrumentation.waitForIdleSync();
    }

    private void setMinLines(final int minLines) throws Throwable {
        mActivityRule.runOnUiThread(() -> mTextView.setMinLines(minLines));
        mInstrumentation.waitForIdleSync();
    }

    /**
     * Convenience for {@link TextView#setText(CharSequence, BufferType)}. And
     * the buffer type is fixed to SPANNABLE.
     *
     * @param tv the text view
     * @param content the content
     */
    private void setSpannableText(final TextView tv, final String content) throws Throwable {
        mActivityRule.runOnUiThread(() -> tv.setText(content, BufferType.SPANNABLE));
        mInstrumentation.waitForIdleSync();
    }

    private void setLines(final int lines) throws Throwable {
        mActivityRule.runOnUiThread(() -> mTextView.setLines(lines));
        mInstrumentation.waitForIdleSync();
    }

    private void setHorizontallyScrolling(final boolean whether) throws Throwable {
        mActivityRule.runOnUiThread(() -> mTextView.setHorizontallyScrolling(whether));
        mInstrumentation.waitForIdleSync();
    }

    private void setWidth(final int pixels) throws Throwable {
        mActivityRule.runOnUiThread(() -> mTextView.setWidth(pixels));
        mInstrumentation.waitForIdleSync();
    }

    private void setHeight(final int pixels) throws Throwable {
        mActivityRule.runOnUiThread(() -> mTextView.setHeight(pixels));
        mInstrumentation.waitForIdleSync();
    }

    private void setMinEms(final int ems) throws Throwable {
        mActivityRule.runOnUiThread(() -> mTextView.setMinEms(ems));
        mInstrumentation.waitForIdleSync();
    }

    private void setMaxEms(final int ems) throws Throwable {
        mActivityRule.runOnUiThread(() -> mTextView.setMaxEms(ems));
        mInstrumentation.waitForIdleSync();
    }

    private void setEms(final int ems) throws Throwable {
        mActivityRule.runOnUiThread(() -> mTextView.setEms(ems));
        mInstrumentation.waitForIdleSync();
    }

    private void setLineSpacing(final float add, final float mult) throws Throwable {
        mActivityRule.runOnUiThread(() -> mTextView.setLineSpacing(add, mult));
        mInstrumentation.waitForIdleSync();
    }

    /**
     * Returns the x, y coordinates of text at a specified indices relative to the position of the
     * TextView.
     *
     * @param textView
     * @param startIndex start index of the text in the textView
     * @param endIndex end index of the text in the textView
     */
    private static Point getCenterPositionOfTextAt(
            TextView textView, int startIndex, int endIndex) {
        int xStart = (int) textView.getLayout().getPrimaryHorizontal(startIndex, true);
        int xEnd = (int) textView.getLayout().getPrimaryHorizontal(endIndex, true);
        int line = textView.getLayout().getLineForOffset(endIndex);
        int yTop = textView.getLayout().getLineTop(line);
        int yBottom = textView.getLayout().getLineBottom(line);

        return new Point((xStart + xEnd) / 2 /* x */, (yTop + yBottom) / 2 /* y */);
    }

    private static abstract class TestSelectedRunnable implements Runnable {
        private TextView mTextView;
        private boolean mIsSelected1;
        private boolean mIsSelected2;

        public TestSelectedRunnable(TextView textview) {
            mTextView = textview;
        }

        public boolean getIsSelected1() {
            return mIsSelected1;
        }

        public boolean getIsSelected2() {
            return mIsSelected2;
        }

        public void saveIsSelected1() {
            mIsSelected1 = mTextView.isSelected();
        }

        public void saveIsSelected2() {
            mIsSelected2 = mTextView.isSelected();
        }
    }

    private static abstract class TestLayoutRunnable implements Runnable {
        private TextView mTextView;
        private Layout mLayout;

        public TestLayoutRunnable(TextView textview) {
            mTextView = textview;
        }

        public Layout getLayout() {
            return mLayout;
        }

        public void saveLayout() {
            mLayout = mTextView.getLayout();
        }
    }

    private static class MockTextWatcher implements TextWatcher {
        private boolean mHasCalledAfterTextChanged;
        private boolean mHasCalledBeforeTextChanged;
        private boolean mHasOnTextChanged;

        public void reset(){
            mHasCalledAfterTextChanged = false;
            mHasCalledBeforeTextChanged = false;
            mHasOnTextChanged = false;
        }

        public boolean hasCalledAfterTextChanged() {
            return mHasCalledAfterTextChanged;
        }

        public boolean hasCalledBeforeTextChanged() {
            return mHasCalledBeforeTextChanged;
        }

        public boolean hasCalledOnTextChanged() {
            return mHasOnTextChanged;
        }

        public void afterTextChanged(Editable s) {
            mHasCalledAfterTextChanged = true;
        }

        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            mHasCalledBeforeTextChanged = true;
        }

        public void onTextChanged(CharSequence s, int start, int before, int count) {
            mHasOnTextChanged = true;
        }
    }

    /**
     * A TextWatcher that converts the text to spaces whenever the text changes.
     */
    private static class ConvertToSpacesTextWatcher implements TextWatcher {
        boolean mChangingText;

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            // Avoid infinite recursion.
            if (mChangingText) {
                return;
            }
            mChangingText = true;
            // Create a string of s.length() spaces.
            StringBuilder builder = new StringBuilder(s.length());
            for (int i = 0; i < s.length(); i++) {
                builder.append(' ');
            }
            s.replace(0, s.length(), builder.toString());
            mChangingText = false;
        }
    }
}
