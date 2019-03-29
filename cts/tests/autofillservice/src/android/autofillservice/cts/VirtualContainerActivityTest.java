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

import static android.autofillservice.cts.CannedFillResponse.NO_RESPONSE;
import static android.autofillservice.cts.Helper.ID_PASSWORD;
import static android.autofillservice.cts.Helper.ID_PASSWORD_LABEL;
import static android.autofillservice.cts.Helper.ID_USERNAME;
import static android.autofillservice.cts.Helper.ID_USERNAME_LABEL;
import static android.autofillservice.cts.Helper.assertTextAndValue;
import static android.autofillservice.cts.Helper.assertTextIsSanitized;
import static android.autofillservice.cts.Helper.dumpStructure;
import static android.autofillservice.cts.Helper.findNodeByResourceId;
import static android.autofillservice.cts.VirtualContainerView.LABEL_CLASS;
import static android.autofillservice.cts.VirtualContainerView.TEXT_CLASS;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_PASSWORD;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.assist.AssistStructure.ViewNode;
import android.autofillservice.cts.CannedFillResponse.CannedDataset;
import android.autofillservice.cts.InstrumentedAutoFillService.FillRequest;
import android.autofillservice.cts.VirtualContainerView.Line;
import android.graphics.Rect;
import android.os.SystemClock;
import android.service.autofill.SaveInfo;
import android.support.test.uiautomator.UiObject2;
import android.view.autofill.AutofillManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test case for an activity containing virtual children.
 */
public class VirtualContainerActivityTest extends AutoFillServiceTestCase {

    @Rule
    public final AutofillActivityTestRule<VirtualContainerActivity> mActivityRule =
            new AutofillActivityTestRule<VirtualContainerActivity>(VirtualContainerActivity.class);

    private VirtualContainerActivity mActivity;

    @Before
    public void setActivity() {
        mActivity = mActivityRule.getActivity();
    }

    @After
    public void finishWelcomeActivity() {
        WelcomeActivity.finishIt();
    }

    @Test
    public void testAutofillSync() throws Exception {
        autofillTest(true);
    }

    @Test
    public void testAutofillAsync() throws Exception {
        autofillTest(false);
    }

    /**
     * Tests autofilling the virtual views, using the sync / async version of ViewStructure.addChild
     */
    private void autofillTest(boolean sync) throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, "dude")
                .setField(ID_PASSWORD, "sweet")
                .setPresentation(createPresentation("The Dude"))
                .build());
        mActivity.expectAutoFill("dude", "sweet");
        mActivity.mCustomView.setSync(sync);

        // Trigger auto-fill.
        mActivity.mUsername.changeFocus(true);
        assertDatasetShown(mActivity.mUsername, "The Dude");

        // Play around with focus to make sure picker is properly drawn.
        mActivity.mPassword.changeFocus(true);
        assertDatasetShown(mActivity.mPassword, "The Dude");
        mActivity.mUsername.changeFocus(true);
        assertDatasetShown(mActivity.mUsername, "The Dude");

        // Make sure input was sanitized.
        final FillRequest request = sReplier.getNextFillRequest();
        final ViewNode usernameLabel = findNodeByResourceId(request.structure, ID_USERNAME_LABEL);
        final ViewNode username = findNodeByResourceId(request.structure, ID_USERNAME);
        final ViewNode passwordLabel = findNodeByResourceId(request.structure, ID_PASSWORD_LABEL);
        final ViewNode password = findNodeByResourceId(request.structure, ID_PASSWORD);

        assertTextIsSanitized(username);
        assertTextIsSanitized(password);
        assertTextAndValue(usernameLabel, "Username");
        assertTextAndValue(passwordLabel, "Password");

        assertThat(usernameLabel.getClassName()).isEqualTo(LABEL_CLASS);
        assertThat(username.getClassName()).isEqualTo(TEXT_CLASS);
        assertThat(passwordLabel.getClassName()).isEqualTo(LABEL_CLASS);
        assertThat(password.getClassName()).isEqualTo(TEXT_CLASS);

        assertThat(username.getIdEntry()).isEqualTo(ID_USERNAME);
        assertThat(password.getIdEntry()).isEqualTo(ID_PASSWORD);

        // Make sure order is preserved and dupes not removed.
        assertThat(username.getAutofillHints()).asList()
                .containsExactly("c", "a", "a", "b", "a", "a")
                .inOrder();

        try {
            VirtualContainerView.assertHtmlInfo(username);
            VirtualContainerView.assertHtmlInfo(password);
        } catch (AssertionError | RuntimeException e) {
            dumpStructure("HtmlInfo failed", request.structure);
            throw e;
        }

        // Make sure initial focus was properly set.
        assertWithMessage("Username node is not focused").that(username.isFocused()).isTrue();
        assertWithMessage("Password node is focused").that(password.isFocused()).isFalse();

        // Auto-fill it.
        sUiBot.selectDataset("The Dude");

        // Check the results.
        mActivity.assertAutoFilled();

        // Sanity checks.
        sReplier.assertNumberUnhandledFillRequests(0);
        sReplier.assertNumberUnhandledSaveRequests(0);
    }

    @Test
    public void testAutofillTwoDatasets() throws Exception {
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
        mActivity.expectAutoFill("DUDE", "SWEET");

        // Trigger auto-fill.
        mActivity.mUsername.changeFocus(true);
        sReplier.getNextFillRequest();
        assertDatasetShown(mActivity.mUsername, "The Dude", "THE DUDE");

        // Play around with focus to make sure picker is properly drawn.
        mActivity.mPassword.changeFocus(true);
        assertDatasetShown(mActivity.mPassword, "The Dude", "THE DUDE");
        mActivity.mUsername.changeFocus(true);
        assertDatasetShown(mActivity.mUsername, "The Dude", "THE DUDE");

        // Auto-fill it.
        sUiBot.selectDataset("THE DUDE");

        // Check the results.
        mActivity.assertAutoFilled();
    }

    @Test
    public void testAutofillOverrideDispatchProvideAutofillStructure() throws Exception {
        mActivity.mCustomView.setOverrideDispatchProvideAutofillStructure(true);
        autofillTest(true);
    }

    @Test
    public void testAutofillManuallyOneDataset() throws Exception {
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
        mActivity.requestAutofill(mActivity.mUsername);
        sReplier.getNextFillRequest();

        // Select datatest.
        sUiBot.selectDataset("The Dude");

        // Check the results.
        mActivity.assertAutoFilled();

        // Sanity checks.
        sReplier.assertNumberUnhandledFillRequests(0);
        sReplier.assertNumberUnhandledSaveRequests(0);
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

        // Trigger auto-fill.
        mActivity.getSystemService(AutofillManager.class).requestAutofill(
                mActivity.mCustomView, mActivity.mUsername.text.id, mActivity.mUsername.bounds);
        sReplier.getNextFillRequest();

        // Auto-fill it.
        final UiObject2 picker = sUiBot.assertDatasets("The Dude", "Jenny");
        sUiBot.selectDataset(picker, pickFirst? "The Dude" : "Jenny");

        // Check the results.
        mActivity.assertAutoFilled();

        // Sanity checks.
        sReplier.assertNumberUnhandledFillRequests(0);
        sReplier.assertNumberUnhandledSaveRequests(0);
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
        mActivity.mUsername.changeFocus(true);
        sReplier.getNextFillRequest();

        callback.assertUiShownEvent(mActivity.mCustomView, mActivity.mUsername.text.id);

        // Change focus
        mActivity.mPassword.changeFocus(true);
        callback.assertUiHiddenEvent(mActivity.mCustomView, mActivity.mUsername.text.id);
        callback.assertUiShownEvent(mActivity.mCustomView, mActivity.mPassword.text.id);
    }

    @Test
    public void testAutofillCallbackDisabled() throws Exception {
        // Set service.
        disableService();
        final MyAutofillCallback callback = mActivity.registerCallback();

        // Trigger auto-fill.
        mActivity.mUsername.changeFocus(true);

        // Assert callback was called
        callback.assertUiUnavailableEvent(mActivity.mCustomView, mActivity.mUsername.text.id);
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
        mActivity.mUsername.changeFocus(true);
        sReplier.getNextFillRequest();

        // Auto-fill it.
        sUiBot.assertNoDatasets();

        // Assert callback was called
        callback.assertUiUnavailableEvent(mActivity.mCustomView, mActivity.mUsername.text.id);
    }

    @Test
    public void testSaveDialogNotShownWhenBackIsPressed() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("The Dude"))
                        .build())
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD)
                .build());
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        mActivity.mUsername.changeFocus(true);
        sReplier.getNextFillRequest();
        assertDatasetShown(mActivity.mUsername, "The Dude");

        sUiBot.pressBack();

        sUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_PASSWORD);
    }

    @Test
    public void testSaveDialogShownWhenAllVirtualViewsNotVisible() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD)
                .setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
                .build());

        final CountDownLatch latch = new CountDownLatch(1);

        // Trigger auto-fill.
        mActivity.runOnUiThread(() -> {
            mActivity.mUsername.changeFocus(true);
            latch.countDown();
        });
        latch.await(Helper.UI_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        sReplier.getNextFillRequest();

        // TODO: 63602573 Should be removed once this bug is fixed
        SystemClock.sleep(1000);

        mActivity.runOnUiThread(() -> {
            // Fill in some stuff
            mActivity.mUsername.setText("foo");
            mActivity.mPassword.setText("bar");

            // Hide all virtual views
            mActivity.mUsername.changeVisibility(false);
            mActivity.mPassword.changeVisibility(false);
        });

        // Make sure save is shown
        sUiBot.assertSaveShowing(SAVE_DATA_TYPE_PASSWORD);
    }

    /**
     * Asserts the dataset picker is properly displayed in a give line.
     */
    private void assertDatasetShown(Line line, String... expectedDatasets) {
        final Rect pickerBounds = sUiBot.assertDatasets(expectedDatasets).getVisibleBounds();
        final Rect fieldBounds = line.getAbsCoordinates();
        assertWithMessage("vertical coordinates don't match; picker=%s, field=%s", pickerBounds,
                fieldBounds).that(pickerBounds.top).isEqualTo(fieldBounds.bottom);
        assertWithMessage("horizontal coordinates don't match; picker=%s, field=%s", pickerBounds,
                fieldBounds).that(pickerBounds.left).isEqualTo(fieldBounds.left);
    }
}
