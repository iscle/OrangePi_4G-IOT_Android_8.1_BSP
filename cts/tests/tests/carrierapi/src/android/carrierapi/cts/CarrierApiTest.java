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

package android.carrierapi.cts;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.test.AndroidTestCase;
import android.util.Log;

import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.uicc.IccUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class CarrierApiTest extends AndroidTestCase {
    private static final String TAG = "CarrierApiTest";
    private TelephonyManager mTelephonyManager;
    private PackageManager mPackageManager;
    private boolean hasCellular;
    private String selfPackageName;
    private String selfCertHash;

    private static final String FiDevCert = "24EB92CBB156B280FA4E1429A6ECEEB6E5C1BFE4";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mTelephonyManager = (TelephonyManager)
                getContext().getSystemService(Context.TELEPHONY_SERVICE);
        mPackageManager = getContext().getPackageManager();
        selfPackageName = getContext().getPackageName();
        selfCertHash = getCertHash(selfPackageName);
        hasCellular = hasCellular();
        if (!hasCellular) {
            Log.e(TAG, "No cellular support, all tests will be skipped.");
        }
    }

    /**
     * Checks whether the cellular stack should be running on this device.
     */
    private boolean hasCellular() {
        ConnectivityManager mgr =
                (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        return mgr.isNetworkSupported(ConnectivityManager.TYPE_MOBILE) &&
               mTelephonyManager.isVoiceCapable();
    }

    private boolean isSimCardPresent() {
        return mTelephonyManager.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE &&
                mTelephonyManager.getSimState() != TelephonyManager.SIM_STATE_ABSENT;
    }

    private String getCertHash(String pkgName) {
        try {
            PackageInfo pInfo = mPackageManager.getPackageInfo(pkgName,
                    PackageManager.GET_SIGNATURES | PackageManager.GET_DISABLED_UNTIL_USED_COMPONENTS);
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            return IccUtils.bytesToHexString(md.digest(pInfo.signatures[0].toByteArray()));
        } catch (PackageManager.NameNotFoundException ex) {
            Log.e(TAG, pkgName + " not found", ex);
        } catch (NoSuchAlgorithmException ex) {
            Log.e(TAG, "Algorithm SHA1 is not found.");
        }
        return "";
    }

    private void failMessage() {
        if (FiDevCert.equalsIgnoreCase(selfCertHash)) {
            fail("This test requires a Project Fi SIM card.");
        } else {
            fail("This test requires a SIM card with carrier privilege rule on it.\n" +
                 "Cert hash: " + selfCertHash + "\n" +
                 "Visit https://source.android.com/devices/tech/config/uicc.html");
        }
    }

    public void testSimCardPresent() {
        if (!hasCellular) return;
        assertTrue("This test requires SIM card.", isSimCardPresent());
    }

    public void testHasCarrierPrivileges() {
        if (!hasCellular) return;
        if (!mTelephonyManager.hasCarrierPrivileges()) {
            failMessage();
        }
    }

    public void testGetIccAuthentication() {
        // EAP-SIM rand is 16 bytes.
        String base64Challenge = "ECcTqwuo6OfY8ddFRboD9WM=";
        String base64Challenge2 = "EMNxjsFrPCpm+KcgCmQGnwQ=";
        if (!hasCellular) return;
        try {
            assertNull("getIccAuthentication should return null for empty data.",
                    mTelephonyManager.getIccAuthentication(TelephonyManager.APPTYPE_USIM,
                    TelephonyManager.AUTHTYPE_EAP_AKA, ""));
            String response = mTelephonyManager.getIccAuthentication(TelephonyManager.APPTYPE_USIM,
                    TelephonyManager.AUTHTYPE_EAP_SIM, base64Challenge);
            assertTrue("Response to EAP-SIM Challenge must not be Null.", response != null);
            // response is base64 encoded. After decoding, the value should be:
            // 1 length byte + SRES(4 bytes) + 1 length byte + Kc(8 bytes)
            byte[] result = android.util.Base64.decode(response, android.util.Base64.DEFAULT);
            assertTrue("Result length must be 14 bytes.", 14 == result.length);
            String response2 = mTelephonyManager.getIccAuthentication(TelephonyManager.APPTYPE_USIM,
                    TelephonyManager.AUTHTYPE_EAP_SIM, base64Challenge2);
            assertTrue("Two responses must be different.", !response.equals(response2));
        } catch (SecurityException e) {
            failMessage();
        }
    }

    public void testSendDialerSpecialCode() {
        if (!hasCellular) return;
        try {
            IntentReceiver intentReceiver = new IntentReceiver();
            final IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(TelephonyIntents.SECRET_CODE_ACTION);
            intentFilter.addDataScheme("android_secret_code");
            getContext().registerReceiver(intentReceiver, intentFilter);

            mTelephonyManager.sendDialerSpecialCode("4636");
            assertTrue("Did not receive expected Intent: " + TelephonyIntents.SECRET_CODE_ACTION,
                    intentReceiver.waitForReceive());
        } catch (SecurityException e) {
            failMessage();
        } catch (InterruptedException e) {
            Log.d(TAG, "Broadcast receiver wait was interrupted.");
        }
    }

    private static class IntentReceiver extends BroadcastReceiver {
        private final CountDownLatch mReceiveLatch = new CountDownLatch(1);

        @Override
        public void onReceive(Context context, Intent intent) {
            mReceiveLatch.countDown();
        }

        public boolean waitForReceive() throws InterruptedException {
            return mReceiveLatch.await(30, TimeUnit.SECONDS);
        }
    }
}
