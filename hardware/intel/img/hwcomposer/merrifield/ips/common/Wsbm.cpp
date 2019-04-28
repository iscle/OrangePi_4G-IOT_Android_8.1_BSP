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
#include <common/Wsbm.h>

Wsbm::Wsbm(int drmFD)
    : mInitialized(false)
{
    CTRACE();
    mDrmFD = drmFD;
}

Wsbm::~Wsbm()
{
    WARN_IF_NOT_DEINIT();
}

bool Wsbm::initialize()
{
    if (mInitialized) {
        WTRACE("object is initialized");
        return true;
    }

    int ret = psbWsbmInitialize(mDrmFD);
    if (ret) {
        ETRACE("failed to initialize Wsbm");
        return false;
    }

    mInitialized = true;
    return true;
}

void Wsbm::deinitialize()
{
    if (!mInitialized) {
        return;
    }
    psbWsbmTakedown();
    mInitialized = false;
}

bool Wsbm::allocateTTMBuffer(uint32_t size, uint32_t align, void ** buf)
{
    int ret = psbWsbmAllocateTTMBuffer(size, align, buf);
    if (ret) {
        ETRACE("failed to allocate buffer");
        return false;
    }

    return true;
}

bool Wsbm::allocateTTMBufferUB(uint32_t size, uint32_t align, void ** buf, void *user_pt)
{
    int ret = psbWsbmAllocateFromUB(size, align, buf, user_pt);
    if (ret) {
        ETRACE("failed to allocate UB buffer");
        return false;
    }

    return true;
}

bool Wsbm::destroyTTMBuffer(void * buf)
{
    int ret = psbWsbmDestroyTTMBuffer(buf);
    if (ret) {
        ETRACE("failed to destroy buffer");
        return false;
    }

    return true;
}

void * Wsbm::getCPUAddress(void * buf)
{
    return psbWsbmGetCPUAddress(buf);
}

uint32_t Wsbm::getGttOffset(void * buf)
{
    return psbWsbmGetGttOffset(buf);
}

bool Wsbm::wrapTTMBuffer(int64_t handle, void **buf)
{
    int ret = psbWsbmWrapTTMBuffer(handle, buf);
    if (ret) {
        ETRACE("failed to wrap buffer");
        return false;
    }

    return true;
}

bool Wsbm::unreferenceTTMBuffer(void *buf)
{
    int ret = psbWsbmUnReference(buf);
    if (ret) {
        ETRACE("failed to unreference buffer");
        return false;
    }

    return true;
}

uint64_t Wsbm::getKBufHandle(void *buf)
{
    return psbWsbmGetKBufHandle(buf);
}

bool Wsbm::waitIdleTTMBuffer(void *buf)
{
    int ret = psbWsbmWaitIdle(buf);
    if (ret) {
        ETRACE("failed to wait ttm buffer for idle");
        return false;
    }

    return true;
}
