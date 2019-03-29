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

#define LOG_TAG "sensors"
#define LOG_NDEBUG  1
#include <utils/Log.h>

#include "hubconnection.h"
#include "sensorlist.h"
#include "sensors.h"

#include <cutils/ashmem.h>
#include <errno.h>
#include <math.h>
#include <media/stagefright/foundation/ADebug.h>
#include <string.h>
#include <sys/mman.h>
#include <stdlib.h>

#ifdef DYNAMIC_SENSOR_EXT_ENABLED
#include <DynamicSensorManager.h>
#include <SensorEventCallback.h>
#endif

#ifdef LEFTY_SERVICE_ENABLED
#include "lefty_service.h"
#endif

using namespace android;

////////////////////////////////////////////////////////////////////////////////

SensorContext::SensorContext(const struct hw_module_t *module)
        : mSensorList(kSensorList, kSensorList + kSensorCount),
          mHubConnection(HubConnection::getInstance()) {
    memset(&device, 0, sizeof(device));

    device.common.tag = HARDWARE_DEVICE_TAG;
    device.common.version = SENSORS_DEVICE_API_VERSION_1_4;
    device.common.module = const_cast<hw_module_t *>(module);
    device.common.close = CloseWrapper;
    device.activate = ActivateWrapper;
    device.setDelay = SetDelayWrapper;
    device.poll = PollWrapper;
    device.batch = BatchWrapper;
    device.flush = FlushWrapper;
    device.inject_sensor_data = InjectSensorDataWrapper;
    mHubConnection->setRawScale(kScaleAccel, kScaleMag);
    if (mHubConnection->isDirectReportSupported()) {
        device.register_direct_channel = RegisterDirectChannelWrapper;
        device.config_direct_report = ConfigDirectReportWrapper;
    }

    mOperationHandler.emplace_back(new HubConnectionOperation(mHubConnection));

    initializeHalExtension();
}

int SensorContext::close() {
    ALOGV("close");

    delete this;

    return 0;
}

int SensorContext::activate(int handle, int enabled) {
    ALOGV("activate");

    for (auto &h : mOperationHandler) {
        if (h->owns(handle)) {
            return h->activate(handle, enabled);
        }
    }
    return INVALID_OPERATION;
}

int SensorContext::setDelay(int handle, int64_t delayNs) {
    ALOGV("setDelay");

    for (auto &h: mOperationHandler) {
        if (h->owns(handle)) {
            return h->setDelay(handle, delayNs);
        }
    }
    return INVALID_OPERATION;
}

int SensorContext::poll(sensors_event_t *data, int count) {
    ALOGV("poll");

    // Release wakelock if held and no more events in ring buffer
    mHubConnection->releaseWakeLockIfAppropriate();

    return mHubConnection->read(data, count);
}

int SensorContext::batch(
        int handle,
        int64_t sampling_period_ns,
        int64_t max_report_latency_ns) {
    ALOGV("batch");

    for (auto &h : mOperationHandler) {
        if (h->owns(handle)) {
            return h->batch(handle, sampling_period_ns, max_report_latency_ns);
        }
    }
    return INVALID_OPERATION;
}

int SensorContext::flush(int handle) {
    ALOGV("flush");

    for (auto &h : mOperationHandler) {
        if (h->owns(handle)) {
            return h->flush(handle);
        }
    }
    return INVALID_OPERATION;
}

int SensorContext::register_direct_channel(
        const struct sensors_direct_mem_t *mem, int32_t channel_handle) {
    if (mem) {
        //add
        return mHubConnection->addDirectChannel(mem);
    } else {
        //remove
        mHubConnection->removeDirectChannel(channel_handle);
        return NO_ERROR;
    }
}

int SensorContext::config_direct_report(
        int32_t sensor_handle, int32_t channel_handle, const struct sensors_direct_cfg_t * config) {
    int rate_level = config->rate_level;
    return mHubConnection->configDirectReport(sensor_handle, channel_handle, rate_level);
}

// static
int SensorContext::CloseWrapper(struct hw_device_t *dev) {
    return reinterpret_cast<SensorContext *>(dev)->close();
}

// static
int SensorContext::ActivateWrapper(
        struct sensors_poll_device_t *dev, int handle, int enabled) {
    return reinterpret_cast<SensorContext *>(dev)->activate(handle, enabled);
}

// static
int SensorContext::SetDelayWrapper(
        struct sensors_poll_device_t *dev, int handle, int64_t delayNs) {
    return reinterpret_cast<SensorContext *>(dev)->setDelay(handle, delayNs);
}

// static
int SensorContext::PollWrapper(
        struct sensors_poll_device_t *dev, sensors_event_t *data, int count) {
    return reinterpret_cast<SensorContext *>(dev)->poll(data, count);
}

// static
int SensorContext::BatchWrapper(
        struct sensors_poll_device_1 *dev,
        int handle,
        int flags,
        int64_t sampling_period_ns,
        int64_t max_report_latency_ns) {
    (void) flags;
    return reinterpret_cast<SensorContext *>(dev)->batch(
            handle, sampling_period_ns, max_report_latency_ns);
}

// static
int SensorContext::FlushWrapper(struct sensors_poll_device_1 *dev, int handle) {
    return reinterpret_cast<SensorContext *>(dev)->flush(handle);
}

// static
int SensorContext::RegisterDirectChannelWrapper(struct sensors_poll_device_1 *dev,
        const struct sensors_direct_mem_t* mem, int channel_handle) {
    return reinterpret_cast<SensorContext *>(dev)->register_direct_channel(
            mem, channel_handle);
}

// static
int SensorContext::ConfigDirectReportWrapper(struct sensors_poll_device_1 *dev,
        int sensor_handle, int channel_handle, const sensors_direct_cfg_t * config) {
    return reinterpret_cast<SensorContext *>(dev)->config_direct_report(
            sensor_handle, channel_handle, config);
}

int SensorContext::inject_sensor_data(const sensors_event_t *event) {
    ALOGV("inject_sensor_data");

    // only support set operation parameter, which will have handle == 0
    if (event == nullptr || event->type != SENSOR_TYPE_ADDITIONAL_INFO) {
        return -EINVAL;
    }

    if (event->sensor != SENSORS_HANDLE_BASE - 1) {
        return -ENOSYS;
    }

    if (event->additional_info.type == AINFO_BEGIN
            || event->additional_info.type == AINFO_END) {
        return 0;
    }

    mHubConnection->setOperationParameter(event->additional_info);
    return 0;
}

// static
int SensorContext::InjectSensorDataWrapper(struct sensors_poll_device_1 *dev,
        const struct sensors_event_t *event) {
    return reinterpret_cast<SensorContext *>(dev)->inject_sensor_data(event);
}

bool SensorContext::getHubAlive() {
    return (mHubConnection->initCheck() == OK && mHubConnection->getAliveCheck() == OK);
}

size_t SensorContext::getSensorList(sensor_t const **list) {
    ALOGE("sensor p = %p, n = %zu", mSensorList.data(), mSensorList.size());
    *list = mSensorList.data();
    return mSensorList.size();
}

// HubConnectionOperation functions
SensorContext::HubConnectionOperation::HubConnectionOperation(sp<HubConnection> hubConnection)
        : mHubConnection(hubConnection) {
    for (size_t i = 0; i < kSensorCount; i++) {
        mHandles.emplace(kSensorList[i].handle);
    }
}

bool SensorContext::HubConnectionOperation::owns(int handle) {
    return mHandles.find(handle) != mHandles.end();
}

int SensorContext::HubConnectionOperation::activate(int handle, int enabled) {
    mHubConnection->queueActivate(handle, enabled);
    return 0;
}

int SensorContext::HubConnectionOperation::setDelay(int handle, int64_t delayNs) {
    // clamp sample rate based on minDelay and maxDelay defined in kSensorList
    int64_t delayNsClamped = delayNs;
    for (size_t i = 0; i < kSensorCount; i++) {
        sensor_t sensor = kSensorList[i];
        if (sensor.handle != handle) {
            continue;
        }

        if ((sensor.flags & REPORTING_MODE_MASK) == SENSOR_FLAG_CONTINUOUS_MODE) {
            if ((delayNs/1000) < sensor.minDelay) {
                delayNsClamped = sensor.minDelay * 1000;
            } else if ((delayNs/1000) > sensor.maxDelay) {
                delayNsClamped = sensor.maxDelay * 1000;
            }
        }

        break;
    }

    mHubConnection->queueSetDelay(handle, delayNsClamped);
    return 0;
}

int SensorContext::HubConnectionOperation::batch(
        int handle, int64_t sampling_period_ns,
        int64_t max_report_latency_ns) {
    // clamp sample rate based on minDelay and maxDelay defined in kSensorList
    int64_t sampling_period_ns_clamped = sampling_period_ns;
    for (size_t i = 0; i < kSensorCount; i++) {
        sensor_t sensor = kSensorList[i];
        if (sensor.handle != handle) {
            continue;
        }

        if ((sensor.flags & REPORTING_MODE_MASK) == SENSOR_FLAG_CONTINUOUS_MODE) {
            if ((sampling_period_ns/1000) < sensor.minDelay) {
                sampling_period_ns_clamped = sensor.minDelay * 1000;
            } else if ((sampling_period_ns/1000) > sensor.maxDelay) {
                sampling_period_ns_clamped = sensor.maxDelay * 1000;
            }
        }

        break;
    }

    mHubConnection->queueBatch(handle, sampling_period_ns_clamped,
                               max_report_latency_ns);
    return 0;
}

int SensorContext::HubConnectionOperation::flush(int handle) {
    mHubConnection->queueFlush(handle);
    return 0;
}

#ifdef DYNAMIC_SENSOR_EXT_ENABLED
namespace {
// adaptor class
class Callback : public SensorEventCallback {
public:
    Callback(sp<HubConnection> hubConnection) : mHubConnection(hubConnection) {}
    virtual int submitEvent(sp<BaseSensorObject> source, const sensors_event_t &e) override;
private:
    sp<HubConnection> mHubConnection;
};

int Callback::submitEvent(sp<BaseSensorObject> source, const sensors_event_t &e) {
    (void) source; // irrelavent in this context
    return (mHubConnection->write(&e, 1) == 1) ? 0 : -ENOSPC;
}
} // anonymous namespace

SensorContext::DynamicSensorManagerOperation::DynamicSensorManagerOperation(DynamicSensorManager* manager)
        : mDynamicSensorManager(manager) {
}

bool SensorContext::DynamicSensorManagerOperation::owns(int handle) {
    return mDynamicSensorManager->owns(handle);
}

int SensorContext::DynamicSensorManagerOperation::activate(int handle, int enabled) {
    return mDynamicSensorManager->activate(handle, enabled);
}

int SensorContext::DynamicSensorManagerOperation::setDelay(int handle, int64_t delayNs) {
    return mDynamicSensorManager->setDelay(handle, delayNs);
}

int SensorContext::DynamicSensorManagerOperation::batch(int handle, int64_t sampling_period_ns,
        int64_t max_report_latency_ns) {
    return mDynamicSensorManager->batch(handle, sampling_period_ns, max_report_latency_ns);
}

int SensorContext::DynamicSensorManagerOperation::flush(int handle) {
    return mDynamicSensorManager->flush(handle);
}
#endif

void SensorContext::initializeHalExtension() {
#ifdef DYNAMIC_SENSOR_EXT_ENABLED
    // initialize callback and dynamic sensor manager
    mEventCallback.reset(new Callback(mHubConnection));
    DynamicSensorManager* manager = DynamicSensorManager::createInstance(
        kDynamicHandleBase, kMaxDynamicHandleCount, mEventCallback.get());

    // add meta sensor to list
    mSensorList.push_back(manager->getDynamicMetaSensor());

    // register operation
    mOperationHandler.emplace_back(new DynamicSensorManagerOperation(manager));
#endif
}

////////////////////////////////////////////////////////////////////////////////

static bool gHubAlive;
static sensor_t const *sensor_list;
static int n_sensor;

static int open_sensors(
        const struct hw_module_t *module,
        const char *,
        struct hw_device_t **dev) {
    ALOGV("open_sensors");

    SensorContext *ctx = new SensorContext(module);
    n_sensor = ctx->getSensorList(&sensor_list);
    gHubAlive = ctx->getHubAlive();
    *dev = &ctx->device.common;

#ifdef LEFTY_SERVICE_ENABLED
    register_lefty_service();
#endif
    return 0;
}

static struct hw_module_methods_t sensors_module_methods = {
    .open = open_sensors
};

static int get_sensors_list(
        struct sensors_module_t *,
        struct sensor_t const **list) {
    ALOGV("get_sensors_list");
    if (gHubAlive && sensor_list != nullptr) {
        *list = sensor_list;
        return n_sensor;
    } else {
        *list = {};
        return 0;
    }
}

static int set_operation_mode(unsigned int mode) {
    ALOGV("set_operation_mode");

    // This is no-op because there is no sensor in the hal that system can
    // inject events. Only operation parameter injection is implemented, which
    // works in both data injection and normal mode.
    (void) mode;
    return 0;
}

struct sensors_module_t HAL_MODULE_INFO_SYM = {
        .common = {
                .tag = HARDWARE_MODULE_TAG,
                .version_major = 1,
                .version_minor = 0,
                .id = SENSORS_HARDWARE_MODULE_ID,
                .name = "Google Sensor module",
                .author = "Google",
                .methods = &sensors_module_methods,
                .dso  = NULL,
                .reserved = {0},
        },
        .get_sensors_list = get_sensors_list,
        .set_operation_mode = set_operation_mode,
};
