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
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Parcelable;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.autofill.AutofillValue;
import android.widget.TimePicker;

import com.android.compatibility.common.util.CtsKeyEventUtil;
import com.android.compatibility.common.util.CtsTouchUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test {@link TimePicker}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class TimePickerTest {
    private Instrumentation mInstrumentation;
    private Activity mActivity;
    private TimePicker mTimePicker;

    @Rule
    public ActivityTestRule<TimePickerCtsActivity> mActivityRule =
            new ActivityTestRule<>(TimePickerCtsActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
        mTimePicker = (TimePicker) mActivity.findViewById(R.id.timepicker_clock);
    }

    @Test
    public void testConstructors() {
        AttributeSet attrs = mActivity.getResources().getLayout(R.layout.timepicker);
        assertNotNull(attrs);

        new TimePicker(mActivity);

        new TimePicker(mActivity, attrs);
        new TimePicker(mActivity, null);

        new TimePicker(mActivity, attrs, 0);
        new TimePicker(mActivity, null, 0);
        new TimePicker(mActivity, attrs, 0);
        new TimePicker(mActivity, null, android.R.attr.timePickerStyle);
        new TimePicker(mActivity, null, 0, android.R.style.Widget_Material_TimePicker);
        new TimePicker(mActivity, null, 0, android.R.style.Widget_Material_Light_TimePicker);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorNullContext1() {
        new TimePicker(null);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorNullContext2() {
        AttributeSet attrs = mActivity.getResources().getLayout(R.layout.timepicker);
        new TimePicker(null, attrs);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorNullContext3() {
        AttributeSet attrs = mActivity.getResources().getLayout(R.layout.timepicker);
        new TimePicker(null, attrs, 0);
    }

    @UiThreadTest
    @Test
    public void testSetEnabled() {
        assertTrue(mTimePicker.isEnabled());

        mTimePicker.setEnabled(false);
        assertFalse(mTimePicker.isEnabled());
        assertNull(mTimePicker.getAutofillValue());
        assertEquals(View.AUTOFILL_TYPE_NONE, mTimePicker.getAutofillType());

        mTimePicker.setEnabled(true);
        assertTrue(mTimePicker.isEnabled());
        assertNotNull(mTimePicker.getAutofillValue());
        assertEquals(View.AUTOFILL_TYPE_DATE, mTimePicker.getAutofillType());
    }

    @UiThreadTest
    @Test
    public void testAutofill() {
        mTimePicker.setEnabled(true);

        final AtomicInteger numberOfListenerCalls = new AtomicInteger();
        mTimePicker.setOnTimeChangedListener((v, h, m) -> numberOfListenerCalls.incrementAndGet());

        final Calendar calendar = new GregorianCalendar();
        calendar.set(Calendar.HOUR_OF_DAY, 4);
        calendar.set(Calendar.MINUTE, 20);

        final AutofillValue autofilledValue = AutofillValue.forDate(calendar.getTimeInMillis());
        mTimePicker.autofill(autofilledValue);
        assertEquals(autofilledValue, mTimePicker.getAutofillValue());
        assertEquals(4, mTimePicker.getHour());
        assertEquals(20, mTimePicker.getMinute());
        assertEquals(1, numberOfListenerCalls.get());

        // Make sure autofill() is ignored when value is null.
        numberOfListenerCalls.set(0);
        mTimePicker.autofill((AutofillValue) null);
        assertEquals(autofilledValue, mTimePicker.getAutofillValue());
        assertEquals(4, mTimePicker.getHour());
        assertEquals(20, mTimePicker.getMinute());
        assertEquals(0, numberOfListenerCalls.get());

        // Make sure autofill() is ignored when value is not a date.
        numberOfListenerCalls.set(0);
        mTimePicker.autofill(AutofillValue.forText("Y U NO IGNORE ME?"));
        assertEquals(autofilledValue, mTimePicker.getAutofillValue());
        assertEquals(4, mTimePicker.getHour());
        assertEquals(20, mTimePicker.getMinute());
        assertEquals(0, numberOfListenerCalls.get());

        // Make sure getAutofillValue() is reset when value is manually filled.
        mTimePicker.autofill(autofilledValue); // 04:20
        mTimePicker.setHour(10);
        calendar.setTimeInMillis(mTimePicker.getAutofillValue().getDateValue());
        assertEquals(10, calendar.get(Calendar.HOUR));
        mTimePicker.autofill(autofilledValue); // 04:20
        mTimePicker.setMinute(8);
        calendar.setTimeInMillis(mTimePicker.getAutofillValue().getDateValue());
        assertEquals(8, calendar.get(Calendar.MINUTE));
    }

    @UiThreadTest
    @Test
    public void testSetOnTimeChangedListener() {
        // On time change listener is notified on every call to setCurrentHour / setCurrentMinute.
        // We want to make sure that before we register our listener, we initialize the time picker
        // to the time that is explicitly different from the values we'll be testing for in both
        // hour and minute. Otherwise if the test happens to run at the time that ends in
        // "minuteForTesting" minutes, we'll get two onTimeChanged callbacks with identical values.
        final int initialHour = 10;
        final int initialMinute = 38;
        final int hourForTesting = 13;
        final int minuteForTesting = 50;

        mTimePicker.setHour(initialHour);
        mTimePicker.setMinute(initialMinute);

        // Now register the listener
        TimePicker.OnTimeChangedListener mockOnTimeChangeListener =
                mock(TimePicker.OnTimeChangedListener.class);
        mTimePicker.setOnTimeChangedListener(mockOnTimeChangeListener);
        mTimePicker.setCurrentHour(Integer.valueOf(hourForTesting));
        mTimePicker.setCurrentMinute(Integer.valueOf(minuteForTesting));
        // We're expecting two onTimeChanged callbacks, one with new hour and one with new
        // hour+minute
        verify(mockOnTimeChangeListener, times(1)).onTimeChanged(
                mTimePicker, hourForTesting, initialMinute);
        verify(mockOnTimeChangeListener, times(1)).onTimeChanged(
                mTimePicker, hourForTesting, minuteForTesting);

        // set the same hour as current
        reset(mockOnTimeChangeListener);
        mTimePicker.setCurrentHour(Integer.valueOf(hourForTesting));
        verifyZeroInteractions(mockOnTimeChangeListener);

        mTimePicker.setCurrentHour(Integer.valueOf(hourForTesting + 1));
        verify(mockOnTimeChangeListener, times(1)).onTimeChanged(
                mTimePicker, hourForTesting + 1, minuteForTesting);

        // set the same minute as current
        reset(mockOnTimeChangeListener);
        mTimePicker.setCurrentMinute(minuteForTesting);
        verifyZeroInteractions(mockOnTimeChangeListener);

        reset(mockOnTimeChangeListener);
        mTimePicker.setCurrentMinute(minuteForTesting + 1);
        verify(mockOnTimeChangeListener, times(1)).onTimeChanged(
                mTimePicker, hourForTesting + 1, minuteForTesting + 1);

        // change time picker mode
        reset(mockOnTimeChangeListener);
        mTimePicker.setIs24HourView(!mTimePicker.is24HourView());
        verifyZeroInteractions(mockOnTimeChangeListener);
    }

    @UiThreadTest
    @Test
    public void testAccessCurrentHour() {
        // AM/PM mode
        mTimePicker.setIs24HourView(false);

        mTimePicker.setCurrentHour(0);
        assertEquals(Integer.valueOf(0), mTimePicker.getCurrentHour());

        mTimePicker.setCurrentHour(12);
        assertEquals(Integer.valueOf(12), mTimePicker.getCurrentHour());

        mTimePicker.setCurrentHour(13);
        assertEquals(Integer.valueOf(13), mTimePicker.getCurrentHour());

        mTimePicker.setCurrentHour(23);
        assertEquals(Integer.valueOf(23), mTimePicker.getCurrentHour());

        // for 24 hour mode
        mTimePicker.setIs24HourView(true);

        mTimePicker.setCurrentHour(0);
        assertEquals(Integer.valueOf(0), mTimePicker.getCurrentHour());

        mTimePicker.setCurrentHour(13);
        assertEquals(Integer.valueOf(13), mTimePicker.getCurrentHour());

        mTimePicker.setCurrentHour(23);
        assertEquals(Integer.valueOf(23), mTimePicker.getCurrentHour());
    }

    @UiThreadTest
    @Test
    public void testAccessHour() {
        // AM/PM mode
        mTimePicker.setIs24HourView(false);

        mTimePicker.setHour(0);
        assertEquals(0, mTimePicker.getHour());

        mTimePicker.setHour(12);
        assertEquals(12, mTimePicker.getHour());

        mTimePicker.setHour(13);
        assertEquals(13, mTimePicker.getHour());

        mTimePicker.setHour(23);
        assertEquals(23, mTimePicker.getHour());

        // for 24 hour mode
        mTimePicker.setIs24HourView(true);

        mTimePicker.setHour(0);
        assertEquals(0, mTimePicker.getHour());

        mTimePicker.setHour(13);
        assertEquals(13, mTimePicker.getHour());

        mTimePicker.setHour(23);
        assertEquals(23, mTimePicker.getHour());
    }

    @UiThreadTest
    @Test
    public void testAccessIs24HourView() {
        assertFalse(mTimePicker.is24HourView());

        mTimePicker.setIs24HourView(true);
        assertTrue(mTimePicker.is24HourView());

        mTimePicker.setIs24HourView(false);
        assertFalse(mTimePicker.is24HourView());
    }

    @UiThreadTest
    @Test
    public void testAccessCurrentMinute() {
        mTimePicker.setCurrentMinute(0);
        assertEquals(Integer.valueOf(0), mTimePicker.getCurrentMinute());

        mTimePicker.setCurrentMinute(12);
        assertEquals(Integer.valueOf(12), mTimePicker.getCurrentMinute());

        mTimePicker.setCurrentMinute(33);
        assertEquals(Integer.valueOf(33), mTimePicker.getCurrentMinute());

        mTimePicker.setCurrentMinute(59);
        assertEquals(Integer.valueOf(59), mTimePicker.getCurrentMinute());
    }

    @UiThreadTest
    @Test
    public void testAccessMinute() {
        mTimePicker.setMinute(0);
        assertEquals(0, mTimePicker.getMinute());

        mTimePicker.setMinute(12);
        assertEquals(12, mTimePicker.getMinute());

        mTimePicker.setMinute(33);
        assertEquals(33, mTimePicker.getMinute());

        mTimePicker.setMinute(59);
        assertEquals(59, mTimePicker.getMinute());
    }

    @Test
    public void testGetBaseline() {
        assertEquals(-1, mTimePicker.getBaseline());
    }

    @Test
    public void testOnSaveInstanceStateAndOnRestoreInstanceState() {
        MyTimePicker source = new MyTimePicker(mActivity);
        MyTimePicker dest = new MyTimePicker(mActivity);
        int expectHour = (dest.getCurrentHour() + 10) % 24;
        int expectMinute = (dest.getCurrentMinute() + 10) % 60;
        source.setCurrentHour(expectHour);
        source.setCurrentMinute(expectMinute);

        Parcelable p = source.onSaveInstanceState();
        dest.onRestoreInstanceState(p);

        assertEquals(Integer.valueOf(expectHour), dest.getCurrentHour());
        assertEquals(Integer.valueOf(expectMinute), dest.getCurrentMinute());
    }

    private boolean isWatch() {
        return (mActivity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_TYPE_WATCH) == Configuration.UI_MODE_TYPE_WATCH;
    }

    @Test
    public void testKeyboardTabTraversalModeClock() throws Throwable {
        if (isWatch()) {
            return;
        }
        mTimePicker = (TimePicker) mActivity.findViewById(R.id.timepicker_clock);

        mActivityRule.runOnUiThread(() -> mTimePicker.setIs24HourView(false));
        mInstrumentation.waitForIdleSync();
        verifyTimePickerKeyboardTraversal(
                true /* goForward */,
                false /* is24HourView */);
        verifyTimePickerKeyboardTraversal(
                false /* goForward */,
                false /* is24HourView */);

        mActivityRule.runOnUiThread(() -> mTimePicker.setIs24HourView(true));
        mInstrumentation.waitForIdleSync();
        verifyTimePickerKeyboardTraversal(
                true /* goForward */,
                true /* is24HourView */);
        verifyTimePickerKeyboardTraversal(
                false /* goForward */,
                true /* is24HourView */);
    }

    @Test
    public void testKeyboardTabTraversalModeSpinner() throws Throwable {
        if (isWatch()) {
            return;
        }
        mTimePicker = (TimePicker) mActivity.findViewById(R.id.timepicker_spinner);

        mActivityRule.runOnUiThread(() -> mTimePicker.setIs24HourView(false));
        mInstrumentation.waitForIdleSync();

        // Spinner time-picker doesn't explicitly define a focus order. Just make sure inputs
        // are able to be traversed (added to focusables).
        ArrayList<View> focusables = new ArrayList<>();
        mTimePicker.addFocusables(focusables, View.FOCUS_FORWARD);
        assertTrue(focusables.contains(mTimePicker.getHourView()));
        assertTrue(focusables.contains(mTimePicker.getMinuteView()));
        assertTrue(focusables.contains(mTimePicker.getAmView()));
        focusables.clear();

        mActivityRule.runOnUiThread(() -> mTimePicker.setIs24HourView(true));
        mInstrumentation.waitForIdleSync();
        mTimePicker.addFocusables(focusables, View.FOCUS_FORWARD);
        assertTrue(focusables.contains(mTimePicker.getHourView()));
        assertTrue(focusables.contains(mTimePicker.getMinuteView()));
    }

    @Test
    public void testKeyboardInputModeClockAmPm() throws Throwable {
        if (isWatch()) {
            return;
        }
        final int initialHour = 6;
        final int initialMinute = 59;
        prepareForKeyboardInput(initialHour, initialMinute, false /* is24hFormat */,
                true /* isClockMode */);

        // Input valid hour.
        assertEquals(initialHour, mTimePicker.getHour());
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mTimePicker.getHourView());
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_1);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_0);
        assertEquals(10, mTimePicker.getHour());
        assertTrue(mTimePicker.getMinuteView().hasFocus());

        // Input valid minute.
        assertEquals(initialMinute, mTimePicker.getMinute());
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_4);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_3);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_TAB);
        assertEquals(43, mTimePicker.getMinute());
        assertTrue(mTimePicker.getAmView().hasFocus());

        // Accepting AM changes nothing.
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_ENTER);
        assertEquals(10, mTimePicker.getHour());
        assertEquals(43, mTimePicker.getMinute());

        // Focus PM radio.
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_TAB);
        assertTrue(mTimePicker.getPmView().hasFocus());
        // Still nothing has changed.
        assertEquals(10, mTimePicker.getHour());
        assertEquals(43, mTimePicker.getMinute());
        // Select PM and verify the hour has changed.
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_ENTER);
        assertEquals(22, mTimePicker.getHour());
        assertEquals(43, mTimePicker.getMinute());
        // Set AM again.
        CtsKeyEventUtil.sendKeyWhileHoldingModifier(mInstrumentation, mTimePicker,
                KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_SHIFT_LEFT);
        assertTrue(mTimePicker.getAmView().hasFocus());
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_ENTER);
        assertEquals(10, mTimePicker.getHour());

        // Re-focus the hour view.
        CtsKeyEventUtil.sendKeyWhileHoldingModifier(mInstrumentation, mTimePicker,
                KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_SHIFT_LEFT);
        CtsKeyEventUtil.sendKeyWhileHoldingModifier(mInstrumentation, mTimePicker,
                KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_SHIFT_LEFT);
        assertTrue(mTimePicker.getHourView().hasFocus());

        // Input an invalid value (larger than 12).
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_1);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_3);
        // Force setting the hour by moving to minute.
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_TAB);
        // After sending 1 and 3 only 1 is accepted.
        assertEquals(1, mTimePicker.getHour());
        assertEquals(43, mTimePicker.getMinute());
        CtsKeyEventUtil.sendKeyWhileHoldingModifier(mInstrumentation, mTimePicker,
                KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_SHIFT_LEFT);
        // The hour view still has focus.
        assertTrue(mTimePicker.getHourView().hasFocus());

        // This time send a valid hour (11).
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_1);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_1);
        // The value is valid.
        assertEquals(11, mTimePicker.getHour());
        assertEquals(43, mTimePicker.getMinute());

        verifyModeClockMinuteInput();
    }

    @Test
    public void testKeyboardInputModeClock24H() throws Throwable {
        if (isWatch()) {
            return;
        }
        final int initialHour = 6;
        final int initialMinute = 59;
        prepareForKeyboardInput(initialHour, initialMinute, true /* is24hFormat */,
                true /* isClockMode */);

        // Input valid hour.
        assertEquals(initialHour, mTimePicker.getHour());
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mTimePicker.getHourView());
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_1);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_0);
        assertEquals(10, mTimePicker.getHour());
        assertTrue(mTimePicker.getMinuteView().hasFocus());

        // Input valid minute.
        assertEquals(initialMinute, mTimePicker.getMinute());
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_4);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_3);
        assertEquals(43, mTimePicker.getMinute());

        // Re-focus the hour view.
        CtsKeyEventUtil.sendKeyWhileHoldingModifier(mInstrumentation, mTimePicker,
                KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_SHIFT_LEFT);
        assertTrue(mTimePicker.getHourView().hasFocus());

        // Input an invalid value (larger than 24).
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_2);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_5);
        // Force setting the hour by moving to minute.
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_TAB);
        // After sending 2 and 5 only 2 is accepted.
        assertEquals(2, mTimePicker.getHour());
        assertEquals(43, mTimePicker.getMinute());
        CtsKeyEventUtil.sendKeyWhileHoldingModifier(mInstrumentation, mTimePicker,
                KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_SHIFT_LEFT);
        // The hour view still has focus.
        assertTrue(mTimePicker.getHourView().hasFocus());

        // This time send a valid hour.
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_2);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_3);
        // The value is valid.
        assertEquals(23, mTimePicker.getHour());
        assertEquals(43, mTimePicker.getMinute());

        verifyModeClockMinuteInput();
    }

    @Test
    public void testKeyboardInputModeSpinnerAmPm() throws Throwable {
        if (isWatch()) {
            return;
        }
        final int initialHour = 6;
        final int initialMinute = 59;
        prepareForKeyboardInput(initialHour, initialMinute, false /* is24hFormat */,
                false /* isClockMode */);

        assertEquals(initialHour, mTimePicker.getHour());
        mActivityRule.runOnUiThread(() -> mTimePicker.getHourView().requestFocus());
        mInstrumentation.waitForIdleSync();

        // Input invalid hour.
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_1);
        // None of the keys below should be accepted after 1 was pressed.
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_3);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_4);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_5);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_TAB);
        // Since only 0, 1 or 2 are accepted for AM/PM hour mode after pressing 1, we expect the
        // hour value to be 1.
        assertEquals(1, mTimePicker.getHour());
        assertFalse(mTimePicker.getHourView().hasFocus());

        //  Go back to hour view and input valid hour.
        mActivityRule.runOnUiThread(() -> mTimePicker.getHourView().requestFocus());
        mInstrumentation.waitForIdleSync();
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_1);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_1);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_TAB);
        assertEquals(11, mTimePicker.getHour());
        assertFalse(mTimePicker.getHourView().hasFocus());

        // Go back to hour view and exercise UP and DOWN keys.
        mActivityRule.runOnUiThread(() -> mTimePicker.getHourView().requestFocus());
        mInstrumentation.waitForIdleSync();
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(12, mTimePicker.getHour());
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_DPAD_UP);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_DPAD_UP);
        assertEquals(10, mTimePicker.getHour());

        // Minute input testing.
        assertEquals(initialMinute, mTimePicker.getMinute());
        verifyModeSpinnerMinuteInput();

        // Reset to values preparing to test the AM/PM picker.
        mActivityRule.runOnUiThread(() -> {
            mTimePicker.setHour(6);
            mTimePicker.setMinute(initialMinute);
        });
        mInstrumentation.waitForIdleSync();
        // In spinner mode the AM view and PM view are the same.
        assertEquals(mTimePicker.getAmView(), mTimePicker.getPmView());
        mActivityRule.runOnUiThread(() -> mTimePicker.getAmView().requestFocus());
        mInstrumentation.waitForIdleSync();
        assertTrue(mTimePicker.getAmView().hasFocus());
        assertEquals(6, mTimePicker.getHour());
        // Pressing A changes nothing.
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_A);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_TAB);
        assertEquals(6, mTimePicker.getHour());
        assertEquals(initialMinute, mTimePicker.getMinute());
        // Pressing P switches to PM.
        CtsKeyEventUtil.sendKeyWhileHoldingModifier(mInstrumentation, mTimePicker,
                KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_SHIFT_LEFT);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_P);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_TAB);
        assertEquals(18, mTimePicker.getHour());
        assertEquals(initialMinute, mTimePicker.getMinute());
        // Pressing P again changes nothing.
        CtsKeyEventUtil.sendKeyWhileHoldingModifier(mInstrumentation, mTimePicker,
                KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_SHIFT_LEFT);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_P);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_TAB);
        assertEquals(18, mTimePicker.getHour());
        assertEquals(initialMinute, mTimePicker.getMinute());
        // Pressing A switches to AM.
        CtsKeyEventUtil.sendKeyWhileHoldingModifier(mInstrumentation, mTimePicker,
                KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_SHIFT_LEFT);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_A);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_TAB);
        assertEquals(6, mTimePicker.getHour());
        assertEquals(initialMinute, mTimePicker.getMinute());
        // Given that we are already set to AM, pressing UP changes nothing.
        mActivityRule.runOnUiThread(() -> mTimePicker.getAmView().requestFocus());
        mInstrumentation.waitForIdleSync();
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_DPAD_UP);
        assertEquals(6, mTimePicker.getHour());
        assertEquals(initialMinute, mTimePicker.getMinute());
        mActivityRule.runOnUiThread(() -> mTimePicker.getAmView().requestFocus());
        mInstrumentation.waitForIdleSync();
        // Pressing down switches to PM.
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(18, mTimePicker.getHour());
        assertEquals(initialMinute, mTimePicker.getMinute());
        mActivityRule.runOnUiThread(() -> mTimePicker.getAmView().requestFocus());
        mInstrumentation.waitForIdleSync();
        // Given that we are set to PM, pressing DOWN again changes nothing.
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(18, mTimePicker.getHour());
        assertEquals(initialMinute, mTimePicker.getMinute());
        mActivityRule.runOnUiThread(() -> mTimePicker.getAmView().requestFocus());
        mInstrumentation.waitForIdleSync();
        // Pressing UP switches to AM.
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_DPAD_UP);
        assertEquals(6, mTimePicker.getHour());
        assertEquals(initialMinute, mTimePicker.getMinute());
    }

    @Test
    public void testKeyboardInputModeSpinner24H() throws Throwable {
        if (isWatch()) {
            return;
        }
        final int initialHour = 6;
        final int initialMinute = 59;
        prepareForKeyboardInput(initialHour, initialMinute, true /* is24hFormat */,
                false /* isClockMode */);

        assertEquals(initialHour, mTimePicker.getHour());
        mActivityRule.runOnUiThread(() -> mTimePicker.getHourView().requestFocus());
        mInstrumentation.waitForIdleSync();

        // Input invalid hour.
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_2);
        // None of the keys below should be accepted after 2 was pressed.
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_4);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_5);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_6);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_TAB);
        // Only 2 is accepted (as the only 0, 1, 2, and 3 can form valid hours after pressing 2).
        assertEquals(2, mTimePicker.getHour());
        assertFalse(mTimePicker.getHourView().hasFocus());

        //  Go back to hour view and input valid hour.
        mActivityRule.runOnUiThread(() -> mTimePicker.getHourView().requestFocus());
        mInstrumentation.waitForIdleSync();
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_2);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_3);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_TAB);
        assertEquals(23, mTimePicker.getHour());
        assertFalse(mTimePicker.getHourView().hasFocus());

        // Go back to hour view and exercise UP and DOWN keys.
        mActivityRule.runOnUiThread(() -> mTimePicker.getHourView().requestFocus());
        mInstrumentation.waitForIdleSync();
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(0 /* 24 */, mTimePicker.getHour());
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_DPAD_UP);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_DPAD_UP);
        assertEquals(22, mTimePicker.getHour());

        // Minute input testing.
        assertEquals(initialMinute, mTimePicker.getMinute());
        verifyModeSpinnerMinuteInput();
    }

    private void verifyModeClockMinuteInput() {
        assertTrue(mTimePicker.getMinuteView().hasFocus());
        // Send a invalid minute.
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_6);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_7);
        // Sent 6 and 7 but only 6 was valid.
        assertEquals(6, mTimePicker.getMinute());
        // No matter what other invalid values we send, the minute is unchanged and the focus is
        // kept.
        // 61 invalid.
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_1);
        assertTrue(mTimePicker.getMinuteView().hasFocus());
        // 62 invalid.
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_2);
        assertTrue(mTimePicker.getMinuteView().hasFocus());
        // 63 invalid.
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_3);
        assertTrue(mTimePicker.getMinuteView().hasFocus());
        assertEquals(6, mTimePicker.getMinute());
        // Refocus.
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_TAB);
        CtsKeyEventUtil.sendKeyWhileHoldingModifier(mInstrumentation, mTimePicker,
                KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_SHIFT_LEFT);
        assertTrue(mTimePicker.getMinuteView().hasFocus());

        // In the end pass a valid minute.
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_5);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_9);
        assertEquals(59, mTimePicker.getMinute());
    }

    private void verifyModeSpinnerMinuteInput() throws Throwable {
        mActivityRule.runOnUiThread(() -> mTimePicker.getMinuteView().requestFocus());
        mInstrumentation.waitForIdleSync();
        assertTrue(mTimePicker.getMinuteView().hasFocus());

        // Input invalid minute.
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_6);
        // None of the keys below should be accepted after 6 was pressed.
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_3);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_4);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_5);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_TAB);
        // Only 6 is accepted (as the only valid minute value that starts with 6 is 6 itself).
        assertEquals(6, mTimePicker.getMinute());

        // Go back to minute view and input valid minute.
        CtsKeyEventUtil.sendKeyWhileHoldingModifier(mInstrumentation, mTimePicker,
                KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_SHIFT_LEFT);
        assertTrue(mTimePicker.getMinuteView().hasFocus());
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_4);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_8);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_TAB);
        assertEquals(48, mTimePicker.getMinute());

        // Go back to minute view and exercise UP and DOWN keys.
        CtsKeyEventUtil.sendKeyWhileHoldingModifier(mInstrumentation, mTimePicker,
                KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_SHIFT_LEFT);
        assertTrue(mTimePicker.getMinuteView().hasFocus());
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(49, mTimePicker.getMinute());
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_DPAD_UP);
        CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTimePicker, KeyEvent.KEYCODE_DPAD_UP);
        assertEquals(47, mTimePicker.getMinute());
    }

    private void prepareForKeyboardInput(int initialHour, int initialMinute, boolean is24hFormat,
            boolean isClockMode) throws Throwable {
        mTimePicker = isClockMode
                ? (TimePicker) mActivity.findViewById(R.id.timepicker_clock)
                : (TimePicker) mActivity.findViewById(R.id.timepicker_spinner);

        mActivityRule.runOnUiThread(() -> {
            mTimePicker.setIs24HourView(is24hFormat);
            mTimePicker.setHour(initialHour);
            mTimePicker.setMinute(initialMinute);
            mTimePicker.requestFocus();
        });
        mInstrumentation.waitForIdleSync();
    }

    private void verifyTimePickerKeyboardTraversal(boolean goForward, boolean is24HourView)
            throws Throwable {
        ArrayList<View> forwardViews = new ArrayList<>();
        String summary = (goForward ? " forward " : " backward ")
                + "traversal, is24HourView=" + is24HourView;
        assertNotNull("Unexpected NULL hour view for" + summary, mTimePicker.getHourView());
        forwardViews.add(mTimePicker.getHourView());
        assertNotNull("Unexpected NULL minute view for" + summary, mTimePicker.getMinuteView());
        forwardViews.add(mTimePicker.getMinuteView());
        if (!is24HourView) {
            assertNotNull("Unexpected NULL AM view for" + summary, mTimePicker.getAmView());
            forwardViews.add(mTimePicker.getAmView());
            assertNotNull("Unexpected NULL PM view for" + summary, mTimePicker.getPmView());
            forwardViews.add(mTimePicker.getPmView());
        }

        if (!goForward) {
            Collections.reverse(forwardViews);
        }

        final int viewsSize = forwardViews.size();
        for (int i = 0; i < viewsSize; i++) {
            final View currentView = forwardViews.get(i);
            String afterKeyCodeFormattedString = "";
            int goForwardKeyCode = KeyEvent.KEYCODE_TAB;
            int modifierKeyCodeToHold = KeyEvent.KEYCODE_SHIFT_LEFT;

            if (i == 0) {
                // Make sure we always start by focusing the 1st element in the list.
                mActivityRule.runOnUiThread(currentView::requestFocus);
            } else {
                if (goForward) {
                    afterKeyCodeFormattedString = " after pressing="
                            + KeyEvent.keyCodeToString(goForwardKeyCode);
                } else {
                    afterKeyCodeFormattedString = " after pressing="
                            + KeyEvent.keyCodeToString(modifierKeyCodeToHold)
                            + "+" + KeyEvent.keyCodeToString(goForwardKeyCode)  + " for" + summary;
                }
            }

            assertTrue("View='" + currentView + "'" + " with index " + i + " is not enabled"
                    + afterKeyCodeFormattedString + " for" + summary, currentView.isEnabled());
            assertTrue("View='" + currentView + "'" + " with index " + i + " is not focused"
                    + afterKeyCodeFormattedString + " for" + summary, currentView.isFocused());

            if (i < viewsSize - 1) {
                if (goForward) {
                    CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, currentView, goForwardKeyCode);
                } else {
                    CtsKeyEventUtil.sendKeyWhileHoldingModifier(mInstrumentation, currentView,
                            goForwardKeyCode, modifierKeyCodeToHold);
                }
            }
        }
    }

    private class MyTimePicker extends TimePicker {
        public MyTimePicker(Context context) {
            super(context);
        }

        @Override
        protected void onRestoreInstanceState(Parcelable state) {
            super.onRestoreInstanceState(state);
        }

        @Override
        protected Parcelable onSaveInstanceState() {
            return super.onSaveInstanceState();
        }
    }
}
