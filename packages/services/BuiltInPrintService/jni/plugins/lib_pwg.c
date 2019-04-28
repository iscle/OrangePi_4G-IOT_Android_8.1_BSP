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
#include <cups/raster.h>

#include "lib_pcl.h"
#include "wprint_image.h"

#include "media.h"

#define TAG "lib_pwg"
#define STANDARD_SCALE_FOR_PDF    72.0
#define _MI_TO_PIXELS(n, res)      ((n)*(res)+500)/1000.0
#define _MI_TO_POINTS(n)          _MI_TO_PIXELS(n, STANDARD_SCALE_FOR_PDF)

cups_raster_t *ras_out = NULL;
cups_page_header2_t header_pwg;

/*
 * Write the PWG header
 */
static void _write_header_pwg(int pixel_width, int pixel_height, cups_page_header2_t *h,
        bool monochrome) {
    if (h != NULL) {
        strcpy(h->MediaClass, "PwgRaster");
        strcpy(h->MediaColor, "");
        strcpy(h->MediaType, "");
        strcpy(h->OutputType, "");
        h->AdvanceDistance = 0;
        h->AdvanceMedia = CUPS_ADVANCE_FILE;
        h->Collate = CUPS_FALSE;
        h->CutMedia = CUPS_CUT_NONE;
        h->cupsPageSize[0] = (float) ((pixel_width * STANDARD_SCALE_FOR_PDF) / h->HWResolution[0]);
        h->cupsPageSize[1] = (float) ((pixel_height * STANDARD_SCALE_FOR_PDF) / h->HWResolution[1]);

        h->ImagingBoundingBox[0] = 0;
        h->ImagingBoundingBox[1] = 0;
        h->ImagingBoundingBox[2] = h->cupsPageSize[0];
        h->ImagingBoundingBox[3] = h->cupsPageSize[1];
        h->cupsBorderlessScalingFactor = 1.0;
        h->InsertSheet = CUPS_FALSE;
        h->Jog = CUPS_JOG_NONE;
        h->LeadingEdge = CUPS_EDGE_TOP;
        h->Margins[0] = 0;
        h->Margins[1] = 0;
        h->ManualFeed = CUPS_TRUE;
        h->MediaPosition = 0;
        h->MediaWeight = 0;
        h->MirrorPrint = CUPS_FALSE;
        h->NegativePrint = CUPS_FALSE;
        h->NumCopies = 1;
        h->Orientation = CUPS_ORIENT_0;
        h->PageSize[0] = (int) h->cupsPageSize[0];
        h->PageSize[1] = (int) h->cupsPageSize[1];
        h->Separations = CUPS_TRUE;
        h->TraySwitch = CUPS_TRUE;
        h->Tumble = CUPS_TRUE;
        h->cupsWidth = pixel_width;
        h->cupsHeight = pixel_height;
        h->cupsBitsPerPixel = (monochrome ? 8 : 24);
        h->cupsBitsPerColor = 8;
        h->cupsColorSpace = (monochrome ? CUPS_CSPACE_SW : CUPS_CSPACE_SRGB);
        h->cupsBytesPerLine = (h->cupsBitsPerPixel * pixel_width + 7) / 8;
        h->cupsColorOrder = CUPS_ORDER_CHUNKED;
        h->cupsCompression = 0;
        h->cupsRowCount = 1;
        h->cupsRowFeed = 1;
        h->cupsRowStep = 1;
        h->cupsNumColors = 0;
        h->cupsImagingBBox[0] = 0.0;
        h->cupsImagingBBox[1] = 0.0;
        h->cupsImagingBBox[2] = 0.0;
        h->cupsImagingBBox[3] = 0.0;

        strcpy(h->cupsMarkerType, "Marker Type");
        strcpy(h->cupsRenderingIntent, "Rendering Intent");
        strcpy(h->cupsPageSizeName, "Letter");
    }
}

/*
 * Store the supplied media size into job_info
 */
static void _get_pwg_media_size(pcl_job_info_t *job_info, media_size_t media_size,
        PCLmPageSetup *myPageInfo) {
    int i = 0;
    do {
        if (myPageInfo == NULL) {
            continue;
        }

        for (i = 0; i < SUPPORTED_MEDIA_SIZE_COUNT; i++) {
            if (media_size == SupportedMediaSizes[i].media_size) {
                strncpy(myPageInfo->mediaSizeName, SupportedMediaSizes[i].PCL6Name,
                        sizeof(myPageInfo->mediaSizeName) - 1);

                myPageInfo->mediaWidth = floorf(
                        _MI_TO_POINTS(SupportedMediaSizes[i].WidthInInches));
                myPageInfo->mediaHeight = floorf(
                        _MI_TO_POINTS(SupportedMediaSizes[i].HeightInInches));

                LOGD("  _get_pwg_media_size(): match found: %d, %s, width=%f, height=%f",
                        media_size, SupportedMediaSizes[i].PCL6Name, myPageInfo->mediaWidth,
                        myPageInfo->mediaHeight);
                break;  // we found a match, so break out of loop
            }
        }
    }
    while (0);

    if (i == SUPPORTED_MEDIA_SIZE_COUNT) {
        // media size not found, defaulting to letter
        LOGD("_get_pwg_media_size(): media size, %d, NOT FOUND, setting to letter", media_size);
        _get_pwg_media_size(job_info, US_LETTER, myPageInfo);
    }
}

/*
 * Write a buffer to the output stream
 */
static ssize_t _pwg_io_write(void *ctx, unsigned char *buf, size_t bytes) {
    pcl_job_info_t *pwg_job_info = (pcl_job_info_t *) ctx;
    _WRITE(pwg_job_info, (const char *) buf, bytes);
    return bytes;
}

static wJob_t _start_job(wJob_t job_handle, pcl_job_info_t *job_info, media_size_t media_size,
        media_type_t media_type, int resolution, duplex_t duplex, duplex_dry_time_t dry_time,
        color_space_t color_space, media_tray_t media_tray, float top_margin,
        float left_margin) {
    if (job_info == NULL) {
        return _WJOBH_NONE;
    }

    if (job_info->job_handle != _WJOBH_NONE) {
        if (job_info->wprint_ifc != NULL) {
            LOGE("_start_job() required cleanup");
        }

        job_info->job_handle = _WJOBH_NONE;
    }

    if ((job_info->wprint_ifc == NULL) || (job_info->print_ifc == NULL)) {
        return _WJOBH_NONE;
    }

    LOGD("_start_job(), media_size %d, media_type %d, dt %d, %s, media_tray %d", media_size,
            media_type, dry_time, (duplex == DUPLEX_MODE_NONE) ? "simplex" : "duplex",
            media_tray);
    job_info->job_handle = job_handle;

    _START_JOB(job_info, "pwg");

    header_pwg.HWResolution[0] = resolution;
    header_pwg.HWResolution[1] = resolution;

    job_info->resolution = resolution;
    job_info->media_size = media_size;
    job_info->standard_scale = (float) resolution / (float) 72;

    //  initialize static variables
    job_info->pclm_output_buffer = NULL;
    job_info->seed_row = job_info->pcl_buff = NULL;    // unused
    job_info->pixel_width = job_info->pixel_height = job_info->page_number = job_info->num_rows = 0;

    memset((void *) &job_info->pclm_page_info, 0x0, sizeof(PCLmPageSetup));
    _get_pwg_media_size(job_info, media_size, &job_info->pclm_page_info);

    if (left_margin < 0.0f || top_margin < 0.0f) {
        job_info->pclm_page_info.mediaWidthOffset = 0.0f;
        job_info->pclm_page_info.mediaHeightOffset = 0.0f;
    } else {
        job_info->pclm_page_info.mediaWidthOffset = left_margin;
        job_info->pclm_page_info.mediaHeightOffset = top_margin;
    }

    header_pwg.cupsMediaType = media_size;

    job_info->pclm_page_info.pageOrigin = top_left;    // REVISIT
    job_info->monochrome = (color_space == COLOR_SPACE_MONO);
    job_info->pclm_page_info.dstColorSpaceSpefication = deviceRGB;
    if (color_space == COLOR_SPACE_MONO) {
        header_pwg.cupsColorSpace = CUPS_CSPACE_SW;
        job_info->pclm_page_info.dstColorSpaceSpefication = deviceRGB;
    } else if (color_space == COLOR_SPACE_COLOR) {
        job_info->pclm_page_info.dstColorSpaceSpefication = deviceRGB;
        header_pwg.cupsColorSpace = CUPS_CSPACE_SRGB;
    } else if (color_space == COLOR_SPACE_ADOBE_RGB) {
        job_info->pclm_page_info.dstColorSpaceSpefication = adobeRGB;
        header_pwg.cupsColorSpace = CUPS_CSPACE_SRGB;
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
        header_pwg.Duplex = CUPS_TRUE;
    } else if (duplex == DUPLEX_MODE_TABLET) {
        job_info->pclm_page_info.duplexDisposition = duplex_shortEdge;
        header_pwg.Duplex = CUPS_TRUE;
    } else {
        job_info->pclm_page_info.duplexDisposition = simplex;
        header_pwg.Duplex = CUPS_FALSE;
    }

    job_info->pclm_page_info.mirrorBackside = false;
    header_pwg.OutputFaceUp = CUPS_FALSE;
    header_pwg.cupsBitsPerColor = BITS_PER_CHANNEL;
    ras_out = cupsRasterOpenIO(_pwg_io_write, (void *) job_info, CUPS_RASTER_WRITE_PWG);
    return job_info->job_handle;
}

static int _start_page(pcl_job_info_t *job_info, int pixel_width, int pixel_height) {
    PCLmPageSetup *page_info = &job_info->pclm_page_info;
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
    page_info->colorContent = color_content;
    page_info->srcColorSpaceSpefication = deviceRGB;

    // REVISIT: possibly get this value dynamically from device via IPP (ePCL)
    // however, current ink devices report RLE as the default compression type, which compresses
    // much worse than JPEG or FLATE
    page_info->compTypeRequested = compressDCT;

    job_info->scan_line_width = BYTES_PER_PIXEL(pixel_width);

    // Fill up the pwg header
    _write_header_pwg(pixel_width, pixel_height, &header_pwg, job_info->monochrome);

    LOGI("cupsWidth = %d", header_pwg.cupsWidth);
    LOGI("cupsHeight = %d", header_pwg.cupsHeight);
    LOGI("cupsPageWidth = %f", header_pwg.cupsPageSize[0]);
    LOGI("cupsPageHeight = %f", header_pwg.cupsPageSize[1]);
    LOGI("cupsBitsPerColor = %d", header_pwg.cupsBitsPerColor);
    LOGI("cupsBitsPerPixel = %d", header_pwg.cupsBitsPerPixel);
    LOGI("cupsBytesPerLine = %d", header_pwg.cupsBytesPerLine);
    LOGI("cupsColorOrder = %d", header_pwg.cupsColorOrder);
    LOGI("cupsColorSpace = %d", header_pwg.cupsColorSpace);

    cupsRasterWriteHeader2(ras_out, &header_pwg);
    job_info->page_number++;
    return job_info->page_number;
}

static int _print_swath(pcl_job_info_t *job_info, char *rgb_pixels, int start_row, int num_rows,
        int bytes_per_row) {
    int outBuffSize;
    _PAGE_DATA(job_info, (const unsigned char *) rgb_pixels, (num_rows * bytes_per_row));

    if (job_info->monochrome) {
        unsigned char *buff = (unsigned char *) rgb_pixels;
        int nbytes = (num_rows * bytes_per_row);
        int readIndex, writeIndex;
        for (readIndex = writeIndex = 0; readIndex < nbytes; readIndex += BYTES_PER_PIXEL(1)) {
            unsigned char gray = SP_GRAY(buff[readIndex + 0], buff[readIndex + 1],
                    buff[readIndex + 2]);
            buff[writeIndex++] = gray;
        }
        outBuffSize = writeIndex;
    } else {
        outBuffSize = num_rows * bytes_per_row;
    }

    LOGD("_print_swath(): page #%d, buffSize=%d, rows %d - %d (%d rows), bytes per row %d",
            job_info->page_number, job_info->strip_height * job_info->scan_line_width,
            start_row, start_row + num_rows - 1, num_rows, bytes_per_row);
    /* If the inBufferSize is ever used in genPCLm, change the input parameter to pass in
     * image_info->printable_width*num_components*strip_height. it is currently pixel_width
     * (from _start_page()) * num_components * strip_height
     */
    if (ras_out != NULL) {
        unsigned result = cupsRasterWritePixels(ras_out, (unsigned char *) rgb_pixels, outBuffSize);
        LOGD("cupsRasterWritePixels return %d", result);
    } else {
        LOGD("cupsRasterWritePixels raster is null");
    }
    return OK;
}

/*
 * Allocate and fill a blank page of PackBits data. Writes size into buffer_size. The buffer
 * must be free'd by the caller.
 */
unsigned char *_generate_blank_data(int pixel_width, int pixel_height, uint8 monochrome, size_t *buffer_size) {
    if (pixel_width == 0 || pixel_height == 0) return NULL;

    /* PWG Raster's PackBits-like algorithm allows for a maximum of:
     * 256 repeating rows and is encoded using a single octet containing (count - 1)
     * 128 repeating color value and is run length encoded using a single octet containing (count - 1)
     */
    int rows_full = pixel_height / 256;
    int columns_full = pixel_width / 128;
    int row_fraction = ((pixel_height % 256) != 0) ? 1 : 0;
    int column_fraction = ((pixel_width % 128) != 0) ? 1 : 0;
    int column_data_size = 1 + (columns_full + column_fraction) * (monochrome ? 2 : 4);

    *buffer_size = (size_t) ((rows_full + row_fraction) * column_data_size);
    unsigned char *buffer = (unsigned char *) malloc(*buffer_size);
    if (buffer == NULL) return NULL;

    int i = 0;
    for (int y = 0; y < rows_full + row_fraction; y++) {
        // Add row-repeat command
        if (y < rows_full) {
            buffer[i++] = 0xFF;
        } else {
            buffer[i++] = (unsigned char) ((pixel_height % 256) - 1);
        }

        for (int x = 0; x < columns_full + column_fraction; x++) {
            // Add column-repeat command
            if (x < columns_full) {
                buffer[i++] = 0x7F;
            } else {
                buffer[i++] = (unsigned char) ((pixel_width % 128) - 1);
            }

            // Pixel color to repeat
            buffer[i++] = 0xFF;
            if (!monochrome) {
                // Add rest of RGB for color output
                buffer[i++] = 0xFF;
                buffer[i++] = 0xFF;
            }
        }
    }
    return buffer;
}

static int _end_page(pcl_job_info_t *job_info, int page_number) {
    if (page_number == -1) {
        LOGD("lib_pclm: _end_page(): writing blank page");

        size_t buffer_size;
        unsigned char *buffer;
        _start_page(job_info, header_pwg.cupsWidth, header_pwg.cupsHeight);
        buffer = _generate_blank_data(header_pwg.cupsWidth, header_pwg.cupsHeight, job_info->monochrome, &buffer_size);
        if (buffer == NULL) {
            return ERROR;
        } else {
            _pwg_io_write(job_info, buffer, buffer_size);
            free(buffer);
        }
    }
    LOGI("lib_pcwg: _end_page()");
    _END_PAGE(job_info);

    return OK;
}

static int _end_job(pcl_job_info_t *job_info) {
    LOGI("_end_job()");
    _END_JOB(job_info);
    cupsRasterClose(ras_out);
    return OK;
}

static bool _canCancelMidPage(void) {
    return false;
}

static const ifc_pcl_t _pcl_ifc = {
        _start_job, _end_job, _start_page, _end_page, _print_swath, _canCancelMidPage
};

ifc_pcl_t *pwg_connect(void) {
    return ((ifc_pcl_t *) &_pcl_ifc);
}