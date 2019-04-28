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

#ifndef __ANDROID_PLAYER_BASE_H__
#define __ANDROID_PLAYER_BASE_H__

#include <audiomanager/IPlayer.h>
#include <audiomanager/AudioManager.h>
#include <audiomanager/IAudioManager.h>


namespace android {

class PlayerBase : public BnPlayer
{
public:
    explicit PlayerBase();
    virtual ~PlayerBase();

    virtual void destroy() = 0;

    //IPlayer implementation
    virtual void start();
    virtual void pause();
    virtual void stop();
    virtual void setVolume(float vol);
    virtual void setPan(float pan);
    virtual void setStartDelayMs(int32_t delayMs);
    virtual void applyVolumeShaper(
            const sp<VolumeShaper::Configuration>& configuration,
            const sp<VolumeShaper::Operation>& operation) override;

    virtual status_t onTransact(
                uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags);


            status_t startWithStatus();
            status_t pauseWithStatus();
            status_t stopWithStatus();

            //FIXME temporary method while some player state is outside of this class
            void reportEvent(player_state_t event);

protected:

            void init(player_type_t playerType, audio_usage_t usage);
            void baseDestroy();

    //IPlayer methods handlers for derived classes
    virtual status_t playerStart()  { return NO_ERROR; }
    virtual status_t playerPause()  { return NO_ERROR; }
    virtual status_t playerStop()  { return NO_ERROR; }
    virtual status_t playerSetVolume()  { return NO_ERROR; }

    // mutex for IPlayer volume and pan, and player-specific volume
    Mutex mSettingsLock;

    // volume multipliers coming from the IPlayer volume and pan controls
    float mPanMultiplierL, mPanMultiplierR;
    float mVolumeMultiplierL, mVolumeMultiplierR;

private:
            // report events to AudioService
            void servicePlayerEvent(player_state_t event);
            void serviceReleasePlayer();

    // native interface to AudioService
    android::sp<android::IAudioManager> mAudioManager;

    // player interface ID, uniquely identifies the player in the system
    audio_unique_id_t mPIId;

    // Mutex for state reporting
    Mutex mPlayerStateLock;
    player_state_t mLastReportedEvent;
};

} // namespace android

#endif /* __ANDROID_PLAYER_BASE_H__ */
