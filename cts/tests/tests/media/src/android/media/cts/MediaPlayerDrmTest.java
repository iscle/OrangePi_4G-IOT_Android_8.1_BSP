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
import android.media.MediaRecorder;
import android.media.MediaTimestamp;
import android.media.PlaybackParams;
import android.media.SubtitleData;
import android.media.SyncParams;
import android.media.TimedText;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.Visualizer;
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.ServiceManager;
import android.os.SystemClock;
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
import java.util.concurrent.CountDownLatch;

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
public class MediaPlayerDrmTest extends MediaPlayerDrmTestBase {

    private static final String LOG_TAG = "MediaPlayerDrmTest";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }


    //////////////////////////////////////////////////////////////////////////////////////////////
    // Asset helpers

    private static Uri getUriFromFile(String path) {
        return Uri.fromFile(new File(getDownloadedPath(path)));
    }

    private static String getDownloadedPath(String fileName) {
        return getDownloadedFolder() + File.separator + fileName;
    }

    private static String getDownloadedFolder() {
        return Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS).getPath();
    }

    private static final class Resolution {
        public final boolean isHD;
        public final int width;
        public final int height;

        public Resolution(boolean isHD, int width, int height) {
            this.isHD = isHD;
            this.width = width;
            this.height = height;
        }
    }

    private static final Resolution RES_720P  = new Resolution(true, 1280,  720);
    private static final Resolution RES_AUDIO = new Resolution(false,   0,    0);


    // Assets

    private static final Uri CENC_AUDIO_URL = Uri.parse(
            "http://storage.googleapis.com/wvmedia/cenc/clearkey/car_cenc-20120827-8c-pssh.mp4");
    private static final Uri CENC_AUDIO_URL_DOWNLOADED = getUriFromFile("car_cenc-20120827-8c.mp4");

    private static final Uri CENC_VIDEO_URL = Uri.parse(
            "http://storage.googleapis.com/wvmedia/cenc/clearkey/car_cenc-20120827-88-pssh.mp4");
    private static final Uri CENC_VIDEO_URL_DOWNLOADED = getUriFromFile("car_cenc-20120827-88.mp4");


    // Tests

    @SmallTest
    @RequiresDevice
    public void testCAR_CLEARKEY_AUDIO_DOWNLOADED_V0_SYNC() throws Exception {
        download(CENC_AUDIO_URL,
                CENC_AUDIO_URL_DOWNLOADED,
                RES_AUDIO,
                ModularDrmTestType.V0_SYNC_TEST);
    }

    @SmallTest
    @RequiresDevice
    public void testCAR_CLEARKEY_AUDIO_DOWNLOADED_V1_ASYNC() throws Exception {
        download(CENC_AUDIO_URL,
                CENC_AUDIO_URL_DOWNLOADED,
                RES_AUDIO,
                ModularDrmTestType.V1_ASYNC_TEST);
    }

    @SmallTest
    @RequiresDevice
    public void testCAR_CLEARKEY_AUDIO_DOWNLOADED_V2_SYNC_CONFIG() throws Exception {
        download(CENC_AUDIO_URL,
                CENC_AUDIO_URL_DOWNLOADED,
                RES_AUDIO,
                ModularDrmTestType.V2_SYNC_CONFIG_TEST);
    }

    @SmallTest
    @RequiresDevice
    public void testCAR_CLEARKEY_AUDIO_DOWNLOADED_V3_ASYNC_DRMPREPARED() throws Exception {
        download(CENC_AUDIO_URL,
                CENC_AUDIO_URL_DOWNLOADED,
                RES_AUDIO,
                ModularDrmTestType.V3_ASYNC_DRMPREPARED_TEST);
    }

    @SmallTest
    @RequiresDevice
    public void testCAR_CLEARKEY_AUDIO_DOWNLOADED_V5_ASYNC_WITH_HANDLER() throws Exception {
        download(CENC_AUDIO_URL,
                CENC_AUDIO_URL_DOWNLOADED,
                RES_AUDIO,
                ModularDrmTestType.V5_ASYNC_DRMPREPARED_TEST_WITH_HANDLER);
    }

    // helpers

    private void stream(Uri uri, Resolution res, ModularDrmTestType testType) throws Exception {
        playModularDrmVideo(uri, res.width, res.height, testType);
    }

    private void download(Uri remote, Uri local, Resolution res, ModularDrmTestType testType)
            throws Exception {
        playModularDrmVideoDownload(remote, local, res.width, res.height, testType);
    }

}
