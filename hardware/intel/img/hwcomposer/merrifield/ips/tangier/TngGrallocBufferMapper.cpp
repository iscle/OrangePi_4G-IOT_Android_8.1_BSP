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
#include <Drm.h>
#include <Hwcomposer.h>
#include <tangier/TngGrallocBufferMapper.h>
#include <common/WsbmWrapper.h>

namespace android {
namespace intel {

TngGrallocBufferMapper::TngGrallocBufferMapper(const hw_device_t& gralloc,
                                               DataBuffer& buffer)
    : GrallocBufferMapperBase(buffer),
      mGralloc(gralloc),
      mBufferObject(0)
{
    CTRACE();

	const native_handle_t *h = (native_handle_t *)mHandle;

	mClonedHandle = native_handle_create(h->numFds, h->numInts);
	if (mClonedHandle == 0) {
		ALOGE("%s:Failed to create handle, out of memory!");
		return;
	}
	for (int i = 0; i < h->numFds; i++)
	{
		mClonedHandle->data[i] = (h->data[i] >= 0) ? dup(h->data[i]) : -1;
	}
	memcpy(mClonedHandle->data + h->numFds, h->data + h->numFds, h->numInts*sizeof(int));
}

TngGrallocBufferMapper::~TngGrallocBufferMapper()
{
    CTRACE();

	if (mClonedHandle == 0)
		return;
	native_handle_close(mClonedHandle);
	native_handle_delete(mClonedHandle);
}

bool TngGrallocBufferMapper::gttMap(void *vaddr,
                                      uint32_t size,
                                      uint32_t gttAlign,
                                      int *offset)
{
    struct psb_gtt_mapping_arg arg;
    bool ret;

    ATRACE("vaddr = %p, size = %d", vaddr, size);

    if (!vaddr || !size || !offset) {
        VTRACE("invalid parameters");
        return false;
    }

    arg.type = PSB_GTT_MAP_TYPE_VIRTUAL;
    arg.page_align = gttAlign;
    arg.vaddr = (unsigned long)vaddr;
    arg.size = size;

    Drm *drm = Hwcomposer::getInstance().getDrm();
    ret = drm->writeReadIoctl(DRM_PSB_GTT_MAP, &arg, sizeof(arg));
    if (ret == false) {
        ETRACE("gtt mapping failed");
        return false;
    }

    VTRACE("offset = %#x", arg.offset_pages);
    *offset =  arg.offset_pages;
    return true;
}

bool TngGrallocBufferMapper::gttUnmap(void *vaddr)
{
    struct psb_gtt_mapping_arg arg;
    bool ret;

    ATRACE("vaddr = %p", vaddr);

    if (!vaddr) {
        ETRACE("invalid parameter");
        return false;
    }

    arg.type = PSB_GTT_MAP_TYPE_VIRTUAL;
    arg.vaddr = (unsigned long)vaddr;

    Drm *drm = Hwcomposer::getInstance().getDrm();
    ret = drm->writeIoctl(DRM_PSB_GTT_UNMAP, &arg, sizeof(arg));
    if (ret == false) {
        ETRACE("gtt unmapping failed");
        return false;
    }

    return true;
}

bool TngGrallocBufferMapper::map()
{
    void *vaddr[SUB_BUFFER_MAX];
    uint32_t size[SUB_BUFFER_MAX];
    int gttOffsetInPage = 0;
    bool ret;
    int err;
    int i;

    CTRACE();
    // get virtual address
    err = gralloc_get_buffer_cpu_addresses_img(&mGralloc,
                                  (buffer_handle_t)mClonedHandle,
                                  vaddr,
                                  size);
    if (err) {
        ETRACE("failed to map. err = %d", err);
        return false;
    }

    for (i = 0; i < SUB_BUFFER_MAX; i++) {
        // skip gtt mapping for empty sub buffers
        if (!vaddr[i] || !size[i])
            continue;

        // map to gtt
        ret = gttMap(vaddr[i], size[i], 0, &gttOffsetInPage);
        if (!ret) {
            VTRACE("failed to map %d into gtt", i);
            break;
        }

        mCpuAddress[i] = vaddr[i];
        mSize[i] = size[i];
        mGttOffsetInPage[i] = gttOffsetInPage;
        // TODO:  set kernel handle
        mKHandle[i] = 0;
    }

    if (i == SUB_BUFFER_MAX) {
        return true;
    }

    // error handling
    for (i = 0; i < SUB_BUFFER_MAX; i++) {
        if (mCpuAddress[i]) {
            gttUnmap(mCpuAddress[i]);
        }
    }

    err = gralloc_put_buffer_cpu_addresses_img(&mGralloc,
                                  (buffer_handle_t)mClonedHandle);
    return false;
}

bool TngGrallocBufferMapper::unmap()
{
    int i;
    int err;

    CTRACE();

    for (i = 0; i < SUB_BUFFER_MAX; i++) {
        if (mCpuAddress[i])
            gttUnmap(mCpuAddress[i]);

        mGttOffsetInPage[i] = 0;
        mCpuAddress[i] = 0;
        mSize[i] = 0;
    }

    err = gralloc_put_buffer_cpu_addresses_img(&mGralloc,
                                  (buffer_handle_t)mClonedHandle);
    if (err) {
        ETRACE("failed to unmap. err = %d", err);
    }
    return err;
}

buffer_handle_t TngGrallocBufferMapper::getKHandle(int subIndex)
{
    buffer_handle_t ret = GrallocBufferMapperBase::getKHandle(subIndex);
    if (subIndex == 0 && ret == 0) {
        if (mapKhandle())
            return mKHandle[subIndex];
    }

    return ret;
}

bool TngGrallocBufferMapper::mapKhandle()
{
    // TODO: this is a complete hack and temporary workaround
    // need support from DDK to map khandle
    void *wsbmBufferObject = 0;
    int ret = psbWsbmWrapTTMBuffer2((uint64_t)mHandle, &wsbmBufferObject);
    if (ret != 0) {
        ETRACE("Wrap ttm buffer failed!");
        return false;
    }

    ret = psbWsbmCreateFromUB(wsbmBufferObject, mWidth * mHeight, mCpuAddress[0]);
    if (ret != 0) {
        ETRACE("Create from UB failed!");
        return false;
    }

    mKHandle[0] = (buffer_handle_t)(unsigned long)psbWsbmGetKBufHandle(wsbmBufferObject);
    psbWsbmUnReference(wsbmBufferObject);
    return true;
}

buffer_handle_t TngGrallocBufferMapper::getFbHandle(int subIndex)
{
    void *vaddr[SUB_BUFFER_MAX];
    uint32_t size[SUB_BUFFER_MAX];
    int err;

    CTRACE();

    if (subIndex < 0 || subIndex >= SUB_BUFFER_MAX) {
        return 0;
    }

    // get virtual address
    err = gralloc_get_buffer_cpu_addresses_img(&mGralloc,
                                  (buffer_handle_t)mClonedHandle,
                                  vaddr,
                                  size);
    if (err) {
        ETRACE("failed to map. err = %d", err);
        return 0;
    }

    return (buffer_handle_t)vaddr[subIndex];
}

void TngGrallocBufferMapper::putFbHandle()
{
    int err = gralloc_put_buffer_cpu_addresses_img(&mGralloc,
                                  (buffer_handle_t)mClonedHandle);
    if (err) {
        ETRACE("failed to unmap. err = %d", err);
    }
    return;

}

} // namespace intel
} // namespace android
