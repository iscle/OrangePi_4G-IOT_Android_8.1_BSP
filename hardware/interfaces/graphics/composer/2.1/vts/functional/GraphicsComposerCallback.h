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

#ifndef GRAPHICS_COMPOSER_CALLBACK_H
#define GRAPHICS_COMPOSER_CALLBACK_H

#include <android/hardware/graphics/composer/2.1/IComposerCallback.h>

#include <mutex>
#include <unordered_set>

namespace android {
namespace hardware {
namespace graphics {
namespace composer {
namespace V2_1 {
namespace tests {

// IComposerCallback to be installed with IComposerClient::registerCallback.
class GraphicsComposerCallback : public IComposerCallback {
 public:
  void setVsyncAllowed(bool allowed);

  std::vector<Display> getDisplays() const;

  int getInvalidHotplugCount() const;

  int getInvalidRefreshCount() const;

  int getInvalidVsyncCount() const;

 private:
  Return<void> onHotplug(Display display, Connection connection) override;
  Return<void> onRefresh(Display display) override;
  Return<void> onVsync(Display display, int64_t) override;

  mutable std::mutex mMutex;
  // the set of all currently connected displays
  std::unordered_set<Display> mDisplays;
  // true only when vsync is enabled
  bool mVsyncAllowed = true;

  // track invalid callbacks
  int mInvalidHotplugCount = 0;
  int mInvalidRefreshCount = 0;
  int mInvalidVsyncCount = 0;
};

}  // namespace tests
}  // namespace V2_1
}  // namespace composer
}  // namespace graphics
}  // namespace hardware
}  // namespace android

#endif  // GRAPHICS_COMPOSER_CALLBACK_H
