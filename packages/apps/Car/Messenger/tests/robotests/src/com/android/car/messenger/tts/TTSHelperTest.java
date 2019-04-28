package com.android.car.messenger.tts;

import android.speech.tts.TextToSpeech;

import com.android.car.messenger.TestConfig;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.Scheduler;

import java.util.Arrays;
import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = TestConfig.MANIFEST_PATH, sdk = TestConfig.SDK_VERSION)
public class TTSHelperTest {
    private static final long SHUTDOWN_DELAY_MILLIS = 10;

    private FakeTTSEngine mFakeTTS;
    private TTSHelper mTTSHelper;

    @Before
    public void setup() {
        mFakeTTS = new FakeTTSEngine();
        mTTSHelper = new TTSHelper(RuntimeEnvironment.application, mFakeTTS, SHUTDOWN_DELAY_MILLIS);
    }

    @Test
    public void testSpeakBeforeInit() {
        // Should get queued since engine not initialized.
        final RecordingTTSListener recorder1 = new RecordingTTSListener();
        mTTSHelper.requestPlay(Collections.singletonList("hello"), recorder1);
        // Will squash previous request.
        final RecordingTTSListener recorder2 = new RecordingTTSListener();
        mTTSHelper.requestPlay(Collections.singletonList("world"), recorder2);
        Assert.assertFalse(recorder1.wasStarted());
        Assert.assertTrue(recorder1.wasStoppedWithSuccess());

        // Finish initialization.
        mFakeTTS.mOnInitListener.onInit(TextToSpeech.SUCCESS);

        // The queued request should have been passed for playout.
        FakeTTSEngine.Request lastRequest = mFakeTTS.mRequests.getLast();
        Assert.assertEquals("world", lastRequest.mText);
        Assert.assertEquals(TextToSpeech.QUEUE_ADD, lastRequest.mQueueMode);

        mFakeTTS.startRequest(lastRequest.mUtteranceId);
        Assert.assertTrue(recorder2.wasStarted());
        mFakeTTS.finishRequest(lastRequest.mUtteranceId);
        Assert.assertTrue(recorder2.wasStoppedWithSuccess());
    }

    @Test
    public void testSpeakInterruptedByNext() {
        // First request made and starts playing.
        final RecordingTTSListener recorder1 = new RecordingTTSListener();
        mTTSHelper.requestPlay(Collections.singletonList("hello"), recorder1);
        // Finish initialization.
        mFakeTTS.mOnInitListener.onInit(TextToSpeech.SUCCESS);
        final FakeTTSEngine.Request request1 = mFakeTTS.mRequests.getLast();
        mFakeTTS.startRequest(request1.mUtteranceId);
        Assert.assertTrue(recorder1.wasStarted());

        // Second request made. It will flush first one.
        final RecordingTTSListener recorder2 = new RecordingTTSListener();
        mTTSHelper.requestPlay(Collections.singletonList("world"), recorder2);
        final FakeTTSEngine.Request request2 = mFakeTTS.mRequests.getLast();
        mFakeTTS.interruptRequest(request1.mUtteranceId, true /* interrupted */);
        Assert.assertTrue(recorder1.wasStoppedWithSuccess());
        mFakeTTS.startRequest(request2.mUtteranceId);
        Assert.assertTrue(recorder2.wasStarted());
        mFakeTTS.finishRequest(request2.mUtteranceId);
        Assert.assertTrue(recorder2.wasStoppedWithSuccess());
    }

    @Test
    public void testKeepAliveAndAutoShutdown() {
        // Request made and starts playing.
        final RecordingTTSListener recorder1 = new RecordingTTSListener();
        mTTSHelper.requestPlay(Collections.singletonList("hello"), recorder1);
        // Finish initialization.
        mFakeTTS.mOnInitListener.onInit(TextToSpeech.SUCCESS);
        final FakeTTSEngine.Request request1 = mFakeTTS.mRequests.getLast();
        mFakeTTS.startRequest(request1.mUtteranceId);
        Assert.assertTrue(recorder1.wasStarted());
        mFakeTTS.finishRequest(request1.mUtteranceId);
        Assert.assertTrue(recorder1.wasStoppedWithSuccess());

        Scheduler scheduler = Robolectric.getForegroundThreadScheduler();
        scheduler.advanceBy(SHUTDOWN_DELAY_MILLIS / 2);
        Assert.assertTrue(mFakeTTS.isInitialized());

        // Queue another request, forces keep-alive.
        final RecordingTTSListener recorder2 = new RecordingTTSListener();
        mTTSHelper.requestPlay(Collections.singletonList("world"), recorder2);
        final FakeTTSEngine.Request request2 = mFakeTTS.mRequests.getLast();
        mFakeTTS.failRequest(request2.mUtteranceId, -2 /* errorCode */);
        Assert.assertTrue(recorder2.wasStoppedWithError());
        Assert.assertTrue(mFakeTTS.isInitialized());

        scheduler.advanceBy(SHUTDOWN_DELAY_MILLIS);
        Assert.assertFalse(mFakeTTS.isInitialized());
    }

    @Test
    public void testBatchPlayout() {
        // Request made and starts playing.
        final RecordingTTSListener recorder1 = new RecordingTTSListener();
        mTTSHelper.requestPlay(Arrays.asList("hello", "world"), recorder1);
        // Finish initialization.
        mFakeTTS.mOnInitListener.onInit(TextToSpeech.SUCCESS);
        // Two requests should be queued from batch.
        Assert.assertEquals(2, mFakeTTS.mRequests.size());
        final FakeTTSEngine.Request request1 = mFakeTTS.mRequests.getFirst();
        final FakeTTSEngine.Request request2 = mFakeTTS.mRequests.getLast();
        mFakeTTS.startRequest(request1.mUtteranceId);
        Assert.assertTrue(recorder1.wasStarted());
        mFakeTTS.finishRequest(request1.mUtteranceId);
        Assert.assertFalse(recorder1.wasStoppedWithSuccess());
        mFakeTTS.startRequest(request2.mUtteranceId);
        Assert.assertTrue(recorder1.wasStarted());
        mFakeTTS.finishRequest(request2.mUtteranceId);
        Assert.assertTrue(recorder1.wasStoppedWithSuccess());
    }

    private static class RecordingTTSListener implements TTSHelper.Listener {
        private boolean mStarted = false;
        private boolean mStopped = false;
        private boolean mError = false;

        boolean wasStarted() {
            return mStarted;
        }

        boolean wasStoppedWithSuccess() {
            return mStopped && !mError;
        }

        boolean wasStoppedWithError() {
            return mStopped && mError;
        }

        @Override
        public void onTTSStarted() {
            mStarted = true;
        }

        @Override
        public void onTTSStopped(boolean error) {
            mStopped = true;
            mError = error;
        }
    }
}