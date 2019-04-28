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

#include <media/EffectsFactoryApi.h>

#include "EffectHalLocal.h"
#include "EffectsFactoryHalLocal.h"

namespace android {

// static
sp<EffectsFactoryHalInterface> EffectsFactoryHalInterface::create() {
    return new EffectsFactoryHalLocal();
}

// static
bool EffectsFactoryHalInterface::isNullUuid(const effect_uuid_t *pEffectUuid) {
    return EffectIsNullUuid(pEffectUuid);
}

status_t EffectsFactoryHalLocal::queryNumberEffects(uint32_t *pNumEffects) {
    return EffectQueryNumberEffects(pNumEffects);
}

status_t EffectsFactoryHalLocal::getDescriptor(
        uint32_t index, effect_descriptor_t *pDescriptor) {
    return EffectQueryEffect(index, pDescriptor);
}

status_t EffectsFactoryHalLocal::getDescriptor(
        const effect_uuid_t *pEffectUuid, effect_descriptor_t *pDescriptor) {
    return EffectGetDescriptor(pEffectUuid, pDescriptor);
}

status_t EffectsFactoryHalLocal::createEffect(
        const effect_uuid_t *pEffectUuid, int32_t sessionId, int32_t ioId,
        sp<EffectHalInterface> *effect) {
    effect_handle_t handle;
    int result = EffectCreate(pEffectUuid, sessionId, ioId, &handle);
    if (result == 0) {
        *effect = new EffectHalLocal(handle);
    }
    return result;
}

status_t EffectsFactoryHalLocal::dumpEffects(int fd) {
    return EffectDumpEffects(fd);
}

} // namespace android
