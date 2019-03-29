/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.cts.isolatedsplitapp;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.model.Statement;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class SplitAppTest {
    private static final String PACKAGE = "com.android.cts.isolatedsplitapp";
    private static final ComponentName FEATURE_A_ACTIVITY =
            ComponentName.createRelative(PACKAGE, ".feature_a.FeatureAActivity");
    private static final ComponentName FEATURE_B_ACTIVITY =
            ComponentName.createRelative(PACKAGE, ".feature_b.FeatureBActivity");
    private static final ComponentName FEATURE_C_ACTIVITY =
            ComponentName.createRelative(PACKAGE, ".feature_c.FeatureCActivity");
    private static final String FEATURE_A_STRING = PACKAGE + ":string/feature_a_string";
    private static final String FEATURE_B_STRING = PACKAGE + ":string/feature_b_string";
    private static final String FEATURE_C_STRING = PACKAGE + ":string/feature_c_string";

    private static final Configuration PL = new Configuration();
    static {
        PL.setLocale(Locale.forLanguageTag("pl"));
    }

    @Rule
    public ActivityTestRule<BaseActivity> mBaseActivityRule =
            new ActivityTestRule<>(BaseActivity.class);

    // Do not launch this activity lazily. We use this rule to launch all feature Activities,
    // so we use #launchActivity() with the correct Intent.
    @Rule
    public ActivityTestRule<Activity> mFeatureActivityRule =
            new ActivityTestRule<>(Activity.class, true /*initialTouchMode*/,
                    false /*launchActivity*/);

    @Rule
    public AppContextTestRule mAppContextTestRule = new AppContextTestRule();

    @Test
    public void shouldLoadDefault() throws Exception {
        final Context context = mBaseActivityRule.getActivity();
        final Resources resources = context.getResources();
        assertThat(resources, notNullValue());

        assertThat(resources.getString(R.string.base_string), equalTo("Base String Default"));

        // The base does not depend on any splits so no splits should be accessible.
        assertActivitiesDoNotExist(context, FEATURE_A_ACTIVITY, FEATURE_B_ACTIVITY,
                FEATURE_C_ACTIVITY);
        assertResourcesDoNotExist(context, FEATURE_A_STRING, FEATURE_B_STRING, FEATURE_C_STRING);
    }

    @Test
    public void shouldLoadPolishLocale() throws Exception {
        final Context context = mBaseActivityRule.getActivity().createConfigurationContext(PL);
        final Resources resources = context.getResources();
        assertThat(resources, notNullValue());

        assertThat(resources.getString(R.string.base_string), equalTo("Base String Polish"));

        // The base does not depend on any splits so no splits should be accessible.
        assertActivitiesDoNotExist(context, FEATURE_A_ACTIVITY, FEATURE_B_ACTIVITY,
                FEATURE_C_ACTIVITY);
        assertResourcesDoNotExist(context, FEATURE_A_STRING, FEATURE_B_STRING, FEATURE_C_STRING);
    }

    @Test
    public void shouldLoadFeatureADefault() throws Exception {
        final Context context = mFeatureActivityRule.launchActivity(
                new Intent().setComponent(FEATURE_A_ACTIVITY));
        final Resources resources = context.getResources();
        assertThat(resources, notNullValue());

        assertThat(resources.getString(R.string.base_string), equalTo("Base String Default"));

        int resourceId = resources.getIdentifier(FEATURE_A_STRING, null, null);
        assertThat(resources.getString(resourceId), equalTo("Feature A String Default"));

        assertActivitiesDoNotExist(context, FEATURE_B_ACTIVITY, FEATURE_C_ACTIVITY);
        assertResourcesDoNotExist(context, FEATURE_B_STRING, FEATURE_C_STRING);
    }

    @Test
    public void shouldLoadFeatureAPolishLocale() throws Exception {
        final Context context = mFeatureActivityRule.launchActivity(
                new Intent().setComponent(FEATURE_A_ACTIVITY)).createConfigurationContext(PL);
        final Resources resources = context.getResources();
        assertThat(resources, notNullValue());

        assertThat(resources.getString(R.string.base_string), equalTo("Base String Polish"));

        int resourceId = resources.getIdentifier(FEATURE_A_STRING, null, null);
        assertThat(resources.getString(resourceId), equalTo("Feature A String Polish"));

        assertActivitiesDoNotExist(context, FEATURE_B_ACTIVITY, FEATURE_C_ACTIVITY);
        assertResourcesDoNotExist(context, FEATURE_B_STRING, FEATURE_C_STRING);
    }

    @Test
    public void shouldLoadFeatureAReceivers() throws Exception {
        final Context context = mAppContextTestRule.getContext();
        final ExtrasResultReceiver receiver = sendOrderedBroadcast(context);
        final Bundle results = receiver.get();
        assertThat(results.getString("base"), equalTo("Base String Default"));
        assertThat(results.getString("feature_a"), equalTo("Feature A String Default"));
        assertThat(results.getString("feature_b"), nullValue());
        assertThat(results.getString("feature_c"), nullValue());
    }

    @Test
    public void shouldLoadFeatureBDefault() throws Exception {
        // Feature B depends on A, so we expect both to be available.
        final Context context = mFeatureActivityRule.launchActivity(
                new Intent().setComponent(FEATURE_B_ACTIVITY));
        final Resources resources = context.getResources();
        assertThat(resources, notNullValue());

        assertThat(resources.getString(R.string.base_string), equalTo("Base String Default"));

        int resourceId = resources.getIdentifier(FEATURE_A_STRING, null, null);
        assertThat(resources.getString(resourceId), equalTo("Feature A String Default"));

        resourceId = resources.getIdentifier(FEATURE_B_STRING, null, null);
        assertThat(resources.getString(resourceId), equalTo("Feature B String Default"));

        assertActivitiesDoNotExist(context, FEATURE_C_ACTIVITY);
        assertResourcesDoNotExist(context, FEATURE_C_STRING);
    }

    @Test
    public void shouldLoadFeatureBPolishLocale() throws Exception {
        final Context context = mFeatureActivityRule.launchActivity(
                new Intent().setComponent(FEATURE_B_ACTIVITY)).createConfigurationContext(PL);
        final Resources resources = context.getResources();
        assertThat(resources, notNullValue());

        assertThat(resources.getString(R.string.base_string), equalTo("Base String Polish"));

        int resourceId = resources.getIdentifier(FEATURE_A_STRING, null, null);
        assertThat(resources.getString(resourceId), equalTo("Feature A String Polish"));

        resourceId = resources.getIdentifier(FEATURE_B_STRING, null, null);
        assertThat(resources.getString(resourceId), equalTo("Feature B String Polish"));

        assertActivitiesDoNotExist(context, FEATURE_C_ACTIVITY);
        assertResourcesDoNotExist(context, FEATURE_C_STRING);
    }

    @Test
    public void shouldLoadFeatureAAndBReceivers() throws Exception {
        final Context context = mAppContextTestRule.getContext();
        final ExtrasResultReceiver receiver = sendOrderedBroadcast(context);
        final Bundle results = receiver.get();
        assertThat(results.getString("base"), equalTo("Base String Default"));
        assertThat(results.getString("feature_a"), equalTo("Feature A String Default"));
        assertThat(results.getString("feature_b"), equalTo("Feature B String Default"));
        assertThat(results.getString("feature_c"), nullValue());
    }

    @Test
    public void shouldLoadFeatureCDefault() throws Exception {
        final Context context = mFeatureActivityRule.launchActivity(
                new Intent().setComponent(FEATURE_C_ACTIVITY));
        final Resources resources = context.getResources();
        assertThat(resources, notNullValue());

        assertThat(resources.getString(R.string.base_string), equalTo("Base String Default"));

        int resourceId = resources.getIdentifier(FEATURE_C_STRING, null, null);
        assertThat(resources.getString(resourceId), equalTo("Feature C String Default"));

        assertActivitiesDoNotExist(context, FEATURE_A_ACTIVITY, FEATURE_B_ACTIVITY);
        assertResourcesDoNotExist(context, FEATURE_A_STRING, FEATURE_B_STRING);
    }

    @Test
    public void shouldLoadFeatureCPolishLocale() throws Exception {
        final Context context = mFeatureActivityRule.launchActivity(
                new Intent().setComponent(FEATURE_C_ACTIVITY)).createConfigurationContext(PL);
        final Resources resources = context.getResources();
        assertThat(resources, notNullValue());

        assertThat(resources.getString(R.string.base_string), equalTo("Base String Polish"));

        int resourceId = resources.getIdentifier(FEATURE_C_STRING, null, null);
        assertThat(resources.getString(resourceId), equalTo("Feature C String Polish"));

        assertActivitiesDoNotExist(context, FEATURE_A_ACTIVITY, FEATURE_B_ACTIVITY);
        assertResourcesDoNotExist(context, FEATURE_A_STRING, FEATURE_B_STRING);
    }

    @Test
    public void shouldLoadFeatureAAndBAndCReceivers() throws Exception {
        final Context context = mAppContextTestRule.getContext();
        final ExtrasResultReceiver receiver = sendOrderedBroadcast(context);
        final Bundle results = receiver.get();
        assertThat(results.getString("base"), equalTo("Base String Default"));
        assertThat(results.getString("feature_a"), equalTo("Feature A String Default"));
        assertThat(results.getString("feature_b"), equalTo("Feature B String Default"));
        assertThat(results.getString("feature_c"), equalTo("Feature C String Default"));
    }

    private static void assertActivitiesDoNotExist(Context context, ComponentName... activities) {
        for (ComponentName activity : activities) {
            try {
                Class.forName(activity.getClassName(), true, context.getClassLoader());
                fail("Class " + activity.getClassName() + " is accessible");
            } catch (ClassNotFoundException e) {
                // Pass.
            }
        }
    }

    private static void assertResourcesDoNotExist(Context context, String... resourceNames) {
        final Resources resources = context.getResources();
        for (String resourceName : resourceNames) {
            final int resid = resources.getIdentifier(resourceName, null, null);
            if (resid != 0) {
                fail("Found resource '" + resourceName + "' with ID " + Integer.toHexString(resid));
            }
        }
    }

    private static class ExtrasResultReceiver extends BroadcastReceiver {
        private final CompletableFuture<Bundle> mResult = new CompletableFuture<>();

        @Override
        public void onReceive(Context context, Intent intent) {
            mResult.complete(getResultExtras(true));
        }

        public Bundle get() throws Exception {
            return mResult.get(5000, TimeUnit.SECONDS);
        }
    }

    private static ExtrasResultReceiver sendOrderedBroadcast(Context context) {
        final ExtrasResultReceiver resultReceiver = new ExtrasResultReceiver();
        context.sendOrderedBroadcast(new Intent(PACKAGE + ".ACTION").setPackage(PACKAGE), null,
                resultReceiver, null, 0, null, null);
        return resultReceiver;
    }

    private static class AppContextTestRule implements TestRule {
        private Context mContext;

        @Override
        public Statement apply(final Statement base, Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
                    base.evaluate();
                }
            };
        }

        public Context getContext() {
            return mContext;
        }
    }
}
