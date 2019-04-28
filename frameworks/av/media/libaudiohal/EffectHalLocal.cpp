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

#define LOG_TAG "EffectHalLocal"
//#define LOG_NDEBUG 0

#include <media/EffectsFactoryApi.h>
#include <utils/Log.h>

#include "EffectHalLocal.h"

namespace android {

EffectHalLocal::EffectHalLocal(effect_handle_t handle)
        : mHandle(handle) {
}

EffectHalLocal::~EffectHalLocal() {
    int status = EffectRelease(mHandle);
    ALOGW_IF(status, "Error releasing effect %p: %s", mHandle, strerror(-status));
    mHandle = 0;
}

status_t EffectHalLocal::setInBuffer(const sp<EffectBufferHalInterface>& buffer) {
    mInBuffer = buffer;
    return OK;
}

status_t EffectHalLocal::setOutBuffer(const sp<EffectBufferHalInterface>& buffer) {
    mOutBuffer = buffer;
    return OK;
}

status_t EffectHalLocal::process() {
    if (mInBuffer == nullptr || mOutBuffer == nullptr) {
        ALOGE_IF(mInBuffer == nullptr, "Input buffer not set");
        ALOGE_IF(mOutBuffer == nullptr, "Output buffer not set");
        return NO_INIT;
    }
    return (*mHandle)->process(mHandle, mInBuffer->audioBuffer(), mOutBuffer->audioBuffer());
}

status_t EffectHalLocal::processReverse() {
    if ((*mHandle)->process_reverse != NULL) {
        if (mInBuffer == nullptr || mOutBuffer == nullptr) {
            ALOGE_IF(mInBuffer == nullptr, "Input buffer not set");
            ALOGE_IF(mOutBuffer == nullptr, "Output buffer not set");
            return NO_INIT;
        }
        return (*mHandle)->process_reverse(
                mHandle, mInBuffer->audioBuffer(), mOutBuffer->audioBuffer());
    } else {
        return INVALID_OPERATION;
    }
}

status_t EffectHalLocal::command(uint32_t cmdCode, uint32_t cmdSize, void *pCmdData,
        uint32_t *replySize, void *pReplyData) {
    return (*mHandle)->command(mHandle, cmdCode, cmdSize, pCmdData, replySize, pReplyData);
}

status_t EffectHalLocal::getDescriptor(effect_descriptor_t *pDescriptor) {
    return (*mHandle)->get_descriptor(mHandle, pDescriptor);
}

status_t EffectHalLocal::close() {
    return OK;
}

} // namespace android
