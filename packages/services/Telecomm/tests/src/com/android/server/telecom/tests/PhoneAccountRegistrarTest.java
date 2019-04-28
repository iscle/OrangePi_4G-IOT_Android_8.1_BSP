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

package com.android.server.telecom.tests;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Process;
import android.os.UserHandle;
import android.telecom.Log;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Xml;

import com.android.internal.telecom.IConnectionService;
import com.android.internal.util.FastXmlSerializer;
import com.android.server.telecom.DefaultDialerCache;
import com.android.server.telecom.PhoneAccountRegistrar;
import com.android.server.telecom.PhoneAccountRegistrar.DefaultPhoneAccountHandle;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

public class PhoneAccountRegistrarTest extends TelecomTestCase {

    private static final int MAX_VERSION = Integer.MAX_VALUE;
    private static final String FILE_NAME = "phone-account-registrar-test-1223.xml";
    private static final String TEST_LABEL = "right";
    private PhoneAccountRegistrar mRegistrar;
    @Mock private TelecomManager mTelecomManager;
    @Mock private DefaultDialerCache mDefaultDialerCache;
    @Mock private PhoneAccountRegistrar.AppLabelProxy mAppLabelProxy;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        mComponentContextFixture.setTelecomManager(mTelecomManager);
        new File(
                mComponentContextFixture.getTestDouble().getApplicationContext().getFilesDir(),
                FILE_NAME)
                .delete();
        when(mDefaultDialerCache.getDefaultDialerApplication(anyInt()))
                .thenReturn("com.android.dialer");
        when(mAppLabelProxy.getAppLabel(anyString()))
                .thenReturn(TEST_LABEL);
        mRegistrar = new PhoneAccountRegistrar(
                mComponentContextFixture.getTestDouble().getApplicationContext(),
                FILE_NAME, mDefaultDialerCache, mAppLabelProxy);
    }

    @Override
    public void tearDown() throws Exception {
        mRegistrar = null;
        new File(
                mComponentContextFixture.getTestDouble().getApplicationContext().getFilesDir(),
                FILE_NAME)
                .delete();
        super.tearDown();
    }

    @MediumTest
    public void testPhoneAccountHandle() throws Exception {
        PhoneAccountHandle input = new PhoneAccountHandle(new ComponentName("pkg0", "cls0"), "id0");
        PhoneAccountHandle result = roundTripXml(this, input,
                PhoneAccountRegistrar.sPhoneAccountHandleXml, mContext);
        assertPhoneAccountHandleEquals(input, result);

        PhoneAccountHandle inputN = new PhoneAccountHandle(new ComponentName("pkg0", "cls0"), null);
        PhoneAccountHandle resultN = roundTripXml(this, inputN,
                PhoneAccountRegistrar.sPhoneAccountHandleXml, mContext);
        Log.i(this, "inputN = %s, resultN = %s", inputN, resultN);
        assertPhoneAccountHandleEquals(inputN, resultN);
    }

    @MediumTest
    public void testPhoneAccount() throws Exception {
        Bundle testBundle = new Bundle();
        testBundle.putInt("EXTRA_INT_1", 1);
        testBundle.putInt("EXTRA_INT_100", 100);
        testBundle.putBoolean("EXTRA_BOOL_TRUE", true);
        testBundle.putBoolean("EXTRA_BOOL_FALSE", false);
        testBundle.putString("EXTRA_STR1", "Hello");
        testBundle.putString("EXTRA_STR2", "There");

        PhoneAccount input = makeQuickAccountBuilder("id0", 0)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .addSupportedUriScheme(PhoneAccount.SCHEME_VOICEMAIL)
                .setExtras(testBundle)
                .setIsEnabled(true)
                .build();
        PhoneAccount result = roundTripXml(this, input, PhoneAccountRegistrar.sPhoneAccountXml,
                mContext);

        assertPhoneAccountEquals(input, result);
    }

    @MediumTest
    public void testDefaultPhoneAccountHandleEmptyGroup() throws Exception {
        DefaultPhoneAccountHandle input = new DefaultPhoneAccountHandle(Process.myUserHandle(),
                makeQuickAccountHandle("i1"), "");
        DefaultPhoneAccountHandle result = roundTripXml(this, input,
                PhoneAccountRegistrar.sDefaultPhoneAcountHandleXml, mContext);

        assertDefaultPhoneAccountHandleEquals(input, result);
    }

    /**
     * Test to ensure non-supported values
     * @throws Exception
     */
    @MediumTest
    public void testPhoneAccountExtrasEdge() throws Exception {
        Bundle testBundle = new Bundle();
        // Ensure null values for string are not persisted.
        testBundle.putString("EXTRA_STR2", null);
        //

        // Ensure unsupported data types are not persisted.
        testBundle.putShort("EXTRA_SHORT", (short) 2);
        testBundle.putByte("EXTRA_BYTE", (byte) 1);
        testBundle.putParcelable("EXTRA_PARC", new Rect(1, 1, 1, 1));
        // Put in something valid so the bundle exists.
        testBundle.putString("EXTRA_OK", "OK");

        PhoneAccount input = makeQuickAccountBuilder("id0", 0)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .addSupportedUriScheme(PhoneAccount.SCHEME_VOICEMAIL)
                .setExtras(testBundle)
                .build();
        PhoneAccount result = roundTripXml(this, input, PhoneAccountRegistrar.sPhoneAccountXml,
                mContext);

        Bundle extras = result.getExtras();
        assertFalse(extras.keySet().contains("EXTRA_STR2"));
        assertFalse(extras.keySet().contains("EXTRA_SHORT"));
        assertFalse(extras.keySet().contains("EXTRA_BYTE"));
        assertFalse(extras.keySet().contains("EXTRA_PARC"));
    }

    @MediumTest
    public void testState() throws Exception {
        PhoneAccountRegistrar.State input = makeQuickState();
        PhoneAccountRegistrar.State result = roundTripXml(this, input,
                PhoneAccountRegistrar.sStateXml,
                mContext);
        assertStateEquals(input, result);
    }

    private void registerAndEnableAccount(PhoneAccount account) {
        mRegistrar.registerPhoneAccount(account);
        mRegistrar.enablePhoneAccount(account.getAccountHandle(), true);
    }

    @MediumTest
    public void testAccounts() throws Exception {
        int i = 0;

        mComponentContextFixture.addConnectionService(makeQuickConnectionServiceComponentName(),
                Mockito.mock(IConnectionService.class));

        registerAndEnableAccount(makeQuickAccountBuilder("id" + i, i++)
                .setCapabilities(PhoneAccount.CAPABILITY_CONNECTION_MANAGER
                        | PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .build());
        registerAndEnableAccount(makeQuickAccountBuilder("id" + i, i++)
                .setCapabilities(PhoneAccount.CAPABILITY_CONNECTION_MANAGER
                        | PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .build());
        registerAndEnableAccount(makeQuickAccountBuilder("id" + i, i++)
                .setCapabilities(PhoneAccount.CAPABILITY_CONNECTION_MANAGER
                        | PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .build());
        registerAndEnableAccount(makeQuickAccountBuilder("id" + i, i++)
                .setCapabilities(PhoneAccount.CAPABILITY_CONNECTION_MANAGER)
                .build());

        assertEquals(4, mRegistrar.getAllPhoneAccountsOfCurrentUser().size());
        assertEquals(3, mRegistrar.getCallCapablePhoneAccountsOfCurrentUser(null, false).size());
        assertEquals(null, mRegistrar.getSimCallManagerOfCurrentUser());
        assertEquals(null, mRegistrar.getOutgoingPhoneAccountForSchemeOfCurrentUser(
                PhoneAccount.SCHEME_TEL));
    }

    @MediumTest
    public void testSimCallManager() throws Exception {
        // TODO
    }

    @MediumTest
    public void testDefaultOutgoing() throws Exception {
        mComponentContextFixture.addConnectionService(makeQuickConnectionServiceComponentName(),
                Mockito.mock(IConnectionService.class));

        // By default, there is no default outgoing account (nothing has been registered)
        assertNull(
                mRegistrar.getOutgoingPhoneAccountForSchemeOfCurrentUser(PhoneAccount.SCHEME_TEL));

        // Register one tel: account
        PhoneAccountHandle telAccount = makeQuickAccountHandle("tel_acct");
        registerAndEnableAccount(new PhoneAccount.Builder(telAccount, "tel_acct")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .build());
        PhoneAccountHandle defaultAccount =
                mRegistrar.getOutgoingPhoneAccountForSchemeOfCurrentUser(PhoneAccount.SCHEME_TEL);
        assertEquals(telAccount, defaultAccount);

        // Add a SIP account, make sure tel: doesn't change
        PhoneAccountHandle sipAccount = makeQuickAccountHandle("sip_acct");
        registerAndEnableAccount(new PhoneAccount.Builder(sipAccount, "sip_acct")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .addSupportedUriScheme(PhoneAccount.SCHEME_SIP)
                .build());
        defaultAccount = mRegistrar.getOutgoingPhoneAccountForSchemeOfCurrentUser(
                PhoneAccount.SCHEME_SIP);
        assertEquals(sipAccount, defaultAccount);
        defaultAccount = mRegistrar.getOutgoingPhoneAccountForSchemeOfCurrentUser(
                PhoneAccount.SCHEME_TEL);
        assertEquals(telAccount, defaultAccount);

        // Add a connection manager, make sure tel: doesn't change
        PhoneAccountHandle connectionManager = makeQuickAccountHandle("mgr_acct");
        registerAndEnableAccount(new PhoneAccount.Builder(connectionManager, "mgr_acct")
                .setCapabilities(PhoneAccount.CAPABILITY_CONNECTION_MANAGER)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .build());
        defaultAccount = mRegistrar.getOutgoingPhoneAccountForSchemeOfCurrentUser(
                PhoneAccount.SCHEME_TEL);
        assertEquals(telAccount, defaultAccount);

        // Unregister the tel: account, make sure there is no tel: default now.
        mRegistrar.unregisterPhoneAccount(telAccount);
        assertNull(
                mRegistrar.getOutgoingPhoneAccountForSchemeOfCurrentUser(PhoneAccount.SCHEME_TEL));
    }

    @MediumTest
    public void testReplacePhoneAccountByGroup() throws Exception {
        mComponentContextFixture.addConnectionService(makeQuickConnectionServiceComponentName(),
                Mockito.mock(IConnectionService.class));

        // By default, there is no default outgoing account (nothing has been registered)
        assertNull(
                mRegistrar.getOutgoingPhoneAccountForSchemeOfCurrentUser(PhoneAccount.SCHEME_TEL));

        // Register one tel: account
        PhoneAccountHandle telAccount1 = makeQuickAccountHandle("tel_acct1");
        registerAndEnableAccount(new PhoneAccount.Builder(telAccount1, "tel_acct1")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .setGroupId("testGroup")
                .build());
        mRegistrar.setUserSelectedOutgoingPhoneAccount(telAccount1, Process.myUserHandle());
        PhoneAccountHandle defaultAccount =
                mRegistrar.getOutgoingPhoneAccountForSchemeOfCurrentUser(PhoneAccount.SCHEME_TEL);
        assertEquals(telAccount1, defaultAccount);

        // Add call capable SIP account, make sure tel: doesn't change
        PhoneAccountHandle sipAccount = makeQuickAccountHandle("sip_acct");
        registerAndEnableAccount(new PhoneAccount.Builder(sipAccount, "sip_acct")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .build());
        defaultAccount = mRegistrar.getOutgoingPhoneAccountForSchemeOfCurrentUser(
                PhoneAccount.SCHEME_TEL);
        assertEquals(telAccount1, defaultAccount);

        // Replace tel: account with another in the same Group
        PhoneAccountHandle telAccount2 = makeQuickAccountHandle("tel_acct2");
        registerAndEnableAccount(new PhoneAccount.Builder(telAccount2, "tel_acct2")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .setGroupId("testGroup")
                .build());
        defaultAccount = mRegistrar.getOutgoingPhoneAccountForSchemeOfCurrentUser(
                PhoneAccount.SCHEME_TEL);
        assertEquals(telAccount2, defaultAccount);
        assertNull(mRegistrar.getPhoneAccountUnchecked(telAccount1));
    }

    @MediumTest
    public void testAddSameDefault() throws Exception {
        mComponentContextFixture.addConnectionService(makeQuickConnectionServiceComponentName(),
                Mockito.mock(IConnectionService.class));

        // By default, there is no default outgoing account (nothing has been registered)
        assertNull(
                mRegistrar.getOutgoingPhoneAccountForSchemeOfCurrentUser(PhoneAccount.SCHEME_TEL));

        // Register one tel: account
        PhoneAccountHandle telAccount1 = makeQuickAccountHandle("tel_acct1");
        registerAndEnableAccount(new PhoneAccount.Builder(telAccount1, "tel_acct1")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .setGroupId("testGroup")
                .build());
        mRegistrar.setUserSelectedOutgoingPhoneAccount(telAccount1, Process.myUserHandle());
        PhoneAccountHandle defaultAccount =
                mRegistrar.getUserSelectedOutgoingPhoneAccount(Process.myUserHandle());
        assertEquals(telAccount1, defaultAccount);
        mRegistrar.unregisterPhoneAccount(telAccount1);

        // Register Emergency Account and unregister
        PhoneAccountHandle emerAccount = makeQuickAccountHandle("emer_acct");
        registerAndEnableAccount(new PhoneAccount.Builder(emerAccount, "emer_acct")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .build());
        defaultAccount =
                mRegistrar.getUserSelectedOutgoingPhoneAccount(Process.myUserHandle());
        assertNull(defaultAccount);
        mRegistrar.unregisterPhoneAccount(emerAccount);

        // Re-register the same account and make sure the default is in place
        registerAndEnableAccount(new PhoneAccount.Builder(telAccount1, "tel_acct1")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .setGroupId("testGroup")
                .build());
        defaultAccount =
                mRegistrar.getUserSelectedOutgoingPhoneAccount(Process.myUserHandle());
        assertEquals(telAccount1, defaultAccount);
    }

    @MediumTest
    public void testAddSameGroup() throws Exception {
        mComponentContextFixture.addConnectionService(makeQuickConnectionServiceComponentName(),
                Mockito.mock(IConnectionService.class));

        // By default, there is no default outgoing account (nothing has been registered)
        assertNull(
                mRegistrar.getOutgoingPhoneAccountForSchemeOfCurrentUser(PhoneAccount.SCHEME_TEL));

        // Register one tel: account
        PhoneAccountHandle telAccount1 = makeQuickAccountHandle("tel_acct1");
        registerAndEnableAccount(new PhoneAccount.Builder(telAccount1, "tel_acct1")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .setGroupId("testGroup")
                .build());
        mRegistrar.setUserSelectedOutgoingPhoneAccount(telAccount1, Process.myUserHandle());
        PhoneAccountHandle defaultAccount =
                mRegistrar.getUserSelectedOutgoingPhoneAccount(Process.myUserHandle());
        assertEquals(telAccount1, defaultAccount);
        mRegistrar.unregisterPhoneAccount(telAccount1);

        // Register Emergency Account and unregister
        PhoneAccountHandle emerAccount = makeQuickAccountHandle("emer_acct");
        registerAndEnableAccount(new PhoneAccount.Builder(emerAccount, "emer_acct")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .build());
        defaultAccount =
                mRegistrar.getUserSelectedOutgoingPhoneAccount(Process.myUserHandle());
        assertNull(defaultAccount);
        mRegistrar.unregisterPhoneAccount(emerAccount);

        // Re-register a new account with the same group
        PhoneAccountHandle telAccount2 = makeQuickAccountHandle("tel_acct2");
        registerAndEnableAccount(new PhoneAccount.Builder(telAccount2, "tel_acct2")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .setGroupId("testGroup")
                .build());
        defaultAccount =
                mRegistrar.getUserSelectedOutgoingPhoneAccount(Process.myUserHandle());
        assertEquals(telAccount2, defaultAccount);
    }

    @MediumTest
    public void testAddSameGroupButDifferentComponent() throws Exception {
        mComponentContextFixture.addConnectionService(makeQuickConnectionServiceComponentName(),
                Mockito.mock(IConnectionService.class));

        // By default, there is no default outgoing account (nothing has been registered)
        assertNull(mRegistrar.getOutgoingPhoneAccountForSchemeOfCurrentUser(
                PhoneAccount.SCHEME_TEL));

        // Register one tel: account
        PhoneAccountHandle telAccount1 = makeQuickAccountHandle("tel_acct1");
        registerAndEnableAccount(new PhoneAccount.Builder(telAccount1, "tel_acct1")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .setGroupId("testGroup")
                .build());
        mRegistrar.setUserSelectedOutgoingPhoneAccount(telAccount1, Process.myUserHandle());
        PhoneAccountHandle defaultAccount =
                mRegistrar.getUserSelectedOutgoingPhoneAccount(Process.myUserHandle());
        assertEquals(telAccount1, defaultAccount);
        assertNotNull(mRegistrar.getPhoneAccountUnchecked(telAccount1));

        // Register a new account with the same group, but different Component, so don't replace
        // Default
        PhoneAccountHandle telAccount2 =  makeQuickAccountHandle(
                new ComponentName("other1", "other2"), "tel_acct2");
        registerAndEnableAccount(new PhoneAccount.Builder(telAccount2, "tel_acct2")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .setGroupId("testGroup")
                .build());
        assertNotNull(mRegistrar.getPhoneAccountUnchecked(telAccount2));

        defaultAccount =
                mRegistrar.getUserSelectedOutgoingPhoneAccount(Process.myUserHandle());
        assertEquals(telAccount1, defaultAccount);
    }

    @MediumTest
    public void testAddSameGroupButDifferentComponent2() throws Exception {
        mComponentContextFixture.addConnectionService(makeQuickConnectionServiceComponentName(),
                Mockito.mock(IConnectionService.class));

        // By default, there is no default outgoing account (nothing has been registered)
        assertNull(mRegistrar.getOutgoingPhoneAccountForSchemeOfCurrentUser(
                PhoneAccount.SCHEME_TEL));

        // Register first tel: account
        PhoneAccountHandle telAccount1 =  makeQuickAccountHandle(
                new ComponentName("other1", "other2"), "tel_acct1");
        registerAndEnableAccount(new PhoneAccount.Builder(telAccount1, "tel_acct1")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .setGroupId("testGroup")
                .build());
        assertNotNull(mRegistrar.getPhoneAccountUnchecked(telAccount1));
        mRegistrar.setUserSelectedOutgoingPhoneAccount(telAccount1, Process.myUserHandle());

        // Register second account with the same group, but a second Component, so don't replace
        // Default
        PhoneAccountHandle telAccount2 = makeQuickAccountHandle("tel_acct2");
        registerAndEnableAccount(new PhoneAccount.Builder(telAccount2, "tel_acct2")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .setGroupId("testGroup")
                .build());

        PhoneAccountHandle defaultAccount =
                mRegistrar.getOutgoingPhoneAccountForSchemeOfCurrentUser(PhoneAccount.SCHEME_TEL);
        assertEquals(telAccount1, defaultAccount);

        // Register third account with the second component name, but same group id
        PhoneAccountHandle telAccount3 = makeQuickAccountHandle("tel_acct3");
        registerAndEnableAccount(new PhoneAccount.Builder(telAccount3, "tel_acct3")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .setGroupId("testGroup")
                .build());

        // Make sure that the default account is still the original PhoneAccount and that the
        // second PhoneAccount with the second ComponentName was replaced by the third PhoneAccount
        defaultAccount =
                mRegistrar.getOutgoingPhoneAccountForSchemeOfCurrentUser(PhoneAccount.SCHEME_TEL);
        assertEquals(telAccount1, defaultAccount);

        assertNotNull(mRegistrar.getPhoneAccountUnchecked(telAccount1));
        assertNull(mRegistrar.getPhoneAccountUnchecked(telAccount2));
        assertNotNull(mRegistrar.getPhoneAccountUnchecked(telAccount3));
    }

    @MediumTest
    public void testPhoneAccountParceling() throws Exception {
        PhoneAccountHandle handle = makeQuickAccountHandle("foo");
        roundTripPhoneAccount(new PhoneAccount.Builder(handle, null).build());
        roundTripPhoneAccount(new PhoneAccount.Builder(handle, "foo").build());
        roundTripPhoneAccount(
                new PhoneAccount.Builder(handle, "foo")
                        .setAddress(Uri.parse("tel:123456"))
                        .setCapabilities(23)
                        .setHighlightColor(0xf0f0f0)
                        .setIcon(Icon.createWithResource(
                                "com.android.server.telecom.tests", R.drawable.stat_sys_phone_call))
                        // TODO: set icon tint (0xfefefe)
                        .setShortDescription("short description")
                        .setSubscriptionAddress(Uri.parse("tel:2345678"))
                        .setSupportedUriSchemes(Arrays.asList("tel", "sip"))
                        .setGroupId("testGroup")
                        .build());
        roundTripPhoneAccount(
                new PhoneAccount.Builder(handle, "foo")
                        .setAddress(Uri.parse("tel:123456"))
                        .setCapabilities(23)
                        .setHighlightColor(0xf0f0f0)
                        .setIcon(Icon.createWithBitmap(
                                BitmapFactory.decodeResource(
                                        getContext().getResources(),
                                        R.drawable.stat_sys_phone_call)))
                        .setShortDescription("short description")
                        .setSubscriptionAddress(Uri.parse("tel:2345678"))
                        .setSupportedUriSchemes(Arrays.asList("tel", "sip"))
                        .setGroupId("testGroup")
                        .build());
    }

    /**
     * Tests ability to register a self-managed PhoneAccount; verifies that the user defined label
     * is overridden.
     * @throws Exception
     */
    @MediumTest
    public void testSelfManagedPhoneAccount() throws Exception {
        mComponentContextFixture.addConnectionService(makeQuickConnectionServiceComponentName(),
                Mockito.mock(IConnectionService.class));

        PhoneAccountHandle selfManagedHandle =  makeQuickAccountHandle(
                new ComponentName("self", "managed"), "selfie1");

        PhoneAccount selfManagedAccount = new PhoneAccount.Builder(selfManagedHandle, "Wrong")
                .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                .build();

        mRegistrar.registerPhoneAccount(selfManagedAccount);

        PhoneAccount registeredAccount = mRegistrar.getPhoneAccountUnchecked(selfManagedHandle);
        assertEquals(TEST_LABEL, registeredAccount.getLabel());
    }

    /**
     * Tests to ensure that when registering a self-managed PhoneAccount, it cannot also be defined
     * as a call provider, connection manager, or sim subscription.
     * @throws Exception
     */
    @MediumTest
    public void testSelfManagedCapabilityOverride() throws Exception {
        mComponentContextFixture.addConnectionService(makeQuickConnectionServiceComponentName(),
                Mockito.mock(IConnectionService.class));

        PhoneAccountHandle selfManagedHandle =  makeQuickAccountHandle(
                new ComponentName("self", "managed"), "selfie1");

        PhoneAccount selfManagedAccount = new PhoneAccount.Builder(selfManagedHandle, TEST_LABEL)
                .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED |
                        PhoneAccount.CAPABILITY_CALL_PROVIDER |
                        PhoneAccount.CAPABILITY_CONNECTION_MANAGER |
                        PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)
                .build();

        mRegistrar.registerPhoneAccount(selfManagedAccount);

        PhoneAccount registeredAccount = mRegistrar.getPhoneAccountUnchecked(selfManagedHandle);
        assertEquals(PhoneAccount.CAPABILITY_SELF_MANAGED, registeredAccount.getCapabilities());
    }

    @MediumTest
    public void testSortSimFirst() throws Exception {
        ComponentName componentA = new ComponentName("a", "a");
        ComponentName componentB = new ComponentName("b", "b");
        mComponentContextFixture.addConnectionService(componentA,
                Mockito.mock(IConnectionService.class));
        mComponentContextFixture.addConnectionService(componentB,
                Mockito.mock(IConnectionService.class));

        PhoneAccount simAccount = new PhoneAccount.Builder(
                makeQuickAccountHandle(componentB, "2"), "2")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER |
                        PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)
                .setIsEnabled(true)
                .build();

        PhoneAccount nonSimAccount = new PhoneAccount.Builder(
                makeQuickAccountHandle(componentA, "1"), "1")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .setIsEnabled(true)
                .build();

        registerAndEnableAccount(nonSimAccount);
        registerAndEnableAccount(simAccount);

        List<PhoneAccount> accounts = mRegistrar.getAllPhoneAccounts(Process.myUserHandle());
        assertTrue(accounts.get(0).getLabel().toString().equals("2"));
        assertTrue(accounts.get(1).getLabel().toString().equals("1"));
    }

    @MediumTest
    public void testSortBySortOrder() throws Exception {
        ComponentName componentA = new ComponentName("a", "a");
        ComponentName componentB = new ComponentName("b", "b");
        ComponentName componentC = new ComponentName("c", "c");
        mComponentContextFixture.addConnectionService(componentA,
                Mockito.mock(IConnectionService.class));
        mComponentContextFixture.addConnectionService(componentB,
                Mockito.mock(IConnectionService.class));
        mComponentContextFixture.addConnectionService(componentC,
                Mockito.mock(IConnectionService.class));

        PhoneAccount account1 = new PhoneAccount.Builder(
                makeQuickAccountHandle(componentA, "c"), "c")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .setExtras(Bundle.forPair(PhoneAccount.EXTRA_SORT_ORDER, "A"))
                .build();

        PhoneAccount account2 = new PhoneAccount.Builder(
                makeQuickAccountHandle(componentB, "b"), "b")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .setExtras(Bundle.forPair(PhoneAccount.EXTRA_SORT_ORDER, "B"))
                .build();

        PhoneAccount account3 = new PhoneAccount.Builder(
                makeQuickAccountHandle(componentC, "c"), "a")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .build();

        registerAndEnableAccount(account3);
        registerAndEnableAccount(account2);
        registerAndEnableAccount(account1);

        List<PhoneAccount> accounts = mRegistrar.getAllPhoneAccounts(Process.myUserHandle());
        assertTrue(accounts.get(0).getLabel().toString().equals("c"));
        assertTrue(accounts.get(1).getLabel().toString().equals("b"));
        assertTrue(accounts.get(2).getLabel().toString().equals("a"));
    }

    @MediumTest
    public void testSortByLabel() throws Exception {
        ComponentName componentA = new ComponentName("a", "a");
        ComponentName componentB = new ComponentName("b", "b");
        ComponentName componentC = new ComponentName("c", "c");
        mComponentContextFixture.addConnectionService(componentA,
                Mockito.mock(IConnectionService.class));
        mComponentContextFixture.addConnectionService(componentB,
                Mockito.mock(IConnectionService.class));
        mComponentContextFixture.addConnectionService(componentC,
                Mockito.mock(IConnectionService.class));

        PhoneAccount account1 = new PhoneAccount.Builder(makeQuickAccountHandle(componentA, "c"),
                "c")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .build();

        PhoneAccount account2 = new PhoneAccount.Builder(makeQuickAccountHandle(componentB, "b"),
                "b")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .build();

        PhoneAccount account3 = new PhoneAccount.Builder(makeQuickAccountHandle(componentC, "a"),
                "a")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .build();

        registerAndEnableAccount(account1);
        registerAndEnableAccount(account2);
        registerAndEnableAccount(account3);

        List<PhoneAccount> accounts = mRegistrar.getAllPhoneAccounts(Process.myUserHandle());
        assertTrue(accounts.get(0).getLabel().toString().equals("a"));
        assertTrue(accounts.get(1).getLabel().toString().equals("b"));
        assertTrue(accounts.get(2).getLabel().toString().equals("c"));
    }

    @MediumTest
    public void testSortAll() throws Exception {
        ComponentName componentA = new ComponentName("a", "a");
        ComponentName componentB = new ComponentName("b", "b");
        ComponentName componentC = new ComponentName("c", "c");
        ComponentName componentW = new ComponentName("w", "w");
        ComponentName componentX = new ComponentName("x", "x");
        ComponentName componentY = new ComponentName("y", "y");
        ComponentName componentZ = new ComponentName("z", "z");
        mComponentContextFixture.addConnectionService(componentA,
                Mockito.mock(IConnectionService.class));
        mComponentContextFixture.addConnectionService(componentB,
                Mockito.mock(IConnectionService.class));
        mComponentContextFixture.addConnectionService(componentC,
                Mockito.mock(IConnectionService.class));
        mComponentContextFixture.addConnectionService(componentW,
                Mockito.mock(IConnectionService.class));
        mComponentContextFixture.addConnectionService(componentX,
                Mockito.mock(IConnectionService.class));
        mComponentContextFixture.addConnectionService(componentY,
                Mockito.mock(IConnectionService.class));
        mComponentContextFixture.addConnectionService(componentZ,
                Mockito.mock(IConnectionService.class));
        PhoneAccount account1 = new PhoneAccount.Builder(makeQuickAccountHandle(
                makeQuickConnectionServiceComponentName(), "y"), "y")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER |
                        PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)
                .setExtras(Bundle.forPair(PhoneAccount.EXTRA_SORT_ORDER, "2"))
                .build();

        PhoneAccount account2 = new PhoneAccount.Builder(makeQuickAccountHandle(
                makeQuickConnectionServiceComponentName(), "z"), "z")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER |
                        PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)
                .setExtras(Bundle.forPair(PhoneAccount.EXTRA_SORT_ORDER, "1"))
                .build();

        PhoneAccount account3 = new PhoneAccount.Builder(makeQuickAccountHandle(
                makeQuickConnectionServiceComponentName(), "x"), "x")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER |
                        PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)
                .build();

        PhoneAccount account4 = new PhoneAccount.Builder(makeQuickAccountHandle(
                makeQuickConnectionServiceComponentName(), "w"), "w")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER |
                        PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)
                .build();

        PhoneAccount account5 = new PhoneAccount.Builder(makeQuickAccountHandle(
                makeQuickConnectionServiceComponentName(), "b"), "b")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .build();

        PhoneAccount account6 = new PhoneAccount.Builder(makeQuickAccountHandle(
                makeQuickConnectionServiceComponentName(), "c"), "a")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .build();

        registerAndEnableAccount(account1);
        registerAndEnableAccount(account2);
        registerAndEnableAccount(account3);
        registerAndEnableAccount(account4);
        registerAndEnableAccount(account5);
        registerAndEnableAccount(account6);

        List<PhoneAccount> accounts = mRegistrar.getAllPhoneAccounts(Process.myUserHandle());
        // Sim accts ordered by sort order first
        assertTrue(accounts.get(0).getLabel().toString().equals("z"));
        assertTrue(accounts.get(1).getLabel().toString().equals("y"));

        // Sim accts with no sort order next
        assertTrue(accounts.get(2).getLabel().toString().equals("w"));
        assertTrue(accounts.get(3).getLabel().toString().equals("x"));

        // Other accts sorted by label next
        assertTrue(accounts.get(4).getLabel().toString().equals("a"));
        assertTrue(accounts.get(5).getLabel().toString().equals("b"));
    }

    private static ComponentName makeQuickConnectionServiceComponentName() {
        return new ComponentName(
                "com.android.server.telecom.tests",
                "com.android.server.telecom.tests.MockConnectionService");
    }

    private static PhoneAccountHandle makeQuickAccountHandle(String id) {
        return makeQuickAccountHandle(makeQuickConnectionServiceComponentName(), id);
    }

    private static PhoneAccountHandle makeQuickAccountHandle(ComponentName name, String id) {
        return new PhoneAccountHandle(name, id, Process.myUserHandle());
    }

    private PhoneAccount.Builder makeQuickAccountBuilder(String id, int idx) {
        return new PhoneAccount.Builder(
                makeQuickAccountHandle(id),
                "label" + idx);
    }

    private PhoneAccount makeQuickAccount(String id, int idx) {
        return makeQuickAccountBuilder(id, idx)
                .setAddress(Uri.parse("http://foo.com/" + idx))
                .setSubscriptionAddress(Uri.parse("tel:555-000" + idx))
                .setCapabilities(idx)
                .setIcon(Icon.createWithResource(
                            "com.android.server.telecom.tests", R.drawable.stat_sys_phone_call))
                .setShortDescription("desc" + idx)
                .setIsEnabled(true)
                .build();
    }

    private static void roundTripPhoneAccount(PhoneAccount original) throws Exception {
        PhoneAccount copy = null;

        {
            Parcel parcel = Parcel.obtain();
            parcel.writeParcelable(original, 0);
            parcel.setDataPosition(0);
            copy = parcel.readParcelable(PhoneAccountRegistrarTest.class.getClassLoader());
            parcel.recycle();
        }

        assertPhoneAccountEquals(original, copy);
    }

    private static <T> T roundTripXml(
            Object self,
            T input,
            PhoneAccountRegistrar.XmlSerialization<T> xml,
            Context context)
            throws Exception {
        Log.d(self, "Input = %s", input);

        byte[] data;
        {
            XmlSerializer serializer = new FastXmlSerializer();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            serializer.setOutput(new BufferedOutputStream(baos), "utf-8");
            xml.writeToXml(input, serializer, context);
            serializer.flush();
            data = baos.toByteArray();
        }

        Log.i(self, "====== XML data ======\n%s", new String(data));

        T result = null;
        {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new BufferedInputStream(new ByteArrayInputStream(data)), null);
            parser.nextTag();
            result = xml.readFromXml(parser, MAX_VERSION, context);
        }

        Log.i(self, "result = " + result);

        return result;
    }

    private static void assertPhoneAccountHandleEquals(PhoneAccountHandle a, PhoneAccountHandle b) {
        if (a != b) {
            assertEquals(
                    a.getComponentName().getPackageName(),
                    b.getComponentName().getPackageName());
            assertEquals(
                    a.getComponentName().getClassName(),
                    b.getComponentName().getClassName());
            assertEquals(a.getId(), b.getId());
        }
    }

    private static void assertIconEquals(Icon a, Icon b) {
        if (a != b) {
            if (a != null && b != null) {
                assertEquals(a.toString(), b.toString());
            } else {
                fail("Icons not equal: " + a + ", " + b);
            }
        }
    }

    private static void assertDefaultPhoneAccountHandleEquals(DefaultPhoneAccountHandle a,
            DefaultPhoneAccountHandle b) {
        if (a != b) {
            if (a!= null && b != null) {
                assertEquals(a.userHandle, b.userHandle);
                assertPhoneAccountHandleEquals(a.phoneAccountHandle, b.phoneAccountHandle);
            } else {
                fail("Default phone account handles are not equal: " + a + ", " + b);
            }
        }
    }

    private static void assertPhoneAccountEquals(PhoneAccount a, PhoneAccount b) {
        if (a != b) {
            if (a != null && b != null) {
                assertPhoneAccountHandleEquals(a.getAccountHandle(), b.getAccountHandle());
                assertEquals(a.getAddress(), b.getAddress());
                assertEquals(a.getSubscriptionAddress(), b.getSubscriptionAddress());
                assertEquals(a.getCapabilities(), b.getCapabilities());
                assertIconEquals(a.getIcon(), b.getIcon());
                assertEquals(a.getHighlightColor(), b.getHighlightColor());
                assertEquals(a.getLabel(), b.getLabel());
                assertEquals(a.getShortDescription(), b.getShortDescription());
                assertEquals(a.getSupportedUriSchemes(), b.getSupportedUriSchemes());
                assertBundlesEqual(a.getExtras(), b.getExtras());
                assertEquals(a.isEnabled(), b.isEnabled());
            } else {
                fail("Phone accounts not equal: " + a + ", " + b);
            }
        }
    }

    private static void assertBundlesEqual(Bundle a, Bundle b) {
        if (a == null && b == null) {
            return;
        }

        assertNotNull(a);
        assertNotNull(b);
        Set<String> keySetA = a.keySet();
        Set<String> keySetB = b.keySet();

        assertTrue("Bundle keys not the same", keySetA.containsAll(keySetB));
        assertTrue("Bundle keys not the same", keySetB.containsAll(keySetA));

        for (String keyA : keySetA) {
            assertEquals("Bundle value not the same", a.get(keyA), b.get(keyA));
        }
    }

    private static void assertStateEquals(
            PhoneAccountRegistrar.State a, PhoneAccountRegistrar.State b) {
        assertEquals(a.defaultOutgoingAccountHandles.size(),
                b.defaultOutgoingAccountHandles.size());
        for (int i = 0; i < a.defaultOutgoingAccountHandles.size(); i++) {
            assertDefaultPhoneAccountHandleEquals(a.defaultOutgoingAccountHandles.get(i),
                    b.defaultOutgoingAccountHandles.get(i));
        }
        assertEquals(a.accounts.size(), b.accounts.size());
        for (int i = 0; i < a.accounts.size(); i++) {
            assertPhoneAccountEquals(a.accounts.get(i), b.accounts.get(i));
        }
    }

    private PhoneAccountRegistrar.State makeQuickState() {
        PhoneAccountRegistrar.State s = new PhoneAccountRegistrar.State();
        s.accounts.add(makeQuickAccount("id0", 0));
        s.accounts.add(makeQuickAccount("id1", 1));
        s.accounts.add(makeQuickAccount("id2", 2));
        PhoneAccountHandle phoneAccountHandle = new PhoneAccountHandle(
                new ComponentName("pkg0", "cls0"), "id0");
        UserHandle userHandle = phoneAccountHandle.getUserHandle();
        s.defaultOutgoingAccountHandles
                .put(userHandle, new DefaultPhoneAccountHandle(userHandle, phoneAccountHandle,
                        "testGroup"));
        return s;
    }
}
