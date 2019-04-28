/*
 * Copyright 2016, The Android Open Source Project
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

package com.android.managedprovisioning.preprovisioning;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.ACTION_START_ENCRYPTION;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.provider.Settings;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.TestInstrumentationRunner;
import com.android.managedprovisioning.common.CustomizationVerifier;
import com.android.managedprovisioning.model.ProvisioningParams;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link EncryptDeviceActivity}.
 */
@SmallTest
public class EncryptDeviceActivityTest {

    private static final ComponentName ADMIN = new ComponentName("com.test.admin", ".Receiver");
    private static final int SAMPLE_COLOR = Color.parseColor("#d40000");
    private static final ProvisioningParams PROFILE_OWNER_PARAMS = new ProvisioningParams.Builder()
            .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
            .setMainColor(SAMPLE_COLOR)
            .setDeviceAdminComponentName(ADMIN)
            .build();
    private static final ProvisioningParams DEVICE_OWNER_PARAMS = new ProvisioningParams.Builder()
            .setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE)
            .setMainColor(SAMPLE_COLOR)
            .setDeviceAdminComponentName(ADMIN)
            .build();
    private static final Intent PROFILE_OWNER_INTENT = new Intent()
            .putExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS, PROFILE_OWNER_PARAMS);
    private static final Intent DEVICE_OWNER_INTENT = new Intent()
            .putExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS, DEVICE_OWNER_PARAMS);

    @Rule
    public ActivityTestRule<EncryptDeviceActivity> mActivityRule = new ActivityTestRule<>(
            EncryptDeviceActivity.class, true /* Initial touch mode  */,
            false /* Lazily launch activity */);

    @Mock EncryptionController mController;
    private static int sRotationLocked;

    @BeforeClass
    public static void setUpClass() {
        // Stop the activity from rotating in order to keep hold of the context
        Context context = InstrumentationRegistry.getTargetContext();

        sRotationLocked = Settings.System.getInt(context.getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION, 0);
        Settings.System.putInt(context.getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION, 0);
    }

    @AfterClass
    public static void tearDownClass() {
        // Reset the rotation value back to what it was before the test
        Context context = InstrumentationRegistry.getTargetContext();

        Settings.System.putInt(context.getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION, sRotationLocked);
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        TestEncryptionActivity.sController = mController;
        TestEncryptionActivity.sLastLaunchedIntent = null;

        TestInstrumentationRunner.registerReplacedActivity(EncryptDeviceActivity.class,
                TestEncryptionActivity.class);
    }

    @After
    public void tearDown() {
        TestInstrumentationRunner.unregisterReplacedActivity(EncryptDeviceActivity.class);
    }

    @Test
    public void testProfileOwner() {
        // WHEN launching EncryptDeviceActivity with a profile owner intent
        Activity activity = mActivityRule.launchActivity(PROFILE_OWNER_INTENT);

        // THEN the profile owner description should be present
        onView(withId(R.id.encrypt_main_text))
                .check(matches(withText(R.string.encrypt_device_text_for_profile_owner_setup)));

        // status bar color matches the one from intent parameters
        new CustomizationVerifier(activity).assertStatusBarColorCorrect(SAMPLE_COLOR);

        // WHEN pressing the encrypt button
        onView(withId(R.id.encrypt_button)).perform(click());

        // THEN encryption reminder should be set
        verify(mController).setEncryptionReminder(PROFILE_OWNER_PARAMS);

        // THEN encryption activity should be started
        assertEquals(ACTION_START_ENCRYPTION,
                TestEncryptionActivity.sLastLaunchedIntent.getAction());
    }

    @Test
    public void testDeviceOwner() {
        // WHEN launching EncryptDeviceActivity with a profile owner intent
        Activity activity = mActivityRule.launchActivity(DEVICE_OWNER_INTENT);

        // THEN the profile owner description should be present
        onView(withId(R.id.encrypt_main_text))
                .check(matches(withText(R.string.encrypt_device_text_for_device_owner_setup)));

        // status bar color matches the one from intent parameters
        new CustomizationVerifier(activity).assertStatusBarColorCorrect(SAMPLE_COLOR);

        // WHEN pressing the encrypt button
        onView(withId(R.id.encrypt_button)).perform(click());

        // THEN encryption reminder should be set
        verify(mController).setEncryptionReminder(DEVICE_OWNER_PARAMS);

        // THEN encryption activity should be started
        assertEquals(ACTION_START_ENCRYPTION,
                TestEncryptionActivity.sLastLaunchedIntent.getAction());
    }

    @Test
    public void testNoParams() {
        // WHEN launching EncryptDeviceActivity without a params object
        mActivityRule.launchActivity(new Intent());

        // THEN the activity should finish immediately
        assertTrue(mActivityRule.getActivity().isFinishing());
    }
}
