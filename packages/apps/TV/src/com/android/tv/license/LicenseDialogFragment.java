/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tv.license;

import android.app.DialogFragment;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.TextView;

import com.android.tv.R;
import com.android.tv.dialog.SafeDismissDialogFragment;

/** A DialogFragment that shows a License in a text view. */
public class LicenseDialogFragment extends SafeDismissDialogFragment {
    public static final String DIALOG_TAG = LicenseDialogFragment.class.getSimpleName();

    private static final String LICENSE = "LICENSE";

    private License mLicense;
    private String mTrackerLabel;

    /**
     * Create a new LicenseDialogFragment to show a particular license.
     *
     * @param license The License to show.
     */
    public static LicenseDialogFragment newInstance(License license) {
        LicenseDialogFragment f = new LicenseDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(LICENSE, license);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mLicense = getArguments().getParcelable(LICENSE);
        String title = mLicense.getLibraryName();
        mTrackerLabel = getArguments().getString(title + "_license");
        int style =
                TextUtils.isEmpty(title)
                        ? DialogFragment.STYLE_NO_TITLE
                        : DialogFragment.STYLE_NORMAL;
        setStyle(style, 0);
    }

    @Nullable
    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        TextView textView = new TextView(getActivity());
        String licenseText = Licenses.getLicenseText(getContext(), mLicense);
        textView.setText(licenseText != null ? licenseText : "");
        textView.setMovementMethod(new ScrollingMovementMethod());
        int verticalOverscan =
                getResources().getDimensionPixelSize(R.dimen.vertical_overscan_safe_margin);
        int horizontalOverscan =
                getResources().getDimensionPixelSize(R.dimen.horizontal_overscan_safe_margin);
        textView.setPadding(
                horizontalOverscan, verticalOverscan, horizontalOverscan, verticalOverscan);
        return textView;
    }

    @Override
    public void onStart() {
        super.onStart();
        // Ensure the dialog is fullscreen, even if the TextView doesn't have its content yet.
        getDialog().getWindow().setLayout(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        getDialog().setTitle(mLicense.getLibraryName());
    }

    @Override
    public String getTrackerLabel() {
        return mTrackerLabel;
    }
}
