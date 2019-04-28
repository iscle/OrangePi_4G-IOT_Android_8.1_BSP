/*
 * Copyright (C) 2013-2016 The Android Open Source Project
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

#define LOG_TAG "tfa_98xx"
/*#define LOG_NDEBUG 0*/
#include <cutils/log.h>

#include <stdlib.h>
#include <audio_hw.h>
#include <dlfcn.h>
#include "audio_extn.h"
#include <platform.h>
#include <math.h>

#define LIB_SPEAKER_BUNDLE "/system/lib/libexTfa98xx.so"


enum exTfa98xx_Audio_Mode
{
    Audio_Mode_None = -1,
    Audio_Mode_Music_Normal,
    Audio_Mode_Hfp_Client,
    Audio_Mode_Voice,
    Audio_Mode_Hs_Hfp,
    Audio_Mode_Max
};
typedef enum exTfa98xx_Audio_Mode exTfa98xx_audio_mode_t;

enum exTfa98xx_Func_Mode
{
    Func_Mode_None = -1,
    Func_Mode_Speaker,
    Func_Mode_BT
};
typedef enum exTfa98xx_Func_Mode exTfa98xx_func_mode_t;

#define I2S_CLOCK_ENABLE        1
#define I2S_CLOCK_DISABLE       0
#define HFP_MAX_VOLUME          (15.000000)
#define TFA_98XX_HFP_VSETPS     (5.0)

exTfa98xx_audio_mode_t current_audio_mode = Audio_Mode_None;

typedef int (*set_speaker_on_t)(exTfa98xx_audio_mode_t);
typedef int (*set_speaker_off_t)(void);
typedef int (*set_speaker_calibration_t)(int);
typedef void (*set_speaker_volume_step_t)(int, int);


struct speaker_data {
    struct audio_device *adev;
    void *speaker_bundle;
    set_speaker_on_t set_speaker_on;
    set_speaker_off_t set_speaker_off;
    set_speaker_calibration_t set_speaker_calibration;
    set_speaker_volume_step_t set_speaker_volume_step;
    int ref_cnt[Audio_Mode_Max];
    int route_cnt[Audio_Mode_Max];
    bool update_ref_cnt;
};

struct speaker_data *tfa98xx_speaker_data = NULL;

static struct speaker_data* open_speaker_bundle()
{
    struct speaker_data *sd = calloc(1, sizeof(struct speaker_data));

    sd->speaker_bundle = dlopen(LIB_SPEAKER_BUNDLE, RTLD_NOW);
    if (sd->speaker_bundle == NULL) {
        ALOGE("%s: DLOPEN failed for %s", __func__, LIB_SPEAKER_BUNDLE);
        goto error;
    } else {
        ALOGV("%s: DLOPEN successful for %s", __func__, LIB_SPEAKER_BUNDLE);

        sd->set_speaker_on = (set_speaker_on_t)dlsym(sd->speaker_bundle,
                                             "exTfa98xx_speakeron");
        if (sd->set_speaker_on == NULL) {
            ALOGE("%s: dlsym error %s for exTfa98xx_speakeron", __func__,
                  dlerror());
            goto error;
        }
        sd->set_speaker_off = (set_speaker_off_t)dlsym(sd->speaker_bundle,
                                             "exTfa98xx_speakeroff");
        if (sd->set_speaker_off == NULL) {
            ALOGE("%s: dlsym error %s for exTfa98xx_speakeroff", __func__,
                  dlerror());
            goto error;
        }
        sd->set_speaker_volume_step = (set_speaker_volume_step_t)dlsym(sd->speaker_bundle,
                                             "exTfa98xx_setvolumestep");
        if (sd->set_speaker_volume_step == NULL) {
            ALOGE("%s: dlsym error %s for exTfa98xx_setvolumestep",
                  __func__, dlerror());
            goto error;
        }
        sd->set_speaker_calibration = (set_speaker_calibration_t)dlsym(sd->speaker_bundle,
                                             "exTfa98xx_calibration");
        if (sd->set_speaker_calibration == NULL) {
            ALOGE("%s: dlsym error %s for exTfa98xx_calibration",
                  __func__, dlerror());
            goto error;
        }
    }
    return sd;

error:
    free(sd);
    return 0;
}

static void close_speaker_bundle(struct speaker_data *sd)
{
    if (sd != NULL) {
        dlclose(sd->speaker_bundle);
        free(sd);
        sd = NULL;
    }
}

static int adev_i2s_clock_operation(int enable, struct audio_device *adev, char *paths)
{
    int ret = -1;

    ALOGD("%s: mixer paths is: %s, enable: %d\n", __func__, paths, enable);
    if(I2S_CLOCK_ENABLE == enable) {
        ret = audio_route_apply_and_update_path(adev->audio_route, paths);
        if(ret) {
            ALOGE("%s: audio_route_apply_and_update_path return %d\n", __func__, ret);
            return ret;
        }
    } else {
        ret = audio_route_reset_and_update_path(adev->audio_route, paths);
        if(ret) {
            ALOGE("%s: audio_route_reset_and_update_path return %d\n", __func__, ret);
            return ret;
        }
    }
    return 0;
}

static int tfa_98xx_set_audio_mode(int enable, struct audio_device *adev, exTfa98xx_audio_mode_t audio_mode)
{
    char paths[32] = "init_smart_pa";

    switch(audio_mode) {
        case Audio_Mode_Music_Normal:
            strcat(paths, " music");
            break;
        case Audio_Mode_Voice:
        case Audio_Mode_Hfp_Client:
        case Audio_Mode_Hs_Hfp:
            strcat(paths, " voice");
            break;
        default:
            ALOGE("%s: function %d not support!\n",__func__, audio_mode);
            return -EINVAL;
    }

    ALOGV("%s: mixer paths is: %s, enable: %d\n", __func__, paths, enable);
    adev_i2s_clock_operation(enable, adev, paths);
    return 0;

}

static exTfa98xx_audio_mode_t tfa_98xx_get_audio_mode(struct speaker_data *data)
{
    exTfa98xx_audio_mode_t tfa_98xx_audio_mode = Audio_Mode_None;
    struct listnode *node;
    struct audio_usecase *usecase;
    audio_mode_t mode = data->adev->mode;
    int i = 0;

    ALOGV("%s: enter\n", __func__);

    for (i = 0; i < Audio_Mode_Max; i++)
        data->route_cnt[i] = 0;

    list_for_each(node, &data->adev->usecase_list) {
        usecase = node_to_item(node, struct audio_usecase, list);
        if (usecase->devices & AUDIO_DEVICE_OUT_ALL_SCO) {
            if(data->adev->snd_dev_ref_cnt[usecase->out_snd_device] != 0) {
                tfa_98xx_audio_mode = Audio_Mode_Hs_Hfp;
                data->route_cnt[tfa_98xx_audio_mode]++;
                ALOGV("%s: audio_mode hs_hfp\n", __func__);
            }
        } else if (usecase->devices & AUDIO_DEVICE_OUT_SPEAKER) {
            if ((mode == AUDIO_MODE_IN_CALL) || audio_extn_hfp_is_active(data->adev)) {
                if (audio_extn_hfp_is_active(data->adev)) {
                    if(data->adev->snd_dev_ref_cnt[usecase->out_snd_device] != 0) {
                        tfa_98xx_audio_mode = Audio_Mode_Hfp_Client;
                        data->route_cnt[tfa_98xx_audio_mode]++;
                        ALOGV("%s: audio_mode hfp client\n", __func__);
                    }
                } else {
                    if(data->adev->snd_dev_ref_cnt[usecase->out_snd_device] != 0) {
                        tfa_98xx_audio_mode = Audio_Mode_Voice;
                        data->route_cnt[tfa_98xx_audio_mode]++;
                        ALOGV("%s: audio_mode voice\n", __func__);
                    }
                }
            } else {
                if (data->adev->snd_dev_ref_cnt[usecase->out_snd_device] != 0) {
                    tfa_98xx_audio_mode = Audio_Mode_Music_Normal;
                    data->route_cnt[tfa_98xx_audio_mode]++;
                    ALOGV("%s: tfa_98xx_audio_mode music\n", __func__);
                }
            }
        } else {
            ALOGE("%s: no device match \n", __func__);
        }
    }
    ALOGV("%s: tfa_98xx_audio_mode %d exit\n", __func__, tfa_98xx_audio_mode);

    return tfa_98xx_audio_mode;
}

static int tfa_98xx_set_func_mode(int enable, struct audio_device *adev, exTfa98xx_func_mode_t func_mode)
{
    struct speaker_data *data = tfa98xx_speaker_data;
    char paths[32] = "init_smart_pa";

    if (data) {
        switch(func_mode) {
            case Func_Mode_Speaker:
                strcat(paths, " func_speaker");
                break;
            case Func_Mode_BT:
                strcat(paths, " func_bt");
                break;
            default:
                ALOGE("%s: function %d not support!\n",__func__, func_mode);
                return -EINVAL;
        }

        ALOGV("%s: mixer paths is: %s, enable: %d\n", __func__, paths, enable);
        adev_i2s_clock_operation(enable, adev, paths);
    }
    return 0;
}

static exTfa98xx_func_mode_t tfa_98xx_get_func_mode(exTfa98xx_audio_mode_t audio_mode)
{
    exTfa98xx_func_mode_t func_mode = Func_Mode_None;

    switch(audio_mode) {
        case Audio_Mode_Music_Normal:
        case Audio_Mode_Voice:
            ALOGV("%s: tfa_98xx_func_mode speaker \n", __func__);
            func_mode = Func_Mode_Speaker;
            break;
        case Audio_Mode_Hfp_Client:
        case Audio_Mode_Hs_Hfp:
            ALOGV("%s: tfa_98xx_func_mode bt \n", __func__);
            func_mode = Func_Mode_BT;
            break;
        default:
            break;
    }
    return func_mode;
}

static void tfa_98xx_disable_speaker(void)
{
    struct speaker_data *data = tfa98xx_speaker_data;
    int ret = 0;

    ret = data->set_speaker_off();
    if (ret) {
        ALOGE("%s: exTfa98xx_speakeroff failed result = %d\n", __func__, ret);
        goto on_error;
    }

    ret = tfa_98xx_set_audio_mode(I2S_CLOCK_DISABLE, data->adev, current_audio_mode);
    if (ret) {
        ALOGE("%s: tfa_98xx_set_audio_mode disable failed return %d\n", __func__, ret);
        goto on_error;
    }
    current_audio_mode = Audio_Mode_None;
on_error:
    return;

}


void audio_extn_tfa_98xx_disable_speaker(snd_device_t snd_device)
{
    struct speaker_data *data = tfa98xx_speaker_data;
    int i = 0;
    exTfa98xx_audio_mode_t new_audio_mode = Audio_Mode_None;

    ALOGV("%s: enter\n", __func__);

    if (data) {
        if ((current_audio_mode == Audio_Mode_None) || (snd_device > SND_DEVICE_OUT_END))
            goto on_exit;

        switch(snd_device) {
            case SND_DEVICE_OUT_SPEAKER:
                new_audio_mode = Audio_Mode_Music_Normal;
                break;
            case SND_DEVICE_OUT_VOICE_SPEAKER:
                new_audio_mode = Audio_Mode_Voice;
                break;
            case SND_DEVICE_OUT_VOICE_SPEAKER_HFP:
                new_audio_mode = Audio_Mode_Hfp_Client;
                break;
            case SND_DEVICE_OUT_BT_SCO:
                new_audio_mode = Audio_Mode_Hs_Hfp;
                break;
            default:
                break;
        }

        if ((new_audio_mode == Audio_Mode_None) || (data->ref_cnt[new_audio_mode] <= 0)) {
            ALOGE("%s: device ref cnt is already 0", __func__);
            goto on_exit;
        }

        data->ref_cnt[new_audio_mode]--;

        for (i = 0; i < Audio_Mode_Max; i++) {
            if (data->ref_cnt[i] > 0) {
                ALOGD("%s: exTfa98xx_speaker still in use\n", __func__);
                goto on_exit;
            }
        }

        if (data->adev->enable_hfp)
            data->set_speaker_volume_step(0, 0);

        tfa_98xx_disable_speaker();
    }

    ALOGV("%s: exit\n", __func__);
on_exit:
    return;
}

int audio_extn_tfa_98xx_enable_speaker(void)
{
    struct speaker_data *data = tfa98xx_speaker_data;
    exTfa98xx_audio_mode_t new_audio_mode = Audio_Mode_Music_Normal;
    int ret = 0;
    int i = 0;

    ALOGV("%s: enter\n", __func__);

    if (data) {

        new_audio_mode = tfa_98xx_get_audio_mode(data);
        if ((new_audio_mode != Audio_Mode_None) && (data->ref_cnt[new_audio_mode] >= 1)) {
            ALOGD("%s, mode %d already active!", __func__, new_audio_mode);
            data->ref_cnt[new_audio_mode]++;
            goto on_exit;
        }

        ret = tfa_98xx_set_audio_mode(I2S_CLOCK_ENABLE, data->adev, new_audio_mode);
        if (ret) {
            ALOGE("%s: tfa_98xx_set_audio_mode enable failed return %d\n", __func__, ret);
            goto on_exit;
        }

        ret = data->set_speaker_on(new_audio_mode);
        if (ret) {
            ALOGE("%s: exTfa98xx_speakeron failed result = %d\n", __func__, ret);
            goto on_exit;
        }

        current_audio_mode = new_audio_mode;
        for (i = 0; i < Audio_Mode_Max; i++) {
            data->ref_cnt[i] = data->route_cnt[i];
        }
        data->update_ref_cnt = false;
    }

    ALOGV("%s: exit\n", __func__);

on_exit:
    return ret;

}

void audio_extn_tfa_98xx_set_mode(void)
{
    int ret = 0;
    struct speaker_data *data = tfa98xx_speaker_data;
    exTfa98xx_audio_mode_t new_audio_mode = Audio_Mode_None;
    exTfa98xx_func_mode_t new_func_mode = Func_Mode_None;

    ALOGV("%s: enter\n", __func__);

    if (data) {
        new_audio_mode = tfa_98xx_get_audio_mode(data);

        new_func_mode = tfa_98xx_get_func_mode(new_audio_mode);
        if (new_func_mode == Func_Mode_None)
            return;

        ret = tfa_98xx_set_func_mode(I2S_CLOCK_ENABLE, data->adev, new_func_mode);
        if (ret) {
            ALOGE("%s: tfa_98xx_set_func_mode enable return %d\n", __func__, ret);
        }
        data->update_ref_cnt = true;
    }

    ALOGV("%s: exit\n", __func__);
}

void audio_extn_tfa_98xx_set_mode_bt(void)
{
    struct speaker_data *data = tfa98xx_speaker_data;
    int ret = 0;

    if (data) {
        ret = tfa_98xx_set_func_mode(I2S_CLOCK_ENABLE, data->adev, Func_Mode_BT);
        if (ret) {
            ALOGE("%s: tfa_98xx_set_func_mode enable return %d\n", __func__, ret);
        }
    }
}

void audio_extn_tfa_98xx_update(void)
{
    struct speaker_data *data = tfa98xx_speaker_data;
    exTfa98xx_audio_mode_t new_audio_mode = Audio_Mode_Music_Normal;

    ALOGD("%s: enter\n", __func__);

    if (data) {

        new_audio_mode = tfa_98xx_get_audio_mode(data);
        if (new_audio_mode <= current_audio_mode) {
            ALOGE("%s: audio_extn_tfa_98xx_update same mode\n", __func__);
            if (data->update_ref_cnt == true) {
                data->ref_cnt[new_audio_mode]++;
                data->update_ref_cnt = false;
            }
            goto on_error;
        }

        if (current_audio_mode != Audio_Mode_None) {
            tfa_98xx_disable_speaker();
        }

        audio_extn_tfa_98xx_enable_speaker();

    }

    ALOGV("%s: exit\n", __func__);
on_error:
    return;

}

void audio_extn_tfa_98xx_set_voice_vol(float vol)
{
    struct speaker_data *data = tfa98xx_speaker_data;
    int vsteps = 0;

    if (data) {
        if (data->adev->enable_hfp) {
            if (vol < 0.0) {
                vol = 0.0;
            } else {
                vol = ((vol > HFP_MAX_VOLUME) ? 1.0 : (vol / HFP_MAX_VOLUME));
            }
            vsteps = (int)floorf((1.0 - vol) * TFA_98XX_HFP_VSETPS);
        } else {
            return;
        }
        ALOGD("%s: vsteps %d\n", __func__, vsteps);
        data->set_speaker_volume_step(vsteps, vsteps);
    }
}

bool audio_extn_tfa_98xx_is_supported(void)
{
    struct speaker_data *data = tfa98xx_speaker_data;
    if (data)
        return true;
    else
        return false;
}

int audio_extn_tfa_98xx_init(struct audio_device *adev)
{
    int ret = 0;
    struct speaker_data *data = open_speaker_bundle();

    ALOGV("%s: enter\n", __func__);

    if (data) {
        ret = tfa_98xx_set_audio_mode(I2S_CLOCK_ENABLE, adev, Audio_Mode_Music_Normal);
        if (ret) {
            ALOGE("%s: tfa_98xx_set_audio_mode enable return %d\n", __func__, ret);
            goto err_init;
        }

        ret = data->set_speaker_calibration(0);
        if (ret) {
            ALOGE("%s: exTfa98xx_calibration return %d\n", __func__, ret);
        }

        ret = tfa_98xx_set_audio_mode(I2S_CLOCK_DISABLE, adev, Audio_Mode_Music_Normal);
        if (ret) {
            ALOGE("%s: tfa_98xx_set_audio_mode disable return %d\n", __func__, ret);
            goto err_init;
        }

        data->adev = adev;
        tfa98xx_speaker_data = data;
        ALOGV("%s: exit\n", __func__);
        return 0;

    }

err_init:
    close_speaker_bundle(data);
    return -EINVAL;
}

void audio_extn_tfa_98xx_deinit(void)
{
    struct speaker_data *data = tfa98xx_speaker_data;

    if (data) {
        data->set_speaker_off();
        close_speaker_bundle(data);
        tfa98xx_speaker_data = NULL;
    }
}

