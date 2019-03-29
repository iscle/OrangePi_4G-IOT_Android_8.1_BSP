/*
 * Copyright (C) 2013 The Android Open Source Project
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

package android.video.cts;

import com.android.compatibility.common.util.MediaUtils;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.media.MediaCodecInfo.VideoCapabilities;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Log;
import android.util.Range;

import java.io.IOException;

/**
 * Utility class for getting codec information like bit rate, fps, and etc.
 * Uses public member variables instead of methods as this code is only for video benchmarking.
 */
public class CodecInfo {
    /** bit rate in bps */
    public int mBitRate = 0;
    /** Frame rate */
    public int mFps = 0;
    /** if codec is supporting YUV semiplanar format */
    public boolean mSupportSemiPlanar = false;
    /** if codec is supporting YUV planar format */
    public boolean mSupportPlanar = false;

    private static final String TAG = "CodecInfo";
    private static final String VIDEO_AVC = MediaFormat.MIMETYPE_VIDEO_AVC;
    /**
     * Check if given codec with given (w,h) is supported.
     * @param codecName codec name
     * @param mimeType codec type in mime format like MediaFormat.MIMETYPE_VIDEO_AVC
     * @param w video width
     * @param h video height
     * @return null if the configuration is not supported.
     */
    public static CodecInfo getSupportedFormatInfo(
            String codecName, String mimeType, int w, int h, int maxFps) {
        MediaCodec codec;
        try {
            codec = MediaCodec.createByCodecName(codecName);
        } catch (IOException e) {
            return null;
        }

        CodecCapabilities cap = codec.getCodecInfo().getCapabilitiesForType(mimeType);
        if (cap.colorFormats.length == 0) {
            Log.w(TAG, "no supported color format");
            codec.release();
            return null;
        }

        CodecInfo info = new CodecInfo();
        for (int color : cap.colorFormats) {
            if (color == CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
                info.mSupportSemiPlanar = true;
            }
            if (color == CodecCapabilities.COLOR_FormatYUV420Planar) {
                info.mSupportPlanar = true;
            }
        }
        printIntArray("supported colors", cap.colorFormats);

        MediaFormat format = MediaFormat.createVideoFormat(mimeType, w, h);
        MediaUtils.setMaxEncoderFrameAndBitrates(cap.getVideoCapabilities(), format, maxFps);
        info.mFps = format.getInteger(MediaFormat.KEY_FRAME_RATE);
        info.mBitRate = format.getInteger(MediaFormat.KEY_BIT_RATE);

        codec.release();
        return info;
    }

    // for debugging
    private static void printIntArray(String msg, int[] data) {
        StringBuilder builder = new StringBuilder();
        builder.append(msg);
        builder.append(":");
        for (int e : data) {
            builder.append(Integer.toHexString(e));
            builder.append(",");
        }
        builder.deleteCharAt(builder.length() - 1);
        Log.i(TAG, builder.toString());
    }
}
