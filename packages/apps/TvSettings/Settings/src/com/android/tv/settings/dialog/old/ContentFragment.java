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

import android.app.Activity;
import android.app.Fragment;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * *************************
 *   DO NOT ADD LOGIC HERE!
 * *************************
 * This is a wrapper for {@link BaseContentFragment}. Place your logic in
 * there and call the function from here
 */
public class ContentFragment extends Fragment implements LiteFragment {

    private final BaseContentFragment mBase = new BaseContentFragment(this);

    public static ContentFragment newInstance(String title) {
        return newInstance(title, null, null, 0, Color.TRANSPARENT);
    }

    public static ContentFragment newInstance(String title, String breadcrumb,
            String description) {
        return newInstance(title, breadcrumb, description, 0, Color.TRANSPARENT);
    }

    public static ContentFragment newInstance(String title, String breadcrumb, String description,
            int iconResourceId) {
        return newInstance(title, breadcrumb, description, iconResourceId, Color.TRANSPARENT);
    }

    public static ContentFragment newInstance(String title, String breadcrumb, String description,
            int iconResourceId, int iconBackgroundColor) {
        ContentFragment fragment = new ContentFragment();
        fragment.setArguments(
                BaseContentFragment.buildArgs(title, breadcrumb, description, iconResourceId,
                        iconBackgroundColor));
        return fragment;
    }

    public static ContentFragment newInstance(String title, String breadcrumb, String description,
            Uri iconResourceUri) {
        return newInstance(title, breadcrumb, description, iconResourceUri, Color.TRANSPARENT);
    }

    public static ContentFragment newInstance(String title, String breadcrumb, String description,
            Uri iconResourceUri, int iconBackgroundColor) {
        ContentFragment fragment = new ContentFragment();
        fragment.setArguments(
                BaseContentFragment.buildArgs(title, breadcrumb, description, iconResourceUri,
                iconBackgroundColor));
        return fragment;
    }

    public static ContentFragment newInstance(String title, String breadcrumb, String description,
            Bitmap iconbitmap) {
        ContentFragment fragment = new ContentFragment();
        fragment.setArguments(BaseContentFragment.buildArgs(title, breadcrumb, description,
                iconbitmap));
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mBase.onCreate(savedInstanceState);
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        mBase.onSaveInstanceState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onAttach(Activity activity) {
        mBase.onAttach(activity);
        super.onAttach(activity);
    }

    @Override
    public void onDetach() {
        mBase.onDetach();
        super.onDetach();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return mBase.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    public ImageView getIcon() {
        return mBase.getIcon();
    }

    public TextView getTitle() {
        return mBase.getTitle();
    }

    public int getIconResourceId() {
        return mBase.getIconResourceId();
    }

    public Bitmap getIconBitmap() {
        return mBase.getIconBitmap();
    }

    public RelativeLayout getRoot() {
        return mBase.getRoot();
    }

    public TextView getBreadCrumb() {
        return mBase.getBreadCrumb();
    }

    public TextView getDescription() {
        return mBase.getDescription();
    }

    public void setTextToExtra(int textViewResourceId, String extraLabel) {
        mBase.setTextToExtra(textViewResourceId, extraLabel);
    }

    public void setText(int textViewResourceId, String text) {
        mBase.setText(textViewResourceId, text);
    }

    public void setTitleText(String text) {
        mBase.setTitleText(text);
    }

    public void setBreadCrumbText(String text) {
        mBase.setBreadCrumbText(text);
    }

    public void setDescriptionText(String text) {
        mBase.setDescriptionText(text);
    }

    public void setIcon(int iconResourceId) {
        mBase.setIcon(iconResourceId);
    }

    public void setIcon(Drawable iconDrawable) {
        mBase.setIcon(iconDrawable);
    }

    protected void setTextToExtra(View parent, int textViewResourceId, String extraLabel) {
        mBase.setTextToExtra(parent, textViewResourceId, extraLabel);
    }

    protected void setText(View parent, int textViewResourceId, String text) {
        mBase.setText(parent, textViewResourceId, text);
    }
}
