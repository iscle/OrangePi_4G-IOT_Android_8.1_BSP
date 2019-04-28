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

package android.telephony.ims;

import static android.Manifest.permission.MODIFY_PHONE_STATE;
import static android.Manifest.permission.READ_PHONE_STATE;
import static android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE;

import static com.android.internal.telephony.ims.ImsResolver.SERVICE_INTERFACE;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.fail;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.support.test.filters.FlakyTest;
import android.support.test.runner.AndroidJUnit4;
import android.telephony.ims.feature.ImsFeature;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.SparseArray;

import com.android.ims.ImsManager;
import com.android.ims.internal.IImsFeatureStatusCallback;
import com.android.ims.internal.IImsServiceController;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for ImsService
 */
@RunWith(AndroidJUnit4.class)
public class ImsServiceTest {

    private static final int TEST_SLOT_0 = 0;
    private static final int TEST_SLOT_1 = 1;

    private TestImsService mTestImsService;
    private IImsServiceController mTestImsServiceBinder;

    @Mock
    private Context mMockContext;
    @Mock
    private IImsFeatureStatusCallback mTestCallback;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mTestImsService = new TestImsService(mMockContext);
        mTestImsServiceBinder = (IImsServiceController) mTestImsService.onBind(
                new Intent(SERVICE_INTERFACE));
    }

    @After
    public void tearDown() throws Exception {
        mTestImsService = null;
        mTestImsServiceBinder = null;
    }

    @Test
    @SmallTest
    public void testCreateMMTelFeature() throws RemoteException {
        mTestImsServiceBinder.createImsFeature(TEST_SLOT_0, ImsFeature.MMTEL, mTestCallback);
        when(mTestImsService.mSpyMMTelFeature.getFeatureState()).thenReturn(
                ImsFeature.STATE_READY);

        SparseArray<ImsFeature> features = mTestImsService.getImsFeatureMap(TEST_SLOT_0);
        assertEquals(mTestImsService.mSpyMMTelFeature,
                mTestImsService.getImsFeatureFromType(features, ImsFeature.MMTEL));
        // Verify that upon creating a feature, we assign the callback and get the set feature state
        // when querying it.
        verify(mTestImsService.mSpyMMTelFeature).addImsFeatureStatusCallback(eq(mTestCallback));
        assertEquals(ImsFeature.STATE_READY, mTestImsServiceBinder.getFeatureStatus(TEST_SLOT_0,
                ImsFeature.MMTEL));
    }

    @Test
    @SmallTest
    public void testRemoveMMTelFeature() throws RemoteException {
        mTestImsServiceBinder.createImsFeature(TEST_SLOT_0, ImsFeature.MMTEL, mTestCallback);

        mTestImsServiceBinder.removeImsFeature(TEST_SLOT_0, ImsFeature.MMTEL, mTestCallback);

        verify(mTestImsService.mSpyMMTelFeature).notifyFeatureRemoved(eq(0));
        verify(mTestImsService.mSpyMMTelFeature).removeImsFeatureStatusCallback(mTestCallback);
        SparseArray<ImsFeature> features = mTestImsService.getImsFeatureMap(TEST_SLOT_0);
        assertNull(mTestImsService.getImsFeatureFromType(features, ImsFeature.MMTEL));
    }

    @Test
    @SmallTest
    public void testCallMethodOnCreatedFeature() throws RemoteException {
        mTestImsServiceBinder.createImsFeature(TEST_SLOT_0, ImsFeature.MMTEL, mTestCallback);

        mTestImsServiceBinder.isConnected(TEST_SLOT_0, ImsFeature.MMTEL, 0 /*callSessionType*/,
                0 /*callType*/);

        verify(mTestImsService.mSpyMMTelFeature).isConnected(anyInt(), anyInt());
    }

    @Test
    @SmallTest
    public void testCallMethodWithNoCreatedFeature() throws RemoteException {
        mTestImsServiceBinder.createImsFeature(TEST_SLOT_0, ImsFeature.MMTEL, mTestCallback);

        mTestImsServiceBinder.isConnected(TEST_SLOT_1, ImsFeature.MMTEL, 0 /*callSessionType*/,
                0 /*callType*/);

        verify(mTestImsService.mSpyMMTelFeature, never()).isConnected(anyInt(), anyInt());
    }

    @Test
    @SmallTest
    public void testCreateFeatureWithNoPermissions() throws RemoteException {
        doThrow(new SecurityException()).when(mMockContext).enforceCallingOrSelfPermission(
                eq(MODIFY_PHONE_STATE), anyString());

        try {
            mTestImsServiceBinder.createImsFeature(TEST_SLOT_0, ImsFeature.MMTEL, mTestCallback);
            fail();
        } catch (SecurityException e) {
            // Expected
        }
    }

    @FlakyTest
    @Ignore
    @Test
    public void testMethodWithNoPermissions() throws RemoteException {
        when(mMockContext.checkCallingOrSelfPermission(READ_PRIVILEGED_PHONE_STATE)).thenReturn(
                PackageManager.PERMISSION_DENIED);
        doThrow(new SecurityException()).when(mMockContext).enforceCallingOrSelfPermission(
                eq(READ_PHONE_STATE), nullable(String.class));
        mTestImsServiceBinder.createImsFeature(TEST_SLOT_0, ImsFeature.MMTEL, mTestCallback);

        try {
            mTestImsServiceBinder.isConnected(TEST_SLOT_1, ImsFeature.MMTEL, 0 /*callSessionType*/,
                    0 /*callType*/);
            fail();
        } catch (SecurityException e) {
            // Expected
        }

        verify(mTestImsService.mSpyMMTelFeature, never()).isConnected(anyInt(), anyInt());
    }

    /**
     * Tests that the new ImsService still sends the IMS_SERVICE_UP broadcast when the feature is
     * set to ready.
     */
    @Test
    @SmallTest
    public void testImsServiceUpSentCompat() throws RemoteException {
        mTestImsServiceBinder.createImsFeature(TEST_SLOT_0, ImsFeature.MMTEL, mTestCallback);

        mTestImsService.mSpyMMTelFeature.sendSetFeatureState(ImsFeature.STATE_READY);

        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockContext).sendBroadcast(intentCaptor.capture());
        try {
            // Verify IMS_SERVICE_UP is sent
            assertNotNull(intentCaptor.getValue());
            verifyServiceUpSent(intentCaptor.getValue());
        } catch (IndexOutOfBoundsException e) {
            fail("Did not receive all intents");
        }
    }

    /**
     * Tests that the new ImsService still sends the IMS_SERVICE_DOWN broadcast when the feature is
     * set to initializing.
     */
    @Test
    @SmallTest
    public void testImsServiceDownSentCompatInitializing() throws RemoteException {
        mTestImsServiceBinder.createImsFeature(TEST_SLOT_0, ImsFeature.MMTEL, mTestCallback);

        mTestImsService.mSpyMMTelFeature.sendSetFeatureState(ImsFeature.STATE_INITIALIZING);

        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockContext).sendBroadcast(intentCaptor.capture());
        try {
            // IMS_SERVICE_DOWN is sent when the service is STATE_INITIALIZING.
            assertNotNull(intentCaptor.getValue());
            verifyServiceDownSent(intentCaptor.getValue());
        } catch (IndexOutOfBoundsException e) {
            fail("Did not receive all intents");
        }
    }

    private void verifyServiceDownSent(Intent testIntent) {
        assertEquals(ImsManager.ACTION_IMS_SERVICE_DOWN, testIntent.getAction());
        assertEquals(TEST_SLOT_0, testIntent.getIntExtra(ImsManager.EXTRA_PHONE_ID, -1));
    }

    private void verifyServiceUpSent(Intent testIntent) {
        assertEquals(ImsManager.ACTION_IMS_SERVICE_UP, testIntent.getAction());
        assertEquals(TEST_SLOT_0, testIntent.getIntExtra(ImsManager.EXTRA_PHONE_ID, -1));
    }
}
