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
    bool modemLogsEnabled = 0;
    std::string modemLogDir = android::base::GetProperty(MODEM_LOG_LOC_PROPERTY, "");
    if (modemLogDir.empty()) {
        ALOGD("No modem log place is set\n");
        return;
    }
    /* Check if smlog_dump tool exist */
    if (!PropertiesHelper::IsUserBuild() && !access("/vendor/bin/smlog_dump", X_OK)) {
        modemLogsEnabled = android::base::GetBoolProperty(MODEM_LOGGING_SWITCH, false);

        /* Execute SMLOG DUMP if SMLOG is enabled */
        if (modemLogsEnabled) {
            CommandOptions options = CommandOptions::WithTimeout(120).Build();
            std::string modemLogAllDir = modemLogDir + "/modem_log";
            std::vector<std::string> rilAndNetmgrLogs
                {
                 "/data/misc/radio/ril_log",
                 "/data/misc/radio/ril_log_old",
                 "/data/misc/netmgr/netmgr_log",
                 "/data/misc/netmgr/netmgr_log_old"
                };
            std::string modemLogMkDirCmd= "/vendor/bin/mkdir " + modemLogAllDir;
            RunCommandToFd(fd, "MKDIR MODEM LOG", { "/vendor/bin/sh", "-c", modemLogMkDirCmd.c_str()}, options);
            RunCommandToFd(fd, "SMLOG DUMP", { "smlog_dump", "-d", "-o", modemLogAllDir.c_str() }, options);
            for (std::string logFile : rilAndNetmgrLogs)
            {
              std::string copyCmd= "/vendor/bin/cp " + logFile + " " + modemLogAllDir;
              RunCommandToFd(fd, "MV MODEM LOG", { "/vendor/bin/sh", "-c", copyCmd.c_str()}, options);
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

    DumpFileToFd(fd, "CPU present", "/sys/devices/system/cpu/present");
    DumpFileToFd(fd, "CPU online", "/sys/devices/system/cpu/online");
    DumpFileToFd(fd, "INTERRUPTS", "/proc/interrupts");

    DumpFileToFd(fd, "UFS model", "/sys/block/sda/device/model");
    DumpFileToFd(fd, "UFS rev", "/sys/block/sda/device/rev");
    DumpFileToFd(fd, "UFS size", "/sys/block/sda/size");
    DumpFileToFd(fd, "UFS heath", "/sys/devices/soc/624000.ufshc/health");
    RunCommandToFd(fd, "UFS dump", {"/vendor/bin/sh", "-c", "for f in $(find /sys/kernel/debug/ufshcd0 -type f); do if [[ -r $f && -f $f ]]; then echo --- $f; cat $f; fi; done"});

    DumpFileToFd(fd, "RPM Stats", "/d/rpm_stats");
    DumpFileToFd(fd, "Power Management Stats", "/d/rpm_master_stats");
    DumpFileToFd(fd, "WLAN Power Stats", "/d/wlan_wcnss/power_stats");
    DumpFileToFd(fd, "Runtime-PM Stats", "/d/cnss_runtime_pm");
    DumpFileToFd(fd, "CNSS Pre-Alloc", "/d/cnss-prealloc/status");

    DumpFileToFd(fd, "SMD Log", "/d/ipc_logging/smd/log");
    RunCommandToFd(fd, "ION HEAPS", {"/vendor/bin/sh", "-c", "for d in $(ls -d /d/ion/*); do for f in $(ls $d); do echo --- $d/$f; cat $d/$f; done; done"});
    DumpFileToFd(fd, "dmabuf info", "/d/dma_buf/bufinfo");
    RunCommandToFd(fd, "Temperatures", {"/vendor/bin/sh", "-c", "for f in `ls /sys/class/thermal` ; do type=`cat /sys/class/thermal/$f/type` ; temp=`cat /sys/class/thermal/$f/temp` ; echo \"$type: $temp\" ; done"});
    DumpFileToFd(fd, "cpu0-1 time-in-state", "/sys/devices/system/cpu/cpu0/cpufreq/stats/time_in_state");
    RunCommandToFd(fd, "cpu0-1 cpuidle", {"/vendor/bin/sh", "-c", "for d in $(ls -d /sys/devices/system/cpu/cpu0/cpuidle/state*); do echo \"$d: `cat $d/name` `cat $d/desc` `cat $d/time` `cat $d/usage`\"; done"});
    DumpFileToFd(fd, "cpu2-3 time-in-state", "/sys/devices/system/cpu/cpu2/cpufreq/stats/time_in_state");
    RunCommandToFd(fd, "cpu2-3 cpuidle", {"/vendor/bin/sh", "-c", "for d in $(ls -d /sys/devices/system/cpu/cpu2/cpuidle/state*); do echo \"$d: `cat $d/name` `cat $d/desc` `cat $d/time` `cat $d/usage`\"; done"});
    DumpFileToFd(fd, "MDP xlogs", "/d/mdp/xlog/dump");
    DumpFileToFd(fd, "TCPM logs", "/d/tcpm/9-0022");
    DumpFileToFd(fd, "FUSB302 logs", "/d/ipc_logging/fusb302/log");

    /* Check if qsee_logger tool exists */
    if (!access("/vendor/bin/qsee_logger", X_OK)) {
      RunCommandToFd(fd, "FP LOGS", {"qsee_logger", "-d"});
    }

    return Void();
};

}  // namespace implementation
}  // namespace V1_0
}  // namespace dumpstate
}  // namespace hardware
}  // namespace android
