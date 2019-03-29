/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static android.view.Gravity.CENTER;
import static android.view.Gravity.LEFT;
import static android.view.Gravity.NO_GRAVITY;
import static android.view.Gravity.RIGHT;
import static android.view.View.TEXT_ALIGNMENT_INHERIT;
import static android.widget.TextView.TEXT_ALIGNMENT_TEXT_END;
import static android.widget.TextView.TEXT_ALIGNMENT_TEXT_START;
import static android.widget.TextView.TEXT_ALIGNMENT_VIEW_END;
import static android.widget.TextView.TEXT_ALIGNMENT_VIEW_START;

import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.app.Instrumentation;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class TextViewFadingEdgeTest {
    private Activity mActivity;

    @Rule
    public ActivityTestRule<EmptyCtsActivity> mActivityRule =
            new ActivityTestRule<>(EmptyCtsActivity.class);

    public static final float DELTA = 0.01f;
    private static final String LONG_RTL_STRING = "مرحبا الروبوت مرحبا الروبوت مرحبا الروبوت";
    private static final String LONG_LTR_STRING = "start start1 middle middle1 end end1";
    private static final String SHORT_RTL_STRING = "ت";
    private static final String SHORT_LTR_STRING = "s";
    public static final int ANY_PADDING = 20;
    public static final int ANY_FADE_LENGTH = 20;
    public static final int TEXT_SIZE = 20;
    public static final int WIDTH = 100;

    private static final TestCase[] TEST_DATA = {
            // no fade - fading disabled
            new TestCase("Should not fade when text:empty, fading:disabled",
                    null, false, NO_GRAVITY, TEXT_ALIGNMENT_INHERIT, 0f, 0f),
            new TestCase("Should not fade when text:short, dir:LTR, fading:disabled",
                    SHORT_LTR_STRING, false, NO_GRAVITY, TEXT_ALIGNMENT_INHERIT, 0f, 0f),
            new TestCase("Should not fade when text:short, dir:RTL, fading:disabled",
                    SHORT_RTL_STRING, false, NO_GRAVITY, TEXT_ALIGNMENT_INHERIT, 0f, 0f),
            new TestCase("Should not fade when text:long , dir:LTR, fading:disabled",
                    LONG_LTR_STRING, false, NO_GRAVITY, TEXT_ALIGNMENT_INHERIT, 0f, 0f),
            new TestCase("Should not fade when text:long , dir:RTL, fading:disabled",
                    LONG_RTL_STRING, false, NO_GRAVITY, TEXT_ALIGNMENT_INHERIT, 0f, 0f),
            new TestCase("Should not fade when text:long, dir:LTR, scroll:middle",
                    LONG_LTR_STRING, false, NO_GRAVITY, TEXT_ALIGNMENT_INHERIT, true, 0f, 0f),
            new TestCase("Should not fade when text:long, dir:RTL, scroll:middle",
                    LONG_RTL_STRING, false, NO_GRAVITY, TEXT_ALIGNMENT_INHERIT, true, 0f, 0f),

            // no fade - text is shorter than view width
            new TestCase("Should not fade when text:empty",
                    null, true, NO_GRAVITY, TEXT_ALIGNMENT_INHERIT, 0f, 0f),
            new TestCase("Should not fade when text:short, dir:LTR",
                    SHORT_LTR_STRING, true, NO_GRAVITY, TEXT_ALIGNMENT_INHERIT, 0f, 0f),
            new TestCase("Should not fade when text:short, dir:LTR, gravity:center",
                    SHORT_LTR_STRING, true, CENTER, TEXT_ALIGNMENT_INHERIT, 0f, 0f),
            new TestCase("Should not fade when text:short, dir:RTL",
                    SHORT_RTL_STRING, true, NO_GRAVITY, TEXT_ALIGNMENT_INHERIT, 0f, 0f),
            new TestCase("Should not fade when text:short, dir:RTL, gravity:center",
                    SHORT_RTL_STRING, true, CENTER, TEXT_ALIGNMENT_INHERIT, 0f, 0f),

            // left fade - LTR
            new TestCase("Should fade left when text:long, dir:LTR, gravity:right",
                    LONG_LTR_STRING, true, RIGHT, TEXT_ALIGNMENT_INHERIT, 1f, 0f),
            new TestCase("Should fade left when text:long, dir:LTR, textAlignment:textEnd",
                    LONG_LTR_STRING, true, NO_GRAVITY, TEXT_ALIGNMENT_TEXT_END, 1f, 0f),
            new TestCase("Should fade left when text:long, dir:LTR, textAlignment:viewEnd",
                    LONG_LTR_STRING, true, NO_GRAVITY, TEXT_ALIGNMENT_VIEW_END, 1f, 0f),

            // left fade - RTL
            new TestCase("Should fade left when text:long, dir:RTL",
                    LONG_RTL_STRING, true, NO_GRAVITY, TEXT_ALIGNMENT_INHERIT, 1f, 0f),
            new TestCase("Should fade left when text:long, dir:RTL, gravity:right",
                    LONG_RTL_STRING, true, RIGHT, TEXT_ALIGNMENT_INHERIT, 1f, 0f),
            new TestCase("Should fade left when text:long, dir:RTL, textAlignment:textStart",
                    LONG_RTL_STRING, true, NO_GRAVITY, TEXT_ALIGNMENT_TEXT_START, 1f, 0f),
            new TestCase("Should fade left when text:long, dir:RTL, textAlignment:viewEnd",
                    LONG_RTL_STRING, true, NO_GRAVITY, TEXT_ALIGNMENT_VIEW_END, 1f, 0f),

            // right fade - LTR
            new TestCase("Should fade right when text:long, dir:LTR",
                    LONG_LTR_STRING, true, NO_GRAVITY, TEXT_ALIGNMENT_INHERIT, 0f, 1f),
            new TestCase("Should fade right when text:long, dir:LTR, textAlignment:textStart",
                    LONG_LTR_STRING, true, NO_GRAVITY, TEXT_ALIGNMENT_TEXT_START, 0f, 1f),
            new TestCase("Should fade right when text:long, dir:LTR, gravity:left",
                    LONG_LTR_STRING, true, LEFT, TEXT_ALIGNMENT_INHERIT, 0f, 1f),
            new TestCase("Should fade right when text:long, dir:LTR, textAlignment:viewStart",
                    LONG_LTR_STRING, true, NO_GRAVITY, TEXT_ALIGNMENT_VIEW_START, 0f, 1f),

            // right fade - RTL
            new TestCase("Should fade right when text:long, dir:RTL, gravity:left",
                    LONG_RTL_STRING, true, LEFT, TEXT_ALIGNMENT_INHERIT, 0f, 1f),
            new TestCase("Should fade right when text:long, dir:RTL, textAlignment:viewStart",
                    LONG_RTL_STRING, true, NO_GRAVITY, TEXT_ALIGNMENT_VIEW_START, 0f, 1f),
            new TestCase("Should fade right when text:long, dir:RTL, textAlignment:textEnd",
                    LONG_RTL_STRING, true, NO_GRAVITY, TEXT_ALIGNMENT_TEXT_END, 0f, 1f),

            // left and right fade
            new TestCase("Should fade both when text:long, dir:LTR, scroll:middle",
                    LONG_LTR_STRING, true, NO_GRAVITY, TEXT_ALIGNMENT_INHERIT, true, 1f, 1f),
            new TestCase("Should fade both when text:long, dir:RTL, scroll:middle",
                    LONG_RTL_STRING, true, NO_GRAVITY, TEXT_ALIGNMENT_INHERIT, true, 1f, 1f)
    };

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
        PollingCheck.waitFor(mActivity::hasWindowFocus);
    }

    @Test
    public void testFadingEdge() throws Throwable {
        for (TestCase data : TEST_DATA) {
            MockTextView textView = createTextView(data.text, data.horizontalFadingEnabled,
                    data.gravity, data.textAlignment, data.scrollToMiddle);

            assertEquals(data.errorMsg,
                    data.expectationLeft, textView.getLeftFadingEdgeStrength(), DELTA);
            assertEquals(data.errorMsg,
                    data.expectationRight, textView.getRightFadingEdgeStrength(), DELTA);
        }
    }

    private final MockTextView createTextView(String text, boolean horizontalFadingEnabled,
            int gravity, int textAlignment, boolean scrollToMiddle) throws Throwable {
        final MockTextView textView = new MockTextView(mActivity);
        textView.setSingleLine(true);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, TEXT_SIZE);
        textView.setPadding(ANY_PADDING, ANY_PADDING, ANY_PADDING, ANY_PADDING);
        textView.setFadingEdgeLength(ANY_FADE_LENGTH);
        textView.setLayoutParams(new ViewGroup.LayoutParams(WIDTH,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        textView.setHorizontalFadingEdgeEnabled(horizontalFadingEnabled);
        textView.setText(text);
        textView.setGravity(gravity);
        textView.setTextAlignment(textAlignment);

        final FrameLayout layout = new FrameLayout(mActivity);
        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        layout.setLayoutParams(layoutParams);
        layout.addView(textView);

        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        mActivityRule.runOnUiThread(() -> mActivity.setContentView(layout));
        if (scrollToMiddle) {
            mActivityRule.runOnUiThread(() -> {
                float lineMid = (textView.getLayout().getLineLeft(0) +
                        textView.getLayout().getLineRight(0)) / 2;
                int scrollPosition = (int) lineMid;
                textView.setScrollX(scrollPosition);
            });
        }
        instrumentation.waitForIdleSync();
        return textView;
    }

    private static class TestCase {
        final String errorMsg;
        final String text;
        final boolean horizontalFadingEnabled;
        final int gravity;
        final int textAlignment;
        final float expectationLeft;
        final float expectationRight;
        final boolean scrollToMiddle;

        TestCase(String errorMsg, String text, boolean horizontalFadingEnabled, int gravity,
                 int textAlignment, boolean scrollToMiddle, float expectationLeft,
                 float expectationRight) {
            this.errorMsg = errorMsg;
            this.text = text;
            this.horizontalFadingEnabled = horizontalFadingEnabled;
            this.gravity = gravity;
            this.textAlignment = textAlignment;
            this.expectationLeft = expectationLeft;
            this.expectationRight = expectationRight;
            this.scrollToMiddle = scrollToMiddle;
        }

        TestCase(String errorMsg, String text, boolean horizontalFadingEnabled, int gravity,
                 int textAlignment, float expectationLeft, float expectationRight) {
            this(errorMsg, text, horizontalFadingEnabled, gravity, textAlignment, false,
                    expectationLeft, expectationRight);
        }
    }
}
