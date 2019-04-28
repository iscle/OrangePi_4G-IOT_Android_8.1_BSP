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

import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;

import com.android.tv.data.Program;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class RoutineWatchEvaluator extends Recommender.Evaluator {
    // TODO: test and refine constant values in WatchedProgramRecommender in order to
    // improve the performance of this recommender.
    private static final double REQUIRED_MIN_SCORE = 0.15;
    @VisibleForTesting
    static final double MULTIPLIER_FOR_UNMATCHED_DAY_OF_WEEK = 0.7;
    private static final double TITLE_MATCH_WEIGHT = 0.5;
    private static final double TIME_MATCH_WEIGHT = 1 - TITLE_MATCH_WEIGHT;
    private static final long DIFF_MS_TOLERANCE_FOR_OLD_PROGRAM = TimeUnit.DAYS.toMillis(14);
    private static final long MAX_DIFF_MS_FOR_OLD_PROGRAM = TimeUnit.DAYS.toMillis(56);

    @Override
    public double evaluateChannel(long channelId) {
        ChannelRecord cr = getRecommender().getChannelRecord(channelId);
        if (cr == null) {
            return NOT_RECOMMENDED;
        }

        Program currentProgram = cr.getCurrentProgram();
        if (currentProgram == null) {
            return NOT_RECOMMENDED;
        }

        WatchedProgram[] watchHistory = cr.getWatchHistory();
        if (watchHistory.length < 1) {
            return NOT_RECOMMENDED;
        }

        Program watchedProgram = watchHistory[watchHistory.length - 1].getProgram();
        long startTimeDiffMsWithCurrentProgram = currentProgram.getStartTimeUtcMillis()
                - watchedProgram.getStartTimeUtcMillis();
        if (startTimeDiffMsWithCurrentProgram >= MAX_DIFF_MS_FOR_OLD_PROGRAM) {
            return NOT_RECOMMENDED;
        }

        double maxScore = NOT_RECOMMENDED;
        long watchedDurationMs = watchHistory[watchHistory.length - 1].getWatchedDurationMs();
        for (int i = watchHistory.length - 2; i >= 0; --i) {
            if (watchedProgram.getStartTimeUtcMillis()
                    == watchHistory[i].getProgram().getStartTimeUtcMillis()) {
                watchedDurationMs += watchHistory[i].getWatchedDurationMs();
            } else {
                double score = calculateRoutineWatchScore(
                        currentProgram, watchedProgram, watchedDurationMs);
                if (score >= REQUIRED_MIN_SCORE && score > maxScore) {
                    maxScore = score;
                }
                watchedProgram = watchHistory[i].getProgram();
                watchedDurationMs = watchHistory[i].getWatchedDurationMs();
                startTimeDiffMsWithCurrentProgram = currentProgram.getStartTimeUtcMillis()
                        - watchedProgram.getStartTimeUtcMillis();
                if (startTimeDiffMsWithCurrentProgram >= MAX_DIFF_MS_FOR_OLD_PROGRAM) {
                    return maxScore;
                }
            }
        }
        double score = calculateRoutineWatchScore(
                currentProgram, watchedProgram, watchedDurationMs);
        if (score >= REQUIRED_MIN_SCORE && score > maxScore) {
            maxScore = score;
        }
        return maxScore;
    }

    private static double calculateRoutineWatchScore(Program currentProgram, Program watchedProgram,
            long watchedDurationMs) {
        double timeMatchScore = calculateTimeMatchScore(currentProgram, watchedProgram);
        double titleMatchScore = calculateTitleMatchScore(
                currentProgram.getTitle(), watchedProgram.getTitle());
        double watchDurationScore = calculateWatchDurationScore(watchedProgram, watchedDurationMs);
        long diffMs = currentProgram.getStartTimeUtcMillis()
                - watchedProgram.getStartTimeUtcMillis();
        double multiplierForOldProgram = (diffMs < MAX_DIFF_MS_FOR_OLD_PROGRAM)
                ? 1.0 - (double) Math.max(diffMs - DIFF_MS_TOLERANCE_FOR_OLD_PROGRAM, 0)
                        / (MAX_DIFF_MS_FOR_OLD_PROGRAM - DIFF_MS_TOLERANCE_FOR_OLD_PROGRAM)
                : 0.0;
        return (titleMatchScore * TITLE_MATCH_WEIGHT + timeMatchScore * TIME_MATCH_WEIGHT)
                * watchDurationScore * multiplierForOldProgram;
    }

    @VisibleForTesting
    static double calculateTitleMatchScore(@Nullable String title1, @Nullable String title2) {
        if (TextUtils.isEmpty(title1) || TextUtils.isEmpty(title2)) {
            return 0;
        }
        List<String> wordList1 = splitTextToWords(title1);
        List<String> wordList2 = splitTextToWords(title2);
        if (wordList1.isEmpty() || wordList2.isEmpty()) {
            return 0;
        }
        int maxMatchedWordSeqLen = calculateMaximumMatchedWordSequenceLength(
                wordList1, wordList2);

        // F-measure score
        double precision = (double) maxMatchedWordSeqLen / wordList1.size();
        double recall = (double) maxMatchedWordSeqLen / wordList2.size();
        return 2.0 * precision * recall / (precision + recall);
    }

    @VisibleForTesting
    static int calculateMaximumMatchedWordSequenceLength(List<String> toSearchWords,
            List<String> toMatchWords) {
        int[] matchedWordSeqLen = new int[toMatchWords.size()];
        int maxMatchedWordSeqLen = 0;
        for (String word : toSearchWords) {
            for (int j = toMatchWords.size() - 1; j >= 0; --j) {
                if (word.equals(toMatchWords.get(j))) {
                    matchedWordSeqLen[j] = j > 0 ? matchedWordSeqLen[j - 1] + 1 : 1;
                } else {
                    maxMatchedWordSeqLen = Math.max(maxMatchedWordSeqLen, matchedWordSeqLen[j]);
                    matchedWordSeqLen[j] = 0;
                }
            }
        }
        for (int len : matchedWordSeqLen) {
            maxMatchedWordSeqLen = Math.max(maxMatchedWordSeqLen, len);
        }

        return maxMatchedWordSeqLen;
    }

    private static double calculateTimeMatchScore(Program p1, Program p2) {
        ProgramTime t1 = ProgramTime.createFromProgram(p1);
        ProgramTime t2 = ProgramTime.createFromProgram(p2);

        double dupTimeScore = calculateOverlappedIntervalScore(t1, t2);

        // F-measure score
        double precision = dupTimeScore / (t1.endTimeOfDayInSec - t1.startTimeOfDayInSec);
        double recall = dupTimeScore / (t2.endTimeOfDayInSec - t2.startTimeOfDayInSec);
        return 2.0 * precision * recall / (precision + recall);
    }

    @VisibleForTesting
    static double calculateOverlappedIntervalScore(ProgramTime t1, ProgramTime t2) {
        if (t1.dayChanged && !t2.dayChanged) {
            // Swap two values.
            return calculateOverlappedIntervalScore(t2, t1);
        }

        boolean sameDay = false;
        // Handle cases like (00:00 - 02:00) - (01:00 - 03:00) or (22:00 - 25:00) - (23:00 - 26:00).
        double score = Math.max(0, Math.min(t1.endTimeOfDayInSec, t2.endTimeOfDayInSec)
                - Math.max(t1.startTimeOfDayInSec, t2.startTimeOfDayInSec));
        if (score > 0) {
            sameDay = (t1.weekDay == t2.weekDay);
        } else if (t1.dayChanged != t2.dayChanged) {
            // To handle cases like t1 : (00:00 - 01:00) and t2 : (23:00 - 25:00).
            score = Math.max(0, Math.min(t1.endTimeOfDayInSec, t2.endTimeOfDayInSec - 24 * 60 * 60)
                    - t1.startTimeOfDayInSec);
            // Same day if next day of t2's start day equals to t1's start day. (1 <= weekDay <= 7)
            sameDay = (t1.weekDay == ((t2.weekDay % 7) + 1));
        }

        if (!sameDay) {
            score *= MULTIPLIER_FOR_UNMATCHED_DAY_OF_WEEK;
        }
        return score;
    }

    private static double calculateWatchDurationScore(Program program, long durationMs) {
        return (double) durationMs
                / (program.getEndTimeUtcMillis() - program.getStartTimeUtcMillis());
    }

    @VisibleForTesting
    static int getTimeOfDayInSec(Calendar time) {
        return time.get(Calendar.HOUR_OF_DAY) * 60 * 60
                + time.get(Calendar.MINUTE) * 60
                + time.get(Calendar.SECOND);
    }

    @VisibleForTesting
    static List<String> splitTextToWords(String text) {
        List<String> wordList = new ArrayList<>();
        BreakIterator boundary = BreakIterator.getWordInstance();
        boundary.setText(text);
        int start = boundary.first();
        for (int end = boundary.next(); end != BreakIterator.DONE;
                start = end, end = boundary.next()) {
            String word = text.substring(start, end);
            if (Character.isLetterOrDigit(word.charAt(0))) {
                wordList.add(word);
            }
        }
        return wordList;
    }

    @VisibleForTesting
    static class ProgramTime {
        final int startTimeOfDayInSec;
        final int endTimeOfDayInSec;
        final int weekDay;
        final boolean dayChanged;

        public static ProgramTime createFromProgram(Program p) {
            Calendar time = Calendar.getInstance();

            time.setTimeInMillis(p.getStartTimeUtcMillis());
            int weekDay = time.get(Calendar.DAY_OF_WEEK);
            int startTimeOfDayInSec = getTimeOfDayInSec(time);

            time.setTimeInMillis(p.getEndTimeUtcMillis());
            boolean dayChanged = (weekDay != time.get(Calendar.DAY_OF_WEEK));
            // Set maximum program duration time to 12 hours.
            int endTimeOfDayInSec = startTimeOfDayInSec +
                    (int) Math.min(p.getEndTimeUtcMillis() - p.getStartTimeUtcMillis(),
                            TimeUnit.HOURS.toMillis(12)) / 1000;

            return new ProgramTime(startTimeOfDayInSec, endTimeOfDayInSec, weekDay, dayChanged);
        }

        private ProgramTime(int startTimeOfDayInSec, int endTimeOfDayInSec, int weekDay,
                boolean dayChanged) {
            this.startTimeOfDayInSec = startTimeOfDayInSec;
            this.endTimeOfDayInSec = endTimeOfDayInSec;
            this.weekDay = weekDay;
            this.dayChanged = dayChanged;
        }
    }
}
