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

#include "android/os/Temperature.h"

#include <math.h>
#include <stdint.h>
#include <binder/Parcel.h>
#include <hardware/thermal.h>
#include <sys/types.h>
#include <utils/Errors.h>

namespace android {
namespace os {

Temperature::Temperature() : value_(NAN), type_(DEVICE_TEMPERATURE_UNKNOWN) {}

Temperature::Temperature(const float value, const int type) :
    value_(value), type_(type)  {}

Temperature::~Temperature() {}

/*
 * Parcel read/write code must be kept in sync with
 * frameworks/base/core/java/android/os/Temperature.java
 */

status_t Temperature::readFromParcel(const Parcel* p) {
    value_ = p->readFloat();
    type_ = p->readInt32();
    return OK;
}

status_t Temperature::writeToParcel(Parcel* p) const {
    p->writeFloat(value_);
    p->writeInt32(type_);
    return OK;
}

}  // namespace os
}  // namespace android
