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
#include <stdlib.h>
#include <sys/stat.h>
#include <fcntl.h>

#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif
#ifndef __USE_UNIX98
#define __USE_UNIX98
#endif

#include <pthread.h>

#include <semaphore.h>
#include <printer_capabilities_types.h>

#include "ifc_print_job.h"
#include "wprint_debug.h"
#include "plugin_db.h"

#include "ifc_status_monitor.h"

#include "ippstatus_monitor.h"
#include "ippstatus_capabilities.h"
#include "ipp_print.h"
#include "ipphelper.h"

#include "lib_printable_area.h"
#include "wprint_io_plugin.h"
#include "../plugins/media.h"

#define TAG "lib_wprint"

/* As expected by target devices */
#define USERAGENT_PREFIX "wPrintAndroid"

#define USE_PWG_OVER_PCLM 0

#if (USE_PWG_OVER_PCLM != 0)
#define _DEFAULT_PRINT_FORMAT  PRINT_FORMAT_PWG
#define _DEFAULT_PCL_TYPE      PCLPWG
#else // (USE_PWG_OVER_PCLM != 0)
#define _DEFAULT_PRINT_FORMAT  PRINT_FORMAT_PCLM
#define _DEFAULT_PCL_TYPE      PCLm
#endif // (USE_PWG_OVER_PCLM != 0)

#define _MAX_SPOOLED_JOBS     100
#define _MAX_MSGS             (_MAX_SPOOLED_JOBS * 5)

#define _MAX_PAGES_PER_JOB   1000

#define MAX_IDLE_WAIT        (5 * 60)

#define DEFAULT_RESOLUTION   (300)

// When searching for a supported resolution this is the max resolution we will consider.
#define MAX_SUPPORTED_RESOLUTION (720)

#define MAX_DONE_WAIT (5 * 60)
#define MAX_START_WAIT (45)

#define IO_PORT_FILE   0

/*
 * The following macros allow for up to 8 bits (256) for spooled job id#s and
 * 24 bits (16 million) of a running sequence number to provide a reasonably
 * unique job handle
 */

// _ENCODE_HANDLE() is only called from _get_handle()
#define _ENCODE_HANDLE(X) ( (((++_running_number) & 0xffffff) << 8) | ((X) & 0xff) )
#define _DECODE_HANDLE(X) ((X) & 0xff)

#undef snprintf
#undef vsnprintf

typedef enum {
    JOB_STATE_FREE, // queue element free
    JOB_STATE_QUEUED, // job queued and waiting to be run
    JOB_STATE_RUNNING, // job running (printing)
    JOB_STATE_BLOCKED, // print job blocked due to printer stall/error
    JOB_STATE_CANCEL_REQUEST, // print job cancelled by user,
    JOB_STATE_CANCELLED, // print job cancelled by user, waiting to be freed
    JOB_STATE_COMPLETED, // print job completed successfully, waiting to be freed
    JOB_STATE_ERROR, // job could not be run due to error
    JOB_STATE_CORRUPTED, // job could not be run due to error

    NUM_JOB_STATES
} _job_state_t;

typedef enum {
    TOP_MARGIN = 0,
    LEFT_MARGIN,
    RIGHT_MARGIN,
    BOTTOM_MARGIN,

    NUM_PAGE_MARGINS
} _page_margins_t;

typedef enum {
    MSG_RUN_JOB, MSG_QUIT,
} wprint_msg_t;

typedef struct {
    wprint_msg_t id;
    wJob_t job_id;
} _msg_t;

/*
 * Define an entry in the job queue
 */
typedef struct {
    wJob_t job_handle;
    _job_state_t job_state;
    unsigned int blocked_reasons;
    wprint_status_cb_t cb_fn;
    char *printer_addr;
    port_t port_num;
    wprint_plugin_t *plugin;
    ifc_print_job_t *print_ifc;
    char *mime_type;
    char *pathname;
    bool is_dir;
    bool last_page_seen;
    int num_pages;
    msg_q_id pageQ;
    msg_q_id saveQ;

    wprint_job_params_t job_params;
    bool cancel_ok;
    bool use_secure_uri;

    const ifc_status_monitor_t *status_ifc;
    char debug_path[MAX_PATHNAME_LENGTH + 1];
    char printer_uri[1024];
    int job_debug_fd;
    int page_debug_fd;
} _job_queue_t;

/*
 * An entry for queued pages
 */
typedef struct {
    int page_num;
    bool pdf_page;
    bool last_page;
    bool corrupted;
    char filename[MAX_PATHNAME_LENGTH + 1];
    unsigned int top_margin;
    unsigned int left_margin;
    unsigned int right_margin;
    unsigned int bottom_margin;
} _page_t;

/*
 * Entry for a registered plugin
 */
typedef struct {
    port_t port_num;
    const wprint_io_plugin_t *io_plugin;
} _io_plugin_t;

static _job_queue_t _job_queue[_MAX_SPOOLED_JOBS];
static msg_q_id _msgQ;

static pthread_t _job_status_tid;
static pthread_t _job_tid;

static pthread_mutex_t _q_lock;
static pthread_mutexattr_t _q_lock_attr;

static sem_t _job_end_wait_sem;
static sem_t _job_start_wait_sem;

static _io_plugin_t _io_plugins[2];

char g_osName[MAX_ID_STRING_LENGTH + 1] = {0};
char g_appName[MAX_ID_STRING_LENGTH + 1] = {0};
char g_appVersion[MAX_ID_STRING_LENGTH + 1] = {0};

/*
 * Convert a pcl_t type to a human-readable string
 */
static char *getPCLTypeString(pcl_t pclenum) {
    switch (pclenum) {
        case PCLNONE:
            return "PCL_NONE";
        case PCLm:
            return "PCLm";
        case PCLJPEG:
            return "PCL_JPEG";
        case PCLPWG:
            return "PWG-Raster";
        default:
            return "unkonwn PCL Type";
    }
}

/*
 * Return a _job_queue_t item by its job_handle or NULL if not found.
 */
static _job_queue_t *_get_job_desc(wJob_t job_handle) {
    unsigned long index;
    if (job_handle == WPRINT_BAD_JOB_HANDLE) {
        return NULL;
    }
    index = _DECODE_HANDLE(job_handle);
    if ((index < _MAX_SPOOLED_JOBS) && (_job_queue[index].job_handle == job_handle) &&
            (_job_queue[index].job_state != JOB_STATE_FREE)) {
        return (&_job_queue[index]);
    } else {
        return NULL;
    }
}

/*
 * Functions below to fill out the _debug_stream_ifc interface
 */

static void _stream_dbg_end_job(wJob_t job_handle) {
    _job_queue_t *jq = _get_job_desc(job_handle);
    if (jq && (jq->job_debug_fd >= 0)) {
        close(jq->job_debug_fd);
        jq->job_debug_fd = -1;
    }
}

static void _stream_dbg_start_job(wJob_t job_handle, const char *ext) {
    _stream_dbg_end_job(job_handle);
    _job_queue_t *jq = _get_job_desc(job_handle);
    if (jq && jq->debug_path[0]) {
        char filename[MAX_PATHNAME_LENGTH + 1];
        snprintf(filename, MAX_PATHNAME_LENGTH, "%s/jobstream.%s", jq->debug_path, ext);
        filename[MAX_PATHNAME_LENGTH] = 0;
        jq->job_debug_fd = open(filename, O_CREAT | O_TRUNC | O_WRONLY, 0600);
    }
}

static void _stream_dbg_job_data(wJob_t job_handle, const unsigned char *buff,
        unsigned long nbytes) {
    _job_queue_t *jq = _get_job_desc(job_handle);
    ssize_t bytes_written;
    if (jq && (jq->job_debug_fd >= 0)) {
        while (nbytes > 0) {
            bytes_written = write(jq->job_debug_fd, buff, nbytes);
            if (bytes_written < 0) {
                return;
            }
            nbytes -= bytes_written;
            buff += bytes_written;
        }
    }
}

static void _stream_dbg_end_page(wJob_t job_handle) {
    _job_queue_t *jq = _get_job_desc(job_handle);
    if (jq && (jq->page_debug_fd >= 0)) {
        close(jq->page_debug_fd);
        jq->page_debug_fd = -1;
    }
}

static void _stream_dbg_page_data(wJob_t job_handle, const unsigned char *buff,
        unsigned long nbytes) {
    _job_queue_t *jq = _get_job_desc(job_handle);
    ssize_t bytes_written;
    if (jq && (jq->page_debug_fd >= 0)) {
        while (nbytes > 0) {
            bytes_written = write(jq->page_debug_fd, buff, nbytes);
            if (bytes_written < 0) {
                return;
            }
            nbytes -= bytes_written;
            buff += bytes_written;
        }
    }
}

#define PPM_IDENTIFIER "P6\n"
#define PPM_HEADER_LENGTH 128

static void _stream_dbg_start_page(wJob_t job_handle, int page_number, int width, int height) {
    _stream_dbg_end_page(job_handle);
    _job_queue_t *jq = _get_job_desc(job_handle);
    if (jq && jq->debug_path[0]) {
        union {
            char filename[MAX_PATHNAME_LENGTH + 1];
            char ppm_header[PPM_HEADER_LENGTH + 1];
        } buff;
        snprintf(buff.filename, MAX_PATHNAME_LENGTH, "%s/page%4.4d.ppm", jq->debug_path,
                page_number);
        buff.filename[MAX_PATHNAME_LENGTH] = 0;
        jq->page_debug_fd = open(buff.filename, O_CREAT | O_TRUNC | O_WRONLY, 0600);
        int length = snprintf(buff.ppm_header, sizeof(buff.ppm_header), "%s\n#%*c\n%d %d\n%d\n",
                PPM_IDENTIFIER, 0, ' ', width, height, 255);
        int padding = sizeof(buff.ppm_header) - length;
        snprintf(buff.ppm_header, sizeof(buff.ppm_header), "%s\n#%*c\n%d %d\n%d\n",
                PPM_IDENTIFIER, padding, ' ', width, height, 255);
        _stream_dbg_page_data(job_handle, (const unsigned char *) buff.ppm_header,
                PPM_HEADER_LENGTH);
    }
}

static const ifc_wprint_debug_stream_t _debug_stream_ifc = {
        .debug_start_job = _stream_dbg_start_job, .debug_job_data = _stream_dbg_job_data,
        .debug_end_job = _stream_dbg_end_job, .debug_start_page = _stream_dbg_start_page,
        .debug_page_data = _stream_dbg_page_data, .debug_end_page = _stream_dbg_end_page
};

/*
 * Return the debug stream interface corresponding to the specified job handle
 */
const ifc_wprint_debug_stream_t *getDebugStreamIfc(wJob_t handle) {
    _job_queue_t *jq = _get_job_desc(handle);
    if (jq) {
        return (jq->debug_path[0] == 0) ? NULL : &_debug_stream_ifc;
    }
    return NULL;
}

const ifc_wprint_t _wprint_ifc = {
        .msgQCreate = msgQCreate, .msgQDelete = msgQDelete,
        .msgQSend = msgQSend, .msgQReceive = msgQReceive, .msgQNumMsgs = msgQNumMsgs,
        .get_debug_stream_ifc = getDebugStreamIfc
};

static pcl_t _default_pcl_type = _DEFAULT_PCL_TYPE;

static const ifc_print_job_t *_printer_file_connect(const ifc_wprint_t *wprint_ifc) {
    return printer_connect(IO_PORT_FILE);
}

static const ifc_printer_capabilities_t *_get_caps_ifc(port_t port_num) {
    int i;
    for (i = 0; i < ARRAY_SIZE(_io_plugins); i++) {
        if (_io_plugins[i].port_num == port_num) {
            if (_io_plugins[i].io_plugin == NULL) {
                return NULL;
            }
            if (_io_plugins[i].io_plugin->getCapsIFC == NULL) {
                return NULL;
            } else {
                return (_io_plugins[i].io_plugin->getCapsIFC(&_wprint_ifc));
            }
        }
    }
    return NULL;
}

static const ifc_status_monitor_t *_get_status_ifc(port_t port_num) {
    int i;
    for (i = 0; i < ARRAY_SIZE(_io_plugins); i++) {
        if (_io_plugins[i].port_num == port_num) {
            if (_io_plugins[i].io_plugin == NULL) {
                return NULL;
            }
            if (_io_plugins[i].io_plugin->getStatusIFC == NULL) {
                return NULL;
            } else {
                return (_io_plugins[i].io_plugin->getStatusIFC(&_wprint_ifc));
            }
        }
    }
    return NULL;
}

static const ifc_print_job_t *_get_print_ifc(port_t port_num) {
    int i;
    for (i = 0; i < ARRAY_SIZE(_io_plugins); i++) {
        if (_io_plugins[i].port_num == port_num) {
            if (_io_plugins[i].io_plugin == NULL) {
                return NULL;
            }
            if (_io_plugins[i].io_plugin->getPrintIFC == NULL) {
                return NULL;
            } else {
                return (_io_plugins[i].io_plugin->getPrintIFC(&_wprint_ifc));
            }
        }
    }
    return NULL;
}

/*
 * Lock the semaphore for this module
 */
static void _lock(void) {
    pthread_mutex_lock(&_q_lock);
}

/*
 * Unlock the semaphore for this module
 */
static void _unlock(void) {
    pthread_mutex_unlock(&_q_lock);
}

static wJob_t _get_handle(void) {
    static unsigned long _running_number = 0;
    wJob_t job_handle = WPRINT_BAD_JOB_HANDLE;
    int i, index, size;
    char *ptr;

    for (i = 0; i < _MAX_SPOOLED_JOBS; i++) {
        index = (i + _running_number) % _MAX_SPOOLED_JOBS;

        if (_job_queue[index].job_state == JOB_STATE_FREE) {
            size = MAX_MIME_LENGTH + MAX_PRINTER_ADDR_LENGTH + MAX_PATHNAME_LENGTH + 4;
            ptr = malloc(size);
            if (ptr) {
                memset(&_job_queue[index], 0, sizeof(_job_queue_t));
                memset(ptr, 0, size);

                _job_queue[index].job_debug_fd = -1;
                _job_queue[index].page_debug_fd = -1;
                _job_queue[index].printer_addr = ptr;

                ptr += (MAX_PRINTER_ADDR_LENGTH + 1);
                _job_queue[index].mime_type = ptr;
                ptr += (MAX_MIME_LENGTH + 1);
                _job_queue[index].pathname = ptr;

                _job_queue[index].job_state = JOB_STATE_QUEUED;
                _job_queue[index].job_handle = _ENCODE_HANDLE(index);

                job_handle = _job_queue[index].job_handle;
            }
            break;
        }
    }
    return job_handle;
}

static int _recycle_handle(wJob_t job_handle) {
    _job_queue_t *jq = _get_job_desc(job_handle);

    if (jq == NULL) {
        return ERROR;
    } else if ((jq->job_state == JOB_STATE_CANCELLED) || (jq->job_state == JOB_STATE_ERROR) ||
            (jq->job_state == JOB_STATE_CORRUPTED) || (jq->job_state == JOB_STATE_COMPLETED)) {
        if (jq->print_ifc != NULL) {
            jq->print_ifc->destroy(jq->print_ifc);
        }

        jq->print_ifc = NULL;
        if (jq->status_ifc != NULL) {
            jq->status_ifc->destroy(jq->status_ifc);
        }
        jq->status_ifc = NULL;
        if (jq->job_params.useragent != NULL) {
            free((void *) jq->job_params.useragent);
        }
        free(jq->printer_addr);
        jq->job_state = JOB_STATE_FREE;
        if (jq->job_debug_fd != -1) {
            close(jq->job_debug_fd);
        }
        jq->job_debug_fd = -1;
        if (jq->page_debug_fd != -1) {
            close(jq->page_debug_fd);
        }
        jq->page_debug_fd = -1;
        jq->debug_path[0] = 0;

        return OK;
    } else {
        return ERROR;
    }
}

/*
 * Stops the job status thread if it exists
 */
static int _stop_status_thread(_job_queue_t *jq) {
    if (!pthread_equal(_job_status_tid, pthread_self()) && (jq && jq->status_ifc)) {
        (jq->status_ifc->stop)(jq->status_ifc);
        _unlock();
        pthread_join(_job_status_tid, 0);
        _lock();
        _job_status_tid = pthread_self();
        return OK;
    } else {
        return ERROR;
    }
}

/*
 * Handles a new status message from the printer. Based on the status of wprint and the printer,
 * this function will start/end a job, send another page, or return blocking errors.
 */
static void _job_status_callback(const printer_state_dyn_t *new_status,
        const printer_state_dyn_t *old_status, void *param) {
    wprint_job_callback_params_t cb_param;
    _job_queue_t *jq = (_job_queue_t *) param;
    unsigned int i, blocked_reasons;
    print_status_t statusnew, statusold;

    statusnew = new_status->printer_status & ~PRINTER_IDLE_BIT;
    statusold = old_status->printer_status & ~PRINTER_IDLE_BIT;

    LOGD("_job_status_callback(): current printer state: %d", statusnew);
    blocked_reasons = 0;
    for (i = 0; i <= PRINT_STATUS_MAX_STATE; i++) {
        if (new_status->printer_reasons[i] == PRINT_STATUS_MAX_STATE) {
            break;
        }
        LOGD("_job_status_callback(): blocking reason %d: %d", i, new_status->printer_reasons[i]);
        blocked_reasons |= (1 << new_status->printer_reasons[i]);
    }

    switch (statusnew) {
        case PRINT_STATUS_UNKNOWN:
            if ((new_status->printer_reasons[0] == PRINT_STATUS_OFFLINE)
                    || (new_status->printer_reasons[0] == PRINT_STATUS_UNKNOWN)) {
                sem_post(&_job_start_wait_sem);
                sem_post(&_job_end_wait_sem);
                _lock();
                if ((new_status->printer_reasons[0] == PRINT_STATUS_OFFLINE)
                        && ((jq->print_ifc != NULL) && (jq->print_ifc->enable_timeout != NULL))) {
                    jq->print_ifc->enable_timeout(jq->print_ifc, 1);
                }
                _unlock();
            }
            break;

        case PRINT_STATUS_IDLE:
            if ((statusold > PRINT_STATUS_IDLE) || (statusold == PRINT_STATUS_CANCELLED)) {
                // Print is over but the job wasn't ended correctly
                if (jq->is_dir && !jq->last_page_seen) {
                    wprintPage(jq->job_handle, jq->num_pages + 1, NULL, true, false, 0, 0, 0, 0);
                }
                sem_post(&_job_end_wait_sem);
            }
            break;

        case PRINT_STATUS_CANCELLED:
            sem_post(&_job_start_wait_sem);
            if ((jq->print_ifc != NULL) && (jq->print_ifc->enable_timeout != NULL)) {
                jq->print_ifc->enable_timeout(jq->print_ifc, 1);
            }
            if (statusold != PRINT_STATUS_CANCELLED) {
                LOGI("status requested job cancel");
                if (new_status->printer_reasons[0] == PRINT_STATUS_OFFLINE) {
                    sem_post(&_job_start_wait_sem);
                    sem_post(&_job_end_wait_sem);
                    if ((jq->print_ifc != NULL) && (jq->print_ifc->enable_timeout != NULL)) {
                        jq->print_ifc->enable_timeout(jq->print_ifc, 1);
                    }
                }
                _lock();
                jq->job_params.cancelled = true;
                _unlock();
            }
            if (new_status->printer_reasons[0] == PRINT_STATUS_OFFLINE) {
                sem_post(&_job_start_wait_sem);
                sem_post(&_job_end_wait_sem);
            }
            break;

        case PRINT_STATUS_PRINTING:
            sem_post(&_job_start_wait_sem);
            _lock();
            if ((jq->job_state != JOB_STATE_RUNNING) || (jq->blocked_reasons != blocked_reasons)) {
                jq->job_state = JOB_STATE_RUNNING;
                jq->blocked_reasons = blocked_reasons;
                if (jq->cb_fn) {
                    cb_param.state = JOB_RUNNING;
                    cb_param.blocked_reasons = jq->blocked_reasons;
                    cb_param.job_done_result = OK;

                    jq->cb_fn(jq->job_handle, (void *) &cb_param);
                }
            }
            _unlock();
            break;

        case PRINT_STATUS_UNABLE_TO_CONNECT:
            sem_post(&_job_start_wait_sem);
            _lock();
            _stop_status_thread(jq);

            jq->blocked_reasons = blocked_reasons;
            jq->job_params.cancelled = true;
            jq->job_state = JOB_STATE_ERROR;
            if (jq->cb_fn) {
                cb_param.state = JOB_DONE;
                cb_param.blocked_reasons = blocked_reasons;
                cb_param.job_done_result = ERROR;

                jq->cb_fn(jq->job_handle, (void *) &cb_param);
            }

            if (jq->print_ifc != NULL) {
                jq->print_ifc->destroy(jq->print_ifc);
                jq->print_ifc = NULL;
            }

            if (jq->status_ifc != NULL) {
                jq->status_ifc->destroy(jq->status_ifc);
                jq->status_ifc = NULL;
            }

            _unlock();
            sem_post(&_job_end_wait_sem);
            break;

        default:
            // an error has occurred, report it back to the client
            sem_post(&_job_start_wait_sem);
            _lock();

            if ((jq->job_state != JOB_STATE_BLOCKED) || (jq->blocked_reasons != blocked_reasons)) {
                jq->job_state = JOB_STATE_BLOCKED;
                jq->blocked_reasons = blocked_reasons;
                if (jq->cb_fn) {
                    cb_param.state = JOB_BLOCKED;
                    cb_param.blocked_reasons = blocked_reasons;
                    cb_param.job_done_result = OK;

                    jq->cb_fn(jq->job_handle, (void *) &cb_param);
                }
            }
            _unlock();
            break;
    }
}

static void *_job_status_thread(void *param) {
    _job_queue_t *jq = (_job_queue_t *) param;
    (jq->status_ifc->start)(jq->status_ifc, _job_status_callback, param);
    return NULL;
}

static int _start_status_thread(_job_queue_t *jq) {
    sigset_t allsig, oldsig;
    int result = ERROR;

    if ((jq == NULL) || (jq->status_ifc == NULL)) {
        return result;
    }

    result = OK;
    sigfillset(&allsig);
#if CHECK_PTHREAD_SIGMASK_STATUS
    result = pthread_sigmask(SIG_SETMASK, &allsig, &oldsig);
#else // else CHECK_PTHREAD_SIGMASK_STATUS
    pthread_sigmask(SIG_SETMASK, &allsig, &oldsig);
#endif // CHECK_PTHREAD_SIGMASK_STATUS
    if (result == OK) {
        result = pthread_create(&_job_status_tid, 0, _job_status_thread, jq);
        if ((result == ERROR) && (_job_status_tid != pthread_self())) {
#if USE_PTHREAD_CANCEL
            pthread_cancel(_job_status_tid);
#else // else USE_PTHREAD_CANCEL
            pthread_kill(_job_status_tid, SIGKILL);
#endif // USE_PTHREAD_CANCEL
            _job_status_tid = pthread_self();
        }
    }

    if (result == OK) {
        sched_yield();
#if CHECK_PTHREAD_SIGMASK_STATUS
        result = pthread_sigmask(SIG_SETMASK, &oldsig, 0);
#else // else CHECK_PTHREAD_SIGMASK_STATUS
        pthread_sigmask(SIG_SETMASK, &oldsig, 0);
#endif // CHECK_PTHREAD_SIGMASK_STATUS
    }
    return result;
}

/*
 * Runs a print job. Contains logic for what to do given different printer statuses.
 */
static void *_job_thread(void *param) {
    wprint_job_callback_params_t cb_param;
    _msg_t msg;
    wJob_t job_handle;
    _job_queue_t *jq;
    _page_t page;
    int i;
    status_t job_result;
    int corrupted = 0;

    while (OK == msgQReceive(_msgQ, (char *) &msg, sizeof(msg), WAIT_FOREVER)) {
        if (msg.id == MSG_RUN_JOB) {
            LOGI("_job_thread(): Received message: MSG_RUN_JOB");
        } else {
            LOGI("_job_thread(): Received message: MSG_QUIT");
        }

        if (msg.id == MSG_QUIT) {
            break;
        }

        job_handle = msg.job_id;

        //  check if this is a valid job_handle that is still active
        _lock();

        jq = _get_job_desc(job_handle);

        //  set state to running and invoke the plugin, there is one
        if (jq) {
            if (jq->job_state != JOB_STATE_QUEUED) {
                _unlock();
                continue;
            }
            corrupted = 0;
            job_result = OK;
            jq->job_params.plugin_data = NULL;

            // clear out the semaphore just in case
            while (sem_trywait(&_job_start_wait_sem) == OK) {
            }
            while (sem_trywait(&_job_end_wait_sem) == OK) {
            }

            // initialize the status ifc
            if (jq->status_ifc != NULL) {
                wprint_connect_info_t connect_info;
                connect_info.printer_addr = jq->printer_addr;
                connect_info.uri_path = jq->printer_uri;
                connect_info.port_num = jq->port_num;
                if (jq->use_secure_uri) {
                    connect_info.uri_scheme = IPPS_PREFIX;
                } else {
                    connect_info.uri_scheme = IPP_PREFIX;
                }
                connect_info.timeout = DEFAULT_IPP_TIMEOUT;
                jq->status_ifc->init(jq->status_ifc, &connect_info);
            }
            // wait for the printer to be idle
            if ((jq->status_ifc != NULL) && (jq->status_ifc->get_status != NULL)) {
                int retry = 0;
                int loop = 1;
                printer_state_dyn_t printer_state;
                do {
                    print_status_t status;
                    jq->status_ifc->get_status(jq->status_ifc, &printer_state);
                    status = printer_state.printer_status & ~PRINTER_IDLE_BIT;

                    switch (status) {
                        case PRINT_STATUS_IDLE:
                            printer_state.printer_status = PRINT_STATUS_IDLE;
                            jq->blocked_reasons = 0;
                            loop = 0;
                            break;
                        case PRINT_STATUS_UNKNOWN:
                            if (printer_state.printer_reasons[0] == PRINT_STATUS_UNKNOWN) {
                                LOGE("PRINTER STATUS UNKNOWN - Ln 747 libwprint.c");
                                // no status available, break out and hope for the best
                                printer_state.printer_status = PRINT_STATUS_IDLE;
                                loop = 0;
                                break;
                            }
                        case PRINT_STATUS_SVC_REQUEST:
                            if ((printer_state.printer_reasons[0] == PRINT_STATUS_UNABLE_TO_CONNECT)
                                    || (printer_state.printer_reasons[0] == PRINT_STATUS_OFFLINE)) {
                                LOGD("_job_thread: Received an Unable to Connect message");
                                jq->blocked_reasons = BLOCKED_REASON_UNABLE_TO_CONNECT;
                                loop = 0;
                                break;
                            }
                        default:
                            if (printer_state.printer_status & PRINTER_IDLE_BIT) {
                                LOGD("printer blocked but appears to be in an idle state. "
                                        "Allowing job to proceed");
                                printer_state.printer_status = PRINT_STATUS_IDLE;
                                loop = 0;
                                break;
                            } else if (retry >= MAX_IDLE_WAIT) {
                                jq->blocked_reasons |= BLOCKED_REASONS_PRINTER_BUSY;
                                loop = 0;
                            } else if (!jq->job_params.cancelled) {
                                int blocked_reasons = 0;
                                for (i = 0; i <= PRINT_STATUS_MAX_STATE; i++) {
                                    if (printer_state.printer_reasons[i] ==
                                            PRINT_STATUS_MAX_STATE) {
                                        break;
                                    }
                                    blocked_reasons |= (1 << printer_state.printer_reasons[i]);
                                }
                                if (blocked_reasons == 0) {
                                    blocked_reasons |= BLOCKED_REASONS_PRINTER_BUSY;
                                }

                                if ((jq->job_state != JOB_STATE_BLOCKED) ||
                                        (jq->blocked_reasons != blocked_reasons)) {
                                    jq->job_state = JOB_STATE_BLOCKED;
                                    jq->blocked_reasons = blocked_reasons;
                                    if (jq->cb_fn) {
                                        cb_param.state = JOB_BLOCKED;
                                        cb_param.blocked_reasons = blocked_reasons;
                                        cb_param.job_done_result = OK;

                                        jq->cb_fn(jq->job_handle, (void *) &cb_param);
                                    }
                                }
                                _unlock();
                                sleep(1);
                                _lock();
                                retry++;
                            }
                            break;
                    }
                    if (jq->job_params.cancelled) {
                        loop = 0;
                    }
                } while (loop);

                if (jq->job_params.cancelled) {
                    job_result = CANCELLED;
                } else {
                    job_result = (((printer_state.printer_status & ~PRINTER_IDLE_BIT) ==
                            PRINT_STATUS_IDLE) ? OK : ERROR);
                }
            }

            _job_status_tid = pthread_self();
            if (job_result == OK) {
                if (jq->print_ifc) {
                    job_result = jq->print_ifc->init(jq->print_ifc, jq->printer_addr,
                            jq->port_num, jq->printer_uri, jq->use_secure_uri);
                    if (job_result == ERROR) {
                        jq->blocked_reasons = BLOCKED_REASON_UNABLE_TO_CONNECT;
                    }
                }
            }
            if (job_result == OK) {
                _start_status_thread(jq);
            }

            /*  call the plugin's start_job method, if no other job is running
             use callback to notify the client */

            if ((job_result == OK) && jq->cb_fn) {
                cb_param.state = JOB_RUNNING;
                cb_param.blocked_reasons = 0;
                cb_param.job_done_result = OK;

                jq->cb_fn(job_handle, (void *) &cb_param);
            }

            jq->job_params.page_num = -1;
            if (job_result == OK) {
                if (jq->print_ifc != NULL) {
                    LOGD("_job_thread: Calling validate_job");
                    if (jq->print_ifc->validate_job != NULL) {
                        job_result = jq->print_ifc->validate_job(jq->print_ifc, &jq->job_params);
                    }

                    /* PDF format plugin's start_job and end_job are to be called for each copy,
                     * inside the for-loop for num_copies.
                     */

                    // Do not call start_job unless validate_job returned OK
                    if ((job_result == OK) && (jq->print_ifc->start_job != NULL) &&
                            (strcmp(jq->job_params.print_format, PRINT_FORMAT_PDF) != 0)) {
                        jq->print_ifc->start_job(jq->print_ifc, &jq->job_params);
                    }
                }

                // Do not call start_job unless validate_job returned OK
                if (job_result == OK && jq->plugin->start_job != NULL) {
                    job_result = jq->plugin->start_job(job_handle, (void *) &_wprint_ifc,
                            (void *) jq->print_ifc, &(jq->job_params));
                }
            }

            if (job_result == OK) {
                jq->job_params.page_num = 0;
            }

            // multi-page print job
            if (jq->is_dir && (job_result == OK)) {
                int per_copy_page_num;
                for (i = 0; (i < jq->job_params.num_copies) &&
                        ((job_result == OK) || (job_result == CORRUPT)) &&
                        (!jq->job_params.cancelled); i++) {
                    if ((i > 0) &&
                            jq->job_params.copies_supported &&
                            (strcmp(jq->job_params.print_format, PRINT_FORMAT_PDF) == 0)) {
                        LOGD("_job_thread multi_page: breaking out copies supported");
                        break;
                    }
                    bool pdf_printed = false;
                    if (jq->print_ifc->start_job != NULL &&
                            (strcmp(jq->job_params.print_format, PRINT_FORMAT_PDF) == 0)) {
                        jq->print_ifc->start_job(jq->print_ifc, &jq->job_params);
                    }

                    per_copy_page_num = 0;
                    jq->job_state = JOB_STATE_RUNNING;

                    // while there is a page to print
                    _unlock();

                    while (OK == msgQReceive(jq->pageQ, (char *) &page, sizeof(page),
                            WAIT_FOREVER)) {
                        _lock();

                        // check for any printing problems so far
                        if (jq->print_ifc->check_status) {
                            if (jq->print_ifc->check_status(jq->print_ifc) == ERROR) {
                                job_result = ERROR;
                                break;
                            }
                        }

                        /* take empty filename as cue to break out of the loop
                         * but we have to do last_page processing
                         */

                        // all copies are clubbed together as a single print job
                        if (page.last_page && ((i == jq->job_params.num_copies - 1) ||
                                (jq->job_params.copies_supported &&
                                        strcmp(jq->job_params.print_format,
                                                PRINT_FORMAT_PDF) == 0))) {
                            jq->job_params.last_page = page.last_page;
                        } else {
                            jq->job_params.last_page = false;
                        }

                        if (strlen(page.filename) > 0) {
                            per_copy_page_num++;
                            {
                                jq->job_params.page_num++;
                            }
                            if (page.pdf_page) {
                                jq->job_params.page_num = page.page_num;
                            } else {
                                jq->job_params.page_num = per_copy_page_num;
                            }

                            // setup page margin information
                            jq->job_params.print_top_margin += page.top_margin;
                            jq->job_params.print_left_margin += page.left_margin;
                            jq->job_params.print_right_margin += page.right_margin;
                            jq->job_params.print_bottom_margin += page.bottom_margin;

                            jq->job_params.copy_num = (i + 1);
                            jq->job_params.copy_page_num = page.page_num;
                            jq->job_params.page_backside = (per_copy_page_num & 0x1);
                            jq->job_params.page_corrupted = (page.corrupted ? 1 : 0);
                            jq->job_params.page_printing = true;
                            _unlock();

                            if (!page.corrupted) {
                                LOGD("_job_thread(): page not corrupt, calling plugin's print_page"
                                        " function for page #%d", page.page_num);
                                if (strcmp(jq->job_params.print_format, PRINT_FORMAT_PDF) != 0) {
                                    job_result = jq->plugin->print_page(&(jq->job_params),
                                            jq->mime_type,
                                            page.filename);
                                } else if (!pdf_printed) {
                                    // for PDF plugin, print_page prints entire document,
                                    // so need to be called only once
                                    job_result = jq->plugin->print_page(&(jq->job_params),
                                            jq->mime_type,
                                            page.filename);
                                    pdf_printed = true;
                                }
                            } else {
                                LOGD("_job_thread(): page IS corrupt, printing blank page for "
                                        "page #%d", page.page_num);
                                job_result = CORRUPT;
                                if ((jq->job_params.duplex != DUPLEX_MODE_NONE) &&
                                        (jq->plugin->print_blank_page != NULL)) {
                                    jq->plugin->print_blank_page(job_handle, &(jq->job_params));
                                }
                            }
                            _lock();

                            jq->job_params.print_top_margin -= page.top_margin;
                            jq->job_params.print_left_margin -= page.left_margin;
                            jq->job_params.print_right_margin -= page.right_margin;
                            jq->job_params.print_bottom_margin -= page.bottom_margin;
                            jq->job_params.page_printing = false;

                            // make sure we only count corrupted pages once
                            if (page.corrupted == false) {
                                page.corrupted = ((job_result == CORRUPT) ? true : false);
                                corrupted += (job_result == CORRUPT);
                            }
                        }

                        // make sure we always print an even number of pages in duplex jobs
                        if (page.last_page && (jq->job_params.duplex != DUPLEX_MODE_NONE)
                                && (jq->job_params.page_backside)
                                && (jq->plugin->print_blank_page != NULL)) {
                            _unlock();
                            jq->plugin->print_blank_page(job_handle, &(jq->job_params));
                            _lock();
                        }

                        // if multiple copies are requested, save the contents of the pageQ message
                        if (jq->saveQ && !jq->job_params.cancelled && (job_result != ERROR)) {
                            job_result = msgQSend(jq->saveQ, (char *) &page,
                                    sizeof(page), NO_WAIT, MSG_Q_FIFO);

                            // swap pageQ and saveQ
                            if (page.last_page && !jq->job_params.last_page) {
                                msg_q_id tmpQ = jq->pageQ;
                                jq->pageQ = jq->saveQ;
                                jq->saveQ = tmpQ;

                                // defensive programming
                                while (msgQNumMsgs(tmpQ) > 0) {
                                    msgQReceive(tmpQ, (char *) &page, sizeof(page), NO_WAIT);
                                    LOGE("pageQ inconsistencies, discarding page #%d, file %s",
                                            page.page_num, page.filename);
                                }
                            }
                        }

                        if (page.last_page || jq->job_params.cancelled) {
                            // Leave the sempahore locked
                            break;
                        }

                        // unlock to go back to the top of the while loop
                        _unlock();
                    } // while there is another page

                    if ((strcmp(jq->job_params.print_format, PRINT_FORMAT_PDF) == 0) &&
                            (jq->print_ifc->end_job)) {
                        int end_job_result = jq->print_ifc->end_job(jq->print_ifc);
                        if (job_result == OK) {
                            if (end_job_result == ERROR) {
                                job_result = ERROR;
                            } else if (end_job_result == CANCELLED) {
                                job_result = CANCELLED;
                            }
                        }
                    }
                } // for each copy of the job
            } else if (job_result == OK) {
                // single page job
                for (i = 0; ((i < jq->job_params.num_copies) && (job_result == OK)); i++) {
                    if ((i > 0) && jq->job_params.copies_supported &&
                            (strcmp(jq->job_params.print_format, PRINT_FORMAT_PDF) == 0)) {
                        LOGD("_job_thread single_page: breaking out copies supported");
                        break;
                    }

                    // check for any printing problems so far
                    if ((jq->print_ifc != NULL) && (jq->print_ifc->check_status)) {
                        if (jq->print_ifc->check_status(jq->print_ifc) == ERROR) {
                            job_result = ERROR;
                            break;
                        }
                    }

                    jq->job_state = JOB_STATE_RUNNING;
                    jq->job_params.page_num++;
                    jq->job_params.last_page = (i == (jq->job_params.num_copies - 1));
                    jq->job_params.copy_num = (i + 1);
                    jq->job_params.copy_page_num = 1;
                    jq->job_params.page_corrupted = (job_result == CORRUPT);
                    jq->job_params.page_printing = true;

                    _unlock();
                    job_result = jq->plugin->print_page(&(jq->job_params), jq->mime_type,
                            jq->pathname);

                    if ((jq->job_params.duplex != DUPLEX_MODE_NONE)
                            && (jq->plugin->print_blank_page != NULL)) {
                        jq->plugin->print_blank_page(job_handle,
                                &(jq->job_params));
                    }

                    _lock();
                    jq->job_params.page_printing = false;

                    corrupted += (job_result == CORRUPT);
                } // for each copy
            }

            // if we started the job end it
            if (jq->job_params.page_num >= 0) {
                // if the job was cancelled without sending anything through, print a blank sheet
                if ((jq->job_params.page_num == 0)
                        && (jq->plugin->print_blank_page != NULL)) {
                    jq->plugin->print_blank_page(job_handle, &(jq->job_params));
                }
                if (jq->plugin->end_job != NULL) {
                    jq->plugin->end_job(&(jq->job_params));
                }
                if ((jq->print_ifc != NULL) && (jq->print_ifc->end_job) &&
                        (strcmp(jq->job_params.print_format, PRINT_FORMAT_PDF) != 0)) {
                    int end_job_result = jq->print_ifc->end_job(jq->print_ifc);
                    if (job_result == OK) {
                        if (end_job_result == ERROR) {
                            job_result = ERROR;
                        } else if (end_job_result == CANCELLED) {
                            job_result = CANCELLED;
                        }
                    }
                }
            }

            // if we started to print, wait for idle
            if ((jq->job_params.page_num > 0) && (jq->status_ifc != NULL)) {
                int retry, result;
                _unlock();

                for (retry = 0, result = ERROR; ((result == ERROR) && (retry <= MAX_START_WAIT));
                        retry++) {
                    if (retry != 0) {
                        sleep(1);
                    }
                    result = sem_trywait(&_job_start_wait_sem);
                }

                if (result == OK) {
                    for (retry = 0, result = ERROR; ((result == ERROR) && (retry <= MAX_DONE_WAIT));
                            retry++) {
                        if (retry != 0) {
                            _lock();
                            if (jq->job_params.cancelled && !jq->cancel_ok) {
                                /* The user tried to cancel and it either didn't go through
                                 * or the printer doesn't support cancel through an OID.
                                 * Either way it's pointless to sit here waiting for idle when
                                 * may never come, so we'll bail out early
                                 */
                                retry = (MAX_DONE_WAIT + 1);
                            }
                            _unlock();
                            sleep(1);
                            if (retry == MAX_DONE_WAIT) {
                                _lock();
                                if (!jq->job_params.cancelled &&
                                        (jq->blocked_reasons
                                                & (BLOCKED_REASON_OUT_OF_PAPER
                                                        | BLOCKED_REASON_JAMMED
                                                        | BLOCKED_REASON_DOOR_OPEN))) {
                                    retry = (MAX_DONE_WAIT - 1);
                                }
                                _unlock();
                            }
                        }
                        result = sem_trywait(&_job_end_wait_sem);
                    }
                } else {
                    LOGD("_job_thread(): the job never started");
                }
                _lock();
            }

            // make sure page_num doesn't stay as a negative number
            jq->job_params.page_num = MAX(0, jq->job_params.page_num);
            _stop_status_thread(jq);

            if (corrupted != 0) {
                job_result = CORRUPT;
            }

            LOGI("job_thread(): with job_state value: %d ", jq->job_state);
            if ((jq->job_state == JOB_STATE_COMPLETED) || (jq->job_state == JOB_STATE_ERROR)
                    || (jq->job_state == JOB_STATE_CANCELLED)
                    || (jq->job_state == JOB_STATE_CORRUPTED)
                    || (jq->job_state == JOB_STATE_FREE)) {
                LOGI("_job_thread(): job finished early: do not send callback again");
            } else {
                switch (job_result) {
                    case OK:
                        if (!jq->job_params.cancelled) {
                            jq->job_state = JOB_STATE_COMPLETED;
                            jq->blocked_reasons = 0;
                            break;
                        } else {
                            job_result = CANCELLED;
                        }
                    case CANCELLED:
                        jq->job_state = JOB_STATE_CANCELLED;
                        jq->blocked_reasons = BLOCKED_REASONS_CANCELLED;
                        if (!jq->cancel_ok) {
                            jq->blocked_reasons |= BLOCKED_REASON_PARTIAL_CANCEL;
                        }
                        break;
                    case CORRUPT:
                        LOGE("_job_thread(): %d file(s) in the job were corrupted", corrupted);
                        jq->job_state = JOB_STATE_CORRUPTED;
                        jq->blocked_reasons = 0;
                        break;
                    case ERROR:
                    default:
                        LOGE("_job_thread(): ERROR plugin->start_job(%ld): %s => %s", job_handle,
                                jq->mime_type, jq->job_params.print_format);
                        job_result = ERROR;
                        jq->job_state = JOB_STATE_ERROR;
                        break;
                } // job_result

                // end of job callback
                if (jq->cb_fn) {
                    cb_param.state = JOB_DONE;
                    cb_param.blocked_reasons = jq->blocked_reasons;
                    cb_param.job_done_result = job_result;

                    jq->cb_fn(job_handle, (void *) &cb_param);
                }

                if (jq->print_ifc != NULL) {
                    jq->print_ifc->destroy(jq->print_ifc);
                    jq->print_ifc = NULL;
                }

                if (jq->status_ifc != NULL) {
                    jq->status_ifc->destroy(jq->status_ifc);
                    jq->status_ifc = NULL;
                }
            }
        } else {
            LOGI("_job_thread(): job %ld not in queue .. maybe cancelled", job_handle);
        }

        _unlock();
        LOGI("_job_thread(): job finished: %ld", job_handle);
    }

    sem_post(&_job_end_wait_sem);
    return NULL;
}

/*
 * Starts the wprint background job thread
 */
static int _start_thread(void) {
    sigset_t allsig, oldsig;
    int result;

    _job_tid = pthread_self();

    result = OK;
    sigfillset(&allsig);
#if CHECK_PTHREAD_SIGMASK_STATUS
    result = pthread_sigmask(SIG_SETMASK, &allsig, &oldsig);
#else // else CHECK_PTHREAD_SIGMASK_STATUS
    pthread_sigmask(SIG_SETMASK, &allsig, &oldsig);
#endif // CHECK_PTHREAD_SIGMASK_STATUS
    if (result == OK) {
        result = pthread_create(&_job_tid, 0, _job_thread, NULL);
        if ((result == ERROR) && (_job_tid != pthread_self())) {
#if USE_PTHREAD_CANCEL
            pthread_cancel(_job_tid);
#else // else USE_PTHREAD_CANCEL
            pthread_kill(_job_tid, SIGKILL);
#endif // USE_PTHREAD_CANCEL
            _job_tid = pthread_self();
        }
    }

    if (result == OK) {
        sched_yield();
#if CHECK_PTHREAD_SIGMASK_STATUS
        result = pthread_sigmask(SIG_SETMASK, &oldsig, 0);
#else // else CHECK_PTHREAD_SIGMASK_STATUS
        pthread_sigmask(SIG_SETMASK, &oldsig, 0);
#endif // CHECK_PTHREAD_SIGMASK_STATUS
    }

    return result;
}

/*
 * Waits for the job thread to reach a stopped state
 */
static int _stop_thread(void) {
    if (!pthread_equal(_job_tid, pthread_self())) {
        pthread_join(_job_tid, 0);
        _job_tid = pthread_self();
        return OK;
    } else {
        return ERROR;
    }
}

static const wprint_io_plugin_t _file_io_plugin = {
        .version = WPRINT_PLUGIN_VERSION(_INTERFACE_MINOR_VERSION),
        .port_num = PORT_FILE, .getCapsIFC = NULL, .getStatusIFC = NULL,
        .getPrintIFC = _printer_file_connect,};

static const wprint_io_plugin_t _ipp_io_plugin = {
        .version = WPRINT_PLUGIN_VERSION(_INTERFACE_MINOR_VERSION),
        .port_num = PORT_IPP, .getCapsIFC = ipp_status_get_capabilities_ifc,
        .getStatusIFC = ipp_status_get_monitor_ifc, .getPrintIFC = ipp_get_print_ifc,};

static void _setup_io_plugins() {
    _io_plugins[0].port_num = PORT_FILE;
    _io_plugins[0].io_plugin = &_file_io_plugin;

    _io_plugins[1].port_num = PORT_IPP;
    _io_plugins[1].io_plugin = &_ipp_io_plugin;
}

extern wprint_plugin_t *libwprintplugin_pcl_reg(void);

extern wprint_plugin_t *libwprintplugin_pdf_reg(void);

static void _setup_print_plugins() {
    plugin_reset();
    plugin_add(libwprintplugin_pcl_reg());
    plugin_add(libwprintplugin_pdf_reg());
}

bool wprintIsRunning() {
    return _msgQ != 0;
}

int wprintInit(void) {
    int count = 0;

    _setup_print_plugins();
    _setup_io_plugins();

    _msgQ = msgQCreate(_MAX_MSGS, sizeof(_msg_t));

    if (!_msgQ) {
        LOGE("ERROR: cannot create msgQ");
        return ERROR;
    }

    sem_init(&_job_end_wait_sem, 0, 0);
    sem_init(&_job_start_wait_sem, 0, 0);

    signal(SIGPIPE, SIG_IGN); // avoid broken pipe process shutdowns
    pthread_mutexattr_settype(&_q_lock_attr, PTHREAD_MUTEX_RECURSIVE_NP);
    pthread_mutex_init(&_q_lock, &_q_lock_attr);

    if (_start_thread() != OK) {
        LOGE("could not start job thread");
        return ERROR;
    }
    return count;
}

static const printer_capabilities_t _default_cap = {.color = true, .borderless = true,
        .numSupportedMediaSizes = 0, .numSupportedMediaTrays = 0,
        .numSupportedMediaTypes = 0,};

/*
 * Check if a media size is supported
 */
static bool is_supported(media_size_t media_size) {
    int i;
    for (i = 0; i < SUPPORTED_MEDIA_SIZE_COUNT; i++) {
        if (SupportedMediaSizes[i].media_size == media_size) return true;
    }
    return false;
}

/*
 * Checks printers reported media sizes and validates that wprint supports them
 */
static void _validate_supported_media_sizes(printer_capabilities_t *printer_cap) {
    if (printer_cap == NULL) return;

    if (printer_cap->numSupportedMediaSizes == 0) {
        unsigned int i = 0;
        printer_cap->supportedMediaSizes[i++] = ISO_A4;
        printer_cap->supportedMediaSizes[i++] = US_LETTER;
        printer_cap->supportedMediaSizes[i++] = INDEX_CARD_4X6;
        printer_cap->supportedMediaSizes[i++] = INDEX_CARD_5X7;
        printer_cap->numSupportedMediaSizes = i;
    } else {
        unsigned int read, write;
        for (read = write = 0; read < printer_cap->numSupportedMediaSizes; read++) {
            if (is_supported(printer_cap->supportedMediaSizes[read])) {
                printer_cap->supportedMediaSizes[write++] =
                        printer_cap->supportedMediaSizes[read];
            }
        }
        printer_cap->numSupportedMediaSizes = write;
    }
}

/*
 * Checks printers numSupportedMediaTrays. If none, then add Auto.
 */
static void _validate_supported_media_trays(printer_capabilities_t *printer_cap) {
    if (printer_cap == NULL) return;

    if (printer_cap->numSupportedMediaTrays == 0) {
        printer_cap->supportedMediaTrays[0] = TRAY_SRC_AUTO_SELECT;
        printer_cap->numSupportedMediaTrays = 1;
    }
}

/*
 * Add a printer's supported input formats to the capabilities struct
 */
static void _collect_supported_input_formats(printer_capabilities_t *printer_caps) {
    unsigned long long input_formats = 0;
    plugin_get_passthru_input_formats(&input_formats);

    // remove things the printer can't support
    if (!printer_caps->canPrintPDF) {
        input_formats &= ~(1 << INPUT_MIME_TYPE_PDF);
    }
    if (!printer_caps->canPrintPCLm) {
        input_formats &= ~(1 << INPUT_MIME_TYPE_PCLM);
    }
    if (!printer_caps->canPrintPWG) {
        input_formats &= ~(1 << INPUT_MIME_TYPE_PWG);
    }
    printer_caps->supportedInputMimeTypes = input_formats;
}

/*
 * Check the print resolutions supported by the printer and verify that wprint supports them.
 * If nothing is found, the desired resolution is selected.
 */
static unsigned int _findCloseResolutionSupported(int desiredResolution, int maxResolution,
        const printer_capabilities_t *printer_cap) {
    int closeResolution = 0;
    int closeDifference = 0;
    unsigned int index = 0;
    for (index = 0; index < printer_cap->numSupportedResolutions; index++) {
        int resolution = printer_cap->supportedResolutions[index];
        if (resolution == desiredResolution) {
            // An exact match wins.. stop looking.
            return resolution;
        } else {
            int difference = abs(desiredResolution - resolution);
            if ((closeResolution == 0) || (difference < closeDifference)) {
                if (resolution <= maxResolution) {
                    // We found a better match now.. record it but keep looking.
                    closeResolution = resolution;
                    closeDifference = difference;
                }
            }
        }
    }

    // If we get here we did not find an exact match.
    if (closeResolution == 0) {
        // We did not find anything.. just pick the desired value.
        closeResolution = desiredResolution;
    }
    return closeResolution;
}

status_t wprintGetCapabilities(const wprint_connect_info_t *connect_info,
        printer_capabilities_t *printer_cap) {
    LOGD("wprintGetCapabilities: Enter");
    status_t result = ERROR;
    int index;
    int port_num = connect_info->port_num;
    const ifc_printer_capabilities_t *caps_ifc = NULL;

    memcpy(printer_cap, &_default_cap, sizeof(printer_capabilities_t));

    caps_ifc = _get_caps_ifc(((port_num == 0) ? PORT_FILE : PORT_IPP));
    LOGD("wprintGetCapabilities: after getting caps ifc: %p", caps_ifc);
    switch (port_num) {
        case PORT_FILE:
            printer_cap->duplex = 1;
            printer_cap->borderless = 1;
            printer_cap->canPrintPCLm = (_default_pcl_type == PCLm);
            printer_cap->canPrintPWG = (_default_pcl_type == PCLPWG);
            printer_cap->stripHeight = STRIPE_HEIGHT;
            result = OK;
            break;
        default:
            break;
    }

    if (caps_ifc != NULL) {
        caps_ifc->init(caps_ifc, connect_info);
        result = caps_ifc->get_capabilities(caps_ifc, printer_cap);
        caps_ifc->destroy(caps_ifc);
    }

    _validate_supported_media_sizes(printer_cap);
    _collect_supported_input_formats(printer_cap);
    _validate_supported_media_trays(printer_cap);

    printer_cap->isSupported = (printer_cap->canPrintPCLm || printer_cap->canPrintPDF ||
            printer_cap->canPrintPWG);

    if (result == OK) {
        LOGD("\tmake: %s", printer_cap->make);
        LOGD("\thas color: %d", printer_cap->color);
        LOGD("\tcan duplex: %d", printer_cap->duplex);
        LOGD("\tcan rotate back page: %d", printer_cap->canRotateDuplexBackPage);
        LOGD("\tcan print borderless: %d", printer_cap->borderless);
        LOGD("\tcan print pdf: %d", printer_cap->canPrintPDF);
        LOGD("\tcan print pclm: %d", printer_cap->canPrintPCLm);
        LOGD("\tcan print pwg: %d", printer_cap->canPrintPWG);
        LOGD("\tsource application name supported: %d", printer_cap->docSourceAppName);
        LOGD("\tsource application version supported: %d", printer_cap->docSourceAppVersion);
        LOGD("\tsource os name supported: %d", printer_cap->docSourceOsName);
        LOGD("\tsource os version supported: %d", printer_cap->docSourceOsVersion);
        LOGD("\tprinter supported: %d", printer_cap->isSupported);
        LOGD("\tstrip height: %d", printer_cap->stripHeight);
        LOGD("\tinkjet: %d", printer_cap->inkjet);
        LOGD("\tresolutions supported:");
        for (index = 0; index < printer_cap->numSupportedResolutions; index++) {
            LOGD("\t (%d dpi)", printer_cap->supportedResolutions[index]);
        }
    }
    LOGD("wprintGetCapabilities: Exit");
    return result;
}

/*
 * Returns a preferred print format supported by the printer
 */
static char *_get_print_format(const char *mime_type, const wprint_job_params_t *job_params,
        const printer_capabilities_t *cap) {
    char *print_format = NULL;

    errno = OK;

    if (((strcmp(mime_type, MIME_TYPE_PDF) == 0) && cap->canPrintPDF)) {
        // For content type=photo and a printer that supports both PCLm and PDF,
        // prefer PCLm over PDF.
        if (job_params && (strcasecmp(job_params->docCategory, "photo") == 0) &&
                cap->canPrintPCLm) {
            print_format = PRINT_FORMAT_PCLM;
            LOGI("_get_print_format(): print_format switched from PDF to PCLm");
        } else {
            print_format = PRINT_FORMAT_PDF;
        }
    } else if (cap->canPrintPCLm || cap->canPrintPDF) {
        // PCLm is a subset of PDF
        print_format = PRINT_FORMAT_PCLM;
#if (USE_PWG_OVER_PCLM != 0)
        if (cap->canPrintPWG) {
            print_format = PRINT_FORMAT_PWG;
        }
#endif // (USE_PWG_OVER_PCLM != 0)
    } else if (cap->canPrintPWG) {
        print_format = PRINT_FORMAT_PWG;
    } else {
        errno = EBADRQC;
    }

    if (print_format != NULL) {
        LOGI("\t_get_print_format(): print_format: %s", print_format);
    }

    return print_format;
}

status_t wprintGetDefaultJobParams(wprint_job_params_t *job_params) {
    status_t result = ERROR;
    static const wprint_job_params_t _default_job_params = {.print_format = _DEFAULT_PRINT_FORMAT,
            .pcl_type = _DEFAULT_PCL_TYPE, .media_size = US_LETTER, .media_type = MEDIA_PLAIN,
            .duplex = DUPLEX_MODE_NONE, .dry_time = DUPLEX_DRY_TIME_NORMAL,
            .color_space = COLOR_SPACE_COLOR, .media_tray = TRAY_SRC_AUTO_SELECT,
            .pixel_units = DEFAULT_RESOLUTION, .render_flags = 0, .num_copies =1,
            .borderless = false, .cancelled = false, .renderInReverseOrder = false,
            .ipp_1_0_supported = false, .ipp_2_0_supported = false, .epcl_ipp_supported = false,
            .strip_height = STRIPE_HEIGHT, .docCategory = {0},
            .copies_supported = false};

    if (job_params == NULL) return result;

    memcpy(job_params, &_default_job_params, sizeof(_default_job_params));

    return OK;
}

status_t wprintGetFinalJobParams(wprint_job_params_t *job_params,
        const printer_capabilities_t *printer_cap) {
    int i;
    status_t result = ERROR;
    float margins[NUM_PAGE_MARGINS];

    if (job_params == NULL) {
        return result;
    }
    result = OK;

    job_params->accepts_pclm = printer_cap->canPrintPCLm;
    job_params->accepts_pdf = printer_cap->canPrintPDF;
    job_params->media_default = printer_cap->mediaDefault;

    if (printer_cap->ePclIppVersion == 1) {
        job_params->epcl_ipp_supported = true;
    }

    if (printer_cap->canCopy) {
        job_params->copies_supported = true;
    }

    if (printer_cap->ippVersionMajor == 2) {
        job_params->ipp_1_0_supported = true;
        job_params->ipp_2_0_supported = true;
    } else if (printer_cap->ippVersionMajor == 1) {
        job_params->ipp_1_0_supported = true;
        job_params->ipp_2_0_supported = false;
    }

    if (!printer_cap->color) {
        job_params->color_space = COLOR_SPACE_MONO;
    }

    if (printer_cap->canPrintPCLm || printer_cap->canPrintPDF) {
        job_params->pcl_type = PCLm;
#if (USE_PWG_OVER_PCLM != 0)
        if ( printer_cap->canPrintPWG) {
            job_params->pcl_type = PCLPWG;
        }
#endif // (USE_PWG_OVER_PCLM != 0)
    } else if (printer_cap->canPrintPWG) {
        job_params->pcl_type = PCLPWG;
    }

    LOGD("wprintGetFinalJobParams: Using PCL Type %s", getPCLTypeString(job_params->pcl_type));

    // set strip height
    job_params->strip_height = printer_cap->stripHeight;

    // make sure the number of copies is valid
    if (job_params->num_copies <= 0) {
        job_params->num_copies = 1;
    }

    // confirm that the media size is supported
    for (i = 0; i < printer_cap->numSupportedMediaSizes; i++) {
        if (job_params->media_size == printer_cap->supportedMediaSizes[i]) {
            break;
        }
    }

    if (i >= printer_cap->numSupportedMediaSizes) {
        job_params->media_size = ISO_A4;
        job_params->media_tray = TRAY_SRC_AUTO_SELECT;
    }

    // check that we support the media tray
    for (i = 0; i < printer_cap->numSupportedMediaTrays; i++) {
        if (job_params->media_tray == printer_cap->supportedMediaTrays[i]) {
            break;
        }
    }

    // media tray not supported, default to automatic
    if (i >= printer_cap->numSupportedMediaTrays) {
        job_params->media_tray = TRAY_SRC_AUTO_SELECT;
    }

    if (printer_cap->isMediaSizeNameSupported == true) {
        job_params->media_size_name = true;
    } else {
        job_params->media_size_name = false;
    }

    // verify borderless setting
    if ((job_params->borderless == true) && !printer_cap->borderless) {
        job_params->borderless = false;
    }

    // borderless and margins don't get along
    if (job_params->borderless &&
            ((job_params->job_top_margin > 0.0f) || (job_params->job_left_margin > 0.0f) ||
                    (job_params->job_right_margin > 0.0f)
                    || (job_params->job_bottom_margin > 0.0f))) {
        job_params->borderless = false;
    }

    // verify duplex setting
    if ((job_params->duplex != DUPLEX_MODE_NONE) && !printer_cap->duplex) {
        job_params->duplex = DUPLEX_MODE_NONE;
    }

    // borderless and duplex don't get along either
    if (job_params->borderless && (job_params->duplex != DUPLEX_MODE_NONE)) {
        job_params->duplex = DUPLEX_MODE_NONE;
    }

    if ((job_params->duplex == DUPLEX_MODE_BOOK)
            && !printer_cap->canRotateDuplexBackPage) {
        job_params->render_flags |= RENDER_FLAG_ROTATE_BACK_PAGE;
    }

    if (job_params->render_flags & RENDER_FLAG_ROTATE_BACK_PAGE) {
        LOGD("wprintGetFinalJobParams: Duplex is on and device needs back page rotated.");
    }

    if ((job_params->duplex == DUPLEX_MODE_NONE) && !printer_cap->faceDownTray) {
        job_params->renderInReverseOrder = true;
    } else {
        job_params->renderInReverseOrder = false;
    }

    if (job_params->render_flags & RENDER_FLAG_AUTO_SCALE) {
        job_params->render_flags |= AUTO_SCALE_RENDER_FLAGS;
    } else if (job_params->render_flags & RENDER_FLAG_AUTO_FIT) {
        job_params->render_flags |= AUTO_FIT_RENDER_FLAGS;
    }

    job_params->pixel_units = _findCloseResolutionSupported(DEFAULT_RESOLUTION,
            MAX_SUPPORTED_RESOLUTION, printer_cap);

    printable_area_get_default_margins(job_params, printer_cap, &margins[TOP_MARGIN],
            &margins[LEFT_MARGIN], &margins[RIGHT_MARGIN], &margins[BOTTOM_MARGIN]);
    printable_area_get(job_params, margins[TOP_MARGIN], margins[LEFT_MARGIN], margins[RIGHT_MARGIN],
            margins[BOTTOM_MARGIN]);

    job_params->accepts_app_name = printer_cap->docSourceAppName;
    job_params->accepts_app_version = printer_cap->docSourceAppVersion;
    job_params->accepts_os_name = printer_cap->docSourceOsName;
    job_params->accepts_os_version = printer_cap->docSourceOsVersion;

    return result;
}

wJob_t wprintStartJob(const char *printer_addr, port_t port_num,
        const wprint_job_params_t *job_params, const printer_capabilities_t *printer_cap,
        const char *mime_type, const char *pathname, wprint_status_cb_t cb_fn,
        const char *debugDir, const char *scheme) {
    wJob_t job_handle = WPRINT_BAD_JOB_HANDLE;
    _msg_t msg;
    struct stat stat_buf;
    bool is_dir = false;
    _job_queue_t *jq;
    wprint_plugin_t *plugin = NULL;
    char *print_format;
    ifc_print_job_t *print_ifc;

    if (mime_type == NULL) {
        errno = EINVAL;
        return job_handle;
    }

    print_format = _get_print_format(mime_type, job_params, printer_cap);
    if (print_format == NULL) return job_handle;

    // check to see if we have an appropriate plugin
    if (OK == stat(pathname, &stat_buf)) {
        if (S_ISDIR(stat_buf.st_mode)) {
            is_dir = true;
        } else if (stat_buf.st_size == 0) {
            errno = EBADF;
            return job_handle;
        }
    } else {
        errno = ENOENT;
        return job_handle;
    }

    // Make sure we have job_params
    if (job_params == NULL) {
        errno = ECOMM;
        return job_handle;
    }

    plugin = plugin_search(mime_type, print_format);
    _lock();

    if (plugin) {
        job_handle = _get_handle();
        if (job_handle == WPRINT_BAD_JOB_HANDLE) {
            errno = EAGAIN;
        }
    } else {
        errno = ENOSYS;
        LOGE("wprintStartJob(): ERROR: no plugin found for %s => %s", mime_type, print_format);
    }

    if (job_handle != WPRINT_BAD_JOB_HANDLE) {
        print_ifc = (ifc_print_job_t *) _get_print_ifc(((port_num == 0) ? PORT_FILE : PORT_IPP));

        // fill out the job queue record
        jq = _get_job_desc(job_handle);
        if (jq == NULL) {
            _recycle_handle(job_handle);
            job_handle = WPRINT_BAD_JOB_HANDLE;
            _unlock();
            return job_handle;
        }

        if (debugDir != NULL) {
            strncpy(jq->debug_path, debugDir, MAX_PATHNAME_LENGTH);
            jq->debug_path[MAX_PATHNAME_LENGTH] = 0;
        }

        strncpy(jq->printer_addr, printer_addr, MAX_PRINTER_ADDR_LENGTH);
        strncpy(jq->mime_type, mime_type, MAX_MIME_LENGTH);
        strncpy(jq->pathname, pathname, MAX_PATHNAME_LENGTH);

        jq->port_num = port_num;
        jq->cb_fn = cb_fn;
        jq->print_ifc = print_ifc;
        jq->cancel_ok = true; // assume cancel is ok
        jq->plugin = plugin;
        memcpy(jq->printer_uri, printer_cap->httpResource,
                MIN(ARRAY_SIZE(printer_cap->httpResource), ARRAY_SIZE(jq->printer_uri)));

        jq->status_ifc = _get_status_ifc(((port_num == 0) ? PORT_FILE : PORT_IPP));

        memcpy((char *) &(jq->job_params), job_params, sizeof(wprint_job_params_t));

        jq->use_secure_uri = (strstr(scheme, IPPS_PREFIX) != NULL);

        size_t useragent_len = strlen(USERAGENT_PREFIX) + strlen(jq->job_params.docCategory) + 1;
        char *useragent = (char *) malloc(useragent_len);
        if (useragent != NULL) {
            snprintf(useragent, useragent_len, USERAGENT_PREFIX "%s", jq->job_params.docCategory);
            jq->job_params.useragent = useragent;
        }

        jq->job_params.page_num = 0;
        jq->job_params.print_format = print_format;
        if (strcmp(print_format, PRINT_FORMAT_PCLM) == 0) {
            if (printer_cap->canPrintPCLm || printer_cap->canPrintPDF) {
                jq->job_params.pcl_type = PCLm;
            } else {
                jq->job_params.pcl_type = PCLNONE;
            }
        }

        if (strcmp(print_format, PRINT_FORMAT_PWG) == 0) {
            if (printer_cap->canPrintPWG) {
                jq->job_params.pcl_type = PCLPWG;
            } else {
                jq->job_params.pcl_type = PCLNONE;
            }
        }

        // if the pathname is a directory, then this is a multi-page job with individual pages
        if (is_dir) {
            jq->is_dir = true;
            jq->num_pages = 0;

            // create a pageQ for queuing page information
            jq->pageQ = msgQCreate(_MAX_PAGES_PER_JOB, sizeof(_page_t));

            // create a secondary page Q for subsequently saving page data for copies #2 to n
            if (jq->job_params.num_copies > 1) {
                jq->saveQ = msgQCreate(_MAX_PAGES_PER_JOB, sizeof(_page_t));
            }
        } else {
            jq->num_pages = 1;
        }

        // post a message with job_handle to the msgQ that is serviced by a thread
        msg.id = MSG_RUN_JOB;
        msg.job_id = job_handle;

        if (print_ifc && plugin && plugin->print_page &&
                (msgQSend(_msgQ, (char *) &msg, sizeof(msg), NO_WAIT, MSG_Q_FIFO) == OK)) {
            errno = OK;
            LOGD("wprintStartJob(): print job %ld queued (%s => %s)", job_handle,
                    mime_type, print_format);
        } else {
            if (print_ifc == NULL) {
                errno = EAFNOSUPPORT;
            } else if ((plugin == NULL) || (plugin->print_page == NULL)) {
                errno = ELIBACC;
            } else {
                errno = EBADMSG;
            }

            LOGE("wprintStartJob(): ERROR plugin->start_job(%ld) : %s => %s", job_handle,
                    mime_type, print_format);
            jq->job_state = JOB_STATE_ERROR;
            _recycle_handle(job_handle);
            job_handle = WPRINT_BAD_JOB_HANDLE;
        }
    }
    _unlock();
    return job_handle;
}

status_t wprintEndJob(wJob_t job_handle) {
    _page_t page;
    _job_queue_t *jq;
    status_t result = ERROR;

    _lock();
    jq = _get_job_desc(job_handle);

    if (jq) {
        // if the job is done and is to be freed, do it
        if ((jq->job_state == JOB_STATE_CANCELLED) || (jq->job_state == JOB_STATE_ERROR) ||
                (jq->job_state == JOB_STATE_CORRUPTED) || (jq->job_state == JOB_STATE_COMPLETED)) {
            result = OK;
            if (jq->pageQ) {
                while ((msgQNumMsgs(jq->pageQ) > 0)
                        && (msgQReceive(jq->pageQ, (char *) &page, sizeof(page),
                                WAIT_FOREVER) == OK)) {
                }
                result |= msgQDelete(jq->pageQ);
                jq->pageQ = NULL;
            }

            if (jq->saveQ) {
                while ((msgQNumMsgs(jq->saveQ) > 0)
                        && (msgQReceive(jq->saveQ, (char *) &page, sizeof(page),
                                WAIT_FOREVER) == OK)) {
                }
                result |= msgQDelete(jq->saveQ);
                jq->saveQ = NULL;
            }
            _recycle_handle(job_handle);
        } else {
            LOGE("job %ld cannot be ended from state %d", job_handle, jq->job_state);
        }
    } else {
        LOGE("ERROR: wprintEndJob(%ld), job not found", job_handle);
    }

    _unlock();
    return result;
}

status_t wprintPage(wJob_t job_handle, int page_num, const char *filename, bool last_page,
        bool pdf_page, unsigned int top_margin, unsigned int left_margin, unsigned int right_margin,
        unsigned int bottom_margin) {
    _job_queue_t *jq;
    _page_t page;
    status_t result = ERROR;
    struct stat stat_buf;

    _lock();
    jq = _get_job_desc(job_handle);

    // use empty string to indicate EOJ for an empty job
    if (!filename) {
        filename = "";
        last_page = true;
    } else if (OK == stat(filename, &stat_buf)) {
        if (!S_ISREG(stat_buf.st_mode) || (stat_buf.st_size == 0)) {
            _unlock();
            return result;
        }
    } else {
        _unlock();
        return result;
    }

    // must be setup as a multi-page job, page_num must be valid, and filename must fit
    if (jq && jq->is_dir && !(jq->last_page_seen) && (((strlen(filename) < MAX_PATHNAME_LENGTH)) ||
            (jq && (strcmp(filename, "") == 0) && last_page))) {
        memset(&page, 0, sizeof(page));
        page.page_num = page_num;
        page.corrupted = false;
        page.pdf_page = pdf_page;
        page.last_page = last_page;
        page.top_margin = top_margin;
        page.left_margin = left_margin;
        page.right_margin = right_margin;
        page.bottom_margin = bottom_margin;

        if ((strlen(filename) == 0) || strchr(filename, '/')) {
            // assume empty or complete pathname and use it as it is
            strncpy(page.filename, filename, MAX_PATHNAME_LENGTH);
        } else {
            // generate a complete pathname
            snprintf(page.filename, MAX_PATHNAME_LENGTH, "%s/%s", jq->pathname, filename);
        }

        if (last_page) {
            jq->last_page_seen = true;
        }

        result = msgQSend(jq->pageQ, (char *) &page, sizeof(page), NO_WAIT, MSG_Q_FIFO);
    }

    if (result == OK) {
        LOGD("wprintPage(%ld, %d, %s, %d)", job_handle, page_num, filename, last_page);
        if (!(last_page && (strcmp(filename, "") == 0))) {
            jq->num_pages++;
        }
    } else {
        LOGE("wprintPage(%ld, %d, %s, %d)", job_handle, page_num, filename, last_page);
    }

    _unlock();
    return result;
}

status_t wprintCancelJob(wJob_t job_handle) {
    _job_queue_t *jq;
    status_t result;

    _lock();

    jq = _get_job_desc(job_handle);

    if (jq) {
        LOGI("received cancel request");
        // send a dummy page in case we're waiting on the msgQ page receive
        if ((jq->job_state == JOB_STATE_RUNNING) || (jq->job_state == JOB_STATE_BLOCKED)) {
            bool enableTimeout = true;
            jq->cancel_ok = true;
            jq->job_params.cancelled = true;
            wprintPage(job_handle, jq->num_pages + 1, NULL, true, false, 0, 0, 0, 0);
            if (jq->status_ifc) {
                // are we blocked waiting for the job to start
                if ((jq->job_state != JOB_STATE_BLOCKED) || (jq->job_params.page_num != 0)) {
                    errno = OK;
                    jq->cancel_ok = ((jq->status_ifc->cancel)(jq->status_ifc,
                            jq->job_params.job_originating_user_name) == 0);
                    if ((jq->cancel_ok == true) && (errno != OK)) {
                        enableTimeout = false;
                    }
                }
            }
            if (!jq->cancel_ok) {
                LOGE("CANCEL did not go through or is not supported for this device");
                enableTimeout = true;
            }
            if (enableTimeout && (jq->print_ifc != NULL) &&
                    (jq->print_ifc->enable_timeout != NULL)) {
                jq->print_ifc->enable_timeout(jq->print_ifc, 1);
            }

            errno = (jq->cancel_ok ? OK : ENOTSUP);
            jq->job_state = JOB_STATE_CANCEL_REQUEST;
            result = OK;
        } else if ((jq->job_state == JOB_STATE_CANCEL_REQUEST) ||
                (jq->job_state == JOB_STATE_CANCELLED)) {
            result = OK;
            errno = (jq->cancel_ok ? OK : ENOTSUP);
        } else if (jq->job_state == JOB_STATE_QUEUED) {
            jq->job_params.cancelled = true;
            jq->job_state = JOB_STATE_CANCELLED;

            if (jq->cb_fn) {
                wprint_job_callback_params_t cb_param;
                cb_param.state = JOB_DONE;
                cb_param.blocked_reasons = BLOCKED_REASONS_CANCELLED;
                cb_param.job_done_result = CANCELLED;

                jq->cb_fn(job_handle, (void *) &cb_param);
            }

            errno = OK;
            result = OK;
        } else {
            LOGE("job in other state");
            result = ERROR;
            errno = EBADRQC;
        }
    } else {
        LOGE("could not find job");
        result = ERROR;
        errno = EBADR;
    }

    _unlock();

    return result;
}

status_t wprintExit(void) {
    _msg_t msg;

    if (_msgQ) {
        //  toss the remaining messages in the msgQ
        while ((msgQNumMsgs(_msgQ) > 0) &&
                (OK == msgQReceive(_msgQ, (char *) &msg, sizeof(msg), NO_WAIT))) {}

        // send a quit message
        msg.id = MSG_QUIT;
        msgQSend(_msgQ, (char *) &msg, sizeof(msg), NO_WAIT, MSG_Q_FIFO);

        // stop the job thread
        _stop_thread();

        // empty out the semaphore
        while (sem_trywait(&_job_end_wait_sem) == OK);
        while (sem_trywait(&_job_start_wait_sem) == OK);

        // receive any messages just in case
        while ((msgQNumMsgs(_msgQ) > 0)
                && (OK == msgQReceive(_msgQ, (char *) &msg, sizeof(msg), NO_WAIT))) {}

        // delete the msgQ
        msgQDelete(_msgQ);
        _msgQ = NULL;

        sem_destroy(&_job_end_wait_sem);
        sem_destroy(&_job_start_wait_sem);
        pthread_mutex_destroy(&_q_lock);
    }

    return OK;
}

void wprintSetSourceInfo(const char *appName, const char *appVersion, const char *osName) {
    if (appName) {
        strncpy(g_appName, appName, (sizeof(g_appName) - 1));
    }

    if (appVersion) {
        strncpy(g_appVersion, appVersion, (sizeof(g_appVersion) - 1));
    }

    if (osName) {
        strncpy(g_osName, osName, (sizeof(g_osName) - 1));
    }

    LOGI("App Name: '%s', Version: '%s', OS: '%s'", g_appName, g_appVersion, g_osName);
}
