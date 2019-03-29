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

import android.app.Activity;
import android.app.assist.AssistStructure;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.autofill.AutofillManager;

/**
 * An activity that authenticates on button press
 */
public class ManualAuthenticationActivity extends Activity {
    private static CannedFillResponse sResponse;
    private static CannedFillResponse.CannedDataset sDataset;

    public static void setResponse(CannedFillResponse response) {
        sResponse = response;
        sDataset = null;
    }

    public static void setDataset(CannedFillResponse.CannedDataset dataset) {
        sDataset = dataset;
        sResponse = null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.single_button_activity);

        findViewById(R.id.button).setOnClickListener((v) -> {
            AssistStructure structure = getIntent().getParcelableExtra(
                    AutofillManager.EXTRA_ASSIST_STRUCTURE);
            if (structure != null) {
                Parcelable result;
                if (sResponse != null) {
                    result = sResponse.asFillResponse(
                            (id) -> Helper.findNodeByResourceId(structure, id));
                } else if (sDataset != null) {
                    result = sDataset.asDataset(
                            (id) -> Helper.findNodeByResourceId(structure, id));
                } else {
                    throw new IllegalStateException("no dataset or response");
                }

                // Pass on the auth result
                Intent intent = new Intent();
                intent.putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, result);
                setResult(RESULT_OK, intent);
            }

            // Done
            finish();
        });
    }
}
