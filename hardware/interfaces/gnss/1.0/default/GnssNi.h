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

#ifndef android_hardware_gnss_V1_0_GnssNi_H_
#define android_hardware_gnss_V1_0_GnssNi_H_

#include <ThreadCreationWrapper.h>
#include <android/hardware/gnss/1.0/IGnssNi.h>
#include <hidl/Status.h>
#include <hardware/gps.h>

namespace android {
namespace hardware {
namespace gnss {
namespace V1_0 {
namespace implementation {

using ::android::hardware::gnss::V1_0::IGnssNi;
using ::android::hardware::gnss::V1_0::IGnssNiCallback;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::hidl_vec;
using ::android::hardware::hidl_string;
using ::android::sp;

/*
 * Extended interface for Network-initiated (NI) support. This interface is used to respond to
 * NI notifications originating from the HAL. Also contains wrapper methods to allow methods from
 * IGnssNiCallback interface to be passed into the conventional implementation of the GNSS HAL.
 */
struct GnssNi : public IGnssNi {
    GnssNi(const GpsNiInterface* gpsNiIface);
    ~GnssNi();

    /*
     * Methods from ::android::hardware::gnss::V1_0::IGnssNi follow.
     * These declarations were generated from IGnssNi.hal.
     */
    Return<void> setCallback(const sp<IGnssNiCallback>& callback) override;
    Return<void> respond(int32_t notifId,
                         IGnssNiCallback::GnssUserResponseType userResponse) override;

    /*
     * Callback methods to be passed into the conventional GNSS HAL by the default
     * implementation. These methods are not part of the IGnssNi base class.
     */
    static pthread_t createThreadCb(const char* name, void (*start)(void*), void* arg);
    static void niNotifyCb(GpsNiNotification* notification);

    /*
     * Holds function pointers to the callback methods.
     */
    static GpsNiCallbacks sGnssNiCb;

 private:
    const GpsNiInterface* mGnssNiIface = nullptr;
    static sp<IGnssNiCallback> sGnssNiCbIface;
    static std::vector<std::unique_ptr<ThreadFuncArgs>> sThreadFuncArgsList;
    static bool sInterfaceExists;
};

}  // namespace implementation
}  // namespace V1_0
}  // namespace gnss
}  // namespace hardware
}  // namespace android

#endif  // android_hardware_gnss_V1_0_GnssNi_H_
