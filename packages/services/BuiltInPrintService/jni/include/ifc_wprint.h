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
#ifndef __IFC_WPRINT_H__
#define __IFC_WPRINT_H__

#include "wprint_msgq.h"
#include "wtypes.h"

#ifdef __cplusplus
extern "C"
{
#endif

/*
 * An interface for capturing job data as it is spooled for debugging purposes
 */
typedef struct {
    void (*debug_start_job)(wJob_t job_handle, const char *ext);

    void (*debug_job_data)(wJob_t job_handle, const unsigned char *buff, unsigned long nbytes);

    void (*debug_end_job)(wJob_t job_handle);

    void (*debug_start_page)(wJob_t job_handle, int page_number, int width, int height);

    void (*debug_page_data)(wJob_t job_handle, const unsigned char *buff, unsigned long nbytes);

    void (*debug_end_page)(wJob_t job_handle);
} ifc_wprint_debug_stream_t;

/*
 * Interface for global wprint functions
 */
typedef struct {
    /*
     * Create a FIFO message queue
     */
    msg_q_id (*msgQCreate)(int max_mesgs, int max_msg_length);

    /*
     * Delete a previously created message queue. Returns OK or ERROR.
     */
    status_t (*msgQDelete)(msg_q_id msgQ);

    /**
     * Sends a message to a message queue. Returns OK or ERROR.
     */
    status_t (*msgQSend)(msg_q_id msgQ, const char *buffer, unsigned long nbytes, int timeout,
            int priority);

    /**
     * Collects a message, returning 0 if successful. timeout may be WAIT_FOREVER or NO_WAIT.
     */
    status_t (*msgQReceive)(msg_q_id msgQ, char *buffer, unsigned long max_nbytes, int timeout);

    /**
     * Returns the number of messages in the queue or ERROR.
     */
    int (*msgQNumMsgs)(msg_q_id msgQ);

    /*
     * Returns an interface used for debugging data delivered for a job
     */
    const ifc_wprint_debug_stream_t *(*get_debug_stream_ifc)(wJob_t id);
} ifc_wprint_t;

#ifdef __cplusplus
}
#endif

#endif // __IFC_WPRINT_H__