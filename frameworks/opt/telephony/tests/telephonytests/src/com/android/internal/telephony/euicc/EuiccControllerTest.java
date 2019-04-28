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
package com.android.internal.telephony.euicc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.annotation.Nullable;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Parcelable;
import android.os.RemoteException;
import android.provider.Settings;
import android.service.euicc.EuiccService;
import android.service.euicc.GetDefaultDownloadableSubscriptionListResult;
import android.service.euicc.GetDownloadableSubscriptionMetadataResult;
import android.support.test.runner.AndroidJUnit4;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.UiccAccessRule;
import android.telephony.euicc.DownloadableSubscription;
import android.telephony.euicc.EuiccInfo;
import android.telephony.euicc.EuiccManager;

import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.mockito.stubbing.Stubber;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public class EuiccControllerTest extends TelephonyTest {
    private static final DownloadableSubscription SUBSCRIPTION =
            DownloadableSubscription.forActivationCode("abcde");

    private static final String PACKAGE_NAME = "test.package";
    private static final String CARRIER_NAME = "test name";
    private static final byte[] SIGNATURE_BYTES = new byte[] {1, 2, 3, 4, 5};

    private static final UiccAccessRule ACCESS_RULE;
    static {
        try {
            ACCESS_RULE = new UiccAccessRule(
                    MessageDigest.getInstance("SHA-256").digest(SIGNATURE_BYTES),
                    PACKAGE_NAME,
                    0);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 must exist");
        }
    }

    private static final DownloadableSubscription SUBSCRIPTION_WITH_METADATA =
            DownloadableSubscription.forActivationCode("abcde");
    static {
        SUBSCRIPTION_WITH_METADATA.setCarrierName("test name");
        SUBSCRIPTION_WITH_METADATA.setAccessRules(new UiccAccessRule[] { ACCESS_RULE });
    }

    private static final String OS_VERSION = "1.0";
    private static final EuiccInfo EUICC_INFO = new EuiccInfo(OS_VERSION);

    private static final int SUBSCRIPTION_ID = 12345;
    private static final String ICC_ID = "54321";

    @Mock private EuiccConnector mMockConnector;
    private TestEuiccController mController;
    private int mSavedEuiccProvisionedValue;

    private static class TestEuiccController extends EuiccController {
        // Captured arguments to addResolutionIntent
        private String mResolutionAction;
        private EuiccOperation mOp;

        // Captured arguments to sendResult.
        private PendingIntent mCallbackIntent;
        private int mResultCode;
        private Intent mExtrasIntent;

        // Whether refreshSubscriptionsAndSendResult was called.
        private boolean mCalledRefreshSubscriptionsAndSendResult;

        TestEuiccController(Context context, EuiccConnector connector) {
            super(context, connector);
        }

        @Override
        public void addResolutionIntent(
                Intent extrasIntent, String resolutionAction, String callingPackage,
                EuiccOperation op) {
            mResolutionAction = resolutionAction;
            mOp = op;
        }

        @Override
        public void sendResult(PendingIntent callbackIntent, int resultCode, Intent extrasIntent) {
            assertNull("sendResult called twice unexpectedly", mCallbackIntent);
            mCallbackIntent = callbackIntent;
            mResultCode = resultCode;
            mExtrasIntent = extrasIntent;
        }

        @Override
        public void refreshSubscriptionsAndSendResult(
                PendingIntent callbackIntent, int resultCode, Intent extrasIntent) {
            mCalledRefreshSubscriptionsAndSendResult = true;
            sendResult(callbackIntent, resultCode, extrasIntent);
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp("EuiccControllerTest");
        mController = new TestEuiccController(mContext, mMockConnector);

        PackageInfo pi = new PackageInfo();
        pi.packageName = PACKAGE_NAME;
        pi.signatures = new Signature[] { new Signature(SIGNATURE_BYTES) };
        when(mPackageManager.getPackageInfo(eq(PACKAGE_NAME), anyInt())).thenReturn(pi);

        mSavedEuiccProvisionedValue = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.EUICC_PROVISIONED, 0);
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.EUICC_PROVISIONED, 0);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.EUICC_PROVISIONED, mSavedEuiccProvisionedValue);
    }

    @Test(expected = SecurityException.class)
    public void testGetEid_noPrivileges() {
        setGetEidPermissions(false /* hasPhoneStatePrivileged */, false /* hasCarrierPrivileges */);
        callGetEid(true /* success */, "ABCDE" /* eid */);
    }

    @Test
    public void testGetEid_withPhoneStatePrivileged() {
        setGetEidPermissions(true /* hasPhoneStatePrivileged */, false /* hasCarrierPrivileges */);
        assertEquals("ABCDE", callGetEid(true /* success */, "ABCDE" /* eid */));
    }

    @Test
    public void testGetEid_withCarrierPrivileges() {
        setGetEidPermissions(false /* hasPhoneStatePrivileged */, true /* hasCarrierPrivileges */);
        assertEquals("ABCDE", callGetEid(true /* success */, "ABCDE" /* eid */));
    }

    @Test
    public void testGetEid_failure() {
        setGetEidPermissions(true /* hasPhoneStatePrivileged */, false /* hasCarrierPrivileges */);
        assertNull(callGetEid(false /* success */, null /* eid */));
    }

    @Test
    public void testGetEid_nullReturnValue() {
        setGetEidPermissions(true /* hasPhoneStatePrivileged */, false /* hasCarrierPrivileges */);
        assertNull(callGetEid(true /* success */, null /* eid */));
    }

    @Test
    public void testGetEuiccInfo_success() {
        assertEquals(OS_VERSION, callGetEuiccInfo(true /* success */, EUICC_INFO).osVersion);
    }

    @Test
    public void testGetEuiccInfo_failure() {
        assertNull(callGetEuiccInfo(false /* success */, null /* euiccInfo */));
    }

    @Test
    public void testGetEuiccInfo_nullReturnValue() {
        assertNull(callGetEuiccInfo(true /* success */, null /* euiccInfo */));
    }

    @Test
    public void testGetDownloadableSubscriptionMetadata_serviceUnavailable() throws Exception {
        setHasWriteEmbeddedPermission(true);
        callGetDownloadableSubscriptionMetadata(
                SUBSCRIPTION, false /* complete */, null /* result */);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR,
                0 /* detailedCode */);
        verify(mMockConnector).getDownloadableSubscriptionMetadata(any(), anyBoolean(), any());
    }

    @Test
    public void testGetDownloadableSubscriptionMetadata_error() throws Exception {
        setHasWriteEmbeddedPermission(true);
        GetDownloadableSubscriptionMetadataResult result =
                new GetDownloadableSubscriptionMetadataResult(42, null /* subscription */);
        callGetDownloadableSubscriptionMetadata(SUBSCRIPTION, true /* complete */, result);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR,
                42 /* detailedCode */);
    }

    @Test
    public void testGetDownloadableSubscriptionMetadata_mustDeactivateSim()
            throws Exception {
        setHasWriteEmbeddedPermission(true);
        GetDownloadableSubscriptionMetadataResult result =
                new GetDownloadableSubscriptionMetadataResult(
                        EuiccService.RESULT_MUST_DEACTIVATE_SIM, null /* subscription */);
        callGetDownloadableSubscriptionMetadata(SUBSCRIPTION, true /* complete */, result);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_RESOLVABLE_ERROR,
                0 /* detailedCode */);
        verifyResolutionIntent(EuiccService.ACTION_RESOLVE_DEACTIVATE_SIM,
                EuiccOperation.ACTION_GET_METADATA_DEACTIVATE_SIM);
    }

    @Test
    public void testGetDownloadableSubscriptionMetadata_success() throws Exception {
        setHasWriteEmbeddedPermission(true);
        GetDownloadableSubscriptionMetadataResult result =
                new GetDownloadableSubscriptionMetadataResult(
                        EuiccService.RESULT_OK, SUBSCRIPTION_WITH_METADATA);
        callGetDownloadableSubscriptionMetadata(SUBSCRIPTION, true /* complete */, result);
        Intent intent = verifyIntentSent(
                EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK, 0 /* detailedCode */);
        DownloadableSubscription receivedSubscription = intent.getParcelableExtra(
                EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_DOWNLOADABLE_SUBSCRIPTION);
        assertNotNull(receivedSubscription);
        assertEquals(CARRIER_NAME, receivedSubscription.getCarrierName());
    }

    @Test
    public void testGetDefaultDownloadableSubscriptionList_serviceUnavailable() throws Exception {
        setHasWriteEmbeddedPermission(true);
        callGetDefaultDownloadableSubscriptionList(false /* complete */, null /* result */);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR,
                0 /* detailedCode */);
    }

    @Test
    public void testGetDefaultDownloadableSubscriptionList_error() throws Exception {
        setHasWriteEmbeddedPermission(true);
        GetDefaultDownloadableSubscriptionListResult result =
                new GetDefaultDownloadableSubscriptionListResult(42, null /* subscriptions */);
        callGetDefaultDownloadableSubscriptionList(true /* complete */, result);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR,
                42 /* detailedCode */);
        verify(mMockConnector).getDefaultDownloadableSubscriptionList(anyBoolean(), any());
    }

    @Test
    public void testGetDefaultDownloadableSubscriptionList_mustDeactivateSim()
            throws Exception {
        setHasWriteEmbeddedPermission(true);
        GetDefaultDownloadableSubscriptionListResult result =
                new GetDefaultDownloadableSubscriptionListResult(
                        EuiccService.RESULT_MUST_DEACTIVATE_SIM, null /* subscriptions */);
        callGetDefaultDownloadableSubscriptionList(true /* complete */, result);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_RESOLVABLE_ERROR,
                0 /* detailedCode */);
        verifyResolutionIntent(EuiccService.ACTION_RESOLVE_DEACTIVATE_SIM,
                EuiccOperation.ACTION_GET_DEFAULT_LIST_DEACTIVATE_SIM);
    }

    @Test
    public void testGetDefaultDownloadableSubscriptionList_success() throws Exception {
        setHasWriteEmbeddedPermission(true);
        GetDefaultDownloadableSubscriptionListResult result =
                new GetDefaultDownloadableSubscriptionListResult(
                        EuiccService.RESULT_OK,
                        new DownloadableSubscription[] { SUBSCRIPTION_WITH_METADATA });
        callGetDefaultDownloadableSubscriptionList(true /* complete */, result);
        Intent intent = verifyIntentSent(
                EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK, 0 /* detailedCode */);
        Parcelable[] receivedSubscriptions = intent.getParcelableArrayExtra(
                EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_DOWNLOADABLE_SUBSCRIPTIONS);
        assertNotNull(receivedSubscriptions);
        assertEquals(1, receivedSubscriptions.length);
        assertEquals(CARRIER_NAME,
                ((DownloadableSubscription) receivedSubscriptions[0]).getCarrierName());
    }

    @Test
    public void testDownloadSubscription_serviceUnavailable() throws Exception {
        setHasWriteEmbeddedPermission(true);
        callDownloadSubscription(
                SUBSCRIPTION, true /* switchAfterDownload */, false /* complete */,
                0 /* result */, "whatever" /* callingPackage */);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR,
                0 /* detailedCode */);
        verify(mMockConnector).downloadSubscription(any(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    public void testDownloadSubscription_error() throws Exception {
        setHasWriteEmbeddedPermission(true);
        callDownloadSubscription(SUBSCRIPTION, false /* switchAfterDownload */, true /* complete */,
                42, "whatever" /* callingPackage */);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR,
                42 /* detailedCode */);
    }

    @Test
    public void testDownloadSubscription_mustDeactivateSim() throws Exception {
        setHasWriteEmbeddedPermission(true);
        callDownloadSubscription(SUBSCRIPTION, false /* switchAfterDownload */, true /* complete */,
                EuiccService.RESULT_MUST_DEACTIVATE_SIM, "whatever" /* callingPackage */);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_RESOLVABLE_ERROR,
                0 /* detailedCode */);
        verifyResolutionIntent(EuiccService.ACTION_RESOLVE_DEACTIVATE_SIM,
                EuiccOperation.ACTION_DOWNLOAD_DEACTIVATE_SIM);
    }

    @Test
    public void testDownloadSubscription_success() throws Exception {
        setHasWriteEmbeddedPermission(true);
        callDownloadSubscription(SUBSCRIPTION, true /* switchAfterDownload */, true /* complete */,
                EuiccService.RESULT_OK, "whatever" /* callingPackage */);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK, 0 /* detailedCode */);
        // switchAfterDownload = true so no refresh should occur.
        assertFalse(mController.mCalledRefreshSubscriptionsAndSendResult);
    }

    @Test
    public void testDownloadSubscription_noSwitch_success() throws Exception {
        setHasWriteEmbeddedPermission(true);
        callDownloadSubscription(SUBSCRIPTION, false /* switchAfterDownload */, true /* complete */,
                EuiccService.RESULT_OK, "whatever" /* callingPackage */);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK, 0 /* detailedCode */);
        assertTrue(mController.mCalledRefreshSubscriptionsAndSendResult);
    }

    @Test
    public void testDownloadSubscription_noPrivileges_getMetadata_serviceUnavailable()
            throws Exception {
        setHasWriteEmbeddedPermission(false);
        prepareGetDownloadableSubscriptionMetadataCall(false /* complete */, null /* result */);
        callDownloadSubscription(SUBSCRIPTION, true /* switchAfterDownload */, true /* complete */,
                12345, PACKAGE_NAME /* callingPackage */);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR,
                0 /* detailedCode */);
        verify(mMockConnector, never()).downloadSubscription(
                any(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    public void testDownloadSubscription_noPrivileges_getMetadata_error()
            throws Exception {
        setHasWriteEmbeddedPermission(false);
        GetDownloadableSubscriptionMetadataResult result =
                new GetDownloadableSubscriptionMetadataResult(42, null /* subscription */);
        prepareGetDownloadableSubscriptionMetadataCall(true /* complete */, result);
        callDownloadSubscription(SUBSCRIPTION, true /* switchAfterDownload */, true /* complete */,
                12345, PACKAGE_NAME /* callingPackage */);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR,
                42 /* detailedCode */);
        verify(mMockConnector, never()).downloadSubscription(
                any(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    public void testDownloadSubscription_noPrivileges_getMetadata_mustDeactivateSim()
            throws Exception {
        setHasWriteEmbeddedPermission(false);
        GetDownloadableSubscriptionMetadataResult result =
                new GetDownloadableSubscriptionMetadataResult(
                        EuiccService.RESULT_MUST_DEACTIVATE_SIM, null /* subscription */);
        prepareGetDownloadableSubscriptionMetadataCall(true /* complete */, result);
        callDownloadSubscription(SUBSCRIPTION, true /* switchAfterDownload */, true /* complete */,
                12345, PACKAGE_NAME /* callingPackage */);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_RESOLVABLE_ERROR,
                0 /* detailedCode */);
        // In this case we go with the potentially stronger NO_PRIVILEGES consent dialog to avoid
        // double prompting.
        verifyResolutionIntent(EuiccService.ACTION_RESOLVE_NO_PRIVILEGES,
                EuiccOperation.ACTION_DOWNLOAD_NO_PRIVILEGES);
    }

    @Test
    public void testDownloadSubscription_noPrivileges_hasCarrierPrivileges() throws Exception {
        setHasWriteEmbeddedPermission(false);
        GetDownloadableSubscriptionMetadataResult result =
                new GetDownloadableSubscriptionMetadataResult(
                        EuiccService.RESULT_OK, SUBSCRIPTION_WITH_METADATA);
        prepareGetDownloadableSubscriptionMetadataCall(true /* complete */, result);
        setHasCarrierPrivilegesOnActiveSubscription(true);
        callDownloadSubscription(SUBSCRIPTION, true /* switchAfterDownload */, true /* complete */,
                EuiccService.RESULT_OK, PACKAGE_NAME /* callingPackage */);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK, 0 /* detailedCode */);
        // switchAfterDownload = true so no refresh should occur.
        assertFalse(mController.mCalledRefreshSubscriptionsAndSendResult);
    }

    @Test
    public void testDownloadSubscription_noPrivileges_hasCarrierPrivileges_needsConsent()
            throws Exception {
        setHasWriteEmbeddedPermission(false);
        GetDownloadableSubscriptionMetadataResult result =
                new GetDownloadableSubscriptionMetadataResult(
                        EuiccService.RESULT_OK, SUBSCRIPTION_WITH_METADATA);
        prepareGetDownloadableSubscriptionMetadataCall(true /* complete */, result);
        setHasCarrierPrivilegesOnActiveSubscription(false);
        callDownloadSubscription(SUBSCRIPTION, true /* switchAfterDownload */, true /* complete */,
                12345, PACKAGE_NAME /* callingPackage */);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_RESOLVABLE_ERROR,
                0 /* detailedCode */);
        verify(mMockConnector, never()).downloadSubscription(
                any(), anyBoolean(), anyBoolean(), any());
        verifyResolutionIntent(EuiccService.ACTION_RESOLVE_NO_PRIVILEGES,
                EuiccOperation.ACTION_DOWNLOAD_NO_PRIVILEGES);
    }

    @Test
    public void testDownloadSubscription_noPrivileges_noCarrierPrivileges() throws Exception {
        setHasWriteEmbeddedPermission(false);
        GetDownloadableSubscriptionMetadataResult result =
                new GetDownloadableSubscriptionMetadataResult(
                        EuiccService.RESULT_OK, SUBSCRIPTION_WITH_METADATA);
        prepareGetDownloadableSubscriptionMetadataCall(true /* complete */, result);
        PackageInfo pi = new PackageInfo();
        pi.packageName = PACKAGE_NAME;
        pi.signatures = new Signature[] { new Signature(new byte[] { 5, 4, 3, 2, 1 }) };
        when(mPackageManager.getPackageInfo(eq(PACKAGE_NAME), anyInt())).thenReturn(pi);
        callDownloadSubscription(SUBSCRIPTION, true /* switchAfterDownload */, true /* complete */,
                12345, PACKAGE_NAME /* callingPackage */);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR,
                0 /* detailedCode */);
        verify(mTelephonyManager, never()).checkCarrierPrivilegesForPackage(PACKAGE_NAME);
        verify(mMockConnector, never()).downloadSubscription(
                any(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    public void testDeleteSubscription_noSuchSubscription() throws Exception {
        setHasWriteEmbeddedPermission(true);
        callDeleteSubscription(
                SUBSCRIPTION_ID, ICC_ID, false /* complete */,
                0 /* result */, "whatever" /* callingPackage */);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR,
                0 /* detailedCode */);
        verify(mMockConnector, never()).deleteSubscription(anyString(), any());
    }

    @Test
    public void testDeleteSubscription_serviceUnavailable() throws Exception {
        setHasWriteEmbeddedPermission(true);
        prepareOperationSubscription(false /* hasPrivileges */);
        callDeleteSubscription(
                SUBSCRIPTION_ID, ICC_ID, false /* complete */,
                0 /* result */, "whatever" /* callingPackage */);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR,
                0 /* detailedCode */);
    }

    @Test
    public void testDeleteSubscription_error() throws Exception {
        setHasWriteEmbeddedPermission(true);
        prepareOperationSubscription(false /* hasPrivileges */);
        callDeleteSubscription(
                SUBSCRIPTION_ID, ICC_ID, true /* complete */,
                42 /* result */, "whatever" /* callingPackage */);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR,
                42 /* detailedCode */);
    }

    @Test
    public void testDeleteSubscription_success() throws Exception {
        setHasWriteEmbeddedPermission(true);
        prepareOperationSubscription(false /* hasPrivileges */);
        callDeleteSubscription(
                SUBSCRIPTION_ID, ICC_ID, true /* complete */,
                EuiccService.RESULT_OK, "whatever" /* callingPackage */);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK, 0 /* detailedCode */);
        assertTrue(mController.mCalledRefreshSubscriptionsAndSendResult);
    }

    @Test
    public void testDeleteSubscription_noPrivileges() throws Exception {
        setHasWriteEmbeddedPermission(false);
        prepareOperationSubscription(false /* hasPrivileges */);
        callDeleteSubscription(
                SUBSCRIPTION_ID, ICC_ID, false /* complete */,
                0 /* result */, "whatever" /* callingPackage */);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR,
                0 /* detailedCode */);
        verify(mMockConnector, never()).deleteSubscription(anyString(), any());
    }

    @Test
    public void testDeleteSubscription_carrierPrivileges_success() throws Exception {
        setHasWriteEmbeddedPermission(false);
        prepareOperationSubscription(true /* hasPrivileges */);
        callDeleteSubscription(
                SUBSCRIPTION_ID, ICC_ID, true /* complete */, EuiccService.RESULT_OK, PACKAGE_NAME);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK, 0 /* detailedCode */);
        assertTrue(mController.mCalledRefreshSubscriptionsAndSendResult);
    }

    @Test
    public void testSwitchToSubscription_noSuchSubscription() throws Exception {
        setHasWriteEmbeddedPermission(true);
        callSwitchToSubscription(
                12345, ICC_ID, false /* complete */, 0 /* result */,
                "whatever" /* callingPackage */);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR,
                0 /* detailedCode */);
        verify(mMockConnector, never()).switchToSubscription(anyString(), anyBoolean(), any());
    }

    @Test
    public void testSwitchToSubscription_emptySubscription_noPrivileges() throws Exception {
        setHasWriteEmbeddedPermission(false);
        callSwitchToSubscription(
                SubscriptionManager.INVALID_SUBSCRIPTION_ID, null /* iccid */, false /* complete */,
                0 /* result */, "whatever" /* callingPackage */);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR,
                0 /* detailedCode */);
        verify(mMockConnector, never()).switchToSubscription(anyString(), anyBoolean(), any());
    }

    @Test
    public void testSwitchToSubscription_serviceUnavailable() throws Exception {
        setHasWriteEmbeddedPermission(true);
        prepareOperationSubscription(false /* hasPrivileges */);
        callSwitchToSubscription(
                SUBSCRIPTION_ID, ICC_ID, false /* complete */, 0 /* result */,
                "whatever" /* callingPackage */);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR,
                0 /* detailedCode */);
        verify(mMockConnector).switchToSubscription(anyString(), anyBoolean(), any());
    }

    @Test
    public void testSwitchToSubscription_error() throws Exception {
        setHasWriteEmbeddedPermission(true);
        prepareOperationSubscription(false /* hasPrivileges */);
        callSwitchToSubscription(
                SUBSCRIPTION_ID, ICC_ID, true /* complete */, 42 /* result */,
                "whatever" /* callingPackage */);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR,
                42 /* detailedCode */);
    }

    @Test
    public void testSwitchToSubscription_success() throws Exception {
        setHasWriteEmbeddedPermission(true);
        prepareOperationSubscription(false /* hasPrivileges */);
        callSwitchToSubscription(
                SUBSCRIPTION_ID, ICC_ID, true /* complete */, EuiccService.RESULT_OK,
                "whatever" /* callingPackage */);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK, 0 /* detailedCode */);
    }

    @Test
    public void testSwitchToSubscription_emptySubscription_success() throws Exception {
        setHasWriteEmbeddedPermission(true);
        callSwitchToSubscription(
                SubscriptionManager.INVALID_SUBSCRIPTION_ID, null /* iccid */, true /* complete */,
                EuiccService.RESULT_OK, "whatever" /* callingPackage */);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK, 0 /* detailedCode */);
    }

    @Test
    public void testSwitchToSubscription_noPrivileges() throws Exception {
        setHasWriteEmbeddedPermission(false);
        prepareOperationSubscription(false /* hasPrivileges */);
        callSwitchToSubscription(
                SUBSCRIPTION_ID, ICC_ID, false /* complete */, 0 /* result */,
                "whatever" /* callingPackage */);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR,
                0 /* detailedCode */);
        verify(mMockConnector, never()).switchToSubscription(anyString(), anyBoolean(), any());
    }

    @Test
    public void testSwitchToSubscription_hasCarrierPrivileges() throws Exception {
        setHasWriteEmbeddedPermission(false);
        prepareOperationSubscription(true /* hasPrivileges */);
        setHasCarrierPrivilegesOnActiveSubscription(true);
        callSwitchToSubscription(
                SUBSCRIPTION_ID, ICC_ID, true /* complete */, EuiccService.RESULT_OK, PACKAGE_NAME);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK, 0 /* detailedCode */);
    }

    @Test
    public void testSwitchToSubscription_hasCarrierPrivileges_needsConsent() throws Exception {
        setHasWriteEmbeddedPermission(false);
        prepareOperationSubscription(true /* hasPrivileges */);
        setHasCarrierPrivilegesOnActiveSubscription(false);
        callSwitchToSubscription(
                SUBSCRIPTION_ID, ICC_ID, false /* complete */, 0 /* result */, PACKAGE_NAME);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_RESOLVABLE_ERROR,
                0 /* detailedCode */);
        verify(mMockConnector, never()).switchToSubscription(anyString(), anyBoolean(), any());
        verifyResolutionIntent(EuiccService.ACTION_RESOLVE_NO_PRIVILEGES,
                EuiccOperation.ACTION_SWITCH_NO_PRIVILEGES);
    }

    @Test(expected = SecurityException.class)
    public void testUpdateSubscriptionNickname_noPrivileges() throws Exception {
        setHasWriteEmbeddedPermission(false);
        callUpdateSubscriptionNickname(
                SUBSCRIPTION_ID, ICC_ID, "nickname", false /* complete */, 0 /* result */);
    }

    @Test
    public void testUpdateSubscriptionNickname_noSuchSubscription() throws Exception {
        setHasWriteEmbeddedPermission(true);
        callUpdateSubscriptionNickname(
                SUBSCRIPTION_ID, ICC_ID, "nickname", false /* complete */, 0 /* result */);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR,
                0 /* detailedCode */);
        verify(mMockConnector, never()).updateSubscriptionNickname(anyString(), anyString(), any());
    }

    @Test
    public void testUpdateSubscriptionNickname_serviceUnavailable() throws Exception {
        setHasWriteEmbeddedPermission(true);
        prepareOperationSubscription(false /* hasPrivileges */);
        callUpdateSubscriptionNickname(
                SUBSCRIPTION_ID, ICC_ID, "nickname", false /* complete */, 0 /* result */);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR,
                0 /* detailedCode */);
        verify(mMockConnector).updateSubscriptionNickname(anyString(), anyString(), any());
    }

    @Test
    public void testUpdateSubscriptionNickname_error() throws Exception {
        setHasWriteEmbeddedPermission(true);
        prepareOperationSubscription(false /* hasPrivileges */);
        callUpdateSubscriptionNickname(
                SUBSCRIPTION_ID, ICC_ID, "nickname", true /* complete */, 42 /* result */);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR,
                42 /* detailedCode */);
    }

    @Test
    public void testUpdateSubscriptionNickname_success() throws Exception {
        setHasWriteEmbeddedPermission(true);
        prepareOperationSubscription(false /* hasPrivileges */);
        callUpdateSubscriptionNickname(
                SUBSCRIPTION_ID, ICC_ID, "nickname", true /* complete */, EuiccService.RESULT_OK);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK, 0 /* detailedCode */);
    }

    @Test(expected = SecurityException.class)
    public void testEraseSubscriptions_noPrivileges() throws Exception {
        setHasWriteEmbeddedPermission(false);
        callEraseSubscriptions(false /* complete */, 0 /* result */);
    }

    @Test
    public void testEraseSubscriptions_serviceUnavailable() throws Exception {
        setHasWriteEmbeddedPermission(true);
        callEraseSubscriptions(false /* complete */, 0 /* result */);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR,
                0 /* detailedCode */);
        verify(mMockConnector).eraseSubscriptions(any());
    }

    @Test
    public void testEraseSubscriptions_error() throws Exception {
        setHasWriteEmbeddedPermission(true);
        callEraseSubscriptions(true /* complete */, 42 /* result */);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR,
                42 /* detailedCode */);
    }

    @Test
    public void testEraseSubscriptions_success() throws Exception {
        setHasWriteEmbeddedPermission(true);
        callEraseSubscriptions(true /* complete */, EuiccService.RESULT_OK);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK, 0 /* detailedCode */);
        assertTrue(mController.mCalledRefreshSubscriptionsAndSendResult);
    }

    @Test(expected = SecurityException.class)
    public void testRetainSubscriptionsForFactoryReset_noPrivileges() throws Exception {
        setHasMasterClearPermission(false);
        callRetainSubscriptionsForFactoryReset(false /* complete */, 0 /* result */);
    }

    @Test
    public void testRetainSubscriptionsForFactoryReset_serviceUnavailable() throws Exception {
        setHasMasterClearPermission(true);
        callRetainSubscriptionsForFactoryReset(false /* complete */, 0 /* result */);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR, 0 /* detailedCode */);
        verify(mMockConnector).retainSubscriptions(any());
    }

    @Test
    public void testRetainSubscriptionsForFactoryReset_error() throws Exception {
        setHasMasterClearPermission(true);
        callRetainSubscriptionsForFactoryReset(true /* complete */, 42 /* result */);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR, 42 /* detailedCode */);
    }

    @Test
    public void testRetainSubscriptionsForFactoryReset_success() throws Exception {
        setHasMasterClearPermission(true);
        callRetainSubscriptionsForFactoryReset(true /* complete */, EuiccService.RESULT_OK);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK, 0 /* detailedCode */);
    }

    private void setGetEidPermissions(
            boolean hasPhoneStatePrivileged, boolean hasCarrierPrivileges) {
        doReturn(hasPhoneStatePrivileged
                ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED)
                .when(mContext)
                .checkCallingPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        when(mTelephonyManager.hasCarrierPrivileges()).thenReturn(hasCarrierPrivileges);
    }

    private void setHasWriteEmbeddedPermission(boolean hasPermission) {
        doReturn(hasPermission
                ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED)
                .when(mContext)
                .checkCallingPermission(Manifest.permission.WRITE_EMBEDDED_SUBSCRIPTIONS);
    }

    private void setHasMasterClearPermission(boolean hasPermission) {
        Stubber stubber = hasPermission ? doNothing() : doThrow(new SecurityException());
        stubber.when(mContext).enforceCallingPermission(
                eq(Manifest.permission.MASTER_CLEAR), anyString());
    }

    private void setHasCarrierPrivilegesOnActiveSubscription(boolean hasPrivileges)
            throws Exception {
        SubscriptionInfo subInfo = new SubscriptionInfo(
                0, "", 0, "", "", 0, 0, "", 0, null, 0, 0, "", true /* isEmbedded */,
                hasPrivileges ? new UiccAccessRule[] { ACCESS_RULE } : null);
        when(mSubscriptionManager.getActiveSubscriptionInfoList()).thenReturn(
                Collections.singletonList(subInfo));
    }

    private void prepareOperationSubscription(boolean hasPrivileges) throws Exception {
        SubscriptionInfo subInfo = new SubscriptionInfo(
                SUBSCRIPTION_ID, ICC_ID, 0, "", "", 0, 0, "", 0, null, 0, 0, "",
                true /* isEmbedded */, hasPrivileges ? new UiccAccessRule[] { ACCESS_RULE } : null);
        when(mSubscriptionManager.getAvailableSubscriptionInfoList()).thenReturn(
                Collections.singletonList(subInfo));
    }

    private String callGetEid(final boolean success, final @Nullable String eid) {
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Exception {
                EuiccConnector.GetEidCommandCallback cb = invocation.getArgument(0);
                if (success) {
                    cb.onGetEidComplete(eid);
                } else {
                    cb.onEuiccServiceUnavailable();
                }
                return null;
            }
        }).when(mMockConnector).getEid(Mockito.<EuiccConnector.GetEidCommandCallback>any());
        return mController.getEid();
    }

    private EuiccInfo callGetEuiccInfo(final boolean success, final @Nullable EuiccInfo euiccInfo) {
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Exception {
                EuiccConnector.GetEuiccInfoCommandCallback cb = invocation.getArgument(0);
                if (success) {
                    cb.onGetEuiccInfoComplete(euiccInfo);
                } else {
                    cb.onEuiccServiceUnavailable();
                }
                return null;
            }
        }).when(mMockConnector).getEuiccInfo(any());
        return mController.getEuiccInfo();
    }

    private void prepareGetDownloadableSubscriptionMetadataCall(
            final boolean complete, final GetDownloadableSubscriptionMetadataResult result) {
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Exception {
                EuiccConnector.GetMetadataCommandCallback cb = invocation.getArgument(2);
                if (complete) {
                    cb.onGetMetadataComplete(result);
                } else {
                    cb.onEuiccServiceUnavailable();
                }
                return null;
            }
        }).when(mMockConnector).getDownloadableSubscriptionMetadata(any(), anyBoolean(), any());
    }

    private void callGetDownloadableSubscriptionMetadata(DownloadableSubscription subscription,
            boolean complete, GetDownloadableSubscriptionMetadataResult result) {
        prepareGetDownloadableSubscriptionMetadataCall(complete, result);
        PendingIntent resultCallback = PendingIntent.getBroadcast(mContext, 0, new Intent(), 0);
        mController.getDownloadableSubscriptionMetadata(subscription, PACKAGE_NAME, resultCallback);
    }

    private void callGetDefaultDownloadableSubscriptionList(
            boolean complete, GetDefaultDownloadableSubscriptionListResult result) {
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Exception {
                EuiccConnector.GetDefaultListCommandCallback cb = invocation.getArgument(1);
                if (complete) {
                    cb.onGetDefaultListComplete(result);
                } else {
                    cb.onEuiccServiceUnavailable();
                }
                return null;
            }
        }).when(mMockConnector).getDefaultDownloadableSubscriptionList(anyBoolean(), any());
        PendingIntent resultCallback = PendingIntent.getBroadcast(mContext, 0, new Intent(), 0);
        mController.getDefaultDownloadableSubscriptionList(PACKAGE_NAME, resultCallback);
    }

    private void callDownloadSubscription(DownloadableSubscription subscription,
            boolean switchAfterDownload, final boolean complete, final int result,
            String callingPackage) {
        PendingIntent resultCallback = PendingIntent.getBroadcast(mContext, 0, new Intent(), 0);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Exception {
                EuiccConnector.DownloadCommandCallback cb = invocation.getArgument(3);
                if (complete) {
                    cb.onDownloadComplete(result);
                } else {
                    cb.onEuiccServiceUnavailable();
                }
                return null;
            }
        }).when(mMockConnector).downloadSubscription(
                any(), eq(switchAfterDownload), anyBoolean(), any());
        mController.downloadSubscription(subscription, switchAfterDownload, callingPackage,
                resultCallback);
        // EUICC_PROVISIONED setting should match whether the download was successful.
        assertEquals(complete && result == EuiccService.RESULT_OK ? 1 : 0,
                Settings.Global.getInt(mContext.getContentResolver(),
                        Settings.Global.EUICC_PROVISIONED, 0));
    }

    private void callDeleteSubscription(int subscriptionId, String iccid, final boolean complete,
            final int result, String callingPackage) {
        PendingIntent resultCallback = PendingIntent.getBroadcast(mContext, 0, new Intent(), 0);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Exception {
                EuiccConnector.DeleteCommandCallback cb = invocation.getArgument(1);
                if (complete) {
                    cb.onDeleteComplete(result);
                } else {
                    cb.onEuiccServiceUnavailable();
                }
                return null;
            }
        }).when(mMockConnector).deleteSubscription(eq(iccid), any());
        mController.deleteSubscription(subscriptionId, callingPackage, resultCallback);
    }

    private void callSwitchToSubscription(int subscriptionId, String iccid, final boolean complete,
            final int result, String callingPackage) {
        PendingIntent resultCallback = PendingIntent.getBroadcast(mContext, 0, new Intent(), 0);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Exception {
                EuiccConnector.SwitchCommandCallback cb = invocation.getArgument(2);
                if (complete) {
                    cb.onSwitchComplete(result);
                } else {
                    cb.onEuiccServiceUnavailable();
                }
                return null;
            }
        }).when(mMockConnector).switchToSubscription(eq(iccid), anyBoolean(), any());
        mController.switchToSubscription(subscriptionId, callingPackage, resultCallback);
    }

    private void callUpdateSubscriptionNickname(int subscriptionId, String iccid, String nickname,
            final boolean complete, final int result) {
        PendingIntent resultCallback = PendingIntent.getBroadcast(mContext, 0, new Intent(), 0);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Exception {
                EuiccConnector.UpdateNicknameCommandCallback cb = invocation.getArgument(2);
                if (complete) {
                    cb.onUpdateNicknameComplete(result);
                } else {
                    cb.onEuiccServiceUnavailable();
                }
                return null;
            }
        }).when(mMockConnector).updateSubscriptionNickname(eq(iccid), eq(nickname), any());
        mController.updateSubscriptionNickname(subscriptionId, nickname, resultCallback);
    }

    private void callEraseSubscriptions(final boolean complete, final int result) {
        PendingIntent resultCallback = PendingIntent.getBroadcast(mContext, 0, new Intent(), 0);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Exception {
                EuiccConnector.EraseCommandCallback cb = invocation.getArgument(0);
                if (complete) {
                    cb.onEraseComplete(result);
                } else {
                    cb.onEuiccServiceUnavailable();
                }
                return null;
            }
        }).when(mMockConnector).eraseSubscriptions(any());
        mController.eraseSubscriptions(resultCallback);
    }

    private void callRetainSubscriptionsForFactoryReset(final boolean complete, final int result) {
        PendingIntent resultCallback = PendingIntent.getBroadcast(mContext, 0, new Intent(), 0);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Exception {
                EuiccConnector.RetainSubscriptionsCommandCallback cb = invocation.getArgument(0);
                if (complete) {
                    cb.onRetainSubscriptionsComplete(result);
                } else {
                    cb.onEuiccServiceUnavailable();
                }
                return null;
            }
        }).when(mMockConnector).retainSubscriptions(any());
        mController.retainSubscriptionsForFactoryReset(resultCallback);
    }

    private void verifyResolutionIntent(String euiccUiAction, @EuiccOperation.Action int action) {
        assertEquals(euiccUiAction, mController.mResolutionAction);
        assertNotNull(mController.mOp);
        assertEquals(action, mController.mOp.mAction);
    }

    private Intent verifyIntentSent(int resultCode, int detailedCode)
            throws RemoteException {
        assertNotNull(mController.mCallbackIntent);
        assertEquals(resultCode, mController.mResultCode);
        if (mController.mExtrasIntent == null) {
            assertEquals(0, detailedCode);
        } else {
            assertEquals(detailedCode,
                    mController.mExtrasIntent.getIntExtra(
                            EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_DETAILED_CODE, 0));
        }
        return mController.mExtrasIntent;
    }
}
