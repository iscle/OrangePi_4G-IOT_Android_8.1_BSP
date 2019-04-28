/*
 * Copyright 2017 The Android Open Source Project
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

#include <ui/DebugUtils.h>
#include <ui/PixelFormat.h>

#include <android-base/stringprintf.h>
#include <string>

std::string decodeStandard(android_dataspace dataspace) {
    const uint32_t dataspaceSelect = (dataspace & HAL_DATASPACE_STANDARD_MASK);
    switch (dataspaceSelect) {
        case HAL_DATASPACE_STANDARD_BT709:
            return std::string("BT709");

        case HAL_DATASPACE_STANDARD_BT601_625:
            return std::string("BT601_625");

        case HAL_DATASPACE_STANDARD_BT601_625_UNADJUSTED:
            return std::string("BT601_625_UNADJUSTED");

        case HAL_DATASPACE_STANDARD_BT601_525:
            return std::string("BT601_525");

        case HAL_DATASPACE_STANDARD_BT601_525_UNADJUSTED:
            return std::string("BT601_525_UNADJUSTED");

        case HAL_DATASPACE_STANDARD_BT2020:
            return std::string("BT2020");

        case HAL_DATASPACE_STANDARD_BT2020_CONSTANT_LUMINANCE:
            return std::string("BT2020 (constant luminance)");

        case HAL_DATASPACE_STANDARD_BT470M:
            return std::string("BT470M");

        case HAL_DATASPACE_STANDARD_FILM:
            return std::string("FILM");

        case HAL_DATASPACE_STANDARD_DCI_P3:
            return std::string("DCI-P3");

        case HAL_DATASPACE_STANDARD_ADOBE_RGB:
            return std::string("AdobeRGB");

        case 0:
            switch (dataspace & 0xffff) {
                case HAL_DATASPACE_JFIF:
                    return std::string("(deprecated) JFIF (BT601_625)");

                case HAL_DATASPACE_BT601_625:
                    return std::string("(deprecated) BT601_625");

                case HAL_DATASPACE_BT601_525:
                    return std::string("(deprecated) BT601_525");

                case HAL_DATASPACE_SRGB_LINEAR:
                case HAL_DATASPACE_SRGB:
                    return std::string("(deprecated) sRGB");

                case HAL_DATASPACE_V0_BT709:
                    return std::string("(deprecated) BT709");

                case HAL_DATASPACE_ARBITRARY:
                    return std::string("ARBITRARY");

                case HAL_DATASPACE_UNKNOWN:
                // Fallthrough
                default:
                    return android::base::StringPrintf("Unknown deprecated dataspace code %d",
                                                       dataspaceSelect);
            }
    }

    return android::base::StringPrintf("Unknown dataspace code %d", dataspaceSelect);
}

std::string decodeTransfer(android_dataspace dataspace) {
    const uint32_t dataspaceSelect = (dataspace & HAL_DATASPACE_STANDARD_MASK);
    if (dataspaceSelect == 0) {
        switch (dataspace & 0xffff) {
            case HAL_DATASPACE_JFIF:
            case HAL_DATASPACE_BT601_625:
            case HAL_DATASPACE_BT601_525:
            case HAL_DATASPACE_V0_BT709:
                return std::string("SMPTE_170M");

            case HAL_DATASPACE_SRGB_LINEAR:
            case HAL_DATASPACE_ARBITRARY:
                return std::string("Linear");

            case HAL_DATASPACE_SRGB:
                return std::string("sRGB");

            case HAL_DATASPACE_UNKNOWN:
            // Fallthrough
            default:
                return std::string("");
        }
    }

    const uint32_t dataspaceTransfer = (dataspace & HAL_DATASPACE_TRANSFER_MASK);
    switch (dataspaceTransfer) {
        case HAL_DATASPACE_TRANSFER_UNSPECIFIED:
            return std::string("Unspecified");

        case HAL_DATASPACE_TRANSFER_LINEAR:
            return std::string("Linear");

        case HAL_DATASPACE_TRANSFER_SRGB:
            return std::string("sRGB");

        case HAL_DATASPACE_TRANSFER_SMPTE_170M:
            return std::string("SMPTE_170M");

        case HAL_DATASPACE_TRANSFER_GAMMA2_2:
            return std::string("gamma 2.2");

        case HAL_DATASPACE_TRANSFER_GAMMA2_6:
            return std::string("gamma 2.6");

        case HAL_DATASPACE_TRANSFER_GAMMA2_8:
            return std::string("gamma 2.8");

        case HAL_DATASPACE_TRANSFER_ST2084:
            return std::string("SMPTE 2084");

        case HAL_DATASPACE_TRANSFER_HLG:
            return std::string("STD-B67");
    }

    return android::base::StringPrintf("Unknown dataspace transfer %d", dataspaceTransfer);
}

std::string decodeRange(android_dataspace dataspace) {
    const uint32_t dataspaceSelect = (dataspace & HAL_DATASPACE_STANDARD_MASK);
    if (dataspaceSelect == 0) {
        switch (dataspace & 0xffff) {
            case HAL_DATASPACE_JFIF:
            case HAL_DATASPACE_SRGB_LINEAR:
            case HAL_DATASPACE_SRGB:
                return std::string("Full range");

            case HAL_DATASPACE_BT601_625:
            case HAL_DATASPACE_BT601_525:
            case HAL_DATASPACE_V0_BT709:
                return std::string("Limited range)");

            case HAL_DATASPACE_ARBITRARY:
            case HAL_DATASPACE_UNKNOWN:
            // Fallthrough
            default:
                return std::string("unspecified range");
        }
    }

    const uint32_t dataspaceRange = (dataspace & HAL_DATASPACE_RANGE_MASK);
    switch (dataspaceRange) {
        case HAL_DATASPACE_RANGE_UNSPECIFIED:
            return std::string("Range Unspecified");

        case HAL_DATASPACE_RANGE_FULL:
            return std::string("Full range");

        case HAL_DATASPACE_RANGE_LIMITED:
            return std::string("Limited range");

        case HAL_DATASPACE_RANGE_EXTENDED:
            return std::string("Extended range");
    }

    return android::base::StringPrintf("Unknown dataspace range %d", dataspaceRange);
}

std::string dataspaceDetails(android_dataspace dataspace) {
    if (dataspace == 0) {
        return "Default (0)";
    }
    return android::base::StringPrintf("%s %s %s", decodeStandard(dataspace).c_str(),
                                       decodeTransfer(dataspace).c_str(),
                                       decodeRange(dataspace).c_str());
}

std::string decodeColorMode(android_color_mode colorMode) {
    switch (colorMode) {
        case HAL_COLOR_MODE_NATIVE:
            return std::string("HAL_COLOR_MODE_NATIVE");

        case HAL_COLOR_MODE_STANDARD_BT601_625:
            return std::string("HAL_COLOR_MODE_BT601_625");

        case HAL_COLOR_MODE_STANDARD_BT601_625_UNADJUSTED:
            return std::string("HAL_COLOR_MODE_BT601_625_UNADJUSTED");

        case HAL_COLOR_MODE_STANDARD_BT601_525:
            return std::string("HAL_COLOR_MODE_BT601_525");

        case HAL_COLOR_MODE_STANDARD_BT601_525_UNADJUSTED:
            return std::string("HAL_COLOR_MODE_BT601_525_UNADJUSTED");

        case HAL_COLOR_MODE_STANDARD_BT709:
            return std::string("HAL_COLOR_MODE_BT709");

        case HAL_COLOR_MODE_DCI_P3:
            return std::string("HAL_COLOR_MODE_DCI_P3");

        case HAL_COLOR_MODE_SRGB:
            return std::string("HAL_COLOR_MODE_SRGB");

        case HAL_COLOR_MODE_ADOBE_RGB:
            return std::string("HAL_COLOR_MODE_ADOBE_RGB");

        case HAL_COLOR_MODE_DISPLAY_P3:
            return std::string("HAL_COLOR_MODE_DISPLAY_P3");
    }

    return android::base::StringPrintf("Unknown color mode %d", colorMode);
}

// Converts a PixelFormat to a human-readable string.  Max 11 chars.
// (Could use a table of prefab String8 objects.)
std::string decodePixelFormat(android::PixelFormat format) {
    switch (format) {
        case android::PIXEL_FORMAT_UNKNOWN:
            return std::string("Unknown/None");
        case android::PIXEL_FORMAT_CUSTOM:
            return std::string("Custom");
        case android::PIXEL_FORMAT_TRANSLUCENT:
            return std::string("Translucent");
        case android::PIXEL_FORMAT_TRANSPARENT:
            return std::string("Transparent");
        case android::PIXEL_FORMAT_OPAQUE:
            return std::string("Opaque");
        case android::PIXEL_FORMAT_RGBA_8888:
            return std::string("RGBA_8888");
        case android::PIXEL_FORMAT_RGBX_8888:
            return std::string("RGBx_8888");
        case android::PIXEL_FORMAT_RGBA_FP16:
            return std::string("RGBA_FP16");
        case android::PIXEL_FORMAT_RGBA_1010102:
            return std::string("RGBA_1010102");
        case android::PIXEL_FORMAT_RGB_888:
            return std::string("RGB_888");
        case android::PIXEL_FORMAT_RGB_565:
            return std::string("RGB_565");
        case android::PIXEL_FORMAT_BGRA_8888:
            return std::string("BGRA_8888");
        default:
            return android::base::StringPrintf("Unknown %#08x", format);
    }
}
