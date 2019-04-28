/*
 * Copyright 2016 The Android Open Source Project
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
//#define LOG_NDEBUG 0
#define LOG_TAG "HdrPlusClientUtils"
#include <log/log.h>

#include <fstream>
#include <inttypes.h>
#include <system/graphics.h>

#include "HdrPlusClientUtils.h"

namespace android {
namespace hdrplus_client_utils {

// Get the RGB values of the pixel at (x, y).
static status_t getRgb(uint8_t *r, uint8_t *g, uint8_t* b, uint32_t x, uint32_t y,
        const pbcamera::StreamConfiguration &streamConfig,
        const pbcamera::StreamBuffer &buffer) {
    switch (streamConfig.image.format) {
        case HAL_PIXEL_FORMAT_YCrCb_420_SP:
        {
            // Check the stream configuration has two planes.
            if (streamConfig.image.planes.size() != 2) {
                ALOGE("%s: NV21 should have 2 planes but it has %zu", __FUNCTION__,
                        streamConfig.image.planes.size());
                return BAD_VALUE;
            }

            // Find the indices of Y, V, and U in the buffer.
            uint32_t yIndex = y * streamConfig.image.planes[0].stride + x;
            uint32_t vIndex = streamConfig.image.planes[0].scanline *
                              streamConfig.image.planes[0].stride +
                              (y / 2) * streamConfig.image.planes[1].stride + (x & ~0x1);
            uint32_t uIndex = vIndex + 1;

            // Convert YUV to RGB.
            int32_t yc = ((uint8_t*)buffer.data)[yIndex];
            int32_t vc = ((uint8_t*)buffer.data)[vIndex] - 128;
            int32_t uc = ((uint8_t*)buffer.data)[uIndex] - 128;
            *r = std::min(std::max(yc + 0.003036f * uc + 1.399457f * vc, 0.0f), 255.0f);
            *g = std::min(std::max(yc - 0.344228f * uc - 0.717202f * vc, 0.0f), 255.0f);
            *b = std::min(std::max(yc + 1.772431f * uc - 0.006137f * vc, 0.0f), 255.0f);
            return OK;
        }
        case HAL_PIXEL_FORMAT_RGB_888:
        {
            // Check the stream configuration has 1 plane.
            if (streamConfig.image.planes.size() != 1) {
                ALOGE("%s: RGB_888 should have 1 plane but it has %zu", __FUNCTION__,
                        streamConfig.image.planes.size());
                return BAD_VALUE;
            }

            uint32_t offset = y * streamConfig.image.planes[0].stride + x * 3;
            *r = ((uint8_t*)buffer.data)[offset];
            *g = ((uint8_t*)buffer.data)[offset + 1];
            *b = ((uint8_t*)buffer.data)[offset + 2];
            return OK;
        }
        default:
            ALOGE("%s: Format %d is not supported.", __FUNCTION__, streamConfig.image.format);
            return BAD_VALUE;
    }
}

status_t writePpm(const std::string &filename, const pbcamera::StreamConfiguration &streamConfig,
        const pbcamera::StreamBuffer &buffer) {
    if (streamConfig.image.format != HAL_PIXEL_FORMAT_YCrCb_420_SP &&
            streamConfig.image.format != HAL_PIXEL_FORMAT_RGB_888) {
        ALOGE("%s: format 0x%x is not supported.", __FUNCTION__, streamConfig.image.format);
        return BAD_VALUE;
    }

    std::ofstream outfile(filename, std::ios::binary);
    if (!outfile.is_open()) {
        ALOGE("%s: Opening file (%s) failed.", __FUNCTION__, filename.data());
        return NO_INIT;
    }

    uint32_t width = streamConfig.image.width;
    uint32_t height = streamConfig.image.height;

    // Write headers of the ppm file.
    outfile << "P6";
    outfile << " " << std::to_string(width) << " " << std::to_string(height) << " 255 ";

    // Write RGB values of the image.
    uint8_t r, g, b;
    for (uint32_t y = 0; y < height; y++) {
        for (uint32_t x = 0; x < width; x++) {
            status_t res = getRgb(&r, &g, &b, x, y, streamConfig, buffer);
            if (res != OK) {
                ALOGE("%s: Getting RGB failed: %s (%d).", __FUNCTION__, strerror(-res), res);
                return res;
            }
            outfile << r << g << b;
        }
    }

    ALOGD("%s: Saved file: %s", __FUNCTION__, filename.data());

    outfile.close();
    return OK;
}

status_t comparePpm(const std::string &filename, const pbcamera::StreamConfiguration &streamConfig,
        const pbcamera::StreamBuffer &buffer, float *diffRatio) {
    if (streamConfig.image.format != HAL_PIXEL_FORMAT_YCrCb_420_SP) {
        ALOGE("%s: format 0x%x is not supported.", __FUNCTION__, streamConfig.image.format);
        return BAD_VALUE;
    }

    std::ifstream ifile(filename, std::ios::binary);
    if (!ifile.is_open()) {
        ALOGE("%s: Opening file (%s) failed.", __FUNCTION__, filename.data());
        return NO_INIT;
    }

    std::string s;

    // Read headers of the ppm file.
    ifile >> s;
    if (s != "P6") {
        ALOGE("%s: Invalid PPM file header: %s", __FUNCTION__, s.c_str());
        return BAD_VALUE;
    }

    // Read width and height.
    ifile >> s;
    uint32_t width = std::stoul(s);

    ifile >> s;
    uint32_t height = std::stoul(s);

    if (width != streamConfig.image.width || height != streamConfig.image.height) {
        ALOGE("%s: Image resolution doesn't match. image %dx%d ppm %dx%d",
                __FUNCTION__, streamConfig.image.width, streamConfig.image.height,
                width, height);
        return BAD_VALUE;
    }

    ifile >> s;
    if (s != "255") {
        ALOGE("%s: Expecting 255 but got %s", __FUNCTION__, s.c_str());
        return BAD_VALUE;
    }

    char c;

    // Get a space
    ifile.get(c);

    // Now the RGB values start.
    uint8_t r, g, b;
    uint64_t diff = 0;

    for (uint32_t y = 0; y < height; y++) {
        for (uint32_t x = 0; x < width; x++) {
            status_t res = getRgb(&r, &g, &b, x, y, streamConfig, buffer);
            if (res != OK) {
                ALOGE("%s: Getting RGB failed: %s (%d).", __FUNCTION__, strerror(-res), res);
                return res;
            }

            // Get r, g, b from golden image and accumulate the differences.
            ifile.get(c);
            diff += abs(static_cast<int32_t>(c) - r);
            ifile.get(c);
            diff += abs(static_cast<int32_t>(c) - g);
            ifile.get(c);
            diff += abs(static_cast<int32_t>(c) - b);
        }
    }

    if (diffRatio != nullptr) {
        *diffRatio = diff / (static_cast<float>(width) * height * 3 * 256);
    }

    return OK;
}

} // hdrplus_client_utils
} // namespace android
