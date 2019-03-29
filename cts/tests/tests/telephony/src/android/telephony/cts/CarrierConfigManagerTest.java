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

package android.telephony.cts;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.test.AndroidTestCase;

public class CarrierConfigManagerTest extends AndroidTestCase {
    private CarrierConfigManager mConfigManager;
    private TelephonyManager mTelephonyManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mTelephonyManager = (TelephonyManager)
                getContext().getSystemService(Context.TELEPHONY_SERVICE);
        mConfigManager = (CarrierConfigManager)
                getContext().getSystemService(Context.CARRIER_CONFIG_SERVICE);
    }

    /**
     * Checks whether the telephony stack should be running on this device.
     *
     * Note: "Telephony" means only SMS/MMS and voice calls in some contexts, but we also care if
     * the device supports cellular data.
     */
    private boolean hasTelephony() {
        ConnectivityManager mgr =
                (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        return mgr.isNetworkSupported(ConnectivityManager.TYPE_MOBILE);
    }

    private boolean isSimCardPresent() {
        return mTelephonyManager.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE &&
                mTelephonyManager.getSimState() != TelephonyManager.SIM_STATE_ABSENT;
    }

    private void checkConfig(PersistableBundle config) {
        if (config == null) {
            assertFalse("Config should only be null when telephony is not running.", hasTelephony());
            return;
        }
        assertNotNull("CarrierConfigManager should not return null config", config);
        if (!isSimCardPresent()) {
            // Static default in CarrierConfigManager will be returned when no sim card present.
            assertEquals("Config doesn't match static default.",
                    config.getBoolean(CarrierConfigManager.KEY_ADDITIONAL_CALL_SETTING_BOOL), true);

            assertEquals("KEY_VVM_DESTINATION_NUMBER_STRING doesn't match static default.",
                config.getString(CarrierConfigManager.KEY_VVM_DESTINATION_NUMBER_STRING), "");
            assertEquals("KEY_VVM_PORT_NUMBER_INT doesn't match static default.",
                config.getInt(CarrierConfigManager.KEY_VVM_PORT_NUMBER_INT), 0);
            assertEquals("KEY_VVM_TYPE_STRING doesn't match static default.",
                config.getString(CarrierConfigManager.KEY_VVM_TYPE_STRING), "");
            assertEquals("KEY_VVM_CELLULAR_DATA_REQUIRED_BOOLEAN doesn't match static default.",
                config.getBoolean(CarrierConfigManager.KEY_VVM_CELLULAR_DATA_REQUIRED_BOOL),
                false);
            assertEquals("KEY_VVM_PREFETCH_BOOLEAN doesn't match static default.",
                config.getBoolean(CarrierConfigManager.KEY_VVM_PREFETCH_BOOL), true);
            assertEquals("KEY_CARRIER_VVM_PACKAGE_NAME_STRING doesn't match static default.",
                config.getString(CarrierConfigManager.KEY_CARRIER_VVM_PACKAGE_NAME_STRING), "");
        }
    }

    public void testGetConfig() {
        PersistableBundle config = mConfigManager.getConfig();
        checkConfig(config);
    }

    public void testGetConfigForSubId() {
        PersistableBundle config =
                mConfigManager.getConfigForSubId(SubscriptionManager.getDefaultSubscriptionId());
        checkConfig(config);
    }

    /**
     * Tests the CarrierConfigManager.notifyConfigChangedForSubId() API. This makes a call to
     * notifyConfigChangedForSubId() API and expects a SecurityException since the test apk is not signed
     * by certificate on the SIM.
     */
    public void testNotifyConfigChangedForSubId() {
        try {
            if (isSimCardPresent()) {
                mConfigManager.notifyConfigChangedForSubId(
                        SubscriptionManager.getDefaultSubscriptionId());
                fail("Expected SecurityException. App doesn't have carrier privileges.");
            }
        } catch (SecurityException expected) {
        }
    }

}
