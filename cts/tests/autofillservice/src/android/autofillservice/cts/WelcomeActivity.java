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

import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.test.uiautomator.UiObject2;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;

/**
 * Activity that displays a "Welcome USER" message after login.
 */
public class WelcomeActivity extends AbstractAutoFillActivity {

    private static WelcomeActivity sInstance;

    private static final String TAG = "WelcomeActivity";

    static final String EXTRA_MESSAGE = "message";
    static final String ID_OUTPUT = "output";

    private TextView mOutput;

    public WelcomeActivity() {
        sInstance = this;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.welcome_activity);

        mOutput = (TextView) findViewById(R.id.output);

        final Intent intent = getIntent();
        final String message = intent.getStringExtra(EXTRA_MESSAGE);

        if (!TextUtils.isEmpty(message)) {
            mOutput.setText(message);
        }

        Log.d(TAG, "Output: " + mOutput.getText());
    }

    static void finishIt() {
        if (sInstance != null) {
            Log.d(TAG, "So long and thanks for all the fish!");
            sInstance.finish();
        }
    }

    // TODO: reuse in other places
    static void assertShowingDefaultMessage(UiBot uiBot) throws Exception {
        assertShowing(uiBot, null);
    }

    // TODO: reuse in other places
    static void assertShowing(UiBot uiBot, @Nullable String expectedMessage) throws Exception {
        final UiObject2 activity = uiBot.assertShownByRelativeId(ID_OUTPUT);
        if (expectedMessage == null) {
            expectedMessage = "Welcome to the jungle!";
        }
        assertWithMessage("wrong text on '%s'", activity).that(activity.getText())
                .isEqualTo(expectedMessage);
    }
}
