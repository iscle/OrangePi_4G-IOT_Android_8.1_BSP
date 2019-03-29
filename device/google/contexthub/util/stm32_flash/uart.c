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
#include <termios.h>
#include <unistd.h>
#include <sys/fcntl.h>
#include <sys/ioctl.h>
#include <linux/i2c-dev.h>

#include "stm32_bl.h"
#include "uart.h"

uint8_t uart_write_data(handle_t *handle, uint8_t *buffer, int length)
{
    uart_handle_t *uart_handle = (uart_handle_t *)handle;

    buffer[length] = checksum(handle, buffer, length);

    if (write(uart_handle->fd, buffer, length + 1) == (length + 1))
        return CMD_ACK;
    else
        return CMD_NACK;
}

uint8_t uart_write_cmd(handle_t *handle, uint8_t cmd)
{
    uart_handle_t *uart_handle = (uart_handle_t *)handle;
    uint8_t buffer[2 * sizeof(uint8_t)] = { cmd, ~cmd };
    int length = 2 * sizeof(uint8_t);

    if (cmd == CMD_UART_ENABLE)
        length--;

    if (write(uart_handle->fd, buffer, length) == length)
        return CMD_ACK;
    else
        return CMD_NACK;
}

uint8_t uart_read_data(handle_t *handle, uint8_t *data, int length)
{
    uart_handle_t *uart_handle = (uart_handle_t *)handle;

    if (read(uart_handle->fd, data, length) == length)
        return CMD_ACK;
    else
        return CMD_NACK;
}

uint8_t uart_read_ack(handle_t *handle)
{
    uint8_t buffer;

    if (handle->read_data(handle, &buffer, sizeof(uint8_t)) == CMD_ACK)
        return buffer;
    else
        return CMD_BUSY;
}

int uart_init(handle_t *handle)
{
    uart_handle_t *uart_handle = (uart_handle_t *)handle;
    struct termios tio;
    int fl;

    handle->cmd_erase = CMD_ERASE;
    handle->cmd_read_memory = CMD_READ_MEMORY;
    handle->cmd_write_memory = CMD_WRITE_MEMORY;

    handle->write_data = uart_write_data;
    handle->write_cmd = uart_write_cmd;
    handle->read_data = uart_read_data;
    handle->read_ack = uart_read_ack;

    /* then switch the fd to blocking */
    fl = fcntl(uart_handle->fd, F_GETFL, 0);
    if (fl < 0)
        return fl;
    fl = fcntl(uart_handle->fd, F_SETFL,  fl & ~O_NDELAY);
    if (fl < 0)
        return fl;
    if (tcgetattr(uart_handle->fd, &tio))
        memset(&tio, 0, sizeof(tio));

    tio.c_cflag = CS8 | CLOCAL | CREAD | PARENB;
    cfsetospeed(&tio, B57600);
    cfsetispeed(&tio, B57600);
    tio.c_iflag = 0; /* turn off IGNPAR */
    tio.c_oflag = 0; /* turn off OPOST */
    tio.c_lflag = 0; /* turn off CANON, ECHO*, etc */
    tio.c_cc[VTIME] = 5;
    tio.c_cc[VMIN] = 0;
    tcflush(uart_handle->fd, TCIFLUSH);
    tcsetattr(uart_handle->fd, TCSANOW, &tio);

    /* Init USART */
    uart_write_cmd(handle, CMD_UART_ENABLE);
    if (uart_read_ack(handle) == CMD_ACK)
        return 0;

    return -1;
}
