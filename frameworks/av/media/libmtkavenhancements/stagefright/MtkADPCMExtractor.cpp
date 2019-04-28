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
#define LOG_TAG "MtkADPCMExtractor"
#include <utils/Log.h>

#include "include/MtkADPCMExtractor.h"

#include <audio_utils/primitives.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/DataSource.h>
#include <media/stagefright/MediaBufferGroup.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MetaData.h>
#include <utils/String8.h>
#include <cutils/bitops.h>

#define CHANNEL_MASK_USE_CHANNEL_ORDER 0

namespace android {

enum {
    WAVE_FORMAT_PCM        = 0x0001,
    WAVE_FORMAT_IEEE_FLOAT = 0x0003,
    WAVE_FORMAT_ALAW       = 0x0006,
    WAVE_FORMAT_MULAW      = 0x0007,
    WAVE_FORMAT_MSGSM      = 0x0031,
    WAVE_FORMAT_EXTENSIBLE = 0xFFFE,
    WAVE_FORMAT_MSADPCM    = 0x0002,
    WAVE_FORMAT_DVI_IMAADCPM = 0x0011
};

static uint32_t U32_LE_AT(const uint8_t *ptr) {
    return ptr[3] << 24 | ptr[2] << 16 | ptr[1] << 8 | ptr[0];
}

static uint16_t U16_LE_AT(const uint8_t *ptr) {
    return ptr[1] << 8 | ptr[0];
}

struct MtkADPCMSource : public MediaSource {
    MtkADPCMSource(
            const sp<DataSource> &dataSource,
            const sp<MetaData> &meta,
            uint16_t waveFormat,
            int32_t bitsPerSample,
            off64_t offset, size_t size);

    virtual status_t start(MetaData *params = NULL);
    virtual status_t stop();
    virtual sp<MetaData> getFormat();

    virtual status_t read(
            MediaBuffer **buffer, const ReadOptions *options = NULL);

    virtual bool supportNonblockingRead() { return true; }

protected:
    virtual ~MtkADPCMSource();

private:
    static const size_t kMaxFrameSize;

    sp<DataSource> mDataSource;
    sp<MetaData> mMeta;
    uint16_t mWaveFormat;
    int32_t mSampleRate;
    int32_t mNumChannels;
    int32_t mBitsPerSample;
    off64_t mOffset;
    size_t mSize;
    bool mStarted;
    MediaBufferGroup *mGroup;
    off64_t mCurrentPos;

    int64_t mBlockDurationUs;
    int32_t mBlockAlign;

    MtkADPCMSource(const MtkADPCMSource &);
    MtkADPCMSource &operator=(const MtkADPCMSource &);
};

MtkADPCMExtractor::MtkADPCMExtractor(const sp<DataSource> &source)
    : mDataSource(source),
      mValidFormat(false),
      mChannelMask(CHANNEL_MASK_USE_CHANNEL_ORDER) {

      mAvgBytesPerSec = 0;
      mBlockAlign = 0;
      mExtraDataSize = 0;
      mpExtraData = NULL;
      mSamplesPerBlock = 0;
      mSamplesNumberPerChannel = 0;
      mBlockDurationUs = 0;

      mInitCheck = init();
}

MtkADPCMExtractor::~MtkADPCMExtractor() {
    if (NULL != mpExtraData) {
        free(mpExtraData);
        mpExtraData = NULL;
    }
}

sp<MetaData> MtkADPCMExtractor::getMetaData() {
    sp<MetaData> meta = new MetaData;

    if (mInitCheck != OK) {
        return meta;
    }

    meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_CONTAINER_ADPCM);

    return meta;
}

size_t MtkADPCMExtractor::countTracks() {
    return mInitCheck == OK ? 1 : 0;
}

sp<IMediaSource> MtkADPCMExtractor::getTrack(size_t index) {
    if (mInitCheck != OK || index > 0) {
        return NULL;
    }

    return new MtkADPCMSource(
            mDataSource, mTrackMeta,
            mWaveFormat, mBitsPerSample, mDataOffset, mDataSize);
}

sp<MetaData> MtkADPCMExtractor::getTrackMetaData(
        size_t index, uint32_t /* flags */) {
    if (mInitCheck != OK || index > 0) {
        return NULL;
    }

    return mTrackMeta;
}

status_t MtkADPCMExtractor::init() {
    uint8_t header[12];
    if (mDataSource->readAt(
                0, header, sizeof(header)) < (ssize_t)sizeof(header)) {
        return NO_INIT;
    }

    if (memcmp(header, "RIFF", 4) || memcmp(&header[8], "WAVE", 4)) {
        return NO_INIT;
    }

    size_t totalSize = U32_LE_AT(&header[4]);

    off64_t offset = 12;
    size_t remainingSize = totalSize;
    while (remainingSize >= 8) {
        uint8_t chunkHeader[8];
        if (mDataSource->readAt(offset, chunkHeader, 8) < 8) {
            return NO_INIT;
        }

        remainingSize -= 8;
        offset += 8;

        uint32_t chunkSize = U32_LE_AT(&chunkHeader[4]);

        if (chunkSize > remainingSize) {
            return NO_INIT;
        }

        if (!memcmp(chunkHeader, "fmt ", 4)) {
            if (chunkSize < 16) {
                return NO_INIT;
            }

            uint8_t formatSpec[40];
            if (mDataSource->readAt(offset, formatSpec, 2) < 2) {
                return NO_INIT;
            }

            mWaveFormat = U16_LE_AT(formatSpec);
            if (mWaveFormat != WAVE_FORMAT_MSADPCM
                    && mWaveFormat != WAVE_FORMAT_DVI_IMAADCPM) {
                return ERROR_UNSUPPORTED;
            }

            uint8_t fmtSize = 16;

            if (mDataSource->readAt(offset, formatSpec, fmtSize) < fmtSize) {
                return NO_INIT;
            }

            mNumChannels = U16_LE_AT(&formatSpec[2]);

            if (mNumChannels < 1 || mNumChannels > 8) {
                ALOGE("Unsupported number of channels (%d)", mNumChannels);
                return ERROR_UNSUPPORTED;
            }

            if (mWaveFormat != WAVE_FORMAT_EXTENSIBLE) {
                if (mNumChannels != 1 && mNumChannels != 2) {
                    ALOGW("More than 2 channels (%d) in non-WAVE_EXT, unknown channel mask",
                            mNumChannels);
                }
            }

            mSampleRate = U32_LE_AT(&formatSpec[4]);

            if (mSampleRate == 0) {
                return ERROR_MALFORMED;
            }

            ALOGV("mNumChannels is %d, mSampleRate is %u", mNumChannels, mSampleRate);

            mAvgBytesPerSec = U32_LE_AT(&formatSpec[8]);
            if (mAvgBytesPerSec <= 0) {
                return ERROR_MALFORMED;
            }

            mBlockAlign = U16_LE_AT(&formatSpec[12]);
            if (mBlockAlign <= 0) {
                return ERROR_MALFORMED;
            }

            if (mWaveFormat == WAVE_FORMAT_MSADPCM ||
                    mWaveFormat == WAVE_FORMAT_DVI_IMAADCPM) {
                ALOGV("mBlockAlign is %u", mBlockAlign);
            }

            mBitsPerSample = U16_LE_AT(&formatSpec[14]);

            if (mWaveFormat == WAVE_FORMAT_MSADPCM ||
                    mWaveFormat == WAVE_FORMAT_DVI_IMAADCPM) {
                uint8_t extraData[2];
                if (mDataSource->readAt(offset+16, extraData, 2) < 2) {
                    return NO_INIT;
                }

                mExtraDataSize = U16_LE_AT(extraData);
                if (mExtraDataSize < 2) {
                    return ERROR_MALFORMED;
                }

                mpExtraData = (uint8_t*)malloc(mExtraDataSize);
                if (NULL == mpExtraData) {
                    ALOGE("ADPCM malloc extraDataSize failed !!!");
                    return ERROR_IO;
                } else {
                    ALOGV("ADPCM mExtraDataSize is %u", mExtraDataSize);
                    uint32_t n = mDataSource->readAt(offset+18, mpExtraData, mExtraDataSize);
                    if (n < mExtraDataSize) {
                        return ERROR_MALFORMED;
                    }
                }
                mSamplesPerBlock = U16_LE_AT(mpExtraData);
            }

            if (mWaveFormat == WAVE_FORMAT_MSADPCM
                || mWaveFormat == WAVE_FORMAT_DVI_IMAADCPM) {
                if (mBitsPerSample != 4) {
                    return ERROR_UNSUPPORTED;
                }
            } else {
                return ERROR_UNSUPPORTED;
            }

            mValidFormat = true;
        } else if (!memcmp(chunkHeader, "fact", 4)) {
            if (chunkSize != 4) {
                ALOGD("fact chunk size is invailed, chunkSize is %u !!!", chunkSize);
            }
            uint8_t factChunkData[4];
            mDataSource->readAt(offset, factChunkData, 4);

            mSamplesNumberPerChannel = U32_LE_AT(factChunkData);
            ALOGV("fact chunk ChannelCount is %d, SamplesNumberPerChannel is %u, SamplesPerBlock is %u",
                          mNumChannels, mSamplesNumberPerChannel, mSamplesPerBlock);
        } else if (!memcmp(chunkHeader, "data", 4)) {
            if (mValidFormat) {
                mDataOffset = offset;
                mDataSize = chunkSize;

                mTrackMeta = new MetaData;

                switch (mWaveFormat) {
                    case WAVE_FORMAT_MSADPCM:
                        mTrackMeta->setCString(
                                kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_MS_ADPCM);
                        break;
                    case WAVE_FORMAT_DVI_IMAADCPM:
                        mTrackMeta->setCString(
                                kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_DVI_IMA_ADPCM);
                        break;
                    default:
                        CHECK_EQ(mWaveFormat, (uint16_t)WAVE_FORMAT_MULAW);
                        mTrackMeta->setCString(
                                kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_G711_MLAW);
                        break;
                }

                mTrackMeta->setInt32(kKeyChannelCount, mNumChannels);
                mTrackMeta->setInt32(kKeyChannelMask, mChannelMask);
                mTrackMeta->setInt32(kKeySampleRate, mSampleRate);
                mTrackMeta->setInt32(kKeyPcmEncoding, kAudioEncodingPcm16bit);

                ALOGV("set value for metaData !!!");
                mTrackMeta->setInt32(kKeyBlockAlign, mBlockAlign);
                mTrackMeta->setInt32(kKeyBitsPerSample, (uint32_t)mBitsPerSample);
                if (NULL != mpExtraData) {
                    mTrackMeta->setData(kKeyExtraDataPointer, 0, mpExtraData, mExtraDataSize);
                } else {
                    ALOGV("mpExtraData pointer is NULL !!!");
                }

                int64_t durationUs = 0;

                ALOGV("set duration value for metaData !!!");
                ALOGV("mSamplesPerBlock %u, mSampleRate %u, mDataSize %zu, mBlockAlign %u",
                        mSamplesPerBlock, mSampleRate, mDataSize, mBlockAlign);
                        mBlockDurationUs = 1000000LL * mSamplesPerBlock / mSampleRate;
                        durationUs = (mDataSize / mBlockAlign) * mBlockDurationUs;
                        ALOGV("mBlockDurationUs is %.2f secs, durationUs is %.2f secs",
                                   mBlockDurationUs / 1E6, durationUs / 1E6);

                mTrackMeta->setInt64(kKeyDuration, durationUs);
                mTrackMeta->setInt64(kKeyBlockDurationUs, mBlockDurationUs);

                return OK;
            }
        }

        offset += chunkSize;
    }

    return NO_INIT;
}

const size_t MtkADPCMSource::kMaxFrameSize = 32768;

MtkADPCMSource::MtkADPCMSource(
        const sp<DataSource> &dataSource,
        const sp<MetaData> &meta,
        uint16_t waveFormat,
        int32_t bitsPerSample,
        off64_t offset, size_t size)
    : mDataSource(dataSource),
      mMeta(meta),
      mWaveFormat(waveFormat),
      mSampleRate(0),
      mNumChannels(0),
      mBitsPerSample(bitsPerSample),
      mOffset(offset),
      mSize(size),
      mStarted(false),
      mGroup(NULL) {
    mBlockDurationUs = 0;
    mBlockAlign = 0;

    CHECK(mMeta->findInt32(kKeySampleRate, &mSampleRate));
    CHECK(mMeta->findInt32(kKeyChannelCount, &mNumChannels));

    mMeta->setInt32(kKeyMaxInputSize, kMaxFrameSize);

    CHECK(mMeta->findInt64(kKeyBlockDurationUs, &mBlockDurationUs));
    CHECK(mMeta->findInt32(kKeyBlockAlign, &mBlockAlign));
    ALOGV("mSize is %zu, mBlockDurationUs %lld, mBlockAlign %d",
                    mSize, (long long)mBlockDurationUs, mBlockAlign);
    if (mWaveFormat == WAVE_FORMAT_MSADPCM ||
        mWaveFormat == WAVE_FORMAT_DVI_IMAADCPM) {
        mMeta->setInt32(kKeyMaxInputSize, kMaxFrameSize / 4);
    }
}

MtkADPCMSource::~MtkADPCMSource() {
    if (mStarted) {
        stop();
    }
}

status_t MtkADPCMSource::start(MetaData * /* params */) {
    ALOGV("MtkADPCMSource::start");

    CHECK(!mStarted);

    // some WAV files may have large audio buffers that use shared memory transfer.
    mGroup = new MediaBufferGroup(4 /* buffers */, kMaxFrameSize);

    if (mBitsPerSample == 8) {
        // As a temporary buffer for 8->16 bit conversion.
        mGroup->add_buffer(new MediaBuffer(kMaxFrameSize));
    }

    mCurrentPos = mOffset;

    mStarted = true;

    return OK;
}

status_t MtkADPCMSource::stop() {
    ALOGV("MtkADPCMSource::stop");

    CHECK(mStarted);

    delete mGroup;
    mGroup = NULL;

    mStarted = false;

    return OK;
}

sp<MetaData> MtkADPCMSource::getFormat() {
    ALOGV("MtkADPCMSource::getFormat");

    return mMeta;
}

status_t MtkADPCMSource::read(
        MediaBuffer **out, const ReadOptions *options) {
    *out = NULL;

    if (options != nullptr && options->getNonBlocking() && !mGroup->has_buffers()) {
        return WOULD_BLOCK;
    }

    int64_t seekTimeUs;
    ReadOptions::SeekMode mode;
    if (options != NULL && options->getSeekTo(&seekTimeUs, &mode)) {
        int64_t pos = 0;

        pos = (seekTimeUs - (int64_t)(mBlockDurationUs >> 1)) / mBlockDurationUs * mBlockAlign;
        if (pos < 0) {
            pos = 0;
        }
        ALOGV("ADPCM seekTimeUs is %.2f secs", seekTimeUs / 1E6);
        ALOGV("ADPCM mOffset %llu, pos %lld", (unsigned long long)mOffset, (long long)pos);

        if (pos > (off64_t)mSize) {
            pos = mSize;
        }
        mCurrentPos = pos + mOffset;
    }

    MediaBuffer *buffer;
    status_t err = mGroup->acquire_buffer(&buffer);
    if (err != OK) {
        return err;
    }

    // make sure that maxBytesToRead is multiple of 3, in 24-bit case
    size_t maxBytesToRead =
        mBitsPerSample == 8 ? kMaxFrameSize / 2 :
        (mBitsPerSample == 24 ? 3*(kMaxFrameSize/3): kMaxFrameSize);

    if (mWaveFormat == WAVE_FORMAT_MSADPCM ||
        mWaveFormat == WAVE_FORMAT_DVI_IMAADCPM) {
        maxBytesToRead = (kMaxFrameSize / 4 / mBlockAlign) * mBlockAlign; // divide 4 to decrease component output buffer size
    }

    size_t maxBytesAvailable =
        (mCurrentPos - mOffset >= (off64_t)mSize)
            ? 0 : mSize - (mCurrentPos - mOffset);

    if (maxBytesToRead > maxBytesAvailable) {
        maxBytesToRead = maxBytesAvailable;
        if (mWaveFormat == WAVE_FORMAT_MSADPCM ||
            mWaveFormat == WAVE_FORMAT_DVI_IMAADCPM) {
            maxBytesToRead = (maxBytesToRead / mBlockAlign) * mBlockAlign;
        }
    }

    // read only integral amounts of audio unit frames.
    const size_t inputUnitFrameSize = mNumChannels * mBitsPerSample / 8;
    if (0 != inputUnitFrameSize) {
        maxBytesToRead -= maxBytesToRead % inputUnitFrameSize;
    }

    ssize_t n = mDataSource->readAt(
            mCurrentPos, buffer->data(),
            maxBytesToRead);

    if (n <= 0) {
        buffer->release();
        buffer = NULL;

        return ERROR_END_OF_STREAM;
    }

    buffer->set_range(0, n);

    ALOGV("======ADPCM Data pass MTK ADPCM Component !======");

    if (mWaveFormat == WAVE_FORMAT_MSADPCM
           || mWaveFormat == WAVE_FORMAT_DVI_IMAADCPM) {
        ALOGV("ADPCM timestamp of this buffer, mBlockAlign is %d, mBlockDurationUs is %lld +++",
                       mBlockAlign,  (long long)mBlockDurationUs);
        int64_t keyTimeUs = ((mCurrentPos - mOffset) / mBlockAlign) * mBlockDurationUs;
        buffer->meta_data()->setInt64(kKeyTime, keyTimeUs);
        ALOGV("ADPCM timestamp of this buffer is %.2f secs, buffer length is %zd", keyTimeUs / 1E6, n);
    }

    buffer->meta_data()->setInt32(kKeyIsSyncFrame, 1);
    mCurrentPos += n;

    *out = buffer;

    return OK;
}

////////////////////////////////////////////////////////////////////////////////

bool SniffADPCM(
        const sp<DataSource> &source, String8 *mimeType, float *confidence,
        sp<AMessage> *) {
    char header[12];
    if (source->readAt(0, header, sizeof(header)) < (ssize_t)sizeof(header)) {
        return false;
    }

    if (memcmp(header, "RIFF", 4) || memcmp(&header[8], "WAVE", 4)) {
        return false;
    }

    sp<MediaExtractor> extractor = new MtkADPCMExtractor(source);
    if (extractor->countTracks() == 0) {
        return false;
    }

    *mimeType = MEDIA_MIMETYPE_CONTAINER_ADPCM;
    *confidence = 0.3f;

    return true;
}

}  // namespace android
