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
package com.android.car.media.localmediaplayer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.session.MediaSession;
import android.media.session.MediaSession.QueueItem;
import android.media.session.PlaybackState;
import android.media.session.PlaybackState.CustomAction;
import android.os.Bundle;
import android.util.Log;

import com.android.car.media.localmediaplayer.nano.Proto.Playlist;
import com.android.car.media.localmediaplayer.nano.Proto.Song;

// Proto should be available in AOSP.
import com.google.protobuf.nano.MessageNano;
import com.google.protobuf.nano.InvalidProtocolBufferNanoException;

import java.io.IOException;
import java.io.File;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/**
 * TODO: Consider doing all content provider accesses and player operations asynchronously.
 */
public class Player extends MediaSession.Callback {
    private static final String TAG = "LMPlayer";
    private static final String SHARED_PREFS_NAME = "com.android.car.media.localmediaplayer.prefs";
    private static final String CURRENT_PLAYLIST_KEY = "__CURRENT_PLAYLIST_KEY__";
    private static final int NOTIFICATION_ID = 42;
    private static final int REQUEST_CODE = 94043;

    private static final float PLAYBACK_SPEED = 1.0f;
    private static final float PLAYBACK_SPEED_STOPPED = 1.0f;
    private static final long PLAYBACK_POSITION_STOPPED = 0;

    // Note: Queues loop around so next/previous are always available.
    private static final long PLAYING_ACTIONS = PlaybackState.ACTION_PAUSE
            | PlaybackState.ACTION_PLAY_FROM_MEDIA_ID | PlaybackState.ACTION_SKIP_TO_NEXT
            | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM;

    private static final long PAUSED_ACTIONS = PlaybackState.ACTION_PLAY
            | PlaybackState.ACTION_PLAY_FROM_MEDIA_ID | PlaybackState.ACTION_SKIP_TO_NEXT
            | PlaybackState.ACTION_SKIP_TO_PREVIOUS;

    private static final long STOPPED_ACTIONS = PlaybackState.ACTION_PLAY
            | PlaybackState.ACTION_PLAY_FROM_MEDIA_ID | PlaybackState.ACTION_SKIP_TO_NEXT
            | PlaybackState.ACTION_SKIP_TO_PREVIOUS;

    private static final String SHUFFLE = "android.car.media.localmediaplayer.shuffle";

    private final Context mContext;
    private final MediaSession mSession;
    private final AudioManager mAudioManager;
    private final PlaybackState mErrorState;
    private final DataModel mDataModel;
    private final CustomAction mShuffle;

    private List<QueueItem> mQueue;
    private int mCurrentQueueIdx = 0;
    private final SharedPreferences mSharedPrefs;

    private NotificationManager mNotificationManager;
    private Notification.Builder mPlayingNotificationBuilder;
    private Notification.Builder mPausedNotificationBuilder;

    // TODO: Use multiple media players for gapless playback.
    private final MediaPlayer mMediaPlayer;

    public Player(Context context, MediaSession session, DataModel dataModel) {
        mContext = context;
        mDataModel = dataModel;
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mSession = session;
        mSharedPrefs = context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);

        mShuffle = new CustomAction.Builder(SHUFFLE, context.getString(R.string.shuffle),
                R.drawable.shuffle).build();

        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.reset();
        mMediaPlayer.setOnCompletionListener(mOnCompletionListener);
        mErrorState = new PlaybackState.Builder()
                .setState(PlaybackState.STATE_ERROR, 0, 0)
                .setErrorMessage(context.getString(R.string.playback_error))
                .build();

        mNotificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // There are 2 forms of the media notification, when playing it needs to show the controls
        // to pause & skip whereas when paused it needs to show controls to play & skip. Setup
        // pre-populated builders for both of these up front.
        Notification.Action prevAction = makeNotificationAction(
                LocalMediaBrowserService.ACTION_PREV, R.drawable.ic_prev, R.string.prev);
        Notification.Action nextAction = makeNotificationAction(
                LocalMediaBrowserService.ACTION_NEXT, R.drawable.ic_next, R.string.next);
        Notification.Action playAction = makeNotificationAction(
                LocalMediaBrowserService.ACTION_PLAY, R.drawable.ic_play, R.string.play);
        Notification.Action pauseAction = makeNotificationAction(
                LocalMediaBrowserService.ACTION_PAUSE, R.drawable.ic_pause, R.string.pause);

        // While playing, you need prev, pause, next.
        mPlayingNotificationBuilder = new Notification.Builder(context)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setSmallIcon(R.drawable.ic_sd_storage_black)
                .addAction(prevAction)
                .addAction(pauseAction)
                .addAction(nextAction);

        // While paused, you need prev, play, next.
        mPausedNotificationBuilder = new Notification.Builder(context)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setSmallIcon(R.drawable.ic_sd_storage_black)
                .addAction(prevAction)
                .addAction(playAction)
                .addAction(nextAction);
    }

    private Notification.Action makeNotificationAction(String action, int iconId, int stringId) {
        PendingIntent intent = PendingIntent.getBroadcast(mContext, REQUEST_CODE,
                new Intent(action), PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Action notificationAction = new Notification.Action.Builder(iconId,
                mContext.getString(stringId), intent)
                .build();
        return notificationAction;
    }

    private boolean requestAudioFocus(Runnable onSuccess) {
        int result = mAudioManager.requestAudioFocus(mAudioFocusListener, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            onSuccess.run();
            return true;
        }
        Log.e(TAG, "Failed to acquire audio focus");
        return false;
    }

    @Override
    public void onPlay() {
        super.onPlay();
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onPlay");
        }
        requestAudioFocus(() -> resumePlayback());
    }

    @Override
    public void onPause() {
        super.onPause();
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onPause");
        }
        pausePlayback();
        mAudioManager.abandonAudioFocus(mAudioFocusListener);
    }

    public void destroy() {
        stopPlayback();
        mNotificationManager.cancelAll();
        mAudioManager.abandonAudioFocus(mAudioFocusListener);
        mMediaPlayer.release();
    }

    public void saveState() {
        if (mQueue == null || mQueue.isEmpty()) {
            return;
        }

        Playlist playlist = new Playlist();
        playlist.songs = new Song[mQueue.size()];

        int idx = 0;
        for (QueueItem item : mQueue) {
            Song song = new Song();
            song.queueId = item.getQueueId();
            MediaDescription description = item.getDescription();
            song.mediaId = description.getMediaId();
            song.title = description.getTitle().toString();
            song.subtitle = description.getSubtitle().toString();
            song.path = description.getExtras().getString(DataModel.PATH_KEY);

            playlist.songs[idx] = song;
            idx++;
        }
        playlist.currentQueueId = mQueue.get(mCurrentQueueIdx).getQueueId();
        playlist.currentSongPosition = mMediaPlayer.getCurrentPosition();
        playlist.name = CURRENT_PLAYLIST_KEY;

        // Go to Base64 to ensure that we can actually store the string in a sharedpref. This is
        // slightly wasteful because of the fact that base64 expands the size a bit but it's a
        // lot less riskier than abusing the java string to directly store bytes coming out of
        // proto encoding.
        String serialized = Base64.getEncoder().encodeToString(MessageNano.toByteArray(playlist));
        SharedPreferences.Editor editor = mSharedPrefs.edit();
        editor.putString(CURRENT_PLAYLIST_KEY, serialized);
        editor.commit();
    }

    private boolean maybeRebuildQueue(Playlist playlist) {
        List<QueueItem> queue = new ArrayList<>();
        int foundIdx = 0;
        // You need to check if the playlist actually is still valid because the user could have
        // deleted files or taken out the sd card between runs so we might as well check this ahead
        // of time before we load up the playlist.
        for (Song song : playlist.songs) {
            File tmp = new File(song.path);
            if (!tmp.exists()) {
                continue;
            }

            if (playlist.currentQueueId == song.queueId) {
                foundIdx = queue.size();
            }

            Bundle bundle = new Bundle();
            bundle.putString(DataModel.PATH_KEY, song.path);
            MediaDescription description = new MediaDescription.Builder()
                    .setMediaId(song.mediaId)
                    .setTitle(song.title)
                    .setSubtitle(song.subtitle)
                    .setExtras(bundle)
                    .build();
            queue.add(new QueueItem(description, song.queueId));
        }

        if (queue.isEmpty()) {
            return false;
        }

        mQueue = queue;
        mCurrentQueueIdx = foundIdx;  // Resumes from beginning if last playing song was not found.

        return true;
    }

    public boolean maybeRestoreState() {
        String serialized = mSharedPrefs.getString(CURRENT_PLAYLIST_KEY, null);
        if (serialized == null) {
            return false;
        }

        try {
            Playlist playlist = Playlist.parseFrom(Base64.getDecoder().decode(serialized));
            if (!maybeRebuildQueue(playlist)) {
                return false;
            }
            updateSessionQueueState();

            requestAudioFocus(() -> {
                try {
                    playCurrentQueueIndex();
                    mMediaPlayer.seekTo(playlist.currentSongPosition);
                    updatePlaybackStatePlaying();
                } catch (IOException e) {
                    Log.e(TAG, "Restored queue, but couldn't resume playback.");
                }
            });
        } catch (IllegalArgumentException | InvalidProtocolBufferNanoException e) {
            // Couldn't restore the playlist. Not the end of the world.
            return false;
        }

        return true;
    }

    private void updateSessionQueueState() {
        mSession.setQueueTitle(mContext.getString(R.string.playlist));
        mSession.setQueue(mQueue);
    }

    private void startPlayback(String key) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "startPlayback()");
        }

        List<QueueItem> queue = mDataModel.getQueue();
        int idx = 0;
        int foundIdx = -1;
        for (QueueItem item : queue) {
            if (item.getDescription().getMediaId().equals(key)) {
                foundIdx = idx;
                break;
            }
            idx++;
        }

        if (foundIdx == -1) {
            mSession.setPlaybackState(mErrorState);
            return;
        }

        mQueue = new ArrayList<>(queue);
        mCurrentQueueIdx = foundIdx;
        QueueItem current = mQueue.get(mCurrentQueueIdx);
        String path = current.getDescription().getExtras().getString(DataModel.PATH_KEY);
        MediaMetadata metadata = mDataModel.getMetadata(current.getDescription().getMediaId());
        updateSessionQueueState();

        try {
            play(path, metadata);
        } catch (IOException e) {
            Log.e(TAG, "Playback failed.", e);
            mSession.setPlaybackState(mErrorState);
        }
    }

    private void resumePlayback() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "resumePlayback()");
        }

        updatePlaybackStatePlaying();

        if (!mMediaPlayer.isPlaying()) {
            mMediaPlayer.start();
        }
    }

    private void postMediaNotification(Notification.Builder builder) {
        if (mQueue == null) {
            return;
        }

        MediaDescription current = mQueue.get(mCurrentQueueIdx).getDescription();
        Notification notification = builder
                .setStyle(new Notification.MediaStyle().setMediaSession(mSession.getSessionToken()))
                .setContentTitle(current.getTitle())
                .setContentText(current.getSubtitle())
                .setShowWhen(false)
                .build();
        notification.flags |= Notification.FLAG_NO_CLEAR;
        mNotificationManager.notify(NOTIFICATION_ID, notification);
    }

    private void updatePlaybackStatePlaying() {
        if (!mSession.isActive()) {
            mSession.setActive(true);
        }

        // Update the state in the media session.
        PlaybackState state = new PlaybackState.Builder()
                .setState(PlaybackState.STATE_PLAYING,
                        mMediaPlayer.getCurrentPosition(), PLAYBACK_SPEED)
                .setActions(PLAYING_ACTIONS)
                .addCustomAction(mShuffle)
                .setActiveQueueItemId(mQueue.get(mCurrentQueueIdx).getQueueId())
                .build();
        mSession.setPlaybackState(state);

        // Update the media styled notification.
        postMediaNotification(mPlayingNotificationBuilder);
    }

    private void pausePlayback() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "pausePlayback()");
        }

        long currentPosition = 0;
        if (mMediaPlayer.isPlaying()) {
            currentPosition = mMediaPlayer.getCurrentPosition();
            mMediaPlayer.pause();
        }

        PlaybackState state = new PlaybackState.Builder()
                .setState(PlaybackState.STATE_PAUSED, currentPosition, PLAYBACK_SPEED_STOPPED)
                .setActions(PAUSED_ACTIONS)
                .addCustomAction(mShuffle)
                .build();
        mSession.setPlaybackState(state);

        // Update the media styled notification.
        postMediaNotification(mPausedNotificationBuilder);
    }

    private void stopPlayback() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "stopPlayback()");
        }

        if (mMediaPlayer.isPlaying()) {
            mMediaPlayer.stop();
        }

        PlaybackState state = new PlaybackState.Builder()
                .setState(PlaybackState.STATE_STOPPED, PLAYBACK_POSITION_STOPPED,
                        PLAYBACK_SPEED_STOPPED)
                .setActions(STOPPED_ACTIONS)
                .build();
        mSession.setPlaybackState(state);
    }

    private void advance() throws IOException {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "advance()");
        }
        // Go to the next song if one exists. Note that if you were to support gapless
        // playback, you would have to change this code such that you had a currently
        // playing and a loading MediaPlayer and juggled between them while also calling
        // setNextMediaPlayer.

        if (mQueue != null && !mQueue.isEmpty()) {
            // Keep looping around when we run off the end of our current queue.
            mCurrentQueueIdx = (mCurrentQueueIdx + 1) % mQueue.size();
            playCurrentQueueIndex();
        } else {
            stopPlayback();
        }
    }

    private void retreat() throws IOException {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "retreat()");
        }
        // Go to the next song if one exists. Note that if you were to support gapless
        // playback, you would have to change this code such that you had a currently
        // playing and a loading MediaPlayer and juggled between them while also calling
        // setNextMediaPlayer.
        if (mQueue != null) {
            // Keep looping around when we run off the end of our current queue.
            mCurrentQueueIdx--;
            if (mCurrentQueueIdx < 0) {
                mCurrentQueueIdx = mQueue.size() - 1;
            }
            playCurrentQueueIndex();
        } else {
            stopPlayback();
        }
    }

    private void playCurrentQueueIndex() throws IOException {
        MediaDescription next = mQueue.get(mCurrentQueueIdx).getDescription();
        String path = next.getExtras().getString(DataModel.PATH_KEY);
        MediaMetadata metadata = mDataModel.getMetadata(next.getMediaId());

        play(path, metadata);
    }

    private void play(String path, MediaMetadata metadata) throws IOException {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "play path=" + path + " metadata=" + metadata);
        }

        mMediaPlayer.reset();
        mMediaPlayer.setDataSource(path);
        mMediaPlayer.prepare();

        if (metadata != null) {
            mSession.setMetadata(metadata);
        }
        boolean wasGrantedAudio = requestAudioFocus(() -> {
            mMediaPlayer.start();
            updatePlaybackStatePlaying();
        });
        if (!wasGrantedAudio) {
            // player.pause() isn't needed since it should not actually be playing, the
            // other steps like, updating the notification and play state are needed, thus we
            // call the pause method.
            pausePlayback();
        }
    }

    private void safeAdvance() {
        try {
            advance();
        } catch (IOException e) {
            Log.e(TAG, "Failed to advance.", e);
            mSession.setPlaybackState(mErrorState);
        }
    }

    private void safeRetreat() {
        try {
            retreat();
        } catch (IOException e) {
            Log.e(TAG, "Failed to advance.", e);
            mSession.setPlaybackState(mErrorState);
        }
    }

    /**
     * This is a naive implementation of shuffle, previously played songs may repeat after the
     * shuffle operation. Only call this from the main thread.
     */
    private void shuffle() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Shuffling");
        }

        // rebuild the the queue in a shuffled form.
        if (mQueue != null && mQueue.size() > 2) {
            QueueItem current = mQueue.remove(mCurrentQueueIdx);
            Collections.shuffle(mQueue);
            mQueue.add(0, current);
            // A QueueItem contains a queue id that's used as the key for when the user selects
            // the current play list. This means the QueueItems must be rebuilt to have their new
            // id's set.
            for (int i = 0; i < mQueue.size(); i++) {
                mQueue.set(i, new QueueItem(mQueue.get(i).getDescription(), i));
            }
            mCurrentQueueIdx = 0;
            updateSessionQueueState();
        }
    }

    @Override
    public void onPlayFromMediaId(String mediaId, Bundle extras) {
        super.onPlayFromMediaId(mediaId, extras);
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onPlayFromMediaId mediaId" + mediaId + " extras=" + extras);
        }

        requestAudioFocus(() -> startPlayback(mediaId));
    }

    @Override
    public void onSkipToNext() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onSkipToNext()");
        }
        safeAdvance();
    }

    @Override
    public void onSkipToPrevious() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onSkipToPrevious()");
        }
        safeRetreat();
    }

    @Override
    public void onSkipToQueueItem(long id) {
        try {
            mCurrentQueueIdx = (int) id;
            playCurrentQueueIndex();
        } catch (IOException e) {
            Log.e(TAG, "Failed to play.", e);
            mSession.setPlaybackState(mErrorState);
        }
    }

    @Override
    public void onCustomAction(String action, Bundle extras) {
        switch (action) {
            case SHUFFLE:
                shuffle();
                break;
            default:
                Log.e(TAG, "Unhandled custom action: " + action);
        }
    }

    private OnAudioFocusChangeListener mAudioFocusListener = new OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focus) {
            switch (focus) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    resumePlayback();
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    pausePlayback();
                    break;
                default:
                    Log.e(TAG, "Unhandled audio focus type: " + focus);
            }
        }
    };

    private OnCompletionListener mOnCompletionListener = new OnCompletionListener() {
        @Override
        public void onCompletion(MediaPlayer mediaPlayer) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onCompletion()");
            }
            safeAdvance();
        }
    };
}
