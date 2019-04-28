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

package com.android.tv.util;

import static android.support.test.InstrumentationRegistry.getTargetContext;
import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.media.tv.TvTrackInfo;
import android.support.test.filters.SmallTest;

import org.junit.Test;

/**
 * Tests for {@link com.android.tv.util.Utils#getMultiAudioString}.
 */
@SmallTest
public class UtilsTest_GetMultiAudioString {
    private static final String TRACK_ID = "test_track_id";
    private static final int AUDIO_SAMPLE_RATE = 48000;

    @Test
    public void testAudioTrackLanguage() {
        Context context = getTargetContext();
        assertEquals("Korean",
                Utils.getMultiAudioString(context, createAudioTrackInfo("kor"), false));
        assertEquals("English",
                Utils.getMultiAudioString(context, createAudioTrackInfo("eng"), false));
        assertEquals("Unknown language",
                Utils.getMultiAudioString(context, createAudioTrackInfo(null), false));
        assertEquals("Unknown language",
                Utils.getMultiAudioString(context, createAudioTrackInfo(""), false));
        assertEquals("abc", Utils.getMultiAudioString(context, createAudioTrackInfo("abc"), false));
    }

    @Test
    public void testAudioTrackCount() {
        Context context = getTargetContext();
        assertEquals("English",
                Utils.getMultiAudioString(context, createAudioTrackInfo("eng", -1), false));
        assertEquals("English",
                Utils.getMultiAudioString(context, createAudioTrackInfo("eng", 0), false));
        assertEquals("English (mono)",
                Utils.getMultiAudioString(context, createAudioTrackInfo("eng", 1), false));
        assertEquals("English (stereo)",
                Utils.getMultiAudioString(context, createAudioTrackInfo("eng", 2), false));
        assertEquals("English (3 channels)",
                Utils.getMultiAudioString(context, createAudioTrackInfo("eng", 3), false));
        assertEquals("English (4 channels)",
                Utils.getMultiAudioString(context, createAudioTrackInfo("eng", 4), false));
        assertEquals("English (5 channels)",
                Utils.getMultiAudioString(context, createAudioTrackInfo("eng", 5), false));
        assertEquals("English (5.1 surround)",
                Utils.getMultiAudioString(context, createAudioTrackInfo("eng", 6), false));
        assertEquals("English (7 channels)",
                Utils.getMultiAudioString(context, createAudioTrackInfo("eng", 7), false));
        assertEquals("English (7.1 surround)",
                Utils.getMultiAudioString(context, createAudioTrackInfo("eng", 8), false));
    }

    @Test
    public void testShowSampleRate() {
        assertEquals("Korean (48kHz)",
                Utils.getMultiAudioString(getTargetContext(),
                createAudioTrackInfo("kor", 0), true));
        assertEquals("Korean (7.1 surround, 48kHz)",
                Utils.getMultiAudioString(getTargetContext(),
                createAudioTrackInfo("kor", 8), true));
    }

    private static TvTrackInfo createAudioTrackInfo(String language) {
        return createAudioTrackInfo(language, 0);
    }

    private static TvTrackInfo createAudioTrackInfo(String language, int channelCount) {
        return new TvTrackInfo.Builder(TvTrackInfo.TYPE_AUDIO, TRACK_ID)
                .setLanguage(language).setAudioChannelCount(channelCount)
                .setAudioSampleRate(AUDIO_SAMPLE_RATE).build();
    }
}
