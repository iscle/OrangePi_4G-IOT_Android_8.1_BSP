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
#include <unistd.h>
#include <errno.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <fcntl.h>
#include <netdb.h>

#include "ifc_print_job.h"
#include "wprint_debug.h"

#define TAG "printer"

#define DEFAULT_TIMEOUT (5000)

typedef struct {
    ifc_print_job_t ifc;
    int port_num;
    int psock;
    wJob_t job_id;
    status_t job_status;
    int timeout_enabled;
} _print_job_t;

static long int _wprint_timeout_msec = DEFAULT_TIMEOUT;

static status_t _init(const ifc_print_job_t *this_p, const char *printer_addr, int port,
        const char *printer_uri, bool use_secure_uri) {
    _print_job_t *print_job = IMPL(_print_job_t, ifc, this_p);

    if (!print_job) return ERROR;

    // if a print-to-file is requested, open a file

    if (print_job->port_num == PORT_FILE) {
        print_job->psock = open(printer_addr, O_CREAT | O_WRONLY | O_TRUNC,
                S_IRUSR | S_IWUSR | S_IRGRP | S_IROTH);

        if (print_job->psock == ERROR) {
            LOGE("cannot create output file : %s, %s", printer_addr, strerror(errno));
        } else {
            LOGI("opened %s for writing", printer_addr);
        }
    } else {
        // open a socket to the printer:port
        print_job->psock = wConnect(printer_addr, print_job->port_num, _wprint_timeout_msec);
    }

    print_job->job_status = ((print_job->psock != -1) ? OK : ERROR);
    return print_job->job_status;
}

static void _destroy(const ifc_print_job_t *this_p) {
    _print_job_t *print_job = IMPL(_print_job_t, ifc, this_p);
    if (print_job) {
        free(print_job);
    }
}

static int _start_job(const ifc_print_job_t *this_p, const wprint_job_params_t *job_params) {
    _print_job_t *print_job = IMPL(_print_job_t, ifc, this_p);

    if (print_job) {
        return OK;
    } else {
        return ERROR;
    }
}

static int _send_data(const ifc_print_job_t *this_p, const char *buffer, size_t length) {
    status_t retval = OK;
    size_t length_in = length;
    ssize_t bytes_written;
    _print_job_t *print_job = IMPL(_print_job_t, ifc, this_p);

    if (this_p && buffer && (print_job->job_status == OK)) {
        if (print_job->port_num == PORT_FILE) {
            while ((length > 0) && (retval != -1)) {
                bytes_written = write(print_job->psock, buffer, length);
                if (bytes_written < 0) {
                    retval = ERROR;
                } else {
                    length -= bytes_written;
                    buffer += bytes_written;
                }
            }
        } else {
            fd_set w_fds;
            int selreturn;
            struct timeval timeout;

            while ((length > 0) && (retval == OK)) {
                FD_ZERO(&w_fds);
                FD_SET(print_job->psock, &w_fds);
                timeout.tv_sec = 20;
                timeout.tv_usec = 0;
                selreturn = select(print_job->psock + 1, NULL, &w_fds, NULL, &timeout);
                if (selreturn < 0) {
                    LOGE("select returned an errnor (%d)", errno);
                    retval = ERROR;
                } else if (selreturn > 0) {
                    if (FD_ISSET(print_job->psock, &w_fds)) {
                        bytes_written = write(print_job->psock, buffer, length);
                        if (bytes_written < 0) {
                            LOGE("unable to transmit %d bytes of data (errno %d)", length, errno);
                            retval = ERROR;
                        } else {
                            length -= bytes_written;
                            buffer += bytes_written;
                        }
                    } else {
                        LOGE("select returned OK, but fd is not set");
                        retval = ERROR;
                    }
                } else {
                    retval = (print_job->timeout_enabled ? ERROR : OK);
                    if (retval == ERROR) {
                        LOGE("select timed out");
                    }
                }
            }
        }

        print_job->job_status = retval;
    } else {
        retval = ERROR;
    }
    return ((retval == OK) ? length_in : (int)ERROR);
}

static int _end_job(const ifc_print_job_t *this_p) {
    _print_job_t *print_job = IMPL(_print_job_t, ifc, this_p);
    if (print_job) {
        close(print_job->psock);
        print_job->psock = -1;
        return print_job->job_status;
    }
    return ERROR;
}

static void _enable_timeout(const ifc_print_job_t *this_p, int enable) {
    _print_job_t *print_job = IMPL(_print_job_t, ifc, this_p);
    if (print_job) {
        print_job->timeout_enabled = enable;
    }
}

static int _check_status(const ifc_print_job_t *this_p) {
    _print_job_t *print_job = IMPL(_print_job_t, ifc, this_p);

    if (print_job) return print_job->job_status;

    return ERROR;
}

int wConnect(const char *printer_addr, int port_num, long int timeout_msec) {
    struct sockaddr_in sin;
    struct hostent *h_info;
    fd_set fdset;
    struct timeval tv;
    int psock;

    psock = socket(PF_INET, SOCK_STREAM, 0);
    if (psock == ERROR) return ERROR;

    memset((char *) &sin, 0, sizeof(sin));
    sin.sin_family = AF_INET;
    sin.sin_port = htons(port_num);

    if ((sin.sin_addr.s_addr = inet_addr(printer_addr)) == -1) {
        /*
         * The IP address is not in dotted decimal notation. Try to get the
         * network peripheral IP address by host name.
         */

        if ((h_info = gethostbyname(printer_addr)) != NULL) {
            (void) memcpy(&(sin.sin_addr.s_addr), h_info->h_addr, h_info->h_length);
        } else {
            LOGE("ERROR: unknown host %s", printer_addr);
            close(psock);
            return ERROR;
        }
    }

    // temporarily set the socket to NONBLOCK'ing mode to catch timeout
    fcntl(psock, F_SETFL, O_NONBLOCK);

    // open a TCP connection to the printer:port
    int socketConnect = connect(psock, (const struct sockaddr *) &sin, sizeof(sin));
    if (socketConnect == 0) {
        FD_ZERO(&fdset);
        FD_SET(psock, &fdset);

        tv.tv_sec = (timeout_msec / 1000);
        tv.tv_usec = (timeout_msec % 1000) * 1000;

        /*  check if the socket is connected and available for write within
         *  the specified timeout period
         */
        if (select(psock + 1, NULL, &fdset, NULL, &tv) == 1) {
            int so_error, flags;
            socklen_t len = sizeof so_error;

            getsockopt(psock, SOL_SOCKET, SO_ERROR, &so_error, &len);
            if (so_error == 0) {
                // restore the socket back to normal blocking mode

                flags = fcntl(psock, F_GETFL);
                fcntl(psock, F_SETFL, flags & ~O_NONBLOCK);

                LOGI("connected to %s:%d", printer_addr, port_num);
            } else {
                close(psock);
                psock = ERROR;
                LOGE("cannot connect on %s:%d, %s", printer_addr, port_num, strerror(errno));
            }
        } else {
            LOGE("connecting to %s:%d .. timed out after %ld milliseconds", printer_addr,
                    port_num, timeout_msec);
            close(psock);
            psock = ERROR;
        }
    }
    return psock;
}

static const ifc_print_job_t _print_job_ifc = {.init = _init, .validate_job = NULL,
        .start_job = _start_job, .send_data = _send_data, .end_job = _end_job, .destroy = _destroy,
        .enable_timeout = _enable_timeout, .check_status = _check_status,};

const ifc_print_job_t *printer_connect(int port_num) {
    _print_job_t *print_job;
    print_job = (_print_job_t *) malloc(sizeof(_print_job_t));

    if (print_job) {
        print_job->port_num = port_num;
        print_job->psock = -1;
        print_job->job_id = WPRINT_BAD_JOB_HANDLE;
        print_job->job_status = ERROR;
        print_job->timeout_enabled = 0;
        memcpy(&print_job->ifc, &_print_job_ifc, sizeof(ifc_print_job_t));

        return &print_job->ifc;
    } else {
        return NULL;
    }
}