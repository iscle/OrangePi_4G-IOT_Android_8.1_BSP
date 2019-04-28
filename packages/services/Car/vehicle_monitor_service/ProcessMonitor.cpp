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
#define LOG_TAG "ProcessMonitor"

#include <mutex>
#include <sys/param.h>
#include <dirent.h>

#include "ProcessMonitor.h"

#define DBG_VERBOSE
#ifdef DBG_VERBOSE
#define LOG_VERBOSE(x...) ALOGD(x)
#else
#define LOG_VERBOSE(x...)
#endif

#define MAX_LINE 256
#define SELF_IO  "/proc/self/io"
#define NUM_PROC_DUMP 10

namespace android {

static bool procDeltaCpuCmp(const std::pair<pid_t, const std::shared_ptr<ProcInfo>> a,
                            const std::pair<pid_t, const std::shared_ptr<ProcInfo>> b) {
    return a.second->delta_time > b.second->delta_time;
}

static bool procDeltaMemCmp(const std::pair<pid_t, const std::shared_ptr<ProcInfo>> a,
                            const std::pair<pid_t, const std::shared_ptr<ProcInfo>> b) {
    return a.second->delta_rss > b.second->delta_rss;
}

static bool procDeltaWbytesCmp(const std::pair<pid_t, const std::shared_ptr<ProcInfo>> a,
                               const std::pair<pid_t, const std::shared_ptr<ProcInfo>> b) {
    return a.second->delta_wbytes > b.second->delta_wbytes;
}

static bool procCpuCmp(const std::pair<pid_t, const std::shared_ptr<ProcInfo>> a,
                       const std::pair<pid_t, const std::shared_ptr<ProcInfo>> b) {
    return (a.second->utime + a.second->utime) > (b.second->stime + b.second->stime);
}

static bool procMemCmp(const std::pair<pid_t, const std::shared_ptr<ProcInfo>> a,
                      const std::pair<pid_t, const std::shared_ptr<ProcInfo>> b) {
    return a.second->rss > b.second->rss;
}

static bool procWbytesCmp(const std::pair<pid_t, const std::shared_ptr<ProcInfo>> a,
                      const std::pair<pid_t, const std::shared_ptr<ProcInfo>> b) {
    return a.second->wbytes > b.second->wbytes;
}

ProcessMonitor::ProcessMonitor() {
    //TODO: read config from policy files.
    if (access(SELF_IO, F_OK) == -1) {
        mIoSupported = false;
        ALOGE("**** DISK I/O PROFILING DISABLED!!!!****\n"
              "Kernel doesn't support I/O profiling.");
    } else {
        mIoSupported = true;
    }
}

ProcessMonitor::~ProcessMonitor() {
}

void ProcessMonitor::dump(String8& msg) {
    std::shared_lock<std::shared_timed_mutex> lock(mMutex);
    msg.append("ProcessMonitor\n");
    msg.appendFormat("Processes count: %d\n", (int) mProcInfoMap.size());
    msg.append("Top CPU usage\n");
    dumpTopProcesses(msg, procCpuCmp);
    msg.append("Top CPU usage increase\n");
    dumpTopProcesses(msg, procDeltaCpuCmp);
    msg.append("Top memory usage\n");
    dumpTopProcesses(msg, procMemCmp);
    msg.append("Top memory usage increase\n");
    dumpTopProcesses(msg, procDeltaMemCmp);
    if (mIoSupported) {
        msg.append("Top disk IO \n");
        dumpTopProcesses(msg, procWbytesCmp);
        msg.append("Top disk IO increase \n");
        dumpTopProcesses(msg, procDeltaWbytesCmp);
    } else {
        msg.append("Disk IO monitoring not supported.\n");
    }
}

void ProcessMonitor::dumpTopProcesses(
        String8& msg,
        bool (*procCmpFn) (
                const std::pair<pid_t, const std::shared_ptr<ProcInfo>>,
                const std::pair<pid_t, const std::shared_ptr<ProcInfo>>)) {

    std::vector<std::pair<pid_t, std::shared_ptr<ProcInfo>>> topPids(NUM_PROC_DUMP);
    std::partial_sort_copy(mProcInfoMap.begin(),
                           mProcInfoMap.end(),
                           topPids.begin(),
                           topPids.end(),
                           *procCmpFn);
    for (auto it = topPids.begin(); it != topPids.end(); ++it) {
        msg.appendFormat("(%s) PID: %d: delta_time: %" PRIu64 ", delta_rss: %" PRIu64 ", "
                         "delta_wbytes: %" PRIu64 ", utime: %" PRIu64" , stime: %" PRIu64 ", "
                         "rss: %" PRIu64 ", wbytes: %" PRIu64 "\n",
                         it->second->name.c_str(),
                         it->first,
                         it->second->delta_time,
                         it->second->delta_rss,
                         it->second->delta_wbytes,
                         it->second->utime,
                         it->second->stime,
                         it->second->rss,
                         it->second->wbytes);
    }

}

status_t ProcessMonitor::setAppPriority(uint32_t , uint32_t, uint32_t) {
    std::unique_lock<std::shared_timed_mutex> lock(mMutex);
    // TODO implement.
    return NO_ERROR;
}

status_t ProcessMonitor::process() {
    status_t status = updateProcessInfo();
    if (status != NO_ERROR) {
        return status;
    }
    return improveSystemHealth();
}

status_t ProcessMonitor::improveSystemHealth() {
    // TODO: implement policy enforcer. kill apps that abuse system.
    return NO_ERROR;
}

status_t ProcessMonitor::updateProcessInfo() {
    std::unique_lock<std::shared_timed_mutex> lock(mMutex);
    std::set<pid_t> oldPids;
    populateExistingPids(oldPids);
    DIR *procDir;
    procDir = opendir("/proc");
    if (!procDir) {
        ALOGE("Failed to open /proc dir");
        return PERMISSION_DENIED;
    }
    struct dirent *pidDir;
    pid_t pid;
    while ((pidDir = readdir(procDir))) {
        if (!isdigit(pidDir->d_name[0])) {
            continue;
        }
        pid = atoi(pidDir->d_name);
        updateOrAddProcess(pid);
        oldPids.erase(pid);
    }
    deleteOutdatedPids(oldPids);
    return NO_ERROR;
}

void ProcessMonitor::deleteOutdatedPids(std::set<pid_t>& pidSet) {
    for(auto it = pidSet.begin(); it != pidSet.end(); ++it) {
        LOG_VERBOSE("Process %d ended. Removing from process map", *it);
        mProcInfoMap.erase(*it);
    }
}

void ProcessMonitor::populateExistingPids(std::set<pid_t>& pidSet) {
    for(auto it = mProcInfoMap.begin(); it != mProcInfoMap.end(); ++it) {
        pidSet.insert(it->first);
    }
}

void ProcessMonitor::updateOrAddProcess(pid_t pid) {
    auto pidDataIt = mProcInfoMap.find(pid);
    std::shared_ptr<ProcInfo> pidData;
    if (pidDataIt == mProcInfoMap.end()) {
        pidData = std::make_shared<ProcInfo>();
        mProcInfoMap.insert(std::pair<pid_t, std::shared_ptr<ProcInfo>>(pid, pidData));
    } else {
        pidData = pidDataIt->second;
    }
    auto originalPidData = std::make_shared<ProcInfo>(*pidData);
    readStat(pidData, pid);
    if (mIoSupported) {
        readIo(pidData, pid);
    }
    readCmdline(pidData, pid);
    readStatus(pidData, pid);
    updateDiffs(pidData, originalPidData);
}

void ProcessMonitor::readStat(std::shared_ptr<ProcInfo> pidData, pid_t pid) {
    char filename[64];
    sprintf(filename, "/proc/%d/stat", pid);
    FILE *file;
    file = fopen(filename, "r");
    if (!file) {
        ALOGD("Failed to open file %s for reading", filename);
        return;
    }
    fscanf(file,
           "%*d %*s %*c %*d %*d %*d %*d %*d %*d %*d %*d %*d %*d "
           "%" SCNu64
           "%" SCNu64 "%*d %*d %*d %*d %*d %*d %*d "
           "%*" SCNu64
           "%" SCNu64 "%*d %*d %*d %*d %*d %*d %*d %*d %*d %*d %*d %*d %*d %*d "
           "%*d",
           &pidData->utime,
           &pidData->stime,
           &pidData->rss);
    fclose(file);
}

void ProcessMonitor::readIo(std::shared_ptr<ProcInfo> pidData, pid_t pid) {
    char filename[64];
    sprintf(filename, "/proc/%d/io", pid);
    FILE *file;
    file = fopen(filename, "r");
    if (!file) {
        ALOGD("Failed to open file %s for reading", filename);
        return;
    }
    char buf[MAX_LINE];
    while (fgets(buf, MAX_LINE, file)) {
        sscanf(buf, "write_bytes: %" PRIu64, &pidData->wbytes);
    }
    fclose(file);
}

void ProcessMonitor::readCmdline(std::shared_ptr<ProcInfo> pidData, pid_t pid) {
    char filename[64];
    sprintf(filename, "/proc/%d/cmdline", pid);
    FILE *file;
    file = fopen(filename, "r");
    if (!file) {
        ALOGD("Failed to open file %s for reading", filename);
        return;
    }
    char buf[MAXPATHLEN];
    fgets(buf, MAXPATHLEN, file);
    fclose(file);
    if (strlen(buf) > 0) {
        pidData->name.assign(buf);
    }
}

void ProcessMonitor::readStatus(std::shared_ptr<ProcInfo> pidData, pid_t pid) {
    char filename[64];
    sprintf(filename, "/proc/%d/status", pid);
    FILE *file;
    file = fopen(filename, "r");
    if (!file) {
        ALOGD("Failed to open file %s for reading", filename);
        return;
    }
    char line[MAX_LINE];
    unsigned int uid;
    while (fgets(line, MAX_LINE, file)) {
        sscanf(line, "Uid: %u", &uid);
    }
    fclose(file);
    pidData->uid = uid;

}

void ProcessMonitor::updateDiffs(std::shared_ptr<ProcInfo> pidData,
                 std::shared_ptr<ProcInfo> oldPidData) {
    pidData->delta_utime = pidData->utime - oldPidData->utime;
    pidData->delta_stime = pidData->stime - oldPidData->stime;
    pidData->delta_time = pidData->delta_utime + pidData->delta_stime;
    pidData->delta_rss = pidData->rss - oldPidData->rss;
    pidData->delta_wbytes = pidData->wbytes - oldPidData->wbytes;
}

}; // namespace android
