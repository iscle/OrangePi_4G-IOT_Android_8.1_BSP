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

package android.view.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.app.Instrumentation;
import android.content.ClipData;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.SystemClock;
import android.os.Vibrator;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.util.Xml;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.Display;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.SoundEffectConstants;
import android.view.TouchDelegate;
import android.view.View;
import android.view.View.BaseSavedState;
import android.view.View.OnLongClickListener;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.android.compatibility.common.util.CtsMouseUtil;
import com.android.compatibility.common.util.CtsTouchUtils;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test {@link View}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class ViewTest {
    /** timeout delta when wait in case the system is sluggish */
    private static final long TIMEOUT_DELTA = 10000;

    private static final String LOG_TAG = "ViewTest";

    private Instrumentation mInstrumentation;
    private ViewTestCtsActivity mActivity;
    private Resources mResources;
    private MockViewParent mMockParent;

    @Rule
    public ActivityTestRule<ViewTestCtsActivity> mActivityRule =
            new ActivityTestRule<>(ViewTestCtsActivity.class);

    @Rule
    public ActivityTestRule<CtsActivity> mCtsActivityRule =
            new ActivityTestRule<>(CtsActivity.class, false, false);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
        PollingCheck.waitFor(mActivity::hasWindowFocus);
        mResources = mActivity.getResources();
        mMockParent = new MockViewParent(mActivity);
        assertTrue(mActivity.waitForWindowFocus(5 * DateUtils.SECOND_IN_MILLIS));
    }

    @Test
    public void testConstructor() {
        new View(mActivity);

        final XmlResourceParser parser = mResources.getLayout(R.layout.view_layout);
        final AttributeSet attrs = Xml.asAttributeSet(parser);
        new View(mActivity, attrs);

        new View(mActivity, null);

        new View(mActivity, attrs, 0);

        new View(mActivity, null, 1);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorNullContext1() {
        final XmlResourceParser parser = mResources.getLayout(R.layout.view_layout);
        final AttributeSet attrs = Xml.asAttributeSet(parser);
        new View(null, attrs);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorNullContext2() {
        new View(null, null, 1);
    }

    // Test that validates that Views can be constructed on a thread that
    // does not have a Looper. Necessary for async inflation
    private Pair<Class<?>, Throwable> sCtorException = null;

    @Test
    public void testConstructor2() throws Exception {
        final Object[] args = new Object[] { mActivity, null };
        final CountDownLatch latch = new CountDownLatch(1);
        sCtorException = null;
        new Thread() {
            @Override
            public void run() {
                final Class<?>[] ctorSignature = new Class[] {
                        Context.class, AttributeSet.class};
                for (Class<?> clazz : ASYNC_INFLATE_VIEWS) {
                    try {
                        Constructor<?> constructor = clazz.getConstructor(ctorSignature);
                        constructor.setAccessible(true);
                        constructor.newInstance(args);
                    } catch (Throwable t) {
                        sCtorException = new Pair<>(clazz, t);
                        break;
                    }
                }
                latch.countDown();
            }
        }.start();
        latch.await();
        if (sCtorException != null) {
            throw new AssertionError("Failed to inflate "
                    + sCtorException.first.getName(), sCtorException.second);
        }
    }

    @Test
    public void testGetContext() {
        View view = new View(mActivity);
        assertSame(mActivity, view.getContext());
    }

    @Test
    public void testGetResources() {
        View view = new View(mActivity);
        assertSame(mResources, view.getResources());
    }

    @Test
    public void testGetAnimation() {
        Animation animation = new AlphaAnimation(0.0f, 1.0f);
        View view = new View(mActivity);
        assertNull(view.getAnimation());

        view.setAnimation(animation);
        assertSame(animation, view.getAnimation());

        view.clearAnimation();
        assertNull(view.getAnimation());
    }

    @Test
    public void testSetAnimation() {
        Animation animation = new AlphaAnimation(0.0f, 1.0f);
        View view = new View(mActivity);
        assertNull(view.getAnimation());

        animation.initialize(100, 100, 100, 100);
        assertTrue(animation.isInitialized());
        view.setAnimation(animation);
        assertSame(animation, view.getAnimation());
        assertFalse(animation.isInitialized());

        view.setAnimation(null);
        assertNull(view.getAnimation());
    }

    @Test
    public void testClearAnimation() {
        Animation animation = new AlphaAnimation(0.0f, 1.0f);
        View view = new View(mActivity);

        assertNull(view.getAnimation());
        view.clearAnimation();
        assertNull(view.getAnimation());

        view.setAnimation(animation);
        assertNotNull(view.getAnimation());
        view.clearAnimation();
        assertNull(view.getAnimation());
    }

    @Test(expected=NullPointerException.class)
    public void testStartAnimationNull() {
        View view = new View(mActivity);
        view.startAnimation(null);
    }

    @Test
    public void testStartAnimation() {
        Animation animation = new AlphaAnimation(0.0f, 1.0f);
        View view = new View(mActivity);

        animation.setStartTime(1L);
        assertEquals(1L, animation.getStartTime());
        view.startAnimation(animation);
        assertEquals(Animation.START_ON_FIRST_FRAME, animation.getStartTime());
    }

    @Test
    public void testOnAnimation() throws Throwable {
        final Animation animation = new AlphaAnimation(0.0f, 1.0f);
        long duration = 2000L;
        animation.setDuration(duration);
        final MockView view = (MockView) mActivity.findViewById(R.id.mock_view);

        // check whether it has started
        mActivityRule.runOnUiThread(() -> view.startAnimation(animation));
        mInstrumentation.waitForIdleSync();

        PollingCheck.waitFor(view::hasCalledOnAnimationStart);

        // check whether it has ended after duration, and alpha changed during this time.
        PollingCheck.waitFor(duration + TIMEOUT_DELTA,
                () -> view.hasCalledOnSetAlpha() && view.hasCalledOnAnimationEnd());
    }

    @Test
    public void testGetParent() {
        MockView view = (MockView) mActivity.findViewById(R.id.mock_view);
        ViewGroup parent = (ViewGroup) mActivity.findViewById(R.id.viewlayout_root);
        assertSame(parent, view.getParent());
    }

    @Test
    public void testAccessScrollIndicators() {
        View view = mActivity.findViewById(R.id.viewlayout_root);

        assertEquals(View.SCROLL_INDICATOR_LEFT | View.SCROLL_INDICATOR_RIGHT,
                view.getScrollIndicators());
    }

    @Test
    public void testSetScrollIndicators() {
        View view = new View(mActivity);

        view.setScrollIndicators(0);
        assertEquals(0, view.getScrollIndicators());

        view.setScrollIndicators(View.SCROLL_INDICATOR_LEFT | View.SCROLL_INDICATOR_RIGHT);
        assertEquals(View.SCROLL_INDICATOR_LEFT | View.SCROLL_INDICATOR_RIGHT,
                view.getScrollIndicators());

        view.setScrollIndicators(View.SCROLL_INDICATOR_TOP, View.SCROLL_INDICATOR_TOP);
        assertEquals(View.SCROLL_INDICATOR_LEFT | View.SCROLL_INDICATOR_RIGHT
                        | View.SCROLL_INDICATOR_TOP, view.getScrollIndicators());

        view.setScrollIndicators(0, view.getScrollIndicators());
        assertEquals(0, view.getScrollIndicators());
    }

    @Test
    public void testFindViewById() {
        View parent = mActivity.findViewById(R.id.viewlayout_root);
        assertSame(parent, parent.findViewById(R.id.viewlayout_root));

        View view = parent.findViewById(R.id.mock_view);
        assertTrue(view instanceof MockView);
    }

    @Test
    public void testAccessTouchDelegate() throws Throwable {
        final MockView view = (MockView) mActivity.findViewById(R.id.mock_view);
        Rect rect = new Rect();
        final Button button = new Button(mActivity);
        final int WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT;
        final int btnHeight = view.getHeight()/3;
        mActivityRule.runOnUiThread(() -> mActivity.addContentView(button,
                new LinearLayout.LayoutParams(WRAP_CONTENT, btnHeight)));
        mInstrumentation.waitForIdleSync();
        button.getHitRect(rect);
        TouchDelegate delegate = spy(new TouchDelegate(rect, button));

        assertNull(view.getTouchDelegate());

        view.setTouchDelegate(delegate);
        assertSame(delegate, view.getTouchDelegate());
        verify(delegate, never()).onTouchEvent(any());
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, view);
        assertTrue(view.hasCalledOnTouchEvent());
        verify(delegate, times(1)).onTouchEvent(any());

        view.setTouchDelegate(null);
        assertNull(view.getTouchDelegate());
    }

    @Test
    public void testMouseEventCallsGetPointerIcon() {
        final MockView view = (MockView) mActivity.findViewById(R.id.mock_view);

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

        MotionEvent event = MotionEvent.obtain(0, eventTime, MotionEvent.ACTION_HOVER_MOVE,
                1, pointerIds, pointerCoords, 0, 0, 0, 0, 0, InputDevice.SOURCE_MOUSE, 0);
        mInstrumentation.sendPointerSync(event);
        mInstrumentation.waitForIdleSync();

        assertTrue(view.hasCalledOnResolvePointerIcon());

        final MockView view2 = (MockView) mActivity.findViewById(R.id.scroll_view);
        assertFalse(view2.hasCalledOnResolvePointerIcon());
    }

    @Test
    public void testAccessPointerIcon() {
        View view = mActivity.findViewById(R.id.pointer_icon_layout);
        MotionEvent event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_HOVER_MOVE, 0, 0, 0);

        // First view has pointerIcon="help"
        assertEquals(PointerIcon.getSystemIcon(mActivity, PointerIcon.TYPE_HELP),
                     view.onResolvePointerIcon(event, 0));

        // Second view inherits pointerIcon="crosshair" from the parent
        event.setLocation(0, 21);
        assertEquals(PointerIcon.getSystemIcon(mActivity, PointerIcon.TYPE_CROSSHAIR),
                     view.onResolvePointerIcon(event, 0));

        // Third view has custom pointer icon defined in a resource.
        event.setLocation(0, 41);
        assertNotNull(view.onResolvePointerIcon(event, 0));

        // Parent view has pointerIcon="crosshair"
        event.setLocation(0, 61);
        assertEquals(PointerIcon.getSystemIcon(mActivity, PointerIcon.TYPE_CROSSHAIR),
                     view.onResolvePointerIcon(event, 0));

        // Outside of the parent view, no pointer icon defined.
        event.setLocation(0, 71);
        assertNull(view.onResolvePointerIcon(event, 0));

        view.setPointerIcon(PointerIcon.getSystemIcon(mActivity, PointerIcon.TYPE_TEXT));
        assertEquals(PointerIcon.getSystemIcon(mActivity, PointerIcon.TYPE_TEXT),
                     view.onResolvePointerIcon(event, 0));
        event.recycle();
    }

    @Test
    public void testPointerIconOverlap() throws Throwable {
        View parent = mActivity.findViewById(R.id.pointer_icon_overlap);
        View child1 = mActivity.findViewById(R.id.pointer_icon_overlap_child1);
        View child2 = mActivity.findViewById(R.id.pointer_icon_overlap_child2);
        View child3 = mActivity.findViewById(R.id.pointer_icon_overlap_child3);

        PointerIcon iconParent = PointerIcon.getSystemIcon(mActivity, PointerIcon.TYPE_HAND);
        PointerIcon iconChild1 = PointerIcon.getSystemIcon(mActivity, PointerIcon.TYPE_HELP);
        PointerIcon iconChild2 = PointerIcon.getSystemIcon(mActivity, PointerIcon.TYPE_TEXT);
        PointerIcon iconChild3 = PointerIcon.getSystemIcon(mActivity, PointerIcon.TYPE_GRAB);

        parent.setPointerIcon(iconParent);
        child1.setPointerIcon(iconChild1);
        child2.setPointerIcon(iconChild2);
        child3.setPointerIcon(iconChild3);

        MotionEvent event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_HOVER_MOVE, 0, 0, 0);

        assertEquals(iconChild3, parent.onResolvePointerIcon(event, 0));

        setVisibilityOnUiThread(child3, View.GONE);
        assertEquals(iconChild2, parent.onResolvePointerIcon(event, 0));

        child2.setPointerIcon(null);
        assertEquals(iconChild1, parent.onResolvePointerIcon(event, 0));

        setVisibilityOnUiThread(child1, View.GONE);
        assertEquals(iconParent, parent.onResolvePointerIcon(event, 0));

        event.recycle();
    }

    @Test
    public void testCreatePointerIcons() {
        assertSystemPointerIcon(PointerIcon.TYPE_NULL);
        assertSystemPointerIcon(PointerIcon.TYPE_DEFAULT);
        assertSystemPointerIcon(PointerIcon.TYPE_ARROW);
        assertSystemPointerIcon(PointerIcon.TYPE_CONTEXT_MENU);
        assertSystemPointerIcon(PointerIcon.TYPE_HAND);
        assertSystemPointerIcon(PointerIcon.TYPE_HELP);
        assertSystemPointerIcon(PointerIcon.TYPE_WAIT);
        assertSystemPointerIcon(PointerIcon.TYPE_CELL);
        assertSystemPointerIcon(PointerIcon.TYPE_CROSSHAIR);
        assertSystemPointerIcon(PointerIcon.TYPE_TEXT);
        assertSystemPointerIcon(PointerIcon.TYPE_VERTICAL_TEXT);
        assertSystemPointerIcon(PointerIcon.TYPE_ALIAS);
        assertSystemPointerIcon(PointerIcon.TYPE_COPY);
        assertSystemPointerIcon(PointerIcon.TYPE_NO_DROP);
        assertSystemPointerIcon(PointerIcon.TYPE_ALL_SCROLL);
        assertSystemPointerIcon(PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW);
        assertSystemPointerIcon(PointerIcon.TYPE_VERTICAL_DOUBLE_ARROW);
        assertSystemPointerIcon(PointerIcon.TYPE_TOP_RIGHT_DIAGONAL_DOUBLE_ARROW);
        assertSystemPointerIcon(PointerIcon.TYPE_TOP_LEFT_DIAGONAL_DOUBLE_ARROW);
        assertSystemPointerIcon(PointerIcon.TYPE_ZOOM_IN);
        assertSystemPointerIcon(PointerIcon.TYPE_ZOOM_OUT);
        assertSystemPointerIcon(PointerIcon.TYPE_GRAB);

        assertNotNull(PointerIcon.load(mResources, R.drawable.custom_pointer_icon));

        Bitmap bitmap = BitmapFactory.decodeResource(mResources, R.drawable.icon_blue);
        assertNotNull(PointerIcon.create(bitmap, 0, 0));
    }

    private void assertSystemPointerIcon(int style) {
        assertNotNull(PointerIcon.getSystemIcon(mActivity, style));
    }

    @UiThreadTest
    @Test
    public void testAccessTag() {
        ViewGroup viewGroup = (ViewGroup) mActivity.findViewById(R.id.viewlayout_root);
        MockView mockView = (MockView) mActivity.findViewById(R.id.mock_view);
        MockView scrollView = (MockView) mActivity.findViewById(R.id.scroll_view);

        ViewData viewData = new ViewData();
        viewData.childCount = 3;
        viewData.tag = "linearLayout";
        viewData.firstChild = mockView;
        viewGroup.setTag(viewData);
        viewGroup.setFocusable(true);
        assertSame(viewData, viewGroup.getTag());

        final String tag = "mock";
        assertNull(mockView.getTag());
        mockView.setTag(tag);
        assertEquals(tag, mockView.getTag());

        scrollView.setTag(viewGroup);
        assertSame(viewGroup, scrollView.getTag());

        assertSame(viewGroup, viewGroup.findViewWithTag(viewData));
        assertSame(mockView, viewGroup.findViewWithTag(tag));
        assertSame(scrollView, viewGroup.findViewWithTag(viewGroup));

        mockView.setTag(null);
        assertNull(mockView.getTag());
    }

    @Test
    public void testOnSizeChanged() throws Throwable {
        final ViewGroup viewGroup = (ViewGroup) mActivity.findViewById(R.id.viewlayout_root);
        final MockView mockView = new MockView(mActivity);
        assertEquals(-1, mockView.getOldWOnSizeChanged());
        assertEquals(-1, mockView.getOldHOnSizeChanged());
        mActivityRule.runOnUiThread(() -> viewGroup.addView(mockView));
        mInstrumentation.waitForIdleSync();
        assertTrue(mockView.hasCalledOnSizeChanged());
        assertEquals(0, mockView.getOldWOnSizeChanged());
        assertEquals(0, mockView.getOldHOnSizeChanged());

        final MockView view = (MockView) mActivity.findViewById(R.id.mock_view);
        assertTrue(view.hasCalledOnSizeChanged());
        view.reset();
        assertEquals(-1, view.getOldWOnSizeChanged());
        assertEquals(-1, view.getOldHOnSizeChanged());
        int oldw = view.getWidth();
        int oldh = view.getHeight();
        final LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(200, 100);
        mActivityRule.runOnUiThread(() -> view.setLayoutParams(layoutParams));
        mInstrumentation.waitForIdleSync();
        assertTrue(view.hasCalledOnSizeChanged());
        assertEquals(oldw, view.getOldWOnSizeChanged());
        assertEquals(oldh, view.getOldHOnSizeChanged());
    }


    @Test(expected=NullPointerException.class)
    public void testGetHitRectNull() {
        MockView view = new MockView(mActivity);
        view.getHitRect(null);
    }

    @Test
    public void testGetHitRect() {
        Rect outRect = new Rect();
        View mockView = mActivity.findViewById(R.id.mock_view);
        mockView.getHitRect(outRect);
        assertEquals(0, outRect.left);
        assertEquals(0, outRect.top);
        assertEquals(mockView.getWidth(), outRect.right);
        assertEquals(mockView.getHeight(), outRect.bottom);
    }

    @Test
    public void testForceLayout() {
        View view = new View(mActivity);

        assertFalse(view.isLayoutRequested());
        view.forceLayout();
        assertTrue(view.isLayoutRequested());

        view.forceLayout();
        assertTrue(view.isLayoutRequested());
    }

    @Test
    public void testIsLayoutRequested() {
        View view = new View(mActivity);

        assertFalse(view.isLayoutRequested());
        view.forceLayout();
        assertTrue(view.isLayoutRequested());

        view.layout(0, 0, 0, 0);
        assertFalse(view.isLayoutRequested());
    }

    @Test
    public void testRequestLayout() {
        MockView view = new MockView(mActivity);
        assertFalse(view.isLayoutRequested());
        assertNull(view.getParent());

        view.requestLayout();
        assertTrue(view.isLayoutRequested());

        view.setParent(mMockParent);
        assertTrue(mMockParent.hasRequestLayout());

        mMockParent.reset();
        view.requestLayout();
        assertTrue(view.isLayoutRequested());
        assertTrue(mMockParent.hasRequestLayout());
    }

    @Test
    public void testLayout() throws Throwable {
        final MockView view = (MockView) mActivity.findViewById(R.id.mock_view);
        assertTrue(view.hasCalledOnLayout());

        view.reset();
        assertFalse(view.hasCalledOnLayout());
        mActivityRule.runOnUiThread(view::requestLayout);
        mInstrumentation.waitForIdleSync();
        assertTrue(view.hasCalledOnLayout());
    }

    @Test
    public void testGetBaseline() {
        View view = new View(mActivity);

        assertEquals(-1, view.getBaseline());
    }

    @Test
    public void testAccessBackground() {
        View view = new View(mActivity);
        Drawable d1 = mResources.getDrawable(R.drawable.scenery);
        Drawable d2 = mResources.getDrawable(R.drawable.pass);

        assertNull(view.getBackground());

        view.setBackgroundDrawable(d1);
        assertEquals(d1, view.getBackground());

        view.setBackgroundDrawable(d2);
        assertEquals(d2, view.getBackground());

        view.setBackgroundDrawable(null);
        assertNull(view.getBackground());
    }

    @Test
    public void testSetBackgroundResource() {
        View view = new View(mActivity);

        assertNull(view.getBackground());

        view.setBackgroundResource(R.drawable.pass);
        assertNotNull(view.getBackground());

        view.setBackgroundResource(0);
        assertNull(view.getBackground());
    }

    @Test
    public void testAccessDrawingCacheBackgroundColor() {
        View view = new View(mActivity);

        assertEquals(0, view.getDrawingCacheBackgroundColor());

        view.setDrawingCacheBackgroundColor(0xFF00FF00);
        assertEquals(0xFF00FF00, view.getDrawingCacheBackgroundColor());

        view.setDrawingCacheBackgroundColor(-1);
        assertEquals(-1, view.getDrawingCacheBackgroundColor());
    }

    @Test
    public void testSetBackgroundColor() {
        View view = new View(mActivity);
        ColorDrawable colorDrawable;
        assertNull(view.getBackground());

        view.setBackgroundColor(0xFFFF0000);
        colorDrawable = (ColorDrawable) view.getBackground();
        assertNotNull(colorDrawable);
        assertEquals(0xFF, colorDrawable.getAlpha());

        view.setBackgroundColor(0);
        colorDrawable = (ColorDrawable) view.getBackground();
        assertNotNull(colorDrawable);
        assertEquals(0, colorDrawable.getAlpha());
    }

    @Test
    public void testVerifyDrawable() {
        MockView view = new MockView(mActivity);
        Drawable d1 = mResources.getDrawable(R.drawable.scenery);
        Drawable d2 = mResources.getDrawable(R.drawable.pass);

        assertNull(view.getBackground());
        assertTrue(view.verifyDrawable(null));
        assertFalse(view.verifyDrawable(d1));

        view.setBackgroundDrawable(d1);
        assertTrue(view.verifyDrawable(d1));
        assertFalse(view.verifyDrawable(d2));
    }

    @Test
    public void testGetDrawingRect() {
        MockView view = new MockView(mActivity);
        Rect outRect = new Rect();

        view.getDrawingRect(outRect);
        assertEquals(0, outRect.left);
        assertEquals(0, outRect.top);
        assertEquals(0, outRect.right);
        assertEquals(0, outRect.bottom);

        view.scrollTo(10, 100);
        view.getDrawingRect(outRect);
        assertEquals(10, outRect.left);
        assertEquals(100, outRect.top);
        assertEquals(10, outRect.right);
        assertEquals(100, outRect.bottom);

        View mockView = mActivity.findViewById(R.id.mock_view);
        mockView.getDrawingRect(outRect);
        assertEquals(0, outRect.left);
        assertEquals(0, outRect.top);
        assertEquals(mockView.getWidth(), outRect.right);
        assertEquals(mockView.getHeight(), outRect.bottom);
    }

    @Test
    public void testGetFocusedRect() {
        MockView view = new MockView(mActivity);
        Rect outRect = new Rect();

        view.getFocusedRect(outRect);
        assertEquals(0, outRect.left);
        assertEquals(0, outRect.top);
        assertEquals(0, outRect.right);
        assertEquals(0, outRect.bottom);

        view.scrollTo(10, 100);
        view.getFocusedRect(outRect);
        assertEquals(10, outRect.left);
        assertEquals(100, outRect.top);
        assertEquals(10, outRect.right);
        assertEquals(100, outRect.bottom);
    }

    @Test
    public void testGetGlobalVisibleRectPoint() throws Throwable {
        final View view = mActivity.findViewById(R.id.mock_view);
        final ViewGroup viewGroup = (ViewGroup) mActivity.findViewById(R.id.viewlayout_root);
        Rect rect = new Rect();
        Point point = new Point();

        assertTrue(view.getGlobalVisibleRect(rect, point));
        Rect rcParent = new Rect();
        Point ptParent = new Point();
        viewGroup.getGlobalVisibleRect(rcParent, ptParent);
        assertEquals(rcParent.left, rect.left);
        assertEquals(rcParent.top, rect.top);
        assertEquals(rect.left + view.getWidth(), rect.right);
        assertEquals(rect.top + view.getHeight(), rect.bottom);
        assertEquals(ptParent.x, point.x);
        assertEquals(ptParent.y, point.y);

        // width is 0
        final LinearLayout.LayoutParams layoutParams1 = new LinearLayout.LayoutParams(0, 300);
        mActivityRule.runOnUiThread(() -> view.setLayoutParams(layoutParams1));
        mInstrumentation.waitForIdleSync();
        assertFalse(view.getGlobalVisibleRect(rect, point));

        // height is -10
        final LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(200, -10);
        mActivityRule.runOnUiThread(() -> view.setLayoutParams(layoutParams2));
        mInstrumentation.waitForIdleSync();
        assertFalse(view.getGlobalVisibleRect(rect, point));

        Display display = mActivity.getWindowManager().getDefaultDisplay();
        int halfWidth = display.getWidth() / 2;
        int halfHeight = display.getHeight() /2;

        final LinearLayout.LayoutParams layoutParams3 =
                new LinearLayout.LayoutParams(halfWidth, halfHeight);
        mActivityRule.runOnUiThread(() -> view.setLayoutParams(layoutParams3));
        mInstrumentation.waitForIdleSync();
        assertTrue(view.getGlobalVisibleRect(rect, point));
        assertEquals(rcParent.left, rect.left);
        assertEquals(rcParent.top, rect.top);
        assertEquals(rect.left + halfWidth, rect.right);
        assertEquals(rect.top + halfHeight, rect.bottom);
        assertEquals(ptParent.x, point.x);
        assertEquals(ptParent.y, point.y);
    }

    @Test
    public void testGetGlobalVisibleRect() throws Throwable {
        final View view = mActivity.findViewById(R.id.mock_view);
        final ViewGroup viewGroup = (ViewGroup) mActivity.findViewById(R.id.viewlayout_root);
        Rect rect = new Rect();

        assertTrue(view.getGlobalVisibleRect(rect));
        Rect rcParent = new Rect();
        viewGroup.getGlobalVisibleRect(rcParent);
        assertEquals(rcParent.left, rect.left);
        assertEquals(rcParent.top, rect.top);
        assertEquals(rect.left + view.getWidth(), rect.right);
        assertEquals(rect.top + view.getHeight(), rect.bottom);

        // width is 0
        final LinearLayout.LayoutParams layoutParams1 = new LinearLayout.LayoutParams(0, 300);
        mActivityRule.runOnUiThread(() -> view.setLayoutParams(layoutParams1));
        mInstrumentation.waitForIdleSync();
        assertFalse(view.getGlobalVisibleRect(rect));

        // height is -10
        final LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(200, -10);
        mActivityRule.runOnUiThread(() -> view.setLayoutParams(layoutParams2));
        mInstrumentation.waitForIdleSync();
        assertFalse(view.getGlobalVisibleRect(rect));

        Display display = mActivity.getWindowManager().getDefaultDisplay();
        int halfWidth = display.getWidth() / 2;
        int halfHeight = display.getHeight() /2;

        final LinearLayout.LayoutParams layoutParams3 =
                new LinearLayout.LayoutParams(halfWidth, halfHeight);
        mActivityRule.runOnUiThread(() -> view.setLayoutParams(layoutParams3));
        mInstrumentation.waitForIdleSync();
        assertTrue(view.getGlobalVisibleRect(rect));
        assertEquals(rcParent.left, rect.left);
        assertEquals(rcParent.top, rect.top);
        assertEquals(rect.left + halfWidth, rect.right);
        assertEquals(rect.top + halfHeight, rect.bottom);
    }

    @Test
    public void testComputeHorizontalScroll() throws Throwable {
        final MockView view = (MockView) mActivity.findViewById(R.id.mock_view);

        assertEquals(0, view.computeHorizontalScrollOffset());
        assertEquals(view.getWidth(), view.computeHorizontalScrollRange());
        assertEquals(view.getWidth(), view.computeHorizontalScrollExtent());

        mActivityRule.runOnUiThread(() -> view.scrollTo(12, 0));
        mInstrumentation.waitForIdleSync();
        assertEquals(12, view.computeHorizontalScrollOffset());
        assertEquals(view.getWidth(), view.computeHorizontalScrollRange());
        assertEquals(view.getWidth(), view.computeHorizontalScrollExtent());

        mActivityRule.runOnUiThread(() -> view.scrollBy(12, 0));
        mInstrumentation.waitForIdleSync();
        assertEquals(24, view.computeHorizontalScrollOffset());
        assertEquals(view.getWidth(), view.computeHorizontalScrollRange());
        assertEquals(view.getWidth(), view.computeHorizontalScrollExtent());

        int newWidth = 200;
        final LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(newWidth, 100);
        mActivityRule.runOnUiThread(() -> view.setLayoutParams(layoutParams));
        mInstrumentation.waitForIdleSync();
        assertEquals(24, view.computeHorizontalScrollOffset());
        assertEquals(newWidth, view.getWidth());
        assertEquals(view.getWidth(), view.computeHorizontalScrollRange());
        assertEquals(view.getWidth(), view.computeHorizontalScrollExtent());
    }

    @Test
    public void testComputeVerticalScroll() throws Throwable {
        final MockView view = (MockView) mActivity.findViewById(R.id.mock_view);

        assertEquals(0, view.computeVerticalScrollOffset());
        assertEquals(view.getHeight(), view.computeVerticalScrollRange());
        assertEquals(view.getHeight(), view.computeVerticalScrollExtent());

        final int scrollToY = 34;
        mActivityRule.runOnUiThread(() -> view.scrollTo(0, scrollToY));
        mInstrumentation.waitForIdleSync();
        assertEquals(scrollToY, view.computeVerticalScrollOffset());
        assertEquals(view.getHeight(), view.computeVerticalScrollRange());
        assertEquals(view.getHeight(), view.computeVerticalScrollExtent());

        final int scrollByY = 200;
        mActivityRule.runOnUiThread(() -> view.scrollBy(0, scrollByY));
        mInstrumentation.waitForIdleSync();
        assertEquals(scrollToY + scrollByY, view.computeVerticalScrollOffset());
        assertEquals(view.getHeight(), view.computeVerticalScrollRange());
        assertEquals(view.getHeight(), view.computeVerticalScrollExtent());

        int newHeight = 333;
        final LinearLayout.LayoutParams layoutParams =
                new LinearLayout.LayoutParams(200, newHeight);
        mActivityRule.runOnUiThread(() -> view.setLayoutParams(layoutParams));
        mInstrumentation.waitForIdleSync();
        assertEquals(scrollToY + scrollByY, view.computeVerticalScrollOffset());
        assertEquals(newHeight, view.getHeight());
        assertEquals(view.getHeight(), view.computeVerticalScrollRange());
        assertEquals(view.getHeight(), view.computeVerticalScrollExtent());
    }

    @Test
    public void testGetFadingEdgeStrength() throws Throwable {
        final MockView view = (MockView) mActivity.findViewById(R.id.mock_view);

        assertEquals(0f, view.getLeftFadingEdgeStrength(), 0.0f);
        assertEquals(0f, view.getRightFadingEdgeStrength(), 0.0f);
        assertEquals(0f, view.getTopFadingEdgeStrength(), 0.0f);
        assertEquals(0f, view.getBottomFadingEdgeStrength(), 0.0f);

        mActivityRule.runOnUiThread(() -> view.scrollTo(10, 10));
        mInstrumentation.waitForIdleSync();
        assertEquals(1f, view.getLeftFadingEdgeStrength(), 0.0f);
        assertEquals(0f, view.getRightFadingEdgeStrength(), 0.0f);
        assertEquals(1f, view.getTopFadingEdgeStrength(), 0.0f);
        assertEquals(0f, view.getBottomFadingEdgeStrength(), 0.0f);

        mActivityRule.runOnUiThread(() -> view.scrollTo(-10, -10));
        mInstrumentation.waitForIdleSync();
        assertEquals(0f, view.getLeftFadingEdgeStrength(), 0.0f);
        assertEquals(1f, view.getRightFadingEdgeStrength(), 0.0f);
        assertEquals(0f, view.getTopFadingEdgeStrength(), 0.0f);
        assertEquals(1f, view.getBottomFadingEdgeStrength(), 0.0f);
    }

    @Test
    public void testGetLeftFadingEdgeStrength() {
        MockView view = new MockView(mActivity);

        assertEquals(0.0f, view.getLeftFadingEdgeStrength(), 0.0f);

        view.scrollTo(1, 0);
        assertEquals(1.0f, view.getLeftFadingEdgeStrength(), 0.0f);
    }

    @Test
    public void testGetRightFadingEdgeStrength() {
        MockView view = new MockView(mActivity);

        assertEquals(0.0f, view.getRightFadingEdgeStrength(), 0.0f);

        view.scrollTo(-1, 0);
        assertEquals(1.0f, view.getRightFadingEdgeStrength(), 0.0f);
    }

    @Test
    public void testGetBottomFadingEdgeStrength() {
        MockView view = new MockView(mActivity);

        assertEquals(0.0f, view.getBottomFadingEdgeStrength(), 0.0f);

        view.scrollTo(0, -2);
        assertEquals(1.0f, view.getBottomFadingEdgeStrength(), 0.0f);
    }

    @Test
    public void testGetTopFadingEdgeStrength() {
        MockView view = new MockView(mActivity);

        assertEquals(0.0f, view.getTopFadingEdgeStrength(), 0.0f);

        view.scrollTo(0, 2);
        assertEquals(1.0f, view.getTopFadingEdgeStrength(), 0.0f);
    }

    @Test
    public void testResolveSize() {
        assertEquals(50, View.resolveSize(50, View.MeasureSpec.UNSPECIFIED));

        assertEquals(40, View.resolveSize(50, 40 | View.MeasureSpec.EXACTLY));

        assertEquals(30, View.resolveSize(50, 30 | View.MeasureSpec.AT_MOST));

        assertEquals(20, View.resolveSize(20, 30 | View.MeasureSpec.AT_MOST));
    }

    @Test
    public void testGetDefaultSize() {
        assertEquals(50, View.getDefaultSize(50, View.MeasureSpec.UNSPECIFIED));

        assertEquals(40, View.getDefaultSize(50, 40 | View.MeasureSpec.EXACTLY));

        assertEquals(30, View.getDefaultSize(50, 30 | View.MeasureSpec.AT_MOST));

        assertEquals(30, View.getDefaultSize(20, 30 | View.MeasureSpec.AT_MOST));
    }

    @Test
    public void testAccessId() {
        View view = new View(mActivity);

        assertEquals(View.NO_ID, view.getId());

        view.setId(10);
        assertEquals(10, view.getId());

        view.setId(0xFFFFFFFF);
        assertEquals(0xFFFFFFFF, view.getId());
    }

    @Test
    public void testAccessLongClickable() {
        View view = new View(mActivity);

        assertFalse(view.isLongClickable());

        view.setLongClickable(true);
        assertTrue(view.isLongClickable());

        view.setLongClickable(false);
        assertFalse(view.isLongClickable());
    }

    @Test
    public void testAccessClickable() {
        View view = new View(mActivity);

        assertFalse(view.isClickable());

        view.setClickable(true);
        assertTrue(view.isClickable());

        view.setClickable(false);
        assertFalse(view.isClickable());
    }

    @Test
    public void testAccessContextClickable() {
        View view = new View(mActivity);

        assertFalse(view.isContextClickable());

        view.setContextClickable(true);
        assertTrue(view.isContextClickable());

        view.setContextClickable(false);
        assertFalse(view.isContextClickable());
    }

    @Test
    public void testGetContextMenuInfo() {
        MockView view = new MockView(mActivity);

        assertNull(view.getContextMenuInfo());
    }

    @Test
    public void testSetOnCreateContextMenuListener() {
        View view = new View(mActivity);
        assertFalse(view.isLongClickable());

        view.setOnCreateContextMenuListener(null);
        assertTrue(view.isLongClickable());

        view.setOnCreateContextMenuListener(mock(View.OnCreateContextMenuListener.class));
        assertTrue(view.isLongClickable());
    }

    @Test
    public void testCreateContextMenu() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            View.OnCreateContextMenuListener listener =
                    mock(View.OnCreateContextMenuListener.class);
            MockView view = new MockView(mActivity);
            mActivity.setContentView(view);
            mActivity.registerForContextMenu(view);
            view.setOnCreateContextMenuListener(listener);
            assertFalse(view.hasCalledOnCreateContextMenu());
            verifyZeroInteractions(listener);

            view.showContextMenu();
            assertTrue(view.hasCalledOnCreateContextMenu());
            verify(listener, times(1)).onCreateContextMenu(
                    any(), eq(view), any());
        });
    }

    @Test(expected=NullPointerException.class)
    public void testCreateContextMenuNull() {
        MockView view = new MockView(mActivity);
        view.createContextMenu(null);
    }

    @Test
    public void testAddFocusables() {
        View view = new View(mActivity);
        ArrayList<View> viewList = new ArrayList<>();

        // view is not focusable
        assertFalse(view.isFocusable());
        assertEquals(0, viewList.size());
        view.addFocusables(viewList, 0);
        assertEquals(0, viewList.size());

        // view is focusable
        view.setFocusable(true);
        view.addFocusables(viewList, 0);
        assertEquals(1, viewList.size());
        assertEquals(view, viewList.get(0));

        // null array should be ignored
        view.addFocusables(null, 0);
    }

    @Test
    public void testGetFocusables() {
        View view = new View(mActivity);
        ArrayList<View> viewList;

        // view is not focusable
        assertFalse(view.isFocusable());
        viewList = view.getFocusables(0);
        assertEquals(0, viewList.size());

        // view is focusable
        view.setFocusable(true);
        viewList = view.getFocusables(0);
        assertEquals(1, viewList.size());
        assertEquals(view, viewList.get(0));

        viewList = view.getFocusables(-1);
        assertEquals(1, viewList.size());
        assertEquals(view, viewList.get(0));
    }

    @Test
    public void testAddFocusablesWithoutTouchMode() {
        View view = new View(mActivity);
        assertFalse("test sanity", view.isInTouchMode());
        focusableInTouchModeTest(view, false);
    }

    @Test
    public void testAddFocusablesInTouchMode() {
        View view = spy(new View(mActivity));
        when(view.isInTouchMode()).thenReturn(true);
        focusableInTouchModeTest(view, true);
    }

    private void focusableInTouchModeTest(View view, boolean inTouchMode) {
        ArrayList<View> views = new ArrayList<>();

        view.setFocusableInTouchMode(false);
        view.setFocusable(true);

        view.addFocusables(views, View.FOCUS_FORWARD);
        if (inTouchMode) {
            assertEquals(Collections.emptyList(), views);
        } else {
            assertEquals(Collections.singletonList(view), views);
        }

        views.clear();
        view.addFocusables(views, View.FOCUS_FORWARD, View.FOCUSABLES_ALL);
        assertEquals(Collections.singletonList(view), views);

        views.clear();
        view.addFocusables(views, View.FOCUS_FORWARD, View.FOCUSABLES_TOUCH_MODE);
        assertEquals(Collections.emptyList(), views);

        view.setFocusableInTouchMode(true);

        views.clear();
        view.addFocusables(views, View.FOCUS_FORWARD);
        assertEquals(Collections.singletonList(view), views);

        views.clear();
        view.addFocusables(views, View.FOCUS_FORWARD, View.FOCUSABLES_ALL);
        assertEquals(Collections.singletonList(view), views);

        views.clear();
        view.addFocusables(views, View.FOCUS_FORWARD, View.FOCUSABLES_TOUCH_MODE);
        assertEquals(Collections.singletonList(view), views);

        view.setFocusable(false);

        views.clear();
        view.addFocusables(views, View.FOCUS_FORWARD);
        assertEquals(Collections.emptyList(), views);

        views.clear();
        view.addFocusables(views, View.FOCUS_FORWARD, View.FOCUSABLES_ALL);
        assertEquals(Collections.emptyList(), views);

        views.clear();
        view.addFocusables(views, View.FOCUS_FORWARD, View.FOCUSABLES_TOUCH_MODE);
        assertEquals(Collections.emptyList(), views);
    }

    @Test
    public void testAddKeyboardNavigationClusters() {
        View view = new View(mActivity);
        ArrayList<View> viewList = new ArrayList<>();

        // View is not a keyboard navigation cluster
        assertFalse(view.isKeyboardNavigationCluster());
        view.addKeyboardNavigationClusters(viewList, 0);
        assertEquals(0, viewList.size());

        // View is a cluster (but not focusable, so technically empty)
        view.setKeyboardNavigationCluster(true);
        view.addKeyboardNavigationClusters(viewList, 0);
        assertEquals(0, viewList.size());
        viewList.clear();
        // a focusable cluster is not-empty
        view.setFocusableInTouchMode(true);
        view.addKeyboardNavigationClusters(viewList, 0);
        assertEquals(1, viewList.size());
        assertEquals(view, viewList.get(0));
    }

    @Test
    public void testKeyboardNavigationClusterSearch() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            ViewGroup decorView = (ViewGroup) mActivity.getWindow().getDecorView();
            decorView.removeAllViews();
            View v1 = new MockView(mActivity);
            v1.setFocusableInTouchMode(true);
            View v2 = new MockView(mActivity);
            v2.setFocusableInTouchMode(true);
            decorView.addView(v1);
            decorView.addView(v2);

            // Searching for clusters.
            v1.setKeyboardNavigationCluster(true);
            v2.setKeyboardNavigationCluster(true);
            assertEquals(v2, decorView.keyboardNavigationClusterSearch(v1, View.FOCUS_FORWARD));
            assertEquals(v1, decorView.keyboardNavigationClusterSearch(null, View.FOCUS_FORWARD));
            assertEquals(v2, decorView.keyboardNavigationClusterSearch(null, View.FOCUS_BACKWARD));
            assertEquals(v2, v1.keyboardNavigationClusterSearch(null, View.FOCUS_FORWARD));
            assertEquals(decorView, v1.keyboardNavigationClusterSearch(null, View.FOCUS_BACKWARD));
            assertEquals(decorView, v2.keyboardNavigationClusterSearch(null, View.FOCUS_FORWARD));
            assertEquals(v1, v2.keyboardNavigationClusterSearch(null, View.FOCUS_BACKWARD));

            // Clusters in 3-level hierarchy.
            decorView.removeAllViews();
            LinearLayout middle = new LinearLayout(mActivity);
            middle.addView(v1);
            middle.addView(v2);
            decorView.addView(middle);
            assertEquals(decorView, v2.keyboardNavigationClusterSearch(null, View.FOCUS_FORWARD));
        });
    }

    @Test
    public void testGetRootView() {
        MockView view = new MockView(mActivity);

        assertNull(view.getParent());
        assertEquals(view, view.getRootView());

        view.setParent(mMockParent);
        assertEquals(mMockParent, view.getRootView());
    }

    @Test
    public void testGetSolidColor() {
        View view = new View(mActivity);

        assertEquals(0, view.getSolidColor());
    }

    @Test
    public void testSetMinimumWidth() {
        MockView view = new MockView(mActivity);
        assertEquals(0, view.getSuggestedMinimumWidth());

        view.setMinimumWidth(100);
        assertEquals(100, view.getSuggestedMinimumWidth());

        view.setMinimumWidth(-100);
        assertEquals(-100, view.getSuggestedMinimumWidth());
    }

    @Test
    public void testGetSuggestedMinimumWidth() {
        MockView view = new MockView(mActivity);
        Drawable d = mResources.getDrawable(R.drawable.scenery);
        int drawableMinimumWidth = d.getMinimumWidth();

        // drawable is null
        view.setMinimumWidth(100);
        assertNull(view.getBackground());
        assertEquals(100, view.getSuggestedMinimumWidth());

        // drawable minimum width is larger than mMinWidth
        view.setBackgroundDrawable(d);
        view.setMinimumWidth(drawableMinimumWidth - 10);
        assertEquals(drawableMinimumWidth, view.getSuggestedMinimumWidth());

        // drawable minimum width is smaller than mMinWidth
        view.setMinimumWidth(drawableMinimumWidth + 10);
        assertEquals(drawableMinimumWidth + 10, view.getSuggestedMinimumWidth());
    }

    @Test
    public void testSetMinimumHeight() {
        MockView view = new MockView(mActivity);
        assertEquals(0, view.getSuggestedMinimumHeight());

        view.setMinimumHeight(100);
        assertEquals(100, view.getSuggestedMinimumHeight());

        view.setMinimumHeight(-100);
        assertEquals(-100, view.getSuggestedMinimumHeight());
    }

    @Test
    public void testGetSuggestedMinimumHeight() {
        MockView view = new MockView(mActivity);
        Drawable d = mResources.getDrawable(R.drawable.scenery);
        int drawableMinimumHeight = d.getMinimumHeight();

        // drawable is null
        view.setMinimumHeight(100);
        assertNull(view.getBackground());
        assertEquals(100, view.getSuggestedMinimumHeight());

        // drawable minimum height is larger than mMinHeight
        view.setBackgroundDrawable(d);
        view.setMinimumHeight(drawableMinimumHeight - 10);
        assertEquals(drawableMinimumHeight, view.getSuggestedMinimumHeight());

        // drawable minimum height is smaller than mMinHeight
        view.setMinimumHeight(drawableMinimumHeight + 10);
        assertEquals(drawableMinimumHeight + 10, view.getSuggestedMinimumHeight());
    }

    @Test
    public void testAccessWillNotCacheDrawing() {
        View view = new View(mActivity);

        assertFalse(view.willNotCacheDrawing());

        view.setWillNotCacheDrawing(true);
        assertTrue(view.willNotCacheDrawing());
    }

    @Test
    public void testAccessDrawingCacheEnabled() {
        View view = new View(mActivity);

        assertFalse(view.isDrawingCacheEnabled());

        view.setDrawingCacheEnabled(true);
        assertTrue(view.isDrawingCacheEnabled());
    }

    @Test
    public void testGetDrawingCache() {
        MockView view = new MockView(mActivity);

        // should not call buildDrawingCache when getDrawingCache
        assertNull(view.getDrawingCache());

        // should call buildDrawingCache when getDrawingCache
        view = (MockView) mActivity.findViewById(R.id.mock_view);
        view.setDrawingCacheEnabled(true);
        Bitmap bitmap1 = view.getDrawingCache();
        assertNotNull(bitmap1);
        assertEquals(view.getWidth(), bitmap1.getWidth());
        assertEquals(view.getHeight(), bitmap1.getHeight());

        view.setWillNotCacheDrawing(true);
        assertNull(view.getDrawingCache());

        view.setWillNotCacheDrawing(false);
        // build a new drawingcache
        Bitmap bitmap2 = view.getDrawingCache();
        assertNotSame(bitmap1, bitmap2);
    }

    @Test
    public void testBuildAndDestroyDrawingCache() {
        MockView view = (MockView) mActivity.findViewById(R.id.mock_view);

        assertNull(view.getDrawingCache());

        view.buildDrawingCache();
        Bitmap bitmap = view.getDrawingCache();
        assertNotNull(bitmap);
        assertEquals(view.getWidth(), bitmap.getWidth());
        assertEquals(view.getHeight(), bitmap.getHeight());

        view.destroyDrawingCache();
        assertNull(view.getDrawingCache());
    }

    @Test
    public void testAccessWillNotDraw() {
        View view = new View(mActivity);

        assertFalse(view.willNotDraw());

        view.setWillNotDraw(true);
        assertTrue(view.willNotDraw());
    }

    @Test
    public void testAccessDrawingCacheQuality() {
        View view = new View(mActivity);

        assertEquals(0, view.getDrawingCacheQuality());

        view.setDrawingCacheQuality(1);
        assertEquals(0, view.getDrawingCacheQuality());

        view.setDrawingCacheQuality(0x00100000);
        assertEquals(0x00100000, view.getDrawingCacheQuality());

        view.setDrawingCacheQuality(0x00080000);
        assertEquals(0x00080000, view.getDrawingCacheQuality());

        view.setDrawingCacheQuality(0xffffffff);
        // 0x00180000 is View.DRAWING_CACHE_QUALITY_MASK
        assertEquals(0x00180000, view.getDrawingCacheQuality());
    }

    @Test
    public void testDispatchSetSelected() {
        MockView mockView1 = new MockView(mActivity);
        MockView mockView2 = new MockView(mActivity);
        mockView1.setParent(mMockParent);
        mockView2.setParent(mMockParent);

        mMockParent.dispatchSetSelected(true);
        assertTrue(mockView1.isSelected());
        assertTrue(mockView2.isSelected());

        mMockParent.dispatchSetSelected(false);
        assertFalse(mockView1.isSelected());
        assertFalse(mockView2.isSelected());
    }

    @Test
    public void testAccessSelected() {
        View view = new View(mActivity);

        assertFalse(view.isSelected());

        view.setSelected(true);
        assertTrue(view.isSelected());
    }

    @Test
    public void testDispatchSetPressed() {
        MockView mockView1 = new MockView(mActivity);
        MockView mockView2 = new MockView(mActivity);
        mockView1.setParent(mMockParent);
        mockView2.setParent(mMockParent);

        mMockParent.dispatchSetPressed(true);
        assertTrue(mockView1.isPressed());
        assertTrue(mockView2.isPressed());

        mMockParent.dispatchSetPressed(false);
        assertFalse(mockView1.isPressed());
        assertFalse(mockView2.isPressed());
    }

    @Test
    public void testAccessPressed() {
        View view = new View(mActivity);

        assertFalse(view.isPressed());

        view.setPressed(true);
        assertTrue(view.isPressed());
    }

    @Test
    public void testAccessSoundEffectsEnabled() {
        View view = new View(mActivity);

        assertTrue(view.isSoundEffectsEnabled());

        view.setSoundEffectsEnabled(false);
        assertFalse(view.isSoundEffectsEnabled());
    }

    @Test
    public void testAccessKeepScreenOn() {
        View view = new View(mActivity);

        assertFalse(view.getKeepScreenOn());

        view.setKeepScreenOn(true);
        assertTrue(view.getKeepScreenOn());
    }

    @Test
    public void testAccessDuplicateParentStateEnabled() {
        View view = new View(mActivity);

        assertFalse(view.isDuplicateParentStateEnabled());

        view.setDuplicateParentStateEnabled(true);
        assertTrue(view.isDuplicateParentStateEnabled());
    }

    @Test
    public void testAccessEnabled() {
        View view = new View(mActivity);

        assertTrue(view.isEnabled());

        view.setEnabled(false);
        assertFalse(view.isEnabled());
    }

    @Test
    public void testAccessSaveEnabled() {
        View view = new View(mActivity);

        assertTrue(view.isSaveEnabled());

        view.setSaveEnabled(false);
        assertFalse(view.isSaveEnabled());
    }

    @Test(expected=NullPointerException.class)
    public void testShowContextMenuNullParent() {
        MockView view = new MockView(mActivity);

        assertNull(view.getParent());
        view.showContextMenu();
    }

    @Test
    public void testShowContextMenu() {
        MockView view = new MockView(mActivity);
        view.setParent(mMockParent);
        assertFalse(mMockParent.hasShowContextMenuForChild());

        assertFalse(view.showContextMenu());
        assertTrue(mMockParent.hasShowContextMenuForChild());
    }

    @Test(expected=NullPointerException.class)
    public void testShowContextMenuXYNullParent() {
        MockView view = new MockView(mActivity);

        assertNull(view.getParent());
        view.showContextMenu(0, 0);
    }

    @Test
    public void testShowContextMenuXY() {
        MockViewParent parent = new MockViewParent(mActivity);
        MockView view = new MockView(mActivity);

        view.setParent(parent);
        assertFalse(parent.hasShowContextMenuForChildXY());

        assertFalse(view.showContextMenu(0, 0));
        assertTrue(parent.hasShowContextMenuForChildXY());
    }

    @Test
    public void testFitSystemWindows() {
        final XmlResourceParser parser = mResources.getLayout(R.layout.view_layout);
        final AttributeSet attrs = Xml.asAttributeSet(parser);
        Rect insets = new Rect(10, 20, 30, 50);

        MockView view = new MockView(mActivity);
        assertFalse(view.fitSystemWindows(insets));
        assertFalse(view.fitSystemWindows(null));

        view = new MockView(mActivity, attrs, android.R.attr.fitsSystemWindows);
        assertFalse(view.fitSystemWindows(insets));
        assertFalse(view.fitSystemWindows(null));
    }

    @Test
    public void testPerformClick() {
        View view = new View(mActivity);
        View.OnClickListener listener = mock(View.OnClickListener.class);

        assertFalse(view.performClick());

        verifyZeroInteractions(listener);
        view.setOnClickListener(listener);

        assertTrue(view.performClick());
        verify(listener,times(1)).onClick(view);

        view.setOnClickListener(null);
        assertFalse(view.performClick());
    }

    @Test
    public void testSetOnClickListener() {
        View view = new View(mActivity);
        assertFalse(view.performClick());
        assertFalse(view.isClickable());

        view.setOnClickListener(null);
        assertFalse(view.performClick());
        assertTrue(view.isClickable());

        view.setOnClickListener(mock(View.OnClickListener.class));
        assertTrue(view.performClick());
        assertTrue(view.isClickable());
    }

    @Test(expected=NullPointerException.class)
    public void testPerformLongClickNullParent() {
        MockView view = new MockView(mActivity);
        view.performLongClick();
    }

    @Test
    public void testPerformLongClick() {
        MockView view = new MockView(mActivity);
        View.OnLongClickListener listener = mock(View.OnLongClickListener.class);
        doReturn(true).when(listener).onLongClick(any());

        view.setParent(mMockParent);
        assertFalse(mMockParent.hasShowContextMenuForChild());
        assertFalse(view.performLongClick());
        assertTrue(mMockParent.hasShowContextMenuForChild());

        view.setOnLongClickListener(listener);
        mMockParent.reset();
        assertFalse(mMockParent.hasShowContextMenuForChild());
        verifyZeroInteractions(listener);
        assertTrue(view.performLongClick());
        assertFalse(mMockParent.hasShowContextMenuForChild());
        verify(listener, times(1)).onLongClick(view);
    }

    @Test(expected=NullPointerException.class)
    public void testPerformLongClickXYNullParent() {
        MockView view = new MockView(mActivity);
        view.performLongClick(0, 0);
    }

    @Test
    public void testPerformLongClickXY() {
        MockViewParent parent = new MockViewParent(mActivity);
        MockView view = new MockView(mActivity);

        parent.addView(view);
        assertFalse(parent.hasShowContextMenuForChildXY());

        // Verify default context menu behavior.
        assertFalse(view.performLongClick(0, 0));
        assertTrue(parent.hasShowContextMenuForChildXY());
    }

    @Test
    public void testPerformLongClickXY_WithListener() {
        OnLongClickListener listener = mock(OnLongClickListener.class);
        when(listener.onLongClick(any(View.class))).thenReturn(true);

        MockViewParent parent = new MockViewParent(mActivity);
        MockView view = new MockView(mActivity);

        view.setOnLongClickListener(listener);
        verify(listener, never()).onLongClick(any(View.class));

        parent.addView(view);
        assertFalse(parent.hasShowContextMenuForChildXY());

        // Verify listener is preferred over default context menu.
        assertTrue(view.performLongClick(0, 0));
        assertFalse(parent.hasShowContextMenuForChildXY());
        verify(listener).onLongClick(view);
    }

    @Test
    public void testSetOnLongClickListener() {
        MockView view = new MockView(mActivity);
        view.setParent(mMockParent);
        assertFalse(view.performLongClick());
        assertFalse(view.isLongClickable());

        view.setOnLongClickListener(null);
        assertFalse(view.performLongClick());
        assertTrue(view.isLongClickable());

        View.OnLongClickListener listener = mock(View.OnLongClickListener.class);
        doReturn(true).when(listener).onLongClick(any());
        view.setOnLongClickListener(listener);
        assertTrue(view.performLongClick());
        assertTrue(view.isLongClickable());
    }

    @Test
    public void testPerformContextClick() {
        MockView view = new MockView(mActivity);
        view.setParent(mMockParent);
        View.OnContextClickListener listener = mock(View.OnContextClickListener.class);
        doReturn(true).when(listener).onContextClick(any());

        view.setOnContextClickListener(listener);
        verifyZeroInteractions(listener);

        assertTrue(view.performContextClick());
        verify(listener, times(1)).onContextClick(view);
    }

    @Test
    public void testSetOnContextClickListener() {
        MockView view = new MockView(mActivity);
        view.setParent(mMockParent);

        assertFalse(view.performContextClick());
        assertFalse(view.isContextClickable());

        View.OnContextClickListener listener = mock(View.OnContextClickListener.class);
        doReturn(true).when(listener).onContextClick(any());
        view.setOnContextClickListener(listener);
        assertTrue(view.performContextClick());
        assertTrue(view.isContextClickable());
    }

    @Test
    public void testAccessOnFocusChangeListener() {
        View view = new View(mActivity);
        View.OnFocusChangeListener listener = mock(View.OnFocusChangeListener.class);

        assertNull(view.getOnFocusChangeListener());

        view.setOnFocusChangeListener(listener);
        assertSame(listener, view.getOnFocusChangeListener());
    }

    @Test
    public void testAccessNextFocusUpId() {
        View view = new View(mActivity);

        assertEquals(View.NO_ID, view.getNextFocusUpId());

        view.setNextFocusUpId(1);
        assertEquals(1, view.getNextFocusUpId());

        view.setNextFocusUpId(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, view.getNextFocusUpId());

        view.setNextFocusUpId(Integer.MIN_VALUE);
        assertEquals(Integer.MIN_VALUE, view.getNextFocusUpId());
    }

    @Test
    public void testAccessNextFocusDownId() {
        View view = new View(mActivity);

        assertEquals(View.NO_ID, view.getNextFocusDownId());

        view.setNextFocusDownId(1);
        assertEquals(1, view.getNextFocusDownId());

        view.setNextFocusDownId(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, view.getNextFocusDownId());

        view.setNextFocusDownId(Integer.MIN_VALUE);
        assertEquals(Integer.MIN_VALUE, view.getNextFocusDownId());
    }

    @Test
    public void testAccessNextFocusLeftId() {
        View view = new View(mActivity);

        assertEquals(View.NO_ID, view.getNextFocusLeftId());

        view.setNextFocusLeftId(1);
        assertEquals(1, view.getNextFocusLeftId());

        view.setNextFocusLeftId(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, view.getNextFocusLeftId());

        view.setNextFocusLeftId(Integer.MIN_VALUE);
        assertEquals(Integer.MIN_VALUE, view.getNextFocusLeftId());
    }

    @Test
    public void testAccessNextFocusRightId() {
        View view = new View(mActivity);

        assertEquals(View.NO_ID, view.getNextFocusRightId());

        view.setNextFocusRightId(1);
        assertEquals(1, view.getNextFocusRightId());

        view.setNextFocusRightId(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, view.getNextFocusRightId());

        view.setNextFocusRightId(Integer.MIN_VALUE);
        assertEquals(Integer.MIN_VALUE, view.getNextFocusRightId());
    }

    @Test
    public void testAccessMeasuredDimension() {
        MockView view = new MockView(mActivity);
        assertEquals(0, view.getMeasuredWidth());
        assertEquals(0, view.getMeasuredHeight());

        view.setMeasuredDimensionWrapper(20, 30);
        assertEquals(20, view.getMeasuredWidth());
        assertEquals(30, view.getMeasuredHeight());
    }

    @Test
    public void testMeasure() throws Throwable {
        final MockView view = (MockView) mActivity.findViewById(R.id.mock_view);
        assertTrue(view.hasCalledOnMeasure());
        assertEquals(100, view.getMeasuredWidth());
        assertEquals(200, view.getMeasuredHeight());

        view.reset();
        mActivityRule.runOnUiThread(view::requestLayout);
        mInstrumentation.waitForIdleSync();
        assertTrue(view.hasCalledOnMeasure());
        assertEquals(100, view.getMeasuredWidth());
        assertEquals(200, view.getMeasuredHeight());

        view.reset();
        final LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(200, 100);
        mActivityRule.runOnUiThread(() -> view.setLayoutParams(layoutParams));
        mInstrumentation.waitForIdleSync();
        assertTrue(view.hasCalledOnMeasure());
        assertEquals(200, view.getMeasuredWidth());
        assertEquals(100, view.getMeasuredHeight());
    }

    @Test(expected=NullPointerException.class)
    public void setSetLayoutParamsNull() {
        View view = new View(mActivity);
        assertNull(view.getLayoutParams());

        view.setLayoutParams(null);
    }

    @Test
    public void testAccessLayoutParams() {
        View view = new View(mActivity);
        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(10, 20);

        assertFalse(view.isLayoutRequested());
        view.setLayoutParams(params);
        assertSame(params, view.getLayoutParams());
        assertTrue(view.isLayoutRequested());
    }

    @Test
    public void testIsShown() {
        MockView view = new MockView(mActivity);

        view.setVisibility(View.INVISIBLE);
        assertFalse(view.isShown());

        view.setVisibility(View.VISIBLE);
        assertNull(view.getParent());
        assertFalse(view.isShown());

        view.setParent(mMockParent);
        // mMockParent is not a instance of ViewRoot
        assertFalse(view.isShown());
    }

    @Test
    public void testGetDrawingTime() {
        View view = new View(mActivity);
        // mAttachInfo is null
        assertEquals(0, view.getDrawingTime());

        // mAttachInfo is not null
        view = mActivity.findViewById(R.id.fit_windows);
        assertEquals(SystemClock.uptimeMillis(), view.getDrawingTime(), 1000);
    }

    @Test
    public void testScheduleDrawable() {
        View view = new View(mActivity);
        Drawable drawable = new StateListDrawable();
        // Does nothing.
        Runnable what = () -> {};
        // mAttachInfo is null
        view.scheduleDrawable(drawable, what, 1000);

        view.setBackgroundDrawable(drawable);
        view.scheduleDrawable(drawable, what, 1000);

        view.scheduleDrawable(null, null, -1000);

        // mAttachInfo is not null
        view = mActivity.findViewById(R.id.fit_windows);
        view.scheduleDrawable(drawable, what, 1000);

        view.scheduleDrawable(view.getBackground(), what, 1000);
        view.unscheduleDrawable(view.getBackground(), what);

        view.scheduleDrawable(null, null, -1000);
    }

    @Test
    public void testUnscheduleDrawable() {
        View view = new View(mActivity);
        Drawable drawable = new StateListDrawable();
        Runnable what = () -> {
            // do nothing
        };

        // mAttachInfo is null
        view.unscheduleDrawable(drawable, what);

        view.setBackgroundDrawable(drawable);
        view.unscheduleDrawable(drawable);

        view.unscheduleDrawable(null, null);
        view.unscheduleDrawable(null);

        // mAttachInfo is not null
        view = mActivity.findViewById(R.id.fit_windows);
        view.unscheduleDrawable(drawable);

        view.scheduleDrawable(view.getBackground(), what, 1000);
        view.unscheduleDrawable(view.getBackground(), what);

        view.unscheduleDrawable(null);
        view.unscheduleDrawable(null, null);
    }

    @Test
    public void testGetWindowVisibility() {
        View view = new View(mActivity);
        // mAttachInfo is null
        assertEquals(View.GONE, view.getWindowVisibility());

        // mAttachInfo is not null
        view = mActivity.findViewById(R.id.fit_windows);
        assertEquals(View.VISIBLE, view.getWindowVisibility());
    }

    @Test
    public void testGetWindowToken() {
        View view = new View(mActivity);
        // mAttachInfo is null
        assertNull(view.getWindowToken());

        // mAttachInfo is not null
        view = mActivity.findViewById(R.id.fit_windows);
        assertNotNull(view.getWindowToken());
    }

    @Test
    public void testHasWindowFocus() {
        View view = new View(mActivity);
        // mAttachInfo is null
        assertFalse(view.hasWindowFocus());

        // mAttachInfo is not null
        final View view2 = mActivity.findViewById(R.id.fit_windows);
        // Wait until the window has been focused.
        PollingCheck.waitFor(TIMEOUT_DELTA, view2::hasWindowFocus);
    }

    @Test
    public void testGetHandler() {
        MockView view = new MockView(mActivity);
        // mAttachInfo is null
        assertNull(view.getHandler());
    }

    @Test
    public void testRemoveCallbacks() throws InterruptedException {
        final long delay = 500L;
        View view = mActivity.findViewById(R.id.mock_view);
        Runnable runner = mock(Runnable.class);
        assertTrue(view.postDelayed(runner, delay));
        assertTrue(view.removeCallbacks(runner));
        assertTrue(view.removeCallbacks(null));
        assertTrue(view.removeCallbacks(mock(Runnable.class)));
        Thread.sleep(delay * 2);
        verifyZeroInteractions(runner);
        // check that the runner actually works
        runner = mock(Runnable.class);
        assertTrue(view.postDelayed(runner, delay));
        Thread.sleep(delay * 2);
        verify(runner, times(1)).run();
    }

    @Test
    public void testCancelLongPress() {
        View view = new View(mActivity);
        // mAttachInfo is null
        view.cancelLongPress();

        // mAttachInfo is not null
        view = mActivity.findViewById(R.id.fit_windows);
        view.cancelLongPress();
    }

    @Test
    public void testGetViewTreeObserver() {
        View view = new View(mActivity);
        // mAttachInfo is null
        assertNotNull(view.getViewTreeObserver());

        // mAttachInfo is not null
        view = mActivity.findViewById(R.id.fit_windows);
        assertNotNull(view.getViewTreeObserver());
    }

    @Test
    public void testGetWindowAttachCount() {
        MockView view = new MockView(mActivity);
        // mAttachInfo is null
        assertEquals(0, view.getWindowAttachCount());
    }

    @UiThreadTest
    @Test
    public void testOnAttachedToAndDetachedFromWindow() {
        MockView mockView = new MockView(mActivity);
        ViewGroup viewGroup = (ViewGroup) mActivity.findViewById(R.id.viewlayout_root);

        viewGroup.addView(mockView);
        assertTrue(mockView.hasCalledOnAttachedToWindow());

        viewGroup.removeView(mockView);
        assertTrue(mockView.hasCalledOnDetachedFromWindow());

        mockView.reset();
        mActivity.setContentView(mockView);
        assertTrue(mockView.hasCalledOnAttachedToWindow());

        mActivity.setContentView(R.layout.view_layout);
        assertTrue(mockView.hasCalledOnDetachedFromWindow());
    }

    @Test
    public void testGetLocationInWindow() {
        final int[] location = new int[]{-1, -1};

        final View layout = mActivity.findViewById(R.id.viewlayout_root);
        int[] layoutLocation = new int[]{-1, -1};
        layout.getLocationInWindow(layoutLocation);

        final View mockView = mActivity.findViewById(R.id.mock_view);
        mockView.getLocationInWindow(location);
        assertEquals(layoutLocation[0], location[0]);
        assertEquals(layoutLocation[1], location[1]);

        final View scrollView = mActivity.findViewById(R.id.scroll_view);
        scrollView.getLocationInWindow(location);
        assertEquals(layoutLocation[0], location[0]);
        assertEquals(layoutLocation[1] + mockView.getHeight(), location[1]);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testGetLocationInWindowNullArray() {
        final View layout = mActivity.findViewById(R.id.viewlayout_root);
        final View mockView = mActivity.findViewById(R.id.mock_view);

        mockView.getLocationInWindow(null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testGetLocationInWindowSmallArray() {
        final View layout = mActivity.findViewById(R.id.viewlayout_root);
        final View mockView = mActivity.findViewById(R.id.mock_view);

        mockView.getLocationInWindow(new int[] { 0 });
    }

    @Test
    public void testGetLocationOnScreen() {
        final int[] location = new int[]{-1, -1};

        // mAttachInfo is not null
        final View layout = mActivity.findViewById(R.id.viewlayout_root);
        final int[] layoutLocation = new int[]{-1, -1};
        layout.getLocationOnScreen(layoutLocation);

        final View mockView = mActivity.findViewById(R.id.mock_view);
        mockView.getLocationOnScreen(location);
        assertEquals(layoutLocation[0], location[0]);
        assertEquals(layoutLocation[1], location[1]);

        final View scrollView = mActivity.findViewById(R.id.scroll_view);
        scrollView.getLocationOnScreen(location);
        assertEquals(layoutLocation[0], location[0]);
        assertEquals(layoutLocation[1] + mockView.getHeight(), location[1]);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testGetLocationOnScreenNullArray() {
        final View scrollView = mActivity.findViewById(R.id.scroll_view);

        scrollView.getLocationOnScreen(null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testGetLocationOnScreenSmallArray() {
        final View scrollView = mActivity.findViewById(R.id.scroll_view);

        scrollView.getLocationOnScreen(new int[] { 0 });
    }

    @Test
    public void testAddTouchables() {
        View view = new View(mActivity);
        ArrayList<View> result = new ArrayList<>();
        assertEquals(0, result.size());

        view.addTouchables(result);
        assertEquals(0, result.size());

        view.setClickable(true);
        view.addTouchables(result);
        assertEquals(1, result.size());
        assertSame(view, result.get(0));

        try {
            view.addTouchables(null);
            fail("should throw NullPointerException");
        } catch (NullPointerException e) {
        }

        result.clear();
        view.setEnabled(false);
        assertTrue(view.isClickable());
        view.addTouchables(result);
        assertEquals(0, result.size());
    }

    @Test
    public void testGetTouchables() {
        View view = new View(mActivity);
        ArrayList<View> result;

        result = view.getTouchables();
        assertEquals(0, result.size());

        view.setClickable(true);
        result = view.getTouchables();
        assertEquals(1, result.size());
        assertSame(view, result.get(0));

        result.clear();
        view.setEnabled(false);
        assertTrue(view.isClickable());
        result = view.getTouchables();
        assertEquals(0, result.size());
    }

    @Test
    public void testInflate() {
        View view = View.inflate(mActivity, R.layout.view_layout, null);
        assertNotNull(view);
        assertTrue(view instanceof LinearLayout);

        MockView mockView = (MockView) view.findViewById(R.id.mock_view);
        assertNotNull(mockView);
        assertTrue(mockView.hasCalledOnFinishInflate());
    }

    @Test
    public void testIsInTouchMode() {
        View view = new View(mActivity);
        // mAttachInfo is null
        assertFalse(view.isInTouchMode());

        // mAttachInfo is not null
        view = mActivity.findViewById(R.id.fit_windows);
        assertFalse(view.isInTouchMode());
    }

    @Test
    public void testIsInEditMode() {
        View view = new View(mActivity);
        assertFalse(view.isInEditMode());
    }

    @Test
    public void testPostInvalidate1() {
        View view = new View(mActivity);
        // mAttachInfo is null
        view.postInvalidate();

        // mAttachInfo is not null
        view = mActivity.findViewById(R.id.fit_windows);
        view.postInvalidate();
    }

    @Test
    public void testPostInvalidate2() {
        View view = new View(mActivity);
        // mAttachInfo is null
        view.postInvalidate(0, 1, 2, 3);

        // mAttachInfo is not null
        view = mActivity.findViewById(R.id.fit_windows);
        view.postInvalidate(10, 20, 30, 40);
        view.postInvalidate(0, -20, -30, -40);
    }

    @Test
    public void testPostInvalidateDelayed() {
        View view = new View(mActivity);
        // mAttachInfo is null
        view.postInvalidateDelayed(1000);
        view.postInvalidateDelayed(500, 0, 0, 100, 200);

        // mAttachInfo is not null
        view = mActivity.findViewById(R.id.fit_windows);
        view.postInvalidateDelayed(1000);
        view.postInvalidateDelayed(500, 0, 0, 100, 200);
        view.postInvalidateDelayed(-1);
    }

    @Test
    public void testPost() {
        View view = new View(mActivity);
        Runnable action = mock(Runnable.class);

        // mAttachInfo is null
        assertTrue(view.post(action));
        assertTrue(view.post(null));

        // mAttachInfo is not null
        view = mActivity.findViewById(R.id.fit_windows);
        assertTrue(view.post(action));
        assertTrue(view.post(null));
    }

    @Test
    public void testPostDelayed() {
        View view = new View(mActivity);
        Runnable action = mock(Runnable.class);

        // mAttachInfo is null
        assertTrue(view.postDelayed(action, 1000));
        assertTrue(view.postDelayed(null, -1));

        // mAttachInfo is not null
        view = mActivity.findViewById(R.id.fit_windows);
        assertTrue(view.postDelayed(action, 1000));
        assertTrue(view.postDelayed(null, 0));
    }

    @UiThreadTest
    @Test
    public void testPlaySoundEffect() {
        MockView view = (MockView) mActivity.findViewById(R.id.mock_view);
        // sound effect enabled
        view.playSoundEffect(SoundEffectConstants.CLICK);

        // sound effect disabled
        view.setSoundEffectsEnabled(false);
        view.playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN);

        // no way to assert the soundConstant be really played.
    }

    @Test
    public void testOnKeyShortcut() throws Throwable {
        final MockView view = (MockView) mActivity.findViewById(R.id.mock_view);
        mActivityRule.runOnUiThread(() -> {
            view.setFocusable(true);
            view.requestFocus();
        });
        mInstrumentation.waitForIdleSync();
        assertTrue(view.isFocused());

        KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU);
        mInstrumentation.sendKeySync(event);
        event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_0);
        mInstrumentation.sendKeySync(event);
        assertTrue(view.hasCalledOnKeyShortcut());
    }

    @Test
    public void testOnKeyMultiple() throws Throwable {
        final MockView view = (MockView) mActivity.findViewById(R.id.mock_view);
        mActivityRule.runOnUiThread(() -> view.setFocusable(true));

        assertFalse(view.hasCalledOnKeyMultiple());
        view.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_MULTIPLE, KeyEvent.KEYCODE_ENTER));
        assertTrue(view.hasCalledOnKeyMultiple());
    }

    @UiThreadTest
    @Test
    public void testDispatchKeyShortcutEvent() {
        MockView view = (MockView) mActivity.findViewById(R.id.mock_view);
        view.setFocusable(true);

        KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_0);
        view.dispatchKeyShortcutEvent(event);
        assertTrue(view.hasCalledOnKeyShortcut());
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testDispatchKeyShortcutEventNull() {
        MockView view = (MockView) mActivity.findViewById(R.id.mock_view);
        view.setFocusable(true);

        view.dispatchKeyShortcutEvent(null);
    }

    @Test
    public void testOnTrackballEvent() throws Throwable {
        final MockView view = (MockView) mActivity.findViewById(R.id.mock_view);
        mActivityRule.runOnUiThread(() -> {
            view.setEnabled(true);
            view.setFocusable(true);
            view.requestFocus();
        });
        mInstrumentation.waitForIdleSync();

        long downTime = SystemClock.uptimeMillis();
        long eventTime = downTime;
        MotionEvent event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_MOVE,
                1, 2, 0);
        mInstrumentation.sendTrackballEventSync(event);
        mInstrumentation.waitForIdleSync();
        assertTrue(view.hasCalledOnTrackballEvent());
    }

    @UiThreadTest
    @Test
    public void testDispatchTrackballMoveEvent() {
        ViewGroup viewGroup = (ViewGroup) mActivity.findViewById(R.id.viewlayout_root);
        MockView mockView1 = new MockView(mActivity);
        MockView mockView2 = new MockView(mActivity);
        viewGroup.addView(mockView1);
        viewGroup.addView(mockView2);
        mockView1.setFocusable(true);
        mockView2.setFocusable(true);
        mockView2.requestFocus();

        long downTime = SystemClock.uptimeMillis();
        long eventTime = downTime;
        MotionEvent event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_MOVE,
                1, 2, 0);
        mockView1.dispatchTrackballEvent(event);
        // issue 1695243
        // It passes a trackball motion event down to itself even if it is not the focused view.
        assertTrue(mockView1.hasCalledOnTrackballEvent());
        assertFalse(mockView2.hasCalledOnTrackballEvent());

        mockView1.reset();
        mockView2.reset();
        downTime = SystemClock.uptimeMillis();
        eventTime = downTime;
        event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_MOVE, 1, 2, 0);
        mockView2.dispatchTrackballEvent(event);
        assertFalse(mockView1.hasCalledOnTrackballEvent());
        assertTrue(mockView2.hasCalledOnTrackballEvent());
    }

    @Test
    public void testDispatchUnhandledMove() throws Throwable {
        final MockView view = (MockView) mActivity.findViewById(R.id.mock_view);
        mActivityRule.runOnUiThread(() -> {
            view.setFocusable(true);
            view.requestFocus();
        });
        mInstrumentation.waitForIdleSync();

        KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT);
        mInstrumentation.sendKeySync(event);

        assertTrue(view.hasCalledDispatchUnhandledMove());
    }

    @Test
    public void testWindowVisibilityChanged() throws Throwable {
        final MockView mockView = new MockView(mActivity);
        final ViewGroup viewGroup = (ViewGroup) mActivity.findViewById(R.id.viewlayout_root);

        mActivityRule.runOnUiThread(() -> viewGroup.addView(mockView));
        mInstrumentation.waitForIdleSync();
        assertTrue(mockView.hasCalledOnWindowVisibilityChanged());

        mockView.reset();
        mActivityRule.runOnUiThread(() -> mActivity.setVisible(false));
        mInstrumentation.waitForIdleSync();
        assertTrue(mockView.hasCalledDispatchWindowVisibilityChanged());
        assertTrue(mockView.hasCalledOnWindowVisibilityChanged());

        mockView.reset();
        mActivityRule.runOnUiThread(() -> mActivity.setVisible(true));
        mInstrumentation.waitForIdleSync();
        assertTrue(mockView.hasCalledDispatchWindowVisibilityChanged());
        assertTrue(mockView.hasCalledOnWindowVisibilityChanged());

        mockView.reset();
        mActivityRule.runOnUiThread(() -> viewGroup.removeView(mockView));
        mInstrumentation.waitForIdleSync();
        assertTrue(mockView.hasCalledOnWindowVisibilityChanged());
    }

    @Test
    public void testGetLocalVisibleRect() throws Throwable {
        final View view = mActivity.findViewById(R.id.mock_view);
        Rect rect = new Rect();

        assertTrue(view.getLocalVisibleRect(rect));
        assertEquals(0, rect.left);
        assertEquals(0, rect.top);
        assertEquals(100, rect.right);
        assertEquals(200, rect.bottom);

        final LinearLayout.LayoutParams layoutParams1 = new LinearLayout.LayoutParams(0, 300);
        mActivityRule.runOnUiThread(() -> view.setLayoutParams(layoutParams1));
        mInstrumentation.waitForIdleSync();
        assertFalse(view.getLocalVisibleRect(rect));

        final LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(200, -10);
        mActivityRule.runOnUiThread(() -> view.setLayoutParams(layoutParams2));
        mInstrumentation.waitForIdleSync();
        assertFalse(view.getLocalVisibleRect(rect));

        Display display = mActivity.getWindowManager().getDefaultDisplay();
        int halfWidth = display.getWidth() / 2;
        int halfHeight = display.getHeight() /2;

        final LinearLayout.LayoutParams layoutParams3 =
                new LinearLayout.LayoutParams(halfWidth, halfHeight);
        mActivityRule.runOnUiThread(() -> {
            view.setLayoutParams(layoutParams3);
            view.scrollTo(20, -30);
        });
        mInstrumentation.waitForIdleSync();
        assertTrue(view.getLocalVisibleRect(rect));
        assertEquals(20, rect.left);
        assertEquals(-30, rect.top);
        assertEquals(halfWidth + 20, rect.right);
        assertEquals(halfHeight - 30, rect.bottom);

        try {
            view.getLocalVisibleRect(null);
            fail("should throw NullPointerException");
        } catch (NullPointerException e) {
        }
    }

    @Test
    public void testMergeDrawableStates() {
        MockView view = new MockView(mActivity);

        int[] states = view.mergeDrawableStatesWrapper(new int[] { 0, 1, 2, 0, 0 },
                new int[] { 3 });
        assertNotNull(states);
        assertEquals(5, states.length);
        assertEquals(0, states[0]);
        assertEquals(1, states[1]);
        assertEquals(2, states[2]);
        assertEquals(3, states[3]);
        assertEquals(0, states[4]);

        try {
            view.mergeDrawableStatesWrapper(new int[] { 1, 2 }, new int[] { 3 });
            fail("should throw IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
        }

        try {
            view.mergeDrawableStatesWrapper(null, new int[] { 0 });
            fail("should throw NullPointerException");
        } catch (NullPointerException e) {
        }

        try {
            view.mergeDrawableStatesWrapper(new int [] { 0 }, null);
            fail("should throw NullPointerException");
        } catch (NullPointerException e) {
        }
    }

    @Test
    public void testSaveAndRestoreHierarchyState() {
        int viewId = R.id.mock_view;
        MockView view = (MockView) mActivity.findViewById(viewId);
        SparseArray<Parcelable> container = new SparseArray<>();
        view.saveHierarchyState(container);
        assertTrue(view.hasCalledDispatchSaveInstanceState());
        assertTrue(view.hasCalledOnSaveInstanceState());
        assertEquals(viewId, container.keyAt(0));

        view.reset();
        container.put(R.id.mock_view, BaseSavedState.EMPTY_STATE);
        view.restoreHierarchyState(container);
        assertTrue(view.hasCalledDispatchRestoreInstanceState());
        assertTrue(view.hasCalledOnRestoreInstanceState());
        container.clear();
        view.saveHierarchyState(container);
        assertTrue(view.hasCalledDispatchSaveInstanceState());
        assertTrue(view.hasCalledOnSaveInstanceState());
        assertEquals(viewId, container.keyAt(0));

        container.clear();
        container.put(viewId, new android.graphics.Rect());
        try {
            view.restoreHierarchyState(container);
            fail("Parcelable state must be an AbsSaveState, should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            view.restoreHierarchyState(null);
            fail("Cannot pass null to restoreHierarchyState(), should throw NullPointerException");
        } catch (NullPointerException e) {
            // expected
        }

        try {
            view.saveHierarchyState(null);
            fail("Cannot pass null to saveHierarchyState(), should throw NullPointerException");
        } catch (NullPointerException e) {
            // expected
        }
    }

    @Test
    public void testOnKeyDownOrUp() throws Throwable {
        final MockView view = (MockView) mActivity.findViewById(R.id.mock_view);
        mActivityRule.runOnUiThread(() -> {
            view.setFocusable(true);
            view.requestFocus();
        });
        mInstrumentation.waitForIdleSync();
        assertTrue(view.isFocused());

        KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_0);
        mInstrumentation.sendKeySync(event);
        assertTrue(view.hasCalledOnKeyDown());

        event = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_0);
        mInstrumentation.sendKeySync(event);
        assertTrue(view.hasCalledOnKeyUp());

        view.reset();
        assertTrue(view.isEnabled());
        assertFalse(view.isClickable());
        assertFalse(view.isPressed());
        event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER);
        mInstrumentation.sendKeySync(event);
        assertFalse(view.isPressed());
        assertTrue(view.hasCalledOnKeyDown());

        mActivityRule.runOnUiThread(() -> {
            view.setEnabled(true);
            view.setClickable(true);
        });
        view.reset();
        View.OnClickListener listener = mock(View.OnClickListener.class);
        view.setOnClickListener(listener);

        assertFalse(view.isPressed());
        event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER);
        mInstrumentation.sendKeySync(event);
        assertTrue(view.isPressed());
        assertTrue(view.hasCalledOnKeyDown());
        event = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER);
        mInstrumentation.sendKeySync(event);
        assertFalse(view.isPressed());
        assertTrue(view.hasCalledOnKeyUp());
        verify(listener, times(1)).onClick(view);

        view.setPressed(false);
        reset(listener);
        view.reset();

        assertFalse(view.isPressed());
        event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER);
        mInstrumentation.sendKeySync(event);
        assertTrue(view.isPressed());
        assertTrue(view.hasCalledOnKeyDown());
        event = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER);
        mInstrumentation.sendKeySync(event);
        assertFalse(view.isPressed());
        assertTrue(view.hasCalledOnKeyUp());
        verify(listener, times(1)).onClick(view);
    }

    private void checkBounds(final ViewGroup viewGroup, final View view,
            final CountDownLatch countDownLatch, final int left, final int top,
            final int width, final int height) {
        viewGroup.getViewTreeObserver().addOnPreDrawListener(
                new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                assertEquals(left, view.getLeft());
                assertEquals(top, view.getTop());
                assertEquals(width, view.getWidth());
                assertEquals(height, view.getHeight());
                countDownLatch.countDown();
                viewGroup.getViewTreeObserver().removeOnPreDrawListener(this);
                return true;
            }
        });
    }

    @Test
    public void testAddRemoveAffectsWrapContentLayout() throws Throwable {
        final int childWidth = 100;
        final int childHeight = 200;
        final int parentHeight = 400;
        final LinearLayout parent = new LinearLayout(mActivity);
        ViewGroup.LayoutParams parentParams = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, parentHeight);
        parent.setLayoutParams(parentParams);
        final MockView child = new MockView(mActivity);
        child.setBackgroundColor(Color.GREEN);
        ViewGroup.LayoutParams childParams = new ViewGroup.LayoutParams(childWidth, childHeight);
        child.setLayoutParams(childParams);
        final ViewGroup viewGroup = (ViewGroup) mActivity.findViewById(R.id.viewlayout_root);

        // Idea:
        // Add the wrap_content parent view to the hierarchy (removing other views as they
        // are not needed), test that parent is 0xparentHeight
        // Add the child view to the parent, test that parent has same width as child
        // Remove the child view from the parent, test that parent is 0xparentHeight
        final CountDownLatch countDownLatch1 = new CountDownLatch(1);
        mActivityRule.runOnUiThread(() -> {
            viewGroup.removeAllViews();
            viewGroup.addView(parent);
            checkBounds(viewGroup, parent, countDownLatch1, 0, 0, 0, parentHeight);
        });
        countDownLatch1.await(500, TimeUnit.MILLISECONDS);

        final CountDownLatch countDownLatch2 = new CountDownLatch(1);
        mActivityRule.runOnUiThread(() -> {
            parent.addView(child);
            checkBounds(viewGroup, parent, countDownLatch2, 0, 0, childWidth, parentHeight);
        });
        countDownLatch2.await(500, TimeUnit.MILLISECONDS);

        final CountDownLatch countDownLatch3 = new CountDownLatch(1);
        mActivityRule.runOnUiThread(() -> {
            parent.removeView(child);
            checkBounds(viewGroup, parent, countDownLatch3, 0, 0, 0, parentHeight);
        });
        countDownLatch3.await(500, TimeUnit.MILLISECONDS);
    }

    @UiThreadTest
    @Test
    public void testDispatchKeyEvent() {
        MockView view = (MockView) mActivity.findViewById(R.id.mock_view);
        MockView mockView1 = new MockView(mActivity);
        MockView mockView2 = new MockView(mActivity);
        ViewGroup viewGroup = (ViewGroup) mActivity.findViewById(R.id.viewlayout_root);
        viewGroup.addView(mockView1);
        viewGroup.addView(mockView2);
        view.setFocusable(true);
        mockView1.setFocusable(true);
        mockView2.setFocusable(true);

        assertFalse(view.hasCalledOnKeyDown());
        assertFalse(mockView1.hasCalledOnKeyDown());
        assertFalse(mockView2.hasCalledOnKeyDown());
        KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_0);
        assertFalse(view.dispatchKeyEvent(event));
        assertTrue(view.hasCalledOnKeyDown());
        assertFalse(mockView1.hasCalledOnKeyDown());
        assertFalse(mockView2.hasCalledOnKeyDown());

        view.reset();
        mockView1.reset();
        mockView2.reset();
        assertFalse(view.hasCalledOnKeyDown());
        assertFalse(mockView1.hasCalledOnKeyDown());
        assertFalse(mockView2.hasCalledOnKeyDown());
        event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_0);
        assertFalse(mockView1.dispatchKeyEvent(event));
        assertFalse(view.hasCalledOnKeyDown());
        // issue 1695243
        // When the view has NOT focus, it dispatches to itself, which disobey the javadoc.
        assertTrue(mockView1.hasCalledOnKeyDown());
        assertFalse(mockView2.hasCalledOnKeyDown());

        assertFalse(view.hasCalledOnKeyUp());
        event = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_0);
        assertFalse(view.dispatchKeyEvent(event));
        assertTrue(view.hasCalledOnKeyUp());

        assertFalse(view.hasCalledOnKeyMultiple());
        event = new KeyEvent(1, 2, KeyEvent.ACTION_MULTIPLE, KeyEvent.KEYCODE_0, 2);
        assertFalse(view.dispatchKeyEvent(event));
        assertTrue(view.hasCalledOnKeyMultiple());

        try {
            view.dispatchKeyEvent(null);
            fail("should throw NullPointerException");
        } catch (NullPointerException e) {
            // expected
        }

        view.reset();
        event = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_0);
        View.OnKeyListener listener = mock(View.OnKeyListener.class);
        doReturn(true).when(listener).onKey(any(), anyInt(), any());
        view.setOnKeyListener(listener);
        verifyZeroInteractions(listener);
        assertTrue(view.dispatchKeyEvent(event));
        ArgumentCaptor<KeyEvent> keyEventCaptor = ArgumentCaptor.forClass(KeyEvent.class);
        verify(listener, times(1)).onKey(eq(view), eq(KeyEvent.KEYCODE_0),
                keyEventCaptor.capture());
        assertEquals(KeyEvent.ACTION_UP, keyEventCaptor.getValue().getAction());
        assertEquals(KeyEvent.KEYCODE_0, keyEventCaptor.getValue().getKeyCode());
        assertFalse(view.hasCalledOnKeyUp());
    }

    @UiThreadTest
    @Test
    public void testDispatchTouchEvent() {
        ViewGroup viewGroup = (ViewGroup) mActivity.findViewById(R.id.viewlayout_root);
        MockView mockView1 = new MockView(mActivity);
        MockView mockView2 = new MockView(mActivity);
        viewGroup.addView(mockView1);
        viewGroup.addView(mockView2);

        int[] xy = new int[2];
        mockView1.getLocationOnScreen(xy);

        final int viewWidth = mockView1.getWidth();
        final int viewHeight = mockView1.getHeight();
        final float x = xy[0] + viewWidth / 2.0f;
        final float y = xy[1] + viewHeight / 2.0f;

        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_MOVE,
                x, y, 0);

        assertFalse(mockView1.hasCalledOnTouchEvent());
        assertFalse(mockView1.dispatchTouchEvent(event));
        assertTrue(mockView1.hasCalledOnTouchEvent());

        assertFalse(mockView2.hasCalledOnTouchEvent());
        assertFalse(mockView2.dispatchTouchEvent(event));
        // issue 1695243
        // it passes the touch screen motion event down to itself even if it is not the target view.
        assertTrue(mockView2.hasCalledOnTouchEvent());

        mockView1.reset();
        View.OnTouchListener listener = mock(View.OnTouchListener.class);
        doReturn(true).when(listener).onTouch(any(), any());
        mockView1.setOnTouchListener(listener);
        verifyZeroInteractions(listener);
        assertTrue(mockView1.dispatchTouchEvent(event));
        verify(listener, times(1)).onTouch(mockView1, event);
        assertFalse(mockView1.hasCalledOnTouchEvent());
    }

    @Test
    public void testInvalidate1() throws Throwable {
        final MockView view = (MockView) mActivity.findViewById(R.id.mock_view);
        assertTrue(view.hasCalledOnDraw());

        view.reset();
        mActivityRule.runOnUiThread(view::invalidate);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(view::hasCalledOnDraw);

        view.reset();
        mActivityRule.runOnUiThread(() -> {
            view.setVisibility(View.INVISIBLE);
            view.invalidate();
        });
        mInstrumentation.waitForIdleSync();
        assertFalse(view.hasCalledOnDraw());
    }

    @Test
    public void testInvalidate2() throws Throwable {
        final MockView view = (MockView) mActivity.findViewById(R.id.mock_view);
        assertTrue(view.hasCalledOnDraw());

        try {
            view.invalidate(null);
            fail("should throw NullPointerException");
        } catch (NullPointerException e) {
        }

        view.reset();
        final Rect dirty = new Rect(view.getLeft() + 1, view.getTop() + 1,
                view.getLeft() + view.getWidth() / 2, view.getTop() + view.getHeight() / 2);
        mActivityRule.runOnUiThread(() -> view.invalidate(dirty));
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(view::hasCalledOnDraw);

        view.reset();
        mActivityRule.runOnUiThread(() -> {
            view.setVisibility(View.INVISIBLE);
            view.invalidate(dirty);
        });
        mInstrumentation.waitForIdleSync();
        assertFalse(view.hasCalledOnDraw());
    }

    @Test
    public void testInvalidate3() throws Throwable {
        final MockView view = (MockView) mActivity.findViewById(R.id.mock_view);
        assertTrue(view.hasCalledOnDraw());

        view.reset();
        final Rect dirty = new Rect(view.getLeft() + 1, view.getTop() + 1,
                view.getLeft() + view.getWidth() / 2, view.getTop() + view.getHeight() / 2);
        mActivityRule.runOnUiThread(
                () -> view.invalidate(dirty.left, dirty.top, dirty.right, dirty.bottom));
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(view::hasCalledOnDraw);

        view.reset();
        mActivityRule.runOnUiThread(() -> {
            view.setVisibility(View.INVISIBLE);
            view.invalidate(dirty.left, dirty.top, dirty.right, dirty.bottom);
        });
        mInstrumentation.waitForIdleSync();
        assertFalse(view.hasCalledOnDraw());
    }

    @Test
    public void testInvalidateDrawable() throws Throwable {
        final MockView view = (MockView) mActivity.findViewById(R.id.mock_view);
        final Drawable d1 = mResources.getDrawable(R.drawable.scenery);
        final Drawable d2 = mResources.getDrawable(R.drawable.pass);

        view.reset();
        mActivityRule.runOnUiThread(() -> {
            view.setBackgroundDrawable(d1);
            view.invalidateDrawable(d1);
        });
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(view::hasCalledOnDraw);

        view.reset();
        mActivityRule.runOnUiThread(() -> view.invalidateDrawable(d2));
        mInstrumentation.waitForIdleSync();
        assertFalse(view.hasCalledOnDraw());

        MockView viewTestNull = new MockView(mActivity);
        try {
            viewTestNull.invalidateDrawable(null);
            fail("should throw NullPointerException");
        } catch (NullPointerException e) {
        }
    }

    @UiThreadTest
    @Test
    public void testOnFocusChanged() {
        MockView view = (MockView) mActivity.findViewById(R.id.mock_view);

        mActivity.findViewById(R.id.fit_windows).setFocusable(true);
        view.setFocusable(true);
        assertFalse(view.hasCalledOnFocusChanged());

        view.requestFocus();
        assertTrue(view.hasCalledOnFocusChanged());

        view.reset();
        view.clearFocus();
        assertTrue(view.hasCalledOnFocusChanged());
    }

    @UiThreadTest
    @Test
    public void testRestoreDefaultFocus() {
        MockView view = new MockView(mActivity);
        view.restoreDefaultFocus();
        assertTrue(view.hasCalledRequestFocus());
    }

    @Test
    public void testDrawableState() {
        MockView view = new MockView(mActivity);
        view.setParent(mMockParent);

        assertFalse(view.hasCalledOnCreateDrawableState());
        assertTrue(Arrays.equals(MockView.getEnabledStateSet(), view.getDrawableState()));
        assertTrue(view.hasCalledOnCreateDrawableState());

        view.reset();
        assertFalse(view.hasCalledOnCreateDrawableState());
        assertTrue(Arrays.equals(MockView.getEnabledStateSet(), view.getDrawableState()));
        assertFalse(view.hasCalledOnCreateDrawableState());

        view.reset();
        assertFalse(view.hasCalledDrawableStateChanged());
        view.setPressed(true);
        assertTrue(view.hasCalledDrawableStateChanged());
        assertTrue(Arrays.equals(MockView.getPressedEnabledStateSet(), view.getDrawableState()));
        assertTrue(view.hasCalledOnCreateDrawableState());

        view.reset();
        mMockParent.reset();
        assertFalse(view.hasCalledDrawableStateChanged());
        assertFalse(mMockParent.hasChildDrawableStateChanged());
        view.refreshDrawableState();
        assertTrue(view.hasCalledDrawableStateChanged());
        assertTrue(mMockParent.hasChildDrawableStateChanged());
        assertTrue(Arrays.equals(MockView.getPressedEnabledStateSet(), view.getDrawableState()));
        assertTrue(view.hasCalledOnCreateDrawableState());
    }

    @Test
    public void testWindowFocusChanged() {
        final MockView view = (MockView) mActivity.findViewById(R.id.mock_view);

        // Wait until the window has been focused.
        PollingCheck.waitFor(TIMEOUT_DELTA, view::hasWindowFocus);

        PollingCheck.waitFor(view::hasCalledOnWindowFocusChanged);

        assertTrue(view.hasCalledOnWindowFocusChanged());
        assertTrue(view.hasCalledDispatchWindowFocusChanged());

        view.reset();
        assertFalse(view.hasCalledOnWindowFocusChanged());
        assertFalse(view.hasCalledDispatchWindowFocusChanged());

        CtsActivity activity = mCtsActivityRule.launchActivity(null);

        // Wait until the window lost focus.
        PollingCheck.waitFor(TIMEOUT_DELTA, () -> !view.hasWindowFocus());

        assertTrue(view.hasCalledOnWindowFocusChanged());
        assertTrue(view.hasCalledDispatchWindowFocusChanged());

        activity.finish();
    }

    @Test
    public void testDraw() throws Throwable {
        final MockView view = (MockView) mActivity.findViewById(R.id.mock_view);
        mActivityRule.runOnUiThread(view::requestLayout);
        mInstrumentation.waitForIdleSync();

        assertTrue(view.hasCalledOnDraw());
        assertTrue(view.hasCalledDispatchDraw());
    }

    @Test
    public void testRequestFocusFromTouch() {
        View view = new View(mActivity);
        view.setFocusable(true);
        assertFalse(view.isFocused());

        view.requestFocusFromTouch();
        assertTrue(view.isFocused());

        view.requestFocusFromTouch();
        assertTrue(view.isFocused());
    }

    @Test
    public void testRequestRectangleOnScreen1() {
        MockView view = new MockView(mActivity);
        Rect rectangle = new Rect(10, 10, 20, 30);
        MockViewGroupParent parent = new MockViewGroupParent(mActivity);

        // parent is null
        assertFalse(view.requestRectangleOnScreen(rectangle, true));
        assertFalse(view.requestRectangleOnScreen(rectangle, false));
        assertFalse(view.requestRectangleOnScreen(null, true));

        view.setParent(parent);
        view.scrollTo(1, 2);
        assertFalse(parent.hasRequestChildRectangleOnScreen());

        assertFalse(view.requestRectangleOnScreen(rectangle, true));
        assertTrue(parent.hasRequestChildRectangleOnScreen());

        parent.reset();
        view.scrollTo(11, 22);
        assertFalse(parent.hasRequestChildRectangleOnScreen());

        assertFalse(view.requestRectangleOnScreen(rectangle, true));
        assertTrue(parent.hasRequestChildRectangleOnScreen());

        try {
            view.requestRectangleOnScreen(null, true);
            fail("should throw NullPointerException");
        } catch (NullPointerException e) {
        }
    }

    @Test
    public void testRequestRectangleOnScreen2() {
        MockView view = new MockView(mActivity);
        Rect rectangle = new Rect();
        MockViewGroupParent parent = new MockViewGroupParent(mActivity);

        MockViewGroupParent grandparent = new MockViewGroupParent(mActivity);

        // parent is null
        assertFalse(view.requestRectangleOnScreen(rectangle));
        assertFalse(view.requestRectangleOnScreen(null));
        assertEquals(0, rectangle.left);
        assertEquals(0, rectangle.top);
        assertEquals(0, rectangle.right);
        assertEquals(0, rectangle.bottom);

        parent.addView(view);
        parent.scrollTo(1, 2);
        grandparent.addView(parent);

        assertFalse(parent.hasRequestChildRectangleOnScreen());
        assertFalse(grandparent.hasRequestChildRectangleOnScreen());

        assertFalse(view.requestRectangleOnScreen(rectangle));

        assertTrue(parent.hasRequestChildRectangleOnScreen());
        assertTrue(grandparent.hasRequestChildRectangleOnScreen());

        // it is grand parent's responsibility to check parent's scroll offset
        final Rect requestedRect = grandparent.getLastRequestedChildRectOnScreen();
        assertEquals(0, requestedRect.left);
        assertEquals(0, requestedRect.top);
        assertEquals(0, requestedRect.right);
        assertEquals(0, requestedRect.bottom);

        try {
            view.requestRectangleOnScreen(null);
            fail("should throw NullPointerException");
        } catch (NullPointerException e) {
        }
    }

    @Test
    public void testRequestRectangleOnScreen3() {
        requestRectangleOnScreenTest(false);
    }

    @Test
    public void testRequestRectangleOnScreen4() {
        requestRectangleOnScreenTest(true);
    }

    @Test
    public void testRequestRectangleOnScreen5() {
        MockView child = new MockView(mActivity);

        MockViewGroupParent parent = new MockViewGroupParent(mActivity);
        MockViewGroupParent grandParent = new MockViewGroupParent(mActivity);
        parent.addView(child);
        grandParent.addView(parent);

        child.layout(5, 6, 7, 9);
        child.requestRectangleOnScreen(new Rect(10, 10, 12, 13));
        assertEquals(new Rect(10, 10, 12, 13), parent.getLastRequestedChildRectOnScreen());
        assertEquals(new Rect(15, 16, 17, 19), grandParent.getLastRequestedChildRectOnScreen());

        child.scrollBy(1, 2);
        child.requestRectangleOnScreen(new Rect(10, 10, 12, 13));
        assertEquals(new Rect(10, 10, 12, 13), parent.getLastRequestedChildRectOnScreen());
        assertEquals(new Rect(14, 14, 16, 17), grandParent.getLastRequestedChildRectOnScreen());
    }

    private void requestRectangleOnScreenTest(boolean scrollParent) {
        MockView child = new MockView(mActivity);

        MockViewGroupParent parent = new MockViewGroupParent(mActivity);
        MockViewGroupParent grandParent = new MockViewGroupParent(mActivity);
        parent.addView(child);
        grandParent.addView(parent);

        child.requestRectangleOnScreen(new Rect(10, 10, 12, 13));
        assertEquals(new Rect(10, 10, 12, 13), parent.getLastRequestedChildRectOnScreen());
        assertEquals(new Rect(10, 10, 12, 13), grandParent.getLastRequestedChildRectOnScreen());

        child.scrollBy(1, 2);
        if (scrollParent) {
            // should not affect anything
            parent.scrollBy(25, 30);
            parent.layout(3, 5, 7, 9);
        }
        child.requestRectangleOnScreen(new Rect(10, 10, 12, 13));
        assertEquals(new Rect(10, 10, 12, 13), parent.getLastRequestedChildRectOnScreen());
        assertEquals(new Rect(9, 8, 11, 11), grandParent.getLastRequestedChildRectOnScreen());
    }

    @Test
    public void testRequestRectangleOnScreenWithScale() {
        // scale should not affect the rectangle
        MockView child = new MockView(mActivity);
        child.setScaleX(2);
        child.setScaleX(3);
        MockViewGroupParent parent = new MockViewGroupParent(mActivity);
        MockViewGroupParent grandParent = new MockViewGroupParent(mActivity);
        parent.addView(child);
        grandParent.addView(parent);
        child.requestRectangleOnScreen(new Rect(10, 10, 12, 13));
        assertEquals(new Rect(10, 10, 12, 13), parent.getLastRequestedChildRectOnScreen());
        assertEquals(new Rect(10, 10, 12, 13), grandParent.getLastRequestedChildRectOnScreen());
    }

    /**
     * For the duration of the tap timeout we are in a 'prepressed' state
     * to differentiate between taps and touch scrolls.
     * Wait at least this long before testing if the view is pressed
     * by calling this function.
     */
    private void waitPrepressedTimeout() {
        try {
            Thread.sleep(ViewConfiguration.getTapTimeout() + 10);
        } catch (InterruptedException e) {
            Log.e(LOG_TAG, "waitPrepressedTimeout() interrupted! Test may fail!", e);
        }
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testOnTouchEvent() throws Throwable {
        final MockView view = (MockView) mActivity.findViewById(R.id.mock_view);

        assertTrue(view.isEnabled());
        assertFalse(view.isClickable());
        assertFalse(view.isLongClickable());

        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, view);
        assertTrue(view.hasCalledOnTouchEvent());

        mActivityRule.runOnUiThread(() -> {
            view.setEnabled(true);
            view.setClickable(true);
            view.setLongClickable(true);
        });
        mInstrumentation.waitForIdleSync();
        assertTrue(view.isEnabled());
        assertTrue(view.isClickable());
        assertTrue(view.isLongClickable());

        // MotionEvent.ACTION_DOWN
        int[] xy = new int[2];
        view.getLocationOnScreen(xy);

        final int viewWidth = view.getWidth();
        final int viewHeight = view.getHeight();
        float x = xy[0] + viewWidth / 2.0f;
        float y = xy[1] + viewHeight / 2.0f;

        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_DOWN,
                x, y, 0);
        assertFalse(view.isPressed());
        mInstrumentation.sendPointerSync(event);
        waitPrepressedTimeout();
        assertTrue(view.hasCalledOnTouchEvent());
        assertTrue(view.isPressed());

        // MotionEvent.ACTION_MOVE
        // move out of the bound.
        view.reset();
        downTime = SystemClock.uptimeMillis();
        eventTime = SystemClock.uptimeMillis();
        int slop = ViewConfiguration.get(mActivity).getScaledTouchSlop();
        x = xy[0] + viewWidth + slop;
        y = xy[1] + viewHeight + slop;
        event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_MOVE, x, y, 0);
        mInstrumentation.sendPointerSync(event);
        assertTrue(view.hasCalledOnTouchEvent());
        assertFalse(view.isPressed());

        // move into view
        view.reset();
        downTime = SystemClock.uptimeMillis();
        eventTime = SystemClock.uptimeMillis();
        x = xy[0] + viewWidth - 1;
        y = xy[1] + viewHeight - 1;
        event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_MOVE, x, y, 0);
        mInstrumentation.sendPointerSync(event);
        waitPrepressedTimeout();
        assertTrue(view.hasCalledOnTouchEvent());
        assertFalse(view.isPressed());

        // MotionEvent.ACTION_UP
        View.OnClickListener listener = mock(View.OnClickListener.class);
        view.setOnClickListener(listener);
        view.reset();
        downTime = SystemClock.uptimeMillis();
        eventTime = SystemClock.uptimeMillis();
        event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_UP, x, y, 0);
        mInstrumentation.sendPointerSync(event);
        assertTrue(view.hasCalledOnTouchEvent());
        verifyZeroInteractions(listener);

        view.reset();
        x = xy[0] + viewWidth / 2.0f;
        y = xy[1] + viewHeight / 2.0f;
        downTime = SystemClock.uptimeMillis();
        eventTime = SystemClock.uptimeMillis();
        event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_DOWN, x, y, 0);
        mInstrumentation.sendPointerSync(event);
        assertTrue(view.hasCalledOnTouchEvent());

        // MotionEvent.ACTION_CANCEL
        view.reset();
        reset(listener);
        downTime = SystemClock.uptimeMillis();
        eventTime = SystemClock.uptimeMillis();
        event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_CANCEL, x, y, 0);
        mInstrumentation.sendPointerSync(event);
        assertTrue(view.hasCalledOnTouchEvent());
        assertFalse(view.isPressed());
        verifyZeroInteractions(listener);
    }

    @Test
    public void testBringToFront() {
        MockView view = new MockView(mActivity);
        view.setParent(mMockParent);

        assertFalse(mMockParent.hasBroughtChildToFront());
        view.bringToFront();
        assertTrue(mMockParent.hasBroughtChildToFront());
    }

    @Test
    public void testGetApplicationWindowToken() {
        View view = new View(mActivity);
        // mAttachInfo is null
        assertNull(view.getApplicationWindowToken());

        // mAttachInfo is not null
        view = mActivity.findViewById(R.id.fit_windows);
        assertNotNull(view.getApplicationWindowToken());
    }

    @Test
    public void testGetBottomPaddingOffset() {
        MockView view = new MockView(mActivity);
        assertEquals(0, view.getBottomPaddingOffset());
    }

    @Test
    public void testGetLeftPaddingOffset() {
        MockView view = new MockView(mActivity);
        assertEquals(0, view.getLeftPaddingOffset());
    }

    @Test
    public void testGetRightPaddingOffset() {
        MockView view = new MockView(mActivity);
        assertEquals(0, view.getRightPaddingOffset());
    }

    @Test
    public void testGetTopPaddingOffset() {
        MockView view = new MockView(mActivity);
        assertEquals(0, view.getTopPaddingOffset());
    }

    @Test
    public void testIsPaddingOffsetRequired() {
        MockView view = new MockView(mActivity);
        assertFalse(view.isPaddingOffsetRequired());
    }

    @UiThreadTest
    @Test
    public void testPadding() {
        MockView view = (MockView) mActivity.findViewById(R.id.mock_view_padding_full);
        Drawable background = view.getBackground();
        Rect backgroundPadding = new Rect();
        background.getPadding(backgroundPadding);

        // There is some background with a non null padding
        assertNotNull(background);
        assertTrue(backgroundPadding.left != 0);
        assertTrue(backgroundPadding.right != 0);
        assertTrue(backgroundPadding.top != 0);
        assertTrue(backgroundPadding.bottom != 0);

        // The XML defines android:padding="0dp" and that should be the resulting padding
        assertEquals(0, view.getPaddingLeft());
        assertEquals(0, view.getPaddingTop());
        assertEquals(0, view.getPaddingRight());
        assertEquals(0, view.getPaddingBottom());

        // LEFT case
        view = (MockView) mActivity.findViewById(R.id.mock_view_padding_left);
        background = view.getBackground();
        backgroundPadding = new Rect();
        background.getPadding(backgroundPadding);

        // There is some background with a non null padding
        assertNotNull(background);
        assertTrue(backgroundPadding.left != 0);
        assertTrue(backgroundPadding.right != 0);
        assertTrue(backgroundPadding.top != 0);
        assertTrue(backgroundPadding.bottom != 0);

        // The XML defines android:paddingLeft="0dp" and that should be the resulting padding
        assertEquals(0, view.getPaddingLeft());
        assertEquals(backgroundPadding.top, view.getPaddingTop());
        assertEquals(backgroundPadding.right, view.getPaddingRight());
        assertEquals(backgroundPadding.bottom, view.getPaddingBottom());

        // RIGHT case
        view = (MockView) mActivity.findViewById(R.id.mock_view_padding_right);
        background = view.getBackground();
        backgroundPadding = new Rect();
        background.getPadding(backgroundPadding);

        // There is some background with a non null padding
        assertNotNull(background);
        assertTrue(backgroundPadding.left != 0);
        assertTrue(backgroundPadding.right != 0);
        assertTrue(backgroundPadding.top != 0);
        assertTrue(backgroundPadding.bottom != 0);

        // The XML defines android:paddingRight="0dp" and that should be the resulting padding
        assertEquals(backgroundPadding.left, view.getPaddingLeft());
        assertEquals(backgroundPadding.top, view.getPaddingTop());
        assertEquals(0, view.getPaddingRight());
        assertEquals(backgroundPadding.bottom, view.getPaddingBottom());

        // TOP case
        view = (MockView) mActivity.findViewById(R.id.mock_view_padding_top);
        background = view.getBackground();
        backgroundPadding = new Rect();
        background.getPadding(backgroundPadding);

        // There is some background with a non null padding
        assertNotNull(background);
        assertTrue(backgroundPadding.left != 0);
        assertTrue(backgroundPadding.right != 0);
        assertTrue(backgroundPadding.top != 0);
        assertTrue(backgroundPadding.bottom != 0);

        // The XML defines android:paddingTop="0dp" and that should be the resulting padding
        assertEquals(backgroundPadding.left, view.getPaddingLeft());
        assertEquals(0, view.getPaddingTop());
        assertEquals(backgroundPadding.right, view.getPaddingRight());
        assertEquals(backgroundPadding.bottom, view.getPaddingBottom());

        // BOTTOM case
        view = (MockView) mActivity.findViewById(R.id.mock_view_padding_bottom);
        background = view.getBackground();
        backgroundPadding = new Rect();
        background.getPadding(backgroundPadding);

        // There is some background with a non null padding
        assertNotNull(background);
        assertTrue(backgroundPadding.left != 0);
        assertTrue(backgroundPadding.right != 0);
        assertTrue(backgroundPadding.top != 0);
        assertTrue(backgroundPadding.bottom != 0);

        // The XML defines android:paddingBottom="0dp" and that should be the resulting padding
        assertEquals(backgroundPadding.left, view.getPaddingLeft());
        assertEquals(backgroundPadding.top, view.getPaddingTop());
        assertEquals(backgroundPadding.right, view.getPaddingRight());
        assertEquals(0, view.getPaddingBottom());

        // Case for interleaved background/padding changes
        view = (MockView) mActivity.findViewById(R.id.mock_view_padding_runtime_updated);
        background = view.getBackground();
        backgroundPadding = new Rect();
        background.getPadding(backgroundPadding);

        // There is some background with a null padding
        assertNotNull(background);
        assertTrue(backgroundPadding.left == 0);
        assertTrue(backgroundPadding.right == 0);
        assertTrue(backgroundPadding.top == 0);
        assertTrue(backgroundPadding.bottom == 0);

        final int paddingLeft = view.getPaddingLeft();
        final int paddingRight = view.getPaddingRight();
        final int paddingTop = view.getPaddingTop();
        final int paddingBottom = view.getPaddingBottom();
        assertEquals(8, paddingLeft);
        assertEquals(0, paddingTop);
        assertEquals(8, paddingRight);
        assertEquals(0, paddingBottom);

        // Manipulate background and padding
        background.setState(view.getDrawableState());
        background.jumpToCurrentState();
        view.setBackground(background);
        view.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom);

        assertEquals(8, view.getPaddingLeft());
        assertEquals(0, view.getPaddingTop());
        assertEquals(8, view.getPaddingRight());
        assertEquals(0, view.getPaddingBottom());
    }

    @Test
    public void testGetWindowVisibleDisplayFrame() {
        Rect outRect = new Rect();
        View view = new View(mActivity);
        // mAttachInfo is null
        DisplayManager dm = (DisplayManager) mActivity.getApplicationContext().getSystemService(
                Context.DISPLAY_SERVICE);
        Display d = dm.getDisplay(Display.DEFAULT_DISPLAY);
        view.getWindowVisibleDisplayFrame(outRect);
        assertEquals(0, outRect.left);
        assertEquals(0, outRect.top);
        assertEquals(d.getWidth(), outRect.right);
        assertEquals(d.getHeight(), outRect.bottom);

        // mAttachInfo is not null
        outRect = new Rect();
        view = mActivity.findViewById(R.id.fit_windows);
        // it's implementation detail
        view.getWindowVisibleDisplayFrame(outRect);
    }

    @Test
    public void testSetScrollContainer() throws Throwable {
        final MockView mockView = (MockView) mActivity.findViewById(R.id.mock_view);
        final MockView scrollView = (MockView) mActivity.findViewById(R.id.scroll_view);
        Bitmap bitmap = Bitmap.createBitmap(200, 300, Bitmap.Config.RGB_565);
        final BitmapDrawable d = new BitmapDrawable(bitmap);
        final InputMethodManager imm = (InputMethodManager) mActivity.getSystemService(
                Context.INPUT_METHOD_SERVICE);
        final LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(300, 500);
        mActivityRule.runOnUiThread(() -> {
            mockView.setBackgroundDrawable(d);
            mockView.setHorizontalFadingEdgeEnabled(true);
            mockView.setVerticalFadingEdgeEnabled(true);
            mockView.setLayoutParams(layoutParams);
            scrollView.setLayoutParams(layoutParams);

            mockView.setFocusable(true);
            mockView.requestFocus();
            mockView.setScrollContainer(true);
            scrollView.setScrollContainer(false);
            imm.showSoftInput(mockView, 0);
        });
        mInstrumentation.waitForIdleSync();

        // FIXME: why the size of view doesn't change?

        mActivityRule.runOnUiThread(
                () -> imm.hideSoftInputFromInputMethod(mockView.getWindowToken(), 0));
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testTouchMode() throws Throwable {
        final MockView mockView = (MockView) mActivity.findViewById(R.id.mock_view);
        final View fitWindowsView = mActivity.findViewById(R.id.fit_windows);
        mActivityRule.runOnUiThread(() -> {
            mockView.setFocusableInTouchMode(true);
            fitWindowsView.setFocusable(true);
            fitWindowsView.requestFocus();
        });
        mInstrumentation.waitForIdleSync();
        assertTrue(mockView.isFocusableInTouchMode());
        assertFalse(fitWindowsView.isFocusableInTouchMode());
        assertTrue(mockView.isFocusable());
        assertTrue(fitWindowsView.isFocusable());
        assertFalse(mockView.isFocused());
        assertTrue(fitWindowsView.isFocused());
        assertFalse(mockView.isInTouchMode());
        assertFalse(fitWindowsView.isInTouchMode());

        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mockView);
        assertFalse(fitWindowsView.isFocused());
        assertFalse(mockView.isFocused());
        mActivityRule.runOnUiThread(mockView::requestFocus);
        mInstrumentation.waitForIdleSync();
        assertTrue(mockView.isFocused());
        mActivityRule.runOnUiThread(fitWindowsView::requestFocus);
        mInstrumentation.waitForIdleSync();
        assertFalse(fitWindowsView.isFocused());
        assertTrue(mockView.isInTouchMode());
        assertTrue(fitWindowsView.isInTouchMode());

        KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_0);
        mInstrumentation.sendKeySync(keyEvent);
        assertTrue(mockView.isFocused());
        assertFalse(fitWindowsView.isFocused());
        mActivityRule.runOnUiThread(fitWindowsView::requestFocus);
        mInstrumentation.waitForIdleSync();
        assertFalse(mockView.isFocused());
        assertTrue(fitWindowsView.isFocused());
        assertFalse(mockView.isInTouchMode());
        assertFalse(fitWindowsView.isInTouchMode());

        // Mouse events should not trigger touch mode.
        final MotionEvent event =
                CtsMouseUtil.obtainMouseEvent(MotionEvent.ACTION_SCROLL, mockView, 0, 0);
        mInstrumentation.sendPointerSync(event);
        assertFalse(fitWindowsView.isInTouchMode());

        event.setAction(MotionEvent.ACTION_DOWN);
        mInstrumentation.sendPointerSync(event);
        assertFalse(fitWindowsView.isInTouchMode());

        // Stylus events should not trigger touch mode.
        event.setSource(InputDevice.SOURCE_STYLUS);
        mInstrumentation.sendPointerSync(event);
        assertFalse(fitWindowsView.isInTouchMode());

        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mockView);
        assertTrue(fitWindowsView.isInTouchMode());

        event.setSource(InputDevice.SOURCE_MOUSE);
        event.setAction(MotionEvent.ACTION_DOWN);
        mInstrumentation.sendPointerSync(event);
        assertFalse(fitWindowsView.isInTouchMode());
    }

    @UiThreadTest
    @Test
    public void testScrollbarStyle() {
        MockView view = (MockView) mActivity.findViewById(R.id.scroll_view);
        Bitmap bitmap = Bitmap.createBitmap(200, 300, Bitmap.Config.RGB_565);
        BitmapDrawable d = new BitmapDrawable(bitmap);
        view.setBackgroundDrawable(d);
        view.setHorizontalFadingEdgeEnabled(true);
        view.setVerticalFadingEdgeEnabled(true);

        assertTrue(view.isHorizontalScrollBarEnabled());
        assertTrue(view.isVerticalScrollBarEnabled());
        int verticalScrollBarWidth = view.getVerticalScrollbarWidth();
        int horizontalScrollBarHeight = view.getHorizontalScrollbarHeight();
        assertTrue(verticalScrollBarWidth > 0);
        assertTrue(horizontalScrollBarHeight > 0);
        assertEquals(0, view.getPaddingRight());
        assertEquals(0, view.getPaddingBottom());

        view.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
        assertEquals(View.SCROLLBARS_INSIDE_INSET, view.getScrollBarStyle());
        assertEquals(verticalScrollBarWidth, view.getPaddingRight());
        assertEquals(horizontalScrollBarHeight, view.getPaddingBottom());

        view.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        assertEquals(View.SCROLLBARS_OUTSIDE_OVERLAY, view.getScrollBarStyle());
        assertEquals(0, view.getPaddingRight());
        assertEquals(0, view.getPaddingBottom());

        view.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_INSET);
        assertEquals(View.SCROLLBARS_OUTSIDE_INSET, view.getScrollBarStyle());
        assertEquals(verticalScrollBarWidth, view.getPaddingRight());
        assertEquals(horizontalScrollBarHeight, view.getPaddingBottom());

        // TODO: how to get the position of the Scrollbar to assert it is inside or outside.
    }

    @UiThreadTest
    @Test
    public void testScrollFading() {
        MockView view = (MockView) mActivity.findViewById(R.id.mock_view);
        Bitmap bitmap = Bitmap.createBitmap(200, 300, Bitmap.Config.RGB_565);
        BitmapDrawable d = new BitmapDrawable(bitmap);
        view.setBackgroundDrawable(d);

        assertFalse(view.isHorizontalFadingEdgeEnabled());
        assertFalse(view.isVerticalFadingEdgeEnabled());
        assertEquals(0, view.getHorizontalFadingEdgeLength());
        assertEquals(0, view.getVerticalFadingEdgeLength());

        view.setHorizontalFadingEdgeEnabled(true);
        view.setVerticalFadingEdgeEnabled(true);
        assertTrue(view.isHorizontalFadingEdgeEnabled());
        assertTrue(view.isVerticalFadingEdgeEnabled());
        assertTrue(view.getHorizontalFadingEdgeLength() > 0);
        assertTrue(view.getVerticalFadingEdgeLength() > 0);

        final int fadingLength = 20;
        view.setFadingEdgeLength(fadingLength);
        assertEquals(fadingLength, view.getHorizontalFadingEdgeLength());
        assertEquals(fadingLength, view.getVerticalFadingEdgeLength());
    }

    @UiThreadTest
    @Test
    public void testScrolling() {
        MockView view = (MockView) mActivity.findViewById(R.id.mock_view);
        view.reset();
        assertEquals(0, view.getScrollX());
        assertEquals(0, view.getScrollY());
        assertFalse(view.hasCalledOnScrollChanged());

        view.scrollTo(0, 0);
        assertEquals(0, view.getScrollX());
        assertEquals(0, view.getScrollY());
        assertFalse(view.hasCalledOnScrollChanged());

        view.scrollBy(0, 0);
        assertEquals(0, view.getScrollX());
        assertEquals(0, view.getScrollY());
        assertFalse(view.hasCalledOnScrollChanged());

        view.scrollTo(10, 100);
        assertEquals(10, view.getScrollX());
        assertEquals(100, view.getScrollY());
        assertTrue(view.hasCalledOnScrollChanged());

        view.reset();
        assertFalse(view.hasCalledOnScrollChanged());
        view.scrollBy(-10, -100);
        assertEquals(0, view.getScrollX());
        assertEquals(0, view.getScrollY());
        assertTrue(view.hasCalledOnScrollChanged());

        view.reset();
        assertFalse(view.hasCalledOnScrollChanged());
        view.scrollTo(-1, -2);
        assertEquals(-1, view.getScrollX());
        assertEquals(-2, view.getScrollY());
        assertTrue(view.hasCalledOnScrollChanged());
    }

    @Test
    public void testInitializeScrollbarsAndFadingEdge() {
        MockView view = (MockView) mActivity.findViewById(R.id.scroll_view);

        assertTrue(view.isHorizontalScrollBarEnabled());
        assertTrue(view.isVerticalScrollBarEnabled());
        assertFalse(view.isHorizontalFadingEdgeEnabled());
        assertFalse(view.isVerticalFadingEdgeEnabled());

        view = (MockView) mActivity.findViewById(R.id.scroll_view_2);
        final int fadingEdgeLength = 20;

        assertTrue(view.isHorizontalScrollBarEnabled());
        assertTrue(view.isVerticalScrollBarEnabled());
        assertTrue(view.isHorizontalFadingEdgeEnabled());
        assertTrue(view.isVerticalFadingEdgeEnabled());
        assertEquals(fadingEdgeLength, view.getHorizontalFadingEdgeLength());
        assertEquals(fadingEdgeLength, view.getVerticalFadingEdgeLength());
    }

    @UiThreadTest
    @Test
    public void testScrollIndicators() {
        MockView view = (MockView) mActivity.findViewById(R.id.scroll_view);

        assertEquals("Set indicators match those specified in XML",
                View.SCROLL_INDICATOR_TOP | View.SCROLL_INDICATOR_BOTTOM,
                view.getScrollIndicators());

        view.setScrollIndicators(0);
        assertEquals("Cleared indicators", 0, view.getScrollIndicators());

        view.setScrollIndicators(View.SCROLL_INDICATOR_START | View.SCROLL_INDICATOR_RIGHT);
        assertEquals("Set start and right indicators",
                View.SCROLL_INDICATOR_START | View.SCROLL_INDICATOR_RIGHT,
                view.getScrollIndicators());

    }

    @Test
    public void testScrollbarSize() {
        final int configScrollbarSize = ViewConfiguration.get(mActivity).getScaledScrollBarSize();
        final int customScrollbarSize = configScrollbarSize * 2;

        // No explicit scrollbarSize or custom drawables, ViewConfiguration applies.
        final MockView view = (MockView) mActivity.findViewById(R.id.scroll_view);
        assertEquals(configScrollbarSize, view.getScrollBarSize());
        assertEquals(configScrollbarSize, view.getVerticalScrollbarWidth());
        assertEquals(configScrollbarSize, view.getHorizontalScrollbarHeight());

        // No custom drawables, explicit scrollbarSize takes precedence.
        final MockView view2 = (MockView) mActivity.findViewById(R.id.scroll_view_2);
        view2.setScrollBarSize(customScrollbarSize);
        assertEquals(customScrollbarSize, view2.getScrollBarSize());
        assertEquals(customScrollbarSize, view2.getVerticalScrollbarWidth());
        assertEquals(customScrollbarSize, view2.getHorizontalScrollbarHeight());

        // Custom drawables with no intrinsic size, ViewConfiguration applies.
        final MockView view3 = (MockView) mActivity.findViewById(R.id.scroll_view_3);
        assertEquals(configScrollbarSize, view3.getVerticalScrollbarWidth());
        assertEquals(configScrollbarSize, view3.getHorizontalScrollbarHeight());
        // Explicit scrollbarSize takes precedence.
        view3.setScrollBarSize(customScrollbarSize);
        assertEquals(view3.getScrollBarSize(), view3.getVerticalScrollbarWidth());
        assertEquals(view3.getScrollBarSize(), view3.getHorizontalScrollbarHeight());

        // Custom thumb drawables with intrinsic sizes define the scrollbars' dimensions.
        final MockView view4 = (MockView) mActivity.findViewById(R.id.scroll_view_4);
        final Resources res = mActivity.getResources();
        final int thumbWidth = res.getDimensionPixelSize(R.dimen.scrollbar_thumb_width);
        final int thumbHeight = res.getDimensionPixelSize(R.dimen.scrollbar_thumb_height);
        assertEquals(thumbWidth, view4.getVerticalScrollbarWidth());
        assertEquals(thumbHeight, view4.getHorizontalScrollbarHeight());
        // Explicit scrollbarSize has no effect.
        view4.setScrollBarSize(customScrollbarSize);
        assertEquals(thumbWidth, view4.getVerticalScrollbarWidth());
        assertEquals(thumbHeight, view4.getHorizontalScrollbarHeight());

        // Custom thumb and track drawables with intrinsic sizes. Track size take precedence.
        final MockView view5 = (MockView) mActivity.findViewById(R.id.scroll_view_5);
        final int trackWidth = res.getDimensionPixelSize(R.dimen.scrollbar_track_width);
        final int trackHeight = res.getDimensionPixelSize(R.dimen.scrollbar_track_height);
        assertEquals(trackWidth, view5.getVerticalScrollbarWidth());
        assertEquals(trackHeight, view5.getHorizontalScrollbarHeight());
        // Explicit scrollbarSize has no effect.
        view5.setScrollBarSize(customScrollbarSize);
        assertEquals(trackWidth, view5.getVerticalScrollbarWidth());
        assertEquals(trackHeight, view5.getHorizontalScrollbarHeight());

        // Custom thumb and track, track with no intrinsic size, ViewConfiguration applies
        // regardless of the thumb drawable dimensions.
        final MockView view6 = (MockView) mActivity.findViewById(R.id.scroll_view_6);
        assertEquals(configScrollbarSize, view6.getVerticalScrollbarWidth());
        assertEquals(configScrollbarSize, view6.getHorizontalScrollbarHeight());
        // Explicit scrollbarSize takes precedence.
        view6.setScrollBarSize(customScrollbarSize);
        assertEquals(customScrollbarSize, view6.getVerticalScrollbarWidth());
        assertEquals(customScrollbarSize, view6.getHorizontalScrollbarHeight());
    }

    @Test
    public void testOnStartAndFinishTemporaryDetach() throws Throwable {
        final AtomicBoolean exitedDispatchStartTemporaryDetach = new AtomicBoolean(false);
        final AtomicBoolean exitedDispatchFinishTemporaryDetach = new AtomicBoolean(false);

        final View view = new View(mActivity) {
            private boolean mEnteredDispatchStartTemporaryDetach = false;
            private boolean mExitedDispatchStartTemporaryDetach = false;
            private boolean mEnteredDispatchFinishTemporaryDetach = false;
            private boolean mExitedDispatchFinishTemporaryDetach = false;

            private boolean mCalledOnStartTemporaryDetach = false;
            private boolean mCalledOnFinishTemporaryDetach = false;

            @Override
            public void dispatchStartTemporaryDetach() {
                assertFalse(mEnteredDispatchStartTemporaryDetach);
                assertFalse(mExitedDispatchStartTemporaryDetach);
                assertFalse(mEnteredDispatchFinishTemporaryDetach);
                assertFalse(mExitedDispatchFinishTemporaryDetach);
                assertFalse(mCalledOnStartTemporaryDetach);
                assertFalse(mCalledOnFinishTemporaryDetach);
                mEnteredDispatchStartTemporaryDetach = true;

                assertFalse(isTemporarilyDetached());

                super.dispatchStartTemporaryDetach();

                assertTrue(isTemporarilyDetached());

                assertTrue(mEnteredDispatchStartTemporaryDetach);
                assertFalse(mExitedDispatchStartTemporaryDetach);
                assertFalse(mEnteredDispatchFinishTemporaryDetach);
                assertFalse(mExitedDispatchFinishTemporaryDetach);
                assertTrue(mCalledOnStartTemporaryDetach);
                assertFalse(mCalledOnFinishTemporaryDetach);
                mExitedDispatchStartTemporaryDetach = true;
                exitedDispatchStartTemporaryDetach.set(true);
            }

            @Override
            public void dispatchFinishTemporaryDetach() {
                assertTrue(mEnteredDispatchStartTemporaryDetach);
                assertTrue(mExitedDispatchStartTemporaryDetach);
                assertFalse(mEnteredDispatchFinishTemporaryDetach);
                assertFalse(mExitedDispatchFinishTemporaryDetach);
                assertTrue(mCalledOnStartTemporaryDetach);
                assertFalse(mCalledOnFinishTemporaryDetach);
                mEnteredDispatchFinishTemporaryDetach = true;

                assertTrue(isTemporarilyDetached());

                super.dispatchFinishTemporaryDetach();

                assertFalse(isTemporarilyDetached());

                assertTrue(mEnteredDispatchStartTemporaryDetach);
                assertTrue(mExitedDispatchStartTemporaryDetach);
                assertTrue(mEnteredDispatchFinishTemporaryDetach);
                assertFalse(mExitedDispatchFinishTemporaryDetach);
                assertTrue(mCalledOnStartTemporaryDetach);
                assertTrue(mCalledOnFinishTemporaryDetach);
                mExitedDispatchFinishTemporaryDetach = true;
                exitedDispatchFinishTemporaryDetach.set(true);
            }

            @Override
            public void onStartTemporaryDetach() {
                assertTrue(mEnteredDispatchStartTemporaryDetach);
                assertFalse(mExitedDispatchStartTemporaryDetach);
                assertFalse(mEnteredDispatchFinishTemporaryDetach);
                assertFalse(mExitedDispatchFinishTemporaryDetach);
                assertFalse(mCalledOnStartTemporaryDetach);
                assertFalse(mCalledOnFinishTemporaryDetach);

                assertTrue(isTemporarilyDetached());

                mCalledOnStartTemporaryDetach = true;
            }

            @Override
            public void onFinishTemporaryDetach() {
                assertTrue(mEnteredDispatchStartTemporaryDetach);
                assertTrue(mExitedDispatchStartTemporaryDetach);
                assertTrue(mEnteredDispatchFinishTemporaryDetach);
                assertFalse(mExitedDispatchFinishTemporaryDetach);
                assertTrue(mCalledOnStartTemporaryDetach);
                assertFalse(mCalledOnFinishTemporaryDetach);

                assertFalse(isTemporarilyDetached());

                mCalledOnFinishTemporaryDetach = true;
            }
        };

        assertFalse(view.isTemporarilyDetached());

        mActivityRule.runOnUiThread(view::dispatchStartTemporaryDetach);
        mInstrumentation.waitForIdleSync();

        assertTrue(view.isTemporarilyDetached());
        assertTrue(exitedDispatchStartTemporaryDetach.get());
        assertFalse(exitedDispatchFinishTemporaryDetach.get());

        mActivityRule.runOnUiThread(view::dispatchFinishTemporaryDetach);
        mInstrumentation.waitForIdleSync();

        assertFalse(view.isTemporarilyDetached());
        assertTrue(exitedDispatchStartTemporaryDetach.get());
        assertTrue(exitedDispatchFinishTemporaryDetach.get());
    }

    @Test
    public void testKeyPreIme() throws Throwable {
        final MockView view = (MockView) mActivity.findViewById(R.id.mock_view);

        mActivityRule.runOnUiThread(() -> {
            view.setFocusable(true);
            view.requestFocus();
        });
        mInstrumentation.waitForIdleSync();

        mInstrumentation.sendKeySync(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK));
        assertTrue(view.hasCalledDispatchKeyEventPreIme());
        assertTrue(view.hasCalledOnKeyPreIme());
    }

    @Test
    public void testHapticFeedback() {
        Vibrator vib = (Vibrator) mActivity.getSystemService(Context.VIBRATOR_SERVICE);
        boolean hasVibrator = vib.hasVibrator();

        final MockView view = (MockView) mActivity.findViewById(R.id.mock_view);
        final int LONG_PRESS = HapticFeedbackConstants.LONG_PRESS;
        final int FLAG_IGNORE_VIEW_SETTING = HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING;
        final int FLAG_IGNORE_GLOBAL_SETTING = HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING;
        final int ALWAYS = FLAG_IGNORE_VIEW_SETTING | FLAG_IGNORE_GLOBAL_SETTING;

        view.setHapticFeedbackEnabled(false);
        assertFalse(view.isHapticFeedbackEnabled());
        assertFalse(view.performHapticFeedback(LONG_PRESS));
        assertFalse(view.performHapticFeedback(LONG_PRESS, FLAG_IGNORE_GLOBAL_SETTING));
        assertEquals(hasVibrator, view.performHapticFeedback(LONG_PRESS, ALWAYS));

        view.setHapticFeedbackEnabled(true);
        assertTrue(view.isHapticFeedbackEnabled());
        assertEquals(hasVibrator, view.performHapticFeedback(LONG_PRESS,
                FLAG_IGNORE_GLOBAL_SETTING));
    }

    @Test
    public void testInputConnection() throws Throwable {
        final InputMethodManager imm = (InputMethodManager) mActivity.getSystemService(
                Context.INPUT_METHOD_SERVICE);
        final MockView view = (MockView) mActivity.findViewById(R.id.mock_view);
        final ViewGroup viewGroup = (ViewGroup) mActivity.findViewById(R.id.viewlayout_root);
        final MockEditText editText = new MockEditText(mActivity);

        mActivityRule.runOnUiThread(() -> {
            viewGroup.addView(editText);
            editText.requestFocus();
        });
        mInstrumentation.waitForIdleSync();
        assertTrue(editText.isFocused());

        mActivityRule.runOnUiThread(() -> imm.showSoftInput(editText, 0));
        mInstrumentation.waitForIdleSync();

        PollingCheck.waitFor(TIMEOUT_DELTA, editText::hasCalledOnCreateInputConnection);

        assertTrue(editText.hasCalledOnCheckIsTextEditor());

        mActivityRule.runOnUiThread(() -> {
            assertTrue(imm.isActive(editText));
            assertFalse(editText.hasCalledCheckInputConnectionProxy());
            imm.isActive(view);
            assertTrue(editText.hasCalledCheckInputConnectionProxy());
        });
    }

    @Test
    public void testFilterTouchesWhenObscured() throws Throwable {
        View.OnTouchListener touchListener = mock(View.OnTouchListener.class);
        doReturn(true).when(touchListener).onTouch(any(), any());
        View view = new View(mActivity);
        view.setOnTouchListener(touchListener);

        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[] {
                new MotionEvent.PointerProperties()
        };
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[] {
                new MotionEvent.PointerCoords()
        };
        MotionEvent obscuredTouch = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN,
                1, props, coords, 0, 0, 0, 0, -1, 0, InputDevice.SOURCE_TOUCHSCREEN,
                MotionEvent.FLAG_WINDOW_IS_OBSCURED);
        MotionEvent unobscuredTouch = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN,
                1, props, coords, 0, 0, 0, 0, -1, 0, InputDevice.SOURCE_TOUCHSCREEN,
                0);

        // Initially filter touches is false so all touches are dispatched.
        assertFalse(view.getFilterTouchesWhenObscured());

        view.dispatchTouchEvent(unobscuredTouch);
        verify(touchListener, times(1)).onTouch(view, unobscuredTouch);
        reset(touchListener);
        view.dispatchTouchEvent(obscuredTouch);
        verify(touchListener, times(1)).onTouch(view, obscuredTouch);
        reset(touchListener);

        // Set filter touches to true so only unobscured touches are dispatched.
        view.setFilterTouchesWhenObscured(true);
        assertTrue(view.getFilterTouchesWhenObscured());

        view.dispatchTouchEvent(unobscuredTouch);
        verify(touchListener, times(1)).onTouch(view, unobscuredTouch);
        reset(touchListener);
        view.dispatchTouchEvent(obscuredTouch);
        verifyZeroInteractions(touchListener);
        reset(touchListener);

        // Set filter touches to false so all touches are dispatched.
        view.setFilterTouchesWhenObscured(false);
        assertFalse(view.getFilterTouchesWhenObscured());

        view.dispatchTouchEvent(unobscuredTouch);
        verify(touchListener, times(1)).onTouch(view, unobscuredTouch);
        reset(touchListener);
        view.dispatchTouchEvent(obscuredTouch);
        verify(touchListener, times(1)).onTouch(view, obscuredTouch);
        reset(touchListener);
    }

    @Test
    public void testBackgroundTint() {
        View inflatedView = mActivity.findViewById(R.id.background_tint);

        assertEquals("Background tint inflated correctly",
                Color.WHITE, inflatedView.getBackgroundTintList().getDefaultColor());
        assertEquals("Background tint mode inflated correctly",
                PorterDuff.Mode.SRC_OVER, inflatedView.getBackgroundTintMode());

        MockDrawable bg = new MockDrawable();
        View view = new View(mActivity);

        view.setBackground(bg);
        assertFalse("No background tint applied by default", bg.hasCalledSetTint());

        view.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
        assertTrue("Background tint applied when setBackgroundTints() called after setBackground()",
                bg.hasCalledSetTint());

        bg.reset();
        view.setBackground(null);
        view.setBackground(bg);
        assertTrue("Background tint applied when setBackgroundTints() called before setBackground()",
                bg.hasCalledSetTint());
    }

    @Test
    public void testStartActionModeWithParent() {
        View view = new View(mActivity);
        MockViewGroup parent = new MockViewGroup(mActivity);
        parent.addView(view);

        ActionMode mode = view.startActionMode(null);

        assertNotNull(mode);
        assertEquals(NO_OP_ACTION_MODE, mode);
        assertTrue(parent.isStartActionModeForChildCalled);
        assertEquals(ActionMode.TYPE_PRIMARY, parent.startActionModeForChildType);
    }

    @Test
    public void testStartActionModeWithoutParent() {
        View view = new View(mActivity);

        ActionMode mode = view.startActionMode(null);

        assertNull(mode);
    }

    @Test
    public void testStartActionModeTypedWithParent() {
        View view = new View(mActivity);
        MockViewGroup parent = new MockViewGroup(mActivity);
        parent.addView(view);

        ActionMode mode = view.startActionMode(null, ActionMode.TYPE_FLOATING);

        assertNotNull(mode);
        assertEquals(NO_OP_ACTION_MODE, mode);
        assertTrue(parent.isStartActionModeForChildCalled);
        assertEquals(ActionMode.TYPE_FLOATING, parent.startActionModeForChildType);
    }

    @Test
    public void testStartActionModeTypedWithoutParent() {
        View view = new View(mActivity);

        ActionMode mode = view.startActionMode(null, ActionMode.TYPE_FLOATING);

        assertNull(mode);
    }

    @Test
    public void testVisibilityAggregated() throws Throwable {
        final View grandparent = mActivity.findViewById(R.id.viewlayout_root);
        final View parent = mActivity.findViewById(R.id.aggregate_visibility_parent);
        final MockView mv = (MockView) mActivity.findViewById(R.id.mock_view_aggregate_visibility);

        assertEquals(parent, mv.getParent());
        assertEquals(grandparent, parent.getParent());

        assertTrue(mv.hasCalledOnVisibilityAggregated());
        assertTrue(mv.getLastAggregatedVisibility());

        final Runnable reset = () -> {
            grandparent.setVisibility(View.VISIBLE);
            parent.setVisibility(View.VISIBLE);
            mv.setVisibility(View.VISIBLE);
            mv.reset();
        };

        mActivityRule.runOnUiThread(reset);

        setVisibilityOnUiThread(parent, View.GONE);

        assertTrue(mv.hasCalledOnVisibilityAggregated());
        assertFalse(mv.getLastAggregatedVisibility());

        mActivityRule.runOnUiThread(reset);

        setVisibilityOnUiThread(grandparent, View.GONE);

        assertTrue(mv.hasCalledOnVisibilityAggregated());
        assertFalse(mv.getLastAggregatedVisibility());

        mActivityRule.runOnUiThread(reset);
        mActivityRule.runOnUiThread(() -> {
            grandparent.setVisibility(View.GONE);
            parent.setVisibility(View.GONE);
            mv.setVisibility(View.VISIBLE);

            grandparent.setVisibility(View.VISIBLE);
        });

        assertTrue(mv.hasCalledOnVisibilityAggregated());
        assertFalse(mv.getLastAggregatedVisibility());

        mActivityRule.runOnUiThread(reset);
        mActivityRule.runOnUiThread(() -> {
            grandparent.setVisibility(View.GONE);
            parent.setVisibility(View.INVISIBLE);

            grandparent.setVisibility(View.VISIBLE);
        });

        assertTrue(mv.hasCalledOnVisibilityAggregated());
        assertFalse(mv.getLastAggregatedVisibility());

        mActivityRule.runOnUiThread(() -> parent.setVisibility(View.VISIBLE));

        assertTrue(mv.getLastAggregatedVisibility());
    }

    @Test
    public void testOverlappingRendering() {
        View overlappingUnsetView = mActivity.findViewById(R.id.overlapping_rendering_unset);
        View overlappingFalseView = mActivity.findViewById(R.id.overlapping_rendering_false);
        View overlappingTrueView = mActivity.findViewById(R.id.overlapping_rendering_true);

        assertTrue(overlappingUnsetView.hasOverlappingRendering());
        assertTrue(overlappingUnsetView.getHasOverlappingRendering());
        overlappingUnsetView.forceHasOverlappingRendering(false);
        assertTrue(overlappingUnsetView.hasOverlappingRendering());
        assertFalse(overlappingUnsetView.getHasOverlappingRendering());
        overlappingUnsetView.forceHasOverlappingRendering(true);
        assertTrue(overlappingUnsetView.hasOverlappingRendering());
        assertTrue(overlappingUnsetView.getHasOverlappingRendering());

        assertTrue(overlappingTrueView.hasOverlappingRendering());
        assertTrue(overlappingTrueView.getHasOverlappingRendering());

        assertTrue(overlappingFalseView.hasOverlappingRendering());
        assertFalse(overlappingFalseView.getHasOverlappingRendering());

        View overridingView = new MockOverlappingRenderingSubclass(mActivity, false);
        assertFalse(overridingView.hasOverlappingRendering());

        overridingView = new MockOverlappingRenderingSubclass(mActivity, true);
        assertTrue(overridingView.hasOverlappingRendering());
        overridingView.forceHasOverlappingRendering(false);
        assertFalse(overridingView.getHasOverlappingRendering());
        assertTrue(overridingView.hasOverlappingRendering());
        overridingView.forceHasOverlappingRendering(true);
        assertTrue(overridingView.getHasOverlappingRendering());
        assertTrue(overridingView.hasOverlappingRendering());
    }

    @Test
    public void testUpdateDragShadow() {
        View view = mActivity.findViewById(R.id.fit_windows);
        assertTrue(view.isAttachedToWindow());

        View.DragShadowBuilder shadowBuilder = mock(View.DragShadowBuilder.class);
        view.startDragAndDrop(ClipData.newPlainText("", ""), shadowBuilder, view, 0);
        reset(shadowBuilder);

        view.updateDragShadow(shadowBuilder);
        // TODO: Verify with the canvas from the drag surface instead.
        verify(shadowBuilder).onDrawShadow(any(Canvas.class));
    }

    @Test
    public void testUpdateDragShadow_detachedView() {
        View view = new View(mActivity);
        assertFalse(view.isAttachedToWindow());

        View.DragShadowBuilder shadowBuilder = mock(View.DragShadowBuilder.class);
        view.startDragAndDrop(ClipData.newPlainText("", ""), shadowBuilder, view, 0);
        reset(shadowBuilder);

        view.updateDragShadow(shadowBuilder);
        verify(shadowBuilder, never()).onDrawShadow(any(Canvas.class));
    }

    @Test
    public void testUpdateDragShadow_noActiveDrag() {
        View view = mActivity.findViewById(R.id.fit_windows);
        assertTrue(view.isAttachedToWindow());

        View.DragShadowBuilder shadowBuilder = mock(View.DragShadowBuilder.class);
        view.updateDragShadow(shadowBuilder);
        verify(shadowBuilder, never()).onDrawShadow(any(Canvas.class));
    }

    private void setVisibilityOnUiThread(final View view, final int visibility) throws Throwable {
        mActivityRule.runOnUiThread(() -> view.setVisibility(visibility));
    }

    private static class MockOverlappingRenderingSubclass extends View {
        boolean mOverlap;

        public MockOverlappingRenderingSubclass(Context context, boolean overlap) {
            super(context);
            mOverlap = overlap;
        }

        @Override
        public boolean hasOverlappingRendering() {
            return mOverlap;
        }
    }

    private static class MockViewGroup extends ViewGroup {
        boolean isStartActionModeForChildCalled = false;
        int startActionModeForChildType = ActionMode.TYPE_PRIMARY;

        public MockViewGroup(Context context) {
            super(context);
        }

        @Override
        public ActionMode startActionModeForChild(View originalView, ActionMode.Callback callback) {
            isStartActionModeForChildCalled = true;
            startActionModeForChildType = ActionMode.TYPE_PRIMARY;
            return NO_OP_ACTION_MODE;
        }

        @Override
        public ActionMode startActionModeForChild(
                View originalView, ActionMode.Callback callback, int type) {
            isStartActionModeForChildCalled = true;
            startActionModeForChildType = type;
            return NO_OP_ACTION_MODE;
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            // no-op
        }
    }

    private static final ActionMode NO_OP_ACTION_MODE =
            new ActionMode() {
                @Override
                public void setTitle(CharSequence title) {}

                @Override
                public void setTitle(int resId) {}

                @Override
                public void setSubtitle(CharSequence subtitle) {}

                @Override
                public void setSubtitle(int resId) {}

                @Override
                public void setCustomView(View view) {}

                @Override
                public void invalidate() {}

                @Override
                public void finish() {}

                @Override
                public Menu getMenu() {
                    return null;
                }

                @Override
                public CharSequence getTitle() {
                    return null;
                }

                @Override
                public CharSequence getSubtitle() {
                    return null;
                }

                @Override
                public View getCustomView() {
                    return null;
                }

                @Override
                public MenuInflater getMenuInflater() {
                    return null;
                }
            };

    @Test
    public void testTranslationSetter() {
        View view = new View(mActivity);
        float offset = 10.0f;
        view.setTranslationX(offset);
        view.setTranslationY(offset);
        view.setTranslationZ(offset);
        view.setElevation(offset);

        assertEquals("Incorrect translationX", offset, view.getTranslationX(), 0.0f);
        assertEquals("Incorrect translationY", offset, view.getTranslationY(), 0.0f);
        assertEquals("Incorrect translationZ", offset, view.getTranslationZ(), 0.0f);
        assertEquals("Incorrect elevation", offset, view.getElevation(), 0.0f);
    }

    @Test
    public void testXYZ() {
        View view = new View(mActivity);
        float offset = 10.0f;
        float start = 15.0f;
        view.setTranslationX(offset);
        view.setLeft((int) start);
        view.setTranslationY(offset);
        view.setTop((int) start);
        view.setTranslationZ(offset);
        view.setElevation(start);

        assertEquals("Incorrect X value", offset + start, view.getX(), 0.0f);
        assertEquals("Incorrect Y value", offset + start, view.getY(), 0.0f);
        assertEquals("Incorrect Z value", offset + start, view.getZ(), 0.0f);
    }

    @Test
    public void testOnHoverEvent() {
        MotionEvent event;

        View view = new View(mActivity);
        long downTime = SystemClock.uptimeMillis();

        // Preconditions.
        assertFalse(view.isHovered());
        assertFalse(view.isClickable());
        assertTrue(view.isEnabled());

        // Simulate an ENTER/EXIT pair on a non-clickable view.
        event = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_HOVER_ENTER, 0, 0, 0);
        view.onHoverEvent(event);
        assertFalse(view.isHovered());
        event.recycle();

        event = MotionEvent.obtain(downTime, downTime + 10, MotionEvent.ACTION_HOVER_EXIT, 0, 0, 0);
        view.onHoverEvent(event);
        assertFalse(view.isHovered());
        event.recycle();

        // Simulate an ENTER/EXIT pair on a clickable view.
        view.setClickable(true);

        event = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_HOVER_ENTER, 0, 0, 0);
        view.onHoverEvent(event);
        assertTrue(view.isHovered());
        event.recycle();

        event = MotionEvent.obtain(downTime, downTime + 10, MotionEvent.ACTION_HOVER_EXIT, 0, 0, 0);
        view.onHoverEvent(event);
        assertFalse(view.isHovered());
        event.recycle();

        // Simulate an ENTER, then disable the view and simulate EXIT.
        event = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_HOVER_ENTER, 0, 0, 0);
        view.onHoverEvent(event);
        assertTrue(view.isHovered());
        event.recycle();

        view.setEnabled(false);

        event = MotionEvent.obtain(downTime, downTime + 10, MotionEvent.ACTION_HOVER_EXIT, 0, 0, 0);
        view.onHoverEvent(event);
        assertFalse(view.isHovered());
        event.recycle();
    }

    private static class MockDrawable extends Drawable {
        private boolean mCalledSetTint = false;

        @Override
        public void draw(Canvas canvas) {}

        @Override
        public void setAlpha(int alpha) {}

        @Override
        public void setColorFilter(ColorFilter cf) {}

        @Override
        public int getOpacity() {
            return 0;
        }

        @Override
        public void setTintList(ColorStateList tint) {
            super.setTintList(tint);
            mCalledSetTint = true;
        }

        public boolean hasCalledSetTint() {
            return mCalledSetTint;
        }

        public void reset() {
            mCalledSetTint = false;
        }
    }

    private static class MockEditText extends EditText {
        private boolean mCalledCheckInputConnectionProxy = false;
        private boolean mCalledOnCreateInputConnection = false;
        private boolean mCalledOnCheckIsTextEditor = false;

        public MockEditText(Context context) {
            super(context);
        }

        @Override
        public boolean checkInputConnectionProxy(View view) {
            mCalledCheckInputConnectionProxy = true;
            return super.checkInputConnectionProxy(view);
        }

        public boolean hasCalledCheckInputConnectionProxy() {
            return mCalledCheckInputConnectionProxy;
        }

        @Override
        public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
            mCalledOnCreateInputConnection = true;
            return super.onCreateInputConnection(outAttrs);
        }

        public boolean hasCalledOnCreateInputConnection() {
            return mCalledOnCreateInputConnection;
        }

        @Override
        public boolean onCheckIsTextEditor() {
            mCalledOnCheckIsTextEditor = true;
            return super.onCheckIsTextEditor();
        }

        public boolean hasCalledOnCheckIsTextEditor() {
            return mCalledOnCheckIsTextEditor;
        }

        public void reset() {
            mCalledCheckInputConnectionProxy = false;
            mCalledOnCreateInputConnection = false;
            mCalledOnCheckIsTextEditor = false;
        }
    }

    private final static class MockViewParent extends ViewGroup {
        private boolean mHasRequestLayout = false;
        private boolean mHasCreateContextMenu = false;
        private boolean mHasShowContextMenuForChild = false;
        private boolean mHasShowContextMenuForChildXY = false;
        private boolean mHasChildDrawableStateChanged = false;
        private boolean mHasBroughtChildToFront = false;

        private final static int[] DEFAULT_PARENT_STATE_SET = new int[] { 789 };

        @Override
        public boolean requestChildRectangleOnScreen(View child, Rect rectangle,
                boolean immediate) {
            return false;
        }

        public MockViewParent(Context context) {
            super(context);
        }

        @Override
        public void bringChildToFront(View child) {
            mHasBroughtChildToFront = true;
        }

        public boolean hasBroughtChildToFront() {
            return mHasBroughtChildToFront;
        }

        @Override
        public void childDrawableStateChanged(View child) {
            mHasChildDrawableStateChanged = true;
        }

        public boolean hasChildDrawableStateChanged() {
            return mHasChildDrawableStateChanged;
        }

        @Override
        public void dispatchSetPressed(boolean pressed) {
            super.dispatchSetPressed(pressed);
        }

        @Override
        public void dispatchSetSelected(boolean selected) {
            super.dispatchSetSelected(selected);
        }

        @Override
        public void clearChildFocus(View child) {

        }

        @Override
        public void createContextMenu(ContextMenu menu) {
            mHasCreateContextMenu = true;
        }

        public boolean hasCreateContextMenu() {
            return mHasCreateContextMenu;
        }

        @Override
        public View focusSearch(View v, int direction) {
            return v;
        }

        @Override
        public void focusableViewAvailable(View v) {

        }

        @Override
        public boolean getChildVisibleRect(View child, Rect r, Point offset) {
            return false;
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {

        }

        @Override
        public ViewParent invalidateChildInParent(int[] location, Rect r) {
            return null;
        }

        @Override
        public boolean isLayoutRequested() {
            return false;
        }

        @Override
        public void recomputeViewAttributes(View child) {

        }

        @Override
        public void requestChildFocus(View child, View focused) {

        }

        @Override
        public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {

        }

        @Override
        public void requestLayout() {
            mHasRequestLayout = true;
        }

        public boolean hasRequestLayout() {
            return mHasRequestLayout;
        }

        @Override
        public void requestTransparentRegion(View child) {

        }

        @Override
        public boolean showContextMenuForChild(View originalView) {
            mHasShowContextMenuForChild = true;
            return false;
        }

        @Override
        public boolean showContextMenuForChild(View originalView, float x, float y) {
            mHasShowContextMenuForChildXY = true;
            return false;
        }

        @Override
        public ActionMode startActionModeForChild(View originalView,
                ActionMode.Callback callback) {
            return null;
        }

        @Override
        public ActionMode startActionModeForChild(View originalView,
                ActionMode.Callback callback, int type) {
            return null;
        }

        public boolean hasShowContextMenuForChild() {
            return mHasShowContextMenuForChild;
        }

        public boolean hasShowContextMenuForChildXY() {
            return mHasShowContextMenuForChildXY;
        }

        @Override
        protected int[] onCreateDrawableState(int extraSpace) {
            return DEFAULT_PARENT_STATE_SET;
        }

        @Override
        public boolean requestSendAccessibilityEvent(View child, AccessibilityEvent event) {
            return false;
        }

        public void reset() {
            mHasRequestLayout = false;
            mHasCreateContextMenu = false;
            mHasShowContextMenuForChild = false;
            mHasShowContextMenuForChildXY = false;
            mHasChildDrawableStateChanged = false;
            mHasBroughtChildToFront = false;
        }

        @Override
        public void childHasTransientStateChanged(View child, boolean hasTransientState) {

        }

        @Override
        public ViewParent getParentForAccessibility() {
            return null;
        }

        @Override
        public void notifySubtreeAccessibilityStateChanged(View child,
            View source, int changeType) {

        }

        @Override
        public boolean onStartNestedScroll(View child, View target, int nestedScrollAxes) {
            return false;
        }

        @Override
        public void onNestedScrollAccepted(View child, View target, int nestedScrollAxes) {
        }

        @Override
        public void onStopNestedScroll(View target) {
        }

        @Override
        public void onNestedScroll(View target, int dxConsumed, int dyConsumed,
                                   int dxUnconsumed, int dyUnconsumed) {
        }

        @Override
        public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {
        }

        @Override
        public boolean onNestedFling(View target, float velocityX, float velocityY,
                boolean consumed) {
            return false;
        }

        @Override
        public boolean onNestedPreFling(View target, float velocityX, float velocityY) {
            return false;
        }

        @Override
        public boolean onNestedPrePerformAccessibilityAction(View target, int action, Bundle args) {
            return false;
        }
    }

    private static class MockViewGroupParent extends ViewGroup implements ViewParent {
        private boolean mHasRequestChildRectangleOnScreen = false;
        private Rect mLastRequestedChildRectOnScreen = new Rect(
                Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);

        public MockViewGroupParent(Context context) {
            super(context);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {

        }

        @Override
        public boolean requestChildRectangleOnScreen(View child,
                Rect rectangle, boolean immediate) {
            mHasRequestChildRectangleOnScreen = true;
            mLastRequestedChildRectOnScreen.set(rectangle);
            return super.requestChildRectangleOnScreen(child, rectangle, immediate);
        }

        public Rect getLastRequestedChildRectOnScreen() {
            return mLastRequestedChildRectOnScreen;
        }

        public boolean hasRequestChildRectangleOnScreen() {
            return mHasRequestChildRectangleOnScreen;
        }

        @Override
        protected void detachViewFromParent(View child) {
            super.detachViewFromParent(child);
        }

        public void reset() {
            mHasRequestChildRectangleOnScreen = false;
        }
    }

    private static final class ViewData {
        public int childCount;
        public String tag;
        public View firstChild;
    }

    private static final Class<?> ASYNC_INFLATE_VIEWS[] = {
        android.app.FragmentBreadCrumbs.class,
// DISABLED because it doesn't have a AppWidgetHostView(Context, AttributeSet)
// constructor, so it's not inflate-able
//        android.appwidget.AppWidgetHostView.class,
        android.gesture.GestureOverlayView.class,
        android.inputmethodservice.ExtractEditText.class,
        android.inputmethodservice.KeyboardView.class,
//        android.media.tv.TvView.class,
//        android.opengl.GLSurfaceView.class,
//        android.view.SurfaceView.class,
        android.view.TextureView.class,
        android.view.ViewStub.class,
//        android.webkit.WebView.class,
        android.widget.AbsoluteLayout.class,
        android.widget.AdapterViewFlipper.class,
        android.widget.AnalogClock.class,
        android.widget.AutoCompleteTextView.class,
        android.widget.Button.class,
        android.widget.CalendarView.class,
        android.widget.CheckBox.class,
        android.widget.CheckedTextView.class,
        android.widget.Chronometer.class,
        android.widget.DatePicker.class,
        android.widget.DialerFilter.class,
        android.widget.DigitalClock.class,
        android.widget.EditText.class,
        android.widget.ExpandableListView.class,
        android.widget.FrameLayout.class,
        android.widget.Gallery.class,
        android.widget.GridView.class,
        android.widget.HorizontalScrollView.class,
        android.widget.ImageButton.class,
        android.widget.ImageSwitcher.class,
        android.widget.ImageView.class,
        android.widget.LinearLayout.class,
        android.widget.ListView.class,
        android.widget.MediaController.class,
        android.widget.MultiAutoCompleteTextView.class,
        android.widget.NumberPicker.class,
        android.widget.ProgressBar.class,
        android.widget.QuickContactBadge.class,
        android.widget.RadioButton.class,
        android.widget.RadioGroup.class,
        android.widget.RatingBar.class,
        android.widget.RelativeLayout.class,
        android.widget.ScrollView.class,
        android.widget.SeekBar.class,
// DISABLED because it has required attributes
//        android.widget.SlidingDrawer.class,
        android.widget.Spinner.class,
        android.widget.StackView.class,
        android.widget.Switch.class,
        android.widget.TabHost.class,
        android.widget.TabWidget.class,
        android.widget.TableLayout.class,
        android.widget.TableRow.class,
        android.widget.TextClock.class,
        android.widget.TextSwitcher.class,
        android.widget.TextView.class,
        android.widget.TimePicker.class,
        android.widget.ToggleButton.class,
        android.widget.TwoLineListItem.class,
//        android.widget.VideoView.class,
        android.widget.ViewAnimator.class,
        android.widget.ViewFlipper.class,
        android.widget.ViewSwitcher.class,
        android.widget.ZoomButton.class,
        android.widget.ZoomControls.class,
    };
}
