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

package com.android.tv.tuner;

import android.util.Log;

import com.android.tv.tuner.data.nano.Channel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses plain text formatted scan files, which contain the list of channels.
 */
public class ChannelScanFileParser {
    private static final String TAG = "ChannelScanFileParser";

    public static final class ScanChannel {
        public final int type;
        public final int frequency;
        public final String modulation;
        public final String filename;
        /**
         * Radio frequency (channel) number specified at
         * https://en.wikipedia.org/wiki/North_American_television_frequencies
         * This can be {@code null} for cases like cable signal.
         */
        public final Integer radioFrequencyNumber;

        public static ScanChannel forTuner(int frequency, String modulation,
                Integer radioFrequencyNumber) {
            return new ScanChannel(Channel.TYPE_TUNER, frequency, modulation, null,
                    radioFrequencyNumber);
        }

        public static ScanChannel forFile(int frequency, String filename) {
            return new ScanChannel(Channel.TYPE_FILE, frequency, "file:", filename, null);
        }

        private ScanChannel(int type, int frequency, String modulation, String filename,
                Integer radioFrequencyNumber) {
            this.type = type;
            this.frequency = frequency;
            this.modulation = modulation;
            this.filename = filename;
            this.radioFrequencyNumber = radioFrequencyNumber;
        }
    }

    /**
     * Parses a given scan file and returns the list of {@link ScanChannel} objects.
     *
     * @param is {@link InputStream} of a scan file. Each line matches one channel.
     *           The line format of the scan file is as follows:<br>
     *           "A &lt;frequency&gt; &lt;modulation&gt;".
     * @return a list of {@link ScanChannel} objects parsed
     */
    public static List<ScanChannel> parseScanFile(InputStream is) {
        BufferedReader in = new BufferedReader(new InputStreamReader(is));
        String line;
        List<ScanChannel> scanChannelList = new ArrayList<>();
        try {
            while ((line = in.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                if (line.charAt(0) == '#') {
                    // Skip comment line
                    continue;
                }
                String[] tokens = line.split("\\s+");
                if (tokens.length != 3 && tokens.length != 4) {
                    continue;
                }
                scanChannelList.add(ScanChannel.forTuner(Integer.parseInt(tokens[1]), tokens[2],
                        tokens.length == 4 ? Integer.parseInt(tokens[3]) : null));
            }
        } catch (IOException e) {
            Log.e(TAG, "error on parseScanFile()", e);
        }
        return scanChannelList;
    }
}
