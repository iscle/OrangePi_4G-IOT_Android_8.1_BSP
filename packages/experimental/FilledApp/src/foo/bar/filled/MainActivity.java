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

package foo.bar.filled;

import android.annotation.NonNull;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.autofill.AutofillManager;
import android.widget.Button;

import foo.bar.filled.R;

public class MainActivity extends Activity {
    public static final String LOG_TAG = "PrintActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!CustomLinearLayout.VIRTUAL) {
            findViewById(R.id.username).setImportantForAutofill(
                    View.IMPORTANT_FOR_AUTOFILL_AUTO);
            findViewById(R.id.password).setImportantForAutofill(
                    View.IMPORTANT_FOR_AUTOFILL_AUTO);
        }

        Button finishButton = findViewById(R.id.finish);
        finishButton.setOnClickListener((view) -> finish());

        AutofillManager autofillManager = getSystemService(AutofillManager.class);
        autofillManager.registerCallback(new AutofillManager.AutofillCallback() {
            @Override
            public void onAutofillEvent(@NonNull View view, int event) {
                super.onAutofillEvent(view, event);
            }
        });
    }
}
