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

#include <hardware/audio_effect.h>
//------------------------------------------------------------------------------
// Effect descriptors
//------------------------------------------------------------------------------

// UUIDs for effect types have been generated from http://www.itu.int/ITU-T/asn1/uuid.html
// as the pre processing effects are not defined by OpenSL ES

// Acoustic Echo Cancellation 5d593ae0-1ef9-11e7-93ae-92361f002671
const effect_descriptor_t qcom_product_aec_descriptor = {
        { 0x7b491460, 0x8d4d, 0x11e0, 0xbd61, { 0x00, 0x02, 0xa5, 0xd5, 0xc5, 0x1b } }, // type
        { 0x5d593ae0, 0x1ef9, 0x11e7, 0x93ae, { 0x92, 0x36, 0x1f, 0x00, 0x26, 0x71 } }, // uuid
        EFFECT_CONTROL_API_VERSION,
        (EFFECT_FLAG_TYPE_PRE_PROC|EFFECT_FLAG_DEVICE_IND|EFFECT_FLAG_HW_ACC_TUNNEL),
        0,
        0,
        "Acoustic Echo Canceler",
        "Qualcomm Fluence for Wahoo"
};

// Noise suppression 5d593d56-1ef9-11e7-93ae-92361f002671
const effect_descriptor_t qcom_product_ns_descriptor = {
        { 0x58b4b260, 0x8e06, 0x11e0, 0xaa8e, { 0x00, 0x02, 0xa5, 0xd5, 0xc5, 0x1b } }, // type
        { 0x5d593d56, 0x1ef9, 0x11e7, 0x93ae, { 0x92, 0x36, 0x1f, 0x00, 0x26, 0x71 } }, // uuid
        EFFECT_CONTROL_API_VERSION,
        (EFFECT_FLAG_TYPE_PRE_PROC|EFFECT_FLAG_DEVICE_IND|EFFECT_FLAG_HW_ACC_TUNNEL),
        0,
        0,
        "Noise Suppression",
        "Qualcomm Fluence for Wahoo"
};
