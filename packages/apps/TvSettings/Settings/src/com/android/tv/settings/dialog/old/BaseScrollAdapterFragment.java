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
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewPropertyAnimator;
import android.view.animation.DecelerateInterpolator;

import com.android.tv.settings.R;
import com.android.tv.settings.widget.ScrollAdapter;
import com.android.tv.settings.widget.ScrollAdapterView;
import com.android.tv.settings.widget.ScrollAdapterView.OnScrollListener;

/**
 * Fragment which uses a ScrollAdapterView as its basic UI.
 * <p>
 * This fragment is meant to be inserted as the action fragment into a {@link DialogActivity}.
 * <p>
 * Users can either subclass this or just use listener objects to receive life cycle events.
 */
public class BaseScrollAdapterFragment implements OnScrollListener {
    private static final String STATE_SELECTION = "BaseScrollAdapterFragment.selection";
    private int mAnimationDuration;
    private final LiteFragment mFragment;

    private ScrollAdapter mAdapter;
    private ScrollAdapterView mScrollAdapterView;
    private View mSelectorView;
    private View mSelectedView = null;
    private volatile boolean mFadedOut = true;

    public BaseScrollAdapterFragment(LiteFragment fragment) {
        mFragment = fragment;
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.settings_list, container, false);
        mScrollAdapterView = null;
        return v;
    }

    public void onSaveInstanceState(Bundle outState) {
        if (mScrollAdapterView != null) {
            outState.putInt(STATE_SELECTION, getSelectedItemPosition());
        }
    }

    public void onViewCreated(View view, Bundle savedInstanceState) {
        ensureList();
        if (savedInstanceState != null) {
            setSelection(savedInstanceState.getInt(STATE_SELECTION, 0));
        }
    }

    public boolean hasCreatedView() {
        return mFragment != null && mFragment.getView() != null;
    }

    public ScrollAdapterView getScrollAdapterView() {
        ensureList();
        return mScrollAdapterView;
    }

    public ScrollAdapter getAdapter() {
        return mAdapter;
    }

    public void setAdapter(ScrollAdapter adapter) {
        mAdapter = adapter;
        if (mScrollAdapterView != null) {
            mScrollAdapterView.setAdapter(mAdapter);
        }
    }

    public void setSelection(int position) {
        mScrollAdapterView.setSelection(position);
    }

    public void setSelectionSmooth(int position) {
        mScrollAdapterView.setSelectionSmooth(position);
    }

    public int getSelectedItemPosition() {
        return mScrollAdapterView.getSelectedItemPosition();
    }

    public void ensureList() {
        if (mScrollAdapterView != null) {
            return;
        }
        View root = mFragment.getView();
        if (root == null) {
            throw new IllegalStateException("Content view not created yet.");
        }
        if (root instanceof ScrollAdapterView) {
            mScrollAdapterView = (ScrollAdapterView) root;
            mSelectorView = null;
        } else {
            mScrollAdapterView = (ScrollAdapterView) root.findViewById(R.id.list);
            if (mScrollAdapterView == null) {
                throw new IllegalStateException("No scroll adapter view exists.");
            }
            mSelectorView = root.findViewById(R.id.selector);
        }
        if (mScrollAdapterView != null) {
            mScrollAdapterView.requestFocusFromTouch();
            if (mAdapter != null) {
                mScrollAdapterView.setAdapter(mAdapter);
            }
            if (mSelectorView != null) {
                mAnimationDuration = mFragment.getActivity()
                        .getResources().getInteger(R.integer.dialog_animation_duration);
                mScrollAdapterView.addOnScrollListener(this);
            }
        }
    }

    /**
     * Sets {@link BaseScrollAdapterFragment#mFadedOut}
     *
     * {@link BaseScrollAdapterFragment#mFadedOut} is true, iff
     * {@link BaseScrollAdapterFragment#mSelectorView} has an alpha of 0
     * (faded out). If false the view either has an alpha of 1 (visible) or
     * is in the process of animating.
     */
    private class Listener implements Animator.AnimatorListener {
        private final boolean mFadingOut;
        private boolean mCanceled;

        public Listener(boolean fadingOut) {
            mFadingOut = fadingOut;
        }

        @Override
        public void onAnimationStart(Animator animation) {
            if(!mFadingOut) {
                mFadedOut = false;
            }
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            if (!mCanceled && mFadingOut) {
                mFadedOut = true;
            }
        }

        @Override
        public void onAnimationCancel(Animator animation) {
            mCanceled = true;
        }

        @Override
        public void onAnimationRepeat(Animator animation) {
        }
    }

    // We want to fade in the selector if we've stopped scrolling on it (mainPosition = 0).
    // If mainPosition is not 0, we want to ensure to dim the selector if we haven't already.
    // we dim the last highlighted view so that while a user is scrolling, nothing is highlighted.
    @Override
    public synchronized void onScrolled(View view, int position, float mainPosition,
            float secondPosition) {
        boolean hasFocus = (mainPosition == 0.0);
        if (hasFocus) {
            if (view != null) {
                // The selector starts with a height of 0. In order to scale up
                // from
                // 0 we first need the set the height to 1 and scale form there.
                int selectorHeight = mSelectorView.getHeight();
                if (selectorHeight == 0) {
                    LayoutParams lp = mSelectorView.getLayoutParams();
                    lp.height = selectorHeight = mFragment.getActivity().getResources()
                            .getDimensionPixelSize(R.dimen.action_fragment_selector_min_height);
                    mSelectorView.setLayoutParams(lp);
                }
                float scaleY = (float) view.getHeight() / selectorHeight;
                ViewPropertyAnimator animation = mSelectorView.animate()
                        .alpha(1f)
                        .setListener(new Listener(false))
                        .setDuration(mAnimationDuration)
                        .setInterpolator(new DecelerateInterpolator(2f));
                if (mFadedOut) {
                    // selector is completely faded out, so we can just scale
                    // before fading in.
                    mSelectorView.setScaleY(scaleY);
                } else {
                    // selector is not faded out, so we must animate the scale
                    // as we fade in.
                    animation.scaleY(scaleY);
                }
                animation.start();
                mSelectedView = view;
            } else {
                LayoutParams lp = mSelectorView.getLayoutParams();
                lp.height = 0;
                mSelectorView.setLayoutParams(lp);
            }
        } else if (mSelectedView != null) {
            mSelectorView.animate()
                    .alpha(0f)
                    .setDuration(mAnimationDuration)
                    .setInterpolator(new DecelerateInterpolator(2f))
                    .setListener(new Listener(true))
                    .start();
            mSelectedView = null;
        }
    }
}
