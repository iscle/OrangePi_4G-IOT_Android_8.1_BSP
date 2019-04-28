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
 * limitations under the License
 */

package com.android.tv.settings.dialog;

import android.app.Fragment;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.DrawableRes;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.tv.settings.R;

public class ProgressDialogFragment extends Fragment {

    private ImageView mIconView;
    private TextView mTitleView;
    private TextView mExtraTextView;
    private TextView mSummaryView;
    private ProgressBar mProgressBar;
    private int mWidth = -1;

    @Override
    public @Nullable View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            Bundle savedInstanceState) {
        final ViewGroup view =
                (ViewGroup) inflater.inflate(R.layout.progress_fragment, container, false);

        mIconView = (ImageView) view.findViewById(android.R.id.icon);
        mTitleView = (TextView) view.findViewById(android.R.id.title);
        mExtraTextView = (TextView) view.findViewById(R.id.extra);
        mSummaryView = (TextView) view.findViewById(android.R.id.summary);
        mProgressBar = (ProgressBar) view.findViewById(android.R.id.progress);

        if (mWidth != -1) {
            final ViewGroup.LayoutParams params = view.getLayoutParams();
            params.width = mWidth;
            view.setLayoutParams(params);
        }

        return view;
    }

    public void setIcon(@DrawableRes int resId) {
        mIconView.setImageResource(resId);
        mIconView.setVisibility(View.VISIBLE);
    }

    public void setIcon(@Nullable Drawable icon) {
        mIconView.setImageDrawable(icon);
        mIconView.setVisibility(icon == null ? View.GONE : View.VISIBLE);
    }

    public void setTitle(@StringRes int resId) {
        mTitleView.setText(resId);
    }

    public void setTitle(CharSequence title) {
        mTitleView.setText(title);
    }

    public void setExtraText(@StringRes int resId) {
        mExtraTextView.setText(resId);
    }

    public void setExtraText(CharSequence text) {
        mExtraTextView.setText(text);
        mExtraTextView.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
    }

    public void setSummary(@StringRes int resId) {
        mSummaryView.setText(resId);
    }

    public void setSummary(CharSequence summary) {
        mSummaryView.setText(summary);
    }

    public void setIndeterminte(boolean indeterminte) {
        mProgressBar.setIndeterminate(indeterminte);
    }

    public void setProgress(int progress) {
        mProgressBar.setProgress(progress);
    }

    public void setProgressMax(int max) {
        mProgressBar.setMax(max);
    }

    public void setContentWidth(int width) {
        mWidth = width;
        final View root = getView();
        if (root == null) {
            return;
        }
        final ViewGroup.LayoutParams params = root.getLayoutParams();
        params.width = width;
        root.setLayoutParams(params);
    }
}
