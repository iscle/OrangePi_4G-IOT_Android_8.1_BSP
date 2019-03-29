/*
 * Copyright (C) 2009 The Android Open Source Project
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

/* this implements a sensors hardware library for the Android emulator.
 * the following code should be built as a shared library that will be
 * placed into /system/lib/hw/sensors.goldfish.so
 *
 * it will be loaded by the code in hardware/libhardware/hardware.c
 * which is itself called from com_android_server_SensorService.cpp
 */


/* we connect with the emulator through the "sensors" qemud service
 */
#define  SENSORS_SERVICE_NAME "sensors"

#define LOG_TAG "QemuSensors"

#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <string.h>
#include <log/log.h>
#include <cutils/sockets.h>
#include <hardware/sensors.h>

#if 0
#define  D(...)  ALOGD(__VA_ARGS__)
#else
#define  D(...)  ((void)0)
#endif

#define  E(...)  ALOGE(__VA_ARGS__)

#include "qemud.h"

/** SENSOR IDS AND NAMES
 **/

#define MAX_NUM_SENSORS 10

#define SUPPORTED_SENSORS  ((1<<MAX_NUM_SENSORS)-1)

#define  ID_BASE                        SENSORS_HANDLE_BASE
#define  ID_ACCELERATION                (ID_BASE+0)
#define  ID_GYROSCOPE                   (ID_BASE+1)
#define  ID_MAGNETIC_FIELD              (ID_BASE+2)
#define  ID_ORIENTATION                 (ID_BASE+3)
#define  ID_TEMPERATURE                 (ID_BASE+4)
#define  ID_PROXIMITY                   (ID_BASE+5)
#define  ID_LIGHT                       (ID_BASE+6)
#define  ID_PRESSURE                    (ID_BASE+7)
#define  ID_HUMIDITY                    (ID_BASE+8)
#define  ID_MAGNETIC_FIELD_UNCALIBRATED (ID_BASE+9)

#define  SENSORS_ACCELERATION                 (1 << ID_ACCELERATION)
#define  SENSORS_GYROSCOPE                    (1 << ID_GYROSCOPE)
#define  SENSORS_MAGNETIC_FIELD               (1 << ID_MAGNETIC_FIELD)
#define  SENSORS_ORIENTATION                  (1 << ID_ORIENTATION)
#define  SENSORS_TEMPERATURE                  (1 << ID_TEMPERATURE)
#define  SENSORS_PROXIMITY                    (1 << ID_PROXIMITY)
#define  SENSORS_LIGHT                        (1 << ID_LIGHT)
#define  SENSORS_PRESSURE                     (1 << ID_PRESSURE)
#define  SENSORS_HUMIDITY                     (1 << ID_HUMIDITY)
#define  SENSORS_MAGNETIC_FIELD_UNCALIBRATED  (1 << ID_MAGNETIC_FIELD_UNCALIBRATED)

#define  ID_CHECK(x)  ((unsigned)((x) - ID_BASE) < MAX_NUM_SENSORS)

#define  SENSORS_LIST  \
    SENSOR_(ACCELERATION,"acceleration") \
    SENSOR_(GYROSCOPE,"gyroscope") \
    SENSOR_(MAGNETIC_FIELD,"magnetic-field") \
    SENSOR_(ORIENTATION,"orientation") \
    SENSOR_(TEMPERATURE,"temperature") \
    SENSOR_(PROXIMITY,"proximity") \
    SENSOR_(LIGHT, "light") \
    SENSOR_(PRESSURE, "pressure") \
    SENSOR_(HUMIDITY, "humidity") \
    SENSOR_(MAGNETIC_FIELD_UNCALIBRATED,"magnetic-field-uncalibrated") \

static const struct {
    const char*  name;
    int          id; } _sensorIds[MAX_NUM_SENSORS] =
{
#define SENSOR_(x,y)  { y, ID_##x },
    SENSORS_LIST
#undef  SENSOR_
};

static const char*
_sensorIdToName( int  id )
{
    int  nn;
    for (nn = 0; nn < MAX_NUM_SENSORS; nn++)
        if (id == _sensorIds[nn].id)
            return _sensorIds[nn].name;
    return "<UNKNOWN>";
}

static int
_sensorIdFromName( const char*  name )
{
    int  nn;

    if (name == NULL)
        return -1;

    for (nn = 0; nn < MAX_NUM_SENSORS; nn++)
        if (!strcmp(name, _sensorIds[nn].name))
            return _sensorIds[nn].id;

    return -1;
}

/* return the current time in nanoseconds */
static int64_t now_ns(void) {
    struct timespec  ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000000000 + ts.tv_nsec;
}

/** SENSORS POLL DEVICE
 **
 ** This one is used to read sensor data from the hardware.
 ** We implement this by simply reading the data from the
 ** emulator through the QEMUD channel.
 **/

typedef struct SensorDevice {
    struct sensors_poll_device_1  device;
    sensors_event_t               sensors[MAX_NUM_SENSORS];
    uint32_t                      pendingSensors;
    int64_t                       timeStart;
    int64_t                       timeOffset;
    uint32_t                      active_sensors;
    int                           fd;
    pthread_mutex_t               lock;
} SensorDevice;

/* Grab the file descriptor to the emulator's sensors service pipe.
 * This function returns a file descriptor on success, or -errno on
 * failure, and assumes the SensorDevice instance's lock is held.
 *
 * This is needed because set_delay(), poll() and activate() can be called
 * from different threads, and poll() is blocking.
 *
 * Note that the emulator's sensors service creates a new client for each
 * connection through qemud_channel_open(), where each client has its own
 * delay and set of activated sensors. This precludes calling
 * qemud_channel_open() on each request, because a typical emulated system
 * will do something like:
 *
 * 1) On a first thread, de-activate() all sensors first, then call poll(),
 *    which results in the thread blocking.
 *
 * 2) On a second thread, slightly later, call set_delay() then activate()
 *    to enable the acceleration sensor.
 *
 * The system expects this to unblock the first thread which will receive
 * new sensor events after the activate() call in 2).
 *
 * This cannot work if both threads don't use the same connection.
 *
 * TODO(digit): This protocol is brittle, implement another control channel
 *              for set_delay()/activate()/batch() when supporting HAL 1.3
 */
static int sensor_device_get_fd_locked(SensorDevice* dev) {
    /* Create connection to service on first call */
    if (dev->fd < 0) {
        dev->fd = qemud_channel_open(SENSORS_SERVICE_NAME);
        if (dev->fd < 0) {
            int ret = -errno;
            E("%s: Could not open connection to service: %s", __FUNCTION__,
                strerror(-ret));
            return ret;
        }
    }
    return dev->fd;
}

/* Send a command to the sensors virtual device. |dev| is a device instance and
 * |cmd| is a zero-terminated command string. Return 0 on success, or -errno
 * on failure. */
static int sensor_device_send_command_locked(SensorDevice* dev,
                                             const char* cmd) {
    int fd = sensor_device_get_fd_locked(dev);
    if (fd < 0) {
        return fd;
    }

    int ret = 0;
    if (qemud_channel_send(fd, cmd, strlen(cmd)) < 0) {
        ret = -errno;
        E("%s(fd=%d): ERROR: %s", __FUNCTION__, fd, strerror(errno));
    }
    return ret;
}

/* Pick up one pending sensor event. On success, this returns the sensor
 * id, and sets |*event| accordingly. On failure, i.e. if there are no
 * pending events, return -EINVAL.
 *
 * Note: The device's lock must be acquired.
 */
static int sensor_device_pick_pending_event_locked(SensorDevice* d,
                                                   sensors_event_t*  event)
{
    uint32_t mask = SUPPORTED_SENSORS & d->pendingSensors;
    if (mask) {
        uint32_t i = 31 - __builtin_clz(mask);
        d->pendingSensors &= ~(1U << i);
        // Copy the structure
        *event = d->sensors[i];

        if (d->sensors[i].type == SENSOR_TYPE_META_DATA) {
            // sensor_device_poll_event_locked() will leave
            // the meta-data in place until we have it.
            // Set |type| to something other than META_DATA
            // so sensor_device_poll_event_locked() can
            // continue.
            d->sensors[i].type = SENSOR_TYPE_META_DATA + 1;
        } else {
            event->sensor = i;
            event->version = sizeof(*event);
        }

        D("%s: %d [%f, %f, %f]", __FUNCTION__,
                i,
                event->data[0],
                event->data[1],
                event->data[2]);
        return i;
    }
    E("No sensor to return!!! pendingSensors=0x%08x", d->pendingSensors);
    // we may end-up in a busy loop, slow things down, just in case.
    usleep(1000);
    return -EINVAL;
}

/* Block until new sensor events are reported by the emulator, or if a
 * 'wake' command is received through the service. On succes, return 0
 * and updates the |pendingEvents| and |sensors| fields of |dev|.
 * On failure, return -errno.
 *
 * Note: The device lock must be acquired when calling this function, and
 *       will still be held on return. However, the function releases the
 *       lock temporarily during the blocking wait.
 */
static int sensor_device_poll_event_locked(SensorDevice* dev)
{
    D("%s: dev=%p", __FUNCTION__, dev);

    int fd = sensor_device_get_fd_locked(dev);
    if (fd < 0) {
        E("%s: Could not get pipe channel: %s", __FUNCTION__, strerror(-fd));
        return fd;
    }

    // Accumulate pending events into |events| and |new_sensors| mask
    // until a 'sync' or 'wake' command is received. This also simplifies the
    // code a bit.
    uint32_t new_sensors = 0U;
    sensors_event_t* events = dev->sensors;

    int64_t event_time = -1;
    int ret = 0;

    for (;;) {
        /* Release the lock since we're going to block on recv() */
        pthread_mutex_unlock(&dev->lock);

        /* read the next event */
        char buff[256];
        int len = qemud_channel_recv(fd, buff, sizeof(buff) - 1U);
        /* re-acquire the lock to modify the device state. */
        pthread_mutex_lock(&dev->lock);

        if (len < 0) {
            ret = -errno;
            E("%s(fd=%d): Could not receive event data len=%d, errno=%d: %s",
              __FUNCTION__, fd, len, errno, strerror(errno));
            break;
        }
        buff[len] = 0;
        D("%s(fd=%d): received [%s]", __FUNCTION__, fd, buff);


        /* "wake" is sent from the emulator to exit this loop. */
        /* TODO(digit): Is it still needed? */
        if (!strcmp((const char*)buff, "wake")) {
            ret = 0x7FFFFFFF;
            break;
        }

        float params[3];

        // If the existing entry for this sensor is META_DATA,
        // do not overwrite it. We can resume saving sensor
        // values after that meta data has been received.

        /* "acceleration:<x>:<y>:<z>" corresponds to an acceleration event */
        if (sscanf(buff, "acceleration:%g:%g:%g", params+0, params+1, params+2)
                == 3) {
            new_sensors |= SENSORS_ACCELERATION;
            if (events[ID_ACCELERATION].type == SENSOR_TYPE_META_DATA) continue;
            events[ID_ACCELERATION].acceleration.x = params[0];
            events[ID_ACCELERATION].acceleration.y = params[1];
            events[ID_ACCELERATION].acceleration.z = params[2];
            events[ID_ACCELERATION].type = SENSOR_TYPE_ACCELEROMETER;
            continue;
        }

        /* "gyroscope:<x>:<y>:<z>" corresponds to a gyroscope event */
        if (sscanf(buff, "gyroscope:%g:%g:%g", params+0, params+1, params+2)
                == 3) {
            new_sensors |= SENSORS_GYROSCOPE;
            if (events[ID_GYROSCOPE].type == SENSOR_TYPE_META_DATA) continue;
            events[ID_GYROSCOPE].gyro.x = params[0];
            events[ID_GYROSCOPE].gyro.y = params[1];
            events[ID_GYROSCOPE].gyro.z = params[2];
            events[ID_GYROSCOPE].type = SENSOR_TYPE_GYROSCOPE;
            continue;
        }

        /* "orientation:<azimuth>:<pitch>:<roll>" is sent when orientation
         * changes */
        if (sscanf(buff, "orientation:%g:%g:%g", params+0, params+1, params+2)
                == 3) {
            new_sensors |= SENSORS_ORIENTATION;
            if (events[ID_ORIENTATION].type == SENSOR_TYPE_META_DATA) continue;
            events[ID_ORIENTATION].orientation.azimuth = params[0];
            events[ID_ORIENTATION].orientation.pitch   = params[1];
            events[ID_ORIENTATION].orientation.roll    = params[2];
            events[ID_ORIENTATION].orientation.status  =
                    SENSOR_STATUS_ACCURACY_HIGH;
            events[ID_ORIENTATION].type = SENSOR_TYPE_ORIENTATION;
            continue;
        }

        /* "magnetic:<x>:<y>:<z>" is sent for the params of the magnetic
         * field */
        if (sscanf(buff, "magnetic:%g:%g:%g", params+0, params+1, params+2)
                == 3) {
            new_sensors |= SENSORS_MAGNETIC_FIELD;
            if (events[ID_MAGNETIC_FIELD].type == SENSOR_TYPE_META_DATA) continue;
            events[ID_MAGNETIC_FIELD].magnetic.x = params[0];
            events[ID_MAGNETIC_FIELD].magnetic.y = params[1];
            events[ID_MAGNETIC_FIELD].magnetic.z = params[2];
            events[ID_MAGNETIC_FIELD].magnetic.status =
                    SENSOR_STATUS_ACCURACY_HIGH;
            events[ID_MAGNETIC_FIELD].type = SENSOR_TYPE_MAGNETIC_FIELD;
            continue;
        }

        if (sscanf(buff, "magnetic-uncalibrated:%g:%g:%g", params+0, params+1, params+2)
                == 3) {
            new_sensors |= SENSORS_MAGNETIC_FIELD_UNCALIBRATED;
            if (events[ID_MAGNETIC_FIELD_UNCALIBRATED].type == SENSOR_TYPE_META_DATA) continue;
            events[ID_MAGNETIC_FIELD_UNCALIBRATED].magnetic.x = params[0];
            events[ID_MAGNETIC_FIELD_UNCALIBRATED].magnetic.y = params[1];
            events[ID_MAGNETIC_FIELD_UNCALIBRATED].magnetic.z = params[2];
            events[ID_MAGNETIC_FIELD_UNCALIBRATED].magnetic.status =
                    SENSOR_STATUS_ACCURACY_HIGH;
            events[ID_MAGNETIC_FIELD_UNCALIBRATED].type = SENSOR_TYPE_MAGNETIC_FIELD_UNCALIBRATED;
            continue;
        }

        /* "temperature:<celsius>" */
        if (sscanf(buff, "temperature:%g", params+0) == 1) {
            new_sensors |= SENSORS_TEMPERATURE;
            if (events[ID_TEMPERATURE].type == SENSOR_TYPE_META_DATA) continue;
            events[ID_TEMPERATURE].temperature = params[0];
            events[ID_TEMPERATURE].type = SENSOR_TYPE_AMBIENT_TEMPERATURE;
            continue;
        }

        /* "proximity:<value>" */
        if (sscanf(buff, "proximity:%g", params+0) == 1) {
            new_sensors |= SENSORS_PROXIMITY;
            if (events[ID_PROXIMITY].type == SENSOR_TYPE_META_DATA) continue;
            events[ID_PROXIMITY].distance = params[0];
            events[ID_PROXIMITY].type = SENSOR_TYPE_PROXIMITY;
            continue;
        }
        /* "light:<lux>" */
        if (sscanf(buff, "light:%g", params+0) == 1) {
            new_sensors |= SENSORS_LIGHT;
            if (events[ID_LIGHT].type == SENSOR_TYPE_META_DATA) continue;
            events[ID_LIGHT].light = params[0];
            events[ID_LIGHT].type = SENSOR_TYPE_LIGHT;
            continue;
        }

        /* "pressure:<hpa>" */
        if (sscanf(buff, "pressure:%g", params+0) == 1) {
            new_sensors |= SENSORS_PRESSURE;
            if (events[ID_PRESSURE].type == SENSOR_TYPE_META_DATA) continue;
            events[ID_PRESSURE].pressure = params[0];
            events[ID_PRESSURE].type = SENSOR_TYPE_PRESSURE;
            continue;
        }

        /* "humidity:<percent>" */
        if (sscanf(buff, "humidity:%g", params+0) == 1) {
            new_sensors |= SENSORS_HUMIDITY;
            if (events[ID_HUMIDITY].type == SENSOR_TYPE_META_DATA) continue;
            events[ID_HUMIDITY].relative_humidity = params[0];
            events[ID_HUMIDITY].type = SENSOR_TYPE_RELATIVE_HUMIDITY;
            continue;
        }

        /* "sync:<time>" is sent after a series of sensor events.
         * where 'time' is expressed in micro-seconds and corresponds
         * to the VM time when the real poll occured.
         */
        if (sscanf(buff, "sync:%lld", &event_time) == 1) {
            if (new_sensors) {
                goto out;
            }
            D("huh ? sync without any sensor data ?");
            continue;
        }
        D("huh ? unsupported command");
    }
out:
    if (new_sensors) {
        /* update the time of each new sensor event. */
        dev->pendingSensors |= new_sensors;
        int64_t t = (event_time < 0) ? 0 : event_time * 1000LL;

        /* Use the time at the first "sync:" as the base for later
         * time values.
         * CTS tests require sensors to return an event timestamp (sync) that is
         * strictly before the time of the event arrival. We don't actually have
         * a time syncronization protocol here, and the only data point is the
         * "sync:" timestamp - which is an emulator's timestamp of a clock that
         * is synced with the guest clock, and it only the timestamp after all
         * events were sent.
         * To make it work, let's compare the calculated timestamp with current
         * time and take the lower value - we don't believe in events from the
         * future anyway.
         */
        const int64_t now = now_ns();

        if (dev->timeStart == 0) {
            dev->timeStart  = now;
            dev->timeOffset = dev->timeStart - t;
        }
        t += dev->timeOffset;
        if (t > now) {
            t = now;
        }

        while (new_sensors) {
            uint32_t i = 31 - __builtin_clz(new_sensors);
            new_sensors &= ~(1U << i);
            dev->sensors[i].timestamp = t;
        }
    }
    return ret;
}

/** SENSORS POLL DEVICE FUNCTIONS **/

static int sensor_device_close(struct hw_device_t* dev0)
{
    SensorDevice* dev = (void*)dev0;
    // Assume that there are no other threads blocked on poll()
    if (dev->fd >= 0) {
        close(dev->fd);
        dev->fd = -1;
    }
    pthread_mutex_destroy(&dev->lock);
    free(dev);
    return 0;
}

/* Return an array of sensor data. This function blocks until there is sensor
 * related events to report. On success, it will write the events into the
 * |data| array, which contains |count| items. The function returns the number
 * of events written into the array, which shall never be greater than |count|.
 * On error, return -errno code.
 *
 * Note that according to the sensor HAL [1], it shall never return 0!
 *
 * [1] http://source.android.com/devices/sensors/hal-interface.html
 */
static int sensor_device_poll(struct sensors_poll_device_t *dev0,
                              sensors_event_t* data, int count)
{
    SensorDevice* dev = (void*)dev0;
    D("%s: dev=%p data=%p count=%d ", __FUNCTION__, dev, data, count);

    if (count <= 0) {
        return -EINVAL;
    }

    int result = 0;
    pthread_mutex_lock(&dev->lock);
    if (!dev->pendingSensors) {
        /* Block until there are pending events. Note that this releases
         * the lock during the blocking call, then re-acquires it before
         * returning. */
        int ret = sensor_device_poll_event_locked(dev);
        if (ret < 0) {
            result = ret;
            goto out;
        }
        if (!dev->pendingSensors) {
            /* 'wake' event received before any sensor data. */
            result = -EIO;
            goto out;
        }
    }
    /* Now read as many pending events as needed. */
    int i;
    for (i = 0; i < count; i++)  {
        if (!dev->pendingSensors) {
            break;
        }
        int ret = sensor_device_pick_pending_event_locked(dev, data);
        if (ret < 0) {
            if (!result) {
                result = ret;
            }
            break;
        }
        data++;
        result++;
    }
out:
    pthread_mutex_unlock(&dev->lock);
    D("%s: result=%d", __FUNCTION__, result);
    return result;
}

static int sensor_device_activate(struct sensors_poll_device_t *dev0,
                                  int handle,
                                  int enabled)
{
    SensorDevice* dev = (void*)dev0;

    D("%s: handle=%s (%d) enabled=%d", __FUNCTION__,
        _sensorIdToName(handle), handle, enabled);

    /* Sanity check */
    if (!ID_CHECK(handle)) {
        E("%s: bad handle ID", __FUNCTION__);
        return -EINVAL;
    }

    /* Exit early if sensor is already enabled/disabled. */
    uint32_t mask = (1U << handle);
    uint32_t sensors = enabled ? mask : 0;

    pthread_mutex_lock(&dev->lock);

    uint32_t active = dev->active_sensors;
    uint32_t new_sensors = (active & ~mask) | (sensors & mask);
    uint32_t changed = active ^ new_sensors;

    int ret = 0;
    if (changed) {
        /* Send command to the emulator. */
        char command[64];
        snprintf(command,
                 sizeof command,
                 "set:%s:%d",
                 _sensorIdToName(handle),
                 enabled != 0);

        ret = sensor_device_send_command_locked(dev, command);
        if (ret < 0) {
            E("%s: when sending command errno=%d: %s", __FUNCTION__, -ret,
              strerror(-ret));
        } else {
            dev->active_sensors = new_sensors;
        }
    }
    pthread_mutex_unlock(&dev->lock);
    return ret;
}

static int sensor_device_default_flush(
        struct sensors_poll_device_1* dev0,
        int handle) {

    SensorDevice* dev = (void*)dev0;

    D("%s: handle=%s (%d)", __FUNCTION__,
        _sensorIdToName(handle), handle);

    /* Sanity check */
    if (!ID_CHECK(handle)) {
        E("%s: bad handle ID", __FUNCTION__);
        return -EINVAL;
    }

    pthread_mutex_lock(&dev->lock);
    dev->sensors[handle].version = META_DATA_VERSION;
    dev->sensors[handle].type = SENSOR_TYPE_META_DATA;
    dev->sensors[handle].sensor = 0;
    dev->sensors[handle].timestamp = 0;
    dev->sensors[handle].meta_data.sensor = handle;
    dev->sensors[handle].meta_data.what = META_DATA_FLUSH_COMPLETE;
    dev->pendingSensors |= (1U << handle);
    pthread_mutex_unlock(&dev->lock);

    return 0;
}

static int sensor_device_set_delay(struct sensors_poll_device_t *dev0,
                                   int handle __unused,
                                   int64_t ns)
{
    SensorDevice* dev = (void*)dev0;

    int ms = (int)(ns / 1000000);
    D("%s: dev=%p delay-ms=%d", __FUNCTION__, dev, ms);

    char command[64];
    snprintf(command, sizeof command, "set-delay:%d", ms);

    pthread_mutex_lock(&dev->lock);
    int ret = sensor_device_send_command_locked(dev, command);
    pthread_mutex_unlock(&dev->lock);
    if (ret < 0) {
        E("%s: Could not send command: %s", __FUNCTION__, strerror(-ret));
    }
    return ret;
}

static int sensor_device_default_batch(
     struct sensors_poll_device_1* dev,
     int sensor_handle,
     int flags,
     int64_t sampling_period_ns,
     int64_t max_report_latency_ns) {
    return sensor_device_set_delay(dev, sensor_handle, sampling_period_ns);
}

/** MODULE REGISTRATION SUPPORT
 **
 ** This is required so that hardware/libhardware/hardware.c
 ** will dlopen() this library appropriately.
 **/

/*
 * the following is the list of all supported sensors.
 * this table is used to build sSensorList declared below
 * according to which hardware sensors are reported as
 * available from the emulator (see get_sensors_list below)
 *
 * note: numerical values for maxRange/resolution/power for
 *       all sensors but light, pressure and humidity were
 *       taken from the reference AK8976A implementation
 */
static const struct sensor_t sSensorListInit[] = {
        { .name       = "Goldfish 3-axis Accelerometer",
          .vendor     = "The Android Open Source Project",
          .version    = 1,
          .handle     = ID_ACCELERATION,
          .type       = SENSOR_TYPE_ACCELEROMETER,
          .maxRange   = 2.8f,
          .resolution = 1.0f/4032.0f,
          .power      = 3.0f,
          .minDelay   = 10000,
          .maxDelay   = 60 * 1000 * 1000,
          .fifoReservedEventCount = 0,
          .fifoMaxEventCount =   0,
          .stringType =         0,
          .requiredPermission = 0,
          .flags = SENSOR_FLAG_CONTINUOUS_MODE,
          .reserved   = {}
        },

        { .name       = "Goldfish 3-axis Gyroscope",
          .vendor     = "The Android Open Source Project",
          .version    = 1,
          .handle     = ID_GYROSCOPE,
          .type       = SENSOR_TYPE_GYROSCOPE,
          .maxRange   = 11.1111111,
          .resolution = 1.0f/1000.0f,
          .power      = 3.0f,
          .minDelay   = 10000,
          .maxDelay   = 60 * 1000 * 1000,
          .reserved   = {}
        },

        { .name       = "Goldfish 3-axis Magnetic field sensor",
          .vendor     = "The Android Open Source Project",
          .version    = 1,
          .handle     = ID_MAGNETIC_FIELD,
          .type       = SENSOR_TYPE_MAGNETIC_FIELD,
          .maxRange   = 2000.0f,
          .resolution = 1.0f,
          .power      = 6.7f,
          .minDelay   = 10000,
          .maxDelay   = 60 * 1000 * 1000,
          .fifoReservedEventCount = 0,
          .fifoMaxEventCount =   0,
          .stringType =         0,
          .requiredPermission = 0,
          .flags = SENSOR_FLAG_CONTINUOUS_MODE,
          .reserved   = {}
        },

        { .name       = "Goldfish Orientation sensor",
          .vendor     = "The Android Open Source Project",
          .version    = 1,
          .handle     = ID_ORIENTATION,
          .type       = SENSOR_TYPE_ORIENTATION,
          .maxRange   = 360.0f,
          .resolution = 1.0f,
          .power      = 9.7f,
          .minDelay   = 10000,
          .maxDelay   = 60 * 1000 * 1000,
          .fifoReservedEventCount = 0,
          .fifoMaxEventCount =   0,
          .stringType =         0,
          .requiredPermission = 0,
          .flags = SENSOR_FLAG_CONTINUOUS_MODE,
          .reserved   = {}
        },

        { .name       = "Goldfish Temperature sensor",
          .vendor     = "The Android Open Source Project",
          .version    = 1,
          .handle     = ID_TEMPERATURE,
          .type       = SENSOR_TYPE_AMBIENT_TEMPERATURE,
          .maxRange   = 80.0f,
          .resolution = 1.0f,
          .power      = 0.0f,
          .minDelay   = 10000,
          .maxDelay   = 60 * 1000 * 1000,
          .fifoReservedEventCount = 0,
          .fifoMaxEventCount =   0,
          .stringType =         0,
          .requiredPermission = 0,
          .flags = SENSOR_FLAG_CONTINUOUS_MODE,
          .reserved   = {}
        },

        { .name       = "Goldfish Proximity sensor",
          .vendor     = "The Android Open Source Project",
          .version    = 1,
          .handle     = ID_PROXIMITY,
          .type       = SENSOR_TYPE_PROXIMITY,
          .maxRange   = 1.0f,
          .resolution = 1.0f,
          .power      = 20.0f,
          .minDelay   = 10000,
          .maxDelay   = 60 * 1000 * 1000,
          .fifoReservedEventCount = 0,
          .fifoMaxEventCount =   0,
          .stringType =         0,
          .requiredPermission = 0,
          .flags = SENSOR_FLAG_WAKE_UP | SENSOR_FLAG_ON_CHANGE_MODE,
          .reserved   = {}
        },

        { .name       = "Goldfish Light sensor",
          .vendor     = "The Android Open Source Project",
          .version    = 1,
          .handle     = ID_LIGHT,
          .type       = SENSOR_TYPE_LIGHT,
          .maxRange   = 40000.0f,
          .resolution = 1.0f,
          .power      = 20.0f,
          .minDelay   = 10000,
          .maxDelay   = 60 * 1000 * 1000,
          .fifoReservedEventCount = 0,
          .fifoMaxEventCount =   0,
          .stringType =         0,
          .requiredPermission = 0,
          .flags = SENSOR_FLAG_ON_CHANGE_MODE,
          .reserved   = {}
        },

        { .name       = "Goldfish Pressure sensor",
          .vendor     = "The Android Open Source Project",
          .version    = 1,
          .handle     = ID_PRESSURE,
          .type       = SENSOR_TYPE_PRESSURE,
          .maxRange   = 800.0f,
          .resolution = 1.0f,
          .power      = 20.0f,
          .minDelay   = 10000,
          .maxDelay   = 60 * 1000 * 1000,
          .fifoReservedEventCount = 0,
          .fifoMaxEventCount =   0,
          .stringType =         0,
          .requiredPermission = 0,
          .flags = SENSOR_FLAG_CONTINUOUS_MODE,
          .reserved   = {}
        },

        { .name       = "Goldfish Humidity sensor",
          .vendor     = "The Android Open Source Project",
          .version    = 1,
          .handle     = ID_HUMIDITY,
          .type       = SENSOR_TYPE_RELATIVE_HUMIDITY,
          .maxRange   = 100.0f,
          .resolution = 1.0f,
          .power      = 20.0f,
          .minDelay   = 10000,
          .maxDelay   = 60 * 1000 * 1000,
          .fifoReservedEventCount = 0,
          .fifoMaxEventCount =   0,
          .stringType =         0,
          .requiredPermission = 0,
          .flags = SENSOR_FLAG_CONTINUOUS_MODE,
          .reserved   = {}
        },

        { .name       = "Goldfish 3-axis Magnetic field sensor (uncalibrated)",
          .vendor     = "The Android Open Source Project",
          .version    = 1,
          .handle     = ID_MAGNETIC_FIELD_UNCALIBRATED,
          .type       = SENSOR_TYPE_MAGNETIC_FIELD_UNCALIBRATED,
          .maxRange   = 2000.0f,
          .resolution = 1.0f,
          .power      = 6.7f,
          .minDelay   = 10000,
          .maxDelay   = 60 * 1000 * 1000,
          .reserved   = {}
        },
};

static struct sensor_t  sSensorList[MAX_NUM_SENSORS];

static int sensors__get_sensors_list(struct sensors_module_t* module __unused,
        struct sensor_t const** list)
{
    int  fd = qemud_channel_open(SENSORS_SERVICE_NAME);
    char buffer[12];
    int  mask, nn, count;
    int  ret = 0;

    if (fd < 0) {
        E("%s: no qemud connection", __FUNCTION__);
        goto out;
    }
    ret = qemud_channel_send(fd, "list-sensors", -1);
    if (ret < 0) {
        E("%s: could not query sensor list: %s", __FUNCTION__,
          strerror(errno));
        goto out;
    }
    ret = qemud_channel_recv(fd, buffer, sizeof buffer-1);
    if (ret < 0) {
        E("%s: could not receive sensor list: %s", __FUNCTION__,
          strerror(errno));
        goto out;
    }
    buffer[ret] = 0;

    /* the result is a integer used as a mask for available sensors */
    mask  = atoi(buffer);
    count = 0;
    for (nn = 0; nn < MAX_NUM_SENSORS; nn++) {
        if (((1 << nn) & mask) == 0)
            continue;
        sSensorList[count++] = sSensorListInit[nn];
    }
    D("%s: returned %d sensors (mask=%d)", __FUNCTION__, count, mask);
    *list = sSensorList;

    ret = count;
out:
    if (fd >= 0) {
        close(fd);
    }
    return ret;
}


static int
open_sensors(const struct hw_module_t* module,
             const char*               name,
             struct hw_device_t*      *device)
{
    int  status = -EINVAL;

    D("%s: name=%s", __FUNCTION__, name);

    if (!strcmp(name, SENSORS_HARDWARE_POLL)) {
        SensorDevice *dev = malloc(sizeof(*dev));

        memset(dev, 0, sizeof(*dev));

        dev->device.common.tag     = HARDWARE_DEVICE_TAG;
        dev->device.common.version = SENSORS_DEVICE_API_VERSION_1_3;
        dev->device.common.module  = (struct hw_module_t*) module;
        dev->device.common.close   = sensor_device_close;
        dev->device.poll           = sensor_device_poll;
        dev->device.activate       = sensor_device_activate;
        dev->device.setDelay       = sensor_device_set_delay;

        // (dev->sensors[i].type == SENSOR_TYPE_META_DATA) is
        // sticky. Don't start off with that setting.
        for (int idx = 0; idx < MAX_NUM_SENSORS; idx++) {
            dev->sensors[idx].type = SENSOR_TYPE_META_DATA + 1;
        }

        // Version 1.3-specific functions
        dev->device.batch       = sensor_device_default_batch;
        dev->device.flush       = sensor_device_default_flush;

        dev->fd = -1;
        pthread_mutex_init(&dev->lock, NULL);

        *device = &dev->device.common;
        status  = 0;
    }
    return status;
}


static struct hw_module_methods_t sensors_module_methods = {
    .open = open_sensors
};

struct sensors_module_t HAL_MODULE_INFO_SYM = {
    .common = {
        .tag = HARDWARE_MODULE_TAG,
        .version_major = 1,
        .version_minor = 3,
        .id = SENSORS_HARDWARE_MODULE_ID,
        .name = "Goldfish SENSORS Module",
        .author = "The Android Open Source Project",
        .methods = &sensors_module_methods,
    },
    .get_sensors_list = sensors__get_sensors_list
};
