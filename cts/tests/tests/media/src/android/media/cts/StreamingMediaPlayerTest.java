/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.media.BufferingParams;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.media.MediaPlayer.TrackInfo;
import android.media.TimedMetaData;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.test.InstrumentationTestRunner;
import android.util.Log;
import android.webkit.cts.CtsTestServer;

import com.android.compatibility.common.util.DynamicConfigDeviceSide;
import com.android.compatibility.common.util.MediaUtils;

import java.io.IOException;
import java.net.HttpCookie;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.HashMap;
import java.util.List;

/**
 * Tests of MediaPlayer streaming capabilities.
 */
public class StreamingMediaPlayerTest extends MediaPlayerTestBase {
    private static final String TAG = "StreamingMediaPlayerTest";

    private static final String HTTP_H263_AMR_VIDEO_1_KEY =
            "streaming_media_player_test_http_h263_amr_video1";
    private static final String HTTP_H263_AMR_VIDEO_2_KEY =
            "streaming_media_player_test_http_h263_amr_video2";
    private static final String HTTP_H264_BASE_AAC_VIDEO_1_KEY =
            "streaming_media_player_test_http_h264_base_aac_video1";
    private static final String HTTP_H264_BASE_AAC_VIDEO_2_KEY =
            "streaming_media_player_test_http_h264_base_aac_video2";
    private static final String HTTP_MPEG4_SP_AAC_VIDEO_1_KEY =
            "streaming_media_player_test_http_mpeg4_sp_aac_video1";
    private static final String HTTP_MPEG4_SP_AAC_VIDEO_2_KEY =
            "streaming_media_player_test_http_mpeg4_sp_aac_video2";
    private static final String MODULE_NAME = "CtsMediaTestCases";
    private DynamicConfigDeviceSide dynamicConfig;

    private CtsTestServer mServer;

    private String mInputUrl;

    @Override
    protected void setUp() throws Exception {
        // if launched with InstrumentationTestRunner to pass a command line argument
        if (getInstrumentation() instanceof InstrumentationTestRunner) {
            InstrumentationTestRunner testRunner =
                    (InstrumentationTestRunner)getInstrumentation();

            Bundle arguments = testRunner.getArguments();
            mInputUrl = arguments.getString("url");
            Log.v(TAG, "setUp: arguments: " + arguments);
            if (mInputUrl != null) {
                Log.v(TAG, "setUp: arguments[url] " + mInputUrl);
            }
        }

        super.setUp();
        dynamicConfig = new DynamicConfigDeviceSide(MODULE_NAME);
    }

/* RTSP tests are more flaky and vulnerable to network condition.
   Disable until better solution is available
    // Streaming RTSP video from YouTube
    public void testRTSP_H263_AMR_Video1() throws Exception {
        playVideoTest("rtsp://v2.cache7.c.youtube.com/video.3gp?cid=0x271de9756065677e"
                + "&fmt=13&user=android-device-test", 176, 144);
    }
    public void testRTSP_H263_AMR_Video2() throws Exception {
        playVideoTest("rtsp://v2.cache7.c.youtube.com/video.3gp?cid=0xc80658495af60617"
                + "&fmt=13&user=android-device-test", 176, 144);
    }

    public void testRTSP_MPEG4SP_AAC_Video1() throws Exception {
        playVideoTest("rtsp://v2.cache7.c.youtube.com/video.3gp?cid=0x271de9756065677e"
                + "&fmt=17&user=android-device-test", 176, 144);
    }
    public void testRTSP_MPEG4SP_AAC_Video2() throws Exception {
        playVideoTest("rtsp://v2.cache7.c.youtube.com/video.3gp?cid=0xc80658495af60617"
                + "&fmt=17&user=android-device-test", 176, 144);
    }

    public void testRTSP_H264Base_AAC_Video1() throws Exception {
        playVideoTest("rtsp://v2.cache7.c.youtube.com/video.3gp?cid=0x271de9756065677e"
                + "&fmt=18&user=android-device-test", 480, 270);
    }
    public void testRTSP_H264Base_AAC_Video2() throws Exception {
        playVideoTest("rtsp://v2.cache7.c.youtube.com/video.3gp?cid=0xc80658495af60617"
                + "&fmt=18&user=android-device-test", 480, 270);
    }
*/
    // Streaming HTTP video from YouTube
    public void testHTTP_H263_AMR_Video1() throws Exception {
        if (!MediaUtils.checkDecoder(MediaFormat.MIMETYPE_VIDEO_H263, MediaFormat.MIMETYPE_AUDIO_AMR_NB)) {
            return; // skip
        }

        String urlString = dynamicConfig.getValue(HTTP_H263_AMR_VIDEO_1_KEY);
        playVideoTest(urlString, 176, 144);
    }

    public void testHTTP_H263_AMR_Video2() throws Exception {
        if (!MediaUtils.checkDecoder(MediaFormat.MIMETYPE_VIDEO_H263, MediaFormat.MIMETYPE_AUDIO_AMR_NB)) {
            return; // skip
        }

        String urlString = dynamicConfig.getValue(HTTP_H263_AMR_VIDEO_2_KEY);
        playVideoTest(urlString, 176, 144);
    }

    public void testHTTP_MPEG4SP_AAC_Video1() throws Exception {
        if (!MediaUtils.checkDecoder(MediaFormat.MIMETYPE_VIDEO_MPEG4)) {
            return; // skip
        }

        String urlString = dynamicConfig.getValue(HTTP_MPEG4_SP_AAC_VIDEO_1_KEY);
        playVideoTest(urlString, 176, 144);
    }

    public void testHTTP_MPEG4SP_AAC_Video2() throws Exception {
        if (!MediaUtils.checkDecoder(MediaFormat.MIMETYPE_VIDEO_MPEG4)) {
            return; // skip
        }

        String urlString = dynamicConfig.getValue(HTTP_MPEG4_SP_AAC_VIDEO_2_KEY);
        playVideoTest(urlString, 176, 144);
    }

    public void testHTTP_H264Base_AAC_Video1() throws Exception {
        if (!MediaUtils.checkDecoder(MediaFormat.MIMETYPE_VIDEO_AVC)) {
            return; // skip
        }

        String urlString = dynamicConfig.getValue(HTTP_H264_BASE_AAC_VIDEO_1_KEY);
        playVideoTest(urlString, 640, 360);
    }

    public void testHTTP_H264Base_AAC_Video2() throws Exception {
        if (!MediaUtils.checkDecoder(MediaFormat.MIMETYPE_VIDEO_AVC)) {
            return; // skip
        }

        String urlString = dynamicConfig.getValue(HTTP_H264_BASE_AAC_VIDEO_2_KEY);
        playVideoTest(urlString, 640, 360);
    }

    // Streaming HLS video from YouTube
    public void testHLS() throws Exception {
        if (!MediaUtils.checkDecoder(MediaFormat.MIMETYPE_VIDEO_AVC)) {
            return; // skip
        }

        // Play stream for 60 seconds
        playLiveVideoTest("http://www.youtube.com/api/manifest/hls_variant/id/"
                + "0168724d02bd9945/itag/5/source/youtube/playlist_type/DVR/ip/"
                + "0.0.0.0/ipbits/0/expire/19000000000/sparams/ip,ipbits,expire"
                + ",id,itag,source,playlist_type/signature/773AB8ACC68A96E5AA48"
                + "1996AD6A1BBCB70DCB87.95733B544ACC5F01A1223A837D2CF04DF85A336"
                + "0/key/ik0/file/m3u8", 60 * 1000);
    }

    public void testHlsWithHeadersCookies() throws Exception {
        if (!MediaUtils.checkDecoder(MediaFormat.MIMETYPE_VIDEO_AVC)) {
            return; // skip
        }

        final Uri uri = Uri.parse(
                "http://www.youtube.com/api/manifest/hls_variant/id/"
                + "0168724d02bd9945/itag/5/source/youtube/playlist_type/DVR/ip/"
                + "0.0.0.0/ipbits/0/expire/19000000000/sparams/ip,ipbits,expire"
                + ",id,itag,source,playlist_type/signature/773AB8ACC68A96E5AA48"
                + "1996AD6A1BBCB70DCB87.95733B544ACC5F01A1223A837D2CF04DF85A336"
                + "0/key/ik0/file/m3u8");

        // TODO: dummy values for headers/cookies till we find a server that actually needs them
        HashMap<String, String> headers = new HashMap<>();
        headers.put("header0", "value0");
        headers.put("header1", "value1");

        String cookieName = "auth_1234567";
        String cookieValue = "0123456789ABCDEF0123456789ABCDEF";
        HttpCookie cookie = new HttpCookie(cookieName, cookieValue);
        cookie.setHttpOnly(true);
        cookie.setDomain("www.youtube.com");
        cookie.setPath("/");        // all paths
        cookie.setSecure(false);
        cookie.setDiscard(false);
        cookie.setMaxAge(24 * 3600);  // 24hrs

        java.util.Vector<HttpCookie> cookies = new java.util.Vector<HttpCookie>();
        cookies.add(cookie);

        // Play stream for 60 seconds
        playLiveVideoTest(uri, headers, cookies, 60 * 1000);
    }

    public void testHlsSampleAes_bbb_audio_only_overridable() throws Exception {
        if (!MediaUtils.checkDecoder(MediaFormat.MIMETYPE_VIDEO_AVC)) {
            return; // skip
        }

        String defaultUrl = "http://storage.googleapis.com/wvmedia/cenc/hls/sample_aes/" +
                            "bbb_1080p_30fps_11min/audio_only/prog_index.m3u8";

        // if url override provided
        String testUrl = (mInputUrl != null) ? mInputUrl : defaultUrl;

        // Play stream for 60 seconds
        playLiveAudioOnlyTest(
                testUrl,
                60 * 1000);
    }

    public void testHlsSampleAes_bbb_unmuxed_1500k() throws Exception {
        if (!MediaUtils.checkDecoder(MediaFormat.MIMETYPE_VIDEO_AVC)) {
            return; // skip
        }

        // Play stream for 60 seconds
        playLiveVideoTest(
                "http://storage.googleapis.com/wvmedia/cenc/hls/sample_aes/" +
                "bbb_1080p_30fps_11min/unmuxed_1500k/prog_index.m3u8",
                60 * 1000);
    }


    // Streaming audio from local HTTP server
    public void testPlayMp3Stream1() throws Throwable {
        localHttpAudioStreamTest("ringer.mp3", false, false);
    }
    public void testPlayMp3Stream2() throws Throwable {
        localHttpAudioStreamTest("ringer.mp3", false, false);
    }
    public void testPlayMp3StreamRedirect() throws Throwable {
        localHttpAudioStreamTest("ringer.mp3", true, false);
    }
    public void testPlayMp3StreamNoLength() throws Throwable {
        localHttpAudioStreamTest("noiseandchirps.mp3", false, true);
    }
    public void testPlayOggStream() throws Throwable {
        localHttpAudioStreamTest("noiseandchirps.ogg", false, false);
    }
    public void testPlayOggStreamRedirect() throws Throwable {
        localHttpAudioStreamTest("noiseandchirps.ogg", true, false);
    }
    public void testPlayOggStreamNoLength() throws Throwable {
        localHttpAudioStreamTest("noiseandchirps.ogg", false, true);
    }
    public void testPlayMp3Stream1Ssl() throws Throwable {
        localHttpsAudioStreamTest("ringer.mp3", false, false);
    }

    private void localHttpAudioStreamTest(final String name, boolean redirect, boolean nolength)
            throws Throwable {
        mServer = new CtsTestServer(mContext);
        try {
            String stream_url = null;
            if (redirect) {
                // Stagefright doesn't have a limit, but we can't test support of infinite redirects
                // Up to 4 redirects seems reasonable though.
                stream_url = mServer.getRedirectingAssetUrl(name, 4);
            } else {
                stream_url = mServer.getAssetUrl(name);
            }
            if (nolength) {
                stream_url = stream_url + "?" + CtsTestServer.NOLENGTH_POSTFIX;
            }

            if (!MediaUtils.checkCodecsForPath(mContext, stream_url)) {
                return; // skip
            }

            mMediaPlayer.setDataSource(stream_url);

            mMediaPlayer.setDisplay(getActivity().getSurfaceHolder());
            mMediaPlayer.setScreenOnWhilePlaying(true);

            mOnBufferingUpdateCalled.reset();
            mMediaPlayer.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener() {
                @Override
                public void onBufferingUpdate(MediaPlayer mp, int percent) {
                    mOnBufferingUpdateCalled.signal();
                }
            });
            mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    fail("Media player had error " + what + " playing " + name);
                    return true;
                }
            });

            assertFalse(mOnBufferingUpdateCalled.isSignalled());
            mMediaPlayer.prepare();

            if (nolength) {
                mMediaPlayer.start();
                Thread.sleep(LONG_SLEEP_TIME);
                assertFalse(mMediaPlayer.isPlaying());
            } else {
                mOnBufferingUpdateCalled.waitForSignal();
                mMediaPlayer.start();
                Thread.sleep(SLEEP_TIME);
            }
            mMediaPlayer.stop();
            mMediaPlayer.reset();
        } finally {
            mServer.shutdown();
        }
    }
    private void localHttpsAudioStreamTest(final String name, boolean redirect, boolean nolength)
            throws Throwable {
        mServer = new CtsTestServer(mContext, true);
        try {
            String stream_url = null;
            if (redirect) {
                // Stagefright doesn't have a limit, but we can't test support of infinite redirects
                // Up to 4 redirects seems reasonable though.
                stream_url = mServer.getRedirectingAssetUrl(name, 4);
            } else {
                stream_url = mServer.getAssetUrl(name);
            }
            if (nolength) {
                stream_url = stream_url + "?" + CtsTestServer.NOLENGTH_POSTFIX;
            }

            mMediaPlayer.setDataSource(stream_url);

            mMediaPlayer.setDisplay(getActivity().getSurfaceHolder());
            mMediaPlayer.setScreenOnWhilePlaying(true);

            mOnBufferingUpdateCalled.reset();
            mMediaPlayer.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener() {
                @Override
                public void onBufferingUpdate(MediaPlayer mp, int percent) {
                    mOnBufferingUpdateCalled.signal();
                }
            });
            mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    fail("Media player had error " + what + " playing " + name);
                    return true;
                }
            });

            assertFalse(mOnBufferingUpdateCalled.isSignalled());
            try {
                mMediaPlayer.prepare();
            } catch (Exception ex) {
                return;
            }
            fail("https playback should have failed");
        } finally {
            mServer.shutdown();
        }
    }

    // TODO: unhide this test when we sort out how to expose buffering control API.
    private void doTestBuffering() throws Throwable {
        final String name = "ringer.mp3";
        mServer = new CtsTestServer(mContext);
        try {
            String stream_url = mServer.getAssetUrl(name);

            if (!MediaUtils.checkCodecsForPath(mContext, stream_url)) {
                Log.w(TAG, "can not find stream " + stream_url + ", skipping test");
                return; // skip
            }

            // getDefaultBufferingParams should be called after setDataSource.
            try {
                BufferingParams params = mMediaPlayer.getDefaultBufferingParams();
                fail("MediaPlayer failed to check state for getDefaultBufferingParams");
            } catch (IllegalStateException e) {
                // expected
            }

            mMediaPlayer.setDataSource(stream_url);

            mMediaPlayer.setDisplay(getActivity().getSurfaceHolder());
            mMediaPlayer.setScreenOnWhilePlaying(true);

            mOnBufferingUpdateCalled.reset();
            mMediaPlayer.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener() {
                @Override
                public void onBufferingUpdate(MediaPlayer mp, int percent) {
                    mOnBufferingUpdateCalled.signal();
                }
            });
            mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    fail("Media player had error " + what + " playing " + name);
                    return true;
                }
            });

            assertFalse(mOnBufferingUpdateCalled.isSignalled());

            BufferingParams params = mMediaPlayer.getDefaultBufferingParams();

            int newMark = -1;
            BufferingParams newParams = null;
            int initialBufferingMode = params.getInitialBufferingMode();
            if (initialBufferingMode == BufferingParams.BUFFERING_MODE_SIZE_ONLY
                    || initialBufferingMode == BufferingParams.BUFFERING_MODE_TIME_THEN_SIZE) {
                newMark = params.getInitialBufferingWatermarkKB() + 1;
                newParams = new BufferingParams.Builder(params).setInitialBufferingWatermarkKB(
                        newMark).build();
            } else if (initialBufferingMode == BufferingParams.BUFFERING_MODE_TIME_ONLY) {
                newMark = params.getInitialBufferingWatermarkMs() + 1;
                newParams = new BufferingParams.Builder(params).setInitialBufferingWatermarkMs(
                        newMark).build();
            } else {
                newParams = params;
            }
            mMediaPlayer.setBufferingParams(newParams);

            int checkMark = -1;
            BufferingParams checkParams = mMediaPlayer.getBufferingParams();
            if (initialBufferingMode == BufferingParams.BUFFERING_MODE_SIZE_ONLY
                    || initialBufferingMode == BufferingParams.BUFFERING_MODE_TIME_THEN_SIZE) {
                checkMark = checkParams.getInitialBufferingWatermarkKB();
            } else if (initialBufferingMode == BufferingParams.BUFFERING_MODE_TIME_ONLY) {
                checkMark = checkParams.getInitialBufferingWatermarkMs();
            }
            assertEquals("marks do not match", newMark, checkMark);

            // TODO: add more dynamic checking, e.g., buffering shall not exceed pre-set mark.

            mMediaPlayer.reset();
        } finally {
            mServer.shutdown();
        }
    }

    public void testPlayHlsStream() throws Throwable {
        if (!MediaUtils.checkDecoder(MediaFormat.MIMETYPE_VIDEO_AVC)) {
            return; // skip
        }
        localHlsTest("hls.m3u8", false, false);
    }

    public void testPlayHlsStreamWithQueryString() throws Throwable {
        if (!MediaUtils.checkDecoder(MediaFormat.MIMETYPE_VIDEO_AVC)) {
            return; // skip
        }
        localHlsTest("hls.m3u8", true, false);
    }

    public void testPlayHlsStreamWithRedirect() throws Throwable {
        if (!MediaUtils.checkDecoder(MediaFormat.MIMETYPE_VIDEO_AVC)) {
            return; // skip
        }
        localHlsTest("hls.m3u8", false, true);
    }

    public void testPlayHlsStreamWithTimedId3() throws Throwable {
        if (!MediaUtils.checkDecoder(MediaFormat.MIMETYPE_VIDEO_AVC)) {
            Log.d(TAG, "Device doesn't have video codec, skipping test");
            return;
        }

        mServer = new CtsTestServer(mContext);
        try {
            // counter must be final if we want to access it inside onTimedMetaData;
            // use AtomicInteger so we can have a final counter object with mutable integer value.
            final AtomicInteger counter = new AtomicInteger();
            String stream_url = mServer.getAssetUrl("prog_index.m3u8");
            mMediaPlayer.setDataSource(stream_url);
            mMediaPlayer.setDisplay(getActivity().getSurfaceHolder());
            mMediaPlayer.setScreenOnWhilePlaying(true);
            mMediaPlayer.setWakeMode(mContext, PowerManager.PARTIAL_WAKE_LOCK);
            mMediaPlayer.setOnTimedMetaDataAvailableListener(new MediaPlayer.OnTimedMetaDataAvailableListener() {
                @Override
                public void onTimedMetaDataAvailable(MediaPlayer mp, TimedMetaData md) {
                    counter.incrementAndGet();
                    int pos = mp.getCurrentPosition();
                    long timeUs = md.getTimestamp();
                    byte[] rawData = md.getMetaData();
                    // Raw data contains an id3 tag holding the decimal string representation of
                    // the associated time stamp rounded to the closest half second.

                    int offset = 0;
                    offset += 3; // "ID3"
                    offset += 2; // version
                    offset += 1; // flags
                    offset += 4; // size
                    offset += 4; // "TXXX"
                    offset += 4; // frame size
                    offset += 2; // frame flags
                    offset += 1; // "\x03" : UTF-8 encoded Unicode
                    offset += 1; // "\x00" : null-terminated empty description

                    int length = rawData.length;
                    length -= offset;
                    length -= 1; // "\x00" : terminating null

                    String data = new String(rawData, offset, length);
                    int dataTimeUs = Integer.parseInt(data);
                    assertTrue("Timed ID3 timestamp does not match content",
                            Math.abs(dataTimeUs - timeUs) < 500000);
                    assertTrue("Timed ID3 arrives after timestamp", pos * 1000 < timeUs);
                }
            });

            final Object completion = new Object();
            mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                int run;
                @Override
                public void onCompletion(MediaPlayer mp) {
                    if (run++ == 0) {
                        mMediaPlayer.seekTo(0);
                        mMediaPlayer.start();
                    } else {
                        mMediaPlayer.stop();
                        synchronized (completion) {
                            completion.notify();
                        }
                    }
                }
            });

            mMediaPlayer.prepare();
            mMediaPlayer.start();
            assertTrue("MediaPlayer not playing", mMediaPlayer.isPlaying());

            int i = -1;
            TrackInfo[] trackInfos = mMediaPlayer.getTrackInfo();
            for (i = 0; i < trackInfos.length; i++) {
                TrackInfo trackInfo = trackInfos[i];
                if (trackInfo.getTrackType() == TrackInfo.MEDIA_TRACK_TYPE_METADATA) {
                    break;
                }
            }
            assertTrue("Stream has no timed ID3 track", i >= 0);
            mMediaPlayer.selectTrack(i);

            synchronized (completion) {
                completion.wait();
            }

            // There are a total of 19 metadata access units in the test stream; every one of them
            // should be received twice: once before the seek and once after.
            assertTrue("Incorrect number of timed ID3s recieved", counter.get() == 38);
        } finally {
            mServer.shutdown();
        }
    }

    private static class WorkerWithPlayer implements Runnable {
        private final Object mLock = new Object();
        private Looper mLooper;
        private MediaPlayer mMediaPlayer;

        /**
         * Creates a worker thread with the given name. The thread
         * then runs a {@link android.os.Looper}.
         * @param name A name for the new thread
         */
        WorkerWithPlayer(String name) {
            Thread t = new Thread(null, this, name);
            t.setPriority(Thread.MIN_PRIORITY);
            t.start();
            synchronized (mLock) {
                while (mLooper == null) {
                    try {
                        mLock.wait();
                    } catch (InterruptedException ex) {
                    }
                }
            }
        }

        public MediaPlayer getPlayer() {
            return mMediaPlayer;
        }

        @Override
        public void run() {
            synchronized (mLock) {
                Looper.prepare();
                mLooper = Looper.myLooper();
                mMediaPlayer = new MediaPlayer();
                mLock.notifyAll();
            }
            Looper.loop();
        }

        public void quit() {
            mLooper.quit();
            mMediaPlayer.release();
        }
    }

    public void testBlockingReadRelease() throws Throwable {

        mServer = new CtsTestServer(mContext);

        WorkerWithPlayer worker = new WorkerWithPlayer("player");
        final MediaPlayer mp = worker.getPlayer();

        try {
            String path = mServer.getDelayedAssetUrl("noiseandchirps.ogg", 15000);
            mp.setDataSource(path);
            mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    fail("prepare should not succeed");
                }
            });
            mp.prepareAsync();
            Thread.sleep(1000);
            long start = SystemClock.elapsedRealtime();
            mp.release();
            long end = SystemClock.elapsedRealtime();
            long releaseDuration = (end - start);
            assertTrue("release took too long: " + releaseDuration, releaseDuration < 1000);
        } catch (IllegalArgumentException e) {
            fail(e.getMessage());
        } catch (SecurityException e) {
            fail(e.getMessage());
        } catch (IllegalStateException e) {
            fail(e.getMessage());
        } catch (IOException e) {
            fail(e.getMessage());
        } catch (InterruptedException e) {
            fail(e.getMessage());
        } finally {
            mServer.shutdown();
        }

        // give the worker a bit of time to start processing the message before shutting it down
        Thread.sleep(5000);
        worker.quit();
    }

    private void localHlsTest(final String name, boolean appendQueryString, boolean redirect)
            throws Throwable {
        mServer = new CtsTestServer(mContext);
        try {
            String stream_url = null;
            if (redirect) {
                stream_url = mServer.getQueryRedirectingAssetUrl(name);
            } else {
                stream_url = mServer.getAssetUrl(name);
            }
            if (appendQueryString) {
                stream_url += "?foo=bar/baz";
            }

            playLiveVideoTest(stream_url, 10);
        } finally {
            mServer.shutdown();
        }
    }
}
