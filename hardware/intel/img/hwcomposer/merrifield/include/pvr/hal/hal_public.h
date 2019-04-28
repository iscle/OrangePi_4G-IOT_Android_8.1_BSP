/* Copyright (c) Imagination Technologies Ltd.
 *
 * The contents of this file are subject to the MIT license as set out below.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

#ifndef HAL_PUBLIC_H
#define HAL_PUBLIC_H

#define PVR_ANDROID_HAS_SET_BUFFERS_DATASPACE
#define PVR_ANDROID_HAS_SET_BUFFERS_DATASPACE_2

#include "img_gralloc_common_public.h"

/* Extension pixel formats used by Intel components */

#undef  HAL_PIXEL_FORMAT_NV12

#define HAL_PIXEL_FORMAT_UYVY                 0x107
#define HAL_PIXEL_FORMAT_INTEL_YV12           0x108
#define HAL_PIXEL_FORMAT_INTEL_ZSL            0x109
#define HAL_PIXEL_FORMAT_NV12                 0x3231564E
#define HAL_PIXEL_FORMAT_NV21                 0x3132564E
#define HAL_PIXEL_FORMAT_I420                 0x30323449
#define HAL_PIXEL_FORMAT_YUY2                 0x32595559
#define HAL_PIXEL_FORMAT_NV12_VED             0x7FA00E00
#define HAL_PIXEL_FORMAT_NV12_VEDT            0x7FA00F00

/* Extension API used by Intel components */

#define GRALLOC_GET_BUFFER_CPU_ADDRESSES_IMG  108
#define GRALLOC_PUT_BUFFER_CPU_ADDRESSES_IMG  109

#define GRALLOC_GET_DISPLAY_DEVICE_IMG        1000
#define GRALLOC_GET_DISPLAY_STATUS_IMG        1001

#include "img_gralloc.h"
#include "img_gralloc1.h"

typedef const gralloc_module_t gralloc0_t;
typedef gralloc1_device_t      gralloc1_t;

static inline int gralloc_is_v1_img(const hw_module_t *m)
{
	return ((m->module_api_version >> 8) & 0xff) == 1;
}

static inline int gralloc_open_img(const hw_device_t **d)
{
	const hw_module_t *m;
	int err;

	err = hw_get_module(GRALLOC_HARDWARE_MODULE_ID, &m);
	if (err)
		return err;

	if (gralloc_is_v1_img(m))
		return gralloc1_open(m, (gralloc1_t **)d);
	else
		return gralloc_open(m, (alloc_device_t **)d);
}

static inline int gralloc_close_img(const hw_device_t *d)
{
	if (gralloc_is_v1_img(d->module))
		return gralloc1_close((gralloc1_t *)d);
	else
		return gralloc_close((alloc_device_t *)d);
}

static inline int gralloc_register_img
	(const hw_device_t *d, buffer_handle_t handle)
{
	if (gralloc_is_v1_img(d->module))
		return gralloc1_register_img((gralloc1_t *)d, handle);
	else
		return gralloc0_register_img((gralloc0_t *)d->module, handle);
}

static inline int gralloc_unregister_img
	(const hw_device_t *d, buffer_handle_t handle)
{
	if (gralloc_is_v1_img(d->module))
		return gralloc1_unregister_img((gralloc1_t *)d, handle);
	else
		return gralloc0_unregister_img((gralloc0_t *)d->module, handle);
}

static inline int gralloc_device_alloc_img
	(const hw_device_t *d, int w, int h, int format, int usage,
	 buffer_handle_t *handle, int *stride)
{
	if (gralloc_is_v1_img(d->module)) {
		usage = (usage | ((usage & 0x33) << 1)) & ~0x11;
		return gralloc1_device_alloc_img((gralloc1_t *)d, w, h, format,
										 usage, handle, stride);
	} else
		return gralloc0_device_alloc_img((alloc_device_t *)d, w, h, format,
										 usage, handle, stride);
}

static inline int gralloc_device_free_img
	(const hw_device_t *d, buffer_handle_t handle)
{
	if (gralloc_is_v1_img(d->module))
		return gralloc1_device_free_img((gralloc1_t *)d, handle);
	else
		return gralloc0_device_free_img((alloc_device_t *)d, handle);
}

static inline int gralloc_lock_async_img
	(const hw_device_t *d, buffer_handle_t handle, int usage,
	 const gralloc1_rect_t *r, void **vaddr, int acquireFence)
{
	if (gralloc_is_v1_img(d->module)) {
		usage = (usage | ((usage & 0x33) << 1)) & ~0x11;
		return gralloc1_lock_async_img((gralloc1_t *)d,
									   handle, usage, r, vaddr, acquireFence);
	} else
		return gralloc0_lock_async_img((gralloc0_t *)d->module,
									   handle, usage, r, vaddr, acquireFence);
}

static inline int gralloc_unlock_async_img
	(const hw_device_t *d, buffer_handle_t handle, int *releaseFence)
{
	if (gralloc_is_v1_img(d->module))
		return gralloc1_unlock_async_img((gralloc1_t *)d,
										 handle, releaseFence);
	else
		return gralloc0_unlock_async_img((gralloc0_t *)d->module,
										 handle, releaseFence);
}

static inline int gralloc_blit_handle_to_handle_img
	(const hw_device_t *d, buffer_handle_t src, buffer_handle_t dest,
	 int w, int h, int x, int y, int transform, int input_fence,
	 int *output_fence)
{
	if (gralloc_is_v1_img(d->module))
		return gralloc1_blit_handle_to_handle_img((gralloc1_t *)d,
												  src, dest, w, h, x, y,
												  transform, input_fence,
												  output_fence);
	else
		return gralloc0_blit_handle_to_handle_img((gralloc0_t *)d->module,
												  src, dest, w, h, x, y,
												  transform, input_fence,
												  output_fence);
}


static inline int gralloc_get_buffer_cpu_addresses_img
	(const hw_device_t *d, buffer_handle_t handle, void **vaddrs,
	 size_t *sizes)
{
	if (gralloc_is_v1_img(d->module))
		return gralloc1_get_buffer_cpu_addresses_img((gralloc1_t *)d,
													 handle, vaddrs, sizes);
	else
		return gralloc0_get_buffer_cpu_addresses_img((gralloc0_t *)d->module,
													 handle, vaddrs, sizes);
}

static inline int gralloc_put_buffer_cpu_addresses_img
	(const hw_device_t *d, buffer_handle_t handle)
{
	if (gralloc_is_v1_img(d->module))
		return gralloc1_put_buffer_cpu_addresses_img((gralloc1_t *)d,
													 handle);
	else
		return gralloc0_put_buffer_cpu_addresses_img((gralloc0_t *)d->module,
													 handle);
}

static inline int gralloc_get_display_device_img
	(const hw_device_t *d, void **ppvDispDev)
{
	if (gralloc_is_v1_img(d->module))
		return gralloc1_get_display_device_img((gralloc1_t *)d,
											   ppvDispDev);
	else
		return gralloc0_get_display_device_img((gralloc0_t *)d->module,
											   ppvDispDev);
}

static inline int gralloc_get_display_status_img
	(const hw_device_t *d, buffer_handle_t handle, uint32_t *pui32Status)
{
	if (gralloc_is_v1_img(d->module))
		return gralloc1_get_display_status_img((gralloc1_t *)d,
											   handle, pui32Status);
	else
		return gralloc0_get_display_status_img((gralloc0_t *)d->module,
											   handle, pui32Status);
}

#endif /* HAL_PUBLIC_H */
