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

#ifndef __HALINTF_H
#define __HALINTF_H
#include <stdint.h>
#include "toolchain.h"
#include "sensType.h"

/*
 * This files contains data structure for HAL and driver / algo to exchange information.
 */

#define APP_TO_SENSOR_HAL_SIZE_MAX      HOSTINTF_SENSOR_DATA_MAX
#define APP_TO_SENSOR_HAL_PAYLOAD_MAX \
        (APP_TO_SENSOR_HAL_DATA_MAX - sizeof(struct AppToSensorHalDataBuffer))
#define APP_TO_SENSOR_HAL_TYPE(sensor, subtype) (((sensor) && 0xFF) | (((subtype) & 0x7F) << 8))
#define APP_TO_SENSOR_HAL_TYPE_MASK     0x7FFF

enum {
    // gyro sensor calibration:  GyroCalBias
    HALINTF_TYPE_GYRO_CAL_BIAS = APP_TO_SENSOR_HAL_TYPE(SENS_TYPE_GYRO, 1),

    // gyro sensor over temperature calibration: GyroOtcData
    HALINTF_TYPE_GYRO_OTC_DATA = APP_TO_SENSOR_HAL_TYPE(SENS_TYPE_GYRO, 2),

    // mag sensor calibration: MagCalBias
    HALINTF_TYPE_MAG_CAL_BIAS = APP_TO_SENSOR_HAL_TYPE(SENS_TYPE_MAG, 1),

    // mag local field information: MagLocalField
    HALINTF_TYPE_MAG_LOCAL_FIELD = APP_TO_SENSOR_HAL_TYPE(SENS_TYPE_MAG, 2),

    // mag sensor spherical calibration: MagSphericalData
    HALINTF_TYPE_MAG_SPERICAL_DATA = APP_TO_SENSOR_HAL_TYPE(SENS_TYPE_MAG, 3),
};

SET_PACKED_STRUCT_MODE_ON
struct GyroCalBias {
    int32_t hardwareBias[3];   // unit depending on hardware implementation
    float softwareBias[3];          // rad/s
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

SET_PACKED_STRUCT_MODE_ON
struct GyroOtcData {
    float lastOffset[3];    // rad/s
    float lastTemperature;  // Celsius
    float sensitivity[3];   // rad/s/K
    float intercept[3];     // rad/s
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

SET_PACKED_STRUCT_MODE_ON
struct MagCalBias {
    float bias[3];     // in uT
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

SET_PACKED_STRUCT_MODE_ON
struct MagLocalField {
    float strength;    // in uT
    float declination; // in rad
    float inclination; // in rad
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

SET_PACKED_STRUCT_MODE_ON
struct MagSphericalData {
    float bias[3];
    float scale[3];
    float skew[3];
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

// data structure for upload bulk data to sensor hal
SET_PACKED_STRUCT_MODE_ON
struct AppToSensorHalDataPayload {
    uint8_t size;       // number of bytes in payload data
    uint8_t reserved;
    uint16_t type;      // use EVENT_TYPE_BIT_DISCARDABLE to mark discardable update
    union
    {
        uint32_t u[0];
        int32_t i[0];
        float f[0];
        struct GyroCalBias gyroCalBias[0];
        struct GyroOtcData gyroOtcData[0];
        struct MagCalBias  magCalBias[0];
        struct MagLocalField magLocalField[0];
        struct MagSphericalData magSphericalData[0];
    };
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

// buffer data structure with header, header compatible with HostIntfDataBuffer
SET_PACKED_STRUCT_MODE_ON
struct AppToSensorHalDataBuffer {
    uint32_t eventType; // placeholder for HostIntfDataBuffer event type field
    struct AppToSensorHalDataPayload payload;
};
SET_PACKED_STRUCT_MODE_OFF

#endif //__HALINTF_H

