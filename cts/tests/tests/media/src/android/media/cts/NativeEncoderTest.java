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

import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.Surface;
import android.webkit.cts.CtsTestServer;

import com.android.compatibility.common.util.MediaUtils;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class NativeEncoderTest extends MediaPlayerTestBase {
    private static final String TAG = "NativeEncoderTest";
    private static Resources mResources;

    private static final String MIME_AVC = "video/avc";
    private static final String MIME_HEVC = "video/hevc";
    private static final String MIME_VP8 = "video/x-vnd.on2.vp8";

    private static int mResourceVideo720p;
    private static int mResourceVideo360p;

    static {
        System.loadLibrary("ctsmediacodec_jni");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mResources = mContext.getResources();

        mResourceVideo720p =
                R.raw.bbb_s4_1280x720_webm_vp8_8mbps_30fps_opus_mono_64kbps_48000hz;
        mResourceVideo360p =
                R.raw.bbb_s1_640x360_webm_vp8_2mbps_30fps_vorbis_5ch_320kbps_48000hz;
    }


    private boolean testEncode(int res, String mime, int width, int height) {
        AssetFileDescriptor fd = mResources.openRawResourceFd(res);

        return testEncodeSurfaceNative(
            fd.getParcelFileDescriptor().getFd(), fd.getStartOffset(), fd.getLength(),
            mime, width, height);
    }
    private static native boolean testEncodeSurfaceNative(int fd, long offset, long size,
            String mime, int width, int height);

    public void testEncodeSurfaceH264720p() throws Exception {
        boolean status = testEncode(mResourceVideo720p, MIME_AVC, 1280, 720);
        assertTrue("native encode error", status);
    }
    public void testEncodeSurfaceVp8720p() throws Exception {
        boolean status = testEncode(mResourceVideo720p, MIME_VP8, 1280, 720);
        assertTrue("native encode error", status);
    }
    public void testEncodeSurfaceHevc720p() throws Exception {
        boolean status = testEncode(mResourceVideo720p, MIME_HEVC, 1280, 720);
        assertTrue("native encode error", status);
    }
    public void testEncodeSurfaceH264360p() throws Exception {
        boolean status = testEncode(mResourceVideo360p, MIME_AVC, 640, 360);
        assertTrue("native encode error", status);
    }
    public void testEncodeSurfaceVp8360p() throws Exception {
        boolean status = testEncode(mResourceVideo360p, MIME_VP8, 640, 360);
        assertTrue("native encode error", status);
    }
    public void testEncodeSurfaceHevc360p() throws Exception {
        boolean status = testEncode(mResourceVideo360p, MIME_HEVC, 640, 360);
        assertTrue("native encode error", status);
    }


    private boolean testEncodeDynamicSyncFrame(int res, String mime, int width, int height) {
        AssetFileDescriptor fd = mResources.openRawResourceFd(res);

        return testEncodeSurfaceDynamicSyncFrameNative(
            fd.getParcelFileDescriptor().getFd(), fd.getStartOffset(), fd.getLength(),
            mime, width, height);
    }
    private static native boolean testEncodeSurfaceDynamicSyncFrameNative(int fd, long offset, long size,
            String mime, int width, int height);

    public void testEncodeDynamicSyncFrameH264720p() throws Exception {
        boolean status = testEncodeDynamicSyncFrame(mResourceVideo720p, MIME_AVC, 1280, 720);
        assertTrue("native encode error", status);
    }
    public void testEncodeDynamicSyncFrameVp8720p() throws Exception {
        boolean status = testEncodeDynamicSyncFrame(mResourceVideo720p, MIME_VP8, 1280, 720);
        assertTrue("native encode error", status);
    }
    public void testEncodeDynamicSyncFrameHevc720p() throws Exception {
        boolean status = testEncodeDynamicSyncFrame(mResourceVideo720p, MIME_HEVC, 1280, 720);
        assertTrue("native encode error", status);
    }
    public void testEncodeDynamicSyncFrameH264360p() throws Exception {
        boolean status = testEncodeDynamicSyncFrame(mResourceVideo360p, MIME_AVC, 640,  360);
        assertTrue("native encode error", status);
    }
    public void testEncodeDynamicSyncFrameVp8360p() throws Exception {
        boolean status = testEncodeDynamicSyncFrame(mResourceVideo360p, MIME_VP8, 640, 360);
        assertTrue("native encode error", status);
    }
    public void testEncodeDynamicSyncFrameHevc360p() throws Exception {
        boolean status = testEncodeDynamicSyncFrame(mResourceVideo360p, MIME_HEVC, 640, 360);
        assertTrue("native encode error", status);
    }


    private boolean testEncodeDynamicBitrate(int res, String mime, int width, int height) {
        AssetFileDescriptor fd = mResources.openRawResourceFd(res);

        return testEncodeSurfaceDynamicBitrateNative(
            fd.getParcelFileDescriptor().getFd(), fd.getStartOffset(), fd.getLength(),
            mime, width, height);
    }
    private static native boolean testEncodeSurfaceDynamicBitrateNative(int fd, long offset, long size,
            String mime, int width, int height);

    public void testEncodeDynamicBitrateH264720p() throws Exception {
        boolean status = testEncodeDynamicBitrate(mResourceVideo720p, MIME_AVC, 1280, 720);
        assertTrue("native encode error", status);
    }
    public void testEncodeDynamicBitrateVp8720p() throws Exception {
        boolean status = testEncodeDynamicBitrate(mResourceVideo720p, MIME_VP8, 1280, 720);
        assertTrue("native encode error", status);
    }
    public void testEncodeDynamicBitrateHevc720p() throws Exception {
        boolean status = testEncodeDynamicBitrate(mResourceVideo720p, MIME_HEVC, 1280, 720);
        assertTrue("native encode error", status);
    }
    public void testEncodeDynamicBitrateH264360p() throws Exception {
        boolean status = testEncodeDynamicBitrate(mResourceVideo360p, MIME_AVC, 640, 360);
        assertTrue("native encode error", status);
    }
    public void testEncodeDynamicBitrateVp8360p() throws Exception {
        boolean status = testEncodeDynamicBitrate(mResourceVideo360p, MIME_VP8, 640, 360);
        assertTrue("native encode error", status);
    }
    public void testEncodeDynamicBitrateHevc360p() throws Exception {
        boolean status = testEncodeDynamicBitrate(mResourceVideo360p, MIME_HEVC, 640, 360);
        assertTrue("native encode error", status);
    }


    private boolean testEncodePersistentSurface(int res, String mime, int width, int height) {
        AssetFileDescriptor fd = mResources.openRawResourceFd(res);

        return testEncodePersistentSurfaceNative(
            fd.getParcelFileDescriptor().getFd(), fd.getStartOffset(), fd.getLength(),
            mime, width, height);
    }

    private static native boolean testEncodePersistentSurfaceNative(int fd, long offset, long size,
            String mime, int width, int height);

    public void testEncodePersistentSurface720p() throws Exception {
        boolean status = testEncodePersistentSurface(mResourceVideo720p, MIME_AVC, 1280, 720);
        assertTrue("native encode error", status);
    }
    public void testEncodePersistentSurface360p() throws Exception {
        boolean status = testEncodePersistentSurface(mResourceVideo360p, MIME_VP8, 640, 360);
        assertTrue("native encode error", status);
    }
}
