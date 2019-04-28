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

package com.android.tv.menu;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.tv.MainActivity;
import com.android.tv.R;
import com.android.tv.data.Channel;
import com.android.tv.data.Program;
import com.android.tv.parental.ParentalControlSettings;
import com.android.tv.util.ImageLoader;

import java.util.Objects;

/**
 * A view to render channel card.
 */
public class ChannelCardView extends BaseCardView<ChannelsRowItem> {
    private static final String TAG = MenuView.TAG;
    private static final boolean DEBUG = MenuView.DEBUG;

    private final int mCardImageWidth;
    private final int mCardImageHeight;

    private ImageView mImageView;
    private TextView mChannelNumberNameView;
    private ProgressBar mProgressBar;
    private Channel mChannel;
    private Program mProgram;
    private String mPosterArtUri;
    private final MainActivity mMainActivity;

    public ChannelCardView(Context context) {
        this(context, null);
    }

    public ChannelCardView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ChannelCardView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mCardImageWidth = getResources().getDimensionPixelSize(R.dimen.card_image_layout_width);
        mCardImageHeight = getResources().getDimensionPixelSize(R.dimen.card_image_layout_height);
        mMainActivity = (MainActivity) context;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mImageView = (ImageView) findViewById(R.id.image);
        mImageView.setBackgroundResource(R.color.channel_card);
        mChannelNumberNameView = (TextView) findViewById(R.id.channel_number_and_name);
        mProgressBar = (ProgressBar) findViewById(R.id.progress);
    }

    @Override
    public void onBind(ChannelsRowItem item, boolean selected) {
        if (DEBUG) {
            Log.d(TAG, "onBind(channelName=" + item.getChannel().getDisplayName() + ", selected="
                    + selected + ")");
        }
        updateChannel(item);
        updateProgram();
        super.onBind(item, selected);
    }

    private void updateChannel(ChannelsRowItem item) {
        if (!item.getChannel().equals(mChannel)) {
            mChannel = item.getChannel();
            mChannelNumberNameView.setText(mChannel.getDisplayText());
            mChannelNumberNameView.setVisibility(VISIBLE);
        }
    }

    private void updateProgram() {
        ParentalControlSettings parental = mMainActivity.getParentalControlSettings();
        if (parental.isParentalControlsEnabled() && mChannel.isLocked()) {
            setText(R.string.program_title_for_blocked_channel);
            mProgram = null;
        } else {
            Program currentProgram =
                    mMainActivity.getProgramDataManager().getCurrentProgram(mChannel.getId());
            if (!Objects.equals(currentProgram, mProgram)) {
                mProgram = currentProgram;
                if (mProgram == null || TextUtils.isEmpty(mProgram.getTitle())) {
                    setTextViewEnabled(false);
                    setText(R.string.program_title_for_no_information);
                } else {
                    setTextViewEnabled(true);
                    setText(mProgram.getTitle());
                }
            }
        }
        if (mProgram == null) {
            mProgressBar.setVisibility(GONE);
            setPosterArt(null);
        } else {
            // Update progress.
            mProgressBar.setVisibility(View.VISIBLE);
            long startTime = mProgram.getStartTimeUtcMillis();
            long endTime = mProgram.getEndTimeUtcMillis();
            long currTime = System.currentTimeMillis();
            if (currTime <= startTime) {
                mProgressBar.setProgress(0);
            } else if (currTime >= endTime) {
                mProgressBar.setProgress(100);
            } else {
                mProgressBar.setProgress(
                        (int) (100 * (currTime - startTime) / (endTime - startTime)));
            }
            // Update image.
            if (!parental.isParentalControlsEnabled()
                    || !parental.isRatingBlocked(mProgram.getContentRatings())) {
                setPosterArt(mProgram.getPosterArtUri());
            }
        }
    }

    private static ImageLoader.ImageLoaderCallback<ChannelCardView> createProgramPosterArtCallback(
            ChannelCardView cardView, final Program program) {
        return new ImageLoader.ImageLoaderCallback<ChannelCardView>(cardView) {
            @Override
            public void onBitmapLoaded(ChannelCardView cardView, @Nullable Bitmap posterArt) {
                if (posterArt == null || cardView.mProgram == null
                        || program.getChannelId() != cardView.mProgram.getChannelId()
                        || program.getChannelId() != cardView.mChannel.getId()) {
                    return;
                }
                cardView.updatePosterArt(posterArt);
            }
        };
    }

    private void setPosterArt(String posterArtUri) {
        if (!TextUtils.equals(mPosterArtUri, posterArtUri)) {
            mPosterArtUri = posterArtUri;
            if (posterArtUri == null
                    || !mProgram.loadPosterArt(getContext(), mCardImageWidth, mCardImageHeight,
                            createProgramPosterArtCallback(this, mProgram))) {
                mImageView.setImageResource(R.drawable.ic_recent_thumbnail_default);
                mImageView.setForeground(null);
            }
        }
    }

    private void updatePosterArt(Bitmap posterArt) {
        mImageView.setImageBitmap(posterArt);
        mImageView.setForeground(getContext().getDrawable(R.drawable.card_image_gradient));
    }
}