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

#ifndef CAR_EVS_APP_EVSSTATECONTROL_H
#define CAR_EVS_APP_EVSSTATECONTROL_H

#include "StreamHandler.h"
#include "ConfigManager.h"
#include "RenderBase.h"

#include <android/hardware/automotive/vehicle/2.0/IVehicle.h>
#include <android/hardware/automotive/evs/1.0/IEvsEnumerator.h>
#include <android/hardware/automotive/evs/1.0/IEvsDisplay.h>
#include <android/hardware/automotive/evs/1.0/IEvsCamera.h>

#include <thread>


using namespace ::android::hardware::automotive::evs::V1_0;
using namespace ::android::hardware::automotive::vehicle::V2_0;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::hidl_vec;
using ::android::hardware::hidl_handle;
using ::android::sp;


/*
 * This class runs the main update loop for the EVS application.  It will sleep when it has
 * nothing to do.  It provides a thread safe way for other threads to wake it and pass commands
 * to it.
 */
class EvsStateControl {
public:
    EvsStateControl(android::sp <IVehicle>       pVnet,
                    android::sp <IEvsEnumerator> pEvs,
                    android::sp <IEvsDisplay>    pDisplay,
                    const ConfigManager&         config);

    enum State {
        OFF = 0,
        REVERSE,
        LEFT,
        RIGHT,
        PARKING,
        NUM_STATES  // Must come last
    };

    enum class Op {
        EXIT,
        CHECK_VEHICLE_STATE,
        TOUCH_EVENT,
    };

    struct Command {
        Op          operation;
        uint32_t    arg1;
        uint32_t    arg2;
    };

    // This spawns a new thread that is expected to run continuously
    bool startUpdateLoop();

    // Safe to be called from other threads
    void postCommand(const Command& cmd);

private:
    void updateLoop();
    StatusCode invokeGet(VehiclePropValue *pRequestedPropValue);
    bool selectStateForCurrentConditions();
    bool configureEvsPipeline(State desiredState);  // Only call from one thread!

    sp<IVehicle>                mVehicle;
    sp<IEvsEnumerator>          mEvs;
    sp<IEvsDisplay>             mDisplay;
    const ConfigManager&        mConfig;

    VehiclePropValue            mGearValue;
    VehiclePropValue            mTurnSignalValue;

    State                       mCurrentState = OFF;

    std::vector<ConfigManager::CameraInfo>  mCameraList[NUM_STATES];
    std::unique_ptr<RenderBase> mCurrentRenderer;

    std::thread                 mRenderThread;  // The thread that runs the main rendering loop

    // Other threads may want to spur us into action, so we provide a thread safe way to do that
    std::mutex                  mLock;
    std::condition_variable     mWakeSignal;
    std::queue<Command>         mCommandQueue;
};


#endif //CAR_EVS_APP_EVSSTATECONTROL_H
