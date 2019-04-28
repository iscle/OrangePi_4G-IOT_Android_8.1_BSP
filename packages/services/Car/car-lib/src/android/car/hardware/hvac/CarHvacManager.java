/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.car.hardware.hvac;

import android.annotation.IntDef;
import android.annotation.SystemApi;
import android.car.Car;
import android.car.CarManagerBase;
import android.car.CarNotConnectedException;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManagerBase;
import android.car.hardware.property.CarPropertyManagerBase.CarPropertyEventCallback;
import android.content.Context;
import android.os.Handler;
import android.os.IBinder;
import android.util.ArraySet;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.List;

/**
 * API for controlling HVAC system in cars
 * @hide
 */
@SystemApi
public final class CarHvacManager implements CarManagerBase {
    private final static boolean DBG = false;
    private final static String TAG = "CarHvacManager";
    private final CarPropertyManagerBase mMgr;
    private final ArraySet<CarHvacEventCallback> mCallbacks = new ArraySet<>();
    private CarPropertyEventListenerToBase mListenerToBase = null;

    /**
     * HVAC property IDs for get/set methods
     */
    /**
     * Global HVAC properties.  There is only a single instance in a car.
     * Global properties are in the range of 0-0x3FFF.
     */
    /**
     * Mirror defrosters state, bool type
     * true indicates mirror defroster is on
     */
    public static final int ID_MIRROR_DEFROSTER_ON = 0x0001;
    /**
     * Steering wheel temp, int type
     * Positive values indicate heating.
     * Negative values indicate cooling
     */
    public static final int ID_STEERING_WHEEL_TEMP = 0x0002;
    /**
     * Outside air temperature, float type
     * Value is in degrees of ID_TEMPERATURE_UNITS
     */
    public static final int ID_OUTSIDE_AIR_TEMP = 0x0003;
    /**
     * Temperature units being used, int type
     *  0x30 = Celsius
     *  0x31 = Fahrenheit
     */
    public static final int ID_TEMPERATURE_UNITS = 0x0004;


    /**
     * The maximum id that can be assigned to global (non-zoned) property.
     * @hide
     */
    public static final int ID_MAX_GLOBAL_PROPERTY_ID = 0x3fff;

    /**
     * ID_ZONED_* represents properties available on a per-zone basis.  All zones in a car are
     * not required to have the same properties.  Zone specific properties start at 0x4000.
     */
    /**
     * Temperature setpoint, float type
     * Temperature set by the user, units are determined by ID_TEMPERTURE_UNITS property.
     */
    public static final int ID_ZONED_TEMP_SETPOINT = 0x4001;
    /**
     * Actual temperature, float type
     * Actual zone temperature is read only value, in terms of F or C.
     */
    public static final int ID_ZONED_TEMP_ACTUAL = 0x4002;
    /**
     * HVAC system powered on / off, bool type
     * In many vehicles, if the HVAC system is powered off, the SET and GET command will
     * throw an IllegalStateException.  To correct this, need to turn on the HVAC module first
     * before manipulating a parameter.
     */
    public static final int ID_ZONED_HVAC_POWER_ON = 0x4003;
    /**
     * Fan speed setpoint, int type
     * Fan speed is an integer from 0-n, depending on number of fan speeds available.
     */
    public static final int ID_ZONED_FAN_SPEED_SETPOINT = 0x4004;
    /**
     * Actual fan speed, int type
     * Actual fan speed is a read-only value, expressed in RPM.
     */
    public static final int ID_ZONED_FAN_SPEED_RPM = 0x4005;
    /** Fan position available, int type
     *  Fan position is a bitmask of positions available for each zone.
     */
    public static final int ID_ZONED_FAN_POSITION_AVAILABLE = 0x4006;
    /**
     * Current fan position setting, int type. The value must be one of the FAN_POSITION_*
     * constants declared in {@link CarHvacManager}.
     */
    public static final int ID_ZONED_FAN_POSITION = 0x4007;
    /**
     * Seat temperature, int type
     * Seat temperature is negative for cooling, positive for heating.  Temperature is a
     * setting, i.e. -3 to 3 for 3 levels of cooling and 3 levels of heating.
     */
    public static final int ID_ZONED_SEAT_TEMP = 0x4008;
    /**
     * Air ON, bool type
     * true indicates AC is ON.
     */
    public static final int ID_ZONED_AC_ON = 0x4009;
    /**
     * Automatic Mode ON, bool type
     * true indicates HVAC is in automatic mode
     */
    public static final int ID_ZONED_AUTOMATIC_MODE_ON = 0x400A;
    /**
     * Air recirculation ON, bool type
     * true indicates recirculation is active.
     */
    public static final int ID_ZONED_AIR_RECIRCULATION_ON = 0x400B;
    /**
     * Max AC ON, bool type
     * true indicates MAX AC is ON
     */
    public static final int ID_ZONED_MAX_AC_ON = 0x400C;
    /** Dual zone ON, bool type
     * true indicates dual zone mode is ON
     */
    public static final int ID_ZONED_DUAL_ZONE_ON = 0x400D;
    /**
     * Max Defrost ON, bool type
     * true indicates max defrost is active.
     */
    public static final int ID_ZONED_MAX_DEFROST_ON = 0x400E;
    /**
     * Automatic recirculation mode ON
     * true indicates recirculation is in automatic mode
     */
    public static final int ID_ZONED_HVAC_AUTO_RECIRC_ON = 0x400F;
    /**
     * Defroster ON, bool type
     * Defroster controls are based on window position.
     * True indicates the defroster is ON.
     */
    public static final int ID_WINDOW_DEFROSTER_ON = 0x5001;

    /** @hide */
    @IntDef({
            ID_MIRROR_DEFROSTER_ON,
            ID_STEERING_WHEEL_TEMP,
            ID_OUTSIDE_AIR_TEMP,
            ID_TEMPERATURE_UNITS,
            ID_ZONED_TEMP_SETPOINT,
            ID_ZONED_TEMP_ACTUAL,
            ID_ZONED_FAN_SPEED_SETPOINT,
            ID_ZONED_FAN_SPEED_RPM,
            ID_ZONED_FAN_POSITION_AVAILABLE,
            ID_ZONED_FAN_POSITION,
            ID_ZONED_SEAT_TEMP,
            ID_ZONED_AC_ON,
            ID_ZONED_AUTOMATIC_MODE_ON,
            ID_ZONED_AIR_RECIRCULATION_ON,
            ID_ZONED_MAX_AC_ON,
            ID_ZONED_DUAL_ZONE_ON,
            ID_ZONED_MAX_DEFROST_ON,
            ID_ZONED_HVAC_POWER_ON,
            ID_ZONED_HVAC_AUTO_RECIRC_ON,
            ID_WINDOW_DEFROSTER_ON,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface PropertyId {}

    /**
     * Represents fan position when air flows through face directed vents.
     * This constant must be used with {@link #ID_ZONED_FAN_POSITION} property.
     */
    public static final int FAN_POSITION_FACE = 1;
    /**
     * Represents fan position when air flows through floor directed vents.
     * This constant must be used with {@link #ID_ZONED_FAN_POSITION} property.
     */
    public static final int FAN_POSITION_FLOOR = 2;
    /**
     * Represents fan position when air flows through face and floor directed vents.
     * This constant must be used with {@link #ID_ZONED_FAN_POSITION} property.
     */
    public static final int FAN_POSITION_FACE_AND_FLOOR = 3;
    /**
     * Represents fan position when air flows through defrost vents.
     * This constant must be used with {@link #ID_ZONED_FAN_POSITION} property.
     */
    public static final int FAN_POSITION_DEFROST = 4;
    /**
     * Represents fan position when air flows through defrost and floor directed vents.
     * This constant must be used with {@link #ID_ZONED_FAN_POSITION} property.
     */
    public static final int FAN_POSITION_DEFROST_AND_FLOOR = 5;

    /**
     * Application registers {@link CarHvacEventCallback} object to receive updates and changes to
     * subscribed Car HVAC properties.
     */
    public interface CarHvacEventCallback {
        /**
         * Called when a property is updated
         * @param value Property that has been updated.
         */
        void onChangeEvent(CarPropertyValue value);

        /**
         * Called when an error is detected with a property
         * @param propertyId
         * @param zone
         */
        void onErrorEvent(@PropertyId int propertyId, int zone);
    }

    private static class CarPropertyEventListenerToBase implements CarPropertyEventCallback {
        private final WeakReference<CarHvacManager> mManager;

        public CarPropertyEventListenerToBase(CarHvacManager manager) {
            mManager = new WeakReference<>(manager);
        }

        @Override
        public void onChangeEvent(CarPropertyValue value) {
            CarHvacManager manager = mManager.get();
            if (manager != null) {
                manager.handleOnChangeEvent(value);
            }
        }

        @Override
        public void onErrorEvent(int propertyId, int zone) {
            CarHvacManager manager = mManager.get();
            if (manager != null) {
                manager.handleOnErrorEvent(propertyId, zone);
            }
        }
    }

    private void handleOnChangeEvent(CarPropertyValue value) {
        Collection<CarHvacEventCallback> callbacks;
        synchronized (this) {
            callbacks = new ArraySet<>(mCallbacks);
        }
        if (!callbacks.isEmpty()) {
            for (CarHvacEventCallback l: callbacks) {
                l.onChangeEvent(value);
            }
        }
    }

    private void handleOnErrorEvent(int propertyId, int zone) {
        Collection<CarHvacEventCallback> callbacks;
        synchronized (this) {
            callbacks = new ArraySet<>(mCallbacks);
        }
        if (!callbacks.isEmpty()) {
            for (CarHvacEventCallback l: callbacks) {
                l.onErrorEvent(propertyId, zone);
            }
        }
    }

    /**
     * Get an instance of the CarHvacManager.
     *
     * Should not be obtained directly by clients, use {@link Car#getCarManager(String)} instead.
     * @param service
     * @param context
     * @param handler
     * @hide
     */
    public CarHvacManager(IBinder service, Context context, Handler handler) {
        mMgr = new CarPropertyManagerBase(service, handler, DBG, TAG);
    }

    /**
     * Determine if a property is zoned or not.
     * @param propertyId
     * @return true if property is a zoned type.
     */
    public static boolean isZonedProperty(@PropertyId int propertyId) {
        return propertyId > ID_MAX_GLOBAL_PROPERTY_ID;
    }

    /**
     * Implement wrappers for contained CarPropertyManagerBase object
     * @param callback
     * @throws CarNotConnectedException
     */
    public synchronized void registerCallback(CarHvacEventCallback callback) throws
            CarNotConnectedException {
        if (mCallbacks.isEmpty()) {
            mListenerToBase = new CarPropertyEventListenerToBase(this);
            mMgr.registerCallback(mListenerToBase);
        }
        mCallbacks.add(callback);
    }

    /**
     * Stop getting property updates for the given callback. If there are multiple registrations for
     * this listener, all listening will be stopped.
     * @param callback
     */
    public synchronized void unregisterCallback(CarHvacEventCallback callback) {
        mCallbacks.remove(callback);
        if (mCallbacks.isEmpty()) {
            mMgr.unregisterCallback();
            mListenerToBase = null;
        }
    }

    /**
     * Get list of properties available to Car Hvac Manager
     * @return List of CarPropertyConfig objects available via Car Hvac Manager.
     * @throws CarNotConnectedException if the connection to the car service has been lost.
     */
    public List<CarPropertyConfig> getPropertyList() throws CarNotConnectedException {
        return mMgr.getPropertyList();
    }

    /**
     * Get value of boolean property
     * @param propertyId
     * @param area
     * @return value of requested boolean property
     * @throws CarNotConnectedException
     */
    public boolean getBooleanProperty(@PropertyId int propertyId, int area)
            throws CarNotConnectedException {
        return mMgr.getBooleanProperty(propertyId, area);
    }

    /**
     * Get value of float property
     * @param propertyId
     * @param area
     * @return value of requested float property
     * @throws CarNotConnectedException
     */
    public float getFloatProperty(@PropertyId int propertyId, int area)
            throws CarNotConnectedException {
        return mMgr.getFloatProperty(propertyId, area);
    }

    /**
     * Get value of integer property
     * @param propertyId
     * @param area
     * @return value of requested integer property
     * @throws CarNotConnectedException
     */
    public int getIntProperty(@PropertyId int propertyId, int area)
            throws CarNotConnectedException {
        return mMgr.getIntProperty(propertyId, area);
    }

    /**
     * Set the value of a boolean property
     * @param propertyId
     * @param area
     * @param val
     * @throws CarNotConnectedException
     */
    public void setBooleanProperty(@PropertyId int propertyId, int area, boolean val)
            throws CarNotConnectedException {
        mMgr.setBooleanProperty(propertyId, area, val);
    }

    /**
     * Set the value of a float property
     * @param propertyId
     * @param area
     * @param val
     * @throws CarNotConnectedException
     */
    public void setFloatProperty(@PropertyId int propertyId, int area, float val)
            throws CarNotConnectedException {
        mMgr.setFloatProperty(propertyId, area, val);
    }

    /**
     * Set the value of an integer property
     * @param propertyId
     * @param area
     * @param val
     * @throws CarNotConnectedException
     */
    public void setIntProperty(@PropertyId int propertyId, int area, int val)
            throws CarNotConnectedException {
        mMgr.setIntProperty(propertyId, area, val);
    }

    /** @hide */
    @Override
    public void onCarDisconnected() {
        mMgr.onCarDisconnected();
    }
}
