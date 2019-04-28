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

#define LOG_TAG "SoundTriggerHalHidl"
//#define LOG_NDEBUG 0

#include <media/audiohal/hidl/HalDeathHandler.h>
#include <utils/Log.h>
#include "SoundTriggerHalHidl.h"
#include <hwbinder/IPCThreadState.h>
#include <hwbinder/ProcessState.h>

namespace android {

using android::hardware::Return;
using android::hardware::ProcessState;
using android::hardware::audio::common::V2_0::AudioDevice;

/* static */
sp<SoundTriggerHalInterface> SoundTriggerHalInterface::connectModule(const char *moduleName)
{
    return new SoundTriggerHalHidl(moduleName);
}

int SoundTriggerHalHidl::getProperties(struct sound_trigger_properties *properties)
{
    sp<ISoundTriggerHw> soundtrigger = getService();
    if (soundtrigger == 0) {
        return -ENODEV;
    }

    ISoundTriggerHw::Properties halProperties;
    Return<void> hidlReturn;
    int ret;
    {
        AutoMutex lock(mHalLock);
        hidlReturn = soundtrigger->getProperties([&](int rc, auto res) {
            ret = rc;
            halProperties = res;
            ALOGI("getProperties res implementor %s", res.implementor.c_str());
        });
    }

    if (hidlReturn.isOk()) {
        if (ret == 0) {
            convertPropertiesFromHal(properties, &halProperties);
        }
    } else {
        ALOGE("getProperties error %s", hidlReturn.description().c_str());
        return FAILED_TRANSACTION;
    }
    ALOGI("getProperties ret %d", ret);
    return ret;
}

int SoundTriggerHalHidl::loadSoundModel(struct sound_trigger_sound_model *sound_model,
                        sound_model_callback_t callback,
                        void *cookie,
                        sound_model_handle_t *handle)
{
    if (handle == NULL) {
        return -EINVAL;
    }

    sp<ISoundTriggerHw> soundtrigger = getService();
    if (soundtrigger == 0) {
        return -ENODEV;
    }

    uint32_t modelId;
    {
        AutoMutex lock(mLock);
        do {
            modelId = nextUniqueId();
            ALOGI("loadSoundModel modelId %u", modelId);
            sp<SoundModel> model = mSoundModels.valueFor(modelId);
            ALOGI("loadSoundModel model %p", model.get());
        } while (mSoundModels.valueFor(modelId) != 0 && modelId != 0);
    }
    LOG_ALWAYS_FATAL_IF(modelId == 0,
                        "loadSoundModel(): wrap around in sound model IDs, num loaded models %zd",
                        mSoundModels.size());

    ISoundTriggerHw::SoundModel *halSoundModel =
            convertSoundModelToHal(sound_model);
    if (halSoundModel == NULL) {
        return -EINVAL;
    }

    Return<void> hidlReturn;
    int ret;
    SoundModelHandle halHandle;
    {
        AutoMutex lock(mHalLock);
        if (sound_model->type == SOUND_MODEL_TYPE_KEYPHRASE) {
            hidlReturn = soundtrigger->loadPhraseSoundModel(
                    *(const ISoundTriggerHw::PhraseSoundModel *)halSoundModel,
                    this, modelId, [&](int32_t retval, auto res) {
                ret = retval;
                halHandle = res;
            });

        } else {
            hidlReturn = soundtrigger->loadSoundModel(*halSoundModel,
                    this, modelId, [&](int32_t retval, auto res) {
                ret = retval;
                halHandle = res;
            });
        }
    }

    delete halSoundModel;

    if (hidlReturn.isOk()) {
        if (ret == 0) {
            AutoMutex lock(mLock);
            *handle = (sound_model_handle_t)modelId;
            sp<SoundModel> model = new SoundModel(*handle, callback, cookie, halHandle);
            mSoundModels.add(*handle, model);
        }
    } else {
        ALOGE("loadSoundModel error %s", hidlReturn.description().c_str());
        return FAILED_TRANSACTION;
    }

    return ret;
}

int SoundTriggerHalHidl::unloadSoundModel(sound_model_handle_t handle)
{
    sp<ISoundTriggerHw> soundtrigger = getService();
    if (soundtrigger == 0) {
        return -ENODEV;
    }

    sp<SoundModel> model = removeModel(handle);
    if (model == 0) {
        ALOGE("unloadSoundModel model not found for handle %u", handle);
        return -EINVAL;
    }

    Return<int32_t> hidlReturn(0);
    {
        AutoMutex lock(mHalLock);
        hidlReturn = soundtrigger->unloadSoundModel(model->mHalHandle);
    }

    if (!hidlReturn.isOk()) {
        ALOGE("unloadSoundModel error %s", hidlReturn.description().c_str());
        return FAILED_TRANSACTION;
    }

    return hidlReturn;
}

int SoundTriggerHalHidl::startRecognition(sound_model_handle_t handle,
                         const struct sound_trigger_recognition_config *config,
                         recognition_callback_t callback,
                         void *cookie)
{
    sp<ISoundTriggerHw> soundtrigger = getService();
    if (soundtrigger == 0) {
        return -ENODEV;
    }

    sp<SoundModel> model = getModel(handle);
    if (model == 0) {
        ALOGE("startRecognition model not found for handle %u", handle);
        return -EINVAL;
    }

    model->mRecognitionCallback = callback;
    model->mRecognitionCookie = cookie;

    ISoundTriggerHw::RecognitionConfig *halConfig =
            convertRecognitionConfigToHal(config);

    Return<int32_t> hidlReturn(0);
    {
        AutoMutex lock(mHalLock);
        hidlReturn = soundtrigger->startRecognition(model->mHalHandle, *halConfig, this, handle);
    }

    delete halConfig;

    if (!hidlReturn.isOk()) {
        ALOGE("startRecognition error %s", hidlReturn.description().c_str());
        return FAILED_TRANSACTION;
    }
    return hidlReturn;
}

int SoundTriggerHalHidl::stopRecognition(sound_model_handle_t handle)
{
    sp<ISoundTriggerHw> soundtrigger = getService();
    if (soundtrigger == 0) {
        return -ENODEV;
    }

    sp<SoundModel> model = getModel(handle);
    if (model == 0) {
        ALOGE("stopRecognition model not found for handle %u", handle);
        return -EINVAL;
    }

    Return<int32_t> hidlReturn(0);
    {
        AutoMutex lock(mHalLock);
        hidlReturn = soundtrigger->stopRecognition(model->mHalHandle);
    }

    if (!hidlReturn.isOk()) {
        ALOGE("stopRecognition error %s", hidlReturn.description().c_str());
        return FAILED_TRANSACTION;
    }
    return hidlReturn;
}

int SoundTriggerHalHidl::stopAllRecognitions()
{
    sp<ISoundTriggerHw> soundtrigger = getService();
    if (soundtrigger == 0) {
        return -ENODEV;
    }

    Return<int32_t> hidlReturn(0);
    {
        AutoMutex lock(mHalLock);
        hidlReturn = soundtrigger->stopAllRecognitions();
    }

    if (!hidlReturn.isOk()) {
        ALOGE("stopAllRecognitions error %s", hidlReturn.description().c_str());
        return FAILED_TRANSACTION;
    }
    return hidlReturn;
}

SoundTriggerHalHidl::SoundTriggerHalHidl(const char *moduleName)
    : mModuleName(moduleName), mNextUniqueId(1)
{
    LOG_ALWAYS_FATAL_IF(strcmp(mModuleName, "primary") != 0,
            "Treble soundtrigger only supports primary module");
}

SoundTriggerHalHidl::~SoundTriggerHalHidl()
{
}

sp<ISoundTriggerHw> SoundTriggerHalHidl::getService()
{
    AutoMutex lock(mLock);
    if (mISoundTrigger == 0) {
        if (mModuleName == NULL) {
            mModuleName = "primary";
        }
        mISoundTrigger = ISoundTriggerHw::getService();
        if (mISoundTrigger != 0) {
            mISoundTrigger->linkToDeath(HalDeathHandler::getInstance(), 0 /*cookie*/);
        }
    }
    return mISoundTrigger;
}

sp<SoundTriggerHalHidl::SoundModel> SoundTriggerHalHidl::getModel(sound_model_handle_t handle)
{
    AutoMutex lock(mLock);
    return mSoundModels.valueFor(handle);
}

sp<SoundTriggerHalHidl::SoundModel> SoundTriggerHalHidl::removeModel(sound_model_handle_t handle)
{
    AutoMutex lock(mLock);
    sp<SoundModel> model = mSoundModels.valueFor(handle);
    mSoundModels.removeItem(handle);
    return model;
}

uint32_t SoundTriggerHalHidl::nextUniqueId()
{
    return (uint32_t) atomic_fetch_add_explicit(&mNextUniqueId,
                (uint_fast32_t) 1, memory_order_acq_rel);
}

void SoundTriggerHalHidl::convertUuidToHal(Uuid *halUuid,
                                           const sound_trigger_uuid_t *uuid)
{
    halUuid->timeLow = uuid->timeLow;
    halUuid->timeMid = uuid->timeMid;
    halUuid->versionAndTimeHigh = uuid->timeHiAndVersion;
    halUuid->variantAndClockSeqHigh = uuid->clockSeq;
    memcpy(halUuid->node.data(), &uuid->node[0], sizeof(uuid->node));
}

void SoundTriggerHalHidl::convertUuidFromHal(sound_trigger_uuid_t *uuid,
                                             const Uuid *halUuid)
{
    uuid->timeLow = halUuid->timeLow;
    uuid->timeMid = halUuid->timeMid;
    uuid->timeHiAndVersion = halUuid->versionAndTimeHigh;
    uuid->clockSeq = halUuid->variantAndClockSeqHigh;
    memcpy(&uuid->node[0], halUuid->node.data(), sizeof(uuid->node));
}

void SoundTriggerHalHidl::convertPropertiesFromHal(
        struct sound_trigger_properties *properties,
        const ISoundTriggerHw::Properties *halProperties)
{
    strlcpy(properties->implementor,
            halProperties->implementor.c_str(), SOUND_TRIGGER_MAX_STRING_LEN);
    strlcpy(properties->description,
            halProperties->description.c_str(), SOUND_TRIGGER_MAX_STRING_LEN);
    properties->version = halProperties->version;
    convertUuidFromHal(&properties->uuid, &halProperties->uuid);
    properties->max_sound_models = halProperties->maxSoundModels;
    properties->max_key_phrases = halProperties->maxKeyPhrases;
    properties->max_users = halProperties->maxUsers;
    properties->recognition_modes = halProperties->recognitionModes;
    properties->capture_transition = (bool)halProperties->captureTransition;
    properties->max_buffer_ms = halProperties->maxBufferMs;
    properties->concurrent_capture = (bool)halProperties->concurrentCapture;
    properties->trigger_in_event = (bool)halProperties->triggerInEvent;
    properties->power_consumption_mw = halProperties->powerConsumptionMw;
}

void SoundTriggerHalHidl::convertTriggerPhraseToHal(
        ISoundTriggerHw::Phrase *halTriggerPhrase,
        const struct sound_trigger_phrase *triggerPhrase)
{
    halTriggerPhrase->id = triggerPhrase->id;
    halTriggerPhrase->recognitionModes = triggerPhrase->recognition_mode;
    halTriggerPhrase->users.setToExternal((uint32_t *)&triggerPhrase->users[0], triggerPhrase->num_users);
    halTriggerPhrase->locale = triggerPhrase->locale;
    halTriggerPhrase->text = triggerPhrase->text;
}

ISoundTriggerHw::SoundModel *SoundTriggerHalHidl::convertSoundModelToHal(
        const struct sound_trigger_sound_model *soundModel)
{
    ISoundTriggerHw::SoundModel *halModel = NULL;
    if (soundModel->type == SOUND_MODEL_TYPE_KEYPHRASE) {
        ISoundTriggerHw::PhraseSoundModel *halKeyPhraseModel =
                new ISoundTriggerHw::PhraseSoundModel();
        struct sound_trigger_phrase_sound_model *keyPhraseModel =
                (struct sound_trigger_phrase_sound_model *)soundModel;
        ISoundTriggerHw::Phrase *halPhrases =
                new ISoundTriggerHw::Phrase[keyPhraseModel->num_phrases];


        for (unsigned int i = 0; i < keyPhraseModel->num_phrases; i++) {
            convertTriggerPhraseToHal(&halPhrases[i],
                                      &keyPhraseModel->phrases[i]);
        }
        halKeyPhraseModel->phrases.setToExternal(halPhrases, keyPhraseModel->num_phrases);
        // FIXME: transfer buffer ownership. should have a method for that in hidl_vec
        halKeyPhraseModel->phrases.resize(keyPhraseModel->num_phrases);

        delete[] halPhrases;

        halModel = (ISoundTriggerHw::SoundModel *)halKeyPhraseModel;
    } else {
        halModel = new ISoundTriggerHw::SoundModel();
    }
    halModel->type = (SoundModelType)soundModel->type;
    convertUuidToHal(&halModel->uuid, &soundModel->uuid);
    convertUuidToHal(&halModel->vendorUuid, &soundModel->vendor_uuid);
    halModel->data.setToExternal((uint8_t *)soundModel + soundModel->data_offset, soundModel->data_size);
    halModel->data.resize(soundModel->data_size);

    return halModel;
}

void SoundTriggerHalHidl::convertPhraseRecognitionExtraToHal(
        PhraseRecognitionExtra *halExtra,
        const struct sound_trigger_phrase_recognition_extra *extra)
{
    halExtra->id = extra->id;
    halExtra->recognitionModes = extra->recognition_modes;
    halExtra->confidenceLevel = extra->confidence_level;
    ConfidenceLevel *halLevels =
            new ConfidenceLevel[extra->num_levels];
    for (unsigned int i = 0; i < extra->num_levels; i++) {
        halLevels[i].userId = extra->levels[i].user_id;
        halLevels[i].levelPercent = extra->levels[i].level;
    }
    halExtra->levels.setToExternal(halLevels, extra->num_levels);
    // FIXME: transfer buffer ownership. should have a method for that in hidl_vec
    halExtra->levels.resize(extra->num_levels);

    delete[] halLevels;
}


ISoundTriggerHw::RecognitionConfig *SoundTriggerHalHidl::convertRecognitionConfigToHal(
        const struct sound_trigger_recognition_config *config)
{
    ISoundTriggerHw::RecognitionConfig *halConfig =
            new ISoundTriggerHw::RecognitionConfig();

    halConfig->captureHandle = config->capture_handle;
    halConfig->captureDevice = (AudioDevice)config->capture_device;
    halConfig->captureRequested = (uint32_t)config->capture_requested;

    PhraseRecognitionExtra *halExtras =
            new PhraseRecognitionExtra[config->num_phrases];

    for (unsigned int i = 0; i < config->num_phrases; i++) {
        convertPhraseRecognitionExtraToHal(&halExtras[i],
                                  &config->phrases[i]);
    }
    halConfig->phrases.setToExternal(halExtras, config->num_phrases);
    // FIXME: transfer buffer ownership. should have a method for that in hidl_vec
    halConfig->phrases.resize(config->num_phrases);

    delete[] halExtras;

    halConfig->data.setToExternal((uint8_t *)config + config->data_offset, config->data_size);

    return halConfig;
}


// ISoundTriggerHwCallback
::android::hardware::Return<void> SoundTriggerHalHidl::recognitionCallback(
        const ISoundTriggerHwCallback::RecognitionEvent& halEvent,
        CallbackCookie cookie)
{
    sp<SoundModel> model;
    {
        AutoMutex lock(mLock);
        model = mSoundModels.valueFor((SoundModelHandle)cookie);
        if (model == 0) {
            return Return<void>();
        }
    }
    struct sound_trigger_recognition_event *event = convertRecognitionEventFromHal(&halEvent);
    if (event == NULL) {
        return Return<void>();
    }
    event->model = model->mHandle;
    model->mRecognitionCallback(event, model->mRecognitionCookie);

    free(event);

    return Return<void>();
}

::android::hardware::Return<void> SoundTriggerHalHidl::phraseRecognitionCallback(
        const ISoundTriggerHwCallback::PhraseRecognitionEvent& halEvent,
        CallbackCookie cookie)
{
    sp<SoundModel> model;
    {
        AutoMutex lock(mLock);
        model = mSoundModels.valueFor((SoundModelHandle)cookie);
        if (model == 0) {
            return Return<void>();
        }
    }

    struct sound_trigger_recognition_event *event = convertRecognitionEventFromHal(
                                   (const ISoundTriggerHwCallback::RecognitionEvent *)&halEvent);
    if (event == NULL) {
        return Return<void>();
    }

    event->model = model->mHandle;
    model->mRecognitionCallback(event, model->mRecognitionCookie);

    free(event);

    return Return<void>();
}

::android::hardware::Return<void> SoundTriggerHalHidl::soundModelCallback(
        const ISoundTriggerHwCallback::ModelEvent& halEvent,
        CallbackCookie cookie)
{
    sp<SoundModel> model;
    {
        AutoMutex lock(mLock);
        model = mSoundModels.valueFor((SoundModelHandle)cookie);
        if (model == 0) {
            return Return<void>();
        }
    }

    struct sound_trigger_model_event *event = convertSoundModelEventFromHal(&halEvent);
    if (event == NULL) {
        return Return<void>();
    }

    event->model = model->mHandle;
    model->mSoundModelCallback(event, model->mSoundModelCookie);

    free(event);

    return Return<void>();
}


struct sound_trigger_model_event *SoundTriggerHalHidl::convertSoundModelEventFromHal(
                                              const ISoundTriggerHwCallback::ModelEvent *halEvent)
{
    struct sound_trigger_model_event *event = (struct sound_trigger_model_event *)malloc(
            sizeof(struct sound_trigger_model_event) +
            halEvent->data.size());
    if (event == NULL) {
        return NULL;
    }

    event->status = (int)halEvent->status;
    // event->model to be set by caller
    event->data_offset = sizeof(struct sound_trigger_model_event);
    event->data_size = halEvent->data.size();
    uint8_t *dst = (uint8_t *)event + event->data_offset;
    uint8_t *src = (uint8_t *)&halEvent->data[0];
    memcpy(dst, src, halEvent->data.size());

    return event;
}

void SoundTriggerHalHidl::convertPhraseRecognitionExtraFromHal(
        struct sound_trigger_phrase_recognition_extra *extra,
        const PhraseRecognitionExtra *halExtra)
{
    extra->id = halExtra->id;
    extra->recognition_modes = halExtra->recognitionModes;
    extra->confidence_level = halExtra->confidenceLevel;

    size_t i;
    for (i = 0; i < halExtra->levels.size() && i < SOUND_TRIGGER_MAX_USERS; i++) {
        extra->levels[i].user_id = halExtra->levels[i].userId;
        extra->levels[i].level = halExtra->levels[i].levelPercent;
    }
    extra->num_levels = (unsigned int)i;
}


struct sound_trigger_recognition_event *SoundTriggerHalHidl::convertRecognitionEventFromHal(
        const ISoundTriggerHwCallback::RecognitionEvent *halEvent)
{
    struct sound_trigger_recognition_event *event;

    if (halEvent->type == SoundModelType::KEYPHRASE) {
        struct sound_trigger_phrase_recognition_event *phraseEvent =
                (struct sound_trigger_phrase_recognition_event *)malloc(
                        sizeof(struct sound_trigger_phrase_recognition_event) +
                        halEvent->data.size());
        if (phraseEvent == NULL) {
            return NULL;
        }
        const ISoundTriggerHwCallback::PhraseRecognitionEvent *halPhraseEvent =
                (const ISoundTriggerHwCallback::PhraseRecognitionEvent *)halEvent;

        for (unsigned int i = 0; i < halPhraseEvent->phraseExtras.size(); i++) {
            convertPhraseRecognitionExtraFromHal(&phraseEvent->phrase_extras[i],
                                                 &halPhraseEvent->phraseExtras[i]);
        }
        phraseEvent->num_phrases = halPhraseEvent->phraseExtras.size();
        event = (struct sound_trigger_recognition_event *)phraseEvent;
        event->data_offset = sizeof(sound_trigger_phrase_recognition_event);
    } else {
        event = (struct sound_trigger_recognition_event *)malloc(
                sizeof(struct sound_trigger_recognition_event) + halEvent->data.size());
        if (event == NULL) {
            return NULL;
        }
        event->data_offset = sizeof(sound_trigger_recognition_event);
    }
    event->status = (int)halEvent->status;
    event->type = (sound_trigger_sound_model_type_t)halEvent->type;
    // event->model to be set by caller
    event->capture_available = (bool)halEvent->captureAvailable;
    event->capture_session = halEvent->captureSession;
    event->capture_delay_ms = halEvent->captureDelayMs;
    event->capture_preamble_ms = halEvent->capturePreambleMs;
    event->trigger_in_data = (bool)halEvent->triggerInData;
    event->audio_config.sample_rate = halEvent->audioConfig.sampleRateHz;
    event->audio_config.channel_mask = (audio_channel_mask_t)halEvent->audioConfig.channelMask;
    event->audio_config.format = (audio_format_t)halEvent->audioConfig.format;

    event->data_size = halEvent->data.size();
    uint8_t *dst = (uint8_t *)event + event->data_offset;
    uint8_t *src = (uint8_t *)&halEvent->data[0];
    memcpy(dst, src, halEvent->data.size());

    return event;
}

} // namespace android
