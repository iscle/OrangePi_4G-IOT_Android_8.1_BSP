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
import android.widget.Button;
import android.widget.EditText;
import android.widget.TimePicker;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Base class for an activity that has the following fields:
 *
 * <ul>
 *   <li>A TimePicker (id: date_picker)
 *   <li>An EditText that is filled with the TimePicker when it changes (id: output)
 *   <li>An OK button that finishes it and navigates to the {@link WelcomeActivity}
 * </ul>
 *
 * <p>It's abstract because the sub-class must provide the view id, so it can support multiple
 * UI types (like clock and spinner).
 */
abstract class AbstractTimePickerActivity extends AbstractAutoFillActivity {

    private static final long OK_TIMEOUT_MS = 1000;

    static final String ID_TIME_PICKER = "time_picker";
    static final String ID_OUTPUT = "output";

    private TimePicker mTimePicker;
    private EditText mOutput;
    private Button mOk;

    private FillExpectation mExpectation;
    private CountDownLatch mOkLatch;

    protected abstract int getContentView();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(getContentView());

        mTimePicker = (TimePicker) findViewById(R.id.time_picker);

        mTimePicker.setOnTimeChangedListener((v, m, h) -> {
            updateOutputWithTime(m, h);
        });

        mOutput = (EditText) findViewById(R.id.output);
        mOk = (Button) findViewById(R.id.ok);
        mOk.setOnClickListener((v) -> {
            ok();
        });
    }

    private void updateOutputWithTime(int hour, int minute) {
        final String time = hour + ":" + minute;
        mOutput.setText(time);
    }

    private void ok() {
        final Intent intent = new Intent(this, WelcomeActivity.class);
        intent.putExtra(WelcomeActivity.EXTRA_MESSAGE, "It's Adventure Time!");
        startActivity(intent);
        if (mOkLatch != null) {
            // Latch is not set when activity launched outside tests
            mOkLatch.countDown();
        }
        finish();
    }

    /**
     * Sets the expectation for an auto-fill request, so it can be asserted through
     * {@link #assertAutoFilled()} later.
     */
    void expectAutoFill(String output, int hour, int minute) {
        mExpectation = new FillExpectation(output, hour, minute);
        mOutput.addTextChangedListener(mExpectation.outputWatcher);
        mTimePicker.setOnTimeChangedListener((v, h, m) -> {
            updateOutputWithTime(h, m);
            mExpectation.timeListener.onTimeChanged(v, h, m);
        });
    }

    /**
     * Asserts the activity was auto-filled with the values passed to
     * {@link #expectAutoFill(String, int, int)}.
     */
    void assertAutoFilled() throws Exception {
        assertWithMessage("expectAutoFill() not called").that(mExpectation).isNotNull();
        mExpectation.timeListener.assertAutoFilled();
        mExpectation.outputWatcher.assertAutoFilled();
    }

    /**
     * Visits the {@code output} in the UiThread.
     */
    void onOutput(Visitor<EditText> v) {
        syncRunOnUiThread(() -> {
            v.visit(mOutput);
        });
    }

    /**
     * Sets the time in the {@link TimePicker}.
     */
    void setTime(int hour, int minute) {
        syncRunOnUiThread(() -> {
            mTimePicker.setHour(hour);
            mTimePicker.setMinute(minute);
        });
    }

    /**
     * Taps the ok button in the UI thread.
     */
    void tapOk() throws Exception {
        mOkLatch = new CountDownLatch(1);
        syncRunOnUiThread(() -> {
            mOk.performClick();
        });
        boolean called = mOkLatch.await(OK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertWithMessage("Timeout (%s ms) waiting for OK action", OK_TIMEOUT_MS)
                .that(called).isTrue();
    }

    /**
     * Holder for the expected auto-fill values.
     */
    private final class FillExpectation {
        private final MultipleTimesTextWatcher outputWatcher;
        private final MultipleTimesTimeListener timeListener;

        private FillExpectation(String output, int hour, int minute) {
            // Output is called twice: by the TimeChangeListener and by auto-fill.
            outputWatcher = new MultipleTimesTextWatcher("output", 2, mOutput, output);
            timeListener = new MultipleTimesTimeListener("timePicker", 1, mTimePicker, hour,
                    minute);
        }
    }
}
