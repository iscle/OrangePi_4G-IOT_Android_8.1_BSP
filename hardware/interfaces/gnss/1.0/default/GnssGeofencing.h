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

#ifndef android_hardware_gnss_V1_0_GnssGeofencing_H_
#define android_hardware_gnss_V1_0_GnssGeofencing_H_

#include <ThreadCreationWrapper.h>
#include <android/hardware/gnss/1.0/IGnssGeofencing.h>
#include <hidl/Status.h>
#include <hardware/gps.h>

namespace android {
namespace hardware {
namespace gnss {
namespace V1_0 {
namespace implementation {

using ::android::hardware::gnss::V1_0::IGnssGeofenceCallback;
using ::android::hardware::gnss::V1_0::IGnssGeofencing;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::hidl_vec;
using ::android::hardware::hidl_string;
using ::android::sp;

/*
 * Interface for GNSS Geofencing support. It also contains wrapper methods to allow
 * methods from IGnssGeofenceCallback interface to be passed into the
 * conventional implementation of the GNSS HAL.
 */
struct GnssGeofencing : public IGnssGeofencing {
    GnssGeofencing(const GpsGeofencingInterface* gpsGeofencingIface);
    ~GnssGeofencing();

    /*
     * Methods from ::android::hardware::gnss::V1_0::IGnssGeofencing follow.
     * These declarations were generated from IGnssGeofencing.hal.
     */
    Return<void> setCallback(const sp<IGnssGeofenceCallback>& callback)  override;
    Return<void> addGeofence(int32_t geofenceId,
                             double latitudeDegrees,
                             double longitudeDegrees,
                             double radiusMeters,
                             IGnssGeofenceCallback::GeofenceTransition lastTransition,
                             int32_t monitorTransitions,
                             uint32_t notificationResponsivenessMs,
                             uint32_t unknownTimerMs)  override;

    Return<void> pauseGeofence(int32_t geofenceId)  override;
    Return<void> resumeGeofence(int32_t geofenceId, int32_t monitorTransitions)  override;
    Return<void> removeGeofence(int32_t geofenceId)  override;

    /*
     * Callback methods to be passed into the conventional GNSS HAL by the default
     * implementation. These methods are not part of the IGnssGeofencing base class.
     */
    static void gnssGfTransitionCb(int32_t geofence_id, GpsLocation* location,
                                   int32_t transition, GpsUtcTime timestamp);
    static void gnssGfStatusCb(int32_t status, GpsLocation* last_location);
    static void gnssGfAddCb(int32_t geofence_id, int32_t status);
    static void gnssGfRemoveCb(int32_t geofence_id, int32_t status);
    static void gnssGfPauseCb(int32_t geofence_id, int32_t status);
    static void gnssGfResumeCb(int32_t geofence_id, int32_t status);
    static pthread_t createThreadCb(const char* name, void (*start)(void*), void* arg);

    /*
     * Holds function pointers to the callback methods.
     */
    static GpsGeofenceCallbacks sGnssGfCb;

 private:
    static std::vector<std::unique_ptr<ThreadFuncArgs>> sThreadFuncArgsList;
    static sp<IGnssGeofenceCallback> mGnssGeofencingCbIface;
    const GpsGeofencingInterface* mGnssGeofencingIface = nullptr;
    static bool sInterfaceExists;
};

}  // namespace implementation
}  // namespace V1_0
}  // namespace gnss
}  // namespace hardware
}  // namespace android

#endif  // android_hardware_gnss_V1_0_GnssGeofencing_H_
