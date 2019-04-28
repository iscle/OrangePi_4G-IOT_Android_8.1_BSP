/*
// Copyright (c) 2014 Intel Corporation 
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
*/

#include <HwcTrace.h>
#include <BufferMapper.h>
#include <common/GrallocSubBuffer.h>
#include <common/VideoPayloadManager.h>
#include <common/VideoPayloadBuffer.h>

namespace android {
namespace intel {

VideoPayloadManager::VideoPayloadManager()
    : IVideoPayloadManager()
{
}

VideoPayloadManager::~VideoPayloadManager()
{
}

bool VideoPayloadManager::getMetaData(BufferMapper *mapper, MetaData *metadata)
{
    if (!mapper || !metadata) {
        ETRACE("Null input params");
        return false;
    }

    VideoPayloadBuffer *p = (VideoPayloadBuffer*) mapper->getCpuAddress(SUB_BUFFER1);
    if (!p) {
        ETRACE("Got null payload from display buffer");
        return false;
    }

    metadata->format = p->format;
    metadata->transform = p->metadata_transform;
    metadata->timestamp = p->timestamp;

    metadata->normalBuffer.khandle = p->khandle;
    metadata->normalBuffer.width = p->crop_width;
    metadata->normalBuffer.height = p->crop_height;
    metadata->normalBuffer.bufWidth = p->width;
    metadata->normalBuffer.bufHeight = p->height;
    metadata->normalBuffer.lumaStride = p->luma_stride;
    metadata->normalBuffer.chromaUStride = p->chroma_u_stride;
    metadata->normalBuffer.chromaVStride = p->chroma_v_stride;
    metadata->normalBuffer.offsetX = 0;
    metadata->normalBuffer.offsetY = 0;
    metadata->normalBuffer.tiled = (p->width > 1280);

    metadata->scalingBuffer.khandle = p->scaling_khandle;
    metadata->scalingBuffer.width = p->scaling_width;
    metadata->scalingBuffer.height = p->scaling_height;
    metadata->scalingBuffer.bufWidth = align_to(p->scaling_width, 32);
    metadata->scalingBuffer.bufHeight = align_to(p->scaling_height, 32);
    metadata->scalingBuffer.lumaStride = p->scaling_luma_stride;
    metadata->scalingBuffer.chromaUStride = p->scaling_chroma_u_stride;
    metadata->scalingBuffer.chromaVStride = p->scaling_chroma_v_stride;
    metadata->scalingBuffer.offsetX = 0;
    metadata->scalingBuffer.offsetY = 0;
    metadata->scalingBuffer.tiled = false;

    metadata->rotationBuffer.khandle = p->rotated_buffer_handle;
    uint16_t rotSrcWidth;
    uint16_t rotSrcHeight;
    if (metadata->scalingBuffer.khandle) {
        rotSrcWidth = metadata->scalingBuffer.width;
        rotSrcHeight = metadata->scalingBuffer.height;
    } else {
        rotSrcWidth = metadata->normalBuffer.width;
        rotSrcHeight = metadata->normalBuffer.height;
    }
    if (metadata->transform == 0 || metadata->transform == HAL_TRANSFORM_ROT_180) {
        metadata->rotationBuffer.width = rotSrcWidth;
        metadata->rotationBuffer.height = rotSrcHeight;
    } else {
        metadata->rotationBuffer.width = rotSrcHeight;
        metadata->rotationBuffer.height = rotSrcWidth;
    }
    metadata->rotationBuffer.bufWidth = p->rotated_width;
    metadata->rotationBuffer.bufHeight = p->rotated_height;
    metadata->rotationBuffer.lumaStride = p->rotate_luma_stride;
    metadata->rotationBuffer.chromaUStride = p->rotate_chroma_u_stride;
    metadata->rotationBuffer.chromaVStride = p->rotate_chroma_v_stride;
    metadata->rotationBuffer.offsetX = (-metadata->rotationBuffer.width) & 0xf;
    metadata->rotationBuffer.offsetY = (-metadata->rotationBuffer.height) & 0xf;
    metadata->rotationBuffer.tiled = metadata->normalBuffer.tiled;

    return true;
}

bool VideoPayloadManager::setRenderStatus(BufferMapper *mapper, bool renderStatus)
{
    if (!mapper) {
        ETRACE("Null mapper param");
        return false;
    }

    VideoPayloadBuffer* p = (VideoPayloadBuffer*) mapper->getCpuAddress(SUB_BUFFER1);
    if (!p) {
        ETRACE("Got null payload from display buffer");
        return false;
    }

    p->renderStatus = renderStatus ? 1 : 0;
    return true;
}

} // namespace intel
} // namespace android
