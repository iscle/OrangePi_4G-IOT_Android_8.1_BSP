/*******************************************************************************
 *      Copyright (C) 2014 Google Inc.
 *      Licensed to The Android Open Source Project.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *******************************************************************************/

package com.android.mail.ui.settings;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.text.TextUtils;

import java.util.Set;

/**
 * Activity that allows directly launching into the preference activity, from external parties
 */
public class PublicPreferenceActivity extends Activity {

    // TODO: Temporary. Once the app-specific preference activities are deleted, this will no longer
    // be needed. This is set by the application subclasses
    public static Class<? extends MailPreferenceActivity> sPreferenceActivityClass;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();

        // We need to remove the extra that allows a fragment to be directly opened
        intent.removeExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT);
        intent.removeExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT_ARGUMENTS);
        intent.removeExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT_SHORT_TITLE);
        intent.removeExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT_TITLE);

        // Remove any fragment specifier from the data uri
        final Uri dataUri = intent.getData();
        if (dataUri != null) {
            final String fragmentIdStr =
                    dataUri.getQueryParameter(MailPreferenceActivity.PREFERENCE_FRAGMENT_ID);
            if (fragmentIdStr != null) {
                final Set<String> paramNames = dataUri.getQueryParameterNames();

                final Uri.Builder builder = dataUri.buildUpon().clearQuery();

                for (String param : paramNames) {
                    if (!TextUtils.equals(param, MailPreferenceActivity.PREFERENCE_FRAGMENT_ID)) {
                        builder.appendQueryParameter(param, dataUri.getQueryParameter(param));
                    }
                }
                intent.setData(builder.build());
            }
        }

        if (sPreferenceActivityClass == null) {
            sPreferenceActivityClass = MailPreferenceActivity.class;
        }
        // Force this intent to be sent to the appropriate MailPreferenceActivity class
        intent.setClass(this, sPreferenceActivityClass);

        startActivity(intent);
        finish();
    }
}
