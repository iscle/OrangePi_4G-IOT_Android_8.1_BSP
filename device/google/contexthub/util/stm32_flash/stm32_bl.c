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

#include <stdint.h>

#include "stm32_bl.h"

/*
 * checksum a sequence of bytes.
 * length == 1 invert the byte
 * length > 1 xor all bytes
 */
uint8_t checksum(__attribute__((unused)) handle_t *handle, uint8_t *bytes, int length)
{
    int i;
    uint8_t csum;

    if (length == 1) {
        csum = ~bytes[0];
    } else if (length > 1) {
        for (csum=0,i=0; i<length; i++)
            csum ^= bytes[i];
    } else {
        csum = 0xFF;
    }

    return csum;
}

static uint8_t write_len(handle_t *handle, int len)
{
    uint8_t buffer[sizeof(uint8_t)+1];

    buffer[0] = len-1;

    return handle->write_data(handle, buffer, sizeof(uint8_t));
}

static uint8_t write_cnt(handle_t *handle, uint16_t cnt)
{
    uint8_t buffer[sizeof(uint16_t)+1];

    buffer[0] = (cnt >> 8) & 0xFF;
    buffer[1] = (cnt     ) & 0xFF;

    return handle->write_data(handle, buffer, sizeof(uint16_t));
}

static uint8_t write_addr(handle_t *handle, uint32_t addr)
{
    uint8_t buffer[sizeof(uint32_t)+1];

    buffer[0] = (addr >> 24) & 0xFF;
    buffer[1] = (addr >> 16) & 0xFF;
    buffer[2] = (addr >>  8) & 0xFF;
    buffer[3] = (addr      ) & 0xFF;

    return handle->write_data(handle, buffer, sizeof(uint32_t));
}

/* write length followed by the data */
static uint8_t write_len_data(handle_t *handle, int len, uint8_t *data)
{
    uint8_t buffer[sizeof(uint8_t)+256+sizeof(uint8_t)];
    int i;

    buffer[0] = len-1;

    for (i=0; i<len; i++)
        buffer[1+i] = data[i];

    return handle->write_data(handle, buffer, sizeof(uint8_t)+len);
}

/* keep checking for ack until we receive a ack or nack */
static uint8_t read_ack_loop(handle_t *handle)
{
    uint8_t ret;

    do {
        ret = handle->read_ack(handle);
    } while (ret != CMD_ACK && ret != CMD_NACK);

    return ret;
}

/* erase a single sector */
uint8_t erase_sector(handle_t *handle, uint16_t sector)
{
    uint8_t ret;

    handle->write_cmd(handle, handle->cmd_erase);
    ret = handle->read_ack(handle);
    if (sector < 0xFFF0 && ret == CMD_ACK) {
        write_cnt(handle, 0x0000);
        ret = read_ack_loop(handle);
    }
    if (ret == CMD_ACK) {
        write_cnt(handle, sector);
        ret = read_ack_loop(handle);
    }
    return ret;
}

/* read memory - this will chop the request into 256 byte reads */
uint8_t read_memory(handle_t *handle, uint32_t addr, uint32_t length, uint8_t *buffer)
{
    uint8_t ret = CMD_ACK;
    uint32_t offset = 0;

    while (ret == CMD_ACK && length > offset) {
        handle->write_cmd(handle, handle->cmd_read_memory);
        ret = handle->read_ack(handle);
        if (ret == CMD_ACK) {
            write_addr(handle, addr+offset);
            ret = read_ack_loop(handle);
            if (ret == CMD_ACK) {
                if (length-offset >= 256) {
                    write_len(handle, 256);
                    ret = read_ack_loop(handle);
                    if (ret == CMD_ACK) {
                        handle->read_data(handle, &buffer[offset], 256);
                        offset += 256;
                    }
                } else {
                    write_len(handle, length-offset);
                    ret = read_ack_loop(handle);
                    if (ret == CMD_ACK) {
                        handle->read_data(handle, &buffer[offset], length - offset);
                        offset = length;
                    }
                }
            }
        }
    }

    return ret;
}

/* write memory - this will chop the request into 256 byte writes */
uint8_t write_memory(handle_t *handle, uint32_t addr, uint32_t length, uint8_t *buffer)
{
    uint8_t ret = CMD_ACK;
    uint32_t offset = 0;

    while (ret == CMD_ACK && length > offset) {
        handle->write_cmd(handle, handle->cmd_write_memory);
        ret = handle->read_ack(handle);
        if (ret == CMD_ACK) {
            write_addr(handle, addr+offset);
            ret = read_ack_loop(handle);
            if (ret == CMD_ACK) {
                if (length-offset >= 256) {
                    write_len_data(handle, 256, &buffer[offset]);
                    offset += 256;
                } else {
                    write_len_data(handle, length-offset, &buffer[offset]);
                    offset = length;
                }
                ret = read_ack_loop(handle);
            }
        }
    }

    return ret;
}
