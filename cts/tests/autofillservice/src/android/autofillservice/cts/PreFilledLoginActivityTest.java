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

import static android.autofillservice.cts.Helper.ID_PASSWORD;
import static android.autofillservice.cts.Helper.ID_PASSWORD_LABEL;
import static android.autofillservice.cts.Helper.ID_USERNAME;
import static android.autofillservice.cts.Helper.ID_USERNAME_LABEL;
import static android.autofillservice.cts.Helper.assertTextAndValue;
import static android.autofillservice.cts.Helper.assertTextIsSanitized;
import static android.autofillservice.cts.Helper.assertTextOnly;
import static android.autofillservice.cts.Helper.findNodeByResourceId;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_PASSWORD;

import android.autofillservice.cts.InstrumentedAutoFillService.FillRequest;
import android.autofillservice.cts.InstrumentedAutoFillService.SaveRequest;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Covers scenarios where the behavior is different because some fields were pre-filled.
 */
public class PreFilledLoginActivityTest extends AutoFillServiceTestCase {

    @Rule
    public final AutofillActivityTestRule<PreFilledLoginActivity> mActivityRule =
            new AutofillActivityTestRule<PreFilledLoginActivity>(PreFilledLoginActivity.class);

    private PreFilledLoginActivity mActivity;

    @Before
    public void setActivity() {
        mActivity = mActivityRule.getActivity();
    }

    @After
    public void finishWelcomeActivity() {
        WelcomeActivity.finishIt();
    }

    @Test
    public void testSanitization() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD)
                .build());

        // Change view contents.
        mActivity.onUsernameLabel((v) -> v.setText("DA USER"));
        mActivity.onPasswordLabel((v) -> v.setText(R.string.new_password_label));

        // Trigger auto-fill.
        mActivity.onUsername((v) -> v.requestFocus());

        // Assert sanitization on fill request:
        final FillRequest fillRequest = sReplier.getNextFillRequest();

        // ...dynamic text should be sanitized.
        assertTextIsSanitized(fillRequest.structure, ID_USERNAME_LABEL);

        // ...password label should be ok because it was set from other resource id
        assertTextOnly(findNodeByResourceId(fillRequest.structure, ID_PASSWORD_LABEL),
                "DA PASSWORD");

        // ...username and password should be ok because they were set in the SML
        assertTextAndValue(findNodeByResourceId(fillRequest.structure, ID_USERNAME),
                "secret_agent");
        assertTextAndValue(findNodeByResourceId(fillRequest.structure, ID_PASSWORD), "T0p S3cr3t");

        // Trigger save
        mActivity.onUsername((v) -> v.setText("malkovich"));
        mActivity.onPassword((v) -> v.setText("malkovich"));
        mActivity.tapLogin();

        // Assert the snack bar is shown and tap "Save".
        sUiBot.saveForAutofill(true, SAVE_DATA_TYPE_PASSWORD);
        final SaveRequest saveRequest = sReplier.getNextSaveRequest();

        // Assert sanitization on save: everything should be available!
        assertTextOnly(findNodeByResourceId(saveRequest.structure, ID_USERNAME_LABEL), "DA USER");
        assertTextOnly(findNodeByResourceId(saveRequest.structure, ID_PASSWORD_LABEL),
                "DA PASSWORD");
        assertTextAndValue(findNodeByResourceId(saveRequest.structure, ID_USERNAME), "malkovich");
        assertTextAndValue(findNodeByResourceId(saveRequest.structure, ID_PASSWORD), "malkovich");
    }
}
