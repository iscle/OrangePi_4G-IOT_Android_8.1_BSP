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
package com.android.car.overview;

import android.app.PendingIntent;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.car.stream.MediaPlaybackExtension;
import com.android.car.stream.StreamCard;

/**
 * A {@link StreamViewHolder} that binds a {@link MediaPlaybackExtension} to
 * an interactive in playback UI card.
 */
public class MediaStreamViewHolder extends StreamViewHolder {
    private static final String TAG = "MediaStreamViewHolder";

    private static final String ELLIPSIS = "\u2026";
    // limit the subtitle to 20 chars, and the rest space is given to the app name.
    private static final int MAX_TEXT_LENGTH = 20;
    private static final String UNICODE_BULLET_SPACER = " \u2022 ";

    private final ImageView mPlayerBackground;
    private final TextView mTitleTextView;
    private final TextView mSubtitleTextview;

    private final ImageButton mSkipToPreviousButton;
    private final ImageButton mSkipToNextButton;
    private final OverviewFabButton mPlayPauseButton;

    private PendingIntent mPlayPauseAction;
    private PendingIntent mSkipToNextAction;
    private PendingIntent mSkipToPreviousAction;

    private PendingIntent mContentPendingIntent;

    public MediaStreamViewHolder(Context context, View itemView) {
        super(context, itemView);

        mPlayerBackground = (ImageView) itemView.findViewById(R.id.media_player_background);
        mTitleTextView = (TextView) itemView.findViewById(R.id.title);
        mSubtitleTextview = (TextView) itemView.findViewById(R.id.subtitle);

        mSkipToPreviousButton = (ImageButton) itemView.findViewById(R.id.skip_previous);
        mSkipToNextButton = (ImageButton) itemView.findViewById(R.id.skip_next);
        mPlayPauseButton = (OverviewFabButton) itemView.findViewById(R.id.play_pause);

        mSkipToPreviousButton.setImageResource(R.drawable.ic_skip_previous);
        mSkipToNextButton.setImageResource(R.drawable.ic_skip_next);

        mPlayPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (mPlayPauseAction != null) {
                        mPlayPauseAction.send();
                    }
                } catch (PendingIntent.CanceledException e) {
                    Log.e(TAG, "Failed to send play/pause action pending intent", e);
                }
            }
        });

        mSkipToPreviousButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (mSkipToPreviousAction != null) {
                        mSkipToPreviousAction.send();
                    }
                } catch (PendingIntent.CanceledException e) {
                    Log.e(TAG, "Failed to send play/pause action pending intent", e);
                }
            }
        });

        mSkipToNextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (mSkipToNextAction != null) {
                        mSkipToNextAction.send();
                    }
                } catch (PendingIntent.CanceledException e) {
                    Log.e(TAG, "Failed to send play/pause action pending intent", e);
                }
            }
        });

        mActionContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (mContentPendingIntent != null) {
                        mContentPendingIntent.send();
                    }
                } catch (PendingIntent.CanceledException e) {
                    Log.e(TAG, "Failed to send content pending intent in media card", e);
                }
            }
        });
    }

    @Override
    public void bindStreamCard(StreamCard card) {
        super.bindStreamCard(card);
        MediaPlaybackExtension extension = (MediaPlaybackExtension) card.getCardExtension();

        if (extension.getAlbumArt() != null) {
            mPlayerBackground.setImageBitmap(extension.getAlbumArt());
            mPlayerBackground.setVisibility(View.VISIBLE);
        }

        String title = extension.getTitle();
        if (!TextUtils.isEmpty(title)) {
            mTitleTextView.setVisibility(View.VISIBLE);
            mTitleTextView.setText(title);
        }

        String subtitle = getSubtitle(extension);
        if (!TextUtils.isEmpty(title)) {
            mSubtitleTextview.setVisibility(View.VISIBLE);
            mSubtitleTextview.setText(subtitle);
        }

        int playPauseIcon;
        if (extension.isPlaying()) {
            if (extension.hasPause()) {
                playPauseIcon = R.drawable.ic_pause;
                mPlayPauseAction = extension.getPauseAction();
            } else {
                playPauseIcon = R.drawable.ic_stop;
                mPlayPauseAction = extension.getStopAction();
            }
        } else {
            playPauseIcon = R.drawable.ic_play_arrow;
            mPlayPauseAction = extension.getPlayAction();
        }

        mPlayPauseButton.setImageResource(playPauseIcon);
        mPlayPauseButton.setAccentColor(extension.getAppAccentColor());

        mSkipToPreviousAction = extension.getSkipToPreviousAction();
        mSkipToNextAction = extension.getSkipToNextAction();

        if (!extension.canSkipToNext()) {
            mSkipToNextButton.setColorFilter(mContext.getColor(R.color.car_grey_700));
            mSkipToNextButton.setEnabled(false);
        }

        if (!extension.canSkipToPrevious()) {
            mSkipToPreviousButton.setColorFilter(mContext.getColor(R.color.car_grey_700));
            mSkipToPreviousButton.setEnabled(false);
        }

        mContentPendingIntent = card.getContentPendingIntent();
    }

    @Override
    protected void resetViews() {
        mSubtitleTextview.setText(null);
        mTitleTextView.setText(null);

        mSubtitleTextview.setVisibility(View.GONE);
        mTitleTextView.setVisibility(View.GONE);
        mPlayerBackground.setVisibility(View.GONE);

        mSkipToNextButton.setEnabled(true);
        mSkipToPreviousButton.setEnabled(true);
        mSkipToNextButton.setColorFilter(0);
        mSkipToPreviousButton.setColorFilter(0);
    }

    /**
     * Ellipsize the subtitle and append the app name if available.
     */
    private String getSubtitle(MediaPlaybackExtension extension) {
        String appName = extension.getAppName();

        StringBuilder sb = new StringBuilder();
        if (!TextUtils.isEmpty(extension.getSubtitle())) {
            sb.append(ellipsizeText(extension.getSubtitle(), MAX_TEXT_LENGTH));
            if (!TextUtils.isEmpty(appName)) {
                sb.append(UNICODE_BULLET_SPACER);
                sb.append(appName);
            }
        }
        return sb.toString();
    }

    /**
     * Ellipsize text and append "..." to the end if its length exceeds the {@code maxLen}.
     * <p/>
     * We are not using the ellipsize in the TextView because we are ellipsizing
     * 2 strings separately.
     */
    private static CharSequence ellipsizeText(CharSequence text, int maxLen) {
        if (text == null || text.length() <= maxLen) {
            return text;
        }

        if (maxLen <= ELLIPSIS.length()) {
            Log.e(TAG, "Unable to truncate string to " + maxLen);
            return text;
        }
        StringBuilder sb = new StringBuilder();
        // now, maxLen > ellip.length()
        sb.append(text.subSequence(0, maxLen - ELLIPSIS.length()));
        sb.append(ELLIPSIS);
        return sb.toString();
    }
}
