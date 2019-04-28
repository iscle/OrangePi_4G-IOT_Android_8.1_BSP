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

#ifndef IMG_GRALLOC1_H
#define IMG_GRALLOC1_H

#include <hardware/gralloc1.h>

#include <stdlib.h>

#define GRALLOC1_FUNCTION_IMG_EXT_OFF 1000

enum
{
	GRALLOC1_FUNCTION_BLIT_HANDLE_TO_HANDLE_IMG =
		(GRALLOC1_FUNCTION_IMG_EXT_OFF + GRALLOC_BLIT_HANDLE_TO_HANDLE_IMG),
	GRALLOC1_FUNCTION_GET_BUFFER_CPU_ADDRESSES_IMG =
		(GRALLOC1_FUNCTION_IMG_EXT_OFF + GRALLOC_GET_BUFFER_CPU_ADDRESSES_IMG),
	GRALLOC1_FUNCTION_PUT_BUFFER_CPU_ADDRESSES_IMG =
		(GRALLOC1_FUNCTION_IMG_EXT_OFF + GRALLOC_PUT_BUFFER_CPU_ADDRESSES_IMG),
	GRALLOC1_FUNCTION_GET_DISPLAY_DEVICE_IMG =
		(GRALLOC1_FUNCTION_IMG_EXT_OFF + GRALLOC_GET_DISPLAY_DEVICE_IMG),
	GRALLOC1_FUNCTION_GET_DISPLAY_STATUS_IMG =
		(GRALLOC1_FUNCTION_IMG_EXT_OFF + GRALLOC_GET_DISPLAY_STATUS_IMG),
};

static inline int gralloc1_register_img
	(gralloc1_device_t *g, buffer_handle_t handle)
{
	GRALLOC1_PFN_RETAIN f =
		(GRALLOC1_PFN_RETAIN)
			g->getFunction(g, GRALLOC1_FUNCTION_RETAIN);
	int32_t err;

	err = f(g, handle);
	switch (err)
	{
		case GRALLOC1_ERROR_NO_RESOURCES:
			return -EAGAIN;
		case GRALLOC1_ERROR_NONE:
			return 0;
		default:
			return -EINVAL;
	}
}

static inline int gralloc1_unregister_img
	(gralloc1_device_t *g, buffer_handle_t handle)
{
	GRALLOC1_PFN_RELEASE f =
		(GRALLOC1_PFN_RELEASE)
			g->getFunction(g, GRALLOC1_FUNCTION_RELEASE);
	int32_t err;

	err = f(g, handle);
	switch (err)
	{
		case GRALLOC1_ERROR_NONE:
			return 0;
		default:
			return -EINVAL;
	}
}

static inline int gralloc1_device_alloc_img
	(gralloc1_device_t *d, int w, int h, int format, int usage,
	 buffer_handle_t *handle, int *stride)
{
	GRALLOC1_PFN_ALLOCATE allocate =
		(GRALLOC1_PFN_ALLOCATE)
			d->getFunction(d, GRALLOC1_FUNCTION_ALLOCATE);
	GRALLOC1_PFN_CREATE_DESCRIPTOR createDescriptor =
		(GRALLOC1_PFN_CREATE_DESCRIPTOR)
			d->getFunction(d, GRALLOC1_FUNCTION_CREATE_DESCRIPTOR);
	GRALLOC1_PFN_DESTROY_DESCRIPTOR destroyDescriptor =
		(GRALLOC1_PFN_DESTROY_DESCRIPTOR)
			d->getFunction(d, GRALLOC1_FUNCTION_DESTROY_DESCRIPTOR);
	GRALLOC1_PFN_SET_CONSUMER_USAGE setConsumerUsage =
		(GRALLOC1_PFN_SET_CONSUMER_USAGE)
			d->getFunction(d, GRALLOC1_FUNCTION_SET_CONSUMER_USAGE);
	GRALLOC1_PFN_SET_DIMENSIONS setDimensions =
		(GRALLOC1_PFN_SET_DIMENSIONS)
			d->getFunction(d, GRALLOC1_FUNCTION_SET_DIMENSIONS);
	GRALLOC1_PFN_SET_FORMAT setFormat =
		(GRALLOC1_PFN_SET_FORMAT)
			d->getFunction(d, GRALLOC1_FUNCTION_SET_FORMAT);
	GRALLOC1_PFN_SET_PRODUCER_USAGE setProducerUsage =
		(GRALLOC1_PFN_SET_PRODUCER_USAGE)
			d->getFunction(d, GRALLOC1_FUNCTION_SET_PRODUCER_USAGE);
	GRALLOC1_PFN_GET_STRIDE getStride =
		(GRALLOC1_PFN_GET_STRIDE)
			d->getFunction(d, GRALLOC1_FUNCTION_GET_STRIDE);
	uint64_t producerUsage =
		(usage & (GRALLOC1_PRODUCER_USAGE_CPU_READ_OFTEN    |
		          GRALLOC1_PRODUCER_USAGE_CPU_WRITE_OFTEN   |
		          GRALLOC1_PRODUCER_USAGE_GPU_RENDER_TARGET |
		          GRALLOC1_PRODUCER_USAGE_PROTECTED         |
		          GRALLOC1_PRODUCER_USAGE_CAMERA            |
		          GRALLOC1_PRODUCER_USAGE_VIDEO_DECODER));
	uint64_t consumerUsage =
		(usage & (GRALLOC1_CONSUMER_USAGE_CPU_READ_OFTEN    |
		          GRALLOC1_CONSUMER_USAGE_GPU_TEXTURE       |
		          GRALLOC1_CONSUMER_USAGE_HWCOMPOSER        |
		          GRALLOC1_CONSUMER_USAGE_CLIENT_TARGET     |
		          GRALLOC1_CONSUMER_USAGE_CURSOR            |
		          GRALLOC1_CONSUMER_USAGE_VIDEO_ENCODER     |
		          GRALLOC1_CONSUMER_USAGE_CAMERA            |
		          GRALLOC1_CONSUMER_USAGE_RENDERSCRIPT));
	gralloc1_buffer_descriptor_t descriptor;
	uint32_t stride32;
	int err = -EINVAL;
	int32_t err32;

	err32 = createDescriptor(d, &descriptor);
	if (err32 != GRALLOC1_ERROR_NONE)
		goto err_out;

	err32 = setDimensions(d, descriptor, w, h);
	if (err32 != GRALLOC1_ERROR_NONE)
		goto err_destroy_descriptor;

	err32 = setFormat(d, descriptor, format);
	if (err32 != GRALLOC1_ERROR_NONE)
		goto err_destroy_descriptor;

	err32 = setConsumerUsage(d, descriptor, consumerUsage);
	if (err32 != GRALLOC1_ERROR_NONE)
		goto err_destroy_descriptor;

	err32 = setProducerUsage(d, descriptor, producerUsage);
	if (err32 != GRALLOC1_ERROR_NONE)
		goto err_destroy_descriptor;

	err32 = allocate(d, 1, &descriptor, handle);
	switch (err32)
	{
		case GRALLOC1_ERROR_NOT_SHARED:
		case GRALLOC1_ERROR_NONE:
			break;
		case GRALLOC1_ERROR_NO_RESOURCES:
			err = -EAGAIN;
		default:
			goto err_destroy_descriptor;
	}

	err32 = getStride(d, *handle, &stride32);
	if (err32 != GRALLOC1_ERROR_NONE)
	{
		gralloc1_unregister_img(d, *handle);
		goto err_destroy_descriptor;
	}

	*stride = (int)stride32;
	err = 0;
err_destroy_descriptor:
	destroyDescriptor(d, descriptor);
err_out:
	return err;
}

static inline int gralloc1_device_free_img
	(gralloc1_device_t *d, buffer_handle_t handle)
{
	return gralloc1_unregister_img(d, handle);
}

static inline int gralloc1_lock_async_img
	(gralloc1_device_t *g, buffer_handle_t handle, int usage,
	 const gralloc1_rect_t *r, void **vaddr, int acquireFence)
{
	GRALLOC1_PFN_LOCK f =
		(GRALLOC1_PFN_LOCK)
			g->getFunction(g, GRALLOC1_FUNCTION_LOCK);
	uint64_t producerUsage =
		(usage & (GRALLOC1_PRODUCER_USAGE_CPU_READ_OFTEN |
		          GRALLOC1_PRODUCER_USAGE_CPU_WRITE_OFTEN));
	uint64_t consumerUsage =
		(usage &  GRALLOC1_CONSUMER_USAGE_CPU_READ_OFTEN);
	int32_t err;

	err = f(g, handle, producerUsage, consumerUsage, r, vaddr, acquireFence);
	switch (err)
	{
		case GRALLOC1_ERROR_NONE:
			return 0;
		case GRALLOC1_ERROR_NO_RESOURCES:
			return -EAGAIN;
		default:
			return -EINVAL;
	}
}

static inline int gralloc1_unlock_async_img
	(gralloc1_device_t *g, buffer_handle_t handle, int *releaseFence)
{
	GRALLOC1_PFN_UNLOCK f =
		(GRALLOC1_PFN_UNLOCK)
			g->getFunction(g, GRALLOC1_FUNCTION_UNLOCK);
	int32_t err, releaseFence32;

	err = f(g, handle, &releaseFence32);
	switch (err)
	{
		case GRALLOC1_ERROR_NONE:
			*releaseFence = releaseFence32;
			return 0;
		default:
			return -EINVAL;
	}
}

typedef int (*GRALLOC1_PFN_BLIT_HANDLE_TO_HANDLE_IMG)
	(gralloc1_device_t *g, buffer_handle_t src, buffer_handle_t dest,
	 int w, int h, int x, int y, int transform, int input_fence,
	 int *output_fence);

static inline int gralloc1_blit_handle_to_handle_img
	(gralloc1_device_t *g, buffer_handle_t src, buffer_handle_t dest,
	 int w, int h, int x, int y, int transform, int input_fence,
	 int *output_fence)
{
	GRALLOC1_PFN_BLIT_HANDLE_TO_HANDLE_IMG f =
		(GRALLOC1_PFN_BLIT_HANDLE_TO_HANDLE_IMG)
			g->getFunction(g, GRALLOC1_FUNCTION_BLIT_HANDLE_TO_HANDLE_IMG);

	return f(g, src, dest, w, h, x, y, transform, input_fence, output_fence);
}

typedef int (*GRALLOC1_PFN_GET_BUFFER_CPU_ADDRESSES_IMG)
	(gralloc1_device_t *g, buffer_handle_t handle, void **vaddrs,
	 size_t *sizes);

static inline int gralloc1_get_buffer_cpu_addresses_img
	(gralloc1_device_t *g, buffer_handle_t handle, void **vaddrs,
	 size_t *sizes)
{
	GRALLOC1_PFN_GET_BUFFER_CPU_ADDRESSES_IMG f =
		(GRALLOC1_PFN_GET_BUFFER_CPU_ADDRESSES_IMG)
			g->getFunction(g, GRALLOC1_FUNCTION_GET_BUFFER_CPU_ADDRESSES_IMG);

	return f(g, handle, vaddrs, sizes);
}

typedef int (*GRALLOC1_PFN_PUT_BUFFER_CPU_ADDRESSES_IMG)
	(gralloc1_device_t *g, buffer_handle_t handle);

static inline int gralloc1_put_buffer_cpu_addresses_img
	(gralloc1_device_t *g, buffer_handle_t handle)
{
	GRALLOC1_PFN_PUT_BUFFER_CPU_ADDRESSES_IMG f =
		(GRALLOC1_PFN_PUT_BUFFER_CPU_ADDRESSES_IMG)
			g->getFunction(g, GRALLOC1_FUNCTION_PUT_BUFFER_CPU_ADDRESSES_IMG);

	return f(g, handle);
}

typedef int (*GRALLOC1_PFN_GET_DISPLAY_DEVICE_IMG)
	(gralloc1_device_t *g, void **ppvDispDev);

static inline int gralloc1_get_display_device_img
	(gralloc1_device_t *g, void **ppvDispDev)
{
	GRALLOC1_PFN_GET_DISPLAY_DEVICE_IMG f =
		(GRALLOC1_PFN_GET_DISPLAY_DEVICE_IMG)
			g->getFunction(g, GRALLOC1_FUNCTION_GET_DISPLAY_DEVICE_IMG);

	return f(g, ppvDispDev);
}

typedef int (*GRALLOC1_PFN_GET_DISPLAY_STATUS_IMG)
	(gralloc1_device_t *g, buffer_handle_t handle, uint32_t *pui32Status);

static inline int gralloc1_get_display_status_img
	(gralloc1_device_t *g, buffer_handle_t handle, uint32_t *pui32Status)
{
	GRALLOC1_PFN_GET_DISPLAY_STATUS_IMG f =
		(GRALLOC1_PFN_GET_DISPLAY_STATUS_IMG)
			g->getFunction(g, GRALLOC1_FUNCTION_GET_DISPLAY_STATUS_IMG);

	return f(g, handle, pui32Status);
}

#endif /* IMG_GRALLOC1_H */
