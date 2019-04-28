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

#include "DebugCommand.h"

#include "Lshal.h"

namespace android {
namespace lshal {

DebugCommand::DebugCommand(Lshal &lshal) : mLshal(lshal) {
}

Status DebugCommand::parseArgs(const std::string &command, const Arg &arg) {
    if (optind >= arg.argc) {
        mLshal.usage(command);
        return USAGE;
    }
    mInterfaceName = arg.argv[optind];
    ++optind;
    for (; optind < arg.argc; ++optind) {
        mOptions.push_back(arg.argv[optind]);
    }
    return OK;
}

Status DebugCommand::main(const std::string &command, const Arg &arg) {
    Status status = parseArgs(command, arg);
    if (status != OK) {
        return status;
    }
    auto pair = splitFirst(mInterfaceName, '/');
    return mLshal.emitDebugInfo(
            pair.first, pair.second.empty() ? "default" : pair.second, mOptions,
            mLshal.out().buf(),
            mLshal.err());
}

}  // namespace lshal
}  // namespace android

