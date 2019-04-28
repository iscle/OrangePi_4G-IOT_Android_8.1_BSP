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
package com.android.mail.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.text.TextUtils;

import java.util.Locale;

/**
 * Constructs the URL for the context sensitive help page for this mail application. The URL will be
 * specific to the user's language, application, application version and topic. For example:
 *
 * <p>http://support.example.com/email/?hl=en-us&p=androidhelp&version=1 for the top level english
 * help page for version 1 of the mail application</p>
 * <p>http://support.example.com/email/?hl=fr-fr&p=email_compose&version=2 for the compose email
 * french help page for version 2 of the mail application</p>
 */
public final class HelpUrl {
    private static final String LOG_TAG = LogTag.getLogTag();

    private HelpUrl() {}

    /**
     * Constructs the URL for the context sensitive help page for this mail application.
     *
     * @param context a context from which to read resources
     * @param topic describes the help topic to display; this String cannot be empty.
     * @return Url for the Help page that is specific to a language, application, version and topic
     */
    public static Uri getHelpUrl(final Context context, Uri helpUri, String topic) {
        if (TextUtils.isEmpty(topic)) {
            throw new IllegalArgumentException("topic must be non-empty");
        }

        // %locale% is a special variable encoded in the Uri that should be replaced if it exists
        if (helpUri.toString().contains("%locale%")) {
            helpUri = Uri.parse(helpUri.toString().replace("%locale%", getLocale()));
        }

        final Uri.Builder builder = helpUri.buildUpon();
        builder.appendQueryParameter("p", topic);
        builder.appendQueryParameter("version", getVersion(context));

        return builder.build();
    }

    private static String getLocale() {
        final Locale locale = Locale.getDefault();
        return locale.getLanguage() + "-" + locale.getCountry().toLowerCase();
    }

    private static String getVersion(final Context context) {
        final String packageName = context.getApplicationInfo().packageName;
        try {
            final PackageInfo pi = context.getPackageManager().getPackageInfo(packageName, 0);
            return String.valueOf(pi.versionCode);
        } catch (PackageManager.NameNotFoundException e) {
            LogUtils.e(LOG_TAG, "Error finding package name for application" + packageName);
            throw new IllegalStateException("unable to determine package name for application");
        }
    }
}
