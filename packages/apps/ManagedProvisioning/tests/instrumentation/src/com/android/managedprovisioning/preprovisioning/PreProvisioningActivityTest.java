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
package com.android.managedprovisioning.preprovisioning;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LOGO_URI;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_MAIN_COLOR;

import static com.android.managedprovisioning.e2eui.ManagedProfileAdminReceiver.COMPONENT_NAME;
import static com.android.managedprovisioning.model.CustomizationParams.DEFAULT_COLOR_ID_BUTTON;
import static com.android.managedprovisioning.model.CustomizationParams.DEFAULT_COLOR_ID_DO;
import static com.android.managedprovisioning.model.CustomizationParams.DEFAULT_COLOR_ID_MP;
import static com.android.managedprovisioning.model.CustomizationParams.DEFAULT_COLOR_ID_SWIPER;

import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.Assert.assertThat;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.view.View;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.TestInstrumentationRunner;
import com.android.managedprovisioning.common.CustomizationVerifier;
import com.android.managedprovisioning.common.UriBitmap;
import com.android.managedprovisioning.preprovisioning.terms.TermsActivity;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;

@SmallTest
// TODO: Currently only color and logo functionality are covered. Fill in the rest (b/32131665).
public class PreProvisioningActivityTest {
    private static final int SAMPLE_COLOR = Color.parseColor("#ffd40000");

    @Rule
    public ActivityTestRule<PreProvisioningActivity> mActivityRule = new ActivityTestRule<>(
            PreProvisioningActivity.class, true, false);

    @BeforeClass
    public static void setUpClass() {
        TestInstrumentationRunner.registerReplacedActivity(PreProvisioningActivity.class,
                (classLoader, className, intent) -> new PreProvisioningActivity(
                        activity -> new PreProvisioningController(activity, activity) {
                            @Override
                            protected boolean checkDevicePolicyPreconditions() {
                                return true;
                            }

                            @Override
                            protected boolean verifyActionAndCaller(Intent intent, String caller) {
                                return true;
                            }
                        }, null));
    }

    @AfterClass
    public static void tearDownClass() {
        TestInstrumentationRunner.unregisterReplacedActivity(TermsActivity.class);
    }

    @Test
    public void profileOwnerDefaultColors() {
        Activity activity = mActivityRule.launchActivity(
                createIntent(ACTION_PROVISION_MANAGED_PROFILE, null));
        CustomizationVerifier v = new CustomizationVerifier(activity);
        v.assertStatusBarColorCorrect(activity.getColor(DEFAULT_COLOR_ID_MP));
        v.assertSwiperColorCorrect(activity.getColor(DEFAULT_COLOR_ID_SWIPER));
        v.assertNextButtonColorCorrect(activity.getColor(DEFAULT_COLOR_ID_BUTTON));
    }

    @Test
    public void profileOwnerCustomColors() {
        Activity activity = mActivityRule.launchActivity(
                createIntent(ACTION_PROVISION_MANAGED_PROFILE, SAMPLE_COLOR));
        CustomizationVerifier v = new CustomizationVerifier(activity);
        v.assertStatusBarColorCorrect(SAMPLE_COLOR);
        v.assertSwiperColorCorrect(SAMPLE_COLOR);
        v.assertNextButtonColorCorrect(SAMPLE_COLOR);
    }

    @Test
    public void deviceOwnerDefaultColorsAndLogo() {
        Activity activity = mActivityRule.launchActivity(
                createIntent(ACTION_PROVISION_MANAGED_DEVICE, null));
        CustomizationVerifier v = new CustomizationVerifier(activity);
        int color = activity.getColor(DEFAULT_COLOR_ID_DO);
        v.assertStatusBarColorCorrect(color);
        v.assertDefaultLogoCorrect(color);
        v.assertNextButtonColorCorrect(color);
    }

    @Test
    public void deviceOwnerCustomColor() {
        Activity activity = mActivityRule.launchActivity(
                createIntent(ACTION_PROVISION_MANAGED_DEVICE, SAMPLE_COLOR));
        CustomizationVerifier v = new CustomizationVerifier(activity);
        v.assertStatusBarColorCorrect(SAMPLE_COLOR);
        v.assertDefaultLogoCorrect(SAMPLE_COLOR);
        v.assertNextButtonColorCorrect(SAMPLE_COLOR);
    }

    @Test
    public void deviceOwnerCustomLogo() throws IOException {
        UriBitmap expectedLogo = UriBitmap.createSimpleInstance();

        Activity activity = mActivityRule.launchActivity(
                createIntent(ACTION_PROVISION_MANAGED_DEVICE, SAMPLE_COLOR).putExtra(
                        EXTRA_PROVISIONING_LOGO_URI, expectedLogo.getUri()));
        CustomizationVerifier v = new CustomizationVerifier(activity);
        v.assertCustomLogoCorrect(expectedLogo.getBitmap());
    }

    @Test
    public void profileOwnerWholeLayoutIsAdjusted() {
        Activity activity = mActivityRule.launchActivity(
                createIntent(ACTION_PROVISION_MANAGED_PROFILE, null));
        View content = activity.findViewById(R.id.intro_po_content);
        View viewport = activity.findViewById(R.id.suw_layout_content);
        assertThat("Width", content.getWidth(), lessThanOrEqualTo(viewport.getWidth()));

        int animationHeight = activity.findViewById(R.id.animated_info).getHeight();
        int minHeight = activity.getResources().getDimensionPixelSize(
                R.dimen.intro_animation_min_height);

        if (animationHeight >= minHeight) {
            assertThat("Height", content.getHeight(), lessThanOrEqualTo(viewport.getHeight()));
        }
    }

    private Intent createIntent(String provisioningAction, Integer mainColor) {
        Intent intent = new Intent(provisioningAction).putExtra(
                EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, COMPONENT_NAME);
        if (mainColor != null) {
            intent.putExtra(EXTRA_PROVISIONING_MAIN_COLOR, mainColor);
        }
        return intent;
    }
}