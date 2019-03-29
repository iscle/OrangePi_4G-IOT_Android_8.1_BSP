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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.Instrumentation;
import android.app.Presentation;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.InputQueue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class WindowTest {
    private static final String TAG = "WindowTest";
    private static final int VIEWGROUP_LAYOUT_HEIGHT = 100;
    private static final int VIEWGROUP_LAYOUT_WIDTH = 200;

    private Instrumentation mInstrumentation;
    private WindowCtsActivity mActivity;
    private Window mWindow;
    private Window.Callback mWindowCallback;
    private SurfaceView mSurfaceView;

    // for testing setLocalFocus
    private ProjectedPresentation mPresentation;
    private VirtualDisplay mVirtualDisplay;

    @Rule
    public ActivityTestRule<WindowCtsActivity> mActivityRule =
            new ActivityTestRule<>(WindowCtsActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
        mWindow = mActivity.getWindow();


        mWindowCallback = mock(Window.Callback.class);
        doReturn(true).when(mWindowCallback).dispatchKeyEvent(any());
        doReturn(true).when(mWindowCallback).dispatchTouchEvent(any());
        doReturn(true).when(mWindowCallback).dispatchTrackballEvent(any());
        doReturn(true).when(mWindowCallback).dispatchGenericMotionEvent(any());
        doReturn(true).when(mWindowCallback).dispatchPopulateAccessibilityEvent(any());
        doReturn(true).when(mWindowCallback).onMenuItemSelected(anyInt(), any());
    }

    @After
    public void teardown() {
        if (mActivity != null) {
            mActivity.setFlagFalse();
        }
    }

    @UiThreadTest
    @Test
    public void testConstructor() {
        mWindow = new MockWindow(mActivity);
        assertSame(mActivity, mWindow.getContext());
    }

    /**
     * Test flags related methods:
     * 1. addFlags: add the given flag to WindowManager.LayoutParams.flags, if add more than one
     *    in sequence, flags will be set to formerFlag | latterFlag.
     * 2. setFlags: _1. set the flags of the window.
     *              _2. test invocation of Window.Callback#onWindowAttributesChanged.
     * 3. clearFlags: clear the flag bits as specified in flags.
     */
    @Test
    public void testOpFlags() {
        mWindow = new MockWindow(mActivity);
        final WindowManager.LayoutParams attrs = mWindow.getAttributes();
        assertEquals(0, attrs.flags);

        mWindow.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        assertEquals(WindowManager.LayoutParams.FLAG_FULLSCREEN, attrs.flags);

        mWindow.addFlags(WindowManager.LayoutParams.FLAG_DITHER);
        assertEquals(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_DITHER, attrs.flags);

        mWindow.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        assertEquals(WindowManager.LayoutParams.FLAG_DITHER, attrs.flags);
        mWindow.clearFlags(WindowManager.LayoutParams.FLAG_DITHER);
        assertEquals(0, attrs.flags);

        mWindow.setCallback(mWindowCallback);
        verify(mWindowCallback, never()).onWindowAttributesChanged(any());
        // mask == flag, no bit of flag need to be modified.
        mWindow.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        assertEquals(WindowManager.LayoutParams.FLAG_FULLSCREEN, attrs.flags);

        // Test if the callback method is called by system
        verify(mWindowCallback, times(1)).onWindowAttributesChanged(attrs);
        mWindow.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        mWindow.clearFlags(WindowManager.LayoutParams.FLAG_DITHER);
    }

    @Test
    public void testFindViewById() {
        TextView v = (TextView) mWindow.findViewById(R.id.listview_window);
        assertNotNull(v);
        assertEquals(R.id.listview_window, v.getId());
    }

    /**
     * getAttributes: Retrieve the current window attributes associated with this panel.
     *    Return is 1.the existing window attributes object.
     *              2.a freshly created one if there is none.
     * setAttributes: Specify custom window attributes.
     *    Here we just set some parameters to test if it can set, and the window is just
     *    available in this method. But it's not proper to setAttributes arbitrarily.
     * setCallback: Set the Callback interface for this window. In Window.java,
     *    there is just one method, onWindowAttributesChanged, used.
     * getCallback: Return the current Callback interface for this window.
     */
    @Test
    public void testAccessAttributes() {
        mWindow = new MockWindow(mActivity);

        // default attributes
        WindowManager.LayoutParams attr = mWindow.getAttributes();
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, attr.width);
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, attr.height);
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION, attr.type);
        assertEquals(PixelFormat.OPAQUE, attr.format);

        int width = 200;
        int height = 300;
        WindowManager.LayoutParams param = new WindowManager.LayoutParams(width, height,
                WindowManager.LayoutParams.TYPE_BASE_APPLICATION,
                WindowManager.LayoutParams.FLAG_DITHER, PixelFormat.RGBA_8888);
        mWindow.setCallback(mWindowCallback);
        assertSame(mWindowCallback, mWindow.getCallback());
        verify(mWindowCallback, never()).onWindowAttributesChanged(any());
        mWindow.setAttributes(param);
        attr = mWindow.getAttributes();
        assertEquals(width, attr.width);
        assertEquals(height, attr.height);
        assertEquals(WindowManager.LayoutParams.TYPE_BASE_APPLICATION, attr.type);
        assertEquals(PixelFormat.RGBA_8888, attr.format);
        assertEquals(WindowManager.LayoutParams.FLAG_DITHER, attr.flags);
        verify(mWindowCallback, times(1)).onWindowAttributesChanged(attr);
    }

    /**
     * If not set container, the DecorWindow operates as a top-level window, the mHasChildren of
     * container is false;
     * Otherwise, it will display itself meanwhile container's mHasChildren is true.
     */
    @Test
    public void testAccessContainer() {
        mWindow = new MockWindow(mActivity);
        assertNull(mWindow.getContainer());
        assertFalse(mWindow.hasChildren());

        MockWindow container = new MockWindow(mActivity);
        mWindow.setContainer(container);
        assertSame(container, mWindow.getContainer());
        assertTrue(container.hasChildren());
    }

    /**
     * addContentView: add an additional content view to the screen.
     *    1.Added after any existing ones in the screen.
     *    2.Existing views are NOT removed.
     * getLayoutInflater: Quick access to the {@link LayoutInflater} instance that this Window
     *    retrieved from its Context.
     */
    @UiThreadTest
    @Test
    public void testAddContentView() {
        final ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(VIEWGROUP_LAYOUT_WIDTH,
                VIEWGROUP_LAYOUT_HEIGHT);
        // The LayoutInflater instance will be inflated to a view and used by
        // addContentView,
        // id of this view should be same with inflated id.
        final LayoutInflater inflater = mActivity.getLayoutInflater();
        TextView addedView = (TextView) mWindow.findViewById(R.id.listview_addwindow);
        assertNull(addedView);
        mWindow.addContentView(inflater.inflate(R.layout.windowstub_addlayout, null), lp);
        TextView view = (TextView) mWindow.findViewById(R.id.listview_window);
        addedView = (TextView) mWindow.findViewById(R.id.listview_addwindow);
        assertNotNull(view);
        assertNotNull(addedView);
        assertEquals(R.id.listview_window, view.getId());
        assertEquals(R.id.listview_addwindow, addedView.getId());
    }

    /**
     * getCurrentFocus: Return the view in this Window that currently has focus, or null if
     *                  there are none.
     * The test will be:
     * 1. Set focus view to null, get current focus, it should be null
     * 2. Set listview_window as focus view, get it and compare.
     */
    @UiThreadTest
    @Test
    public void testGetCurrentFocus() {
        TextView v = (TextView) mWindow.findViewById(R.id.listview_window);
        v.clearFocus();
        assertNull(mWindow.getCurrentFocus());

        v.setFocusable(true);
        assertTrue(v.isFocusable());
        assertTrue(v.requestFocus());
        View focus = mWindow.getCurrentFocus();
        assertNotNull(focus);
        assertEquals(R.id.listview_window, focus.getId());
    }

    /**
     * 1. getDecorView() retrieves the top-level window decor view, which contains the standard
     *    window frame/decorations and the client's content inside of that, we should check the
     *    primary components of this view which have no relationship concrete realization of Window.
     *    Therefore we should check if the size of this view equals to the screen size and the
     *    ontext is same as Window's context.
     * 2. Return null if decor view is not created, else the same with detDecorView.
     */
    @Test
    public void testDecorView() {
        mInstrumentation.waitForIdleSync();
        View decor = mWindow.getDecorView();
        assertNotNull(decor);
        verifyDecorView(decor);

        decor = mWindow.peekDecorView();
        if (decor != null) {
            verifyDecorView(decor);
        }
    }

    private void verifyDecorView(View decor) {
        DisplayMetrics dm = new DisplayMetrics();
        mActivity.getWindowManager().getDefaultDisplay().getMetrics(dm);
        int screenWidth = dm.widthPixels;
        int screenHeight = dm.heightPixels;
        assertTrue(decor.getWidth() >= screenWidth);
        assertTrue(decor.getHeight() >= screenHeight);
        assertTrue(decor.getContext() instanceof ContextThemeWrapper);
    }

    /**
     * setVolumeControlStream: Suggests an audio stream whose volume should be changed by
     *    the hardware volume controls.
     * getVolumeControlStream: Gets the suggested audio stream whose volume should be changed by
     *    the harwdare volume controls.
     */
    @Test
    public void testAccessVolumeControlStream() {
        // Default value is AudioManager.USE_DEFAULT_STREAM_TYPE, see javadoc of
        // {@link Activity#setVolumeControlStream}.
        assertEquals(AudioManager.USE_DEFAULT_STREAM_TYPE, mWindow.getVolumeControlStream());
        mWindow.setVolumeControlStream(AudioManager.STREAM_MUSIC);
        assertEquals(AudioManager.STREAM_MUSIC, mWindow.getVolumeControlStream());
    }

    /**
     * setWindowManager: Set the window manager for use by this Window.
     * getWindowManager: Return the window manager allowing this Window to display its own
     *    windows.
     */
    @Test
    public void testAccessWindowManager() {
        mWindow = new MockWindow(mActivity);
        WindowManager expected = (WindowManager) mActivity.getSystemService(
                Context.WINDOW_SERVICE);
        assertNull(mWindow.getWindowManager());
        mWindow.setWindowManager(expected, null,
                mActivity.getApplicationInfo().loadLabel(mActivity.getPackageManager()).toString());
        // No way to compare the expected and actual directly, they are
        // different object
        assertNotNull(mWindow.getWindowManager());
    }

    /**
     * Return the {@link android.R.styleable#Window} attributes from this
     * window's theme. It's invisible.
     */
    @Test
    public void testGetWindowStyle() {
        mWindow = new MockWindow(mActivity);
        final TypedArray windowStyle = mWindow.getWindowStyle();
        // the windowStyle is obtained from
        // com.android.internal.R.styleable.Window whose details
        // are invisible for user.
        assertNotNull(windowStyle);
    }

    @Test
    public void testIsActive() {
        MockWindow window = new MockWindow(mActivity);
        assertFalse(window.isActive());

        window.makeActive();
        assertTrue(window.isActive());
        assertTrue(window.mIsOnActiveCalled);
    }

    /**
     * isFloating: Return whether this window is being displayed with a floating style
     * (based on the {@link android.R.attr#windowIsFloating} attribute in the style/theme).
     */
    @Test
    public void testIsFloating() {
        // Default system theme defined by themes.xml, the windowIsFloating is set false.
        assertFalse(mWindow.isFloating());
    }

    /**
     * Change the background of this window to a custom Drawable.
     * Setting the background to null will make the window be opaque(No way to get the window
     *  attribute of PixelFormat to check if the window is opaque). To make the window
     * transparent, you can use an empty drawable(eg. ColorDrawable with the color 0).
     */
    @Test
    public void testSetBackgroundDrawable() throws Throwable {
        // DecorView holds the background
        View decor = mWindow.getDecorView();
        if (!mWindow.hasFeature(Window.FEATURE_SWIPE_TO_DISMISS)) {
            assertEquals(PixelFormat.OPAQUE, decor.getBackground().getOpacity());
        }
        // setBackgroundDrawableResource(int resId) has the same
        // functionality with setBackgroundDrawable(Drawable drawable), just different in
        // parameter.
        mActivityRule.runOnUiThread(() -> mWindow.setBackgroundDrawableResource(R.drawable.faces));
        mInstrumentation.waitForIdleSync();

        mActivityRule.runOnUiThread(() -> {
            ColorDrawable drawable = new ColorDrawable(0);
            mWindow.setBackgroundDrawable(drawable);
        });
        mInstrumentation.waitForIdleSync();
        decor = mWindow.getDecorView();
        // Color 0 with one alpha bit
        assertEquals(PixelFormat.TRANSPARENT, decor.getBackground().getOpacity());

        mActivityRule.runOnUiThread(() -> mWindow.setBackgroundDrawable(null));
        mInstrumentation.waitForIdleSync();
        decor = mWindow.getDecorView();
        assertNull(decor.getBackground());
    }

    /**
     * setContentView(int): set the screen content from a layout resource.
     * setContentView(View): set the screen content to an explicit view.
     * setContentView(View, LayoutParams): Set the screen content to an explicit view.
     *
     * Note that calling this function "locks in" various characteristics
     * of the window that can not, from this point forward, be changed: the
     * features that have been requested with {@link #requestFeature(int)},
     * and certain window flags as described in {@link #setFlags(int, int)}.
     *   This functionality point is hard to test:
     *   1. can't get the features requested because the getter is protected final.
     *   2. certain window flags are not clear to concrete one.
     */
    @Test
    public void testSetContentView() throws Throwable {
        final ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(VIEWGROUP_LAYOUT_WIDTH,
                VIEWGROUP_LAYOUT_HEIGHT);
        final LayoutInflater inflate = mActivity.getLayoutInflater();

        mActivityRule.runOnUiThread(() -> {
            TextView view;
            View setView;
            // Test setContentView(int layoutResID)
            mWindow.setContentView(R.layout.windowstub_layout);
            view = (TextView) mWindow.findViewById(R.id.listview_window);
            assertNotNull(view);
            assertEquals(R.id.listview_window, view.getId());

            // Test setContentView(View view)
            setView = inflate.inflate(R.layout.windowstub_addlayout, null);
            mWindow.setContentView(setView);
            view = (TextView) mWindow.findViewById(R.id.listview_addwindow);
            assertNotNull(view);
            assertEquals(R.id.listview_addwindow, view.getId());

            // Test setContentView(View view, ViewGroup.LayoutParams params)
            setView = inflate.inflate(R.layout.windowstub_layout, null);
            mWindow.setContentView(setView, lp);
            assertEquals(VIEWGROUP_LAYOUT_WIDTH, setView.getLayoutParams().width);
            assertEquals(VIEWGROUP_LAYOUT_HEIGHT, setView.getLayoutParams().height);
            view = (TextView) mWindow.findViewById(R.id.listview_window);
            assertNotNull(view);
            assertEquals(R.id.listview_window, view.getId());
        });
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testSetTitle() throws Throwable {
        final String title = "Android Window Test";
        mActivityRule.runOnUiThread(() -> {
            mWindow.setTitle(title);
            mWindow.setTitleColor(Color.BLUE);
        });
        mInstrumentation.waitForIdleSync();
        // No way to get title and title color
    }

    /**
     * takeKeyEvents: Request that key events come to this activity. Use this if your activity
     * has no views with focus, but the activity still wants a chance to process key events.
     */
    @Test
    public void testTakeKeyEvents() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            View v = mWindow.findViewById(R.id.listview_window);
            v.clearFocus();
            assertNull(mWindow.getCurrentFocus());
            mWindow.takeKeyEvents(false);
        });
        mInstrumentation.waitForIdleSync();
    }

    /**
     * setDefaultWindowFormat: Set the format of window, as per the PixelFormat types. This
     *    is the format that will be used unless the client specifies in explicit format with
     *    setFormat().
     * setFormat: Set the format of window, as per the PixelFormat types.
     *            param format: The new window format (see PixelFormat).  Use
     *                          PixelFormat.UNKNOWN to allow the Window to select
     *                          the format.
     */
    @Test
    public void testSetDefaultWindowFormat() {
        MockWindow window = new MockWindow(mActivity);

        // mHaveWindowFormat will be true after set PixelFormat.OPAQUE and
        // setDefaultWindowFormat is invalid
        window.setFormat(PixelFormat.OPAQUE);
        window.setCallback(mWindowCallback);
        verify(mWindowCallback, never()).onWindowAttributesChanged(any());
        window.setDefaultWindowFormat(PixelFormat.JPEG);
        assertEquals(PixelFormat.OPAQUE, window.getAttributes().format);
        verify(mWindowCallback, never()).onWindowAttributesChanged(any());

        // mHaveWindowFormat will be false after set PixelFormat.UNKNOWN and
        // setDefaultWindowFormat is valid
        window.setFormat(PixelFormat.UNKNOWN);
        reset(mWindowCallback);
        window.setDefaultWindowFormat(PixelFormat.JPEG);
        assertEquals(PixelFormat.JPEG, window.getAttributes().format);
        verify(mWindowCallback, times(1)).onWindowAttributesChanged(window.getAttributes());
    }

    /**
     * Set the gravity of the window
     */
    @Test
    public void testSetGravity() {
        mWindow = new MockWindow(mActivity);
        WindowManager.LayoutParams attrs = mWindow.getAttributes();
        assertEquals(0, attrs.gravity);

        mWindow.setCallback(mWindowCallback);
        verify(mWindowCallback, never()).onWindowAttributesChanged(any());
        mWindow.setGravity(Gravity.TOP);
        attrs = mWindow.getAttributes();
        assertEquals(Gravity.TOP, attrs.gravity);
        verify(mWindowCallback, times(1)).onWindowAttributesChanged(attrs);
    }

    /**
     * Set the width and height layout parameters of the window.
     *    1.The default for both of these is MATCH_PARENT;
     *    2.You can change them to WRAP_CONTENT to make a window that is not full-screen.
     */
    @Test
    public void testSetLayout() {
        mWindow = new MockWindow(mActivity);
        WindowManager.LayoutParams attrs = mWindow.getAttributes();
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, attrs.width);
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, attrs.height);

        mWindow.setCallback(mWindowCallback);
        verify(mWindowCallback, never()).onWindowAttributesChanged(any());
        mWindow.setLayout(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT);
        attrs = mWindow.getAttributes();
        assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, attrs.width);
        assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, attrs.height);
        verify(mWindowCallback, times(1)).onWindowAttributesChanged(attrs);
    }

    /**
     * Set the type of the window, as per the WindowManager.LayoutParams types.
     */
    @Test
    public void testSetType() {
        mWindow = new MockWindow(mActivity);
        WindowManager.LayoutParams attrs = mWindow.getAttributes();
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION, attrs.type);

        mWindow.setCallback(mWindowCallback);
        verify(mWindowCallback, never()).onWindowAttributesChanged(any());
        mWindow.setType(WindowManager.LayoutParams.TYPE_BASE_APPLICATION);
        attrs = mWindow.getAttributes();
        assertEquals(WindowManager.LayoutParams.TYPE_BASE_APPLICATION, attrs.type);
        verify(mWindowCallback, times(1)).onWindowAttributesChanged(attrs);
    }

    /**
     * Specify an explicit soft input mode to use for the window, as per
     * WindowManager.LayoutParams#softInputMode.
     *    1.Providing "unspecified" here will NOT override the input mode the window.
     *    2.Providing "unspecified" here will override the input mode the window.
     */
    @Test
    public void testSetSoftInputMode() {
        mWindow = new MockWindow(mActivity);
        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED,
                mWindow.getAttributes().softInputMode);
        mWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE,
                mWindow.getAttributes().softInputMode);
        mWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE,
                mWindow.getAttributes().softInputMode);
    }

    /**
     * Specify custom animations to use for the window, as per
     * WindowManager.LayoutParams#windowAnimations.
     *    1.Providing 0 here will NOT override the animations the window(But here we can't check
     *    it because the getter is in WindowManagerService and is private)
     *    2.Providing 0 here will override the animations the window.
     */
    @Test
    public void testSetWindowAnimations() {
        mWindow = new MockWindow(mActivity);

        mWindow.setCallback(mWindowCallback);
        verify(mWindowCallback, never()).onWindowAttributesChanged(any());
        mWindow.setWindowAnimations(R.anim.alpha);
        WindowManager.LayoutParams attrs = mWindow.getAttributes();
        assertEquals(R.anim.alpha, attrs.windowAnimations);
        verify(mWindowCallback, times(1)).onWindowAttributesChanged(attrs);
    }

    /**
     * Test setLocalFocus together with injectInputEvent.
     */
    @Test
    public void testSetLocalFocus() throws Throwable {
        mActivityRule.runOnUiThread(() -> mSurfaceView = new SurfaceView(mActivity));
        mInstrumentation.waitForIdleSync();

        final Semaphore waitingSemaphore = new Semaphore(0);
        mSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                destroyPresentation();
                createPresentation(holder.getSurface(), width, height);
                waitingSemaphore.release();
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                destroyPresentation();
            }
        });
        mActivityRule.runOnUiThread(() -> mWindow.setContentView(mSurfaceView));
        mInstrumentation.waitForIdleSync();
        assertTrue(waitingSemaphore.tryAcquire(5, TimeUnit.SECONDS));
        assertNotNull(mVirtualDisplay);
        assertNotNull(mPresentation);

        PollingCheck.waitFor(() -> (mPresentation.button1 != null)
                && (mPresentation.button2 != null) && (mPresentation.button3 != null)
                && mPresentation.ready);
        assertTrue(mPresentation.button1.isFocusable() && mPresentation.button2.isFocusable() &&
                mPresentation.button3.isFocusable());
        // currently it is only for debugging
        View.OnFocusChangeListener listener = (View v, boolean hasFocus) ->
                Log.d(TAG, "view " + v + " focus " + hasFocus);

        // check key event focus
        mPresentation.button1.setOnFocusChangeListener(listener);
        mPresentation.button2.setOnFocusChangeListener(listener);
        mPresentation.button3.setOnFocusChangeListener(listener);
        final Window presentationWindow = mPresentation.getWindow();
        presentationWindow.setLocalFocus(true, false);
        PollingCheck.waitFor(() -> mPresentation.button1.hasWindowFocus());
        checkPresentationButtonFocus(true, false, false);
        assertFalse(mPresentation.button1.isInTouchMode());
        injectKeyEvent(presentationWindow, KeyEvent.KEYCODE_TAB);
        checkPresentationButtonFocus(false, true, false);
        injectKeyEvent(presentationWindow, KeyEvent.KEYCODE_TAB);
        checkPresentationButtonFocus(false, false, true);

        // check touch input injection
        presentationWindow.setLocalFocus(true, true);
        PollingCheck.waitFor(() -> mPresentation.button1.isInTouchMode());
        View.OnClickListener clickListener = (View v) -> {
            Log.d(TAG, "onClick " + v);
            if (v == mPresentation.button1) {
                waitingSemaphore.release();
            }
        };
        mPresentation.button1.setOnClickListener(clickListener);
        mPresentation.button2.setOnClickListener(clickListener);
        mPresentation.button3.setOnClickListener(clickListener);
        injectTouchEvent(presentationWindow, mPresentation.button1.getX() +
                mPresentation.button1.getWidth() / 2,
                mPresentation.button1.getY() + mPresentation.button1.getHeight() / 2);
        assertTrue(waitingSemaphore.tryAcquire(5, TimeUnit.SECONDS));

        destroyPresentation();
    }

    private void checkPresentationButtonFocus(final boolean button1Focused,
            final boolean button2Focused, final boolean button3Focused) {
        PollingCheck.waitFor(() -> (mPresentation.button1.isFocused() == button1Focused) &&
                        (mPresentation.button2.isFocused() == button2Focused) &&
                        (mPresentation.button3.isFocused() == button3Focused));
    }

    private void injectKeyEvent(Window window, int keyCode) {
        KeyEvent downEvent = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
        window.injectInputEvent(downEvent);
        KeyEvent upEvent = new KeyEvent(KeyEvent.ACTION_UP, keyCode);
        window.injectInputEvent(upEvent);
    }

    private void injectTouchEvent(Window window, float x, float y) {
        Log.d(TAG, "injectTouchEvent " + x + "," + y);
        long downTime = SystemClock.uptimeMillis();
        MotionEvent downEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN,
                x, y, 0);
        downEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        window.injectInputEvent(downEvent);
        long upTime = SystemClock.uptimeMillis();
        MotionEvent upEvent = MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP,
                x, y, 0);
        upEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        window.injectInputEvent(upEvent);
    }

    private void createPresentation(final Surface surface, final int width,
            final int height) {
        DisplayManager displayManager =
                (DisplayManager) mActivity.getSystemService(Context.DISPLAY_SERVICE);
        mVirtualDisplay = displayManager.createVirtualDisplay("localFocusTest",
                width, height, 300, surface, 0);
        mPresentation = new ProjectedPresentation(mActivity, mVirtualDisplay.getDisplay());
        mPresentation.show();
    }

    private void destroyPresentation() {
        if (mPresentation != null) {
            mPresentation.dismiss();
        }
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
        }
    }

    private class ProjectedPresentation extends Presentation {
        public Button button1 = null;
        public Button button2 = null;
        public Button button3 = null;
        public volatile boolean ready = false;

        public ProjectedPresentation(Context outerContext, Display display) {
            super(outerContext, display);
            getWindow().setType(WindowManager.LayoutParams.TYPE_PRIVATE_PRESENTATION);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_LOCAL_FOCUS_MODE);
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.windowstub_presentation);
            button1 = (Button) findViewById(R.id.presentation_button1);
            button2 = (Button) findViewById(R.id.presentation_button2);
            button3 = (Button) findViewById(R.id.presentation_button3);
        }

        @Override
        public void show() {
            super.show();
            new Handler().post(() -> ready = true);
        }
    }

    public class MockWindow extends Window {
        public boolean mIsOnConfigurationChangedCalled = false;
        public boolean mIsOnActiveCalled = false;

        public MockWindow(Context context) {
            super(context);
        }

        public boolean isFloating() {
            return false;
        }

        public void setContentView(int layoutResID) {
        }

        public void setContentView(View view) {
        }

        public void setContentView(View view, ViewGroup.LayoutParams params) {
        }

        public void addContentView(View view, ViewGroup.LayoutParams params) {
        }

        public void clearContentView() {
        }

        public View getCurrentFocus() {
            return null;
        }

        public LayoutInflater getLayoutInflater() {
            return null;
        }

        public void setTitle(CharSequence title) {
        }

        public void setTitleColor(int textColor) {
        }

        public void openPanel(int featureId, KeyEvent event) {
        }

        public void closePanel(int featureId) {
        }

        public void togglePanel(int featureId, KeyEvent event) {
        }

        public void invalidatePanelMenu(int featureId) {
        }

        public boolean performPanelShortcut(int featureId, int keyCode, KeyEvent event, int flags) {
            return true;
        }

        public boolean performPanelIdentifierAction(int featureId, int id, int flags) {
            return true;
        }

        public void closeAllPanels() {
        }

        public boolean performContextMenuIdentifierAction(int id, int flags) {
            return true;
        }

        public void onConfigurationChanged(Configuration newConfig) {
            mIsOnConfigurationChangedCalled = true;
        }

        public void setBackgroundDrawable(Drawable drawable) {
        }

        public void setFeatureDrawableResource(int featureId, int resId) {
        }

        public void setFeatureDrawableUri(int featureId, Uri uri) {
        }

        public void setFeatureDrawable(int featureId, Drawable drawable) {
        }

        public void setFeatureDrawableAlpha(int featureId, int alpha) {
        }

        public void setFeatureInt(int featureId, int value) {
        }

        public void takeKeyEvents(boolean get) {
        }

        public boolean superDispatchKeyEvent(KeyEvent event) {
            return true;
        }

        public boolean superDispatchKeyShortcutEvent(KeyEvent event) {
            return false;
        }

        public boolean superDispatchTouchEvent(MotionEvent event) {
            return true;
        }

        public boolean superDispatchTrackballEvent(MotionEvent event) {
            return true;
        }

        public boolean superDispatchGenericMotionEvent(MotionEvent event) {
            return true;
        }

        public View getDecorView() {
            return null;
        }

        public void alwaysReadCloseOnTouchAttr() {
        }

        public View peekDecorView() {
            return null;
        }

        public Bundle saveHierarchyState() {
            return null;
        }

        public void restoreHierarchyState(Bundle savedInstanceState) {
        }

        protected void onActive() {
            mIsOnActiveCalled = true;
        }

        public void setChildDrawable(int featureId, Drawable drawable) {

        }

        public void setChildInt(int featureId, int value) {
        }

        public boolean isShortcutKey(int keyCode, KeyEvent event) {
            return false;
        }

        public void setVolumeControlStream(int streamType) {
        }

        public int getVolumeControlStream() {
            return 0;
        }

        public void setDefaultWindowFormatFake(int format) {
            super.setDefaultWindowFormat(format);
        }

        @Override
        public void setDefaultWindowFormat(int format) {
            super.setDefaultWindowFormat(format);
        }

        @Override
        public void takeSurface(SurfaceHolder.Callback2 callback) {
        }

        @Override
        public void takeInputQueue(InputQueue.Callback callback) {
        }

        @Override
        public void setStatusBarColor(int color) {
        }

        @Override
        public int getStatusBarColor() {
            return 0;
        }

        @Override
        public void setNavigationBarColor(int color) {
        }

        @Override
        public void setDecorCaptionShade(int decorCaptionShade) {

        }

        @Override
        public void setResizingCaptionDrawable(Drawable drawable) {

        }

        @Override
        public int getNavigationBarColor() {
            return 0;
        }

        @Override
        public void onMultiWindowModeChanged() {
        }

        @Override
        public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        }

        @Override
        public void reportActivityRelaunched() {
        }
    }
}
