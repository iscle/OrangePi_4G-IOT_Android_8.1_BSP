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
#define LOG_TAG "android.hardware.gatekeeper@1.0-service"

#include <dlfcn.h>

#include <log/log.h>

#include "Gatekeeper.h"

namespace android {
namespace hardware {
namespace gatekeeper {
namespace V1_0 {
namespace implementation {

Gatekeeper::Gatekeeper()
{
    int ret = hw_get_module_by_class(GATEKEEPER_HARDWARE_MODULE_ID, NULL, &module);
    device = NULL;

    if (!ret) {
        ret = gatekeeper_open(module, &device);
    }
    if (ret < 0) {
        LOG_ALWAYS_FATAL_IF(ret < 0, "Unable to open GateKeeper HAL");
    }
}

Gatekeeper::~Gatekeeper()
{
    if (device != nullptr) {
        int ret = gatekeeper_close(device);
        if (ret < 0) {
            ALOGE("Unable to close GateKeeper HAL");
        }
    }
    dlclose(module->dso);
}

// Methods from ::android::hardware::gatekeeper::V1_0::IGatekeeper follow.
Return<void> Gatekeeper::enroll(uint32_t uid,
        const hidl_vec<uint8_t>& currentPasswordHandle,
        const hidl_vec<uint8_t>& currentPassword,
        const hidl_vec<uint8_t>& desiredPassword,
        enroll_cb cb)
{
    GatekeeperResponse rsp;
    uint8_t *enrolled_password_handle = nullptr;
    uint32_t enrolled_password_handle_length = 0;

    int ret = device->enroll(device, uid,
            currentPasswordHandle.data(), currentPasswordHandle.size(),
            currentPassword.data(), currentPassword.size(),
            desiredPassword.data(), desiredPassword.size(),
            &enrolled_password_handle, &enrolled_password_handle_length);
    if (!ret) {
        rsp.data.setToExternal(enrolled_password_handle,
                               enrolled_password_handle_length,
                               true);
        rsp.code = GatekeeperStatusCode::STATUS_OK;
    } else if (ret > 0) {
        rsp.timeout = ret;
        rsp.code = GatekeeperStatusCode::ERROR_RETRY_TIMEOUT;
    } else {
        rsp.code = GatekeeperStatusCode::ERROR_GENERAL_FAILURE;
    }
    cb(rsp);
    return Void();
}

Return<void> Gatekeeper::verify(uint32_t uid,
                                uint64_t challenge,
                                const hidl_vec<uint8_t>& enrolledPasswordHandle,
                                const hidl_vec<uint8_t>& providedPassword,
                                verify_cb cb)
{
    GatekeeperResponse rsp;
    uint8_t *auth_token = nullptr;
    uint32_t auth_token_length = 0;
    bool request_reenroll = false;

    int ret = device->verify(device, uid, challenge,
            enrolledPasswordHandle.data(), enrolledPasswordHandle.size(),
            providedPassword.data(), providedPassword.size(),
            &auth_token, &auth_token_length,
            &request_reenroll);
    if (!ret) {
        rsp.data.setToExternal(auth_token, auth_token_length, true);
        if (request_reenroll) {
            rsp.code = GatekeeperStatusCode::STATUS_REENROLL;
        } else {
            rsp.code = GatekeeperStatusCode::STATUS_OK;
        }
    } else if (ret > 0) {
        rsp.timeout = ret;
        rsp.code = GatekeeperStatusCode::ERROR_RETRY_TIMEOUT;
    } else {
        rsp.code = GatekeeperStatusCode::ERROR_GENERAL_FAILURE;
    }
    cb(rsp);
    return Void();
}

Return<void> Gatekeeper::deleteUser(uint32_t uid, deleteUser_cb cb)  {
    GatekeeperResponse rsp;

    if (device->delete_user != nullptr) {
        int ret = device->delete_user(device, uid);
        if (!ret) {
            rsp.code = GatekeeperStatusCode::STATUS_OK;
        } else if (ret > 0) {
            rsp.timeout = ret;
            rsp.code = GatekeeperStatusCode::ERROR_RETRY_TIMEOUT;
        } else {
            rsp.code = GatekeeperStatusCode::ERROR_GENERAL_FAILURE;
        }
    } else {
        rsp.code = GatekeeperStatusCode::ERROR_NOT_IMPLEMENTED;
    }
    cb(rsp);
    return Void();
}

Return<void> Gatekeeper::deleteAllUsers(deleteAllUsers_cb cb)  {
    GatekeeperResponse rsp;
    if (device->delete_all_users != nullptr) {
        int ret = device->delete_all_users(device);
        if (!ret) {
            rsp.code = GatekeeperStatusCode::STATUS_OK;
        } else if (ret > 0) {
            rsp.timeout = ret;
            rsp.code = GatekeeperStatusCode::ERROR_RETRY_TIMEOUT;
        } else {
            rsp.code = GatekeeperStatusCode::ERROR_GENERAL_FAILURE;
        }
    } else {
        rsp.code = GatekeeperStatusCode::ERROR_NOT_IMPLEMENTED;
    }
    cb(rsp);
    return Void();
}

IGatekeeper* HIDL_FETCH_IGatekeeper(const char* /* name */) {
    return new Gatekeeper();
}

} // namespace implementation
}  // namespace V1_0
}  // namespace gatekeeper
}  // namespace hardware
}  // namespace android
