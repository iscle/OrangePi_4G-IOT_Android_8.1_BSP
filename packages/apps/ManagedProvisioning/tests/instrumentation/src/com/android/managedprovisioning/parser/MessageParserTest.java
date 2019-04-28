/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.managedprovisioning.parser;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_SHAREABLE_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_USER;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME;
import static android.app.admin.DevicePolicyManager.MIME_TYPE_PROVISIONING_NFC;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import android.accounts.Account;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.managedprovisioning.common.Utils;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayOutputStream;
import java.util.Locale;
import java.util.Properties;

/** Tests {@link MessageParser} */
@SmallTest
public class MessageParserTest extends AndroidTestCase {
    private static final String TEST_PACKAGE_NAME = "com.afwsamples.testdpc";
    private static final ComponentName TEST_COMPONENT_NAME =
            ComponentName.unflattenFromString(
                    "com.afwsamples.testdpc/com.afwsamples.testdpc.DeviceAdminReceiver");
    private static final long TEST_LOCAL_TIME = 1456939524713L;
    private static final Locale TEST_LOCALE = Locale.UK;
    private static final String TEST_TIME_ZONE = "GMT";
    private static final Integer TEST_MAIN_COLOR = 65280;
    private static final boolean TEST_SKIP_ENCRYPTION = true;
    private static final Account TEST_ACCOUNT_TO_MIGRATE =
            new Account("user@gmail.com", "com.google");

    @Mock
    private Context mContext;

    private Utils mUtils;

    private MessageParser mMessageParser;

    @Override
    public void setUp() {
        // this is necessary for mockito to work
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().toString());

        MockitoAnnotations.initMocks(this);

        mMessageParser = new MessageParser(mContext, mUtils = spy(new Utils()));
    }

    public void test_correctParserUsedToParseNfcIntent() throws Exception {
        // GIVEN a NFC provisioning intent with some supported data.
        Properties props = new Properties();
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        props.setProperty(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, TEST_PACKAGE_NAME);
        props.setProperty(
                EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                TEST_COMPONENT_NAME.flattenToString());
        props.store(stream, "NFC provisioning intent" /* data description */);
        NdefRecord record = NdefRecord.createMime(
                DevicePolicyManager.MIME_TYPE_PROVISIONING_NFC,
                stream.toByteArray());
        NdefMessage ndfMsg = new NdefMessage(new NdefRecord[]{record});

        Intent intent = new Intent(NfcAdapter.ACTION_NDEF_DISCOVERED)
                .setType(MIME_TYPE_PROVISIONING_NFC)
                .putExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, new NdefMessage[]{ndfMsg});

        // WHEN the mMessageParser.getParser is invoked.
        ProvisioningDataParser parser = mMessageParser.getParser(intent);

        // THEN the properties parser is returned.
        assertTrue(parser instanceof PropertiesProvisioningDataParser);
    }

    public void test_correctParserUsedToParseOtherSupportedProvisioningIntent() throws Exception {
        // GIVEN the device admin app is installed.
        doReturn(TEST_COMPONENT_NAME)
                .when(mUtils)
                .findDeviceAdmin(null, TEST_COMPONENT_NAME, mContext);
        // GIVEN a list of supported provisioning actions, except NFC.
        String[] supportedProvisioningActions = new String[] {
                ACTION_PROVISION_MANAGED_DEVICE,
                ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE,
                ACTION_PROVISION_MANAGED_USER,
                ACTION_PROVISION_MANAGED_PROFILE,
                ACTION_PROVISION_MANAGED_SHAREABLE_DEVICE
        };

        for (String provisioningAction : supportedProvisioningActions) {
            Intent intent = new Intent(provisioningAction)
                    .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, TEST_COMPONENT_NAME);

            // WHEN the mMessageParser.getParser is invoked.
            ProvisioningDataParser parser = mMessageParser.getParser(intent);

            // THEN the extras parser is returned.
            assertTrue(parser instanceof ExtrasProvisioningDataParser);
        }
    }
}
