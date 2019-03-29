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

package android.widget.cts;

import static com.android.compatibility.common.util.CtsMockitoUtils.within;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.media.MediaPlayer;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.LargeTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;
import android.view.View.MeasureSpec;
import android.widget.MediaController;
import android.widget.VideoView;

import com.android.compatibility.common.util.MediaUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * Test {@link VideoView}.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class VideoViewTest {
    /** Debug TAG. **/
    private static final String TAG = "VideoViewTest";
    /** The maximum time to wait for an operation. */
    private static final long   TIME_OUT = 15000L;
    /** The interval time to wait for completing an operation. */
    private static final long   OPERATION_INTERVAL  = 1500L;
    /** The duration of R.raw.testvideo. */
    private static final int    TEST_VIDEO_DURATION = 11047;
    /** The full name of R.raw.testvideo. */
    private static final String VIDEO_NAME   = "testvideo.3gp";
    /** delta for duration in case user uses different decoders on different
        hardware that report a duration that's different by a few milliseconds */
    private static final int DURATION_DELTA = 100;
    /** AudioAttributes to be used by this player */
    private static final AudioAttributes AUDIO_ATTR = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();

    private Instrumentation mInstrumentation;
    private Activity mActivity;
    private VideoView mVideoView;
    private String mVideoPath;

    @Rule
    public ActivityTestRule<VideoViewCtsActivity> mActivityRule =
            new ActivityTestRule<>(VideoViewCtsActivity.class);

    @Before
    public void setup() throws Throwable {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
        mVideoView = (VideoView) mActivity.findViewById(R.id.videoview);

        mVideoPath = prepareSampleVideo();
        assertNotNull(mVideoPath);
    }

    private boolean hasCodec() {
        return MediaUtils.hasCodecsForResource(mActivity, R.raw.testvideo);
    }

    private String prepareSampleVideo() throws IOException {
        try (InputStream source = mActivity.getResources().openRawResource(R.raw.testvideo);
             OutputStream target = mActivity.openFileOutput(VIDEO_NAME, Context.MODE_PRIVATE)) {
            final byte[] buffer = new byte[1024];
            for (int len = source.read(buffer); len > 0; len = source.read(buffer)) {
                target.write(buffer, 0, len);
            }
        }

        return mActivity.getFileStreamPath(VIDEO_NAME).getAbsolutePath();
    }

    private void makeVideoView() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            MediaController mediaController = new MediaController(mActivity);
            mVideoView.setMediaController(mediaController);
        });
        mInstrumentation.waitForIdleSync();
    }

    @UiThreadTest
    @Test
    public void testConstructor() {
        new VideoView(mActivity);

        new VideoView(mActivity, null);

        new VideoView(mActivity, null, 0);
    }

    @Test
    public void testPlayVideo() throws Throwable {
        makeVideoView();
        // Don't run the test if the codec isn't supported.
        if (!hasCodec()) {
            Log.i(TAG, "SKIPPING testPlayVideo1(): codec is not supported");
            return;
        }

        final MediaPlayer.OnPreparedListener mockPreparedListener =
                mock(MediaPlayer.OnPreparedListener.class);
        mVideoView.setOnPreparedListener(mockPreparedListener);

        final MediaPlayer.OnCompletionListener mockCompletionListener =
                mock(MediaPlayer.OnCompletionListener.class);
        mVideoView.setOnCompletionListener(mockCompletionListener);

        mActivityRule.runOnUiThread(() -> mVideoView.setVideoPath(mVideoPath));
        verify(mockPreparedListener, within(TIME_OUT)).onPrepared(any(MediaPlayer.class));
        verify(mockPreparedListener, times(1)).onPrepared(any(MediaPlayer.class));
        verifyZeroInteractions(mockCompletionListener);

        mActivityRule.runOnUiThread(mVideoView::start);
        // wait time is longer than duration in case system is sluggish
        verify(mockCompletionListener, within(TIME_OUT)).onCompletion(any(MediaPlayer.class));
        verify(mockCompletionListener, times(1)).onCompletion(any(MediaPlayer.class));
    }

    private static final class MyPlaybackCallback extends AudioManager.AudioPlaybackCallback {
        boolean mMatchingPlayerFound = false;

        @Override
        public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs) {
            for (AudioPlaybackConfiguration apc : configs) {
                if (apc.getPlayerState() == AudioPlaybackConfiguration.PLAYER_STATE_STARTED
                        && apc.getAudioAttributes().getUsage() == AUDIO_ATTR.getUsage()
                        && apc.getAudioAttributes().getContentType()
                                == AUDIO_ATTR.getContentType()) {
                    mMatchingPlayerFound = true;
                    break;
                }
            }
        }
    }

    @Test
    public void testAudioAttributes() throws Throwable {
        makeVideoView();
        // Don't run the test if the codec isn't supported.
        if (!hasCodec()) {
            Log.i(TAG, "SKIPPING testAudioAttributes(): codec is not supported");
            return;
        }

        final MediaPlayer.OnCompletionListener mockCompletionListener =
                mock(MediaPlayer.OnCompletionListener.class);
        mVideoView.setOnCompletionListener(mockCompletionListener);

        mVideoView.setAudioAttributes(AUDIO_ATTR);
        mVideoView.setAudioFocusRequest(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);

        final AudioManager am = new AudioManager(mActivity);
        final MyPlaybackCallback myCb = new MyPlaybackCallback();
        mActivityRule.runOnUiThread(() -> am.registerAudioPlaybackCallback(myCb, null));
        mActivityRule.runOnUiThread(() -> mVideoView.setVideoPath(mVideoPath));
        mActivityRule.runOnUiThread(mVideoView::start);
        // wait time is longer than duration in case system is sluggish
        verify(mockCompletionListener, within(TIME_OUT)).onCompletion(any(MediaPlayer.class));
        verify(mockCompletionListener, times(1)).onCompletion(any(MediaPlayer.class));

        // TODO is there a more compact way to test this with mockito?
        assertTrue("Audio playback configuration not found for VideoView",
                myCb.mMatchingPlayerFound);
    }

    @Test
    public void testSetOnErrorListener() throws Throwable {
        makeVideoView();

        final MediaPlayer.OnErrorListener mockErrorListener =
                mock(MediaPlayer.OnErrorListener.class);
        mVideoView.setOnErrorListener(mockErrorListener);

        mActivityRule.runOnUiThread(() -> {
            String path = "unknown path";
            mVideoView.setVideoPath(path);
            mVideoView.start();
        });
        mInstrumentation.waitForIdleSync();

        verify(mockErrorListener, within(TIME_OUT)).onError(
                any(MediaPlayer.class), anyInt(), anyInt());
        verify(mockErrorListener, times(1)).onError(any(MediaPlayer.class), anyInt(), anyInt());
    }

    @Test
    public void testGetBufferPercentage() throws Throwable {
        makeVideoView();
        // Don't run the test if the codec isn't supported.
        if (!hasCodec()) {
            Log.i(TAG, "SKIPPING testGetBufferPercentage(): codec is not supported");
            return;
        }

        final MediaPlayer.OnPreparedListener mockPreparedListener =
                mock(MediaPlayer.OnPreparedListener.class);
        mVideoView.setOnPreparedListener(mockPreparedListener);

        mActivityRule.runOnUiThread(() -> mVideoView.setVideoPath(mVideoPath));
        mInstrumentation.waitForIdleSync();

        verify(mockPreparedListener, within(TIME_OUT)).onPrepared(any(MediaPlayer.class));
        verify(mockPreparedListener, times(1)).onPrepared(any(MediaPlayer.class));
        int percent = mVideoView.getBufferPercentage();
        assertTrue(percent >= 0 && percent <= 100);
    }

    @UiThreadTest
    @Test
    public void testResolveAdjustedSize() {
        mVideoView = new VideoView(mActivity);

        final int desiredSize = 100;
        int resolvedSize = mVideoView.resolveAdjustedSize(desiredSize, MeasureSpec.UNSPECIFIED);
        assertEquals(desiredSize, resolvedSize);

        final int specSize = MeasureSpec.getSize(MeasureSpec.AT_MOST);
        resolvedSize = mVideoView.resolveAdjustedSize(desiredSize, MeasureSpec.AT_MOST);
        assertEquals(Math.min(desiredSize, specSize), resolvedSize);

        resolvedSize = mVideoView.resolveAdjustedSize(desiredSize, MeasureSpec.EXACTLY);
        assertEquals(specSize, resolvedSize);
    }

    @Test
    public void testGetDuration() throws Throwable {
        // Don't run the test if the codec isn't supported.
        if (!hasCodec()) {
            Log.i(TAG, "SKIPPING testGetDuration(): codec is not supported");
            return;
        }

        mActivityRule.runOnUiThread(() -> mVideoView.setVideoPath(mVideoPath));
        SystemClock.sleep(OPERATION_INTERVAL);
        assertTrue(Math.abs(mVideoView.getDuration() - TEST_VIDEO_DURATION) < DURATION_DELTA);
    }

    @UiThreadTest
    @Test
    public void testSetMediaController() {
        final MediaController ctlr = new MediaController(mActivity);
        mVideoView.setMediaController(ctlr);
    }
}
