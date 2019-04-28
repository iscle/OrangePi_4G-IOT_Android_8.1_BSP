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

import android.util.Log;

import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.MediaClock;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.MediaFormatHolder;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.util.Assertions;
import com.android.tv.tuner.cc.Cea708Parser;
import com.android.tv.tuner.data.Cea708Data.CaptionEvent;

import java.io.IOException;

/**
 * A {@link TrackRenderer} for CEA-708 textual subtitles.
 */
public class Cea708TextTrackRenderer extends TrackRenderer implements
        Cea708Parser.OnCea708ParserListener {
    private static final String TAG = "Cea708TextTrackRenderer";
    private static final boolean DEBUG = false;

    public static final int MSG_SERVICE_NUMBER = 1;
    public static final int MSG_ENABLE_CLOSED_CAPTION = 2;

    // According to CEA-708B, the maximum value of closed caption bandwidth is 9600bps.
    private static final int DEFAULT_INPUT_BUFFER_SIZE = 9600 / 8;

    private final SampleSource.SampleSourceReader mSource;
    private final SampleHolder mSampleHolder;
    private final MediaFormatHolder mFormatHolder;
    private int mServiceNumber;
    private boolean mInputStreamEnded;
    private long mCurrentPositionUs;
    private long mPresentationTimeUs;
    private int mTrackIndex;
    private boolean mRenderingDisabled;
    private Cea708Parser mCea708Parser;
    private CcListener mCcListener;

    public interface CcListener {
        void emitEvent(CaptionEvent captionEvent);
        void clearCaption();
        void discoverServiceNumber(int serviceNumber);
    }

    public Cea708TextTrackRenderer(SampleSource source) {
        mSource = source.register();
        mTrackIndex = -1;
        mSampleHolder = new SampleHolder(SampleHolder.BUFFER_REPLACEMENT_MODE_DIRECT);
        mSampleHolder.ensureSpaceForWrite(DEFAULT_INPUT_BUFFER_SIZE);
        mFormatHolder = new MediaFormatHolder();
    }

    @Override
    protected MediaClock getMediaClock() {
        return null;
    }

    private boolean handlesMimeType(String mimeType) {
        return mimeType.equals(MpegTsSampleExtractor.MIMETYPE_TEXT_CEA_708);
    }

    @Override
    protected boolean doPrepare(long positionUs) throws ExoPlaybackException {
        boolean sourcePrepared = mSource.prepare(positionUs);
        if (!sourcePrepared) {
            return false;
        }
        int trackCount = mSource.getTrackCount();
        for (int i = 0; i < trackCount; ++i) {
            MediaFormat trackFormat = mSource.getFormat(i);
            if (handlesMimeType(trackFormat.mimeType)) {
                mTrackIndex = i;
                clearDecodeState();
                return true;
            }
        }
        // TODO: Check this case. (Source do not have the proper mime type.)
        return true;
    }

    @Override
    protected void onEnabled(int track, long positionUs, boolean joining) {
        Assertions.checkArgument(mTrackIndex != -1 && track == 0);
        mSource.enable(mTrackIndex, positionUs);
        mInputStreamEnded = false;
        mPresentationTimeUs = positionUs;
        mCurrentPositionUs = Long.MIN_VALUE;
    }

    @Override
    protected void onDisabled() {
        mSource.disable(mTrackIndex);
    }

    @Override
    protected void onReleased() {
        mSource.release();
        mCea708Parser = null;
    }

    @Override
    protected boolean isEnded() {
        return mInputStreamEnded;
    }

    @Override
    protected boolean isReady() {
        // Since this track will be fed by {@link VideoTrackRenderer},
        // it is not required to control transition between ready state and buffering state.
        return true;
    }

    @Override
    protected int getTrackCount() {
        return mTrackIndex < 0 ? 0 : 1;
    }

    @Override
    protected MediaFormat getFormat(int track) {
        Assertions.checkArgument(mTrackIndex != -1 && track == 0);
        return mSource.getFormat(mTrackIndex);
    }

    @Override
    protected void maybeThrowError() throws ExoPlaybackException {
        try {
            mSource.maybeThrowError();
        } catch (IOException e) {
            throw new ExoPlaybackException(e);
        }
    }

    @Override
    protected void doSomeWork(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
        try {
            mPresentationTimeUs = positionUs;
            if (!mInputStreamEnded) {
                processOutput();
                feedInputBuffer();
            }
        } catch (IOException e) {
            throw new ExoPlaybackException(e);
        }
    }

    private boolean processOutput() {
        return !mInputStreamEnded && mCea708Parser != null &&
                mCea708Parser.processClosedCaptions(mPresentationTimeUs);
    }

    private boolean feedInputBuffer() throws IOException, ExoPlaybackException {
        if (mInputStreamEnded) {
            return false;
        }
        long discontinuity = mSource.readDiscontinuity(mTrackIndex);
        if (discontinuity != SampleSource.NO_DISCONTINUITY) {
            if (DEBUG) {
                Log.d(TAG, "Read discontinuity happened");
            }

            // TODO: handle input discontinuity for trickplay.
            clearDecodeState();
            mPresentationTimeUs = discontinuity;
            return false;
        }
        mSampleHolder.data.clear();
        mSampleHolder.size = 0;
        int result = mSource.readData(mTrackIndex, mPresentationTimeUs,
                mFormatHolder, mSampleHolder);
        switch (result) {
            case SampleSource.NOTHING_READ: {
                return false;
            }
            case SampleSource.FORMAT_READ: {
                if (DEBUG) {
                    Log.i(TAG, "Format was read again");
                }
                return true;
            }
            case SampleSource.END_OF_STREAM: {
                if (DEBUG) {
                    Log.i(TAG, "End of stream from SampleSource");
                }
                mInputStreamEnded = true;
                return false;
            }
            case SampleSource.SAMPLE_READ: {
                mSampleHolder.data.flip();
                if (mCea708Parser != null && !mRenderingDisabled) {
                    mCea708Parser.parseClosedCaption(mSampleHolder.data, mSampleHolder.timeUs);
                }
                return true;
            }
        }
        return false;
    }

    private void clearDecodeState() {
        mCea708Parser = new Cea708Parser();
        mCea708Parser.setListener(this);
        mCea708Parser.setListenServiceNumber(mServiceNumber);
    }

    @Override
    protected long getDurationUs() {
        return mSource.getFormat(mTrackIndex).durationUs;
    }

    @Override
    protected long getBufferedPositionUs() {
        return mSource.getBufferedPositionUs();
    }

    @Override
    protected void seekTo(long currentPositionUs) throws ExoPlaybackException {
        mSource.seekToUs(currentPositionUs);
        mInputStreamEnded = false;
        mPresentationTimeUs = currentPositionUs;
        mCurrentPositionUs = Long.MIN_VALUE;
    }

    @Override
    protected void onStarted() {
        // do nothing.
    }

    @Override
    protected void onStopped() {
        // do nothing.
    }

    private void setServiceNumber(int serviceNumber) {
        mServiceNumber = serviceNumber;
        if (mCea708Parser != null) {
            mCea708Parser.setListenServiceNumber(serviceNumber);
        }
    }

    @Override
    public void emitEvent(CaptionEvent event) {
        if (mCcListener != null) {
            mCcListener.emitEvent(event);
        }
    }

    @Override
    public void discoverServiceNumber(int serviceNumber) {
        if (mCcListener != null) {
            mCcListener.discoverServiceNumber(serviceNumber);
        }
    }

    public void setCcListener(CcListener ccListener) {
        mCcListener = ccListener;
    }

    @Override
    public void handleMessage(int messageType, Object message) throws ExoPlaybackException {
        switch (messageType) {
            case MSG_SERVICE_NUMBER:
                setServiceNumber((int) message);
                break;
            case MSG_ENABLE_CLOSED_CAPTION:
                boolean renderingDisabled = (Boolean) message == false;
                if (mRenderingDisabled != renderingDisabled) {
                    mRenderingDisabled = renderingDisabled;
                    if (mRenderingDisabled) {
                        if (mCea708Parser != null) {
                            mCea708Parser.clear();
                        }
                        if (mCcListener != null) {
                            mCcListener.clearCaption();
                        }
                    }
                }
                break;
            default:
                super.handleMessage(messageType, message);
        }
    }
}
