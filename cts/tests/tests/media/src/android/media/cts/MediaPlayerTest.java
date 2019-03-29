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

import android.media.cts.R;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Rect;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.MediaCodec;
import android.media.MediaDataSource;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnSeekCompleteListener;
import android.media.MediaPlayer.OnTimedTextListener;
import android.media.MediaRecorder;
import android.media.MediaTimestamp;
import android.media.PlaybackParams;
import android.media.SubtitleData;
import android.media.SyncParams;
import android.media.TimedText;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.Visualizer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.platform.test.annotations.RequiresDevice;
import android.util.Log;

import com.android.compatibility.common.util.MediaUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.StringTokenizer;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import junit.framework.AssertionFailedError;

/**
 * Tests for the MediaPlayer API and local video/audio playback.
 *
 * The files in res/raw used by testLocalVideo* are (c) copyright 2008,
 * Blender Foundation / www.bigbuckbunny.org, and are licensed under the Creative Commons
 * Attribution 3.0 License at http://creativecommons.org/licenses/by/3.0/us/.
 */
@SmallTest
@RequiresDevice
public class MediaPlayerTest extends MediaPlayerTestBase {

    private String RECORDED_FILE;
    private static final String LOG_TAG = "MediaPlayerTest";

    private static final int  RECORDED_VIDEO_WIDTH  = 176;
    private static final int  RECORDED_VIDEO_HEIGHT = 144;
    private static final long RECORDED_DURATION_MS  = 3000;
    private static final float FLOAT_TOLERANCE = .0001f;

    private final Vector<Integer> mTimedTextTrackIndex = new Vector<>();
    private final Monitor mOnTimedTextCalled = new Monitor();
    private int mSelectedTimedTextIndex;

    private final Vector<Integer> mSubtitleTrackIndex = new Vector<>();
    private final Monitor mOnSubtitleDataCalled = new Monitor();
    private int mSelectedSubtitleIndex;

    private File mOutFile;

    private int mBoundsCount;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        RECORDED_FILE = new File(Environment.getExternalStorageDirectory(),
                "mediaplayer_record.out").getAbsolutePath();
        mOutFile = new File(RECORDED_FILE);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (mOutFile != null && mOutFile.exists()) {
            mOutFile.delete();
        }
    }

    public void testFlacHeapOverflow() throws Exception {
        testIfMediaServerDied(R.raw.heap_oob_flac);
    }

    private void testIfMediaServerDied(int res) throws Exception {
        mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                assertTrue(mp == mMediaPlayer);
                assertTrue("mediaserver process died", what != MediaPlayer.MEDIA_ERROR_SERVER_DIED);
                Log.w(LOG_TAG, "onError " + what);
                return false;
            }
        });

        mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                assertTrue(mp == mMediaPlayer);
                mOnCompletionCalled.signal();
            }
        });

        AssetFileDescriptor afd = mResources.openRawResourceFd(res);
        mMediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
        afd.close();
        try {
            mMediaPlayer.prepare();
            mMediaPlayer.start();
            if (!mOnCompletionCalled.waitForSignal(5000)) {
                Log.w(LOG_TAG, "testIfMediaServerDied: Timed out waiting for Error/Completion");
            }
        } catch (Exception e) {
            Log.w(LOG_TAG, "playback failed", e);
        } finally {
            mMediaPlayer.release();
        }
    }

    // Bug 13652927
    public void testVorbisCrash() throws Exception {
        MediaPlayer mp = mMediaPlayer;
        MediaPlayer mp2 = mMediaPlayer2;
        AssetFileDescriptor afd2 = mResources.openRawResourceFd(R.raw.testmp3_2);
        mp2.setDataSource(afd2.getFileDescriptor(), afd2.getStartOffset(), afd2.getLength());
        afd2.close();
        mp2.prepare();
        mp2.setLooping(true);
        mp2.start();

        for (int i = 0; i < 20; i++) {
            try {
                AssetFileDescriptor afd = mResources.openRawResourceFd(R.raw.bug13652927);
                mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();
                mp.prepare();
                fail("shouldn't be here");
            } catch (Exception e) {
                // expected to fail
                Log.i("@@@", "failed: " + e);
            }
            Thread.sleep(500);
            assertTrue("media server died", mp2.isPlaying());
            mp.reset();
        }
    }

    public void testPlayNullSourcePath() throws Exception {
        try {
            mMediaPlayer.setDataSource((String) null);
            fail("Null path was accepted");
        } catch (RuntimeException e) {
            // expected
        }
    }

    public void testPlayAudioFromDataURI() throws Exception {
        final int mp3Duration = 34909;
        final int tolerance = 70;
        final int seekDuration = 100;

        // This is "R.raw.testmp3_2", base64-encoded.
        final int resid = R.raw.testmp3_3;

        InputStream is = mContext.getResources().openRawResource(resid);
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));

        StringBuilder builder = new StringBuilder();
        builder.append("data:;base64,");
        builder.append(reader.readLine());
        Uri uri = Uri.parse(builder.toString());

        MediaPlayer mp = MediaPlayer.create(mContext, uri);

        try {
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mp.setWakeMode(mContext, PowerManager.PARTIAL_WAKE_LOCK);

            assertFalse(mp.isPlaying());
            mp.start();
            assertTrue(mp.isPlaying());

            assertFalse(mp.isLooping());
            mp.setLooping(true);
            assertTrue(mp.isLooping());

            assertEquals(mp3Duration, mp.getDuration(), tolerance);
            int pos = mp.getCurrentPosition();
            assertTrue(pos >= 0);
            assertTrue(pos < mp3Duration - seekDuration);

            mp.seekTo(pos + seekDuration);
            assertEquals(pos + seekDuration, mp.getCurrentPosition(), tolerance);

            // test pause and restart
            mp.pause();
            Thread.sleep(SLEEP_TIME);
            assertFalse(mp.isPlaying());
            mp.start();
            assertTrue(mp.isPlaying());

            // test stop and restart
            mp.stop();
            mp.reset();
            mp.setDataSource(mContext, uri);
            mp.prepare();
            assertFalse(mp.isPlaying());
            mp.start();
            assertTrue(mp.isPlaying());

            // waiting to complete
            while(mp.isPlaying()) {
                Thread.sleep(SLEEP_TIME);
            }
        } finally {
            mp.release();
        }
    }

    public void testPlayAudio() throws Exception {
        final int resid = R.raw.testmp3_2;
        final int mp3Duration = 34909;
        final int tolerance = 70;
        final int seekDuration = 100;

        MediaPlayer mp = MediaPlayer.create(mContext, resid);
        try {
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mp.setWakeMode(mContext, PowerManager.PARTIAL_WAKE_LOCK);

            assertFalse(mp.isPlaying());
            mp.start();
            assertTrue(mp.isPlaying());

            assertFalse(mp.isLooping());
            mp.setLooping(true);
            assertTrue(mp.isLooping());

            assertEquals(mp3Duration, mp.getDuration(), tolerance);
            int pos = mp.getCurrentPosition();
            assertTrue(pos >= 0);
            assertTrue(pos < mp3Duration - seekDuration);

            mp.seekTo(pos + seekDuration);
            assertEquals(pos + seekDuration, mp.getCurrentPosition(), tolerance);

            // test pause and restart
            mp.pause();
            Thread.sleep(SLEEP_TIME);
            assertFalse(mp.isPlaying());
            mp.start();
            assertTrue(mp.isPlaying());

            // test stop and restart
            mp.stop();
            mp.reset();
            AssetFileDescriptor afd = mResources.openRawResourceFd(resid);
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mp.prepare();
            assertFalse(mp.isPlaying());
            mp.start();
            assertTrue(mp.isPlaying());

            // waiting to complete
            while(mp.isPlaying()) {
                Thread.sleep(SLEEP_TIME);
            }
        } finally {
            mp.release();
        }
    }

    public void testConcurentPlayAudio() throws Exception {
        final int resid = R.raw.test1m1s; // MP3 longer than 1m are usualy offloaded
        final int tolerance = 70;

        List<MediaPlayer> mps = Stream.generate(() -> MediaPlayer.create(mContext, resid))
                                      .limit(5).collect(Collectors.toList());

        try {
            for (MediaPlayer mp : mps) {
                mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mp.setWakeMode(mContext, PowerManager.PARTIAL_WAKE_LOCK);

                assertFalse(mp.isPlaying());
                mp.start();
                assertTrue(mp.isPlaying());

                assertFalse(mp.isLooping());
                mp.setLooping(true);
                assertTrue(mp.isLooping());

                int pos = mp.getCurrentPosition();
                assertTrue(pos >= 0);

                Thread.sleep(SLEEP_TIME); // Delay each track to be able to ear them
            }
            // Check that all mp3 are playing concurrently here
            for (MediaPlayer mp : mps) {
                int pos = mp.getCurrentPosition();
                Thread.sleep(SLEEP_TIME);
                assertEquals(pos + SLEEP_TIME, mp.getCurrentPosition(), tolerance);
            }
        } finally {
            mps.forEach(MediaPlayer::release);
        }
    }

    public void testPlayAudioLooping() throws Exception {
        final int resid = R.raw.testmp3;

        MediaPlayer mp = MediaPlayer.create(mContext, resid);
        try {
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mp.setWakeMode(mContext, PowerManager.PARTIAL_WAKE_LOCK);
            mp.setLooping(true);
            mOnCompletionCalled.reset();
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    Log.i("@@@", "got oncompletion");
                    mOnCompletionCalled.signal();
                }
            });

            assertFalse(mp.isPlaying());
            mp.start();
            assertTrue(mp.isPlaying());

            int duration = mp.getDuration();
            Thread.sleep(duration * 4); // allow for several loops
            assertTrue(mp.isPlaying());
            assertEquals("wrong number of completion signals", 0, mOnCompletionCalled.getNumSignal());
            mp.setLooping(false);

            // wait for playback to finish
            while(mp.isPlaying()) {
                Thread.sleep(SLEEP_TIME);
            }
            assertEquals("wrong number of completion signals", 1, mOnCompletionCalled.getNumSignal());
        } finally {
            mp.release();
        }
    }

    public void testPlayMidi() throws Exception {
        final int resid = R.raw.midi8sec;
        final int midiDuration = 8000;
        final int tolerance = 70;
        final int seekDuration = 1000;

        MediaPlayer mp = MediaPlayer.create(mContext, resid);
        try {
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mp.setWakeMode(mContext, PowerManager.PARTIAL_WAKE_LOCK);

            mp.start();

            assertFalse(mp.isLooping());
            mp.setLooping(true);
            assertTrue(mp.isLooping());

            assertEquals(midiDuration, mp.getDuration(), tolerance);
            int pos = mp.getCurrentPosition();
            assertTrue(pos >= 0);
            assertTrue(pos < midiDuration - seekDuration);

            mp.seekTo(pos + seekDuration);
            assertEquals(pos + seekDuration, mp.getCurrentPosition(), tolerance);

            // test stop and restart
            mp.stop();
            mp.reset();
            AssetFileDescriptor afd = mResources.openRawResourceFd(resid);
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mp.prepare();
            mp.start();

            Thread.sleep(SLEEP_TIME);
        } finally {
            mp.release();
        }
    }

    private final class VerifyAndSignalTimedText implements MediaPlayer.OnTimedTextListener {

        final boolean mCheckStartTimeIncrease;
        final int mTargetSignalCount;
        int mPrevStartMs = -1;

        VerifyAndSignalTimedText() {
            this(Integer.MAX_VALUE, false);
        }

        VerifyAndSignalTimedText(int targetSignalCount, boolean checkStartTimeIncrease) {
            mTargetSignalCount = targetSignalCount;
            mCheckStartTimeIncrease = checkStartTimeIncrease;
        }

        void reset() {
            mPrevStartMs = -1;
        }

        @Override
        public void onTimedText(MediaPlayer mp, TimedText text) {
            final int toleranceMs = 500;
            final int durationMs = 500;
            int posMs = mMediaPlayer.getCurrentPosition();
            if (text != null) {
                text.getText();
                String plainText = text.getText();
                if (plainText != null) {
                    StringTokenizer tokens = new StringTokenizer(plainText.trim(), ":");
                    int subtitleTrackIndex = Integer.parseInt(tokens.nextToken());
                    int startMs = Integer.parseInt(tokens.nextToken());
                    Log.d(LOG_TAG, "text: " + plainText.trim() +
                          ", trackId: " + subtitleTrackIndex + ", posMs: " + posMs);
                    assertTrue("The diff between subtitle's start time " + startMs +
                               " and current time " + posMs +
                               " is over tolerance " + toleranceMs,
                               (posMs >= startMs - toleranceMs) &&
                               (posMs < startMs + durationMs + toleranceMs) );
                    assertEquals("Expected track: " + mSelectedTimedTextIndex +
                                 ", actual track: " + subtitleTrackIndex,
                                 mSelectedTimedTextIndex, subtitleTrackIndex);
                    assertTrue("timed text start time did not increase; current: " + startMs +
                               ", previous: " + mPrevStartMs,
                               !mCheckStartTimeIncrease || startMs > mPrevStartMs);
                    mPrevStartMs = startMs;
                    mOnTimedTextCalled.signal();
                    if (mTargetSignalCount >= mOnTimedTextCalled.getNumSignal()) {
                        reset();
                    }
                }
                Rect bounds = text.getBounds();
                if (bounds != null) {
                    Log.d(LOG_TAG, "bounds: " + bounds);
                    mBoundsCount++;
                    Rect expected = new Rect(0, 0, 352, 288);
                    assertEquals("wrong bounds", expected, bounds);
                }
            }
        }

    }

    static class OutputListener {
        int mSession;
        AudioEffect mVc;
        Visualizer mVis;
        byte [] mVisData;
        boolean mSoundDetected;
        OutputListener(int session) {
            mSession = session;
            // creating a volume controller on output mix ensures that ro.audio.silent mutes
            // audio after the effects and not before
            mVc = new AudioEffect(
                    AudioEffect.EFFECT_TYPE_NULL,
                    UUID.fromString("119341a0-8469-11df-81f9-0002a5d5c51b"),
                    0,
                    session);
            mVc.setEnabled(true);
            mVis = new Visualizer(session);
            int size = 256;
            int[] range = Visualizer.getCaptureSizeRange();
            if (size < range[0]) {
                size = range[0];
            }
            if (size > range[1]) {
                size = range[1];
            }
            assertTrue(mVis.setCaptureSize(size) == Visualizer.SUCCESS);

            mVis.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(Visualizer visualizer,
                        byte[] waveform, int samplingRate) {
                    if (!mSoundDetected) {
                        for (int i = 0; i < waveform.length; i++) {
                            // 8 bit unsigned PCM, zero level is at 128, which is -128 when
                            // seen as a signed byte
                            if (waveform[i] != -128) {
                                mSoundDetected = true;
                                break;
                            }
                        }
                    }
                }

                @Override
                public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
                }
            }, 10000 /* milliHertz */, true /* PCM */, false /* FFT */);
            assertTrue(mVis.setEnabled(true) == Visualizer.SUCCESS);
        }

        void reset() {
            mSoundDetected = false;
        }

        boolean heardSound() {
            return mSoundDetected;
        }

        void release() {
            mVis.release();
            mVc.release();
        }
    }

    public void testPlayAudioTwice() throws Exception {

        final int resid = R.raw.camera_click;

        MediaPlayer mp = MediaPlayer.create(mContext, resid);
        try {
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mp.setWakeMode(mContext, PowerManager.PARTIAL_WAKE_LOCK);

            OutputListener listener = new OutputListener(mp.getAudioSessionId());

            Thread.sleep(SLEEP_TIME);
            assertFalse("noise heard before test started", listener.heardSound());

            mp.start();
            Thread.sleep(SLEEP_TIME);
            assertFalse("player was still playing after " + SLEEP_TIME + " ms", mp.isPlaying());
            assertTrue("nothing heard while test ran", listener.heardSound());
            listener.reset();
            mp.seekTo(0);
            mp.start();
            Thread.sleep(SLEEP_TIME);
            assertTrue("nothing heard when sound was replayed", listener.heardSound());
            listener.release();
        } finally {
            mp.release();
        }
    }

    public void testPlayVideo() throws Exception {
        playVideoTest(R.raw.testvideo, 352, 288);
    }

    private void initMediaPlayer(MediaPlayer player) throws Exception {
        AssetFileDescriptor afd = mResources.openRawResourceFd(R.raw.test1m1s);
        try {
            player.reset();
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            player.prepare();
            player.seekTo(56000);
        } finally {
            afd.close();
        }
    }

    public void testSetNextMediaPlayerWithReset() throws Exception {

        initMediaPlayer(mMediaPlayer);

        try {
            initMediaPlayer(mMediaPlayer2);
            mMediaPlayer2.reset();
            mMediaPlayer.setNextMediaPlayer(mMediaPlayer2);
            fail("setNextMediaPlayer() succeeded with unprepared player");
        } catch (RuntimeException e) {
            // expected
        } finally {
            mMediaPlayer.reset();
        }
    }

    public void testSetNextMediaPlayerWithRelease() throws Exception {

        initMediaPlayer(mMediaPlayer);

        try {
            initMediaPlayer(mMediaPlayer2);
            mMediaPlayer2.release();
            mMediaPlayer.setNextMediaPlayer(mMediaPlayer2);
            fail("setNextMediaPlayer() succeeded with unprepared player");
        } catch (RuntimeException e) {
            // expected
        } finally {
            mMediaPlayer.reset();
        }
    }

    public void testSetNextMediaPlayer() throws Exception {
        initMediaPlayer(mMediaPlayer);

        final Monitor mTestCompleted = new Monitor();

        Thread timer = new Thread(new Runnable() {

            @Override
            public void run() {
                long startTime = SystemClock.elapsedRealtime();
                while(true) {
                    SystemClock.sleep(SLEEP_TIME);
                    if (mTestCompleted.isSignalled()) {
                        // done
                        return;
                    }
                    long now = SystemClock.elapsedRealtime();
                    if ((now - startTime) > 25000) {
                        // We've been running for 25 seconds and still aren't done, so we're stuck
                        // somewhere. Signal ourselves to dump the thread stacks.
                        android.os.Process.sendSignal(android.os.Process.myPid(), 3);
                        SystemClock.sleep(2000);
                        fail("Test is stuck, see ANR stack trace for more info. You may need to" +
                                " create /data/anr first");
                        return;
                    }
                }
            }
        });

        timer.start();

        try {
            for (int i = 0; i < 3; i++) {

                initMediaPlayer(mMediaPlayer2);
                mOnCompletionCalled.reset();
                mOnInfoCalled.reset();
                mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        assertEquals(mMediaPlayer, mp);
                        mOnCompletionCalled.signal();
                    }
                });
                mMediaPlayer2.setOnInfoListener(new MediaPlayer.OnInfoListener() {
                    @Override
                    public boolean onInfo(MediaPlayer mp, int what, int extra) {
                        assertEquals(mMediaPlayer2, mp);
                        if (what == MediaPlayer.MEDIA_INFO_STARTED_AS_NEXT) {
                            mOnInfoCalled.signal();
                        }
                        return false;
                    }
                });

                mMediaPlayer.setNextMediaPlayer(mMediaPlayer2);
                mMediaPlayer.start();
                assertTrue(mMediaPlayer.isPlaying());
                assertFalse(mOnCompletionCalled.isSignalled());
                assertFalse(mMediaPlayer2.isPlaying());
                assertFalse(mOnInfoCalled.isSignalled());
                while(mMediaPlayer.isPlaying()) {
                    Thread.sleep(SLEEP_TIME);
                }
                // wait a little longer in case the callbacks haven't quite made it through yet
                Thread.sleep(100);
                assertTrue(mMediaPlayer2.isPlaying());
                assertTrue(mOnCompletionCalled.isSignalled());
                assertTrue(mOnInfoCalled.isSignalled());

                // At this point the 1st player is done, and the 2nd one is playing.
                // Now swap them, and go through the loop again.
                MediaPlayer tmp = mMediaPlayer;
                mMediaPlayer = mMediaPlayer2;
                mMediaPlayer2 = tmp;
            }

            // Now test that setNextMediaPlayer(null) works. 1 is still playing, 2 is done
            mOnCompletionCalled.reset();
            mOnInfoCalled.reset();
            initMediaPlayer(mMediaPlayer2);
            mMediaPlayer.setNextMediaPlayer(mMediaPlayer2);

            mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    assertEquals(mMediaPlayer, mp);
                    mOnCompletionCalled.signal();
                }
            });
            mMediaPlayer2.setOnInfoListener(new MediaPlayer.OnInfoListener() {
                @Override
                public boolean onInfo(MediaPlayer mp, int what, int extra) {
                    assertEquals(mMediaPlayer2, mp);
                    if (what == MediaPlayer.MEDIA_INFO_STARTED_AS_NEXT) {
                        mOnInfoCalled.signal();
                    }
                    return false;
                }
            });
            assertTrue(mMediaPlayer.isPlaying());
            assertFalse(mOnCompletionCalled.isSignalled());
            assertFalse(mMediaPlayer2.isPlaying());
            assertFalse(mOnInfoCalled.isSignalled());
            Thread.sleep(SLEEP_TIME);
            mMediaPlayer.setNextMediaPlayer(null);
            while(mMediaPlayer.isPlaying()) {
                Thread.sleep(SLEEP_TIME);
            }
            // wait a little longer in case the callbacks haven't quite made it through yet
            Thread.sleep(100);
            assertFalse(mMediaPlayer.isPlaying());
            assertFalse(mMediaPlayer2.isPlaying());
            assertTrue(mOnCompletionCalled.isSignalled());
            assertFalse(mOnInfoCalled.isSignalled());

        } finally {
            mMediaPlayer.reset();
            mMediaPlayer2.reset();
        }
        mTestCompleted.signal();

    }

    // The following tests are all a bit flaky, which is why they're retried a
    // few times in a loop.

    // This test uses one mp3 that is silent but has a strong positive DC offset,
    // and a second mp3 that is also silent but has a strong negative DC offset.
    // If the two are played back overlapped, they will cancel each other out,
    // and result in zeroes being detected. If there is a gap in playback, that
    // will also result in zeroes being detected.
    // Note that this test does NOT guarantee that the correct data is played
    public void testGapless1() throws Exception {
        flakyTestWrapper(R.raw.monodcpos, R.raw.monodcneg);
    }

    // This test is similar, but uses two identical m4a files that have some noise
    // with a strong positive DC offset. This is used to detect if there is
    // a gap in playback
    // Note that this test does NOT guarantee that the correct data is played
    public void testGapless2() throws Exception {
        flakyTestWrapper(R.raw.stereonoisedcpos, R.raw.stereonoisedcpos);
    }

    // same as above, but with a mono file
    public void testGapless3() throws Exception {
        flakyTestWrapper(R.raw.mononoisedcpos, R.raw.mononoisedcpos);
    }

    private void flakyTestWrapper(int resid1, int resid2) throws Exception {
        boolean success = false;
        // test usually succeeds within a few tries, but occasionally may fail
        // many times in a row, so be aggressive and try up to 20 times
        for (int i = 0; i < 20 && !success; i++) {
            try {
                testGapless(resid1, resid2);
                success = true;
            } catch (Throwable t) {
                SystemClock.sleep(1000);
            }
        }
        // Try one more time. If this succeeds, we'll consider the test a success,
        // otherwise the exception gets thrown
        if (!success) {
            testGapless(resid1, resid2);
        }
    }

    private void testGapless(int resid1, int resid2) throws Exception {
        MediaPlayer mp1 = null;
        MediaPlayer mp2 = null;
        AudioEffect vc = null;
        Visualizer vis = null;
        AudioManager am = null;
        int oldRingerMode = Integer.MIN_VALUE;
        int oldVolume = Integer.MIN_VALUE;
        try {
            Utils.toggleNotificationPolicyAccess(
                    mContext.getPackageName(), getInstrumentation(), true /* on */);

            mp1 = new MediaPlayer();
            mp1.setAudioStreamType(AudioManager.STREAM_MUSIC);

            AssetFileDescriptor afd = mContext.getResources().openRawResourceFd(resid1);
            mp1.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mp1.prepare();

            int session = mp1.getAudioSessionId();

            mp2 = new MediaPlayer();
            mp2.setAudioSessionId(session);
            mp2.setAudioStreamType(AudioManager.STREAM_MUSIC);

            afd = mContext.getResources().openRawResourceFd(resid2);
            mp2.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mp2.prepare();

            // creating a volume controller on output mix ensures that ro.audio.silent mutes
            // audio after the effects and not before
            vc = new AudioEffect(
                            AudioEffect.EFFECT_TYPE_NULL,
                            UUID.fromString("119341a0-8469-11df-81f9-0002a5d5c51b"),
                            0,
                            session);
            vc.setEnabled(true);
            int captureintervalms = mp1.getDuration() + mp2.getDuration() - 2000;
            int size = 256;
            int[] range = Visualizer.getCaptureSizeRange();
            if (size < range[0]) {
                size = range[0];
            }
            if (size > range[1]) {
                size = range[1];
            }
            byte[] vizdata = new byte[size];

            vis = new Visualizer(session);
            am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            oldRingerMode = am.getRingerMode();
            // make sure we aren't in silent mode
            am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            oldVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            am.setStreamVolume(AudioManager.STREAM_MUSIC, 1, 0);

            assertEquals("setCaptureSize failed",
                    Visualizer.SUCCESS, vis.setCaptureSize(vizdata.length));
            assertEquals("setEnabled failed", Visualizer.SUCCESS, vis.setEnabled(true));

            mp1.setNextMediaPlayer(mp2);
            mp1.start();
            assertTrue(mp1.isPlaying());
            assertFalse(mp2.isPlaying());
            // allow playback to get started
            Thread.sleep(SLEEP_TIME);
            long start = SystemClock.elapsedRealtime();
            // there should be no consecutive zeroes (-128) in the capture buffer
            // when going to the next file. If silence is detected right away, then
            // the volume is probably turned all the way down (visualizer data
            // is captured after volume adjustment).
            boolean first = true;
            while((SystemClock.elapsedRealtime() - start) < captureintervalms) {
                assertTrue(vis.getWaveForm(vizdata) == Visualizer.SUCCESS);
                for (int i = 0; i < vizdata.length - 1; i++) {
                    if (vizdata[i] == -128 && vizdata[i + 1] == -128) {
                        if (first) {
                            fail("silence detected, please increase volume and rerun test");
                        } else {
                            fail("gap or overlap detected at t=" +
                                    (SLEEP_TIME + SystemClock.elapsedRealtime() - start) +
                                    ", offset " + i);
                        }
                        break;
                    }
                }
                first = false;
            }
        } finally {
            if (mp1 != null) {
                mp1.release();
            }
            if (mp2 != null) {
                mp2.release();
            }
            if (vis != null) {
                vis.release();
            }
            if (vc != null) {
                vc.release();
            }
            if (oldRingerMode != Integer.MIN_VALUE) {
                am.setRingerMode(oldRingerMode);
            }
            if (oldVolume != Integer.MIN_VALUE) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, oldVolume, 0);
            }
            Utils.toggleNotificationPolicyAccess(
                    mContext.getPackageName(), getInstrumentation(), false  /* on == false */);
        }
    }

    /**
     * Test for reseting a surface during video playback
     * After reseting, the video should continue playing
     * from the time setDisplay() was called
     */
    public void testVideoSurfaceResetting() throws Exception {
        final int tolerance = 150;
        final int audioLatencyTolerance = 1000;  /* covers audio path latency variability */
        final int seekPos = 4760;  // This is the I-frame position

        final CountDownLatch seekDone = new CountDownLatch(1);

        mMediaPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
            @Override
            public void onSeekComplete(MediaPlayer mp) {
                seekDone.countDown();
            }
        });

        if (!checkLoadResource(R.raw.testvideo)) {
            return; // skip;
        }
        playLoadedVideo(352, 288, -1);

        Thread.sleep(SLEEP_TIME);

        int posBefore = mMediaPlayer.getCurrentPosition();
        mMediaPlayer.setDisplay(getActivity().getSurfaceHolder2());
        int posAfter = mMediaPlayer.getCurrentPosition();

        /* temporarily disable timestamp checking because MediaPlayer now seeks to I-frame
         * position, instead of requested position. setDisplay invovles a seek operation
         * internally.
         */
        // TODO: uncomment out line below when MediaPlayer can seek to requested position.
        // assertEquals(posAfter, posBefore, tolerance);
        assertTrue(mMediaPlayer.isPlaying());

        Thread.sleep(SLEEP_TIME);

        mMediaPlayer.seekTo(seekPos);
        seekDone.await();
        posAfter = mMediaPlayer.getCurrentPosition();
        assertEquals(seekPos, posAfter, tolerance + audioLatencyTolerance);

        Thread.sleep(SLEEP_TIME / 2);
        posBefore = mMediaPlayer.getCurrentPosition();
        mMediaPlayer.setDisplay(null);
        posAfter = mMediaPlayer.getCurrentPosition();
        // TODO: uncomment out line below when MediaPlayer can seek to requested position.
        // assertEquals(posAfter, posBefore, tolerance);
        assertTrue(mMediaPlayer.isPlaying());

        Thread.sleep(SLEEP_TIME);

        posBefore = mMediaPlayer.getCurrentPosition();
        mMediaPlayer.setDisplay(getActivity().getSurfaceHolder());
        posAfter = mMediaPlayer.getCurrentPosition();

        // TODO: uncomment out line below when MediaPlayer can seek to requested position.
        // assertEquals(posAfter, posBefore, tolerance);
        assertTrue(mMediaPlayer.isPlaying());

        Thread.sleep(SLEEP_TIME);
    }

    public void testRecordedVideoPlayback0() throws Exception {
        testRecordedVideoPlaybackWithAngle(0);
    }

    public void testRecordedVideoPlayback90() throws Exception {
        testRecordedVideoPlaybackWithAngle(90);
    }

    public void testRecordedVideoPlayback180() throws Exception {
        testRecordedVideoPlaybackWithAngle(180);
    }

    public void testRecordedVideoPlayback270() throws Exception {
        testRecordedVideoPlaybackWithAngle(270);
    }

    private boolean hasCamera() {
        return getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
    }

    private Camera mCamera;
    private void testRecordedVideoPlaybackWithAngle(int angle) throws Exception {
        int width = RECORDED_VIDEO_WIDTH;
        int height = RECORDED_VIDEO_HEIGHT;
        final String file = RECORDED_FILE;
        final long durationMs = RECORDED_DURATION_MS;

        if (!hasCamera()) {
            return;
        }

        boolean isSupported = false;
        mCamera = Camera.open(0);
        Camera.Parameters parameters = mCamera.getParameters();
        List<Camera.Size> videoSizes = parameters.getSupportedVideoSizes();
        // getSupportedVideoSizes returns null when separate video/preview size
        // is not supported.
        if (videoSizes == null) {
            videoSizes = parameters.getSupportedPreviewSizes();
        }
        for (Camera.Size size : videoSizes)
        {
            if (size.width == width && size.height == height) {
                isSupported = true;
                break;
            }
        }
        mCamera.release();
        mCamera = null;
        if (!isSupported) {
            width = videoSizes.get(0).width;
            height = videoSizes.get(0).height;
        }
        checkOrientation(angle);
        recordVideo(width, height, angle, file, durationMs);
        checkDisplayedVideoSize(width, height, angle, file);
        checkVideoRotationAngle(angle, file);
    }

    private void checkOrientation(int angle) throws Exception {
        assertTrue(angle >= 0);
        assertTrue(angle < 360);
        assertTrue((angle % 90) == 0);
    }

    private void recordVideo(
            int w, int h, int angle, String file, long durationMs) throws Exception {

        MediaRecorder recorder = new MediaRecorder();
        recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        recorder.setOutputFile(file);
        recorder.setOrientationHint(angle);
        recorder.setVideoSize(w, h);
        recorder.setPreviewDisplay(getActivity().getSurfaceHolder2().getSurface());
        recorder.prepare();
        recorder.start();
        Thread.sleep(durationMs);
        recorder.stop();
        recorder.release();
        recorder = null;
    }

    private void checkDisplayedVideoSize(
            int w, int h, int angle, String file) throws Exception {

        int displayWidth  = w;
        int displayHeight = h;
        if ((angle % 180) != 0) {
            displayWidth  = h;
            displayHeight = w;
        }
        playVideoTest(file, displayWidth, displayHeight);
    }

    private void checkVideoRotationAngle(int angle, String file) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(file);
        String rotation = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
        retriever.release();
        retriever = null;
        assertNotNull(rotation);
        assertEquals(Integer.parseInt(rotation), angle);
    }

    // setPlaybackParams() with non-zero speed should start playback.
    public void testSetPlaybackParamsPositiveSpeed() throws Exception {
        if (!checkLoadResource(
                R.raw.video_480x360_mp4_h264_1000kbps_30fps_aac_stereo_128kbps_44100hz)) {
            return; // skip
        }

        mMediaPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
            @Override
            public void onSeekComplete(MediaPlayer mp) {
                mOnSeekCompleteCalled.signal();
            }
        });
        mOnCompletionCalled.reset();
        mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mOnCompletionCalled.signal();
            }
        });
        mMediaPlayer.setDisplay(mActivity.getSurfaceHolder());

        mMediaPlayer.prepare();

        mOnSeekCompleteCalled.reset();
        mMediaPlayer.seekTo(0);
        mOnSeekCompleteCalled.waitForSignal();

        final float playbackRate = 1.0f;

        int playTime = 2000;  // The testing clip is about 10 second long.
        mMediaPlayer.setPlaybackParams(new PlaybackParams().setSpeed(playbackRate));
        assertTrue("MediaPlayer should be playing", mMediaPlayer.isPlaying());
        Thread.sleep(playTime);
        assertTrue("MediaPlayer should still be playing",
                mMediaPlayer.getCurrentPosition() > 0);

        int duration = mMediaPlayer.getDuration();
        mOnSeekCompleteCalled.reset();
        mMediaPlayer.seekTo(duration - 1000);
        mOnSeekCompleteCalled.waitForSignal();

        mOnCompletionCalled.waitForSignal();
        assertFalse("MediaPlayer should not be playing", mMediaPlayer.isPlaying());
        int eosPosition = mMediaPlayer.getCurrentPosition();

        mMediaPlayer.setPlaybackParams(new PlaybackParams().setSpeed(playbackRate));
        assertTrue("MediaPlayer should be playing after EOS", mMediaPlayer.isPlaying());
        Thread.sleep(playTime);
        int position = mMediaPlayer.getCurrentPosition();
        assertTrue("MediaPlayer should still be playing after EOS",
                position > 0 && position < eosPosition);

        mMediaPlayer.stop();
    }

    // setPlaybackParams() with zero speed should pause playback.
    public void testSetPlaybackParamsZeroSpeed() throws Exception {
        if (!checkLoadResource(
                R.raw.video_480x360_mp4_h264_1000kbps_30fps_aac_stereo_128kbps_44100hz)) {
            return; // skip
        }

        mMediaPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
            @Override
            public void onSeekComplete(MediaPlayer mp) {
                mOnSeekCompleteCalled.signal();
            }
        });
        mMediaPlayer.setDisplay(mActivity.getSurfaceHolder());

        mMediaPlayer.prepare();

        mMediaPlayer.setPlaybackParams(new PlaybackParams().setSpeed(0.0f));
        assertFalse("MediaPlayer should not be playing", mMediaPlayer.isPlaying());

        int playTime = 2000;  // The testing clip is about 10 second long.
        mOnSeekCompleteCalled.reset();
        mMediaPlayer.seekTo(0);
        mOnSeekCompleteCalled.waitForSignal();
        Thread.sleep(playTime);
        assertTrue("MediaPlayer should not be playing",
                !mMediaPlayer.isPlaying() && mMediaPlayer.getCurrentPosition() == 0);

        mMediaPlayer.start();
        Thread.sleep(playTime);
        assertTrue("MediaPlayer should be playing",
                mMediaPlayer.isPlaying() && mMediaPlayer.getCurrentPosition() > 0);

        mMediaPlayer.setPlaybackParams(new PlaybackParams().setSpeed(0.0f));
        assertFalse("MediaPlayer should not be playing", mMediaPlayer.isPlaying());
        Thread.sleep(1000);
        int position = mMediaPlayer.getCurrentPosition();
        Thread.sleep(playTime);
        assertTrue("MediaPlayer should be paused", mMediaPlayer.getCurrentPosition() == position);

        mMediaPlayer.stop();
    }

    public void testPlaybackRate() throws Exception {
        final int toleranceMs = 1000;
        if (!checkLoadResource(
                R.raw.video_480x360_mp4_h264_1000kbps_30fps_aac_stereo_128kbps_44100hz)) {
            return; // skip
        }

        mMediaPlayer.setDisplay(mActivity.getSurfaceHolder());
        mMediaPlayer.prepare();
        SyncParams sync = new SyncParams().allowDefaults();
        mMediaPlayer.setSyncParams(sync);
        sync = mMediaPlayer.getSyncParams();

        float[] rates = { 0.25f, 0.5f, 1.0f, 2.0f };
        for (float playbackRate : rates) {
            mMediaPlayer.seekTo(0);
            Thread.sleep(1000);
            int playTime = 4000;  // The testing clip is about 10 second long.
            mMediaPlayer.setPlaybackParams(new PlaybackParams().setSpeed(playbackRate));
            mMediaPlayer.start();
            Thread.sleep(playTime);
            PlaybackParams pbp = mMediaPlayer.getPlaybackParams();
            assertEquals(
                    playbackRate, pbp.getSpeed(),
                    FLOAT_TOLERANCE + playbackRate * sync.getTolerance());
            assertTrue("MediaPlayer should still be playing", mMediaPlayer.isPlaying());

            int playedMediaDurationMs = mMediaPlayer.getCurrentPosition();
            int diff = Math.abs((int)(playedMediaDurationMs / playbackRate) - playTime);
            if (diff > toleranceMs) {
                fail("Media player had error in playback rate " + playbackRate
                     + ", play time is " + playTime + " vs expected " + playedMediaDurationMs);
            }
            mMediaPlayer.pause();
            pbp = mMediaPlayer.getPlaybackParams();
            assertEquals(0.f, pbp.getSpeed(), FLOAT_TOLERANCE);
        }
        mMediaPlayer.stop();
    }

    public void testSeekModes() throws Exception {
        // This clip has 2 I frames at 66687us and 4299687us.
        if (!checkLoadResource(
                R.raw.bbb_s1_320x240_mp4_h264_mp2_800kbps_30fps_aac_lc_5ch_240kbps_44100hz)) {
            return; // skip
        }

        mMediaPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
            @Override
            public void onSeekComplete(MediaPlayer mp) {
                mOnSeekCompleteCalled.signal();
            }
        });
        mMediaPlayer.setDisplay(mActivity.getSurfaceHolder());
        mMediaPlayer.prepare();
        mOnSeekCompleteCalled.reset();
        mMediaPlayer.start();

        final int seekPosMs = 3000;
        final int timeToleranceMs = 100;
        final int syncTime1Ms = 67;
        final int syncTime2Ms = 4300;

        // TODO: tighten checking range. For now, ensure mediaplayer doesn't
        // seek to previous sync or next sync.
        int cp = runSeekMode(MediaPlayer.SEEK_CLOSEST, seekPosMs);
        assertTrue("MediaPlayer did not seek to closest position",
                cp > seekPosMs && cp < syncTime2Ms);

        // TODO: tighten checking range. For now, ensure mediaplayer doesn't
        // seek to closest position or next sync.
        cp = runSeekMode(MediaPlayer.SEEK_PREVIOUS_SYNC, seekPosMs);
        assertTrue("MediaPlayer did not seek to preivous sync position",
                cp < seekPosMs - timeToleranceMs);

        // TODO: tighten checking range. For now, ensure mediaplayer doesn't
        // seek to closest position or previous sync.
        cp = runSeekMode(MediaPlayer.SEEK_NEXT_SYNC, seekPosMs);
        assertTrue("MediaPlayer did not seek to next sync position",
                cp > syncTime2Ms - timeToleranceMs);

        // TODO: tighten checking range. For now, ensure mediaplayer doesn't
        // seek to closest position or previous sync.
        cp = runSeekMode(MediaPlayer.SEEK_CLOSEST_SYNC, seekPosMs);
        assertTrue("MediaPlayer did not seek to closest sync position",
                cp > syncTime2Ms - timeToleranceMs);

        mMediaPlayer.stop();
    }

    private int runSeekMode(int seekMode, int seekPosMs) throws Exception {
        final int sleepIntervalMs = 100;
        int timeRemainedMs = 10000;  // total time for testing
        final int timeToleranceMs = 100;

        mMediaPlayer.seekTo(seekPosMs, seekMode);
        mOnSeekCompleteCalled.waitForSignal();
        mOnSeekCompleteCalled.reset();
        int cp = -seekPosMs;
        while (timeRemainedMs > 0) {
            cp = mMediaPlayer.getCurrentPosition();
            // Wait till MediaPlayer starts rendering since MediaPlayer caches
            // seek position as current position.
            if (cp < seekPosMs - timeToleranceMs || cp > seekPosMs + timeToleranceMs) {
                break;
            }
            timeRemainedMs -= sleepIntervalMs;
            Thread.sleep(sleepIntervalMs);
        }
        assertTrue("MediaPlayer did not finish seeking in time for mode " + seekMode,
                timeRemainedMs > 0);
        return cp;
    }

    public void testGetTimestamp() throws Exception {
        final int toleranceUs = 100000;
        final float playbackRate = 1.0f;
        if (!checkLoadResource(
                R.raw.video_480x360_mp4_h264_1000kbps_30fps_aac_stereo_128kbps_44100hz)) {
            return; // skip
        }

        mMediaPlayer.setDisplay(mActivity.getSurfaceHolder());
        mMediaPlayer.prepare();
        mMediaPlayer.start();
        mMediaPlayer.setPlaybackParams(new PlaybackParams().setSpeed(playbackRate));
        Thread.sleep(SLEEP_TIME);  // let player get into stable state.
        long nt1 = System.nanoTime();
        MediaTimestamp ts1 = mMediaPlayer.getTimestamp();
        long nt2 = System.nanoTime();
        assertTrue("Media player should return a valid time stamp", ts1 != null);
        assertEquals("MediaPlayer had error in clockRate " + ts1.getMediaClockRate(),
                playbackRate, ts1.getMediaClockRate(), 0.001f);
        assertTrue("The nanoTime of Media timestamp should be taken when getTimestamp is called.",
                nt1 <= ts1.getAnchorSytemNanoTime() && ts1.getAnchorSytemNanoTime() <= nt2);

        mMediaPlayer.pause();
        ts1 = mMediaPlayer.getTimestamp();
        assertTrue("Media player should return a valid time stamp", ts1 != null);
        assertTrue("Media player should have play rate of 0.0f when paused",
                ts1.getMediaClockRate() == 0.0f);

        mMediaPlayer.seekTo(0);
        mMediaPlayer.start();
        Thread.sleep(SLEEP_TIME);  // let player get into stable state.
        int playTime = 4000;  // The testing clip is about 10 second long.
        ts1 = mMediaPlayer.getTimestamp();
        assertTrue("Media player should return a valid time stamp", ts1 != null);
        Thread.sleep(playTime);
        MediaTimestamp ts2 = mMediaPlayer.getTimestamp();
        assertTrue("Media player should return a valid time stamp", ts2 != null);
        assertTrue("The clockRate should not be changed.",
                ts1.getMediaClockRate() == ts2.getMediaClockRate());
        assertEquals("MediaPlayer had error in timestamp.",
                ts1.getAnchorMediaTimeUs() + (long)(playTime * ts1.getMediaClockRate() * 1000),
                ts2.getAnchorMediaTimeUs(), toleranceUs);

        mMediaPlayer.stop();
    }

    public void testLocalVideo_MP4_H264_480x360_500kbps_25fps_AAC_Stereo_128kbps_44110Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_480x360_mp4_h264_500kbps_25fps_aac_stereo_128kbps_44100hz, 480, 360);
    }

    public void testLocalVideo_MP4_H264_480x360_500kbps_30fps_AAC_Stereo_128kbps_44110Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_480x360_mp4_h264_500kbps_30fps_aac_stereo_128kbps_44100hz, 480, 360);
    }

    public void testLocalVideo_MP4_H264_480x360_1000kbps_25fps_AAC_Stereo_128kbps_44110Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_480x360_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz, 480, 360);
    }

    public void testLocalVideo_MP4_H264_480x360_1000kbps_30fps_AAC_Stereo_128kbps_44110Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_480x360_mp4_h264_1000kbps_30fps_aac_stereo_128kbps_44100hz, 480, 360);
    }

    public void testLocalVideo_MP4_H264_480x360_1350kbps_25fps_AAC_Stereo_128kbps_44110Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_480x360_mp4_h264_1350kbps_25fps_aac_stereo_128kbps_44100hz, 480, 360);
    }

    public void testLocalVideo_MP4_H264_480x360_1350kbps_30fps_AAC_Stereo_128kbps_44110Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_128kbps_44100hz, 480, 360);
    }

    public void testLocalVideo_MP4_H264_480x360_1350kbps_30fps_AAC_Stereo_128kbps_44110Hz_frag()
            throws Exception {
        playVideoTest(
                R.raw.video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_128kbps_44100hz_fragmented,
                480, 360);
    }


    public void testLocalVideo_MP4_H264_480x360_1350kbps_30fps_AAC_Stereo_192kbps_44110Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_192kbps_44100hz, 480, 360);
    }

    public void testLocalVideo_3gp_H263_176x144_56kbps_12fps_AAC_Mono_24kbps_11025Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_56kbps_12fps_aac_mono_24kbps_11025hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_56kbps_12fps_AAC_Mono_24kbps_22050Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_56kbps_12fps_aac_mono_24kbps_22050hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_56kbps_12fps_AAC_Stereo_24kbps_11025Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_56kbps_12fps_aac_stereo_24kbps_11025hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_56kbps_12fps_AAC_Stereo_24kbps_22050Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_56kbps_12fps_aac_stereo_24kbps_11025hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_56kbps_12fps_AAC_Stereo_128kbps_11025Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_56kbps_12fps_aac_stereo_128kbps_11025hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_56kbps_12fps_AAC_Stereo_128kbps_22050Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_56kbps_12fps_aac_stereo_128kbps_11025hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_56kbps_25fps_AAC_Mono_24kbps_11025Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_56kbps_25fps_aac_mono_24kbps_11025hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_56kbps_25fps_AAC_Mono_24kbps_22050Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_56kbps_25fps_aac_mono_24kbps_22050hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_56kbps_25fps_AAC_Stereo_24kbps_11025Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_56kbps_25fps_aac_stereo_24kbps_11025hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_56kbps_25fps_AAC_Stereo_24kbps_22050Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_56kbps_25fps_aac_stereo_24kbps_11025hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_56kbps_25fps_AAC_Stereo_128kbps_11025Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_56kbps_25fps_aac_stereo_128kbps_11025hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_56kbps_25fps_AAC_Stereo_128kbps_22050Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_56kbps_25fps_aac_stereo_128kbps_11025hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_300kbps_12fps_AAC_Mono_24kbps_11025Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_300kbps_12fps_aac_mono_24kbps_11025hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_300kbps_12fps_AAC_Mono_24kbps_22050Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_300kbps_12fps_aac_mono_24kbps_22050hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_300kbps_12fps_AAC_Stereo_24kbps_11025Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_300kbps_12fps_aac_stereo_24kbps_11025hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_300kbps_12fps_AAC_Stereo_24kbps_22050Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_300kbps_12fps_aac_stereo_24kbps_11025hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_300kbps_12fps_AAC_Stereo_128kbps_11025Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_300kbps_12fps_aac_stereo_128kbps_11025hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_300kbps_12fps_AAC_Stereo_128kbps_22050Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_300kbps_12fps_aac_stereo_128kbps_11025hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_300kbps_25fps_AAC_Mono_24kbps_11025Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_300kbps_25fps_aac_mono_24kbps_11025hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_300kbps_25fps_AAC_Mono_24kbps_22050Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_300kbps_25fps_aac_mono_24kbps_22050hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_300kbps_25fps_AAC_Stereo_24kbps_11025Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_300kbps_25fps_aac_stereo_24kbps_11025hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_300kbps_25fps_AAC_Stereo_24kbps_22050Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_300kbps_25fps_aac_stereo_24kbps_11025hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_300kbps_25fps_AAC_Stereo_128kbps_11025Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_300kbps_25fps_aac_stereo_128kbps_11025hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_300kbps_25fps_AAC_Stereo_128kbps_22050Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_300kbps_25fps_aac_stereo_128kbps_22050hz, 176, 144);
    }

    private void readSubtitleTracks() throws Exception {
        mSubtitleTrackIndex.clear();
        MediaPlayer.TrackInfo[] trackInfos = mMediaPlayer.getTrackInfo();
        if (trackInfos == null || trackInfos.length == 0) {
            return;
        }

        Vector<Integer> subtitleTrackIndex = new Vector<>();
        for (int i = 0; i < trackInfos.length; ++i) {
            assertTrue(trackInfos[i] != null);
            if (trackInfos[i].getTrackType() == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_SUBTITLE) {
                subtitleTrackIndex.add(i);
            }
        }

        mSubtitleTrackIndex.addAll(subtitleTrackIndex);
    }

    private void selectSubtitleTrack(int index) throws Exception {
        int trackIndex = mSubtitleTrackIndex.get(index);
        mMediaPlayer.selectTrack(trackIndex);
        mSelectedSubtitleIndex = index;
    }

    private void deselectSubtitleTrack(int index) throws Exception {
        int trackIndex = mSubtitleTrackIndex.get(index);
        mMediaPlayer.deselectTrack(trackIndex);
        if (mSelectedSubtitleIndex == index) {
            mSelectedSubtitleIndex = -1;
        }
    }

    public void testDeselectTrackForSubtitleTracks() throws Throwable {
        if (!checkLoadResource(R.raw.testvideo_with_2_subtitle_tracks)) {
            return; // skip;
        }

        getInstrumentation().waitForIdleSync();

        mMediaPlayer.setOnSubtitleDataListener(new MediaPlayer.OnSubtitleDataListener() {
            @Override
            public void onSubtitleData(MediaPlayer mp, SubtitleData data) {
                if (data != null && data.getData() != null) {
                    mOnSubtitleDataCalled.signal();
                }
            }
        });
        mMediaPlayer.setOnInfoListener(new MediaPlayer.OnInfoListener() {
            @Override
            public boolean onInfo(MediaPlayer mp, int what, int extra) {
                if (what == MediaPlayer.MEDIA_INFO_METADATA_UPDATE) {
                    mOnInfoCalled.signal();
                }
                return false;
            }
        });

        mMediaPlayer.setDisplay(getActivity().getSurfaceHolder());
        mMediaPlayer.setScreenOnWhilePlaying(true);
        mMediaPlayer.setWakeMode(mContext, PowerManager.PARTIAL_WAKE_LOCK);

        mMediaPlayer.prepare();
        mMediaPlayer.start();
        assertTrue(mMediaPlayer.isPlaying());

        // Closed caption tracks are in-band.
        // So, those tracks will be found after processing a number of frames.
        mOnInfoCalled.waitForSignal(1500);

        mOnInfoCalled.reset();
        mOnInfoCalled.waitForSignal(1500);

        readSubtitleTracks();

        // Run twice to check if repeated selection-deselection on the same track works well.
        for (int i = 0; i < 2; i++) {
            // Waits until at least one subtitle is fired. Timeout is 2.5 seconds.
            selectSubtitleTrack(i);
            mOnSubtitleDataCalled.reset();
            assertTrue(mOnSubtitleDataCalled.waitForSignal(2500));

            // Try deselecting track.
            deselectSubtitleTrack(i);
            mOnSubtitleDataCalled.reset();
            assertFalse(mOnSubtitleDataCalled.waitForSignal(1500));
        }

        try {
            deselectSubtitleTrack(0);
            fail("Deselecting unselected track: expected RuntimeException, " +
                 "but no exception has been triggered.");
        } catch (RuntimeException e) {
            // expected
        }

        mMediaPlayer.stop();
    }

    public void testChangeSubtitleTrack() throws Throwable {
        if (!checkLoadResource(R.raw.testvideo_with_2_subtitle_tracks)) {
            return; // skip;
        }

        mMediaPlayer.setOnSubtitleDataListener(new MediaPlayer.OnSubtitleDataListener() {
            @Override
            public void onSubtitleData(MediaPlayer mp, SubtitleData data) {
                if (data != null && data.getData() != null) {
                    mOnSubtitleDataCalled.signal();
                }
            }
        });
        mMediaPlayer.setOnInfoListener(new MediaPlayer.OnInfoListener() {
            @Override
            public boolean onInfo(MediaPlayer mp, int what, int extra) {
                if (what == MediaPlayer.MEDIA_INFO_METADATA_UPDATE) {
                    mOnInfoCalled.signal();
                }
                return false;
            }
        });

        mMediaPlayer.setDisplay(getActivity().getSurfaceHolder());
        mMediaPlayer.setScreenOnWhilePlaying(true);
        mMediaPlayer.setWakeMode(mContext, PowerManager.PARTIAL_WAKE_LOCK);

        mMediaPlayer.prepare();
        mMediaPlayer.start();
        assertTrue(mMediaPlayer.isPlaying());

        // Closed caption tracks are in-band.
        // So, those tracks will be found after processing a number of frames.
        mOnInfoCalled.waitForSignal(1500);

        mOnInfoCalled.reset();
        mOnInfoCalled.waitForSignal(1500);

        readSubtitleTracks();

        // Waits until at least two captions are fired. Timeout is 2.5 sec.
        selectSubtitleTrack(0);
        assertTrue(mOnSubtitleDataCalled.waitForCountedSignals(2, 2500) >= 2);

        mOnSubtitleDataCalled.reset();
        selectSubtitleTrack(1);
        assertTrue(mOnSubtitleDataCalled.waitForCountedSignals(2, 2500) >= 2);

        mMediaPlayer.stop();
    }

    public void testGetTrackInfoForVideoWithSubtitleTracks() throws Throwable {
        if (!checkLoadResource(R.raw.testvideo_with_2_subtitle_tracks)) {
            return; // skip;
        }

        getInstrumentation().waitForIdleSync();

        mMediaPlayer.setOnInfoListener(new MediaPlayer.OnInfoListener() {
            @Override
            public boolean onInfo(MediaPlayer mp, int what, int extra) {
                if (what == MediaPlayer.MEDIA_INFO_METADATA_UPDATE) {
                    mOnInfoCalled.signal();
                }
                return false;
            }
        });

        mMediaPlayer.setDisplay(getActivity().getSurfaceHolder());
        mMediaPlayer.setScreenOnWhilePlaying(true);
        mMediaPlayer.setWakeMode(mContext, PowerManager.PARTIAL_WAKE_LOCK);

        mMediaPlayer.prepare();
        mMediaPlayer.start();
        assertTrue(mMediaPlayer.isPlaying());

        // The media metadata will be changed while playing since closed caption tracks are in-band
        // and those tracks will be found after processing a number of frames. These tracks will be
        // found within one second.
        mOnInfoCalled.waitForSignal(1500);

        mOnInfoCalled.reset();
        mOnInfoCalled.waitForSignal(1500);

        readSubtitleTracks();
        assertEquals(2, mSubtitleTrackIndex.size());

        mMediaPlayer.stop();
    }

    private void readTimedTextTracks() throws Exception {
        mTimedTextTrackIndex.clear();
        MediaPlayer.TrackInfo[] trackInfos = mMediaPlayer.getTrackInfo();
        if (trackInfos == null || trackInfos.length == 0) {
            return;
        }

        Vector<Integer> externalTrackIndex = new Vector<>();
        for (int i = 0; i < trackInfos.length; ++i) {
            assertTrue(trackInfos[i] != null);
            if (trackInfos[i].getTrackType() == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT) {
                MediaFormat format = trackInfos[i].getFormat();
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (MediaPlayer.MEDIA_MIMETYPE_TEXT_SUBRIP.equals(mime)) {
                    externalTrackIndex.add(i);
                } else {
                    mTimedTextTrackIndex.add(i);
                }
            }
        }

        mTimedTextTrackIndex.addAll(externalTrackIndex);
    }

    private int getTimedTextTrackCount() {
        return mTimedTextTrackIndex.size();
    }

    private void selectTimedTextTrack(int index) throws Exception {
        int trackIndex = mTimedTextTrackIndex.get(index);
        mMediaPlayer.selectTrack(trackIndex);
        mSelectedTimedTextIndex = index;
    }

    private void deselectTimedTextTrack(int index) throws Exception {
        int trackIndex = mTimedTextTrackIndex.get(index);
        mMediaPlayer.deselectTrack(trackIndex);
        if (mSelectedTimedTextIndex == index) {
            mSelectedTimedTextIndex = -1;
        }
    }

    public void testDeselectTrackForTimedTextTrack() throws Throwable {
        if (!checkLoadResource(R.raw.testvideo_with_2_timedtext_tracks)) {
            return; // skip;
        }
        runTestOnUiThread(new Runnable() {
            public void run() {
                try {
                    loadSubtitleSource(R.raw.test_subtitle1_srt);
                } catch (Exception e) {
                    throw new AssertionFailedError(e.getMessage());
                }
            }
        });
        getInstrumentation().waitForIdleSync();

        mMediaPlayer.setDisplay(getActivity().getSurfaceHolder());
        mMediaPlayer.setScreenOnWhilePlaying(true);
        mMediaPlayer.setWakeMode(mContext, PowerManager.PARTIAL_WAKE_LOCK);
        mMediaPlayer.setOnTimedTextListener(new MediaPlayer.OnTimedTextListener() {
            @Override
            public void onTimedText(MediaPlayer mp, TimedText text) {
                if (text != null) {
                    String plainText = text.getText();
                    if (plainText != null) {
                        mOnTimedTextCalled.signal();
                        Log.d(LOG_TAG, "text: " + plainText.trim());
                    }
                }
            }
        });
        mMediaPlayer.prepare();
        readTimedTextTracks();
        assertEquals(getTimedTextTrackCount(), 3);

        mMediaPlayer.start();
        assertTrue(mMediaPlayer.isPlaying());

        // Run twice to check if repeated selection-deselection on the same track works well.
        for (int i = 0; i < 2; i++) {
            // Waits until at least one subtitle is fired. Timeout is 1.5 sec.
            selectTimedTextTrack(0);
            mOnTimedTextCalled.reset();
            assertTrue(mOnTimedTextCalled.waitForSignal(1500));

            // Try deselecting track.
            deselectTimedTextTrack(0);
            mOnTimedTextCalled.reset();
            assertFalse(mOnTimedTextCalled.waitForSignal(1500));
        }

        // Run the same test for external subtitle track.
        for (int i = 0; i < 2; i++) {
            selectTimedTextTrack(2);
            mOnTimedTextCalled.reset();
            assertTrue(mOnTimedTextCalled.waitForSignal(1500));

            // Try deselecting track.
            deselectTimedTextTrack(2);
            mOnTimedTextCalled.reset();
            assertFalse(mOnTimedTextCalled.waitForSignal(1500));
        }

        try {
            deselectTimedTextTrack(0);
            fail("Deselecting unselected track: expected RuntimeException, " +
                 "but no exception has been triggered.");
        } catch (RuntimeException e) {
            // expected
        }

        mMediaPlayer.stop();
    }

    public void testChangeTimedTextTrack() throws Throwable {
        testTimedText(R.raw.testvideo_with_2_timedtext_tracks, 2,
                new int[] {R.raw.test_subtitle1_srt, R.raw.test_subtitle2_srt},
                new VerifyAndSignalTimedText(),
                new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        selectTimedTextTrack(0);
                        mOnTimedTextCalled.reset();

                        mMediaPlayer.start();
                        assertTrue(mMediaPlayer.isPlaying());

                        // Waits until at least two subtitles are fired. Timeout is 2.5 sec.
                        // Please refer the test srt files:
                        // test_subtitle1_srt.3gp and test_subtitle2_srt.3gp
                        assertTrue(mOnTimedTextCalled.waitForCountedSignals(2, 2500) >= 2);

                        selectTimedTextTrack(1);
                        mOnTimedTextCalled.reset();
                        assertTrue(mOnTimedTextCalled.waitForCountedSignals(2, 2500) >= 2);

                        selectTimedTextTrack(2);
                        mOnTimedTextCalled.reset();
                        assertTrue(mOnTimedTextCalled.waitForCountedSignals(2, 2500) >= 2);

                        selectTimedTextTrack(3);
                        mOnTimedTextCalled.reset();
                        assertTrue(mOnTimedTextCalled.waitForCountedSignals(2, 2500) >= 2);
                        mMediaPlayer.stop();

                        assertEquals("Wrong bounds count", 2, mBoundsCount);
                        return null;
                    }
                });
    }

    public void testSeekWithTimedText() throws Throwable {
        AtomicInteger iteration = new AtomicInteger(5);
        AtomicInteger num = new AtomicInteger(10);
        try {
            Bundle args = InstrumentationRegistry.getArguments();
            num.set(Integer.parseInt(args.getString("num", "10")));
            iteration.set(Integer.parseInt(args.getString("iteration", "5")));
        } catch (Exception e) {
            Log.w(LOG_TAG, "bad num/iteration arguments, using default", e);
        }
        testTimedText(R.raw.testvideo_with_2_timedtext_tracks, 2, new int[] {},
                new VerifyAndSignalTimedText(num.get(), true),
                new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        selectTimedTextTrack(0);
                        mOnSeekCompleteCalled.reset();
                        mOnTimedTextCalled.reset();
                        OnSeekCompleteListener seekListener = new OnSeekCompleteListener() {
                            @Override
                            public void onSeekComplete(MediaPlayer mp) {
                                mOnSeekCompleteCalled.signal();
                            }
                        };
                        mMediaPlayer.setOnSeekCompleteListener(seekListener);
                        mMediaPlayer.start();
                        assertTrue(mMediaPlayer.isPlaying());
                        int n = num.get();
                        for (int i = 0; i < iteration.get(); ++i) {
                            assertTrue(mOnTimedTextCalled.waitForCountedSignals(n, 15000) == n);
                            mOnTimedTextCalled.reset();
                            mMediaPlayer.seekTo(0);
                            mOnSeekCompleteCalled.waitForSignal();
                            mOnSeekCompleteCalled.reset();
                        }
                        mMediaPlayer.stop();
                        return null;
                    }
                });
    }

    private void testTimedText(
            int resource, int numInternalTracks, int[] subtitleResources,
            OnTimedTextListener onTimedTextListener, Callable<?> testBody) throws Throwable {
        if (!checkLoadResource(resource)) {
            return; // skip;
        }

        mMediaPlayer.setDisplay(getActivity().getSurfaceHolder());
        mMediaPlayer.setScreenOnWhilePlaying(true);
        mMediaPlayer.setWakeMode(mContext, PowerManager.PARTIAL_WAKE_LOCK);
        mMediaPlayer.setOnTimedTextListener(onTimedTextListener);
        mBoundsCount = 0;

        mMediaPlayer.prepare();
        assertFalse(mMediaPlayer.isPlaying());
        runTestOnUiThread(new Runnable() {
            public void run() {
                try {
                    readTimedTextTracks();
                } catch (Exception e) {
                    throw new AssertionFailedError(e.getMessage());
                }
            }
        });
        getInstrumentation().waitForIdleSync();
        assertEquals(getTimedTextTrackCount(), numInternalTracks);

        runTestOnUiThread(new Runnable() {
            public void run() {
                try {
                    // Adds two more external subtitle files.
                    for (int subRes : subtitleResources) {
                        loadSubtitleSource(subRes);
                    }
                    readTimedTextTracks();
                } catch (Exception e) {
                    throw new AssertionFailedError(e.getMessage());
                }
            }
        });
        getInstrumentation().waitForIdleSync();
        assertEquals(getTimedTextTrackCount(), numInternalTracks + subtitleResources.length);

        testBody.call();
    }

    public void testGetTrackInfoForVideoWithTimedText() throws Throwable {
        if (!checkLoadResource(R.raw.testvideo_with_2_timedtext_tracks)) {
            return; // skip;
        }
        runTestOnUiThread(new Runnable() {
            public void run() {
                try {
                    loadSubtitleSource(R.raw.test_subtitle1_srt);
                    loadSubtitleSource(R.raw.test_subtitle2_srt);
                } catch (Exception e) {
                    throw new AssertionFailedError(e.getMessage());
                }
            }
        });
        getInstrumentation().waitForIdleSync();
        mMediaPlayer.prepare();
        mMediaPlayer.start();

        readTimedTextTracks();
        selectTimedTextTrack(2);

        int count = 0;
        MediaPlayer.TrackInfo[] trackInfos = mMediaPlayer.getTrackInfo();
        assertTrue(trackInfos != null && trackInfos.length != 0);
        for (int i = 0; i < trackInfos.length; ++i) {
            assertTrue(trackInfos[i] != null);
            if (trackInfos[i].getTrackType() == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT) {
                String trackLanguage = trackInfos[i].getLanguage();
                assertTrue(trackLanguage != null);
                trackLanguage = trackLanguage.trim();
                Log.d(LOG_TAG, "track info lang: " + trackLanguage);
                assertTrue("Should not see empty track language with our test data.",
                           trackLanguage.length() > 0);
                count++;
            }
        }
        // There are 4 subtitle tracks in total in our test data.
        assertEquals(4, count);
    }

    /*
     *  This test assumes the resources being tested are between 8 and 14 seconds long
     *  The ones being used here are 10 seconds long.
     */
    public void testResumeAtEnd() throws Throwable {
        int testsRun =
            testResumeAtEnd(R.raw.loudsoftmp3) +
            testResumeAtEnd(R.raw.loudsoftwav) +
            testResumeAtEnd(R.raw.loudsoftogg) +
            testResumeAtEnd(R.raw.loudsoftitunes) +
            testResumeAtEnd(R.raw.loudsoftfaac) +
            testResumeAtEnd(R.raw.loudsoftaac);
        if (testsRun == 0) {
            MediaUtils.skipTest("no decoder found");
        }
    }

    // returns 1 if test was run, 0 otherwise
    private int testResumeAtEnd(int res) throws Throwable {
        if (!loadResource(res)) {
            Log.i(LOG_TAG, "testResumeAtEnd: No decoder found for " +
                mContext.getResources().getResourceEntryName(res) +
                " --- skipping.");
            return 0; // skip
        }
        mMediaPlayer.prepare();
        mOnCompletionCalled.reset();
        mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mOnCompletionCalled.signal();
                mMediaPlayer.start();
            }
        });
        // skip the first part of the file so we reach EOF sooner
        mMediaPlayer.seekTo(5000);
        mMediaPlayer.start();
        // sleep long enough that we restart playback at least once, but no more
        Thread.sleep(10000);
        assertTrue("MediaPlayer should still be playing", mMediaPlayer.isPlaying());
        mMediaPlayer.reset();
        assertEquals("wrong number of repetitions", 1, mOnCompletionCalled.getNumSignal());
        return 1;
    }

    public void testPositionAtEnd() throws Throwable {
        int testsRun =
            testPositionAtEnd(R.raw.test1m1shighstereo) +
            testPositionAtEnd(R.raw.loudsoftmp3) +
            testPositionAtEnd(R.raw.loudsoftwav) +
            testPositionAtEnd(R.raw.loudsoftogg) +
            testPositionAtEnd(R.raw.loudsoftitunes) +
            testPositionAtEnd(R.raw.loudsoftfaac) +
            testPositionAtEnd(R.raw.loudsoftaac);
        if (testsRun == 0) {
            MediaUtils.skipTest(LOG_TAG, "no decoder found");
        }
    }

    private int testPositionAtEnd(int res) throws Throwable {
        if (!loadResource(res)) {
            Log.i(LOG_TAG, "testPositionAtEnd: No decoder found for " +
                mContext.getResources().getResourceEntryName(res) +
                " --- skipping.");
            return 0; // skip
        }
        mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mMediaPlayer.prepare();
        int duration = mMediaPlayer.getDuration();
        assertTrue("resource too short", duration > 6000);
        mOnCompletionCalled.reset();
        mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mOnCompletionCalled.signal();
            }
        });
        mMediaPlayer.seekTo(duration - 5000);
        mMediaPlayer.start();
        while (mMediaPlayer.isPlaying()) {
            Log.i("@@@@", "position: " + mMediaPlayer.getCurrentPosition());
            Thread.sleep(500);
        }
        Log.i("@@@@", "final position: " + mMediaPlayer.getCurrentPosition());
        assertTrue(mMediaPlayer.getCurrentPosition() > duration - 1000);
        mMediaPlayer.reset();
        return 1;
    }

    public void testCallback() throws Throwable {
        final int mp4Duration = 8484;

        if (!checkLoadResource(R.raw.testvideo)) {
            return; // skip;
        }

        mMediaPlayer.setDisplay(getActivity().getSurfaceHolder());
        mMediaPlayer.setScreenOnWhilePlaying(true);

        mMediaPlayer.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener() {
            @Override
            public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
                mOnVideoSizeChangedCalled.signal();
            }
        });

        mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mOnPrepareCalled.signal();
            }
        });

        mMediaPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
            @Override
            public void onSeekComplete(MediaPlayer mp) {
                mOnSeekCompleteCalled.signal();
            }
        });

        mOnCompletionCalled.reset();
        mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mOnCompletionCalled.signal();
            }
        });

        mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                mOnErrorCalled.signal();
                return false;
            }
        });

        mMediaPlayer.setOnInfoListener(new MediaPlayer.OnInfoListener() {
            @Override
            public boolean onInfo(MediaPlayer mp, int what, int extra) {
                mOnInfoCalled.signal();
                return false;
            }
        });

        assertFalse(mOnPrepareCalled.isSignalled());
        assertFalse(mOnVideoSizeChangedCalled.isSignalled());
        mMediaPlayer.prepare();
        mOnPrepareCalled.waitForSignal();
        mOnVideoSizeChangedCalled.waitForSignal();
        mOnSeekCompleteCalled.reset();
        mMediaPlayer.seekTo(mp4Duration >> 1);
        mOnSeekCompleteCalled.waitForSignal();
        assertFalse(mOnCompletionCalled.isSignalled());
        mMediaPlayer.start();
        while(mMediaPlayer.isPlaying()) {
            Thread.sleep(SLEEP_TIME);
        }
        assertFalse(mMediaPlayer.isPlaying());
        mOnCompletionCalled.waitForSignal();
        assertFalse(mOnErrorCalled.isSignalled());
        mMediaPlayer.stop();
        mMediaPlayer.start();
        mOnErrorCalled.waitForSignal();
    }

    public void testRecordAndPlay() throws Exception {
        if (!hasMicrophone()) {
            MediaUtils.skipTest(LOG_TAG, "no microphone");
            return;
        }
        if (!MediaUtils.checkDecoder(MediaFormat.MIMETYPE_AUDIO_AMR_NB)
                || !MediaUtils.checkEncoder(MediaFormat.MIMETYPE_AUDIO_AMR_NB)) {
            return; // skip
        }
        File outputFile = new File(Environment.getExternalStorageDirectory(),
                "record_and_play.3gp");
        String outputFileLocation = outputFile.getAbsolutePath();
        try {
            recordMedia(outputFileLocation);
            MediaPlayer mp = new MediaPlayer();
            try {
                mp.setDataSource(outputFileLocation);
                mp.prepareAsync();
                Thread.sleep(SLEEP_TIME);
                playAndStop(mp);
            } finally {
                mp.release();
            }

            Uri uri = Uri.parse(outputFileLocation);
            mp = new MediaPlayer();
            try {
                mp.setDataSource(mContext, uri);
                mp.prepareAsync();
                Thread.sleep(SLEEP_TIME);
                playAndStop(mp);
            } finally {
                mp.release();
            }

            try {
                mp = MediaPlayer.create(mContext, uri);
                playAndStop(mp);
            } finally {
                if (mp != null) {
                    mp.release();
                }
            }

            try {
                mp = MediaPlayer.create(mContext, uri, getActivity().getSurfaceHolder());
                playAndStop(mp);
            } finally {
                if (mp != null) {
                    mp.release();
                }
            }
        } finally {
            outputFile.delete();
        }
    }

    private void playAndStop(MediaPlayer mp) throws Exception {
        mp.start();
        Thread.sleep(SLEEP_TIME);
        mp.stop();
    }

    private void recordMedia(String outputFile) throws Exception {
        MediaRecorder mr = new MediaRecorder();
        try {
            mr.setAudioSource(MediaRecorder.AudioSource.MIC);
            mr.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mr.setOutputFile(outputFile);

            mr.prepare();
            mr.start();
            Thread.sleep(SLEEP_TIME);
            mr.stop();
        } finally {
            mr.release();
        }
    }

    private boolean hasMicrophone() {
        return getActivity().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_MICROPHONE);
    }

    // Smoke test playback from a MediaDataSource.
    public void testPlaybackFromAMediaDataSource() throws Exception {
        final int resid = R.raw.video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_192kbps_44100hz;
        final int duration = 10000;

        if (!MediaUtils.hasCodecsForResource(mContext, resid)) {
            return;
        }

        TestMediaDataSource dataSource =
                TestMediaDataSource.fromAssetFd(mResources.openRawResourceFd(resid));
        // Test returning -1 from getSize() to indicate unknown size.
        dataSource.returnFromGetSize(-1);
        mMediaPlayer.setDataSource(dataSource);
        playLoadedVideo(null, null, -1);
        assertTrue(mMediaPlayer.isPlaying());

        // Test pause and restart.
        mMediaPlayer.pause();
        Thread.sleep(SLEEP_TIME);
        assertFalse(mMediaPlayer.isPlaying());
        mMediaPlayer.start();
        assertTrue(mMediaPlayer.isPlaying());

        // Test reset.
        mMediaPlayer.stop();
        mMediaPlayer.reset();
        mMediaPlayer.setDataSource(dataSource);
        mMediaPlayer.prepare();
        mMediaPlayer.start();
        assertTrue(mMediaPlayer.isPlaying());

        // Test seek. Note: the seek position is cached and returned as the
        // current position so there's no point in comparing them.
        mMediaPlayer.seekTo(duration - SLEEP_TIME);
        while (mMediaPlayer.isPlaying()) {
            Thread.sleep(SLEEP_TIME);
        }
    }

    public void testNullMediaDataSourceIsRejected() throws Exception {
        try {
            mMediaPlayer.setDataSource((MediaDataSource) null);
            fail("Null MediaDataSource was accepted");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testMediaDataSourceIsClosedOnReset() throws Exception {
        TestMediaDataSource dataSource = new TestMediaDataSource(new byte[0]);
        mMediaPlayer.setDataSource(dataSource);
        mMediaPlayer.reset();
        assertTrue(dataSource.isClosed());
    }

    public void testPlaybackFailsIfMediaDataSourceThrows() throws Exception {
        final int resid = R.raw.video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_192kbps_44100hz;
        if (!MediaUtils.hasCodecsForResource(mContext, resid)) {
            return;
        }

        setOnErrorListener();
        TestMediaDataSource dataSource =
                TestMediaDataSource.fromAssetFd(mResources.openRawResourceFd(resid));
        mMediaPlayer.setDataSource(dataSource);
        mMediaPlayer.prepare();

        dataSource.throwFromReadAt();
        mMediaPlayer.start();
        assertTrue(mOnErrorCalled.waitForSignal());
    }

    public void testPlaybackFailsIfMediaDataSourceReturnsAnError() throws Exception {
        final int resid = R.raw.video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_192kbps_44100hz;
        if (!MediaUtils.hasCodecsForResource(mContext, resid)) {
            return;
        }

        setOnErrorListener();
        TestMediaDataSource dataSource =
                TestMediaDataSource.fromAssetFd(mResources.openRawResourceFd(resid));
        mMediaPlayer.setDataSource(dataSource);
        mMediaPlayer.prepare();

        dataSource.returnFromReadAt(-2);
        mMediaPlayer.start();
        assertTrue(mOnErrorCalled.waitForSignal());
    }
}
