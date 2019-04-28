/*
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.car.stream.media;

import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.KeyEvent;
import com.android.car.apps.common.util.Assert;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A class to listen for changes in sessions from {@link MediaSessionManager}. It also notifies
 * listeners of changes in the playback state or metadata.
 */
public class MediaStateManager {
    private static final String TAG = "MediaStateManager";
    private static final String TELECOM_PACKAGE = "com.android.server.telecom";

    private final Context mContext;

    private MediaAppInfo mConnectedAppInfo;
    private MediaController mController;
    private Handler mHandler;
    private final Set<Listener> mListeners;

    public interface Listener {
        void onMediaSessionConnected(PlaybackState playbackState, MediaMetadata metaData,
                MediaAppInfo appInfo);

        void onPlaybackStateChanged(@Nullable PlaybackState state);

        void onMetadataChanged(@Nullable MediaMetadata metadata);

        void onSessionDestroyed();
    }

    public MediaStateManager(@NonNull Context context) {
        mContext = context;
        mHandler = new Handler(Looper.getMainLooper());
        mListeners = new LinkedHashSet<>();
    }

    public void start() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Starting MediaStateManager");
        }
        MediaSessionManager sessionManager
                = (MediaSessionManager) mContext.getSystemService(Context.MEDIA_SESSION_SERVICE);

        try {
            sessionManager.addOnActiveSessionsChangedListener(mSessionChangedListener, null);

            List<MediaController> controllers = sessionManager.getActiveSessions(null);
            updateMediaController(controllers);
        } catch (SecurityException e) {
            // User hasn't granted the permission so we should just go away silently.
        }
    }

    @MainThread
    public void destroy() {
        Assert.isMainThread();
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "destroy()");
        }
        stop();
        mListeners.clear();
        mHandler = null;
    }

    @MainThread
    public void stop() {
        Assert.isMainThread();
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "stop()");
        }

        if (mController != null) {
            mController.unregisterCallback(mMediaControllerCallback);
            mController = null;
        }
        // Calling this with null will clear queue of callbacks and message. This needs to be done
        // here because prior to the above lines to disconnect and unregister the
        // controller a posted runnable to do work maybe have happened and thus we need to clear it
        // out to prevent race conditions.
        mHandler.removeCallbacksAndMessages(null);
    }

    public void dispatchMediaButton(KeyEvent keyEvent) {
        if (mController != null) {
            MediaController.TransportControls transportControls
                    = mController.getTransportControls();
            int eventId = keyEvent.getKeyCode();

            switch (eventId) {
                case KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD:
                    transportControls.skipToPrevious();
                    break;
                case KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD:
                    transportControls.skipToNext();
                    break;
                case KeyEvent.KEYCODE_MEDIA_PLAY:
                    transportControls.play();
                    break;
                case KeyEvent.KEYCODE_MEDIA_PAUSE:
                    transportControls.pause();
                    break;
                case KeyEvent.KEYCODE_MEDIA_STOP:
                    transportControls.stop();
                    break;
                default:
                    mController.dispatchMediaButtonEvent(keyEvent);
            }
        }
    }

    public void addListener(@NonNull Listener listener) {
        mListeners.add(listener);
    }

    public void removeListener(@NonNull Listener listener) {
        mListeners.remove(listener);
    }

    private void updateMediaController(List<MediaController> controllers) {
        if (controllers.size() > 0) {
            // If the telecom package is trying to onStart a media session, ignore it
            // so that the existing media item continues to appear in the stream.
            if (TELECOM_PACKAGE.equals(controllers.get(0).getPackageName())) {
                return;
            }

            if (mController != null) {
                mController.unregisterCallback(mMediaControllerCallback);
            }
            // Currently the first controller is the active one playing music.
            // If this is no longer the case, consider checking notification listener
            // for a MediaStyle notification to get currently playing media app.
            mController = controllers.get(0);
            mController.registerCallback(mMediaControllerCallback);

            mConnectedAppInfo = new MediaAppInfo(mContext, mController.getPackageName());

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "updating media controller");
            }

            for (Listener listener : mListeners) {
                listener.onMediaSessionConnected(mController.getPlaybackState(),
                        mController.getMetadata(), mConnectedAppInfo);
            }
        } else {
            Log.w(TAG, "Updating controllers with an empty list!");
        }
    }

    public static boolean isMainThread() {
        return Looper.myLooper() == Looper.getMainLooper();
    }

    private final MediaSessionManager.OnActiveSessionsChangedListener
            mSessionChangedListener = new MediaSessionManager.OnActiveSessionsChangedListener() {
        @Override
        public void onActiveSessionsChanged(List<MediaController> controllers) {
            updateMediaController(controllers);
        }
    };

    private final MediaController.Callback mMediaControllerCallback =
            new MediaController.Callback() {
                @Override
                public void onPlaybackStateChanged(@NonNull final PlaybackState state) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (Log.isLoggable(TAG, Log.DEBUG)) {
                                Log.d(TAG, "onPlaybackStateChanged(" + state + ")");
                            }
                            for (Listener listener : mListeners) {
                                listener.onPlaybackStateChanged(state);
                            }
                        }
                    });
                }

                @Override
                public void onMetadataChanged(@Nullable final MediaMetadata metadata) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (Log.isLoggable(TAG, Log.DEBUG)) {
                                Log.d(TAG, "onMetadataChanged(" + metadata + ")");
                            }
                            for (Listener listener : mListeners) {
                                listener.onMetadataChanged(metadata);
                            }
                        }
                    });
                }

                @Override
                public void onSessionDestroyed() {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (Log.isLoggable(TAG, Log.DEBUG)) {
                                Log.d(TAG, "onSessionDestroyed()");
                            }

                            mConnectedAppInfo = null;
                            if (mController != null) {
                                mController.unregisterCallback(mMediaControllerCallback);
                                mController = null;
                            }

                            for (Listener listener : mListeners) {
                                listener.onSessionDestroyed();
                            }
                        }
                    });
                }
            };
}
