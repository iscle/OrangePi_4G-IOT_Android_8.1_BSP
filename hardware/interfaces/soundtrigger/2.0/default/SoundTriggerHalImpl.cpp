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

#define LOG_TAG "SoundTriggerHalImpl"
//#define LOG_NDEBUG 0

#include <android/log.h>
#include "SoundTriggerHalImpl.h"


namespace android {
namespace hardware {
namespace soundtrigger {
namespace V2_0 {
namespace implementation {

// static
void SoundTriggerHalImpl::soundModelCallback(struct sound_trigger_model_event *halEvent,
                                               void *cookie)
{
    if (halEvent == NULL) {
        ALOGW("soundModelCallback called with NULL event");
        return;
    }
    sp<SoundModelClient> client =
            wp<SoundModelClient>(static_cast<SoundModelClient *>(cookie)).promote();
    if (client == 0) {
        ALOGW("soundModelCallback called on stale client");
        return;
    }
    if (halEvent->model != client->mHalHandle) {
        ALOGW("soundModelCallback call with wrong handle %d on client with handle %d",
              (int)halEvent->model, (int)client->mHalHandle);
        return;
    }

    ISoundTriggerHwCallback::ModelEvent event;
    convertSoundModelEventFromHal(&event, halEvent);
    event.model = client->mId;

    client->mCallback->soundModelCallback(event, client->mCookie);
}

// static
void SoundTriggerHalImpl::recognitionCallback(struct sound_trigger_recognition_event *halEvent,
                                               void *cookie)
{
    if (halEvent == NULL) {
        ALOGW("recognitionCallback call NULL event");
        return;
    }
    sp<SoundModelClient> client =
            wp<SoundModelClient>(static_cast<SoundModelClient *>(cookie)).promote();
    if (client == 0) {
        ALOGW("soundModelCallback called on stale client");
        return;
    }

    ISoundTriggerHwCallback::RecognitionEvent *event = convertRecognitionEventFromHal(halEvent);
    event->model = client->mId;
    if (halEvent->type == SOUND_MODEL_TYPE_KEYPHRASE) {
        client->mCallback->phraseRecognitionCallback(
                *(reinterpret_cast<ISoundTriggerHwCallback::PhraseRecognitionEvent *>(event)),
                client->mCookie);
    } else {
        client->mCallback->recognitionCallback(*event, client->mCookie);
    }
    delete event;
}



// Methods from ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw follow.
Return<void> SoundTriggerHalImpl::getProperties(getProperties_cb _hidl_cb)
{
    ALOGV("getProperties() mHwDevice %p", mHwDevice);
    int ret;
    struct sound_trigger_properties halProperties;
    ISoundTriggerHw::Properties properties;

    if (mHwDevice == NULL) {
        ret = -ENODEV;
        goto exit;
    }

    ret = mHwDevice->get_properties(mHwDevice, &halProperties);

    convertPropertiesFromHal(&properties, &halProperties);

    ALOGV("getProperties implementor %s recognitionModes %08x",
          properties.implementor.c_str(), properties.recognitionModes);

exit:
    _hidl_cb(ret, properties);
    return Void();
}

int SoundTriggerHalImpl::doLoadSoundModel(const ISoundTriggerHw::SoundModel& soundModel,
                                                 const sp<ISoundTriggerHwCallback>& callback,
                                                 ISoundTriggerHwCallback::CallbackCookie cookie,
                                                 uint32_t *modelId)
{
    int32_t ret = 0;
    struct sound_trigger_sound_model *halSoundModel;
    *modelId = 0;
    sp<SoundModelClient> client;

    ALOGV("doLoadSoundModel() data size %zu", soundModel.data.size());

    if (mHwDevice == NULL) {
        ret = -ENODEV;
        goto exit;
    }

    halSoundModel = convertSoundModelToHal(&soundModel);
    if (halSoundModel == NULL) {
        ret = -EINVAL;
        goto exit;
    }

    {
        AutoMutex lock(mLock);
        do {
            *modelId = nextUniqueId();
        } while (mClients.valueFor(*modelId) != 0 && *modelId != 0);
    }
    LOG_ALWAYS_FATAL_IF(*modelId == 0,
                        "wrap around in sound model IDs, num loaded models %zu", mClients.size());

    client = new SoundModelClient(*modelId, callback, cookie);

    ret = mHwDevice->load_sound_model(mHwDevice, halSoundModel, soundModelCallback,
                                          client.get(), &client->mHalHandle);

    free(halSoundModel);

    if (ret != 0) {
        goto exit;
    }

    {
        AutoMutex lock(mLock);
        mClients.add(*modelId, client);
    }

exit:
    return ret;
}

Return<void> SoundTriggerHalImpl::loadSoundModel(const ISoundTriggerHw::SoundModel& soundModel,
                                                 const sp<ISoundTriggerHwCallback>& callback,
                                                 ISoundTriggerHwCallback::CallbackCookie cookie,
                                                 loadSoundModel_cb _hidl_cb)
{
    uint32_t modelId = 0;
    int32_t ret = doLoadSoundModel(soundModel, callback, cookie, &modelId);

    _hidl_cb(ret, modelId);
    return Void();
}

Return<void> SoundTriggerHalImpl::loadPhraseSoundModel(
                                            const ISoundTriggerHw::PhraseSoundModel& soundModel,
                                            const sp<ISoundTriggerHwCallback>& callback,
                                            ISoundTriggerHwCallback::CallbackCookie cookie,
                                            ISoundTriggerHw::loadPhraseSoundModel_cb _hidl_cb)
{
    uint32_t modelId = 0;
    int32_t ret = doLoadSoundModel((const ISoundTriggerHw::SoundModel&)soundModel,
                                   callback, cookie, &modelId);

    _hidl_cb(ret, modelId);
    return Void();
}

Return<int32_t> SoundTriggerHalImpl::unloadSoundModel(SoundModelHandle modelHandle)
{
    int32_t ret;
    sp<SoundModelClient> client;

    if (mHwDevice == NULL) {
        ret = -ENODEV;
        goto exit;
    }

    {
        AutoMutex lock(mLock);
        client = mClients.valueFor(modelHandle);
        if (client == 0) {
            ret = -ENOSYS;
            goto exit;
        }
    }

    ret = mHwDevice->unload_sound_model(mHwDevice, client->mHalHandle);

    mClients.removeItem(modelHandle);

exit:
    return ret;
}

Return<int32_t> SoundTriggerHalImpl::startRecognition(SoundModelHandle modelHandle,
                                           const ISoundTriggerHw::RecognitionConfig& config,
                                           const sp<ISoundTriggerHwCallback>& callback __unused,
                                           ISoundTriggerHwCallback::CallbackCookie cookie __unused)
{
    int32_t ret;
    sp<SoundModelClient> client;
    struct sound_trigger_recognition_config *halConfig;

    if (mHwDevice == NULL) {
        ret = -ENODEV;
        goto exit;
    }

    {
        AutoMutex lock(mLock);
        client = mClients.valueFor(modelHandle);
        if (client == 0) {
            ret = -ENOSYS;
            goto exit;
        }
    }


    halConfig = convertRecognitionConfigToHal(&config);

    if (halConfig == NULL) {
        ret = -EINVAL;
        goto exit;
    }
    ret = mHwDevice->start_recognition(mHwDevice, client->mHalHandle, halConfig,
                                 recognitionCallback, client.get());

    free(halConfig);

exit:
    return ret;
}

Return<int32_t> SoundTriggerHalImpl::stopRecognition(SoundModelHandle modelHandle)
{
    int32_t ret;
    sp<SoundModelClient> client;
    if (mHwDevice == NULL) {
        ret = -ENODEV;
        goto exit;
    }

    {
        AutoMutex lock(mLock);
        client = mClients.valueFor(modelHandle);
        if (client == 0) {
            ret = -ENOSYS;
            goto exit;
        }
    }

    ret = mHwDevice->stop_recognition(mHwDevice, client->mHalHandle);

exit:
    return ret;
}

Return<int32_t> SoundTriggerHalImpl::stopAllRecognitions()
{
    int32_t ret;
    if (mHwDevice == NULL) {
        ret = -ENODEV;
        goto exit;
    }

    if (mHwDevice->common.version >= SOUND_TRIGGER_DEVICE_API_VERSION_1_1 &&
            mHwDevice->stop_all_recognitions) {
        ret = mHwDevice->stop_all_recognitions(mHwDevice);
    } else {
        ret = -ENOSYS;
    }
exit:
    return ret;
}

SoundTriggerHalImpl::SoundTriggerHalImpl()
    : mModuleName("primary"), mHwDevice(NULL), mNextModelId(1)
{
}

void SoundTriggerHalImpl::onFirstRef()
{
    const hw_module_t *mod;
    int rc;

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
        sound_trigger_hw_device_close(mHwDevice);
        mHwDevice = NULL;
        return;
    }

    ALOGI("onFirstRef() mModuleName %s mHwDevice %p", mModuleName, mHwDevice);
}

SoundTriggerHalImpl::~SoundTriggerHalImpl()
{
    if (mHwDevice != NULL) {
        sound_trigger_hw_device_close(mHwDevice);
    }
}

uint32_t SoundTriggerHalImpl::nextUniqueId()
{
    return (uint32_t) atomic_fetch_add_explicit(&mNextModelId,
                (uint_fast32_t) 1, memory_order_acq_rel);
}

void SoundTriggerHalImpl::convertUuidFromHal(Uuid *uuid,
                                             const sound_trigger_uuid_t *halUuid)
{
    uuid->timeLow = halUuid->timeLow;
    uuid->timeMid = halUuid->timeMid;
    uuid->versionAndTimeHigh = halUuid->timeHiAndVersion;
    uuid->variantAndClockSeqHigh = halUuid->clockSeq;
    memcpy(&uuid->node[0], &halUuid->node[0], 6);
}

void SoundTriggerHalImpl::convertUuidToHal(sound_trigger_uuid_t *halUuid,
                                           const Uuid *uuid)
{
    halUuid->timeLow = uuid->timeLow;
    halUuid->timeMid = uuid->timeMid;
    halUuid->timeHiAndVersion = uuid->versionAndTimeHigh;
    halUuid->clockSeq = uuid->variantAndClockSeqHigh;
    memcpy(&halUuid->node[0], &uuid->node[0], 6);
}

void SoundTriggerHalImpl::convertPropertiesFromHal(
        ISoundTriggerHw::Properties *properties,
        const struct sound_trigger_properties *halProperties)
{
    properties->implementor = halProperties->implementor;
    properties->description = halProperties->description;
    properties->version = halProperties->version;
    convertUuidFromHal(&properties->uuid, &halProperties->uuid);
    properties->maxSoundModels = halProperties->max_sound_models;
    properties->maxKeyPhrases = halProperties->max_key_phrases;
    properties->maxUsers = halProperties->max_users;
    properties->recognitionModes = halProperties->recognition_modes;
    properties->captureTransition = halProperties->capture_transition;
    properties->maxBufferMs = halProperties->max_buffer_ms;
    properties->concurrentCapture = halProperties->concurrent_capture;
    properties->triggerInEvent = halProperties->trigger_in_event;
    properties->powerConsumptionMw = halProperties->power_consumption_mw;

}

void SoundTriggerHalImpl::convertTriggerPhraseToHal(
        struct sound_trigger_phrase *halTriggerPhrase,
        const ISoundTriggerHw::Phrase *triggerPhrase)
{
    halTriggerPhrase->id = triggerPhrase->id;
    halTriggerPhrase->recognition_mode = triggerPhrase->recognitionModes;
    unsigned int i;
    for (i = 0; i < triggerPhrase->users.size(); i++) {
        halTriggerPhrase->users[i] = triggerPhrase->users[i];
    }
    halTriggerPhrase->num_users = i;

    strlcpy(halTriggerPhrase->locale,
            triggerPhrase->locale.c_str(), SOUND_TRIGGER_MAX_LOCALE_LEN);
    strlcpy(halTriggerPhrase->text,
            triggerPhrase->text.c_str(), SOUND_TRIGGER_MAX_STRING_LEN);
}

struct sound_trigger_sound_model *SoundTriggerHalImpl::convertSoundModelToHal(
        const ISoundTriggerHw::SoundModel *soundModel)
{
    struct sound_trigger_sound_model *halModel = NULL;
    if (soundModel->type == SoundModelType::KEYPHRASE) {
        size_t allocSize =
                sizeof(struct sound_trigger_phrase_sound_model) + soundModel->data.size();
        struct sound_trigger_phrase_sound_model *halKeyPhraseModel =
                static_cast<struct sound_trigger_phrase_sound_model *>(malloc(allocSize));
        LOG_ALWAYS_FATAL_IF(halKeyPhraseModel == NULL,
                        "malloc failed for size %zu in convertSoundModelToHal PHRASE", allocSize);

        const ISoundTriggerHw::PhraseSoundModel *keyPhraseModel =
                reinterpret_cast<const ISoundTriggerHw::PhraseSoundModel *>(soundModel);

        size_t i;
        for (i = 0; i < keyPhraseModel->phrases.size() && i < SOUND_TRIGGER_MAX_PHRASES; i++) {
            convertTriggerPhraseToHal(&halKeyPhraseModel->phrases[i],
                                      &keyPhraseModel->phrases[i]);
        }
        halKeyPhraseModel->num_phrases = (unsigned int)i;
        halModel = reinterpret_cast<struct sound_trigger_sound_model *>(halKeyPhraseModel);
        halModel->data_offset = sizeof(struct sound_trigger_phrase_sound_model);
    } else {
        size_t allocSize =
                sizeof(struct sound_trigger_sound_model) + soundModel->data.size();
        halModel = static_cast<struct sound_trigger_sound_model *>(malloc(allocSize));
        LOG_ALWAYS_FATAL_IF(halModel == NULL,
                            "malloc failed for size %zu in convertSoundModelToHal GENERIC",
                            allocSize);

        halModel->data_offset = sizeof(struct sound_trigger_sound_model);
    }
    halModel->type = (sound_trigger_sound_model_type_t)soundModel->type;
    convertUuidToHal(&halModel->uuid, &soundModel->uuid);
    convertUuidToHal(&halModel->vendor_uuid, &soundModel->vendorUuid);
    halModel->data_size = soundModel->data.size();
    uint8_t *dst = reinterpret_cast<uint8_t *>(halModel) + halModel->data_offset;
    const uint8_t *src = reinterpret_cast<const uint8_t *>(&soundModel->data[0]);
    memcpy(dst, src, soundModel->data.size());

    return halModel;
}

void SoundTriggerHalImpl::convertPhraseRecognitionExtraToHal(
        struct sound_trigger_phrase_recognition_extra *halExtra,
        const PhraseRecognitionExtra *extra)
{
    halExtra->id = extra->id;
    halExtra->recognition_modes = extra->recognitionModes;
    halExtra->confidence_level = extra->confidenceLevel;

    unsigned int i;
    for (i = 0; i < extra->levels.size() && i < SOUND_TRIGGER_MAX_USERS; i++) {
        halExtra->levels[i].user_id = extra->levels[i].userId;
        halExtra->levels[i].level = extra->levels[i].levelPercent;
    }
    halExtra->num_levels = i;
}

struct sound_trigger_recognition_config *SoundTriggerHalImpl::convertRecognitionConfigToHal(
        const ISoundTriggerHw::RecognitionConfig *config)
{
    size_t allocSize = sizeof(struct sound_trigger_recognition_config) + config->data.size();
    struct sound_trigger_recognition_config *halConfig =
            static_cast<struct sound_trigger_recognition_config *>(malloc(allocSize));

    LOG_ALWAYS_FATAL_IF(halConfig == NULL,
                        "malloc failed for size %zu in convertRecognitionConfigToHal",
                        allocSize);

    halConfig->capture_handle = (audio_io_handle_t)config->captureHandle;
    halConfig->capture_device = (audio_devices_t)config->captureDevice;
    halConfig->capture_requested = config->captureRequested;

    unsigned int i;
    for (i = 0; i < config->phrases.size() && i < SOUND_TRIGGER_MAX_PHRASES; i++) {
        convertPhraseRecognitionExtraToHal(&halConfig->phrases[i],
                                  &config->phrases[i]);
    }
    halConfig->num_phrases = i;

    halConfig->data_offset = sizeof(struct sound_trigger_recognition_config);
    halConfig->data_size = config->data.size();
    uint8_t *dst = reinterpret_cast<uint8_t *>(halConfig) + halConfig->data_offset;
    const uint8_t *src = reinterpret_cast<const uint8_t *>(&config->data[0]);
    memcpy(dst, src, config->data.size());
    return halConfig;
}

// static
void SoundTriggerHalImpl::convertSoundModelEventFromHal(ISoundTriggerHwCallback::ModelEvent *event,
                                                const struct sound_trigger_model_event *halEvent)
{
    event->status = (ISoundTriggerHwCallback::SoundModelStatus)halEvent->status;
    // event->model to be remapped by called
    event->data.setToExternal(
            const_cast<uint8_t *>(reinterpret_cast<const uint8_t *>(halEvent)) + halEvent->data_offset,
            halEvent->data_size);
}

// static
ISoundTriggerHwCallback::RecognitionEvent *SoundTriggerHalImpl::convertRecognitionEventFromHal(
                                            const struct sound_trigger_recognition_event *halEvent)
{
    ISoundTriggerHwCallback::RecognitionEvent * event;

    if (halEvent->type == SOUND_MODEL_TYPE_KEYPHRASE) {
        const struct sound_trigger_phrase_recognition_event *halPhraseEvent =
                reinterpret_cast<const struct sound_trigger_phrase_recognition_event *>(halEvent);
        ISoundTriggerHwCallback::PhraseRecognitionEvent *phraseEvent =
                new ISoundTriggerHwCallback::PhraseRecognitionEvent();

        PhraseRecognitionExtra *phraseExtras =
                new PhraseRecognitionExtra[halPhraseEvent->num_phrases];
        for (unsigned int i = 0; i < halPhraseEvent->num_phrases; i++) {
            convertPhraseRecognitionExtraFromHal(&phraseExtras[i],
                                                 &halPhraseEvent->phrase_extras[i]);
        }
        phraseEvent->phraseExtras.setToExternal(phraseExtras, halPhraseEvent->num_phrases);
        // FIXME: transfer buffer ownership. should have a method for that in hidl_vec
        phraseEvent->phraseExtras.resize(halPhraseEvent->num_phrases);
        delete[] phraseExtras;
        event = reinterpret_cast<ISoundTriggerHwCallback::RecognitionEvent *>(phraseEvent);
    } else {
        event = new ISoundTriggerHwCallback::RecognitionEvent();
    }

    event->status = static_cast<ISoundTriggerHwCallback::RecognitionStatus>(halEvent->status);
    event->type = static_cast<SoundModelType>(halEvent->type);
    // event->model to be remapped by called
    event->captureAvailable = halEvent->capture_available;
    event->captureSession = halEvent->capture_session;
    event->captureDelayMs = halEvent->capture_delay_ms;
    event->capturePreambleMs = halEvent->capture_preamble_ms;
    event->triggerInData = halEvent->trigger_in_data;
    event->audioConfig.sampleRateHz = halEvent->audio_config.sample_rate;
    event->audioConfig.channelMask =
            (audio::common::V2_0::AudioChannelMask)halEvent->audio_config.channel_mask;
    event->audioConfig.format = (audio::common::V2_0::AudioFormat)halEvent->audio_config.format;
    event->data.setToExternal(
            const_cast<uint8_t *>(reinterpret_cast<const uint8_t *>(halEvent)) + halEvent->data_offset,
            halEvent->data_size);

    return event;
}

// static
void SoundTriggerHalImpl::convertPhraseRecognitionExtraFromHal(
        PhraseRecognitionExtra *extra,
        const struct sound_trigger_phrase_recognition_extra *halExtra)
{
    extra->id = halExtra->id;
    extra->recognitionModes = halExtra->recognition_modes;
    extra->confidenceLevel = halExtra->confidence_level;

    ConfidenceLevel *levels =
            new ConfidenceLevel[halExtra->num_levels];
    for (unsigned int i = 0; i < halExtra->num_levels; i++) {
        levels[i].userId = halExtra->levels[i].user_id;
        levels[i].levelPercent = halExtra->levels[i].level;
    }
    extra->levels.setToExternal(levels, halExtra->num_levels);
    // FIXME: transfer buffer ownership. should have a method for that in hidl_vec
    extra->levels.resize(halExtra->num_levels);
    delete[] levels;
}

ISoundTriggerHw *HIDL_FETCH_ISoundTriggerHw(const char* /* name */)
{
    return new SoundTriggerHalImpl();
}
} // namespace implementation
}  // namespace V2_0
}  // namespace soundtrigger
}  // namespace hardware
}  // namespace android



