/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.car.cluster.sample;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Panel that responsible of holding cards.
 */
public class CardPanel extends FrameLayout {
    private final static String TAG = DebugUtil.getTag(CardPanel.class);

    private final List<View> mOrderedChildren = new ArrayList<>(10);
    private final Set<View> mViewsToBeRemoved = new HashSet<>();

    public CardPanel(Context context) {
        this(context, null);
    }

    public CardPanel(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CardPanel(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        setChildrenDrawingOrderEnabled(true);
    }

    @Override
    public void addView(View child, int index) {
        super.addView(child, index);
        if (index < 0) {
            mOrderedChildren.add(child);
        } else {
            mOrderedChildren.add(index, child);
        }
    }

    @Override
    public void removeView(View view) {
        super.removeView(view);

        mOrderedChildren.remove(view);
        mViewsToBeRemoved.remove(view);
    }

    /**
     * If we are removing view with animation, we do not want to treat this view as visible.
     */
    public void markViewToBeRemoved(View view) {
        mViewsToBeRemoved.add(view);
    }


    public boolean childViewExists(View child) {
        return indexOfChild(child) >= 0 && !mViewsToBeRemoved.contains(child);
    }

    /** Moves given child behind the top card */
    public void moveChildBehindTheTop(View child) {
        if (mOrderedChildren.size() <= 1) {
            return;
        }

        int newIndex = mOrderedChildren.size() - 2;
        int oldIndex = mOrderedChildren.indexOf(child);
        if (oldIndex == -1) {
            Log.e(TAG, "Child: " + child + " not found in "
                    + Arrays.toString(mOrderedChildren.toArray()));
            return;
        }
        if (newIndex == oldIndex) {
            return;
        }

        // Swap children.
        View tmpChild = mOrderedChildren.get(newIndex);
        mOrderedChildren.set(newIndex, child);
        mOrderedChildren.set(oldIndex, tmpChild);
    }

    public View getTopVisibleChild() {
        for (int i = mOrderedChildren.size() - 1; i >= 0; i--) {
            View child = mOrderedChildren.get(i);
            if (child.getVisibility() == VISIBLE && !mViewsToBeRemoved.contains(child)) {
                return child;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public <E> E getChildOrNull(Class<E> clazz) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (clazz.isInstance(child) && !mViewsToBeRemoved.contains(child)) {
                return (E) child;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public <E> E getVisibleChildOrNull(Class<E> clazz) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (clazz.isInstance(child) && !mViewsToBeRemoved.contains(child)
                    && child.getVisibility() == VISIBLE) {
                return (E) child;
            }
        }
        return null;
    }

    @Override
    protected int getChildDrawingOrder(int childCount, int i) {
        return indexOfChild(mOrderedChildren.get(i));
    }
}
