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
#ifndef ANDROID_HARDWARE_GATEKEEPER_V1_0_GATEKEEPER_H
#define ANDROID_HARDWARE_GATEKEEPER_V1_0_GATEKEEPER_H

#include <android/hardware/gatekeeper/1.0/IGatekeeper.h>
#include <hidl/Status.h>

#include <hidl/MQDescriptor.h>

#include <hardware/hardware.h>
#include <hardware/gatekeeper.h>

namespace android {
namespace hardware {
namespace gatekeeper {
namespace V1_0 {
namespace implementation {

using ::android::hardware::gatekeeper::V1_0::GatekeeperResponse;
using ::android::hardware::gatekeeper::V1_0::IGatekeeper;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::hidl_vec;
using ::android::hardware::hidl_string;
using ::android::sp;

class Gatekeeper : public IGatekeeper {
public:
    Gatekeeper();
    ~Gatekeeper();

    // Methods from ::android::hardware::gatekeeper::V1_0::IGatekeeper follow.
    Return<void> enroll(uint32_t uid,
                        const hidl_vec<uint8_t>& currentPasswordHandle,
                        const hidl_vec<uint8_t>& currentPassword,
                        const hidl_vec<uint8_t>& desiredPassword,
                        enroll_cb _hidl_cb)  override;
    Return<void> verify(uint32_t uid,
                        uint64_t challenge,
                        const hidl_vec<uint8_t>& enrolledPasswordHandle,
                        const hidl_vec<uint8_t>& providedPassword,
                        verify_cb _hidl_cb)  override;
    Return<void> deleteUser(uint32_t uid, deleteUser_cb _hidl_cb)  override;
    Return<void> deleteAllUsers(deleteAllUsers_cb _hidl_cb)  override;
private:
    gatekeeper_device_t *device;
    const hw_module_t *module;
};

extern "C" IGatekeeper* HIDL_FETCH_IGatekeeper(const char* name);

}  // namespace implementation
}  // namespace V1_0
}  // namespace gatekeeper
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_GATEKEEPER_V1_0_GATEKEEPER_H
