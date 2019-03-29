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

package android.app.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.app.TimePickerDialog.OnTimeSetListener;
import android.app.stubs.R;
import android.content.Context;
import android.os.Bundle;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.test.UiThreadTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.widget.TimePicker;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
/**
 * Test {@link TimePickerDialog}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class TimePickerDialogTest {
    private static final String HOUR = "hour";
    private static final String MINUTE = "minute";
    private static final String IS_24_HOUR = "is24hour";

    private static final int TARGET_HOUR = 15;
    private static final int TARGET_MINUTE = 9;

    private int mCallbackHour;
    private int mCallbackMinute;

    private OnTimeSetListener mOnTimeSetListener;

    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mOnTimeSetListener = new OnTimeSetListener(){
            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                mCallbackHour = hourOfDay;
                mCallbackMinute = minute;
            }
        };
    }

    @UiThreadTest
    @Test
    public void testConstructor() {
        new TimePickerDialog(mContext, null, 1, 1, false);

        new TimePickerDialog(mContext, null, 1, 1, true);

        new TimePickerDialog(mContext, AlertDialog.THEME_TRADITIONAL, null, 1, 1, false);

        new TimePickerDialog(mContext, AlertDialog.THEME_HOLO_DARK, null, 1, 1, false);

        new TimePickerDialog(mContext,
                android.R.style.Theme_Material_Dialog_Alert, null, 1, 1, false);
    }

    @UiThreadTest
    @Test(expected = NullPointerException.class)
    public void testConstructorWithNullContext() {
        new TimePickerDialog(null, null, 0, 0, false);
    }

    @UiThreadTest
    @Test
    public void testSaveInstanceState() {
        TimePickerDialog tD = new TimePickerDialog(
                mContext, mOnTimeSetListener, TARGET_HOUR, TARGET_MINUTE, true);

        Bundle b = tD.onSaveInstanceState();

        assertEquals(TARGET_HOUR, b.getInt(HOUR));
        assertEquals(TARGET_MINUTE, b.getInt(MINUTE));
        assertTrue(b.getBoolean(IS_24_HOUR));

        int minute = 13;
        tD = new TimePickerDialog(
                mContext, R.style.Theme_AlertDialog,
                    mOnTimeSetListener, TARGET_HOUR, minute, false);

        b = tD.onSaveInstanceState();

        assertEquals(TARGET_HOUR, b.getInt(HOUR));
        assertEquals(minute, b.getInt(MINUTE));
        assertFalse(b.getBoolean(IS_24_HOUR));
    }

    @UiThreadTest
    @Test
    public void testOnClick() {
        TimePickerDialog timePickerDialog = buildDialog();
        timePickerDialog.onClick(null, TimePickerDialog.BUTTON_POSITIVE);

        assertEquals(TARGET_HOUR, mCallbackHour);
        assertEquals(TARGET_MINUTE, mCallbackMinute);
    }

    @UiThreadTest
    @Test
    public void testOnTimeChanged() throws Throwable {
        final int minute = 34;
        final TimePickerDialog d = buildDialog();
        d.onTimeChanged(null, TARGET_HOUR, minute);
    }

    @UiThreadTest
    @Test
    public void testUpdateTime() {
        TimePickerDialog timePickerDialog = buildDialog();
        int minute = 18;
        timePickerDialog.updateTime(TARGET_HOUR, minute);

        // here call onSaveInstanceState is to check the data put by updateTime
        Bundle b = timePickerDialog.onSaveInstanceState();

        assertEquals(TARGET_HOUR, b.getInt(HOUR));
        assertEquals(minute, b.getInt(MINUTE));
    }

    @UiThreadTest
    @Test
    public void testOnRestoreInstanceState() {
        int minute = 27;
        Bundle b1 = new Bundle();
        b1.putInt(HOUR, TARGET_HOUR);
        b1.putInt(MINUTE, minute);
        b1.putBoolean(IS_24_HOUR, false);

        TimePickerDialog timePickerDialog = buildDialog();
        timePickerDialog.onRestoreInstanceState(b1);

        //here call onSaveInstanceState is to check the data put by onRestoreInstanceState
        Bundle b2 = timePickerDialog.onSaveInstanceState();

        assertEquals(TARGET_HOUR, b2.getInt(HOUR));
        assertEquals(minute, b2.getInt(MINUTE));
        assertFalse(b2.getBoolean(IS_24_HOUR));
    }

    private TimePickerDialog buildDialog() {
        return new TimePickerDialog(
                mContext, mOnTimeSetListener, TARGET_HOUR, TARGET_MINUTE, true);
    }
}
