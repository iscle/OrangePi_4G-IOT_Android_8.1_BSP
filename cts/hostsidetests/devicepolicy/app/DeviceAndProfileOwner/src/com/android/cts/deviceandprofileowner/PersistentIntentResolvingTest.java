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
package com.android.cts.deviceandprofileowner;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;

public class PersistentIntentResolvingTest extends BaseDeviceAdminTest {
    static final String EXAMPLE_ACTION = "com.android.cts.deviceandprofileowner.EXAMPLE_ACTION";

    private boolean mReceivedConfirmationFrom1;
    private boolean mReceivedConfirmationFrom2;
    private BroadcastReceiver mReceiver;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        IntentFilter filter = new IntentFilter();
        filter.addAction(ExampleIntentReceivingActivity1.CONFIRM_ACTION);
        filter.addAction(ExampleIntentReceivingActivity2.CONFIRM_ACTION);

        mReceiver = new ConfirmReceiver();
        mContext.registerReceiver(mReceiver, filter);

        synchronized(this) {
            mReceivedConfirmationFrom1 = false;
            mReceivedConfirmationFrom2 = false;
        }
    }

    @Override
    protected void tearDown() throws Exception {
        mDevicePolicyManager.clearPackagePersistentPreferredActivities(ADMIN_RECEIVER_COMPONENT,
                PACKAGE_NAME);
        mContext.unregisterReceiver(mReceiver);

        super.tearDown();
    }

    public void testNoPersistentPreferredActivityYieldsResolverActivity() {
        sendExampleIntent();
        SystemClock.sleep(5000);

        // Default behavior: intent results in resolver activity, since there are two potential
        // receivers. No intent is received.
        synchronized(this) {
            assertFalse(mReceivedConfirmationFrom1);
            assertFalse(mReceivedConfirmationFrom2);
        }
    }

    public void testAddPersistentPreferredActivityYieldsReceptionAtTarget() {
        addPersistentPreferredActivity();
        sendExampleIntent();
        SystemClock.sleep(5000);

        // Persistent preferred activity present: intent should be received by activity 2.
        synchronized(this) {
            assertFalse(mReceivedConfirmationFrom1);
            assertTrue(mReceivedConfirmationFrom2);
        }
    }

    public void testAddAndClearPersistentPreferredActivitiesYieldsResolverActivity() {
        addPersistentPreferredActivity();
        mDevicePolicyManager.clearPackagePersistentPreferredActivities(ADMIN_RECEIVER_COMPONENT,
                PACKAGE_NAME);

        sendExampleIntent();
        SystemClock.sleep(5000);

        // Default behavior: intent results in resolver activity, since there are two potential
        // receivers. No intent is received.
        synchronized(this) {
            assertFalse(mReceivedConfirmationFrom1);
            assertFalse(mReceivedConfirmationFrom2);
        }
    }

    public class ConfirmReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ExampleIntentReceivingActivity1.CONFIRM_ACTION)) {
                synchronized (PersistentIntentResolvingTest.this) {
                    mReceivedConfirmationFrom1 = true;
                }
            } else if (intent.getAction().equals(ExampleIntentReceivingActivity2
                            .CONFIRM_ACTION)) {
                synchronized (PersistentIntentResolvingTest.this) {
                    mReceivedConfirmationFrom2 = true;
                }
            }
        }
    }

    private void sendExampleIntent() {
        Intent exampleIntent = new Intent(EXAMPLE_ACTION);
        exampleIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(exampleIntent);
    }

    private void addPersistentPreferredActivity() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(EXAMPLE_ACTION);
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        ComponentName targetComponent = new ComponentName(PACKAGE_NAME,
                ExampleIntentReceivingActivity2.class.getName());
        mDevicePolicyManager.addPersistentPreferredActivity(ADMIN_RECEIVER_COMPONENT, filter,
                targetComponent);
    }
}
