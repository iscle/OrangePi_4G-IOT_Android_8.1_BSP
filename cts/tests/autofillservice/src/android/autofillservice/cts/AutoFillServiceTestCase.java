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

import static android.autofillservice.cts.Helper.getContext;
import static android.autofillservice.cts.Helper.getLoggingLevel;
import static android.autofillservice.cts.Helper.hasAutofillFeature;
import static android.autofillservice.cts.Helper.runShellCommand;
import static android.autofillservice.cts.Helper.setLoggingLevel;
import static android.autofillservice.cts.InstrumentedAutoFillService.SERVICE_NAME;
import static android.provider.Settings.Secure.AUTOFILL_SERVICE;

import android.autofillservice.cts.InstrumentedAutoFillService.Replier;
import android.content.Context;
import android.content.pm.PackageManager;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;
import android.widget.RemoteViews;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * Base class for all other tests.
 */
@RunWith(AndroidJUnit4.class)
abstract class AutoFillServiceTestCase {
    private static final String TAG = "AutoFillServiceTestCase";

    protected static UiBot sUiBot;

    protected static final Replier sReplier = InstrumentedAutoFillService.getReplier();

    @Rule
    public final RetryRule mRetryRule = new RetryRule(2);

    @Rule
    public final AutofillLoggingTestRule mLoggingRule = new AutofillLoggingTestRule(TAG);

    @Rule
    public final RequiredFeatureRule mRequiredFeatureRule =
            new RequiredFeatureRule(PackageManager.FEATURE_AUTOFILL);

    protected final Context mContext;
    protected final String mPackageName;
    /**
     * Stores the previous logging level so it's restored after the test.
     */
    private String mLoggingLevel;

    protected AutoFillServiceTestCase() {
        mContext = InstrumentationRegistry.getTargetContext();
        mPackageName = mContext.getPackageName();
    }

    @BeforeClass
    public static void prepareScreen() {
        if (!hasAutofillFeature()) return;

        // Unlock screen.
        runShellCommand("input keyevent KEYCODE_WAKEUP");

        // Collapse notifications.
        runShellCommand("cmd statusbar collapse");
    }

    @BeforeClass
    public static void setUiBot() throws Exception {
        if (!hasAutofillFeature()) return;

        sUiBot = new UiBot(InstrumentationRegistry.getInstrumentation());
    }

    @AfterClass
    public static void resetSettings() {
        if (!hasAutofillFeature()) return;

        // Clean up only - no need to call disableService() because it doesn't need to fail if
        // it's not reset.
        runShellCommand("settings delete secure %s", AUTOFILL_SERVICE);
    }

    @Before
    public void reset() {
        sReplier.reset();
    }

    @Before
    public void setVerboseLogging() {
        try {
            mLoggingLevel = getLoggingLevel();
        } catch (Exception e) {
            Log.w(TAG, "Could not get previous logging level: " + e);
            mLoggingLevel = "debug";
        }
        try {
            setLoggingLevel("verbose");
        } catch (Exception e) {
            Log.w(TAG, "Could not change logging level to verbose: " + e);
        }
    }

    @After
    public void resetVerboseLogging() {
        try {
            setLoggingLevel(mLoggingLevel);
        } catch (Exception e) {
            Log.w(TAG, "Could not restore logging level to " + mLoggingLevel + ": " + e);
        }
    }

    // TODO: we shouldn't throw exceptions on @After / @AfterClass because if the test failed, these
    // exceptions would mask the real cause. A better approach might be using a @Rule or some other
    // visitor pattern.
    @After
    public void assertNothingIsPending() throws Throwable {
        final MultipleExceptionsCatcher catcher = new MultipleExceptionsCatcher()
            .run(() -> sReplier.assertNumberUnhandledFillRequests(0))
            .run(() -> sReplier.assertNumberUnhandledSaveRequests(0));

        final List<Exception> replierExceptions = sReplier.getExceptions();
        if (replierExceptions != null) {
            for (Exception e : replierExceptions) {
                catcher.add(e);
            }
        }
        catcher.throwIfAny();
    }

    @After
    public void ignoreFurtherRequests() {
        InstrumentedAutoFillService.setIgnoreUnexpectedRequests(true);
    }

    /**
     * Enables the {@link InstrumentedAutoFillService} for autofill for the current user.
     */
    protected void enableService() {
        Helper.enableAutofillService(getContext(), SERVICE_NAME);
        InstrumentedAutoFillService.setIgnoreUnexpectedRequests(false);
    }

    /**
     * Disables the {@link InstrumentedAutoFillService} for autofill for the current user.
     */
    protected void disableService() {
        if (!hasAutofillFeature()) return;

        Helper.disableAutofillService(getContext(), SERVICE_NAME);
        InstrumentedAutoFillService.setIgnoreUnexpectedRequests(true);
    }

    /**
     * Asserts that the {@link InstrumentedAutoFillService} is enabled for the default user.
     */
    protected void assertServiceEnabled() {
        Helper.assertAutofillServiceStatus(SERVICE_NAME, true);
    }

    /**
     * Asserts that the {@link InstrumentedAutoFillService} is disabled for the default user.
     */
    protected void assertServiceDisabled() {
        Helper.assertAutofillServiceStatus(SERVICE_NAME, false);
    }

    protected RemoteViews createPresentation(String message) {
        final RemoteViews presentation = new RemoteViews(getContext()
                .getPackageName(), R.layout.list_item);
        presentation.setTextViewText(R.id.text1, message);
        return presentation;
    }
}
