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

#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif

#include <stdio.h>
#include <stdlib.h>
#include <pthread.h>
#include <semaphore.h>

#include "wprint_msgq.h"
#include "wprint_debug.h"

#define TAG "wprint_msgq"

#define _SEM_NAME_LENGTH    16

typedef struct {
    msg_q_id msgq_id;
    char name[_SEM_NAME_LENGTH];
    int max_msgs;
    int max_msg_length;
    int num_msgs;
    sem_t sem_count;
    sem_t *sem_ptr;
    pthread_mutex_t mutex;
    pthread_mutexattr_t mutexattr;
    unsigned long read_offset;
    unsigned long write_offset;
} _msgq_hdr_t;

msg_q_id msgQCreate(int max_msgs, int max_msg_length) {
    _msgq_hdr_t *msgq;
    int msgq_size;

    msgq_size = sizeof(_msgq_hdr_t) + max_msgs * max_msg_length;
    msgq = (_msgq_hdr_t *) malloc((size_t)msgq_size);

    if (msgq) {
        memset((char *) msgq, 0, (size_t)msgq_size);
        msgq->msgq_id = (msg_q_id) msgq;
        msgq->max_msgs = max_msgs;
        msgq->max_msg_length = max_msg_length;
        msgq->num_msgs = 0;

        // create a mutex to protect access to this structure
        pthread_mutexattr_init(&(msgq->mutexattr));
        pthread_mutexattr_settype(&(msgq->mutexattr), PTHREAD_MUTEX_RECURSIVE_NP);
        pthread_mutex_init(&msgq->mutex, &msgq->mutexattr);

        // create a counting semaphore
        msgq->sem_ptr = &msgq->sem_count;
        sem_init(msgq->sem_ptr, 0, 0); // PRIVATE, EMPTY

        msgq->read_offset = 0;
        msgq->write_offset = 0;
    }
    return ((msg_q_id) msgq);
}

status_t msgQDelete(msg_q_id msgQ) {
    _msgq_hdr_t *msgq = (msg_q_id) msgQ;

    if (msgq) {
        pthread_mutex_lock(&(msgq->mutex));
        if (msgq->num_msgs) {
            LOGE("Warning msgQDelete() called on queue with %d messages", msgq->num_msgs);
        }

        sem_destroy(&(msgq->sem_count));
        pthread_mutex_unlock(&(msgq->mutex));
        pthread_mutex_destroy(&(msgq->mutex));
        free((void *) msgq);
    }
    return (msgq ? OK : ERROR);
}

status_t msgQSend(msg_q_id msgQ, const char *buffer, unsigned long nbytes, int timeout,
        int priority) {
    _msgq_hdr_t *msgq = (msg_q_id) msgQ;
    char *msg_loc;
    status_t result = ERROR;

    // validate function arguments
    if (msgq && (timeout == NO_WAIT) && (priority == MSG_Q_FIFO)) {
        pthread_mutex_lock(&(msgq->mutex));

        // ensure the message conforms to size limits and there is room in the msgQ
        if ((nbytes <= msgq->max_msg_length) && (msgq->num_msgs < msgq->max_msgs)) {
            msg_loc = (char *) msgq + sizeof(_msgq_hdr_t) +
                    (msgq->write_offset * msgq->max_msg_length);
            memcpy(msg_loc, buffer, nbytes);
            msgq->write_offset = (msgq->write_offset + 1) % msgq->max_msgs;
            msgq->num_msgs++;
            sem_post(msgq->sem_ptr);
            result = OK;
        }

        pthread_mutex_unlock(&(msgq->mutex));
    }
    return result;
}

status_t msgQReceive(msg_q_id msgQ, char *buffer, unsigned long max_nbytes, int timeout) {
    _msgq_hdr_t *msgq = (msg_q_id) msgQ;
    char *msg_loc;
    status_t result = ERROR;

    if (msgq && buffer && ((timeout == WAIT_FOREVER) || (timeout == NO_WAIT))) {
        if (timeout == WAIT_FOREVER) {
            result = (status_t) sem_wait(msgq->sem_ptr);
        } else {
            /* timeout is NO_WAIT */
            result = (status_t) sem_trywait(msgq->sem_ptr);
        }

        if (result == 0) {
            pthread_mutex_lock(&(msgq->mutex));

            msg_loc = (char *) msgq + sizeof(_msgq_hdr_t) +
                    (msgq->read_offset * msgq->max_msg_length);
            memcpy(buffer, msg_loc, max_nbytes);
            msgq->read_offset = (msgq->read_offset + 1) % msgq->max_msgs;
            msgq->num_msgs--;
            pthread_mutex_unlock(&(msgq->mutex));
        }
    }
    return result;
}

int msgQNumMsgs(msg_q_id msgQ) {
    _msgq_hdr_t *msgq = (msg_q_id) msgQ;
    int num_msgs = -1;

    if (msgq) {
        pthread_mutex_lock(&(msgq->mutex));
        num_msgs = msgq->num_msgs;
        pthread_mutex_unlock(&(msgq->mutex));
    }
    return num_msgs;
}