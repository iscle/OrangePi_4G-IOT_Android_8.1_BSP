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
#define LOG_TAG "BroadcastRadio"
//#define LOG_NDEBUG 0

#include <log/log.h>

#include <hardware/radio.h>

#include "BroadcastRadio.h"
#include "Tuner.h"
#include "Utils.h"

namespace android {
namespace hardware {
namespace broadcastradio {
namespace V1_0 {
namespace implementation {

BroadcastRadio::BroadcastRadio(Class classId)
    : mStatus(Result::NOT_INITIALIZED), mClassId(classId), mHwDevice(NULL)
{
}

BroadcastRadio::~BroadcastRadio()
{
    if (mHwDevice != NULL) {
        radio_hw_device_close(mHwDevice);
    }
}

void BroadcastRadio::onFirstRef()
{
    const hw_module_t *mod;
    int rc;
    ALOGI("%s mClassId %d", __FUNCTION__, mClassId);

    mHwDevice = NULL;
    const char *classString = Utils::getClassString(mClassId);
    if (classString == NULL) {
        ALOGE("invalid class ID %d", mClassId);
        mStatus = Result::INVALID_ARGUMENTS;
        return;
    }

    ALOGI("%s RADIO_HARDWARE_MODULE_ID %s %s",
          __FUNCTION__, RADIO_HARDWARE_MODULE_ID, classString);

    rc = hw_get_module_by_class(RADIO_HARDWARE_MODULE_ID, classString, &mod);
    if (rc != 0) {
        ALOGE("couldn't load radio module %s.%s (%s)",
              RADIO_HARDWARE_MODULE_ID, classString, strerror(-rc));
        mStatus = Result::INVALID_ARGUMENTS;
        return;
    }
    rc = radio_hw_device_open(mod, &mHwDevice);
    if (rc != 0) {
        ALOGE("couldn't open radio hw device in %s.%s (%s)",
              RADIO_HARDWARE_MODULE_ID, "primary", strerror(-rc));
        mHwDevice = NULL;
        return;
    }
    if (mHwDevice->common.version != RADIO_DEVICE_API_VERSION_CURRENT) {
        ALOGE("wrong radio hw device version %04x", mHwDevice->common.version);
        radio_hw_device_close(mHwDevice);
        mHwDevice = NULL;
    } else {
        mStatus = Result::OK;
    }
}

int BroadcastRadio::closeHalTuner(const struct radio_tuner *halTuner)
{
    ALOGV("%s", __FUNCTION__);
    if (mHwDevice == NULL) {
        return -ENODEV;
    }
    if (halTuner == 0) {
        return -EINVAL;
    }
    return mHwDevice->close_tuner(mHwDevice, halTuner);
}


// Methods from ::android::hardware::broadcastradio::V1_0::IBroadcastRadio follow.
Return<void> BroadcastRadio::getProperties(getProperties_cb _hidl_cb)
{
    int rc;
    radio_hal_properties_t halProperties;
    Properties properties;

    if (mHwDevice == NULL) {
        rc = -ENODEV;
        goto exit;
    }
    rc = mHwDevice->get_properties(mHwDevice, &halProperties);
    if (rc == 0) {
        Utils::convertPropertiesFromHal(&properties, &halProperties);
    }

exit:
    _hidl_cb(Utils::convertHalResult(rc), properties);
    return Void();
}

Return<void> BroadcastRadio::openTuner(const BandConfig& config, bool audio,
                                       const sp<ITunerCallback>& callback, openTuner_cb _hidl_cb)
{
    sp<Tuner> tunerImpl = new Tuner(callback, this);

    radio_hal_band_config_t halConfig;
    const struct radio_tuner *halTuner;
    Utils::convertBandConfigToHal(&halConfig, &config);
    int rc = mHwDevice->open_tuner(mHwDevice, &halConfig, audio,
                                   Tuner::callback, tunerImpl.get(),
                                   &halTuner);
    if (rc == 0) {
        tunerImpl->setHalTuner(halTuner);
    }

    _hidl_cb(Utils::convertHalResult(rc), tunerImpl);
    return Void();
}


} // namespace implementation
}  // namespace V1_0
}  // namespace broadcastradio
}  // namespace hardware
}  // namespace android
