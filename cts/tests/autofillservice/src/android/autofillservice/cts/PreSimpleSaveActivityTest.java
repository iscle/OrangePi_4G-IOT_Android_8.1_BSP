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

import static android.autofillservice.cts.Helper.assertTextAndValue;
import static android.autofillservice.cts.Helper.findNodeByResourceId;
import static android.autofillservice.cts.LoginActivity.ID_USERNAME_CONTAINER;
import static android.autofillservice.cts.PreSimpleSaveActivity.ID_PRE_INPUT;
import static android.autofillservice.cts.SimpleSaveActivity.ID_INPUT;
import static android.autofillservice.cts.SimpleSaveActivity.ID_LABEL;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_EMAIL_ADDRESS;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_PASSWORD;

import android.autofillservice.cts.InstrumentedAutoFillService.SaveRequest;
import android.content.Intent;
import android.support.test.uiautomator.UiObject2;
import android.view.View;

import org.junit.Rule;

public class PreSimpleSaveActivityTest extends CustomDescriptionWithLinkTestCase {

    @Rule
    public final AutofillActivityTestRule<PreSimpleSaveActivity> mActivityRule =
            new AutofillActivityTestRule<PreSimpleSaveActivity>(PreSimpleSaveActivity.class, false);

    private PreSimpleSaveActivity mActivity;

    private void startActivity(boolean remainOnRecents) {
        final Intent intent = new Intent(mContext, PreSimpleSaveActivity.class);
        if (remainOnRecents) {
            intent.setFlags(
                    Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS | Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        }
        mActivity = mActivityRule.launchActivity(intent);
    }

    @Override
    protected void saveUiRestoredAfterTappingLinkTest(PostSaveLinkTappedAction type)
            throws Exception {
        startActivity(false);
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setCustomDescription(newCustomDescription(WelcomeActivity.class))
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_PRE_INPUT)
                .build());

        // Trigger autofill.
        mActivity.syncRunOnUiThread(() -> mActivity.mPreInput.requestFocus());
        sReplier.getNextFillRequest();
        Helper.assertHasSessions(mPackageName);

        // Trigger save.
        mActivity.syncRunOnUiThread(() -> {
            mActivity.mPreInput.setText("108");
            mActivity.mSubmit.performClick();
        });
        // Make sure post-save activity is shown...
        sUiBot.assertShownByRelativeId(ID_INPUT);

        // Tap the link.
        final UiObject2 saveUi = assertSaveUiWithLinkIsShown(SAVE_DATA_TYPE_PASSWORD);
        tapSaveUiLink(saveUi);

        // Make sure new activity is shown...
        WelcomeActivity.assertShowingDefaultMessage(sUiBot);
        sUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_PASSWORD);

        // .. then do something to return to previous activity...
        switch (type) {
            case ROTATE_THEN_TAP_BACK_BUTTON:
                sUiBot.setScreenOrientation(UiBot.LANDSCAPE);
                // not breaking on purpose
            case TAP_BACK_BUTTON:
                sUiBot.pressBack();
                break;
            case FINISH_ACTIVITY:
                // ..then finishes it.
                WelcomeActivity.finishIt();
                break;
            default:
                throw new IllegalArgumentException("invalid type: " + type);
        }

        // ... and tap save.
        final UiObject2 newSaveUi = assertSaveUiWithLinkIsShown(SAVE_DATA_TYPE_PASSWORD);
        sUiBot.saveForAutofill(newSaveUi, true);

        final SaveRequest saveRequest = sReplier.getNextSaveRequest();
        assertTextAndValue(findNodeByResourceId(saveRequest.structure, ID_PRE_INPUT), "108");
    }

    @Override
    protected void tapLinkThenTapBackThenStartOverTest(PostSaveLinkTappedAction action,
            boolean manualRequest) throws Exception {
        startActivity(false);
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setCustomDescription(newCustomDescription(WelcomeActivity.class))
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_PRE_INPUT)
                .build());

        // Trigger autofill.
        mActivity.syncRunOnUiThread(() -> mActivity.mPreInput.requestFocus());
        sReplier.getNextFillRequest();
        Helper.assertHasSessions(mPackageName);

        // Trigger save.
        mActivity.syncRunOnUiThread(() -> {
            mActivity.mPreInput.setText("108");
            mActivity.mSubmit.performClick();
        });
        // Make sure post-save activity is shown...
        sUiBot.assertShownByRelativeId(ID_INPUT);

        // Tap the link.
        final UiObject2 saveUi = assertSaveUiWithLinkIsShown(SAVE_DATA_TYPE_PASSWORD);
        tapSaveUiLink(saveUi);

        // Make sure new activity is shown...
        WelcomeActivity.assertShowingDefaultMessage(sUiBot);
        sUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_PASSWORD);

        // Tap back to restore the Save UI...
        sUiBot.pressBack();

        // ...but don't tap it...
        final UiObject2 saveUi2 = sUiBot.assertSaveShowing(SAVE_DATA_TYPE_PASSWORD);

        // ...instead, do something to dismiss it:
        switch (action) {
            case TOUCH_OUTSIDE:
                sUiBot.assertShownByRelativeId(ID_LABEL).longClick();
                break;
            case TAP_NO_ON_SAVE_UI:
                sUiBot.saveForAutofill(saveUi2, false);
                break;
            case TAP_YES_ON_SAVE_UI:
                sUiBot.saveForAutofill(true, SAVE_DATA_TYPE_PASSWORD);

                final SaveRequest saveRequest = sReplier.getNextSaveRequest();
                assertTextAndValue(findNodeByResourceId(saveRequest.structure, ID_PRE_INPUT),
                        "108");
                Helper.assertNoDanglingSessions();
                break;
            default:
                throw new IllegalArgumentException("invalid action: " + action);
        }
        sUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_PASSWORD);

        // Make sure previous session was finished.
        Helper.assertNoDanglingSessions();

        // Now triggers a new session in the new activity (SaveActivity) and do business as usual...
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_EMAIL_ADDRESS, ID_INPUT)
                .build());

        // Trigger autofill.
        final SimpleSaveActivity newActivty = SimpleSaveActivity.getInstance();
        if (manualRequest) {
            newActivty.getAutofillManager().requestAutofill(newActivty.mInput);
        } else {
            newActivty.syncRunOnUiThread(() -> newActivty.mPassword.requestFocus());
        }

        sReplier.getNextFillRequest();
        Helper.assertHasSessions(mPackageName);

        // Trigger save.
        newActivty.syncRunOnUiThread(() -> {
            newActivty.mInput.setText("42");
            newActivty.mCommit.performClick();
        });
        // Make sure post-save activity is shown...
        sUiBot.assertShownByRelativeId(ID_INPUT);

        // Save it...
        sUiBot.saveForAutofill(true, SAVE_DATA_TYPE_EMAIL_ADDRESS);

        // ... and assert results
        final SaveRequest saveRequest = sReplier.getNextSaveRequest();
        assertTextAndValue(findNodeByResourceId(saveRequest.structure, ID_INPUT), "42");
    }

    @Override
    protected void saveUiCancelledAfterTappingLinkTest(PostSaveLinkTappedAction type)
            throws Exception {
        startActivity(type == PostSaveLinkTappedAction.TAP_RECENTS);
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setCustomDescription(newCustomDescription(WelcomeActivity.class))
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_PRE_INPUT)
                .build());

        // Trigger autofill.
        mActivity.syncRunOnUiThread(() -> mActivity.mPreInput.requestFocus());
        sReplier.getNextFillRequest();
        Helper.assertHasSessions(mPackageName);

        // Trigger save.
        mActivity.syncRunOnUiThread(() -> {
            mActivity.mPreInput.setText("108");
            mActivity.mSubmit.performClick();
        });
        // Make sure post-save activity is shown...
        sUiBot.assertShownByRelativeId(ID_INPUT);

        // Tap the link.
        final UiObject2 saveUi = assertSaveUiWithLinkIsShown(SAVE_DATA_TYPE_PASSWORD);
        tapSaveUiLink(saveUi);

        // Make sure linked activity is shown...
        WelcomeActivity.assertShowingDefaultMessage(sUiBot);
        sUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_PASSWORD);

        switch (type) {
            case TAP_RECENTS:
                sUiBot.switchAppsUsingRecents();
                // Make sure right activity is showing.
                sUiBot.assertShownByRelativeId(ID_INPUT);
                break;
            case LAUNCH_PREVIOUS_ACTIVITY:
                startActivity(PreSimpleSaveActivity.class);
                sUiBot.assertShownByRelativeId(ID_INPUT);
                break;
            case LAUNCH_NEW_ACTIVITY:
                // Launch a 3rd activity...
                startActivity(LoginActivity.class);
                sUiBot.assertShownByRelativeId(ID_USERNAME_CONTAINER);
                // ...then go back
                sUiBot.pressBack();
                sUiBot.assertShownByRelativeId(ID_INPUT);
                break;
            default:
                throw new IllegalArgumentException("invalid type: " + type);
        }

        sUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_PASSWORD);
    }

    @Override
    protected void tapLinkLaunchTrampolineActivityThenTapBackAndStartNewSessionTest()
            throws Exception {
        // Prepare activity.
        startActivity(false);
        mActivity.mPreInput.getRootView()
                .setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);

        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setCustomDescription(newCustomDescription(TrampolineWelcomeActivity.class))
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_PRE_INPUT)
                .build());

        // Trigger autofill.
        mActivity.getAutofillManager().requestAutofill(mActivity.mPreInput);
        sReplier.getNextFillRequest();
        Helper.assertHasSessions(mPackageName);

        // Trigger save.
        mActivity.syncRunOnUiThread(() -> {
            mActivity.mPreInput.setText("108");
            mActivity.mSubmit.performClick();
        });
        final UiObject2 saveUi = assertSaveUiWithLinkIsShown(SAVE_DATA_TYPE_PASSWORD);

        // Tap the link.
        tapSaveUiLink(saveUi);

        // Make sure new activity is shown...
        WelcomeActivity.assertShowingDefaultMessage(sUiBot);

        // Save UI should be showing as well, since Trampoline finished.
        sUiBot.assertSaveShowing(SAVE_DATA_TYPE_PASSWORD);

        // Go back and make sure it's showing the right activity.
        sUiBot.pressBack();
        sUiBot.assertShownByRelativeId(ID_INPUT);

        // Now triggers a new session in the new activity (SaveActivity) and do business as usual...
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_EMAIL_ADDRESS, ID_INPUT)
                .build());

        // Trigger autofill.
        final SimpleSaveActivity newActivty = SimpleSaveActivity.getInstance();
        newActivty.getAutofillManager().requestAutofill(newActivty.mInput);

        sReplier.getNextFillRequest();
        Helper.assertHasSessions(mPackageName);

        // Trigger save.
        newActivty.syncRunOnUiThread(() -> {
            newActivty.mInput.setText("42");
            newActivty.mCommit.performClick();
        });
        // Make sure post-save activity is shown...
        sUiBot.assertShownByRelativeId(ID_INPUT);

        // Save it...
        sUiBot.saveForAutofill(true, SAVE_DATA_TYPE_EMAIL_ADDRESS);

        // ... and assert results
        final SaveRequest saveRequest1 = sReplier.getNextSaveRequest();
        assertTextAndValue(findNodeByResourceId(saveRequest1.structure, ID_INPUT), "42");
    }
}
