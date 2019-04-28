/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */

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

#define LOG_TAG "CameraProviderManager"
#define ATRACE_TAG ATRACE_TAG_CAMERA
//#define LOG_NDEBUG 0

#include "common/CameraProviderManager.h"
#include <vendor/mediatek/hardware/camera/device/1.1/IMtkCameraDevice.h>

namespace android {

status_t CameraProviderManager::setProperty( String8 const& key, String8 const& value )
{
    ALOGV("setProperty + : key %s, value %s", key.c_str(), value.c_str());
    for (auto& provider : mProviders) {
        for (auto& device : provider->mDevices) {
            //This method is implemented in Device HAL 1.x
            //so it's only available for device with version 1.x
            if (device->mVersion.get_major() == 1) {
                 auto mCamera =  device.get();
                 if (mCamera == nullptr){
                     ALOGE("get camera device failed: device_name = %s", device->mName.c_str());
                     return NAME_NOT_FOUND;
                 }
                 auto* mCamera1 = static_cast<ProviderInfo::DeviceInfo1*>(mCamera);
                 sp<vendor::mediatek::hardware::camera::device::V1_1::IMtkCameraDevice> mMtkCamera =
                     vendor::mediatek::hardware::camera::device::V1_1::IMtkCameraDevice::castFrom(mCamera1->mInterface);

                 if (mMtkCamera == nullptr){
                     ALOGE("Cast from CameraDevice to MtkCameraDevice failed");
                     return INVALID_OPERATION;
                 }

                 hardware::hidl_string hidlkey = hardware::hidl_string(key);
                 hardware::hidl_string hidlvalue = hardware::hidl_string(value);
                 hardware::Return<void> ret = mMtkCamera->setProperty(hidlkey, hidlvalue);

                 if (!ret.isOk()) {
                     ALOGE("setProperty failed: device_name = %s", device->mName.c_str());
                     return DEAD_OBJECT;
                 }
                 return OK;
            }
        }
    }

    //no device1.x exists
    return INVALID_OPERATION;
}

status_t CameraProviderManager::getProperty( String8 const& key, String8& value )
{
    ALOGV("getProperty + : key %s", key.c_str());
    for (auto& provider : mProviders) {
        for (auto& device : provider->mDevices) {
            //This method is implemented in Device HAL 1.x
            //so it's only available for device with version 1.x
            if (device->mVersion.get_major() == 1) {
                 auto mCamera =  device.get();
                 if (mCamera == nullptr){
                     ALOGE("get camera device failed: device_name = %s", device->mName.c_str());
                     return NAME_NOT_FOUND;
                 }
                 auto* mCamera1 = static_cast<ProviderInfo::DeviceInfo1*>(mCamera);
                 sp<vendor::mediatek::hardware::camera::device::V1_1::IMtkCameraDevice> mMtkCamera =
                     vendor::mediatek::hardware::camera::device::V1_1::IMtkCameraDevice::castFrom(mCamera1->mInterface);

                 if (mMtkCamera == nullptr){
                     ALOGE("Cast from CameraDevice to MtkCameraDevice failed");
                     return INVALID_OPERATION;
                 }

                 hardware::hidl_string hidlkey = hardware::hidl_string(key);
                 hardware::Return<void> ret = mMtkCamera->getProperty(hidlkey, [&value]
                                                          (auto& v){value = String8(v.c_str());
                                                       });

                 if (!ret.isOk()) {
                     ALOGE("getProperty failed: device_name = %s", device->mName.c_str());
                     return DEAD_OBJECT;
                 }
                 ALOGV("getProperty - : key %s, value %s", key.c_str(), value.c_str());
                 return OK;
            }
        }
    }

    //no device1.x exists
    return INVALID_OPERATION;
}

} // namespace android
