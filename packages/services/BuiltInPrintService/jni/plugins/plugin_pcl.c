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
#include <unistd.h>
#include "ifc_print_job.h"
#include "lib_pcl.h"
#include "wprint_image.h"

#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif
#ifndef __USE_UNIX98
#define __USE_UNIX98
#endif

#include <pthread.h>
#include <semaphore.h>

#define MAX_SEND_BUFFS (BUFFERED_ROWS / STRIPE_HEIGHT)

#define TAG "plugin_pcl"

typedef enum {
    MSG_START_JOB,
    MSG_START_PAGE,
    MSG_SEND,
    MSG_END_JOB,
    MSG_END_PAGE,
} msg_id_t;

typedef struct {
    msg_id_t id;

    union {
        struct {
            float extra_margin;
            int width;
            int height;
        } start_page;
        struct {
            char *buffer;
            int start_row;
            int num_rows;
            int bytes_per_row;
        } send;
        struct {
            int page;
            char *buffers[MAX_SEND_BUFFS];
            int count;
        } end_page;
    } param;
} msgQ_msg_t;

typedef struct {
    wJob_t job_handle;
    msg_q_id msgQ;
    pthread_t send_tid;
    pcl_job_info_t job_info;
    wprint_job_params_t *job_params;
    sem_t buffs_sem;
    ifc_pcl_t *pcl_ifc;
} plugin_data_t;

static const char *_mime_types[] = {
        MIME_TYPE_PDF,
        NULL};

static const char *_print_formats[] = {
        PRINT_FORMAT_PCLM,
        PRINT_FORMAT_PWG,
        PRINT_FORMAT_PDF,
        NULL};

static const char **_get_mime_types(void) {
    return _mime_types;
}

static const char **_get_print_formats(void) {
    return _print_formats;
}

static void _cleanup_plugin_data(plugin_data_t *priv) {
    if (priv != NULL) {
        if (priv->msgQ != MSG_Q_INVALID_ID) {
            priv->job_info.wprint_ifc->msgQDelete(priv->msgQ);
        }
        sem_destroy(&priv->buffs_sem);
        free(priv);
    }
}

/*
 * Waits to receive message from the msgQ. Handles messages and sends commands to handle jobs
 */
static void *_send_thread(void *param) {
    msgQ_msg_t msg;
    plugin_data_t *priv = (plugin_data_t *) param;

    while (priv->job_info.wprint_ifc->msgQReceive(priv->msgQ, (char *) &msg, sizeof(msgQ_msg_t),
            WAIT_FOREVER) == OK) {
        if (msg.id == MSG_START_JOB) {
            priv->pcl_ifc->start_job(priv->job_handle, &priv->job_info,
                    priv->job_params->media_size, priv->job_params->media_type,
                    priv->job_params->pixel_units, priv->job_params->duplex,
                    priv->job_params->dry_time, priv->job_params->color_space,
                    priv->job_params->media_tray, priv->job_params->page_top_margin,
                    priv->job_params->page_left_margin);
        } else if (msg.id == MSG_START_PAGE) {
            priv->pcl_ifc->start_page(&priv->job_info, msg.param.start_page.width,
                    msg.param.start_page.height);
        } else if (msg.id == MSG_SEND) {
            if (!priv->pcl_ifc->canCancelMidPage() || !priv->job_params->cancelled) {
                priv->pcl_ifc->print_swath(&priv->job_info, msg.param.send.buffer,
                        msg.param.send.start_row, msg.param.send.num_rows,
                        msg.param.send.bytes_per_row);
            }
            sem_post(&priv->buffs_sem);
        } else if (msg.id == MSG_END_PAGE) {
            int i;
            priv->pcl_ifc->end_page(&priv->job_info, msg.param.end_page.page);
            for (i = 0; i < msg.param.end_page.count; i++) {
                if (msg.param.end_page.buffers[i] != NULL) {
                    free(msg.param.end_page.buffers[i]);
                }
            }
        } else if (msg.id == MSG_END_JOB) {
            priv->pcl_ifc->end_job(&priv->job_info);
            break;
        }
    }
    return NULL;
}

/*
 * Starts pcl thread
 */
static status_t _start_thread(plugin_data_t *param) {
    sigset_t allsig, oldsig;
    status_t result;

    if (param == NULL) {
        return ERROR;
    }

    param->send_tid = pthread_self();

    result = OK;
    sigfillset(&allsig);
#if CHECK_PTHREAD_SIGMASK_STATUS
    result = pthread_sigmask(SIG_SETMASK, &allsig, &oldsig);
#else // CHECK_PTHREAD_SIGMASK_STATUS
    pthread_sigmask(SIG_SETMASK, &allsig, &oldsig);
#endif // CHECK_PTHREAD_SIGMASK_STATUS
    if (result == OK) {
        result = (status_t) pthread_create(&(param->send_tid), 0, _send_thread, (void *) param);
        if ((result == ERROR) && (param->send_tid != pthread_self())) {
#if USE_PTHREAD_CANCEL
            pthread_cancel(param->send_tid);
#else // else USE_PTHREAD_CANCEL
            pthread_kill(param->send_tid, SIGKILL);
#endif // USE_PTHREAD_CANCEL
            param->send_tid = pthread_self();
        }
    }

    if (result == OK) {
        sched_yield();
#if CHECK_PTHREAD_SIGMASK_STATUS
        result = pthread_sigmask(SIG_SETMASK, &oldsig, 0);
#else // CHECK_PTHREAD_SIGMASK_STATUS
        pthread_sigmask(SIG_SETMASK, &oldsig, 0);
#endif // CHECK_PTHREAD_SIGMASK_STATUS
    }
    return result;
}

/*
 * Stops pcl thread
 */
static status_t _stop_thread(plugin_data_t *priv) {
    status_t result = ERROR;
    if (priv == NULL) {
        return result;
    }
    if (!pthread_equal(priv->send_tid, pthread_self())) {
        msgQ_msg_t msg;
        msg.id = MSG_END_JOB;

        priv->job_info.wprint_ifc->msgQSend(
                priv->msgQ, (char *) &msg, sizeof(msgQ_msg_t), NO_WAIT, MSG_Q_FIFO);
        pthread_join(priv->send_tid, 0);
        priv->send_tid = pthread_self();
        result = OK;
    }
    _cleanup_plugin_data(priv);
    return result;
}

static int _start_job(wJob_t job_handle, const ifc_wprint_t *wprint_ifc_p,
        const ifc_print_job_t *print_ifc_p, wprint_job_params_t *job_params) {
    msgQ_msg_t msg;
    plugin_data_t *priv = NULL;

    do {
        if (job_params == NULL) continue;

        job_params->plugin_data = NULL;
        if ((wprint_ifc_p == NULL) || (print_ifc_p == NULL)) continue;

        priv = (plugin_data_t *) malloc(sizeof(plugin_data_t));
        if (priv == NULL) continue;

        memset(priv, 0, sizeof(plugin_data_t));

        priv->job_handle = job_handle;
        priv->job_params = job_params;
        priv->send_tid = pthread_self();
        priv->job_info.job_handle = _WJOBH_NONE;
        priv->job_info.print_ifc = (ifc_print_job_t *) print_ifc_p;
        priv->job_info.wprint_ifc = (ifc_wprint_t *) wprint_ifc_p;
        priv->job_info.strip_height = job_params->strip_height;
        priv->job_info.useragent = job_params->useragent;

        sem_init(&priv->buffs_sem, 0, MAX_SEND_BUFFS);
        switch (job_params->pcl_type) {
            case PCLm:
                priv->pcl_ifc = pclm_connect();
                break;
            case PCLPWG:
                priv->pcl_ifc = pwg_connect();
                break;
            default:
                break;
        }

        if (priv->pcl_ifc == NULL) {
            LOGE("ERROR: cannot start PCL job, no ifc found");
            continue;
        }

        priv->msgQ = priv->job_info.wprint_ifc->msgQCreate(
                (MAX_SEND_BUFFS * 2), sizeof(msgQ_msg_t));
        if (priv->msgQ == MSG_Q_INVALID_ID) continue;

        if (_start_thread(priv) == ERROR) continue;

        job_params->plugin_data = (void *) priv;
        msg.id = MSG_START_JOB;
        priv->job_info.wprint_ifc->msgQSend(
                priv->msgQ, (char *) &msg, sizeof(msgQ_msg_t), NO_WAIT, MSG_Q_FIFO);

        return OK;
    } while (0);

    _cleanup_plugin_data(priv);
    return ERROR;
}

static status_t _print_page(wprint_job_params_t *job_params, const char *mime_type,
        const char *pathname) {
    wprint_image_info_t *image_info;
    FILE *imgfile;
    status_t result;
    int num_rows, height, image_row;
    int blank_data;
    char *buff;
    int i, buff_index, buff_size;
    char *buff_pool[MAX_SEND_BUFFS];

    int nbytes;
    plugin_data_t *priv;
    msgQ_msg_t msg;
    int image_padding = PAD_PRINT;

    if (job_params == NULL) return ERROR;

    priv = (plugin_data_t *) job_params->plugin_data;

    if (priv == NULL) return ERROR;

    switch (job_params->pcl_type) {
        case PCLm:
        case PCLPWG:
            image_padding = PAD_ALL;
            break;
        default:
            break;
    }

    if (pathname == NULL) {
        LOGE("_print_page(): cannot print file with NULL name");
        msg.param.end_page.page = -1;
        msg.param.end_page.count = 0;
        result = ERROR;
    } else if (strlen(pathname)) {
        image_info = malloc(sizeof(wprint_image_info_t));
        if (image_info == NULL) return ERROR;

        imgfile = fopen(pathname, "r");
        if (imgfile) {
            LOGD("_print_page(): fopen succeeded on %s", pathname);
            wprint_image_setup(image_info, mime_type, priv->job_info.wprint_ifc,
                    job_params->pixel_units, job_params->pdf_render_resolution);
            wprint_image_init(image_info, pathname, job_params->page_num);

            // get the image_info of the input file of specified MIME type
            if ((result = wprint_image_get_info(imgfile, image_info)) == OK) {
                wprint_rotation_t rotation = ROT_0;

                if ((job_params->render_flags & RENDER_FLAG_PORTRAIT_MODE) != 0) {
                    LOGI("_print_page(): portrait mode");
                    rotation = ROT_0;
                } else if ((job_params->render_flags & RENDER_FLAG_LANDSCAPE_MODE) != 0) {
                    LOGI("_print_page(): landscape mode");
                    rotation = ROT_90;
                } else if (wprint_image_is_landscape(image_info) &&
                        ((job_params->render_flags & RENDER_FLAG_AUTO_ROTATE) != 0)) {
                    LOGI("_print_page(): auto mode");
                    rotation = ROT_90;
                }

                if ((job_params->render_flags & RENDER_FLAG_CENTER_ON_ORIENTATION) != 0) {
                    job_params->render_flags &= ~(RENDER_FLAG_CENTER_HORIZONTAL |
                            RENDER_FLAG_CENTER_VERTICAL);
                    job_params->render_flags |= ((rotation == ROT_0) ? RENDER_FLAG_CENTER_HORIZONTAL
                            : RENDER_FLAG_CENTER_VERTICAL);
                }

                if ((job_params->duplex == DUPLEX_MODE_BOOK) &&
                        (job_params->page_backside) &&
                        ((job_params->render_flags & RENDER_FLAG_ROTATE_BACK_PAGE) != 0) &&
                        ((job_params->render_flags & RENDER_FLAG_BACK_PAGE_PREROTATED) == 0)) {
                    rotation = ((rotation == ROT_0) ? ROT_180 : ROT_270);
                }
                LOGI("_print_page(): rotation = %d", rotation);

                wprint_image_set_output_properties(image_info, rotation,
                        job_params->printable_area_width, job_params->printable_area_height,
                        job_params->print_top_margin, job_params->print_left_margin,
                        job_params->print_right_margin, job_params->print_bottom_margin,
                        job_params->render_flags, job_params->strip_height, MAX_SEND_BUFFS,
                        image_padding);

                // allocate memory for a stripe of data
                for (i = 0; i < MAX_SEND_BUFFS; i++) {
                    buff_pool[i] = NULL;
                }

                blank_data = MAX_SEND_BUFFS;
                buff_size = wprint_image_get_output_buff_size(image_info);
                for (i = 0; i < MAX_SEND_BUFFS; i++) {
                    buff_pool[i] = malloc(buff_size);
                    if (buff_pool[i] == NULL) {
                        break;
                    }
                    memset(buff_pool[i], 0xff, buff_size);
                }

                if (i == MAX_SEND_BUFFS) {
                    msg.id = MSG_START_PAGE;
                    msg.param.start_page.extra_margin = ((job_params->duplex !=
                            DUPLEX_MODE_NONE) &&
                            ((job_params->page_num & 0x1) == 0))
                            ? job_params->page_bottom_margin : 0.0f;
                    msg.param.start_page.width = wprint_image_get_width(image_info);
                    msg.param.start_page.height = wprint_image_get_height(image_info);
                    priv->job_info.num_components = image_info->num_components;
                    priv->job_info.wprint_ifc->msgQSend(priv->msgQ, (char *) &msg,
                            sizeof(msgQ_msg_t), NO_WAIT, MSG_Q_FIFO);

                    msg.id = MSG_SEND;
                    msg.param.send.bytes_per_row = BYTES_PER_PIXEL(wprint_image_get_width(
                            image_info));

                    // send blank rows for any offset
                    buff_index = 0;
                    num_rows = wprint_image_get_height(image_info);
                    image_row = 0;

                    // decode and render each stripe into PCL3 raster format
                    while ((result != ERROR) && (num_rows > 0)) {
                        if (priv->pcl_ifc->canCancelMidPage() && job_params->cancelled) {
                            break;
                        }
                        sem_wait(&priv->buffs_sem);

                        buff = buff_pool[buff_index];
                        buff_index = ((buff_index + 1) % MAX_SEND_BUFFS);

                        height = MIN(num_rows, job_params->strip_height);
                        if (!job_params->cancelled) {
                            nbytes = wprint_image_decode_stripe(image_info, image_row, &height,
                                    (unsigned char *) buff);

                            if (blank_data > 0) {
                                blank_data--;
                            }
                        } else if (blank_data < MAX_SEND_BUFFS) {
                            nbytes = buff_size;
                            memset(buff, 0xff, buff_size);
                            blank_data++;
                        }

                        if (nbytes > 0) {
                            msg.param.send.buffer = buff;
                            msg.param.send.start_row = image_row;
                            msg.param.send.num_rows = height;

                            result = priv->job_info.wprint_ifc->msgQSend(priv->msgQ, (char *) &msg,
                                    sizeof(msgQ_msg_t), NO_WAIT, MSG_Q_FIFO);
                            if (result < 0) {
                                sem_post(&priv->buffs_sem);
                            }

                            image_row += height;
                            num_rows -= height;
                        } else {
                            sem_post(&priv->buffs_sem);
                            if (nbytes < 0) {
                                LOGE("_print_page(): ERROR: file appears to be corrupted");
                                result = CORRUPT;
                            }
                            break;
                        }
                    }

                    if ((result == OK) && job_params->cancelled) {
                        result = CANCELLED;
                    }

                    LOGI("_print_page(): sends done, result: %d", result);

                    // free the buffer and eject the page
                    msg.param.end_page.page = job_params->page_num;
                    LOGI("_print_page(): processed %d out of"
                            " %d rows of page # %d from %s to printer %s %s {%s}",
                            image_row, wprint_image_get_height(image_info),
                            job_params->page_num, pathname,
                            (job_params->last_page) ? "- last page" : "- ",
                            (job_params->cancelled) ? "- job cancelled"
                                    : ".",
                            (result == OK) ? "OK" : "ERROR");
                } else {
                    msg.param.end_page.page = -1;
                    result = ERROR;
                    LOGE("_print_page(): plugin_pcl cannot allocate memory for image stripe");
                }
                for (i = 0; i < MAX_SEND_BUFFS; i++) {
                    msg.param.end_page.buffers[i] = buff_pool[i];
                }
                msg.param.end_page.count = MAX_SEND_BUFFS;
            } else {
                msg.param.end_page.page = -1;
                msg.param.end_page.count = 0;
                result = CORRUPT;
                LOGE("_print_page(): file does not appear to be valid");
            }

            // send the end page message
            wprint_image_cleanup(image_info);
            fclose(imgfile);
        } else {
            msg.param.end_page.page = -1;
            msg.param.end_page.count = 0;
            LOGE("_print_page(): could not open %s", pathname);
            result = CORRUPT;
        }
        free(image_info);
    } else {
        LOGE("_print_page(): ERROR: filename was empty");
        msg.param.end_page.page = -1;
        msg.param.end_page.count = 0;
        result = ERROR;
    }

    msg.id = MSG_END_PAGE;
    priv->job_info.wprint_ifc->msgQSend(priv->msgQ, (char *) &msg, sizeof(msgQ_msg_t), NO_WAIT,
            MSG_Q_FIFO);
    return result;
}

/*
 * Prints a blank page
 */
static int _print_blank_page(wJob_t job_handle, wprint_job_params_t *job_params) {
    msgQ_msg_t msg;
    plugin_data_t *priv;

    if (job_params == NULL) return ERROR;

    priv = (plugin_data_t *) job_params->plugin_data;
    if (priv == NULL) return ERROR;

    msg.id = MSG_END_PAGE;
    msg.param.end_page.page = -1;
    msg.param.end_page.count = 0;
    priv->job_info.wprint_ifc->msgQSend(priv->msgQ, (char *) &msg, sizeof(msgQ_msg_t), NO_WAIT,
            MSG_Q_FIFO);
    return OK;
}

static int _end_job(wprint_job_params_t *job_params) {
    if (job_params != NULL) {
        _stop_thread((plugin_data_t *) job_params->plugin_data);
    }
    return OK;
}

wprint_plugin_t *libwprintplugin_pcl_reg(void) {
    static const wprint_plugin_t _pcl_plugin = {.version = WPRINT_PLUGIN_VERSION(0),
            .priority = PRIORITY_LOCAL, .get_mime_types = _get_mime_types,
            .get_print_formats = _get_print_formats, .start_job = _start_job,
            .print_page = _print_page, .print_blank_page = _print_blank_page, .end_job = _end_job,};
    return ((wprint_plugin_t *) &_pcl_plugin);
}