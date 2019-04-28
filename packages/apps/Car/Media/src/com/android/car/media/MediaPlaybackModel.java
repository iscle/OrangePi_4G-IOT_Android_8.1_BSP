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
package com.android.car.media;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.media.MediaMetadata;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.android.car.apps.common.util.Assert;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A model for controlling media playback. This model will take care of all Media Manager, Browser,
 * and controller connection and callbacks. On each stage of the connection, error, or disconnect
 * this model will call back to the presenter. All call backs to the presenter will be done on the
 * main thread. Intended to provide a much more usable model interface to UI code.
 */
public class MediaPlaybackModel {
    private static final String TAG = "MediaPlaybackModel";

    private final Context mContext;
    private final Bundle mBrowserExtras;
    private final List<MediaPlaybackModel.Listener> mListeners = new LinkedList<>();

    private Handler mHandler;
    private MediaController mController;
    private MediaBrowser mBrowser;
    private int mPrimaryColor;
    private int mPrimaryColorDark;
    private int mAccentColor;
    private ComponentName mCurrentComponentName;
    private Resources mPackageResources;

    /**
     * This is the interface to listen to {@link MediaPlaybackModel} callbacks. All callbacks are
     * done in the main thread.
     */
    public interface Listener {
        /** Indicates active media app has changed. A new mediaBrowser is now connecting to the new
          * app and mediaController has been released, pending connection to new service.
          */
        void onMediaAppChanged(@Nullable ComponentName currentName,
                               @Nullable ComponentName newName);
        void onMediaAppStatusMessageChanged(@Nullable String message);

        /**
         * Indicates the mediaBrowser is not connected and mediaController is available.
         */
        void onMediaConnected();
        /**
         * Indicates mediaBrowser connection is temporarily suspended.
         * */
        void onMediaConnectionSuspended();
        /**
         * Indicates that the MediaBrowser connected failed. The mediaBrowser and controller have
         * now been released.
         */
        void onMediaConnectionFailed(CharSequence failedMediaClientName);
        void onPlaybackStateChanged(@Nullable PlaybackState state);
        void onMetadataChanged(@Nullable MediaMetadata metadata);
        void onQueueChanged(List<MediaSession.QueueItem> queue);
        /**
         * Indicates that the MediaSession was destroyed. The mediaController has been released.
         */
        void onSessionDestroyed(CharSequence destroyedMediaClientName);
    }

    /** Convenient Listener base class for extension */
    public static abstract class AbstractListener implements Listener {
        @Override
        public void onMediaAppChanged(@Nullable ComponentName currentName,
                @Nullable ComponentName newName) {}
        @Override
        public void onMediaAppStatusMessageChanged(@Nullable String message) {}
        @Override
        public void onMediaConnected() {}
        @Override
        public void onMediaConnectionSuspended() {}
        @Override
        public void onMediaConnectionFailed(CharSequence failedMediaClientName) {}
        @Override
        public void onPlaybackStateChanged(@Nullable PlaybackState state) {}
        @Override
        public void onMetadataChanged(@Nullable MediaMetadata metadata) {}
        @Override
        public void onQueueChanged(List<MediaSession.QueueItem> queue) {}
        @Override
        public void onSessionDestroyed(CharSequence destroyedMediaClientName) {}
    }

    public MediaPlaybackModel(Context context, Bundle browserExtras) {
        mContext = context;
        mBrowserExtras = browserExtras;
        mHandler = new Handler(Looper.getMainLooper());
    }

    @MainThread
    public void start() {
        Assert.isMainThread();
        MediaManager.getInstance(mContext).addListener(mMediaManagerListener);
    }

    @MainThread
    public void stop() {
        Assert.isMainThread();
        MediaManager.getInstance(mContext).removeListener(mMediaManagerListener);
        if (mBrowser != null) {
            mBrowser.disconnect();
            mBrowser = null;
        }
        if (mController != null) {
            mController.unregisterCallback(mMediaControllerCallback);
            mController = null;
        }
        // Calling this with null will clear queue of callbacks and message. This needs to be done
        // here because prior to the above lines to disconnect and unregister the browser and
        // controller a posted runnable to do work maybe have happened and thus we need to clear it
        // out to prevent race conditions.
        mHandler.removeCallbacksAndMessages(null);
    }

    @MainThread
    public void addListener(MediaPlaybackModel.Listener listener) {
        Assert.isMainThread();
        mListeners.add(listener);
    }

    @MainThread
    public void removeListener(MediaPlaybackModel.Listener listener) {
        Assert.isMainThread();
        mListeners.remove(listener);
    }

    @MainThread
    private void notifyListeners(Consumer<Listener> callback) {
        Assert.isMainThread();
        // Clone mListeners in case any of the callbacks made triggers a listener to be added or
        // removed to/from mListeners.
        List<Listener> listenersCopy = new LinkedList<>(mListeners);
        // Invokes callback.accept(listener) for each listener.
        listenersCopy.forEach(callback);
    }

    @MainThread
    public Resources getPackageResources() {
        Assert.isMainThread();
        return mPackageResources;
    }

    @MainThread
    public int getPrimaryColor() {
        Assert.isMainThread();
        return mPrimaryColor;
    }

    @MainThread
    public int getAccentColor() {
        Assert.isMainThread();
        return mAccentColor;
    }

    @MainThread
    public int getPrimaryColorDark() {
        Assert.isMainThread();
        return mPrimaryColorDark;
    }

    @MainThread
    public MediaMetadata getMetadata() {
        Assert.isMainThread();
        if (mController == null) {
            return null;
        }
        return mController.getMetadata();
    }

    @MainThread
    public @NonNull List<MediaSession.QueueItem> getQueue() {
        Assert.isMainThread();
        if (mController == null) {
            return new ArrayList<>();
        }
        List<MediaSession.QueueItem> currentQueue = mController.getQueue();
        if (currentQueue == null) {
            currentQueue = new ArrayList<>();
        }
        return currentQueue;
    }

    @MainThread
    public PlaybackState getPlaybackState() {
        Assert.isMainThread();
        if (mController == null) {
            return null;
        }
        return mController.getPlaybackState();
    }

    /**
     * Return true if the slot of the action should be always reserved for it,
     * even when the corresponding playbackstate action is disabled. This avoids
     * an undesired reflow on the playback drawer when a temporary state
     * disables some action. This information can be set on the MediaSession
     * extras as a boolean for each default action that needs its slot
     * reserved. Currently supported actions are ACTION_SKIP_TO_PREVIOUS,
     * ACTION_SKIP_TO_NEXT and ACTION_SHOW_QUEUE.
     */
    @MainThread
    public boolean isSlotForActionReserved(String actionExtraKey) {
        Assert.isMainThread();
        if (mController != null) {
            Bundle extras = mController.getExtras();
            if (extras != null) {
                return extras.getBoolean(actionExtraKey, false);
            }
        }
        return false;
    }

    @MainThread
    public boolean isConnected() {
        Assert.isMainThread();
        return mController != null;
    }

    @MainThread
    public MediaBrowser getMediaBrowser() {
        Assert.isMainThread();
        return mBrowser;
    }

    @MainThread
    public MediaController.TransportControls getTransportControls() {
        Assert.isMainThread();
        if (mController == null) {
            return null;
        }
        return mController.getTransportControls();
    }

    @MainThread
    public @NonNull CharSequence getQueueTitle() {
        Assert.isMainThread();
        if (mController == null) {
            return "";
        }
        return mController.getQueueTitle();
    }

    private final MediaManager.Listener mMediaManagerListener = new MediaManager.Listener() {
        @Override
        public void onMediaAppChanged(final ComponentName name) {
            mHandler.post(() -> {
                if (mBrowser != null) {
                    mBrowser.disconnect();
                }
                mBrowser = new MediaBrowser(mContext, name, mConnectionCallback, mBrowserExtras);
                try {
                    mPackageResources = mContext.getPackageManager().getResourcesForApplication(
                            name.getPackageName());
                } catch (PackageManager.NameNotFoundException e) {
                    Log.e(TAG, "Unable to get resources for " + name.getPackageName());
                }

                if (mController != null) {
                    mController.unregisterCallback(mMediaControllerCallback);
                    mController = null;
                }
                mBrowser.connect();

                // reset the colors and views if we switch to another app.
                MediaManager manager = MediaManager.getInstance(mContext);
                mPrimaryColor = manager.getMediaClientPrimaryColor();
                mAccentColor = manager.getMediaClientAccentColor();
                mPrimaryColorDark = manager.getMediaClientPrimaryColorDark();

                final ComponentName currentName = mCurrentComponentName;
                notifyListeners((listener) -> listener.onMediaAppChanged(currentName, name));
                mCurrentComponentName = name;
            });
        }

        @Override
        public void onStatusMessageChanged(final String message) {
            mHandler.post(() -> {
                notifyListeners((listener) -> listener.onMediaAppStatusMessageChanged(message));
            });
        }
    };

    private final MediaBrowser.ConnectionCallback mConnectionCallback =
            new MediaBrowser.ConnectionCallback() {
                @Override
                public void onConnected() {
                    mHandler.post(()->{
                        // Existing mController has already been disconnected before we call
                        // MediaBrowser.connect()
                        // getSessionToken returns a non null token
                        MediaSession.Token token = mBrowser.getSessionToken();
                        if (mController != null) {
                            mController.unregisterCallback(mMediaControllerCallback);
                        }
                        mController = new MediaController(mContext, token);
                        mController.registerCallback(mMediaControllerCallback);
                        notifyListeners(Listener::onMediaConnected);
                    });
                }

                @Override
                public void onConnectionSuspended() {
                    mHandler.post(() -> {
                        if (Log.isLoggable(TAG, Log.VERBOSE)) {
                            Log.v(TAG, "Media browser service connection suspended."
                                    + " Waiting to be reconnected....");
                        }
                        notifyListeners(Listener::onMediaConnectionSuspended);
                    });
                }

                @Override
                public void onConnectionFailed() {
                    mHandler.post(() -> {
                        Log.e(TAG, "Media browser service connection FAILED!");
                        // disconnect anyway to make sure we get into a sanity state
                        mBrowser.disconnect();
                        mBrowser = null;
                        mCurrentComponentName = null;

                        CharSequence failedClientName = MediaManager.getInstance(mContext)
                                .getMediaClientName();
                        notifyListeners(
                                (listener) -> listener.onMediaConnectionFailed(failedClientName));
                    });
                }
            };

    private final MediaController.Callback mMediaControllerCallback =
            new MediaController.Callback() {
                @Override
                public void onPlaybackStateChanged(final PlaybackState state) {
                    mHandler.post(() -> {
                        notifyListeners((listener) -> listener.onPlaybackStateChanged(state));
                    });
                }

                @Override
                public void onMetadataChanged(final MediaMetadata metadata) {
                    mHandler.post(() -> {
                        notifyListeners((listener) -> listener.onMetadataChanged(metadata));
                    });
                }

                @Override
                public void onQueueChanged(final List<MediaSession.QueueItem> queue) {
                    mHandler.post(() -> {
                        final List<MediaSession.QueueItem> currentQueue =
                                queue != null ? queue : new ArrayList<>();
                        notifyListeners((listener) -> listener.onQueueChanged(currentQueue));
                    });
                }

                @Override
                public void onSessionDestroyed() {
                    mHandler.post(() -> {
                        if (Log.isLoggable(TAG, Log.VERBOSE)) {
                            Log.v(TAG, "onSessionDestroyed()");
                        }
                        mCurrentComponentName = null;
                        if (mController != null) {
                            mController.unregisterCallback(mMediaControllerCallback);
                            mController = null;
                        }

                        CharSequence destroyedClientName = MediaManager.getInstance(
                                mContext).getMediaClientName();
                        notifyListeners(
                                (listener) -> listener.onSessionDestroyed(destroyedClientName));
                    });
                }
            };
}
