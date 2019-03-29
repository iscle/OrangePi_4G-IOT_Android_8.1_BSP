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
package android.media.cts;

import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Rating;
import android.media.VolumeProvider;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState.CustomAction;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.test.AndroidTestCase;

/**
 * Test {@link android.media.session.MediaController}.
 */
public class MediaControllerTest extends AndroidTestCase {
    // The maximum time to wait for an operation.
    private static final long TIME_OUT_MS = 3000L;
    private static final String SESSION_TAG = "test-session";
    private static final String EXTRAS_KEY = "test-key";
    private static final String EXTRAS_VALUE = "test-val";

    private final Object mWaitLock = new Object();
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private MediaSession mSession;
    private MediaSessionCallback mCallback = new MediaSessionCallback();
    private MediaController mController;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mSession = new MediaSession(getContext(), SESSION_TAG);
        mSession.setCallback(mCallback, mHandler);
        mController = mSession.getController();
    }

    public void testGetPackageName() {
        assertEquals(getContext().getPackageName(), mController.getPackageName());
    }

    public void testGetRatingType() {
        assertEquals("Default rating type of a session must be Rating.RATING_NONE",
                Rating.RATING_NONE, mController.getRatingType());

        mSession.setRatingType(Rating.RATING_5_STARS);
        assertEquals(Rating.RATING_5_STARS, mController.getRatingType());
    }

    public void testGetSessionToken() throws Exception {
        assertEquals(mSession.getSessionToken(), mController.getSessionToken());
    }

    public void testSendCommand() throws Exception {
        synchronized (mWaitLock) {
            mCallback.reset();
            final String command = "test-command";
            final Bundle extras = new Bundle();
            extras.putString(EXTRAS_KEY, EXTRAS_VALUE);
            mController.sendCommand(command, extras, new ResultReceiver(null));
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(mCallback.mOnCommandCalled);
            assertNotNull(mCallback.mCommandCallback);
            assertEquals(command, mCallback.mCommand);
            assertEquals(EXTRAS_VALUE, mCallback.mExtras.getString(EXTRAS_KEY));
        }
    }

    public void testAdjustVolumeWithIllegalDirection() {
        // Call the method with illegal direction. System should not reboot.
        mController.adjustVolume(37, 0);
    }

    public void testVolumeControl() throws Exception {
        VolumeProvider vp = new VolumeProvider(VolumeProvider.VOLUME_CONTROL_ABSOLUTE, 11, 5) {
            @Override
            public void onSetVolumeTo(int volume) {
                synchronized (mWaitLock) {
                    setCurrentVolume(volume);
                    mWaitLock.notify();
                }
            }

            @Override
            public void onAdjustVolume(int direction) {
                synchronized (mWaitLock) {
                    switch (direction) {
                        case AudioManager.ADJUST_LOWER:
                            setCurrentVolume(getCurrentVolume() - 1);
                            break;
                        case AudioManager.ADJUST_RAISE:
                            setCurrentVolume(getCurrentVolume() + 1);
                            break;
                    }
                    mWaitLock.notify();
                }
            }
        };
        mSession.setPlaybackToRemote(vp);

        synchronized (mWaitLock) {
            // test setVolumeTo
            mController.setVolumeTo(7, 0);
            mWaitLock.wait(TIME_OUT_MS);
            assertEquals(7, vp.getCurrentVolume());

            // test adjustVolume
            mController.adjustVolume(AudioManager.ADJUST_LOWER, 0);
            mWaitLock.wait(TIME_OUT_MS);
            assertEquals(6, vp.getCurrentVolume());

            mController.adjustVolume(AudioManager.ADJUST_RAISE, 0);
            mWaitLock.wait(TIME_OUT_MS);
            assertEquals(7, vp.getCurrentVolume());
        }
    }

    public void testTransportControlsAndMediaSessionCallback() throws Exception {
        MediaController.TransportControls controls = mController.getTransportControls();
        final MediaSession.Callback callback = (MediaSession.Callback) mCallback;

        synchronized (mWaitLock) {
            mCallback.reset();
            controls.play();
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(mCallback.mOnPlayCalled);
            // just call the callback once directly so it's marked as tested
            callback.onPlay();

            mCallback.reset();
            controls.pause();
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(mCallback.mOnPauseCalled);
            // just call the callback once directly so it's marked as tested
            callback.onPause();

            mCallback.reset();
            controls.stop();
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(mCallback.mOnStopCalled);
            // just call the callback once directly so it's marked as tested
            callback.onStop();

            mCallback.reset();
            controls.fastForward();
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(mCallback.mOnFastForwardCalled);
            // just call the callback once directly so it's marked as tested
            callback.onFastForward();

            mCallback.reset();
            controls.rewind();
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(mCallback.mOnRewindCalled);
            // just call the callback once directly so it's marked as tested
            callback.onRewind();

            mCallback.reset();
            controls.skipToPrevious();
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(mCallback.mOnSkipToPreviousCalled);
            // just call the callback once directly so it's marked as tested
            callback.onSkipToPrevious();

            mCallback.reset();
            controls.skipToNext();
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(mCallback.mOnSkipToNextCalled);
            // just call the callback once directly so it's marked as tested
            callback.onSkipToNext();

            mCallback.reset();
            final long seekPosition = 1000;
            controls.seekTo(seekPosition);
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(mCallback.mOnSeekToCalled);
            assertEquals(seekPosition, mCallback.mSeekPosition);
            // just call the callback once directly so it's marked as tested
            callback.onSeekTo(mCallback.mSeekPosition);

            mCallback.reset();
            final Rating rating = Rating.newStarRating(Rating.RATING_5_STARS, 3f);
            controls.setRating(rating);
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(mCallback.mOnSetRatingCalled);
            assertEquals(rating.getRatingStyle(), mCallback.mRating.getRatingStyle());
            assertEquals(rating.getStarRating(), mCallback.mRating.getStarRating());
            // just call the callback once directly so it's marked as tested
            callback.onSetRating(mCallback.mRating);

            mCallback.reset();
            final String mediaId = "test-media-id";
            final Bundle extras = new Bundle();
            extras.putString(EXTRAS_KEY, EXTRAS_VALUE);
            controls.playFromMediaId(mediaId, extras);
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(mCallback.mOnPlayFromMediaIdCalled);
            assertEquals(mediaId, mCallback.mMediaId);
            assertEquals(EXTRAS_VALUE, mCallback.mExtras.getString(EXTRAS_KEY));
            // just call the callback once directly so it's marked as tested
            callback.onPlayFromMediaId(mCallback.mMediaId, mCallback.mExtras);

            mCallback.reset();
            final String query = "test-query";
            controls.playFromSearch(query, extras);
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(mCallback.mOnPlayFromSearchCalled);
            assertEquals(query, mCallback.mQuery);
            assertEquals(EXTRAS_VALUE, mCallback.mExtras.getString(EXTRAS_KEY));
            // just call the callback once directly so it's marked as tested
            callback.onPlayFromSearch(mCallback.mQuery, mCallback.mExtras);

            mCallback.reset();
            final Uri uri = Uri.parse("content://test/popcorn.mod");
            controls.playFromUri(uri, extras);
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(mCallback.mOnPlayFromUriCalled);
            assertEquals(uri, mCallback.mUri);
            assertEquals(EXTRAS_VALUE, mCallback.mExtras.getString(EXTRAS_KEY));
            // just call the callback once directly so it's marked as tested
            callback.onPlayFromUri(mCallback.mUri, mCallback.mExtras);

            mCallback.reset();
            final String action = "test-action";
            controls.sendCustomAction(action, extras);
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(mCallback.mOnCustomActionCalled);
            assertEquals(action, mCallback.mAction);
            assertEquals(EXTRAS_VALUE, mCallback.mExtras.getString(EXTRAS_KEY));
            // just call the callback once directly so it's marked as tested
            callback.onCustomAction(mCallback.mAction, mCallback.mExtras);

            mCallback.reset();
            mCallback.mOnCustomActionCalled = false;
            final CustomAction customAction =
                    new CustomAction.Builder(action, action, -1).setExtras(extras).build();
            controls.sendCustomAction(customAction, extras);
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(mCallback.mOnCustomActionCalled);
            assertEquals(action, mCallback.mAction);
            assertEquals(EXTRAS_VALUE, mCallback.mExtras.getString(EXTRAS_KEY));
            // just call the callback once directly so it's marked as tested
            callback.onCustomAction(mCallback.mAction, mCallback.mExtras);

            mCallback.reset();
            final long queueItemId = 1000;
            controls.skipToQueueItem(queueItemId);
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(mCallback.mOnSkipToQueueItemCalled);
            assertEquals(queueItemId, mCallback.mQueueItemId);
            // just call the callback once directly so it's marked as tested
            callback.onSkipToQueueItem(mCallback.mQueueItemId);

            mCallback.reset();
            controls.prepare();
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(mCallback.mOnPrepareCalled);
            // just call the callback once directly so it's marked as tested
            callback.onPrepare();

            mCallback.reset();
            controls.prepareFromMediaId(mediaId, extras);
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(mCallback.mOnPrepareFromMediaIdCalled);
            assertEquals(mediaId, mCallback.mMediaId);
            assertEquals(EXTRAS_VALUE, mCallback.mExtras.getString(EXTRAS_KEY));
            // just call the callback once directly so it's marked as tested
            callback.onPrepareFromMediaId(mCallback.mMediaId, mCallback.mExtras);

            mCallback.reset();
            controls.prepareFromSearch(query, extras);
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(mCallback.mOnPrepareFromSearchCalled);
            assertEquals(query, mCallback.mQuery);
            assertEquals(EXTRAS_VALUE, mCallback.mExtras.getString(EXTRAS_KEY));
            // just call the callback once directly so it's marked as tested
            callback.onPrepareFromSearch(mCallback.mQuery, mCallback.mExtras);

            mCallback.reset();
            controls.prepareFromUri(uri, extras);
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(mCallback.mOnPrepareFromUriCalled);
            assertEquals(uri, mCallback.mUri);
            assertEquals(EXTRAS_VALUE, mCallback.mExtras.getString(EXTRAS_KEY));
        }
    }

    public void testPlaybackInfo() {
        final int playbackType = MediaController.PlaybackInfo.PLAYBACK_TYPE_LOCAL;
        final int volumeControl = VolumeProvider.VOLUME_CONTROL_ABSOLUTE;
        final int maxVolume = 10;
        final int currentVolume = 3;

        AudioAttributes audioAttributes = new AudioAttributes.Builder().build();
        MediaController.PlaybackInfo info = new MediaController.PlaybackInfo(
                playbackType, audioAttributes, volumeControl, maxVolume, currentVolume);

        assertEquals(playbackType, info.getPlaybackType());
        assertEquals(audioAttributes, info.getAudioAttributes());
        assertEquals(volumeControl, info.getVolumeControl());
        assertEquals(maxVolume, info.getMaxVolume());
        assertEquals(currentVolume, info.getCurrentVolume());
    }

    private class MediaSessionCallback extends MediaSession.Callback {
        private long mSeekPosition;
        private long mQueueItemId;
        private Rating mRating;
        private String mMediaId;
        private String mQuery;
        private Uri mUri;
        private String mAction;
        private String mCommand;
        private Bundle mExtras;
        private ResultReceiver mCommandCallback;

        private boolean mOnPlayCalled;
        private boolean mOnPauseCalled;
        private boolean mOnStopCalled;
        private boolean mOnFastForwardCalled;
        private boolean mOnRewindCalled;
        private boolean mOnSkipToPreviousCalled;
        private boolean mOnSkipToNextCalled;
        private boolean mOnSeekToCalled;
        private boolean mOnSkipToQueueItemCalled;
        private boolean mOnSetRatingCalled;
        private boolean mOnPlayFromMediaIdCalled;
        private boolean mOnPlayFromSearchCalled;
        private boolean mOnPlayFromUriCalled;
        private boolean mOnCustomActionCalled;
        private boolean mOnCommandCalled;
        private boolean mOnPrepareCalled;
        private boolean mOnPrepareFromMediaIdCalled;
        private boolean mOnPrepareFromSearchCalled;
        private boolean mOnPrepareFromUriCalled;

        public void reset() {
            mSeekPosition = -1;
            mQueueItemId = -1;
            mRating = null;
            mMediaId = null;
            mQuery = null;
            mUri = null;
            mAction = null;
            mExtras = null;
            mCommand = null;
            mCommandCallback = null;

            mOnPlayCalled = false;
            mOnPauseCalled = false;
            mOnStopCalled = false;
            mOnFastForwardCalled = false;
            mOnRewindCalled = false;
            mOnSkipToPreviousCalled = false;
            mOnSkipToNextCalled = false;
            mOnSkipToQueueItemCalled = false;
            mOnSeekToCalled = false;
            mOnSetRatingCalled = false;
            mOnPlayFromMediaIdCalled = false;
            mOnPlayFromSearchCalled = false;
            mOnPlayFromUriCalled = false;
            mOnCustomActionCalled = false;
            mOnCommandCalled = false;
            mOnPrepareCalled = false;
            mOnPrepareFromMediaIdCalled = false;
            mOnPrepareFromSearchCalled = false;
            mOnPrepareFromUriCalled = false;
        }

        @Override
        public void onPlay() {
            synchronized (mWaitLock) {
                mOnPlayCalled = true;
                mWaitLock.notify();
            }
        }

        @Override
        public void onPause() {
            synchronized (mWaitLock) {
                mOnPauseCalled = true;
                mWaitLock.notify();
            }
        }

        @Override
        public void onStop() {
            synchronized (mWaitLock) {
                mOnStopCalled = true;
                mWaitLock.notify();
            }
        }

        @Override
        public void onFastForward() {
            synchronized (mWaitLock) {
                mOnFastForwardCalled = true;
                mWaitLock.notify();
            }
        }

        @Override
        public void onRewind() {
            synchronized (mWaitLock) {
                mOnRewindCalled = true;
                mWaitLock.notify();
            }
        }

        @Override
        public void onSkipToPrevious() {
            synchronized (mWaitLock) {
                mOnSkipToPreviousCalled = true;
                mWaitLock.notify();
            }
        }

        @Override
        public void onSkipToNext() {
            synchronized (mWaitLock) {
                mOnSkipToNextCalled = true;
                mWaitLock.notify();
            }
        }

        @Override
        public void onSeekTo(long pos) {
            synchronized (mWaitLock) {
                mOnSeekToCalled = true;
                mSeekPosition = pos;
                mWaitLock.notify();
            }
        }

        @Override
        public void onSetRating(Rating rating) {
            synchronized (mWaitLock) {
                mOnSetRatingCalled = true;
                mRating = rating;
                mWaitLock.notify();
            }
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            synchronized (mWaitLock) {
                mOnPlayFromMediaIdCalled = true;
                mMediaId = mediaId;
                mExtras = extras;
                mWaitLock.notify();
            }
        }

        @Override
        public void onPlayFromSearch(String query, Bundle extras) {
            synchronized (mWaitLock) {
                mOnPlayFromSearchCalled = true;
                mQuery = query;
                mExtras = extras;
                mWaitLock.notify();
            }
        }

        @Override
        public void onPlayFromUri(Uri uri, Bundle extras) {
            synchronized (mWaitLock) {
                mOnPlayFromUriCalled = true;
                mUri = uri;
                mExtras = extras;
                mWaitLock.notify();
            }
        }

        @Override
        public void onCustomAction(String action, Bundle extras) {
            synchronized (mWaitLock) {
                mOnCustomActionCalled = true;
                mAction = action;
                mExtras = extras;
                mWaitLock.notify();
            }
        }

        @Override
        public void onSkipToQueueItem(long id) {
            synchronized (mWaitLock) {
                mOnSkipToQueueItemCalled = true;
                mQueueItemId = id;
                mWaitLock.notify();
            }
        }

        @Override
        public void onCommand(String command, Bundle extras, ResultReceiver cb) {
            synchronized (mWaitLock) {
                mOnCommandCalled = true;
                mCommand = command;
                mExtras = extras;
                mCommandCallback = cb;
                mWaitLock.notify();
            }
        }

        @Override
        public void onPrepare() {
            synchronized (mWaitLock) {
                mOnPrepareCalled = true;
                mWaitLock.notify();
            }
        }

        @Override
        public void onPrepareFromMediaId(String mediaId, Bundle extras) {
            synchronized (mWaitLock) {
                mOnPrepareFromMediaIdCalled = true;
                mMediaId = mediaId;
                mExtras = extras;
                mWaitLock.notify();
            }
        }

        @Override
        public void onPrepareFromSearch(String query, Bundle extras) {
            synchronized (mWaitLock) {
                mOnPrepareFromSearchCalled = true;
                mQuery = query;
                mExtras = extras;
                mWaitLock.notify();
            }
        }

        @Override
        public void onPrepareFromUri(Uri uri, Bundle extras) {
            synchronized (mWaitLock) {
                mOnPrepareFromUriCalled = true;
                mUri = uri;
                mExtras = extras;
                mWaitLock.notify();
            }
        }
    }
}
