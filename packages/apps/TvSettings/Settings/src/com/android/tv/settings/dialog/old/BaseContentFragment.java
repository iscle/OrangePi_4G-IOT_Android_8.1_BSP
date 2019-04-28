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

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.accessibility.AccessibilityManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.tv.settings.R;
import com.android.tv.settings.widget.FrameLayoutWithShadows;

/**
 * This class exists to make extending both v4 fragments and regular fragments easy
 */
public class BaseContentFragment {

    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_BREADCRUMB = "breadcrumb";
    private static final String EXTRA_DESCRIPTION = "description";
    private static final String EXTRA_ICON_RESOURCE_ID = "iconResourceId";
    private static final String EXTRA_ICON_URI = "iconUri";
    private static final String EXTRA_ICON_BITMAP = "iconBitmap";
    private static final String EXTRA_ICON_BACKGROUND = "iconBackground";

    public static Bundle buildArgs(
            String title, String breadcrumb, String description, int iconResourceId,
            int backgroundColor) {
        return buildArgs(title, breadcrumb, description, iconResourceId, null, null,
                backgroundColor);
    }

    public static Bundle buildArgs(String title, String breadcrumb, String description, Uri iconUri,
            int backgroundColor) {
        return buildArgs(title, breadcrumb, description, 0, iconUri, null, backgroundColor);
    }

    public static Bundle buildArgs(String title, String breadcrumb, String description,
            Bitmap iconBitmap) {
        return buildArgs(title, breadcrumb, description, 0, null, iconBitmap, Color.TRANSPARENT);
    }

    private static Bundle buildArgs(
            String title, String breadcrumb, String description, int iconResourceId,
            Uri iconUri, Bitmap iconBitmap, int iconBackgroundColor) {
        Bundle args = new Bundle();
        args.putString(EXTRA_TITLE, title);
        args.putString(EXTRA_BREADCRUMB, breadcrumb);
        args.putString(EXTRA_DESCRIPTION, description);
        args.putInt(EXTRA_ICON_RESOURCE_ID, iconResourceId);
        args.putParcelable(EXTRA_ICON_URI, iconUri);
        args.putParcelable(EXTRA_ICON_BITMAP, iconBitmap);
        args.putInt(EXTRA_ICON_BACKGROUND, iconBackgroundColor);
        return args;
    }

    private final LiteFragment mFragment;
    private Activity mActivity;
    private String mTitle;
    private String mBreadcrumb;
    private String mDescription;
    private int mIconResourceId;
    private Bitmap mIconBitmap;
    private int mIconBackgroundColor;
    private AccessibilityManager mAccessManager;

    public BaseContentFragment(LiteFragment fragment) {
        mFragment = fragment;
    }

    public void onCreate(Bundle savedInstanceState) {
        Bundle state = (savedInstanceState != null) ? savedInstanceState : mFragment.getArguments();
        if (mTitle == null) {
            mTitle = state.getString(EXTRA_TITLE);
        }
        if (mBreadcrumb == null) {
            mBreadcrumb = state.getString(EXTRA_BREADCRUMB);
        }
        if (mDescription == null) {
            mDescription = state.getString(EXTRA_DESCRIPTION);
        }
        if (mIconResourceId == 0) {
            mIconResourceId = state.getInt(EXTRA_ICON_RESOURCE_ID, 0);
        }
        if (mIconBitmap == null) {
            mIconBitmap = state.getParcelable(EXTRA_ICON_BITMAP);
        }
        if (mIconBackgroundColor == Color.TRANSPARENT) {
            mIconBackgroundColor = state.getInt(EXTRA_ICON_BACKGROUND, Color.TRANSPARENT);
        }
    }

    public void onSaveInstanceState(Bundle outState) {
        outState.putString(EXTRA_TITLE, mTitle);
        outState.putString(EXTRA_BREADCRUMB, mBreadcrumb);
        outState.putString(EXTRA_DESCRIPTION, mDescription);
        outState.putInt(EXTRA_ICON_RESOURCE_ID, mIconResourceId);
        outState.putParcelable(EXTRA_ICON_BITMAP, mIconBitmap);
        outState.putInt(EXTRA_ICON_BACKGROUND, mIconBackgroundColor);
    }

    /**
     * Pass activity from ContentFragment to BaseContentFragment when it is
     * attached.
     */
    public void onAttach(Activity activity) {
        mActivity = activity;
    }

    /**
     * Rest BaseContentFragment mActivity to null when ContentFragment is
     * detached.
     */
    public void onDetach() {
        mActivity = null;
    }

    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.content_fragment, container, false);
        setText(view, R.id.title, mTitle);
        setText(view, R.id.breadcrumb, mBreadcrumb);
        setText(view, R.id.description, mDescription);
        int iconResourceId = getIconResourceId();
        final ImageView iconImageView = (ImageView) view.findViewById(R.id.icon);
        int iconBackground = getIconBackgroundColor();
        if (iconBackground != Color.TRANSPARENT) {
            iconImageView.setBackgroundColor(iconBackground);
        }

        if (iconResourceId != 0) {
            iconImageView.setImageResource(iconResourceId);
            addShadow(iconImageView, view);
            updateViewSize(iconImageView);
        } else {
            Bitmap iconBitmap = getIconBitmap();
            if (iconBitmap != null) {
                iconImageView.setImageBitmap(iconBitmap);
                addShadow(iconImageView, view);
                updateViewSize(iconImageView);
            } else {
                iconImageView.setVisibility(View.GONE);
            }
        }

        return view;
    }

    public ImageView getIcon() {
        if (mFragment.getView() == null) return null;
        return (ImageView) mFragment.getView().findViewById(R.id.icon);
    }

    public TextView getTitle() {
        if (mFragment.getView() == null) return null;
        return (TextView) mFragment.getView().findViewById(R.id.title);
    }

    public int getIconResourceId() {
        return mIconResourceId;
    }

    public Bitmap getIconBitmap() {
        return mIconBitmap;
    }

    public int getIconBackgroundColor() {
        return mIconBackgroundColor;
    }

    public RelativeLayout getRoot() {
        return (RelativeLayout) mFragment.getView();
    }

    public TextView getBreadCrumb() {
        if (mFragment.getView() == null) return null;
        return (TextView) mFragment.getView().findViewById(R.id.breadcrumb);
    }

    public TextView getDescription() {
        if (mFragment.getView() == null) return null;
        return (TextView) mFragment.getView().findViewById(R.id.description);
    }

    public void setTextToExtra(View parent, int textViewResourceId,
            String extraLabel) {
        String text = mFragment.getArguments().getString(extraLabel, null);
        setText(parent, textViewResourceId, text);
    }

    public void setTextToExtra(int textViewResourceId, String extraLabel) {
        if (mFragment.getView() == null) return;
        setTextToExtra(mFragment.getView(), textViewResourceId, extraLabel);
    }

    public void setText(View parent, int textViewResourceId, String text) {
        TextView textView = (TextView) parent.findViewById(textViewResourceId);
        if (textView != null && text != null) {
            textView.setText(text);

            // Enable focusable title and description if accessibility is enabled.
            if (mActivity != null) {
                if (mAccessManager == null) {
                    mAccessManager = (AccessibilityManager) mActivity
                            .getSystemService(Context.ACCESSIBILITY_SERVICE);
                }
                if (mAccessManager.isEnabled()) {
                    textView.setFocusable(true);
                    textView.setFocusableInTouchMode(true);
                }
            }
        }
    }

    public void setText(int textViewResourceId, String text) {
        if (mFragment.getView() == null) return;
        setText(mFragment.getView(), textViewResourceId, text);
    }

    public void setTitleText(String text) {
        mTitle = text;
        if (mFragment.getView() == null) return;
        setText(mFragment.getView(), R.id.title, text);
    }

    public void setBreadCrumbText(String text) {
        mBreadcrumb = text;
        if (mFragment.getView() == null) return;
        setText(mFragment.getView(), R.id.breadcrumb, text);
    }

    public void setDescriptionText(String text) {
        mDescription = text;
        if (mFragment.getView() == null) return;
        setText(mFragment.getView(), R.id.description, text);
    }

    /**
     * Unlike {@link #setIcon(int)}, this will only work if called
     * after {@link #onCreateView(LayoutInflater, ViewGroup, Bundle)}.
     * @param iconDrawable
     */
    public void setIcon(Drawable iconDrawable) {
        if (mFragment.getView() == null) return;

        final ImageView iconImageView = (ImageView) mFragment.getView().findViewById(R.id.icon);
        if (iconImageView != null) {
            if (iconDrawable != null) {
                iconImageView.setImageDrawable(iconDrawable);
                iconImageView.setVisibility(View.VISIBLE);
                updateViewSize(iconImageView);
            }
        }
    }

    public void setIcon(int iconResourceId) {
        mIconResourceId = iconResourceId;
        if (mFragment.getView() == null) return;

        final ImageView iconImageView = (ImageView) mFragment.getView().findViewById(R.id.icon);
        if (iconImageView != null) {
            if (iconResourceId != 0) {
                iconImageView.setImageResource(iconResourceId);
                iconImageView.setVisibility(View.VISIBLE);
                updateViewSize(iconImageView);
            }
        }
    }

    private void updateViewSize(ImageView iconView) {
        int intrinsicWidth = iconView.getDrawable().getIntrinsicWidth();
        LayoutParams lp = iconView.getLayoutParams();
        if (intrinsicWidth > 0) {
            lp.height = lp.width * iconView.getDrawable().getIntrinsicHeight()
                    / intrinsicWidth;
        } else {
            // If no intrinsic width, then just mke this a square.
            lp.height = lp.width;
        }
    }

    private void addShadow(ImageView icon, View view) {
        FrameLayoutWithShadows shadowLayout = (FrameLayoutWithShadows)
                view.findViewById(R.id.shadow_layout);
        shadowLayout.addShadowView(icon);
    }

    private void fadeIn(View v) {
        v.setAlpha(0f);
        ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(v, "alpha", 1f);
        alphaAnimator.setDuration(mActivity.getResources().getInteger(
                android.R.integer.config_mediumAnimTime));
        alphaAnimator.start();
    }
}
