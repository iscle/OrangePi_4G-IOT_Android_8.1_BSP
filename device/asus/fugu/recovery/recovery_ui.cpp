/*
 * Copyright (C) 2011 The Android Open Source Project
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

#include <errno.h>
#include <fcntl.h>
#include <linux/fb.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/time.h>
#include <sys/types.h>
#include <time.h>
#include <unistd.h>

#include <string>

#include "common.h"
#include "device.h"
#include "ui.h"
#include "screen_ui.h"

#define kFBDevice "/dev/graphics/fb0"

#define FBIO_PSB_SET_RGBX       _IOWR('F', 0x42, struct fb_var_screeninfo)
#define FBIO_PSB_SET_RMODE      _IOWR('F', 0x43, struct fb_var_screeninfo)

class FuguUI : public ScreenRecoveryUI {
  public:
    bool Init(const std::string& locale) override {
        SetupDisplayMode();
        return ScreenRecoveryUI::Init(locale);
    }

    void SetupDisplayMode() {
        printf("opening fb %s\n", kFBDevice);
        int fb_dev = open(kFBDevice, O_RDWR);
        if (fb_dev == -1) {
            fprintf(stderr, "FAIL: failed to open \"%s\": %s\n", kFBDevice, strerror(errno));
            return;
        }

        struct fb_var_screeninfo current_mode;
        if (ioctl(fb_dev, FBIO_PSB_SET_RMODE, &current_mode) == -1) {
            fprintf(stderr, "FAIL: unable to set RGBX mode on display controller: %s\n",
                    strerror(errno));
            return;
        }

        if (ioctl(fb_dev, FBIOGET_VSCREENINFO, &current_mode) == -1) {
            fprintf(stderr, "FAIL: unable to get mode: %s\n", strerror(errno));
            return;
        }

        if (ioctl(fb_dev, FBIOBLANK, FB_BLANK_POWERDOWN) == -1) {
            fprintf(stderr, "FAIL: unable to blank display: %s\n", strerror(errno));
            return;
        }

        current_mode.bits_per_pixel = 32;
        current_mode.red.offset = 0;
        current_mode.red.length = 8;
        current_mode.green.offset = 8;
        current_mode.green.length = 8;
        current_mode.blue.offset = 16;
        current_mode.blue.length = 8;

        if (ioctl(fb_dev, FBIOPUT_VSCREENINFO, &current_mode) == -1) {
            fprintf(stderr, "FAIL: unable to set mode: %s\n", strerror(errno));
            return;
        }

        if (ioctl(fb_dev, FBIO_PSB_SET_RGBX, &current_mode) == -1) {
            fprintf(stderr, "FAIL: unable to set RGBX mode on display controller: %s\n",
                    strerror(errno));
            return;
        }

        if (ioctl(fb_dev, FBIOBLANK, FB_BLANK_UNBLANK) == -1) {
            fprintf(stderr, "FAIL: unable to unblank display: %s\n", strerror(errno));
            return;
        }
    }
};

Device* make_device() {
    return new Device(new FuguUI);
}
