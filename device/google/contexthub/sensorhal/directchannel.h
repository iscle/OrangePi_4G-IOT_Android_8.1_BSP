/*
 * Copyright (C) 2016 The Android Open Source Project
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

#ifndef DIRECTCHANNEL_H_
#define DIRECTCHANNEL_H_

#include "ring.h"
#include <cutils/native_handle.h>
#include <hardware/gralloc.h>
#include <hardware/gralloc1.h>
#include <hardware/sensors.h>
#include <utils/Singleton.h>
#include <memory>

namespace android {

class DirectChannelBase {
public:
    DirectChannelBase() : mError(NO_INIT), mSize(0), mBase(nullptr) { }
    virtual ~DirectChannelBase() {}
    virtual bool memoryMatches(const struct sensors_direct_mem_t *mem) const = 0;

    bool isValid();
    int getError();
    void write(const sensors_event_t * ev);

protected:
    int mError;
    std::unique_ptr<LockfreeBuffer> mBuffer;

    size_t mSize;
    void* mBase;
};

class AshmemDirectChannel : public DirectChannelBase {
public:
    AshmemDirectChannel(const struct sensors_direct_mem_t *mem);
    ~AshmemDirectChannel() override;
    bool memoryMatches(const struct sensors_direct_mem_t *mem) const override;
private:
    int mAshmemFd;
};

class GrallocHalWrapper : public Singleton<GrallocHalWrapper> {
public:
    int registerBuffer(const native_handle_t *handle);
    int unregisterBuffer(const native_handle_t *handle);
    int lock(const native_handle_t *handle, int usage, int l, int t, int w, int h, void **vaddr);
    int unlock(const native_handle_t *handle);
    bool isSameMemory(const native_handle_t *h1, const native_handle_t *h2);
    bool unregisterImplyDelete() { return mUnregisterImplyDelete; }
private:
    friend class Singleton<GrallocHalWrapper>;
    GrallocHalWrapper();
    ~GrallocHalWrapper();
    static int mapGralloc1Error(int grallocError);

    int mError;
    int mVersion;
    gralloc_module_t *mGrallocModule;
    // gralloc
    alloc_device_t *mAllocDevice;

    // gralloc1
    gralloc1_device_t *mGralloc1Device;
    GRALLOC1_PFN_RETAIN mPfnRetain;
    GRALLOC1_PFN_RELEASE mPfnRelease;
    GRALLOC1_PFN_LOCK mPfnLock;
    GRALLOC1_PFN_UNLOCK mPfnUnlock;
    GRALLOC1_PFN_GET_BACKING_STORE mPfnGetBackingStore;
    bool mUnregisterImplyDelete;
};

class GrallocDirectChannel : public DirectChannelBase {
public:
    GrallocDirectChannel(const struct sensors_direct_mem_t *mem);
    ~GrallocDirectChannel() override;
    bool memoryMatches(const struct sensors_direct_mem_t *mem) const override;
private:
    native_handle_t *mNativeHandle;
};

} // namespace android

#endif  // DIRECTCHANNEL_H_
