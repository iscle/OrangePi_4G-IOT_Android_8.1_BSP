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

#include "Contexthub.h"

#include <inttypes.h>

#include <log/log.h>

#include <android/hardware/contexthub/1.0/IContexthub.h>
#include <hardware/context_hub.h>
#include <sys/endian.h>

#undef LOG_TAG
#define LOG_TAG "ContextHubHalAdapter"

namespace android {
namespace hardware {
namespace contexthub {
namespace V1_0 {
namespace implementation {

static constexpr uint64_t ALL_APPS = UINT64_C(0xFFFFFFFFFFFFFFFF);

Contexthub::Contexthub()
        : mInitCheck(NO_INIT),
          mContextHubModule(nullptr),
          mDeathRecipient(new DeathRecipient(this)),
          mIsTransactionPending(false) {
    const hw_module_t *module;

    mInitCheck = hw_get_module(CONTEXT_HUB_MODULE_ID, &module);

    if (mInitCheck != OK) {
        ALOGE("Could not load %s module: %s", CONTEXT_HUB_MODULE_ID, strerror(-mInitCheck));
    } else if (module == nullptr) {
        ALOGE("hal returned succes but a null module!");
        // Assign an error, this should not really happen...
        mInitCheck = UNKNOWN_ERROR;
    } else {
        ALOGI("Loaded Context Hub module");
        mContextHubModule = reinterpret_cast<const context_hub_module_t *>(module);
    }
}

bool Contexthub::setOsAppAsDestination(hub_message_t *msg, int hubId) {
    if (!isValidHubId(hubId)) {
        ALOGW("%s: Hub information is null for hubHandle %d",
              __FUNCTION__,
              hubId);
        return false;
    } else {
        msg->app_name = mCachedHubInfo[hubId].osAppName;
        return true;
    }
}

Return<void> Contexthub::getHubs(getHubs_cb _hidl_cb) {
    std::vector<ContextHub> hubs;
    if (isInitialized()) {
        const context_hub_t *hubArray = nullptr;
        size_t numHubs;

        // Explicitly discarding const. HAL method discards it.
        numHubs = mContextHubModule->get_hubs(const_cast<context_hub_module_t *>(mContextHubModule),
                                              &hubArray);
        ALOGI("Context Hub Hal Adapter reports %zu hubs", numHubs);

        mCachedHubInfo.clear();

        for (size_t i = 0; i < numHubs; i++) {
            CachedHubInformation info;
            ContextHub c;

            c.hubId = hubArray[i].hub_id;
            c.name = hubArray[i].name;
            c.vendor = hubArray[i].vendor;
            c.toolchain = hubArray[i].toolchain;
            c.toolchainVersion = hubArray[i].toolchain_version;
            c.platformVersion = hubArray[i].platform_version;
            c.maxSupportedMsgLen = hubArray[i].max_supported_msg_len;
            c.peakMips = hubArray[i].peak_mips;
            c.peakPowerDrawMw = hubArray[i].peak_power_draw_mw;
            c.stoppedPowerDrawMw = hubArray[i].stopped_power_draw_mw;
            c.sleepPowerDrawMw = hubArray[i].sleep_power_draw_mw;

            info.callback = nullptr;
            info.osAppName = hubArray[i].os_app_name;
            mCachedHubInfo[hubArray[i].hub_id] = info;

            hubs.push_back(c);
        }
    } else {
        ALOGW("Context Hub Hal Adapter not initialized");
    }

    _hidl_cb(hubs);
    return Void();
}

Contexthub::DeathRecipient::DeathRecipient(sp<Contexthub> contexthub)
        : mContexthub(contexthub) {}

void Contexthub::DeathRecipient::serviceDied(
        uint64_t cookie,
        const wp<::android::hidl::base::V1_0::IBase>& /*who*/) {
    uint32_t hubId = static_cast<uint32_t>(cookie);
    mContexthub->handleServiceDeath(hubId);
}

bool Contexthub::isValidHubId(uint32_t hubId) {
    if (!mCachedHubInfo.count(hubId)) {
        ALOGW("Hub information not found for hubId %" PRIu32, hubId);
        return false;
    } else {
        return true;
    }
}

sp<IContexthubCallback> Contexthub::getCallBackForHubId(uint32_t hubId) {
    if (!isValidHubId(hubId)) {
        return nullptr;
    } else {
        return mCachedHubInfo[hubId].callback;
    }
}

Return<Result> Contexthub::sendMessageToHub(uint32_t hubId,
                                            const ContextHubMsg &msg) {
    if (!isInitialized()) {
        return Result::NOT_INIT;
    }

    if (!isValidHubId(hubId) || msg.msg.size() > UINT32_MAX) {
        return Result::BAD_PARAMS;
    }

    hub_message_t txMsg = {
        .app_name.id = msg.appName,
        .message_type = msg.msgType,
        .message_len = static_cast<uint32_t>(msg.msg.size()), // Note the check above
        .message = static_cast<const uint8_t *>(msg.msg.data()),
    };

    ALOGI("Sending msg of type %" PRIu32 ", size %" PRIu32 " to app 0x%" PRIx64,
          txMsg.message_type,
          txMsg.message_len,
          txMsg.app_name.id);

    if(mContextHubModule->send_message(hubId, &txMsg) != 0) {
        return Result::TRANSACTION_FAILED;
    }

    return Result::OK;
}

Return<Result> Contexthub::reboot(uint32_t hubId) {
    if (!isInitialized()) {
      return Result::NOT_INIT;
    }

    hub_message_t msg;

    if (setOsAppAsDestination(&msg, hubId) == false) {
        return Result::BAD_PARAMS;
    }

    msg.message_type = CONTEXT_HUB_OS_REBOOT;
    msg.message_len = 0;
    msg.message = nullptr;

    if(mContextHubModule->send_message(hubId, &msg) != 0) {
        return Result::TRANSACTION_FAILED;
    } else {
        return Result::OK;
    }
}

Return<Result> Contexthub::registerCallback(uint32_t hubId,
                                            const sp<IContexthubCallback> &cb) {
    Return<Result> retVal = Result::BAD_PARAMS;

    if (!isInitialized()) {
        // Not initilalized
        ALOGW("Context hub not initialized successfully");
        retVal = Result::NOT_INIT;
    } else if (!isValidHubId(hubId)) {
        // Initialized, but hubId is  not valid
        retVal = Result::BAD_PARAMS;
    } else if (mContextHubModule->subscribe_messages(hubId,
                                                     contextHubCb,
                                                     this) == 0) {
        // Initialized && valid hub && subscription successful
        if (mCachedHubInfo[hubId].callback != nullptr) {
            ALOGD("Modifying callback for hubId %" PRIu32, hubId);
            mCachedHubInfo[hubId].callback->unlinkToDeath(mDeathRecipient);
        }

        mCachedHubInfo[hubId].callback = cb;
        if (cb != nullptr) {
            Return<bool> linkResult = cb->linkToDeath(mDeathRecipient, hubId);
            bool linkSuccess = linkResult.isOk() ?
                static_cast<bool>(linkResult) : false;
            if (!linkSuccess) {
                ALOGW("Couldn't link death recipient for hubId %" PRIu32,
                      hubId);
            }
        }
        retVal = Result::OK;
    } else {
        // Initalized && valid hubId - but subscription unsuccessful
        // This is likely an internal error in the HAL implementation, but we
        // cannot add more information.
        ALOGW("Could not subscribe to the hub for callback");
        retVal = Result::UNKNOWN_FAILURE;
    }

    return retVal;
}

static bool isValidOsStatus(const uint8_t *msg,
                            size_t msgLen,
                            status_response_t *rsp) {
    // Workaround a bug in some HALs
    if (msgLen == 1) {
        rsp->result = msg[0];
        return true;
    }

    if (msg == nullptr || msgLen != sizeof(*rsp)) {
        ALOGI("Received invalid response (is null : %d, size %zu)",
              msg == nullptr ? 1 : 0,
              msgLen);
        return false;
    }

    memcpy(rsp, msg, sizeof(*rsp));

    // No sanity checks on return values
    return true;
}

int Contexthub::handleOsMessage(sp<IContexthubCallback> cb,
                                uint32_t msgType,
                                const uint8_t *msg,
                                int msgLen) {
    int retVal = -1;


    switch(msgType) {
        case CONTEXT_HUB_APPS_ENABLE:
        case CONTEXT_HUB_APPS_DISABLE:
        case CONTEXT_HUB_LOAD_APP:
        case CONTEXT_HUB_UNLOAD_APP:
        {
            struct status_response_t rsp;
            TransactionResult result;
            if (isValidOsStatus(msg, msgLen, &rsp) && rsp.result == 0) {
                retVal = 0;
                result = TransactionResult::SUCCESS;
            } else {
                result = TransactionResult::FAILURE;
            }

            if (cb != nullptr) {
                cb->handleTxnResult(mTransactionId, result);
            }
            retVal = 0;
            mIsTransactionPending = false;
            break;
        }

        case CONTEXT_HUB_QUERY_APPS:
        {
            std::vector<HubAppInfo> apps;
            int numApps = msgLen / sizeof(hub_app_info);
            const hub_app_info *unalignedInfoAddr = reinterpret_cast<const hub_app_info *>(msg);

            for (int i = 0; i < numApps; i++) {
                hub_app_info query_info;
                memcpy(&query_info, &unalignedInfoAddr[i], sizeof(query_info));
                HubAppInfo app;
                app.appId = query_info.app_name.id;
                app.version = query_info.version;
                // TODO :: Add memory ranges

                apps.push_back(app);
            }

            if (cb != nullptr) {
                cb->handleAppsInfo(apps);
            }
            retVal = 0;
            break;
        }

        case CONTEXT_HUB_QUERY_MEMORY:
        {
            // Deferring this use
            retVal = 0;
            break;
        }

        case CONTEXT_HUB_OS_REBOOT:
        {
            mIsTransactionPending = false;
            if (cb != nullptr) {
                cb->handleHubEvent(AsyncEventType::RESTARTED);
            }
            retVal = 0;
            break;
        }

        default:
        {
            retVal = -1;
            break;
        }
      }

      return retVal;
}

void Contexthub::handleServiceDeath(uint32_t hubId) {
    ALOGI("Callback/service died for hubId %" PRIu32, hubId);
    int ret = mContextHubModule->subscribe_messages(hubId, nullptr, nullptr);
    if (ret != 0) {
        ALOGW("Failed to unregister callback from hubId %" PRIu32 ": %d",
              hubId, ret);
    }
    mCachedHubInfo[hubId].callback.clear();
}

int Contexthub::contextHubCb(uint32_t hubId,
                             const struct hub_message_t *rxMsg,
                             void *cookie) {
    Contexthub *obj = static_cast<Contexthub *>(cookie);

    if (rxMsg == nullptr) {
        ALOGW("Ignoring NULL message");
        return -1;
    }

    if (!obj->isValidHubId(hubId)) {
        ALOGW("Invalid hub Id %" PRIu32, hubId);
        return -1;
    }

    sp<IContexthubCallback> cb = obj->getCallBackForHubId(hubId);

    if (cb == nullptr) {
        // This should not ever happen
        ALOGW("No callback registered, returning");
        return -1;
    }

    if (rxMsg->message_type < CONTEXT_HUB_TYPE_PRIVATE_MSG_BASE) {
        obj->handleOsMessage(cb,
                             rxMsg->message_type,
                             static_cast<const uint8_t *>(rxMsg->message),
                             rxMsg->message_len);
    } else {
        ContextHubMsg msg;

        msg.appName = rxMsg->app_name.id;
        msg.msgType = rxMsg->message_type;
        msg.msg = std::vector<uint8_t>(static_cast<const uint8_t *>(rxMsg->message),
                                       static_cast<const uint8_t *>(rxMsg->message) +
                                       rxMsg->message_len);

        cb->handleClientMsg(msg);
    }

    return 0;
}

Return<Result> Contexthub::unloadNanoApp(uint32_t hubId,
                                         uint64_t appId,
                                         uint32_t transactionId) {
    if (!isInitialized()) {
      return Result::NOT_INIT;
    }

    if (mIsTransactionPending) {
        return Result::TRANSACTION_PENDING;
    }

    hub_message_t msg;

    if (setOsAppAsDestination(&msg, hubId) == false) {
        return Result::BAD_PARAMS;
    }

    struct apps_disable_request_t req;

    msg.message_type = CONTEXT_HUB_UNLOAD_APP;
    msg.message_len = sizeof(req);
    msg.message = &req;
    req.app_name.id = appId;

    if(mContextHubModule->send_message(hubId, &msg) != 0) {
        return Result::TRANSACTION_FAILED;
    } else {
        mTransactionId = transactionId;
        mIsTransactionPending = true;
        return Result::OK;
    }
}

Return<Result> Contexthub::loadNanoApp(uint32_t hubId,
                                       const NanoAppBinary& appBinary,
                                       uint32_t transactionId) {
    if (!isInitialized()) {
      return Result::NOT_INIT;
    }

    if (mIsTransactionPending) {
        return Result::TRANSACTION_PENDING;
    }

    hub_message_t hubMsg;

    if (setOsAppAsDestination(&hubMsg, hubId) == false) {
        return Result::BAD_PARAMS;
    }

    // Data from the nanoapp header is passed through HIDL as explicit fields,
    // but the legacy HAL expects it prepended to the binary, therefore we must
    // reconstruct it here prior to passing to the legacy HAL.
    const struct nano_app_binary_t header = {
        .header_version = htole32(1),
        .magic = htole32(NANOAPP_MAGIC),
        .app_id.id = htole64(appBinary.appId),
        .app_version = htole32(appBinary.appVersion),
        .flags = htole32(appBinary.flags),
        .hw_hub_type = htole64(0),
        .target_chre_api_major_version = appBinary.targetChreApiMajorVersion,
        .target_chre_api_minor_version = appBinary.targetChreApiMinorVersion,
    };
    const uint8_t *headerBytes = reinterpret_cast<const uint8_t *>(&header);

    std::vector<uint8_t> binaryWithHeader(appBinary.customBinary);
    binaryWithHeader.insert(binaryWithHeader.begin(),
                            headerBytes,
                            headerBytes + sizeof(header));

    hubMsg.message_type = CONTEXT_HUB_LOAD_APP;
    hubMsg.message_len = binaryWithHeader.size();
    hubMsg.message = binaryWithHeader.data();

    if (mContextHubModule->send_message(hubId, &hubMsg) != 0) {
        return Result::TRANSACTION_FAILED;
    } else {
        mTransactionId = transactionId;
        mIsTransactionPending = true;
        return Result::OK;
    }
}

Return<Result> Contexthub::enableNanoApp(uint32_t hubId,
                                         uint64_t appId,
                                         uint32_t transactionId) {
    if (!isInitialized()) {
      return Result::NOT_INIT;
    }

    if (mIsTransactionPending) {
        return Result::TRANSACTION_PENDING;
    }

    hub_message_t msg;

    if (setOsAppAsDestination(&msg, hubId) == false) {
        return Result::BAD_PARAMS;
    }

    struct apps_enable_request_t req;

    msg.message_type = CONTEXT_HUB_APPS_ENABLE;
    msg.message_len = sizeof(req);
    req.app_name.id = appId;
    msg.message = &req;

    if(mContextHubModule->send_message(hubId, &msg) != 0) {
        return Result::TRANSACTION_FAILED;
    } else {
        mTransactionId = transactionId;
        mIsTransactionPending = true;
        return Result::OK;
    }
}

Return<Result> Contexthub::disableNanoApp(uint32_t hubId,
                                          uint64_t appId,
                                          uint32_t transactionId) {
    if (!isInitialized()) {
      return Result::NOT_INIT;
    }

    if (mIsTransactionPending) {
        return Result::TRANSACTION_PENDING;
    }

    hub_message_t msg;

    if (setOsAppAsDestination(&msg, hubId) == false) {
        return Result::BAD_PARAMS;
    }

    struct apps_disable_request_t req;

    msg.message_type = CONTEXT_HUB_APPS_DISABLE;
    msg.message_len = sizeof(req);
    req.app_name.id = appId;
    msg.message = &req;

    if(mContextHubModule->send_message(hubId, &msg) != 0) {
        return Result::TRANSACTION_FAILED;
    } else {
        mTransactionId = transactionId;
        mIsTransactionPending = true;
        return Result::OK;
    }
}

Return<Result> Contexthub::queryApps(uint32_t hubId) {
    if (!isInitialized()) {
      return Result::NOT_INIT;
    }

    hub_message_t msg;

    if (setOsAppAsDestination(&msg, hubId) == false) {
        ALOGW("Could not find hubId %" PRIu32, hubId);
        return Result::BAD_PARAMS;
    }

    query_apps_request_t payload;
    payload.app_name.id = ALL_APPS; // TODO : Pass this in as a parameter
    msg.message = &payload;
    msg.message_len = sizeof(payload);
    msg.message_type = CONTEXT_HUB_QUERY_APPS;

    if(mContextHubModule->send_message(hubId, &msg) != 0) {
        ALOGW("Query Apps sendMessage failed");
        return Result::TRANSACTION_FAILED;
    }

    return Result::OK;
}

bool Contexthub::isInitialized() {
    return (mInitCheck == OK && mContextHubModule != nullptr);
}

IContexthub *HIDL_FETCH_IContexthub(const char * halName) {
    ALOGI("%s Called for %s", __FUNCTION__, halName);
    Contexthub *contexthub = new Contexthub;

    if (!contexthub->isInitialized()) {
        delete contexthub;
        contexthub = nullptr;
    }

    return contexthub;
}

}  // namespace implementation
}  // namespace V1_0
}  // namespace contexthub
}  // namespace hardware
}  // namespace android
