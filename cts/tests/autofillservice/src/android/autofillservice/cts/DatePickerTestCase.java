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

import static android.autofillservice.cts.AbstractDatePickerActivity.ID_DATE_PICKER;
import static android.autofillservice.cts.AbstractDatePickerActivity.ID_OUTPUT;
import static android.autofillservice.cts.Helper.assertDateValue;
import static android.autofillservice.cts.Helper.assertNumberOfChildren;
import static android.autofillservice.cts.Helper.assertTextAndValue;
import static android.autofillservice.cts.Helper.assertTextIsSanitized;
import static android.autofillservice.cts.Helper.findNodeByResourceId;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_GENERIC;

import static com.google.common.truth.Truth.assertWithMessage;

import android.autofillservice.cts.CannedFillResponse.CannedDataset;
import android.autofillservice.cts.InstrumentedAutoFillService.FillRequest;
import android.autofillservice.cts.InstrumentedAutoFillService.SaveRequest;
import android.icu.util.Calendar;

import org.junit.After;
import org.junit.Test;

/**
 * Base class for {@link AbstractDatePickerActivity} tests.
 */
abstract class DatePickerTestCase<T extends AbstractDatePickerActivity>
        extends AutoFillServiceTestCase {

    protected abstract T getDatePickerActivity();

    @After
    public void finishWelcomeActivity() {
        WelcomeActivity.finishIt();
    }

    @Test
    public void testAutoFillAndSave() throws Exception {
        final T activity = getDatePickerActivity();

        // Set service.
        enableService();

        // Set expectations.
        final Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, 2012);
        cal.set(Calendar.MONTH, 11);
        cal.set(Calendar.DAY_OF_MONTH, 20);

        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                    .setPresentation(createPresentation("The end of the world"))
                    .setField(ID_OUTPUT, "Y U NO CHANGE ME?")
                    .setField(ID_DATE_PICKER, cal.getTimeInMillis())
                    .build())
                .setRequiredSavableIds(SAVE_DATA_TYPE_GENERIC, ID_OUTPUT, ID_DATE_PICKER)
                .build());
        activity.expectAutoFill("2012/11/20", 2012, 11, 20);

        // Trigger auto-fill.
        activity.onOutput((v) -> v.requestFocus());
        final FillRequest fillRequest = sReplier.getNextFillRequest();

        // Assert properties of DatePicker field.
        assertTextIsSanitized(fillRequest.structure, ID_DATE_PICKER);
        assertNumberOfChildren(fillRequest.structure, ID_DATE_PICKER, 0);

        // Auto-fill it.
        sUiBot.selectDataset("The end of the world");

        // Check the results.
        activity.assertAutoFilled();

        // Trigger save.
        activity.setDate(2010, 11, 12);
        activity.tapOk();

        sUiBot.saveForAutofill(true, SAVE_DATA_TYPE_GENERIC);
        final SaveRequest saveRequest = sReplier.getNextSaveRequest();
        assertWithMessage("onSave() not called").that(saveRequest).isNotNull();

        // Assert sanitization on save: everything should be available!
        assertDateValue(findNodeByResourceId(saveRequest.structure, ID_DATE_PICKER), 2010, 11, 12);
        assertTextAndValue(findNodeByResourceId(saveRequest.structure, ID_OUTPUT), "2010/11/12");
    }
}
