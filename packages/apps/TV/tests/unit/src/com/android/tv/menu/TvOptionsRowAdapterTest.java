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
package com.android.tv.menu;

import static android.support.test.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.media.tv.TvTrackInfo;
import android.os.SystemClock;
import android.support.test.filters.MediumTest;
import android.text.TextUtils;

import com.android.tv.BaseMainActivityTestCase;
import com.android.tv.testing.Constants;
import com.android.tv.testing.testinput.ChannelState;
import com.android.tv.testing.testinput.ChannelStateData;
import com.android.tv.testing.testinput.TvTestInputConstants;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

/**
 * Tests for {@link TvOptionsRowAdapter}.
 */
@MediumTest
public class TvOptionsRowAdapterTest extends BaseMainActivityTestCase {
    private static final int WAIT_TRACK_EVENT_TIMEOUT_MS = 300;
    public static final int TRACK_CHECK_INTERVAL_MS = 10;

    // TODO: Refactor TvOptionsRowAdapter so it does not rely on MainActivity
    private TvOptionsRowAdapter mTvOptionsRowAdapter;

    @Override
    @Before
    public void setUp() {
        super.setUp();
        mTvOptionsRowAdapter = new TvOptionsRowAdapter(mActivity, Collections.emptyList());
        tuneToChannel(TvTestInputConstants.CH_1_DEFAULT_DONT_MODIFY);
        waitUntilAudioTracksHaveSize(1);
        waitUntilAudioTrackSelected(ChannelState.DEFAULT.getSelectedAudioTrackId());
        // update should be called on the main thread to avoid the multi-thread problem.
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mTvOptionsRowAdapter.update();
            }
        });
    }

    @Test
    public void testUpdateAudioAction_2tracks() {
        ChannelStateData data = new ChannelStateData();
        data.mTvTrackInfos.add(Constants.GENERIC_AUDIO_TRACK);
        updateThenTune(data, TvTestInputConstants.CH_2);
        waitUntilAudioTracksHaveSize(2);
        waitUntilAudioTrackSelected(Constants.EN_STEREO_AUDIO_TRACK.getId());

        boolean result = mTvOptionsRowAdapter.updateMultiAudioAction();
        assertEquals("update Action had change", true, result);
        assertEquals("Multi Audio enabled", true,
                MenuAction.SELECT_AUDIO_LANGUAGE_ACTION.isEnabled());
    }

    @Test
    public void testUpdateAudioAction_1track() {
        ChannelStateData data = new ChannelStateData();
        data.mTvTrackInfos.clear();
        data.mTvTrackInfos.add(Constants.GENERIC_AUDIO_TRACK);
        data.mSelectedVideoTrackId = null;
        data.mSelectedAudioTrackId = Constants.GENERIC_AUDIO_TRACK.getId();
        updateThenTune(data, TvTestInputConstants.CH_2);
        waitUntilAudioTracksHaveSize(1);
        waitUntilAudioTrackSelected(Constants.GENERIC_AUDIO_TRACK.getId());

        boolean result = mTvOptionsRowAdapter.updateMultiAudioAction();
        assertEquals("update Action had change", true, result);
        assertEquals("Multi Audio enabled", false,
                MenuAction.SELECT_AUDIO_LANGUAGE_ACTION.isEnabled());
    }

    @Test
    public void testUpdateAudioAction_noTracks() {
        ChannelStateData data = new ChannelStateData();
        data.mTvTrackInfos.clear();
        data.mTvTrackInfos.add(ChannelState.DEFAULT_VIDEO_TRACK);
        data.mSelectedVideoTrackId = ChannelState.DEFAULT_VIDEO_TRACK.getId();
        data.mSelectedAudioTrackId = null;
        updateThenTune(data, TvTestInputConstants.CH_2);
        // Wait for the video tracks, because there's no audio track.
        waitUntilVideoTracksHaveSize(1);
        waitUntilVideoTrackSelected(data.mSelectedVideoTrackId);

        boolean result = mTvOptionsRowAdapter.updateMultiAudioAction();
        assertEquals("update Action had change", true, result);
        assertEquals("Multi Audio enabled", false,
                MenuAction.SELECT_AUDIO_LANGUAGE_ACTION.isEnabled());
    }

    private void waitUntilAudioTracksHaveSize(int expected) {
        waitUntilTracksHaveSize(TvTrackInfo.TYPE_AUDIO, expected);
    }

    private void waitUntilVideoTracksHaveSize(int expected) {
        waitUntilTracksHaveSize(TvTrackInfo.TYPE_VIDEO, expected);
    }

    private void waitUntilTracksHaveSize(int trackType, int expected) {
        long start = SystemClock.elapsedRealtime();
        int size = -1;
        while (SystemClock.elapsedRealtime() < start + WAIT_TRACK_EVENT_TIMEOUT_MS) {
            getInstrumentation().waitForIdleSync();
            List<TvTrackInfo> tracks = mActivity.getTracks(trackType);
            if (tracks != null) {
                size = tracks.size();
                if (size == expected) {
                    return;
                }
            }
            SystemClock.sleep(TRACK_CHECK_INTERVAL_MS);
        }
        fail("Waited for " + WAIT_TRACK_EVENT_TIMEOUT_MS + " milliseconds for track size to be "
                + expected + " but was " + size);
    }

    private void waitUntilAudioTrackSelected(String trackId) {
        waitUntilTrackSelected(TvTrackInfo.TYPE_AUDIO, trackId);
    }

    private void waitUntilVideoTrackSelected(String trackId) {
        waitUntilTrackSelected(TvTrackInfo.TYPE_VIDEO, trackId);
    }

    private void waitUntilTrackSelected(int trackType, String trackId) {
        long start = SystemClock.elapsedRealtime();
        String selectedTrackId = null;
        while (SystemClock.elapsedRealtime() < start + WAIT_TRACK_EVENT_TIMEOUT_MS) {
            getInstrumentation().waitForIdleSync();
            selectedTrackId = mActivity.getSelectedTrack(trackType);
            if (TextUtils.equals(selectedTrackId, trackId)) {
                return;
            }
            SystemClock.sleep(TRACK_CHECK_INTERVAL_MS);
        }
        fail("Waited for " + WAIT_TRACK_EVENT_TIMEOUT_MS + " milliseconds for track ID to be "
                + trackId + " but was " + selectedTrackId);
    }
}
