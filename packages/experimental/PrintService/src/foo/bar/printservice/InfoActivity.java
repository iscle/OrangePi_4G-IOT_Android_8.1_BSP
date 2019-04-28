/*
 * Copyright (C) 2015 The Android Open Source Project
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

package foo.bar.printservice;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.printservice.PrintService;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**
 * Dummy activity showing more information about a printer
 */
public class InfoActivity extends Activity {
    /** Intent-extra-key used for the name of the printer */
    public static final String PRINTER_NAME = "PRINTER_NAME";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_info);

        TextView text = (TextView) findViewById(R.id.info);

        text.setText(getResources().getString(R.string.info,
                getIntent().getStringExtra(PRINTER_NAME)));

        if (getIntent().getBooleanExtra(PrintService.EXTRA_CAN_SELECT_PRINTER,
                false)) {
            Button selectButton = (Button) findViewById(R.id.select_button);

            selectButton.setVisibility(View.VISIBLE);
            selectButton.setOnClickListener(v -> {
                        setResult(Activity.RESULT_OK,
                                (new Intent()).putExtra(PrintService.EXTRA_SELECT_PRINTER, true));
                        finish();
                    }
            );
        }
    }
}
