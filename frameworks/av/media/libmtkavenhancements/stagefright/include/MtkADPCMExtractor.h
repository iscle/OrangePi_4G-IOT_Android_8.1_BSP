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

#ifndef ADPCM_EXTRACTOR_H_

#define ADPCM_EXTRACTOR_H_

#include <utils/Errors.h>
#include <media/stagefright/MediaExtractor.h>

namespace android {

struct AMessage;
class DataSource;
class String8;

class MtkADPCMExtractor : public MediaExtractor {
public:
    // Extractor assumes ownership of "source".
    explicit MtkADPCMExtractor(const sp<DataSource> &source);

    virtual size_t countTracks();
    virtual sp<IMediaSource> getTrack(size_t index);
    virtual sp<MetaData> getTrackMetaData(size_t index, uint32_t flags);

    virtual sp<MetaData> getMetaData();
    virtual const char * name() { return "MtkADPCMExtractor"; }

protected:
    virtual ~MtkADPCMExtractor();

private:
    sp<DataSource> mDataSource;
    status_t mInitCheck;
    bool mValidFormat;
    uint16_t mWaveFormat;
    uint16_t mNumChannels;
    uint32_t mChannelMask;
    uint32_t mSampleRate;
    uint16_t mBitsPerSample;
    off64_t mDataOffset;
    size_t mDataSize;
    sp<MetaData> mTrackMeta;

    status_t init();

// #ifdef MTK_AOSP_ENHANCEMENT
// #ifdef MTK_AUDIO_ADPCM_SUPPORT
    uint32_t mAvgBytesPerSec;
    uint32_t mBlockAlign;
    uint32_t mExtraDataSize;
    uint8_t* mpExtraData;
    uint32_t mSamplesPerBlock;
    uint32_t mSamplesNumberPerChannel;
    uint64_t mBlockDurationUs;
//#endif
//#endif

    MtkADPCMExtractor(const MtkADPCMExtractor &);
    MtkADPCMExtractor &operator=(const MtkADPCMExtractor &);
};

bool SniffADPCM(
        const sp<DataSource> &source, String8 *mimeType, float *confidence,
        sp<AMessage> *);

}  // namespace android

#endif  // ADPCM_EXTRACTOR_H_

