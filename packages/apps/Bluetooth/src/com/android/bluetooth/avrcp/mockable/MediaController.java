package com.android.bluetooth.avrcp;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.ParceledListSlice;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaDescription;
import android.media.Rating;
import android.media.VolumeProvider;
import android.media.session.PlaybackState;
import android.media.session.MediaSession;
import android.media.session.MediaSession.QueueItem;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class MediaController {
    public @NonNull android.media.session.MediaController mDelegate;
    public android.media.session.MediaController.TransportControls mTransportDelegate;
    public TransportControls mTransportControls;

    @Nullable
    public static MediaController wrap(@Nullable android.media.session.MediaController delegate) {
      return (delegate != null) ? new MediaController(delegate) : null;
    }

    public MediaController(@NonNull android.media.session.MediaController delegate) {
        mDelegate = delegate;
        mTransportDelegate = delegate.getTransportControls();
        mTransportControls = new TransportControls();
    }

    public android.media.session.MediaController getWrappedInstance() {
        return mDelegate;
    }

    public @NonNull TransportControls getTransportControls() {
        return mTransportControls;
    }

    public boolean dispatchMediaButtonEvent(@NonNull KeyEvent keyEvent) {
        return mDelegate.dispatchMediaButtonEvent(keyEvent);
    }

    public @Nullable PlaybackState getPlaybackState() {
        return mDelegate.getPlaybackState();
    }

    public @Nullable MediaMetadata getMetadata() {
        return mDelegate.getMetadata();
    }

    public @Nullable List<MediaSession.QueueItem> getQueue() {
        return mDelegate.getQueue();
    }

    public @Nullable CharSequence getQueueTitle() {
        return mDelegate.getQueueTitle();
    }

    public @Nullable Bundle getExtras() {
        return mDelegate.getExtras();
    }

    public int getRatingType() {
        return mDelegate.getRatingType();
    }

    public long getFlags() {
        return mDelegate.getFlags();
    }

    public @Nullable android.media.session.MediaController.PlaybackInfo getPlaybackInfo() {
        return mDelegate.getPlaybackInfo();
    }

    public @Nullable PendingIntent getSessionActivity() {
        return mDelegate.getSessionActivity();
    }

    public @NonNull MediaSession.Token getSessionToken() {
        return mDelegate.getSessionToken();
    }

    public void setVolumeTo(int value, int flags) {
        mDelegate.setVolumeTo(value, flags);
    }

    public void adjustVolume(int direction, int flags) {
        mDelegate.adjustVolume(direction, flags);
    }

    public void registerCallback(@NonNull Callback callback) {
        //TODO(apanicke): Add custom callback struct to be able to analyze and
        // delegate callbacks
        mDelegate.registerCallback(callback);
    }

    public void registerCallback(@NonNull Callback callback, @Nullable Handler handler) {
        mDelegate.registerCallback(callback, handler);
    }

    public void unregisterCallback(@NonNull Callback callback) {
        mDelegate.unregisterCallback(callback);
    }

    public void sendCommand(@NonNull String command, @Nullable Bundle args,
            @Nullable ResultReceiver cb) {
        mDelegate.sendCommand(command, args, cb);
    }

    public String getPackageName() {
        return mDelegate.getPackageName();
    }

    public String getTag() {
        return mDelegate.getTag();
    }

    public boolean controlsSameSession(MediaController other) {
        return mDelegate.controlsSameSession(other.getWrappedInstance());
    }

    public boolean controlsSameSession(android.media.session.MediaController other) {
        return mDelegate.controlsSameSession(other);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof android.media.session.MediaController) {
            return mDelegate.equals(o);
        } else if (o instanceof MediaController) {
            MediaController other = (MediaController) o;
            return mDelegate.equals(other.mDelegate);
        }
        return false;
    }

    @Override
    public String toString() {
        MediaMetadata data = getMetadata();
        MediaDescription desc = (data == null) ? null : data.getDescription();
        return "MediaController (" + getPackageName() + "@"
                + Integer.toHexString(mDelegate.hashCode()) + ") " + desc;
    }

    public static abstract class Callback extends android.media.session.MediaController.Callback { }

    public class TransportControls {

        public void prepare() {
            mTransportDelegate.prepare();
        }

        public void prepareFromMediaId(String mediaId, Bundle extras) {
            mTransportDelegate.prepareFromMediaId(mediaId, extras);
        }

        public void prepareFromSearch(String query, Bundle extras) {
            mTransportDelegate.prepareFromSearch(query, extras);
        }

        public void prepareFromUri(Uri uri, Bundle extras) {
            mTransportDelegate.prepareFromUri(uri, extras);
        }

        public void play() {
            mTransportDelegate.play();
        }

        public void playFromMediaId(String mediaId, Bundle extras) {
            mTransportDelegate.playFromMediaId(mediaId, extras);
        }

        public void playFromSearch(String query, Bundle extras) {
            mTransportDelegate.playFromSearch(query, extras);
        }

        public void playFromUri(Uri uri, Bundle extras) {
            mTransportDelegate.playFromUri(uri, extras);
        }

        public void skipToQueueItem(long id) {
            mTransportDelegate.skipToQueueItem(id);
        }

        public void pause() {
            mTransportDelegate.pause();
        }

        public void stop() {
            mTransportDelegate.stop();
        }

        public void seekTo(long pos) {
            mTransportDelegate.seekTo(pos);
        }

        public void fastForward() {
            mTransportDelegate.fastForward();
        }

        public void skipToNext() {
            mTransportDelegate.skipToNext();
        }

        public void rewind() {
            mTransportDelegate.rewind();
        }

        public void skipToPrevious() {
            mTransportDelegate.skipToPrevious();
        }

        public void setRating(Rating rating) {
            mTransportDelegate.setRating(rating);
        }

        public void sendCustomAction(@NonNull PlaybackState.CustomAction customAction,
                @Nullable Bundle args) {
            mTransportDelegate.sendCustomAction(customAction, args);
        }

        public void sendCustomAction(@NonNull String action, @Nullable Bundle args) {
            mTransportDelegate.sendCustomAction(action, args);
        }
    }
}

