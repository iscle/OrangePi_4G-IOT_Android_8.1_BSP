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
 *   ASFExtractor.cpp
 *
 * Project:
 * --------
 *   MT6573
 *
 * Description:
 * ------------
 *   ASF Extractor implementation
 *
 * Author:
 * -------
 *   Morris Yang (mtk03147)
 *
 ****************************************************************************/ 
#ifdef MTK_WMV_PLAYBACK_SUPPORT
#include <utils/Log.h>
#undef LOG_TAG
#define LOG_TAG "ASFExtractor"
#include "include/ASFExtractor.h"
#include <arpa/inet.h>
#include <ctype.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <avc_utils.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/DataSource.h>
#include <media/stagefright/MediaBuffer.h>
#include <media/stagefright/MediaBufferGroup.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MetaData.h>
#include <media/stagefright/foundation/ABuffer.h>
#include <media/stagefright/foundation/ABase.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/AMessage.h>
#include <media/stagefright/Utils.h>
#include <utils/String8.h>
#include <cutils/properties.h>
#ifdef MTK_DEMUXER_QUERY_CAPABILITY_FROM_DRV_SUPPORT
#include "vdec_drv_if_public.h"
#include "val_types_public.h"
#endif

namespace android {
#define ASF_DEBUG_LOGE(x, ...)  // ALOGE("[ASFExtractor]: "x,  ##__VA_ARGS__)
#define ASF_DEBUG_LOGV(x, ...)  // ALOGE("[ASFExtractor]: "x,  ##__VA_ARGS__)

static const uint32_t kMP3HeaderMask = 0xfffe0c00;
//0xfffe0cc0 add by zhihui zhang no consider channel mode

// copy from MP3Extractor
static bool get_mp3_frame_size(uint32_t header, size_t *frame_size, unsigned *out_sampling_rate = NULL,
        int *out_channels = NULL, unsigned *out_bitrate = NULL, unsigned *out_sampleperframe=NULL) {
    *frame_size = 0;
    unsigned sampleperframe = 0;
    if (out_sampling_rate) {
        *out_sampling_rate = 0;
    }

    if (out_channels) {
        *out_channels = 0;
    }

    if (out_bitrate) {
        *out_bitrate = 0;
    }

    if ((header & 0xffe00000) != 0xffe00000) {
        return false;
    }

    unsigned version = (header >> 19) & 3;

    if (version == 0x01) {
        return false;
    }

    unsigned layer = (header >> 17) & 3;

    if (layer == 0x00) {
        return false;
    } else if (layer == 3) {
        return false;
    }

    // add by zhihui zhang for mp2 framesize calculate
    if (layer == 2 || (version == 3 && layer == 1)) {
        sampleperframe = 1152;
    } else if ((version == 2 || version==0) && layer == 1) {
        sampleperframe=576;
    }
    if (out_sampleperframe != NULL) {
        *out_sampleperframe = sampleperframe;
    }

    unsigned bitrate_index = (header >> 12) & 0x0f;

    if (bitrate_index == 0 || bitrate_index == 0x0f) {
        // Disallow "free" bitrate.
        return false;
    }

    unsigned sampling_rate_index = (header >> 10) & 3;

    if (sampling_rate_index == 3) {
        return false;
    }

    static const unsigned kSamplingRateV1[] = { 44100, 48000, 32000 };
    unsigned sampling_rate = kSamplingRateV1[sampling_rate_index];
    if (version == 2 /* V2 */) {
        sampling_rate /= 2;
    } else if (version == 0 /* V2.5 */) {
        sampling_rate /= 4;
    }

    unsigned padding = (header >> 9) & 1;

    {
        // layer II or III

        static const unsigned kBitrateV1L2[] = {
            32, 48, 56, 64, 80, 96, 112, 128,
            160, 192, 224, 256, 320, 384
        };

        static const unsigned kBitrateV1L3[] = {
            32, 40, 48, 56, 64, 80, 96, 112,
            128, 160, 192, 224, 256, 320
        };

        static const unsigned kBitrateV2[] = {
            8, 16, 24, 32, 40, 48, 56, 64,
            80, 96, 112, 128, 144, 160
        };

        unsigned bitrate;
        if (version == 3 /* V1 */) {
            bitrate = (layer == 2 /* L2 */) ? kBitrateV1L2[bitrate_index - 1] : kBitrateV1L3[bitrate_index - 1];
        } else {
            // V2 (or 2.5)

            bitrate = kBitrateV2[bitrate_index - 1];
        }

        if (out_bitrate) {
            *out_bitrate = bitrate;
        }

        if (version == 3 /* V1 */) {
            //*frame_size = 144000 * bitrate / sampling_rate + padding;
            *frame_size = (sampleperframe*125) * bitrate / sampling_rate + padding;
        } else {
            // V2 or V2.5
            //*frame_size = 72000 * bitrate / sampling_rate + padding;
            *frame_size = (sampleperframe*125) * bitrate / sampling_rate + padding;
        }
    }

    if (out_sampling_rate) {
        *out_sampling_rate = sampling_rate;
    }

    if (out_channels) {
        int channel_mode = (header >> 6) & 3;

        *out_channels = (channel_mode == 3) ? 1 : 2;
    }
    return true;
}

static int mp3HeaderStartAt(const uint8_t *start, unsigned length, unsigned header) {
    uint32_t code = 0;
    unsigned i = 0;

    for(i=0; i<length; i++) {
        code = (code<<8) + start[i];
        if ((code & kMP3HeaderMask) == (header & kMP3HeaderMask)) {
            // some files has no seq start code
            return (int)(i - 3u);
        }
    }
    return -1;
}


struct ASFSource : public MediaSource {
    ASFSource(const sp<ASFExtractor> &extractor, size_t index);
    virtual status_t start(MetaData *params);
    virtual status_t stop();
    virtual sp<MetaData> getFormat();
    virtual status_t read(MediaBuffer **buffer, const ReadOptions *options);
    status_t read_next(MediaBuffer **out, bool newBuffer);
    status_t assembleAVCSizeNalToFrame(MediaBuffer **out);
    status_t assembleMp3Frame(MediaBuffer **out);
    status_t assembleAVCToNal(MediaBuffer **out, bool newBuffer);
    status_t assembleMjpegFrame(MediaBuffer **out);
    status_t findMP3Header(int32_t * header);

private:
    sp<ASFExtractor> mExtractor;
    size_t mTrackIndex;
    AsfStreamType mType;
    bool mIsVideo;
    int32_t mMP3Header;
    bool mIsMP3;
    bool mIsAVC;
    bool mIsMPEG4;
    bool mIsMJPEG;
    bool mWantsNALFragments;
    Mutex mLock;

    uint32_t mStreamId;
    bool mSeeking;
    MediaBuffer *mBuffer;

    ASFSource(const ASFSource &);
    ~ASFSource();
    ASFSource &operator=(const ASFSource &);
};

ASFSource::ASFSource(const sp<ASFExtractor> &extractor, size_t index)
    : mExtractor(extractor),
      mTrackIndex(index),
      mType(ASF_OTHER),
      mIsVideo(true),
      mMP3Header(0),
      mIsMP3(false),
      mIsAVC(false),
      mIsMPEG4(false),
      mIsMJPEG(false),
      mWantsNALFragments(false),
      mStreamId(0),
      mSeeking(false),
      mBuffer(NULL) {
    mStreamId = mExtractor->mTracks.itemAt(index).mTrackNum;

    ALOGV("[ASF]ASFSource::ASFSource stream id =%d\n", mStreamId);

    AsfTrackInfo *trackInfo = &(mExtractor->mTracks.editItemAt(mTrackIndex));

    if (trackInfo->mNextPacket) {
        mExtractor->mAsfParser->asf_packet_destroy(trackInfo->mNextPacket);
        trackInfo->mNextPacket = NULL;
        ALOGV("[ASF]ASFSource::ASFSource stream id =%d, asf_packet_destroy\n", mStreamId);
    }
    ALOGV("[ASF]ASFSource::ASFSource stream id =%d, asf_packet_create\n", mStreamId);

    trackInfo->mNextPacket =  mExtractor->mAsfParser->asf_packet_create();
    /* *///no need
    trackInfo->mCurPayloadIdx=0;

    const char *mime;
    CHECK(mExtractor->mTracks.itemAt(index).mMeta->findCString(kKeyMIMEType, &mime));

    if (!strncasecmp(mime, "video/", 6)) {
        mType = ASF_VIDEO;
    } else if ((!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_WMA))
#ifdef MTK_SWIP_WMAPRO
            || (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_WMAPRO))
#endif
            || (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_RAW))
            || (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_MPEG))
            || (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_MPEG_LAYER_II))
            || (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_AAC))) {
        mType = ASF_AUDIO;
    }

    //MP3
    if ((!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_MPEG)) || (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_MPEG_LAYER_II))) {
        mIsMP3 = true;

        if (findMP3Header(&mMP3Header) != OK) {
            ALOGV("No mp3 header found");
        }
        ALOGV("mMP3Header=0x%d", mMP3Header);
    }
    //AVC
    if (!strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_AVC)) {
        mIsAVC = true;
    }
    //MPEG4
    if (!strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_MPEG4)) {
        mIsMPEG4 = true;
    }

    //MJPEG
    if (!strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_MJPEG)) {
        mIsMJPEG = true;
    }
}

ASFSource::~ASFSource() {
    ALOGV("[ASF]~ASFSource stream id =%d", mStreamId);
    AsfTrackInfo *trackInfo = &(mExtractor->mTracks.editItemAt(mTrackIndex));

    if (trackInfo->mNextPacket) {
        mExtractor->mAsfParser->asf_packet_destroy(trackInfo->mNextPacket);
        trackInfo->mNextPacket = NULL;
    }
    if (trackInfo->mCodecSpecificData!=NULL) {
        ALOGV("~ASFSource:free mCodecSpecificData=0x%p\n", trackInfo->mCodecSpecificData);
        free(trackInfo->mCodecSpecificData);
        trackInfo->mCodecSpecificData = NULL;
    }
}
/*
static int seqStartAt(const uint8_t *start, int length) {
    uint32_t code = (uint32_t)(-1);
    int i = 0;

    for(i=0; i<length; i++) {
        code = (code<<8) + start[i];
        if (code == 0x000001b3 || code == 0x000001b6) {
            // some files has no seq start code
            return i - 3;
        }
    }

    return -1;
}
*/
static int32_t nalStartAt(const uint8_t *start, int32_t length, int32_t *prefixLen) {
    uint32_t code = (uint32_t)(-1);
    int32_t i = 0;

    for(i=0; i<length; i++) {
        code = (code<<8) + start[i];
        if ((code & 0x00ffffff) == 0x1) {
            int32_t fourBytes = code == 0x1;
            *prefixLen = 3 + fourBytes;
            return i - *prefixLen + 1;
        }
    }

    return -1;
}

static int32_t realAVCStart(const uint8_t *start, int32_t length) {
    int32_t i = 0;
    for(i=0; i<length; i++) {
        if((start[i] == 0x00) && (start[i+1] == 0x00) && (start[i+2] == 0x01)) {
            if((start[i+3] & 0x1f)  == 0x07 ) {
                return i;
            }
        }
    }
    return -1;
}

static int32_t realPPSStart(const uint8_t *start, int32_t length) {
    int32_t i = 0;
    for(i=0; i<length; i++) {
        if((start[i] == 0x00) && (start[i+1] == 0x00) && (start[i+2] == 0x01)) {
            if((start[i+3] & 0x1f)  == 0x08 ) {
                return i;
            }
        }
    }
    return -1;
}

//find SOI
static int32_t mjpegStartAt(const uint8_t *start, int32_t length) {
    int32_t i = 0;
    for(i = 0; i < length; i++) {
        if((start[i] == 0xff) && (start[i+1] == 0xd8)) {
            return i;
        }
    }
    return -1;
}
//find EOI
static int32_t mjpegEndAt(const uint8_t *start, int32_t length) {
    int32_t i = 0;
    for (i = 0; i < length; i++) {
        if ((start[i] == 0xff) && (start[i+1] == 0xd9)) {
            return i+1;
        }
    }
    return -1;
}

status_t ASFSource::findMP3Header(int32_t *pHeader) {
    MediaBuffer *packet1 = NULL;
    uint32_t readPackets = 0;
    unsigned header1 = 0;

    *pHeader = 0;
    while (0 == *pHeader) {
        // read data from DataSource
        ASFErrorType retVal = ASF_SUCCESS;
        bool bIsKeyFrame = false;
        bool isSeeking = false;

        // not seeking
        retVal = mExtractor->GetNextMediaFrame(&packet1, bIsKeyFrame, ASF_AUDIO, &isSeeking, mTrackIndex);

        if ((ASF_END_OF_FILE == retVal) && (0 != header1)) {
            ALOGE("[ASF_ERROR]ASFSource::findMP3Header failed, fake header = 0x%u", header1);
            *pHeader = (int32_t)header1;
            mExtractor->ASFSeekTo(0);
            return OK;
        } else if (ASF_SUCCESS != retVal) {
            ALOGE("[ASF_ERROR]ASFSource::findMP3Header no MP3 Header");
            mExtractor->ASFSeekTo(0);
            return ERROR_END_OF_STREAM;
        }

        unsigned length = packet1->range_length(); // buffer data length
        const uint8_t *src = (const uint8_t *)packet1->data() + packet1->range_offset();

        unsigned start = 0;  // the start byte for finding MP3 header
        // find mp3 header
        while (start+ 3 < length) {
            header1 = U32_AT(src + start);
            size_t frame_size;

            if (get_mp3_frame_size(header1, &frame_size, NULL, NULL, NULL)) {
                ALOGV("possible header %u size %zu", header1, frame_size);

                uint8_t tmp[4];
                unsigned j = 0;
                for(; (j + frame_size + start) < length && j < 4; j++) {
                    tmp[j] = src[frame_size + start+ j];
                }

                //handle the end of this buffer
                if (j < 4) {
                    int left = (int)(4 - j);
                    MediaBuffer *packet2 = NULL;

                    ALOGV("[ASF_ERROR]ASFSource::findMP3Header End of this packet(= %d th, left= %d), read next.",
                         readPackets, left);
                    if (ASF_SUCCESS == mExtractor->GetNextMediaFrame(&packet2,
                            bIsKeyFrame, ASF_AUDIO, &isSeeking, mTrackIndex)) {
                        const uint8_t *src1 = (const uint8_t *)packet2->data() + packet2->range_offset();

                        if (packet2->range_length() < (size_t)left) {
                            ALOGV("ASF The packet(= %zu < left= %d)is too small to check MP3 Header.",
                                    packet2->range_length(), left);
                            packet2->release();
                            packet2 = NULL;
                            break;
                        }

                        for (int i = 0 ; i < left && j < 4 ; i++) {
                            tmp[j] = src1[i];
                            ++j;
                        }
                    } else {
                        ALOGV("[ASF_ERROR]ASFSource::findMP3Header End of stream,fake header = 0x%u", header1);
                        *pHeader = (int32_t)header1;
                        mExtractor->ASFSeekTo(0);
                        packet1->release();
                        packet1 = NULL;
                        return OK;
                    }
                    packet2->release();
                    packet2 = NULL;
                }

                //check mp3 header by header2(the beginning 4 bytes of next frame)
                unsigned header2 = U32_AT(tmp);
                ALOGV("possible header %u size %zu, test %x", header1, frame_size, header2);
                if ((header2 & kMP3HeaderMask) == (header1 & kMP3HeaderMask)) {
                    *pHeader = (int32_t)header1;
                    mExtractor->ASFSeekTo(0);
                    packet1->release();
                    packet1 = NULL;
                    return OK;
                }
            }

            ++start;
        }

        packet1->release();
        packet1 = NULL;
        ++readPackets;
    }

    mExtractor->ASFSeekTo(0);
    return OK;
}

status_t ASFSource::start(MetaData *params) {
    Mutex::Autolock autoLock(mLock);
    ALOGV("[ASF]ASFSource::start stream id =%d", mStreamId);

    int32_t val;
    if (params && params->findInt32(kKeyWantsNALFragments, &val) && val != 0) {
        mWantsNALFragments = true;
        ALOGV("[ASF]ASFSource::start mWantsNALFragments = true");
    } else {
        mWantsNALFragments = false;
        ALOGV("[ASF]ASFSource::start mWantsNALFragments = false");
    }


    if(mIsVideo==true) {
        mExtractor->mHasVideo= true;
        mExtractor->mHasVideoTrack = true;
        ALOGV("[ASF]ASFSource::mHasVideo=true");
    }

    return OK;
}

status_t ASFSource::stop() {
    ALOGV("[ASF]ASFSource::stop stream id =%d", mStreamId);
    Mutex::Autolock autoLock(mLock);

    if(mIsVideo==true) {
        ALOGV("WMV video file, stop video track!");
        mExtractor->mHasVideo= false;
        mExtractor->mHasVideoTrack = false;
        ALOGV("[ASF]ASFSource::mHasVideo=false");
    }

    if (mBuffer != NULL) {
        mBuffer->release();
        mBuffer = NULL;
    }

    return OK;
}

sp<MetaData> ASFSource::getFormat() {
    //ALOGE("[ASF]ASFSource::getFormat stream id =%d", mStreamId);
    sp<MetaData> meta;
    meta = mExtractor->mTracks.itemAt(mTrackIndex).mMeta;
    const char* mime;
    CHECK(meta->findCString(kKeyMIMEType, &mime));

    if (!strncasecmp("audio/", mime, 6)) {
        mIsVideo = false;
    } else {
        CHECK(!strncasecmp("video/", mime, 6));
        mIsVideo =true;
    }

    return meta;
}

//if only audio file, seek will error
status_t ASFSource::read(MediaBuffer **out, const ReadOptions *options) {
    ALOGV("ASFSource::read, mType %d", mType);
    android::Mutex::Autolock autoLock(mExtractor->mCacheLock);
    //avoid read by different instance read at the same time?

    ASFErrorType retVal = ASF_SUCCESS;
    int64_t seekTimeUs, keyTimeUs, seekTimeUsfake;
    ReadOptions::SeekMode mode;
    bool bIsKeyFrame = false;
    bool bAdjustSeekTime = false;
    bool bNeedResetSeekTime = false;
    bool newBuffer = false;
    *out = NULL;

    // path 1: seek happen
    if (options && options->getSeekTo(&seekTimeUs, &mode)) {  // seek and find an I
        ALOGV("ASFSource::read() seek to %lld us (mType %d, mode %d, mHasVideo %d)",
             (long long)seekTimeUs, mType, mode, mExtractor->mHasVideo);
        mSeeking = true;

        //just for file-with-video-track, to seek accurately
        /*
        if(seekTimeUs==0) { //if seek to 0, not need to speed up
            seekTimeUsfake = seekTimeUs;
            bAdjustSeekTime = false;
        } else {  //for speed up for finding the closet frame
            seekTimeUsfake =seekTimeUs + mExtractor->mPrerollTimeUs;
            bAdjustSeekTime = true;
        }
        */
        seekTimeUsfake = seekTimeUs;
        uint32_t repeat_cnt = 0;
        int64_t diff = 0;
        int64_t lastSeekTime = -1;
        int64_t lastDiff = 0;
        //in repeat-reset-seek-time period, when eosTimes=2, it may be filesource::read() error, then we will return ASF_END_OF_FILE.
        uint32_t eosTimes = 0;

RESET_SEEK_TIME:
        if (bNeedResetSeekTime) {
            if (bAdjustSeekTime) {
                seekTimeUsfake = seekTimeUsfake - (int64_t) mExtractor->mPrerollTimeUs; //subtrack preroll time
                bAdjustSeekTime = false;
                ALOGV("ASFSource::read seek: RESET_SEEK_TIME once mPrerollTimeUs to %lld us", (long long)seekTimeUsfake);
            } else {
                if((diff > 1000000ll) || (diff < -1000000ll)) {
                    seekTimeUsfake = seekTimeUsfake -(diff/2);
                }
                else {
                    if (diff > 0) {
                        seekTimeUsfake = seekTimeUsfake - 1000000ll;
                    } else {
                        seekTimeUsfake = seekTimeUsfake + 1000000ll;
                    }
                }
                ALOGV("ASFSource::read seek: RESET_SEEK_TIME once again to %lld us", (long long)seekTimeUsfake);
            }

            if (seekTimeUsfake < 0) {
                seekTimeUsfake = 0;
            }
            bNeedResetSeekTime = false;//reset
        }
        ALOGV("ASFSource::read seek fake to %lld us", (long long)seekTimeUsfake);

        // path 1.1: file contains video seek
        if (mExtractor->mHasVideo) {  // has video file
            if (mType == ASF_VIDEO) {
                int64_t ret = mExtractor->ASFSeekTo(seekTimeUsfake/1000);
                if (ret < 0) {
                    ALOGE("Seek error");
                    // When seek error, if lastSeek valid, then seek to lastSeekTime, otherwise return EOS
                    if (lastSeekTime != -1) {
                        mExtractor->ASFSeekTo(lastSeekTime);
                        repeat_cnt = 10;
                        ALOGD("Seek to lastSeekTime %lld", (long long)lastSeekTime);
                    } else {
                        ALOGE("ASFSource::read() return EOS when seek error");
                        return ERROR_END_OF_STREAM;
                    }
                }

                bIsKeyFrame = false;
                while (!bIsKeyFrame) {
                    retVal = mExtractor->GetNextMediaFrame(out, bIsKeyFrame, mType, &mSeeking, mTrackIndex);
                    ALOGV("GetNextMediaFrame when seek, return %d", retVal);
                    if ((ASF_END_OF_FILE == retVal) || (ASF_ERROR_MALFORMED == retVal)) {
                        ALOGV("[ASF_ERROR]ASFSource::read EOS reached in seek (stream id = %d)", mStreamId);
                        eosTimes++;
                        if (lastSeekTime != -1 && eosTimes < 2) {
                            mExtractor->ASFSeekTo(lastSeekTime);
                            //seet to the time, and set postion in the packet for a/v stream
                            if (*out != NULL) {
                                (*out)->release();
                                (*out) = NULL;
                            }
                            mSeeking = true;
                            bIsKeyFrame = false;
                            repeat_cnt = 10;
                            ALOGV("seek to last seek Time %lld when ASF_END_OF_FILE, eosTimes=%u", (long long)lastSeekTime, eosTimes);
                        } else {
                            ALOGV("return EOS, eosTimes=%u", eosTimes);
                            if (ASF_ERROR_MALFORMED == retVal) return ERROR_MALFORMED;
                            else return ERROR_END_OF_STREAM;
                        }
                    } else if (ASF_FILE_READ_ERR == retVal) {  // not a frame data
                        (*out)->meta_data()->findInt64(kKeyTime, &keyTimeUs);
                        ALOGV("[ASF_ERROR] ASF_FILE_READ_ERR: ASF Video Seek Drop %lld ms", (long long)(keyTimeUs/1000));
                        (*out)->release();
                        (*out) = NULL;
                        bIsKeyFrame=false;
                        mSeeking=true;
                    } else {
                        (*out)->meta_data()->findInt64(kKeyTime, &keyTimeUs);
                        diff = keyTimeUs-seekTimeUs;
                        if (repeat_cnt < 10) {
                            if (((mode == ReadOptions::SEEK_PREVIOUS_SYNC && (diff > 0 || diff < -500000))) ||
                                    (mode != ReadOptions::SEEK_PREVIOUS_SYNC && (diff > 500000 || diff < -500000))) {
                                if (mode == ReadOptions::SEEK_PREVIOUS_SYNC) {
                                    if ((lastDiff > 0 && diff < 0)) {
                                        ALOGV("I got the best seek time,return now");
                                        break;
                                    } else if (lastDiff < 0 && diff > 0) {
                                         // should seek to last seek time
                                        mExtractor->ASFSeekTo(lastSeekTime);
                                        //seet to the time, and set postion in the packet for a/v stream
                                        (*out)->release();  //seek  to 100ms, is not I ,will seek forward, bot nearest I befroe???
                                        (*out) = NULL;
                                        mSeeking = true;
                                        bIsKeyFrame = false;
                                        repeat_cnt = 10;
                                        ALOGV("seek to last seek Time %lld, lastDiff %lld, diff %lld",
                                                (long long)lastSeekTime, (long long)lastDiff, (long long)diff);
                                        continue;
                                    }
                                }
                                lastSeekTime = seekTimeUsfake/1000;
                                (*out)->release();  //seek  to 100ms, is not I ,will seek forward, bot nearest I befroe???
                                (*out) = NULL;
                                bIsKeyFrame=false;
                                mSeeking=true;
                                bNeedResetSeekTime = true;
                                repeat_cnt++;
                                ALOGV(
                                        "ASFSource::read seek bNeedResetSeekTime,diff=keyTimeUs-seekTimeUs=%lld-%lld=%lld ms,repeat_cnt=%d",
                                        (long long)(keyTimeUs / 1000), (long long)(seekTimeUs / 1000),
                                        (long long)(keyTimeUs / 1000 - seekTimeUs / 1000), repeat_cnt);
                                lastDiff = diff;
                                goto RESET_SEEK_TIME;
                            }
                        } else if(!bIsKeyFrame) {
                            ALOGV("ASFSource::read seek ASF Video Seek Drop non-I ts = %lld ms", (long long)(keyTimeUs/1000));
                            (*out)->release();  //seek  to 100ms, is not I ,will seek forward, bot nearest I befroe???
                            (*out) = NULL;
                        }
                        repeat_cnt=0;
                    }
                }
                {
                    (*out)->meta_data()->findInt64(kKeyTime, &keyTimeUs);
                    (*out)->meta_data()->setInt64(kKeyTargetTime, seekTimeUs);
                }
                ALOGV("ASFSource::read seek ASF Video Seek done: KeyTime is %lld ms, seekTimeUs %lld ms",
                     (long long)(keyTimeUs / 1000), (long long)(seekTimeUs / 1000));//debug

                // if video = AVC or MJPEG, which need to be fragmented, copy (*out) to mBuffer
                if ((mIsAVC && mWantsNALFragments) || mIsMJPEG || (mIsAVC && (!mWantsNALFragments) &&
                        (ASF_SIZE_NAL_TYPE == mExtractor->getNALParserType()))) {
                    //*out = mBuffer;
                    int64_t keyTime=0;
                    int32_t keyIsSyncFrame;
                    if (mBuffer != NULL) {
                        mBuffer->release();
                        mBuffer = NULL;
                    }

                    if ((*out)!= NULL) {
                        newBuffer = true;

                        // clone *out buffer to mBuffer
                        mBuffer = new MediaBuffer((*out)->size());
                        (*out)->meta_data()->findInt64(kKeyTime, &keyTime);
                        (*out)->meta_data()->findInt32(kKeyIsSyncFrame, &keyIsSyncFrame);
                        mBuffer->meta_data()->setInt64(kKeyTime, keyTime);
                        mBuffer->meta_data()->setInt32(kKeyIsSyncFrame, keyIsSyncFrame);
                        uint8_t *data = (uint8_t *)mBuffer->data();
                        memcpy(data, (*out)->data(), (*out)->size());
                        CHECK(mBuffer != NULL);
                        mBuffer->set_range((*out)->range_offset(), (*out)->range_length());

                        (*out)->release();
                        (*out) = NULL;
                    } else {
                        ALOGV("[ASFERROR]ASFSource::read seek Video, *out == NULL, AVC/MJPEG get no media frame ");
                    }
                }
            }

            if (mType == ASF_AUDIO) {
                while (1) {
                    retVal = mExtractor->GetNextMediaFrame(out, bIsKeyFrame, mType, &mSeeking, mTrackIndex);
                    if (ASF_SUCCESS != retVal) {
                        ALOGV("[ASF_ERROR]ASFSource::read EOS reached in seek (stream id = %d)", mStreamId);
                        if (ASF_ERROR_MALFORMED == retVal) return ERROR_MALFORMED;
                        else return ERROR_END_OF_STREAM;
                    }
                    (*out)->meta_data()->findInt64(kKeyTime, &keyTimeUs);

                    if (seekTimeUs - keyTimeUs > 300000) {
                        ALOGV("ASF Audio Seek Drop audio2 ts = %lld", (long long)(keyTimeUs/1000));
                        (*out)->release();
                        (*out) = NULL;
                        continue;
                    } else {
                        break;
                    }
                }
                ALOGV("ASF Audio Seek done: KeyTimeMs %lld", (long long)(keyTimeUs/1000));

                if (mIsMP3) {
                    int64_t keyTime=0;
                    int32_t keyIsSyncFrame;
                    if ((*out)!= NULL) {
                        newBuffer = true;

                        // clone *out buffer to mBuffer
                        mBuffer = new MediaBuffer((*out)->size());
                        (*out)->meta_data()->findInt64(kKeyTime, &keyTime);
                        (*out)->meta_data()->findInt32(kKeyIsSyncFrame, &keyIsSyncFrame);
                        mBuffer->meta_data()->setInt64(kKeyTime, keyTime);
                        mBuffer->meta_data()->setInt32(kKeyIsSyncFrame, keyIsSyncFrame);
                        uint8_t *data = (uint8_t *)mBuffer->data();
                        memcpy(data, (*out)->data(), (*out)->size());
                        CHECK(mBuffer != NULL);
                        mBuffer->set_range((*out)->range_offset(), (*out)->range_length());

                        (*out)->release();
                        (*out) = NULL;
                    } else {
                        ALOGE("[ASF_ERROR]ASFSource::read seek ASF Video,*out == NULL, MP3 get no media frame ");//debug
                    }
                }
            }
        }/*has video file*/ else { //1.2 path: pure audio file
            if (mType == ASF_AUDIO) {
                ALOGV("ASF pure audio file, seekTimeUsfake %lld ms", (long long)(seekTimeUsfake/1000));
                mExtractor->ASFSeekTo(seekTimeUsfake/1000);

                while (1) {
                    //in audio path, mSeeking and bIsKeyFrame are not used,
                    //just use payload count and index to find the audio frame
                    retVal = mExtractor->GetNextMediaFrame(out, bIsKeyFrame,mType,&mSeeking,mTrackIndex);
                    if (ASF_SUCCESS != retVal) {
                        ALOGV("[ASF_ERROR]ASFSource::read EOS reached in seek (stream id = %d)", mStreamId);
                        if (ASF_ERROR_MALFORMED == retVal) return ERROR_MALFORMED;
                        else return ERROR_END_OF_STREAM;
                    }
                    (*out)->meta_data()->findInt64(kKeyTime, &keyTimeUs);

                    diff = keyTimeUs-seekTimeUs;
                    if( diff > 500000 && repeat_cnt < 50) {
                        (*out)->release();
                        (*out) = NULL;
                        bNeedResetSeekTime = true;
                        repeat_cnt++;
                        ALOGV(
                             "ASFSource::Audio read seek bNeedResetSeekTime keyTimeUs=%lldms > seekTimeUs=%lldms repeat_cnt=%d",
                             (long long)(keyTimeUs / 1000), (long long)(seekTimeUs / 10000), repeat_cnt);
                        goto RESET_SEEK_TIME;
                    } else if (diff < -500000) {
                        ALOGV("ASF Audio Seek Drop audio2 ts = %lld", (long long)(keyTimeUs/1000));
                        (*out)->release();
                        (*out) = NULL;
                        continue;
                    } else {
                        break;
                    }
                }

                if (mIsMP3) {
                    int64_t keyTime=0;
                    int32_t keyIsSyncFrame;
                    if ((*out)!= NULL) {
                        newBuffer = true;

                        // clone *out buffer to mBuffer
                        mBuffer = new MediaBuffer((*out)->size());
                        (*out)->meta_data()->findInt64(kKeyTime, &keyTime);
                        (*out)->meta_data()->findInt32(kKeyIsSyncFrame, &keyIsSyncFrame);
                        mBuffer->meta_data()->setInt64(kKeyTime, keyTime);
                        mBuffer->meta_data()->setInt32(kKeyIsSyncFrame, keyIsSyncFrame);
                        uint8_t *data = (uint8_t *)mBuffer->data();
                        memcpy(data, (*out)->data(), (*out)->size());
                        CHECK(mBuffer != NULL);
                        mBuffer->set_range((*out)->range_offset(), (*out)->range_length());

                        (*out)->release();
                        (*out) = NULL;
                    } else {
                        ALOGE("[ASF_ERROR]ASFSource::read seek ASF Video, *out == NULL, MP3 get no media frame");
                    }
                }
                ALOGV("ASF Audio Seek done: KeyTimeMs %lld", (long long)(keyTimeUs/1000));
            }
        }//pure audio file seek end
    }/*seek the 1th frame done */ else { // 2 path : normal play, enter here
        if ((mIsAVC && (ASF_SIZE_NAL_TYPE == mExtractor->getNALParserType()) && (!mWantsNALFragments))
                || mIsMP3 || (mIsAVC && mWantsNALFragments) || mIsMJPEG) {
        // if mBuffer is null, then read frame to mBuffer;
            if (mBuffer == NULL) {
                ALOGV("ASFSource::read() mBuffer == NULL, acquire buffer");
                newBuffer = true;

                retVal = mExtractor->GetNextMediaFrame(&mBuffer, bIsKeyFrame,mType,&mSeeking,mTrackIndex);
                if (ASF_SUCCESS != retVal) {
                    ALOGV("[ASF_ERROR]ASFSource::read EOS reached 1(stream id = %d)", mStreamId);
                    if (ASF_ERROR_MALFORMED == retVal) return ERROR_MALFORMED;
                    else return ERROR_END_OF_STREAM;
                }
            }
        } else { // read data to out,and return
            retVal = mExtractor->GetNextMediaFrame(out, bIsKeyFrame,mType,&mSeeking,mTrackIndex);
            if (ASF_ERROR_MALFORMED == retVal) return ERROR_MALFORMED;
            else if (retVal != ASF_SUCCESS) return ERROR_END_OF_STREAM;
        }
    }

    // read_next():assemble or split frame
    return read_next(out,newBuffer);
}

status_t ASFSource::read_next(MediaBuffer **out, bool newBuffer) {
    ALOGV("ASFSource::read_next IN\n");
    if (mIsAVC && (ASF_SIZE_NAL_TYPE == mExtractor->getNALParserType()) && (!mWantsNALFragments)) {
        return assembleAVCSizeNalToFrame(out);
    }
    if (mIsMP3) {
        return assembleMp3Frame(out);
    }
    if (mIsAVC && mWantsNALFragments) {
        return assembleAVCToNal(out,newBuffer);
    }
    if (mIsMJPEG) {
        return assembleMjpegFrame(out);
    }
    ALOGV("ASFSource::read_next OUT\n");
    return OK;
}

// reassemble mBuffer into a frame which can be sended to OMXCodec
// make the mBuffer's Nals are splited by startcode
status_t ASFSource::assembleAVCSizeNalToFrame(MediaBuffer **out) {
    //bool bIsKeyFrame = false;
    uint32_t nalSizeLength = 0;
    //uint32_t nextNALLength = 0;
    uint32_t nalLength = 0;
    //int32_t length = 0;
    //int32_t start = 0;
    int64_t keyTime = 0;
    int32_t keyIsSyncFrame = 0;
    //int i = 0;
    ALOGV("ASFSource::read()Video Type = AVC,mWantsNALFragments=false,ASF_SIZE_NAL_TYPE ");

    MediaBuffer *tempBuffer = new MediaBuffer(mBuffer->size());
    while(mBuffer->range_length() != 0 ) {
        uint8_t startcode[3]={0,0,1};
        uint8_t *data = NULL;

        nalSizeLength = mExtractor->getNALSizeLength();
        nalLength = mExtractor->parseNALSize((uint8_t*)mBuffer->data() + mBuffer->range_offset());
        if (0 == nalLength) {
            *out = mBuffer;
            mBuffer->release();
            mBuffer = NULL;
            return ERROR_END_OF_STREAM;
        }

        CHECK(mBuffer != NULL);
        mBuffer->set_range(mBuffer->range_offset() + nalSizeLength,
                mBuffer->size() - mBuffer->range_offset() - nalSizeLength);

        MediaBuffer *clone = new MediaBuffer(3 + nalLength);
        data = (uint8_t *)clone->data();
        memcpy(data,startcode, 3);
        memcpy(data + 3, (char *)mBuffer->data() + mBuffer->range_offset(), nalLength);
        memcpy((char *)tempBuffer->data() + tempBuffer->range_offset(),data, 3 + nalLength);
        tempBuffer->set_range(tempBuffer->range_offset() +nalLength + 3,
                tempBuffer->size() - tempBuffer->range_offset() - nalLength - nalSizeLength);

        CHECK(mBuffer != NULL);
        mBuffer->set_range(mBuffer->range_offset() +nalLength,
                mBuffer->size() - mBuffer->range_offset() - nalLength );
        clone->release();
        clone = NULL;
        data = NULL;
    }
    if (mBuffer->range_length() == 0) {
        mBuffer->meta_data()->findInt64(kKeyTime, &keyTime);
        mBuffer->meta_data()->findInt32(kKeyIsSyncFrame, &keyIsSyncFrame);
        MediaBuffer *reBuffer = new MediaBuffer(tempBuffer->range_offset());
        tempBuffer->set_range(0,tempBuffer->range_offset());
        memcpy((char *)reBuffer->data() + reBuffer->range_offset(),
                (char *)tempBuffer->data() + tempBuffer->range_offset(),tempBuffer->range_length());
        reBuffer->meta_data()->setInt64(kKeyTime, keyTime);
        reBuffer->meta_data()->setInt32(kKeyIsSyncFrame, keyIsSyncFrame);
        *out = reBuffer;
        mBuffer->release();
        tempBuffer->release();
        mBuffer = NULL;
    }
    return OK;
}

// reassemble mBuffer to 1 MP3 Frame
status_t ASFSource::assembleMp3Frame(MediaBuffer **out) {
    ALOGV("ASFSource::read() Audio Type = MP3, reassemble mBuffer to 1 MP3 frame");
    ASFErrorType retVal = ASF_SUCCESS;
    bool bIsKeyFrame = false;
    // for ALPS00962015(there's noice when playing MP3),
    // to avoid a case that a MP3Header was divided in 2 packets
    // ex. 0xFF in n-th packet, and 0xFB 52 00 is in (n+1)-th packet =>
    // you cannot find 0xFFFB5200 of MP3Header
    // clone mBuffer to tmp and release mBuffer; read one more buffer to nextBuffer;
    // renew mBuffer to store tmp and nextBuffer.
    if((mBuffer != NULL) && (mBuffer->range_length() < 4)) {
        ALOGV("ASFSource::read() mBuffer size(%zu) < 4 bytes, appending buffer for detecting MP3 header",
             mBuffer->range_length());

        MediaBuffer *tmp = new MediaBuffer(mBuffer->range_length());
        //MediaBuffer *tmp = NULL;
        MediaBuffer *nextBuffer = NULL;
        //get 1 ASF frame
        retVal = mExtractor->GetNextMediaFrame(&nextBuffer, bIsKeyFrame,mType,&mSeeking,mTrackIndex);
        if (ASF_SUCCESS != retVal) {
            ALOGV("[ASF_ERROR]ASFSource::read EOS reached 1(stream id = %d)", mStreamId);
            if (ASF_ERROR_MALFORMED == retVal) return ERROR_MALFORMED;
            else return ERROR_END_OF_STREAM;
        }

        //copy remaining data of mBuffer to tmp
        memcpy((char *)tmp->data(), (char *)mBuffer->data()+mBuffer->range_offset(),mBuffer->range_length());

        //empty mBuffer
        if (mBuffer != NULL) {
            mBuffer->release();
            mBuffer = NULL;
        }

        //reallocate mbuffer
        int64_t keyTime=0;
        int32_t keyIsSyncFrame;
        mBuffer = new MediaBuffer(nextBuffer->size()+tmp->size());
        nextBuffer->meta_data()->findInt64(kKeyTime, &keyTime);
        nextBuffer->meta_data()->findInt32(kKeyIsSyncFrame, &keyIsSyncFrame);
        mBuffer->meta_data()->setInt64(kKeyTime, keyTime);
        mBuffer->meta_data()->setInt32(kKeyIsSyncFrame, keyIsSyncFrame);
        uint8_t *data = (uint8_t *)mBuffer->data();
        memcpy(data, (uint8_t*)tmp->data(), tmp->size());
        memcpy(data+tmp->size(), (uint8_t*)nextBuffer->data() + nextBuffer->range_offset(),
                nextBuffer->range_length());

        CHECK(mBuffer != NULL);
        mBuffer->set_range(0, nextBuffer->range_length()+tmp->size());
        ALOGV(
             "ASFSource::read() new mBuffer size(%zu), including original mBuffer size(%zu) + new packet size(%zu)",
             mBuffer->range_length(), tmp->range_length(), nextBuffer->range_length());

        //release all temporal buffer
        nextBuffer->release();
        nextBuffer = NULL;

        tmp->release();
        tmp = NULL;
    }

    int start=0;
    uint32_t header=0;
    size_t frameSize;

    // MP3 frame header start with 0xff
    if (mMP3Header >= 0) {
        ALOGV("[ASF_Read]mMP3Header=0x%u", mMP3Header);
    }

    unsigned length = mBuffer->range_length(); // buffer data length
    const uint8_t *src = (const uint8_t *)mBuffer->data() + mBuffer->range_offset();

    // find the position of MP3 header(every mp3 frame start with MP3 header )
    start = mp3HeaderStartAt(src, length, (unsigned)mMP3Header);

    if (start >= 0) header = U32_AT(src + start);

    unsigned bitrate;
    bool ret= false;
    ret = get_mp3_frame_size(header, (size_t*)&frameSize, NULL, NULL, &bitrate);
    ALOGV("[ASF_Read]mp3 start %d header %x frameSize %zu length %d bitrate %d",
         start, header, frameSize, length, bitrate);

    if (start >= 0 && ret) {
        //framesize > buffer length, need to get next ASF frame
        if (frameSize + (unsigned)start > length) {
            ALOGV("[ASF_Read]MP3 frameSize(%zu) + start(%d) > length(%d)", frameSize, start, length);
            MediaBuffer *tmp = new MediaBuffer(frameSize);
            //MediaBuffer *tmp = NULL;
            MediaBuffer *nextBuffer = NULL;
            unsigned needSizeOrg = frameSize + (unsigned)start - length;
            unsigned needSize = needSizeOrg;
            unsigned existSizeOrg = length - (unsigned)start;
            unsigned existSize = existSizeOrg;
            status_t readret =  OK;
            //ssize_t bytesRea = 0;
            unsigned remainSize = 0;

            // handle the data which exceeds mBuffer->range_length()
            while(readret == OK) {
                if (nextBuffer != NULL) {
                    nextBuffer->release();
                    nextBuffer = NULL;
                }

                //get 1 next ASF media frame
                readret = mExtractor->GetNextMediaFrame(&nextBuffer, bIsKeyFrame,mType,&mSeeking,mTrackIndex);
                if (ASF_SUCCESS != readret) {
                    ALOGV("[ASF_ERROR]ASFSource::read EOS reached(stream id = %d) when read next MP3 frame", mStreamId);
                    tmp->release();
                    tmp = NULL;
                    if (ASF_ERROR_MALFORMED == readret) return ERROR_MALFORMED;
                    else return ERROR_END_OF_STREAM;
                }

                //copy the needSize from next ASF media frame
                if (needSize >= nextBuffer->range_length()) {
                    memcpy((uint8_t*)tmp->data()+ existSize,
                            (uint8_t*)nextBuffer->data() + nextBuffer->range_offset(), nextBuffer->range_length());
                    needSize -= nextBuffer->range_length();
                    existSize += nextBuffer->range_length();
                    ALOGV("[ASF_Read]next asf frame size(%zu) <= need Size(%d)", nextBuffer->range_length(), needSize);
                } else {
                    memcpy((uint8_t*)tmp->data()+ existSize,
                            (uint8_t*)nextBuffer->data() + nextBuffer->range_offset(), needSize);
                    remainSize = nextBuffer->range_length() - needSize;
                    ALOGV("[ASF_Read]in next ASF frame, remainSize = %d", remainSize);
                    break;
                }

                if( existSize >= frameSize) {
                    ALOGV("[ASF_Read]have enough frame size (existSize)=%d", existSize);
                    break;
                }
            }
            if(readret == OK) {
                int64_t keyTime=0;
                int32_t keyIsSyncFrame;

                memcpy(tmp->data(),
                        (uint8_t*)mBuffer->data() + mBuffer->range_offset() + start, existSizeOrg);
                mBuffer->meta_data()->findInt64(kKeyTime, &keyTime);
                tmp->meta_data()->clear();
                tmp->meta_data()->setInt64(kKeyTime, keyTime);
                tmp->set_range(0, frameSize);
                tmp->meta_data()->setInt32(kKeyIsSyncFrame, 1);
                *out = tmp;

                //copy all the data to tmp buffer, clear mBuffer
                if(mBuffer != NULL) {
                    mBuffer->release();
                    mBuffer = NULL;
                }

                // clone the remain "next ASF frame"(*out) to mBuffer,
                // clone size = nextbuffer->range_length() -needSize
                if (nextBuffer!= NULL) {
                    mBuffer = new MediaBuffer(remainSize);
                    nextBuffer->meta_data()->findInt64(kKeyTime, &keyTime);
                    nextBuffer->meta_data()->findInt32(kKeyIsSyncFrame, &keyIsSyncFrame);
                    mBuffer->meta_data()->setInt64(kKeyTime, keyTime);
                    mBuffer->meta_data()->setInt32(kKeyIsSyncFrame, keyIsSyncFrame);
                    uint8_t *data = (uint8_t *)mBuffer->data();
                    memcpy(data, (char *)nextBuffer->data()+ needSize, remainSize);
                    CHECK(mBuffer != NULL);
                    mBuffer->set_range(0, remainSize);

                    nextBuffer->release();
                    nextBuffer = NULL;

                    ALOGV("[ASFSource]ASFSource::read MP3 next buffer remain Size= %d.", remainSize);
                } else {
                    ALOGV("[ASFSource]ASFSource::read MP3 next buffer no remain Size.");
                    if (nextBuffer != NULL) {
                        nextBuffer->release();
                        nextBuffer = NULL;
                    }
                }
                return OK;
            } else {
                ALOGE("readNextASFframe return 0x%u", readret);
                tmp->release();
                tmp = NULL;
                nextBuffer->release();
                nextBuffer = NULL;

                if(mBuffer != NULL) {
                    mBuffer->release();
                    mBuffer = NULL;
                }

                return readret;
            }
            tmp->release();
            nextBuffer->release();
        } else {
            ALOGV("MP3 frameSize + MP3 header position < buffer length");
        }
    } else {
        ALOGV("bad MP3 frame without header, all remain bytes %d", length);

        if ( length >= 4) {     //to create a header for ASF frame without MP3 header
            char *p = (char*)mBuffer->data() + mBuffer->range_offset();
            char *q = (char*)&mMP3Header;

            p[0] = q[3];
            p[1] = q[2];
            p[2] = q[1];
            p[3] = q[0];

            for (unsigned i = 4; i < 16 && i < length; i++) {
                p[i] = 0;
            }

            frameSize = length; //send all mBuffer to out
            ALOGE("fake MP3 header = 0x%x", mMP3Header);
        } else {
            frameSize = length;
            ALOGV("[ASF_Error] Read MP3 frame, mBuffer length( = %d) < 4", length);
        }
        start = 0;
    }

    //clone modified data to out
    MediaBuffer *clone = new MediaBuffer(mBuffer->size());
    int64_t keyTime=0;
    int32_t keyIsSyncFrame;
    mBuffer->meta_data()->findInt64(kKeyTime, &keyTime);
    mBuffer->meta_data()->findInt32(kKeyIsSyncFrame, &keyIsSyncFrame);
    clone->meta_data()->setInt64(kKeyTime, keyTime);
    clone->meta_data()->setInt32(kKeyIsSyncFrame, keyIsSyncFrame);
    uint8_t *data = (uint8_t *)clone->data();
    memcpy(data, mBuffer->data(), mBuffer->size());
    CHECK(clone != NULL);
    clone->set_range(mBuffer->range_offset() + (unsigned)start, frameSize);

    CHECK(mBuffer != NULL);
    mBuffer->set_range(mBuffer->range_offset() + frameSize + (unsigned)start,
            mBuffer->range_length() - frameSize - (unsigned)start);

    if (mBuffer->range_length() == 0) {
        ALOGV("ASFSource::read() mBuffer->range_length = 0 ");
        mBuffer->release();
        mBuffer = NULL;
    }
    *out = clone;
    return OK;
}

// divide ASF media frame into NAL fragments
status_t ASFSource::assembleAVCToNal(MediaBuffer **out, bool newBuffer) {
    //bool bIsKeyFrame = false;
    uint32_t nalSizeLength = 0;
    //uint32_t nextNALLength = 0;
    uint32_t nalLength = 0;
    int32_t length = 0;
    int32_t start = 0;
    int64_t keyTime = 0;
    int32_t keyIsSyncFrame = 0;

    ALOGV("ASFSource::read() Video Type = AVC, reassemble buffer to 1 NAL unit");
    if (ASF_SIZE_NAL_TYPE == mExtractor->getNALParserType()) {
        uint8_t startcode[3]={0,0,1};
        nalSizeLength = mExtractor->getNALSizeLength();
        nalLength = mExtractor->parseNALSize((uint8_t*)mBuffer->data() + mBuffer->range_offset());
        if (0 == nalLength) {
            *out = mBuffer;
            mBuffer->release();
            mBuffer = NULL;
            return ERROR_END_OF_STREAM;
        }
        CHECK(mBuffer != NULL);
        mBuffer->set_range(mBuffer->range_offset() + nalSizeLength,
                mBuffer->size() - mBuffer->range_offset() - nalSizeLength);
        MediaBuffer *clone = new MediaBuffer(mBuffer->size());
        mBuffer->meta_data()->findInt64(kKeyTime, &keyTime);
        mBuffer->meta_data()->findInt32(kKeyIsSyncFrame, &keyIsSyncFrame);
        clone->meta_data()->setInt64(kKeyTime, keyTime);
        clone->meta_data()->setInt32(kKeyIsSyncFrame, keyIsSyncFrame);
        uint8_t *data = (uint8_t *)clone->data();
        memcpy(data,startcode, 3);
        memcpy(data + 3, (char *)mBuffer->data() + mBuffer->range_offset(), nalLength);
        CHECK(clone != NULL);
        clone->set_range(0, nalLength + 3);
        *out = clone;
        CHECK(mBuffer != NULL);
        mBuffer->set_range(mBuffer->range_offset() +nalLength,
                mBuffer->size() - mBuffer->range_offset() - nalLength );

        if (mBuffer->range_length() == 0) {
            ALOGV("ASFSource::read() mBuffer->range_length = 0 ");
            mBuffer->release();
            mBuffer = NULL;
        }
    } else if (ASF_START_CODE_TYPE == mExtractor->getNALParserType()) {
    // Each NAL unit is split up into its constituent fragments and
    // each one of them returned in its own buffer.
        if (newBuffer) {
            start = nalStartAt((uint8_t*)mBuffer->data(), (int32_t)mBuffer->size(), &length);
            ALOGV("ASFSource::read() check newBuffer NAL Start position = %d", start);
            if (start == -1) {
                // not a byte-stream
                *out = mBuffer;
                mBuffer->release();
                mBuffer = NULL;
                //return OK;
                return ERROR_END_OF_STREAM;
            }
            mBuffer->set_range(mBuffer->range_offset() + (unsigned)length,
                    mBuffer->range_length() - (unsigned)length);
        }

        const uint8_t *src = (const uint8_t *)mBuffer->data() + mBuffer->range_offset();
        start = nalStartAt(src, (int32_t)mBuffer->range_length(), &length);
        if (start == -1) {
            ALOGV("ASFSource::read() check NAL Start position = %d", start);
            start = (int32_t)mBuffer->range_length();
            length = 0;
        }
        //clone modified data to out
        MediaBuffer *clone = new MediaBuffer(mBuffer->size());
        mBuffer->meta_data()->findInt64(kKeyTime, &keyTime);
        mBuffer->meta_data()->findInt32(kKeyIsSyncFrame, &keyIsSyncFrame);
        clone->meta_data()->setInt64(kKeyTime, keyTime);
        clone->meta_data()->setInt32(kKeyIsSyncFrame, keyIsSyncFrame);
        uint8_t *data = (uint8_t *)clone->data();
        memcpy(data, mBuffer->data(), mBuffer->size());
        CHECK(clone != NULL);
        clone->set_range(mBuffer->range_offset(), (unsigned)start);
        CHECK(mBuffer != NULL);
        mBuffer->set_range( mBuffer->range_offset() + (size_t)start + (size_t)length,
                mBuffer->range_length() - (size_t)start - (size_t)length);
        if (mBuffer->range_length() == 0) {
            ALOGV("ASFSource::read() mBuffer->range_length = 0 ");
            mBuffer->release();
            mBuffer = NULL;
        }
        *out = clone;
    }
    return OK;
}

// Reassemble JPEG frame, SOI(0xffd8) - Data - EOI(0xffd9)
status_t ASFSource::assembleMjpegFrame(MediaBuffer **out) {
    bool bIsKeyFrame = false;
    ASFErrorType retVal = ASF_SUCCESS;
    // ALOGV("ASFSource::read() Video Type = MJPEG, reassemble buffer to 1 JPEG frame (SOI-data-EOI)");
    int end = -1;
    int start = -1;
    const uint8_t *src = (const uint8_t *)mBuffer->data() + mBuffer->range_offset();

    // 1st buffer must start with 0xffd8
    start = mjpegStartAt(src, mBuffer->range_length());
    ALOGV("ASFSource::read() check newBuffer SOI position = %d", start);

    // no start code(0xffd8), release this media frame and get another one
    while (start == -1) {
        if (mBuffer != NULL) {
            mBuffer->release();
            mBuffer = NULL;
        }

        ALOGV("ASFSource::read() find no SOI, get another media frame");
        retVal = mExtractor->GetNextMediaFrame(&mBuffer, bIsKeyFrame,mType,&mSeeking,mTrackIndex);

        if (ASF_SUCCESS != retVal) {
            ALOGV("[ASF_ERROR]ASFSource::read EOS reached 1(stream id = %d)", mStreamId);
            if (ASF_ERROR_MALFORMED == retVal) return ERROR_MALFORMED;
            else return ERROR_END_OF_STREAM;
        }

        src = (const uint8_t *)mBuffer->data() + mBuffer->range_offset();
        start = mjpegStartAt( src, (int32_t)mBuffer->range_length());
        ALOGV("ASFSource::read() check newBuffer SOI position = %d", start);
    }

    size_t mjpeg_frame_size = 0;

    //find the 1st buffer's metadata for out buffer
    int64_t keyTime=0;
    int32_t keyIsSyncFrame;
    mBuffer->meta_data()->findInt64(kKeyTime, &keyTime);
    mBuffer->meta_data()->findInt32(kKeyIsSyncFrame, &keyIsSyncFrame);

    end = mjpegEndAt(src + start, (int32_t)mBuffer->range_length() - (int32_t)start);
    ALOGV("ASFSource::read() check newBuffer EOI position = %d", start + end);

    // ensure mBuffer contain SOI and EOI,format:Data-SOI-Data-EOI-RemainFrame
    if (end != -1) { //Here, SOI and EOI are in the same frame
        mjpeg_frame_size += ((size_t)end + 1);
    } else { //find EOI(0xffd9) from subsequent ASF media frames
        //pBuffer = old mBuffer(with SOI) + next ASF frame +...+ next ASF frame with EOI
        uint8_t *pBuffer = new uint8_t[2*MAX_VIDEO_INPUT_SIZE];
        MediaBuffer *tmpBuffer = NULL;
        size_t pBufferSize = 0;
        size_t tmpBufferSize = 0; //store the last tmpBufferSize

        mjpeg_frame_size += (mBuffer->range_length() - (size_t)start);//EOI in next frame
        memcpy(pBuffer, src, mBuffer->range_length()); //copy old mBuffer to pBuffer
        pBufferSize += mBuffer->range_length();

        mBuffer->release();
        mBuffer = NULL;

        //if there's EOI in next frame, find it and copy the whole frame to pBuffer
        while (end == -1) {
            ALOGV("ASFSource::read() get next buffer to find EOI(0xffd9)");
            retVal = mExtractor->GetNextMediaFrame(&tmpBuffer, bIsKeyFrame,ASF_VIDEO,&mSeeking,mTrackIndex);

            if (ASF_SUCCESS != retVal) {
                ALOGV("[ASF_ERROR]ASFSource::read EOS reached 1(stream id = %d)", mStreamId);
                delete[] pBuffer;
                if (ASF_ERROR_MALFORMED == retVal) {
                    return ERROR_MALFORMED;
                } else {
                    return ERROR_END_OF_STREAM;
                }
            }

            end = mjpegEndAt((const uint8_t *)tmpBuffer->data() + tmpBuffer->range_offset(),
                    (int32_t)tmpBuffer->range_length());
            ALOGV("ASFSource::read() check newBuffer EOI position = %d", end);

            //copy ASF media frame to pBuffer
            uint8_t *data = (uint8_t *)tmpBuffer->data() + tmpBuffer->range_offset();
            memcpy(pBuffer + pBufferSize, data, tmpBuffer->range_length());

            pBufferSize += tmpBuffer->range_length();
            mjpeg_frame_size += tmpBuffer->range_length();
            tmpBufferSize = tmpBuffer->range_length();

            tmpBuffer->release();
            tmpBuffer = NULL;
        }
        mjpeg_frame_size = mjpeg_frame_size - tmpBufferSize + (size_t)end;
        //copy pBuffer(Data-SOI-Data-EOI-RemainFrame) to mBuffer
        uint8_t *data = (uint8_t *)mBuffer->data();
        memcpy(data, pBuffer, pBufferSize); // now the mBuffer=Data-SOI-Data-EOI-RemainFrame
        mBuffer->set_range(0, pBufferSize);

        delete[] pBuffer;
    }

    //clone modified data to out
    MediaBuffer *clone = new MediaBuffer(mjpeg_frame_size);
    clone->meta_data()->setInt64(kKeyTime, keyTime);
    clone->meta_data()->setInt32(kKeyIsSyncFrame, keyIsSyncFrame);
    uint8_t *data = (uint8_t *)clone->data();
    memcpy(data, (char *)mBuffer->data() + mBuffer->range_offset() + (size_t)start, mjpeg_frame_size);
    CHECK(clone != NULL);
    clone->set_range(0, mjpeg_frame_size);

    CHECK(mBuffer != NULL);
    mBuffer->set_range(mBuffer->range_offset() + (size_t)start + mjpeg_frame_size,
            mBuffer->range_length() - (size_t)start - mjpeg_frame_size);

    if (mBuffer->range_length() == 0) {
        // ALOGV("ASFSource::read() mBuffer->range_length = 0 ");
        mBuffer->release();
        mBuffer = NULL;
    }

    *out = clone;
    ALOGV("ASFSource::read() MJPEG MediaBuffer range_offset=%zu, range_length = %zu",
         (*out)->range_offset(), (*out)->range_length());
    return OK;
}
/////////////////////////////////////////////////////////////////////////
static void hexdump(const void *_data, size_t size) {
    const uint8_t *data = (const uint8_t *)_data;
    size_t offset = 0;
    while (offset < size) {
        printf("%zu  ", offset);

        size_t n = size - offset;
        if (n > 16) {
            n = 16;
        }

        for (size_t i = 0; i < 16; ++i) {
            if (i == 8) {
                printf(" ");
            }

            if (offset + i < size) {
                printf("%02x ", data[offset + i]);
            } else {
                printf("   ");
            }
        }
        printf(" ");

        for (size_t i = 0; i < n; ++i) {
            if (isprint(data[offset + i])) {
                printf("%c", data[offset + i]);
            } else {
                printf(".");
            }
        }
        printf("\n");
        offset += 16;
    }
}


int32_t asf_io_read_func(void *pAsfExtractor, void *aBuffer, int32_t aSize) {
    ASFExtractor* _pExtractor = (ASFExtractor*)pAsfExtractor;
    if (_pExtractor) {
        int32_t bytesRead = _pExtractor->mDataSource->readAt(
                _pExtractor->mAsfParserReadOffset, aBuffer, aSize);
        if (bytesRead >= 0 ) {
            _pExtractor->mAsfParserReadOffset += bytesRead;
        } else {
            ALOGE("asf_io_read_func: bytesRead %d < 0", bytesRead);
        }
        return bytesRead;
    }
    ALOGV("asf_io_read_func: return 0");
    return 0;
}

int32_t asf_io_write_func(void * /*pAsfExtractor*/, void * /*aBuffer*/, int32_t /*aSize*/) {
    return 0;
}

int64_t asf_io_seek_func(void *pAsfExtractor, int64_t aOffset) {
    // only supports SEEK_SET
    ASFExtractor* _pExtractor = (ASFExtractor*)pAsfExtractor;
    if (_pExtractor) {
        _pExtractor->mAsfParserReadOffset = aOffset;
        return aOffset;
    }

    return 0;
}

uint32_t  vc1_util_show_bits(uint8_t * data, uint32_t  bitcnt, uint32_t  num) {
    uint32_t  tmp, out, tmp1;

    tmp = (bitcnt & 0x7) + num;

    if (tmp <= 8) {
        out = (data[bitcnt >> 3] >> (8 - tmp)) & ((1 << num) - 1);
    } else {
        out = data[bitcnt >> 3]&((1 << (8 - (bitcnt & 0x7))) - 1);

        tmp -= 8;
        bitcnt += (8 - (bitcnt & 0x7));

        while (tmp > 8) {
            out = (out << 8) + data[bitcnt >> 3];

            tmp -= 8;
            bitcnt += 8;
        }

        tmp1 = (data[bitcnt >> 3] >> (8 - tmp)) & ((1 << tmp) - 1);
        out = (out << tmp) + tmp1;
    }

    return out;
}

uint32_t  vc1_util_get_bits(uint8_t * data, uint32_t  * bitcnt, uint32_t  num) {
    uint32_t ret;
    ret = vc1_util_show_bits(data, *bitcnt, num);
    (*bitcnt) += num;
    return ret;
}

uint32_t  vc1_util_show_word(uint8_t * a) {
    return ((a[0] << 24) + (a[1] << 16) + (a[2] << 8) + a[3]);
}

// output one complete frame
ASFErrorType ASFExtractor::GetNextMediaFrame(MediaBuffer** out, bool& bIsKeyFrame,
        AsfStreamType strmType, bool *isSeeking ,uint32_t curTrackIndex) {
    ASFErrorType retVal = ASF_SUCCESS;
    uint32_t max_buffer_size = 0;
    if (strmType == ASF_VIDEO) {
        max_buffer_size = MAX_VIDEO_INPUT_SIZE;//may be can be optimize by the real size
    } else if (strmType == ASF_AUDIO) {
        max_buffer_size = MAX_AUDIO_INPUT_SIZE;
    } else {
        ALOGE("[ASF_ERROR]Undefined ASFSource type!!!");
        return ASF_ERROR_UNKNOWN;
    }

    uint32_t sample_size = max_buffer_size;
    uint32_t timestamp = 0;
    uint32_t timestamp_dummy = 0;
    uint32_t replicatedata_size = 0;
    uint32_t current_frame_size = 0;
    uint8_t *pBuffer = new uint8_t[max_buffer_size];
    bool bIsKeyFrame_dummy = false;
    int read_new_obj = 0;

    if (*isSeeking) {
        //reset curTrackIndex's payload count when seek, then we will get a new packet from the new position
        ALOGV("set curTrackIndex %d's payload count == 0 when seek", curTrackIndex);
        AsfTrackInfo *trackInfo = &(mTracks.editItemAt(curTrackIndex));
        trackInfo->mCurPayloadIdx=0;
        trackInfo->mNextPacket->payload_count=0;
        if (strmType == ASF_AUDIO) (*isSeeking) = false;
    }

    retVal = GetNextMediaPayload(pBuffer + current_frame_size, sample_size, timestamp,
            replicatedata_size, bIsKeyFrame, curTrackIndex);
    if (ASF_SUCCESS != retVal) {
        ALOGE("[ASF_ERROR]GetNextMediaFrame failed A");
        delete[] pBuffer;
        if (ASF_ERROR_MALFORMED == retVal) return ASF_ERROR_MALFORMED;
        else return ASF_END_OF_FILE;
    } else {
        current_frame_size += sample_size;
    }

// Morris Yang 20110208 skip non-key frame payload [
//in a seek ponint , the first payload belonged to a media object maybe not
//the start payload of this meida object
//in this scenario, we will got a incompleted frame.So will drop
//a [KF] payload must be hte start payload of this meida object
    while ((*isSeeking) && (false == bIsKeyFrame)) {
        current_frame_size = 0;
        sample_size = max_buffer_size;
        ASF_DEBUG_LOGV("[ASF_ERROR]GetNextMediaFrame :drop a payload: timestamp=%d\n",timestamp);

        retVal = GetNextMediaPayload(pBuffer + current_frame_size, sample_size, timestamp,
                replicatedata_size, bIsKeyFrame,curTrackIndex);
        if (ASF_SUCCESS != retVal) {
            ALOGE("[ASF_ERROR]GetNextMediaFrame failed B");
            delete[] pBuffer;
            if (ASF_ERROR_MALFORMED == retVal) return ASF_ERROR_MALFORMED;
            else return ASF_END_OF_FILE;
        } else {
            //meaningless,as before GetNextMediaPayload , current_frame_size is reset to 0
            current_frame_size += sample_size;
        }
    }
    //(*isSeeking) = false;
    ASF_DEBUG_LOGV(
            "GetNextMediaFrame:1 isSeeking=%d,bIsKeyFrame=%d,current_frame_size=%d,replicatedata_size=%d\n",
            (*isSeeking), bIsKeyFrame, current_frame_size, replicatedata_size);

// ]

    RESET_PAYLOAD:
    //this while-loop will retrive all payloads belonged to one frame in cuurent stream ID

    //if a frame contains more than one payload, will enter this while-loop
    //we check this by current_frame_size(current red payload total size) <
    //replicatedata_size(whole frame size)
    while (current_frame_size < replicatedata_size) { //replicatedata_size is a frame size
        sample_size = max_buffer_size;
        //timestamp_dummy is no use ,as the payload in a frame should same
        //sample_size will be update each payload read, is the patload size
        retVal = GetNextMediaPayload(pBuffer + current_frame_size, sample_size, timestamp_dummy,
                replicatedata_size, bIsKeyFrame_dummy, curTrackIndex);
        if (ASF_SUCCESS != retVal) {
            ALOGE("[ASF_ERROR]GetNextMediaFrame failed D");
            delete[] pBuffer;
            if (ASF_ERROR_MALFORMED == retVal) return ASF_ERROR_MALFORMED;
            else return ASF_END_OF_FILE;
        }
        //Seek case:
        //read success,BUT not in the same media object
        //skip previous payload and get a new media object which started with present payload
        else if ((*isSeeking) && (timestamp_dummy != timestamp)) {
            ALOGE("[ASF_ERROR]GetNextMediaFrame failed C, cur_payload's ts is not equal to previous one\n");
            ALOGE("[ASF_ERROR]GetNextMediaFrame failed C, skip previous payload\n");

            //1st media objest after seek must be key frame, OTHERWISE, skip this payload
            if(bIsKeyFrame_dummy && (read_new_obj < 3)) {
                //memmove(pBuffer, pBuffer+current_frame_size, sample_size);
                //memset(pBuffer+sample_size, 0, max_buffer_size-sample_size);
                uint8_t *tmp = new uint8_t[sample_size];
                memcpy(tmp, pBuffer+(uint32_t)current_frame_size, sample_size);
                delete[] pBuffer;
                pBuffer = new uint8_t[max_buffer_size];
                memcpy(pBuffer, tmp, sample_size);

                current_frame_size = sample_size;
                timestamp = timestamp_dummy;
                timestamp_dummy = 0;
                delete [] tmp;
                read_new_obj++;

                goto RESET_PAYLOAD; //to get a new frame
            } else {
                retVal = ASF_FILE_READ_ERR;//not read a right object
                break;
            }
        }
        //-->add b qian start
        // in case of the 1th I payload in the seek point is not the 1 playlod of the I frame
        //BUT,if this case happen, the Frame type will check error, we will drop the 1th-not-I frame
        else if (timestamp_dummy != timestamp) { //read success,BUT not a frame
            ALOGV("GetNextMediaFrame failed: cur_payload's ts != previous one, cur_payload's ts %d, pre_payload's ts %d",
                    timestamp_dummy, timestamp);
            //drop the previous payload, reassemble a new frame from the cur_payload
            uint8_t *tmp = new uint8_t[sample_size];
            memcpy(tmp, pBuffer+(uint32_t)current_frame_size, sample_size);
            delete[] pBuffer;
            pBuffer = new uint8_t[max_buffer_size];
            memcpy(pBuffer, tmp, sample_size);

            current_frame_size = sample_size;
            timestamp = timestamp_dummy;
            timestamp_dummy = 0;
            delete [] tmp;
            goto RESET_PAYLOAD;
        } else { //read success,and belong to a frame
            current_frame_size += sample_size;
        }
    }
    // ALOGE("GetNextMediaFrame:2 isSeeking=%d, bIsKeyFrame=%d, current_frame_size=%d,
    // replicatedata_size=%d\n", (*isSeeking), bIsKeyFrame, current_frame_size, replicatedata_size);
    MediaBuffer *buffer = new MediaBuffer(current_frame_size);//when release this buffer???
    buffer->meta_data()->setInt64(kKeyTime, timestamp*1000LL);
    buffer->meta_data()->setInt32(kKeyIsSyncFrame, bIsKeyFrame);
    uint8_t *data = (uint8_t *)buffer->data();
    memcpy(data, pBuffer, current_frame_size);
    buffer->set_range(0, current_frame_size);
    (*isSeeking) = false;
    *out = buffer;
/*
    ALOGV("-GetNextMediaFrame(): streamId %d, ts %d, IsKey %d, replicated_size %d, (*out)->range_length() %zu, current_frame_size %d",
            mTracks.editItemAt(curTrackIndex).mTrackNum, timestamp, bIsKeyFrame, replicatedata_size,
            (*out)->range_length(), current_frame_size);
    ALOGV("mCurPayloadIdx %d, payload_count %d", mTracks.editItemAt(curTrackIndex).mCurPayloadIdx,
            mTracks.editItemAt(curTrackIndex).mNextPacket->payload_count);
*/
    delete[] pBuffer;
    return retVal;
}
// GetNextMediaPayload(pBuffer+current_frame_size, sample_size, timestamp,
// replicatedata_size, bIsKeyFrame);
// ust retrive one payload in the current stream in the pNextPacket
ASFErrorType ASFExtractor::GetNextMediaPayload(uint8_t* aBuffer,
        uint32_t& arSize, uint32_t& arTimeStamp, uint32_t& arRepDataSize,
        bool& bIsKeyFrame, uint32_t curTrackIndex) {
    ASFErrorType retVal = ASF_SUCCESS;
    asf_packet_t * pNextPacket= NULL;
    uint32_t *CurPayloadIdx = NULL;
    bool next_payload_found = false;
    bool payload_retrieved = false;

    asf_stream_t * pStreamProp = NULL;
    asf_stream_type_t stream_type = ASF_STREAM_TYPE_UNKNOWN;
    //mStreamId = mExtractor->mTracks.itemAt(index).mTrackNum;
    pStreamProp = mAsfParser->asf_get_stream(mTracks.editItemAt(curTrackIndex).mTrackNum);
    stream_type = pStreamProp->type;

    pNextPacket = mTracks.editItemAt(curTrackIndex).mNextPacket;
    CurPayloadIdx = &(mTracks.editItemAt(curTrackIndex).mCurPayloadIdx);

    if (mAsfParser != NULL) {
        //the do-while just retrive one payload in the needed stream in the pNextPacket, then out
        do {
            ASF_DEBUG_LOGE(
                    "[ASF]:GetNextMediaPayld:CurTrackIdx=%d,enter dowhile:*CurPayldIdx=%d,pNextPackt->payld_count=%d\n",
                    curTrackIndex, *CurPayloadIdx,pNextPacket->payload_count);
            // retrieve next packet in case there is no payload left in current packet
            if (!pNextPacket->payload_count) {
                int ret = mAsfParser->asf_get_stream_packet(pNextPacket, mTracks.editItemAt(curTrackIndex).mTrackNum);
                ALOGV("GetNextMediaPayload: curTrackIndex = %d, find a new packet, contain payloads = %d, ret = %d",
                        curTrackIndex, pNextPacket->payload_count, ret);
                if (ret <= 0) {  // should > 0 else is EOS, no data in file
                    asf_stream_t *pStreamProp = mAsfParser->asf_get_stream(mTracks.editItemAt(curTrackIndex).mTrackNum);

                    if(pStreamProp->flags & ASF_STREAM_FLAG_EXTENDED) { //has Extended Stream Properties Object
                        //Avg. time for frame is 100-nanosec - arTimestamp unit is millisec.
                        arTimeStamp = pStreamProp->extended->avg_time_per_frame / 10000;
                    } else {
                        ALOGE("[ASF_ERROR]GetNextMediaPayload:no extended field. dummy value inserted\n");
                        arTimeStamp = 0;
                    }

                    if (ASF_ERROR_INVALID_LENGTH == ret) {
                        ALOGE("GetNextMediaPayload: file doesn't not comply to spec, return ASF_ERROR_MALFORMED");
                        return ASF_ERROR_MALFORMED;
                    } else {
                        ALOGE("GetNextMediaPayload: return ASF_END_OF_FILE (asf_get_stream_packet return err = %d), streamID = %d",
                        ret, mTracks.editItemAt(curTrackIndex).mTrackNum);
                        return ASF_END_OF_FILE;
                    }
                }
                //updated Current Payload index
                *CurPayloadIdx = 0;// first payload in the newly found packet
            }
            //retrive all payloads in the newly found packet

            //mStreamId is  the track number(stream_number) when create the asf track source
            //payloads in a packet may be belong to different streams
            //if this payload in current packet is no belong to current stream, goto next payload
            asf_payload_t * ptrPayload = &pNextPacket->payloads[*CurPayloadIdx];
            if (NULL == ptrPayload) {
                retVal = ASF_END_OF_FILE;
                break;
            }
            if (mTracks.editItemAt(curTrackIndex).mTrackNum == (uint32_t)ptrPayload->stream_number) {
                if (!next_payload_found) { // is retrieving current payload
                    ASF_DEBUG_LOGV("[ASF]:GetNextMediaPayload 2: curTrackIndex=%d, next_payload_found = %d \n",
                            curTrackIndex, next_payload_found);
                    if (ptrPayload->datalen <= arSize) { //arSize is input sample size, the buffer size
                        if (stream_type == ASF_STREAM_TYPE_VIDEO) {
                            ASF_DEBUG_LOGV("%s Video Payload replen %d datalen %d ",
                                    __FUNCTION__,ptrPayload->replicated_length,ptrPayload->datalen);
                            if(ptrPayload->replicated_length) {
                                // the repliated data (1th 4byte is the frame size, 2th 4byte is frame TS)
                                arRepDataSize = ASFByteIO::asf_byteio_getDWLE(ptrPayload->replicated_data);
                                //ALOGV("rep size %d\n",arRepDataSize);
                            } else {
                                arRepDataSize  = ptrPayload->datalen;//???frame size ==this payload size ??
                            }
                        }
                        //update input parameters
                        arSize = ptrPayload->datalen;//update the input param,real payload size
                        memcpy(aBuffer, ptrPayload->data, ptrPayload->datalen);//copy this payload to the input buffer
                        arTimeStamp = ptrPayload->pts;
                        bIsKeyFrame = ptrPayload->key_frame;
                        ASF_DEBUG_LOGV(
                                "[ASF]:GetNextMediaPayload 2 curTrackIndex=%d size=%d, obj=%d, ts=%d, rep_len=%d, IsKey=%d\n",
                                curTrackIndex, arSize, ptrPayload->media_object_number, ptrPayload->pts,
                                arRepDataSize, ptrPayload->key_frame);
                        //Change the Current index and decrease payload
                        (*CurPayloadIdx)++;
                        pNextPacket->payload_count--;
                        ASF_DEBUG_LOGV(
                                "[ASF]:GetNextMediaPayload 2, curTrackIndex = %d, payload_count = %d, (*CurPayloadIdx) = %d\n",
                                curTrackIndex, pNextPacket->payload_count,(*CurPayloadIdx));

                        next_payload_found = true;//if set true, next do-while will not enter this path,error??
                        payload_retrieved = true;
                    } else {
                        retVal = ASF_ERR_NO_MEMORY;
                        ALOGE("[ASF_ERROR]GetNextMediaPayload return ASF_ERR_NO_MEMORY A\n");
                        break;
                    }
                }
            } else {
                //Change the Current index and decrease payload
                (*CurPayloadIdx)++;
                pNextPacket->payload_count--;
                ASF_DEBUG_LOGV(
                        "[ASF]:GetNextMediaPayload 4: current payload is not belong to the stream curTrackIndex=%d\n",
                        curTrackIndex);
            }
        }
        while(!payload_retrieved);
    } else {
        ASF_DEBUG_LOGV("%s Error OUT\n",__FUNCTION__);;
        retVal = ASF_END_OF_FILE;
        ALOGE("[ASF_ERROR]GetNextMediaPayload return ASF_END_OF_FILE B, streamID=%d\n",
             mTracks.editItemAt(curTrackIndex).mTrackNum);
    }

    return retVal;
}

ASFExtractor::ASFExtractor(const sp<DataSource> &source)
    : mFileMeta(new MetaData),
      mDbgFlags(0),
      mIgnoreAudio(0),
      mIgnoreVideo(0),
      mDurationMs(0),
      mSeekable(false),
      mThumbnailMode(false),
      mExtractedThumbnails(false),
      //mParsedFrameNum(0),
      //mFisrtFrameTsUs(0),
      //mFisrtFrameParsed(false),
      //mSecondFrameParsed(false),
      mPrerollTimeUs(0),
      mNalParType(ASF_START_CODE_TYPE),
      mVC1FourCC(WMV1_FOURCC),
      mSizeLength(0),
      mDataSource(source),
      mAsfParser(NULL),
      mAsfParserReadOffset(0),
      mIsValidAsfFile(false),
      mIsAsfParsed(false),
      mHasVideo(false),
      mHasVideoTrack(false),
      mFileSize(0) {
    mDataSource->getSize((off64_t*)&mFileSize);
    ALOGV("+ASFExtractor() 0x%p, tid %d, mFileSize %lld", this, gettid(), (long long)mFileSize);
    mAsfParser = new ASFParser((void*)this, asf_io_read_func, asf_io_write_func, asf_io_seek_func);

    if (!mAsfParser) {
        ALOGE("[ASF_ERROR]Error: ASFParser creation failed\n");
    }

    int retVal = ASF_ERROR_UNKNOWN;
    retVal = mAsfParser->IsAsfFile();//parse the file here
    if (ASF_SUCCESS == retVal) {
        ALOGD("This is an ASF File");
        mIsValidAsfFile = true;
    } else {
        ALOGV("Not an ASF File");
        mIsValidAsfFile = false;
    }

    char value[PROPERTY_VALUE_MAX];
    property_get("asfff.showts", value, "0");
    bool _res = atoi(value);
    if (_res) mDbgFlags |= ASFFF_SHOW_TIMESTAMP;

    property_get("asfff.ignoreaudio", value, "0");
    _res = atoi(value);
    if (_res) mDbgFlags |= ASFFF_IGNORE_AUDIO_TRACK;

    property_get("asfff.ignorevideo", value, "0");
    _res = atoi(value);
    if (_res) mDbgFlags |= ASFFF_IGNORE_VIDEO_TRACK;

    ALOGV("-ASFExtractor 0x%p, tid=%d\n", this, gettid());
}

ASFExtractor::~ASFExtractor() {
    ALOGV("~ASFExtractor 0x%p, tid=%d", this, gettid());
    if (mAsfParser) delete mAsfParser;
}

sp<MetaData> ASFExtractor::getMetaData() {
    ALOGV("[ASF]ASFExtractor::getMetaData()");
    if (false == mIsAsfParsed) {
        if(!ParseASF()) return NULL;
    }
    mFileMeta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_CONTAINER_ASF);

    if (countTracks() > 0) {
        if (mHasVideo) mFileMeta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_WMV);
        else mFileMeta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_WMA);
    }

    if(!mHasVideo && mHasVideoTrack) {
        mFileMeta->setInt32(kKeyHasUnsupportVideo, true);
        ALOGV("ASF has unsupport video track");
    }

    return mFileMeta;
}

size_t ASFExtractor::countTracks() {
    ALOGV("countTracks(): mIsAsfParsed %d", mIsAsfParsed);
    if (false == mIsAsfParsed) { //normal first parsed here
        if(!ParseASF()) return 0;
    }
    ALOGV("ASFExtractor::countTracks return %zu", mTracks.size());
    return mTracks.size();;
}

sp<MetaData> ASFExtractor::getTrackMetaData(size_t index, uint32_t flags) {
    ALOGV("getTrackMetaData(): mIsAsfParsed %d, index %zu", mIsAsfParsed, index);
    if (index >= mTracks.size()) {
        return NULL;
    }

    if ((flags & kIncludeExtensiveMetaData) && (false == mExtractedThumbnails)) {
        findThumbnail();
        mExtractedThumbnails = true;
    }
    return mTracks.itemAt(index).mMeta;
}

uint32_t ASFExtractor::flags() const {
    ALOGV("flags(): mSeekable %d", mSeekable);
    if(mSeekable) {
        return CAN_SEEK_BACKWARD | CAN_SEEK_FORWARD | CAN_PAUSE | CAN_SEEK;
    } else {
        ALOGV("[ASF]flags: can not seek,just can pasue\n");
        return CAN_PAUSE;
    }
}

void ASFExtractor::findThumbnail() {
    ALOGV("findThumbnail(): mSeekable %d", mSeekable);
    uint32_t idx=0;
    uint32_t currIdx=0;

    MediaBuffer *FrameSize[ASF_THUMBNAIL_SCAN_SIZE];
    MediaBuffer *out=NULL;
    ASFErrorType retVal = ASF_SUCCESS;
    mThumbnailMode = true;


    if(mSeekable) {
        for (size_t j = 0; j < ASF_THUMBNAIL_SCAN_SIZE; j++) {
            FrameSize[j]=NULL;
        }

        for (size_t i = 0; i < mTracks.size(); ++i) {
            AsfTrackInfo *info = &mTracks.editItemAt(i);
            const char *mime;
            CHECK(info->mMeta->findCString(kKeyMIMEType, &mime));
            currIdx=i;
            if (strncasecmp(mime, "video/", 6)) {
                continue;
            }

            for(idx=0; idx<ASF_THUMBNAIL_SCAN_SIZE; idx++) {
                bool bIsKeyFrame = false;
                bool isSeeking =false;
                bool bEOS=false;

                while (!bIsKeyFrame) {
                    retVal = GetNextMediaFrame(&out, bIsKeyFrame,ASF_VIDEO,&isSeeking,currIdx);//not seeking
                    if (ASF_SUCCESS != retVal) {
                        ALOGE("[ASF_ERROR]findThumbnail EOS (stream id = %d)", info->mTrackNum);
                        bEOS=true;
                        break;//may be a file has only <10 I frames
                    } else if (!bIsKeyFrame) {
                        (out)->release();  //seek  to 100ms, is not I ,will seek forward, bot nearest I befroe???
                    } else {
                        FrameSize[idx]=out;
                        ASF_DEBUG_LOGE("[ASF]findThumbnail find FrameSize[%d] =0x%p (stream id = %d)",
                                idx, out, info->mTrackNum);
                    }
                }
                if(bEOS) {
                    ALOGE("[ASF_ERROR]findThumbnail EOS (stream id = %d,idx=%d)", info->mTrackNum,idx);
                    break;
                }
            }

            uint32_t _max_frame_len = 0;
            int64_t _thumbnail_frame_ts = 0;
            uint32_t _cur_frame_len =0;
            int64_t _cur_timeUs;

            for (size_t j = 0; j < ASF_THUMBNAIL_SCAN_SIZE; j++) {
                if (FrameSize[j]!=NULL) {
                    _cur_frame_len =FrameSize[j]->range_length();
                    CHECK(FrameSize[j]->meta_data()->findInt64(kKeyTime, &_cur_timeUs));
                    //should add the preroll time
                    //as the parser give the TS is the Frame real ts,not presention ts
                    //presention ts = Frame real ts + preroll ts
                    //play when seek, it give the presention ts to seek command

                    ASF_DEBUG_LOGE("[ASF]findThumbnail: _cur_frame_len = %d, _cur_timeUs = %.2f s\n",
                            _cur_frame_len, (_cur_timeUs) / 1E6);//(_cur_timeUs+mPrerollTimeUs)/1E6);

                    if (_cur_frame_len >= _max_frame_len) {
                        _max_frame_len =_cur_frame_len;
                        _thumbnail_frame_ts = _cur_timeUs;
                    }
                }
            }

            //_thumbnail_frame_ts=_thumbnail_frame_ts;//+mPrerollTimeUs;

            info->mMeta->setInt64(kKeyThumbnailTime, _thumbnail_frame_ts);
            ALOGV("findThumbnail: final time is %.2f s, size %d", _thumbnail_frame_ts/1E6, _max_frame_len);
            for (size_t j = 0; j < ASF_THUMBNAIL_SCAN_SIZE; j++) {
                if(FrameSize[j]!=NULL) {
                    ASF_DEBUG_LOGE("ASFExtractor::findThumbnail rlease FrameSize[%d] = 0x%p (stream id = %d)",
                            j, FrameSize[j], info->mTrackNum);
                    FrameSize[j]->release();
                    FrameSize[j]=NULL;//avoid Wild pointer, set null after release
                }
                FrameSize[j]=NULL;
            }
        }

        ASFSeekTo(0);

        for (size_t i = 0; i < mTracks.size(); ++i) {
            AsfTrackInfo *trackInfo = &mTracks.editItemAt(i);

            if (trackInfo->mNextPacket) {
                mAsfParser->asf_packet_destroy(trackInfo->mNextPacket);
                trackInfo->mNextPacket = NULL;
                ASF_DEBUG_LOGE("[ASF]ASFSource::ASFSource Tracks index =%d, asf_packet_destroy\n", i);
            }
        }/**///no need to  destroy here
    }//if(mAsfParser->asf_check_simple_index_obj()) has seek index
    else {
        for (size_t i = 0; i < mTracks.size(); ++i) {
            AsfTrackInfo *info = &mTracks.editItemAt(i);
            const char *mime;
            CHECK(info->mMeta->findCString(kKeyMIMEType, &mime));
            currIdx=i;
            if (strncasecmp(mime, "video/", 6)) {
                continue;
            }
            info->mMeta->setInt64(kKeyThumbnailTime, 0);//just get the first frame to show
            ALOGV("kKeyThumbnailTime=0");
        }
    }

    mThumbnailMode = false;/**/
    //ALOGE("-ASFExtractor::findThumbnail");
}

// Given a time in seconds since Jan 1 1904, produce a human-readable string.
/*
static void convertTimeToDate(int64_t time_1904, String8 *s) {
    time_t time_1970 = time_1904 - (((66 * 365 + 17) * 24) * 3600);

    char tmp[32];
    strftime(tmp, sizeof(tmp), "%Y%m%dT%H%M%S.000Z", gmtime(&time_1970));

    s->setTo(tmp);
}
*/
sp<IMediaSource> ASFExtractor::getTrack(size_t index) {
    ALOGV("getTrack:index %zu, mTracks.size() %zu", index, mTracks.size());
    if (index >= mTracks.size()) {
        return NULL;
    }

    return new ASFSource(this, index);
}

bool ASFExtractor::ParserVC1CodecPrivateData(uint8_t*input,
        uint32_t /*inputlen*/, VC1SeqData* pSeqData) {
    uint32_t bitcnt = 0;
    uint32_t reserved ;
    reserved=(uint32_t)(*input);
    // Read Sequence Header for SP/MP (STRUCT_C)
    ALOGV("---ParserVC1CodecPrivateData ---");
    ALOGV("---CodecPrivateData is 0x%u ---", reserved);
    pSeqData->profile = vc1_util_get_bits(input, &bitcnt, 2);//profile
    if (pSeqData->profile == 3) { //WVC1: advanced
        ALOGE("[VC-1 Playback capability Error] VC-1 advanced profile, not support, failed\n");
        //ALOGE("[ASF_ERROR]VC-1 advanced profile, not support, failed\n");
        return false;
    }
    reserved=vc1_util_get_bits(input, &bitcnt, 2);
    reserved = vc1_util_get_bits(input, &bitcnt, 3);//frmrtq_postproc
    reserved = vc1_util_get_bits(input, &bitcnt, 5);//bitrtq_postproc
    reserved = vc1_util_get_bits(input, &bitcnt, 1);//loopfilter
    reserved = vc1_util_get_bits(input, &bitcnt, 1);
    if (reserved!=0) {
        ALOGE("[ASF_ERROR]VC-1 , error in BITMAPINFOHEADER, reserved bit should be 0,failed 1\n");
        return false;
    }


    pSeqData->multires = vc1_util_get_bits(input, &bitcnt, 1);//multires
    reserved = vc1_util_get_bits(input, &bitcnt, 1);
    if (reserved != 1) {
        // ALOGE("[ASF_ERROR]VC-1: error in BITMAPINFOHEADER, reserved bit should be 1, failed 2");
        // return false;
    }

    reserved = vc1_util_get_bits(input, &bitcnt, 1);//fastuvmc
    reserved = vc1_util_get_bits(input, &bitcnt, 1);//extended_mv
    reserved = vc1_util_get_bits(input, &bitcnt, 2);//dquant
    reserved = vc1_util_get_bits(input, &bitcnt, 1);//vstransform
    reserved = vc1_util_get_bits(input, &bitcnt, 1);
    if (reserved!=0) {
        ALOGE("[ASF_ERROR]VC-1  ,error in BITMAPINFOHEADER, reserved bit should be 0, failed 3\n");
        return false;
    }

    reserved = vc1_util_get_bits(input, &bitcnt, 1);//overlap
    reserved = vc1_util_get_bits(input, &bitcnt, 1);//syncmarker
    pSeqData->rangered = vc1_util_get_bits(input, &bitcnt, 1);
    pSeqData->maxbframes = vc1_util_get_bits(input, &bitcnt, 3);
    reserved = vc1_util_get_bits(input, &bitcnt, 2);//quantizer
    pSeqData->finterpflag = vc1_util_get_bits(input, &bitcnt, 1);
    reserved = vc1_util_get_bits(input, &bitcnt, 1);
    if(reserved!=1) {
        ALOGE("[ASF_ERROR]VC-1  , error in BITMAPINFOHEADER, reserved bit should be 1, failed 4\n");
        return false;
    }
    //parser done

    ALOGV("SeqData->profile = %d\n", pSeqData->profile);
    ALOGV("SeqData->rangered = %d\n", pSeqData->rangered);
    ALOGV("SeqData->maxbframes = %d\n", pSeqData->maxbframes);
    ALOGV("SeqData->finterpflag = %d\n", pSeqData->finterpflag);
    ALOGV("SeqData->multires = %d\n", pSeqData->multires);   // :multiresoltuin
    if (pSeqData->multires) {
    }
    ALOGV("SeqData->us_time_per_frame %lld, SeqData->framerate = %0.2f",
            (long long)pSeqData->us_time_per_frame, (float)(pSeqData->fps100/100));
    return true;
}

//Stream Properties Object
bool ASFExtractor::RetrieveWmvCodecSpecificData(asf_stream_t * pStreamProp,
        const sp<MetaData>& meta, VC1SeqData* pCodecSpecificData) {
    bool ret=true;
    char _four_cc[5];
    uint32_t retFourCC = 0;

    asf_bitmapinfoheader_t *bmih = (asf_bitmapinfoheader_t *)pStreamProp->properties;
    MakeFourCCString(bmih->biCompression, _four_cc);
    for (int i=0;_four_cc[i];i++) {
        _four_cc[i] = toupper(_four_cc[i]);
    }
    retFourCC =MakeStringToIntFourCC(_four_cc);
    if (retFourCC == 0) {
        ALOGE("Video format is null..");
    }
    MakeFourCCString(retFourCC, _four_cc);

    switch (retFourCC) {
        case FOURCC_WMV1:
        {
            mVC1FourCC = WMV1_FOURCC;
            meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_WMV);
            break;
        }
        case FOURCC_WMV2:
        {
            mVC1FourCC = WMV2_FOURCC;
            meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_WMV);
            break;
        }
        case FOURCC_WMV3:
        {
            mVC1FourCC = WMV3_FOURCC;
            meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_WMV);
            break;
        }
        case FOURCC_WMVA:
        {
            mVC1FourCC = WMVA_FOURCC;
            meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_WMV);
            break;
        }
        case FOURCC_WVC1:
        {
            mVC1FourCC = WVC1_FOURCC;
            meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_WMV);
            break;
        }
    //MPEG4
        case FOURCC_MP4S:
        case FOURCC_XVID:
        case FOURCC_DIVX:
        case FOURCC_DX50:
        case FOURCC_MP4V:
        case FOURCC_M4S2:
        {
            meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_MPEG4);
            break;
        }
        // H264, from http://wiki.multimedia.cx/index.php?title=H264
        case FOURCC_AVC1:
        case FOURCC_DAVC:
        case FOURCC_X264:
        case FOURCC_H264:
        case FOURCC_VSSH:
        {
            meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_AVC);
            break;
        }
    //MJPEG
        case FOURCC_MJPG:
        {
            meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_MJPEG);
            break;
        }
        default:
        {
            ALOGE("[VC-1 Playback capability Error] capability not support as :Unknown video format\n");
            meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_CONTAINER_ASF);
            ret = false;
            break;
        }
    }

    ALOGV("--- RetrieveWmvCodecSpecificData ---");
    ALOGV("bmih->biWidth %d, bmih->biHeight %d, extra data size %d",
            bmih->biWidth, bmih->biHeight, bmih->biSize - ASF_BITMAPINFOHEADER_SIZE);

    size_t mb_y_limit=(bmih->biHeight>>4)+(((bmih->biHeight&0xf)==0)?0:1);
    size_t mb_x_limit=(bmih->biWidth>>4)+(((bmih->biWidth&0xf)==0)?0:1);
    ALOGV("mb_y_limit %zu, mb_x_limit %zu", mb_y_limit, mb_x_limit);

    meta->setInt32(kKeyWidth, (int32_t)bmih->biWidth);
    meta->setInt32(kKeyHeight, (int32_t)bmih->biHeight);
    meta->setInt32(kKeyMaxInputSize, MAX_VIDEO_INPUT_SIZE);  // TODO: modify to a suitable value

    //framerate info
    //Extended Stream Properties Object (optional, 1 per media stream)
    double framerate=0.0;
    if (pStreamProp->flags & ASF_STREAM_FLAG_EXTENDED) {
        uint32_t max_buffer_size_video = pStreamProp->extended->max_obj_size;
        if (max_buffer_size_video <= MAX_VIDEO_INPUT_SIZE) { // else,means this value is error???
            meta->setInt32(kKeyMaxInputSize, max_buffer_size_video);
        }

        ALOGV("kKeyMaxInputSize %d, MAX_VIDEO_INPUT_SIZE %d", max_buffer_size_video, MAX_VIDEO_INPUT_SIZE);
        uint64_t  avg_time_per_frame = pStreamProp->extended->avg_time_per_frame;//100-nanosecond units,
        pCodecSpecificData->us_time_per_frame = avg_time_per_frame/10;//ms
        framerate = (double)(1000000.0/(double)(pCodecSpecificData->us_time_per_frame)) ;
    }
    //we will peep the 2nd frame TS, the calculate the framerate
    else {
    }
    pCodecSpecificData->fps100=(uint32_t)(framerate*100.0);

    ASF_DEBUG_LOGV("fps100 = %d\n", pCodecSpecificData->fps100);
    //set codec decoder infor, add the fps in the last position

    uint32_t _config_size_bmpinfo_hdr = ASF_BITMAPINFOHEADER_SIZE;
    uint32_t _config_size_extra_data = (bmih->biSize - ASF_BITMAPINFOHEADER_SIZE);
    uint32_t _keyWMVC_size =_config_size_bmpinfo_hdr + _config_size_extra_data + sizeof(uint32_t);

    uint8_t* _config = new uint8_t[_keyWMVC_size];
    ALOGV("config size %d, _config_size_extra_data size %d", _keyWMVC_size, _config_size_extra_data);

    memcpy(_config, bmih, ASF_BITMAPINFOHEADER_SIZE);
    memcpy(_config + ASF_BITMAPINFOHEADER_SIZE, bmih->data, _config_size_extra_data);
    memcpy(_config + ASF_BITMAPINFOHEADER_SIZE + _config_size_extra_data,
            &(pCodecSpecificData->fps100), sizeof(uint32_t));

    ASF_DEBUG_LOGV("fps100=%d\n", *(uint32_t*)
            (_config + ASF_BITMAPINFOHEADER_SIZE + _config_size_extra_data));

    // extra data is codec specific data, sequence header
    const char *mime_present;
    if (!meta->findCString(kKeyMIMEType, &mime_present)) {
        mime_present = "";
        ALOGD("video track has no kKeyMIMEType");
    }
    if (!strcasecmp(mime_present,MEDIA_MIMETYPE_VIDEO_WMV)) {
        ALOGV("mimeType = VIDEO_WMV, enter ParserVC1CodecPrivateData()\n");
        //send bitmap header + seq header + frame rate*100
        sp<ABuffer> csd = new ABuffer(_keyWMVC_size);
        memcpy(csd->data(), _config, _keyWMVC_size);
        sp<ABuffer> esds = MakeESDS(csd);
        meta->setData(kKeyESDS, kTypeESDS, esds->data(), esds->size());

        //send (seq header + frame rate*100) only
        //meta->setData(kKeyWMVC, 0, bmih->data, (_config_size_extra_data+sizeof(unit32_t)));
        if (!ParserVC1CodecPrivateData((_config + ASF_BITMAPINFOHEADER_SIZE),
                _config_size_extra_data,pCodecSpecificData)) {
            ret = false;
        }
    } else if(!strcasecmp(mime_present,MEDIA_MIMETYPE_VIDEO_MPEG4)) {
        ALOGV("mimeType = VIDEO_MPEG4\n");

        if (_config_size_extra_data!=0) {
            sp<ABuffer> csd = new ABuffer(_config_size_extra_data);
            memcpy(csd->data(), bmih->data, _config_size_extra_data);
            hexdump(csd->data(), csd->size());
            sp<ABuffer> esds = MakeESDS(csd);
            meta->setData(kKeyESDS, kTypeESDS, esds->data(), esds->size());
        } else {
            status_t ret = OK;
            ret = addMPEG4CodecSpecificData(meta);
            if( OK != ret ) {
                ALOGV("Can not find MPEG4 codec specific data: error %d", ret);
            }
        }
    }
    // no codec specific data, construct a new one from bitstream
    else if(!strcasecmp(mime_present,MEDIA_MIMETYPE_VIDEO_AVC)) {
        if (_config_size_extra_data != 0) {
            sp<ABuffer> csd = new ABuffer(_config_size_extra_data);
            memcpy(csd->data(), bmih->data, _config_size_extra_data);
            if (true == isNALStartCodeType(csd)) {
                mNalParType = ASF_START_CODE_TYPE;
            } else if (true == isNALSizeNalType(csd)) {
                mNalParType = ASF_SIZE_NAL_TYPE;
            } else {
                mNalParType = ASF_OTHER_TYPE;
            }

            ALOGV("mNalParType = %d", mNalParType);

            if (mNalParType == ASF_SIZE_NAL_TYPE ) {
                mSizeLength = getLengthSizeMinusOne(csd) + 1;
                ALOGV("mSizeLength = %d", mSizeLength);
            }

            switch (getNALParserType()) {
                case ASF_SIZE_NAL_TYPE:
                    meta->setInt32(kKeyWidth, bmih->biWidth);
                    meta->setInt32(kKeyHeight, bmih->biHeight);
                    meta->setData(kKeyAVCC, kTypeAVCC, csd->data(), csd->size());
                    break;
                case ASF_START_CODE_TYPE:
                    if (asfMakeAVCCodecSpecificData(csd, meta) != OK) {
                        ALOGV("asfMakeAVCCodecSpecificData fail!!");
                        mIgnoreVideo = true;
                        ret = false;
                    }
                    break;
                case ASF_OTHER_TYPE:
                    ALOGV("asfMakeAVCCodecSpecificData fail for ASF_OTHER_TYPE!!");
                    mIgnoreVideo = true;
                    ret = false;
                    break;
                default:
                    break;
            }

        } else {
            status_t retStatus = OK;
            retStatus = addAVCCodecSpecificData(meta);
            if ( OK != retStatus ) {
                ALOGV("Can not find AVC codec specific data: error %d", ret);
            }
        }
    } else {
        ALOGV("Unknown Video Type");
    }

    delete[] _config;
    return ret;
}

// Stream Properties Object
// Stream Type Specific:Stream Type Audio Media 18byte config + cb size extra data
bool ASFExtractor::RetrieveWmaCodecSpecificData(
        asf_stream_t * pStreamProp, const sp<MetaData>& meta) {
    if (pStreamProp->flags & ASF_STREAM_FLAG_AVAILABLE) {
        asf_waveformatex_t *wfx = (asf_waveformatex_t *)pStreamProp->properties;
        switch (wfx->wFormatTag) {
            case WAVE_FORMAT_WMA1:
                break;
            case WAVE_FORMAT_WMA2:
                break;
            case WAVE_FORMAT_WMA3:
            {
#ifdef MTK_SWIP_WMAPRO
                break;
#else
                ALOGE("[ASF Playback Capability Error]: audio WMA3(that is WMAPRO) is not supported");
                return false;
#endif
            };
            case WAVE_FROMAT_MSPCM:
            {
                break;
            }
            case WAVE_FORMAT_MP3:
            {
                break;
            }
            case WAVE_FROMAT_MSADPCM:
            {
                ALOGE("[ASF Playback Capability Error]: audio MSADPCM is not supported");
                return false;;
            }
            case WAVE_FORMAT_MP2:
            {
                break;
            }
            case WAVE_FORMAT_AAC:
            case WAVE_FORMAT_AAC_AC:
            case WAVE_FORMAT_AAC_pm:
            {
                meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_AAC);
                int profile = 1;
                meta->setInt32(kKeyAACProfile, profile);
                meta->setInt32(kKeySampleRate, wfx->nSamplesPerSec);
                meta->setInt32(kKeyChannelCount, wfx->nChannels);

                sp<ABuffer> csd = new ABuffer(wfx->cbSize);
                memcpy(csd->data(), wfx->data, wfx->cbSize);

                hexdump(csd->data(), csd->size());
                sp<ABuffer> esds = MakeESDS(csd);
                meta->setData(kKeyESDS, kTypeESDS, esds->data(), esds->size());
                break;
            }
            default:
            {
                ALOGE("[ASF Playback Capability Error]: unknown audio format 0x%hu", wfx->wFormatTag);
                return false;
            }
        }
        ALOGV("-----[ASF]RetrieveWmaCodecSpecificData -----");
        ALOGV("wfx->wFormatTag = 0x%hu\n", wfx->wFormatTag);
        ALOGV("wfx->nChannels = %d\n", wfx->nChannels);
        ALOGV("wfx->nSamplesPerSec = %d\n", wfx->nSamplesPerSec);
        ALOGV("wfx->nAvgBytesPerSec = %d\n", wfx->nAvgBytesPerSec);
        ALOGV("wfx->nBlockAlign = %d\n", wfx->nBlockAlign);
        ALOGV("wfx->wBitsPerSample = %d\n", wfx->wBitsPerSample);
        ALOGV("wfx->cbSize = %d\n", wfx->cbSize);
        if(wfx->wFormatTag == WAVE_FORMAT_WMA1 || wfx->wFormatTag == WAVE_FORMAT_WMA2) {
            meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_WMA);
        }
#ifdef MTK_SWIP_WMAPRO
        else if(wfx->wFormatTag == WAVE_FORMAT_WMA3) {
            meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_WMAPRO);
        }
#endif
        else if (wfx->wFormatTag == WAVE_FROMAT_MSPCM) {
            meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_RAW);

            // for PCM component
#ifdef MTK_AUDIO_RAW_SUPPORT
            meta->setInt32(kKeyEndian, 2);  //1: big endian, 2: little endian(default)
            meta->setInt32(kKeyBitWidth, wfx->wBitsPerSample); //bits per sample
            meta->setInt32(kKeyPCMType, 1); //1. WAV file  2.BD file 3.DVD_VOB file,  4.DVD_AOB file
            //meta->setInt32(kKeyChannelAssignment, 1);
            /* support unsigned PCM */
            if (wfx->wBitsPerSample == 8) {
                meta->setInt32(kKeyNumericalType, 2);
            }
#endif
        } else if(wfx->wFormatTag == WAVE_FORMAT_MP3) {
            meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_MPEG);
        } else if(wfx->wFormatTag == WAVE_FORMAT_MP2) {
            meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_MPEG_LAYER_II);
        }
#ifdef MTK_SWIP_WMAPRO
        if (wfx->wFormatTag == WAVE_FORMAT_WMA1 || wfx->wFormatTag == WAVE_FORMAT_WMA2 ||
                wfx->wFormatTag == WAVE_FORMAT_WMA3)
#else
        if (wfx->wFormatTag == WAVE_FORMAT_WMA1 || wfx->wFormatTag == WAVE_FORMAT_WMA2)
#endif
        {
            uint32_t _config_size = (uint32_t)ASF_WAVEFORMATEX_SIZE + wfx->cbSize;  //wfx->cbSize must be 10
            uint8_t* _config = new uint8_t[_config_size];
            ALOGV("config_size %d", _config_size);
            memcpy(_config, wfx, ASF_WAVEFORMATEX_SIZE);
            memcpy(_config + ASF_WAVEFORMATEX_SIZE, wfx->data, wfx->cbSize);
            if (wfx->wFormatTag == WAVE_FORMAT_WMA3) {
                //meta->setData(kKeyWMAPROC, 0, _config, _config_size);
                sp<ABuffer> csd = new ABuffer(_config_size);
                memcpy(csd->data(), _config, _config_size);
                sp<ABuffer> esds = MakeESDS(csd);
                meta->setData(kKeyESDS, kTypeESDS, esds->data(), esds->size());
            } else {
                sp<ABuffer> csd = new ABuffer(_config_size);
                memcpy(csd->data(), _config, _config_size);
                sp<ABuffer> esds = MakeESDS(csd);
                meta->setData(kKeyESDS, kTypeESDS, esds->data(), esds->size());
            }

            delete [] _config;
        }

        meta->setInt32(kKeySampleRate, wfx->nSamplesPerSec);
        meta->setInt32(kKeyChannelCount, wfx->nChannels);
        meta->setInt32(kKeyMaxInputSize, MAX_AUDIO_INPUT_SIZE);//4K

        return true;
    }

    ALOGE("[ASF_ERROR]RetrieveWmaCodecSpecificData no codec specific info available");
    return false;
}

bool ASFExtractor::ParseASF() {
    ALOGV("+[ASF]ASFExtractor::ParseASF");
    ALOGV("===============================================================================\n");
    ALOGV("[VC-1 Playback capability info]\n");
    ALOGV("=====================================\n");
    ALOGV("Resolution = \"[(8,8) ~ (1280720)]\" \n");
    ALOGV("Profile_Level = \"VC1 simple, main,adnvanced profile\" \n");
    ALOGV("Support Codec = \"Video:WMV3,WMVA,WVC1 ; Audio: WMA1 0x160,WMA2 0x161\" \n");
    ALOGV("Performance limitation = 4Mbps  (720*480@30fps)\n");
    ALOGV("===============================================================================\n");

    if (mAsfParser) {
        uint8_t hasDRMObj=mAsfParser->asf_parse_check_hasDRM();
        if(hasDRMObj) {
            ALOGE("!!![ASF_ERROR]has DRM obj, encrypted file, not support");
            return false;
        }
        uint64_t mDurationMs = mAsfParser->asf_get_duration();
        ALOGV("Duration: %llu ms\n", (unsigned long long) mDurationMs);
        if (mDurationMs <= 0) {
            ALOGE("!!![ASF_ERROR]Duration == 0, error file, not support");
            return false;
        }

        uint8_t numTracks = mAsfParser->asf_get_stream_count();
        ALOGV("Num of Streams: %d\n", numTracks);

        uint32_t max_bitrate = mAsfParser->asf_get_max_bitrate();
        ALOGV("Max bitrate: %d\n", max_bitrate);

        mSeekable = mAsfParser->asf_is_seekable();
        ALOGV("mSeekable: %d\n", (mSeekable > 0));

        int numpackets = mAsfParser->asf_get_data_packets();
        ALOGV("numpackets: %d \n", numpackets);
        if (numpackets<=0) {
            ALOGE("!!![ASF_ERROR]has no packets data, error file, not support");
            return false;
        }

        mPrerollTimeUs =(mAsfParser->asf_get_preroll_ms())*1000;

        ALOGV("mPrerollTimeUs: %lld ms\n", (long long)(mPrerollTimeUs/1000));

#if 1
        // retrieve meta data (optional)
        asf_metadata_t * pASFMetadata = mAsfParser->asf_header_get_metadata();
        if (!pASFMetadata) {
            ALOGE("ASFExtractor pAsfParser->asf_header_get_metadata failed\n");
        } else {
            //kKeyArtist ' "artist"
            //  kKeyAlbum             = 'albu',  //
            // kKeyTitle             = 'titl',
            //kKeyAlbumArt          = 'albA',

            char keyArtist[7]="Author";
            //uint32_t out_len;
            asf_metadata_entry_t* entry  =  NULL;

            entry =mAsfParser->asf_findMetaValueByKey(pASFMetadata,keyArtist,6);
            if (entry && entry->value) {
                mFileMeta->setCString(kKeyArtist, entry->value);
            } else {
                ASF_DEBUG_LOGE("no kKeyArtist / Author in file meta");
            }
            entry=NULL;
            char keyAlbum[14]="WM/AlbumTitle";
            entry =  mAsfParser->asf_findMetaValueByKey(pASFMetadata,keyAlbum,13);
            if (entry && entry->value) {
                mFileMeta->setCString(kKeyAlbum,  entry->value);
            } else {
                ASF_DEBUG_LOGE("no kKeyAlbum / WM/AlbumTitle  in file meta");
            }
            entry=NULL;
            char keyTitle[6]="Title";
            entry =  mAsfParser->asf_findMetaValueByKey(pASFMetadata,keyTitle,5);
            if (entry && entry->value) {
                mFileMeta->setCString(kKeyTitle,  entry->value);
            } else {
                ASF_DEBUG_LOGE("no kKeyTitle / Title in file meta");
            }
            entry=NULL;
            char keyAlbumArt[11]="WM/Picture";
            entry =  mAsfParser->asf_findMetaValueByKey(pASFMetadata,keyAlbumArt,10);
            if (entry && entry->value) {
                uint32_t dataoff=0;
                mAsfParser->asf_parse_WMPicture((uint8_t*)(entry->value), entry->size, &dataoff);
                mFileMeta->setData(kKeyAlbumArt, MetaData::TYPE_NONE,  entry->value+dataoff, entry->size-dataoff);
            } else {
                ASF_DEBUG_LOGE("no kKeyAlbumArt  / WM/Picture in file meta ");
            }
            entry=NULL;

            char keyTrackNumber[15]="WM/TrackNumber";
            entry =  mAsfParser->asf_findMetaValueByKey(pASFMetadata,keyTrackNumber,14);
            if (entry && entry->value) {
                mFileMeta->setCString(kKeyCDTrackNumber,entry->value);
            } else {
                ASF_DEBUG_LOGE("no WM/TrackNumber in file meta ");
            }
            mAsfParser->asf_metadata_destroy(pASFMetadata);
        }

#endif

        //uint32_t max_buffer_size_video;
        //uint32_t max_buffer_size_audio;

        uint32_t stream_id_video = 0;
        uint32_t stream_id_audio = 0;

        // get stream and format type
        for (uint32_t i = ASF_STREAM_ID_START ; i <= numTracks ; i++) {
            asf_stream_t * pStreamProp = NULL;
            asf_stream_type_t stream_type = ASF_STREAM_TYPE_UNKNOWN;
            pStreamProp = mAsfParser->asf_get_stream(i);
            stream_type = pStreamProp->type;

            sp<MetaData> meta = new MetaData;

            void* pCodecSpecificData =NULL;//video will set ,audio not use
            uint32_t CurCodecSpecificSize=0;

            meta->setInt64(kKeyDuration, mDurationMs*1000LL);
            meta->setInt32(kKeyBitRate, max_bitrate);

            if (stream_type == ASF_STREAM_TYPE_AUDIO) {
                if (mDbgFlags & ASFFF_IGNORE_AUDIO_TRACK) {
                    ALOGE("[ASF_ERROR]ParseASF:ASFFF_IGNORE_AUDIO_TRACK\n");
                    continue;
                }

                ALOGV("[ASF]Stream %d is AUDIO: ", i);
                stream_id_audio = i;

                //codec specific data poniter is not used,set null

                //set WMA codec specific data to meta
                if (false == RetrieveWmaCodecSpecificData(pStreamProp, meta)) {
                    meta = NULL;
                    continue;
                }
            } else if (stream_type == ASF_STREAM_TYPE_VIDEO) {
                mHasVideoTrack = true;

                if (mDbgFlags & ASFFF_IGNORE_VIDEO_TRACK) {
                    ALOGE("[ASF_ERROR]ParseASF:ASFFF_IGNORE_VIDEO_TRACK\n");
                    mHasVideo = false;
                    continue;
                }

                ALOGV("Stream %d is VIDEO: ", i);
                stream_id_video = i;

                pCodecSpecificData =(VC1SeqData*)calloc(1, sizeof(VC1SeqData));
                CurCodecSpecificSize= sizeof(VC1SeqData);
                if (pCodecSpecificData==NULL) {
                    ALOGE("[ASF_ERROR]:calloc VC1SeqData failed, stream_id_video=%d\n",i);
                }
                ASF_DEBUG_LOGV("ASF ParseASF:calloc pCodecSpecificData=0x%p\n", pCodecSpecificData);

                if (!(pStreamProp->flags & ASF_STREAM_FLAG_AVAILABLE)) {
                    ALOGE("[ASF_ERROR]RetrieveWmvCodecSpecificData no codec specific info available");
                    meta = NULL;
                    //mFileMeta->setInt32(kKeyHasUnsupportVideo,true);
                    continue;
                } else if (false == RetrieveWmvCodecSpecificData(pStreamProp, meta,
                        (VC1SeqData*)pCodecSpecificData)) {
                    ALOGV("no codec specific data, or unsupported video format ");
                    if (mIgnoreVideo== true) {
                        meta = NULL;
                        continue;
                    }
                } else {
                    ALOGV("change!");
                }

                mHasVideo = true;//if code come to here , video

            } else {
                ALOGE("[ASF]Stream %d is not audio or video, skip it,stream type is =%d: ", i,stream_type);
                meta = NULL;
                continue;
            }
            //acesss meta from here start NE
            ALOGV("mTrackNum %d", i);

            if (meta == NULL) {
                ALOGE ("[ASF]Stream %d has no metadata, please check,stream type is =%d: ", i,stream_type);
            }
            mTracks.push();
            AsfTrackInfo *trackInfo = &mTracks.editItemAt(mTracks.size() - 1);
            trackInfo->mTrackNum = i; // stream id
            trackInfo->mMeta = meta; // track meta is important
            trackInfo->mCodecSpecificData=pCodecSpecificData;
            trackInfo->mCodecSpecificSize=CurCodecSpecificSize;
            trackInfo->mNextPacket =  mAsfParser->asf_packet_create();
            trackInfo->mCurPayloadIdx=0;
        }
    }
    mIsAsfParsed = true;
    return true;
}

int64_t ASFExtractor::ASFSeekTo(uint32_t seekTimeMs) {
    int64_t _new_ts = mAsfParser->asf_seek_to_msec(seekTimeMs);
    ALOGV("ASFSeekTo %d return %lld ms", seekTimeMs, (long long)_new_ts);
    return _new_ts;
}

////////////////////////////////////////////////////////////////////////////////

int switchAACSampleRateToIndex_asf(int sample_rate) {
    int index = 0;

    switch (sample_rate) {
        case 96000:
            index = 0;
            return index;

        case 88200:
            index = 1;
            return index;

        case 64000:
            index = 2;
            return index;

        case 48000:
            index = 3;
            return index;

        case 44100:
            index = 4;
            return index;

        case 32000:
            index = 5;
            return index;

        case 24000:
            index = 6;
            return index;

        case 22050:
            index = 7;
            return index;

        case 16000:
            index = 8;
            return index;

        case 12000:
            index = 9;
            return index;

        case 11025:
            index = 10;
            return index;

        case 8000:
            index = 11;
            return index;

        case 7350:
            index = 12;
            return index;

        default:
            index = -1;
            ALOGE("switchAACSampleRateToIndex_asf: error sample rate: %d , just use index 0 to try", sample_rate);
        return index;
    }
}

status_t ASFExtractor::addMPEG4CodecSpecificData(const sp<MetaData>& mMeta) {
    off64_t offset;
    size_t size;
    size_t volStart = 0;
    bool foundVolStart = false;
    size_t vopStart = 0;
    bool foundVopStart = false;

    offset = mAsfParser->asf_get_data_position()+50;
    size = MAX_VIDEO_INPUT_SIZE ;
    sp<ABuffer> buffer = new ABuffer(size);
    ssize_t n = mDataSource->readAt(offset, buffer->data(), buffer->size());

    if (n < (ssize_t)size) {
        return n < 0 ? (status_t)n : ERROR_MALFORMED;
    }

    // Extract everything up to the first VOP start code from the first
    // frame's encoded data and use it to construct an ESDS with the
    // codec specific data.
    // extractor VOL ~ VOP's buffer to MakeESDS()
    // VOL(video object layer) start code: 0x00 00 01 20 - 0x00 00 01 2F
    // VOP start code: 0x00 00 01 B6

    while (volStart + 3 < buffer->size()) {
        if (!memcmp("\x00\x00\x01", &buffer->data()[volStart], 3) && ((buffer->data()[volStart + 3] & 0xf0) == 0x20)) {
            foundVolStart = true;
            break;
        }
        ++volStart;
    }

    if (!foundVolStart) {
        ALOGV("VOL start code: 0x00000120-2F not found!");
        return ERROR_MALFORMED;
    }

    vopStart = volStart + 4; //ensure vopStart is greater than volStart
    while (vopStart + 3 < buffer->size()) {
        if (!memcmp("\x00\x00\x01\xb6", &buffer->data()[vopStart], 4)) {
            foundVopStart = true;
            break;
        }
        ++vopStart;
    }

    if (!foundVopStart) {
        ALOGV("VOP start code:0x000001B6 not found!");
        return ERROR_MALFORMED;
    }
    ALOGV("volStart = %zu, vopStart = %zu", volStart, vopStart);
    for (uint32_t i = volStart ; i < vopStart; i++) {
        ALOGV("VOS[%d] = 0x%hhu", i, *((uint8_t *)buffer->data() + i));
    }

    buffer->setRange(volStart, vopStart);
    sp<ABuffer> csd = MakeESDS(buffer);
    mMeta->setData(kKeyESDS, kTypeESDS, csd->data(), csd->size());
    return OK;
}

status_t ASFExtractor::addAVCCodecSpecificData(const sp<MetaData>& mMeta) {
    int32_t iSPSPos = 0;
    int64_t asfDataPos;
    int32_t offsetStart = 0;
    bool IsFindPPS = false;
    //int PPSOffset = 0;
    int prefixLen = -1;
    uint8_t *bufstart = NULL;
    uint8_t *bufend = NULL;
    uint32_t size = 0;
    ssize_t n = 0;
   // int32_t width = 0;
    //int32_t height = 0;
    //uint32_t type;
    //const void *csd = NULL;
    //size_t csdSize = 0;
    sp<MetaData> metaAVC;
    uint8_t iNALSizeLength = 0;
    //uint32_t nalLength = 0;

    size = MAX_VIDEO_INPUT_SIZE;
    sp<ABuffer> buffer = new ABuffer(size);
    iNALSizeLength = getNALSizeLength();
    ALOGV("mimeType = VIDEO_AVC, MakeAVCCodecSpecificData\n");
    asfDataPos = mAsfParser->asf_get_data_position() + 50;

    n = mDataSource->readAt(asfDataPos, buffer->data(), size);
    ALOGV("asfDataPos %lld, read %zx bytes", (long long)asfDataPos, n);
    if (n < (ssize_t)size) {
        ALOGV("ASF_ERROR: can not find AVC codec specific data");
        return n < 0 ? (status_t)n : ERROR_MALFORMED;
    }

    asfDataPos += n;
    bufstart = (uint8_t*)buffer->data();
    bufend = bufstart + size;

    // exp: Retrieve data  00 00 01 67 and 00 00 01 68
    iSPSPos = realAVCStart(buffer->data(), (int32_t)size);//SPS position= ptr+AVCpos
    if (-1 == iSPSPos) {
        ALOGE("[error] iSPSPos = -1.");
        mIgnoreVideo = true;
        return false;
    }

    while (bufstart < bufend) { //find pps
        offsetStart = nalStartAt(bufstart, bufend - bufstart, &prefixLen);
        if (offsetStart == -1 || bufstart >= bufend) {
        ALOGE("[error] offsetStart= %d , (bufstart >= bufend) = %d",offsetStart,bufstart >= bufend);
            mIgnoreVideo = true;
            return false;
        }
        bufstart += offsetStart + prefixLen;

        //offsetStart = the length of (SPS + PPS)
        if (true == IsFindPPS) {
            if (bufstart > ((uint8_t*)buffer->data()+ iSPSPos)) {
                offsetStart = bufstart - ((uint8_t*)buffer->data()+ iSPSPos);//length of SPS and PPS
                buffer->setRange((size_t)iSPSPos, (size_t)offsetStart);
            } else {
                ALOGE("[error] ERROR Plz check PPS order");
                mIgnoreVideo = true;
                return false;
            }
            break;
        }

        if ((*bufstart  & 0x1f)  == 0x08) {
            IsFindPPS = true;
        }
    }

    ALOGV("AVCPos = %d, ptr = 0x%p, offsetStart = %d, prefixLen = %d\n",
          iSPSPos, bufstart, offsetStart, prefixLen);

    if (IsFindPPS == true) {
        if (asfMakeAVCCodecSpecificData(buffer, mMeta) != OK) {
            ALOGV("Make AVCCodec SpecificData fail!!");
            mIgnoreVideo = true;
            return false;
        }
    } else {
        ALOGV(" Error,IsFindPPS == false!!");
        mIgnoreVideo = true;
        return false;
    }
    return OK;
}

NAL_Parser_Type ASFExtractor::getNALParserType() {
    return mNalParType;
}

uint8_t ASFExtractor::getNALSizeLength() {
    return mSizeLength;
}

uint8_t ASFExtractor::getLengthSizeMinusOne(const sp<ABuffer> &buffer) {
    uint8_t sizeLengthMinusOne;
    CHECK(buffer->size() >= 7);
    CHECK(1 == *((uint8_t*)(buffer->data())));
    sizeLengthMinusOne =(*((uint8_t*)(buffer->data())+4))&0x03;
    return sizeLengthMinusOne;
}

bool ASFExtractor::isNALStartCodeType(const sp<ABuffer> &buffer) {
    int32_t iSPSPos = 0;
    int32_t iPPSPos = 0;
    if ( NULL == buffer->data() ) {
        ALOGE("[error] isNALStartCodeType() buffer->data() is null!");
        return false;
    }
    iSPSPos = realAVCStart(buffer->data(), (int32_t)buffer->size());
    if (-1 == iSPSPos) {
        ALOGE("isNALStartCodeType() NO SPS!!");
        return false;
    }
    iPPSPos = realPPSStart(buffer->data(), (int32_t)buffer->size());
    if (-1 == iPPSPos) {
        ALOGE("isNALStartCodeType() NO PPS!!");
        return false;
    }
    if (iPPSPos < iSPSPos) {
        ALOGE("[error] PPS is in front of SPS.");
    }
    return true;
}

bool ASFExtractor::isNALSizeNalType(const sp<ABuffer> &buffer) {
    CHECK(buffer->size() >= 7);
    //size_t mBufSize = buffer->size();
    if ( NULL == buffer->data() ) {
        ALOGE("[isNALSizeNalType][error] buffer->data() is null!");
        return false;
    }
    if (1 != *((uint8_t*)(buffer->data()))) {
        ALOGE("[isNALSizeNalType][error] configureationVertion is not 1.");
        return false;
    }
    return true;
}

uint32_t ASFExtractor::parseNALSize(uint8_t *data) {
    if (NULL == data) {
        return 0;
    }

    switch (getNALSizeLength()) {
        case 1:
            return *data;
        case 2:
            return U16_AT(data);
        case 3:
            return ((size_t)data[0] << 16) | U16_AT(&data[1]);
        case 4:
            return U32_AT(data);
    }

    // This cannot happen, mNALLengthSize springs to life by adding 1 to
    // a 2-bit integer.
    CHECK(!"Should not be here.");

    return 0;
}

status_t ASFExtractor::asfMakeAVCCodecSpecificData(
        const sp<ABuffer> &buffer,const sp<MetaData>& mMeta) {
    sp<MetaData> metaAVC;
    int32_t width = 0;
    int32_t height = 0;
    const void *csd = NULL;
    size_t csdSize = 0;
    uint32_t type;

    if (NULL == buffer->data()) {
        return ERROR_MALFORMED;
    }

    metaAVC = MakeAVCCodecSpecificData(buffer);
    if (metaAVC == NULL) {
        ALOGE("Unable to extract AVC codec specific data");
        return ERROR_MALFORMED;
    }
    CHECK(metaAVC->findInt32(kKeyWidth, &width));
    CHECK(metaAVC->findInt32(kKeyHeight, &height));
    CHECK(metaAVC->findData(kKeyAVCC, &type, &csd, &csdSize));
    mMeta->setInt32(kKeyWidth, width);
    mMeta->setInt32(kKeyHeight, height);
    mMeta->setData(kKeyAVCC, type, csd, csdSize);
    return OK;
}

VC1_FourCC ASFExtractor::getVC1FourCC() {
    return mVC1FourCC;
}

//little endian
void ASFExtractor::MakeFourCCString(uint32_t x, char *s) {
    s[0] = x;
    s[1] = (x >> 8) & 0xff;
    s[2] = (x >> 16) & 0xff;
    s[3] = (x >> 24) & 0xff;
    s[4] = '\0';
}

uint32_t ASFExtractor::MakeStringToIntFourCC(char *strFourCC) {
    uint32_t intfourCC = 0;
    if (strFourCC == NULL ) {
        return 0;
    }

    for (int i = 3; i >= 0; i-- ) {
        if (i != 3) {
            intfourCC=(intfourCC<<8)|strFourCC[i];
        } else {
            intfourCC = strFourCC[i];
        }
    }
    return intfourCC;
}

bool SniffASF(const sp<DataSource> &source, String8 *mimeType, float *confidence, sp<AMessage> * /*meta*/) {
    sp<ASFExtractor> extractor = new ASFExtractor(source);
    bool ret = extractor->IsValidAsfFile();
    if (ret) {
        *mimeType = MEDIA_MIMETYPE_CONTAINER_ASF;
        *confidence = 0.8;
        ret = true;
    } else {
        ret = false;
    }

    extractor = NULL;  // deconstruct
    return ret;
}
}  // namespace android
#endif
