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

#include <log/log.h>
#include <cutils/properties.h>

#include "DumpstateUtil.h"

using android::os::dumpstate::CommandOptions;
using android::os::dumpstate::DumpFileToFd;
using android::os::dumpstate::RunCommandToFd;

namespace android {
namespace hardware {
namespace dumpstate {
namespace V1_0 {
namespace implementation {

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

    /* ask init.dragon.rc to dump the charging state and wait */
    property_set("debug.bq25892", "dump");
    sleep(1);

    DumpFileToFd(fd, "EC Version", "/sys/class/chromeos/cros_ec/version");
    RunCommandToFd(fd, "FW Version", {"fwtool", "vboot"}, CommandOptions::WithTimeout(5).Build());

    DumpFileToFd(fd, "INTERRUPTS", "/proc/interrupts");
    // This is the file created by setting debug.bq25892.
    DumpFileToFd(fd, "Charger chip registers", "/data/misc/fw_logs/bq25892.txt");

    DumpFileToFd(fd, "Battery gas gauge", "/sys/class/power_supply/bq27742-0/uevent");
    DumpFileToFd(fd, "Touchscreen firmware updater", "/data/misc/touchfwup/rmi4update.txt");
    DumpFileToFd(fd, "Ion heap", "/d/ion/heaps/system");

    return Void();
}

}  // namespace implementation
}  // namespace V1_0
}  // namespace dumpstate
}  // namespace hardware
}  // namespace android
