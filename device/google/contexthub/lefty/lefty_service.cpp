/*
 * Copyright (C) 2017 The Android Open Source Project
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
#include "Lefty.h"
#include "lefty_service.h"

#include <android-base/logging.h>
#include <hidl/LegacySupport.h>
#include <utils/StrongPointer.h>
#include <vendor/google_clockwork/lefty/1.0/ILefty.h>

using ::android::sp;
using ::vendor::google_clockwork::lefty::V1_0::ILefty;
using ::vendor::google_clockwork::lefty::V1_0::implementation::Lefty;

void register_lefty_service() {
    // Kids, don't do this at home. Here, registerAsService is called without
    // configureRpcThreadpool/joinRpcThreadpool, because it is called from
    // open_sensors() function, called from HIDL_FETCH_ISensors, called from
    // ISensor::getService, called from registerPassthroughServiceImplementation
    // which is surrounded with configureRpcThreadpool/joinRpcThreadpool.
    sp<ILefty> lefty = new Lefty();
    CHECK_EQ(lefty->registerAsService(), android::NO_ERROR)
            << "Failed to register Lefty HAL";
}
