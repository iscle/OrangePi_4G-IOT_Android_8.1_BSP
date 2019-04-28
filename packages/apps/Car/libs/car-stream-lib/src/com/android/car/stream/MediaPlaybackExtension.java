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
package com.android.car.stream;

import android.app.PendingIntent;
import android.graphics.Bitmap;
import android.os.Bundle;

/**
 * An extension to {@link StreamCard} that holds data for media playback controls.
 */
public class MediaPlaybackExtension extends StreamCardExtension {
    private static final String TITLE_KEY = "title_key";
    private static final String SUBTITLE_KEY = "subtitle_key";
    private static final String ALBUM_ART_ICON_KEY = "album_art_icon";
    private static final String ACCENT_COLOR_KEY = "accent_color";
    private static final String CAN_SKIP_TO_NEXT_KEY = "can_skip_to_next";
    private static final String CAN_SKIP_TO_PREV_KEY = "can_skip_to_prev";
    private static final String HAS_PAUSE_KEY = "has_pause";
    private static final String IS_PLAYING_KEY = "is_playing";
    private static final String APP_NAME_KEY = "app_name";

    private static final String SKIP_TO_NEXT_ACTION_KEY = "skip_to_next_action";
    private static final String SKIP_TO_PREVIOUS_ACTION_KEY = "skip_to_previous_action";
    private static final String PLAY_ACTION_KEY = "play_action";
    private static final String PAUSE_ACTION_KEY = "pause_action";
    private static final String STOP_ACTION_KEY = "stop_action";

    private String mTitle;
    private String mSubTitle;
    private Bitmap mAlbumArt;
    private int mAppAccentColor;
    private String mAppName;

    private boolean mCanSkipToNext = false;
    private boolean mCanSkipToPrevious = false;
    private boolean mHasPause = false;
    private boolean mIsPlaying = false;

    private PendingIntent mPauseAction;
    private PendingIntent mSkipToNextAction;
    private PendingIntent mSkipToPreviousAction;
    private PendingIntent mPlayAction;
    private PendingIntent mStopAction;

    public static final Creator<MediaPlaybackExtension> CREATOR
            = new BundleableCreator<>(MediaPlaybackExtension.class);

    public MediaPlaybackExtension() {}

    public MediaPlaybackExtension(
            String title,
            String subtitle,
            Bitmap albumArt,
            int appAccentColor,
            boolean canSkipToNext,
            boolean canSkipToPrevious,
            boolean hasPause,
            boolean isPlaying,
            String appName,
            PendingIntent stopAction,
            PendingIntent pauseAction,
            PendingIntent playAction,
            PendingIntent skipToNextAction,
            PendingIntent skipToPreviousAction) {

        mTitle = title;
        mSubTitle = subtitle;
        mAlbumArt = albumArt;
        mAppAccentColor = appAccentColor;
        mCanSkipToNext = canSkipToNext;
        mCanSkipToPrevious = canSkipToPrevious;
        mHasPause = hasPause;
        mIsPlaying = isPlaying;
        mAppName = appName;

        mStopAction = stopAction;
        mPauseAction = pauseAction;
        mPlayAction = playAction;
        mSkipToNextAction = skipToNextAction;
        mSkipToPreviousAction = skipToPreviousAction;
    }

    public String getTitle() {
        return mTitle;
    }

    public String getSubtitle() {
        return mSubTitle;
    }

    public Bitmap getAlbumArt() {
        return mAlbumArt;
    }

    public int getAppAccentColor() {
        return mAppAccentColor;
    }

    public boolean canSkipToNext() {
        return mCanSkipToNext;
    }

    public boolean canSkipToPrevious() {
        return mCanSkipToPrevious;
    }

    public boolean hasPause() {
        return mHasPause;
    }

    public boolean isPlaying() {
        return mIsPlaying;
    }

    public String getAppName() {
        return mAppName;
    }

    public PendingIntent getStopAction() {
        return mStopAction;
    }

    public PendingIntent getPlayAction() {
        return mPlayAction;
    }

    public PendingIntent getSkipToPreviousAction() {
        return mSkipToPreviousAction;
    }

    public PendingIntent getSkipToNextAction() {
        return mSkipToNextAction;
    }

    public PendingIntent getPauseAction() {
        return mPauseAction;
    }

    @Override
    protected void writeToBundle(Bundle bundle) {
        bundle.putString(TITLE_KEY, mTitle);
        bundle.putString(SUBTITLE_KEY, mSubTitle);
        bundle.putParcelable(ALBUM_ART_ICON_KEY, mAlbumArt);
        bundle.putBoolean(CAN_SKIP_TO_NEXT_KEY, mCanSkipToNext);
        bundle.putBoolean(CAN_SKIP_TO_PREV_KEY, mCanSkipToPrevious);
        bundle.putBoolean(HAS_PAUSE_KEY, mHasPause);
        bundle.putBoolean(IS_PLAYING_KEY, mIsPlaying);
        bundle.putInt(ACCENT_COLOR_KEY, mAppAccentColor);
        bundle.putString(APP_NAME_KEY, mAppName);

        bundle.putParcelable(STOP_ACTION_KEY, mStopAction);
        bundle.putParcelable(PLAY_ACTION_KEY, mPlayAction);
        bundle.putParcelable(PAUSE_ACTION_KEY, mPauseAction);
        bundle.putParcelable(SKIP_TO_NEXT_ACTION_KEY, mSkipToNextAction);
        bundle.putParcelable(SKIP_TO_PREVIOUS_ACTION_KEY, mSkipToPreviousAction);
    }

    @Override
    protected void readFromBundle(Bundle bundle) {
        mTitle = bundle.getString(TITLE_KEY);
        mSubTitle = bundle.getString(SUBTITLE_KEY);
        mAlbumArt = bundle.getParcelable(ALBUM_ART_ICON_KEY);
        mCanSkipToNext = bundle.getBoolean(CAN_SKIP_TO_NEXT_KEY);
        mCanSkipToPrevious = bundle.getBoolean(CAN_SKIP_TO_PREV_KEY);
        mHasPause = bundle.getBoolean(HAS_PAUSE_KEY);
        mIsPlaying = bundle.getBoolean(IS_PLAYING_KEY);
        mAppAccentColor = bundle.getInt(ACCENT_COLOR_KEY);
        mAppName = bundle.getString(APP_NAME_KEY);

        mStopAction = bundle.getParcelable(STOP_ACTION_KEY);
        mPlayAction = bundle.getParcelable(PLAY_ACTION_KEY);
        mPauseAction = bundle.getParcelable(PAUSE_ACTION_KEY);
        mSkipToNextAction = bundle.getParcelable(SKIP_TO_NEXT_ACTION_KEY);
        mSkipToPreviousAction = bundle.getParcelable(SKIP_TO_PREVIOUS_ACTION_KEY);
    }
}
