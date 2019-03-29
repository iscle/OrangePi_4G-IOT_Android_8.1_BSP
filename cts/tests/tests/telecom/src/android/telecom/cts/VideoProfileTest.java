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
 * limitations under the License
 */

package android.telecom.cts;

import android.telecom.VideoProfile;
import android.test.AndroidTestCase;

/**
 * Tests helper methods in the {@link VideoProfile} class.
 */
public class VideoProfileTest extends AndroidTestCase {
    public void testIsAudioOnly() {
        assertTrue(VideoProfile.isAudioOnly(VideoProfile.STATE_AUDIO_ONLY));
        assertTrue(VideoProfile.isAudioOnly(VideoProfile.STATE_PAUSED));

        assertFalse(VideoProfile.isAudioOnly(VideoProfile.STATE_BIDIRECTIONAL));
        assertFalse(VideoProfile.isAudioOnly(VideoProfile.STATE_TX_ENABLED));
        assertFalse(VideoProfile.isAudioOnly(VideoProfile.STATE_RX_ENABLED));
        assertFalse(VideoProfile
                .isAudioOnly(VideoProfile.STATE_BIDIRECTIONAL | VideoProfile.STATE_PAUSED));
        assertFalse(VideoProfile
                .isAudioOnly(VideoProfile.STATE_TX_ENABLED | VideoProfile.STATE_PAUSED));
        assertFalse(VideoProfile
                .isAudioOnly(VideoProfile.STATE_RX_ENABLED | VideoProfile.STATE_PAUSED));
    }

    public void testIsVideo() {
        assertTrue(VideoProfile.isVideo(VideoProfile.STATE_BIDIRECTIONAL));
        assertTrue(VideoProfile.isVideo(VideoProfile.STATE_RX_ENABLED));
        assertTrue(VideoProfile.isVideo(VideoProfile.STATE_TX_ENABLED));
        assertTrue(VideoProfile.isVideo(VideoProfile.STATE_BIDIRECTIONAL |
                VideoProfile.STATE_PAUSED));
        assertTrue(VideoProfile.isVideo(VideoProfile.STATE_RX_ENABLED | VideoProfile.STATE_PAUSED));
        assertTrue(VideoProfile.isVideo(VideoProfile.STATE_TX_ENABLED | VideoProfile.STATE_PAUSED));

        assertFalse(VideoProfile.isVideo(VideoProfile.STATE_AUDIO_ONLY));
        assertFalse(VideoProfile.isVideo(VideoProfile.STATE_PAUSED));
    }

    public void testIsBidirectional() {
        assertTrue(VideoProfile.isBidirectional(VideoProfile.STATE_BIDIRECTIONAL));
        assertTrue(VideoProfile.isBidirectional(VideoProfile.STATE_BIDIRECTIONAL |
                VideoProfile.STATE_PAUSED));

        assertFalse(VideoProfile.isBidirectional(VideoProfile.STATE_TX_ENABLED));
        assertFalse(VideoProfile.isBidirectional(VideoProfile.STATE_TX_ENABLED |
                VideoProfile.STATE_PAUSED));
        assertFalse(VideoProfile.isBidirectional(VideoProfile.STATE_RX_ENABLED));
        assertFalse(VideoProfile.isBidirectional(VideoProfile.STATE_RX_ENABLED |
                VideoProfile.STATE_PAUSED));
    }

    public void testIsPaused() {
        assertTrue(VideoProfile.isPaused(VideoProfile.STATE_PAUSED));
        assertTrue(VideoProfile.isPaused(VideoProfile.STATE_BIDIRECTIONAL |
                VideoProfile.STATE_PAUSED));
        assertTrue(VideoProfile.isPaused(VideoProfile.STATE_TX_ENABLED |
                VideoProfile.STATE_PAUSED));
        assertTrue(VideoProfile.isPaused(VideoProfile.STATE_RX_ENABLED |
                VideoProfile.STATE_PAUSED));

        assertFalse(VideoProfile.isPaused(VideoProfile.STATE_AUDIO_ONLY));
        assertFalse(VideoProfile.isPaused(VideoProfile.STATE_TX_ENABLED));
        assertFalse(VideoProfile.isPaused(VideoProfile.STATE_RX_ENABLED));
        assertFalse(VideoProfile.isPaused(VideoProfile.STATE_BIDIRECTIONAL));
    }

    public void testIsReceptionEnabled() {
        assertTrue(VideoProfile.isReceptionEnabled(VideoProfile.STATE_RX_ENABLED));
        assertTrue(VideoProfile.isReceptionEnabled(VideoProfile.STATE_BIDIRECTIONAL));
        assertTrue(VideoProfile.isReceptionEnabled(VideoProfile.STATE_RX_ENABLED |
                VideoProfile.STATE_PAUSED));
        assertTrue(VideoProfile.isReceptionEnabled(VideoProfile.STATE_BIDIRECTIONAL |
                VideoProfile.STATE_PAUSED));

        assertFalse(VideoProfile.isReceptionEnabled(VideoProfile.STATE_AUDIO_ONLY));
        assertFalse(VideoProfile.isReceptionEnabled(VideoProfile.STATE_TX_ENABLED));
        assertFalse(VideoProfile.isReceptionEnabled(VideoProfile.STATE_PAUSED));
    }

    public void testIsTransmissionEnabled() {
        assertTrue(VideoProfile.isTransmissionEnabled(VideoProfile.STATE_TX_ENABLED));
        assertTrue(VideoProfile.isTransmissionEnabled(VideoProfile.STATE_BIDIRECTIONAL));
        assertTrue(VideoProfile.isTransmissionEnabled(VideoProfile.STATE_TX_ENABLED |
                VideoProfile.STATE_PAUSED));
        assertTrue(VideoProfile.isTransmissionEnabled(VideoProfile.STATE_BIDIRECTIONAL |
                VideoProfile.STATE_PAUSED));

        assertFalse(VideoProfile.isTransmissionEnabled(VideoProfile.STATE_AUDIO_ONLY));
        assertFalse(VideoProfile.isTransmissionEnabled(VideoProfile.STATE_RX_ENABLED));
        assertFalse(VideoProfile.isTransmissionEnabled(VideoProfile.STATE_PAUSED));
    }
}
