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
#ifndef DRM_CONFIG_H
#define DRM_CONFIG_H

namespace android {
namespace intel {

#define fourcc_code(a, b, c, d) ((__u32)(a) | ((__u32)(b) << 8) | \
                 ((__u32)(c) << 16) | ((__u32)(d) << 24))
#define DRM_FORMAT_XRGB8888    fourcc_code('X', 'R', '2', '4') /* [31:0] x:R:G:B 8:8:8:8 little endian */

class DrmConfig
{
public:
    static const char* getDrmPath();
    static uint32_t getDrmConnector(int device);
    static uint32_t getDrmEncoder(int device);
    static uint32_t getFrameBufferFormat();
    static uint32_t getFrameBufferDepth();
    static uint32_t getFrameBufferBpp();
    static const char* getUeventEnvelope();
    static const char* getHotplugString();
    static const char* getRepeatedFrameString();
    static uint32_t convertHalFormatToDrmFormat(uint32_t halFormat);
};

} // namespace intel
} // namespace android

#endif /*DRM_CONFIG_H*/
