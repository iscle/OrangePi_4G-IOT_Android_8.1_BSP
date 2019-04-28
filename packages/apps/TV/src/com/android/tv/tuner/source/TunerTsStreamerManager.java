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

import com.android.tv.common.AutoCloseableUtils;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.tuner.TunerHal;
import com.android.tv.tuner.data.TunerChannel;
import com.android.tv.tuner.tvinput.EventDetector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Manages {@link TunerTsStreamer} for playback and recording.
 * The class hides handling of {@link TunerHal} from other classes.
 * This class is used by {@link TsDataSourceManager}. Don't use this class directly.
 */
class TunerTsStreamerManager {
    // The lock will protect mStreamerFinder, mSourceToStreamerMap and some part of TsStreamCreator
    // to support timely {@link TunerTsStreamer} cancellation due to a new tune request from
    // the same session.
    private final Object mCancelLock = new Object();
    private final StreamerFinder mStreamerFinder = new StreamerFinder();
    private final Map<Integer, TsStreamerCreator> mCreators = new HashMap<>();
    private final Map<Integer, EventDetector.EventListener> mListeners = new HashMap<>();
    private final Map<TsDataSource, TunerTsStreamer> mSourceToStreamerMap = new HashMap<>();
    private final TunerHalManager mTunerHalManager = new TunerHalManager();
    private static TunerTsStreamerManager sInstance;

    /**
     * Returns the singleton instance for the class
     * @return TunerTsStreamerManager
     */
    static synchronized TunerTsStreamerManager getInstance() {
        if (sInstance == null) {
            sInstance = new TunerTsStreamerManager();
        }
        return sInstance;
    }

    private TunerTsStreamerManager() { }

    synchronized TsDataSource createDataSource(
            Context context, TunerChannel channel, EventDetector.EventListener listener,
            int sessionId, boolean reuse) {
        TsStreamerCreator creator;
        synchronized (mCancelLock) {
            if (mStreamerFinder.containsLocked(channel)) {
                mStreamerFinder.appendSessionLocked(channel, sessionId);
                TunerTsStreamer streamer =  mStreamerFinder.getStreamerLocked(channel);
                TsDataSource source = streamer.createDataSource();
                mListeners.put(sessionId, listener);
                streamer.registerListener(listener);
                mSourceToStreamerMap.put(source, streamer);
                return source;
            }
            creator = new TsStreamerCreator(context, channel, listener);
            mCreators.put(sessionId, creator);
        }
        TunerTsStreamer streamer = creator.create(sessionId, reuse);
        synchronized (mCancelLock) {
            mCreators.remove(sessionId);
            if (streamer == null) {
                return null;
            }
            if (!creator.isCancelledLocked()) {
                mStreamerFinder.putLocked(channel, sessionId, streamer);
                TsDataSource source = streamer.createDataSource();
                mListeners.put(sessionId, listener);
                mSourceToStreamerMap.put(source, streamer);
                return source;
            }
        }
        // Created streamer was cancelled by a new tune request.
        streamer.stopStream();
        TunerHal hal = streamer.getTunerHal();
        hal.setHasPendingTune(false);
        mTunerHalManager.releaseTunerHal(hal, sessionId, reuse);
        return null;
    }

    synchronized void releaseDataSource(TsDataSource source, int sessionId,
            boolean reuse) {
        TunerTsStreamer streamer;
        synchronized (mCancelLock) {
            streamer = mSourceToStreamerMap.get(source);
            mSourceToStreamerMap.remove(source);
            if (streamer == null) {
                return;
            }
            EventDetector.EventListener listener = mListeners.remove(sessionId);
            streamer.unregisterListener(listener);
            TunerChannel channel = streamer.getChannel();
            SoftPreconditions.checkState(channel != null);
            mStreamerFinder.removeSessionLocked(channel, sessionId);
            if (mStreamerFinder.containsLocked(channel)) {
                return;
            }
        }
        streamer.stopStream();
        TunerHal hal = streamer.getTunerHal();
        hal.setHasPendingTune(false);
        mTunerHalManager.releaseTunerHal(hal, sessionId, reuse);
    }

    void setHasPendingTune(int sessionId) {
        synchronized (mCancelLock) {
           if (mCreators.containsKey(sessionId)) {
               mCreators.get(sessionId).cancelLocked();
           }
        }
    }

    /**
     * Add tuner hal into TunerHalManager for test.
     */
    void addTunerHal(TunerHal tunerHal, int sessionId) {
        mTunerHalManager.addTunerHal(tunerHal, sessionId);
    }

    synchronized void release(int sessionId) {
        mTunerHalManager.releaseCachedHal(sessionId);
    }

    private class StreamerFinder {
        private final Map<TunerChannel, Set<Integer>> mSessions = new HashMap<>();
        private final Map<TunerChannel, TunerTsStreamer> mStreamers = new HashMap<>();

        // @GuardedBy("mCancelLock")
        private void putLocked(TunerChannel channel, int sessionId, TunerTsStreamer streamer) {
            Set<Integer> sessions = new HashSet<>();
            sessions.add(sessionId);
            mSessions.put(channel, sessions);
            mStreamers.put(channel, streamer);
        }

        // @GuardedBy("mCancelLock")
        private void appendSessionLocked(TunerChannel channel, int sessionId) {
            if (mSessions.containsKey(channel)) {
                mSessions.get(channel).add(sessionId);
            }
        }

        // @GuardedBy("mCancelLock")
        private void removeSessionLocked(TunerChannel channel, int sessionId) {
            Set<Integer> sessions = mSessions.get(channel);
            sessions.remove(sessionId);
            if (sessions.size() == 0) {
                mSessions.remove(channel);
                mStreamers.remove(channel);
            }
        }

        // @GuardedBy("mCancelLock")
        private boolean containsLocked(TunerChannel channel) {
            return mSessions.containsKey(channel);
        }

        // @GuardedBy("mCancelLock")
        private TunerTsStreamer getStreamerLocked(TunerChannel channel) {
            return mStreamers.containsKey(channel) ? mStreamers.get(channel) : null;
        }
    }

    /**
     * {@link TunerTsStreamer} creation can be cancelled by a new tune request for the same
     * session. The class supports the cancellation in creating new {@link TunerTsStreamer}.
     */
    private class TsStreamerCreator {
        private final Context mContext;
        private final TunerChannel mChannel;
        private final EventDetector.EventListener mEventListener;
        // mCancelled will be {@code true} if a new tune request for the same session
        // cancels create().
        private boolean mCancelled;
        private TunerHal mTunerHal;

        private TsStreamerCreator(Context context, TunerChannel channel,
                EventDetector.EventListener listener) {
            mContext = context;
            mChannel = channel;
            mEventListener = listener;
        }

        private TunerTsStreamer create(int sessionId, boolean reuse) {
            TunerHal hal = mTunerHalManager.getOrCreateTunerHal(mContext, sessionId);
            if (hal == null) {
                return null;
            }
            boolean canceled = false;
            synchronized (mCancelLock) {
                if (!mCancelled) {
                    mTunerHal = hal;
                } else {
                    canceled = true;
                }
            }
            if (!canceled) {
                TunerTsStreamer tsStreamer = new TunerTsStreamer(hal, mEventListener, mContext);
                if (tsStreamer.startStream(mChannel)) {
                    return tsStreamer;
                }
                synchronized (mCancelLock) {
                    mTunerHal = null;
                }
            }
            hal.setHasPendingTune(false);
            // Since TunerTsStreamer is not properly created, closes TunerHal.
            // And do not re-use TunerHal when it is not cancelled.
            mTunerHalManager.releaseTunerHal(hal, sessionId, mCancelled && reuse);
            return null;
        }

        // @GuardedBy("mCancelLock")
        private void cancelLocked() {
                if (mCancelled) {
                    return;
                }
                mCancelled = true;
                if (mTunerHal != null) {
                    mTunerHal.setHasPendingTune(true);
                }
        }

        // @GuardedBy("mCancelLock")
        private boolean isCancelledLocked() {
                return mCancelled;
        }
    }

    /**
     * Supports sharing {@link TunerHal} among multiple sessions.
     * The class also supports session affinity for {@link TunerHal} allocation.
     */
    private class TunerHalManager {
        private final Map<Integer, TunerHal> mTunerHals = new HashMap<>();

        private TunerHal getOrCreateTunerHal(Context context, int sessionId) {
            // Handles session affinity.
            TunerHal hal = mTunerHals.get(sessionId);
            if (hal != null) {
                mTunerHals.remove(sessionId);
                return hal;
            }
            // Finds a TunerHal which is cached for other sessions.
            Iterator it = mTunerHals.keySet().iterator();
            if (it.hasNext()) {
                Integer key = (Integer) it.next();
                hal = mTunerHals.get(key);
                mTunerHals.remove(key);
                return hal;
            }
            return TunerHal.createInstance(context);
        }

        private void releaseTunerHal(TunerHal hal, int sessionId, boolean reuse) {
            if (!reuse || !hal.isReusable()) {
                AutoCloseableUtils.closeQuietly(hal);
                return;
            }
            TunerHal cachedHal = mTunerHals.get(sessionId);
            if (cachedHal != hal) {
                mTunerHals.put(sessionId, hal);
                if (cachedHal != null) {
                    AutoCloseableUtils.closeQuietly(cachedHal);
                }
            }
        }

        private void releaseCachedHal(int sessionId) {
            TunerHal hal = mTunerHals.get(sessionId);
            if (hal != null) {
                mTunerHals.remove(sessionId);
            }
            if (hal != null) {
                AutoCloseableUtils.closeQuietly(hal);
            }
        }

        private void addTunerHal(TunerHal tunerHal, int sessionId) {
            mTunerHals.put(sessionId, tunerHal);
        }
    }
}