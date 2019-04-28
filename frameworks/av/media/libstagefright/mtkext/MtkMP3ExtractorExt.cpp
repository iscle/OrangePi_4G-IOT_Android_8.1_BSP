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
#define LOG_TAG "MtkMP3SourceExt"

#include "mtkext/include/MtkMP3ExtractorExt.h"

#include "include/avc_utils.h"
#include "include/ID3.h"
#include "include/VBRISeeker.h"
#include "include/XINGSeeker.h"

#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/AMessage.h>
#include <media/stagefright/DataSource.h>
#include <media/stagefright/MediaBuffer.h>
#include <media/stagefright/MediaBufferGroup.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MetaData.h>
#include <media/stagefright/Utils.h>
#include <utils/String8.h>


namespace android {

static const uint32_t kMask = 0xfffe0c00;

////////////////////////////////////////////////////////////////////////////////

MtkMP3SourceExt::MtkMP3SourceExt()
{

}

MtkMP3SourceExt::~MtkMP3SourceExt()
{

}

int MtkMP3SourceExt::getMultiFrameSize(const sp<DataSource> &source, off64_t pos,
                uint32_t fixedHeader, size_t *frame_sizes, int *num_samples,
                int *sample_rate, int request_frames)
{
    uint8_t headbuf[4]={0};
    size_t frameSize = 0;
    int numSamples = 0;
    ssize_t frameCount = 0;

    *frame_sizes = 0;
    *num_samples = 0;
    *sample_rate = 0;

    for(int i = 0; i< request_frames; i++) {
        ssize_t n = source->readAt(pos,headbuf,4);

        if (n < 4) {
            ALOGV("Bad header");
            return frameCount;
        }
        uint32_t header = U32_AT((const uint8_t *)headbuf);

        if ((header & kMask) == (fixedHeader & kMask)
                && GetMPEGAudioFrameSize(
                    header, &frameSize, sample_rate, NULL,
                    NULL, &numSamples)) {

            *frame_sizes += frameSize;//20 frame length
            *num_samples += numSamples;
            frameCount++;
            pos += frameSize; //new position
        } else {
            break;
        }
    }

    return frameCount;
}

//copy from MP3Extractor
static bool Resync(
        const sp<DataSource> &source, uint32_t match_header,
        off64_t *inout_pos, off64_t *post_id3_pos, uint32_t *out_header) {
    if (post_id3_pos != NULL) {
        *post_id3_pos = 0;
    }

    if (*inout_pos == 0) {
        // Skip an optional ID3 header if syncing at the very beginning
        // of the datasource.

        for (;;) {
            uint8_t id3header[10];
            if (source->readAt(*inout_pos, id3header, sizeof(id3header))
                    < (ssize_t)sizeof(id3header)) {
                // If we can't even read these 10 bytes, we might as well bail
                // out, even if there _were_ 10 bytes of valid mp3 audio data...
                return false;
            }

            if (memcmp("ID3", id3header, 3)) {
                break;
            }

            // Skip the ID3v2 header.

            size_t len =
                ((id3header[6] & 0x7f) << 21)
                | ((id3header[7] & 0x7f) << 14)
                | ((id3header[8] & 0x7f) << 7)
                | (id3header[9] & 0x7f);

            len += 10;

            *inout_pos += len;

            ALOGV("skipped ID3 tag, new starting offset is %lld (0x%016llx)",
                    (long long)*inout_pos, (long long)*inout_pos);
        }

        if (post_id3_pos != NULL) {
            *post_id3_pos = *inout_pos;
        }
    }

    off64_t pos = *inout_pos;
    bool valid = false;

    const size_t kMaxReadBytes = 1024;
    const size_t kMaxBytesChecked = 128 * 1024;
    uint8_t buf[kMaxReadBytes];
    ssize_t bytesToRead = kMaxReadBytes;
    ssize_t totalBytesRead = 0;
    ssize_t remainingBytes = 0;
    bool reachEOS = false;
    uint8_t *tmp = buf;

    do {
        if (pos >= (off64_t)(*inout_pos + kMaxBytesChecked)) {
            // Don't scan forever.
            ALOGV("giving up at offset %lld", (long long)pos);
            break;
        }

        if (remainingBytes < 4) {
            if (reachEOS) {
                break;
            } else {
                memcpy(buf, tmp, remainingBytes);
                bytesToRead = kMaxReadBytes - remainingBytes;

                /*
                 * The next read position should start from the end of
                 * the last buffer, and thus should include the remaining
                 * bytes in the buffer.
                 */
                totalBytesRead = source->readAt(pos + remainingBytes,
                                                buf + remainingBytes,
                                                bytesToRead);
                if (totalBytesRead <= 0) {
                    break;
                }
                reachEOS = (totalBytesRead != bytesToRead);
                totalBytesRead += remainingBytes;
                remainingBytes = totalBytesRead;
                tmp = buf;
                continue;
            }
        }

        uint32_t header = U32_AT(tmp);

        if (match_header != 0 && (header & kMask) != (match_header & kMask)) {
            ++pos;
            ++tmp;
            --remainingBytes;
            continue;
        }

        size_t frame_size;
        int sample_rate, num_channels, bitrate;
        if (!GetMPEGAudioFrameSize(
                    header, &frame_size,
                    &sample_rate, &num_channels, &bitrate)) {
            ++pos;
            ++tmp;
            --remainingBytes;
            continue;
        }

        ALOGV("found possible 1st frame at %lld (header = 0x%08x)", (long long)pos, header);

        // We found what looks like a valid frame,
        // now find its successors.

        off64_t test_pos = pos + frame_size;

        valid = true;
        for (int j = 0; j < 3; ++j) {
            uint8_t tmp[4];
            if (source->readAt(test_pos, tmp, 4) < 4) {
                valid = false;
                break;
            }

            uint32_t test_header = U32_AT(tmp);

            ALOGV("subsequent header is %08x", test_header);

            if ((test_header & kMask) != (header & kMask)) {
                valid = false;
                break;
            }

            size_t test_frame_size;
            if (!GetMPEGAudioFrameSize(
                        test_header, &test_frame_size)) {
                valid = false;
                break;
            }

            ALOGV("found subsequent frame #%d at %lld", j + 2, (long long)test_pos);

            test_pos += test_frame_size;
        }

        if (valid) {
            *inout_pos = pos;

            if (out_header != NULL) {
                *out_header = header;
            }
        } else {
            ALOGV("no dice, no valid sequence of frames found.");
        }

        ++pos;
        ++tmp;
        --remainingBytes;
    } while (!valid);

    return valid;
}

int isJointStereoMp3(const sp<DataSource> &source, off64_t pos,
        uint32_t fixedHeader)
{
    const int request_frames = 9;
    uint8_t headbuf[4]={0};
    size_t frameSize = 0;
    int ch = 0;

    for(int i = 0; i< request_frames; i++) {
        int cur_ch = 0;
        ssize_t n = source->readAt(pos,headbuf,4);

        if (n < 4) {
            ALOGV("Bad header");
            return false;
        }
        uint32_t header = U32_AT((const uint8_t *)headbuf);

        if ((header & kMask) == (fixedHeader & kMask)
                && GetMPEGAudioFrameSize(
                    header, &frameSize, NULL, &cur_ch,
                    NULL, NULL)) {
            pos += frameSize; //new position
            if (ch == 0) {
                ch = cur_ch;
            }
            if (ch != cur_ch) {
                ALOGI("JointStereo type (%d, %d)", ch, cur_ch);
                return true;
            }
        } else {
            if (!Resync(source, fixedHeader, &pos, NULL, NULL)) {
                return false;
            }
        }
    }

    return false;
}

}  // namespace android
