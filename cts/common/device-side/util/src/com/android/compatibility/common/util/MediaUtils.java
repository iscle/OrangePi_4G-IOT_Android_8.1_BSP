/*
 * Copyright 2014 The Android Open Source Project
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
package com.android.compatibility.common.util;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.drm.DrmConvertedStatus;
import android.drm.DrmManagerClient;
import android.graphics.ImageFormat;
import android.media.Image;
import android.media.Image.Plane;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.VideoCapabilities;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.util.Log;
import android.util.Range;

import com.android.compatibility.common.util.DeviceReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.security.MessageDigest;

import static java.lang.reflect.Modifier.isPublic;
import static java.lang.reflect.Modifier.isStatic;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static junit.framework.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

public class MediaUtils {
    private static final String TAG = "MediaUtils";

    /*
     *  ----------------------- HELPER METHODS FOR SKIPPING TESTS -----------------------
     */
    private static final int ALL_AV_TRACKS = -1;

    private static final MediaCodecList sMCL = new MediaCodecList(MediaCodecList.REGULAR_CODECS);

    /**
     * Returns the test name (heuristically).
     *
     * Since it uses heuristics, this method has only been verified for media
     * tests. This centralizes the way to signal errors during a test.
     */
    public static String getTestName() {
        return getTestName(false /* withClass */);
    }

    /**
     * Returns the test name with the full class (heuristically).
     *
     * Since it uses heuristics, this method has only been verified for media
     * tests. This centralizes the way to signal errors during a test.
     */
    public static String getTestNameWithClass() {
        return getTestName(true /* withClass */);
    }

    private static String getTestName(boolean withClass) {
        int bestScore = -1;
        String testName = "test???";
        Map<Thread, StackTraceElement[]> traces = Thread.getAllStackTraces();
        for (Map.Entry<Thread, StackTraceElement[]> entry : traces.entrySet()) {
            StackTraceElement[] stack = entry.getValue();
            for (int index = 0; index < stack.length; ++index) {
                // method name must start with "test"
                String methodName = stack[index].getMethodName();
                if (!methodName.startsWith("test")) {
                    continue;
                }

                int score = 0;
                // see if there is a public non-static void method that takes no argument
                Class<?> clazz;
                try {
                    clazz = Class.forName(stack[index].getClassName());
                    ++score;
                    for (final Method method : clazz.getDeclaredMethods()) {
                        if (method.getName().equals(methodName)
                                && isPublic(method.getModifiers())
                                && !isStatic(method.getModifiers())
                                && method.getParameterTypes().length == 0
                                && method.getReturnType().equals(Void.TYPE)) {
                            ++score;
                            break;
                        }
                    }
                    if (score == 1) {
                        // if we could read the class, but method is not public void, it is
                        // not a candidate
                        continue;
                    }
                } catch (ClassNotFoundException e) {
                }

                // even if we cannot verify the method signature, there are signals in the stack

                // usually test method is invoked by reflection
                int depth = 1;
                while (index + depth < stack.length
                        && stack[index + depth].getMethodName().equals("invoke")
                        && stack[index + depth].getClassName().equals(
                                "java.lang.reflect.Method")) {
                    ++depth;
                }
                if (depth > 1) {
                    ++score;
                    // and usually test method is run by runMethod method in android.test package
                    if (index + depth < stack.length) {
                        if (stack[index + depth].getClassName().startsWith("android.test.")) {
                            ++score;
                        }
                        if (stack[index + depth].getMethodName().equals("runMethod")) {
                            ++score;
                        }
                    }
                }

                if (score > bestScore) {
                    bestScore = score;
                    testName = methodName;
                    if (withClass) {
                        testName = stack[index].getClassName() + "." + testName;
                    }
                }
            }
        }
        return testName;
    }

    /**
     * Finds test name (heuristically) and prints out standard skip message.
     *
     * Since it uses heuristics, this method has only been verified for media
     * tests. This centralizes the way to signal a skipped test.
     */
    public static void skipTest(String tag, String reason) {
        Log.i(tag, "SKIPPING " + getTestName() + "(): " + reason);
        DeviceReportLog log = new DeviceReportLog("CtsMediaSkippedTests", "test_skipped");
        try {
            log.addValue("reason", reason, ResultType.NEUTRAL, ResultUnit.NONE);
            log.addValue(
                    "test", getTestNameWithClass(), ResultType.NEUTRAL, ResultUnit.NONE);
            log.submit();
        } catch (NullPointerException e) { }
    }

    /**
     * Finds test name (heuristically) and prints out standard skip message.
     *
     * Since it uses heuristics, this method has only been verified for media
     * tests.  This centralizes the way to signal a skipped test.
     */
    public static void skipTest(String reason) {
        skipTest(TAG, reason);
    }

    public static boolean check(boolean result, String message) {
        if (!result) {
            skipTest(message);
        }
        return result;
    }

    /*
     *  ------------------- HELPER METHODS FOR CHECKING CODEC SUPPORT -------------------
     */

    // returns the list of codecs that support any one of the formats
    private static String[] getCodecNames(
            boolean isEncoder, Boolean isGoog, MediaFormat... formats) {
        MediaCodecList mcl = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        ArrayList<String> result = new ArrayList<>();
        for (MediaCodecInfo info : mcl.getCodecInfos()) {
            if (info.isEncoder() != isEncoder) {
                continue;
            }
            if (isGoog != null
                    && info.getName().toLowerCase().startsWith("omx.google.") != isGoog) {
                continue;
            }

            for (MediaFormat format : formats) {
                String mime = format.getString(MediaFormat.KEY_MIME);

                CodecCapabilities caps = null;
                try {
                    caps = info.getCapabilitiesForType(mime);
                } catch (IllegalArgumentException e) {  // mime is not supported
                    continue;
                }
                if (caps.isFormatSupported(format)) {
                    result.add(info.getName());
                    break;
                }
            }
        }
        return result.toArray(new String[result.size()]);
    }

    /* Use isGoog = null to query all decoders */
    public static String[] getDecoderNames(/* Nullable */ Boolean isGoog, MediaFormat... formats) {
        return getCodecNames(false /* isEncoder */, isGoog, formats);
    }

    public static String[] getDecoderNames(MediaFormat... formats) {
        return getCodecNames(false /* isEncoder */, null /* isGoog */, formats);
    }

    /* Use isGoog = null to query all decoders */
    public static String[] getEncoderNames(/* Nullable */ Boolean isGoog, MediaFormat... formats) {
        return getCodecNames(true /* isEncoder */, isGoog, formats);
    }

    public static String[] getEncoderNames(MediaFormat... formats) {
        return getCodecNames(true /* isEncoder */, null /* isGoog */, formats);
    }

    public static String[] getDecoderNamesForMime(String mime) {
        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, mime);
        return getCodecNames(false /* isEncoder */, null /* isGoog */, format);
    }

    public static String[] getEncoderNamesForMime(String mime) {
        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, mime);
        return getCodecNames(true /* isEncoder */, null /* isGoog */, format);
    }

    public static void verifyNumCodecs(
            int count, boolean isEncoder, Boolean isGoog, MediaFormat... formats) {
        String desc = (isEncoder ? "encoders" : "decoders") + " for "
                + (formats.length == 1 ? formats[0].toString() : Arrays.toString(formats));
        if (isGoog != null) {
            desc = (isGoog ? "Google " : "non-Google ") + desc;
        }

        String[] codecs = getCodecNames(isEncoder, isGoog, formats);
        assertTrue("test can only verify " + count + " " + desc + "; found " + codecs.length + ": "
                + Arrays.toString(codecs), codecs.length <= count);
    }

    public static MediaCodec getDecoder(MediaFormat format) {
        String decoder = sMCL.findDecoderForFormat(format);
        if (decoder != null) {
            try {
                return MediaCodec.createByCodecName(decoder);
            } catch (IOException e) {
            }
        }
        return null;
    }

    public static boolean canEncode(MediaFormat format) {
        if (sMCL.findEncoderForFormat(format) == null) {
            Log.i(TAG, "no encoder for " + format);
            return false;
        }
        return true;
    }

    public static boolean canDecode(MediaFormat format) {
        if (sMCL.findDecoderForFormat(format) == null) {
            Log.i(TAG, "no decoder for " + format);
            return false;
        }
        return true;
    }

    public static boolean supports(String codecName, String mime, int w, int h) {
        // While this could be simply written as such, give more graceful feedback.
        // MediaFormat format = MediaFormat.createVideoFormat(mime, w, h);
        // return supports(codecName, format);

        VideoCapabilities vidCap = getVideoCapabilities(codecName, mime);
        if (vidCap == null) {
            return false;
        } else if (vidCap.isSizeSupported(w, h)) {
            return true;
        }

        Log.w(TAG, "unsupported size " + w + "x" + h);
        return false;
    }

    public static boolean supports(String codecName, MediaFormat format) {
        MediaCodec codec;
        try {
            codec = MediaCodec.createByCodecName(codecName);
        } catch (IOException e) {
            Log.w(TAG, "codec not found: " + codecName);
            return false;
        }

        String mime = format.getString(MediaFormat.KEY_MIME);
        CodecCapabilities cap = null;
        try {
            cap = codec.getCodecInfo().getCapabilitiesForType(mime);
            return cap.isFormatSupported(format);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "not supported mime: " + mime);
            return false;
        } finally {
            codec.release();
        }
    }

    public static boolean hasCodecForTrack(MediaExtractor ex, int track) {
        int count = ex.getTrackCount();
        if (track < 0 || track >= count) {
            throw new IndexOutOfBoundsException(track + " not in [0.." + (count - 1) + "]");
        }
        return canDecode(ex.getTrackFormat(track));
    }

    /**
     * return true iff all audio and video tracks are supported
     */
    public static boolean hasCodecsForMedia(MediaExtractor ex) {
        for (int i = 0; i < ex.getTrackCount(); ++i) {
            MediaFormat format = ex.getTrackFormat(i);
            // only check for audio and video codecs
            String mime = format.getString(MediaFormat.KEY_MIME).toLowerCase();
            if (!mime.startsWith("audio/") && !mime.startsWith("video/")) {
                continue;
            }
            if (!canDecode(format)) {
                return false;
            }
        }
        return true;
    }

    /**
     * return true iff any track starting with mimePrefix is supported
     */
    public static boolean hasCodecForMediaAndDomain(MediaExtractor ex, String mimePrefix) {
        mimePrefix = mimePrefix.toLowerCase();
        for (int i = 0; i < ex.getTrackCount(); ++i) {
            MediaFormat format = ex.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.toLowerCase().startsWith(mimePrefix)) {
                if (canDecode(format)) {
                    return true;
                }
                Log.i(TAG, "no decoder for " + format);
            }
        }
        return false;
    }

    private static boolean hasCodecsForResourceCombo(
            Context context, int resourceId, int track, String mimePrefix) {
        try {
            AssetFileDescriptor afd = null;
            MediaExtractor ex = null;
            try {
                afd = context.getResources().openRawResourceFd(resourceId);
                ex = new MediaExtractor();
                ex.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                if (mimePrefix != null) {
                    return hasCodecForMediaAndDomain(ex, mimePrefix);
                } else if (track == ALL_AV_TRACKS) {
                    return hasCodecsForMedia(ex);
                } else {
                    return hasCodecForTrack(ex, track);
                }
            } finally {
                if (ex != null) {
                    ex.release();
                }
                if (afd != null) {
                    afd.close();
                }
            }
        } catch (IOException e) {
            Log.i(TAG, "could not open resource");
        }
        return false;
    }

    /**
     * return true iff all audio and video tracks are supported
     */
    public static boolean hasCodecsForResource(Context context, int resourceId) {
        return hasCodecsForResourceCombo(context, resourceId, ALL_AV_TRACKS, null /* mimePrefix */);
    }

    public static boolean checkCodecsForResource(Context context, int resourceId) {
        return check(hasCodecsForResource(context, resourceId), "no decoder found");
    }

    /**
     * return true iff track is supported.
     */
    public static boolean hasCodecForResource(Context context, int resourceId, int track) {
        return hasCodecsForResourceCombo(context, resourceId, track, null /* mimePrefix */);
    }

    public static boolean checkCodecForResource(Context context, int resourceId, int track) {
        return check(hasCodecForResource(context, resourceId, track), "no decoder found");
    }

    /**
     * return true iff any track starting with mimePrefix is supported
     */
    public static boolean hasCodecForResourceAndDomain(
            Context context, int resourceId, String mimePrefix) {
        return hasCodecsForResourceCombo(context, resourceId, ALL_AV_TRACKS, mimePrefix);
    }

    /**
     * return true iff all audio and video tracks are supported
     */
    public static boolean hasCodecsForPath(Context context, String path) {
        MediaExtractor ex = null;
        try {
            ex = getExtractorForPath(context, path);
            return hasCodecsForMedia(ex);
        } catch (IOException e) {
            Log.i(TAG, "could not open path " + path);
        } finally {
            if (ex != null) {
                ex.release();
            }
        }
        return true;
    }

    private static MediaExtractor getExtractorForPath(Context context, String path)
            throws IOException {
        Uri uri = Uri.parse(path);
        String scheme = uri.getScheme();
        MediaExtractor ex = new MediaExtractor();
        try {
            if (scheme == null) { // file
                ex.setDataSource(path);
            } else if (scheme.equalsIgnoreCase("file")) {
                ex.setDataSource(uri.getPath());
            } else {
                ex.setDataSource(context, uri, null);
            }
        } catch (IOException e) {
            ex.release();
            throw e;
        }
        return ex;
    }

    public static boolean checkCodecsForPath(Context context, String path) {
        return check(hasCodecsForPath(context, path), "no decoder found");
    }

    public static boolean hasCodecForDomain(boolean encoder, String domain) {
        for (MediaCodecInfo info : sMCL.getCodecInfos()) {
            if (encoder != info.isEncoder()) {
                continue;
            }

            for (String type : info.getSupportedTypes()) {
                if (type.toLowerCase().startsWith(domain.toLowerCase() + "/")) {
                    Log.i(TAG, "found codec " + info.getName() + " for mime " + type);
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean checkCodecForDomain(boolean encoder, String domain) {
        return check(hasCodecForDomain(encoder, domain),
                "no " + domain + (encoder ? " encoder" : " decoder") + " found");
    }

    private static boolean hasCodecForMime(boolean encoder, String mime) {
        for (MediaCodecInfo info : sMCL.getCodecInfos()) {
            if (encoder != info.isEncoder()) {
                continue;
            }

            for (String type : info.getSupportedTypes()) {
                if (type.equalsIgnoreCase(mime)) {
                    Log.i(TAG, "found codec " + info.getName() + " for mime " + mime);
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasCodecForMimes(boolean encoder, String[] mimes) {
        for (String mime : mimes) {
            if (!hasCodecForMime(encoder, mime)) {
                Log.i(TAG, "no " + (encoder ? "encoder" : "decoder") + " for mime " + mime);
                return false;
            }
        }
        return true;
    }


    public static boolean hasEncoder(String... mimes) {
        return hasCodecForMimes(true /* encoder */, mimes);
    }

    public static boolean hasDecoder(String... mimes) {
        return hasCodecForMimes(false /* encoder */, mimes);
    }

    public static boolean checkDecoder(String... mimes) {
        return check(hasCodecForMimes(false /* encoder */, mimes), "no decoder found");
    }

    public static boolean checkEncoder(String... mimes) {
        return check(hasCodecForMimes(true /* encoder */, mimes), "no encoder found");
    }

    public static boolean canDecodeVideo(String mime, int width, int height, float rate) {
        MediaFormat format = MediaFormat.createVideoFormat(mime, width, height);
        format.setFloat(MediaFormat.KEY_FRAME_RATE, rate);
        return canDecode(format);
    }

    public static boolean canDecodeVideo(
            String mime, int width, int height, float rate,
            Integer profile, Integer level, Integer bitrate) {
        MediaFormat format = MediaFormat.createVideoFormat(mime, width, height);
        format.setFloat(MediaFormat.KEY_FRAME_RATE, rate);
        if (profile != null) {
            format.setInteger(MediaFormat.KEY_PROFILE, profile);
            if (level != null) {
                format.setInteger(MediaFormat.KEY_LEVEL, level);
            }
        }
        if (bitrate != null) {
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        }
        return canDecode(format);
    }

    public static boolean checkEncoderForFormat(MediaFormat format) {
        return check(canEncode(format), "no encoder for " + format);
    }

    public static boolean checkDecoderForFormat(MediaFormat format) {
        return check(canDecode(format), "no decoder for " + format);
    }

    /*
     *  ----------------------- HELPER METHODS FOR MEDIA HANDLING -----------------------
     */

    public static VideoCapabilities getVideoCapabilities(String codecName, String mime) {
        for (MediaCodecInfo info : sMCL.getCodecInfos()) {
            if (!info.getName().equalsIgnoreCase(codecName)) {
                continue;
            }
            CodecCapabilities caps;
            try {
                caps = info.getCapabilitiesForType(mime);
            } catch (IllegalArgumentException e) {
                // mime is not supported
                Log.w(TAG, "not supported mime: " + mime);
                return null;
            }
            VideoCapabilities vidCaps = caps.getVideoCapabilities();
            if (vidCaps == null) {
                Log.w(TAG, "not a video codec: " + codecName);
            }
            return vidCaps;
        }
        Log.w(TAG, "codec not found: " + codecName);
        return null;
    }

    public static MediaFormat getTrackFormatForResource(
            Context context,
            int resourceId,
            String mimeTypePrefix) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        AssetFileDescriptor afd = context.getResources().openRawResourceFd(resourceId);
        try {
            extractor.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
        } finally {
            afd.close();
        }
        return getTrackFormatForExtractor(extractor, mimeTypePrefix);
    }

    public static MediaFormat getTrackFormatForPath(
            Context context, String path, String mimeTypePrefix)
            throws IOException {
      MediaExtractor extractor = getExtractorForPath(context, path);
      return getTrackFormatForExtractor(extractor, mimeTypePrefix);
    }

    private static MediaFormat getTrackFormatForExtractor(
            MediaExtractor extractor,
            String mimeTypePrefix) {
      int trackIndex;
      MediaFormat format = null;
      for (trackIndex = 0; trackIndex < extractor.getTrackCount(); trackIndex++) {
          MediaFormat trackMediaFormat = extractor.getTrackFormat(trackIndex);
          if (trackMediaFormat.getString(MediaFormat.KEY_MIME).startsWith(mimeTypePrefix)) {
              format = trackMediaFormat;
              break;
          }
      }
      extractor.release();
      if (format == null) {
          throw new RuntimeException("couldn't get a track for " + mimeTypePrefix);
      }

      return format;
    }

    public static MediaExtractor createMediaExtractorForMimeType(
            Context context, int resourceId, String mimeTypePrefix)
            throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        AssetFileDescriptor afd = context.getResources().openRawResourceFd(resourceId);
        try {
            extractor.setDataSource(
                    afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
        } finally {
            afd.close();
        }
        int trackIndex;
        for (trackIndex = 0; trackIndex < extractor.getTrackCount(); trackIndex++) {
            MediaFormat trackMediaFormat = extractor.getTrackFormat(trackIndex);
            if (trackMediaFormat.getString(MediaFormat.KEY_MIME).startsWith(mimeTypePrefix)) {
                extractor.selectTrack(trackIndex);
                break;
            }
        }
        if (trackIndex == extractor.getTrackCount()) {
            extractor.release();
            throw new IllegalStateException("couldn't get a track for " + mimeTypePrefix);
        }

        return extractor;
    }

    /*
     *  ---------------------- HELPER METHODS FOR CODEC CONFIGURATION
     */

    /** Format must contain mime, width and height.
     *  Throws Exception if encoder does not support this width and height */
    public static void setMaxEncoderFrameAndBitrates(
            MediaCodec encoder, MediaFormat format, int maxFps) {
        String mime = format.getString(MediaFormat.KEY_MIME);

        VideoCapabilities vidCaps =
            encoder.getCodecInfo().getCapabilitiesForType(mime).getVideoCapabilities();
        setMaxEncoderFrameAndBitrates(vidCaps, format, maxFps);
    }

    public static void setMaxEncoderFrameAndBitrates(
            VideoCapabilities vidCaps, MediaFormat format, int maxFps) {
        int width = format.getInteger(MediaFormat.KEY_WIDTH);
        int height = format.getInteger(MediaFormat.KEY_HEIGHT);

        int maxWidth = vidCaps.getSupportedWidths().getUpper();
        int maxHeight = vidCaps.getSupportedHeightsFor(maxWidth).getUpper();
        int frameRate = Math.min(
                maxFps, vidCaps.getSupportedFrameRatesFor(width, height).getUpper().intValue());
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);

        int bitrate = vidCaps.getBitrateRange().clamp(
            (int)(vidCaps.getBitrateRange().getUpper() /
                  Math.sqrt((double)maxWidth * maxHeight / width / height)));
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
    }

    /*
     *  ------------------ HELPER METHODS FOR STATISTICS AND REPORTING ------------------
     */

    // TODO: migrate this into com.android.compatibility.common.util.Stat
    public static class Stats {
        /** does not support NaN or Inf in |data| */
        public Stats(double[] data) {
            mData = data;
            if (mData != null) {
                mNum = mData.length;
            }
        }

        public int getNum() {
            return mNum;
        }

        /** calculate mSumX and mSumXX */
        private void analyze() {
            if (mAnalyzed) {
                return;
            }

            if (mData != null) {
                for (double x : mData) {
                    if (!(x >= mMinX)) { // mMinX may be NaN
                        mMinX = x;
                    }
                    if (!(x <= mMaxX)) { // mMaxX may be NaN
                        mMaxX = x;
                    }
                    mSumX += x;
                    mSumXX += x * x;
                }
            }
            mAnalyzed = true;
        }

        /** returns the maximum or NaN if it does not exist */
        public double getMin() {
            analyze();
            return mMinX;
        }

        /** returns the minimum or NaN if it does not exist */
        public double getMax() {
            analyze();
            return mMaxX;
        }

        /** returns the average or NaN if it does not exist. */
        public double getAverage() {
            analyze();
            if (mNum == 0) {
                return Double.NaN;
            } else {
                return mSumX / mNum;
            }
        }

        /** returns the standard deviation or NaN if it does not exist. */
        public double getStdev() {
            analyze();
            if (mNum == 0) {
                return Double.NaN;
            } else {
                double average = mSumX / mNum;
                return Math.sqrt(mSumXX / mNum - average * average);
            }
        }

        /** returns the statistics for the moving average over n values */
        public Stats movingAverage(int n) {
            if (n < 1 || mNum < n) {
                return new Stats(null);
            } else if (n == 1) {
                return this;
            }

            double[] avgs = new double[mNum - n + 1];
            double sum = 0;
            for (int i = 0; i < mNum; ++i) {
                sum += mData[i];
                if (i >= n - 1) {
                    avgs[i - n + 1] = sum / n;
                    sum -= mData[i - n + 1];
                }
            }
            return new Stats(avgs);
        }

        /** returns the statistics for the moving average over a window over the
         *  cumulative sum. Basically, moves a window from: [0, window] to
         *  [sum - window, sum] over the cumulative sum, over ((sum - window) / average)
         *  steps, and returns the average value over each window.
         *  This method is used to average time-diff data over a window of a constant time.
         */
        public Stats movingAverageOverSum(double window) {
            if (window <= 0 || mNum < 1) {
                return new Stats(null);
            }

            analyze();
            double average = mSumX / mNum;
            if (window >= mSumX) {
                return new Stats(new double[] { average });
            }
            int samples = (int)Math.ceil((mSumX - window) / average);
            double[] avgs = new double[samples];

            // A somewhat brute force approach to calculating the moving average.
            // TODO: add support for weights in Stats, so we can do a more refined approach.
            double sum = 0; // sum of elements in the window
            int num = 0; // number of elements in the moving window
            int bi = 0; // index of the first element in the moving window
            int ei = 0; // index of the last element in the moving window
            double space = window; // space at the end of the window
            double foot = 0; // space at the beginning of the window

            // invariants: foot + sum + space == window
            //             bi + num == ei
            //
            //  window:             |-------------------------------|
            //                      |    <-----sum------>           |
            //                      <foot>               <---space-->
            //                           |               |
            //  intervals:   |-----------|-------|-------|--------------------|--------|
            //                           ^bi             ^ei

            int ix = 0; // index in the result
            while (ix < samples) {
                // add intervals while there is space in the window
                while (ei < mData.length && mData[ei] <= space) {
                    space -= mData[ei];
                    sum += mData[ei];
                    num++;
                    ei++;
                }

                // calculate average over window and deal with odds and ends (e.g. if there are no
                // intervals in the current window: pick whichever element overlaps the window
                // most.
                if (num > 0) {
                    avgs[ix++] = sum / num;
                } else if (bi > 0 && foot > space) {
                    // consider previous
                    avgs[ix++] = mData[bi - 1];
                } else if (ei == mData.length) {
                    break;
                } else {
                    avgs[ix++] = mData[ei];
                }

                // move the window to the next position
                foot -= average;
                space += average;

                // remove intervals that are now partially or wholly outside of the window
                while (bi < ei && foot < 0) {
                    foot += mData[bi];
                    sum -= mData[bi];
                    num--;
                    bi++;
                }
            }
            return new Stats(Arrays.copyOf(avgs, ix));
        }

        /** calculate mSortedData */
        private void sort() {
            if (mSorted || mNum == 0) {
                return;
            }
            mSortedData = Arrays.copyOf(mData, mNum);
            Arrays.sort(mSortedData);
            mSorted = true;
        }

        /** returns an array of percentiles for the points using nearest rank */
        public double[] getPercentiles(double... points) {
            sort();
            double[] res = new double[points.length];
            for (int i = 0; i < points.length; ++i) {
                if (mNum < 1 || points[i] < 0 || points[i] > 100) {
                    res[i] = Double.NaN;
                } else {
                    res[i] = mSortedData[(int)Math.round(points[i] / 100 * (mNum - 1))];
                }
            }
            return res;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof Stats) {
                Stats other = (Stats)o;
                if (other.mNum != mNum) {
                    return false;
                } else if (mNum == 0) {
                    return true;
                }
                return Arrays.equals(mData, other.mData);
            }
            return false;
        }

        private double[] mData;
        private double mSumX = 0;
        private double mSumXX = 0;
        private double mMinX = Double.NaN;
        private double mMaxX = Double.NaN;
        private int mNum = 0;
        private boolean mAnalyzed = false;
        private double[] mSortedData;
        private boolean mSorted = false;
    }

    /**
     * Convert a forward lock .dm message stream to a .fl file
     * @param context Context to use
     * @param dmStream The .dm message
     * @param flFile The output file to be written
     * @return success
     */
    public static boolean convertDmToFl(
            Context context,
            InputStream dmStream,
            RandomAccessFile flFile) {
        final String MIMETYPE_DRM_MESSAGE = "application/vnd.oma.drm.message";
        byte[] dmData = new byte[10000];
        int totalRead = 0;
        int numRead;
        while (true) {
            try {
                numRead = dmStream.read(dmData, totalRead, dmData.length - totalRead);
            } catch (IOException e) {
                Log.w(TAG, "Failed to read from input file");
                return false;
            }
            if (numRead == -1) {
                break;
            }
            totalRead += numRead;
            if (totalRead == dmData.length) {
                // grow array
                dmData = Arrays.copyOf(dmData, dmData.length + 10000);
            }
        }
        byte[] fileData = Arrays.copyOf(dmData, totalRead);

        DrmManagerClient drmClient = null;
        try {
            drmClient = new DrmManagerClient(context);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "DrmManagerClient instance could not be created, context is Illegal.");
            return false;
        } catch (IllegalStateException e) {
            Log.w(TAG, "DrmManagerClient didn't initialize properly.");
            return false;
        }

        try {
            int convertSessionId = -1;
            try {
                convertSessionId = drmClient.openConvertSession(MIMETYPE_DRM_MESSAGE);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Conversion of Mimetype: " + MIMETYPE_DRM_MESSAGE
                        + " is not supported.", e);
                return false;
            } catch (IllegalStateException e) {
                Log.w(TAG, "Could not access Open DrmFramework.", e);
                return false;
            }

            if (convertSessionId < 0) {
                Log.w(TAG, "Failed to open session.");
                return false;
            }

            DrmConvertedStatus convertedStatus = null;
            try {
                convertedStatus = drmClient.convertData(convertSessionId, fileData);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Buffer with data to convert is illegal. Convertsession: "
                        + convertSessionId, e);
                return false;
            } catch (IllegalStateException e) {
                Log.w(TAG, "Could not convert data. Convertsession: " + convertSessionId, e);
                return false;
            }

            if (convertedStatus == null ||
                    convertedStatus.statusCode != DrmConvertedStatus.STATUS_OK ||
                    convertedStatus.convertedData == null) {
                Log.w(TAG, "Error in converting data. Convertsession: " + convertSessionId);
                try {
                    DrmConvertedStatus result = drmClient.closeConvertSession(convertSessionId);
                    if (result.statusCode != DrmConvertedStatus.STATUS_OK) {
                        Log.w(TAG, "Conversion failed with status: " + result.statusCode);
                        return false;
                    }
                } catch (IllegalStateException e) {
                    Log.w(TAG, "Could not close session. Convertsession: " +
                           convertSessionId, e);
                }
                return false;
            }

            try {
                flFile.write(convertedStatus.convertedData, 0, convertedStatus.convertedData.length);
            } catch (IOException e) {
                Log.w(TAG, "Failed to write to output file: " + e);
                return false;
            }

            try {
                convertedStatus = drmClient.closeConvertSession(convertSessionId);
            } catch (IllegalStateException e) {
                Log.w(TAG, "Could not close convertsession. Convertsession: " +
                        convertSessionId, e);
                return false;
            }

            if (convertedStatus == null ||
                    convertedStatus.statusCode != DrmConvertedStatus.STATUS_OK ||
                    convertedStatus.convertedData == null) {
                Log.w(TAG, "Error in closing session. Convertsession: " + convertSessionId);
                return false;
            }

            try {
                flFile.seek(convertedStatus.offset);
                flFile.write(convertedStatus.convertedData);
            } catch (IOException e) {
                Log.w(TAG, "Could not update file.", e);
                return false;
            }

            return true;
        } finally {
            drmClient.close();
        }
    }

    /**
     * @param decoder new MediaCodec object
     * @param ex MediaExtractor after setDataSource and selectTrack
     * @param frameMD5Sums reference MD5 checksum for decoded frames
     * @return true if decoded frames checksums matches reference checksums
     * @throws IOException
     */
    public static boolean verifyDecoder(
            MediaCodec decoder, MediaExtractor ex, List<String> frameMD5Sums)
            throws IOException {

        int trackIndex = ex.getSampleTrackIndex();
        MediaFormat format = ex.getTrackFormat(trackIndex);
        decoder.configure(format, null /* surface */, null /* crypto */, 0 /* flags */);
        decoder.start();

        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;
        final long kTimeOutUs = 5000; // 5ms timeout
        int decodedFrameCount = 0;
        int expectedFrameCount = frameMD5Sums.size();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        while (!sawOutputEOS) {
            // handle input
            if (!sawInputEOS) {
                int inIdx = decoder.dequeueInputBuffer(kTimeOutUs);
                if (inIdx >= 0) {
                    ByteBuffer buffer = decoder.getInputBuffer(inIdx);
                    int sampleSize = ex.readSampleData(buffer, 0);
                    if (sampleSize < 0) {
                        final int flagEOS = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                        decoder.queueInputBuffer(inIdx, 0, 0, 0, flagEOS);
                        sawInputEOS = true;
                    } else {
                        decoder.queueInputBuffer(inIdx, 0, sampleSize, ex.getSampleTime(), 0);
                        ex.advance();
                    }
                }
            }

            // handle output
            int outputBufIndex = decoder.dequeueOutputBuffer(info, kTimeOutUs);
            if (outputBufIndex >= 0) {
                try {
                    if (info.size > 0) {
                        // Disregard 0-sized buffers at the end.
                        String md5CheckSum = "";
                        Image image = decoder.getOutputImage(outputBufIndex);
                        md5CheckSum = getImageMD5Checksum(image);

                        if (!md5CheckSum.equals(frameMD5Sums.get(decodedFrameCount))) {
                            Log.d(TAG,
                                    String.format(
                                            "Frame %d md5sum mismatch: %s(actual) vs %s(expected)",
                                            decodedFrameCount, md5CheckSum,
                                            frameMD5Sums.get(decodedFrameCount)));
                            return false;
                        }

                        decodedFrameCount++;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "getOutputImage md5CheckSum failed", e);
                    return false;
                } finally {
                    decoder.releaseOutputBuffer(outputBufIndex, false /* render */);
                }
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    sawOutputEOS = true;
                }
            } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat decOutputFormat = decoder.getOutputFormat();
                Log.d(TAG, "output format " + decOutputFormat);
            } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                Log.i(TAG, "Skip handling MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED");
            } else if (outputBufIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                continue;
            } else {
                Log.w(TAG, "decoder.dequeueOutputBuffer() unrecognized index: " + outputBufIndex);
                return false;
            }
        }

        if (decodedFrameCount != expectedFrameCount) {
            return false;
        }

        return true;
    }

    public static String getImageMD5Checksum(Image image) throws Exception {
        int format = image.getFormat();
        if (ImageFormat.YUV_420_888 != format) {
            Log.w(TAG, "unsupported image format");
            return "";
        }

        MessageDigest md = MessageDigest.getInstance("MD5");

        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();

        Image.Plane[] planes = image.getPlanes();
        for (int i = 0; i < planes.length; ++i) {
            ByteBuffer buf = planes[i].getBuffer();

            int width, height, rowStride, pixelStride, x, y;
            rowStride = planes[i].getRowStride();
            pixelStride = planes[i].getPixelStride();
            if (i == 0) {
                width = imageWidth;
                height = imageHeight;
            } else {
                width = imageWidth / 2;
                height = imageHeight /2;
            }
            // local contiguous pixel buffer
            byte[] bb = new byte[width * height];
            if (buf.hasArray()) {
                byte b[] = buf.array();
                int offs = buf.arrayOffset();
                if (pixelStride == 1) {
                    for (y = 0; y < height; ++y) {
                        System.arraycopy(bb, y * width, b, y * rowStride + offs, width);
                    }
                } else {
                    // do it pixel-by-pixel
                    for (y = 0; y < height; ++y) {
                        int lineOffset = offs + y * rowStride;
                        for (x = 0; x < width; ++x) {
                            bb[y * width + x] = b[lineOffset + x * pixelStride];
                        }
                    }
                }
            } else { // almost always ends up here due to direct buffers
                int pos = buf.position();
                if (pixelStride == 1) {
                    for (y = 0; y < height; ++y) {
                        buf.position(pos + y * rowStride);
                        buf.get(bb, y * width, width);
                    }
                } else {
                    // local line buffer
                    byte[] lb = new byte[rowStride];
                    // do it pixel-by-pixel
                    for (y = 0; y < height; ++y) {
                        buf.position(pos + y * rowStride);
                        // we're only guaranteed to have pixelStride * (width - 1) + 1 bytes
                        buf.get(lb, 0, pixelStride * (width - 1) + 1);
                        for (x = 0; x < width; ++x) {
                            bb[y * width + x] = lb[x * pixelStride];
                        }
                    }
                }
                buf.position(pos);
            }
            md.update(bb, 0, width * height);
        }

        return convertByteArrayToHEXString(md.digest());
    }

    private static String convertByteArrayToHEXString(byte[] ba) throws Exception {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < ba.length; i++) {
            result.append(Integer.toString((ba[i] & 0xff) + 0x100, 16).substring(1));
        }
        return result.toString();
    }


    /*
     *  -------------------------------------- END --------------------------------------
     */
}
