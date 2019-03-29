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
import android.util.Log;
import android.view.Surface;
import java.nio.ByteBuffer;

public class NdkMediaCodec implements MediaCodecWrapper {

    private static final String CSD_0 = "csd-0";
    private long mNdkMediaCodec;
    private final String mName;

    static {
        Log.i("@@@", "before loadlibrary");
        System.loadLibrary("ctsmediacodec_jni");
        Log.i("@@@", "after loadlibrary");
    }

    private static native long AMediaCodecCreateCodecByName(String name);
    private static native boolean AMediaCodecDelete(long ndkMediaCodec);
    private static native boolean AMediaCodecStart(long ndkMediaCodec);
    private static native boolean AMediaCodecStop(long ndkMediaCodec);
    private static native String AMediaCodecGetOutputFormatString(long ndkMediaCodec);
    private static native boolean AMediaCodecSetInputSurface(long ndkMediaCodec, Surface surface);
    private static native boolean AMediaCodecSetNativeInputSurface(long ndkMediaCodec, long aNativeWindow);
    private static native long AMediaCodecCreateInputSurface(long ndkMediaCodec);
    private static native long AMediaCodecCreatePersistentInputSurface();
    private static native boolean AMediaCodecSignalEndOfInputStream(long ndkMediaCodec);
    private static native boolean AMediaCodecReleaseOutputBuffer(long ndkMediaCodec, int index, boolean render);
    private static native ByteBuffer AMediaCodecGetOutputBuffer(long ndkMediaCodec, int index);
    private static native long[] AMediaCodecDequeueOutputBuffer(long ndkMediaCodec, long timeoutUs);
    private static native ByteBuffer AMediaCodecGetInputBuffer(long ndkMediaCodec, int index);
    private static native int AMediaCodecDequeueInputBuffer(long ndkMediaCodec, long timeoutUs);
    private static native boolean AMediaCodecSetParameter(long ndkMediaCodec, String key, int value);

    private static native boolean AMediaCodecConfigure(
            long ndkMediaCodec,
            String mime,
            int width,
            int height,
            int colorFormat,
            int bitRate,
            int frameRate,
            int iFrameInterval,
            ByteBuffer csd,
            int flags);

    private static native boolean AMediaCodecQueueInputBuffer(
            long ndkMediaCodec,
            int index,
            int offset,
            int size,
            long presentationTimeUs,
            int flags);

    public NdkMediaCodec(String name) {
        mName = name;
        mNdkMediaCodec = AMediaCodecCreateCodecByName(name);
    }

    @Override
    protected void finalize() throws Throwable {
        AMediaCodecDelete(mNdkMediaCodec);
    }

    @Override
    public void release() {
        AMediaCodecDelete(mNdkMediaCodec);
        mNdkMediaCodec = 0;
    }

    @Override
    public void start() {
        AMediaCodecStart(mNdkMediaCodec);
    }

    @Override
    public void stop() {
        AMediaCodecStop(mNdkMediaCodec);
    }

    @Override
    public void configure(MediaFormat format, int flags) {

        int width = format.getInteger(MediaFormat.KEY_WIDTH, -1);
        int height = format.getInteger(MediaFormat.KEY_HEIGHT, -1);
        int colorFormat = format.getInteger(MediaFormat.KEY_COLOR_FORMAT, -1);
        int bitRate = format.getInteger(MediaFormat.KEY_BIT_RATE, -1);
        int frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE, -1);
        int iFrameInterval = format.getInteger(MediaFormat.KEY_I_FRAME_INTERVAL, -1);

        ByteBuffer csdBufCopy = null;
        if (format.containsKey(CSD_0)) {
            ByteBuffer csdBufOld = format.getByteBuffer(CSD_0);
            csdBufCopy = ByteBuffer.allocateDirect(csdBufOld.remaining());
            csdBufCopy.put(csdBufOld);
            csdBufCopy.position(0);
        }

        AMediaCodecConfigure(
                mNdkMediaCodec,
                format.getString(MediaFormat.KEY_MIME),
                width,
                height,
                colorFormat,
                bitRate,
                frameRate,
                iFrameInterval ,
                csdBufCopy,
                flags);
    }

    @Override
    public void setInputSurface(InputSurfaceInterface surface) {
        surface.configure(this);
    }

    public void setInputSurface(Surface surface) {
        AMediaCodecSetInputSurface(mNdkMediaCodec, surface);
    }

    public void setInputSurface(long aNativeWindow) {
        AMediaCodecSetNativeInputSurface(mNdkMediaCodec, aNativeWindow);
    }

    @Override
    public InputSurfaceInterface createInputSurface() {
        return new NdkInputSurface(AMediaCodecCreateInputSurface(mNdkMediaCodec));
    }

    public static InputSurfaceInterface createPersistentInputSurface() {
        return new NdkInputSurface(AMediaCodecCreatePersistentInputSurface());
    }

    @Override
    public int dequeueOutputBuffer(BufferInfo info, long timeoutUs) {
        long[] ret = AMediaCodecDequeueOutputBuffer(mNdkMediaCodec, timeoutUs);
        if (ret[0] >= 0) {
            info.offset = (int)ret[1];
            info.size = (int)ret[2];
            info.presentationTimeUs = ret[3];
            info.flags = (int)ret[4];
        }
        return (int)ret[0];
    }

    @Override
    public ByteBuffer getOutputBuffer(int index) {
        return AMediaCodecGetOutputBuffer(mNdkMediaCodec, index);
    }

    @Override
    public void releaseOutputBuffer(int index, boolean render) {
        AMediaCodecReleaseOutputBuffer(mNdkMediaCodec, index, render);
    }

    @Override
    public void signalEndOfInputStream() {
        AMediaCodecSignalEndOfInputStream(mNdkMediaCodec);
    }

    @Override
    public String getOutputFormatString() {
        return AMediaCodecGetOutputFormatString(mNdkMediaCodec);
    }

    @Override
    public ByteBuffer[] getOutputBuffers() {
        return null;
    }

    @Override
    public ByteBuffer getInputBuffer(int index) {
        return AMediaCodecGetInputBuffer(mNdkMediaCodec, index);
    }

    @Override
    public ByteBuffer[] getInputBuffers() {
        return null;
    }

    @Override
    public void queueInputBuffer(
            int index,
            int offset,
            int size,
            long presentationTimeUs,
            int flags) {

        AMediaCodecQueueInputBuffer(mNdkMediaCodec, index, offset, size, presentationTimeUs, flags);

    }

    @Override
    public int dequeueInputBuffer(long timeoutUs) {
        return AMediaCodecDequeueInputBuffer(mNdkMediaCodec, timeoutUs);
    }

    @Override
    public void setParameters(Bundle params) {

        String keys[] = new String[] {
                MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME,
                MediaCodec.PARAMETER_KEY_VIDEO_BITRATE};

        for (String key : keys) {
            if (params.containsKey(key)) {
                int value = params.getInt(key);
                AMediaCodecSetParameter(mNdkMediaCodec, key, value);
            }
        }

    }

    @Override
    public void setCallback(Callback mCallback) {
        throw new UnsupportedOperationException(mCallback.toString());
    }

    @Override
    public String toString() {
        return String.format("%s(%s, %x)", getClass(), mName, mNdkMediaCodec);
    }
}
