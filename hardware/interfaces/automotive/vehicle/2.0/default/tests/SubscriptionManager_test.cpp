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

#include <functional>
#include <iostream>
#include <unordered_map>

#include <gtest/gtest.h>

#include "vhal_v2_0/SubscriptionManager.h"

#include "VehicleHalTestUtils.h"

namespace android {
namespace hardware {
namespace automotive {
namespace vehicle {
namespace V2_0 {

namespace {

using namespace std::placeholders;

class SubscriptionManagerTest : public ::testing::Test {
public:
    SubscriptionManagerTest() : manager(([this](int x) { onPropertyUnsubscribed(x); })) {}

    SubscriptionManager manager;
    static constexpr int32_t PROP1 = toInt(VehicleProperty::HVAC_FAN_SPEED);
    static constexpr int32_t PROP2 = toInt(VehicleProperty::DISPLAY_BRIGHTNESS);

    sp<IVehicleCallback> cb1 = new MockedVehicleCallback();
    sp<IVehicleCallback> cb2 = new MockedVehicleCallback();
    sp<IVehicleCallback> cb3 = new MockedVehicleCallback();

    void SetUp() override {
        lastUnsubscribedProperty = -1;
    }

    hidl_vec<SubscribeOptions> subscrToProp1 = {
        SubscribeOptions {
            .propId = PROP1,
            .vehicleAreas = toInt(VehicleAreaZone::ROW_1_LEFT),
            .flags = SubscribeFlags::HAL_EVENT
        },
    };

    hidl_vec<SubscribeOptions> subscrToProp2 = {
        SubscribeOptions {
            .propId = PROP2,
            .flags = SubscribeFlags::HAL_EVENT
        },
    };

    hidl_vec<SubscribeOptions> subscrToProp1and2 = {
        SubscribeOptions {
            .propId = PROP1,
            .vehicleAreas = toInt(VehicleAreaZone::ROW_1_LEFT),
            .flags = SubscribeFlags::HAL_EVENT
        },
        SubscribeOptions {
            .propId = PROP2,
            .flags = SubscribeFlags::HAL_EVENT
        },
    };

    static std::list<sp<IVehicleCallback>> extractCallbacks(
            const std::list<sp<HalClient>>& clients) {
        std::list<sp<IVehicleCallback>> callbacks;
        for (auto c : clients) {
            callbacks.push_back(c->getCallback());
        }
        return callbacks;
    }

    std::list<sp<HalClient>> clientsToProp1() {
        return manager.getSubscribedClients(PROP1,
                                            toInt(VehicleAreaZone::ROW_1_LEFT),
                                            SubscribeFlags::DEFAULT);
    }

    std::list<sp<HalClient>> clientsToProp2() {
        return manager.getSubscribedClients(PROP2, 0,
                                            SubscribeFlags::DEFAULT);
    }

    void onPropertyUnsubscribed(int propertyId) {
        // Called when there are no clients who subscribed to particular property. This can happen
        // because of explict unsubscribe call or when client (IVehicleCallback) was disconnected.
        lastUnsubscribedProperty = propertyId;
    }

    void assertOnPropertyUnsubscribedNotCalled() {
        ASSERT_EQ(-1, lastUnsubscribedProperty);
    }

    void assertLastUnsubscribedProperty(int expectedPropertyId) {
        ASSERT_EQ(expectedPropertyId, lastUnsubscribedProperty);
        lastUnsubscribedProperty = -1;
    }

private:
    int lastUnsubscribedProperty;
};


TEST_F(SubscriptionManagerTest, multipleClients) {
    std::list<SubscribeOptions> updatedOptions;
    ASSERT_EQ(StatusCode::OK,
              manager.addOrUpdateSubscription(1, cb1, subscrToProp1, &updatedOptions));
    ASSERT_EQ(StatusCode::OK,
              manager.addOrUpdateSubscription(2, cb2, subscrToProp1, &updatedOptions));

    auto clients = manager.getSubscribedClients(
            PROP1,
            toInt(VehicleAreaZone::ROW_1_LEFT),
            SubscribeFlags::HAL_EVENT);

    ASSERT_ALL_EXISTS({cb1, cb2}, extractCallbacks(clients));
}

TEST_F(SubscriptionManagerTest, negativeCases) {
    std::list<SubscribeOptions> updatedOptions;
    ASSERT_EQ(StatusCode::OK,
              manager.addOrUpdateSubscription(1, cb1, subscrToProp1, &updatedOptions));

    // Wrong zone
    auto clients = manager.getSubscribedClients(
            PROP1,
            toInt(VehicleAreaZone::ROW_2_LEFT),
            SubscribeFlags::HAL_EVENT);
    ASSERT_TRUE(clients.empty());

    // Wrong prop
    clients = manager.getSubscribedClients(
            toInt(VehicleProperty::AP_POWER_BOOTUP_REASON),
            toInt(VehicleAreaZone::ROW_1_LEFT),
            SubscribeFlags::HAL_EVENT);
    ASSERT_TRUE(clients.empty());

    // Wrong flag
    clients = manager.getSubscribedClients(
            PROP1,
            toInt(VehicleAreaZone::ROW_1_LEFT),
            SubscribeFlags::SET_CALL);
    ASSERT_TRUE(clients.empty());
}

TEST_F(SubscriptionManagerTest, mulipleSubscriptions) {
    std::list<SubscribeOptions> updatedOptions;
    ASSERT_EQ(StatusCode::OK, manager.addOrUpdateSubscription(1, cb1, subscrToProp1,
                                                              &updatedOptions));

    auto clients = manager.getSubscribedClients(
            PROP1,
            toInt(VehicleAreaZone::ROW_1_LEFT),
            SubscribeFlags::DEFAULT);
    ASSERT_EQ((size_t) 1, clients.size());
    ASSERT_EQ(cb1, clients.front()->getCallback());

    // Same property, but different zone, to make sure we didn't unsubscribe
    // from previous zone.
    ASSERT_EQ(StatusCode::OK, manager.addOrUpdateSubscription(1, cb1, {
        SubscribeOptions {
                .propId = PROP1,
                .vehicleAreas = toInt(VehicleAreaZone::ROW_2),
                .flags = SubscribeFlags::DEFAULT
            }
        }, &updatedOptions));

    clients = manager.getSubscribedClients(PROP1,
                                           toInt(VehicleAreaZone::ROW_1_LEFT),
                                           SubscribeFlags::DEFAULT);
    ASSERT_ALL_EXISTS({cb1}, extractCallbacks(clients));

    clients = manager.getSubscribedClients(PROP1,
                                           toInt(VehicleAreaZone::ROW_2),
                                           SubscribeFlags::DEFAULT);
    ASSERT_ALL_EXISTS({cb1}, extractCallbacks(clients));
}

TEST_F(SubscriptionManagerTest, unsubscribe) {
    std::list<SubscribeOptions> updatedOptions;
    ASSERT_EQ(StatusCode::OK,
              manager.addOrUpdateSubscription(1, cb1, subscrToProp1, &updatedOptions));
    ASSERT_EQ(StatusCode::OK,
              manager.addOrUpdateSubscription(2, cb2, subscrToProp2, &updatedOptions));
    ASSERT_EQ(StatusCode::OK,
              manager.addOrUpdateSubscription(3, cb3, subscrToProp1and2, &updatedOptions));

    ASSERT_ALL_EXISTS({ cb1, cb3 }, extractCallbacks(clientsToProp1()));
    ASSERT_ALL_EXISTS({cb2, cb3}, extractCallbacks(clientsToProp2()));

    manager.unsubscribe(1, PROP1);
    assertOnPropertyUnsubscribedNotCalled();
    ASSERT_ALL_EXISTS({cb3}, extractCallbacks(clientsToProp1()));

    // Make sure nothing changed in PROP2 so far.
    ASSERT_ALL_EXISTS({cb2, cb3}, extractCallbacks(clientsToProp2()));

    // No one subscribed to PROP1, subscription for PROP2 is not affected.
    manager.unsubscribe(3, PROP1);
    assertLastUnsubscribedProperty(PROP1);
    ASSERT_ALL_EXISTS({cb2, cb3}, extractCallbacks(clientsToProp2()));

    manager.unsubscribe(3, PROP2);
    assertOnPropertyUnsubscribedNotCalled();
    ASSERT_ALL_EXISTS({cb2}, extractCallbacks(clientsToProp2()));

    // The last client unsubscribed from this property.
    manager.unsubscribe(2, PROP2);
    assertLastUnsubscribedProperty(PROP2);

    // No one subscribed anymore
    manager.unsubscribe(1, PROP1);
    assertLastUnsubscribedProperty(PROP1);
}

}  // namespace anonymous

}  // namespace V2_0
}  // namespace vehicle
}  // namespace automotive
}  // namespace hardware
}  // namespace android
