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

#include <stdio.h>
#include "common_defines.h"
#include <wprint_debug.h>

extern "C"
{
#include <jpeglib.h>
}

#define TAG "genJPEGStrips"

/*
 * Function for setting up the buffer (which we already did)
 */
static void init_buffer(jpeg_compress_struct *) {
}

/*
 * Function for handling buffer overlow (should not happen because we allocated a large
 * buffer)
 */
static boolean empty_buffer(jpeg_compress_struct *) {
    return TRUE;
}

/*
 * Function for finalizing the buffer (which we do not need to do)
 */
static void term_buffer(jpeg_compress_struct *) {
}

GLOBAL(void)
write_JPEG_Buff(ubyte *buffPtr, int quality, int image_width, int image_height,
        JSAMPLE *imageBuffer, int resolution, colorSpaceDisposition destCS, int *numCompBytes) {
    struct jpeg_error_mgr jerr;

    // Step 1: allocate and initialize JPEG compression object
    struct jpeg_compress_struct cinfo = {
            .client_data = NULL, .err = jpeg_std_error(&jerr)
    };

    // Now we can initialize the JPEG compression object.
    jpeg_create_compress(&cinfo);

    // Step 2: specify data destination (we will use a memory buffer)
    struct jpeg_destination_mgr dm = {
            .init_destination = init_buffer, .empty_output_buffer = empty_buffer,
            .term_destination = term_buffer, .next_output_byte = buffPtr,
            .free_in_buffer = (size_t) image_width * image_height * 3
    };
    cinfo.dest = &dm;

    // Step 3: set parameters for compression

    cinfo.image_width = (JDIMENSION) image_width;
    cinfo.image_height = (JDIMENSION) image_height;
    if (destCS == deviceRGB || destCS == adobeRGB) {
        cinfo.in_color_space = JCS_RGB;
        cinfo.jpeg_color_space = JCS_RGB;
        cinfo.input_components = 3;
    } else {
        cinfo.in_color_space = JCS_GRAYSCALE;
        cinfo.jpeg_color_space = JCS_GRAYSCALE;
        cinfo.input_components = 1;
    }

    jpeg_set_defaults(&cinfo);

    /* Now you can set any non-default parameters you wish to.
     * Here we just illustrate the use of quality (quantization table) scaling:
     */
    jpeg_set_quality(&cinfo, quality, TRUE); // TRUE = limit to baseline-JPEG values

    // Set the density so that the JFIF header has the correct settings
    cinfo.density_unit = 1;      // 1=dots-per-inch, 2=dots per cm
    cinfo.X_density = (UINT16) resolution;
    cinfo.Y_density = (UINT16) resolution;

    // set the rows/columns setting to reflect the resolution
    // MCU = Minimum Coded Unit
    cinfo.MCUs_per_row = (JDIMENSION) image_width;
    cinfo.MCU_rows_in_scan = (JDIMENSION) image_height;

    // Step 4: Start compressor
    jpeg_start_compress(&cinfo, TRUE);

    // Step 5: Write scanlines

    int row_stride; // physical row width in image buffer
    row_stride = image_width * cinfo.input_components; // JSAMPLEs per row in imageBuffer

    JSAMPROW row_pointer[1]; // pointer to JSAMPLE row[s]
    while (cinfo.next_scanline < cinfo.image_height) {
        row_pointer[0] = &imageBuffer[cinfo.next_scanline * row_stride];
        (void) jpeg_write_scanlines(&cinfo, row_pointer, 1);
    }

    // Step 6: Finish compression

    jpeg_finish_compress(&cinfo);

    // Step 7: release JPEG compression object

    jpeg_destroy_compress(&cinfo);

    *numCompBytes = (int) (cinfo.dest->next_output_byte - buffPtr);

    LOGD("write_JPEG_Buff: w=%d, h=%d, r=%d, q=%d compressed to %d", image_width, image_height,
            resolution, quality, *numCompBytes);
}