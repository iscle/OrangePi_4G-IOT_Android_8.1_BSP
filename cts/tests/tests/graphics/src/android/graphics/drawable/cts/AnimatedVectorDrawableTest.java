/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.graphics.drawable.cts;

import static junit.framework.Assert.fail;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.cts.R;
import android.graphics.drawable.Animatable2;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Drawable.ConstantState;
import android.support.test.filters.LargeTest;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.AttributeSet;
import android.util.Xml;
import android.widget.ImageView;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class AnimatedVectorDrawableTest {
    private static final int IMAGE_WIDTH = 64;
    private static final int IMAGE_HEIGHT = 64;
    private static final long MAX_TIMEOUT_MS = 1000;
    private static final long MAX_START_TIMEOUT_MS = 5000;
    private static final int MS_TO_NS = 1000000;

    @Rule
    public ActivityTestRule<DrawableStubActivity> mActivityRule =
            new ActivityTestRule<DrawableStubActivity>(DrawableStubActivity.class);
    private Activity mActivity;
    private Resources mResources;
    private static final boolean DBG_DUMP_PNG = false;
    private final int mResId = R.drawable.animation_vector_drawable_grouping_1;
    private final int mLayoutId = R.layout.animated_vector_drawable_source;
    private final int mImageViewId = R.id.avd_view;

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
        mResources = mActivity.getResources();
    }

    @SmallTest
    @Test
    public void testInflate() throws Exception {
        // Setup AnimatedVectorDrawable from xml file
        XmlPullParser parser = mResources.getXml(mResId);
        AttributeSet attrs = Xml.asAttributeSet(parser);

        int type;
        while ((type=parser.next()) != XmlPullParser.START_TAG &&
                type != XmlPullParser.END_DOCUMENT) {
            // Empty loop
        }

        if (type != XmlPullParser.START_TAG) {
            throw new XmlPullParserException("No start tag found");
        }
        Bitmap bitmap = Bitmap.createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        AnimatedVectorDrawable drawable = new AnimatedVectorDrawable();
        drawable.inflate(mResources, parser, attrs);
        drawable.setBounds(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
        bitmap.eraseColor(0);
        drawable.draw(canvas);
        int sunColor = bitmap.getPixel(IMAGE_WIDTH / 2, IMAGE_HEIGHT / 2);
        int earthColor = bitmap.getPixel(IMAGE_WIDTH * 3 / 4 + 2, IMAGE_HEIGHT / 2);
        assertTrue(sunColor == 0xFFFF8000);
        assertTrue(earthColor == 0xFF5656EA);

        if (DBG_DUMP_PNG) {
            DrawableTestUtils.saveAutoNamedVectorDrawableIntoPNG(mActivity, bitmap, mResId, null);
        }
    }

    @Test
    public void testGetChangingConfigurations() {
        AnimatedVectorDrawable avd = new AnimatedVectorDrawable();
        ConstantState constantState = avd.getConstantState();

        // default
        assertEquals(0, constantState.getChangingConfigurations());
        assertEquals(0, avd.getChangingConfigurations());

        // change the drawable's configuration does not affect the state's configuration
        avd.setChangingConfigurations(0xff);
        assertEquals(0xff, avd.getChangingConfigurations());
        assertEquals(0, constantState.getChangingConfigurations());

        // the state's configuration get refreshed
        constantState = avd.getConstantState();
        assertEquals(0xff,  constantState.getChangingConfigurations());

        // set a new configuration to drawable
        avd.setChangingConfigurations(0xff00);
        assertEquals(0xff,  constantState.getChangingConfigurations());
        assertEquals(0xffff,  avd.getChangingConfigurations());
    }

    @Test
    public void testGetConstantState() {
        AnimatedVectorDrawable AnimatedVectorDrawable = new AnimatedVectorDrawable();
        ConstantState constantState = AnimatedVectorDrawable.getConstantState();
        assertNotNull(constantState);
        assertEquals(0, constantState.getChangingConfigurations());

        AnimatedVectorDrawable.setChangingConfigurations(1);
        constantState = AnimatedVectorDrawable.getConstantState();
        assertNotNull(constantState);
        assertEquals(1, constantState.getChangingConfigurations());
    }

    @SmallTest
    @Test
    public void testMutate() {
        AnimatedVectorDrawable d1 = (AnimatedVectorDrawable) mResources.getDrawable(mResId);
        AnimatedVectorDrawable d2 = (AnimatedVectorDrawable) mResources.getDrawable(mResId);
        AnimatedVectorDrawable d3 = (AnimatedVectorDrawable) mResources.getDrawable(mResId);
        int restoreAlpha = d1.getAlpha();

        try {
            int originalAlpha = d2.getAlpha();
            int newAlpha = (originalAlpha + 1) % 255;

            // AVD is different than VectorDrawable. Every instance of it is a deep copy
            // of the VectorDrawable.
            // So every setAlpha operation will happen only to that specific object.
            d1.setAlpha(newAlpha);
            assertEquals(newAlpha, d1.getAlpha());
            assertEquals(originalAlpha, d2.getAlpha());
            assertEquals(originalAlpha, d3.getAlpha());

            d1.mutate();
            d1.setAlpha(0x40);
            assertEquals(0x40, d1.getAlpha());
            assertEquals(originalAlpha, d2.getAlpha());
            assertEquals(originalAlpha, d3.getAlpha());

            d2.setAlpha(0x20);
            assertEquals(0x40, d1.getAlpha());
            assertEquals(0x20, d2.getAlpha());
            assertEquals(originalAlpha, d3.getAlpha());
        } finally {
            mResources.getDrawable(mResId).setAlpha(restoreAlpha);
        }
    }

    @SmallTest
    @Test
    public void testGetOpacity() {
        AnimatedVectorDrawable d1 = (AnimatedVectorDrawable) mResources.getDrawable(mResId);
        assertEquals("Default is translucent", PixelFormat.TRANSLUCENT, d1.getOpacity());
        d1.setAlpha(0);
        assertEquals("Still translucent", PixelFormat.TRANSLUCENT, d1.getOpacity());
    }

    @SmallTest
    @Test
    public void testColorFilter() {
        PorterDuffColorFilter filter = new PorterDuffColorFilter(Color.RED, PorterDuff.Mode.SRC_IN);
        AnimatedVectorDrawable d1 = (AnimatedVectorDrawable) mResources.getDrawable(mResId);
        d1.setColorFilter(filter);

        assertEquals(filter, d1.getColorFilter());
    }

    @Test
    public void testReset() throws Throwable {
        final MyCallback callback = new MyCallback();
        final AnimatedVectorDrawable d1 = (AnimatedVectorDrawable) mResources.getDrawable(mResId);
        // The AVD has a duration as 100ms.
        mActivityRule.runOnUiThread(() -> {
            d1.registerAnimationCallback(callback);
            d1.start();
            d1.reset();
        });
        waitForAVDStop(callback, MAX_TIMEOUT_MS);
        assertFalse(d1.isRunning());

    }

    @Test
    public void testStop() throws Throwable {
        final MyCallback callback = new MyCallback();
        final AnimatedVectorDrawable d1 = (AnimatedVectorDrawable) mResources.getDrawable(mResId);
        // The AVD has a duration as 100ms.
        mActivityRule.runOnUiThread(() -> {
            d1.registerAnimationCallback(callback);
            d1.start();
            d1.stop();
        });
        waitForAVDStop(callback, MAX_TIMEOUT_MS);
        assertFalse(d1.isRunning());
    }

    @Test
    public void testAddCallbackBeforeStart() throws Throwable {
        final MyCallback callback = new MyCallback();
        // The AVD has a duration as 100ms.
        mActivityRule.runOnUiThread(() -> {
            mActivity.setContentView(mLayoutId);
            ImageView imageView = (ImageView) mActivity.findViewById(mImageViewId);
            AnimatedVectorDrawable d1 = (AnimatedVectorDrawable) imageView.getDrawable();
            d1.registerAnimationCallback(callback);
            d1.start();
        });
        callback.waitForStart();
        waitForAVDStop(callback, MAX_TIMEOUT_MS);
        callback.assertStarted(true);
        callback.assertEnded(true);
    }

    @Test
    public void testAddCallbackAfterTrigger() throws Throwable {
        final MyCallback callback = new MyCallback();
        // The AVD has a duration as 100ms.
        mActivityRule.runOnUiThread(() -> {
            mActivity.setContentView(mLayoutId);
            ImageView imageView = (ImageView) mActivity.findViewById(mImageViewId);
            AnimatedVectorDrawable d1 = (AnimatedVectorDrawable) imageView.getDrawable();
            // This reset call can enforce the AnimatorSet is setup properly in AVD, when
            // running on UI thread.
            d1.reset();
            d1.registerAnimationCallback(callback);
            d1.start();
        });
        callback.waitForStart();
        waitForAVDStop(callback, MAX_TIMEOUT_MS);

        callback.assertStarted(true);
        callback.assertEnded(true);
    }

    @Test
    public void testAddCallbackAfterStart() throws Throwable {
        final MyCallback callback = new MyCallback();
        // The AVD has a duration as 100ms.
        mActivityRule.runOnUiThread(() -> {
            mActivity.setContentView(mLayoutId);
            ImageView imageView = (ImageView) mActivity.findViewById(mImageViewId);
            AnimatedVectorDrawable d1 = (AnimatedVectorDrawable) imageView.getDrawable();
            d1.start();
            d1.registerAnimationCallback(callback);
        });
        callback.waitForStart();

        waitForAVDStop(callback, MAX_TIMEOUT_MS);
        // Whether or not the callback.start is true could vary when running on Render Thread.
        // Therefore, we don't make assertion here. The most useful flag is the callback.mEnded.
        callback.assertEnded(true);
        callback.assertAVDRuntime(0, 400 * MS_TO_NS); // 4 times of the duration of the AVD.
    }

    @Test
    public void testRemoveCallback() throws Throwable {
        final MyCallback callback = new MyCallback();
        // The AVD has a duration as 100ms.
        mActivityRule.runOnUiThread(() -> {
            mActivity.setContentView(mLayoutId);
            ImageView imageView = (ImageView) mActivity.findViewById(mImageViewId);
            AnimatedVectorDrawable d1 = (AnimatedVectorDrawable) imageView.getDrawable();
            d1.registerAnimationCallback(callback);
            assertTrue(d1.unregisterAnimationCallback(callback));
            d1.start();
        });
        callback.waitForStart();

        waitForAVDStop(callback, MAX_TIMEOUT_MS);
        callback.assertStarted(false);
        callback.assertEnded(false);
    }

    @Test
    public void testClearCallback() throws Throwable {
        final MyCallback callback = new MyCallback();

        // The AVD has a duration as 100ms.
        mActivityRule.runOnUiThread(() -> {
            mActivity.setContentView(mLayoutId);
            ImageView imageView = (ImageView) mActivity.findViewById(mImageViewId);
            AnimatedVectorDrawable d1 = (AnimatedVectorDrawable) imageView.getDrawable();
            d1.registerAnimationCallback(callback);
            d1.clearAnimationCallbacks();
            d1.start();
        });
        callback.waitForStart();

        waitForAVDStop(callback, MAX_TIMEOUT_MS);
        callback.assertStarted(false);
        callback.assertEnded(false);
    }

    // The time out is expected when the listener is removed successfully.
    // Such that we don't get the end event.
    static void waitForAVDStop(MyCallback callback, long timeout) {
        try {
            callback.waitForEnd(timeout);
        } catch (InterruptedException e) {
            e.printStackTrace();
            fail("We should not see the AVD run this long time!");
        }
    }

    // Now this class can not only listen to the events, but also synchronize the key events,
    // logging the event timestamp, and centralize some assertions.
    static class MyCallback extends Animatable2.AnimationCallback {
        private boolean mStarted = false;
        private boolean mEnded = false;

        private long mStartNs = Long.MAX_VALUE;
        private long mEndNs = Long.MIN_VALUE;

        // Use this lock to make sure the onAnimationEnd() has been called.
        // Each sub test should have its own lock.
        private final Object mEndLock = new Object();

        // Use this lock to make sure the test thread know when the AVD.start() has been called.
        // Each sub test should have its own lock.
        private final Object mStartLock = new Object();

        public boolean waitForEnd(long timeoutMs) throws InterruptedException {
            synchronized (mEndLock) {
                if (!mEnded) {
                    // Return immediately if the AVD has already ended.
                    mEndLock.wait(timeoutMs);
                }
                return mEnded;
            }
        }

        public boolean waitForStart() throws InterruptedException {
            synchronized(mStartLock) {
                if (!mStarted) {
                    // Return immediately if the AVD has already started.
                    mStartLock.wait(MAX_START_TIMEOUT_MS);
                }
                return mStarted;
            }
        }

        @Override
        public void onAnimationStart(Drawable drawable) {
            mStartNs = System.nanoTime();
            synchronized(mStartLock) {
                mStarted = true;
                mStartLock.notify();
            }
        }

        @Override
        public void onAnimationEnd(Drawable drawable) {
            mEndNs = System.nanoTime();
            synchronized (mEndLock) {
                mEnded = true;
                mEndLock.notify();
            }
        }

        public boolean endIsCalled() {
            synchronized (mEndLock) {
                return mEnded;
            }
        }

        public void assertStarted(boolean started) {
            assertEquals(started, mStarted);
        }

        public void assertEnded(boolean ended) {
            assertEquals(ended, mEnded);
        }

        public void assertAVDRuntime(long min, long max) {
            assertTrue(mStartNs != Long.MAX_VALUE);
            assertTrue(mEndNs != Long.MIN_VALUE);
            long durationNs = mEndNs - mStartNs;
            assertTrue("current duration " + durationNs + " should be within " +
                    min + "," + max, durationNs <= max && durationNs >= min);
        }
    }
}
