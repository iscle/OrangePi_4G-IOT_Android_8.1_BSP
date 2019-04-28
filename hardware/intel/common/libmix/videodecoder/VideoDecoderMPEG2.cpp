/*
* Copyright (c) 2009-2011 Intel Corporation.  All rights reserved.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

#include "VideoDecoderMPEG2.h"
#include "VideoDecoderTrace.h"
#include <string.h>

#define MAX_PICTURE_WIDTH_MPEG2 1920
#define MAX_PICTURE_HEIGHT_MPEG2 1088

VideoDecoderMPEG2::VideoDecoderMPEG2(const char *mimeType)
    : VideoDecoderBase(mimeType, VBP_MPEG2),
    mBufferIDs(NULL),
    mNumBufferIDs(0) {
    //do nothing
}

VideoDecoderMPEG2::~VideoDecoderMPEG2() {
    stop();
}

Decode_Status VideoDecoderMPEG2::start(VideoConfigBuffer *buffer) {
    Decode_Status status;

    status = VideoDecoderBase::start(buffer);
    CHECK_STATUS("VideoDecoderBase::start");

    if (buffer->data == NULL || buffer->size == 0) {
        WTRACE("No config data to start VA.");
        return DECODE_SUCCESS;
    }

    vbp_data_mpeg2 *data = NULL;
    status = VideoDecoderBase::parseBuffer(
            buffer->data,
            buffer->size,
            true, // config flag
            (void**)&data);
    CHECK_STATUS("VideoDecoderBase::parseBuffer");

    if (data->codec_data->frame_width > MAX_PICTURE_WIDTH_MPEG2 ||
            data->codec_data->frame_height > MAX_PICTURE_HEIGHT_MPEG2) {
        return DECODE_INVALID_DATA;
    }

    status = startVA(data);
    return status;
}

void VideoDecoderMPEG2::stop(void) {
    if (mBufferIDs) {
        delete [] mBufferIDs;
        mBufferIDs = NULL;
    }
    mNumBufferIDs = 0;

    VideoDecoderBase::stop();
}

Decode_Status VideoDecoderMPEG2::decode(VideoDecodeBuffer *buffer) {
    Decode_Status status;
    vbp_data_mpeg2 *data = NULL;
    bool useGraphicbuffer = mConfigBuffer.flag & USE_NATIVE_GRAPHIC_BUFFER;

    if (buffer == NULL) {
        return DECODE_INVALID_DATA;
    }

#ifdef DUMP_INPUT_BUFFER
    if (mConfigBuffer.flag & USE_NATIVE_GRAPHIC_BUFFER) {
        DumpInputBuffer(buffer, "mpeg2");
    }
#endif

    buffer->ext = NULL;
    status =  VideoDecoderBase::parseBuffer(
            buffer->data,
            buffer->size,
            false,        // config flag
            (void**)&data);
    CHECK_STATUS("VideoDecoderBase::parseBuffer");

    if (data->codec_data->frame_width > MAX_PICTURE_WIDTH_MPEG2 ||
            data->codec_data->frame_height > MAX_PICTURE_HEIGHT_MPEG2) {
        return DECODE_INVALID_DATA;
    }

    if (!mVAStarted) {
        status = startVA(data);
        CHECK_STATUS("startVA");
    }

    if (mSizeChanged && !useGraphicbuffer) {
        // some container has the incorrect width/height.
        // send the format change to OMX to update the crop info.
        mSizeChanged = false;
        ITRACE("Video size is changed during startVA");
        return DECODE_FORMAT_CHANGE;
    }

    if ((mVideoFormatInfo.width != (uint32_t)data->codec_data->frame_width ||
        mVideoFormatInfo.height != (uint32_t)data->codec_data->frame_height) &&
        (data->codec_data->frame_width > 0) && (data->codec_data->frame_height)) {
        // update  encoded image size
        ITRACE("Video size is changed. from %dx%d to %dx%d\n",mVideoFormatInfo.width,mVideoFormatInfo.height, data->codec_data->frame_width,data->codec_data->frame_height);
        if (useGraphicbuffer && mStoreMetaData) {
            pthread_mutex_lock(&mFormatLock);
        }
        mVideoFormatInfo.width = data->codec_data->frame_width;
        mVideoFormatInfo.height = data->codec_data->frame_height;
        bool needFlush = false;
        if (useGraphicbuffer) {
            if (mStoreMetaData) {
                needFlush = true;

                mVideoFormatInfo.valid = false;
                pthread_mutex_unlock(&mFormatLock);
            } else {
                needFlush = (mVideoFormatInfo.width > mVideoFormatInfo.surfaceWidth)
                         || (mVideoFormatInfo.height > mVideoFormatInfo.surfaceHeight);
            }
        }

        if (needFlush) {
            if (mStoreMetaData) {
                status = endDecodingFrame(false);
                CHECK_STATUS("endDecodingFrame");
            } else {
                flushSurfaceBuffers();
            }
            mSizeChanged = false;
            return DECODE_FORMAT_CHANGE;
        } else {
            mSizeChanged = true;
        }

        setRenderRect();
    } else {
        if (useGraphicbuffer && mStoreMetaData) {
            mVideoFormatInfo.valid = true;
        }
    }

    VideoDecoderBase::setRotationDegrees(buffer->rotationDegrees);

    status = decodeFrame(buffer, data);
    CHECK_STATUS("decodeFrame");

    return status;
}

void VideoDecoderMPEG2::flush(void) {
    VideoDecoderBase::flush();
}

Decode_Status VideoDecoderMPEG2::decodeFrame(VideoDecodeBuffer *buffer, vbp_data_mpeg2 *data) {
    Decode_Status status;
    // check if any slice is parsed, we may just receive configuration data
    if (data->num_pictures == 0 || data->pic_data == NULL) {
        WTRACE("Number of pictures is 0, buffer contains configuration data only?");
        return DECODE_SUCCESS;
    }

    status = acquireSurfaceBuffer();
    CHECK_STATUS("acquireSurfaceBuffer");

    // set referenceFrame to true if frame decoded is I/P frame, false otherwise.
    int frameType = data->codec_data->frame_type;
    mAcquiredBuffer->referenceFrame = (frameType == MPEG2_PICTURE_TYPE_I || frameType == MPEG2_PICTURE_TYPE_P);

    if (data->num_pictures > 1) {
        if (data->pic_data[0].pic_parms->picture_coding_extension.bits.picture_structure == MPEG2_PIC_STRUCT_TOP)
        {
            mAcquiredBuffer->renderBuffer.scanFormat = VA_TOP_FIELD;
        } else {
            mAcquiredBuffer->renderBuffer.scanFormat = VA_BOTTOM_FIELD;
        }
    } else {
        mAcquiredBuffer->renderBuffer.scanFormat = VA_FRAME_PICTURE;
    }

    mAcquiredBuffer->renderBuffer.timeStamp = buffer->timeStamp;
    mAcquiredBuffer->renderBuffer.flag = 0;
    if (buffer->flag & WANT_DECODE_ONLY) {
        mAcquiredBuffer->renderBuffer.flag |= WANT_DECODE_ONLY;
    }
    if (mSizeChanged) {
        mSizeChanged = false;
        mAcquiredBuffer->renderBuffer.flag |= IS_RESOLUTION_CHANGE;
    }

    for (uint32_t index = 0; index < data->num_pictures; index++) {
        status = decodePicture(data, index);
        if (status != DECODE_SUCCESS) {
            endDecodingFrame(true);
            return status;
        }
    }

    // if sample is successfully decoded, call outputSurfaceBuffer(); otherwise
    // call releaseSurfacebuffer();
    status = outputSurfaceBuffer();
    return status;
}

Decode_Status VideoDecoderMPEG2::decodePicture(vbp_data_mpeg2 *data, int picIndex) {
    Decode_Status status;
    VAStatus vaStatus;
    uint32_t bufferIDCount = 0;

    vbp_picture_data_mpeg2 *picData = &(data->pic_data[picIndex]);
    VAPictureParameterBufferMPEG2 *picParam = picData->pic_parms;

    status = allocateVABufferIDs(picData->num_slices * 2 + 2);
    CHECK_STATUS("allocateVABufferIDs")

    // send picture parametre for each slice
    status = setReference(picParam);
    CHECK_STATUS("setReference");

    vaStatus = vaBeginPicture(mVADisplay, mVAContext, mAcquiredBuffer->renderBuffer.surface);
    CHECK_VA_STATUS("vaBeginPicture");
    // setting mDecodingFrame to true so vaEndPicture will be invoked to end the picture decoding.
    mDecodingFrame = true;

    vaStatus = vaCreateBuffer(
            mVADisplay,
            mVAContext,
            VAPictureParameterBufferType,
            sizeof(VAPictureParameterBufferMPEG2),
            1,
            picParam,
            &mBufferIDs[bufferIDCount]);
    CHECK_VA_STATUS("vaCreatePictureParameterBuffer");
    bufferIDCount++;

    vaStatus = vaCreateBuffer(
                mVADisplay,
                mVAContext,
                VAIQMatrixBufferType,
                sizeof(VAIQMatrixBufferMPEG2),
                1,
                data->iq_matrix_buffer,
                &mBufferIDs[bufferIDCount]);
    CHECK_VA_STATUS("vaCreateIQMatrixBuffer");
    bufferIDCount++;

    for (uint32_t i = 0; i < picData->num_slices; i++) {
        vaStatus = vaCreateBuffer(
                mVADisplay,
                mVAContext,
                VASliceParameterBufferType,
                sizeof(VASliceParameterBufferMPEG2),
                1,
                &(picData->slice_data[i].slice_param),
                &mBufferIDs[bufferIDCount]);
        CHECK_VA_STATUS("vaCreateSliceParameterBuffer");
        bufferIDCount++;

        // slice data buffer pointer
        // Note that this is the original data buffer ptr;
        // offset to the actual slice data is provided in
        // slice_data_offset in VASliceParameterBufferMPEG2
        vaStatus = vaCreateBuffer(
                mVADisplay,
                mVAContext,
                VASliceDataBufferType,
                picData->slice_data[i].slice_size, //size
                1,        //num_elements
                picData->slice_data[i].buffer_addr + picData->slice_data[i].slice_offset,
                &mBufferIDs[bufferIDCount]);
        CHECK_VA_STATUS("vaCreateSliceDataBuffer");
        bufferIDCount++;
    }

    vaStatus = vaRenderPicture(
            mVADisplay,
            mVAContext,
            mBufferIDs,
            bufferIDCount);
    CHECK_VA_STATUS("vaRenderPicture");

    vaStatus = vaEndPicture(mVADisplay, mVAContext);
    mDecodingFrame = false;
    CHECK_VA_STATUS("vaRenderPicture");

    return DECODE_SUCCESS;
}

Decode_Status VideoDecoderMPEG2::setReference(VAPictureParameterBufferMPEG2 *picParam) {
    switch (picParam->picture_coding_type) {
        case MPEG2_PICTURE_TYPE_I:
            picParam->forward_reference_picture = VA_INVALID_SURFACE;
            picParam->backward_reference_picture = VA_INVALID_SURFACE;
            break;
        case MPEG2_PICTURE_TYPE_P:
            if (mLastReference != NULL) {
                picParam->forward_reference_picture = mLastReference->renderBuffer.surface;
            } else {
                VTRACE("%s: no reference frame, but keep decoding", __FUNCTION__);
                picParam->forward_reference_picture = VA_INVALID_SURFACE;
            }
            picParam->backward_reference_picture = VA_INVALID_SURFACE;
            break;
        case MPEG2_PICTURE_TYPE_B:
            if (mLastReference == NULL || mForwardReference == NULL) {
                return DECODE_NO_REFERENCE;
            } else {
                picParam->forward_reference_picture = mForwardReference->renderBuffer.surface;
                picParam->backward_reference_picture = mLastReference->renderBuffer.surface;
            }
            break;
        default:
            // Will never reach here;
            return DECODE_PARSER_FAIL;
    }
    return DECODE_SUCCESS;
}

Decode_Status VideoDecoderMPEG2::startVA(vbp_data_mpeg2 *data) {
    updateFormatInfo(data);

    VAProfile vaProfile;

    // profile_and_level_indication is 8-bit field
    // | x | x x x | x x x x|
    //      profile  level
    // profile: 101 - simple
    //          100 - main
    // level:   1010 - low
    //          1000 - main
    //          0100 - high
    //          0110 - high 1440
    if ((data->codec_data->profile_and_level_indication & 0x70) == 0x50) {
        vaProfile = VAProfileMPEG2Simple;
    } else {
        vaProfile = VAProfileMPEG2Main;
    }

    return VideoDecoderBase::setupVA(MPEG2_SURFACE_NUMBER, vaProfile);
}

Decode_Status VideoDecoderMPEG2::allocateVABufferIDs(int32_t number) {
    if (mNumBufferIDs > number) {
        return DECODE_SUCCESS;
    }
    if (mBufferIDs) {
        delete [] mBufferIDs;
    }
    mBufferIDs = NULL;
    mNumBufferIDs = 0;
    mBufferIDs = new VABufferID [number];
    if (mBufferIDs == NULL) {
        return DECODE_MEMORY_FAIL;
    }
    mNumBufferIDs = number;
    return DECODE_SUCCESS;
}

void VideoDecoderMPEG2::updateFormatInfo(vbp_data_mpeg2 *data) {
    ITRACE("updateFormatInfo: current size: %d x %d, new size: %d x %d",
        mVideoFormatInfo.width, mVideoFormatInfo.height,
        data->codec_data->frame_width,
        data->codec_data->frame_height);

    mVideoFormatInfo.cropBottom = (data->codec_data->frame_height > mVideoFormatInfo.height) ?
                                                       (data->codec_data->frame_height - mVideoFormatInfo.height) : 0;
    mVideoFormatInfo.cropRight = (data->codec_data->frame_width > mVideoFormatInfo.width) ?
                                                       (data->codec_data->frame_width - mVideoFormatInfo.width) : 0;

    if ((mVideoFormatInfo.width != (uint32_t)data->codec_data->frame_width ||
         mVideoFormatInfo.height != (uint32_t)data->codec_data->frame_height) &&
        (data->codec_data->frame_width > 0) && (data->codec_data->frame_height)) {
        // update  encoded image size
        mVideoFormatInfo.width = data->codec_data->frame_width;
        mVideoFormatInfo.height = data->codec_data->frame_height;
        mSizeChanged = true;
        ITRACE("Video size is changed.");
    }

    // video_range has default value of 0. Y ranges from 16 to 235.
    mVideoFormatInfo.videoRange = data->codec_data->video_range;

    switch (data->codec_data->matrix_coefficients) {
        case 1:
            mVideoFormatInfo.colorMatrix = VA_SRC_BT709;
            break;

        // ITU-R Recommendation BT.470-6 System B, G (MP4), same as
        // SMPTE 170M/BT601
        case 5:
        case 6:
            mVideoFormatInfo.colorMatrix = VA_SRC_BT601;
            break;

        default:
            // unknown color matrix, set to 0 so color space flag will not be set.
            mVideoFormatInfo.colorMatrix = 0;
            break;
    }

    mVideoFormatInfo.aspectX = data->codec_data->par_width;
    mVideoFormatInfo.aspectY = data->codec_data->par_height;
    mVideoFormatInfo.bitrate = data->codec_data->bit_rate;
    mVideoFormatInfo.valid = true;

    setRenderRect();
}

Decode_Status VideoDecoderMPEG2::checkHardwareCapability() {
    VAStatus vaStatus;
    VAConfigAttrib cfgAttribs[2];
    cfgAttribs[0].type = VAConfigAttribMaxPictureWidth;
    cfgAttribs[1].type = VAConfigAttribMaxPictureHeight;
    vaStatus = vaGetConfigAttributes(mVADisplay,
            VAProfileMPEG2Main,
            VAEntrypointVLD, cfgAttribs, 2);
    CHECK_VA_STATUS("vaGetConfigAttributes");
    if (cfgAttribs[0].value * cfgAttribs[1].value < (uint32_t)mVideoFormatInfo.width * (uint32_t)mVideoFormatInfo.height) {
        ETRACE("hardware supports resolution %d * %d smaller than the clip resolution %d * %d",
                cfgAttribs[0].value, cfgAttribs[1].value, mVideoFormatInfo.width, mVideoFormatInfo.height);
        return DECODE_DRIVER_FAIL;
    }
    return DECODE_SUCCESS;
}
