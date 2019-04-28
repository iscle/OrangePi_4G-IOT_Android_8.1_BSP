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

#ifndef FRAMEWORK_NATIVE_CMDS_LSHAL_DEBUG_COMMAND_H_
#define FRAMEWORK_NATIVE_CMDS_LSHAL_DEBUG_COMMAND_H_

#include <string>

#include <android-base/macros.h>

#include "utils.h"

namespace android {
namespace lshal {

class Lshal;

class DebugCommand {
public:
    DebugCommand(Lshal &lshal);
    Status main(const std::string &command, const Arg &arg);
private:
    Status parseArgs(const std::string &command, const Arg &arg);

    Lshal &mLshal;
    std::string mInterfaceName;
    std::vector<std::string> mOptions;

    DISALLOW_COPY_AND_ASSIGN(DebugCommand);
};


}  // namespace lshal
}  // namespace android

#endif  // FRAMEWORK_NATIVE_CMDS_LSHAL_DEBUG_COMMAND_H_
