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

    DumpFileToFd(fd, "INTERRUPTS", "/proc/interrupts");
    DumpFileToFd(fd, "RPM Stats", "/d/rpm_stats");
    DumpFileToFd(fd, "Power Management Stats", "/d/rpm_master_stats");
    RunCommandToFd(fd, "SUBSYSTEM TOMBSTONES", {"ls", "-l", "/data/tombstones/ramdump"}, CommandOptions::AS_ROOT);
    DumpFileToFd(fd, "BAM DMUX Log", "/d/ipc_logging/bam_dmux/log");
    DumpFileToFd(fd, "SMD Log", "/d/ipc_logging/smd/log");
    DumpFileToFd(fd, "SMD PKT Log", "/d/ipc_logging/smd_pkt/log");
    DumpFileToFd(fd, "IPC Router Log", "/d/ipc_logging/ipc_router/log");
    DumpFileToFd(fd, "Enabled Clocks", "/d/clk/enabled_clocks");
    DumpFileToFd(fd, "wlan", "/sys/module/bcmdhd/parameters/info_string");
    RunCommandToFd(fd, "ION HEAPS", {"/system/bin/sh", "-c", "for d in $(ls -d /d/ion/*); do for f in $(ls $d); do echo --- $d/$f; cat $d/$f; done; done"}, CommandOptions::AS_ROOT);
    RunCommandToFd(fd, "Temperatures", {"/system/bin/sh", "-c", "for f in die_temp emmc_therm msm_therm pa_therm1 quiet_therm ; do echo -n \"$f : \" ; cat /sys/class/hwmon/hwmon1/device/$f ; done ; for f in `ls /sys/class/thermal` ; do type=`cat /sys/class/thermal/$f/type` ; temp=`cat /sys/class/thermal/$f/temp` ; echo \"$type: $temp\" ; done"}, CommandOptions::AS_ROOT);
    DumpFileToFd(fd, "dmesg-ramoops-0", "/sys/fs/pstore/dmesg-ramoops-0");
    DumpFileToFd(fd, "dmesg-ramoops-1", "/sys/fs/pstore/dmesg-ramoops-1");
    DumpFileToFd(fd, "LITTLE cluster time-in-state", "/sys/devices/system/cpu/cpu0/cpufreq/stats/time_in_state");
    RunCommandToFd(fd, "LITTLE cluster cpuidle", {"/system/bin/sh", "-c", "for d in $(ls -d /sys/devices/system/cpu/cpu0/cpuidle/state*); do echo \"$d: `cat $d/name` `cat $d/desc` `cat $d/time` `cat $d/usage`\"; done"}, CommandOptions::AS_ROOT);
    DumpFileToFd(fd, "big cluster time-in-state", "/sys/devices/system/cpu/cpu4/cpufreq/stats/time_in_state");
    RunCommandToFd(fd,"big cluster cpuidle", {"/system/bin/sh", "-c", "for d in $(ls -d /sys/devices/system/cpu/cpu4/cpuidle/state*); do echo \"$d: `cat $d/name` `cat $d/desc` `cat $d/time` `cat $d/usage`\"; done"}, CommandOptions::AS_ROOT);
    DumpFileToFd(fd, "Battery:", "/sys/class/power_supply/bms/uevent");
    RunCommandToFd(fd, "Battery:", {"/system/bin/sh", "-c", "for f in 1 2 3 4 5 6 7 8; do echo $f > /sys/class/power_supply/bms/cycle_count_id; echo \"$f: `cat /sys/class/power_supply/bms/cycle_count`\"; done"}, CommandOptions::AS_ROOT);

    return Void();
}

}  // namespace implementation
}  // namespace V1_0
}  // namespace dumpstate
}  // namespace hardware
}  // namespace android
