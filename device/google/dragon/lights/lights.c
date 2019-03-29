/*
 * Copyright (C) 2014 The Android Open Source Project
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

#define LOG_TAG "lights"

#include <cutils/log.h>

#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <fcntl.h>

#include <sys/ioctl.h>
#include <sys/types.h>

#include <hardware/lights.h>
#include <hardware/hardware.h>

#define ALOG_ONCE(mask, op, ...)			\
	do {					\
		if (!(mask & op)) {		\
			ALOGE(__VA_ARGS__);	\
			mask |= op;		\
		}				\
	} while (0);
#define OP_WRITE_OPEN		(1 << 0)
#define OP_BRIGHTNESS_PATH	(1 << 1)
#define OP_BRIGHTNESS_VALUE	(1 << 2)
#define OP_BRIGHTNESS_WRITE	(1 << 3)
#define OP_MAX_BRIGHTNESS_PATH	(1 << 4)
#define OP_MAX_BRIGHTNESS_OPEN	(1 << 5)
#define OP_MAX_BRIGHTNESS_READ	(1 << 6)

struct dragon_lights {
	struct light_device_t base;

	pthread_mutex_t lock;
	char const *sysfs_path;

	int max_brightness;

	unsigned long logged_failures;
};

static char const * kBacklightPath =
	"/sys/class/backlight/lpm102a188a-backlight";
static const int kNumBrightnessLevels = 16;
static const int kBrightnessLevels[] =
	{8, 25, 30, 40, 55, 65, 75, 85, 95, 105, 120, 135, 160, 180, 200, 220};

static struct dragon_lights *to_dragon_lights(struct light_device_t *dev)
{
	return (struct dragon_lights *)dev;
}

static int write_brightness(struct dragon_lights *lights, int brightness)
{
	char buffer[20], path[PATH_MAX];
	int fd, bytes, amt, ret = 0;

	bytes = snprintf(path, sizeof(path), "%s/brightness",
			 lights->sysfs_path);
	if (bytes < 0 || (size_t)bytes >= sizeof(path)) {
		ALOG_ONCE(lights->logged_failures, OP_BRIGHTNESS_PATH,
			  "failed to create brightness path %d\n", bytes);
		return -EINVAL;
	}

	fd = open(path, O_RDWR);
	if (fd < 0) {
		ALOG_ONCE(lights->logged_failures, OP_WRITE_OPEN,
			  "write_int failed to open %s/%d\n", path, errno);
		return -errno;
	}

	bytes = snprintf(buffer, sizeof(buffer), "%d\n", brightness);
	if (bytes < 0 || (size_t)bytes >= sizeof(buffer)) {
		ALOG_ONCE(lights->logged_failures, OP_BRIGHTNESS_VALUE,
			  "failed to create brightness value %d/%d\n",
			  brightness, bytes);
		ret = -EINVAL;
		goto out;
	}
	amt = write(fd, buffer, bytes);
	if (amt != bytes) {
		ALOG_ONCE(lights->logged_failures, OP_BRIGHTNESS_WRITE,
			  "failed to write brightness value %d/%d\n", amt,
			  bytes);
		ret = amt == -1 ? -errno : -EINVAL;
		goto out;
	}

out:
	close(fd);
	return ret;
}

static int read_max_brightness(struct dragon_lights *lights, int *value)
{
	char buffer[20], path[PATH_MAX];
	int ret = 0, fd, bytes;

	bytes = snprintf(path, sizeof(path), "%s/max_brightness",
			 lights->sysfs_path);
	if (bytes < 0 || (size_t)bytes >= sizeof(path)) {
		ALOG_ONCE(lights->logged_failures, OP_MAX_BRIGHTNESS_PATH,
			  "failed to create max_brightness path %d\n", bytes);
		return -EINVAL;
	}

	fd = open(path, O_RDONLY);
	if (fd < 0) {
		ALOG_ONCE(lights->logged_failures, OP_MAX_BRIGHTNESS_OPEN,
			  "failed to open max_brightness %s/%d\n", path, errno);
		return -errno;
	}

	bytes = read(fd, buffer, sizeof(buffer));
	if (bytes <= 0) {
		ALOG_ONCE(lights->logged_failures, OP_MAX_BRIGHTNESS_READ,
			  "failed to read max_brightness %s/%d\n", path, errno);
		ret = -errno;
		goto out;
	}

	*value = atoi(buffer);

out:
	close(fd);
	return ret;
}

static int rgb_to_brightness(struct light_state_t const *state)
{
	int color = state->color & 0x00ffffff;
	return ((77 * ((color >> 16) & 0x00ff))
			+ (150 * ((color >> 8) & 0x00ff)) +
			(29 * (color & 0x00ff))) >> 8;
}

static int set_light_backlight(struct light_device_t *dev,
			       struct light_state_t const *state)
{
	struct dragon_lights *lights = to_dragon_lights(dev);
	int err, brightness_idx;
	int brightness = rgb_to_brightness(state);

	if (brightness > 0) {
		// Get the bin number for brightness (0 to kNumBrightnessLevels - 1)
		brightness_idx = (brightness - 1) * kNumBrightnessLevels / 0xff;

		// Get brightness level
		brightness = kBrightnessLevels[brightness_idx];
	}

	pthread_mutex_lock(&lights->lock);
	err = write_brightness(lights, brightness);
	pthread_mutex_unlock(&lights->lock);

	return err;
}

static int close_lights(struct hw_device_t *dev)
{
	struct dragon_lights *lights = (struct dragon_lights *)dev;
	if (lights)
		free(lights);
	return 0;
}

static int open_lights(const struct hw_module_t *module, char const *name,
		       struct hw_device_t **device)
{
	struct dragon_lights *lights;
	int ret;

	// Only support backlight at the moment
	if (strcmp(LIGHT_ID_BACKLIGHT, name))
		return -EINVAL;

	lights = malloc(sizeof(*lights));
	if (lights == NULL) {
		ALOGE("failed to allocate lights memory");
		return -ENOMEM;
	}
	memset(lights, 0, sizeof(*lights));

	pthread_mutex_init(&lights->lock, NULL);

	lights->sysfs_path = kBacklightPath;

	ret = read_max_brightness(lights, &lights->max_brightness);
	if (ret) {
		close_lights((struct hw_device_t *)lights);
		return ret;
	}

	lights->base.common.tag = HARDWARE_DEVICE_TAG;
	lights->base.common.version = 0;
	lights->base.common.module = (struct hw_module_t *)module;
	lights->base.common.close = close_lights;
	lights->base.set_light = set_light_backlight;

	*device = (struct hw_device_t *)lights;
	return 0;
}

static struct hw_module_methods_t lights_methods = {
	.open =  open_lights,
};

struct hw_module_t HAL_MODULE_INFO_SYM = {
	.tag = HARDWARE_MODULE_TAG,
	.version_major = 1,
	.version_minor = 0,
	.id = LIGHTS_HARDWARE_MODULE_ID,
	.name = "dragon lights module",
	.author = "Google, Inc.",
	.methods = &lights_methods,
};
