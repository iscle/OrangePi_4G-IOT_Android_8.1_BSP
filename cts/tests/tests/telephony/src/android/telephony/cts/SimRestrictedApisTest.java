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
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.test.AndroidTestCase;

public class SimRestrictedApisTest extends AndroidTestCase {
    private static final byte[] TEST_PDU = { 0, 0 };
    private TelephonyManager mTelephonyManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mTelephonyManager =
                (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
    }

    private boolean isSimCardPresent() {
        return mTelephonyManager.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE &&
                mTelephonyManager.getSimState() != TelephonyManager.SIM_STATE_ABSENT;
    }

    /**
     * Tests the SmsManager.injectSmsPdu() API. This makes a call to injectSmsPdu() API and expects
     * a SecurityException since the test apk is not signed by a certificate on the SIM.
     */
    public void testInjectSmsPdu() {
        try {
            if (isSimCardPresent()) {
                SmsManager.getDefault().injectSmsPdu(TEST_PDU, "3gpp", null);
                fail("Expected SecurityException. App doesn't have carrier privileges.");
            }
        } catch (SecurityException expected) {
        }
    }

    /**
     * Tests the TelephonyManager.setLine1NumberForDisplay() API. This makes a call to
     * setLine1NumberForDisplay() API and expects a SecurityException since the test apk is not
     * signed by a certificate on the SIM.
     */
    public void testSetLine1NumberForDisplay() {
        try {
            if (isSimCardPresent()) {
                TelephonyManager.getDefault().setLine1NumberForDisplay("", "");
                fail("Expected SecurityException. App doesn't have carrier privileges.");
            }
        } catch (SecurityException expected) {
        }
    }

    /**
     * Tests the TelephonyManager.setLine1NumberForDisplay(long, string, string) API. This makes a
     * call to setLine1NumberForDisplay() API and expects a SecurityException since the test apk is
     * not signed by the certificate on the SIM.
     */
    public void testSetLine1NumberForDisplay2() {
        try {
            if (isSimCardPresent()) {
                TelephonyManager.getDefault().setLine1NumberForDisplay(0, "", "");
                fail("Expected SecurityException. App doesn't have carrier privileges.");
            }
        } catch (SecurityException expected) {
        }
    }

    /**
     * Tests the TelephonyManager.iccOpenLogicalChannel() API. This makes a call to
     * iccOpenLogicalChannel() API and expects a SecurityException since the test apk is not signed
     * by certificate on the SIM.
     */
    public void testIccOpenLogicalChannel() {
        try {
            if (isSimCardPresent()) {
                TelephonyManager.getDefault().iccOpenLogicalChannel("");
                fail("Expected SecurityException. App doesn't have carrier privileges.");
            }
        } catch (SecurityException expected) {
        }
    }

    /**
     * Tests the TelephonyManager.iccCloseLogicalChannel() API. This makes a call to
     * iccCloseLogicalChannel() API and expects a SecurityException since the test apk is not signed
     * by certificate on the SIM.
     */
    public void testIccCloseLogicalChannel() {
        try {
            if (isSimCardPresent()) {
                TelephonyManager.getDefault().iccCloseLogicalChannel(0);
                fail("Expected SecurityException. App doesn't have carrier privileges.");
            }
        } catch (SecurityException expected) {
        }
    }

    /**
     * Tests the TelephonyManager.iccTransmitApduLogicalChannel() API. This makes a call to
     * iccTransmitApduLogicalChannel() API and expects a SecurityException since the test apk is not
     * signed by a certificate on the SIM.
     */
    public void testIccTransmitApduLogicalChannel() {
        try {
            if (isSimCardPresent()) {
                TelephonyManager.getDefault().iccTransmitApduLogicalChannel(0, 0, 0, 0, 0, 0, "");
                fail("Expected SecurityException. App doesn't have carrier privileges.");
            }
        } catch (SecurityException expected) {
        }
    }

    /**
     * Tests the TelephonyManager.iccTransmitApduBasicChannel() API. This makes a call to
     * iccTransmitApduBasicChannel() API and expects a SecurityException since the test apk is not
     * signed by a certificate on the SIM.
     */
    public void testIccTransmitApduBasicChannel() {
        try {
            if (isSimCardPresent()) {
                TelephonyManager.getDefault().iccTransmitApduBasicChannel(0, 0, 0, 0, 0, "");
                fail("Expected SecurityException. App doesn't have carrier privileges.");
            }
        } catch (SecurityException expected) {
        }
    }

    /**
     * Tests the TelephonyManager.sendEnvelopeWithStatus() API. This makes a call to
     * sendEnvelopeWithStatus() API and expects a SecurityException since the test apk is not signed
     * by certificate on the SIM.
     */
    public void testSendEnvelopeWithStatus() {
        try {
            if (isSimCardPresent()) {
                TelephonyManager.getDefault().sendEnvelopeWithStatus("");
                fail("Expected SecurityException. App doesn't have carrier privileges.");
            }
        } catch (SecurityException expected) {
        }
    }

    /**
     * Tests the TelephonyManager.nvReadItem() API. This makes a call to nvReadItem() API and
     * expects a SecurityException since the test apk is not signed by a certificate on the SIM.
     */
    public void testNvReadItem() {
        try {
            if (isSimCardPresent()) {
                TelephonyManager.getDefault().nvReadItem(0);
                fail("Expected SecurityException. App doesn't have carrier privileges.");
            }
        } catch (SecurityException expected) {
        }
    }

    /**
     * Tests the TelephonyManager.nvWriteItem() API. This makes a call to nvWriteItem() API and
     * expects a SecurityException since the test apk is not signed by a certificate on the SIM.
     */
    public void testNvWriteItem() {
        try {
            if (isSimCardPresent()) {
                TelephonyManager.getDefault().nvWriteItem(0, "");
                fail("Expected SecurityException. App doesn't have carrier privileges.");
            }
        } catch (SecurityException expected) {
        }
    }

    /**
     * Tests the TelephonyManager.nvWriteCdmaPrl() API. This makes a call to nvWriteCdmaPrl() API
     * and expects a SecurityException since the test apk is not signed by a certificate on the SIM.
     */
    public void testNvWriteCdmaPrl() {
        try {
            if (isSimCardPresent()) {
                TelephonyManager.getDefault().nvWriteCdmaPrl(null);
                fail("Expected SecurityException. App doesn't have carrier privileges.");
            }
        } catch (SecurityException expected) {
        }
    }

    /**
     * Tests the TelephonyManager.nvResetConfig() API. This makes a call to nvResetConfig() API and
     * expects a SecurityException since the test apk is not signed by a certificate on the SIM.
     */
    public void testNvResetConfig() {
        try {
            if (isSimCardPresent()) {
                TelephonyManager.getDefault().nvResetConfig(0);
                fail("Expected SecurityException. App doesn't have carrier privileges.");
            }
        } catch (SecurityException expected) {
        }
    }

    /**
     * Tests the TelephonyManager.getPreferredNetworkType() API. This makes a call to
     * getPreferredNetworkType() API and expects a SecurityException since the test apk is not
     * signed by certificate on the SIM.
     */
    public void testGetPreferredNetworkType() {
        try {
            if (isSimCardPresent()) {
                TelephonyManager.getDefault().getPreferredNetworkType(0);
                fail("Expected SecurityException. App doesn't have carrier privileges.");
            }
        } catch (SecurityException expected) {
        }
    }

    /**
     * Tests the TelephonyManager.setPreferredNetworkTypeToGlobal() API. This makes a call to
     * setPreferredNetworkTypeToGlobal() API and expects a SecurityException since the test apk is not
     * signed by certificate on the SIM.
     */
    public void testSetPreferredNetworkTypeToGlobal() {
        try {
            if (isSimCardPresent()) {
                TelephonyManager.getDefault().setPreferredNetworkTypeToGlobal();
                fail("Expected SecurityException. App doesn't have carrier privileges.");
            }
        } catch (SecurityException expected) {
        }
    }

    /**
     * Tests that the test apk doesn't have carrier previliges.
     */
    public void testHasCarrierPrivileges() {
        if (TelephonyManager.getDefault().hasCarrierPrivileges()) {
            fail("App unexpectedly has carrier privileges");
        }
    }

    /**
     * Tests the TelephonyManager.setOperatorBrandOverride() API. This makes a call to
     * setOperatorBrandOverride() API and expects a SecurityException since the test apk is not
     * signed by certificate on the SIM.
     */
    public void testSetOperatorBrandOverride() {
        try {
            if (isSimCardPresent()) {
                TelephonyManager.getDefault().setOperatorBrandOverride("");
                fail("Expected SecurityException. App doesn't have carrier privileges.");
            }
        } catch (SecurityException expected) {
        }
    }

    /**
     * Tests the TelephonyManager.getIccAuthentication() API. This makes a call to
     * getIccAuthentication() API and expects a SecurityException since the test apk is not
     * signed by certificate on the SIM.
     */
    public void testGetIccAuthentication() {
        try {
            if (isSimCardPresent()) {
                TelephonyManager.getDefault().getIccAuthentication(TelephonyManager.APPTYPE_USIM,
                        TelephonyManager.AUTHTYPE_EAP_AKA, "");
                fail("Expected SecurityException. App doesn't have carrier privileges.");
            }
        } catch (SecurityException expected) {
        }
    }
}
