/*
 * Copyright (C) 2013 ARM Limited. All rights reserved.
 *
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
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

#include <errno.h>
#include <pthread.h>

#include <cutils/log.h>
#include <cutils/atomic.h>
#include <hardware/hardware.h>
#include <hardware/gralloc.h>

#include "gralloc_priv.h"
#include "alloc_device.h"
#include "framebuffer_device.h"

#include <linux/ion.h>
#include <ion/ion.h>
#include <sys/mman.h>

int gralloc_backend_register(private_handle_t* hnd)
{
	int retval = -EINVAL;

	switch (hnd->flags & private_handle_t::PRIV_FLAGS_USES_ION)
	{
	case private_handle_t::PRIV_FLAGS_USES_ION:
		unsigned char *mappedAddress;
		size_t size = hnd->size;
		hw_module_t * pmodule = NULL;
		private_module_t *m=NULL;
		if (hw_get_module(GRALLOC_HARDWARE_MODULE_ID, (const hw_module_t **)&pmodule) == 0)
		{
			m = reinterpret_cast<private_module_t *>(pmodule);
		}
		else
		{
			AERR("Could not get gralloc module for handle: %p", hnd);
			retval = -errno;
			break;
		}
		/* the test condition is set to m->ion_client <= 0 here, because:
		 * 1) module structure are initialized to 0 if no initial value is applied
		 * 2) a second user process should get a ion fd greater than 0.
		 */
		if (m->ion_client <= 0)
		{
			/* a second user process must obtain a client handle first via ion_open before it can obtain the shared ion buffer*/	
			m->ion_client = ion_open();
			
			if (m->ion_client < 0) 
			{
				AERR( "Could not open ion device for handle: %p", hnd );
				retval = -errno;
				break;
			}
		}

		mappedAddress = (unsigned char*)mmap( NULL, size, PROT_READ | PROT_WRITE,
		                                      MAP_SHARED, hnd->share_fd, 0 );
		
		if ( MAP_FAILED == mappedAddress )
		{
			AERR( "mmap( share_fd:%d ) failed with %s",  hnd->share_fd, strerror( errno ) );
			retval = -errno;
			break;
		}
		
		hnd->base = (void*)(uintptr_t(mappedAddress) + hnd->offset);
		retval = 0;
		break;
	}

	return retval;
}

void gralloc_backend_unregister(private_handle_t* hnd)
{
	switch (hnd->flags & private_handle_t::PRIV_FLAGS_USES_ION)
	{
	case private_handle_t::PRIV_FLAGS_USES_ION:
		void* base = (void*)hnd->base;
		size_t size = hnd->size;

		if ( munmap( base,size ) < 0 )
		{
			AERR("Could not munmap base:%p size:%zd '%s'", base, size, strerror(errno));
		}
		break;
	}
}

void gralloc_backend_sync(private_handle_t* hnd)
{
	switch (hnd->flags & private_handle_t::PRIV_FLAGS_USES_ION)
	{
	case private_handle_t::PRIV_FLAGS_USES_ION:
		hw_module_t * pmodule = NULL;
		private_module_t *m=NULL;
		if (hw_get_module(GRALLOC_HARDWARE_MODULE_ID, (const hw_module_t **)&pmodule) == 0)
		{
			if(!(hnd->flags & private_handle_t::PRIV_FLAGS_USES_ION_DMA_HEAP))
			{
				m = reinterpret_cast<private_module_t *>(pmodule);
				ion_sync_fd(m->ion_client, hnd->share_fd);
			}
		}
		else
		{
			AERR("Could not get gralloc module for handle %p\n", hnd);
		}
		break;
	}
}
