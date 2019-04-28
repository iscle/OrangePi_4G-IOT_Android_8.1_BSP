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

package com.android.tv.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.TypeEvaluator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.Property;
import android.view.Display;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;

import com.android.tv.Features;
import com.android.tv.R;
import com.android.tv.TvOptionsManager;
import com.android.tv.data.DisplayMode;
import com.android.tv.util.TvSettings;

/**
 * The TvViewUiManager is responsible for handling UI layouting and animation of main TvView.
 * It also control the settings regarding TvView UI such as display mode.
 */
public class TvViewUiManager {
    private static final String TAG = "TvViewManager";
    private static final boolean DEBUG = false;

    private static final float DISPLAY_MODE_EPSILON = 0.001f;
    private static final float DISPLAY_ASPECT_RATIO_EPSILON = 0.01f;

    private static final int MSG_SET_LAYOUT_PARAMS = 1000;

    private final Context mContext;
    private final Resources mResources;
    private final FrameLayout mContentView;
    private final TunableTvView mTvView;
    private final TvOptionsManager mTvOptionsManager;
    private final int mTvViewShrunkenStartMargin;
    private final int mTvViewShrunkenEndMargin;
    private int mWindowWidth;
    private int mWindowHeight;
    private final SharedPreferences mSharedPreferences;
    private final TimeInterpolator mLinearOutSlowIn;
    private final TimeInterpolator mFastOutLinearIn;
    private final Handler mHandler =
            new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    switch (msg.what) {
                        case MSG_SET_LAYOUT_PARAMS:
                            FrameLayout.LayoutParams layoutParams =
                                    (FrameLayout.LayoutParams) msg.obj;
                            if (DEBUG) {
                                Log.d(
                                        TAG,
                                        "setFixedSize: w="
                                                + layoutParams.width
                                                + " h="
                                                + layoutParams.height);
                            }
                            mTvView.setTvViewLayoutParams(layoutParams);
                            mTvView.setLayoutParams(mTvViewFrame);
                            // Smooth PIP size change, we don't change surface size when
                            // isInPictureInPictureMode is true.
                            if (!Features.PICTURE_IN_PICTURE.isEnabled(mContext)
                                    || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                                            && !((Activity) mContext).isInPictureInPictureMode())) {
                                mTvView.setFixedSurfaceSize(
                                        layoutParams.width, layoutParams.height);
                            }
                            break;
                    }
                }
            };
    private int mDisplayMode;
    // Used to restore the previous state from ShrunkenTvView state.
    private int mTvViewStartMarginBeforeShrunken;
    private int mTvViewEndMarginBeforeShrunken;
    private int mDisplayModeBeforeShrunken;
    private boolean mIsUnderShrunkenTvView;
    private int mTvViewStartMargin;
    private int mTvViewEndMargin;
    private ObjectAnimator mTvViewAnimator;
    private FrameLayout.LayoutParams mTvViewLayoutParams;
    // TV view's position when the display mode is FULL. It is used to compute PIP location relative
    // to TV view's position.
    private FrameLayout.LayoutParams mTvViewFrame;
    private FrameLayout.LayoutParams mLastAnimatedTvViewFrame;
    private FrameLayout.LayoutParams mOldTvViewFrame;
    private ObjectAnimator mBackgroundAnimator;
    private int mBackgroundColor;
    private int mAppliedDisplayedMode = DisplayMode.MODE_NOT_DEFINED;
    private int mAppliedTvViewStartMargin;
    private int mAppliedTvViewEndMargin;
    private float mAppliedVideoDisplayAspectRatio;

    public TvViewUiManager(Context context, TunableTvView tvView,
            FrameLayout contentView, TvOptionsManager tvOptionManager) {
        mContext = context;
        mResources = mContext.getResources();
        mTvView = tvView;
        mContentView = contentView;
        mTvOptionsManager = tvOptionManager;

        DisplayManager displayManager = (DisplayManager) mContext
                .getSystemService(Context.DISPLAY_SERVICE);
        Display display = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        Point size = new Point();
        display.getSize(size);
        mWindowWidth = size.x;
        mWindowHeight = size.y;

        // Have an assumption that TvView Shrinking happens only in full screen.
        mTvViewShrunkenStartMargin = mResources
                .getDimensionPixelOffset(R.dimen.shrunken_tvview_margin_start);
        mTvViewShrunkenEndMargin =
                mResources.getDimensionPixelOffset(R.dimen.shrunken_tvview_margin_end)
                        + mResources.getDimensionPixelSize(R.dimen.side_panel_width);
        mTvViewFrame = createMarginLayoutParams(0, 0, 0, 0);

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);

        mLinearOutSlowIn = AnimationUtils
                .loadInterpolator(mContext, android.R.interpolator.linear_out_slow_in);
        mFastOutLinearIn = AnimationUtils
                .loadInterpolator(mContext, android.R.interpolator.fast_out_linear_in);
    }

    public void onConfigurationChanged(final int windowWidth, final int windowHeight) {
        if (windowWidth > 0 && windowHeight > 0) {
            if (mWindowWidth != windowWidth || mWindowHeight != windowHeight) {
                mWindowWidth = windowWidth;
                mWindowHeight = windowHeight;
                applyDisplayMode(mTvView.getVideoDisplayAspectRatio(), false, true);
            }
        }
    }

    /**
     * Initializes animator in advance of using the animator to improve animation performance.
     * For fast first tune, it is not expected to be called in Activity.onCreate, but called
     * a few seconds later after onCreate.
     */
    public void initAnimatorIfNeeded() {
        initTvAnimatorIfNeeded();
        initBackgroundAnimatorIfNeeded();
    }

    /**
     * It is called when shrunken TvView is desired, such as EditChannelFragment and
     * ChannelsLockedFragment.
     */
    public void startShrunkenTvView() {
        mIsUnderShrunkenTvView = true;
        mTvView.setIsUnderShrunken(true);

        mTvViewStartMarginBeforeShrunken = mTvViewStartMargin;
        mTvViewEndMarginBeforeShrunken = mTvViewEndMargin;
        setTvViewMargin(mTvViewShrunkenStartMargin, mTvViewShrunkenEndMargin);
        mDisplayModeBeforeShrunken = setDisplayMode(DisplayMode.MODE_NORMAL, false, true);
    }

    /**
     * It is called when shrunken TvView is no longer desired, such as EditChannelFragment and
     * ChannelsLockedFragment.
     */
    public void endShrunkenTvView() {
        mIsUnderShrunkenTvView = false;
        mTvView.setIsUnderShrunken(false);
        setTvViewMargin(mTvViewStartMarginBeforeShrunken, mTvViewEndMarginBeforeShrunken);
        setDisplayMode(mDisplayModeBeforeShrunken, false, true);
    }

    /**
     * Returns true, if TvView is shrunken.
     */
    public boolean isUnderShrunkenTvView() {
        return mIsUnderShrunkenTvView;
    }

    /**
     * Returns true, if {@code displayMode} is available now. If screen ratio is matched to
     * video ratio, other display modes than {@link DisplayMode#MODE_NORMAL} are not available.
     */
    public boolean isDisplayModeAvailable(int displayMode) {
        if (displayMode == DisplayMode.MODE_NORMAL) {
            return true;
        }

        int viewWidth = mContentView.getWidth();
        int viewHeight = mContentView.getHeight();

        float videoDisplayAspectRatio = mTvView.getVideoDisplayAspectRatio();
        if (viewWidth <= 0 || viewHeight <= 0 || videoDisplayAspectRatio <= 0f) {
            Log.w(TAG, "Video size is currently unavailable");
            if (DEBUG) {
                Log.d(TAG, "isDisplayModeAvailable: "
                        + "viewWidth=" + viewWidth
                        + ", viewHeight=" + viewHeight
                        + ", videoDisplayAspectRatio=" + videoDisplayAspectRatio
                );
            }
            return false;
        }

        float viewRatio = viewWidth / (float) viewHeight;
        return Math.abs(viewRatio - videoDisplayAspectRatio) >= DISPLAY_MODE_EPSILON;
    }

    /**
     * Returns a constant defined in DisplayMode.
     */
    public int getDisplayMode() {
        if (isDisplayModeAvailable(mDisplayMode)) {
            return mDisplayMode;
        }
        return DisplayMode.MODE_NORMAL;
    }

    /**
     * Sets the display mode to the given value.
     *
     * @return the previous display mode.
     */
    public int setDisplayMode(int displayMode, boolean storeInPreference, boolean animate) {
        int prev = mDisplayMode;
        mDisplayMode = displayMode;
        if (storeInPreference) {
            mSharedPreferences.edit().putInt(TvSettings.PREF_DISPLAY_MODE, displayMode).apply();
        }
        applyDisplayMode(mTvView.getVideoDisplayAspectRatio(), animate, false);
        return prev;
    }

    /**
     * Restores the display mode to the display mode stored in preference.
     */
    public void restoreDisplayMode(boolean animate) {
        int displayMode = mSharedPreferences
                .getInt(TvSettings.PREF_DISPLAY_MODE, DisplayMode.MODE_NORMAL);
        setDisplayMode(displayMode, false, animate);
    }

    /**
     * Updates TvView's aspect ratio. It should be called when video resolution is changed.
     */
    public void updateTvAspectRatio() {
        applyDisplayMode(mTvView.getVideoDisplayAspectRatio(), false, false);
        if (mTvView.isVideoAvailable() && mTvView.isFadedOut()) {
            mTvView.fadeIn(mResources.getInteger(R.integer.tvview_fade_in_duration),
                    mFastOutLinearIn, null);
        }
    }

    /**
     * Fades in TvView.
     */
    public void fadeInTvView() {
        if (mTvView.isFadedOut()) {
            mTvView.fadeIn(mResources.getInteger(R.integer.tvview_fade_in_duration),
                    mFastOutLinearIn, null);
        }
    }

    /**
     * Fades out TvView.
     */
    public void fadeOutTvView(Runnable postAction) {
        if (!mTvView.isFadedOut()) {
            mTvView.fadeOut(mResources.getInteger(R.integer.tvview_fade_out_duration),
                    mLinearOutSlowIn, postAction);
        }
    }

    /**
     * This margins will be applied when applyDisplayMode is called.
     */
    private void setTvViewMargin(int tvViewStartMargin, int tvViewEndMargin) {
        mTvViewStartMargin = tvViewStartMargin;
        mTvViewEndMargin = tvViewEndMargin;
    }

    private boolean isTvViewFullScreen() {
        return mTvViewStartMargin == 0 && mTvViewEndMargin == 0;
    }

    private void setBackgroundColor(int color, FrameLayout.LayoutParams targetLayoutParams,
            boolean animate) {
        if (animate) {
            initBackgroundAnimatorIfNeeded();
            if (mBackgroundAnimator.isStarted()) {
                // Cancel the current animation and start new one.
                mBackgroundAnimator.cancel();
            }

            int decorViewWidth = mContentView.getWidth();
            int decorViewHeight = mContentView.getHeight();
            boolean hasPillarBox = mTvView.getWidth() != decorViewWidth
                    || mTvView.getHeight() != decorViewHeight;
            boolean willHavePillarBox = ((targetLayoutParams.width != LayoutParams.MATCH_PARENT)
                    && targetLayoutParams.width != decorViewWidth) || (
                    (targetLayoutParams.height != LayoutParams.MATCH_PARENT)
                            && targetLayoutParams.height != decorViewHeight);

            if (!isTvViewFullScreen() && !hasPillarBox) {
                // If there is no pillar box, no animation is needed.
                mContentView.setBackgroundColor(color);
            } else if (!isTvViewFullScreen() || willHavePillarBox) {
                mBackgroundAnimator.setIntValues(mBackgroundColor, color);
                mBackgroundAnimator.setEvaluator(new ArgbEvaluator());
                mBackgroundAnimator.setInterpolator(mFastOutLinearIn);
                mBackgroundAnimator.start();
            }
            // In the 'else' case (TV activity is getting out of the shrunken tv view mode and will
            // have a pillar box), we keep the background color and don't show the animation.
        } else {
            mContentView.setBackgroundColor(color);
        }
        mBackgroundColor = color;
    }

    private void setTvViewPosition(final FrameLayout.LayoutParams layoutParams,
            FrameLayout.LayoutParams tvViewFrame, boolean animate) {
        if (DEBUG) {
            Log.d(TAG, "setTvViewPosition: w=" + layoutParams.width + " h=" + layoutParams.height
                    + " s=" + layoutParams.getMarginStart() + " t=" + layoutParams.topMargin
                    + " e=" + layoutParams.getMarginEnd() + " b=" + layoutParams.bottomMargin
                    + " animate=" + animate);
        }
        FrameLayout.LayoutParams oldTvViewFrame = mTvViewFrame;
        mTvViewLayoutParams = layoutParams;
        mTvViewFrame = tvViewFrame;
        if (animate) {
            initTvAnimatorIfNeeded();
            if (mTvViewAnimator.isStarted()) {
                // Cancel the current animation and start new one.
                mTvViewAnimator.cancel();
                mOldTvViewFrame = new FrameLayout.LayoutParams(mLastAnimatedTvViewFrame);
            } else {
                mOldTvViewFrame = new FrameLayout.LayoutParams(oldTvViewFrame);
            }
            mTvViewAnimator.setObjectValues(mTvView.getTvViewLayoutParams(), layoutParams);
            mTvViewAnimator.setEvaluator(new TypeEvaluator<FrameLayout.LayoutParams>() {
                FrameLayout.LayoutParams lp;
                @Override
                public FrameLayout.LayoutParams evaluate(float fraction,
                        FrameLayout.LayoutParams startValue, FrameLayout.LayoutParams endValue) {
                    if (lp == null) {
                        lp = new FrameLayout.LayoutParams(0, 0);
                        lp.gravity = startValue.gravity;
                    }
                    interpolateMargins(lp, startValue, endValue, fraction);
                    return lp;
                }
            });
            mTvViewAnimator
                    .setInterpolator(isTvViewFullScreen() ? mFastOutLinearIn : mLinearOutSlowIn);
            mTvViewAnimator.start();
        } else {
            if (mTvViewAnimator != null && mTvViewAnimator.isStarted()) {
                // Continue the current animation.
                // layoutParams will be applied when animation ends.
                return;
            }
            // This block is also called when animation ends.
            if (isTvViewFullScreen()) {
                // When this layout is for full screen, fix the surface size after layout to make
                // resize animation smooth. During PIP size change, the multiple messages can be
                // queued, if we don't remove MSG_SET_LAYOUT_PARAMS.
                mHandler.removeMessages(MSG_SET_LAYOUT_PARAMS);
                mHandler.obtainMessage(MSG_SET_LAYOUT_PARAMS, layoutParams).sendToTarget();
            } else {
                mTvView.setTvViewLayoutParams(layoutParams);
                mTvView.setLayoutParams(mTvViewFrame);
            }
        }
    }

    private void initTvAnimatorIfNeeded() {
        if (mTvViewAnimator != null) {
            return;
        }

        // TvViewAnimator animates TvView by repeatedly re-layouting TvView.
        // TvView includes a SurfaceView on which scale/translation effects do not work. Normally,
        // SurfaceView can be animated by changing left/top/right/bottom directly using
        // ObjectAnimator, although it would require calling getChildAt(0) against TvView (which is
        // supposed to be opaque). More importantly, this method does not work in case of TvView,
        // because TvView may request layout itself during animation and layout SurfaceView with
        // its own parameters when TvInputService requests to do so.
        mTvViewAnimator = new ObjectAnimator();
        mTvViewAnimator.setTarget(mTvView.getTvView());
        mTvViewAnimator.setProperty(
                Property.of(FrameLayout.class, ViewGroup.LayoutParams.class, "layoutParams"));
        mTvViewAnimator.setDuration(mResources.getInteger(R.integer.tvview_anim_duration));
        mTvViewAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean mCanceled = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                mCanceled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (mCanceled) {
                    mCanceled = false;
                    return;
                }
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        setTvViewPosition(mTvViewLayoutParams, mTvViewFrame, false);
                    }
                });
            }
        });
        mTvViewAnimator.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                float fraction = animator.getAnimatedFraction();
                mLastAnimatedTvViewFrame = (FrameLayout.LayoutParams) mTvView.getLayoutParams();
                interpolateMargins(mLastAnimatedTvViewFrame,
                        mOldTvViewFrame, mTvViewFrame, fraction);
                mTvView.setLayoutParams(mLastAnimatedTvViewFrame);
            }
        });
    }

    private void initBackgroundAnimatorIfNeeded() {
        if (mBackgroundAnimator != null) {
            return;
        }

        mBackgroundAnimator = new ObjectAnimator();
        mBackgroundAnimator.setTarget(mContentView);
        mBackgroundAnimator.setPropertyName("backgroundColor");
        mBackgroundAnimator
                .setDuration(mResources.getInteger(R.integer.tvactivity_background_anim_duration));
        mBackgroundAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mContentView.setBackgroundColor(mBackgroundColor);
                    }
                });
            }
        });
    }

    private void applyDisplayMode(float videoDisplayAspectRatio, boolean animate,
            boolean forceUpdate) {
        if (videoDisplayAspectRatio <= 0f) {
            videoDisplayAspectRatio = (float) mWindowWidth / mWindowHeight;
        }
        if (mAppliedDisplayedMode == mDisplayMode
                && mAppliedTvViewStartMargin == mTvViewStartMargin
                && mAppliedTvViewEndMargin == mTvViewEndMargin
                && Math.abs(mAppliedVideoDisplayAspectRatio - videoDisplayAspectRatio) <
                        DISPLAY_ASPECT_RATIO_EPSILON) {
            if (!forceUpdate) {
                return;
            }
        } else {
            mAppliedDisplayedMode = mDisplayMode;
            mAppliedTvViewStartMargin = mTvViewStartMargin;
            mAppliedTvViewEndMargin = mTvViewEndMargin;
            mAppliedVideoDisplayAspectRatio = videoDisplayAspectRatio;
        }
        int availableAreaWidth = mWindowWidth - mTvViewStartMargin - mTvViewEndMargin;
        int availableAreaHeight = availableAreaWidth * mWindowHeight / mWindowWidth;
        int displayMode = mDisplayMode;
        float availableAreaRatio = 0;
        if (availableAreaWidth <= 0 || availableAreaHeight <= 0) {
            displayMode = DisplayMode.MODE_FULL;
            Log.w(TAG, "Some resolution info is missing during applyDisplayMode. ("
                    + "availableAreaWidth=" + availableAreaWidth + ", availableAreaHeight="
                    + availableAreaHeight + ")");
        } else {
            availableAreaRatio = (float) availableAreaWidth / availableAreaHeight;
        }
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(0, 0,
                ((FrameLayout.LayoutParams) mTvView.getTvViewLayoutParams()).gravity);
        switch (displayMode) {
            case DisplayMode.MODE_ZOOM:
                if (videoDisplayAspectRatio < availableAreaRatio) {
                    // Y axis will be clipped.
                    layoutParams.width = availableAreaWidth;
                    layoutParams.height = Math.round(availableAreaWidth / videoDisplayAspectRatio);
                } else {
                    // X axis will be clipped.
                    layoutParams.width = Math.round(availableAreaHeight * videoDisplayAspectRatio);
                    layoutParams.height = availableAreaHeight;
                }
                break;
            case DisplayMode.MODE_NORMAL:
                if (videoDisplayAspectRatio < availableAreaRatio) {
                    // X axis has black area.
                    layoutParams.width = Math.round(availableAreaHeight * videoDisplayAspectRatio);
                    layoutParams.height = availableAreaHeight;
                } else {
                    // Y axis has black area.
                    layoutParams.width = availableAreaWidth;
                    layoutParams.height = Math.round(availableAreaWidth / videoDisplayAspectRatio);
                }
                break;
            case DisplayMode.MODE_FULL:
            default:
                layoutParams.width = availableAreaWidth;
                layoutParams.height = availableAreaHeight;
                break;
        }
        // FrameLayout has an issue with centering when left and right margins differ.
        // So stick to Gravity.START | Gravity.CENTER_VERTICAL.
        int marginStart = (availableAreaWidth - layoutParams.width) / 2;
        layoutParams.setMarginStart(marginStart);
        int tvViewFrameTop = (mWindowHeight - availableAreaHeight) / 2;
        FrameLayout.LayoutParams tvViewFrame = createMarginLayoutParams(
                mTvViewStartMargin, mTvViewEndMargin, tvViewFrameTop, tvViewFrameTop);
        setTvViewPosition(layoutParams, tvViewFrame, animate);
        setBackgroundColor(mResources.getColor(isTvViewFullScreen()
                ? R.color.tvactivity_background : R.color.tvactivity_background_on_shrunken_tvview,
                null), layoutParams, animate);

        // Update the current display mode.
        mTvOptionsManager.onDisplayModeChanged(displayMode);
    }

    private static int interpolate(int start, int end, float fraction) {
        return (int) (start + (end - start) * fraction);
    }

    private static void interpolateMargins(MarginLayoutParams out,
            MarginLayoutParams startValue, MarginLayoutParams endValue, float fraction) {
        out.topMargin = interpolate(startValue.topMargin, endValue.topMargin, fraction);
        out.bottomMargin = interpolate(startValue.bottomMargin, endValue.bottomMargin, fraction);
        out.setMarginStart(interpolate(startValue.getMarginStart(), endValue.getMarginStart(),
                fraction));
        out.setMarginEnd(interpolate(startValue.getMarginEnd(), endValue.getMarginEnd(), fraction));
        out.width = interpolate(startValue.width, endValue.width, fraction);
        out.height = interpolate(startValue.height, endValue.height, fraction);
    }

    private FrameLayout.LayoutParams createMarginLayoutParams(
            int startMargin, int endMargin, int topMargin, int bottomMargin) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(0, 0);
        lp.setMarginStart(startMargin);
        lp.setMarginEnd(endMargin);
        lp.topMargin = topMargin;
        lp.bottomMargin = bottomMargin;
        lp.width = mWindowWidth - startMargin - endMargin;
        lp.height = mWindowHeight - topMargin - bottomMargin;
        return lp;
    }
}