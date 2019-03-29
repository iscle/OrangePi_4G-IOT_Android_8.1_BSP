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

import static android.autofillservice.cts.Helper.FILL_TIMEOUT_MS;

import static com.google.common.truth.Truth.assertWithMessage;

import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.EditText;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Custom {@link TextWatcher} used to assert a {@link EditText} was set multiple times.
 */
class MultipleTimesTextWatcher implements TextWatcher {
    private static final String TAG = "MultipleTimesTextWatcher";

    private final String mName;
    private final CountDownLatch mLatch;
    private final EditText mEditText;
    private final CharSequence mExpected;

    MultipleTimesTextWatcher(String name, int times, EditText editText,
            CharSequence expectedAutofillValue) {
        this.mName = name;
        this.mEditText = editText;
        this.mExpected = expectedAutofillValue;
        this.mLatch = new CountDownLatch(times);
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        Log.v(TAG, "onTextChanged(" + mLatch.getCount() + "): " + mName + " = " + s);
        mLatch.countDown();
    }

    @Override
    public void afterTextChanged(Editable s) {
    }

    void assertAutoFilled() throws Exception {
        final boolean set = mLatch.await(FILL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertWithMessage("Timeout (%s ms) on EditText %s", FILL_TIMEOUT_MS, mName)
                .that(set).isTrue();
        final String actual = mEditText.getText().toString();
        assertWithMessage("Wrong auto-fill value on EditText %s", mName)
                .that(actual).isEqualTo(mExpected.toString());
    }
}
