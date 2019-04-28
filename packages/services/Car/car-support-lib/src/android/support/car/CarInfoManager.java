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

package android.support.car;

import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Utility to retrieve various static information from car.
 */
public abstract class CarInfoManager implements CarManagerBase {

    /** Location of the driver is unknown. */
    public static final int DRIVER_SIDE_UNKNOWN = 0;
    /** Location of the driver: left. */
    public static final int DRIVER_SIDE_LEFT   = 1;
    /** Location of the driver: right. */
    public static final int DRIVER_SIDE_RIGHT  = 2;
    /** Location of the driver: center. */
    public static final int DRIVER_SIDE_CENTER = 3;

    /** @hide */
    @IntDef({
        DRIVER_SIDE_UNKNOWN,
        DRIVER_SIDE_LEFT,
        DRIVER_SIDE_RIGHT,
        DRIVER_SIDE_CENTER
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface DriverSide {}

    /**
     * Return manufacturer of the car.
     * @return null if information is not available.
     */
    public abstract @Nullable String getManufacturer() throws CarNotConnectedException;

    /**
     * Return model name of the car. This information may not necessarily allow distinguishing
     * different car models as the same name may be used for different cars depending on
     * manufacturers.
     * @return null if information is not available.
     */
    public abstract @Nullable String getModel() throws CarNotConnectedException;

    /**
     * Return model year of the car in AC.
     * @return null if information is not available.
     */
    public abstract @Nullable String getModelYear() throws CarNotConnectedException;

    /**
     * Return unique identifier for the car. This is not VIN, and id is persistent until user
     * resets it.
     * @return null if information is not available.
     */
    public abstract @Nullable String getVehicleId() throws CarNotConnectedException;

    /**
     * Return manufacturer of the head unit.
     * @return null if information is not available.
     */
    public abstract @Nullable String getHeadunitManufacturer() throws CarNotConnectedException;

    /**
     * Return model of the headunit.
     * @return null if information is not available.
     */
    public abstract @Nullable String getHeadunitModel() throws CarNotConnectedException;

    /**
     * Return S/W build of the headunit.
     * @return null if information is not available.
     */
    public abstract @Nullable String getHeadunitSoftwareBuild() throws CarNotConnectedException;

    /**
     * Return S/W version of the headunit.
     * @return null if information is not available.
     */
    public abstract @Nullable String getHeadunitSoftwareVersion() throws CarNotConnectedException;

    /**
     * Return driver side of the car.
     * @return {@link #DRIVER_SIDE_UNKNOWN} if information is not available.
     */
    public abstract @DriverSide int getDriverPosition() throws CarNotConnectedException;
}
