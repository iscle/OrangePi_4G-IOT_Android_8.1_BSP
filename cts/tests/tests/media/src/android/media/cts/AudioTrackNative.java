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

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.util.Log;

import com.android.compatibility.common.util.CtsAndroidTestCase;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class AudioTrackNative {
    // Must be kept in sync with C++ JNI audio-track-native (AudioTrackNative) WRITE_FLAG_*
    public static final int WRITE_FLAG_BLOCKING = 1 << 0;
    /** @hide */
    @IntDef(flag = true,
            value = {
                    WRITE_FLAG_BLOCKING,
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface WriteFlags { }

    public AudioTrackNative() {
        mNativeTrackInJavaObj = nativeCreateTrack();
    }

    // TODO: eventually accept AudioFormat
    // numBuffers is the number of internal buffers before hitting the OpenSL ES.
    // A value of 0 means that all writes are blocking.
    public boolean open(int numChannels, int sampleRate, boolean useFloat, int numBuffers) {
        return open(numChannels, 0, sampleRate, useFloat, numBuffers);
    }

    public boolean open(int numChannels, int channelMask, int sampleRate,
            boolean useFloat, int numBuffers) {
        if (nativeOpen(mNativeTrackInJavaObj, numChannels, channelMask,
                sampleRate, useFloat, numBuffers) == STATUS_OK) {
            mChannelCount = numChannels;
            return true;
        }
        return false;
    }

    public void close() {
        nativeClose(mNativeTrackInJavaObj);
    }

    public boolean start() {
        return nativeStart(mNativeTrackInJavaObj) == STATUS_OK;
    }

    public boolean stop() {
        return nativeStop(mNativeTrackInJavaObj) == STATUS_OK;
    }

    public boolean pause() {
        return nativePause(mNativeTrackInJavaObj) == STATUS_OK;
    }

    public boolean flush() {
        return nativeFlush(mNativeTrackInJavaObj) == STATUS_OK;
    }

    public long getPositionInMsec() {
        long[] position = new long[1];
        if (nativeGetPositionInMsec(mNativeTrackInJavaObj, position) != STATUS_OK) {
            throw new IllegalStateException();
        }
        return position[0];
    }

    public int getBuffersPending() {
        return nativeGetBuffersPending(mNativeTrackInJavaObj);
    }

    /* returns number of samples written.
     * 0 may be returned if !isBlocking.
     * negative value indicates error.
     */
    public int write(@NonNull byte[] byteArray,
            int offsetInSamples, int sizeInSamples, @WriteFlags int writeFlags) {
        return nativeWriteByteArray(
                mNativeTrackInJavaObj, byteArray, offsetInSamples, sizeInSamples, writeFlags);
    }

    public int write(@NonNull short[] shortArray,
            int offsetInSamples, int sizeInSamples, @WriteFlags int writeFlags) {
        return nativeWriteShortArray(
                mNativeTrackInJavaObj, shortArray, offsetInSamples, sizeInSamples, writeFlags);
    }

    public int write(@NonNull float[] floatArray,
            int offsetInSamples, int sizeInSamples, @WriteFlags int writeFlags) {
        return nativeWriteFloatArray(
                mNativeTrackInJavaObj, floatArray, offsetInSamples, sizeInSamples, writeFlags);
    }

    public int getChannelCount() {
        return mChannelCount;
    }

    public static boolean test(int numChannels, int sampleRate, boolean useFloat,
            int msecPerBuffer, int numBuffers) {
        return test(numChannels, 0, sampleRate, useFloat, msecPerBuffer, numBuffers);
    }

    public static boolean test(int numChannels, int channelMask, int sampleRate, boolean useFloat,
            int msecPerBuffer, int numBuffers) {
        return nativeTest(numChannels, channelMask, sampleRate, useFloat, msecPerBuffer, numBuffers)
                == STATUS_OK;
    }

    @Override
    protected void finalize() {
        nativeClose(mNativeTrackInJavaObj);
        nativeDestroyTrack(mNativeTrackInJavaObj);
    }

    static {
        System.loadLibrary("audio_jni");
    }

    private static final String TAG = "AudioTrackNative";
    private int mChannelCount;
    private final long mNativeTrackInJavaObj;
    private static final int STATUS_OK = 0;

    // static native API.
    // The native API uses a long "track handle" created by nativeCreateTrack.
    // The handle must be destroyed after use by nativeDestroyTrack.
    //
    // Return codes from the native layer are status_t.
    // Converted to Java booleans or exceptions at the public API layer.
    private static native long nativeCreateTrack();
    private static native void nativeDestroyTrack(long track);
    private static native int nativeOpen(
            long track, int numChannels, int channelMask,
            int sampleRate, boolean useFloat, int numBuffers);
    private static native void nativeClose(long track);
    private static native int nativeStart(long track);
    private static native int nativeStop(long track);
    private static native int nativePause(long track);
    private static native int nativeFlush(long track);
    private static native int nativeGetPositionInMsec(long track, @NonNull long[] position);
    private static native int nativeGetBuffersPending(long track);
    private static native int nativeWriteByteArray(long track, @NonNull byte[] byteArray,
            int offsetInSamples, int sizeInSamples, @WriteFlags int writeFlags);
    private static native int nativeWriteShortArray(long track, @NonNull short[] shortArray,
            int offsetInSamples, int sizeInSamples, @WriteFlags int writeFlags);
    private static native int nativeWriteFloatArray(long track, @NonNull float[] floatArray,
            int offsetInSamples, int sizeInSamples, @WriteFlags int writeFlags);

    // native interface for all-in-one testing, no track handle required.
    private static native int nativeTest(
            int numChannels, int channelMask, int sampleRate,
            boolean useFloat, int msecPerBuffer, int numBuffers);
}
