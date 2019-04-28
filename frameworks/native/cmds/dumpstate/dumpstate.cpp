/*
 * Copyright (C) 2008 The Android Open Source Project
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

#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <libgen.h>
#include <limits.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/resource.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <sys/wait.h>
#include <unistd.h>
#include <memory>
#include <regex>
#include <set>
#include <string>
#include <vector>

#include <android-base/file.h>
#include <android-base/properties.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <android-base/unique_fd.h>
#include <android/hardware/dumpstate/1.0/IDumpstateDevice.h>
#include <cutils/native_handle.h>
#include <cutils/properties.h>
#include <openssl/sha.h>
#include <private/android_filesystem_config.h>
#include <private/android_logger.h>

#include "DumpstateInternal.h"
#include "DumpstateService.h"
#include "dumpstate.h"

using ::android::hardware::dumpstate::V1_0::IDumpstateDevice;

// TODO: remove once moved to namespace
using android::os::dumpstate::CommandOptions;
using android::os::dumpstate::DumpFileToFd;
using android::os::dumpstate::PropertiesHelper;
using android::os::dumpstate::GetPidByName;

/* read before root is shed */
static char cmdline_buf[16384] = "(unknown)";
static const char *dump_traces_path = NULL;

// TODO: variables and functions below should be part of dumpstate object

static std::set<std::string> mount_points;
void add_mountinfo();

#define PSTORE_LAST_KMSG "/sys/fs/pstore/console-ramoops"
#define ALT_PSTORE_LAST_KMSG "/sys/fs/pstore/console-ramoops-0"
#define BLK_DEV_SYS_DIR "/sys/block"

#define RAFT_DIR "/data/misc/raft"
#define RECOVERY_DIR "/cache/recovery"
#define RECOVERY_DATA_DIR "/data/misc/recovery"
#define LOGPERSIST_DATA_DIR "/data/misc/logd"
#define PROFILE_DATA_DIR_CUR "/data/misc/profiles/cur"
#define PROFILE_DATA_DIR_REF "/data/misc/profiles/ref"
#define WLUTIL "/vendor/xbin/wlutil"

// TODO(narayan): Since this information has to be kept in sync
// with tombstoned, we should just put it in a common header.
//
// File: system/core/debuggerd/tombstoned/tombstoned.cpp
static const std::string TOMBSTONE_DIR = "/data/tombstones/";
static const std::string TOMBSTONE_FILE_PREFIX = "tombstone_";
static const std::string ANR_DIR = "/data/anr/";
static const std::string ANR_FILE_PREFIX = "anr_";

struct DumpData {
    std::string name;
    int fd;
    time_t mtime;
};

static bool operator<(const DumpData& d1, const DumpData& d2) {
    return d1.mtime > d2.mtime;
}

static std::unique_ptr<std::vector<DumpData>> tombstone_data;
static std::unique_ptr<std::vector<DumpData>> anr_data;

// TODO: temporary variables and functions used during C++ refactoring
static Dumpstate& ds = Dumpstate::GetInstance();
static int RunCommand(const std::string& title, const std::vector<std::string>& fullCommand,
                      const CommandOptions& options = CommandOptions::DEFAULT) {
    return ds.RunCommand(title, fullCommand, options);
}
static void RunDumpsys(const std::string& title, const std::vector<std::string>& dumpsysArgs,
                       const CommandOptions& options = Dumpstate::DEFAULT_DUMPSYS,
                       long dumpsysTimeout = 0) {
    return ds.RunDumpsys(title, dumpsysArgs, options, dumpsysTimeout);
}
static int DumpFile(const std::string& title, const std::string& path) {
    return ds.DumpFile(title, path);
}

// Relative directory (inside the zip) for all files copied as-is into the bugreport.
static const std::string ZIP_ROOT_DIR = "FS";

// Must be hardcoded because dumpstate HAL implementation need SELinux access to it
static const std::string kDumpstateBoardPath = "/bugreports/";
static const std::string kDumpstateBoardFiles[] = {
    "dumpstate_board.txt",
    "dumpstate_board.bin"
};
static const int NUM_OF_DUMPS = arraysize(kDumpstateBoardFiles);

static const std::string kLsHalDebugPath = "/bugreports/dumpstate_lshal.txt";

static constexpr char PROPERTY_EXTRA_OPTIONS[] = "dumpstate.options";
static constexpr char PROPERTY_LAST_ID[] = "dumpstate.last_id";
static constexpr char PROPERTY_VERSION[] = "dumpstate.version";
static constexpr char PROPERTY_EXTRA_TITLE[] = "dumpstate.options.title";
static constexpr char PROPERTY_EXTRA_DESCRIPTION[] = "dumpstate.options.description";

static const CommandOptions AS_ROOT_20 = CommandOptions::WithTimeout(20).AsRoot().Build();

/*
 * Returns a vector of dump fds under |dir_path| with a given |file_prefix|.
 * The returned vector is sorted by the mtimes of the dumps. If |limit_by_mtime|
 * is set, the vector only contains files that were written in the last 30 minutes.
 * If |limit_by_count| is set, the vector only contains the ten latest files.
 */
static std::vector<DumpData>* GetDumpFds(const std::string& dir_path,
                                         const std::string& file_prefix,
                                         bool limit_by_mtime,
                                         bool limit_by_count = true) {
    const time_t thirty_minutes_ago = ds.now_ - 60 * 30;

    std::unique_ptr<std::vector<DumpData>> dump_data(new std::vector<DumpData>());
    std::unique_ptr<DIR, decltype(&closedir)> dump_dir(opendir(dir_path.c_str()), closedir);

    struct dirent* entry = nullptr;
    while ((entry = readdir(dump_dir.get()))) {
        if (entry->d_type != DT_REG) {
            continue;
        }

        const std::string base_name(entry->d_name);
        if (base_name.find(file_prefix) != 0) {
            continue;
        }

        const std::string abs_path = dir_path + base_name;
        android::base::unique_fd fd(
            TEMP_FAILURE_RETRY(open(abs_path.c_str(), O_RDONLY | O_CLOEXEC | O_NOFOLLOW | O_NONBLOCK)));
        if (fd == -1) {
            MYLOGW("Unable to open dump file: %s %s\n", abs_path.c_str(), strerror(errno));
            break;
        }

        struct stat st = {};
        if (fstat(fd, &st) == -1) {
            MYLOGW("Unable to stat dump file: %s %s\n", abs_path.c_str(), strerror(errno));
            continue;
        }

        if (limit_by_mtime && st.st_mtime >= thirty_minutes_ago) {
            MYLOGI("Excluding stale dump file: %s\n", abs_path.c_str());
            continue;
        }

        DumpData data = {.name = abs_path, .fd = fd.release(), .mtime = st.st_mtime};

        dump_data->push_back(data);
    }

    std::sort(dump_data->begin(), dump_data->end());

    if (limit_by_count && dump_data->size() > 10) {
        dump_data->erase(dump_data->begin() + 10, dump_data->end());
    }

    return dump_data.release();
}

static bool AddDumps(const std::vector<DumpData>::const_iterator start,
                     const std::vector<DumpData>::const_iterator end,
                     const char* type_name, const bool add_to_zip) {
    bool dumped = false;
    for (auto it = start; it != end; ++it) {
        const std::string& name = it->name;
        const int fd = it->fd;
        dumped = true;
        if (ds.IsZipping() && add_to_zip) {
            if (!ds.AddZipEntryFromFd(ZIP_ROOT_DIR + name, fd)) {
                MYLOGE("Unable to add %s %s to zip file\n", name.c_str(), type_name);
            }
        } else {
            dump_file_from_fd(type_name, name.c_str(), fd);
        }

        close(fd);
    }

    return dumped;
}

// for_each_pid() callback to get mount info about a process.
void do_mountinfo(int pid, const char* name __attribute__((unused))) {
    char path[PATH_MAX];

    // Gets the the content of the /proc/PID/ns/mnt link, so only unique mount points
    // are added.
    snprintf(path, sizeof(path), "/proc/%d/ns/mnt", pid);
    char linkname[PATH_MAX];
    ssize_t r = readlink(path, linkname, PATH_MAX);
    if (r == -1) {
        MYLOGE("Unable to read link for %s: %s\n", path, strerror(errno));
        return;
    }
    linkname[r] = '\0';

    if (mount_points.find(linkname) == mount_points.end()) {
        // First time this mount point was found: add it
        snprintf(path, sizeof(path), "/proc/%d/mountinfo", pid);
        if (ds.AddZipEntry(ZIP_ROOT_DIR + path, path)) {
            mount_points.insert(linkname);
        } else {
            MYLOGE("Unable to add mountinfo %s to zip file\n", path);
        }
    }
}

void add_mountinfo() {
    if (!ds.IsZipping()) return;
    std::string title = "MOUNT INFO";
    mount_points.clear();
    DurationReporter duration_reporter(title, true);
    for_each_pid(do_mountinfo, nullptr);
    MYLOGD("%s: %d entries added to zip file\n", title.c_str(), (int)mount_points.size());
}

static void dump_dev_files(const char *title, const char *driverpath, const char *filename)
{
    DIR *d;
    struct dirent *de;
    char path[PATH_MAX];

    d = opendir(driverpath);
    if (d == NULL) {
        return;
    }

    while ((de = readdir(d))) {
        if (de->d_type != DT_LNK) {
            continue;
        }
        snprintf(path, sizeof(path), "%s/%s/%s", driverpath, de->d_name, filename);
        DumpFile(title, path);
    }

    closedir(d);
}



// dump anrd's trace and add to the zip file.
// 1. check if anrd is running on this device.
// 2. send a SIGUSR1 to its pid which will dump anrd's trace.
// 3. wait until the trace generation completes and add to the zip file.
static bool dump_anrd_trace() {
    unsigned int pid;
    char buf[50], path[PATH_MAX];
    struct dirent *trace;
    struct stat st;
    DIR *trace_dir;
    int retry = 5;
    long max_ctime = 0, old_mtime;
    long long cur_size = 0;
    const char *trace_path = "/data/misc/anrd/";

    if (!ds.IsZipping()) {
        MYLOGE("Not dumping anrd trace because it's not a zipped bugreport\n");
        return false;
    }

    // find anrd's pid if it is running.
    pid = GetPidByName("/system/xbin/anrd");

    if (pid > 0) {
        if (stat(trace_path, &st) == 0) {
            old_mtime = st.st_mtime;
        } else {
            MYLOGE("Failed to find: %s\n", trace_path);
            return false;
        }

        // send SIGUSR1 to the anrd to generate a trace.
        sprintf(buf, "%u", pid);
        if (RunCommand("ANRD_DUMP", {"kill", "-SIGUSR1", buf},
                       CommandOptions::WithTimeout(1).Build())) {
            MYLOGE("anrd signal timed out. Please manually collect trace\n");
            return false;
        }

        while (retry-- > 0 && old_mtime == st.st_mtime) {
            sleep(1);
            stat(trace_path, &st);
        }

        if (retry < 0 && old_mtime == st.st_mtime) {
            MYLOGE("Failed to stat %s or trace creation timeout\n", trace_path);
            return false;
        }

        // identify the trace file by its creation time.
        if (!(trace_dir = opendir(trace_path))) {
            MYLOGE("Can't open trace file under %s\n", trace_path);
        }
        while ((trace = readdir(trace_dir))) {
            if (strcmp(trace->d_name, ".") == 0
                    || strcmp(trace->d_name, "..") == 0) {
                continue;
            }
            sprintf(path, "%s%s", trace_path, trace->d_name);
            if (stat(path, &st) == 0) {
                if (st.st_ctime > max_ctime) {
                    max_ctime = st.st_ctime;
                    sprintf(buf, "%s", trace->d_name);
                }
            }
        }
        closedir(trace_dir);

        // Wait until the dump completes by checking the size of the trace.
        if (max_ctime > 0) {
            sprintf(path, "%s%s", trace_path, buf);
            while(true) {
                sleep(1);
                if (stat(path, &st) == 0) {
                    if (st.st_size == cur_size) {
                        break;
                    } else if (st.st_size > cur_size) {
                        cur_size = st.st_size;
                    } else {
                        return false;
                    }
                } else {
                    MYLOGE("Cant stat() %s anymore\n", path);
                    return false;
                }
            }
            // Add to the zip file.
            if (!ds.AddZipEntry("anrd_trace.txt", path)) {
                MYLOGE("Unable to add anrd_trace file %s to zip file\n", path);
            } else {
                if (remove(path)) {
                    MYLOGE("Error removing anrd_trace file %s: %s", path, strerror(errno));
                }
                return true;
            }
        } else {
            MYLOGE("Can't stats any trace file under %s\n", trace_path);
        }
    }
    return false;
}

static void dump_systrace() {
    if (!ds.IsZipping()) {
        MYLOGD("Not dumping systrace because it's not a zipped bugreport\n");
        return;
    }
    std::string systrace_path = ds.GetPath("-systrace.txt");
    if (systrace_path.empty()) {
        MYLOGE("Not dumping systrace because path is empty\n");
        return;
    }
    const char* path = "/sys/kernel/debug/tracing/tracing_on";
    long int is_tracing;
    if (read_file_as_long(path, &is_tracing)) {
        return; // error already logged
    }
    if (is_tracing <= 0) {
        MYLOGD("Skipping systrace because '%s' content is '%ld'\n", path, is_tracing);
        return;
    }

    MYLOGD("Running '/system/bin/atrace --async_dump -o %s', which can take several minutes",
            systrace_path.c_str());
    if (RunCommand("SYSTRACE", {"/system/bin/atrace", "--async_dump", "-o", systrace_path},
                   CommandOptions::WithTimeout(120).Build())) {
        MYLOGE("systrace timed out, its zip entry will be incomplete\n");
        // TODO: RunCommand tries to kill the process, but atrace doesn't die
        // peacefully; ideally, we should call strace to stop itself, but there is no such option
        // yet (just a --async_stop, which stops and dump
        // if (RunCommand("SYSTRACE", {"/system/bin/atrace", "--kill"})) {
        //   MYLOGE("could not stop systrace ");
        // }
    }
    if (!ds.AddZipEntry("systrace.txt", systrace_path)) {
        MYLOGE("Unable to add systrace file %s to zip file\n", systrace_path.c_str());
    } else {
        if (remove(systrace_path.c_str())) {
            MYLOGE("Error removing systrace file %s: %s", systrace_path.c_str(), strerror(errno));
        }
    }
}

static void dump_raft() {
    if (PropertiesHelper::IsUserBuild()) {
        return;
    }

    std::string raft_path = ds.GetPath("-raft_log.txt");
    if (raft_path.empty()) {
        MYLOGD("raft_path is empty\n");
        return;
    }

    struct stat s;
    if (stat(RAFT_DIR, &s) != 0 || !S_ISDIR(s.st_mode)) {
        MYLOGD("%s does not exist or is not a directory\n", RAFT_DIR);
        return;
    }

    CommandOptions options = CommandOptions::WithTimeout(600).Build();
    if (!ds.IsZipping()) {
        // Write compressed and encoded raft logs to stdout if it's not a zipped bugreport.
        RunCommand("RAFT LOGS", {"logcompressor", "-r", RAFT_DIR}, options);
        return;
    }

    RunCommand("RAFT LOGS", {"logcompressor", "-n", "-r", RAFT_DIR, "-o", raft_path}, options);
    if (!ds.AddZipEntry("raft_log.txt", raft_path)) {
        MYLOGE("Unable to add raft log %s to zip file\n", raft_path.c_str());
    } else {
        if (remove(raft_path.c_str())) {
            MYLOGE("Error removing raft file %s: %s\n", raft_path.c_str(), strerror(errno));
        }
    }
}

static bool skip_not_stat(const char *path) {
    static const char stat[] = "/stat";
    size_t len = strlen(path);
    if (path[len - 1] == '/') { /* Directory? */
        return false;
    }
    return strcmp(path + len - sizeof(stat) + 1, stat); /* .../stat? */
}

static bool skip_none(const char* path __attribute__((unused))) {
    return false;
}

unsigned long worst_write_perf = 20000; /* in KB/s */

//
//  stat offsets
// Name            units         description
// ----            -----         -----------
// read I/Os       requests      number of read I/Os processed
#define __STAT_READ_IOS      0
// read merges     requests      number of read I/Os merged with in-queue I/O
#define __STAT_READ_MERGES   1
// read sectors    sectors       number of sectors read
#define __STAT_READ_SECTORS  2
// read ticks      milliseconds  total wait time for read requests
#define __STAT_READ_TICKS    3
// write I/Os      requests      number of write I/Os processed
#define __STAT_WRITE_IOS     4
// write merges    requests      number of write I/Os merged with in-queue I/O
#define __STAT_WRITE_MERGES  5
// write sectors   sectors       number of sectors written
#define __STAT_WRITE_SECTORS 6
// write ticks     milliseconds  total wait time for write requests
#define __STAT_WRITE_TICKS   7
// in_flight       requests      number of I/Os currently in flight
#define __STAT_IN_FLIGHT     8
// io_ticks        milliseconds  total time this block device has been active
#define __STAT_IO_TICKS      9
// time_in_queue   milliseconds  total wait time for all requests
#define __STAT_IN_QUEUE     10
#define __STAT_NUMBER_FIELD 11
//
// read I/Os, write I/Os
// =====================
//
// These values increment when an I/O request completes.
//
// read merges, write merges
// =========================
//
// These values increment when an I/O request is merged with an
// already-queued I/O request.
//
// read sectors, write sectors
// ===========================
//
// These values count the number of sectors read from or written to this
// block device.  The "sectors" in question are the standard UNIX 512-byte
// sectors, not any device- or filesystem-specific block size.  The
// counters are incremented when the I/O completes.
#define SECTOR_SIZE 512
//
// read ticks, write ticks
// =======================
//
// These values count the number of milliseconds that I/O requests have
// waited on this block device.  If there are multiple I/O requests waiting,
// these values will increase at a rate greater than 1000/second; for
// example, if 60 read requests wait for an average of 30 ms, the read_ticks
// field will increase by 60*30 = 1800.
//
// in_flight
// =========
//
// This value counts the number of I/O requests that have been issued to
// the device driver but have not yet completed.  It does not include I/O
// requests that are in the queue but not yet issued to the device driver.
//
// io_ticks
// ========
//
// This value counts the number of milliseconds during which the device has
// had I/O requests queued.
//
// time_in_queue
// =============
//
// This value counts the number of milliseconds that I/O requests have waited
// on this block device.  If there are multiple I/O requests waiting, this
// value will increase as the product of the number of milliseconds times the
// number of requests waiting (see "read ticks" above for an example).
#define S_TO_MS 1000
//

static int dump_stat_from_fd(const char *title __unused, const char *path, int fd) {
    unsigned long long fields[__STAT_NUMBER_FIELD];
    bool z;
    char *cp, *buffer = NULL;
    size_t i = 0;
    FILE *fp = fdopen(fd, "rb");
    getline(&buffer, &i, fp);
    fclose(fp);
    if (!buffer) {
        return -errno;
    }
    i = strlen(buffer);
    while ((i > 0) && (buffer[i - 1] == '\n')) {
        buffer[--i] = '\0';
    }
    if (!*buffer) {
        free(buffer);
        return 0;
    }
    z = true;
    for (cp = buffer, i = 0; i < (sizeof(fields) / sizeof(fields[0])); ++i) {
        fields[i] = strtoull(cp, &cp, 10);
        if (fields[i] != 0) {
            z = false;
        }
    }
    if (z) { /* never accessed */
        free(buffer);
        return 0;
    }

    if (!strncmp(path, BLK_DEV_SYS_DIR, sizeof(BLK_DEV_SYS_DIR) - 1)) {
        path += sizeof(BLK_DEV_SYS_DIR) - 1;
    }

    printf("%-30s:%9s%9s%9s%9s%9s%9s%9s%9s%9s%9s%9s\n%-30s:\t%s\n", "Block-Dev",
           "R-IOs", "R-merg", "R-sect", "R-wait", "W-IOs", "W-merg", "W-sect",
           "W-wait", "in-fli", "activ", "T-wait", path, buffer);
    free(buffer);

    if (fields[__STAT_IO_TICKS]) {
        unsigned long read_perf = 0;
        unsigned long read_ios = 0;
        if (fields[__STAT_READ_TICKS]) {
            unsigned long long divisor = fields[__STAT_READ_TICKS]
                                       * fields[__STAT_IO_TICKS];
            read_perf = ((unsigned long long)SECTOR_SIZE
                           * fields[__STAT_READ_SECTORS]
                           * fields[__STAT_IN_QUEUE] + (divisor >> 1))
                                        / divisor;
            read_ios = ((unsigned long long)S_TO_MS * fields[__STAT_READ_IOS]
                           * fields[__STAT_IN_QUEUE] + (divisor >> 1))
                                        / divisor;
        }

        unsigned long write_perf = 0;
        unsigned long write_ios = 0;
        if (fields[__STAT_WRITE_TICKS]) {
            unsigned long long divisor = fields[__STAT_WRITE_TICKS]
                                       * fields[__STAT_IO_TICKS];
            write_perf = ((unsigned long long)SECTOR_SIZE
                           * fields[__STAT_WRITE_SECTORS]
                           * fields[__STAT_IN_QUEUE] + (divisor >> 1))
                                        / divisor;
            write_ios = ((unsigned long long)S_TO_MS * fields[__STAT_WRITE_IOS]
                           * fields[__STAT_IN_QUEUE] + (divisor >> 1))
                                        / divisor;
        }

        unsigned queue = (fields[__STAT_IN_QUEUE]
                             + (fields[__STAT_IO_TICKS] >> 1))
                                 / fields[__STAT_IO_TICKS];

        if (!write_perf && !write_ios) {
            printf("%-30s: perf(ios) rd: %luKB/s(%lu/s) q: %u\n", path, read_perf, read_ios, queue);
        } else {
            printf("%-30s: perf(ios) rd: %luKB/s(%lu/s) wr: %luKB/s(%lu/s) q: %u\n", path, read_perf,
                   read_ios, write_perf, write_ios, queue);
        }

        /* bugreport timeout factor adjustment */
        if ((write_perf > 1) && (write_perf < worst_write_perf)) {
            worst_write_perf = write_perf;
        }
    }
    return 0;
}

/* timeout in ms */
static unsigned long logcat_timeout(const char *name) {
    log_id_t id = android_name_to_log_id(name);
    unsigned long property_size = __android_logger_get_buffer_size(id);
    /* Engineering margin is ten-fold our guess */
    return 10 * (property_size + worst_write_perf) / worst_write_perf;
}

void Dumpstate::PrintHeader() const {
    std::string build, fingerprint, radio, bootloader, network;
    char date[80];

    build = android::base::GetProperty("ro.build.display.id", "(unknown)");
    fingerprint = android::base::GetProperty("ro.build.fingerprint", "(unknown)");
    radio = android::base::GetProperty("gsm.version.baseband", "(unknown)");
    bootloader = android::base::GetProperty("ro.bootloader", "(unknown)");
    network = android::base::GetProperty("gsm.operator.alpha", "(unknown)");
    strftime(date, sizeof(date), "%Y-%m-%d %H:%M:%S", localtime(&now_));

    printf("========================================================\n");
    printf("== dumpstate: %s\n", date);
    printf("========================================================\n");

    printf("\n");
    printf("Build: %s\n", build.c_str());
    // NOTE: fingerprint entry format is important for other tools.
    printf("Build fingerprint: '%s'\n", fingerprint.c_str());
    printf("Bootloader: %s\n", bootloader.c_str());
    printf("Radio: %s\n", radio.c_str());
    printf("Network: %s\n", network.c_str());

    printf("Kernel: ");
    DumpFileToFd(STDOUT_FILENO, "", "/proc/version");
    printf("Command line: %s\n", strtok(cmdline_buf, "\n"));
    printf("Bugreport format version: %s\n", version_.c_str());
    printf("Dumpstate info: id=%d pid=%d dry_run=%d args=%s extra_options=%s\n", id_, pid_,
           PropertiesHelper::IsDryRun(), args_.c_str(), extra_options_.c_str());
    printf("\n");
}

// List of file extensions that can cause a zip file attachment to be rejected by some email
// service providers.
static const std::set<std::string> PROBLEMATIC_FILE_EXTENSIONS = {
      ".ade", ".adp", ".bat", ".chm", ".cmd", ".com", ".cpl", ".exe", ".hta", ".ins", ".isp",
      ".jar", ".jse", ".lib", ".lnk", ".mde", ".msc", ".msp", ".mst", ".pif", ".scr", ".sct",
      ".shb", ".sys", ".vb",  ".vbe", ".vbs", ".vxd", ".wsc", ".wsf", ".wsh"
};

bool Dumpstate::AddZipEntryFromFd(const std::string& entry_name, int fd) {
    if (!IsZipping()) {
        MYLOGD("Not adding zip entry %s from fd because it's not a zipped bugreport\n",
               entry_name.c_str());
        return false;
    }
    std::string valid_name = entry_name;

    // Rename extension if necessary.
    size_t idx = entry_name.rfind(".");
    if (idx != std::string::npos) {
        std::string extension = entry_name.substr(idx);
        std::transform(extension.begin(), extension.end(), extension.begin(), ::tolower);
        if (PROBLEMATIC_FILE_EXTENSIONS.count(extension) != 0) {
            valid_name = entry_name + ".renamed";
            MYLOGI("Renaming entry %s to %s\n", entry_name.c_str(), valid_name.c_str());
        }
    }

    // Logging statement  below is useful to time how long each entry takes, but it's too verbose.
    // MYLOGD("Adding zip entry %s\n", entry_name.c_str());
    int32_t err = zip_writer_->StartEntryWithTime(valid_name.c_str(), ZipWriter::kCompress,
                                                  get_mtime(fd, ds.now_));
    if (err != 0) {
        MYLOGE("zip_writer_->StartEntryWithTime(%s): %s\n", valid_name.c_str(),
               ZipWriter::ErrorCodeString(err));
        return false;
    }

    std::vector<uint8_t> buffer(65536);
    while (1) {
        ssize_t bytes_read = TEMP_FAILURE_RETRY(read(fd, buffer.data(), buffer.size()));
        if (bytes_read == 0) {
            break;
        } else if (bytes_read == -1) {
            MYLOGE("read(%s): %s\n", entry_name.c_str(), strerror(errno));
            return false;
        }
        err = zip_writer_->WriteBytes(buffer.data(), bytes_read);
        if (err) {
            MYLOGE("zip_writer_->WriteBytes(): %s\n", ZipWriter::ErrorCodeString(err));
            return false;
        }
    }

    err = zip_writer_->FinishEntry();
    if (err != 0) {
        MYLOGE("zip_writer_->FinishEntry(): %s\n", ZipWriter::ErrorCodeString(err));
        return false;
    }

    return true;
}

bool Dumpstate::AddZipEntry(const std::string& entry_name, const std::string& entry_path) {
    android::base::unique_fd fd(
        TEMP_FAILURE_RETRY(open(entry_path.c_str(), O_RDONLY | O_NONBLOCK | O_CLOEXEC)));
    if (fd == -1) {
        MYLOGE("open(%s): %s\n", entry_path.c_str(), strerror(errno));
        return false;
    }

    return AddZipEntryFromFd(entry_name, fd.get());
}

/* adds a file to the existing zipped bugreport */
static int _add_file_from_fd(const char* title __attribute__((unused)), const char* path, int fd) {
    return ds.AddZipEntryFromFd(ZIP_ROOT_DIR + path, fd) ? 0 : 1;
}

void Dumpstate::AddDir(const std::string& dir, bool recursive) {
    if (!IsZipping()) {
        MYLOGD("Not adding dir %s because it's not a zipped bugreport\n", dir.c_str());
        return;
    }
    MYLOGD("Adding dir %s (recursive: %d)\n", dir.c_str(), recursive);
    DurationReporter duration_reporter(dir, true);
    dump_files("", dir.c_str(), recursive ? skip_none : is_dir, _add_file_from_fd);
}

bool Dumpstate::AddTextZipEntry(const std::string& entry_name, const std::string& content) {
    if (!IsZipping()) {
        MYLOGD("Not adding text zip entry %s because it's not a zipped bugreport\n",
               entry_name.c_str());
        return false;
    }
    MYLOGD("Adding zip text entry %s\n", entry_name.c_str());
    int32_t err = zip_writer_->StartEntryWithTime(entry_name.c_str(), ZipWriter::kCompress, ds.now_);
    if (err != 0) {
        MYLOGE("zip_writer_->StartEntryWithTime(%s): %s\n", entry_name.c_str(),
               ZipWriter::ErrorCodeString(err));
        return false;
    }

    err = zip_writer_->WriteBytes(content.c_str(), content.length());
    if (err != 0) {
        MYLOGE("zip_writer_->WriteBytes(%s): %s\n", entry_name.c_str(),
               ZipWriter::ErrorCodeString(err));
        return false;
    }

    err = zip_writer_->FinishEntry();
    if (err != 0) {
        MYLOGE("zip_writer_->FinishEntry(): %s\n", ZipWriter::ErrorCodeString(err));
        return false;
    }

    return true;
}

static void DoKmsg() {
    struct stat st;
    if (!stat(PSTORE_LAST_KMSG, &st)) {
        /* Also TODO: Make console-ramoops CAP_SYSLOG protected. */
        DumpFile("LAST KMSG", PSTORE_LAST_KMSG);
    } else if (!stat(ALT_PSTORE_LAST_KMSG, &st)) {
        DumpFile("LAST KMSG", ALT_PSTORE_LAST_KMSG);
    } else {
        /* TODO: Make last_kmsg CAP_SYSLOG protected. b/5555691 */
        DumpFile("LAST KMSG", "/proc/last_kmsg");
    }
}

static void DoLogcat() {
    unsigned long timeout;
    // DumpFile("EVENT LOG TAGS", "/etc/event-log-tags");
    // calculate timeout
    timeout = logcat_timeout("main") + logcat_timeout("system") + logcat_timeout("crash");
    if (timeout < 20000) {
        timeout = 20000;
    }
    RunCommand("SYSTEM LOG",
               {"logcat", "-v", "threadtime", "-v", "printable", "-v", "uid",
                        "-d", "*:v"},
               CommandOptions::WithTimeout(timeout / 1000).Build());
    timeout = logcat_timeout("events");
    if (timeout < 20000) {
        timeout = 20000;
    }
    RunCommand("EVENT LOG",
               {"logcat", "-b", "events", "-v", "threadtime", "-v", "printable", "-v", "uid",
                        "-d", "*:v"},
               CommandOptions::WithTimeout(timeout / 1000).Build());
    timeout = logcat_timeout("radio");
    if (timeout < 20000) {
        timeout = 20000;
    }
    RunCommand("RADIO LOG",
               {"logcat", "-b", "radio", "-v", "threadtime", "-v", "printable", "-v", "uid",
                        "-d", "*:v"},
               CommandOptions::WithTimeout(timeout / 1000).Build());

    RunCommand("LOG STATISTICS", {"logcat", "-b", "all", "-S"});

    /* kernels must set CONFIG_PSTORE_PMSG, slice up pstore with device tree */
    RunCommand("LAST LOGCAT",
                {"logcat", "-L", "-b", "all", "-v", "threadtime", "-v", "printable", "-v", "uid",
                        "-d", "*:v"});
}

static void DumpIpTablesAsRoot() {
    RunCommand("IPTABLES", {"iptables", "-L", "-nvx"});
    RunCommand("IP6TABLES", {"ip6tables", "-L", "-nvx"});
    RunCommand("IPTABLES NAT", {"iptables", "-t", "nat", "-L", "-nvx"});
    /* no ip6 nat */
    RunCommand("IPTABLES MANGLE", {"iptables", "-t", "mangle", "-L", "-nvx"});
    RunCommand("IP6TABLES MANGLE", {"ip6tables", "-t", "mangle", "-L", "-nvx"});
    RunCommand("IPTABLES RAW", {"iptables", "-t", "raw", "-L", "-nvx"});
    RunCommand("IP6TABLES RAW", {"ip6tables", "-t", "raw", "-L", "-nvx"});
}

static void AddGlobalAnrTraceFile(const bool add_to_zip, const std::string& anr_traces_file,
                                  const std::string& anr_traces_dir) {
    std::string dump_traces_dir;

    if (dump_traces_path != nullptr) {
        if (add_to_zip) {
            dump_traces_dir = dirname(dump_traces_path);
            MYLOGD("Adding ANR traces (directory %s) to the zip file\n", dump_traces_dir.c_str());
            ds.AddDir(dump_traces_dir, true);
        } else {
            MYLOGD("Dumping current ANR traces (%s) to the main bugreport entry\n",
                   dump_traces_path);
            ds.DumpFile("VM TRACES JUST NOW", dump_traces_path);
        }
    }


    // Make sure directory is not added twice.
    // TODO: this is an overzealous check because it's relying on dump_traces_path - which is
    // generated by dump_traces() -  and anr_traces_path - which is retrieved from a system
    // property - but in reality they're the same path (although the former could be nullptr).
    // Anyways, once dump_traces() is refactored as a private Dumpstate function, this logic should
    // be revisited.
    bool already_dumped = anr_traces_dir == dump_traces_dir;

    MYLOGD("AddGlobalAnrTraceFile(): dump_traces_dir=%s, anr_traces_dir=%s, already_dumped=%d\n",
           dump_traces_dir.c_str(), anr_traces_dir.c_str(), already_dumped);

    int fd = TEMP_FAILURE_RETRY(
        open(anr_traces_file.c_str(), O_RDONLY | O_CLOEXEC | O_NOFOLLOW | O_NONBLOCK));
    if (fd < 0) {
        printf("*** NO ANR VM TRACES FILE (%s): %s\n\n", anr_traces_file.c_str(), strerror(errno));
    } else {
        if (add_to_zip) {
            if (!already_dumped) {
                MYLOGD("Adding dalvik ANR traces (directory %s) to the zip file\n",
                       anr_traces_dir.c_str());
                ds.AddDir(anr_traces_dir, true);
            }
        } else {
            MYLOGD("Dumping last ANR traces (%s) to the main bugreport entry\n",
                   anr_traces_file.c_str());
            dump_file_from_fd("VM TRACES AT LAST ANR", anr_traces_file.c_str(), fd);
        }
    }
}

static void AddAnrTraceDir(const bool add_to_zip, const std::string& anr_traces_dir) {
    MYLOGD("AddAnrTraceDir(): dump_traces_file=%s, anr_traces_dir=%s\n", dump_traces_path,
           anr_traces_dir.c_str());

    // If we're here, dump_traces_path will always be a temporary file
    // (created with mkostemp or similar) that contains dumps taken earlier
    // on in the process.
    if (dump_traces_path != nullptr) {
        if (add_to_zip) {
            ds.AddZipEntry(ZIP_ROOT_DIR + anr_traces_dir + "/traces-just-now.txt", dump_traces_path);
        } else {
            MYLOGD("Dumping current ANR traces (%s) to the main bugreport entry\n",
                   dump_traces_path);
            ds.DumpFile("VM TRACES JUST NOW", dump_traces_path);
        }

        const int ret = unlink(dump_traces_path);
        if (ret == -1) {
            MYLOGW("Error unlinking temporary trace path %s: %s\n", dump_traces_path,
                   strerror(errno));
        }
    }

    // Add a specific message for the first ANR Dump.
    if (anr_data->size() > 0) {
        AddDumps(anr_data->begin(), anr_data->begin() + 1,
                 "VM TRACES AT LAST ANR", add_to_zip);

        if (anr_data->size() > 1) {
            // NOTE: Historical ANRs are always added as separate entries in the
            // bugreport zip file.
            AddDumps(anr_data->begin() + 1, anr_data->end(),
                     "HISTORICAL ANR", true /* add_to_zip */);
        }
    } else {
        printf("*** NO ANRs to dump in %s\n\n", ANR_DIR.c_str());
    }
}

static void AddAnrTraceFiles() {
    const bool add_to_zip = ds.IsZipping() && ds.version_ == VERSION_SPLIT_ANR;

    std::string anr_traces_file;
    std::string anr_traces_dir;
    bool is_global_trace_file = true;

    // First check whether the stack-trace-dir property is set. When it's set,
    // each ANR trace will be written to a separate file and not to a global
    // stack trace file.
    anr_traces_dir = android::base::GetProperty("dalvik.vm.stack-trace-dir", "");
    if (anr_traces_dir.empty()) {
        anr_traces_file = android::base::GetProperty("dalvik.vm.stack-trace-file", "");
        if (!anr_traces_file.empty()) {
            anr_traces_dir = dirname(anr_traces_file.c_str());
        }
    } else {
        is_global_trace_file = false;
    }

    // We have neither configured a global trace file nor a trace directory,
    // there will be nothing to dump.
    if (anr_traces_file.empty() && anr_traces_dir.empty()) {
        printf("*** NO VM TRACES FILE DEFINED (dalvik.vm.stack-trace-file)\n\n");
        return;
    }

    if (is_global_trace_file) {
        AddGlobalAnrTraceFile(add_to_zip, anr_traces_file, anr_traces_dir);
    } else {
        AddAnrTraceDir(add_to_zip, anr_traces_dir);
    }

    /* slow traces for slow operations */
    struct stat st;
    if (!anr_traces_dir.empty()) {
        int i = 0;
        while (true) {
            const std::string slow_trace_path =
                anr_traces_dir + android::base::StringPrintf("slow%02d.txt", i);
            if (stat(slow_trace_path.c_str(), &st)) {
                // No traces file at this index, done with the files.
                break;
            }
            ds.DumpFile("VM TRACES WHEN SLOW", slow_trace_path.c_str());
            i++;
        }
    }
}

static void DumpBlockStatFiles() {
    DurationReporter duration_reporter("DUMP BLOCK STAT");

    std::unique_ptr<DIR, std::function<int(DIR*)>> dirptr(opendir(BLK_DEV_SYS_DIR), closedir);

    if (dirptr == nullptr) {
        MYLOGE("Failed to open %s: %s\n", BLK_DEV_SYS_DIR, strerror(errno));
        return;
    }

    printf("------ DUMP BLOCK STAT ------\n\n");
    while (struct dirent *d = readdir(dirptr.get())) {
        if ((d->d_name[0] == '.')
         && (((d->d_name[1] == '.') && (d->d_name[2] == '\0'))
          || (d->d_name[1] == '\0'))) {
            continue;
        }
        const std::string new_path =
            android::base::StringPrintf("%s/%s", BLK_DEV_SYS_DIR, d->d_name);
        printf("------ BLOCK STAT (%s) ------\n", new_path.c_str());
        dump_files("", new_path.c_str(), skip_not_stat, dump_stat_from_fd);
        printf("\n");
    }
     return;
}

static void DumpPacketStats() {
    DumpFile("NETWORK DEV INFO", "/proc/net/dev");
    DumpFile("QTAGUID NETWORK INTERFACES INFO", "/proc/net/xt_qtaguid/iface_stat_all");
    DumpFile("QTAGUID NETWORK INTERFACES INFO (xt)", "/proc/net/xt_qtaguid/iface_stat_fmt");
    DumpFile("QTAGUID CTRL INFO", "/proc/net/xt_qtaguid/ctrl");
    DumpFile("QTAGUID STATS INFO", "/proc/net/xt_qtaguid/stats");
}

static void DumpIpAddrAndRules() {
    /* The following have a tendency to get wedged when wifi drivers/fw goes belly-up. */
    RunCommand("NETWORK INTERFACES", {"ip", "link"});
    RunCommand("IPv4 ADDRESSES", {"ip", "-4", "addr", "show"});
    RunCommand("IPv6 ADDRESSES", {"ip", "-6", "addr", "show"});
    RunCommand("IP RULES", {"ip", "rule", "show"});
    RunCommand("IP RULES v6", {"ip", "-6", "rule", "show"});
}

static void dumpstate() {
    DurationReporter duration_reporter("DUMPSTATE");

    dump_dev_files("TRUSTY VERSION", "/sys/bus/platform/drivers/trusty", "trusty_version");
    RunCommand("UPTIME", {"uptime"});
    DumpBlockStatFiles();
    dump_emmc_ecsd("/d/mmc0/mmc0:0001/ext_csd");
    DumpFile("MEMORY INFO", "/proc/meminfo");
    RunCommand("CPU INFO", {"top", "-b", "-n", "1", "-H", "-s", "6", "-o",
                            "pid,tid,user,pr,ni,%cpu,s,virt,res,pcy,cmd,name"});
    RunCommand("PROCRANK", {"procrank"}, AS_ROOT_20);
    DumpFile("VIRTUAL MEMORY STATS", "/proc/vmstat");
    DumpFile("VMALLOC INFO", "/proc/vmallocinfo");
    DumpFile("SLAB INFO", "/proc/slabinfo");
    DumpFile("ZONEINFO", "/proc/zoneinfo");
    DumpFile("PAGETYPEINFO", "/proc/pagetypeinfo");
    DumpFile("BUDDYINFO", "/proc/buddyinfo");
    DumpFile("FRAGMENTATION INFO", "/d/extfrag/unusable_index");

    DumpFile("KERNEL WAKE SOURCES", "/d/wakeup_sources");
    DumpFile("KERNEL CPUFREQ", "/sys/devices/system/cpu/cpu0/cpufreq/stats/time_in_state");
    DumpFile("KERNEL SYNC", "/d/sync");

    RunCommand("PROCESSES AND THREADS",
               {"ps", "-A", "-T", "-Z", "-O", "pri,nice,rtprio,sched,pcy"});
    RunCommand("LIBRANK", {"librank"}, CommandOptions::AS_ROOT);

    if (ds.IsZipping()) {
        RunCommand(
                "HARDWARE HALS",
                {"lshal", std::string("--debug=") + kLsHalDebugPath},
                CommandOptions::AS_ROOT);

        ds.AddZipEntry("lshal-debug.txt", kLsHalDebugPath);

        unlink(kLsHalDebugPath.c_str());
    } else {
        RunCommand(
                "HARDWARE HALS", {"lshal", "--debug"}, CommandOptions::AS_ROOT);
    }

    RunCommand("PRINTENV", {"printenv"});
    RunCommand("NETSTAT", {"netstat", "-nW"});
    struct stat s;
    if (stat("/proc/modules", &s) != 0) {
        MYLOGD("Skipping 'lsmod' because /proc/modules does not exist\n");
    } else {
        RunCommand("LSMOD", {"lsmod"});
    }

    do_dmesg();

    RunCommand("LIST OF OPEN FILES", {"lsof"}, CommandOptions::AS_ROOT);
    for_each_pid(do_showmap, "SMAPS OF ALL PROCESSES");
    for_each_tid(show_wchan, "BLOCKED PROCESS WAIT-CHANNELS");
    for_each_pid(show_showtime, "PROCESS TIMES (pid cmd user system iowait+percentage)");

    /* Dump Bluetooth HCI logs */
    ds.AddDir("/data/misc/bluetooth/logs", true);

    if (!ds.do_early_screenshot_) {
        MYLOGI("taking late screenshot\n");
        ds.TakeScreenshot();
    }

    DoLogcat();

    AddAnrTraceFiles();

    // NOTE: tombstones are always added as separate entries in the zip archive
    // and are not interspersed with the main report.
    const bool tombstones_dumped = AddDumps(tombstone_data->begin(), tombstone_data->end(),
                                            "TOMBSTONE", true /* add_to_zip */);
    if (!tombstones_dumped) {
        printf("*** NO TOMBSTONES to dump in %s\n\n", TOMBSTONE_DIR.c_str());
    }

    DumpPacketStats();

    DoKmsg();

    DumpIpAddrAndRules();

    dump_route_tables();

    RunCommand("ARP CACHE", {"ip", "-4", "neigh", "show"});
    RunCommand("IPv6 ND CACHE", {"ip", "-6", "neigh", "show"});
    RunCommand("MULTICAST ADDRESSES", {"ip", "maddr"});

    RunDumpsys("NETWORK DIAGNOSTICS", {"connectivity", "--diag"},
               CommandOptions::WithTimeout(10).Build());

    RunCommand("SYSTEM PROPERTIES", {"getprop"});

    RunCommand("VOLD DUMP", {"vdc", "dump"});
    RunCommand("SECURE CONTAINERS", {"vdc", "asec", "list"});

    RunCommand("STORAGED TASKIOINFO", {"storaged", "-u"}, CommandOptions::WithTimeout(10).Build());

    RunCommand("FILESYSTEMS & FREE SPACE", {"df"});

    RunCommand("LAST RADIO LOG", {"parse_radio_log", "/proc/last_radio_log"});

    printf("------ BACKLIGHTS ------\n");
    printf("LCD brightness=");
    DumpFile("", "/sys/class/leds/lcd-backlight/brightness");
    printf("Button brightness=");
    DumpFile("", "/sys/class/leds/button-backlight/brightness");
    printf("Keyboard brightness=");
    DumpFile("", "/sys/class/leds/keyboard-backlight/brightness");
    printf("ALS mode=");
    DumpFile("", "/sys/class/leds/lcd-backlight/als");
    printf("LCD driver registers:\n");
    DumpFile("", "/sys/class/leds/lcd-backlight/registers");
    printf("\n");

    /* Binder state is expensive to look at as it uses a lot of memory. */
    DumpFile("BINDER FAILED TRANSACTION LOG", "/sys/kernel/debug/binder/failed_transaction_log");
    DumpFile("BINDER TRANSACTION LOG", "/sys/kernel/debug/binder/transaction_log");
    DumpFile("BINDER TRANSACTIONS", "/sys/kernel/debug/binder/transactions");
    DumpFile("BINDER STATS", "/sys/kernel/debug/binder/stats");
    DumpFile("BINDER STATE", "/sys/kernel/debug/binder/state");

    ds.DumpstateBoard();

    /* Migrate the ril_dumpstate to a device specific dumpstate? */
    int rilDumpstateTimeout = android::base::GetIntProperty("ril.dumpstate.timeout", 0);
    if (rilDumpstateTimeout > 0) {
        // su does not exist on user builds, so try running without it.
        // This way any implementations of vril-dump that do not require
        // root can run on user builds.
        CommandOptions::CommandOptionsBuilder options =
            CommandOptions::WithTimeout(rilDumpstateTimeout);
        if (!PropertiesHelper::IsUserBuild()) {
            options.AsRoot();
        }
        RunCommand("DUMP VENDOR RIL LOGS", {"vril-dump"}, options.Build());
    }

    printf("========================================================\n");
    printf("== Android Framework Services\n");
    printf("========================================================\n");

    RunDumpsys("DUMPSYS", {"--skip", "meminfo", "cpuinfo"}, CommandOptions::WithTimeout(90).Build(),
               10);

    printf("========================================================\n");
    printf("== Checkins\n");
    printf("========================================================\n");

    RunDumpsys("CHECKIN BATTERYSTATS", {"batterystats", "-c"});
    RunDumpsys("CHECKIN MEMINFO", {"meminfo", "--checkin"});
    RunDumpsys("CHECKIN NETSTATS", {"netstats", "--checkin"});
    RunDumpsys("CHECKIN PROCSTATS", {"procstats", "-c"});
    RunDumpsys("CHECKIN USAGESTATS", {"usagestats", "-c"});
    RunDumpsys("CHECKIN PACKAGE", {"package", "--checkin"});

    printf("========================================================\n");
    printf("== Running Application Activities\n");
    printf("========================================================\n");

    RunDumpsys("APP ACTIVITIES", {"activity", "-v", "all"});

    printf("========================================================\n");
    printf("== Running Application Services\n");
    printf("========================================================\n");

    RunDumpsys("APP SERVICES", {"activity", "service", "all"});

    printf("========================================================\n");
    printf("== Running Application Providers\n");
    printf("========================================================\n");

    RunDumpsys("APP PROVIDERS", {"activity", "provider", "all"});

    printf("========================================================\n");
    printf("== Dropbox crashes\n");
    printf("========================================================\n");

    RunDumpsys("DROPBOX SYSTEM SERVER CRASHES", {"dropbox", "-p", "system_server_crash"});
    RunDumpsys("DROPBOX SYSTEM APP CRASHES", {"dropbox", "-p", "system_app_crash"});

    printf("========================================================\n");
    printf("== Final progress (pid %d): %d/%d (estimated %d)\n", ds.pid_, ds.progress_->Get(),
           ds.progress_->GetMax(), ds.progress_->GetInitialMax());
    printf("========================================================\n");
    printf("== dumpstate: done (id %d)\n", ds.id_);
    printf("========================================================\n");
}

// This method collects dumpsys for telephony debugging only
static void DumpstateTelephonyOnly() {
    DurationReporter duration_reporter("DUMPSTATE");

    DumpIpTablesAsRoot();

    if (!DropRootUser()) {
        return;
    }

    do_dmesg();
    DoLogcat();
    DumpPacketStats();
    DoKmsg();
    DumpIpAddrAndRules();
    dump_route_tables();

    RunDumpsys("NETWORK DIAGNOSTICS", {"connectivity", "--diag"},
               CommandOptions::WithTimeout(10).Build());

    RunCommand("SYSTEM PROPERTIES", {"getprop"});

    printf("========================================================\n");
    printf("== Android Framework Services\n");
    printf("========================================================\n");

    RunDumpsys("DUMPSYS", {"connectivity"}, CommandOptions::WithTimeout(90).Build(), 10);
    RunDumpsys("DUMPSYS", {"carrier_config"}, CommandOptions::WithTimeout(90).Build(), 10);

    printf("========================================================\n");
    printf("== Running Application Services\n");
    printf("========================================================\n");

    RunDumpsys("TELEPHONY SERVICES", {"activity", "service", "TelephonyDebugService"});

    printf("========================================================\n");
    printf("== dumpstate: done (id %d)\n", ds.id_);
    printf("========================================================\n");
}

void Dumpstate::DumpstateBoard() {
    DurationReporter duration_reporter("dumpstate_board()");
    printf("========================================================\n");
    printf("== Board\n");
    printf("========================================================\n");

    ::android::sp<IDumpstateDevice> dumpstate_device(IDumpstateDevice::getService());
    if (dumpstate_device == nullptr) {
        MYLOGE("No IDumpstateDevice implementation\n");
        return;
    }

    if (!IsZipping()) {
        MYLOGD("Not dumping board info because it's not a zipped bugreport\n");
        return;
    }

    std::string path[NUM_OF_DUMPS];
    android::base::unique_fd fd[NUM_OF_DUMPS];
    int numFds = 0;

    for (int i = 0; i < NUM_OF_DUMPS; i++) {
        path[i] = kDumpstateBoardPath + kDumpstateBoardFiles[i];
        MYLOGI("Calling IDumpstateDevice implementation using path %s\n", path[i].c_str());

        fd[i] = android::base::unique_fd(
            TEMP_FAILURE_RETRY(open(path[i].c_str(),
            O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC | O_NOFOLLOW,
            S_IRUSR | S_IWUSR | S_IRGRP | S_IROTH)));
        if (fd[i] < 0) {
            MYLOGE("Could not open file %s: %s\n", path[i].c_str(), strerror(errno));
            return;
        } else {
            numFds++;
        }
    }

    native_handle_t *handle = native_handle_create(numFds, 0);
    if (handle == nullptr) {
        MYLOGE("Could not create native_handle\n");
        return;
    }

    for (int i = 0; i < numFds; i++) {
        handle->data[i] = fd[i].release();
    }

    // TODO: need a timeout mechanism so dumpstate does not hang on device implementation call.
    android::hardware::Return<void> status = dumpstate_device->dumpstateBoard(handle);
    if (!status.isOk()) {
        MYLOGE("dumpstateBoard failed: %s\n", status.description().c_str());
        native_handle_close(handle);
        native_handle_delete(handle);
        return;
    }

    for (int i = 0; i < numFds; i++) {
        struct stat s;
        if (fstat(handle->data[i], &s) == -1) {
            MYLOGE("Failed to fstat %s: %d\n", kDumpstateBoardFiles[i].c_str(), errno);
        } else if (s.st_size > 0) {
            AddZipEntry(kDumpstateBoardFiles[i], path[i]);
        } else {
            MYLOGE("Ignoring empty %s\n", kDumpstateBoardFiles[i].c_str());
        }
    }

    printf("*** See dumpstate-board.txt entry ***\n");

    native_handle_close(handle);
    native_handle_delete(handle);

    for (int i = 0; i < numFds; i++) {
        if (remove(path[i].c_str()) != 0) {
            MYLOGE("Could not remove(%s): %s\n", path[i].c_str(), strerror(errno));
        }
    }
}

static void ShowUsageAndExit(int exitCode = 1) {
    fprintf(stderr,
            "usage: dumpstate [-h] [-b soundfile] [-e soundfile] [-o file] [-d] [-p] "
            "[-z]] [-s] [-S] [-q] [-B] [-P] [-R] [-V version]\n"
            "  -h: display this help message\n"
            "  -b: play sound file instead of vibrate, at beginning of job\n"
            "  -e: play sound file instead of vibrate, at end of job\n"
            "  -o: write to file (instead of stdout)\n"
            "  -d: append date to filename (requires -o)\n"
            "  -p: capture screenshot to filename.png (requires -o)\n"
            "  -z: generate zipped file (requires -o)\n"
            "  -s: write output to control socket (for init)\n"
            "  -S: write file location to control socket (for init; requires -o and -z)"
            "  -q: disable vibrate\n"
            "  -B: send broadcast when finished (requires -o)\n"
            "  -P: send broadcast when started and update system properties on "
            "progress (requires -o and -B)\n"
            "  -R: take bugreport in remote mode (requires -o, -z, -d and -B, "
            "shouldn't be used with -P)\n"
            "  -v: prints the dumpstate header and exit\n");
    exit(exitCode);
}

static void ExitOnInvalidArgs() {
    fprintf(stderr, "invalid combination of args\n");
    ShowUsageAndExit();
}

static void sig_handler(int) {
    _exit(EXIT_FAILURE);
}

static void register_sig_handler() {
    struct sigaction sa;
    sigemptyset(&sa.sa_mask);
    sa.sa_flags = 0;
    sa.sa_handler = sig_handler;
    sigaction(SIGPIPE, &sa, NULL); // broken pipe
    sigaction(SIGSEGV, &sa, NULL); // segment fault
    sigaction(SIGINT, &sa, NULL); // ctrl-c
    sigaction(SIGTERM, &sa, NULL); // killed
    sigaction(SIGQUIT, &sa, NULL); // quit
}

bool Dumpstate::FinishZipFile() {
    std::string entry_name = base_name_ + "-" + name_ + ".txt";
    MYLOGD("Adding main entry (%s) from %s to .zip bugreport\n", entry_name.c_str(),
           tmp_path_.c_str());
    // Final timestamp
    char date[80];
    time_t the_real_now_please_stand_up = time(nullptr);
    strftime(date, sizeof(date), "%Y/%m/%d %H:%M:%S", localtime(&the_real_now_please_stand_up));
    MYLOGD("dumpstate id %d finished around %s (%ld s)\n", ds.id_, date,
           the_real_now_please_stand_up - ds.now_);

    if (!ds.AddZipEntry(entry_name, tmp_path_)) {
        MYLOGE("Failed to add text entry to .zip file\n");
        return false;
    }
    if (!AddTextZipEntry("main_entry.txt", entry_name)) {
        MYLOGE("Failed to add main_entry.txt to .zip file\n");
        return false;
    }

    // Add log file (which contains stderr output) to zip...
    fprintf(stderr, "dumpstate_log.txt entry on zip file logged up to here\n");
    if (!ds.AddZipEntry("dumpstate_log.txt", ds.log_path_.c_str())) {
        MYLOGE("Failed to add dumpstate log to .zip file\n");
        return false;
    }
    // ... and re-opens it for further logging.
    redirect_to_existing_file(stderr, const_cast<char*>(ds.log_path_.c_str()));
    fprintf(stderr, "\n");

    int32_t err = zip_writer_->Finish();
    if (err != 0) {
        MYLOGE("zip_writer_->Finish(): %s\n", ZipWriter::ErrorCodeString(err));
        return false;
    }

    // TODO: remove once FinishZipFile() is automatically handled by Dumpstate's destructor.
    ds.zip_file.reset(nullptr);

    MYLOGD("Removing temporary file %s\n", tmp_path_.c_str())
    if (remove(tmp_path_.c_str()) != 0) {
        MYLOGE("Failed to remove temporary file (%s): %s\n", tmp_path_.c_str(), strerror(errno));
    }

    return true;
}

static std::string SHA256_file_hash(std::string filepath) {
    android::base::unique_fd fd(TEMP_FAILURE_RETRY(open(filepath.c_str(), O_RDONLY | O_NONBLOCK
            | O_CLOEXEC | O_NOFOLLOW)));
    if (fd == -1) {
        MYLOGE("open(%s): %s\n", filepath.c_str(), strerror(errno));
        return NULL;
    }

    SHA256_CTX ctx;
    SHA256_Init(&ctx);

    std::vector<uint8_t> buffer(65536);
    while (1) {
        ssize_t bytes_read = TEMP_FAILURE_RETRY(read(fd.get(), buffer.data(), buffer.size()));
        if (bytes_read == 0) {
            break;
        } else if (bytes_read == -1) {
            MYLOGE("read(%s): %s\n", filepath.c_str(), strerror(errno));
            return NULL;
        }

        SHA256_Update(&ctx, buffer.data(), bytes_read);
    }

    uint8_t hash[SHA256_DIGEST_LENGTH];
    SHA256_Final(hash, &ctx);

    char hash_buffer[SHA256_DIGEST_LENGTH * 2 + 1];
    for(size_t i = 0; i < SHA256_DIGEST_LENGTH; i++) {
        sprintf(hash_buffer + (i * 2), "%02x", hash[i]);
    }
    hash_buffer[sizeof(hash_buffer) - 1] = 0;
    return std::string(hash_buffer);
}

static void SendBroadcast(const std::string& action, const std::vector<std::string>& args) {
    // clang-format off
    std::vector<std::string> am = {"/system/bin/cmd", "activity", "broadcast", "--user", "0",
                    "--receiver-foreground", "--receiver-include-background", "-a", action};
    // clang-format on

    am.insert(am.end(), args.begin(), args.end());

    RunCommand("", am,
               CommandOptions::WithTimeout(20)
                   .Log("Sending broadcast: '%s'\n")
                   .Always()
                   .DropRoot()
                   .RedirectStderr()
                   .Build());
}

static void Vibrate(int duration_ms) {
    // clang-format off
    RunCommand("", {"cmd", "vibrator", "vibrate", std::to_string(duration_ms), "dumpstate"},
               CommandOptions::WithTimeout(10)
                   .Log("Vibrate: '%s'\n")
                   .Always()
                   .Build());
    // clang-format on
}

int main(int argc, char *argv[]) {
    int do_add_date = 0;
    int do_zip_file = 0;
    int do_vibrate = 1;
    char* use_outfile = 0;
    int use_socket = 0;
    int use_control_socket = 0;
    int do_fb = 0;
    int do_broadcast = 0;
    int is_remote_mode = 0;
    bool show_header_only = false;
    bool do_start_service = false;
    bool telephony_only = false;

    /* set as high priority, and protect from OOM killer */
    setpriority(PRIO_PROCESS, 0, -20);

    FILE* oom_adj = fopen("/proc/self/oom_score_adj", "we");
    if (oom_adj) {
        fputs("-1000", oom_adj);
        fclose(oom_adj);
    } else {
        /* fallback to kernels <= 2.6.35 */
        oom_adj = fopen("/proc/self/oom_adj", "we");
        if (oom_adj) {
            fputs("-17", oom_adj);
            fclose(oom_adj);
        }
    }

    /* parse arguments */
    int c;
    while ((c = getopt(argc, argv, "dho:svqzpPBRSV:")) != -1) {
        switch (c) {
            // clang-format off
            case 'd': do_add_date = 1;            break;
            case 'z': do_zip_file = 1;            break;
            case 'o': use_outfile = optarg;       break;
            case 's': use_socket = 1;             break;
            case 'S': use_control_socket = 1;     break;
            case 'v': show_header_only = true;    break;
            case 'q': do_vibrate = 0;             break;
            case 'p': do_fb = 1;                  break;
            case 'P': ds.update_progress_ = true; break;
            case 'R': is_remote_mode = 1;         break;
            case 'B': do_broadcast = 1;           break;
            case 'V':                             break; // compatibility no-op
            case 'h':
                ShowUsageAndExit(0);
                break;
            default:
                fprintf(stderr, "Invalid option: %c\n", c);
                ShowUsageAndExit();
                // clang-format on
        }
    }

    // TODO: use helper function to convert argv into a string
    for (int i = 0; i < argc; i++) {
        ds.args_ += argv[i];
        if (i < argc - 1) {
            ds.args_ += " ";
        }
    }

    ds.extra_options_ = android::base::GetProperty(PROPERTY_EXTRA_OPTIONS, "");
    if (!ds.extra_options_.empty()) {
        // Framework uses a system property to override some command-line args.
        // Currently, it contains the type of the requested bugreport.
        if (ds.extra_options_ == "bugreportplus") {
            // Currently, the dumpstate binder is only used by Shell to update progress.
            do_start_service = true;
            ds.update_progress_ = true;
            do_fb = 0;
        } else if (ds.extra_options_ == "bugreportremote") {
            do_vibrate = 0;
            is_remote_mode = 1;
            do_fb = 0;
        } else if (ds.extra_options_ == "bugreportwear") {
            ds.update_progress_ = true;
        } else if (ds.extra_options_ == "bugreporttelephony") {
            telephony_only = true;
        } else {
            MYLOGE("Unknown extra option: %s\n", ds.extra_options_.c_str());
        }
        // Reset the property
        android::base::SetProperty(PROPERTY_EXTRA_OPTIONS, "");
    }

    ds.notification_title = android::base::GetProperty(PROPERTY_EXTRA_TITLE, "");
    if (!ds.notification_title.empty()) {
        // Reset the property
        android::base::SetProperty(PROPERTY_EXTRA_TITLE, "");

        ds.notification_description = android::base::GetProperty(PROPERTY_EXTRA_DESCRIPTION, "");
        if (!ds.notification_description.empty()) {
            // Reset the property
            android::base::SetProperty(PROPERTY_EXTRA_DESCRIPTION, "");
        }
        MYLOGD("notification (title:  %s, description: %s)\n",
               ds.notification_title.c_str(), ds.notification_description.c_str());
    }

    if ((do_zip_file || do_add_date || ds.update_progress_ || do_broadcast) && !use_outfile) {
        ExitOnInvalidArgs();
    }

    if (use_control_socket && !do_zip_file) {
        ExitOnInvalidArgs();
    }

    if (ds.update_progress_ && !do_broadcast) {
        ExitOnInvalidArgs();
    }

    if (is_remote_mode && (ds.update_progress_ || !do_broadcast || !do_zip_file || !do_add_date)) {
        ExitOnInvalidArgs();
    }

    if (ds.version_ == VERSION_DEFAULT) {
        ds.version_ = VERSION_CURRENT;
    }

    if (ds.version_ != VERSION_CURRENT && ds.version_ != VERSION_SPLIT_ANR) {
        MYLOGE("invalid version requested ('%s'); suppported values are: ('%s', '%s', '%s')\n",
               ds.version_.c_str(), VERSION_DEFAULT.c_str(), VERSION_CURRENT.c_str(),
               VERSION_SPLIT_ANR.c_str());
        exit(1);
    }

    if (show_header_only) {
        ds.PrintHeader();
        exit(0);
    }

    /* redirect output if needed */
    bool is_redirecting = !use_socket && use_outfile;

    // TODO: temporarily set progress until it's part of the Dumpstate constructor
    std::string stats_path =
        is_redirecting ? android::base::StringPrintf("%s/dumpstate-stats.txt", dirname(use_outfile))
                       : "";
    ds.progress_.reset(new Progress(stats_path));

    /* gets the sequential id */
    uint32_t last_id = android::base::GetIntProperty(PROPERTY_LAST_ID, 0);
    ds.id_ = ++last_id;
    android::base::SetProperty(PROPERTY_LAST_ID, std::to_string(last_id));

    MYLOGI("begin\n");

    register_sig_handler();

    if (do_start_service) {
        MYLOGI("Starting 'dumpstate' service\n");
        android::status_t ret;
        if ((ret = android::os::DumpstateService::Start()) != android::OK) {
            MYLOGE("Unable to start DumpstateService: %d\n", ret);
        }
    }

    if (PropertiesHelper::IsDryRun()) {
        MYLOGI("Running on dry-run mode (to disable it, call 'setprop dumpstate.dry_run false')\n");
    }

    MYLOGI("dumpstate info: id=%d, args='%s', extra_options= %s)\n", ds.id_, ds.args_.c_str(),
           ds.extra_options_.c_str());

    MYLOGI("bugreport format version: %s\n", ds.version_.c_str());

    ds.do_early_screenshot_ = ds.update_progress_;

    // If we are going to use a socket, do it as early as possible
    // to avoid timeouts from bugreport.
    if (use_socket) {
        redirect_to_socket(stdout, "dumpstate");
    }

    if (use_control_socket) {
        MYLOGD("Opening control socket\n");
        ds.control_socket_fd_ = open_socket("dumpstate");
        ds.update_progress_ = 1;
    }

    if (is_redirecting) {
        ds.bugreport_dir_ = dirname(use_outfile);
        std::string build_id = android::base::GetProperty("ro.build.id", "UNKNOWN_BUILD");
        std::string device_name = android::base::GetProperty("ro.product.name", "UNKNOWN_DEVICE");
        ds.base_name_ = android::base::StringPrintf("%s-%s-%s", basename(use_outfile),
                                                    device_name.c_str(), build_id.c_str());
        if (do_add_date) {
            char date[80];
            strftime(date, sizeof(date), "%Y-%m-%d-%H-%M-%S", localtime(&ds.now_));
            ds.name_ = date;
        } else {
            ds.name_ = "undated";
        }

        if (telephony_only) {
            ds.base_name_ += "-telephony";
        }

        if (do_fb) {
            ds.screenshot_path_ = ds.GetPath(".png");
        }
        ds.tmp_path_ = ds.GetPath(".tmp");
        ds.log_path_ = ds.GetPath("-dumpstate_log-" + std::to_string(ds.pid_) + ".txt");

        MYLOGD(
            "Bugreport dir: %s\n"
            "Base name: %s\n"
            "Suffix: %s\n"
            "Log path: %s\n"
            "Temporary path: %s\n"
            "Screenshot path: %s\n",
            ds.bugreport_dir_.c_str(), ds.base_name_.c_str(), ds.name_.c_str(),
            ds.log_path_.c_str(), ds.tmp_path_.c_str(), ds.screenshot_path_.c_str());

        if (do_zip_file) {
            ds.path_ = ds.GetPath(".zip");
            MYLOGD("Creating initial .zip file (%s)\n", ds.path_.c_str());
            create_parent_dirs(ds.path_.c_str());
            ds.zip_file.reset(fopen(ds.path_.c_str(), "wb"));
            if (ds.zip_file == nullptr) {
                MYLOGE("fopen(%s, 'wb'): %s\n", ds.path_.c_str(), strerror(errno));
                do_zip_file = 0;
            } else {
                ds.zip_writer_.reset(new ZipWriter(ds.zip_file.get()));
            }
            ds.AddTextZipEntry("version.txt", ds.version_);
        }

        if (ds.update_progress_) {
            if (do_broadcast) {
                // clang-format off

                std::vector<std::string> am_args = {
                     "--receiver-permission", "android.permission.DUMP",
                     "--es", "android.intent.extra.NAME", ds.name_,
                     "--ei", "android.intent.extra.ID", std::to_string(ds.id_),
                     "--ei", "android.intent.extra.PID", std::to_string(ds.pid_),
                     "--ei", "android.intent.extra.MAX", std::to_string(ds.progress_->GetMax()),
                };
                // clang-format on
                SendBroadcast("com.android.internal.intent.action.BUGREPORT_STARTED", am_args);
            }
            if (use_control_socket) {
                dprintf(ds.control_socket_fd_, "BEGIN:%s\n", ds.path_.c_str());
            }
        }
    }

    /* read /proc/cmdline before dropping root */
    FILE *cmdline = fopen("/proc/cmdline", "re");
    if (cmdline) {
        fgets(cmdline_buf, sizeof(cmdline_buf), cmdline);
        fclose(cmdline);
    }

    if (do_vibrate) {
        Vibrate(150);
    }

    if (do_fb && ds.do_early_screenshot_) {
        if (ds.screenshot_path_.empty()) {
            // should not have happened
            MYLOGE("INTERNAL ERROR: skipping early screenshot because path was not set\n");
        } else {
            MYLOGI("taking early screenshot\n");
            ds.TakeScreenshot();
        }
    }

    if (do_zip_file) {
        if (chown(ds.path_.c_str(), AID_SHELL, AID_SHELL)) {
            MYLOGE("Unable to change ownership of zip file %s: %s\n", ds.path_.c_str(),
                   strerror(errno));
        }
    }

    if (is_redirecting) {
        redirect_to_file(stderr, const_cast<char*>(ds.log_path_.c_str()));
        if (chown(ds.log_path_.c_str(), AID_SHELL, AID_SHELL)) {
            MYLOGE("Unable to change ownership of dumpstate log file %s: %s\n",
                   ds.log_path_.c_str(), strerror(errno));
        }
        /* TODO: rather than generating a text file now and zipping it later,
           it would be more efficient to redirect stdout to the zip entry
           directly, but the libziparchive doesn't support that option yet. */
        redirect_to_file(stdout, const_cast<char*>(ds.tmp_path_.c_str()));
        if (chown(ds.tmp_path_.c_str(), AID_SHELL, AID_SHELL)) {
            MYLOGE("Unable to change ownership of temporary bugreport file %s: %s\n",
                   ds.tmp_path_.c_str(), strerror(errno));
        }
    }

    // Don't buffer stdout
    setvbuf(stdout, nullptr, _IONBF, 0);

    // NOTE: there should be no stdout output until now, otherwise it would break the header.
    // In particular, DurationReport objects should be created passing 'title, NULL', so their
    // duration is logged into MYLOG instead.
    ds.PrintHeader();

    if (telephony_only) {
        DumpstateTelephonyOnly();
        ds.DumpstateBoard();
    } else {
        // Dumps systrace right away, otherwise it will be filled with unnecessary events.
        // First try to dump anrd trace if the daemon is running. Otherwise, dump
        // the raw trace.
        if (!dump_anrd_trace()) {
            dump_systrace();
        }

        // Invoking the following dumpsys calls before dump_traces() to try and
        // keep the system stats as close to its initial state as possible.
        RunDumpsys("DUMPSYS MEMINFO", {"meminfo", "-a"},
                   CommandOptions::WithTimeout(90).DropRoot().Build());
        RunDumpsys("DUMPSYS CPUINFO", {"cpuinfo", "-a"},
                   CommandOptions::WithTimeout(10).DropRoot().Build());

        // TODO: Drop root user and move into dumpstate() once b/28633932 is fixed.
        dump_raft();

        /* collect stack traces from Dalvik and native processes (needs root) */
        dump_traces_path = dump_traces();

        /* Run some operations that require root. */
        tombstone_data.reset(GetDumpFds(TOMBSTONE_DIR, TOMBSTONE_FILE_PREFIX, !ds.IsZipping()));
        anr_data.reset(GetDumpFds(ANR_DIR, ANR_FILE_PREFIX, !ds.IsZipping()));

        ds.AddDir(RECOVERY_DIR, true);
        ds.AddDir(RECOVERY_DATA_DIR, true);
        ds.AddDir(LOGPERSIST_DATA_DIR, false);
        if (!PropertiesHelper::IsUserBuild()) {
            ds.AddDir(PROFILE_DATA_DIR_CUR, true);
            ds.AddDir(PROFILE_DATA_DIR_REF, true);
        }
        add_mountinfo();
        DumpIpTablesAsRoot();

        // Capture any IPSec policies in play.  No keys are exposed here.
        RunCommand("IP XFRM POLICY", {"ip", "xfrm", "policy"},
                   CommandOptions::WithTimeout(10).Build());

        // Run ss as root so we can see socket marks.
        RunCommand("DETAILED SOCKET STATE", {"ss", "-eionptu"},
                   CommandOptions::WithTimeout(10).Build());

        if (!DropRootUser()) {
            return -1;
        }

        dumpstate();
    }

    /* close output if needed */
    if (is_redirecting) {
        fclose(stdout);
    }

    /* rename or zip the (now complete) .tmp file to its final location */
    if (use_outfile) {

        /* check if user changed the suffix using system properties */
        std::string name = android::base::GetProperty(
            android::base::StringPrintf("dumpstate.%d.name", ds.pid_), "");
        bool change_suffix= false;
        if (!name.empty()) {
            /* must whitelist which characters are allowed, otherwise it could cross directories */
            std::regex valid_regex("^[-_a-zA-Z0-9]+$");
            if (std::regex_match(name.c_str(), valid_regex)) {
                change_suffix = true;
            } else {
                MYLOGE("invalid suffix provided by user: %s\n", name.c_str());
            }
        }
        if (change_suffix) {
            MYLOGI("changing suffix from %s to %s\n", ds.name_.c_str(), name.c_str());
            ds.name_ = name;
            if (!ds.screenshot_path_.empty()) {
                std::string new_screenshot_path = ds.GetPath(".png");
                if (rename(ds.screenshot_path_.c_str(), new_screenshot_path.c_str())) {
                    MYLOGE("rename(%s, %s): %s\n", ds.screenshot_path_.c_str(),
                           new_screenshot_path.c_str(), strerror(errno));
                } else {
                    ds.screenshot_path_ = new_screenshot_path;
                }
            }
        }

        bool do_text_file = true;
        if (do_zip_file) {
            if (!ds.FinishZipFile()) {
                MYLOGE("Failed to finish zip file; sending text bugreport instead\n");
                do_text_file = true;
            } else {
                do_text_file = false;
                // Since zip file is already created, it needs to be renamed.
                std::string new_path = ds.GetPath(".zip");
                if (ds.path_ != new_path) {
                    MYLOGD("Renaming zip file from %s to %s\n", ds.path_.c_str(), new_path.c_str());
                    if (rename(ds.path_.c_str(), new_path.c_str())) {
                        MYLOGE("rename(%s, %s): %s\n", ds.path_.c_str(), new_path.c_str(),
                               strerror(errno));
                    } else {
                        ds.path_ = new_path;
                    }
                }
            }
        }
        if (do_text_file) {
            ds.path_ = ds.GetPath(".txt");
            MYLOGD("Generating .txt bugreport at %s from %s\n", ds.path_.c_str(),
                   ds.tmp_path_.c_str());
            if (rename(ds.tmp_path_.c_str(), ds.path_.c_str())) {
                MYLOGE("rename(%s, %s): %s\n", ds.tmp_path_.c_str(), ds.path_.c_str(),
                       strerror(errno));
                ds.path_.clear();
            }
        }
        if (use_control_socket) {
            if (do_text_file) {
                dprintf(ds.control_socket_fd_,
                        "FAIL:could not create zip file, check %s "
                        "for more details\n",
                        ds.log_path_.c_str());
            } else {
                dprintf(ds.control_socket_fd_, "OK:%s\n", ds.path_.c_str());
            }
        }
    }

    /* vibrate a few but shortly times to let user know it's finished */
    for (int i = 0; i < 3; i++) {
        Vibrate(75);
        usleep((75 + 50) * 1000);
    }

    /* tell activity manager we're done */
    if (do_broadcast) {
        if (!ds.path_.empty()) {
            MYLOGI("Final bugreport path: %s\n", ds.path_.c_str());
            // clang-format off

            std::vector<std::string> am_args = {
                 "--receiver-permission", "android.permission.DUMP",
                 "--ei", "android.intent.extra.ID", std::to_string(ds.id_),
                 "--ei", "android.intent.extra.PID", std::to_string(ds.pid_),
                 "--ei", "android.intent.extra.MAX", std::to_string(ds.progress_->GetMax()),
                 "--es", "android.intent.extra.BUGREPORT", ds.path_,
                 "--es", "android.intent.extra.DUMPSTATE_LOG", ds.log_path_
            };
            // clang-format on
            if (do_fb) {
                am_args.push_back("--es");
                am_args.push_back("android.intent.extra.SCREENSHOT");
                am_args.push_back(ds.screenshot_path_);
            }
            if (!ds.notification_title.empty()) {
                am_args.push_back("--es");
                am_args.push_back("android.intent.extra.TITLE");
                am_args.push_back(ds.notification_title);
                if (!ds.notification_description.empty()) {
                    am_args.push_back("--es");
                    am_args.push_back("android.intent.extra.DESCRIPTION");
                    am_args.push_back(ds.notification_description);
                }
            }
            if (is_remote_mode) {
                am_args.push_back("--es");
                am_args.push_back("android.intent.extra.REMOTE_BUGREPORT_HASH");
                am_args.push_back(SHA256_file_hash(ds.path_));
                SendBroadcast("com.android.internal.intent.action.REMOTE_BUGREPORT_FINISHED",
                              am_args);
            } else {
                SendBroadcast("com.android.internal.intent.action.BUGREPORT_FINISHED", am_args);
            }
        } else {
            MYLOGE("Skipping finished broadcast because bugreport could not be generated\n");
        }
    }

    MYLOGD("Final progress: %d/%d (estimated %d)\n", ds.progress_->Get(), ds.progress_->GetMax(),
           ds.progress_->GetInitialMax());
    ds.progress_->Save();
    MYLOGI("done (id %d)\n", ds.id_);

    if (is_redirecting) {
        fclose(stderr);
    }

    if (use_control_socket && ds.control_socket_fd_ != -1) {
        MYLOGD("Closing control socket\n");
        close(ds.control_socket_fd_);
    }

    return 0;
}
