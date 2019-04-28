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

//#define LOG_NDEBUG 0
#define LOG_TAG "MtkMPEG4ExtractorExt"

#include <ctype.h>
#include <inttypes.h>
#include <memory>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include <utils/Log.h>

#include "include/SampleTable.h"
#include "include/ItemTable.h"
#include "include/ESDS.h"

#include <media/stagefright/foundation/ABitReader.h>
#include <media/stagefright/foundation/ABuffer.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/AMessage.h>
#include <media/stagefright/foundation/AUtils.h>
#include <media/stagefright/foundation/ColorUtils.h>
#include <media/stagefright/foundation/hexdump.h>
#include <media/stagefright/MediaBuffer.h>
#include <media/stagefright/MediaBufferGroup.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MetaData.h>
#include <utils/String8.h>

#include <byteswap.h>
#include "include/ID3.h"
#include "include/avc_utils.h"

#include "include/MPEG4Extractor.h"

namespace android {

static int isAudioRaw(uint32_t fourcc)
{
    switch (fourcc) {
        case FOURCC('r', 'a', 'w', ' '):
        case FOURCC('t', 'w', 'o', 's'):
        case FOURCC('i', 'n', '2', '4'):
        case FOURCC('i', 'n', '3', '2'):
        case FOURCC('s', 'o', 'w', 't'):
            return 1;
        default:
            return 0;
    }
    return 0;
}

////////////////////////////////////////////////////////////////////////////////

status_t MPEG4Extractor::parseRawSampleEntry(const sp<DataSource> &source,
        Track *track, off64_t data_offset, int32_t chunk_type){

    if (!isAudioRaw(chunk_type)) {

        return NO_ERROR;
    }

    uint8_t buffer[8 + 20];
    if (source->readAt(
                data_offset, buffer, sizeof(buffer)) < (ssize_t)sizeof(buffer)) {
        return ERROR_IO;
    }

    //uint16_t data_ref_index __unused = U16_AT(&buffer[6]);
    uint16_t version = U16_AT(&buffer[8]);
    //uint32_t num_channels = U16_AT(&buffer[16]);

    uint16_t sample_size = U16_AT(&buffer[18]);
    //uint32_t sample_rate = U32_AT(&buffer[24]) >> 16;

    if (version == 1) {
        uint8_t buffer2[16];
        if (source->readAt(
                    data_offset+28, buffer2, sizeof(buffer2)) < (ssize_t)sizeof(buffer2)) {
            return ERROR_IO;
        }
        sample_size = U32_AT(&buffer2[4]) * 8;
    }
    track->meta->setInt32(kKeyBitWidth, sample_size);
    track->meta->setInt32(kKeyPCMType, 1); //pcm_wave
    if (chunk_type == FOURCC('r', 'a', 'w', ' ')) {
        ALOGI("raw box, unsigned");
        track->meta->setInt32(kKeyNumericalType, 2); // 1:signed 2:unsigned
    } else if (chunk_type == FOURCC('s', 'o', 'w', 't')) {
        ALOGI("sowt box");
        track->meta->setInt32(kKeyEndian, 2);
    } else if (chunk_type == FOURCC('t', 'w', 'o', 's')) {
        ALOGI("twos box");
        track->meta->setInt32(kKeyEndian, 1);
    } else if (chunk_type == FOURCC('i', 'n', '2', '4')) {
        ALOGI("in24 box");
        track->meta->setInt32(kKeyEndian, 2);
        if (version == 0) {
            track->skipTrack = true;
            ALOGD("warning:box in24 version 0 skip it");
        }
    } else if (chunk_type == FOURCC('i', 'n', '3', '2')) {
        ALOGI("in32 box");
        track->meta->setInt32(kKeyEndian, 2);
        if (version == 0) {
            track->skipTrack = true;
            ALOGD("warning:box in32 version 0 skip it");
        }
    }
    return NO_ERROR;
}

status_t MPEG4Extractor::adjustRawDefaultFrameSize(Track *track)
{
    int32_t pcmType=0,chanCount=0,bitWidth=0;
    const char *mimeStr = NULL;
    if(track->meta->findCString(kKeyMIMEType, &mimeStr) &&
            !strcasecmp(mimeStr, MEDIA_MIMETYPE_AUDIO_RAW)) {
        if (track->meta->findInt32(kKeyPCMType, &pcmType) &&
                (pcmType == 1)) {
            if(track->meta->findInt32(kKeyChannelCount, &chanCount) &&
                    track->meta->findInt32(kKeyBitWidth, &bitWidth)) {
                //samplesize in stsz may not right , so updade default samplesize
                track->sampleTable->setPredictSampleSize(chanCount*bitWidth/8);
            }
        }
    }

    return OK;
}

status_t MPEG4Extractor::adjustRawMaxFrameSize(Track *track, int frame_size)
{
    const char *mime;
    CHECK(track->meta->findCString(kKeyMIMEType, &mime));
    if (!strcmp(mime, MEDIA_MIMETYPE_AUDIO_RAW)) {
        ALOGI("set raw size to framesize %d", frame_size);
        track->meta->setInt32(kKeyMaxInputSize, frame_size);
    }

    return OK;
}

status_t MPEG4Extractor::parseALACSampleEntry(const sp<DataSource> &source,
        Track *track, off64_t offset){

    uint8_t buffer2[0x24];

    if (track == NULL) {
        return ERROR_MALFORMED;
    }

    if (source->readAt(
                offset, buffer2, sizeof(buffer2)) < (ssize_t)sizeof(buffer2)) {
        return ERROR_IO;
    }

    if (U32_AT(&buffer2[0]) == sizeof(buffer2)) {
        if (U32_AT(&buffer2[4]) != FOURCC('a', 'l', 'a', 'c')) {
            ALOGE("alac codec data section chunk_type != alac, LINE = %d", __LINE__);
            return ERROR_MALFORMED;
        }
        uint8_t alacBitWidth = 0, alacNumChannel = 0;
        uint32_t alacSampleRate = 0;
        alacBitWidth   = buffer2[17];
        alacNumChannel = buffer2[21];
        alacSampleRate = U32_AT(&buffer2[32]);
        track->meta->setInt32(kKeyBitWidth, alacBitWidth);
        track->meta->setInt32(kKeyChannelCount, alacNumChannel);
        track->meta->setInt32(kKeySampleRate, alacSampleRate);
        ALOGD("alac spec info, sample rate: %u, channel : %u, bit-width: %u",
                alacSampleRate, alacNumChannel, alacBitWidth);
        //-12 => skip [size] [alac] [0000]
        track->meta->setData(kKeyALACC, 0, buffer2+12, sizeof(buffer2)-12);
        return OK;
    } else {
        ALOGE("alac codec data section size != 0x24, LINE = %d", __LINE__);
        return ERROR_MALFORMED;
    }
}


status_t MPEG4Extractor::setCodecInfoFromFirstFrame(Track *track)
{
    status_t err = OK;

    if (track == NULL || track->sampleTable == NULL) {
        return ERROR_MALFORMED;
    }

    const char* mime;
    if (!track->meta->findCString(kKeyMIMEType, &mime)){
        ALOGE("No mime in track!!");
        return ERROR_MALFORMED;
    }
    if (strcmp(mime, MEDIA_MIMETYPE_AUDIO_MPEG) != 0){
        return ERROR_UNSUPPORTED;
    }

    uint8_t data[4] = {0};
    off64_t offset = 0;
    size_t  size = 0;
    int mach_count = 0;
    int sampleRate = 0;
    int channels = 0;
    for (int i=0; i < 4; i++) {
        err = track->sampleTable->getMetaDataForSample(i, &offset, &size, NULL, NULL);
        if (err != OK) {
            ALOGE("Fail to Get Frame Information");
            return err;
        }
        if (mDataSource->readAt(offset, data, 4) != 4) {
            ALOGE("read %d frame fail!!", i);
            return ERROR_IO;
        }
        uint32_t header = U32_AT(data);
        ALOGV("find header 0x%8x", header);
        size_t frame_size;
        int sample_rate = -1, num_channels = -1, bitrate;
        if (!GetMPEGAudioFrameSize(
                    header, &frame_size,
                    &sample_rate, &num_channels, &bitrate)) {
            continue;
        } else {
            if (sampleRate != sample_rate && channels != num_channels) {
                sampleRate = sample_rate;
                channels = num_channels;
                mach_count = 1;
            } else {
                mach_count++;
            }

        }
    }

    if (sampleRate > 0 &&  channels > 0 && mach_count > 1) {
        mLastTrack->meta->setInt32(kKeyChannelCount, channels);
        mLastTrack->meta->setInt32(kKeySampleRate, sampleRate);
        ALOGV("codec setting channel %d, sampleRate %d", channels, sampleRate);
        return OK;
    }

    return ERROR_MALFORMED;
}


}  // namespace android
