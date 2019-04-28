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

#ifndef android_hardware_gnss_V1_0_GnssXtra_H_
#define android_hardware_gnss_V1_0_GnssXtra_H_

#include <ThreadCreationWrapper.h>
#include <android/hardware/gnss/1.0/IGnssXtra.h>
#include <hardware/gps.h>
#include <hidl/Status.h>

namespace android {
namespace hardware {
namespace gnss {
namespace V1_0 {
namespace implementation {

using ::android::hardware::gnss::V1_0::IGnssXtra;
using ::android::hardware::gnss::V1_0::IGnssXtraCallback;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::hidl_vec;
using ::android::hardware::hidl_string;
using ::android::sp;

/*
 * This interface is used by the GNSS HAL to request the framework to download XTRA data.
 * Also contains wrapper methods to allow methods from IGnssXtraCallback interface to be passed
 * into the conventional implementation of the GNSS HAL.
 */
struct GnssXtra : public IGnssXtra {
    GnssXtra(const GpsXtraInterface* xtraIface);
    ~GnssXtra();

    /*
     * Methods from ::android::hardware::gnss::V1_0::IGnssXtra follow.
     * These declarations were generated from IGnssXtra.hal.
     */
    Return<bool> setCallback(const sp<IGnssXtraCallback>& callback) override;
    Return<bool> injectXtraData(const hidl_string& xtraData) override;

    /*
     * Callback methods to be passed into the conventional GNSS HAL by the default implementation.
     * These methods are not part of the IGnssXtra base class.
     */
    static pthread_t createThreadCb(const char* name, void (*start)(void*), void* arg);
    static void gnssXtraDownloadRequestCb();

    /*
     * Holds function pointers to the callback methods.
     */
    static GpsXtraCallbacks sGnssXtraCb;

 private:
    const GpsXtraInterface* mGnssXtraIface = nullptr;
    static sp<IGnssXtraCallback> sGnssXtraCbIface;
    static std::vector<std::unique_ptr<ThreadFuncArgs>> sThreadFuncArgsList;
    static bool sInterfaceExists;
};

}  // namespace implementation
}  // namespace V1_0
}  // namespace gnss
}  // namespace hardware
}  // namespace android

#endif  // android_hardware_gnss_V1_0_GnssXtra_H_
