/**
 * Copyright (c) 2011, Google Inc.
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
package com.android.mail.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.annotation.TargetApi;
import android.content.Context;
import android.os.Handler;
import android.support.annotation.StringRes;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.utils.Utils;
import com.android.mail.utils.ViewUtils;

/**
 * A custom {@link View} that exposes an action to the user.
 */
public class ActionableToastBar extends FrameLayout {

    private boolean mHidden = true;
    private final Runnable mHideToastBarRunnable;
    private final Handler mHideToastBarHandler;

    /**
     * The floating action button if it must be animated with the toast bar; <code>null</code>
     * otherwise.
     */
    private View mFloatingActionButton;

    /**
     * <tt>true</tt> while animation is occurring; false otherwise; It is used to block attempts to
     * hide the toast bar while it is being animated
     */
    private boolean mAnimating = false;

    /** The interpolator that produces position values during animation. */
    private TimeInterpolator mAnimationInterpolator;

    /** The length of time (in milliseconds) that the popup / push down animation run over */
    private int mAnimationDuration;

    /**
     * The time at which the toast popup completed. This is used to ensure the toast remains
     * visible for a minimum duration before it is removed.
     */
    private long mAnimationCompleteTimestamp;

    /** The min time duration for which the toast must remain visible and cannot be dismissed. */
    private long mMinToastDuration;

    /** The max time duration for which the toast can remain visible and must be dismissed. */
    private long mMaxToastDuration;

    /** The view that contains the description when laid out as a single line. */
    private TextView mSingleLineDescriptionView;

    /** The view that contains the text for the action button when laid out as a single line. */
    private TextView mSingleLineActionView;

    /** The view that contains the description when laid out as a multiple lines;
     * always <tt>null</tt> in two-pane layouts. */
    private TextView mMultiLineDescriptionView;

    /** The view that contains the text for the action button when laid out as a multiple lines;
     * always <tt>null</tt> in two-pane layouts. */
    private TextView mMultiLineActionView;

    /** The minimum width of this view; applicable when description text is very short. */
    private int mMinWidth;

    /** The maximum width of this view; applicable when description text is long enough to wrap. */
    private int mMaxWidth;

    private ToastBarOperation mOperation;

    public ActionableToastBar(Context context) {
        this(context, null);
    }

    public ActionableToastBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ActionableToastBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mAnimationInterpolator = createTimeInterpolator();
        mAnimationDuration = getResources().getInteger(R.integer.toast_bar_animation_duration_ms);
        mMinToastDuration = getResources().getInteger(R.integer.toast_bar_min_duration_ms);
        mMaxToastDuration = getResources().getInteger(R.integer.toast_bar_max_duration_ms);
        mMinWidth = getResources().getDimensionPixelOffset(R.dimen.snack_bar_min_width);
        mMaxWidth = getResources().getDimensionPixelOffset(R.dimen.snack_bar_max_width);
        mHideToastBarHandler = new Handler();
        mHideToastBarRunnable = new Runnable() {
            @Override
            public void run() {
                if (!mHidden) {
                    hide(true, false /* actionClicked */);
                }
            }
        };
    }

    private TimeInterpolator createTimeInterpolator() {
        // L and beyond we can use the new PathInterpolator
        if (Utils.isRunningLOrLater()) {
            return createPathInterpolator();
        }

        // fall back to basic LinearInterpolator
        return new LinearInterpolator();
    }

    @TargetApi(21)
    private TimeInterpolator createPathInterpolator() {
        return new PathInterpolator(0.4f, 0f, 0.2f, 1f);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mSingleLineDescriptionView = (TextView) findViewById(R.id.description_text);
        mSingleLineActionView = (TextView) findViewById(R.id.action_text);
        mMultiLineDescriptionView = (TextView) findViewById(R.id.multiline_description_text);
        mMultiLineActionView = (TextView) findViewById(R.id.multiline_action_text);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final boolean showAction = !TextUtils.isEmpty(mSingleLineActionView.getText());

        // configure the UI assuming the description fits on a single line
        setVisibility(false /* multiLine */, showAction);

        // measure the view and its content
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        // if specific views exist to handle the multiline case
        if (mMultiLineDescriptionView != null) {
            // if the description does not fit on a single line
            if (mSingleLineDescriptionView.getLineCount() > 1) {
                //switch to multi line display views
                setVisibility(true /* multiLine */, showAction);

                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        // if width constraints were given explicitly, honor them; otherwise use the natural width
        } else if (mMinWidth >= 0 && mMaxWidth >= 0) {
            // otherwise, adjust the the single line view so wrapping occurs at the desired width
            // (the total width of the toast bar must always fall between the given min and max
            // width; if max width cannot accommodate all of the description text, it wraps)
            if (getMeasuredWidth() < mMinWidth) {
                widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(mMinWidth, MeasureSpec.EXACTLY);
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            } else if (getMeasuredWidth() > mMaxWidth) {
                widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(mMaxWidth, MeasureSpec.EXACTLY);
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        }
    }

    /**
     * Displays the toast bar and makes it visible. Allows the setting of
     * parameters to customize the display.
     * @param listener Performs some action when the action button is clicked.
     *                 If the {@link ToastBarOperation} overrides
     *                 {@link ToastBarOperation#shouldTakeOnActionClickedPrecedence()}
     *                 to return <code>true</code>, the
     *                 {@link ToastBarOperation#onActionClicked(android.content.Context)}
     *                 will override this listener and be called instead.
     * @param descriptionText a description text to show in the toast bar
     * @param actionTextResourceId resource ID for the text to show in the action button
     * @param replaceVisibleToast if true, this toast should replace any currently visible toast.
     *                            Otherwise, skip showing this toast.
     * @param autohide <tt>true</tt> indicates the toast will be automatically hidden after a time
     *                 delay; <tt>false</tt> indicate the toast will remain visible until the user
     *                 dismisses it
     * @param op the operation that corresponds to the specific toast being shown
     */
    public void show(final ActionClickedListener listener, final CharSequence descriptionText,
                     @StringRes final int actionTextResourceId, final boolean replaceVisibleToast,
                     final boolean autohide, final ToastBarOperation op) {
        if (!mHidden && !replaceVisibleToast) {
            return;
        }

        // Remove any running delayed animations first
        mHideToastBarHandler.removeCallbacks(mHideToastBarRunnable);

        mOperation = op;

        setActionClickListener(new OnClickListener() {
            @Override
            public void onClick(View widget) {
                if (op != null && op.shouldTakeOnActionClickedPrecedence()) {
                    op.onActionClicked(getContext());
                } else {
                    listener.onActionClicked(getContext());
                }
                hide(true /* animate */, true /* actionClicked */);
            }
        });

        setDescriptionText(descriptionText);
        ViewUtils.announceForAccessibility(this, descriptionText);
        setActionText(actionTextResourceId);

        // if this toast bar is not yet hidden, animate it in place; otherwise we just update the
        // text that it displays
        if (mHidden) {
            mHidden = false;
            popupToast();
        }

        if (autohide) {
            // Set up runnable to execute hide toast once delay is completed
            mHideToastBarHandler.postDelayed(mHideToastBarRunnable, mMaxToastDuration);
        }
    }

    public ToastBarOperation getOperation() {
        return mOperation;
    }

    /**
     * Hides the view and resets the state.
     */
    public void hide(boolean animate, boolean actionClicked) {
        mHidden = true;
        mAnimationCompleteTimestamp = 0;
        mHideToastBarHandler.removeCallbacks(mHideToastBarRunnable);
        if (getVisibility() == View.VISIBLE) {
            setActionClickListener(null);
            // Hide view once it's clicked.
            if (animate) {
                pushDownToast();
            } else {
                // immediate hiding implies no position adjustment of the FAB and hide the toast bar
                if (mFloatingActionButton != null) {
                    mFloatingActionButton.setTranslationY(0);
                }
                setVisibility(View.GONE);
            }

            if (!actionClicked && mOperation != null) {
                mOperation.onToastBarTimeout(getContext());
            }
        }
    }

    /**
     * @return <tt>true</tt> while the toast bar animation is popping up or pushing down the toast;
     *      <tt>false</tt> otherwise
     */
    public boolean isAnimating() {
        return mAnimating;
    }

    /**
     * @return <tt>true</tt> if this toast bar has not yet been displayed for a long enough period
     *      of time to be dismissed; <tt>false</tt> otherwise
     */
    public boolean cannotBeHidden() {
        return System.currentTimeMillis() - mAnimationCompleteTimestamp < mMinToastDuration;
    }

    @Override
    public void onDetachedFromWindow() {
        mHideToastBarHandler.removeCallbacks(mHideToastBarRunnable);
        super.onDetachedFromWindow();
    }

    public boolean isEventInToastBar(MotionEvent event) {
        if (!isShown()) {
            return false;
        }
        int[] xy = new int[2];
        float x = event.getX();
        float y = event.getY();
        getLocationOnScreen(xy);
        return (x > xy[0] && x < (xy[0] + getWidth()) && y > xy[1] && y < xy[1] + getHeight());
    }

    /**
     * Indicates that the given view should be animated with this toast bar as it pops up and pushes
     * down. In some layouts, the floating action button appears above the toast bar and thus must
     * be pushed up as the toast pops up and fall down as the toast is pushed down.
     *
     * @param floatingActionButton a the floating action button to be animated with the toast bar as
     *                             it pops up and pushes down
     */
    public void setFloatingActionButton(View floatingActionButton) {
        mFloatingActionButton = floatingActionButton;
    }

    /**
     * If the View requires multiple lines to fully display the toast description then make the
     * multi-line view visible and hide the single line view; otherwise vice versa. If the action
     * text is present, display it, otherwise hide it.
     *
     * @param multiLine <tt>true</tt> if the View requires multiple lines to display the toast
     * @param showAction <tt>true</tt> if the action text is present and should be shown
     */
    private void setVisibility(boolean multiLine, boolean showAction) {
        mSingleLineDescriptionView.setVisibility(!multiLine ? View.VISIBLE : View.GONE);
        mSingleLineActionView.setVisibility(!multiLine && showAction ? View.VISIBLE : View.GONE);
        if (mMultiLineDescriptionView != null) {
            mMultiLineDescriptionView.setVisibility(multiLine ? View.VISIBLE : View.GONE);
        }
        if (mMultiLineActionView != null) {
            mMultiLineActionView.setVisibility(multiLine && showAction ? View.VISIBLE : View.GONE);
        }
    }

    private void setDescriptionText(CharSequence description) {
        mSingleLineDescriptionView.setText(description);
        if (mMultiLineDescriptionView != null) {
            mMultiLineDescriptionView.setText(description);
        }
    }

    private void setActionText(@StringRes int actionTextResourceId) {
        if (actionTextResourceId == 0) {
            mSingleLineActionView.setText("");
            if (mMultiLineActionView != null) {
                mMultiLineActionView.setText("");
            }
        } else {
            mSingleLineActionView.setText(actionTextResourceId);
            if (mMultiLineActionView != null) {
                mMultiLineActionView.setText(actionTextResourceId);
            }
        }
    }

    private void setActionClickListener(OnClickListener listener) {
        mSingleLineActionView.setOnClickListener(listener);

        if (mMultiLineActionView != null) {
            mMultiLineActionView.setOnClickListener(listener);
        }
    }

    /**
     * Pops up the toast (and optionally the floating action button) into view via an animation.
     */
    private void popupToast() {
        final float animationDistance = getAnimationDistance();

        setVisibility(View.VISIBLE);
        setTranslationY(animationDistance);
        animate()
                .setDuration(mAnimationDuration)
                .setInterpolator(mAnimationInterpolator)
                .translationYBy(-animationDistance)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        mAnimating = true;
                    }
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mAnimating = false;
                        mAnimationCompleteTimestamp = System.currentTimeMillis();
                    }
                });

        if (mFloatingActionButton != null) {
            mFloatingActionButton.setTranslationY(animationDistance);
            mFloatingActionButton.animate()
                    .setDuration(mAnimationDuration)
                    .setInterpolator(mAnimationInterpolator)
                    .translationYBy(-animationDistance);
        }
    }

    /**
     * Pushes down the toast (and optionally the floating action button) out of view via an
     * animation.
     */
    private void pushDownToast() {
        final float animationDistance = getAnimationDistance();

        setTranslationY(0);
        animate()
                .setDuration(mAnimationDuration)
                .setInterpolator(mAnimationInterpolator)
                .translationYBy(animationDistance)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        mAnimating = true;
                    }
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mAnimating = false;
                        // on push down animation completion the toast bar is no longer present
                        setVisibility(View.GONE);
                    }
                });

        if (mFloatingActionButton != null) {
            mFloatingActionButton.setTranslationY(0);
            mFloatingActionButton.animate()
                    .setDuration(mAnimationDuration)
                    .setInterpolator(mAnimationInterpolator)
                    .translationYBy(animationDistance)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            // on push down animation completion the FAB no longer needs translation
                            mFloatingActionButton.setTranslationY(0);
                        }
                    });
        }
    }

    /**
     * The toast bar is assumed to be positioned at the bottom of the display, so the distance over
     * which to animate is the height of the toast bar + any margin beneath the toast bar.
     *
     * @return the distance to move the toast bar to make it appear to pop up / push down from the
     *      bottom of the display
     */
    private int getAnimationDistance() {
        // total height over which the animation takes place is the toast bar height + bottom margin
        final ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) getLayoutParams();
        return getHeight() + params.bottomMargin;
    }

    /**
     * Classes that wish to perform some action when the action button is clicked
     * should implement this interface.
     */
    public interface ActionClickedListener {
        public void onActionClicked(Context context);
    }
}
