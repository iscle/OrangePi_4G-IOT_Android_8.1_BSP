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

import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodec.Callback;
import android.media.MediaFormat;
import android.os.Bundle;
import java.nio.ByteBuffer;

public class SdkMediaCodec implements MediaCodecWrapper {

    private final MediaCodec mCodec;
    private final boolean mAsync;
    private ByteBuffer[] mOutputBuffers;
    private ByteBuffer[] mInputBuffers;

    public SdkMediaCodec(MediaCodec codec, boolean async) {
        this.mCodec = codec;
        this.mAsync = async;
    }

    public SdkMediaCodec(MediaCodec codec) {
        this(codec, false);
    }

    public MediaCodec getMediaCodec() {
        return mCodec;
    }

    @Override
    public final void release() {
        mCodec.release();
    }

    @Override
    public void configure(MediaFormat format, int flags) {
        mCodec.configure(format, null, null, flags);
    }

    @Override
    public void setInputSurface(InputSurfaceInterface surface) {
        surface.configure(mCodec);
    }

    @Override
    public final InputSurfaceInterface createInputSurface() {
        return new InputSurface(mCodec.createInputSurface());
    }

    @Override
    public final void start() {
        mCodec.start();
    }

    @Override
    public final void stop() {
        mCodec.stop();
    }

    @Override
    public final int dequeueOutputBuffer(BufferInfo info, long timeoutUs) {
        return mCodec.dequeueOutputBuffer(info, timeoutUs);
    }

    @Override
    public final void releaseOutputBuffer(int index, boolean render) {
        mCodec.releaseOutputBuffer(index, render);
    }

    @Override
    public final void signalEndOfInputStream() {
        mCodec.signalEndOfInputStream();
    }

    @Override
    public ByteBuffer[] getOutputBuffers() {
        return mOutputBuffers = mCodec.getOutputBuffers();
    }

    @Override
    public final String getOutputFormatString() {
        return mCodec.getOutputFormat().toString();
    }

    @Override
    public ByteBuffer getOutputBuffer(int index) {
        return mAsync? mCodec.getOutputBuffer(index) : mOutputBuffers[index];
    }

    @Override
    public ByteBuffer[] getInputBuffers() {
        return mInputBuffers = mCodec.getInputBuffers();
    }

    @Override
    public ByteBuffer getInputBuffer(int index) {
        return mAsync? mCodec.getInputBuffer(index) : mInputBuffers[index];
    }

    @Override
    public void queueInputBuffer(
            int index,
            int offset,
            int size,
            long presentationTimeUs,
            int flags) {
        mCodec.queueInputBuffer(index, offset, size, presentationTimeUs, flags);
    }

    @Override
    public int dequeueInputBuffer(long timeoutUs) {
        return mCodec.dequeueInputBuffer(timeoutUs);
    }

    @Override
    public void setParameters(Bundle params) {
        mCodec.setParameters(params);
    }

    @Override
    public void setCallback(Callback mCallback) {
        mCodec.setCallback(mCallback);
    }

}
