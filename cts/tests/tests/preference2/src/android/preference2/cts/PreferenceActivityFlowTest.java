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
 * limitations under the License
 */

package android.preference2.cts;

import static android.preference.PreferenceActivity.EXTRA_NO_HEADERS;
import static android.preference.PreferenceActivity.EXTRA_SHOW_FRAGMENT;
import static android.preference.PreferenceActivity.EXTRA_SHOW_FRAGMENT_TITLE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.preference2.cts.R;
import android.util.Log;

import com.android.compatibility.common.util.BitmapUtils;

/**
 * This test suite covers {@link android.preference.PreferenceActivity} to ensure its correct
 * behavior in orientation and multi-window changes together with navigation between different
 * screens and going back in the history. It should also cover any possible transition between
 * single and multi pane modes. Some tests are designed to run only on large or small screen devices
 * and some can run both. These tests are run from {@link PreferenceActivityFlowLandscapeTest} and
 * {@link PreferenceActivityFlowPortraitTest} to ensure that both display configurations are
 * checked.
 */
public abstract class PreferenceActivityFlowTest {

    private static final String TAG = "PreferenceFlowTest";

    // Helper strings to ensure that some parts of preferences are visible or not.
    private static final String PREFS1_HEADER_TITLE = "Prefs 1";
    private static final String PREFS2_HEADER_TITLE = "Prefs 2";
    private static final String PREFS1_PANEL_TITLE = "Preferences panel 1";
    private static final String PREFS2_PANEL_TITLE = "Preferences panel 2";
    private static final String INNER_FRAGMENT_PREF_BUTTON = "Fragment preference";
    private static final String INNER_FRAGMENT_PREF_TITLE = "Inner fragment";
    private static final String LIST_PREF_TITLE = "List preference";
    private static final String LIST_PREF_OPTION = "alpha";

    private static final int INITIAL_TITLE_RES_ID = R.string.test_title;
    private static final int EXPECTED_HEADERS_COUNT = 3;

    TestUtils mTestUtils;
    protected PreferenceWithHeaders mActivity;
    private boolean mIsMultiPane;

    void switchHeadersInner() {
        launchActivity();
        if (shouldRunLargeDeviceTest()) {
            largeScreenSwitchHeadersInner();
        } else {
            smallScreenSwitchHeadersInner();
        }
    }

    /**
     * For: Large screen (multi-pane).
     * Scenario: Tests that tapping on header changes to its proper preference panel and that the
     * headers are still visible.
     */
    private void largeScreenSwitchHeadersInner() {
        assertTrue(shouldRunLargeDeviceTest());
        assertInitialState();

        tapOnPrefs2Header();

        // Headers and panel Prefs2 must be shown.
        assertHeadersShown();
        assertPanelPrefs1Hidden();
        assertPanelPrefs2Shown();

        tapOnPrefs1Header();

        // Headers and panel Prefs1 must be shown.
        assertHeadersShown();
        assertPanelPrefs1Shown();
        assertPanelPrefs2Hidden();
    }

    /**
     * For: Small screen (single-pane).
     * Scenario: Tests that tapping on header changes to its proper preference panel and that the
     * headers are hidden and pressing back button after that shows them again.
     */
    private void smallScreenSwitchHeadersInner() {
        assertTrue(shouldRunSmallDeviceTest());
        assertInitialState();

        tapOnPrefs2Header();

        // Only Prefs2 must be shown.
        assertHeadersHidden();
        assertPanelPrefs1Hidden();
        assertPanelPrefs2Shown();

        pressBack();

        tapOnPrefs1Header();

        // Only Prefs1 must be shown.
        assertHeadersHidden();
        assertPanelPrefs1Shown();
        assertPanelPrefs2Hidden();
    }

    /**
     * For: Small screen (single-pane).
     * Scenario: Tests that after navigating back to the headers list there will be no header
     * highlighted and that the title was properly restored..
     */
    void smallScreenNoHighlightInHeadersListInner() {
        launchActivity();
        if (!shouldRunSmallDeviceTest()) {
            return;
        }

        assertInitialState();

        CharSequence title = mActivity.getTitle();

        tapOnPrefs2Header();
        assertHeadersHidden();

        pressBack();
        assertHeadersShown();

        // Verify that no headers are focused.
        assertHeadersNotFocused();

        // Verify that the title was properly restored.
        assertEquals(title, mActivity.getTitle());

        // Verify that everthing restores back to initial state again.
        assertInitialState();
    }

    void backPressToExitInner() {
        launchActivity();
        if (shouldRunLargeDeviceTest()) {
            largeScreenBackPressToExitInner();
        } else {
            smallScreenBackPressToExitInner();
        }
    }

    /**
     * For: Small screen (single-pane).
     * Scenario: Tests that pressing back button twice after having preference panel opened will
     * exit the app when running single-pane.
     */
    private void smallScreenBackPressToExitInner() {
        assertTrue(shouldRunSmallDeviceTest());
        assertInitialState();

        tapOnPrefs2Header();

        // Only Prefs2 must be shown - covered by smallScreenSwitchHeadersTest

        pressBack();
        pressBack();

        // Now we should be out of the activity
        assertHeadersHidden();
        assertPanelPrefs1Hidden();
        assertPanelPrefs2Hidden();
    }

    /**
     * For: Large screen (multi-pane).
     * Scenario: Selects a header and then leaves the activity by pressing back button. Tests that
     * we don't transition to the previous header or list of header like in single-pane.
     */
    private void largeScreenBackPressToExitInner() {
        assertTrue(shouldRunLargeDeviceTest());
        assertInitialState();

        tapOnPrefs2Header();

        // Headers and panel Prefs2 must be shown - covered by largeScreenSwitchHeadersInner.

        pressBack();

        assertHeadersHidden();
    }

    void goToFragmentInner() {
        launchActivity();
        if (shouldRunLargeDeviceTest()) {
            largeScreenGoToFragmentInner();
        } else {
            smallScreenGoToFragmentInner();
        }
    }

    /**
     * For: Large screen (multi-pane).
     * Scenario: Navigates to inner fragment. Test that the fragment was opened correctly and
     * headers are still visible. Also tests that back press doesn't close the app but navigates
     * back from the fragment.
     */
    private void largeScreenGoToFragmentInner() {
        assertTrue(shouldRunLargeDeviceTest());
        assertInitialState();

        tapOnPrefs1Header();

        // Go to preferences inner fragment.
        mTestUtils.tapOnViewWithText(INNER_FRAGMENT_PREF_BUTTON);

        // Headers and inner fragment must be shown.
        assertHeadersShown();
        assertPanelPrefs1Hidden();
        assertInnerFragmentShown();

        pressBack();

        // Headers and panel Prefs1 must be shown.
        assertHeadersShown();
        assertPanelPrefs1Shown();
        assertPanelPrefs2Hidden();
        assertInnerFragmentHidden();
    }

    /**
     * For: Small screen (single-pane).
     * Scenario: Navigates to inner fragment. Tests that the fragment was opened correctly and
     * headers are hidden. Also tests that back press doesn't close the app but navigates back from
     * the fragment.
     */
    private void smallScreenGoToFragmentInner() {
        assertTrue(shouldRunSmallDeviceTest());
        assertInitialState();

        tapOnPrefs1Header();

        // Go to preferences inner fragment.
        mTestUtils.tapOnViewWithText(INNER_FRAGMENT_PREF_BUTTON);

        // Only inner fragment must be shown.
        assertHeadersHidden();
        assertPanelPrefs1Hidden();
        assertInnerFragmentShown();

        pressBack();

        // Prefs1 must be shown.
        assertHeadersHidden();
        assertPanelPrefs1Shown();
        assertPanelPrefs2Hidden();
        assertInnerFragmentHidden();
    }

    /**
     * For: Any screen (single or multi-pane).
     * Scenario: Tests that opening specific preference fragment directly via intent works properly.
     */
    void startWithFragmentInner() {
        launchActivityWithExtras(PreferenceWithHeaders.PrefsTwoFragment.class,
                false /* noHeaders */, -1 /* initialTitle */);

        assertInitialStateForFragment();
    }

    /**
     * For: Any screen (single or multi-pane).
     * Scenario: Tests that preference fragment opened directly survives recreation (via screenshot
     * tests).
     */
    void startWithFragmentAndRecreateInner() {
        launchActivityWithExtras(PreferenceWithHeaders.PrefsTwoFragment.class,
                false /* noHeaders */, -1 /* initialTitle */);

        assertInitialStateForFragment();

        // Take screenshot
        Bitmap before = mTestUtils.takeScreenshot();

        // Force recreate
        recreate();

        assertInitialStateForFragment();

        // Compare screenshots
        Bitmap after = mTestUtils.takeScreenshot();
        assertScreenshotsAreEqual(before, after);
    }

    /**
     * For: Any screen (single or multi-pane).
     * Scenario: Starts preference fragment directly with the given initial title and tests that
     * multi-pane does not show it and single-pane does.
     */
    void startWithFragmentAndInitTitleInner() {
        launchActivityWithExtras(PreferenceWithHeaders.PrefsTwoFragment.class,
                false /* noHeaders */, INITIAL_TITLE_RES_ID);

        assertInitialStateForFragment();

        if (mIsMultiPane) {
            String testTitle = mActivity.getResources().getString(INITIAL_TITLE_RES_ID);
            // Title should not be shown.
            assertTextHidden(testTitle);
        } else {
            // Title should be shown.
            assertTitleShown();
        }
    }

    /**
     * For: Large screen (multi-pane).
     * Scenario: Tests that initial title is displayed or hidden properly when transitioning in and
     * out of the multi-window mode.
     */
    void startWithFragmentAndInitTitleMultiWindowInner() {
        launchActivityWithExtras(PreferenceWithHeaders.PrefsTwoFragment.class,
                false /* noHeaders */, INITIAL_TITLE_RES_ID);
        if (!shouldRunLargeDeviceTest()) {
            return;
        }

        assertInitialStateForFragment();
        String testTitle = mActivity.getResources().getString(INITIAL_TITLE_RES_ID);

        // Title should not be shown (we are in multi-pane).
        assertFalse(mTestUtils.isTextShown(testTitle));

        mTestUtils.enterMultiWindow(mActivity);
        mTestUtils.getMultiWindowFocus(mActivity);

        // Title should be shown (we are in single-pane).
        assertTextShown(testTitle);

        mTestUtils.leaveMultiWindow(mActivity);

        // Title should not be shown (we are back in multi-pane).
        assertTextHidden(testTitle);
    }

    /**
     * For: Any screen (single or multi-pane).
     * Scenario: Tests that EXTRA_NO_HEADERS intent arg that prevents showing headers in multi-pane
     * is applied correctly.
     */
    void startWithFragmentNoHeadersInner() {
        launchActivityWithExtras(PreferenceWithHeaders.PrefsTwoFragment.class,
                true /* noHeaders */, -1 /* initialTitle */);

        assertInitialStateForFragment();
        // Only Prefs2 should be shown.
        assertHeadersHidden();
        assertPanelPrefs1Hidden();
        assertPanelPrefs2Shown();
    }

    /**
     * For: Any screen (single or multi-pane).
     * Scenario: Tests that EXTRA_NO_HEADERS intent arg that prevents showing headers in multi-pane
     * is applied correctly plus initial title is displayed.
     */
    void startWithFragmentNoHeadersButInitTitleInner() {
        launchActivityWithExtras(PreferenceWithHeaders.PrefsTwoFragment.class,
                true /* noHeaders */, INITIAL_TITLE_RES_ID);

        assertInitialStateForFragment();
        // Only Prefs2 should be shown.
        assertHeadersHidden();
        assertPanelPrefs1Hidden();
        assertPanelPrefs2Shown();

        assertTitleShown();
    }

    /**
     * For: Any screen (single or multi-pane).
     * Scenario: Tests that EXTRA_NO_HEADERS intent arg that prevents showing headers survives
     * correctly multi-window changes. Tested via screenshots.
     */
    void startWithFragmentNoHeadersMultiWindowTest() {
        launchActivityWithExtras(PreferenceWithHeaders.PrefsTwoFragment.class,
                true /* noHeaders */, -1 /* initialTitle */);

        assertInitialStateForFragment();

        // Workaround for some focus bug in the framework
        mTestUtils.tapOnViewWithText(PREFS2_PANEL_TITLE);

        // Take screenshot
        Bitmap before = mTestUtils.takeScreenshot();

        // Enter and leave multi-window.
        mTestUtils.enterMultiWindow(mActivity);
        mTestUtils.leaveMultiWindow(mActivity);

        assertInitialStateForFragment();

        // Compare screenshots
        Bitmap after = mTestUtils.takeScreenshot();
        assertScreenshotsAreEqual(before, after);
    }

    /**
     * For: Any screen (single or multi-pane).
     * Scenario: Tests that list preference opens correctly and that back press correctly closes it.
     */
    void listDialogTest()  {
        launchActivity();

        assertInitialState();
        if (!mIsMultiPane) {
            tapOnPrefs1Header();
        }

        mTestUtils.tapOnViewWithText(LIST_PREF_TITLE);

        mTestUtils.isTextShown(LIST_PREF_OPTION);

        pressBack();

        if (mIsMultiPane) {
            // Headers and Prefs1 should be shown.
            assertHeadersShown();
            assertPanelPrefs1Shown();
        } else {
            // Only Prefs1 should be shown.
            assertHeadersHidden();
            assertPanelPrefs1Shown();
        }
    }

    /**
     * For: Any screen (single or multi-pane).
     * Scenario: Tests that the PreferenceActivity properly restores its state after recreation.
     * Test done via screenshots.
     */
    void recreateTest() {
        launchActivity();

        assertInitialState();
        tapOnPrefs2Header();

        assertPanelPrefs2Shown();

        // Take screenshot
        Bitmap before = mTestUtils.takeScreenshot();

        recreate();

        assertPanelPrefs2Shown();

        // Compare screenshots
        Bitmap after = mTestUtils.takeScreenshot();
        assertScreenshotsAreEqual(before, after);
    }

    /**
     * For: Any screen (single or multi-pane).
     * Scenario: Tests that the PreferenceActivity properly restores its state after recreation
     * while an inner fragment is shown. Test done via screenshots.
     */
    void recreateInnerFragmentTest() {
        launchActivity();

        assertInitialState();

        if (!mIsMultiPane) {
            tapOnPrefs1Header();
        }

        // Go to preferences inner fragment.
        mTestUtils.tapOnViewWithText(INNER_FRAGMENT_PREF_BUTTON);

        // Only inner fragment must be shown.
        assertHeadersHidden();
        assertPanelPrefs1Hidden();
        assertInnerFragmentShown();

        // Take screenshot
        Bitmap before = mTestUtils.takeScreenshot();

        recreate();

        // Only inner fragment must be shown.
        assertHeadersHidden();
        assertPanelPrefs1Hidden();
        assertInnerFragmentShown();

        // Compare screenshots
        Bitmap after = mTestUtils.takeScreenshot();
        assertScreenshotsAreEqual(before, after);
    }

    /**
     * For: Any screen (single or multi-pane).
     * Scenario: Tests that the PreferenceActivity properly restores its state after going to
     * multi-window and back. Test done via screenshots.
     */
    void multiWindowInOutTest() {
        launchActivity();

        assertInitialState();
        // Tap on Prefs2 header.
        tapOnPrefs2Header();

        assertPanelPrefs2Shown();

        // Take screenshot
        Bitmap before = mTestUtils.takeScreenshot();

        // Enter and leave multi-window.
        mTestUtils.enterMultiWindow(mActivity);
        mTestUtils.leaveMultiWindow(mActivity);

        assertPanelPrefs2Shown();

        // Compare screenshots
        Bitmap after = mTestUtils.takeScreenshot();
        assertScreenshotsAreEqual(before, after);
    }

    /**
     * For: Any screen (single or multi-pane).
     * Scenario: Tests that the PreferenceActivity properly restores its state after going to
     * multi-window and back while an inner fragment is shown. Test done via screenshots.
     */
    void multiWindowInnerFragmentInOutTest() {
        launchActivity();

        assertInitialState();
        if (!mIsMultiPane) {
            tapOnPrefs1Header();
        }

        // Go to preferences inner fragment.
        mTestUtils.tapOnViewWithText(INNER_FRAGMENT_PREF_BUTTON);

        // We don't need to check that correct panel is displayed that is already covered by
        // smallScreenGoToFragmentInner and largeScreenGoToFragmentInner

        // Take screenshot
        Bitmap before = mTestUtils.takeScreenshot();

        // Enter and leave multi-window.
        mTestUtils.enterMultiWindow(mActivity);
        mTestUtils.leaveMultiWindow(mActivity);

        // Compare screenshots
        Bitmap after = mTestUtils.takeScreenshot();
        assertScreenshotsAreEqual(before, after);
    }

    /**
     * For: Large screen (single or multi-pane).
     * Scenario: Goes to single-pane by entering multi-window and tests that back press ends up with
     * a list of headers and nothing else. Then leaves multi-window back to single-pane and tests if
     * the proper default header was opened (screenshot test).
     */
    void multiWindowInitialHeaderOnBackTest() {
        launchActivity();
        if (!shouldRunLargeDeviceTest()) {
            return;
        }

        assertInitialState();

        Bitmap before = mTestUtils.takeScreenshot();

        // Enter multi-window.
        mTestUtils.enterMultiWindow(mActivity);

        // Get window focus (otherwise back press would close multi-window instead of firing to the
        // Activity.
        mTestUtils.getMultiWindowFocus(mActivity);

        pressBack();

        // Only headers should be shown (also checks that we have correct focus).
        assertHeadersShown();
        assertPanelPrefs1Hidden();
        assertPanelPrefs2Hidden();

        // Leave multi-window
        mTestUtils.leaveMultiWindow(mActivity);

        // Headers and Prefs1 should be shown.
        assertHeadersShown();
        assertPanelPrefs1Shown();
        assertPanelPrefs2Hidden();

        // Compare screenshots
        Bitmap after = mTestUtils.takeScreenshot();
        assertScreenshotsAreEqual(before, after);
    }

    /**
     * For: Large screen (multi-pane).
     * Scenario: Tests that history is preserved correctly while transitioning to multi-window.
     * Navigates to Prefs2 pane and then goes to single-pane mode via multi-window. Test that back
     * press navigates to the headers list. Then tests that restoring multi-pane by leaving
     * multi-window opens the same screen with which was the activity started before (screenshot
     * test).
     */
    void multiWindowHistoryPreserveTest() {
        launchActivity();
        if (!shouldRunLargeDeviceTest()) {
            return;
        }

        assertInitialState();
        Bitmap before = mTestUtils.takeScreenshot();

        tapOnPrefs2Header();

        // Enter multi-window.
        mTestUtils.enterMultiWindow(mActivity);
        mTestUtils.getMultiWindowFocus(mActivity);

        // Only Prefs2 should be shown (also checks that we have correct focus).
        assertHeadersHidden();
        assertPanelPrefs1Hidden();
        assertPanelPrefs2Shown();

        pressBack();

        // Only headers should be shown.
        assertHeadersShown();
        assertPanelPrefs1Hidden();
        assertPanelPrefs2Hidden();

        tapOnPrefs1Header();

        // Only Prefs1 should be shown.
        assertHeadersHidden();
        assertPanelPrefs1Shown();
        assertPanelPrefs2Hidden();

        // Leave multi-window
        mTestUtils.leaveMultiWindow(mActivity);

        // Headers and Prefs1 should be shown.
        assertHeadersShown();
        assertPanelPrefs1Shown();
        assertPanelPrefs2Hidden();

        // Compare screenshots
        Bitmap after = mTestUtils.takeScreenshot();
        assertScreenshotsAreEqual(before, after);
    }

    private void assertScreenshotsAreEqual(Bitmap before, Bitmap after) {
        assertTrue("Screenshots do not match!", BitmapUtils.compareBitmaps(before, after));
    }

    private void assertInitialState() {
        if (mIsMultiPane) {
            // Headers and panel Prefs1 must be shown.
            assertHeadersShown();
            runOnUiThread(() -> assertTrue(mActivity.hasHeaders()));
            assertPanelPrefs1Shown();
            assertPanelPrefs2Hidden();
        } else {
            // Headers must be shown and nothing else.
            assertHeadersShown();
            runOnUiThread(() -> assertTrue(mActivity.hasHeaders()));
            assertPanelPrefs1Hidden();
            assertPanelPrefs2Hidden();
        }

        assertHeadersAreLoaded();
    }

    private void assertInitialStateForFragment() {
        if (mIsMultiPane) {
            // Headers and Prefs2 should be shown.
            assertHeadersShown();
            runOnUiThread(() -> assertTrue(mActivity.hasHeaders()));
            assertPanelPrefs1Hidden();
            assertPanelPrefs2Shown();
        } else {
            // Only Prefs2 should be shown.
            assertHeadersHidden();
            runOnUiThread(() -> assertFalse(mActivity.hasHeaders()));
            assertPanelPrefs1Hidden();
            assertPanelPrefs2Shown();
        }


    }

    public boolean shouldRunLargeDeviceTest() {
        if (mActivity.onIsMultiPane()) {
            return true;
        }

        Log.d(TAG, "Skipping a large device test.");
        return false;
    }

    public boolean shouldRunSmallDeviceTest() {
        if (!mActivity.onIsMultiPane()) {
            return true;
        }

        Log.d(TAG, "Skipping a small device test.");
        return false;
    }

    private void tapOnPrefs1Header() {
        mTestUtils.tapOnViewWithText(PREFS1_HEADER_TITLE);
    }

    private void tapOnPrefs2Header() {
        mTestUtils.tapOnViewWithText(PREFS2_HEADER_TITLE);
    }

    private void assertHeadersAreLoaded() {
        runOnUiThread(() -> {
            assertEquals(EXPECTED_HEADERS_COUNT,
                    mActivity.loadedHeaders == null
                            ? 0
                            : mActivity.loadedHeaders.size());
        });
    }

    private void assertHeadersShown() {
        assertTextShown(PREFS1_HEADER_TITLE);
        assertTextShown(PREFS2_HEADER_TITLE);
    }

    private void assertHeadersNotFocused() {
        assertFalse(mTestUtils.isTextFocused(PREFS1_HEADER_TITLE));
        assertFalse(mTestUtils.isTextFocused(PREFS2_HEADER_TITLE));
    }

    private void assertHeadersHidden() {
        // We check '&' instead of each individual separately because these headers are also part
        // of individual preference panels breadcrumbs so it would fail for one.
        assertFalse(mTestUtils.isTextShown(PREFS1_HEADER_TITLE)
                && mTestUtils.isTextShown(PREFS2_HEADER_TITLE));
    }

    private void assertPanelPrefs1Shown() {
        assertTextShown(PREFS1_PANEL_TITLE);
    }

    private void assertPanelPrefs1Hidden() {
        assertTextHidden(PREFS1_PANEL_TITLE);
    }

    private void assertPanelPrefs2Shown() {
        assertTextShown(PREFS2_PANEL_TITLE);
    }

    private void assertPanelPrefs2Hidden() {
        assertTextHidden(PREFS2_PANEL_TITLE);
    }

    private void assertInnerFragmentShown() {
        assertTextShown(INNER_FRAGMENT_PREF_TITLE);
    }

    private void assertInnerFragmentHidden() {
        assertTextHidden(INNER_FRAGMENT_PREF_TITLE);
    }

    private void assertTextShown(String text) {
        assertTrue(mTestUtils.isTextShown(text));
    }

    private void assertTextHidden(String text) {
        assertTrue(mTestUtils.isTextHidden(text));
    }

    private void assertTitleShown() {
        if (!mTestUtils.isOnWatchUiMode()) {
            // On watch, activity title is not shown by default.
            String testTitle = mActivity.getResources().getString(INITIAL_TITLE_RES_ID);
            assertTextShown(testTitle);
        }
    }

    private void recreate() {
        runOnUiThread(() -> mActivity.recreate());
        SystemClock.sleep(1000);
        waitForIdle();
    }

    private void waitForIdle() {
        mTestUtils.device.waitForIdle();
    }

    private void pressBack() {
        mTestUtils.device.pressBack();
        waitForIdle();
    }

    private void launchActivity() {
        mActivity = launchActivity(null);
        mTestUtils.device.waitForIdle();
        runOnUiThread(() -> {
            mIsMultiPane = mActivity.isMultiPane();
        });
    }

    private void launchActivityWithExtras(Class extraFragment, boolean noHeaders,
            int initialTitle) {
        Intent intent = new Intent(Intent.ACTION_MAIN);

        if (extraFragment != null) {
            intent.putExtra(EXTRA_SHOW_FRAGMENT, extraFragment.getName());
        }
        if (noHeaders) {
            intent.putExtra(EXTRA_NO_HEADERS, true);
        }
        if (initialTitle != -1) {
            intent.putExtra(EXTRA_SHOW_FRAGMENT_TITLE, initialTitle);
        }

        mActivity = launchActivity(intent);
        mTestUtils.device.waitForIdle();
        runOnUiThread(() -> {
            mIsMultiPane = mActivity.isMultiPane();
        });
    }

    protected abstract PreferenceWithHeaders launchActivity(Intent intent);

    protected abstract void runOnUiThread(final Runnable runnable);
}
