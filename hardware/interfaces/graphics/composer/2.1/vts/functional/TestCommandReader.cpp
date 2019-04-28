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

#include "TestCommandReader.h"

#include <gtest/gtest.h>

namespace android {
namespace hardware {
namespace graphics {
namespace composer {
namespace V2_1 {
namespace tests {

void TestCommandReader::parse() {
  while (!isEmpty()) {
    IComposerClient::Command command;
    uint16_t length;
    ASSERT_TRUE(beginCommand(&command, &length));

    switch (command) {
      case IComposerClient::Command::SET_ERROR: {
        ASSERT_EQ(2, length);
        auto loc = read();
        auto err = readSigned();
        GTEST_FAIL() << "unexpected error " << err << " at location " << loc;
      } break;
      case IComposerClient::Command::SELECT_DISPLAY:
      case IComposerClient::Command::SET_CHANGED_COMPOSITION_TYPES:
      case IComposerClient::Command::SET_DISPLAY_REQUESTS:
      case IComposerClient::Command::SET_PRESENT_FENCE:
      case IComposerClient::Command::SET_RELEASE_FENCES:
        break;
      default:
        GTEST_FAIL() << "unexpected return command " << std::hex
                     << static_cast<int>(command);
        break;
    }

    endCommand();
  }
}

}  // namespace tests
}  // namespace V2_1
}  // namespace composer
}  // namespace graphics
}  // namespace hardware
}  // namespace android
