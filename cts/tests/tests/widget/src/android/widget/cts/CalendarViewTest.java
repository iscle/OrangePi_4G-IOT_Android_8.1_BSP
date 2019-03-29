/*
 * Copyright (C) 2016 The Android Open Source Project
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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.annotation.ColorInt;
import android.app.Instrumentation;
import android.graphics.Rect;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.ViewGroup;
import android.widget.CalendarView;
import android.widget.ScrollView;
import android.widget.cts.util.TestUtils;

import com.android.compatibility.common.util.CtsTouchUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Calendar;
import java.util.GregorianCalendar;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class CalendarViewTest {
    private Instrumentation mInstrumentation;
    private CalendarViewCtsActivity mActivity;
    private CalendarView mCalendarViewMaterial;
    private CalendarView mCalendarViewHolo;

    @Rule
    public ActivityTestRule<CalendarViewCtsActivity> mActivityRule =
            new ActivityTestRule<>(CalendarViewCtsActivity.class);

    @UiThreadTest
    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
        mCalendarViewMaterial = (CalendarView) mActivity.findViewById(R.id.calendar_view_material);
        mCalendarViewHolo = (CalendarView) mActivity.findViewById(R.id.calendar_view_holoyolo);

        // Initialize both calendar views to the current date
        final long currentDate = new GregorianCalendar().getTime().getTime();
        mCalendarViewMaterial.setDate(currentDate);
        mCalendarViewHolo.setDate(currentDate);
    }

    @UiThreadTest
    @Test
    public void testConstructor() {
        new CalendarView(mActivity);

        new CalendarView(mActivity, null);

        new CalendarView(mActivity, null, android.R.attr.calendarViewStyle);

        new CalendarView(mActivity, null, 0, android.R.style.Widget_DeviceDefault_CalendarView);

        new CalendarView(mActivity, null, 0,
                android.R.style.Widget_DeviceDefault_Light_CalendarView);

        new CalendarView(mActivity, null, 0, android.R.style.Widget_Material_CalendarView);

        new CalendarView(mActivity, null, 0, android.R.style.Widget_Material_Light_CalendarView);
    }

    @UiThreadTest
    @Test
    public void testAccessDate() {
        // Go back one year
        final Calendar newCalendar = new GregorianCalendar();
        newCalendar.set(Calendar.YEAR, newCalendar.get(Calendar.YEAR) - 1);
        final long yearAgoDate = newCalendar.getTime().getTime();

        mCalendarViewMaterial.setDate(yearAgoDate);
        assertEquals(yearAgoDate, mCalendarViewMaterial.getDate());

        // Go forward two years (one year from current date in aggregate)
        newCalendar.set(Calendar.YEAR, newCalendar.get(Calendar.YEAR) + 2);
        final long yearHenceDate = newCalendar.getTime().getTime();

        mCalendarViewMaterial.setDate(yearHenceDate, true, false);
        assertEquals(yearHenceDate, mCalendarViewMaterial.getDate());
    }

    @UiThreadTest
    @Test
    public void testAccessMinMaxDate() {
        // Use a range of minus/plus one year as min/max dates
        final Calendar minCalendar = new GregorianCalendar();
        minCalendar.set(Calendar.YEAR, minCalendar.get(Calendar.YEAR) - 1);
        final Calendar maxCalendar = new GregorianCalendar();
        maxCalendar.set(Calendar.YEAR, maxCalendar.get(Calendar.YEAR) + 1);

        final long minDate = minCalendar.getTime().getTime();
        final long maxDate = maxCalendar.getTime().getTime();

        mCalendarViewMaterial.setMinDate(minDate);
        mCalendarViewMaterial.setMaxDate(maxDate);

        assertEquals(mCalendarViewMaterial.getMinDate(), minDate);
        assertEquals(mCalendarViewMaterial.getMaxDate(), maxDate);
    }

    @UiThreadTest
    @Test
    public void testMinMaxRangeRestrictionsHolo() {
        // Use a range of minus/plus one year as min/max dates.
        final Calendar minCalendar = new GregorianCalendar();
        minCalendar.set(Calendar.YEAR, minCalendar.get(Calendar.YEAR) - 1);
        final Calendar maxCalendar = new GregorianCalendar();
        maxCalendar.set(Calendar.YEAR, maxCalendar.get(Calendar.YEAR) + 1);
        final long minDate = minCalendar.getTime().getTime();
        final long maxDate = maxCalendar.getTime().getTime();

        mCalendarViewHolo.setMinDate(minDate);
        mCalendarViewHolo.setMaxDate(maxDate);

        try {
            mCalendarViewHolo.setDate(minDate - 1);
            fail("Should throw IllegalArgumentException, date is before minDate");
        } catch (IllegalArgumentException e) {
        }

        try {
            mCalendarViewHolo.setDate(maxDate + 1);
            fail("Should throw IllegalArgumentException, date is after maxDate");
        } catch (IllegalArgumentException e) {
        }
    }

    @UiThreadTest
    @Test
    public void testMinMaxRangeClampingMaterial() {
        // Use a range of minus/plus one year as min/max dates.
        final Calendar minCalendar = new GregorianCalendar();
        minCalendar.set(Calendar.YEAR, minCalendar.get(Calendar.YEAR) - 1);
        final Calendar maxCalendar = new GregorianCalendar();
        maxCalendar.set(Calendar.YEAR, maxCalendar.get(Calendar.YEAR) + 1);
        final long minDate = minCalendar.getTime().getTime();
        final long maxDate = maxCalendar.getTime().getTime();

        mCalendarViewMaterial.setMinDate(minDate);
        mCalendarViewMaterial.setMaxDate(maxDate);
        mCalendarViewMaterial.setDate(minDate + 1);

        // Expecting to get back the same values because minDate < date < maxDate.
        assertEquals(minDate, mCalendarViewMaterial.getMinDate());
        assertEquals(maxDate, mCalendarViewMaterial.getMaxDate());
        assertEquals(minDate + 1, mCalendarViewMaterial.getDate());

        // Set a new minDate after the current date.
        mCalendarViewMaterial.setMinDate(minDate + 20);
        assertEquals(minDate + 20, mCalendarViewMaterial.getMinDate());
        assertEquals(maxDate, mCalendarViewMaterial.getMaxDate());
        // Verify date clamping when calling setMinDate().
        assertEquals(minDate + 20, mCalendarViewMaterial.getDate());

        mCalendarViewMaterial.setMinDate(minDate);
        // Set a maxDate before the current date.
        mCalendarViewMaterial.setMaxDate(minDate + 10);
        assertEquals(minDate, mCalendarViewMaterial.getMinDate());
        assertEquals(minDate + 10, mCalendarViewMaterial.getMaxDate());
        // Verify date clamping when calling setMaxDate().
        assertEquals(minDate + 10, mCalendarViewMaterial.getDate());

        // Set a date before minDate.
        mCalendarViewMaterial.setDate(mCalendarViewMaterial.getMinDate() - 1);
        // Expect date clamped to minDate.
        assertEquals(mCalendarViewMaterial.getMinDate(), mCalendarViewMaterial.getDate());

        // Set a date after maxDate.
        mCalendarViewMaterial.setDate(mCalendarViewMaterial.getMaxDate() + 1);
        // Expect date clamped to maxDate.
        assertEquals(mCalendarViewMaterial.getMaxDate(), mCalendarViewMaterial.getDate());
    }

    private void verifyOnDateChangeListener(CalendarView calendarView,
            boolean onlyAllowOneChangeEvent) throws Throwable {
        final CalendarView.OnDateChangeListener mockDateChangeListener =
                mock(CalendarView.OnDateChangeListener.class);
        calendarView.setOnDateChangeListener(mockDateChangeListener);

        // Go back to September 2008
        final Calendar calendar = new GregorianCalendar();
        calendar.set(Calendar.YEAR, 2008);
        calendar.set(Calendar.MONTH, Calendar.SEPTEMBER);
        calendar.set(Calendar.DAY_OF_MONTH, 16);
        mActivityRule.runOnUiThread(
                () -> calendarView.setDate(calendar.getTime().getTime(), false, true));
        mInstrumentation.waitForIdleSync();

        // Get bounds of 09/07/2008
        calendar.set(Calendar.DAY_OF_MONTH, 7);
        final Rect dayBounds = new Rect();
        final boolean getDayBoundsSuccess = calendarView.getBoundsForDate(
                calendar.getTime().getTime(), dayBounds);
        assertTrue(getDayBoundsSuccess);

        if (onlyAllowOneChangeEvent) {
            verifyZeroInteractions(mockDateChangeListener);
        }

        // Use instrumentation to emulate a tap on 09/07/2008
        CtsTouchUtils.emulateTapOnView(mInstrumentation, calendarView,
                dayBounds.left + dayBounds.width() / 2,
                dayBounds.top + dayBounds.height() / 2);

        verify(mockDateChangeListener, times(1)).onSelectedDayChange(calendarView,
                2008, Calendar.SEPTEMBER, 7);
        if (onlyAllowOneChangeEvent) {
            verifyNoMoreInteractions(mockDateChangeListener);
        }
    }

    @Test
    public void testOnDateChangeListenerHolo() throws Throwable {
        // Scroll the Holo calendar view all the way up so it's fully visible
        final ScrollView scroller = (ScrollView) mActivity.findViewById(R.id.scroller);
        final ViewGroup container = (ViewGroup) scroller.findViewById(R.id.container);

        mActivityRule.runOnUiThread(() -> scroller.scrollTo(0, container.getHeight()));
        // Note that in pre-Material world we are "allowing" the CalendarView to notify
        // the date change listener on multiple occasions. This is the old behavior of the widget.
        verifyOnDateChangeListener(mCalendarViewHolo, false);
    }

    @Test
    public void testOnDateChangeListenerMaterial() throws Throwable {
        // Note that in Material world only "real" date change events are allowed to be reported
        // to our listener. This is the new behavior of the widget.
        verifyOnDateChangeListener(mCalendarViewMaterial, true);
    }

    @UiThreadTest
    @Test
    public void testAppearanceMaterial() {
        // The logic in this method is performed on a Material-styled CalendarView and
        // non-deprecated attributes / visual appearance APIs

        // Test the initial appearance defined in the layout XML
        assertEquals(2, mCalendarViewMaterial.getFirstDayOfWeek());
        assertEquals(R.style.TextAppearance_WithColor,
                mCalendarViewMaterial.getDateTextAppearance());
        assertEquals(R.style.TextAppearance_WithColorGreen,
                mCalendarViewMaterial.getWeekDayTextAppearance());

        // Change the visual appearance of the widget
        mCalendarViewMaterial.setFirstDayOfWeek(Calendar.TUESDAY);
        mCalendarViewMaterial.setDateTextAppearance(R.style.TextAppearance_WithColorBlue);
        mCalendarViewMaterial.setWeekDayTextAppearance(R.style.TextAppearance_WithColorMagenta);

        assertEquals(Calendar.TUESDAY, mCalendarViewMaterial.getFirstDayOfWeek());
        assertEquals(R.style.TextAppearance_WithColorBlue,
                mCalendarViewMaterial.getDateTextAppearance());
        assertEquals(R.style.TextAppearance_WithColorMagenta,
                mCalendarViewMaterial.getWeekDayTextAppearance());
    }

    @UiThreadTest
    @Test
    public void testAppearanceHolo() {
        // All the logic in this method is performed on a Holo-styled CalendarView, as
        // under Material design we are ignoring most of these decorative attributes

        // Test the initial appearance defined in the layout XML
        assertEquals(3, mCalendarViewHolo.getFirstDayOfWeek());
        assertEquals(5, mCalendarViewHolo.getShownWeekCount());
        assertFalse(mCalendarViewHolo.getShowWeekNumber());
        assertEquals(R.style.TextAppearance_WithColor,
                mCalendarViewHolo.getDateTextAppearance());
        assertEquals(R.style.TextAppearance_WithColorGreen,
                mCalendarViewHolo.getWeekDayTextAppearance());
        assertEquals(mActivity.getColor(R.color.calendarview_week_background),
                mCalendarViewHolo.getSelectedWeekBackgroundColor());
        assertEquals(mActivity.getColor(R.color.calendarview_focusedmonthdate),
                mCalendarViewHolo.getFocusedMonthDateColor());
        assertEquals(mActivity.getColor(R.color.calendarview_unfocusedmonthdate),
                mCalendarViewHolo.getUnfocusedMonthDateColor());
        TestUtils.assertAllPixelsOfColor("Selected date vertical bar blue",
                mCalendarViewHolo.getSelectedDateVerticalBar(), 40, 40, true, 0xFF0000FF, 1, true);

        // Change the visual appearance of the widget
        final @ColorInt int newSelectedWeekBackgroundColor =
                mActivity.getColor(R.color.calendarview_week_background_new);
        final @ColorInt int newFocusedMonthDateColor =
                mActivity.getColor(R.color.calendarview_focusedmonthdate_new);
        final @ColorInt int newUnfocusedMonthDataColor =
                mActivity.getColor(R.color.calendarview_unfocusedmonthdate_new);
        final @ColorInt int newWeekNumberColor =
                mActivity.getColor(R.color.calendarview_week_number_new);
        final @ColorInt int newWeekSeparatorLineColor =
                mActivity.getColor(R.color.calendarview_week_separatorline_new);

        mCalendarViewHolo.setFirstDayOfWeek(Calendar.SUNDAY);
        mCalendarViewHolo.setShownWeekCount(4);
        mCalendarViewHolo.setShowWeekNumber(true);
        mCalendarViewHolo.setDateTextAppearance(R.style.TextAppearance_WithColorBlue);
        mCalendarViewHolo.setWeekDayTextAppearance(R.style.TextAppearance_WithColorMagenta);
        mCalendarViewHolo.setSelectedWeekBackgroundColor(newSelectedWeekBackgroundColor);
        mCalendarViewHolo.setFocusedMonthDateColor(newFocusedMonthDateColor);
        mCalendarViewHolo.setUnfocusedMonthDateColor(newUnfocusedMonthDataColor);
        mCalendarViewHolo.setWeekNumberColor(newWeekNumberColor);
        mCalendarViewHolo.setWeekSeparatorLineColor(newWeekSeparatorLineColor);

        assertEquals(Calendar.SUNDAY, mCalendarViewHolo.getFirstDayOfWeek());
        assertEquals(4, mCalendarViewHolo.getShownWeekCount());
        assertTrue(mCalendarViewHolo.getShowWeekNumber());
        assertEquals(R.style.TextAppearance_WithColorBlue,
                mCalendarViewHolo.getDateTextAppearance());
        assertEquals(R.style.TextAppearance_WithColorMagenta,
                mCalendarViewHolo.getWeekDayTextAppearance());
        assertEquals(newSelectedWeekBackgroundColor,
                mCalendarViewHolo.getSelectedWeekBackgroundColor());
        assertEquals(newFocusedMonthDateColor,
                mCalendarViewHolo.getFocusedMonthDateColor());
        assertEquals(newUnfocusedMonthDataColor,
                mCalendarViewHolo.getUnfocusedMonthDateColor());
        assertEquals(newWeekNumberColor,
                mCalendarViewHolo.getWeekNumberColor());
        assertEquals(newWeekSeparatorLineColor,
                mCalendarViewHolo.getWeekSeparatorLineColor());

        mCalendarViewHolo.setSelectedDateVerticalBar(R.drawable.yellow_fill);
        TestUtils.assertAllPixelsOfColor("Selected date vertical bar yellow",
                mCalendarViewHolo.getSelectedDateVerticalBar(), 40, 40, true, 0xFFFFFF00, 1, true);

        mCalendarViewHolo.setSelectedDateVerticalBar(
                mActivity.getDrawable(R.drawable.magenta_fill));
        TestUtils.assertAllPixelsOfColor("Selected date vertical bar magenta",
                mCalendarViewHolo.getSelectedDateVerticalBar(), 40, 40, true, 0xFFFF00FF, 1, true);
    }
}
