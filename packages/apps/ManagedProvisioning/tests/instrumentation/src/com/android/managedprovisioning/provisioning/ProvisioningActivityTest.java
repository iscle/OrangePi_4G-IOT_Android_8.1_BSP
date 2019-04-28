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

package com.android.managedprovisioning.provisioning;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.ACTION_STATE_USER_SETUP_COMPLETE;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.Espresso.pressBack;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.intent.Intents.intended;
import static android.support.test.espresso.intent.matcher.IntentMatchers.hasAction;
import static android.support.test.espresso.intent.matcher.IntentMatchers.hasComponent;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static com.android.managedprovisioning.common.LogoUtils.saveOrganisationLogo;
import static com.android.managedprovisioning.model.CustomizationParams.DEFAULT_COLOR_ID_DO;
import static com.android.managedprovisioning.model.CustomizationParams.DEFAULT_COLOR_ID_MP;
import static java.util.Arrays.asList;
import static org.hamcrest.core.AllOf.allOf;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.Manifest.permission;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.intent.Intents;
import android.support.test.espresso.intent.rule.IntentsTestRule;
import android.support.test.filters.SmallTest;
import android.support.test.runner.lifecycle.Stage;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.TestInstrumentationRunner;
import com.android.managedprovisioning.common.CustomizationVerifier;
import com.android.managedprovisioning.common.LogoUtils;
import com.android.managedprovisioning.common.UriBitmap;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.testcommon.ActivityLifecycleWaiter;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.hamcrest.MockitoHamcrest;

/**
 * Unit tests for {@link ProvisioningActivity}.
 */
@SmallTest
public class ProvisioningActivityTest {
    private static final String ADMIN_PACKAGE = "com.test.admin";
    private static final String TEST_PACKAGE = "com.android.managedprovisioning.tests";
    private static final ComponentName ADMIN = new ComponentName(ADMIN_PACKAGE, ".Receiver");
    private static final ComponentName TEST_ACTIVITY = new ComponentName(TEST_PACKAGE,
            EmptyActivity.class.getCanonicalName());
    public static final ProvisioningParams PROFILE_OWNER_PARAMS = new ProvisioningParams.Builder()
            .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
            .setDeviceAdminComponentName(ADMIN)
            .build();
    public static final ProvisioningParams DEVICE_OWNER_PARAMS = new ProvisioningParams.Builder()
            .setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE)
            .setDeviceAdminComponentName(ADMIN)
            .build();
    private static final ProvisioningParams NFC_PARAMS = new ProvisioningParams.Builder()
            .setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE)
            .setDeviceAdminComponentName(ADMIN)
            .setStartedByTrustedSource(true)
            .setIsNfc(true)
            .build();
    private static final Intent PROFILE_OWNER_INTENT = new Intent()
            .putExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS, PROFILE_OWNER_PARAMS);
    private static final Intent DEVICE_OWNER_INTENT = new Intent()
            .putExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS, DEVICE_OWNER_PARAMS);
    private static final Intent NFC_INTENT = new Intent()
            .putExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS, NFC_PARAMS);

    private static class CustomIntentsTestRule extends IntentsTestRule<ProvisioningActivity> {
        private boolean mIsActivityRunning = false;
        private CustomIntentsTestRule() {
            super(ProvisioningActivity.class, true /* Initial touch mode  */,
                    false /* Lazily launch activity */);
        }

        @Override
        protected synchronized void afterActivityLaunched() {
            mIsActivityRunning = true;
            super.afterActivityLaunched();
        }

        @Override
        public synchronized void afterActivityFinished() {
            // Temp fix for b/37663530
            if (mIsActivityRunning) {
                super.afterActivityFinished();
                mIsActivityRunning = false;
            }
        }
    }

    @Rule
    public CustomIntentsTestRule mActivityRule = new CustomIntentsTestRule();

    @Mock private ProvisioningManager mProvisioningManager;
    @Mock private PackageManager mPackageManager;
    @Mock private Utils mUtils;
    private static int mRotationLocked;

    @BeforeClass
    public static void setUpClass() {
        // Stop the activity from rotating in order to keep hold of the context
        Context context = InstrumentationRegistry.getTargetContext();

        mRotationLocked = Settings.System.getInt(context.getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION, 0);
        Settings.System.putInt(context.getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION, 0);
    }

    @AfterClass
    public static void tearDownClass() {
        // Reset the rotation value back to what it was before the test
        Context context = InstrumentationRegistry.getTargetContext();

        Settings.System.putInt(context.getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION, mRotationLocked);
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        TestInstrumentationRunner.registerReplacedActivity(ProvisioningActivity.class,
                (classLoader, className, intent) ->
                        new ProvisioningActivity(mProvisioningManager, mUtils) {
                            @Override
                            public PackageManager getPackageManager() {
                                return mPackageManager;
                            }
                        });
        // LogoUtils cached icon globally. Clean-up the cache
        LogoUtils.cleanUp(InstrumentationRegistry.getTargetContext());
    }

    @After
    public void tearDown() {
        TestInstrumentationRunner.unregisterReplacedActivity(ProvisioningActivity.class);
    }

    @Test
    public void testLaunch() {
        // GIVEN the activity was launched with a profile owner intent
        launchActivityAndWait(PROFILE_OWNER_INTENT);

        // THEN the provisioning process should be initiated
        verify(mProvisioningManager).maybeStartProvisioning(PROFILE_OWNER_PARAMS);

        // THEN the activity should start listening for provisioning updates
        verify(mProvisioningManager).registerListener(any(ProvisioningManagerCallback.class));
        verifyNoMoreInteractions(mProvisioningManager);
    }

    @Test
    public void testColors() throws Throwable {
        Context context = InstrumentationRegistry.getTargetContext();

        // default color Managed Profile (MP)
        assertColorsCorrect(PROFILE_OWNER_INTENT, context.getColor(DEFAULT_COLOR_ID_MP));

        // default color Device Owner (DO)
        assertColorsCorrect(DEVICE_OWNER_INTENT, context.getColor(DEFAULT_COLOR_ID_DO));

        // custom color for both cases (MP, DO)
        int targetColor = Color.parseColor("#d40000"); // any color (except default) would do
        for (String action : asList(ACTION_PROVISION_MANAGED_PROFILE,
                ACTION_PROVISION_MANAGED_DEVICE)) {
            ProvisioningParams provisioningParams = new ProvisioningParams.Builder()
                    .setProvisioningAction(action)
                    .setDeviceAdminComponentName(ADMIN)
                    .setMainColor(targetColor)
                    .build();
            assertColorsCorrect(new Intent().putExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS,
                    provisioningParams), targetColor);
        }
    }

    private void assertColorsCorrect(Intent intent, int color) throws Throwable {
        launchActivityAndWait(intent);
        Activity activity = mActivityRule.getActivity();

        CustomizationVerifier customizationVerifier = new CustomizationVerifier(activity);
        customizationVerifier.assertStatusBarColorCorrect(color);
        customizationVerifier.assertDefaultLogoCorrect(color);
        customizationVerifier.assertProgressBarColorCorrect(color);

        finishAndWait();
    }

    private void finishAndWait() throws Throwable {
        Activity activity = mActivityRule.getActivity();
        ActivityLifecycleWaiter waiter = new ActivityLifecycleWaiter(activity, Stage.DESTROYED);
        mActivityRule.runOnUiThread(() -> activity.finish());
        waiter.waitForStage();
        mActivityRule.afterActivityFinished();
    }

    @Test
    public void testCustomLogo_profileOwner() throws Throwable {
        assertCustomLogoCorrect(PROFILE_OWNER_INTENT);
    }

    @Test
    public void testCustomLogo_deviceOwner() throws Throwable {
        assertCustomLogoCorrect(PROFILE_OWNER_INTENT);
    }

    private void assertCustomLogoCorrect(Intent intent) throws Throwable {
        UriBitmap targetLogo = UriBitmap.createSimpleInstance();
        saveOrganisationLogo(InstrumentationRegistry.getTargetContext(), targetLogo.getUri());
        launchActivityAndWait(intent);
        ProvisioningActivity activity = mActivityRule.getActivity();
        new CustomizationVerifier(activity).assertCustomLogoCorrect(targetLogo.getBitmap());
        finishAndWait();
    }

    @Test
    public void testSavedInstanceState() throws Throwable {
        // GIVEN the activity was launched with a profile owner intent
        launchActivityAndWait(PROFILE_OWNER_INTENT);

        // THEN the provisioning process should be initiated
        verify(mProvisioningManager).maybeStartProvisioning(PROFILE_OWNER_PARAMS);

        // WHEN the activity is recreated with a saved instance state
        mActivityRule.runOnUiThread(() -> {
                    Bundle bundle = new Bundle();
                    InstrumentationRegistry.getInstrumentation()
                            .callActivityOnSaveInstanceState(mActivityRule.getActivity(), bundle);
                    InstrumentationRegistry.getInstrumentation()
                            .callActivityOnCreate(mActivityRule.getActivity(), bundle);
                });

        // THEN provisioning should not be initiated again
        verify(mProvisioningManager).maybeStartProvisioning(PROFILE_OWNER_PARAMS);
    }

    @Test
    public void testPause() throws Throwable {
        // GIVEN the activity was launched with a profile owner intent
        launchActivityAndWait(PROFILE_OWNER_INTENT);

        // WHEN the activity is paused
        mActivityRule.runOnUiThread(() -> {
            InstrumentationRegistry.getInstrumentation()
                    .callActivityOnPause(mActivityRule.getActivity());
        });

        // THEN the listener is unregistered
        verify(mProvisioningManager).unregisterListener(any(ProvisioningManagerCallback.class));
    }

    @Test
    public void testErrorNoFactoryReset() throws Throwable {
        // GIVEN the activity was launched with a profile owner intent
        launchActivityAndWait(PROFILE_OWNER_INTENT);

        // WHEN an error occurred that does not require factory reset
        final int errorMsgId = R.string.managed_provisioning_error_text;
        mActivityRule.runOnUiThread(() -> mActivityRule.getActivity().error(R.string.cant_set_up_device, errorMsgId, false));

        // THEN the UI should show an error dialog
        onView(withText(errorMsgId)).check(matches(isDisplayed()));

        // WHEN clicking ok
        onView(withId(android.R.id.button1))
                .check(matches(withText(R.string.device_owner_error_ok)))
                .perform(click());

        // THEN the activity should be finishing
        assertTrue(mActivityRule.getActivity().isFinishing());
    }

    @Test
    public void testErrorFactoryReset() throws Throwable {
        // GIVEN the activity was launched with a device owner intent
        launchActivityAndWait(DEVICE_OWNER_INTENT);

        // WHEN an error occurred that does not require factory reset
        final int errorMsgId = R.string.managed_provisioning_error_text;
        mActivityRule.runOnUiThread(() -> mActivityRule.getActivity().error(R.string.cant_set_up_device, errorMsgId, true));

        // THEN the UI should show an error dialog
        onView(withText(errorMsgId)).check(matches(isDisplayed()));

        // WHEN clicking the ok button that says that factory reset is required
        onView(withId(android.R.id.button1))
                .check(matches(withText(R.string.reset)))
                .perform(click());

        // THEN factory reset should be invoked
        verify(mUtils).sendFactoryResetBroadcast(any(Context.class), anyString());
    }

    @Test
    public void testCancelProfileOwner() throws Throwable {
        // GIVEN the activity was launched with a profile owner intent
        launchActivityAndWait(PROFILE_OWNER_INTENT);

        // WHEN the user tries to cancel
        pressBack();

        // THEN the cancel dialog should be shown
        onView(withText(R.string.profile_owner_cancel_message)).check(matches(isDisplayed()));

        // WHEN deciding not to cancel
        onView(withId(android.R.id.button2))
                .check(matches(withText(R.string.profile_owner_cancel_cancel)))
                .perform(click());

        // THEN the activity should not be finished
        assertFalse(mActivityRule.getActivity().isFinishing());

        // WHEN the user tries to cancel
        pressBack();

        // THEN the cancel dialog should be shown
        onView(withText(R.string.profile_owner_cancel_message)).check(matches(isDisplayed()));

        // WHEN deciding to cancel
        onView(withId(android.R.id.button1))
                .check(matches(withText(R.string.profile_owner_cancel_ok)))
                .perform(click());

        // THEN the manager should be informed
        verify(mProvisioningManager).cancelProvisioning();

        // THEN the activity should be finished
        assertTrue(mActivityRule.getActivity().isFinishing());
    }

    @Test
    public void testCancelProfileOwner_CompProvisioningWithSkipConsent() throws Throwable {
        // GIVEN launching profile intent with skipping user consent
        ProvisioningParams params = new ProvisioningParams.Builder()
                .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
                .setDeviceAdminComponentName(ADMIN)
                .setSkipUserConsent(true)
                .build();
        Intent intent = new Intent()
                .putExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS, params);
        launchActivityAndWait(new Intent(intent));

        // WHEN the user tries to cancel
        mActivityRule.runOnUiThread(() -> mActivityRule.getActivity().onBackPressed());

        // THEN never unregistering ProvisioningManager
        verify(mProvisioningManager, never()).unregisterListener(
                any(ProvisioningManagerCallback.class));
    }

    @Test
    public void testCancelProfileOwner_CompProvisioningWithoutSkipConsent() throws Throwable {
        // GIVEN launching profile intent without skipping user consent
        ProvisioningParams params = new ProvisioningParams.Builder()
                .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
                .setDeviceAdminComponentName(ADMIN)
                .setSkipUserConsent(false)
                .build();
        Intent intent = new Intent()
                .putExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS, params);
        launchActivityAndWait(new Intent(intent));

        // WHEN the user tries to cancel
        mActivityRule.runOnUiThread(() -> mActivityRule.getActivity().onBackPressed());

        // THEN unregistering ProvisioningManager
        verify(mProvisioningManager).unregisterListener(any(ProvisioningManagerCallback.class));

        // THEN the cancel dialog should be shown
        onView(withText(R.string.profile_owner_cancel_message)).check(matches(isDisplayed()));
    }

    @Test
    public void testCancelDeviceOwner() throws Throwable {
        // GIVEN the activity was launched with a device owner intent
        launchActivityAndWait(DEVICE_OWNER_INTENT);

        // WHEN the user tries to cancel
        pressBack();

        // THEN the cancel dialog should be shown
        onView(withText(R.string.stop_setup_reset_device_question)).check(matches(isDisplayed()));
        onView(withText(R.string.this_will_reset_take_back_first_screen))
                .check(matches(isDisplayed()));

        // WHEN deciding not to cancel
        onView(withId(android.R.id.button2))
                .check(matches(withText(R.string.device_owner_cancel_cancel)))
                .perform(click());

        // THEN the activity should not be finished
        assertFalse(mActivityRule.getActivity().isFinishing());

        // WHEN the user tries to cancel
        pressBack();

        // THEN the cancel dialog should be shown
        onView(withText(R.string.stop_setup_reset_device_question)).check(matches(isDisplayed()));

        // WHEN deciding to cancel
        onView(withId(android.R.id.button1))
                .check(matches(withText(R.string.reset)))
                .perform(click());

        // THEN factory reset should be invoked
        verify(mUtils).sendFactoryResetBroadcast(any(Context.class), anyString());
    }

    @Test
    public void testSuccess() throws Throwable {
        // GIVEN the activity was launched with a profile owner intent
        launchActivityAndWait(PROFILE_OWNER_INTENT);

        // WHEN preFinalization is completed
        mActivityRule.runOnUiThread(() -> mActivityRule.getActivity().preFinalizationCompleted());

        // THEN the activity should finish
        assertTrue(mActivityRule.getActivity().isFinishing());
    }

    @Test
    public void testSuccess_Nfc() throws Throwable {
        // GIVEN queryIntentActivities return test_activity
        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.packageName = TEST_ACTIVITY.getPackageName();
        activityInfo.name = TEST_ACTIVITY.getClassName();
        activityInfo.permission = permission.BIND_DEVICE_ADMIN;
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.activityInfo = activityInfo;
        List<ResolveInfo> resolveInfoList = new ArrayList();
        resolveInfoList.add(resolveInfo);
        when(mPackageManager.queryIntentActivities(
                MockitoHamcrest.argThat(hasAction(ACTION_STATE_USER_SETUP_COMPLETE)),
                eq(0))).thenReturn(resolveInfoList);
        when(mPackageManager.checkPermission(eq(permission.DISPATCH_PROVISIONING_MESSAGE),
                eq(activityInfo.packageName))).thenReturn(PackageManager.PERMISSION_GRANTED);

        // GIVEN the activity was launched with a nfc intent
        launchActivityAndWait(NFC_INTENT);

        // WHEN preFinalization is completed
        mActivityRule.runOnUiThread(() -> mActivityRule.getActivity().preFinalizationCompleted());
        // THEN verify starting TEST_ACTIVITY
        intended(allOf(hasComponent(TEST_ACTIVITY), hasAction(ACTION_STATE_USER_SETUP_COMPLETE)));
        // THEN the activity should finish
        assertTrue(mActivityRule.getActivity().isFinishing());
    }

    private void launchActivityAndWait(Intent intent) {
        mActivityRule.launchActivity(intent);
        onView(withId(R.id.setup_wizard_layout));
    }
}
