/*
**
** Copyright 2017, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

#define LOG_TAG "IPlayer"
//#define LOG_NDEBUG 0
#include <utils/Log.h>

#include <stdint.h>
#include <sys/types.h>

#include <binder/Parcel.h>

#include <audiomanager/IPlayer.h>

namespace android {

enum {
    START      = IBinder::FIRST_CALL_TRANSACTION,
    PAUSE      = IBinder::FIRST_CALL_TRANSACTION + 1,
    STOP       = IBinder::FIRST_CALL_TRANSACTION + 2,
    SET_VOLUME = IBinder::FIRST_CALL_TRANSACTION + 3,
    SET_PAN    = IBinder::FIRST_CALL_TRANSACTION + 4,
    SET_START_DELAY_MS = IBinder::FIRST_CALL_TRANSACTION + 5,
    APPLY_VOLUME_SHAPER = IBinder::FIRST_CALL_TRANSACTION + 6,
};

class BpPlayer : public BpInterface<IPlayer>
{
public:
    explicit BpPlayer(const sp<IBinder>& impl)
        : BpInterface<IPlayer>(impl)
    {
    }

    virtual void start()
    {
        Parcel data, reply;
        data.writeInterfaceToken(IPlayer::getInterfaceDescriptor());
        remote()->transact(START, data, &reply);
    }

    virtual void pause()
    {
        Parcel data, reply;
        data.writeInterfaceToken(IPlayer::getInterfaceDescriptor());
        remote()->transact(PAUSE, data, &reply);
    }

    virtual void stop()
    {
        Parcel data, reply;
        data.writeInterfaceToken(IPlayer::getInterfaceDescriptor());
        remote()->transact(STOP, data, &reply);
    }

    virtual void setVolume(float vol)
    {
        Parcel data, reply;
        data.writeInterfaceToken(IPlayer::getInterfaceDescriptor());
        data.writeFloat(vol);
        remote()->transact(SET_VOLUME, data, &reply);
    }

    virtual void setPan(float pan)
    {
        Parcel data, reply;
        data.writeInterfaceToken(IPlayer::getInterfaceDescriptor());
        data.writeFloat(pan);
        remote()->transact(SET_PAN, data, &reply);
    }

    virtual void setStartDelayMs(int32_t delayMs) {
        Parcel data, reply;
        data.writeInterfaceToken(IPlayer::getInterfaceDescriptor());
        data.writeInt32(delayMs);
        remote()->transact(SET_START_DELAY_MS, data, &reply);
    }

    virtual void applyVolumeShaper(
            const sp<VolumeShaper::Configuration>& configuration,
            const sp<VolumeShaper::Operation>& operation) {
        Parcel data, reply;
        data.writeInterfaceToken(IPlayer::getInterfaceDescriptor());

        status_t status = configuration.get() == nullptr
                ? data.writeInt32(0)
                :  data.writeInt32(1)
                    ?: configuration->writeToParcel(&data);
        if (status != NO_ERROR) {
            ALOGW("applyVolumeShaper failed configuration parceling: %d", status);
            return; // ignore error
        }

        status = operation.get() == nullptr
                ? status = data.writeInt32(0)
                : data.writeInt32(1)
                    ?: operation->writeToParcel(&data);
        if (status != NO_ERROR) {
            ALOGW("applyVolumeShaper failed operation parceling: %d", status);
            return; // ignore error
        }

        status = remote()->transact(APPLY_VOLUME_SHAPER, data, &reply);

        ALOGW_IF(status != NO_ERROR, "applyVolumeShaper failed transact: %d", status);
        return; // one way transaction, ignore error
    }
};

IMPLEMENT_META_INTERFACE(Player, "android.media.IPlayer");

// ----------------------------------------------------------------------

status_t BnPlayer::onTransact(
    uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags)
{
    switch (code) {
        case START: {
            CHECK_INTERFACE(IPlayer, data, reply);
            start();
            return NO_ERROR;
        } break;
        case PAUSE: {
            CHECK_INTERFACE(IPlayer, data, reply);
            pause();
            return NO_ERROR;
        }
        case STOP: {
            CHECK_INTERFACE(IPlayer, data, reply);
            stop();
            return NO_ERROR;
        } break;
        case SET_VOLUME: {
            CHECK_INTERFACE(IPlayer, data, reply);
            setVolume(data.readFloat());
            return NO_ERROR;
        } break;
        case SET_PAN: {
            CHECK_INTERFACE(IPlayer, data, reply);
            setPan(data.readFloat());
            return NO_ERROR;
        } break;
        case SET_START_DELAY_MS: {
            CHECK_INTERFACE(IPlayer, data, reply);
            setStartDelayMs(data.readInt32());
            return NO_ERROR;
        } break;
        case APPLY_VOLUME_SHAPER: {
            CHECK_INTERFACE(IPlayer, data, reply);
            sp<VolumeShaper::Configuration> configuration;
            sp<VolumeShaper::Operation> operation;

            int32_t present;
            status_t status = data.readInt32(&present);
            if (status == NO_ERROR && present != 0) {
                configuration = new VolumeShaper::Configuration();
                status = configuration->readFromParcel(data);
            }
            status = status ?: data.readInt32(&present);
            if (status == NO_ERROR && present != 0) {
                operation = new VolumeShaper::Operation();
                status = operation->readFromParcel(data);
            }
            if (status == NO_ERROR) {
                // one way transaction, no error returned
                applyVolumeShaper(configuration, operation);
            }
            return NO_ERROR;
        } break;
        default:
            return BBinder::onTransact(code, data, reply, flags);
    }
}

} // namespace android
