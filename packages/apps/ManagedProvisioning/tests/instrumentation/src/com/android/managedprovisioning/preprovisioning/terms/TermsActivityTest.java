/*
 * Copyright 2017, The Android Open Source Project
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
package com.android.managedprovisioning.preprovisioning.terms;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;

import static org.hamcrest.core.IsNot.not;

import android.content.ComponentName;
import android.content.Intent;
import android.provider.Settings;
import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.ViewAssertion;
import android.support.test.filters.FlakyTest;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.TestInstrumentationRunner;
import com.android.managedprovisioning.model.DisclaimersParam;
import com.android.managedprovisioning.model.ProvisioningParams;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Tests for {@link TermsActivity}.
 */
@SmallTest
public class TermsActivityTest {

    private static final String HEADER_0_TOP = InstrumentationRegistry.getTargetContext()
        .getString(R.string.work_profile_info);
    private static final String HEADER_1 = "header1";
    private static final String HEADER_2 = "header2";
    private static final String HEADER_3_BOTTOM = "header3";
    private static final String CONTENT_1 = "content1";
    private static final String CONTENT_2_HTML =
            "<ol>" + "<li>" + "<b>" + "item" + "</b>" + "1" + "<li>" + "item" + "<i>" + "2";
    private static final String CONTENT_3 = "content3";

    private final Map<String, String> mPathToContent = new HashMap<>();

    @Rule
    public ActivityTestRule<TermsActivity> mActivityRule = new ActivityTestRule<>(
            TermsActivity.class, true, false);

    @Before
    public void setUp() throws Settings.SettingNotFoundException {
        TestInstrumentationRunner.registerReplacedActivity(TermsActivity.class,
                (classLoader, className, intent) -> new TermsActivity(
                        (file) -> mPathToContent.get(file.getPath()), null));
        mPathToContent.clear();
    }

    @After
    public void tearDown() {
        TestInstrumentationRunner.unregisterReplacedActivity(TermsActivity.class);
    }

    // TODO(b/35613314): remove @FlakyTest once bug fixed
    @FlakyTest
    @Test
    public void expanderTest() throws InterruptedException {
        // given an intent with disclaimers
        DisclaimersParam.Disclaimer[] extraDisclaimers = {
                setUpDisclaimer(HEADER_1, "path1", CONTENT_1),
                setUpDisclaimer(HEADER_2, "path2", CONTENT_2_HTML),
                setUpDisclaimer(HEADER_3_BOTTOM, "path3", CONTENT_3)};
        Intent intent = createIntent(ACTION_PROVISION_MANAGED_PROFILE, extraDisclaimers);

        // when an activity is launched
        mActivityRule.launchActivity(intent);

        // then all headers are displayed
        onView(withText(HEADER_0_TOP)).check(matches(isDisplayed()));
        for (DisclaimersParam.Disclaimer d : extraDisclaimers) {
            onView(withText(d.mHeader)).check(matches(isDisplayed()));
        }

        // none of the content is displayed
        onView(withId(R.id.disclaimer_content)).check(isNotDisplayed());

        // when clicking on one, it expands
        onView(withText(HEADER_1)).perform(click());
        onView(withText(CONTENT_1)).check(matches(isDisplayed()));

        // when clicking on another, it collapses the first one, and expands the other one
        onView(withText(HEADER_3_BOTTOM)).perform(click());
        onView(withText(CONTENT_1)).check(isNotDisplayed());
        onView(withText(CONTENT_3)).check(matches(isDisplayed()));

        // TODO: replace with a proper fix; all attempts failed so far
        // Not tried yet: https://google.github.io/android-testing-support-library/docs/espresso/idling-resource/
        Thread.sleep(500);

        // when clicking again on the first one, the last one collapses
        onView(withText(HEADER_1)).perform(click());
        onView(withText(CONTENT_3)).check(isNotDisplayed());
        onView(withText(CONTENT_1)).check(matches(isDisplayed()));

        // check that HTML in disclaimers is respected
        onView(withText("header2")).perform(click());
        onView(withId(R.id.disclaimer_content)).check(matches(withText("item1\nitem2\n")));
    }

    /**
     * As long as as the item is not visible to the user, we're happy
     */
    private ViewAssertion isNotDisplayed() {
        return (view, e) -> {
            if (view != null) {
                matches(not(isDisplayed()));
            }
        };
    }

    private Intent createIntent(String provisioningAction,
            DisclaimersParam.Disclaimer... disclaimers) {
        DisclaimersParam disclaimersParam = new DisclaimersParam.Builder().setDisclaimers(
                disclaimers).build();
        ProvisioningParams params = new ProvisioningParams.Builder()
                .setProvisioningAction(provisioningAction)
                .setDisclaimersParam(disclaimersParam)
                .setDeviceAdminComponentName(new ComponentName("test.pkg.name.controller", "Main"))
                .build();
        Intent intent = new Intent();
        intent.putExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS, params);
        return intent;
    }

    private DisclaimersParam.Disclaimer setUpDisclaimer(String header, String contentPath,
            String content) {
        mPathToContent.put(contentPath, content);
        return new DisclaimersParam.Disclaimer(header, contentPath);
    }
}

