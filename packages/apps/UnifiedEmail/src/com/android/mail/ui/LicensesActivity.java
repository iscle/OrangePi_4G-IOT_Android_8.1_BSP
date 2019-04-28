/**
 * Copyright (c) 2014, Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.mail.ui;

import android.app.ActionBar;
import android.app.Activity;
import android.os.Bundle;
import android.view.MenuItem;
import android.webkit.WebView;

import com.android.mail.R;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;

/**
 * This activity displays the Software Licenses of the libraries used by this software.
 */
public class LicensesActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.licenses_activity);

        // Enable back navigation
        final ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        InputStream input = null;
        String licenseHTML = null;
        try {
            // read the raw license String from the license HTML file
            input = getResources().openRawResource(R.raw.licenses);
            final String license = IOUtils.toString(input, "UTF-8");

            // encode the license string for display as HTML in the webview
            licenseHTML = URLEncoder.encode(license, "UTF-8").replace("+", "%20");
        } catch (IOException e) {
            LogUtils.e(LogTag.getLogTag(), e, "Error reading licence file");
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ioe) {
                    // best attempt only
                }
            }
        }

        // set the license HTML into the webview
        final WebView webview = (WebView) findViewById(R.id.webview);
        webview.loadData(licenseHTML, "text/html", null);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                finish();
                return true;
            }
            default: return super.onOptionsItemSelected(item);
        }
    }
}