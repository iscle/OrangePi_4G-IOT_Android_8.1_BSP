/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2010 The Android Open Source Project
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
#define LOG_TAG "MtkMatroskaExtractor"
#include <utils/Log.h>

#include "FLACDecoder.h"
#include "include/MtkMatroskaExtractor.h"
#include "avc_utils.h"

#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/AUtils.h>
#include <media/stagefright/foundation/ABuffer.h>
#include <media/stagefright/foundation/ColorUtils.h>
#include <media/stagefright/foundation/hexdump.h>
#include <media/stagefright/DataSource.h>
#include <media/stagefright/MediaBuffer.h>
#include <media/stagefright/MediaDefs.h>

#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MetaData.h>
#include <media/stagefright/Utils.h>
#include <utils/String8.h>

#include <inttypes.h>

// add for mtk
#include <cutils/properties.h>

// big endian fourcc
#define BFOURCC(c1, c2, c3, c4) \
    (c4 << 24 | c3 << 16 | c2 << 8 | c1)
// end of add for mtk

namespace android {
// add for mtk
#ifdef CONFIG_MT_ENG_BUILD  // for log reduction
bool MKVLogOpen = true;
#else
bool MKVLogOpen = false;
#endif

#define MKV_RIFF_WAVE_FORMAT_PCM            (0x0001)
#define MKV_RIFF_WAVE_FORMAT_ALAW           (0x0006)
#define MKV_RIFF_WAVE_FORMAT_ADPCM_ms       (0x0002)
#define MKV_RIFF_WAVE_FORMAT_ADPCM_ima_wav  (0x0011)
#define MKV_RIFF_WAVE_FORMAT_MULAW          (0x0007)
#define MKV_RIFF_WAVE_FORMAT_MPEGL12        (0x0050)
#define MKV_RIFF_WAVE_FORMAT_MPEGL3         (0x0055)
#define MKV_RIFF_WAVE_FORMAT_AMR_NB         (0x0057)
#define MKV_RIFF_WAVE_FORMAT_AMR_WB         (0x0058)
#define MKV_RIFF_WAVE_FORMAT_AAC            (0x00ff)
#define MKV_RIFF_IBM_FORMAT_MULAW           (0x0101)
#define MKV_RIFF_IBM_FORMAT_ALAW            (0x0102)
#define MKV_RIFF_WAVE_FORMAT_WMAV1          (0x0160)
#define MKV_RIFF_WAVE_FORMAT_WMAV2          (0x0161)
#define MKV_RIFF_WAVE_FORMAT_WMAV3          (0x0162)
#define MKV_RIFF_WAVE_FORMAT_WMAV3_L        (0x0163)
#define MKV_RIFF_WAVE_FORMAT_AAC_AC         (0x4143)
#define MKV_RIFF_WAVE_FORMAT_VORBIS         (0x566f)
#define MKV_RIFF_WAVE_FORMAT_VORBIS1        (0x674f)
#define MKV_RIFF_WAVE_FORMAT_VORBIS2        (0x6750)
#define MKV_RIFF_WAVE_FORMAT_VORBIS3        (0x6751)
#define MKV_RIFF_WAVE_FORMAT_VORBIS1PLUS    (0x676f)
#define MKV_RIFF_WAVE_FORMAT_VORBIS2PLUS    (0x6770)
#define MKV_RIFF_WAVE_FORMAT_VORBIS3PLUS    (0x6771)
#define MKV_RIFF_WAVE_FORMAT_AAC_pm         (0x706d)
#define MKV_RIFF_WAVE_FORMAT_GSM_AMR_CBR    (0x7A21)
#define MKV_RIFF_WAVE_FORMAT_GSM_AMR_VBR    (0x7A22)

static const uint32_t kMP3HeaderMask = 0xfffe0c00;  // 0xfffe0c00 add by zhihui zhang no consider channel mode
static const char *MKVwave2MIME(uint16_t id) {
    switch (id) {
        case  MKV_RIFF_WAVE_FORMAT_AMR_NB:
        case  MKV_RIFF_WAVE_FORMAT_GSM_AMR_CBR:
        case  MKV_RIFF_WAVE_FORMAT_GSM_AMR_VBR:
            return MEDIA_MIMETYPE_AUDIO_AMR_NB;

        case  MKV_RIFF_WAVE_FORMAT_AMR_WB:
            return MEDIA_MIMETYPE_AUDIO_AMR_WB;

        case  MKV_RIFF_WAVE_FORMAT_AAC:
        case  MKV_RIFF_WAVE_FORMAT_AAC_AC:
        case  MKV_RIFF_WAVE_FORMAT_AAC_pm:
            return MEDIA_MIMETYPE_AUDIO_AAC;

        case  MKV_RIFF_WAVE_FORMAT_VORBIS:
        case  MKV_RIFF_WAVE_FORMAT_VORBIS1:
        case  MKV_RIFF_WAVE_FORMAT_VORBIS2:
        case  MKV_RIFF_WAVE_FORMAT_VORBIS3:
        case  MKV_RIFF_WAVE_FORMAT_VORBIS1PLUS:
        case  MKV_RIFF_WAVE_FORMAT_VORBIS2PLUS:
        case  MKV_RIFF_WAVE_FORMAT_VORBIS3PLUS:
            return MEDIA_MIMETYPE_AUDIO_VORBIS;

        case  MKV_RIFF_WAVE_FORMAT_MPEGL12:
            return MEDIA_MIMETYPE_AUDIO_MPEG_LAYER_II;

        case  MKV_RIFF_WAVE_FORMAT_MPEGL3:
            return MEDIA_MIMETYPE_AUDIO_MPEG;

        case MKV_RIFF_WAVE_FORMAT_MULAW:
        case MKV_RIFF_IBM_FORMAT_MULAW:
            return MEDIA_MIMETYPE_AUDIO_G711_MLAW;

        case MKV_RIFF_WAVE_FORMAT_ALAW:
        case MKV_RIFF_IBM_FORMAT_ALAW:
            return MEDIA_MIMETYPE_AUDIO_G711_ALAW;

        case MKV_RIFF_WAVE_FORMAT_PCM:
            return MEDIA_MIMETYPE_AUDIO_RAW;
#if defined(MTK_AUDIO_ADPCM_SUPPORT) || defined(HAVE_ADPCMENCODE_FEATURE)
        case MKV_RIFF_WAVE_FORMAT_ADPCM_ms:
            return MEDIA_MIMETYPE_AUDIO_MS_ADPCM;
        case MKV_RIFF_WAVE_FORMAT_ADPCM_ima_wav:
            return MEDIA_MIMETYPE_AUDIO_DVI_IMA_ADPCM;
#endif
        case MKV_RIFF_WAVE_FORMAT_WMAV1:
            return MEDIA_MIMETYPE_AUDIO_WMA;
        case MKV_RIFF_WAVE_FORMAT_WMAV2:
            return MEDIA_MIMETYPE_AUDIO_WMA;
        default:
            ALOGW("unknown wave %x", id);
            return "";
    };
}

static const uint32_t AACSampleFreqTable[16] = {
    96000, /* 96000 Hz */
    88200, /* 88200 Hz */
    64000, /* 64000 Hz */
    48000, /* 48000 Hz */
    44100, /* 44100 Hz */
    32000, /* 32000 Hz */
    24000, /* 24000 Hz */
    22050, /* 22050 Hz */
    16000, /* 16000 Hz */
    12000, /* 12000 Hz */
    11025, /* 11025 Hz */
    8000, /*  8000 Hz */
    0, /* future use */
    0, /* future use */
    0, /* future use */
    0  /* escape value */
};

static bool findAACSampleFreqIndex(uint32_t freq, uint8_t *index) {
    uint8_t i;
    uint8_t num = sizeof(AACSampleFreqTable) / sizeof(AACSampleFreqTable[0]);
    for (i = 0; i < num; i++) {
        if (freq == AACSampleFreqTable[i])
            break;
    }
    if (i > 11)
        return false;

    *index = i;
    return true;
}

static uint8_t charLower(uint8_t ch) {
    uint8_t ch_out = ch;
    if (ch >= 'A' && ch <= 'Z')
        ch_out = ch + 32;
    return ch_out;
}

/* trans all FOURCC  to lower char */
static uint32_t FourCCtoLower(uint32_t fourcc) {
    uint8_t ch_1 = (uint8_t)charLower(fourcc >> 24);
    uint8_t ch_2 = (uint8_t)charLower(fourcc >> 16);
    uint8_t ch_3 = (uint8_t)charLower(fourcc >> 8);
    uint8_t ch_4 = (uint8_t)charLower(fourcc);
    uint32_t fourcc_out = ch_1 << 24 | ch_2 << 16 | ch_3 << 8 | ch_4;

    return fourcc_out;
}

static const char *BMKVFourCC2MIME(uint32_t fourcc) {
    ALOGV("BMKVFourCC2MIME fourcc 0x%8.8x", fourcc);
    uint32_t lowerFourcc = FourCCtoLower(fourcc);
    ALOGV("BMKVFourCC2MIME fourcc to lower 0x%8.8x", lowerFourcc);
    switch (lowerFourcc) {
        case BFOURCC('m', 'p', '4', 'a'):
            return MEDIA_MIMETYPE_AUDIO_AAC;

        case BFOURCC('s', 'a', 'm', 'r'):
            return MEDIA_MIMETYPE_AUDIO_AMR_NB;

        case BFOURCC('s', 'a', 'w', 'b'):
            return MEDIA_MIMETYPE_AUDIO_AMR_WB;

        case BFOURCC('x', 'v', 'i', 'd'):
            return MEDIA_MIMETYPE_VIDEO_XVID;
        case BFOURCC('d', 'i', 'v', 'x'):
        case BFOURCC('d', 'x', '5', '0'):
            return MEDIA_MIMETYPE_VIDEO_DIVX;
        case BFOURCC('m', 'p', '4', 'v'):
            return MEDIA_MIMETYPE_VIDEO_MPEG4;

        case BFOURCC('d', 'i', 'v', '3'):
        case BFOURCC('d', 'i', 'v', '4'):
            return MEDIA_MIMETYPE_VIDEO_DIVX3;

        case BFOURCC('s', '2', '6', '3'):
        case BFOURCC('h', '2', '6', '3'):
            return MEDIA_MIMETYPE_VIDEO_H263;

        case BFOURCC('a', 'v', 'c', '1'):
        case BFOURCC('h', '2', '6', '4'):
            return MEDIA_MIMETYPE_VIDEO_AVC;

        case BFOURCC('m', 'p', 'g', '2'):
            return MEDIA_MIMETYPE_VIDEO_MPEG2;
        case BFOURCC('m', 'j', 'p', 'g'):
        case BFOURCC('m', 'p', 'p', 'g'):
            return MEDIA_MIMETYPE_VIDEO_MJPEG;

                case FOURCC('h', 'v', 'c', '1'):
        case BFOURCC('h', 'e', 'v', 'c'):
        case FOURCC('h', 'e', 'v', '1'):
            return MEDIA_MIMETYPE_VIDEO_HEVC;

        default:
            ALOGW("unknown fourcc 0x%8.8x", fourcc);
            return "";
    }
}

static bool get_mp3_info(
        uint32_t header, size_t *frame_size,
        int *out_sampling_rate = NULL, int *out_channels = NULL,
        int *out_bitrate = NULL) {
    *frame_size = 0;

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
        ALOGD("line=%d", __LINE__);
        return false;
    }

    unsigned version = (header >> 19) & 3;
    if (version == 0x01) {
        ALOGD("line=%d", __LINE__);
        return false;
    }

    unsigned layer = (header >> 17) & 3;
    if (layer == 0x00) {
        ALOGD("line=%d", __LINE__);
        return false;
    }

    unsigned bitrate_index = (header >> 12) & 0x0f;
    if (bitrate_index == 0 || bitrate_index == 0x0f) {
        // Disallow "free" bitrate.
        ALOGD("line=%d", __LINE__);
        return false;
    }

    unsigned sampling_rate_index = (header >> 10) & 3;
    if (sampling_rate_index == 3) {
        ALOGD("line=%d", __LINE__);
        return false;
    }

    static const int kSamplingRateV1[] = { 44100, 48000, 32000 };
    int sampling_rate = kSamplingRateV1[sampling_rate_index];
    if (version == 2 /* V2 */) {
        sampling_rate /= 2;
    } else if (version == 0 /* V2.5 */) {
        sampling_rate /= 4;
    }

    unsigned padding = (header >> 9) & 1;
    if (layer == 3) {        // layer I
        static const int kBitrateV1[] = {
            32, 64, 96, 128, 160, 192, 224, 256,
            288, 320, 352, 384, 416, 448
        };

        static const int kBitrateV2[] = {
            32, 48, 56, 64, 80, 96, 112, 128,
            144, 160, 176, 192, 224, 256
        };

        int bitrate =
            (version == 3 /* V1 */)
                ? kBitrateV1[bitrate_index - 1]
                : kBitrateV2[bitrate_index - 1];

        if (out_bitrate) {
            *out_bitrate = bitrate;
        }

        *frame_size = (12000 * bitrate / sampling_rate + padding) * 4;
    } else {
        // layer II or III
        static const int kBitrateV1L2[] = {
            32, 48, 56, 64, 80, 96, 112, 128,
            160, 192, 224, 256, 320, 384
        };

        static const int kBitrateV1L3[] = {
            32, 40, 48, 56, 64, 80, 96, 112,
            128, 160, 192, 224, 256, 320
        };

        static const int kBitrateV2[] = {
            8, 16, 24, 32, 40, 48, 56, 64,
            80, 96, 112, 128, 144, 160
        };

        int bitrate;
        if (version == 3 /* V1 */) {
            bitrate = (layer == 2 /* L2 */)
                ? kBitrateV1L2[bitrate_index - 1]
                : kBitrateV1L3[bitrate_index - 1];
        } else {            // V2 (or 2.5)
            bitrate = kBitrateV2[bitrate_index - 1];
        }

        if (out_bitrate) {
            *out_bitrate = bitrate;
        }

        if (version == 3 /* V1 */) {
            *frame_size = 144000 * bitrate / sampling_rate + padding;
        } else {            // V2 or V2.5
            if (layer == 2 /* L2 */) {
                    *frame_size = 144000 * bitrate / sampling_rate + padding;
            } else {
                *frame_size = 72000 * bitrate / sampling_rate + padding;
            }
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

static int mkv_mp3HeaderStartAt(const uint8_t *start, int length, uint32_t header) {
    uint32_t code = 0;
    int i = 0;

    for (i = 0; i < length; i++) {        // ALOGD("start[%d]=%x", i, start[i]);
        code = (code << 8) + start[i];        // ALOGD("code=0x%8.8x, mask=0x%8.8x", code, kMP3HeaderMask);
        if ((code & kMP3HeaderMask) == (header & kMP3HeaderMask)) {            // some files has no seq start code
            return i - 3;
        }
    }
    return -1;
}
// end of add for mtk

struct MtkDataSourceReader : public mkvparser::IMkvReader {
    explicit MtkDataSourceReader(const sp<DataSource> &source)
        : mSource(source) {
    }

    virtual int Read(long long position, long length, unsigned char* buffer) {
        CHECK(position >= 0);
        CHECK(length >= 0);

        if (length == 0) {
            return 0;
        }

        ssize_t n = mSource->readAt(position, buffer, length);

        if (n <= 0) {
            ALOGE("readAt %zd bytes, Read return -1\nposition= %lld, length= %ld", n, position, length);
            return -1;
        }

        return 0;
    }

    virtual int Length(long long* total, long long* available) {
        off64_t size;
        if (mSource->getSize(&size) != OK) {
            *total = -1;
            *available = (long long)((1ull << 63) - 1);

            return 0;
        }

        if (total) {
            *total = size;
        }

        if (available) {
            *available = size;
        }

        return 0;
    }

private:
    sp<DataSource> mSource;

    MtkDataSourceReader(const MtkDataSourceReader &);
    MtkDataSourceReader &operator=(const MtkDataSourceReader &);
};

////////////////////////////////////////////////////////////////////////////////

struct MtkBlockIterator {
    MtkBlockIterator(MtkMatroskaExtractor *extractor, unsigned long trackNum, unsigned long index);

    bool eos() const;

    void advance();
    void reset();

    void seek(
            int64_t seekTimeUs, bool isAudio,
            int64_t *actualFrameTimeUs);

    const mkvparser::Block *block() const;
    int64_t blockTimeUs() const;

private:
    MtkMatroskaExtractor *mExtractor;
    long long mTrackNum;
    unsigned long mIndex;

    const mkvparser::Cluster *mCluster;
    const mkvparser::BlockEntry *mBlockEntry;
    long mBlockEntryIndex;
// add for mtk
    unsigned long mTrackType;
    void seekwithoutcue(int64_t seekTimeUs);
// end of add for mtk

    void advance_l();

    MtkBlockIterator(const MtkBlockIterator &);
    MtkBlockIterator &operator=(const MtkBlockIterator &);
};

struct MtkMatroskaSource : public MediaSource {
    MtkMatroskaSource(
            const sp<MtkMatroskaExtractor> &extractor, size_t index);

    virtual status_t start(MetaData *params);
    virtual status_t stop();

    virtual sp<MetaData> getFormat();

    virtual status_t read(
            MediaBuffer **buffer, const ReadOptions *options);

protected:
    virtual ~MtkMatroskaSource();

private:
    enum Type {
        AVC,
        AAC,
// add for mtk, mtk added codec types
        VP8,
        VP9,
        VORBIS,
        MPEG4,
        MPEG2,
        MP2_3,
        MJPEG,
        HEVC,
// end of add for mtk
        OTHER
    };

    sp<MtkMatroskaExtractor> mExtractor;
    size_t mTrackIndex;
    Type mType;
    bool mIsAudio;
    MtkBlockIterator mBlockIter;
    ssize_t mNALSizeLen;  // for type AVC

    List<MediaBuffer *> mPendingFrames;

    status_t advance();

    status_t setWebmBlockCryptoInfo(MediaBuffer *mbuf);
    status_t readBlock();
    void clearPendingFrames();

    MtkMatroskaSource(const MtkMatroskaSource &);
    MtkMatroskaSource &operator=(const MtkMatroskaSource &);

// add for mtk
    status_t findMP3Header(uint32_t *header);
    MtkMatroskaExtractor::TrackInfo *mTrackInfo;
    int64_t mCurrentTS;
    bool mFirstFrame;
    uint32_t mMP3Header;
    bool mIsFromFFmpeg;

public:
    void setCodecInfoFromFirstFrame();
// end of add for mtk
};

const mkvparser::Track* MtkMatroskaExtractor::TrackInfo::getTrack() const {
    return mExtractor->mSegment->GetTracks()->GetTrackByNumber(mTrackNum);
}

// This function does exactly the same as mkvparser::Cues::Find, except that it
// searches in our own track based vectors. We should not need this once mkvparser
// adds the same functionality.
const mkvparser::CuePoint::TrackPosition *MtkMatroskaExtractor::TrackInfo::find(
        long long timeNs) const {
    ALOGV("mCuePoints.size %zu", mCuePoints.size());
    if (mCuePoints.empty()) {
        return NULL;
    }

    const mkvparser::CuePoint* cp = mCuePoints.itemAt(0);
    const mkvparser::Track* track = getTrack();
    if (timeNs <= cp->GetTime(mExtractor->mSegment)) {
        return cp->Find(track);
    }

    // Binary searches through relevant cues; assumes cues are ordered by timecode.
    // If we do detect out-of-order cues, return NULL.
    size_t lo = 0;
    size_t hi = mCuePoints.size();
    while (lo < hi) {
        const size_t mid = lo + (hi - lo) / 2;
        const mkvparser::CuePoint* const midCp = mCuePoints.itemAt(mid);
        const long long cueTimeNs = midCp->GetTime(mExtractor->mSegment);
        if (cueTimeNs <= timeNs) {
            lo = mid + 1;
        } else {
            hi = mid;
        }
    }

    if (lo == 0) {
        return NULL;
    }

    cp = mCuePoints.itemAt(lo - 1);
    if (cp->GetTime(mExtractor->mSegment) > timeNs) {
        return NULL;
    }

    return cp->Find(track);
}

MtkMatroskaSource::MtkMatroskaSource(
        const sp<MtkMatroskaExtractor> &extractor, size_t index)
    : mExtractor(extractor),
      mTrackIndex(index),
      mType(OTHER),
      mIsAudio(false),
      mBlockIter(mExtractor.get(),
                 mExtractor->mTracks.itemAt(index).mTrackNum,
                 index),
      mNALSizeLen(-1) {
// add for mtk
    mTrackInfo = NULL;
    mTrackInfo = &mExtractor->mTracks.editItemAt(mTrackIndex);
    mCurrentTS = 0;
    mFirstFrame = true;
    mMP3Header = 0;
    // check whether is ffmeg video with codecID of  V_MS/VFW/FOURCC
    mIsFromFFmpeg = false;
    const char * CodecId = (mTrackInfo->getTrack())->GetCodecId();
    if (!strcmp("V_MS/VFW/FOURCC", CodecId))
        mIsFromFFmpeg = true;
// end of add for mtk
    sp<MetaData> meta = mExtractor->mTracks.itemAt(index).mMeta;

    const char *mime;
    CHECK(meta->findCString(kKeyMIMEType, &mime));

    mIsAudio = !strncasecmp("audio/", mime, 6);

    if (!strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_AVC)) {
        mType = AVC;

        uint32_t dummy;
        const uint8_t *avcc;
        size_t avccSize;
        int32_t nalSizeLen = 0;
// add for mtk
        if (!meta->findData(kKeyAVCC, &dummy, (const void **)&avcc, &avccSize)) {
            sp<MetaData> metadata = NULL;
            while (metadata == NULL) {
                clearPendingFrames();
                while (mPendingFrames.empty()) {
                    status_t err = readBlock();

                    if (err != OK) {
                        clearPendingFrames();
                        break;
                    }
                }

                if (!mPendingFrames.empty()) {
                    MediaBuffer *buffer = *mPendingFrames.begin();
                    sp < ABuffer >  accessUnit = new ABuffer(buffer->range_length());
                    ALOGV("bigbuf->range_length() = %zu", buffer->range_length());
                    memcpy(accessUnit->data(), buffer->data(), buffer->range_length());
                    metadata = MakeAVCCodecSpecificData(accessUnit);
                }
            }
            CHECK(metadata->findData(kKeyAVCC, &dummy, (const void **)&avcc, &avccSize));
            ALOGV("avccSize = %zu ", avccSize);
            CHECK_GE(avccSize, 5u);
            meta->setData(kKeyAVCC, 0, avcc, avccSize);
            mBlockIter.reset();
            clearPendingFrames();
        }
// end of add for mtk
        if (meta->findInt32(kKeyNalLengthSize, &nalSizeLen)) {
            if (nalSizeLen >= 0 && nalSizeLen <= 4) {
                mNALSizeLen = nalSizeLen;
            }
        } else if (meta->findData(kKeyAVCC, &dummy, (const void **)&avcc, &avccSize)
                && avccSize >= 5u) {
            mNALSizeLen = 1 + (avcc[4] & 3);
            ALOGV("mNALSizeLen = %zd", mNALSizeLen);
        } else {
            ALOGE("No mNALSizeLen");
        }
    } else if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_AAC)) {
        mType = AAC;
    }
// add for mtk
    else if (!strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_VPX)) {
        mType = VP8;
    } else if (!strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_VP9)) {
        mType = VP9;
    } else if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_VORBIS)) {
        mType = VORBIS;
    } else if (!strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_MPEG4)) {
        mType = MPEG4;
    } else if (!strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_XVID)) {
        mType = MPEG4;
    } else if (!strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_DIVX)) {
        mType = MPEG4;
    } else if (!strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_DIVX3)) {
        mType = MPEG4;
    } else if (!strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_MPEG2)) {
        mType = MPEG2;
    } else if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_MPEG)) {
        mType = MP2_3;
        if (findMP3Header(&mMP3Header) != OK) {
            ALOGW("No mp3 header found");
        }
        ALOGV("mMP3Header=0x%8.8x", mMP3Header);
    } else if (!strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_MJPEG)) {
        mType = MJPEG;
    } else if (!strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_HEVC)) {
        mType = HEVC;

        uint32_t dummy;
        const uint8_t *hvcc;
        size_t hvccSize;
        if (!meta->findData(kKeyHVCC, &dummy, (const void **)&hvcc, &hvccSize)) {
            sp<MetaData> metadata = NULL;
            while (metadata == NULL) {
                clearPendingFrames();
                while (mPendingFrames.empty()) {
                    status_t err = readBlock();

                    if (err != OK) {
                        clearPendingFrames();
                        break;
                    }
                }

                if (!mPendingFrames.empty()) {
                    MediaBuffer *buffer = *mPendingFrames.begin();
                    sp < ABuffer >  accessUnit = new ABuffer(buffer->range_length());
                    ALOGV("firstBuffer->range_length() = %zu", buffer->range_length());
                    memcpy(accessUnit->data(), buffer->data(), buffer->range_length());
                    metadata = MakeHEVCCodecSpecificData(accessUnit);
                }
            }
            CHECK(metadata->findData(kKeyHVCC, &dummy, (const void **)&hvcc, &hvccSize));
            ALOGV("avccSize = %zu ", hvccSize);
            CHECK_GE(hvccSize, 5u);
            meta->setData(kKeyHVCC, 0, hvcc, hvccSize);
            mBlockIter.reset();
            clearPendingFrames();
        }
        if (meta->findData(kKeyHVCC, &dummy, (const void **)&hvcc, &hvccSize)
                && hvccSize >= 5u) {
            mNALSizeLen = 1 + (hvcc[21] & 3);
            ALOGV("hevc mNALSizeLen = %zd", mNALSizeLen);
        } else {
            ALOGE("No mNALSizeLen");
        }
    }
    ALOGV("MtkMatroskaSource constructor mType=%d", mType);
// end of add for mtk
}

MtkMatroskaSource::~MtkMatroskaSource() {
    clearPendingFrames();
}

status_t MtkMatroskaSource::start(MetaData * /* params */) {
    if (mType == AVC && mNALSizeLen < 0) {
        return ERROR_MALFORMED;
    }

    mBlockIter.reset();
    ALOGD("MtkMatroskaSource start");

    return OK;
}

status_t MtkMatroskaSource::stop() {
    clearPendingFrames();

    return OK;
}

sp<MetaData> MtkMatroskaSource::getFormat() {
    return mExtractor->mTracks.itemAt(mTrackIndex).mMeta;
}

////////////////////////////////////////////////////////////////////////////////

MtkBlockIterator::MtkBlockIterator(
        MtkMatroskaExtractor *extractor, unsigned long trackNum, unsigned long index)
    : mExtractor(extractor),
      mTrackNum(trackNum),
      mIndex(index),
      mCluster(NULL),
      mBlockEntry(NULL),
      mBlockEntryIndex(0) {
// add for mtk
      mTrackType = mExtractor->mSegment->GetTracks()->GetTrackByNumber(trackNum)->GetType();
// end of add for mtk
    reset();
}

bool MtkBlockIterator::eos() const {
    return mCluster == NULL || mCluster->EOS();
}

void MtkBlockIterator::advance() {
    Mutex::Autolock autoLock(mExtractor->mLock);
    advance_l();
}

void MtkBlockIterator::advance_l() {
    for (;;) {
        long res = mCluster->GetEntry(mBlockEntryIndex, mBlockEntry);
        ALOGV("GetEntry returned %ld", res);

        long long pos;
        long len;
        if (res < 0) {
            // Need to parse this cluster some more

            CHECK_EQ(res, mkvparser::E_BUFFER_NOT_FULL);

            res = mCluster->Parse(pos, len);
            ALOGV("Parse returned %ld", res);

            if (res < 0) {
                // I/O error

                ALOGE("Cluster::Parse returned result %ld", res);

                mCluster = NULL;
                break;
            }

            continue;
        } else if (res == 0) {
            // We're done with this cluster

            const mkvparser::Cluster *nextCluster;
            res = mExtractor->mSegment->ParseNext(
                    mCluster, nextCluster, pos, len);
            ALOGV("ParseNext returned %ld", res);

            if (res != 0) {
                // EOF or error

                mCluster = NULL;
                break;
            }

            CHECK_EQ(res, 0);
            CHECK(nextCluster != NULL);
            CHECK(!nextCluster->EOS());

            mCluster = nextCluster;

            res = mCluster->Parse(pos, len);
            ALOGV("Parse (2) returned %ld", res);
            CHECK_GE(res, 0);

            mBlockEntryIndex = 0;
            continue;
        }

        CHECK(mBlockEntry != NULL);
        CHECK(mBlockEntry->GetBlock() != NULL);
        ++mBlockEntryIndex;

        if (mBlockEntry->GetBlock()->GetTrackNumber() == mTrackNum) {
            break;
        }
    }
}

void MtkBlockIterator::reset() {
    Mutex::Autolock autoLock(mExtractor->mLock);

    mCluster = mExtractor->mSegment->GetFirst();
    mBlockEntry = NULL;
    mBlockEntryIndex = 0;

    do {
        advance_l();
    } while (!eos() && block()->GetTrackNumber() != mTrackNum);
}

void MtkBlockIterator::seek(
        int64_t seekTimeUs, bool isAudio,
        int64_t *actualFrameTimeUs) {
    Mutex::Autolock autoLock(mExtractor->mLock);

    *actualFrameTimeUs = -1ll;

    if (seekTimeUs > INT64_MAX / 1000ll ||
            seekTimeUs < INT64_MIN / 1000ll ||
            (mExtractor->mSeekPreRollNs > 0 &&
                    (seekTimeUs * 1000ll) < INT64_MIN + mExtractor->mSeekPreRollNs) ||
            (mExtractor->mSeekPreRollNs < 0 &&
                    (seekTimeUs * 1000ll) > INT64_MAX + mExtractor->mSeekPreRollNs)) {
        ALOGE("cannot seek to %lld", (long long) seekTimeUs);
        return;
    }

    const int64_t seekTimeNs = seekTimeUs * 1000ll - mExtractor->mSeekPreRollNs;

    mkvparser::Segment* const pSegment = mExtractor->mSegment;

    // Special case the 0 seek to avoid loading Cues when the application
    // extraneously seeks to 0 before playing.
    if (seekTimeNs <= 0) {
        ALOGV("Seek to beginning: %" PRId64, seekTimeUs);
        mCluster = pSegment->GetFirst();
        mBlockEntryIndex = 0;
        do {
            advance_l();
        } while (!eos() && block()->GetTrackNumber() != mTrackNum);
        return;
    }

    ALOGV("Seeking to: %" PRId64, seekTimeUs);

    // If the Cues have not been located then find them.
    const mkvparser::Cues* pCues = pSegment->GetCues();
    const mkvparser::SeekHead* pSH = pSegment->GetSeekHead();
    if (!pCues && pSH) {
        const size_t count = pSH->GetCount();
        const mkvparser::SeekHead::Entry* pEntry;
        ALOGV("No Cues yet");

        for (size_t index = 0; index < count; index++) {
            pEntry = pSH->GetEntry(index);

            if (pEntry->id == 0x0C53BB6B) {  // Cues ID
                long len; long long pos;
                pSegment->ParseCues(pEntry->pos, pos, len);
                pCues = pSegment->GetCues();
                ALOGV("Cues found");
                break;
            }
        }

        if (!pCues) {
            ALOGE("No Cues in file");
// add for mtk
            ALOGI("no cue data,seek without cue data");
            seekwithoutcue(seekTimeUs);
// end of add for mtk
            return;
        }
    }
    else if (!pSH) {
        ALOGE("No SeekHead");
// add for mtk
        ALOGI("no seekhead, seek without cue data");
        seekwithoutcue(seekTimeUs);
// end of add for mtk
        return;
    }

    const mkvparser::CuePoint* pCP;
    mkvparser::Tracks const *pTracks = pSegment->GetTracks();
    while (!pCues->DoneParsing()) {
        pCues->LoadCuePoint();
        pCP = pCues->GetLast();
// add for mtk
        ALOGV("pCP = %s", pCP == NULL ? "NULL" : "not NULL");
        if (pCP == NULL)
            continue;
// end of add for mtk
#if 0  // android default code insteaded by mtk code
        CHECK(pCP);
#endif

        size_t trackCount = mExtractor->mTracks.size();
        for (size_t index = 0; index < trackCount; ++index) {
            MtkMatroskaExtractor::TrackInfo& track = mExtractor->mTracks.editItemAt(index);
            const mkvparser::Track *pTrack = pTracks->GetTrackByNumber(track.mTrackNum);
            if (pTrack && pTrack->GetType() == 1 && pCP->Find(pTrack)) {  // VIDEO_TRACK
                track.mCuePoints.push_back(pCP);
            }
        }

        if (pCP->GetTime(pSegment) >= seekTimeNs) {
            ALOGV("Parsed past relevant Cue");
            break;
        }
    }

    const mkvparser::CuePoint::TrackPosition *pTP = NULL;
    const mkvparser::Track *thisTrack = pTracks->GetTrackByNumber(mTrackNum);
    if (thisTrack->GetType() == 1) {  // video
        MtkMatroskaExtractor::TrackInfo& track = mExtractor->mTracks.editItemAt(mIndex);
        pTP = track.find(seekTimeNs);
    } else {
        // The Cue index is built around video keyframes
        unsigned long int trackCount = pTracks->GetTracksCount();
        for (size_t index = 0; index < trackCount; ++index) {
            const mkvparser::Track *pTrack = pTracks->GetTrackByIndex(index);
            if (pTrack && pTrack->GetType() == 1 && pCues->Find(seekTimeNs, pTrack, pCP, pTP)) {
                ALOGV("Video track located at %zu", index);
                break;
            }
        }
    }


    // Always *search* based on the video track, but finalize based on mTrackNum
    if (!pTP) {
        ALOGE("Did not locate the video track for seeking");
// add for mtk
        seekwithoutcue(seekTimeUs);
// end of add for mtk
        return;
    }

    mCluster = pSegment->FindOrPreloadCluster(pTP->m_pos);

    CHECK(mCluster);
    CHECK(!mCluster->EOS());

    // mBlockEntryIndex starts at 0 but m_block starts at 1
    CHECK_GT(pTP->m_block, 0);
    mBlockEntryIndex = pTP->m_block - 1;

    for (;;) {
        advance_l();

        if (eos()) break;

        if (isAudio || block()->IsKey()) {
            // Accept the first key frame
            int64_t frameTimeUs = (block()->GetTime(mCluster) + 500LL) / 1000LL;
            if (thisTrack->GetType() == 1 || frameTimeUs >= seekTimeUs) {
                *actualFrameTimeUs = frameTimeUs;
                ALOGV("Requested seek point: %" PRId64 " actual: %" PRId64,
                      seekTimeUs, *actualFrameTimeUs);
                break;
            }
        }
    }
}

const mkvparser::Block *MtkBlockIterator::block() const {
    CHECK(!eos());

    return mBlockEntry->GetBlock();
}

int64_t MtkBlockIterator::blockTimeUs() const {
    if (mCluster == NULL || mBlockEntry == NULL) {
        return -1;
    }
    return (mBlockEntry->GetBlock()->GetTime(mCluster) + 500ll) / 1000ll;
}

////////////////////////////////////////////////////////////////////////////////

static unsigned U24_AT(const uint8_t *ptr) {
    return ptr[0] << 16 | ptr[1] << 8 | ptr[2];
}

void MtkMatroskaSource::clearPendingFrames() {
    while (!mPendingFrames.empty()) {
        MediaBuffer *frame = *mPendingFrames.begin();
        mPendingFrames.erase(mPendingFrames.begin());

        frame->release();
        frame = NULL;
    }
}

status_t MtkMatroskaSource::setWebmBlockCryptoInfo(MediaBuffer *mbuf) {
    if (mbuf->range_length() < 1 || mbuf->range_length() - 1 > INT32_MAX) {
        // 1-byte signal
        return ERROR_MALFORMED;
    }

    const uint8_t *data = (const uint8_t *)mbuf->data() + mbuf->range_offset();
    bool blockEncrypted = data[0] & 0x1;
    if (blockEncrypted && mbuf->range_length() < 9) {
        // 1-byte signal + 8-byte IV
        return ERROR_MALFORMED;
    }

    sp<MetaData> meta = mbuf->meta_data();
    if (blockEncrypted) {
        /*
         *  0                   1                   2                   3
         *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
         *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         *  |  Signal Byte  |                                               |
         *  +-+-+-+-+-+-+-+-+             IV                                |
         *  |                                                               |
         *  |               +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         *  |               |                                               |
         *  |-+-+-+-+-+-+-+-+                                               |
         *  :               Bytes 1..N of encrypted frame                   :
         *  |                                                               |
         *  |                                                               |
         *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         */
        int32_t plainSizes[] = { 0 };
        int32_t encryptedSizes[] = { static_cast<int32_t>(mbuf->range_length() - 9) };
        uint8_t ctrCounter[16] = { 0 };
        uint32_t type;
        const uint8_t *keyId;
        size_t keyIdSize;
        sp<MetaData> trackMeta = mExtractor->mTracks.itemAt(mTrackIndex).mMeta;
        CHECK(trackMeta->findData(kKeyCryptoKey, &type, (const void **)&keyId, &keyIdSize));
        meta->setData(kKeyCryptoKey, 0, keyId, keyIdSize);
        memcpy(ctrCounter, data + 1, 8);
        meta->setData(kKeyCryptoIV, 0, ctrCounter, 16);
        meta->setData(kKeyPlainSizes, 0, plainSizes, sizeof(plainSizes));
        meta->setData(kKeyEncryptedSizes, 0, encryptedSizes, sizeof(encryptedSizes));
        mbuf->set_range(9, mbuf->range_length() - 9);
    } else {
        /*
         *  0                   1                   2                   3
         *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
         *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         *  |  Signal Byte  |                                               |
         *  +-+-+-+-+-+-+-+-+                                               |
         *  :               Bytes 1..N of unencrypted frame                 :
         *  |                                                               |
         *  |                                                               |
         *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         */
        int32_t plainSizes[] = { static_cast<int32_t>(mbuf->range_length() - 1) };
        int32_t encryptedSizes[] = { 0 };
        meta->setData(kKeyPlainSizes, 0, plainSizes, sizeof(plainSizes));
        meta->setData(kKeyEncryptedSizes, 0, encryptedSizes, sizeof(encryptedSizes));
        mbuf->set_range(1, mbuf->range_length() - 1);
    }

    return OK;
}

status_t MtkMatroskaSource::readBlock() {
    CHECK(mPendingFrames.empty());

    if (mBlockIter.eos()) {
        return ERROR_END_OF_STREAM;
    }

    const mkvparser::Block *block = mBlockIter.block();

    int64_t timeUs = mBlockIter.blockTimeUs();

    for (int i = 0; i < block->GetFrameCount(); ++i) {
        MtkMatroskaExtractor::TrackInfo *trackInfo = &mExtractor->mTracks.editItemAt(mTrackIndex);
        const mkvparser::Block::Frame &frame = block->GetFrame(i);
        size_t len = frame.len;
        if (SIZE_MAX - len < trackInfo->mHeaderLen) {
            return ERROR_MALFORMED;
        }

        len += trackInfo->mHeaderLen;
        MediaBuffer *mbuf = new MediaBuffer(len);
        uint8_t *data = static_cast<uint8_t *>(mbuf->data());
        if (trackInfo->mHeader) {
            memcpy(data, trackInfo->mHeader, trackInfo->mHeaderLen);
        }

        mbuf->meta_data()->setInt64(kKeyTime, timeUs);
        mbuf->meta_data()->setInt32(kKeyIsSyncFrame, block->IsKey());

        status_t err = frame.Read(mExtractor->mReader, data + trackInfo->mHeaderLen);
        if (err == OK
                && mExtractor->mIsWebm
                && trackInfo->mEncrypted) {
            err = setWebmBlockCryptoInfo(mbuf);
        }

        if (err != OK) {
            mPendingFrames.clear();

            mBlockIter.advance();
            mbuf->release();
            return err;
        }

        mPendingFrames.push_back(mbuf);
    }

    mBlockIter.advance();

    return OK;
}

status_t MtkMatroskaSource::read(
        MediaBuffer **out, const ReadOptions *options) {
    *out = NULL;

    int64_t targetSampleTimeUs = -1ll;

    int64_t seekTimeUs;
    ReadOptions::SeekMode mode;
// add for mtk
    bool seeking = false;
// end of add for mtk
    if (options && options->getSeekTo(&seekTimeUs, &mode)
            && !mExtractor->isLiveStreaming()) {
        clearPendingFrames();

        // The audio we want is located by using the Cues to seek the video
        // stream to find the target Cluster then iterating to finalize for
        // audio.
        int64_t actualFrameTimeUs;
        mBlockIter.seek(seekTimeUs, mIsAudio, &actualFrameTimeUs);

// add for mtk
        if (MKVLogOpen)
            ALOGD("read, seeking mode=%d, seekTimeUs=%lld, %s mType=%d, actualFrameTimeUs=%lld",
                 mode, (long long)seekTimeUs, mIsAudio ? "audio" : "video", mType, (long long)actualFrameTimeUs);

        if (mIsAudio || mode == ReadOptions::SEEK_CLOSEST) {
            ALOGV("mIsAudio=%d or mode=%d, need set targetSampleTimeUs = seekTimeUs", mIsAudio, mode);
            targetSampleTimeUs = seekTimeUs;
            seeking = true;
        }
// end of add for mtk
#if 0  // android default code insteaded by mtk code
        if (mode == ReadOptions::SEEK_CLOSEST) {
            targetSampleTimeUs = actualFrameTimeUs;
        }
#endif
    }

    while (mPendingFrames.empty()) {
        status_t err = readBlock();

        if (err != OK) {
            clearPendingFrames();

            return err;
        }
    }

    MediaBuffer *frame = *mPendingFrames.begin();

// add for mtk
    if (seeking || mFirstFrame) {
        ALOGV("MtkMatroskaSource::read,%s mType=%d,seeking =%d or mFirstFrame=%d",
            mIsAudio ? "audio" : "video", mType, seeking, mFirstFrame);
        mFirstFrame = false;
        CHECK(frame->meta_data()->findInt64(kKeyTime, &mCurrentTS));
        if (mCurrentTS >= 0) {
            if (MKVLogOpen)
                ALOGD("frame mCurrentTS=%lld", (long long)mCurrentTS);
        } else {
            ALOGE("frame mCurrentTS=%lld, set ts = 0", (long long)mCurrentTS);
            mCurrentTS = 0;
            frame->meta_data()->setInt64(kKeyTime, mCurrentTS);
        }
    }

    size_t size = frame->range_length();
    ALOGV("%s mType=%d, frame size =%zu", mIsAudio ? "audio" : "video", mType, size);
    if (seeking && (mType == VP8 || mType == VP9 || mType == MPEG4 ||
        mType == MJPEG || mType == MPEG2 || mType == HEVC)) {
        frame->meta_data()->setInt64(kKeyTargetTime, (targetSampleTimeUs >= 0ll ? targetSampleTimeUs : seekTimeUs));
    }

    if (mType != AVC && mType != HEVC) {
        if (MP2_3 == mType) {
            ALOGV("MtkMatroskaSource::read MP2_3-->");
            int32_t start = -1;
            while (start < 0) {
                start = mkv_mp3HeaderStartAt(
                    (const uint8_t*)frame->data() + frame->range_offset(), frame->range_length(), mMP3Header);
                ALOGV("start=%d, frame->range_length() = %zu, frame->range_offset() =%zu",
                              start, frame->range_length(), frame->range_offset());
                if (start >= 0)  break;
                frame->release();
                mPendingFrames.erase(mPendingFrames.begin());
                while (mPendingFrames.empty()) {
                    status_t err = readBlock();
                    if (err != OK) {
                        clearPendingFrames();
                        return err;
                    }
                }
                frame = *mPendingFrames.begin();
                CHECK(frame->meta_data()->findInt64(kKeyTime, &mCurrentTS));
            }

            frame->set_range(frame->range_offset() + start, frame->range_length() - start);

            uint32_t header = *(uint32_t*)((uint8_t*)frame->data()+frame->range_offset());
            header = ((header >> 24) & 0xff) | ((header >> 8) & 0xff00) |
                            ((header << 8) & 0xff0000) | ((header << 24) & 0xff000000);
            size_t frame_size;
            int out_sampling_rate;
            int out_channels;
            int out_bitrate;
            if (!get_mp3_info(header, &frame_size, &out_sampling_rate, &out_channels, &out_bitrate)) {
                ALOGE("MP3 Header read fail!!");
                return ERROR_UNSUPPORTED;
            }
            MediaBuffer *buffer = new MediaBuffer(frame_size);
            ALOGV("MP3 frame %zu frame->range_length() %zu", frame_size, frame->range_length());

            if (frame_size > frame->range_length()) {
                memcpy(buffer->data(), (uint8_t*)(frame->data()) + frame->range_offset(), frame->range_length());
                size_t sumSize = 0;
                sumSize += frame->range_length();
                size_t needSize = frame_size - frame->range_length();
                frame->release();
                mPendingFrames.erase(mPendingFrames.begin());
                while (mPendingFrames.empty()) {
                    status_t err = readBlock();
                    if (err != OK) {
                        clearPendingFrames();
                        return err;
                    }
                }
                frame = *mPendingFrames.begin();
                size_t offset = frame->range_offset();
                size_t size = frame->range_length();

                // the next buffer frame is not enough to fullfill mp3 frame, we have read until mp3 frame is completed.
                while (size < needSize) {
                    memcpy((uint8_t*)(buffer->data())+sumSize, (uint8_t*)(frame->data())+offset, size);
                    needSize -= size;
                    sumSize+=size;
                    frame->release();
                    mPendingFrames.erase(mPendingFrames.begin());
                    while (mPendingFrames.empty()) {
                        status_t err = readBlock();
                        if (err != OK) {
                            clearPendingFrames();
                            return err;
                        }
                    }
                    frame = *mPendingFrames.begin();
                    offset = frame->range_offset();
                    size = frame->range_length();
                }
                memcpy((uint8_t*)(buffer->data()) + sumSize, (uint8_t*)(frame->data()) + offset, needSize);
                frame->set_range(offset + needSize, size - needSize);
             } else {
                size_t offset = frame->range_offset();
                size_t size = frame->range_length();
                memcpy(buffer->data(), (uint8_t*)(frame->data()) + offset, frame_size);
                frame->set_range(offset + frame_size, size - frame_size);
            }
            if (frame->range_length() < 4) {
                frame->release();
                frame = NULL;
                mPendingFrames.erase(mPendingFrames.begin());
            }
            ALOGV("MtkMatroskaSource::read MP2_3 frame kKeyTime=%lld,kKeyTargetTime=%lld",
                            (long long)mCurrentTS, (long long)targetSampleTimeUs);
            buffer->meta_data()->setInt64(kKeyTime, mCurrentTS);
            mCurrentTS += (int64_t)frame_size * 8000ll / out_bitrate;

            if (targetSampleTimeUs >= 0ll)
                buffer->meta_data()->setInt64(kKeyTargetTime, targetSampleTimeUs);
            *out = buffer;
            ALOGV("MtkMatroskaSource::read MP2_3--<, keyTime=%lld for next frame", (long long)mCurrentTS);
            return OK;

        } else {
            ALOGV("MtkMatroskaSource::read,not AVC,HEVC,mp3,return frame directly,kKeyTargetTime=%lld",
                            (long long)targetSampleTimeUs);
            if (targetSampleTimeUs >= 0ll) {
                frame->meta_data()->setInt64(
                        kKeyTargetTime, targetSampleTimeUs);
            }
            *out = frame;
            mPendingFrames.erase(mPendingFrames.begin());
            return OK;
        }
    }
// end of mtk
#if 0  // android default code insteaded by mtk code
    mPendingFrames.erase(mPendingFrames.begin());

    if (mType != AVC || mNALSizeLen == 0) {
        if (targetSampleTimeUs >= 0ll) {
            frame->meta_data()->setInt64(
                    kKeyTargetTime, targetSampleTimeUs);
        }

        *out = frame;

        return OK;
    }
#endif

// add for mtk
    // is AVC or HEVC
    if (size < (size_t)mNALSizeLen) {
        *out = frame;
        frame = NULL;
        mPendingFrames.erase(mPendingFrames.begin());
        ALOGE("[Warning]size:%zu < mNALSizeLen:%zu", size, mNALSizeLen);
        return OK;
    }
// end of add for mtk
    // Each input frame contains one or more NAL fragments, each fragment
    // is prefixed by mNALSizeLen bytes giving the fragment length,
    // followed by a corresponding number of bytes containing the fragment.
    // We output all these fragments into a single large buffer separated
    // by startcodes (0x00 0x00 0x00 0x01).
    //
    // When mNALSizeLen is 0, we assume the data is already in the format
    // desired.

    const uint8_t *srcPtr =
        (const uint8_t *)frame->data() + frame->range_offset();

    size_t srcSize = frame->range_length();

// add for mtk
    if (( srcSize >= 4
            && *(srcPtr + 0) == 0x00 && *(srcPtr + 1) == 0x00 && *(srcPtr + 2) == 0x00
            && (*(srcPtr + 3) == 0x01 || *(srcPtr + 3) == 0x00))
            || (mIsFromFFmpeg && frame->range_length() >= 3
            && *(srcPtr + 0) == 0x00 && *(srcPtr + 1) == 0x00 && *(srcPtr + 2) == 0x01)) {
        // already nal start code+nal data
        ALOGV("frame is already nal start code + nal data, return frame directly, isFromFFMpeg=%d", mIsFromFFmpeg);
        if (targetSampleTimeUs >= 0ll) {
            frame->meta_data()->setInt64(kKeyTargetTime, targetSampleTimeUs);
        }
        *out = frame;
        mPendingFrames.erase(mPendingFrames.begin());
        return OK;
    }
// end of add for mtk
    size_t dstSize = 0;
    MediaBuffer *buffer = NULL;
    uint8_t *dstPtr = NULL;

    for (int32_t pass = 0; pass < 2; ++pass) {
        size_t srcOffset = 0;
        size_t dstOffset = 0;
        while (srcOffset + mNALSizeLen <= srcSize) {
            size_t NALsize;
            switch (mNALSizeLen) {
                case 1: NALsize = srcPtr[srcOffset]; break;
                case 2: NALsize = U16_AT(srcPtr + srcOffset); break;
                case 3: NALsize = U24_AT(srcPtr + srcOffset); break;
                case 4: NALsize = U32_AT(srcPtr + srcOffset); break;
                default:
                    TRESPASS();
            }

            if (srcOffset + mNALSizeLen + NALsize <= srcOffset + mNALSizeLen) {
                frame->release();
                frame = NULL;

                return ERROR_MALFORMED;
            } else if (srcOffset + mNALSizeLen + NALsize > srcSize) {
                break;
            }

            if (pass == 1) {
                memcpy(&dstPtr[dstOffset], "\x00\x00\x00\x01", 4);

                if (frame != buffer) {
                    memcpy(&dstPtr[dstOffset + 4],
                           &srcPtr[srcOffset + mNALSizeLen],
                           NALsize);
                }
            }

            dstOffset += 4;  // 0x00 00 00 01
            dstOffset += NALsize;

            srcOffset += mNALSizeLen + NALsize;
        }

        if (srcOffset < srcSize) {
            // There were trailing bytes or not enough data to complete
            // a fragment.

            frame->release();
            frame = NULL;
// add for mtk
            mPendingFrames.erase(mPendingFrames.begin());
// end of add for mtk

            return ERROR_MALFORMED;
        }

        if (pass == 0) {
            dstSize = dstOffset;

            if (dstSize == srcSize && mNALSizeLen == 4) {
                // In this special case we can re-use the input buffer by substituting
                // each 4-byte nal size with a 4-byte start code
                buffer = frame;
            } else {
                buffer = new MediaBuffer(dstSize);
            }

            int64_t timeUs;
            CHECK(frame->meta_data()->findInt64(kKeyTime, &timeUs));
            int32_t isSync;
            CHECK(frame->meta_data()->findInt32(kKeyIsSyncFrame, &isSync));

            buffer->meta_data()->setInt64(kKeyTime, timeUs);
            buffer->meta_data()->setInt32(kKeyIsSyncFrame, isSync);

            dstPtr = (uint8_t *)buffer->data();
        }
    }

    if (frame != buffer) {
        frame->release();
        frame = NULL;
    }

    if (targetSampleTimeUs >= 0ll) {
        buffer->meta_data()->setInt64(
                kKeyTargetTime, targetSampleTimeUs);
    }

    *out = buffer;
// add for mtk
        ALOGV("read return,buffer range_length=%zu,buffer offset=%zu",
                        buffer->range_length(), buffer->range_offset());
        mPendingFrames.erase(mPendingFrames.begin());
// end of add for mtk

    return OK;
}

////////////////////////////////////////////////////////////////////////////////

MtkMatroskaExtractor::MtkMatroskaExtractor(const sp<DataSource> &source)
    : mDataSource(source),
      mReader(new MtkDataSourceReader(mDataSource)),
      mSegment(NULL),
      mExtractedThumbnails(false),
      mIsWebm(false),
      mSeekPreRollNs(0) {
    off64_t size;
    mIsLiveStreaming =
        (mDataSource->flags()
            & (DataSource::kWantsPrefetching
                | DataSource::kIsCachingDataSource))
        && mDataSource->getSize(&size) != OK;

    mkvparser::EBMLHeader ebmlHeader;
    long long pos;
// add for mtk
    // for log reduction
    char value[PROPERTY_VALUE_MAX];
    property_get("mkv.logopen", value, "0");
    if (atoi(value))
        MKVLogOpen = true;

    ALOGV("=====================================\n");
    ALOGD("MtkMatroskaExtractor constructor +++ \n");
    ALOGV("[MKV Playback capability info]\n");
    ALOGV("Support Codec = \"Video:AVC,H263,MPEG4,HEVC,VP8,VP9\" \n");
    ALOGV("Support Codec = \"Audio: VORBIS,AAC,AMR,MP3\" \n");
    ALOGV("=====================================\n");
// end of add for mtk
    if (ebmlHeader.Parse(mReader, pos) < 0) {
        return;
    }

    if (ebmlHeader.m_docType && !strcmp("webm", ebmlHeader.m_docType)) {
        mIsWebm = true;
    }

    long long ret =
        mkvparser::Segment::CreateInstance(mReader, pos, mSegment);

    if (ret) {
        CHECK(mSegment == NULL);
        return;
    }

// add for mtk
    if (mIsLiveStreaming) {
        ALOGI("MtkMatroskaExtractor is live streaming");
// end of add for mtk
    // from mkvparser::Segment::Load(), but stop at first cluster
    ret = mSegment->ParseHeaders();
    if (ret == 0) {
        long len;
        ret = mSegment->LoadCluster(pos, len);
        if (ret >= 1) {
            // no more clusters
            ret = 0;
        }
    } else if (ret > 0) {
        ret = mkvparser::E_BUFFER_NOT_FULL;
    }
// add for mtk
    } else {
        ret = mSegment->ParseHeaders();
        if (ret < 0) {
            ALOGE("MtkMatroskaExtractor,Segment parse header return fail %lld", ret);
            delete mSegment;
            mSegment = NULL;
            return;
        } else if (ret == 0) {
            const mkvparser::Cues* mCues = mSegment->GetCues();
            const mkvparser::SeekHead* mSH = mSegment->GetSeekHead();
            if ((mCues == NULL) && (mSH != NULL)) {
                size_t count = mSH->GetCount();
                const mkvparser::SeekHead::Entry* mEntry;
                for (size_t index = 0; index < count; index++) {
                    mEntry = mSH->GetEntry(index);
                    if (mEntry->id == 0x0C53BB6B) {  // Cues ID
                        long len;
                        long long pos;
                        mSegment->ParseCues(mEntry->pos, pos, len);
                        mCues = mSegment->GetCues();
                        ALOGV("find cue data by seekhead");
                        break;
                    }
                }
            }

            if (mCues) {
                long len;
                ret = mSegment->LoadCluster(pos, len);
                if (MKVLogOpen)
                    ALOGD("has Cue data, Cluster num=%ld", mSegment->GetCount());
            } else  {
                ALOGW("no Cue data");
                ret = mSegment->Load();
            }
        } else if (ret > 0) {
            ret = mkvparser::E_BUFFER_NOT_FULL;
        }
    }
// end of add for mtk

    if (ret < 0) {
        ALOGW("Corrupt %s source: %s", mIsWebm ? "webm" : "matroska",
                uriDebugString(mDataSource->getUri()).c_str());
        delete mSegment;
        mSegment = NULL;
        return;
    }

#if 0
    const mkvparser::SegmentInfo *info = mSegment->GetInfo();
    ALOGI("muxing app: %s, writing app: %s",
         info->GetMuxingAppAsUTF8(),
         info->GetWritingAppAsUTF8());
#endif

    addTracks();
}

MtkMatroskaExtractor::~MtkMatroskaExtractor() {
    ALOGI("~MtkMatroskaExtractor destructor");
    delete mSegment;
    mSegment = NULL;

    delete mReader;
    mReader = NULL;
}

size_t MtkMatroskaExtractor::countTracks() {
    return mTracks.size();
}

sp<IMediaSource> MtkMatroskaExtractor::getTrack(size_t index) {
    if (index >= mTracks.size()) {
        return NULL;
    }

// add for mtk
    sp<IMediaSource> matroskasource = new MtkMatroskaSource(this, index);
    int32_t isinfirstframe = false;
    ALOGV("getTrack,index=%zu", index);
    if (mTracks.itemAt(index).mMeta->findInt32(kKeyCodecInfoIsInFirstFrame, &isinfirstframe)
        && isinfirstframe) {
        ALOGD("Codec info is in first frame");;
        (static_cast<MtkMatroskaSource*>(matroskasource.get()))->setCodecInfoFromFirstFrame();
    }
    return matroskasource;
// end of add for mtk
#if 0  // android default code insteaded by mtk code
    return new MtkMatroskaSource(this, index);
#endif
}

sp<MetaData> MtkMatroskaExtractor::getTrackMetaData(
        size_t index, uint32_t flags) {
    if (index >= mTracks.size()) {
        return NULL;
    }

    if ((flags & kIncludeExtensiveMetaData) && !mExtractedThumbnails
            && !isLiveStreaming()) {
        findThumbnails();
        mExtractedThumbnails = true;
    }

    return mTracks.itemAt(index).mMeta;
}

bool MtkMatroskaExtractor::isLiveStreaming() const {
    return mIsLiveStreaming;
}

static int bytesForSize(size_t size) {
    // use at most 28 bits (4 times 7)
    CHECK(size <= 0xfffffff);

    if (size > 0x1fffff) {
        return 4;
    } else if (size > 0x3fff) {
        return 3;
    } else if (size > 0x7f) {
        return 2;
    }
    return 1;
}

static void storeSize(uint8_t *data, size_t &idx, size_t size) {
    int numBytes = bytesForSize(size);
    idx += numBytes;

    data += idx;
    size_t next = 0;
    while (numBytes--) {
        *--data = (size & 0x7f) | next;
        size >>= 7;
        next = 0x80;
    }
}

static void addESDSFromCodecPrivate(
        const sp<MetaData> &meta,
        bool isAudio, const void *priv, size_t privSize) {
    int privSizeBytesRequired = bytesForSize(privSize);
    int esdsSize2 = 14 + privSizeBytesRequired + privSize;
    int esdsSize2BytesRequired = bytesForSize(esdsSize2);
    int esdsSize1 = 4 + esdsSize2BytesRequired + esdsSize2;
    int esdsSize1BytesRequired = bytesForSize(esdsSize1);
    size_t esdsSize = 1 + esdsSize1BytesRequired + esdsSize1;
    uint8_t *esds = new uint8_t[esdsSize];

    size_t idx = 0;
    esds[idx++] = 0x03;
    storeSize(esds, idx, esdsSize1);
    esds[idx++] = 0x00;  // ES_ID
    esds[idx++] = 0x00;  // ES_ID
    esds[idx++] = 0x00;  // streamDependenceFlag, URL_Flag, OCRstreamFlag
    esds[idx++] = 0x04;
    storeSize(esds, idx, esdsSize2);
    esds[idx++] = isAudio ? 0x40   // Audio ISO/IEC 14496-3
                          : 0x20;  // Visual ISO/IEC 14496-2
    for (int i = 0; i < 12; i++) {
        esds[idx++] = 0x00;
    }
    esds[idx++] = 0x05;
    storeSize(esds, idx, privSize);
    memcpy(esds + idx, priv, privSize);

    meta->setData(kKeyESDS, 0, esds, esdsSize);

    delete[] esds;
    esds = NULL;
}

status_t addVorbisCodecInfo(
        const sp<MetaData> &meta,
        const void *_codecPrivate, size_t codecPrivateSize) {
    // hexdump(_codecPrivate, codecPrivateSize);

    if (codecPrivateSize < 1) {
        return ERROR_MALFORMED;
    }

    const uint8_t *codecPrivate = (const uint8_t *)_codecPrivate;

    if (codecPrivate[0] != 0x02) {
        return ERROR_MALFORMED;
    }

    // codecInfo starts with two lengths, len1 and len2, that are
    // "Xiph-style-lacing encoded"...

    size_t offset = 1;
    size_t len1 = 0;
    while (offset < codecPrivateSize && codecPrivate[offset] == 0xff) {
        if (len1 > (SIZE_MAX - 0xff)) {
            return ERROR_MALFORMED;  // would overflow
        }
        len1 += 0xff;
        ++offset;
    }
    if (offset >= codecPrivateSize) {
        return ERROR_MALFORMED;
    }
    if (len1 > (SIZE_MAX - codecPrivate[offset])) {
        return ERROR_MALFORMED;  // would overflow
    }
    len1 += codecPrivate[offset++];

    size_t len2 = 0;
    while (offset < codecPrivateSize && codecPrivate[offset] == 0xff) {
        if (len2 > (SIZE_MAX - 0xff)) {
            return ERROR_MALFORMED;  // would overflow
        }
        len2 += 0xff;
        ++offset;
    }
    if (offset >= codecPrivateSize) {
        return ERROR_MALFORMED;
    }
    if (len2 > (SIZE_MAX - codecPrivate[offset])) {
        return ERROR_MALFORMED;  // would overflow
    }
    len2 += codecPrivate[offset++];

    if (len1 > SIZE_MAX - len2 || offset > SIZE_MAX - (len1 + len2) ||
            codecPrivateSize < offset + len1 + len2) {
        return ERROR_MALFORMED;
    }

    if (codecPrivate[offset] != 0x01) {
        return ERROR_MALFORMED;
    }
    meta->setData(kKeyVorbisInfo, 0, &codecPrivate[offset], len1);

    offset += len1;
    if (codecPrivate[offset] != 0x03) {
        return ERROR_MALFORMED;
    }

    offset += len2;
    if (codecPrivate[offset] != 0x05) {
        return ERROR_MALFORMED;
    }

    meta->setData(
            kKeyVorbisBooks, 0, &codecPrivate[offset],
            codecPrivateSize - offset);

    return OK;
}

static status_t addFlacMetadata(
        const sp<MetaData> &meta,
        const void *codecPrivate, size_t codecPrivateSize) {
    // hexdump(codecPrivate, codecPrivateSize);

    meta->setData(kKeyFlacMetadata, 0, codecPrivate, codecPrivateSize);

    int32_t maxInputSize = 64 << 10;
    sp<FLACDecoder> flacDecoder = FLACDecoder::Create();
    if (flacDecoder != NULL
            && flacDecoder->parseMetadata((const uint8_t*)codecPrivate, codecPrivateSize) == OK) {
        FLAC__StreamMetadata_StreamInfo streamInfo = flacDecoder->getStreamInfo();
        maxInputSize = streamInfo.max_framesize;
        if (maxInputSize == 0) {
            // In case max framesize is not available, use raw data size as max framesize,
            // assuming there is no expansion.
            if (streamInfo.max_blocksize != 0
                    && streamInfo.channels != 0
                    && ((streamInfo.bits_per_sample + 7) / 8) >
                        INT32_MAX / streamInfo.max_blocksize / streamInfo.channels) {
                return ERROR_MALFORMED;
            }
            maxInputSize = ((streamInfo.bits_per_sample + 7) / 8)
                * streamInfo.max_blocksize * streamInfo.channels;
        }
    }
    meta->setInt32(kKeyMaxInputSize, maxInputSize);

    return OK;
}

status_t MtkMatroskaExtractor::synthesizeAVCC(TrackInfo *trackInfo, size_t index) {
    MtkBlockIterator iter(this, trackInfo->mTrackNum, index);
    if (iter.eos()) {
        return ERROR_MALFORMED;
    }

    const mkvparser::Block *block = iter.block();
    if (block->GetFrameCount() <= 0) {
        return ERROR_MALFORMED;
    }

    const mkvparser::Block::Frame &frame = block->GetFrame(0);
    sp<ABuffer> abuf = new ABuffer(frame.len);
    long n = frame.Read(mReader, abuf->data());
    if (n != 0) {
        return ERROR_MALFORMED;
    }

    sp<MetaData> avcMeta = MakeAVCCodecSpecificData(abuf);
    if (avcMeta == NULL) {
        return ERROR_MALFORMED;
    }

    // Override the synthesized nal length size, which is arbitrary
    avcMeta->setInt32(kKeyNalLengthSize, 0);
    trackInfo->mMeta = avcMeta;
    return OK;
}

static inline bool isValidInt32ColourValue(long long value) {
    return value != mkvparser::Colour::kValueNotPresent
            && value >= INT32_MIN
            && value <= INT32_MAX;
}

static inline bool isValidUint16ColourValue(long long value) {
    return value != mkvparser::Colour::kValueNotPresent
            && value >= 0
            && value <= UINT16_MAX;
}

static inline bool isValidPrimary(const mkvparser::PrimaryChromaticity *primary) {
    return primary != NULL && primary->x >= 0 && primary->x <= 1
             && primary->y >= 0 && primary->y <= 1;
}

void MtkMatroskaExtractor::getColorInformation(
        const mkvparser::VideoTrack *vtrack, sp<MetaData> &meta) {
    const mkvparser::Colour *color = vtrack->GetColour();
    if (color == NULL) {
        return;
    }

    // Color Aspects
    {
        int32_t primaries = 2;  // ISO unspecified
        int32_t transfer = 2;  // ISO unspecified
        int32_t coeffs = 2;  // ISO unspecified
        bool fullRange = false;  // default
        bool rangeSpecified = false;

        if (isValidInt32ColourValue(color->primaries)) {
            primaries = color->primaries;
        }
        if (isValidInt32ColourValue(color->transfer_characteristics)) {
            transfer = color->transfer_characteristics;
        }
        if (isValidInt32ColourValue(color->matrix_coefficients)) {
            coeffs = color->matrix_coefficients;
        }
        if (color->range != mkvparser::Colour::kValueNotPresent
                && color->range != 0 /* MKV unspecified */) {
            // We only support MKV broadcast range (== limited) and full range.
            // We treat all other value as the default limited range.
            fullRange = color->range == 2 /* MKV fullRange */;
            rangeSpecified = true;
        }

        ColorAspects aspects;
        ColorUtils::convertIsoColorAspectsToCodecAspects(
                primaries, transfer, coeffs, fullRange, aspects);
        meta->setInt32(kKeyColorPrimaries, aspects.mPrimaries);
        meta->setInt32(kKeyTransferFunction, aspects.mTransfer);
        meta->setInt32(kKeyColorMatrix, aspects.mMatrixCoeffs);
        meta->setInt32(
                kKeyColorRange, rangeSpecified ? aspects.mRange : ColorAspects::RangeUnspecified);
    }

    // HDR Static Info
    {
        HDRStaticInfo info, nullInfo;  // nullInfo is a fully unspecified static info
        memset(&info, 0, sizeof(info));
        memset(&nullInfo, 0, sizeof(nullInfo));
        if (isValidUint16ColourValue(color->max_cll)) {
            info.sType1.mMaxContentLightLevel = color->max_cll;
        }
        if (isValidUint16ColourValue(color->max_fall)) {
            info.sType1.mMaxFrameAverageLightLevel = color->max_fall;
        }
        const mkvparser::MasteringMetadata *mastering = color->mastering_metadata;
        if (mastering != NULL) {
            // Convert matroska values to HDRStaticInfo equivalent values for each fully specified
            // group. See CTA-681.3 section 3.2.1 for more info.
            if (mastering->luminance_max >= 0.5 && mastering->luminance_max < 65535.5) {
                info.sType1.mMaxDisplayLuminance = (uint16_t)(mastering->luminance_max + 0.5);
            }
            if (mastering->luminance_min >= 0.00005 && mastering->luminance_min < 6.55355) {
                // HDRStaticInfo Type1 stores min luminance scaled 10000:1
                info.sType1.mMinDisplayLuminance =
                    (uint16_t)(10000 * mastering->luminance_min + 0.5);
            }
            // HDRStaticInfo Type1 stores primaries scaled 50000:1
            if (isValidPrimary(mastering->white_point)) {
                info.sType1.mW.x = (uint16_t)(50000 * mastering->white_point->x + 0.5);
                info.sType1.mW.y = (uint16_t)(50000 * mastering->white_point->y + 0.5);
            }
            if (isValidPrimary(mastering->r) && isValidPrimary(mastering->g)
                    && isValidPrimary(mastering->b)) {
                info.sType1.mR.x = (uint16_t)(50000 * mastering->r->x + 0.5);
                info.sType1.mR.y = (uint16_t)(50000 * mastering->r->y + 0.5);
                info.sType1.mG.x = (uint16_t)(50000 * mastering->g->x + 0.5);
                info.sType1.mG.y = (uint16_t)(50000 * mastering->g->y + 0.5);
                info.sType1.mB.x = (uint16_t)(50000 * mastering->b->x + 0.5);
                info.sType1.mB.y = (uint16_t)(50000 * mastering->b->y + 0.5);
            }
        }
        // Only advertise static info if at least one of the groups have been specified.
        if (memcmp(&info, &nullInfo, sizeof(info)) != 0) {
            info.mID = HDRStaticInfo::kType1;
            meta->setData(kKeyHdrStaticInfo, 'hdrS', &info, sizeof(info));
        }
    }
}

status_t MtkMatroskaExtractor::initTrackInfo(
        const mkvparser::Track *track, const sp<MetaData> &meta, TrackInfo *trackInfo) {
    trackInfo->mTrackNum = track->GetNumber();
    trackInfo->mMeta = meta;
    trackInfo->mExtractor = this;
    trackInfo->mEncrypted = false;
    trackInfo->mHeader = NULL;
    trackInfo->mHeaderLen = 0;

    for (size_t i = 0; i < track->GetContentEncodingCount(); i++) {
        const mkvparser::ContentEncoding *encoding = track->GetContentEncodingByIndex(i);
        for (size_t j = 0; j < encoding->GetEncryptionCount(); j++) {
            const mkvparser::ContentEncoding::ContentEncryption *encryption;
            encryption = encoding->GetEncryptionByIndex(j);
            trackInfo->mMeta->setData(kKeyCryptoKey, 0, encryption->key_id, encryption->key_id_len);
            trackInfo->mEncrypted = true;
            break;
        }

        for (size_t j = 0; j < encoding->GetCompressionCount(); j++) {
            const mkvparser::ContentEncoding::ContentCompression *compression;
            compression = encoding->GetCompressionByIndex(j);
            ALOGV("compression algo %llu settings_len %lld",
                compression->algo, compression->settings_len);
            if (compression->algo == 3
                    && compression->settings
                    && compression->settings_len > 0) {
                trackInfo->mHeader = compression->settings;
                trackInfo->mHeaderLen = compression->settings_len;
            }
        }
    }

    return OK;
}

void MtkMatroskaExtractor::addTracks() {
    const mkvparser::Tracks *tracks = mSegment->GetTracks();

    for (size_t index = 0; index < tracks->GetTracksCount(); ++index) {
        const mkvparser::Track *track = tracks->GetTrackByIndex(index);

        if (track == NULL) {
            // Apparently this is currently valid (if unexpected) behaviour
            // of the mkv parser lib.
            continue;
        }

        const char *const codecID = track->GetCodecId();
        ALOGD("codec id = %s", codecID);
        ALOGD("codec name = %s", track->GetCodecNameAsUTF8());
        if (codecID == NULL) {
            ALOGW("unknown codecID is not supported.");
            continue;
        }

        size_t codecPrivateSize;
        const unsigned char *codecPrivate =
            track->GetCodecPrivate(codecPrivateSize);

        enum { VIDEO_TRACK = 1, AUDIO_TRACK = 2 };

        sp<MetaData> meta = new MetaData;

        status_t err = OK;

        switch (track->GetType()) {
            case VIDEO_TRACK:
            {
                const mkvparser::VideoTrack *vtrack =
                    static_cast<const mkvparser::VideoTrack *>(track);

                if (!strcmp("V_MPEG4/ISO/AVC", codecID)) {
                    meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_AVC);
// add for mtk
                    ALOGV("Video Codec: AVC");
                    if (NULL == codecPrivate) {
                        ALOGE("Unsupport AVC Video: No codec info");
                        continue;
                    }
// end of add for mtk
                    meta->setData(kKeyAVCC, 0, codecPrivate, codecPrivateSize);
                } else if (!strcmp("V_MPEG4/ISO/ASP", codecID)) {
// add for mtk
                    meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_MPEG4);
// end of add for mtk
                    if (codecPrivateSize > 0) {
#if 0
                        meta->setCString(
                                kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_MPEG4);
#endif
                        addESDSFromCodecPrivate(
                                meta, false, codecPrivate, codecPrivateSize);
                    } else {
                        ALOGW("%s is detected, but does not have configuration.",
                                codecID);
// add for mtk
                        ALOGW("find configuration from the first frame");
                        meta->setInt32(kKeyCodecInfoIsInFirstFrame, true);
// end of add for mtk
#if 0
                        continue;
#endif
                    }
                } else if (!strcmp("V_VP8", codecID)) {
                    meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_VP8);
                } else if (!strcmp("V_VP9", codecID)) {
                    meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_VP9);
                    if (codecPrivateSize > 0) {
                      // 'csd-0' for VP9 is the Blob of Codec Private data as
                      // specified in http://www.webmproject.org/vp9/profiles/.
                      meta->setData(
                              kKeyVp9CodecPrivate, 0, codecPrivate,
                              codecPrivateSize);
                    }
                }
// add for mtk
                else if (!strcmp("V_MPEGH/ISO/HEVC", codecID)) {
                    meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_HEVC);
                    if (NULL == codecPrivate) {
                        ALOGE("HEVC Video: No codec info, need make Codec info using the stream");
                    } else {
                        meta->setData(kKeyHVCC, 0, codecPrivate, codecPrivateSize);
                        ALOGV("Video Codec: HEVC,codecPrivateSize =%zu", codecPrivateSize);
                    }
                } else if (!strcmp("V_MPEG2", codecID) || !strcmp("V_MPEG1", codecID)) {
                        meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_MPEG2);
                        if (codecPrivate != NULL) {
                            addESDSFromCodecPrivate(meta, false, codecPrivate, codecPrivateSize);
                        } else {
                            ALOGW("No specific codec private data, find it from the first frame");
                            meta->setInt32(kKeyCodecInfoIsInFirstFrame, true);
                        }
                }
#ifdef MTK_MKV_PLAYBACK_ENHANCEMENT
// Parser support but decoder not support or phone not support but tablet support are included in this FO.
                else if (!strcmp("V_MJPEG", codecID)) {
                        meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_MJPEG);
                }
#endif
                else if (!strcmp("V_MS/VFW/FOURCC", codecID)) {
                    ALOGV("Video CodecID: V_MS/VFW/FOURCC");
                    if (NULL == codecPrivate ||codecPrivateSize < 20) {
                        ALOGE("Unsupport video: V_MS/VFW/FOURCC has no invalid private data: \n");
                        ALOGE("codecPrivate=%p, codecPrivateSize=%zu", codecPrivate, codecPrivateSize);
                        continue;
                    } else {
                        uint32_t fourcc = *(uint32_t *)(codecPrivate + 16);
                        const char* mime = BMKVFourCC2MIME(fourcc);
                        ALOGV("V_MS/VFW/FOURCC type is %s", mime);
                        if (!strncasecmp("video/", mime, 6)) {
                            meta->setCString(kKeyMIMEType, mime);
                        } else {
                            ALOGE("V_MS/VFW/FOURCC continue,unsupport video type");
                            continue;
                        }
                        if (!strcmp(mime, MEDIA_MIMETYPE_VIDEO_AVC)) {
                            meta->setInt32(kKeyCodecInfoIsInFirstFrame, true);
#ifdef MTK_MKV_PLAYBACK_ENHANCEMENT
                        } else if (!strcmp(mime, MEDIA_MIMETYPE_VIDEO_DIVX)) {
                            meta->setInt32(kKeyCodecInfoIsInFirstFrame, true);
                        } else if (!strcmp(mime, MEDIA_MIMETYPE_VIDEO_XVID)) {
                            meta->setInt32(kKeyCodecInfoIsInFirstFrame, true);
#endif
                        } else if (!strcmp(mime, MEDIA_MIMETYPE_VIDEO_MPEG4)) {
                            meta->setInt32(kKeyCodecInfoIsInFirstFrame, true);
                        } else if (!strcmp(mime, MEDIA_MIMETYPE_VIDEO_HEVC)) {
                            meta->setInt32(kKeyCodecInfoIsInFirstFrame, true);
                        } else if (!strcmp(mime, MEDIA_MIMETYPE_VIDEO_H263)) {
                            meta->setInt32(kKeyCodecInfoIsInFirstFrame, true);
                        } else if (!strcmp(mime, MEDIA_MIMETYPE_VIDEO_MPEG2)) {
                            meta->setInt32(kKeyCodecInfoIsInFirstFrame, true);
                        } else {
                            ALOGW("FourCC have unsupport codec, fourcc=0x%08x.", fourcc);
                            continue;
                        }
                    }
                }
// end of add for mtk
               else {
                    ALOGW("%s is not supported.", codecID);
                    continue;
                }

                const long long width = vtrack->GetWidth();
                const long long height = vtrack->GetHeight();
                if (width <= 0 || width > INT32_MAX) {
                    ALOGW("track width exceeds int32_t, %lld", width);
                    continue;
                }
                if (height <= 0 || height > INT32_MAX) {
                    ALOGW("track height exceeds int32_t, %lld", height);
                    continue;
                }
                meta->setInt32(kKeyWidth, (int32_t)width);
                meta->setInt32(kKeyHeight, (int32_t)height);
                if (MKVLogOpen)
                    ALOGD("video track width = %lld, height = %lld", width, height);

                // setting display width/height is optional
                const long long displayUnit = vtrack->GetDisplayUnit();
                const long long displayWidth = vtrack->GetDisplayWidth();
                const long long displayHeight = vtrack->GetDisplayHeight();
                if (displayWidth > 0 && displayWidth <= INT32_MAX
                        && displayHeight > 0 && displayHeight <= INT32_MAX) {
                    switch (displayUnit) {
                    case 0:  // pixels
                        meta->setInt32(kKeyDisplayWidth, (int32_t)displayWidth);
                        meta->setInt32(kKeyDisplayHeight, (int32_t)displayHeight);
                        break;
                    case 1:  // centimeters
                    case 2:  // inches
                    case 3:  // aspect ratio
                    {
                        // Physical layout size is treated the same as aspect ratio.
                        // Note: displayWidth and displayHeight are never zero as they are
                        // checked in the if above.
                        const long long computedWidth =
                                std::max(width, height * displayWidth / displayHeight);
                        const long long computedHeight =
                                std::max(height, width * displayHeight / displayWidth);
                        if (computedWidth <= INT32_MAX && computedHeight <= INT32_MAX) {
                            meta->setInt32(kKeyDisplayWidth, (int32_t)computedWidth);
                            meta->setInt32(kKeyDisplayHeight, (int32_t)computedHeight);
                        }
                        break;
                    }
                    default:  // unknown display units, perhaps future version of spec.
                        break;
                    }
                }

                getColorInformation(vtrack, meta);

                break;
            }

            case AUDIO_TRACK:
            {
                const mkvparser::AudioTrack *atrack =
                    static_cast<const mkvparser::AudioTrack *>(track);

// add for mtk
                if (!strncasecmp("A_AAC", codecID, 5)) {
                    unsigned char aacCodecInfo[2] = {0, 0};
                    if (codecPrivateSize >= 2) {
                        // do nothing
                    } else if (NULL == codecPrivate) {
                        if (!strcasecmp("A_AAC", codecID)) {
                            ALOGW("Unspport AAC: No profile");
                            continue;
                        } else {
                            uint8_t freq_index = -1;
                            uint8_t profile;
                            if (!findAACSampleFreqIndex((uint32_t)atrack->GetSamplingRate(), &freq_index)) {
                                ALOGE("Unsupport AAC freq");
                                continue;
                            }

                            if (!strcasecmp("A_AAC/MPEG4/LC", codecID) ||
                                !strcasecmp("A_AAC/MPEG2/LC", codecID)) {
                                profile = 2;
                            } else if (!strcasecmp("A_AAC/MPEG4/LC/SBR", codecID) ||
                                !strcasecmp("A_AAC/MPEG2/LC/SBR", codecID)) {
                                profile = 5;
                            } else if (!strcasecmp("A_AAC/MPEG4/LTP", codecID)) {
                                profile = 4;
                            } else {
                                ALOGE("Unsupport AAC Codec profile %s", codecID);
                                continue;
                            }

                            codecPrivate = aacCodecInfo;
                            codecPrivateSize = 2;
                            aacCodecInfo[0] |= (profile << 3) & 0xf8;               // put it into the highest 5 bits
                            aacCodecInfo[0] |= ((freq_index >> 1) & 0x07);      // put 3 bits
                            aacCodecInfo[1] |= ((freq_index << 7) & 0x80);      // put 1 bit
                            aacCodecInfo[1] |= ((unsigned char)atrack->GetChannels()<< 3);
                            ALOGV("Make codec info 0x%x, 0x%x", aacCodecInfo[0], aacCodecInfo[1]);
                        }
                    } else {
                        ALOGE("Incomplete AAC Codec Info %zu byte", codecPrivateSize);
                        continue;
                    }
                    addESDSFromCodecPrivate(meta, true, codecPrivate, codecPrivateSize);
                    meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_AAC);
                    ALOGV("Audio Codec: %s", codecID);
                }
// end of add for mtk
#if 0  // android default code insteaded by mtk code
                if (!strcmp("A_AAC", codecID)) {
                    meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_AAC);
                    CHECK(codecPrivateSize >= 2);

                    addESDSFromCodecPrivate(
                            meta, true, codecPrivate, codecPrivateSize);
                }
#endif
                else if (!strcmp("A_VORBIS", codecID)) {
                    meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_VORBIS);

                    err = addVorbisCodecInfo(
                            meta, codecPrivate, codecPrivateSize);
                    ALOGD("Audio Codec: VORBIS,addVorbisCodecInfo return err=%d", err);
                } else if (!strcmp("A_OPUS", codecID)) {
                    meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_OPUS);
                    meta->setData(kKeyOpusHeader, 0, codecPrivate, codecPrivateSize);
                    meta->setInt64(kKeyOpusCodecDelay, track->GetCodecDelay());
                    meta->setInt64(kKeyOpusSeekPreRoll, track->GetSeekPreRoll());
                    mSeekPreRollNs = track->GetSeekPreRoll();
#if 0  // android default code insteaded by mtk code
                } else if (!strcmp("A_MPEG/L3", codecID)) {
                    meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_MPEG);
#endif
                } else if (!strcmp("A_FLAC", codecID)) {
                    meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_FLAC);
                    err = addFlacMetadata(meta, codecPrivate, codecPrivateSize);
                }
// add for mtk
#ifdef MTK_MKV_PLAYBACK_ENHANCEMENT
// only tablet support, phone not support
#ifdef MTK_AUDIO_RAW_SUPPORT
                else if (!strcmp("A_PCM/INT/LIT", codecID)) {
                    meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_RAW);
                    meta->setInt32(kKeyEndian, 2);
                    meta->setInt32(kKeyBitWidth, atrack->GetBitDepth());
                    meta->setInt32(kKeyPCMType, 1);
                    if (atrack->GetBitDepth() == 8) {
                        meta->setInt32(kKeyNumericalType, 2);
                    }
                } else if (!strcmp("A_PCM/INT/BIG", codecID)) {
                    meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_RAW);
                    meta->setInt32(kKeyEndian, 1);
                    meta->setInt32(kKeyBitWidth, atrack->GetBitDepth());
                    meta->setInt32(kKeyPCMType, 1);
                    if (atrack->GetBitDepth() == 8) {
                        meta->setInt32(kKeyNumericalType, 2);
                    }
                }
#endif
#endif
                else if (!strcmp("A_MPEG/L1", codecID)) {
                    meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_MPEG_LAYER_I);
                    ALOGV("Audio Codec: MPEG/L1");
                    if (atrack->GetChannels() > 2) {
                        ALOGE("Unsupport MP1 Channel count=%lld", (long long)atrack->GetChannels());
                        continue;
                    }
                    if ((atrack->GetSamplingRate() < 0.1) || (atrack->GetChannels() == 0)) {
                        meta->setInt32(kKeyCodecInfoIsInFirstFrame, true);
                    }
                } else if (!strcmp("A_MPEG/L3", codecID)) {
                    meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_MPEG);
                    if (atrack->GetChannels() > 2) {
                        ALOGE("Unsupport MP3 Channel count=%lld", (long long)atrack->GetChannels());
                        continue;
                    }
                    if (atrack->GetSamplingRate() < 0.1 || atrack->GetChannels() == 0) {
                        meta->setInt32(kKeyCodecInfoIsInFirstFrame, true);
                    }
                } else if (!strcmp("A_MPEG/L2", codecID)) {
                    meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_MPEG_LAYER_II);
                    ALOGV("Audio Codec: MPEG2");
                    if (atrack->GetChannels() > 2) {
                        ALOGE("Unsupport MP2 Channel count=%lld", (long long)atrack->GetChannels());
                        continue;
                    }
                    if (atrack->GetSamplingRate() < 0.1  || atrack->GetChannels() == 0) {
                        meta->setInt32(kKeyCodecInfoIsInFirstFrame, true);
                    }
                } else if ((!strcmp("A_MS/ACM", codecID))) {
                    if ((NULL == codecPrivate) || (codecPrivateSize < 8)) {
                        ALOGE("Unsupport audio: A_MS/ACM has no invalid private data: \n");
                        ALOGE("codecPrivate=%p, codecPrivateSize=%zu", codecPrivate, codecPrivateSize);
                        continue;
                    } else {
                        uint16_t ID = *(uint16_t *)codecPrivate;
                        const char* mime = MKVwave2MIME(ID);
                        ALOGV("A_MS/ACM type is %s", mime);
                        if (!strncasecmp("audio/", mime, 6)) {
                            meta->setCString(kKeyMIMEType, mime);
                        } else {
                            ALOGE("A_MS/ACM continue");
                            continue;
                        }
#if defined(MTK_AUDIO_ADPCM_SUPPORT) || defined(HAVE_ADPCMENCODE_FEATURE)
                        if (!strcmp(mime, MEDIA_MIMETYPE_AUDIO_DVI_IMA_ADPCM) ||
                            !strcmp(mime, MEDIA_MIMETYPE_AUDIO_MS_ADPCM)) {
                            uint32_t channel_count = *(uint16_t*)(codecPrivate + 2);
                            uint32_t sample_rate = *(uint32_t*)(codecPrivate + 4);
                            uint32_t BlockAlign = *(uint16_t*)(codecPrivate + 12);
                            uint32_t BitesPerSample = *(uint16_t*)(codecPrivate + 14);
                            uint32_t cbSize = *(uint16_t*)(codecPrivate + 16);
                            ALOGV("channel_count=%u, sample_rate=%u, BlockAlign=%u, BitesPerSampe=%u, cbSize=%u",
                                channel_count, sample_rate, BlockAlign, BitesPerSample, cbSize);
                            meta->setInt32(kKeySampleRate, sample_rate);
                            meta->setInt32(kKeyChannelCount, channel_count);
                            meta->setInt32(kKeyBlockAlign, BlockAlign);
                            meta->setInt32(kKeyBitsPerSample, BitesPerSample);
                            meta->setData(kKeyExtraDataPointer, 0, codecPrivate + 18, cbSize);
                        }
#endif
#ifdef MTK_MKV_PLAYBACK_ENHANCEMENT
// only tablet support, phone not support
#ifdef MTK_AUDIO_RAW_SUPPORT
                        if (!strcmp(mime, MEDIA_MIMETYPE_AUDIO_RAW)) {
                            uint32_t BitesPerSample = *(uint16_t*)(codecPrivate + 14);
                            meta->setInt32(kKeyBitWidth, BitesPerSample);
                            meta->setInt32(kKeyEndian, 2);
                            meta->setInt32(kKeyPCMType, 1);
                            if (BitesPerSample == 8)
                                meta->setInt32(kKeyNumericalType, 2);
                        }
#endif
#endif
                    }
                }
// end of add for mtk
                else {
                    ALOGW("%s is not supported.", codecID);
                    continue;
                }

                meta->setInt32(kKeySampleRate, atrack->GetSamplingRate());
                meta->setInt32(kKeyChannelCount, atrack->GetChannels());
                if (MKVLogOpen)
                    ALOGD("Samplerate=%f, channelcount=%lld", atrack->GetSamplingRate(), atrack->GetChannels());
                break;
            }

            default:
                continue;
        }

        if (err != OK) {
            ALOGE("skipping track, codec specific data was malformed.");
            continue;
        }

        long long durationNs = mSegment->GetDuration();
        meta->setInt64(kKeyDuration, (durationNs + 500) / 1000);

        mTracks.push();
        size_t n = mTracks.size() - 1;
        TrackInfo *trackInfo = &mTracks.editItemAt(n);
        initTrackInfo(track, meta, trackInfo);

        if (!strcmp("V_MPEG4/ISO/AVC", codecID) && codecPrivateSize == 0) {
            // Attempt to recover from AVC track without codec private data
            err = synthesizeAVCC(trackInfo, n);
            if (err != OK) {
                mTracks.pop();
            }
        }
    }
    ALOGD("addTracks DONE");
}

void MtkMatroskaExtractor::findThumbnails() {
    for (size_t i = 0; i < mTracks.size(); ++i) {
        TrackInfo *info = &mTracks.editItemAt(i);

        const char *mime;
        CHECK(info->mMeta->findCString(kKeyMIMEType, &mime));

        if (strncasecmp(mime, "video/", 6)) {
            continue;
        }

        MtkBlockIterator iter(this, info->mTrackNum, i);
        int32_t j = 0;
        int64_t thumbnailTimeUs = 0;
        size_t maxBlockSize = 0;
// add for mtk
        int64_t nowUs = ALooper::GetNowUs();
// end of add for mtk
        while (!iter.eos() && j < 20) {
            if (iter.block()->IsKey()) {
                ++j;

                size_t blockSize = 0;
                for (int k = 0; k < iter.block()->GetFrameCount(); ++k) {
                    blockSize += iter.block()->GetFrame(k).len;
                }

                if (blockSize > maxBlockSize) {
                    maxBlockSize = blockSize;
                    thumbnailTimeUs = iter.blockTimeUs();
                }
            }
// add for mtk
            int64_t timeUs = ALooper::GetNowUs();
            if ((timeUs - nowUs) > 3000000LL) {
                ALOGW("findThumbnails timed out 3s");
                break;
            }
// end of add for mtk
            iter.advance();
        }
        info->mMeta->setInt64(kKeyThumbnailTime, thumbnailTimeUs);
    }
}

sp<MetaData> MtkMatroskaExtractor::getMetaData() {
    sp<MetaData> meta = new MetaData;

    meta->setCString(
            kKeyMIMEType,
            mIsWebm ? "video/webm" : MEDIA_MIMETYPE_CONTAINER_MATROSKA);
    if (MKVLogOpen)
        ALOGI("getMetaData, %s", mIsWebm ? "video/webm" : "video/x-matroska");

    return meta;
}

uint32_t MtkMatroskaExtractor::flags() const {
    uint32_t x = CAN_PAUSE;
    if (!isLiveStreaming()) {
        x |= CAN_SEEK_BACKWARD | CAN_SEEK_FORWARD | CAN_SEEK;
    }

    return x;
}

bool SniffMatroska(
        const sp<DataSource> &source, String8 *mimeType, float *confidence,
        sp<AMessage> *) {
    MtkDataSourceReader reader(source);
    mkvparser::EBMLHeader ebmlHeader;
    long long pos;
    if (ebmlHeader.Parse(&reader, pos) < 0) {
        return false;
    }

    mimeType->setTo(MEDIA_MIMETYPE_CONTAINER_MATROSKA);
    *confidence = 0.6;

    return true;
}

// add for mtk
status_t MtkMatroskaSource::findMP3Header(uint32_t * header) {
    if (header != NULL) {
        *header = 0;
    } else {
        ALOGE("header is null!");
        return ERROR_END_OF_STREAM;
    }

    uint32_t code = 0;
    while (0 == *header) {
        while (mPendingFrames.empty()) {
            status_t err = readBlock();

            if (err != OK) {
                clearPendingFrames();
                return err;
            }
        }
        MediaBuffer *frame = *mPendingFrames.begin();
        size_t size = frame->range_length();
        size_t offset = frame->range_offset();
        size_t i;
        size_t frame_size;
        for (i = 0; i < size; i++) {
            if (MKVLogOpen)
                ALOGD("data[%zu]=%x", i, *((uint8_t*)frame->data() + offset + i));
            code = (code << 8) + *((uint8_t*)frame->data() + offset + i);
            if (get_mp3_info(code, &frame_size, NULL, NULL, NULL)) {
                *header = code;
                mBlockIter.reset();
                clearPendingFrames();
                return OK;
            }
        }
    }

    return ERROR_END_OF_STREAM;
}

void MtkBlockIterator::seekwithoutcue(int64_t seekTimeUs) {
    //    Mutex::Autolock autoLock(mExtractor->mLock);
    mCluster = mExtractor->mSegment->FindCluster(seekTimeUs * 1000ll);
    const long status = mCluster->GetFirst(mBlockEntry);
    if (status < 0) {  // error
        ALOGE("get last blockenry failed!");
        mCluster = NULL;
        return;
    }
    //    mBlockEntry = mCluster != NULL ? mCluster->GetFirst(mBlockEntry): NULL;
    //    mBlockEntry = NULL;
    mBlockEntryIndex = 0;

    while (!eos() && ((block()->GetTrackNumber() != mTrackNum) || (blockTimeUs() < seekTimeUs))) {
        advance_l();
    }

    while (!eos() && !mBlockEntry->GetBlock()->IsKey() && (mTrackType != 2)/*Audio*/) {  // hai.li
        advance_l();
    }
}

void MtkMatroskaSource::setCodecInfoFromFirstFrame() {
    clearPendingFrames();
    int64_t actualFrameTimeUs;
        mBlockIter.seek(0, mIsAudio, &actualFrameTimeUs);
    // mBlockIter.seek(0);

    status_t err = readBlock();
    if (err != OK) {
        ALOGE("read codec info from first block fail!");
        mBlockIter.reset();
        clearPendingFrames();
        return;
    }
    if (mPendingFrames.empty()) {
        return;
    }
    if (MPEG4 == mType) {
        size_t vosend;
        for (vosend = 0; (vosend < 200) && (vosend < (*mPendingFrames.begin())->range_length() - 4); vosend++) {
            if (0xB6010000 == *(uint32_t*)((uint8_t*)((*mPendingFrames.begin())->data()) + vosend)) {
                break;  // Send VOS until VOP
            }
        }
        // getFormat()->setData(kKeyMPEG4VOS, 0, (*mPendingFrames.begin())->data(), vosend);
        addESDSFromCodecPrivate(
                getFormat(), false, (*mPendingFrames.begin())->data(), vosend);

        // for (int32_t i=0; i<vosend; i++)
        //     ALOGD("VOS[%d] = 0x%x", i, *((uint8_t *)((*mPendingFrames.begin())->data()) + i));
    } else if (MPEG2 == mType) {
        size_t header_start = 0;
        size_t header_lenth = 0;
        for (header_start = 0;
                (header_start < 200) && (header_start < (*mPendingFrames.begin())->range_length() - 4);
                header_start++) {
            if (0xB3010000 == *(uint32_t*)((uint8_t*)((*mPendingFrames.begin())->data()) + header_start)) {
                break;
            }
        }
        for (header_lenth = 0;
                (header_lenth < 200) && (header_lenth < (*mPendingFrames.begin())->range_length() - 4 - header_start);
                header_lenth++) {
            if (0xB8010000 == *(uint32_t*)((uint8_t*)((*mPendingFrames.begin())->data())
                                    + header_start + header_lenth)) {
                break;
            }
        }
        for (size_t i = 0; i < header_lenth; i++)
            if (MKVLogOpen)
                ALOGD("MPEG2info[%zu] = 0x%x", i, *((uint8_t *)((*mPendingFrames.begin())->data()) + i + header_start));
            addESDSFromCodecPrivate(getFormat(), false,
                                        (uint8_t*)((*mPendingFrames.begin())->data()) + header_start, header_lenth);
    } else if (MP2_3 == mType) {
        uint32_t header = *(uint32_t*)((uint8_t*)(*mPendingFrames.begin())->data()
                                + (*mPendingFrames.begin())->range_offset());
        header = ((header >> 24) & 0xff) | ((header >> 8) & 0xff00)
                    | ((header << 8) & 0xff0000) | ((header << 24) & 0xff000000);
        if (MKVLogOpen)
            ALOGD("HEADER = 0x%x", header);
        size_t frame_size;
        int32_t out_sampling_rate;
        int32_t out_channels;
        int32_t out_bitrate;
        if (!get_mp3_info(header, &frame_size, &out_sampling_rate, &out_channels, &out_bitrate)) {
            ALOGE("Get mp3 info fail");
            return;
        }
        ALOGV("mp3: frame_size=%zu, sample_rate=%d, channel_count=%d, out_bitrate=%d",
                       frame_size, out_sampling_rate, out_channels, out_bitrate);
        if (out_channels > 2) {
            ALOGE("Unsupport mp3 channel count %d", out_channels);
            return;
        }
        getFormat()->setInt32(kKeySampleRate, out_sampling_rate);
        getFormat()->setInt32(kKeyChannelCount, out_channels);
    }

    mBlockIter.reset();
    clearPendingFrames();
}
// end of add for mtk
}  // namespace android
