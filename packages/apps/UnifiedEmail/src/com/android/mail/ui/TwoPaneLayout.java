/*******************************************************************************
 *      Copyright (C) 2012 Google Inc.
 *      Licensed to The Android Open Source Project.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *******************************************************************************/

package com.android.mail.ui;

import java.util.List;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;

import com.android.mail.R;
import com.android.mail.ui.ViewMode.ModeChangeListener;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;
import com.android.mail.utils.ViewUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;

/**
 * This is a custom layout that manages the possible views of Gmail's large screen (read: tablet)
 * activity, and the transitions between them.
 *
 * This is not intended to be a generic layout; it is specific to the {@code Fragment}s
 * available in {@link MailActivity} and assumes their existence. It merely configures them
 * according to the specific <i>modes</i> the {@link Activity} can be in.
 *
 * Currently, the layout differs in three dimensions: orientation, two aspects of view modes.
 * This results in essentially three states: One where the folders are on the left and conversation
 * list is on the right, and two states where the conversation list is on the left: one in which
 * it's collapsed and another where it is not.
 *
 * In folder or conversation list view, conversations are hidden and folders and conversation lists
 * are visible. This is the case in both portrait and landscape
 *
 * In Conversation List or Conversation View, folders are hidden, and conversation lists and
 * conversation view is visible. This is the case in both portrait and landscape.
 *
 * In the Gmail source code, this was called TriStateSplitLayout
 */
final class TwoPaneLayout extends FrameLayout implements ModeChangeListener,
        GmailDragHelper.GmailDragHelperCallback {
    public static final int MISCELLANEOUS_VIEW_ID = R.id.miscellaneous_pane;
    public static final long SLIDE_DURATION_MS = 300;

    private static final String LOG_TAG = "TwoPaneLayout";

    private final int mDrawerWidthMini;
    private final int mDrawerWidthOpen;
    private final int mDrawerWidthDelta;
    private final double mConversationListWeight;
    private final TimeInterpolator mSlideInterpolator;
    /**
     * If true, always show a conversation view right next to the conversation list. This view will
     * also be populated (preview / "peek" mode) with a default conversation if none is selected by
     * the user.<br>
     * <br>
     * If false, this layout group will treat the thread list and conversation view as full-width
     * panes to switch between.
     */
    private final boolean mShouldShowPreviewPanel;

    /**
     * The current mode that the tablet layout is in. This is a constant integer that holds values
     * that are {@link ViewMode} constants like {@link ViewMode#CONVERSATION}.
     */
    private int mCurrentMode = ViewMode.UNKNOWN;
    /**
     * This is a copy of {@link #mCurrentMode} that layout/positioning/animating code uses to
     * compare to the 'new' current mode, to avoid unnecessarily calculation.
     */
    private int mTranslatedMode = ViewMode.UNKNOWN;

    private TwoPaneController mController;
    private LayoutListener mListener;
    // Drag helper for capturing drag over the list pane
    private final GmailDragHelper mDragHelper;
    private int mCurrentDragMode;
    // mXThreshold is only used for dragging the mini-drawer out. This optional parameter allows for
    // the drag to only initiate once it hits the edge of the mini-drawer so that the edge follows
    // the drag.
    private Float mXThreshold;

    private View mFoldersView;
    private View mListView;
    // content view encompasses both conversation and ad view.
    private View mConversationFrame;

    // These two views get switched in/out depending on the view mode.
    private View mConversationView;
    private View mMiscellaneousView;

    private boolean mIsRtl;

    // These are computed when the base layout changes.
    private int mFoldersLeft;
    private int mFoldersRight;
    private int mListLeft;
    private int mListRight;
    private int mConvLeft;
    private int mConvRight;

    private final Drawable mShadowDrawable;
    private final int mShadowMinWidth;

    private final List<Runnable> mTransitionCompleteJobs = Lists.newArrayList();
    private final PaneAnimationListener mPaneAnimationListener = new PaneAnimationListener();

    // Keep track if we are tracking the current touch events
    private boolean mShouldInterceptCurrentTouch;

    public interface ConversationListLayoutListener {
        /**
         * Used for two-pane landscape layout positioning when other views need to align themselves
         * to the list view. Should be called only in tablet landscape mode!
         * @param xEnd the ending x coordinate of the list view
         * @param drawerOpen
         */
        void onConversationListLayout(int xEnd, boolean drawerOpen);
    }

    // Responsible for invalidating the shadow region only to minimize drawing overhead (and jank)
    // Coordinated with ListView animation to ensure shadow and list slide together.
    private final ValueAnimator.AnimatorUpdateListener mListViewAnimationListener =
            new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    if (mIsRtl) {
                        // Get the right edge of list and use as left edge coord for shadow
                        final int leftEdgeCoord = (int) mListView.getX() + mListView.getWidth();
                        invalidate(leftEdgeCoord, 0, leftEdgeCoord + mShadowMinWidth,
                                getBottom());
                    } else {
                        // Get the left edge of list and use as right edge coord for shadow
                        final int rightEdgeCoord = (int) mListView.getX();
                        invalidate(rightEdgeCoord - mShadowMinWidth, 0, rightEdgeCoord,
                                getBottom());
                    }
                }
            };

    public TwoPaneLayout(Context context) {
        this(context, null);
    }

    public TwoPaneLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        final Resources res = getResources();

        // The conversation list might be visible now, depending on the layout: in portrait we
        // don't show the conversation list, but in landscape we do.  This information is stored
        // in the constants
        mShouldShowPreviewPanel = res.getBoolean(R.bool.is_tablet_landscape);

        mDrawerWidthMini = res.getDimensionPixelSize(R.dimen.two_pane_drawer_width_mini);
        mDrawerWidthOpen = res.getDimensionPixelSize(R.dimen.two_pane_drawer_width_open);
        mDrawerWidthDelta = mDrawerWidthOpen - mDrawerWidthMini;

        mSlideInterpolator = AnimationUtils.loadInterpolator(context,
                android.R.interpolator.decelerate_cubic);

        final int convListWeight = res.getInteger(R.integer.conversation_list_weight);
        final int convViewWeight = res.getInteger(R.integer.conversation_view_weight);
        mConversationListWeight = (double) convListWeight
                / (convListWeight + convViewWeight);

        mShadowDrawable = getResources().getDrawable(R.drawable.ic_vertical_shadow_start_4dp);
        mShadowMinWidth = mShadowDrawable.getMinimumWidth();

        mDragHelper = new GmailDragHelper(context, this);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(super.toString());
        sb.append("{mTranslatedMode=");
        sb.append(mTranslatedMode);
        sb.append(" mCurrDragMode=");
        sb.append(mCurrentDragMode);
        sb.append(" mShouldInterceptCurrentTouch=");
        sb.append(mShouldInterceptCurrentTouch);
        sb.append(" mTransitionCompleteJobs=");
        sb.append(mTransitionCompleteJobs);
        sb.append("}");
        return sb.toString();
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        // Draw children/update the canvas first.
        super.dispatchDraw(canvas);

        if (ViewUtils.isViewRtl(this)) {
            // Get the right edge of list and use as left edge coord for shadow
            final int leftEdgeCoord = (int) mListView.getX() + mListView.getWidth();
            mShadowDrawable.setBounds(leftEdgeCoord, 0, leftEdgeCoord + mShadowMinWidth,
                    mListView.getBottom());
        } else {
            // Get the left edge of list and use as right edge coord for shadow
            final int rightEdgeCoord = (int) mListView.getX();
            mShadowDrawable.setBounds(rightEdgeCoord - mShadowMinWidth, 0, rightEdgeCoord,
                    mListView.getBottom());
        }

        mShadowDrawable.draw(canvas);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mFoldersView = findViewById(R.id.drawer);
        mListView = findViewById(R.id.conversation_list_pane);
        mConversationFrame = findViewById(R.id.conversation_frame);

        mConversationView = mConversationFrame.findViewById(R.id.conversation_pane);
        mMiscellaneousView = mConversationFrame.findViewById(MISCELLANEOUS_VIEW_ID);

        // all panes start GONE in initial UNKNOWN mode to avoid drawing misplaced panes
        mCurrentMode = ViewMode.UNKNOWN;
        mFoldersView.setVisibility(GONE);
        mListView.setVisibility(GONE);
        mConversationView.setVisibility(GONE);
        mMiscellaneousView.setVisibility(GONE);
    }

    @VisibleForTesting
    public void setController(TwoPaneController controller) {
        mController = controller;
        mListener = controller;

        ((ConversationViewFrame) mConversationFrame).setDownEventListener(mController);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        LogUtils.d(Utils.VIEW_DEBUGGING_TAG, "TPL(%s).onMeasure()", this);
        setupPaneWidths(MeasureSpec.getSize(widthMeasureSpec));
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        LogUtils.d(Utils.VIEW_DEBUGGING_TAG, "TPL(%s).onLayout()", this);
        super.onLayout(changed, l, t, r, b);
        mIsRtl = ViewUtils.isViewRtl(this);

        // Layout only positions the children views at their default locations, and any pane
        // movement is done via translation rather than layout.
        // Thus, we should only re-compute the overall layout on changed.
        if (changed) {
            final int width = getMeasuredWidth();
            computePanePositions(width);

            // If the view mode is different from positions and we are computing pane position, then
            // set the default translation for portrait mode.
            // This is necessary because on rotation we get onViewModeChanged() call before
            // onMeasure actually happens, so we often do not know the width to translate to. This
            // call ensures that the default translation values always correspond to the view mode.
            if (mTranslatedMode != mCurrentMode && !mShouldShowPreviewPanel) {
                translateDueToViewMode(width, false /* animate */);
            } else {
                onTransitionComplete();
            }
        }

        // Layout the children views
        final int bottom = getMeasuredHeight();
        mFoldersView.layout(mFoldersLeft, 0, mFoldersRight, bottom);
        mListView.layout(mListLeft, 0, mListRight, bottom);
        mConversationFrame.layout(mConvLeft, 0, mConvRight, bottom);
    }

    /**
     * Sizes up the three sliding panes. This method will ensure that the LayoutParams of the panes
     * have the correct widths set for the current overall size and view mode.
     *
     * @param parentWidth this view's new width
     */
    private void setupPaneWidths(int parentWidth) {
        // only adjust the pane widths when my width changes
        if (parentWidth != getMeasuredWidth()) {
            final int convWidth = computeConversationWidth(parentWidth);
            setPaneWidth(mConversationFrame, convWidth);
            setPaneWidth(mListView, computeConversationListWidth(parentWidth));
        }
    }

    /**
     * Compute the default base location of each pane and save it in their corresponding
     * instance variables. onLayout will then layout each child accordingly.
     * @param width the available width to layout the children panes
     */
    private void computePanePositions(int width) {
        // Always compute the base value as closed drawer
        final int foldersW = mDrawerWidthMini;
        final int listW = getPaneWidth(mListView);
        final int convW = getPaneWidth(mConversationFrame);

        // Compute default pane positions
        if (mIsRtl) {
            mFoldersLeft = width - mDrawerWidthOpen;
            mListLeft = width - foldersW- listW;
            mConvLeft = mListLeft - convW;
        } else {
            mFoldersLeft = 0;
            mListLeft = foldersW;
            mConvLeft = mListLeft + listW;
        }
        mFoldersRight = mFoldersLeft + mDrawerWidthOpen;
        mListRight = mListLeft + listW;
        mConvRight = mConvLeft + convW;
    }

    /**
     * Animate the drawer to the provided state.
     */
    public void animateDrawer(boolean minimized) {
        // In rtl the drawer opens in the negative direction.
        final int openDrawerDelta = mIsRtl ? -mDrawerWidthDelta : mDrawerWidthDelta;
        translatePanes(minimized ? 0 : openDrawerDelta, 0 /* drawerDeltaX */, true /* animate */);
    }

    /**
     * Translate the panes to their ending positions, can choose to either animate the translation
     * or let it be instantaneous.
     * @param deltaX The ending translationX to translate all of the panes except for drawer.
     * @param drawerDeltaX the ending translationX to translate the drawer. This is necessary
     *   because in landscape mode the drawer doesn't actually move and rest of the panes simply
     *   move to cover/uncover the drawer. The drawer only moves in portrait from TL -> CV.
     * @param animate whether to animate the translation or not.
     */
    private void translatePanes(float deltaX, float drawerDeltaX, boolean animate) {
        if (animate) {
            animatePanes(deltaX, drawerDeltaX);
        } else {
            mFoldersView.setTranslationX(drawerDeltaX);
            mListView.setTranslationX(deltaX);
            mConversationFrame.setTranslationX(deltaX);
        }
    }

    /**
     * Animate the panes' translationX to their corresponding deltas. Refer to
     * {@link TwoPaneLayout#translatePanes(float, float, boolean)} for explanation on deltas.
     */
    private void animatePanes(float deltaX, float drawerDeltaX) {
        mConversationFrame.animate().translationX(deltaX);

        final ViewPropertyAnimator listAnimation =  mListView.animate()
                .translationX(deltaX)
                .setListener(mPaneAnimationListener);

        mFoldersView.animate().translationX(drawerDeltaX);

        // If we're running K+, we can use the update listener to transition the list's left shadow
        // and set different update listeners based on rtl to avoid doing a check on every frame
        if (Utils.isRunningKitkatOrLater()) {
            listAnimation.setUpdateListener(mListViewAnimationListener);
        }

        configureAnimations(mFoldersView, mListView, mConversationFrame);
    }

    private void configureAnimations(View... views) {
        for (View v : views) {
            v.animate()
                .setInterpolator(mSlideInterpolator)
                .setDuration(SLIDE_DURATION_MS);
        }
    }

    /**
     * Adjusts the visibility of each pane before and after a transition. After the transition,
     * any invisible panes should be marked invisible. But visible panes should not wait for the
     * transition to finish-- they should be marked visible immediately.
     */
    private void adjustPaneVisibility(final boolean folderVisible, final boolean listVisible,
            final boolean cvVisible) {
        applyPaneVisibility(VISIBLE, folderVisible, listVisible, cvVisible);
        mTransitionCompleteJobs.add(new Runnable() {
            @Override
            public void run() {
                applyPaneVisibility(INVISIBLE, !folderVisible, !listVisible, !cvVisible);
            }
        });
    }

    private void applyPaneVisibility(int visibility, boolean applyToFolders, boolean applyToList,
            boolean applyToCV) {
        if (applyToFolders) {
            mFoldersView.setVisibility(visibility);
        }
        if (applyToList) {
            mListView.setVisibility(visibility);
        }
        if (applyToCV) {
            if (mConversationView.getVisibility() != GONE) {
                mConversationView.setVisibility(visibility);
            }
            if (mMiscellaneousView.getVisibility() != GONE) {
                mMiscellaneousView.setVisibility(visibility);
            }
        }
    }

    private void onTransitionComplete() {
        if (mController.isDestroyed()) {
            // quit early if the hosting activity was destroyed before the animation finished
            LogUtils.i(LOG_TAG, "IN TPL.onTransitionComplete, activity destroyed->quitting early");
            return;
        }

        for (Runnable job : mTransitionCompleteJobs) {
            job.run();
        }
        mTransitionCompleteJobs.clear();

        // We finished transitioning into the new mode.
        mTranslatedMode = mCurrentMode;

        // Notify conversation list layout listeners of position change.
        final int xEnd = mIsRtl ? mListLeft : mListRight;
        if (mShouldShowPreviewPanel && xEnd != 0) {
            final List<ConversationListLayoutListener> layoutListeners =
                    mController.getConversationListLayoutListeners();
            for (ConversationListLayoutListener listener : layoutListeners) {
                listener.onConversationListLayout(xEnd, isDrawerOpen());
            }
        }

        dispatchVisibilityChanged();
    }

    private void dispatchVisibilityChanged() {
        switch (mCurrentMode) {
            case ViewMode.CONVERSATION:
            case ViewMode.SEARCH_RESULTS_CONVERSATION:
                dispatchConversationVisibilityChanged(true);
                dispatchConversationListVisibilityChange(!isConversationListCollapsed());

                break;
            case ViewMode.CONVERSATION_LIST:
            case ViewMode.SEARCH_RESULTS_LIST:
            case ViewMode.WAITING_FOR_ACCOUNT_INITIALIZATION:
                dispatchConversationVisibilityChanged(false);
                dispatchConversationListVisibilityChange(true);

                break;
            case ViewMode.AD:
                dispatchConversationVisibilityChanged(false);
                dispatchConversationListVisibilityChange(!isConversationListCollapsed());

                break;
            default:
                break;
        }
    }

    @Override
    public void onDragStarted() {
        mController.onDrawerDragStarted();
    }

    @Override
    public void onDrag(float deltaX) {
        // We use percentDragged here because deltaX is relative to the current drag and not
        // relative to the start/end positions of the drawer.
        final float percentDragged = computeDragPercentage(deltaX);
        // Again, in RTL the drawer opens in the negative direction, so need to inverse the delta.
        final float translationX = percentDragged *
                (mIsRtl ? -mDrawerWidthDelta : mDrawerWidthDelta);
        translatePanes(translationX, 0 /* drawerDeltaX */, false /* animate */);
        mController.onDrawerDrag(percentDragged);
        // Invalidate the entire drawers region to ensure that we don't get the "ghosts" of the
        // fake shadow for pre-L.
        if (mIsRtl) {
            invalidate((int) mListView.getX() + mListView.getWidth(), 0,
                    (int) mFoldersView.getX() + mFoldersView.getWidth(), getBottom());
        } else {
            invalidate((int) mFoldersView.getX(), 0, (int) mListView.getX(), getBottom());
        }
    }

    @Override
    public void onDragEnded(float deltaX, float velocityX, boolean isFling) {
        if (isFling) {
            // Drawer is minimized if velocity is toward the left or it's rtl.
            if (mIsRtl) {
                mController.onDrawerDragEnded(velocityX >= 0);
            } else {
                mController.onDrawerDragEnded(velocityX < 0);
            }
        } else {
            // If we got past the half-way mark, animate it rest of the way.
            mController.onDrawerDragEnded(computeDragPercentage(deltaX) < 0.5f);
        }
    }

    /**
     * Given the delta that user moved, return a percentage that signifies the drag progress.
     * @param deltaX the distance dragged.
     * @return percent dragged (values range from 0 to 1).
     *   0 means a fully closed drawer, and 1 means a fully open drawer.
     */
    private float computeDragPercentage(float deltaX) {
        final float percent;
        if (mIsRtl) {
            if (mCurrentDragMode == GmailDragHelper.CAPTURE_LEFT_TO_RIGHT) {
                percent = (mDrawerWidthDelta - deltaX) / mDrawerWidthDelta;
            } else {
                percent = -deltaX / mDrawerWidthDelta;
            }
        } else {
            if (mCurrentDragMode == GmailDragHelper.CAPTURE_LEFT_TO_RIGHT) {
                percent = deltaX / mDrawerWidthDelta;
            } else {
                percent = (mDrawerWidthDelta + deltaX) / mDrawerWidthDelta;
            }
        }

        return percent < 0 ? 0 : percent > 1 ? 1 : percent;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (isModeChangePending()) {
            return false;
        }

        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                final float x = ev.getX();
                final boolean drawerOpen = isDrawerOpen();
                if (drawerOpen) {
                    // Only start intercepting if the down event is inside the list pane or in
                    // landscape conv pane
                    final float left;
                    final float right;
                    if (mShouldShowPreviewPanel) {
                        final boolean isAdMode = ViewMode.isAdMode(mCurrentMode);
                        left = mIsRtl ? mConversationFrame.getX() : mListView.getX();
                        right = mIsRtl ? mListView.getX() + mListView.getWidth() :
                                mConversationFrame.getX() + mConversationFrame.getWidth();
                    } else {
                        left = mListView.getX();
                        right = left + mListView.getWidth();
                    }

                    // Set the potential start drag states
                    mShouldInterceptCurrentTouch = x >= left && x <= right;
                    mXThreshold = null;
                    if (mIsRtl) {
                        mCurrentDragMode = GmailDragHelper.CAPTURE_LEFT_TO_RIGHT;
                    } else {
                        mCurrentDragMode = GmailDragHelper.CAPTURE_RIGHT_TO_LEFT;
                    }
                } else {
                    // Only capture within the mini drawer
                    final float foldersX1 = mIsRtl ? mFoldersView.getX() + mDrawerWidthDelta :
                            mFoldersView.getX();
                    final float foldersX2 = foldersX1 + mDrawerWidthMini;

                    // Set the potential start drag states
                    mShouldInterceptCurrentTouch = x >= foldersX1 && x <= foldersX2;
                    if (mIsRtl) {
                        mCurrentDragMode = GmailDragHelper.CAPTURE_RIGHT_TO_LEFT;
                        mXThreshold = (float) mFoldersLeft + mDrawerWidthDelta;
                    } else {
                        mCurrentDragMode = GmailDragHelper.CAPTURE_LEFT_TO_RIGHT;
                        mXThreshold = (float) mFoldersLeft + mDrawerWidthMini;
                    }
                }
                break;
        }
        return mShouldInterceptCurrentTouch &&
                mDragHelper.processTouchEvent(ev, mCurrentDragMode, mXThreshold);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent ev) {
        if (mShouldInterceptCurrentTouch) {
            mDragHelper.processTouchEvent(ev, mCurrentDragMode, mXThreshold);
            return true;
        }
        return super.onTouchEvent(ev);
    }

    /**
     * Computes the width of the conversation list in stable state of the current mode.
     */
    public int computeConversationListWidth() {
        return computeConversationListWidth(getMeasuredWidth());
    }

    /**
     * Computes the width of the conversation list in stable state of the current mode.
     */
    private int computeConversationListWidth(int parentWidth) {
        final int availWidth = parentWidth - mDrawerWidthMini;
        return mShouldShowPreviewPanel ? (int) (availWidth * mConversationListWeight) : availWidth;
    }

    public int computeConversationWidth() {
        return computeConversationWidth(getMeasuredWidth());
    }

    /**
     * Computes the width of the conversation pane in stable state of the
     * current mode.
     */
    private int computeConversationWidth(int parentWidth) {
        return mShouldShowPreviewPanel ? parentWidth - computeConversationListWidth(parentWidth)
                - mDrawerWidthMini : parentWidth;
    }

    private void dispatchConversationListVisibilityChange(boolean visible) {
        if (mListener != null) {
            mListener.onConversationListVisibilityChanged(visible);
        }
    }

    private void dispatchConversationVisibilityChanged(boolean visible) {
        if (mListener != null) {
            mListener.onConversationVisibilityChanged(visible);
        }
    }

    // does not apply to drawer children. will return zero for those.
    private int getPaneWidth(View pane) {
        return pane.getLayoutParams().width;
    }

    private boolean isDrawerOpen() {
        return mController != null && mController.isDrawerOpen();
    }

    /**
     * @return Whether or not the conversation list is visible on screen.
     */
    @Deprecated
    public boolean isConversationListCollapsed() {
        return !ViewMode.isListMode(mCurrentMode) && !mShouldShowPreviewPanel;
    }

    @Override
    public void onViewModeChanged(int newMode) {
        // make all initially GONE panes visible only when the view mode is first determined
        if (mCurrentMode == ViewMode.UNKNOWN) {
            mFoldersView.setVisibility(VISIBLE);
            mListView.setVisibility(VISIBLE);
        }

        if (ViewMode.isAdMode(newMode)) {
            mMiscellaneousView.setVisibility(VISIBLE);
            mConversationView.setVisibility(GONE);
        } else {
            mConversationView.setVisibility(VISIBLE);
            mMiscellaneousView.setVisibility(GONE);
        }

        // detach the pager immediately from its data source (to prevent processing updates)
        if (ViewMode.isConversationMode(mCurrentMode)) {
            mController.disablePagerUpdates();
        }

        // notify of list visibility change up-front when going to list mode
        // (so the transition runs with the full TL in view)
        if (newMode == ViewMode.CONVERSATION_LIST) {
            dispatchConversationListVisibilityChange(true);
        }

        mCurrentMode = newMode;
        LogUtils.i(LOG_TAG, "onViewModeChanged(%d)", newMode);

        // If this is the first view mode change, we can't perform any translations yet because
        // the view doesn't have any measurements.
        final int width = getMeasuredWidth();
        if (width != 0) {
            // On view mode changes, ensure that we animate the panes & notify visibility changes.
            if (mShouldShowPreviewPanel) {
                onTransitionComplete();
            } else {
                translateDueToViewMode(width, true /* animate */);
            }
        }
    }

    /**
     * This is only called in portrait mode since only view mode changes in portrait mode affect
     * the pane positioning. This should be called after every view mode change to ensure that
     * each pane are in their corresponding locations based on the view mode.
     * @param width the available width to position the panes.
     * @param animate whether to animate the translation or not.
     */
    private void translateDueToViewMode(int width, boolean animate) {
        // Need to translate for CV mode
        if (ViewMode.isConversationMode(mCurrentMode) || ViewMode.isAdMode(mCurrentMode)) {
            final int translateWidth = mIsRtl ? width : -width;
            translatePanes(translateWidth, translateWidth, animate);
            adjustPaneVisibility(false /* folder */, false /* list */, true /* cv */);
        } else {
            translatePanes(0, 0, animate);
            adjustPaneVisibility(true /* folder */, true /* list */, false /* cv */);
        }
        // adjustPaneVisibility assumes onTransitionComplete will be called to finish setting the
        // visibility of disappearing panes.
        if (!animate) {
            onTransitionComplete();
        }
    }

    public boolean isModeChangePending() {
        return mTranslatedMode != mCurrentMode;
    }

    private void setPaneWidth(View pane, int w) {
        final ViewGroup.LayoutParams lp = pane.getLayoutParams();
        if (lp.width == w) {
            return;
        }
        lp.width = w;
        pane.setLayoutParams(lp);
        if (LogUtils.isLoggable(LOG_TAG, LogUtils.DEBUG)) {
            final String s;
            if (pane == mFoldersView) {
                s = "folders";
            } else if (pane == mListView) {
                s = "conv-list";
            } else if (pane == mConversationView) {
                s = "conv-view";
            } else if (pane == mMiscellaneousView) {
                s = "misc-view";
            } else if (pane == mConversationFrame) {
                s = "conv-misc-wrapper";
            } else {
                s = "???:" + pane;
            }
            LogUtils.d(LOG_TAG, "TPL: setPaneWidth, w=%spx pane=%s", w, s);
        }
    }

    public boolean shouldShowPreviewPanel() {
        return mShouldShowPreviewPanel;
    }

    private class PaneAnimationListener extends AnimatorListenerAdapter implements Runnable {

        @Override
        public void run() {
            onTransitionComplete();
        }

        @Override
        public void onAnimationStart(Animator animation) {
            // If we're running pre-K, we don't have ViewPropertyAnimator's setUpdateListener.
            // This is a hack to get around it and uses a dummy ValueAnimator to allow us
            // to create an animation for the shadow along with the list view.
            if (!Utils.isRunningKitkatOrLater()) {
                final ValueAnimator shadowAnimator = ValueAnimator.ofFloat(0, 1);
                shadowAnimator.setDuration(SLIDE_DURATION_MS)
                        .addUpdateListener(mListViewAnimationListener);
                shadowAnimator.start();
            }
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            onTransitionComplete();
        }

    }

}
