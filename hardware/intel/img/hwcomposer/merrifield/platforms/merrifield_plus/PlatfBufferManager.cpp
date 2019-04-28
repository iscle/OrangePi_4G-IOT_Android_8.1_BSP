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
#include <PlatfBufferManager.h>
#include <tangier/TngGrallocBuffer.h>
#include <tangier/TngGrallocBufferMapper.h>
#include <sync/sync.h>

namespace android {
namespace intel {

PlatfBufferManager::PlatfBufferManager()
    : BufferManager()
{

}

PlatfBufferManager::~PlatfBufferManager()
{

}

bool PlatfBufferManager::initialize()
{
    return BufferManager::initialize();
}

void PlatfBufferManager::deinitialize()
{
    BufferManager::deinitialize();
}

DataBuffer* PlatfBufferManager::createDataBuffer(buffer_handle_t handle)
{
    return new TngGrallocBuffer(handle);
}

BufferMapper* PlatfBufferManager::createBufferMapper(DataBuffer& buffer)
{
    return new TngGrallocBufferMapper(*mGralloc, buffer);
}

bool PlatfBufferManager::blit(buffer_handle_t srcHandle, buffer_handle_t destHandle,
                              const crop_t& destRect, bool filter, bool async)

{
    int fenceFd;

    if (gralloc_blit_handle_to_handle_img(mGralloc,
                                srcHandle,
                                destHandle,
                                destRect.w, destRect.h, destRect.x,
                                destRect.y, 0, -1, &fenceFd)) {
        ETRACE("Blit failed");
        return false;
    }

    if (!async) {
        sync_wait(fenceFd, -1);
    }
    close(fenceFd);

    return true;
}

} // namespace intel
} // namespace android
