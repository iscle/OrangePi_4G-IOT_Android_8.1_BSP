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

#include "GraphicsComposerCallback.h"

namespace android {
namespace hardware {
namespace graphics {
namespace composer {
namespace V2_1 {
namespace tests {

void GraphicsComposerCallback::setVsyncAllowed(bool allowed) {
  std::lock_guard<std::mutex> lock(mMutex);
  mVsyncAllowed = allowed;
}

std::vector<Display> GraphicsComposerCallback::getDisplays() const {
  std::lock_guard<std::mutex> lock(mMutex);
  return std::vector<Display>(mDisplays.begin(), mDisplays.end());
}

int GraphicsComposerCallback::getInvalidHotplugCount() const {
  std::lock_guard<std::mutex> lock(mMutex);
  return mInvalidHotplugCount;
}

int GraphicsComposerCallback::getInvalidRefreshCount() const {
  std::lock_guard<std::mutex> lock(mMutex);
  return mInvalidRefreshCount;
}

int GraphicsComposerCallback::getInvalidVsyncCount() const {
  std::lock_guard<std::mutex> lock(mMutex);
  return mInvalidVsyncCount;
}

Return<void> GraphicsComposerCallback::onHotplug(Display display,
                                                 Connection connection) {
  std::lock_guard<std::mutex> lock(mMutex);

  if (connection == Connection::CONNECTED) {
    if (!mDisplays.insert(display).second) {
      mInvalidHotplugCount++;
    }
  } else if (connection == Connection::DISCONNECTED) {
    if (!mDisplays.erase(display)) {
      mInvalidHotplugCount++;
    }
  }

  return Void();
}

Return<void> GraphicsComposerCallback::onRefresh(Display display) {
  std::lock_guard<std::mutex> lock(mMutex);

  if (mDisplays.count(display) == 0) {
    mInvalidRefreshCount++;
  }

  return Void();
}

Return<void> GraphicsComposerCallback::onVsync(Display display, int64_t) {
  std::lock_guard<std::mutex> lock(mMutex);

  if (!mVsyncAllowed || mDisplays.count(display) == 0) {
    mInvalidVsyncCount++;
  }

  return Void();
}

}  // namespace tests
}  // namespace V2_1
}  // namespace composer
}  // namespace graphics
}  // namespace hardware
}  // namespace android
