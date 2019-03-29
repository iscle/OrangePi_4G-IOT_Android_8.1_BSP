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

#ifndef _STM32_BL_H_
#define _STM32_BL_H_

/*
 * cmd_erase, cmd_read_memory, cmd_write_memory - byte to send for cmd
 *
 * write_data - write length bytes of data
 *   (function must checksum data. space reserved for checksum in buffer)
 * write_cmd - write cmd
 * read_data - read length bytes of data
 * read_ack - read ack
 * functions return results of command
 */
typedef struct handle
{
    uint8_t cmd_erase;
    uint8_t cmd_read_memory;
    uint8_t cmd_write_memory;

    uint8_t (*write_data)(struct handle *, uint8_t *buffer, int length);
    uint8_t (*write_cmd)(struct handle *, uint8_t cmd);
    uint8_t (*read_data)(struct handle *, uint8_t *buffer, int length);
    uint8_t (*read_ack)(struct handle *);
} handle_t;

uint8_t checksum(handle_t *handle, uint8_t *bytes, int length);
uint8_t erase_sector(handle_t *handle, uint16_t sector);
uint8_t read_memory(handle_t *handle, uint32_t addr, uint32_t length, uint8_t *buffer);
uint8_t write_memory(handle_t *handle, uint32_t addr, uint32_t length, uint8_t *buffer);

/*
 * Bootloader commands
 * _NS versions are no-stretch.
 * will return CMD_BUSY instead of stretching the clock
 */

#define CMD_GET				0x00
#define CMD_GET_VERSION			0x01
#define CMD_GET_ID			0x02
#define CMD_READ_MEMORY			0x11
#define CMD_NACK			0x1F
#define CMD_GO				0x21
#define CMD_WRITE_MEMORY		0x31
#define CMD_WRITE_MEMORY_NS		0x32
#define CMD_ERASE			0x44
#define CMD_ERASE_NS			0x45
#define CMD_SOF				0x5A
#define CMD_WRITE_PROTECT		0x63
#define CMD_WRITE_PROTECT_NS		0x64
#define CMD_WRITE_UNPROTECT		0x73
#define CMD_WRITE_UNPROTECT_NS		0x74
#define CMD_BUSY			0x76
#define CMD_ACK				0x79
#define CMD_UART_ENABLE			0x7F
#define CMD_READOUT_PROTECT		0x82
#define CMD_READOUT_PROTECT_NS		0x83
#define CMD_READOUT_UNPROTECT		0x92
#define CMD_READOUT_UNPROTECT_NS	0x93
#define CMD_SOF_ACK			0xA5

#endif /* _STM32_BL_H_ */
