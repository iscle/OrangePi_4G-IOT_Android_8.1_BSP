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

import static android.autofillservice.cts.AbstractTimePickerActivity.ID_OUTPUT;
import static android.autofillservice.cts.AbstractTimePickerActivity.ID_TIME_PICKER;
import static android.autofillservice.cts.Helper.assertNumberOfChildren;
import static android.autofillservice.cts.Helper.assertTextAndValue;
import static android.autofillservice.cts.Helper.assertTextIsSanitized;
import static android.autofillservice.cts.Helper.assertTimeValue;
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
 * Base class for {@link AbstractTimePickerActivity} tests.
 */
abstract class TimePickerTestCase<T extends AbstractTimePickerActivity>
        extends AutoFillServiceTestCase {

    protected abstract T getTimePickerActivity();

    @After
    public void finishWelcomeActivity() {
        WelcomeActivity.finishIt();
    }

    @Test
    public void testAutoFillAndSave() throws Exception {
        final T activity = getTimePickerActivity();

        // Set service.
        enableService();

        // Set expectations.
        final Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 4);
        cal.set(Calendar.MINUTE, 20);

        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                    .setPresentation(createPresentation("Adventure Time"))
                    .setField(ID_OUTPUT, "Y U NO CHANGE ME?")
                    .setField(ID_TIME_PICKER, cal.getTimeInMillis())
                    .build())
                .setRequiredSavableIds(SAVE_DATA_TYPE_GENERIC, ID_OUTPUT, ID_TIME_PICKER)
                .build());

        activity.expectAutoFill("4:20", 4, 20);

        // Trigger auto-fill.
        activity.onOutput((v) -> v.requestFocus());
        final FillRequest fillRequest = sReplier.getNextFillRequest();

        // Assert properties of TimePicker field.
        assertTextIsSanitized(fillRequest.structure, ID_TIME_PICKER);
        assertNumberOfChildren(fillRequest.structure, ID_TIME_PICKER, 0);
        // Auto-fill it.
        sUiBot.selectDataset("Adventure Time");

        // Check the results.
        activity.assertAutoFilled();

        // Trigger save.
        activity.setTime(10, 40);
        activity.tapOk();

        sUiBot.saveForAutofill(true, SAVE_DATA_TYPE_GENERIC);
        final SaveRequest saveRequest = sReplier.getNextSaveRequest();
        assertWithMessage("onSave() not called").that(saveRequest).isNotNull();

        // Assert sanitization on save: everything should be available!
        assertTimeValue(findNodeByResourceId(saveRequest.structure, ID_TIME_PICKER), 10, 40);
        assertTextAndValue(findNodeByResourceId(saveRequest.structure, ID_OUTPUT), "10:40");
    }
}
