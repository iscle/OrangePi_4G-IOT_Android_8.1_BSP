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

import static android.autofillservice.cts.Helper.ID_LOGIN;
import static android.autofillservice.cts.Helper.ID_PASSWORD;
import static android.autofillservice.cts.Helper.ID_USERNAME;
import static android.autofillservice.cts.Helper.assertNoDanglingSessions;
import static android.autofillservice.cts.Helper.assertTextAndValue;
import static android.autofillservice.cts.Helper.eventually;
import static android.autofillservice.cts.Helper.findNodeByResourceId;
import static android.autofillservice.cts.Helper.getContext;
import static android.autofillservice.cts.Helper.getOutOfProcessPid;
import static android.autofillservice.cts.Helper.runShellCommand;
import static android.autofillservice.cts.OutOfProcessLoginActivity.getStoppedMarker;
import static android.autofillservice.cts.UiBot.LANDSCAPE;
import static android.autofillservice.cts.UiBot.PORTRAIT;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_PASSWORD;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.PendingIntent;
import android.app.assist.AssistStructure;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.autofill.AutofillValue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Test the lifecycle of a autofill session
 */
public class SessionLifecycleTest extends AutoFillServiceTestCase {
    private static final String USERNAME_FULL_ID = "android.autofillservice.cts:id/" + ID_USERNAME;
    private static final String PASSWORD_FULL_ID = "android.autofillservice.cts:id/" + ID_PASSWORD;
    private static final String LOGIN_FULL_ID = "android.autofillservice.cts:id/" + ID_LOGIN;
    private static final String BUTTON_FULL_ID = "android.autofillservice.cts:id/button";
    private static final String CANCEL_FULL_ID = "android.autofillservice.cts:id/cancel";

    @Before
    public void cleanUpState() {
        Helper.preTestCleanup();
    }

    /**
     * Prevents the screen to rotate by itself
     */
    @Before
    public void disableAutoRotation() {
        Helper.disableAutoRotation(sUiBot);
    }

    /**
     * Allows the screen to rotate by itself
     */
    @After
    public void allowAutoRotation() {
        Helper.allowAutoRotation();
    }

    private void killOfProcessLoginActivityProcess() throws Exception {
        // Waiting for activity to stop (stop marker appears)
        eventually(() -> assertThat(getStoppedMarker(getContext()).exists()).isTrue());

        // onStop might not be finished, hence wait more
        SystemClock.sleep(1000);

        // Kill activity that is in the background
        runShellCommand("kill -9 %d",
                getOutOfProcessPid("android.autofillservice.cts.outside"));
    }

    @Test
    public void testDatasetAuthResponseWhileAutofilledAppIsLifecycled() throws Exception {
        // Set service.
        enableService();

        // Start activity that is autofilled in a separate process so it can be killed
        Intent outOfProcessAcvitityStartIntent = new Intent(getContext(),
                OutOfProcessLoginActivity.class);
        getContext().startActivity(outOfProcessAcvitityStartIntent);

        // Set expectations.
        final Bundle extras = new Bundle();
        extras.putString("numbers", "4815162342");

        // Create the authentication intent (launching a full screen activity)
        IntentSender authentication = PendingIntent.getActivity(getContext(), 0,
                new Intent(getContext(), ManualAuthenticationActivity.class),
                0).getIntentSender();

        // Prepare the authenticated response
        ManualAuthenticationActivity.setResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_USERNAME, AutofillValue.forText("autofilled username"))
                        .setPresentation(createPresentation("dataset")).build())
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD)
                .setExtras(extras).build());

        CannedFillResponse response = new CannedFillResponse.Builder()
                .setAuthentication(authentication, ID_USERNAME, ID_PASSWORD)
                .setPresentation(createPresentation("authenticate"))
                .build();
        sReplier.addResponse(response);

        // Trigger autofill on username
        sUiBot.selectById(USERNAME_FULL_ID);

        // Wait for fill request to be processed
        sReplier.getNextFillRequest();

        // Wait until authentication is shown
        sUiBot.assertDatasets("authenticate");

        // Change orientation which triggers a destroy -> create in the app as the activity
        // cannot deal with such situations
        sUiBot.setScreenOrientation(LANDSCAPE);

        // Delete stopped marker
        getStoppedMarker(getContext()).delete();

        // Authenticate
        sUiBot.selectDataset("authenticate");

        // Kill activity that is in the background
        killOfProcessLoginActivityProcess();

        // Change orientation which triggers a destroy -> create in the app as the activity
        // cannot deal with such situations
        sUiBot.setScreenOrientation(PORTRAIT);

        // Approve authentication
        sUiBot.selectById(BUTTON_FULL_ID);

        // Wait for dataset to be shown
        sUiBot.assertDatasets("dataset");

        // Change orientation which triggers a destroy -> create in the app as the activity
        // cannot deal with such situations
        sUiBot.setScreenOrientation(LANDSCAPE);

        // Select dataset
        sUiBot.selectDataset("dataset");

        // Check the results.
        eventually(() -> assertThat(sUiBot.getTextById(USERNAME_FULL_ID)).isEqualTo(
                "autofilled username"));

        // Set password
        sUiBot.setTextById(PASSWORD_FULL_ID, "new password");

        // Login
        sUiBot.selectById(LOGIN_FULL_ID);

        // Wait for save UI to be shown
        sUiBot.assertShownById("android:id/autofill_save_yes");

        // Change orientation to make sure save UI can handle this
        sUiBot.setScreenOrientation(PORTRAIT);

        // Tap "Save".
        sUiBot.selectById("android:id/autofill_save_yes");

        // Get save request
        InstrumentedAutoFillService.SaveRequest saveRequest = sReplier.getNextSaveRequest();
        assertWithMessage("onSave() not called").that(saveRequest).isNotNull();

        // Make sure data is correctly saved
        final AssistStructure.ViewNode username = findNodeByResourceId(saveRequest.structure,
                ID_USERNAME);
        assertTextAndValue(username, "autofilled username");
        final AssistStructure.ViewNode password = findNodeByResourceId(saveRequest.structure,
                ID_PASSWORD);
        assertTextAndValue(password, "new password");

        // Make sure extras were passed back on onSave()
        assertThat(saveRequest.data).isNotNull();
        final String extraValue = saveRequest.data.getString("numbers");
        assertWithMessage("extras not passed on save").that(extraValue).isEqualTo("4815162342");

        eventually(() -> assertNoDanglingSessions());
    }

    @Test
    public void testAuthCanceledWhileAutofilledAppIsLifecycled() throws Exception {
        // Set service.
        enableService();

        // Start activity that is autofilled in a separate process so it can be killed
        Intent outOfProcessAcvitityStartIntent = new Intent(getContext(),
                OutOfProcessLoginActivity.class);
        getContext().startActivity(outOfProcessAcvitityStartIntent);

        // Create the authentication intent (launching a full screen activity)
        IntentSender authentication = PendingIntent.getActivity(getContext(), 0,
                new Intent(getContext(), ManualAuthenticationActivity.class),
                0).getIntentSender();

        CannedFillResponse response = new CannedFillResponse.Builder()
                .setAuthentication(authentication, ID_USERNAME, ID_PASSWORD)
                .setPresentation(createPresentation("authenticate"))
                .build();
        sReplier.addResponse(response);

        // Trigger autofill on username
        sUiBot.selectById(USERNAME_FULL_ID);

        // Wait for fill request to be processed
        sReplier.getNextFillRequest();

        // Wait until authentication is shown
        sUiBot.assertDatasets("authenticate");

        // Delete stopped marker
        getStoppedMarker(getContext()).delete();

        // Authenticate
        sUiBot.selectDataset("authenticate");

        // Kill activity that is in the background
        killOfProcessLoginActivityProcess();

        // Cancel authentication activity
        sUiBot.pressBack();

        // Authentication should still be shown
        sUiBot.assertDatasets("authenticate");
    }

    @Test
    public void testDatasetVisibleWhileAutofilledAppIsLifecycled() throws Exception {
        // Set service.
        enableService();

        // Start activity that is autofilled in a separate process so it can be killed
        Intent outOfProcessAcvitityStartIntent = new Intent(getContext(),
                OutOfProcessLoginActivity.class);
        getContext().startActivity(outOfProcessAcvitityStartIntent);

        CannedFillResponse response = new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder(
                        createPresentation("dataset"))
                        .setField(ID_USERNAME, "filled").build())
                .build();
        sReplier.addResponse(response);

        // Trigger autofill on username
        sUiBot.selectById(USERNAME_FULL_ID);

        // Wait for fill request to be processed
        sReplier.getNextFillRequest();

        // Wait until dataset is shown
        sUiBot.assertDatasets("dataset");

        // Delete stopped marker
        getStoppedMarker(getContext()).delete();

        // Start an activity on top of the autofilled activity
        Intent intent = new Intent(getContext(), EmptyActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        getContext().startActivity(intent);

        // Kill activity that is in the background
        killOfProcessLoginActivityProcess();

        // Cancel activity on top
        sUiBot.pressBack();

        // Dataset should still be shown
        sUiBot.assertDatasets("dataset");
    }

    @Test
    public void testAutofillNestedActivitiesWhileAutofilledAppIsLifecycled() throws Exception {
        // Set service.
        enableService();

        // Start activity that is autofilled in a separate process so it can be killed
        Intent outOfProcessAcvitityStartIntent = new Intent(getContext(),
                OutOfProcessLoginActivity.class);
        getContext().startActivity(outOfProcessAcvitityStartIntent);

        // Prepare response for first activity
        CannedFillResponse response = new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder(
                        createPresentation("dataset1"))
                        .setField(ID_USERNAME, "filled").build())
                .build();
        sReplier.addResponse(response);

        // Trigger autofill on username
        sUiBot.selectById(USERNAME_FULL_ID);

        // Wait for fill request to be processed
        sReplier.getNextFillRequest();

        // Wait until dataset1 is shown
        sUiBot.assertDatasets("dataset1");

        // Delete stopped marker
        getStoppedMarker(getContext()).delete();

        // Prepare response for nested activity
        response = new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder(
                        createPresentation("dataset2"))
                        .setField(ID_USERNAME, "filled").build())
                .build();
        sReplier.addResponse(response);

        // Start nested login activity
        Intent intent = new Intent(getContext(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(intent);

        // Kill activity that is in the background
        killOfProcessLoginActivityProcess();

        // Trigger autofill on username in nested activity
        sUiBot.selectById(USERNAME_FULL_ID);

        // Wait for fill request to be processed
        sReplier.getNextFillRequest();

        // Wait until dataset in nested activity is shown
        sUiBot.assertDatasets("dataset2");

        // Tap "Cancel".
        sUiBot.selectById(CANCEL_FULL_ID);

        // Dataset should still be shown
        sUiBot.assertDatasets("dataset1");
    }
}
