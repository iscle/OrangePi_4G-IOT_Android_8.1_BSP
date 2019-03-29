/*
 * Copyright (C) 2012 The Android Open Source Project
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
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.test.AndroidTestCase;
import android.util.Log;

import com.android.compatibility.common.util.MediaUtils;

import java.io.File;
import java.io.InputStream;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class EncoderTest extends AndroidTestCase {
    private static final String TAG = "EncoderTest";
    private static final boolean VERBOSE = false;

    private static final int kNumInputBytes = 512 * 1024;
    private static final long kTimeoutUs = 100;

    // not all combinations are valid
    private static final int MODE_SILENT = 0;
    private static final int MODE_RANDOM = 1;
    private static final int MODE_RESOURCE = 2;
    private static final int MODE_QUIET = 4;
    private static final int MODE_SILENTLEAD = 8;

    /*
     * Set this to true to save the encoding results to /data/local/tmp
     * You will need to make /data/local/tmp writeable, run "setenforce 0",
     * and remove files left from a previous run.
     */
    private static boolean sSaveResults = false;

    @Override
    public void setContext(Context context) {
        super.setContext(context);
    }

    public void testAMRNBEncoders() {
        LinkedList<MediaFormat> formats = new LinkedList<MediaFormat>();

        final int kBitRates[] =
            { 4750, 5150, 5900, 6700, 7400, 7950, 10200, 12200 };

        for (int j = 0; j < kBitRates.length; ++j) {
            MediaFormat format  = new MediaFormat();
            format.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AMR_NB);
            format.setInteger(MediaFormat.KEY_SAMPLE_RATE, 8000);
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
            format.setInteger(MediaFormat.KEY_BIT_RATE, kBitRates[j]);
            formats.push(format);
        }

        testEncoderWithFormats(MediaFormat.MIMETYPE_AUDIO_AMR_NB, formats);
    }

    public void testAMRWBEncoders() {
        LinkedList<MediaFormat> formats = new LinkedList<MediaFormat>();

        final int kBitRates[] =
            { 6600, 8850, 12650, 14250, 15850, 18250, 19850, 23050, 23850 };

        for (int j = 0; j < kBitRates.length; ++j) {
            MediaFormat format  = new MediaFormat();
            format.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AMR_WB);
            format.setInteger(MediaFormat.KEY_SAMPLE_RATE, 16000);
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
            format.setInteger(MediaFormat.KEY_BIT_RATE, kBitRates[j]);
            formats.push(format);
        }

        testEncoderWithFormats(MediaFormat.MIMETYPE_AUDIO_AMR_WB, formats);
    }

    public void testAACEncoders() {
        LinkedList<MediaFormat> formats = new LinkedList<MediaFormat>();

        final int kAACProfiles[] = {
            2 /* OMX_AUDIO_AACObjectLC */,
            5 /* OMX_AUDIO_AACObjectHE */,
            39 /* OMX_AUDIO_AACObjectELD */
        };

        final int kSampleRates[] = { 8000, 11025, 22050, 44100, 48000 };
        final int kBitRates[] = { 64000, 128000 };

        for (int k = 0; k < kAACProfiles.length; ++k) {
            for (int i = 0; i < kSampleRates.length; ++i) {
                if (kAACProfiles[k] == 5 && kSampleRates[i] < 22050) {
                    // Is this right? HE does not support sample rates < 22050Hz?
                    continue;
                }
                for (int j = 0; j < kBitRates.length; ++j) {
                    for (int ch = 1; ch <= 2; ++ch) {
                        MediaFormat format  = new MediaFormat();
                        format.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC);

                        format.setInteger(
                                MediaFormat.KEY_AAC_PROFILE, kAACProfiles[k]);

                        format.setInteger(
                                MediaFormat.KEY_SAMPLE_RATE, kSampleRates[i]);

                        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, ch);
                        format.setInteger(MediaFormat.KEY_BIT_RATE, kBitRates[j]);
                        formats.push(format);
                    }
                }
            }
        }

        testEncoderWithFormats(MediaFormat.MIMETYPE_AUDIO_AAC, formats);
    }

    private void testEncoderWithFormats(
            String mime, List<MediaFormat> formatList) {
        MediaFormat[] formats = formatList.toArray(new MediaFormat[formatList.size()]);
        String[] componentNames = MediaUtils.getEncoderNames(formats);
        if (componentNames.length == 0) {
            MediaUtils.skipTest("no encoders found for " + Arrays.toString(formats));
            return;
        }
        ExecutorService pool = Executors.newFixedThreadPool(3);

        for (String componentName : componentNames) {
            for (MediaFormat format : formats) {
                assertEquals(mime, format.getString(MediaFormat.KEY_MIME));
                pool.execute(new EncoderRun(componentName, format));
            }
        }
        try {
            pool.shutdown();
            assertTrue("timed out waiting for encoder threads",
                    pool.awaitTermination(10, TimeUnit.MINUTES));
        } catch (InterruptedException e) {
            fail("interrupted while waiting for encoder threads");
        }
    }

    // See bug 25843966
    private long[] mBadSeeds = {
            101833462733980l, // fail @ 23680 in all-random mode
            273262699095706l, // fail @ 58880 in all-random mode
            137295510492957l, // fail @ 35840 in zero-lead mode
            57821391502855l,  // fail @ 32000 in zero-lead mode
    };

    private int queueInputBuffer(
            MediaCodec codec, ByteBuffer[] inputBuffers, int index,
            InputStream istream, int mode, long timeUs, Random random) {
        ByteBuffer buffer = inputBuffers[index];
        buffer.rewind();
        int size = buffer.limit();

        if ((mode & MODE_RESOURCE) != 0 && istream != null) {
            while (buffer.hasRemaining()) {
                try {
                    int next = istream.read();
                    if (next < 0) {
                        break;
                    }
                    buffer.put((byte) next);
                } catch (Exception ex) {
                    Log.i(TAG, "caught exception writing: " + ex);
                    break;
                }
            }
        } else if ((mode & MODE_RANDOM) != 0) {
            if ((mode & MODE_SILENTLEAD) != 0) {
                buffer.putInt(0);
                buffer.putInt(0);
                buffer.putInt(0);
                buffer.putInt(0);
            }
            while (true) {
                try {
                    int next = random.nextInt();
                    buffer.putInt(random.nextInt());
                } catch (BufferOverflowException ex) {
                    break;
                }
            }
        } else {
            byte[] zeroes = new byte[size];
            buffer.put(zeroes);
        }

        if ((mode & MODE_QUIET) != 0) {
            int n = buffer.limit();
            for (int i = 0; i < n; i += 2) {
                short s = buffer.getShort(i);
                s /= 8;
                buffer.putShort(i, s);
            }
        }

        codec.queueInputBuffer(index, 0 /* offset */, size, timeUs, 0 /* flags */);

        return size;
    }

    private void dequeueOutputBuffer(
            MediaCodec codec, ByteBuffer[] outputBuffers,
            int index, MediaCodec.BufferInfo info) {
        codec.releaseOutputBuffer(index, false /* render */);
    }

    class EncoderRun implements Runnable {
        String mComponentName;
        MediaFormat mFormat;

        EncoderRun(String componentName, MediaFormat format) {
            mComponentName = componentName;
            mFormat = format;
        }
        @Override
        public void run() {
            testEncoder(mComponentName, mFormat);
        }
    }

    private void testEncoder(String componentName, MediaFormat format) {
        Log.i(TAG, "testEncoder " + componentName + "/" + format);
        // test with all zeroes/silence
        testEncoder(componentName, format, 0, -1, MODE_SILENT);

        // test with pcm input file
        testEncoder(componentName, format, 0, R.raw.okgoogle123_good, MODE_RESOURCE);
        testEncoder(componentName, format, 0, R.raw.okgoogle123_good, MODE_RESOURCE | MODE_QUIET);
        testEncoder(componentName, format, 0, R.raw.tones, MODE_RESOURCE);
        testEncoder(componentName, format, 0, R.raw.tones, MODE_RESOURCE | MODE_QUIET);

        // test with random data, with and without a few leading zeroes
        for (int i = 0; i < mBadSeeds.length; i++) {
            testEncoder(componentName, format, mBadSeeds[i], -1, MODE_RANDOM);
            testEncoder(componentName, format, mBadSeeds[i], -1, MODE_RANDOM | MODE_SILENTLEAD);
        }
    }

    private void testEncoder(String componentName, MediaFormat format,
            long startSeed, int resid, int mode) {

        Log.i(TAG, "testEncoder " + componentName + "/" + mode + "/" + format);
        int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        int inBitrate = sampleRate * channelCount * 16;  // bit/sec
        int outBitrate = format.getInteger(MediaFormat.KEY_BIT_RATE);

        MediaMuxer muxer = null;
        int muxidx = -1;
        if (sSaveResults) {
            try {
                String outFile = "/data/local/tmp/transcoded-" + componentName +
                        "-" + sampleRate + "Hz-" + channelCount + "ch-" + outBitrate +
                        "bps-" + mode + "-" + resid + "-" + startSeed + "-" +
                        (android.os.Process.is64Bit() ? "64bit" : "32bit") + ".mp4";
                new File("outFile").delete();
                muxer = new MediaMuxer(outFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                // The track can't be added until we have the codec specific data
            } catch (Exception e) {
                Log.i(TAG, "couldn't create muxer: " + e);
            }
        }

        InputStream istream = null;
        if ((mode & MODE_RESOURCE) != 0) {
            istream = mContext.getResources().openRawResource(resid);
        }

        Random random = new Random(startSeed);
        MediaCodec codec;
        try {
            codec = MediaCodec.createByCodecName(componentName);
        } catch (Exception e) {
            fail("codec '" + componentName + "' failed construction.");
            return; /* does not get here, but avoids warning */
        }
        try {
            codec.configure(
                    format,
                    null /* surface */,
                    null /* crypto */,
                    MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (IllegalStateException e) {
            fail("codec '" + componentName + "' failed configuration.");
        }

        codec.start();
        ByteBuffer[] codecInputBuffers = codec.getInputBuffers();
        ByteBuffer[] codecOutputBuffers = codec.getOutputBuffers();

        int numBytesSubmitted = 0;
        boolean doneSubmittingInput = false;
        int numBytesDequeued = 0;

        while (true) {
            int index;

            if (!doneSubmittingInput) {
                index = codec.dequeueInputBuffer(kTimeoutUs /* timeoutUs */);

                if (index != MediaCodec.INFO_TRY_AGAIN_LATER) {
                    long timeUs =
                            (long)numBytesSubmitted * 1000000 / (2 * channelCount * sampleRate);
                    if (numBytesSubmitted >= kNumInputBytes) {
                        codec.queueInputBuffer(
                                index,
                                0 /* offset */,
                                0 /* size */,
                                timeUs,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);

                        if (VERBOSE) {
                            Log.d(TAG, "queued input EOS.");
                        }

                        doneSubmittingInput = true;
                    } else {
                        int size = queueInputBuffer(
                                codec, codecInputBuffers, index, istream, mode, timeUs, random);

                        numBytesSubmitted += size;

                        if (VERBOSE) {
                            Log.d(TAG, "queued " + size + " bytes of input data.");
                        }
                    }
                }
            }

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            index = codec.dequeueOutputBuffer(info, kTimeoutUs /* timeoutUs */);

            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
            } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            } else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                codecOutputBuffers = codec.getOutputBuffers();
            } else {
                if (muxer != null) {
                    ByteBuffer buffer = codec.getOutputBuffer(index);
                    if (muxidx < 0) {
                        MediaFormat trackFormat = codec.getOutputFormat();
                        muxidx = muxer.addTrack(trackFormat);
                        muxer.start();
                    }
                    muxer.writeSampleData(muxidx, buffer, info);
                }

                dequeueOutputBuffer(codec, codecOutputBuffers, index, info);

                numBytesDequeued += info.size;

                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    if (VERBOSE) {
                        Log.d(TAG, "dequeued output EOS.");
                    }
                    break;
                }

                if (VERBOSE) {
                    Log.d(TAG, "dequeued " + info.size + " bytes of output data.");
                }
            }
        }

        if (VERBOSE) {
            Log.d(TAG, "queued a total of " + numBytesSubmitted + "bytes, "
                    + "dequeued " + numBytesDequeued + " bytes.");
        }

        float desiredRatio = (float)outBitrate / (float)inBitrate;
        float actualRatio = (float)numBytesDequeued / (float)numBytesSubmitted;

        if (actualRatio < 0.9 * desiredRatio || actualRatio > 1.1 * desiredRatio) {
            Log.w(TAG, "desiredRatio = " + desiredRatio
                    + ", actualRatio = " + actualRatio);
        }

        codec.release();
        codec = null;
        if (muxer != null) {
            muxer.stop();
            muxer.release();
            muxer = null;
        }
    }
}

