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

//#define LOG_NDEBUG 0
#define LOG_TAG "MtkMPEG4Writer"

#include <algorithm>

#include <arpa/inet.h>
#include <fcntl.h>
#include <inttypes.h>
#include <pthread.h>
#include <sys/prctl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#include <utils/Log.h>

#include <functional>

#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/AMessage.h>
#include <media/stagefright/foundation/AUtils.h>
#include <media/stagefright/foundation/ColorUtils.h>
#include <stagefright/include/MtkMPEG4Writer.h>
#include <media/stagefright/MediaBuffer.h>
#include <media/stagefright/MetaData.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/Utils.h>
#include <media/mediarecorder.h>
#include <cutils/properties.h>

namespace android {
#define TRACK_SKIPNOTIFY_RATIO          3

MtkMPEG4Writer::MtkMPEG4Writer(int fd)
    : MPEG4Writer(fd) {
    ALOGD("MtkMPEG4Writer constructor");
    // init will not been implemented when call initInternal() in MPEG4Writer constructor,
    // as MtkMPEG4Writer not been created yet, so init must been called again bellow.
    init();
}

MtkMPEG4Writer::~MtkMPEG4Writer() {
}

// add common interfaces
void MtkMPEG4Writer::init() {
    // add notify file size to app for mms
    mNotifyCounter = 0;
    mMediaInfoFlag = 0;  // add for mtk defined infos in mediarecorder.h.

    // for EIS
    mIsEISStop = false;
    mAudioDurationUs = 0;
    mVideoDurationUs = 0;
}

void MtkMPEG4Writer::initStart(MetaData *param) {
    // add for mtk defined infos in mediarecorder.h.
    int32_t mediainfoflag = 0;
    if (param &&
        param->findInt32(kKeyMediaInfoFlag, &mediainfoflag)) {
        mMediaInfoFlag = mediainfoflag;
    }
}

// add notify file size to app for mms
void MtkMPEG4Writer::notifyEstimateSize(int64_t nTotalBytesEstimate) {
    if (0 == (mNotifyCounter % TRACK_SKIPNOTIFY_RATIO)) {
        // add for mtk defined infos in mediarecorder.h.
        if (mMediaInfoFlag & RECORDING_SIZE_FLAG) {
            ALOGV("notify nTotalBytesEstimate %" PRId64 ", %d", nTotalBytesEstimate, mNotifyCounter);
            // add notify file size to app for mms
            notify(MEDIA_RECORDER_EVENT_INFO, MEDIA_RECORDER_INFO_RECORDING_SIZE, (int)nTotalBytesEstimate);
        }
    }
    mNotifyCounter++;
}

/*
void MtkMPEG4Writer::Track::init() {
    mMediaInfoFlag = 0;  // add for mtk defined infos in mediarecorder.h.
}
void MtkMPEG4Writer::Track::initStart(MetaData *params) {
    // add for mtk defined infos in mediarecorder.h.
    int32_t mediainfoflag = 0;
    if (params && params->findInt32(kKeyMediaInfoFlag, &mediainfoflag)) {
        ALOGV("kKeyMediaInfoFlag = %x", mediainfoflag);
        mMediaInfoFlag = mediainfoflag;
    }
}
*/

// for EIS
void MtkMPEG4Writer::setTrackDurationUs(bool isaudio, int64_t timeUs) {
    if (isaudio) {
        mAudioDurationUs = timeUs;
    } else {
        mVideoDurationUs = timeUs;
    }
}

int64_t MtkMPEG4Writer::getAudioDurationUs() {
    return mAudioDurationUs;
}

int64_t MtkMPEG4Writer::getVideoDurationUs() {
    return mVideoDurationUs;
}

}  // namespace android
