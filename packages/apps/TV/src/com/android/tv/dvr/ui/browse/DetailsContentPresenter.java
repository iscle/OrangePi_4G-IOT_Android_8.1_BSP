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
 * limitations under the License
 */

package com.android.tv.dvr.ui.browse;

import android.app.Activity;
import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.graphics.Paint;
import android.graphics.Paint.FontMetricsInt;
import android.support.v17.leanback.widget.Presenter;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.tv.R;
import com.android.tv.ui.ViewUtils;
import com.android.tv.util.Utils;

/**
 * An {@link Presenter} for rendering a detailed description of an DVR item.
 * Typically this Presenter will be used in a
 * {@link android.support.v17.leanback.widget.DetailsOverviewRowPresenter}.
 * Most codes of this class is originated from
 * {@link android.support.v17.leanback.widget.AbstractDetailsDescriptionPresenter}.
 * The latter class are re-used to provide a customized version of
 * {@link android.support.v17.leanback.widget.DetailsOverviewRow}.
 */
class DetailsContentPresenter extends Presenter {
    /**
     * The ViewHolder for the {@link DetailsContentPresenter}.
     */
    public static class ViewHolder extends Presenter.ViewHolder {
        final TextView mTitle;
        final TextView mSubtitle;
        final LinearLayout mDescriptionContainer;
        final TextView mBody;
        final TextView mReadMoreView;
        final int mTitleMargin;
        final int mUnderTitleBaselineMargin;
        final int mUnderSubtitleBaselineMargin;
        final int mTitleLineSpacing;
        final int mBodyLineSpacing;
        final int mBodyMaxLines;
        final int mBodyMinLines;
        final FontMetricsInt mTitleFontMetricsInt;
        final FontMetricsInt mSubtitleFontMetricsInt;
        final FontMetricsInt mBodyFontMetricsInt;
        final int mTitleMaxLines;

        private Activity mActivity;
        private boolean mFullTextMode;
        private int mFullTextAnimationDuration;
        private boolean mIsListeningToPreDraw;

        private ViewTreeObserver.OnPreDrawListener mPreDrawListener =
                new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        if (mSubtitle.getVisibility() == View.VISIBLE
                                && mSubtitle.getTop() > view.getHeight()
                                && mTitle.getLineCount() > 1) {
                            mTitle.setMaxLines(mTitle.getLineCount() - 1);
                            return false;
                        }
                        final int bodyLines = mBody.getLineCount();
                        int maxLines = mFullTextMode ? bodyLines :
                                (mTitle.getLineCount() > 1 ? mBodyMinLines : mBodyMaxLines);
                        if (bodyLines > maxLines) {
                            mReadMoreView.setVisibility(View.VISIBLE);
                            mDescriptionContainer.setFocusable(true);
                            mDescriptionContainer.setClickable(true);
                            mDescriptionContainer.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    mFullTextMode = true;
                                    mReadMoreView.setVisibility(View.GONE);
                                    mDescriptionContainer.setFocusable((
                                            (AccessibilityManager) view.getContext()
                                                    .getSystemService(
                                                            Context.ACCESSIBILITY_SERVICE))
                                            .isEnabled());
                                    mDescriptionContainer.setClickable(false);
                                    mDescriptionContainer.setOnClickListener(null);
                                    int oldMaxLines = mBody.getMaxLines();
                                    mBody.setMaxLines(bodyLines);
                                    // Minus 1 from line difference to eliminate the space
                                    // originally occupied by "READ MORE"
                                    showFullText((bodyLines - oldMaxLines - 1) * mBodyLineSpacing);
                                }
                            });
                        }
                        if (mReadMoreView.getVisibility() == View.VISIBLE
                                && mSubtitle.getVisibility() == View.VISIBLE) {
                            // If both "READ MORE" and subtitle is shown, the capable maximum lines
                            // will be one line less.
                            maxLines -= 1;
                        }
                        if (mBody.getMaxLines() != maxLines) {
                            mBody.setMaxLines(maxLines);
                            return false;
                        } else {
                            removePreDrawListener();
                            return true;
                        }
                    }
                };

        public ViewHolder(final View view) {
            super(view);
            view.addOnAttachStateChangeListener(
                    new View.OnAttachStateChangeListener() {
                        @Override
                        public void onViewAttachedToWindow(View v) {
                            // In case predraw listener was removed in detach, make sure
                            // we have the proper layout.
                            addPreDrawListener();
                        }

                        @Override
                        public void onViewDetachedFromWindow(View v) {
                            removePreDrawListener();
                        }
                    });
            mTitle = (TextView) view.findViewById(R.id.dvr_details_description_title);
            mSubtitle = (TextView) view.findViewById(R.id.dvr_details_description_subtitle);
            mBody = (TextView) view.findViewById(R.id.dvr_details_description_body);
            mDescriptionContainer =
                    (LinearLayout) view.findViewById(R.id.dvr_details_description_container);
            // We have to explicitly set focusable to true here for accessibility, since we might
            // set the view's focusable state when we need to show "READ MORE", which would remove
            // the default focusable state for accessibility.
            mDescriptionContainer.setFocusable(((AccessibilityManager) view.getContext()
                    .getSystemService(Context.ACCESSIBILITY_SERVICE)).isEnabled());
            mReadMoreView = (TextView) view.findViewById(R.id.dvr_details_description_read_more);

            FontMetricsInt titleFontMetricsInt = getFontMetricsInt(mTitle);
            final int titleAscent = view.getResources().getDimensionPixelSize(
                    R.dimen.lb_details_description_title_baseline);
            // Ascent is negative
            mTitleMargin = titleAscent + titleFontMetricsInt.ascent;

            mUnderTitleBaselineMargin = view.getResources().getDimensionPixelSize(
                    R.dimen.lb_details_description_under_title_baseline_margin);
            mUnderSubtitleBaselineMargin = view.getResources().getDimensionPixelSize(
                    R.dimen.dvr_details_description_under_subtitle_baseline_margin);

            mTitleLineSpacing = view.getResources().getDimensionPixelSize(
                    R.dimen.lb_details_description_title_line_spacing);
            mBodyLineSpacing = view.getResources().getDimensionPixelSize(
                    R.dimen.lb_details_description_body_line_spacing);

            mBodyMaxLines = view.getResources().getInteger(
                    R.integer.lb_details_description_body_max_lines);
            mBodyMinLines = view.getResources().getInteger(
                    R.integer.lb_details_description_body_min_lines);
            mTitleMaxLines = mTitle.getMaxLines();

            mTitleFontMetricsInt = getFontMetricsInt(mTitle);
            mSubtitleFontMetricsInt = getFontMetricsInt(mSubtitle);
            mBodyFontMetricsInt = getFontMetricsInt(mBody);
        }

        void addPreDrawListener() {
            if (!mIsListeningToPreDraw) {
                mIsListeningToPreDraw = true;
                view.getViewTreeObserver().addOnPreDrawListener(mPreDrawListener);
            }
        }

        void removePreDrawListener() {
            if (mIsListeningToPreDraw) {
                view.getViewTreeObserver().removeOnPreDrawListener(mPreDrawListener);
                mIsListeningToPreDraw = false;
            }
        }

        public TextView getTitle() {
            return mTitle;
        }

        public TextView getSubtitle() {
            return mSubtitle;
        }

        public TextView getBody() {
            return mBody;
        }

        private FontMetricsInt getFontMetricsInt(TextView textView) {
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setTextSize(textView.getTextSize());
            paint.setTypeface(textView.getTypeface());
            return paint.getFontMetricsInt();
        }

        private void showFullText(int heightDiff) {
            final ViewGroup detailsFrame = (ViewGroup) mActivity.findViewById(R.id.details_frame);
            int nowHeight = ViewUtils.getLayoutHeight(detailsFrame);
            Animator expandAnimator = ViewUtils.createHeightAnimator(
                    detailsFrame, nowHeight, nowHeight + heightDiff);
            expandAnimator.setDuration(mFullTextAnimationDuration);
            Animator shiftAnimator = ObjectAnimator.ofPropertyValuesHolder(detailsFrame,
                    PropertyValuesHolder.ofFloat(View.TRANSLATION_Y,
                            0f, -(heightDiff / 2)));
            shiftAnimator.setDuration(mFullTextAnimationDuration);
            AnimatorSet fullTextAnimator = new AnimatorSet();
            fullTextAnimator.playTogether(expandAnimator, shiftAnimator);
            fullTextAnimator.start();
        }
    }

    private final Activity mActivity;
    private final int mFullTextAnimationDuration;

    public DetailsContentPresenter(Activity activity) {
        super();
        mActivity = activity;
        mFullTextAnimationDuration = mActivity.getResources()
                .getInteger(R.integer.dvr_details_full_text_animation_duration);
    }

    @Override
    public final ViewHolder onCreateViewHolder(ViewGroup parent) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.dvr_details_description, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public final void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
        final ViewHolder vh = (ViewHolder) viewHolder;
        final DetailsContent detailsContent = (DetailsContent) item;

        vh.mActivity = mActivity;
        vh.mFullTextAnimationDuration = mFullTextAnimationDuration;

        boolean hasTitle = true;
        if (TextUtils.isEmpty(detailsContent.getTitle())) {
            vh.mTitle.setVisibility(View.GONE);
            hasTitle = false;
        } else {
            vh.mTitle.setText(detailsContent.getTitle());
            vh.mTitle.setVisibility(View.VISIBLE);
            vh.mTitle.setLineSpacing(vh.mTitleLineSpacing - vh.mTitle.getLineHeight()
                    + vh.mTitle.getLineSpacingExtra(), vh.mTitle.getLineSpacingMultiplier());
            vh.mTitle.setMaxLines(vh.mTitleMaxLines);
        }
        setTopMargin(vh.mTitle, vh.mTitleMargin);

        boolean hasSubtitle = true;
        if (detailsContent.getStartTimeUtcMillis() != DetailsContent.INVALID_TIME
                && detailsContent.getEndTimeUtcMillis() != DetailsContent.INVALID_TIME) {
            vh.mSubtitle.setText(Utils.getDurationString(viewHolder.view.getContext(),
                    detailsContent.getStartTimeUtcMillis(),
                    detailsContent.getEndTimeUtcMillis(), false));
            vh.mSubtitle.setVisibility(View.VISIBLE);
            if (hasTitle) {
                setTopMargin(vh.mSubtitle, vh.mUnderTitleBaselineMargin
                        + vh.mSubtitleFontMetricsInt.ascent - vh.mTitleFontMetricsInt.descent);
            } else {
                setTopMargin(vh.mSubtitle, 0);
            }
        } else {
            vh.mSubtitle.setVisibility(View.GONE);
            hasSubtitle = false;
        }

        if (TextUtils.isEmpty(detailsContent.getDescription())) {
            vh.mBody.setVisibility(View.GONE);
        } else {
            vh.mBody.setText(detailsContent.getDescription());
            vh.mBody.setVisibility(View.VISIBLE);
            vh.mBody.setLineSpacing(vh.mBodyLineSpacing - vh.mBody.getLineHeight()
                    + vh.mBody.getLineSpacingExtra(), vh.mBody.getLineSpacingMultiplier());
            if (hasSubtitle) {
                setTopMargin(vh.mDescriptionContainer, vh.mUnderSubtitleBaselineMargin
                        + vh.mBodyFontMetricsInt.ascent - vh.mSubtitleFontMetricsInt.descent
                        - vh.mBody.getPaddingTop());
            } else if (hasTitle) {
                setTopMargin(vh.mDescriptionContainer, vh.mUnderTitleBaselineMargin
                        + vh.mBodyFontMetricsInt.ascent - vh.mTitleFontMetricsInt.descent
                        - vh.mBody.getPaddingTop());
            } else {
                setTopMargin(vh.mDescriptionContainer, 0);
            }
        }
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) { }

    private void setTopMargin(View view, int topMargin) {
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        lp.topMargin = topMargin;
        view.setLayoutParams(lp);
    }
}