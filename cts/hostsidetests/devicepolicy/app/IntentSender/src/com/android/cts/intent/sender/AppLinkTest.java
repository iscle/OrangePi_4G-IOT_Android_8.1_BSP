/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.cts.intent.sender;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.test.InstrumentationTestCase;

public class AppLinkTest extends InstrumentationTestCase {

    private static final String TAG = "AppLinkTest";

    private Context mContext;
    private IntentSenderActivity mActivity;

    private static final String EXTRA_IN_MANAGED_PROFILE = "extra_in_managed_profile";
    private static final String EXTRA_RECEIVER_CLASS = "extra_receiver_class";
    private static final String APP_LINK_ACTIVITY
            = "com.android.cts.intent.receiver.AppLinkActivity";
    private static final String BROWSER_ACTIVITY
            = "com.android.cts.intent.receiver.BrowserActivity";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getTargetContext();
        mActivity = launchActivity(mContext.getPackageName(), IntentSenderActivity.class, null);
    }

    @Override
    public void tearDown() throws Exception {
        mActivity.finish();
        super.tearDown();
    }

    public void testReceivedByAppLinkActivityInPrimary() throws Exception {
        checkHttpIntentResult(APP_LINK_ACTIVITY, false);
    }

    public void testReceivedByAppLinkActivityInManaged() throws Exception {
        checkHttpIntentResult(APP_LINK_ACTIVITY, true);
    }

    public void testReceivedByBrowserActivityInManaged() throws Exception {
        checkHttpIntentResult(BROWSER_ACTIVITY, true);
    }

    public void testTwoReceivers() {
        assertNumberOfReceivers(2);
    }

    public void testThreeReceivers() {
        assertNumberOfReceivers(3);
    }

    // Should not be called if there are several possible receivers to the intent
    // (see getHttpIntent)
    private void checkHttpIntentResult(String receiverClassName, boolean inManagedProfile)
            throws Exception {
        PackageManager pm = mContext.getPackageManager();

        Intent result = mActivity.getResult(getHttpIntent());
        assertNotNull(result);

        // If it is received in the other profile, we cannot check the class from the ResolveInfo
        // returned by queryIntentActivities. So we rely on the receiver telling us its class.
        assertEquals(receiverClassName, result.getStringExtra(EXTRA_RECEIVER_CLASS));
        assertTrue(result.hasExtra(EXTRA_IN_MANAGED_PROFILE));
        assertEquals(inManagedProfile, result.getBooleanExtra(EXTRA_IN_MANAGED_PROFILE, false));
    }

    private void assertNumberOfReceivers(int n) {
        PackageManager pm = mContext.getPackageManager();
        assertEquals(n, pm.queryIntentActivities(getHttpIntent(), /* flags = */ 0).size());
    }

    private Intent getHttpIntent() {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.addCategory(Intent.CATEGORY_BROWSABLE);
        i.setData(Uri.parse("http://com.android.cts.intent.receiver"));
        return i;
    }
}
