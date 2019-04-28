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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import com.android.mail.R;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;

/**
 * This activity customizes its ActionBar and leaves the heavy lifting to its child HelpFragment.
 */
public class HelpActivity extends Activity {

    private static final String LOG_TAG = LogTag.getLogTag();

    public static final String PARAM_HELP_URL = "help.url";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.help_activity);

        // Customize the action bar behavior and display
        final ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.help_and_info);

            final PackageManager packageManager = getPackageManager();
            if (packageManager != null) {
                try {
                    final String appName = getString(R.string.app_name);
                    final PackageInfo pi = packageManager.getPackageInfo(getPackageName(), 0);
                    actionBar.setSubtitle(getString(R.string.version, appName, pi.versionName));
                } catch (PackageManager.NameNotFoundException e) {
                    // Can't find version? Oh well. Just leave it blank.
                    LogUtils.wtf(LOG_TAG, e, "Unable to locate application version.");
                }
            }
        }
    }
}