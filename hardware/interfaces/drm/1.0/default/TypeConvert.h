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

#ifndef ANDROID_HARDWARE_DRM_V1_0_TYPECONVERT
#define ANDROID_HARDWARE_DRM_V1_0_TYPECONVERT

#include <android/hardware/drm/1.0/types.h>
#include <media/stagefright/MediaErrors.h>
#include <utils/Vector.h>

namespace android {
namespace hardware {
namespace drm {
namespace V1_0 {
namespace implementation {

using ::android::hardware::hidl_vec;

template<typename T> const hidl_vec<T> toHidlVec(const Vector<T> &Vector) {
    hidl_vec<T> vec;
    vec.setToExternal(const_cast<T *>(Vector.array()), Vector.size());
    return vec;
}

template<typename T> hidl_vec<T> toHidlVec(Vector<T> &Vector) {
    hidl_vec<T> vec;
    vec.setToExternal(Vector.editArray(), Vector.size());
    return vec;
}

template<typename T> const Vector<T> toVector(const hidl_vec<T> &vec) {
    Vector<T> vector;
    vector.appendArray(vec.data(), vec.size());
    return *const_cast<const Vector<T> *>(&vector);
}

template<typename T> Vector<T> toVector(hidl_vec<T> &vec) {
    Vector<T> vector;
    vector.appendArray(vec.data(), vec.size());
    return vector;
}

template<typename T, size_t SIZE> const Vector<T> toVector(
        const hidl_array<T, SIZE> &array) {
    Vector<T> vector;
    vector.appendArray(array.data(), array.size());
    return vector;
}

template<typename T, size_t SIZE> Vector<T> toVector(
        hidl_array<T, SIZE> &array) {
    Vector<T> vector;
    vector.appendArray(array.data(), array.size());
    return vector;
}

Status toStatus(status_t legacyStatus);

}  // namespace implementation
}  // namespace V1_0
}  // namespace drm
}  // namespace hardware
}  // namespace android

#endif // ANDROID_HARDWARE_DRM_V1_0_TYPECONVERT
