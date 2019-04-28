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

#ifndef MTK_MPEG4_WRITER_H_

#define MTK_MPEG4_WRITER_H_

#include <stdio.h>

#include <media/IMediaSource.h>
#include <media/stagefright/MPEG4Writer.h>
#include <utils/List.h>
#include <utils/threads.h>
#include <media/stagefright/foundation/AHandlerReflector.h>
#include <media/stagefright/foundation/ALooper.h>

namespace android {

class MtkMPEG4Writer : public MPEG4Writer {
public:
    explicit MtkMPEG4Writer(int fd);

protected:
    virtual ~MtkMPEG4Writer();

    MtkMPEG4Writer(const MtkMPEG4Writer &);
    MtkMPEG4Writer &operator=(const MtkMPEG4Writer &);

public:
    // add common interfaces
    void        init();
    void        initStart(MetaData * /*param*/);
    // add notify file size to app for mms
    void        notifyEstimateSize(int64_t /*nTotalBytesEstimate*/);

    // for EIS
    void        setEISStop() { mIsEISStop = true; }
    bool        getEISStop() { return mIsEISStop; }
    void        setTrackDurationUs(bool isaudio, int64_t timeUs);
    int64_t     getAudioDurationUs();
    int64_t     getVideoDurationUs();

private:
    // add notify file size to app for mms
    uint32_t    mNotifyCounter;
    int32_t     mMediaInfoFlag;  // add for mtk defined infos in mediarecorder.h.

    // for EIS
    bool        mIsEISStop;
    int64_t     mAudioDurationUs;
    int64_t     mVideoDurationUs;
};

}  // namespace android

#endif  // MTK_MPEG4_WRITER_H_
