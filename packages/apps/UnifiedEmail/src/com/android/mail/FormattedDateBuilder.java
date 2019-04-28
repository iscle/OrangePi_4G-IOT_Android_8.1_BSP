/*
 * Copyright (C) 2012 Google Inc.
 * Licensed to The Android Open Source Project.
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
package com.android.mail;

import android.content.Context;
import android.text.format.DateUtils;

import java.util.Calendar;
import java.util.Formatter;

/**
 * Convenience class to efficiently make multiple short date strings. Instantiating and reusing
 * one of these builders is faster than repeatedly bringing up all the locale stuff.
 *
 */
public class FormattedDateBuilder {

    private final StringBuilder sb;
    private final Formatter dateFormatter;
    private final Context mContext;

    public FormattedDateBuilder(Context context) {
        mContext = context;
        sb = new StringBuilder();
        dateFormatter = new Formatter(sb);
    }

    /**
     * This is used in the conversation list, and headers of collapsed messages in
     * threaded conversations.
     * Times on today's date will just display time, e.g. 8:15 AM
     * Times not today, but within the same calendar year will display absolute date, e.g. Nov 6
     * Times not in the same year display a numeric absolute date, e.g. 11/18/12
     *
     * @param when The time to generate a formatted date for
     * @return The formatted date
     */
    public CharSequence formatShortDateTime(long when) {
        if (DateUtils.isToday(when)) {
            return formatDateTime(when, DateUtils.FORMAT_SHOW_TIME);
        } else if (isCurrentYear(when)) {
            return formatDateTime(when, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH);
        } else {
            return formatDateTime(when, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NUMERIC_DATE);
        }
    }

    /**
     * This is used in regular message headers.
     * Times on today's date will just display time, e.g. 8:15 AM
     * Times not today, but within two weeks ago will display relative date and time,
     * e.g. 6 days ago, 8:15 AM
     * Times more than two weeks ago but within the same calendar year will display
     * absolute date and time, e.g. Nov 6, 8:15 AM
     * Times not in the same year display a numeric absolute date, e.g. 11/18/12
     *
     * @param when The time to generate a formatted date for
     * @return The formatted date
     */
    public CharSequence formatLongDateTime(long when) {
        if (DateUtils.isToday(when)) {
            return formatDateTime(when, DateUtils.FORMAT_SHOW_TIME);
        } else if (isCurrentYear(when)) {
            return getRelativeDateTimeString(mContext, when, DateUtils.DAY_IN_MILLIS,
                    2 * DateUtils.WEEK_IN_MILLIS,
                    DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH);
        } else {
            return formatDateTime(when, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NUMERIC_DATE);
        }
    }

    /**
     * This is used in expanded details headers.
     * Displays full date and time e.g. Tue, Nov 18, 2012, 8:15 AM, or
     * Yesterday, Nov 18, 2012, 8:15 AM
     *
     * @param when The time to generate a formatted date for
     * @return The formatted date
     */
    public CharSequence formatFullDateTime(long when) {
        sb.setLength(0);
        DateUtils.formatDateRange(mContext, dateFormatter, when, when,
                DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE |
                DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_ABBREV_ALL);
        return sb.toString();
    }

    /**
     * This is used for displaying dates when printing.
     * Displays the full date, e.g. Tue, Nov 18, 2012 at 8:15 PM
     *
     * @param when The time to generate a formatted date for
     * @return The formatted date
     */
    public String formatDateTimeForPrinting(long when) {
        return mContext.getString(R.string.date_message_received_print,
                formatDateTime(when, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_WEEKDAY |
                        DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_ABBREV_ALL),
                        formatDateTime(when, DateUtils.FORMAT_SHOW_TIME));
    }

    private boolean isCurrentYear(long when) {
        final Calendar nowCal = Calendar.getInstance();
        final Calendar whenCal = Calendar.getInstance();
        whenCal.setTimeInMillis(when);
        return (nowCal.get(Calendar.YEAR) == whenCal.get(Calendar.YEAR));
    }

    private CharSequence formatDateTime(long when, int flags) {
        sb.setLength(0);
        DateUtils.formatDateRange(mContext, dateFormatter, when, when, flags);
        return sb.toString();
    }

    /**
     * A port of
     * {@link DateUtils#getRelativeDateTimeString(android.content.Context, long, long, long, int)}
     * that does not include the time in strings like "2 days ago".
     */
    private static CharSequence getRelativeDateTimeString(Context c, long time, long minResolution,
            long transitionResolution, int flags) {
        final long now = System.currentTimeMillis();
        final long duration = Math.abs(now - time);

        // getRelativeTimeSpanString() doesn't correctly format relative dates
        // above a week or exact dates below a day, so clamp
        // transitionResolution as needed.
        if (transitionResolution > DateUtils.WEEK_IN_MILLIS) {
            transitionResolution = DateUtils.WEEK_IN_MILLIS;
        } else if (transitionResolution < DateUtils.DAY_IN_MILLIS) {
            transitionResolution = DateUtils.DAY_IN_MILLIS;
        }

        if (duration < transitionResolution) {
            return DateUtils.getRelativeTimeSpanString(time, now, minResolution, flags);
        } else {
            return DateUtils.getRelativeTimeSpanString(c, time, false);
        }
    }
}
