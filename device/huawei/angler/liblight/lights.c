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

#include <cutils/log.h>

#include <stdint.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <malloc.h>

#include <linux/msm_mdp.h>

#include <sys/ioctl.h>
#include <sys/types.h>

#include <hardware/lights.h>

/******************************************************************************/

/*
 * Change this to 1 to support battery notifications via BatteryService
 */
#define LIGHTS_SUPPORT_BATTERY 0

#define DEFAULT_LOW_PERSISTENCE_MODE_BRIGHTNESS 0x4F

static pthread_once_t g_init = PTHREAD_ONCE_INIT;
static pthread_mutex_t g_lock = PTHREAD_MUTEX_INITIALIZER;
static pthread_mutex_t g_lcd_lock = PTHREAD_MUTEX_INITIALIZER;
static struct light_state_t g_notification;
static int g_last_backlight_mode = BRIGHTNESS_MODE_USER;
#if LIGHTS_SUPPORT_BATTERY
static struct light_state_t g_battery;
#endif

char const*const RED_LED_FILE
        = "/sys/class/leds/red/brightness";

char const*const GREEN_LED_FILE
        = "/sys/class/leds/green/brightness";

char const*const BLUE_LED_FILE
        = "/sys/class/leds/blue/brightness";

char const*const LCD_FILE
        = "/sys/class/leds/lcd-backlight/brightness";

char const*const RED_TIMER_FILE
        = "/sys/class/leds/red/on_off_ms";

char const*const GREEN_TIMER_FILE
        = "/sys/class/leds/green/on_off_ms";

char const*const BLUE_TIMER_FILE
        = "/sys/class/leds/blue/on_off_ms";

char const*const RGB_LOCK_FILE
        = "/sys/class/leds/red/rgb_start";

char const*const DISPLAY_FB_DEV_PATH
        = "/dev/graphics/fb0";

enum led_type {
    LED_NOTIFICATION = 0,
    LED_BATTERY,
};

/**
 * device methods
 */

void init_globals(void)
{
    // init the mutex
    pthread_mutex_init(&g_lock, NULL);
    pthread_mutex_init(&g_lcd_lock, NULL);
}

static int
write_int(char const* path, int value)
{
    int fd;
    static int already_warned = 0;

    fd = open(path, O_WRONLY);
    if (fd >= 0) {
        char buffer[20] = {0,};
        int bytes = snprintf(buffer, sizeof(buffer), "%d\n", value);
        ssize_t amt = write(fd, buffer, (size_t)bytes);
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
write_on_off(char const* path, int on, int off)
{
    int fd;
    static int already_warned = 0;

    fd = open(path, O_WRONLY);
    if (fd >= 0) {
        char buffer[32] = {0,};
        int bytes = snprintf(buffer, sizeof(buffer), "%d %d\n", on, off);
        int amt = write(fd, buffer, bytes);
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
    return ((77 * ((color >> 16) & 0x00ff))
            + (150 * ((color >> 8) & 0x00ff)) + (29 * (color & 0x00ff))) >> 8;
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

    pthread_mutex_lock(&g_lcd_lock);

    // If we're not in lp mode and it has been enabled or if we are in lp mode
    // and it has been disabled send an ioctl to the display with the update
    if ((g_last_backlight_mode != state->brightnessMode && lpEnabled) ||
            (!lpEnabled && g_last_backlight_mode == BRIGHTNESS_MODE_LOW_PERSISTENCE)) {
        int fd = -1;
        fd = open(DISPLAY_FB_DEV_PATH, O_RDWR);
        if (fd >= 0) {
            if ((err = ioctl(fd, MSMFB_SET_PERSISTENCE_MODE, &lpEnabled)) != 0) {
                ALOGE("%s: Failed in ioctl call to %s: %s\n", __FUNCTION__, DISPLAY_FB_DEV_PATH,
                        strerror(errno));
                err = -1;
            }
            close(fd);

            brightness = DEFAULT_LOW_PERSISTENCE_MODE_BRIGHTNESS;
        } else {
            ALOGE("%s: Failed to open %s: %s\n", __FUNCTION__, DISPLAY_FB_DEV_PATH,
                    strerror(errno));
            err = -1;
        }
    }


    g_last_backlight_mode = state->brightnessMode;

    if (!err) {
        err = write_int(LCD_FILE, brightness);
    }

    pthread_mutex_unlock(&g_lcd_lock);
    return err;
}

static int
set_speaker_light_locked(struct light_device_t* dev,
        struct light_state_t const* state, enum led_type type)
{
    int red, green, blue;
    int blink;
    int onMS, offMS;
    unsigned int colorRGB;
    int override = 0;

    if(!dev) {
        return -1;
    }

#if LIGHTS_SUPPORT_BATTERY
    // Ensure that LED notifications override charging LED.
    if (type == LED_BATTERY && is_lit(&g_notification)) {
        state = &g_notification;
        override = 1;
    }

    // When turning off the notification LED, restore the battery
    // notification state.
    if (type == LED_NOTIFICATION && !is_lit(&g_notification)) {
       state = &g_battery;
       override = 1;
    }
#endif

    switch (state->flashMode) {
        case LIGHT_FLASH_TIMED:
        case LIGHT_FLASH_HARDWARE:
            onMS = state->flashOnMS;
            offMS = state->flashOffMS;
            break;
        case LIGHT_FLASH_NONE:
            // if the light is lit, use some time on and no time off
            if (is_lit(state)) {
                onMS = 1;
                offMS = 0;
                break;
            }
            // fall through
        default:
            onMS = 0;
            offMS = 0;
            break;
    }

    colorRGB = state->color;

    ALOGD("set_speaker_light_locked mode %d, colorRGB=%08X, onMS=%d, "
          "offMS=%d, type %s%c\n",
          state->flashMode, colorRGB, onMS, offMS,
          type == LED_BATTERY ? "BATTERY" : "NOTIFICATION",
          override ? '*' : ' ');
    red = (colorRGB >> 16) & 0xFF;
    green = (colorRGB >> 8) & 0xFF;
    blue = colorRGB & 0xFF;

    if (onMS == 0) {
        red = 0;
        green = 0;
        blue = 0;
    }

    write_int(RGB_LOCK_FILE, 0);

    write_int(RED_LED_FILE, red);
    write_int(GREEN_LED_FILE, green);
    write_int(BLUE_LED_FILE, blue);

    write_on_off(RED_TIMER_FILE, onMS, offMS);
    write_on_off(GREEN_TIMER_FILE, onMS, offMS);
    write_on_off(BLUE_TIMER_FILE, onMS, offMS);

    write_int(RGB_LOCK_FILE, 1);

    return 0;
}

#if LIGHTS_SUPPORT_BATTERY
static int
set_light_battery(struct light_device_t* dev,
        struct light_state_t const* state)
{
    if(!dev) {
        return -1;
    }

    pthread_mutex_lock(&g_lock);
    g_battery = *state;
    set_speaker_light_locked(dev, &g_battery, LED_BATTERY);
    pthread_mutex_unlock(&g_lock);
    return 0;
}
#endif

static int
set_light_notifications(struct light_device_t* dev,
        struct light_state_t const* state)
{
    if(!dev) {
        return -1;
    }

    pthread_mutex_lock(&g_lock);
    g_notification = *state;
    set_speaker_light_locked(dev, &g_notification, LED_NOTIFICATION);
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
