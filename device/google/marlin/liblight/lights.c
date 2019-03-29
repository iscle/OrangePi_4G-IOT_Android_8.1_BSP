/*
 * Copyright (C) 2008 The Android Open Source Project
 * Copyright (C) 2014 The  Linux Foundation. All rights reserved.
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

#include <cutils/log.h>
#include <cutils/properties.h>

#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>

#include <linux/msm_mdp.h>

#include <sys/ioctl.h>
#include <sys/types.h>

#include <hardware/lights.h>

/*
 * Change this to 1 to support battery notifications via BatteryService
 */
#define LIGHTS_SUPPORT_BATTERY 0
#define CG_COLOR_ID_PROPERTY "ro.boot.hardware.color"

static pthread_once_t g_init = PTHREAD_ONCE_INIT;
static pthread_mutex_t g_lock = PTHREAD_MUTEX_INITIALIZER;
static struct light_state_t g_notification;
static struct light_state_t g_battery;
static int g_last_backlight_mode = BRIGHTNESS_MODE_USER;
static int g_attention = 0;
static int rgb_brightness_ratio = 255;

char const*const RED_LED_FILE
        = "/sys/class/leds/red/brightness";

char const*const GREEN_LED_FILE
        = "/sys/class/leds/green/brightness";

char const*const BLUE_LED_FILE
        = "/sys/class/leds/blue/brightness";

char const*const LCD_FILE
        = "/sys/class/leds/lcd-backlight/brightness";

char const*const PERSISTENCE_FILE
        = "/sys/class/leds/lcd-backlight/low_persistence";

char const*const RED_BLINK_FILE
        = "/sys/class/leds/red/blink";

char const*const GREEN_BLINK_FILE
        = "/sys/class/leds/green/blink";

char const*const BLUE_BLINK_FILE
        = "/sys/class/leds/blue/blink";

char const*const RED_ON_OFF_MS_FILE
        = "/sys/class/leds/red/on_off_ms";

char const*const GREEN_ON_OFF_MS_FILE
        = "/sys/class/leds/green/on_off_ms";

char const*const BLUE_ON_OFF_MS_FILE
        = "/sys/class/leds/blue/on_off_ms";

char const*const RED_RGB_START_FILE
        = "/sys/class/leds/red/rgb_start";

char const*const GREEN_RGB_START_FILE
        = "/sys/class/leds/green/rgb_start";

char const*const BLUE_RGB_START_FILE
        = "/sys/class/leds/blue/rgb_start";

/**
 * device methods
 */

void init_globals(void)
{
    char color_id_prop[PROPERTY_VALUE_MAX] = {""};

    // init the mutex
    pthread_mutex_init(&g_lock, NULL);

    // check CG color
    property_get(CG_COLOR_ID_PROPERTY, color_id_prop, "DEF00");
    if (strcmp(color_id_prop, "GRA00") == 0) {
        rgb_brightness_ratio = 25;
    } else if (strcmp(color_id_prop, "SLV00") == 0) {
        rgb_brightness_ratio = 15;
    } else if (strcmp(color_id_prop, "BLU00") == 0) {
        rgb_brightness_ratio = 15;
    } else {
        rgb_brightness_ratio = 20;
    }
}

static int
write_int(char const* path, int value)
{
    int fd;
    static int already_warned = 0;

    fd = open(path, O_WRONLY);
    if (fd >= 0) {
        char buffer[20];
        size_t bytes = snprintf(buffer, sizeof(buffer), "%d\n", value);
        if(bytes >= sizeof(buffer)) return -EINVAL;
        ssize_t amt = write(fd, buffer, bytes);
        close(fd);
        return amt == -1 ? -errno : 0;
    } else {
        if (already_warned == 0) {
            ALOGE("write_int failed to open %s\n", path);
            already_warned = 1;
        }
        return -errno;
    }
}

static int
write_double_int(char const* path, int value1, int value2)
{
    int fd;
    static int already_warned = 0;

    fd = open(path, O_WRONLY);
    if (fd >= 0) {
        char buffer[20];
        size_t bytes = snprintf(buffer, sizeof(buffer), "%d %d\n", value1, value2);
        if(bytes >= sizeof(buffer)) return -EINVAL;
        ssize_t amt = write(fd, buffer, bytes);
        close(fd);
        return amt == -1 ? -errno : 0;
    } else {
        if (already_warned == 0) {
            ALOGE("write_int failed to open %s\n", path);
            already_warned = 1;
        }
        return -errno;
    }
}

static int
is_lit(struct light_state_t const* state)
{
    return state->color & 0x00ffffff;
}

static int
rgb_to_brightness(struct light_state_t const* state)
{
    int color = state->color & 0x00ffffff;
    return ((77*((color>>16)&0x00ff))
            + (150*((color>>8)&0x00ff)) + (29*(color&0x00ff))) >> 8;
}

static int
set_light_backlight(struct light_device_t* dev,
        struct light_state_t const* state)
{
    int err = 0;
    int brightness = rgb_to_brightness(state);
    unsigned int lpEnabled = state->brightnessMode == BRIGHTNESS_MODE_LOW_PERSISTENCE;
    if(!dev) {
        return -1;
    }

    pthread_mutex_lock(&g_lock);

    // If we're not in lp mode and it has been enabled or if we are in lp mode
    // and it has been disabled send an ioctl to the display with the update
    if ((g_last_backlight_mode != state->brightnessMode && lpEnabled) ||
            (!lpEnabled && g_last_backlight_mode == BRIGHTNESS_MODE_LOW_PERSISTENCE)) {
        if ((err = write_int(PERSISTENCE_FILE, lpEnabled)) != 0) {
            ALOGE("%s: Failed to write to %s: %s\n", __FUNCTION__, PERSISTENCE_FILE,
                    strerror(errno));
        }
    }

    g_last_backlight_mode = state->brightnessMode;

    if (!err) {
        err = write_int(LCD_FILE, brightness);
    }

    pthread_mutex_unlock(&g_lock);
    return err;
}

static int
set_speaker_light_locked(struct light_device_t* dev,
        struct light_state_t const* state)
{
    int red, green, blue;
    int blink;
    int onMS, offMS;
    unsigned int colorRGB;

    if(!dev) {
        return -1;
    }

    switch (state->flashMode) {
        case LIGHT_FLASH_TIMED:
            onMS = state->flashOnMS;
            offMS = state->flashOffMS;
            break;
        case LIGHT_FLASH_NONE:
        default:
            onMS = 0;
            offMS = 0;
            break;
    }

    colorRGB = state->color;

#if 0
    ALOGD("set_speaker_light_locked mode %d, colorRGB=%08X, onMS=%d, offMS=%d\n",
            state->flashMode, colorRGB, onMS, offMS);
#endif

    red = ((colorRGB >> 16) & 0xFF) * rgb_brightness_ratio / 255;
    green = ((colorRGB >> 8) & 0xFF) * rgb_brightness_ratio / 255;
    blue = (colorRGB & 0xFF) * rgb_brightness_ratio / 255;

    write_double_int(RED_ON_OFF_MS_FILE, onMS, offMS);
    write_int(RED_LED_FILE, red);
    write_double_int(GREEN_ON_OFF_MS_FILE, onMS, offMS);
    write_int(GREEN_LED_FILE, green);
    write_double_int(BLUE_ON_OFF_MS_FILE, onMS, offMS);
    write_int(BLUE_LED_FILE, blue);

    if(!write_int(RED_RGB_START_FILE, 1))
        if(!write_int(GREEN_RGB_START_FILE, 1))
            if(!write_int(BLUE_RGB_START_FILE, 1))
                return -1;

    return 0;
}

static void
handle_speaker_battery_locked(struct light_device_t* dev)
{
    if (is_lit(&g_battery)) {
        set_speaker_light_locked(dev, &g_battery);
    } else {
        set_speaker_light_locked(dev, &g_notification);
    }
}

#if LIGHTS_SUPPORT_BATTERY
static int
set_light_battery(struct light_device_t* dev,
        struct light_state_t const* state)
{
    pthread_mutex_lock(&g_lock);
    g_battery = *state;
    handle_speaker_battery_locked(dev);
    pthread_mutex_unlock(&g_lock);
    return 0;
}
#endif

static int
set_light_notifications(struct light_device_t* dev,
        struct light_state_t const* state)
{
    pthread_mutex_lock(&g_lock);
    g_notification = *state;
    handle_speaker_battery_locked(dev);
    pthread_mutex_unlock(&g_lock);
    return 0;
}

static int
set_light_attention(struct light_device_t* dev,
        struct light_state_t const* state)
{
    pthread_mutex_lock(&g_lock);
    if (state->flashMode == LIGHT_FLASH_HARDWARE) {
        g_attention = state->flashOnMS;
    } else if (state->flashMode == LIGHT_FLASH_NONE) {
        g_attention = 0;
    }
    handle_speaker_battery_locked(dev);
    pthread_mutex_unlock(&g_lock);
    return 0;
}

/** Close the lights device */
static int
close_lights(struct light_device_t *dev)
{
    if (dev) {
        free(dev);
    }
    return 0;
}


/******************************************************************************/

/**
 * module methods
 */

/** Open a new instance of a lights device using name */
static int open_lights(const struct hw_module_t* module, char const* name,
        struct hw_device_t** device)
{
    int (*set_light)(struct light_device_t* dev,
            struct light_state_t const* state);

    if (0 == strcmp(LIGHT_ID_BACKLIGHT, name))
        set_light = set_light_backlight;
#if LIGHTS_SUPPORT_BATTERY
    else if (0 == strcmp(LIGHT_ID_BATTERY, name))
        set_light = set_light_battery;
#endif
    else if (0 == strcmp(LIGHT_ID_NOTIFICATIONS, name))
        set_light = set_light_notifications;
    else if (0 == strcmp(LIGHT_ID_ATTENTION, name))
        set_light = set_light_attention;
    else
        return -EINVAL;

    pthread_once(&g_init, init_globals);

    struct light_device_t *dev = malloc(sizeof(struct light_device_t));

    if(!dev)
        return -ENOMEM;

    memset(dev, 0, sizeof(*dev));

    dev->common.tag = HARDWARE_DEVICE_TAG;
    dev->common.version = LIGHTS_DEVICE_API_VERSION_2_0;
    dev->common.module = (struct hw_module_t*)module;
    dev->common.close = (int (*)(struct hw_device_t*))close_lights;
    dev->set_light = set_light;

    *device = (struct hw_device_t*)dev;
    return 0;
}

static struct hw_module_methods_t lights_module_methods = {
    .open =  open_lights,
};

/*
 * The lights Module
 */
struct hw_module_t HAL_MODULE_INFO_SYM = {
    .tag = HARDWARE_MODULE_TAG,
    .version_major = 1,
    .version_minor = 0,
    .id = LIGHTS_HARDWARE_MODULE_ID,
    .name = "lights Module",
    .author = "Google, Inc.",
    .methods = &lights_module_methods,
};
