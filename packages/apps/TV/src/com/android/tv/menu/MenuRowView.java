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

package com.android.tv.menu;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.tv.R;
import com.android.tv.menu.Menu.MenuShowReason;

public abstract class MenuRowView extends LinearLayout {
    private static final String TAG = "MenuRowView";
    private static final boolean DEBUG = false;

    private TextView mTitleView;
    private View mContentsView;

    private final float mTitleViewAlphaDeselected;
    private final float mTitleViewScaleSelected;

    /**
     * The lastly focused view. It is used to keep the focus while navigating the menu rows and
     * reset when the menu is popped up.
     */
    private View mLastFocusView;
    private MenuRow mRow;

    private final OnFocusChangeListener mOnFocusChangeListener = new OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            onChildFocusChange(v, hasFocus);
        }
    };

    /**
     * Returns the alpha value of the title view when it's deselected.
     */
    public float getTitleViewAlphaDeselected() {
        return mTitleViewAlphaDeselected;
    }

    /**
     * Returns the scale value of the title view when it's selected.
     */
    public float getTitleViewScaleSelected() {
        return mTitleViewScaleSelected;
    }

    public MenuRowView(Context context) {
        this(context, null);
    }

    public MenuRowView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MenuRowView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public MenuRowView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        Resources res = context.getResources();
        TypedValue outValue = new TypedValue();
        res.getValue(R.dimen.menu_row_title_alpha_deselected, outValue, true);
        mTitleViewAlphaDeselected = outValue.getFloat();
        float textSizeSelected =
                res.getDimensionPixelSize(R.dimen.menu_row_title_text_size_selected);
        float textSizeDeselected =
                res.getDimensionPixelSize(R.dimen.menu_row_title_text_size_deselected);
        mTitleViewScaleSelected = textSizeSelected / textSizeDeselected;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mTitleView = (TextView) findViewById(R.id.title);
        mContentsView = findViewById(getContentsViewId());
        if (mContentsView.isFocusable()) {
            mContentsView.setOnFocusChangeListener(mOnFocusChangeListener);
        }
        if (mContentsView instanceof ViewGroup) {
            setOnFocusChangeListenerToChildren((ViewGroup) mContentsView);
        }
        // Make contents view invisible in order that the view participates in the initial layout.
        // The visibility is set to GONE after the first layout finishes.
        // If not, we can't see the contents view animation for the first time it is shown.
        // TODO: Find a better way to resolve this issue.
        mContentsView.setVisibility(INVISIBLE);
    }

    private void setOnFocusChangeListenerToChildren(ViewGroup parent) {
        int childCount = parent.getChildCount();
        for (int i = 0; i < childCount; ++i) {
            View child = parent.getChildAt(i);
            if (child.isFocusable()) {
                child.setOnFocusChangeListener(mOnFocusChangeListener);
            }
            if (child instanceof ViewGroup) {
                setOnFocusChangeListenerToChildren((ViewGroup) child);
            }
        }
    }

    abstract protected int getContentsViewId();

    /**
     * Returns the title view.
     */
    public final TextView getTitleView() {
        return mTitleView;
    }

    /**
     * Returns the contents view.
     */
    public final View getContentsView() {
        return mContentsView;
    }

    /**
     * Initialize this view. e.g. Set the initial selection.
     * This method is called when the main menu is visible.
     * Subclass of {@link MenuRowView} should override this to set correct mLastFocusView.
     *
     * @param reason A reason why this is initialized. See {@link MenuShowReason}
     */
    public void initialize(@MenuShowReason int reason) {
        mLastFocusView = null;
    }

    protected Menu getMenu() {
        return mRow == null ? null : mRow.getMenu();
    }

    public void onBind(MenuRow row) {
        if (DEBUG) Log.d(TAG, "onBind: row=" + row);
        mRow = row;
        mTitleView.setText(row.getTitle());
    }

    @Override
    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        // Expand view here so initial focused item can be shown.
        return getInitialFocusView().requestFocus();
    }

    @NonNull
    private View getInitialFocusView() {
        if (mLastFocusView == null) {
            return mContentsView;
        }
        return mLastFocusView;
    }

    /**
     * Sets the view which needs to have focus when this row appears.
     * Subclasses should call this in {@link #initialize} if needed.
     */
    protected void setInitialFocusView(@NonNull View v) {
        mLastFocusView = v;
    }

    /**
     * Called when the focus of a child view is changed.
     * The inherited class should override this method instead of calling
     * {@link android.view.View#setOnFocusChangeListener(android.view.View.OnFocusChangeListener)}.
     */
    protected void onChildFocusChange(View v, boolean hasFocus) {
        if (hasFocus) {
            mLastFocusView = v;
        }
    }

    /**
     * Returns the ID of row object bound to this view.
     */
    public String getRowId() {
        return mRow == null ? null : mRow.getId();
    }

    /**
     * Called when this row is selected.
     *
     * @param showTitle If {@code true}, the title is not hidden immediately after the row is
     * selected even though hideTitleWhenSelected() is {@code true}.
     */
    public void onSelected(boolean showTitle) {
        if (mRow.hideTitleWhenSelected() && !showTitle) {
            // Title view should participate in the layout even though it is not visible.
            mTitleView.setVisibility(INVISIBLE);
        } else {
            mTitleView.setVisibility(VISIBLE);
            mTitleView.setAlpha(1.0f);
            mTitleView.setScaleX(mTitleViewScaleSelected);
            mTitleView.setScaleY(mTitleViewScaleSelected);
        }
        // Making the content view visible will cause it to set a focus item
        // So we store mLastFocusView and reset it
        View lastFocusView = mLastFocusView;
        mContentsView.setVisibility(VISIBLE);
        mLastFocusView = lastFocusView;
    }

    /**
     * Called when this row is deselected.
     */
    public void onDeselected() {
        mTitleView.setVisibility(VISIBLE);
        mTitleView.setAlpha(mTitleViewAlphaDeselected);
        mTitleView.setScaleX(1.0f);
        mTitleView.setScaleY(1.0f);
        mContentsView.setVisibility(GONE);
    }

    /**
     * Returns the preferred height of the contents view. The top/bottom padding is excluded.
     */
    public int getPreferredContentsHeight() {
        return mRow.getHeight();
    }
}
