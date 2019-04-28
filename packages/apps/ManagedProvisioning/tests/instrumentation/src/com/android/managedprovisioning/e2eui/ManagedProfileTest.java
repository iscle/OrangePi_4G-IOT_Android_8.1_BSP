/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.managedprovisioning.e2eui;

import android.content.pm.UserInfo;
import android.os.UserManager;
import android.support.test.espresso.ViewInteraction;
import android.support.test.espresso.base.DefaultFailureHandler;
import android.support.test.filters.LargeTest;
import android.support.test.rule.ActivityTestRule;
import android.test.AndroidTestCase;
import android.util.Log;

import android.view.View;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.TestInstrumentationRunner;
import com.android.managedprovisioning.preprovisioning.PreProvisioningActivity;
import org.hamcrest.Matcher;

import java.util.List;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.scrollTo;
import static android.support.test.espresso.matcher.ViewMatchers.withId;

@LargeTest
public class ManagedProfileTest extends AndroidTestCase {
    private static final String TAG = "ManagedProfileTest";

    private static final long TIMEOUT = 120L;

    public ActivityTestRule mActivityRule;
    private ProvisioningResultListener mResultListener;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mActivityRule = new ActivityTestRule<>(
                PreProvisioningActivity.class,
                true /* initialTouchMode */,
                false);  // launchActivity. False to set intent per method
        mResultListener = new ProvisioningResultListener(getContext());
        TestInstrumentationRunner.registerReplacedActivity(PreProvisioningActivity.class,
                (cl, className, intent) -> new TestPreProvisioningActivity(mResultListener));
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        TestInstrumentationRunner.unregisterReplacedActivity(PreProvisioningActivity.class);
        mResultListener.unregister();

        // Remove any managed profiles in case that
        removeAllManagedProfiles();
    }

    private void removeAllManagedProfiles() {
        UserManager um = getContext().getSystemService(UserManager.class);
        List<UserInfo> users = um.getUsers();
        for (UserInfo user : users) {
            if (user.isManagedProfile()) {
                int userId = user.getUserHandle().getIdentifier();
                um.removeUserEvenWhenDisallowed(userId);
                Log.e(TAG, "remove managed profile user: " + userId);
            }
        }
    }

    public void testManagedProfile() throws Exception {
        mActivityRule.launchActivity(ManagedProfileAdminReceiver.INTENT_PROVISION_MANAGED_PROFILE);

        mResultListener.register();

        // Retry the sequence of 2 actions 3 times to avoid flakiness of the test
        new EspressoClickRetryActions(3) {
            @Override
            public ViewInteraction newViewInteraction1() {
                return onView(withId(R.id.next_button));
            }
        }.run();

        if (mResultListener.await(TIMEOUT)) {
            assertTrue(mResultListener.getResult());
        } else {
            fail("timeout: " + TIMEOUT + " seconds");
        }
    }

    private abstract class EspressoClickRetryActions {
        private final int mRetries;
        private int i = 0;

        EspressoClickRetryActions(int retries) {
            mRetries = retries;
        }

        public abstract ViewInteraction newViewInteraction1();

        public void run() {
            i++;
            newViewInteraction1()
                    .withFailureHandler(this::handleFailure)
                    .perform(scrollTo(), click());
            Log.i(TAG, "newViewInteraction1 succeeds.");
        }

        private void handleFailure(Throwable e, Matcher<View> matcher) {
            Log.i(TAG, "espresso handleFailure count: " + i, e);
            if (i < mRetries) {
                run();
            } else {
                new DefaultFailureHandler(getContext()).handle(e, matcher);
            }
        }
    }
}

