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

package com.android.tv.tuner.exoplayer.audio;

import android.media.MediaCodec;
import android.util.Log;

import com.google.android.exoplayer.CodecCounters;
import com.google.android.exoplayer.DecoderInfo;
import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.MediaCodecSelector;
import com.google.android.exoplayer.MediaCodecUtil;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.SampleHolder;

import java.nio.ByteBuffer;
import java.util.ArrayList;

/** A decoder to use MediaCodec for decoding audio stream. */
public class MediaCodecAudioDecoder extends AudioDecoder {
    private static final String TAG = "MediaCodecAudioDecoder";

    public static final int INDEX_INVALID = -1;

    private final CodecCounters mCodecCounters;
    private final MediaCodecSelector mSelector;

    private MediaCodec mCodec;
    private MediaCodec.BufferInfo mOutputBufferInfo;
    private ByteBuffer mMediaCodecOutputBuffer;
    private ArrayList<Long> mDecodeOnlyPresentationTimestamps;
    private boolean mWaitingForFirstSyncFrame;
    private boolean mIsNewIndex;
    private int mInputIndex;
    private int mOutputIndex;

    /** Creates a MediaCodec based audio decoder. */
    public MediaCodecAudioDecoder(MediaCodecSelector selector) {
        mSelector = selector;
        mOutputBufferInfo = new MediaCodec.BufferInfo();
        mCodecCounters = new CodecCounters();
        mDecodeOnlyPresentationTimestamps = new ArrayList<>();
    }

    /** Returns {@code true} if there is decoder for {@code mimeType}. */
    public static boolean supportMimeType(MediaCodecSelector selector, String mimeType) {
        if (selector == null) {
            return false;
        }
        return getDecoderInfo(selector, mimeType) != null;
    }

    private static DecoderInfo getDecoderInfo(MediaCodecSelector selector, String mimeType) {
        try {
            return selector.getDecoderInfo(mimeType, false);
        } catch (MediaCodecUtil.DecoderQueryException e) {
            Log.e(TAG, "Select decoder error:" + e);
            return null;
        }
    }

    private boolean shouldInitCodec(MediaFormat format) {
        return format != null && mCodec == null;
    }

    @Override
    public void maybeInitDecoder(MediaFormat format) throws ExoPlaybackException {
        if (!shouldInitCodec(format)) {
            return;
        }

        String mimeType = format.mimeType;
        DecoderInfo decoderInfo = getDecoderInfo(mSelector, mimeType);
        if (decoderInfo == null) {
            Log.i(TAG, "There is not decoder found for " + mimeType);
            return;
        }

        String codecName = decoderInfo.name;
        try {
            mCodec = MediaCodec.createByCodecName(codecName);
            mCodec.configure(format.getFrameworkMediaFormatV16(), null, null, 0);
            mCodec.start();
        } catch (Exception e) {
            Log.e(TAG, "Failed when configure or start codec:" + e);
            throw new ExoPlaybackException(e);
        }
        mInputIndex = INDEX_INVALID;
        mOutputIndex = INDEX_INVALID;
        mWaitingForFirstSyncFrame = true;
        mCodecCounters.codecInitCount++;
    }

    @Override
    public void resetDecoderState(String mimeType) {
        if (mCodec == null) {
            return;
        }
        mInputIndex = INDEX_INVALID;
        mOutputIndex = INDEX_INVALID;
        mDecodeOnlyPresentationTimestamps.clear();
        mCodec.flush();
        mWaitingForFirstSyncFrame = true;
    }

    @Override
    public void release() {
        if (mCodec != null) {
            mDecodeOnlyPresentationTimestamps.clear();
            mInputIndex = INDEX_INVALID;
            mOutputIndex = INDEX_INVALID;
            mCodecCounters.codecReleaseCount++;
            try {
                mCodec.stop();
            } finally {
                try {
                    mCodec.release();
                } finally {
                    mCodec = null;
                }
            }
        }
    }

    /** Returns the index of input buffer which is ready for using. */
    public int getInputIndex() {
        return mInputIndex;
    }

    @Override
    public ByteBuffer getInputBuffer() {
        if (mInputIndex < 0) {
            mInputIndex = mCodec.dequeueInputBuffer(0);
            if (mInputIndex < 0) {
                return null;
            }
            return mCodec.getInputBuffer(mInputIndex);
        }
        return mCodec.getInputBuffer(mInputIndex);
    }

    @Override
    public void decode(SampleHolder sampleHolder) {
        if (mWaitingForFirstSyncFrame) {
            if (!sampleHolder.isSyncFrame()) {
                sampleHolder.clearData();
                return;
            }
            mWaitingForFirstSyncFrame = false;
        }
        long presentationTimeUs = sampleHolder.timeUs;
        if (sampleHolder.isDecodeOnly()) {
            mDecodeOnlyPresentationTimestamps.add(presentationTimeUs);
        }
        mCodec.queueInputBuffer(mInputIndex, 0, sampleHolder.data.limit(), presentationTimeUs, 0);
        mInputIndex = INDEX_INVALID;
        mCodecCounters.inputBufferCount++;
    }

    private int getDecodeOnlyIndex(long presentationTimeUs) {
        final int size = mDecodeOnlyPresentationTimestamps.size();
        for (int i = 0; i < size; i++) {
            if (mDecodeOnlyPresentationTimestamps.get(i).longValue() == presentationTimeUs) {
                return i;
            }
        }
        return INDEX_INVALID;
    }

    /** Returns the index of output buffer which is ready for using. */
    public int getOutputIndex() {
        if (mOutputIndex < 0) {
            mOutputIndex = mCodec.dequeueOutputBuffer(mOutputBufferInfo, 0);
            mIsNewIndex = true;
        } else {
            mIsNewIndex = false;
        }
        return mOutputIndex;
    }

    @Override
    public android.media.MediaFormat getOutputFormat() {
        return mCodec.getOutputFormat();
    }

    /** Returns {@code true} if the output is only for decoding but not for rendering. */
    public boolean maybeDecodeOnlyIndex() {
        int decodeOnlyIndex = getDecodeOnlyIndex(mOutputBufferInfo.presentationTimeUs);
        if (decodeOnlyIndex != INDEX_INVALID) {
            mCodec.releaseOutputBuffer(mOutputIndex, false);
            mCodecCounters.skippedOutputBufferCount++;
            mDecodeOnlyPresentationTimestamps.remove(decodeOnlyIndex);
            mOutputIndex = INDEX_INVALID;
            return true;
        }
        return false;
    }

    @Override
    public ByteBuffer getDecodedSample() {
        if (maybeDecodeOnlyIndex() || mOutputIndex < 0) {
            return null;
        }
        if (mIsNewIndex) {
            mMediaCodecOutputBuffer = mCodec.getOutputBuffer(mOutputIndex);
        }
        return mMediaCodecOutputBuffer;
    }

    @Override
    public long getDecodedTimeUs() {
        return mOutputBufferInfo.presentationTimeUs;
    }

    /** Releases the output buffer after rendering. */
    public void releaseOutputBuffer() {
        mCodecCounters.renderedOutputBufferCount++;
        mCodec.releaseOutputBuffer(mOutputIndex, false);
        mOutputIndex = INDEX_INVALID;
    }
}
