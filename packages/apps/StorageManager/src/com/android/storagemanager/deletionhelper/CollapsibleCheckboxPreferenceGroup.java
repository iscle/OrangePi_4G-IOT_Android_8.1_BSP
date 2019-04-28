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

package com.android.storagemanager.deletionhelper;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceGroup;
import android.support.v7.preference.PreferenceViewHolder;
import android.util.AttributeSet;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Checkable;

import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.android.internal.annotations.VisibleForTesting;
import com.android.storagemanager.R;
import com.android.storagemanager.deletionhelper.DeletionType.LoadingStatus;

/**
 * CollapsibleCheckboxPreferenceGroup is a preference group that can be expanded or collapsed and
 * also has a checkbox.
 */
public class CollapsibleCheckboxPreferenceGroup extends PreferenceGroup implements
        View.OnClickListener {
    private boolean mCollapsed;
    private boolean mChecked;
    private TextView mTextView;
    private ProgressBar mProgressBar;
    private View mWidget;
    private boolean mLoaded;

    public CollapsibleCheckboxPreferenceGroup(Context context) {
        this(context, null);
    }

    public CollapsibleCheckboxPreferenceGroup(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.deletion_preference);
        setWidgetLayoutResource(R.layout.preference_widget_checkbox);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        View checkbox = holder.findViewById(com.android.internal.R.id.checkbox);
        mTextView = (TextView) holder.findViewById(android.R.id.summary);
        // Ensures that the color of the text is consistent with the checkbox having a tick or not
        mTextView.setActivated(mChecked);
        if (checkbox != null && checkbox instanceof Checkable) {
            ((Checkable) checkbox).setChecked(mChecked);

            // Expand the touch target by making the parent the touch target.
            View parent = (View) checkbox.getParent();
            parent.setClickable(true);
            parent.setFocusable(true);
            parent.setOnClickListener(this);
        }
        mProgressBar = (ProgressBar) holder.findViewById(R.id.progress_bar);
        mProgressBar.setVisibility(mLoaded ? View.GONE : View.VISIBLE);
        mWidget = holder.findViewById(android.R.id.widget_frame);
        mWidget.setVisibility(mLoaded ? View.VISIBLE : View.GONE);

        // CollapsibleCheckboxPreferenceGroup considers expansion to be its "longer-term
        // (activation) state."
        final ImageView imageView = (ImageView) holder.findViewById(android.R.id.icon);
        imageView.setActivated(!mCollapsed);
    }

    @Override
    public boolean addPreference(Preference p) {
        super.addPreference(p);
        p.setVisible(!isCollapsed());
        return true;
    }

    // The preference click handler.
    @Override
    protected void onClick() {
        super.onClick();
        setCollapse(!isCollapsed());
    }

    // The checkbox view click handler.
    @Override
    public void onClick(View v) {
        super.onClick();
        setChecked(!isChecked());

        // We need to find the CheckBox in the parent view that we are using as a touch target.
        // If we don't update it before onClick finishes, the accessibility gives invalid
        // responses.
        ViewGroup parent = (ViewGroup) v;
        View child =  parent.findViewById(com.android.internal.R.id.checkbox);
        Checkable checkable = (Checkable) child;
        checkable.setChecked(mChecked);
        // Causes text color change when activated to differentiate selected elements from
        // unselected elements.
        mTextView.setActivated(mChecked);
    }

    /**
     * Return if the view is collapsed.
     */
    public boolean isCollapsed() {
        return mCollapsed;
    }

    /**
     * Returns the checked state of the preference.
     */
    public boolean isChecked() {
        return mChecked;
    }

    /**
     * Sets the checked state and notifies listeners of the state change.
     */
    public void setChecked(boolean checked) {
        if (mChecked != checked) {
            mChecked = checked;

            callChangeListener(checked);
            notifyDependencyChange(shouldDisableDependents());
            notifyChanged();
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfoCompat info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setCheckable(true);
        info.setChecked(isChecked());
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();

        final SavedState myState = new SavedState(superState);
        myState.checked = isChecked();
        myState.collapsed = isCollapsed();
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        // Only restore the state if it is valid and our saved state.
        if (state == null || !SavedState.class.equals(state.getClass())) {
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());
        setChecked(myState.checked);
        setCollapse(myState.collapsed);
    }

    private void setCollapse(boolean isCollapsed) {
        if (mCollapsed == isCollapsed) {
            return;
        }

        mCollapsed = isCollapsed;
        setAllPreferencesVisibility(!isCollapsed);
        notifyChanged();
    }

    private void setAllPreferencesVisibility(boolean visible) {
        for (int i = 0; i < getPreferenceCount(); i++) {
            Preference p = getPreference(i);
            p.setVisible(visible);
        }
    }

    private static class SavedState extends BaseSavedState {
        boolean checked;
        boolean collapsed;

        public SavedState(Parcel source) {
            super(source);
            checked = source.readInt() != 0;
            collapsed = source.readInt() != 0;
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(checked ? 1 : 0);
            dest.writeInt(collapsed ? 1 : 0);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }

    @VisibleForTesting
    void switchSpinnerToCheckboxOrDisablePreference(long freeableBytes, int loadingStatus) {
        mLoaded = loadingStatus != LoadingStatus.LOADING;
        setEnabled(loadingStatus != LoadingStatus.EMPTY);
        if (!isEnabled()) {
            setChecked(false);
        }
        if (mProgressBar != null) {
            mProgressBar.setVisibility(View.GONE);
        }
        if (mWidget != null) {
            mWidget.setVisibility(View.VISIBLE);
        }
    }
}
