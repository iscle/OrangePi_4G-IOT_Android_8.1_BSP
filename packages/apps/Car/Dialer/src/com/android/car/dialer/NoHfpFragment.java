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
package com.android.car.dialer;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * A fragment that informs the user that there is no bluetooth device attached that can make
 * phone calls.
 */
public class NoHfpFragment extends Fragment {
    private static final String ERROR_MESSAGE_KEY = "ERROR_MESSAGE_KEY";

    private TextView mErrorMessageView;
    private String mErrorMessage;

    /**
     * Returns an instance of the {@link NoHfpFragment} with the given error message as the one to
     * display.
     */
    static NoHfpFragment newInstance(String errorMessage) {
        NoHfpFragment fragment = new NoHfpFragment();

        Bundle args = new Bundle();
        args.putString(ERROR_MESSAGE_KEY, errorMessage);
        fragment.setArguments(args);

        return fragment;
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        if (args != null) {
            mErrorMessage = args.getString(ERROR_MESSAGE_KEY);
        }
    }

    /**
     * Sets the given error message to be displayed.
     */
    void setErrorMessage(String errorMessage) {
        mErrorMessage = errorMessage;

        // If this method is called before the error message view is available, then no need to
        // set the message. Instead, it will be set in onCreateView().
        if (mErrorMessageView != null && !TextUtils.isEmpty(mErrorMessage)) {
            mErrorMessageView.setText(mErrorMessage);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.no_hfp, container, false);
        mErrorMessageView = v.findViewById(R.id.error_string);

        // If no error message is set, the default string from the layout will be used.
        if (!TextUtils.isEmpty(mErrorMessage)) {
            mErrorMessageView.setText(mErrorMessage);
        }

        return v;
    }
}
