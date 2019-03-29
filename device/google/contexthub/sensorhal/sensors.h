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

#ifndef SENSORS_H_

#define SENSORS_H_

#include <hardware/hardware.h>
#include <hardware/sensors.h>
#include <media/stagefright/foundation/ABase.h>
#include <utils/RefBase.h>

#include <memory>
#include <unordered_set>
#include <vector>

using android::sp;

namespace android {
    struct HubConnection;
} // namespace android
using android::HubConnection;

namespace android {
    namespace SensorHalExt {
        class BaseSensorObject;
        class DynamicSensorManager;
        class SensorEventCallback;
    } // namespace BaseSensorObject
} // namespace android

using android::SensorHalExt::BaseSensorObject;
using android::SensorHalExt::DynamicSensorManager;
using android::SensorHalExt::SensorEventCallback;

struct SensorContext {
    struct sensors_poll_device_1 device;

    explicit SensorContext(const struct hw_module_t *module);

    bool getHubAlive();

    size_t getSensorList(sensor_t const **list);

private:

    int close();
    int activate(int handle, int enabled);
    int setDelay(int handle, int64_t delayNs);
    int poll(sensors_event_t *data, int count);

    int batch(int handle, int64_t sampling_period_ns,
              int64_t max_report_latency_ns);

    int flush(int handle);

    int register_direct_channel(
            const struct sensors_direct_mem_t* mem, int channel_handle);

    int config_direct_report(
            int sensor_handle, int channel_handle, const struct sensors_direct_cfg_t * config);

    int inject_sensor_data(const struct sensors_event_t *event);

    void initializeHalExtension();

    // static wrappers
    static int CloseWrapper(struct hw_device_t *dev);

    static int ActivateWrapper(
            struct sensors_poll_device_t *dev, int handle, int enabled);

    static int SetDelayWrapper(
            struct sensors_poll_device_t *dev, int handle, int64_t delayNs);

    static int PollWrapper(
            struct sensors_poll_device_t *dev, sensors_event_t *data, int count);

    static int BatchWrapper(
            struct sensors_poll_device_1 *dev,
            int handle,
            int flags,
            int64_t sampling_period_ns,
            int64_t max_report_latency_ns);

    static int FlushWrapper(struct sensors_poll_device_1 *dev, int handle);

    static int RegisterDirectChannelWrapper(struct sensors_poll_device_1 *dev,
            const struct sensors_direct_mem_t* mem, int channel_handle);
    static int ConfigDirectReportWrapper(struct sensors_poll_device_1 *dev,
            int sensor_handle, int channel_handle, const struct sensors_direct_cfg_t * config);
    static int InjectSensorDataWrapper(struct sensors_poll_device_1 *dev, const sensors_event_t *event);

    class SensorOperation {
    public:
        virtual bool owns(int handle) = 0;
        virtual int activate(int handle, int enabled) = 0;
        virtual int setDelay(int handle, int64_t delayNs) = 0;
        virtual int batch(
                int handle, int64_t sampling_period_ns,
                int64_t max_report_latency_ns) = 0;
        virtual int flush(int handle) = 0;
        virtual ~SensorOperation() {}
    };

    class HubConnectionOperation : public SensorOperation {
    public:
        HubConnectionOperation(sp<HubConnection> hubConnection);
        virtual bool owns(int handle) override;
        virtual int activate(int handle, int enabled) override;
        virtual int setDelay(int handle, int64_t delayNs) override;
        virtual int batch(
                int handle, int64_t sampling_period_ns,
                int64_t max_report_latency_ns) override;
        virtual int flush(int handle) override;
        virtual ~HubConnectionOperation() {}
    private:
        sp<HubConnection> mHubConnection;
        std::unordered_set<int> mHandles;
    };

    std::vector<sensor_t> mSensorList;

    sp<HubConnection> mHubConnection;
    std::vector<std::unique_ptr<SensorOperation> > mOperationHandler;

#ifdef DYNAMIC_SENSOR_EXT_ENABLED
private:
    class DynamicSensorManagerOperation : public SensorOperation {
    public:
        DynamicSensorManagerOperation(DynamicSensorManager* manager);
        virtual bool owns(int handle) override;
        virtual int activate(int handle, int enabled) override;
        virtual int setDelay(int handle, int64_t delayNs) override;
        virtual int batch(
                int handle, int64_t sampling_period_ns,
                int64_t max_report_latency_ns) override;
        virtual int flush(int handle) override;
        virtual ~DynamicSensorManagerOperation() {}
    private:
        std::unique_ptr<DynamicSensorManager> mDynamicSensorManager;
    };

    static constexpr int32_t kDynamicHandleBase = 0x10000;
    static constexpr int32_t kMaxDynamicHandleCount = 0xF0000; // ~1M handles, enough before reboot

    std::unique_ptr<SensorEventCallback> mEventCallback;
#endif //DYNAMIC_SENSOR_EXT_ENABLED

    DISALLOW_EVIL_CONSTRUCTORS(SensorContext);
};

#endif  // SENSORS_H_
