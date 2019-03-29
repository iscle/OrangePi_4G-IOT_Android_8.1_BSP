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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.XmlResourceParser;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.BitmapDrawable;
import android.os.Parcelable;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.LargeTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.SparseArray;
import android.view.ActionMode;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.BaseSavedState;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.LayoutAnimationController;
import android.view.animation.RotateAnimation;
import android.view.animation.Transformation;
import android.view.cts.util.XmlUtils;
import android.widget.Button;
import android.widget.TextView;

import com.android.compatibility.common.util.CTSResult;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;

import java.util.ArrayList;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class ViewGroupTest implements CTSResult {
    private Context mContext;
    private MotionEvent mMotionEvent;
    private int mResultCode;

    private MockViewGroup mMockViewGroup;
    private TextView mTextView;
    private MockTextView mMockTextView;

    @Rule
    public ActivityTestRule<CtsActivity> mCtsActivityRule =
            new ActivityTestRule<>(CtsActivity.class, false, false);

    private final Sync mSync = new Sync();
    private static class Sync {
        boolean mHasNotify;
    }

    @UiThreadTest
    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getTargetContext();
        mMockViewGroup = new MockViewGroup(mContext);
        mTextView = new TextView(mContext);
        mMockTextView = new MockTextView(mContext);
    }

    @Test
    public void testConstructor() {
        new MockViewGroup(mContext);
        new MockViewGroup(mContext, null);
        new MockViewGroup(mContext, null, 0);
    }

    @UiThreadTest
    @Test
    public void testAddFocusables() {
        mMockViewGroup.setFocusable(true);

        // Child is focusable.
        ArrayList<View> list = new ArrayList<>();
        list.add(mTextView);
        mMockViewGroup.addView(mTextView);
        mMockViewGroup.addFocusables(list, 0);

        assertEquals(2, list.size());

        // Parent blocks descendants.
        list = new ArrayList<>();
        list.add(mTextView);
        mMockViewGroup.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        mMockViewGroup.setFocusable(false);
        mMockViewGroup.addFocusables(list, 0);
        assertEquals(1, list.size());

        // Both parent and child are focusable.
        list.clear();
        mMockViewGroup.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        mTextView.setFocusable(true);
        mMockViewGroup.setFocusable(true);
        mMockViewGroup.addFocusables(list, 0);
        assertEquals(2, list.size());
    }

    @UiThreadTest
    @Test
    public void testAddKeyboardNavigationClusters() {
        View v1 = new MockView(mContext);
        v1.setFocusableInTouchMode(true);
        View v2 = new MockView(mContext);
        v2.setFocusableInTouchMode(true);
        mMockViewGroup.addView(v1);
        mMockViewGroup.addView(v2);

        // No clusters.
        ArrayList<View> list = new ArrayList<>();
        mMockViewGroup.addKeyboardNavigationClusters(list, 0);
        assertEquals(0, list.size());

        // A cluster and a non-cluster child.
        v1.setKeyboardNavigationCluster(true);
        mMockViewGroup.addKeyboardNavigationClusters(list, 0);
        assertEquals(1, list.size());
        assertEquals(v1, list.get(0));
        list.clear();

        // Blocking descendants from getting focus also blocks group search.
        mMockViewGroup.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        mMockViewGroup.addKeyboardNavigationClusters(list, 0);
        assertEquals(0, list.size());
        mMockViewGroup.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);

        // Testing the results ordering.
        v2.setKeyboardNavigationCluster(true);
        mMockViewGroup.addKeyboardNavigationClusters(list, 0);
        assertEquals(2, list.size());
        assertEquals(v1, list.get(0));
        assertEquals(v2, list.get(1));
        list.clear();

        // 3-level hierarchy.
        ViewGroup parent = new MockViewGroup(mContext);
        parent.addView(mMockViewGroup);
        mMockViewGroup.removeView(v2);
        parent.addKeyboardNavigationClusters(list, 0);
        assertEquals(1, list.size());
        assertEquals(v1, list.get(0));
        list.clear();

        // Cluster with no focusables gets ignored
        mMockViewGroup.addView(v2);
        v2.setFocusable(false);
        mMockViewGroup.addKeyboardNavigationClusters(list, 0);
        assertEquals(1, list.size());
        list.clear();

        // Invisible children get ignored.
        mMockViewGroup.setVisibility(View.GONE);
        parent.addKeyboardNavigationClusters(list, 0);
        assertEquals(0, list.size());
        list.clear();

        // Nested clusters are ignored
        TestClusterHier h = new TestClusterHier();
        h.nestedGroup.setKeyboardNavigationCluster(true);
        h.cluster2.setKeyboardNavigationCluster(false);
        h.top.addKeyboardNavigationClusters(list, View.FOCUS_FORWARD);
        assertTrue(list.contains(h.nestedGroup));
        list.clear();
        h.cluster2.setKeyboardNavigationCluster(true);
        h.top.addKeyboardNavigationClusters(list, View.FOCUS_FORWARD);
        assertFalse(list.contains(h.nestedGroup));
        list.clear();
    }

    @UiThreadTest
    @Test
    public void testAddStatesFromChildren() {
        mMockViewGroup.addView(mTextView);
        assertFalse(mMockViewGroup.addStatesFromChildren());

        mMockViewGroup.setAddStatesFromChildren(true);
        mTextView.performClick();
        assertTrue(mMockViewGroup.addStatesFromChildren());
        assertTrue(mMockViewGroup.isDrawableStateChangedCalled);
    }

    @UiThreadTest
    @Test
    public void testAddTouchables() {
        mMockViewGroup.setFocusable(true);

        ArrayList<View> list = new ArrayList<>();
        mTextView.setVisibility(View.VISIBLE);
        mTextView.setClickable(true);
        mTextView.setEnabled(true);

        list.add(mTextView);
        mMockViewGroup.addView(mTextView);
        mMockViewGroup.addTouchables(list);

        assertEquals(2, list.size());

        View v = mMockViewGroup.getChildAt(0);
        assertSame(mTextView, v);

        v = mMockViewGroup.getChildAt(-1);
        assertNull(v);

        v = mMockViewGroup.getChildAt(1);
        assertNull(v);

        v = mMockViewGroup.getChildAt(100);
        assertNull(v);

        v = mMockViewGroup.getChildAt(-100);
        assertNull(v);
    }

    @UiThreadTest
    @Test
    public void testAddView() {
        assertEquals(0, mMockViewGroup.getChildCount());

        mMockViewGroup.addView(mTextView);
        assertEquals(1, mMockViewGroup.getChildCount());
        assertTrue(mMockViewGroup.isOnViewAddedCalled);
    }

    @UiThreadTest
    @Test
    public void testAddViewWithParaViewInt() {
        assertEquals(0, mMockViewGroup.getChildCount());

        mMockViewGroup.addView(mTextView, -1);
        assertEquals(1, mMockViewGroup.getChildCount());
        assertTrue(mMockViewGroup.isOnViewAddedCalled);
    }

    @UiThreadTest
    @Test
    public void testAddViewWithParaViewLayoutPara() {
        assertEquals(0, mMockViewGroup.getChildCount());

        mMockViewGroup.addView(mTextView, new ViewGroup.LayoutParams(100, 200));

        assertEquals(1, mMockViewGroup.getChildCount());
        assertTrue(mMockViewGroup.isOnViewAddedCalled);
    }

    @UiThreadTest
    @Test
    public void testAddViewWithParaViewIntInt() {
        final int width = 100;
        final int height = 200;

        assertEquals(0, mMockViewGroup.getChildCount());

        mMockViewGroup.addView(mTextView, width, height);
        assertEquals(width, mTextView.getLayoutParams().width);
        assertEquals(height, mTextView.getLayoutParams().height);

        assertEquals(1, mMockViewGroup.getChildCount());
        assertTrue(mMockViewGroup.isOnViewAddedCalled);
    }

    @UiThreadTest
    @Test
    public void testAddViewWidthParaViewIntLayoutParam() {
        assertEquals(0, mMockViewGroup.getChildCount());

        mMockViewGroup.addView(mTextView, -1, new ViewGroup.LayoutParams(100, 200));

        assertEquals(1, mMockViewGroup.getChildCount());
        assertTrue(mMockViewGroup.isOnViewAddedCalled);
    }

    @UiThreadTest
    @Test
    public void testAddViewInLayout() {
        assertEquals(0, mMockViewGroup.getChildCount());

        assertTrue(mMockViewGroup.isRequestLayoutCalled);
        mMockViewGroup.isRequestLayoutCalled = false;
        assertTrue(mMockViewGroup.addViewInLayout(
                mTextView, -1, new ViewGroup.LayoutParams(100, 200)));
        assertEquals(1, mMockViewGroup.getChildCount());
        // check that calling addViewInLayout() does not trigger a
        // requestLayout() on this ViewGroup
        assertFalse(mMockViewGroup.isRequestLayoutCalled);
        assertTrue(mMockViewGroup.isOnViewAddedCalled);
    }

    @UiThreadTest
    @Test
    public void testAttachLayoutAnimationParameters() {
        ViewGroup.LayoutParams param = new ViewGroup.LayoutParams(10, 10);

        mMockViewGroup.attachLayoutAnimationParameters(null, param, 1, 2);
        assertEquals(2, param.layoutAnimationParameters.count);
        assertEquals(1, param.layoutAnimationParameters.index);
    }

    @UiThreadTest
    @Test
    public void testAttachViewToParent() {
        mMockViewGroup.setFocusable(true);
        assertEquals(0, mMockViewGroup.getChildCount());

        ViewGroup.LayoutParams param = new ViewGroup.LayoutParams(10, 10);

        mTextView.setFocusable(true);
        mMockViewGroup.attachViewToParent(mTextView, -1, param);
        assertSame(mMockViewGroup, mTextView.getParent());
        assertEquals(1, mMockViewGroup.getChildCount());
        assertSame(mTextView, mMockViewGroup.getChildAt(0));
    }

    @UiThreadTest
    @Test
    public void testAddViewInLayoutWithParamViewIntLayB() {
        assertEquals(0, mMockViewGroup.getChildCount());

        assertTrue(mMockViewGroup.isRequestLayoutCalled);
        mMockViewGroup.isRequestLayoutCalled = false;
        assertTrue(mMockViewGroup.addViewInLayout(
                mTextView, -1, new ViewGroup.LayoutParams(100, 200), true));

        assertEquals(1, mMockViewGroup.getChildCount());
        // check that calling addViewInLayout() does not trigger a
        // requestLayout() on this ViewGroup
        assertFalse(mMockViewGroup.isRequestLayoutCalled);
        assertTrue(mMockViewGroup.isOnViewAddedCalled);
    }

    @UiThreadTest
    @Test
    public void testBringChildToFront() {
        TextView textView1 = new TextView(mContext);
        TextView textView2 = new TextView(mContext);

        assertEquals(0, mMockViewGroup.getChildCount());

        mMockViewGroup.addView(textView1);
        mMockViewGroup.addView(textView2);
        assertEquals(2, mMockViewGroup.getChildCount());

        mMockViewGroup.bringChildToFront(textView1);
        assertEquals(mMockViewGroup, textView1.getParent());
        assertEquals(2, mMockViewGroup.getChildCount());
        assertNotNull(mMockViewGroup.getChildAt(0));
        assertSame(textView2, mMockViewGroup.getChildAt(0));

        mMockViewGroup.bringChildToFront(textView2);
        assertEquals(mMockViewGroup, textView2.getParent());
        assertEquals(2, mMockViewGroup.getChildCount());
        assertNotNull(mMockViewGroup.getChildAt(0));
        assertSame(textView1, mMockViewGroup.getChildAt(0));
    }

    @UiThreadTest
    @Test
    public void testCanAnimate() {
        assertFalse(mMockViewGroup.canAnimate());

        RotateAnimation animation = new RotateAnimation(0.1f, 0.1f);
        LayoutAnimationController la = new LayoutAnimationController(animation);
        mMockViewGroup.setLayoutAnimation(la);
        assertTrue(mMockViewGroup.canAnimate());
    }

    @UiThreadTest
    @Test
    public void testCheckLayoutParams() {
        assertFalse(mMockViewGroup.checkLayoutParams(null));

        assertTrue(mMockViewGroup.checkLayoutParams(new ViewGroup.LayoutParams(100, 200)));
    }

    @UiThreadTest
    @Test
    public void testChildDrawableStateChanged() {
        mMockViewGroup.setAddStatesFromChildren(true);

        mMockViewGroup.childDrawableStateChanged(null);
        assertTrue(mMockViewGroup.isRefreshDrawableStateCalled);
    }

    @UiThreadTest
    @Test
    public void testCleanupLayoutState() {
        assertTrue(mTextView.isLayoutRequested());

        mMockViewGroup.cleanupLayoutState(mTextView);
        assertFalse(mTextView.isLayoutRequested());
    }

    @UiThreadTest
    @Test
    public void testClearChildFocus() {
        mMockViewGroup.addView(mTextView);
        mMockViewGroup.requestChildFocus(mTextView, null);

        View focusedView = mMockViewGroup.getFocusedChild();
        assertSame(mTextView, focusedView);

        mMockViewGroup.clearChildFocus(mTextView);
        assertNull(mMockViewGroup.getFocusedChild());
    }

    @UiThreadTest
    @Test
    public void testClearDisappearingChildren() {
        Canvas canvas = new Canvas();
        MockViewGroup child = new MockViewGroup(mContext);
        child.setAnimation(new MockAnimation());
        mMockViewGroup.addView(child);
        assertEquals(1, mMockViewGroup.getChildCount());

        assertNotNull(child.getAnimation());
        mMockViewGroup.dispatchDraw(canvas);
        assertEquals(1, mMockViewGroup.drawChildCalledTime);

        child.setAnimation(new MockAnimation());
        mMockViewGroup.removeAllViewsInLayout();

        mMockViewGroup.drawChildCalledTime = 0;
        mMockViewGroup.dispatchDraw(canvas);
        assertEquals(1, mMockViewGroup.drawChildCalledTime);

        child.setAnimation(new MockAnimation());
        mMockViewGroup.clearDisappearingChildren();

        mMockViewGroup.drawChildCalledTime = 0;
        mMockViewGroup.dispatchDraw(canvas);
        assertEquals(0, mMockViewGroup.drawChildCalledTime);
    }

    @UiThreadTest
    @Test
    public void testClearFocus() {
        mMockViewGroup.addView(mMockTextView);
        mMockViewGroup.requestChildFocus(mMockTextView, null);
        mMockViewGroup.clearFocus();
        assertTrue(mMockTextView.isClearFocusCalled);
    }

    @UiThreadTest
    @Test
    public void testDetachAllViewsFromParent() {
        mMockViewGroup.addView(mTextView);
        assertEquals(1, mMockViewGroup.getChildCount());
        assertSame(mMockViewGroup, mTextView.getParent());
        mMockViewGroup.detachAllViewsFromParent();
        assertEquals(0, mMockViewGroup.getChildCount());
        assertNull(mTextView.getParent());
    }

    @UiThreadTest
    @Test
    public void testDetachViewFromParent() {
        mMockViewGroup.addView(mTextView);
        assertEquals(1, mMockViewGroup.getChildCount());

        mMockViewGroup.detachViewFromParent(0);

        assertEquals(0, mMockViewGroup.getChildCount());
        assertNull(mTextView.getParent());
    }

    @UiThreadTest
    @Test
    public void testDetachViewFromParentWithParamView() {
        mMockViewGroup.addView(mTextView);
        assertEquals(1, mMockViewGroup.getChildCount());
        assertSame(mMockViewGroup, mTextView.getParent());

        mMockViewGroup.detachViewFromParent(mTextView);

        assertEquals(0, mMockViewGroup.getChildCount());
        assertNull(mMockViewGroup.getParent());
    }

    @UiThreadTest
    @Test
    public void testDetachViewsFromParent() {
        TextView textView1 = new TextView(mContext);
        TextView textView2 = new TextView(mContext);
        TextView textView3 = new TextView(mContext);

        mMockViewGroup.addView(textView1);
        mMockViewGroup.addView(textView2);
        mMockViewGroup.addView(textView3);
        assertEquals(3, mMockViewGroup.getChildCount());

        mMockViewGroup.detachViewsFromParent(0, 2);

        assertEquals(1, mMockViewGroup.getChildCount());
        assertNull(textView1.getParent());
        assertNull(textView2.getParent());
    }

    @UiThreadTest
    @Test
    public void testDispatchDraw() {
        Canvas canvas = new Canvas();

        mMockViewGroup.draw(canvas);
        assertTrue(mMockViewGroup.isDispatchDrawCalled);
        assertSame(canvas, mMockViewGroup.canvas);
    }

    @UiThreadTest
    @Test
    public void testDispatchFreezeSelfOnly() {
        mMockViewGroup.setId(1);
        mMockViewGroup.setSaveEnabled(true);

        SparseArray container = new SparseArray();
        assertEquals(0, container.size());
        mMockViewGroup.dispatchFreezeSelfOnly(container);
        assertEquals(1, container.size());
    }

    @UiThreadTest
    @Test
    public void testDispatchKeyEvent() {
        KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER);
        assertFalse(mMockViewGroup.dispatchKeyEvent(event));

        mMockViewGroup.addView(mMockTextView);
        mMockViewGroup.requestChildFocus(mMockTextView, null);
        mMockTextView.layout(1, 1, 100, 100);

        assertTrue(mMockViewGroup.dispatchKeyEvent(event));
    }

    @UiThreadTest
    @Test
    public void testDispatchSaveInstanceState() {
        mMockViewGroup.setId(2);
        mMockViewGroup.setSaveEnabled(true);
        mMockTextView.setSaveEnabled(true);
        mMockTextView.setId(1);
        mMockViewGroup.addView(mMockTextView);

        SparseArray array = new SparseArray();
        mMockViewGroup.dispatchSaveInstanceState(array);

        assertTrue(array.size() > 0);
        assertNotNull(array.get(2));

        array = new SparseArray();
        mMockViewGroup.dispatchRestoreInstanceState(array);
        assertTrue(mMockTextView.isDispatchRestoreInstanceStateCalled);
    }

    @UiThreadTest
    @Test
    public void testDispatchSetPressed() {
        mMockViewGroup.addView(mMockTextView);

        mMockViewGroup.dispatchSetPressed(true);
        assertTrue(mMockTextView.isPressed());

        mMockViewGroup.dispatchSetPressed(false);
        assertFalse(mMockTextView.isPressed());
    }

    @UiThreadTest
    @Test
    public void testDispatchSetSelected() {
        mMockViewGroup.addView(mMockTextView);

        mMockViewGroup.dispatchSetSelected(true);
        assertTrue(mMockTextView.isSelected());

        mMockViewGroup.dispatchSetSelected(false);
        assertFalse(mMockTextView.isSelected());
    }

    @UiThreadTest
    @Test
    public void testDispatchThawSelfOnly() {
        mMockViewGroup.setId(1);
        SparseArray array = new SparseArray();
        array.put(1, BaseSavedState.EMPTY_STATE);

        mMockViewGroup.dispatchThawSelfOnly(array);
        assertTrue(mMockViewGroup.isOnRestoreInstanceStateCalled);
    }

    @UiThreadTest
    @Test
    public void testDispatchTouchEvent() {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        Display d = wm.getDefaultDisplay();
        d.getMetrics(metrics);
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;
        mMockViewGroup.layout(0, 0, screenWidth, screenHeight);
        mMockViewGroup.setLayoutParams(new ViewGroup.LayoutParams(screenWidth, screenHeight));

        mMotionEvent = null;
        mMockTextView.setOnTouchListener((View v, MotionEvent event) -> {
            mMotionEvent = event;
            return true;
        });

        mMockTextView.setVisibility(View.VISIBLE);
        mMockTextView.setEnabled(true);

        mMockViewGroup.addView(mMockTextView, new LayoutParams(screenWidth, screenHeight));

        mMockViewGroup.requestDisallowInterceptTouchEvent(true);
        MotionEvent me = MotionEvent.obtain(SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN,
                screenWidth / 2, screenHeight / 2, 0);

        assertFalse(mMockViewGroup.dispatchTouchEvent(me));
        assertNull(mMotionEvent);

        mMockTextView.layout(0, 0, screenWidth, screenHeight);
        assertTrue(mMockViewGroup.dispatchTouchEvent(me));
        assertSame(me, mMotionEvent);
    }

    @UiThreadTest
    @Test
    public void testDispatchTrackballEvent() {
        MotionEvent me = MotionEvent.obtain(SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, 100, 100,
                0);
        assertFalse(mMockViewGroup.dispatchTrackballEvent(me));

        mMockViewGroup.addView(mMockTextView);
        mMockTextView.layout(1, 1, 100, 100);
        mMockViewGroup.requestChildFocus(mMockTextView, null);
        assertTrue(mMockViewGroup.dispatchTrackballEvent(me));
    }

    @UiThreadTest
    @Test
    public void testDispatchUnhandledMove() {
        assertFalse(mMockViewGroup.dispatchUnhandledMove(mMockTextView, View.FOCUS_DOWN));

        mMockViewGroup.addView(mMockTextView);
        mMockTextView.layout(1, 1, 100, 100);
        mMockViewGroup.requestChildFocus(mMockTextView, null);
        assertTrue(mMockViewGroup.dispatchUnhandledMove(mMockTextView, View.FOCUS_DOWN));
    }

    @UiThreadTest
    @Test
    public void testDispatchWindowFocusChanged() {
        mMockViewGroup.addView(mMockTextView);
        mMockTextView.setPressed(true);
        assertTrue(mMockTextView.isPressed());

        mMockViewGroup.dispatchWindowFocusChanged(false);
        assertFalse(mMockTextView.isPressed());
    }

    @UiThreadTest
    @Test
    public void testDispatchWindowVisibilityChanged() {
        int expected = 10;

        mMockViewGroup.addView(mMockTextView);
        mMockViewGroup.dispatchWindowVisibilityChanged(expected);
        assertEquals(expected, mMockTextView.visibility);
    }

    @UiThreadTest
    @Test
    public void testDrawableStateChanged() {
        mMockTextView.setDuplicateParentStateEnabled(true);

        mMockViewGroup.addView(mMockTextView);
        mMockViewGroup.setAddStatesFromChildren(false);
        mMockViewGroup.drawableStateChanged();
        assertTrue(mMockTextView.mIsRefreshDrawableStateCalled);
    }

    @UiThreadTest
    @Test
    public void testDrawChild() {
        mMockViewGroup.addView(mMockTextView);

        MockCanvas canvas = new MockCanvas();
        mMockTextView.setBackgroundDrawable(new BitmapDrawable(Bitmap.createBitmap(100, 100,
                Config.ALPHA_8)));
        assertFalse(mMockViewGroup.drawChild(canvas, mMockTextView, 100));
        // test whether child's draw method is called.
        assertTrue(mMockTextView.isDrawCalled);
    }

    @UiThreadTest
    @Test
    public void testFindFocus() {
        assertNull(mMockViewGroup.findFocus());
        mMockViewGroup.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        mMockViewGroup.setFocusable(true);
        mMockViewGroup.setVisibility(View.VISIBLE);
        mMockViewGroup.setFocusableInTouchMode(true);
        assertTrue(mMockViewGroup.requestFocus(1, new Rect()));

        assertSame(mMockViewGroup, mMockViewGroup.findFocus());
    }

    @UiThreadTest
    @Test
    public void testFitSystemWindows() {
        Rect rect = new Rect(1, 1, 100, 100);
        assertFalse(mMockViewGroup.fitSystemWindows(rect));

        mMockViewGroup = new MockViewGroup(mContext, null, 0);
        MockView mv = new MockView(mContext);
        mMockViewGroup.addView(mv);
        assertTrue(mMockViewGroup.fitSystemWindows(rect));
    }

    static class MockView extends ViewGroup {

        public int mWidthMeasureSpec;
        public int mHeightMeasureSpec;

        public MockView(Context context) {
            super(context);
        }

        @Override
        public void onLayout(boolean changed, int l, int t, int r, int b) {
        }

        @Override
        public boolean fitSystemWindows(Rect insets) {
            return true;
        }

        @Override
        public void onMeasure(int widthMeasureSpec,
                int heightMeasureSpec) {
            mWidthMeasureSpec = widthMeasureSpec;
            mHeightMeasureSpec = heightMeasureSpec;
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    @UiThreadTest
    @Test
    public void testFocusableViewAvailable() {
        MockView child = new MockView(mContext);
        mMockViewGroup.addView(child);

        child.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        child.focusableViewAvailable(mMockViewGroup);

        assertTrue(mMockViewGroup.isFocusableViewAvailable);
    }

    @UiThreadTest
    @Test
    public void testFocusSearch() {
        MockView child = new MockView(mContext);
        mMockViewGroup.addView(child);
        child.addView(mMockTextView);
        assertSame(mMockTextView, child.focusSearch(mMockTextView, 1));
    }

    @UiThreadTest
    @Test
    public void testGatherTransparentRegion() {
        Region region = new Region();
        mMockTextView.setAnimation(new AlphaAnimation(mContext, null));
        mMockTextView.setVisibility(100);
        mMockViewGroup.addView(mMockTextView);
        assertEquals(1, mMockViewGroup.getChildCount());

        assertTrue(mMockViewGroup.gatherTransparentRegion(region));
        assertTrue(mMockViewGroup.gatherTransparentRegion(null));
    }

    @UiThreadTest
    @Test
    public void testGenerateDefaultLayoutParams(){
        LayoutParams lp = mMockViewGroup.generateDefaultLayoutParams();

        assertEquals(LayoutParams.WRAP_CONTENT, lp.width);
        assertEquals(LayoutParams.WRAP_CONTENT, lp.height);
    }

    @UiThreadTest
    @Test
    public void testGenerateLayoutParamsWithParaAttributeSet() throws Exception {
        XmlResourceParser set = mContext.getResources().getLayout(
                android.view.cts.R.layout.abslistview_layout);
        XmlUtils.beginDocument(set, "ViewGroup_Layout");
        LayoutParams lp = mMockViewGroup.generateLayoutParams(set);
        assertNotNull(lp);
        assertEquals(25, lp.height);
        assertEquals(25, lp.width);
    }

    @UiThreadTest
    @Test
    public void testGenerateLayoutParams() {
        LayoutParams p = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.MATCH_PARENT);
        LayoutParams generatedParams = mMockViewGroup.generateLayoutParams(p);
        assertEquals(generatedParams.getClass(), p.getClass());
        assertEquals(p.width, generatedParams.width);
        assertEquals(p.height, generatedParams.height);
    }

    @UiThreadTest
    @Test
    public void testGetChildDrawingOrder() {
        assertEquals(1, mMockViewGroup.getChildDrawingOrder(0, 1));
        assertEquals(2, mMockViewGroup.getChildDrawingOrder(0, 2));
    }

    @Test
    public void testGetChildMeasureSpec() {
        int spec = 1;
        int padding = 1;
        int childDimension = 1;
        assertEquals(MeasureSpec.makeMeasureSpec(childDimension, MeasureSpec.EXACTLY),
                ViewGroup.getChildMeasureSpec(spec, padding, childDimension));
        spec = 4;
        padding = 6;
        childDimension = 9;
        assertEquals(MeasureSpec.makeMeasureSpec(childDimension, MeasureSpec.EXACTLY),
                ViewGroup.getChildMeasureSpec(spec, padding, childDimension));
    }

    @UiThreadTest
    @Test
    public void testGetChildStaticTransformation() {
        assertFalse(mMockViewGroup.getChildStaticTransformation(null, null));
    }

    @UiThreadTest
    @Test
    public void testGetChildVisibleRect() {
        mMockTextView.layout(1, 1, 100, 100);
        Rect rect = new Rect(1, 1, 50, 50);
        Point p = new Point();
        assertFalse(mMockViewGroup.getChildVisibleRect(mMockTextView, rect, p));

        mMockTextView.layout(0, 0, 0, 0);
        mMockViewGroup.layout(20, 20, 60, 60);
        rect = new Rect(10, 10, 40, 40);
        p = new Point();
        assertTrue(mMockViewGroup.getChildVisibleRect(mMockTextView, rect, p));
    }

    @UiThreadTest
    @Test
    public void testGetDescendantFocusability() {
        final int FLAG_MASK_FOCUSABILITY = 0x60000;
        assertFalse((mMockViewGroup.getDescendantFocusability() & FLAG_MASK_FOCUSABILITY) == 0);

        mMockViewGroup.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        assertFalse((mMockViewGroup.getDescendantFocusability() & FLAG_MASK_FOCUSABILITY) == 0);
    }

    @UiThreadTest
    @Test
    public void testGetLayoutAnimation() {
        assertNull(mMockViewGroup.getLayoutAnimation());
        RotateAnimation animation = new RotateAnimation(0.1f, 0.1f);
        LayoutAnimationController la = new LayoutAnimationController(animation);
        mMockViewGroup.setLayoutAnimation(la);
        assertTrue(mMockViewGroup.canAnimate());
        assertSame(la, mMockViewGroup.getLayoutAnimation());
    }

    @UiThreadTest
    @Test
    public void testGetLayoutAnimationListener() {
        assertNull(mMockViewGroup.getLayoutAnimationListener());

        AnimationListener al = new AnimationListener() {
            @Override
            public void onAnimationEnd(Animation animation) {
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }

            @Override
            public void onAnimationStart(Animation animation) {
            }
        };
        mMockViewGroup.setLayoutAnimationListener(al);
        assertSame(al, mMockViewGroup.getLayoutAnimationListener());
    }

    @UiThreadTest
    @Test
    public void testGetPersistentDrawingCache() {
        final int mPersistentDrawingCache1 = 2;
        final int mPersistentDrawingCache2 = 3;
        assertEquals(mPersistentDrawingCache1, mMockViewGroup.getPersistentDrawingCache());

        mMockViewGroup.setPersistentDrawingCache(mPersistentDrawingCache2);
        assertEquals(mPersistentDrawingCache2, mMockViewGroup.getPersistentDrawingCache());
    }

    @UiThreadTest
    @Test
    public void testHasFocus() {
        assertFalse(mMockViewGroup.hasFocus());

        mMockViewGroup.addView(mTextView);
        mMockViewGroup.requestChildFocus(mTextView, null);

        assertTrue(mMockViewGroup.hasFocus());
    }

    @UiThreadTest
    @Test
    public void testHasFocusable() {
        assertFalse(mMockViewGroup.hasFocusable());

        mMockViewGroup.setVisibility(View.VISIBLE);
        mMockViewGroup.setFocusable(true);
        assertTrue(mMockViewGroup.hasFocusable());
    }

    @UiThreadTest
    @Test
    public void testIndexOfChild() {
        assertEquals(-1, mMockViewGroup.indexOfChild(mTextView));

        mMockViewGroup.addView(mTextView);
        assertEquals(0, mMockViewGroup.indexOfChild(mTextView));
    }

    @LargeTest
    @Test
    public void testInvalidateChild() {
        ViewGroupInvalidateChildCtsActivity.setResult(this);

        Context context = InstrumentationRegistry.getTargetContext();
        Intent intent = new Intent(context, ViewGroupInvalidateChildCtsActivity.class);
        intent.setAction(ViewGroupInvalidateChildCtsActivity.ACTION_INVALIDATE_CHILD);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);

        waitForResult();
        assertEquals(CTSResult.RESULT_OK, mResultCode);
    }

    @Test
    public void testOnDescendantInvalidated() throws Throwable {
        Activity activity = null;
        try {
            activity = mCtsActivityRule.launchActivity(new Intent());

            mCtsActivityRule.runOnUiThread(() -> {
                View child = mTextView;
                MockViewGroup parent = mMockViewGroup;
                MockViewGroup grandParent = new MockViewGroup(mContext);
                parent.addView(child);
                grandParent.addView(parent);
                mCtsActivityRule.getActivity().setContentView(grandParent);

                parent.isOnDescendantInvalidatedCalled = false;
                grandParent.isOnDescendantInvalidatedCalled = false;

                parent.invalidateChild(child, new Rect(0, 0, 1, 1));

                assertTrue(parent.isOnDescendantInvalidatedCalled);
                assertTrue(grandParent.isOnDescendantInvalidatedCalled);

                parent.isOnDescendantInvalidatedCalled = false;
                grandParent.isOnDescendantInvalidatedCalled = false;

                grandParent.invalidateChild(child, new Rect(0, 0, 1, 1));

                assertFalse(parent.isOnDescendantInvalidatedCalled);
                assertTrue(grandParent.isOnDescendantInvalidatedCalled);
            });
        } finally {
            if (activity != null) {
                activity.finish();
            }
        }
    }

    private void waitForResult() {
        synchronized (mSync) {
            while(!mSync.mHasNotify) {
                try {
                    mSync.wait();
                } catch (InterruptedException e) {
                }
            }
        }
    }

    @UiThreadTest
    @Test
    public void testIsAlwaysDrawnWithCacheEnabled() {
        assertTrue(mMockViewGroup.isAlwaysDrawnWithCacheEnabled());

        mMockViewGroup.setAlwaysDrawnWithCacheEnabled(false);
        assertFalse(mMockViewGroup.isAlwaysDrawnWithCacheEnabled());
        mMockViewGroup.setAlwaysDrawnWithCacheEnabled(true);
        assertTrue(mMockViewGroup.isAlwaysDrawnWithCacheEnabled());
    }

    @UiThreadTest
    @Test
    public void testIsAnimationCacheEnabled() {
        assertTrue(mMockViewGroup.isAnimationCacheEnabled());

        mMockViewGroup.setAnimationCacheEnabled(false);
        assertFalse(mMockViewGroup.isAnimationCacheEnabled());
        mMockViewGroup.setAnimationCacheEnabled(true);
        assertTrue(mMockViewGroup.isAnimationCacheEnabled());
    }

    @UiThreadTest
    @Test
    public void testIsChildrenDrawnWithCacheEnabled() {
        assertFalse(mMockViewGroup.isChildrenDrawnWithCacheEnabled());

        mMockViewGroup.setChildrenDrawnWithCacheEnabled(true);
        assertTrue(mMockViewGroup.isChildrenDrawnWithCacheEnabled());
    }

    @UiThreadTest
    @Test
    public void testMeasureChild() {
        final int width = 100;
        final int height = 200;
        MockView child = new MockView(mContext);
        child.setLayoutParams(new LayoutParams(width, height));
        child.forceLayout();
        mMockViewGroup.addView(child);

        final int parentWidthMeasureSpec = 1;
        final int parentHeightMeasureSpec = 2;
        mMockViewGroup.measureChild(child, parentWidthMeasureSpec, parentHeightMeasureSpec);
        assertEquals(ViewGroup.getChildMeasureSpec(parentWidthMeasureSpec, 0, width),
                child.mWidthMeasureSpec);
        assertEquals(ViewGroup.getChildMeasureSpec(parentHeightMeasureSpec, 0, height),
                child.mHeightMeasureSpec);
    }

    @UiThreadTest
    @Test
    public void testMeasureChildren() {
        final int widthMeasureSpec = 100;
        final int heightMeasureSpec = 200;
        MockTextView textView1 = new MockTextView(mContext);

        mMockViewGroup.addView(textView1);
        mMockViewGroup.measureChildCalledTime = 0;
        mMockViewGroup.measureChildren(widthMeasureSpec, heightMeasureSpec);
        assertEquals(1, mMockViewGroup.measureChildCalledTime);

        MockTextView textView2 = new MockTextView(mContext);
        textView2.setVisibility(View.GONE);
        mMockViewGroup.addView(textView2);

        mMockViewGroup.measureChildCalledTime = 0;
        mMockViewGroup.measureChildren(widthMeasureSpec, heightMeasureSpec);
        assertEquals(1, mMockViewGroup.measureChildCalledTime);
    }

    @UiThreadTest
    @Test
    public void testMeasureChildWithMargins() {
        final int width = 10;
        final int height = 20;
        final int parentWidthMeasureSpec = 1;
        final int widthUsed = 2;
        final int parentHeightMeasureSpec = 3;
        final int heightUsed = 4;
        MockView child = new MockView(mContext);

        mMockViewGroup.addView(child);
        child.setLayoutParams(new ViewGroup.LayoutParams(width, height));
        try {
            mMockViewGroup.measureChildWithMargins(child, parentWidthMeasureSpec, widthUsed,
                    parentHeightMeasureSpec, heightUsed);
            fail("measureChildWithMargins should throw out class cast exception");
        } catch (RuntimeException e) {
        }
        child.setLayoutParams(new ViewGroup.MarginLayoutParams(width, height));

        mMockViewGroup.measureChildWithMargins(child, parentWidthMeasureSpec, widthUsed,
                parentHeightMeasureSpec, heightUsed);
        assertEquals(ViewGroup.getChildMeasureSpec(parentWidthMeasureSpec, parentHeightMeasureSpec,
                width), child.mWidthMeasureSpec);
        assertEquals(ViewGroup.getChildMeasureSpec(widthUsed, heightUsed, height),
                child.mHeightMeasureSpec);
    }

    @UiThreadTest
    @Test
    public void testOffsetDescendantRectToMyCoords() {
        try {
            mMockViewGroup.offsetDescendantRectToMyCoords(mMockTextView, new Rect());
            fail("offsetDescendantRectToMyCoords should throw out "
                    + "IllegalArgumentException");
        } catch (RuntimeException e) {
            // expected
        }
        mMockViewGroup.addView(mMockTextView);
        mMockTextView.layout(1, 2, 3, 4);
        Rect rect = new Rect();
        mMockViewGroup.offsetDescendantRectToMyCoords(mMockTextView, rect);
        assertEquals(2, rect.bottom);
        assertEquals(2, rect.top);
        assertEquals(1, rect.left);
        assertEquals(1, rect.right);
    }

    @UiThreadTest
    @Test
    public void testOffsetRectIntoDescendantCoords() {
        mMockViewGroup.layout(10, 20, 30, 40);

        try {
            mMockViewGroup.offsetRectIntoDescendantCoords(mMockTextView, new Rect());
            fail("offsetRectIntoDescendantCoords should throw out "
                    + "IllegalArgumentException");
        } catch (RuntimeException e) {
            // expected
        }
        mMockTextView.layout(1, 2, 3, 4);
        mMockViewGroup.addView(mMockTextView);

        Rect rect = new Rect(5, 6, 7, 8);
        mMockViewGroup.offsetRectIntoDescendantCoords(mMockTextView, rect);
        assertEquals(6, rect.bottom);
        assertEquals(4, rect.top);
        assertEquals(4, rect.left);
        assertEquals(6, rect.right);
    }

    @UiThreadTest
    @Test
    public void testOnAnimationEnd() {
        // this function is a call back function it should be tested in ViewGroup#drawChild.
        MockViewGroup parent = new MockViewGroup(mContext);
        MockViewGroup child = new MockViewGroup(mContext);
        child.setAnimation(new MockAnimation());
        // this call will make mPrivateFlags |= ANIMATION_STARTED;
        child.onAnimationStart();
        parent.addView(child);

        MockCanvas canvas = new MockCanvas();
        assertFalse(parent.drawChild(canvas, child, 100));
        assertTrue(child.isOnAnimationEndCalled);
    }

    private class MockAnimation extends Animation {
        public MockAnimation() {
            super();
        }

        public MockAnimation(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        @Override
        public boolean getTransformation(long currentTime, Transformation outTransformation) {
           super.getTransformation(currentTime, outTransformation);
           return false;
        }
    }

    @UiThreadTest
    @Test
    public void testOnAnimationStart() {
        // This is a call back method. It should be tested in ViewGroup#drawChild.
        MockViewGroup parent = new MockViewGroup(mContext);
        MockViewGroup child = new MockViewGroup(mContext);

        parent.addView(child);

        MockCanvas canvas = new MockCanvas();
        try {
            assertFalse(parent.drawChild(canvas, child, 100));
            assertFalse(child.isOnAnimationStartCalled);
        } catch (Exception e) {
            // expected
        }

        child.setAnimation(new MockAnimation());
        assertFalse(parent.drawChild(canvas, child, 100));
        assertTrue(child.isOnAnimationStartCalled);
    }

    @UiThreadTest
    @Test
    public void testOnCreateDrawableState() {
        // Call back function. Called in View#getDrawableState()
        int[] data = mMockViewGroup.getDrawableState();
        assertTrue(mMockViewGroup.isOnCreateDrawableStateCalled);
        assertEquals(1, data.length);
    }

    @UiThreadTest
    @Test
    public void testOnInterceptTouchEvent() {
        MotionEvent me = MotionEvent.obtain(SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, 100, 100, 0);

        assertFalse(mMockViewGroup.dispatchTouchEvent(me));
        assertTrue(mMockViewGroup.isOnInterceptTouchEventCalled);
    }

    @UiThreadTest
    @Test
    public void testOnLayout() {
        final int left = 1;
        final int top = 2;
        final int right = 100;
        final int bottom = 200;
        mMockViewGroup.layout(left, top, right, bottom);
        assertEquals(left, mMockViewGroup.left);
        assertEquals(top, mMockViewGroup.top);
        assertEquals(right, mMockViewGroup.right);
        assertEquals(bottom, mMockViewGroup.bottom);
    }

    @UiThreadTest
    @Test
    public void testOnRequestFocusInDescendants() {
        mMockViewGroup.requestFocus(View.FOCUS_DOWN, new Rect());
        assertTrue(mMockViewGroup.isOnRequestFocusInDescendantsCalled);
    }

    @UiThreadTest
    @Test
    public void testRemoveAllViews() {
        assertEquals(0, mMockViewGroup.getChildCount());

        mMockViewGroup.addView(mMockTextView);
        assertEquals(1, mMockViewGroup.getChildCount());

        mMockViewGroup.removeAllViews();
        assertEquals(0, mMockViewGroup.getChildCount());
        assertNull(mMockTextView.getParent());
    }

    @UiThreadTest
    @Test
    public void testRemoveAllViewsInLayout() {
        MockViewGroup parent = new MockViewGroup(mContext);
        MockViewGroup child = new MockViewGroup(mContext);

        assertEquals(0, parent.getChildCount());

        child.addView(mMockTextView);
        parent.addView(child);
        assertEquals(1, parent.getChildCount());

        parent.removeAllViewsInLayout();
        assertEquals(0, parent.getChildCount());
        assertEquals(1, child.getChildCount());
        assertNull(child.getParent());
        assertSame(child, mMockTextView.getParent());
    }

    @UiThreadTest
    @Test
    public void testRemoveDetachedView() {
        MockViewGroup parent = new MockViewGroup(mContext);
        MockViewGroup child1 = new MockViewGroup(mContext);
        MockViewGroup child2 = new MockViewGroup(mContext);
        ViewGroup.OnHierarchyChangeListener listener =
                mock(ViewGroup.OnHierarchyChangeListener.class);
        parent.setOnHierarchyChangeListener(listener);
        parent.addView(child1);
        parent.addView(child2);

        parent.removeDetachedView(child1, false);

        InOrder inOrder = inOrder(listener);
        inOrder.verify(listener, times(1)).onChildViewAdded(parent, child1);
        inOrder.verify(listener, times(1)).onChildViewAdded(parent, child2);
        inOrder.verify(listener, times(1)).onChildViewRemoved(parent, child1);
    }

    @UiThreadTest
    @Test
    public void testRemoveView() {
        MockViewGroup parent = new MockViewGroup(mContext);
        MockViewGroup child = new MockViewGroup(mContext);

        assertEquals(0, parent.getChildCount());

        parent.addView(child);
        assertEquals(1, parent.getChildCount());

        parent.removeView(child);
        assertEquals(0, parent.getChildCount());
        assertNull(child.getParent());
        assertTrue(parent.isOnViewRemovedCalled);
    }

    @UiThreadTest
    @Test
    public void testRemoveViewAt() {
        MockViewGroup parent = new MockViewGroup(mContext);
        MockViewGroup child = new MockViewGroup(mContext);

        assertEquals(0, parent.getChildCount());

        parent.addView(child);
        assertEquals(1, parent.getChildCount());

        try {
            parent.removeViewAt(2);
            fail("should throw out null pointer exception");
        } catch (RuntimeException e) {
            // expected
        }
        assertEquals(1, parent.getChildCount());

        parent.removeViewAt(0);
        assertEquals(0, parent.getChildCount());
        assertNull(child.getParent());
        assertTrue(parent.isOnViewRemovedCalled);
    }

    @UiThreadTest
    @Test
    public void testRemoveViewInLayout() {
        MockViewGroup parent = new MockViewGroup(mContext);
        MockViewGroup child = new MockViewGroup(mContext);

        assertEquals(0, parent.getChildCount());

        parent.addView(child);
        assertEquals(1, parent.getChildCount());

        parent.removeViewInLayout(child);
        assertEquals(0, parent.getChildCount());
        assertNull(child.getParent());
        assertTrue(parent.isOnViewRemovedCalled);
    }

    @UiThreadTest
    @Test
    public void testRemoveViews() {
        MockViewGroup parent = new MockViewGroup(mContext);
        MockViewGroup child1 = new MockViewGroup(mContext);
        MockViewGroup child2 = new MockViewGroup(mContext);

        assertEquals(0, parent.getChildCount());
        parent.addView(child1);
        parent.addView(child2);
        assertEquals(2, parent.getChildCount());

        try {
            parent.removeViews(-1, 1); // negative begin
            fail("should fail with IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {}

        try {
            parent.removeViews(0, -1); // negative count
            fail("should fail with IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {}

        try {
            parent.removeViews(1, 2); // past end
            fail("should fail with IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {}
        assertEquals(2, parent.getChildCount()); // child list unmodified

        parent.removeViews(0, 1);
        assertEquals(1, parent.getChildCount());
        assertNull(child1.getParent());

        parent.removeViews(0, 1);
        assertEquals(0, parent.getChildCount());
        assertNull(child2.getParent());
        assertTrue(parent.isOnViewRemovedCalled);
    }

    @UiThreadTest
    @Test
    public void testRemoveViewsInLayout() {
        MockViewGroup parent = new MockViewGroup(mContext);
        MockViewGroup child1 = new MockViewGroup(mContext);
        MockViewGroup child2 = new MockViewGroup(mContext);

        assertEquals(0, parent.getChildCount());
        parent.addView(child1);
        parent.addView(child2);
        assertEquals(2, parent.getChildCount());

        try {
            parent.removeViewsInLayout(-1, 1); // negative begin
            fail("should fail with IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {}

        try {
            parent.removeViewsInLayout(0, -1); // negative count
            fail("should fail with IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {}

        try {
            parent.removeViewsInLayout(1, 2); // past end
            fail("should fail with IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {}
        assertEquals(2, parent.getChildCount()); // child list unmodified

        parent.removeViewsInLayout(0, 1);
        assertEquals(1, parent.getChildCount());
        assertNull(child1.getParent());

        parent.removeViewsInLayout(0, 1);
        assertEquals(0, parent.getChildCount());
        assertNull(child2.getParent());
        assertTrue(parent.isOnViewRemovedCalled);
    }

    @UiThreadTest
    @Test
    public void testRequestChildFocus() {
        mMockViewGroup.addView(mTextView);
        mMockViewGroup.requestChildFocus(mTextView, null);

        assertNotNull(mMockViewGroup.getFocusedChild());

        mMockViewGroup.clearChildFocus(mTextView);
        assertNull(mMockViewGroup.getFocusedChild());
    }

    @UiThreadTest
    @Test
    public void testRequestChildRectangleOnScreen() {
        assertFalse(mMockViewGroup.requestChildRectangleOnScreen(null, null, false));
    }

    @UiThreadTest
    @Test
    public void testRequestDisallowInterceptTouchEvent() {
        MockView child = new MockView(mContext);

        mMockViewGroup.addView(child);
        child.requestDisallowInterceptTouchEvent(true);
        child.requestDisallowInterceptTouchEvent(false);
        assertTrue(mMockViewGroup.isRequestDisallowInterceptTouchEventCalled);
    }

    @UiThreadTest
    @Test
    public void testRequestFocus() {
        mMockViewGroup.requestFocus(View.FOCUS_DOWN, new Rect());
        assertTrue(mMockViewGroup.isOnRequestFocusInDescendantsCalled);
    }

    private class TestClusterHier {
        public MockViewGroup top = new MockViewGroup(mContext);
        public MockViewGroup cluster1 = new MockViewGroup(mContext);
        public Button c1view1 = new Button(mContext);
        public Button c1view2 = new Button(mContext);
        public MockViewGroup cluster2 = new MockViewGroup(mContext);
        public MockViewGroup nestedGroup = new MockViewGroup(mContext);
        public Button c2view1 = new Button(mContext);
        public Button c2view2 = new Button(mContext);
        TestClusterHier() {
            this(true);
        }
        TestClusterHier(boolean inTouchMode) {
            for (Button bt : new Button[]{c1view1, c1view2, c2view1, c2view2}) {
                // Otherwise this test won't work during suite-run.
                bt.setFocusableInTouchMode(inTouchMode);
            }
            for (MockViewGroup mvg : new MockViewGroup[]{top, cluster1, cluster2, nestedGroup}) {
                mvg.returnActualFocusSearchResult = true;
            }
            top.setIsRootNamespace(true);
            cluster1.setKeyboardNavigationCluster(true);
            cluster2.setKeyboardNavigationCluster(true);
            cluster1.addView(c1view1);
            cluster1.addView(c1view2);
            cluster2.addView(c2view1);
            nestedGroup.addView(c2view2);
            cluster2.addView(nestedGroup);
            top.addView(cluster1);
            top.addView(cluster2);
        }
    }

    @UiThreadTest
    @Test
    public void testRestoreFocusInCluster() {
        TestClusterHier h = new TestClusterHier();
        h.cluster1.restoreFocusInCluster(View.FOCUS_DOWN);
        assertSame(h.c1view1, h.top.findFocus());

        h.cluster2.restoreFocusInCluster(View.FOCUS_DOWN);
        assertSame(h.c2view1, h.top.findFocus());

        h.c2view2.setFocusedInCluster();
        h.cluster2.restoreFocusInCluster(View.FOCUS_DOWN);
        assertSame(h.c2view2, h.top.findFocus());
        h.c2view1.setFocusedInCluster();
        h.cluster2.restoreFocusInCluster(View.FOCUS_DOWN);
        assertSame(h.c2view1, h.top.findFocus());

        h.c1view2.setFocusedInCluster();
        h.cluster1.restoreFocusInCluster(View.FOCUS_DOWN);
        assertSame(h.c1view2, h.top.findFocus());

        h = new TestClusterHier();
        h.cluster1.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        h.cluster1.restoreFocusInCluster(View.FOCUS_DOWN);
        assertNull(h.top.findFocus());

        h.c2view1.setVisibility(View.INVISIBLE);
        h.cluster2.restoreFocusInCluster(View.FOCUS_DOWN);
        assertSame(h.c2view2, h.top.findFocus());

        // Nested clusters should be ignored.
        h = new TestClusterHier();
        h.c1view1.setFocusedInCluster();
        h.nestedGroup.setKeyboardNavigationCluster(true);
        h.c2view2.setFocusedInCluster();
        h.cluster2.restoreFocusInCluster(View.FOCUS_DOWN);
        assertSame(h.c2view2, h.top.findFocus());
    }

    @UiThreadTest
    @Test
    public void testDefaultCluster() {
        TestClusterHier h = new TestClusterHier();
        h.cluster2.setKeyboardNavigationCluster(false);
        assertTrue(h.top.restoreFocusNotInCluster());
        assertSame(h.c2view1, h.top.findFocus());

        // Check saves state within non-cluster
        h = new TestClusterHier();
        h.cluster2.setKeyboardNavigationCluster(false);
        h.c2view2.setFocusedInCluster();
        assertTrue(h.top.restoreFocusNotInCluster());
        assertSame(h.c2view2, h.top.findFocus());

        // Check that focusable view groups have descendantFocusability honored.
        h = new TestClusterHier();
        h.cluster2.setKeyboardNavigationCluster(false);
        h.cluster2.setFocusableInTouchMode(true);
        h.cluster2.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        assertTrue(h.top.restoreFocusNotInCluster());
        assertSame(h.c2view1, h.top.findFocus());
        h = new TestClusterHier();
        h.cluster2.setKeyboardNavigationCluster(false);
        h.cluster2.setFocusableInTouchMode(true);
        h.cluster2.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        assertTrue(h.top.restoreFocusNotInCluster());
        assertSame(h.cluster2, h.top.findFocus());

        // Check that we return false if nothing out-of-cluster is focusable
        // (also tests FOCUS_BLOCK_DESCENDANTS)
        h = new TestClusterHier();
        h.cluster2.setKeyboardNavigationCluster(false);
        h.cluster2.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        assertFalse(h.top.restoreFocusNotInCluster());
        assertNull(h.top.findFocus());
    }

    @UiThreadTest
    @Test
    public void testFocusInClusterRemovals() {
        // Removing focused-in-cluster view from its parent in various ways.
        TestClusterHier h = new TestClusterHier();
        h.c1view1.setFocusedInCluster();
        h.cluster1.removeView(h.c1view1);
        h.cluster1.restoreFocusInCluster(View.FOCUS_DOWN);
        assertSame(h.c1view2, h.cluster1.findFocus());

        h = new TestClusterHier();
        h.c1view1.setFocusedInCluster();
        h.cluster1.removeViews(0, 1);
        h.cluster1.restoreFocusInCluster(View.FOCUS_DOWN);
        assertSame(h.c1view2, h.cluster1.findFocus());

        h = new TestClusterHier();
        h.c2view1.setFocusedInCluster();
        h.cluster2.removeAllViewsInLayout();
        h.cluster2.restoreFocusInCluster(View.FOCUS_DOWN);
        assertNull(h.cluster2.findFocus());

        h = new TestClusterHier();
        h.c1view1.setFocusedInCluster();
        h.cluster1.detachViewFromParent(h.c1view1);
        h.cluster1.attachViewToParent(h.c1view1, 1, null);
        h.cluster1.restoreFocusInCluster(View.FOCUS_DOWN);
        assertSame(h.c1view1, h.cluster1.findFocus());

        h = new TestClusterHier();
        h.c1view1.setFocusedInCluster();
        h.cluster1.detachViewFromParent(h.c1view1);
        h.cluster1.removeDetachedView(h.c1view1, false);
        h.cluster1.restoreFocusInCluster(View.FOCUS_DOWN);
        assertSame(h.c1view2, h.cluster1.findFocus());
    }

    @UiThreadTest
    @Test
    public void testFocusInClusterFocusableChanges() {
        TestClusterHier h = new TestClusterHier();
        h.cluster1.setKeyboardNavigationCluster(false);
        h.c1view2.setFocusedInCluster();
        h.c2view1.requestFocus();
        assertSame(h.top.findFocus(), h.c2view1);
        assertTrue(h.top.restoreFocusNotInCluster());
        assertSame(h.top.findFocus(), h.c1view2);
        h.c1view1.setFocusable(false);
        // making it invisible should clear focusNotInCluster chain
        h.c1view2.setVisibility(View.INVISIBLE);
        assertFalse(h.top.restoreFocusNotInCluster());
        h.c1view2.setVisibility(View.VISIBLE);
        h.c1view2.requestFocus();
        h.c1view2.setFocusedInCluster();
        h.c2view1.setFocusable(false);
        h.c2view2.setFocusable(false);
        assertFalse(h.cluster2.restoreFocusInCluster(View.FOCUS_DOWN));
    }

    @UiThreadTest
    @Test
    public void testRestoreDefaultFocus() {
        TestClusterHier h = new TestClusterHier();
        h.c1view2.setFocusedByDefault(true);
        h.top.restoreDefaultFocus();
        assertSame(h.c1view2, h.top.findFocus());

        h.c1view2.setFocusedByDefault(false);
        h.top.restoreDefaultFocus();
        assertSame(h.c1view1, h.top.findFocus());

        // default focus favors higher-up views
        h.c1view2.setFocusedByDefault(true);
        h.cluster1.setFocusedByDefault(true);
        h.top.restoreDefaultFocus();
        assertSame(h.c1view2, h.top.findFocus());
        h.c2view1.setFocusedByDefault(true);
        h.top.restoreDefaultFocus();
        assertSame(h.c1view2, h.top.findFocus());
        h.cluster2.setFocusedByDefault(true);
        h.cluster1.setFocusedByDefault(false);
        h.top.restoreDefaultFocus();
        assertSame(h.c2view1, h.top.findFocus());

        // removing default receivers should resolve to an existing default
        h = new TestClusterHier();
        h.c1view2.setFocusedByDefault(true);
        h.cluster1.setFocusedByDefault(true);
        h.c2view2.setFocusedByDefault(true);
        h.top.restoreDefaultFocus();
        assertSame(h.c1view2, h.top.findFocus());
        h.c1view2.setFocusedByDefault(false);
        h.cluster1.setFocusedByDefault(false);
        // only 1 focused-by-default view left, but its in a different branch. Should still pull
        // default focus.
        h.top.restoreDefaultFocus();
        assertSame(h.c2view2, h.top.findFocus());
    }

    @UiThreadTest
    @Test
    public void testDefaultFocusViewRemoved() {
        // Removing default-focus view from its parent in various ways.
        TestClusterHier h = new TestClusterHier();
        h.c1view1.setFocusedByDefault(true);
        h.cluster1.removeView(h.c1view1);
        h.cluster1.restoreDefaultFocus();
        assertSame(h.c1view2, h.cluster1.findFocus());

        h = new TestClusterHier();
        h.c1view1.setFocusedByDefault(true);
        h.cluster1.removeViews(0, 1);
        h.cluster1.restoreDefaultFocus();
        assertSame(h.c1view2, h.cluster1.findFocus());

        h = new TestClusterHier();
        h.c1view1.setFocusedByDefault(true);
        h.cluster1.removeAllViewsInLayout();
        h.cluster1.restoreDefaultFocus();
        assertNull(h.cluster1.findFocus());

        h = new TestClusterHier();
        h.c1view1.setFocusedByDefault(true);
        h.cluster1.detachViewFromParent(h.c1view1);
        h.cluster1.attachViewToParent(h.c1view1, 1, null);
        h.cluster1.restoreDefaultFocus();
        assertSame(h.c1view1, h.cluster1.findFocus());

        h = new TestClusterHier();
        h.c1view1.setFocusedByDefault(true);
        h.cluster1.detachViewFromParent(h.c1view1);
        h.cluster1.removeDetachedView(h.c1view1, false);
        h.cluster1.restoreDefaultFocus();
        assertSame(h.c1view2, h.cluster1.findFocus());
    }

    @UiThreadTest
    @Test
    public void testAddViewWithDefaultFocus() {
        // Adding a view that has default focus propagates the default focus chain to the root.
        mMockViewGroup = new MockViewGroup(mContext);
        mMockTextView = new MockTextView(mContext);
        mMockTextView.setFocusable(true);
        mTextView = new TextView(mContext);
        mTextView.setFocusable(true);
        mTextView.setFocusableInTouchMode(true);
        mTextView.setFocusedByDefault(true);
        mMockViewGroup.addView(mMockTextView);
        mMockViewGroup.addView(mTextView);
        mMockViewGroup.restoreDefaultFocus();
        assertTrue(mTextView.isFocused());
    }

    @UiThreadTest
    @Test
    public void testDefaultFocusWorksForClusters() {
        TestClusterHier h = new TestClusterHier();
        h.c2view2.setFocusedByDefault(true);
        h.cluster1.setFocusedByDefault(true);
        h.top.restoreDefaultFocus();
        assertSame(h.c1view1, h.top.findFocus());
        h.cluster2.restoreFocusInCluster(View.FOCUS_DOWN);
        assertSame(h.c2view2, h.top.findFocus());

        // make sure focused in cluster takes priority in cluster-focus
        h.c1view2.setFocusedByDefault(true);
        h.c1view1.setFocusedInCluster();
        h.cluster1.restoreFocusInCluster(View.FOCUS_DOWN);
        assertSame(h.c1view1, h.top.findFocus());
    }

    @UiThreadTest
    @Test
    public void testTouchscreenBlocksFocus() {
        if (!mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) {
            return;
        }
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false);

        // Can't focus/default-focus an element in touchscreenBlocksFocus
        TestClusterHier h = new TestClusterHier(false);
        h.cluster1.setTouchscreenBlocksFocus(true);
        h.c1view2.setFocusedByDefault(true);
        h.top.restoreDefaultFocus();
        assertSame(h.c2view1, h.top.findFocus());
        ArrayList<View> views = new ArrayList<>();
        h.top.addFocusables(views, View.FOCUS_DOWN);
        for (View v : views) {
            assertFalse(v.getParent() == h.cluster1);
        }
        views.clear();

        // Can cluster navigate into it though
        h.top.addKeyboardNavigationClusters(views, View.FOCUS_DOWN);
        assertTrue(views.contains(h.cluster1));
        views.clear();
        h.cluster1.restoreFocusInCluster(View.FOCUS_DOWN);
        assertSame(h.c1view2, h.top.findFocus());
        // can normal-navigate around once inside
        h.top.addFocusables(views, View.FOCUS_DOWN);
        assertTrue(views.contains(h.c1view1));
        views.clear();
        h.c1view1.requestFocus();
        assertSame(h.c1view1, h.top.findFocus());
        // focus loops within cluster (doesn't leave)
        h.c1view2.requestFocus();
        View next = h.top.focusSearch(h.c1view2, View.FOCUS_FORWARD);
        assertSame(h.c1view1, next);
        // but once outside, can no-longer navigate in.
        h.c2view2.requestFocus();
        h.c1view1.requestFocus();
        assertSame(h.c2view2, h.top.findFocus());

        h = new TestClusterHier(false);
        h.c1view1.requestFocus();
        h.nestedGroup.setKeyboardNavigationCluster(true);
        h.nestedGroup.setTouchscreenBlocksFocus(true);
        // since cluster is nested, it should ignore its touchscreenBlocksFocus behavior.
        h.c2view2.requestFocus();
        assertSame(h.c2view2, h.top.findFocus());
        h.top.addFocusables(views, View.FOCUS_DOWN);
        assertTrue(views.contains(h.c2view2));
        views.clear();
    }

    @UiThreadTest
    @Test
    public void testRequestTransparentRegion() {
        MockViewGroup parent = new MockViewGroup(mContext);
        MockView child1 = new MockView(mContext);
        MockView child2 = new MockView(mContext);
        child1.addView(child2);
        parent.addView(child1);
        child1.requestTransparentRegion(child2);
        assertTrue(parent.isRequestTransparentRegionCalled);
    }

    @UiThreadTest
    @Test
    public void testScheduleLayoutAnimation() {
        Animation animation = new AlphaAnimation(mContext, null);

        LayoutAnimationController al = spy(new LayoutAnimationController(animation));
        mMockViewGroup.setLayoutAnimation(al);
        mMockViewGroup.scheduleLayoutAnimation();
        mMockViewGroup.dispatchDraw(new Canvas());
        verify(al, times(1)).start();
    }

    @UiThreadTest
    @Test
    public void testSetAddStatesFromChildren() {
        mMockViewGroup.setAddStatesFromChildren(true);
        assertTrue(mMockViewGroup.addStatesFromChildren());

        mMockViewGroup.setAddStatesFromChildren(false);
        assertFalse(mMockViewGroup.addStatesFromChildren());
    }

    @UiThreadTest
    @Test
    public void testSetChildrenDrawingCacheEnabled() {
        assertTrue(mMockViewGroup.isAnimationCacheEnabled());

        mMockViewGroup.setAnimationCacheEnabled(false);
        assertFalse(mMockViewGroup.isAnimationCacheEnabled());

        mMockViewGroup.setAnimationCacheEnabled(true);
        assertTrue(mMockViewGroup.isAnimationCacheEnabled());
    }

    @UiThreadTest
    @Test
    public void testSetChildrenDrawnWithCacheEnabled() {
        assertFalse(mMockViewGroup.isChildrenDrawnWithCacheEnabled());

        mMockViewGroup.setChildrenDrawnWithCacheEnabled(true);
        assertTrue(mMockViewGroup.isChildrenDrawnWithCacheEnabled());

        mMockViewGroup.setChildrenDrawnWithCacheEnabled(false);
        assertFalse(mMockViewGroup.isChildrenDrawnWithCacheEnabled());
    }

    @UiThreadTest
    @Test
    public void testSetClipChildren() {
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);

        mMockTextView.layout(1, 2, 30, 40);
        mMockViewGroup.layout(1, 1, 100, 200);
        mMockViewGroup.setClipChildren(true);

        MockCanvas canvas = new MockCanvas(bitmap);
        mMockViewGroup.drawChild(canvas, mMockTextView, 100);
        Rect rect = canvas.getClipBounds();
        assertEquals(0, rect.top);
        assertEquals(100, rect.bottom);
        assertEquals(0, rect.left);
        assertEquals(100, rect.right);
    }

    class MockCanvas extends Canvas {

        public boolean mIsSaveCalled;
        public int mLeft;
        public int mTop;
        public int mRight;
        public int mBottom;

        public MockCanvas() {
            super(Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888));
        }

        public MockCanvas(Bitmap bitmap) {
            super(bitmap);
        }

        @Override
        public boolean quickReject(float left, float top, float right,
                float bottom, EdgeType type) {
            super.quickReject(left, top, right, bottom, type);
            return false;
        }

        @Override
        public int save() {
            mIsSaveCalled = true;
            return super.save();
        }

        @Override
        public int save(int saveFlags) {
            mIsSaveCalled = true;
            return super.save(saveFlags);
        }

        @Override
        public boolean clipRect(int left, int top, int right, int bottom) {
            mLeft = left;
            mTop = top;
            mRight = right;
            mBottom = bottom;
            return super.clipRect(left, top, right, bottom);
        }
    }

    @UiThreadTest
    @Test
    public void testSetClipToPadding() {
        final int frameLeft = 1;
        final int frameTop = 2;
        final int frameRight = 100;
        final int frameBottom = 200;
        mMockViewGroup.layout(frameLeft, frameTop, frameRight, frameBottom);

        mMockViewGroup.setClipToPadding(true);
        MockCanvas canvas = new MockCanvas();
        final int paddingLeft = 10;
        final int paddingTop = 20;
        final int paddingRight = 100;
        final int paddingBottom = 200;
        mMockViewGroup.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom);
        mMockViewGroup.dispatchDraw(canvas);
        //check that the clip region does not contain the padding area
        assertTrue(canvas.mIsSaveCalled);
        assertEquals(10, canvas.mLeft);
        assertEquals(20, canvas.mTop);
        assertEquals(-frameLeft, canvas.mRight);
        assertEquals(-frameTop, canvas.mBottom);

        mMockViewGroup.setClipToPadding(false);
        canvas = new MockCanvas();
        mMockViewGroup.dispatchDraw(canvas);
        assertFalse(canvas.mIsSaveCalled);
        assertEquals(0, canvas.mLeft);
        assertEquals(0, canvas.mTop);
        assertEquals(0, canvas.mRight);
        assertEquals(0, canvas.mBottom);
    }

    @UiThreadTest
    @Test
    public void testSetDescendantFocusability() {
        final int FLAG_MASK_FOCUSABILITY = 0x60000;
        assertFalse((mMockViewGroup.getDescendantFocusability() & FLAG_MASK_FOCUSABILITY) == 0);

        mMockViewGroup.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        assertFalse((mMockViewGroup.getDescendantFocusability() & FLAG_MASK_FOCUSABILITY) == 0);

        mMockViewGroup.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        assertFalse((mMockViewGroup.getDescendantFocusability() & FLAG_MASK_FOCUSABILITY) == 0);
        assertFalse((mMockViewGroup.getDescendantFocusability() &
                ViewGroup.FOCUS_BEFORE_DESCENDANTS) == 0);
    }

    @UiThreadTest
    @Test
    public void testSetOnHierarchyChangeListener() {
        MockViewGroup parent = new MockViewGroup(mContext);
        MockViewGroup child = new MockViewGroup(mContext);
        ViewGroup.OnHierarchyChangeListener listener =
                mock(ViewGroup.OnHierarchyChangeListener.class);
        parent.setOnHierarchyChangeListener(listener);
        parent.addView(child);

        parent.removeDetachedView(child, false);
        InOrder inOrder = inOrder(listener);
        inOrder.verify(listener, times(1)).onChildViewAdded(parent, child);
        inOrder.verify(listener, times(1)).onChildViewRemoved(parent, child);
    }

    @UiThreadTest
    @Test
    public void testSetPadding() {
        final int left = 1;
        final int top = 2;
        final int right = 3;
        final int bottom = 4;

        assertEquals(0, mMockViewGroup.getPaddingBottom());
        assertEquals(0, mMockViewGroup.getPaddingTop());
        assertEquals(0, mMockViewGroup.getPaddingLeft());
        assertEquals(0, mMockViewGroup.getPaddingRight());
        assertEquals(0, mMockViewGroup.getPaddingStart());
        assertEquals(0, mMockViewGroup.getPaddingEnd());

        mMockViewGroup.setPadding(left, top, right, bottom);

        assertEquals(bottom, mMockViewGroup.getPaddingBottom());
        assertEquals(top, mMockViewGroup.getPaddingTop());
        assertEquals(left, mMockViewGroup.getPaddingLeft());
        assertEquals(right, mMockViewGroup.getPaddingRight());

        assertEquals(left, mMockViewGroup.getPaddingStart());
        assertEquals(right, mMockViewGroup.getPaddingEnd());
        assertEquals(false, mMockViewGroup.isPaddingRelative());

        // force RTL direction
        mMockViewGroup.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        assertEquals(bottom, mMockViewGroup.getPaddingBottom());
        assertEquals(top, mMockViewGroup.getPaddingTop());
        assertEquals(left, mMockViewGroup.getPaddingLeft());
        assertEquals(right, mMockViewGroup.getPaddingRight());

        assertEquals(right, mMockViewGroup.getPaddingStart());
        assertEquals(left, mMockViewGroup.getPaddingEnd());
        assertEquals(false, mMockViewGroup.isPaddingRelative());
    }

    @UiThreadTest
    @Test
    public void testSetPaddingRelative() {
        final int start = 1;
        final int top = 2;
        final int end = 3;
        final int bottom = 4;

        assertEquals(0, mMockViewGroup.getPaddingBottom());
        assertEquals(0, mMockViewGroup.getPaddingTop());
        assertEquals(0, mMockViewGroup.getPaddingLeft());
        assertEquals(0, mMockViewGroup.getPaddingRight());
        assertEquals(0, mMockViewGroup.getPaddingStart());
        assertEquals(0, mMockViewGroup.getPaddingEnd());

        mMockViewGroup.setPaddingRelative(start, top, end, bottom);

        assertEquals(bottom, mMockViewGroup.getPaddingBottom());
        assertEquals(top, mMockViewGroup.getPaddingTop());
        assertEquals(start, mMockViewGroup.getPaddingLeft());
        assertEquals(end, mMockViewGroup.getPaddingRight());

        assertEquals(start, mMockViewGroup.getPaddingStart());
        assertEquals(end, mMockViewGroup.getPaddingEnd());
        assertEquals(true, mMockViewGroup.isPaddingRelative());

        // force RTL direction after setting relative padding
        mMockViewGroup.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        assertEquals(bottom, mMockViewGroup.getPaddingBottom());
        assertEquals(top, mMockViewGroup.getPaddingTop());
        assertEquals(end, mMockViewGroup.getPaddingLeft());
        assertEquals(start, mMockViewGroup.getPaddingRight());

        assertEquals(start, mMockViewGroup.getPaddingStart());
        assertEquals(end, mMockViewGroup.getPaddingEnd());
        assertEquals(true, mMockViewGroup.isPaddingRelative());

        // force RTL direction before setting relative padding
        mMockViewGroup = new MockViewGroup(mContext);
        mMockViewGroup.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        assertEquals(0, mMockViewGroup.getPaddingBottom());
        assertEquals(0, mMockViewGroup.getPaddingTop());
        assertEquals(0, mMockViewGroup.getPaddingLeft());
        assertEquals(0, mMockViewGroup.getPaddingRight());
        assertEquals(0, mMockViewGroup.getPaddingStart());
        assertEquals(0, mMockViewGroup.getPaddingEnd());

        mMockViewGroup.setPaddingRelative(start, top, end, bottom);

        assertEquals(bottom, mMockViewGroup.getPaddingBottom());
        assertEquals(top, mMockViewGroup.getPaddingTop());
        assertEquals(end, mMockViewGroup.getPaddingLeft());
        assertEquals(start, mMockViewGroup.getPaddingRight());

        assertEquals(start, mMockViewGroup.getPaddingStart());
        assertEquals(end, mMockViewGroup.getPaddingEnd());
        assertEquals(true, mMockViewGroup.isPaddingRelative());
    }

    @UiThreadTest
    @Test
    public void testSetPersistentDrawingCache() {
        mMockViewGroup.setPersistentDrawingCache(1);
        assertEquals(1 & ViewGroup.PERSISTENT_ALL_CACHES, mMockViewGroup
                .getPersistentDrawingCache());
    }

    @UiThreadTest
    @Test
    public void testShowContextMenuForChild() {
        MockViewGroup parent = new MockViewGroup(mContext);
        MockViewGroup child = new MockViewGroup(mContext);
        parent.addView(child);

        child.showContextMenuForChild(null);
        assertTrue(parent.isShowContextMenuForChildCalled);
    }

    @UiThreadTest
    @Test
    public void testShowContextMenuForChild_WithXYCoords() {
        MockViewGroup parent = new MockViewGroup(mContext);
        MockViewGroup child = new MockViewGroup(mContext);
        parent.addView(child);

        child.showContextMenuForChild(null, 48, 48);
        assertTrue(parent.isShowContextMenuForChildCalledWithXYCoords);
    }

    @UiThreadTest
    @Test
    public void testStartLayoutAnimation() {
        RotateAnimation animation = new RotateAnimation(0.1f, 0.1f);
        LayoutAnimationController la = new LayoutAnimationController(animation);
        mMockViewGroup.setLayoutAnimation(la);

        mMockViewGroup.layout(1, 1, 100, 100);
        assertFalse(mMockViewGroup.isLayoutRequested());
        mMockViewGroup.startLayoutAnimation();
        assertTrue(mMockViewGroup.isLayoutRequested());
    }

    @UiThreadTest
    @Test
    public void testUpdateViewLayout() {
        MockViewGroup parent = new MockViewGroup(mContext);
        MockViewGroup child = new MockViewGroup(mContext);

        parent.addView(child);
        LayoutParams param = new LayoutParams(100, 200);
        parent.updateViewLayout(child, param);
        assertEquals(param.width, child.getLayoutParams().width);
        assertEquals(param.height, child.getLayoutParams().height);
    }

    @UiThreadTest
    @Test
    public void testDebug() {
        final int EXPECTED = 100;
        MockViewGroup parent = new MockViewGroup(mContext);
        MockViewGroup child = new MockViewGroup(mContext);
        parent.addView(child);

        parent.debug(EXPECTED);
        assertEquals(EXPECTED + 1, child.debugDepth);
    }

    @UiThreadTest
    @Test
    public void testDispatchKeyEventPreIme() {
        KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER);
        assertFalse(mMockViewGroup.dispatchKeyEventPreIme(event));
        assertFalse(mMockViewGroup.dispatchKeyShortcutEvent(event));

        mMockViewGroup.addView(mMockTextView);
        mMockViewGroup.requestChildFocus(mMockTextView, null);
        mMockViewGroup.layout(0, 0, 100, 200);
        assertFalse(mMockViewGroup.dispatchKeyEventPreIme(event));
        assertFalse(mMockViewGroup.dispatchKeyShortcutEvent(event));

        mMockViewGroup.requestChildFocus(mMockTextView, null);
        mMockTextView.layout(0, 0, 50, 50);
        assertTrue(mMockViewGroup.dispatchKeyEventPreIme(event));
        assertTrue(mMockViewGroup.dispatchKeyShortcutEvent(event));

        mMockViewGroup.setStaticTransformationsEnabled(true);
        Canvas canvas = new Canvas();
        mMockViewGroup.drawChild(canvas, mMockTextView, 100);
        assertTrue(mMockViewGroup.isGetChildStaticTransformationCalled);
        mMockViewGroup.isGetChildStaticTransformationCalled = false;
        mMockViewGroup.setStaticTransformationsEnabled(false);
        mMockViewGroup.drawChild(canvas, mMockTextView, 100);
        assertFalse(mMockViewGroup.isGetChildStaticTransformationCalled);
    }

    @UiThreadTest
    @Test
    public void testStartActionModeForChildRespectsSubclassModeOnPrimary() {
        MockViewGroupSubclass vgParent = new MockViewGroupSubclass(mContext);
        MockViewGroupSubclass vg = new MockViewGroupSubclass(mContext);
        vg.shouldReturnOwnTypelessActionMode = true;
        vgParent.addView(vg);
        vg.addView(mMockTextView);

        mMockTextView.startActionMode(NO_OP_ACTION_MODE_CALLBACK, ActionMode.TYPE_PRIMARY);

        assertTrue(vg.isStartActionModeForChildTypedCalled);
        assertTrue(vg.isStartActionModeForChildTypelessCalled);
        // Call should not bubble up as we have an intercepting implementation.
        assertFalse(vgParent.isStartActionModeForChildTypedCalled);
    }

    @UiThreadTest
    @Test
    public void testStartActionModeForChildIgnoresSubclassModeOnFloating() {
        MockViewGroupSubclass vgParent = new MockViewGroupSubclass(mContext);
        MockViewGroupSubclass vg = new MockViewGroupSubclass(mContext);
        vg.shouldReturnOwnTypelessActionMode = true;
        vgParent.addView(vg);
        vg.addView(mMockTextView);

        mMockTextView.startActionMode(NO_OP_ACTION_MODE_CALLBACK, ActionMode.TYPE_FLOATING);

        assertTrue(vg.isStartActionModeForChildTypedCalled);
        assertFalse(vg.isStartActionModeForChildTypelessCalled);
        // Call should bubble up as we have a floating type.
        assertTrue(vgParent.isStartActionModeForChildTypedCalled);
    }

    @UiThreadTest
    @Test
    public void testStartActionModeForChildTypedBubblesUpToParent() {
        MockViewGroupSubclass vgParent = new MockViewGroupSubclass(mContext);
        MockViewGroupSubclass vg = new MockViewGroupSubclass(mContext);
        vgParent.addView(vg);
        vg.addView(mMockTextView);

        mMockTextView.startActionMode(NO_OP_ACTION_MODE_CALLBACK, ActionMode.TYPE_FLOATING);

        assertTrue(vg.isStartActionModeForChildTypedCalled);
        assertTrue(vgParent.isStartActionModeForChildTypedCalled);
    }

    @UiThreadTest
    @Test
    public void testStartActionModeForChildTypelessBubblesUpToParent() {
        MockViewGroupSubclass vgParent = new MockViewGroupSubclass(mContext);
        MockViewGroupSubclass vg = new MockViewGroupSubclass(mContext);
        vgParent.addView(vg);
        vg.addView(mMockTextView);

        mMockTextView.startActionMode(NO_OP_ACTION_MODE_CALLBACK);

        assertTrue(vg.isStartActionModeForChildTypedCalled);
        assertTrue(vg.isStartActionModeForChildTypelessCalled);
        assertTrue(vgParent.isStartActionModeForChildTypedCalled);
    }

    @UiThreadTest
    @Test
    public void testTemporaryDetach() {
        // [vgParent]
        //   - [viewParent1]
        //   - [viewParent1]
        //   - [mMockViewGroup]
        //     - [view1]
        //     - [view2]
        MockViewGroupSubclass vgParent = new MockViewGroupSubclass(mContext);
        TemporaryDetachingMockView viewParent1 = new TemporaryDetachingMockView(mContext);
        TemporaryDetachingMockView viewParent2 = new TemporaryDetachingMockView(mContext);
        vgParent.addView(viewParent1);
        vgParent.addView(viewParent2);
        MockViewGroupSubclass vg = new MockViewGroupSubclass(mContext);
        vgParent.addView(vg);
        TemporaryDetachingMockView view1 = new TemporaryDetachingMockView(mContext);
        TemporaryDetachingMockView view2 = new TemporaryDetachingMockView(mContext);
        vg.addView(view1);
        vg.addView(view2);

        // Make sure that no View is temporarity detached in the initial state.
        assertFalse(viewParent1.isTemporarilyDetached());
        assertEquals(0, viewParent1.getDispatchStartTemporaryDetachCount());
        assertEquals(0, viewParent1.getDispatchFinishTemporaryDetachCount());
        assertEquals(0, viewParent1.getOnStartTemporaryDetachCount());
        assertEquals(0, viewParent1.getOnFinishTemporaryDetachCount());
        assertFalse(viewParent2.isTemporarilyDetached());
        assertEquals(0, viewParent2.getDispatchStartTemporaryDetachCount());
        assertEquals(0, viewParent2.getDispatchFinishTemporaryDetachCount());
        assertEquals(0, viewParent2.getOnStartTemporaryDetachCount());
        assertEquals(0, viewParent2.getOnFinishTemporaryDetachCount());
        assertFalse(view1.isTemporarilyDetached());
        assertEquals(0, view1.getDispatchStartTemporaryDetachCount());
        assertEquals(0, view1.getDispatchFinishTemporaryDetachCount());
        assertEquals(0, view1.getOnStartTemporaryDetachCount());
        assertEquals(0, view1.getOnFinishTemporaryDetachCount());
        assertFalse(view2.isTemporarilyDetached());
        assertEquals(0, view2.getDispatchStartTemporaryDetachCount());
        assertEquals(0, view2.getDispatchFinishTemporaryDetachCount());
        assertEquals(0, view2.getOnStartTemporaryDetachCount());
        assertEquals(0, view2.getOnFinishTemporaryDetachCount());

        // [vgParent]
        //   - [viewParent1]
        //   - [viewParent1]
        //   - [mMockViewGroup]           <- dispatchStartTemporaryDetach()
        //     - [view1]
        //     - [view2]
        vg.dispatchStartTemporaryDetach();

        assertFalse(viewParent1.isTemporarilyDetached());
        assertEquals(0, viewParent1.getDispatchStartTemporaryDetachCount());
        assertEquals(0, viewParent1.getDispatchFinishTemporaryDetachCount());
        assertEquals(0, viewParent1.getOnStartTemporaryDetachCount());
        assertEquals(0, viewParent1.getOnFinishTemporaryDetachCount());
        assertFalse(viewParent2.isTemporarilyDetached());
        assertEquals(0, viewParent2.getDispatchStartTemporaryDetachCount());
        assertEquals(0, viewParent2.getDispatchFinishTemporaryDetachCount());
        assertEquals(0, viewParent2.getOnStartTemporaryDetachCount());
        assertEquals(0, viewParent2.getOnFinishTemporaryDetachCount());
        assertTrue(view1.isTemporarilyDetached());
        assertEquals(1, view1.getDispatchStartTemporaryDetachCount());
        assertEquals(0, view1.getDispatchFinishTemporaryDetachCount());
        assertEquals(1, view1.getOnStartTemporaryDetachCount());
        assertEquals(0, view1.getOnFinishTemporaryDetachCount());
        assertTrue(view2.isTemporarilyDetached());
        assertEquals(1, view2.getDispatchStartTemporaryDetachCount());
        assertEquals(0, view2.getDispatchFinishTemporaryDetachCount());
        assertEquals(1, view2.getOnStartTemporaryDetachCount());
        assertEquals(0, view2.getOnFinishTemporaryDetachCount());

        // [vgParent]
        //   - [viewParent1]
        //   - [viewParent1]
        //   - [mMockViewGroup]           <- dispatchFinishTemporaryDetach()
        //     - [view1]
        //     - [view2]
        vg.dispatchFinishTemporaryDetach();

        assertFalse(viewParent1.isTemporarilyDetached());
        assertEquals(0, viewParent1.getDispatchStartTemporaryDetachCount());
        assertEquals(0, viewParent1.getDispatchFinishTemporaryDetachCount());
        assertEquals(0, viewParent1.getOnStartTemporaryDetachCount());
        assertEquals(0, viewParent1.getOnFinishTemporaryDetachCount());
        assertFalse(viewParent2.isTemporarilyDetached());
        assertEquals(0, viewParent2.getDispatchStartTemporaryDetachCount());
        assertEquals(0, viewParent2.getDispatchFinishTemporaryDetachCount());
        assertEquals(0, viewParent2.getOnStartTemporaryDetachCount());
        assertEquals(0, viewParent2.getOnFinishTemporaryDetachCount());
        assertFalse(view1.isTemporarilyDetached());
        assertEquals(1, view1.getDispatchStartTemporaryDetachCount());
        assertEquals(1, view1.getDispatchFinishTemporaryDetachCount());
        assertEquals(1, view1.getOnStartTemporaryDetachCount());
        assertEquals(1, view1.getOnFinishTemporaryDetachCount());
        assertFalse(view2.isTemporarilyDetached());
        assertEquals(1, view2.getDispatchStartTemporaryDetachCount());
        assertEquals(1, view2.getDispatchFinishTemporaryDetachCount());
        assertEquals(1, view2.getOnStartTemporaryDetachCount());
        assertEquals(1, view2.getOnFinishTemporaryDetachCount());

        // [vgParent]         <- dispatchStartTemporaryDetach()
        //   - [viewParent1]
        //   - [viewParent1]
        //   - [mMockViewGroup]
        //     - [view1]
        //     - [view2]
        vgParent.dispatchStartTemporaryDetach();

        assertTrue(viewParent1.isTemporarilyDetached());
        assertEquals(1, viewParent1.getDispatchStartTemporaryDetachCount());
        assertEquals(0, viewParent1.getDispatchFinishTemporaryDetachCount());
        assertEquals(1, viewParent1.getOnStartTemporaryDetachCount());
        assertEquals(0, viewParent1.getOnFinishTemporaryDetachCount());
        assertTrue(viewParent2.isTemporarilyDetached());
        assertEquals(1, viewParent2.getDispatchStartTemporaryDetachCount());
        assertEquals(0, viewParent2.getDispatchFinishTemporaryDetachCount());
        assertEquals(1, viewParent2.getOnStartTemporaryDetachCount());
        assertEquals(0, viewParent2.getOnFinishTemporaryDetachCount());
        assertTrue(view1.isTemporarilyDetached());
        assertEquals(2, view1.getDispatchStartTemporaryDetachCount());
        assertEquals(1, view1.getDispatchFinishTemporaryDetachCount());
        assertEquals(2, view1.getOnStartTemporaryDetachCount());
        assertEquals(1, view1.getOnFinishTemporaryDetachCount());
        assertTrue(view2.isTemporarilyDetached());
        assertEquals(2, view2.getDispatchStartTemporaryDetachCount());
        assertEquals(1, view2.getDispatchFinishTemporaryDetachCount());
        assertEquals(2, view2.getOnStartTemporaryDetachCount());
        assertEquals(1, view2.getOnFinishTemporaryDetachCount());

        // [vgParent]         <- dispatchFinishTemporaryDetach()
        //   - [viewParent1]
        //   - [viewParent1]
        //   - [mMockViewGroup]
        //     - [view1]
        //     - [view2]
        vgParent.dispatchFinishTemporaryDetach();

        assertFalse(viewParent1.isTemporarilyDetached());
        assertEquals(1, viewParent1.getDispatchStartTemporaryDetachCount());
        assertEquals(1, viewParent1.getDispatchFinishTemporaryDetachCount());
        assertEquals(1, viewParent1.getOnStartTemporaryDetachCount());
        assertEquals(1, viewParent1.getOnFinishTemporaryDetachCount());
        assertFalse(viewParent2.isTemporarilyDetached());
        assertEquals(1, viewParent2.getDispatchStartTemporaryDetachCount());
        assertEquals(1, viewParent2.getDispatchFinishTemporaryDetachCount());
        assertEquals(1, viewParent2.getOnStartTemporaryDetachCount());
        assertEquals(1, viewParent2.getOnFinishTemporaryDetachCount());
        assertFalse(view1.isTemporarilyDetached());
        assertEquals(2, view1.getDispatchStartTemporaryDetachCount());
        assertEquals(2, view1.getDispatchFinishTemporaryDetachCount());
        assertEquals(2, view1.getOnStartTemporaryDetachCount());
        assertEquals(2, view1.getOnFinishTemporaryDetachCount());
        assertFalse(view2.isTemporarilyDetached());
        assertEquals(2, view2.getDispatchStartTemporaryDetachCount());
        assertEquals(2, view2.getDispatchFinishTemporaryDetachCount());
        assertEquals(2, view2.getOnStartTemporaryDetachCount());
        assertEquals(2, view2.getOnFinishTemporaryDetachCount());
    }

    private static final ActionMode.Callback NO_OP_ACTION_MODE_CALLBACK =
            new ActionMode.Callback() {
                @Override
                public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                    return false;
                }

                @Override
                public void onDestroyActionMode(ActionMode mode) {}

                @Override
                public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                    return false;
                }

                @Override
                public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                    return false;
                }
            };

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

    private static class MockViewGroupSubclass extends ViewGroup {
        boolean isStartActionModeForChildTypedCalled = false;
        boolean isStartActionModeForChildTypelessCalled = false;
        boolean shouldReturnOwnTypelessActionMode = false;

        public MockViewGroupSubclass(Context context) {
            super(context);
        }

        @Override
        public ActionMode startActionModeForChild(View originalView, ActionMode.Callback callback) {
            isStartActionModeForChildTypelessCalled = true;
            if (shouldReturnOwnTypelessActionMode) {
                return NO_OP_ACTION_MODE;
            }
            return super.startActionModeForChild(originalView, callback);
        }

        @Override
        public ActionMode startActionModeForChild(
                View originalView, ActionMode.Callback callback, int type) {
            isStartActionModeForChildTypedCalled = true;
            return super.startActionModeForChild(originalView, callback, type);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            // no-op
        }
    }

    static public int resetRtlPropertiesCount;
    static public int resetResolvedLayoutDirectionCount;
    static public int resetResolvedTextDirectionCount;
    static public int resetResolvedTextAlignmentCount;
    static public int resetResolvedPaddingCount;
    static public int resetResolvedDrawablesCount;


    private static void clearRtlCounters() {
        resetRtlPropertiesCount = 0;
        resetResolvedLayoutDirectionCount = 0;
        resetResolvedTextDirectionCount = 0;
        resetResolvedTextAlignmentCount = 0;
        resetResolvedPaddingCount = 0;
        resetResolvedDrawablesCount = 0;
    }

    @UiThreadTest
    @Test
    public void testResetRtlProperties() {
        clearRtlCounters();

        MockView2 v1 = new MockView2(mContext);
        MockView2 v2 = new MockView2(mContext);

        MockViewGroup v3 = new MockViewGroup(mContext);
        MockView2 v4 = new MockView2(mContext);

        v3.addView(v4);
        assertEquals(1, resetRtlPropertiesCount);
        assertEquals(1, resetResolvedLayoutDirectionCount);
        assertEquals(1, resetResolvedTextDirectionCount);
        assertEquals(1, resetResolvedTextAlignmentCount);
        assertEquals(1, resetResolvedPaddingCount);
        assertEquals(1, resetResolvedDrawablesCount);

        clearRtlCounters();
        mMockViewGroup.addView(v1);
        mMockViewGroup.addView(v2);
        mMockViewGroup.addView(v3);

        assertEquals(3, resetRtlPropertiesCount); // for v1 / v2 / v3 only
        assertEquals(4, resetResolvedLayoutDirectionCount); // for v1 / v2 / v3 / v4
        assertEquals(4, resetResolvedTextDirectionCount);
        assertEquals(3, resetResolvedTextAlignmentCount); // for v1 / v2 / v3 only
        assertEquals(4, resetResolvedPaddingCount);
        assertEquals(4, resetResolvedDrawablesCount);

        clearRtlCounters();
        mMockViewGroup.resetRtlProperties();
        assertEquals(1, resetRtlPropertiesCount); // for mMockViewGroup only
        assertEquals(5, resetResolvedLayoutDirectionCount); // for all
        assertEquals(5, resetResolvedTextDirectionCount);
        // for mMockViewGroup only as TextAlignment is not inherited (default is Gravity)
        assertEquals(1, resetResolvedTextAlignmentCount);
        assertEquals(5, resetResolvedPaddingCount);
        assertEquals(5, resetResolvedDrawablesCount);
    }

    static class MockTextView extends TextView {

        public boolean isClearFocusCalled;
        public boolean isDispatchRestoreInstanceStateCalled;
        public int visibility;
        public boolean mIsRefreshDrawableStateCalled;
        public boolean isDrawCalled;

        public MockTextView(Context context) {
            super(context);
        }

        @Override
        public void draw(Canvas canvas) {
            super.draw(canvas);
            isDrawCalled = true;
        }

        @Override
        public void clearFocus() {
            isClearFocusCalled = true;
            super.clearFocus();
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            return true;
        }

        @Override
        public void dispatchRestoreInstanceState(
                SparseArray<Parcelable> container) {
            isDispatchRestoreInstanceStateCalled = true;
            super.dispatchRestoreInstanceState(container);
        }

        @Override
        public boolean onTrackballEvent(MotionEvent event) {
            return true;
        }

        @Override
        public boolean dispatchUnhandledMove(View focused, int direction) {
            return true;
        }

        @Override
        public void onWindowVisibilityChanged(int visibility) {
            this.visibility = visibility;
            super.onWindowVisibilityChanged(visibility);
        }

        @Override
        public void refreshDrawableState() {
            mIsRefreshDrawableStateCalled = true;
            super.refreshDrawableState();
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            super.dispatchTouchEvent(event);
            return true;
        }

        @Override
        public boolean dispatchKeyEventPreIme(KeyEvent event) {
            return true;
        }

        @Override
        public boolean dispatchKeyShortcutEvent(KeyEvent event) {
            return true;
        }
    }

    static class MockViewGroup extends ViewGroup {

        public boolean isRecomputeViewAttributesCalled;
        public boolean isShowContextMenuForChildCalled;
        public boolean isShowContextMenuForChildCalledWithXYCoords;
        public boolean isRefreshDrawableStateCalled;
        public boolean isOnRestoreInstanceStateCalled;
        public boolean isOnCreateDrawableStateCalled;
        public boolean isOnInterceptTouchEventCalled;
        public boolean isOnRequestFocusInDescendantsCalled;
        public boolean isOnViewAddedCalled;
        public boolean isOnViewRemovedCalled;
        public boolean isFocusableViewAvailable;
        public boolean isDispatchDrawCalled;
        public boolean isRequestDisallowInterceptTouchEventCalled;
        public boolean isRequestTransparentRegionCalled;
        public boolean isGetChildStaticTransformationCalled;
        public int[] location;
        public int measureChildCalledTime;
        public boolean isOnAnimationEndCalled;
        public boolean isOnAnimationStartCalled;
        public int debugDepth;
        public int drawChildCalledTime;
        public Canvas canvas;
        public boolean isDrawableStateChangedCalled;
        public boolean isRequestLayoutCalled;
        public boolean isOnLayoutCalled;
        public boolean isOnDescendantInvalidatedCalled;
        public int left;
        public int top;
        public int right;
        public int bottom;
        public boolean returnActualFocusSearchResult;

        public MockViewGroup(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }

        public MockViewGroup(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public MockViewGroup(Context context) {
            super(context);
        }

        @Override
        public void onLayout(boolean changed, int l, int t, int r, int b) {
            isOnLayoutCalled = true;
            left = l;
            top = t;
            right = r;
            bottom = b;
        }

        @Override
        public boolean addViewInLayout(View child, int index,
                ViewGroup.LayoutParams params) {
            return super.addViewInLayout(child, index, params);
        }

        @Override
        public boolean addViewInLayout(View child, int index,
                ViewGroup.LayoutParams params, boolean preventRequestLayout) {
            return super.addViewInLayout(child, index, params, preventRequestLayout);
        }

        @Override
        public void attachLayoutAnimationParameters(View child,
                ViewGroup.LayoutParams params, int index, int count) {
            super.attachLayoutAnimationParameters(child, params, index, count);
        }

        @Override
        public void attachViewToParent(View child, int index,
                LayoutParams params) {
            super.attachViewToParent(child, index, params);
        }

        @Override
        public boolean canAnimate() {
            return super.canAnimate();
        }

        @Override
        public boolean checkLayoutParams(LayoutParams p) {
            return super.checkLayoutParams(p);
        }

        @Override
        public void refreshDrawableState() {
            isRefreshDrawableStateCalled = true;
            super.refreshDrawableState();
        }

        @Override
        public void cleanupLayoutState(View child) {
            super.cleanupLayoutState(child);
        }

        @Override
        public void detachAllViewsFromParent() {
            super.detachAllViewsFromParent();
        }

        @Override
        public void detachViewFromParent(int index) {
            super.detachViewFromParent(index);
        }

        @Override
        public void detachViewFromParent(View child) {
            super.detachViewFromParent(child);
        }
        @Override

        public void detachViewsFromParent(int start, int count) {
            super.detachViewsFromParent(start, count);
        }

        @Override
        public void dispatchDraw(Canvas canvas) {
            isDispatchDrawCalled = true;
            super.dispatchDraw(canvas);
            this.canvas = canvas;
        }

        @Override
        public void dispatchFreezeSelfOnly(SparseArray<Parcelable> container) {
            super.dispatchFreezeSelfOnly(container);
        }

        @Override
        public void dispatchRestoreInstanceState(
                SparseArray<Parcelable> container) {
            super.dispatchRestoreInstanceState(container);
        }

        @Override
        public void dispatchSaveInstanceState(
                SparseArray<Parcelable> container) {
            super.dispatchSaveInstanceState(container);
        }

        @Override
        public void dispatchSetPressed(boolean pressed) {
            super.dispatchSetPressed(pressed);
        }

        @Override
        public void dispatchThawSelfOnly(SparseArray<Parcelable> container) {
            super.dispatchThawSelfOnly(container);
        }

        @Override
        public void onRestoreInstanceState(Parcelable state) {
            isOnRestoreInstanceStateCalled = true;
            super.onRestoreInstanceState(state);
        }

        @Override
        public void drawableStateChanged() {
            isDrawableStateChangedCalled = true;
            super.drawableStateChanged();
        }

        @Override
        public boolean drawChild(Canvas canvas, View child, long drawingTime) {
            drawChildCalledTime++;
            return super.drawChild(canvas, child, drawingTime);
        }

        @Override
        public boolean fitSystemWindows(Rect insets) {
            return super.fitSystemWindows(insets);
        }

        @Override
        public LayoutParams generateDefaultLayoutParams() {
            return super.generateDefaultLayoutParams();
        }

        @Override
        public LayoutParams generateLayoutParams(LayoutParams p) {
            return super.generateLayoutParams(p);
        }

        @Override
        public int getChildDrawingOrder(int childCount, int i) {
            return super.getChildDrawingOrder(childCount, i);
        }

        @Override
        public boolean getChildStaticTransformation(View child,
                Transformation t) {
            isGetChildStaticTransformationCalled = true;
            return super.getChildStaticTransformation(child, t);
        }

        @Override
        public void measureChild(View child, int parentWidthMeasureSpec,
                int parentHeightMeasureSpec) {
            measureChildCalledTime++;
            super.measureChild(child, parentWidthMeasureSpec, parentHeightMeasureSpec);
        }

        @Override
        public void measureChildren(int widthMeasureSpec,
                int heightMeasureSpec) {
            super.measureChildren(widthMeasureSpec, heightMeasureSpec);
        }

        @Override
        public void measureChildWithMargins(View child,
                int parentWidthMeasureSpec, int widthUsed,
                int parentHeightMeasureSpec, int heightUsed) {
            super.measureChildWithMargins(child, parentWidthMeasureSpec, widthUsed,
                    parentHeightMeasureSpec, heightUsed);
        }

        @Override
        public void onAnimationEnd() {
            isOnAnimationEndCalled = true;
            super.onAnimationEnd();
        }

        @Override
        public void onAnimationStart() {
            super.onAnimationStart();
            isOnAnimationStartCalled = true;
        }

        @Override
        public int[] onCreateDrawableState(int extraSpace) {
            isOnCreateDrawableStateCalled = true;
            return super.onCreateDrawableState(extraSpace);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            isOnInterceptTouchEventCalled = true;
            return super.onInterceptTouchEvent(ev);
        }

        @Override
        public boolean onRequestFocusInDescendants(int direction,
                Rect previouslyFocusedRect) {
            isOnRequestFocusInDescendantsCalled = true;
            return super.onRequestFocusInDescendants(direction, previouslyFocusedRect);
        }

        @Override
        public void onViewAdded(View child) {
            isOnViewAddedCalled = true;
            super.onViewAdded(child);
        }

        @Override
        public void onViewRemoved(View child) {
            isOnViewRemovedCalled = true;
            super.onViewRemoved(child);
        }

        @Override
        public void recomputeViewAttributes(View child) {
            isRecomputeViewAttributesCalled = true;
            super.recomputeViewAttributes(child);
        }

        @Override
        public void removeDetachedView(View child, boolean animate) {
            super.removeDetachedView(child, animate);
        }

        @Override
        public boolean showContextMenuForChild(View originalView) {
            isShowContextMenuForChildCalled = true;
            return super.showContextMenuForChild(originalView);
        }

        @Override
        public boolean showContextMenuForChild(View originalView, float x, float y) {
            isShowContextMenuForChildCalledWithXYCoords = true;
            return super.showContextMenuForChild(originalView, x, y);
        }

        @Override
        public boolean isInTouchMode() {
            super.isInTouchMode();
            return false;
        }

        @Override
        public void focusableViewAvailable(View v) {
            isFocusableViewAvailable = true;
            super.focusableViewAvailable(v);
        }

        @Override
        public View focusSearch(View focused, int direction) {
            if (returnActualFocusSearchResult) {
                return super.focusSearch(focused, direction);
            } else {
                super.focusSearch(focused, direction);
                return focused;
            }
        }

        @Override
        public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            isRequestDisallowInterceptTouchEventCalled = true;
            super.requestDisallowInterceptTouchEvent(disallowIntercept);
        }

        @Override
        public void requestTransparentRegion(View child) {
            isRequestTransparentRegionCalled = true;
            super.requestTransparentRegion(child);
        }

        @Override
        public void debug(int depth) {
            debugDepth = depth;
            super.debug(depth);
        }

        @Override
        public void requestLayout() {
            isRequestLayoutCalled = true;
            super.requestLayout();
        }

        @Override
        public void setStaticTransformationsEnabled(boolean enabled) {
            super.setStaticTransformationsEnabled(enabled);
        }

        @Override
        public void resetRtlProperties() {
            super.resetRtlProperties();
            resetRtlPropertiesCount++;
        }

        @Override
        public void resetResolvedLayoutDirection() {
            super.resetResolvedLayoutDirection();
            resetResolvedLayoutDirectionCount++;
        }

        @Override
        public void resetResolvedTextDirection() {
            super.resetResolvedTextDirection();
            resetResolvedTextDirectionCount++;
        }

        @Override
        public void resetResolvedTextAlignment() {
            super.resetResolvedTextAlignment();
            resetResolvedTextAlignmentCount++;
        }

        @Override
        public void resetResolvedPadding() {
            super.resetResolvedPadding();
            resetResolvedPaddingCount++;
        }

        @Override
        protected void resetResolvedDrawables() {
            super.resetResolvedDrawables();
            resetResolvedDrawablesCount++;
        }

        @Override
        public boolean setFrame(int left, int top, int right, int bottom) {
            return super.setFrame(left, top, right, bottom);
        }

        @Override
        public void setChildrenDrawnWithCacheEnabled(boolean enabled) {
            super.setChildrenDrawnWithCacheEnabled(enabled);
        }

        @Override
        public boolean isChildrenDrawnWithCacheEnabled() {
            return super.isChildrenDrawnWithCacheEnabled();
        }

        @Override
        public void onDescendantInvalidated(@NonNull View child, @NonNull View target) {
            isOnDescendantInvalidatedCalled = true;
            super.onDescendantInvalidated(child, target);
        }
    }

    static class MockView2 extends View {

        public MockView2(Context context) {
            super(context);
        }

        public MockView2(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public MockView2(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }

        @Override
        public void resetRtlProperties() {
            super.resetRtlProperties();
            resetRtlPropertiesCount++;
        }

        @Override
        public void resetResolvedLayoutDirection() {
            super.resetResolvedLayoutDirection();
            resetResolvedLayoutDirectionCount++;
        }

        @Override
        public void resetResolvedTextDirection() {
            super.resetResolvedTextDirection();
            resetResolvedTextDirectionCount++;
        }

        @Override
        public void resetResolvedTextAlignment() {
            super.resetResolvedTextAlignment();
            resetResolvedTextAlignmentCount++;
        }

        @Override
        public void resetResolvedPadding() {
            super.resetResolvedPadding();
            resetResolvedPaddingCount++;
        }

        @Override
        protected void resetResolvedDrawables() {
            super.resetResolvedDrawables();
            resetResolvedDrawablesCount++;
        }
    }

    static final class TemporaryDetachingMockView extends View {
        private int mDispatchStartTemporaryDetachCount = 0;
        private int mDispatchFinishTemporaryDetachCount = 0;
        private int mOnStartTemporaryDetachCount = 0;
        private int mOnFinishTemporaryDetachCount = 0;

        public TemporaryDetachingMockView(Context context) {
            super(context);
        }

        @Override
        public void dispatchStartTemporaryDetach() {
            super.dispatchStartTemporaryDetach();
            mDispatchStartTemporaryDetachCount += 1;
        }

        @Override
        public void dispatchFinishTemporaryDetach() {
            super.dispatchFinishTemporaryDetach();
            mDispatchFinishTemporaryDetachCount += 1;
        }

        @Override
        public void onStartTemporaryDetach() {
            super.onStartTemporaryDetach();
            mOnStartTemporaryDetachCount += 1;
        }

        @Override
        public void onFinishTemporaryDetach() {
            super.onFinishTemporaryDetach();
            mOnFinishTemporaryDetachCount += 1;
        }

        public int getDispatchStartTemporaryDetachCount() {
            return mDispatchStartTemporaryDetachCount;
        }

        public int getDispatchFinishTemporaryDetachCount() {
            return mDispatchFinishTemporaryDetachCount;
        }

        public int getOnStartTemporaryDetachCount() {
            return mOnStartTemporaryDetachCount;
        }

        public int getOnFinishTemporaryDetachCount() {
            return mOnFinishTemporaryDetachCount;
        }
    }

    @Override
    public void setResult(int resultCode) {
        synchronized (mSync) {
            mSync.mHasNotify = true;
            mSync.notify();
            mResultCode = resultCode;
        }
    }
}
