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

package android.car;

import android.annotation.Nullable;
import android.car.annotation.FutureFeature;
import android.car.annotation.ValueTypeDef;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;


import com.android.internal.annotations.GuardedBy;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;


/**
 * Utility to retrieve various static information from car. Each data are grouped as {@link Bundle}
 * and relevant data can be checked from {@link Bundle} using pre-specified keys.
 */
public final class CarInfoManager implements CarManagerBase {

    /**
     * Key for manufacturer of the car. Passed in basic info Bundle.
     * @hide
     */
    @ValueTypeDef(type = String.class)
    public static final String BASIC_INFO_KEY_MANUFACTURER = "android.car.manufacturer";
    /**
     * Key for model name of the car. This information may not necessarily allow distinguishing
     * different car models as the same name may be used for different cars depending on
     * manufacturers. Passed in basic info Bundle.
     * @hide
     */
    @ValueTypeDef(type = String.class)
    public static final String BASIC_INFO_KEY_MODEL = "android.car.model";
    /**
     * Key for model year of the car in AC. Passed in basic info Bundle.
     * @hide
     */
    @ValueTypeDef(type = Integer.class)
    public static final String BASIC_INFO_KEY_MODEL_YEAR = "android.car.model-year";
    /**
     * Key for unique identifier for the car. This is not VIN, and id is persistent until user
     * resets it. Passed in basic info Bundle.
     * @hide
     */
    @ValueTypeDef(type = String.class)
    public static final String BASIC_INFO_KEY_VEHICLE_ID = "android.car.vehicle-id";

    /**
     * Key for product configuration info.
     * @FutureFeature Cannot drop due to usage in non-flag protected place.
     * @hide
     */
    @ValueTypeDef(type = String.class)
    public static final String INFO_KEY_PRODUCT_CONFIGURATION = "android.car.product-config";

    /* TODO bug: 32059999
    //@ValueTypeDef(type = Integer.class)
    //public static final String KEY_DRIVER_POSITION = "driver-position";

    //@ValueTypeDef(type = int[].class)
    //public static final String KEY_SEAT_CONFIGURATION = "seat-configuration";

    //@ValueTypeDef(type = Integer.class)
    //public static final String KEY_WINDOW_CONFIGURATION = "window-configuration";

    //MT, AT, CVT, ...
    //@ValueTypeDef(type = Integer.class)
    //public static final String KEY_TRANSMISSION_TYPE = "transmission-type";

    // add: transmission gear available selection, gear available steps
    //          drive wheel: FWD, RWD, AWD, 4WD */

    private final ICarInfo mService;

    @GuardedBy("this")
    private Bundle mBasicInfo;

    /**
     * Return manufacturer of the car.
     * @return null if information is not available.
     */
    public @android.annotation.Nullable String getManufacturer() throws CarNotConnectedException {
        return getBasicInfo().getString(BASIC_INFO_KEY_MANUFACTURER);
    }

    /**
     * Return model name of the car. This information may not necessarily allow distinguishing
     * different car models as the same name may be used for different cars depending on
     * manufacturers.
     * @return null if information is not available.
     */
    public @Nullable String getModel() throws CarNotConnectedException {
        return getBasicInfo().getString(BASIC_INFO_KEY_MODEL);
    }

    /**
     * Return model year of the car in AC.
     * @return null if information is not available.
     */
    public @Nullable String getModelYear() throws CarNotConnectedException {
        return getBasicInfo().getString(BASIC_INFO_KEY_MODEL_YEAR);
    }

    /**
     * Return unique identifier for the car. This is not VIN, and id is persistent until user
     * resets it. This ID is guaranteed to be always available.
     * @return vehicle id
     */
    public String getVehicleId() throws CarNotConnectedException {
        return getBasicInfo().getString(BASIC_INFO_KEY_VEHICLE_ID);
    }

    /**
     * Get product configuration string. Contents of this string is product specific but it should
     * be composed of key-value pairs with the format of:
     *   key1=value1;key2=value2;...
     * @return null if such information is not available in this car.
     * @throws CarNotConnectedException
     * @hide
     */
    @FutureFeature
    public @Nullable String getProductConfiguration() throws CarNotConnectedException {
        try {
            return mService.getStringInfo(INFO_KEY_PRODUCT_CONFIGURATION);
        } catch (IllegalStateException e) {
            CarApiUtil.checkCarNotConnectedExceptionFromCarService(e);
        } catch (RemoteException e) {
            throw new CarNotConnectedException(e);
        }
        return null;
    }

    /**
     * Get {@link android.os.Bundle} containing basic car information. Check
     * {@link #BASIC_INFO_KEY_MANUFACTURER}, {@link #BASIC_INFO_KEY_MODEL},
     * {@link #BASIC_INFO_KEY_MODEL_YEAR}, and {@link #BASIC_INFO_KEY_VEHICLE_ID} for supported
     * keys in the {@link android.os.Bundle}.
     * @return {@link android.os.Bundle} containing basic car info.
     * @throws CarNotConnectedException
     */
    private synchronized Bundle getBasicInfo() throws CarNotConnectedException {
        if (mBasicInfo != null) {
            return mBasicInfo;
        }
        try {
            mBasicInfo = mService.getBasicInfo();
        } catch (IllegalStateException e) {
            CarApiUtil.checkCarNotConnectedExceptionFromCarService(e);
        } catch (RemoteException e) {
            throw new CarNotConnectedException(e);
        }
        return mBasicInfo;
    }

    /** @hide */
    CarInfoManager(IBinder service) {
        mService = ICarInfo.Stub.asInterface(service);
    }

    /** @hide */
    @Override
    public void onCarDisconnected() {
        synchronized (this) {
            mBasicInfo = null;
        }
    }
}
