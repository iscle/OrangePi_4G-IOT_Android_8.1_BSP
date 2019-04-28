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

package com.android.tv.ui.sidepanel;

import android.support.annotation.UiThread;
import android.view.View;
import android.view.ViewGroup;

@UiThread
public abstract class Item {
    private View mItemView;
    private boolean mEnabled = true;
    private boolean mClickable = true;

    public void setEnabled(boolean enabled) {
        if (mEnabled != enabled) {
            mEnabled = enabled;
            if (mItemView != null) {
                setEnabledInternal(mItemView, enabled);
            }
        }
    }

    /**
     * Sets the item to be clickable or not.
     */
    public void setClickable(boolean clickable) {
        mClickable = clickable;
        if (mItemView != null) {
            mItemView.setClickable(clickable);
        }
    }

    /**
     * Returns whether this item is enabled.
     */
    public boolean isEnabled() {
        return mEnabled;
    }

    public final void notifyUpdated() {
        if (mItemView != null) {
            onUpdate();
        }
    }

    protected abstract int getResourceId();

    protected void onBind(View view) {
        mItemView = view;
    }

    protected void onUnbind() {
        mItemView = null;
    }

    /**
     * Called after onBind is called and when {@link #notifyUpdated} is called.
     * {@link #notifyUpdated} is usually called by {@link SideFragment#notifyItemChanged} and
     * {@link SideFragment#notifyItemsChanged}.
     */
    protected void onUpdate() {
        setEnabledInternal(mItemView, mEnabled);
        mItemView.setClickable(mClickable);
    }

    protected abstract void onSelected();

    protected void onFocused() {
    }

    /**
     * Returns true if the item is bound, i.e., onBind is called.
     */
    protected boolean isBound() {
        return mItemView != null;
    }

    private void setEnabledInternal(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) view;
            int childCount = parent.getChildCount();
            for (int i = 0; i < childCount; ++i) {
                setEnabledInternal(parent.getChildAt(i), enabled);
            }
        }
    }
}