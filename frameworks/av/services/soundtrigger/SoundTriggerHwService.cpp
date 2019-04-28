/*
 * Copyright (C) 2014 The Android Open Source Project
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

#define LOG_TAG "SoundTriggerHwService"
//#define LOG_NDEBUG 0

#include <stdio.h>
#include <string.h>
#include <sys/types.h>
#include <pthread.h>

#include <system/sound_trigger.h>
#include <cutils/atomic.h>
#include <cutils/properties.h>
#include <hardware/hardware.h>
#include <media/AudioSystem.h>
#include <utils/Errors.h>
#include <utils/Log.h>
#include <binder/IServiceManager.h>
#include <binder/MemoryBase.h>
#include <binder/MemoryHeapBase.h>
#include <system/sound_trigger.h>
#include <ServiceUtilities.h>
#include "SoundTriggerHwService.h"

#ifdef SOUND_TRIGGER_USE_STUB_MODULE
#define HW_MODULE_PREFIX "stub"
#else
#define HW_MODULE_PREFIX "primary"
#endif
namespace android {

SoundTriggerHwService::SoundTriggerHwService()
    : BnSoundTriggerHwService(),
      mNextUniqueId(1),
      mMemoryDealer(new MemoryDealer(1024 * 1024, "SoundTriggerHwService")),
      mCaptureState(false)
{
}

void SoundTriggerHwService::onFirstRef()
{
    int rc;

    sp<SoundTriggerHalInterface> halInterface =
            SoundTriggerHalInterface::connectModule(HW_MODULE_PREFIX);

    if (halInterface == 0) {
        ALOGW("could not connect to HAL");
        return;
    }
    sound_trigger_module_descriptor descriptor;
    rc = halInterface->getProperties(&descriptor.properties);
    if (rc != 0) {
        ALOGE("could not read implementation properties");
        return;
    }
    descriptor.handle =
            (sound_trigger_module_handle_t)android_atomic_inc(&mNextUniqueId);
    ALOGI("loaded default module %s, handle %d", descriptor.properties.description,
                                                 descriptor.handle);

    sp<Module> module = new Module(this, halInterface, descriptor);
    mModules.add(descriptor.handle, module);
    mCallbackThread = new CallbackThread(this);
}

SoundTriggerHwService::~SoundTriggerHwService()
{
    if (mCallbackThread != 0) {
        mCallbackThread->exit();
    }
}

status_t SoundTriggerHwService::listModules(struct sound_trigger_module_descriptor *modules,
                             uint32_t *numModules)
{
    ALOGV("listModules");
    if (!captureHotwordAllowed(IPCThreadState::self()->getCallingPid(),
                               IPCThreadState::self()->getCallingUid())) {
        return PERMISSION_DENIED;
    }

    AutoMutex lock(mServiceLock);
    if (numModules == NULL || (*numModules != 0 && modules == NULL)) {
        return BAD_VALUE;
    }
    size_t maxModules = *numModules;
    *numModules = mModules.size();
    for (size_t i = 0; i < mModules.size() && i < maxModules; i++) {
        modules[i] = mModules.valueAt(i)->descriptor();
    }
    return NO_ERROR;
}

status_t SoundTriggerHwService::attach(const sound_trigger_module_handle_t handle,
                        const sp<ISoundTriggerClient>& client,
                        sp<ISoundTrigger>& moduleInterface)
{
    ALOGV("attach module %d", handle);
    if (!captureHotwordAllowed(IPCThreadState::self()->getCallingPid(),
                               IPCThreadState::self()->getCallingUid())) {
        return PERMISSION_DENIED;
    }

    AutoMutex lock(mServiceLock);
    moduleInterface.clear();
    if (client == 0) {
        return BAD_VALUE;
    }
    ssize_t index = mModules.indexOfKey(handle);
    if (index < 0) {
        return BAD_VALUE;
    }
    sp<Module> module = mModules.valueAt(index);

    sp<ModuleClient> moduleClient = module->addClient(client);
    if (moduleClient == 0) {
        return NO_INIT;
    }

    moduleClient->setCaptureState_l(mCaptureState);
    moduleInterface = moduleClient;

    return NO_ERROR;
}

status_t SoundTriggerHwService::setCaptureState(bool active)
{
    ALOGV("setCaptureState %d", active);
    AutoMutex lock(mServiceLock);
    mCaptureState = active;
    for (size_t i = 0; i < mModules.size(); i++) {
        mModules.valueAt(i)->setCaptureState_l(active);
    }
    return NO_ERROR;
}


static const int kDumpLockRetries = 50;
static const int kDumpLockSleep = 60000;

static bool tryLock(Mutex& mutex)
{
    bool locked = false;
    for (int i = 0; i < kDumpLockRetries; ++i) {
        if (mutex.tryLock() == NO_ERROR) {
            locked = true;
            break;
        }
        usleep(kDumpLockSleep);
    }
    return locked;
}

status_t SoundTriggerHwService::dump(int fd, const Vector<String16>& args __unused) {
    String8 result;
    if (checkCallingPermission(String16("android.permission.DUMP")) == false) {
        result.appendFormat("Permission Denial: can't dump SoundTriggerHwService");
        write(fd, result.string(), result.size());
    } else {
        bool locked = tryLock(mServiceLock);
        // failed to lock - SoundTriggerHwService is probably deadlocked
        if (!locked) {
            result.append("SoundTriggerHwService may be deadlocked\n");
            write(fd, result.string(), result.size());
        }

        if (locked) mServiceLock.unlock();
    }
    return NO_ERROR;
}

status_t SoundTriggerHwService::onTransact(
    uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags) {
    return BnSoundTriggerHwService::onTransact(code, data, reply, flags);
}


// static
void SoundTriggerHwService::recognitionCallback(struct sound_trigger_recognition_event *event,
                                                void *cookie)
{
    Module *module = (Module *)cookie;
    if (module == NULL) {
        return;
    }
    sp<SoundTriggerHwService> service = module->service().promote();
    if (service == 0) {
        return;
    }

    service->sendRecognitionEvent(event, module);
}

sp<IMemory> SoundTriggerHwService::prepareRecognitionEvent_l(
                                                    struct sound_trigger_recognition_event *event)
{
    sp<IMemory> eventMemory;

    //sanitize event
    switch (event->type) {
    case SOUND_MODEL_TYPE_KEYPHRASE:
        ALOGW_IF(event->data_size != 0 && event->data_offset !=
                    sizeof(struct sound_trigger_phrase_recognition_event),
                    "prepareRecognitionEvent_l(): invalid data offset %u for keyphrase event type",
                    event->data_offset);
        event->data_offset = sizeof(struct sound_trigger_phrase_recognition_event);
        break;
    case SOUND_MODEL_TYPE_GENERIC:
        ALOGW_IF(event->data_size != 0 && event->data_offset !=
                    sizeof(struct sound_trigger_generic_recognition_event),
                    "prepareRecognitionEvent_l(): invalid data offset %u for generic event type",
                    event->data_offset);
        event->data_offset = sizeof(struct sound_trigger_generic_recognition_event);
        break;
    case SOUND_MODEL_TYPE_UNKNOWN:
        ALOGW_IF(event->data_size != 0 && event->data_offset !=
                    sizeof(struct sound_trigger_recognition_event),
                    "prepareRecognitionEvent_l(): invalid data offset %u for unknown event type",
                    event->data_offset);
        event->data_offset = sizeof(struct sound_trigger_recognition_event);
        break;
    default:
        return eventMemory;
    }

    size_t size = event->data_offset + event->data_size;
    eventMemory = mMemoryDealer->allocate(size);
    if (eventMemory == 0 || eventMemory->pointer() == NULL) {
        eventMemory.clear();
        return eventMemory;
    }
    memcpy(eventMemory->pointer(), event, size);

    return eventMemory;
}

void SoundTriggerHwService::sendRecognitionEvent(struct sound_trigger_recognition_event *event,
                                                 Module *module)
 {
     AutoMutex lock(mServiceLock);
     if (module == NULL) {
         return;
     }
     sp<IMemory> eventMemory = prepareRecognitionEvent_l(event);
     if (eventMemory == 0) {
         return;
     }
     sp<Module> strongModule;
     for (size_t i = 0; i < mModules.size(); i++) {
         if (mModules.valueAt(i).get() == module) {
             strongModule = mModules.valueAt(i);
             break;
         }
     }
     if (strongModule == 0) {
         return;
     }

    sp<CallbackEvent> callbackEvent = new CallbackEvent(CallbackEvent::TYPE_RECOGNITION,
                                                        eventMemory);
    callbackEvent->setModule(strongModule);
    sendCallbackEvent_l(callbackEvent);
}

// static
void SoundTriggerHwService::soundModelCallback(struct sound_trigger_model_event *event,
                                               void *cookie)
{
    Module *module = (Module *)cookie;
    if (module == NULL) {
        return;
    }
    sp<SoundTriggerHwService> service = module->service().promote();
    if (service == 0) {
        return;
    }

    service->sendSoundModelEvent(event, module);
}

sp<IMemory> SoundTriggerHwService::prepareSoundModelEvent_l(struct sound_trigger_model_event *event)
{
    sp<IMemory> eventMemory;

    size_t size = event->data_offset + event->data_size;
    eventMemory = mMemoryDealer->allocate(size);
    if (eventMemory == 0 || eventMemory->pointer() == NULL) {
        eventMemory.clear();
        return eventMemory;
    }
    memcpy(eventMemory->pointer(), event, size);

    return eventMemory;
}

void SoundTriggerHwService::sendSoundModelEvent(struct sound_trigger_model_event *event,
                                                Module *module)
{
    AutoMutex lock(mServiceLock);
    sp<IMemory> eventMemory = prepareSoundModelEvent_l(event);
    if (eventMemory == 0) {
        return;
    }
    sp<Module> strongModule;
    for (size_t i = 0; i < mModules.size(); i++) {
        if (mModules.valueAt(i).get() == module) {
            strongModule = mModules.valueAt(i);
            break;
        }
    }
    if (strongModule == 0) {
        return;
    }
    sp<CallbackEvent> callbackEvent = new CallbackEvent(CallbackEvent::TYPE_SOUNDMODEL,
                                                        eventMemory);
    callbackEvent->setModule(strongModule);
    sendCallbackEvent_l(callbackEvent);
}


sp<IMemory> SoundTriggerHwService::prepareServiceStateEvent_l(sound_trigger_service_state_t state)
{
    sp<IMemory> eventMemory;

    size_t size = sizeof(sound_trigger_service_state_t);
    eventMemory = mMemoryDealer->allocate(size);
    if (eventMemory == 0 || eventMemory->pointer() == NULL) {
        eventMemory.clear();
        return eventMemory;
    }
    *((sound_trigger_service_state_t *)eventMemory->pointer()) = state;
    return eventMemory;
}

// call with mServiceLock held
void SoundTriggerHwService::sendServiceStateEvent_l(sound_trigger_service_state_t state,
                                                  Module *module)
{
    sp<IMemory> eventMemory = prepareServiceStateEvent_l(state);
    if (eventMemory == 0) {
        return;
    }
    sp<Module> strongModule;
    for (size_t i = 0; i < mModules.size(); i++) {
        if (mModules.valueAt(i).get() == module) {
            strongModule = mModules.valueAt(i);
            break;
        }
    }
    if (strongModule == 0) {
        return;
    }
    sp<CallbackEvent> callbackEvent = new CallbackEvent(CallbackEvent::TYPE_SERVICE_STATE,
                                                        eventMemory);
    callbackEvent->setModule(strongModule);
    sendCallbackEvent_l(callbackEvent);
}

void SoundTriggerHwService::sendServiceStateEvent_l(sound_trigger_service_state_t state,
                                                    ModuleClient *moduleClient)
{
    sp<IMemory> eventMemory = prepareServiceStateEvent_l(state);
    if (eventMemory == 0) {
        return;
    }
    sp<CallbackEvent> callbackEvent = new CallbackEvent(CallbackEvent::TYPE_SERVICE_STATE,
                                                        eventMemory);
    callbackEvent->setModuleClient(moduleClient);
    sendCallbackEvent_l(callbackEvent);
}

// call with mServiceLock held
void SoundTriggerHwService::sendCallbackEvent_l(const sp<CallbackEvent>& event)
{
    mCallbackThread->sendCallbackEvent(event);
}

void SoundTriggerHwService::onCallbackEvent(const sp<CallbackEvent>& event)
{
    ALOGV("onCallbackEvent");
    sp<Module> module;
    sp<ModuleClient> moduleClient;
    {
        AutoMutex lock(mServiceLock);
        //CallbackEvent is either for Module or ModuleClient
        module = event->mModule.promote();
        if (module == 0) {
            moduleClient = event->mModuleClient.promote();
            if (moduleClient == 0) {
                return;
            }
        }
    }
    if (module != 0) {
        ALOGV("onCallbackEvent for module");
        module->onCallbackEvent(event);
    } else if (moduleClient != 0) {
        ALOGV("onCallbackEvent for moduleClient");
        moduleClient->onCallbackEvent(event);
    }
    {
        AutoMutex lock(mServiceLock);
        // clear now to execute with mServiceLock locked
        event->mMemory.clear();
    }
}

#undef LOG_TAG
#define LOG_TAG "SoundTriggerHwService::CallbackThread"

SoundTriggerHwService::CallbackThread::CallbackThread(const wp<SoundTriggerHwService>& service)
    : mService(service)
{
}

SoundTriggerHwService::CallbackThread::~CallbackThread()
{
    while (!mEventQueue.isEmpty()) {
        mEventQueue[0]->mMemory.clear();
        mEventQueue.removeAt(0);
    }
}

void SoundTriggerHwService::CallbackThread::onFirstRef()
{
    run("soundTrigger cbk", ANDROID_PRIORITY_URGENT_AUDIO);
}

bool SoundTriggerHwService::CallbackThread::threadLoop()
{
    while (!exitPending()) {
        sp<CallbackEvent> event;
        sp<SoundTriggerHwService> service;
        {
            Mutex::Autolock _l(mCallbackLock);
            while (mEventQueue.isEmpty() && !exitPending()) {
                ALOGV("CallbackThread::threadLoop() sleep");
                mCallbackCond.wait(mCallbackLock);
                ALOGV("CallbackThread::threadLoop() wake up");
            }
            if (exitPending()) {
                break;
            }
            event = mEventQueue[0];
            mEventQueue.removeAt(0);
            service = mService.promote();
        }
        if (service != 0) {
            service->onCallbackEvent(event);
        }
    }
    return false;
}

void SoundTriggerHwService::CallbackThread::exit()
{
    Mutex::Autolock _l(mCallbackLock);
    requestExit();
    mCallbackCond.broadcast();
}

void SoundTriggerHwService::CallbackThread::sendCallbackEvent(
                        const sp<SoundTriggerHwService::CallbackEvent>& event)
{
    AutoMutex lock(mCallbackLock);
    mEventQueue.add(event);
    mCallbackCond.signal();
}

SoundTriggerHwService::CallbackEvent::CallbackEvent(event_type type, sp<IMemory> memory)
    : mType(type), mMemory(memory)
{
}

SoundTriggerHwService::CallbackEvent::~CallbackEvent()
{
}


#undef LOG_TAG
#define LOG_TAG "SoundTriggerHwService::Module"

SoundTriggerHwService::Module::Module(const sp<SoundTriggerHwService>& service,
                                      const sp<SoundTriggerHalInterface>& halInterface,
                                      sound_trigger_module_descriptor descriptor)
 : mService(service), mHalInterface(halInterface), mDescriptor(descriptor),
   mServiceState(SOUND_TRIGGER_STATE_NO_INIT)
{
}

SoundTriggerHwService::Module::~Module() {
    mModuleClients.clear();
}

sp<SoundTriggerHwService::ModuleClient>
SoundTriggerHwService::Module::addClient(const sp<ISoundTriggerClient>& client)
{
    AutoMutex lock(mLock);
    sp<ModuleClient> moduleClient;

    for (size_t i = 0; i < mModuleClients.size(); i++) {
        if (mModuleClients[i]->client() == client) {
            // Client already present, reuse client
            return moduleClient;
        }
    }
    moduleClient = new ModuleClient(this, client);

    ALOGV("addClient() client %p", moduleClient.get());
    mModuleClients.add(moduleClient);

    return moduleClient;
}

void SoundTriggerHwService::Module::detach(const sp<ModuleClient>& moduleClient)
{
    ALOGV("Module::detach()");
    Vector<audio_session_t> releasedSessions;

    {
        AutoMutex lock(mLock);
        ssize_t index = -1;

        for (size_t i = 0; i < mModuleClients.size(); i++) {
            if (mModuleClients[i] == moduleClient) {
                index = i;
                break;
            }
        }
        if (index == -1) {
            return;
        }

        ALOGV("remove client %p", moduleClient.get());
        mModuleClients.removeAt(index);

        // Iterate in reverse order as models are removed from list inside the loop.
        for (size_t i = mModels.size(); i > 0; i--) {
            sp<Model> model = mModels.valueAt(i - 1);
            if (moduleClient == model->mModuleClient) {
                mModels.removeItemsAt(i - 1);
                ALOGV("detach() unloading model %d", model->mHandle);
                if (mHalInterface != 0) {
                    if (model->mState == Model::STATE_ACTIVE) {
                        mHalInterface->stopRecognition(model->mHandle);
                    }
                    mHalInterface->unloadSoundModel(model->mHandle);
                }
                releasedSessions.add(model->mCaptureSession);
            }
        }
    }

    for (size_t i = 0; i < releasedSessions.size(); i++) {
        // do not call AudioSystem methods with mLock held
        AudioSystem::releaseSoundTriggerSession(releasedSessions[i]);
    }
}

status_t SoundTriggerHwService::Module::loadSoundModel(const sp<IMemory>& modelMemory,
                                                       sp<ModuleClient> moduleClient,
                                                       sound_model_handle_t *handle)
{
    ALOGV("loadSoundModel() handle");
    if (mHalInterface == 0) {
        return NO_INIT;
    }
    if (modelMemory == 0 || modelMemory->pointer() == NULL) {
        ALOGE("loadSoundModel() modelMemory is 0 or has NULL pointer()");
        return BAD_VALUE;
    }
    struct sound_trigger_sound_model *sound_model =
            (struct sound_trigger_sound_model *)modelMemory->pointer();

    size_t structSize;
    if (sound_model->type == SOUND_MODEL_TYPE_KEYPHRASE) {
        structSize = sizeof(struct sound_trigger_phrase_sound_model);
    } else {
        structSize = sizeof(struct sound_trigger_sound_model);
    }

    if (sound_model->data_offset < structSize ||
           sound_model->data_size > (UINT_MAX - sound_model->data_offset) ||
           modelMemory->size() < sound_model->data_offset ||
           sound_model->data_size > (modelMemory->size() - sound_model->data_offset)) {
        android_errorWriteLog(0x534e4554, "30148546");
        ALOGE("loadSoundModel() data_size is too big");
        return BAD_VALUE;
    }

    audio_session_t session;
    audio_io_handle_t ioHandle;
    audio_devices_t device;
    // do not call AudioSystem methods with mLock held
    status_t status = AudioSystem::acquireSoundTriggerSession(&session, &ioHandle, &device);
    if (status != NO_ERROR) {
        return status;
    }

    {
        AutoMutex lock(mLock);

        if (mModels.size() >= mDescriptor.properties.max_sound_models) {
            ALOGW("loadSoundModel(): Not loading, max number of models (%d) would be exceeded",
                  mDescriptor.properties.max_sound_models);
            status = INVALID_OPERATION;
            goto exit;
        }

        status = mHalInterface->loadSoundModel(sound_model,
                                                      SoundTriggerHwService::soundModelCallback,
                                                      this, handle);
        if (status != NO_ERROR) {
            goto exit;
        }

        sp<Model> model = new Model(*handle, session, ioHandle, device, sound_model->type,
                                    moduleClient);
        mModels.replaceValueFor(*handle, model);
    }
exit:
    if (status != NO_ERROR) {
        // do not call AudioSystem methods with mLock held
        AudioSystem::releaseSoundTriggerSession(session);
    }
    return status;
}

status_t SoundTriggerHwService::Module::unloadSoundModel(sound_model_handle_t handle)
{
    ALOGV("unloadSoundModel() model handle %d", handle);
    status_t status;
    audio_session_t session;

    {
        AutoMutex lock(mLock);
        if (mHalInterface == 0) {
            return NO_INIT;
        }
        ssize_t index = mModels.indexOfKey(handle);
        if (index < 0) {
            return BAD_VALUE;
        }
        sp<Model> model = mModels.valueAt(index);
        mModels.removeItem(handle);
        if (model->mState == Model::STATE_ACTIVE) {
            mHalInterface->stopRecognition(model->mHandle);
            model->mState = Model::STATE_IDLE;
        }
        status = mHalInterface->unloadSoundModel(handle);
        session = model->mCaptureSession;
    }
    // do not call AudioSystem methods with mLock held
    AudioSystem::releaseSoundTriggerSession(session);
    return status;
}

status_t SoundTriggerHwService::Module::startRecognition(sound_model_handle_t handle,
                                 const sp<IMemory>& dataMemory)
{
    ALOGV("startRecognition() model handle %d", handle);
    if (mHalInterface == 0) {
        return NO_INIT;
    }
    if (dataMemory == 0 || dataMemory->pointer() == NULL) {
        ALOGE("startRecognition() dataMemory is 0 or has NULL pointer()");
        return BAD_VALUE;

    }

    struct sound_trigger_recognition_config *config =
            (struct sound_trigger_recognition_config *)dataMemory->pointer();

    if (config->data_offset < sizeof(struct sound_trigger_recognition_config) ||
            config->data_size > (UINT_MAX - config->data_offset) ||
            dataMemory->size() < config->data_offset ||
            config->data_size > (dataMemory->size() - config->data_offset)) {
        ALOGE("startRecognition() data_size is too big");
        return BAD_VALUE;
    }

    AutoMutex lock(mLock);
    if (mServiceState == SOUND_TRIGGER_STATE_DISABLED) {
        return INVALID_OPERATION;
    }
    sp<Model> model = getModel(handle);
    if (model == 0) {
        return BAD_VALUE;
    }

    if (model->mState == Model::STATE_ACTIVE) {
        return INVALID_OPERATION;
    }


    //TODO: get capture handle and device from audio policy service
    config->capture_handle = model->mCaptureIOHandle;
    config->capture_device = model->mCaptureDevice;
    status_t status = mHalInterface->startRecognition(handle, config,
                                        SoundTriggerHwService::recognitionCallback,
                                        this);

    if (status == NO_ERROR) {
        model->mState = Model::STATE_ACTIVE;
        model->mConfig = *config;
    }

    return status;
}

status_t SoundTriggerHwService::Module::stopRecognition(sound_model_handle_t handle)
{
    ALOGV("stopRecognition() model handle %d", handle);
    if (mHalInterface == 0) {
        return NO_INIT;
    }
    AutoMutex lock(mLock);
    sp<Model> model = getModel(handle);
    if (model == 0) {
        return BAD_VALUE;
    }

    if (model->mState != Model::STATE_ACTIVE) {
        return INVALID_OPERATION;
    }
    mHalInterface->stopRecognition(handle);
    model->mState = Model::STATE_IDLE;
    return NO_ERROR;
}

void SoundTriggerHwService::Module::onCallbackEvent(const sp<CallbackEvent>& event)
{
    ALOGV("onCallbackEvent type %d", event->mType);

    sp<IMemory> eventMemory = event->mMemory;

    if (eventMemory == 0 || eventMemory->pointer() == NULL) {
        return;
    }
    if (mModuleClients.isEmpty()) {
        ALOGI("%s no clients", __func__);
        return;
    }

    switch (event->mType) {
    case CallbackEvent::TYPE_RECOGNITION: {
        struct sound_trigger_recognition_event *recognitionEvent =
                (struct sound_trigger_recognition_event *)eventMemory->pointer();
        sp<ISoundTriggerClient> client;
        {
            AutoMutex lock(mLock);
            sp<Model> model = getModel(recognitionEvent->model);
            if (model == 0) {
                ALOGW("%s model == 0", __func__);
                return;
            }
            if (model->mState != Model::STATE_ACTIVE) {
                ALOGV("onCallbackEvent model->mState %d != Model::STATE_ACTIVE", model->mState);
                return;
            }

            recognitionEvent->capture_session = model->mCaptureSession;
            model->mState = Model::STATE_IDLE;
            client = model->mModuleClient->client();
        }
        if (client != 0) {
            client->onRecognitionEvent(eventMemory);
        }
    } break;
    case CallbackEvent::TYPE_SOUNDMODEL: {
        struct sound_trigger_model_event *soundmodelEvent =
                (struct sound_trigger_model_event *)eventMemory->pointer();
        sp<ISoundTriggerClient> client;
        {
            AutoMutex lock(mLock);
            sp<Model> model = getModel(soundmodelEvent->model);
            if (model == 0) {
                ALOGW("%s model == 0", __func__);
                return;
            }
            client = model->mModuleClient->client();
        }
        if (client != 0) {
            client->onSoundModelEvent(eventMemory);
        }
    } break;
    case CallbackEvent::TYPE_SERVICE_STATE: {
        Vector< sp<ISoundTriggerClient> > clients;
        {
            AutoMutex lock(mLock);
            for (size_t i = 0; i < mModuleClients.size(); i++) {
                if (mModuleClients[i] != 0) {
                    clients.add(mModuleClients[i]->client());
                }
            }
        }
        for (size_t i = 0; i < clients.size(); i++) {
            clients[i]->onServiceStateChange(eventMemory);
        }
    } break;
    default:
        LOG_ALWAYS_FATAL("onCallbackEvent unknown event type %d", event->mType);
    }
}

sp<SoundTriggerHwService::Model> SoundTriggerHwService::Module::getModel(
        sound_model_handle_t handle)
{
    sp<Model> model;
    ssize_t index = mModels.indexOfKey(handle);
    if (index >= 0) {
        model = mModels.valueAt(index);
    }
    return model;
}

// Called with mServiceLock held
void SoundTriggerHwService::Module::setCaptureState_l(bool active)
{
    ALOGV("Module::setCaptureState_l %d", active);
    sp<SoundTriggerHwService> service;
    sound_trigger_service_state_t state;

    Vector< sp<IMemory> > events;
    {
        AutoMutex lock(mLock);
        state = (active && !mDescriptor.properties.concurrent_capture) ?
                                        SOUND_TRIGGER_STATE_DISABLED : SOUND_TRIGGER_STATE_ENABLED;

        if (state == mServiceState) {
            return;
        }

        mServiceState = state;

        service = mService.promote();
        if (service == 0) {
            return;
        }

        if (state == SOUND_TRIGGER_STATE_ENABLED) {
            goto exit;
        }

        const bool supports_stop_all =
                (mHalInterface != 0) && (mHalInterface->stopAllRecognitions() != -ENOSYS);

        for (size_t i = 0; i < mModels.size(); i++) {
            sp<Model> model = mModels.valueAt(i);
            if (model->mState == Model::STATE_ACTIVE) {
                if (mHalInterface != 0 && !supports_stop_all) {
                    mHalInterface->stopRecognition(model->mHandle);
                }
                // keep model in ACTIVE state so that event is processed by onCallbackEvent()
                if (model->mType == SOUND_MODEL_TYPE_KEYPHRASE) {
                    struct sound_trigger_phrase_recognition_event event;
                    memset(&event, 0, sizeof(struct sound_trigger_phrase_recognition_event));
                    event.num_phrases = model->mConfig.num_phrases;
                    for (size_t i = 0; i < event.num_phrases; i++) {
                        event.phrase_extras[i] = model->mConfig.phrases[i];
                    }
                    event.common.status = RECOGNITION_STATUS_ABORT;
                    event.common.type = model->mType;
                    event.common.model = model->mHandle;
                    event.common.data_size = 0;
                    sp<IMemory> eventMemory = service->prepareRecognitionEvent_l(&event.common);
                    if (eventMemory != 0) {
                        events.add(eventMemory);
                    }
                } else if (model->mType == SOUND_MODEL_TYPE_GENERIC) {
                    struct sound_trigger_generic_recognition_event event;
                    memset(&event, 0, sizeof(struct sound_trigger_generic_recognition_event));
                    event.common.status = RECOGNITION_STATUS_ABORT;
                    event.common.type = model->mType;
                    event.common.model = model->mHandle;
                    event.common.data_size = 0;
                    sp<IMemory> eventMemory = service->prepareRecognitionEvent_l(&event.common);
                    if (eventMemory != 0) {
                        events.add(eventMemory);
                    }
                } else if (model->mType == SOUND_MODEL_TYPE_UNKNOWN) {
                    struct sound_trigger_phrase_recognition_event event;
                    memset(&event, 0, sizeof(struct sound_trigger_phrase_recognition_event));
                    event.common.status = RECOGNITION_STATUS_ABORT;
                    event.common.type = model->mType;
                    event.common.model = model->mHandle;
                    event.common.data_size = 0;
                    sp<IMemory> eventMemory = service->prepareRecognitionEvent_l(&event.common);
                    if (eventMemory != 0) {
                        events.add(eventMemory);
                    }
                } else {
                    goto exit;
                }
            }
        }
    }

    for (size_t i = 0; i < events.size(); i++) {
        sp<CallbackEvent> callbackEvent = new CallbackEvent(CallbackEvent::TYPE_RECOGNITION,
                                                            events[i]);
        callbackEvent->setModule(this);
        service->sendCallbackEvent_l(callbackEvent);
    }

exit:
    service->sendServiceStateEvent_l(state, this);
}


SoundTriggerHwService::Model::Model(sound_model_handle_t handle, audio_session_t session,
                                    audio_io_handle_t ioHandle, audio_devices_t device,
                                    sound_trigger_sound_model_type_t type,
                                    sp<ModuleClient>& moduleClient) :
    mHandle(handle), mState(STATE_IDLE), mCaptureSession(session),
    mCaptureIOHandle(ioHandle), mCaptureDevice(device), mType(type),
    mModuleClient(moduleClient)
{
}

#undef LOG_TAG
#define LOG_TAG "SoundTriggerHwService::ModuleClient"

SoundTriggerHwService::ModuleClient::ModuleClient(const sp<Module>& module,
                                                  const sp<ISoundTriggerClient>& client)
 : mModule(module), mClient(client)
{
}

void SoundTriggerHwService::ModuleClient::onFirstRef()
{
    sp<IBinder> binder = IInterface::asBinder(mClient);
    if (binder != 0) {
        binder->linkToDeath(this);
    }
}

SoundTriggerHwService::ModuleClient::~ModuleClient()
{
}

status_t SoundTriggerHwService::ModuleClient::dump(int fd __unused,
                                                   const Vector<String16>& args __unused) {
    String8 result;
    return NO_ERROR;
}

void SoundTriggerHwService::ModuleClient::detach() {
    ALOGV("detach()");
    if (!captureHotwordAllowed(IPCThreadState::self()->getCallingPid(),
                               IPCThreadState::self()->getCallingUid())) {
        return;
    }

    {
        AutoMutex lock(mLock);
        if (mClient != 0) {
            IInterface::asBinder(mClient)->unlinkToDeath(this);
            mClient.clear();
        }
    }

    sp<Module> module = mModule.promote();
    if (module == 0) {
        return;
    }
    module->detach(this);
}

status_t SoundTriggerHwService::ModuleClient::loadSoundModel(const sp<IMemory>& modelMemory,
                                sound_model_handle_t *handle)
{
    ALOGV("loadSoundModel() handle");
    if (!captureHotwordAllowed(IPCThreadState::self()->getCallingPid(),
                               IPCThreadState::self()->getCallingUid())) {
        return PERMISSION_DENIED;
    }

    sp<Module> module = mModule.promote();
    if (module == 0) {
        return NO_INIT;
    }
    return module->loadSoundModel(modelMemory, this, handle);
}

status_t SoundTriggerHwService::ModuleClient::unloadSoundModel(sound_model_handle_t handle)
{
    ALOGV("unloadSoundModel() model handle %d", handle);
    if (!captureHotwordAllowed(IPCThreadState::self()->getCallingPid(),
                               IPCThreadState::self()->getCallingUid())) {
        return PERMISSION_DENIED;
    }

    sp<Module> module = mModule.promote();
    if (module == 0) {
        return NO_INIT;
    }
    return module->unloadSoundModel(handle);
}

status_t SoundTriggerHwService::ModuleClient::startRecognition(sound_model_handle_t handle,
                                 const sp<IMemory>& dataMemory)
{
    ALOGV("startRecognition() model handle %d", handle);
    if (!captureHotwordAllowed(IPCThreadState::self()->getCallingPid(),
                               IPCThreadState::self()->getCallingUid())) {
        return PERMISSION_DENIED;
    }

    sp<Module> module = mModule.promote();
    if (module == 0) {
        return NO_INIT;
    }
    return module->startRecognition(handle, dataMemory);
}

status_t SoundTriggerHwService::ModuleClient::stopRecognition(sound_model_handle_t handle)
{
    ALOGV("stopRecognition() model handle %d", handle);
    if (!captureHotwordAllowed(IPCThreadState::self()->getCallingPid(),
                               IPCThreadState::self()->getCallingUid())) {
        return PERMISSION_DENIED;
    }

    sp<Module> module = mModule.promote();
    if (module == 0) {
        return NO_INIT;
    }
    return module->stopRecognition(handle);
}

void SoundTriggerHwService::ModuleClient::setCaptureState_l(bool active)
{
    ALOGV("ModuleClient::setCaptureState_l %d", active);
    sp<SoundTriggerHwService> service;
    sound_trigger_service_state_t state;

    sp<Module> module = mModule.promote();
    if (module == 0) {
        return;
    }
    {
        AutoMutex lock(mLock);
        state = (active && !module->isConcurrentCaptureAllowed()) ?
                                        SOUND_TRIGGER_STATE_DISABLED : SOUND_TRIGGER_STATE_ENABLED;

        service = module->service().promote();
        if (service == 0) {
            return;
        }
    }
    service->sendServiceStateEvent_l(state, this);
}

void SoundTriggerHwService::ModuleClient::onCallbackEvent(const sp<CallbackEvent>& event)
{
    ALOGV("ModuleClient onCallbackEvent type %d", event->mType);

    sp<IMemory> eventMemory = event->mMemory;

    if (eventMemory == 0 || eventMemory->pointer() == NULL) {
        return;
    }

    switch (event->mType) {
    case CallbackEvent::TYPE_SERVICE_STATE: {
        sp<ISoundTriggerClient> client;
        {
            AutoMutex lock(mLock);
            client = mClient;
        }
        if (client !=0 ) {
            client->onServiceStateChange(eventMemory);
        }
    } break;
    default:
        LOG_ALWAYS_FATAL("onCallbackEvent unknown event type %d", event->mType);
    }
}

void SoundTriggerHwService::ModuleClient::binderDied(
    const wp<IBinder> &who __unused) {
    ALOGW("client binder died for client %p", this);
    detach();
}

}; // namespace android
