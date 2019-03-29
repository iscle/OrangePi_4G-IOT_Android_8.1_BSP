/*
 * Copyright (C) 2015 The Android Open Source Project
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

#define LOG_TAG "crash_collector"

#include <dirent.h>
#include <errno.h>
#include <pwd.h>
#include <sys/capability.h>
#include <sys/types.h>
#include <unistd.h>

#include <string>
#include <vector>

#include <android-base/file.h>
#include <cutils/properties.h>
#include <log/log.h>
#include <private/android_filesystem_config.h>
#include <utils/String8.h>

#include "client/linux/minidump_writer/linux_core_dumper.h"
#include "client/linux/minidump_writer/minidump_writer.h"

#undef DISALLOW_COPY_AND_ASSIGN  // Defined in breakpad's header.
#include "coredump_writer.h"

namespace {

using android::String8;

const char kOutputDirectory[] = "/data/system/crash_reports";
const int kMaxNumReports = 16;

// Gets a list of entries under the specified directory.
bool ReadDirectory(const std::string& path, std::vector<dirent>* entries) {
  std::unique_ptr<DIR, int(*)(DIR*)> dir(opendir(path.c_str()), closedir);
  if (!dir)
    return false;
  while (struct dirent* entry = readdir(dir.get())) {
    if (strcmp(entry->d_name, ".") != 0 && strcmp(entry->d_name, "..") != 0)
      entries->push_back(*entry);
  }
  return true;
}

// Removes a file or a directory recursively.
bool RemoveRecursively(const std::string& path) {
  if (unlink(path.c_str()) == 0)
    return true;
  if (errno != EISDIR) {
    ALOGE("Failed to unlink: %s, errno = %d", path.c_str(), errno);
    return false;
  }
  std::vector<dirent> entries;
  if (!ReadDirectory(path, &entries))
    return false;
  for (const auto& entry : entries) {
    if (!RemoveRecursively(path + "/" + entry.d_name))
      return false;
  }
  return rmdir(path.c_str()) == 0;
}

// Makes room for the new crash report by deleting old files when necessary.
bool MakeRoomForNewReport() {
  // Enumerate reports.
  std::vector<dirent> entries;
  if (!ReadDirectory(kOutputDirectory, &entries))
    return false;

  std::vector<time_t> dump_mtimes;  // Modification time of dump files.
  std::vector<std::pair<time_t, String8>> all_files;
  for (const auto& entry : entries) {
    String8 filename = String8(kOutputDirectory).appendPath(entry.d_name);
    struct stat attributes;
    if (stat(filename.string(), &attributes))
      return false;
    all_files.push_back(std::make_pair(attributes.st_mtime, filename));
    if (filename.getPathExtension() == ".dmp")
      dump_mtimes.push_back(attributes.st_mtime);
  }

  // Remove old files.
  if (dump_mtimes.size() >= kMaxNumReports) {
    // Sort the vector (newer file comes first).
    std::sort(dump_mtimes.rbegin(), dump_mtimes.rend());

    const time_t threshold = dump_mtimes[kMaxNumReports - 1];
    for (const auto& file : all_files) {
      const time_t mtime = file.first;
      const String8& filename = file.second;
      if (mtime <= threshold) {
        if (!RemoveRecursively(filename.string()))
          return false;
      }
    }
  }
  return true;
}

// Returns the specified system property.
std::string GetSystemProperty(const std::string& key) {
  char buf[PROPERTY_VALUE_MAX];
  property_get(key.c_str(), buf, "");
  return std::string(buf);
}

// Writes metadata as JSON file.
bool WriteMetadata(ssize_t result_coredump_size,
                   size_t coredump_size_limit,
                   size_t expected_coredump_size,
                   const std::string& pid,
                   const std::string& uid,
                   const std::string& gid,
                   const std::string& signal,
                   const std::string& username,
                   const std::string& exec_name,
                   const std::string& filename) {
  std::string content = "{";
  content += "\"version\":\"" + GetSystemProperty("ro.build.id") + "\"";
  content += ",";
  content += "\"result_coredump_size\":" + std::to_string(result_coredump_size);
  content += ",";
  content += "\"coredump_size_limit\":" + std::to_string(coredump_size_limit);
  content += ",";
  content += "\"expected_coredump_size\":" +
      std::to_string(expected_coredump_size);
  content += ",";
  content += "\"pid\":" + pid;
  content += ",";
  content += "\"uid\":" + uid;
  content += ",";
  content += "\"gid\":" + gid;
  content += ",";
  content += "\"signal\":" + signal;
  content += ",";
  content += "\"username\":\"" + username + "\"";
  content += ",";
  content += "\"process\":\"" + exec_name + "\"";
  content += "}";
  return android::base::WriteStringToFile(
      content, filename, S_IRUSR | S_IWUSR, AID_SYSTEM, AID_SYSTEM);
}

// Converts the specified coredump file to a minidump.
bool ConvertCoredumpToMinidump(const std::string& coredump_filename,
                               const std::string& proc_files_dir,
                               const std::string& minidump_filename) {
  google_breakpad::MappingList mappings;
  google_breakpad::AppMemoryList memory_list;
  google_breakpad::LinuxCoreDumper dumper(
      0, coredump_filename.c_str(), proc_files_dir.c_str());
  bool success = google_breakpad::WriteMinidump(
      minidump_filename.c_str(), mappings, memory_list, &dumper);
  unlink(coredump_filename.c_str());
  RemoveRecursively(proc_files_dir);
  return success;
}

}  // namespace

int main(int argc, char** argv) {
  if (argc < 7) {
    ALOGE("Insufficient args.");
    return 1;
  }
  const std::string pid_string = argv[1];
  const std::string uid_string = argv[2];
  const std::string gid_string = argv[3];
  const std::string signal_string = argv[4];
  const std::string crash_time = argv[5];
  const std::string exec_name = argv[6];

  const uid_t uid = std::stoi(uid_string);
  const uid_t appid = uid % AID_USER;
  if (appid >= AID_APP) {  // Ignore non-system crashes.
    return 0;
  }

  // Act as the system user and drop all capabilities.
  struct __user_cap_header_struct capheader;
  struct __user_cap_data_struct capdata[2];
  memset(&capheader, 0, sizeof(capheader));
  memset(&capdata, 0, sizeof(capdata));
  capheader.version = _LINUX_CAPABILITY_VERSION_3;
  if (setegid(AID_SYSTEM) != 0 || seteuid(AID_SYSTEM) != 0 ||
      capset(&capheader, capdata) != 0) {
    ALOGE("Failed to stop being root.");
    return 1;
  }

  // Username lookup.
  passwd* pwd = getpwuid(appid);
  std::string username((pwd != NULL) ? pwd->pw_name : "");
  // Delete old crash reports.
  if (!MakeRoomForNewReport()) {
    ALOGE("Failed to delete old crash reports.");
    return 1;
  }
  // Read coredump from stdin.
  const std::string basename =
      std::string(kOutputDirectory) + "/" + crash_time + "." + pid_string;
  const std::string coredump = basename + ".core";
  const std::string proc_files_dir = basename + ".proc";
  if (mkdir(proc_files_dir.c_str(), S_IRUSR | S_IWUSR | S_IXUSR) == -1) {
    ALOGE("Failed to create proc directory. errno = %d", errno);
    return 1;
  }
  CoredumpWriter coredump_writer(STDIN_FILENO, coredump, proc_files_dir);
  const ssize_t result_coredump_size = coredump_writer.WriteCoredump();
  if (result_coredump_size > 0) {
    // Convert coredump to minidump.
    const std::string minidump = basename + ".dmp";
    if (!ConvertCoredumpToMinidump(coredump, proc_files_dir, minidump)) {
      ALOGE("Failed to convert coredump to minidump.");
    }
  } else {
    ALOGE("Failed to copy coredump from stdin.");
  }
  // Write metadata.
  const std::string metadata = basename + ".meta";
  if (!WriteMetadata(result_coredump_size, coredump_writer.coredump_size_limit(),
                     coredump_writer.expected_coredump_size(), pid_string,
                     uid_string, gid_string, signal_string, username, exec_name,
                     metadata)) {
    ALOGE("Failed to write metadata.");
    return 1;
  }
  return 0;
}
