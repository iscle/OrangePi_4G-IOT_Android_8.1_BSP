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
 * Copyright (C) 2008 The Android Open Source Project
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

#ifndef _MTK_HARDWARE_MTKCAM_INCLUDE_MTKCAM_UTILS_FWK_MTKCAMERA_H_
#define _MTK_HARDWARE_MTKCAM_INCLUDE_MTKCAM_UTILS_FWK_MTKCAMERA_H_

#include <binder/IMemory.h>

namespace android {

// extended msgType in notifyCallback and dataCallback functions
// NOTE: make sure that MTK_CAMERA_MSG_ALL_MSGS is the union of all msgs
enum {
    MTK_CAMERA_MSG_EXT_NOTIFY       = 0x40000000,
    MTK_CAMERA_MSG_EXT_DATA         = 0x20000000,
    MTK_CAMERA_MSG_ALL_MSGS         = 0x60000000,
};

// extended notify message related to MTK_CAMERA_MSG_EXT_NOTIFY used in notifyCallback functions
enum {
    //
    //  Smile Detection
    MTK_CAMERA_MSG_EXT_NOTIFY_SMILE_DETECT      = 0x00000001,
    //
    //  Auto Scene Detection
    MTK_CAMERA_MSG_EXT_NOTIFY_ASD               = 0x00000002,
    //
    //  Multi Angle View
    MTK_CAMERA_MSG_EXT_NOTIFY_MAV               = 0x00000003,
    //
    // Burst Shutter Callback
    //  ext2: count-down shutter number; 0: the last one shutter.
    MTK_CAMERA_MSG_EXT_NOTIFY_BURST_SHUTTER     = 0x00000004,
    //
    // Continuous Shutter Callback
    //  ext2: current continuous shutter number.
    MTK_CAMERA_MSG_EXT_NOTIFY_CONTINUOUS_SHUTTER= 0x00000005,
    //
    // Continuous EndCallback
    MTK_CAMERA_MSG_EXT_NOTIFY_CONTINUOUS_END    = 0x00000006,

    // ZSD preview done
    MTK_CAMERA_MSG_EXT_NOTIFY_ZSD_PREVIEW_DONE  = 0x00000007,
    //
    // Capture done (disable CAMERA_MSG_SHUTTER / CAMERA_MSG_COMPRESSED_IMAGE)
    MTK_CAMERA_MSG_EXT_NOTIFY_CAPTURE_DONE      = 0x00000010,
    //
    // Shutter Callback (not disable CAMERA_MSG_SHUTTER)
    //  ext2: 1: CameraService will play shutter sound.
    MTK_CAMERA_MSG_EXT_NOTIFY_SHUTTER           = 0x00000011,
    //
    // for EM preview raw dump error notify
    MTK_CAMERA_MSG_EXT_NOTIFY_RAW_DUMP_STOPPED  = 0x00000012,
    //
    // Gesture Detection
    MTK_CAMERA_MSG_EXT_NOTIFY_GESTURE_DETECT    = 0x00000013,
    //
    // Stereo Feature: warning message
    MTK_CAMERA_MSG_EXT_NOTIFY_STEREO_WARNING    = 0x00000014,
    //
    // Stereo Feature: distance value
    MTK_CAMERA_MSG_EXT_NOTIFY_STEREO_DISTANCE   = 0x00000015,
    //
    // Result & Static metadata
    MTK_CAMERA_MSG_EXT_NOTIFY_METADATA_DONE     = 0x00000016,
    //
    // ZSD capture early callback
    MTK_CAMERA_MSG_EXT_NOTIFY_P2DONE            = 0x00000017,
};

// extended data message related to MTK_CAMERA_MSG_EXT_DATA used in dataCallback functions
// extended data header is located at the top of dataPrt in dataCallback functions
//  DATA: Header + Params
enum {
    //
    // Auto Panorama
    //  Params:
    //      int[0]: 0:mAUTORAMAMVCallback, 1:mAUTORAMACallback
    //      int[1~]:depends on
    //
    MTK_CAMERA_MSG_EXT_DATA_AUTORAMA            = 0x00000001,
    //
    // AF Window Results
    MTK_CAMERA_MSG_EXT_DATA_AF                  = 0x00000002,
    //
    // Burst Shot (EV Shot)
    //      int[0]: the total shut count.
    //      int[1]: count-down shut number; 0: the last one shut.
    MTK_CAMERA_MSG_EXT_DATA_BURST_SHOT          = 0x00000003,
    //
    //    Continuous Shot
    //        int[0]: current continuous shut number.
    MTK_CAMERA_MSG_EXT_DATA_CONTINUOUS_SHOT     = 0x00000004,


    MTK_CAMERA_MSG_EXT_DATA_OT                  = 0x00000005,

    //  Facebeauty Shot
    //      int[0]: data type. 0:original image.
    MTK_CAMERA_MSG_EXT_DATA_FACEBEAUTY          = 0x00000006,
    //
    //  MAV Shot
    //      int[0]: data type. 0:original image.
    MTK_CAMERA_MSG_EXT_DATA_MAV                 = 0x00000007,
    //
    //  HDR Shot
    //      int[0]: data type. 0:0EV image.
    MTK_CAMERA_MSG_EXT_DATA_HDR                 = 0x00000008,

    //
    // Motion Track
    //  Params:
    //      int[0]: 0: frame EIS, 1: captured image, 2: blended image, 3: intermediate data
    //      int[1~]:depends on
    //
    MTK_CAMERA_MSG_EXT_DATA_MOTIONTRACK         = 0x00000009,

    //
    //  Compressed Image (not disable CAMERA_MSG_COMPRESSED_IMAGE)
    //      int[0]: current shut index; 0: the first one shut.
    MTK_CAMERA_MSG_EXT_DATA_COMPRESSED_IMAGE    = 0x00000010,

    //
    //  Stereo Shot
    //      int[0]: data type.
    MTK_CAMERA_MSG_EXT_DATA_JPS                 = 0x00000011,

    //
    //  Stereo Debug Data
    //      int[0]: data type.
    MTK_CAMERA_MSG_EXT_DATA_STEREO_DBG          = 0x00000012,

    //
    // raw16
    MTK_CAMERA_MSG_EXT_DATA_RAW16               = 0x00000013,

    //
    // Stereo Shot
    //      int[0]: data type.
    MTK_CAMERA_MSG_EXT_DATA_DEPTHMAP            = 0x00000014,

    //
    // Stereo Shot
    //      int[0]: data type.
    MTK_CAMERA_MSG_EXT_DATA_STEREO_CLEAR_IMAGE  = 0x00000015,

    //
    // LDC image
    //      int[0]: data type.
    MTK_CAMERA_MSG_EXT_DATA_STEREO_LDC          = 0x00000016,

    //
    // MAIN1 YUV image
    //      int[0]: data type.
    MTK_CAMERA_MSG_EXT_DATA_STEREO_MAIN1_YUV    = 0x00000017,

    //
    // MAIN2 YUV image
    //      int[0]: data type.
    MTK_CAMERA_MSG_EXT_DATA_STEREO_MAIN2_YUV    = 0x00000018,

    //
    // N3D debug image
    //      int[0]: data type.
    MTK_CAMERA_MSG_EXT_DATA_STEREO_N3D_DEBUG    = 0x00000019,

    //
    // Depthmap wrapper image
    //      int[0]: data type.
    MTK_CAMERA_MSG_EXT_DATA_STEREO_DEPTHWRAPPER    = 0x00000020,

};
// extended data message related to MTK_CAMERA_MSG_EXT_DATA used in dataCallback functions
// extended data header is located at the top of dataPrt in dataCallback functions
//  DATA: Header + Params
enum {
    //
    // static & result metadata for raw16
    MTK_CAMERA_MSG_EXT_METADATA_RAW16            = 0x00000001,

    // static & result metadata for 3rd party
    MTK_CAMERA_MSG_EXT_METADATA_3RDPARTY         = 0x00000002,
};

//  MTK-extended camera message data helper.
//  DATA: Header + Params
class MtkCamMsgExtDataHelper
{
public:
    //  The header type of MTK-extended camera message data.
    struct DataHeader {
        uint32_t        extMsgType;
    };

public:
    MtkCamMsgExtDataHelper();
    ~MtkCamMsgExtDataHelper();
    bool            init(const sp<IMemory>& dataPtr);
    bool            uninit();
    bool            create(size_t const extParamSize, uint32_t const u4ExtMsgType);
    bool            destroy();

    uint8_t*                        getExtParamBase() const;
    size_t                          getExtParamSize() const;
    ssize_t                         getExtParamOffset() const;
    inline uint32_t                 getExtMsgType() const { return mExtDataHdr.extMsgType; }
    inline DataHeader const&        getExtDataHeader() const { return mExtDataHdr; }
    inline sp<IMemory>const&        getData() const { return mspData; }
    inline sp<IMemoryHeap>const&    getHeap() const { return mspHeap; }

protected:
    bool            mIsValid;
    sp<IMemory>     mspData;
    sp<IMemoryHeap> mspHeap;
    ssize_t         mDataOffset;
    size_t          mDataSize;
    DataHeader      mExtDataHdr;
};


// cmdType in sendCommand functions
enum {
    CAMERA_CMD_MTK_DEFINE_START     = 0x10000000,
    CAMERA_CMD_DO_PANORAMA,
    CAMERA_CMD_CANCEL_PANORAMA,
    CAMERA_CMD_START_SD_PREVIEW,            //(Smile Detection)
    CAMERA_CMD_CANCEL_SD_PREVIEW,           //(Smile Detection)
    CAMERA_CMD_START_OT,
    CAMERA_CMD_STOP_OT,
    CAMERA_CMD_START_MAV,
    CAMERA_CMD_STOP_MAV,
    CAMERA_CMD_START_AUTORAMA,
    CAMERA_CMD_STOP_AUTORAMA,
    CAMERA_CMD_GET_MEM_INFO,                //For Video to get PMEM buffer info
    CAMERA_CMD_GET_REC_BUF_INFO,
    CAMERA_CMD_CANCEL_CSHOT,
    CAMERA_CMD_SET_CSHOT_SPEED,
    CAMERA_CMD_START_3DSHOT,
    CAMERA_CMD_STOP_3DSHOT,
    CAMERA_CMD_START_MOTIONTRACK,
    CAMERA_CMD_STOP_MOTIONTRACK,
    CAMERA_CMD_START_CLONECAMERA,
    CAMERA_CMD_SHOT_CLONECAMERA,
    CAMERA_CMD_STOP_CLONECAMERA,
    CAMERA_CMD_START_GD_PREVIEW,            //(Gesture Detection)
    CAMERA_CMD_CANCEL_GD_PREVIEW,           //(Gesture Detection)

    // For SDK Heartrate
    CAMERA_CMD_START_HR_PREVIEW,            //(Heartrate Detection)
    CAMERA_CMD_STOP_HR_PREVIEW,             //(Heartrate Detection)
    CAMERA_CMD_SETCB_HR_PREVIEW,            //(Heartrate Detection)
    CAMERA_CMD_SETUSER_HR_PREVIEW,          //(Heartrate Detection)
    CAMERA_CMD_SETMODE_HR_PREVIEW,          //(Heartrate Detection)
    CAMERA_CMD_CHECKPARA_HR_PREVIEW,        //(Heartrate Detection)

    // For Main face informatin
    CAMERA_CMD_SET_MAIN_FACE_COORDINATE,
    CAMERA_CMD_CANCEL_MAIN_FACE,
    //
    CAMERA_CMD_ENABLE_RAW16_CALLBACK,
};


/*
 *  set camera fatal errors enum
 *
 */
enum {
    CAMERA_ERROR_NO_MEMORY   = 1000,
    CAMERA_ERROR_RESET       = 1001,
    CAMERA_ERROR_CALI_FLASH  = 1002,
};


}; // namespace android

#endif  //_MTK_HARDWARE_MTKCAM_INCLUDE_MTKCAM_UTILS_FWK_MTKCAMERA_H_
