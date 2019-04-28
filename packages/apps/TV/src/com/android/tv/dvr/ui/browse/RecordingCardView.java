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

package com.android.tv.dvr.ui.browse;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.support.v17.leanback.widget.BaseCardView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.tv.R;
import com.android.tv.dvr.data.RecordedProgram;
import com.android.tv.ui.ViewUtils;
import com.android.tv.util.ImageLoader;

/**
 * A CardView for displaying info about a {@link com.android.tv.dvr.data.ScheduledRecording}
 * or {@link RecordedProgram} or {@link com.android.tv.dvr.data.SeriesRecording}.
 */
public class RecordingCardView extends BaseCardView {
    // This value should be the same with
    // android.support.v17.leanback.widget.FocusHighlightHelper.BrowseItemFocusHighlight.DURATION_MS
    private final static int ANIMATION_DURATION = 150;
    private final ImageView mImageView;
    private final int mImageWidth;
    private final int mImageHeight;
    private String mImageUri;
    private final TextView mMajorContentView;
    private final TextView mMinorContentView;
    private final ProgressBar mProgressBar;
    private final View mAffiliatedIconContainer;
    private final ImageView mAffiliatedIcon;
    private final Drawable mDefaultImage;
    private final FrameLayout mTitleArea;
    private final TextView mFoldedTitleView;
    private final TextView mExpandedTitleView;
    private final ValueAnimator mExpandTitleAnimator;
    private final int mFoldedTitleHeight;
    private final int mExpandedTitleHeight;
    private final boolean mExpandTitleWhenFocused;
    private boolean mExpanded;
    private String mDetailBackgroundImageUri;

    public RecordingCardView(Context context) {
        this(context, false);
    }

    public RecordingCardView(Context context, boolean expandTitleWhenFocused) {
        this(context, context.getResources().getDimensionPixelSize(
                R.dimen.dvr_library_card_image_layout_width), context.getResources()
                .getDimensionPixelSize(R.dimen.dvr_library_card_image_layout_height),
                expandTitleWhenFocused);
    }

    public RecordingCardView(Context context, int imageWidth, int imageHeight,
            boolean expandTitleWhenFocused) {
        super(context);
        //TODO(dvr): move these to the layout XML.
        setCardType(BaseCardView.CARD_TYPE_INFO_UNDER_WITH_EXTRA);
        setInfoVisibility(BaseCardView.CARD_REGION_VISIBLE_ALWAYS);
        setFocusable(true);
        setFocusableInTouchMode(true);
        mDefaultImage = getResources().getDrawable(R.drawable.dvr_default_poster, null);

        LayoutInflater inflater = LayoutInflater.from(getContext());
        inflater.inflate(R.layout.dvr_recording_card_view, this);
        mImageView = (ImageView) findViewById(R.id.image);
        mImageWidth = imageWidth;
        mImageHeight = imageHeight;
        mProgressBar = (ProgressBar) findViewById(R.id.recording_progress);
        mAffiliatedIconContainer = findViewById(R.id.affiliated_icon_container);
        mAffiliatedIcon = (ImageView) findViewById(R.id.affiliated_icon);
        mMajorContentView = (TextView) findViewById(R.id.content_major);
        mMinorContentView = (TextView) findViewById(R.id.content_minor);
        mTitleArea = (FrameLayout) findViewById(R.id.title_area);
        mFoldedTitleView = (TextView) findViewById(R.id.title_one_line);
        mExpandedTitleView = (TextView) findViewById(R.id.title_two_lines);
        mFoldedTitleHeight = getResources()
                .getDimensionPixelSize(R.dimen.dvr_library_card_folded_title_height);
        mExpandedTitleHeight = getResources()
                .getDimensionPixelSize(R.dimen.dvr_library_card_expanded_title_height);
        mExpandTitleAnimator = ValueAnimator.ofFloat(0.0f, 1.0f).setDuration(ANIMATION_DURATION);
        mExpandTitleAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float value = (Float) valueAnimator.getAnimatedValue();
                mExpandedTitleView.setAlpha(value);
                mFoldedTitleView.setAlpha(1.0f - value);
                ViewUtils.setLayoutHeight(mTitleArea, (int) (mFoldedTitleHeight
                        + (mExpandedTitleHeight - mFoldedTitleHeight) * value));
            }
        });
        mExpandTitleWhenFocused = expandTitleWhenFocused;
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        // Preload the background image going to be used in detail fragments here to prevent
        // loading and drawing background images during activity transitions.
        if (gainFocus) {
            if (!TextUtils.isEmpty(mDetailBackgroundImageUri)) {
                ImageLoader.loadBitmap(getContext(), mDetailBackgroundImageUri,
                            Integer.MAX_VALUE, Integer.MAX_VALUE, null);
            }
        }
        if (mExpandTitleWhenFocused) {
            if (gainFocus) {
                expandTitle(true, true);
            } else {
                expandTitle(false, true);
            }
        }
    }

    /**
     * Expands/folds the title area to show program title with two/one lines.
     *
     * @param expand {@code true} to expand the title area, or {@code false} to fold it.
     * @param withAnimation {@code true} to expand/fold with animation.
     */
    public void expandTitle(boolean expand, boolean withAnimation) {
        if (expand != mExpanded && mFoldedTitleView.getLayout().getEllipsisCount(0) > 0) {
            if (withAnimation) {
                if (expand) {
                    mExpandTitleAnimator.start();
                } else {
                    mExpandTitleAnimator.reverse();
                }
            } else {
                if (expand) {
                    mFoldedTitleView.setAlpha(0.0f);
                    mExpandedTitleView.setAlpha(1.0f);
                    ViewUtils.setLayoutHeight(mTitleArea, mExpandedTitleHeight);
                } else {
                    mFoldedTitleView.setAlpha(1.0f);
                    mExpandedTitleView.setAlpha(0.0f);
                    ViewUtils.setLayoutHeight(mTitleArea, mFoldedTitleHeight);
                }
            }
            mExpanded = expand;
        }
    }

    void setTitle(CharSequence title) {
        mFoldedTitleView.setText(title);
        mExpandedTitleView.setText(title);
    }

    void setContent(CharSequence majorContent, CharSequence minorContent) {
        if (!TextUtils.isEmpty(majorContent)) {
            mMajorContentView.setText(majorContent);
            mMajorContentView.setVisibility(View.VISIBLE);
        } else {
            mMajorContentView.setVisibility(View.GONE);
        }
        if (!TextUtils.isEmpty(minorContent)) {
            mMinorContentView.setText(minorContent);
            mMinorContentView.setVisibility(View.VISIBLE);
        } else {
            mMinorContentView.setVisibility(View.GONE);
        }
    }

    /**
     * Sets progress bar. If progress is {@code null}, hides progress bar.
     */
    void setProgressBar(Integer progress) {
        if (progress == null) {
            mProgressBar.setVisibility(View.GONE);
        } else {
            mProgressBar.setProgress(progress);
            mProgressBar.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Sets the color of progress bar.
     */
    void setProgressBarColor(int color) {
        mProgressBar.getProgressDrawable().setTint(color);
    }

    /**
     * Sets the image URI of the poster should be shown on the card view.

     * @param isChannelLogo {@code true} if the image is from channels' logo.
     */
    void setImageUri(String uri, boolean isChannelLogo) {
        if (isChannelLogo) {
            mImageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        } else {
            mImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        }
        mImageUri = uri;
        if (TextUtils.isEmpty(uri)) {
            mImageView.setImageDrawable(mDefaultImage);
        } else {
            ImageLoader.loadBitmap(getContext(), uri, mImageWidth, mImageHeight,
                    new RecordingCardImageLoaderCallback(this, uri));
        }
    }

    /**
     * Sets the {@link Drawable} of the poster should be shown on the card view.
     */
    public void setImage(Drawable image) {
        if (image != null) {
            mImageView.setImageDrawable(image);
        }
    }

    /**
     * Sets the affiliated icon of the card view, which will be displayed at the lower-right corner
     * of the poster.
     */
    public void setAffiliatedIcon(int imageResId) {
        if (imageResId > 0) {
            mAffiliatedIconContainer.setVisibility(View.VISIBLE);
            mAffiliatedIcon.setImageResource(imageResId);
        } else {
            mAffiliatedIconContainer.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * Sets the background image URI of the card view, which will be displayed as background when
     * the view is clicked and shows its details fragment.
     */
    public void setDetailBackgroundImageUri(String uri) {
        mDetailBackgroundImageUri = uri;
    }

    /**
     * Returns image view.
     */
    public ImageView getImageView() {
        return mImageView;
    }

    private static class RecordingCardImageLoaderCallback
            extends ImageLoader.ImageLoaderCallback<RecordingCardView> {
        private final String mUri;

        RecordingCardImageLoaderCallback(RecordingCardView referent, String uri) {
            super(referent);
            mUri = uri;
        }

        @Override
        public void onBitmapLoaded(RecordingCardView view, @Nullable Bitmap bitmap) {
            if (bitmap == null || !mUri.equals(view.mImageUri)) {
                view.mImageView.setImageDrawable(view.mDefaultImage);
            } else {
                view.mImageView.setImageDrawable(new BitmapDrawable(view.getResources(), bitmap));
            }
        }
    }

    public void reset() {
        mFoldedTitleView.setText(null);
        mExpandedTitleView.setText(null);
        setContent(null, null);
        mImageView.setImageDrawable(mDefaultImage);
    }
}