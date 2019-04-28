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

package com.android.internal.telephony.ims;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.support.test.filters.FlakyTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.Pair;

import com.android.ims.internal.IImsServiceFeatureListener;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;

import java.util.HashSet;

/**
 * Unit tests for ImsServiceController
 */
@RunWith(AndroidJUnit4.class)
@Ignore
public class ImsServiceControllerTest extends ImsTestBase {

    private static final int RETRY_TIMEOUT = 50; // ms

    @Spy TestImsServiceControllerAdapter mMockServiceControllerBinder;
    @Mock IBinder mMockBinder;
    @Mock ImsServiceController.ImsServiceControllerCallbacks mMockCallbacks;
    @Mock IImsServiceFeatureListener mMockProxyCallbacks;
    @Mock Context mMockContext;
    private final ComponentName mTestComponentName = new ComponentName("TestPkg",
            "ImsServiceControllerTest");
    private ImsServiceController mTestImsServiceController;
    private final Handler mTestHandler = new Handler(Looper.getMainLooper());

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        mTestImsServiceController = new ImsServiceController(mMockContext, mTestComponentName,
                mMockCallbacks, mTestHandler);
        mTestImsServiceController.addImsServiceFeatureListener(mMockProxyCallbacks);
        when(mMockContext.bindService(any(), any(), anyInt())).thenReturn(true);
    }


    @After
    @Override
    public void tearDown() throws Exception {
        mTestHandler.removeCallbacksAndMessages(null);
        mTestImsServiceController = null;
        super.tearDown();
    }

    /**
     * Tests that Context.bindService is called with the correct parameters when we call bind.
     */
    @FlakyTest
    @Test
    public void testBindService() {
        HashSet<Pair<Integer, Integer>> testFeatures = new HashSet<>();
        testFeatures.add(new Pair<>(1, 1));
        testFeatures.add(new Pair<>(1, 2));
        ArgumentCaptor<Intent> intentCaptor =
                ArgumentCaptor.forClass(Intent.class);

        assertTrue(mTestImsServiceController.bind(testFeatures));

        int expectedFlags = Context.BIND_AUTO_CREATE | Context.BIND_FOREGROUND_SERVICE
                | Context.BIND_IMPORTANT;
        verify(mMockContext).bindService(intentCaptor.capture(), any(), eq(expectedFlags));
        Intent testIntent = intentCaptor.getValue();
        assertEquals(ImsResolver.SERVICE_INTERFACE, testIntent.getAction());
        assertEquals(mTestComponentName, testIntent.getComponent());
    }

    /**
     * Verify that if bind is called multiple times, we only call bindService once.
     */
    @FlakyTest
    @Test
    public void testBindFailureWhenBound() {
        HashSet<Pair<Integer, Integer>> testFeatures = new HashSet<>();
        testFeatures.add(new Pair<>(1, 1));
        bindAndConnectService(testFeatures);

        // already bound, should return false
        assertFalse(mTestImsServiceController.bind(testFeatures));

        verify(mMockContext, times(1)).bindService(any(), any(), anyInt());
    }

    /**
     * Tests ImsServiceController callbacks are properly called when an ImsService is bound and
     * connected.
     */
    @FlakyTest
    @Test
    public void testBindServiceAndConnected() throws RemoteException {
        HashSet<Pair<Integer, Integer>> testFeatures = new HashSet<>();
        testFeatures.add(new Pair<>(1, 1));
        testFeatures.add(new Pair<>(1, 2));

        bindAndConnectService(testFeatures);

        IBinder binder = mMockServiceControllerBinder.getBinder().asBinder();
        verify(binder).linkToDeath(any(), anyInt());
        verify(mMockServiceControllerBinder).createImsFeature(eq(1), eq(1));
        verify(mMockServiceControllerBinder).createImsFeature(eq(1), eq(2));
        verify(mMockCallbacks).imsServiceFeatureCreated(eq(1), eq(1),
                eq(mTestImsServiceController));
        verify(mMockCallbacks).imsServiceFeatureCreated(eq(1), eq(2),
                eq(mTestImsServiceController));
        verify(mMockProxyCallbacks).imsFeatureCreated(eq(1), eq(1));
        verify(mMockProxyCallbacks).imsFeatureCreated(eq(1), eq(2));
        assertEquals(mMockServiceControllerBinder.getBinder(),
                mTestImsServiceController.getImsServiceControllerBinder());
    }

    /**
     * Tests ImsServiceController callbacks are properly called when an ImsService is bound and
     * connected.
     */
    @FlakyTest
    @Test
    public void testBindServiceAndConnectedDisconnected() throws RemoteException {
        HashSet<Pair<Integer, Integer>> testFeatures = new HashSet<>();
        testFeatures.add(new Pair<>(1, 1));
        testFeatures.add(new Pair<>(1, 2));
        ServiceConnection conn = bindAndConnectService(testFeatures);

        conn.onServiceDisconnected(mTestComponentName);

        IBinder binder = mMockServiceControllerBinder.getBinder().asBinder();
        verify(binder).unlinkToDeath(any(), anyInt());
        // binder already disconnected, removeImsFeatures shouldn't be called.
        verify(mMockServiceControllerBinder, never()).removeImsFeature(anyInt(), anyInt());
        verify(mMockCallbacks).imsServiceFeatureRemoved(eq(1), eq(1),
                eq(mTestImsServiceController));
        verify(mMockCallbacks).imsServiceFeatureRemoved(eq(1), eq(2),
                eq(mTestImsServiceController));
        verify(mMockProxyCallbacks).imsFeatureRemoved(eq(1), eq(1));
        verify(mMockProxyCallbacks).imsFeatureRemoved(eq(1), eq(2));
    }

    /**
     * Tests ImsServiceController callbacks are properly called when an ImsService is bound and
     * connected.
     */
    @FlakyTest
    @Test
    public void testBindServiceBindUnbind() throws RemoteException {
        HashSet<Pair<Integer, Integer>> testFeatures = new HashSet<>();
        testFeatures.add(new Pair<>(1, 1));
        testFeatures.add(new Pair<>(1, 2));
        ServiceConnection conn = bindAndConnectService(testFeatures);

        mTestImsServiceController.unbind();

        verify(mMockContext).unbindService(eq(conn));
        IBinder binder = mMockServiceControllerBinder.getBinder().asBinder();
        verify(binder).unlinkToDeath(any(), anyInt());
        verify(mMockServiceControllerBinder).removeImsFeature(eq(1), eq(1));
        verify(mMockServiceControllerBinder).removeImsFeature(eq(1), eq(2));
        verify(mMockCallbacks).imsServiceFeatureRemoved(eq(1), eq(1),
                eq(mTestImsServiceController));
        verify(mMockCallbacks).imsServiceFeatureRemoved(eq(1), eq(2),
                eq(mTestImsServiceController));
        verify(mMockProxyCallbacks).imsFeatureRemoved(eq(1), eq(1));
        verify(mMockProxyCallbacks).imsFeatureRemoved(eq(1), eq(2));
    }

    /**
     * Ensures that imsServiceFeatureRemoved is called when the binder dies in another process.
     */
    @FlakyTest
    @Test
    public void testBindServiceAndBinderDied() throws RemoteException {
        HashSet<Pair<Integer, Integer>> testFeatures = new HashSet<>();
        testFeatures.add(new Pair<>(1, 1));
        testFeatures.add(new Pair<>(1, 2));
        bindAndConnectService(testFeatures);
        ArgumentCaptor<IBinder.DeathRecipient> deathCaptor =
                ArgumentCaptor.forClass(IBinder.DeathRecipient.class);
        IBinder binder = mMockServiceControllerBinder.getBinder().asBinder();
        verify(binder).linkToDeath(deathCaptor.capture(), anyInt());

        deathCaptor.getValue().binderDied();

        verify(mMockCallbacks).imsServiceFeatureRemoved(eq(1), eq(1),
                eq(mTestImsServiceController));
        verify(mMockCallbacks).imsServiceFeatureRemoved(eq(1), eq(2),
                eq(mTestImsServiceController));
        verify(mMockProxyCallbacks).imsFeatureRemoved(eq(1), eq(1));
        verify(mMockProxyCallbacks).imsFeatureRemoved(eq(1), eq(2));
    }

    /**
     * Ensures ImsService and ImsResolver are notified when a feature is added.
     */
    @FlakyTest
    @Test
    public void testBindServiceAndAddFeature() throws RemoteException {
        HashSet<Pair<Integer, Integer>> testFeatures = new HashSet<>();
        testFeatures.add(new Pair<>(1, 1));
        bindAndConnectService(testFeatures);
        verify(mMockServiceControllerBinder).createImsFeature(eq(1), eq(1));
        verify(mMockCallbacks).imsServiceFeatureCreated(eq(1), eq(1),
                eq(mTestImsServiceController));
        verify(mMockProxyCallbacks).imsFeatureCreated(eq(1), eq(1));
        // Create a new list with an additional item
        HashSet<Pair<Integer, Integer>> testFeaturesWithAddition = new HashSet<>(testFeatures);
        testFeaturesWithAddition.add(new Pair<>(2, 1));

        mTestImsServiceController.changeImsServiceFeatures(testFeaturesWithAddition);

        verify(mMockServiceControllerBinder).createImsFeature(eq(2), eq(1));
        verify(mMockCallbacks).imsServiceFeatureCreated(eq(2), eq(1),
                eq(mTestImsServiceController));
        verify(mMockProxyCallbacks).imsFeatureCreated(eq(2), eq(1));
    }

    /**
     * Ensures ImsService and ImsResolver are notified when a feature is added.
     */
    @FlakyTest
    @Test
    public void testBindServiceAndRemoveFeature() throws RemoteException {
        HashSet<Pair<Integer, Integer>> testFeatures = new HashSet<>();
        testFeatures.add(new Pair<>(1, 1));
        testFeatures.add(new Pair<>(2, 1));
        bindAndConnectService(testFeatures);
        verify(mMockServiceControllerBinder).createImsFeature(eq(1), eq(1));
        verify(mMockCallbacks).imsServiceFeatureCreated(eq(1), eq(1),
                eq(mTestImsServiceController));
        verify(mMockProxyCallbacks).imsFeatureCreated(eq(1), eq(1));
        verify(mMockServiceControllerBinder).createImsFeature(eq(2), eq(1));
        verify(mMockCallbacks).imsServiceFeatureCreated(eq(2), eq(1),
                eq(mTestImsServiceController));
        verify(mMockProxyCallbacks).imsFeatureCreated(eq(2), eq(1));
        // Create a new list with one less item
        HashSet<Pair<Integer, Integer>> testFeaturesWithSubtraction = new HashSet<>(testFeatures);
        testFeaturesWithSubtraction.remove(new Pair<>(2, 1));

        mTestImsServiceController.changeImsServiceFeatures(testFeaturesWithSubtraction);

        verify(mMockServiceControllerBinder).removeImsFeature(eq(2), eq(1));
        verify(mMockCallbacks).imsServiceFeatureRemoved(eq(2), eq(1),
                eq(mTestImsServiceController));
        verify(mMockProxyCallbacks).imsFeatureRemoved(eq(2), eq(1));
    }

    /**
     * Ensures ImsService and ImsResolver are notified when all features are removed.
     */
    @FlakyTest
    @Test
    public void testBindServiceAndRemoveAllFeatures() throws RemoteException {
        HashSet<Pair<Integer, Integer>> testFeatures = new HashSet<>();
        testFeatures.add(new Pair<>(1, 1));
        testFeatures.add(new Pair<>(2, 1));
        bindAndConnectService(testFeatures);
        verify(mMockServiceControllerBinder).createImsFeature(eq(1), eq(1));
        verify(mMockCallbacks).imsServiceFeatureCreated(eq(1), eq(1),
                eq(mTestImsServiceController));
        verify(mMockProxyCallbacks).imsFeatureCreated(eq(1), eq(1));
        verify(mMockServiceControllerBinder).createImsFeature(eq(2), eq(1));
        verify(mMockCallbacks).imsServiceFeatureCreated(eq(2), eq(1),
                eq(mTestImsServiceController));
        verify(mMockProxyCallbacks).imsFeatureCreated(eq(2), eq(1));

        // Create a new empty list
        mTestImsServiceController.changeImsServiceFeatures(new HashSet<>());

        verify(mMockServiceControllerBinder).removeImsFeature(eq(1), eq(1));
        verify(mMockCallbacks).imsServiceFeatureRemoved(eq(1), eq(1),
                eq(mTestImsServiceController));
        verify(mMockProxyCallbacks).imsFeatureRemoved(eq(1), eq(1));
        verify(mMockServiceControllerBinder).removeImsFeature(eq(2), eq(1));
        verify(mMockCallbacks).imsServiceFeatureRemoved(eq(2), eq(1),
                eq(mTestImsServiceController));
        verify(mMockProxyCallbacks).imsFeatureRemoved(eq(2), eq(1));
    }

    /**
     * Verifies that nothing is notified of a feature change if the service is not bound.
     */
    @FlakyTest
    @Test
    public void testBindUnbindServiceAndAddFeature() throws RemoteException {
        HashSet<Pair<Integer, Integer>> testFeatures = new HashSet<>();
        testFeatures.add(new Pair<>(1, 1));
        bindAndConnectService(testFeatures);
        mTestImsServiceController.unbind();
        // Create a new list with an additional item
        HashSet<Pair<Integer, Integer>> testFeaturesWithAddition = new HashSet<>(testFeatures);
        testFeaturesWithAddition.add(new Pair<>(1, 2));

        mTestImsServiceController.changeImsServiceFeatures(testFeaturesWithAddition);

        verify(mMockServiceControllerBinder, never()).createImsFeature(eq(1), eq(2));
        verify(mMockCallbacks, never()).imsServiceFeatureCreated(eq(1), eq(2),
                eq(mTestImsServiceController));
        verify(mMockProxyCallbacks, never()).imsFeatureCreated(eq(1), eq(2));
    }

    /**
     * Verifies that the ImsServiceController automatically tries to bind again after an untimely
     * binder death.
     */
    @FlakyTest
    @Test
    public void testAutoBindAfterBinderDied() throws RemoteException {
        HashSet<Pair<Integer, Integer>> testFeatures = new HashSet<>();
        testFeatures.add(new Pair<>(1, 1));
        testFeatures.add(new Pair<>(1, 2));
        bindAndConnectService(testFeatures);
        mTestImsServiceController.setRebindRetryTime(() -> RETRY_TIMEOUT);

        getDeathRecipient().binderDied();

        waitForHandlerActionDelayed(mTestImsServiceController.getHandler(), RETRY_TIMEOUT,
                2 * RETRY_TIMEOUT);
        // The service should autobind after RETRY_TIMEOUT occurs
        verify(mMockContext, times(2)).bindService(any(), any(), anyInt());
    }

    /**
     * Ensure that bindService has only been called once before automatic rebind occurs.
     */
    @FlakyTest
    @Test
    public void testNoAutoBindBeforeTimeout() throws RemoteException {
        HashSet<Pair<Integer, Integer>> testFeatures = new HashSet<>();
        testFeatures.add(new Pair<>(1, 1));
        testFeatures.add(new Pair<>(1, 2));
        bindAndConnectService(testFeatures);
        mTestImsServiceController.setRebindRetryTime(() -> RETRY_TIMEOUT);

        getDeathRecipient().binderDied();

        // Be sure that there are no binds before the RETRY_TIMEOUT expires
        verify(mMockContext, times(1)).bindService(any(), any(), anyInt());
    }

    /**
     * Ensure that calling unbind stops automatic rebind of the ImsService from occuring.
     */
    @FlakyTest
    @Test
    public void testUnbindCauseAutoBindCancelAfterBinderDied() throws RemoteException {
        HashSet<Pair<Integer, Integer>> testFeatures = new HashSet<>();
        testFeatures.add(new Pair<>(1, 1));
        testFeatures.add(new Pair<>(1, 2));
        bindAndConnectService(testFeatures);
        mTestImsServiceController.setRebindRetryTime(() -> RETRY_TIMEOUT);

        getDeathRecipient().binderDied();
        mTestImsServiceController.unbind();

        waitForHandlerActionDelayed(mTestImsServiceController.getHandler(), RETRY_TIMEOUT,
                2 * RETRY_TIMEOUT);
        // Unbind should stop the autobind from occurring.
        verify(mMockContext, times(1)).bindService(any(), any(), anyInt());
    }

    /**
     * Ensure that calling bind causes the automatic rebinding to be cancelled or not cause another
     * call to bindService.
     */
    @FlakyTest
    @Test
    public void testBindCauseAutoBindCancelAfterBinderDied() throws RemoteException {
        HashSet<Pair<Integer, Integer>> testFeatures = new HashSet<>();
        testFeatures.add(new Pair<>(1, 1));
        testFeatures.add(new Pair<>(1, 2));
        bindAndConnectService(testFeatures);
        mTestImsServiceController.setRebindRetryTime(() -> RETRY_TIMEOUT);
        getDeathRecipient().binderDied();
        mTestImsServiceController.bind(testFeatures);

        waitForHandlerActionDelayed(mTestImsServiceController.getHandler(), RETRY_TIMEOUT,
                2 * RETRY_TIMEOUT);
        // Should only see two binds, not three from the auto rebind that occurs.
        verify(mMockContext, times(2)).bindService(any(), any(), anyInt());
    }

    private ServiceConnection bindAndConnectService(HashSet<Pair<Integer, Integer>> testFeatures) {
        ArgumentCaptor<ServiceConnection> serviceCaptor =
                ArgumentCaptor.forClass(ServiceConnection.class);
        assertTrue(mTestImsServiceController.bind(testFeatures));
        verify(mMockContext).bindService(any(), serviceCaptor.capture(), anyInt());
        serviceCaptor.getValue().onServiceConnected(mTestComponentName,
                mMockServiceControllerBinder.getBinder().asBinder());
        return serviceCaptor.getValue();
    }

    private IBinder.DeathRecipient getDeathRecipient() throws RemoteException {
        ArgumentCaptor<IBinder.DeathRecipient> deathCaptor =
                ArgumentCaptor.forClass(IBinder.DeathRecipient.class);
        IBinder binder = mMockServiceControllerBinder.getBinder().asBinder();
        verify(binder).linkToDeath(deathCaptor.capture(), anyInt());
        return deathCaptor.getValue();
    }
}
