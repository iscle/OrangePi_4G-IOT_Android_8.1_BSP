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

#define LOG_TAG "dumpstate"

#include "DumpstateDevice.h"

#include "DumpstateUtil.h"

#include <errno.h>
#include <log/log.h>
#include <fcntl.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>

static const char base64[] =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
static const char pad64 = '=';

using android::os::dumpstate::CommandOptions;
using android::os::dumpstate::DumpFileToFd;
using android::os::dumpstate::RunCommandToFd;

namespace android {
namespace hardware {
namespace dumpstate {
namespace V1_0 {
namespace implementation {

static void base64_output3(int out_fd, const unsigned char *src, int len)
{
    dprintf(out_fd, "%c", base64[src[0] >> 2]);
    dprintf(out_fd, "%c", base64[((src[0] & 0x03) << 4) | (src[1] >> 4)]);
    if (len == 1) {
        dprintf(out_fd, "==");
        return;
    }
    dprintf(out_fd, "%c", base64[((src[1] & 0x0F) << 2) | (src[2] >> 6)]);
    if (len == 2) {
        dprintf(out_fd, "=");
        return;
    }
    dprintf(out_fd, "%c", base64[src[2] & 0x3F]);
}

static void fugu_dump_base64(int out_fd, const char *path)
{

    dprintf(out_fd, "------ (%s) ------\n", path);
    int fd = open(path, O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    if (fd < 0) {
        dprintf(out_fd, "*** %s: %s\n\n", path, strerror(errno));
        return;
    }

    /* buffer size multiple of 3 for ease of use */
    unsigned char buffer[1200];
    int left = 0;
    int count = 0;
    for (;;) {
        int ret = read(fd, &buffer[left], sizeof(buffer) - left);
        if (ret <= 0) {
            break;
        }
        left += ret;
        int ofs = 0;
        while (left > 2) {
            base64_output3(out_fd, &buffer[ofs], 3);
            left -= 3;
            ofs += 3;
            count += 4;
            if (count > 72) {
                dprintf(out_fd, "\n");
                count = 0;
            }
        }
        if (left) {
            memmove(buffer, &buffer[ofs], left);
        }
    }
    close(fd);

    if (!left) {
        dprintf(out_fd, "\n------ end ------\n");
        return;
    }

    /* finish padding */
    count = left;
    while (count < 3) {
        buffer[count++] = 0;
    }
    base64_output3(out_fd, buffer, left);

    dprintf(out_fd, "\n------ end ------\n");
}

// Methods from ::android::hardware::dumpstate::V1_0::IDumpstateDevice follow.
Return<void> DumpstateDevice::dumpstateBoard(const hidl_handle& handle) {
    if (handle == nullptr || handle->numFds < 1) {
        ALOGE("no FDs\n");
        return Void();
    }

    int fd = handle->data[0];
    if (fd < 0) {
        ALOGE("invalid FD: %d\n", handle->data[0]);
        return Void();
    }

    DumpFileToFd(fd, "INTERRUPTS", "/proc/interrupts");
    DumpFileToFd(fd, "last ipanic_console", "/data/dontpanic/ipanic_console");
    DumpFileToFd(fd, "last ipanic_threads", "/data/dontpanic/ipanic_threads");

    fugu_dump_base64(fd, "/dev/snd_atvr_mSBC");
    fugu_dump_base64(fd, "/dev/snd_atvr_pcm");

    return Void();
}

}  // namespace implementation
}  // namespace V1_0
}  // namespace dumpstate
}  // namespace hardware
}  // namespace android
