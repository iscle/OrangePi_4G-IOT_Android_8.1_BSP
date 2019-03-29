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

package android.media.cts;

import android.media.cts.R;

import android.media.cts.CodecUtils;

import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.VideoCapabilities;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.util.Log;
import android.util.Pair;
import android.util.Range;
import android.util.Size;
import android.view.Surface;

import com.android.compatibility.common.util.MediaUtils;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

public class VideoEncoderTest extends MediaPlayerTestBase {
    private static final int MAX_SAMPLE_SIZE = 256 * 1024;
    private static final String TAG = "VideoEncoderTest";
    private static final long FRAME_TIMEOUT_MS = 1000;
    // use larger delay before we get first frame, some encoders may need more time
    private static final long INIT_TIMEOUT_MS = 2000;

    private static final String SOURCE_URL =
        "android.resource://android.media.cts/raw/video_480x360_mp4_h264_871kbps_30fps";

    private final boolean DEBUG = false;

    class VideoStorage {
        private LinkedList<Pair<ByteBuffer, BufferInfo>> mStream;
        private MediaFormat mFormat;
        private int mInputBufferSize;

        public VideoStorage() {
            mStream = new LinkedList<Pair<ByteBuffer, BufferInfo>>();
        }

        public void setFormat(MediaFormat format) {
            mFormat = format;
        }

        public void addBuffer(ByteBuffer buffer, BufferInfo info) {
            ByteBuffer savedBuffer = ByteBuffer.allocate(info.size);
            savedBuffer.put(buffer);
            if (info.size > mInputBufferSize) {
                mInputBufferSize = info.size;
            }
            BufferInfo savedInfo = new BufferInfo();
            savedInfo.set(0, savedBuffer.position(), info.presentationTimeUs, info.flags);
            mStream.addLast(Pair.create(savedBuffer, savedInfo));
        }

        private void play(MediaCodec decoder, Surface surface) {
            decoder.reset();
            final Object condition = new Object();
            final Iterator<Pair<ByteBuffer, BufferInfo>> it = mStream.iterator();
            decoder.setCallback(new MediaCodec.Callback() {
                public void onOutputBufferAvailable(MediaCodec codec, int ix, BufferInfo info) {
                    codec.releaseOutputBuffer(ix, info.size > 0);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        synchronized (condition) {
                            condition.notifyAll();
                        }
                    }
                }
                public void onInputBufferAvailable(MediaCodec codec, int ix) {
                    if (it.hasNext()) {
                        Pair<ByteBuffer, BufferInfo> el = it.next();
                        el.first.clear();
                        try {
                            codec.getInputBuffer(ix).put(el.first);
                        } catch (java.nio.BufferOverflowException e) {
                            Log.e(TAG, "cannot fit " + el.first.limit()
                                    + "-byte encoded buffer into "
                                    + codec.getInputBuffer(ix).remaining()
                                    + "-byte input buffer of " + codec.getName()
                                    + " configured for " + codec.getInputFormat());
                            throw e;
                        }
                        BufferInfo info = el.second;
                        codec.queueInputBuffer(
                                ix, 0, info.size, info.presentationTimeUs, info.flags);
                    }
                }
                public void onError(MediaCodec codec, MediaCodec.CodecException e) {
                    Log.i(TAG, "got codec exception", e);
                    fail("received codec error during decode" + e);
                }
                public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
                    Log.i(TAG, "got output format " + format);
                }
            });
            mFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, mInputBufferSize);
            decoder.configure(mFormat, surface, null /* crypto */, 0 /* flags */);
            decoder.start();
            synchronized (condition) {
                try {
                    condition.wait();
                } catch (InterruptedException e) {
                    fail("playback interrupted");
                }
            }
            decoder.stop();
        }

        public void playAll(Surface surface) {
            if (mFormat == null) {
                Log.i(TAG, "no stream to play");
                return;
            }
            String mime = mFormat.getString(MediaFormat.KEY_MIME);
            MediaCodecList mcl = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
            for (MediaCodecInfo info : mcl.getCodecInfos()) {
                if (info.isEncoder()) {
                    continue;
                }
                MediaCodec codec = null;
                try {
                    CodecCapabilities caps = info.getCapabilitiesForType(mime);
                    if (!caps.isFormatSupported(mFormat)) {
                        continue;
                    }
                    codec = MediaCodec.createByCodecName(info.getName());
                } catch (IllegalArgumentException | IOException e) {
                    continue;
                }
                play(codec, surface);
                codec.release();
            }
        }
    }

    abstract class VideoProcessorBase extends MediaCodec.Callback {
        private static final String TAG = "VideoProcessorBase";

        /*
         * Set this to true to save the encoding results to /data/local/tmp
         * You will need to make /data/local/tmp writeable, run "setenforce 0",
         * and remove files left from a previous run.
         */
        private boolean mSaveResults = false;
        private static final String FILE_DIR = "/data/local/tmp";
        protected int mMuxIndex = -1;

        protected String mProcessorName = "VideoProcessor";
        private MediaExtractor mExtractor;
        protected MediaMuxer mMuxer;
        private ByteBuffer mBuffer = ByteBuffer.allocate(MAX_SAMPLE_SIZE);
        protected int mTrackIndex = -1;
        private boolean mSignaledDecoderEOS;

        protected boolean mCompleted;
        protected boolean mEncoderIsActive;
        protected boolean mEncodeOutputFormatUpdated;
        protected final Object mCondition = new Object();
        protected final Object mCodecLock = new Object();

        protected MediaFormat mDecFormat;
        protected MediaCodec mDecoder, mEncoder;

        private VideoStorage mEncodedStream;
        protected int mFrameRate = 0;
        protected int mBitRate = 0;

        protected Function<MediaFormat, Boolean> mUpdateConfigFormatHook;
        protected Function<MediaFormat, Boolean> mCheckOutputFormatHook;

        public void setProcessorName(String name) {
            mProcessorName = name;
        }

        public void setUpdateConfigHook(Function<MediaFormat, Boolean> hook) {
            mUpdateConfigFormatHook = hook;
        }

        public void setCheckOutputFormatHook(Function<MediaFormat, Boolean> hook) {
            mCheckOutputFormatHook = hook;
        }

        protected void open(String path) throws IOException {
            mExtractor = new MediaExtractor();
            if (path.startsWith("android.resource://")) {
                mExtractor.setDataSource(mContext, Uri.parse(path), null);
            } else {
                mExtractor.setDataSource(path);
            }

            for (int i = 0; i < mExtractor.getTrackCount(); i++) {
                MediaFormat fmt = mExtractor.getTrackFormat(i);
                String mime = fmt.getString(MediaFormat.KEY_MIME).toLowerCase();
                if (mime.startsWith("video/")) {
                    mTrackIndex = i;
                    mDecFormat = fmt;
                    mExtractor.selectTrack(i);
                    break;
                }
            }
            mEncodedStream = new VideoStorage();
            assertTrue("file " + path + " has no video", mTrackIndex >= 0);
        }

        // returns true if encoder supports the size
        protected boolean initCodecsAndConfigureEncoder(
                String videoEncName, String outMime, int width, int height,
                int colorFormat) throws IOException {
            mDecFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);

            MediaCodecList mcl = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
            String videoDecName = mcl.findDecoderForFormat(mDecFormat);
            Log.i(TAG, "decoder for " + mDecFormat + " is " + videoDecName);
            mDecoder = MediaCodec.createByCodecName(videoDecName);
            mEncoder = MediaCodec.createByCodecName(videoEncName);

            mDecoder.setCallback(this);
            mEncoder.setCallback(this);

            VideoCapabilities encCaps =
                mEncoder.getCodecInfo().getCapabilitiesForType(outMime).getVideoCapabilities();
            if (!encCaps.isSizeSupported(width, height)) {
                Log.i(TAG, videoEncName + " does not support size: " + width + "x" + height);
                return false;
            }

            MediaFormat outFmt = MediaFormat.createVideoFormat(outMime, width, height);
            int bitRate = 0;
            MediaUtils.setMaxEncoderFrameAndBitrates(encCaps, outFmt, 30);
            if (mFrameRate > 0) {
                outFmt.setInteger(MediaFormat.KEY_FRAME_RATE, mFrameRate);
            }
            if (mBitRate > 0) {
                outFmt.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
            }
            outFmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            outFmt.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
            // Some extra configure before starting the encoder.
            if (mUpdateConfigFormatHook != null) {
                if (!mUpdateConfigFormatHook.apply(outFmt)) {
                    return false;
                }
            }
            mEncoder.configure(outFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            Log.i(TAG, "encoder input format " + mEncoder.getInputFormat() + " from " + outFmt);
            if (mSaveResults) {
                try {
                    String outFileName =
                            FILE_DIR + mProcessorName + "_" + bitRate + "bps";
                    if (outMime.equals(MediaFormat.MIMETYPE_VIDEO_VP8) ||
                            outMime.equals(MediaFormat.MIMETYPE_VIDEO_VP9)) {
                        mMuxer = new MediaMuxer(
                                outFileName + ".webm", MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM);
                    } else {
                        mMuxer = new MediaMuxer(
                                outFileName + ".mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                    }
                    // The track can't be added until we have the codec specific data
                } catch (Exception e) {
                    Log.i(TAG, "couldn't create muxer: " + e);
                }
            }
            return true;
        }

        protected void close() {
            synchronized (mCodecLock) {
                if (mDecoder != null) {
                    mDecoder.release();
                    mDecoder = null;
                }
                if (mEncoder != null) {
                    mEncoder.release();
                    mEncoder = null;
                }
            }
            if (mExtractor != null) {
                mExtractor.release();
                mExtractor = null;
            }
            if (mMuxer != null) {
                mMuxer.stop();
                mMuxer.release();
                mMuxer = null;
            }
        }

        // returns true if filled buffer
        protected boolean fillDecoderInputBuffer(int ix) {
            if (DEBUG) Log.v(TAG, "decoder received input #" + ix);
            while (!mSignaledDecoderEOS) {
                int track = mExtractor.getSampleTrackIndex();
                if (track >= 0 && track != mTrackIndex) {
                    mExtractor.advance();
                    continue;
                }
                int size = mExtractor.readSampleData(mBuffer, 0);
                if (size < 0) {
                    // queue decoder input EOS
                    if (DEBUG) Log.v(TAG, "queuing decoder EOS");
                    mDecoder.queueInputBuffer(
                            ix, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    mSignaledDecoderEOS = true;
                } else {
                    mBuffer.limit(size);
                    mBuffer.position(0);
                    BufferInfo info = new BufferInfo();
                    info.set(
                            0, mBuffer.limit(), mExtractor.getSampleTime(),
                            mExtractor.getSampleFlags());
                    mDecoder.getInputBuffer(ix).put(mBuffer);
                    if (DEBUG) Log.v(TAG, "queing input #" + ix + " for decoder with timestamp "
                            + info.presentationTimeUs);
                    mDecoder.queueInputBuffer(
                            ix, 0, mBuffer.limit(), info.presentationTimeUs, 0);
                }
                mExtractor.advance();
                return true;
            }
            return false;
        }

        protected void emptyEncoderOutputBuffer(int ix, BufferInfo info) {
            if (DEBUG) Log.v(TAG, "encoder received output #" + ix
                     + " (sz=" + info.size + ", f=" + info.flags
                     + ", ts=" + info.presentationTimeUs + ")");
            ByteBuffer outputBuffer = mEncoder.getOutputBuffer(ix);
            mEncodedStream.addBuffer(outputBuffer, info);

            if (mMuxer != null) {
                // reset position as addBuffer() modifies it
                outputBuffer.position(info.offset);
                outputBuffer.limit(info.offset + info.size);
                mMuxer.writeSampleData(mMuxIndex, outputBuffer, info);
            }

            if (!mCompleted) {
                mEncoder.releaseOutputBuffer(ix, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "encoder received output EOS");
                    synchronized(mCondition) {
                        mCompleted = true;
                        mCondition.notifyAll(); // condition is always satisfied
                    }
                } else {
                    synchronized(mCondition) {
                        mEncoderIsActive = true;
                    }
                }
            }
        }

        protected void saveEncoderFormat(MediaFormat format) {
            mEncodedStream.setFormat(format);
            if (mCheckOutputFormatHook != null) {
                mCheckOutputFormatHook.apply(format);
            }
            if (mMuxer != null) {
                if (mMuxIndex < 0) {
                    mMuxIndex = mMuxer.addTrack(format);
                    mMuxer.start();
                }
            }
        }

        public void playBack(Surface surface) {
            mEncodedStream.playAll(surface);
        }

        public void setFrameAndBitRates(int frameRate, int bitRate) {
            mFrameRate = frameRate;
            mBitRate = bitRate;
        }

        @Override
        public void onInputBufferAvailable(MediaCodec mediaCodec, int ix) {
            synchronized (mCodecLock) {
                if (mEncoder != null && mDecoder != null) {
                    onInputBufferAvailableLocked(mediaCodec, ix);
                }
            }
        }

        @Override
        public void onOutputBufferAvailable(
                MediaCodec mediaCodec, int ix, BufferInfo info) {
            synchronized (mCodecLock) {
                if (mEncoder != null && mDecoder != null) {
                    onOutputBufferAvailableLocked(mediaCodec, ix, info);
                }
            }
        }

        public abstract boolean processLoop(
                String path, String outMime, String videoEncName,
                int width, int height, boolean optional);
        protected abstract void onInputBufferAvailableLocked(
                MediaCodec mediaCodec, int ix);
        protected abstract void onOutputBufferAvailableLocked(
                MediaCodec mediaCodec, int ix, BufferInfo info);
    }

    class VideoProcessor extends VideoProcessorBase {
        private static final String TAG = "VideoProcessor";
        private boolean mWorkInProgress;
        private boolean mGotDecoderEOS;
        private boolean mSignaledEncoderEOS;

        private LinkedList<Pair<Integer, BufferInfo>> mBuffersToRender =
            new LinkedList<Pair<Integer, BufferInfo>>();
        private LinkedList<Integer> mEncInputBuffers = new LinkedList<Integer>();

        private int mEncInputBufferSize = -1;

        @Override
        public boolean processLoop(
                 String path, String outMime, String videoEncName,
                 int width, int height, boolean optional) {
            boolean skipped = true;
            try {
                open(path);
                if (!initCodecsAndConfigureEncoder(
                        videoEncName, outMime, width, height,
                        CodecCapabilities.COLOR_FormatYUV420Flexible)) {
                    assertTrue("could not configure encoder for supported size", optional);
                    return !skipped;
                }
                skipped = false;

                mDecoder.configure(mDecFormat, null /* surface */, null /* crypto */, 0);

                mDecoder.start();
                mEncoder.start();

                // main loop - process GL ops as only main thread has GL context
                while (!mCompleted) {
                    Pair<Integer, BufferInfo> decBuffer = null;
                    int encBuffer = -1;
                    synchronized (mCondition) {
                        try {
                            // wait for an encoder input buffer and a decoder output buffer
                            // Use a timeout to avoid stalling the test if it doesn't arrive.
                            if (!haveBuffers() && !mCompleted) {
                                mCondition.wait(mEncodeOutputFormatUpdated ?
                                        FRAME_TIMEOUT_MS : INIT_TIMEOUT_MS);
                            }
                        } catch (InterruptedException ie) {
                            fail("wait interrupted");  // shouldn't happen
                        }
                        if (mCompleted) {
                            break;
                        }
                        if (!haveBuffers()) {
                            if (mEncoderIsActive) {
                                mEncoderIsActive = false;
                                Log.d(TAG, "No more input but still getting output from encoder.");
                                continue;
                            }
                            fail("timed out after " + mBuffersToRender.size()
                                    + " decoder output and " + mEncInputBuffers.size()
                                    + " encoder input buffers");
                        }

                        if (DEBUG) Log.v(TAG, "got image");
                        decBuffer = mBuffersToRender.removeFirst();
                        encBuffer = mEncInputBuffers.removeFirst();
                        if (isEOSOnlyBuffer(decBuffer)) {
                            queueEncoderEOS(decBuffer, encBuffer);
                            continue;
                        }
                        mWorkInProgress = true;
                    }

                    if (mWorkInProgress) {
                        renderDecodedBuffer(decBuffer, encBuffer);
                        synchronized(mCondition) {
                            mWorkInProgress = false;
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                fail("received exception " + e);
            } finally {
                close();
            }
            return !skipped;
        }

        @Override
        public void onInputBufferAvailableLocked(MediaCodec mediaCodec, int ix) {
            if (mediaCodec == mDecoder) {
                // fill input buffer from extractor
                fillDecoderInputBuffer(ix);
            } else if (mediaCodec == mEncoder) {
                synchronized(mCondition) {
                    mEncInputBuffers.addLast(ix);
                    tryToPropagateEOS();
                    if (haveBuffers()) {
                        mCondition.notifyAll();
                    }
                }
            } else {
                fail("received input buffer on " + mediaCodec.getName());
            }
        }

        @Override
        public void onOutputBufferAvailableLocked(
                MediaCodec mediaCodec, int ix, BufferInfo info) {
            if (mediaCodec == mDecoder) {
                if (DEBUG) Log.v(TAG, "decoder received output #" + ix
                         + " (sz=" + info.size + ", f=" + info.flags
                         + ", ts=" + info.presentationTimeUs + ")");
                // render output buffer from decoder
                if (!mGotDecoderEOS) {
                    boolean eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    // can release empty buffers now
                    if (info.size == 0) {
                        mDecoder.releaseOutputBuffer(ix, false /* render */);
                        ix = -1; // dummy index used by render to not render
                    }
                    synchronized(mCondition) {
                        if (ix < 0 && eos && mBuffersToRender.size() > 0) {
                            // move lone EOS flag to last buffer to be rendered
                            mBuffersToRender.peekLast().second.flags |=
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                        } else if (ix >= 0 || eos) {
                            mBuffersToRender.addLast(Pair.create(ix, info));
                        }
                        if (eos) {
                            tryToPropagateEOS();
                            mGotDecoderEOS = true;
                        }
                        if (haveBuffers()) {
                            mCondition.notifyAll();
                        }
                    }
                }
            } else if (mediaCodec == mEncoder) {
                emptyEncoderOutputBuffer(ix, info);
            } else {
                fail("received output buffer on " + mediaCodec.getName());
            }
        }

        private void renderDecodedBuffer(Pair<Integer, BufferInfo> decBuffer, int encBuffer) {
            // process heavyweight actions under instance lock
            Image encImage = mEncoder.getInputImage(encBuffer);
            Image decImage = mDecoder.getOutputImage(decBuffer.first);
            assertNotNull("could not get encoder image for " + mEncoder.getInputFormat(), encImage);
            assertNotNull("could not get decoder image for " + mDecoder.getInputFormat(), decImage);
            assertEquals("incorrect decoder format",decImage.getFormat(), ImageFormat.YUV_420_888);
            assertEquals("incorrect encoder format", encImage.getFormat(), ImageFormat.YUV_420_888);

            CodecUtils.copyFlexYUVImage(encImage, decImage);

            // TRICKY: need this for queueBuffer
            if (mEncInputBufferSize < 0) {
                mEncInputBufferSize = mEncoder.getInputBuffer(encBuffer).capacity();
            }
            Log.d(TAG, "queuing input #" + encBuffer + " for encoder (sz="
                    + mEncInputBufferSize + ", f=" + decBuffer.second.flags
                    + ", ts=" + decBuffer.second.presentationTimeUs + ")");
            mEncoder.queueInputBuffer(
                    encBuffer, 0, mEncInputBufferSize, decBuffer.second.presentationTimeUs,
                    decBuffer.second.flags);
            if ((decBuffer.second.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                mSignaledEncoderEOS = true;
            }
            mDecoder.releaseOutputBuffer(decBuffer.first, false /* render */);
        }

        @Override
        public void onError(MediaCodec mediaCodec, MediaCodec.CodecException e) {
            fail("received error on " + mediaCodec.getName() + ": " + e);
        }

        @Override
        public void onOutputFormatChanged(MediaCodec mediaCodec, MediaFormat mediaFormat) {
            Log.i(TAG, mediaCodec.getName() + " got new output format " + mediaFormat);
            if (mediaCodec == mEncoder) {
                mEncodeOutputFormatUpdated = true;
                saveEncoderFormat(mediaFormat);
            }
        }

        // next methods are synchronized on mCondition
        private boolean haveBuffers() {
            return mEncInputBuffers.size() > 0 && mBuffersToRender.size() > 0
                    && !mSignaledEncoderEOS;
        }

        private boolean isEOSOnlyBuffer(Pair<Integer, BufferInfo> decBuffer) {
            return decBuffer.first < 0 || decBuffer.second.size == 0;
        }

        protected void tryToPropagateEOS() {
            if (!mWorkInProgress && haveBuffers() && isEOSOnlyBuffer(mBuffersToRender.getFirst())) {
                Pair<Integer, BufferInfo> decBuffer = mBuffersToRender.removeFirst();
                int encBuffer = mEncInputBuffers.removeFirst();
                queueEncoderEOS(decBuffer, encBuffer);
            }
        }

        void queueEncoderEOS(Pair<Integer, BufferInfo> decBuffer, int encBuffer) {
            Log.d(TAG, "signaling encoder EOS");
            mEncoder.queueInputBuffer(encBuffer, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            mSignaledEncoderEOS = true;
            if (decBuffer.first >= 0) {
                mDecoder.releaseOutputBuffer(decBuffer.first, false /* render */);
            }
        }
    }


    class SurfaceVideoProcessor extends VideoProcessorBase
            implements SurfaceTexture.OnFrameAvailableListener {
        private static final String TAG = "SurfaceVideoProcessor";
        private boolean mFrameAvailable;
        private boolean mGotDecoderEOS;
        private boolean mSignaledEncoderEOS;

        private InputSurface mEncSurface;
        private OutputSurface mDecSurface;
        private BufferInfo mInfoOnSurface;

        private LinkedList<Pair<Integer, BufferInfo>> mBuffersToRender =
            new LinkedList<Pair<Integer, BufferInfo>>();

        @Override
        public boolean processLoop(
                String path, String outMime, String videoEncName,
                int width, int height, boolean optional) {
            boolean skipped = true;
            try {
                open(path);
                if (!initCodecsAndConfigureEncoder(
                        videoEncName, outMime, width, height,
                        CodecCapabilities.COLOR_FormatSurface)) {
                    assertTrue("could not configure encoder for supported size", optional);
                    return !skipped;
                }
                skipped = false;

                mEncSurface = new InputSurface(mEncoder.createInputSurface());
                mEncSurface.makeCurrent();

                mDecSurface = new OutputSurface(this);
                //mDecSurface.changeFragmentShader(FRAGMENT_SHADER);
                mDecoder.configure(mDecFormat, mDecSurface.getSurface(), null /* crypto */, 0);

                mDecoder.start();
                mEncoder.start();

                // main loop - process GL ops as only main thread has GL context
                while (!mCompleted) {
                    BufferInfo info = null;
                    synchronized (mCondition) {
                        try {
                            // wait for mFrameAvailable, which is set by onFrameAvailable().
                            // Use a timeout to avoid stalling the test if it doesn't arrive.
                            if (!mFrameAvailable && !mCompleted && !mEncoderIsActive) {
                                mCondition.wait(mEncodeOutputFormatUpdated ?
                                        FRAME_TIMEOUT_MS : INIT_TIMEOUT_MS);
                            }
                        } catch (InterruptedException ie) {
                            fail("wait interrupted");  // shouldn't happen
                        }
                        if (mCompleted) {
                            break;
                        }
                        if (mEncoderIsActive) {
                            mEncoderIsActive = false;
                            if (DEBUG) Log.d(TAG, "encoder is still active, continue");
                            continue;
                        }
                        assertTrue("still waiting for image", mFrameAvailable);
                        if (DEBUG) Log.v(TAG, "got image");
                        info = mInfoOnSurface;
                    }
                    if (info == null) {
                        continue;
                    }
                    if (info.size > 0) {
                        mDecSurface.latchImage();
                        if (DEBUG) Log.v(TAG, "latched image");
                        mFrameAvailable = false;

                        mDecSurface.drawImage();
                        Log.d(TAG, "encoding frame at " + info.presentationTimeUs * 1000);

                        mEncSurface.setPresentationTime(info.presentationTimeUs * 1000);
                        mEncSurface.swapBuffers();
                    }
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        mSignaledEncoderEOS = true;
                        Log.d(TAG, "signaling encoder EOS");
                        mEncoder.signalEndOfInputStream();
                    }

                    synchronized (mCondition) {
                        mInfoOnSurface = null;
                        if (mBuffersToRender.size() > 0 && mInfoOnSurface == null) {
                            if (DEBUG) Log.v(TAG, "handling postponed frame");
                            Pair<Integer, BufferInfo> nextBuffer = mBuffersToRender.removeFirst();
                            renderDecodedBuffer(nextBuffer.first, nextBuffer.second);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                fail("received exception " + e);
            } finally {
                close();
                if (mEncSurface != null) {
                    mEncSurface.release();
                    mEncSurface = null;
                }
                if (mDecSurface != null) {
                    mDecSurface.release();
                    mDecSurface = null;
                }
            }
            return !skipped;
        }

        @Override
        public void onFrameAvailable(SurfaceTexture st) {
            if (DEBUG) Log.v(TAG, "new frame available");
            synchronized (mCondition) {
                assertFalse("mFrameAvailable already set, frame could be dropped", mFrameAvailable);
                mFrameAvailable = true;
                mCondition.notifyAll();
            }
        }

        @Override
        public void onInputBufferAvailableLocked(MediaCodec mediaCodec, int ix) {
            if (mediaCodec == mDecoder) {
                // fill input buffer from extractor
                fillDecoderInputBuffer(ix);
            } else {
                fail("received input buffer on " + mediaCodec.getName());
            }
        }

        @Override
        public void onOutputBufferAvailableLocked(
                MediaCodec mediaCodec, int ix, BufferInfo info) {
            if (mediaCodec == mDecoder) {
                if (DEBUG) Log.v(TAG, "decoder received output #" + ix
                         + " (sz=" + info.size + ", f=" + info.flags
                         + ", ts=" + info.presentationTimeUs + ")");
                // render output buffer from decoder
                if (!mGotDecoderEOS) {
                    boolean eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    if (eos) {
                        mGotDecoderEOS = true;
                    }
                    // can release empty buffers now
                    if (info.size == 0) {
                        mDecoder.releaseOutputBuffer(ix, false /* render */);
                        ix = -1; // dummy index used by render to not render
                    }
                    if (eos || info.size > 0) {
                        synchronized(mCondition) {
                            if (mInfoOnSurface != null || mBuffersToRender.size() > 0) {
                                if (DEBUG) Log.v(TAG, "postponing render, surface busy");
                                mBuffersToRender.addLast(Pair.create(ix, info));
                            } else {
                                renderDecodedBuffer(ix, info);
                            }
                        }
                    }
                }
            } else if (mediaCodec == mEncoder) {
                emptyEncoderOutputBuffer(ix, info);
                synchronized(mCondition) {
                    if (!mCompleted) {
                        mEncoderIsActive = true;
                        mCondition.notifyAll();
                    }
                }
            } else {
                fail("received output buffer on " + mediaCodec.getName());
            }
        }

        private void renderDecodedBuffer(int ix, BufferInfo info) {
            boolean eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
            mInfoOnSurface = info;
            if (info.size > 0) {
                Log.d(TAG, "rendering frame #" + ix + " at " + info.presentationTimeUs * 1000
                        + (eos ? " with EOS" : ""));
                mDecoder.releaseOutputBuffer(ix, info.presentationTimeUs * 1000);
            }

            if (eos && info.size == 0) {
                if (DEBUG) Log.v(TAG, "decoder output EOS available");
                mFrameAvailable = true;
                mCondition.notifyAll();
            }
        }

        @Override
        public void onError(MediaCodec mediaCodec, MediaCodec.CodecException e) {
            fail("received error on " + mediaCodec.getName() + ": " + e);
        }

        @Override
        public void onOutputFormatChanged(MediaCodec mediaCodec, MediaFormat mediaFormat) {
            Log.i(TAG, mediaCodec.getName() + " got new output format " + mediaFormat);
            if (mediaCodec == mEncoder) {
                mEncodeOutputFormatUpdated = true;
                saveEncoderFormat(mediaFormat);
            }
        }
    }

    class Encoder {
        final private String mName;
        final private String mMime;
        final private CodecCapabilities mCaps;
        final private VideoCapabilities mVideoCaps;

        final private Map<Size, Set<Size>> mMinMax;     // extreme sizes
        final private Map<Size, Set<Size>> mNearMinMax; // sizes near extreme
        final private Set<Size> mArbitraryW;            // arbitrary widths in the middle
        final private Set<Size> mArbitraryH;            // arbitrary heights in the middle
        final private Set<Size> mSizes;                 // all non-specifically tested sizes

        final private int xAlign;
        final private int yAlign;

        Encoder(String name, String mime, CodecCapabilities caps) {
            mName = name;
            mMime = mime;
            mCaps = caps;
            mVideoCaps = caps.getVideoCapabilities();

            /* calculate min/max sizes */
            mMinMax = new HashMap<Size, Set<Size>>();
            mNearMinMax = new HashMap<Size, Set<Size>>();
            mArbitraryW = new HashSet<Size>();
            mArbitraryH = new HashSet<Size>();
            mSizes = new HashSet<Size>();

            xAlign = mVideoCaps.getWidthAlignment();
            yAlign = mVideoCaps.getHeightAlignment();

            initializeSizes();
        }

        private void initializeSizes() {
            for (int x = 0; x < 2; ++x) {
                for (int y = 0; y < 2; ++y) {
                    addExtremeSizesFor(x, y);
                }
            }

            // initialize arbitrary sizes
            for (int i = 1; i <= 7; ++i) {
                int j = ((7 * i) % 11) + 1;
                int width, height;
                try {
                    width = alignedPointInRange(i * 0.125, xAlign, mVideoCaps.getSupportedWidths());
                    height = alignedPointInRange(
                            j * 0.077, yAlign, mVideoCaps.getSupportedHeightsFor(width));
                    mArbitraryW.add(new Size(width, height));
                } catch (IllegalArgumentException e) {
                }

                try {
                    height = alignedPointInRange(i * 0.125, yAlign, mVideoCaps.getSupportedHeights());
                    width = alignedPointInRange(j * 0.077, xAlign, mVideoCaps.getSupportedWidthsFor(height));
                    mArbitraryH.add(new Size(width, height));
                } catch (IllegalArgumentException e) {
                }
            }
            mArbitraryW.removeAll(mArbitraryH);
            mArbitraryW.removeAll(mSizes);
            mSizes.addAll(mArbitraryW);
            mArbitraryH.removeAll(mSizes);
            mSizes.addAll(mArbitraryH);
            if (DEBUG) Log.i(TAG, "arbitrary=" + mArbitraryW + "/" + mArbitraryH);
        }

        private void addExtremeSizesFor(int x, int y) {
            Set<Size> minMax = new HashSet<Size>();
            Set<Size> nearMinMax = new HashSet<Size>();

            for (int dx = 0; dx <= xAlign; dx += xAlign) {
                for (int dy = 0; dy <= yAlign; dy += yAlign) {
                    Set<Size> bucket = (dx + dy == 0) ? minMax : nearMinMax;
                    try {
                        int width = getExtreme(mVideoCaps.getSupportedWidths(), x, dx);
                        int height = getExtreme(mVideoCaps.getSupportedHeightsFor(width), y, dy);
                        bucket.add(new Size(width, height));

                        // try max max with more reasonable ratio if too skewed
                        if (x + y == 2 && width >= 4 * height) {
                            Size wideScreen = getLargestSizeForRatio(16, 9);
                            width = getExtreme(
                                    mVideoCaps.getSupportedWidths()
                                            .intersect(0, wideScreen.getWidth()), x, dx);
                            height = getExtreme(mVideoCaps.getSupportedHeightsFor(width), y, 0);
                            bucket.add(new Size(width, height));
                        }
                    } catch (IllegalArgumentException e) {
                    }

                    try {
                        int height = getExtreme(mVideoCaps.getSupportedHeights(), y, dy);
                        int width = getExtreme(mVideoCaps.getSupportedWidthsFor(height), x, dx);
                        bucket.add(new Size(width, height));

                        // try max max with more reasonable ratio if too skewed
                        if (x + y == 2 && height >= 4 * width) {
                            Size wideScreen = getLargestSizeForRatio(9, 16);
                            height = getExtreme(
                                    mVideoCaps.getSupportedHeights()
                                            .intersect(0, wideScreen.getHeight()), y, dy);
                            width = getExtreme(mVideoCaps.getSupportedWidthsFor(height), x, dx);
                            bucket.add(new Size(width, height));
                        }
                    } catch (IllegalArgumentException e) {
                    }
                }
            }

            // keep unique sizes
            minMax.removeAll(mSizes);
            mSizes.addAll(minMax);
            nearMinMax.removeAll(mSizes);
            mSizes.addAll(nearMinMax);

            mMinMax.put(new Size(x, y), minMax);
            mNearMinMax.put(new Size(x, y), nearMinMax);
            if (DEBUG) Log.i(TAG, x + "x" + y + ": minMax=" + mMinMax + ", near=" + mNearMinMax);
        }

        private int alignInRange(double value, int align, Range<Integer> range) {
            return range.clamp(align * (int)Math.round(value / align));
        }

        /* point should be between 0. and 1. */
        private int alignedPointInRange(double point, int align, Range<Integer> range) {
            return alignInRange(
                    range.getLower() + point * (range.getUpper() - range.getLower()), align, range);
        }

        private int getExtreme(Range<Integer> range, int i, int delta) {
            int dim = i == 1 ? range.getUpper() - delta : range.getLower() + delta;
            if (delta == 0
                    || (dim > range.getLower() && dim < range.getUpper())) {
                return dim;
            }
            throw new IllegalArgumentException();
        }

        private Size getLargestSizeForRatio(int x, int y) {
            Range<Integer> widthRange = mVideoCaps.getSupportedWidths();
            Range<Integer> heightRange = mVideoCaps.getSupportedHeightsFor(widthRange.getUpper());
            final int xAlign = mVideoCaps.getWidthAlignment();
            final int yAlign = mVideoCaps.getHeightAlignment();

            // scale by alignment
            int width = alignInRange(
                    Math.sqrt(widthRange.getUpper() * heightRange.getUpper() * (double)x / y),
                    xAlign, widthRange);
            int height = alignInRange(
                    width * (double)y / x, yAlign, mVideoCaps.getSupportedHeightsFor(width));
            return new Size(width, height);
        }


        public boolean testExtreme(int x, int y, boolean flexYUV, boolean near) {
            boolean skipped = true;
            for (Size s : (near ? mNearMinMax : mMinMax).get(new Size(x, y))) {
                if (test(s.getWidth(), s.getHeight(), false /* optional */, flexYUV)) {
                    skipped = false;
                }
            }
            return !skipped;
        }

        public boolean testArbitrary(boolean flexYUV, boolean widths) {
            boolean skipped = true;
            for (Size s : (widths ? mArbitraryW : mArbitraryH)) {
                if (test(s.getWidth(), s.getHeight(), false /* optional */, flexYUV)) {
                    skipped = false;
                }
            }
            return !skipped;
        }

        public boolean testSpecific(int width, int height, boolean flexYUV) {
            // already tested by one of the min/max tests
            if (mSizes.contains(new Size(width, height))) {
                return false;
            }
            return test(width, height, true /* optional */, flexYUV);
        }

        public boolean testIntraRefresh(int width, int height) {
            if (!mCaps.isFeatureSupported(CodecCapabilities.FEATURE_IntraRefresh)) {
                return false;
            }

            final int refreshPeriod[] = new int[] {10, 13, 17, 22, 29, 38, 50, 60};

            // Test the support of refresh periods in the range of 10 - 60 frames
            for (int period : refreshPeriod) {
                Function<MediaFormat, Boolean> updateConfigFormatHook =
                new Function<MediaFormat, Boolean>() {
                    public Boolean apply(MediaFormat fmt) {
                        // set i-frame-interval to 10000 so encoded video only has 1 i-frame.
                        fmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10000);
                        fmt.setInteger(MediaFormat.KEY_INTRA_REFRESH_PERIOD, period);
                        return true;
                    }
                };

                Function<MediaFormat, Boolean> checkOutputFormatHook =
                new Function<MediaFormat, Boolean>() {
                    public Boolean apply(MediaFormat fmt) {
                        int intraPeriod = fmt.getInteger(MediaFormat.KEY_INTRA_REFRESH_PERIOD);
                        // Make sure intra period is correct and carried in the output format.
                        // intraPeriod must be larger than 0 and operate within 20% of refresh period.
                        if (intraPeriod > 1.2 * period || intraPeriod < 0.8 * period) {
                            throw new RuntimeException("Intra period mismatch");
                        }
                        return true;
                    }
                };

                String testName =
                mName + '_' + width + "x" + height + '_' + "flexYUV_intraRefresh";

                Consumer<VideoProcessorBase> configureVideoProcessor =
                new Consumer<VideoProcessorBase>() {
                    public void accept(VideoProcessorBase processor) {
                        processor.setProcessorName(testName);
                        processor.setUpdateConfigHook(updateConfigFormatHook);
                        processor.setCheckOutputFormatHook(checkOutputFormatHook);
                    }
                };

                if (!test(width, height, 0 /* frameRate */, 0 /* bitRate */, true /* optional */,
                    true /* flex */, configureVideoProcessor)) {
                    return false;
                }
            }

            return true;
        }

        public boolean testDetailed(
                int width, int height, int frameRate, int bitRate, boolean flexYUV) {
            String testName =
                    mName + '_' + width + "x" + height + '_' + (flexYUV ? "flexYUV" : " surface");
            Consumer<VideoProcessorBase> configureVideoProcessor =
                    new Consumer<VideoProcessorBase>() {
                public void accept(VideoProcessorBase processor) {
                    processor.setProcessorName(testName);
                }
            };
            return test(width, height, frameRate, bitRate, true /* optional */, flexYUV,
                    configureVideoProcessor);
        }

        public boolean testSupport(int width, int height, int frameRate, int bitRate) {
            return mVideoCaps.areSizeAndRateSupported(width, height, frameRate) &&
                    mVideoCaps.getBitrateRange().contains(bitRate);
        }

        private boolean test(
                int width, int height, boolean optional, boolean flexYUV) {
            String testName =
                    mName + '_' + width + "x" + height + '_' + (flexYUV ? "flexYUV" : " surface");
            Consumer<VideoProcessorBase> configureVideoProcessor =
                    new Consumer<VideoProcessorBase>() {
                public void accept(VideoProcessorBase processor) {
                    processor.setProcessorName(testName);
                }
            };
            return test(width, height, 0 /* frameRate */, 0 /* bitRate */,
                    optional, flexYUV, configureVideoProcessor);
        }

        private boolean test(
                int width, int height, int frameRate, int bitRate, boolean optional,
                boolean flexYUV, Consumer<VideoProcessorBase> configureVideoProcessor) {
            Log.i(TAG, "testing " + mMime + " on " + mName + " for " + width + "x" + height
                    + (flexYUV ? " flexYUV" : " surface"));

            VideoProcessorBase processor =
                flexYUV ? new VideoProcessor() : new SurfaceVideoProcessor();

            processor.setFrameAndBitRates(frameRate, bitRate);
            configureVideoProcessor.accept(processor);

            // We are using a resource URL as an example
            boolean success = processor.processLoop(
                    SOURCE_URL, mMime, mName, width, height, optional);
            if (success) {
                processor.playBack(getActivity().getSurfaceHolder().getSurface());
            }
            return success;
        }
    }

    private Encoder[] googH265()  { return goog(MediaFormat.MIMETYPE_VIDEO_HEVC); }
    private Encoder[] googH264()  { return goog(MediaFormat.MIMETYPE_VIDEO_AVC); }
    private Encoder[] googH263()  { return goog(MediaFormat.MIMETYPE_VIDEO_H263); }
    private Encoder[] googMpeg4() { return goog(MediaFormat.MIMETYPE_VIDEO_MPEG4); }
    private Encoder[] googVP8()   { return goog(MediaFormat.MIMETYPE_VIDEO_VP8); }
    private Encoder[] googVP9()   { return goog(MediaFormat.MIMETYPE_VIDEO_VP9); }

    private Encoder[] otherH265()  { return other(MediaFormat.MIMETYPE_VIDEO_HEVC); }
    private Encoder[] otherH264()  { return other(MediaFormat.MIMETYPE_VIDEO_AVC); }
    private Encoder[] otherH263()  { return other(MediaFormat.MIMETYPE_VIDEO_H263); }
    private Encoder[] otherMpeg4() { return other(MediaFormat.MIMETYPE_VIDEO_MPEG4); }
    private Encoder[] otherVP8()   { return other(MediaFormat.MIMETYPE_VIDEO_VP8); }
    private Encoder[] otherVP9()   { return other(MediaFormat.MIMETYPE_VIDEO_VP9); }

    private Encoder[] goog(String mime) {
        return encoders(mime, true /* goog */);
    }

    private Encoder[] other(String mime) {
        return encoders(mime, false /* goog */);
    }

    private Encoder[] combineArray(Encoder[] a, Encoder[] b) {
        Encoder[] all = new Encoder[a.length + b.length];
        System.arraycopy(a, 0, all, 0, a.length);
        System.arraycopy(b, 0, all, a.length, b.length);
        return all;
    }

    private Encoder[] h264()  {
        return combineArray(googH264(), otherH264());
    }

    private Encoder[] vp8()  {
        return combineArray(googVP8(), otherVP8());
    }

    private Encoder[] encoders(String mime, boolean goog) {
        MediaCodecList mcl = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        ArrayList<Encoder> result = new ArrayList<Encoder>();

        for (MediaCodecInfo info : mcl.getCodecInfos()) {
            if (!info.isEncoder()
                    || info.getName().toLowerCase().startsWith("omx.google.") != goog) {
                continue;
            }
            CodecCapabilities caps = null;
            try {
                caps = info.getCapabilitiesForType(mime);
            } catch (IllegalArgumentException e) { // mime is not supported
                continue;
            }
            assertNotNull(info.getName() + " capabilties for " + mime + " returned null", caps);
            result.add(new Encoder(info.getName(), mime, caps));
        }
        return result.toArray(new Encoder[result.size()]);
    }

    public void testGoogH265FlexMinMin()   { minmin(googH265(),   true /* flex */); }
    public void testGoogH265SurfMinMin()   { minmin(googH265(),   false /* flex */); }
    public void testGoogH264FlexMinMin()   { minmin(googH264(),   true /* flex */); }
    public void testGoogH264SurfMinMin()   { minmin(googH264(),   false /* flex */); }
    public void testGoogH263FlexMinMin()   { minmin(googH263(),   true /* flex */); }
    public void testGoogH263SurfMinMin()   { minmin(googH263(),   false /* flex */); }
    public void testGoogMpeg4FlexMinMin()  { minmin(googMpeg4(),  true /* flex */); }
    public void testGoogMpeg4SurfMinMin()  { minmin(googMpeg4(),  false /* flex */); }
    public void testGoogVP8FlexMinMin()    { minmin(googVP8(),    true /* flex */); }
    public void testGoogVP8SurfMinMin()    { minmin(googVP8(),    false /* flex */); }
    public void testGoogVP9FlexMinMin()    { minmin(googVP9(),    true /* flex */); }
    public void testGoogVP9SurfMinMin()    { minmin(googVP9(),    false /* flex */); }

    public void testOtherH265FlexMinMin()  { minmin(otherH265(),  true /* flex */); }
    public void testOtherH265SurfMinMin()  { minmin(otherH265(),  false /* flex */); }
    public void testOtherH264FlexMinMin()  { minmin(otherH264(),  true /* flex */); }
    public void testOtherH264SurfMinMin()  { minmin(otherH264(),  false /* flex */); }
    public void testOtherH263FlexMinMin()  { minmin(otherH263(),  true /* flex */); }
    public void testOtherH263SurfMinMin()  { minmin(otherH263(),  false /* flex */); }
    public void testOtherMpeg4FlexMinMin() { minmin(otherMpeg4(), true /* flex */); }
    public void testOtherMpeg4SurfMinMin() { minmin(otherMpeg4(), false /* flex */); }
    public void testOtherVP8FlexMinMin()   { minmin(otherVP8(),   true /* flex */); }
    public void testOtherVP8SurfMinMin()   { minmin(otherVP8(),   false /* flex */); }
    public void testOtherVP9FlexMinMin()   { minmin(otherVP9(),   true /* flex */); }
    public void testOtherVP9SurfMinMin()   { minmin(otherVP9(),   false /* flex */); }

    public void testGoogH265FlexMinMax()   { minmax(googH265(),   true /* flex */); }
    public void testGoogH265SurfMinMax()   { minmax(googH265(),   false /* flex */); }
    public void testGoogH264FlexMinMax()   { minmax(googH264(),   true /* flex */); }
    public void testGoogH264SurfMinMax()   { minmax(googH264(),   false /* flex */); }
    public void testGoogH263FlexMinMax()   { minmax(googH263(),   true /* flex */); }
    public void testGoogH263SurfMinMax()   { minmax(googH263(),   false /* flex */); }
    public void testGoogMpeg4FlexMinMax()  { minmax(googMpeg4(),  true /* flex */); }
    public void testGoogMpeg4SurfMinMax()  { minmax(googMpeg4(),  false /* flex */); }
    public void testGoogVP8FlexMinMax()    { minmax(googVP8(),    true /* flex */); }
    public void testGoogVP8SurfMinMax()    { minmax(googVP8(),    false /* flex */); }
    public void testGoogVP9FlexMinMax()    { minmax(googVP9(),    true /* flex */); }
    public void testGoogVP9SurfMinMax()    { minmax(googVP9(),    false /* flex */); }

    public void testOtherH265FlexMinMax()  { minmax(otherH265(),  true /* flex */); }
    public void testOtherH265SurfMinMax()  { minmax(otherH265(),  false /* flex */); }
    public void testOtherH264FlexMinMax()  { minmax(otherH264(),  true /* flex */); }
    public void testOtherH264SurfMinMax()  { minmax(otherH264(),  false /* flex */); }
    public void testOtherH263FlexMinMax()  { minmax(otherH263(),  true /* flex */); }
    public void testOtherH263SurfMinMax()  { minmax(otherH263(),  false /* flex */); }
    public void testOtherMpeg4FlexMinMax() { minmax(otherMpeg4(), true /* flex */); }
    public void testOtherMpeg4SurfMinMax() { minmax(otherMpeg4(), false /* flex */); }
    public void testOtherVP8FlexMinMax()   { minmax(otherVP8(),   true /* flex */); }
    public void testOtherVP8SurfMinMax()   { minmax(otherVP8(),   false /* flex */); }
    public void testOtherVP9FlexMinMax()   { minmax(otherVP9(),   true /* flex */); }
    public void testOtherVP9SurfMinMax()   { minmax(otherVP9(),   false /* flex */); }

    public void testGoogH265FlexMaxMin()   { maxmin(googH265(),   true /* flex */); }
    public void testGoogH265SurfMaxMin()   { maxmin(googH265(),   false /* flex */); }
    public void testGoogH264FlexMaxMin()   { maxmin(googH264(),   true /* flex */); }
    public void testGoogH264SurfMaxMin()   { maxmin(googH264(),   false /* flex */); }
    public void testGoogH263FlexMaxMin()   { maxmin(googH263(),   true /* flex */); }
    public void testGoogH263SurfMaxMin()   { maxmin(googH263(),   false /* flex */); }
    public void testGoogMpeg4FlexMaxMin()  { maxmin(googMpeg4(),  true /* flex */); }
    public void testGoogMpeg4SurfMaxMin()  { maxmin(googMpeg4(),  false /* flex */); }
    public void testGoogVP8FlexMaxMin()    { maxmin(googVP8(),    true /* flex */); }
    public void testGoogVP8SurfMaxMin()    { maxmin(googVP8(),    false /* flex */); }
    public void testGoogVP9FlexMaxMin()    { maxmin(googVP9(),    true /* flex */); }
    public void testGoogVP9SurfMaxMin()    { maxmin(googVP9(),    false /* flex */); }

    public void testOtherH265FlexMaxMin()  { maxmin(otherH265(),  true /* flex */); }
    public void testOtherH265SurfMaxMin()  { maxmin(otherH265(),  false /* flex */); }
    public void testOtherH264FlexMaxMin()  { maxmin(otherH264(),  true /* flex */); }
    public void testOtherH264SurfMaxMin()  { maxmin(otherH264(),  false /* flex */); }
    public void testOtherH263FlexMaxMin()  { maxmin(otherH263(),  true /* flex */); }
    public void testOtherH263SurfMaxMin()  { maxmin(otherH263(),  false /* flex */); }
    public void testOtherMpeg4FlexMaxMin() { maxmin(otherMpeg4(), true /* flex */); }
    public void testOtherMpeg4SurfMaxMin() { maxmin(otherMpeg4(), false /* flex */); }
    public void testOtherVP8FlexMaxMin()   { maxmin(otherVP8(),   true /* flex */); }
    public void testOtherVP8SurfMaxMin()   { maxmin(otherVP8(),   false /* flex */); }
    public void testOtherVP9FlexMaxMin()   { maxmin(otherVP9(),   true /* flex */); }
    public void testOtherVP9SurfMaxMin()   { maxmin(otherVP9(),   false /* flex */); }

    public void testGoogH265FlexMaxMax()   { maxmax(googH265(),   true /* flex */); }
    public void testGoogH265SurfMaxMax()   { maxmax(googH265(),   false /* flex */); }
    public void testGoogH264FlexMaxMax()   { maxmax(googH264(),   true /* flex */); }
    public void testGoogH264SurfMaxMax()   { maxmax(googH264(),   false /* flex */); }
    public void testGoogH263FlexMaxMax()   { maxmax(googH263(),   true /* flex */); }
    public void testGoogH263SurfMaxMax()   { maxmax(googH263(),   false /* flex */); }
    public void testGoogMpeg4FlexMaxMax()  { maxmax(googMpeg4(),  true /* flex */); }
    public void testGoogMpeg4SurfMaxMax()  { maxmax(googMpeg4(),  false /* flex */); }
    public void testGoogVP8FlexMaxMax()    { maxmax(googVP8(),    true /* flex */); }
    public void testGoogVP8SurfMaxMax()    { maxmax(googVP8(),    false /* flex */); }
    public void testGoogVP9FlexMaxMax()    { maxmax(googVP9(),    true /* flex */); }
    public void testGoogVP9SurfMaxMax()    { maxmax(googVP9(),    false /* flex */); }

    public void testOtherH265FlexMaxMax()  { maxmax(otherH265(),  true /* flex */); }
    public void testOtherH265SurfMaxMax()  { maxmax(otherH265(),  false /* flex */); }
    public void testOtherH264FlexMaxMax()  { maxmax(otherH264(),  true /* flex */); }
    public void testOtherH264SurfMaxMax()  { maxmax(otherH264(),  false /* flex */); }
    public void testOtherH263FlexMaxMax()  { maxmax(otherH263(),  true /* flex */); }
    public void testOtherH263SurfMaxMax()  { maxmax(otherH263(),  false /* flex */); }
    public void testOtherMpeg4FlexMaxMax() { maxmax(otherMpeg4(), true /* flex */); }
    public void testOtherMpeg4SurfMaxMax() { maxmax(otherMpeg4(), false /* flex */); }
    public void testOtherVP8FlexMaxMax()   { maxmax(otherVP8(),   true /* flex */); }
    public void testOtherVP8SurfMaxMax()   { maxmax(otherVP8(),   false /* flex */); }
    public void testOtherVP9FlexMaxMax()   { maxmax(otherVP9(),   true /* flex */); }
    public void testOtherVP9SurfMaxMax()   { maxmax(otherVP9(),   false /* flex */); }

    public void testGoogH265FlexNearMinMin()   { nearminmin(googH265(),   true /* flex */); }
    public void testGoogH265SurfNearMinMin()   { nearminmin(googH265(),   false /* flex */); }
    public void testGoogH264FlexNearMinMin()   { nearminmin(googH264(),   true /* flex */); }
    public void testGoogH264SurfNearMinMin()   { nearminmin(googH264(),   false /* flex */); }
    public void testGoogH263FlexNearMinMin()   { nearminmin(googH263(),   true /* flex */); }
    public void testGoogH263SurfNearMinMin()   { nearminmin(googH263(),   false /* flex */); }
    public void testGoogMpeg4FlexNearMinMin()  { nearminmin(googMpeg4(),  true /* flex */); }
    public void testGoogMpeg4SurfNearMinMin()  { nearminmin(googMpeg4(),  false /* flex */); }
    public void testGoogVP8FlexNearMinMin()    { nearminmin(googVP8(),    true /* flex */); }
    public void testGoogVP8SurfNearMinMin()    { nearminmin(googVP8(),    false /* flex */); }
    public void testGoogVP9FlexNearMinMin()    { nearminmin(googVP9(),    true /* flex */); }
    public void testGoogVP9SurfNearMinMin()    { nearminmin(googVP9(),    false /* flex */); }

    public void testOtherH265FlexNearMinMin()  { nearminmin(otherH265(),  true /* flex */); }
    public void testOtherH265SurfNearMinMin()  { nearminmin(otherH265(),  false /* flex */); }
    public void testOtherH264FlexNearMinMin()  { nearminmin(otherH264(),  true /* flex */); }
    public void testOtherH264SurfNearMinMin()  { nearminmin(otherH264(),  false /* flex */); }
    public void testOtherH263FlexNearMinMin()  { nearminmin(otherH263(),  true /* flex */); }
    public void testOtherH263SurfNearMinMin()  { nearminmin(otherH263(),  false /* flex */); }
    public void testOtherMpeg4FlexNearMinMin() { nearminmin(otherMpeg4(), true /* flex */); }
    public void testOtherMpeg4SurfNearMinMin() { nearminmin(otherMpeg4(), false /* flex */); }
    public void testOtherVP8FlexNearMinMin()   { nearminmin(otherVP8(),   true /* flex */); }
    public void testOtherVP8SurfNearMinMin()   { nearminmin(otherVP8(),   false /* flex */); }
    public void testOtherVP9FlexNearMinMin()   { nearminmin(otherVP9(),   true /* flex */); }
    public void testOtherVP9SurfNearMinMin()   { nearminmin(otherVP9(),   false /* flex */); }

    public void testGoogH265FlexNearMinMax()   { nearminmax(googH265(),   true /* flex */); }
    public void testGoogH265SurfNearMinMax()   { nearminmax(googH265(),   false /* flex */); }
    public void testGoogH264FlexNearMinMax()   { nearminmax(googH264(),   true /* flex */); }
    public void testGoogH264SurfNearMinMax()   { nearminmax(googH264(),   false /* flex */); }
    public void testGoogH263FlexNearMinMax()   { nearminmax(googH263(),   true /* flex */); }
    public void testGoogH263SurfNearMinMax()   { nearminmax(googH263(),   false /* flex */); }
    public void testGoogMpeg4FlexNearMinMax()  { nearminmax(googMpeg4(),  true /* flex */); }
    public void testGoogMpeg4SurfNearMinMax()  { nearminmax(googMpeg4(),  false /* flex */); }
    public void testGoogVP8FlexNearMinMax()    { nearminmax(googVP8(),    true /* flex */); }
    public void testGoogVP8SurfNearMinMax()    { nearminmax(googVP8(),    false /* flex */); }
    public void testGoogVP9FlexNearMinMax()    { nearminmax(googVP9(),    true /* flex */); }
    public void testGoogVP9SurfNearMinMax()    { nearminmax(googVP9(),    false /* flex */); }

    public void testOtherH265FlexNearMinMax()  { nearminmax(otherH265(),  true /* flex */); }
    public void testOtherH265SurfNearMinMax()  { nearminmax(otherH265(),  false /* flex */); }
    public void testOtherH264FlexNearMinMax()  { nearminmax(otherH264(),  true /* flex */); }
    public void testOtherH264SurfNearMinMax()  { nearminmax(otherH264(),  false /* flex */); }
    public void testOtherH263FlexNearMinMax()  { nearminmax(otherH263(),  true /* flex */); }
    public void testOtherH263SurfNearMinMax()  { nearminmax(otherH263(),  false /* flex */); }
    public void testOtherMpeg4FlexNearMinMax() { nearminmax(otherMpeg4(), true /* flex */); }
    public void testOtherMpeg4SurfNearMinMax() { nearminmax(otherMpeg4(), false /* flex */); }
    public void testOtherVP8FlexNearMinMax()   { nearminmax(otherVP8(),   true /* flex */); }
    public void testOtherVP8SurfNearMinMax()   { nearminmax(otherVP8(),   false /* flex */); }
    public void testOtherVP9FlexNearMinMax()   { nearminmax(otherVP9(),   true /* flex */); }
    public void testOtherVP9SurfNearMinMax()   { nearminmax(otherVP9(),   false /* flex */); }

    public void testGoogH265FlexNearMaxMin()   { nearmaxmin(googH265(),   true /* flex */); }
    public void testGoogH265SurfNearMaxMin()   { nearmaxmin(googH265(),   false /* flex */); }
    public void testGoogH264FlexNearMaxMin()   { nearmaxmin(googH264(),   true /* flex */); }
    public void testGoogH264SurfNearMaxMin()   { nearmaxmin(googH264(),   false /* flex */); }
    public void testGoogH263FlexNearMaxMin()   { nearmaxmin(googH263(),   true /* flex */); }
    public void testGoogH263SurfNearMaxMin()   { nearmaxmin(googH263(),   false /* flex */); }
    public void testGoogMpeg4FlexNearMaxMin()  { nearmaxmin(googMpeg4(),  true /* flex */); }
    public void testGoogMpeg4SurfNearMaxMin()  { nearmaxmin(googMpeg4(),  false /* flex */); }
    public void testGoogVP8FlexNearMaxMin()    { nearmaxmin(googVP8(),    true /* flex */); }
    public void testGoogVP8SurfNearMaxMin()    { nearmaxmin(googVP8(),    false /* flex */); }
    public void testGoogVP9FlexNearMaxMin()    { nearmaxmin(googVP9(),    true /* flex */); }
    public void testGoogVP9SurfNearMaxMin()    { nearmaxmin(googVP9(),    false /* flex */); }

    public void testOtherH265FlexNearMaxMin()  { nearmaxmin(otherH265(),  true /* flex */); }
    public void testOtherH265SurfNearMaxMin()  { nearmaxmin(otherH265(),  false /* flex */); }
    public void testOtherH264FlexNearMaxMin()  { nearmaxmin(otherH264(),  true /* flex */); }
    public void testOtherH264SurfNearMaxMin()  { nearmaxmin(otherH264(),  false /* flex */); }
    public void testOtherH263FlexNearMaxMin()  { nearmaxmin(otherH263(),  true /* flex */); }
    public void testOtherH263SurfNearMaxMin()  { nearmaxmin(otherH263(),  false /* flex */); }
    public void testOtherMpeg4FlexNearMaxMin() { nearmaxmin(otherMpeg4(), true /* flex */); }
    public void testOtherMpeg4SurfNearMaxMin() { nearmaxmin(otherMpeg4(), false /* flex */); }
    public void testOtherVP8FlexNearMaxMin()   { nearmaxmin(otherVP8(),   true /* flex */); }
    public void testOtherVP8SurfNearMaxMin()   { nearmaxmin(otherVP8(),   false /* flex */); }
    public void testOtherVP9FlexNearMaxMin()   { nearmaxmin(otherVP9(),   true /* flex */); }
    public void testOtherVP9SurfNearMaxMin()   { nearmaxmin(otherVP9(),   false /* flex */); }

    public void testGoogH265FlexNearMaxMax()   { nearmaxmax(googH265(),   true /* flex */); }
    public void testGoogH265SurfNearMaxMax()   { nearmaxmax(googH265(),   false /* flex */); }
    public void testGoogH264FlexNearMaxMax()   { nearmaxmax(googH264(),   true /* flex */); }
    public void testGoogH264SurfNearMaxMax()   { nearmaxmax(googH264(),   false /* flex */); }
    public void testGoogH263FlexNearMaxMax()   { nearmaxmax(googH263(),   true /* flex */); }
    public void testGoogH263SurfNearMaxMax()   { nearmaxmax(googH263(),   false /* flex */); }
    public void testGoogMpeg4FlexNearMaxMax()  { nearmaxmax(googMpeg4(),  true /* flex */); }
    public void testGoogMpeg4SurfNearMaxMax()  { nearmaxmax(googMpeg4(),  false /* flex */); }
    public void testGoogVP8FlexNearMaxMax()    { nearmaxmax(googVP8(),    true /* flex */); }
    public void testGoogVP8SurfNearMaxMax()    { nearmaxmax(googVP8(),    false /* flex */); }
    public void testGoogVP9FlexNearMaxMax()    { nearmaxmax(googVP9(),    true /* flex */); }
    public void testGoogVP9SurfNearMaxMax()    { nearmaxmax(googVP9(),    false /* flex */); }

    public void testOtherH265FlexNearMaxMax()  { nearmaxmax(otherH265(),  true /* flex */); }
    public void testOtherH265SurfNearMaxMax()  { nearmaxmax(otherH265(),  false /* flex */); }
    public void testOtherH264FlexNearMaxMax()  { nearmaxmax(otherH264(),  true /* flex */); }
    public void testOtherH264SurfNearMaxMax()  { nearmaxmax(otherH264(),  false /* flex */); }
    public void testOtherH263FlexNearMaxMax()  { nearmaxmax(otherH263(),  true /* flex */); }
    public void testOtherH263SurfNearMaxMax()  { nearmaxmax(otherH263(),  false /* flex */); }
    public void testOtherMpeg4FlexNearMaxMax() { nearmaxmax(otherMpeg4(), true /* flex */); }
    public void testOtherMpeg4SurfNearMaxMax() { nearmaxmax(otherMpeg4(), false /* flex */); }
    public void testOtherVP8FlexNearMaxMax()   { nearmaxmax(otherVP8(),   true /* flex */); }
    public void testOtherVP8SurfNearMaxMax()   { nearmaxmax(otherVP8(),   false /* flex */); }
    public void testOtherVP9FlexNearMaxMax()   { nearmaxmax(otherVP9(),   true /* flex */); }
    public void testOtherVP9SurfNearMaxMax()   { nearmaxmax(otherVP9(),   false /* flex */); }

    public void testGoogH265FlexArbitraryW()   { arbitraryw(googH265(),   true /* flex */); }
    public void testGoogH265SurfArbitraryW()   { arbitraryw(googH265(),   false /* flex */); }
    public void testGoogH264FlexArbitraryW()   { arbitraryw(googH264(),   true /* flex */); }
    public void testGoogH264SurfArbitraryW()   { arbitraryw(googH264(),   false /* flex */); }
    public void testGoogH263FlexArbitraryW()   { arbitraryw(googH263(),   true /* flex */); }
    public void testGoogH263SurfArbitraryW()   { arbitraryw(googH263(),   false /* flex */); }
    public void testGoogMpeg4FlexArbitraryW()  { arbitraryw(googMpeg4(),  true /* flex */); }
    public void testGoogMpeg4SurfArbitraryW()  { arbitraryw(googMpeg4(),  false /* flex */); }
    public void testGoogVP8FlexArbitraryW()    { arbitraryw(googVP8(),    true /* flex */); }
    public void testGoogVP8SurfArbitraryW()    { arbitraryw(googVP8(),    false /* flex */); }
    public void testGoogVP9FlexArbitraryW()    { arbitraryw(googVP9(),    true /* flex */); }
    public void testGoogVP9SurfArbitraryW()    { arbitraryw(googVP9(),    false /* flex */); }

    public void testOtherH265FlexArbitraryW()  { arbitraryw(otherH265(),  true /* flex */); }
    public void testOtherH265SurfArbitraryW()  { arbitraryw(otherH265(),  false /* flex */); }
    public void testOtherH264FlexArbitraryW()  { arbitraryw(otherH264(),  true /* flex */); }
    public void testOtherH264SurfArbitraryW()  { arbitraryw(otherH264(),  false /* flex */); }
    public void testOtherH263FlexArbitraryW()  { arbitraryw(otherH263(),  true /* flex */); }
    public void testOtherH263SurfArbitraryW()  { arbitraryw(otherH263(),  false /* flex */); }
    public void testOtherMpeg4FlexArbitraryW() { arbitraryw(otherMpeg4(), true /* flex */); }
    public void testOtherMpeg4SurfArbitraryW() { arbitraryw(otherMpeg4(), false /* flex */); }
    public void testOtherVP8FlexArbitraryW()   { arbitraryw(otherVP8(),   true /* flex */); }
    public void testOtherVP8SurfArbitraryW()   { arbitraryw(otherVP8(),   false /* flex */); }
    public void testOtherVP9FlexArbitraryW()   { arbitraryw(otherVP9(),   true /* flex */); }
    public void testOtherVP9SurfArbitraryW()   { arbitraryw(otherVP9(),   false /* flex */); }

    public void testGoogH265FlexArbitraryH()   { arbitraryh(googH265(),   true /* flex */); }
    public void testGoogH265SurfArbitraryH()   { arbitraryh(googH265(),   false /* flex */); }
    public void testGoogH264FlexArbitraryH()   { arbitraryh(googH264(),   true /* flex */); }
    public void testGoogH264SurfArbitraryH()   { arbitraryh(googH264(),   false /* flex */); }
    public void testGoogH263FlexArbitraryH()   { arbitraryh(googH263(),   true /* flex */); }
    public void testGoogH263SurfArbitraryH()   { arbitraryh(googH263(),   false /* flex */); }
    public void testGoogMpeg4FlexArbitraryH()  { arbitraryh(googMpeg4(),  true /* flex */); }
    public void testGoogMpeg4SurfArbitraryH()  { arbitraryh(googMpeg4(),  false /* flex */); }
    public void testGoogVP8FlexArbitraryH()    { arbitraryh(googVP8(),    true /* flex */); }
    public void testGoogVP8SurfArbitraryH()    { arbitraryh(googVP8(),    false /* flex */); }
    public void testGoogVP9FlexArbitraryH()    { arbitraryh(googVP9(),    true /* flex */); }
    public void testGoogVP9SurfArbitraryH()    { arbitraryh(googVP9(),    false /* flex */); }

    public void testOtherH265FlexArbitraryH()  { arbitraryh(otherH265(),  true /* flex */); }
    public void testOtherH265SurfArbitraryH()  { arbitraryh(otherH265(),  false /* flex */); }
    public void testOtherH264FlexArbitraryH()  { arbitraryh(otherH264(),  true /* flex */); }
    public void testOtherH264SurfArbitraryH()  { arbitraryh(otherH264(),  false /* flex */); }
    public void testOtherH263FlexArbitraryH()  { arbitraryh(otherH263(),  true /* flex */); }
    public void testOtherH263SurfArbitraryH()  { arbitraryh(otherH263(),  false /* flex */); }
    public void testOtherMpeg4FlexArbitraryH() { arbitraryh(otherMpeg4(), true /* flex */); }
    public void testOtherMpeg4SurfArbitraryH() { arbitraryh(otherMpeg4(), false /* flex */); }
    public void testOtherVP8FlexArbitraryH()   { arbitraryh(otherVP8(),   true /* flex */); }
    public void testOtherVP8SurfArbitraryH()   { arbitraryh(otherVP8(),   false /* flex */); }
    public void testOtherVP9FlexArbitraryH()   { arbitraryh(otherVP9(),   true /* flex */); }
    public void testOtherVP9SurfArbitraryH()   { arbitraryh(otherVP9(),   false /* flex */); }

    public void testGoogH265FlexQCIF()   { specific(googH265(),   176, 144, true /* flex */); }
    public void testGoogH265SurfQCIF()   { specific(googH265(),   176, 144, false /* flex */); }
    public void testGoogH264FlexQCIF()   { specific(googH264(),   176, 144, true /* flex */); }
    public void testGoogH264SurfQCIF()   { specific(googH264(),   176, 144, false /* flex */); }
    public void testGoogH263FlexQCIF()   { specific(googH263(),   176, 144, true /* flex */); }
    public void testGoogH263SurfQCIF()   { specific(googH263(),   176, 144, false /* flex */); }
    public void testGoogMpeg4FlexQCIF()  { specific(googMpeg4(),  176, 144, true /* flex */); }
    public void testGoogMpeg4SurfQCIF()  { specific(googMpeg4(),  176, 144, false /* flex */); }
    public void testGoogVP8FlexQCIF()    { specific(googVP8(),    176, 144, true /* flex */); }
    public void testGoogVP8SurfQCIF()    { specific(googVP8(),    176, 144, false /* flex */); }
    public void testGoogVP9FlexQCIF()    { specific(googVP9(),    176, 144, true /* flex */); }
    public void testGoogVP9SurfQCIF()    { specific(googVP9(),    176, 144, false /* flex */); }

    public void testOtherH265FlexQCIF()  { specific(otherH265(),  176, 144, true /* flex */); }
    public void testOtherH265SurfQCIF()  { specific(otherH265(),  176, 144, false /* flex */); }
    public void testOtherH264FlexQCIF()  { specific(otherH264(),  176, 144, true /* flex */); }
    public void testOtherH264SurfQCIF()  { specific(otherH264(),  176, 144, false /* flex */); }
    public void testOtherH263FlexQCIF()  { specific(otherH263(),  176, 144, true /* flex */); }
    public void testOtherH263SurfQCIF()  { specific(otherH263(),  176, 144, false /* flex */); }
    public void testOtherMpeg4FlexQCIF() { specific(otherMpeg4(), 176, 144, true /* flex */); }
    public void testOtherMpeg4SurfQCIF() { specific(otherMpeg4(), 176, 144, false /* flex */); }
    public void testOtherVP8FlexQCIF()   { specific(otherVP8(),   176, 144, true /* flex */); }
    public void testOtherVP8SurfQCIF()   { specific(otherVP8(),   176, 144, false /* flex */); }
    public void testOtherVP9FlexQCIF()   { specific(otherVP9(),   176, 144, true /* flex */); }
    public void testOtherVP9SurfQCIF()   { specific(otherVP9(),   176, 144, false /* flex */); }

    public void testGoogH265Flex480p()   { specific(googH265(),   720, 480, true /* flex */); }
    public void testGoogH265Surf480p()   { specific(googH265(),   720, 480, false /* flex */); }
    public void testGoogH264Flex480p()   { specific(googH264(),   720, 480, true /* flex */); }
    public void testGoogH264Surf480p()   { specific(googH264(),   720, 480, false /* flex */); }
    public void testGoogH263Flex480p()   { specific(googH263(),   720, 480, true /* flex */); }
    public void testGoogH263Surf480p()   { specific(googH263(),   720, 480, false /* flex */); }
    public void testGoogMpeg4Flex480p()  { specific(googMpeg4(),  720, 480, true /* flex */); }
    public void testGoogMpeg4Surf480p()  { specific(googMpeg4(),  720, 480, false /* flex */); }
    public void testGoogVP8Flex480p()    { specific(googVP8(),    720, 480, true /* flex */); }
    public void testGoogVP8Surf480p()    { specific(googVP8(),    720, 480, false /* flex */); }
    public void testGoogVP9Flex480p()    { specific(googVP9(),    720, 480, true /* flex */); }
    public void testGoogVP9Surf480p()    { specific(googVP9(),    720, 480, false /* flex */); }

    public void testOtherH265Flex480p()  { specific(otherH265(),  720, 480, true /* flex */); }
    public void testOtherH265Surf480p()  { specific(otherH265(),  720, 480, false /* flex */); }
    public void testOtherH264Flex480p()  { specific(otherH264(),  720, 480, true /* flex */); }
    public void testOtherH264Surf480p()  { specific(otherH264(),  720, 480, false /* flex */); }
    public void testOtherH263Flex480p()  { specific(otherH263(),  720, 480, true /* flex */); }
    public void testOtherH263Surf480p()  { specific(otherH263(),  720, 480, false /* flex */); }
    public void testOtherMpeg4Flex480p() { specific(otherMpeg4(), 720, 480, true /* flex */); }
    public void testOtherMpeg4Surf480p() { specific(otherMpeg4(), 720, 480, false /* flex */); }
    public void testOtherVP8Flex480p()   { specific(otherVP8(),   720, 480, true /* flex */); }
    public void testOtherVP8Surf480p()   { specific(otherVP8(),   720, 480, false /* flex */); }
    public void testOtherVP9Flex480p()   { specific(otherVP9(),   720, 480, true /* flex */); }
    public void testOtherVP9Surf480p()   { specific(otherVP9(),   720, 480, false /* flex */); }

    // even though H.263 and MPEG-4 are not defined for 720p or 1080p
    // test for it, in case device claims support for it.

    public void testGoogH265Flex720p()   { specific(googH265(),   1280, 720, true /* flex */); }
    public void testGoogH265Surf720p()   { specific(googH265(),   1280, 720, false /* flex */); }
    public void testGoogH264Flex720p()   { specific(googH264(),   1280, 720, true /* flex */); }
    public void testGoogH264Surf720p()   { specific(googH264(),   1280, 720, false /* flex */); }
    public void testGoogH263Flex720p()   { specific(googH263(),   1280, 720, true /* flex */); }
    public void testGoogH263Surf720p()   { specific(googH263(),   1280, 720, false /* flex */); }
    public void testGoogMpeg4Flex720p()  { specific(googMpeg4(),  1280, 720, true /* flex */); }
    public void testGoogMpeg4Surf720p()  { specific(googMpeg4(),  1280, 720, false /* flex */); }
    public void testGoogVP8Flex720p()    { specific(googVP8(),    1280, 720, true /* flex */); }
    public void testGoogVP8Surf720p()    { specific(googVP8(),    1280, 720, false /* flex */); }
    public void testGoogVP9Flex720p()    { specific(googVP9(),    1280, 720, true /* flex */); }
    public void testGoogVP9Surf720p()    { specific(googVP9(),    1280, 720, false /* flex */); }

    public void testOtherH265Flex720p()  { specific(otherH265(),  1280, 720, true /* flex */); }
    public void testOtherH265Surf720p()  { specific(otherH265(),  1280, 720, false /* flex */); }
    public void testOtherH264Flex720p()  { specific(otherH264(),  1280, 720, true /* flex */); }
    public void testOtherH264Surf720p()  { specific(otherH264(),  1280, 720, false /* flex */); }
    public void testOtherH263Flex720p()  { specific(otherH263(),  1280, 720, true /* flex */); }
    public void testOtherH263Surf720p()  { specific(otherH263(),  1280, 720, false /* flex */); }
    public void testOtherMpeg4Flex720p() { specific(otherMpeg4(), 1280, 720, true /* flex */); }
    public void testOtherMpeg4Surf720p() { specific(otherMpeg4(), 1280, 720, false /* flex */); }
    public void testOtherVP8Flex720p()   { specific(otherVP8(),   1280, 720, true /* flex */); }
    public void testOtherVP8Surf720p()   { specific(otherVP8(),   1280, 720, false /* flex */); }
    public void testOtherVP9Flex720p()   { specific(otherVP9(),   1280, 720, true /* flex */); }
    public void testOtherVP9Surf720p()   { specific(otherVP9(),   1280, 720, false /* flex */); }

    public void testGoogH265Flex1080p()   { specific(googH265(),   1920, 1080, true /* flex */); }
    public void testGoogH265Surf1080p()   { specific(googH265(),   1920, 1080, false /* flex */); }
    public void testGoogH264Flex1080p()   { specific(googH264(),   1920, 1080, true /* flex */); }
    public void testGoogH264Surf1080p()   { specific(googH264(),   1920, 1080, false /* flex */); }
    public void testGoogH263Flex1080p()   { specific(googH263(),   1920, 1080, true /* flex */); }
    public void testGoogH263Surf1080p()   { specific(googH263(),   1920, 1080, false /* flex */); }
    public void testGoogMpeg4Flex1080p()  { specific(googMpeg4(),  1920, 1080, true /* flex */); }
    public void testGoogMpeg4Surf1080p()  { specific(googMpeg4(),  1920, 1080, false /* flex */); }
    public void testGoogVP8Flex1080p()    { specific(googVP8(),    1920, 1080, true /* flex */); }
    public void testGoogVP8Surf1080p()    { specific(googVP8(),    1920, 1080, false /* flex */); }
    public void testGoogVP9Flex1080p()    { specific(googVP9(),    1920, 1080, true /* flex */); }
    public void testGoogVP9Surf1080p()    { specific(googVP9(),    1920, 1080, false /* flex */); }

    public void testOtherH265Flex1080p()  { specific(otherH265(),  1920, 1080, true /* flex */); }
    public void testOtherH265Surf1080p()  { specific(otherH265(),  1920, 1080, false /* flex */); }
    public void testOtherH264Flex1080p()  { specific(otherH264(),  1920, 1080, true /* flex */); }
    public void testOtherH264Surf1080p()  { specific(otherH264(),  1920, 1080, false /* flex */); }
    public void testOtherH263Flex1080p()  { specific(otherH263(),  1920, 1080, true /* flex */); }
    public void testOtherH263Surf1080p()  { specific(otherH263(),  1920, 1080, false /* flex */); }
    public void testOtherMpeg4Flex1080p() { specific(otherMpeg4(), 1920, 1080, true /* flex */); }
    public void testOtherMpeg4Surf1080p() { specific(otherMpeg4(), 1920, 1080, false /* flex */); }
    public void testOtherVP8Flex1080p()   { specific(otherVP8(),   1920, 1080, true /* flex */); }
    public void testOtherVP8Surf1080p()   { specific(otherVP8(),   1920, 1080, false /* flex */); }
    public void testOtherVP9Flex1080p()   { specific(otherVP9(),   1920, 1080, true /* flex */); }
    public void testOtherVP9Surf1080p()   { specific(otherVP9(),   1920, 1080, false /* flex */); }

    public void testGoogH265Flex360pWithIntraRefresh() {
        intraRefresh(googH265(), 480, 360);
    }

    public void testGoogH264Flex360pWithIntraRefresh() {
        intraRefresh(googH264(), 480, 360);
    }

    public void testGoogH263Flex360pWithIntraRefresh() {
        intraRefresh(googH263(), 480, 360);
    }

    public void testGoogMpeg4Flex360pWithIntraRefresh() {
        intraRefresh(googMpeg4(), 480, 360);
    }

    public void testGoogVP8Flex360pWithIntraRefresh() {
        intraRefresh(googVP8(), 480, 360);
    }

    public void testOtherH265Flex360pWithIntraRefresh() {
        intraRefresh(otherH265(), 480, 360);
    }

    public void testOtherH264Flex360pWithIntraRefresh() {
        intraRefresh(otherH264(), 480, 360);
    }

    public void testOtherH263FlexQCIFWithIntraRefresh() {
        intraRefresh(otherH263(), 176, 120);
    }

    public void testOtherMpeg4Flex360pWithIntraRefresh() {
        intraRefresh(otherMpeg4(), 480, 360);
    }

    public void testOtherVP8Flex360pWithIntraRefresh() {
        intraRefresh(otherVP8(), 480, 360);
    }

    // Tests encoder profiles required by CDD.
    // H264
    public void testH264LowQualitySDSupport()   {
        support(h264(), 320, 240, 20, 384 * 1000);
    }

    public void testH264HighQualitySDSupport()   {
        support(h264(), 720, 480, 30, 2 * 1000000);
    }

    public void testH264FlexQVGA20fps384kbps()   {
        detailed(h264(), 320, 240, 20, 384 * 1000, true /* flex */);
    }

    public void testH264SurfQVGA20fps384kbps()   {
        detailed(h264(), 320, 240, 20, 384 * 1000, false /* flex */);
    }

    public void testH264Flex480p30fps2Mbps()   {
        detailed(h264(), 720, 480, 30, 2 * 1000000, true /* flex */);
    }

    public void testH264Surf480p30fps2Mbps()   {
        detailed(h264(), 720, 480, 30, 2 * 1000000, false /* flex */);
    }

    public void testH264Flex720p30fps4Mbps()   {
        detailed(h264(), 1280, 720, 30, 4 * 1000000, true /* flex */);
    }

    public void testH264Surf720p30fps4Mbps()   {
        detailed(h264(), 1280, 720, 30, 4 * 1000000, false /* flex */);
    }

    public void testH264Flex1080p30fps10Mbps()   {
        detailed(h264(), 1920, 1080, 30, 10 * 1000000, true /* flex */);
    }

    public void testH264Surf1080p30fps10Mbps()   {
        detailed(h264(), 1920, 1080, 30, 10 * 1000000, false /* flex */);
    }

    // VP8
    public void testVP8LowQualitySDSupport()   {
        support(vp8(), 320, 180, 30, 800 * 1000);
    }

    public void testVP8HighQualitySDSupport()   {
        support(vp8(), 640, 360, 30, 2 * 1000000);
    }

    public void testVP8Flex180p30fps800kbps()   {
        detailed(vp8(), 320, 180, 30, 800 * 1000, true /* flex */);
    }

    public void testVP8Surf180p30fps800kbps()   {
        detailed(vp8(), 320, 180, 30, 800 * 1000, false /* flex */);
    }

    public void testVP8Flex360p30fps2Mbps()   {
        detailed(vp8(), 640, 360, 30, 2 * 1000000, true /* flex */);
    }

    public void testVP8Surf360p30fps2Mbps()   {
        detailed(vp8(), 640, 360, 30, 2 * 1000000, false /* flex */);
    }

    public void testVP8Flex720p30fps4Mbps()   {
        detailed(vp8(), 1280, 720, 30, 4 * 1000000, true /* flex */);
    }

    public void testVP8Surf720p30fps4Mbps()   {
        detailed(vp8(), 1280, 720, 30, 4 * 1000000, false /* flex */);
    }

    public void testVP8Flex1080p30fps10Mbps()   {
        detailed(vp8(), 1920, 1080, 30, 10 * 1000000, true /* flex */);
    }

    public void testVP8Surf1080p30fps10Mbps()   {
        detailed(vp8(), 1920, 1080, 30, 10 * 1000000, false /* flex */);
    }

    private void minmin(Encoder[] encoders, boolean flexYUV) {
        extreme(encoders, 0 /* x */, 0 /* y */, flexYUV, false /* near */);
    }

    private void minmax(Encoder[] encoders, boolean flexYUV) {
        extreme(encoders, 0 /* x */, 1 /* y */, flexYUV, false /* near */);
    }

    private void maxmin(Encoder[] encoders, boolean flexYUV) {
        extreme(encoders, 1 /* x */, 0 /* y */, flexYUV, false /* near */);
    }

    private void maxmax(Encoder[] encoders, boolean flexYUV) {
        extreme(encoders, 1 /* x */, 1 /* y */, flexYUV, false /* near */);
    }

    private void nearminmin(Encoder[] encoders, boolean flexYUV) {
        extreme(encoders, 0 /* x */, 0 /* y */, flexYUV, true /* near */);
    }

    private void nearminmax(Encoder[] encoders, boolean flexYUV) {
        extreme(encoders, 0 /* x */, 1 /* y */, flexYUV, true /* near */);
    }

    private void nearmaxmin(Encoder[] encoders, boolean flexYUV) {
        extreme(encoders, 1 /* x */, 0 /* y */, flexYUV, true /* near */);
    }

    private void nearmaxmax(Encoder[] encoders, boolean flexYUV) {
        extreme(encoders, 1 /* x */, 1 /* y */, flexYUV, true /* near */);
    }

    private void extreme(Encoder[] encoders, int x, int y, boolean flexYUV, boolean near) {
        boolean skipped = true;
        if (encoders.length == 0) {
            MediaUtils.skipTest("no such encoder present");
            return;
        }
        for (Encoder encoder: encoders) {
            if (encoder.testExtreme(x, y, flexYUV, near)) {
                skipped = false;
            }
        }
        if (skipped) {
            MediaUtils.skipTest("duplicate resolution extreme");
        }
    }

    private void arbitrary(Encoder[] encoders, boolean flexYUV, boolean widths) {
        boolean skipped = true;
        if (encoders.length == 0) {
            MediaUtils.skipTest("no such encoder present");
            return;
        }
        for (Encoder encoder: encoders) {
            if (encoder.testArbitrary(flexYUV, widths)) {
                skipped = false;
            }
        }
        if (skipped) {
            MediaUtils.skipTest("duplicate resolution");
        }
    }

    private void arbitraryw(Encoder[] encoders, boolean flexYUV) {
        arbitrary(encoders, flexYUV, true /* widths */);
    }

    private void arbitraryh(Encoder[] encoders, boolean flexYUV) {
        arbitrary(encoders, flexYUV, false /* widths */);
    }

    /* test specific size */
    private void specific(Encoder[] encoders, int width, int height, boolean flexYUV) {
        boolean skipped = true;
        if (encoders.length == 0) {
            MediaUtils.skipTest("no such encoder present");
            return;
        }
        for (Encoder encoder : encoders) {
            if (encoder.testSpecific(width, height, flexYUV)) {
                skipped = false;
            }
        }
        if (skipped) {
            MediaUtils.skipTest("duplicate or unsupported resolution");
        }
    }

    /* test intra refresh with flexYUV */
    private void intraRefresh(Encoder[] encoders, int width, int height) {
        boolean skipped = true;
        if (encoders.length == 0) {
            MediaUtils.skipTest("no such encoder present");
            return;
        }
        for (Encoder encoder : encoders) {
            if (encoder.testIntraRefresh(width, height)) {
                skipped = false;
            }
        }
        if (skipped) {
            MediaUtils.skipTest("intra-refresh unsupported");
        }
    }

    /* test size, frame rate and bit rate */
    private void detailed(
            Encoder[] encoders, int width, int height, int frameRate, int bitRate,
            boolean flexYUV) {
        if (encoders.length == 0) {
            MediaUtils.skipTest("no such encoder present");
            return;
        }
        boolean skipped = true;
        for (Encoder encoder : encoders) {
            if (encoder.testSupport(width, height, frameRate, bitRate)) {
                skipped = false;
                encoder.testDetailed(width, height, frameRate, bitRate, flexYUV);
            }
        }
        if (skipped) {
            MediaUtils.skipTest("unsupported resolution and rate");
        }
    }

    /* test size and rate are supported */
    private void support(Encoder[] encoders, int width, int height, int frameRate, int bitRate) {
        boolean supported = false;
        if (encoders.length == 0) {
            MediaUtils.skipTest("no such encoder present");
            return;
        }
        for (Encoder encoder : encoders) {
            if (encoder.testSupport(width, height, frameRate, bitRate)) {
                supported = true;
                break;
            }
        }
        if (!supported) {
            fail("unsupported format " + width + "x" + height + " " +
                    frameRate + "fps " + bitRate + "bps");
        }
    }
}
