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

#ifndef android_hardware_automotive_vehicle_V2_0_impl_FakeHalEventGenerator_H_
#define android_hardware_automotive_vehicle_V2_0_impl_FakeHalEventGenerator_H_

#include <chrono>

#include <android/hardware/automotive/vehicle/2.0/types.h>

#include <vhal_v2_0/RecurrentTimer.h>

namespace android {
namespace hardware {
namespace automotive {
namespace vehicle {
namespace V2_0 {

namespace impl {

class FakeValueGenerator {
private:
    // In every timer tick we may want to generate new value based on initial value for debug
    // purpose. It's better to have sequential values to see if events gets delivered in order
    // to the client.

    struct GeneratorCfg {
        float initialValue;  //
        float currentValue;  //  Should be in range (initialValue +/- dispersion).
        float dispersion;    //  Defines minimum and maximum value based on initial value.
        float increment;     //  Value that we will be added to currentValue with each timer tick.
    };

public:
    using OnHalEvent = std::function<void(int32_t propId, float value)>;

    FakeValueGenerator(const OnHalEvent& onHalEvent) :
        mOnHalEvent(onHalEvent),
        mRecurrentTimer(std::bind(&FakeValueGenerator::onTimer, this,
                                  std::placeholders::_1))
    {}

    ~FakeValueGenerator() = default;


    void startGeneratingHalEvents(std::chrono::nanoseconds interval, int propId, float initialValue,
                                  float dispersion, float increment) {
        MuxGuard g(mLock);

        removeLocked(propId);

        mGenCfg.insert({propId, GeneratorCfg {
            .initialValue = initialValue,
            .currentValue = initialValue,
            .dispersion = dispersion,
            .increment = increment,
        }});

        mRecurrentTimer.registerRecurrentEvent(interval, propId);
    }

    void stopGeneratingHalEvents(int propId) {
        MuxGuard g(mLock);
        if (propId == 0) {
            // Remove all.
            for (auto&& it : mGenCfg) {
                removeLocked(it.first);
            }
        } else {
            removeLocked(propId);
        }
    }

private:
    void removeLocked(int propId) {
        if (mGenCfg.erase(propId)) {
            mRecurrentTimer.unregisterRecurrentEvent(propId);
        }
    }

    void onTimer(const std::vector<int32_t>& properties) {
        MuxGuard g(mLock);

        for (int32_t propId : properties) {
            auto& cfg = mGenCfg[propId];
            cfg.currentValue += cfg.increment;
            if (cfg.currentValue > cfg.initialValue + cfg.dispersion) {
                cfg.currentValue = cfg.initialValue - cfg.dispersion;
            }
            mOnHalEvent(propId, cfg.currentValue);
        }
    }

private:
    using MuxGuard = std::lock_guard<std::mutex>;

    mutable std::mutex mLock;
    OnHalEvent mOnHalEvent;
    RecurrentTimer mRecurrentTimer;
    std::unordered_map<int32_t, GeneratorCfg> mGenCfg;
};


}  // impl

}  // namespace V2_0
}  // namespace vehicle
}  // namespace automotive
}  // namespace hardware
}  // namespace android



#endif //android_hardware_automotive_vehicle_V2_0_impl_FakeHalEventGenerator_H_
