/*
 * Copyright (C) 2015 The Android Open Source Project
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

#define LOG_TAG "audio_hw_dsm_feedback"
/*#define LOG_NDEBUG 0*/
#define LOG_NDDEBUG 0

#include <errno.h>
#include <math.h>
#include <cutils/log.h>

#include "audio_hw.h"
#include "platform.h"
#include "platform_api.h"
#include <stdlib.h>


static struct pcm_config pcm_config_dsm = {
    .channels = 2,
    .rate = 48000,
    .period_size = 256,
    .period_count = 4,
    .format = PCM_FORMAT_S16_LE,
    .start_threshold = 0,
    .stop_threshold = INT_MAX,
    .avail_min = 0,
};

int start_dsm_feedback_processing(struct audio_device *adev, int enable)
{
    int ret = 0;
    int32_t pcm_dev_tx_id = -1;
    static struct pcm *dsm_pcm_handle = NULL;

    if (enable) {
        /*do nothing if already enabled*/
        if (dsm_pcm_handle)
            return ret;

        pcm_dev_tx_id = platform_get_pcm_device_id(USECASE_AUDIO_DSM_FEEDBACK, PCM_CAPTURE);
        if (pcm_dev_tx_id < 0) {
            ALOGE("%s: Invalid pcm device for usecase (%d)",
                  __func__, USECASE_AUDIO_DSM_FEEDBACK);
            ret = -ENODEV;
            goto close;
        }

        dsm_pcm_handle = pcm_open(adev->snd_card,
                                 pcm_dev_tx_id,
                                 PCM_IN, &pcm_config_dsm);
        if (dsm_pcm_handle && !pcm_is_ready(dsm_pcm_handle)) {
            ALOGE("%s: %s", __func__, pcm_get_error(dsm_pcm_handle));
            ret = -EIO;
            goto close;
        }

        if (pcm_start(dsm_pcm_handle) < 0) {
            ALOGE("%s: pcm start for RX failed", __func__);
            ret = -EINVAL;
            goto close;
        }

        return ret;
    }

close:
    /*close pcm if disable or error happend in opening*/
    if (dsm_pcm_handle) {
        pcm_close(dsm_pcm_handle);
        dsm_pcm_handle = NULL;
    }

    return ret;
}

void audio_extn_dsm_feedback_enable(struct audio_device *adev,
                         snd_device_t snd_device,
                         int benable)
{
    if ( NULL == adev )
        return;

    if( snd_device == SND_DEVICE_OUT_SPEAKER ||
        snd_device == SND_DEVICE_OUT_SPEAKER_REVERSE ||
        snd_device == SND_DEVICE_OUT_VOICE_SPEAKER ||
        snd_device == SND_DEVICE_OUT_SPEAKER_SAFE ||
        snd_device == SND_DEVICE_OUT_SPEAKER_AND_HEADPHONES ||
        snd_device == SND_DEVICE_OUT_SPEAKER_AND_LINE ||
        snd_device == SND_DEVICE_OUT_SPEAKER_SAFE_AND_HEADPHONES ||
        snd_device == SND_DEVICE_OUT_SPEAKER_SAFE_AND_LINE )
        start_dsm_feedback_processing(adev, benable);
}
