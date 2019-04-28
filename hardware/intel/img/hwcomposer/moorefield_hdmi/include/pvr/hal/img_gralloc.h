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

#ifndef IMG_GRALLOC_H
#define IMG_GRALLOC_H

#include <hardware/gralloc.h>

/* for gralloc1_rect_t */
#include <hardware/gralloc1.h>

static inline int gralloc0_register_img
	(const gralloc_module_t *g, buffer_handle_t handle)
{
	return g->registerBuffer(g, handle);
}

static inline int gralloc0_unregister_img
	(const gralloc_module_t *g, buffer_handle_t handle)
{
	return g->unregisterBuffer(g, handle);
}

static inline int gralloc0_device_alloc_img
	(alloc_device_t *d, int w, int h, int format, int usage,
	 buffer_handle_t *handle, int *stride)
{
	return d->alloc(d, w, h, format, usage, handle, stride);
}

static inline int gralloc0_device_free_img
	(alloc_device_t *d, buffer_handle_t handle)
{
	return d->free(d, handle);
}

static inline int gralloc0_lock_async_img
	(const gralloc_module_t *g, buffer_handle_t handle, int usage,
	 const gralloc1_rect_t *r, void **vaddr, int acquireFence)
{
	return g->lockAsync(g, handle, usage,
						r->left, r->top, r->width, r->height,
						vaddr, acquireFence);
}

static inline int gralloc0_unlock_async_img
	(const gralloc_module_t *g, buffer_handle_t handle, int *releaseFence)
{
	return g->unlockAsync(g, handle, releaseFence);
}

static inline int gralloc0_blit_handle_to_handle_img
	(const gralloc_module_t *g, buffer_handle_t src, buffer_handle_t dest,
	 int w, int h, int x, int y, int transform, int input_fence,
	 int *output_fence)
{
	return g->perform(g, GRALLOC_BLIT_HANDLE_TO_HANDLE_IMG, src, dest, w, h,
					  x, y, transform, input_fence, output_fence);
}

static inline int gralloc0_get_buffer_cpu_addresses_img
	(const gralloc_module_t *g, buffer_handle_t handle, void **vaddrs,
	 size_t *sizes)
{
	return g->perform(g, GRALLOC_GET_BUFFER_CPU_ADDRESSES_IMG, handle, vaddrs,
					  sizes);
}

static inline int gralloc0_put_buffer_cpu_addresses_img
	(const gralloc_module_t *g, buffer_handle_t handle)
{
	return g->perform(g, GRALLOC_PUT_BUFFER_CPU_ADDRESSES_IMG, handle);
}

static inline int gralloc0_get_display_device_img
	(const gralloc_module_t *g, void **ppvDispDev)
{
	return g->perform(g, GRALLOC_GET_DISPLAY_DEVICE_IMG, ppvDispDev);
}

static inline int gralloc0_get_display_status_img
	(const gralloc_module_t *g, buffer_handle_t handle, uint32_t *pui32Status)
{
	return g->perform(g, GRALLOC_GET_DISPLAY_STATUS_IMG, handle, pui32Status);
}

#endif /* IMG_GRALLOC_H */
