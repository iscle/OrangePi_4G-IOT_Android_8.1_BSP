/*
 * Copyright (C) 2017 The Android Open Source Project
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

#ifndef ANDROID_IPLAYER_H
#define ANDROID_IPLAYER_H

#include <stdint.h>
#include <sys/types.h>

#include <media/VolumeShaper.h>
#include <utils/RefBase.h>
#include <utils/Errors.h>
#include <binder/IInterface.h>

namespace android {

// ----------------------------------------------------------------------------

class IPlayer : public IInterface
{
public:
    DECLARE_META_INTERFACE(Player);

    virtual void start() = 0;

    virtual void pause() = 0;

    virtual void stop() = 0;

    virtual void setVolume(float vol) = 0;

    virtual void setPan(float pan) = 0;

    virtual void setStartDelayMs(int delayMs) = 0;

    virtual void applyVolumeShaper(
            const sp<VolumeShaper::Configuration>& configuration,
            const sp<VolumeShaper::Operation>& operation) = 0;
};

// ----------------------------------------------------------------------------

class BnPlayer : public BnInterface<IPlayer>
{
public:
    virtual status_t    onTransact( uint32_t code,
                                    const Parcel& data,
                                    Parcel* reply,
                                    uint32_t flags = 0);
};

// ----------------------------------------------------------------------------

}; // namespace android

#endif // ANDROID_IPLAYER_H
