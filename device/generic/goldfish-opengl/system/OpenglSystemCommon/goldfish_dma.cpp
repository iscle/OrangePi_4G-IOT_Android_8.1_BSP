/*
 * Copyright (C) 2016 Google, Inc.
 *
 * This software is licensed under the terms of the GNU General Public
 * License version 2, as published by the Free Software Foundation, and
 * may be copied, distributed, and modified under those terms.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 */

#include "goldfish_dma.h"
#include "qemu_pipe.h"

#include <cutils/log.h>
#include <sys/mman.h>
#include <stdlib.h>
#include <string.h>

int goldfish_dma_lock(struct goldfish_dma_context* cxt) {
    struct goldfish_dma_ioctl_info info;

    return ioctl(cxt->fd, GOLDFISH_DMA_IOC_LOCK, &info);
}

int goldfish_dma_unlock(struct goldfish_dma_context* cxt) {
    struct goldfish_dma_ioctl_info info;

    return ioctl(cxt->fd, GOLDFISH_DMA_IOC_UNLOCK, &info);
}

int goldfish_dma_create_region(uint32_t sz, struct goldfish_dma_context* res) {

    res->fd = qemu_pipe_open("opengles");
    res->mapped = NULL;
    res->sz = 0;

    if (res->fd > 0) {
        // now alloc
        struct goldfish_dma_ioctl_info info;
        info.size = sz;
        int alloc_res = ioctl(res->fd, GOLDFISH_DMA_IOC_CREATE_REGION, &info);

        if (alloc_res) {
            ALOGE("%s: failed to allocate DMA region. errno=%d",
                  __FUNCTION__, errno);
            close(res->fd);
            res->fd = -1;
            return alloc_res;
        }

        res->sz = sz;
        ALOGV("%s: successfully allocated goldfish DMA region with size %lu cxt=%p",
              __FUNCTION__, sz, res);
        return 0;
    } else {
        ALOGE("%s: could not obtain fd to device! fd %d errno=%d\n",
              __FUNCTION__, res->fd, errno);
        return ENODEV;
    }
}

void* goldfish_dma_map(struct goldfish_dma_context* cxt) {
    ALOGV("%s: on fd %d errno=%d", __FUNCTION__, cxt->fd, errno);
    cxt->mapped = mmap(0, cxt->sz, PROT_WRITE, MAP_SHARED, cxt->fd, 0);
    ALOGV("%s: mapped addr=%p errno=%d", __FUNCTION__, cxt->mapped, errno);

    if (cxt->mapped == MAP_FAILED) {
        cxt->mapped = NULL;
    }
    return cxt->mapped;
}

int goldfish_dma_unmap(struct goldfish_dma_context* cxt) {
    munmap(cxt->mapped, cxt->sz);
    cxt->mapped = NULL;
    cxt->sz = 0;
    return 0;
}

void goldfish_dma_write(struct goldfish_dma_context* cxt,
                               void* to_write,
                               uint32_t sz) {
    ALOGV("%s: mapped addr=%p", __FUNCTION__, cxt->mapped);
    memcpy(cxt->mapped, to_write, sz);
}

void goldfish_dma_free(goldfish_dma_context* cxt) {
    struct goldfish_dma_ioctl_info info;
    close(cxt->fd);
}

uint64_t goldfish_dma_guest_paddr(struct goldfish_dma_context* cxt) {
    struct goldfish_dma_ioctl_info info;
    ioctl(cxt->fd, GOLDFISH_DMA_IOC_GETOFF, &info);
    return info.phys_begin;
}
