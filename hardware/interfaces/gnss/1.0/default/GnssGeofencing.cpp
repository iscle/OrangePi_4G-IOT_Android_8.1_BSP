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

#define LOG_TAG "GnssHal_GnssGeofencing"

#include "GnssGeofencing.h"
#include <GnssUtils.h>

namespace android {
namespace hardware {
namespace gnss {
namespace V1_0 {
namespace implementation {

std::vector<std::unique_ptr<ThreadFuncArgs>> GnssGeofencing::sThreadFuncArgsList;
sp<IGnssGeofenceCallback> GnssGeofencing::mGnssGeofencingCbIface = nullptr;
bool GnssGeofencing::sInterfaceExists = false;

GpsGeofenceCallbacks GnssGeofencing::sGnssGfCb = {
    .geofence_transition_callback = gnssGfTransitionCb,
    .geofence_status_callback = gnssGfStatusCb,
    .geofence_add_callback = gnssGfAddCb,
    .geofence_remove_callback = gnssGfRemoveCb,
    .geofence_pause_callback = gnssGfPauseCb,
    .geofence_resume_callback = gnssGfResumeCb,
    .create_thread_cb = createThreadCb
};

GnssGeofencing::GnssGeofencing(const GpsGeofencingInterface* gpsGeofencingIface)
    : mGnssGeofencingIface(gpsGeofencingIface) {
    /* Error out if an instance of the interface already exists. */
    LOG_ALWAYS_FATAL_IF(sInterfaceExists);
    sInterfaceExists = true;
}

GnssGeofencing::~GnssGeofencing() {
    sThreadFuncArgsList.clear();
    sInterfaceExists = false;
}
void GnssGeofencing::gnssGfTransitionCb(int32_t geofenceId,
                                        GpsLocation* location,
                                        int32_t transition,
                                        GpsUtcTime timestamp) {
    if (mGnssGeofencingCbIface == nullptr) {
        ALOGE("%s: GNSS Geofence Callback Interface configured incorrectly", __func__);
        return;
    }

    if (location == nullptr) {
        ALOGE("%s : Invalid location from GNSS HAL", __func__);
        return;
    }

    GnssLocation gnssLocation = convertToGnssLocation(location);
    auto ret = mGnssGeofencingCbIface->gnssGeofenceTransitionCb(
            geofenceId,
            gnssLocation,
            static_cast<IGnssGeofenceCallback::GeofenceTransition>(transition),
            timestamp);
    if (!ret.isOk()) {
        ALOGE("%s: Unable to invoke callback", __func__);
    }
}

void GnssGeofencing::gnssGfStatusCb(int32_t status, GpsLocation* location) {
    if (mGnssGeofencingCbIface == nullptr) {
        ALOGE("%s: GNSS Geofence Callback Interface configured incorrectly", __func__);
        return;
    }

    GnssLocation gnssLocation;

    if (location != nullptr) {
        gnssLocation = convertToGnssLocation(location);
    } else {
        gnssLocation = {};
    }

    auto ret = mGnssGeofencingCbIface->gnssGeofenceStatusCb(
            static_cast<IGnssGeofenceCallback::GeofenceAvailability>(status), gnssLocation);
    if (!ret.isOk()) {
        ALOGE("%s: Unable to invoke callback", __func__);
    }
}

void GnssGeofencing::gnssGfAddCb(int32_t geofenceId, int32_t status) {
    if (mGnssGeofencingCbIface == nullptr) {
        ALOGE("%s: GNSS Geofence Callback Interface configured incorrectly", __func__);
        return;
    }

    auto ret = mGnssGeofencingCbIface->gnssGeofenceAddCb(
            geofenceId, static_cast<IGnssGeofenceCallback::GeofenceStatus>(status));
    if (!ret.isOk()) {
        ALOGE("%s: Unable to invoke callback", __func__);
    }
}

void GnssGeofencing::gnssGfRemoveCb(int32_t geofenceId, int32_t status) {
    if (mGnssGeofencingCbIface == nullptr) {
        ALOGE("%s: GNSS Geofence Callback Interface configured incorrectly", __func__);
        return;
    }

    auto ret = mGnssGeofencingCbIface->gnssGeofenceRemoveCb(
            geofenceId, static_cast<IGnssGeofenceCallback::GeofenceStatus>(status));
    if (!ret.isOk()) {
        ALOGE("%s: Unable to invoke callback", __func__);
    }
}

void GnssGeofencing::gnssGfPauseCb(int32_t geofenceId, int32_t status) {
    if (mGnssGeofencingCbIface == nullptr) {
        ALOGE("%s: GNSS Geofence Callback Interface configured incorrectly", __func__);
        return;
    }

    auto ret = mGnssGeofencingCbIface->gnssGeofencePauseCb(
            geofenceId, static_cast<IGnssGeofenceCallback::GeofenceStatus>(status));
    if (!ret.isOk()) {
        ALOGE("%s: Unable to invoke callback", __func__);
    }
}

void GnssGeofencing::gnssGfResumeCb(int32_t geofenceId, int32_t status) {
    if (mGnssGeofencingCbIface == nullptr) {
        ALOGE("%s: GNSS Geofence Callback Interface configured incorrectly", __func__);
        return;
    }

    auto ret = mGnssGeofencingCbIface->gnssGeofenceResumeCb(
            geofenceId, static_cast<IGnssGeofenceCallback::GeofenceStatus>(status));
    if (!ret.isOk()) {
        ALOGE("%s: Unable to invoke callback", __func__);
    }
}

pthread_t GnssGeofencing::createThreadCb(const char* name, void (*start)(void*), void* arg) {
    return createPthread(name, start, arg, &sThreadFuncArgsList);
}

// Methods from ::android::hardware::gnss::V1_0::IGnssGeofencing follow.
Return<void> GnssGeofencing::setCallback(const sp<IGnssGeofenceCallback>& callback)  {
    mGnssGeofencingCbIface = callback;

    if (mGnssGeofencingIface == nullptr) {
        ALOGE("%s: GnssGeofencing interface is not available", __func__);
    } else {
        mGnssGeofencingIface->init(&sGnssGfCb);
    }

    return Void();
}

Return<void> GnssGeofencing::addGeofence(
        int32_t geofenceId,
        double latitudeDegrees,
        double longitudeDegrees,
        double radiusMeters,
        IGnssGeofenceCallback::GeofenceTransition lastTransition,
        int32_t monitorTransitions,
        uint32_t notificationResponsivenessMs,
        uint32_t unknownTimerMs)  {
    if (mGnssGeofencingIface == nullptr) {
        ALOGE("%s: GnssGeofencing interface is not available", __func__);
        return Void();
    } else {
        mGnssGeofencingIface->add_geofence_area(
                geofenceId,
                latitudeDegrees,
                longitudeDegrees,
                radiusMeters,
                static_cast<int32_t>(lastTransition),
                monitorTransitions,
                notificationResponsivenessMs,
                unknownTimerMs);
    }
    return Void();
}

Return<void> GnssGeofencing::pauseGeofence(int32_t geofenceId)  {
    if (mGnssGeofencingIface == nullptr) {
        ALOGE("%s: GnssGeofencing interface is not available", __func__);
    } else {
        mGnssGeofencingIface->pause_geofence(geofenceId);
    }
    return Void();
}

Return<void> GnssGeofencing::resumeGeofence(int32_t geofenceId, int32_t monitorTransitions)  {
    if (mGnssGeofencingIface == nullptr) {
        ALOGE("%s: GnssGeofencing interface is not available", __func__);
    } else {
        mGnssGeofencingIface->resume_geofence(geofenceId, monitorTransitions);
    }
    return Void();
}

Return<void> GnssGeofencing::removeGeofence(int32_t geofenceId)  {
    if (mGnssGeofencingIface == nullptr) {
        ALOGE("%s: GnssGeofencing interface is not available", __func__);
    } else {
        mGnssGeofencingIface->remove_geofence_area(geofenceId);
    }
    return Void();
}

}  // namespace implementation
}  // namespace V1_0
}  // namespace gnss
}  // namespace hardware
}  // namespace android
