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

import android.content.Intent;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;
import android.test.suitebuilder.annotation.MediumTest;

import com.android.mail.preferences.MailPrefs;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.AutoAdvance;

public class GeneralPrefsFragmentTest
        extends ActivityInstrumentationTestCase2<MailPreferenceActivity> {

    private static final String PREFS_NAME_TEST = "UnifiedEmailTest";

    public GeneralPrefsFragmentTest() {
        super(MailPreferenceActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        final Intent i = new Intent();
        i.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT,
                "com.android.mail.ui.settings.GeneralPrefsFragment");
        final Bundle b = new Bundle(1);
        b.putBoolean(GeneralPrefsFragment.CALLED_FROM_TEST, true);
        i.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT_ARGUMENTS, b);
        setActivityIntent(i);
        final MailPreferenceActivity activity = getActivity();
        getInstrumentation().waitForIdleSync();
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                activity.getFragmentManager().executePendingTransactions();
            }
        });
        final GeneralPrefsFragment fragment = activity.getGeneralPrefsFragment();
        fragment.mMailPrefs = new MailPrefs(activity, PREFS_NAME_TEST);
    }

    @UiThreadTest
    @MediumTest
    public void testChangeAutoAdvance() throws Throwable {
        final MailPreferenceActivity activity = getActivity();
        final GeneralPrefsFragment fragment = activity.getGeneralPrefsFragment();
        final MailPrefs mailPrefs = fragment.mMailPrefs;
        final ListPreference autoAdvancePref = (ListPreference) fragment
                .findPreference(GeneralPrefsFragment.AUTO_ADVANCE_WIDGET);

        fragment.onPreferenceChange(autoAdvancePref, UIProvider.AUTO_ADVANCE_MODE_OLDER);
        assertEquals(mailPrefs.getAutoAdvanceMode(), AutoAdvance.OLDER);

        fragment.onPreferenceChange(autoAdvancePref, UIProvider.AUTO_ADVANCE_MODE_NEWER);
        assertEquals(mailPrefs.getAutoAdvanceMode(), AutoAdvance.NEWER);
    }

}
