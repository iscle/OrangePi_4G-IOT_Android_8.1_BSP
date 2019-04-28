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
import android.animation.TimeInterpolator;
import android.transition.Fade;
import android.transition.Transition;
import android.transition.TransitionValues;
import android.transition.Visibility;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Execute horizontal slide of 1/4 width and fade (to workaround bug 23718734)
 */
public class FadeAndShortSlide extends Visibility {
    private static final TimeInterpolator APPEAR_INTERPOLATOR = new DecelerateInterpolator();
    private static final TimeInterpolator DISAPPEAR_INTERPOLATOR = new AccelerateInterpolator();

    private static final String PROPNAME_SCREEN_POSITION =
            "android_fadeAndShortSlideTransition_screenPosition";
    private static final String PROPNAME_DELAY = "propname_delay";

    private static final int DEFAULT_DISTANCE = 200;

    private static abstract class CalculateSlide {
        /** Returns the translation value for view when it goes out of the scene */
        public abstract float getGoneX(ViewGroup sceneRoot, View view, int[] position,
                int distance);
    }

    private static final CalculateSlide sCalculateStart = new CalculateSlide() {
        @Override
        public float getGoneX(ViewGroup sceneRoot, View view, int[] position, int distance) {
            final boolean isRtl = sceneRoot.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            final float x;
            if (isRtl) {
                x = view.getTranslationX() + distance;
            } else {
                x = view.getTranslationX() - distance;
            }
            return x;
        }
    };

    private static final CalculateSlide sCalculateEnd = new CalculateSlide() {
        @Override
        public float getGoneX(ViewGroup sceneRoot, View view, int[] position, int distance) {
            final boolean isRtl = sceneRoot.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            final float x;
            if (isRtl) {
                x = view.getTranslationX() - distance;
            } else {
                x = view.getTranslationX() + distance;
            }
            return x;
        }
    };

    private static final ViewPositionComparator sViewPositionComparator =
            new ViewPositionComparator();

    private int mSlideEdge;
    private CalculateSlide mSlideCalculator = sCalculateEnd;
    private Visibility mFade = new Fade();

    // TODO: Consider using TransitionPropagation.
    private final int[] mParentIdsForDelay;
    private int mDistance = DEFAULT_DISTANCE;

    public FadeAndShortSlide() {
        this(Gravity.START);
    }

    public FadeAndShortSlide(int slideEdge) {
        this(slideEdge, null);
    }

    public FadeAndShortSlide(int slideEdge, int[] parentIdsForDelay) {
        setSlideEdge(slideEdge);
        mParentIdsForDelay = parentIdsForDelay;
    }

    @Override
    public void setEpicenterCallback(EpicenterCallback epicenterCallback) {
        super.setEpicenterCallback(epicenterCallback);
        mFade.setEpicenterCallback(epicenterCallback);
    }

    private void captureValues(TransitionValues transitionValues) {
        View view = transitionValues.view;
        int[] position = new int[2];
        view.getLocationOnScreen(position);
        transitionValues.values.put(PROPNAME_SCREEN_POSITION, position);
    }

    private int getDelayOrder(View view, boolean appear) {
        if (mParentIdsForDelay == null) {
            return -1;
        }
        final View parentForDelay = findParentForDelay(view);
        if (parentForDelay == null || !(parentForDelay instanceof ViewGroup)) {
            return -1;
        }
        List<View> transitionTargets = new ArrayList<>();
        getTransitionTargets((ViewGroup) parentForDelay, transitionTargets);
        sViewPositionComparator.mParentForDelay = parentForDelay;
        sViewPositionComparator.mIsLtr = view.getLayoutDirection() == View.LAYOUT_DIRECTION_LTR;
        sViewPositionComparator.mToLeft = sViewPositionComparator.mIsLtr
                ? mSlideEdge == (appear ? Gravity.END : Gravity.START)
                : mSlideEdge == (appear ? Gravity.START : Gravity.END);
        Collections.sort(transitionTargets, sViewPositionComparator);
        return transitionTargets.indexOf(view);
    }

    private View findParentForDelay(View view) {
        if (isParentForDelay(view.getId())) {
            return view;
        }
        View parent = view;
        while (parent.getParent() instanceof View) {
            parent = (View) parent.getParent();
            if (isParentForDelay(parent.getId())) {
                return parent;
            }
        }
        return null;
    }

    private boolean isParentForDelay(int viewId) {
        for (int id : mParentIdsForDelay) {
            if (id == viewId) {
                return true;
            }
        }
        return false;
    }

    private void getTransitionTargets(ViewGroup parent, List<View> transitionTargets) {
        int count = parent.getChildCount();
        for (int i = 0; i < count; ++i) {
            View child = parent.getChildAt(i);
            if (child instanceof ViewGroup && !((ViewGroup) child).isTransitionGroup()) {
                getTransitionTargets((ViewGroup) child, transitionTargets);
            } else {
                transitionTargets.add(child);
            }
        }
    }

    @Override
    public void captureStartValues(TransitionValues transitionValues) {
        super.captureStartValues(transitionValues);
        mFade.captureStartValues(transitionValues);
        captureValues(transitionValues);
        int delayIndex = getDelayOrder(transitionValues.view, false);
        if (delayIndex > 0) {
            transitionValues.values.put(PROPNAME_DELAY,
                    delayIndex * SetupAnimationHelper.DELAY_BETWEEN_SIBLINGS_MS);
        }
    }

    @Override
    public void captureEndValues(TransitionValues transitionValues) {
        super.captureEndValues(transitionValues);
        mFade.captureEndValues(transitionValues);
        captureValues(transitionValues);
        int delayIndex = getDelayOrder(transitionValues.view, true);
        if (delayIndex > 0) {
            transitionValues.values.put(PROPNAME_DELAY,
                    delayIndex * SetupAnimationHelper.DELAY_BETWEEN_SIBLINGS_MS);
        }
    }

    public void setSlideEdge(int slideEdge) {
        mSlideEdge = slideEdge;
        switch (slideEdge) {
            case Gravity.START:
                mSlideCalculator = sCalculateStart;
                break;
            case Gravity.END:
                mSlideCalculator = sCalculateEnd;
                break;
            default:
                throw new IllegalArgumentException("Invalid slide direction");
        }
    }

    @Override
    public Animator onAppear(ViewGroup sceneRoot, View view, TransitionValues startValues,
            TransitionValues endValues) {
        if (endValues == null) {
            return null;
        }
        int[] position = (int[]) endValues.values.get(PROPNAME_SCREEN_POSITION);
        int left = position[0];
        float endX = view.getTranslationX();
        float startX = mSlideCalculator.getGoneX(sceneRoot, view, position, mDistance);
        final Animator slideAnimator = TranslationAnimationCreator.createAnimation(view, endValues,
                left, startX, endX, APPEAR_INTERPOLATOR, this);
        if (slideAnimator == null) {
            return null;
        }
        mFade.setInterpolator(APPEAR_INTERPOLATOR);
        final AnimatorSet set = new AnimatorSet();
        set.play(slideAnimator).with(mFade.onAppear(sceneRoot, view, startValues, endValues));
        Long delay = (Long ) endValues.values.get(PROPNAME_DELAY);
        if (delay != null) {
            set.setStartDelay(delay);
        }
        return set;
    }

    @Override
    public Animator onDisappear(ViewGroup sceneRoot, final View view, TransitionValues startValues,
            TransitionValues endValues) {
        if (startValues == null) {
            return null;
        }
        int[] position = (int[]) startValues.values.get(PROPNAME_SCREEN_POSITION);
        int left = position[0];
        float startX = view.getTranslationX();
        float endX = mSlideCalculator.getGoneX(sceneRoot, view, position, mDistance);
        final Animator slideAnimator = TranslationAnimationCreator.createAnimation(view,
                startValues, left, startX, endX, DISAPPEAR_INTERPOLATOR, this);
        if (slideAnimator == null) { // slideAnimator is null if startX == endX
            return null;
        }

        mFade.setInterpolator(DISAPPEAR_INTERPOLATOR);
        final Animator fadeAnimator = mFade.onDisappear(sceneRoot, view, startValues, endValues);
        if (fadeAnimator == null) {
            return null;
        }
        fadeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                fadeAnimator.removeListener(this);
                view.setAlpha(0.0f);
            }
        });

        final AnimatorSet set = new AnimatorSet();
        set.play(slideAnimator).with(fadeAnimator);
        Long delay = (Long) startValues.values.get(PROPNAME_DELAY);
        if (delay != null) {
            set.setStartDelay(delay);
        }
        return set;
    }

    @Override
    public Transition addListener(TransitionListener listener) {
        mFade.addListener(listener);
        return super.addListener(listener);
    }

    @Override
    public Transition removeListener(TransitionListener listener) {
        mFade.removeListener(listener);
        return super.removeListener(listener);
    }

    @Override
    public Transition clone() {
        FadeAndShortSlide clone = (FadeAndShortSlide) super.clone();
        clone.mFade = (Visibility) mFade.clone();
        return clone;
    }

    @Override
    public Transition setDuration(long duration) {
        long scaledDuration = SetupAnimationHelper.applyAnimationTimeScale(duration);
        mFade.setDuration(scaledDuration);
        return super.setDuration(scaledDuration);
    }

    /**
     * Sets the moving distance in pixel.
     */
    public void setDistance(int distance) {
        mDistance = distance;
    }

    private static class ViewPositionComparator implements Comparator<View> {
        View mParentForDelay;
        boolean mIsLtr;
        boolean mToLeft;

        @Override
        public int compare(View lhs, View rhs) {
            int start1;
            int start2;
            if (mIsLtr) {
                start1 = getRelativeLeft(lhs, mParentForDelay);
                start2 = getRelativeLeft(rhs, mParentForDelay);
            } else {
                start1 = getRelativeRight(lhs, mParentForDelay);
                start2 = getRelativeRight(rhs, mParentForDelay);
            }
            if (mToLeft) {
                if (start1 > start2) {
                    return 1;
                } else if (start1 < start2) {
                    return -1;
                }
            } else {
                if (start1 > start2) {
                    return -1;
                } else if (start1 < start2) {
                    return 1;
                }
            }
            int top1 = getRelativeTop(lhs, mParentForDelay);
            int top2 = getRelativeTop(rhs, mParentForDelay);
            return Integer.compare(top1, top2);
        }

        private int getRelativeLeft(View child, View ancestor) {
            ViewParent parent = child.getParent();
            int left = child.getLeft();
            while (parent instanceof View) {
                if (parent == ancestor) {
                    break;
                }
                left += ((View) parent).getLeft();
                parent = parent.getParent();
            }
            return left;
        }

        private int getRelativeRight(View child, View ancestor) {
            ViewParent parent = child.getParent();
            int right = child.getRight();
            while (parent instanceof View) {
                if (parent == ancestor) {
                    break;
                }
                right += ((View) parent).getLeft();
                parent = parent.getParent();
            }
            return right;
        }

        private int getRelativeTop(View child, View ancestor) {
            ViewParent parent = child.getParent();
            int top = child.getTop();
            while (parent instanceof View) {
                if (parent == ancestor) {
                    break;
                }
                top += ((View) parent).getTop();
                parent = parent.getParent();
            }
            return top;
        }
    }
}
