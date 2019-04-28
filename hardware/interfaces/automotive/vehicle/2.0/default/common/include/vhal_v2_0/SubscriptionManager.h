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

#ifndef android_hardware_automotive_vehicle_V2_0_SubscriptionManager_H_
#define android_hardware_automotive_vehicle_V2_0_SubscriptionManager_H_

#include <memory>
#include <map>
#include <set>
#include <list>

#include <android/log.h>
#include <hidl/HidlSupport.h>
#include <utils/SortedVector.h>

#include <android/hardware/automotive/vehicle/2.0/IVehicle.h>

#include "ConcurrentQueue.h"
#include "VehicleObjectPool.h"

namespace android {
namespace hardware {
namespace automotive {
namespace vehicle {
namespace V2_0 {

class HalClient : public android::RefBase {
public:
    HalClient(const sp<IVehicleCallback> &callback)
        : mCallback(callback) {}

    virtual ~HalClient() {}
public:
    sp<IVehicleCallback> getCallback() const {
        return mCallback;
    }

    void addOrUpdateSubscription(const SubscribeOptions &opts);
    bool isSubscribed(int32_t propId, int32_t areaId, SubscribeFlags flags);
    std::vector<int32_t> getSubscribedProperties() const;

private:
    const sp<IVehicleCallback> mCallback;

    std::map<int32_t, SubscribeOptions> mSubscriptions;
};

class HalClientVector : private SortedVector<sp<HalClient>> , public RefBase {
public:
    virtual ~HalClientVector() {}

    inline void addOrUpdate(const sp<HalClient> &client) {
        SortedVector::add(client);
    }

    using SortedVector::remove;
    using SortedVector::size;
    using SortedVector::indexOf;
    using SortedVector::itemAt;
    using SortedVector::isEmpty;
};

struct HalClientValues {
    sp<HalClient> client;
    std::list<VehiclePropValue *> values;
};

using ClientId = uint64_t;

class SubscriptionManager {
public:
    using OnPropertyUnsubscribed = std::function<void(int32_t)>;

    /**
     * Constructs SubscriptionManager
     *
     * @param onPropertyUnsubscribed - this callback function will be called when there are no
     *                                    more client subscribed to particular property.
     */
    SubscriptionManager(const OnPropertyUnsubscribed& onPropertyUnsubscribed)
            : mOnPropertyUnsubscribed(onPropertyUnsubscribed),
                mCallbackDeathRecipient(new DeathRecipient(
                    std::bind(&SubscriptionManager::onCallbackDead, this, std::placeholders::_1)))
    {}

    ~SubscriptionManager() = default;

    /**
     * Updates subscription. Returns the vector of properties subscription that
     * needs to be updated in VehicleHAL.
     */
    StatusCode addOrUpdateSubscription(ClientId clientId,
                                       const sp<IVehicleCallback>& callback,
                                       const hidl_vec<SubscribeOptions>& optionList,
                                       std::list<SubscribeOptions>* outUpdatedOptions);

    /**
     * Returns a list of IVehicleCallback -> list of VehiclePropValue ready for
     * dispatching to its clients.
     */
    std::list<HalClientValues> distributeValuesToClients(
            const std::vector<recyclable_ptr<VehiclePropValue>>& propValues,
            SubscribeFlags flags) const;

    std::list<sp<HalClient>> getSubscribedClients(int32_t propId,
                                                  int32_t area,
                                                  SubscribeFlags flags) const;
    /**
     * If there are no clients subscribed to given properties than callback function provided
     * in the constructor will be called.
     */
    void unsubscribe(ClientId clientId, int32_t propId);
private:
    std::list<sp<HalClient>> getSubscribedClientsLocked(int32_t propId,
                                                        int32_t area,
                                                        SubscribeFlags flags) const;

    bool updateHalEventSubscriptionLocked(const SubscribeOptions &opts, SubscribeOptions* out);

    void addClientToPropMapLocked(int32_t propId, const sp<HalClient>& client);

    sp<HalClientVector> getClientsForPropertyLocked(int32_t propId) const;

    sp<HalClient> getOrCreateHalClientLocked(ClientId callingPid,
                                             const sp<IVehicleCallback>& callback);

    void onCallbackDead(uint64_t cookie);

private:
    using OnClientDead = std::function<void(uint64_t)>;

    class DeathRecipient : public hidl_death_recipient {
    public:
        DeathRecipient(const OnClientDead& onClientDead)
            : mOnClientDead(onClientDead) {}
        ~DeathRecipient() = default;

        DeathRecipient(const DeathRecipient& ) = delete;
        DeathRecipient& operator=(const DeathRecipient&) = delete;

        void serviceDied(uint64_t cookie,
                         const wp<::android::hidl::base::V1_0::IBase>& /* who */) override {
            mOnClientDead(cookie);
        }
    private:
        OnClientDead mOnClientDead;
    };

private:
    using MuxGuard = std::lock_guard<std::mutex>;

    mutable std::mutex mLock;

    std::map<ClientId, sp<HalClient>> mClients;
    std::map<int32_t, sp<HalClientVector>> mPropToClients;
    std::map<int32_t, SubscribeOptions> mHalEventSubscribeOptions;

    OnPropertyUnsubscribed mOnPropertyUnsubscribed;
    sp<DeathRecipient> mCallbackDeathRecipient;
};


}  // namespace V2_0
}  // namespace vehicle
}  // namespace automotive
}  // namespace hardware
}  // namespace android


#endif // android_hardware_automotive_vehicle_V2_0_SubscriptionManager_H_
