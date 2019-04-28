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

#ifndef ANDROID_HARDWARE_SOUNDTRIGGER_HAL_HIDL_H
#define ANDROID_HARDWARE_SOUNDTRIGGER_HAL_HIDL_H

#include <stdatomic.h>
#include <utils/RefBase.h>
#include <utils/KeyedVector.h>
#include <utils/Vector.h>
#include <utils/threads.h>
#include "SoundTriggerHalInterface.h"
#include <android/hardware/soundtrigger/2.0/types.h>
#include <android/hardware/soundtrigger/2.0/ISoundTriggerHw.h>
#include <android/hardware/soundtrigger/2.0/ISoundTriggerHwCallback.h>

namespace android {

using android::hardware::audio::common::V2_0::Uuid;
using android::hardware::soundtrigger::V2_0::ConfidenceLevel;
using android::hardware::soundtrigger::V2_0::PhraseRecognitionExtra;
using android::hardware::soundtrigger::V2_0::SoundModelType;
using android::hardware::soundtrigger::V2_0::SoundModelHandle;
using android::hardware::soundtrigger::V2_0::ISoundTriggerHw;
using android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback;

class SoundTriggerHalHidl : public SoundTriggerHalInterface,
                            public virtual ISoundTriggerHwCallback

{
public:
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
        virtual int stopAllRecognitions();

        // ISoundTriggerHwCallback
        virtual ::android::hardware::Return<void> recognitionCallback(
                const ISoundTriggerHwCallback::RecognitionEvent& event, CallbackCookie cookie);
        virtual ::android::hardware::Return<void> phraseRecognitionCallback(
                const ISoundTriggerHwCallback::PhraseRecognitionEvent& event, int32_t cookie);
        virtual ::android::hardware::Return<void> soundModelCallback(
                const ISoundTriggerHwCallback::ModelEvent& event, CallbackCookie cookie);
private:
        class SoundModel : public RefBase {
        public:
            SoundModel(sound_model_handle_t handle, sound_model_callback_t callback,
                       void *cookie, android::hardware::soundtrigger::V2_0::SoundModelHandle halHandle)
                 : mHandle(handle), mHalHandle(halHandle),
                   mSoundModelCallback(callback), mSoundModelCookie(cookie),
                   mRecognitionCallback(NULL), mRecognitionCookie(NULL) {}
            ~SoundModel() {}

            sound_model_handle_t   mHandle;
            android::hardware::soundtrigger::V2_0::SoundModelHandle mHalHandle;
            sound_model_callback_t mSoundModelCallback;
            void *                 mSoundModelCookie;
            recognition_callback_t mRecognitionCallback;
            void *                 mRecognitionCookie;
        };

        friend class SoundTriggerHalInterface;

        explicit SoundTriggerHalHidl(const char *moduleName = NULL);
        virtual  ~SoundTriggerHalHidl();

        void convertUuidToHal(Uuid *halUuid,
                              const sound_trigger_uuid_t *uuid);
        void convertUuidFromHal(sound_trigger_uuid_t *uuid,
                                const Uuid *halUuid);

        void convertPropertiesFromHal(
                struct sound_trigger_properties *properties,
                const ISoundTriggerHw::Properties *halProperties);

        void convertTriggerPhraseToHal(
                ISoundTriggerHw::Phrase *halTriggerPhrase,
                const struct sound_trigger_phrase *triggerPhrase);
        ISoundTriggerHw::SoundModel *convertSoundModelToHal(
                const struct sound_trigger_sound_model *soundModel);

        void convertPhraseRecognitionExtraToHal(
                PhraseRecognitionExtra *halExtra,
                const struct sound_trigger_phrase_recognition_extra *extra);
        ISoundTriggerHw::RecognitionConfig *convertRecognitionConfigToHal(
                const struct sound_trigger_recognition_config *config);

        struct sound_trigger_model_event *convertSoundModelEventFromHal(
                                              const ISoundTriggerHwCallback::ModelEvent *halEvent);
        void convertPhraseRecognitionExtraFromHal(
                struct sound_trigger_phrase_recognition_extra *extra,
                const PhraseRecognitionExtra *halExtra);
        struct sound_trigger_recognition_event *convertRecognitionEventFromHal(
                const ISoundTriggerHwCallback::RecognitionEvent *halEvent);

        uint32_t nextUniqueId();
        sp<ISoundTriggerHw> getService();
        sp<SoundModel> getModel(sound_model_handle_t handle);
        sp<SoundModel> removeModel(sound_model_handle_t handle);

        static pthread_once_t sOnceControl;
        static void sOnceInit();

        Mutex mLock;
        Mutex mHalLock;
        const char *mModuleName;
        volatile atomic_uint_fast32_t  mNextUniqueId;
        // Effect chains without a valid thread
        DefaultKeyedVector< sound_model_handle_t , sp<SoundModel> > mSoundModels;
        sp<::android::hardware::soundtrigger::V2_0::ISoundTriggerHw> mISoundTrigger;
};

} // namespace android

#endif // ANDROID_HARDWARE_SOUNDTRIGGER_HAL_HIDL_H
