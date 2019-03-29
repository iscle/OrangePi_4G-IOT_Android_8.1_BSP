/*
 * Copyright (C) 2008-2015 The Android Open Source Project
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

#define LOG_TAG "CrosECSensor"

#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/input.h>
#include <math.h>
#include <poll.h>
#include <pthread.h>
#include <stdlib.h>

#include <utils/Atomic.h>
#include <utils/Log.h>

#include <hardware/sensors.h>

#include "cros_ec_sensors.h"
#include "sensors.h"

/*****************************************************************************/

/*****************************************************************************/

#define UNSET_FIELD -1
/*
 * TODO(gwendal): We should guess the fifo size, but
 * we need to issue an ioctl instead of just reading IIO sysfs.
 * EC will trigger an interrupt at 2/3 of its FIFO.
 */
#define CROS_EC_FIFO_SIZE (2048 * 2 / 3)

/* Name of iio devices, as reported by cros_ec_dev.c */
const char *cros_ec_sensor_names[] = {
    [CROS_EC_ACCEL] = "cros-ec-accel",
    [CROS_EC_GYRO] = "cros-ec-gyro",
    [CROS_EC_MAG] = "cros-ec-mag",
    [CROS_EC_PROX] = "cros-ec-prox-unused", // Prevent a match.
    [CROS_EC_LIGHT] = "cros-ec-light",
    [CROS_EC_ACTIVITY] = "cros-ec-activity",
    [CROS_EC_RING] = "cros-ec-ring",
};

/* Name of iio data names, as reported by IIO */
const char *cros_ec_iio_axis_names[] = {
    [CROS_EC_ACCEL] = "in_accel",
    [CROS_EC_GYRO] = "in_anglvel",
};

/*
 * cros_ec_activity is shared between sensors interface and
 * activity interface.
 * Activity has a separate module is not implemented yet
 */

/* Activities that belongs to the sensor interface */
const char *cros_ec_gesture_name[] = {
    [CROS_EC_SIGMO] = "in_activity_still_change_falling_en",
};

const int cros_ec_gesture_id[] = {
    [CROS_EC_SIGMO] = MOTIONSENSE_ACTIVITY_SIG_MOTION,
};


/*
 * Template for sensor_t structure return to motionservice.
 *
 * Some parameters (handle, range, resolution) are retreived
 * from IIO.
 */
static const struct sensor_t sSensorListTemplate[] = {
    [CROS_EC_ACCEL] = {
        .name =              "CrosEC Accelerometer",
        .vendor =            "Google",
        .version =           1,
        .handle =            UNSET_FIELD,
        .type =              SENSOR_TYPE_ACCELEROMETER,
        .maxRange =          UNSET_FIELD,
        .resolution =        UNSET_FIELD,
        .power =             0.18f,    /* Based on BMI160 */
        .minDelay =          5000,
        .fifoReservedEventCount =0,
        .fifoMaxEventCount = CROS_EC_FIFO_SIZE,
        .stringType =        SENSOR_STRING_TYPE_ACCELEROMETER,
        .requiredPermission =0,
        /*
         * BMI160 has a problem at 6.25Hz or less, FIFO not readable.
         * Works at 12.5Hz, so set maxDelay at 80ms
         */
        .maxDelay =          80000,
        .flags =             SENSOR_FLAG_CONTINUOUS_MODE,
        .reserved =          { 0 }
    },
    [CROS_EC_GYRO] = {
        .name =              "CrosEC Gyroscope",
        .vendor =            "Google",
        .version =           1,
        .handle =            UNSET_FIELD,
        .type =              SENSOR_TYPE_GYROSCOPE,
        .maxRange =          UNSET_FIELD,
        .resolution =        UNSET_FIELD,
        .power =             0.85f,
        .minDelay =          5000,
        .fifoReservedEventCount =0,
        .fifoMaxEventCount = CROS_EC_FIFO_SIZE,
        .stringType =        SENSOR_STRING_TYPE_GYROSCOPE,
        .requiredPermission =0,
        .maxDelay =          80000,
        .flags =             SENSOR_FLAG_CONTINUOUS_MODE,
        .reserved =          { 0 }
    },
    [CROS_EC_MAG] = {
        .name =              "CrosEC Compass",
        .vendor =            "Google",
        .version =           1,
        .handle =            UNSET_FIELD,
        .type =              SENSOR_TYPE_MAGNETIC_FIELD,
        .maxRange =          UNSET_FIELD,
        .resolution =        UNSET_FIELD,
        .power =             5.0f,  /* Based on BMM150 */
        /*
         * BMI150 uses repetition to reduce output noise.
         * Set ODR at no more than 25Hz.
         */
        .minDelay =          40000,
        .fifoReservedEventCount =0,
        .fifoMaxEventCount = CROS_EC_FIFO_SIZE,
        .stringType =        SENSOR_STRING_TYPE_MAGNETIC_FIELD,
        .requiredPermission =0,
        .maxDelay =          200000,
        .flags =             SENSOR_FLAG_CONTINUOUS_MODE,
        .reserved =          { 0 }
    },
    [CROS_EC_PROX] = {
        .name =              "CrosEC Proximity",
        .vendor =            "Google",
        .version =           1,
        .handle =            UNSET_FIELD,
        .type =              SENSOR_TYPE_PROXIMITY,
        .maxRange =          UNSET_FIELD,
        .resolution =        UNSET_FIELD,
        .power =             0.12f,  /* Based on Si1141 */
        .minDelay =          20000,
        .fifoReservedEventCount =0,
        .fifoMaxEventCount = CROS_EC_FIFO_SIZE,
        .stringType =        SENSOR_STRING_TYPE_PROXIMITY,
        .requiredPermission =0,
        /* Forced mode, can be long =10s */
        .maxDelay =          10000000,
        /* WAKE UP required by API */
        .flags =             SENSOR_FLAG_ON_CHANGE_MODE | SENSOR_FLAG_WAKE_UP,
        .reserved =          { 0 }
    },
    [CROS_EC_LIGHT] = {
        .name =              "CrosEC Light",
        .vendor =            "Google",
        .version =           1,
        .handle =            UNSET_FIELD,
        .type =              SENSOR_TYPE_LIGHT,
        .maxRange =          UNSET_FIELD,
        .resolution =        UNSET_FIELD,
        .power =             0.12f,  /* Based on Si1141 */
        .minDelay =          20000,
        .fifoReservedEventCount =0,
        .fifoMaxEventCount = CROS_EC_FIFO_SIZE,
        .stringType =        SENSOR_STRING_TYPE_LIGHT,
        .requiredPermission =0,
        /* Forced mode, can be long =9s */
        .maxDelay =          10000000,
        .flags =             SENSOR_FLAG_ON_CHANGE_MODE,
        .reserved =          { 0 }
    },
};

static const struct sensor_t sGestureListTemplate[] = {
    [CROS_EC_SIGMO] = {
        .name =              "CrosEC Significant Motion",
        .vendor =            "Google",
        .version =           1,
        .handle =            UNSET_FIELD,
        .type =              SENSOR_TYPE_SIGNIFICANT_MOTION,
        .maxRange =          1.0f,
        .resolution =        1.0f,
        .power =             0.18f,    /* Based on BMI160 */
        .minDelay =          -1,
        .fifoReservedEventCount =0,
        .fifoMaxEventCount = 0,
        .stringType =        SENSOR_STRING_TYPE_SIGNIFICANT_MOTION,
        .requiredPermission =0,
        .maxDelay =          0,
        .flags =             SENSOR_FLAG_ONE_SHOT_MODE | SENSOR_FLAG_WAKE_UP,
        .reserved =          { 0 }
    },
};

/* We only support the sensors in the lid */
static const char *cros_ec_location = "lid";

static int Stotal_sensor_count_ = 0;
static int Stotal_max_sensor_handle_ = 0;
static int Stotal_max_gesture_handle_ = 0;

static struct sensor_t *Ssensor_list_ = NULL;

struct cros_ec_sensor_info *Ssensor_info_ = NULL;
struct cros_ec_gesture_info *Sgesture_info_ = NULL;

static int cros_ec_open_sensors(const struct hw_module_t *module,
                                const char *id,
                                struct hw_device_t **device);
/*
 * cros_ec_get_sensors_list: entry point that returns the list
 * of sensors.
 *
 * At first invocation, build the list from Ssensor_info_,
 * then keep returning the same list.
 *
 * The expected design is a hardcoded list of sensors.
 * Therefore we don't have access to
 */
static int cros_ec_get_sensors_list(struct sensors_module_t*,
        struct sensor_t const** list)
{
    ALOGD("counting sensors: count %d: sensor_list_ %p\n",
            Stotal_sensor_count_, Ssensor_list_);

    if (Stotal_sensor_count_ != 0) {
        *list = Ssensor_list_;
        return Stotal_sensor_count_;
    }

    for (int i = 0 ; i < Stotal_max_sensor_handle_ ; i++) {
        if (Ssensor_info_[i].device_name == NULL)
            continue;

        Stotal_sensor_count_++;
        Ssensor_list_ = (sensor_t*)realloc(Ssensor_list_,
                Stotal_sensor_count_ * sizeof(sensor_t));
        if (Ssensor_list_ == NULL) {
            ALOGI("Unable to allocate Ssensor_list_\n");
            return 0;
        }
        sensor_t *sensor_data;
        sensor_data = &Ssensor_info_[i].sensor_data;
        memcpy(&Ssensor_list_[Stotal_sensor_count_ - 1], sensor_data,
               sizeof(sensor_t));
    }

    for (int i = 0 ; i < Stotal_max_gesture_handle_ ; i++) {
        if (Sgesture_info_[i].device_name == NULL)
            continue;

        Stotal_sensor_count_++;
        Ssensor_list_ = (sensor_t*)realloc(Ssensor_list_,
                Stotal_sensor_count_ * sizeof(sensor_t));
        if (Ssensor_list_ == NULL) {
            ALOGI("Unable to allocate Ssensor_list_\n");
            return 0;
        }
        sensor_t *sensor_data;
        sensor_data = &Sgesture_info_[i].sensor_data;
        memcpy(&Ssensor_list_[Stotal_sensor_count_ - 1], sensor_data,
               sizeof(sensor_t));
    }
    *list = Ssensor_list_;
    return Stotal_sensor_count_;
}

/*
 * cros_ec_get_gesture_names: Build list of gestures from IIO
 *
 * Looking into the cros_ec_activity sensors, looks for events
 * the sensorserivces are managing.
 *
 * We assume only one cros_ec activity sensor.
 */
static int cros_ec_get_gesture_names(const char *sensor_name)
{
    char path_device[IIO_MAX_DEVICE_NAME_LENGTH];
    strcpy(path_device, IIO_DIR);
    strcat(path_device, sensor_name);
    strcat(path_device, "/events");
    DIR *events_dir;
    ALOGD("looking at %s:", path_device);
    events_dir = opendir(path_device);
    if (events_dir == NULL)
        return -ENODEV;
    const struct dirent *ent_event;
    while (ent_event = readdir(events_dir), ent_event != NULL) {
        int gesture;
        for (gesture = 0; gesture < CROS_EC_MAX_GESTURE; gesture++) {
            if (!strcmp(ent_event->d_name, cros_ec_gesture_name[gesture]))
                break;
        }
        if (gesture == CROS_EC_MAX_GESTURE)
            continue;
        int gesture_id = cros_ec_gesture_id[gesture];
        if (Stotal_max_gesture_handle_ <= gesture_id) {
            Sgesture_info_ = (cros_ec_gesture_info*)realloc(Sgesture_info_,
                    (gesture_id + 1) * sizeof(cros_ec_gesture_info));
            if (Sgesture_info_ == NULL)
                return -ENOMEM;
            memset(&Sgesture_info_[Stotal_max_gesture_handle_], 0,
                    (gesture_id + 1 - Stotal_max_gesture_handle_) *
                    sizeof(cros_ec_gesture_info));
            Stotal_max_gesture_handle_ = gesture_id + 1;
        }
        cros_ec_gesture_info *gesture_info = &Sgesture_info_[gesture_id];
        gesture_info->device_name = strdup(sensor_name);
        gesture_info->enable_entry = cros_ec_gesture_name[gesture];

        sensor_t *sensor_data;
        sensor_data = &gesture_info->sensor_data;
        memcpy(sensor_data, &sGestureListTemplate[gesture], sizeof(sensor_t));
        sensor_data->handle = CROS_EC_MAX_PHYSICAL_SENSOR + gesture_id;

        ALOGD("new gesture '%s' on device '%s' : handle: %d\n",
              gesture_info->enable_entry, gesture_info->device_name, gesture_id);
    }
    closedir(events_dir);
    return 0;
}

/*
 * cros_ec_calibrate_3d_sensor: calibrate Accel or Gyro.
 *
 * In factory, calibration data is in VPD.
 * It is available from user space by reading /sys/firmware/vpd/ro/<Key>.
 * Key names are similar to iio: <type>_<axis>_calibbias,
 * when type is in_accel or in_anglvel and axis is x,y, or z.
 */
static int cros_ec_calibrate_3d_sensor(int sensor_type, const char *device_name)
{
    const char vpd_path[] = "/sys/firmware/vpd/ro";
    char calib_value[MAX_AXIS][20];
    char calib_key[MAX_AXIS][IIO_MAX_NAME_LENGTH];
    bool calib_data_valid = true;

    for (int i = X ; i < MAX_AXIS; i++) {
        snprintf(calib_key[i], sizeof(calib_key[i]), "%s_%c_calibbias",
                cros_ec_iio_axis_names[sensor_type], 'x' + i);
    }
    for (int i = X ; i < MAX_AXIS; i++) {
        if (cros_ec_sysfs_get_attr(vpd_path, calib_key[i], calib_value[i])) {
            ALOGI("Calibration key %s missing.\n", calib_key[i]);
            calib_data_valid = false;
            break;
        }
    }
    if (calib_data_valid && sensor_type == CROS_EC_ACCEL) {
        for (int i = X ; i < MAX_AXIS; i++) {
            /*
             * Workaround for invalid calibration values obveserved on several
             * devices (b/26927000). If the value seems bogus, ignore the whole
             * calibration.
             * If one calibration axis is greater than 2 m/s^2, ignore.
             */
            int value = atoi(calib_value[i]);
            if (abs(value) > (2 * 1024 * 100 / 981)) {
                ALOGE("Calibration data invalid on axis %d: %d\n", i, value);
                calib_data_valid = false;
                break;
            }
        }
    }

    for (int i = X ; i < MAX_AXIS; i++) {
        const char *value = (calib_data_valid ? calib_value[i] : "0");
        if (cros_ec_sysfs_set_input_attr(device_name, calib_key[i],
                    value, strlen(value))) {
            ALOGE("Writing bias %s to %s for device %s failed.\n",
                    calib_key[i], value, device_name);
        }
    }
    return 0;
}

/*
 * cros_ec_get_sensors_names: Build list of sensors from IIO
 *
 * Scanning /sys/iio/devices, finds all the sensors managed by the EC.
 *
 * Fill Ssensor_info_ global structure.
 * ring_device_name: name of iio ring buffer. We
 *   will open /dev/<ring_device_name> later
 * ring_trigger_name: Name of hardware trigger for setting the
 *   ring buffer producer side.
 */
static int cros_ec_get_sensors_names(char **ring_device_name,
                                     char **ring_trigger_name)
{
    /*
     * If Ssensor_info_ is valid, we don't want to open
     * the same device twice.
     */
    if (Stotal_max_sensor_handle_ != 0)
        return -EINVAL;

    *ring_device_name = NULL;
    *ring_trigger_name = NULL;

    DIR *iio_dir;
    iio_dir = opendir(IIO_DIR);
    if (iio_dir == NULL) {
        return -ENODEV;
    }
    const struct dirent *ent_device;
    while (ent_device = readdir(iio_dir), ent_device != NULL) {
        /* Find the iio directory with the sensor definition */
        if (ent_device->d_type != DT_LNK)
            continue;
        char path_device[IIO_MAX_DEVICE_NAME_LENGTH];
        strcpy(path_device, IIO_DIR);
        strcat(path_device, ent_device->d_name);

        char dev_name[IIO_MAX_NAME_LENGTH + 1];
        if (cros_ec_sysfs_get_attr(path_device, "name", dev_name))
            continue;

        for (int i = CROS_EC_ACCEL; i < CROS_EC_RING; ++i) {
            /* We assume only one sensor hub per device.
             * Otherwise we need to look at the symlink and connect the 2:
             * iio:device0 ->
             *  ../../../devices/7000c400.i2c/i2c-1/1-001e/cros-ec-dev.0/
             *  cros-ec-accel.0/iio:device0
             * and
             * ...
             * iio:device1 ->
             *  ../../../devices/7000c400.i2c/i2c-1/1-001e/cros-ec-dev.0/
             *  cros-ec-ring.0/iio:device1
             */
            if (!strcmp(cros_ec_sensor_names[i], dev_name)) {
                /*
                 * First check if the device belongs to the lid.
                 * (base is keyboard)
                 */
                char loc[IIO_MAX_NAME_LENGTH + 1];
                if (cros_ec_sysfs_get_attr(path_device, "location", loc))
                    continue;
                if (strcmp(cros_ec_location, loc))
                    continue;

                char dev_id[40];
                if (cros_ec_sysfs_get_attr(path_device, "id", dev_id))
                    continue;
                int sensor_id = atoi(dev_id);
                if (Stotal_max_sensor_handle_ <= sensor_id) {
                    Ssensor_info_ = (cros_ec_sensor_info*)realloc(Ssensor_info_,
                            (sensor_id + 1) * sizeof(cros_ec_sensor_info));
                    if (Ssensor_info_ == NULL) {
                        closedir(iio_dir);
                        return -ENOMEM;
                    }
                    memset(&Ssensor_info_[Stotal_max_sensor_handle_], 0,
                            (sensor_id + 1 - Stotal_max_sensor_handle_) *
                            sizeof(cros_ec_sensor_info));
                    Stotal_max_sensor_handle_ = sensor_id + 1;
                }

                struct cros_ec_sensor_info *sensor_info = &Ssensor_info_[sensor_id];
                sensor_info->type = static_cast<enum cros_ec_sensor_device>(i);

                if (i == CROS_EC_ACTIVITY) {
                    cros_ec_get_gesture_names(ent_device->d_name);
                } else {
                    sensor_info->device_name = strdup(ent_device->d_name);
                    char dev_scale[40];
                    if (cros_ec_sysfs_get_attr(path_device, "scale", dev_scale)) {
                        ALOGE("Unable to read scale\n");
                        continue;
                    }
                    double scale = atof(dev_scale);

                    sensor_t *sensor_data = &sensor_info->sensor_data;
                    memcpy(sensor_data, &sSensorListTemplate[i], sizeof(sensor_t));
                    sensor_data->handle = sensor_id;

                    if (sensor_data->type == SENSOR_TYPE_MAGNETIC_FIELD)
                        /* iio units are in Gauss, not micro Telsa */
                        scale *= 100;
                    if (sensor_data->type == SENSOR_TYPE_PROXIMITY) {
                        /*
                         * Proximity does not detect anything beyond 3m.
                         */
                        sensor_data->resolution = 1;
                        sensor_data->maxRange = 300;
                    } else {
                        sensor_data->resolution = scale;
                        sensor_data->maxRange = scale * (1 << 15);
                    }

                    if (sensor_data->type == SENSOR_TYPE_ACCELEROMETER ||
                        sensor_data->type == SENSOR_TYPE_GYROSCOPE) {
                        /* There is an assumption by the calibration code that there is
                         * only one type of sensors per device.
                         * If it needs to change, we will add "location" sysfs key
                         * to find the proper calibration data.
                         */
                        cros_ec_calibrate_3d_sensor(i, sensor_info->device_name);
                    }

                    ALOGD("new dev '%s' handle: %d\n",
                            sensor_info->device_name, sensor_id);
                }
                break;
            }
        }

        if (!strcmp(cros_ec_sensor_names[CROS_EC_RING], dev_name)) {
            *ring_device_name = strdup(ent_device->d_name);
        }

        char trigger_name[80];
        strcpy(trigger_name, cros_ec_sensor_names[CROS_EC_RING]);
        strcat(trigger_name, "-trigger");
        if (!strncmp(trigger_name, dev_name, strlen(trigger_name))) {
            *ring_trigger_name = strdup(dev_name);
            ALOGD("new trigger '%s' \n", *ring_trigger_name);
            continue;
        }
    }
    closedir(iio_dir);

    if (*ring_device_name == NULL || *ring_trigger_name == NULL)
        return -ENODEV;

    return Stotal_max_sensor_handle_ ? 0 : -ENODEV;
}

static struct hw_module_methods_t cros_ec_sensors_methods = {
    .open = cros_ec_open_sensors,
};

struct sensors_module_t HAL_MODULE_INFO_SYM = {
    .common ={
        .tag =HARDWARE_MODULE_TAG,
        .version_major =1,
        .version_minor =0,
        .id =SENSORS_HARDWARE_MODULE_ID,
        .name ="CrosEC sensor hub module",
        .author ="Google",
        .methods =&cros_ec_sensors_methods,
        .dso =NULL,
        .reserved ={ 0 },
    },
    .get_sensors_list =cros_ec_get_sensors_list,
    .set_operation_mode =NULL,
};

/*****************************************************************************/
cros_ec_sensors_poll_context_t::cros_ec_sensors_poll_context_t(
        const struct hw_module_t *module,
        const char *ring_device_name,
        const char *ring_trigger_name)
{
    memset(&device, 0, sizeof(sensors_poll_device_1_t));

    device.common.tag      = HARDWARE_DEVICE_TAG;
    device.common.version  = SENSORS_DEVICE_API_VERSION_1_3;
    device.common.module   = const_cast<hw_module_t *>(module);
    device.common.close    = wrapper_close;
    device.activate        = wrapper_activate;
    device.setDelay        = wrapper_setDelay;
    device.poll            = wrapper_poll;

    // Batch processing
    device.batch           = wrapper_batch;
    device.flush           = wrapper_flush;

    /*
     * One more time, assume only one sensor hub in the system.
     * Find the iio:deviceX with name "cros_ec_ring"
     * Open /dev/iio:deviceX, enable buffer.
     */
    mSensor = new CrosECSensor(
        Ssensor_info_, Stotal_max_sensor_handle_,
        Sgesture_info_, Stotal_max_gesture_handle_,
        ring_device_name, ring_trigger_name);

    mPollFds[crosEcRingFd].fd = mSensor->getFd();
    mPollFds[crosEcRingFd].events = POLLIN;
    mPollFds[crosEcRingFd].revents = 0;

    int wakeFds[2];
    int result = pipe(wakeFds);
    ALOGE_IF(result < 0, "error creating wake pipe (%s)", strerror(errno));
    fcntl(wakeFds[0], F_SETFL, O_NONBLOCK);
    fcntl(wakeFds[1], F_SETFL, O_NONBLOCK);
    mWritePipeFd = wakeFds[1];

    mPollFds[crosEcWakeFd].fd = wakeFds[0];
    mPollFds[crosEcWakeFd].events = POLLIN;
    mPollFds[crosEcWakeFd].revents = 0;
}

cros_ec_sensors_poll_context_t::~cros_ec_sensors_poll_context_t() {
    delete mSensor;
    close(mPollFds[crosEcWakeFd].fd);
    close(mWritePipeFd);
}

int cros_ec_sensors_poll_context_t::activate(int handle, int enabled) {
    int err = mSensor->activate(handle, enabled);

    if (enabled && !err) {
        const char wakeMessage(WAKE_MESSAGE);
        int result = write(mWritePipeFd, &wakeMessage, 1);
        ALOGE_IF(result<0, "error sending wake message (%s)", strerror(errno));
    }
    return err;
}

int cros_ec_sensors_poll_context_t::setDelay(int /* handle */,
                                             int64_t /* ns */) {
    /* No supported */
    return 0;
}

int cros_ec_sensors_poll_context_t::pollEvents(sensors_event_t* data, int count)
{
    int nbEvents = 0;
    int n = 0;
    do {
        // see if we have some leftover from the last poll()
        if (mPollFds[crosEcRingFd].revents & POLLIN) {
            int nb = mSensor->readEvents(data, count);
            if (nb < count) {
                // no more data for this sensor
                mPollFds[crosEcRingFd].revents = 0;
            }
            count -= nb;
            nbEvents += nb;
            data += nb;
        }

        if (count) {
            // we still have some room, so try to see if we can get
            // some events immediately or just wait if we don't have
            // anything to return
            do {
                TEMP_FAILURE_RETRY(n = poll(mPollFds, numFds,
                                            nbEvents ? 0 : -1));
            } while (n < 0 && errno == EINTR);
            if (n < 0) {
                ALOGE("poll() failed (%s)", strerror(errno));
                return -errno;
            }
            if (mPollFds[crosEcWakeFd].revents & POLLIN) {
                char msg(WAKE_MESSAGE);
                int result = read(mPollFds[crosEcWakeFd].fd, &msg, 1);
                ALOGE_IF(result < 0,
                         "error reading from wake pipe (%s)", strerror(errno));
                ALOGE_IF(msg != WAKE_MESSAGE,
                         "unknown message on wake queue (0x%02x)", int(msg));
                mPollFds[crosEcWakeFd].revents = 0;
            }
        }
        // if we have events and space, go read them
    } while (n && count);
    return nbEvents;
}

int cros_ec_sensors_poll_context_t::batch(int handle, int /* flags */,
        int64_t sampling_period_ns,
        int64_t max_report_latency_ns)
{
    return mSensor->batch(handle, sampling_period_ns,
                          max_report_latency_ns);
}

int cros_ec_sensors_poll_context_t::flush(int handle)
{
    return mSensor->flush(handle);
}


/*****************************************************************************/

int cros_ec_sensors_poll_context_t::wrapper_close(struct hw_device_t *dev)
{
    cros_ec_sensors_poll_context_t *ctx = reinterpret_cast<cros_ec_sensors_poll_context_t *>(dev);
    if (ctx) {
        delete ctx;
    }
    if (Stotal_max_sensor_handle_ != 0) {
        free(Ssensor_info_);
        Stotal_max_sensor_handle_ = 0;
        free(Sgesture_info_);
    }
    return 0;
}

int cros_ec_sensors_poll_context_t::wrapper_activate(struct sensors_poll_device_t *dev,
        int handle, int enabled)
{
    cros_ec_sensors_poll_context_t *ctx = reinterpret_cast<cros_ec_sensors_poll_context_t *>(dev);
    return ctx->activate(handle, enabled);
}

int cros_ec_sensors_poll_context_t::wrapper_setDelay(struct sensors_poll_device_t *dev,
        int handle, int64_t ns)
{
    cros_ec_sensors_poll_context_t *ctx = reinterpret_cast<cros_ec_sensors_poll_context_t *>(dev);
    return ctx->setDelay(handle, ns);
}

int cros_ec_sensors_poll_context_t::wrapper_poll(struct sensors_poll_device_t *dev,
        sensors_event_t* data, int count)
{
    cros_ec_sensors_poll_context_t *ctx = reinterpret_cast<cros_ec_sensors_poll_context_t *>(dev);
    return ctx->pollEvents(data, count);
}

int cros_ec_sensors_poll_context_t::wrapper_batch(struct sensors_poll_device_1 *dev,
        int handle, int flags, int64_t period_ns, int64_t timeout)
{
    cros_ec_sensors_poll_context_t *ctx = reinterpret_cast<cros_ec_sensors_poll_context_t *>(dev);
    return ctx->batch(handle, flags, period_ns, timeout);
}

int cros_ec_sensors_poll_context_t::wrapper_flush(struct sensors_poll_device_1 *dev,
        int handle)
{
    cros_ec_sensors_poll_context_t *ctx = reinterpret_cast<cros_ec_sensors_poll_context_t *>(dev);
    return ctx->flush(handle);
}

/*****************************************************************************/

/*
 * cros_ec_open_sensors: open entry point.
 *
 * Call by sensor service via helper function:  sensors_open()
 *
 * Create a device the service will use for event polling.
 * Assume one open/one close.
 *
 * Later, sensorservice will use device with an handle to access
 * a particular sensor.
 */
static int cros_ec_open_sensors(
        const struct hw_module_t* module, const char*,
        struct hw_device_t** device)
{
    char *ring_device_name = NULL, *ring_trigger_name = NULL;
    int err;
    err = cros_ec_get_sensors_names(&ring_device_name, &ring_trigger_name);
    if (err == 0) {
        cros_ec_sensors_poll_context_t *dev = new cros_ec_sensors_poll_context_t(
                module, ring_device_name, ring_trigger_name);

        *device = &dev->device.common;
    }
    free(ring_device_name);
    free(ring_trigger_name);
    return err;
}

