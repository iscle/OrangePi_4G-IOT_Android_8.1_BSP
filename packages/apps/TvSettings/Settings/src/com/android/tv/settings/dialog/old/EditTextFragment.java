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

import android.app.Fragment;
import android.os.Bundle;
import android.text.Editable;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.text.TextUtils;
import android.widget.TextView;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;

import com.android.tv.settings.R;

public class EditTextFragment extends Fragment
        implements TextWatcher, TextView.OnEditorActionListener {

    private static final String EXTRA_LAYOUT_RES_ID = "layout_res_id";
    private static final String EXTRA_EDIT_TEXT_RES_ID = "edit_text_res_id";
    private static final String EXTRA_DESC = "description";
    private static final String EXTRA_INITIAL_TEXT = "initialText";
    private static final String EXTRA_PASSWORD = "password";
    private TextWatcher mTextWatcher = null;
    private TextView.OnEditorActionListener mEditorActionListener = null;

    public static EditTextFragment newInstance(int layoutResId, int editTextResId) {
        EditTextFragment fragment = new EditTextFragment();
        Bundle args = new Bundle();
        if (layoutResId == 0 || editTextResId == 0) {
            throw new IllegalArgumentException("resource id must be valid values");
        }
        args.putInt(EXTRA_LAYOUT_RES_ID, layoutResId);
        args.putInt(EXTRA_EDIT_TEXT_RES_ID, editTextResId);
        fragment.setArguments(args);
        return fragment;
    }

    public static EditTextFragment newInstance(String description) {
        return newInstance(description, null);
    }

    public static EditTextFragment newInstance(String description, String initialText) {
        return newInstance(description, initialText, false);
    }

    public static EditTextFragment newInstance(String description, String initialText,
            boolean password) {
        EditTextFragment fragment = new EditTextFragment();
        Bundle args = new Bundle();
        args.putString(EXTRA_DESC, description);
        args.putString(EXTRA_INITIAL_TEXT, initialText);
        args.putBoolean(EXTRA_PASSWORD, password);
        fragment.setArguments(args);
        return fragment;

    }

    public void setTextWatcher(TextWatcher textWatcher) {
        mTextWatcher = textWatcher;
    }

    public void setOnEditorActionListener(TextView.OnEditorActionListener listener) {
        mEditorActionListener = listener;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = null;
        EditText editText = null;
        int layoutResId = getArguments().getInt(EXTRA_LAYOUT_RES_ID, R.layout.edittext_fragment);
        int editTextResId = getArguments().getInt(EXTRA_EDIT_TEXT_RES_ID, R.id.edittext);

        view = inflater.inflate(layoutResId, container, false);
        editText = (EditText) view.findViewById(editTextResId);

        String descString = getArguments().getString(EXTRA_DESC);
        if (!TextUtils.isEmpty(descString)) {
            TextView description = (TextView) view.findViewById(R.id.description);
            if (description != null) {
                description.setText(descString);
                description.setVisibility(View.VISIBLE);
            }
        }

        if (editText != null) {
            editText.setOnEditorActionListener(this);
            editText.addTextChangedListener(this);
            editText.requestFocus();
            String initialText = getArguments().getString(EXTRA_INITIAL_TEXT);
            if(!TextUtils.isEmpty(initialText)) {
                editText.setText(initialText);
                editText.setSelection(initialText.length());
            }
            if (getArguments().getBoolean(EXTRA_PASSWORD, false)) {
                editText.setTransformationMethod(PasswordTransformationMethod.getInstance());
            }
        }
        return view;
    }

    @Override
    public void afterTextChanged(Editable s) {
        if (mTextWatcher != null) {
            mTextWatcher.afterTextChanged(s);
        }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        if (mTextWatcher != null) {
            mTextWatcher.beforeTextChanged(s, start, count, after);
        }
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (mTextWatcher != null) {
            mTextWatcher.onTextChanged(s, start, before, count);
        }
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (mEditorActionListener != null) {
            return mEditorActionListener.onEditorAction(v, actionId, event);
        } else {
            return false;
        }
    }
}
