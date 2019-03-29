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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.transition.Transition;
import android.transition.Transition.TransitionListener;
import android.transition.TransitionValues;
import android.util.AttributeSet;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.PopupWindow.OnDismissListener;
import android.widget.TextView;

import com.android.compatibility.common.util.WidgetTestUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class PopupWindowTest {
    private static final int WINDOW_SIZE_DP = 50;
    private static final int CONTENT_SIZE_DP = 30;

    private Instrumentation mInstrumentation;
    private PopupWindowCtsActivity mActivity;
    private PopupWindow mPopupWindow;
    private TextView mTextView;

    @Rule
    public ActivityTestRule<PopupWindowCtsActivity> mActivityRule =
            new ActivityTestRule<>(PopupWindowCtsActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
    }

    @Test
    public void testConstructor() {
        new PopupWindow(mActivity);

        new PopupWindow(mActivity, null);

        new PopupWindow(mActivity, null, android.R.attr.popupWindowStyle);

        new PopupWindow(mActivity, null, 0, android.R.style.Widget_DeviceDefault_PopupWindow);

        new PopupWindow(mActivity, null, 0, android.R.style.Widget_DeviceDefault_Light_PopupWindow);

        new PopupWindow(mActivity, null, 0, android.R.style.Widget_Material_PopupWindow);

        new PopupWindow(mActivity, null, 0, android.R.style.Widget_Material_Light_PopupWindow);
    }

    @UiThreadTest
    @Test
    public void testSize() {
        mPopupWindow = new PopupWindow();
        assertEquals(0, mPopupWindow.getWidth());
        assertEquals(0, mPopupWindow.getHeight());

        mPopupWindow = new PopupWindow(50, 50);
        assertEquals(50, mPopupWindow.getWidth());
        assertEquals(50, mPopupWindow.getHeight());

        mPopupWindow = new PopupWindow(-1, -1);
        assertEquals(-1, mPopupWindow.getWidth());
        assertEquals(-1, mPopupWindow.getHeight());

        TextView contentView = new TextView(mActivity);
        mPopupWindow = new PopupWindow(contentView);
        assertSame(contentView, mPopupWindow.getContentView());

        mPopupWindow = new PopupWindow(contentView, 0, 0);
        assertEquals(0, mPopupWindow.getWidth());
        assertEquals(0, mPopupWindow.getHeight());
        assertSame(contentView, mPopupWindow.getContentView());

        mPopupWindow = new PopupWindow(contentView, 50, 50);
        assertEquals(50, mPopupWindow.getWidth());
        assertEquals(50, mPopupWindow.getHeight());
        assertSame(contentView, mPopupWindow.getContentView());

        mPopupWindow = new PopupWindow(contentView, -1, -1);
        assertEquals(-1, mPopupWindow.getWidth());
        assertEquals(-1, mPopupWindow.getHeight());
        assertSame(contentView, mPopupWindow.getContentView());

        mPopupWindow = new PopupWindow(contentView, 0, 0, true);
        assertEquals(0, mPopupWindow.getWidth());
        assertEquals(0, mPopupWindow.getHeight());
        assertSame(contentView, mPopupWindow.getContentView());
        assertTrue(mPopupWindow.isFocusable());

        mPopupWindow = new PopupWindow(contentView, 50, 50, false);
        assertEquals(50, mPopupWindow.getWidth());
        assertEquals(50, mPopupWindow.getHeight());
        assertSame(contentView, mPopupWindow.getContentView());
        assertFalse(mPopupWindow.isFocusable());

        mPopupWindow = new PopupWindow(contentView, -1, -1, true);
        assertEquals(-1, mPopupWindow.getWidth());
        assertEquals(-1, mPopupWindow.getHeight());
        assertSame(contentView, mPopupWindow.getContentView());
        assertTrue(mPopupWindow.isFocusable());
    }

    @Test
    public void testAccessEnterExitTransitions() {
        PopupWindow w = new PopupWindow(mActivity, null, 0, 0);
        assertNull(w.getEnterTransition());
        assertNull(w.getExitTransition());

        w = new PopupWindow(mActivity, null, 0, R.style.PopupWindow_NullTransitions);
        assertNull(w.getEnterTransition());
        assertNull(w.getExitTransition());

        w = new PopupWindow(mActivity, null, 0, R.style.PopupWindow_CustomTransitions);
        assertTrue(w.getEnterTransition() instanceof CustomTransition);
        assertTrue(w.getExitTransition() instanceof CustomTransition);

        Transition enterTransition = new CustomTransition();
        Transition exitTransition = new CustomTransition();
        w = new PopupWindow(mActivity, null, 0, 0);
        w.setEnterTransition(enterTransition);
        w.setExitTransition(exitTransition);
        assertEquals(enterTransition, w.getEnterTransition());
        assertEquals(exitTransition, w.getExitTransition());

        w.setEnterTransition(null);
        w.setExitTransition(null);
        assertNull(w.getEnterTransition());
        assertNull(w.getExitTransition());
    }

    public static class CustomTransition extends Transition {
        public CustomTransition() {
        }

        // This constructor is needed for reflection-based creation of a transition when
        // the transition is defined in layout XML via attribute.
        @SuppressWarnings("unused")
        public CustomTransition(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        @Override
        public void captureStartValues(TransitionValues transitionValues) {}

        @Override
        public void captureEndValues(TransitionValues transitionValues) {}
    }

    @Test
    public void testAccessBackground() {
        mPopupWindow = new PopupWindow(mActivity);

        Drawable drawable = new ColorDrawable();
        mPopupWindow.setBackgroundDrawable(drawable);
        assertSame(drawable, mPopupWindow.getBackground());

        mPopupWindow.setBackgroundDrawable(null);
        assertNull(mPopupWindow.getBackground());
    }

    @Test
    public void testAccessAnimationStyle() {
        mPopupWindow = new PopupWindow(mActivity);
        // default is -1
        assertEquals(-1, mPopupWindow.getAnimationStyle());

        mPopupWindow.setAnimationStyle(android.R.style.Animation_Toast);
        assertEquals(android.R.style.Animation_Toast,
                mPopupWindow.getAnimationStyle());

        // abnormal values
        mPopupWindow.setAnimationStyle(-100);
        assertEquals(-100, mPopupWindow.getAnimationStyle());
    }

    @Test
    public void testAccessContentView() throws Throwable {
        mPopupWindow = new PopupWindow(mActivity);
        assertNull(mPopupWindow.getContentView());

        mActivityRule.runOnUiThread(() -> mTextView = new TextView(mActivity));
        mInstrumentation.waitForIdleSync();
        mPopupWindow.setContentView(mTextView);
        assertSame(mTextView, mPopupWindow.getContentView());

        mPopupWindow.setContentView(null);
        assertNull(mPopupWindow.getContentView());

        // can not set the content if the old content is shown
        mPopupWindow.setContentView(mTextView);
        assertFalse(mPopupWindow.isShowing());
        showPopup();
        ImageView img = new ImageView(mActivity);
        assertTrue(mPopupWindow.isShowing());
        mPopupWindow.setContentView(img);
        assertSame(mTextView, mPopupWindow.getContentView());
        dismissPopup();
    }

    @Test
    public void testAccessFocusable() {
        mPopupWindow = new PopupWindow(mActivity);
        assertFalse(mPopupWindow.isFocusable());

        mPopupWindow.setFocusable(true);
        assertTrue(mPopupWindow.isFocusable());

        mPopupWindow.setFocusable(false);
        assertFalse(mPopupWindow.isFocusable());
    }

    @Test
    public void testAccessHeight() {
        mPopupWindow = new PopupWindow(mActivity);
        assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, mPopupWindow.getHeight());

        int height = getDisplay().getHeight() / 2;
        mPopupWindow.setHeight(height);
        assertEquals(height, mPopupWindow.getHeight());

        height = getDisplay().getHeight();
        mPopupWindow.setHeight(height);
        assertEquals(height, mPopupWindow.getHeight());

        mPopupWindow.setHeight(0);
        assertEquals(0, mPopupWindow.getHeight());

        height = getDisplay().getHeight() * 2;
        mPopupWindow.setHeight(height);
        assertEquals(height, mPopupWindow.getHeight());

        height = -getDisplay().getHeight() / 2;
        mPopupWindow.setHeight(height);
        assertEquals(height, mPopupWindow.getHeight());
    }

    /**
     * Gets the display.
     *
     * @return the display
     */
    private Display getDisplay() {
        WindowManager wm = (WindowManager) mActivity.getSystemService(Context.WINDOW_SERVICE);
        return wm.getDefaultDisplay();
    }

    @Test
    public void testAccessWidth() {
        mPopupWindow = new PopupWindow(mActivity);
        assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, mPopupWindow.getWidth());

        int width = getDisplay().getWidth() / 2;
        mPopupWindow.setWidth(width);
        assertEquals(width, mPopupWindow.getWidth());

        width = getDisplay().getWidth();
        mPopupWindow.setWidth(width);
        assertEquals(width, mPopupWindow.getWidth());

        mPopupWindow.setWidth(0);
        assertEquals(0, mPopupWindow.getWidth());

        width = getDisplay().getWidth() * 2;
        mPopupWindow.setWidth(width);
        assertEquals(width, mPopupWindow.getWidth());

        width = - getDisplay().getWidth() / 2;
        mPopupWindow.setWidth(width);
        assertEquals(width, mPopupWindow.getWidth());
    }

    private static final int TOP = 0x00;
    private static final int BOTTOM = 0x01;

    private static final int LEFT = 0x00;
    private static final int RIGHT = 0x01;

    private static final int GREATER_THAN = 1;
    private static final int LESS_THAN = -1;
    private static final int EQUAL_TO = 0;

    @Test
    public void testShowAsDropDown() throws Throwable {
        final PopupWindow popup = createPopupWindow(createPopupContent(CONTENT_SIZE_DP,
                CONTENT_SIZE_DP));
        popup.setClipToScreenEnabled(false);
        popup.setOverlapAnchor(false);
        popup.setAnimationStyle(0);
        popup.setExitTransition(null);
        popup.setEnterTransition(null);

        verifyPosition(popup, R.id.anchor_upper_left,
                LEFT, EQUAL_TO, LEFT, TOP, EQUAL_TO, BOTTOM);
        verifyPosition(popup, R.id.anchor_upper,
                LEFT, EQUAL_TO, LEFT, TOP, EQUAL_TO, BOTTOM);
        verifyPosition(popup, R.id.anchor_upper_right,
                RIGHT, EQUAL_TO, RIGHT, TOP, EQUAL_TO, BOTTOM);

        verifyPosition(popup, R.id.anchor_middle_left,
                LEFT, EQUAL_TO, LEFT, TOP, EQUAL_TO, BOTTOM);
        verifyPosition(popup, R.id.anchor_middle,
                LEFT, EQUAL_TO, LEFT, TOP, EQUAL_TO, BOTTOM);
        verifyPosition(popup, R.id.anchor_middle_right,
                RIGHT, EQUAL_TO, RIGHT, TOP, EQUAL_TO, BOTTOM);

        verifyPosition(popup, R.id.anchor_lower_left,
                LEFT, EQUAL_TO, LEFT, BOTTOM, EQUAL_TO, TOP);
        verifyPosition(popup, R.id.anchor_lower,
                LEFT, EQUAL_TO, LEFT, BOTTOM, EQUAL_TO, TOP);
        verifyPosition(popup, R.id.anchor_lower_right,
                RIGHT, EQUAL_TO, RIGHT, BOTTOM, EQUAL_TO, TOP);
    }

    @Test
    public void testShowAsDropDown_ClipToScreen() throws Throwable {
        final PopupWindow popup = createPopupWindow(createPopupContent(CONTENT_SIZE_DP,
                CONTENT_SIZE_DP));
        popup.setClipToScreenEnabled(true);
        popup.setOverlapAnchor(false);
        popup.setAnimationStyle(0);
        popup.setExitTransition(null);
        popup.setEnterTransition(null);

        verifyPosition(popup, R.id.anchor_upper_left,
                LEFT, EQUAL_TO, LEFT, TOP, EQUAL_TO, BOTTOM);
        verifyPosition(popup, R.id.anchor_upper,
                LEFT, EQUAL_TO, LEFT, TOP, EQUAL_TO, BOTTOM);
        verifyPosition(popup, R.id.anchor_upper_right,
                RIGHT, EQUAL_TO, RIGHT, TOP, EQUAL_TO, BOTTOM);

        verifyPosition(popup, R.id.anchor_middle_left,
                LEFT, EQUAL_TO, LEFT, TOP, EQUAL_TO, BOTTOM);
        verifyPosition(popup, R.id.anchor_middle,
                LEFT, EQUAL_TO, LEFT, TOP, EQUAL_TO, BOTTOM);
        verifyPosition(popup, R.id.anchor_middle_right,
                RIGHT, EQUAL_TO, RIGHT, TOP, EQUAL_TO, BOTTOM);

        verifyPosition(popup, R.id.anchor_lower_left,
                LEFT, EQUAL_TO, LEFT, BOTTOM, EQUAL_TO, TOP);
        verifyPosition(popup, R.id.anchor_lower,
                LEFT, EQUAL_TO, LEFT, BOTTOM, EQUAL_TO, TOP);
        verifyPosition(popup, R.id.anchor_lower_right,
                RIGHT, EQUAL_TO, RIGHT, BOTTOM, EQUAL_TO, TOP);
    }

    @Test
    public void testShowAsDropDown_ClipToScreen_Overlap() throws Throwable {
        final PopupWindow popup = createPopupWindow(createPopupContent(CONTENT_SIZE_DP,
                CONTENT_SIZE_DP));
        popup.setClipToScreenEnabled(true);
        popup.setOverlapAnchor(true);
        popup.setAnimationStyle(0);
        popup.setExitTransition(null);
        popup.setEnterTransition(null);

        verifyPosition(popup, R.id.anchor_upper_left,
                LEFT, EQUAL_TO, LEFT, TOP, EQUAL_TO, TOP);
        verifyPosition(popup, R.id.anchor_upper,
                LEFT, EQUAL_TO, LEFT, TOP, EQUAL_TO, TOP);
        verifyPosition(popup, R.id.anchor_upper_right,
                RIGHT, EQUAL_TO, RIGHT, TOP, EQUAL_TO, TOP);

        verifyPosition(popup, R.id.anchor_middle_left,
                LEFT, EQUAL_TO, LEFT, TOP, EQUAL_TO, TOP);
        verifyPosition(popup, R.id.anchor_middle,
                LEFT, EQUAL_TO, LEFT, TOP, EQUAL_TO, TOP);
        verifyPosition(popup, R.id.anchor_middle_right,
                RIGHT, EQUAL_TO, RIGHT, TOP, EQUAL_TO, TOP);

        verifyPosition(popup, R.id.anchor_lower_left,
                LEFT, EQUAL_TO, LEFT, BOTTOM, EQUAL_TO, TOP);
        verifyPosition(popup, R.id.anchor_lower,
                LEFT, EQUAL_TO, LEFT, BOTTOM, EQUAL_TO, TOP);
        verifyPosition(popup, R.id.anchor_lower_right,
                RIGHT, EQUAL_TO, RIGHT, BOTTOM, EQUAL_TO, TOP);
    }

    @Test
    public void testShowAsDropDown_ClipToScreen_Overlap_Offset() throws Throwable {
        final PopupWindow popup = createPopupWindow(createPopupContent(CONTENT_SIZE_DP,
                CONTENT_SIZE_DP));
        popup.setClipToScreenEnabled(true);
        popup.setOverlapAnchor(true);
        popup.setAnimationStyle(0);
        popup.setExitTransition(null);
        popup.setEnterTransition(null);

        final int offsetX = mActivity.findViewById(R.id.anchor_upper).getWidth() / 2;
        final int offsetY = mActivity.findViewById(R.id.anchor_upper).getHeight() / 2;
        final int gravity = Gravity.TOP | Gravity.START;

        verifyPosition(popup, R.id.anchor_upper_left,
                LEFT, GREATER_THAN, LEFT, TOP, GREATER_THAN, TOP,
                offsetX, offsetY, gravity);
        verifyPosition(popup, R.id.anchor_upper,
                LEFT, GREATER_THAN, LEFT, TOP, GREATER_THAN, TOP,
                offsetX, offsetY, gravity);
        verifyPosition(popup, R.id.anchor_upper_right,
                RIGHT, EQUAL_TO, RIGHT, TOP, GREATER_THAN, TOP,
                offsetX, offsetY, gravity);

        verifyPosition(popup, R.id.anchor_middle_left,
                LEFT, GREATER_THAN, LEFT, TOP, GREATER_THAN, TOP,
                offsetX, offsetY, gravity);
        verifyPosition(popup, R.id.anchor_middle,
                LEFT, GREATER_THAN, LEFT, TOP, GREATER_THAN, TOP,
                offsetX, offsetY, gravity);
        verifyPosition(popup, R.id.anchor_middle_right,
                RIGHT, EQUAL_TO, RIGHT, TOP, GREATER_THAN, TOP,
                offsetX, offsetY, gravity);

        verifyPosition(popup, R.id.anchor_lower_left,
                LEFT, GREATER_THAN, LEFT, BOTTOM, LESS_THAN, BOTTOM,
                offsetX, offsetY, gravity);
        verifyPosition(popup, R.id.anchor_lower,
                LEFT, GREATER_THAN, LEFT, BOTTOM, LESS_THAN, BOTTOM,
                offsetX, offsetY, gravity);
        verifyPosition(popup, R.id.anchor_lower_right,
                RIGHT, EQUAL_TO, RIGHT, BOTTOM, LESS_THAN, BOTTOM,
                offsetX, offsetY, gravity);
    }

    @Test
    public void testShowAsDropDown_ClipToScreen_TooBig() throws Throwable {
        final View rootView = mActivity.findViewById(R.id.anchor_upper_left).getRootView();
        final int width = rootView.getWidth() * 2;
        final int height = rootView.getHeight() * 2;

        final PopupWindow popup = createPopupWindow(createPopupContent(width, height));
        popup.setWidth(width);
        popup.setHeight(height);

        popup.setClipToScreenEnabled(true);
        popup.setOverlapAnchor(false);
        popup.setAnimationStyle(0);
        popup.setExitTransition(null);
        popup.setEnterTransition(null);

        verifyPosition(popup, R.id.anchor_upper_left,
                LEFT, EQUAL_TO, LEFT, TOP, LESS_THAN, TOP);
        verifyPosition(popup, R.id.anchor_upper,
                LEFT, LESS_THAN, LEFT, TOP, LESS_THAN, TOP);
        verifyPosition(popup, R.id.anchor_upper_right,
                RIGHT, EQUAL_TO, RIGHT, TOP, LESS_THAN, TOP);

        verifyPosition(popup, R.id.anchor_middle_left,
                LEFT, EQUAL_TO, LEFT, TOP, LESS_THAN, TOP);
        verifyPosition(popup, R.id.anchor_middle,
                LEFT, LESS_THAN, LEFT, TOP, LESS_THAN, TOP);
        verifyPosition(popup, R.id.anchor_middle_right,
                RIGHT, EQUAL_TO, RIGHT, TOP, LESS_THAN, TOP);

        verifyPosition(popup, R.id.anchor_lower_left,
                LEFT, EQUAL_TO, LEFT, BOTTOM, EQUAL_TO, BOTTOM);
        verifyPosition(popup, R.id.anchor_lower,
                LEFT, LESS_THAN, LEFT, BOTTOM, EQUAL_TO, BOTTOM);
        verifyPosition(popup, R.id.anchor_lower_right,
                RIGHT, EQUAL_TO, RIGHT, BOTTOM, EQUAL_TO, BOTTOM);
    }

    private void verifyPosition(PopupWindow popup, int anchorId,
            int contentEdgeX, int operatorX, int anchorEdgeX,
            int contentEdgeY, int operatorY, int anchorEdgeY) throws Throwable {
        verifyPosition(popup, mActivity.findViewById(anchorId),
                contentEdgeX, operatorX, anchorEdgeX,
                contentEdgeY, operatorY, anchorEdgeY,
                0, 0, Gravity.TOP | Gravity.START);
    }

    private void verifyPosition(PopupWindow popup, int anchorId,
            int contentEdgeX, int operatorX, int anchorEdgeX,
            int contentEdgeY, int operatorY, int anchorEdgeY,
            int offsetX, int offsetY, int gravity) throws Throwable {
        verifyPosition(popup, mActivity.findViewById(anchorId),
                contentEdgeX, operatorX, anchorEdgeX,
                contentEdgeY, operatorY, anchorEdgeY, offsetX, offsetY, gravity);
    }

    private void verifyPosition(PopupWindow popup, View anchor,
            int contentEdgeX, int operatorX, int anchorEdgeX,
            int contentEdgeY, int operatorY, int anchorEdgeY) throws Throwable {
        verifyPosition(popup, anchor,
                contentEdgeX, operatorX, anchorEdgeX,
                contentEdgeY, operatorY, anchorEdgeY,
                0, 0, Gravity.TOP | Gravity.START);
    }

    private void verifyPosition(PopupWindow popup, View anchor,
            int contentEdgeX, int operatorX, int anchorEdgeX,
            int contentEdgeY, int operatorY, int anchorEdgeY,
            int offsetX, int offsetY, int gravity) throws Throwable {
        final View content = popup.getContentView();

        mActivityRule.runOnUiThread(() -> popup.showAsDropDown(
                anchor, offsetX, offsetY, gravity));
        mInstrumentation.waitForIdleSync();

        assertTrue(popup.isShowing());
        verifyPositionX(content, contentEdgeX, operatorX, anchor, anchorEdgeX);
        verifyPositionY(content, contentEdgeY, operatorY, anchor, anchorEdgeY);

        // Make sure it fits in the display frame.
        final Rect displayFrame = new Rect();
        anchor.getWindowVisibleDisplayFrame(displayFrame);
        final Rect contentFrame = new Rect();
        content.getBoundsOnScreen(contentFrame);
        assertTrue("Content (" + contentFrame + ") extends outside display ("
                + displayFrame + ")", displayFrame.contains(contentFrame));

        mActivityRule.runOnUiThread(popup::dismiss);
        mInstrumentation.waitForIdleSync();

        assertFalse(popup.isShowing());
    }

    private void verifyPositionY(View content, int contentEdge, int flags,
            View anchor, int anchorEdge) {
        final int[] anchorOnScreenXY = new int[2];
        anchor.getLocationOnScreen(anchorOnScreenXY);
        int anchorY = anchorOnScreenXY[1];
        if ((anchorEdge & BOTTOM) == BOTTOM) {
            anchorY += anchor.getHeight();
        }

        final int[] contentOnScreenXY = new int[2];
        content.getLocationOnScreen(contentOnScreenXY);
        int contentY = contentOnScreenXY[1];
        if ((contentEdge & BOTTOM) == BOTTOM) {
            contentY += content.getHeight();
        }

        assertComparison(contentY, flags, anchorY);
    }

    private void verifyPositionX(View content, int contentEdge, int flags,
            View anchor, int anchorEdge) {
        final int[] anchorOnScreenXY = new int[2];
        anchor.getLocationOnScreen(anchorOnScreenXY);
        int anchorX = anchorOnScreenXY[0];
        if ((anchorEdge & RIGHT) == RIGHT) {
            anchorX += anchor.getWidth();
        }

        final int[] contentOnScreenXY = new int[2];
        content.getLocationOnScreen(contentOnScreenXY);
        int contentX = contentOnScreenXY[0];
        if ((contentEdge & RIGHT) == RIGHT) {
            contentX += content.getWidth();
        }

        assertComparison(contentX, flags, anchorX);
    }

    private void assertComparison(int left, int operator, int right) {
        switch (operator) {
            case GREATER_THAN:
                assertTrue(left + " <= " + right, left > right);
                break;
            case LESS_THAN:
                assertTrue(left + " >= " + right, left < right);
                break;
            case EQUAL_TO:
                assertTrue(left + " != " + right, left == right);
                break;
        }
    }

    @Test
    public void testShowAtLocation() throws Throwable {
        int[] popupContentViewInWindowXY = new int[2];
        int[] popupContentViewOnScreenXY = new int[2];
        Rect containingRect = new Rect();

        mPopupWindow = createPopupWindow(createPopupContent(CONTENT_SIZE_DP, CONTENT_SIZE_DP));
        // Do not attach within the decor; we will be measuring location
        // with regard to screen coordinates.
        mPopupWindow.setAttachedInDecor(false);
        assertFalse(mPopupWindow.isAttachedInDecor());

        final View upperAnchor = mActivity.findViewById(R.id.anchor_upper);

        final int xOff = 10;
        final int yOff = 21;
        assertFalse(mPopupWindow.isShowing());
        mPopupWindow.getContentView().getLocationInWindow(popupContentViewInWindowXY);
        assertEquals(0, popupContentViewInWindowXY[0]);
        assertEquals(0, popupContentViewInWindowXY[1]);

        mActivityRule.runOnUiThread(
                () -> mPopupWindow.showAtLocation(upperAnchor, Gravity.NO_GRAVITY, xOff, yOff));
        mInstrumentation.waitForIdleSync();

        assertTrue(mPopupWindow.isShowing());
        mPopupWindow.getContentView().getLocationInWindow(popupContentViewInWindowXY);
        mPopupWindow.getContentView().getLocationOnScreen(popupContentViewOnScreenXY);
        upperAnchor.getWindowDisplayFrame(containingRect);

        assertTrue(popupContentViewInWindowXY[0] >= 0);
        assertTrue(popupContentViewInWindowXY[1] >= 0);
        assertEquals(containingRect.left + popupContentViewInWindowXY[0] + xOff,
                popupContentViewOnScreenXY[0]);
        assertEquals(containingRect.top + popupContentViewInWindowXY[1] + yOff,
                popupContentViewOnScreenXY[1]);

        dismissPopup();
    }

    @Test
    public void testShowAsDropDownWithOffsets() throws Throwable {
        int[] anchorXY = new int[2];
        int[] viewOnScreenXY = new int[2];
        int[] viewInWindowXY = new int[2];

        mPopupWindow = createPopupWindow(createPopupContent(CONTENT_SIZE_DP, CONTENT_SIZE_DP));
        final View upperAnchor = mActivity.findViewById(R.id.anchor_upper);
        upperAnchor.getLocationOnScreen(anchorXY);
        int height = upperAnchor.getHeight();

        final int xOff = 11;
        final int yOff = 12;

        mActivityRule.runOnUiThread(() -> mPopupWindow.showAsDropDown(upperAnchor, xOff, yOff));
        mInstrumentation.waitForIdleSync();

        mPopupWindow.getContentView().getLocationOnScreen(viewOnScreenXY);
        mPopupWindow.getContentView().getLocationInWindow(viewInWindowXY);
        assertEquals(anchorXY[0] + xOff + viewInWindowXY[0], viewOnScreenXY[0]);
        assertEquals(anchorXY[1] + height + yOff + viewInWindowXY[1], viewOnScreenXY[1]);

        dismissPopup();
    }

    @Test
    public void testOverlapAnchor() throws Throwable {
        int[] anchorXY = new int[2];
        int[] viewOnScreenXY = new int[2];
        int[] viewInWindowXY = new int[2];

        mPopupWindow = createPopupWindow(createPopupContent(CONTENT_SIZE_DP, CONTENT_SIZE_DP));
        final View upperAnchor = mActivity.findViewById(R.id.anchor_upper);
        upperAnchor.getLocationOnScreen(anchorXY);

        assertFalse(mPopupWindow.getOverlapAnchor());
        mPopupWindow.setOverlapAnchor(true);
        assertTrue(mPopupWindow.getOverlapAnchor());

        mActivityRule.runOnUiThread(() -> mPopupWindow.showAsDropDown(upperAnchor, 0, 0));
        mInstrumentation.waitForIdleSync();

        mPopupWindow.getContentView().getLocationOnScreen(viewOnScreenXY);
        mPopupWindow.getContentView().getLocationInWindow(viewInWindowXY);
        assertEquals(anchorXY[0] + viewInWindowXY[0], viewOnScreenXY[0]);
        assertEquals(anchorXY[1] + viewInWindowXY[1], viewOnScreenXY[1]);
    }

    @Test
    public void testAccessWindowLayoutType() {
        mPopupWindow = createPopupWindow(createPopupContent(CONTENT_SIZE_DP, CONTENT_SIZE_DP));
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                mPopupWindow.getWindowLayoutType());
        mPopupWindow.setWindowLayoutType(WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL);
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL,
                mPopupWindow.getWindowLayoutType());
    }

    @Test
    public void testGetMaxAvailableHeight() {
        mPopupWindow = createPopupWindow(createPopupContent(CONTENT_SIZE_DP, CONTENT_SIZE_DP));
        Rect displayFrame = new Rect();
        View anchorView = mActivity.findViewById(R.id.anchor_upper);
        anchorView.getWindowVisibleDisplayFrame(displayFrame);
        int displayHeight = displayFrame.height();
        int available = displayHeight - anchorView.getHeight();
        int maxAvailableHeight = mPopupWindow.getMaxAvailableHeight(anchorView);
        int maxAvailableHeightIgnoringBottomDecoration =
                mPopupWindow.getMaxAvailableHeight(anchorView, 0, true);
        assertTrue(maxAvailableHeight > 0);
        assertTrue(maxAvailableHeight <= available);
        assertTrue(maxAvailableHeightIgnoringBottomDecoration >= maxAvailableHeight);
        assertTrue(maxAvailableHeightIgnoringBottomDecoration <= available);

        int maxAvailableHeightWithOffset = mPopupWindow.getMaxAvailableHeight(anchorView, 2);
        assertEquals(maxAvailableHeight - 2, maxAvailableHeightWithOffset);

        maxAvailableHeightWithOffset =
                mPopupWindow.getMaxAvailableHeight(anchorView, maxAvailableHeight);
        assertTrue(maxAvailableHeightWithOffset > 0);
        assertTrue(maxAvailableHeightWithOffset <= available);

        maxAvailableHeightWithOffset =
                mPopupWindow.getMaxAvailableHeight(anchorView, maxAvailableHeight / 2 - 1);
        assertTrue(maxAvailableHeightWithOffset > 0);
        assertTrue(maxAvailableHeightWithOffset <= available);

        maxAvailableHeightWithOffset = mPopupWindow.getMaxAvailableHeight(anchorView, -1);
        assertTrue(maxAvailableHeightWithOffset > 0);
        assertTrue(maxAvailableHeightWithOffset <= available);

        int maxAvailableHeightWithOffsetIgnoringBottomDecoration =
                mPopupWindow.getMaxAvailableHeight(anchorView, 2, true);
        assertEquals(maxAvailableHeightIgnoringBottomDecoration - 2,
                maxAvailableHeightWithOffsetIgnoringBottomDecoration);

        maxAvailableHeightWithOffsetIgnoringBottomDecoration =
                mPopupWindow.getMaxAvailableHeight(anchorView, maxAvailableHeight, true);
        assertTrue(maxAvailableHeightWithOffsetIgnoringBottomDecoration > 0);
        assertTrue(maxAvailableHeightWithOffsetIgnoringBottomDecoration <= available);

        maxAvailableHeightWithOffsetIgnoringBottomDecoration =
                mPopupWindow.getMaxAvailableHeight(anchorView, maxAvailableHeight / 2 - 1, true);
        assertTrue(maxAvailableHeightWithOffsetIgnoringBottomDecoration > 0);
        assertTrue(maxAvailableHeightWithOffsetIgnoringBottomDecoration <= available);

        maxAvailableHeightWithOffsetIgnoringBottomDecoration =
                mPopupWindow.getMaxAvailableHeight(anchorView, -1, true);
        assertTrue(maxAvailableHeightWithOffsetIgnoringBottomDecoration > 0);
        assertTrue(maxAvailableHeightWithOffsetIgnoringBottomDecoration <= available);

        anchorView = mActivity.findViewById(R.id.anchor_lower);
        // On some devices the view might actually have larger size than the physical display
        // due to chin and content will be laid out as if outside of the display. We need to use
        // larger from the display height and the main view height.
        available = Math.max(displayHeight,
                mActivity.findViewById(android.R.id.content).getHeight()) - anchorView.getHeight();
        maxAvailableHeight = mPopupWindow.getMaxAvailableHeight(anchorView);
        assertTrue(maxAvailableHeight > 0);
        assertTrue(maxAvailableHeight <= available);

        anchorView = mActivity.findViewById(R.id.anchor_middle_left);
        available = displayHeight - anchorView.getHeight()
                - mActivity.findViewById(R.id.anchor_upper).getHeight();
        maxAvailableHeight = mPopupWindow.getMaxAvailableHeight(anchorView);
        assertTrue(maxAvailableHeight > 0);
        assertTrue(maxAvailableHeight <= available);
    }

    @UiThreadTest
    @Test
    public void testDismiss() {
        mPopupWindow = createPopupWindow(createPopupContent(CONTENT_SIZE_DP, CONTENT_SIZE_DP));
        assertFalse(mPopupWindow.isShowing());
        View anchorView = mActivity.findViewById(R.id.anchor_upper);
        mPopupWindow.showAsDropDown(anchorView);

        mPopupWindow.dismiss();
        assertFalse(mPopupWindow.isShowing());

        mPopupWindow.dismiss();
        assertFalse(mPopupWindow.isShowing());
    }

    @Test
    public void testSetOnDismissListener() throws Throwable {
        mActivityRule.runOnUiThread(() -> mTextView = new TextView(mActivity));
        mInstrumentation.waitForIdleSync();
        mPopupWindow = new PopupWindow(mTextView);
        mPopupWindow.setOnDismissListener(null);

        OnDismissListener onDismissListener = mock(OnDismissListener.class);
        mPopupWindow.setOnDismissListener(onDismissListener);
        showPopup();
        dismissPopup();
        verify(onDismissListener, times(1)).onDismiss();

        showPopup();
        dismissPopup();
        verify(onDismissListener, times(2)).onDismiss();

        mPopupWindow.setOnDismissListener(null);
        showPopup();
        dismissPopup();
        verify(onDismissListener, times(2)).onDismiss();
    }

    @Test
    public void testUpdate() throws Throwable {
        mPopupWindow = createPopupWindow(createPopupContent(CONTENT_SIZE_DP, CONTENT_SIZE_DP));
        mPopupWindow.setBackgroundDrawable(null);
        showPopup();

        mPopupWindow.setIgnoreCheekPress();
        mPopupWindow.setFocusable(true);
        mPopupWindow.setTouchable(false);
        mPopupWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        mPopupWindow.setClippingEnabled(false);
        mPopupWindow.setOutsideTouchable(true);

        WindowManager.LayoutParams p = (WindowManager.LayoutParams)
                mPopupWindow.getContentView().getRootView().getLayoutParams();

        assertEquals(0, WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES & p.flags);
        assertEquals(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE & p.flags);
        assertEquals(0, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE & p.flags);
        assertEquals(0, WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH & p.flags);
        assertEquals(0, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS & p.flags);
        assertEquals(0, WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM & p.flags);

        mActivityRule.runOnUiThread(mPopupWindow::update);
        mInstrumentation.waitForIdleSync();

        assertEquals(WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES,
                WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES & p.flags);
        assertEquals(0, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE & p.flags);
        assertEquals(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE & p.flags);
        assertEquals(WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH & p.flags);
        assertEquals(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS & p.flags);
        assertEquals(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM & p.flags);
    }

    @Test
    public void testEnterExitInterruption() throws Throwable {
        final View anchorView = mActivity.findViewById(R.id.anchor_upper);
        verifyEnterExitTransition(
                () -> mPopupWindow.showAsDropDown(anchorView, 0, 0), true);
    }

    @Test
    public void testEnterExitTransitionAsDropDown() throws Throwable {
        final View anchorView = mActivity.findViewById(R.id.anchor_upper);
        verifyEnterExitTransition(
                () -> mPopupWindow.showAsDropDown(anchorView, 0, 0), false);
    }

    @Test
    public void testEnterExitTransitionAtLocation() throws Throwable {
        final View anchorView = mActivity.findViewById(R.id.anchor_upper);
        verifyEnterExitTransition(
                () -> mPopupWindow.showAtLocation(anchorView, Gravity.BOTTOM, 0, 0), false);
    }

    private void verifyEnterExitTransition(Runnable showRunnable, boolean showAgain)
            throws Throwable {
        TransitionListener enterListener = mock(TransitionListener.class);
        Transition enterTransition = new BaseTransition();
        enterTransition.addListener(enterListener);

        TransitionListener exitListener = mock(TransitionListener.class);
        Transition exitTransition = new BaseTransition();
        exitTransition.addListener(exitListener);

        OnDismissListener dismissListener = mock(OnDismissListener.class);

        mPopupWindow = createPopupWindow(createPopupContent(CONTENT_SIZE_DP, CONTENT_SIZE_DP));
        mPopupWindow.setEnterTransition(enterTransition);
        mPopupWindow.setExitTransition(exitTransition);
        mPopupWindow.setOnDismissListener(dismissListener);

        mActivityRule.runOnUiThread(showRunnable);
        mInstrumentation.waitForIdleSync();
        verify(enterListener, times(1)).onTransitionStart(any(Transition.class));
        verify(exitListener, never()).onTransitionStart(any(Transition.class));
        verify(dismissListener, never()).onDismiss();

        mActivityRule.runOnUiThread(mPopupWindow::dismiss);

        int times;
        if (showAgain) {
            // Interrupt dismiss by calling show again, then actually dismiss.
            mActivityRule.runOnUiThread(showRunnable);
            mInstrumentation.waitForIdleSync();
            mActivityRule.runOnUiThread(mPopupWindow::dismiss);

            times = 2;
        } else {
            times = 1;
        }

        mInstrumentation.waitForIdleSync();
        verify(enterListener, times(times)).onTransitionStart(any(Transition.class));
        verify(exitListener, times(times)).onTransitionStart(any(Transition.class));
        verify(dismissListener, times(times)).onDismiss();
    }

    @Test
    public void testUpdatePositionAndDimension() throws Throwable {
        int[] fstXY = new int[2];
        int[] sndXY = new int[2];
        int[] viewInWindowXY = new int[2];
        Rect containingRect = new Rect();

        mActivityRule.runOnUiThread(() -> {
            mPopupWindow = createPopupWindow(createPopupContent(CONTENT_SIZE_DP, CONTENT_SIZE_DP));
            // Do not attach within the decor; we will be measuring location
            // with regard to screen coordinates.
            mPopupWindow.setAttachedInDecor(false);
        });

        mInstrumentation.waitForIdleSync();
        // Do not update if it is not shown
        assertFalse(mPopupWindow.isShowing());
        assertFalse(mPopupWindow.isAttachedInDecor());
        assertEquals(WINDOW_SIZE_DP, mPopupWindow.getWidth());
        assertEquals(WINDOW_SIZE_DP, mPopupWindow.getHeight());

        showPopup();
        mPopupWindow.getContentView().getLocationInWindow(viewInWindowXY);
        final View containerView = mActivity.findViewById(R.id.main_container);
        containerView.getWindowDisplayFrame(containingRect);

        // update if it is not shown
        mActivityRule.runOnUiThread(() -> mPopupWindow.update(80, 80));

        mInstrumentation.waitForIdleSync();
        assertTrue(mPopupWindow.isShowing());
        assertEquals(80, mPopupWindow.getWidth());
        assertEquals(80, mPopupWindow.getHeight());

        // update if it is not shown
        mActivityRule.runOnUiThread(() -> mPopupWindow.update(20, 50, 50, 50));

        mInstrumentation.waitForIdleSync();
        assertTrue(mPopupWindow.isShowing());
        assertEquals(50, mPopupWindow.getWidth());
        assertEquals(50, mPopupWindow.getHeight());

        mPopupWindow.getContentView().getLocationOnScreen(fstXY);
        assertEquals(containingRect.left + viewInWindowXY[0] + 20, fstXY[0]);
        assertEquals(containingRect.top + viewInWindowXY[1] + 50, fstXY[1]);

        // ignore if width or height is -1
        mActivityRule.runOnUiThread(() -> mPopupWindow.update(4, 0, -1, -1, true));
        mInstrumentation.waitForIdleSync();

        assertTrue(mPopupWindow.isShowing());
        assertEquals(50, mPopupWindow.getWidth());
        assertEquals(50, mPopupWindow.getHeight());

        mPopupWindow.getContentView().getLocationOnScreen(sndXY);
        assertEquals(containingRect.left + viewInWindowXY[0] + 4, sndXY[0]);
        assertEquals(containingRect.top + viewInWindowXY[1], sndXY[1]);

        dismissPopup();
    }

    @Test
    public void testUpdateDimensionAndAlignAnchorView() throws Throwable {
        mActivityRule.runOnUiThread(
                () -> mPopupWindow = createPopupWindow(createPopupContent(CONTENT_SIZE_DP,
                        CONTENT_SIZE_DP)));
        mInstrumentation.waitForIdleSync();

        final View anchorView = mActivity.findViewById(R.id.anchor_upper);
        mPopupWindow.update(anchorView, 50, 50);
        // Do not update if it is not shown
        assertFalse(mPopupWindow.isShowing());
        assertEquals(WINDOW_SIZE_DP, mPopupWindow.getWidth());
        assertEquals(WINDOW_SIZE_DP, mPopupWindow.getHeight());

        mActivityRule.runOnUiThread(() -> mPopupWindow.showAsDropDown(anchorView));
        mInstrumentation.waitForIdleSync();
        // update if it is shown
        mActivityRule.runOnUiThread(() -> mPopupWindow.update(anchorView, 50, 50));
        mInstrumentation.waitForIdleSync();
        assertTrue(mPopupWindow.isShowing());
        assertEquals(50, mPopupWindow.getWidth());
        assertEquals(50, mPopupWindow.getHeight());

        // ignore if width or height is -1
        mActivityRule.runOnUiThread(() -> mPopupWindow.update(anchorView, -1, -1));
        mInstrumentation.waitForIdleSync();
        assertTrue(mPopupWindow.isShowing());
        assertEquals(50, mPopupWindow.getWidth());
        assertEquals(50, mPopupWindow.getHeight());

        mActivityRule.runOnUiThread(mPopupWindow::dismiss);
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testUpdateDimensionAndAlignAnchorViewWithOffsets() throws Throwable {
        int[] anchorXY = new int[2];
        int[] viewInWindowOff = new int[2];
        int[] viewXY = new int[2];

        mPopupWindow = createPopupWindow(createPopupContent(CONTENT_SIZE_DP, CONTENT_SIZE_DP));
        final View anchorView = mActivity.findViewById(R.id.anchor_upper);
        // Do not update if it is not shown
        assertFalse(mPopupWindow.isShowing());
        assertEquals(WINDOW_SIZE_DP, mPopupWindow.getWidth());
        assertEquals(WINDOW_SIZE_DP, mPopupWindow.getHeight());

        showPopup();
        anchorView.getLocationOnScreen(anchorXY);
        mPopupWindow.getContentView().getLocationInWindow(viewInWindowOff);

        // update if it is not shown
        mActivityRule.runOnUiThread(() -> mPopupWindow.update(anchorView, 20, 50, 50, 50));

        mInstrumentation.waitForIdleSync();

        assertTrue(mPopupWindow.isShowing());
        assertEquals(50, mPopupWindow.getWidth());
        assertEquals(50, mPopupWindow.getHeight());

        mPopupWindow.getContentView().getLocationOnScreen(viewXY);

        // The popup should appear below and to right with an offset.
        assertEquals(anchorXY[0] + 20 + viewInWindowOff[0], viewXY[0]);
        assertEquals(anchorXY[1] + anchorView.getHeight() + 50 + viewInWindowOff[1], viewXY[1]);

        // ignore width and height but change location
        mActivityRule.runOnUiThread(() -> mPopupWindow.update(anchorView, 10, 50, -1, -1));
        mInstrumentation.waitForIdleSync();

        assertTrue(mPopupWindow.isShowing());
        assertEquals(50, mPopupWindow.getWidth());
        assertEquals(50, mPopupWindow.getHeight());

        mPopupWindow.getContentView().getLocationOnScreen(viewXY);

        // The popup should appear below and to right with an offset.
        assertEquals(anchorXY[0] + 10 + viewInWindowOff[0], viewXY[0]);
        assertEquals(anchorXY[1] + anchorView.getHeight() + 50 + viewInWindowOff[1], viewXY[1]);

        final View anotherView = mActivity.findViewById(R.id.anchor_middle_left);
        mActivityRule.runOnUiThread(() -> mPopupWindow.update(anotherView, 0, 0, 60, 60));
        mInstrumentation.waitForIdleSync();

        assertTrue(mPopupWindow.isShowing());
        assertEquals(60, mPopupWindow.getWidth());
        assertEquals(60, mPopupWindow.getHeight());

        int[] newXY = new int[2];
        anotherView.getLocationOnScreen(newXY);
        mPopupWindow.getContentView().getLocationOnScreen(viewXY);

        // The popup should appear below and to the right.
        assertEquals(newXY[0] + viewInWindowOff[0], viewXY[0]);
        assertEquals(newXY[1] + anotherView.getHeight() + viewInWindowOff[1], viewXY[1]);

        dismissPopup();
    }

    @Test
    public void testAccessInputMethodMode() {
        mPopupWindow = new PopupWindow(mActivity);
        assertEquals(0, mPopupWindow.getInputMethodMode());

        mPopupWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_FROM_FOCUSABLE);
        assertEquals(PopupWindow.INPUT_METHOD_FROM_FOCUSABLE, mPopupWindow.getInputMethodMode());

        mPopupWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
        assertEquals(PopupWindow.INPUT_METHOD_NEEDED, mPopupWindow.getInputMethodMode());

        mPopupWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        assertEquals(PopupWindow.INPUT_METHOD_NOT_NEEDED, mPopupWindow.getInputMethodMode());

        mPopupWindow.setInputMethodMode(-1);
        assertEquals(-1, mPopupWindow.getInputMethodMode());
    }

    @Test
    public void testAccessClippingEnabled() {
        mPopupWindow = new PopupWindow(mActivity);
        assertTrue(mPopupWindow.isClippingEnabled());

        mPopupWindow.setClippingEnabled(false);
        assertFalse(mPopupWindow.isClippingEnabled());
    }

    @Test
    public void testAccessOutsideTouchable() {
        mPopupWindow = new PopupWindow(mActivity);
        assertFalse(mPopupWindow.isOutsideTouchable());

        mPopupWindow.setOutsideTouchable(true);
        assertTrue(mPopupWindow.isOutsideTouchable());
    }

    @Test
    public void testAccessTouchable() {
        mPopupWindow = new PopupWindow(mActivity);
        assertTrue(mPopupWindow.isTouchable());

        mPopupWindow.setTouchable(false);
        assertFalse(mPopupWindow.isTouchable());
    }

    @Test
    public void testIsAboveAnchor() throws Throwable {
        mActivityRule.runOnUiThread(() -> mPopupWindow = createPopupWindow(createPopupContent(
                CONTENT_SIZE_DP, CONTENT_SIZE_DP)));
        mInstrumentation.waitForIdleSync();
        final View upperAnchor = mActivity.findViewById(R.id.anchor_upper);

        mActivityRule.runOnUiThread(() -> mPopupWindow.showAsDropDown(upperAnchor));
        mInstrumentation.waitForIdleSync();
        assertFalse(mPopupWindow.isAboveAnchor());
        dismissPopup();

        mPopupWindow = createPopupWindow(createPopupContent(CONTENT_SIZE_DP, CONTENT_SIZE_DP));
        final View lowerAnchor = mActivity.findViewById(R.id.anchor_lower);

        mActivityRule.runOnUiThread(() -> mPopupWindow.showAsDropDown(lowerAnchor, 0, 0));
        mInstrumentation.waitForIdleSync();
        assertTrue(mPopupWindow.isAboveAnchor());
        dismissPopup();
    }

    @Test
    public void testSetTouchInterceptor() throws Throwable {
        mActivityRule.runOnUiThread(() -> mTextView = new TextView(mActivity));
        mInstrumentation.waitForIdleSync();
        mPopupWindow = new PopupWindow(mTextView);

        OnTouchListener onTouchListener = mock(OnTouchListener.class);
        when(onTouchListener.onTouch(any(View.class), any(MotionEvent.class))).thenReturn(true);

        mPopupWindow.setTouchInterceptor(onTouchListener);
        mPopupWindow.setFocusable(true);
        mPopupWindow.setOutsideTouchable(true);
        Drawable drawable = new ColorDrawable();
        mPopupWindow.setBackgroundDrawable(drawable);
        showPopup();

        int[] xy = new int[2];
        mPopupWindow.getContentView().getLocationOnScreen(xy);
        final int viewWidth = mPopupWindow.getContentView().getWidth();
        final int viewHeight = mPopupWindow.getContentView().getHeight();
        final float x = xy[0] + (viewWidth / 2.0f);
        float y = xy[1] + (viewHeight / 2.0f);

        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(downTime, eventTime,
                MotionEvent.ACTION_DOWN, x, y, 0);
        mInstrumentation.sendPointerSync(event);
        verify(onTouchListener, times(1)).onTouch(any(View.class), any(MotionEvent.class));

        downTime = SystemClock.uptimeMillis();
        eventTime = SystemClock.uptimeMillis();
        event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_UP, x, y, 0);
        mInstrumentation.sendPointerSync(event);
        verify(onTouchListener, times(2)).onTouch(any(View.class), any(MotionEvent.class));

        mPopupWindow.setTouchInterceptor(null);
        downTime = SystemClock.uptimeMillis();
        eventTime = SystemClock.uptimeMillis();
        event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_DOWN, x, y, 0);
        mInstrumentation.sendPointerSync(event);
        verify(onTouchListener, times(2)).onTouch(any(View.class), any(MotionEvent.class));
    }

    @Test
    public void testSetWindowLayoutMode() throws Throwable {
        mActivityRule.runOnUiThread(() -> mTextView = new TextView(mActivity));
        mInstrumentation.waitForIdleSync();
        mPopupWindow = new PopupWindow(mTextView);
        showPopup();

        ViewGroup.LayoutParams p = mPopupWindow.getContentView().getRootView().getLayoutParams();
        assertEquals(0, p.width);
        assertEquals(0, p.height);

        mPopupWindow.setWindowLayoutMode(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
        mActivityRule.runOnUiThread(() -> mPopupWindow.update(20, 50, 50, 50));

        assertEquals(LayoutParams.WRAP_CONTENT, p.width);
        assertEquals(LayoutParams.MATCH_PARENT, p.height);
    }

    @Test
    public void testAccessElevation() throws Throwable {
        mPopupWindow = createPopupWindow(createPopupContent(CONTENT_SIZE_DP, CONTENT_SIZE_DP));
        mActivityRule.runOnUiThread(() -> mPopupWindow.setElevation(2.0f));

        showPopup();
        assertEquals(2.0f, mPopupWindow.getElevation(), 0.0f);

        dismissPopup();
        mActivityRule.runOnUiThread(() -> mPopupWindow.setElevation(4.0f));
        showPopup();
        assertEquals(4.0f, mPopupWindow.getElevation(), 0.0f);

        dismissPopup();
        mActivityRule.runOnUiThread(() -> mPopupWindow.setElevation(10.0f));
        showPopup();
        assertEquals(10.0f, mPopupWindow.getElevation(), 0.0f);
    }

    @Test
    public void testAccessSoftInputMode() throws Throwable {
        mPopupWindow = createPopupWindow(createPopupContent(CONTENT_SIZE_DP, CONTENT_SIZE_DP));
        mActivityRule.runOnUiThread(
                () -> mPopupWindow.setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE));

        showPopup();
        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE,
                mPopupWindow.getSoftInputMode());

        dismissPopup();
        mActivityRule.runOnUiThread(
                () -> mPopupWindow.setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN));
        showPopup();
        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN,
                mPopupWindow.getSoftInputMode());
    }

    @Test
    public void testAccessSplitTouchEnabled() throws Throwable {
        mPopupWindow = createPopupWindow(createPopupContent(CONTENT_SIZE_DP, CONTENT_SIZE_DP));
        mActivityRule.runOnUiThread(() -> mPopupWindow.setSplitTouchEnabled(true));

        showPopup();
        assertTrue(mPopupWindow.isSplitTouchEnabled());

        dismissPopup();
        mActivityRule.runOnUiThread(() -> mPopupWindow.setSplitTouchEnabled(false));
        showPopup();
        assertFalse(mPopupWindow.isSplitTouchEnabled());

        dismissPopup();
        mActivityRule.runOnUiThread(() -> mPopupWindow.setSplitTouchEnabled(true));
        showPopup();
        assertTrue(mPopupWindow.isSplitTouchEnabled());
    }

    @Test
    public void testVerticallyClippedBeforeAdjusted() throws Throwable {
        View parentWindowView = mActivity.getWindow().getDecorView();
        int parentWidth = parentWindowView.getMeasuredWidth();
        int parentHeight = parentWindowView.getMeasuredHeight();

        // We make a popup which is too large to fit within the parent window.
        // After showing it, we verify that it is shrunk to fit the window,
        // rather than adjusted up.
        mPopupWindow = createPopupWindow(createPopupContent(parentWidth*2, parentHeight*2));
        mPopupWindow.setWidth(WindowManager.LayoutParams.WRAP_CONTENT);
        mPopupWindow.setHeight(WindowManager.LayoutParams.WRAP_CONTENT);

        showPopup(R.id.anchor_middle);

        View popupRoot = mPopupWindow.getContentView();
        int measuredWidth = popupRoot.getMeasuredWidth();
        int measuredHeight = popupRoot.getMeasuredHeight();
        View anchor = mActivity.findViewById(R.id.anchor_middle);

        // The popup should occupy all available vertical space.
        int[] anchorLocationInWindowXY = new int[2];
        anchor.getLocationInWindow(anchorLocationInWindowXY);
        assertEquals(measuredHeight,
                parentHeight - (anchorLocationInWindowXY[1] + anchor.getHeight()));

        // The popup should be vertically aligned to the anchor's bottom edge.
        int[] anchorLocationOnScreenXY = new int[2];
        anchor.getLocationOnScreen(anchorLocationOnScreenXY);
        int[] popupLocationOnScreenXY = new int[2];
        popupRoot.getLocationOnScreen(popupLocationOnScreenXY);
        assertEquals(anchorLocationOnScreenXY[1] + anchor.getHeight(), popupLocationOnScreenXY[1]);
    }

    @Test
    public void testClipToScreenClipsToInsets() throws Throwable {
        int[] orientationValues = {ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT};
        int currentOrientation = mActivity.getResources().getConfiguration().orientation;
        if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            orientationValues[0] = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            orientationValues[1] = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        }

        for (int i = 0; i < 2; i++) {
            final int orientation = orientationValues[i];
            mActivity.runOnUiThread(() ->
                    mActivity.setRequestedOrientation(orientation));
            mActivity.waitForConfigurationChanged();

            View parentWindowView = mActivity.getWindow().getDecorView();
            int parentWidth = parentWindowView.getMeasuredWidth();
            int parentHeight = parentWindowView.getMeasuredHeight();

            mPopupWindow = createPopupWindow(createPopupContent(parentWidth*2, parentHeight*2));
            mPopupWindow.setWidth(WindowManager.LayoutParams.MATCH_PARENT);
            mPopupWindow.setHeight(WindowManager.LayoutParams.MATCH_PARENT);
            mPopupWindow.setClipToScreenEnabled(true);

            showPopup(R.id.anchor_upper_left);

            View popupRoot = mPopupWindow.getContentView().getRootView();
            int measuredWidth  = popupRoot.getMeasuredWidth();
            int measuredHeight = popupRoot.getMeasuredHeight();

            // The visible frame will not include the insets.
            Rect visibleFrame = new Rect();
            parentWindowView.getWindowVisibleDisplayFrame(visibleFrame);

            assertEquals(measuredWidth, visibleFrame.width());
            assertEquals(measuredHeight, visibleFrame.height());
        }
    }

    @Test
    public void testPositionAfterParentScroll() throws Throwable {
        View.OnScrollChangeListener scrollChangeListener = mock(
                View.OnScrollChangeListener.class);

        mActivityRule.runOnUiThread(() -> {
            mActivity.setContentView(R.layout.popup_window_scrollable);

            View anchor = mActivity.findViewById(R.id.anchor_upper);
            PopupWindow window = createPopupWindow();
            window.showAsDropDown(anchor);
        });

        mActivityRule.runOnUiThread(() -> {
            View parent = mActivity.findViewById(R.id.main_container);
            parent.scrollBy(0, 500);
            parent.setOnScrollChangeListener(scrollChangeListener);
        });

        verify(scrollChangeListener, never()).onScrollChange(
                any(View.class), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    public void testPositionAfterAnchorRemoval() throws Throwable {
        mPopupWindow = createPopupWindow(createPopupContent(CONTENT_SIZE_DP, CONTENT_SIZE_DP));
        showPopup(R.id.anchor_middle);

        final ViewGroup container = (ViewGroup) mActivity.findViewById(R.id.main_container);
        final View anchor = mActivity.findViewById(R.id.anchor_middle);
        final LayoutParams anchorLayoutParams = anchor.getLayoutParams();

        final int[] originalLocation = mPopupWindow.getContentView().getLocationOnScreen();

        final int deltaX = 30;
        final int deltaY = 20;

        // Scroll the container, the popup should move along with the anchor.
        WidgetTestUtils.runOnMainAndLayoutSync(
                mActivityRule,
                mPopupWindow.getContentView().getRootView(),
                () -> container.scrollBy(deltaX, deltaY),
                false  /* force layout */);
        assertPopupLocation(originalLocation, deltaX, deltaY);

        // Detach the anchor, the popup should stay in the same location.
        WidgetTestUtils.runOnMainAndLayoutSync(
                mActivityRule,
                mActivity.getWindow().getDecorView(),
                () -> container.removeView(anchor),
                false  /* force layout */);
        assertPopupLocation(originalLocation, deltaX, deltaY);

        // Scroll the container while the anchor is detached, the popup should not move.
        WidgetTestUtils.runOnMainAndLayoutSync(
                mActivityRule,
                mActivity.getWindow().getDecorView(),
                () -> container.scrollBy(deltaX, deltaY),
                true  /* force layout */);
        assertPopupLocation(originalLocation, deltaX, deltaY);

        // Re-attach the anchor, the popup should snap back to the new anchor location.
        WidgetTestUtils.runOnMainAndLayoutSync(
                mActivityRule,
                mPopupWindow.getContentView().getRootView(),
                () -> container.addView(anchor, anchorLayoutParams),
                false  /* force layout */);
        assertPopupLocation(originalLocation, deltaX * 2, deltaY * 2);
    }

    @Test
    public void testAnchorInPopup() throws Throwable {
        mPopupWindow = createPopupWindow(
                mActivity.getLayoutInflater().inflate(R.layout.popup_window, null));

        final PopupWindow subPopup =
                createPopupWindow(createPopupContent(CONTENT_SIZE_DP, CONTENT_SIZE_DP));

        // Check alignment without overlapping the anchor.
        assertFalse(subPopup.getOverlapAnchor());

        verifySubPopupPosition(subPopup, R.id.anchor_upper_left, R.id.anchor_lower_right,
                LEFT, EQUAL_TO, LEFT, TOP, EQUAL_TO, BOTTOM);
        verifySubPopupPosition(subPopup, R.id.anchor_middle_left, R.id.anchor_lower_right,
                LEFT, EQUAL_TO, LEFT, TOP, EQUAL_TO, BOTTOM);
        verifySubPopupPosition(subPopup, R.id.anchor_lower_left, R.id.anchor_lower_right,
                LEFT, EQUAL_TO, LEFT, BOTTOM, EQUAL_TO, TOP);

        verifySubPopupPosition(subPopup, R.id.anchor_upper, R.id.anchor_lower_right,
                LEFT, EQUAL_TO, LEFT, TOP, EQUAL_TO, BOTTOM);
        verifySubPopupPosition(subPopup, R.id.anchor_middle, R.id.anchor_lower_right,
                LEFT, EQUAL_TO, LEFT, TOP, EQUAL_TO, BOTTOM);
        verifySubPopupPosition(subPopup, R.id.anchor_lower, R.id.anchor_lower_right,
                LEFT, EQUAL_TO, LEFT, BOTTOM, EQUAL_TO, TOP);

        verifySubPopupPosition(subPopup, R.id.anchor_upper_right, R.id.anchor_lower_right,
                RIGHT, EQUAL_TO, RIGHT, TOP, EQUAL_TO, BOTTOM);
        verifySubPopupPosition(subPopup, R.id.anchor_middle_right, R.id.anchor_lower_right,
                RIGHT, EQUAL_TO, RIGHT, TOP, EQUAL_TO, BOTTOM);
        verifySubPopupPosition(subPopup, R.id.anchor_lower_right, R.id.anchor_lower_right,
                RIGHT, EQUAL_TO, RIGHT, BOTTOM, EQUAL_TO, TOP);

        // Check alignment while overlapping the anchor.
        subPopup.setOverlapAnchor(true);

        final int anchorHeight = mActivity.findViewById(R.id.anchor_lower_right).getHeight();
        // To simplify the math assert that all three lower anchors are the same height.
        assertEquals(anchorHeight, mActivity.findViewById(R.id.anchor_lower_left).getHeight());
        assertEquals(anchorHeight, mActivity.findViewById(R.id.anchor_lower).getHeight());

        final int verticalSpaceBelowAnchor = anchorHeight * 2;
        // Ensure that the subpopup is flipped vertically.
        subPopup.setHeight(verticalSpaceBelowAnchor + 1);

        verifySubPopupPosition(subPopup, R.id.anchor_upper_left, R.id.anchor_lower_right,
                LEFT, EQUAL_TO, LEFT, TOP, EQUAL_TO, TOP);
        verifySubPopupPosition(subPopup, R.id.anchor_middle_left, R.id.anchor_lower_right,
                LEFT, EQUAL_TO, LEFT, TOP, EQUAL_TO, TOP);
        verifySubPopupPosition(subPopup, R.id.anchor_lower_left, R.id.anchor_lower_right,
                LEFT, EQUAL_TO, LEFT, BOTTOM, EQUAL_TO, TOP);

        verifySubPopupPosition(subPopup, R.id.anchor_upper, R.id.anchor_lower_right,
                LEFT, EQUAL_TO, LEFT, TOP, EQUAL_TO, TOP);
        verifySubPopupPosition(subPopup, R.id.anchor_middle, R.id.anchor_lower_right,
                LEFT, EQUAL_TO, LEFT, TOP, EQUAL_TO, TOP);
        verifySubPopupPosition(subPopup, R.id.anchor_lower, R.id.anchor_lower_right,
                LEFT, EQUAL_TO, LEFT, BOTTOM, EQUAL_TO, TOP);

        verifySubPopupPosition(subPopup, R.id.anchor_upper_right, R.id.anchor_lower_right,
                RIGHT, EQUAL_TO, RIGHT, TOP, EQUAL_TO, TOP);
        verifySubPopupPosition(subPopup, R.id.anchor_middle_right, R.id.anchor_lower_right,
                RIGHT, EQUAL_TO, RIGHT, TOP, EQUAL_TO, TOP);
        verifySubPopupPosition(subPopup, R.id.anchor_lower_right, R.id.anchor_lower_right,
                RIGHT, EQUAL_TO, RIGHT, BOTTOM, EQUAL_TO, TOP);

        // Re-test for the bottom anchor row ensuring that the subpopup not flipped vertically.
        subPopup.setHeight(verticalSpaceBelowAnchor - 1);

        verifySubPopupPosition(subPopup, R.id.anchor_lower_left, R.id.anchor_lower_right,
                LEFT, EQUAL_TO, LEFT, TOP, EQUAL_TO, TOP);
        verifySubPopupPosition(subPopup, R.id.anchor_lower, R.id.anchor_lower_right,
                LEFT, EQUAL_TO, LEFT, TOP, EQUAL_TO, TOP);
        verifySubPopupPosition(subPopup, R.id.anchor_lower_right, R.id.anchor_lower_right,
                RIGHT, EQUAL_TO, RIGHT, TOP, EQUAL_TO, TOP);

        // Check that scrolling scrolls the sub popup along with the main popup.
        showPopup(R.id.anchor_middle);

        mActivityRule.runOnUiThread(() -> subPopup.showAsDropDown(
                mPopupWindow.getContentView().findViewById(R.id.anchor_middle)));
        mInstrumentation.waitForIdleSync();

        final int[] popupLocation = mPopupWindow.getContentView().getLocationOnScreen();
        final int[] subPopupLocation = subPopup.getContentView().getLocationOnScreen();

        final int deltaX = 20;
        final int deltaY = 30;

        final ViewGroup container = (ViewGroup) mActivity.findViewById(R.id.main_container);
        WidgetTestUtils.runOnMainAndLayoutSync(
                mActivityRule,
                subPopup.getContentView().getRootView(),
                () -> container.scrollBy(deltaX, deltaY),
                false  /* force layout */);

        final int[] newPopupLocation = mPopupWindow.getContentView().getLocationOnScreen();
        assertEquals(popupLocation[0] - deltaX, newPopupLocation[0]);
        assertEquals(popupLocation[1] - deltaY, newPopupLocation[1]);

        final int[] newSubPopupLocation = subPopup.getContentView().getLocationOnScreen();
        assertEquals(subPopupLocation[0] - deltaX, newSubPopupLocation[0]);
        assertEquals(subPopupLocation[1] - deltaY, newSubPopupLocation[1]);
    }

    private void verifySubPopupPosition(PopupWindow subPopup, int mainAnchorId, int subAnchorId,
            int contentEdgeX, int operatorX, int anchorEdgeX,
            int contentEdgeY, int operatorY, int anchorEdgeY) throws Throwable {
        showPopup(mainAnchorId);
        verifyPosition(subPopup, mPopupWindow.getContentView().findViewById(subAnchorId),
                contentEdgeX, operatorX, anchorEdgeX, contentEdgeY, operatorY, anchorEdgeY);
        dismissPopup();
    }

    private void assertPopupLocation(int[] originalLocation, int deltaX, int deltaY) {
        final int[] actualLocation = mPopupWindow.getContentView().getLocationOnScreen();
        assertEquals(originalLocation[0] - deltaX, actualLocation[0]);
        assertEquals(originalLocation[1] - deltaY, actualLocation[1]);
    }

    private static class BaseTransition extends Transition {
        @Override
        public void captureStartValues(TransitionValues transitionValues) {}

        @Override
        public void captureEndValues(TransitionValues transitionValues) {}
    }

    private View createPopupContent(int width, int height) {
        final View popupView = new View(mActivity);
        popupView.setLayoutParams(new ViewGroup.LayoutParams(width, height));
        popupView.setBackgroundColor(Color.MAGENTA);

        return popupView;
    }

    private PopupWindow createPopupWindow() {
        PopupWindow window = new PopupWindow(mActivity);
        window.setWidth(WINDOW_SIZE_DP);
        window.setHeight(WINDOW_SIZE_DP);
        window.setBackgroundDrawable(new ColorDrawable(Color.YELLOW));
        return window;
    }

    private PopupWindow createPopupWindow(View content) {
        PopupWindow window = createPopupWindow();
        window.setContentView(content);
        return window;
    }

    private void showPopup(int resourceId) throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            if (mPopupWindow == null || mPopupWindow.isShowing()) {
                return;
            }
            View anchor = mActivity.findViewById(resourceId);
            mPopupWindow.showAsDropDown(anchor);
            assertTrue(mPopupWindow.isShowing());
        });
        mInstrumentation.waitForIdleSync();
    }

    private void showPopup() throws Throwable {
        showPopup(R.id.anchor_upper_left);
    }

    private void dismissPopup() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            if (mPopupWindow == null || !mPopupWindow.isShowing()) {
                return;
            }
            mPopupWindow.dismiss();
        });
        mInstrumentation.waitForIdleSync();
    }
}
