/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2016 Mopria Alliance, Inc.
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

package com.android.bips.ui;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.bips.R;
import com.android.bips.discovery.DiscoveredPrinter;
import com.android.bips.discovery.ManualDiscovery;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Allows the user to enter printer address manually
 */
class AddManualPrinterDialog extends AlertDialog implements TextWatcher,
        TextView.OnEditorActionListener, View.OnKeyListener {
    private static final String TAG = AddManualPrinterDialog.class.getSimpleName();
    private static final boolean DEBUG = false;

    /**
     * A regex that matches IP addresses and domain names like "192.168.1.101" and
     * "printer1.company.com"
     */
    private static final String NAME_IP_REGEX =
            "[a-zA-Z0-9]+(\\-[a-zA-Z0-9]+)*(\\.[a-zA-Z0-9]+(\\-[a-zA-Z0-9]+)*)*";
    private static final String HOSTNAME_REGEXP = "^" + NAME_IP_REGEX + "$";
    private static final Pattern HOSTNAME_PATTERN = Pattern.compile(HOSTNAME_REGEXP);

    private final ManualDiscovery mDiscovery;
    private TextView mHostnameView;
    private Button mAddButton;
    private ProgressBar mProgressBar;

    AddManualPrinterDialog(Context context, ManualDiscovery discovery) {
        super(context);
        mDiscovery = discovery;
    }

    @SuppressLint("InflateParams")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (DEBUG) Log.d(TAG, "onCreate");
        View view = getLayoutInflater().inflate(R.layout.manual_printer_add, null);
        setView(view);
        setTitle(R.string.add_manual_printer);
        setButton(AlertDialog.BUTTON_NEGATIVE, getContext().getString(android.R.string.cancel),
                (OnClickListener) null);
        setButton(AlertDialog.BUTTON_POSITIVE, getContext().getString(R.string.add),
                (OnClickListener) null);

        super.onCreate(savedInstanceState);
        mAddButton = getButton(AlertDialog.BUTTON_POSITIVE);
        mHostnameView = (TextView) findViewById(R.id.hostname);
        mProgressBar = (ProgressBar) findViewById(R.id.progress);

        mAddButton.setOnClickListener(view1 -> addPrinter());

        // Update add button as contents change
        mHostnameView.addTextChangedListener(this);
        mHostnameView.setOnEditorActionListener(this);
        mHostnameView.setOnKeyListener(this);

        // Force open keyboard if appropriate
        openKeyboard(mHostnameView);

        updateButtonState();
    }

    private void openKeyboard(TextView view) {
        Window window = getWindow();
        if (window != null) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }

        view.requestFocus();
        InputMethodManager imm = (InputMethodManager) getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
    }

    private void updateButtonState() {
        String hostname = mHostnameView.getText().toString();
        Matcher hostMatch = HOSTNAME_PATTERN.matcher(hostname);

        mAddButton.setEnabled(hostMatch.matches());
    }

    /** Attempt to add the printer based on current data */
    private void addPrinter() {
        // Disable other actions while we are checking
        mAddButton.setEnabled(false);
        mHostnameView.setEnabled(false);
        mProgressBar.setVisibility(View.VISIBLE);

        // Begin an attempt to add the printer
        mDiscovery.addManualPrinter(mHostnameView.getText().toString(),
                new ManualDiscovery.PrinterAddCallback() {
                    @Override
                    public void onFound(DiscoveredPrinter printer, boolean supported) {
                        if (supported) {
                            // Success case
                            dismiss();
                        } else {
                            error(getContext().getString(R.string.printer_not_supported));
                        }
                    }

                    @Override
                    public void onNotFound() {
                        error(getContext().getString(R.string.no_printer_found));
                    }
                });
    }

    /** Inform user of error and allow them to correct it */
    private void error(String message) {
        mProgressBar.setVisibility(View.GONE);
        mHostnameView.setError(message);
        mHostnameView.setEnabled(true);
        openKeyboard(mHostnameView);
    }

    @Override
    public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable editable) {
        updateButtonState();
    }

    @Override
    public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
        if (id == EditorInfo.IME_ACTION_DONE && mAddButton.isEnabled()) {
            addPrinter();
            return true;
        }
        return false;
    }

    @Override
    public boolean onKey(View view, int keyCode, KeyEvent keyEvent) {
        if (keyCode == KeyEvent.KEYCODE_ENTER && mAddButton.isEnabled()) {
            addPrinter();
            return true;
        }
        return false;
    }
}