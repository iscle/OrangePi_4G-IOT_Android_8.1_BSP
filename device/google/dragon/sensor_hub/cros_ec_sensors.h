/*
 * Copyright (C) 2008-2014 The Android Open Source Project
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

#ifndef CROS_EC_SENSORS_H
#define CROS_EC_SENSORS_H

#include <errno.h>
#include <stdint.h>
#include <sys/cdefs.h>
#include <sys/types.h>
#include <utils/BitSet.h>

#include <hardware/sensors.h>

#define IIO_DIR  "/sys/bus/iio/devices/"
#define IIO_MAX_NAME_LENGTH 30
#define IIO_MAX_BUFF_SIZE 4096
#define INT32_CHAR_LEN 12

/*
 * Use sizeof(...) - 1 instead of strlen because clang FORTIFY makes strlen
 * non-constant.
 */
#define IIO_MAX_DEVICE_NAME_LENGTH (sizeof(IIO_DIR) - 1 + IIO_MAX_NAME_LENGTH)

#define CROS_EC_MAX_SAMPLING_PERIOD ((1 << 16) - 2)

enum {X, Y, Z, MAX_AXIS};

extern const char *cros_ec_sensor_names[];

#define CROS_EC_EVENT_FLUSH_FLAG 0x1
#define CROS_EC_EVENT_WAKEUP_FLAG 0x2

#define CROS_EC_MAX_PHYSICAL_SENSOR 256

enum cros_ec_gesture {
    CROS_EC_SIGMO,
    CROS_EC_MAX_GESTURE,
};


/*****************************************************************************/
/* from ec_commands.h */
struct cros_ec_event {
    uint8_t sensor_id;
    uint8_t flags;
    union {
        int16_t vector[MAX_AXIS];
        struct {
            uint8_t activity;
            uint8_t state;
            uint16_t add_info[2];
        };
    };
    uint64_t timestamp;
} __packed;

enum motionsensor_activity {
    MOTIONSENSE_ACTIVITY_RESERVED = 0,
    MOTIONSENSE_ACTIVITY_SIG_MOTION = 1,
    MOTIONSENSE_MAX_ACTIVITY,
};


/*****************************************************************************/

enum cros_ec_sensor_device {
    CROS_EC_ACCEL,
    CROS_EC_GYRO,
    CROS_EC_MAG,
    CROS_EC_PROX,
    CROS_EC_LIGHT,
    CROS_EC_ACTIVITY,
    CROS_EC_RING, /* should be the last device */
    CROS_EC_MAX_DEVICE,
};

struct cros_ec_sensor_info {
    /* description of the sensor, as reported to sensorservice. */
    sensor_t sensor_data;
    enum cros_ec_sensor_device type;
    const char *device_name;
    int64_t sampling_period_ns;
    int64_t max_report_latency_ns;
    bool enabled;
};

struct cros_ec_gesture_info {
    /* For activities managed by the sensor interface */
    sensor_t sensor_data;
    const char *device_name;
    const char *enable_entry;
    bool enabled;
};

/*
 * To write sysfs parameters: IIO_DIR is appended before path.
 */
int cros_ec_sysfs_set_input_attr(const char *path, const char *attr, const char *value, size_t len);
int cros_ec_sysfs_set_input_attr_by_int(const char *path, const char *attr, int value);

/*
 * To read sysfs parameters: IIO_DIR is NOT appended.
 */
int cros_ec_sysfs_get_attr(const char *path, const char *attr, char *output);

class CrosECSensor {
    struct cros_ec_sensor_info *mSensorInfo;
    size_t mSensorNb;
    struct cros_ec_gesture_info *mGestureInfo;
    size_t mGestureNb;
    char mRingPath[IIO_MAX_DEVICE_NAME_LENGTH];
    cros_ec_event mEvents[IIO_MAX_BUFF_SIZE];
    int mDataFd;

    int processEvent(sensors_event_t* data, const cros_ec_event *event);
public:
    CrosECSensor(
        struct cros_ec_sensor_info *sensor_info,
        size_t sensor_nb,
        struct cros_ec_gesture_info *gesture_info,
        size_t gesture_nb,
        const char *ring_device_name,
        const char *trigger_name);
    virtual ~CrosECSensor();
    virtual int getFd(void);
    int readEvents(sensors_event_t* data, int count);

    virtual int activate(int handle, int enabled);
    virtual int batch(int handle, int64_t period_ns, int64_t timeout);
    virtual int flush(int handle);
};

#endif  // CROS_EC_SENSORS_H
