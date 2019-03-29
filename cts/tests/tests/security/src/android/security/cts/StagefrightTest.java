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
 *
 *
 * This code was provided to AOSP by Zimperium Inc and was
 * written by:
 *
 * Simone "evilsocket" Margaritelli
 * Joshua "jduck" Drake
 */
package android.security.cts;

import android.test.AndroidTestCase;
import android.util.Log;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.opengl.GLES20;
import android.opengl.GLES11Ext;
import android.os.Looper;
import android.os.SystemClock;
import android.platform.test.annotations.SecurityTest;
import android.test.InstrumentationTestCase;
import android.util.Log;
import android.view.Surface;
import android.webkit.cts.CtsTestServer;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import android.security.cts.R;


/**
 * Verify that the device is not vulnerable to any known Stagefright
 * vulnerabilities.
 */
@SecurityTest
public class StagefrightTest extends InstrumentationTestCase {
    static final String TAG = "StagefrightTest";

    private final long TIMEOUT_NS = 10000000000L;  // 10 seconds.

    public StagefrightTest() {
    }

    /***********************************************************
     to prevent merge conflicts, add K tests below this comment,
     before any existing test methods
     ***********************************************************/

    @SecurityTest
    public void testStagefright_bug_64710074() throws Exception {
        doStagefrightTest(R.raw.bug_64710074);
    }

    @SecurityTest
    public void testStagefright_cve_2017_0643() throws Exception {
        doStagefrightTest(R.raw.cve_2017_0643);
    }

    @SecurityTest
    public void testStagefright_cve_2017_0728() throws Exception {
        doStagefrightTest(R.raw.cve_2017_0728);
    }

    @SecurityTest
    public void testStagefright_bug_62187433() throws Exception {
        doStagefrightTest(R.raw.bug_62187433);
    }

    @SecurityTest
    public void testStagefrightANR_bug_62673844() throws Exception {
        doStagefrightTestANR(R.raw.bug_62673844);
    }

    @SecurityTest
    public void testStagefright_bug_37079296() throws Exception {
        doStagefrightTest(R.raw.bug_37079296);
    }

    @SecurityTest
    public void testStagefright_bug_38342499() throws Exception {
        doStagefrightTest(R.raw.bug_38342499);
    }

    @SecurityTest
    public void testStagefright_bug_23270724() throws Exception {
        doStagefrightTest(R.raw.bug_23270724_1);
        doStagefrightTest(R.raw.bug_23270724_2);
    }

    @SecurityTest
    public void testStagefright_bug_22771132() throws Exception {
        doStagefrightTest(R.raw.bug_22771132);
    }

    public void testStagefright_bug_21443020() throws Exception {
        doStagefrightTest(R.raw.bug_21443020_webm);
    }

    public void testStagefright_bug_34360591() throws Exception {
        doStagefrightTest(R.raw.bug_34360591);
    }

    public void testStagefright_bug_35763994() throws Exception {
        doStagefrightTest(R.raw.bug_35763994);
    }

    @SecurityTest
    public void testStagefright_bug_33137046() throws Exception {
        doStagefrightTest(R.raw.bug_33137046);
    }

    @SecurityTest
    public void testStagefright_cve_2016_2507() throws Exception {
        doStagefrightTest(R.raw.cve_2016_2507);
    }

    @SecurityTest
    public void testStagefright_bug_31647370() throws Exception {
        doStagefrightTest(R.raw.bug_31647370);
    }

    @SecurityTest
    public void testStagefright_bug_32577290() throws Exception {
        doStagefrightTest(R.raw.bug_32577290);
    }

    @SecurityTest
    public void testStagefright_cve_2015_1538_1() throws Exception {
        doStagefrightTest(R.raw.cve_2015_1538_1);
    }

    @SecurityTest
    public void testStagefright_cve_2015_1538_2() throws Exception {
        doStagefrightTest(R.raw.cve_2015_1538_2);
    }

    @SecurityTest
    public void testStagefright_cve_2015_1538_3() throws Exception {
        doStagefrightTest(R.raw.cve_2015_1538_3);
    }

    @SecurityTest
    public void testStagefright_cve_2015_1538_4() throws Exception {
        doStagefrightTest(R.raw.cve_2015_1538_4);
    }

    @SecurityTest
    public void testStagefright_cve_2015_1539() throws Exception {
        doStagefrightTest(R.raw.cve_2015_1539);
    }

    @SecurityTest
    public void testStagefright_cve_2015_3824() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3824);
    }

    @SecurityTest
    public void testStagefright_cve_2015_3826() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3826);
    }

    @SecurityTest
    public void testStagefright_cve_2015_3827() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3827);
    }

    @SecurityTest
    public void testStagefright_cve_2015_3828() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3828);
    }

    @SecurityTest
    public void testStagefright_cve_2015_3829() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3829);
    }

    @SecurityTest
    public void testStagefright_cve_2015_3836() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3836);
    }

    @SecurityTest
    public void testStagefright_cve_2015_3864() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3864);
    }

    @SecurityTest
    public void testStagefright_cve_2015_3864_b23034759() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3864_b23034759);
    }

    @SecurityTest
    public void testStagefright_cve_2015_6598() throws Exception {
        doStagefrightTest(R.raw.cve_2015_6598);
    }

    @SecurityTest
    public void testStagefright_bug_26366256() throws Exception {
        doStagefrightTest(R.raw.bug_26366256);
    }

    @SecurityTest
    public void testStagefright_cve_2016_2429_b_27211885() throws Exception {
        doStagefrightTest(R.raw.cve_2016_2429_b_27211885);
    }

    @SecurityTest
    public void testStagefright_bug_34031018() throws Exception {
        doStagefrightTest(R.raw.bug_34031018_32bit);
        doStagefrightTest(R.raw.bug_34031018_64bit);
    }

    /***********************************************************
     to prevent merge conflicts, add L tests below this comment,
     before any existing test methods
     ***********************************************************/

    @SecurityTest
    public void testStagefright_cve_2017_0852_b_62815506() throws Exception {
        doStagefrightTest(R.raw.cve_2017_0852_b_62815506);
    }

    /***********************************************************
     to prevent merge conflicts, add M tests below this comment,
     before any existing test methods
     ***********************************************************/

    @SecurityTest
    public void testStagefright_bug_65717533() throws Exception {
        doStagefrightTest(R.raw.bug_65717533_header_corrupt);
    }

    @SecurityTest
    public void testStagefright_cve_2017_0857() throws Exception {
        doStagefrightTest(R.raw.cve_2017_0857);
    }

    @SecurityTest
    public void testStagefright_cve_2017_0600() throws Exception {
        doStagefrightTest(R.raw.cve_2017_0600);
    }

    @SecurityTest
    public void testStagefright_cve_2017_0599() throws Exception {
        doStagefrightTest(R.raw.cve_2017_0599);
    }

    @SecurityTest
    public void testStagefright_cve_2016_0842() throws Exception {
        doStagefrightTest(R.raw.cve_2016_0842);
    }

    @SecurityTest
    public void testStagefright_cve_2016_6712() throws Exception {
        doStagefrightTest(R.raw.cve_2016_6712);
    }

    @SecurityTest
    public void testStagefright_bug_34097672() throws Exception {
        doStagefrightTest(R.raw.bug_34097672);
    }

    @SecurityTest
    public void testStagefright_bug_33818508() throws Exception {
        doStagefrightTest(R.raw.bug_33818508);
    }

    @SecurityTest
    public void testStagefright_bug_32873375() throws Exception {
        doStagefrightTest(R.raw.bug_32873375);
    }

    @SecurityTest
    public void testStagefright_bug_25765591() throws Exception {
        doStagefrightTest(R.raw.bug_25765591);
    }

    @SecurityTest
    public void testStagefright_cve_2015_3867() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3867);
    }

    @SecurityTest
    public void testStagefright_cve_2015_3869() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3869);
    }

    @SecurityTest
    public void testStagefright_bug_32322258() throws Exception {
        doStagefrightTest(R.raw.bug_32322258);
    }

    @SecurityTest
    public void testStagefright_cve_2015_3873_b_23248776() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3873_b_23248776);
    }

    @SecurityTest
    public void testStagefright_cve_2015_3873_b_20718524() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3873_b_20718524);
    }

    @SecurityTest
    public void testStagefright_cve_2015_3862_b_22954006() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3862_b_22954006);
    }

    @SecurityTest
    public void testStagefright_cve_2015_3867_b_23213430() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3867_b_23213430);
    }

    @SecurityTest
    public void testStagefright_cve_2015_3873_b_21814993() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3873_b_21814993);
    }

    @SecurityTest
    public void testStagefright_bug_25812590() throws Exception {
        doStagefrightTest(R.raw.bug_25812590);
    }

    public void testStagefright_cve_2015_6600() throws Exception {
        doStagefrightTest(R.raw.cve_2015_6600);
    }

    public void testStagefright_cve_2015_6603() throws Exception {
        doStagefrightTest(R.raw.cve_2015_6603);
    }

    public void testStagefright_cve_2015_6604() throws Exception {
        doStagefrightTest(R.raw.cve_2015_6604);
    }

    public void testStagefright_cve_2015_3871() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3871);
    }

    public void testStagefright_bug_26070014() throws Exception {
        doStagefrightTest(R.raw.bug_26070014);
    }

    public void testStagefright_bug_32915871() throws Exception {
        doStagefrightTest(R.raw.bug_32915871);
    }

    @SecurityTest
    public void testStagefright_bug_28333006() throws Exception {
        doStagefrightTest(R.raw.bug_28333006);
    }

    @SecurityTest
    public void testStagefright_bug_14388161() throws Exception {
        doStagefrightTestMediaPlayer(R.raw.bug_14388161);
    }

    @SecurityTest
    public void testStagefright_cve_2016_3755() throws Exception {
        doStagefrightTest(R.raw.cve_2016_3755);
    }

    @SecurityTest
    public void testStagefright_cve_2016_3878_b_29493002() throws Exception {
        doStagefrightTest(R.raw.cve_2016_3878_b_29493002);
    }

    @SecurityTest
    public void testStagefright_cve_2015_6608_b_23680780() throws Exception {
        doStagefrightTest(R.raw.cve_2015_6608_b_23680780);
    }

    @SecurityTest
    public void testStagefright_bug_27855419_CVE_2016_2463() throws Exception {
        doStagefrightTest(R.raw.bug_27855419);
    }

    public void testStagefright_bug_19779574() throws Exception {
        doStagefrightTest(R.raw.bug_19779574);
    }

    /***********************************************************
     to prevent merge conflicts, add N tests below this comment,
     before any existing test methods
     ***********************************************************/

    @SecurityTest
    public void testStagefright_bug_35467107() throws Exception {
        doStagefrightTest(R.raw.bug_35467107);
    }

    private void doStagefrightTest(final int rid) throws Exception {
        doStagefrightTestMediaPlayer(rid);
        doStagefrightTestMediaCodec(rid);
        doStagefrightTestMediaMetadataRetriever(rid);

        Context context = getInstrumentation().getContext();
        Resources resources =  context.getResources();
        CtsTestServer server = new CtsTestServer(context);
        String rname = resources.getResourceEntryName(rid);
        String url = server.getAssetUrl("raw/" + rname);
        doStagefrightTestMediaPlayer(url);
        doStagefrightTestMediaCodec(url);
        doStagefrightTestMediaMetadataRetriever(url);
        server.shutdown();
    }

    private void doStagefrightTestANR(final int rid) throws Exception {
        doStagefrightTestMediaPlayerANR(rid, null);
    }

    private Surface getDummySurface() {
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0]);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE);
        SurfaceTexture surfaceTex = new SurfaceTexture(textures[0]);
        surfaceTex.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
            @Override
            public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                Log.i(TAG, "new frame available");
            }
        });
        return new Surface(surfaceTex);
    }

    class MediaPlayerCrashListener
    implements MediaPlayer.OnErrorListener,
        MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener {
        @Override
        public boolean onError(MediaPlayer mp, int newWhat, int extra) {
            Log.i(TAG, "error: " + newWhat + "/" + extra);
            // don't overwrite a more severe error with a less severe one
            if (what != MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
                what = newWhat;
            }
            lock.lock();
            condition.signal();
            lock.unlock();

            return true; // don't call oncompletion
        }

        @Override
        public void onPrepared(MediaPlayer mp) {
            mp.start();
        }

        @Override
        public void onCompletion(MediaPlayer mp) {
            // preserve error condition, if any
            lock.lock();
            completed = true;
            condition.signal();
            lock.unlock();
        }

        public int waitForError() throws InterruptedException {
            lock.lock();
            if (condition.awaitNanos(TIMEOUT_NS) <= 0) {
                Log.d(TAG, "timed out on waiting for error");
            }
            lock.unlock();
            if (what != 0) {
                // Sometimes mediaserver signals a decoding error first, and *then* crashes
                // due to additional in-flight buffers being processed, so wait a little
                // and see if more errors show up.
                SystemClock.sleep(1000);
            }
            return what;
        }

        public boolean waitForErrorOrCompletion() throws InterruptedException {
            lock.lock();
            if (condition.awaitNanos(TIMEOUT_NS) <= 0) {
                Log.d(TAG, "timed out on waiting for error or completion");
            }
            lock.unlock();
            return (what != 0 && what != MediaPlayer.MEDIA_ERROR_SERVER_DIED) || completed;
        }

        ReentrantLock lock = new ReentrantLock();
        Condition condition = lock.newCondition();
        int what;
        boolean completed = false;
    }

    class LooperThread extends Thread {
        private Looper mLooper;

        LooperThread(Runnable runner) {
            super(runner);
        }

        @Override
        public void run() {
            Looper.prepare();
            mLooper = Looper.myLooper();
            super.run();
        }

        public void stopLooper() {
            mLooper.quitSafely();
        }
    }

    private void doStagefrightTestMediaPlayer(final int rid) throws Exception {
        doStagefrightTestMediaPlayer(rid, null);
    }

    private void doStagefrightTestMediaPlayer(final String url) throws Exception {
        doStagefrightTestMediaPlayer(-1, url);
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (RuntimeException rethrown) {
                throw rethrown;
            } catch (Exception ignored) {
            }
        }
    }

    private void doStagefrightTestMediaPlayer(final int rid, final String uri) throws Exception {

        String name = uri != null ? uri :
            getInstrumentation().getContext().getResources().getResourceEntryName(rid);
        Log.i(TAG, "start mediaplayer test for: " + name);

        final MediaPlayerCrashListener mpcl = new MediaPlayerCrashListener();

        LooperThread t = new LooperThread(new Runnable() {
            @Override
            public void run() {

                MediaPlayer mp = new MediaPlayer();
                mp.setOnErrorListener(mpcl);
                mp.setOnPreparedListener(mpcl);
                mp.setOnCompletionListener(mpcl);
                Surface surface = getDummySurface();
                mp.setSurface(surface);
                AssetFileDescriptor fd = null;
                try {
                    if (uri == null) {
                        fd = getInstrumentation().getContext().getResources()
                                .openRawResourceFd(rid);

                        mp.setDataSource(fd.getFileDescriptor(),
                                         fd.getStartOffset(),
                                         fd.getLength());

                    } else {
                        mp.setDataSource(uri);
                    }
                    mp.prepareAsync();
                } catch (Exception e) {
                } finally {
                    closeQuietly(fd);
                }

                Looper.loop();
                mp.release();
            }
        });

        t.start();
        String cve = name.replace("_", "-").toUpperCase();
        assertFalse("Device *IS* vulnerable to " + cve,
                    mpcl.waitForError() == MediaPlayer.MEDIA_ERROR_SERVER_DIED);
        t.stopLooper();
        t.join(); // wait for thread to exit so we're sure the player was released
    }

    private void doStagefrightTestMediaCodec(final int rid) throws Exception {
        doStagefrightTestMediaCodec(rid, null);
    }

    private void doStagefrightTestMediaCodec(final String url) throws Exception {
        doStagefrightTestMediaCodec(-1, url);
    }

    private void doStagefrightTestMediaCodec(final int rid, final String url) throws Exception {

        final MediaPlayerCrashListener mpcl = new MediaPlayerCrashListener();

        LooperThread thr = new LooperThread(new Runnable() {
            @Override
            public void run() {

                MediaPlayer mp = new MediaPlayer();
                mp.setOnErrorListener(mpcl);
                try {
                    AssetFileDescriptor fd = getInstrumentation().getContext().getResources()
                        .openRawResourceFd(R.raw.good);

                    // the onErrorListener won't receive MEDIA_ERROR_SERVER_DIED until
                    // setDataSource has been called
                    mp.setDataSource(fd.getFileDescriptor(),
                                     fd.getStartOffset(),
                                     fd.getLength());
                    fd.close();
                } catch (Exception e) {
                    // this is a known-good file, so no failure should occur
                    fail("setDataSource of known-good file failed");
                }

                synchronized(mpcl) {
                    mpcl.notify();
                }
                Looper.loop();
                mp.release();
            }
        });
        thr.start();
        // wait until the thread has initialized the MediaPlayer
        synchronized(mpcl) {
            mpcl.wait();
        }

        Resources resources =  getInstrumentation().getContext().getResources();
        MediaExtractor ex = new MediaExtractor();
        if (url == null) {
            AssetFileDescriptor fd = resources.openRawResourceFd(rid);
            try {
                ex.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            } catch (IOException e) {
                // ignore
            } finally {
                closeQuietly(fd);
            }
        } else {
            try {
                ex.setDataSource(url);
            } catch (Exception e) {
                // indicative of problems with our tame CTS test web server
            }
        }
        int numtracks = ex.getTrackCount();
        String rname = url != null ? url: resources.getResourceEntryName(rid);
        Log.i(TAG, "start mediacodec test for: " + rname + ", which has " + numtracks + " tracks");
        for (int t = 0; t < numtracks; t++) {
            // find all the available decoders for this format
            ArrayList<String> matchingCodecs = new ArrayList<String>();
            MediaFormat format = null;
            try {
                format = ex.getTrackFormat(t);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "could not get track format for track " + t);
                continue;
            }
            String mime = format.getString(MediaFormat.KEY_MIME);
            int numCodecs = MediaCodecList.getCodecCount();
            for (int i = 0; i < numCodecs; i++) {
                MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
                if (info.isEncoder()) {
                    continue;
                }
                try {
                    MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType(mime);
                    if (caps != null) {
                        matchingCodecs.add(info.getName());
                        Log.i(TAG, "Found matching codec " + info.getName() + " for track " + t);
                    }
                } catch (IllegalArgumentException e) {
                    // type is not supported
                }
            }

            if (matchingCodecs.size() == 0) {
                Log.w(TAG, "no codecs for track " + t + ", type " + mime);
            }
            // decode this track once with each matching codec
            try {
                ex.selectTrack(t);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "couldn't select track " + t);
                // continue on with codec initialization anyway, since that might still crash
            }
            for (String codecName: matchingCodecs) {
                Log.i(TAG, "Decoding track " + t + " using codec " + codecName);
                ex.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                MediaCodec codec = MediaCodec.createByCodecName(codecName);
                Surface surface = null;
                if (mime.startsWith("video/")) {
                    surface = getDummySurface();
                }
                try {
                    codec.configure(format, surface, null, 0);
                    codec.start();
                } catch (Exception e) {
                    Log.i(TAG, "Failed to start/configure:", e);
                }
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                try {
                    ByteBuffer [] inputBuffers = codec.getInputBuffers();
                    while (true) {
                        int flags = ex.getSampleFlags();
                        long time = ex.getSampleTime();
                        ex.getCachedDuration();
                        int bufidx = codec.dequeueInputBuffer(5000);
                        if (bufidx >= 0) {
                            int n = ex.readSampleData(inputBuffers[bufidx], 0);
                            if (n < 0) {
                                flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                                time = 0;
                                n = 0;
                            }
                            codec.queueInputBuffer(bufidx, 0, n, time, flags);
                            ex.advance();
                        }
                        int status = codec.dequeueOutputBuffer(info, 5000);
                        if (status >= 0) {
                            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                break;
                            }
                            if (info.presentationTimeUs > TIMEOUT_NS / 1000) {
                                Log.d(TAG, "stopping after 10 seconds worth of data");
                                break;
                            }
                            codec.releaseOutputBuffer(status, true);
                        }
                    }
                } catch (Exception e) {
                    // local exceptions ignored, not security issues
                } finally {
                    codec.release();
                }
            }
            ex.unselectTrack(t);
        }
        ex.release();
        String cve = rname.replace("_", "-").toUpperCase();
        assertFalse("Device *IS* vulnerable to " + cve,
                    mpcl.waitForError() == MediaPlayer.MEDIA_ERROR_SERVER_DIED);
        thr.stopLooper();
        thr.join();
    }

    private void doStagefrightTestMediaMetadataRetriever(final int rid) throws Exception {
        doStagefrightTestMediaMetadataRetriever(rid, null);
    }

    private void doStagefrightTestMediaMetadataRetriever(final String url) throws Exception {
        doStagefrightTestMediaMetadataRetriever(-1, url);
    }

    private void doStagefrightTestMediaMetadataRetriever(
            final int rid, final String url) throws Exception {

        final MediaPlayerCrashListener mpcl = new MediaPlayerCrashListener();

        LooperThread thr = new LooperThread(new Runnable() {
            @Override
            public void run() {

                MediaPlayer mp = new MediaPlayer();
                mp.setOnErrorListener(mpcl);
                AssetFileDescriptor fd = null;
                try {
                    fd = getInstrumentation().getContext().getResources()
                        .openRawResourceFd(R.raw.good);

                    // the onErrorListener won't receive MEDIA_ERROR_SERVER_DIED until
                    // setDataSource has been called
                    mp.setDataSource(fd.getFileDescriptor(),
                                     fd.getStartOffset(),
                                     fd.getLength());
                    fd.close();
                } catch (Exception e) {
                    // this is a known-good file, so no failure should occur
                    fail("setDataSource of known-good file failed");
                }

                synchronized(mpcl) {
                    mpcl.notify();
                }
                Looper.loop();
                mp.release();
            }
        });
        thr.start();
        // wait until the thread has initialized the MediaPlayer
        synchronized(mpcl) {
            mpcl.wait();
        }

        Resources resources =  getInstrumentation().getContext().getResources();
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        if (url == null) {
            AssetFileDescriptor fd = resources.openRawResourceFd(rid);
            try {
                retriever.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            } catch (Exception e) {
                // ignore
            } finally {
                closeQuietly(fd);
            }
        } else {
            try {
                retriever.setDataSource(url, new HashMap<String, String>());
            } catch (Exception e) {
                // indicative of problems with our tame CTS test web server
            }
        }
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        retriever.getEmbeddedPicture();
        retriever.getFrameAtTime();

        retriever.release();
        String rname = url != null ? url : resources.getResourceEntryName(rid);
        String cve = rname.replace("_", "-").toUpperCase();
        assertFalse("Device *IS* vulnerable to " + cve,
                    mpcl.waitForError() == MediaPlayer.MEDIA_ERROR_SERVER_DIED);
        thr.stopLooper();
        thr.join();
    }

    public void testBug36215950() throws Exception {
        doStagefrightTestRawBlob(R.raw.bug_36215950, "video/hevc", 320, 240);
    }

    public void testBug36816007() throws Exception {
        doStagefrightTestRawBlob(R.raw.bug_36816007, "video/avc", 320, 240);
    }

    public void testBug36895511() throws Exception {
        doStagefrightTestRawBlob(R.raw.bug_36895511, "video/hevc", 320, 240);
    }

    public void testBug64836894() throws Exception {
        doStagefrightTestRawBlob(R.raw.bug_64836894, "video/avc", 320, 240);
    }

    @SecurityTest
    public void testCve_2017_0762() throws Exception {
        doStagefrightTestRawBlob(R.raw.cve_2017_0762, "video/hevc", 320, 240);
    }

    @SecurityTest
    public void testCve_2017_0687() throws Exception {
        doStagefrightTestRawBlob(R.raw.cve_2017_0687, "video/avc", 320, 240);
    }

    @SecurityTest
    public void testBug_37930177() throws Exception {
        doStagefrightTestRawBlob(R.raw.bug_37930177_hevc, "video/hevc", 320, 240);
    }

    private void runWithTimeout(Runnable runner, int timeout) {
        Thread t = new Thread(runner);
        t.start();
        try {
            t.join(timeout);
        } catch (InterruptedException e) {
            fail("operation was interrupted");
        }
        if (t.isAlive()) {
            fail("operation not completed within timeout of " + timeout + "ms");
        }
    }

    private void releaseCodec(final MediaCodec codec) {
        runWithTimeout(new Runnable() {
            @Override
            public void run() {
                codec.release();
            }
        }, 5000);
    }

    private void doStagefrightTestRawBlob(int rid, String mime, int initWidth, int initHeight) throws Exception {

        final MediaPlayerCrashListener mpcl = new MediaPlayerCrashListener();
        final Context context = getInstrumentation().getContext();
        final Resources resources =  context.getResources();

        LooperThread thr = new LooperThread(new Runnable() {
            @Override
            public void run() {

                MediaPlayer mp = new MediaPlayer();
                mp.setOnErrorListener(mpcl);
                AssetFileDescriptor fd = null;
                try {
                    fd = resources.openRawResourceFd(R.raw.good);

                    // the onErrorListener won't receive MEDIA_ERROR_SERVER_DIED until
                    // setDataSource has been called
                    mp.setDataSource(fd.getFileDescriptor(),
                                     fd.getStartOffset(),
                                     fd.getLength());
                    fd.close();
                } catch (Exception e) {
                    // this is a known-good file, so no failure should occur
                    fail("setDataSource of known-good file failed");
                }

                synchronized(mpcl) {
                    mpcl.notify();
                }
                Looper.loop();
                mp.release();
            }
        });
        thr.start();
        // wait until the thread has initialized the MediaPlayer
        synchronized(mpcl) {
            mpcl.wait();
        }

        AssetFileDescriptor fd = resources.openRawResourceFd(rid);
        byte [] blob = new byte[(int)fd.getLength()];
        FileInputStream fis = fd.createInputStream();
        int numRead = fis.read(blob);
        fis.close();
        //Log.i("@@@@", "read " + numRead + " bytes");

        // find all the available decoders for this format
        ArrayList<String> matchingCodecs = new ArrayList<String>();
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
            if (info.isEncoder()) {
                continue;
            }
            try {
                MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType(mime);
                if (caps != null) {
                    matchingCodecs.add(info.getName());
                }
            } catch (IllegalArgumentException e) {
                // type is not supported
            }
        }

        if (matchingCodecs.size() == 0) {
            Log.w(TAG, "no codecs for mime type " + mime);
        }
        String rname = resources.getResourceEntryName(rid);
        // decode this blob once with each matching codec
        for (String codecName: matchingCodecs) {
            Log.i(TAG, "Decoding blob " + rname + " using codec " + codecName);
            MediaCodec codec = MediaCodec.createByCodecName(codecName);
            MediaFormat format = MediaFormat.createVideoFormat(mime, initWidth, initHeight);
            codec.configure(format, null, null, 0);
            codec.start();

            try {
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                ByteBuffer [] inputBuffers = codec.getInputBuffers();
                // enqueue the bad data a number of times, in case
                // the codec needs multiple buffers to fail.
                for(int i = 0; i < 64; i++) {
                    int bufidx = codec.dequeueInputBuffer(5000);
                    if (bufidx >= 0) {
                        Log.i(TAG, "got input buffer of size " + inputBuffers[bufidx].capacity());
                        inputBuffers[bufidx].rewind();
                        inputBuffers[bufidx].put(blob, 0, numRead);
                        codec.queueInputBuffer(bufidx, 0, numRead, 0, 0);
                    } else {
                        Log.i(TAG, "no input buffer");
                    }
                    bufidx = codec.dequeueOutputBuffer(info, 5000);
                    if (bufidx >= 0) {
                        Log.i(TAG, "got output buffer");
                        codec.releaseOutputBuffer(bufidx, false);
                    } else {
                        Log.i(TAG, "no output buffer");
                    }
                }
            } catch (Exception e) {
                // ignore, not a security issue
            } finally {
                releaseCodec(codec);
            }
        }

        String cve = rname.replace("_", "-").toUpperCase();
        assertFalse("Device *IS* vulnerable to " + cve,
                    mpcl.waitForError() == MediaPlayer.MEDIA_ERROR_SERVER_DIED);
        thr.stopLooper();
        thr.join();
    }

    private void doStagefrightTestMediaPlayerANR(final int rid, final String uri) throws Exception {
        String name = uri != null ? uri :
            getInstrumentation().getContext().getResources().getResourceEntryName(rid);
        Log.i(TAG, "start mediaplayerANR test for: " + name);

        final MediaPlayerCrashListener mpl = new MediaPlayerCrashListener();

        LooperThread t = new LooperThread(new Runnable() {
            @Override
            public void run() {
                MediaPlayer mp = new MediaPlayer();
                mp.setOnErrorListener(mpl);
                mp.setOnPreparedListener(mpl);
                mp.setOnCompletionListener(mpl);
                Surface surface = getDummySurface();
                mp.setSurface(surface);
                AssetFileDescriptor fd = null;
                try {
                    if (uri == null) {
                        fd = getInstrumentation().getContext().getResources()
                                .openRawResourceFd(rid);

                        mp.setDataSource(fd.getFileDescriptor(),
                                fd.getStartOffset(),
                                fd.getLength());
                    } else {
                        mp.setDataSource(uri);
                    }
                    mp.prepareAsync();
                } catch (Exception e) {
                } finally {
                    closeQuietly(fd);
                }

                Looper.loop();
                mp.release();
            }
        });

        t.start();
        String cve = name.replace("_", "-").toUpperCase();
        assertTrue("Device *IS* vulnerable to " + cve, mpl.waitForErrorOrCompletion());
        t.stopLooper();
        t.join(); // wait for thread to exit so we're sure the player was released
    }
}
