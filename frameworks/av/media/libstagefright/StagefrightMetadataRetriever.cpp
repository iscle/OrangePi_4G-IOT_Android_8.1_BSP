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
#define LOG_TAG "StagefrightMetadataRetriever"

#include <inttypes.h>

#include <utils/Log.h>
#include <gui/Surface.h>

#include "include/avc_utils.h"
#include "include/StagefrightMetadataRetriever.h"

#include <media/ICrypto.h>
#include <media/IMediaHTTPService.h>
#include <media/MediaCodecBuffer.h>

#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/AMessage.h>
#include <media/stagefright/ColorConverter.h>
#include <media/stagefright/DataSource.h>
#include <media/stagefright/FileSource.h>
#include <media/stagefright/MediaBuffer.h>
#include <media/stagefright/MediaCodec.h>
#include <media/stagefright/MediaCodecList.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MediaExtractor.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MetaData.h>
#include <media/stagefright/Utils.h>

#include <media/CharacterEncodingDetector.h>

#ifdef MTK_DRM_APP
#include <drmutils/drm_utils_mtk.h>
#include <utils/String8.h>
#endif
namespace android {

static const int64_t kBufferTimeOutUs = 30000ll; // 30 msec
static const size_t kRetryCount = 20; // must be >0

StagefrightMetadataRetriever::StagefrightMetadataRetriever()
    : mParsedMetaData(false),
      mAlbumArt(NULL) {
    ALOGV("StagefrightMetadataRetriever()");
}

StagefrightMetadataRetriever::~StagefrightMetadataRetriever() {
    ALOGV("~StagefrightMetadataRetriever()");
    clearMetadata();
    // Explicitly release extractor before continuing with the destructor,
    // some extractors might need to callback to close off the DataSource
    // and we need to make sure it's still there.
    if (mExtractor != NULL) {
        mExtractor->release();
    }
    if (mSource != NULL) {
        mSource->close();
    }
}

status_t StagefrightMetadataRetriever::setDataSource(
        const sp<IMediaHTTPService> &httpService,
        const char *uri,
        const KeyedVector<String8, String8> *headers) {
    ALOGV("setDataSource(%s)", uri);

    clearMetadata();
    mSource = DataSource::CreateFromURI(httpService, uri, headers);

    if (mSource == NULL) {
        ALOGE("Unable to create data source for '%s'.", uri);
        return UNKNOWN_ERROR;
    }

    mExtractor = MediaExtractor::Create(mSource);

#ifdef MTK_DRM_APP
    // after it attempts to create extractor: for .dcf file with invalid rights,
    //   the mExtractor will be NULL. We need to return OK here directly.
    if (mExtractor == NULL && IsOMADrm(uri)) {
        // we assume it's file path name - for OMA DRM v1
        ALOGD("setDataSource() : it is a OMA DRM v1 .dcf file. return OK");
        return OK;
    }
#endif
    if (mExtractor == NULL) {
        ALOGE("Unable to instantiate an extractor for '%s'.", uri);

        mSource.clear();

        return UNKNOWN_ERROR;
    }

    return OK;
}

// Warning caller retains ownership of the filedescriptor! Dup it if necessary.
status_t StagefrightMetadataRetriever::setDataSource(
        int fd, int64_t offset, int64_t length) {
    fd = dup(fd);

    ALOGV("setDataSource(%d, %" PRId64 ", %" PRId64 ")", fd, offset, length);

    clearMetadata();
    mSource = new FileSource(fd, offset, length);

    status_t err;
    if ((err = mSource->initCheck()) != OK) {
        mSource.clear();

        return err;
    }

    mExtractor = MediaExtractor::Create(mSource);

#ifdef MTK_DRM_APP
    // OMA DRM v1 implementation:
    // after it attempts to create extractor: for .dcf file with invalid rights,
    //   the mExtractor will be NULL. We need to return OK here directly.
    if (mExtractor == NULL && IsOMADrm(fd)) {
        ALOGD("setDataSource() : it is a OMA DRM v1 .dcf file. return OK");
        return OK;
    }
#endif
    if (mExtractor == NULL) {
        mSource.clear();

        return UNKNOWN_ERROR;
    }

    return OK;
}

status_t StagefrightMetadataRetriever::setDataSource(
        const sp<DataSource>& source, const char *mime) {
    ALOGV("setDataSource(DataSource)");

    clearMetadata();
    mSource = source;
    mExtractor = MediaExtractor::Create(mSource, mime);

    if (mExtractor == NULL) {
        ALOGE("Failed to instantiate a MediaExtractor.");
        mSource.clear();
        return UNKNOWN_ERROR;
    }

    return OK;
}

static VideoFrame *allocVideoFrame(
        const sp<MetaData> &trackMeta, int32_t width, int32_t height, int32_t bpp, bool metaOnly) {
    int32_t rotationAngle;
    if (!trackMeta->findInt32(kKeyRotation, &rotationAngle)) {
        rotationAngle = 0;  // By default, no rotation
    }

    uint32_t type;
    const void *iccData;
    size_t iccSize;
    if (!trackMeta->findData(kKeyIccProfile, &type, &iccData, &iccSize)){
        iccData = NULL;
        iccSize = 0;
    }

    int32_t sarWidth, sarHeight;
    int32_t displayWidth, displayHeight;
    if (trackMeta->findInt32(kKeySARWidth, &sarWidth)
            && trackMeta->findInt32(kKeySARHeight, &sarHeight)
            && sarHeight != 0) {
        displayWidth = (width * sarWidth) / sarHeight;
        displayHeight = height;
    } else if (trackMeta->findInt32(kKeyDisplayWidth, &displayWidth)
                && trackMeta->findInt32(kKeyDisplayHeight, &displayHeight)
                && displayWidth > 0 && displayHeight > 0
                && width > 0 && height > 0) {
        ALOGV("found display size %dx%d", displayWidth, displayHeight);
    } else {
        displayWidth = width;
        displayHeight = height;
    }

    return new VideoFrame(width, height, displayWidth, displayHeight,
            rotationAngle, bpp, !metaOnly, iccData, iccSize);
}

static bool getDstColorFormat(android_pixel_format_t colorFormat,
        OMX_COLOR_FORMATTYPE *omxColorFormat, int32_t *bpp) {
    switch (colorFormat) {
        case HAL_PIXEL_FORMAT_RGB_565:
        {
            *omxColorFormat = OMX_COLOR_Format16bitRGB565;
            *bpp = 2;
            return true;
        }
        case HAL_PIXEL_FORMAT_RGBA_8888:
        {
            *omxColorFormat = OMX_COLOR_Format32BitRGBA8888;
            *bpp = 4;
            return true;
        }
        case HAL_PIXEL_FORMAT_BGRA_8888:
        {
            *omxColorFormat = OMX_COLOR_Format32bitBGRA8888;
            *bpp = 4;
            return true;
        }
        default:
        {
            ALOGE("Unsupported color format: %d", colorFormat);
            break;
        }
    }
    return false;
}

static VideoFrame *extractVideoFrame(
        const AString &componentName,
        const sp<MetaData> &trackMeta,
        const sp<IMediaSource> &source,
        int64_t frameTimeUs,
        int seekMode,
        int colorFormat,
        bool metaOnly) {
    sp<MetaData> format = source->getFormat();

    MediaSource::ReadOptions::SeekMode mode =
            static_cast<MediaSource::ReadOptions::SeekMode>(seekMode);
    if (seekMode < MediaSource::ReadOptions::SEEK_PREVIOUS_SYNC ||
        seekMode > MediaSource::ReadOptions::SEEK_CLOSEST) {
        ALOGE("Unknown seek mode: %d", seekMode);
        return NULL;
    }

    int32_t dstBpp;
    OMX_COLOR_FORMATTYPE dstFormat;
#ifdef MTK_HIGH_QUALITY_THUMBNAIL
    colorFormat = HAL_PIXEL_FORMAT_RGBA_8888;
#endif
    if (!getDstColorFormat(
            (android_pixel_format_t)colorFormat, &dstFormat, &dstBpp)) {
        return NULL;
    }

    if (metaOnly) {
        int32_t width, height;
        CHECK(trackMeta->findInt32(kKeyWidth, &width));
        CHECK(trackMeta->findInt32(kKeyHeight, &height));
        return allocVideoFrame(trackMeta, width, height, dstBpp, true);
    }

    MediaSource::ReadOptions options;
    sp<MetaData> overrideMeta;
    if (frameTimeUs < 0) {
        uint32_t type;
        const void *data;
        size_t size;
        int64_t thumbNailTime;
        int32_t thumbnailWidth, thumbnailHeight;

        // if we have a stand-alone thumbnail, set up the override meta,
        // and set seekTo time to -1.
        if (trackMeta->findInt32(kKeyThumbnailWidth, &thumbnailWidth)
         && trackMeta->findInt32(kKeyThumbnailHeight, &thumbnailHeight)
         && trackMeta->findData(kKeyThumbnailHVCC, &type, &data, &size)){
            overrideMeta = new MetaData(*trackMeta);
            overrideMeta->remove(kKeyDisplayWidth);
            overrideMeta->remove(kKeyDisplayHeight);
            overrideMeta->setInt32(kKeyWidth, thumbnailWidth);
            overrideMeta->setInt32(kKeyHeight, thumbnailHeight);
            overrideMeta->setData(kKeyHVCC, type, data, size);
            thumbNailTime = -1ll;
            ALOGV("thumbnail: %dx%d", thumbnailWidth, thumbnailHeight);
        } else if (!trackMeta->findInt64(kKeyThumbnailTime, &thumbNailTime)
                || thumbNailTime < 0) {
            thumbNailTime = 0;
        }

        options.setSeekTo(thumbNailTime, mode);
    } else {
        options.setSeekTo(frameTimeUs, mode);
    }

    int32_t gridRows = 1, gridCols = 1;
    if (overrideMeta == NULL) {
        // check if we're dealing with a tiled heif
        int32_t gridWidth, gridHeight;
        if (trackMeta->findInt32(kKeyGridWidth, &gridWidth) && gridWidth > 0
         && trackMeta->findInt32(kKeyGridHeight, &gridHeight) && gridHeight > 0) {
            int32_t width, height, displayWidth, displayHeight;
            CHECK(trackMeta->findInt32(kKeyWidth, &width));
            CHECK(trackMeta->findInt32(kKeyHeight, &height));
            CHECK(trackMeta->findInt32(kKeyDisplayWidth, &displayWidth));
            CHECK(trackMeta->findInt32(kKeyDisplayHeight, &displayHeight));

            if (width >= displayWidth && height >= displayHeight
                    && (width % gridWidth == 0) && (height % gridHeight == 0)) {
                ALOGV("grid config: %dx%d, display %dx%d, grid %dx%d",
                        width, height, displayWidth, displayHeight, gridWidth, gridHeight);

                overrideMeta = new MetaData(*trackMeta);
                overrideMeta->remove(kKeyDisplayWidth);
                overrideMeta->remove(kKeyDisplayHeight);
                overrideMeta->setInt32(kKeyWidth, gridWidth);
                overrideMeta->setInt32(kKeyHeight, gridHeight);
                gridCols = width / gridWidth;
                gridRows = height / gridHeight;
            } else {
                ALOGE("Bad grid config: %dx%d, display %dx%d, grid %dx%d",
                        width, height, displayWidth, displayHeight, gridWidth, gridHeight);
            }
        }
        if (overrideMeta == NULL) {
            overrideMeta = trackMeta;
        }
    }
    int32_t numTiles = gridRows * gridCols;

    sp<AMessage> videoFormat;
    if (convertMetaDataToMessage(overrideMeta, &videoFormat) != OK) {
        ALOGE("b/23680780");
        ALOGW("Failed to convert meta data to message");
        return NULL;
    }

    // TODO: Use Flexible color instead
    videoFormat->setInt32("color-format", OMX_COLOR_FormatYUV420Planar);

    // For the thumbnail extraction case, try to allocate single buffer in both
    // input and output ports, if seeking to a sync frame. NOTE: This request may
    // fail if component requires more than that for decoding.
    bool isSeekingClosest = (seekMode == MediaSource::ReadOptions::SEEK_CLOSEST);
    bool decodeSingleFrame = !isSeekingClosest && (numTiles == 1);
    if (decodeSingleFrame) {
        videoFormat->setInt32("android._num-input-buffers", 1);
        videoFormat->setInt32("android._num-output-buffers", 1);
    }

    status_t err;
    sp<ALooper> looper = new ALooper;
    looper->start();
    sp<MediaCodec> decoder = MediaCodec::CreateByComponentName(
            looper, componentName, &err);

    if (decoder.get() == NULL || err != OK) {
        ALOGW("Failed to instantiate decoder [%s]", componentName.c_str());
        return NULL;
    }
#ifdef MTK_THUMBNAIL_OPTIMIZATION
     if (!isSeekingClosest)
         err = decoder->configure(videoFormat, NULL /* surface */, NULL /* crypto */,
                              MediaCodec::CONFIGURE_FLAG_ENABLE_THUMBNAIL_OPTIMIZATION /* flags */);
     else
         err = decoder->configure(videoFormat, NULL /* surface */, NULL /* crypto */, 0 /* flags */);
#else
    err = decoder->configure(videoFormat, NULL /* surface */, NULL /* crypto */, 0 /* flags */);
#endif

    if (err != OK) {
        ALOGW("configure returned error %d (%s)", err, asString(err));
        decoder->release();
        return NULL;
    }

    err = decoder->start();
    if (err != OK) {
        ALOGW("start returned error %d (%s)", err, asString(err));
        decoder->release();
        return NULL;
    }

    err = source->start();
    if (err != OK) {
        ALOGW("source failed to start: %d (%s)", err, asString(err));
        decoder->release();
        return NULL;
    }

    Vector<sp<MediaCodecBuffer> > inputBuffers;
    err = decoder->getInputBuffers(&inputBuffers);
    if (err != OK) {
        ALOGW("failed to get input buffers: %d (%s)", err, asString(err));
        decoder->release();
        source->stop();
        return NULL;
    }

    Vector<sp<MediaCodecBuffer> > outputBuffers;
    err = decoder->getOutputBuffers(&outputBuffers);
    if (err != OK) {
        ALOGW("failed to get output buffers: %d (%s)", err, asString(err));
        decoder->release();
        source->stop();
        return NULL;
    }

    sp<AMessage> outputFormat = NULL;
    bool haveMoreInputs = true;
    size_t index, offset, size;
    int64_t timeUs;
    size_t retriesLeft = kRetryCount;
    bool done = false;
#ifdef MTK_THUMBNAIL_OPTIMIZATION
    int Numinputbuffers = 0;
    bool HaveAvcOrHevcIDR = false;
#endif
    const char *mime;
    bool success = format->findCString(kKeyMIMEType, &mime);
    if (!success) {
        ALOGE("Could not find mime type");
        return NULL;
    }

    bool isAvcOrHevc = !strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_AVC)
            || !strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_HEVC);

    bool firstSample = true;
    int64_t targetTimeUs = -1ll;

    VideoFrame *frame = NULL;
    int32_t tilesDecoded = 0;

    do {
        size_t inputIndex = -1;
        int64_t ptsUs = 0ll;
        uint32_t flags = 0;
        sp<MediaCodecBuffer> codecBuffer = NULL;

        while (haveMoreInputs) {
            err = decoder->dequeueInputBuffer(&inputIndex, kBufferTimeOutUs);
            if (err != OK) {
                ALOGW("Timed out waiting for input");
#ifdef MTK_THUMBNAIL_OPTIMIZATION
                if (--retriesLeft) {
#else
                if (retriesLeft) {
#endif
                    err = OK;
                }
                break;
            }
            codecBuffer = inputBuffers[inputIndex];

            MediaBuffer *mediaBuffer = NULL;

            err = source->read(&mediaBuffer, &options);
            options.clearSeekTo();
            if (err != OK) {
                ALOGW("Input Error or EOS");
                haveMoreInputs = false;
                if (err == ERROR_END_OF_STREAM) {
#ifdef MTK_THUMBNAIL_OPTIMIZATION
                    Numinputbuffers = Numinputbuffers + 2;  //  read EOS,break read input loop
                    err = decoder->queueInputBuffer(
                            inputIndex,
                            0,
                            0,
                            0,
                            MediaCodec::BUFFER_FLAG_EOS);
#endif
                    err = OK;
                }
                break;
            }
            if (firstSample && isSeekingClosest) {
                mediaBuffer->meta_data()->findInt64(kKeyTargetTime, &targetTimeUs);
                ALOGV("Seeking closest: targetTimeUs=%lld", (long long)targetTimeUs);
            }
            firstSample = false;

            if (mediaBuffer->range_length() > codecBuffer->capacity()) {
                ALOGE("buffer size (%zu) too large for codec input size (%zu)",
                        mediaBuffer->range_length(), codecBuffer->capacity());
                haveMoreInputs = false;
                err = BAD_VALUE;
            } else {
                codecBuffer->setRange(0, mediaBuffer->range_length());

                CHECK(mediaBuffer->meta_data()->findInt64(kKeyTime, &ptsUs));
                memcpy(codecBuffer->data(),
                        (const uint8_t*)mediaBuffer->data() + mediaBuffer->range_offset(),
                        mediaBuffer->range_length());
            }

            mediaBuffer->release();
            break;
        }

        if (haveMoreInputs && inputIndex < inputBuffers.size()) {
#ifdef MTK_THUMBNAIL_OPTIMIZATION  //  add for avc or hevc top&bottom case
            if (!isSeekingClosest) {
                if (isAvcOrHevc && (!HaveAvcOrHevcIDR) && IsIDR(codecBuffer)) {
                    HaveAvcOrHevcIDR = true;
                } else if (HaveAvcOrHevcIDR) {
                    // sometimes codec need two input frame to decode one correct output.
                    haveMoreInputs = false;
                    flags |= MediaCodec::BUFFER_FLAG_EOS;
                }
            }
#else
            if (isAvcOrHevc && IsIDR(codecBuffer) && decodeSingleFrame) {
                // Only need to decode one IDR frame, unless we're seeking with CLOSEST
                // option, in which case we need to actually decode to targetTimeUs.
                haveMoreInputs = false;
                flags |= MediaCodec::BUFFER_FLAG_EOS;
            }
#endif

            ALOGV("QueueInput: size=%zu ts=%" PRId64 " us flags=%x",
                    codecBuffer->size(), ptsUs, flags);
            err = decoder->queueInputBuffer(
                    inputIndex,
                    codecBuffer->offset(),
                    codecBuffer->size(),
                    ptsUs,
                    flags);
#ifdef MTK_THUMBNAIL_OPTIMIZATION
            Numinputbuffers++;
#endif
            // we don't expect an output from codec config buffer
            if (flags & MediaCodec::BUFFER_FLAG_CODECCONFIG) {
                continue;
            }
        }
#ifdef MTK_THUMBNAIL_OPTIMIZATION
        while (err == OK && Numinputbuffers > 1) {
#else
        while (err == OK) {
#endif
            // wait for a decoded buffer
            err = decoder->dequeueOutputBuffer(
                    &index,
                    &offset,
                    &size,
                    &timeUs,
                    &flags,
                    kBufferTimeOutUs);

            if (err == INFO_FORMAT_CHANGED) {
                ALOGV("Received format change");
                err = decoder->getOutputFormat(&outputFormat);
            } else if (err == INFO_OUTPUT_BUFFERS_CHANGED) {
                ALOGV("Output buffers changed");
                err = decoder->getOutputBuffers(&outputBuffers);
            } else {
                if (err == -EAGAIN /* INFO_TRY_AGAIN_LATER */ && --retriesLeft > 0) {
                    ALOGV("Timed-out waiting for output.. retries left = %zu", retriesLeft);
                    err = OK;
                } else if (err == OK) {
#ifdef MTK_THUMBNAIL_OPTIMIZATION
                    if(outputFormat == NULL){
                        ALOGE("get outputFormat fail");
                        source->stop();
                        decoder->release();
                        if(frame != NULL){
                            delete frame;
                            frame = NULL;
                        }
                        return NULL;
                    }
#endif
                    // If we're seeking with CLOSEST option and obtained a valid targetTimeUs
                    // from the extractor, decode to the specified frame. Otherwise we're done.
                    ALOGV("Received an output buffer, timeUs=%lld", (long long)timeUs);
                    sp<MediaCodecBuffer> videoFrameBuffer = outputBuffers.itemAt(index);

                    int32_t width, height;
                    CHECK(outputFormat != NULL);
                    CHECK(outputFormat->findInt32("width", &width));
                    CHECK(outputFormat->findInt32("height", &height));
#ifdef MTK_THUMBNAIL_OPTIMIZATION
                    int32_t Stridewidth, SliceHeight;
                    CHECK(outputFormat->findInt32("stride", &Stridewidth));
                    CHECK(outputFormat->findInt32("slice-height", &SliceHeight));
                    ALOGD("kKeyWidth=%d, kKeyHeight=%d", width, height);
                    ALOGD("Stridewidth=%d, SliceHeight=%d", Stridewidth, SliceHeight);
#endif
                    int32_t crop_left, crop_top, crop_right, crop_bottom;
                    if (!outputFormat->findRect("crop", &crop_left, &crop_top, &crop_right, &crop_bottom)) {
                        crop_left = crop_top = 0;
                        crop_right = width - 1;
                        crop_bottom = height - 1;
                    }

                    if (frame == NULL) {
                        frame = allocVideoFrame(
                                trackMeta,
                                (crop_right - crop_left + 1) * gridCols,
                                (crop_bottom - crop_top + 1) * gridRows,
                                dstBpp,
                                false /*metaOnly*/);
                    }

                    int32_t srcFormat;
                    CHECK(outputFormat->findInt32("color-format", &srcFormat));
#ifdef MTK_THUMBNAIL_OPTIMIZATION
                    width = Stridewidth;
                    height = SliceHeight;
#endif
                    ColorConverter converter((OMX_COLOR_FORMATTYPE)srcFormat, dstFormat);

                    int32_t dstLeft, dstTop, dstRight, dstBottom;
                    if (numTiles == 1) {
                        dstLeft = crop_left;
                        dstTop = crop_top;
                        dstRight = crop_right;
                        dstBottom = crop_bottom;
                    } else {
                        dstLeft = tilesDecoded % gridCols * width;
                        dstTop = tilesDecoded / gridCols * height;
                        dstRight = dstLeft + width - 1;
                        dstBottom = dstTop + height - 1;
                    }

                    if (converter.isValid()) {
                        err = converter.convert(
                                (const uint8_t *)videoFrameBuffer->data(),
                                width, height,
                                crop_left, crop_top, crop_right, crop_bottom,
                                frame->mData,
                                frame->mWidth,
                                frame->mHeight,
                                dstLeft, dstTop, dstRight, dstBottom);
                    } else {
                        ALOGE("Unable to convert from format 0x%08x to 0x%08x",
                                srcFormat, dstFormat);

                        err = ERROR_UNSUPPORTED;
                    }

                    done = (targetTimeUs < 0ll) || (timeUs >= targetTimeUs);
                    if (numTiles > 1) {
                        tilesDecoded++;
                        done &= (tilesDecoded >= numTiles);
                    }
                    err = decoder->releaseOutputBuffer(index);
                } else {
                    ALOGW("Received error %d (%s) instead of output", err, asString(err));
                    done = true;
                }
                break;
            }
        }
    } while (err == OK && !done);
    source->stop();
    decoder->release();

    if (err != OK) {
        ALOGE("failed to get video frame (err %d)", err);
        delete frame;
        frame = NULL;
    }

    return frame;
}

VideoFrame *StagefrightMetadataRetriever::getFrameAtTime(
        int64_t timeUs, int option, int colorFormat, bool metaOnly) {

    ALOGV("getFrameAtTime: %" PRId64 " us option: %d colorFormat: %d, metaOnly: %d",
            timeUs, option, colorFormat, metaOnly);

    if (mExtractor.get() == NULL) {
        ALOGV("no extractor.");
        return NULL;
    }

    sp<MetaData> fileMeta = mExtractor->getMetaData();

    if (fileMeta == NULL) {
        ALOGV("extractor doesn't publish metadata, failed to initialize?");
        return NULL;
    }

    int32_t drm = 0;
    if (fileMeta->findInt32(kKeyIsDRM, &drm) && drm != 0) {
        ALOGE("frame grab not allowed.");
        return NULL;
    }

    size_t n = mExtractor->countTracks();
    size_t i;
    for (i = 0; i < n; ++i) {
        sp<MetaData> meta = mExtractor->getTrackMetaData(i);

        const char *mime;
        CHECK(meta->findCString(kKeyMIMEType, &mime));

        if (!strncasecmp(mime, "video/", 6)) {
            break;
        }
    }

    if (i == n) {
        ALOGV("no video track found.");
        return NULL;
    }

    sp<MetaData> trackMeta = mExtractor->getTrackMetaData(
            i, MediaExtractor::kIncludeExtensiveMetaData);

    sp<IMediaSource> source = mExtractor->getTrack(i);

    if (source.get() == NULL) {
        ALOGV("unable to instantiate video track.");
        return NULL;
    }

    const void *data;
    uint32_t type;
    size_t dataSize;
    if (fileMeta->findData(kKeyAlbumArt, &type, &data, &dataSize)
            && mAlbumArt == NULL) {
        mAlbumArt = MediaAlbumArt::fromData(dataSize, data);
    }

    const char *mime;
    CHECK(trackMeta->findCString(kKeyMIMEType, &mime));

    Vector<AString> matchingCodecs;
#ifdef MTK_AOSP_ENHANCEMENT
    MediaCodecList::findMatchingCodecs(
            mime,
            false, /* encoder */
            0,
            &matchingCodecs);
    ALOGD("matchingCodecs size is %zu", matchingCodecs.size());
#else
    MediaCodecList::findMatchingCodecs(
            mime,
            false, /* encoder */
            MediaCodecList::kPreferSoftwareCodecs,
            &matchingCodecs);
#endif

    for (size_t i = 0; i < matchingCodecs.size(); ++i) {
        const AString &componentName = matchingCodecs[i];
        VideoFrame *frame = extractVideoFrame(
                componentName, trackMeta, source, timeUs, option, colorFormat, metaOnly);

        if (frame != NULL) {
            return frame;
        }
        ALOGV("%s failed to extract thumbnail, trying next decoder.", componentName.c_str());
    }

    return NULL;
}

MediaAlbumArt *StagefrightMetadataRetriever::extractAlbumArt() {
    ALOGV("extractAlbumArt (extractor: %s)", mExtractor.get() != NULL ? "YES" : "NO");

    if (mExtractor == NULL) {
        return NULL;
    }

    if (!mParsedMetaData) {
        parseMetaData();

        mParsedMetaData = true;
    }

    if (mAlbumArt) {
        return mAlbumArt->clone();
    }

    return NULL;
}

const char *StagefrightMetadataRetriever::extractMetadata(int keyCode) {
    if (mExtractor == NULL) {
        return NULL;
    }

    if (!mParsedMetaData) {
        parseMetaData();

        mParsedMetaData = true;
    }

    ssize_t index = mMetaData.indexOfKey(keyCode);

    if (index < 0) {
        return NULL;
    }

    return mMetaData.valueAt(index).string();
}

void StagefrightMetadataRetriever::parseMetaData() {
#ifdef MTK_DRM_APP
    // OMA DRM v1 implementation: NULL extractor means .dcf without valid rights
    if (mExtractor.get() == NULL) {
        ALOGD("Invalid rights for OMA DRM v1 file. NULL extractor and cannot parse meta data.");
        return;
    }
#endif
    sp<MetaData> meta = mExtractor->getMetaData();

    if (meta == NULL) {
        ALOGV("extractor doesn't publish metadata, failed to initialize?");
        return;
    }

    struct Map {
        int from;
        int to;
        const char *name;
    };
    static const Map kMap[] = {
        { kKeyMIMEType, METADATA_KEY_MIMETYPE, NULL },
        { kKeyCDTrackNumber, METADATA_KEY_CD_TRACK_NUMBER, "tracknumber" },
        { kKeyDiscNumber, METADATA_KEY_DISC_NUMBER, "discnumber" },
        { kKeyAlbum, METADATA_KEY_ALBUM, "album" },
        { kKeyArtist, METADATA_KEY_ARTIST, "artist" },
        { kKeyAlbumArtist, METADATA_KEY_ALBUMARTIST, "albumartist" },
        { kKeyAuthor, METADATA_KEY_AUTHOR, NULL },
        { kKeyComposer, METADATA_KEY_COMPOSER, "composer" },
        { kKeyDate, METADATA_KEY_DATE, NULL },
        { kKeyGenre, METADATA_KEY_GENRE, "genre" },
        { kKeyTitle, METADATA_KEY_TITLE, "title" },
        { kKeyYear, METADATA_KEY_YEAR, "year" },
        { kKeyWriter, METADATA_KEY_WRITER, "writer" },
        { kKeyCompilation, METADATA_KEY_COMPILATION, "compilation" },
        { kKeyLocation, METADATA_KEY_LOCATION, NULL },
    };

    static const size_t kNumMapEntries = sizeof(kMap) / sizeof(kMap[0]);

    CharacterEncodingDetector *detector = new CharacterEncodingDetector();

    for (size_t i = 0; i < kNumMapEntries; ++i) {
        const char *value;
        if (meta->findCString(kMap[i].from, &value)) {
            if (kMap[i].name) {
                // add to charset detector
                detector->addTag(kMap[i].name, value);
            } else {
                // directly add to output list
                mMetaData.add(kMap[i].to, String8(value));
            }
        }
    }

    detector->detectAndConvert();
    int size = detector->size();
    if (size) {
        for (int i = 0; i < size; i++) {
            const char *name;
            const char *value;
            detector->getTag(i, &name, &value);
            for (size_t j = 0; j < kNumMapEntries; ++j) {
                if (kMap[j].name && !strcmp(kMap[j].name, name)) {
                    mMetaData.add(kMap[j].to, String8(value));
                }
            }
        }
    }
    delete detector;

    const void *data;
    uint32_t type;
    size_t dataSize;
    if (meta->findData(kKeyAlbumArt, &type, &data, &dataSize)
            && mAlbumArt == NULL) {
        mAlbumArt = MediaAlbumArt::fromData(dataSize, data);
    }

    size_t numTracks = mExtractor->countTracks();

    char tmp[32];
    sprintf(tmp, "%zu", numTracks);

    mMetaData.add(METADATA_KEY_NUM_TRACKS, String8(tmp));

    float captureFps;
    if (meta->findFloat(kKeyCaptureFramerate, &captureFps)) {
        sprintf(tmp, "%f", captureFps);
        mMetaData.add(METADATA_KEY_CAPTURE_FRAMERATE, String8(tmp));
    }

    bool hasAudio = false;
    bool hasVideo = false;
    int32_t videoWidth = -1;
    int32_t videoHeight = -1;
    int32_t audioBitrate = -1;
    int32_t rotationAngle = -1;

    // The overall duration is the duration of the longest track.
    int64_t maxDurationUs = 0;
    String8 timedTextLang;
    for (size_t i = 0; i < numTracks; ++i) {
        sp<MetaData> trackMeta = mExtractor->getTrackMetaData(i);

        int64_t durationUs;
        if (trackMeta->findInt64(kKeyDuration, &durationUs)) {
            if (durationUs > maxDurationUs) {
                maxDurationUs = durationUs;
            }
        }

        const char *mime;
        if (trackMeta->findCString(kKeyMIMEType, &mime)) {
            if (!hasAudio && !strncasecmp("audio/", mime, 6)) {
                hasAudio = true;

                if (!trackMeta->findInt32(kKeyBitRate, &audioBitrate)) {
                    audioBitrate = -1;
                }
            } else if (!hasVideo && !strncasecmp("video/", mime, 6)) {
                hasVideo = true;

                CHECK(trackMeta->findInt32(kKeyWidth, &videoWidth));
                CHECK(trackMeta->findInt32(kKeyHeight, &videoHeight));
                if (!trackMeta->findInt32(kKeyRotation, &rotationAngle)) {
                    rotationAngle = 0;
                }
            } else if (!strcasecmp(mime, MEDIA_MIMETYPE_TEXT_3GPP)) {
                const char *lang;
                if (trackMeta->findCString(kKeyMediaLanguage, &lang)) {
                    timedTextLang.append(String8(lang));
                    timedTextLang.append(String8(":"));
                } else {
                    ALOGE("No language found for timed text");
                }
            }
        }
    }

    // To save the language codes for all timed text tracks
    // If multiple text tracks present, the format will look
    // like "eng:chi"
    if (!timedTextLang.isEmpty()) {
        mMetaData.add(METADATA_KEY_TIMED_TEXT_LANGUAGES, timedTextLang);
    }

    // The duration value is a string representing the duration in ms.
    sprintf(tmp, "%" PRId64, (maxDurationUs + 500) / 1000);
    mMetaData.add(METADATA_KEY_DURATION, String8(tmp));

    if (hasAudio) {
        mMetaData.add(METADATA_KEY_HAS_AUDIO, String8("yes"));
    }

    if (hasVideo) {
        mMetaData.add(METADATA_KEY_HAS_VIDEO, String8("yes"));

        sprintf(tmp, "%d", videoWidth);
        mMetaData.add(METADATA_KEY_VIDEO_WIDTH, String8(tmp));

        sprintf(tmp, "%d", videoHeight);
        mMetaData.add(METADATA_KEY_VIDEO_HEIGHT, String8(tmp));

        sprintf(tmp, "%d", rotationAngle);
        mMetaData.add(METADATA_KEY_VIDEO_ROTATION, String8(tmp));
    }

    if (numTracks == 1 && hasAudio && audioBitrate >= 0) {
        sprintf(tmp, "%d", audioBitrate);
        mMetaData.add(METADATA_KEY_BITRATE, String8(tmp));
    } else {
        off64_t sourceSize;
        if (mSource != NULL && mSource->getSize(&sourceSize) == OK) {
            int64_t avgBitRate = (int64_t)(sourceSize * 8E6 / maxDurationUs);

            sprintf(tmp, "%" PRId64, avgBitRate);
            mMetaData.add(METADATA_KEY_BITRATE, String8(tmp));
        }
    }

    if (numTracks == 1) {
        const char *fileMIME;

        if (meta->findCString(kKeyMIMEType, &fileMIME) &&
                !strcasecmp(fileMIME, "video/x-matroska")) {
            sp<MetaData> trackMeta = mExtractor->getTrackMetaData(0);
            const char *trackMIME;
            CHECK(trackMeta->findCString(kKeyMIMEType, &trackMIME));

            if (!strncasecmp("audio/", trackMIME, 6)) {
                // The matroska file only contains a single audio track,
                // rewrite its mime type.
                mMetaData.add(
                        METADATA_KEY_MIMETYPE, String8("audio/x-matroska"));
            }
        }
    }
}

void StagefrightMetadataRetriever::clearMetadata() {
    mParsedMetaData = false;
    mMetaData.clear();
    delete mAlbumArt;
    mAlbumArt = NULL;
}

}  // namespace android
