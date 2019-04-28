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

package com.android.tv.settings.dialog.old;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.support.v4.view.ViewCompat;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.tv.settings.R;
import com.android.tv.settings.widget.FrameLayoutWithShadows;

/**
 * This class exists to make extending both v4 DialogFragments and regular DialogFragments easy
 */
public class BaseDialogFragment {

    private static final int ANIMATE_IN_DURATION = 250;
    private static final int ANIMATE_DELAY = 550;
    private static final int SLIDE_IN_DISTANCE = 120;

    public static final String TAG_CONTENT = "content";
    public static final String TAG_ACTION = "action";

    public int mContentAreaId = R.id.content_fragment;
    public int mActionAreaId = R.id.action_fragment;

    private FrameLayoutWithShadows mShadowLayer;
    public boolean mFirstOnStart = true;
    private boolean mIntroAnimationInProgress = false;

    private final LiteFragment mFragment;

    // Related to activity entry transition
    private ColorDrawable mBgDrawable = new ColorDrawable();

    public BaseDialogFragment(LiteFragment fragment) {
        mFragment = fragment;
    }

    public void onActionClicked(Activity activity, Action action) {
        if (activity instanceof ActionAdapter.Listener) {
            ((ActionAdapter.Listener) activity).onActionClicked(action);
        } else {
            Intent intent = action.getIntent();
            if (intent != null) {
                activity.startActivity(intent);
                activity.finish();
            }
        }
    }

    public void disableEntryAnimation() {
        mFirstOnStart = false;
    }

    /**
     * This method sets the layout property of this class. <br/>
     * Activities extending {@link DialogFragment} should call this method
     * before calling {@link #onCreate(Bundle)} if they want to have a
     * custom view.
     *
     * @param contentAreaId id of the content area
     * @param actionAreaId id of the action area
     */
    public void setLayoutProperties(int contentAreaId, int actionAreaId) {
        mContentAreaId = contentAreaId;
        mActionAreaId = actionAreaId;
    }

    public void performEntryTransition(final Activity activity, final ViewGroup contentView,
            final ImageView icon, final TextView title,
            final TextView description, final TextView breadcrumb) {
        // Pull out the root layout of the dialog and set the background drawable, to be
        // faded in during the transition.
        final ViewGroup twoPane = (ViewGroup) contentView.getChildAt(0);
        twoPane.setVisibility(View.INVISIBLE);

        // If the appropriate data is embedded in the intent and there is an icon specified
        // in the content fragment, we animate the icon from its initial position to the final
        // position. Otherwise, we perform a simpler transition in which the ActionFragment
        // slides in and the ContentFragment text fields slide in.
        mIntroAnimationInProgress = true;

        // Fade out the old activity, and hard cut the new activity.
        activity.overridePendingTransition(R.anim.hard_cut_in, R.anim.fade_out);

        int bgColor = mFragment.getActivity().getColor(R.color.dialog_activity_background);
        mBgDrawable.setColor(bgColor);
        mBgDrawable.setAlpha(0);
        twoPane.setBackground(mBgDrawable);

        // If we're animating the icon, we create a new ImageView in which to place the embedded
        // bitmap. We place it in the root layout to match its location in the previous activity.
        mShadowLayer = (FrameLayoutWithShadows) twoPane.findViewById(R.id.shadow_layout);

        // We need to defer the remainder of the animation preparation until the first
        // layout has occurred, as we don't yet know the final location of the icon.
        twoPane.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                twoPane.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                // if we buildLayer() at this time,  the texture is actually not created
                // delay a little so we can make sure all hardware layer is created before
                // animation, in that way we can avoid the jittering of start animation
                twoPane.postOnAnimationDelayed(mEntryAnimationRunnable, ANIMATE_DELAY);
            }

            final Runnable mEntryAnimationRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!mFragment.isAdded()) {
                        // We have been detached before this could run, so just bail
                        return;
                    }

                    twoPane.setVisibility(View.VISIBLE);
                    final int secondaryDelay = SLIDE_IN_DISTANCE;

                    // Fade in the activity background protection
                    ObjectAnimator oa = ObjectAnimator.ofInt(mBgDrawable, "alpha", 255);
                    oa.setDuration(ANIMATE_IN_DURATION);
                    oa.setStartDelay(secondaryDelay);
                    oa.setInterpolator(new DecelerateInterpolator(1.0f));
                    oa.start();

                    View actionFragmentView = activity.findViewById(mActionAreaId);
                    boolean isRtl = ViewCompat.getLayoutDirection(contentView) ==
                            ViewCompat.LAYOUT_DIRECTION_RTL;
                    int startDist = isRtl ? SLIDE_IN_DISTANCE : -SLIDE_IN_DISTANCE;
                    int endDist = isRtl ? -actionFragmentView.getMeasuredWidth() :
                            actionFragmentView.getMeasuredWidth();

                    // Fade in and slide in the ContentFragment TextViews from the start.
                    prepareAndAnimateView(title, 0, startDist,
                            secondaryDelay, ANIMATE_IN_DURATION,
                            new DecelerateInterpolator(1.0f),
                            false);
                    prepareAndAnimateView(breadcrumb, 0, startDist,
                            secondaryDelay, ANIMATE_IN_DURATION,
                            new DecelerateInterpolator(1.0f),
                            false);
                    prepareAndAnimateView(description, 0,
                            startDist,
                            secondaryDelay, ANIMATE_IN_DURATION,
                            new DecelerateInterpolator(1.0f),
                            false);

                    // Fade in and slide in the ActionFragment from the end.
                    prepareAndAnimateView(actionFragmentView, 0,
                            endDist, secondaryDelay,
                            ANIMATE_IN_DURATION, new DecelerateInterpolator(1.0f),
                            false);

                    if (icon != null) {
                        prepareAndAnimateView(icon, 0, startDist,
                                secondaryDelay, ANIMATE_IN_DURATION,
                                new DecelerateInterpolator(1.0f), true /* is the icon */);
                        if (mShadowLayer != null) {
                            mShadowLayer.setShadowsAlpha(0f);
                        }
                    }
                }
            };
        });
    }

    /**
     * Animates a view.
     *
     * @param v              view to animate
     * @param initAlpha      initial alpha
     * @param initTransX     initial translation in the X
     * @param delay          delay in ms
     * @param duration       duration in ms
     * @param interpolator   interpolator to be used, can be null
     * @param isIcon         if {@code true}, this is the main icon being moved
     */
    public void prepareAndAnimateView(final View v, float initAlpha, float initTransX, int delay,
            int duration, Interpolator interpolator, final boolean isIcon) {
        if (v != null && v.getWindowToken() != null) {
            v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            v.buildLayer();
            v.setAlpha(initAlpha);
            v.setTranslationX(initTransX);
            v.animate().alpha(1f).translationX(0).setDuration(duration).setStartDelay(delay);
            if (interpolator != null) {
                v.animate().setInterpolator(interpolator);
            }
            v.animate().setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    v.setLayerType(View.LAYER_TYPE_NONE, null);
                    if (isIcon) {
                        if (mShadowLayer != null) {
                            mShadowLayer.setShadowsAlpha(1f);
                        }
                        onIntroAnimationFinished();
                    }
                }
            });
            v.animate().start();
        }
    }

    /**
     * Called when intro animation is finished.
     * <p>
     * If a subclass is going to alter the view, should wait until this is called.
     */
    public void onIntroAnimationFinished() {
        mIntroAnimationInProgress = false;
    }

    public boolean isIntroAnimationInProgress() {
        return mIntroAnimationInProgress;
    }

    public ColorDrawable getBackgroundDrawable() {
        return mBgDrawable;
    }

    public void setBackgroundDrawable(ColorDrawable drawable) {
        mBgDrawable = drawable;
    }

}
