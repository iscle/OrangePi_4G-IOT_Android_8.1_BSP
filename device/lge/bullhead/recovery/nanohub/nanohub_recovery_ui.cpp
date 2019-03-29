/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <errno.h>

#include "device.h"
#include "screen_ui.h"

class Nanohub_Device : public Device
{
public:
    Nanohub_Device(ScreenRecoveryUI* ui) : Device(ui) {}
    bool PostWipeData();
};

bool Nanohub_Device::PostWipeData()
{
    int fd;

    fd = open("/sys/class/nanohub/nanohub/erase_shared", O_WRONLY);
    if (fd < 0) {
        printf("error: open erase_shared failed: %s\n", strerror(errno));
    } else {
        if (write(fd, "1\n", 2) != 2) {
            printf("error: write to erase_shared failed: %s\n", strerror(errno));
        } else {
            printf("Successfully erased nanoapps.\n");
        }
        close(fd);
    }

    // open/write failure caused by permissions issues would persist across
    // reboots, so always return true to prevent a factory reset failure loop.
    return true;
}

Device *make_device()
{
    return new Nanohub_Device(new ScreenRecoveryUI);
}
