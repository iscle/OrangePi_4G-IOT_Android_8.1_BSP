/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

/*
 * Copyright (C) 2009 The Android Open Source Project
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
/*****************************************************************************
 *
 * Filename:
 * ---------
 *   ASFExtractor.h
 *
 * Project:
 * --------
 *   MT6573
 *
 * Description:
 * ------------
 *   ASF Extractor interface
 *
 * Author:
 * -------
 *   Morris Yang (mtk03147)
 *
 ****************************************************************************/
#ifndef ASF_EXTRACTOR_H_
#define ASF_EXTRACTOR_H_

#include <media/stagefright/MediaExtractor.h>
#include <media/stagefright/MediaBuffer.h>
#include <utils/Vector.h>
#include <utils/threads.h>
#include "asfparser.h"

namespace android {

#define ASF_EVERYTHING_FINE 0
#define ASF_END_OF_TRACK 1
#define ASF_INSUFFICIENT_DATA 2
#define ASF_INSUFFICIENT_BUFFER_SIZE 3
#define ASF_READ_FAILED 4

#define MAX_VIDEO_WIDTH 1920
#define MAX_VIDEO_HEIGHT 1080
#ifdef  MAX_VIDEO_INPUT_SIZE
#undef  MAX_VIDEO_INPUT_SIZE
#endif
#define MAX_VIDEO_INPUT_SIZE (MAX_VIDEO_WIDTH*MAX_VIDEO_HEIGHT*3 >> 1)
#define MAX_AUDIO_INPUT_SIZE (1024*20)

#define ASFFF_SHOW_TIMESTAMP        (1 << 0)
#define ASFFF_IGNORE_AUDIO_TRACK    (1 << 1)
#define ASFFF_IGNORE_VIDEO_TRACK    (1 << 2)

#define ASF_THUMBNAIL_SCAN_SIZE 10

struct AMessage;
class DataSource;
class String8;

#define GECKO_VERSION                 ((1L<<24)|(0L<<16)|(0L<<8)|(3L))

enum AsfStreamType {
    ASF_VIDEO,
    ASF_AUDIO,
    ASF_OTHER
};

enum {
    IVOP,
    PVOP,
    BVOP,
    BIVOP,
    SKIPPED
};


struct  VC1SeqData {
    uint32_t profile;
    uint32_t level;
    uint32_t rangered;
    uint32_t maxbframes;
    uint32_t finterpflag;
    uint32_t multires;
    uint32_t fps100;
    uint64_t us_time_per_frame;
};

struct AsfTrackInfo {
    uint32_t mTrackNum;
    sp<MetaData> mMeta;
    void * mCodecSpecificData;
    uint32_t mCodecSpecificSize;
    asf_packet_t * mNextPacket;
    uint32_t mCurPayloadIdx;
};

struct  VC1PicData {
    uint32_t interpfrm;
    uint32_t rangeredfrm;
    uint32_t frmcnt;
    uint32_t ptype;
};

enum NAL_Parser_Type {
    ASF_SIZE_NAL_TYPE,
    ASF_START_CODE_TYPE,
    ASF_OTHER_TYPE,
};

enum VC1_FourCC {
    WMV1_FOURCC,
    WMV2_FOURCC,
    WMV3_FOURCC,
    WVC1_FOURCC,
    WMVA_FOURCC
};

class ASFExtractor : public MediaExtractor {
public:
    // Extractor assumes ownership of "source".
    ASFExtractor(const sp<DataSource> &source);

    virtual size_t countTracks();
    virtual sp<IMediaSource> getTrack(size_t index);
    virtual sp<MetaData> getTrackMetaData(size_t index, uint32_t flags);

    virtual sp<MetaData> getMetaData();
    virtual uint32_t flags() const;

protected:
    virtual ~ASFExtractor();

    bool ParseASF();
    int64_t ASFSeekTo(uint32_t seekTimeMs);

    bool RetrieveWmvCodecSpecificData(asf_stream_t * pStreamProp, const sp<MetaData>& meta,
            VC1SeqData* pCodecSpecificData);
    bool RetrieveWmaCodecSpecificData(asf_stream_t * pStreamProp, const sp<MetaData>& meta);

    void findThumbnail();

public:
    friend struct ASFSource;

    friend int32_t asf_io_read_func(void *aSource, void *aBuffer, int32_t aSize);
    friend int32_t asf_io_write_func(void *aSource, void *aBuffer, int32_t aSize);
    friend int64_t asf_io_seek_func(void *aSource, int64_t aOffset);

    bool IsValidAsfFile() { return mIsValidAsfFile; }
    bool ParserASF();
    ASFErrorType GetNextMediaPayload(uint8_t* aBuffer, uint32_t& arSize, uint32_t& arTimeStamp,
            uint32_t& arRepDataSize, bool& bIsKeyFrame,uint32_t CurTrackIndex);
    ASFErrorType GetNextMediaFrame(MediaBuffer** out, bool& bIsKeyFrame, AsfStreamType strmType,
            bool *isSeeking,uint32_t CurTrackIndex);
    bool ParserVC1CodecPrivateData(uint8_t*input , uint32_t inputlen, VC1SeqData* pSeqData);
    status_t addMPEG4CodecSpecificData(const sp<MetaData>& mMeta);
    status_t addAVCCodecSpecificData(const sp<MetaData>& mMeta);
    NAL_Parser_Type getNALParserType();
    uint8_t getNALSizeLength();
    bool isNALStartCodeType(const sp<ABuffer> &buffer);
    bool isNALSizeNalType(const sp<ABuffer> &buffer);
    uint8_t getLengthSizeMinusOne(const sp<ABuffer> &buffer);
    uint32_t parseNALSize(uint8_t *data);
    status_t asfMakeAVCCodecSpecificData(const sp<ABuffer> &buffer,const sp<MetaData>& mMeta);
    VC1_FourCC getVC1FourCC();
    void MakeFourCCString(uint32_t x, char *s);
    uint32_t MakeStringToIntFourCC(char *s);

private:
    //VC1PicData mVC1CurPicData;
    sp<MetaData> mFileMeta;

    uint32_t mDbgFlags;
    bool mIgnoreAudio;
    bool mIgnoreVideo;
    uint64_t mDurationMs;
    bool mSeekable;

    bool mThumbnailMode;
    bool mExtractedThumbnails;

    uint64_t mPrerollTimeUs;
    //sp<MetaData> mFileMetaData;
    NAL_Parser_Type mNalParType;
    VC1_FourCC mVC1FourCC;
    uint8_t mSizeLength;

    ASFExtractor(const ASFExtractor &);
    ASFExtractor &operator=(const ASFExtractor &);

protected:
    sp<DataSource> mDataSource;
    Vector<AsfTrackInfo> mTracks;
    android::Mutex mCacheLock;

    ASFParser* mAsfParser;
    uint32_t mAsfParserReadOffset;
    bool mIsValidAsfFile;
    bool mIsAsfParsed;
    bool mHasVideo;
    bool mHasVideoTrack;
    uint64_t mFileSize;
};

int switchAACSampleRateToIndex_asf(int sample_rate);

bool SniffASF(const sp<DataSource> &source, String8 *mimeType, float *confidence, sp<AMessage> *meta);
}  // namespace android

#endif  // RM_EXTRACTOR_H_
