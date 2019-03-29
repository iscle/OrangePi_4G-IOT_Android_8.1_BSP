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
 * limitations under the License.
 */
package android.autofillservice.cts;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

/**
 * Simple activity that has an edit text and buttons to cancel or commit the autofill context.
 */
public class SimpleSaveActivity extends AbstractAutoFillActivity {

    static final String ID_LABEL = "label";
    static final String ID_INPUT = "input";
    static final String ID_PASSWORD = "password";
    static final String ID_COMMIT = "commit";
    static final String TEXT_LABEL = "Label:";

    TextView mLabel;
    EditText mInput;
    EditText mPassword;
    Button mCancel;
    Button mCommit;

    private static SimpleSaveActivity sInstance;

    public static SimpleSaveActivity getInstance() {
        return sInstance;
    }

    public SimpleSaveActivity() {
        sInstance = this;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.simple_save_activity);

        mLabel = findViewById(R.id.label);
        mInput = findViewById(R.id.input);
        mPassword = findViewById(R.id.password);
        mCancel = findViewById(R.id.cancel);
        mCommit = findViewById(R.id.commit);

        mCancel.setOnClickListener((v) -> getAutofillManager().cancel());
        mCommit.setOnClickListener((v) -> getAutofillManager().commit());
    }

    FillExpectation expectAutoFill(String input, String password) {
        final FillExpectation expectation = new FillExpectation(input, password);
        mInput.addTextChangedListener(expectation.mInputWatcher);
        mPassword.addTextChangedListener(expectation.mPasswordWatcher);
        return expectation;
    }

    final class FillExpectation {
        private final OneTimeTextWatcher mInputWatcher;
        private final OneTimeTextWatcher mPasswordWatcher;

        private FillExpectation(String input, String password) {
            mInputWatcher = new OneTimeTextWatcher("input", mInput, input);
            mPasswordWatcher = new OneTimeTextWatcher("password", mPassword, password);
        }

        void assertAutoFilled() throws Exception {
            mInputWatcher.assertAutoFilled();
            mPasswordWatcher.assertAutoFilled();
        }
    }
}
