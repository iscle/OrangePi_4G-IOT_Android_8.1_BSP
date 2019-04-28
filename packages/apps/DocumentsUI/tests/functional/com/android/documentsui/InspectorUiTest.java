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
package com.android.documentsui;

import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;

import com.android.documentsui.bots.UiBot;
import com.android.documentsui.inspector.InspectorActivity;

public class InspectorUiTest extends ActivityTest<InspectorActivity> {

    private static final String TEST_DOC_NAME = "test.txt";

    public InspectorUiTest() {
        super(InspectorActivity.class);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @Override
    public void launchActivity() {
        if (!features.isInspectorEnabled()) {
            return;
        }
        final Intent intent = context.getPackageManager().getLaunchIntentForPackage(
                UiBot.TARGET_PKG);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        Uri uri = DocumentsContract.buildDocumentUri(InspectorProvider.AUTHORITY, TEST_DOC_NAME);
        intent.setData(uri);
        setActivityIntent(intent);
        getActivity();
    }

    public void testDisplayFileName() throws Exception {
        if (!features.isInspectorEnabled()) {
            return;
        }
        bots.inspector.assertTitle("test.txt");
    }

    public void testDisplayFileType() throws Exception {
        if (!features.isInspectorEnabled()) {
            return;
        }
        bots.inspector.assertRowPresent(getActivity().getString(R.string.sort_dimension_file_type),
                "vnd.android.document/directory", getActivity());
    }
}
