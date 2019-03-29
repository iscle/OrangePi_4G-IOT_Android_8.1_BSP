/*
 * Copyright (C) 2011 The Android Open Source Project
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

/*
 * Contains implementation of a class EmulatedQemuCamera that encapsulates
 * functionality of an emulated camera connected to the host.
 */

#define LOG_NDEBUG 0
#define LOG_TAG "EmulatedCamera_QemuCamera"
#include <cutils/log.h>
#include "EmulatedQemuCamera.h"
#include "EmulatedCameraFactory.h"

#undef min
#undef max
#include <sstream>
#include <string>
#include <vector>

namespace android {

EmulatedQemuCamera::EmulatedQemuCamera(int cameraId, struct hw_module_t* module)
        : EmulatedCamera(cameraId, module),
          mQemuCameraDevice(this)
{
}

EmulatedQemuCamera::~EmulatedQemuCamera()
{
}

/****************************************************************************
 * EmulatedCamera virtual overrides.
 ***************************************************************************/

status_t EmulatedQemuCamera::Initialize(const char* device_name,
                                        const char* frame_dims,
                                        const char* facing_dir)
{
    ALOGV("%s:\n   Name=%s\n   Facing '%s'\n   Dimensions=%s",
         __FUNCTION__, device_name, facing_dir, frame_dims);
    /* Save dimensions. */
    mFrameDims = frame_dims;

    /* Initialize camera device. */
    status_t res = mQemuCameraDevice.Initialize(device_name);
    if (res != NO_ERROR) {
        return res;
    }

    /* Initialize base class. */
    res = EmulatedCamera::Initialize();
    if (res != NO_ERROR) {
        return res;
    }

    /*
     * Set customizable parameters.
     */
    using Size = std::pair<int, int>;
    std::vector<Size> resolutions;
    std::stringstream ss(frame_dims);
    std::string input;
    while (std::getline(ss, input, ',')) {
        int width = 0;
        int height = 0;
        char none = 0;
        /* Expect only two results because that means there was nothing after
         * the height, we don't want any trailing characters. Otherwise we just
         * ignore this entry. */
        if (sscanf(input.c_str(), "%dx%d%c", &width, &height, &none) == 2) {
            resolutions.push_back(Size(width, height));
            ALOGE("%s: %dx%d", __FUNCTION__, width, height);
        }
    }

    /* The Android framework contains a wrapper around the v1 Camera API so that
     * it can be used with API v2. This wrapper attempts to figure out the
     * sensor resolution of the camera by looking at the resolution with the
     * largest area and infer that the dimensions of that resolution must also
     * be the size of the camera sensor. Any resolution with a dimension that
     * exceeds the sensor size will be rejected so Camera API calls will start
     * failing. To work around this we remove any resolutions with at least one
     * dimension exceeding that of the max area resolution. */

    /* First find the resolution with the maximum area, the "sensor size" */
    int maxArea = 0;
    int maxAreaWidth = 0;
    int maxAreaHeight = 0;
    for (const auto& res : resolutions) {
        int area = res.first * res.second;
        if (area > maxArea) {
            maxArea = area;
            maxAreaWidth = res.first;
            maxAreaHeight = res.second;
        }
    }

    /* Next remove any resolution with a dimension exceeding the sensor size. */
    for (auto res = resolutions.begin(); res != resolutions.end(); ) {
        if (res->first > maxAreaWidth || res->second > maxAreaHeight) {
            /* Width and/or height larger than sensor, remove it */
            res = resolutions.erase(res);
        } else {
            ++res;
        }
    }

    if (resolutions.empty()) {
        ALOGE("%s: Qemu camera has no valid resolutions", __FUNCTION__);
        return EINVAL;
    }

    /* Next rebuild the frame size string for the camera parameters */
    std::stringstream sizesStream;
    for (size_t i = 0; i < resolutions.size(); ++i) {
        if (i != 0) {
            sizesStream << ',';
        }
        sizesStream << resolutions[i].first << 'x' << resolutions[i].second;
    }
    std::string sizes = sizesStream.str();

    mParameters.set(EmulatedCamera::FACING_KEY, facing_dir);
    mParameters.set(EmulatedCamera::ORIENTATION_KEY,
                    gEmulatedCameraFactory.getQemuCameraOrientation());
    mParameters.set(CameraParameters::KEY_ROTATION,
                    gEmulatedCameraFactory.getQemuCameraOrientation());
    mParameters.set(CameraParameters::KEY_SUPPORTED_PICTURE_SIZES,
                    sizes.c_str());
    mParameters.set(CameraParameters::KEY_SUPPORTED_PREVIEW_SIZES,
                    sizes.c_str());

    /*
     * Use first dimension reported by the device to set current preview and
     * picture sizes.
     */
    int x = resolutions[0].first;
    int y = resolutions[0].second;
    mParameters.setPreviewSize(x, y);
    mParameters.setPictureSize(x, y);

    ALOGV("%s: Qemu camera %s is initialized. Current frame is %dx%d",
         __FUNCTION__, device_name, x, y);

    return NO_ERROR;
}

EmulatedCameraDevice* EmulatedQemuCamera::getCameraDevice()
{
    return &mQemuCameraDevice;
}

};  /* namespace android */
