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

package com.android.tv.common.ui.setup.animation;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TypeEvaluator;
import android.content.Context;
import android.transition.Transition;
import android.transition.TransitionSet;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;

import com.android.tv.common.R;

/**
 * A helper class for setup animation.
 */
public final class SetupAnimationHelper {
    public static final long DELAY_BETWEEN_SIBLINGS_MS = applyAnimationTimeScale(33);

    private static final float ANIMATION_TIME_SCALE = 1.0f;

    private static boolean sInitialized;
    private static long sFragmentTransitionDuration;
    private static int sFragmentTransitionLongDistance;
    private static int sFragmentTransitionShortDistance;

    private SetupAnimationHelper() { }

    /**
     * Load initial parameters. This method should be called before using this class.
     */
    public static void initialize(Context context) {
        if (sInitialized) {
            return;
        }
        sFragmentTransitionDuration = context.getResources()
                .getInteger(R.integer.setup_fragment_transition_duration);
        sFragmentTransitionLongDistance = context.getResources()
                .getDimensionPixelOffset(R.dimen.setup_fragment_transition_long_distance);
        sFragmentTransitionShortDistance = context.getResources()
                .getDimensionPixelOffset(R.dimen.setup_fragment_transition_short_distance);
        sInitialized = true;
    }

    private static void checkInitialized() {
        if (!sInitialized) {
            throw new IllegalStateException("SetupAnimationHelper not initialized");
        }
    }

    public static class TransitionBuilder {
        private int mSlideEdge = Gravity.START;
        private final int mDistance = sFragmentTransitionLongDistance;
        private long mDuration = sFragmentTransitionDuration;
        private int[] mParentIdForDelay;
        private int[] mExcludeIds;

        public TransitionBuilder() {
            checkInitialized();
        }

        /**
         * Sets the edge of the slide transition.
         *
         * @see android.transition.Slide#setSlideEdge
         */
        public TransitionBuilder setSlideEdge(int slideEdge) {
            mSlideEdge = slideEdge;
            return this;
        }

        /**
         * Sets the duration of the transition.
         */
        public TransitionBuilder setDuration(long duration) {
            mDuration = duration;
            return this;
        }

        /**
         * Sets the ID of the view whose descendants will perform delayed move.
         *
         * @see android.view.ViewGroup#isTransitionGroup
         */
        public TransitionBuilder setParentIdsForDelay(int[] parentIdForDelay) {
            mParentIdForDelay = parentIdForDelay;
            return this;
        }

        /**
         * Sets the ID's of the views which will not be included in the transition.
         */
        public TransitionBuilder setExcludeIds(int[] excludeIds) {
            mExcludeIds = excludeIds;
            return this;
        }

        /**
         * Builds and returns the {@link android.transition.Transition}.
         */
        public Transition build() {
            FadeAndShortSlide transition = new FadeAndShortSlide(mSlideEdge, mParentIdForDelay);
            transition.setDistance(mDistance);
            transition.setDuration(mDuration);
            if (mExcludeIds != null) {
                for (int id : mExcludeIds) {
                    transition.excludeTarget(id, true);
                }
            }
            return transition;
        }
    }

    /**
     * Changes the move distance of the {@code transition} to long distance.
     */
    public static void setLongDistance(FadeAndShortSlide transition) {
        checkInitialized();
        transition.setDistance(sFragmentTransitionLongDistance);
    }

    /**
     * Changes the move distance of the {@code transition} to short distance.
     */
    public static void setShortDistance(FadeAndShortSlide transition) {
        checkInitialized();
        transition.setDistance(sFragmentTransitionShortDistance);
    }

    /**
     * Applies the animation scale to the given {@code animator}.
     */
    public static Animator applyAnimationTimeScale(Animator animator) {
        if (animator instanceof AnimatorSet) {
            for (Animator child : ((AnimatorSet) animator).getChildAnimations()) {
                applyAnimationTimeScale(child);
            }
        }
        if (animator.getDuration() > 0) {
            animator.setDuration((long) (animator.getDuration() * ANIMATION_TIME_SCALE));
        }
        animator.setStartDelay((long) (animator.getStartDelay() * ANIMATION_TIME_SCALE));
        return animator;
    }

    /**
     * Applies the animation scale to the given {@code transition}.
     */
    public static Transition applyAnimationTimeScale(Transition transition) {
        if (transition instanceof TransitionSet) {
            TransitionSet set = (TransitionSet) transition;
            int count = set.getTransitionCount();
            for (int i = 0; i < count; ++i) {
                applyAnimationTimeScale(set.getTransitionAt(i));
            }
        }
        if (transition.getDuration() > 0) {
            transition.setDuration((long) (transition.getDuration() * ANIMATION_TIME_SCALE));
        }
        transition.setStartDelay((long) (transition.getStartDelay() * ANIMATION_TIME_SCALE));
        return transition;
    }

    /**
     * Applies the animation scale to the given {@code time}.
     */
    public static long applyAnimationTimeScale(long time) {
        return (long) (time * ANIMATION_TIME_SCALE);
    }

    /**
     * Returns an animator which animates the source image of the {@link ImageView}.
     *
     * <p>The frame rate is 60 fps.
     */
    public static ObjectAnimator createFrameAnimator(ImageView imageView, int[] frames) {
        return createFrameAnimatorWithDelay(imageView, frames, 0);
    }

    /**
     * Returns an animator which animates the source image of the {@link ImageView} with start delay.
     *
     * <p>The frame rate is 60 fps.
     */
    public static ObjectAnimator createFrameAnimatorWithDelay(ImageView imageView, int[] frames,
            long startDelay) {
        ObjectAnimator animator = ObjectAnimator.ofInt(imageView, "imageResource", frames);
        // Make it 60 fps.
        animator.setDuration(frames.length * 1000 / 60);
        animator.setInterpolator(null);
        animator.setStartDelay(startDelay);
        animator.setEvaluator(new TypeEvaluator<Integer>() {
            @Override
            public Integer evaluate(float fraction, Integer startValue, Integer endValue) {
                return startValue;
            }
        });
        return animator;
    }

    /**
     * Creates a fade out animator.
     *
     * @param view The view which will be animated.
     * @param duration The duration of the animation.
     * @param makeVisibleAfterAnimation If {@code true}, the view will become visible after the
     * animation ends.
     */
    public static Animator createFadeOutAnimator(final View view, long duration,
            boolean makeVisibleAfterAnimation) {
        ObjectAnimator animator =
                ObjectAnimator.ofFloat(view, View.ALPHA, 1.0f, 0.0f).setDuration(duration);
        if (makeVisibleAfterAnimation) {
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    view.setAlpha(1.0f);
                }
            });
        }
        return animator;
    }
}
