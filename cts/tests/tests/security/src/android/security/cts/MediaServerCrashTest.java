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

package android.security.cts;

import android.content.res.AssetFileDescriptor;
import android.drm.DrmConvertedStatus;
import android.drm.DrmManagerClient;
import android.media.MediaPlayer;
import android.os.ConditionVariable;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.SecurityTest;
import android.test.AndroidTestCase;
import android.util.Log;

import com.android.compatibility.common.util.MediaUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

import android.security.cts.R;

@SecurityTest
public class MediaServerCrashTest extends AndroidTestCase {
    private static final String TAG = "MediaServerCrashTest";

    private static final String MIMETYPE_DRM_MESSAGE = "application/vnd.oma.drm.message";

    private String mFlFilePath;

    private final MediaPlayer mMediaPlayer = new MediaPlayer();
    private final ConditionVariable mOnPrepareCalled = new ConditionVariable();
    private final ConditionVariable mOnCompletionCalled = new ConditionVariable();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mFlFilePath = new File(Environment.getExternalStorageDirectory(),
                "temp.fl").getAbsolutePath();

        mOnPrepareCalled.close();
        mOnCompletionCalled.close();
        mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                assertTrue(mp == mMediaPlayer);
                assertTrue("mediaserver process died", what != MediaPlayer.MEDIA_ERROR_SERVER_DIED);
                Log.w(TAG, "onError " + what);
                return false;
            }
        });

        mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                assertTrue(mp == mMediaPlayer);
                mOnPrepareCalled.open();
            }
        });

        mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                assertTrue(mp == mMediaPlayer);
                mOnCompletionCalled.open();
            }
        });
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        File flFile = new File(mFlFilePath);
        if (flFile.exists()) {
            flFile.delete();
        }
    }

    public void testInvalidMidiNullPointerAccess() throws Exception {
        testIfMediaServerDied(R.raw.midi_crash);
    }

    private void testIfMediaServerDied(int res) throws Exception {
        AssetFileDescriptor afd = getContext().getResources().openRawResourceFd(res);
        mMediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
        afd.close();
        try {
            mMediaPlayer.prepareAsync();
            if (!mOnPrepareCalled.block(5000)) {
                Log.w(TAG, "testIfMediaServerDied: Timed out waiting for prepare");
                return;
            }
            mMediaPlayer.start();
            if (!mOnCompletionCalled.block(5000)) {
                Log.w(TAG, "testIfMediaServerDied: Timed out waiting for Error/Completion");
            }
        } catch (Exception e) {
            Log.w(TAG, "playback failed", e);
        } finally {
            mMediaPlayer.release();
        }
    }

    public void testDrmManagerClientReset() throws Exception {
        checkIfMediaServerDiedForDrm(R.raw.drm_uaf);
    }

    private void checkIfMediaServerDiedForDrm(int res) throws Exception {
        AssetFileDescriptor afd = mContext.getResources().openRawResourceFd(res);
        FileInputStream dmStream = afd.createInputStream();
        RandomAccessFile flFile = new RandomAccessFile(mFlFilePath, "rw");
        if (!MediaUtils.convertDmToFl(mContext, dmStream, flFile)) {
            Log.w(TAG, "Can not convert dm to fl, skip checkIfMediaServerDiedForDrm");
            mMediaPlayer.release();
            return;
        }
        dmStream.close();
        flFile.close();
        Log.d(TAG, "intermediate fl file is " + mFlFilePath);

        ParcelFileDescriptor flFd = null;
        try {
            flFd = ParcelFileDescriptor.open(new File(mFlFilePath),
                    ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (FileNotFoundException e) {
            fail("Could not find file: " + mFlFilePath +  e);
        }

        mMediaPlayer.setDataSource(flFd.getFileDescriptor(), 0, flFd.getStatSize());
        flFd.close();
        try {
            mMediaPlayer.prepare();
        } catch (Exception e) {
            Log.d(TAG, "Prepare failed", e);
        }

        try {
            mMediaPlayer.reset();
            if (!mOnCompletionCalled.block(5000)) {
                Log.w(TAG, "checkIfMediaServerDiedForDrm: Timed out waiting for Error/Completion");
            }
        } catch (Exception e) {
            fail("reset failed" + e);
        } finally {
            mMediaPlayer.release();
        }
    }
}
