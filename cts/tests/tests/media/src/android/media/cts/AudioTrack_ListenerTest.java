/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.media.cts;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.AudioTrack.OnPlaybackPositionUpdateListener;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.android.compatibility.common.util.CtsAndroidTestCase;
import com.android.compatibility.common.util.DeviceReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;

import java.util.ArrayList;

public class AudioTrack_ListenerTest extends CtsAndroidTestCase {
    private final static String TAG = "AudioTrack_ListenerTest";
    private static final String REPORT_LOG_NAME = "CtsMediaTestCases";
    private final static int TEST_SR = 11025;
    private final static int TEST_CONF = AudioFormat.CHANNEL_OUT_MONO;
    private final static int TEST_FORMAT = AudioFormat.ENCODING_PCM_8BIT;
    private final static int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;
    private final static int TEST_LOOP_FACTOR = 2; // # loops (>= 1) for static tracks
                                                   // simulated for streaming.
    private final static int TEST_BUFFER_FACTOR = 25;
    private boolean mIsHandleMessageCalled;
    private int mMarkerPeriodInFrames;
    private int mMarkerPosition;
    private int mFrameCount;
    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            mIsHandleMessageCalled = true;
            super.handleMessage(msg);
        }
    };

    public void testAudioTrackCallback() throws Exception {
        doTest("streaming_local_looper", true /*localTrack*/, false /*customHandler*/,
                30 /*periodsPerSecond*/, 2 /*markerPeriodsPerSecond*/, AudioTrack.MODE_STREAM);
    }

    public void testAudioTrackCallbackWithHandler() throws Exception {
        // with 100 periods per second, trigger back-to-back notifications.
        doTest("streaming_private_handler", false /*localTrack*/, true /*customHandler*/,
                100 /*periodsPerSecond*/, 10 /*markerPeriodsPerSecond*/, AudioTrack.MODE_STREAM);
        // verify mHandler is used only for accessing its associated Looper
        assertFalse(mIsHandleMessageCalled);
    }

    public void testStaticAudioTrackCallback() throws Exception {
        doTest("static", false /*localTrack*/, false /*customHandler*/,
                100 /*periodsPerSecond*/, 10 /*markerPeriodsPerSecond*/, AudioTrack.MODE_STATIC);
    }

    public void testStaticAudioTrackCallbackWithHandler() throws Exception {
        String streamName = "test_static_audio_track_callback_handler";
        doTest("static_private_handler", false /*localTrack*/, true /*customHandler*/,
                30 /*periodsPerSecond*/, 2 /*markerPeriodsPerSecond*/, AudioTrack.MODE_STATIC);
        // verify mHandler is used only for accessing its associated Looper
        assertFalse(mIsHandleMessageCalled);
    }

    private void doTest(String reportName, boolean localTrack, boolean customHandler,
            int periodsPerSecond, int markerPeriodsPerSecond, final int mode) throws Exception {
        mIsHandleMessageCalled = false;
        final int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        final int bufferSizeInBytes;
        if (mode == AudioTrack.MODE_STATIC && TEST_LOOP_FACTOR > 1) {
            // use setLoopPoints for static mode
            bufferSizeInBytes = minBuffSize * TEST_BUFFER_FACTOR;
            mFrameCount = bufferSizeInBytes * TEST_LOOP_FACTOR;
        } else {
            bufferSizeInBytes = minBuffSize * TEST_BUFFER_FACTOR * TEST_LOOP_FACTOR;
            mFrameCount = bufferSizeInBytes;
        }

        final AudioTrack track;
        final AudioHelper.MakeSomethingAsynchronouslyAndLoop<AudioTrack> makeSomething;
        if (localTrack) {
            makeSomething = null;
            track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF,
                    TEST_FORMAT, bufferSizeInBytes, mode);
        } else {
            makeSomething =
                    new AudioHelper.MakeSomethingAsynchronouslyAndLoop<AudioTrack>(
                    new AudioHelper.MakesSomething<AudioTrack>() {
                        @Override
                        public AudioTrack makeSomething() {
                            return new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF,
                                TEST_FORMAT, bufferSizeInBytes, mode);
                        }
                    }
                );
           // create audiotrack on different thread's looper.
           track = makeSomething.make();
        }
        final MockOnPlaybackPositionUpdateListener listener;
        if (customHandler) {
            listener = new MockOnPlaybackPositionUpdateListener(track, mHandler);
        } else {
            listener = new MockOnPlaybackPositionUpdateListener(track);
        }

        byte[] vai = AudioHelper.createSoundDataInByteArray(
                bufferSizeInBytes, TEST_SR, 1024 /* frequency */, 0 /* sweep */);
        int markerPeriods = Math.max(3, mFrameCount * markerPeriodsPerSecond / TEST_SR);
        mMarkerPeriodInFrames = mFrameCount / markerPeriods;
        markerPeriods = mFrameCount / mMarkerPeriodInFrames; // recalculate due to round-down
        mMarkerPosition = mMarkerPeriodInFrames;

        // check that we can get and set notification marker position
        assertEquals(0, track.getNotificationMarkerPosition());
        assertEquals(AudioTrack.SUCCESS,
                track.setNotificationMarkerPosition(mMarkerPosition));
        assertEquals(mMarkerPosition, track.getNotificationMarkerPosition());

        int updatePeriods = Math.max(3, mFrameCount * periodsPerSecond / TEST_SR);
        final int updatePeriodInFrames = mFrameCount / updatePeriods;
        updatePeriods = mFrameCount / updatePeriodInFrames; // recalculate due to round-down

        // we set the notification period before running for better period positional accuracy.
        // check that we can get and set notification periods
        assertEquals(0, track.getPositionNotificationPeriod());
        assertEquals(AudioTrack.SUCCESS,
                track.setPositionNotificationPeriod(updatePeriodInFrames));
        assertEquals(updatePeriodInFrames, track.getPositionNotificationPeriod());

        if (mode == AudioTrack.MODE_STATIC && TEST_LOOP_FACTOR > 1) {
            track.setLoopPoints(0, vai.length, TEST_LOOP_FACTOR - 1);
        }
        // write data with single blocking write, then play.
        assertEquals(vai.length, track.write(vai, 0 /* offsetInBytes */, vai.length));
        track.play();

        // sleep until track completes playback - it must complete within 1 second
        // of the expected length otherwise the periodic test should fail.
        final int numChannels =  AudioFormat.channelCountFromOutChannelMask(TEST_CONF);
        final int bytesPerSample = AudioFormat.getBytesPerSample(TEST_FORMAT);
        final int bytesPerFrame = numChannels * bytesPerSample;
        final int trackLengthMs = (int)((double)mFrameCount * 1000 / TEST_SR / bytesPerFrame);
        Thread.sleep(trackLengthMs + 1000);

        // stop listening - we should be done.
        listener.stop();

        // Beware: stop() resets the playback head position for both static and streaming
        // audio tracks, so stop() cannot be called while we're still logging playback
        // head positions. We could recycle the track after stop(), which isn't done here.
        track.stop();

        // clean up
        if (makeSomething != null) {
            makeSomething.join();
        }
        listener.release();
        track.release();

        // collect statistics
        final ArrayList<Integer> markerList = listener.getMarkerList();
        final ArrayList<Integer> periodicList = listener.getPeriodicList();
        // verify count of markers and periodic notifications.
        assertEquals(markerPeriods, markerList.size());
        assertEquals(updatePeriods, periodicList.size());
        // verify actual playback head positions returned.
        // the max diff should really be around 24 ms,
        // but system load and stability will affect this test;
        // we use 80ms limit here for failure.
        final int tolerance80MsInFrames = TEST_SR * 80 / 1000;

        AudioHelper.Statistics markerStat = new AudioHelper.Statistics();
        for (int i = 0; i < markerPeriods; ++i) {
            final int expected = mMarkerPeriodInFrames * (i + 1);
            final int actual = markerList.get(i);
            // Log.d(TAG, "Marker: expected(" + expected + ")  actual(" + actual
            //        + ")  diff(" + (actual - expected) + ")");
            assertEquals(expected, actual, tolerance80MsInFrames);
            markerStat.add((double)(actual - expected) * 1000 / TEST_SR);
        }

        AudioHelper.Statistics periodicStat = new AudioHelper.Statistics();
        for (int i = 0; i < updatePeriods; ++i) {
            final int expected = updatePeriodInFrames * (i + 1);
            final int actual = periodicList.get(i);
            // Log.d(TAG, "Update: expected(" + expected + ")  actual(" + actual
            //        + ")  diff(" + (actual - expected) + ")");
            assertEquals(expected, actual, tolerance80MsInFrames);
            periodicStat.add((double)(actual - expected) * 1000 / TEST_SR);
        }

        // report this
        DeviceReportLog log = new DeviceReportLog(REPORT_LOG_NAME, reportName);
        log.addValue("average_marker_diff", markerStat.getAvg(), ResultType.LOWER_BETTER,
                ResultUnit.MS);
        log.addValue("maximum_marker_abs_diff", markerStat.getMaxAbs(), ResultType.LOWER_BETTER,
                ResultUnit.MS);
        log.addValue("average_marker_abs_diff", markerStat.getAvgAbs(), ResultType.LOWER_BETTER,
                ResultUnit.MS);
        log.addValue("average_periodic_diff", periodicStat.getAvg(), ResultType.LOWER_BETTER,
                ResultUnit.MS);
        log.addValue("maximum_periodic_abs_diff", periodicStat.getMaxAbs(), ResultType.LOWER_BETTER,
                ResultUnit.MS);
        log.addValue("average_periodic_abs_diff", periodicStat.getAvgAbs(), ResultType.LOWER_BETTER,
                ResultUnit.MS);
        log.setSummary("unified_abs_diff", (periodicStat.getAvgAbs() + markerStat.getAvgAbs()) / 2,
                ResultType.LOWER_BETTER, ResultUnit.MS);
        log.submit(getInstrumentation());
    }

    private class MockOnPlaybackPositionUpdateListener
                                        implements OnPlaybackPositionUpdateListener {
        public MockOnPlaybackPositionUpdateListener(AudioTrack track) {
            mAudioTrack = track;
            track.setPlaybackPositionUpdateListener(this);
        }

        public MockOnPlaybackPositionUpdateListener(AudioTrack track, Handler handler) {
            mAudioTrack = track;
            track.setPlaybackPositionUpdateListener(this, handler);
        }

        public synchronized void onMarkerReached(AudioTrack track) {
            if (mIsTestActive) {
                int position = mAudioTrack.getPlaybackHeadPosition();
                mOnMarkerReachedCalled.add(position);
                mMarkerPosition += mMarkerPeriodInFrames;
                if (mMarkerPosition <= mFrameCount) {
                    assertEquals(AudioTrack.SUCCESS,
                            mAudioTrack.setNotificationMarkerPosition(mMarkerPosition));
                }
            } else {
                fail("onMarkerReached called when not active");
            }
        }

        public synchronized void onPeriodicNotification(AudioTrack track) {
            if (mIsTestActive) {
                mOnPeriodicNotificationCalled.add(mAudioTrack.getPlaybackHeadPosition());
            } else {
                fail("onPeriodicNotification called when not active");
            }
        }

        public synchronized void stop() {
            mIsTestActive = false;
        }

        public ArrayList<Integer> getMarkerList() {
            return mOnMarkerReachedCalled;
        }

        public ArrayList<Integer> getPeriodicList() {
            return mOnPeriodicNotificationCalled;
        }

        public synchronized void release() {
            mAudioTrack.setPlaybackPositionUpdateListener(null);
            mAudioTrack = null;
        }

        private boolean mIsTestActive = true;
        private AudioTrack mAudioTrack;
        private ArrayList<Integer> mOnMarkerReachedCalled = new ArrayList<Integer>();
        private ArrayList<Integer> mOnPeriodicNotificationCalled = new ArrayList<Integer>();
    }
}
