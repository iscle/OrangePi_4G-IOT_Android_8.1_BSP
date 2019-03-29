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

import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.LinkedList;

/**
 * Class for directly managing both audio and video playback by
 * using {@link MediaCodec} and {@link AudioTrack}.
 */
public class CodecState {
    private static final String TAG = CodecState.class.getSimpleName();

    private boolean mSawInputEOS, mSawOutputEOS;
    private boolean mLimitQueueDepth;
    private boolean mTunneled;
    private boolean mIsAudio;
    private int mAudioSessionId;
    private ByteBuffer[] mCodecInputBuffers;
    private ByteBuffer[] mCodecOutputBuffers;
    private int mTrackIndex;
    private LinkedList<Integer> mAvailableInputBufferIndices;
    private LinkedList<Integer> mAvailableOutputBufferIndices;
    private LinkedList<MediaCodec.BufferInfo> mAvailableOutputBufferInfos;
    private long mPresentationTimeUs;
    private long mSampleBaseTimeUs;
    private MediaCodec mCodec;
    private MediaTimeProvider mMediaTimeProvider;
    private MediaExtractor mExtractor;
    private MediaFormat mFormat;
    private MediaFormat mOutputFormat;
    private NonBlockingAudioTrack mAudioTrack;

    /**
     * Manages audio and video playback using MediaCodec and AudioTrack.
     */
    public CodecState(
            MediaTimeProvider mediaTimeProvider,
            MediaExtractor extractor,
            int trackIndex,
            MediaFormat format,
            MediaCodec codec,
            boolean limitQueueDepth,
            boolean tunneled,
            int audioSessionId) {
        mMediaTimeProvider = mediaTimeProvider;
        mExtractor = extractor;
        mTrackIndex = trackIndex;
        mFormat = format;
        mSawInputEOS = mSawOutputEOS = false;
        mLimitQueueDepth = limitQueueDepth;
        mTunneled = tunneled;
        mAudioSessionId = audioSessionId;
        mSampleBaseTimeUs = -1;

        mCodec = codec;

        mAvailableInputBufferIndices = new LinkedList<Integer>();
        mAvailableOutputBufferIndices = new LinkedList<Integer>();
        mAvailableOutputBufferInfos = new LinkedList<MediaCodec.BufferInfo>();

        mPresentationTimeUs = 0;

        String mime = mFormat.getString(MediaFormat.KEY_MIME);
        Log.d(TAG, "CodecState::onOutputFormatChanged " + mime);
        mIsAudio = mime.startsWith("audio/");
    }

    public void release() {
        mCodec.stop();
        mCodecInputBuffers = null;
        mCodecOutputBuffers = null;
        mOutputFormat = null;

        mAvailableInputBufferIndices.clear();
        mAvailableOutputBufferIndices.clear();
        mAvailableOutputBufferInfos.clear();

        mAvailableInputBufferIndices = null;
        mAvailableOutputBufferIndices = null;
        mAvailableOutputBufferInfos = null;

        mCodec.release();
        mCodec = null;

        if (mAudioTrack != null) {
            mAudioTrack.release();
            mAudioTrack = null;
        }
    }

    public void start() {
        mCodec.start();
        mCodecInputBuffers = mCodec.getInputBuffers();
        if (!mTunneled || mIsAudio) {
            mCodecOutputBuffers = mCodec.getOutputBuffers();
        }

        if (mAudioTrack != null) {
            mAudioTrack.play();
        }
    }

    public void pause() {
        if (mAudioTrack != null) {
            mAudioTrack.pause();
        }
    }

    public long getCurrentPositionUs() {
        return mPresentationTimeUs;
    }

    public void flush() {
        mAvailableInputBufferIndices.clear();
        if (!mTunneled || mIsAudio) {
            mAvailableOutputBufferIndices.clear();
            mAvailableOutputBufferInfos.clear();
        }

        mSawInputEOS = false;
        mSawOutputEOS = false;

        if (mAudioTrack != null
                && mAudioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
            mAudioTrack.flush();
        }

        mCodec.flush();
    }

    public boolean isEnded() {
        return mSawInputEOS && mSawOutputEOS;
    }

    /**
     * doSomeWork() is the worker function that does all buffer handling and decoding works.
     * It first reads data from {@link MediaExtractor} and pushes it into {@link MediaCodec};
     * it then dequeues buffer from {@link MediaCodec}, consumes it and pushes back to its own
     * buffer queue for next round reading data from {@link MediaExtractor}.
     */
    public void doSomeWork() {
        int indexInput = mCodec.dequeueInputBuffer(0 /* timeoutUs */);

        if (indexInput != MediaCodec.INFO_TRY_AGAIN_LATER) {
            mAvailableInputBufferIndices.add(indexInput);
        }

        while (feedInputBuffer()) {
        }

        if (mIsAudio || !mTunneled) {
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int indexOutput = mCodec.dequeueOutputBuffer(info, 0 /* timeoutUs */);

            if (indexOutput == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                mOutputFormat = mCodec.getOutputFormat();
                onOutputFormatChanged();
            } else if (indexOutput == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                mCodecOutputBuffers = mCodec.getOutputBuffers();
            } else if (indexOutput != MediaCodec.INFO_TRY_AGAIN_LATER) {
                mAvailableOutputBufferIndices.add(indexOutput);
                mAvailableOutputBufferInfos.add(info);
            }

            while (drainOutputBuffer()) {
            }
        }
    }

    /** Returns true if more input data could be fed. */
    private boolean feedInputBuffer() throws MediaCodec.CryptoException, IllegalStateException {
        if (mSawInputEOS || mAvailableInputBufferIndices.isEmpty()) {
            return false;
        }

        // stalls read if audio queue is larger than 2MB full so we will not occupy too much heap
        if (mLimitQueueDepth && mAudioTrack != null &&
                mAudioTrack.getNumBytesQueued() > 2 * 1024 * 1024) {
            return false;
        }

        int index = mAvailableInputBufferIndices.peekFirst().intValue();

        ByteBuffer codecData = mCodecInputBuffers[index];

        int trackIndex = mExtractor.getSampleTrackIndex();

        if (trackIndex == mTrackIndex) {
            int sampleSize =
                mExtractor.readSampleData(codecData, 0 /* offset */);

            long sampleTime = mExtractor.getSampleTime();

            int sampleFlags = mExtractor.getSampleFlags();

            if (sampleSize <= 0) {
                Log.d(TAG, "sampleSize: " + sampleSize + " trackIndex:" + trackIndex +
                        " sampleTime:" + sampleTime + " sampleFlags:" + sampleFlags);
                mSawInputEOS = true;
                // FIX-ME: in tunneled mode we currently use input EOS as output EOS indicator
                // we should use stream duration
                if (mTunneled && !mIsAudio) {
                    mSawOutputEOS = true;
                }
                return false;
            }

            if (mTunneled && !mIsAudio) {
                if (mSampleBaseTimeUs == -1) {
                    mSampleBaseTimeUs = sampleTime;
                }
                sampleTime -= mSampleBaseTimeUs;
                // FIX-ME: in tunneled mode we currently use input buffer time
                // as video presentation time. This is not accurate and should be fixed
                mPresentationTimeUs = sampleTime;
            }

            if ((sampleFlags & MediaExtractor.SAMPLE_FLAG_ENCRYPTED) != 0) {
                MediaCodec.CryptoInfo info = new MediaCodec.CryptoInfo();
                mExtractor.getSampleCryptoInfo(info);

                mCodec.queueSecureInputBuffer(
                        index, 0 /* offset */, info, sampleTime, 0 /* flags */);
            } else {
                mCodec.queueInputBuffer(
                        index, 0 /* offset */, sampleSize, sampleTime, 0 /* flags */);
            }

            mAvailableInputBufferIndices.removeFirst();
            mExtractor.advance();

            return true;
        } else if (trackIndex < 0) {
            Log.d(TAG, "saw input EOS on track " + mTrackIndex);

            mSawInputEOS = true;
            // FIX-ME: in tunneled mode we currently use input EOS as output EOS indicator
            // we should use stream duration
            if (mTunneled && !mIsAudio) {
                mSawOutputEOS = true;
            }

            mCodec.queueInputBuffer(
                    index, 0 /* offset */, 0 /* sampleSize */,
                    0 /* sampleTime */, MediaCodec.BUFFER_FLAG_END_OF_STREAM);

            mAvailableInputBufferIndices.removeFirst();
        }

        return false;
    }

    private void onOutputFormatChanged() {
        String mime = mOutputFormat.getString(MediaFormat.KEY_MIME);
        // b/9250789
        Log.d(TAG, "CodecState::onOutputFormatChanged " + mime);

        mIsAudio = false;
        if (mime.startsWith("audio/")) {
            mIsAudio = true;
            int sampleRate =
                mOutputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);

            int channelCount =
                mOutputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);

            Log.d(TAG, "CodecState::onOutputFormatChanged Audio" +
                    " sampleRate:" + sampleRate + " channels:" + channelCount);
            // We do sanity check here after we receive data from MediaExtractor and before
            // we pass them down to AudioTrack. If MediaExtractor works properly, this
            // sanity-check is not necessary, however, in our tests, we found that there
            // are a few cases where ch=0 and samplerate=0 were returned by MediaExtractor.
            if (channelCount < 1 || channelCount > 8 ||
                    sampleRate < 8000 || sampleRate > 128000) {
                return;
            }
            mAudioTrack = new NonBlockingAudioTrack(sampleRate, channelCount,
                                    mTunneled, mAudioSessionId);
            mAudioTrack.play();
        }

        if (mime.startsWith("video/")) {
            int width = mOutputFormat.getInteger(MediaFormat.KEY_WIDTH);
            int height = mOutputFormat.getInteger(MediaFormat.KEY_HEIGHT);
            Log.d(TAG, "CodecState::onOutputFormatChanged Video" +
                    " width:" + width + " height:" + height);
        }
    }

    /** Returns true if more output data could be drained. */
    private boolean drainOutputBuffer() {
        if (mSawOutputEOS || mAvailableOutputBufferIndices.isEmpty()) {
            return false;
        }

        int index = mAvailableOutputBufferIndices.peekFirst().intValue();
        MediaCodec.BufferInfo info = mAvailableOutputBufferInfos.peekFirst();

        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            Log.d(TAG, "saw output EOS on track " + mTrackIndex);

            mSawOutputEOS = true;

            // Do not stop audio track here. Video presentation may not finish
            // yet, stopping the auido track now would result in getAudioTimeUs
            // returning 0 and prevent video samples from being presented.
            // We stop the audio track before the playback thread exits.
            return false;
        }

        long realTimeUs =
            mMediaTimeProvider.getRealTimeUsForMediaTime(info.presentationTimeUs);

        long nowUs = mMediaTimeProvider.getNowUs();

        long lateUs = nowUs - realTimeUs;

        if (mAudioTrack != null) {
            ByteBuffer buffer = mCodecOutputBuffers[index];
            buffer.clear();
            ByteBuffer audioBuffer = ByteBuffer.allocate(buffer.remaining());
            audioBuffer.put(buffer);
            audioBuffer.clear();

            mAudioTrack.write(audioBuffer, info.size, info.presentationTimeUs*1000);

            mCodec.releaseOutputBuffer(index, false /* render */);

            mPresentationTimeUs = info.presentationTimeUs;

            mAvailableOutputBufferIndices.removeFirst();
            mAvailableOutputBufferInfos.removeFirst();
            return true;
        } else {
            // video
            boolean render;

            if (lateUs < -45000) {
                // too early;
                return false;
            } else if (lateUs > 30000) {
                Log.d(TAG, "video late by " + lateUs + " us.");
                render = false;
            } else {
                render = true;
                mPresentationTimeUs = info.presentationTimeUs;
            }

            mCodec.releaseOutputBuffer(index, render);

            mAvailableOutputBufferIndices.removeFirst();
            mAvailableOutputBufferInfos.removeFirst();
            return true;
        }
    }

    public long getAudioTimeUs() {
        if (mAudioTrack == null) {
            return 0;
        }

        return mAudioTrack.getAudioTimeUs();
    }

    public void process() {
        if (mAudioTrack != null) {
            mAudioTrack.process();
        }
    }

    public void stop() {
        if (mAudioTrack != null) {
            mAudioTrack.stop();
        }
    }
}
