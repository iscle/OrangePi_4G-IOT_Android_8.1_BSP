/*
 * Copyright (C) 2016 ARM Limited. All rights reserved.
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

#ifndef MALI_GRALLOC_FORMATS_H_
#define MALI_GRALLOC_FORMATS_H_

#include <system/graphics.h>

/* Internal formats are represented in gralloc as a 64bit identifier
 * where the 32 lower bits are a base format and the 32 upper bits are modifiers.
 *
 * Modifier bits are divided into mutually exclusive ones and those that are not.
 */
/* Internal format type */
typedef uint64_t mali_gralloc_internal_format;

/* Internal format masks */
#define    MALI_GRALLOC_INTFMT_FMT_MASK             0x00000000ffffffffULL
#define    MALI_GRALLOC_INTFMT_EXT_MASK             0xffffffff00000000ULL
#define    MALI_GRALLOC_INTFMT_ME_EXT_MASK          0x0000ffff00000000ULL
#define    MALI_GRALLOC_INTFMT_REG_EXT_MASK         0xffff000000000000ULL

/* Internal base formats */

/* Base formats that do not have an identical HAL match
 * are defined starting at the Android private range
 */
#define MALI_GRALLOC_FORMAT_INTERNAL_RANGE_BASE 0x100

typedef enum
{
    /* Internal definitions for HAL formats. */
    MALI_GRALLOC_FORMAT_INTERNAL_RGBA_8888 = HAL_PIXEL_FORMAT_RGBA_8888,
    MALI_GRALLOC_FORMAT_INTERNAL_RGBX_8888 = HAL_PIXEL_FORMAT_RGBX_8888,
    MALI_GRALLOC_FORMAT_INTERNAL_RGB_888 = HAL_PIXEL_FORMAT_RGB_888,
    MALI_GRALLOC_FORMAT_INTERNAL_RGB_565 = HAL_PIXEL_FORMAT_RGB_565 ,
    MALI_GRALLOC_FORMAT_INTERNAL_BGRA_8888 = HAL_PIXEL_FORMAT_BGRA_8888,
    MALI_GRALLOC_FORMAT_INTERNAL_YV12 = HAL_PIXEL_FORMAT_YV12 ,
    MALI_GRALLOC_FORMAT_INTERNAL_Y8 = HAL_PIXEL_FORMAT_Y8,
    MALI_GRALLOC_FORMAT_INTERNAL_Y16 = HAL_PIXEL_FORMAT_Y16,
    MALI_GRALLOC_FORMAT_INTERNAL_YUV420_888 = HAL_PIXEL_FORMAT_YCbCr_420_888,

    /* Camera specific HAL formats */
    MALI_GRALLOC_FORMAT_INTERNAL_RAW16 = HAL_PIXEL_FORMAT_RAW16,
    MALI_GRALLOC_FORMAT_INTERNAL_RAW12 = HAL_PIXEL_FORMAT_RAW12,
    MALI_GRALLOC_FORMAT_INTERNAL_RAW10 = HAL_PIXEL_FORMAT_RAW10,
    MALI_GRALLOC_FORMAT_INTERNAL_BLOB = HAL_PIXEL_FORMAT_BLOB,

    /* Flexible YUV formats would be parsed but not have any representation as
     * internal format itself but one of the ones below
     */

    /* The internal private formats that have no HAL equivivalent are defined
     * afterwards starting at a specific base range */
    MALI_GRALLOC_FORMAT_INTERNAL_NV12 = MALI_GRALLOC_FORMAT_INTERNAL_RANGE_BASE,
    MALI_GRALLOC_FORMAT_INTERNAL_NV21,
    MALI_GRALLOC_FORMAT_INTERNAL_YUV422_8BIT,

    /* Extended YUV formats
     *
     * NOTE: P010, P210, and Y410 are only supported uncompressed.
     */
    MALI_GRALLOC_FORMAT_INTERNAL_Y0L2,
    MALI_GRALLOC_FORMAT_INTERNAL_P010,
    MALI_GRALLOC_FORMAT_INTERNAL_P210,
    MALI_GRALLOC_FORMAT_INTERNAL_Y210,
    MALI_GRALLOC_FORMAT_INTERNAL_Y410,

    /* Add more internal formats here. Make sure decode_internal_format() is updated. */

    /* These are legacy 0.3 gralloc formats used only by the wrap/unwrap macros. */
    MALI_GRALLOC_FORMAT_INTERNAL_YV12_WRAP,
    MALI_GRALLOC_FORMAT_INTERNAL_Y8_WRAP,
    MALI_GRALLOC_FORMAT_INTERNAL_Y16_WRAP,

	MALI_GRALLOC_FORMAT_INTERNAL_RANGE_LAST,
} mali_gralloc_pixel_format;


/* Format Modifier Bits Locations */
#define MALI_GRALLOC_INTFMT_ME_EXTENSION_BIT_START             32
#define MALI_GRALLOC_INTFMT_EXTENSION_BIT_START                (MALI_GRALLOC_INTFMT_ME_EXTENSION_BIT_START+16)

/* Mutually Exclusive Modifier Bits */

/* This format will use AFBC */
#define    MALI_GRALLOC_INTFMT_AFBC_BASIC                 (1ULL << (MALI_GRALLOC_INTFMT_ME_EXTENSION_BIT_START+0))

/* This format uses AFBC split block mode */
#define    MALI_GRALLOC_INTFMT_AFBC_SPLITBLK        (1ULL << (MALI_GRALLOC_INTFMT_ME_EXTENSION_BIT_START+1))

#define    MALI_GRALLOC_INTFMT_UNUSED               (1ULL << (MALI_GRALLOC_INTFMT_ME_EXTENSION_BIT_START+2))

/* This format uses AFBC wide block mode */
#define    MALI_GRALLOC_INTFMT_AFBC_WIDEBLK         (1ULL << (MALI_GRALLOC_INTFMT_ME_EXTENSION_BIT_START+3))


/* Regular Modifier Bits */
#define    MALI_GRALLOC_INTFMT_AFBC_TILED_HEADERS   (1ULL << (MALI_GRALLOC_INTFMT_EXTENSION_BIT_START+0))


/* This mask should be used to check or clear support for AFBC for an internal format
 * These bits are mutually exclusive so this mask should not be used to enable support
 */
#define    MALI_GRALLOC_INTFMT_AFBCENABLE_MASK                   ((uint64_t) (MALI_GRALLOC_INTFMT_AFBC_BASIC | \
                                                                   MALI_GRALLOC_INTFMT_AFBC_SPLITBLK | \
                                                                   MALI_GRALLOC_INTFMT_AFBC_WIDEBLK))

/* Prototypes */
uint64_t mali_gralloc_select_format(int req_format,int usage, int buffer_size);

/* These are legacy Gralloc 0.3 support macros for passing private formats through the 0.3 alloc interface.
 * It packs modifier bits together with base format into a 32 bit format identifier.
 * Gralloc 1.0 interface should use private functions to set private buffer format in the buffer descriptor.
 *
 * Packing:
 *
 * Bits 15-0:    mali_gralloc_pixel_format format
 * Bits 23-16:   mutually exclusive modifier bits
 * Bits 31-24:   regular modifier bits
 */
static inline int mali_gralloc_format_wrapper(int format, int modifiers)
{
    /* Internal formats that are identical to HAL formats
     * have the same definition. This is convenient for
     * client parsing code to not have to parse them separately.
     *
     * For 3 of the HAL YUV formats that have very large definitions
     * this causes problems for packing in modifier bits.
     * Because of this reason we redefine these three formats
     * while packing/unpacking them.
     */
    if(format == MALI_GRALLOC_FORMAT_INTERNAL_YV12)
    {
        format = MALI_GRALLOC_FORMAT_INTERNAL_YV12_WRAP;
    }
    else if(format == MALI_GRALLOC_FORMAT_INTERNAL_Y8)
    {
        format = MALI_GRALLOC_FORMAT_INTERNAL_Y8_WRAP;
    }
    else if(format == MALI_GRALLOC_FORMAT_INTERNAL_Y16)
    {
        format = MALI_GRALLOC_FORMAT_INTERNAL_Y16_WRAP;
    }
    return (modifiers | format);
}

static inline uint64_t mali_gralloc_format_unwrap(int x)
{
    uint64_t internal_format = (uint64_t) (    ((((uint64_t)(x)) & 0xff000000) << 24) | // Regular modifier bits
                                               ((((uint64_t)(x)) & 0x00ff0000) << 16) | // Mutually exclusive modifier bits
                                                (((uint64_t)(x)) & 0x0000ffff)   );     // Private format

    uint64_t base_format = internal_format & MALI_GRALLOC_INTFMT_FMT_MASK;
    uint64_t modifiers = internal_format & MALI_GRALLOC_INTFMT_EXT_MASK;

    if(base_format == MALI_GRALLOC_FORMAT_INTERNAL_YV12_WRAP)
    {
        base_format = MALI_GRALLOC_FORMAT_INTERNAL_YV12;
    }
    else if(base_format == MALI_GRALLOC_FORMAT_INTERNAL_Y8_WRAP)
    {
        base_format = MALI_GRALLOC_FORMAT_INTERNAL_Y8;
    }
    else if(base_format == MALI_GRALLOC_FORMAT_INTERNAL_Y16_WRAP)
    {
        base_format = MALI_GRALLOC_FORMAT_INTERNAL_Y16;
    }
    return (modifiers | base_format);
}

#define    GRALLOC_PRIVATE_FORMAT_WRAPPER(x)                           ( mali_gralloc_format_wrapper(x, 0) )
#define    GRALLOC_PRIVATE_FORMAT_WRAPPER_AFBC(x)                      ( mali_gralloc_format_wrapper(x, (MALI_GRALLOC_INTFMT_AFBC_BASIC >> 16)) )
#define    GRALLOC_PRIVATE_FORMAT_WRAPPER_AFBC_SPLITBLK(x)             ( mali_gralloc_format_wrapper(x, (MALI_GRALLOC_INTFMT_AFBC_SPLITBLK >> 16)) )
#define    GRALLOC_PRIVATE_FORMAT_WRAPPER_AFBC_WIDEBLK(x)              ( mali_gralloc_format_wrapper(x, (MALI_GRALLOC_INTFMT_AFBC_WIDEBLK >> 16)) )
#define    GRALLOC_PRIVATE_FORMAT_WRAPPER_AFBC_TILED_HEADERS_BASIC(x)  ( mali_gralloc_format_wrapper(x, (MALI_GRALLOC_INTFMT_AFBC_TILED_HEADERS >> 24) | \
                                                                                                        (MALI_GRALLOC_INTFMT_AFBC_BASIC >> 16)))
#define    GRALLOC_PRIVATE_FORMAT_WRAPPER_AFBC_TILED_HEADERS_WIDE(x)   ( mali_gralloc_format_wrapper(x, (MALI_GRALLOC_INTFMT_AFBC_TILED_HEADERS >> 24) | \
                                                                                                        (MALI_GRALLOC_INTFMT_AFBC_WIDEBLK >> 16)))
#define    GRALLOC_PRIVATE_FORMAT_UNWRAP(x)                            mali_gralloc_format_unwrap(x)

/* IP block capability masks */
#define MALI_GRALLOC_FORMAT_CAPABILITY_OPTIONS_PRESENT            ((uint64_t) (1 << 0))
#define MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_BASIC                 ((uint64_t) (1 << 1))
#define MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_SPLITBLK              ((uint64_t) (1 << 2))
#define MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_WIDEBLK               ((uint64_t) (1 << 3))
#define MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_WIDEBLK_YUV_DISABLE   ((uint64_t) (1 << 4))
#define MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_YUV_NOREAD            ((uint64_t) (1 << 5))
#define MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_YUV_NOWRITE           ((uint64_t) (1 << 6))
#define MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_TILED_HEADERS         ((uint64_t) (1 << 7))

#define MALI_GRALLOC_FORMAT_CAPABILITY_AFBCENABLE_MASK ((uint64_t) (MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_BASIC | \
                                                                    MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_SPLITBLK | \
                                                                    MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_WIDEBLK | \
                                                                    MALI_GRALLOC_FORMAT_CAPABILITY_AFBC_TILED_HEADERS))

struct mali_gralloc_format_caps
{
	uint64_t caps_mask;
};
typedef struct mali_gralloc_format_caps mali_gralloc_format_caps;

#define MALI_GRALLOC_FORMATCAPS_SYM_NAME        mali_gralloc_format_capabilities
#define MALI_GRALLOC_FORMATCAPS_SYM_NAME_STR    "mali_gralloc_format_capabilities"

/* Producer and Consumer definitions */
typedef enum
{
	MALI_GRALLOC_PRODUCER_VIDEO_DECODER,
	MALI_GRALLOC_PRODUCER_GPU,
	MALI_GRALLOC_PRODUCER_CAMERA,
} mali_gralloc_producer_type;

typedef enum
{

    /* For surface composition in SurfaceFlinger a producer
     * will not know what consumer will process a buffer.
     *
     * MALI_GRALLOC_CONSUMER_GPU_OR_DISPLAY means the GPU
     * MUST support the given format but it should be allocated
     * with preference to the DPU.
     */
    MALI_GRALLOC_CONSUMER_GPU_OR_DISPLAY,
    MALI_GRALLOC_CONSUMER_VIDEO_ENCODER,


    /* This is used when no known "premium" dpu is configured.
     * For example, HDLCD/CLCD would be such a dpu.
     */
    MALI_GRALLOC_CONSUMER_GPU_EXCL,
} mali_gralloc_consumer_type;


/*
 * Below usage types overlap, this is intentional.
 * The reason is that for Gralloc 0.3 there are very
 * few usage flags we have at our disposal.
 *
 * The overlapping is handled by processing the definitions
 * in a specific order.
 *
 * MALI_GRALLOC_USAGE_PRIVATE_FORMAT and MALI_GRALLOC_USAGE_NO_AFBC
 * don't overlap and are processed first.
 *
 * MALI_GRALLOC_USAGE_YUV_CONF are only for YUV formats and clients
 * using MALI_GRALLOC_USAGE_NO_AFBC must never allocate YUV formats.
 * The latter is strictly enforced and allocations will fail.
 *
 * MALI_GRALLOC_USAGE_AFBC_PADDING is only valid if MALI_GRALLOC_USAGE_NO_AFBC
 * is not present.
 */
typedef enum
{
	/* The client has specified a private format in the format parameter */
	MALI_GRALLOC_USAGE_PRIVATE_FORMAT = (int) GRALLOC_USAGE_PRIVATE_3,

	/* Buffer won't be allocated as AFBC */
	MALI_GRALLOC_USAGE_NO_AFBC = (int) (GRALLOC_USAGE_PRIVATE_1 | GRALLOC_USAGE_PRIVATE_2),

	/* Valid only for YUV allocations */
	MALI_GRALLOC_USAGE_YUV_CONF_0 = 0,
	MALI_GRALLOC_USAGE_YUV_CONF_1 = (int) GRALLOC_USAGE_PRIVATE_1,
	MALI_GRALLOC_USAGE_YUV_CONF_2 = (int) GRALLOC_USAGE_PRIVATE_0,
	MALI_GRALLOC_USAGE_YUV_CONF_3 = (int) (GRALLOC_USAGE_PRIVATE_0 | GRALLOC_USAGE_PRIVATE_1),
	MALI_GRALLOC_USAGE_YUV_CONF_MASK = MALI_GRALLOC_USAGE_YUV_CONF_3,

	/* A very specific alignment is requested on some buffers */
	MALI_GRALLOC_USAGE_AFBC_PADDING = GRALLOC_USAGE_PRIVATE_2,

} mali_gralloc_usage_type;

/* Prototypes */
uint64_t mali_gralloc_select_format(int req_format,int usage, int buffer_size);

#ifdef __cplusplus
extern "C"
{
#endif

void mali_gralloc_get_gpu_caps(struct mali_gralloc_format_caps *gpu_caps);

#ifdef __cplusplus
}
#endif

#endif /* MALI_GRALLOC_FORMATS_H_ */
