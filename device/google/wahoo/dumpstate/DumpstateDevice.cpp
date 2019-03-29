/*
 * Copyright 2016 The Android Open Source Project
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

#include <android-base/properties.h>
#include <android-base/unique_fd.h>
#include <cutils/properties.h>
#include <libgen.h>
#include <log/log.h>
#include <stdlib.h>
#include <string>

#include "DumpstateUtil.h"

#define MODEM_LOG_PREFIX_PROPERTY "ro.radio.log_prefix"
#define MODEM_LOG_LOC_PROPERTY "ro.radio.log_loc"
#define MODEM_LOGGING_SWITCH "persist.radio.smlog_switch"

#define DIAG_MDLOG_PROPERTY "sys.modem.diag.mdlog"
#define DIAG_MDLOG_STATUS_PROPERTY "sys.modem.diag.mdlog_on"

using android::os::dumpstate::CommandOptions;
using android::os::dumpstate::DumpFileToFd;
using android::os::dumpstate::PropertiesHelper;
using android::os::dumpstate::RunCommandToFd;

namespace android {
namespace hardware {
namespace dumpstate {
namespace V1_0 {
namespace implementation {

void DumpstateDevice::dumpModem(int fd, int fdModem)
{
    std::string modemLogDir = android::base::GetProperty(MODEM_LOG_LOC_PROPERTY, "");
    if (modemLogDir.empty()) {
        ALOGD("No modem log place is set\n");
        return;
    }

    if (!PropertiesHelper::IsUserBuild()) {
        bool smlogEnabled = android::base::GetBoolProperty(MODEM_LOGGING_SWITCH, false) &&
                !access("/vendor/bin/smlog_dump", X_OK);

        bool diagLogEnabled = android::base::GetBoolProperty(DIAG_MDLOG_PROPERTY, false);

        CommandOptions options = CommandOptions::WithTimeout(120).Build();
        std::string modemLogAllDir = modemLogDir + "/modem_log";
        std::string diagLogDir = "/data/vendor/radio/diag_logs/logs";
        std::vector<std::string> rilAndNetmgrLogs
            {
              "/data/vendor/radio/ril_log",
              "/data/vendor/radio/ril_log_old",
              "/data/vendor/netmgr/netmgr_log",
              "/data/vendor/netmgr/netmgr_log_old"
            };

        std::string modemLogMkDirCmd= "/vendor/bin/mkdir -p " + modemLogAllDir;
        RunCommandToFd(fd, "MKDIR MODEM LOG", { "/vendor/bin/sh", "-c", modemLogMkDirCmd.c_str()}, options);

        if (smlogEnabled) {
            RunCommandToFd(fd, "SMLOG DUMP", { "smlog_dump", "-d", "-o", modemLogAllDir.c_str() }, options);
        } else if (diagLogEnabled) {
            std::string copyCmd= "/vendor/bin/cp -rf " + diagLogDir + " " + modemLogAllDir;

            android::base::SetProperty(DIAG_MDLOG_PROPERTY, "false");

            ALOGD("Waiting for diag log to exit\n");
            for (int i = 0; i < 30; i++) {
                if (!android::base::GetBoolProperty(DIAG_MDLOG_STATUS_PROPERTY, false)) {
                    ALOGD("diag log exited\n");
                    sleep(1);
                    break;
                }

                sleep(1);
            }

            RunCommandToFd(fd, "CP DIAG LOGS", { "/vendor/bin/sh", "-c", copyCmd.c_str()}, options);

            android::base::SetProperty(DIAG_MDLOG_PROPERTY, "true");
        }

        for (const auto& logFile : rilAndNetmgrLogs)
        {
            std::string copyCmd= "/vendor/bin/cp " + logFile + " " + modemLogAllDir;
            RunCommandToFd(fd, "CP MODEM LOG", { "/vendor/bin/sh", "-c", copyCmd.c_str()}, options);
        }

        std::string filePrefix = android::base::GetProperty(MODEM_LOG_PREFIX_PROPERTY, "");

        if (!filePrefix.empty()) {
            std::string modemLogCombined = modemLogDir + "/" + filePrefix + "all.tar";
            std::string modemLogTarCmd= "/vendor/bin/tar cvf " + modemLogCombined + " -C " + modemLogAllDir + " .";
            RunCommandToFd(fd, "TAR LOG", { "/vendor/bin/sh", "-c", modemLogTarCmd.c_str()}, options);

            std::string modemLogPermCmd= "/vendor/bin/chmod a+rw " + modemLogCombined;
            RunCommandToFd(fd, "CHG PERM", { "/vendor/bin/sh", "-c", modemLogPermCmd.c_str()}, options);

            std::vector<uint8_t> buffer(65536);
            android::base::unique_fd fdLog(TEMP_FAILURE_RETRY(open(modemLogCombined.c_str(), O_RDONLY | O_CLOEXEC | O_NONBLOCK)));

            if (fdLog >= 0) {
                while (1) {
                    ssize_t bytes_read = TEMP_FAILURE_RETRY(read(fdLog, buffer.data(), buffer.size()));

                    if (bytes_read == 0) {
                        break;
                    } else if (bytes_read < 0) {
                        ALOGD("read(%s): %s\n", modemLogCombined.c_str(), strerror(errno));
                        break;
                    }

                    ssize_t result = TEMP_FAILURE_RETRY(write(fdModem, buffer.data(), bytes_read));

                    if (result != bytes_read) {
                        ALOGD("Failed to write %ld bytes, actually written: %ld", bytes_read, result);
                        break;
                    }
                }
            }

            std::string modemLogClearCmd= "/vendor/bin/rm -r " + modemLogAllDir;
            RunCommandToFd(fd, "RM MODEM DIR", { "/vendor/bin/sh", "-c", modemLogClearCmd.c_str()}, options);
            RunCommandToFd(fd, "RM LOG", { "/vendor/bin/rm", modemLogCombined.c_str()}, options);
        }
    }
}

static void DumpTouch(int fd) {
    if (!access("/sys/android_touch", R_OK)) {
        DumpFileToFd(fd, "Synaptics touch firmware version",
                     "/sys/android_touch/vendor");
        DumpFileToFd(fd, "Synaptics touch firmware config",
                     "/sys/android_touch/config");
    }
    if (!access("/sys/class/input/ftm4_touch", R_OK)) {
        DumpFileToFd(fd, "STM touch firmware config",
                     "/sys/class/input/ftm4_touch/version");
        DumpFileToFd(fd, "STM touch VR Mode",
                     "/sys/class/input/ftm4_touch/vrmode");
    }
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

    if (handle->numFds < 2) {
        ALOGE("no FD for modem\n");
    }
    else {
        int fdModem = handle->data[1];
        dumpModem(fd, fdModem);
    }

    DumpFileToFd(fd, "SoC serial number", "/sys/devices/soc0/serial_number");
    DumpFileToFd(fd, "CPU present", "/sys/devices/system/cpu/present");
    DumpFileToFd(fd, "CPU online", "/sys/devices/system/cpu/online");
    DumpFileToFd(fd, "UFS model", "/sys/block/sda/device/model");
    DumpFileToFd(fd, "UFS rev", "/sys/block/sda/device/rev");
    DumpFileToFd(fd, "UFS size", "/sys/block/sda/size");
    RunCommandToFd(fd, "UFS health", {"/vendor/bin/sh", "-c", "for f in $(find /sys/kernel/debug/ufshcd0 -type f); do if [[ -r $f && -f $f ]]; then echo --- $f; cat $f; fi; done"});
    DumpFileToFd(fd, "INTERRUPTS", "/proc/interrupts");
    DumpFileToFd(fd, "RPM Stats", "/d/rpm_stats");
    DumpFileToFd(fd, "Power Management Stats", "/d/rpm_master_stats");
    DumpFileToFd(fd, "WLAN Power Stats", "/d/wlan0/power_stats");
    DumpFileToFd(fd, "LL-Stats", "/d/wlan0/ll_stats");
    DumpFileToFd(fd, "ICNSS Stats", "/d/icnss/stats");
    DumpFileToFd(fd, "SMD Log", "/d/ipc_logging/smd/log");
    RunCommandToFd(fd, "ION HEAPS", {"/vendor/bin/sh", "-c", "for d in $(ls -d /d/ion/*); do for f in $(ls $d); do echo --- $d/$f; cat $d/$f; done; done"});
    DumpFileToFd(fd, "dmabuf info", "/d/dma_buf/bufinfo");
    RunCommandToFd(fd, "Temperatures", {"/vendor/bin/sh", "-c", "for f in `ls /sys/class/thermal` ; do type=`cat /sys/class/thermal/$f/type` ; temp=`cat /sys/class/thermal/$f/temp` ; echo \"$type: $temp\" ; done"});
    RunCommandToFd(fd, "Easel debug info", {"/vendor/bin/sh", "-c", "for f in `ls /sys/bus/i2c/devices/9-0008/@(*curr|temperature|vbat|total_power)`; do echo \"$f: `cat $f`\" ; done; file=/sys/devices/virtual/misc/mnh_sm/state; echo \"$file: `cat $file`\""});
    DumpFileToFd(fd, "cpu0-3 time-in-state", "/sys/devices/system/cpu/cpu0/cpufreq/stats/time_in_state");
    RunCommandToFd(fd, "cpu0-3 cpuidle", {"/vendor/bin/sh", "-c", "for d in $(ls -d /sys/devices/system/cpu/cpu0/cpuidle/state*); do echo \"$d: `cat $d/name` `cat $d/desc` `cat $d/time` `cat $d/usage`\"; done"});
    DumpFileToFd(fd, "cpu4-7 time-in-state", "/sys/devices/system/cpu/cpu4/cpufreq/stats/time_in_state");
    RunCommandToFd(fd, "cpu4-7 cpuidle", {"/vendor/bin/sh", "-c", "for d in $(ls -d /sys/devices/system/cpu/cpu4/cpuidle/state*); do echo \"$d: `cat $d/name` `cat $d/desc` `cat $d/time` `cat $d/usage`\"; done"});
    DumpFileToFd(fd, "cpu0-3 thermal limit", "/sys/devices/virtual/thermal/cooling_device0/cur_state");
    DumpFileToFd(fd, "cpu4-7 thermal limit", "/sys/devices/virtual/thermal/cooling_device1/cur_state");
    DumpFileToFd(fd, "MDP xlogs", "/data/vendor/display/mdp_xlog");
    DumpFileToFd(fd, "TCPM logs", "/d/tcpm/usbpd0");
    DumpFileToFd(fd, "PD Engine", "/d/pd_engine/usbpd0");
    DumpFileToFd(fd, "smblib-usb logs", "/d/ipc_logging/smblib/log");
    DumpFileToFd(fd, "ipc-local-ports", "/d/msm_ipc_router/dump_local_ports");
    DumpTouch(fd);
    RunCommandToFd(fd, "USB Device Descriptors", {"/vendor/bin/sh", "-c", "cd /sys/bus/usb/devices/1-1 && cat product && cat bcdDevice; cat descriptors | od -t x1 -w16 -N96"});

    /* Check if qsee_logger tool exists */
    if (!access("/vendor/bin/qsee_logger", X_OK)) {
      RunCommandToFd(fd, "FP LOGS", {"qsee_logger", "-d"});
    }

    RunCommandToFd(fd, "Battery cycle count", {"/vendor/bin/sh", "-c", "for f in 1 2 3 4 5 6 7 8 ; do echo $f > /sys/class/power_supply/bms/cycle_count_id; count=`cat /sys/class/power_supply/bms/cycle_count`; echo \"$f: $count\"; done"});
    return Void();
};

}  // namespace implementation
}  // namespace V1_0
}  // namespace dumpstate
}  // namespace hardware
}  // namespace android
