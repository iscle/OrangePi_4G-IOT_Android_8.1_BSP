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

#include <utils/Log.h>
#include "SoundTriggerHalLegacy.h"

namespace android {

/* static */
sp<SoundTriggerHalInterface> SoundTriggerHalInterface::connectModule(const char *moduleName)
{
    return new SoundTriggerHalLegacy(moduleName);
}

SoundTriggerHalLegacy::SoundTriggerHalLegacy(const char *moduleName)
    : mModuleName(moduleName), mHwDevice(NULL)
{
}

void SoundTriggerHalLegacy::onFirstRef()
{
    const hw_module_t *mod;
    int rc;

    if (mModuleName == NULL) {
        mModuleName = "primary";
    }

    rc = hw_get_module_by_class(SOUND_TRIGGER_HARDWARE_MODULE_ID, mModuleName, &mod);
    if (rc != 0) {
        ALOGE("couldn't load sound trigger module %s.%s (%s)",
              SOUND_TRIGGER_HARDWARE_MODULE_ID, mModuleName, strerror(-rc));
        return;
    }
    rc = sound_trigger_hw_device_open(mod, &mHwDevice);
    if (rc != 0) {
        ALOGE("couldn't open sound trigger hw device in %s.%s (%s)",
              SOUND_TRIGGER_HARDWARE_MODULE_ID, mModuleName, strerror(-rc));
        mHwDevice = NULL;
        return;
    }
    if (mHwDevice->common.version < SOUND_TRIGGER_DEVICE_API_VERSION_1_0 ||
            mHwDevice->common.version > SOUND_TRIGGER_DEVICE_API_VERSION_CURRENT) {
        ALOGE("wrong sound trigger hw device version %04x", mHwDevice->common.version);
        return;
    }
}

SoundTriggerHalLegacy::~SoundTriggerHalLegacy()
{
    if (mHwDevice != NULL) {
        sound_trigger_hw_device_close(mHwDevice);
    }
}

int SoundTriggerHalLegacy::getProperties(struct sound_trigger_properties *properties)
{
    if (mHwDevice == NULL) {
        return -ENODEV;
    }
    return mHwDevice->get_properties(mHwDevice, properties);
}

int SoundTriggerHalLegacy::loadSoundModel(struct sound_trigger_sound_model *sound_model,
                        sound_model_callback_t callback,
                        void *cookie,
                        sound_model_handle_t *handle)
{
    if (mHwDevice == NULL) {
        return -ENODEV;
    }
    return mHwDevice->load_sound_model(mHwDevice, sound_model, callback, cookie, handle);
}

int SoundTriggerHalLegacy::unloadSoundModel(sound_model_handle_t handle)
{
    if (mHwDevice == NULL) {
        return -ENODEV;
    }
    return mHwDevice->unload_sound_model(mHwDevice, handle);
}

int SoundTriggerHalLegacy::startRecognition(sound_model_handle_t handle,
                         const struct sound_trigger_recognition_config *config,
                         recognition_callback_t callback,
                         void *cookie)
{
    if (mHwDevice == NULL) {
        return -ENODEV;
    }
    return mHwDevice->start_recognition(mHwDevice, handle, config, callback, cookie);
}

int SoundTriggerHalLegacy::stopRecognition(sound_model_handle_t handle)
{
    if (mHwDevice == NULL) {
        return -ENODEV;
    }
    return mHwDevice->stop_recognition(mHwDevice, handle);
}

int SoundTriggerHalLegacy::stopAllRecognitions()
{
    if (mHwDevice == NULL) {
        return -ENODEV;
    }
    if (mHwDevice->common.version >= SOUND_TRIGGER_DEVICE_API_VERSION_1_1 &&
     mHwDevice->stop_all_recognitions) {
        return mHwDevice->stop_all_recognitions(mHwDevice);
    }
    return -ENOSYS;
}

} // namespace android
