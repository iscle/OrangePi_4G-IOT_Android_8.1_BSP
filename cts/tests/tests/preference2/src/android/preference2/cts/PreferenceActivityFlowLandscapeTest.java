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

import android.content.Intent;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Landscape tests setup for {@link PreferenceActivityFlowTest}
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class PreferenceActivityFlowLandscapeTest extends PreferenceActivityFlowTest {

    @Rule
    public ActivityTestRule<PreferenceWithHeadersLandscape> mActivityRule =
            new ActivityTestRule<>(PreferenceWithHeadersLandscape.class, false, false);

    @Before
    public void setup() {
        mTestUtils = new TestUtils();
    }

    /**
     * Landscape setup of {@link #switchHeadersInner}.
     */
    @Test
    public void switchHeadersLandscapeTest() {
        switchHeadersInner();
    }

    /**
     * Landscape setup of {@link #smallScreenNoHighlightInHeadersListInner}.
     */
    @Test
    public void smallScreenNoHighlightInHeadersListTest() {
        smallScreenNoHighlightInHeadersListInner();
    }

    /**
     * Landscape setup of {@link #backPressToExitInner}.
     */
    @Test
    public void backPressToExitLandscapeTest() {
        backPressToExitInner();
    }

    /**
     * Landscape setup of {@link #goToFragmentInner}.
     */
    @Test
    public void goToFragmentLandscapeTest() {
        goToFragmentInner();
    }

    /**
     * Landscape setup of {@link #startWithFragmentInner}.
     */
    @Test
    public void startWithFragmentLandscapeTest() {
        startWithFragmentInner();
    }

    /**
     * Landscape setup of {@link #startWithFragmentAndRecreateInner}.
     */
    @Test
    public void startWithFragmentAndRecreateLandscapeTest() {
        startWithFragmentAndRecreateInner();
    }


    /**
     * Landscape setup of {@link #startWithFragmentAndInitTitleInner}.
     */
    @Test
    public void startWithFragmentAndInitTitleLandscapeTest() {
        startWithFragmentAndInitTitleInner();
    }

    /**
     * Landscape setup of {@link #startWithFragmentAndInitTitleMultiWindowInner}.
     */
    @Test
    public void startWithFragmentAndInitTitleMultiWindowLandscapeTest() {
        startWithFragmentAndInitTitleMultiWindowInner();
    }

    /**
     * Landscape setup of {@link #startWithFragmentNoHeadersInner}.
     */
    @Test
    public void startWithFragmentNoHeadersLandscapeTest() {
        startWithFragmentNoHeadersInner();
    }

    /**
     * Landscape setup of {@link #startWithFragmentNoHeadersButInitTitleInner}.
     */
    @Test
    public void startWithFragmentNoHeadersButInitTitleLandscapeTest() {
        startWithFragmentNoHeadersButInitTitleInner();
    }

    /**
     * Landscape setup of {@link #startWithFragmentNoHeadersMultiWindowTest}.
     */
    @Test
    public void startWithFragmentNoHeadersMultiWindowLandscapeTest() {
        startWithFragmentNoHeadersMultiWindowTest();
    }

    /**
     * Landscape setup of {@link #listDialogTest}.
     */
    @Test
    public void listDialogLandscapeTest() {
        listDialogTest();
    }

    /**
     * Landscape setup of {@link #recreateTest}.
     */
    @Test
    public void recreateLandscapeTest() {
        recreateTest();
    }

    /**
     * Landscape setup of {@link #recreateInnerFragmentTest}.
     */
    @Test
    public void recreateInnerFragmentLandscapeTest() {
        recreateInnerFragmentTest();
    }

    /**
     * Landscape setup of {@link #multiWindowInOutTest}.
     */
    @Test
    public void multiWindowInOutLandscapeTest() {
        multiWindowInOutTest();
    }

    /**
     * Landscape setup of {@link #multiWindowInnerFragmentInOutTest}.
     */
    @Test
    public void multiWindowInnerFragmentInOutLandscapeTest() {
        multiWindowInnerFragmentInOutTest();
    }

    /**
     * Landscape setup of {@link #multiWindowInitialHeaderOnBackTest}.
     */
    @Test
    public void multiWindowInitialHeaderOnBackLandscapeTest() {
        multiWindowInitialHeaderOnBackTest();
    }

    /**
     * Landscape setup of {@link #multiWindowHistoryPreserveTest}.
     */
    @Test
    public void multiWindowHistoryPreserveLandscapeTest() {
        multiWindowHistoryPreserveTest();
    }

    @Override
    protected PreferenceWithHeaders launchActivity(Intent intent) {
        if (intent != null) {
            intent.setClass(InstrumentationRegistry.getTargetContext(),
                    PreferenceWithHeadersLandscape.class);
        }
        return mActivityRule.launchActivity(intent);
    }

    @Override
    protected void runOnUiThread(final Runnable runnable) {
        try {
            mActivityRule.runOnUiThread(() -> {
                runnable.run();
            });
        } catch (Throwable ex) {
            throw new RuntimeException("Failure on the UI thread", ex);
        }
    }

}
