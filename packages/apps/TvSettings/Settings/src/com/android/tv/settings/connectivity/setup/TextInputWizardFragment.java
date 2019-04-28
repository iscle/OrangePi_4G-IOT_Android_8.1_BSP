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

package com.android.tv.settings.connectivity.setup;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import com.android.tv.settings.R;
import com.android.tv.settings.util.AccessibilityHelper;

/**
 * Displays a UI for text input in the "wizard" style.
 * TODO: Merge with EditTextFragment
 */
public class TextInputWizardFragment extends Fragment {

    public static final int INPUT_TYPE_NORMAL = InputType.TYPE_CLASS_TEXT
            | InputType.TYPE_TEXT_VARIATION_NORMAL;
    public static final int INPUT_TYPE_PASSWORD = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            | InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
    public static final int INPUT_TYPE_EMAIL = InputType.TYPE_CLASS_TEXT
            | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
    public static final int INPUT_TYPE_NO_SUGGESTIONS = InputType.TYPE_CLASS_TEXT
            | InputType.TYPE_TEXT_VARIATION_NORMAL | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
    public static final int INPUT_TYPE_NUMERIC = InputType.TYPE_CLASS_NUMBER
            | InputType.TYPE_NUMBER_VARIATION_NORMAL;

    public interface Listener {
        /**
         * Called when text input is complete.
         *
         * @param text the text that was input.
         * @return true if the text is acceptable; false if not.
         */
        boolean onTextInputComplete(String text);
    }

    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_DESCRIPTION = "description";
    private static final String EXTRA_INPUT_TYPE = "input_type";
    private static final String EXTRA_PREFILL = "prefill";

    public static TextInputWizardFragment newInstance(
            String title, String description, int inputType, String prefill) {
        TextInputWizardFragment fragment = new TextInputWizardFragment();
        Bundle args = new Bundle();
        args.putString(EXTRA_TITLE, title);
        args.putString(EXTRA_DESCRIPTION, description);
        args.putInt(EXTRA_INPUT_TYPE, inputType);
        args.putString(EXTRA_PREFILL, prefill);
        fragment.setArguments(args);
        return fragment;
    }

    private Handler mHandler;
    private EditText mTextInput;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle icicle) {
        mHandler = new Handler();
        final View view = inflater.inflate(R.layout.account_content_area, container, false);

        final ViewGroup descriptionArea = (ViewGroup) view.findViewById(R.id.description);
        final View content = inflater.inflate(R.layout.wifi_content, descriptionArea, false);
        descriptionArea.addView(content);

        final ViewGroup actionArea = (ViewGroup) view.findViewById(R.id.action);
        final View action = inflater.inflate(R.layout.wifi_text_input, actionArea, false);
        actionArea.addView(action);

        TextView titleText = (TextView) content.findViewById(R.id.guidance_title);
        TextView descriptionText = (TextView) content.findViewById(R.id.guidance_description);
        mTextInput = (EditText) action.findViewById(R.id.text_input);

        Bundle args = getArguments();
        String title = args.getString(EXTRA_TITLE);
        String description = args.getString(EXTRA_DESCRIPTION);
        int inputType = args.getInt(EXTRA_INPUT_TYPE);
        String prefill = args.getString(EXTRA_PREFILL);

        boolean forceFocusable = AccessibilityHelper.forceFocusableViews(getActivity());
        if (title != null) {
            titleText.setText(title);
            titleText.setVisibility(View.VISIBLE);
            if (forceFocusable) {
                titleText.setFocusable(true);
                titleText.setFocusableInTouchMode(true);
            }
        } else {
            titleText.setVisibility(View.GONE);
        }

        if (description != null) {
            descriptionText.setText(description);
            descriptionText.setVisibility(View.VISIBLE);
            if (forceFocusable) {
                descriptionText.setFocusable(true);
                descriptionText.setFocusableInTouchMode(true);
            }
        } else {
            descriptionText.setVisibility(View.GONE);
        }

        if ((inputType & InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0) {
            mTextInput.setTransformationMethod(new PasswordTransformationMethod());
        }
        mTextInput.setInputType(inputType);

        if (prefill != null) {
            mTextInput.setText(prefill);
            mTextInput.setSelection(mTextInput.getText().length(), mTextInput.getText().length());
        }

        mTextInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (event == null || event.getAction() == KeyEvent.ACTION_UP) {
                    Activity a = getActivity();
                    if (a instanceof Listener) {
                        return ((Listener) a).onTextInputComplete(v.getText().toString());
                    }
                    return false;
                }
                return true;  // If we don't return true on ACTION_DOWN, we don't get the ACTION_UP.
            }
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Activity a = getActivity();
                if (a != null) {
                    InputMethodManager inputMethodManager = (InputMethodManager) a.getSystemService(
                            Context.INPUT_METHOD_SERVICE);
                    inputMethodManager.showSoftInput(mTextInput, 0);
                    mTextInput.requestFocus();
                }
            }
        });
    }

    @Override
    public void onPause() {
        InputMethodManager imm = (InputMethodManager) getActivity()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mTextInput.getWindowToken(), 0);
        super.onPause();
    }
}
