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
#ifndef __WPRINT_MSGQ_H__
#define __WPRINT_MSGQ_H__

#include "wtypes.h"

#define WAIT_FOREVER -1
#define NO_WAIT 0

#define MSG_Q_FIFO 0

typedef void *msg_q_id;

#define MSG_Q_INVALID_ID ((msg_q_id)NULL)

/*
 * Definitions corresponding to ifc_wprint_t interfaces
 */

msg_q_id msgQCreate(int max_msgs, int max_msg_length);

status_t msgQDelete(msg_q_id msgQ);

status_t msgQSend(msg_q_id msgQ, const char *buffer, unsigned long nbytes, int timeout,
        int priority);

status_t msgQReceive(msg_q_id msgQ, char *buffer, unsigned long max_nbytes, int timeout);

int msgQNumMsgs(msg_q_id msgQ);

#endif // __WPRINT_MSGQ_H__