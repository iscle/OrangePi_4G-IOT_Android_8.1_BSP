/*******************************************************************************
 *      Copyright (C) 2014 Google Inc.
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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.support.v7.widget.Toolbar;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.analytics.Analytics;
import com.android.mail.providers.Account;
import com.android.mail.providers.AccountObserver;
import com.android.mail.utils.ViewUtils;

/**
 * Custom toolbar that supports a custom view so we can display our search icon wherever we want.
 */
public class CustomViewToolbar extends Toolbar implements ViewMode.ModeChangeListener,
        TwoPaneLayout.ConversationListLayoutListener {
    private static final long FADE_ANIMATION_DURATION_MS = 150;

    private ControllableActivity mActivity;
    private ActivityController mController;
    private ViewMode mViewMode;
    private AccountObserver mAccountObserver = new AccountObserver() {
        @Override
        public void onChanged(Account newAccount) {
            setSearchButtonVisibility(newAccount);
        }
    };

    protected View mCustomView;
    protected TextView mActionBarTitle;
    protected View mSearchButton;

    public CustomViewToolbar(Context context) {
        super(context);
    }

    public CustomViewToolbar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setController(ControllableActivity activity, ActivityController controller,
            ViewMode viewMode) {
        mActivity = activity;
        mController = controller;
        mViewMode = viewMode;
        mViewMode.addListener(this);

        mAccountObserver.initialize(mActivity.getAccountController());
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mCustomView = findViewById(R.id.actionbar_custom_view);
        mActionBarTitle = (TextView) findViewById(R.id.actionbar_title);
        mSearchButton = findViewById(R.id.actionbar_search_button);

        mSearchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Since search is no longer a menu item, log the search "menu" event here.
                Analytics.getInstance().sendEvent(Analytics.EVENT_CATEGORY_MENU_ITEM,
                        "search", "action_bar/" + mViewMode.getModeString(), 0);
                mController.startSearch();
            }
        });
    }

    protected void onDestroy() {
        mAccountObserver.unregisterAndDestroy();
    }

    /**
     * Sets the search button visibility based on the current account.
     */
    private void setSearchButtonVisibility() {
        setSearchButtonVisibility(mActivity.getAccountController().getAccount());
    }

    private void setSearchButtonVisibility(Account account) {
        if (mSearchButton != null) {
            final boolean visible = mController.shouldShowSearchMenuItem() &&
                    account.supportsSearch();
            mSearchButton.setVisibility(visible ? VISIBLE : INVISIBLE);
        }
    }

    @Override
    public void onViewModeChanged(int newMode) {
        setSearchButtonVisibility();
    }

    @Override
    public void onConversationListLayout(int xEnd, boolean drawerOpen) {
        if (drawerOpen) {
            mSearchButton.animate()
                    .alpha(0f)
                    .setDuration(FADE_ANIMATION_DURATION_MS)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            mSearchButton.setVisibility(INVISIBLE);
                        }
                    });
        } else {
            setSearchButtonVisibility();
            // setListener(null) is necessary because the animator carries the previously set
            // listener over, aka the listener for fade out.
            if (mSearchButton.isShown()) {
                mSearchButton.animate()
                        .alpha(1f)
                        .setDuration(FADE_ANIMATION_DURATION_MS)
                        .setListener(null);
            }

            final int[] coords = new int[2];
            mCustomView.getLocationInWindow(coords);
            final int newWidth;
            if (ViewUtils.isViewRtl(this)) {
                newWidth = coords[0] + mCustomView.getWidth() - xEnd;
            } else {
                newWidth = xEnd - coords[0];
            }

            // Only set the width if it's different than before so we avoid draw on layout pass.
            if (mCustomView.getWidth() != newWidth) {
                final ViewGroup.LayoutParams params = mCustomView.getLayoutParams();
                params.width = newWidth;
                mCustomView.setLayoutParams(params);
            }
        }
    }

    // OVERRIDE DEFAULT TOOLBAR TITLE FUNCTIONS SO THEY ARE RENDERED CORRECTLY.
    // TODO: subtitles? we currently don't have any of those, but we will need to support them.

    @Override
    public void setTitle(int resId) {
        setTitle(getResources().getString(resId));
    }

    @Override
    public void setTitle(CharSequence title) {
        mActionBarTitle.setText(title);
    }

    @Override
    public void setTitleTextAppearance(Context context, int resId) {
        mActionBarTitle.setTextAppearance(context, resId);
    }

    @Override
    public void setTitleTextColor(int color) {
        mActionBarTitle.setTextColor(color);
    }
}
