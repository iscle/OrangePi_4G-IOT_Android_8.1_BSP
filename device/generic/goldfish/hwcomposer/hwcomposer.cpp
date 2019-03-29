/*
 * Copyright (C) 2012 The Android Open Source Project
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
#include <pthread.h>
#include <stdlib.h>
#include <sys/time.h>
#include <sys/resource.h>
#include <unistd.h>

#include <log/log.h>
#include <hardware/hwcomposer.h>
#include <sync/sync.h>

struct ranchu_hwc_composer_device_1 {
    hwc_composer_device_1_t base; // constant after init
    const hwc_procs_t *procs;     // constant after init
    pthread_t vsync_thread;       // constant after init
    int32_t vsync_period_ns;      // constant after init
    framebuffer_device_t* fbdev;  // constant after init

    pthread_mutex_t vsync_lock;
    bool vsync_callback_enabled; // protected by this->vsync_lock
};

static int hwc_prepare(hwc_composer_device_1_t* dev __unused,
                       size_t numDisplays, hwc_display_contents_1_t** displays) {

    if (!numDisplays || !displays) return 0;

    hwc_display_contents_1_t* contents = displays[HWC_DISPLAY_PRIMARY];

    if (!contents) return 0;

    for (size_t i = 0; i < contents->numHwLayers; i++) {
    // We do not handle any layers, so set composition type of any non
    // HWC_FRAMEBUFFER_TARGET layer to to HWC_FRAMEBUFFER.
        if (contents->hwLayers[i].compositionType == HWC_FRAMEBUFFER_TARGET) {
            continue;
        }
        contents->hwLayers[i].compositionType = HWC_FRAMEBUFFER;
    }
    return 0;
}

static int hwc_set(struct hwc_composer_device_1* dev,size_t numDisplays,
                   hwc_display_contents_1_t** displays) {
    struct ranchu_hwc_composer_device_1* pdev = (struct ranchu_hwc_composer_device_1*)dev;
    if (!numDisplays || !displays) {
        return 0;
    }

    hwc_display_contents_1_t* contents = displays[HWC_DISPLAY_PRIMARY];

    int retireFenceFd = -1;
    int err = 0;
    for (size_t layer = 0; layer < contents->numHwLayers; layer++) {
            hwc_layer_1_t* fb_layer = &contents->hwLayers[layer];

        int releaseFenceFd = -1;
        if (fb_layer->acquireFenceFd > 0) {
            const int kAcquireWarningMS= 3000;
            err = sync_wait(fb_layer->acquireFenceFd, kAcquireWarningMS);
            if (err < 0 && errno == ETIME) {
                ALOGE("hwcomposer waited on fence %d for %d ms",
                      fb_layer->acquireFenceFd, kAcquireWarningMS);
            }
            close(fb_layer->acquireFenceFd);

            if (fb_layer->compositionType != HWC_FRAMEBUFFER_TARGET) {
                ALOGE("hwcomposer found acquire fence on layer %d which is not an"
                      "HWC_FRAMEBUFFER_TARGET layer", layer);
            }

            releaseFenceFd = dup(fb_layer->acquireFenceFd);
            fb_layer->acquireFenceFd = -1;
        }

        if (fb_layer->compositionType != HWC_FRAMEBUFFER_TARGET) {
            continue;
        }

        pdev->fbdev->post(pdev->fbdev, fb_layer->handle);
        fb_layer->releaseFenceFd = releaseFenceFd;

        if (releaseFenceFd > 0) {
            if (retireFenceFd == -1) {
                retireFenceFd = dup(releaseFenceFd);
            } else {
                int mergedFenceFd = sync_merge("hwc_set retireFence",
                                               releaseFenceFd, retireFenceFd);
                close(retireFenceFd);
                retireFenceFd = mergedFenceFd;
            }
        }
    }

    contents->retireFenceFd = retireFenceFd;
    return err;
}

static int hwc_query(struct hwc_composer_device_1* dev, int what, int* value) {
    struct ranchu_hwc_composer_device_1* pdev =
            (struct ranchu_hwc_composer_device_1*)dev;

    switch (what) {
        case HWC_BACKGROUND_LAYER_SUPPORTED:
            // we do not support the background layer
            value[0] = 0;
            break;
        case HWC_VSYNC_PERIOD:
            value[0] = pdev->vsync_period_ns;
            break;
        default:
            // unsupported query
            ALOGE("%s badness unsupported query what=%d", __FUNCTION__, what);
            return -EINVAL;
    }
    return 0;
}

static int hwc_event_control(struct hwc_composer_device_1* dev, int dpy __unused,
                             int event, int enabled) {
    struct ranchu_hwc_composer_device_1* pdev =
            (struct ranchu_hwc_composer_device_1*)dev;
    int ret = -EINVAL;

    // enabled can only be 0 or 1
    if (!(enabled & ~1)) {
        if (event == HWC_EVENT_VSYNC) {
            pthread_mutex_lock(&pdev->vsync_lock);
            pdev->vsync_callback_enabled=enabled;
            pthread_mutex_unlock(&pdev->vsync_lock);
            ret = 0;
        }
    }
    return ret;
}

static int hwc_blank(struct hwc_composer_device_1* dev __unused, int disp,
                     int blank __unused) {
    if (disp != HWC_DISPLAY_PRIMARY) {
        return -EINVAL;
    }
    return 0;
}

static void hwc_dump(hwc_composer_device_1* dev __unused, char* buff __unused,
                     int buff_len __unused) {
    // This is run when running dumpsys.
    // No-op for now.
}


static int hwc_get_display_configs(struct hwc_composer_device_1* dev __unused,
                                   int disp, uint32_t* configs, size_t* numConfigs) {
    if (*numConfigs == 0) {
        return 0;
    }

    if (disp == HWC_DISPLAY_PRIMARY) {
        configs[0] = 0;
        *numConfigs = 1;
        return 0;
    }

    return -EINVAL;
}


static int32_t hwc_attribute(struct ranchu_hwc_composer_device_1* pdev,
                             const uint32_t attribute) {
    switch(attribute) {
        case HWC_DISPLAY_VSYNC_PERIOD:
            return pdev->vsync_period_ns;
        case HWC_DISPLAY_WIDTH:
            return pdev->fbdev->width;
        case HWC_DISPLAY_HEIGHT:
            return pdev->fbdev->height;
        case HWC_DISPLAY_DPI_X:
            return pdev->fbdev->xdpi*1000;
        case HWC_DISPLAY_DPI_Y:
            return pdev->fbdev->ydpi*1000;
        default:
            ALOGE("unknown display attribute %u", attribute);
            return -EINVAL;
    }
}

static int hwc_get_display_attributes(struct hwc_composer_device_1* dev __unused,
                                      int disp, uint32_t config __unused,
                                      const uint32_t* attributes, int32_t* values) {

    struct ranchu_hwc_composer_device_1* pdev = (struct ranchu_hwc_composer_device_1*)dev;
    for (int i = 0; attributes[i] != HWC_DISPLAY_NO_ATTRIBUTE; i++) {
        if (disp == HWC_DISPLAY_PRIMARY) {
            values[i] = hwc_attribute(pdev, attributes[i]);
        } else {
            ALOGE("unknown display type %u", disp);
            return -EINVAL;
        }
    }

    return 0;
}

static int hwc_close(hw_device_t* dev) {
    struct ranchu_hwc_composer_device_1* pdev = (struct ranchu_hwc_composer_device_1*)dev;
    pthread_kill(pdev->vsync_thread, SIGTERM);
    pthread_join(pdev->vsync_thread, NULL);
    free(dev);
    return 0;
}

static void* hwc_vsync_thread(void* data) {
    struct ranchu_hwc_composer_device_1* pdev = (struct ranchu_hwc_composer_device_1*)data;
    setpriority(PRIO_PROCESS, 0, HAL_PRIORITY_URGENT_DISPLAY);

    struct timespec rt;
    if (clock_gettime(CLOCK_MONOTONIC, &rt) == -1) {
        ALOGE("%s:%d error in vsync thread clock_gettime: %s",
              __FILE__, __LINE__, strerror(errno));
    }
    const int log_interval = 60;
    int64_t last_logged = rt.tv_sec;
    int sent = 0;
    int last_sent = 0;
    bool vsync_enabled = false;
    struct timespec wait_time;
    wait_time.tv_sec = 0;
    wait_time.tv_nsec = pdev->vsync_period_ns;

    while (true) {
        int err = nanosleep(&wait_time, NULL);
        if (err == -1) {
            if (errno == EINTR) {
                break;
            }
            ALOGE("error in vsync thread: %s", strerror(errno));
        }

        pthread_mutex_lock(&pdev->vsync_lock);
        vsync_enabled = pdev->vsync_callback_enabled;
        pthread_mutex_unlock(&pdev->vsync_lock);

        if (!vsync_enabled) {
            continue;
        }

        if (clock_gettime(CLOCK_MONOTONIC, &rt) == -1) {
            ALOGE("%s:%d error in vsync thread clock_gettime: %s",
                  __FILE__, __LINE__, strerror(errno));
        }

        int64_t timestamp = int64_t(rt.tv_sec) * 1e9 + rt.tv_nsec;
        pdev->procs->vsync(pdev->procs, 0, timestamp);
        if (rt.tv_sec - last_logged >= log_interval) {
            ALOGD("hw_composer sent %d syncs in %ds", sent - last_sent, rt.tv_sec - last_logged);
            last_logged = rt.tv_sec;
            last_sent = sent;
        }
        ++sent;
    }

    return NULL;
}

static void hwc_register_procs(struct hwc_composer_device_1* dev,
                               hwc_procs_t const* procs) {
    struct ranchu_hwc_composer_device_1* pdev = (struct ranchu_hwc_composer_device_1*)dev;
    pdev->procs = procs;
}

static int hwc_open(const struct hw_module_t* module, const char* name,
                    struct hw_device_t** device) {
    int ret = 0;

    if (strcmp(name, HWC_HARDWARE_COMPOSER)) {
        ALOGE("%s called with bad name %s", __FUNCTION__, name);
        return -EINVAL;
    }

    ranchu_hwc_composer_device_1 *pdev = new ranchu_hwc_composer_device_1();
    if (!pdev) {
        ALOGE("%s failed to allocate dev", __FUNCTION__);
        return -ENOMEM;
    }

    pdev->base.common.tag = HARDWARE_DEVICE_TAG;
    pdev->base.common.version = HWC_DEVICE_API_VERSION_1_1;
    pdev->base.common.module = const_cast<hw_module_t *>(module);
    pdev->base.common.close = hwc_close;

    pdev->base.prepare = hwc_prepare;
    pdev->base.set = hwc_set;
    pdev->base.eventControl = hwc_event_control;
    pdev->base.blank = hwc_blank;
    pdev->base.query = hwc_query;
    pdev->base.registerProcs = hwc_register_procs;
    pdev->base.dump = hwc_dump;
    pdev->base.getDisplayConfigs = hwc_get_display_configs;
    pdev->base.getDisplayAttributes = hwc_get_display_attributes;

    pdev->vsync_period_ns = 1000*1000*1000/60; // vsync is 60 hz

    hw_module_t const* hw_module;
    ret = hw_get_module(GRALLOC_HARDWARE_MODULE_ID, &hw_module);
    if (ret != 0) {
        ALOGE("ranchu_hw_composer hwc_open %s module not found", GRALLOC_HARDWARE_MODULE_ID);
        return ret;
    }
    ret = framebuffer_open(hw_module, &pdev->fbdev);
    if (ret != 0) {
        ALOGE("ranchu_hw_composer hwc_open could not open framebuffer");
    }

    pthread_mutex_init(&pdev->vsync_lock, NULL);
    pdev->vsync_callback_enabled = false;

    ret = pthread_create (&pdev->vsync_thread, NULL, hwc_vsync_thread, pdev);
    if (ret) {
        ALOGE("ranchu_hw_composer could not start vsync_thread\n");
    }

    *device = &pdev->base.common;

    return ret;
}


static struct hw_module_methods_t hwc_module_methods = {
    open: hwc_open,
};

hwc_module_t HAL_MODULE_INFO_SYM = {
    common: {
        tag: HARDWARE_MODULE_TAG,
        module_api_version: HWC_MODULE_API_VERSION_0_1,
        hal_api_version: HARDWARE_HAL_API_VERSION,
        id: HWC_HARDWARE_MODULE_ID,
        name: "Android Emulator hwcomposer module",
        author: "The Android Open Source Project",
        methods: &hwc_module_methods,
    }
};
