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

#include <time.h>
#include "wprint_mupdf.h"
#include "lib_wprint.h"
#include "ipphelper.h"

#define TAG "wprint_mupdf"
#define MUPDF_DEFAULT_RESOLUTION 72
#define RGB_NUMBER_PIXELS_NUM_COMPONENTS 3

static pdf_render_ifc_t *pdf_render;

static void _mupdf_init(wprint_image_info_t *image_info) {
    pdf_render = create_pdf_render_ifc();
}

/* Return current clock time in milliseconds */
static long get_millis() {
    struct timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);
    return (long) (((int64_t) now.tv_sec * 1000000000LL + now.tv_nsec) / 1000000);
}

static status_t _mupdf_get_hdr(wprint_image_info_t *image_info) {
    double pageWidth, pageHeight;
    float zoom;
    unsigned int imageWidth;
    unsigned int imageHeight;
    int size;
    char *rawBuffer;
    status_t result;
    int pages;

    pages = pdf_render->openDocument(pdf_render, image_info->decoder_data.urlPath);
    if (pages < 1) return ERROR;

    result = pdf_render->getPageAttributes(pdf_render, image_info->decoder_data.page, &pageWidth,
            &pageHeight);
    if (result != OK) return result;

    const float POINTS_PER_INCH = MUPDF_DEFAULT_RESOLUTION;
    zoom = (image_info->pdf_render_resolution) / POINTS_PER_INCH;

    imageWidth = (unsigned int) (pageWidth * zoom);
    imageHeight = (unsigned int) (pageHeight * zoom);

    image_info->width = imageWidth;
    image_info->height = imageHeight;

    size = imageWidth * imageHeight * 3;

    rawBuffer = (char *) malloc((size_t) size);
    if (!rawBuffer) return ERROR;

    LOGI("Render page=%d w=%.0f h=%.0f res=%d zoom=%0.2f size=%d", image_info->decoder_data.page,
            pageWidth, pageHeight, image_info->pdf_render_resolution, zoom, size);

    long now = get_millis();

    result = pdf_render->renderPageStripe(pdf_render, image_info->decoder_data.page, imageWidth,
            imageHeight, zoom, rawBuffer);
    if (result != OK) {
        free(rawBuffer);
        return result;
    }

    LOGI("Render complete in %ld ms", get_millis() - now);

    image_info->decoder_data.pdf_info.fz_pixmap_ptr = rawBuffer;
    image_info->decoder_data.pdf_info.bitmap_ptr = malloc(
            image_info->width * RGB_NUMBER_PIXELS_NUM_COMPONENTS);
    image_info->num_components = RGB_NUMBER_PIXELS_NUM_COMPONENTS;

    return OK;
}

static unsigned char *_mupdf_decode_row(wprint_image_info_t *image_info, int row) {
    unsigned char *rgbPixels = 0;

    if (image_info->swath_start == -1) {
        wprint_image_compute_rows_to_cache(image_info);
    }

    image_info->swath_start = row;
    if (NULL != image_info->decoder_data.pdf_info.fz_pixmap_ptr) {
        rgbPixels = (unsigned char *) image_info->decoder_data.pdf_info.bitmap_ptr;
        memcpy(rgbPixels, (char *) (image_info->decoder_data.pdf_info.fz_pixmap_ptr) +
                        row * image_info->width * RGB_NUMBER_PIXELS_NUM_COMPONENTS,
                image_info->width * RGB_NUMBER_PIXELS_NUM_COMPONENTS);
    }
    return rgbPixels;
}

static status_t _mupdf_cleanup(wprint_image_info_t *image_info) {
    LOGD("MUPDF: _mupdf_cleanup(): Enter");
    if (NULL != image_info->decoder_data.pdf_info.fz_pixmap_ptr) {
        free(image_info->decoder_data.pdf_info.fz_pixmap_ptr);
        image_info->decoder_data.pdf_info.fz_pixmap_ptr = NULL;
    }
    if (image_info->decoder_data.pdf_info.bitmap_ptr != NULL) {
        free(image_info->decoder_data.pdf_info.bitmap_ptr);
    }
    pdf_render->destroy(pdf_render);
    pdf_render = NULL;
    return OK;
}

static status_t _mupdf_supports_subsampling(wprint_image_info_t *image_info) {
    LOGI("MUPDF: _mupdf_supports_subsampling(): Enter");
    return ERROR;
}

static int _mupdf_native_units(wprint_image_info_t *image_info) {
    return image_info->pdf_render_resolution;
}

static const image_decode_ifc_t _mupdf_decode_ifc = {&_mupdf_init, &_mupdf_get_hdr,
        &_mupdf_decode_row, &_mupdf_cleanup,
        &_mupdf_supports_subsampling,
        &_mupdf_native_units,};

const image_decode_ifc_t *wprint_mupdf_decode_ifc = &_mupdf_decode_ifc;