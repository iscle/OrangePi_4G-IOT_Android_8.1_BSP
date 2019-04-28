/*
 * Copyright (C) 2017 The Android Open Source Project
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

#define LOG_TAG "lshal"
#include <android-base/logging.h>

#include "Lshal.h"

#include <set>
#include <string>

#include <hidl/ServiceManagement.h>

#include "DebugCommand.h"
#include "ListCommand.h"
#include "PipeRelay.h"

namespace android {
namespace lshal {

using ::android::hidl::manager::V1_0::IServiceManager;

Lshal::Lshal()
    : mOut(std::cout), mErr(std::cerr),
      mServiceManager(::android::hardware::defaultServiceManager()),
      mPassthroughManager(::android::hardware::getPassthroughServiceManager()) {
}

Lshal::Lshal(std::ostream &out, std::ostream &err,
            sp<hidl::manager::V1_0::IServiceManager> serviceManager,
            sp<hidl::manager::V1_0::IServiceManager> passthroughManager)
    : mOut(out), mErr(err),
      mServiceManager(serviceManager),
      mPassthroughManager(passthroughManager) {

}

void Lshal::usage(const std::string &command) const {
    static const std::string helpSummary =
            "lshal: List and debug HALs.\n"
            "\n"
            "commands:\n"
            "    help            Print help message\n"
            "    list            list HALs\n"
            "    debug           debug a specified HAL\n"
            "\n"
            "If no command is specified, `list` is the default.\n";

    static const std::string list =
            "list:\n"
            "    lshal\n"
            "    lshal list\n"
            "        List all hals with default ordering and columns (`lshal list -ipc`)\n"
            "    lshal list [-h|--help]\n"
            "        -h, --help: Print help message for list (`lshal help list`)\n"
            "    lshal [list] [--interface|-i] [--transport|-t] [-r|--arch] [-e|--threads]\n"
            "            [--pid|-p] [--address|-a] [--clients|-c] [--cmdline|-m]\n"
            "            [--sort={interface|i|pid|p}] [--init-vintf[=<output file>]]\n"
            "            [--debug|-d[=<output file>]]\n"
            "        -i, --interface: print the interface name column\n"
            "        -n, --instance: print the instance name column\n"
            "        -t, --transport: print the transport mode column\n"
            "        -r, --arch: print if the HAL is in 64-bit or 32-bit\n"
            "        -e, --threads: print currently used/available threads\n"
            "                       (note, available threads created lazily)\n"
            "        -p, --pid: print the server PID, or server cmdline if -m is set\n"
            "        -a, --address: print the server object address column\n"
            "        -c, --clients: print the client PIDs, or client cmdlines if -m is set\n"
            "        -m, --cmdline: print cmdline instead of PIDs\n"
            "        -d[=<output file>], --debug[=<output file>]: emit debug info from \n"
            "                IBase::debug with empty options\n"
            "        --sort=i, --sort=interface: sort by interface name\n"
            "        --sort=p, --sort=pid: sort by server pid\n"
            "        --init-vintf=<output file>: form a skeleton HAL manifest to specified\n"
            "                      file, or stdout if no file specified.\n";

    static const std::string debug =
            "debug:\n"
            "    lshal debug <interface> [options [options [...]]] \n"
            "        Print debug information of a specified interface.\n"
            "        <inteface>: Format is `android.hardware.foo@1.0::IFoo/default`.\n"
            "            If instance name is missing `default` is used.\n"
            "        options: space separated options to IBase::debug.\n";

    static const std::string help =
            "help:\n"
            "    lshal -h\n"
            "    lshal --help\n"
            "    lshal help\n"
            "        Print this help message\n"
            "    lshal help list\n"
            "        Print help message for list\n"
            "    lshal help debug\n"
            "        Print help message for debug\n";

    if (command == "list") {
        mErr << list;
        return;
    }
    if (command == "debug") {
        mErr << debug;
        return;
    }

    mErr << helpSummary << "\n" << list << "\n" << debug << "\n" << help;
}

// A unique_ptr type using a custom deleter function.
template<typename T>
using deleted_unique_ptr = std::unique_ptr<T, std::function<void(T *)> >;

static hardware::hidl_vec<hardware::hidl_string> convert(const std::vector<std::string> &v) {
    hardware::hidl_vec<hardware::hidl_string> hv;
    hv.resize(v.size());
    for (size_t i = 0; i < v.size(); ++i) {
        hv[i].setToExternal(v[i].c_str(), v[i].size());
    }
    return hv;
}

Status Lshal::emitDebugInfo(
        const std::string &interfaceName,
        const std::string &instanceName,
        const std::vector<std::string> &options,
        std::ostream &out,
        NullableOStream<std::ostream> err) const {
    using android::hidl::base::V1_0::IBase;

    hardware::Return<sp<IBase>> retBase = serviceManager()->get(interfaceName, instanceName);

    if (!retBase.isOk()) {
        std::string msg = "Cannot get " + interfaceName + "/" + instanceName + ": "
                + retBase.description();
        err << msg << std::endl;
        LOG(ERROR) << msg;
        return TRANSACTION_ERROR;
    }

    sp<IBase> base = retBase;
    if (base == nullptr) {
        std::string msg = interfaceName + "/" + instanceName + " does not exist, or "
                + "no permission to connect.";
        err << msg << std::endl;
        LOG(ERROR) << msg;
        return NO_INTERFACE;
    }

    PipeRelay relay(out);

    if (relay.initCheck() != OK) {
        std::string msg = "PipeRelay::initCheck() FAILED w/ " + std::to_string(relay.initCheck());
        err << msg << std::endl;
        LOG(ERROR) << msg;
        return IO_ERROR;
    }

    deleted_unique_ptr<native_handle_t> fdHandle(
        native_handle_create(1 /* numFds */, 0 /* numInts */),
        native_handle_delete);

    fdHandle->data[0] = relay.fd();

    hardware::Return<void> ret = base->debug(fdHandle.get(), convert(options));

    if (!ret.isOk()) {
        std::string msg = "debug() FAILED on " + interfaceName + "/" + instanceName + ": "
                + ret.description();
        err << msg << std::endl;
        LOG(ERROR) << msg;
        return TRANSACTION_ERROR;
    }
    return OK;
}

Status Lshal::parseArgs(const Arg &arg) {
    static std::set<std::string> sAllCommands{"list", "debug", "help"};
    optind = 1;
    if (optind >= arg.argc) {
        // no options at all.
        return OK;
    }
    mCommand = arg.argv[optind];
    if (sAllCommands.find(mCommand) != sAllCommands.end()) {
        ++optind;
        return OK; // mCommand is set correctly
    }

    if (mCommand.size() > 0 && mCommand[0] == '-') {
        // first argument is an option, set command to "" (which is recognized as "list")
        mCommand = "";
        return OK;
    }

    mErr << arg.argv[0] << ": unrecognized option `" << arg.argv[optind] << "`" << std::endl;
    usage();
    return USAGE;
}

void signalHandler(int sig) {
    if (sig == SIGINT) {
        int retVal;
        pthread_exit(&retVal);
    }
}

Status Lshal::main(const Arg &arg) {
    // Allow SIGINT to terminate all threads.
    signal(SIGINT, signalHandler);

    Status status = parseArgs(arg);
    if (status != OK) {
        return status;
    }
    if (mCommand == "help") {
        usage(optind < arg.argc ? arg.argv[optind] : "");
        return USAGE;
    }
    // Default command is list
    if (mCommand == "list" || mCommand == "") {
        return ListCommand{*this}.main(mCommand, arg);
    }
    if (mCommand == "debug") {
        return DebugCommand{*this}.main(mCommand, arg);
    }
    usage();
    return USAGE;
}

NullableOStream<std::ostream> Lshal::err() const {
    return mErr;
}
NullableOStream<std::ostream> Lshal::out() const {
    return mOut;
}

const sp<IServiceManager> &Lshal::serviceManager() const {
    return mServiceManager;
}

const sp<IServiceManager> &Lshal::passthroughManager() const {
    return mPassthroughManager;
}

}  // namespace lshal
}  // namespace android
