/**
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef ANDROID_OS_DUMPSTATE_H_
#define ANDROID_OS_DUMPSTATE_H_

#include <mutex>
#include <vector>

#include <binder/BinderService.h>

#include "android/os/BnDumpstate.h"
#include "android/os/BnDumpstateToken.h"
#include "dumpstate.h"

namespace android {
namespace os {

class DumpstateService : public BinderService<DumpstateService>, public BnDumpstate {
  public:
    DumpstateService();

    static status_t Start();
    static char const* getServiceName();

    status_t dump(int fd, const Vector<String16>& args) override;
    binder::Status setListener(const std::string& name, const sp<IDumpstateListener>& listener,
                               sp<IDumpstateToken>* returned_token) override;

  private:
    Dumpstate& ds_;
    std::mutex lock_;
};

}  // namespace os
}  // namespace android

#endif  // ANDROID_OS_DUMPSTATE_H_
