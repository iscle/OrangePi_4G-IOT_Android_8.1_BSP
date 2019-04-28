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

package com.android.tv.tuner.exoplayer;

import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.MediaFormatHolder;
import com.google.android.exoplayer.MediaFormatUtil;
import com.google.android.exoplayer.SampleHolder;
import com.android.tv.tuner.exoplayer.buffer.BufferManager;
import com.android.tv.tuner.exoplayer.buffer.RecordingSampleBuffer;
import com.android.tv.tuner.tvinput.PlaybackBufferListener;

import android.os.Handler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A class that plays a recorded stream without using {@link android.media.MediaExtractor},
 * since all samples are extracted and stored to the permanent storage already.
 */
public class FileSampleExtractor implements SampleExtractor{
    private static final String TAG = "FileSampleExtractor";
    private static final boolean DEBUG = false;

    private int mTrackCount;
    private boolean mReleased;

    private final List<MediaFormat> mTrackFormats = new ArrayList<>();
    private final BufferManager mBufferManager;
    private final PlaybackBufferListener mBufferListener;
    private BufferManager.SampleBuffer mSampleBuffer;

    public FileSampleExtractor(
            BufferManager bufferManager, PlaybackBufferListener bufferListener) {
        mBufferManager = bufferManager;
        mBufferListener = bufferListener;
        mTrackCount = -1;
    }

    @Override
    public void maybeThrowError() throws IOException {
        // Do nothing.
    }

    @Override
    public boolean prepare() throws IOException {
        List<BufferManager.TrackFormat> trackFormatList = mBufferManager.readTrackInfoFiles();
        if (trackFormatList == null || trackFormatList.isEmpty()) {
            throw new IOException("Cannot find meta files for the recording.");
        }
        mTrackCount = trackFormatList.size();
        List<String> ids = new ArrayList<>();
        mTrackFormats.clear();
        for (int i = 0; i < mTrackCount; ++i) {
            BufferManager.TrackFormat trackFormat = trackFormatList.get(i);
            ids.add(trackFormat.trackId);
            mTrackFormats.add(MediaFormatUtil.createMediaFormat(trackFormat.format));
        }
        mSampleBuffer = new RecordingSampleBuffer(mBufferManager, mBufferListener, true,
                RecordingSampleBuffer.BUFFER_REASON_RECORDED_PLAYBACK);
        mSampleBuffer.init(ids, mTrackFormats);
        return true;
    }

    @Override
    public List<MediaFormat> getTrackFormats() {
        return mTrackFormats;
    }

    @Override
    public void getTrackMediaFormat(int track, MediaFormatHolder outMediaFormatHolder) {
        outMediaFormatHolder.format = mTrackFormats.get(track);
        outMediaFormatHolder.drmInitData = null;
    }

    @Override
    public void release() {
        if (!mReleased) {
            if (mSampleBuffer != null) {
                try {
                    mSampleBuffer.release();
                } catch (IOException e) {
                    // Do nothing. Playback ends now.
                }
            }
        }
        mReleased = true;
    }

    @Override
    public void selectTrack(int index) {
        mSampleBuffer.selectTrack(index);
    }

    @Override
    public void deselectTrack(int index) {
        mSampleBuffer.deselectTrack(index);
    }

    @Override
    public long getBufferedPositionUs() {
        return mSampleBuffer.getBufferedPositionUs();
    }

    @Override
    public void seekTo(long positionUs) {
        mSampleBuffer.seekTo(positionUs);
    }

    @Override
    public int readSample(int track, SampleHolder sampleHolder) {
        return mSampleBuffer.readSample(track, sampleHolder);
    }

    @Override
    public boolean continueBuffering(long positionUs) {
        return mSampleBuffer.continueBuffering(positionUs);
    }

    @Override
    public void setOnCompletionListener(OnCompletionListener listener, Handler handler) { }
}
