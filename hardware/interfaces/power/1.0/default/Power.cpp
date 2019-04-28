/*
 * Copyright (C) 2016 The Android Open Source Project
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

#define LOG_TAG "android.hardware.power@1.0-impl"

#include <log/log.h>

#include <hardware/hardware.h>
#include <hardware/power.h>

#include "Power.h"

namespace android {
namespace hardware {
namespace power {
namespace V1_0 {
namespace implementation {

Power::Power(power_module_t *module) : mModule(module) {
    if (mModule)
        mModule->init(mModule);
}

Power::~Power() {
    delete(mModule);
}

// Methods from ::android::hardware::power::V1_0::IPower follow.
Return<void> Power::setInteractive(bool interactive)  {
    if (mModule->setInteractive)
        mModule->setInteractive(mModule, interactive ? 1 : 0);
    return Void();
}

Return<void> Power::powerHint(PowerHint hint, int32_t data)  {
    int32_t param = data;
    if (mModule->powerHint) {
        if (data)
            mModule->powerHint(mModule, static_cast<power_hint_t>(hint), &param);
        else
            mModule->powerHint(mModule, static_cast<power_hint_t>(hint), NULL);
    }
    return Void();
}

Return<void> Power::setFeature(Feature feature, bool activate)  {
    if (mModule->setFeature)
        mModule->setFeature(mModule, static_cast<feature_t>(feature),
                activate ? 1 : 0);
    return Void();
}

Return<void> Power::getPlatformLowPowerStats(getPlatformLowPowerStats_cb _hidl_cb)  {
    hidl_vec<PowerStatePlatformSleepState> states;
    ssize_t number_platform_modes;
    size_t *voters = nullptr;
    power_state_platform_sleep_state_t *legacy_states = nullptr;
    int ret;

    if (mModule->get_number_of_platform_modes == nullptr ||
            mModule->get_voter_list == nullptr ||
            mModule->get_platform_low_power_stats == nullptr)
    {
        _hidl_cb(states, Status::SUCCESS);
        return Void();
    }

    number_platform_modes = mModule->get_number_of_platform_modes(mModule);
    if (number_platform_modes)
    {
       if ((ssize_t) (SIZE_MAX / sizeof(size_t)) <= number_platform_modes)  // overflow
           goto done;
       voters = new (std::nothrow) size_t [number_platform_modes];
       if (voters == nullptr)
           goto done;

       ret = mModule->get_voter_list(mModule, voters);
       if (ret != 0)
           goto done;

       if ((ssize_t) (SIZE_MAX / sizeof(power_state_platform_sleep_state_t))
           <= number_platform_modes)  // overflow
           goto done;
       legacy_states = new (std::nothrow)
           power_state_platform_sleep_state_t [number_platform_modes];
       if (legacy_states == nullptr)
           goto done;

       for (int i = 0; i < number_platform_modes; i++)
       {
          legacy_states[i].voters = nullptr;
          legacy_states[i].voters = new power_state_voter_t [voters[i]];
          if (legacy_states[i].voters == nullptr)
              goto done;
       }

       ret = mModule->get_platform_low_power_stats(mModule, legacy_states);
       if (ret != 0)
           goto done;

       states.resize(number_platform_modes);
       for (int i = 0; i < number_platform_modes; i++)
       {
          power_state_platform_sleep_state_t& legacy_state = legacy_states[i];
          PowerStatePlatformSleepState& state = states[i];
          state.name = legacy_state.name;
          state.residencyInMsecSinceBoot = legacy_state.residency_in_msec_since_boot;
          state.totalTransitions = legacy_state.total_transitions;
          state.supportedOnlyInSuspend = legacy_state.supported_only_in_suspend;
          state.voters.resize(voters[i]);
          for(size_t j = 0; j < voters[i]; j++)
          {
              state.voters[j].name = legacy_state.voters[j].name;
              state.voters[j].totalTimeInMsecVotedForSinceBoot = legacy_state.voters[j].total_time_in_msec_voted_for_since_boot;
              state.voters[j].totalNumberOfTimesVotedSinceBoot = legacy_state.voters[j].total_number_of_times_voted_since_boot;
          }
       }
    }
done:
    if (legacy_states)
    {
        for (int i = 0; i < number_platform_modes; i++)
        {
            if(legacy_states[i].voters)
                delete(legacy_states[i].voters);
        }
    }
    delete[] legacy_states;
    delete[] voters;
    _hidl_cb(states, Status::SUCCESS);
    return Void();
}

IPower* HIDL_FETCH_IPower(const char* /* name */) {
    const hw_module_t* hw_module = nullptr;
    power_module_t* power_module = nullptr;
    int err = hw_get_module(POWER_HARDWARE_MODULE_ID, &hw_module);
    if (err) {
        ALOGE("hw_get_module %s failed: %d", POWER_HARDWARE_MODULE_ID, err);
        return nullptr;
    }

    if (!hw_module->methods || !hw_module->methods->open) {
        power_module = reinterpret_cast<power_module_t*>(
            const_cast<hw_module_t*>(hw_module));
    } else {
        err = hw_module->methods->open(
            hw_module, POWER_HARDWARE_MODULE_ID,
            reinterpret_cast<hw_device_t**>(&power_module));
        if (err) {
            ALOGE("Passthrough failed to load legacy HAL.");
            return nullptr;
        }
    }
    return new Power(power_module);
}

} // namespace implementation
}  // namespace V1_0
}  // namespace power
}  // namespace hardware
}  // namespace android
