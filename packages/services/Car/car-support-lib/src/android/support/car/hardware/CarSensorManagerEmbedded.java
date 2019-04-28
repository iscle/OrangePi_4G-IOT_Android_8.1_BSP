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

package android.support.car.hardware;

import android.content.Context;
import android.support.annotation.RestrictTo;
import android.support.car.CarNotConnectedException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import static android.support.annotation.RestrictTo.Scope.GROUP_ID;

/**
 *  @hide
 */
@RestrictTo(GROUP_ID)
public class CarSensorManagerEmbedded extends CarSensorManager {
    private static final String TAG = "CarSensorsProxy";

    private final android.car.hardware.CarSensorManager mManager;
    private final CarSensorsProxy mCarSensorsProxy;
    private final LinkedList<OnSensorChangedListenerProxy> mListeners = new LinkedList<>();

    public CarSensorManagerEmbedded(Object manager, Context context) {
        mManager = (android.car.hardware.CarSensorManager) manager;
        mCarSensorsProxy = new CarSensorsProxy(this, context);
    }

    @Override
    public int[] getSupportedSensors() throws CarNotConnectedException {
        try {
            Set<Integer> sensorsSet = new HashSet<Integer>();
            for (Integer sensor : mManager.getSupportedSensors()) {
                sensorsSet.add(sensor);
            }
            for (Integer proxySensor : mCarSensorsProxy.getSupportedSensors()) {
                sensorsSet.add(proxySensor);
            }
            return toIntArray(sensorsSet);
        } catch (android.car.CarNotConnectedException e) {
            throw new CarNotConnectedException(e);
        }
    }

    private static int[] toIntArray(Collection<Integer> collection) {
        int len = collection.size();
        int[] arr = new int[len];
        int arrIndex = 0;
        for (Integer item : collection) {
            arr[arrIndex] = item;
            arrIndex++;
        }
        return arr;
    }

    @Override
    public boolean isSensorSupported(int sensorType) throws CarNotConnectedException {
        try {
            return mManager.isSensorSupported(sensorType)
                    || mCarSensorsProxy.isSensorSupported(sensorType);
        } catch (android.car.CarNotConnectedException e) {
            throw new CarNotConnectedException(e);
        }
    }

    private boolean isSensorProxied(int sensorType) throws CarNotConnectedException {
        try {
            return !mManager.isSensorSupported(sensorType)
                    && mCarSensorsProxy.isSensorSupported(sensorType);
        } catch (android.car.CarNotConnectedException e) {
            throw new CarNotConnectedException(e);
        }
    }

    @Override
    public boolean addListener(OnSensorChangedListener listener, int sensorType,
            int rate) throws CarNotConnectedException, IllegalArgumentException {
        if (isSensorProxied(sensorType)) {
            return mCarSensorsProxy.registerSensorListener(listener, sensorType, rate);
        }
        OnSensorChangedListenerProxy proxy = null;
        synchronized (this) {
            proxy = findListenerLocked(listener);
            if (proxy == null) {
                proxy = new OnSensorChangedListenerProxy(listener, sensorType, this);
                mListeners.add(proxy);
            } else {
                proxy.sensors.add(sensorType);
            }
        }
        try {
            return mManager.registerListener(proxy, sensorType, rate);
        } catch (android.car.CarNotConnectedException e) {
            throw new CarNotConnectedException(e);
        }
    }

    @Override
    public void removeListener(OnSensorChangedListener listener) {
        mCarSensorsProxy.unregisterSensorListener(listener);
        OnSensorChangedListenerProxy proxy = null;
        synchronized (this) {
            proxy = findListenerLocked(listener);
            if (proxy == null) {
                return;
            }
            mListeners.remove(proxy);
        }
        mManager.unregisterListener(proxy);
    }

    @Override
    public void removeListener(OnSensorChangedListener listener, int sensorType) {
        mCarSensorsProxy.unregisterSensorListener(listener, sensorType);
        OnSensorChangedListenerProxy proxy = null;
        synchronized (this) {
            proxy = findListenerLocked(listener);
            if (proxy == null) {
                return;
            }
            proxy.sensors.remove(sensorType);
            if (proxy.sensors.isEmpty()) {
                mListeners.remove(proxy);
            }
        }
        mManager.unregisterListener(proxy, sensorType);
    }

    @Override
    public CarSensorEvent getLatestSensorEvent(int type) throws CarNotConnectedException {
        if (isSensorProxied(type)) {
            return mCarSensorsProxy.getLatestSensorEvent(type);
        }
        try {
            return convert(mManager.getLatestSensorEvent(type));
        } catch (android.car.CarNotConnectedException e) {
            throw new CarNotConnectedException(e);
        }
    }

    @Override
    public CarSensorConfig getSensorConfig(@SensorType int type)
        throws CarNotConnectedException {
        try {
            return convert(mManager.getSensorConfig(type));
        } catch (android.car.CarNotConnectedException e) {
            throw new CarNotConnectedException(e);
        }
    }

    @Override
    public void onCarDisconnected() {
        //nothing to do
    }

    private OnSensorChangedListenerProxy findListenerLocked(OnSensorChangedListener listener) {
        for (OnSensorChangedListenerProxy proxy : mListeners) {
            if (proxy.listener == listener) {
                return proxy;
            }
        }
        return null;
    }

    private static CarSensorEvent convert(android.car.hardware.CarSensorEvent event) {
        if (event == null) {
            return null;
        }
        return new CarSensorEvent(event.sensorType, event.timestamp, event.floatValues,
                event.intValues, event.longValues);
    }

    private static CarSensorConfig convert(android.car.hardware.CarSensorConfig cfg) {
        if (cfg == null) {
            return null;
        }
        return new CarSensorConfig(cfg.getType(), cfg.getBundle());
    }

    private static class OnSensorChangedListenerProxy
            implements android.car.hardware.CarSensorManager.OnSensorChangedListener {

        public final OnSensorChangedListener listener;
        public final Set<Integer> sensors = new HashSet<>();
        public final CarSensorManager manager;

        OnSensorChangedListenerProxy(OnSensorChangedListener listener, int sensor,
                CarSensorManager manager) {
            this.listener = listener;
            this.sensors.add(sensor);
            this.manager = manager;
        }

        @Override
        public void onSensorChanged(android.car.hardware.CarSensorEvent event) {
            CarSensorEvent newEvent = convert(event);
            listener.onSensorChanged(manager, newEvent);
        }
    }
}
