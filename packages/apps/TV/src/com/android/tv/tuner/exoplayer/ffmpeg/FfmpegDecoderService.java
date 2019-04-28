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

package com.android.tv.tuner.exoplayer.ffmpeg;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.google.android.exoplayer2.ext.ffmpeg.FfmpegAudioDecoder;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Ffmpeg based audio decoder service.
 * It should be isolatedProcess due to security reason.
 */
public class FfmpegDecoderService extends Service {
    private static final String TAG = "FfmpegDecoderService";
    private static final boolean DEBUG = false;

    private static final String POLICY_FILE = "whitelist.policy";

    private static final long MINIJAIL_SETUP_WAIT_TIMEOUT_MS = 5000;

    private static boolean sLibraryLoaded = true;

    static {
        try {
            System.loadLibrary("minijail_jni");
        } catch (Exception | Error e) {
            Log.e(TAG, "Load minijail failed:", e);
            sLibraryLoaded = false;
        }
    }

    private FfmpegDecoder mBinder = new FfmpegDecoder();
    private volatile Object mMinijailSetupMonitor = new Object();
    //@GuardedBy("mMinijailSetupMonitor")
    private volatile Boolean mMinijailSetup;

    @Override
    public void onCreate() {
        if (sLibraryLoaded) {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    synchronized (mMinijailSetupMonitor) {
                        int pipeFd = getPolicyPipeFd();
                        if (pipeFd <= 0) {
                            Log.e(TAG, "fail to open policy file");
                            mMinijailSetup = false;
                        } else {
                            nativeSetupMinijail(pipeFd);
                            mMinijailSetup = true;
                            if (DEBUG) Log.d(TAG, "Minijail setup successfully");
                        }
                        mMinijailSetupMonitor.notify();
                    }
                    return null;
                }
            }.execute();
        } else {
            synchronized (mMinijailSetupMonitor) {
                mMinijailSetup = false;
                mMinijailSetupMonitor.notify();
            }
        }
        super.onCreate();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private int getPolicyPipeFd() {
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            final ParcelFileDescriptor.AutoCloseOutputStream outputStream =
                    new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]);
            final AssetFileDescriptor policyFile = getAssets().openFd("whitelist.policy");
            final byte[] buffer = new byte[2048];
            final FileInputStream policyStream = policyFile.createInputStream();
            while (true) {
                int bytesRead = policyStream.read(buffer);
                if (bytesRead == -1) break;
                outputStream.write(buffer, 0, bytesRead);
            }
            policyStream.close();
            outputStream.close();
            return pipe[0].detachFd();
        } catch (IOException e) {
            Log.e(TAG, "Policy file not found:" + e);
        }
        return -1;
    }

    private final class FfmpegDecoder extends IFfmpegDecoder.Stub {
        FfmpegAudioDecoder mDecoder;
        @Override
        public boolean isAvailable() {
            return isMinijailSetupDone() && FfmpegAudioDecoder.isAvailable();
        }

        @Override
        public void create() {
            mDecoder = new FfmpegAudioDecoder(FfmpegDecoderService.this);
        }

        @Override
        public void release() {
            if (mDecoder != null) {
                mDecoder.release();
                mDecoder = null;
            }
        }

        @Override
        public void decode(long timeUs, byte[] sample) {
            if (!isMinijailSetupDone()) {
                // If minijail is not setup, we don't run decode for better security.
                return;
            }
            mDecoder.decode(timeUs, sample);
        }

        @Override
        public void resetDecoderState(String mimetype) {
            mDecoder.resetDecoderState(mimetype);
        }

        @Override
        public byte[] getDecodedSample() {
            ByteBuffer decodedBuffer = mDecoder.getDecodedSample();
            byte[] ret = new byte[decodedBuffer.limit()];
            decodedBuffer.get(ret, 0, ret.length);
            return ret;
        }

        @Override
        public long getDecodedTimeUs() {
            return mDecoder.getDecodedTimeUs();
        }

        private boolean isMinijailSetupDone() {
            synchronized (mMinijailSetupMonitor) {
                if (DEBUG) Log.d(TAG, "mMinijailSetup in isAvailable(): " + mMinijailSetup);
                if (mMinijailSetup == null) {
                    try {
                        if (DEBUG) Log.d(TAG, "Wait till Minijail setup is done");
                        mMinijailSetupMonitor.wait(MINIJAIL_SETUP_WAIT_TIMEOUT_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                return mMinijailSetup != null && mMinijailSetup;
            }
        }

        @Override
        public void testSandboxIsolatedProcess() {
            if (!isMinijailSetupDone()) {
                // If minijail is not setup, we return directly to make the test fail.
                return;
            }
            if (FfmpegDecoderService.this.checkSelfPermission("android.permission.INTERNET")
                    == PackageManager.PERMISSION_GRANTED) {
                throw new SecurityException("Shouldn't have the permission of internet");
            }
        }

        @Override
        public void testSandboxMinijail() {
            if (!isMinijailSetupDone()) {
                // If minijail is not setup, we return directly to make the test fail.
                return;
            }
            nativeTestMinijail();
        }
    }

    private native void nativeSetupMinijail(int policyFd);
    private native void nativeTestMinijail();
}
