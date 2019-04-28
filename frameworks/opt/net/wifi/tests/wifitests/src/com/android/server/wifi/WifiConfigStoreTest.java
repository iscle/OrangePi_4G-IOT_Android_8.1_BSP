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

package com.android.server.wifi;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.app.test.TestAlarmManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiConfiguration;
import android.os.test.TestLooper;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.server.wifi.WifiConfigStore.StoreFile;
import com.android.server.wifi.util.XmlUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Unit tests for {@link com.android.server.wifi.WifiConfigStore}.
 */
@SmallTest
public class WifiConfigStoreTest {
    // Store file content without any data.
    private static final String EMPTY_FILE_CONTENT =
            "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n"
            + "<WifiConfigStoreData>\n"
            + "<int name=\"Version\" value=\"1\" />\n"
            + "</WifiConfigStoreData>\n";

    private static final String TEST_USER_DATA = "UserData";
    private static final String TEST_SHARE_DATA = "ShareData";
    private static final String TEST_CREATOR_NAME = "CreatorName";

    private static final String TEST_DATA_XML_STRING_FORMAT =
            "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n"
                    + "<WifiConfigStoreData>\n"
                    + "<int name=\"Version\" value=\"1\" />\n"
                    + "<NetworkList>\n"
                    + "<Network>\n"
                    + "<WifiConfiguration>\n"
                    + "<string name=\"ConfigKey\">%s</string>\n"
                    + "<string name=\"SSID\">%s</string>\n"
                    + "<null name=\"BSSID\" />\n"
                    + "<null name=\"PreSharedKey\" />\n"
                    + "<null name=\"WEPKeys\" />\n"
                    + "<int name=\"WEPTxKeyIndex\" value=\"0\" />\n"
                    + "<boolean name=\"HiddenSSID\" value=\"false\" />\n"
                    + "<boolean name=\"RequirePMF\" value=\"false\" />\n"
                    + "<byte-array name=\"AllowedKeyMgmt\" num=\"1\">01</byte-array>\n"
                    + "<byte-array name=\"AllowedProtocols\" num=\"0\"></byte-array>\n"
                    + "<byte-array name=\"AllowedAuthAlgos\" num=\"0\"></byte-array>\n"
                    + "<byte-array name=\"AllowedGroupCiphers\" num=\"0\"></byte-array>\n"
                    + "<byte-array name=\"AllowedPairwiseCiphers\" num=\"0\"></byte-array>\n"
                    + "<boolean name=\"Shared\" value=\"%s\" />\n"
                    + "<int name=\"Status\" value=\"2\" />\n"
                    + "<null name=\"FQDN\" />\n"
                    + "<null name=\"ProviderFriendlyName\" />\n"
                    + "<null name=\"LinkedNetworksList\" />\n"
                    + "<null name=\"DefaultGwMacAddress\" />\n"
                    + "<boolean name=\"ValidatedInternetAccess\" value=\"false\" />\n"
                    + "<boolean name=\"NoInternetAccessExpected\" value=\"false\" />\n"
                    + "<int name=\"UserApproved\" value=\"0\" />\n"
                    + "<boolean name=\"MeteredHint\" value=\"false\" />\n"
                    + "<int name=\"MeteredOverride\" value=\"0\" />\n"
                    + "<boolean name=\"UseExternalScores\" value=\"false\" />\n"
                    + "<int name=\"NumAssociation\" value=\"0\" />\n"
                    + "<int name=\"CreatorUid\" value=\"%d\" />\n"
                    + "<string name=\"CreatorName\">%s</string>\n"
                    + "<null name=\"CreationTime\" />\n"
                    + "<int name=\"LastUpdateUid\" value=\"-1\" />\n"
                    + "<null name=\"LastUpdateName\" />\n"
                    + "<int name=\"LastConnectUid\" value=\"0\" />\n"
                    + "<boolean name=\"IsLegacyPasspointConfig\" value=\"false\" />\n"
                    + "<long-array name=\"RoamingConsortiumOIs\" num=\"0\" />\n"
                    + "</WifiConfiguration>\n"
                    + "<NetworkStatus>\n"
                    + "<string name=\"SelectionStatus\">NETWORK_SELECTION_ENABLED</string>\n"
                    + "<string name=\"DisableReason\">NETWORK_SELECTION_ENABLE</string>\n"
                    + "<null name=\"ConnectChoice\" />\n"
                    + "<long name=\"ConnectChoiceTimeStamp\" value=\"-1\" />\n"
                    + "<boolean name=\"HasEverConnected\" value=\"false\" />\n"
                    + "</NetworkStatus>\n"
                    + "<IpConfiguration>\n"
                    + "<string name=\"IpAssignment\">DHCP</string>\n"
                    + "<string name=\"ProxySettings\">NONE</string>\n"
                    + "</IpConfiguration>\n"
                    + "</Network>\n"
                    + "</NetworkList>\n"
                    + "<DeletedEphemeralSSIDList>\n"
                    + "<set name=\"SSIDList\">\n"
                    + "<string>%s</string>\n"
                    + "</set>\n"
                    + "</DeletedEphemeralSSIDList>\n"
                    + "</WifiConfigStoreData>\n";

    // Test mocks
    @Mock private Context mContext;
    @Mock private PackageManager mPackageManager;
    private TestAlarmManager mAlarmManager;
    private TestLooper mLooper;
    @Mock private Clock mClock;
    private MockStoreFile mSharedStore;
    private MockStoreFile mUserStore;
    private MockStoreData mStoreData;

    /**
     * Test instance of WifiConfigStore.
     */
    private WifiConfigStore mWifiConfigStore;

    /**
     * Setup mocks before the test starts.
     */
    private void setupMocks() throws Exception {
        MockitoAnnotations.initMocks(this);
        mAlarmManager = new TestAlarmManager();
        mLooper = new TestLooper();
        when(mContext.getSystemService(Context.ALARM_SERVICE))
                .thenReturn(mAlarmManager.getAlarmManager());
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mPackageManager.getNameForUid(anyInt())).thenReturn(TEST_CREATOR_NAME);
        mUserStore = new MockStoreFile();
        mSharedStore = new MockStoreFile();
        mStoreData = new MockStoreData();
    }

    /**
     * Setup the test environment.
     */
    @Before
    public void setUp() throws Exception {
        setupMocks();

        mWifiConfigStore = new WifiConfigStore(mContext, mLooper.getLooper(), mClock, mSharedStore);
        // Enable verbose logging before tests.
        mWifiConfigStore.enableVerboseLogging(true);
    }

    /**
     * Called after each test
     */
    @After
    public void cleanup() {
        validateMockitoUsage();
    }

    /**
     * Verify the contents of the config file with empty data.  The data content should be the
     * same as {@link #EMPTY_FILE_CONTENT}.
     *
     * @throws Exception
     */
    @Test
    public void testWriteWithEmptyData() throws Exception {
        // Perform force write to both share and user store file.
        mWifiConfigStore.switchUserStoreAndRead(mUserStore);
        mWifiConfigStore.write(true);

        assertFalse(mAlarmManager.isPending(WifiConfigStore.BUFFERED_WRITE_ALARM_TAG));
        assertTrue(mSharedStore.isStoreWritten());
        assertTrue(mUserStore.isStoreWritten());
        assertTrue(Arrays.equals(EMPTY_FILE_CONTENT.getBytes(StandardCharsets.UTF_8),
                mSharedStore.getStoreBytes()));
        assertTrue(Arrays.equals(EMPTY_FILE_CONTENT.getBytes(StandardCharsets.UTF_8),
                mUserStore.getStoreBytes()));
    }

    /**
     * Tests the write API with the force flag set to true.
     * Expected behavior: This should trigger an immediate write to the store files and no alarms
     * should be started.
     */
    @Test
    public void testForceWrite() throws Exception {
        mWifiConfigStore.switchUserStoreAndRead(mUserStore);
        mWifiConfigStore.write(true);

        assertFalse(mAlarmManager.isPending(WifiConfigStore.BUFFERED_WRITE_ALARM_TAG));
        assertTrue(mSharedStore.isStoreWritten());
        assertTrue(mUserStore.isStoreWritten());
    }

    /**
     * Tests the write API with the force flag set to false.
     * Expected behavior: This should set an alarm to write to the store files.
     */
    @Test
    public void testBufferedWrite() throws Exception {
        mWifiConfigStore.switchUserStoreAndRead(mUserStore);
        mWifiConfigStore.write(false);

        assertTrue(mAlarmManager.isPending(WifiConfigStore.BUFFERED_WRITE_ALARM_TAG));
        assertFalse(mSharedStore.isStoreWritten());
        assertFalse(mUserStore.isStoreWritten());

        // Now send the alarm and ensure that the writes happen.
        mAlarmManager.dispatch(WifiConfigStore.BUFFERED_WRITE_ALARM_TAG);
        mLooper.dispatchAll();
        assertTrue(mSharedStore.isStoreWritten());
        assertTrue(mUserStore.isStoreWritten());
    }

    /**
     * Tests the force write after a buffered write.
     * Expected behaviour: The force write should override the previous buffered write and stop the
     * buffer write alarms.
     */
    @Test
    public void testForceWriteAfterBufferedWrite() throws Exception {
        // Register a test data container with bogus data.
        mWifiConfigStore.registerStoreData(mStoreData);
        mStoreData.setShareData("abcds");
        mStoreData.setUserData("asdfa");

        // Perform buffered write for both user and share store file.
        mWifiConfigStore.switchUserStoreAndRead(mUserStore);
        mWifiConfigStore.write(false);

        assertTrue(mAlarmManager.isPending(WifiConfigStore.BUFFERED_WRITE_ALARM_TAG));
        assertFalse(mSharedStore.isStoreWritten());
        assertFalse(mUserStore.isStoreWritten());

        // Update the container with new set of data. The send a force write and ensure that the
        // writes have been performed and alarms have been stopped and updated data are written.
        mStoreData.setUserData(TEST_USER_DATA);
        mStoreData.setShareData(TEST_SHARE_DATA);
        mWifiConfigStore.write(true);

        assertFalse(mAlarmManager.isPending(WifiConfigStore.BUFFERED_WRITE_ALARM_TAG));
        assertTrue(mSharedStore.isStoreWritten());
        assertTrue(mUserStore.isStoreWritten());

        // Verify correct data are loaded to the data container after a read.
        mWifiConfigStore.read();
        assertEquals(TEST_USER_DATA, mStoreData.getUserData());
        assertEquals(TEST_SHARE_DATA, mStoreData.getShareData());
    }

    /**
     * Tests the read API behaviour after a write to the store files.
     * Expected behaviour: The read should return the same data that was last written.
     */
    @Test
    public void testReadAfterWrite() throws Exception {
        // Register data container.
        mWifiConfigStore.registerStoreData(mStoreData);

        // Read both share and user config store.
        mWifiConfigStore.switchUserStoreAndRead(mUserStore);

        // Verify no data is read.
        assertNull(mStoreData.getUserData());
        assertNull(mStoreData.getShareData());

        // Write share and user data.
        mStoreData.setUserData(TEST_USER_DATA);
        mStoreData.setShareData(TEST_SHARE_DATA);
        mWifiConfigStore.write(true);

        // Read and verify the data content in the data container.
        mWifiConfigStore.read();
        assertEquals(TEST_USER_DATA, mStoreData.getUserData());
        assertEquals(TEST_SHARE_DATA, mStoreData.getShareData());
    }

    /**
     * Tests the read API behaviour when there is no store files on the device.
     * Expected behaviour: The read should return an empty store data instance when the file not
     * found exception is raised.
     */
    @Test
    public void testReadWithNoStoreFile() throws Exception {
        // Reading the mock store without a write should simulate the file not found case because
        // |readRawData| would return null.
        mWifiConfigStore.registerStoreData(mStoreData);
        assertFalse(mWifiConfigStore.areStoresPresent());
        mWifiConfigStore.read();

        // Empty data.
        assertNull(mStoreData.getUserData());
        assertNull(mStoreData.getShareData());
    }

    /**
     * Tests the read API behaviour after a write to the shared store file when the user
     * store file is null.
     * Expected behaviour: The read should return the same data that was last written.
     */
    @Test
    public void testReadAfterWriteWithNoUserStore() throws Exception {
        // Setup data container.
        mWifiConfigStore.registerStoreData(mStoreData);
        mStoreData.setUserData(TEST_USER_DATA);
        mStoreData.setShareData(TEST_SHARE_DATA);

        // Perform write for the share store file.
        mWifiConfigStore.write(true);
        mWifiConfigStore.read();
        // Verify data content for both user and share data.
        assertEquals(TEST_SHARE_DATA, mStoreData.getShareData());
        assertNull(mStoreData.getUserData());
    }

    /**
     * Verifies that a read operation will reset the data in the data container, to avoid
     * any stale data from previous read.
     *
     * @throws Exception
     */
    @Test
    public void testReadWillResetStoreData() throws Exception {
        // Register and setup store data.
        mWifiConfigStore.registerStoreData(mStoreData);

        // Perform force write with empty data content to both user and share store file.
        mWifiConfigStore.switchUserStoreAndRead(mUserStore);
        mWifiConfigStore.write(true);

        // Setup data container with some value.
        mStoreData.setUserData(TEST_USER_DATA);
        mStoreData.setShareData(TEST_SHARE_DATA);

        // Perform read of both user and share store file and verify data in the data container
        // is in sync (empty) with what is in the file.
        mWifiConfigStore.read();
        assertNull(mStoreData.getShareData());
        assertNull(mStoreData.getUserData());
    }

    /**
     * Verify that a store file contained WiFi configuration store data (network list and
     * deleted ephemeral SSID list) using the predefined test XML data is read and parsed
     * correctly.
     *
     * @throws Exception
     */
    @Test
    public void testReadWifiConfigStoreData() throws Exception {
        // Setup network list.
        NetworkListStoreData networkList = new NetworkListStoreData(mContext);
        mWifiConfigStore.registerStoreData(networkList);
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        openNetwork.creatorName = TEST_CREATOR_NAME;
        openNetwork.setIpConfiguration(
                WifiConfigurationTestUtil.createDHCPIpConfigurationWithNoProxy());
        List<WifiConfiguration> userConfigs = new ArrayList<>();
        userConfigs.add(openNetwork);

        // Setup deleted ephemeral SSID list.
        DeletedEphemeralSsidsStoreData deletedEphemeralSsids =
                new DeletedEphemeralSsidsStoreData();
        mWifiConfigStore.registerStoreData(deletedEphemeralSsids);
        String testSsid = "Test SSID";
        Set<String> ssidList = new HashSet<>();
        ssidList.add(testSsid);

        // Setup user store XML bytes.
        String xmlString = String.format(TEST_DATA_XML_STRING_FORMAT,
                openNetwork.configKey().replaceAll("\"", "&quot;"),
                openNetwork.SSID.replaceAll("\"", "&quot;"),
                openNetwork.shared, openNetwork.creatorUid, openNetwork.creatorName, testSsid);
        byte[] xmlBytes = xmlString.getBytes(StandardCharsets.UTF_8);
        mUserStore.storeRawDataToWrite(xmlBytes);

        mWifiConfigStore.switchUserStoreAndRead(mUserStore);
        WifiConfigurationTestUtil.assertConfigurationsEqualForConfigStore(
                userConfigs, networkList.getUserConfigurations());
        assertEquals(ssidList, deletedEphemeralSsids.getSsidList());
    }

    /**
     * Verify that the WiFi configuration store data containing network list and deleted
     * ephemeral SSID list are serialized correctly, matches the predefined test XML data.
     *
     * @throws Exception
     */
    @Test
    public void testWriteWifiConfigStoreData() throws Exception {
        // Setup user store.
        mWifiConfigStore.switchUserStoreAndRead(mUserStore);

        // Setup network list store data.
        NetworkListStoreData networkList = new NetworkListStoreData(mContext);
        mWifiConfigStore.registerStoreData(networkList);
        WifiConfiguration openNetwork = WifiConfigurationTestUtil.createOpenNetwork();
        openNetwork.creatorName = TEST_CREATOR_NAME;
        openNetwork.setIpConfiguration(
                WifiConfigurationTestUtil.createDHCPIpConfigurationWithNoProxy());
        List<WifiConfiguration> userConfigs = new ArrayList<>();
        userConfigs.add(openNetwork);
        networkList.setUserConfigurations(userConfigs);

        // Setup deleted ephemeral SSID list store data.
        DeletedEphemeralSsidsStoreData deletedEphemeralSsids =
                new DeletedEphemeralSsidsStoreData();
        mWifiConfigStore.registerStoreData(deletedEphemeralSsids);
        String testSsid = "Test SSID";
        Set<String> ssidList = new HashSet<>();
        ssidList.add(testSsid);
        deletedEphemeralSsids.setSsidList(ssidList);

        // Setup expected XML bytes.
        String xmlString = String.format(TEST_DATA_XML_STRING_FORMAT,
                openNetwork.configKey().replaceAll("\"", "&quot;"),
                openNetwork.SSID.replaceAll("\"", "&quot;"),
                openNetwork.shared, openNetwork.creatorUid, openNetwork.creatorName, testSsid);
        byte[] xmlBytes = xmlString.getBytes(StandardCharsets.UTF_8);

        mWifiConfigStore.write(true);
        assertEquals(xmlBytes.length, mUserStore.getStoreBytes().length);
        // Verify the user store content.
        assertTrue(Arrays.equals(xmlBytes, mUserStore.getStoreBytes()));
    }

    /**
     * Verify that a XmlPullParserException will be thrown when reading an user store file
     * containing unknown data.
     *
     * @throws Exception
     */
    @Test(expected = XmlPullParserException.class)
    public void testReadUserStoreContainedUnknownData() throws Exception {
        String storeFileData =
                "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n"
                        + "<WifiConfigStoreData>\n"
                        + "<int name=\"Version\" value=\"1\" />\n"
                        + "<UnknownTag>\n"    // No StoreData registered to handle this tag.
                        + "</UnknownTag>\n"
                        + "</WifiConfigStoreData>\n";
        mUserStore.storeRawDataToWrite(storeFileData.getBytes(StandardCharsets.UTF_8));
        mWifiConfigStore.switchUserStoreAndRead(mUserStore);
    }

    /**
     * Verify that a XmlPullParserException will be thrown when reading the share store file
     * containing unknown data.
     *
     * @throws Exception
     */
    @Test(expected = XmlPullParserException.class)
    public void testReadShareStoreContainedUnknownData() throws Exception {
        String storeFileData =
                "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n"
                        + "<WifiConfigStoreData>\n"
                        + "<int name=\"Version\" value=\"1\" />\n"
                        + "<UnknownTag>\n"    // No StoreData registered to handle this tag.
                        + "</UnknownTag>\n"
                        + "</WifiConfigStoreData>\n";
        mSharedStore.storeRawDataToWrite(storeFileData.getBytes(StandardCharsets.UTF_8));
        mWifiConfigStore.read();
    }

    /**
     * Mock Store File to redirect all file writes from WifiConfigStore to local buffers.
     * This can be used to examine the data output by WifiConfigStore.
     */
    private class MockStoreFile extends StoreFile {
        private byte[] mStoreBytes;
        private boolean mStoreWritten;

        public MockStoreFile() {
            super(new File("MockStoreFile"));
        }

        @Override
        public byte[] readRawData() {
            return mStoreBytes;
        }

        @Override
        public void storeRawDataToWrite(byte[] data) {
            mStoreBytes = data;
            mStoreWritten = false;
        }

        @Override
        public boolean exists() {
            return (mStoreBytes != null);
        }

        @Override
        public void writeBufferedRawData() {
            mStoreWritten = true;
        }

        public byte[] getStoreBytes() {
            return mStoreBytes;
        }

        public boolean isStoreWritten() {
            return mStoreWritten;
        }
    }

    /**
     * Mock data container for providing test data for the store file.
     */
    private class MockStoreData implements WifiConfigStore.StoreData {
        private static final String XML_TAG_TEST_HEADER = "TestHeader";
        private static final String XML_TAG_TEST_DATA = "TestData";

        private String mShareData;
        private String mUserData;

        MockStoreData() {}

        @Override
        public void serializeData(XmlSerializer out, boolean shared)
                throws XmlPullParserException, IOException {
            if (shared) {
                XmlUtil.writeNextValue(out, XML_TAG_TEST_DATA, mShareData);
            } else {
                XmlUtil.writeNextValue(out, XML_TAG_TEST_DATA, mUserData);
            }
        }

        @Override
        public void deserializeData(XmlPullParser in, int outerTagDepth, boolean shared)
                throws XmlPullParserException, IOException {
            if (shared) {
                mShareData = (String) XmlUtil.readNextValueWithName(in, XML_TAG_TEST_DATA);
            } else {
                mUserData = (String) XmlUtil.readNextValueWithName(in, XML_TAG_TEST_DATA);
            }
        }

        @Override
        public void resetData(boolean shared) {
            if (shared) {
                mShareData = null;
            } else {
                mUserData = null;
            }
        }

        @Override
        public String getName() {
            return XML_TAG_TEST_HEADER;
        }

        @Override
        public boolean supportShareData() {
            return true;
        }

        public String getShareData() {
            return mShareData;
        }

        public void setShareData(String shareData) {
            mShareData = shareData;
        }

        public String getUserData() {
            return mUserData;
        }

        public void setUserData(String userData) {
            mUserData = userData;
        }
    }
}
