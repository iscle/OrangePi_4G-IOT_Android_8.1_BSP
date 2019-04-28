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

#ifndef __LIB_WPRINT_H__
#define __LIB_WPRINT_H__

#include "wtypes.h"
#include "wprint_df_types.h"
#include "mime_types.h"
#include "printer_capabilities_types.h"
#include "wprint_status_types.h"
#include "ifc_wprint.h"
#include <dlfcn.h>
#include <ctype.h>
#include <stdlib.h>

#define WPRINT_BAD_JOB_HANDLE ((wJob_t) ERROR)

#define _INTERFACE_MAJOR_VERSION  1
#define _INTERFACE_MINOR_VERSION  0

#define _PLUGIN_MAJOR_VERSION 1

#define WPRINT_INTERFACE_VERSION ((uint32)((_INTERFACE_MAJOR_VERSION << 16) | \
    (_INTERFACE_MINOR_VERSION & 0xffff)))
#define WPRINT_PLUGIN_VERSION(X) ((uint32)((_PLUGIN_MAJOR_VERSION << 16) | (X & 0xffff)))

#define major_version(X) ((X >> 16) & 0xffff)
#define minor_version(X) ((X >> 0) & 0xffff)

#define STRIPE_HEIGHT           (16)
#define BUFFERED_ROWS           (STRIPE_HEIGHT * 8)

#define MAX_MIME_LENGTH         (64)
#define MAX_PRINTER_ADDR_LENGTH (64)
#define MAX_FILENAME_LENGTH     (32)
#define MAX_PATHNAME_LENGTH     (255)
#define MAX_ID_STRING_LENGTH    (64)
#define MAX_NAME_LENGTH         (255)

#ifdef __cplusplus
extern "C"
{
#endif

typedef enum {
    DUPLEX_DRY_TIME_NORMAL, // 18 seconds
    DUPLEX_DRY_TIME_LOWER, // 10 seconds
    DUPLEX_DRY_TIME_MINIMUM     // 5 seconds
} duplex_dry_time_t;

typedef enum {
    PORT_INVALID = -1,
    PORT_FILE = 0,
    PORT_IPP = 631,
} port_t;

typedef enum {
    PCLNONE,
    PCLm,
    PCLJPEG,
    PCLPWG,

    PCL_NUM_TYPES
} pcl_t;

typedef enum {
    AUTO_ROTATE,
    CENTER_VERTICAL,
    CENTER_HORIZONTAL,
    ROTATE_BACK_PAGE,
    BACK_PAGE_PREROTATED,
    AUTO_SCALE,
    AUTO_FIT,
    PORTRAIT_MODE,
    LANDSCAPE_MODE,
    CENTER_ON_ORIENTATION,
    DOCUMENT_SCALING,
} render_flags_t;

#define RENDER_FLAG_AUTO_ROTATE           (1 << AUTO_ROTATE)
#define RENDER_FLAG_ROTATE_BACK_PAGE      (1 << ROTATE_BACK_PAGE)
#define RENDER_FLAG_BACK_PAGE_PREROTATED  (1 << BACK_PAGE_PREROTATED)
#define RENDER_FLAG_CENTER_VERTICAL       (1 << CENTER_VERTICAL)
#define RENDER_FLAG_CENTER_HORIZONTAL     (1 << CENTER_HORIZONTAL)
#define RENDER_FLAG_AUTO_SCALE            (1 << AUTO_SCALE)
#define RENDER_FLAG_AUTO_FIT              (1 << AUTO_FIT)
#define RENDER_FLAG_PORTRAIT_MODE         (1 << PORTRAIT_MODE)
#define RENDER_FLAG_LANDSCAPE_MODE        (1 << LANDSCAPE_MODE)
#define RENDER_FLAG_CENTER_ON_ORIENTATION (1 << CENTER_ON_ORIENTATION)
#define RENDER_FLAG_DOCUMENT_SCALING      (1 << DOCUMENT_SCALING)

#define AUTO_SCALE_RENDER_FLAGS          (RENDER_FLAG_AUTO_SCALE | \
                                          RENDER_FLAG_AUTO_ROTATE | \
                                          RENDER_FLAG_CENTER_VERTICAL | \
                                          RENDER_FLAG_CENTER_HORIZONTAL)

#define AUTO_FIT_RENDER_FLAGS            (RENDER_FLAG_AUTO_FIT | \
                                          RENDER_FLAG_AUTO_ROTATE | \
                                          RENDER_FLAG_CENTER_ON_ORIENTATION)

#define ORIENTATION_RENDER_FLAGS         (RENDER_FLAG_AUTO_ROTATE | \
                                          RENDER_FLAG_PORTRAIT_MODE | \
                                          RENDER_FLAG_LANDSCAPE_MODE | \
                                          RENDER_FLAG_CENTER_ON_ORIENTATION)

typedef void (*wprint_status_cb_t)(wJob_t job_id, void *parm);

/*
 * Parameters describing a job request
 */
typedef struct {
    media_size_t media_size;
    media_type_t media_type;
    duplex_t duplex;
    duplex_dry_time_t dry_time;
    color_space_t color_space;
    media_tray_t media_tray;
    unsigned int num_copies;
    bool borderless;
    unsigned int render_flags;
    float job_top_margin;
    float job_left_margin;
    float job_right_margin;
    float job_bottom_margin;

    bool renderInReverseOrder;

    // these values are pixels
    unsigned int print_top_margin;
    unsigned int print_left_margin;
    unsigned int print_right_margin;
    unsigned int print_bottom_margin;

    // these values are in pixels
    unsigned int pixel_units;
    unsigned int width;
    unsigned int height;
    unsigned int printable_area_width;
    unsigned int printable_area_height;
    unsigned int strip_height;

    bool cancelled;
    bool last_page;
    int page_num;
    int copy_num;
    int copy_page_num;
    int page_corrupted;
    bool page_printing;
    bool page_backside;

    bool media_size_name;

    // these values are in inches
    float page_width;
    float page_height;
    float page_top_margin;
    float page_left_margin;
    float page_right_margin;
    float page_bottom_margin;

    const char *print_format;
    char *page_range;
    pcl_t pcl_type;
    void *plugin_data;
    bool ipp_1_0_supported;
    bool ipp_2_0_supported;
    bool epcl_ipp_supported;
    bool accepts_pclm;
    bool accepts_pdf;
    bool copies_supported;
    const char *useragent;
    char docCategory[10];
    const char *media_default;

    // IPP max job-name is 2**31 - 1, we set a shorter limit
    char job_name[MAX_ID_STRING_LENGTH + 1];
    char job_originating_user_name[MAX_NAME_LENGTH + 1];
    int pdf_render_resolution;
    bool accepts_app_name;
    bool accepts_app_version;
    bool accepts_os_name;
    bool accepts_os_version;
} wprint_job_params_t;

/*
 * Parameters defining how to reach a remote printing service
 */
typedef struct {
    const char *printer_addr;
    const char *uri_path;
    const char *uri_scheme;
    int port_num;
    /* Timeout per retry in milliseconds */
    long timeout;
} wprint_connect_info_t;

/*
 * Current state of a queued job
 */
typedef enum {
    JOB_QUEUED = 1,
    JOB_RUNNING,
    JOB_BLOCKED,
    JOB_DONE
} wprint_job_state_t;

typedef struct {
    wprint_job_state_t state;
    unsigned int blocked_reasons;
    int job_done_result;
} wprint_job_callback_params_t;

typedef enum {
    PRIORITY_PASSTHRU = 1,
    PRIORITY_LOCAL,
} wprint_priority_t;

/* Forward declaration (actual definition in ifc_print_job.h) */
struct ifc_print_job_st;

/*
 * Defines an interface for delivering print jobs
 */
typedef struct {
    uint32 version;
    wprint_priority_t priority;

    char const **(*get_mime_types)(void);

    char const **(*get_print_formats)(void);

    status_t (*start_job)(wJob_t job_handle, const ifc_wprint_t *wprint_ifc,
            const struct ifc_print_job_st *job_ifc, wprint_job_params_t *job_params);

    status_t (*print_page)(wprint_job_params_t *job_params, const char *mime_type,
            const char *pathname);

    status_t (*print_blank_page)(wJob_t job_handle,
            wprint_job_params_t *job_params);

    status_t (*end_job)(wprint_job_params_t *job_params);
} wprint_plugin_t;

/*
 * Initialize the wprint system. Identify and gather capabilities of available plug-ins.
 * Returns the number of plugins found or ERROR.
 */
int wprintInit(void);

/*
 * Call to test if wprint is running or has been shut down.
 */
bool wprintIsRunning();

/*
 * Gets the capabilities of the specified printer.
 */
status_t wprintGetCapabilities(const wprint_connect_info_t *connect_info,
        printer_capabilities_t *printer_cap);

/*
 * Fills in the job params structure with default values.
 */
status_t wprintGetDefaultJobParams(wprint_job_params_t *job_params);

/*
 * Fills in the job params structure with values in accordance with printer capabilities.
 */
status_t wprintGetFinalJobParams(wprint_job_params_t *job_param,
        const printer_capabilities_t *printer_cap);

/*
 * Called once per job at the start of the job. Returns a print job handle that is used in
 * other functions of this library. Returns WPRINT_BAD_JOB_HANDLE for errors.
 */
wJob_t wprintStartJob(const char *printer_addr, port_t port_num,
        const wprint_job_params_t *job_params, const printer_capabilities_t *printer_cap,
        const char *mime_type, const char *pathname, wprint_status_cb_t cb_fn,
        const char *debugDir, const char *scheme);

/*
 * Sent once per job at the end of the job. A current print job must end for the next one
 * to start.
 */
status_t wprintEndJob(wJob_t job_handle);

/*
 * Sent once per page of a multi-page job to deliver a page image in a previously
 * specified MIME type. The page_number must increment from 1. last_page flag is true if it
 * is the last page of the job.
 *
 * top/left/right/bottom margin are the incremental per page margins in pixels
 * at the current print resolution that are added on top of the physical page
 * page margins, passing in 0 results in the default page margins being used.
 */
status_t wprintPage(wJob_t job_handle, int page_number, const char *filename, bool last_page,
        bool pdf_page, unsigned int top_margin, unsigned int left_margin,
        unsigned int right_margin, unsigned int bottom_margin);

/*
 * Cancels a spooled or running job. Returns OK or ERROR
 */
status_t wprintCancelJob(wJob_t job_handle);

/*
 * Exits the print subsystem
 */
status_t wprintExit(void);

/*
 * Supplies info about the sending application and OS name
 */
void wprintSetSourceInfo(const char *appName, const char *appVersion, const char *osName);

/* Global variables to hold API, application, and OS details */
extern int g_API_version;
extern char g_osName[MAX_ID_STRING_LENGTH + 1];
extern char g_appName[MAX_ID_STRING_LENGTH + 1];
extern char g_appVersion[MAX_ID_STRING_LENGTH + 1];

#ifdef __cplusplus
}
#endif

#endif // __LIB_WPRINT_H__