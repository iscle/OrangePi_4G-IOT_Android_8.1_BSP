/*
 * Copyright (C) 2014 Google Inc.
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

package com.android.mail.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.DrawableRes;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.utils.ViewUtils;

/**
 * Custom view for the action bar when search is displayed.
 */
public class MaterialSearchActionView extends LinearLayout implements TextWatcher,
        View.OnClickListener, TextView.OnEditorActionListener, View.OnKeyListener {
    // Dark drawables are used for when the search bar is visible (thus dark icon on light bg).
    // Light drawables are used when we are showing the default action bar.
    private Drawable mLightBgDrawable;
    private Drawable mDarkBgDrawable;
    private @DrawableRes int mLightBgBackDrawable;
    private @DrawableRes int mDarkBgBackDrawable;
    private @DrawableRes int mLightBgClearTextDrawable;
    private @DrawableRes int mDarkBgClearTextDrawable;
    private @DrawableRes int mLightBgVoiceDrawable;
    private @DrawableRes int mDarkBgVoiceDrawable;
    private int mLightBgTextColor;
    private int mDarkBgTextColor;

    private MaterialSearchViewController mController;
    private InputMethodManager mImm;
    private boolean mSupportVoice;
    private boolean mShowingClearButton;
    private boolean mAlignWithTl;

    private ImageView mBackButton;
    private EditText mQueryText;
    private ImageView mEndingButton;

    public MaterialSearchActionView(Context context) {
        this(context, null);
    }

    public MaterialSearchActionView(Context context, AttributeSet attrs) {
        super(context, attrs);

        final Resources res = getResources();
        mLightBgDrawable = new ColorDrawable(res.getColor(android.R.color.white));
        mDarkBgDrawable = new ColorDrawable(res.getColor(R.color.primary_color));
        mLightBgBackDrawable = R.drawable.ic_arrow_back_24dp_with_rtl;
        mDarkBgBackDrawable = R.drawable.ic_arrow_back_wht_24dp_with_rtl;
        mLightBgClearTextDrawable = R.drawable.ic_close_24dp;
        mDarkBgClearTextDrawable = R.drawable.ic_close_wht_24dp;
        mLightBgVoiceDrawable = R.drawable.ic_mic_24dp;
        mDarkBgVoiceDrawable = R.drawable.ic_mic_wht_24dp;
        mLightBgTextColor = res.getColor(R.color.search_query_text);
        mDarkBgTextColor = res.getColor(android.R.color.white);
    }

    // PUBLIC API
    public void setController(MaterialSearchViewController controller, String initialQuery,
            boolean supportVoice) {
        mController = controller;
        mQueryText.setText(initialQuery);
        mSupportVoice = supportVoice;
    }

    public void clearSearchQuery() {
        mQueryText.setText("");
    }

    public void focusSearchBar(boolean hasFocus) {
        if (hasFocus) {
            mQueryText.requestFocus();
            mImm.showSoftInput(mQueryText, 0);
        } else {
            mImm.hideSoftInputFromWindow(mQueryText.getWindowToken(), 0);
        }
    }

    public void adjustViewForTwoPaneLandscape(boolean alignWithTl, int xEnd) {
        mAlignWithTl = alignWithTl;
        final ViewGroup.LayoutParams params = getLayoutParams();
        if (alignWithTl) {
            setBackgroundDrawable(mDarkBgDrawable);
            mBackButton.setImageResource(mDarkBgBackDrawable);
            mQueryText.setTextColor(mDarkBgTextColor);

            if (ViewUtils.isViewRtl(this)) {
                int[] coords = new int[2];
                getLocationInWindow(coords);
                params.width = coords[0] + getWidth() - xEnd;
            } else {
                params.width = xEnd;
            }
        } else {
            setBackgroundDrawable(mLightBgDrawable);
            mBackButton.setImageResource(mLightBgBackDrawable);
            mQueryText.setTextColor(mLightBgTextColor);
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        }
        setupEndingButton(mQueryText.getText());
        setLayoutParams(params);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mImm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        mBackButton = (ImageView) findViewById(R.id.search_actionbar_back_button);
        mBackButton.setOnClickListener(this);
        mQueryText = (EditText) findViewById(R.id.search_actionbar_query_text);
        mQueryText.addTextChangedListener(this);
        mQueryText.setOnClickListener(this);
        mQueryText.setOnEditorActionListener(this);
        mQueryText.setOnKeyListener(this);
        // Disable CAB for search edittext
        mQueryText.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
            }
        });
        mEndingButton = (ImageView) findViewById(R.id.search_actionbar_ending_button);
        mEndingButton.setOnClickListener(this);
        setupEndingButton(mQueryText.getText());
    }

    private void setupEndingButton(CharSequence currentText) {
        final Resources res = getResources();
        if (!mSupportVoice || currentText.length() > 0) {
            if (mAlignWithTl) {
                mEndingButton.setImageResource(mDarkBgClearTextDrawable);
            } else {
                mEndingButton.setImageResource(mLightBgClearTextDrawable);
            }
            mEndingButton.setContentDescription(res.getString(R.string.search_clear_desc));
            mShowingClearButton = true;
        } else {
            if (mAlignWithTl) {
                mEndingButton.setImageResource(mDarkBgVoiceDrawable);
            } else {
                mEndingButton.setImageResource(mLightBgVoiceDrawable);
            }
            mEndingButton.setContentDescription(res.getString(R.string.search_voice_desc));
            mShowingClearButton = false;
        }
    }

    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
        // Only care about onTextChanged
    }

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
        mController.onQueryTextChanged(charSequence.toString());
        setupEndingButton(charSequence);
    }

    @Override
    public void afterTextChanged(Editable editable) {
        // Only care about onTextChanged
    }

    @Override
    public void onClick(View view) {
        if (view == mBackButton) {
            mController.onSearchCanceled();
        } else if (view == mEndingButton) {
            if (mShowingClearButton) {
                mQueryText.setText("");
                mController.showSearchActionBar(
                        MaterialSearchViewController.SEARCH_VIEW_STATE_VISIBLE);
            } else {
                mController.onVoiceSearch();
            }
        } else if (view == mQueryText) {
            mController.showSearchActionBar(MaterialSearchViewController.SEARCH_VIEW_STATE_VISIBLE);
        }
    }

    @Override
    public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
        if (actionId == EditorInfo.IME_ACTION_SEARCH) {
            mController.onSearchPerformed(mQueryText.getText().toString());
        }
        return false;
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        // Hardware keyboard doesn't represent Enter as Search through imeOptions, so we need to
        // capture them manually here.
        if (event.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_ENTER) {
            mController.onSearchPerformed(mQueryText.getText().toString());
        }
        return false;
    }
}
