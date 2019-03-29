/*
 * Copyright (C) 2009 The Android Open Source Project
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
package android.speech.tts.cts;

import android.content.Context;
import android.media.MediaPlayer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.Assert;

/**
 * Wrapper for {@link TextToSpeech} with some handy test functionality.
 */
public class TextToSpeechWrapper {
    private static final String LOG_TAG = "TextToSpeechServiceTest";

    public static final String MOCK_TTS_ENGINE = "android.speech.tts.cts";

    private final Context mContext;
    private TextToSpeech mTts;
    private final InitWaitListener mInitListener;
    private final UtteranceWaitListener mUtteranceListener;

    /** maximum time to wait for tts to be initialized */
    private static final int TTS_INIT_MAX_WAIT_TIME = 30 * 1000;
    /** maximum time to wait for speech call to be complete */
    private static final int TTS_SPEECH_MAX_WAIT_TIME = 5 * 1000;

    private TextToSpeechWrapper(Context context) {
        mContext = context;
        mInitListener = new InitWaitListener();
        mUtteranceListener = new UtteranceWaitListener();
    }

    private boolean initTts() throws InterruptedException {
        return initTts(new TextToSpeech(mContext, mInitListener));
    }

    private boolean initTts(String engine) throws InterruptedException {
        return initTts(new TextToSpeech(mContext, mInitListener, engine));
    }

    private boolean initTts(TextToSpeech tts) throws InterruptedException {
        mTts = tts;
        if (!mInitListener.waitForInit()) {
            return false;
        }
        mTts.setOnUtteranceProgressListener(mUtteranceListener);
        return true;
    }

    public boolean waitForComplete(String utteranceId) throws InterruptedException {
        return mUtteranceListener.waitForComplete(utteranceId);
    }

    public boolean waitForStop(String utteranceId) throws InterruptedException {
        return mUtteranceListener.waitForStop(utteranceId);
    }

    public TextToSpeech getTts() {
        return mTts;
    }

    public void shutdown() {
        mTts.shutdown();
    }

    public final Map<String, Integer> chunksReceived() {
        return mUtteranceListener.chunksReceived();
    }

    public final Map<String, List<Integer>> timePointsStart() {
        return mUtteranceListener.timePointsStart();
    }

    public final Map<String, List<Integer>> timePointsEnd() {
        return mUtteranceListener.timePointsEnd();
    }

    public final Map<String, List<Integer>> timePointsFrame() {
        return mUtteranceListener.timePointsFrame();
    }

    /**
     * Sanity checks that the utteranceIds and only the utteranceIds completed and produced the
     * correct callbacks.
     * Can only be used when the test knows exactly which utterances should have been finished when
     * this call is made. Else use waitForStop(String) or waitForComplete(String).
     */
    public void verify(String... utteranceIds) {
        mUtteranceListener.verify(utteranceIds);
    }

    public static TextToSpeechWrapper createTextToSpeechWrapper(Context context)
            throws InterruptedException {
        TextToSpeechWrapper wrapper = new TextToSpeechWrapper(context);
        if (wrapper.initTts()) {
            return wrapper;
        } else {
            return null;
        }
    }

    public static TextToSpeechWrapper createTextToSpeechMockWrapper(Context context)
            throws InterruptedException {
        TextToSpeechWrapper wrapper = new TextToSpeechWrapper(context);
        if (wrapper.initTts(MOCK_TTS_ENGINE)) {
            return wrapper;
        } else {
            return null;
        }
    }

    /**
     * Listener for waiting for TTS engine initialization completion.
     */
    private static class InitWaitListener implements OnInitListener {
        private final Lock mLock = new ReentrantLock();
        private final Condition mDone  = mLock.newCondition();
        private Integer mStatus = null;

        public void onInit(int status) {
            mLock.lock();
            try {
                mStatus = new Integer(status);
                mDone.signal();
            } finally {
                mLock.unlock();
            }
        }

        public boolean waitForInit() throws InterruptedException {
            long timeOutNanos = TimeUnit.MILLISECONDS.toNanos(TTS_INIT_MAX_WAIT_TIME);
            mLock.lock();
            try {
                while (mStatus == null) {
                    if (timeOutNanos <= 0) {
                        return false;
                    }
                    timeOutNanos = mDone.awaitNanos(timeOutNanos);
                }
                return mStatus == TextToSpeech.SUCCESS;
            } finally {
                mLock.unlock();
            }
        }
    }

    /**
     * Listener for waiting for utterance completion.
     */
    private static class UtteranceWaitListener extends UtteranceProgressListener {
        private final Lock mLock = new ReentrantLock();
        private final Condition mDone  = mLock.newCondition();
        private final Set<String> mStartedUtterances = new HashSet<>();
        // Contains the list of utterances that are stopped. Entry is removed after waitForStop().
        private final Set<String> mStoppedUtterances = new HashSet<>();
        private final Map<String, Integer> mErredUtterances = new HashMap<>();
        // Contains the list of utterances that are completed. Entry is removed after
        // waitForComplete().
        private final Set<String> mCompletedUtterances = new HashSet<>();
        private final Set<String> mBeginSynthesisUtterances = new HashSet<>();
        private final Map<String, Integer> mChunksReceived = new HashMap<>();
        private final Map<String, List<Integer>> mTimePointsStart = new HashMap<>();
        private final Map<String, List<Integer>> mTimePointsEnd = new HashMap<>();
        private final Map<String, List<Integer>> mTimePointsFrame = new HashMap<>();

        @Override
        public void onDone(String utteranceId) {
            mLock.lock();
            try {
                Assert.assertTrue(mStartedUtterances.contains(utteranceId));
                mCompletedUtterances.add(utteranceId);
                mDone.signal();
            } finally {
                mLock.unlock();
            }
        }

        @Override
        public void onError(String utteranceId) {
            mLock.lock();
            try {
                mErredUtterances.put(utteranceId, -1);
                mDone.signal();
            } finally {
                mLock.unlock();
            }
        }

        @Override
        public void onError(String utteranceId, int errorCode) {
            mLock.lock();
            try {
                mErredUtterances.put(utteranceId, errorCode);
                mDone.signal();
            } finally {
                mLock.unlock();
            }
        }

        @Override
        public void onStart(String utteranceId) {
            mLock.lock();
            try {
                // TODO: Due to a bug in the framework onStart() is called twice for
                //       synthesizeToFile requests. Once that is fixed we should assert here that we
                //       expect only one onStart() per utteranceId.
                mStartedUtterances.add(utteranceId);
            } finally {
                mLock.unlock();
            }
        }

        @Override
        public void onStop(String utteranceId, boolean isStarted) {
            mLock.lock();
            try {
                mStoppedUtterances.add(utteranceId);
                mDone.signal();
            } finally {
                mLock.unlock();
            }
        }

        @Override
        public void onBeginSynthesis(String utteranceId, int sampleRateInHz, int audioFormat, int channelCount) {
            Assert.assertNotNull(utteranceId);
            Assert.assertTrue(sampleRateInHz > 0);
            Assert.assertTrue(audioFormat == android.media.AudioFormat.ENCODING_PCM_8BIT
                              || audioFormat == android.media.AudioFormat.ENCODING_PCM_16BIT
                              || audioFormat == android.media.AudioFormat.ENCODING_PCM_FLOAT);
            Assert.assertTrue(channelCount >= 1);
            Assert.assertTrue(channelCount <= 2);
            mLock.lock();
            try {
                mBeginSynthesisUtterances.add(utteranceId);
            } finally {
                mLock.unlock();
            }
        }

        @Override
        public void onAudioAvailable(String utteranceId, byte[] audio) {
            Assert.assertNotNull(utteranceId);
            Assert.assertTrue(audio.length > 0);
            mLock.lock();
            try {
                Assert.assertTrue(mBeginSynthesisUtterances.contains(utteranceId));
                if (mChunksReceived.get(utteranceId) != null) {
                    mChunksReceived.put(utteranceId, mChunksReceived.get(utteranceId) + 1);
                } else {
                    mChunksReceived.put(utteranceId, 1);
                }
            } finally {
                mLock.unlock();
            }
        }

        @Override
        public void onRangeStart(String utteranceId, int start, int end, int frame) {
            Assert.assertNotNull(utteranceId);
            mLock.lock();
            try {
                Assert.assertTrue(mBeginSynthesisUtterances.contains(utteranceId));
                if (mTimePointsStart.get(utteranceId) == null) {
                    mTimePointsStart.put(utteranceId, new ArrayList<Integer>());
                    mTimePointsEnd.put(utteranceId, new ArrayList<Integer>());
                    mTimePointsFrame.put(utteranceId, new ArrayList<Integer>());
                }
                mTimePointsStart.get(utteranceId).add(start);
                mTimePointsEnd.get(utteranceId).add(end);
                mTimePointsFrame.get(utteranceId).add(frame);
            } finally {
                mLock.unlock();
            }
        }

        public boolean waitForComplete(String utteranceId)
                throws InterruptedException {
            long timeOutNanos = TimeUnit.MILLISECONDS.toNanos(TTS_INIT_MAX_WAIT_TIME);
            mLock.lock();
            try {
                while (!mCompletedUtterances.remove(utteranceId)) {
                    if (timeOutNanos <= 0) {
                        return false;
                    }
                    timeOutNanos = mDone.awaitNanos(timeOutNanos);
                }
                return true;
            } finally {
                mLock.unlock();
            }
        }

        public boolean waitForStop(String utteranceId)
                throws InterruptedException {
            long timeOutNanos = TimeUnit.MILLISECONDS.toNanos(TTS_INIT_MAX_WAIT_TIME);
            mLock.lock();
            try {
                while (!mStoppedUtterances.remove(utteranceId)) {
                    if (timeOutNanos <= 0) {
                        return false;
                    }
                    timeOutNanos = mDone.awaitNanos(timeOutNanos);
                }
                return true;
            } finally {
                mLock.unlock();
            }
        }

        public final Map<String, Integer> chunksReceived() {
            return mChunksReceived;
        }

        public final Map<String, List<Integer>> timePointsStart() {
            return mTimePointsStart;
        }

        public final Map<String, List<Integer>> timePointsEnd() {
            return mTimePointsEnd;
        }

        public final Map<String, List<Integer>> timePointsFrame() {
            return mTimePointsFrame;
        }

        public void verify(String... utteranceIds) {
            Assert.assertTrue(utteranceIds.length == mStartedUtterances.size());
            for (String id : utteranceIds) {
                Assert.assertTrue(mStartedUtterances.contains(id));
                Assert.assertTrue(mBeginSynthesisUtterances.contains(id));
                Assert.assertTrue(mChunksReceived.containsKey(id));
            }
        }
    }

    /**
     * Determines if given file path is a valid, playable music file.
     */
    public static boolean isSoundFile(String filePath) {
        // use media player to play the file. If it succeeds with no exceptions, assume file is
        //valid
        MediaPlayer mp = null;
        try {
            mp = new MediaPlayer();
            mp.setDataSource(filePath);
            mp.prepare();
            mp.start();
            mp.stop();
            return true;
        } catch (Exception e) {
            Log.e(LOG_TAG, "Exception while attempting to play music file", e);
            return false;
        } finally {
            if (mp != null) {
                mp.release();
            }
        }
    }

}
