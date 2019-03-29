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

import android.widget.TimePicker;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Custom {@OnDateChangedListener} used to assert a {@link TimePicker} was auto-filled properly.
 */
final class MultipleTimesTimeListener implements TimePicker.OnTimeChangedListener {
    private final String name;
    private final CountDownLatch latch;
    private final TimePicker timePicker;
    private final int expectedHour;
    private final int expectedMinute;

    MultipleTimesTimeListener(String name, int times, TimePicker timePicker, int expectedHour,
            int expectedMinute) {
        this.name = name;
        this.timePicker = timePicker;
        this.expectedHour = expectedHour;
        this.expectedMinute = expectedMinute;
        this.latch = new CountDownLatch(times);
    }

    @Override
    public void onTimeChanged(TimePicker view, int hour, int minute) {
        latch.countDown();
    }

    void assertAutoFilled() throws Exception {
        final boolean set = latch.await(FILL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertWithMessage("Timeout (%s ms) on TimePicker %s", FILL_TIMEOUT_MS, name)
                .that(set).isTrue();
        assertWithMessage("Wrong hour on TimePicker %s", name)
                .that(timePicker.getHour()).isEqualTo(expectedHour);
        assertWithMessage("Wrong minute on TimePicker %s", name)
                .that(timePicker.getMinute()).isEqualTo(expectedMinute);
    }
}
