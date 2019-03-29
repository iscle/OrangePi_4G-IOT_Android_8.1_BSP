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

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.icu.util.Calendar;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.autofill.AutofillValue;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TimePicker;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/*
 * TODO: refactor this class.
 *
 * It has 2 types of tests:
 *  1. unit tests that asserts AutofillValue methods
 *  2. integrationg tests that uses a the InstrumentedAutofillService
 *
 *  The unit tests (createXxxx*() should either be moved to the CtsViewTestCases module or to a
 *  class that does not need to extend AutoFillServiceTestCase.
 *
 *  Most integration tests overlap the tests on CheckoutActivityTest - we should remove the
 *  redundant tests and add more tests (like triggering autofill using different views) to
 *  CheckoutActivityTest.
 */
public class AutofillValueTest extends AutoFillServiceTestCase {
    @Rule
    public final AutofillActivityTestRule<AllAutofillableViewsActivity> mActivityRule =
            new AutofillActivityTestRule<>(AllAutofillableViewsActivity.class);

    private AllAutofillableViewsActivity mActivity;
    private EditText mEditText;
    private CompoundButton mCompoundButton;
    private RadioGroup mRadioGroup;
    private RadioButton mRadioButton1;
    private RadioButton mRadioButton2;
    private Spinner mSpinner;
    private DatePicker mDatePicker;
    private TimePicker mTimePicker;

    @Before
    public void setFields() {
        mActivity = mActivityRule.getActivity();

        mEditText = (EditText) mActivity.findViewById(R.id.editText);
        mCompoundButton = (CompoundButton) mActivity.findViewById(R.id.compoundButton);
        mRadioGroup = (RadioGroup) mActivity.findViewById(R.id.radioGroup);
        mRadioButton1 = (RadioButton) mActivity.findViewById(R.id.radioButton1);
        mRadioButton2 = (RadioButton) mActivity.findViewById(R.id.radioButton2);
        mSpinner = (Spinner) mActivity.findViewById(R.id.spinner);
        mDatePicker = (DatePicker) mActivity.findViewById(R.id.datePicker);
        mTimePicker = (TimePicker) mActivity.findViewById(R.id.timePicker);
    }

    @Test
    public void createTextValue() throws Exception {
        assertThat(AutofillValue.forText(null)).isNull();

        assertThat(AutofillValue.forText("").isText()).isTrue();
        assertThat(AutofillValue.forText("").isToggle()).isFalse();
        assertThat(AutofillValue.forText("").isList()).isFalse();
        assertThat(AutofillValue.forText("").isDate()).isFalse();

        AutofillValue emptyV = AutofillValue.forText("");
        assertThat(emptyV.getTextValue().toString()).isEqualTo("");

        final AutofillValue v = AutofillValue.forText("someText");
        assertThat(v.getTextValue()).isEqualTo("someText");

        assertThrows(IllegalStateException.class, v::getToggleValue);
        assertThrows(IllegalStateException.class, v::getListValue);
        assertThrows(IllegalStateException.class, v::getDateValue);
    }

    @Test
    public void createToggleValue() throws Exception {
        assertThat(AutofillValue.forToggle(true).getToggleValue()).isTrue();
        assertThat(AutofillValue.forToggle(false).getToggleValue()).isFalse();

        assertThat(AutofillValue.forToggle(true).isText()).isFalse();
        assertThat(AutofillValue.forToggle(true).isToggle()).isTrue();
        assertThat(AutofillValue.forToggle(true).isList()).isFalse();
        assertThat(AutofillValue.forToggle(true).isDate()).isFalse();


        final AutofillValue v = AutofillValue.forToggle(true);

        assertThrows(IllegalStateException.class, v::getTextValue);
        assertThrows(IllegalStateException.class, v::getListValue);
        assertThrows(IllegalStateException.class, v::getDateValue);
    }

    @Test
    public void createListValue() throws Exception {
        assertThat(AutofillValue.forList(-1).getListValue()).isEqualTo(-1);
        assertThat(AutofillValue.forList(0).getListValue()).isEqualTo(0);
        assertThat(AutofillValue.forList(1).getListValue()).isEqualTo(1);

        assertThat(AutofillValue.forList(0).isText()).isFalse();
        assertThat(AutofillValue.forList(0).isToggle()).isFalse();
        assertThat(AutofillValue.forList(0).isList()).isTrue();
        assertThat(AutofillValue.forList(0).isDate()).isFalse();

        final AutofillValue v = AutofillValue.forList(0);

        assertThrows(IllegalStateException.class, v::getTextValue);
        assertThrows(IllegalStateException.class, v::getToggleValue);
        assertThrows(IllegalStateException.class, v::getDateValue);
    }

    @Test
    public void createDateValue() throws Exception {
        assertThat(AutofillValue.forDate(-1).getDateValue()).isEqualTo(-1);
        assertThat(AutofillValue.forDate(0).getDateValue()).isEqualTo(0);
        assertThat(AutofillValue.forDate(1).getDateValue()).isEqualTo(1);

        assertThat(AutofillValue.forDate(0).isText()).isFalse();
        assertThat(AutofillValue.forDate(0).isToggle()).isFalse();
        assertThat(AutofillValue.forDate(0).isList()).isFalse();
        assertThat(AutofillValue.forDate(0).isDate()).isTrue();

        final AutofillValue v = AutofillValue.forDate(0);

        assertThrows(IllegalStateException.class, v::getTextValue);
        assertThrows(IllegalStateException.class, v::getToggleValue);
        assertThrows(IllegalStateException.class, v::getListValue);
    }

    /**
     * Trigger autofill on a view.
     *
     * @param view The view to trigger the autofill on
     */
    private void startAutoFill(@NonNull View view) throws Exception {
        mActivity.syncRunOnUiThread(() -> {
            view.clearFocus();
            view.requestFocus();
        });

        sReplier.getNextFillRequest();
    }

    private void autofillEditText(@Nullable AutofillValue value, String expectedText,
            boolean expectAutoFill) throws Exception {
        mActivity.syncRunOnUiThread(() -> mEditText.setVisibility(View.VISIBLE));

        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.CannedDataset.Builder()
                .setField("editText", value)
                .setPresentation(createPresentation("dataset"))
                .build());
        OneTimeTextWatcher textWatcher = new OneTimeTextWatcher("editText", mEditText,
                expectedText);
        mEditText.addTextChangedListener(textWatcher);

        // Trigger autofill.
        startAutoFill(mEditText);

        // Autofill it.
        sUiBot.selectDataset("dataset");

        if (expectAutoFill) {
            // Check the results.
            textWatcher.assertAutoFilled();
        } else {
            assertThat(mEditText.getText().toString()).isEqualTo(expectedText);
        }
    }

    @Test
    public void autofillValidTextValue() throws Exception {
        autofillEditText(AutofillValue.forText("filled"), "filled", true);
    }

    @Test
    public void autofillEmptyTextValue() throws Exception {
        autofillEditText(AutofillValue.forText(""), "", true);
    }

    @Test
    public void autofillTextWithListValue() throws Exception {
        autofillEditText(AutofillValue.forList(0), "", false);
    }

    @Test
    public void getEditTextAutoFillValue() throws Exception {
        mActivity.syncRunOnUiThread(() -> mEditText.setText("test"));
        assertThat(mEditText.getAutofillValue()).isEqualTo(AutofillValue.forText("test"));

        mActivity.syncRunOnUiThread(() -> mEditText.setEnabled(false));
        assertThat(mEditText.getAutofillValue()).isNull();
    }

    private void autofillCompoundButton(@Nullable AutofillValue value, boolean expectedValue,
            boolean expectAutoFill) throws Exception {
        mActivity.syncRunOnUiThread(() -> mCompoundButton.setVisibility(View.VISIBLE));

        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.CannedDataset.Builder()
                .setField("compoundButton", value)
                .setPresentation(createPresentation("dataset"))
                .build());
        OneTimeCompoundButtonListener checkedWatcher = new OneTimeCompoundButtonListener(
                    "compoundButton", mCompoundButton, expectedValue);
        mCompoundButton.setOnCheckedChangeListener(checkedWatcher);

        startAutoFill(mCompoundButton);

        // Autofill it.
        sUiBot.selectDataset("dataset");

        if (expectAutoFill) {
            // Check the results.
            checkedWatcher.assertAutoFilled();
        } else {
            assertThat(mCompoundButton.isChecked()).isEqualTo(expectedValue);
        }
    }

    @Test
    public void autofillToggleValueWithTrue() throws Exception {
        autofillCompoundButton(AutofillValue.forToggle(true), true, true);
    }

    @Test
    public void autofillToggleValueWithFalse() throws Exception {
        autofillCompoundButton(AutofillValue.forToggle(false), false, false);
    }

    @Test
    public void autofillCompoundButtonWithTextValue() throws Exception {
        autofillCompoundButton(AutofillValue.forText(""), false, false);
    }

    @Test
    public void getCompoundButtonAutoFillValue() throws Exception {
        mActivity.syncRunOnUiThread(() -> mCompoundButton.setChecked(true));
        assertThat(mCompoundButton.getAutofillValue()).isEqualTo(AutofillValue.forToggle(true));

        mActivity.syncRunOnUiThread(() -> mCompoundButton.setEnabled(false));
        assertThat(mCompoundButton.getAutofillValue()).isNull();
    }

    private void autofillListValue(@Nullable AutofillValue value, int expectedValue,
            boolean expectAutoFill) throws Exception {
        mActivity.syncRunOnUiThread(() -> mSpinner.setVisibility(View.VISIBLE));

        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.CannedDataset.Builder()
                .setField("spinner", value)
                .setPresentation(createPresentation("dataset"))
                .build());
        OneTimeSpinnerListener spinnerWatcher = new OneTimeSpinnerListener(
                "spinner", mSpinner, expectedValue);
        mSpinner.setOnItemSelectedListener(spinnerWatcher);

        startAutoFill(mSpinner);

        // Autofill it.
        sUiBot.selectDataset("dataset");

        if (expectAutoFill) {
            // Check the results.
            spinnerWatcher.assertAutoFilled();
        } else {
            assertThat(mSpinner.getSelectedItemPosition()).isEqualTo(expectedValue);
        }
    }

    @Test
    public void autofillZeroListValueToSpinner() throws Exception {
        autofillListValue(AutofillValue.forList(0), 0, false);
    }

    @Test
    public void autofillOneListValueToSpinner() throws Exception {
        autofillListValue(AutofillValue.forList(1), 1, true);
    }

    @Test
    public void autofillInvalidListValueToSpinner() throws Exception {
        autofillListValue(AutofillValue.forList(-1), 0, false);
    }

    @Test
    public void autofillSpinnerWithTextValue() throws Exception {
        autofillListValue(AutofillValue.forText(""), 0, false);
    }

    @Test
    public void getSpinnerAutoFillValue() throws Exception {
        mActivity.syncRunOnUiThread(() -> mSpinner.setSelection(1));
        assertThat(mSpinner.getAutofillValue()).isEqualTo(AutofillValue.forList(1));

        mActivity.syncRunOnUiThread(() -> mSpinner.setEnabled(false));
        assertThat(mSpinner.getAutofillValue()).isNull();
    }

    private void autofillDateValueToDatePicker(@Nullable AutofillValue value,
            boolean expectAutoFill) throws Exception {
        mActivity.syncRunOnUiThread(() -> {
            mEditText.setVisibility(View.VISIBLE);
            mDatePicker.setVisibility(View.VISIBLE);
        });

        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.CannedDataset.Builder()
                .setField("datePicker", value)
                .setField("editText", "filled")
                .setPresentation(createPresentation("dataset"))
                .build());
        OneTimeDateListener dateWatcher = new OneTimeDateListener("datePicker", mDatePicker,
                2017, 3, 7);
        mDatePicker.setOnDateChangedListener(dateWatcher);

        int nonAutofilledYear = mDatePicker.getYear();
        int nonAutofilledMonth = mDatePicker.getMonth();
        int nonAutofilledDay = mDatePicker.getDayOfMonth();

        // Trigger autofill.
        startAutoFill(mEditText);

        // Autofill it.
        sUiBot.selectDataset("dataset");

        if (expectAutoFill) {
            // Check the results.
            dateWatcher.assertAutoFilled();
        } else {
            Helper.assertDateValue(mDatePicker, nonAutofilledYear, nonAutofilledMonth,
                    nonAutofilledDay);
        }
    }

    private long getDateAsMillis(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance(
                mActivity.getResources().getConfiguration().getLocales().get(0));

        calendar.set(year, month, day, hour, minute);

        return calendar.getTimeInMillis();
    }

    @Test
    public void autofillValidDateValueToDatePicker() throws Exception {
        autofillDateValueToDatePicker(AutofillValue.forDate(getDateAsMillis(2017, 3, 7, 12, 32)),
                true);
    }

    @Test
    public void autofillDatePickerWithTextValue() throws Exception {
        autofillDateValueToDatePicker(AutofillValue.forText(""), false);
    }

    @Test
    public void getDatePickerAutoFillValue() throws Exception {
        mActivity.syncRunOnUiThread(() -> mDatePicker.updateDate(2017, 3, 7));

        Helper.assertDateValue(mDatePicker, 2017, 3, 7);

        mActivity.syncRunOnUiThread(() -> mDatePicker.setEnabled(false));
        assertThat(mDatePicker.getAutofillValue()).isNull();
    }

    private void autofillDateValueToTimePicker(@Nullable AutofillValue value,
            boolean expectAutoFill) throws Exception {
        mActivity.syncRunOnUiThread(() -> {
            mEditText.setVisibility(View.VISIBLE);
            mTimePicker.setIs24HourView(true);
            mTimePicker.setVisibility(View.VISIBLE);
        });

        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.CannedDataset.Builder()
                .setField("timePicker", value)
                .setField("editText", "filled")
                .setPresentation(createPresentation("dataset"))
                .build());
        MultipleTimesTimeListener timeWatcher = new MultipleTimesTimeListener("timePicker", 1,
                mTimePicker, 12, 32);
        mTimePicker.setOnTimeChangedListener(timeWatcher);

        int nonAutofilledHour = mTimePicker.getHour();
        int nonAutofilledMinute = mTimePicker.getMinute();

        // Trigger autofill.
        startAutoFill(mEditText);

        // Autofill it.
        sUiBot.selectDataset("dataset");

        if (expectAutoFill) {
            // Check the results.
            timeWatcher.assertAutoFilled();
        } else {
            Helper.assertTimeValue(mTimePicker, nonAutofilledHour, nonAutofilledMinute);
        }
    }

    @Test
    public void autofillValidDateValueToTimePicker() throws Exception {
        autofillDateValueToTimePicker(AutofillValue.forDate(getDateAsMillis(2017, 3, 7, 12, 32)),
                true);
    }

    @Test
    public void autofillTimePickerWithTextValue() throws Exception {
        autofillDateValueToTimePicker(AutofillValue.forText(""), false);
    }

    @Test
    public void getTimePickerAutoFillValue() throws Exception {
        mActivity.syncRunOnUiThread(() -> {
            mTimePicker.setHour(12);
            mTimePicker.setMinute(32);
        });

        Helper.assertTimeValue(mTimePicker, 12, 32);

        mActivity.syncRunOnUiThread(() -> mTimePicker.setEnabled(false));
        assertThat(mTimePicker.getAutofillValue()).isNull();
    }

    private void autofillRadioGroup(@Nullable AutofillValue value, int expectedValue,
            boolean expectAutoFill) throws Exception {
        mActivity.syncRunOnUiThread(() -> mEditText.setVisibility(View.VISIBLE));
        mActivity.syncRunOnUiThread(() -> mRadioGroup.setVisibility(View.VISIBLE));

        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.CannedDataset.Builder()
                .setField("radioGroup", value)
                .setField("editText", "filled")
                .setPresentation(createPresentation("dataset"))
                .build());
        MultipleTimesRadioGroupListener radioGroupWatcher = new MultipleTimesRadioGroupListener(
                "radioGroup", 2, mRadioGroup, expectedValue);
        mRadioGroup.setOnCheckedChangeListener(radioGroupWatcher);

        // Trigger autofill.
        startAutoFill(mEditText);

        // Autofill it.
        sUiBot.selectDataset("dataset");

        if (expectAutoFill) {
            // Check the results.
            radioGroupWatcher.assertAutoFilled();
        } else {
            if (expectedValue == 0) {
                assertThat(mRadioButton1.isChecked()).isEqualTo(true);
                assertThat(mRadioButton2.isChecked()).isEqualTo(false);
            } else {
                assertThat(mRadioButton1.isChecked()).isEqualTo(false);
                assertThat(mRadioButton2.isChecked()).isEqualTo(true);

            }
        }
    }

    @Test
    public void autofillZeroListValueToRadioGroup() throws Exception {
        autofillRadioGroup(AutofillValue.forList(0), 0, false);
    }

    @Test
    public void autofillOneListValueToRadioGroup() throws Exception {
        autofillRadioGroup(AutofillValue.forList(1), 1, true);
    }

    @Test
    public void autofillInvalidListValueToRadioGroup() throws Exception {
        autofillListValue(AutofillValue.forList(-1), 0, false);
    }

    @Test
    public void autofillRadioGroupWithTextValue() throws Exception {
        autofillRadioGroup(AutofillValue.forText(""), 0, false);
    }

    @Test
    public void getRadioGroupAutoFillValue() throws Exception {
        mActivity.syncRunOnUiThread(() -> mRadioButton2.setChecked(true));
        assertThat(mRadioGroup.getAutofillValue()).isEqualTo(AutofillValue.forList(1));

        mActivity.syncRunOnUiThread(() -> mRadioGroup.setEnabled(false));
        assertThat(mRadioGroup.getAutofillValue()).isNull();
    }
}
