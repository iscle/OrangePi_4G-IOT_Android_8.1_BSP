/*
 * Copyright (C) 2010 ARM Limited. All rights reserved.
 *
 * Copyright (C) 2008 The Android Open Source Project
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

#ifndef GRALLOC_PRIV_H_
#define GRALLOC_PRIV_H_

#include <stdint.h>
#include <pthread.h>
#include <errno.h>
#include <linux/fb.h>
#include <linux/ion.h>
#include <sys/types.h>
#include <unistd.h>
#include <sys/mman.h>
#include <hardware/gralloc.h>
#include <cutils/native_handle.h>
#include "alloc_device.h"
#include <utils/Log.h>

#include "mali_gralloc_formats.h"
#include "gralloc_helper.h"


/* NOTE:
 * If your framebuffer device driver is integrated with dma_buf, you will have to
 * change this IOCTL definition to reflect your integration with the framebuffer
 * device.
 * Expected return value is a structure filled with a file descriptor
 * backing your framebuffer device memory.
 */
struct fb_dmabuf_export
{
	__u32 fd;
	__u32 flags;
};
#define FBIOGET_DMABUF	_IOR('F', 0x21, struct fb_dmabuf_export)


/* the max string size of GRALLOC_HARDWARE_GPU0 & GRALLOC_HARDWARE_FB0
 * 8 is big enough for "gpu0" & "fb0" currently
 */
#define MALI_GRALLOC_HARDWARE_MAX_STR_LEN 8
#define NUM_FB_BUFFERS 2

/* Define number of shared file descriptors */
#define GRALLOC_ARM_NUM_FDS 2

#define NUM_INTS_IN_PRIVATE_HANDLE ((sizeof(struct private_handle_t) - sizeof(native_handle)) / sizeof(int) - sNumFds)

#define SZ_4K      0x00001000
#define SZ_2M      0x00200000

typedef enum
{
	MALI_YUV_NO_INFO,
	MALI_YUV_BT601_NARROW,
	MALI_YUV_BT601_WIDE,
	MALI_YUV_BT709_NARROW,
	MALI_YUV_BT709_WIDE
} mali_gralloc_yuv_info;

typedef enum
{
	MALI_DPY_TYPE_UNKNOWN = 0,
	MALI_DPY_TYPE_CLCD,
	MALI_DPY_TYPE_HDLCD
} mali_dpy_type;

struct private_handle_t;

struct private_module_t
{
	gralloc_module_t base;

	struct private_handle_t* framebuffer;
	uint32_t flags;
	uint32_t numBuffers;
	uint32_t bufferMask;
	pthread_mutex_t lock;
	buffer_handle_t currentBuffer;
	int ion_client;
	mali_dpy_type dpy_type;

	struct fb_var_screeninfo info;
	struct fb_fix_screeninfo finfo;
	float xdpi;
	float ydpi;
	float fps;
	int swapInterval;

#ifdef __cplusplus
	/* Never intended to be used from C code */
	enum
	{
		// flag to indicate we'll post this buffer
		PRIV_USAGE_LOCKED_FOR_POST = 0x80000000
	};
#endif

#ifdef __cplusplus
	/* default constructor */
	private_module_t();
#endif
};

#ifndef __cplusplus
/* C99 with pedantic don't allow anonymous unions which is used in below struct
 * Disable pedantic for C for this struct only.
 */
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wpedantic"
#endif

#ifdef __cplusplus
struct private_handle_t : public native_handle
{
#else
struct private_handle_t
{
	struct native_handle nativeHandle;
#endif

#ifdef __cplusplus
	/* Never intended to be used from C code */
	enum
	{
		PRIV_FLAGS_FRAMEBUFFER                = 0x00000001,
		PRIV_FLAGS_USES_ION_COMPOUND_HEAP     = 0x00000002,
		PRIV_FLAGS_USES_ION                   = 0x00000004,
		PRIV_FLAGS_USES_ION_DMA_HEAP          = 0x00000008
	};

	enum
	{
		LOCK_STATE_WRITE     =   1<<31,
		LOCK_STATE_MAPPED    =   1<<30,
		LOCK_STATE_READ_MASK =   0x3FFFFFFF
	};
#endif

	/*
	 * Shared file descriptor for dma_buf sharing. This must be the first element in the
	 * structure so that binder knows where it is and can properly share it between
	 * processes.
	 * DO NOT MOVE THIS ELEMENT!
	 */
	int     share_fd;
	int     share_attr_fd;

	ion_user_handle_t ion_hnd_UNUSED;

	// ints
	int        magic;
	int        req_format;
	uint64_t   internal_format;
	int        byte_stride;
	int        flags;
	int        usage;
	int        size;
	int        width;
	int        height;
	int        internalWidth;
	int        internalHeight;
	int        stride;
	union {
		void*    base;
		uint64_t padding;
	};
	int        lockState;
	int        writeOwner;
	int        pid_UNUSED;

	// locally mapped shared attribute area
	union {
		void*    attr_base;
		uint64_t padding3;
	};

	mali_gralloc_yuv_info yuv_info;

	// Following members is for framebuffer only
	int   shallow_fbdev_fd; // shallow copy, not dup'ed
	union {
		off_t    offset;
		uint64_t padding4;
	};

	/*
	 * min_pgsz denotes minimum phys_page size used by this buffer.
	 * if buffer memory is physical contiguous set min_pgsz to buff->size
	 * if not sure buff's real phys_page size, you can use SZ_4K for safe.
	 */
	int min_pgsz;
#ifdef __cplusplus
	/*
	 * We track the number of integers in the structure. There are 16 unconditional
	 * integers (magic - pid, yuv_info, shallow_fbdev_fd and offset).
	 * Note that the shallow_fbdev_fd element is
	 * considered an int not an fd because it is not intended to be used outside the
	 * surface flinger process. The GRALLOC_ARM_NUM_INTS variable is used to track the
	 * number of integers that are conditionally included. Similar considerations apply
	 * to the number of fds.
	 */
	static const int sNumFds = GRALLOC_ARM_NUM_FDS;
	static const int sMagic = 0x3141592;

	private_handle_t(int _flags, int _usage, int _size, void *_base, int lock_state, int fb_file, off_t fb_offset):
		share_fd(-1),
		share_attr_fd(-1),
		ion_hnd_UNUSED(-1),
		magic(sMagic),
		flags(_flags),
		usage(_usage),
		size(_size),
		width(0),
		height(0),
		stride(0),
		base(_base),
		lockState(lock_state),
		writeOwner(0),
		pid_UNUSED(-1),
		attr_base(MAP_FAILED),
		yuv_info(MALI_YUV_NO_INFO),
		shallow_fbdev_fd(fb_file),
		offset(fb_offset)
	{
		version = sizeof(native_handle);
		numFds = sNumFds;
		numInts = NUM_INTS_IN_PRIVATE_HANDLE;
	}

	~private_handle_t()
	{
		magic = 0;
	}

	bool usesPhysicallyContiguousMemory()
	{
		return (flags & PRIV_FLAGS_FRAMEBUFFER) ? true : false;
	}

	static int validate(const native_handle* h)
	{
		const private_handle_t* hnd = (const private_handle_t*)h;
		if (!h ||
		    h->version != sizeof(native_handle) ||
		    h->numInts != NUM_INTS_IN_PRIVATE_HANDLE ||
		    h->numFds != sNumFds ||
		    hnd->magic != sMagic)
		{
			return -EINVAL;
		}
		return 0;
	}

	static private_handle_t* dynamicCast(const native_handle* in)
	{
		if (validate(in) == 0)
		{
			return (private_handle_t*) in;
		}
		return NULL;
	}
#endif
};
#ifndef __cplusplus
/* Restore previous diagnostic for pedantic */
#pragma GCC diagnostic pop
#endif

#endif /* GRALLOC_PRIV_H_ */
