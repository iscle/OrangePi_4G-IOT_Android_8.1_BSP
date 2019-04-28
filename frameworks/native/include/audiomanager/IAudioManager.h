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

#ifndef ANDROID_IAUDIOMANAGER_H
#define ANDROID_IAUDIOMANAGER_H

#include <utils/Errors.h>
#include <binder/IInterface.h>
#include <hardware/power.h>
#include <system/audio.h>

namespace android {

// ----------------------------------------------------------------------------

class IAudioManager : public IInterface
{
public:
    // These transaction IDs must be kept in sync with the method order from
    // IAudioService.aidl.
    enum {
        // transaction IDs for the unsupported methods are commented out
        /*
        ADJUSTSUGGESTEDSTREAMVOLUME           = IBinder::FIRST_CALL_TRANSACTION,
        ADJUSTSTREAMVOLUME                    = IBinder::FIRST_CALL_TRANSACTION + 1,
        SETSTREAMVOLUME                       = IBinder::FIRST_CALL_TRANSACTION + 2,
        ISSTREAMMUTE                          = IBinder::FIRST_CALL_TRANSACTION + 3,
        FORCEREMOTESUBMIXFULLVOLUME           = IBinder::FIRST_CALL_TRANSACTION + 4,
        ISMASTERMUTE                          = IBinder::FIRST_CALL_TRANSACTION + 5,
        SETMASTERMUTE                         = IBinder::FIRST_CALL_TRANSACTION + 6,
        GETSTREAMVOLUME                       = IBinder::FIRST_CALL_TRANSACTION + 7,
        GETSTREAMMINVOLUME                    = IBinder::FIRST_CALL_TRANSACTION + 8,
        GETSTREAMMAXVOLUME                    = IBinder::FIRST_CALL_TRANSACTION + 9,
        GETLASTAUDIBLESTREAMVOLUME            = IBinder::FIRST_CALL_TRANSACTION + 10,
        SETMICROPHONEMUTE                     = IBinder::FIRST_CALL_TRANSACTION + 11,
        SETRINGERMODEEXTERNAL                 = IBinder::FIRST_CALL_TRANSACTION + 12,
        SETRINGERMODEINTERNAL                 = IBinder::FIRST_CALL_TRANSACTION + 13,
        GETRINGERMODEEXTERNAL                 = IBinder::FIRST_CALL_TRANSACTION + 14,
        GETRINGERMODEINTERNAL                 = IBinder::FIRST_CALL_TRANSACTION + 15,
        ISVALIDRINGERMODE                     = IBinder::FIRST_CALL_TRANSACTION + 16,
        SETVIBRATESETTING                     = IBinder::FIRST_CALL_TRANSACTION + 17,
        GETVIBRATESETTING                     = IBinder::FIRST_CALL_TRANSACTION + 18,
        SHOULDVIBRATE                         = IBinder::FIRST_CALL_TRANSACTION + 19,
        SETMODE                               = IBinder::FIRST_CALL_TRANSACTION + 20,
        GETMODE                               = IBinder::FIRST_CALL_TRANSACTION + 21,
        PLAYSOUNDEFFECT                       = IBinder::FIRST_CALL_TRANSACTION + 22,
        PLAYSOUNDEFFECTVOLUME                 = IBinder::FIRST_CALL_TRANSACTION + 23,
        LOADSOUNDEFFECTS                      = IBinder::FIRST_CALL_TRANSACTION + 24,
        UNLOADSOUNDEFFECTS                    = IBinder::FIRST_CALL_TRANSACTION + 25,
        RELOADAUDIOSETTINGS                   = IBinder::FIRST_CALL_TRANSACTION + 26,
        AVRCPSUPPORTSABSOLUTEVOLUME           = IBinder::FIRST_CALL_TRANSACTION + 27,
        SETSPEAKERPHONEON                     = IBinder::FIRST_CALL_TRANSACTION + 28,
        ISSPEAKERPHONEON                      = IBinder::FIRST_CALL_TRANSACTION + 29,
        SETBLUETOOTHSCOON                     = IBinder::FIRST_CALL_TRANSACTION + 30,
        ISBLUETOOTHSCOON                      = IBinder::FIRST_CALL_TRANSACTION + 31,
        SETBLUETOOTHA2DPON                    = IBinder::FIRST_CALL_TRANSACTION + 32,
        ISBLUETOOTHA2DPON                     = IBinder::FIRST_CALL_TRANSACTION + 33,
        REQUESTAUDIOFOCUS                     = IBinder::FIRST_CALL_TRANSACTION + 34,
        ABANDONAUDIOFOCUS                     = IBinder::FIRST_CALL_TRANSACTION + 35,
        UNREGISTERAUDIOFOCUSCLIENT            = IBinder::FIRST_CALL_TRANSACTION + 36,
        GETCURRENTAUDIOFOCUS                  = IBinder::FIRST_CALL_TRANSACTION + 37,
        STARTBLUETOOTHSCO                     = IBinder::FIRST_CALL_TRANSACTION + 38,
        STARTBLUETOOTHSCOVIRTUALCALL          = IBinder::FIRST_CALL_TRANSACTION + 39,
        STOPBLUETOOTHSCO                      = IBinder::FIRST_CALL_TRANSACTION + 40,
        FORCEVOLUMECONTROLSTREAM              = IBinder::FIRST_CALL_TRANSACTION + 41,
        SETRINGTONEPLAYER                     = IBinder::FIRST_CALL_TRANSACTION + 42,
        GETRINGTONEPLAYER                     = IBinder::FIRST_CALL_TRANSACTION + 43,
        GETUISOUNDSSTREAMTYPE                 = IBinder::FIRST_CALL_TRANSACTION + 44,
        SETWIREDDEVICECONNECTIONSTATE         = IBinder::FIRST_CALL_TRANSACTION + 45,
        SETBLUETOOTHA2DPDEVICECONNECTIONSTATE = IBinder::FIRST_CALL_TRANSACTION + 46,
        HANDLEBLUETOOTHA2DPDEVICECONFIGCHANGE = IBinder::FIRST_CALL_TRANSACTION + 47,
        STARTWATCHINGROUTES                   = IBinder::FIRST_CALL_TRANSACTION + 48,
        ISCAMERASOUNDFORCED                   = IBinder::FIRST_CALL_TRANSACTION + 49,
        SETVOLUMECONTROLLER                   = IBinder::FIRST_CALL_TRANSACTION + 50,
        NOTIFYVOLUMECONTROLLERVISIBLE         = IBinder::FIRST_CALL_TRANSACTION + 51,
        ISSTREAMAFFECTEDBYRINGERMODE          = IBinder::FIRST_CALL_TRANSACTION + 52,
        ISSTREAMAFFECTEDBYMUTE                = IBinder::FIRST_CALL_TRANSACTION + 53,
        DISABLESAFEMEDIAVOLUME                = IBinder::FIRST_CALL_TRANSACTION + 54,
        SETHDMISYSTEMAUDIOSUPPORTED           = IBinder::FIRST_CALL_TRANSACTION + 55,
        ISHDMISYSTEMAUDIOSUPPORTED            = IBinder::FIRST_CALL_TRANSACTION + 56,
        REGISTERAUDIOPOLICY                   = IBinder::FIRST_CALL_TRANSACTION + 57,
        UNREGISTERAUDIOPOLICYASYNC            = IBinder::FIRST_CALL_TRANSACTION + 58,
        SETFOCUSPROPERTIESFORPOLICY           = IBinder::FIRST_CALL_TRANSACTION + 59,
        SETVOLUMEPOLICY                       = IBinder::FIRST_CALL_TRANSACTION + 60,
        REGISTERRECORDINGCALLBACK             = IBinder::FIRST_CALL_TRANSACTION + 61,
        UNREGISTERRECORDINGCALLBACK           = IBinder::FIRST_CALL_TRANSACTION + 62,
        GETACTIVERECORDINGCONFIGURATIONS      = IBinder::FIRST_CALL_TRANSACTION + 63,
        REGISTERPLAYBACKCALLBACK              = IBinder::FIRST_CALL_TRANSACTION + 64,
        UNREGISTERPLAYBACKCALLBACK            = IBinder::FIRST_CALL_TRANSACTION + 65,
        GETACTIVEPLAYBACKCONFIGURATIONS       = IBinder::FIRST_CALL_TRANSACTION + 66,
        */

        TRACK_PLAYER                          = IBinder::FIRST_CALL_TRANSACTION + 67,
        PLAYER_ATTRIBUTES                     = IBinder::FIRST_CALL_TRANSACTION + 68,
        PLAYER_EVENT                          = IBinder::FIRST_CALL_TRANSACTION + 69,
        RELEASE_PLAYER                        = IBinder::FIRST_CALL_TRANSACTION + 70,

        /*
        DISABLE_RINGTONE_SYNC                 = IBinder::FIRST_CALL_TRANSACTION + 71,
        */
    };

    DECLARE_META_INTERFACE(AudioManager)

    // The parcels created by these methods must be kept in sync with the
    // corresponding methods from IAudioService.aidl and objects it imports.
    virtual audio_unique_id_t trackPlayer(player_type_t playerType, audio_usage_t usage,
                audio_content_type_t content, const sp<IBinder>& player) = 0;
    /*oneway*/ virtual status_t playerAttributes(audio_unique_id_t piid, audio_usage_t usage,
                audio_content_type_t content)= 0;
    /*oneway*/ virtual status_t playerEvent(audio_unique_id_t piid, player_state_t event) = 0;
    /*oneway*/ virtual status_t releasePlayer(audio_unique_id_t piid) = 0;
};

// ----------------------------------------------------------------------------

}; // namespace android

#endif // ANDROID_IAUDIOMANAGER_H
