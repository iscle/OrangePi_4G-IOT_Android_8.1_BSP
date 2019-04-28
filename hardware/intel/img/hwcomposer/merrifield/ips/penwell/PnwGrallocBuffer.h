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
#ifndef PNW_GRALLOC_BUFFER_H
#define PNW_GRALLOC_BUFFER_H

#include <common/GrallocSubBuffer.h>
#include <common/GrallocBufferBase.h>

namespace android {
namespace intel {

struct PnwIMGGrallocBuffer{
    native_handle_t base;
    int fd[SUB_BUFFER_MAX];
    unsigned long long stamp;
    int usage;
    int width;
    int height;
    int format;
    int bpp;
}__attribute__((aligned(sizeof(int)),packed));


class PnwGrallocBuffer : public GrallocBufferBase {
public:
    PnwGrallocBuffer(uint32_t handle);
};

} // namespace intel
} // namespace android


#endif /* PNW_GRALLOC_BUFFER_H */
