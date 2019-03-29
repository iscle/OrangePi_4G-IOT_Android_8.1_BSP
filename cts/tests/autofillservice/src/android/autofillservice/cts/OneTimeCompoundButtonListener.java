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

import android.widget.CompoundButton;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Custom {@link android.widget.CompoundButton.OnCheckedChangeListener} used to assert a
 * {@link CompoundButton} was auto-filled properly.
 */
final class OneTimeCompoundButtonListener implements CompoundButton.OnCheckedChangeListener {
    private final String name;
    private final CountDownLatch latch = new CountDownLatch(1);
    private final CompoundButton button;
    private final boolean expected;

    OneTimeCompoundButtonListener(String name, CompoundButton button,
            boolean expectedAutofillValue) {
        this.name = name;
        this.button = button;
        this.expected = expectedAutofillValue;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        latch.countDown();
    }

    void assertAutoFilled() throws Exception {
        final boolean set = latch.await(FILL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertWithMessage("Timeout (%s ms) on CompoundButton %s", FILL_TIMEOUT_MS, name)
            .that(set).isTrue();
        final boolean actual = button.isChecked();
        assertWithMessage("Wrong auto-fill value on CompoundButton %s", name)
            .that(actual).isEqualTo(expected);
    }
}
