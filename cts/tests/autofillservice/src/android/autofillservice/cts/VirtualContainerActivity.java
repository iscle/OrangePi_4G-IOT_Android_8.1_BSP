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

import static android.autofillservice.cts.Helper.ID_PASSWORD;
import static android.autofillservice.cts.Helper.ID_PASSWORD_LABEL;
import static android.autofillservice.cts.Helper.ID_USERNAME;
import static android.autofillservice.cts.Helper.ID_USERNAME_LABEL;

import static com.google.common.truth.Truth.assertWithMessage;

import android.autofillservice.cts.VirtualContainerView.Line;
import android.autofillservice.cts.VirtualContainerView.Line.OneTimeLineWatcher;
import android.graphics.Canvas;
import android.os.Bundle;

/**
 * A custom activity that uses {@link Canvas} to draw the following fields:
 *
 * <ul>
 *   <li>Username
 *   <li>Password
 * </ul>
 */
public class VirtualContainerActivity extends AbstractAutoFillActivity {

    static final String BLANK_VALUE = "        ";

    VirtualContainerView mCustomView;

    Line mUsername;
    Line mPassword;

    private FillExpectation mExpectation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.virtual_container_activity);

        mCustomView = findViewById(R.id.virtual_container_view);

        mUsername = mCustomView.addLine(ID_USERNAME_LABEL, "Username", ID_USERNAME, BLANK_VALUE);
        mPassword = mCustomView.addLine(ID_PASSWORD_LABEL, "Password", ID_PASSWORD, BLANK_VALUE);
    }

    /**
     * Triggers manual autofill in a given line.
     */
    void requestAutofill(Line line) {
        getAutofillManager().requestAutofill(mCustomView, line.text.id, line.bounds);
    }

    /**
     * Sets the expectation for an auto-fill request, so it can be asserted through
     * {@link #assertAutoFilled()} later.
     */
    void expectAutoFill(String username, String password) {
        mExpectation = new FillExpectation(username, password);
        mUsername.setTextChangedListener(mExpectation.ccUsernameWatcher);
        mPassword.setTextChangedListener(mExpectation.ccPasswordWatcher);
    }

    /**
     * Asserts the activity was auto-filled with the values passed to
     * {@link #expectAutoFill(String, String)}.
     */
    void assertAutoFilled() throws Exception {
        assertWithMessage("expectAutoFill() not called").that(mExpectation).isNotNull();
        mExpectation.ccUsernameWatcher.assertAutoFilled();
        mExpectation.ccPasswordWatcher.assertAutoFilled();
    }

    /**
     * Holder for the expected auto-fill values.
     */
    private final class FillExpectation {
        private final OneTimeLineWatcher ccUsernameWatcher;
        private final OneTimeLineWatcher ccPasswordWatcher;

        private FillExpectation(String username, String password) {
            ccUsernameWatcher = mUsername.new OneTimeLineWatcher(username);
            ccPasswordWatcher = mPassword.new OneTimeLineWatcher(password);
        }
    }
}
