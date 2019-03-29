/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.compatibility.common.util.ApiLevelUtil;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.media.MediaCodecList;
import android.media.MediaDrm;
import android.media.MediaDrmException;
import android.media.MediaFormat;
import android.media.CamcorderProfile;
import android.net.Uri;
import android.os.Environment;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Base64;
import android.util.Log;
import android.view.SurfaceHolder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Vector;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Tests of MediaPlayer streaming capabilities.
 */
public class ClearKeySystemTest extends MediaPlayerTestBase {
    private static final String TAG = ClearKeySystemTest.class.getSimpleName();

    // Add additional keys here if the content has more keys.
    private static final byte[] CLEAR_KEY_CENC = {
            (byte)0x3f, (byte)0x0a, (byte)0x33, (byte)0xf3, (byte)0x40, (byte)0x98, (byte)0xb9, (byte)0xe2,
            (byte)0x2b, (byte)0xc0, (byte)0x78, (byte)0xe0, (byte)0xa1, (byte)0xb5, (byte)0xe8, (byte)0x54 };

    private static final byte[] CLEAR_KEY_WEBM = "_CLEAR_KEY_WEBM_".getBytes();

    private static final int CONNECTION_RETRIES = 10;
    private static final int SLEEP_TIME_MS = 1000;
    private static final int VIDEO_WIDTH_CENC = 1280;
    private static final int VIDEO_HEIGHT_CENC = 720;
    private static final int VIDEO_WIDTH_WEBM = 352;
    private static final int VIDEO_HEIGHT_WEBM = 288;
    private static final int VIDEO_WIDTH_MPEG2TS = 320;
    private static final int VIDEO_HEIGHT_MPEG2TS = 240;
    private static final long PLAY_TIME_MS = TimeUnit.MILLISECONDS.convert(25, TimeUnit.SECONDS);
    private static final String MIME_VIDEO_AVC = MediaFormat.MIMETYPE_VIDEO_AVC;
    private static final String MIME_VIDEO_VP8 = MediaFormat.MIMETYPE_VIDEO_VP8;

    private static final Uri CENC_AUDIO_URL = Uri.parse(
            "https://storage.googleapis.com/wvmedia/clear/h264/llama/llama_aac_audio.mp4");
    private static final Uri CENC_VIDEO_URL = Uri.parse(
            "https://storage.googleapis.com/wvmedia/clearkey/llama_h264_main_720p_8000.mp4");
    private static final Uri WEBM_URL = Uri.parse(
            "android.resource://android.media.cts/" + R.raw.video_320x240_webm_vp8_800kbps_30fps_vorbis_stereo_128kbps_44100hz_crypt);
    private static final Uri MPEG2TS_SCRAMBLED_URL = Uri.parse(
            "android.resource://android.media.cts/" + R.raw.segment000001_scrambled);
    private static final Uri MPEG2TS_CLEAR_URL = Uri.parse(
            "android.resource://android.media.cts/" + R.raw.segment000001);

    private static final UUID COMMON_PSSH_SCHEME_UUID =
            new UUID(0x1077efecc0b24d02L, 0xace33c1e52e2fb4bL);
    private static final UUID CLEARKEY_SCHEME_UUID =
            new UUID(0xe2719d58a985b3c9L, 0x781ab030af78d30eL);

    private byte[] mDrmInitData;
    private byte[] mSessionId;
    private Looper mLooper;
    private MediaCodecClearKeyPlayer mMediaCodecPlayer;
    private MediaDrm mDrm;
    private Object mLock = new Object();
    private SurfaceHolder mSurfaceHolder;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (false == deviceHasMediaDrm()) {
            tearDown();
        }
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    private boolean deviceHasMediaDrm() {
        // ClearKey is introduced after KitKat.
        if (ApiLevelUtil.isAtMost(android.os.Build.VERSION_CODES.KITKAT)) {
            Log.i(TAG, "This test is designed to work after Android KitKat.");
            return false;
        }
        return true;
    }

    /**
     * Extracts key ids from the pssh blob returned by getKeyRequest() and
     * places it in keyIds.
     * keyRequestBlob format (section 5.1.3.1):
     * https://dvcs.w3.org/hg/html-media/raw-file/default/encrypted-media/encrypted-media.html#clear-key
     *
     * @return size of keyIds vector that contains the key ids, 0 for error
     */
    private int getKeyIds(byte[] keyRequestBlob, Vector<String> keyIds) {
        if (0 == keyRequestBlob.length || keyIds == null)
            return 0;

        String jsonLicenseRequest = new String(keyRequestBlob);
        keyIds.clear();

        try {
            JSONObject license = new JSONObject(jsonLicenseRequest);
            final JSONArray ids = license.getJSONArray("kids");
            for (int i = 0; i < ids.length(); ++i) {
                keyIds.add(ids.getString(i));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Invalid JSON license = " + jsonLicenseRequest);
            return 0;
        }
        return keyIds.size();
    }

    /**
     * Creates the JSON Web Key string.
     *
     * @return JSON Web Key string.
     */
    private String createJsonWebKeySet(Vector<String> keyIds, Vector<String> keys) {
        String jwkSet = "{\"keys\":[";
        for (int i = 0; i < keyIds.size(); ++i) {
            String id = new String(keyIds.get(i).getBytes(Charset.forName("UTF-8")));
            String key = new String(keys.get(i).getBytes(Charset.forName("UTF-8")));

            jwkSet += "{\"kty\":\"oct\",\"kid\":\"" + id +
                    "\",\"k\":\"" + key + "\"}";
        }
        jwkSet += "]}";
        return jwkSet;
    }

    /**
     * Retrieves clear key ids from getKeyRequest(), create JSON Web Key
     * set and send it to the CDM via provideKeyResponse().
     */
    private void getKeys(MediaDrm drm, String initDataType,
            byte[] sessionId, byte[] drmInitData, byte[][] clearKeys) {
        MediaDrm.KeyRequest drmRequest = null;;
        try {
            drmRequest = drm.getKeyRequest(sessionId, drmInitData, initDataType,
                    MediaDrm.KEY_TYPE_STREAMING, null);
        } catch (Exception e) {
            e.printStackTrace();
            Log.i(TAG, "Failed to get key request: " + e.toString());
        }
        if (drmRequest == null) {
            Log.e(TAG, "Failed getKeyRequest");
            return;
        }

        Vector<String> keyIds = new Vector<String>();
        if (0 == getKeyIds(drmRequest.getData(), keyIds)) {
            Log.e(TAG, "No key ids found in initData");
            return;
        }

        if (clearKeys.length != keyIds.size()) {
            Log.e(TAG, "Mismatch number of key ids and keys: ids=" +
                    keyIds.size() + ", keys=" + clearKeys.length);
            return;
        }

        // Base64 encodes clearkeys. Keys are known to the application.
        Vector<String> keys = new Vector<String>();
        for (int i = 0; i < clearKeys.length; ++i) {
            String clearKey = Base64.encodeToString(clearKeys[i],
                    Base64.NO_PADDING | Base64.NO_WRAP);
            keys.add(clearKey);
        }

        String jwkSet = createJsonWebKeySet(keyIds, keys);
        byte[] jsonResponse = jwkSet.getBytes(Charset.forName("UTF-8"));

        try {
            try {
                drm.provideKeyResponse(sessionId, jsonResponse);
            } catch (IllegalStateException e) {
                Log.e(TAG, "Failed to provide key response: " + e.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "Failed to provide key response: " + e.toString());
        }
    }

    private @NonNull MediaDrm startDrm(final byte[][] clearKeys, final String initDataType, final UUID drmSchemeUuid) {
        new Thread() {
            @Override
            public void run() {
                // Set up a looper to handle events
                Looper.prepare();

                // Save the looper so that we can terminate this thread
                // after we are done with it.
                mLooper = Looper.myLooper();

                try {
                    mDrm = new MediaDrm(drmSchemeUuid);
                } catch (MediaDrmException e) {
                    Log.e(TAG, "Failed to create MediaDrm: " + e.getMessage());
                    return;
                }

                synchronized(mLock) {
                    mDrm.setOnEventListener(new MediaDrm.OnEventListener() {
                            @Override
                            public void onEvent(MediaDrm md, byte[] sessionId, int event,
                                    int extra, byte[] data) {
                                if (event == MediaDrm.EVENT_KEY_REQUIRED) {
                                    Log.i(TAG, "MediaDrm event: Key required");
                                    getKeys(mDrm, initDataType, mSessionId, mDrmInitData, clearKeys);
                                } else if (event == MediaDrm.EVENT_KEY_EXPIRED) {
                                    Log.i(TAG, "MediaDrm event: Key expired");
                                    getKeys(mDrm, initDataType, mSessionId, mDrmInitData, clearKeys);
                                } else {
                                    Log.e(TAG, "Events not supported" + event);
                                }
                            }
                        });
                    mLock.notify();
                }
                Looper.loop();  // Blocks forever until Looper.quit() is called.
            }
        }.start();

        // wait for mDrm to be created
        synchronized(mLock) {
            try {
                mLock.wait(1000);
            } catch (Exception e) {
            }
        }
        return mDrm;
    }

    private void stopDrm(MediaDrm drm) {
        if (drm != mDrm) {
            Log.e(TAG, "invalid drm specified in stopDrm");
        }
        mLooper.quit();
    }

    private @NonNull byte[] openSession(MediaDrm drm) {
        byte[] mSessionId = null;
        boolean mRetryOpen;
        do {
            try {
                mRetryOpen = false;
                mSessionId = drm.openSession();
            } catch (Exception e) {
                mRetryOpen = true;
            }
        } while (mRetryOpen);
        return mSessionId;
    }

    private void closeSession(MediaDrm drm, byte[] sessionId) {
        drm.closeSession(sessionId);
    }

    private boolean isResolutionSupported(String mime, String[] features,
            int videoWidth, int videoHeight) {
        if (ApiLevelUtil.isBefore(android.os.Build.VERSION_CODES.JELLY_BEAN)) {
            if  (videoHeight <= 144) {
                return CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_QCIF);
            } else if (videoHeight <= 240) {
                return CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_QVGA);
            } else if (videoHeight <= 288) {
                return CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_CIF);
            } else if (videoHeight <= 480) {
                return CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_480P);
            } else if (videoHeight <= 720) {
                return CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_720P);
            } else if (videoHeight <= 1080) {
                return CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_1080P);
            } else {
                return false;
            }
        }

        MediaFormat format = MediaFormat.createVideoFormat(mime, videoWidth, videoHeight);
        for (String feature: features) {
            format.setFeatureEnabled(feature, true);
        }
        MediaCodecList mcl = new MediaCodecList(MediaCodecList.ALL_CODECS);
        if (mcl.findDecoderForFormat(format) == null) {
            Log.i(TAG, "could not find codec for " + format);
            return false;
        }
        return true;
    }

    /**
     * Tests clear key system playback.
     */
    private void testClearKeyPlayback(
            UUID drmSchemeUuid,
            String videoMime, String[] videoFeatures,
            String initDataType, byte[][] clearKeys,
            Uri audioUrl, boolean audioEncrypted,
            Uri videoUrl, boolean videoEncrypted,
            int videoWidth, int videoHeight, boolean scrambled) throws Exception {
        MediaDrm drm = null;
        mSessionId = null;
        if (!scrambled) {
            drm = startDrm(clearKeys, initDataType, drmSchemeUuid);
            if (!drm.isCryptoSchemeSupported(drmSchemeUuid)) {
                stopDrm(drm);
                throw new Error("Crypto scheme is not supported.");
            }
            mSessionId = openSession(drm);
        }

        if (!isResolutionSupported(videoMime, videoFeatures, videoWidth, videoHeight)) {
            Log.i(TAG, "Device does not support " +
                    videoWidth + "x" + videoHeight + " resolution for " + videoMime);
            return;
        }

        IConnectionStatus connectionStatus = new ConnectionStatus(mContext);
        if (!connectionStatus.isAvailable()) {
            throw new Error("Network is not available, reason: " +
                    connectionStatus.getNotConnectedReason());
        }

        // If device is not online, recheck the status a few times.
        int retries = 0;
        while (!connectionStatus.isConnected()) {
            if (retries++ >= CONNECTION_RETRIES) {
                throw new Error("Device is not online, reason: " +
                        connectionStatus.getNotConnectedReason());
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
            }
        }
        connectionStatus.testConnection(videoUrl);

        mMediaCodecPlayer = new MediaCodecClearKeyPlayer(
                getActivity().getSurfaceHolder(),
                mSessionId, scrambled,
                mContext.getResources());

        mMediaCodecPlayer.setAudioDataSource(audioUrl, null, audioEncrypted);
        mMediaCodecPlayer.setVideoDataSource(videoUrl, null, videoEncrypted);
        mMediaCodecPlayer.start();
        mMediaCodecPlayer.prepare();
        if (!scrambled) {
            mDrmInitData = mMediaCodecPlayer.getDrmInitData();
            getKeys(mDrm, initDataType, mSessionId, mDrmInitData, clearKeys);
        }
        // starts video playback
        mMediaCodecPlayer.startThread();

        long timeOut = System.currentTimeMillis() + PLAY_TIME_MS;
        while (timeOut > System.currentTimeMillis() && !mMediaCodecPlayer.isEnded()) {
            Thread.sleep(SLEEP_TIME_MS);
            if (mMediaCodecPlayer.getCurrentPosition() >= mMediaCodecPlayer.getDuration() ) {
                Log.d(TAG, "current pos = " + mMediaCodecPlayer.getCurrentPosition() +
                        ">= duration = " + mMediaCodecPlayer.getDuration());
                break;
            }
        }

        Log.d(TAG, "playVideo player.reset()");
        mMediaCodecPlayer.reset();
        if (!scrambled) {
            closeSession(drm, mSessionId);
            stopDrm(drm);
        }
    }

    private boolean queryKeyStatus(@NonNull final MediaDrm drm, @NonNull final byte[] sessionId) {
        final HashMap<String, String> keyStatus = drm.queryKeyStatus(sessionId);
        if (keyStatus.isEmpty()) {
            Log.e(TAG, "queryKeyStatus: empty key status");
            return false;
        }

        final Set<String> keySet = keyStatus.keySet();
        final int numKeys = keySet.size();
        final String[] keys = keySet.toArray(new String[numKeys]);
        for (int i = 0; i < numKeys; ++i) {
            final String key = keys[i];
            Log.i(TAG, "queryKeyStatus: key=" + key + ", value=" + keyStatus.get(key));
        }

        return true;
    }

    public void testQueryKeyStatus() throws Exception {
        MediaDrm drm = startDrm(new byte[][] { CLEAR_KEY_CENC }, "cenc", COMMON_PSSH_SCHEME_UUID);
        if (!drm.isCryptoSchemeSupported(COMMON_PSSH_SCHEME_UUID)) {
            stopDrm(drm);
            throw new Error("Crypto scheme is not supported.");
        }

        mSessionId = openSession(drm);

        // Test default key status, should not be defined
        final HashMap<String, String> keyStatus = drm.queryKeyStatus(mSessionId);
        if (!keyStatus.isEmpty()) {
            closeSession(drm, mSessionId);
            stopDrm(drm);
            throw new Error("query default key status failed");
        }

        // Test valid key status
        mMediaCodecPlayer = new MediaCodecClearKeyPlayer(
                getActivity().getSurfaceHolder(),
                mSessionId, false,
                mContext.getResources());
        mMediaCodecPlayer.setAudioDataSource(CENC_AUDIO_URL, null, false);
        mMediaCodecPlayer.setVideoDataSource(CENC_VIDEO_URL, null, true);
        mMediaCodecPlayer.start();
        mMediaCodecPlayer.prepare();

        mDrmInitData = mMediaCodecPlayer.getDrmInitData();
        getKeys(drm, "cenc", mSessionId, mDrmInitData, new byte[][] { CLEAR_KEY_CENC });
        boolean success = true;
        if (!queryKeyStatus(drm, mSessionId)) {
            success = false;
        }

        mMediaCodecPlayer.reset();
        closeSession(drm, mSessionId);
        stopDrm(drm);
        if (!success) {
            throw new Error("query key status failed");
        }
    }

    public void testClearKeyPlaybackCenc() throws Exception {
        testClearKeyPlayback(
            COMMON_PSSH_SCHEME_UUID,
            // using secure codec even though it is clear key DRM
            MIME_VIDEO_AVC, new String[] { CodecCapabilities.FEATURE_SecurePlayback },
            "cenc", new byte[][] { CLEAR_KEY_CENC },
            CENC_AUDIO_URL, false,
            CENC_VIDEO_URL, true,
            VIDEO_WIDTH_CENC, VIDEO_HEIGHT_CENC, false);
    }

    public void testClearKeyPlaybackCenc2() throws Exception {
        testClearKeyPlayback(
            CLEARKEY_SCHEME_UUID,
            // using secure codec even though it is clear key DRM
            MIME_VIDEO_AVC, new String[] { CodecCapabilities.FEATURE_SecurePlayback },
            "cenc", new byte[][] { CLEAR_KEY_CENC },
            CENC_AUDIO_URL, false,
            CENC_VIDEO_URL, true,
            VIDEO_WIDTH_CENC, VIDEO_HEIGHT_CENC, false);
    }

    public void testClearKeyPlaybackWebm() throws Exception {
        testClearKeyPlayback(
            COMMON_PSSH_SCHEME_UUID,
            MIME_VIDEO_VP8, new String[0],
            "webm", new byte[][] { CLEAR_KEY_WEBM },
            WEBM_URL, true,
            WEBM_URL, true,
            VIDEO_WIDTH_WEBM, VIDEO_HEIGHT_WEBM, false);
    }

    public void testClearKeyPlaybackMpeg2ts() throws Exception {
        testClearKeyPlayback(
            COMMON_PSSH_SCHEME_UUID,
            MIME_VIDEO_AVC, new String[0],
            "mpeg2ts", null,
            MPEG2TS_SCRAMBLED_URL, false,
            MPEG2TS_SCRAMBLED_URL, false,
            VIDEO_WIDTH_MPEG2TS, VIDEO_HEIGHT_MPEG2TS, true);
    }

    public void testPlaybackMpeg2ts() throws Exception {
        testClearKeyPlayback(
            COMMON_PSSH_SCHEME_UUID,
            MIME_VIDEO_AVC, new String[0],
            "mpeg2ts", null,
            MPEG2TS_CLEAR_URL, false,
            MPEG2TS_CLEAR_URL, false,
            VIDEO_WIDTH_MPEG2TS, VIDEO_HEIGHT_MPEG2TS, false);
    }
}
