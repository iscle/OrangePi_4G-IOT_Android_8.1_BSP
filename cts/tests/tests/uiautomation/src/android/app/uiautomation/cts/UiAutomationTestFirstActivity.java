/*
* Copyright (C) 2014 The Android Open Source Project
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

package android.app.uiautomation.cts;

import android.app.uiautomation.cts.R;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ListView;

/**
* Activity for testing the UiAutomatoin APIs.
*/
public class UiAutomationTestFirstActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ui_automation_test);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

        String[] cheeses = getResources().getStringArray(R.array.some_cheeses);
        ArrayAdapter<String> cheeseAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, cheeses);

        ListView listView = (ListView) findViewById(R.id.list_view);
        listView.setAdapter(cheeseAdapter);
    }
}
