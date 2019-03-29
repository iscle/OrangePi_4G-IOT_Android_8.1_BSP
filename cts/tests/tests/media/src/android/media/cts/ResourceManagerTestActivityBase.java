/*
 * Copyright 2015 The Android Open Source Project
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

import android.app.Activity;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.VideoCapabilities;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Bundle;
import android.util.Log;
import java.io.IOException;
import java.util.Vector;

public class ResourceManagerTestActivityBase extends Activity {
    public static final int TYPE_NONSECURE = 0;
    public static final int TYPE_SECURE = 1;
    public static final int TYPE_MIX = 2;

    protected String TAG;
    private static final int FRAME_RATE = 10;
    private static final int IFRAME_INTERVAL = 10;  // 10 seconds between I-frames
    private static final String MIME = MediaFormat.MIMETYPE_VIDEO_AVC;

    private Vector<MediaCodec> mCodecs = new Vector<MediaCodec>();

    private class TestCodecCallback extends MediaCodec.Callback {
        @Override
        public void onInputBufferAvailable(MediaCodec codec, int index) {
            Log.d(TAG, "onInputBufferAvailable " + codec.toString());
        }

        @Override
        public void onOutputBufferAvailable(
                MediaCodec codec, int index, MediaCodec.BufferInfo info) {
            Log.d(TAG, "onOutputBufferAvailable " + codec.toString());
        }

        @Override
        public void onError(MediaCodec codec, MediaCodec.CodecException e) {
            Log.d(TAG, "onError " + codec.toString() + " errorCode " + e.getErrorCode());
        }

        @Override
        public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
            Log.d(TAG, "onOutputFormatChanged " + codec.toString());
        }
    }

    private MediaCodec.Callback mCallback = new TestCodecCallback();

    private MediaFormat getTestFormat(CodecCapabilities caps, boolean securePlayback) {
        VideoCapabilities vcaps = caps.getVideoCapabilities();
        int width = vcaps.getSupportedWidths().getLower();
        int height = vcaps.getSupportedHeightsFor(width).getLower();
        int bitrate = vcaps.getBitrateRange().getLower();

        MediaFormat format = MediaFormat.createVideoFormat(MIME, width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, caps.colorFormats[0]);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        format.setFeatureEnabled(CodecCapabilities.FEATURE_SecurePlayback, securePlayback);
        return format;
    }

    private MediaCodecInfo getTestCodecInfo(boolean securePlayback) {
        // Use avc decoder for testing.
        boolean isEncoder = false;

        MediaCodecList mcl = new MediaCodecList(MediaCodecList.ALL_CODECS);
        for (MediaCodecInfo info : mcl.getCodecInfos()) {
            if (info.isEncoder() != isEncoder) {
                continue;
            }
            CodecCapabilities caps;
            try {
                caps = info.getCapabilitiesForType(MIME);
                boolean securePlaybackSupported =
                        caps.isFeatureSupported(CodecCapabilities.FEATURE_SecurePlayback);
                boolean securePlaybackRequired =
                        caps.isFeatureRequired(CodecCapabilities.FEATURE_SecurePlayback);
                if ((securePlayback && securePlaybackSupported) ||
                        (!securePlayback && !securePlaybackRequired) ) {
                    Log.d(TAG, "securePlayback " + securePlayback + " will use " + info.getName());
                } else {
                    Log.d(TAG, "securePlayback " + securePlayback + " skip " + info.getName());
                    continue;
                }
            } catch (IllegalArgumentException e) {
                // mime is not supported
                continue;
            }
            return info;
        }

        return null;
    }

    protected int allocateCodecs(int max) {
        Bundle extras = getIntent().getExtras();
        int type = TYPE_NONSECURE;
        if (extras != null) {
            type = extras.getInt("test-type", type);
            Log.d(TAG, "type is: " + type);
        }

        boolean shouldSkip = false;
        boolean securePlayback;
        if (type == TYPE_NONSECURE || type == TYPE_MIX) {
            securePlayback = false;
            MediaCodecInfo info = getTestCodecInfo(securePlayback);
            if (info != null) {
                allocateCodecs(max, info, securePlayback);
            } else {
                shouldSkip = true;
            }
        }

        if (!shouldSkip) {
            if (type == TYPE_SECURE || type == TYPE_MIX) {
                securePlayback = true;
                MediaCodecInfo info = getTestCodecInfo(securePlayback);
                if (info != null) {
                    allocateCodecs(max, info, securePlayback);
                } else {
                    shouldSkip = true;
                }
            }
        }

        if (shouldSkip) {
            Log.d(TAG, "test skipped as there's no supported codec.");
            finishWithResult(RESULT_OK);
        }

        Log.d(TAG, "allocateCodecs returned " + mCodecs.size());
        return mCodecs.size();
    }

    protected void allocateCodecs(int max, MediaCodecInfo info, boolean securePlayback) {
        String name = info.getName();
        CodecCapabilities caps = info.getCapabilitiesForType(MIME);
        MediaFormat format = getTestFormat(caps, securePlayback);
        MediaCodec codec = null;
        for (int i = mCodecs.size(); i < max; ++i) {
            try {
                Log.d(TAG, "Create codec " + name + " #" + i);
                codec = MediaCodec.createByCodecName(name);
                codec.setCallback(mCallback);
                Log.d(TAG, "Configure codec " + format);
                codec.configure(format, null, null, 0);
                Log.d(TAG, "Start codec " + format);
                codec.start();
                mCodecs.add(codec);
                codec = null;
            } catch (IllegalArgumentException e) {
                Log.d(TAG, "IllegalArgumentException " + e.getMessage());
                break;
            } catch (IOException e) {
                Log.d(TAG, "IOException " + e.getMessage());
                break;
            } catch (MediaCodec.CodecException e) {
                Log.d(TAG, "CodecException 0x" + Integer.toHexString(e.getErrorCode()));
                break;
            } finally {
                if (codec != null) {
                    Log.d(TAG, "release codec");
                    codec.release();
                    codec = null;
                }
            }
        }
    }

    protected void finishWithResult(int result) {
        for (int i = 0; i < mCodecs.size(); ++i) {
            Log.d(TAG, "release codec #" + i);
            mCodecs.get(i).release();
        }
        mCodecs.clear();
        setResult(result);
        finish();
        Log.d(TAG, "activity finished");
    }

    private void doUseCodecs() {
        int current = 0;
        try {
            for (current = 0; current < mCodecs.size(); ++current) {
                mCodecs.get(current).getName();
            }
        } catch (MediaCodec.CodecException e) {
            Log.d(TAG, "useCodecs got CodecException 0x" + Integer.toHexString(e.getErrorCode()));
            if (e.getErrorCode() == MediaCodec.CodecException.ERROR_RECLAIMED) {
                Log.d(TAG, "Remove codec " + current + " from the list");
                mCodecs.get(current).release();
                mCodecs.remove(current);
                mGotReclaimedException = true;
                mUseCodecs = false;
            }
            return;
        }
    }

    protected boolean mWaitForReclaim = true;
    private Thread mWorkerThread;
    private volatile boolean mUseCodecs = true;
    private volatile boolean mGotReclaimedException = false;
    protected void useCodecs() {
        mWorkerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                long start = System.currentTimeMillis();
                long timeSinceStartedMs = 0;
                while (mUseCodecs && (timeSinceStartedMs < 15000)) {  // timeout in 15s
                    doUseCodecs();
                    try {
                        Thread.sleep(50 /* millis */);
                    } catch (InterruptedException e) {}
                    timeSinceStartedMs = System.currentTimeMillis() - start;
                }
                if (mGotReclaimedException) {
                    Log.d(TAG, "Got expected reclaim exception.");
                    finishWithResult(RESULT_OK);
                } else {
                    Log.d(TAG, "Stopped without getting reclaim exception.");
                    // if the test is supposed to wait for reclaim event then this is a failure,
                    // otherwise this is a pass.
                    finishWithResult(mWaitForReclaim ? RESULT_CANCELED : RESULT_OK);
                }
            }
        });
        mWorkerThread.start();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy called.");
        super.onDestroy();
    }
}
