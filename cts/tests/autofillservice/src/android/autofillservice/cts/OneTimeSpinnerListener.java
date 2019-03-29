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

import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Spinner;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Custom {@link OnItemSelectedListener} used to assert an {@link Spinner} was auto-filled properly.
 */
final class OneTimeSpinnerListener implements OnItemSelectedListener {
    private final String name;
    private final CountDownLatch latch = new CountDownLatch(1);
    private final Spinner spinner;
    private final int expected;

    OneTimeSpinnerListener(String name, Spinner spinner, int expectedAutoFilledValue) {
        this.name = name;
        this.spinner = spinner;
        this.expected = expectedAutoFilledValue;
    }

    void assertAutoFilled() throws Exception {
        final boolean set = latch.await(FILL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertWithMessage("Timeout (%s ms) on Spinner %s", FILL_TIMEOUT_MS, name)
            .that(set).isTrue();
        final int actual = spinner.getSelectedItemPosition();
        assertWithMessage("Wrong auto-fill value on Spinner %s", name)
            .that(actual).isEqualTo(expected);
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        latch.countDown();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        latch.countDown();
    }
}
