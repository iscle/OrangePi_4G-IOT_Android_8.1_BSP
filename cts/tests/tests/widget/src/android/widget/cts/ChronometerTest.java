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
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import android.app.Instrumentation;
import android.content.Context;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.LargeTest;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.ContextThemeWrapper;
import android.widget.Chronometer;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link Chronometer}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class ChronometerTest {
    private Instrumentation mInstrumentation;
    private ChronometerCtsActivity mActivity;

    @Rule
    public ActivityTestRule<ChronometerCtsActivity> mActivityRule =
            new ActivityTestRule<>(ChronometerCtsActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
    }

    @Test
    public void testConstructor() {
        new Chronometer(mActivity);

        new Chronometer(mActivity, null);

        new Chronometer(mActivity, null, 0);
    }

    @Test
    public void testConstructorFromAttr() {
        final Context context = new ContextThemeWrapper(mActivity, R.style.ChronometerAwareTheme);
        final Chronometer chronometer = new Chronometer(context, null, R.attr.chronometerStyle);
        assertTrue(chronometer.isCountDown());
        assertEquals(mActivity.getString(R.string.chronometer_format), chronometer.getFormat());
    }

    @Test
    public void testConstructorFromStyle() {
        final Chronometer chronometer = new Chronometer(mActivity, null, 0,
                R.style.ChronometerStyle);
        assertTrue(chronometer.isCountDown());
        assertEquals(mActivity.getString(R.string.chronometer_format), chronometer.getFormat());
    }

    @UiThreadTest
    @Test
    public void testAccessBase() {
        Chronometer chronometer = mActivity.getChronometer();
        CharSequence oldText = chronometer.getText();

        chronometer.setBase(100000);
        assertEquals(100000, chronometer.getBase());
        assertNotSame(oldText, chronometer.getText());

        oldText = chronometer.getText();
        chronometer.setBase(100);
        assertEquals(100, chronometer.getBase());
        assertNotSame(oldText, chronometer.getText());

        oldText = chronometer.getText();
        chronometer.setBase(-1);
        assertEquals(-1, chronometer.getBase());
        assertNotSame(oldText, chronometer.getText());

        oldText = chronometer.getText();
        chronometer.setBase(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, chronometer.getBase());
        assertNotSame(oldText, chronometer.getText());
    }

    @UiThreadTest
    @Test
    public void testAccessFormat() {
        Chronometer chronometer = mActivity.getChronometer();
        String expected = "header-%S-trail";

        chronometer.setFormat(expected);
        assertEquals(expected, chronometer.getFormat());

        chronometer.start();
        String text = chronometer.getText().toString();
        assertTrue(text.startsWith("header"));
        assertTrue(text.endsWith("trail"));
    }

    @Test
    @LargeTest
    public void testStartAndStop() throws Throwable {
        final Chronometer chronometer = mActivity.getChronometer();

        // we will check the text is really updated every 1000ms after start,
        // so we need sleep a moment to wait wait this time. The sleep code shouldn't
        // in the same thread with UI, that's why we use runOnUiThread here.
        mActivityRule.runOnUiThread(() -> {
            // the text will update immediately when call start.
            final CharSequence valueBeforeStart = chronometer.getText();
            chronometer.start();
            assertNotSame(valueBeforeStart, chronometer.getText());
        });
        mInstrumentation.waitForIdleSync();

        CharSequence expected = chronometer.getText();
        SystemClock.sleep(1500);
        assertFalse(expected.equals(chronometer.getText()));

        // we will check the text is really NOT updated anymore every 1000ms after stop,
        // so we need sleep a moment to wait wait this time. The sleep code shouldn't
        // in the same thread with UI, that's why we use runOnUiThread here.
        mActivityRule.runOnUiThread(() -> {
            // the text will never be updated when call stop.
            final CharSequence valueBeforeStop = chronometer.getText();
            chronometer.stop();
            assertSame(valueBeforeStop, chronometer.getText());
        });
        mInstrumentation.waitForIdleSync();

        expected = chronometer.getText();
        SystemClock.sleep(1500);
        assertTrue(expected.equals(chronometer.getText()));
    }

    @Test
    @LargeTest
    public void testAccessOnChronometerTickListener() throws Throwable {
        final Chronometer chronometer = mActivity.getChronometer();
        final Chronometer.OnChronometerTickListener mockTickListener =
                mock(Chronometer.OnChronometerTickListener.class);

        mActivityRule.runOnUiThread(() -> {
            chronometer.setOnChronometerTickListener(mockTickListener);
            chronometer.start();
        });
        mInstrumentation.waitForIdleSync();

        assertEquals(mockTickListener, chronometer.getOnChronometerTickListener());
        verify(mockTickListener, atLeastOnce()).onChronometerTick(chronometer);

        reset(mockTickListener);
        SystemClock.sleep(1500);
        verify(mockTickListener, atLeastOnce()).onChronometerTick(chronometer);
    }

    @Test
    @LargeTest
    public void testCountDown() throws Throwable {
        final Chronometer chronometer = mActivity.getChronometer();
        final Chronometer.OnChronometerTickListener mockTickListener =
                mock(Chronometer.OnChronometerTickListener.class);

        mActivityRule.runOnUiThread(() -> {
            chronometer.setCountDown(true);
            chronometer.setOnChronometerTickListener(mockTickListener);
            chronometer.start();
        });
        mInstrumentation.waitForIdleSync();

        assertTrue(chronometer.isCountDown());

        SystemClock.sleep(5000);
        verify(mockTickListener, atLeastOnce()).onChronometerTick(chronometer);
    }

    @Test
    @LargeTest
    public void testCountUp() throws Throwable {
        final Chronometer chronometer = mActivity.getChronometer();
        final Chronometer.OnChronometerTickListener mockTickListener =
                mock(Chronometer.OnChronometerTickListener.class);

        mActivityRule.runOnUiThread(() -> {
            chronometer.setCountDown(false);
            chronometer.setOnChronometerTickListener(mockTickListener);
            chronometer.start();
        });
        mInstrumentation.waitForIdleSync();

        assertFalse(chronometer.isCountDown());

        SystemClock.sleep(5000);
        verify(mockTickListener, atLeastOnce()).onChronometerTick(chronometer);
    }
}
