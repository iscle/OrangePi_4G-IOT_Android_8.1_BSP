/*
 * Copyright (C) 2008 The Android Open Source Project
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.AttributeSet;
import android.util.Xml;
import android.view.MotionEvent;
import android.view.View;
import android.widget.MediaController;
import android.widget.VideoView;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Test {@link MediaController}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class MediaControllerTest {
    private Instrumentation mInstrumentation;
    private Activity mActivity;
    private MediaController mMediaController;

    @Rule
    public ActivityTestRule<MediaControllerCtsActivity> mActivityRule =
            new ActivityTestRule<>(MediaControllerCtsActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
    }

    @UiThreadTest
    @Test
    public void testConstructor() {
        new MediaController(mActivity, null);

        new MediaController(mActivity, true);

        new MediaController(mActivity);

        final XmlPullParser parser =
                mActivity.getResources().getXml(R.layout.mediacontroller_layout);
        final AttributeSet attrs = Xml.asAttributeSet(parser);
        new MediaController(mActivity, attrs);
    }

    /**
     * scenario description:
     * 1. Show the MediaController.
     *
     */
    @UiThreadTest
    @Test
    public void testMediaController() {
        mMediaController = new MediaController(mActivity);
        final MockMediaPlayerControl mediaPlayerControl = new MockMediaPlayerControl();
        mMediaController.setMediaPlayer(mediaPlayerControl);

        assertFalse(mMediaController.isShowing());
        mMediaController.show();
        // setAnchorView() must be called before show(),
        // otherwise MediaController never show.
        assertFalse(mMediaController.isShowing());

        View videoview = mActivity.findViewById(R.id.mediacontroller_videoview);
        mMediaController.setAnchorView(videoview);

        mMediaController.show();
        assertTrue(mMediaController.isShowing());

        // ideally test would trigger pause/play/ff/rew here and test response, but no way
        // to trigger those actions from MediaController

        mMediaController = new MediaController(mActivity, false);
        mMediaController.setMediaPlayer(mediaPlayerControl);
        videoview = mActivity.findViewById(R.id.mediacontroller_videoview);
        mMediaController.setAnchorView(videoview);

        mMediaController.show();
        assertTrue(mMediaController.isShowing());
    }

    @Test
    public void testShow() throws Throwable {
        mActivityRule.runOnUiThread(
                () -> mMediaController = new MediaController(mActivity, true));
        mInstrumentation.waitForIdleSync();
        assertFalse(mMediaController.isShowing());

        final MockMediaPlayerControl mediaPlayerControl = new MockMediaPlayerControl();
        mMediaController.setMediaPlayer(mediaPlayerControl);

        final VideoView videoView =
                (VideoView) mActivity.findViewById(R.id.mediacontroller_videoview);
        mMediaController.setAnchorView(videoView);

        mActivityRule.runOnUiThread(mMediaController::show);
        mInstrumentation.waitForIdleSync();
        assertTrue(mMediaController.isShowing());

        mActivityRule.runOnUiThread(mMediaController::hide);
        mInstrumentation.waitForIdleSync();
        assertFalse(mMediaController.isShowing());

        final int timeout = 2000;
        mActivityRule.runOnUiThread(() -> mMediaController.show(timeout));

        mInstrumentation.waitForIdleSync();
        assertTrue(mMediaController.isShowing());

        // isShowing() should return false, but MediaController still shows, this may be a bug.
        PollingCheck.waitFor(500, mMediaController::isShowing);
    }

    private String prepareSampleVideo() {
        final String VIDEO_NAME   = "testvideo.3gp";

        try (InputStream source = mActivity.getResources().openRawResource(R.raw.testvideo);
             OutputStream target = mActivity.openFileOutput(VIDEO_NAME, Context.MODE_PRIVATE)) {

            final byte[] buffer = new byte[1024];
            for (int len = source.read(buffer); len > 0; len = source.read(buffer)) {
                target.write(buffer, 0, len);
            }
        } catch (final IOException e) {
            fail(e.getMessage());
        }

        return mActivity.getFileStreamPath(VIDEO_NAME).getAbsolutePath();
    }

    @Test
    public void testOnTrackballEvent() throws Throwable {
        mActivityRule.runOnUiThread(() -> mMediaController = new MediaController(mActivity));
        mInstrumentation.waitForIdleSync();
        final MockMediaPlayerControl mediaPlayerControl = new MockMediaPlayerControl();
        mMediaController.setMediaPlayer(mediaPlayerControl);

        final VideoView videoView =
                (VideoView) mActivity.findViewById(R.id.mediacontroller_videoview);
        videoView.setMediaController(mMediaController);
        mActivityRule.runOnUiThread(() -> {
            videoView.setVideoPath(prepareSampleVideo());
            videoView.requestFocus();
        });
        mInstrumentation.waitForIdleSync();

        final long curTime = System.currentTimeMillis();
        // get the center of the VideoView.
        final int[] xy = new int[2];
        videoView.getLocationOnScreen(xy);

        final int viewWidth = videoView.getWidth();
        final int viewHeight = videoView.getHeight();

        final float x = xy[0] + viewWidth / 2.0f;
        final float y = xy[1] + viewHeight / 2.0f;
        final MotionEvent event = MotionEvent.obtain(curTime, 100,
                MotionEvent.ACTION_DOWN, x, y, 0);
        mInstrumentation.sendTrackballEventSync(event);
        mInstrumentation.waitForIdleSync();
    }

    @UiThreadTest
    @Test
    public void testSetEnabled() {
        final View videoView = mActivity.findViewById(R.id.mediacontroller_videoview);
        final MockMediaPlayerControl mediaPlayerControl = new MockMediaPlayerControl();

        mMediaController = new MediaController(mActivity);
        mMediaController.setAnchorView(videoView);
        mMediaController.setMediaPlayer(mediaPlayerControl);

        final View.OnClickListener mockNextClickListener = mock(View.OnClickListener.class);
        final View.OnClickListener mockPrevClickListener = mock(View.OnClickListener.class);
        mMediaController.setPrevNextListeners(mockNextClickListener, mockPrevClickListener);

        mMediaController.show();

        mMediaController.setEnabled(true);
        assertTrue(mMediaController.isEnabled());

        mMediaController.setEnabled(false);
        assertFalse(mMediaController.isEnabled());
    }

    @UiThreadTest
    @Test
    public void testSetPrevNextListeners() {
        final View videoView = mActivity.findViewById(R.id.mediacontroller_videoview);
        final MockMediaPlayerControl mediaPlayerControl = new MockMediaPlayerControl();

        mMediaController = new MediaController(mActivity);
        mMediaController.setAnchorView(videoView);
        mMediaController.setMediaPlayer(mediaPlayerControl);

        final View.OnClickListener mockNextClickListener = mock(View.OnClickListener.class);
        final View.OnClickListener mockPrevClickListener = mock(View.OnClickListener.class);
        mMediaController.setPrevNextListeners(mockNextClickListener, mockPrevClickListener);
    }

    private static class MockMediaPlayerControl implements MediaController.MediaPlayerControl {
        private boolean mIsPlaying = false;
        private int mPosition = 0;

        public void start() {
            mIsPlaying = true;
        }

        public void pause() {
            mIsPlaying = false;
        }

        public int getDuration() {
            return 0;
        }

        public int getCurrentPosition() {
            return mPosition;
        }

        public void seekTo(int pos) {
            mPosition = pos;
        }

        public boolean isPlaying() {
            return mIsPlaying;
        }

        public int getBufferPercentage() {
            return 0;
        }

        public boolean canPause() {
            return true;
        }

        public boolean canSeekBackward() {
            return true;
        }

        public boolean canSeekForward() {
            return true;
        }

        @Override
        public int getAudioSessionId() {
            return 0;
        }
    }
}
