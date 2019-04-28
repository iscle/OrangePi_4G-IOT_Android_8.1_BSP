/*
 * Copyright (C) 2015 The Android Open Source Project
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

#ifndef android_hardware_automotive_vehicle_V2_0_VehicleHalManager_H_
#define android_hardware_automotive_vehicle_V2_0_VehicleHalManager_H_

#include <inttypes.h>
#include <stdint.h>
#include <sys/types.h>

#include <list>
#include <map>
#include <memory>
#include <set>

#include <android/hardware/automotive/vehicle/2.0/IVehicle.h>

#include "ConcurrentQueue.h"
#include "SubscriptionManager.h"
#include "VehicleHal.h"
#include "VehicleObjectPool.h"
#include "VehiclePropConfigIndex.h"

namespace android {
namespace hardware {
namespace automotive {
namespace vehicle {
namespace V2_0 {

/**
 * This class is a thick proxy between IVehicle HIDL interface and vendor's implementation.
 *
 * It has some boilerplate code like batching and caching property values, checking permissions,
 * etc. Vendors must implement VehicleHal class.
 */
class VehicleHalManager : public IVehicle {
public:
    VehicleHalManager(VehicleHal* vehicleHal)
        : mHal(vehicleHal),
          mSubscriptionManager(std::bind(&VehicleHalManager::onAllClientsUnsubscribed,
                                         this, std::placeholders::_1)) {
        init();
    }

    virtual ~VehicleHalManager();

    void init();

    // ---------------------------------------------------------------------------------------------
    // Methods derived from IVehicle
    Return<void> getAllPropConfigs(getAllPropConfigs_cb _hidl_cb)  override;
    Return<void> getPropConfigs(const hidl_vec<int32_t>& properties,
                                getPropConfigs_cb _hidl_cb)  override;
    Return<void> get(const VehiclePropValue& requestedPropValue,
                     get_cb _hidl_cb)  override;
    Return<StatusCode> set(const VehiclePropValue& value)  override;
    Return<StatusCode> subscribe(const sp<IVehicleCallback>& callback,
                                const hidl_vec<SubscribeOptions>& options)  override;
    Return<StatusCode> unsubscribe(const sp<IVehicleCallback>& callback,
                                   int32_t propId)  override;
    Return<void> debugDump(debugDump_cb _hidl_cb = nullptr) override;

private:
    using VehiclePropValuePtr = VehicleHal::VehiclePropValuePtr;
    // Returns true if needs to call again shortly.
    using RetriableAction = std::function<bool()>;

    // ---------------------------------------------------------------------------------------------
    // Events received from VehicleHal
    void onHalEvent(VehiclePropValuePtr  v);
    void onHalPropertySetError(StatusCode errorCode, int32_t property,
                               int32_t areaId);

    // ---------------------------------------------------------------------------------------------
    // This method will be called from BatchingConsumer thread
    void onBatchHalEvent(const std::vector<VehiclePropValuePtr >& values);

    void handlePropertySetEvent(const VehiclePropValue& value);

    const VehiclePropConfig* getPropConfigOrNull(int32_t prop) const;

    bool checkWritePermission(const VehiclePropConfig &config) const;
    bool checkReadPermission(const VehiclePropConfig &config) const;
    void onAllClientsUnsubscribed(int32_t propertyId);

    static bool isSubscribable(const VehiclePropConfig& config,
                               SubscribeFlags flags);
    static bool isSampleRateFixed(VehiclePropertyChangeMode mode);
    static float checkSampleRate(const VehiclePropConfig& config,
                                 float sampleRate);
    static ClientId getClientId(const sp<IVehicleCallback>& callback);
private:
    VehicleHal* mHal;
    std::unique_ptr<VehiclePropConfigIndex> mConfigIndex;
    SubscriptionManager mSubscriptionManager;

    hidl_vec<VehiclePropValue> mHidlVecOfVehiclePropValuePool;

    ConcurrentQueue<VehiclePropValuePtr> mEventQueue;
    BatchingConsumer<VehiclePropValuePtr> mBatchingConsumer;
    VehiclePropValuePool mValueObjectPool;
};

}  // namespace V2_0
}  // namespace vehicle
}  // namespace automotive
}  // namespace hardware
}  // namespace android


#endif // android_hardware_automotive_vehicle_V2_0_VehicleHalManager_H_
