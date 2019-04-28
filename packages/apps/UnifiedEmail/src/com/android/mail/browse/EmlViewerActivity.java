/*
 * Copyright (C) 2013 Google Inc.
 * Licensed to The Android Open Source Project.
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

package com.android.mail.browse;

import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;

import com.android.mail.R;
import com.android.mail.analytics.Analytics;
import com.android.mail.ui.AccountFeedbackActivity;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.MimeType;

public class EmlViewerActivity extends AccountFeedbackActivity {
    private static final String LOG_TAG = LogTag.getLogTag();

    private static final String FRAGMENT_TAG = "eml_message_fragment";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        final String action = intent.getAction();
        final String type = intent.getType();

        if (savedInstanceState == null) {
            if (Intent.ACTION_VIEW.equals(action) &&
                    MimeType.isEmlMimeType(type)) {
                final FragmentTransaction transaction = getFragmentManager().beginTransaction();
                transaction.add(R.id.root, EmlMessageViewFragment.newInstance(
                        intent.getData(), mAccountUri), FRAGMENT_TAG);
                transaction.commit();
                Analytics.getInstance().sendEvent("eml_viewer", null, null, 0);
            } else {
                LogUtils.wtf(LOG_TAG,
                        "Entered EmlViewerActivity with wrong intent action or type: %s, %s",
                        action, type);
                finish(); // we should not be here. bail out. bail out.
                return;
            }
        }
    }
}
