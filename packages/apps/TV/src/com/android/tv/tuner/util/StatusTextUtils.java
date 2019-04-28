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

package com.android.tv.tuner.util;

import java.util.Locale;

/**
 * Utility class for tuner status messages.
 */
public class StatusTextUtils {
    private static final int PACKETS_PER_SEC_YELLOW = 1500;
    private static final int PACKETS_PER_SEC_RED = 1000;
    private static final int AUDIO_POSITION_MS_RATE_DIFF_YELLOW = 100;
    private static final int AUDIO_POSITION_MS_RATE_DIFF_RED = 200;
    private static final String COLOR_RED = "red";
    private static final String COLOR_YELLOW = "yellow";
    private static final String COLOR_GREEN = "green";
    private static final String COLOR_GRAY = "gray";

    private StatusTextUtils() { }

    /**
     * Returns tuner status warning message in HTML.
     *
     * <p>This is only called for debuging and always shown in english.</p>
     */
    public static String getStatusWarningInHTML(long packetsPerSec,
            int videoFrameDrop, int bytesInQueue,
            long audioPositionUs, long audioPositionUsRate,
            long audioPtsUs, long audioPtsUsRate,
            long videoPtsUs, long videoPtsUsRate) {
        StringBuffer buffer = new StringBuffer();

        // audioPosition should go in rate of 1000ms.
        long audioPositionMsRate = audioPositionUsRate / 1000;
        String audioPositionColor;
        if (Math.abs(audioPositionMsRate - 1000) > AUDIO_POSITION_MS_RATE_DIFF_RED) {
            audioPositionColor = COLOR_RED;
        } else if (Math.abs(audioPositionMsRate - 1000) > AUDIO_POSITION_MS_RATE_DIFF_YELLOW) {
            audioPositionColor = COLOR_YELLOW;
        } else {
            audioPositionColor = COLOR_GRAY;
        }
        buffer.append(String.format(Locale.US, "<font color=%s>", audioPositionColor));
        buffer.append(
                String.format(Locale.US, "audioPositionMs: %d (%d)<br>", audioPositionUs / 1000,
                        audioPositionMsRate));
        buffer.append("</font>\n");
        buffer.append("<font color=" + COLOR_GRAY + ">");
        buffer.append(String.format(Locale.US, "audioPtsMs: %d (%d, %d)<br>", audioPtsUs / 1000,
                        audioPtsUsRate / 1000, (audioPtsUs - audioPositionUs) / 1000));
        buffer.append(String.format(Locale.US, "videoPtsMs: %d (%d, %d)<br>", videoPtsUs / 1000,
                        videoPtsUsRate / 1000, (videoPtsUs - audioPositionUs) / 1000));
        buffer.append("</font>\n");

        appendStatusLine(buffer, "KbytesInQueue", bytesInQueue / 1000, 1, 10);
        buffer.append("<br/>");
        appendErrorStatusLine(buffer, "videoFrameDrop", videoFrameDrop, 0, 2);
        buffer.append("<br/>");
        appendStatusLine(buffer, "packetsPerSec", packetsPerSec, PACKETS_PER_SEC_RED,
                PACKETS_PER_SEC_YELLOW);
        return buffer.toString();
    }

    /**
     * Returns audio unavailable warning message in HTML.
     */
    public static String getAudioWarningInHTML(String msg) {
        return String.format("<font color=%s>%s</font>\n", COLOR_YELLOW, msg);
    }

    private static void appendStatusLine(StringBuffer buffer, String factorName, long value,
            int minRed, int minYellow) {
        buffer.append("<font color=");
        if (value <= minRed) {
            buffer.append(COLOR_RED);
        } else if (value <= minYellow) {
            buffer.append(COLOR_YELLOW);
        } else {
            buffer.append(COLOR_GREEN);
        }
        buffer.append(">");
        buffer.append(factorName);
        buffer.append(" : ");
        buffer.append(value);
        buffer.append("</font>");
    }

    private static void appendErrorStatusLine(StringBuffer buffer, String factorName, int value,
            int minGreen, int minYellow) {
        buffer.append("<font color=");
        if (value <= minGreen) {
            buffer.append(COLOR_GREEN);
        } else if (value <= minYellow) {
            buffer.append(COLOR_YELLOW);
        } else {
            buffer.append(COLOR_RED);
        }
        buffer.append(">");
        buffer.append(factorName);
        buffer.append(" : ");
        buffer.append(value);
        buffer.append("</font>");
    }
}
