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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;

import android.support.annotation.MainThread;
import android.support.annotation.WorkerThread;
import android.support.annotation.VisibleForTesting;
import com.google.android.exoplayer.SampleHolder;
import com.android.tv.Features;
import com.android.tv.tuner.exoplayer.audio.AudioDecoder;

import java.nio.ByteBuffer;

/**
 * The class connects {@link FfmpegDecoderService} to decode audio samples.
 * In order to sandbox ffmpeg based decoder, {@link FfmpegDecoderService} is an isolated process
 * without any permission and connected by binder.
 */
public class FfmpegDecoderClient extends AudioDecoder {
    private static FfmpegDecoderClient sInstance;

    private IFfmpegDecoder mService;
    private Boolean mIsAvailable;

    private static final String FFMPEG_DECODER_SERVICE_FILTER =
            "com.android.tv.tuner.exoplayer.ffmpeg.IFfmpegDecoder";
    private static final long FFMPEG_SERVICE_CONNECT_TIMEOUT_MS = 500;

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mService = IFfmpegDecoder.Stub.asInterface(service);
            synchronized (FfmpegDecoderClient.this) {
                try {
                    mIsAvailable = mService.isAvailable();
                } catch (RemoteException e) {
                }
                FfmpegDecoderClient.this.notify();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            synchronized (FfmpegDecoderClient.this) {
                sInstance.releaseLocked();
                mIsAvailable = false;
                mService = null;
            }
        }
    };

    /**
     * Connects to the decoder service for future uses.
     * @param context
     * @return {@code true} when decoder service is connected.
     */
    @MainThread
    public synchronized static boolean connect(Context context) {
        if (Features.AC3_SOFTWARE_DECODE.isEnabled(context)) {
            if (sInstance == null) {
                sInstance = new FfmpegDecoderClient();
                Intent intent =
                        new Intent(FFMPEG_DECODER_SERVICE_FILTER)
                                .setComponent(
                                        new ComponentName(context, FfmpegDecoderService.class));
                if (context.bindService(intent, sInstance.mConnection, Context.BIND_AUTO_CREATE)) {
                    return true;
                } else {
                    sInstance = null;
                }
            }
        }
        return false;
    }

    /**
     * Disconnects from the decoder service and release resources.
     * @param context
     */
    @MainThread
    public synchronized static void disconnect(Context context) {
        if (sInstance != null) {
            synchronized (sInstance) {
                sInstance.releaseLocked();
                if (sInstance.mIsAvailable != null && sInstance.mIsAvailable) {
                    context.unbindService(sInstance.mConnection);
                }
                sInstance.mIsAvailable = false;
                sInstance.mService = null;
            }
            sInstance = null;
        }
    }

    /**
     * Returns whether service is available or not.
     * Before using client, this should be used to check availability.
     */
    @WorkerThread
    public synchronized static boolean isAvailable() {
        if (sInstance != null) {
            return sInstance.available();
        }
        return false;
    }

    /**
     * Returns an client instance.
     */
    public synchronized static FfmpegDecoderClient getInstance() {
        if (sInstance != null) {
            sInstance.createDecoder();
        }
        return sInstance;
    }

    private FfmpegDecoderClient() {
    }

    private synchronized boolean available() {
        if (mIsAvailable == null) {
            try {
                this.wait(FFMPEG_SERVICE_CONNECT_TIMEOUT_MS);
            } catch (InterruptedException e) {
            }
        }
        return mIsAvailable != null && mIsAvailable == true;
    }

    private synchronized void createDecoder() {
        if (mIsAvailable == null || mIsAvailable == false) {
            return;
        }
        try {
            mService.create();
        } catch (RemoteException e) {
        }
    }

    private void releaseLocked() {
        if (mIsAvailable == null || mIsAvailable == false) {
            return;
        }
        try {
          mService.release();
        } catch (RemoteException e) {
        }
    }

    @Override
    public synchronized void release() {
        releaseLocked();
    }

    @Override
    public synchronized void decode(SampleHolder sampleHolder) {
        if (mIsAvailable == null || mIsAvailable == false) {
            return;
        }
        byte[] sampleBytes = new byte [sampleHolder.data.limit()];
        sampleHolder.data.get(sampleBytes, 0, sampleBytes.length);
        try {
            mService.decode(sampleHolder.timeUs, sampleBytes);
        } catch (RemoteException e) {
        }
    }

    @Override
    public synchronized void resetDecoderState(String mimeType) {
        if (mIsAvailable == null || mIsAvailable == false) {
            return;
        }
        try {
            mService.resetDecoderState(mimeType);
        } catch (RemoteException e) {
        }
    }

    @Override
    public synchronized ByteBuffer getDecodedSample() {
        if (mIsAvailable == null || mIsAvailable == false) {
            return null;
        }
        try {
            byte[] outputBytes = mService.getDecodedSample();
            if (outputBytes != null && outputBytes.length > 0) {
                return ByteBuffer.wrap(outputBytes);
            }
        } catch (RemoteException e) {
        }
        return null;
    }

    @Override
    public synchronized long getDecodedTimeUs() {
        if (mIsAvailable == null || mIsAvailable == false) {
            return 0;
        }
        try {
            return mService.getDecodedTimeUs();
        } catch (RemoteException e) {
        }
        return 0;
    }

    @VisibleForTesting
    public boolean testSandboxIsolatedProcess() {
        // When testing isolated process, we will check the permission in FfmpegDecoderService.
        // If the service have any permission, an exception will be thrown.
        try {
            mService.testSandboxIsolatedProcess();
        } catch (RemoteException e) {
            return false;
        }
        return true;
    }

    @VisibleForTesting
    public void testSandboxMinijail() {
        // When testing minijail, we will call a system call which is blocked by minijail. In that
        // case, the FfmpegDecoderService will be disconnected, we can check the connection status
        // to make sure if the minijail works or not.
        try {
            mService.testSandboxMinijail();
        } catch (RemoteException e) {
        }
    }
}
