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
import static android.autofillservice.cts.FragmentContainerActivity.FRAGMENT_TAG;
import static android.autofillservice.cts.Helper.FILL_TIMEOUT_MS;
import static android.autofillservice.cts.Helper.eventually;
import static android.autofillservice.cts.Helper.findNodeByResourceId;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_GENERIC;

import static com.google.common.truth.Truth.assertThat;

import android.app.assist.AssistStructure;
import android.app.assist.AssistStructure.ViewNode;
import android.os.Bundle;
import android.util.Log;
import android.view.autofill.AutofillValue;
import android.widget.EditText;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class MultipleFragmentLoginTest extends AutoFillServiceTestCase {
    private static final String LOG_TAG = MultipleFragmentLoginTest.class.getSimpleName();
    @Rule
    public final AutofillActivityTestRule<FragmentContainerActivity> mActivityRule =
            new AutofillActivityTestRule<>(FragmentContainerActivity.class);
    private FragmentContainerActivity mActivity;
    private EditText mEditText1;
    private EditText mEditText2;

    @Before
    public void init() {
        mActivity = mActivityRule.getActivity();
        mEditText1 = mActivity.findViewById(R.id.editText1);
        mEditText2 = mActivity.findViewById(R.id.editText2);
    }

    @Test
    public void loginOnTwoFragments() throws Exception {
        enableService();

        Bundle clientState = new Bundle();
        clientState.putString("key", "value1");
        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField("editText1", "editText1-autofilled")
                        .setPresentation(createPresentation("dataset1"))
                        .build())
                .setExtras(clientState)
                .build());

        final InstrumentedAutoFillService.FillRequest[] fillRequest =
                new InstrumentedAutoFillService.FillRequest[1];

        // Trigger autofill
        eventually(() -> {
            mActivity.syncRunOnUiThread(() -> {
                mEditText2.requestFocus();
                mEditText1.requestFocus();
            });

            try {
                fillRequest[0] = sReplier.getNextFillRequest();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }, (int) (FILL_TIMEOUT_MS * 2));

        assertThat(fillRequest[0].data).isNull();

        AssistStructure structure = fillRequest[0].contexts.get(0).getStructure();
        assertThat(fillRequest[0].contexts.size()).isEqualTo(1);
        assertThat(findNodeByResourceId(structure, "editText1")).isNotNull();
        assertThat(findNodeByResourceId(structure, "editText2")).isNotNull();
        assertThat(findNodeByResourceId(structure, "editText3")).isNull();
        assertThat(findNodeByResourceId(structure, "editText4")).isNull();
        assertThat(findNodeByResourceId(structure, "editText5")).isNull();

        // Wait until autofill has been applied
        sUiBot.selectDataset("dataset1");
        sUiBot.assertShownByText("editText1-autofilled");

        // Manually fill view
        mActivity.syncRunOnUiThread(() -> mEditText2.setText("editText2-manually-filled"));

        // Replacing the fragment focused a previously unknown view which triggers a new
        // partition
        clientState.putString("key", "value2");
        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField("editText3", "editText3-autofilled")
                        .setField("editText4", "editText4-autofilled")
                        .setPresentation(createPresentation("dataset2"))
                        .build())
                .setRequiredSavableIds(SAVE_DATA_TYPE_GENERIC, "editText2", "editText5")
                .setExtras(clientState)
                .build());

        Log.i(LOG_TAG, "Switching Fragments");
        mActivity.syncRunOnUiThread(
                () -> mActivity.getFragmentManager().beginTransaction().replace(
                        R.id.rootContainer, new FragmentWithMoreEditTexts(),
                        FRAGMENT_TAG).commitNow());
        EditText editText5 = mActivity.findViewById(R.id.editText5);
        fillRequest[0] = sReplier.getNextFillRequest();

        // The fillRequest should have a fillContext for each partition. The first partition
        // should be filled in
        assertThat(fillRequest[0].contexts.size()).isEqualTo(2);

        assertThat(fillRequest[0].data.getString("key")).isEqualTo("value1");

        AssistStructure structure1 = fillRequest[0].contexts.get(0).getStructure();
        ViewNode editText1Node = findNodeByResourceId(structure1, "editText1");
        // The actual value in the structure is not updated in FillRequest-contexts, but the
        // autofill value is. For text views in SaveRequest both are updated, but this is the
        // only exception.
        assertThat(editText1Node.getAutofillValue()).isEqualTo(
                AutofillValue.forText("editText1-autofilled"));

        ViewNode editText2Node = findNodeByResourceId(structure1, "editText2");
        // Manually filled fields are not send to onFill. They appear in onSave if they are set
        // as saveable fields.
        assertThat(editText2Node.getText().toString()).isEqualTo("");

        assertThat(findNodeByResourceId(structure1, "editText3")).isNull();
        assertThat(findNodeByResourceId(structure1, "editText4")).isNull();
        assertThat(findNodeByResourceId(structure1, "editText5")).isNull();

        AssistStructure structure2 = fillRequest[0].contexts.get(1).getStructure();

        assertThat(findNodeByResourceId(structure2, "editText1")).isNull();
        assertThat(findNodeByResourceId(structure2, "editText2")).isNull();
        assertThat(findNodeByResourceId(structure2, "editText3")).isNotNull();
        assertThat(findNodeByResourceId(structure2, "editText4")).isNotNull();
        assertThat(findNodeByResourceId(structure2, "editText5")).isNotNull();

        // Wait until autofill has been applied
        sUiBot.selectDataset("dataset2");
        sUiBot.assertShownByText("editText3-autofilled");
        sUiBot.assertShownByText("editText4-autofilled");

        // Manually fill view
        mActivity.syncRunOnUiThread(() -> editText5.setText("editText5-manually-filled"));

        // Finish activity and save data
        mActivity.finish();
        sUiBot.saveForAutofill(true, SAVE_DATA_TYPE_GENERIC);

        // The saveRequest should have a fillContext for each partition with all the data
        InstrumentedAutoFillService.SaveRequest saveRequest = sReplier.getNextSaveRequest();
        assertThat(saveRequest.contexts.size()).isEqualTo(2);

        assertThat(saveRequest.data.getString("key")).isEqualTo("value2");

        structure1 = saveRequest.contexts.get(0).getStructure();
        editText1Node = findNodeByResourceId(structure1, "editText1");
        assertThat(editText1Node.getText().toString()).isEqualTo("editText1-autofilled");

        editText2Node = findNodeByResourceId(structure1, "editText2");
        assertThat(editText2Node.getText().toString()).isEqualTo("editText2-manually-filled");

        assertThat(findNodeByResourceId(structure1, "editText3")).isNull();
        assertThat(findNodeByResourceId(structure1, "editText4")).isNull();
        assertThat(findNodeByResourceId(structure1, "editText5")).isNull();

        structure2 = saveRequest.contexts.get(1).getStructure();
        assertThat(findNodeByResourceId(structure2, "editText1")).isNull();
        assertThat(findNodeByResourceId(structure2, "editText2")).isNull();

        ViewNode editText3Node = findNodeByResourceId(structure2, "editText3");
        assertThat(editText3Node.getText().toString()).isEqualTo("editText3-autofilled");

        ViewNode editText4Node = findNodeByResourceId(structure2, "editText4");
        assertThat(editText4Node.getText().toString()).isEqualTo("editText4-autofilled");

        ViewNode editText5Node = findNodeByResourceId(structure2, "editText5");
        assertThat(editText5Node.getText().toString()).isEqualTo("editText5-manually-filled");
    }

    @Test
    public void uiDismissedWhenNonSavableFragmentIsGone() throws Exception {
        uiDismissedWhenFragmentIsGoneText(false);
    }

    @Test
    public void uiDismissedWhenSavableFragmentIsGone() throws Exception {
        uiDismissedWhenFragmentIsGoneText(true);
    }

    private void uiDismissedWhenFragmentIsGoneText(boolean savable) throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        final CannedFillResponse.Builder response = new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField("editText1", "whatever")
                        .setPresentation(createPresentation("dataset1"))
                        .build());
        if (savable) {
            response.setRequiredSavableIds(SAVE_DATA_TYPE_GENERIC, "editText2");
        }

        sReplier.addResponse(response.build());

        // Trigger autofill
        mActivity.syncRunOnUiThread(() -> {
            mEditText2.requestFocus();
            mEditText1.requestFocus();
        });

        // Check UI is shown, but don't select it.
        sReplier.getNextFillRequest();
        sUiBot.assertDatasets("dataset1");

        // Switch fragments
        sReplier.addResponse(NO_RESPONSE);
        mActivity.syncRunOnUiThread(
                () -> mActivity.getFragmentManager().beginTransaction().replace(
                        R.id.rootContainer, new FragmentWithMoreEditTexts(),
                        FRAGMENT_TAG).commitNow());
        // Make sure UI is gone.
        sReplier.getNextFillRequest();
        sUiBot.assertNoDatasets();
    }

    // TODO: add similar tests for fragment with virtual view
}
