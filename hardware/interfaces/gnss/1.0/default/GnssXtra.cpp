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

#define LOG_TAG "GnssHAL_GnssXtraInterface"

#include "GnssXtra.h"

namespace android {
namespace hardware {
namespace gnss {
namespace V1_0 {
namespace implementation {

std::vector<std::unique_ptr<ThreadFuncArgs>> GnssXtra::sThreadFuncArgsList;
sp<IGnssXtraCallback> GnssXtra::sGnssXtraCbIface = nullptr;
bool GnssXtra::sInterfaceExists = false;

GpsXtraCallbacks GnssXtra::sGnssXtraCb = {
    .download_request_cb = gnssXtraDownloadRequestCb,
    .create_thread_cb = createThreadCb,
};

GnssXtra::~GnssXtra() {
    sThreadFuncArgsList.clear();
    sInterfaceExists = false;
}

pthread_t GnssXtra::createThreadCb(const char* name, void (*start)(void*), void* arg) {
    return createPthread(name, start, arg, &sThreadFuncArgsList);
}

GnssXtra::GnssXtra(const GpsXtraInterface* xtraIface) : mGnssXtraIface(xtraIface) {
    /* Error out if an instance of the interface already exists. */
    LOG_ALWAYS_FATAL_IF(sInterfaceExists);
    sInterfaceExists = true;
}

void GnssXtra::gnssXtraDownloadRequestCb() {
    if (sGnssXtraCbIface == nullptr) {
        ALOGE("%s: GNSS Callback Interface configured incorrectly", __func__);
        return;
    }

    auto ret = sGnssXtraCbIface->downloadRequestCb();
    if (!ret.isOk()) {
        ALOGE("%s: Unable to invoke callback", __func__);
    }
}

// Methods from ::android::hardware::gnss::V1_0::IGnssXtra follow.
Return<bool> GnssXtra::setCallback(const sp<IGnssXtraCallback>& callback)  {
    if (mGnssXtraIface == nullptr) {
        ALOGE("%s: Gnss Xtra interface is unavailable", __func__);
        return false;
    }

    sGnssXtraCbIface = callback;

    return (mGnssXtraIface->init(&sGnssXtraCb) == 0);
}

Return<bool> GnssXtra::injectXtraData(const hidl_string& xtraData)  {
    if (mGnssXtraIface == nullptr) {
        ALOGE("%s: Gnss Xtra interface is unavailable", __func__);
        return false;
    }

    char* buf = new char[xtraData.size()];
    const char* data = xtraData.c_str();

    memcpy(buf, data, xtraData.size());

    int ret = mGnssXtraIface->inject_xtra_data(buf, xtraData.size());
    delete[] buf;
    return (ret == 0);
}

}  // namespace implementation
}  // namespace V1_0
}  // namespace gnss
}  // namespace hardware
}  // namespace android
