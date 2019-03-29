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

#include <stdio.h>
#include <stdint.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <linux/i2c-dev.h>

#include "stm32_bl.h"
#include "i2c.h"

uint8_t i2c_write_data(handle_t *handle, uint8_t *buffer, int length)
{
    i2c_handle_t *i2c_handle = (i2c_handle_t *)handle;

    buffer[length] = checksum(handle, buffer, length);

    if (write(i2c_handle->fd, buffer, length+1) == (length+1))
        return CMD_ACK;
    else
        return CMD_NACK;
}

uint8_t i2c_write_cmd(handle_t *handle, uint8_t cmd)
{
    uint8_t buffer[sizeof(uint8_t)+1] =
    {
        cmd
    };

    return handle->write_data(handle, buffer, sizeof(uint8_t));
}

uint8_t i2c_read_data(handle_t *handle, uint8_t *data, int length)
{
    i2c_handle_t *i2c_handle = (i2c_handle_t *)handle;

    if (read(i2c_handle->fd, data, length) == length)
        return CMD_ACK;
    else
        return CMD_NACK;
}

uint8_t i2c_read_ack(handle_t *handle)
{
    uint8_t buffer;

    if (handle->read_data(handle, &buffer, sizeof(uint8_t)) == CMD_ACK)
        return buffer;
    else
        return CMD_NACK;
}

int i2c_init(handle_t *handle)
{
    i2c_handle_t *i2c_handle = (i2c_handle_t *)handle;

    handle->cmd_erase = CMD_ERASE_NS;
    handle->cmd_read_memory = CMD_READ_MEMORY;
    handle->cmd_write_memory = CMD_WRITE_MEMORY_NS;

    handle->write_data = i2c_write_data;
    handle->write_cmd = i2c_write_cmd;
    handle->read_data = i2c_read_data;
    handle->read_ack = i2c_read_ack;

    if (ioctl(i2c_handle->fd, I2C_SLAVE, i2c_handle->addr) < 0) {
        perror("Error setting slave addr");
        return -1;
    } else {
        return 0;
    }
}
