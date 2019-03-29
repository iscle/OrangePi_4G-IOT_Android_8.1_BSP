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

#ifndef ANDROID_INCLUDE_HARDWARE_GOLDFISH_DMA_H
#define ANDROID_INCLUDE_HARDWARE_GOLDFISH_DMA_H

#include <errno.h>
#include <linux/ioctl.h>
#include <linux/types.h>
#include <sys/cdefs.h>
#include <fcntl.h>
#include <stdlib.h>

/* There is an ioctl associated with goldfish dma driver.
 * Make it conflict with ioctls that are not likely to be used
 * in the emulator.
 * 'G'	00-3F	drivers/misc/sgi-gru/grulib.h	conflict!
 * 'G'	00-0F	linux/gigaset_dev.h	conflict!
 */
#define GOLDFISH_DMA_IOC_MAGIC	'G'

#define GOLDFISH_DMA_IOC_LOCK			_IOWR(GOLDFISH_DMA_IOC_MAGIC, 0, struct goldfish_dma_ioctl_info)
#define GOLDFISH_DMA_IOC_UNLOCK			_IOWR(GOLDFISH_DMA_IOC_MAGIC, 1, struct goldfish_dma_ioctl_info)
#define GOLDFISH_DMA_IOC_GETOFF			_IOWR(GOLDFISH_DMA_IOC_MAGIC, 2, struct goldfish_dma_ioctl_info)
#define GOLDFISH_DMA_IOC_CREATE_REGION	_IOWR(GOLDFISH_DMA_IOC_MAGIC, 3, struct goldfish_dma_ioctl_info)

struct goldfish_dma_ioctl_info {
    uint64_t phys_begin;
    uint64_t size;
};

// userspace interface
struct goldfish_dma_context {
    void* mapped;
#if !defined(__LP64__)
    int mapped_padding;
#endif
    uint64_t sz; // size of reservation
    int fd;
    int fd_padding;
};

int goldfish_dma_lock(struct goldfish_dma_context* cxt);
int goldfish_dma_unlock(struct goldfish_dma_context* cxt);
int goldfish_dma_create_region(uint32_t sz, struct goldfish_dma_context* res);

void* goldfish_dma_map(struct goldfish_dma_context* cxt);
int goldfish_dma_unmap(struct goldfish_dma_context* cxt);

void goldfish_dma_write(struct goldfish_dma_context* cxt,
                        void* to_write,
                        uint32_t sz);

void goldfish_dma_free(goldfish_dma_context* cxt);
uint64_t goldfish_dma_guest_paddr(struct goldfish_dma_context* cxt);

#endif
