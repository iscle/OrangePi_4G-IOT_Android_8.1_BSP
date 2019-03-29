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

import static com.google.common.truth.Truth.assertThat;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.service.autofill.CustomDescription;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiObject2;
import android.widget.RemoteViews;

import org.junit.Test;

/**
 * Template for tests cases that test what happens when a link in the {@link CustomDescription} is
 * tapped by the user.
 *
 * <p>It must be extend by 2 sub-class to provide tests for the 2 distinct scenarios:
 * <ul>
 *   <li>Save is triggered by 1st activity finishing and launching a 2nd activity.
 *   <li>Save is triggered by explicit {@link android.view.autofill.AutofillManager#commit()} call
 *       and shown in the same activity.
 * </ul>
 *
 * <p>The overall behavior should be the same in both cases, although the implementation of the
 * tests per se will be sligthly different.
 */
abstract class CustomDescriptionWithLinkTestCase extends AutoFillServiceTestCase {

    private static final String ID_LINK = "link";

    /**
     * Tests scenarios when user taps a link in the custom description and then taps back:
     * the Save UI should have been restored.
     */
    @Test
    public final void testTapLink_tapBack() throws Exception {
        saveUiRestoredAfterTappingLinkTest(PostSaveLinkTappedAction.TAP_BACK_BUTTON);
    }

    /**
     * Tests scenarios when user taps a link in the custom description, change the screen
     * orientation while the new activity is show, then taps back:
     * the Save UI should have been restored.
     */
    @Test
    public final void testTapLink_changeOrientationThenTapBack() throws Exception {
        sUiBot.setScreenOrientation(UiBot.PORTRAIT);
        try {
            saveUiRestoredAfterTappingLinkTest(
                    PostSaveLinkTappedAction.ROTATE_THEN_TAP_BACK_BUTTON);
        } finally {
            sUiBot.setScreenOrientation(UiBot.PORTRAIT);
        }
    }

    /**
     * Tests scenarios when user taps a link in the custom description, then the new activity
     * finishes:
     * the Save UI should have been restored.
     */
    @Test
    public final void testTapLink_finishActivity() throws Exception {
        saveUiRestoredAfterTappingLinkTest(PostSaveLinkTappedAction.FINISH_ACTIVITY);
    }

    protected abstract void saveUiRestoredAfterTappingLinkTest(PostSaveLinkTappedAction type)
            throws Exception;

    /**
     * Tests scenarios when user taps a link in the custom description, taps back to return to the
     * activity with the Save UI, and touch outside the Save UI to dismiss it.
     *
     * <p>Then user starts a new session by focusing in a field.
     */
    @Test
    public final void testTapLink_tapBack_thenStartOverByTouchOutsideAndFocus()
            throws Exception {
        tapLinkThenTapBackThenStartOverTest(PostSaveLinkTappedAction.TOUCH_OUTSIDE, false);
    }

    /**
     * Tests scenarios when user taps a link in the custom description, taps back to return to the
     * activity with the Save UI, and touch outside the Save UI to dismiss it.
     *
     * <p>Then user starts a new session by forcing autofill.
     */
    @Test
    public void testTapLink_tapBack_thenStartOverByTouchOutsideAndManualRequest()
            throws Exception {
        tapLinkThenTapBackThenStartOverTest(PostSaveLinkTappedAction.TOUCH_OUTSIDE, true);
    }

    /**
     * Tests scenarios when user taps a link in the custom description, taps back to return to the
     * activity with the Save UI, and tap the "No" button to dismiss it.
     *
     * <p>Then user starts a new session by focusing in a field.
     */
    @Test
    public final void testTapLink_tapBack_thenStartOverBySayingNoAndFocus()
            throws Exception {
        tapLinkThenTapBackThenStartOverTest(PostSaveLinkTappedAction.TAP_NO_ON_SAVE_UI,
                false);
    }

    /**
     * Tests scenarios when user taps a link in the custom description, taps back to return to the
     * activity with the Save UI, and tap the "No" button to dismiss it.
     *
     * <p>Then user starts a new session by forcing autofill.
     */
    @Test
    public final void testTapLink_tapBack_thenStartOverBySayingNoAndManualRequest()
            throws Exception {
        tapLinkThenTapBackThenStartOverTest(PostSaveLinkTappedAction.TAP_NO_ON_SAVE_UI, true);
    }

    /**
     * Tests scenarios when user taps a link in the custom description, taps back to return to the
     * activity with the Save UI, and the "Yes" button to save it.
     *
     * <p>Then user starts a new session by focusing in a field.
     */
    @Test
    public final void testTapLink_tapBack_thenStartOverBySayingYesAndFocus()
            throws Exception {
        tapLinkThenTapBackThenStartOverTest(PostSaveLinkTappedAction.TAP_YES_ON_SAVE_UI,
                false);
    }

    /**
     * Tests scenarios when user taps a link in the custom description, taps back to return to the
     * activity with the Save UI, and the "Yes" button to save it.
     *
     * <p>Then user starts a new session by forcing autofill.
     */
    @Test
    public final void testTapLink_tapBack_thenStartOverBySayingYesAndManualRequest()
            throws Exception {
        tapLinkThenTapBackThenStartOverTest(PostSaveLinkTappedAction.TAP_YES_ON_SAVE_UI, true);
    }

    protected abstract void tapLinkThenTapBackThenStartOverTest(
            PostSaveLinkTappedAction action, boolean manualRequest) throws Exception;

    /**
     * Tests scenarios when user taps a link in the custom description, then double-tap recents
     * to go back to the original activity:
     * the Save UI should have been canceled.
     */
    @Test
    public final void testTapLink_backToPreviousActivityByTappingRecents()
            throws Exception {
        saveUiCancelledAfterTappingLinkTest(PostSaveLinkTappedAction.TAP_RECENTS);
    }

    /**
     * Tests scenarios when user taps a link in the custom description, then re-launches the
     * original activity:
     * the Save UI should have been canceled.
     */
    @Test
    public final void testTapLink_backToPreviousActivityByLaunchingIt()
            throws Exception {
        saveUiCancelledAfterTappingLinkTest(PostSaveLinkTappedAction.LAUNCH_PREVIOUS_ACTIVITY);
    }

    /**
     * Tests scenarios when user taps a link in the custom description, then launches a 3rd
     * activity:
     * the Save UI should have been canceled.
     */
    @Test
    public final void testTapLink_launchNewActivityThenTapBack() throws Exception {
        saveUiCancelledAfterTappingLinkTest(PostSaveLinkTappedAction.LAUNCH_NEW_ACTIVITY);
    }

    @Test
    public final void testTapLink_launchTrampolineActivityThenTapBackAndStartNewSession()
            throws Exception {
        tapLinkLaunchTrampolineActivityThenTapBackAndStartNewSessionTest();
    }

    protected abstract void tapLinkLaunchTrampolineActivityThenTapBackAndStartNewSessionTest()
            throws Exception;

    enum PostSaveLinkTappedAction {
        TAP_BACK_BUTTON,
        ROTATE_THEN_TAP_BACK_BUTTON,
        TAP_RECENTS,
        FINISH_ACTIVITY,
        LAUNCH_NEW_ACTIVITY,
        LAUNCH_PREVIOUS_ACTIVITY,
        TOUCH_OUTSIDE,
        TAP_NO_ON_SAVE_UI,
        TAP_YES_ON_SAVE_UI
    }

    protected abstract void saveUiCancelledAfterTappingLinkTest(PostSaveLinkTappedAction type)
            throws Exception;

    protected final void startActivity(Class<?> clazz) {
        mContext.startActivity(new Intent(mContext, clazz));
    }

    protected final CustomDescription newCustomDescription(
            Class<? extends Activity> activityClass) {
        final Intent intent = new Intent(mContext, activityClass);
        intent.setFlags(Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS | Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        return newCustomDescription(intent);
    }

    protected final CustomDescription newCustomDescription(Intent intent) {
        final RemoteViews presentation = new RemoteViews(mPackageName,
                R.layout.custom_description_with_link);
        final PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, intent, 0);
        presentation.setOnClickPendingIntent(R.id.link, pendingIntent);
        return new CustomDescription.Builder(presentation).build();
    }

    protected final UiObject2 assertSaveUiWithLinkIsShown(int saveType) {
        // First make sure the UI is shown...
        final UiObject2 saveUi = sUiBot.assertSaveShowing(saveType);
        // Then make sure it does have the custom view with link on it...
        getLink(saveUi);
        return saveUi;
    }

    protected final UiObject2 getLink(final UiObject2 container) {
        final UiObject2 button = container.findObject(By.res(mPackageName, ID_LINK));
        assertThat(button).isNotNull();
        assertThat(button.getText()).isEqualTo("DON'T TAP ME!");
        return button;
    }

    protected final void tapSaveUiLink(UiObject2 saveUi) {
        getLink(saveUi).click();
    }
}
