/*
 * Copyright (C) 2009 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.cts.ImageViewCtsActivity;
import android.graphics.cts.R;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableContainer.DrawableContainerState;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.LargeTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.Xml;
import android.widget.ImageView;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class AnimationDrawableTest {
    private static final int FRAMES_COUNT        = 3;
    private static final int FIRST_FRAME_INDEX   = 0;
    private static final int SECOND_FRAME_INDEX  = 1;
    private static final int THIRD_FRAME_INDEX   = 2;
    private static final long TOLERANCE = 500;
    private static final long FIRST_FRAME_DURATION   = 3000;
    private static final long SECOND_FRAME_DURATION  = 2000;
    private static final long THIRD_FRAME_DURATION   = 1000;

    private AnimationDrawable mAnimationDrawable;
    private Resources mResources;
    private boolean mInitialOneShotValue;

    @Rule
    public ActivityTestRule<ImageViewCtsActivity> mActivityRule =
            new ActivityTestRule<>(ImageViewCtsActivity.class);

    @UiThreadTest
    @Before
    public void setup() throws Throwable {
        final Activity activity = mActivityRule.getActivity();
        mResources = activity.getResources();

        try {
            mActivityRule.runOnUiThread(new Runnable() {
                public void run() {
                    ImageView imageView = (ImageView) activity.findViewById(R.id.imageview);
                    imageView.setBackgroundResource(R.drawable.animationdrawable);
                    mAnimationDrawable = (AnimationDrawable) imageView.getBackground();
                    mInitialOneShotValue = mAnimationDrawable.isOneShot();
                }
            });
        } catch (Throwable t) {
            throw new Exception(t);
        }
    }

    @After
    public void tearDown() throws Exception {
        mAnimationDrawable.setOneShot(mInitialOneShotValue);
    }

    @Test
    public void testConstructor() {
        AnimationDrawable animationDrawable = new AnimationDrawable();
        // Check the values set in the constructor
        assertNotNull(animationDrawable.getConstantState());
        assertFalse(animationDrawable.isRunning());
        assertFalse(animationDrawable.isOneShot());
    }

    @Test
    public void testSetVisible() throws Throwable {
        assertTrue(mAnimationDrawable.isVisible());
        mActivityRule.runOnUiThread(mAnimationDrawable::start);
        assertTrue(mAnimationDrawable.isRunning());
        assertSame(mAnimationDrawable.getFrame(FIRST_FRAME_INDEX), mAnimationDrawable.getCurrent());

        pollingCheckDrawable(SECOND_FRAME_INDEX, FIRST_FRAME_DURATION);

        mActivityRule.runOnUiThread(() -> assertTrue(mAnimationDrawable.setVisible(false, false)));
        assertFalse(mAnimationDrawable.isVisible());
        assertFalse(mAnimationDrawable.isRunning());
        verifyStoppedAnimation(SECOND_FRAME_INDEX, SECOND_FRAME_DURATION);

        // restart animation
        mActivityRule.runOnUiThread(() -> assertTrue(mAnimationDrawable.setVisible(true, true)));
        assertTrue(mAnimationDrawable.isVisible());
        assertTrue(mAnimationDrawable.isRunning());
        pollingCheckDrawable(SECOND_FRAME_INDEX, FIRST_FRAME_DURATION);
    }

    @Test
    public void testStart() throws Throwable {
        // animation should play repeat if do not stop it.
        assertFalse(mAnimationDrawable.isOneShot());
        assertFalse(mAnimationDrawable.isRunning());
        mActivityRule.runOnUiThread(mAnimationDrawable::start);

        assertTrue(mAnimationDrawable.isRunning());
        assertSame(mAnimationDrawable.getFrame(FIRST_FRAME_INDEX),
                mAnimationDrawable.getCurrent());
        pollingCheckDrawable(SECOND_FRAME_INDEX, FIRST_FRAME_DURATION);

        mActivityRule.runOnUiThread(mAnimationDrawable::start);
        pollingCheckDrawable(THIRD_FRAME_INDEX, SECOND_FRAME_DURATION);

        mActivityRule.runOnUiThread(mAnimationDrawable::stop);
        assertFalse(mAnimationDrawable.isRunning());
        verifyStoppedAnimation(THIRD_FRAME_INDEX, THIRD_FRAME_DURATION);

        // This method has no effect if the animation is not running.
        mActivityRule.runOnUiThread(mAnimationDrawable::stop);
        assertFalse(mAnimationDrawable.isRunning());
        verifyStoppedAnimation(THIRD_FRAME_INDEX, THIRD_FRAME_DURATION);
    }

    @Test
    public void testRun() throws Throwable {
        assertFalse(mAnimationDrawable.isRunning());
        mActivityRule.runOnUiThread(mAnimationDrawable::run);

        assertTrue(mAnimationDrawable.isRunning());
        pollingCheckDrawable(SECOND_FRAME_INDEX, FIRST_FRAME_DURATION);

        mActivityRule.runOnUiThread(() -> mAnimationDrawable.unscheduleSelf(mAnimationDrawable));
    }

    @Test
    public void testUnscheduleSelf() throws Throwable {
        assertFalse(mAnimationDrawable.isRunning());
        mActivityRule.runOnUiThread(mAnimationDrawable::start);

        assertTrue(mAnimationDrawable.isRunning());
        pollingCheckDrawable(SECOND_FRAME_INDEX, FIRST_FRAME_DURATION);

        mActivityRule.runOnUiThread(() -> mAnimationDrawable.unscheduleSelf(mAnimationDrawable));
        assertFalse(mAnimationDrawable.isRunning());
        verifyStoppedAnimation(SECOND_FRAME_INDEX, SECOND_FRAME_DURATION);
    }

    @Test
    public void testGetNumberOfFrames() {
        AnimationDrawable mutated = (AnimationDrawable) mAnimationDrawable.mutate();
        assertEquals(FRAMES_COUNT, mutated.getNumberOfFrames());

        Drawable frame = mResources.getDrawable(R.drawable.failed);
        mAnimationDrawable.addFrame(frame, 2000);
        assertEquals(FRAMES_COUNT + 1, mutated.getNumberOfFrames());

        // add same frame with same duration
        mAnimationDrawable.addFrame(frame, 2000);
        assertEquals(FRAMES_COUNT + 2, mutated.getNumberOfFrames());

        try {
            mAnimationDrawable.addFrame(null, 1000);
            fail("Should throw NullPointerException if param frame is null.");
        } catch (NullPointerException e) {
            // expected
        }
    }

    @Test
    public void testGetFrame() {
        Drawable frame = mAnimationDrawable.getFrame(FIRST_FRAME_INDEX);
        Drawable drawable = mResources.getDrawable(R.drawable.testimage);
        assertEquals(drawable.getIntrinsicWidth(), frame.getIntrinsicWidth());
        assertEquals(drawable.getIntrinsicHeight(), frame.getIntrinsicHeight());

        frame = mAnimationDrawable.getFrame(SECOND_FRAME_INDEX);
        drawable = mResources.getDrawable(R.drawable.pass);
        assertEquals(drawable.getIntrinsicWidth(), frame.getIntrinsicWidth());
        assertEquals(drawable.getIntrinsicHeight(), frame.getIntrinsicHeight());

        frame = mAnimationDrawable.getFrame(THIRD_FRAME_INDEX);
        drawable = mResources.getDrawable(R.drawable.scenery);
        assertEquals(drawable.getIntrinsicWidth(), frame.getIntrinsicWidth());
        assertEquals(drawable.getIntrinsicHeight(), frame.getIntrinsicHeight());

        assertNull(mAnimationDrawable.getFrame(THIRD_FRAME_INDEX + 1));
    }

    @Test(expected=ArrayIndexOutOfBoundsException.class)
    public void testGetFrameTooLow() {
        mAnimationDrawable.getFrame(-1);
    }

    @Test(expected=ArrayIndexOutOfBoundsException.class)
    public void testGetFrameTooHigh() {
        mAnimationDrawable.getFrame(10);
    }

    @Test
    public void testGetDuration() {
        assertEquals(FIRST_FRAME_DURATION, mAnimationDrawable.getDuration(FIRST_FRAME_INDEX));
        assertEquals(SECOND_FRAME_DURATION, mAnimationDrawable.getDuration(SECOND_FRAME_INDEX));
        assertEquals(THIRD_FRAME_DURATION, mAnimationDrawable.getDuration(THIRD_FRAME_INDEX));
        assertEquals(0, mAnimationDrawable.getDuration(THIRD_FRAME_INDEX + 1));
    }

    @Test(expected=ArrayIndexOutOfBoundsException.class)
    public void testGetDurationTooLow() {
        mAnimationDrawable.getDuration(-1);
    }

    @Test(expected=ArrayIndexOutOfBoundsException.class)
    public void testGetDurationTooHigh() {
        mAnimationDrawable.getDuration(10);
    }

    @Test
    public void testAccessOneShot() throws Throwable {
        // animation should play repeat if do not stop it.
        assertFalse(mAnimationDrawable.isOneShot());
        mActivityRule.runOnUiThread(mAnimationDrawable::start);
        pollingCheckDrawable(SECOND_FRAME_INDEX, FIRST_FRAME_DURATION);
        pollingCheckDrawable(THIRD_FRAME_INDEX, SECOND_FRAME_DURATION);
        // begin to repeat
        pollingCheckDrawable(FIRST_FRAME_INDEX, THIRD_FRAME_DURATION);

        mActivityRule.runOnUiThread(() -> {
            mAnimationDrawable.stop();
            mAnimationDrawable.setOneShot(true);
            assertTrue(mAnimationDrawable.isOneShot());
            mAnimationDrawable.start();
        });
        pollingCheckDrawable(SECOND_FRAME_INDEX, FIRST_FRAME_DURATION);
        pollingCheckDrawable(THIRD_FRAME_INDEX, SECOND_FRAME_DURATION);
        // do not repeat
        verifyStoppedAnimation(THIRD_FRAME_INDEX, THIRD_FRAME_DURATION);
        // Set visible to false and restart to false
        mActivityRule.runOnUiThread(() -> mAnimationDrawable.setVisible(false, false));
        // Check that animation drawable stays on the same frame
        verifyStoppedAnimation(THIRD_FRAME_INDEX, THIRD_FRAME_DURATION);

        // Set visible to true and restart to false
        mActivityRule.runOnUiThread(() -> mAnimationDrawable.setVisible(true, false));
        // Check that animation drawable stays on the same frame
        verifyStoppedAnimation(THIRD_FRAME_INDEX, THIRD_FRAME_DURATION);
    }

    @Test
    public void testInflateCorrect() throws XmlPullParserException, IOException {
        XmlResourceParser parser = getResourceParser(R.xml.anim_list_correct);
        AnimationDrawable dr = new AnimationDrawable();
        dr.inflate(mResources, parser, Xml.asAttributeSet(parser));
        // android:visible="false"
        assertFalse(dr.isVisible());
        // android:oneShot="true"
        assertTrue(dr.isOneShot());
        // android:variablePadding="true"
        DrawableContainerState state =
                (DrawableContainerState) dr.getConstantState();
        assertNull(state.getConstantPadding());
        assertEquals(2, dr.getNumberOfFrames());
        assertEquals(2000, dr.getDuration(0));
        assertEquals(1000, dr.getDuration(1));
        assertSame(dr.getFrame(0), dr.getCurrent());
    }

    @Test
    public void testInflateMissingDrawable() throws XmlPullParserException, IOException {
        XmlResourceParser parser = getResourceParser(R.xml.anim_list_missing_item_drawable);
        AnimationDrawable dr = new AnimationDrawable();
        try {
            dr.inflate(mResources, parser, Xml.asAttributeSet(parser));
            fail("Should throw XmlPullParserException if drawable of item is missing");
        } catch (XmlPullParserException e) {
            // expected
        }
    }

    @Test(expected=NullPointerException.class)
    public void testInflateNullResources() throws XmlPullParserException, IOException {
        XmlResourceParser parser = getResourceParser(R.drawable.animationdrawable);
        AnimationDrawable dr = new AnimationDrawable();
        // Should throw NullPointerException if resource is null
        dr.inflate(null, parser, Xml.asAttributeSet(parser));
    }

    @Test(expected=NullPointerException.class)
    public void testInflateNullXmlPullParser() throws XmlPullParserException, IOException {
        XmlResourceParser parser = getResourceParser(R.drawable.animationdrawable);
        AnimationDrawable dr = new AnimationDrawable();
        // Should throw NullPointerException if parser is null
        dr.inflate(mResources, null, Xml.asAttributeSet(parser));
    }

    @Test(expected=NullPointerException.class)
    public void testInflateNullAttributeSet() throws XmlPullParserException, IOException {
        XmlResourceParser parser = getResourceParser(R.drawable.animationdrawable);
        AnimationDrawable dr = new AnimationDrawable();
        // Should throw NullPointerException if AttributeSet is null
        dr.inflate(mResources, parser, null);
    }

    @Test
    public void testMutate() {
        AnimationDrawable d1 = (AnimationDrawable) mResources
                .getDrawable(R.drawable.animationdrawable);
        // multiple instances inflated from the same resource do not share the state
        // simply call mutate to make sure it does not throw an exception
        d1.mutate();
    }

    private XmlResourceParser getResourceParser(int resId) throws XmlPullParserException,
            IOException {
        XmlResourceParser parser = mResources.getXml(resId);
        int type;
        while ((type = parser.next()) != XmlPullParser.START_TAG
                && type != XmlPullParser.END_DOCUMENT) {
            // Empty loop
        }
        return parser;
    }

    /**
     * Polling check specific frame should be current one in timeout.
     * @param index - expected index of frame.
     * @param timeout - timeout.
     */
    private void pollingCheckDrawable(final int index, long timeout) {
        final Drawable expected = mAnimationDrawable.getFrame(index);
        PollingCheck.waitFor(timeout + TOLERANCE,
                () -> mAnimationDrawable.getCurrent().equals(expected));
    }

    /**
     * Assert animation had been stopped. It will sleep duration + TOLERANCE milliseconds and
     * then make sure current frame had not been changed.
     * @param index - index of current frame.
     * @param duration - duration of current frame.
     */
    private void verifyStoppedAnimation(int index, long duration) throws InterruptedException {
        Thread.sleep(duration + TOLERANCE);
        assertSame(mAnimationDrawable.getFrame(index), mAnimationDrawable.getCurrent());
    }
}
