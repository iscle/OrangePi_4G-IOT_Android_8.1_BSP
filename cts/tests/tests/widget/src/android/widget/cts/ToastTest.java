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

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.LargeTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class ToastTest {
    private static final String TEST_TOAST_TEXT = "test toast";
    private static final long TIME_FOR_UI_OPERATION  = 1000L;
    private static final long TIME_OUT = 5000L;
    private Toast mToast;
    private Context mContext;
    private Instrumentation mInstrumentation;
    private boolean mLayoutDone;
    private ViewTreeObserver.OnGlobalLayoutListener mLayoutListener;

    @Rule
    public ActivityTestRule<CtsActivity> mActivityRule =
            new ActivityTestRule<>(CtsActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = InstrumentationRegistry.getTargetContext();
        mLayoutListener = () -> mLayoutDone = true;
    }

    @UiThreadTest
    @Test
    public void testConstructor() {
        new Toast(mContext);
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testConstructorNullContext() {
        new Toast(null);
    }

    private static void assertShowToast(final View view) {
        PollingCheck.waitFor(TIME_OUT, () -> null != view.getParent());
    }

    private static void assertShowAndHide(final View view) {
        assertShowToast(view);
        PollingCheck.waitFor(TIME_OUT, () -> null == view.getParent());
    }

    private static void assertNotShowToast(final View view) {
        // sleep a while and then make sure do not show toast
        SystemClock.sleep(TIME_FOR_UI_OPERATION);
        assertNull(view.getParent());
    }

    private void registerLayoutListener(final View view) {
        mLayoutDone = false;
        view.getViewTreeObserver().addOnGlobalLayoutListener(mLayoutListener);
    }

    private void assertLayoutDone(final View view) {
        PollingCheck.waitFor(TIME_OUT, () -> mLayoutDone);
        view.getViewTreeObserver().removeOnGlobalLayoutListener(mLayoutListener);
    }

    private void makeToast() throws Throwable {
        mActivityRule.runOnUiThread(
                () -> mToast = Toast.makeText(mContext, TEST_TOAST_TEXT, Toast.LENGTH_LONG));
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testShow() throws Throwable {
        makeToast();

        final View view = mToast.getView();

        // view has not been attached to screen yet
        assertNull(view.getParent());
        assertEquals(View.VISIBLE, view.getVisibility());

        mActivityRule.runOnUiThread(mToast::show);
        mInstrumentation.waitForIdleSync();

        // view will be attached to screen when show it
        assertEquals(View.VISIBLE, view.getVisibility());
        assertShowToast(view);
    }

    @UiThreadTest
    @Test(expected=RuntimeException.class)
    public void testShowFailure() {
        Toast toast = new Toast(mContext);
        // do not have any views.
        assertNull(toast.getView());
        toast.show();
    }

    @Test
    public void testCancel() throws Throwable {
        makeToast();

        final View view = mToast.getView();

        // view has not been attached to screen yet
        assertNull(view.getParent());
        mActivityRule.runOnUiThread(() -> {
            mToast.show();
            mToast.cancel();
        });
        mInstrumentation.waitForIdleSync();

        assertNotShowToast(view);
    }

    @Test
    public void testAccessView() throws Throwable {
        makeToast();
        assertFalse(mToast.getView() instanceof ImageView);

        final ImageView imageView = new ImageView(mContext);
        Drawable drawable = mContext.getResources().getDrawable(R.drawable.pass);
        imageView.setImageDrawable(drawable);

        mActivityRule.runOnUiThread(() -> {
            mToast.setView(imageView);
            mToast.show();
        });
        mInstrumentation.waitForIdleSync();
        assertSame(imageView, mToast.getView());
        assertShowAndHide(imageView);
    }

    @Test
    public void testAccessDuration() throws Throwable {
        long start = SystemClock.uptimeMillis();
        makeToast();
        mActivityRule.runOnUiThread(mToast::show);
        mInstrumentation.waitForIdleSync();
        assertEquals(Toast.LENGTH_LONG, mToast.getDuration());

        View view = mToast.getView();
        assertShowAndHide(view);
        long longDuration = SystemClock.uptimeMillis() - start;

        start = SystemClock.uptimeMillis();
        mActivityRule.runOnUiThread(() -> {
            mToast.setDuration(Toast.LENGTH_SHORT);
            mToast.show();
        });
        mInstrumentation.waitForIdleSync();
        assertEquals(Toast.LENGTH_SHORT, mToast.getDuration());

        view = mToast.getView();
        assertShowAndHide(view);
        long shortDuration = SystemClock.uptimeMillis() - start;

        assertTrue(longDuration > shortDuration);
    }

    @Test
    public void testAccessMargin() throws Throwable {
        makeToast();
        View view = mToast.getView();
        assertFalse(view.getLayoutParams() instanceof WindowManager.LayoutParams);

        final float horizontal1 = 1.0f;
        final float vertical1 = 1.0f;
        mActivityRule.runOnUiThread(() -> {
            mToast.setMargin(horizontal1, vertical1);
            mToast.show();
            registerLayoutListener(mToast.getView());
        });
        mInstrumentation.waitForIdleSync();
        assertShowToast(view);

        assertEquals(horizontal1, mToast.getHorizontalMargin(), 0.0f);
        assertEquals(vertical1, mToast.getVerticalMargin(), 0.0f);
        WindowManager.LayoutParams params1 = (WindowManager.LayoutParams) view.getLayoutParams();
        assertEquals(horizontal1, params1.horizontalMargin, 0.0f);
        assertEquals(vertical1, params1.verticalMargin, 0.0f);
        assertLayoutDone(view);
        int[] xy1 = new int[2];
        view.getLocationOnScreen(xy1);
        assertShowAndHide(view);

        final float horizontal2 = 0.1f;
        final float vertical2 = 0.1f;
        mActivityRule.runOnUiThread(() -> {
            mToast.setMargin(horizontal2, vertical2);
            mToast.show();
            registerLayoutListener(mToast.getView());
        });
        mInstrumentation.waitForIdleSync();
        assertShowToast(view);

        assertEquals(horizontal2, mToast.getHorizontalMargin(), 0.0f);
        assertEquals(vertical2, mToast.getVerticalMargin(), 0.0f);
        WindowManager.LayoutParams params2 = (WindowManager.LayoutParams) view.getLayoutParams();
        assertEquals(horizontal2, params2.horizontalMargin, 0.0f);
        assertEquals(vertical2, params2.verticalMargin, 0.0f);

        assertLayoutDone(view);
        int[] xy2 = new int[2];
        view.getLocationOnScreen(xy2);
        assertShowAndHide(view);

        assertTrue(xy1[0] > xy2[0]);
        assertTrue(xy1[1] < xy2[1]);
    }

    @Test
    public void testAccessGravity() throws Throwable {
        makeToast();
        mActivityRule.runOnUiThread(() -> {
            mToast.setGravity(Gravity.CENTER, 0, 0);
            mToast.show();
            registerLayoutListener(mToast.getView());
        });
        mInstrumentation.waitForIdleSync();
        View view = mToast.getView();
        assertShowToast(view);
        assertEquals(Gravity.CENTER, mToast.getGravity());
        assertEquals(0, mToast.getXOffset());
        assertEquals(0, mToast.getYOffset());
        assertLayoutDone(view);
        int[] centerXY = new int[2];
        view.getLocationOnScreen(centerXY);
        assertShowAndHide(view);

        mActivityRule.runOnUiThread(() -> {
            mToast.setGravity(Gravity.BOTTOM, 0, 0);
            mToast.show();
            registerLayoutListener(mToast.getView());
        });
        mInstrumentation.waitForIdleSync();
        view = mToast.getView();
        assertShowToast(view);
        assertEquals(Gravity.BOTTOM, mToast.getGravity());
        assertEquals(0, mToast.getXOffset());
        assertEquals(0, mToast.getYOffset());
        assertLayoutDone(view);
        int[] bottomXY = new int[2];
        view.getLocationOnScreen(bottomXY);
        assertShowAndHide(view);

        // x coordinate is the same
        assertEquals(centerXY[0], bottomXY[0]);
        // bottom view is below of center view
        assertTrue(centerXY[1] < bottomXY[1]);

        final int xOffset = 20;
        final int yOffset = 10;
        mActivityRule.runOnUiThread(() -> {
            mToast.setGravity(Gravity.BOTTOM, xOffset, yOffset);
            mToast.show();
            registerLayoutListener(mToast.getView());
        });
        mInstrumentation.waitForIdleSync();
        view = mToast.getView();
        assertShowToast(view);
        assertEquals(Gravity.BOTTOM, mToast.getGravity());
        assertEquals(xOffset, mToast.getXOffset());
        assertEquals(yOffset, mToast.getYOffset());
        assertLayoutDone(view);
        int[] bottomOffsetXY = new int[2];
        view.getLocationOnScreen(bottomOffsetXY);
        assertShowAndHide(view);

        assertEquals(bottomXY[0] + xOffset, bottomOffsetXY[0]);
        assertEquals(bottomXY[1] - yOffset, bottomOffsetXY[1]);
    }

    @UiThreadTest
    @Test
    public void testMakeTextFromString() {
        Toast toast = Toast.makeText(mContext, "android", Toast.LENGTH_SHORT);
        assertNotNull(toast);
        assertEquals(Toast.LENGTH_SHORT, toast.getDuration());
        View view = toast.getView();
        assertNotNull(view);

        toast = Toast.makeText(mContext, "cts", Toast.LENGTH_LONG);
        assertNotNull(toast);
        assertEquals(Toast.LENGTH_LONG, toast.getDuration());
        view = toast.getView();
        assertNotNull(view);

        toast = Toast.makeText(mContext, null, Toast.LENGTH_LONG);
        assertNotNull(toast);
        assertEquals(Toast.LENGTH_LONG, toast.getDuration());
        view = toast.getView();
        assertNotNull(view);
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testMaketTextFromStringNullContext() {
        Toast.makeText(null, "test", Toast.LENGTH_LONG);
    }

    @UiThreadTest
    @Test
    public void testMakeTextFromResource() {
        Toast toast = Toast.makeText(mContext, R.string.hello_world, Toast.LENGTH_LONG);

        assertNotNull(toast);
        assertEquals(Toast.LENGTH_LONG, toast.getDuration());
        View view = toast.getView();
        assertNotNull(view);

        toast = Toast.makeText(mContext, R.string.hello_android, Toast.LENGTH_SHORT);
        assertNotNull(toast);
        assertEquals(Toast.LENGTH_SHORT, toast.getDuration());
        view = toast.getView();
        assertNotNull(view);
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testMaketTextFromResourceNullContext() {
        Toast.makeText(null, R.string.hello_android, Toast.LENGTH_SHORT);
    }

    @UiThreadTest
    @Test
    public void testSetTextFromResource() {
        Toast toast = Toast.makeText(mContext, R.string.text, Toast.LENGTH_LONG);

        toast.setText(R.string.hello_world);
        // TODO: how to getText to assert?

        toast.setText(R.string.hello_android);
        // TODO: how to getText to assert?
    }

    @UiThreadTest
    @Test(expected=RuntimeException.class)
    public void testSetTextFromInvalidResource() {
        Toast toast = Toast.makeText(mContext, R.string.text, Toast.LENGTH_LONG);
        toast.setText(-1);
    }

    @UiThreadTest
    @Test
    public void testSetTextFromString() {
        Toast toast = Toast.makeText(mContext, R.string.text, Toast.LENGTH_LONG);

        toast.setText("cts");
        // TODO: how to getText to assert?

        toast.setText("android");
        // TODO: how to getText to assert?
    }

    @UiThreadTest
    @Test(expected=RuntimeException.class)
    public void testSetTextFromStringNullView() {
        Toast toast = Toast.makeText(mContext, R.string.text, Toast.LENGTH_LONG);
        toast.setView(null);
        toast.setText(null);
    }
}
