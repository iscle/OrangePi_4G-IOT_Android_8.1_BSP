/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.cts.verifier;

import com.android.cts.verifier.TestListAdapter.TestListItem;

import android.app.ListActivity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.ListView;

/** {@link ListActivity} that displays a list of manual tests. */
public abstract class AbstractTestListActivity extends ListActivity {
    private static final int LAUNCH_TEST_REQUEST_CODE = 9001;

    protected TestListAdapter mAdapter;

    protected void setTestListAdapter(TestListAdapter adapter) {
        mAdapter = adapter;
        setListAdapter(mAdapter);
        mAdapter.loadTestResults();
    }

    private Intent getIntent(int position) {
        TestListItem item = mAdapter.getItem(position);
        return item.intent;
    }

    @Override
    protected final void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        handleActivityResult(requestCode, resultCode, data);
    }

    /** Override this in subclasses instead of onActivityResult */
    protected void handleActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case LAUNCH_TEST_REQUEST_CODE:
                handleLaunchTestResult(resultCode, data);
                break;

            default:
                throw new IllegalArgumentException("Unknown request code: " + requestCode);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if ((getResources().getConfiguration().uiMode & Configuration.UI_MODE_TYPE_MASK)
              == Configuration.UI_MODE_TYPE_TELEVISION) {
            getWindow().requestFeature(Window.FEATURE_OPTIONS_PANEL);
        }
        setContentView(R.layout.list_content);
    }

    protected void handleLaunchTestResult(int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            TestResult testResult = TestResult.fromActivityResult(resultCode, data);
            mAdapter.setTestResult(testResult);
        }
    }

    /** Launch the activity when its {@link ListView} item is clicked. */
    @Override
    protected final void onListItemClick(ListView listView, View view, int position, long id) {
        super.onListItemClick(listView, view, position, id);
        handleItemClick(listView, view, position, id);
    }

    /** Override this in subclasses instead of onListItemClick */
    protected void handleItemClick(ListView listView, View view, int position, long id) {
        Intent intent = getIntent(position);
        startActivityForResult(intent, LAUNCH_TEST_REQUEST_CODE);
    }
}
