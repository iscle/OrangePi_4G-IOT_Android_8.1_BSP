/*
 * Copyright (C) 2013 ARM Limited. All rights reserved.
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

#include <string.h>
#include <errno.h>
#include <inttypes.h>
#include <pthread.h>

#include <cutils/log.h>
#include <cutils/atomic.h>
#include <hardware/hardware.h>
#include <hardware/gralloc.h>

#include <sys/ioctl.h>

#include "alloc_device.h"
#include "gralloc_priv.h"
#include "gralloc_helper.h"
#include "framebuffer_device.h"

#include "mali_gralloc_formats.h"

#include <linux/ion.h>
#include <ion/ion.h>

static void init_afbc(uint8_t *buf, uint64_t internal_format, int w, int h)
{
	uint32_t n_headers = (w * h) / 64;
	uint32_t body_offset = n_headers * 16;
	uint32_t headers[][4] = { {body_offset, 0x1, 0x0, 0x0}, /* Layouts 0, 3, 4 */
	                          {(body_offset + (1 << 28)), 0x200040, 0x4000, 0x80} /* Layouts 1, 5 */
	                        };
	uint32_t i, layout;

	/* map format if necessary (also removes internal extension bits) */
	uint64_t base_format = internal_format & MALI_GRALLOC_INTFMT_FMT_MASK;

	switch (base_format)
	{
		case MALI_GRALLOC_FORMAT_INTERNAL_RGBA_8888:
		case MALI_GRALLOC_FORMAT_INTERNAL_RGBX_8888:
		case MALI_GRALLOC_FORMAT_INTERNAL_RGB_888:
		case MALI_GRALLOC_FORMAT_INTERNAL_RGB_565:
		case MALI_GRALLOC_FORMAT_INTERNAL_BGRA_8888:
			layout = 0;
			break;

		case MALI_GRALLOC_FORMAT_INTERNAL_YV12:
		case MALI_GRALLOC_FORMAT_INTERNAL_NV12:
		case MALI_GRALLOC_FORMAT_INTERNAL_NV21:
			layout = 1;
			break;
		default:
			layout = 0;
	}

	ALOGV("Writing AFBC header layout %d for format %" PRIu64, layout, base_format);

	for (i = 0; i < n_headers; i++)
	{
		memcpy(buf, headers[layout], sizeof(headers[layout]));
		buf += sizeof(headers[layout]);
	}

}

static ion_user_handle_t alloc_from_ion_heap(int ion_fd, size_t size, unsigned int heap_mask,
		unsigned int flags, int *min_pgsz)
{
	ion_user_handle_t ion_hnd = -1;
	int ret;

	if ((ion_fd < 0) || (size <= 0) || (heap_mask == 0) || (min_pgsz == NULL))
		return -1;

	ret = ion_alloc(ion_fd, size, 0, heap_mask, flags, &ion_hnd);
	if (ret < 0)
	{
#if defined(ION_HEAP_SECURE_MASK)
		if (heap_mask == ION_HEAP_SECURE_MASK)
		{
			return -1;
		}
		else
#endif
		{
			/* If everything else failed try system heap */
			flags = 0; /* Fallback option flags are not longer valid */
			heap_mask = ION_HEAP_SYSTEM_MASK;
			ret = ion_alloc(ion_fd, size, 0, heap_mask, flags, &ion_hnd);
		}
	}

	if (ret >= 0)
	{
		switch (heap_mask)
		{
		case ION_HEAP_SYSTEM_MASK:
			*min_pgsz = SZ_4K;
			break;
		case ION_HEAP_SYSTEM_CONTIG_MASK:
		case ION_HEAP_CARVEOUT_MASK:
#ifdef ION_HEAP_TYPE_DMA_MASK
		case ION_HEAP_TYPE_DMA_MASK:
#endif
			*min_pgsz = size;
			break;
#ifdef ION_HEAP_CHUNK_MASK
		/* NOTE: if have this heap make sure your ION chunk size is 2M*/
		case ION_HEAP_CHUNK_MASK:
			*min_pgsz = SZ_2M;
			break;
#endif
#ifdef ION_HEAP_COMPOUND_PAGE_MASK
		case ION_HEAP_COMPOUND_PAGE_MASK:
			*min_pgsz = SZ_2M;
			break;
#endif
		/* If have customized heap please set the suitable pg type according to
		 * the customized ION implementation
		 */
#ifdef ION_HEAP_CUSTOM_MASK
		case ION_HEAP_CUSTOM_MASK:
			*min_pgsz = SZ_4K;
			break;
#endif
		default:
			*min_pgsz = SZ_4K;
			break;
		}
	}

	return ion_hnd;
}

unsigned int pick_ion_heap(int usage)
{
	unsigned int heap_mask;

	if(usage & GRALLOC_USAGE_PROTECTED)
	{
#if defined(ION_HEAP_SECURE_MASK)
		heap_mask = ION_HEAP_SECURE_MASK;
#else
		AERR("Protected ION memory is not supported on this platform.");
		return 0;
#endif
	}
#if defined(ION_HEAP_TYPE_COMPOUND_PAGE_MASK) && GRALLOC_USE_ION_COMPOUND_PAGE_HEAP
	else if(!(usage & GRALLOC_USAGE_HW_VIDEO_ENCODER) && (usage & (GRALLOC_USAGE_HW_FB | GRALLOC_USAGE_HW_COMPOSER)))
	{
		heap_mask = ION_HEAP_TYPE_COMPOUND_PAGE_MASK;
	}
#elif defined(ION_HEAP_TYPE_DMA_MASK) && GRALLOC_USE_ION_DMA_HEAP
	else if(!(usage & GRALLOC_USAGE_HW_VIDEO_ENCODER) && (usage & (GRALLOC_USAGE_HW_FB | GRALLOC_USAGE_HW_COMPOSER)))
	{
		heap_mask = ION_HEAP_TYPE_DMA_MASK;
	}
#endif
	else
	{
		heap_mask = ION_HEAP_SYSTEM_MASK;
	}

	return heap_mask;
}

void set_ion_flags(unsigned int heap_mask, int usage, unsigned int *priv_heap_flag, int *ion_flags)
{
#if !GRALLOC_USE_ION_DMA_HEAP
	GRALLOC_UNUSED(heap_mask);
#endif

	if (priv_heap_flag)
	{
#if defined(ION_HEAP_TYPE_DMA_MASK) && GRALLOC_USE_ION_DMA_HEAP
		if (heap_mask == ION_HEAP_TYPE_DMA_MASK)
		{
			*priv_heap_flag = private_handle_t::PRIV_FLAGS_USES_ION_DMA_HEAP;
		}
#endif
	}

	if (ion_flags)
	{
#if defined(ION_HEAP_TYPE_DMA_MASK) && GRALLOC_USE_ION_DMA_HEAP
		if(heap_mask != ION_HEAP_TYPE_DMA_MASK)
		{
#endif
			if ( (usage & GRALLOC_USAGE_SW_READ_MASK) == GRALLOC_USAGE_SW_READ_OFTEN )
			{
				*ion_flags = ION_FLAG_CACHED | ION_FLAG_CACHED_NEEDS_SYNC;
			}
#if defined(ION_HEAP_TYPE_DMA_MASK) && GRALLOC_USE_ION_DMA_HEAP
		}
#endif
	}
}

int alloc_backend_alloc(alloc_device_t* dev, size_t size, int usage, buffer_handle_t* pHandle, uint64_t fmt, int w, int h)
{
	private_module_t* m = reinterpret_cast<private_module_t*>(dev->common.module);
	ion_user_handle_t ion_hnd;
	unsigned char *cpu_ptr = NULL;
	int shared_fd;
	int ret;
	unsigned int heap_mask, priv_heap_flag = 0;
	int ion_flags = 0;
	static int support_protected = 1; /* initially, assume we support protected memory */
	int lock_state = 0;
	int min_pgsz = 0;

	heap_mask = pick_ion_heap(usage);
	if(heap_mask == 0)
	{
		AERR("Failed to find an appropriate ion heap");
		return -1;
	}
	set_ion_flags(heap_mask, usage, &priv_heap_flag, &ion_flags);

	ion_hnd = alloc_from_ion_heap(m->ion_client, size, heap_mask, ion_flags, &min_pgsz);
	if (ion_hnd < 0)
	{
		AERR("Failed to ion_alloc from ion_client:%d", m->ion_client);
		return -1;
	}

	ret = ion_share( m->ion_client, ion_hnd, &shared_fd );
	if ( ret != 0 )
	{
		AERR( "ion_share( %d ) failed", m->ion_client );
		if ( 0 != ion_free( m->ion_client, ion_hnd ) ) AERR( "ion_free( %d ) failed", m->ion_client );		
		return -1;
	}

        // we do not need ion_hnd once we have shared_fd
        if (0 != ion_free(m->ion_client, ion_hnd))
        {
            AWAR("ion_free( %d ) failed", m->ion_client);
        }
        ion_hnd = -1;

	if (!(usage & GRALLOC_USAGE_PROTECTED))
	{
		cpu_ptr = (unsigned char*)mmap( NULL, size, PROT_READ | PROT_WRITE, MAP_SHARED, shared_fd, 0 );

		if ( MAP_FAILED == cpu_ptr )
		{
			AERR( "ion_map( %d ) failed", m->ion_client );
			close( shared_fd );
			return -1;
		}
		lock_state = private_handle_t::LOCK_STATE_MAPPED;

#if GRALLOC_INIT_AFBC == 1
		if (fmt & MALI_GRALLOC_INTFMT_AFBCENABLE_MASK)
		{
			init_afbc(cpu_ptr, fmt, w, h);
		}
#else
		GRALLOC_UNUSED(fmt);
		GRALLOC_UNUSED(w);
		GRALLOC_UNUSED(h);

#endif /* GRALLOC_INIT_AFBC == 1 */
	}

	private_handle_t *hnd = new private_handle_t( private_handle_t::PRIV_FLAGS_USES_ION | priv_heap_flag, usage, size, cpu_ptr,
	                                              lock_state, -1, 0);

	if ( NULL != hnd )
	{
		hnd->share_fd = shared_fd;
		hnd->min_pgsz = min_pgsz;
		*pHandle = hnd;
		return 0;
	}
	else
	{
		AERR( "Gralloc out of mem for ion_client:%d", m->ion_client );
	}

	close( shared_fd );

	if(!(usage & GRALLOC_USAGE_PROTECTED))
	{
		ret = munmap( cpu_ptr, size );
		if ( 0 != ret ) AERR( "munmap failed for base:%p size: %zd", cpu_ptr, size );
	}

	return -1;
}

int alloc_backend_alloc_framebuffer(private_module_t* m, private_handle_t* hnd)
{
	struct fb_dmabuf_export fb_dma_buf;
	int res;
	res = ioctl( m->framebuffer->shallow_fbdev_fd, FBIOGET_DMABUF, &fb_dma_buf );
	if(res == 0)
	{
		hnd->share_fd = fb_dma_buf.fd;
		return 0;
	}
	else
	{
		AINF("FBIOGET_DMABUF ioctl failed(%d). See gralloc_priv.h and the integration manual for vendor framebuffer integration", res);
		return -1;
	}
}

void alloc_backend_alloc_free(private_handle_t const* hnd, private_module_t* m)
{
	(void) m;
	if (hnd->flags & private_handle_t::PRIV_FLAGS_FRAMEBUFFER)
	{
		return;
	}
	else if ( hnd->flags & private_handle_t::PRIV_FLAGS_USES_ION )
	{
		/* Buffer might be unregistered already so we need to assure we have a valid handle*/
		if ( 0 != hnd->base )
		{
			if ( 0 != munmap( (void*)hnd->base, hnd->size ) ) AERR( "Failed to munmap handle %p", hnd );
		}
		close( hnd->share_fd );
		memset( (void*)hnd, 0, sizeof( *hnd ) );
	}
}

int alloc_backend_open(alloc_device_t *dev)
{
	private_module_t *m = reinterpret_cast<private_module_t *>(dev->common.module);
	m->ion_client = ion_open();
	if ( m->ion_client < 0 )
	{
		AERR( "ion_open failed with %s", strerror(errno) );
		return -1;
	}

	return 0;
}

int alloc_backend_close(struct hw_device_t *device)
{
	alloc_device_t* dev = reinterpret_cast<alloc_device_t*>(device);
	if (dev)
	{
		private_module_t *m = reinterpret_cast<private_module_t*>(dev->common.module);
		if ( 0 != ion_close(m->ion_client) ) AERR( "Failed to close ion_client: %d err=%s", m->ion_client , strerror(errno));
		delete dev;
	}
	return 0;
}
