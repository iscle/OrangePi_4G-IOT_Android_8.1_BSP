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
#ifndef IVIDEO_PAYLOAD_MANAGER_H
#define IVIDEO_PAYLOAD_MANAGER_H

#include <hardware/hwcomposer.h>

namespace android {
namespace intel {

class BufferMapper;

class IVideoPayloadManager {
public:
    IVideoPayloadManager() {}
    virtual ~IVideoPayloadManager() {}

public:
    struct Buffer {
        buffer_handle_t khandle;
        uint16_t width;
        uint16_t height;
        uint16_t bufWidth;
        uint16_t bufHeight;
        uint16_t lumaStride;
        uint16_t chromaUStride;
        uint16_t chromaVStride;
        uint16_t offsetX;
        uint16_t offsetY;
        bool     tiled;
    };
    struct MetaData {
        uint32_t format;
        uint32_t transform;
        int64_t  timestamp;
        Buffer normalBuffer;
        Buffer scalingBuffer;
        Buffer rotationBuffer;
    };

public:
    virtual bool getMetaData(BufferMapper *mapper, MetaData *metadata) = 0;
    virtual bool setRenderStatus(BufferMapper *mapper, bool renderStatus) = 0;
};

} // namespace intel
} // namespace android

#endif /* IVIDEO_PAYLOAD_MANAGER_H */
