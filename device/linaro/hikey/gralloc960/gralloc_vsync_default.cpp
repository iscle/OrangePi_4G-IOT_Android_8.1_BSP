/*
 * Copyright (C) 2014 ARM Limited. All rights reserved.
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

#include "gralloc_priv.h"
#include "gralloc_vsync.h"
#include "gralloc_vsync_report.h"
#include <sys/ioctl.h>
#include <errno.h>

#define FBIO_WAITFORVSYNC       _IOW('F', 0x20, __u32)

int gralloc_vsync_enable(framebuffer_device_t *dev)
{
	GRALLOC_UNUSED(dev);
	return 0;
}

int gralloc_vsync_disable(framebuffer_device_t *dev)
{
	GRALLOC_UNUSED(dev);
	return 0;
}

int gralloc_wait_for_vsync(framebuffer_device_t *dev)
{
	private_module_t* m = reinterpret_cast<private_module_t*>(dev->common.module);
	if (MALI_DPY_TYPE_CLCD == m->dpy_type || MALI_DPY_TYPE_HDLCD == m->dpy_type)
	{
		/* Silently ignore wait for vsync as neither PL111 nor HDLCD implement this IOCTL. */
		return 0;
	}

	if ( m->swapInterval )
	{
		int crtc = 0;
		gralloc_mali_vsync_report(MALI_VSYNC_EVENT_BEGIN_WAIT);
		if(ioctl(m->framebuffer->shallow_fbdev_fd, FBIO_WAITFORVSYNC, &crtc) < 0) 
		{
			gralloc_mali_vsync_report(MALI_VSYNC_EVENT_END_WAIT);
			return -errno;
		}
		gralloc_mali_vsync_report(MALI_VSYNC_EVENT_END_WAIT);
	}
	return 0;
}
