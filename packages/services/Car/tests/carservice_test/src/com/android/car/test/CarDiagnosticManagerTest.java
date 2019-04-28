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

package com.android.car.test;

import static java.lang.Integer.toHexString;

import android.car.Car;
import android.car.diagnostic.CarDiagnosticEvent;
import android.car.diagnostic.CarDiagnosticEvent.FuelSystemStatus;
import android.car.diagnostic.CarDiagnosticEvent.FuelType;
import android.car.diagnostic.CarDiagnosticEvent.CommonIgnitionMonitors;
import android.car.diagnostic.CarDiagnosticEvent.CompressionIgnitionMonitors;
import android.car.diagnostic.CarDiagnosticEvent.SparkIgnitionMonitors;
import android.car.diagnostic.CarDiagnosticEvent.SecondaryAirStatus;
import android.car.diagnostic.CarDiagnosticManager;
import android.car.diagnostic.FloatSensorIndex;
import android.car.diagnostic.IntegerSensorIndex;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.os.SystemClock;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.JsonReader;
import android.util.JsonWriter;
import android.util.Log;
import com.android.car.vehiclehal.DiagnosticEventBuilder;
import com.android.car.vehiclehal.DiagnosticJson;
import com.android.car.vehiclehal.VehiclePropValueBuilder;
import com.android.car.vehiclehal.test.MockedVehicleHal.VehicleHalPropertyHandler;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/** Test the public entry points for the CarDiagnosticManager */
@MediumTest
public class CarDiagnosticManagerTest extends MockedCarTestBase {
    private static final String TAG = CarDiagnosticManagerTest.class.getSimpleName();

    private final DiagnosticEventBuilder mLiveFrameEventBuilder =
            new DiagnosticEventBuilder(VehicleProperty.OBD2_LIVE_FRAME);
    private final DiagnosticEventBuilder mFreezeFrameEventBuilder =
            new DiagnosticEventBuilder(VehicleProperty.OBD2_FREEZE_FRAME);
    private final FreezeFrameProperties mFreezeFrameProperties = new FreezeFrameProperties();

    private CarDiagnosticManager mCarDiagnosticManager;

    private static final String DTC = "P1010";

    /**
     * This class is a central repository for freeze frame data. It ensures that timestamps and
     * events are kept in sync and provides a consistent access model for diagnostic properties.
     */
    class FreezeFrameProperties {
        private final HashMap<Long, VehiclePropValue> mEvents = new HashMap<>();

        public final VehicleHalPropertyHandler mFreezeFrameInfoHandler =
                new FreezeFrameInfoHandler();
        public final VehicleHalPropertyHandler mFreezeFrameHandler = new FreezeFrameHandler();
        public final VehicleHalPropertyHandler mFreezeFrameClearHandler =
                new FreezeFrameClearHandler();

        synchronized VehiclePropValue addNewEvent(DiagnosticEventBuilder builder) {
            long timestamp = SystemClock.elapsedRealtimeNanos();
            return addNewEvent(builder, timestamp);
        }

        synchronized VehiclePropValue addNewEvent(DiagnosticEventBuilder builder, long timestamp) {
            VehiclePropValue newEvent = builder.build(timestamp);
            mEvents.put(timestamp, newEvent);
            return newEvent;
        }

        synchronized VehiclePropValue removeEvent(long timestamp) {
            return mEvents.remove(timestamp);
        }

        synchronized void removeEvents() {
            mEvents.clear();
        }

        synchronized long[] getTimestamps() {
            return mEvents.keySet().stream().mapToLong(Long::longValue).toArray();
        }

        synchronized VehiclePropValue getEvent(long timestamp) {
            return mEvents.get(timestamp);
        }

        class FreezeFramePropertyHandler implements VehicleHalPropertyHandler {
            private boolean mSubscribed = false;

            protected final int VEHICLE_PROPERTY;

            protected FreezeFramePropertyHandler(int propertyId) {
                VEHICLE_PROPERTY = propertyId;
            }

            @Override
            public synchronized void onPropertySet(VehiclePropValue value) {
                assertEquals(VEHICLE_PROPERTY, value.prop);
            }

            @Override
            public synchronized VehiclePropValue onPropertyGet(VehiclePropValue value) {
                assertEquals(VEHICLE_PROPERTY, value.prop);
                return null;
            }

            @Override
            public synchronized void onPropertySubscribe(
                    int property, int zones, float sampleRate) {
                assertEquals(VEHICLE_PROPERTY, property);
                mSubscribed = true;
            }

            @Override
            public synchronized void onPropertyUnsubscribe(int property) {
                assertEquals(VEHICLE_PROPERTY, property);
                if (!mSubscribed) {
                    throw new IllegalArgumentException(
                            "Property was not subscribed 0x" + toHexString(property));
                }
                mSubscribed = false;
            }
        }

        class FreezeFrameInfoHandler extends FreezeFramePropertyHandler {
            FreezeFrameInfoHandler() {
                super(VehicleProperty.OBD2_FREEZE_FRAME_INFO);
            }

            @Override
            public synchronized VehiclePropValue onPropertyGet(VehiclePropValue value) {
                super.onPropertyGet(value);
                VehiclePropValueBuilder builder =
                        VehiclePropValueBuilder.newBuilder(VEHICLE_PROPERTY);
                builder.setInt64Value(getTimestamps());
                return builder.build();
            }
        }

        class FreezeFrameHandler extends FreezeFramePropertyHandler {
            FreezeFrameHandler() {
                super(VehicleProperty.OBD2_FREEZE_FRAME);
            }

            @Override
            public synchronized VehiclePropValue onPropertyGet(VehiclePropValue value) {
                super.onPropertyGet(value);
                long timestamp = value.value.int64Values.get(0);
                return getEvent(timestamp);
            }
        }

        class FreezeFrameClearHandler extends FreezeFramePropertyHandler {
            FreezeFrameClearHandler() {
                super(VehicleProperty.OBD2_FREEZE_FRAME_CLEAR);
            }

            @Override
            public synchronized void onPropertySet(VehiclePropValue value) {
                super.onPropertySet(value);
                if (0 == value.value.int64Values.size()) {
                    removeEvents();
                } else {
                    for (long timestamp : value.value.int64Values) {
                        removeEvent(timestamp);
                    }
                }
            }
        }
    }

    @Override
    protected synchronized void configureMockedHal() {
        java.util.Collection<Integer> numVendorSensors = Arrays.asList(0, 0);
        addProperty(VehicleProperty.OBD2_LIVE_FRAME, mLiveFrameEventBuilder.build())
                .setConfigArray(numVendorSensors);
        addProperty(
                VehicleProperty.OBD2_FREEZE_FRAME_INFO,
                mFreezeFrameProperties.mFreezeFrameInfoHandler);
        addProperty(VehicleProperty.OBD2_FREEZE_FRAME, mFreezeFrameProperties.mFreezeFrameHandler)
                .setConfigArray(numVendorSensors);
        addProperty(
                VehicleProperty.OBD2_FREEZE_FRAME_CLEAR,
                mFreezeFrameProperties.mFreezeFrameClearHandler);
    }

    @Override
    protected void setUp() throws Exception {
        mLiveFrameEventBuilder.addIntSensor(IntegerSensorIndex.AMBIENT_AIR_TEMPERATURE, 30);
        mLiveFrameEventBuilder.addIntSensor(
                IntegerSensorIndex.FUEL_SYSTEM_STATUS,
                FuelSystemStatus.OPEN_ENGINE_LOAD_OR_DECELERATION);
        mLiveFrameEventBuilder.addIntSensor(
                IntegerSensorIndex.RUNTIME_SINCE_ENGINE_START, 5000);
        mLiveFrameEventBuilder.addIntSensor(IntegerSensorIndex.CONTROL_MODULE_VOLTAGE, 2);
        mLiveFrameEventBuilder.addFloatSensor(FloatSensorIndex.CALCULATED_ENGINE_LOAD, 0.125f);
        mLiveFrameEventBuilder.addFloatSensor(FloatSensorIndex.VEHICLE_SPEED, 12.5f);

        mFreezeFrameEventBuilder.addIntSensor(IntegerSensorIndex.AMBIENT_AIR_TEMPERATURE, 30);
        mFreezeFrameEventBuilder.addIntSensor(
                IntegerSensorIndex.RUNTIME_SINCE_ENGINE_START, 5000);
        mFreezeFrameEventBuilder.addIntSensor(IntegerSensorIndex.CONTROL_MODULE_VOLTAGE, 2);
        mFreezeFrameEventBuilder.addFloatSensor(
                FloatSensorIndex.CALCULATED_ENGINE_LOAD, 0.125f);
        mFreezeFrameEventBuilder.addFloatSensor(FloatSensorIndex.VEHICLE_SPEED, 12.5f);
        mFreezeFrameEventBuilder.setDTC(DTC);

        super.setUp();

        Log.i(TAG, "attempting to get DIAGNOSTIC_SERVICE");
        mCarDiagnosticManager =
                (CarDiagnosticManager) getCar().getCarManager(Car.DIAGNOSTIC_SERVICE);
    }

    public void testLiveFrameRead() throws Exception {
        CarDiagnosticEvent liveFrame = mCarDiagnosticManager.getLatestLiveFrame();

        assertNotNull(liveFrame);
        assertTrue(liveFrame.isLiveFrame());
        assertFalse(liveFrame.isFreezeFrame());
        assertFalse(liveFrame.isEmptyFrame());

        assertEquals(
                5000,
                liveFrame
                        .getSystemIntegerSensor(IntegerSensorIndex.RUNTIME_SINCE_ENGINE_START)
                        .intValue());
        assertEquals(
                30,
                liveFrame
                        .getSystemIntegerSensor(IntegerSensorIndex.AMBIENT_AIR_TEMPERATURE)
                        .intValue());
        assertEquals(
                2,
                liveFrame
                        .getSystemIntegerSensor(IntegerSensorIndex.CONTROL_MODULE_VOLTAGE)
                        .intValue());
        assertEquals(
                0.125f,
                liveFrame
                        .getSystemFloatSensor(FloatSensorIndex.CALCULATED_ENGINE_LOAD)
                        .floatValue());
        assertEquals(
                12.5f,
                liveFrame.getSystemFloatSensor(FloatSensorIndex.VEHICLE_SPEED).floatValue());
    }

    public void testLiveFrameEvent() throws Exception {
        Listener listener = new Listener();
        mCarDiagnosticManager.registerListener(
                listener,
                CarDiagnosticManager.FRAME_TYPE_LIVE,
                android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);

        listener.reset();
        long time = SystemClock.elapsedRealtimeNanos();
        mLiveFrameEventBuilder.addIntSensor(
                IntegerSensorIndex.RUNTIME_SINCE_ENGINE_START, 5100);

        getMockedVehicleHal().injectEvent(mLiveFrameEventBuilder.build(time));
        assertTrue(listener.waitForEvent(time));

        CarDiagnosticEvent liveFrame = listener.getLastEvent();

        assertEquals(
                5100,
                liveFrame
                        .getSystemIntegerSensor(IntegerSensorIndex.RUNTIME_SINCE_ENGINE_START)
                        .intValue());
    }

    public void testMissingSensorRead() throws Exception {
        Listener listener = new Listener();
        mCarDiagnosticManager.registerListener(
                listener,
                CarDiagnosticManager.FRAME_TYPE_LIVE,
                android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);

        getMockedVehicleHal().injectEvent(mLiveFrameEventBuilder.build());
        assertTrue(listener.waitForEvent());

        CarDiagnosticEvent liveFrame = listener.getLastEvent();
        assertNotNull(liveFrame);

        assertNull(
                liveFrame.getSystemIntegerSensor(
                        IntegerSensorIndex.DRIVER_DEMAND_PERCENT_TORQUE));
        assertEquals(
                -1,
                liveFrame.getSystemIntegerSensor(
                        IntegerSensorIndex.DRIVER_DEMAND_PERCENT_TORQUE, -1));

        assertNull(liveFrame.getSystemFloatSensor(FloatSensorIndex.OXYGEN_SENSOR6_VOLTAGE));
        assertEquals(
                0.25f,
                liveFrame.getSystemFloatSensor(FloatSensorIndex.OXYGEN_SENSOR5_VOLTAGE, 0.25f));

        assertNull(liveFrame.getVendorIntegerSensor(IntegerSensorIndex.VENDOR_START));
        assertEquals(-1, liveFrame.getVendorIntegerSensor(IntegerSensorIndex.VENDOR_START, -1));

        assertNull(liveFrame.getVendorFloatSensor(FloatSensorIndex.VENDOR_START));
        assertEquals(
                0.25f, liveFrame.getVendorFloatSensor(FloatSensorIndex.VENDOR_START, 0.25f));
    }

    public void testFuelSystemStatus() throws Exception {
        Listener listener = new Listener();
        mCarDiagnosticManager.registerListener(
                listener,
                CarDiagnosticManager.FRAME_TYPE_LIVE,
                android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);

        getMockedVehicleHal().injectEvent(mLiveFrameEventBuilder.build());
        assertTrue(listener.waitForEvent());

        CarDiagnosticEvent liveFrame = listener.getLastEvent();
        assertNotNull(liveFrame);

        assertEquals(
                FuelSystemStatus.OPEN_ENGINE_LOAD_OR_DECELERATION,
                liveFrame
                        .getSystemIntegerSensor(IntegerSensorIndex.FUEL_SYSTEM_STATUS)
                        .intValue());
        assertEquals(
                FuelSystemStatus.OPEN_ENGINE_LOAD_OR_DECELERATION,
                liveFrame.getFuelSystemStatus().intValue());
    }

    public void testSecondaryAirStatus() throws Exception {
        Listener listener = new Listener();
        mCarDiagnosticManager.registerListener(
                listener,
                CarDiagnosticManager.FRAME_TYPE_LIVE,
                android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);

        mLiveFrameEventBuilder.addIntSensor(
                IntegerSensorIndex.COMMANDED_SECONDARY_AIR_STATUS,
                SecondaryAirStatus.FROM_OUTSIDE_OR_OFF);
        long timestamp = SystemClock.elapsedRealtimeNanos();
        getMockedVehicleHal().injectEvent(mLiveFrameEventBuilder.build(timestamp));

        assertTrue(listener.waitForEvent(timestamp));

        CarDiagnosticEvent liveFrame = listener.getLastEvent();
        assertNotNull(liveFrame);

        assertEquals(
                SecondaryAirStatus.FROM_OUTSIDE_OR_OFF,
                liveFrame
                        .getSystemIntegerSensor(
                                IntegerSensorIndex.COMMANDED_SECONDARY_AIR_STATUS)
                        .intValue());
        assertEquals(
                SecondaryAirStatus.FROM_OUTSIDE_OR_OFF,
                liveFrame.getSecondaryAirStatus().intValue());
    }

    public void testIgnitionMonitors() throws Exception {
        Listener listener = new Listener();
        mCarDiagnosticManager.registerListener(
                listener,
                CarDiagnosticManager.FRAME_TYPE_LIVE,
                android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);

        // cfr. CarDiagnosticEvent for the meaning of the several bits
        final int sparkMonitorsValue =
                0x1 | (0x1 << 2) | (0x1 << 3) | (0x1 << 6) | (0x1 << 10) | (0x1 << 11);

        final int compressionMonitorsValue =
                (0x1 << 2) | (0x1 << 3) | (0x1 << 6) | (0x1 << 12) | (0x1 << 13);

        mLiveFrameEventBuilder.addIntSensor(IntegerSensorIndex.IGNITION_MONITORS_SUPPORTED, 0);
        mLiveFrameEventBuilder.addIntSensor(
                IntegerSensorIndex.IGNITION_SPECIFIC_MONITORS, sparkMonitorsValue);

        long timestamp = SystemClock.elapsedRealtimeNanos();
        getMockedVehicleHal().injectEvent(mLiveFrameEventBuilder.build(timestamp));

        assertTrue(listener.waitForEvent(timestamp));

        CarDiagnosticEvent liveFrame = listener.getLastEvent();
        assertNotNull(liveFrame);

        CommonIgnitionMonitors commonIgnitionMonitors = liveFrame.getIgnitionMonitors();
        assertNotNull(commonIgnitionMonitors);
        assertTrue(commonIgnitionMonitors.components.available);
        assertFalse(commonIgnitionMonitors.components.incomplete);
        assertTrue(commonIgnitionMonitors.fuelSystem.available);
        assertTrue(commonIgnitionMonitors.fuelSystem.incomplete);
        assertFalse(commonIgnitionMonitors.misfire.available);
        assertFalse(commonIgnitionMonitors.misfire.incomplete);

        SparkIgnitionMonitors sparkIgnitionMonitors =
                commonIgnitionMonitors.asSparkIgnitionMonitors();
        assertNotNull(sparkIgnitionMonitors);
        assertNull(commonIgnitionMonitors.asCompressionIgnitionMonitors());

        assertTrue(sparkIgnitionMonitors.EGR.available);
        assertFalse(sparkIgnitionMonitors.EGR.incomplete);
        assertFalse(sparkIgnitionMonitors.oxygenSensorHeater.available);
        assertFalse(sparkIgnitionMonitors.oxygenSensorHeater.incomplete);
        assertTrue(sparkIgnitionMonitors.oxygenSensor.available);
        assertTrue(sparkIgnitionMonitors.oxygenSensor.incomplete);
        assertFalse(sparkIgnitionMonitors.ACRefrigerant.available);
        assertFalse(sparkIgnitionMonitors.ACRefrigerant.incomplete);
        assertFalse(sparkIgnitionMonitors.secondaryAirSystem.available);
        assertFalse(sparkIgnitionMonitors.secondaryAirSystem.incomplete);
        assertFalse(sparkIgnitionMonitors.evaporativeSystem.available);
        assertFalse(sparkIgnitionMonitors.evaporativeSystem.incomplete);
        assertFalse(sparkIgnitionMonitors.heatedCatalyst.available);
        assertFalse(sparkIgnitionMonitors.heatedCatalyst.incomplete);
        assertFalse(sparkIgnitionMonitors.catalyst.available);
        assertFalse(sparkIgnitionMonitors.catalyst.incomplete);

        mLiveFrameEventBuilder.addIntSensor(IntegerSensorIndex.IGNITION_MONITORS_SUPPORTED, 1);
        mLiveFrameEventBuilder.addIntSensor(
                IntegerSensorIndex.IGNITION_SPECIFIC_MONITORS, compressionMonitorsValue);

        timestamp += 1000;
        getMockedVehicleHal().injectEvent(mLiveFrameEventBuilder.build(timestamp));

        assertTrue(listener.waitForEvent(timestamp));

        liveFrame = listener.getLastEvent();
        assertNotNull(liveFrame);
        assertEquals(timestamp, liveFrame.timestamp);

        commonIgnitionMonitors = liveFrame.getIgnitionMonitors();
        assertNotNull(commonIgnitionMonitors);
        assertFalse(commonIgnitionMonitors.components.available);
        assertFalse(commonIgnitionMonitors.components.incomplete);
        assertTrue(commonIgnitionMonitors.fuelSystem.available);
        assertTrue(commonIgnitionMonitors.fuelSystem.incomplete);
        assertFalse(commonIgnitionMonitors.misfire.available);
        assertFalse(commonIgnitionMonitors.misfire.incomplete);
        CompressionIgnitionMonitors compressionIgnitionMonitors =
                commonIgnitionMonitors.asCompressionIgnitionMonitors();
        assertNull(commonIgnitionMonitors.asSparkIgnitionMonitors());
        assertNotNull(compressionIgnitionMonitors);

        assertTrue(compressionIgnitionMonitors.EGROrVVT.available);
        assertFalse(compressionIgnitionMonitors.EGROrVVT.incomplete);
        assertFalse(compressionIgnitionMonitors.PMFilter.available);
        assertFalse(compressionIgnitionMonitors.PMFilter.incomplete);
        assertFalse(compressionIgnitionMonitors.exhaustGasSensor.available);
        assertFalse(compressionIgnitionMonitors.exhaustGasSensor.incomplete);
        assertTrue(compressionIgnitionMonitors.boostPressure.available);
        assertTrue(compressionIgnitionMonitors.boostPressure.incomplete);
        assertFalse(compressionIgnitionMonitors.NOxSCR.available);
        assertFalse(compressionIgnitionMonitors.NOxSCR.incomplete);
        assertFalse(compressionIgnitionMonitors.NMHCCatalyst.available);
        assertFalse(compressionIgnitionMonitors.NMHCCatalyst.incomplete);
    }

    public void testFuelType() throws Exception {
        Listener listener = new Listener();
        mCarDiagnosticManager.registerListener(
                listener,
                CarDiagnosticManager.FRAME_TYPE_LIVE,
                android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);

        mLiveFrameEventBuilder.addIntSensor(
                IntegerSensorIndex.FUEL_TYPE, FuelType.BIFUEL_RUNNING_LPG);
        long timestamp = SystemClock.elapsedRealtimeNanos();
        getMockedVehicleHal().injectEvent(mLiveFrameEventBuilder.build(timestamp));

        assertTrue(listener.waitForEvent(timestamp));

        CarDiagnosticEvent liveFrame = listener.getLastEvent();
        assertNotNull(liveFrame);

        assertEquals(
                FuelType.BIFUEL_RUNNING_LPG,
                liveFrame.getSystemIntegerSensor(IntegerSensorIndex.FUEL_TYPE).intValue());
        assertEquals(FuelType.BIFUEL_RUNNING_LPG, liveFrame.getFuelType().intValue());
    }

    public void testDiagnosticJson() throws Exception {
        Listener listener = new Listener();
        mCarDiagnosticManager.registerListener(
                listener,
                CarDiagnosticManager.FRAME_TYPE_LIVE,
                android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);

        mLiveFrameEventBuilder.addIntSensor(IntegerSensorIndex.ENGINE_OIL_TEMPERATURE, 74);
        mLiveFrameEventBuilder.addFloatSensor(FloatSensorIndex.OXYGEN_SENSOR1_VOLTAGE, 0.125f);

        long timestamp = SystemClock.elapsedRealtimeNanos();
        getMockedVehicleHal().injectEvent(mLiveFrameEventBuilder.build(timestamp));

        assertTrue(listener.waitForEvent(timestamp));

        CarDiagnosticEvent liveFrame = listener.getLastEvent();
        assertNotNull(liveFrame);

        assertEquals(
                74,
                liveFrame
                        .getSystemIntegerSensor(IntegerSensorIndex.ENGINE_OIL_TEMPERATURE)
                        .intValue());
        assertEquals(
                0.125f,
                liveFrame.getSystemFloatSensor(FloatSensorIndex.OXYGEN_SENSOR1_VOLTAGE));

        StringWriter stringWriter = new StringWriter();
        JsonWriter jsonWriter = new JsonWriter(stringWriter);

        liveFrame.writeToJson(jsonWriter);
        jsonWriter.flush();

        StringReader stringReader = new StringReader(stringWriter.toString());
        JsonReader jsonReader = new JsonReader(stringReader);
        DiagnosticJson diagnosticJson = DiagnosticJson.build(jsonReader);

        assertEquals(
                74,
                diagnosticJson
                        .intValues
                        .get(IntegerSensorIndex.ENGINE_OIL_TEMPERATURE)
                        .intValue());
        assertEquals(
                0.125f,
                diagnosticJson.floatValues.get(FloatSensorIndex.OXYGEN_SENSOR1_VOLTAGE));
    }

    public void testMultipleListeners() throws Exception {
        Listener listener1 = new Listener();
        Listener listener2 = new Listener();

        mCarDiagnosticManager.registerListener(
                listener1,
                CarDiagnosticManager.FRAME_TYPE_LIVE,
                android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);
        mCarDiagnosticManager.registerListener(
                listener2,
                CarDiagnosticManager.FRAME_TYPE_LIVE,
                android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);

        listener1.reset();
        listener2.reset();

        long time = SystemClock.elapsedRealtimeNanos();
        getMockedVehicleHal().injectEvent(mLiveFrameEventBuilder.build(time));
        assertTrue(listener1.waitForEvent(time));
        assertTrue(listener2.waitForEvent(time));

        CarDiagnosticEvent event1 = listener1.getLastEvent();
        CarDiagnosticEvent event2 = listener2.getLastEvent();

        assertTrue(event1.equals(event1));
        assertTrue(event2.equals(event2));
        assertTrue(event1.equals(event2));
        assertTrue(event2.equals(event1));

        assertTrue(event1.hashCode() == event1.hashCode());
        assertTrue(event1.hashCode() == event2.hashCode());

        assertEquals(
                5000,
                event1.getSystemIntegerSensor(IntegerSensorIndex.RUNTIME_SINCE_ENGINE_START)
                        .intValue());
        assertEquals(
                5000,
                event2.getSystemIntegerSensor(IntegerSensorIndex.RUNTIME_SINCE_ENGINE_START)
                        .intValue());

        listener1.reset();
        listener2.reset();

        mCarDiagnosticManager.unregisterListener(listener1);

        time += 1000;
        getMockedVehicleHal().injectEvent(mLiveFrameEventBuilder.build(time));
        assertFalse(listener1.waitForEvent(time));
        assertTrue(listener2.waitForEvent(time));

        assertNull(listener1.getLastEvent());
        event2 = listener2.getLastEvent();

        assertTrue(event1.isEarlierThan(event2));
        assertFalse(event1.equals(event2));
        assertFalse(event2.equals(event1));

        assertEquals(
                5000,
                event2.getSystemIntegerSensor(IntegerSensorIndex.RUNTIME_SINCE_ENGINE_START)
                        .intValue());
    }

    public void testFreezeFrameEvent() throws Exception {
        Listener listener = new Listener();
        mCarDiagnosticManager.registerListener(
                listener,
                CarDiagnosticManager.FRAME_TYPE_FREEZE,
                android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);

        listener.reset();
        VehiclePropValue injectedEvent =
                mFreezeFrameProperties.addNewEvent(mFreezeFrameEventBuilder);
        getMockedVehicleHal().injectEvent(injectedEvent);
        assertTrue(listener.waitForEvent(injectedEvent.timestamp));

        CarDiagnosticEvent freezeFrame = listener.getLastEvent();

        assertEquals(DTC, freezeFrame.dtc);

        mFreezeFrameEventBuilder.addIntSensor(
                IntegerSensorIndex.ABSOLUTE_BAROMETRIC_PRESSURE, 22);
        injectedEvent = mFreezeFrameProperties.addNewEvent(mFreezeFrameEventBuilder);
        getMockedVehicleHal().injectEvent(injectedEvent);
        assertTrue(listener.waitForEvent(injectedEvent.timestamp));

        freezeFrame = listener.getLastEvent();

        assertNotNull(freezeFrame);
        assertFalse(freezeFrame.isLiveFrame());
        assertTrue(freezeFrame.isFreezeFrame());
        assertFalse(freezeFrame.isEmptyFrame());

        assertEquals(DTC, freezeFrame.dtc);
        assertEquals(
                22,
                freezeFrame
                        .getSystemIntegerSensor(IntegerSensorIndex.ABSOLUTE_BAROMETRIC_PRESSURE)
                        .intValue());
    }

    public void testFreezeFrameTimestamps() throws Exception {
        Listener listener = new Listener();
        mCarDiagnosticManager.registerListener(
                listener,
                CarDiagnosticManager.FRAME_TYPE_FREEZE,
                android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);

        Set<Long> generatedTimestamps = new HashSet<>();

        VehiclePropValue injectedEvent =
                mFreezeFrameProperties.addNewEvent(mFreezeFrameEventBuilder);
        getMockedVehicleHal().injectEvent(injectedEvent);
        generatedTimestamps.add(injectedEvent.timestamp);
        assertTrue(listener.waitForEvent(injectedEvent.timestamp));

        injectedEvent =
                mFreezeFrameProperties.addNewEvent(
                        mFreezeFrameEventBuilder, injectedEvent.timestamp + 1000);
        getMockedVehicleHal().injectEvent(injectedEvent);
        generatedTimestamps.add(injectedEvent.timestamp);
        assertTrue(listener.waitForEvent(injectedEvent.timestamp));

        long[] acquiredTimestamps = mCarDiagnosticManager.getFreezeFrameTimestamps();
        assertEquals(generatedTimestamps.size(), acquiredTimestamps.length);
        for (long acquiredTimestamp : acquiredTimestamps) {
            assertTrue(generatedTimestamps.contains(acquiredTimestamp));
        }
    }

    public void testClearFreezeFrameTimestamps() throws Exception {
        Listener listener = new Listener();
        mCarDiagnosticManager.registerListener(
                listener,
                CarDiagnosticManager.FRAME_TYPE_FREEZE,
                android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);

        VehiclePropValue injectedEvent =
                mFreezeFrameProperties.addNewEvent(mFreezeFrameEventBuilder);
        getMockedVehicleHal().injectEvent(injectedEvent);
        assertTrue(listener.waitForEvent(injectedEvent.timestamp));

        assertNotNull(mCarDiagnosticManager.getFreezeFrame(injectedEvent.timestamp));
        mCarDiagnosticManager.clearFreezeFrames(injectedEvent.timestamp);
        assertNull(mCarDiagnosticManager.getFreezeFrame(injectedEvent.timestamp));
    }

    public void testListenerUnregister() throws Exception {
        Listener listener1 = new Listener();
        Listener listener2 = new Listener();
        mCarDiagnosticManager.registerListener(
            listener1,
            CarDiagnosticManager.FRAME_TYPE_LIVE,
            android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);
        mCarDiagnosticManager.registerListener(
            listener1,
            CarDiagnosticManager.FRAME_TYPE_FREEZE,
            android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);

        mCarDiagnosticManager.unregisterListener(listener1);

        // you need a listener to be registered before MockedVehicleHal will actually dispatch
        // your events - add one, but do it *after* unregistering the first listener
        mCarDiagnosticManager.registerListener(
            listener2,
            CarDiagnosticManager.FRAME_TYPE_LIVE,
            android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);
        mCarDiagnosticManager.registerListener(
            listener2,
            CarDiagnosticManager.FRAME_TYPE_FREEZE,
            android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);

        VehiclePropValue injectedEvent =
            mFreezeFrameProperties.addNewEvent(mFreezeFrameEventBuilder);
        long time = injectedEvent.timestamp;
        getMockedVehicleHal().injectEvent(injectedEvent);
        assertFalse(listener1.waitForEvent(time));
        assertTrue(listener2.waitForEvent(time));

        time += 1000;
        getMockedVehicleHal().injectEvent(mLiveFrameEventBuilder.build(time));
        assertFalse(listener1.waitForEvent(time));
        assertTrue(listener2.waitForEvent(time));
    }

    public void testIsSupportedApiCalls() throws Exception {
        assertTrue(mCarDiagnosticManager.isLiveFrameSupported());
        assertTrue(mCarDiagnosticManager.isFreezeFrameNotificationSupported());
        assertTrue(mCarDiagnosticManager.isGetFreezeFrameSupported());
        assertTrue(mCarDiagnosticManager.isClearFreezeFramesSupported());
    }

    class Listener implements CarDiagnosticManager.OnDiagnosticEventListener {
        private final Object mSync = new Object();

        private CarDiagnosticEvent mLastEvent = null;

        CarDiagnosticEvent getLastEvent() {
            return mLastEvent;
        }

        void reset() {
            synchronized (mSync) {
                mLastEvent = null;
            }
        }

        boolean waitForEvent() throws InterruptedException {
            return waitForEvent(0);
        }

        boolean waitForEvent(long eventTimeStamp) throws InterruptedException {
            long start = SystemClock.elapsedRealtime();
            boolean matchTimeStamp = eventTimeStamp != 0;
            synchronized (mSync) {
                while ((mLastEvent == null
                                || (matchTimeStamp && mLastEvent.timestamp != eventTimeStamp))
                        && (start + SHORT_WAIT_TIMEOUT_MS > SystemClock.elapsedRealtime())) {
                    mSync.wait(10L);
                }
                return mLastEvent != null
                        && (!matchTimeStamp || mLastEvent.timestamp == eventTimeStamp);
            }
        }

        @Override
        public void onDiagnosticEvent(CarDiagnosticEvent event) {
            synchronized (mSync) {
                // We're going to hold a reference to this object
                mLastEvent = event;
                mSync.notify();
            }
        }
    }
}
