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

import static android.autofillservice.cts.CannedFillResponse.NO_RESPONSE;
import static android.autofillservice.cts.FatActivity.ID_CAPTCHA;
import static android.autofillservice.cts.FatActivity.ID_IMAGE;
import static android.autofillservice.cts.FatActivity.ID_IMPORTANT_CONTAINER_EXCLUDING_DESCENDANTS;
import static android.autofillservice.cts.FatActivity.ID_IMPORTANT_CONTAINER_EXCLUDING_DESCENDANTS_CHILD;
import static android.autofillservice.cts.FatActivity.ID_IMPORTANT_CONTAINER_EXCLUDING_DESCENDANTS_GRAND_CHILD;
import static android.autofillservice.cts.FatActivity.ID_IMPORTANT_IMAGE;
import static android.autofillservice.cts.FatActivity.ID_INPUT;
import static android.autofillservice.cts.FatActivity.ID_INPUT_CONTAINER;
import static android.autofillservice.cts.FatActivity.ID_NOT_IMPORTANT_CONTAINER_EXCLUDING_DESCENDANTS;
import static android.autofillservice.cts.FatActivity.ID_NOT_IMPORTANT_CONTAINER_EXCLUDING_DESCENDANTS_CHILD;
import static android.autofillservice.cts.FatActivity.ID_NOT_IMPORTANT_CONTAINER_EXCLUDING_DESCENDANTS_GRAND_CHILD;
import static android.autofillservice.cts.FatActivity.ID_NOT_IMPORTANT_CONTAINER_MIXED_DESCENDANTS;
import static android.autofillservice.cts.FatActivity.ID_NOT_IMPORTANT_CONTAINER_MIXED_DESCENDANTS_CHILD;
import static android.autofillservice.cts.FatActivity.ID_NOT_IMPORTANT_CONTAINER_MIXED_DESCENDANTS_GRAND_CHILD;
import static android.autofillservice.cts.Helper.assertNumberOfChildren;
import static android.autofillservice.cts.Helper.findNodeByResourceId;
import static android.autofillservice.cts.Helper.findNodeByText;

import static com.google.common.truth.Truth.assertThat;

import android.app.assist.AssistStructure.ViewNode;
import android.autofillservice.cts.InstrumentedAutoFillService.FillRequest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test case for an activity containing useless auto-fill data that should be optimized out.
 */
public class FatActivityTest extends AutoFillServiceTestCase {

    @Rule
    public final AutofillActivityTestRule<FatActivity> mActivityRule =
        new AutofillActivityTestRule<FatActivity>(FatActivity.class);

    private FatActivity mFatActivity;

    @Before
    public void setActivity() {
        mFatActivity = mActivityRule.getActivity();
    }

    @Test
    public void testNoContainers() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(NO_RESPONSE);

        // Trigger auto-fill.
        mFatActivity.onInput((v) -> v.requestFocus());
        final FillRequest fillRequest = sReplier.getNextFillRequest();
        sUiBot.assertNoDatasets();

        // TODO: should only have 5 children, but there is an extra
        // TextView that's probably coming from the title. For now we're just ignoring it, but
        // ideally we should change the .xml to exclude it.
        assertNumberOfChildren(fillRequest.structure, 8);

        // Should not have ImageView...
        assertThat(findNodeByResourceId(fillRequest.structure, ID_IMAGE)).isNull();

        // ...unless app developer asked to:
        assertThat(findNodeByResourceId(fillRequest.structure, ID_IMPORTANT_IMAGE)).isNotNull();

        // Should have TextView, even if it does not have id.
        assertThat(findNodeByText(fillRequest.structure, "Label with no ID")).isNotNull();

        // Should not have EditText that was explicitly removed.
        assertThat(findNodeByResourceId(fillRequest.structure, ID_CAPTCHA)).isNull();

        // Make sure container with a resource id was included.
        final ViewNode inputContainer =
                findNodeByResourceId(fillRequest.structure, ID_INPUT_CONTAINER);
        assertThat(inputContainer).isNotNull();
        assertThat(inputContainer.getChildCount()).isEqualTo(1);
        final ViewNode input = inputContainer.getChildAt(0);
        assertThat(input.getIdEntry()).isEqualTo(ID_INPUT);

        // Make sure a non-important container can exclude descendants
        assertThat(findNodeByResourceId(fillRequest.structure,
                ID_NOT_IMPORTANT_CONTAINER_EXCLUDING_DESCENDANTS)).isNull();
        assertThat(findNodeByResourceId(fillRequest.structure,
                ID_NOT_IMPORTANT_CONTAINER_EXCLUDING_DESCENDANTS_CHILD)).isNull();
        assertThat(findNodeByResourceId(fillRequest.structure,
                ID_NOT_IMPORTANT_CONTAINER_EXCLUDING_DESCENDANTS_GRAND_CHILD)).isNull();

        // Make sure an important container can exclude descendants
        assertThat(findNodeByResourceId(fillRequest.structure,
                ID_IMPORTANT_CONTAINER_EXCLUDING_DESCENDANTS)).isNotNull();
        assertThat(findNodeByResourceId(fillRequest.structure,
                ID_IMPORTANT_CONTAINER_EXCLUDING_DESCENDANTS_CHILD)).isNull();
        assertThat(findNodeByResourceId(fillRequest.structure,
                ID_IMPORTANT_CONTAINER_EXCLUDING_DESCENDANTS_GRAND_CHILD)).isNull();

        // Make sure an intermediary descendant can be excluded
        assertThat(findNodeByResourceId(fillRequest.structure,
                ID_NOT_IMPORTANT_CONTAINER_MIXED_DESCENDANTS)).isNull();
        assertThat(findNodeByResourceId(fillRequest.structure,
                ID_NOT_IMPORTANT_CONTAINER_MIXED_DESCENDANTS_CHILD)).isNotNull();
        assertThat(findNodeByResourceId(fillRequest.structure,
                ID_NOT_IMPORTANT_CONTAINER_MIXED_DESCENDANTS_GRAND_CHILD)).isNull();
    }
}
