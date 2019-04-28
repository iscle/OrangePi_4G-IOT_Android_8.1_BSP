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

#ifndef TEST_COMMAND_READER_H
#define TEST_COMMAND_READER_H

#include <IComposerCommandBuffer.h>

namespace android {
namespace hardware {
namespace graphics {
namespace composer {
namespace V2_1 {
namespace tests {

// A command parser that checks that no error nor unexpected commands are
// returned.
class TestCommandReader : public CommandReaderBase {
 public:
  // Parse all commands in the return command queue.  Call GTEST_FAIL() for
  // unexpected errors or commands.
  void parse();
};

}  // namespace tests
}  // namespace V2_1
}  // namespace composer
}  // namespace graphics
}  // namespace hardware
}  // namespace android

#endif  // TEST_COMMAND_READER_H
