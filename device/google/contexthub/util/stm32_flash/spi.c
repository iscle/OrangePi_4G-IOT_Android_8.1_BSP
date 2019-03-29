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
#include <sys/ioctl.h>
#include <linux/spi/spidev.h>

#include "spi.h"

uint8_t spi_write_data(handle_t *handle, uint8_t *buffer, int length)
{
    spi_handle_t *spi_handle = (spi_handle_t *)handle;
    struct spi_ioc_transfer xfer =
    {
        .len = length + 1,
        .tx_buf = (unsigned long)buffer,
        .rx_buf = (unsigned long)buffer,
        .cs_change = 1,
    };

    buffer[length] = checksum(handle, buffer, length);

    if (ioctl(spi_handle->fd, SPI_IOC_MESSAGE(1), &xfer) >= 0)
        return buffer[length];
    else
        return CMD_NACK;
}

uint8_t spi_write_cmd(handle_t *handle, uint8_t cmd)
{
    spi_handle_t *spi_handle = (spi_handle_t *)handle;
    uint8_t buffer[] =
    {
        CMD_SOF,
        cmd,
        ~cmd
    };
    struct spi_ioc_transfer xfer =
    {
        .len = sizeof(buffer),
        .tx_buf = (unsigned long)buffer,
        .rx_buf = (unsigned long)buffer,
        .cs_change = 1,
    };

    if (ioctl(spi_handle->fd, SPI_IOC_MESSAGE(1), &xfer) >= 0)
        return CMD_ACK;
    else
        return CMD_NACK;
}

uint8_t spi_read_data(handle_t *handle, uint8_t *data, int length)
{
    spi_handle_t *spi_handle = (spi_handle_t *)handle;
    uint8_t buffer[] =
    {
        0x00
    };
    struct spi_ioc_transfer xfer[] =
    {
        {
            .len = sizeof(buffer),
            .tx_buf = (unsigned long)buffer,
            .rx_buf = (unsigned long)buffer,
        },
        {
            .len = length,
            .tx_buf = (unsigned long)data,
            .rx_buf = (unsigned long)data,
            .cs_change = 1,
        }
    };

    if (ioctl(spi_handle->fd, SPI_IOC_MESSAGE(2), xfer) >= 0)
        return CMD_ACK;
    else
        return CMD_NACK;
}

uint8_t spi_read_ack(handle_t *handle)
{
    spi_handle_t *spi_handle = (spi_handle_t *)handle;
    uint16_t timeout = 65535;
    uint8_t ret;
    uint8_t buffer[] =
    {
        0x00,
    };
    struct spi_ioc_transfer xfer =
    {
        .len = sizeof(buffer),
        .tx_buf = (unsigned long)buffer,
        .rx_buf = (unsigned long)buffer,
        .cs_change = 1,
    };

    if (ioctl(spi_handle->fd, SPI_IOC_MESSAGE(1), &xfer) >= 0) {
        do {
            ioctl(spi_handle->fd, SPI_IOC_MESSAGE(1), &xfer);
            timeout --;
        } while (buffer[0] != CMD_ACK && buffer[0] != CMD_NACK && timeout > 0);

        if (buffer[0] != CMD_ACK && buffer[0] != CMD_NACK && timeout == 0)
            ret = CMD_NACK;
        else
            ret = buffer[0];
        ioctl(spi_handle->fd, SPI_IOC_MESSAGE(1), &xfer);

        return ret;
    } else {
        return CMD_NACK;
    }
}

uint8_t spi_sync(handle_t *handle)
{
    spi_handle_t *spi_handle = (spi_handle_t *)handle;
    uint8_t buffer[] =
    {
        CMD_SOF,
    };
    struct spi_ioc_transfer xfer =
    {
        .len = sizeof(buffer),
        .tx_buf = (unsigned long)buffer,
        .rx_buf = (unsigned long)buffer,
        .cs_change = 1,
    };

    if (ioctl(spi_handle->fd, SPI_IOC_MESSAGE(1), &xfer) >= 0)
        return handle->read_ack(handle);
    else
        return CMD_NACK;
}

int spi_init(handle_t *handle)
{
    spi_handle_t *spi_handle = (spi_handle_t *)handle;
    uint8_t tmp8;
    uint32_t tmp32;

    handle->cmd_erase = CMD_ERASE;
    handle->cmd_read_memory = CMD_READ_MEMORY;
    handle->cmd_write_memory = CMD_WRITE_MEMORY;

    handle->write_data = spi_write_data;
    handle->write_cmd = spi_write_cmd;
    handle->read_data = spi_read_data;
    handle->read_ack = spi_read_ack;

    tmp8 = SPI_MODE_0;
    if (ioctl(spi_handle->fd, SPI_IOC_WR_MODE, &tmp8) < 0) {
        perror("Error setting mode");
        return -1;
    }

    tmp32 = 8000000;
    if (ioctl(spi_handle->fd, SPI_IOC_WR_MAX_SPEED_HZ, &tmp32) < 0) {
        perror("Error setting speed");
        return -1;
    }

    tmp8 = 8;
    if (ioctl(spi_handle->fd, SPI_IOC_WR_BITS_PER_WORD, &tmp8) < 0) {
        perror("Error setting bits per word");
        return -1;
    }

    if (spi_sync(handle) == CMD_ACK)
        return 0;
    else
        return -1;
}
