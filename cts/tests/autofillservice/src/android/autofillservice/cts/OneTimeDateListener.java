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

import android.widget.DatePicker;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Custom {@OnDateChangedListener} used to assert a {@link DatePicker} was auto-filled properly.
 */
final class OneTimeDateListener implements DatePicker.OnDateChangedListener {
    private final String name;
    private final CountDownLatch latch = new CountDownLatch(1);
    private final DatePicker datePicker;
    private final int expectedYear;
    private final int expectedMonth;
    private final int expectedDay;

    OneTimeDateListener(String name, DatePicker datePicker, int expectedYear, int expectedMonth,
            int expectedDay) {
        this.name = name;
        this.datePicker = datePicker;
        this.expectedYear = expectedYear;
        this.expectedMonth = expectedMonth;
        this.expectedDay = expectedDay;
    }

    @Override
    public void onDateChanged(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
        latch.countDown();
    }

    void assertAutoFilled() throws Exception {
        final boolean set = latch.await(FILL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertWithMessage("Timeout (%s ms) on DatePicker %s", FILL_TIMEOUT_MS, name)
            .that(set).isTrue();
        assertWithMessage("Wrong year on DatePicker %s", name)
            .that(datePicker.getYear()).isEqualTo(expectedYear);
        assertWithMessage("Wrong month on DatePicker %s", name)
            .that(datePicker.getMonth()).isEqualTo(expectedMonth);
        assertWithMessage("Wrong day on DatePicker %s", name)
            .that(datePicker.getDayOfMonth()).isEqualTo(expectedDay);
    }
}
