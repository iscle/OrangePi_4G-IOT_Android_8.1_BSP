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

#define LOG_TAG "automotive.vehicle@2.0-impl"

#include "SubscriptionManager.h"

#include <cmath>
#include <inttypes.h>

#include <android/log.h>

#include "VehicleUtils.h"

namespace android {
namespace hardware {
namespace automotive {
namespace vehicle {
namespace V2_0 {

bool mergeSubscribeOptions(const SubscribeOptions &oldOpts,
                           const SubscribeOptions &newOpts,
                           SubscribeOptions *outResult) {

    int32_t updatedAreas = oldOpts.vehicleAreas;
    if (updatedAreas != kAllSupportedAreas) {
        updatedAreas = newOpts.vehicleAreas != kAllSupportedAreas
            ? updatedAreas | newOpts.vehicleAreas
            : kAllSupportedAreas;
    }

    float updatedRate = std::max(oldOpts.sampleRate, newOpts.sampleRate);
    SubscribeFlags updatedFlags = SubscribeFlags(oldOpts.flags | newOpts.flags);

    bool updated = updatedRate > oldOpts.sampleRate
                   || updatedAreas != oldOpts.vehicleAreas
                   || updatedFlags != oldOpts.flags;
    if (updated) {
        *outResult = oldOpts;
        outResult->vehicleAreas = updatedAreas;
        outResult->sampleRate = updatedRate;
        outResult->flags = updatedFlags;
    }

    return updated;
}

void HalClient::addOrUpdateSubscription(const SubscribeOptions &opts)  {
    ALOGI("%s opts.propId: 0x%x", __func__, opts.propId);

    auto it = mSubscriptions.find(opts.propId);
    if (it == mSubscriptions.end()) {
        mSubscriptions.emplace(opts.propId, opts);
    } else {
        const SubscribeOptions& oldOpts = it->second;
        SubscribeOptions updatedOptions;
        if (mergeSubscribeOptions(oldOpts, opts, &updatedOptions)) {
            mSubscriptions.erase(it);
            mSubscriptions.emplace(opts.propId, updatedOptions);
        }
    }
}

bool HalClient::isSubscribed(int32_t propId,
                             int32_t areaId,
                             SubscribeFlags flags) {
    auto it = mSubscriptions.find(propId);
    if (it == mSubscriptions.end()) {
        return false;
    }
    const SubscribeOptions& opts = it->second;
    bool res = (opts.flags & flags)
           && (opts.vehicleAreas == 0 || areaId == 0 || opts.vehicleAreas & areaId);
    return res;
}

std::vector<int32_t> HalClient::getSubscribedProperties() const {
    std::vector<int32_t> props;
    for (const auto& subscription : mSubscriptions) {
        ALOGI("%s propId: 0x%x, propId: 0x%x", __func__, subscription.first, subscription.second.propId);
        props.push_back(subscription.first);
    }
    return props;
}

StatusCode SubscriptionManager::addOrUpdateSubscription(
        ClientId clientId,
        const sp<IVehicleCallback> &callback,
        const hidl_vec<SubscribeOptions> &optionList,
        std::list<SubscribeOptions>* outUpdatedSubscriptions) {
    outUpdatedSubscriptions->clear();

    MuxGuard g(mLock);

    ALOGI("SubscriptionManager::addOrUpdateSubscription, callback: %p", callback.get());

    const sp<HalClient>& client = getOrCreateHalClientLocked(clientId, callback);
    if (client.get() == nullptr) {
        return StatusCode::INTERNAL_ERROR;
    }

    for (size_t i = 0; i < optionList.size(); i++) {
        const SubscribeOptions& opts = optionList[i];
        ALOGI("SubscriptionManager::addOrUpdateSubscription, prop: 0x%x", opts.propId);
        client->addOrUpdateSubscription(opts);

        addClientToPropMapLocked(opts.propId, client);

        if (SubscribeFlags::HAL_EVENT & opts.flags) {
            SubscribeOptions updated;
            if (updateHalEventSubscriptionLocked(opts, &updated)) {
                outUpdatedSubscriptions->push_back(updated);
            }
        }
    }

    return StatusCode::OK;
}

std::list<HalClientValues> SubscriptionManager::distributeValuesToClients(
        const std::vector<recyclable_ptr<VehiclePropValue>>& propValues,
        SubscribeFlags flags) const {
    std::map<sp<HalClient>, std::list<VehiclePropValue*>> clientValuesMap;

    {
        MuxGuard g(mLock);
        for (const auto& propValue: propValues) {
            VehiclePropValue* v = propValue.get();
            auto clients = getSubscribedClientsLocked(
                v->prop, v->areaId, flags);
            for (const auto& client : clients) {
                clientValuesMap[client].push_back(v);
            }
        }
    }

    std::list<HalClientValues> clientValues;
    for (const auto& entry : clientValuesMap) {
        clientValues.push_back(HalClientValues {
            .client = entry.first,
            .values = entry.second
        });
    }

    return clientValues;
}

std::list<sp<HalClient>> SubscriptionManager::getSubscribedClients(
    int32_t propId, int32_t area, SubscribeFlags flags) const {
    MuxGuard g(mLock);
    return getSubscribedClientsLocked(propId, area, flags);
}

std::list<sp<HalClient>> SubscriptionManager::getSubscribedClientsLocked(
        int32_t propId, int32_t area, SubscribeFlags flags) const {
    std::list<sp<HalClient>> subscribedClients;

    sp<HalClientVector> propClients = getClientsForPropertyLocked(propId);
    if (propClients.get() != nullptr) {
        for (size_t i = 0; i < propClients->size(); i++) {
            const auto& client = propClients->itemAt(i);
            if (client->isSubscribed(propId, area, flags)) {
                subscribedClients.push_back(client);
            }
        }
    }

    return subscribedClients;
}

bool SubscriptionManager::updateHalEventSubscriptionLocked(
        const SubscribeOptions &opts, SubscribeOptions *outUpdated) {
    bool updated = false;
    auto it = mHalEventSubscribeOptions.find(opts.propId);
    if (it == mHalEventSubscribeOptions.end()) {
        *outUpdated = opts;
        mHalEventSubscribeOptions.emplace(opts.propId, opts);
        updated = true;
    } else {
        const SubscribeOptions& oldOpts = it->second;

        if (mergeSubscribeOptions(oldOpts, opts, outUpdated)) {
            mHalEventSubscribeOptions.erase(opts.propId);
            mHalEventSubscribeOptions.emplace(opts.propId, *outUpdated);
            updated = true;
        }
    }

    return updated;
}

void SubscriptionManager::addClientToPropMapLocked(
        int32_t propId, const sp<HalClient> &client) {
    auto it = mPropToClients.find(propId);
    sp<HalClientVector> propClients;
    if (it == mPropToClients.end()) {
        propClients = new HalClientVector();
        mPropToClients.insert(std::make_pair(propId, propClients));
    } else {
        propClients = it->second;
    }
    propClients->addOrUpdate(client);
}

sp<HalClientVector> SubscriptionManager::getClientsForPropertyLocked(
        int32_t propId) const {
    auto it = mPropToClients.find(propId);
    return it == mPropToClients.end() ? nullptr : it->second;
}

sp<HalClient> SubscriptionManager::getOrCreateHalClientLocked(
        ClientId clientId, const sp<IVehicleCallback>& callback) {
    auto it = mClients.find(clientId);

    if (it == mClients.end()) {
        uint64_t cookie = reinterpret_cast<uint64_t>(clientId);
        ALOGI("Creating new client and linking to death recipient, cookie: 0x%" PRIx64, cookie);
        auto res = callback->linkToDeath(mCallbackDeathRecipient, cookie);
        if (!res.isOk()) {  // Client is already dead?
            ALOGW("%s failed to link to death, client %p, err: %s",
                  __func__, callback.get(), res.description().c_str());
            return nullptr;
        }

        sp<HalClient> client = new HalClient(callback);
        mClients.insert({clientId, client});
        return client;
    } else {
        return it->second;
    }
}

void SubscriptionManager::unsubscribe(ClientId clientId,
                                      int32_t propId) {
    MuxGuard g(mLock);
    auto propertyClients = getClientsForPropertyLocked(propId);
    auto clientIter = mClients.find(clientId);
    if (clientIter == mClients.end()) {
        ALOGW("Unable to unsubscribe: no callback found, propId: 0x%x", propId);
    } else {
        auto client = clientIter->second;

        if (propertyClients != nullptr) {
            propertyClients->remove(client);

            if (propertyClients->isEmpty()) {
                mPropToClients.erase(propId);
            }
        }

        bool isClientSubscribedToOtherProps = false;
        for (const auto& propClient : mPropToClients) {
            if (propClient.second->indexOf(client) >= 0) {
                isClientSubscribedToOtherProps = true;
                break;
            }
        }

        if (!isClientSubscribedToOtherProps) {
            auto res = client->getCallback()->unlinkToDeath(mCallbackDeathRecipient);
            if (!res.isOk()) {
                ALOGW("%s failed to unlink to death, client: %p, err: %s",
                      __func__, client->getCallback().get(), res.description().c_str());
            }
            mClients.erase(clientIter);
        }
    }

    if (propertyClients == nullptr || propertyClients->isEmpty()) {
        mHalEventSubscribeOptions.erase(propId);
        mOnPropertyUnsubscribed(propId);
    }
}

void SubscriptionManager::onCallbackDead(uint64_t cookie) {
    ALOGI("%s, cookie: 0x%" PRIx64, __func__, cookie);
    ClientId clientId = cookie;

    std::vector<int32_t> props;
    {
        MuxGuard g(mLock);
        const auto& it = mClients.find(clientId);
        if (it == mClients.end()) {
            return;  // Nothing to do here, client wasn't subscribed to any properties.
        }
        const auto& halClient = it->second;
        props = halClient->getSubscribedProperties();
    }

    for (int32_t propId : props) {
        unsubscribe(clientId, propId);
    }
}


}  // namespace V2_0
}  // namespace vehicle
}  // namespace automotive
}  // namespace hardware
}  // namespace android
