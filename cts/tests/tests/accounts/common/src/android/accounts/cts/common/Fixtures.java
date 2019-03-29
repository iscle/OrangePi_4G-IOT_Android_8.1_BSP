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

package android.accounts.cts.common;

import android.accounts.Account;

import java.util.ArrayList;
import java.util.List;

/**
 * Constants shared amongst account hostside tests.
 */
public final class Fixtures {

    public static final String TYPE_CUSTOM = "android.accounts.test.custom";
    public static final String TYPE_STANDARD = "android.accounts.test.standard";
    public static final String TYPE_DEFAULT = "android.accounts.test.default";

    public static final String TYPE_STANDARD_UNAFFILIATED =
            "android.accounts.test.standard.unaffiliated";

    public static final String PREFIX_TOKEN = "token:";
    public static final String PREFIX_PASSWORD = "password:";
    public static final String PREFIX_STATUS_TOKEN = "status_token:";

    public static final String SUFFIX_NAME_FIXTURE = "fixture.com";
    public static final String SUFFIX_NAME_TEST = "test.com";

    public static final String PREFIX_NAME_SUCCESS = "success_on_return";
    public static final String PREFIX_NAME_ERROR = "error";
    public static final String PREFIX_NAME_INTERVENE = "intervene";

    private static final String[] accountNamePrefixes = new String[] {
            PREFIX_NAME_SUCCESS,
            PREFIX_NAME_ERROR,
            PREFIX_NAME_INTERVENE
    };

    public static final Account ACCOUNT_UNAFFILIATED_FIXTURE_SUCCESS = new Account(
            PREFIX_NAME_SUCCESS + "@" + SUFFIX_NAME_FIXTURE,
            TYPE_STANDARD_UNAFFILIATED);

    public static final Account ACCOUNT_DEFAULT = new Account(
            PREFIX_NAME_SUCCESS + "@" + SUFFIX_NAME_FIXTURE,
            TYPE_DEFAULT);

    public static List<String> getFixtureAccountNames() {
        List<String> accountNames = new ArrayList<>(accountNamePrefixes.length);
        for (String prefix : accountNamePrefixes) {
            accountNames.add(prefix + "@" + SUFFIX_NAME_FIXTURE);
        }
        return accountNames;
    }

    public static final int TEST_SUPPORT_RESULT_SUCCESS = 1;
    public static final int TEST_SUPPORT_RESULT_FAIL = 2;

    public static final String KEY_ACCOUNT_NAME = "test:account_name";

    public static final String KEY_CALLBACK = "test:callback";
    public static final String KEY_CALLBACK_REQUIRED = "test:callback_required";
    public static final String KEY_RESULT = "test:result";
    public static final String KEY_TOKEN_EXPIRY = "test:token_duration";

    public static final String KEY_ACCOUNT_SESSION_BUNDLE = "test:account_session_bundle";
    public static final String ACCOUNT_STATUS_TOKEN_UNAFFILIATED =
            "android.accounts.cts.unaffiliated.account.status.token";

    private Fixtures() {}
}
