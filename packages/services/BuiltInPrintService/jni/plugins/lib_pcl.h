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

#ifndef __LIB_PCL_H__
#define __LIB_PCL_H__

#include "ifc_print_job.h"
#include "ifc_wprint.h"
#include "lib_wprint.h"
#include "lib_pclm.h"
#include "common_defines.h"

#define _WJOBH_NONE  0
#define STANDARD_SCALE_FOR_PDF    72.0

#define SP_GRAY(Yr, Cbg, Crb) (((Yr<<6) + (Cbg*160) + (Crb<<5)) >> 8)

#define _START_JOB(JOB_INFO, EXT) \
{ \
    const ifc_wprint_debug_stream_t* debug_ifc = \
        JOB_INFO->wprint_ifc->get_debug_stream_ifc(JOB_INFO->job_handle); \
    if (debug_ifc) { \
        debug_ifc->debug_start_job(JOB_INFO->job_handle, EXT); \
    } \
}

#define _START_PAGE(JOB_INFO, WIDTH, HEIGHT) \
{ \
    const ifc_wprint_debug_stream_t* debug_ifc = \
        JOB_INFO->wprint_ifc->get_debug_stream_ifc(JOB_INFO->job_handle); \
    if (debug_ifc) { \
        debug_ifc->debug_start_page(JOB_INFO->job_handle, JOB_INFO->page_number + 1, WIDTH, \
            HEIGHT); \
    } \
}

#define _PAGE_DATA(JOB_INFO, BUFF, LEN) \
{ \
    const ifc_wprint_debug_stream_t* debug_ifc = \
        JOB_INFO->wprint_ifc->get_debug_stream_ifc(JOB_INFO->job_handle); \
    if (debug_ifc) { \
        debug_ifc->debug_page_data(JOB_INFO->job_handle, BUFF, LEN); \
    } \
}

#define _END_PAGE(JOB_INFO) \
{ \
    const ifc_wprint_debug_stream_t* debug_ifc = \
        JOB_INFO->wprint_ifc->get_debug_stream_ifc(JOB_INFO->job_handle); \
    if (debug_ifc) { \
        debug_ifc->debug_end_page(JOB_INFO->job_handle); \
    } \
}

#define _END_JOB(JOB_INFO) \
{ \
    const ifc_wprint_debug_stream_t* debug_ifc = \
        JOB_INFO->wprint_ifc->get_debug_stream_ifc(JOB_INFO->job_handle); \
    if (debug_ifc) { \
        debug_ifc->debug_end_job(JOB_INFO->job_handle); \
    } \
}

#define _WRITE(JOB_INFO, BUFF, LEN)   \
{ \
    const ifc_wprint_debug_stream_t* debug_ifc = \
        JOB_INFO->wprint_ifc->get_debug_stream_ifc(JOB_INFO->job_handle); \
    if (debug_ifc) { \
        debug_ifc->debug_job_data(JOB_INFO->job_handle, (const unsigned char *)BUFF, LEN); \
    } \
    JOB_INFO->print_ifc->send_data(JOB_INFO->print_ifc, BUFF, LEN); \
}

/*
 * PCL/PWG job definition
 */
typedef struct {
    const ifc_wprint_t *wprint_ifc;
    const ifc_print_job_t *print_ifc;

    wJob_t job_handle;
    uint8 *seed_row, *pcl_buff;
    uint8 *halftone_row;
    sint16 *error_buf;
    int pixel_width, pixel_height;
    media_size_t media_size;
    int resolution;
    int page_number, num_rows;
    int send_full_row;
    int rows_to_skip;
    uint8 monochrome;

    int num_components;
    int scan_line_width;
    float standard_scale;
    int strip_height;
    int pclm_scan_line_width;

    void *pclmgen_obj;
    PCLmPageSetup pclm_page_info;
    uint8 *pclm_output_buffer;
    const char *useragent;
} pcl_job_info_t;

/*
 * Interface for PCL and PWG job handling
 */
typedef struct ifc_pcl_st {
    /*
     * Called once per job at the start of the job. Returns a print job handle that is used
     * in other functions of this library. Returns WPRINT_BAD_JOB_HANDLE for errors.
     */
    wJob_t (*start_job)(wJob_t job_handle, pcl_job_info_t *job_info, media_size_t media_size,
            media_type_t media_type, int resolution, duplex_t duplex,
            duplex_dry_time_t dry_time, color_space_t color_space, media_tray_t media_tray,
            float top_margin, float left_margin);

    /*
     * Called once per job at the end of the job. A current print job
     * must end for the next one to start. Returns OK or ERROR as the case maybe.
     */
    status_t (*end_job)(pcl_job_info_t *job_info);

    /*
     * Called once per page of the job to indicate start of the page and page metrics.
     * Returns running page number starting with 1 or ERROR.
     */
    status_t (*start_page)(pcl_job_info_t *job_info,
            int pixel_width,
            int pixel_height);

    /*
     * Called once per page of the job to indicate end of the page. Returns OK or ERROR.
     */
    status_t (*end_page)(pcl_job_info_t *job_info,
            int page_number);

    /*
     * Called several times a page to send a rectangular swath of RGB data. The array
     * rgb_pixels[] must have (num_rows * pixel_width) pixels. bytes_per_row can be used for
     * 32-bit aligned rows. Returns OK or ERROR.
     */
    status_t (*print_swath)(pcl_job_info_t *job_info, char *rgb_pixels, int start_row, int num_rows,
            int bytes_per_row);

    /*
     * Return true if this interface can cancel a job partway through a page
     */
    bool (*canCancelMidPage)(void);
} ifc_pcl_t;

/*
 * Connect to the PCLm plugin, returning its interface
 */
ifc_pcl_t *pclm_connect(void);

/*
 * Connect to the pwg plugin, returning its interface
 */
ifc_pcl_t *pwg_connect(void);

#endif // __LIB_PCL_H__