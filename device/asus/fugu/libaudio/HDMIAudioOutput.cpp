/*
**
** Copyright 2014, The Android Open Source Project
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

#define LOG_TAG "AudioHAL_HDMIAudioOutput"

#include <utils/Log.h>

#include <stdint.h>
#include <sound/asound.h> // bionic

#include "AudioHardwareOutput.h"
#include "AudioStreamOut.h"
#include "HDMIAudioOutput.h"

namespace android {

extern AudioHardwareOutput gAudioHardwareOutput;

HDMIAudioOutput::HDMIAudioOutput()
    : AudioOutput(kHDMI_ALSADeviceName, PCM_FORMAT_S24_LE)
{
}

HDMIAudioOutput::~HDMIAudioOutput()
{
}

status_t HDMIAudioOutput::setupForStream(const AudioStreamOut& stream)
{
    mFramesPerChunk = stream.framesPerChunk();
    mFramesPerSec = stream.outputSampleRate();
    mBufferChunks = stream.nomChunksInFlight();
    mChannelCnt = audio_channel_count_from_out_mask(stream.chanMask());

    ALOGI("setupForStream format %08x, rate = %u", stream.format(), mFramesPerSec);

    if (!gAudioHardwareOutput.getHDMIAudioCaps().supportsFormat(
            stream.format(),
            stream.sampleRate(),
            mChannelCnt,
            stream.isIec958NonAudio())) {
        ALOGE("HDMI Sink does not support format = 0x%0X, srate = %d, #channels = 0%d",
                stream.format(), mFramesPerSec, mChannelCnt);
        return BAD_VALUE;
    }

    setupInternal();

    setChannelStatusToCompressed(stream.isIec958NonAudio());

    return initCheck();
}

void HDMIAudioOutput::applyPendingVolParams()
{
}

#define IEC958_AES0_NONAUDIO      (1<<1)   /* 0 = audio, 1 = non-audio */

void HDMIAudioOutput::setChannelStatusToCompressed(bool compressed)
{
    struct snd_aes_iec958  iec958;
    struct mixer* mixer;
    int err;
    const size_t count = 1;

    ALOGI("setChannelStatusToCompressed %d", compressed);

    mixer = mixer_open(mALSACardID);
    if (mixer == NULL) {
        ALOGE("Couldn't open mixer on alsa id %d", mALSACardID);
        return;
    }

    const char *ctlName = "IEC958 Playback Default";
    struct mixer_ctl *ctl = mixer_get_ctl_by_name(mixer, ctlName);
    if (ctl == NULL) {
        ALOGE("Couldn't get mixer ctl %s", ctlName);
        goto finish;
    }

    // Set count to 1 so we get one complete iec958 structure.
    err = mixer_ctl_get_array(ctl, &iec958, count);
    if (err < 0) {
        ALOGE("Channel Status bit get has failed\n");
        goto finish;
    }

    if (compressed) {
        iec958.status[0] |= IEC958_AES0_NONAUDIO;
    } else {
        iec958.status[0] &= ~IEC958_AES0_NONAUDIO;
    }

    err = mixer_ctl_set_array(ctl, &iec958, count);
    if (err < 0) {
        ALOGE("Channel Status bit set has failed\n");
    }

finish:
    mixer_close(mixer);
}

void HDMIAudioOutput::dump(String8& result)
{
    const size_t SIZE = 256;
    char buffer[SIZE];

    snprintf(buffer, SIZE,
            "\t%s Audio Output\n"
            "\t\tSample Rate       : %d\n"
            "\t\tChannel Count     : %d\n"
            "\t\tState             : %d\n",
            getOutputName(),
            mFramesPerSec,
            mChannelCnt,
            mState);
    result.append(buffer);
}

} // namespace android
