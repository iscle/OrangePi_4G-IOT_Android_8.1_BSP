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

package android.media.cts;

import android.media.cts.R;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;
import android.util.Range;

import com.android.compatibility.common.util.DeviceReportLog;
import com.android.compatibility.common.util.MediaUtils;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.compatibility.common.util.Stat;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Conformance test for decoders on the device.
 *
 * This test will decode test vectors and calculate every decoded frame's md5
 * checksum to see if it matches with the correct md5 value read from a
 * reference file associated with the test vector. Test vector md5 sums are
 * based on the YUV 420 plannar format.
 */
public class DecoderConformanceTest extends MediaPlayerTestBase {
    private static enum Status {
        FAIL,
        PASS,
        SKIP;
    }

    private static final String REPORT_LOG_NAME = "CtsMediaTestCases";
    private static final String TAG = "DecoderConformanceTest";
    private Resources mResources;
    private DeviceReportLog mReportLog;
    private MediaCodec mDecoder;
    private MediaExtractor mExtractor;

    private static final Map<String, String> MIMETYPE_TO_TAG = new HashMap <String, String>() {{
        put(MediaFormat.MIMETYPE_VIDEO_VP9, "vp9");
    }};

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mResources = mContext.getResources();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }


    private List<String> readResourceLines(String fileName) throws Exception {
        int resId = mResources.getIdentifier(fileName, "raw", mContext.getPackageName());
        InputStream is = mContext.getResources().openRawResource(resId);
        BufferedReader in = new BufferedReader(new InputStreamReader(is, "UTF-8"));

        // Read the file line by line.
        List<String> lines = new ArrayList<String>();
        String str;
        while ((str = in.readLine()) != null) {
            int k = str.indexOf(' ');
            String line = k >= 0 ? str.substring(0, k) : str;
            lines.add(line);
        }

        is.close();
        return lines;
    }

    private List<String> readCodecTestVectors(String mime) throws Exception {
        String tag = MIMETYPE_TO_TAG.get(mime);
        String testVectorFileName = tag + "_test_vectors";
        return readResourceLines(testVectorFileName);
    }

    private List<String> readVectorMD5Sums(String mime, String vectorName) throws Exception {
        String tag = MIMETYPE_TO_TAG.get(mime);
        String md5FileName = vectorName + "_" + tag + "_md5";
        return readResourceLines(md5FileName);
    }

    private void release() {
        try {
            mDecoder.stop();
        } catch (Exception e) {
            Log.e(TAG, "Mediacodec stop exception");
        }

        try {
            mDecoder.release();
            mExtractor.release();
        } catch (Exception e) {
            Log.e(TAG, "Mediacodec release exception");
        }

        mDecoder = null;
        mExtractor = null;
    }

    private Status decodeTestVector(String mime, String decoderName, String vectorName) throws Exception {
        int resId = mResources.getIdentifier(vectorName, "raw", mContext.getPackageName());
        AssetFileDescriptor testFd = mResources.openRawResourceFd(resId);
        mExtractor = new MediaExtractor();
        mExtractor.setDataSource(testFd.getFileDescriptor(), testFd.getStartOffset(), testFd.getLength());
        mExtractor.selectTrack(0);

        MediaCodecInfo codecInfo = mDecoder.getCodecInfo();
        MediaCodecInfo.CodecCapabilities caps = codecInfo.getCapabilitiesForType(mime);
        if (!caps.isFormatSupported(mExtractor.getTrackFormat(0))) {
            return Status.SKIP;
        }

        List<String> frameMD5Sums;
        try {
            frameMD5Sums = readVectorMD5Sums(mime, vectorName);
        } catch(Exception e) {
            Log.e(TAG, "Fail to read " + vectorName + "md5sum file");
            return Status.FAIL;
        }

        try {
            mDecoder = MediaCodec.createByCodecName(decoderName);
            if (MediaUtils.verifyDecoder(mDecoder, mExtractor, frameMD5Sums)) {
                return Status.PASS;
            }
            Log.d(TAG, vectorName + " decoded frames do not match");
            return Status.FAIL;
        } finally {
            release();
        }
    }

    void decodeTestVectors(String mime, boolean isGoog) throws Exception {
        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, mime);
        String[] decoderNames = MediaUtils.getDecoderNames(isGoog, format);
        for (String decoderName: decoderNames) {
            List<String> testVectors = readCodecTestVectors(mime);
            for (String vectorName: testVectors) {
                boolean pass = false;
                Log.d(TAG, "Decode vector " + vectorName + " with " + decoderName);
                try {
                    Status stat = decodeTestVector(mime, decoderName, vectorName);
                    if (stat == Status.PASS) {
                        pass = true;
                    } else if (stat == Status.SKIP) {
                        continue;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Decode " + vectorName + " fail");
                }

                String streamName = "decoder_conformance_test";
                mReportLog = new DeviceReportLog(REPORT_LOG_NAME, streamName);
                mReportLog.addValue("mime", mime, ResultType.NEUTRAL, ResultUnit.NONE);
                mReportLog.addValue("is_goog", isGoog, ResultType.NEUTRAL, ResultUnit.NONE);
                mReportLog.addValue("pass", pass, ResultType.NEUTRAL, ResultUnit.NONE);
                mReportLog.addValue("vector_name", vectorName, ResultType.NEUTRAL, ResultUnit.NONE);
                mReportLog.addValue("decode_name", decoderName, ResultType.NEUTRAL,
                        ResultUnit.NONE);
                mReportLog.submit(getInstrumentation());

                if (!pass) {
                    // Release mediacodec in failure or exception cases.
                    release();
                }
            }

        }
    }

    /**
     * Test VP9 decoders from vendor.
     */
    public void testVP9Other() throws Exception {
        decodeTestVectors(MediaFormat.MIMETYPE_VIDEO_VP9, false /* isGoog */);
    }

    /**
     * Test Google's VP9 decoder from libvpx.
     */
    public void testVP9Goog() throws Exception {
        decodeTestVectors(MediaFormat.MIMETYPE_VIDEO_VP9, true /* isGoog */);
    }

}
