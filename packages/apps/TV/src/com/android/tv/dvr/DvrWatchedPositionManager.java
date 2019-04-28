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
 * limitations under the License
 */

package com.android.tv.dvr;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.tv.TvInputManager;
import android.support.annotation.IntDef;

import com.android.tv.common.SharedPreferencesUtils;
import com.android.tv.dvr.data.RecordedProgram;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * A class to manage DVR watched state.
 * It will remember and provides previous watched position of DVR playback.
 */
public class DvrWatchedPositionManager {
    private SharedPreferences mWatchedPositions;
    private final Map<Long, Set> mListeners = new HashMap<>();

    /**
     * The minimum percentage of recorded program being watched that will be considered as being
     * completely watched.
     */
    public static final float DVR_WATCHED_THRESHOLD_RATE = 0.98f;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({DVR_WATCHED_STATUS_NEW, DVR_WATCHED_STATUS_WATCHING, DVR_WATCHED_STATUS_WATCHED})
    public @interface DvrWatchedStatus {}
    /**
     * The status indicates the recorded program has not been watched at all.
     */
    public static final int DVR_WATCHED_STATUS_NEW = 0;
    /**
     * The status indicates the recorded program is being watched.
     */
    public static final int DVR_WATCHED_STATUS_WATCHING = 1;
    /**
     * The status indicates the recorded program was completely watched.
     */
    public static final int DVR_WATCHED_STATUS_WATCHED = 2;

    public DvrWatchedPositionManager(Context context) {
        mWatchedPositions = context.getSharedPreferences(
                SharedPreferencesUtils.SHARED_PREF_DVR_WATCHED_POSITION, Context.MODE_PRIVATE);
    }

    /**
     * Sets the watched position of the give program.
     */
    public void setWatchedPosition(long recordedProgramId, long positionMs) {
        mWatchedPositions.edit().putLong(Long.toString(recordedProgramId), positionMs).apply();
        notifyWatchedPositionChanged(recordedProgramId, positionMs);
    }

    /**
     * Gets the watched position of the give program.
     */
    public long getWatchedPosition(long recordedProgramId) {
        return mWatchedPositions.getLong(Long.toString(recordedProgramId),
                TvInputManager.TIME_SHIFT_INVALID_TIME);
    }

    @DvrWatchedStatus public int getWatchedStatus(RecordedProgram recordedProgram) {
        long watchedPosition = getWatchedPosition(recordedProgram.getId());
        if (watchedPosition == TvInputManager.TIME_SHIFT_INVALID_TIME) {
            return DVR_WATCHED_STATUS_NEW;
        } else if (watchedPosition > recordedProgram
                .getDurationMillis() * DVR_WATCHED_THRESHOLD_RATE) {
            return DVR_WATCHED_STATUS_WATCHED;
        } else {
            return DVR_WATCHED_STATUS_WATCHING;
        }
    }

    /**
     * Adds {@link WatchedPositionChangedListener}.
     */
    public void addListener(WatchedPositionChangedListener listener, long recordedProgramId) {
        if (recordedProgramId == RecordedProgram.ID_NOT_SET) {
            return;
        }
        Set<WatchedPositionChangedListener> listenerSet = mListeners.get(recordedProgramId);
        if (listenerSet == null) {
            listenerSet = new CopyOnWriteArraySet<>();
            mListeners.put(recordedProgramId, listenerSet);
        }
        listenerSet.add(listener);
    }

    /**
     * Removes {@link WatchedPositionChangedListener}.
     */
    public void removeListener(WatchedPositionChangedListener listener) {
        for (long recordedProgramId : new ArrayList<>(mListeners.keySet())) {
            removeListener(listener, recordedProgramId);
        }
    }

    /**
     * Removes {@link WatchedPositionChangedListener}.
     */
    public void removeListener(WatchedPositionChangedListener listener, long recordedProgramId) {
        Set<WatchedPositionChangedListener> listenerSet = mListeners.get(recordedProgramId);
        if (listenerSet == null) {
            return;
        }
        listenerSet.remove(listener);
        if (listenerSet.isEmpty()) {
            mListeners.remove(recordedProgramId);
        }
    }

    private void notifyWatchedPositionChanged(long recordedProgramId, long positionMs) {
        Set<WatchedPositionChangedListener> listenerSet = mListeners.get(recordedProgramId);
        if (listenerSet == null) {
            return;
        }
        for (WatchedPositionChangedListener listener : listenerSet) {
            listener.onWatchedPositionChanged(recordedProgramId, positionMs);
        }
    }

    public interface WatchedPositionChangedListener {
        /**
         * Called when the watched position of some program is changed.
         */
        void onWatchedPositionChanged(long recordedProgramId, long positionMs);
    }
}
