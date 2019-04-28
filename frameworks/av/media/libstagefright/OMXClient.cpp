/*
 * Copyright (C) 2009 The Android Open Source Project
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

//#define LOG_NDEBUG 0
#define LOG_TAG "OMXClient"

#ifdef __LP64__
#define OMX_ANDROID_COMPILE_AS_32BIT_ON_64BIT_PLATFORMS
#endif

#include <utils/Log.h>
#include <cutils/properties.h>

#include <binder/IServiceManager.h>
#include <media/IMediaCodecService.h>
#include <media/stagefright/OMXClient.h>

#include <media/IOMX.h>

#include <media/omx/1.0/WOmx.h>

namespace android {

OMXClient::OMXClient() {
}

status_t OMXClient::connect() {
    return connect("default", nullptr);
}

status_t OMXClient::connect(bool* trebleFlag) {
    if (property_get_bool("persist.media.treble_omx", true)) {
        if (trebleFlag != nullptr) {
            *trebleFlag = true;
        }
        return connectTreble();
    }
    if (trebleFlag != nullptr) {
        *trebleFlag = false;
    }
    return connectLegacy();
}

status_t OMXClient::connect(const char* name, bool* trebleFlag) {
    if (property_get_bool("persist.media.treble_omx", true)) {
        if (trebleFlag != nullptr) {
            *trebleFlag = true;
        }
        return connectTreble(name);
    }
    if (trebleFlag != nullptr) {
        *trebleFlag = false;
    }
    return connectLegacy();
}

status_t OMXClient::connectLegacy() {
    sp<IServiceManager> sm = defaultServiceManager();
    sp<IBinder> codecbinder = sm->getService(String16("media.codec"));
    sp<IMediaCodecService> codecservice = interface_cast<IMediaCodecService>(codecbinder);

    if (codecservice.get() == NULL) {
        ALOGE("Cannot obtain IMediaCodecService");
        return NO_INIT;
    }

    mOMX = codecservice->getOMX();
    if (mOMX.get() == NULL) {
        ALOGE("Cannot obtain mediacodec IOMX");
        return NO_INIT;
    }

    return OK;
}

status_t OMXClient::connectTreble(const char* name) {
    using namespace ::android::hardware::media::omx::V1_0;
    if (name == nullptr) {
        name = "default";
    }
    sp<IOmx> tOmx = IOmx::getService(name);
    if (tOmx.get() == nullptr) {
        ALOGE("Cannot obtain Treble IOmx.");
        return NO_INIT;
    }
    if (!tOmx->isRemote()) {
        ALOGE("Treble IOmx is in passthrough mode.");
        return NO_INIT;
    }
    mOMX = new utils::LWOmx(tOmx);
    ALOGI("Treble IOmx obtained");
    return OK;
}

void OMXClient::disconnect() {
    mOMX.clear();
}

}  // namespace android
