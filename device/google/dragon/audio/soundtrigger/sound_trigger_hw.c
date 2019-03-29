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

#define LOG_TAG "sound_trigger_hw_dragon"
/*#define LOG_NDEBUG 0*/

#include <errno.h>
#include <fcntl.h>
#include <malloc.h>
#include <poll.h>
#include <pthread.h>
#include <sys/ioctl.h>
#include <sys/prctl.h>
#include <cutils/log.h>
#include <cutils/uevent.h>

#include <hardware/hardware.h>
#include <system/sound_trigger.h>
#include <hardware/sound_trigger.h>
#include <tinyalsa/asoundlib.h>

#define DRAGON_MIXER_VAD          0
#define DRAGON_MIC_CTRL           "Int Mic Switch"
#define DRAGON_HOTWORD_MODEL_CTRL "Hotword Model"
#define UEVENT_MSG_LEN            1024

#define DRAGON_ST_CARD_NUM 0
#define DRAGON_ST_DEV_NUM 87
#define DRAGON_VAD_DEV	"/dev/snd/pcmC0D87c"

static const struct sound_trigger_properties hw_properties = {
    "The Android Open Source Project", // implementor
    "Dragon OK Google ", // description
    1, // version
    { 0xe780f240, 0xf034, 0x11e3, 0xb79a, { 0x00, 0x02, 0xa5, 0xd5, 0xc5, 0x1b } }, // uuid
    1, // max_sound_models
    1, // max_key_phrases
    1, // max_users
    RECOGNITION_MODE_VOICE_TRIGGER, // recognition_modes
    true, // capture_transition
    0, // max_capture_ms
    false, // concurrent_capture
    false, // trigger_in_event
    0 // power_consumption_mw
};

struct dragon_sound_trigger_device {
    struct sound_trigger_hw_device device;
    sound_model_handle_t model_handle;
    recognition_callback_t recognition_callback;
    void *recognition_cookie;
    sound_model_callback_t sound_model_callback;
    void *sound_model_cookie;
    pthread_t callback_thread;
    pthread_mutex_t lock;
    int send_sock;
    int term_sock;
    struct mixer *mixer;
    struct mixer_ctl *int_mic_sw;
    struct mixer_ctl *hotword_model;
    struct sound_trigger_recognition_config *config;
    struct pcm *pcm;
    int is_streaming;
    int opened;
};

struct rt_codec_cmd {
    size_t number;
    int *buf;
};

enum {
    RT_READ_CODEC_DSP_IOCTL = _IOR('R', 0x04, struct rt_codec_cmd),
    RT_WRITE_CODEC_DSP_IOCTL = _IOW('R', 0x04, struct rt_codec_cmd),
};

// Since there's only ever one sound_trigger_device, keep it as a global so that other people can
// dlopen this lib to get at the streaming audio.
static struct dragon_sound_trigger_device g_stdev = { .lock = PTHREAD_MUTEX_INITIALIZER };

static void stdev_dsp_set_power(struct dragon_sound_trigger_device *stdev,
                                int val)
{
    stdev->is_streaming = 0;
    mixer_ctl_set_value(stdev->int_mic_sw, 0, val);
}

static int stdev_init_mixer(struct dragon_sound_trigger_device *stdev)
{
    int ret = -1;


    stdev->mixer = mixer_open(DRAGON_MIXER_VAD);
    if (!stdev->mixer)
        goto err;

    stdev->int_mic_sw = mixer_get_ctl_by_name(stdev->mixer, DRAGON_MIC_CTRL);
    if (!stdev->int_mic_sw)
        goto err;

    stdev->hotword_model = mixer_get_ctl_by_name(stdev->mixer,
                                                 DRAGON_HOTWORD_MODEL_CTRL);
    if (!stdev->hotword_model)
        ALOGE("No hotword model mixer control\n");

    stdev_dsp_set_power(stdev, 0); // Disable DSP at the beginning

    return 0;

err:
    if (stdev->mixer)
        mixer_close(stdev->mixer);
    return ret;
}

static void stdev_close_term_sock(struct dragon_sound_trigger_device *stdev)
{
    if (stdev->send_sock >=0) {
        close(stdev->send_sock);
        stdev->send_sock = -1;
    }
    if (stdev->term_sock >=0) {
        close(stdev->term_sock);
        stdev->term_sock = -1;
    }
}

static void stdev_close_mixer(struct dragon_sound_trigger_device *stdev)
{
    if (stdev) {
        stdev_dsp_set_power(stdev, 0);
        mixer_close(stdev->mixer);
        stdev_close_term_sock(stdev);
    }
}

static int vad_load_sound_model(struct dragon_sound_trigger_device *stdev,
                                char *buf, size_t len)
{
    int ret;
    ret = mixer_ctl_set_array(stdev->hotword_model, buf, len);
    if (ret)
        ALOGE("Failed hotword model write %d\n", ret);
    return ret;
}

static char *sound_trigger_event_alloc(struct dragon_sound_trigger_device *
                                       stdev)
{
    char *data;
    struct sound_trigger_phrase_recognition_event *event;

    data = (char *)calloc(1,
                    sizeof(struct sound_trigger_phrase_recognition_event));
    if (!data)
        return NULL;

    event = (struct sound_trigger_phrase_recognition_event *)data;
    event->common.status = RECOGNITION_STATUS_SUCCESS;
    event->common.type = SOUND_MODEL_TYPE_KEYPHRASE;
    event->common.model = stdev->model_handle;

    if (stdev->config) {
        unsigned int i;

        event->num_phrases = stdev->config->num_phrases;
        if (event->num_phrases > SOUND_TRIGGER_MAX_PHRASES)
            event->num_phrases = SOUND_TRIGGER_MAX_PHRASES;
        for (i=0; i < event->num_phrases; i++)
            memcpy(&event->phrase_extras[i], &stdev->config->phrases[i],
                   sizeof(struct sound_trigger_phrase_recognition_extra));
    }

    event->num_phrases = 1;
    event->phrase_extras[0].confidence_level = 100;
    event->phrase_extras[0].num_levels = 1;
    event->phrase_extras[0].levels[0].level = 100;
    event->phrase_extras[0].levels[0].user_id = 0;
    // Signify that all the data is comming through streaming, not through the
    // buffer.
    event->common.capture_available = true;

    event->common.audio_config = AUDIO_CONFIG_INITIALIZER;
    event->common.audio_config.sample_rate = 16000;
    event->common.audio_config.channel_mask = AUDIO_CHANNEL_IN_MONO;
    event->common.audio_config.format = AUDIO_FORMAT_PCM_16_BIT;

    return data;
}

static void *callback_thread_loop(void *context)
{
    char msg[UEVENT_MSG_LEN];
    struct dragon_sound_trigger_device *stdev =
               (struct dragon_sound_trigger_device *)context;
    struct pollfd fds[2];
    struct pcm_config config;
    int exit_sockets[2];
    int err = 0;
    int i, n;

    ALOGI("%s", __func__);
    prctl(PR_SET_NAME, (unsigned long)"sound trigger callback", 0, 0, 0);

    pthread_mutex_lock(&stdev->lock);
    if (stdev->recognition_callback == NULL)
        goto exit;

    if (socketpair(AF_UNIX, SOCK_STREAM, 0, exit_sockets) == -1)
        goto exit;

    stdev_close_term_sock(stdev);
    stdev->send_sock = exit_sockets[0];
    stdev->term_sock = exit_sockets[1];

    stdev_dsp_set_power(stdev, 1);

    memset(&config, 0, sizeof(config));
    config.channels = 1;
    config.rate = 16000;
    config.period_size = 240;
    config.period_count = 8;
    config.format = PCM_FORMAT_S16_LE;
    config.start_threshold = 0;
    config.stop_threshold = 0;
    config.silence_threshold = 0;
    stdev->pcm = pcm_open(DRAGON_ST_CARD_NUM, DRAGON_ST_DEV_NUM,
                          PCM_IN | PCM_MMAP, &config);
    if (!stdev->pcm || !pcm_is_ready(stdev->pcm)) {
        ALOGE("Unable to open PCM device (%s)\n", pcm_get_error(stdev->pcm));
        err = -1;
        goto exit;
    }
    err = pcm_start(stdev->pcm);
    if (err) {
        ALOGE("Unable to start PCM device (%s)\n", pcm_get_error(stdev->pcm));
        goto exit;
    }

    memset(fds, 0, 2 * sizeof(struct pollfd));
    fds[0].events = POLLIN;
    fds[0].fd = pcm_get_poll_fd(stdev->pcm);;
    if (fds[0].fd == -1) {
        ALOGE("Error opening socket for hotplug uevent");
        goto exit;
    }
    fds[1].events = POLLIN;
    fds[1].fd = stdev->term_sock;

    pthread_mutex_unlock(&stdev->lock);

    while (1) {
        err = poll(fds, 2, -1);
        pthread_mutex_lock(&stdev->lock);
        if ((err < 0) || (stdev->recognition_callback == NULL)) {
            ALOGE_IF(err < 0, "Error in hotplug CPU poll: %d", errno);
            break;
        }

        if (fds[0].revents & POLLIN) {
            struct sound_trigger_phrase_recognition_event *event;
            recognition_callback_t callback = stdev->recognition_callback;
            void *cookie = stdev->recognition_cookie;

            event = (struct sound_trigger_phrase_recognition_event *)
                        sound_trigger_event_alloc(stdev);
            if (!event) {
                pcm_close(stdev->pcm);
                stdev->pcm = NULL;
                goto exit;
            }
            stdev->is_streaming = 1;
            ALOGI("%s send callback model %d", __func__,
                    stdev->model_handle);
            pthread_mutex_unlock(&stdev->lock);
            if (callback != NULL) {
                callback(&event->common, cookie);
            }
            pthread_mutex_lock(&stdev->lock);
            free(event);
            /* Leave the device open for streaming. */
            goto exit;
        } else if (fds[1].revents & POLLIN) {
            read(fds[1].fd, &n, sizeof(n)); /* clear the socket */
            ALOGI("%s: Termination message", __func__);
            break;
        } else {
            ALOGI("%s: Message to ignore", __func__);
        }
        pthread_mutex_unlock(&stdev->lock);
    }

    if (stdev->pcm) {
        pcm_close(stdev->pcm);
        stdev->pcm = NULL;
    }

exit:
    stdev->recognition_callback = NULL;
    stdev_close_term_sock(stdev);

    if (stdev->config && !stdev->config->capture_requested)
        stdev_dsp_set_power(stdev, 0);

    pthread_mutex_unlock(&stdev->lock);

    return (void *)(long)err;
}

static int stdev_get_properties(const struct sound_trigger_hw_device *dev,
                                struct sound_trigger_properties *properties)
{
    struct dragon_sound_trigger_device *stdev =
                               (struct dragon_sound_trigger_device *)dev;

    ALOGI("%s", __func__);
    if (properties == NULL)
        return -EINVAL;
    memcpy(properties, &hw_properties, sizeof(struct sound_trigger_properties));
    return 0;
}

static int stdev_load_sound_model(const struct sound_trigger_hw_device *dev,
                                  struct sound_trigger_sound_model *sound_model,
                                  sound_model_callback_t callback,
                                  void *cookie,
                                  sound_model_handle_t *handle)
{
    struct dragon_sound_trigger_device *stdev =
                                 (struct dragon_sound_trigger_device *)dev;
    int ret = 0;

    ALOGI("%s", __func__);
    pthread_mutex_lock(&stdev->lock);
    if (handle == NULL || sound_model == NULL) {
        ret = -EINVAL;
        goto exit;
    }

    if (stdev->model_handle == 1) {
        ret = -ENOSYS;
        goto exit;
    }

    ret = vad_load_sound_model(stdev,
                               (char *)sound_model + sound_model->data_offset,
                               sound_model->data_size);
    if (ret)
        goto exit;

    stdev->model_handle = 1;
    stdev->sound_model_callback = callback;
    stdev->sound_model_cookie = cookie;
    *handle = stdev->model_handle;

exit:
    pthread_mutex_unlock(&stdev->lock);
    return ret;
}

static int stdev_unload_sound_model(const struct sound_trigger_hw_device *dev,
                                    sound_model_handle_t handle)
{
    struct dragon_sound_trigger_device *stdev =
                                   (struct dragon_sound_trigger_device *)dev;
    int status = 0;

    ALOGI("%s handle %d", __func__, handle);
    pthread_mutex_lock(&stdev->lock);
    if (handle != 1) {
        status = -EINVAL;
        goto exit;
    }
    if (stdev->model_handle == 0) {
        status = -ENOSYS;
        goto exit;
    }
    stdev->model_handle = 0;
    free(stdev->config);
    stdev->config = NULL;
    if (stdev->recognition_callback != NULL) {
        stdev->recognition_callback = NULL;
        if (stdev->send_sock >=0)
            write(stdev->send_sock, "T", 1);
        pthread_mutex_unlock(&stdev->lock);

        pthread_join(stdev->callback_thread, (void **)NULL);

        pthread_mutex_lock(&stdev->lock);
    }

exit:
    stdev_dsp_set_power(stdev, 0);

    pthread_mutex_unlock(&stdev->lock);
    return status;
}

static int stdev_start_recognition(const struct sound_trigger_hw_device *dev,
                                   sound_model_handle_t sound_model_handle,
                                   const struct sound_trigger_recognition_config *config,
                                   recognition_callback_t callback,
                                   void *cookie)
{
    struct dragon_sound_trigger_device *stdev =
                                  (struct dragon_sound_trigger_device *)dev;
    int status = 0;

    ALOGI("%s sound model %d", __func__, sound_model_handle);
    pthread_mutex_lock(&stdev->lock);
    if (stdev->model_handle != sound_model_handle) {
        status = -ENOSYS;
        goto exit;
    }
    if (stdev->recognition_callback != NULL) {
        status = -ENOSYS;
        goto exit;
    }

    free(stdev->config);
    stdev->config = NULL;
    if (config) {
        stdev->config = malloc(sizeof(*config));
        if (!stdev->config) {
            status = -ENOMEM;
            goto exit;
        }
        memcpy(stdev->config, config, sizeof(*config));
    }

    if (stdev->pcm) {
        pcm_close(stdev->pcm);
        stdev->pcm = NULL;
    }

    stdev_dsp_set_power(stdev, 0);

    stdev->recognition_callback = callback;
    stdev->recognition_cookie = cookie;
    pthread_create(&stdev->callback_thread, (const pthread_attr_t *) NULL,
                        callback_thread_loop, stdev);
exit:
    pthread_mutex_unlock(&stdev->lock);
    return status;
}

static int stdev_stop_recognition(const struct sound_trigger_hw_device *dev,
                                  sound_model_handle_t sound_model_handle)
{
    struct dragon_sound_trigger_device *stdev =
                                 (struct dragon_sound_trigger_device *)dev;
    int status = 0;

    ALOGI("%s sound model %d", __func__, sound_model_handle);
    pthread_mutex_lock(&stdev->lock);
    if (stdev->model_handle != sound_model_handle) {
        status = -ENOSYS;
        goto exit;
    }
    if (stdev->recognition_callback == NULL) {
        status = -ENOSYS;
        goto exit;
    }
    free(stdev->config);
    stdev->config = NULL;
    stdev->recognition_callback = NULL;
    if (stdev->send_sock >=0)
        write(stdev->send_sock, "T", 1);
    pthread_mutex_unlock(&stdev->lock);

    pthread_join(stdev->callback_thread, (void **)NULL);

    if (stdev->pcm) {
        pcm_close(stdev->pcm);
        stdev->pcm = NULL;
    }

    pthread_mutex_lock(&stdev->lock);

exit:
    stdev_dsp_set_power(stdev, 0);

    pthread_mutex_unlock(&stdev->lock);
    return status;
}

__attribute__ ((visibility ("default")))
int sound_trigger_open_for_streaming()
{
    struct dragon_sound_trigger_device *stdev = &g_stdev;
    int ret = 0;

    pthread_mutex_lock(&stdev->lock);

    if (!stdev->opened) {
        ALOGE("%s: stdev has not been opened", __func__);
        ret = -EFAULT;
        goto exit;
    }
    if (!stdev->is_streaming) {
        ALOGE("%s: DSP is not currently streaming", __func__);
        ret = -EBUSY;
        goto exit;
    }
    if (!stdev->pcm) {
        ALOGE("%s: PCM is not open", __func__);
        ret = -EINVAL;
        goto exit;
    }
    // TODO: Probably want to get something from whoever called us to bind to it/assert that it's a
    // valid connection. Perhaps returning a more
    // meaningful handle would be a good idea as well.
    ret = 1;
exit:
    pthread_mutex_unlock(&stdev->lock);
    return ret;
}

__attribute__ ((visibility ("default")))
size_t sound_trigger_read_samples(int audio_handle, void *buffer, size_t  buffer_len)
{
    struct dragon_sound_trigger_device *stdev = &g_stdev;
    int i;
    int ret = 0;
    int frames;
    int orig_frames;
    unsigned int offset;
    unsigned int bytes_mmapped;
    int16_t* snd_buffer;
    unsigned int retry_attempts = 0;

    if (audio_handle <= 0) {
        ALOGE("%s: invalid audio handle", __func__);
        return -EINVAL;
    }

    pthread_mutex_lock(&stdev->lock);

    if (!stdev->opened) {
        ALOGE("%s: stdev has not been opened", __func__);
        ret = -EFAULT;
        goto exit;
    }
    if (!stdev->is_streaming) {
        ALOGE("%s: DSP is not currently streaming", __func__);
        ret = -EINVAL;
        goto exit;
    }
    if (!stdev->pcm) {
        ALOGE("%s: PCM has closed", __func__);
        ret = -EINVAL;
        goto exit;
    }


read_again:
    frames = pcm_mmap_avail(stdev->pcm);
    if (frames < 0) {
        ALOGE("%s: DSP mmap error %d", __func__, frames);
        ret = frames;
        goto exit;
    }
    if (frames == 0) {
//        ALOGE("%s: DSP mmap retry %d", __func__, frames);
        retry_attempts++;
        if (retry_attempts > 10)
            goto exit;
        usleep(35000);
        goto read_again;
    }

    if (frames > buffer_len / 2)
        frames = buffer_len / 2;

    orig_frames = frames;

    ret = pcm_mmap_begin(stdev->pcm, (void**)&snd_buffer, &offset, &frames);
    if (ret < 0) {
        ALOGE("Failed to mmap hotword buffer %d", ret);
        goto exit;
    }
    memcpy(buffer, snd_buffer + offset, frames * 2);
    pcm_mmap_commit(stdev->pcm, offset, frames);
    ret = frames * 2;
    ALOGV("%s: Sent %u frames to buffer", __func__, frames);

exit:
    pthread_mutex_unlock(&stdev->lock);
    return ret;
}

__attribute__ ((visibility ("default")))
int sound_trigger_close_for_streaming(int audio_handle)
{
    struct dragon_sound_trigger_device *stdev = &g_stdev;

    if (audio_handle <= 0) {
        ALOGE("%s: invalid audio handle", __func__);
        return -EINVAL;
    }

    if (stdev->pcm) {
        pcm_close(stdev->pcm);
        stdev->pcm = NULL;
    }

    return 0;
}

static int stdev_close(hw_device_t *device)
{
    struct dragon_sound_trigger_device *stdev =
                                (struct dragon_sound_trigger_device *)device;
    int ret = 0;

    pthread_mutex_lock(&stdev->lock);
    if (!stdev->opened) {
        ALOGE("%s: device already closed", __func__);
        ret = -EFAULT;
        goto exit;
    }
    stdev_close_mixer(stdev);
    stdev->model_handle = 0;
    stdev->send_sock = 0;
    stdev->term_sock = 0;
    stdev->opened = false;

exit:
    pthread_mutex_unlock(&stdev->lock);
    return ret;
}

static int stdev_open(const hw_module_t *module, const char *name,
                      hw_device_t **device)
{
    struct dragon_sound_trigger_device *stdev;
    int ret;

    if (strcmp(name, SOUND_TRIGGER_HARDWARE_INTERFACE) != 0)
        return -EINVAL;

    stdev = &g_stdev;
    pthread_mutex_lock(&stdev->lock);

    if (stdev->opened) {
        ALOGE("%s: Only one sountrigger can be opened at a time", __func__);
        ret = -EBUSY;
        goto exit;
    }

    ret = stdev_init_mixer(stdev);
    if (ret) {
        ALOGE("Error mixer init");
        goto exit;
    }

    stdev->device.common.tag = HARDWARE_DEVICE_TAG;
    stdev->device.common.version = SOUND_TRIGGER_DEVICE_API_VERSION_1_0;
    stdev->device.common.module = (struct hw_module_t *)module;
    stdev->device.common.close = stdev_close;
    stdev->device.get_properties = stdev_get_properties;
    stdev->device.load_sound_model = stdev_load_sound_model;
    stdev->device.unload_sound_model = stdev_unload_sound_model;
    stdev->device.start_recognition = stdev_start_recognition;
    stdev->device.stop_recognition = stdev_stop_recognition;
    stdev->send_sock = stdev->term_sock = -1;
    stdev->opened = true;

    *device = &stdev->device.common; /* same address as stdev */
exit:
    pthread_mutex_unlock(&stdev->lock);
    return ret;
}

static struct hw_module_methods_t hal_module_methods = {
    .open = stdev_open,
};

struct sound_trigger_module HAL_MODULE_INFO_SYM = {
    .common = {
        .tag = HARDWARE_MODULE_TAG,
        .module_api_version = SOUND_TRIGGER_MODULE_API_VERSION_1_0,
        .hal_api_version = HARDWARE_HAL_API_VERSION,
        .id = SOUND_TRIGGER_HARDWARE_MODULE_ID,
        .name = "Default sound trigger HAL",
        .author = "The Android Open Source Project",
        .methods = &hal_module_methods,
    },
};
