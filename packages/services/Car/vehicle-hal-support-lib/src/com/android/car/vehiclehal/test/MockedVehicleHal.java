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

package com.android.car.vehiclehal.test;

import static java.lang.Integer.toHexString;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.fail;

import android.hardware.automotive.vehicle.V2_0.IVehicle;
import android.hardware.automotive.vehicle.V2_0.IVehicleCallback;
import android.hardware.automotive.vehicle.V2_0.StatusCode;
import android.hardware.automotive.vehicle.V2_0.SubscribeOptions;
import android.hardware.automotive.vehicle.V2_0.VehiclePropConfig;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VehiclePropertyAccess;
import android.os.RemoteException;
import android.os.SystemClock;

import com.google.android.collect.Lists;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mocked implementation of {@link IVehicle}.
 */
public class MockedVehicleHal extends IVehicle.Stub {
    /**
     * Interface for handler of each property.
     */
    public interface VehicleHalPropertyHandler {
        default void onPropertySet(VehiclePropValue value) {}
        default VehiclePropValue onPropertyGet(VehiclePropValue value) { return null; }
        default void onPropertySubscribe(int property, int zones, float sampleRate) {}
        default void onPropertyUnsubscribe(int property) {}

        VehicleHalPropertyHandler NOP = new VehicleHalPropertyHandler() {};
    }

    private final Map<Integer, VehicleHalPropertyHandler> mPropertyHandlerMap = new HashMap<>();
    private final Map<Integer, VehiclePropConfig> mConfigs = new HashMap<>();
    private final Map<Integer, List<IVehicleCallback>> mSubscribers = new HashMap<>();

    public synchronized void addProperties(VehiclePropConfig... configs) {
        for (VehiclePropConfig config : configs) {
            addProperty(config, new DefaultPropertyHandler(config, null));
        }
    }

    public synchronized void addProperty(VehiclePropConfig config,
            VehicleHalPropertyHandler handler) {
        mPropertyHandlerMap.put(config.prop, handler);
        mConfigs.put(config.prop, config);
    }

    public synchronized void addStaticProperty(VehiclePropConfig config,
            VehiclePropValue value) {
        addProperty(config, new StaticPropertyHandler(value));
    }

    public boolean waitForSubscriber(int propId, long timeoutMillis) {
        long startTime = SystemClock.elapsedRealtime();
        try {
            synchronized (this) {
                while (mSubscribers.get(propId) == null) {
                    long waitMillis = startTime - SystemClock.elapsedRealtime() + timeoutMillis;
                    if (waitMillis < 0) break;
                    wait(waitMillis);
                }

                return mSubscribers.get(propId) != null;
            }
        } catch (InterruptedException e) {
            return false;
        }
    }

    public synchronized void injectEvent(VehiclePropValue value) {
        List<IVehicleCallback> callbacks = mSubscribers.get(value.prop);
        assertNotNull("Injecting event failed for property: " + value.prop
                        + ". No listeners found", callbacks);
        for (IVehicleCallback callback : callbacks) {
            try {
                callback.onPropertyEvent(Lists.newArrayList(value));
            } catch (RemoteException e) {
                e.printStackTrace();
                fail("Remote exception while injecting events.");
            }
        }
    }

    public synchronized void injectError(int errorCode, int propertyId, int areaId) {
        List<IVehicleCallback> callbacks = mSubscribers.get(propertyId);
        assertNotNull("Injecting error failed for property: " + propertyId
                        + ". No listeners found", callbacks);
        for (IVehicleCallback callback : callbacks) {
            try {
                callback.onPropertySetError(errorCode, propertyId, areaId);
            } catch (RemoteException e) {
                e.printStackTrace();
                fail("Remote exception while injecting errors.");
            }
        }
    }

    @Override
    public synchronized ArrayList<VehiclePropConfig> getAllPropConfigs() {
        return new ArrayList<>(mConfigs.values());
    }

    @Override
    public synchronized void getPropConfigs(ArrayList<Integer> props, getPropConfigsCallback cb) {
        ArrayList<VehiclePropConfig> res = new ArrayList<>();
        for (Integer prop : props) {
            VehiclePropConfig config = mConfigs.get(prop);
            if (config == null) {
                cb.onValues(StatusCode.INVALID_ARG, new ArrayList<>());
                return;
            }
            res.add(config);
        }
        cb.onValues(StatusCode.OK, res);
    }

    @Override
    public synchronized void get(VehiclePropValue requestedPropValue, getCallback cb) {
        VehicleHalPropertyHandler handler = mPropertyHandlerMap.get(requestedPropValue.prop);
        if (handler == null) {
            cb.onValues(StatusCode.INVALID_ARG, null);
        } else {
            cb.onValues(StatusCode.OK, handler.onPropertyGet(requestedPropValue));
        }
    }

    @Override
    public synchronized int set(VehiclePropValue propValue) {
        VehicleHalPropertyHandler handler = mPropertyHandlerMap.get(propValue.prop);
        if (handler == null) {
            return StatusCode.INVALID_ARG;
        } else {
            handler.onPropertySet(propValue);
            return StatusCode.OK;
        }
    }

    @Override
    public synchronized int subscribe(IVehicleCallback callback,
            ArrayList<SubscribeOptions> options) {
        for (SubscribeOptions opt : options) {
            VehicleHalPropertyHandler handler = mPropertyHandlerMap.get(opt.propId);
            if (handler == null) {
                return StatusCode.INVALID_ARG;
            }

            handler.onPropertySubscribe(opt.propId, opt.vehicleAreas, opt.sampleRate);
            List<IVehicleCallback>  subscribers = mSubscribers.get(opt.propId);
            if (subscribers == null) {
                subscribers = new ArrayList<>();
                mSubscribers.put(opt.propId, subscribers);
                notifyAll();
            }
            subscribers.add(callback);
        }
        return StatusCode.OK;
    }

    @Override
    public synchronized int unsubscribe(IVehicleCallback callback, int propId) {
        VehicleHalPropertyHandler handler = mPropertyHandlerMap.get(propId);
        if (handler == null) {
            return StatusCode.INVALID_ARG;
        }

        handler.onPropertyUnsubscribe(propId);
        List<IVehicleCallback>  subscribers = mSubscribers.get(propId);
        if (subscribers != null) {
            subscribers.remove(callback);
            if (subscribers.size() == 0) {
                mSubscribers.remove(propId);
            }
        }
        return StatusCode.OK;
    }

    @Override
    public String debugDump() {
        return null;
    }

    public static class FailingPropertyHandler implements VehicleHalPropertyHandler {
        @Override
        public void onPropertySet(VehiclePropValue value) {
            fail("Unexpected onPropertySet call");
        }

        @Override
        public VehiclePropValue onPropertyGet(VehiclePropValue value) {
            fail("Unexpected onPropertyGet call");
            return null;
        }

        @Override
        public void onPropertySubscribe(int property, int zones, float sampleRate) {
            fail("Unexpected onPropertySubscribe call");
        }

        @Override
        public void onPropertyUnsubscribe(int property) {
            fail("Unexpected onPropertyUnsubscribe call");
        }
    }

    public static class StaticPropertyHandler extends FailingPropertyHandler {
        private final VehiclePropValue mValue;

        public StaticPropertyHandler(VehiclePropValue value) {
            mValue = value;
        }

        @Override
        public synchronized VehiclePropValue onPropertyGet(VehiclePropValue value) {
            return mValue;
        }
    }

    public static class DefaultPropertyHandler implements VehicleHalPropertyHandler {
        private final VehiclePropConfig mConfig;
        private VehiclePropValue mValue;
        private boolean mSubscribed = false;

        public DefaultPropertyHandler(VehiclePropConfig config, VehiclePropValue initialValue) {
            mConfig = config;
            mValue = initialValue;
        }

        @Override
        public synchronized void onPropertySet(VehiclePropValue value) {
            assertEquals(mConfig.prop, value.prop);
            assertEquals(VehiclePropertyAccess.WRITE, mConfig.access & VehiclePropertyAccess.WRITE);
            mValue = value;
        }

        @Override
        public synchronized VehiclePropValue onPropertyGet(VehiclePropValue value) {
            assertEquals(mConfig.prop, value.prop);
            assertEquals(VehiclePropertyAccess.READ, mConfig.access & VehiclePropertyAccess.READ);
            return mValue;
        }

        @Override
        public synchronized void onPropertySubscribe(int property, int zones, float sampleRate) {
            assertEquals(mConfig.prop, property);
            mSubscribed = true;
        }

        @Override
        public synchronized void onPropertyUnsubscribe(int property) {
            assertEquals(mConfig.prop, property);
            if (!mSubscribed) {
                throw new IllegalArgumentException("Property was not subscribed 0x"
                        + toHexString( property));
            }
            mSubscribed = false;
        }
    }
}
