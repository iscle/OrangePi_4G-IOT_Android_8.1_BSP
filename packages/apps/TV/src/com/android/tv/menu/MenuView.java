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
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewTreeObserver.OnGlobalFocusChangeListener;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.FrameLayout;

import com.android.tv.menu.Menu.MenuShowReason;

import java.util.ArrayList;
import java.util.List;

/**
 * A view that represents TV main menu.
 */
public class MenuView extends FrameLayout implements IMenuView {
    static final String TAG = MenuView.class.getSimpleName();
    static final boolean DEBUG = false;

    private final LayoutInflater mLayoutInflater;
    private final List<MenuRow> mMenuRows = new ArrayList<>();
    private final List<MenuRowView> mMenuRowViews = new ArrayList<>();

    @MenuShowReason private int mShowReason = Menu.REASON_NONE;

    private final MenuLayoutManager mLayoutManager;

    public MenuView(Context context) {
        this(context, null, 0);
    }

    public MenuView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MenuView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mLayoutInflater = LayoutInflater.from(context);
        // Set hardware layer type for smooth animation of lots of views.
        setLayerType(LAYER_TYPE_HARDWARE, null);
        getViewTreeObserver().addOnGlobalFocusChangeListener(new OnGlobalFocusChangeListener() {
            @Override
            public void onGlobalFocusChanged(View oldFocus, View newFocus) {
                MenuRowView newParent = getParentMenuRowView(newFocus);
                if (newParent != null) {
                    if (DEBUG) Log.d(TAG, "Focus changed to " + newParent);
                    // When the row is selected, the row view itself has the focus because the row
                    // is collapsed. To make the child of the row have the focus, requestFocus()
                    // should be called again after the row is expanded. It's done in
                    // setSelectedPosition().
                    setSelectedPositionSmooth(mMenuRowViews.indexOf(newParent));
                }
            }
        });
        mLayoutManager = new MenuLayoutManager(context, this);
    }

    @Override
    public void setMenuRows(List<MenuRow> menuRows) {
        mMenuRows.clear();
        mMenuRows.addAll(menuRows);
        for (MenuRow row : menuRows) {
            MenuRowView view = createMenuRowView(row);
            mMenuRowViews.add(view);
            addView(view);
        }
        mLayoutManager.setMenuRowsAndViews(mMenuRows, mMenuRowViews);
    }

    private MenuRowView createMenuRowView(MenuRow row) {
        MenuRowView view = (MenuRowView) mLayoutInflater.inflate(row.getLayoutResId(), this, false);
        view.onBind(row);
        row.setMenuRowView(view);
        return view;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        mLayoutManager.layout(left, top, right, bottom);
    }

    @Override
    public void onShow(@MenuShowReason int reason, String rowIdToSelect,
            final Runnable runnableAfterShow) {
        if (DEBUG) {
            Log.d(TAG, "onShow(reason=" + reason + ", rowIdToSelect=" + rowIdToSelect + ")");
        }
        mShowReason = reason;
        if (getVisibility() == VISIBLE) {
            if (rowIdToSelect != null) {
                int position = getItemPosition(rowIdToSelect);
                if (position >= 0) {
                    MenuRowView rowView = mMenuRowViews.get(position);
                    rowView.initialize(reason);
                    setSelectedPosition(position);
                }
            }
            return;
        }
        initializeChildren();
        update(true);
        int position = getItemPosition(rowIdToSelect);
        if (position == -1 || !mMenuRows.get(position).isVisible()) {
            // Channels row is always visible.
            position = getItemPosition(ChannelsRow.ID);
        }
        setSelectedPosition(position);
        // Change the visibility as late as possible to avoid the unnecessary animation.
        setVisibility(VISIBLE);
        // Make the selected row have the focus.
        requestFocus();
        if (runnableAfterShow != null) {
            getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    // Start show animation after layout finishes for smooth animation because the
                    // layout can take long time.
                    runnableAfterShow.run();
                }
            });
        }
        mLayoutManager.onMenuShow();
    }

    @Override
    public void onHide() {
        if (getVisibility() == GONE) {
            return;
        }
        mLayoutManager.onMenuHide();
        setVisibility(GONE);
    }

    @Override
    public boolean isVisible() {
        return getVisibility() == VISIBLE;
    }

    @Override
    public boolean update(boolean menuActive) {
        if (menuActive) {
            for (MenuRow row : mMenuRows) {
                row.update();
            }
            mLayoutManager.onMenuRowUpdated();
            return true;
        }
        return false;
    }

    @Override
    public boolean update(String rowId, boolean menuActive) {
        if (menuActive) {
            MenuRow row = getMenuRow(rowId);
            if (row != null) {
                row.update();
                mLayoutManager.onMenuRowUpdated();
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        int selectedPosition = mLayoutManager.getSelectedPosition();
        // When the menu shows up, the selected row should have focus.
        if (selectedPosition >= 0 && selectedPosition < mMenuRowViews.size()) {
            return mMenuRowViews.get(selectedPosition).requestFocus();
        }
        return super.onRequestFocusInDescendants(direction, previouslyFocusedRect);
    }

    @Override
    public void focusableViewAvailable(View v) {
        // Workaround of b/30788222 and b/32074688.
        // The re-layout of RecyclerView gives the focus to the card view even when the menu is not
        // visible. Don't report focusable view when the menu is not visible.
        if (getVisibility() == VISIBLE) {
            super.focusableViewAvailable(v);
        }
    }

    private void setSelectedPosition(int position) {
        mLayoutManager.setSelectedPosition(position);
    }

    private void setSelectedPositionSmooth(int position) {
        mLayoutManager.setSelectedPositionSmooth(position);
    }

    private void initializeChildren() {
        for (MenuRowView view : mMenuRowViews) {
            view.initialize(mShowReason);
        }
    }

    private MenuRow getMenuRow(String rowId) {
        for (MenuRow item : mMenuRows) {
            if (rowId.equals(item.getId())) {
                return item;
            }
        }
        return null;
    }

    private int getItemPosition(String rowIdToSelect) {
        if (rowIdToSelect == null) {
            return -1;
        }
        int position = 0;
        for (MenuRow item : mMenuRows) {
            if (rowIdToSelect.equals(item.getId())) {
                return position;
            }
            ++position;
        }
        return -1;
    }

    @Override
    public View focusSearch(View focused, int direction) {
        // The bounds of the views move and overlap with each other during the animation. In this
        // situation, the framework can't perform the correct focus navigation. So the menu view
        // should search by itself.
        if (direction == View.FOCUS_UP) {
            View newView = super.focusSearch(focused, direction);
            MenuRowView oldfocusedParent = getParentMenuRowView(focused);
            MenuRowView newFocusedParent = getParentMenuRowView(newView);
            int selectedPosition = mLayoutManager.getSelectedPosition();
            if (newFocusedParent != oldfocusedParent) {
                // The focus leaves from the current menu row view.
                for (int i = selectedPosition - 1; i >= 0; --i) {
                    MenuRowView view = mMenuRowViews.get(i);
                    if (view.getVisibility() == View.VISIBLE) {
                        return view;
                    }
                }
            }
            return newView;
        } else if (direction == View.FOCUS_DOWN) {
            View newView = super.focusSearch(focused, direction);
            MenuRowView oldfocusedParent = getParentMenuRowView(focused);
            MenuRowView newFocusedParent = getParentMenuRowView(newView);
            int selectedPosition = mLayoutManager.getSelectedPosition();
            if (newFocusedParent != oldfocusedParent) {
                // The focus leaves from the current menu row view.
                int count = mMenuRowViews.size();
                for (int i = selectedPosition + 1; i < count; ++i) {
                    MenuRowView view = mMenuRowViews.get(i);
                    if (view.getVisibility() == View.VISIBLE) {
                        return view;
                    }
                }
            }
            return newView;
        }
        return super.focusSearch(focused, direction);
    }

    private MenuRowView getParentMenuRowView(View view) {
        if (view == null) {
            return null;
        }
        ViewParent parent = view.getParent();
        if (parent == MenuView.this) {
            return (MenuRowView) view;
        }
        if (parent instanceof View) {
            return getParentMenuRowView((View) parent);
        }
        return null;
    }
}
