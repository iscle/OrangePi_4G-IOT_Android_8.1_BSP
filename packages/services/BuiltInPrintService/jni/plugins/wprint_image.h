/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2016 Mopria Alliance, Inc.
 * Copyright (C) 2013 Hewlett-Packard Development Company, L.P.
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

#ifndef __WPRINT_IMAGE__
#define __WPRINT_IMAGE__

#include <stdio.h>
#include "mime_types.h"
#include "wprint_scaler.h"
#include "wprint_debug.h"
#include "ifc_wprint.h"

#ifdef __cplusplus
extern "C"
{
#endif

#define BYTES_PER_PIXEL(X)  ((X)*3)
#define BITS_PER_CHANNEL    8

/*
 * Rotations to apply while decoding
 */
typedef enum {
    ROT_0 = 0,
    ROT_90,
    ROT_180,
    ROT_270,
} wprint_rotation_t;

/*
 * Location(s) for padding to be applied while decoding
 */
typedef enum {
    PAD_TOP = (1 << 0),
    PAD_LEFT = (1 << 1),
    PAD_RIGHT = (1 << 2),
    PAD_BOTTOM = (1 << 3),
} wprint_padding_t;

#define PAD_NONE  (0)
#define PAD_PRINT (PAD_TOP | PAD_LEFT)
#define PAD_ALL   (PAD_TOP | PAD_LEFT | PAD_RIGHT | PAD_BOTTOM)

#define __DEFINE_WPRINT_PLATFORM_TYPES__

#include "wprint_image_platform.h"

#undef __DEFINE_WPRINT_PLATFORM_TYPES__

/*
 * Define an image which can be decoded into a stream
 */
typedef struct {
    // file information
    const char *mime_type;
    FILE *imgfile;

    // interfaces
    const struct _image_decode_ifc_st *decode_ifc;
    const ifc_wprint_t *wprint_ifc;

    // image dimensions
    unsigned int width;
    unsigned int height;
    unsigned int sampled_width;
    unsigned int sampled_height;

    // printable area information
    unsigned int printable_width;
    unsigned int printable_height;
    unsigned int print_resolution;
    unsigned int render_flags;

    // output information
    unsigned int output_width;
    unsigned int output_height;
    int num_components;
    int pdf_render_resolution;

    // memory optimization parameters
    unsigned int stripe_height;
    unsigned int concurrent_stripes;
    unsigned int output_rows;

    // scaling parameters
    unsigned int scaled_sample_size;
    unsigned int scaled_width;
    unsigned int scaled_height;
    int unscaled_start_row;
    int unscaled_end_row;
    unsigned int unscaled_rows_needed;
    unsigned char *unscaled_rows;
    unsigned int mixed_memory_needed;
    unsigned char *mixed_memory;
    unsigned char scaling_needed;
    scaler_config_t scaler_config;

    // padding parameters
    unsigned int output_padding_top;
    unsigned int output_padding_bottom;
    unsigned int output_padding_left;
    unsigned int output_padding_right;
    unsigned int padding_options;

    // decoding information
    wprint_rotation_t rotation;
    unsigned int row_offset;
    unsigned int col_offset;
    int swath_start;
    int rows_cached;
    unsigned char **output_cache;
    int output_swath_start;
    decoder_data_t decoder_data;
} wprint_image_info_t;

/*
 * Defines an interface for decoding images
 */
typedef struct _image_decode_ifc_st {
    /*
     * Prepare for decoding of the specified image
     */
    void (*init)(wprint_image_info_t *image_info);

    /*
     * Prepare for decoding of an image
     */
    status_t (*get_hdr)(wprint_image_info_t *image_info);

    /*
     * Supply image data at the specified row
     */
    unsigned char *(*decode_row)(wprint_image_info_t *, int row);

    /*
     * Release all resources related to the image
     */
    status_t (*cleanup)(wprint_image_info_t *image_info);

    /*
     * Return OK if subsampling is supported by this decoder
     */
    status_t (*supports_subsampling)(wprint_image_info_t *image_info);

    /*
     * Return resolution in DPI
     */
    int (*native_units)(wprint_image_info_t *image_info);
} image_decode_ifc_t;

/*
 * Return the appropriate decoding object corresponding to the image
 */
const image_decode_ifc_t *wprint_image_get_decode_ifc(wprint_image_info_t *image_info);

/*
 * Initializes image_info with supplied parameters
 */
void wprint_image_setup(wprint_image_info_t *image_info, const char *mime_type,
        const ifc_wprint_t *wprint_ifc, unsigned int output_resolution, int pdf_render_resolution);

/*
 * Open an initialized image from a file
 */
status_t wprint_image_get_info(FILE *imgfile, wprint_image_info_t *image_info);

/*
 * Configure image_info parameters as supplied
 */
status_t wprint_image_set_output_properties(wprint_image_info_t *image_info,
        wprint_rotation_t rotation, unsigned int printable_width, unsigned int printable_height,
        unsigned int top_margin, unsigned int left_margin, unsigned int right_margin,
        unsigned int bottom_margin, unsigned int render_flags, unsigned int max_decode_stripe,
        unsigned int concurrent_stripes, unsigned int padding_options);

/*
 * Return true if the image is wider than it is high (landscape orientation)
 */
bool wprint_image_is_landscape(wprint_image_info_t *image_info);

/*
 * Return the size required to render the image
 */
int wprint_image_get_output_buff_size(wprint_image_info_t *image_info);

/*
 * Return the full image width, including any padding
 */
int wprint_image_get_width(wprint_image_info_t *image_info);

/*
 * Return the full image height, including any padding
 */
int wprint_image_get_height(wprint_image_info_t *image_info);

/*
 * Decode a single stripe of data into rgb_pixels, storing height rendered and returning
 * bytes processed or 0/negative on error.
 */
int wprint_image_decode_stripe(wprint_image_info_t *image_info, int start_row, int *height,
        unsigned char *rgb_pixels);

/*
 * Compute and allocate memory in preparation for decoding row data, returning the number of rows
 */
int wprint_image_compute_rows_to_cache(wprint_image_info_t *image_info);

/*
 * Return the current number of cached rows
 */
int wprint_image_input_rows_cached(wprint_image_info_t *image_info);

/*
 * Free all image resources
 */
void wprint_image_cleanup(wprint_image_info_t *image_info);

#ifdef __cplusplus
}
#endif

#define __DEFINE_WPRINT_PLATFORM_METHODS__

#include "wprint_image_platform.h"

#undef __DEFINE_WPRINT_PLATFORM_METHODS__

#endif // __WPRINT_IMAGE__