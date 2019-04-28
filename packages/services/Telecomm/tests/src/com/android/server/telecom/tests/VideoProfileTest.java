/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.telecom.tests;

import android.telecom.VideoProfile;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

/**
 * Unit tests for the {@link android.telecom.VideoProfile} class.
 */
public class VideoProfileTest extends AndroidTestCase {
    @SmallTest
    public void testToString() {
        assertEquals("Audio Only", VideoProfile.videoStateToString(VideoProfile.STATE_AUDIO_ONLY));
        assertEquals("Audio Tx", VideoProfile.videoStateToString(VideoProfile.STATE_TX_ENABLED));
        assertEquals("Audio Rx", VideoProfile.videoStateToString(VideoProfile.STATE_RX_ENABLED));
        assertEquals("Audio Tx Rx", VideoProfile.videoStateToString(
                VideoProfile.STATE_BIDIRECTIONAL));

        assertEquals("Audio Pause", VideoProfile.videoStateToString(VideoProfile.STATE_PAUSED));
        assertEquals("Audio Tx Pause", VideoProfile.videoStateToString(
                VideoProfile.STATE_TX_ENABLED | VideoProfile.STATE_PAUSED));
        assertEquals("Audio Rx Pause", VideoProfile.videoStateToString(
                VideoProfile.STATE_RX_ENABLED | VideoProfile.STATE_PAUSED));
        assertEquals("Audio Tx Rx Pause", VideoProfile.videoStateToString(
                VideoProfile.STATE_BIDIRECTIONAL | VideoProfile.STATE_PAUSED));
    }

    @SmallTest
    public void testIsAudioOnly() {
        assertFalse(VideoProfile.isAudioOnly(VideoProfile.STATE_RX_ENABLED));
        assertFalse(VideoProfile.isAudioOnly(VideoProfile.STATE_TX_ENABLED));
        assertFalse(VideoProfile.isAudioOnly(VideoProfile.STATE_BIDIRECTIONAL));

        assertTrue(VideoProfile.isAudioOnly(VideoProfile.STATE_PAUSED));
        assertTrue(VideoProfile.isAudioOnly(VideoProfile.STATE_AUDIO_ONLY));
    }
}
