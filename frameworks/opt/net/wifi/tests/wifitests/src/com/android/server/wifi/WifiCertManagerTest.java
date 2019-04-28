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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.os.UserHandle;
import android.security.Credentials;
import android.security.KeyStore;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;

/**
 * Unit tests for {@link com.android.server.wifi.WifiCertManager}.
 */
@SmallTest
public class WifiCertManagerTest {
    private static final String TAG = "WifiCertManagerTest";
    private byte[] mConfig;
    private String mConfigFile = "";

    @Mock private Context mContext;
    @Rule public TemporaryFolder mTempFolder = new TemporaryFolder();

    public WifiCertManagerTest() {
        mConfig = null;
    }

    @Before
    public void setUp() {
        try {
            File configFile = mTempFolder.newFile();
            mConfigFile = configFile.getAbsolutePath();
            configFile.delete();
        } catch (Exception e) {
            Log.e(TAG, "Failed to construct test", e);
        }
    }

    /**
     * This class is created to avoid mocking file system and KeyStore.
     */
    private class TestWifiCertManager extends WifiCertManager {
        private boolean mAffiliatedUser;

        public TestWifiCertManager(Context context) {
            super(context, mConfigFile);
            mAffiliatedUser = false;
        }

        protected String[] listClientCertsForAllUsers() {
            String prefix = Credentials.USER_PRIVATE_KEY;
            String mockAnswer[] = {prefix + "abc", prefix + "def", prefix + "ghi"};
            return mockAnswer;
        }

        protected byte[] readConfigFile() {
            return mConfig;
        }

        protected void writeConfigFile(byte[] payload) {
            mConfig = payload;
        }

        protected boolean isAffiliatedUser() {
            return mAffiliatedUser;
        }

        public void setAffiliatedUser(boolean value) {
            mAffiliatedUser = value;
        }
    }

    @Test
    public void testEmptyConfigFile() {
        WifiCertManager certManager = new WifiCertManager(mContext, mConfigFile);
        final String[] expected =
                KeyStore.getInstance().list(
                        Credentials.USER_PRIVATE_KEY, UserHandle.myUserId());
        assertArrayEquals(expected, certManager.listClientCertsForCurrentUser());
    }

    @Test
    public void testOperations() {
        TestWifiCertManager certManager = new TestWifiCertManager(mContext);
        final HashSet<String> expected1 = new HashSet<>();
        String prefix = Credentials.USER_PRIVATE_KEY;
        expected1.add(prefix + "abc");
        expected1.add(prefix + "def");
        expected1.add(prefix + "ghi");

        final HashSet<String> expected2 = new HashSet<>();
        expected2.add(prefix + "abc");

        certManager.setAffiliatedUser(false);
        assertEquals(expected1,
                new HashSet<>(Arrays.asList(certManager.listClientCertsForCurrentUser())));

        certManager.hideCertFromUnaffiliatedUsers("def");
        certManager.hideCertFromUnaffiliatedUsers("ghi");
        assertEquals(expected2,
                new HashSet<>(Arrays.asList(certManager.listClientCertsForCurrentUser())));

        certManager.setAffiliatedUser(true);
        assertEquals(expected1,
                new HashSet<>(Arrays.asList(certManager.listClientCertsForCurrentUser())));

        TestWifiCertManager certManager2 = new TestWifiCertManager(mContext);
        certManager2.setAffiliatedUser(false);
        assertEquals(expected2,
                new HashSet<>(Arrays.asList(certManager2.listClientCertsForCurrentUser())));

        certManager2.setAffiliatedUser(true);
        assertEquals(expected1,
                new HashSet<>(Arrays.asList(certManager2.listClientCertsForCurrentUser())));
    }
}
