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
package android.mediastress.cts.preconditions.app;

import com.android.compatibility.common.util.DynamicConfigDeviceSide;
import com.android.compatibility.common.util.MediaUtils;

import android.app.Instrumentation;
import android.media.MediaFormat;
import android.os.Bundle;
import android.support.test.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test class that uses device-side media APIs to determine up to which resolution MediaPreparer
 * should copy media files for CtsMediaStressTestCases.
 */
@RunWith(JUnit4.class)
public class MediaPreparerAppTest {

    /** The module name used to retrieve Dynamic Configuration data */
    private static final String MODULE_NAME = "CtsMediaStressTestCases";

    /** The default (minimum) resolution of media file to copy to the device */
    private static final int DEFAULT_MAX_WIDTH = 480;
    private static final int DEFAULT_MAX_HEIGHT = 360;

    /** Instrumentation status code used to write resolution to metrics */
    private static final int INST_STATUS_IN_PROGRESS = 2;

    /** Helper class for generating and retrieving width-height pairs */
    private static final class Resolution {
        // regex that matches a resolution string
        private static final String PATTERN = "(\\d+)x(\\d+)";
        // group indices for accessing resolution witdh/height from a Matcher created from PATTERN
        private static final int WIDTH_INDEX = 1;
        private static final int HEIGHT_INDEX = 2;

        private final int width;
        private final int height;

        private Resolution(int width, int height) {
            this.width = width;
            this.height = height;
        }

        private Resolution(String resolution) {
            Pattern pattern = Pattern.compile(PATTERN);
            Matcher matcher = pattern.matcher(resolution);
            matcher.find();
            this.width = Integer.parseInt(matcher.group(WIDTH_INDEX));
            this.height = Integer.parseInt(matcher.group(HEIGHT_INDEX));
        }

        @Override
        public String toString() {
            return String.format("%dx%d", width, height);
        }
    }

    @Test
    public void testGetResolutions() throws Exception {
        Resolution maxRes = new Resolution(DEFAULT_MAX_WIDTH, DEFAULT_MAX_HEIGHT);
        DynamicConfigDeviceSide config = new DynamicConfigDeviceSide(MODULE_NAME);
        for (String key : config.keySet()) {
            int width = 0;
            int height = 0;
            for (MediaFormat format : stringsToFormats(config.getValues(key))) {
                try {
                    width = Math.max(width, format.getInteger(MediaFormat.KEY_WIDTH));
                    height = Math.max(height, format.getInteger(MediaFormat.KEY_HEIGHT));
                } catch (NullPointerException | ClassCastException e) {
                    // audio format, or invalid format created by unrelated dynamic config entry
                    // simply continue in this case
                }
            }
            Resolution fileResolution = new Resolution(width, height);
            // if the file is of greater resolution than maxRes, check for support
            if (fileResolution.width > maxRes.width) {
                boolean supported = true;
                for (MediaFormat format : stringsToFormats(config.getValues(key))) {
                    supported &= MediaUtils.checkDecoderForFormat(format);
                }
                if (supported) {
                    // update if all MediaFormats for file are supported by device
                    maxRes = fileResolution;
                }
            }
        }
        // write resolution string to metrics
        Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        Bundle maxResBundle = new Bundle();
        maxResBundle.putString("resolution", maxRes.toString());
        inst.sendStatus(INST_STATUS_IN_PROGRESS, maxResBundle);
    }

    /**
     * Converts string representations of MediaFormats into actual MediaFormats
     * @param formatStrings a list of string representations of MediaFormats. Each input string
     * may represent one or more MediaFormats
     * @return a list of MediaFormats
     */
    private List<MediaFormat> stringsToFormats(List<String> formatStrings) {
        List<MediaFormat> formats = new ArrayList<MediaFormat>();
        for (String formatString : formatStrings) {
            for (String trackFormatString : formatString.split(";")) {
                formats.add(parseTrackFormat(trackFormatString));
            }
        }
        return formats;
    }

    /**
     * Converts a single media track format string into a MediaFormat object
     * @param trackFormatString a string representation of the format of one media track
     * @return a MediaFormat
     */
    private static MediaFormat parseTrackFormat(String trackFormatString) {
        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, "");
        for (String entry : trackFormatString.split(",")) {
            String[] kv = entry.split("=");
            if (kv.length < 2) {
                continue;
            }
            String k = kv[0];
            String v = kv[1];
            try {
                format.setInteger(k, Integer.parseInt(v));
            } catch(NumberFormatException e) {
                format.setString(k, v);
            }
        }
        return format;
    }
}
