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

#include <sys/types.h>
#include <stdio.h>
#include <stdlib.h>
#include <math.h>

#include "lib_pcl.h"
#include "wprint_image.h"

#include "pclm_wrapper_api.h"

#include "media.h"
#include "wprint_debug.h"

#define TAG "lib_pclm"

/*
 * Store a valid media_size name into media_name
 */
static void _get_pclm_media_size_name(pcl_job_info_t *job_info, media_size_t media_size,
        char *media_name) {
    int i = 0;
    for (i = 0; i < SUPPORTED_MEDIA_SIZE_COUNT; i++) {
        if (media_size == SupportedMediaSizes[i].media_size) {
            strncpy(media_name, SupportedMediaSizes[i].PCL6Name,
                    strlen(SupportedMediaSizes[i].PCL6Name));
            LOGD("_get_pclm_media_size_name(): match found: %d, %s", media_size, media_name);
            break;  // we found a match, so break out of loop
        }
    }

    if (i == SUPPORTED_MEDIA_SIZE_COUNT) {
        // media size not found, defaulting to letter
        LOGD("_get_pclm_media_size_name(): media size, %d, NOT FOUND, setting to letter",
                media_size);
        _get_pclm_media_size_name(job_info, US_LETTER, media_name);
    }
}

/*
 * Write a valid media_size into myPageInfo
 */
static void _get_pclm_media_size(pcl_job_info_t *job_info, media_size_t media_size,
        PCLmPageSetup *myPageInfo) {
    int i = SUPPORTED_MEDIA_SIZE_COUNT;

    if (myPageInfo != NULL) {
        for (i = 0; i < SUPPORTED_MEDIA_SIZE_COUNT; i++) {
            if (media_size == SupportedMediaSizes[i].media_size) {
                strncpy(myPageInfo->mediaSizeName, SupportedMediaSizes[i].PCL6Name,
                        sizeof(myPageInfo->mediaSizeName) - 1);

                myPageInfo->mediaWidth = floorf(
                        _MI_TO_POINTS(SupportedMediaSizes[i].WidthInInches));
                myPageInfo->mediaHeight = floorf(
                        _MI_TO_POINTS(SupportedMediaSizes[i].HeightInInches));

                LOGD("_get_pclm_media_size(): match found: %d, %s, width=%f, height=%f",
                        media_size, SupportedMediaSizes[i].PCL6Name, myPageInfo->mediaWidth,
                        myPageInfo->mediaHeight);
                break;  // we found a match, so break out of loop
            }
        }
    }

    if (i == SUPPORTED_MEDIA_SIZE_COUNT) {
        // media size not found, defaulting to letter
        LOGD("_get_pclm_media_size(): media size, %d, NOT FOUND, setting to letter", media_size);
        _get_pclm_media_size(job_info, US_LETTER, myPageInfo);
    }
}

static wJob_t _start_job(wJob_t job_handle, pcl_job_info_t *job_info, media_size_t media_size,
        media_type_t media_type, int resolution, duplex_t duplex, duplex_dry_time_t dry_time,
        color_space_t color_space, media_tray_t media_tray, float top_margin,
        float left_margin) {
    int outBuffSize = 0;

    if (job_info == NULL) {
        return _WJOBH_NONE;
    }

    if (job_info->job_handle != _WJOBH_NONE) {
        if (job_info->wprint_ifc != NULL) {
            LOGD("_start_job() required cleanup");
        }
        job_info->job_handle = _WJOBH_NONE;
    }

    if ((job_info->wprint_ifc == NULL) || (job_info->print_ifc == NULL)) {
        return _WJOBH_NONE;
    }

    LOGD("_start_job(), media_size %d, media_type %d, dt %d, %s, media_tray %d margins T %f L %f",
            media_size, media_type, dry_time,
            (duplex == DUPLEX_MODE_NONE) ? "simplex" : "duplex",
            media_tray, top_margin, left_margin);

    job_info->job_handle = job_handle;

    _START_JOB(job_info, "pdf");

    job_info->resolution = resolution;
    job_info->media_size = media_size;
    job_info->standard_scale = (float) resolution / (float) STANDARD_SCALE_FOR_PDF;

    //  initialize static variables
    job_info->pclm_output_buffer = NULL;
    job_info->seed_row = job_info->pcl_buff = NULL;    // unused
    job_info->pixel_width = job_info->pixel_height = job_info->page_number = job_info->num_rows = 0;

    memset((void *) &job_info->pclm_page_info, 0x0, sizeof(PCLmPageSetup));
    _get_pclm_media_size_name(job_info, media_size, &job_info->pclm_page_info.mediaSizeName[0]);

    if ((left_margin < 0.0f) || (top_margin < 0.0f)) {
        job_info->pclm_page_info.mediaWidthOffset = 0.0f;
        job_info->pclm_page_info.mediaHeightOffset = 0.0f;
    } else {
        job_info->pclm_page_info.mediaWidthOffset = (left_margin * (float) STANDARD_SCALE_FOR_PDF);
        job_info->pclm_page_info.mediaHeightOffset = (top_margin * (float) STANDARD_SCALE_FOR_PDF);
    }

    LOGI("_start_job(), mediaHeightOffsets W %f H %f", job_info->pclm_page_info.mediaWidthOffset,
            job_info->pclm_page_info.mediaHeightOffset);

    job_info->pclm_page_info.pageOrigin = top_left;    // REVISIT

    job_info->monochrome = (color_space == COLOR_SPACE_MONO);
    job_info->pclm_page_info.dstColorSpaceSpefication = deviceRGB;
    if (color_space == COLOR_SPACE_MONO) {
        job_info->pclm_page_info.dstColorSpaceSpefication = grayScale;
    } else if (color_space == COLOR_SPACE_COLOR) {
        job_info->pclm_page_info.dstColorSpaceSpefication = deviceRGB;
    } else if (color_space == COLOR_SPACE_ADOBE_RGB) {
        job_info->pclm_page_info.dstColorSpaceSpefication = adobeRGB;
    }

    job_info->pclm_page_info.stripHeight = job_info->strip_height;
    job_info->pclm_page_info.destinationResolution = res600;
    if (resolution == 300) {
        job_info->pclm_page_info.destinationResolution = res300;
    } else if (resolution == 600) {
        job_info->pclm_page_info.destinationResolution = res600;
    } else if (resolution == 1200) {
        job_info->pclm_page_info.destinationResolution = res1200;
    }

    if (duplex == DUPLEX_MODE_BOOK) {
        job_info->pclm_page_info.duplexDisposition = duplex_longEdge;
    } else if (duplex == DUPLEX_MODE_TABLET) {
        job_info->pclm_page_info.duplexDisposition = duplex_shortEdge;
    } else {
        job_info->pclm_page_info.duplexDisposition = simplex;
    }

    job_info->pclm_page_info.mirrorBackside = false;
    job_info->pclmgen_obj = CreatePCLmGen();
    PCLmStartJob(job_info->pclmgen_obj, (void **) &job_info->pclm_output_buffer, &outBuffSize);
    _WRITE(job_info, (const char *) job_info->pclm_output_buffer, outBuffSize);
    return job_info->job_handle;
}

static int _start_page(pcl_job_info_t *job_info, int pixel_width, int pixel_height) {
    PCLmPageSetup *page_info = &job_info->pclm_page_info;
    ubyte *pclm_output_buff = job_info->pclm_output_buffer;
    int outBuffSize = 0;

    _START_PAGE(job_info, pixel_width, pixel_height);

    page_info->sourceHeight = (float) pixel_height / job_info->standard_scale;
    page_info->sourceWidth = (float) pixel_width / job_info->standard_scale;
    LOGI("_start_page(), strip height=%d, image width=%d, image height=%d, scaled width=%f, "
            "scaled height=%f", page_info->stripHeight, pixel_width, pixel_height,
            page_info->sourceWidth, page_info->sourceHeight);

    if (job_info->num_components == 3) {
        page_info->colorContent = color_content;
        page_info->srcColorSpaceSpefication = deviceRGB;
    } else {
        page_info->colorContent = gray_content;
        page_info->srcColorSpaceSpefication = grayScale;
    }

    /* Note: we could possibly get this value dynamically from device via IPP (ePCL) however,
     * current ink devices report RLE as the default compression type, which compresses much
     * worse than JPEG or FLATE
     */
    page_info->compTypeRequested = compressDCT;
    job_info->scan_line_width = pixel_width * job_info->num_components;
    int res1 = PCLmGetMediaDimensions(job_info->pclmgen_obj, page_info->mediaSizeName, page_info);
    page_info->SourceWidthPixels = MIN(pixel_width, job_info->pclm_page_info.mediaWidthInPixels);
    page_info->SourceHeightPixels = pixel_height;
    job_info->pclm_scan_line_width =
            job_info->pclm_page_info.mediaWidthInPixels * job_info->num_components;

    LOGD("PCLmGetMediaDimensions(%d), mediaSizeName=%s, mediaheight=%f, mediawidth=%f, "
            "mheightpixels=%d, mwidthpixels=%d", res1, job_info->pclm_page_info.mediaSizeName,
            job_info->pclm_page_info.mediaWidth, job_info->pclm_page_info.mediaHeight,
            job_info->pclm_page_info.mediaWidthInPixels,
            job_info->pclm_page_info.mediaHeightInPixels);

    PCLmStartPage(job_info->pclmgen_obj, page_info, (void **) &pclm_output_buff, &outBuffSize);
    _WRITE(job_info, (const char *) pclm_output_buff, outBuffSize);

    job_info->page_number++;
    return job_info->page_number;
}

static int _print_swath(pcl_job_info_t *job_info, char *rgb_pixels, int start_row, int num_rows,
        int bytes_per_row) {
    int outBuffSize = 0;
    _PAGE_DATA(job_info, (const unsigned char *) rgb_pixels, (num_rows * bytes_per_row));

    if (job_info->monochrome) {
        unsigned char *buff = (unsigned char *) rgb_pixels;
        int nbytes = (num_rows * bytes_per_row);
        int readIndex, writeIndex;
        for (readIndex = writeIndex = 0; readIndex < nbytes; readIndex += BYTES_PER_PIXEL(1)) {
            unsigned char gray = SP_GRAY(buff[readIndex + 0], buff[readIndex + 1],
                    buff[readIndex + 2]);
            buff[writeIndex++] = gray;
            buff[writeIndex++] = gray;
            buff[writeIndex++] = gray;
        }
    }

    LOGD("_print_swath(): page #%d, buffSize=%d, rows %d - %d (%d rows), bytes per row %d",
            job_info->page_number, job_info->strip_height * job_info->scan_line_width, start_row,
            start_row + num_rows - 1, num_rows, bytes_per_row);

    if (job_info->scan_line_width > job_info->pclm_scan_line_width) {
        int i;
        char *src_pixels = rgb_pixels + job_info->scan_line_width;
        char *dest_pixels = rgb_pixels + job_info->pclm_scan_line_width;
        for (i = 1; i < num_rows; i++, src_pixels += job_info->scan_line_width,
                dest_pixels += job_info->pclm_scan_line_width) {
            memmove(dest_pixels, src_pixels, job_info->pclm_scan_line_width);
        }
    }

    /* if the inBufferSize is ever used in genPCLm, change the input parameter to pass in
     * image_info->printable_width*num_components*strip_height
     * it is currently pixel_width (from _start_page()) * num_components * strip_height
     */
    PCLmEncapsulate(job_info->pclmgen_obj, rgb_pixels,
            job_info->strip_height * MIN(job_info->scan_line_width, job_info->pclm_scan_line_width),
            num_rows, (void **) &job_info->pclm_output_buffer, &outBuffSize);
    _WRITE(job_info, (const char *) job_info->pclm_output_buffer, outBuffSize);

    return OK;
}

static int _end_page(pcl_job_info_t *job_info, int page_number) {
    int outBuffSize = 0;

    if (page_number == -1) {
        LOGI("_end_page(): writing blank page");
        _start_page(job_info, 0, 0);
        unsigned char blank_data[1] = {0xFF};
        PCLmEncapsulate(job_info->pclmgen_obj, (void *) blank_data, 1, 1,
                (void **) &job_info->pclm_output_buffer, &outBuffSize);
        _WRITE(job_info, (const char *) job_info->pclm_output_buffer, outBuffSize);
    }
    LOGI("_end_page()");
    PCLmEndPage(job_info->pclmgen_obj, (void **) &job_info->pclm_output_buffer, &outBuffSize);
    _WRITE(job_info, (const char *) job_info->pclm_output_buffer, outBuffSize);
    _END_PAGE(job_info);

    return OK;
}

static int _end_job(pcl_job_info_t *job_info) {
    int outBuffSize = 0;

    LOGI("_end_job()");
    PCLmEndJob(job_info->pclmgen_obj, (void **) &job_info->pclm_output_buffer, &outBuffSize);
    _WRITE(job_info, (const char *) job_info->pclm_output_buffer, outBuffSize);
    PCLmFreeBuffer(job_info->pclmgen_obj, job_info->pclm_output_buffer);
    DestroyPCLmGen(job_info->pclmgen_obj);
    _END_JOB(job_info);
    return OK;
}

static bool _canCancelMidPage(void) {
    return false;
}

static const ifc_pcl_t _pcl_ifc = {
    _start_job, _end_job, _start_page, _end_page, _print_swath, _canCancelMidPage
};

ifc_pcl_t *pclm_connect(void) {
    return ((ifc_pcl_t *) &_pcl_ifc);
}