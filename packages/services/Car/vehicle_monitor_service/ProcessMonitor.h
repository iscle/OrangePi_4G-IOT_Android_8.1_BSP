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
#ifndef CAR_PROCESS_MONITOR_H_
#define CAR_PROCESS_MONITOR_H_

#include <map>
#include <memory>
#include <set>
#include <string>
#include <shared_mutex>

#include <inttypes.h>
#include <cutils/compiler.h>

#include <binder/BinderService.h>
#include <binder/IBinder.h>
#include <utils/String8.h>

#include <IVehicleMonitor.h>
#include <HandlerThread.h>

namespace android {

// ----------------------------------------------------------------------------
struct ProcInfo {
    pid_t pid;
    uid_t uid;
    std::string name;
    uint64_t utime;
    uint64_t stime;
    uint64_t rss;
    uint64_t wbytes;
    int64_t delta_utime;
    int64_t delta_stime;
    int64_t delta_time;
    int64_t delta_rss;
    int64_t delta_wbytes;
};

// ----------------------------------------------------------------------------

// This class is used to collect information about running processes.
// It also enforces certain policies to running apps - ex. kills non-system,
// non-foreground applications that use too much memory, CPU, or write too much
// data to disk.
class ProcessMonitor {
public:
    ProcessMonitor();
    ~ProcessMonitor();

    void dump(String8& msg);
    status_t setAppPriority(uint32_t pid, uint32_t uid, uint32_t priority);
    status_t process();

private:
    status_t updateProcessInfo();
    void populateExistingPids(std::set<pid_t>& pidSet);
    void deleteOutdatedPids(std::set<pid_t>& pidSet);
    void updateOrAddProcess(pid_t pid);
    void readStat(std::shared_ptr<ProcInfo> pidData, pid_t pid);
    void readIo(std::shared_ptr<ProcInfo> pidData, pid_t pid);
    void readCmdline(std::shared_ptr<ProcInfo> pidData, pid_t pid);
    void readStatus(std::shared_ptr<ProcInfo> pidData, pid_t pid);
    void updateDiffs(std::shared_ptr<ProcInfo> pidData,
                     std::shared_ptr<ProcInfo> oldPidData);
    status_t improveSystemHealth();
    void dumpTopProcesses(String8& msg,
        bool (*procCmpFn) (
                const std::pair<pid_t, const std::shared_ptr<ProcInfo>>,
                const std::pair<pid_t, const std::shared_ptr<ProcInfo>>));


private:
    std::map<pid_t, std::shared_ptr<ProcInfo>> mProcInfoMap;
    bool mIoSupported;
    mutable std::shared_timed_mutex mMutex;
};

}

#endif /* CAR_PROCESS_MONITOR_H_ */
