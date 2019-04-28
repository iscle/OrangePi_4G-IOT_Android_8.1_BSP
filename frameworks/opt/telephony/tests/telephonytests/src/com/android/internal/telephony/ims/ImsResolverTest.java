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

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.telephony.CarrierConfigManager;
import android.telephony.ims.feature.ImsFeature;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Pair;

import com.android.ims.internal.IImsServiceController;
import com.android.internal.telephony.PhoneConstants;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ImsResolver
 */
@RunWith(AndroidJUnit4.class)
public class ImsResolverTest extends ImsTestBase {

    private static final int TEST_TIMEOUT = 200; //ms
    private static final ComponentName TEST_DEVICE_DEFAULT_NAME = new ComponentName("TestDevicePkg",
            "DeviceImsService");
    private static final ComponentName TEST_CARRIER_DEFAULT_NAME = new ComponentName(
            "TestCarrierPkg", "CarrierImsService");
    private static final ComponentName TEST_CARRIER_2_DEFAULT_NAME = new ComponentName(
            "TestCarrier2Pkg", "Carrier2ImsService");

    @Mock Context mMockContext;
    @Mock PackageManager mMockPM;
    @Mock ImsResolver.SubscriptionManagerProxy mTestSubscriptionManagerProxy;
    @Mock CarrierConfigManager mMockCarrierConfigManager;
    private ImsResolver mTestImsResolver;
    private BroadcastReceiver mTestPackageBroadcastReceiver;
    private BroadcastReceiver mTestCarrierConfigReceiver;
    private PersistableBundle[] mCarrierConfigs;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        mTestImsResolver = null;
        super.tearDown();
    }

    /**
     * Add a package to the package manager and make sure it is added to the cache of available
     * ImsServices in the ImsResolver
     */
    @Test
    @SmallTest
    public void testAddPackageToCache() {
        setupResolver(1/*numSlots*/);
        List<ResolveInfo> info = new ArrayList<>();
        Set<String> features = new HashSet<>();
        features.add(ImsResolver.METADATA_EMERGENCY_MMTEL_FEATURE);
        features.add(ImsResolver.METADATA_MMTEL_FEATURE);
        features.add(ImsResolver.METADATA_RCS_FEATURE);
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, features, true));
        when(mMockPM.queryIntentServicesAsUser(any(), anyInt(), anyInt())).thenReturn(info);
        setConfigCarrierString(0, TEST_CARRIER_DEFAULT_NAME.getPackageName());

        mTestImsResolver.populateCacheAndStartBind();

        waitForHandlerAction(mTestImsResolver.getHandler(), TEST_TIMEOUT);
        ImsResolver.ImsServiceInfo testCachedService =
                mTestImsResolver.getImsServiceInfoFromCache(
                        TEST_DEVICE_DEFAULT_NAME.getPackageName());
        assertNotNull(testCachedService);
        assertTrue(isImsServiceInfoEqual(TEST_DEVICE_DEFAULT_NAME, features, testCachedService));
    }

    /**
     * Set the carrier config override value and ensure that ImsResolver calls .bind on that
     * package name with the correct ImsFeatures.
     */
    @Test
    @SmallTest
    public void testCarrierPackageBind() throws RemoteException {
        setupResolver(1/*numSlots*/);
        // Set CarrierConfig default package name and make it available to the package manager
        setConfigCarrierString(0, TEST_CARRIER_DEFAULT_NAME.getPackageName());
        List<ResolveInfo> info = new ArrayList<>();
        Set<String> features = new HashSet<>();
        features.add(ImsResolver.METADATA_EMERGENCY_MMTEL_FEATURE);
        features.add(ImsResolver.METADATA_MMTEL_FEATURE);
        features.add(ImsResolver.METADATA_RCS_FEATURE);
        info.add(getResolveInfo(TEST_CARRIER_DEFAULT_NAME, features, true));
        when(mMockPM.queryIntentServicesAsUser(any(), anyInt(), anyInt())).thenReturn(info);
        ImsServiceController controller = mock(ImsServiceController.class);
        mTestImsResolver.setImsServiceControllerFactory((context, componentName) -> {
            when(controller.getComponentName()).thenReturn(componentName);
            return controller;
        });


        mTestImsResolver.populateCacheAndStartBind();

        waitForHandlerAction(mTestImsResolver.getHandler(), TEST_TIMEOUT);
        verify(controller).bind(convertToHashSet(features, 0));
        verify(controller, never()).unbind();
        assertEquals(TEST_CARRIER_DEFAULT_NAME, controller.getComponentName());
    }

    /**
     * Ensure that no ImsService is bound if there is no carrier or device package explictly set.
     */
    @Test
    @SmallTest
    public void testDontBindWhenNullCarrierPackage() throws RemoteException {
        setupResolver(1/*numSlots*/);
        List<ResolveInfo> info = new ArrayList<>();
        Set<String> features = new HashSet<>();
        features.add(ImsResolver.METADATA_EMERGENCY_MMTEL_FEATURE);
        features.add(ImsResolver.METADATA_MMTEL_FEATURE);
        features.add(ImsResolver.METADATA_RCS_FEATURE);
        info.add(getResolveInfo(TEST_CARRIER_DEFAULT_NAME, features, true));
        when(mMockPM.queryIntentServicesAsUser(any(), anyInt(), anyInt())).thenReturn(info);
        ImsServiceController controller = mock(ImsServiceController.class);
        mTestImsResolver.setImsServiceControllerFactory((context, componentName) -> {
            when(controller.getComponentName()).thenReturn(componentName);
            return controller;
        });

        // Set the CarrierConfig string to null so that ImsResolver will not bind to the available
        // Services
        setConfigCarrierString(0, null);
        mTestImsResolver.populateCacheAndStartBind();

        waitForHandlerAction(mTestImsResolver.getHandler(), TEST_TIMEOUT);
        verify(controller, never()).bind(any());
        verify(controller, never()).unbind();
    }

    /**
     * Test that the ImsService corresponding to the default device ImsService package name is
     * bound.
     */
    @Test
    @SmallTest
    public void testDevicePackageBind() throws RemoteException {
        setupResolver(1/*numSlots*/);
        List<ResolveInfo> info = new ArrayList<>();
        Set<String> features = new HashSet<>();
        features.add(ImsResolver.METADATA_EMERGENCY_MMTEL_FEATURE);
        features.add(ImsResolver.METADATA_MMTEL_FEATURE);
        features.add(ImsResolver.METADATA_RCS_FEATURE);
        // Use device default package, which will load the ImsService that the device provides
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, features, true));
        info.add(getResolveInfo(TEST_CARRIER_DEFAULT_NAME, features, true));
        when(mMockPM.queryIntentServicesAsUser(any(), anyInt(), anyInt())).thenReturn(info);
        ImsServiceController controller = mock(ImsServiceController.class);
        mTestImsResolver.setImsServiceControllerFactory((context, componentName) -> {
            when(controller.getComponentName()).thenReturn(componentName);
            return controller;
        });


        mTestImsResolver.populateCacheAndStartBind();

        waitForHandlerAction(mTestImsResolver.getHandler(), TEST_TIMEOUT);
        // There is no carrier override set, so make sure that the ImsServiceController binds
        // to all SIMs.
        HashSet<Pair<Integer, Integer>> featureSet = convertToHashSet(features, 0);
        verify(controller).bind(featureSet);
        verify(controller, never()).unbind();
        assertEquals(TEST_DEVICE_DEFAULT_NAME, controller.getComponentName());
    }

    /**
     * Test that when a device and carrier override package are set, both ImsServices are bound.
     * Verify that the carrier ImsService features are created and the device default features
     * are created for all features that are not covered by the carrier ImsService.
     */
    @Test
    @SmallTest
    public void testDeviceAndCarrierPackageBind() throws RemoteException {
        setupResolver(1/*numSlots*/);
        List<ResolveInfo> info = new ArrayList<>();
        Set<String> deviceFeatures = new HashSet<>();
        deviceFeatures.add(ImsResolver.METADATA_EMERGENCY_MMTEL_FEATURE);
        deviceFeatures.add(ImsResolver.METADATA_MMTEL_FEATURE);
        deviceFeatures.add(ImsResolver.METADATA_RCS_FEATURE);
        // Set the carrier override package for slot 0
        setConfigCarrierString(0, TEST_CARRIER_DEFAULT_NAME.getPackageName());
        Set<String> carrierFeatures = new HashSet<>();
        // Carrier service doesn't support the emergency voice feature.
        carrierFeatures.add(ImsResolver.METADATA_MMTEL_FEATURE);
        carrierFeatures.add(ImsResolver.METADATA_RCS_FEATURE);
        // Use device default package, which will load the ImsService that the device provides
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, deviceFeatures, true));
        info.add(getResolveInfo(TEST_CARRIER_DEFAULT_NAME, carrierFeatures, true));
        when(mMockPM.queryIntentServicesAsUser(any(), anyInt(), anyInt())).thenReturn(info);
        ImsServiceController deviceController = mock(ImsServiceController.class);
        ImsServiceController carrierController = mock(ImsServiceController.class);
        setImsServiceControllerFactory(deviceController, carrierController);


        mTestImsResolver.populateCacheAndStartBind();

        waitForHandlerAction(mTestImsResolver.getHandler(), TEST_TIMEOUT);
        // Verify that all features that have been defined for the carrier override are bound
        HashSet<Pair<Integer, Integer>> carrierFeatureSet = convertToHashSet(carrierFeatures, 0);
        carrierFeatureSet.addAll(convertToHashSet(carrierFeatures, 0));
        verify(carrierController).bind(carrierFeatureSet);
        verify(carrierController, never()).unbind();
        assertEquals(TEST_CARRIER_DEFAULT_NAME, carrierController.getComponentName());
        // Verify that all features that are not defined in the carrier override are bound in the
        // device controller (including emergency voice for slot 0)
        HashSet<Pair<Integer, Integer>> deviceFeatureSet = convertToHashSet(deviceFeatures, 0);
        deviceFeatureSet.removeAll(carrierFeatureSet);
        verify(deviceController).bind(deviceFeatureSet);
        verify(deviceController, never()).unbind();
        assertEquals(TEST_DEVICE_DEFAULT_NAME, deviceController.getComponentName());
    }

    /**
     * Verify that the ImsServiceController is available for the feature specified
     * (carrier for VOICE/RCS and device for emergency).
     */
    @Test
    @SmallTest
    public void testGetDeviceCarrierFeatures() throws RemoteException {
        setupResolver(2/*numSlots*/);
        ImsServiceController deviceController = mock(ImsServiceController.class);
        IImsServiceController iDeviceController = mock(IImsServiceController.class);
        when(deviceController.getImsServiceController()).thenReturn(iDeviceController);
        ImsServiceController carrierController = mock(ImsServiceController.class);
        IImsServiceController iCarrierController = mock(IImsServiceController.class);
        when(carrierController.getImsServiceController()).thenReturn(iCarrierController);
        mTestImsResolver.populateCacheAndStartBind();

        // Callback from mock ImsServiceControllers
        // All features on slot 1 should be the device default
        mTestImsResolver.imsServiceFeatureCreated(1, ImsFeature.EMERGENCY_MMTEL, deviceController);
        mTestImsResolver.imsServiceFeatureCreated(1, ImsFeature.MMTEL, deviceController);
        mTestImsResolver.imsServiceFeatureCreated(1, ImsFeature.RCS, deviceController);
        // The carrier override does not support emergency voice
        mTestImsResolver.imsServiceFeatureCreated(1, ImsFeature.EMERGENCY_MMTEL, deviceController);
        // The carrier override contains these features
        mTestImsResolver.imsServiceFeatureCreated(0, ImsFeature.MMTEL, carrierController);
        mTestImsResolver.imsServiceFeatureCreated(0, ImsFeature.RCS, carrierController);
        // Get the IImsServiceControllers for each feature on each slot and verify they are correct.
        assertEquals(iDeviceController, mTestImsResolver.getImsServiceControllerAndListen(
                1/*Slot id*/, ImsFeature.EMERGENCY_MMTEL, null));
        assertEquals(iDeviceController, mTestImsResolver.getImsServiceControllerAndListen(
                1 /*Slot id*/, ImsFeature.MMTEL, null));
        assertEquals(iDeviceController, mTestImsResolver.getImsServiceControllerAndListen(
                1 /*Slot id*/, ImsFeature.RCS, null));
        assertEquals(iDeviceController, mTestImsResolver.getImsServiceControllerAndListen(
                1 /*Slot id*/, ImsFeature.EMERGENCY_MMTEL, null));
        assertEquals(iCarrierController, mTestImsResolver.getImsServiceControllerAndListen(
                0 /*Slot id*/, ImsFeature.MMTEL, null));
        assertEquals(iCarrierController, mTestImsResolver.getImsServiceControllerAndListen(
                0 /*Slot id*/, ImsFeature.RCS, null));
    }

    /**
     * Bind to device ImsService and change the feature set. Verify that changeImsServiceFeature
     * is called with the new feature set.
     */
    @Test
    @SmallTest
    public void testAddDeviceFeatureNoCarrier() throws RemoteException {
        setupResolver(2/*numSlots*/);
        List<ResolveInfo> info = new ArrayList<>();
        Set<String> features = new HashSet<>();
        features.add(ImsResolver.METADATA_EMERGENCY_MMTEL_FEATURE);
        features.add(ImsResolver.METADATA_MMTEL_FEATURE);
        // Doesn't include RCS feature by default
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, features, true));
        when(mMockPM.queryIntentServicesAsUser(any(), anyInt(), anyInt())).thenReturn(info);
        ImsServiceController controller = mock(ImsServiceController.class);
        mTestImsResolver.setImsServiceControllerFactory((context, componentName) -> {
            when(controller.getComponentName()).thenReturn(componentName);
            return controller;
        });

        // Bind using default features
        mTestImsResolver.populateCacheAndStartBind();
        waitForHandlerAction(mTestImsResolver.getHandler(), TEST_TIMEOUT);
        HashSet<Pair<Integer, Integer>> featureSet = convertToHashSet(features, 0);
        featureSet.addAll(convertToHashSet(features, 1));
        verify(controller).bind(featureSet);

        // add RCS to features list
        Set<String> newFeatures = new HashSet<>(features);
        newFeatures.add(ImsResolver.METADATA_RCS_FEATURE);
        info.clear();
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, newFeatures, true));

        // Tell the package manager that a new device feature is installed
        Intent addPackageIntent = new Intent();
        addPackageIntent.setAction(Intent.ACTION_PACKAGE_ADDED);
        addPackageIntent.setData(new Uri.Builder().scheme("package")
                .opaquePart(TEST_DEVICE_DEFAULT_NAME.getPackageName()).build());
        mTestPackageBroadcastReceiver.onReceive(null, addPackageIntent);
        waitForHandlerAction(mTestImsResolver.getHandler(), TEST_TIMEOUT);

        //Verify new feature is added to the device default.
        HashSet<Pair<Integer, Integer>> newFeatureSet = convertToHashSet(newFeatures, 0);
        newFeatureSet.addAll(convertToHashSet(newFeatures, 1));
        verify(controller).changeImsServiceFeatures(newFeatureSet);
    }

    /**
     * Bind to device ImsService and change the feature set. Verify that changeImsServiceFeature
     * is called with the new feature set on the sub that doesn't include the carrier override.
     */
    @Test
    @SmallTest
    public void testAddDeviceFeatureWithCarrier() throws RemoteException {
        setupResolver(2/*numSlots*/);
        List<ResolveInfo> info = new ArrayList<>();
        Set<String> deviceFeatures = new HashSet<>();
        deviceFeatures.add(ImsResolver.METADATA_EMERGENCY_MMTEL_FEATURE);
        deviceFeatures.add(ImsResolver.METADATA_MMTEL_FEATURE);
        // Set the carrier override package for slot 0
        setConfigCarrierString(0, TEST_CARRIER_DEFAULT_NAME.getPackageName());
        Set<String> carrierFeatures = new HashSet<>();
        // Carrier service doesn't support the emergency voice feature.
        carrierFeatures.add(ImsResolver.METADATA_MMTEL_FEATURE);
        carrierFeatures.add(ImsResolver.METADATA_RCS_FEATURE);
        // Use device default package, which will load the ImsService that the device provides
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, deviceFeatures, true));
        info.add(getResolveInfo(TEST_CARRIER_DEFAULT_NAME, carrierFeatures, true));
        when(mMockPM.queryIntentServicesAsUser(any(), anyInt(), anyInt())).thenReturn(info);
        ImsServiceController deviceController = mock(ImsServiceController.class);
        ImsServiceController carrierController = mock(ImsServiceController.class);
        setImsServiceControllerFactory(deviceController, carrierController);

        mTestImsResolver.populateCacheAndStartBind();

        waitForHandlerAction(mTestImsResolver.getHandler(), TEST_TIMEOUT);
        // Verify that all features that have been defined for the carrier override are bound
        HashSet<Pair<Integer, Integer>> carrierFeatureSet = convertToHashSet(carrierFeatures, 0);
        carrierFeatureSet.addAll(convertToHashSet(carrierFeatures, 0));
        verify(carrierController).bind(carrierFeatureSet);
        verify(carrierController, never()).unbind();
        assertEquals(TEST_CARRIER_DEFAULT_NAME, carrierController.getComponentName());
        // Verify that all features that are not defined in the carrier override are bound in the
        // device controller (including emergency voice for slot 0)
        HashSet<Pair<Integer, Integer>> deviceFeatureSet = convertToHashSet(deviceFeatures, 1);
        deviceFeatures.removeAll(carrierFeatures);
        deviceFeatureSet.addAll(convertToHashSet(deviceFeatures, 0));
        verify(deviceController).bind(deviceFeatureSet);
        verify(deviceController, never()).unbind();
        assertEquals(TEST_DEVICE_DEFAULT_NAME, deviceController.getComponentName());

        // add RCS to features list
        Set<String> newDeviceFeatures = new HashSet<>();
        newDeviceFeatures.add(ImsResolver.METADATA_EMERGENCY_MMTEL_FEATURE);
        newDeviceFeatures.add(ImsResolver.METADATA_MMTEL_FEATURE);
        newDeviceFeatures.add(ImsResolver.METADATA_RCS_FEATURE);
        info.clear();
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, newDeviceFeatures, true));

        // Tell the package manager that a new device feature is installed
        Intent addPackageIntent = new Intent();
        addPackageIntent.setAction(Intent.ACTION_PACKAGE_ADDED);
        addPackageIntent.setData(new Uri.Builder().scheme("package")
                .opaquePart(TEST_DEVICE_DEFAULT_NAME.getPackageName()).build());
        mTestPackageBroadcastReceiver.onReceive(null, addPackageIntent);
        waitForHandlerAction(mTestImsResolver.getHandler(), TEST_TIMEOUT);

        //Verify new feature is added to the device default.
        // add all features for slot 1
        HashSet<Pair<Integer, Integer>> newDeviceFeatureSet =
                convertToHashSet(newDeviceFeatures, 1);
        // remove carrier overrides for slot 0
        newDeviceFeatures.removeAll(carrierFeatures);
        newDeviceFeatureSet.addAll(convertToHashSet(newDeviceFeatures, 0));
        verify(deviceController).changeImsServiceFeatures(newDeviceFeatureSet);
        verify(carrierController, never()).changeImsServiceFeatures(any());
    }

    /**
     * Bind to device ImsService and change the feature set of the carrier overridden ImsService.
     * Verify that the device and carrier ImsServices are changed.
     */
    @Test
    @SmallTest
    public void testAddCarrierFeature() throws RemoteException {
        setupResolver(2/*numSlots*/);
        List<ResolveInfo> info = new ArrayList<>();
        Set<String> deviceFeatures = new HashSet<>();
        deviceFeatures.add(ImsResolver.METADATA_EMERGENCY_MMTEL_FEATURE);
        deviceFeatures.add(ImsResolver.METADATA_MMTEL_FEATURE);
        deviceFeatures.add(ImsResolver.METADATA_RCS_FEATURE);
        // Set the carrier override package for slot 0
        setConfigCarrierString(0, TEST_CARRIER_DEFAULT_NAME.getPackageName());
        Set<String> carrierFeatures = new HashSet<>();
        // Carrier service doesn't support the emergency voice feature.
        carrierFeatures.add(ImsResolver.METADATA_MMTEL_FEATURE);
        // Use device default package, which will load the ImsService that the device provides
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, deviceFeatures, true));
        info.add(getResolveInfo(TEST_CARRIER_DEFAULT_NAME, carrierFeatures, true));
        when(mMockPM.queryIntentServicesAsUser(any(), anyInt(), anyInt())).thenReturn(info);
        ImsServiceController deviceController = mock(ImsServiceController.class);
        ImsServiceController carrierController = mock(ImsServiceController.class);
        setImsServiceControllerFactory(deviceController, carrierController);

        mTestImsResolver.populateCacheAndStartBind();

        waitForHandlerAction(mTestImsResolver.getHandler(), TEST_TIMEOUT);
        // Verify that all features that have been defined for the carrier override are bound
        HashSet<Pair<Integer, Integer>> carrierFeatureSet = convertToHashSet(carrierFeatures, 0);
        carrierFeatureSet.addAll(convertToHashSet(carrierFeatures, 0));
        verify(carrierController).bind(carrierFeatureSet);
        verify(carrierController, never()).unbind();
        assertEquals(TEST_CARRIER_DEFAULT_NAME, carrierController.getComponentName());
        // Verify that all features that are not defined in the carrier override are bound in the
        // device controller (including emergency voice for slot 0)
        HashSet<Pair<Integer, Integer>> deviceFeatureSet = convertToHashSet(deviceFeatures, 1);
        deviceFeatures.removeAll(carrierFeatures);
        deviceFeatureSet.addAll(convertToHashSet(deviceFeatures, 0));
        verify(deviceController).bind(deviceFeatureSet);
        verify(deviceController, never()).unbind();
        assertEquals(TEST_DEVICE_DEFAULT_NAME, deviceController.getComponentName());

        // add RCS to carrier features list
        Set<String> newCarrierFeatures = new HashSet<>();
        newCarrierFeatures.add(ImsResolver.METADATA_MMTEL_FEATURE);
        newCarrierFeatures.add(ImsResolver.METADATA_RCS_FEATURE);
        info.clear();
        info.add(getResolveInfo(TEST_CARRIER_DEFAULT_NAME, newCarrierFeatures, true));

        // Tell the package manager that a new device feature is installed
        Intent addPackageIntent = new Intent();
        addPackageIntent.setAction(Intent.ACTION_PACKAGE_ADDED);
        addPackageIntent.setData(new Uri.Builder().scheme("package")
                .opaquePart(TEST_CARRIER_DEFAULT_NAME.getPackageName()).build());
        mTestPackageBroadcastReceiver.onReceive(null, addPackageIntent);
        waitForHandlerAction(mTestImsResolver.getHandler(), TEST_TIMEOUT);

        //Verify new feature is added to the carrier override.
        // add all features for slot 0
        HashSet<Pair<Integer, Integer>> newCarrierFeatureSet =
                convertToHashSet(newCarrierFeatures, 0);
        verify(carrierController).changeImsServiceFeatures(newCarrierFeatureSet);
        deviceFeatureSet.removeAll(newCarrierFeatureSet);
        verify(deviceController).changeImsServiceFeatures(deviceFeatureSet);
    }

    /**
     * Bind to device ImsService and change the feature set of the carrier overridden ImsService by
     * removing a feature.
     * Verify that the device and carrier ImsServices are changed.
     */
    @Test
    @SmallTest
    public void testRemoveCarrierFeature() throws RemoteException {
        setupResolver(2/*numSlots*/);
        List<ResolveInfo> info = new ArrayList<>();
        Set<String> deviceFeatures = new HashSet<>();
        deviceFeatures.add(ImsResolver.METADATA_EMERGENCY_MMTEL_FEATURE);
        deviceFeatures.add(ImsResolver.METADATA_MMTEL_FEATURE);
        deviceFeatures.add(ImsResolver.METADATA_RCS_FEATURE);
        // Set the carrier override package for slot 0
        setConfigCarrierString(0, TEST_CARRIER_DEFAULT_NAME.getPackageName());
        Set<String> carrierFeatures = new HashSet<>();
        // Carrier service doesn't support the emergency voice feature.
        carrierFeatures.add(ImsResolver.METADATA_MMTEL_FEATURE);
        carrierFeatures.add(ImsResolver.METADATA_RCS_FEATURE);
        // Use device default package, which will load the ImsService that the device provides
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, deviceFeatures, true));
        info.add(getResolveInfo(TEST_CARRIER_DEFAULT_NAME, carrierFeatures, true));
        when(mMockPM.queryIntentServicesAsUser(any(), anyInt(), anyInt())).thenReturn(info);
        ImsServiceController deviceController = mock(ImsServiceController.class);
        ImsServiceController carrierController = mock(ImsServiceController.class);
        setImsServiceControllerFactory(deviceController, carrierController);

        mTestImsResolver.populateCacheAndStartBind();

        waitForHandlerAction(mTestImsResolver.getHandler(), TEST_TIMEOUT);
        // Verify that all features that have been defined for the carrier override are bound
        HashSet<Pair<Integer, Integer>> carrierFeatureSet = convertToHashSet(carrierFeatures, 0);
        carrierFeatureSet.addAll(convertToHashSet(carrierFeatures, 0));
        verify(carrierController).bind(carrierFeatureSet);
        verify(carrierController, never()).unbind();
        assertEquals(TEST_CARRIER_DEFAULT_NAME, carrierController.getComponentName());
        // Verify that all features that are not defined in the carrier override are bound in the
        // device controller (including emergency voice for slot 0)
        HashSet<Pair<Integer, Integer>> deviceFeatureSet = convertToHashSet(deviceFeatures, 1);
        deviceFeatures.removeAll(carrierFeatures);
        deviceFeatureSet.addAll(convertToHashSet(deviceFeatures, 0));
        verify(deviceController).bind(deviceFeatureSet);
        verify(deviceController, never()).unbind();
        assertEquals(TEST_DEVICE_DEFAULT_NAME, deviceController.getComponentName());

        // remove RCS from carrier features list
        Set<String> newCarrierFeatures = new HashSet<>();
        newCarrierFeatures.add(ImsResolver.METADATA_MMTEL_FEATURE);
        info.clear();
        info.add(getResolveInfo(TEST_CARRIER_DEFAULT_NAME, newCarrierFeatures, true));

        // Tell the package manager that a new device feature is installed
        Intent addPackageIntent = new Intent();
        addPackageIntent.setAction(Intent.ACTION_PACKAGE_ADDED);
        addPackageIntent.setData(new Uri.Builder().scheme("package")
                .opaquePart(TEST_CARRIER_DEFAULT_NAME.getPackageName()).build());
        mTestPackageBroadcastReceiver.onReceive(null, addPackageIntent);
        waitForHandlerAction(mTestImsResolver.getHandler(), TEST_TIMEOUT);

        //Verify new feature is added to the carrier override.
        // add all features for slot 0
        HashSet<Pair<Integer, Integer>> newCarrierFeatureSet =
                convertToHashSet(newCarrierFeatures, 0);
        verify(carrierController).changeImsServiceFeatures(newCarrierFeatureSet);
        Set<String> newDeviceFeatures = new HashSet<>();
        newDeviceFeatures.add(ImsResolver.METADATA_EMERGENCY_MMTEL_FEATURE);
        newDeviceFeatures.add(ImsResolver.METADATA_MMTEL_FEATURE);
        newDeviceFeatures.add(ImsResolver.METADATA_RCS_FEATURE);
        HashSet<Pair<Integer, Integer>> newDeviceFeatureSet = convertToHashSet(newDeviceFeatures,
                1);
        newDeviceFeatures.removeAll(newCarrierFeatures);
        newDeviceFeatureSet.addAll(convertToHashSet(newDeviceFeatures, 0));
        verify(deviceController).changeImsServiceFeatures(newDeviceFeatureSet);
    }

    /**
     * Inform the ImsResolver that a Carrier ImsService has been installed and must be bound.
     */
    @Test
    @SmallTest
    public void testInstallCarrierImsService() throws RemoteException {
        setupResolver(2/*numSlots*/);
        List<ResolveInfo> info = new ArrayList<>();
        Set<String> deviceFeatures = new HashSet<>();
        deviceFeatures.add(ImsResolver.METADATA_EMERGENCY_MMTEL_FEATURE);
        deviceFeatures.add(ImsResolver.METADATA_MMTEL_FEATURE);
        deviceFeatures.add(ImsResolver.METADATA_RCS_FEATURE);
        // Set the carrier override package for slot 0
        setConfigCarrierString(0, TEST_CARRIER_DEFAULT_NAME.getPackageName());
        // Use device default package, which will load the ImsService that the device provides
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, deviceFeatures, true));
        when(mMockPM.queryIntentServicesAsUser(any(), anyInt(), anyInt())).thenReturn(info);
        ImsServiceController deviceController = mock(ImsServiceController.class);
        ImsServiceController carrierController = mock(ImsServiceController.class);
        setImsServiceControllerFactory(deviceController, carrierController);

        mTestImsResolver.populateCacheAndStartBind();
        waitForHandlerAction(mTestImsResolver.getHandler(), TEST_TIMEOUT);

        Set<String> carrierFeatures = new HashSet<>();
        // Carrier service doesn't support the emergency voice feature.
        carrierFeatures.add(ImsResolver.METADATA_MMTEL_FEATURE);
        carrierFeatures.add(ImsResolver.METADATA_RCS_FEATURE);
        info.add(getResolveInfo(TEST_CARRIER_DEFAULT_NAME, carrierFeatures, true));
        when(mMockPM.queryIntentServicesAsUser(any(), anyInt(), anyInt())).thenReturn(info);

        // Tell the package manager that a new carrier app is installed
        Intent addPackageIntent = new Intent();
        addPackageIntent.setAction(Intent.ACTION_PACKAGE_ADDED);
        addPackageIntent.setData(new Uri.Builder().scheme("package")
                .opaquePart(TEST_CARRIER_DEFAULT_NAME.getPackageName()).build());
        mTestPackageBroadcastReceiver.onReceive(null, addPackageIntent);
        waitForHandlerAction(mTestImsResolver.getHandler(), TEST_TIMEOUT);

        // Verify that all features that have been defined for the carrier override are bound
        HashSet<Pair<Integer, Integer>> carrierFeatureSet = convertToHashSet(carrierFeatures, 0);
        carrierFeatureSet.addAll(convertToHashSet(carrierFeatures, 0));
        verify(carrierController).bind(carrierFeatureSet);
        // device features change
        HashSet<Pair<Integer, Integer>> deviceFeatureSet = convertToHashSet(deviceFeatures, 1);
        deviceFeatureSet.addAll(convertToHashSet(deviceFeatures, 0));
        deviceFeatureSet.removeAll(carrierFeatureSet);
        verify(deviceController).changeImsServiceFeatures(deviceFeatureSet);
    }

    /**
     * Inform the ImsResolver that a carrier ImsService has been uninstalled and the device default
     * must now use those features.
     */
    @Test
    @SmallTest
    public void testUninstallCarrierImsService() throws RemoteException {
        setupResolver(2/*numSlots*/);
        List<ResolveInfo> info = new ArrayList<>();
        Set<String> deviceFeatures = new HashSet<>();
        deviceFeatures.add(ImsResolver.METADATA_EMERGENCY_MMTEL_FEATURE);
        deviceFeatures.add(ImsResolver.METADATA_MMTEL_FEATURE);
        deviceFeatures.add(ImsResolver.METADATA_RCS_FEATURE);
        // Set the carrier override package for slot 0
        setConfigCarrierString(0, TEST_CARRIER_DEFAULT_NAME.getPackageName());
        Set<String> carrierFeatures = new HashSet<>();
        // Carrier service doesn't support the emergency voice feature.
        carrierFeatures.add(ImsResolver.METADATA_MMTEL_FEATURE);
        carrierFeatures.add(ImsResolver.METADATA_RCS_FEATURE);
        info.add(getResolveInfo(TEST_CARRIER_DEFAULT_NAME, carrierFeatures, true));
        // Use device default package, which will load the ImsService that the device provides
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, deviceFeatures, true));
        when(mMockPM.queryIntentServicesAsUser(any(), anyInt(), anyInt())).thenReturn(info);
        ImsServiceController deviceController = mock(ImsServiceController.class);
        ImsServiceController carrierController = mock(ImsServiceController.class);
        setImsServiceControllerFactory(deviceController, carrierController);

        mTestImsResolver.populateCacheAndStartBind();
        waitForHandlerAction(mTestImsResolver.getHandler(), TEST_TIMEOUT);

        // Tell the package manager that carrier app is uninstalled
        Intent removePackageIntent = new Intent();
        removePackageIntent.setAction(Intent.ACTION_PACKAGE_REMOVED);
        removePackageIntent.setData(new Uri.Builder().scheme("package")
                .opaquePart(TEST_CARRIER_DEFAULT_NAME.getPackageName()).build());
        info.clear();
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, deviceFeatures, true));
        when(mMockPM.queryIntentServicesAsUser(any(), anyInt(), anyInt())).thenReturn(info);
        mTestPackageBroadcastReceiver.onReceive(null, removePackageIntent);
        waitForHandlerAction(mTestImsResolver.getHandler(), TEST_TIMEOUT);

        // Verify that the carrier controller is unbound
        verify(carrierController).unbind();
        assertNull(mTestImsResolver.getImsServiceInfoFromCache(
                TEST_CARRIER_DEFAULT_NAME.getPackageName()));
        // device features change to include all supported functionality
        HashSet<Pair<Integer, Integer>> deviceFeatureSet = convertToHashSet(deviceFeatures, 1);
        deviceFeatureSet.addAll(convertToHashSet(deviceFeatures, 0));
        verify(deviceController).changeImsServiceFeatures(deviceFeatureSet);
    }

    /**
     * Inform ImsResolver that the carrier config has changed to none, requiring the device
     * ImsService to be bound/set up and the previous carrier ImsService to be unbound.
     */
    @Test
    @SmallTest
    public void testCarrierConfigChangedToNone() throws RemoteException {
        setupResolver(2/*numSlots*/);
        List<ResolveInfo> info = new ArrayList<>();
        Set<String> deviceFeatures = new HashSet<>();
        deviceFeatures.add(ImsResolver.METADATA_EMERGENCY_MMTEL_FEATURE);
        deviceFeatures.add(ImsResolver.METADATA_MMTEL_FEATURE);
        deviceFeatures.add(ImsResolver.METADATA_RCS_FEATURE);
        // Set the carrier override package for slot 0
        setConfigCarrierString(0, TEST_CARRIER_DEFAULT_NAME.getPackageName());
        Set<String> carrierFeatures = new HashSet<>();
        // Carrier service doesn't support the emergency voice feature.
        carrierFeatures.add(ImsResolver.METADATA_MMTEL_FEATURE);
        carrierFeatures.add(ImsResolver.METADATA_RCS_FEATURE);
        info.add(getResolveInfo(TEST_CARRIER_DEFAULT_NAME, carrierFeatures, true));
        // Use device default package, which will load the ImsService that the device provides
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, deviceFeatures, true));
        when(mMockPM.queryIntentServicesAsUser(any(), anyInt(), anyInt())).thenReturn(info);
        ImsServiceController deviceController = mock(ImsServiceController.class);
        ImsServiceController carrierController = mock(ImsServiceController.class);
        setImsServiceControllerFactory(deviceController, carrierController);

        mTestImsResolver.populateCacheAndStartBind();
        waitForHandlerAction(mTestImsResolver.getHandler(), TEST_TIMEOUT);

        setConfigCarrierString(0, null);
        Intent carrierConfigIntent = new Intent();
        carrierConfigIntent.putExtra(PhoneConstants.SUBSCRIPTION_KEY, 0);
        mTestCarrierConfigReceiver.onReceive(null, carrierConfigIntent);
        waitForHandlerAction(mTestImsResolver.getHandler(), TEST_TIMEOUT);

        // Verify that the carrier controller is unbound
        verify(carrierController).unbind();
        assertNotNull(mTestImsResolver.getImsServiceInfoFromCache(
                TEST_CARRIER_DEFAULT_NAME.getPackageName()));
        // device features change
        HashSet<Pair<Integer, Integer>> deviceFeatureSet = convertToHashSet(deviceFeatures, 1);
        deviceFeatureSet.addAll(convertToHashSet(deviceFeatures, 0));
        verify(deviceController).changeImsServiceFeatures(deviceFeatureSet);
    }

    /**
     * Inform ImsResolver that the carrier config has changed to another, requiring the new carrier
     * ImsService to be bound/set up and the previous carrier ImsService to be unbound.
     */
    @Test
    @SmallTest
    public void testCarrierConfigChangedToAnotherService() throws RemoteException {
        setupResolver(2/*numSlots*/);
        List<ResolveInfo> info = new ArrayList<>();
        Set<String> deviceFeatures = new HashSet<>();
        deviceFeatures.add(ImsResolver.METADATA_EMERGENCY_MMTEL_FEATURE);
        deviceFeatures.add(ImsResolver.METADATA_MMTEL_FEATURE);
        deviceFeatures.add(ImsResolver.METADATA_RCS_FEATURE);
        // Set the carrier override package for slot 0
        setConfigCarrierString(0, TEST_CARRIER_DEFAULT_NAME.getPackageName());
        Set<String> carrierFeatures1 = new HashSet<>();
        // Carrier service doesn't support the emergency voice feature.
        carrierFeatures1.add(ImsResolver.METADATA_MMTEL_FEATURE);
        carrierFeatures1.add(ImsResolver.METADATA_RCS_FEATURE);
        Set<String> carrierFeatures2 = new HashSet<>();
        // Carrier service doesn't support the emergency voice feature.
        carrierFeatures2.add(ImsResolver.METADATA_RCS_FEATURE);
        info.add(getResolveInfo(TEST_CARRIER_2_DEFAULT_NAME, carrierFeatures2, true));
        info.add(getResolveInfo(TEST_CARRIER_DEFAULT_NAME, carrierFeatures1, true));
        // Use device default package, which will load the ImsService that the device provides
        info.add(getResolveInfo(TEST_DEVICE_DEFAULT_NAME, deviceFeatures, true));
        when(mMockPM.queryIntentServicesAsUser(any(), anyInt(), anyInt())).thenReturn(info);
        ImsServiceController deviceController = mock(ImsServiceController.class);
        ImsServiceController carrierController1 = mock(ImsServiceController.class);
        ImsServiceController carrierController2 = mock(ImsServiceController.class);
        setImsServiceControllerFactory(deviceController, carrierController1, carrierController2);

        mTestImsResolver.populateCacheAndStartBind();
        waitForHandlerAction(mTestImsResolver.getHandler(), TEST_TIMEOUT);

        setConfigCarrierString(0, TEST_CARRIER_2_DEFAULT_NAME.getPackageName());
        Intent carrierConfigIntent = new Intent();
        carrierConfigIntent.putExtra(PhoneConstants.SUBSCRIPTION_KEY, 0);
        mTestCarrierConfigReceiver.onReceive(null, carrierConfigIntent);
        waitForHandlerAction(mTestImsResolver.getHandler(), TEST_TIMEOUT);

        // Verify that carrier 1 is unbound
        verify(carrierController1).unbind();
        assertNotNull(mTestImsResolver.getImsServiceInfoFromCache(
                TEST_CARRIER_DEFAULT_NAME.getPackageName()));
        // Verify that carrier 2 is bound
        HashSet<Pair<Integer, Integer>> carrier2FeatureSet = convertToHashSet(carrierFeatures2, 0);
        verify(carrierController2).bind(carrier2FeatureSet);
        assertNotNull(mTestImsResolver.getImsServiceInfoFromCache(
                TEST_CARRIER_DEFAULT_NAME.getPackageName()));
        // device features change to accommodate for the features carrier 2 lacks
        HashSet<Pair<Integer, Integer>> deviceFeatureSet = convertToHashSet(deviceFeatures, 1);
        deviceFeatures.removeAll(carrierFeatures2);
        deviceFeatureSet.addAll(convertToHashSet(deviceFeatures, 0));
        verify(deviceController).changeImsServiceFeatures(deviceFeatureSet);
    }

    private void setupResolver(int numSlots) {
        when(mMockContext.getSystemService(eq(Context.CARRIER_CONFIG_SERVICE))).thenReturn(
                mMockCarrierConfigManager);
        when(mMockContext.getPackageManager()).thenReturn(mMockPM);
        mCarrierConfigs = new PersistableBundle[numSlots];
        for (int i = 0; i < numSlots; i++) {
            mCarrierConfigs[i] = new PersistableBundle();
            when(mMockCarrierConfigManager.getConfigForSubId(eq(i))).thenReturn(
                    mCarrierConfigs[i]);
            when(mTestSubscriptionManagerProxy.getSlotIndex(eq(i))).thenReturn(i);
            when(mTestSubscriptionManagerProxy.getSubId(eq(i))).thenReturn(i);
        }

        mTestImsResolver = new ImsResolver(mMockContext, TEST_DEVICE_DEFAULT_NAME.getPackageName(),
                numSlots);

        ArgumentCaptor<BroadcastReceiver> packageBroadcastCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        ArgumentCaptor<BroadcastReceiver> carrierConfigCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mMockContext).registerReceiverAsUser(packageBroadcastCaptor.capture(), any(),
                any(), any(), any());
        verify(mMockContext).registerReceiver(carrierConfigCaptor.capture(), any());
        mTestCarrierConfigReceiver = carrierConfigCaptor.getValue();
        mTestPackageBroadcastReceiver = packageBroadcastCaptor.getValue();
        mTestImsResolver.setSubscriptionManagerProxy(mTestSubscriptionManagerProxy);
    }

    private void setImsServiceControllerFactory(ImsServiceController deviceController,
            ImsServiceController carrierController) {
        mTestImsResolver.setImsServiceControllerFactory((context, componentName) -> {
            if (TEST_DEVICE_DEFAULT_NAME.getPackageName().equals(componentName.getPackageName())) {
                when(deviceController.getComponentName()).thenReturn(componentName);
                return deviceController;
            } else if (TEST_CARRIER_DEFAULT_NAME.getPackageName().equals(
                    componentName.getPackageName())) {
                when(carrierController.getComponentName()).thenReturn(componentName);
                return carrierController;
            }
            return null;
        });
    }

    private void setImsServiceControllerFactory(ImsServiceController deviceController,
            ImsServiceController carrierController1, ImsServiceController carrierController2) {
        mTestImsResolver.setImsServiceControllerFactory((context, componentName) -> {
            if (TEST_DEVICE_DEFAULT_NAME.getPackageName().equals(componentName.getPackageName())) {
                when(deviceController.getComponentName()).thenReturn(componentName);
                return deviceController;
            } else if (TEST_CARRIER_DEFAULT_NAME.getPackageName().equals(
                    componentName.getPackageName())) {
                when(carrierController1.getComponentName()).thenReturn(componentName);
                return carrierController1;
            } else if (TEST_CARRIER_2_DEFAULT_NAME.getPackageName().equals(
                    componentName.getPackageName())) {
                when(carrierController2.getComponentName()).thenReturn(componentName);
                return carrierController2;
            }
            return null;
        });
    }


    private void setConfigCarrierString(int subId, String packageName) {
        mCarrierConfigs[subId].putString(
                CarrierConfigManager.KEY_CONFIG_IMS_PACKAGE_OVERRIDE_STRING, packageName);
    }

    private HashSet<Pair<Integer, Integer>> convertToHashSet(Set<String> features, int subId) {
        HashSet<Pair<Integer, Integer>> featureSet = features.stream()
                .map(f -> new Pair<>(subId, metadataStringToFeature(f)))
                .collect(Collectors.toCollection(HashSet::new));
        return featureSet;
    }

    private int metadataStringToFeature(String f) {
        switch (f) {
            case ImsResolver.METADATA_EMERGENCY_MMTEL_FEATURE:
                return ImsFeature.EMERGENCY_MMTEL;
            case ImsResolver.METADATA_MMTEL_FEATURE:
                return ImsFeature.MMTEL;
            case ImsResolver.METADATA_RCS_FEATURE:
                return ImsFeature.RCS;
        }
        return -1;
    }

    private boolean isImsServiceInfoEqual(ComponentName name, Set<String> features,
            ImsResolver.ImsServiceInfo sInfo) {
        if (!Objects.equals(sInfo.name, name)) {
            return false;
        }
        for (String f : features) {
            switch (f) {
                case ImsResolver.METADATA_EMERGENCY_MMTEL_FEATURE:
                    if (!sInfo.supportedFeatures.contains(ImsFeature.EMERGENCY_MMTEL)) {
                        return false;
                    }
                    break;
                case ImsResolver.METADATA_MMTEL_FEATURE:
                    if (!sInfo.supportedFeatures.contains(ImsFeature.MMTEL)) {
                        return false;
                    }
                    break;
                case ImsResolver.METADATA_RCS_FEATURE:
                    if (!sInfo.supportedFeatures.contains(ImsFeature.RCS)) {
                        return false;
                    }
                    break;
            }
        }
        return true;
    }

    private ResolveInfo getResolveInfo(ComponentName name, Set<String> features,
            boolean isPermissionGranted) {
        ResolveInfo info = new ResolveInfo();
        info.serviceInfo = new ServiceInfo();
        info.serviceInfo.packageName = name.getPackageName();
        info.serviceInfo.name = name.getClassName();
        info.serviceInfo.metaData = new Bundle();
        for (String s : features) {
            info.serviceInfo.metaData.putBoolean(s, true);
        }
        if (isPermissionGranted) {
            info.serviceInfo.permission = Manifest.permission.BIND_IMS_SERVICE;
        }
        return info;
    }
}
