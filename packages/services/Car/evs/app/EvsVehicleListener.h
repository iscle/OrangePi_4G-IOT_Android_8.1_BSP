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

#ifndef CAR_EVS_APP_VEHICLELISTENER_H
#define CAR_EVS_APP_VEHICLELISTENER_H

#include "EvsStateControl.h"

/*
 * This class listens for asynchronous updates from the Vehicle HAL.  While the EVS
 * applications is active, it can poll the vehicle state directly.  However, when it goes to
 * sleep, we need these notifications to bring it active again.
 */
class EvsVehicleListener : public IVehicleCallback {
public:
    // Methods from ::android::hardware::automotive::vehicle::V2_0::IVehicleCallback follow.
    Return<void> onPropertyEvent(const hidl_vec <VehiclePropValue> & /*values*/) override {
        {
            // Our use case is so simple, we don't actually need to update a variable,
            // but the docs seem to say we have to take the lock anyway to keep
            // the condition variable implementation happy.
            std::lock_guard<std::mutex> g(mLock);
        }
        mEventCond.notify_one();
        return Return<void>();
    }

    Return<void> onPropertySet(const VehiclePropValue & /*value*/) override {
        // Ignore the direct set calls (we don't expect to make any anyway)
        return Return<void>();
    }

    Return<void> onPropertySetError(StatusCode      /* errorCode */,
                                    int32_t         /* propId */,
                                    int32_t         /* areaId */) override {
        // We don't set values, so we don't listen for set errors
        return Return<void>();
    }

    bool waitForEvents(int timeout_ms) {
        std::unique_lock<std::mutex> g(mLock);
        std::cv_status result = mEventCond.wait_for(g, std::chrono::milliseconds(timeout_ms));
        return (result == std::cv_status::no_timeout);
    }

    void run(EvsStateControl *pStateController) {
        while (true) {
            // Wait until we have an event to which to react
            // (wake up and validate our current state "just in case" every so often)
            waitForEvents(5000);

            // If we were delivered an event (or it's been a while) update as necessary
            EvsStateControl::Command cmd = {
                .operation = EvsStateControl::Op::CHECK_VEHICLE_STATE,
                .arg1      = 0,
                .arg2      = 0,
            };
            pStateController->postCommand(cmd);
        }
    }

private:
    std::mutex mLock;
    std::condition_variable mEventCond;
};

#endif //CAR_EVS_APP_VEHICLELISTENER_H
