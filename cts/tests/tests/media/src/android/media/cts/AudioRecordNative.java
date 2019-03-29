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

public class AudioRecordNative {
    // Must be kept in sync with C++ JNI audio-record-native (AudioRecordNative) READ_FLAG_*
    public static final int READ_FLAG_BLOCKING = 1 << 0;
    /** @hide */
    @IntDef(flag = true,
            value = {
                    READ_FLAG_BLOCKING,
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ReadFlags { }

    public AudioRecordNative() {
        mNativeRecordInJavaObj = nativeCreateRecord();
    }

    public boolean open(int numChannels, int sampleRate, boolean useFloat, int numBuffers) {
        return open(numChannels, 0, sampleRate, useFloat,numBuffers);
    }

    public boolean open(int numChannels, int channelMask, int sampleRate,
            boolean useFloat, int numBuffers) {
        if (nativeOpen(mNativeRecordInJavaObj, numChannels, channelMask,
                sampleRate, useFloat, numBuffers) == STATUS_OK) {
            mChannelCount = numChannels;
            return true;
        }
        return false;
    }

    public void close() {
        nativeClose(mNativeRecordInJavaObj);
    }

    public boolean start() {
        return nativeStart(mNativeRecordInJavaObj) == STATUS_OK;
    }

    public boolean stop() {
        return nativeStop(mNativeRecordInJavaObj) == STATUS_OK;
    }

    public boolean pause() {
        return nativePause(mNativeRecordInJavaObj) == STATUS_OK;
    }

    public boolean flush() {
        return nativeFlush(mNativeRecordInJavaObj) == STATUS_OK;
    }

    public long getPositionInMsec() {
        long[] position = new long[1];
        if (nativeGetPositionInMsec(mNativeRecordInJavaObj, position) != STATUS_OK) {
            throw new IllegalStateException();
        }
        return position[0];
    }

    public int getBuffersPending() {
        return nativeGetBuffersPending(mNativeRecordInJavaObj);
    }

    public int read(@NonNull byte[] byteArray,
            int offsetInSamples, int sizeInSamples, @ReadFlags int readFlags) {
        return nativeReadByteArray(
                mNativeRecordInJavaObj, byteArray, offsetInSamples, sizeInSamples, readFlags);
    }

    public int read(@NonNull short[] shortArray,
            int offsetInSamples, int sizeInSamples, @ReadFlags int readFlags) {
        return nativeReadShortArray(
                mNativeRecordInJavaObj, shortArray, offsetInSamples, sizeInSamples, readFlags);
    }

    public int read(@NonNull float[] floatArray,
            int offsetInSamples, int sizeInSamples, @ReadFlags int readFlags) {
        return nativeReadFloatArray(
                mNativeRecordInJavaObj, floatArray, offsetInSamples, sizeInSamples, readFlags);
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
        nativeClose(mNativeRecordInJavaObj);
        nativeDestroyRecord(mNativeRecordInJavaObj);
    }

    static {
        System.loadLibrary("audio_jni");
    }

    private static final String TAG = "AudioRecordNative";
    private int mChannelCount;
    private final long mNativeRecordInJavaObj;
    private static final int STATUS_OK = 0;

    // static native API.
    // The native API uses a long "record handle" created by nativeCreateRecord.
    // The handle must be destroyed after use by nativeDestroyRecord.
    //
    // Return codes from the native layer are status_t.
    // Converted to Java booleans or exceptions at the public API layer.
    private static native long nativeCreateRecord();
    private static native void nativeDestroyRecord(long record);
    private static native int nativeOpen(
            long record, int numChannels, int channelMask,
            int sampleRate, boolean useFloat, int numBuffers);
    private static native void nativeClose(long record);
    private static native int nativeStart(long record);
    private static native int nativeStop(long record);
    private static native int nativePause(long record);
    private static native int nativeFlush(long record);
    private static native int nativeGetPositionInMsec(long record, @NonNull long[] position);
    private static native int nativeGetBuffersPending(long record);
    private static native int nativeReadByteArray(long record, @NonNull byte[] byteArray,
            int offsetInSamples, int sizeInSamples, @ReadFlags int readFlags);
    private static native int nativeReadShortArray(long record, @NonNull short[] shortArray,
            int offsetInSamples, int sizeInSamples, @ReadFlags int readFlags);
    private static native int nativeReadFloatArray(long record, @NonNull float[] floatArray,
            int offsetInSamples, int sizeInSamples, @ReadFlags int readFlags);

    // native interface for all-in-one testing, no record handle required.
    private static native int nativeTest(
            int numChannels, int channelMask, int sampleRate,
            boolean useFloat, int msecPerBuffer, int numBuffers);
}
