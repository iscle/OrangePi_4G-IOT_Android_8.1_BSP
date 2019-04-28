/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tv.tuner.source;

import android.content.Context;
import android.support.annotation.VisibleForTesting;

import com.android.tv.tuner.TunerHal;
import com.android.tv.tuner.data.TunerChannel;
import com.android.tv.tuner.data.nano.Channel;
import com.android.tv.tuner.tvinput.EventDetector;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages {@link DataSource} for playback and recording.
 * The class hides handling of {@link TunerHal} and {@link TsStreamer} from other classes.
 * One TsDataSourceManager should be created for per session.
 */
public class TsDataSourceManager {
    private static final Object sLock = new Object();
    private static final Map<TsDataSource, TsStreamer> sTsStreamers =
            new ConcurrentHashMap<>();

    private static int sSequenceId;

    private final int mId;
    private final boolean mIsRecording;
    private final TunerTsStreamerManager mTunerStreamerManager =
            TunerTsStreamerManager.getInstance();

    private boolean mKeepTuneStatus;

    /**
     * Creates TsDataSourceManager to create and release {@link DataSource} which will be
     * used for playing and recording.
     * @param isRecording {@code true} when for recording, {@code false} otherwise
     * @return {@link TsDataSourceManager}
     */
    public static TsDataSourceManager createSourceManager(boolean isRecording) {
        int id;
        synchronized (sLock) {
            id = ++sSequenceId;
        }
        return new TsDataSourceManager(id, isRecording);
    }

    private TsDataSourceManager(int id, boolean isRecording) {
        mId = id;
        mIsRecording = isRecording;
        mKeepTuneStatus = true;
    }

    /**
     * Creates or retrieves {@link TsDataSource} for playing or recording
     * @param context a {@link Context} instance
     * @param channel to play or record
     * @param eventListener for program information which will be scanned from MPEG2-TS stream
     * @return {@link TsDataSource} which will provide the specified channel stream
     */
    public TsDataSource createDataSource(Context context, TunerChannel channel,
            EventDetector.EventListener eventListener) {
        if (channel.getType() == Channel.TYPE_FILE) {
            // MPEG2 TS captured stream file recording is not supported.
            if (mIsRecording) {
                return null;
            }
            FileTsStreamer streamer = new FileTsStreamer(eventListener, context);
            if (streamer.startStream(channel)) {
                TsDataSource source = streamer.createDataSource();
                sTsStreamers.put(source, streamer);
                return source;
            }
            return null;
        }
        return mTunerStreamerManager.createDataSource(context, channel, eventListener,
                mId, !mIsRecording && mKeepTuneStatus);
    }

    /**
     * Releases the specified {@link TsDataSource} and underlying {@link TunerHal}.
     * @param source to release
     */
    public void releaseDataSource(TsDataSource source) {
        if (source instanceof TunerTsStreamer.TunerDataSource) {
            mTunerStreamerManager.releaseDataSource(
                    source, mId, !mIsRecording && mKeepTuneStatus);
        } else if (source instanceof FileTsStreamer.FileDataSource) {
            FileTsStreamer streamer = (FileTsStreamer) sTsStreamers.get(source);
            if (streamer != null) {
                sTsStreamers.remove(source);
                streamer.stopStream();
            }
        }
    }

    /**
     * Indicates that the current session has pending tunes.
     */
    public void setHasPendingTune() {
        mTunerStreamerManager.setHasPendingTune(mId);
    }

    /**
     * Indicates whether the underlying {@link TunerHal} should be kept or not when data source
     * is being released.
     * TODO: If b/30750953 is fixed, we can remove this function.
     * @param keepTuneStatus underlying {@link TunerHal} will be reused when data source releasing.
     */
    public void setKeepTuneStatus(boolean keepTuneStatus) {
        mKeepTuneStatus = keepTuneStatus;
    }

    /**
     * Add tuner hal into TunerTsStreamerManager for test.
     */
    @VisibleForTesting
    public void addTunerHalForTest(TunerHal tunerHal) {
        mTunerStreamerManager.addTunerHal(tunerHal, mId);
    }

    /**
     * Releases persistent resources.
     */
    public void release() {
        mTunerStreamerManager.release(mId);
    }
}
