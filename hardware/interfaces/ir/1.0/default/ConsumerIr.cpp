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

#define LOG_TAG "ConsumerIrService"

#include <log/log.h>

#include <hardware/hardware.h>
#include <hardware/consumerir.h>

#include "ConsumerIr.h"

namespace android {
namespace hardware {
namespace ir {
namespace V1_0 {
namespace implementation {

ConsumerIr::ConsumerIr(consumerir_device_t *device) {
    mDevice = device;
}

// Methods from ::android::hardware::consumerir::V1_0::IConsumerIr follow.
Return<bool> ConsumerIr::transmit(int32_t carrierFreq, const hidl_vec<int32_t>& pattern) {
    return mDevice->transmit(mDevice, carrierFreq, pattern.data(), pattern.size()) == 0;
}

Return<void> ConsumerIr::getCarrierFreqs(getCarrierFreqs_cb _hidl_cb) {
    int32_t len = mDevice->get_num_carrier_freqs(mDevice);
    if (len < 0) {
        _hidl_cb(false, {});
        return Void();
    }

    consumerir_freq_range_t *rangeAr = new consumerir_freq_range_t[len];
    bool success = (mDevice->get_carrier_freqs(mDevice, len, rangeAr) >= 0);
    if (!success) {
        _hidl_cb(false, {});
        return Void();
    }

    hidl_vec<ConsumerIrFreqRange> rangeVec;
    rangeVec.resize(len);
    for (int32_t i = 0; i < len; i++) {
        rangeVec[i].min = static_cast<uint32_t>(rangeAr[i].min);
        rangeVec[i].max = static_cast<uint32_t>(rangeAr[i].max);
    }
    _hidl_cb(true, rangeVec);
    return Void();
}


IConsumerIr* HIDL_FETCH_IConsumerIr(const char * /*name*/) {
    consumerir_device_t *dev;
    const hw_module_t *hw_module = NULL;

    int ret = hw_get_module(CONSUMERIR_HARDWARE_MODULE_ID, &hw_module);
    if (ret != 0) {
        ALOGE("hw_get_module %s failed: %d", CONSUMERIR_HARDWARE_MODULE_ID, ret);
        return nullptr;
    }
    ret = hw_module->methods->open(hw_module, CONSUMERIR_TRANSMITTER, (hw_device_t **) &dev);
    if (ret < 0) {
        ALOGE("Can't open consumer IR transmitter, error: %d", ret);
        return nullptr;
    }
    return new ConsumerIr(dev);
}

}  // namespace implementation
}  // namespace V1_0
}  // namespace ir
}  // namespace hardware
}  // namespace android
