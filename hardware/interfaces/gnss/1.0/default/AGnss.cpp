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

#define LOG_TAG "GnssHAL_AGnssInterface"

#include "AGnss.h"

namespace android {
namespace hardware {
namespace gnss {
namespace V1_0 {
namespace implementation {

std::vector<std::unique_ptr<ThreadFuncArgs>> AGnss::sThreadFuncArgsList;
sp<IAGnssCallback> AGnss::sAGnssCbIface = nullptr;
bool AGnss::sInterfaceExists = false;

AGpsCallbacks AGnss::sAGnssCb = {
    .status_cb = statusCb,
    .create_thread_cb = createThreadCb
};

AGnss::AGnss(const AGpsInterface* aGpsIface) : mAGnssIface(aGpsIface) {
    /* Error out if an instance of the interface already exists. */
    LOG_ALWAYS_FATAL_IF(sInterfaceExists);
    sInterfaceExists = true;
}

AGnss::~AGnss() {
    sThreadFuncArgsList.clear();
    sInterfaceExists = false;
}

void AGnss::statusCb(AGpsStatus* status) {
    if (sAGnssCbIface == nullptr) {
        ALOGE("%s: AGNSS Callback Interface configured incorrectly", __func__);
        return;
    }

    if (status == nullptr) {
        ALOGE("AGNSS status is invalid");
        return;
    }

    /*
     * Logic based on AGnssStatus processing by GnssLocationProvider. Size of
     * AGpsStatus is checked for backward compatibility since some devices may
     * be sending out an older version of AGpsStatus that only supports IPv4.
     */
    size_t statusSize = status->size;
    if (status->size == sizeof(AGpsStatus)) {
        switch (status->addr.ss_family)
        {
            case AF_INET:
                {
                    /*
                     * ss_family indicates IPv4.
                     */
                    struct sockaddr_in* in = reinterpret_cast<struct sockaddr_in*>(&(status->addr));
                    IAGnssCallback::AGnssStatusIpV4 aGnssStatusIpV4 = {
                        .type = static_cast<IAGnssCallback::AGnssType>(status->type),
                        .status = static_cast<IAGnssCallback::AGnssStatusValue>(status->status),
                        .ipV4Addr = in->sin_addr.s_addr,
                    };

                    /*
                     * Callback to client with agnssStatusIpV4Cb.
                     */
                    auto ret = sAGnssCbIface->agnssStatusIpV4Cb(aGnssStatusIpV4);
                    if (!ret.isOk()) {
                        ALOGE("%s: Unable to invoke callback", __func__);
                    }
                    break;
                }
            case AF_INET6:
                {
                    /*
                     * ss_family indicates IPv6. Callback to client with agnssStatusIpV6Cb.
                     */
                    IAGnssCallback::AGnssStatusIpV6 aGnssStatusIpV6;

                    aGnssStatusIpV6.type = static_cast<IAGnssCallback::AGnssType>(status->type);
                    aGnssStatusIpV6.status = static_cast<IAGnssCallback::AGnssStatusValue>(
                            status->status);

                    struct sockaddr_in6* in6 = reinterpret_cast<struct sockaddr_in6 *>(
                            &(status->addr));
                    memcpy(&(aGnssStatusIpV6.ipV6Addr[0]), in6->sin6_addr.s6_addr,
                           aGnssStatusIpV6.ipV6Addr.size());
                    auto ret = sAGnssCbIface->agnssStatusIpV6Cb(aGnssStatusIpV6);
                    if (!ret.isOk()) {
                        ALOGE("%s: Unable to invoke callback", __func__);
                    }
                    break;
                }
             default:
                    ALOGE("Invalid ss_family found: %d", status->addr.ss_family);
        }
    } else if (statusSize >= sizeof(AGpsStatus_v2)) {
        AGpsStatus_v2* statusV2 = reinterpret_cast<AGpsStatus_v2*>(status);
        uint32_t ipV4Addr = statusV2->ipaddr;
        IAGnssCallback::AGnssStatusIpV4 aGnssStatusIpV4 = {
            .type = static_cast<IAGnssCallback::AGnssType>(AF_INET),
            .status = static_cast<IAGnssCallback::AGnssStatusValue>(status->status),
            /*
             * For older versions of AGpsStatus, change IP addr to net order. This
             * was earlier being done in GnssLocationProvider.
             */
            .ipV4Addr = htonl(ipV4Addr)
        };
        /*
         * Callback to client with agnssStatusIpV4Cb.
         */
        auto ret = sAGnssCbIface->agnssStatusIpV4Cb(aGnssStatusIpV4);
        if (!ret.isOk()) {
            ALOGE("%s: Unable to invoke callback", __func__);
        }
    } else {
        ALOGE("%s: Invalid size for AGPS Status", __func__);
    }
}

pthread_t AGnss::createThreadCb(const char* name, void (*start)(void*), void* arg) {
    return createPthread(name, start, arg, &sThreadFuncArgsList);
}

/*
 * Implementation of methods from ::android::hardware::gnss::V1_0::IAGnss follow.
 */
Return<void> AGnss::setCallback(const sp<IAGnssCallback>& callback) {
    if (mAGnssIface == nullptr) {
        ALOGE("%s: AGnss interface is unavailable", __func__);
        return Void();
    }

    sAGnssCbIface = callback;

    mAGnssIface->init(&sAGnssCb);
    return Void();
}

Return<bool> AGnss::dataConnClosed()  {
    if (mAGnssIface == nullptr) {
        ALOGE("%s: AGnss interface is unavailable", __func__);
        return false;
    }

    return (mAGnssIface->data_conn_closed() == 0);
}

Return<bool> AGnss::dataConnFailed()  {
    if (mAGnssIface == nullptr) {
        ALOGE("%s: AGnss interface is unavailable", __func__);
        return false;
    }

    return (mAGnssIface->data_conn_failed() == 0);
}

Return<bool> AGnss::setServer(IAGnssCallback::AGnssType type,
                              const hidl_string& hostname,
                              int32_t port) {
    if (mAGnssIface == nullptr) {
        ALOGE("%s: AGnss interface is unavailable", __func__);
        return false;
    }

    return (mAGnssIface->set_server(static_cast<AGpsType>(type), hostname.c_str(), port) == 0);
}

Return<bool> AGnss::dataConnOpen(const hidl_string& apn, IAGnss::ApnIpType apnIpType) {
    if (mAGnssIface == nullptr) {
        ALOGE("%s: AGnss interface is unavailable", __func__);
        return false;
    }

    return (mAGnssIface->data_conn_open_with_apn_ip_type(apn.c_str(),
                                                     static_cast<uint16_t>(apnIpType)) == 0);
}

}  // namespace implementation
}  // namespace V1_0
}  // namespace gnss
}  // namespace hardware
}  // namespace android
