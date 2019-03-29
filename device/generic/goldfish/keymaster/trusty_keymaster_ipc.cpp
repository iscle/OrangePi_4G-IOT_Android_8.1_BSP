/*
 * Copyright (C) 2015 The Android Open Source Project
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

#define LOG_TAG "TrustyKeymaster"

// TODO: make this generic in libtrusty

#include <assert.h>
#include <errno.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include <log/log.h>

#include "keymaster_ipc.h"
#include "trusty_keymaster_ipc.h"
#include "qemud.h"

#define KEYMASTER_SERVICE_NAME "KeymasterService3"

static int handle_ = 0;

int trusty_keymaster_connect() {
    ALOGE("calling %s\n", __func__);
    handle_ = qemu_pipe_open(KEYMASTER_SERVICE_NAME);
    if (handle_ < 0) {
        handle_ = 0;
        ALOGE("failed to open %s pipe service", KEYMASTER_SERVICE_NAME);
        ALOGE("calling %s failed\n", __func__);
        return -1;
    }
    ALOGE("calling %s succeeded\n", __func__);
    return 0;
}

int trusty_keymaster_call(uint32_t cmd, void* in, uint32_t in_size, uint8_t* out,
                          uint32_t* out_size) {

    size_t msg_size = in_size + sizeof(struct keymaster_message);
    ALOGE("calling %s insize %d msg size %d\n", __func__, (int)in_size, (int) msg_size);
    struct keymaster_message* msg = reinterpret_cast<struct keymaster_message*>(malloc(msg_size));
    msg->cmd = cmd;
    memcpy(msg->payload, in, in_size);

    int pipe_command_length = msg_size;
    assert(pipe_command_length > 0);
    ssize_t rc = WriteFully(handle_, &pipe_command_length, sizeof(pipe_command_length));
    if (rc < 1) {
        ALOGE("failed to send msg_size (%d) for cmd (%d) to %s: %s\n", (int)(sizeof(pipe_command_length)),
                (int)cmd, KEYMASTER_PORT, strerror(errno));
        return -errno;
    }

    rc = WriteFully(handle_, msg, pipe_command_length);
    if (in_size == 157 && cmd == KM_FINISH_OPERATION) {
        for (int i=0; i < (int)in_size; ++i) {
            ALOGE("pay[%d]: %d", i, (int)(msg->payload[i]));
        }
    }
    free(msg);


    if (rc < 1) {
        ALOGE("failed to send cmd (%d) to %s: %s\n", cmd, KEYMASTER_PORT, strerror(errno));
        return -errno;
    }

    rc = ReadFully(handle_, &pipe_command_length, sizeof(pipe_command_length));
    if (rc < 1) {
        ALOGE("failed to retrieve response length for cmd (%d) to %s: %s\n", cmd, KEYMASTER_PORT,
              strerror(errno));
        return -errno;
    }

    rc = ReadFully(handle_, out, pipe_command_length);
    if (rc < 1) {
        ALOGE("failed to retrieve response for cmd (%d) to %s: %s\n", cmd, KEYMASTER_PORT,
              strerror(errno));
        return -errno;
    }
    *out_size = pipe_command_length;
    return pipe_command_length;
}

void trusty_keymaster_disconnect() {
    ALOGE("calling %s\n", __func__);
    if (handle_ != 0) {
        close(handle_);
        handle_ = 0;
    }
}
