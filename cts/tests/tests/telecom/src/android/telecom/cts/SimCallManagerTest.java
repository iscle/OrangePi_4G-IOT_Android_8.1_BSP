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

package android.telecom.cts;

import static android.telecom.cts.TestUtils.ACCOUNT_ID;
import static android.telecom.cts.TestUtils.ACCOUNT_LABEL;
import static android.telecom.cts.TestUtils.COMPONENT;
import static android.telecom.cts.TestUtils.PACKAGE;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.PersistableBundle;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;
import android.test.InstrumentationTestCase;
import android.text.TextUtils;

import java.util.Arrays;

/**
 * Verifies the behavior of TelecomManager.getSimCallManager() with respect to the default dialer
 */
public class SimCallManagerTest extends InstrumentationTestCase {
    public static final PhoneAccountHandle TEST_PHONE_ACCOUNT_HANDLE =
            new PhoneAccountHandle(new ComponentName(PACKAGE, COMPONENT), ACCOUNT_ID);

    public static final PhoneAccount TEST_SIM_CALL_MANAGER_ACCOUNT = PhoneAccount.builder(
            TEST_PHONE_ACCOUNT_HANDLE, ACCOUNT_LABEL)
            .setAddress(Uri.parse("tel:555-TEST"))
            .setSubscriptionAddress(Uri.parse("tel:555-TEST"))
            .setCapabilities(PhoneAccount.CAPABILITY_CONNECTION_MANAGER)
            .setHighlightColor(Color.RED)
            .setShortDescription(ACCOUNT_LABEL)
            .setSupportedUriSchemes(Arrays.asList("tel"))
            .build();

    private Context mContext;
    private TelecomManager mTelecomManager;
    private String mPreviousDefaultDialer = null;
    private String mSystemDialer = null;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getContext();

        if (!TestUtils.shouldTestTelecom(mContext)) {
            return;
        }
        mPreviousDefaultDialer = TestUtils.getDefaultDialer(getInstrumentation());
        // Reset the current dialer to the system dialer, to ensure that we start each test
        // without being the default dialer.
        mSystemDialer = TestUtils.getSystemDialer(getInstrumentation());
        if (!TextUtils.isEmpty(mSystemDialer)) {
            TestUtils.setDefaultDialer(getInstrumentation(), mSystemDialer);
        }
        mTelecomManager = (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
    }

    @Override
    protected void tearDown() throws Exception {
        if (!TextUtils.isEmpty(mPreviousDefaultDialer)) {
            // Restore the default dialer to whatever the default dialer was before the tests
            // were started. This may or may not be the system dialer.
            TestUtils.setDefaultDialer(getInstrumentation(), mPreviousDefaultDialer);
        }
        super.tearDown();
    }

    public void testGetSimCallManager() throws Exception {
        if (!TestUtils.shouldTestTelecom(mContext)) {
            return;
        }

        // By default, getSimCallManager should return either the carrier configured sim call
        // manager or the system dialer's sim call manager.
        assertEquals(mSystemDialer, mTelecomManager.getDefaultDialerPackage());
        assertNotSame(TEST_PHONE_ACCOUNT_HANDLE, mTelecomManager.getSimCallManager());

        ComponentName carrierConfigSimCallManager = null;
        CarrierConfigManager configManager = (CarrierConfigManager) mContext.getSystemService(
                Context.CARRIER_CONFIG_SERVICE);
        PersistableBundle configBundle = configManager.getConfig();
        if (configBundle != null) {
            final String componentString = configBundle.getString(
                    CarrierConfigManager.KEY_DEFAULT_SIM_CALL_MANAGER_STRING);
            if (!TextUtils.isEmpty(componentString)) {
                carrierConfigSimCallManager = ComponentName.unflattenFromString(componentString);
            }
        }

        // If the default dialer has not registered a sim call manager, getSimCallManager should
        // return the carrier configured sim call manager (which can be null).
        PhoneAccountHandle simCallManager = mTelecomManager.getSimCallManager();
        TestUtils.setDefaultDialer(getInstrumentation(), TestUtils.PACKAGE);
        assertEquals(TestUtils.PACKAGE, mTelecomManager.getDefaultDialerPackage());
        assertNotSame(TEST_PHONE_ACCOUNT_HANDLE, mTelecomManager.getSimCallManager());
        assertEquals("Sim call manager should be the carrier configured value if no default-dialer"
                + " provided value",
                carrierConfigSimCallManager,
                simCallManager == null ? null : simCallManager.getComponentName());

        // Once the default dialer has registered a sim call manager, getSimCallManager should
        // return the new sim call manager.
        mTelecomManager.registerPhoneAccount(TEST_SIM_CALL_MANAGER_ACCOUNT);
        assertEquals("Sim call manager should be default dialer's sim call manager if provided"
                + " by default dialer",
                TEST_PHONE_ACCOUNT_HANDLE,
                mTelecomManager.getSimCallManager());

        // If the dialer is no longer the default dialer, it is no longer the sim call manager.
        TestUtils.setDefaultDialer(getInstrumentation(), mSystemDialer);
        assertNotSame(TEST_PHONE_ACCOUNT_HANDLE, mTelecomManager.getSimCallManager());
    }
}
