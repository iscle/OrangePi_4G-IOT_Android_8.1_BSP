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

#ifndef FRAMEWORK_NATIVE_CMDS_LSHAL_TABLE_ENTRY_H_
#define FRAMEWORK_NATIVE_CMDS_LSHAL_TABLE_ENTRY_H_

#include <stdint.h>

#include <string>
#include <vector>
#include <iostream>

namespace android {
namespace lshal {

using Pids = std::vector<int32_t>;

enum : unsigned int {
    HWSERVICEMANAGER_LIST, // through defaultServiceManager()->list()
    PTSERVICEMANAGER_REG_CLIENT, // through registerPassthroughClient
    LIST_DLLIB, // through listing dynamic libraries
};
using TableEntrySource = unsigned int;

enum : unsigned int {
    ARCH_UNKNOWN = 0,
    ARCH32       = 1 << 0,
    ARCH64       = 1 << 1,
    ARCH_BOTH    = ARCH32 | ARCH64
};
using Architecture = unsigned int;

struct TableEntry {
    std::string interfaceName;
    std::string transport;
    int32_t serverPid;
    uint32_t threadUsage;
    uint32_t threadCount;
    std::string serverCmdline;
    uint64_t serverObjectAddress;
    Pids clientPids;
    std::vector<std::string> clientCmdlines;
    Architecture arch;

    static bool sortByInterfaceName(const TableEntry &a, const TableEntry &b) {
        return a.interfaceName < b.interfaceName;
    };
    static bool sortByServerPid(const TableEntry &a, const TableEntry &b) {
        return a.serverPid < b.serverPid;
    };

    std::string getThreadUsage() const {
        if (threadCount == 0) {
            return "N/A";
        }

        return std::to_string(threadUsage) + "/" + std::to_string(threadCount);
    }
};

struct Table {
    using Entries = std::vector<TableEntry>;
    std::string description;
    Entries entries;

    Entries::iterator begin() { return entries.begin(); }
    Entries::const_iterator begin() const { return entries.begin(); }
    Entries::iterator end() { return entries.end(); }
    Entries::const_iterator end() const { return entries.end(); }
};

using TableEntryCompare = std::function<bool(const TableEntry &, const TableEntry &)>;

enum : unsigned int {
    ENABLE_INTERFACE_NAME = 1 << 0,
    ENABLE_TRANSPORT      = 1 << 1,
    ENABLE_SERVER_PID     = 1 << 2,
    ENABLE_SERVER_ADDR    = 1 << 3,
    ENABLE_CLIENT_PIDS    = 1 << 4,
    ENABLE_ARCH           = 1 << 5,
    ENABLE_THREADS        = 1 << 6,
};

using TableEntrySelect = unsigned int;

enum {
    NO_PID = -1,
    NO_PTR = 0
};

}  // namespace lshal
}  // namespace android

#endif  // FRAMEWORK_NATIVE_CMDS_LSHAL_TABLE_ENTRY_H_
