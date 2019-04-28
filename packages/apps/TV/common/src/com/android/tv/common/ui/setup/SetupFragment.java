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

package com.android.tv.common.ui.setup;

import android.app.Fragment;
import android.os.Bundle;
import android.support.annotation.IntDef;
import android.transition.Transition;
import android.transition.Transition.TransitionListener;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

import com.android.tv.common.ui.setup.animation.FadeAndShortSlide;
import com.android.tv.common.ui.setup.animation.SetupAnimationHelper;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A fragment which slides when it is entering/exiting.
 */
public abstract class SetupFragment extends Fragment {
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true,
            value = {FRAGMENT_ENTER_TRANSITION, FRAGMENT_EXIT_TRANSITION,
                    FRAGMENT_REENTER_TRANSITION, FRAGMENT_RETURN_TRANSITION})
    public @interface FragmentTransitionType {}
    public static final int FRAGMENT_ENTER_TRANSITION = 0x01;
    public static final int FRAGMENT_EXIT_TRANSITION = FRAGMENT_ENTER_TRANSITION << 1;
    public static final int FRAGMENT_REENTER_TRANSITION = FRAGMENT_ENTER_TRANSITION << 2;
    public static final int FRAGMENT_RETURN_TRANSITION = FRAGMENT_ENTER_TRANSITION << 3;

    private boolean mEnterTransitionRunning;

    private final TransitionListener mTransitionListener = new TransitionListener() {
        @Override
        public void onTransitionStart(Transition transition) {
            mEnterTransitionRunning = true;
        }

        @Override
        public void onTransitionEnd(Transition transition) {
            mEnterTransitionRunning = false;
            onEnterTransitionEnd();
        }

        @Override
        public void onTransitionCancel(Transition transition) { }

        @Override
        public void onTransitionPause(Transition transition) { }

        @Override
        public void onTransitionResume(Transition transition) { }
    };

    /**
     * Returns {@code true} if the enter/reenter transition is running.
     */
    protected boolean isEnterTransitionRunning() {
        return mEnterTransitionRunning;
    }

    /**
     * Called when the enter/reenter transition ends.
     */
    protected void onEnterTransitionEnd() { }

    public SetupFragment() {
        setAllowEnterTransitionOverlap(false);
        setAllowReturnTransitionOverlap(false);
        enableFragmentTransition(FRAGMENT_ENTER_TRANSITION | FRAGMENT_EXIT_TRANSITION
                | FRAGMENT_REENTER_TRANSITION | FRAGMENT_RETURN_TRANSITION);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(getLayoutResourceId(), container, false);
        // After the transition animation, we need to request the focus. If not, this fragment
        // doesn't have the focus.
        view.requestFocus();
        return view;
    }

    /**
     * Returns the layout resource ID for this fragment.
     */
    abstract protected int getLayoutResourceId();

    protected void setOnClickAction(View view, final String category, final int actionId) {
        view.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                onActionClick(category, actionId);
            }
        });
    }

    protected boolean onActionClick(String category, int actionId) {
        return SetupActionHelper.onActionClick(this, category, actionId);
    }

    protected boolean onActionClick(String category, int actionId, Bundle params) {
        return SetupActionHelper.onActionClick(this, category, actionId, params);
    }

    @Override
    public void setEnterTransition(Transition transition) {
        super.setEnterTransition(transition);
        if (transition != null) {
            transition.addListener(mTransitionListener);
        }
    }

    @Override
    public void setReenterTransition(Transition transition) {
        super.setReenterTransition(transition);
        if (transition != null) {
            transition.addListener(mTransitionListener);
        }
    }

    /**
     * Enables fragment transition according to the given {@code mask}.
     *
     * @param mask This value is the combination of {@link #FRAGMENT_ENTER_TRANSITION},
     * {@link #FRAGMENT_EXIT_TRANSITION}, {@link #FRAGMENT_REENTER_TRANSITION}, and
     * {@link #FRAGMENT_RETURN_TRANSITION}.
     */
    public void enableFragmentTransition(@FragmentTransitionType int mask) {
        setEnterTransition((mask & FRAGMENT_ENTER_TRANSITION) == 0 ? null
                : createTransition(Gravity.END));
        setExitTransition((mask & FRAGMENT_EXIT_TRANSITION) == 0 ? null
                : createTransition(Gravity.START));
        setReenterTransition((mask & FRAGMENT_REENTER_TRANSITION) == 0 ? null
                : createTransition(Gravity.START));
        setReturnTransition((mask & FRAGMENT_RETURN_TRANSITION) == 0 ? null
                : createTransition(Gravity.END));
    }

    /**
     * Sets the transition with the given {@code slidEdge}.
     */
    public void setFragmentTransition(@FragmentTransitionType int transitionType, int slideEdge) {
        switch (transitionType) {
            case FRAGMENT_ENTER_TRANSITION:
                setEnterTransition(createTransition(slideEdge));
                break;
            case FRAGMENT_EXIT_TRANSITION:
                setExitTransition(createTransition(slideEdge));
                break;
            case FRAGMENT_REENTER_TRANSITION:
                setReenterTransition(createTransition(slideEdge));
                break;
            case FRAGMENT_RETURN_TRANSITION:
                setReturnTransition(createTransition(slideEdge));
                break;
        }
    }

    private Transition createTransition(int slideEdge) {
        return new SetupAnimationHelper.TransitionBuilder()
                .setSlideEdge(slideEdge)
                .setParentIdsForDelay(getParentIdsForDelay())
                .setExcludeIds(getExcludedTargetIds())
                .build();
    }

    /**
     * Changes the move distance of the transitions to short distance.
     */
    public void setShortDistance(@FragmentTransitionType int mask) {
        if ((mask & FRAGMENT_ENTER_TRANSITION) != 0) {
            Transition transition = getEnterTransition();
            if (transition instanceof FadeAndShortSlide) {
                SetupAnimationHelper.setShortDistance((FadeAndShortSlide) transition);
            }
        }
        if ((mask & FRAGMENT_EXIT_TRANSITION) != 0) {
            Transition transition = getExitTransition();
            if (transition instanceof FadeAndShortSlide) {
                SetupAnimationHelper.setShortDistance((FadeAndShortSlide) transition);
            }
        }
        if ((mask & FRAGMENT_REENTER_TRANSITION) != 0) {
            Transition transition = getReenterTransition();
            if (transition instanceof FadeAndShortSlide) {
                SetupAnimationHelper.setShortDistance((FadeAndShortSlide) transition);
            }
        }
        if ((mask & FRAGMENT_RETURN_TRANSITION) != 0) {
            Transition transition = getReturnTransition();
            if (transition instanceof FadeAndShortSlide) {
                SetupAnimationHelper.setShortDistance((FadeAndShortSlide) transition);
            }
        }
    }

    /**
     * Returns the ID's of the view's whose descendants will perform delayed move.
     *
     * @see com.android.tv.common.ui.setup.animation.SetupAnimationHelper.TransitionBuilder
     * #setParentIdsForDelay
     */
    protected int[] getParentIdsForDelay() {
        return null;
    }

    /**
     * Sets the ID's of the views which will not be included in the transition.
     *
     * @see com.android.tv.common.ui.setup.animation.SetupAnimationHelper.TransitionBuilder
     * #setExcludeIds
     */
    protected int[] getExcludedTargetIds() {
        return null;
    }

    /**
     * Returns the ID's of the shared elements.
     *
     * <p>Note that the shared elements should have their own transition names.
     */
    public int[] getSharedElementIds() {
        return null;
    }
}
