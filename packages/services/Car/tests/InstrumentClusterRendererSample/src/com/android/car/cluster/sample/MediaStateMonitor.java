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
package com.android.car.cluster.sample;

import static com.android.car.cluster.sample.DebugUtil.DEBUG;

import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.MediaSessionManager.OnActiveSessionsChangedListener;
import android.media.session.PlaybackState;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * Reports current media status to instrument cluster renderer through {@link MediaStateListener}
 */
public class MediaStateMonitor {

    private final static String TAG = DebugUtil.getTag(MediaStateMonitor.class);

    private final Context mContext;
    private final MediaListener mMediaListener;
    private MediaController mPrimaryMediaController;
    private OnActiveSessionsChangedListener mActiveSessionsChangedListener;
    private MediaSessionManager mMediaSessionManager;
    private MediaStateListener mListener;

    public MediaStateMonitor(Context context, MediaStateListener listener) {
        mListener = listener;
        mContext = context;
        mMediaListener = new MediaListener(this);
        mActiveSessionsChangedListener = controllers -> onActiveSessionsChanged(controllers);
        mMediaSessionManager = mContext.getSystemService(MediaSessionManager.class);
        mMediaSessionManager.addOnActiveSessionsChangedListener(
                mActiveSessionsChangedListener, null);

        onActiveSessionsChanged(mMediaSessionManager.getActiveSessions(null));
    }

    private void onActiveSessionsChanged(List<MediaController> controllers) {
        if (DEBUG) {
            Log.d(TAG, "onActiveSessionsChanged, controllers found:  " + controllers.size());
        }
        MediaController newPrimaryController = null;
        if (!controllers.isEmpty()) {
            newPrimaryController = controllers.get(0);
            if (mPrimaryMediaController == newPrimaryController) {
                // Primary media controller has not been changed.
                return;
            }
        }

        releasePrimaryMediaController();

        if (newPrimaryController != null) {
            mPrimaryMediaController = newPrimaryController;
            mPrimaryMediaController.registerCallback(mMediaListener);
            mListener.onMediaAppChanged(
                    new MediaAppInfo(mContext, mPrimaryMediaController.getPackageName()));
        }
        updateRendererMediaStatusIfAvailable();
    }

    public void release() {
        releasePrimaryMediaController();
        if (mActiveSessionsChangedListener != null) {
            mMediaSessionManager.removeOnActiveSessionsChangedListener(
                    mActiveSessionsChangedListener);
            mActiveSessionsChangedListener = null;
        }
        mMediaSessionManager = null;
    }

    private void releasePrimaryMediaController() {
        if (mPrimaryMediaController != null) {
            mPrimaryMediaController.unregisterCallback(mMediaListener);
            mPrimaryMediaController = null;
        }
    }

    private void updateRendererMediaStatusIfAvailable() {
        mListener.onMetadataChanged(
                mPrimaryMediaController == null ? null : mPrimaryMediaController.getMetadata());
        mListener.onPlaybackStateChanged(
                mPrimaryMediaController == null
                ? null : mPrimaryMediaController.getPlaybackState());
    }

    private void onPlaybackStateChanged(PlaybackState state) {
        mListener.onPlaybackStateChanged(state);
    }

    private void onMetadataChanged(MediaMetadata metadata) {
        mListener.onMetadataChanged(metadata);
    }

    public interface MediaStateListener {
        void onPlaybackStateChanged(PlaybackState playbackState);
        void onMetadataChanged(MediaMetadata metadata);
        void onMediaAppChanged(MediaAppInfo mediaAppPackage);
    }

    private static class MediaListener extends MediaController.Callback {
        private final WeakReference<MediaStateMonitor> mServiceRef;

        MediaListener(MediaStateMonitor service) {
            mServiceRef = new WeakReference<>(service);
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            MediaStateMonitor service = mServiceRef.get();
            if (service != null) {
                service.onPlaybackStateChanged(state);
            }
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            MediaStateMonitor service = mServiceRef.get();
            if (service != null) {
                service.onMetadataChanged(metadata);
            }
        }
    }
}
