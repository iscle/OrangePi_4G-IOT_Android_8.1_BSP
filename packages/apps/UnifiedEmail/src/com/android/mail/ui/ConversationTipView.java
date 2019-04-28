/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.mail.ui;

import android.animation.ObjectAnimator;
import android.app.LoaderManager;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.browse.ConversationCursor;
import com.android.mail.providers.Folder;
import com.android.mail.utils.LogTag;

/**
 * Base class to display tip teasers in the thread list.
 * Supports two-line text and start/end icons.
 */
public abstract class ConversationTipView extends LinearLayout
        implements ConversationSpecialItemView, SwipeableItemView, View.OnClickListener {
    protected static final String LOG_TAG = LogTag.getLogTag();

    protected Context mContext;
    protected AnimatedAdapter mAdapter;

    private int mScrollSlop;
    private int mShrinkAnimationDuration;
    private int mAnimatedHeight = -1;

    protected View mSwipeableContent;
    private View mContent;
    private TextView mText;

    public ConversationTipView(Context context) {
        this(context, null);
    }

    public ConversationTipView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;

        final Resources resources = context.getResources();
        mScrollSlop = resources.getInteger(R.integer.swipeScrollSlop);
        mShrinkAnimationDuration = resources.getInteger(
                R.integer.shrink_animation_duration);

        // Inflate the actual content and add it to this view
        mContent = LayoutInflater.from(mContext).inflate(getChildLayout(), this, false);
        addView(mContent);
        setupViews();
    }

    @Override
    public ViewGroup.LayoutParams getLayoutParams() {
        ViewGroup.LayoutParams params = super.getLayoutParams();
        if (params != null) {
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        return params;
    }

    protected @LayoutRes int getChildLayout() {
        // Should override setupViews as well if this is overridden.
        return R.layout.conversation_tip_view;
    }

    protected void setupViews() {
        // If this is overridden, child classes cannot rely on setText/getStartIconAttr/etc.
        mSwipeableContent = mContent.findViewById(R.id.conversation_tip_swipeable_content);
        mText = (TextView) mContent.findViewById(R.id.conversation_tip_text);
        final ImageView startImage = (ImageView) mContent.findViewById(R.id.conversation_tip_icon1);
        final ImageView dismiss = (ImageView) mContent.findViewById(R.id.dismiss_icon);

        // Bind content (text content must be bound by calling setText(..))
        bindIcon(startImage, getStartIconAttr());

        // Bind listeners
        dismiss.setOnClickListener(this);
        mText.setOnClickListener(getTextAreaOnClickListener());
    }

    /**
     * Helper function to bind the additional attributes to the icon, or make the icon GONE.
     */
    private void bindIcon(ImageView image, ImageAttrSet attr) {
        if (attr != null) {
            image.setVisibility(VISIBLE);
            image.setContentDescription(attr.contentDescription);
            // Must override resId for the actual icon, so no need to check -1 here.
            image.setImageResource(attr.resId);
            if (attr.background != -1) {
                image.setBackgroundResource(attr.background);
            }
        } else {
            image.setVisibility(GONE);
        }
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        if (mAnimatedHeight == -1) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        } else {
            setMeasuredDimension(View.MeasureSpec.getSize(widthMeasureSpec), mAnimatedHeight);
        }
    }

    protected ImageAttrSet getStartIconAttr() {
        return null;
    }

    protected void setText(CharSequence text) {
        mText.setText(text);
    }

    protected OnClickListener getTextAreaOnClickListener() {
        return null;
    }

    @Override
    public void onClick(View view) {
        // Default on click for the default dismiss button
        dismiss();
    }

    @Override
    public void onUpdate(Folder folder, ConversationCursor cursor) {
        // Do nothing by default
    }

    @Override
    public void onGetView() {
        // Do nothing by default
    }

    @Override
    public int getPosition() {
        // By default the tip teasers go on top of the list.
        return 0;
    }

    @Override
    public void setAdapter(AnimatedAdapter adapter) {
        mAdapter = adapter;
    }

    @Override
    public void bindFragment(LoaderManager loaderManager, Bundle savedInstanceState) {
        // Do nothing by default
    }

    @Override
    public void cleanup() {
        // Do nothing by default
    }

    @Override
    public void onConversationSelected() {
        // Do nothing by default
    }

    @Override
    public void onCabModeEntered() {
        // Do nothing by default
    }

    @Override
    public void onCabModeExited() {
        // Do nothing by default
    }

    @Override
    public boolean acceptsUserTaps() {
        return true;
    }

    @Override
    public void onConversationListVisibilityChanged(boolean visible) {
        // Do nothing by default
    }

    @Override
    public void saveInstanceState(Bundle outState) {
        // Do nothing by default
    }

    @Override
    public boolean commitLeaveBehindItem() {
        // Tip has no leave-behind by default
        return false;
    }

    @Override
    public SwipeableView getSwipeableView() {
        return SwipeableView.from(mSwipeableContent);
    }

    @Override
    public boolean canChildBeDismissed() {
        return true;
    }

    @Override
    public void dismiss() {
        startDestroyAnimation();
    }

    @Override
    public float getMinAllowScrollDistance() {
        return mScrollSlop;
    }

    private void startDestroyAnimation() {
        final int start = getHeight();
        final int end = 0;
        mAnimatedHeight = start;
        final ObjectAnimator heightAnimator =
                ObjectAnimator.ofInt(this, "animatedHeight", start, end);
        heightAnimator.setInterpolator(new DecelerateInterpolator(2.0f));
        heightAnimator.setDuration(mShrinkAnimationDuration);
        heightAnimator.start();

        /*
         * Ideally, we would like to call mAdapter.notifyDataSetChanged() in a listener's
         * onAnimationEnd(), but we are in the middle of a touch event, and this will cause all the
         * views to get recycled, which will cause problems.
         *
         * Instead, we'll just leave the item in the list with a height of 0, and the next
         * notifyDatasetChanged() will remove it from the adapter.
         */
    }

    /**
     * This method is used by the animator.  It is explicitly kept in proguard.flags to prevent it
     * from being removed, inlined, or obfuscated.
     * Edit ./vendor/unbundled/packages/apps/UnifiedGmail/proguard.flags
     * In the future, we want to use @Keep
     */
    public void setAnimatedHeight(final int height) {
        mAnimatedHeight = height;
        requestLayout();
    }

    public static class ImageAttrSet {
        // -1 for these resIds to not override the default value.
        public int resId;
        public int background;
        public String contentDescription;

        public ImageAttrSet(int resId, int background, String contentDescription) {
            this.resId = resId;
            this.background = background;
            this.contentDescription = contentDescription;
        }
    }
}
