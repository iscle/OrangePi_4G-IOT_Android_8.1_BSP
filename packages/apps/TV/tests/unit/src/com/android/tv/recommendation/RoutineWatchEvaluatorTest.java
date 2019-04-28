/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tv.recommendation;

import static org.junit.Assert.assertEquals;

import android.support.test.filters.SmallTest;
import android.test.MoreAsserts;

import com.android.tv.data.Program;
import com.android.tv.recommendation.RoutineWatchEvaluator.ProgramTime;

import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

@SmallTest
public class RoutineWatchEvaluatorTest extends EvaluatorTestCase<RoutineWatchEvaluator> {
    private static class ScoredItem implements Comparable<ScoredItem> {
        private final String mBase;
        private final String mText;
        private final double mScore;

        private ScoredItem(String base, String text) {
            this.mBase = base;
            this.mText = text;
            this.mScore = RoutineWatchEvaluator.calculateTitleMatchScore(base, text);
        }

        @Override
        public int compareTo(ScoredItem scoredItem) {
            return Double.compare(mScore, scoredItem.mScore);
        }

        @Override
        public String toString() {
            return mBase + " scored with " + mText + " is " + mScore;
        }
    }

    private static ScoredItem score(String t1, String t2) {
        return new ScoredItem(t1, t2);
    }

    @Override
    public RoutineWatchEvaluator createEvaluator() {
        return new RoutineWatchEvaluator();
    }

    @Test
    public void testSplitTextToWords() {
        assertSplitTextToWords("");
        assertSplitTextToWords("Google", "Google");
        assertSplitTextToWords("The Big Bang Theory", "The", "Big", "Bang", "Theory");
        assertSplitTextToWords("Hello, world!", "Hello", "world");
        assertSplitTextToWords("Adam's Rib", "Adam's", "Rib");
        assertSplitTextToWords("G.I. Joe", "G.I", "Joe");
        assertSplitTextToWords("A.I.", "A.I");
    }

    @Test
    public void testCalculateMaximumMatchedWordSequenceLength() {
        assertMaximumMatchedWordSequenceLength(0, "", "Google");
        assertMaximumMatchedWordSequenceLength(2, "The Big Bang Theory", "Big Bang");
        assertMaximumMatchedWordSequenceLength(2, "The Big Bang Theory", "Theory Of Big Bang");
        assertMaximumMatchedWordSequenceLength(4, "The Big Bang Theory", "The Big Bang Theory");
        assertMaximumMatchedWordSequenceLength(1, "Modern Family", "Family Guy");
        assertMaximumMatchedWordSequenceLength(1, "The Simpsons", "The Walking Dead");
        assertMaximumMatchedWordSequenceLength(3, "Game Of Thrones 1", "Game Of Thrones 6");
        assertMaximumMatchedWordSequenceLength(0, "Dexter", "Friends");
    }

    @Test
    public void testCalculateTitleMatchScore_empty() {
        assertEqualScores(0.0, RoutineWatchEvaluator.calculateTitleMatchScore("", ""));
        assertEqualScores(0.0, RoutineWatchEvaluator.calculateTitleMatchScore("foo", ""));
        assertEqualScores(0.0, RoutineWatchEvaluator.calculateTitleMatchScore("", "foo"));
    }

    @Test
    public void testCalculateTitleMatchScore_spaces() {
        assertEqualScores(0.0, RoutineWatchEvaluator.calculateTitleMatchScore(" ", " "));
        assertEqualScores(0.0, RoutineWatchEvaluator.calculateTitleMatchScore("foo", " "));
        assertEqualScores(0.0, RoutineWatchEvaluator.calculateTitleMatchScore(" ", "foo"));
    }


    @Test
    public void testCalculateTitleMatchScore_null() {
        assertEqualScores(0.0, RoutineWatchEvaluator.calculateTitleMatchScore(null, null));
        assertEqualScores(0.0, RoutineWatchEvaluator.calculateTitleMatchScore("foo", null));
        assertEqualScores(0.0, RoutineWatchEvaluator.calculateTitleMatchScore(null, "foo"));
    }

    @Test
    public void testCalculateTitleMatchScore_longerMatchIsBetter() {
        String base = "foo bar baz";
        assertInOrder(
                score(base, ""),
                score(base, "bar"),
                score(base, "foo bar"),
                score(base, "foo bar baz"));
    }

    @Test
    public void testProgramTime_createFromProgram() {
        Calendar time = Calendar.getInstance();
        int todayDayOfWeek = time.get(Calendar.DAY_OF_WEEK);
        // Value of DayOfWeek is between 1 and 7 (inclusive).
        int tomorrowDayOfWeek = (todayDayOfWeek % 7) + 1;

        // Today 00:00 - 01:00.
        ProgramTime programTimeToday0000_0100 = ProgramTime.createFromProgram(
                createDummyProgram(todayAtHourMin(0, 0), TimeUnit.HOURS.toMillis(1)));
        assertProgramTime(todayDayOfWeek, hourMinuteToSec(0, 0), hourMinuteToSec(1, 0),
                programTimeToday0000_0100);

        // Today 23:30 - 24:30.
        ProgramTime programTimeToday2330_2430 = ProgramTime.createFromProgram(
                createDummyProgram(todayAtHourMin(23, 30), TimeUnit.HOURS.toMillis(1)));
        assertProgramTime(todayDayOfWeek, hourMinuteToSec(23, 30), hourMinuteToSec(24, 30),
                programTimeToday2330_2430);

        // Tomorrow 00:00 - 01:00.
        ProgramTime programTimeTomorrow0000_0100 = ProgramTime.createFromProgram(
                createDummyProgram(tomorrowAtHourMin(0, 0), TimeUnit.HOURS.toMillis(1)));
        assertProgramTime(tomorrowDayOfWeek, hourMinuteToSec(0, 0), hourMinuteToSec(1, 0),
                programTimeTomorrow0000_0100);

        // Tomorrow 23:30 - 24:30.
        ProgramTime programTimeTomorrow2330_2430 = ProgramTime.createFromProgram(
                createDummyProgram(tomorrowAtHourMin(23, 30), TimeUnit.HOURS.toMillis(1)));
        assertProgramTime(tomorrowDayOfWeek, hourMinuteToSec(23, 30), hourMinuteToSec(24, 30),
                programTimeTomorrow2330_2430);

        // Today 18:00 - Tomorrow 12:00.
        ProgramTime programTimeToday1800_3600 = ProgramTime.createFromProgram(
                createDummyProgram(todayAtHourMin(18, 0), TimeUnit.HOURS.toMillis(18)));
        // Maximum duration of ProgramTime is 12 hours.
        // So, this program looks like it ends at Tomorrow 06:00 (30:00).
        assertProgramTime(todayDayOfWeek, hourMinuteToSec(18, 0), hourMinuteToSec(30, 0),
                programTimeToday1800_3600);
    }

    @Test
    public void testCalculateOverlappedIntervalScore() {
        // Today 21:00 - 24:00.
        ProgramTime programTimeToday2100_2400 = ProgramTime.createFromProgram(
                createDummyProgram(todayAtHourMin(21, 0), TimeUnit.HOURS.toMillis(3)));
        // Today 22:00 - 01:00.
        ProgramTime programTimeToday2200_0100 = ProgramTime.createFromProgram(
                createDummyProgram(todayAtHourMin(22, 0), TimeUnit.HOURS.toMillis(3)));
        // Tomorrow 00:00 - 03:00.
        ProgramTime programTimeTomorrow0000_0300 = ProgramTime.createFromProgram(
                createDummyProgram(tomorrowAtHourMin(0, 0), TimeUnit.HOURS.toMillis(3)));
        // Tomorrow 20:00 - Tomorrow 23:00.
        ProgramTime programTimeTomorrow2000_2300 = ProgramTime.createFromProgram(
                createDummyProgram(tomorrowAtHourMin(20, 0), TimeUnit.HOURS.toMillis(3)));

        // Check intersection time and commutative law in all cases.
        int oneHourInSec = hourMinuteToSec(1, 0);
        assertOverlappedIntervalScore(2 * oneHourInSec, true, programTimeToday2100_2400,
                programTimeToday2200_0100);
        assertOverlappedIntervalScore(0, false, programTimeToday2100_2400,
                programTimeTomorrow0000_0300);
        assertOverlappedIntervalScore(2 * oneHourInSec, false, programTimeToday2100_2400,
                programTimeTomorrow2000_2300);
        assertOverlappedIntervalScore(oneHourInSec, true, programTimeToday2200_0100,
                programTimeTomorrow0000_0300);
        assertOverlappedIntervalScore(oneHourInSec, false, programTimeToday2200_0100,
                programTimeTomorrow2000_2300);
        assertOverlappedIntervalScore(0, false, programTimeTomorrow0000_0300,
                programTimeTomorrow2000_2300);
    }

    @Test
    public void testGetTimeOfDayInSec() {
        // Time was set as 00:00:00. So, getTimeOfDay must returns 0 (= 0 * 60 * 60 + 0 * 60 + 0).
        assertEquals("TimeOfDayInSec", hourMinuteToSec(0, 0),
                RoutineWatchEvaluator.getTimeOfDayInSec(todayAtHourMin(0, 0)));

        // Time was set as 23:59:59. So, getTimeOfDay must returns 23 * 60 + 60 + 59 * 60 + 59.
        assertEquals("TimeOfDayInSec", hourMinuteSecondToSec(23, 59, 59),
                RoutineWatchEvaluator.getTimeOfDayInSec(todayAtHourMinSec(23, 59, 59)));
    }

    private void assertSplitTextToWords(String text, String... words) {
        List<String> wordList = RoutineWatchEvaluator.splitTextToWords(text);
        MoreAsserts.assertContentsInOrder(wordList, words);
    }

    private void assertMaximumMatchedWordSequenceLength(int expectedLength, String text1,
            String text2) {
        List<String> wordList1 = RoutineWatchEvaluator.splitTextToWords(text1);
        List<String> wordList2 = RoutineWatchEvaluator.splitTextToWords(text2);
        assertEquals("MaximumMatchedWordSequenceLength", expectedLength,
                RoutineWatchEvaluator.calculateMaximumMatchedWordSequenceLength(
                        wordList1, wordList2));
        assertEquals("MaximumMatchedWordSequenceLength", expectedLength,
                RoutineWatchEvaluator.calculateMaximumMatchedWordSequenceLength(
                        wordList2, wordList1));
    }

    private void assertProgramTime(int expectedWeekDay, int expectedStartTimeOfDayInSec,
            int expectedEndTimeOfDayInSec, ProgramTime actualProgramTime) {
        assertEquals("Weekday", expectedWeekDay, actualProgramTime.weekDay);
        assertEquals("StartTimeOfDayInSec", expectedStartTimeOfDayInSec,
                actualProgramTime.startTimeOfDayInSec);
        assertEquals("EndTimeOfDayInSec", expectedEndTimeOfDayInSec,
                actualProgramTime.endTimeOfDayInSec);
    }

    private void assertOverlappedIntervalScore(int expectedSeconds, boolean overlappedOnSameDay,
            ProgramTime t1, ProgramTime t2) {
        double score = expectedSeconds;
        if (!overlappedOnSameDay) {
            score *= RoutineWatchEvaluator.MULTIPLIER_FOR_UNMATCHED_DAY_OF_WEEK;
        }
        // Two tests for testing commutative law.
        assertEqualScores("OverlappedIntervalScore", score,
                RoutineWatchEvaluator.calculateOverlappedIntervalScore(t1, t2));
        assertEqualScores("OverlappedIntervalScore", score,
                RoutineWatchEvaluator.calculateOverlappedIntervalScore(t2, t1));
    }

    private int hourMinuteToSec(int hour, int minute) {
        return hourMinuteSecondToSec(hour, minute, 0);
    }

    private int hourMinuteSecondToSec(int hour, int minute, int second) {
        return hour * 60 * 60 + minute * 60 + second;
    }

    private Calendar todayAtHourMin(int hour, int minute) {
        return todayAtHourMinSec(hour, minute, 0);
    }

    private Calendar todayAtHourMinSec(int hour, int minute, int second) {
        Calendar time = Calendar.getInstance();
        time.set(Calendar.HOUR_OF_DAY, hour);
        time.set(Calendar.MINUTE, minute);
        time.set(Calendar.SECOND, second);
        return time;
    }

    private Calendar tomorrowAtHourMin(int hour, int minute) {
        Calendar time = todayAtHourMin(hour, minute);
        time.add(Calendar.DATE, 1);
        return time;
    }

    private Program createDummyProgram(Calendar startTime, long programDurationMs) {
        long startTimeMs = startTime.getTimeInMillis();

        return new Program.Builder().setStartTimeUtcMillis(startTimeMs)
                .setEndTimeUtcMillis(startTimeMs + programDurationMs).build();
    }

    private static <T> void assertInOrder(T... items) {
        TreeSet<T> copy = new TreeSet<>(Arrays.asList(items));
        MoreAsserts.assertContentsInOrder(copy, items);
    }
}
