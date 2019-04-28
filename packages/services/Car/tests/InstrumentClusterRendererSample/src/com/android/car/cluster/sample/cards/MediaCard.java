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

package com.android.car.cluster.sample.cards;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.car.cluster.sample.DebugUtil;
import com.android.car.cluster.sample.R;

/**
 * Card responsible to display media content.
 */
public class MediaCard extends CardView {

    private LinearLayout mMediaPanel;
    private ProgressBar mProgressBar;
    private TextView mTitle;
    private TextView mSubtitle;

    public MediaCard(Context context, PriorityChangedListener listener) {
        super(context, CardType.MEDIA, listener);
    }

    @Override
    protected void init() {
        inflate(R.layout.media_card);

        mPriority = PRIORITY_MEDIA_NOTIFICATION;

        mMediaPanel = viewById(R.id.message_panel);
        mProgressBar = viewById(R.id.progress_bar);
        mTitle = viewById(R.id.media_title);
        mSubtitle = viewById(R.id.media_subtitle);

        mDetailsPanel = mMediaPanel;
        mLeftIconSwitcher.setVisibility(GONE);
    }

    @Override
    public void onPlayRevealAnimation() {
        super.onPlayRevealAnimation();

        // Decrease priority once notification animation is complete.
        runDelayed(3000, new Runnable() {
            @Override
            public void run() {
                setPriority(PRIORITY_MEDIA_ACTIVE);
            }
        });

        if (mBackgroundImage.getVisibility() != GONE) {
            runDelayed(SHOW_ANIMATION_DURATION + 3000, new Runnable() {
                @Override
                public void run() {
                    animateBackgroundFadeOut();
                }
            });
        }
    }

    private void animateBackgroundFadeOut() {
        TimeInterpolator interpolator =
                new AccelerateDecelerateInterpolator(getContext(), null);

        long duration = 500 * DebugUtil.ANIMATION_FACTOR;

        mBackgroundImage.animate()
                .alpha(0f)
                .setDuration(duration);

        mLeftIconSwitcher.setTranslationX(mLeftPadding);
        mLeftIconSwitcher.setVisibility(VISIBLE);
        mRightIconSwitcher.animate()
                .translationX(mLeftPadding + mIconsOverlap)
                .setInterpolator(interpolator)
                .setDuration(duration);

        mDetailsPanel.animate()
                .translationX(
                        mLeftPadding + mIconsOverlap + mIconSize + mLeftPadding)
                .setInterpolator(interpolator)
                .setDuration(duration);

    }

    public void setProgressColor(int color) {
        mProgressBar.getIndeterminateDrawable().setColorFilter(color, PorterDuff.Mode.SRC_IN);
        mProgressBar.setProgressTintList(ColorStateList.valueOf(color));
    }

    public void setProgress(int progress) {
        if (progress == -1) {
            mProgressBar.setIndeterminate(true);
            return;
        } else {
            mProgressBar.setIndeterminate(false);
        }

        if (progress > 100) {
            progress = 100;
        }
        if (progress < 0) {
            progress = 0;
        }
        mProgressBar.setProgress(progress);
    }

    public void setTitle(String album) {
        mTitle.setText(album);
    }

    public void setSubtitle(String track) {
        mSubtitle.setText(track);
    }

    public String getTitle() {
        return mTitle.getText() == null ? null : String.valueOf(mTitle.getText());
    }

    public String getSubtitle() {
        return mSubtitle.getText() == null ? null : String.valueOf(mSubtitle.getText());
    }
}
