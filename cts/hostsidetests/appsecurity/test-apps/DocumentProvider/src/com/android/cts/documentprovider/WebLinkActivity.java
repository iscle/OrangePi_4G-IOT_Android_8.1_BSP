/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.cts.documentprovider;

import android.app.Activity;
import android.app.AlertDialog.Builder;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

public class WebLinkActivity extends Activity {
    public static final String EXTRA_DOCUMENT_ID =
            "com.android.cts.documentprovider.EXTRA_DOCUMENT_ID";
    private static final Uri FAKE_WEB_LINK = Uri.parse(
            "http://www.foobar.com/shared/SW33TCH3RR13S");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final String documentId = getIntent().getStringExtra(EXTRA_DOCUMENT_ID);
        final String email = getIntent().getStringExtra(Intent.EXTRA_EMAIL);

        new AlertDialog.Builder(this)
            .setTitle("Grant permissions to this file to " + email + "?")
            .setMessage(documentId)
            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    final Intent intent = new Intent();
                    intent.setData(FAKE_WEB_LINK);
                    setResult(RESULT_OK, intent);
                    finish();
                }
             })
            .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    setResult(RESULT_CANCELED, null);
                    finish();
                }
             })
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show();
    }
}
