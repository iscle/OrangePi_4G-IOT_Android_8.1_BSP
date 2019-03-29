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

package android.autofillservice.cts;

import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;
import static android.autofillservice.cts.CannedFillResponse.DO_NOT_REPLY_RESPONSE;
import static android.autofillservice.cts.CannedFillResponse.NO_RESPONSE;
import static android.autofillservice.cts.CheckoutActivity.ID_CC_NUMBER;
import static android.autofillservice.cts.Helper.ID_PASSWORD;
import static android.autofillservice.cts.Helper.ID_PASSWORD_LABEL;
import static android.autofillservice.cts.Helper.ID_USERNAME;
import static android.autofillservice.cts.Helper.assertNoDanglingSessions;
import static android.autofillservice.cts.Helper.assertNumberOfChildren;
import static android.autofillservice.cts.Helper.assertTextAndValue;
import static android.autofillservice.cts.Helper.assertTextIsSanitized;
import static android.autofillservice.cts.Helper.assertValue;
import static android.autofillservice.cts.Helper.dumpStructure;
import static android.autofillservice.cts.Helper.findNodeByResourceId;
import static android.autofillservice.cts.Helper.runShellCommand;
import static android.autofillservice.cts.Helper.setUserComplete;
import static android.autofillservice.cts.InstrumentedAutoFillService.waitUntilConnected;
import static android.autofillservice.cts.InstrumentedAutoFillService.waitUntilDisconnected;
import static android.autofillservice.cts.LoginActivity.AUTHENTICATION_MESSAGE;
import static android.autofillservice.cts.LoginActivity.BACKDOOR_USERNAME;
import static android.autofillservice.cts.LoginActivity.ID_USERNAME_CONTAINER;
import static android.autofillservice.cts.LoginActivity.getWelcomeMessage;
import static android.service.autofill.FillEventHistory.Event.TYPE_AUTHENTICATION_SELECTED;
import static android.service.autofill.FillEventHistory.Event.TYPE_DATASET_AUTHENTICATION_SELECTED;
import static android.service.autofill.FillEventHistory.Event.TYPE_DATASET_SELECTED;
import static android.service.autofill.FillEventHistory.Event.TYPE_SAVE_SHOWN;
import static android.service.autofill.FillRequest.FLAG_MANUAL_REQUEST;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_ADDRESS;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_CREDIT_CARD;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_EMAIL_ADDRESS;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_GENERIC;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_PASSWORD;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_USERNAME;
import static android.text.InputType.TYPE_NULL;
import static android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD;
import static android.view.View.IMPORTANT_FOR_AUTOFILL_NO;
import static android.view.WindowManager.LayoutParams.FLAG_SECURE;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.PendingIntent;
import android.app.assist.AssistStructure.ViewNode;
import android.autofillservice.cts.CannedFillResponse.CannedDataset;
import android.autofillservice.cts.InstrumentedAutoFillService.FillRequest;
import android.autofillservice.cts.InstrumentedAutoFillService.SaveRequest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.graphics.Color;
import android.os.Bundle;
import android.service.autofill.FillEventHistory;
import android.service.autofill.SaveInfo;
import android.support.test.uiautomator.UiObject2;
import android.util.Log;
import android.view.View;
import android.view.View.AccessibilityDelegate;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.view.autofill.AutofillManager;
import android.view.autofill.AutofillValue;
import android.widget.RemoteViews;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This is the test case covering most scenarios - other test cases will cover characteristics
 * specific to that test's activity (for example, custom views).
 */
public class LoginActivityTest extends AutoFillServiceTestCase {

    private static final String TAG = "LoginActivityTest";

    @Rule
    public final AutofillActivityTestRule<LoginActivity> mActivityRule =
        new AutofillActivityTestRule<LoginActivity>(LoginActivity.class);

    private LoginActivity mActivity;

    @Before
    public void setActivity() {
        mActivity = mActivityRule.getActivity();
    }

    @After
    public void finishWelcomeActivity() {
        WelcomeActivity.finishIt();
    }

    @Test
    public void testAutoFillNoDatasets() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(NO_RESPONSE);

        // Trigger autofill.
        mActivity.onUsername(View::requestFocus);

        // Test connection lifecycle.
        waitUntilConnected();
        sReplier.getNextFillRequest();

        // Make sure UI is not shown.
        sUiBot.assertNoDatasets();

        // Try to trigger it again...

        mActivity.onPassword(View::requestFocus);
        // ...and make sure it didn't
        sUiBot.assertNoDatasets();
        sReplier.assertNumberUnhandledFillRequests(0);

        // Test connection lifecycle.
        waitUntilDisconnected();
    }

    @Test
    public void testAutofillManuallyAfterServiceReturnedNoDatasets() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(NO_RESPONSE);

        // Trigger autofill.
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();

        // Make sure UI is not shown.
        sUiBot.assertNoDatasets();

        // Try again, forcing it
        sReplier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, "dude")
                .setField(ID_PASSWORD, "sweet")
                .setPresentation(createPresentation("The Dude"))
                .build());
        mActivity.expectAutoFill("dude", "sweet");

        mActivity.forceAutofillOnUsername();

        final FillRequest fillRequest = sReplier.getNextFillRequest();
        assertThat(fillRequest.flags).isEqualTo(FLAG_MANUAL_REQUEST);

        // Selects the dataset.
        sUiBot.selectDataset("The Dude");

        // Check the results.
        mActivity.assertAutoFilled();
    }

    @Test
    public void testAutofillManuallyAndSaveAfterServiceReturnedNoDatasets() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(NO_RESPONSE);

        // Trigger autofill.
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();

        // Make sure UI is not shown.
        sUiBot.assertNoDatasets();
        sReplier.assertNumberUnhandledFillRequests(0);
        mActivity.onPassword(View::requestFocus);
        sUiBot.assertNoDatasets();
        sReplier.assertNumberUnhandledFillRequests(0);

        // Try again, forcing it
        saveOnlyTest(true);
    }

    @Test
    public void testAutoFillOneDataset() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, "dude")
                .setField(ID_PASSWORD, "sweet")
                .setPresentation(createPresentation("The Dude"))
                .build());
        mActivity.expectAutoFill("dude", "sweet");

        // Dynamically set password to make sure it's sanitized.
        mActivity.onPassword((v) -> v.setText("I AM GROOT"));

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);

        // Auto-fill it.
        sUiBot.selectDataset("The Dude");

        // Check the results.
        mActivity.assertAutoFilled();

        // Sanity checks.

        // Make sure input was sanitized.
        final FillRequest request = sReplier.getNextFillRequest();
        assertWithMessage("CancelationSignal is null").that(request.cancellationSignal).isNotNull();
        assertTextIsSanitized(request.structure, ID_PASSWORD);

        // Make sure initial focus was properly set.
        assertWithMessage("Username node is not focused").that(
                findNodeByResourceId(request.structure, ID_USERNAME).isFocused()).isTrue();
        assertWithMessage("Password node is focused").that(
                findNodeByResourceId(request.structure, ID_PASSWORD).isFocused()).isFalse();
    }

    @Test
    public void testAutoFillTwoDatasetsSameNumberOfFields() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("The Dude"))
                        .build())
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "DUDE")
                        .setField(ID_PASSWORD, "SWEET")
                        .setPresentation(createPresentation("THE DUDE"))
                        .build())
                .build());
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();

        // Make sure all datasets are available...
        sUiBot.assertDatasets("The Dude", "THE DUDE");

        // ... on all fields.
        mActivity.onPassword(View::requestFocus);
        sUiBot.assertDatasets("The Dude", "THE DUDE");

        // Auto-fill it.
        sUiBot.selectDataset("The Dude");

        // Check the results.
        mActivity.assertAutoFilled();
    }

    @Test
    public void testAutoFillTwoDatasetsUnevenNumberOfFieldsFillsAll() throws Exception {
        autoFillTwoDatasetsUnevenNumberOfFieldsTest(true);
    }

    @Test
    public void testAutoFillTwoDatasetsUnevenNumberOfFieldsFillsOne() throws Exception {
        autoFillTwoDatasetsUnevenNumberOfFieldsTest(false);
    }

    private void autoFillTwoDatasetsUnevenNumberOfFieldsTest(boolean fillsAll) throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("The Dude"))
                        .build())
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "DUDE")
                        .setPresentation(createPresentation("THE DUDE"))
                        .build())
                .build());
        if (fillsAll) {
            mActivity.expectAutoFill("dude", "sweet");
        } else {
            mActivity.expectAutoFill("DUDE");
        }

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();

        // Make sure all datasets are available on username...
        sUiBot.assertDatasets("The Dude", "THE DUDE");

        // ... but just one for password
        mActivity.onPassword(View::requestFocus);
        sUiBot.assertDatasets("The Dude");

        // Auto-fill it.
        mActivity.onUsername(View::requestFocus);
        sUiBot.assertDatasets("The Dude", "THE DUDE");
        if (fillsAll) {
            sUiBot.selectDataset("The Dude");
        } else {
            sUiBot.selectDataset("THE DUDE");
        }

        // Check the results.
        mActivity.assertAutoFilled();
    }

    @Test
    public void testAutoFillWhenViewHasChildAccessibilityNodes() throws Exception {
        mActivity.onUsername((v) -> v.setAccessibilityDelegate(new AccessibilityDelegate() {
            @Override
            public AccessibilityNodeProvider getAccessibilityNodeProvider(View host) {
                return new AccessibilityNodeProvider() {
                    @Override
                    public AccessibilityNodeInfo createAccessibilityNodeInfo(int virtualViewId) {
                        final AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
                        if (virtualViewId == View.NO_ID) {
                            info.addChild(v, 108);
                        }
                        return info;
                    }
                };
            }
        }));

        testAutoFillOneDataset();
    }

    @Test
    public void testAutoFillOneDatasetAndMoveFocusAround() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, "dude")
                .setField(ID_PASSWORD, "sweet")
                .setPresentation(createPresentation("The Dude"))
                .build());
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();

        // Make sure tapping on other fields from the dataset does not trigger it again
        mActivity.onPassword(View::requestFocus);
        sReplier.assertNumberUnhandledFillRequests(0);

        mActivity.onUsername(View::requestFocus);
        sReplier.assertNumberUnhandledFillRequests(0);

        // Auto-fill it.
        sUiBot.selectDataset("The Dude");

        // Check the results.
        mActivity.assertAutoFilled();

        // Make sure tapping on other fields from the dataset does not trigger it again
        mActivity.onPassword(View::requestFocus);
        mActivity.onUsername(View::requestFocus);
    }

    @Test
    public void testUiNotShownAfterAutofilled() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, "dude")
                .setField(ID_PASSWORD, "sweet")
                .setPresentation(createPresentation("The Dude"))
                .build());
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();
        sUiBot.selectDataset("The Dude");

        // Check the results.
        mActivity.assertAutoFilled();

        // Make sure tapping on autofilled field does not trigger it again
        mActivity.onPassword(View::requestFocus);
        sUiBot.assertNoDatasets();

        mActivity.onUsername(View::requestFocus);
        sUiBot.assertNoDatasets();
    }

    @Test
    public void testAutofillCallbacks() throws Exception {
        // Set service.
        enableService();
        final MyAutofillCallback callback = mActivity.registerCallback();

        // Set expectations.
        sReplier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, "dude")
                .setField(ID_PASSWORD, "sweet")
                .setPresentation(createPresentation("The Dude"))
                .build());
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();
        final View username = mActivity.getUsername();
        final View password = mActivity.getPassword();

        callback.assertUiShownEvent(username);

        mActivity.onPassword(View::requestFocus);
        callback.assertUiHiddenEvent(username);
        callback.assertUiShownEvent(password);

        mActivity.onUsername(View::requestFocus);
        mActivity.unregisterCallback();
        callback.assertNumberUnhandledEvents(0);

        // Auto-fill it.
        sUiBot.selectDataset("The Dude");

        // Check the results.
        mActivity.assertAutoFilled();
    }

    @Test
    public void testAutofillCallbackDisabled() throws Exception {
        // Set service.
        disableService();

        final MyAutofillCallback callback = mActivity.registerCallback();

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);

        // Assert callback was called
        final View username = mActivity.getUsername();
        callback.assertUiUnavailableEvent(username);
    }

    @Test
    public void testAutofillCallbackNoDatasets() throws Exception {
        callbackUnavailableTest(NO_RESPONSE);
    }

    @Test
    public void testAutofillCallbackNoDatasetsButSaveInfo() throws Exception {
        callbackUnavailableTest(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD)
                .build());
    }

    private void callbackUnavailableTest(CannedFillResponse response) throws Exception {
        // Set service.
        enableService();
        final MyAutofillCallback callback = mActivity.registerCallback();

        // Set expectations.
        sReplier.addResponse(response);

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();

        // Auto-fill it.
        sUiBot.assertNoDatasets();

        // Assert callback was called
        final View username = mActivity.getUsername();
        callback.assertUiUnavailableEvent(username);
    }

    @Test
    public void testAutoFillOneDatasetAndSave() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        final Bundle extras = new Bundle();
        extras.putString("numbers", "4815162342");

        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("The Dude"))
                        .build())
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD)
                .setExtras(extras)
                .build());
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);

        // Since this is a Presubmit test, wait for connection to avoid flakiness.
        waitUntilConnected();

        sReplier.getNextFillRequest();

        // Auto-fill it.
        sUiBot.selectDataset("The Dude");

        // Check the results.
        mActivity.assertAutoFilled();

        // Try to login, it will fail.
        final String loginMessage = mActivity.tapLogin();

        assertWithMessage("Wrong login msg").that(loginMessage).isEqualTo(AUTHENTICATION_MESSAGE);

        // Set right password...
        mActivity.onPassword((v) -> v.setText("dude"));

        // ... and try again
        final String expectedMessage = getWelcomeMessage("dude");
        final String actualMessage = mActivity.tapLogin();
        assertWithMessage("Wrong welcome msg").that(actualMessage).isEqualTo(expectedMessage);

        // Assert the snack bar is shown and tap "Save".
        sUiBot.saveForAutofill(true, SAVE_DATA_TYPE_PASSWORD);

        final SaveRequest saveRequest = sReplier.getNextSaveRequest();

        // Assert value of expected fields - should not be sanitized.
        final ViewNode username = findNodeByResourceId(saveRequest.structure, ID_USERNAME);
        assertTextAndValue(username, "dude");
        final ViewNode password = findNodeByResourceId(saveRequest.structure, ID_USERNAME);
        assertTextAndValue(password, "dude");

        // Make sure extras were passed back on onSave()
        assertThat(saveRequest.data).isNotNull();
        final String extraValue = saveRequest.data.getString("numbers");
        assertWithMessage("extras not passed on save").that(extraValue).isEqualTo("4815162342");

        // Sanity check: once saved, the session should be finished.
        assertNoDanglingSessions();
    }

    @Test
    public void testAutoFillOneDatasetAndSaveHidingOverlays() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        final Bundle extras = new Bundle();
        extras.putString("numbers", "4815162342");

        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("The Dude"))
                        .build())
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD)
                .setExtras(extras)
                .build());
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);

        // Since this is a Presubmit test, wait for connection to avoid flakiness.
        waitUntilConnected();

        sReplier.getNextFillRequest();

        // Add an overlay on top of the whole screen
        final View[] overlay = new View[1];
        try {
            // Allow ourselves to add overlays
            runShellCommand("appops set %s SYSTEM_ALERT_WINDOW allow", mPackageName);

            // Make sure the fill UI is shown.
            sUiBot.assertDatasets("The Dude");

            final CountDownLatch latch = new CountDownLatch(1);

            mActivity.runOnUiThread(() -> {
                // This overlay is focusable, full-screen, which should block interaction
                // with the fill UI unless the platform successfully hides overlays.
                final WindowManager.LayoutParams params = new WindowManager.LayoutParams();
                params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
                params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
                params.width = ViewGroup.LayoutParams.MATCH_PARENT;
                params.height = ViewGroup.LayoutParams.MATCH_PARENT;

                final View view = new View(mContext) {
                    @Override
                    protected void onAttachedToWindow() {
                        super.onAttachedToWindow();
                        latch.countDown();
                    }
                };
                view.setBackgroundColor(Color.RED);
                WindowManager windowManager = mContext.getSystemService(WindowManager.class);
                windowManager.addView(view, params);
                overlay[0] = view;
            });

            // Wait for the window being added.
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

            // Auto-fill it.
            sUiBot.selectDataset("The Dude");

            // Check the results.
            mActivity.assertAutoFilled();

            // Try to login, it will fail.
            final String loginMessage = mActivity.tapLogin();

            assertWithMessage("Wrong login msg").that(loginMessage).isEqualTo(
                    AUTHENTICATION_MESSAGE);

            // Set right password...
            mActivity.onPassword((v) -> v.setText("dude"));

            // ... and try again
            final String expectedMessage = getWelcomeMessage("dude");
            final String actualMessage = mActivity.tapLogin();
            assertWithMessage("Wrong welcome msg").that(actualMessage).isEqualTo(expectedMessage);

            // Assert the snack bar is shown and tap "Save".
            sUiBot.saveForAutofill(true, SAVE_DATA_TYPE_PASSWORD);

            final SaveRequest saveRequest = sReplier.getNextSaveRequest();

            // Assert value of expected fields - should not be sanitized.
            final ViewNode username = findNodeByResourceId(saveRequest.structure, ID_USERNAME);
            assertTextAndValue(username, "dude");
            final ViewNode password = findNodeByResourceId(saveRequest.structure, ID_USERNAME);
            assertTextAndValue(password, "dude");

            // Make sure extras were passed back on onSave()
            assertThat(saveRequest.data).isNotNull();
            final String extraValue = saveRequest.data.getString("numbers");
            assertWithMessage("extras not passed on save").that(extraValue).isEqualTo("4815162342");

            // Sanity check: once saved, the session should be finished.
            assertNoDanglingSessions();
        } finally {
            // Make sure we can no longer add overlays
            runShellCommand("appops set %s SYSTEM_ALERT_WINDOW ignore", mPackageName);
            // Make sure the overlay is removed
            mActivity.runOnUiThread(() -> {
                WindowManager windowManager = mContext.getSystemService(WindowManager.class);
                windowManager.removeView(overlay[0]);
            });
        }
    }

    @Test
    public void testAutoFillMultipleDatasetsPickFirst() throws Exception {
        multipleDatasetsTest(1);
    }

    @Test
    public void testAutoFillMultipleDatasetsPickSecond() throws Exception {
        multipleDatasetsTest(2);
    }

    @Test
    public void testAutoFillMultipleDatasetsPickThird() throws Exception {
        multipleDatasetsTest(3);
    }

    private void multipleDatasetsTest(int number) throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "mr_plow")
                        .setField(ID_PASSWORD, "D'OH!")
                        .setPresentation(createPresentation("Mr Plow"))
                        .build())
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "el barto")
                        .setField(ID_PASSWORD, "aycaramba!")
                        .setPresentation(createPresentation("El Barto"))
                        .build())
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "mr sparkle")
                        .setField(ID_PASSWORD, "Aw3someP0wer")
                        .setPresentation(createPresentation("Mr Sparkle"))
                        .build())
                .build());
        final String name;

        switch (number) {
            case 1:
                name = "Mr Plow";
                mActivity.expectAutoFill("mr_plow", "D'OH!");
                break;
            case 2:
                name = "El Barto";
                mActivity.expectAutoFill("el barto", "aycaramba!");
                break;
            case 3:
                name = "Mr Sparkle";
                mActivity.expectAutoFill("mr sparkle", "Aw3someP0wer");
                break;
            default:
                throw new IllegalArgumentException("invalid dataset number: " + number);
        }

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();

        // Make sure all datasets are shown.
        final UiObject2 picker = sUiBot.assertDatasets("Mr Plow", "El Barto", "Mr Sparkle");

        // Auto-fill it.
        sUiBot.selectDataset(picker, name);

        // Check the results.
        mActivity.assertAutoFilled();
    }

    /**
     * Tests the scenario where the service uses custom remote views for different fields (username
     * and password).
     */
    @Test
    public void testAutofillOneDatasetCustomPresentation() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, "dude",
                        createPresentation("The Dude"))
                .setField(ID_PASSWORD, "sweet",
                        createPresentation("Dude's password"))
                .build());
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();

        // Check initial field.
        sUiBot.assertDatasets("The Dude");

        // Then move around...
        mActivity.onPassword(View::requestFocus);
        sUiBot.assertDatasets("Dude's password");
        mActivity.onUsername(View::requestFocus);
        sUiBot.assertDatasets("The Dude");

        // Auto-fill it.
        mActivity.onPassword(View::requestFocus);
        sUiBot.selectDataset("Dude's password");

        // Check the results.
        mActivity.assertAutoFilled();
    }

    /**
     * Tests the scenario where the service uses custom remote views for different fields (username
     * and password) and the dataset itself, and each dataset has the same number of fields.
     */
    @Test
    public void testAutofillMultipleDatasetsCustomPresentations() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder(createPresentation("Dataset1"))
                        .setField(ID_USERNAME, "user1") // no presentation
                        .setField(ID_PASSWORD, "pass1", createPresentation("Pass1"))
                        .build())
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "user2", createPresentation("User2"))
                        .setField(ID_PASSWORD, "pass2") // no presentation
                        .setPresentation(createPresentation("Dataset2"))
                        .build())
                .build());
        mActivity.expectAutoFill("user1", "pass1");

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();

        // Check initial field.
        sUiBot.assertDatasets("Dataset1", "User2");

        // Then move around...
        mActivity.onPassword(View::requestFocus);
        sUiBot.assertDatasets("Pass1", "Dataset2");
        mActivity.onUsername(View::requestFocus);
        sUiBot.assertDatasets("Dataset1", "User2");

        // Auto-fill it.
        mActivity.onPassword(View::requestFocus);
        sUiBot.selectDataset("Pass1");

        // Check the results.
        mActivity.assertAutoFilled();
    }

    /**
     * Tests the scenario where the service uses custom remote views for different fields (username
     * and password), and each dataset has the same number of fields.
     */
    @Test
    public void testAutofillMultipleDatasetsCustomPresentationSameFields() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "user1", createPresentation("User1"))
                        .setField(ID_PASSWORD, "pass1", createPresentation("Pass1"))
                        .build())
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "user2", createPresentation("User2"))
                        .setField(ID_PASSWORD, "pass2", createPresentation("Pass2"))
                        .build())
                .build());
        mActivity.expectAutoFill("user1", "pass1");

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();

        // Check initial field.
        sUiBot.assertDatasets("User1", "User2");

        // Then move around...
        mActivity.onPassword(View::requestFocus);
        sUiBot.assertDatasets("Pass1", "Pass2");
        mActivity.onUsername(View::requestFocus);
        sUiBot.assertDatasets("User1", "User2");

        // Auto-fill it.
        mActivity.onPassword(View::requestFocus);
        sUiBot.selectDataset("Pass1");

        // Check the results.
        mActivity.assertAutoFilled();
    }

    /**
     * Tests the scenario where the service uses custom remote views for different fields (username
     * and password), but each dataset has a different number of fields.
     */
    @Test
    public void testAutofillMultipleDatasetsCustomPresentationFirstDatasetMissingSecondField()
            throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "user1", createPresentation("User1"))
                        .build())
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "user2", createPresentation("User2"))
                        .setField(ID_PASSWORD, "pass2", createPresentation("Pass2"))
                        .build())
                .build());
        mActivity.expectAutoFill("user2", "pass2");

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();

        // Check initial field.
        sUiBot.assertDatasets("User1", "User2");

        // Then move around...
        mActivity.onPassword(View::requestFocus);
        sUiBot.assertDatasets("Pass2");
        mActivity.onUsername(View::requestFocus);
        sUiBot.assertDatasets("User1", "User2");

        // Auto-fill it.
        sUiBot.selectDataset("User2");

        // Check the results.
        mActivity.assertAutoFilled();
    }

    /**
     * Tests the scenario where the service uses custom remote views for different fields (username
     * and password), but each dataset has a different number of fields.
     */
    @Test
    public void testAutofillMultipleDatasetsCustomPresentationSecondDatasetMissingFirstField()
            throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "user1", createPresentation("User1"))
                        .setField(ID_PASSWORD, "pass1", createPresentation("Pass1"))
                        .build())
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_PASSWORD, "pass2", createPresentation("Pass2"))
                        .build())
                .build());
        mActivity.expectAutoFill("user1", "pass1");

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();

        // Check initial field.
        sUiBot.assertDatasets("User1");

        // Then move around...
        mActivity.onPassword(View::requestFocus);
        sUiBot.assertDatasets("Pass1", "Pass2");
        mActivity.onUsername(View::requestFocus);
        sUiBot.assertDatasets("User1");

        // Auto-fill it.
        sUiBot.selectDataset("User1");

        // Check the results.
        mActivity.assertAutoFilled();
    }

    @Test
    public void filterText() throws Exception {
        final String AA = "Two A's";
        final String AB = "A and B";
        final String B = "Only B";

        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "aa")
                        .setPresentation(createPresentation(AA))
                        .build())
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "ab")
                        .setPresentation(createPresentation(AB))
                        .build())
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "b")
                        .setPresentation(createPresentation(B))
                        .build())
                .build());

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();

        // With no filter text all datasets should be shown
        sUiBot.assertDatasets(AA, AB, B);

        // Only two datasets start with 'a'
        runShellCommand("input keyevent KEYCODE_A");
        sUiBot.assertDatasets(AA, AB);

        // Only one dataset start with 'aa'
        runShellCommand("input keyevent KEYCODE_A");
        sUiBot.assertDatasets(AA);

        // Only two datasets start with 'a'
        runShellCommand("input keyevent KEYCODE_DEL");
        sUiBot.assertDatasets(AA, AB);

        // With no filter text all datasets should be shown
        runShellCommand("input keyevent KEYCODE_DEL");
        sUiBot.assertDatasets(AA, AB, B);

        // No dataset start with 'aaa'
        runShellCommand("input keyevent KEYCODE_A");
        runShellCommand("input keyevent KEYCODE_A");
        runShellCommand("input keyevent KEYCODE_A");
        sUiBot.assertNoDatasets();
    }

    @Test
    public void filterTextNullValuesAlwaysMatched() throws Exception {
        final String AA = "Two A's";
        final String AB = "A and B";
        final String B = "Only B";

        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "aa")
                        .setPresentation(createPresentation(AA))
                        .build())
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "ab")
                        .setPresentation(createPresentation(AB))
                        .build())
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, (String) null)
                        .setPresentation(createPresentation(B))
                        .build())
                .build());

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();

        // With no filter text all datasets should be shown
        sUiBot.assertDatasets(AA, AB, B);

        // Two datasets start with 'a' and one with null value always shown
        runShellCommand("input keyevent KEYCODE_A");
        sUiBot.assertDatasets(AA, AB, B);

        // One dataset start with 'aa' and one with null value always shown
        runShellCommand("input keyevent KEYCODE_A");
        sUiBot.assertDatasets(AA, B);

        // Two datasets start with 'a' and one with null value always shown
        runShellCommand("input keyevent KEYCODE_DEL");
        sUiBot.assertDatasets(AA, AB, B);

        // With no filter text all datasets should be shown
        runShellCommand("input keyevent KEYCODE_DEL");
        sUiBot.assertDatasets(AA, AB, B);

        // No dataset start with 'aaa' and one with null value always shown
        runShellCommand("input keyevent KEYCODE_A");
        runShellCommand("input keyevent KEYCODE_A");
        runShellCommand("input keyevent KEYCODE_A");
        sUiBot.assertDatasets(B);
    }

    @Test
    public void filterTextDifferentPrefixes() throws Exception {
        final String A = "aaa";
        final String B = "bra";
        final String C = "cadabra";

        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, A)
                        .setPresentation(createPresentation(A))
                        .build())
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, B)
                        .setPresentation(createPresentation(B))
                        .build())
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, C)
                        .setPresentation(createPresentation(C))
                        .build())
                .build());

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();

        // With no filter text all datasets should be shown
        sUiBot.assertDatasets(A, B, C);

        mActivity.onUsername((v) -> v.setText("a"));
        sUiBot.assertDatasets(A);

        mActivity.onUsername((v) -> v.setText("b"));
        sUiBot.assertDatasets(B);

        mActivity.onUsername((v) -> v.setText("c"));
        sUiBot.assertDatasets(C);
    }

    @Test
    public void testSaveOnly() throws Exception {
        saveOnlyTest(false);
    }

    @Test
    public void testSaveOnlyTriggeredManually() throws Exception {
        saveOnlyTest(false);
    }

    private void saveOnlyTest(boolean manually) throws Exception {
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD)
                .build());

        // Trigger auto-fill.
        if (manually) {
            mActivity.forceAutofillOnUsername();
        } else {
            mActivity.onUsername(View::requestFocus);
        }

        // Sanity check.
        sUiBot.assertNoDatasets();

        // Wait for onFill() before proceeding, otherwise the fields might be changed before
        // the session started
        sReplier.getNextFillRequest();

        // Set credentials...
        mActivity.onUsername((v) -> v.setText("malkovich"));
        mActivity.onPassword((v) -> v.setText("malkovich"));

        // ...and login
        final String expectedMessage = getWelcomeMessage("malkovich");
        final String actualMessage = mActivity.tapLogin();
        assertWithMessage("Wrong welcome msg").that(actualMessage).isEqualTo(expectedMessage);

        // Assert the snack bar is shown and tap "Save".
        sUiBot.saveForAutofill(true, SAVE_DATA_TYPE_PASSWORD);

        final SaveRequest saveRequest = sReplier.getNextSaveRequest();
        sReplier.assertNumberUnhandledSaveRequests(0);

        // Assert value of expected fields - should not be sanitized.
        try {
            final ViewNode username = findNodeByResourceId(saveRequest.structure, ID_USERNAME);
            assertTextAndValue(username, "malkovich");
            final ViewNode password = findNodeByResourceId(saveRequest.structure, ID_PASSWORD);
            assertTextAndValue(password, "malkovich");
        } catch (AssertionError | RuntimeException e) {
            dumpStructure("saveOnlyTest() failed", saveRequest.structure);
            throw e;
        }

        // Sanity check: once saved, the session should be finished.
        assertNoDanglingSessions();
    }

    @Test
    public void testSaveGoesAwayWhenTappingHomeButton() throws Exception {
        saveGoesAway(DismissType.HOME_BUTTON);
    }

    @Test
    public void testSaveGoesAwayWhenTappingBackButton() throws Exception {
        saveGoesAway(DismissType.BACK_BUTTON);
    }

    @Test
    public void testSaveGoesAwayWhenTappingRecentsButton() throws Exception {
        // Launches new activity first...
        startCheckoutActivityAsNewTask();
        try {
            // .. then the real activity being tested.
            sUiBot.switchAppsUsingRecents();
            sUiBot.assertShownByRelativeId(ID_USERNAME_CONTAINER);

            saveGoesAway(DismissType.RECENTS_BUTTON);
        } finally {
            CheckoutActivity.finishIt();
        }
    }

    @Test
    public void testSaveGoesAwayWhenTouchingOutside() throws Exception {
        saveGoesAway(DismissType.TOUCH_OUTSIDE);
    }

    private void startCheckoutActivityAsNewTask() {
        final Intent intent = new Intent(mContext, CheckoutActivity.class);
        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS);
        mContext.startActivity(intent);
        sUiBot.assertShownByRelativeId(CheckoutActivity.ID_ADDRESS);
    }

    private void saveGoesAway(DismissType dismissType) throws Exception {
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD)
                .build());

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);

        // Sanity check.
        sUiBot.assertNoDatasets();

        // Wait for onFill() before proceeding, otherwise the fields might be changed before
        // the session started
        sReplier.getNextFillRequest();

        // Set credentials...
        mActivity.onUsername((v) -> v.setText("malkovich"));
        mActivity.onPassword((v) -> v.setText("malkovich"));

        // ...and login
        final String expectedMessage = getWelcomeMessage("malkovich");
        final String actualMessage = mActivity.tapLogin();
        assertWithMessage("Wrong welcome msg").that(actualMessage).isEqualTo(expectedMessage);

        // Assert the snack bar is shown and tap "Save".
        sUiBot.assertSaveShowing(SAVE_DATA_TYPE_PASSWORD);

        // Then make sure it goes away when user doesn't want it..
        switch (dismissType) {
            case BACK_BUTTON:
                sUiBot.pressBack();
                break;
            case HOME_BUTTON:
                sUiBot.pressHome();
                break;
            case TOUCH_OUTSIDE:
                sUiBot.assertShownByText(expectedMessage).click();
                break;
            case RECENTS_BUTTON:
                sUiBot.switchAppsUsingRecents();
                sUiBot.assertShownByRelativeId(CheckoutActivity.ID_ADDRESS);
                break;
            default:
                throw new IllegalArgumentException("invalid dismiss type: " + dismissType);
        }
        sUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_PASSWORD);
    }

    @Test
    public void testSaveOnlyPreFilled() throws Exception {
        saveOnlyTestPreFilled(false);
    }

    @Test
    public void testSaveOnlyTriggeredManuallyPreFilled() throws Exception {
        saveOnlyTestPreFilled(true);
    }

    private void saveOnlyTestPreFilled(boolean manually) throws Exception {
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD)
                .build());

        // Set activity
        mActivity.onUsername((v) -> v.setText("user_before"));
        mActivity.onPassword((v) -> v.setText("pass_before"));

        // Trigger auto-fill.
        if (manually) {
            mActivity.forceAutofillOnUsername();
        } else {
            mActivity.onUsername(View::requestFocus);
        }

        // Sanity check.
        sUiBot.assertNoDatasets();

        // Wait for onFill() before proceeding, otherwise the fields might be changed before
        // the session started
        sReplier.getNextFillRequest();

        // Set credentials...
        mActivity.onUsername((v) -> v.setText("user_after"));
        mActivity.onPassword((v) -> v.setText("pass_after"));

        // ...and login
        final String expectedMessage = getWelcomeMessage("user_after");
        final String actualMessage = mActivity.tapLogin();
        assertWithMessage("Wrong welcome msg").that(actualMessage).isEqualTo(expectedMessage);

        // Assert the snack bar is shown and tap "Save".
        sUiBot.saveForAutofill(true, SAVE_DATA_TYPE_PASSWORD);

        final SaveRequest saveRequest = sReplier.getNextSaveRequest();
        sReplier.assertNumberUnhandledSaveRequests(0);

        // Assert value of expected fields - should not be sanitized.
        try {
            final ViewNode username = findNodeByResourceId(saveRequest.structure, ID_USERNAME);
            assertTextAndValue(username, "user_after");
            final ViewNode password = findNodeByResourceId(saveRequest.structure, ID_PASSWORD);
            assertTextAndValue(password, "pass_after");
        } catch (AssertionError | RuntimeException e) {
            dumpStructure("saveOnlyTest() failed", saveRequest.structure);
            throw e;
        }

        // Sanity check: once saved, the session should be finsihed.
        assertNoDanglingSessions();
    }

    @Test
    public void testSaveOnlyTwoRequiredFieldsOnePrefilled() throws Exception {
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD)
                .build());

        // Set activity
        mActivity.onUsername((v) -> v.setText("I_AM_USER"));

        // Trigger auto-fill.
        mActivity.onPassword(View::requestFocus);

        // Wait for onFill() before changing value, otherwise the fields might be changed before
        // the session started
        sReplier.getNextFillRequest();
        sUiBot.assertNoDatasets();

        // Set credentials...
        mActivity.onPassword((v) -> v.setText("thou should pass")); // contains pass

        // ...and login
        final String expectedMessage = getWelcomeMessage("I_AM_USER"); // contains pass
        final String actualMessage = mActivity.tapLogin();
        assertWithMessage("Wrong welcome msg").that(actualMessage).isEqualTo(expectedMessage);

        // Assert the snack bar is shown and tap "Save".
        sUiBot.saveForAutofill(true, SAVE_DATA_TYPE_PASSWORD);

        final SaveRequest saveRequest = sReplier.getNextSaveRequest();
        sReplier.assertNumberUnhandledSaveRequests(0);

        // Assert value of expected fields - should not be sanitized.
        try {
            final ViewNode username = findNodeByResourceId(saveRequest.structure, ID_USERNAME);
            assertTextAndValue(username, "I_AM_USER");
            final ViewNode password = findNodeByResourceId(saveRequest.structure, ID_PASSWORD);
            assertTextAndValue(password, "thou should pass");
        } catch (AssertionError | RuntimeException e) {
            dumpStructure("saveOnlyTest() failed", saveRequest.structure);
            throw e;
        }

        // Sanity check: once saved, the session should be finsihed.
        assertNoDanglingSessions();
    }

    @Test
    public void testSaveOnlyOptionalField() throws Exception {
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME)
                .setOptionalSavableIds(ID_PASSWORD)
                .build());

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);

        // Sanity check.
        sUiBot.assertNoDatasets();

        // Wait for onFill() before proceeding, otherwise the fields might be changed before
        // the session started
        sReplier.getNextFillRequest();

        // Set credentials...
        mActivity.onUsername((v) -> v.setText("malkovich"));
        mActivity.onPassword(View::requestFocus);
        mActivity.onPassword((v) -> v.setText("malkovich"));

        // ...and login
        final String expectedMessage = getWelcomeMessage("malkovich");
        final String actualMessage = mActivity.tapLogin();
        assertWithMessage("Wrong welcome msg").that(actualMessage).isEqualTo(expectedMessage);

        // Assert the snack bar is shown and tap "Save".
        sUiBot.saveForAutofill(true, SAVE_DATA_TYPE_PASSWORD);

        final SaveRequest saveRequest = sReplier.getNextSaveRequest();

        // Assert value of expected fields - should not be sanitized.
        final ViewNode username = findNodeByResourceId(saveRequest.structure, ID_USERNAME);
        assertTextAndValue(username, "malkovich");
        final ViewNode password = findNodeByResourceId(saveRequest.structure, ID_PASSWORD);
        assertTextAndValue(password, "malkovich");

        // Sanity check: once saved, the session should be finsihed.
        assertNoDanglingSessions();
    }

    @Test
    public void testSaveNoRequiredField_NoneFilled() throws Exception {
        optionalOnlyTest(FilledFields.NONE);
    }

    @Test
    public void testSaveNoRequiredField_OneFilled() throws Exception {
        optionalOnlyTest(FilledFields.USERNAME_ONLY);
    }

    @Test
    public void testSaveNoRequiredField_BothFilled() throws Exception {
        optionalOnlyTest(FilledFields.BOTH);
    }

    enum FilledFields {
        NONE,
        USERNAME_ONLY,
        BOTH
    }

    private void optionalOnlyTest(FilledFields filledFields) throws Exception {
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD)
                .setOptionalSavableIds(ID_USERNAME, ID_PASSWORD)
                .build());

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);

        // Sanity check.
        sUiBot.assertNoDatasets();

        // Wait for onFill() before proceeding, otherwise the fields might be changed before
        // the session started
        sReplier.getNextFillRequest();

        // Set credentials...
        final String expectedUsername;
        if (filledFields == FilledFields.USERNAME_ONLY || filledFields == FilledFields.BOTH) {
            expectedUsername = BACKDOOR_USERNAME;
            mActivity.onUsername((v) -> v.setText(BACKDOOR_USERNAME));
        } else {
            expectedUsername = "";
        }
        mActivity.onPassword(View::requestFocus);
        if (filledFields == FilledFields.BOTH) {
            mActivity.onPassword((v) -> v.setText("whatever"));
        }

        // ...and login
        final String expectedMessage = getWelcomeMessage(expectedUsername);
        final String actualMessage = mActivity.tapLogin();
        assertWithMessage("Wrong welcome msg").that(actualMessage).isEqualTo(expectedMessage);

        if (filledFields == FilledFields.NONE) {
            sUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_PASSWORD);
            assertNoDanglingSessions();
            return;
        }

        // Assert the snack bar is shown and tap "Save".
        sUiBot.saveForAutofill(true, SAVE_DATA_TYPE_PASSWORD);

        final SaveRequest saveRequest = sReplier.getNextSaveRequest();

        // Assert value of expected fields - should not be sanitized.
        final ViewNode username = findNodeByResourceId(saveRequest.structure, ID_USERNAME);
        assertTextAndValue(username, BACKDOOR_USERNAME);

        if (filledFields == FilledFields.BOTH) {
            final ViewNode password = findNodeByResourceId(saveRequest.structure, ID_PASSWORD);
            assertTextAndValue(password, "whatever");
        }

        // Sanity check: once saved, the session should be finished.
        assertNoDanglingSessions();
    }

    @Test
    public void testGenericSave() throws Exception {
        customizedSaveTest(SAVE_DATA_TYPE_GENERIC);
    }

    @Test
    public void testCustomizedSavePassword() throws Exception {
        customizedSaveTest(SAVE_DATA_TYPE_PASSWORD);
    }

    @Test
    public void testCustomizedSaveAddress() throws Exception {
        customizedSaveTest(SAVE_DATA_TYPE_ADDRESS);
    }

    @Test
    public void testCustomizedSaveCreditCard() throws Exception {
        customizedSaveTest(SAVE_DATA_TYPE_CREDIT_CARD);
    }

    @Test
    public void testCustomizedSaveUsername() throws Exception {
        customizedSaveTest(SAVE_DATA_TYPE_USERNAME);
    }

    @Test
    public void testCustomizedSaveEmailAddress() throws Exception {
        customizedSaveTest(SAVE_DATA_TYPE_EMAIL_ADDRESS);
    }

    private void customizedSaveTest(int type) throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        final String saveDescription = "Your data will be saved with love and care...";
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(type, ID_USERNAME, ID_PASSWORD)
                .setSaveDescription(saveDescription)
                .build());

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);

        // Sanity check.
        sUiBot.assertNoDatasets();

        // Wait for onFill() before proceeding, otherwise the fields might be changed before
        // the session started.
        sReplier.getNextFillRequest();

        // Set credentials...
        mActivity.onUsername((v) -> v.setText("malkovich"));
        mActivity.onPassword((v) -> v.setText("malkovich"));

        // ...and login
        final String expectedMessage = getWelcomeMessage("malkovich");
        final String actualMessage = mActivity.tapLogin();
        assertWithMessage("Wrong welcome msg").that(actualMessage).isEqualTo(expectedMessage);

        // Assert the snack bar is shown and tap "Save".
        final UiObject2 saveSnackBar = sUiBot.assertSaveShowing(saveDescription, type);
        sUiBot.saveForAutofill(saveSnackBar, true);

        // Assert save was called.
        sReplier.getNextSaveRequest();
    }

    @Test
    public void testAutoFillOneDatasetAndSaveWhenFlagSecure() throws Exception {
        mActivity.setFlags(FLAG_SECURE);
        testAutoFillOneDatasetAndSave();
    }

    @Test
    public void testAutoFillOneDatasetWhenFlagSecure() throws Exception {
        mActivity.setFlags(FLAG_SECURE);
        testAutoFillOneDataset();
    }

    @Test
    public void testFillResponseAuthBothFields() throws Exception {
        fillResponseAuthBothFields(false);
    }

    @Test
    public void testFillResponseAuthBothFieldsUserCancelsFirstAttempt() throws Exception {
        fillResponseAuthBothFields(true);
    }

    private void fillResponseAuthBothFields(boolean cancelFirstAttempt) throws Exception {
        // Set service.
        enableService();
        final MyAutofillCallback callback = mActivity.registerCallback();

        // Prepare the authenticated response
        final Bundle clientState = new Bundle();
        clientState.putString("numbers", "4815162342");
        final IntentSender authentication = AuthenticationActivity.createSender(mContext, 1,
                new CannedFillResponse.Builder().addDataset(
                        new CannedDataset.Builder()
                                .setField(ID_USERNAME, "dude")
                                .setField(ID_PASSWORD, "sweet")
                                .setId("name")
                                .setPresentation(createPresentation("Dataset"))
                                .build())
                        .setExtras(clientState).build());

        // Configure the service behavior
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setAuthentication(authentication, ID_USERNAME, ID_PASSWORD)
                .setPresentation(createPresentation("Tap to auth response"))
                .setExtras(clientState)
                .build());

        // Set expectation for the activity
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);

        // Wait for onFill() before proceeding.
        sReplier.getNextFillRequest();
        final View username = mActivity.getUsername();
        callback.assertUiShownEvent(username);
        sUiBot.assertDatasets("Tap to auth response");

        // Make sure UI is show on 2nd field as well
        final View password = mActivity.getPassword();
        mActivity.onPassword(View::requestFocus);
        callback.assertUiHiddenEvent(username);
        callback.assertUiShownEvent(password);
        sUiBot.assertDatasets("Tap to auth response");

        // Now tap on 1st field to show it again...
        mActivity.onUsername(View::requestFocus);
        callback.assertUiHiddenEvent(password);
        callback.assertUiShownEvent(username);

        if (cancelFirstAttempt) {
            // Trigger the auth dialog, but emulate cancel.
            AuthenticationActivity.setResultCode(RESULT_CANCELED);
            sUiBot.selectDataset("Tap to auth response");
            callback.assertUiHiddenEvent(username);
            callback.assertUiShownEvent(username);
            sUiBot.assertDatasets("Tap to auth response");

            // Make sure it's still shown on other fields...
            mActivity.onPassword(View::requestFocus);
            callback.assertUiHiddenEvent(username);
            callback.assertUiShownEvent(password);
            sUiBot.assertDatasets("Tap to auth response");

            // Tap on 1st field to show it again...
            mActivity.onUsername(View::requestFocus);
            callback.assertUiHiddenEvent(password);
            callback.assertUiShownEvent(username);
        }

        // ...and select it this time
        AuthenticationActivity.setResultCode(RESULT_OK);
        sUiBot.selectDataset("Tap to auth response");
        callback.assertUiHiddenEvent(username);
        callback.assertUiShownEvent(username);
        final UiObject2 picker = sUiBot.assertDatasets("Dataset");
        sUiBot.selectDataset(picker, "Dataset");
        callback.assertUiHiddenEvent(username);
        sUiBot.assertNoDatasets();

        // Check the results.
        mActivity.assertAutoFilled();

        final Bundle data = AuthenticationActivity.getData();
        assertThat(data).isNotNull();
        final String extraValue = data.getString("numbers");
        assertThat(extraValue).isEqualTo("4815162342");
    }

    @Test
    public void testFillResponseAuthJustOneField() throws Exception {
        // Set service.
        enableService();
        final MyAutofillCallback callback = mActivity.registerCallback();

        // Prepare the authenticated response
        final Bundle clientState = new Bundle();
        clientState.putString("numbers", "4815162342");
        final IntentSender authentication = AuthenticationActivity.createSender(mContext, 1,
                new CannedFillResponse.Builder().addDataset(
                        new CannedDataset.Builder()
                                .setField(ID_USERNAME, "dude")
                                .setField(ID_PASSWORD, "sweet")
                                .setPresentation(createPresentation("Dataset"))
                                .build())
                        .build());

        // Configure the service behavior
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setAuthentication(authentication, ID_USERNAME)
                .setIgnoreFields(ID_PASSWORD)
                .setPresentation(createPresentation("Tap to auth response"))
                .setExtras(clientState)
                .build());

        // Set expectation for the activity
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);

        // Wait for onFill() before proceeding.
        sReplier.getNextFillRequest();
        final View username = mActivity.getUsername();
        callback.assertUiShownEvent(username);
        sUiBot.assertDatasets("Tap to auth response");

        // Make sure UI is not show on 2nd field
        mActivity.onPassword(View::requestFocus);
        callback.assertUiHiddenEvent(username);
        sUiBot.assertNoDatasets();
        // Now tap on 1st field to show it again...
        mActivity.onUsername(View::requestFocus);
        callback.assertUiShownEvent(username);

        // ...and select it this time
        sUiBot.selectDataset("Tap to auth response");
        callback.assertUiHiddenEvent(username);
        final UiObject2 picker = sUiBot.assertDatasets("Dataset");

        callback.assertUiShownEvent(username);
        sUiBot.selectDataset(picker, "Dataset");
        callback.assertUiHiddenEvent(username);
        sUiBot.assertNoDatasets();

        // Check the results.
        mActivity.assertAutoFilled();
        final Bundle data = AuthenticationActivity.getData();
        assertThat(data).isNotNull();
        final String extraValue = data.getString("numbers");
        assertThat(extraValue).isEqualTo("4815162342");
    }

    @Test
    public void testFillResponseAuthWhenAppCallsCancel() throws Exception {
        // Set service.
        enableService();
        final MyAutofillCallback callback = mActivity.registerCallback();

        // Prepare the authenticated response
        final IntentSender authentication = AuthenticationActivity.createSender(mContext, 1,
                new CannedFillResponse.Builder().addDataset(
                        new CannedDataset.Builder()
                                .setField(ID_USERNAME, "dude")
                                .setField(ID_PASSWORD, "sweet")
                                .setId("name")
                                .setPresentation(createPresentation("Dataset"))
                                .build())
                        .build());

        // Configure the service behavior
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setAuthentication(authentication, ID_USERNAME, ID_PASSWORD)
                .setPresentation(createPresentation("Tap to auth response"))
                .build());

        // Trigger autofill.
        mActivity.onUsername(View::requestFocus);

        // Wait for onFill() before proceeding.
        sReplier.getNextFillRequest();
        final View username = mActivity.getUsername();
        callback.assertUiShownEvent(username);
        sUiBot.assertDatasets("Tap to auth response");

        // Disables autofill so it's not triggered again after the auth activity is finished
        // (and current session is canceled) and the login activity is resumed.
        username.setImportantForAutofill(IMPORTANT_FOR_AUTOFILL_NO);

        // Autofill it.
        final CountDownLatch latch = new CountDownLatch(1);
        AuthenticationActivity.setResultCode(latch, RESULT_OK);

        sUiBot.selectDataset("Tap to auth response");
        callback.assertUiHiddenEvent(username);

        // Cancel session...
        mActivity.getAutofillManager().cancel();

        // ...before finishing the Auth UI.
        latch.countDown();

        sUiBot.assertNoDatasets();
    }

    @Test
    public void testFillResponseAuthServiceHasNoDataButCanSave() throws Exception {
        fillResponseAuthServiceHasNoDataTest(true);
    }

    @Test
    public void testFillResponseAuthServiceHasNoData() throws Exception {
        fillResponseAuthServiceHasNoDataTest(false);
    }

    private void fillResponseAuthServiceHasNoDataTest(boolean canSave) throws Exception {
        // Set service.
        enableService();
        final MyAutofillCallback callback = mActivity.registerCallback();

        // Prepare the authenticated response
        final CannedFillResponse response = canSave
                ? new CannedFillResponse.Builder()
                        .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD)
                        .build()
                : CannedFillResponse.NO_RESPONSE;

        final IntentSender authentication =
                AuthenticationActivity.createSender(mContext, 1, response);

        // Configure the service behavior
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setAuthentication(authentication, ID_USERNAME, ID_PASSWORD)
                .setPresentation(createPresentation("Tap to auth response"))
                .build());

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);

        // Wait for onFill() before proceeding.
        sReplier.getNextFillRequest();
        final View username = mActivity.getUsername();
        callback.assertUiShownEvent(username);

        // Select the authentication dialog.
        sUiBot.selectDataset("Tap to auth response");
        callback.assertUiHiddenEvent(username);
        sUiBot.assertNoDatasets();
    }

    @Test
    public void testFillResponseFiltering() throws Exception {
        // Set service.
        enableService();
        final MyAutofillCallback callback = mActivity.registerCallback();

        // Prepare the authenticated response
        final Bundle clientState = new Bundle();
        clientState.putString("numbers", "4815162342");
        final IntentSender authentication = AuthenticationActivity.createSender(mContext, 1,
                new CannedFillResponse.Builder().addDataset(
                        new CannedDataset.Builder()
                                .setField(ID_USERNAME, "dude")
                                .setField(ID_PASSWORD, "sweet")
                                .setId("name")
                                .setPresentation(createPresentation("Dataset"))
                                .build())
                        .setExtras(clientState).build());

        // Configure the service behavior
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setAuthentication(authentication, ID_USERNAME, ID_PASSWORD)
                .setPresentation(createPresentation("Tap to auth response"))
                .setExtras(clientState)
                .build());

        // Set expectation for the activity
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);

        // Wait for onFill() before proceeding.
        sReplier.getNextFillRequest();
        final View username = mActivity.getUsername();

        // Make sure it's showing initially...
        callback.assertUiShownEvent(username);
        sUiBot.assertDatasets("Tap to auth response");

        // ..then type something to hide it.
        runShellCommand("input keyevent KEYCODE_A");
        callback.assertUiHiddenEvent(username);
        sUiBot.assertNoDatasets();

        // Now delete the char and assert it's shown again...
        runShellCommand("input keyevent KEYCODE_DEL");
        callback.assertUiShownEvent(username);
        sUiBot.assertDatasets("Tap to auth response");

        // ...and select it this time
        AuthenticationActivity.setResultCode(RESULT_OK);
        sUiBot.selectDataset("Tap to auth response");
        callback.assertUiHiddenEvent(username);
        callback.assertUiShownEvent(username);
        final UiObject2 picker = sUiBot.assertDatasets("Dataset");
        sUiBot.selectDataset(picker, "Dataset");
        callback.assertUiHiddenEvent(username);
        sUiBot.assertNoDatasets();

        // Check the results.
        mActivity.assertAutoFilled();

        final Bundle data = AuthenticationActivity.getData();
        assertThat(data).isNotNull();
        final String extraValue = data.getString("numbers");
        assertThat(extraValue).isEqualTo("4815162342");
    }

    @Test
    public void testDatasetAuthTwoFields() throws Exception {
        datasetAuthTwoFields(false);
    }

    @Test
    public void testDatasetAuthTwoFieldsUserCancelsFirstAttempt() throws Exception {
        datasetAuthTwoFields(true);
    }

    private void datasetAuthTwoFields(boolean cancelFirstAttempt) throws Exception {
        // TODO: current API requires these fields...
        final RemoteViews bogusPresentation = createPresentation("Whatever man, I'm not used...");
        final String bogusValue = "Y U REQUIRE IT?";

        // Set service.
        enableService();
        final MyAutofillCallback callback = mActivity.registerCallback();

        // Prepare the authenticated response
        final IntentSender authentication = AuthenticationActivity.createSender(mContext, 1,
                new CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(bogusPresentation)
                        .build());

        // Configure the service behavior
        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, bogusValue)
                        .setField(ID_PASSWORD, bogusValue)
                        .setPresentation(createPresentation("Tap to auth dataset"))
                        .setAuthentication(authentication)
                        .build())
                .build());

        // Set expectation for the activity
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);

        // Wait for onFill() before proceeding.
        sReplier.getNextFillRequest();
        final View username = mActivity.getUsername();
        callback.assertUiShownEvent(username);
        sUiBot.assertDatasets("Tap to auth dataset");

        // Make sure UI is show on 2nd field as well
        final View password = mActivity.getPassword();
        mActivity.onPassword(View::requestFocus);
        callback.assertUiHiddenEvent(username);
        callback.assertUiShownEvent(password);
        sUiBot.assertDatasets("Tap to auth dataset");

        // Now tap on 1st field to show it again...
        mActivity.onUsername(View::requestFocus);
        callback.assertUiHiddenEvent(password);
        callback.assertUiShownEvent(username);
        sUiBot.assertDatasets("Tap to auth dataset");

        if (cancelFirstAttempt) {
            // Trigger the auth dialog, but emulate cancel.
            AuthenticationActivity.setResultCode(RESULT_CANCELED);
            sUiBot.selectDataset("Tap to auth dataset");
            callback.assertUiHiddenEvent(username);
            callback.assertUiShownEvent(username);
            sUiBot.assertDatasets("Tap to auth dataset");

            // Make sure it's still shown on other fields...
            mActivity.onPassword(View::requestFocus);
            callback.assertUiHiddenEvent(username);
            callback.assertUiShownEvent(password);
            sUiBot.assertDatasets("Tap to auth dataset");

            // Tap on 1st field to show it again...
            mActivity.onUsername(View::requestFocus);
            callback.assertUiHiddenEvent(password);
            callback.assertUiShownEvent(username);
        }

        // ...and select it this time
        AuthenticationActivity.setResultCode(RESULT_OK);
        sUiBot.selectDataset("Tap to auth dataset");
        callback.assertUiHiddenEvent(username);
        sUiBot.assertNoDatasets();

        // Check the results.
        mActivity.assertAutoFilled();
    }

    @Test
    public void testDatasetAuthTwoFieldsReplaceResponse() throws Exception {
        // Set service.
        enableService();
        final MyAutofillCallback callback = mActivity.registerCallback();

        // Prepare the authenticated response
        final IntentSender authentication = AuthenticationActivity.createSender(mContext, 1,
                new CannedFillResponse.Builder().addDataset(
                        new CannedDataset.Builder()
                                .setField(ID_USERNAME, "dude")
                                .setField(ID_PASSWORD, "sweet")
                                .setPresentation(createPresentation("Dataset"))
                                .build())
                        .build());

        // Set up the authentication response client state
        final Bundle authentionClientState = new Bundle();
        authentionClientState.putCharSequence("clientStateKey1", "clientStateValue1");

        // Configure the service behavior
        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, (AutofillValue) null)
                        .setField(ID_PASSWORD, (AutofillValue) null)
                        .setPresentation(createPresentation("Tap to auth dataset"))
                        .setAuthentication(authentication)
                        .build())
                .setExtras(authentionClientState)
                .build());

        // Set expectation for the activity
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);

        // Wait for onFill() before proceeding.
        sReplier.getNextFillRequest();
        final View username = mActivity.getUsername();

        // Authenticate
        callback.assertUiShownEvent(username);
        sUiBot.selectDataset("Tap to auth dataset");
        callback.assertUiHiddenEvent(username);

        // Select a dataset from the new response
        callback.assertUiShownEvent(username);
        sUiBot.selectDataset("Dataset");
        callback.assertUiHiddenEvent(username);
        sUiBot.assertNoDatasets();

        // Check the results.
        mActivity.assertAutoFilled();

        final Bundle data = AuthenticationActivity.getData();
        assertThat(data).isNotNull();
        final String extraValue = data.getString("clientStateKey1");
        assertThat(extraValue).isEqualTo("clientStateValue1");
    }

    @Test
    public void testDatasetAuthTwoFieldsNoValues() throws Exception {
        // Set service.
        enableService();
        final MyAutofillCallback callback = mActivity.registerCallback();

        // Create the authentication intent
        final IntentSender authentication = AuthenticationActivity.createSender(mContext, 1,
                new CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("Dataset"))
                        .build());

        // Configure the service behavior
        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, (String) null)
                        .setField(ID_PASSWORD, (String) null)
                        .setPresentation(createPresentation("Tap to auth dataset"))
                        .setAuthentication(authentication)
                        .build())
                .build());

        // Set expectation for the activity
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);

        // Wait for onFill() before proceeding.
        sReplier.getNextFillRequest();
        final View username = mActivity.getUsername();

        // Authenticate
        callback.assertUiShownEvent(username);
        sUiBot.selectDataset("Tap to auth dataset");
        callback.assertUiHiddenEvent(username);
        sUiBot.assertNoDatasets();

        // Check the results.
        mActivity.assertAutoFilled();
    }

    @Test
    public void testDatasetAuthTwoDatasets() throws Exception {
        // TODO: current API requires these fields...
        final RemoteViews bogusPresentation = createPresentation("Whatever man, I'm not used...");
        final String bogusValue = "Y U REQUIRE IT?";

        // Set service.
        enableService();
        final MyAutofillCallback callback = mActivity.registerCallback();

        // Create the authentication intents
        final CannedDataset unlockedDataset = new CannedDataset.Builder()
                .setField(ID_USERNAME, "dude")
                .setField(ID_PASSWORD, "sweet")
                .setPresentation(bogusPresentation)
                .build();
        final IntentSender authentication1 = AuthenticationActivity.createSender(mContext, 1,
                unlockedDataset);
        final IntentSender authentication2 = AuthenticationActivity.createSender(mContext, 2,
                unlockedDataset);

        // Configure the service behavior
        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, bogusValue)
                        .setField(ID_PASSWORD, bogusValue)
                        .setPresentation(createPresentation("Tap to auth dataset 1"))
                        .setAuthentication(authentication1)
                        .build())
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, bogusValue)
                        .setField(ID_PASSWORD, bogusValue)
                        .setPresentation(createPresentation("Tap to auth dataset 2"))
                        .setAuthentication(authentication2)
                        .build())
                .build());

        // Set expectation for the activity
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);

        // Wait for onFill() before proceeding.
        sReplier.getNextFillRequest();
        final View username = mActivity.getUsername();

        // Authenticate
        callback.assertUiShownEvent(username);
        sUiBot.assertDatasets("Tap to auth dataset 1", "Tap to auth dataset 2");

        sUiBot.selectDataset("Tap to auth dataset 1");
        callback.assertUiHiddenEvent(username);
        sUiBot.assertNoDatasets();

        // Check the results.
        mActivity.assertAutoFilled();
    }

    @Test
    public void testDatasetAuthMixedSelectAuth() throws Exception {
        datasetAuthMixedTest(true);
    }

    @Test
    public void testDatasetAuthMixedSelectNonAuth() throws Exception {
        datasetAuthMixedTest(false);
    }

    private void datasetAuthMixedTest(boolean selectAuth) throws Exception {
        // Set service.
        enableService();
        final MyAutofillCallback callback = mActivity.registerCallback();

        // Prepare the authenticated response
        final IntentSender authentication = AuthenticationActivity.createSender(mContext, 1,
                new CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("Dataset"))
                        .build());

        // Configure the service behavior
        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("Tap to auth dataset"))
                        .setAuthentication(authentication)
                        .build())
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "DUDE")
                        .setField(ID_PASSWORD, "SWEET")
                        .setPresentation(createPresentation("What, me auth?"))
                        .build())
                .build());

        // Set expectation for the activity
        if (selectAuth) {
            mActivity.expectAutoFill("dude", "sweet");
        } else {
            mActivity.expectAutoFill("DUDE", "SWEET");
        }

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);

        // Wait for onFill() before proceeding.
        sReplier.getNextFillRequest();
        final View username = mActivity.getUsername();

        // Authenticate
        callback.assertUiShownEvent(username);
        sUiBot.assertDatasets("Tap to auth dataset", "What, me auth?");

        final String chosenOne = selectAuth ? "Tap to auth dataset" : "What, me auth?";
        sUiBot.selectDataset(chosenOne);
        callback.assertUiHiddenEvent(username);
        sUiBot.assertNoDatasets();

        // Check the results.
        mActivity.assertAutoFilled();
    }

    @Test
    public void testDatasetAuthFiltering() throws Exception {
        // TODO: current API requires these fields...
        final RemoteViews bogusPresentation = createPresentation("Whatever man, I'm not used...");
        final String bogusValue = "Y U REQUIRE IT?";

        // Set service.
        enableService();
        final MyAutofillCallback callback = mActivity.registerCallback();

        // Create the authentication intents
        final CannedDataset unlockedDataset = new CannedDataset.Builder()
                .setField(ID_USERNAME, "dude")
                .setField(ID_PASSWORD, "sweet")
                .setPresentation(bogusPresentation)
                .build();
        final IntentSender authentication = AuthenticationActivity.createSender(mContext, 1,
                unlockedDataset);

        // Configure the service behavior
        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, bogusValue)
                        .setField(ID_PASSWORD, bogusValue)
                        .setPresentation(createPresentation("Tap to auth dataset"))
                        .setAuthentication(authentication)
                        .build())
                .build());

        // Set expectation for the activity
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);

        // Wait for onFill() before proceeding.
        sReplier.getNextFillRequest();
        final View username = mActivity.getUsername();

        // Make sure it's showing initially...
        callback.assertUiShownEvent(username);
        sUiBot.assertDatasets("Tap to auth dataset");

        // ..then type something to hide it.
        runShellCommand("input keyevent KEYCODE_A");
        callback.assertUiHiddenEvent(username);
        sUiBot.assertNoDatasets();

        // Now delete the char and assert it's shown again...
        runShellCommand("input keyevent KEYCODE_DEL");
        callback.assertUiShownEvent(username);
        sUiBot.assertDatasets("Tap to auth dataset");

        // ...and select it this time
        sUiBot.selectDataset("Tap to auth dataset");
        callback.assertUiHiddenEvent(username);
        sUiBot.assertNoDatasets();

        // Check the results.
        mActivity.assertAutoFilled();
    }

    @Test
    public void testDatasetAuthMixedFilteringSelectAuth() throws Exception {
        datasetAuthMixedFilteringTest(true);
    }

    @Test
    public void testDatasetAuthMixedFilteringSelectNonAuth() throws Exception {
        datasetAuthMixedFilteringTest(false);
    }

    private void datasetAuthMixedFilteringTest(boolean selectAuth) throws Exception {
        // TODO: current API requires these fields...
        final RemoteViews bogusPresentation = createPresentation("Whatever man, I'm not used...");
        final String bogusValue = "Y U REQUIRE IT?";

        // Set service.
        enableService();
        final MyAutofillCallback callback = mActivity.registerCallback();

        // Create the authentication intents
        final CannedDataset unlockedDataset = new CannedDataset.Builder()
                .setField(ID_USERNAME, "DUDE")
                .setField(ID_PASSWORD, "SWEET")
                .setPresentation(bogusPresentation)
                .build();
        final IntentSender authentication = AuthenticationActivity.createSender(mContext, 1,
                unlockedDataset);

        // Configure the service behavior
        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, bogusValue)
                        .setField(ID_PASSWORD, bogusValue)
                        .setPresentation(createPresentation("Tap to auth dataset"))
                        .setAuthentication(authentication)
                        .build())
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("What, me auth?"))
                        .build())
                .build());

        // Set expectation for the activity
        if (selectAuth) {
            mActivity.expectAutoFill("DUDE", "SWEET");
        } else {
            mActivity.expectAutoFill("dude", "sweet");
        }

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);

        // Wait for onFill() before proceeding.
        sReplier.getNextFillRequest();
        final View username = mActivity.getUsername();

        // Make sure it's showing initially...
        callback.assertUiShownEvent(username);
        sUiBot.assertDatasets("Tap to auth dataset", "What, me auth?");

        // Filter the auth dataset.
        runShellCommand("input keyevent KEYCODE_D");
        sUiBot.assertDatasets("What, me auth?");

        // Filter all.
        runShellCommand("input keyevent KEYCODE_W");
        callback.assertUiHiddenEvent(username);
        sUiBot.assertNoDatasets();

        // Now delete the char and assert the non-auth is shown again.
        runShellCommand("input keyevent KEYCODE_DEL");
        callback.assertUiShownEvent(username);
        sUiBot.assertDatasets("What, me auth?");

        // Delete again and assert all dataset are shown.
        runShellCommand("input keyevent KEYCODE_DEL");
        sUiBot.assertDatasets("Tap to auth dataset", "What, me auth?");

        // ...and select it this time
        final String chosenOne = selectAuth ? "Tap to auth dataset" : "What, me auth?";
        sUiBot.selectDataset(chosenOne);
        callback.assertUiHiddenEvent(username);
        sUiBot.assertNoDatasets();

        // Check the results.
        mActivity.assertAutoFilled();
    }

    @Test
    public void testDisableSelf() throws Exception {
        enableService();

        // Can disable while connected.
        mActivity.runOnUiThread(() -> mContext.getSystemService(
                AutofillManager.class).disableAutofillServices());

        // Ensure disabled.
        assertServiceDisabled();
    }

    @Test
    public void testRejectStyleNegativeSaveButton() throws Exception {
        enableService();

        // Set service behavior.

        final String intentAction = "android.autofillservice.cts.CUSTOM_ACTION";

        // Configure the save UI.
        final IntentSender listener = PendingIntent.getBroadcast(
                mContext, 0, new Intent(intentAction), 0).getIntentSender();

        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD)
                .setNegativeAction(SaveInfo.NEGATIVE_BUTTON_STYLE_REJECT, listener)
                .build());

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);

        // Wait for onFill() before proceeding.
        sReplier.getNextFillRequest();

        // Trigger save.
        mActivity.onUsername((v) -> v.setText("foo"));
        mActivity.onPassword((v) -> v.setText("foo"));
        mActivity.tapLogin();

        // Start watching for the negative intent
        final CountDownLatch latch = new CountDownLatch(1);
        final IntentFilter intentFilter = new IntentFilter(intentAction);
        mContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mContext.unregisterReceiver(this);
                latch.countDown();
            }
        }, intentFilter);

        // Trigger the negative button.
        sUiBot.saveForAutofill(SaveInfo.NEGATIVE_BUTTON_STYLE_REJECT,
                false, SAVE_DATA_TYPE_PASSWORD);

        // Wait for the custom action.
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

        assertNoDanglingSessions();
    }

    @Test
    public void testCancelStyleNegativeSaveButton() throws Exception {
        enableService();

        // Set service behavior.

        final String intentAction = "android.autofillservice.cts.CUSTOM_ACTION";

        // Configure the save UI.
        final IntentSender listener = PendingIntent.getBroadcast(
                mContext, 0, new Intent(intentAction), 0).getIntentSender();

        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD)
                .setNegativeAction(SaveInfo.NEGATIVE_BUTTON_STYLE_CANCEL, listener)
                .build());

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);

        // Wait for onFill() before proceeding.
        sReplier.getNextFillRequest();

        // Trigger save.
        mActivity.onUsername((v) -> v.setText("foo"));
        mActivity.onPassword((v) -> v.setText("foo"));
        mActivity.tapLogin();

        // Start watching for the negative intent
        final CountDownLatch latch = new CountDownLatch(1);
        final IntentFilter intentFilter = new IntentFilter(intentAction);
        mContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mContext.unregisterReceiver(this);
                latch.countDown();
            }
        }, intentFilter);

        // Trigger the negative button.
        sUiBot.saveForAutofill(SaveInfo.NEGATIVE_BUTTON_STYLE_CANCEL,
                false, SAVE_DATA_TYPE_PASSWORD);

        // Wait for the custom action.
        assertThat(latch.await(500, TimeUnit.SECONDS)).isTrue();

        assertNoDanglingSessions();
    }

    @Test
    public void testGetTextInputType() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(NO_RESPONSE);

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);

        // Assert input text on fill request:
        final FillRequest fillRequest = sReplier.getNextFillRequest();

        final ViewNode label = findNodeByResourceId(fillRequest.structure, ID_PASSWORD_LABEL);
        assertThat(label.getInputType()).isEqualTo(TYPE_NULL);
        final ViewNode password = findNodeByResourceId(fillRequest.structure, ID_PASSWORD);
        assertWithMessage("No TYPE_TEXT_VARIATION_PASSWORD on %s", password.getInputType())
                .that(password.getInputType() & TYPE_TEXT_VARIATION_PASSWORD)
                .isEqualTo(TYPE_TEXT_VARIATION_PASSWORD);
    }

    @Test
    public void testNoContainers() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(NO_RESPONSE);

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);

        sUiBot.assertNoDatasets();

        final FillRequest fillRequest = sReplier.getNextFillRequest();

        // Assert it only has 1 root view with 10 "leaf" nodes:
        // 1.text view for app title
        // 2.username text label
        // 3.username text field
        // 4.password text label
        // 5.password text field
        // 6.output text field
        // 7.clear button
        // 8.save button
        // 9.login button
        // 10.cancel button
        //
        // But it also has an intermediate container (for username) that should be included because
        // it has a resource id.

        assertNumberOfChildren(fillRequest.structure, 12);

        // Make sure container with a resource id was included:
        final ViewNode usernameContainer = findNodeByResourceId(fillRequest.structure,
                ID_USERNAME_CONTAINER);
        assertThat(usernameContainer).isNotNull();
        assertThat(usernameContainer.getChildCount()).isEqualTo(2);
    }

    @Test
    public void testAutofillManuallyOneDataset() throws Exception {
        // Set service.
        enableService();

        // And activity.
        mActivity.onUsername((v) -> v.setImportantForAutofill(IMPORTANT_FOR_AUTOFILL_NO));

        // Set expectations.
        sReplier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, "dude")
                .setField(ID_PASSWORD, "sweet")
                .setPresentation(createPresentation("The Dude"))
                .build());
        mActivity.expectAutoFill("dude", "sweet");

        // Explicitly uses the contextual menu to test that functionality.
        sUiBot.getAutofillMenuOption(ID_USERNAME).click();

        final FillRequest fillRequest = sReplier.getNextFillRequest();
        assertThat(fillRequest.flags).isEqualTo(FLAG_MANUAL_REQUEST);

        // Should have been automatically filled.
        sUiBot.selectDataset("The Dude");

        // Check the results.
        mActivity.assertAutoFilled();
    }

    @Test
    public void testAutofillManuallyTwoDatasetsPickFirst() throws Exception {
        autofillManuallyTwoDatasets(true);
    }

    @Test
    public void testAutofillManuallyTwoDatasetsPickSecond() throws Exception {
        autofillManuallyTwoDatasets(false);
    }

    private void autofillManuallyTwoDatasets(boolean pickFirst) throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("The Dude"))
                        .build())
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "jenny")
                        .setField(ID_PASSWORD, "8675309")
                        .setPresentation(createPresentation("Jenny"))
                        .build())
                .build());
        if (pickFirst) {
            mActivity.expectAutoFill("dude", "sweet");
        } else {
            mActivity.expectAutoFill("jenny", "8675309");

        }

        // Force a manual autofill request.
        mActivity.forceAutofillOnUsername();

        final FillRequest fillRequest = sReplier.getNextFillRequest();
        assertThat(fillRequest.flags).isEqualTo(FLAG_MANUAL_REQUEST);

        // Auto-fill it.
        final UiObject2 picker = sUiBot.assertDatasets("The Dude", "Jenny");
        sUiBot.selectDataset(picker, pickFirst ? "The Dude" : "Jenny");

        // Check the results.
        mActivity.assertAutoFilled();
    }

    @Test
    public void testAutofillManuallyPartialField() throws Exception {
        // Set service.
        enableService();

        // And activity.
        mActivity.onUsername((v) -> v.setText("dud"));
        mActivity.onPassword((v) -> v.setText("IamSecretMan"));

        // Set expectations.
        sReplier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, "dude")
                .setField(ID_PASSWORD, "sweet")
                .setPresentation(createPresentation("The Dude"))
                .build());
        mActivity.expectAutoFill("dude", "sweet");

        // Force a manual autofill request.
        mActivity.forceAutofillOnUsername();

        final FillRequest fillRequest = sReplier.getNextFillRequest();
        assertThat(fillRequest.flags).isEqualTo(FLAG_MANUAL_REQUEST);
        // Username value should be available because it triggered the manual request...
        assertValue(fillRequest.structure, ID_USERNAME, "dud");
        // ... but password didn't
        assertTextIsSanitized(fillRequest.structure, ID_PASSWORD);

        // Selects the dataset.
        sUiBot.selectDataset("The Dude");

        // Check the results.
        mActivity.assertAutoFilled();
    }

    @Test
    public void testAutofillManuallyAgainAfterAutomaticallyAutofilledBefore() throws Exception {
        // Set service.
        enableService();

        /*
         * 1st fill (automatic).
         */
        // Set expectations.
        sReplier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, "dude")
                .setField(ID_PASSWORD, "sweet")
                .setPresentation(createPresentation("The Dude"))
                .build());
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);

        // Assert request.
        final FillRequest fillRequest1 = sReplier.getNextFillRequest();
        assertThat(fillRequest1.flags).isEqualTo(0);
        assertTextIsSanitized(fillRequest1.structure, ID_USERNAME);
        assertTextIsSanitized(fillRequest1.structure, ID_PASSWORD);

        // Select it.
        sUiBot.selectDataset("The Dude");

        // Check the results.
        mActivity.assertAutoFilled();

        /*
         * 2nd fill (manual).
         */
        // Set expectations.
        sReplier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, "DUDE")
                .setField(ID_PASSWORD, "SWEET")
                .setPresentation(createPresentation("THE DUDE"))
                .build());
        mActivity.expectAutoFill("DUDE", "SWEET");
        // Change password to make sure it's not sent to the service.
        mActivity.onPassword((v) -> v.setText("IamSecretMan"));

        // Trigger auto-fill.
        mActivity.forceAutofillOnUsername();

        // Assert request.
        final FillRequest fillRequest2 = sReplier.getNextFillRequest();
        assertThat(fillRequest2.flags).isEqualTo(FLAG_MANUAL_REQUEST);
        assertValue(fillRequest2.structure, ID_USERNAME, "dude");
        assertTextIsSanitized(fillRequest2.structure, ID_PASSWORD);

        // Select it.
        sUiBot.selectDataset("THE DUDE");

        // Check the results.
        mActivity.assertAutoFilled();
    }

    @Test
    public void testAutofillManuallyAgainAfterManuallyAutofilledBefore() throws Exception {
        // Set service.
        enableService();

        /*
         * 1st fill (manual).
         */
        // Set expectations.
        sReplier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, "dude")
                .setField(ID_PASSWORD, "sweet")
                .setPresentation(createPresentation("The Dude"))
                .build());
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mActivity.forceAutofillOnUsername();

        // Assert request.
        final FillRequest fillRequest1 = sReplier.getNextFillRequest();
        assertThat(fillRequest1.flags).isEqualTo(FLAG_MANUAL_REQUEST);
        assertValue(fillRequest1.structure, ID_USERNAME, "");
        assertTextIsSanitized(fillRequest1.structure, ID_PASSWORD);

        // Select it.
        sUiBot.selectDataset("The Dude");

        // Check the results.
        mActivity.assertAutoFilled();

        /*
         * 2nd fill (manual).
         */
        // Set expectations.
        sReplier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, "DUDE")
                .setField(ID_PASSWORD, "SWEET")
                .setPresentation(createPresentation("THE DUDE"))
                .build());
        mActivity.expectAutoFill("DUDE", "SWEET");
        // Change password to make sure it's not sent to the service.
        mActivity.onPassword((v) -> v.setText("IamSecretMan"));

        // Trigger auto-fill.
        mActivity.forceAutofillOnUsername();

        // Assert request.
        final FillRequest fillRequest2 = sReplier.getNextFillRequest();
        assertThat(fillRequest2.flags).isEqualTo(FLAG_MANUAL_REQUEST);
        assertValue(fillRequest2.structure, ID_USERNAME, "dude");
        assertTextIsSanitized(fillRequest2.structure, ID_PASSWORD);

        // Select it.
        sUiBot.selectDataset("THE DUDE");

        // Check the results.
        mActivity.assertAutoFilled();
    }

    @Test
    public void testCommitMultipleTimes() throws Throwable {
        // Set service.
        enableService();

        final CannedFillResponse response = new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD)
                .build();

        for (int i = 1; i <= 3; i++) {
            Log.i(TAG, "testCommitMultipleTimes(): step " + i);
            final String username = "user-" + i;
            final String password = "pass-" + i;
            try {
                // Set expectations.
                sReplier.addResponse(response);

                // Trigger auto-fill.
                mActivity.onUsername(View::requestFocus);

                // Sanity check.
                sUiBot.assertNoDatasets();

                // Wait for onFill() before proceeding, otherwise the fields might be changed before
                // the session started
                waitUntilConnected();
                sReplier.getNextFillRequest();

                // Set credentials...
                mActivity.onUsername((v) -> v.setText(username));
                mActivity.onPassword((v) -> v.setText(password));

                // Change focus to prepare for next step - must do it before session is gone
                mActivity.onPassword(View::requestFocus);

                // ...and save them
                mActivity.tapSave();

                // Assert the snack bar is shown and tap "Save".
                sUiBot.saveForAutofill(true, SAVE_DATA_TYPE_PASSWORD);

                final SaveRequest saveRequest = sReplier.getNextSaveRequest();

                // Assert value of expected fields - should not be sanitized.
                final ViewNode usernameNode = findNodeByResourceId(saveRequest.structure,
                        ID_USERNAME);
                assertTextAndValue(usernameNode, username);
                final ViewNode passwordNode = findNodeByResourceId(saveRequest.structure,
                        ID_PASSWORD);
                assertTextAndValue(passwordNode, password);

                waitUntilDisconnected();
                assertNoDanglingSessions();
            } catch (RetryableException e) {
                throw new RetryableException(e, "on step %d", i);
            } catch (Throwable t) {
                throw new Throwable("Error on step " + i, t);
            }
        }
    }

    @Test
    public void testCancelMultipleTimes() throws Throwable {
        // Set service.
        enableService();

        for (int i = 1; i <= 3; i++) {
            Log.i(TAG, "testCancelMultipleTimes(): step " + i);
            final String username = "user-" + i;
            final String password = "pass-" + i;
            sReplier.addResponse(new CannedDataset.Builder()
                    .setField(ID_USERNAME, username)
                    .setField(ID_PASSWORD, password)
                    .setPresentation(createPresentation("The Dude"))
                    .build());
            mActivity.expectAutoFill(username, password);
            try {
                // Trigger auto-fill.
                mActivity.onUsername(View::requestFocus);

                waitUntilConnected();
                sReplier.getNextFillRequest();

                // Auto-fill it.
                sUiBot.selectDataset("The Dude");

                // Check the results.
                mActivity.assertAutoFilled();

                // Change focus to prepare for next step - must do it before session is gone
                mActivity.onPassword(View::requestFocus);

                // Rinse and repeat...
                mActivity.tapClear();

                waitUntilDisconnected();
                assertNoDanglingSessions();
            } catch (RetryableException e) {
                throw e;
            } catch (Throwable t) {
                throw new Throwable("Error on step " + i, t);
            }
        }
    }

    @Test
    public void testClickCustomButton() throws Exception {
        // Set service.
        enableService();

        Intent intent = new Intent(mContext, EmptyActivity.class);
        IntentSender sender = PendingIntent.getActivity(mContext, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_CANCEL_CURRENT)
                .getIntentSender();

        RemoteViews presentation = new RemoteViews(mPackageName, R.layout.list_item);
        presentation.setTextViewText(R.id.text1, "Poke");
        Intent firstIntent = new Intent(mContext, DummyActivity.class);
        presentation.setOnClickPendingIntent(R.id.text1, PendingIntent.getActivity(
                mContext, 0, firstIntent, PendingIntent.FLAG_ONE_SHOT
                        | PendingIntent.FLAG_CANCEL_CURRENT));

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setAuthentication(sender, ID_USERNAME)
                .setPresentation(presentation)
                .build());

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);

        // Wait for onFill() before proceeding.
        sReplier.getNextFillRequest();

        // Click on the custom button
        sUiBot.selectByText("Poke");

        // Make sure the click worked
        sUiBot.selectByText("foo");

        // Go back to the filled app.
        sUiBot.pressBack();

        // The session should be gone
        assertNoDanglingSessions();
    }

    @Test
    public void checkFillSelectionAfterSelectingDatasetAuthentication() throws Exception {
        enableService();

        // Set up FillResponse with dataset authentication
        Bundle clientState = new Bundle();
        clientState.putCharSequence("clientStateKey", "clientStateValue");

        // Prepare the authenticated response
        final IntentSender authentication = AuthenticationActivity.createSender(mContext, 1,
                new CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("Dataset"))
                        .build());

        sReplier.addResponse(new CannedFillResponse.Builder().addDataset(
                new CannedDataset.Builder()
                        .setField(ID_USERNAME, "username")
                        .setId("name")
                        .setPresentation(createPresentation("authentication"))
                        .setAuthentication(authentication)
                        .build())
                .setExtras(clientState).build());
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger autofill.
        mActivity.onUsername(View::requestFocus);

        // Authenticate
        sUiBot.selectDataset("authentication");
        sReplier.getNextFillRequest();
        mActivity.assertAutoFilled();

        // Verify fill selection
        FillEventHistory selection = InstrumentedAutoFillService.peekInstance()
                .getFillEventHistory();
        assertThat(selection.getClientState().getCharSequence("clientStateKey")).isEqualTo(
                "clientStateValue");

        assertThat(selection.getEvents().size()).isEqualTo(1);
        FillEventHistory.Event event = selection.getEvents().get(0);
        assertThat(event.getType()).isEqualTo(TYPE_DATASET_AUTHENTICATION_SELECTED);
        assertThat(event.getDatasetId()).isEqualTo("name");
    }

    @Test
    public void checkFillSelectionAfterSelectingAuthentication() throws Exception {
        enableService();

        // Set up FillResponse with response wide authentication
        Bundle clientState = new Bundle();
        clientState.putCharSequence("clientStateKey", "clientStateValue");

        // Prepare the authenticated response
        final IntentSender authentication = AuthenticationActivity.createSender(mContext, 1,
                new CannedFillResponse.Builder().addDataset(
                        new CannedDataset.Builder()
                                .setField(ID_USERNAME, "username")
                                .setId("name")
                                .setPresentation(createPresentation("dataset"))
                                .build())
                        .setExtras(clientState).build());

        sReplier.addResponse(new CannedFillResponse.Builder().setExtras(clientState)
                .setPresentation(createPresentation("authentication"))
                .setAuthentication(authentication, ID_USERNAME)
                .build());

        // Trigger autofill.
        mActivity.onUsername(View::requestFocus);

        // Authenticate
        sUiBot.selectDataset("authentication");
        sReplier.getNextFillRequest();
        sUiBot.assertDatasets("dataset");

        // Verify fill selection
        FillEventHistory selection = InstrumentedAutoFillService.peekInstance()
                .getFillEventHistory();
        assertThat(selection.getClientState().getCharSequence("clientStateKey")).isEqualTo(
                "clientStateValue");

        assertThat(selection.getEvents().size()).isEqualTo(1);
        FillEventHistory.Event event = selection.getEvents().get(0);
        assertThat(event.getType()).isEqualTo(TYPE_AUTHENTICATION_SELECTED);
        assertThat(event.getDatasetId()).isNull();
    }

    @Test
    public void checkFillSelectionAfterSelectingTwoDatasets() throws Exception {
        enableService();

        // Set up first partition with an anonymous dataset
        sReplier.addResponse(new CannedFillResponse.Builder().addDataset(
                new CannedDataset.Builder()
                        .setField(ID_USERNAME, "username")
                        .setPresentation(createPresentation("dataset1"))
                        .build())
                .build());
        mActivity.expectAutoFill("username");

        // Trigger autofill on username
        mActivity.onUsername(View::requestFocus);
        waitUntilConnected();
        sUiBot.selectDataset("dataset1");
        sReplier.getNextFillRequest();
        mActivity.assertAutoFilled();

        {
            // Verify fill selection
            FillEventHistory selection = InstrumentedAutoFillService.peekInstance()
                    .getFillEventHistory();
            assertThat(selection.getClientState()).isNull();

            assertThat(selection.getEvents().size()).isEqualTo(1);
            FillEventHistory.Event event = selection.getEvents().get(0);
            assertThat(event.getType()).isEqualTo(TYPE_DATASET_SELECTED);
            assertThat(event.getDatasetId()).isNull();
        }

        // Set up second partition with a named dataset
        Bundle clientState = new Bundle();
        clientState.putCharSequence("clientStateKey", "clientStateValue");

        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(
                        new CannedDataset.Builder()
                                .setField(ID_PASSWORD, "password2")
                                .setPresentation(createPresentation("dataset2"))
                                .setId("name2")
                                .build())
                .addDataset(
                        new CannedDataset.Builder()
                                .setField(ID_PASSWORD, "password3")
                                .setPresentation(createPresentation("dataset3"))
                                .setId("name3")
                                .build())
                .setExtras(clientState)
                .setRequiredSavableIds(SAVE_DATA_TYPE_GENERIC, ID_PASSWORD).build());
        mActivity.expectPasswordAutoFill("password3");

        // Trigger autofill on password
        mActivity.onPassword(View::requestFocus);
        sUiBot.selectDataset("dataset3");
        sReplier.getNextFillRequest();
        mActivity.assertAutoFilled();

        {
            // Verify fill selection
            FillEventHistory selection = InstrumentedAutoFillService.peekInstance()
                    .getFillEventHistory();
            assertThat(selection.getClientState().getCharSequence("clientStateKey")).isEqualTo(
                    "clientStateValue");

            assertThat(selection.getEvents().size()).isEqualTo(1);
            FillEventHistory.Event event = selection.getEvents().get(0);
            assertThat(event.getType()).isEqualTo(TYPE_DATASET_SELECTED);
            assertThat(event.getDatasetId()).isEqualTo("name3");
        }

        mActivity.onPassword((v) -> v.setText("new password"));
        mActivity.syncRunOnUiThread(() -> mActivity.finish());
        waitUntilDisconnected();

        {
            // Verify fill selection
            FillEventHistory selection = InstrumentedAutoFillService.peekInstance()
                    .getFillEventHistory();
            assertThat(selection.getClientState().getCharSequence("clientStateKey")).isEqualTo(
                    "clientStateValue");

            assertThat(selection.getEvents().size()).isEqualTo(2);
            FillEventHistory.Event event1 = selection.getEvents().get(0);
            assertThat(event1.getType()).isEqualTo(TYPE_DATASET_SELECTED);
            assertThat(event1.getDatasetId()).isEqualTo("name3");

            FillEventHistory.Event event2 = selection.getEvents().get(1);
            assertThat(event2.getType()).isEqualTo(TYPE_SAVE_SHOWN);
            assertThat(event2.getDatasetId()).isNull();
        }
    }

    @Test
    public void checkFillSelectionIsResetAfterReturningNull() throws Exception {
        enableService();

        // First reset
        sReplier.addResponse(new CannedFillResponse.Builder().addDataset(
                new CannedDataset.Builder()
                        .setField(ID_USERNAME, "username")
                        .setPresentation(createPresentation("dataset1"))
                        .build())
                .build());
        mActivity.expectAutoFill("username");

        mActivity.onUsername(View::requestFocus);
        waitUntilConnected();
        sReplier.getNextFillRequest();
        sUiBot.selectDataset("dataset1");
        mActivity.assertAutoFilled();

        {
            // Verify fill selection
            FillEventHistory selection = InstrumentedAutoFillService.peekInstance()
                    .getFillEventHistory();
            assertThat(selection.getClientState()).isNull();

            assertThat(selection.getEvents().size()).isEqualTo(1);
            FillEventHistory.Event event = selection.getEvents().get(0);
            assertThat(event.getType()).isEqualTo(TYPE_DATASET_SELECTED);
            assertThat(event.getDatasetId()).isNull();
        }

        // Second request
        sReplier.addResponse(NO_RESPONSE);
        mActivity.onPassword(View::requestFocus);
        sReplier.getNextFillRequest();
        sUiBot.assertNoDatasets();
        waitUntilDisconnected();

        {
            // Verify fill selection
            FillEventHistory selection = InstrumentedAutoFillService.peekInstance()
                    .getFillEventHistory();
            assertThat(selection).isNull();
        }
    }

    @Test
    public void checkFillSelectionIsResetAfterReturningError() throws Exception {
        enableService();

        // First reset
        sReplier.addResponse(new CannedFillResponse.Builder().addDataset(
                new CannedDataset.Builder()
                        .setField(ID_USERNAME, "username")
                        .setPresentation(createPresentation("dataset1"))
                        .build())
                .build());
        mActivity.expectAutoFill("username");

        mActivity.onUsername(View::requestFocus);
        waitUntilConnected();
        sReplier.getNextFillRequest();
        sUiBot.selectDataset("dataset1");
        mActivity.assertAutoFilled();

        {
            // Verify fill selection
            FillEventHistory selection = InstrumentedAutoFillService.peekInstance()
                    .getFillEventHistory();
            assertThat(selection.getClientState()).isNull();

            assertThat(selection.getEvents().size()).isEqualTo(1);
            FillEventHistory.Event event = selection.getEvents().get(0);
            assertThat(event.getType()).isEqualTo(TYPE_DATASET_SELECTED);
            assertThat(event.getDatasetId()).isNull();
        }

        // Second request
        sReplier.addResponse(new CannedFillResponse.Builder().returnFailure("D'OH!").build());
        mActivity.onPassword(View::requestFocus);
        sReplier.getNextFillRequest();
        sUiBot.assertNoDatasets();
        waitUntilDisconnected();

        {
            // Verify fill selection
            FillEventHistory selection = InstrumentedAutoFillService.peekInstance()
                    .getFillEventHistory();
            assertThat(selection).isNull();
        }
    }

    @Test
    public void checkFillSelectionIsResetAfterTimeout() throws Exception {
        enableService();

        // First reset
        sReplier.addResponse(new CannedFillResponse.Builder().addDataset(
                new CannedDataset.Builder()
                        .setField(ID_USERNAME, "username")
                        .setPresentation(createPresentation("dataset1"))
                        .build())
                .build());
        mActivity.expectAutoFill("username");

        mActivity.onUsername(View::requestFocus);
        waitUntilConnected();
        sReplier.getNextFillRequest();
        sUiBot.selectDataset("dataset1");
        mActivity.assertAutoFilled();

        {
            // Verify fill selection
            FillEventHistory selection = InstrumentedAutoFillService.peekInstance()
                    .getFillEventHistory();
            assertThat(selection.getClientState()).isNull();

            assertThat(selection.getEvents().size()).isEqualTo(1);
            FillEventHistory.Event event = selection.getEvents().get(0);
            assertThat(event.getType()).isEqualTo(TYPE_DATASET_SELECTED);
            assertThat(event.getDatasetId()).isNull();
        }

        // Second request
        sReplier.addResponse(DO_NOT_REPLY_RESPONSE);
        mActivity.onPassword(View::requestFocus);
        sReplier.getNextFillRequest();
        waitUntilDisconnected();

        {
            // Verify fill selection
            FillEventHistory selection = InstrumentedAutoFillService.peekInstance()
                    .getFillEventHistory();
            assertThat(selection).isNull();
        }
    }

    private Bundle getBundle(String key, String value) {
        final Bundle bundle = new Bundle();
        bundle.putString(key, value);
        return bundle;
    }

    /**
     * Tests the following scenario:
     *
     * <ol>
     *    <li>Activity A is launched.
     *    <li>Activity A triggers autofill.
     *    <li>Activity B is launched.
     *    <li>Activity B triggers autofill.
     *    <li>User goes back to Activity A.
     *    <li>User triggers save on Activity A - at this point, service should have stats of
     *        activity B, and stats for activity A should have beeen discarded.
     * </ol>
     */
    @Test
    public void checkFillSelectionFromPreviousSessionIsDiscarded() throws Exception {
        enableService();

        // Launch activity A
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setExtras(getBundle("activity", "A"))
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD)
                .build());

        // Trigger autofill on activity A
        mActivity.onUsername(View::requestFocus);
        waitUntilConnected();
        sReplier.getNextFillRequest();

        // Verify fill selection for Activity A
        FillEventHistory selectionA = InstrumentedAutoFillService.peekInstance()
                .getFillEventHistory();
        assertThat(selectionA.getClientState().getString("activity")).isEqualTo("A");
        assertThat(selectionA.getEvents()).isNull();

        // Launch activity B
        mContext.startActivity(new Intent(mContext, CheckoutActivity.class));

        // Trigger autofill on activity B
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setExtras(getBundle("activity", "B"))
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_CC_NUMBER, "4815162342")
                        .setPresentation(createPresentation("datasetB"))
                        .build())
                .build());
        sUiBot.focusByRelativeId(ID_CC_NUMBER);
        sReplier.getNextFillRequest();

        // Verify fill selection for Activity B
        final FillEventHistory selectionB = InstrumentedAutoFillService.peekInstance()
                .getFillEventHistory();
        assertThat(selectionB.getClientState().getString("activity")).isEqualTo("B");
        assertThat(selectionB.getEvents()).isNull();

        // Now switch back to A...
        sUiBot.pressBack(); // dismiss keyboard
        sUiBot.pressBack(); // dismiss task
        sUiBot.assertShownByRelativeId(ID_USERNAME);
        // ...and trigger save
        // Set credentials...
        mActivity.onUsername((v) -> v.setText("malkovich"));
        mActivity.onPassword((v) -> v.setText("malkovich"));
        final String expectedMessage = getWelcomeMessage("malkovich");
        final String actualMessage = mActivity.tapLogin();
        assertWithMessage("Wrong welcome msg").that(actualMessage).isEqualTo(expectedMessage);
        sUiBot.saveForAutofill(true, SAVE_DATA_TYPE_PASSWORD);
        sReplier.getNextSaveRequest();

        // Finally, make sure history is right
        final FillEventHistory finalSelection = InstrumentedAutoFillService.peekInstance()
                .getFillEventHistory();
        assertThat(finalSelection.getClientState().getString("activity")).isEqualTo("B");
        assertThat(finalSelection.getEvents()).isNull();

    }

    @Test
    public void testIsServiceEnabled() throws Exception {
        disableService();
        final AutofillManager afm = mActivity.getAutofillManager();
        assertThat(afm.hasEnabledAutofillServices()).isFalse();
        try {
            enableService();
            assertThat(afm.hasEnabledAutofillServices()).isTrue();
        } finally {
            disableService();
        }
    }

    @Test
    public void testSetupComplete() throws Exception {
        enableService();

        // Sanity check.
        final AutofillManager afm = mActivity.getAutofillManager();
        assertThat(afm.isEnabled()).isTrue();

        // Now disable user_complete and try again.
        try {
            setUserComplete(mContext, false);
            assertThat(afm.isEnabled()).isFalse();
        } finally {
            setUserComplete(mContext, true);
        }
    }

    @Test
    public void testPopupGoesAwayWhenServiceIsChanged() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, "dude")
                .setField(ID_PASSWORD, "sweet")
                .setPresentation(createPresentation("The Dude"))
                .build());
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();
        sUiBot.assertDatasets("The Dude");

        // Now disable service by setting another service
        Helper.enableAutofillService(mContext, NoOpAutofillService.SERVICE_NAME);

        // ...and make sure popup's gone
        sUiBot.assertNoDatasets();
    }

    @Test
    public void testAutofillMovesCursorToTheEnd() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, "dude")
                .setField(ID_PASSWORD, "sweet")
                .setPresentation(createPresentation("The Dude"))
                .build());
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();

        // Auto-fill it.
        sUiBot.selectDataset("The Dude");

        // Check the results.
        mActivity.assertAutoFilled();

        // NOTE: need to call getSelectionEnd() inside the UI thread, otherwise it returns 0
        final AtomicInteger atomicBombToKillASmallInsect = new AtomicInteger();

        mActivity.onUsername((v) -> atomicBombToKillASmallInsect.set(v.getSelectionEnd()));
        assertWithMessage("Wrong position on username").that(atomicBombToKillASmallInsect.get())
                .isEqualTo(4);

        mActivity.onPassword((v) -> atomicBombToKillASmallInsect.set(v.getSelectionEnd()));
        assertWithMessage("Wrong position on password").that(atomicBombToKillASmallInsect.get())
                .isEqualTo(5);
    }

    @Test
    public void testAutofillLargeNumberOfDatasets() throws Exception {
        // Set service.
        enableService();

        final StringBuilder bigStringBuilder = new StringBuilder();
        for (int i = 0; i < 10_000 ; i++) {
            bigStringBuilder.append("BigAmI");
        }
        final String bigString = bigStringBuilder.toString();

        final int size = 100;
        Log.d(TAG, "testAutofillLargeNumberOfDatasets(): " + size + " datasets with "
                + bigString.length() +"-bytes id");

        final CannedFillResponse.Builder response = new CannedFillResponse.Builder();
        for (int i = 0; i < size; i++) {
            final String suffix = "-" + (i + 1);
            response.addDataset(new CannedDataset.Builder()
                    .setField(ID_USERNAME, "user" + suffix)
                    .setField(ID_PASSWORD, "pass" + suffix)
                    .setId(bigString)
                    .setPresentation(createPresentation("DS" + suffix))
                    .build());
        }

        // Set expectations.
        sReplier.addResponse(response.build());

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();

        // Make sure all datasets are shown.
        // TODO: improve assertDatasets() so it supports scrolling, and assert all of them are
        // shown
        sUiBot.assertDatasets("DS-1", "DS-2", "DS-3");

        // TODO: once it supports scrolling, selects the last dataset and asserts it's filled.
    }

    @Test
    public void testCancellationSignalCalledAfterTimeout() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        final OneTimeCancellationSignalListener listener =
                new OneTimeCancellationSignalListener(Helper.FILL_TIMEOUT_MS + 2000);
        sReplier.addResponse(DO_NOT_REPLY_RESPONSE);

        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);

        // Attach listener to CancellationSignal.
        waitUntilConnected();
        sReplier.getNextFillRequest().cancellationSignal.setOnCancelListener(listener);

        // AssertResults
        waitUntilDisconnected();
        listener.assertOnCancelCalled();
    }
}
