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

#ifndef ANDROID_HARDWARE_SOUNDTRIGGER_V2_0_IMPLEMENTATION_H
#define ANDROID_HARDWARE_SOUNDTRIGGER_V2_0_IMPLEMENTATION_H

#include <android/hardware/soundtrigger/2.0/ISoundTriggerHw.h>
#include <android/hardware/soundtrigger/2.0/ISoundTriggerHwCallback.h>
#include <hidl/Status.h>
#include <stdatomic.h>
#include <utils/threads.h>
#include <utils/KeyedVector.h>
#include <system/sound_trigger.h>
#include <hardware/sound_trigger.h>

namespace android {
namespace hardware {
namespace soundtrigger {
namespace V2_0 {
namespace implementation {

using ::android::hardware::audio::common::V2_0::Uuid;
using ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback;


class SoundTriggerHalImpl : public ISoundTriggerHw {
public:
        SoundTriggerHalImpl();

        // Methods from ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw follow.
        Return<void> getProperties(getProperties_cb _hidl_cb)  override;
        Return<void> loadSoundModel(const ISoundTriggerHw::SoundModel& soundModel,
                                    const sp<ISoundTriggerHwCallback>& callback,
                                    ISoundTriggerHwCallback::CallbackCookie cookie,
                                    loadSoundModel_cb _hidl_cb)  override;
        Return<void> loadPhraseSoundModel(const ISoundTriggerHw::PhraseSoundModel& soundModel,
                                    const sp<ISoundTriggerHwCallback>& callback,
                                    ISoundTriggerHwCallback::CallbackCookie cookie,
                                    loadPhraseSoundModel_cb _hidl_cb)  override;

        Return<int32_t> unloadSoundModel(SoundModelHandle modelHandle)  override;
        Return<int32_t> startRecognition(SoundModelHandle modelHandle,
                                      const ISoundTriggerHw::RecognitionConfig& config,
                                      const sp<ISoundTriggerHwCallback>& callback,
                                      ISoundTriggerHwCallback::CallbackCookie cookie)  override;
        Return<int32_t> stopRecognition(SoundModelHandle modelHandle)  override;
        Return<int32_t> stopAllRecognitions()  override;

        // RefBase
        virtual     void        onFirstRef();

        static void soundModelCallback(struct sound_trigger_model_event *halEvent,
                                       void *cookie);
        static void recognitionCallback(struct sound_trigger_recognition_event *halEvent,
                                        void *cookie);

private:

        class SoundModelClient : public RefBase {
        public:
            SoundModelClient(uint32_t id, sp<ISoundTriggerHwCallback> callback,
                             ISoundTriggerHwCallback::CallbackCookie cookie)
                : mId(id), mCallback(callback), mCookie(cookie) {}
            virtual ~SoundModelClient() {}

            uint32_t mId;
            sound_model_handle_t mHalHandle;
            sp<ISoundTriggerHwCallback> mCallback;
            ISoundTriggerHwCallback::CallbackCookie mCookie;
        };

        uint32_t nextUniqueId();
        void convertUuidFromHal(Uuid *uuid,
                                const sound_trigger_uuid_t *halUuid);
        void convertUuidToHal(sound_trigger_uuid_t *halUuid,
                              const Uuid *uuid);
        void convertPropertiesFromHal(ISoundTriggerHw::Properties *properties,
                                      const struct sound_trigger_properties *halProperties);
        void convertTriggerPhraseToHal(struct sound_trigger_phrase *halTriggerPhrase,
                                       const ISoundTriggerHw::Phrase *triggerPhrase);
        // returned HAL sound model must be freed by caller
        struct sound_trigger_sound_model *convertSoundModelToHal(
                    const ISoundTriggerHw::SoundModel *soundModel);
        void convertPhraseRecognitionExtraToHal(
                struct sound_trigger_phrase_recognition_extra *halExtra,
                const PhraseRecognitionExtra *extra);
        // returned recognition config must be freed by caller
        struct sound_trigger_recognition_config *convertRecognitionConfigToHal(
                const ISoundTriggerHw::RecognitionConfig *config);


        static void convertSoundModelEventFromHal(ISoundTriggerHwCallback::ModelEvent *event,
                                            const struct sound_trigger_model_event *halEvent);
        static ISoundTriggerHwCallback::RecognitionEvent *convertRecognitionEventFromHal(
                                            const struct sound_trigger_recognition_event *halEvent);
        static void convertPhraseRecognitionExtraFromHal(PhraseRecognitionExtra *extra,
                                    const struct sound_trigger_phrase_recognition_extra *halExtra);

        int doLoadSoundModel(const ISoundTriggerHw::SoundModel& soundModel,
                             const sp<ISoundTriggerHwCallback>& callback,
                             ISoundTriggerHwCallback::CallbackCookie cookie,
                             uint32_t *modelId);

        virtual             ~SoundTriggerHalImpl();

        const char *                                        mModuleName;
        struct sound_trigger_hw_device*                     mHwDevice;
        volatile atomic_uint_fast32_t                       mNextModelId;
        DefaultKeyedVector<int32_t, sp<SoundModelClient> >  mClients;
        Mutex                                               mLock;
};

extern "C" ISoundTriggerHw *HIDL_FETCH_ISoundTriggerHw(const char *name);

}  // namespace implementation
}  // namespace V2_0
}  // namespace soundtrigger
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_SOUNDTRIGGER_V2_0_IMPLEMENTATION_H

