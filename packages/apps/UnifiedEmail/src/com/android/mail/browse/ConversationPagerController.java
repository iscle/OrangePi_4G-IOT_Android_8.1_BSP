/*
 * Copyright (C) 2012 Google Inc.
 * Licensed to The Android Open Source Project.
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

package com.android.mail.browse;

import android.animation.AnimatorListenerAdapter;
import android.app.FragmentManager;
import android.content.Context;
import android.content.res.TypedArray;
import android.database.DataSetObservable;
import android.database.DataSetObserver;
import android.graphics.drawable.Drawable;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.view.ViewPropertyAnimator;

import com.android.mail.R;
import com.android.mail.graphics.PageMarginDrawable;
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.ui.AbstractActivityController;
import com.android.mail.ui.ActivityController;
import com.android.mail.ui.RestrictedActivity;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;

/**
 * A simple controller for a {@link ViewPager} of conversations.
 * <p>
 * Instead of placing a ViewPager in a Fragment that replaces the other app views, we leave a
 * ViewPager in the activity's view hierarchy at all times and have this controller manage it.
 * This allows the ViewPager to safely instantiate inner conversation fragments since it is not
 * itself contained in a Fragment (no nested fragments!).
 * <p>
 * This arrangement has pros and cons...<br>
 * pros: FragmentManager manages restoring conversation fragments, each conversation gets its own
 * LoaderManager<br>
 * cons: the activity's Controller has to specially handle show/hide conversation view,
 * conversation fragment transitions must be done manually
 * <p>
 * This controller is a small delegate of {@link AbstractActivityController} and shares its
 * lifetime.
 *
 */
public class ConversationPagerController {

    private ViewPager mPager;
    private ConversationPagerAdapter mPagerAdapter;
    private FragmentManager mFragmentManager;
    private ActivityController mActivityController;
    private boolean mShown;
    /**
     * True when the initial conversation passed to show() is busy loading. We assume that the
     * first {@link #onConversationSeen()} callback is triggered by that initial
     * conversation, and unset this flag when first signaled. Side-to-side paging will not re-enable
     * this flag, since it's only needed for initial conversation load.
     */
    private boolean mInitialConversationLoading;
    private final DataSetObservable mLoadedObservable = new DataSetObservable();

    public static final String LOG_TAG = "ConvPager";

    /**
     * Enables an optimization to the PagerAdapter that causes ViewPager to initially load just the
     * target conversation, then when the conversation view signals that the conversation is loaded
     * and visible (via onConversationSeen), we switch to paged mode to load the left/right
     * adjacent conversations.
     * <p>
     * Should improve load times. It also works around an issue in ViewPager that always loads item
     * zero (with the fragment visibility hint ON) when the adapter is initially set.
     */
    private static final boolean ENABLE_SINGLETON_INITIAL_LOAD = false;

    /** Duration of pager.show(...)'s animation */
    private static final int SHOW_ANIMATION_DURATION = 300;

    public ConversationPagerController(RestrictedActivity activity,
            ActivityController controller) {
        mFragmentManager = activity.getFragmentManager();
        mPager = (ViewPager) activity.findViewById(R.id.conversation_pager);
        mActivityController = controller;
        setupPageMargin(activity.getActivityContext());
    }

    /**
     * Show the conversation pager for the given conversation and animate in if specified along
     * with given animation listener.
     * @param account current account
     * @param folder current folder
     * @param initialConversation conversation to display initially in pager
     * @param changeVisibility true if we need to make the pager appear
     * @param pagerAnimationListener animation listener for pager fade-in, null indicates no
     *                               animation should take place
     */
    public void show(Account account, Folder folder, Conversation initialConversation,
            boolean changeVisibility, AnimatorListenerAdapter pagerAnimationListener) {
        mInitialConversationLoading = true;

        if (mShown) {
            LogUtils.d(LOG_TAG, "IN CPC.show, but already shown");
            // optimize for the case where account+folder are the same, when we can just shift
            // the existing pager to show the new conversation
            // If in detached mode, don't do this optimization
            if (mPagerAdapter != null && mPagerAdapter.matches(account, folder)
                    && !mPagerAdapter.isDetached()) {
                final int pos = mPagerAdapter.getConversationPosition(initialConversation);
                if (pos >= 0) {
                    setCurrentItem(pos);
                    return;
                }
            }
            // unable to shift, destroy existing state and fall through to normal startup
            cleanup();
        }

        if (changeVisibility) {
            // If we have a pagerAnimationListener, go ahead and animate
            if (pagerAnimationListener != null) {
                // Reset alpha to 0 before animating/making it visible
                mPager.setAlpha(0f);
                mPager.setVisibility(View.VISIBLE);

                // Fade in pager to full visibility - this can be cancelled mid-animation
                mPager.animate().alpha(1f)
                        .setDuration(SHOW_ANIMATION_DURATION).setListener(pagerAnimationListener);

            // Otherwise, make the pager appear without animation
            } else {
                // In case pager animation was cancelled and alpha value was not reset,
                // ensure that the pager is completely visible for a non-animated pager.show
                mPager.setAlpha(1f);
                mPager.setVisibility(View.VISIBLE);
            }
        }

        mPagerAdapter = new ConversationPagerAdapter(mPager.getContext(), mFragmentManager,
                account, folder, initialConversation);
        mPagerAdapter.setSingletonMode(ENABLE_SINGLETON_INITIAL_LOAD);
        mPagerAdapter.setActivityController(mActivityController);
        mPagerAdapter.setPager(mPager);
        LogUtils.d(LOG_TAG, "IN CPC.show, adapter=%s", mPagerAdapter);

        Utils.sConvLoadTimer.mark("pager init");
        LogUtils.d(LOG_TAG, "init pager adapter, count=%d initialConv=%s adapter=%s",
                mPagerAdapter.getCount(), initialConversation, mPagerAdapter);
        mPager.setAdapter(mPagerAdapter);

        if (!ENABLE_SINGLETON_INITIAL_LOAD) {
            // FIXME: unnecessary to do this on restore. setAdapter will restore current position
            final int initialPos = mPagerAdapter.getConversationPosition(initialConversation);
            if (initialPos >= 0) {
                LogUtils.d(LOG_TAG, "*** pager fragment init pos=%d", initialPos);
                setCurrentItem(initialPos);
            }
        }
        Utils.sConvLoadTimer.mark("pager setAdapter");

        mShown = true;
    }

    /**
     * Hide the pager and cancel any running/pending animation
     * @param changeVisibility true if we need to make the pager disappear
     */
    public void hide(boolean changeVisibility) {
        if (!mShown) {
            LogUtils.d(LOG_TAG, "IN CPC.hide, but already hidden");
            return;
        }
        mShown = false;

        // Cancel any potential animations to avoid listener methods running when they shouldn't
        mPager.animate().cancel();

        if (changeVisibility) {
            mPager.setVisibility(View.GONE);
        }

        LogUtils.d(LOG_TAG, "IN CPC.hide, clearing adapter and unregistering list observer");
        mPager.setAdapter(null);
        cleanup();
    }

    /**
     * Part of a delicate dance to kill fragments on restore after rotation if
     * the device configuration no longer calls for them. You must call
     * {@link #show(Account, Folder, Conversation, boolean, boolean)} first, and you probably want
     * to call {@link #hide(boolean)} afterwards to finish the cleanup. See go/xqaxk. Sorry...
     *
     */
    public void killRestoredFragments() {
        mPagerAdapter.killRestoredFragments();
    }

    // Explicitly set the focus to the conversation pager, specifically the conv overlay.
    public void focusPager() {
        mPager.requestFocus();
    }

    private void setCurrentItem(int pos) {
        // disable onPageSelected notifications during this operation. that listener is only there
        // to update the rest of the app when the user swipes to another page.
        mPagerAdapter.enablePageChangeListener(false);
        mPager.setCurrentItem(pos);
        mPagerAdapter.enablePageChangeListener(true);
    }

    public boolean isInitialConversationLoading() {
        return mInitialConversationLoading;
    }

    public void onDestroy() {
        // need to release resources before a configuration change kills the activity and controller
        cleanup();
    }

    private void cleanup() {
        if (mPagerAdapter != null) {
            // stop observing the conversation list
            mPagerAdapter.setActivityController(null);
            mPagerAdapter.setPager(null);
            mPagerAdapter = null;
        }
    }

    public void onConversationSeen() {
        if (mPagerAdapter == null) {
            return;
        }

        // take the adapter out of singleton mode to begin loading the
        // other non-visible conversations
        if (mPagerAdapter.isSingletonMode()) {
            LogUtils.i(LOG_TAG, "IN pager adapter, finished loading primary conversation," +
                    " switching to cursor mode to load other conversations");
            mPagerAdapter.setSingletonMode(false);
        }

        if (mInitialConversationLoading) {
            mInitialConversationLoading = false;
            mLoadedObservable.notifyChanged();
        }
    }

    public void registerConversationLoadedObserver(DataSetObserver observer) {
        mLoadedObservable.registerObserver(observer);
    }

    public void unregisterConversationLoadedObserver(DataSetObserver observer) {
        mLoadedObservable.unregisterObserver(observer);
    }

    /**
     * Stops listening to changes to the adapter. This must be followed immediately by
     * {@link #hide(boolean)}.
     */
    public void stopListening() {
        if (mPagerAdapter != null) {
            mPagerAdapter.stopListening();
        }
    }

    private void setupPageMargin(Context c) {
        final TypedArray a = c.obtainStyledAttributes(new int[] {android.R.attr.listDivider});
        final Drawable divider = a.getDrawable(0);
        a.recycle();
        final int padding = c.getResources().getDimensionPixelOffset(
                R.dimen.conversation_page_gutter);
        final Drawable gutterDrawable = new PageMarginDrawable(divider, padding, 0, padding, 0,
                c.getResources().getColor(R.color.conversation_view_background_color));
        mPager.setPageMargin(gutterDrawable.getIntrinsicWidth() + 2 * padding);
        mPager.setPageMarginDrawable(gutterDrawable);
    }

}
