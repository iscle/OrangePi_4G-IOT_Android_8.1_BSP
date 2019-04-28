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

#include <string.h>
#include <wsbm_pool.h>
#include <wsbm_driver.h>
#include <wsbm_manager.h>
#include <wsbm_util.h>
#include <drm/ttm/ttm_placement.h>
#include <linux/psb_drm.h>
#include <xf86drm.h>
#include <HwcTrace.h>

struct _WsbmBufferPool * mainPool = NULL;

struct PsbWsbmValidateNode
{
    struct  _ValidateNode base;
    struct psb_validate_arg arg;
};

static inline uint32_t align_to(uint32_t arg, uint32_t align)
{
    return ((arg + (align - 1)) & (~(align - 1)));
}

static struct _ValidateNode * pvrAlloc(struct _WsbmVNodeFuncs * func,
                                       int typeId)
{
    CTRACE();
    if(typeId == 0) {
        struct PsbWsbmValidateNode * vNode = malloc(sizeof(*vNode));
        if(!vNode) {
            ETRACE("failed to allocate memory");
            return NULL;
        }

        vNode->base.func = func;
        vNode->base.type_id = 0;
        return &vNode->base;
    } else {
        struct _ValidateNode * node = malloc(sizeof(*node));
        if(!node) {
            ETRACE("failed to allocate node");
            return NULL;
        }

        node->func = func;
        node->type_id = 1;
        return node;
    }
}

static void pvrFree(struct _ValidateNode * node)
{
    CTRACE();
    if(node->type_id == 0) {
        free(containerOf(node, struct PsbWsbmValidateNode, base));
    } else {
        free(node);
    }
}

static void pvrClear(struct _ValidateNode * node)
{
    CTRACE();
    if(node->type_id == 0) {
        struct PsbWsbmValidateNode * vNode =
            containerOf(node, struct PsbWsbmValidateNode, base);
        memset(&vNode->arg.d.req, 0, sizeof(vNode->arg.d.req));
    }
}

static struct _WsbmVNodeFuncs vNodeFuncs = {
    .alloc  = pvrAlloc,
    .free   = pvrFree,
    .clear  = pvrClear,
};

void psbWsbmTakedown()
{
    CTRACE();

    if (mainPool) {
        wsbmPoolTakeDown(mainPool);
        mainPool = NULL;
    }

    if (wsbmIsInitialized()) {
        wsbmTakedown();
    }
}

int psbWsbmInitialize(int drmFD)
{
    union drm_psb_extension_arg arg;
    const char drmExt[] = "psb_ttm_placement_alphadrop";
    int ret = 0;

    CTRACE();

    if (drmFD <= 0) {
        ETRACE("invalid drm fd %d", drmFD);
        return drmFD;
    }

    /*init wsbm*/
    ret = wsbmInit(wsbmNullThreadFuncs(), &vNodeFuncs);
    if (ret) {
        ETRACE("failed to initialize Wsbm, error code %d", ret);
        return ret;
    }

    VTRACE("DRM_PSB_EXTENSION %d", DRM_PSB_EXTENSION);

    /*get devOffset via drm IOCTL*/
    strncpy(arg.extension, drmExt, sizeof(drmExt));

    ret = drmCommandWriteRead(drmFD, 6/*DRM_PSB_EXTENSION*/, &arg, sizeof(arg));
    if(ret || !arg.rep.exists) {
        ETRACE("failed to get device offset, error code %d", ret);
        goto out;
    }

    VTRACE("ioctl offset %#x", arg.rep.driver_ioctl_offset);

    mainPool = wsbmTTMPoolInit(drmFD, arg.rep.driver_ioctl_offset);
    if(!mainPool) {
        ETRACE("failed to initialize TTM Pool");
        ret = -EINVAL;
        goto out;
    }

    VTRACE("Wsbm initialization succeeded. mainPool %p", mainPool);

    return 0;

out:
    psbWsbmTakedown();
    return ret;
}

int psbWsbmAllocateFromUB(uint32_t size, uint32_t align, void ** buf, void *user_pt)
{
    struct _WsbmBufferObject * wsbmBuf = NULL;
    int ret = 0;
    int offset = 0;

    ATRACE("size %d", align_to(size, 4096));

    if(!buf || !user_pt) {
        ETRACE("invalid parameter");
        return -EINVAL;
    }

    VTRACE("mainPool %p", mainPool);

    ret = wsbmGenBuffers(mainPool, 1, &wsbmBuf, align,
                        DRM_PSB_FLAG_MEM_MMU | WSBM_PL_FLAG_CACHED |
                        WSBM_PL_FLAG_NO_EVICT | WSBM_PL_FLAG_SHARED);
    if(ret) {
        ETRACE("wsbmGenBuffers failed with error code %d", ret);
        return ret;
    }

    ret = wsbmBODataUB(wsbmBuf,
                       align_to(size, 4096), NULL, NULL, 0,
                       user_pt, -1);

    if(ret) {
        ETRACE("wsbmBOData failed with error code %d", ret);
        /*FIXME: should I unreference this buffer here?*/
        return ret;
    }

    *buf = wsbmBuf;

    VTRACE("ttm UB buffer allocated. %p", *buf);
    return 0;
}

int psbWsbmAllocateTTMBuffer(uint32_t size, uint32_t align, void ** buf)
{
    struct _WsbmBufferObject * wsbmBuf = NULL;
    int ret = 0;
    int offset = 0;

    ATRACE("size %d", align_to(size, 4096));

    if(!buf) {
        ETRACE("invalid parameter");
        return -EINVAL;
    }

    VTRACE("mainPool %p", mainPool);

    ret = wsbmGenBuffers(mainPool, 1, &wsbmBuf, align,
                        (WSBM_PL_FLAG_VRAM | WSBM_PL_FLAG_TT |
                         WSBM_PL_FLAG_SHARED | WSBM_PL_FLAG_NO_EVICT));
    if(ret) {
        ETRACE("wsbmGenBuffers failed with error code %d", ret);
        return ret;
    }

    ret = wsbmBOData(wsbmBuf, align_to(size, 4096), NULL, NULL, 0);
    if(ret) {
        ETRACE("wsbmBOData failed with error code %d", ret);
        /*FIXME: should I unreference this buffer here?*/
        return ret;
    }

    /* wsbmBOReference(wsbmBuf); */ /* no need to add reference */

    *buf = wsbmBuf;

    VTRACE("ttm buffer allocated. %p", *buf);
    return 0;
}

int psbWsbmWrapTTMBuffer(uint64_t handle, void **buf)
{
    int ret = 0;
    struct _WsbmBufferObject *wsbmBuf;

    if (!buf) {
        ETRACE("invalid parameter");
        return -EINVAL;
    }

    ret = wsbmGenBuffers(mainPool, 1, &wsbmBuf, 0,
                        (WSBM_PL_FLAG_VRAM | WSBM_PL_FLAG_TT |
                        /*WSBM_PL_FLAG_NO_EVICT |*/ WSBM_PL_FLAG_SHARED));

    if (ret) {
        ETRACE("wsbmGenBuffers failed with error code %d", ret);
        return ret;
    }

    ret = wsbmBOSetReferenced(wsbmBuf, handle);
    if (ret) {
        ETRACE("wsbmBOSetReferenced failed with error code %d", ret);
        return ret;
    }

    *buf = (void *)wsbmBuf;

    VTRACE("wrap buffer %p for handle %#x", wsbmBuf, handle);
    return 0;
}

int psbWsbmWrapTTMBuffer2(uint64_t handle, void **buf)
{
    int ret = 0;
    struct _WsbmBufferObject *wsbmBuf;

    if (!buf) {
        ETRACE("invalid parameter");
        return -EINVAL;
    }

    ret = wsbmGenBuffers(mainPool, 1, &wsbmBuf, 4096,
            (WSBM_PL_FLAG_SHARED | DRM_PSB_FLAG_MEM_MMU | WSBM_PL_FLAG_UNCACHED));

    if (ret) {
        ETRACE("wsbmGenBuffers failed with error code %d", ret);
        return ret;
    }

    *buf = (void *)wsbmBuf;

    VTRACE("wrap buffer %p for handle %#x", wsbmBuf, handle);
    return 0;
}


int psbWsbmCreateFromUB(void *buf, uint32_t size, void *vaddr)
{
    int ret = 0;
    struct _WsbmBufferObject *wsbmBuf;

    if (!buf || !vaddr) {
        ETRACE("invalid parameter");
        return -EINVAL;
    }

    wsbmBuf = (struct _WsbmBufferObject *)buf;
    ret = wsbmBODataUB(wsbmBuf, size, NULL, NULL, 0, vaddr, -1);
    if (ret) {
        ETRACE("wsbmBODataUB failed with error code %d", ret);
        return ret;
    }

    return 0;
}

int psbWsbmUnReference(void *buf)
{
    struct _WsbmBufferObject *wsbmBuf;

    if (!buf) {
        ETRACE("invalid parameter");
        return -EINVAL;
    }

    wsbmBuf = (struct _WsbmBufferObject *)buf;

    wsbmBOUnreference(&wsbmBuf);

    return 0;
}

int psbWsbmDestroyTTMBuffer(void * buf)
{
    CTRACE();

    if(!buf) {
        ETRACE("invalid ttm buffer");
        return -EINVAL;
    }

    /*FIXME: should I unmap this buffer object first?*/
    wsbmBOUnmap((struct _WsbmBufferObject *)buf);

    wsbmBOUnreference((struct _WsbmBufferObject **)&buf);

    XTRACE();

    return 0;
}

void * psbWsbmGetCPUAddress(void * buf)
{
    if(!buf) {
        ETRACE("invalid ttm buffer");
        return NULL;
    }

    VTRACE("buffer object %p", buf);

    void * address = wsbmBOMap((struct _WsbmBufferObject *)buf,
                                WSBM_ACCESS_READ | WSBM_ACCESS_WRITE);
    if(!address) {
        ETRACE("failed to map buffer object");
        return NULL;
    }

    VTRACE("mapped successfully. %p, size %ld",
        address, wsbmBOSize((struct _WsbmBufferObject *)buf));

    return address;
}

uint32_t psbWsbmGetGttOffset(void * buf)
{
    if(!buf) {
        ETRACE("invalid ttm buffer");
        return 0;
    }

    VTRACE("buffer object %p", buf);

    uint32_t offset =
        wsbmBOOffsetHint((struct _WsbmBufferObject *)buf) - 0x10000000;

    VTRACE("offset %#x", offset >> 12);

    return offset >> 12;
}

uint32_t psbWsbmGetKBufHandle(void *buf)
{
    if (!buf) {
        ETRACE("invalid ttm buffer");
        return 0;
    }

    return (wsbmKBufHandle(wsbmKBuf((struct _WsbmBufferObject *)buf)));
}

uint32_t psbWsbmWaitIdle(void *buf)
{
    if (!buf) {
        ETRACE("invalid ttm buffer");
        return -EINVAL;
    }

    wsbmBOWaitIdle(buf, 0);
    return 0;
}
