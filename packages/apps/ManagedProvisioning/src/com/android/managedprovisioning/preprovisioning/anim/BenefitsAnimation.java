/*
 * Copyright 2016, The Android Open Source Project
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
package com.android.managedprovisioning.preprovisioning.anim;

import static com.android.internal.util.Preconditions.checkNotNull;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.graphics.drawable.Animatable2;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.managedprovisioning.R;

import java.util.List;

/**
 * <p>Drives the animation showing benefits of having a Managed Profile.
 * <p>Tightly coupled with the {@link R.layout#intro_animation} layout.
 */
public class BenefitsAnimation {
    /** Array of Id pairs: {{@link ObjectAnimator}, {@link TextView}} */
    private static final int[][] ID_ANIMATION_TARGET = {
            {R.anim.text_scene_0_animation, R.id.text_0},
            {R.anim.text_scene_1_animation, R.id.text_1},
            {R.anim.text_scene_2_animation, R.id.text_2},
            {R.anim.text_scene_3_animation, R.id.text_3},
            {R.anim.text_scene_master_animation, R.id.text_master}};

    private static final int[] SLIDE_CAPTION_TEXT_VIEWS = {
            R.id.text_0, R.id.text_1, R.id.text_2, R.id.text_3};

    /** Id of an {@link ImageView} containing the animated graphic */
    private static final int ID_ANIMATED_GRAPHIC = R.id.animated_info;

    /** Id of an {@link ImageView} containing the animated pager dots */
    private static final int ID_ANIMATED_DOTS = R.id.animated_dots;

    private static final int SLIDE_COUNT = 3;
    private static final int ANIMATION_ORIGINAL_WIDTH_PX = 1080;

    private final AnimatedVectorDrawable mTopAnimation;
    private final AnimatedVectorDrawable mDotsAnimation;
    private final Animator mTextAnimation;
    private final Activity mActivity;

    private boolean mStopped;

    /**
     * @param captions slide captions for the animation
     * @param contentDescription for accessibility
     */
    public BenefitsAnimation(@NonNull Activity activity, @NonNull List<Integer> captions,
            int contentDescription) {
        if (captions.size() != SLIDE_COUNT) {
            throw new IllegalArgumentException(
                    "Wrong number of slide captions. Expected: " + SLIDE_COUNT);
        }
        mActivity = checkNotNull(activity);
        mTextAnimation = checkNotNull(assembleTextAnimation());
        applySlideCaptions(captions);
        applyContentDescription(contentDescription);
        mDotsAnimation = checkNotNull(extractAnimationFromImageView(ID_ANIMATED_DOTS));
        mTopAnimation = checkNotNull(extractAnimationFromImageView(ID_ANIMATED_GRAPHIC));

        // chain all animations together
        chainAnimations();

        // once the screen is ready, adjust size
        mActivity.findViewById(android.R.id.content).post(this::adjustToScreenSize);
    }

    /** Starts playing the animation in a loop. */
    public void start() {
        mStopped = false;
        mTopAnimation.start();
    }

    /** Stops the animation. */
    public void stop() {
        mStopped = true;
        mTopAnimation.stop();
    }

    /**
     * Adjust animation and text to match actual screen size
     */
    private void adjustToScreenSize() {
        if (mActivity.isDestroyed()) {
            return;
        }

        ImageView animatedInfo = mActivity.findViewById(R.id.animated_info);
        int widthPx = animatedInfo.getWidth();
        float scaleRatio = (float) widthPx / ANIMATION_ORIGINAL_WIDTH_PX;

        // adjust animation height; width happens automatically
        LayoutParams layoutParams = animatedInfo.getLayoutParams();
        int originalHeight = animatedInfo.getHeight();
        int adjustedHeight = (int) (originalHeight * scaleRatio);
        layoutParams.height = adjustedHeight;
        animatedInfo.setLayoutParams(layoutParams);

        // adjust captions size only if downscaling
        if (scaleRatio < 1) {
            for (int textViewId : SLIDE_CAPTION_TEXT_VIEWS) {
                View view = mActivity.findViewById(textViewId);
                view.setScaleX(scaleRatio);
                view.setScaleY(scaleRatio);
            }
        }

        // if the content is bigger than the screen, try to shrink just the animation
        int offset = adjustedHeight - originalHeight;
        int contentHeight = mActivity.findViewById(R.id.intro_po_content).getHeight() + offset;
        int viewportHeight = mActivity.findViewById(R.id.suw_layout_content).getHeight();
        if (contentHeight > viewportHeight) {
            int targetHeight = layoutParams.height - (contentHeight - viewportHeight);
            int minHeight = mActivity.getResources().getDimensionPixelSize(
                    R.dimen.intro_animation_min_height);

            // if the animation becomes too small, leave it as is and the scrollbar will show
            if (targetHeight >= minHeight) {
                layoutParams.height = targetHeight;
                animatedInfo.setLayoutParams(layoutParams);
            }
        }
    }

    /**
     * <p>Chains all three sub-animations, and configures them to play in sync in a loop.
     * <p>Looping {@link AnimatedVectorDrawable} and {@link AnimatorSet} currently not possible in
     * XML.
     */
    private void chainAnimations() {
        mTopAnimation.registerAnimationCallback(new Animatable2.AnimationCallback() {
            @Override
            public void onAnimationStart(Drawable drawable) {
                super.onAnimationStart(drawable);

                // starting the other animations at the same time
                mDotsAnimation.start();
                mTextAnimation.start();
            }

            @Override
            public void onAnimationEnd(Drawable drawable) {
                super.onAnimationEnd(drawable);

                // without explicitly stopping them, sometimes they won't restart
                mDotsAnimation.stop();
                mTextAnimation.cancel();

                // repeating the animation in loop
                if (!mStopped) {
                    mTopAnimation.start();
                }
            }
        });
    }

    /**
     * <p>Inflates animators required to animate text headers' part of the whole animation.
     * <p>This has to be done through code, as setting a target on {@link
     * android.animation.ObjectAnimator} is not currently possible in XML.
     *
     * @return {@link AnimatorSet} responsible for the animated text
     */
    private AnimatorSet assembleTextAnimation() {
        Animator[] animators = new Animator[ID_ANIMATION_TARGET.length];
        for (int i = 0; i < ID_ANIMATION_TARGET.length; i++) {
            int[] instance = ID_ANIMATION_TARGET[i];
            animators[i] = AnimatorInflater.loadAnimator(mActivity, instance[0]);
            animators[i].setTarget(mActivity.findViewById(instance[1]));
        }

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(animators);
        return animatorSet;
    }

    /**
     * @param captions slide titles
     */
    private void applySlideCaptions(List<Integer> captions) {
        int slideIx = 0;
        for (int viewId : SLIDE_CAPTION_TEXT_VIEWS) {
            ((TextView) mActivity.findViewById(viewId)).setText(
                    captions.get(slideIx++ % captions.size()));
        }
    }

    private void applyContentDescription(int contentDescription) {
        mActivity.findViewById(R.id.animation_top_level_frame).setContentDescription(
                mActivity.getString(contentDescription));
    }

    /** Extracts an {@link AnimatedVectorDrawable} from a containing {@link ImageView}. */
    private AnimatedVectorDrawable extractAnimationFromImageView(int id) {
        ImageView imageView = mActivity.findViewById(id);
        return (AnimatedVectorDrawable) imageView.getDrawable();
    }
}