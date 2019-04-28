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

#ifndef ANDROID_HARDWARE_SOUNDTRIGGER_HAL_LEGACY_H
#define ANDROID_HARDWARE_SOUNDTRIGGER_HAL_LEGACY_H

#include "SoundTriggerHalInterface.h"

namespace android {

class SoundTriggerHalLegacy : public SoundTriggerHalInterface

{
public:
        virtual             ~SoundTriggerHalLegacy();

        virtual int getProperties(struct sound_trigger_properties *properties);

        /*
         * Load a sound model. Once loaded, recognition of this model can be started and stopped.
         * Only one active recognition per model at a time. The SoundTrigger service will handle
         * concurrent recognition requests by different users/applications on the same model.
         * The implementation returns a unique handle used by other functions (unload_sound_model(),
         * start_recognition(), etc...
         */
        virtual int loadSoundModel(struct sound_trigger_sound_model *sound_model,
                                sound_model_callback_t callback,
                                void *cookie,
                                sound_model_handle_t *handle);

        /*
         * Unload a sound model. A sound model can be unloaded to make room for a new one to overcome
         * implementation limitations.
         */
        virtual int unloadSoundModel(sound_model_handle_t handle);

        /* Start recognition on a given model. Only one recognition active at a time per model.
         * Once recognition succeeds of fails, the callback is called.
         * TODO: group recognition configuration parameters into one struct and add key phrase options.
         */
        virtual int startRecognition(sound_model_handle_t handle,
                                 const struct sound_trigger_recognition_config *config,
                                 recognition_callback_t callback,
                                 void *cookie);

        /* Stop recognition on a given model.
         * The implementation does not have to call the callback when stopped via this method.
         */
        virtual int stopRecognition(sound_model_handle_t handle);

        /* Stop recognition on all models.
         * Only supported for device api versions SOUND_TRIGGER_DEVICE_API_VERSION_1_1 or above.
         * If no implementation is provided, stop_recognition will be called for each running model.
         */
        int stopAllRecognitions();

        // RefBase
        virtual     void        onFirstRef();

private:

        friend class SoundTriggerHalInterface;

        explicit SoundTriggerHalLegacy(const char *moduleName = NULL);

        const char *mModuleName;
        struct sound_trigger_hw_device*        mHwDevice;
};

} // namespace android

#endif // ANDROID_HARDWARE_SOUNDTRIGGER_HAL_LEGACY_H
