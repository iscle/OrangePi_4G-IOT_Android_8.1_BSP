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
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <stdint.h>
#include <fcntl.h>
#include <malloc.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <stdbool.h>
#include <time.h>
#include <errno.h>

#include "stm32_bl.h"
#include "stm32f4_crc.h"
#include "i2c.h"
#include "spi.h"
#include "uart.h"

enum USE_INTERFACE {
    USE_SPI,
    USE_I2C,
    USE_UART,
};

static inline size_t pad(ssize_t length)
{
    return (length + 3) & ~3;
}

static inline size_t tot_len(ssize_t length)
{
    // [TYPE:1] [LENGTH:3] [DATA] [PAD:0-3] [CRC:4]
    return sizeof(uint32_t) + pad(length) + sizeof(uint32_t);
}

ssize_t write_byte(int fd, uint8_t byte)
{
    ssize_t ret;

    do {
        ret = write(fd, &byte, 1);
    } while (ret == 0 || (ret == -1 && errno == EINTR));

    return ret;
}

int main(int argc, char *argv[])
{
    uint8_t addr = 0x39;
    char device[] = "/dev/spidev7.0";
    int gpio_nreset = 59;
    char gpio_dev[30];
    struct stat buf;
    uint8_t *buffer;
    uint32_t crc;
    i2c_handle_t i2c_handle;
    spi_handle_t spi_handle;
    uart_handle_t uart_handle;
    handle_t *handle;
    char options[] = "d:e:w:a:t:r:l:g:csiu";
    char *dev = device;
    int opt;
    uint32_t address = 0x08000000;
    char *write_filename = NULL;
    char *read_filename = NULL;
    int sector = -1;
    int do_crc = 0;
    uint8_t type = 0x11;
    ssize_t length = 0;
    uint8_t ret;
    int use_iface = USE_SPI;
    int fd;
    int gpio;
    FILE *file;
    int val;
    struct timespec ts;

    if (argc == 1) {
        printf("Usage: %s\n", argv[0]);
        printf("  -s (use spi. default)\n");
        printf("  -i (use i2c)\n");
        printf("  -u (use uart)\n");
        printf("  -g <gpio> (reset gpio. default: %d)\n", gpio_nreset);
        printf("  -d <device> (device. default: %s)\n", device);
        printf("  -e <sector> (sector to erase)\n");
        printf("  -w <filename> (filename to write to flash)\n");
        printf("  -r <filename> (filename to read from flash)\n");
        printf("  -l <length> (length to read/write)\n");
        printf("  -a <address> (address to write filename to. default: 0x%08x)\n",
               address);
        printf("  -c (add type, length, file contents, and CRC)\n");
        printf("  -t <type> (type value for -c option. default: %d)\n", type);
        return 0;
    }

    while ((opt = getopt(argc, argv, options)) != -1) {
        switch (opt) {
        case 'd':
            dev = optarg;
            break;
        case 'e':
            sector = strtol(optarg, NULL, 0);
            break;
        case 'w':
            write_filename = optarg;
            break;
        case 'r':
            read_filename = optarg;
            break;
        case 'l':
            length = strtol(optarg, NULL, 0);
            break;
        case 'a':
            address = strtol(optarg, NULL, 0);
            break;
        case 'c':
            do_crc = 1;
            break;
        case 't':
            type = strtol(optarg, NULL, 0);
            break;
        case 's':
            use_iface = USE_SPI;
            break;
        case 'i':
            use_iface = USE_I2C;
            break;
        case 'u':
            use_iface = USE_UART;
            break;
        case 'g':
            gpio_nreset = strtol(optarg, NULL, 0);
            break;
        }
    }

    if (use_iface == USE_UART)
        fd = open(dev, O_RDWR | O_NOCTTY | O_NDELAY);
    else
        fd = open(dev, O_RDWR);
    if (fd < 0) {
        perror("Error opening dev");
        return -1;
    }

    snprintf(gpio_dev, sizeof(gpio_dev), "/sys/class/gpio/gpio%d/value", gpio_nreset);
    gpio = open(gpio_dev, O_WRONLY);
    if (gpio < 0) {
        perror("Error opening nreset gpio");
    } else {
        if (write_byte(gpio, '1') < 0)
            perror("Failed to set gpio to 1");
        close(gpio);
        ts.tv_sec = 0;
        ts.tv_nsec = 5000000;
        nanosleep(&ts, NULL);
    }

    if (use_iface == USE_SPI) {
        handle = &spi_handle.handle;
        spi_handle.fd = fd;

        val = spi_init(handle);
    } else if (use_iface == USE_UART) {
        handle = &uart_handle.handle;
        uart_handle.fd = fd;

        val = uart_init(handle);
    } else {
        handle = &i2c_handle.handle;
        i2c_handle.fd = fd;
        i2c_handle.addr = addr;

        val = i2c_init(handle);
    }

    if (val < 0)
        return val;

    if (sector >= 0) {
        printf("Erasing sector %d\n", sector);
        ret = erase_sector(handle, sector);
        if (ret == CMD_ACK)
            printf("Erase succeeded\n");
        else
            printf("Erase failed\n");
    }

    if (write_filename != NULL) {
        file = fopen(write_filename, "r");
        if (!file) {
            perror("Error opening input file");
            return -1;
        }

        if (fstat(fileno(file), &buf) < 0) {
            perror("error stating file");
            return -1;
        }

        /*
         * For CRC: (when writing to eedata/shared)
         *   [TYPE:1] [LENGTH:3] [DATA] [PAD:0-3] [CRC:4]
         * Otherwise:
         *   [DATA]
         */
        buffer = calloc(tot_len(buf.st_size), 1);
        if (length == 0 || length > buf.st_size)
            length = buf.st_size;

        if (fread(&buffer[sizeof(uint32_t)], 1, length, file) < (size_t)length) {
            perror("Error reading input file");
            free(buffer);
            fclose(file);
            return -1;
        }

        printf("Writing %zd bytes from %s to 0x%08x\n", length,
               write_filename, address);

        if (do_crc) {
            /* Populate TYPE, LENGTH, and CRC */
            buffer[0] = type;
            buffer[1] = (length >> 16) & 0xFF;
            buffer[2] = (length >>  8) & 0xFF;
            buffer[3] = (length      ) & 0xFF;
            crc = ~stm32f4_crc32(buffer, sizeof(uint32_t) + length);

            memcpy(&buffer[sizeof(uint32_t) + pad(length)],
                   &crc, sizeof(uint32_t));

            ret = write_memory(handle, address,
                               tot_len(length), buffer);
        } else {
            /* Skip over space reserved for TYPE and LENGTH */
            ret = write_memory(handle, address,
                               length, &buffer[sizeof(uint32_t)]);
        }

        if (ret == CMD_ACK)
            printf("Write succeeded\n");
        else
            printf("Write failed\n");

        free(buffer);
        fclose(file);
    }

    if (read_filename != NULL) {
        file = fopen(read_filename, "w");
        if (!file) {
            perror("Error opening output file");
            return -1;
        }

        if (length > 0) {
            /* If passed in a length, just read that many bytes */
            buffer = calloc(length, 1);

            ret = read_memory(handle, address, length, buffer);
            if (ret == CMD_ACK) {
                if (fwrite(buffer, 1, length, file) < (size_t)length)
                    perror("Failed to write all read bytes to file");

                printf("Read %zd bytes from %s @ 0x%08x\n",
                       length, read_filename, address);
            } else {
                printf("Read failed\n");
            }
            free(buffer);
        } else if (do_crc) {
            /* otherwise if crc specified, read type, length, data, and crc */
            uint8_t tmp_buf[sizeof(uint32_t)];
            ret = read_memory(handle, address, sizeof(uint32_t), tmp_buf);
            if (ret == CMD_ACK) {
                type = tmp_buf[0];
                length = ((tmp_buf[1] << 16) & 0x00FF0000) |
                         ((tmp_buf[2] <<  8) & 0x0000FF00) |
                         ((tmp_buf[3]      ) & 0x000000FF);

                if (type != 0xFF) {
                    buffer = calloc(tot_len(length), 1);
                    ret = read_memory(handle, address,
                                      tot_len(length), buffer);
                    if (ret == CMD_ACK) {
                        crc = stm32f4_crc32(buffer, tot_len(length));
                        if (fwrite(buffer, 1, tot_len(length), file) < tot_len(length))
                            perror("Failed to write all read bytes to file");

                        printf("Read %zd bytes from %s @ 0x%08x (type %02x, crc %s)\n",
                               length, read_filename, address, type,
                               crc == STM32F4_CRC_RESIDUE ? "good" : "bad");
                    } else {
                        printf("Read of payload failed\n");
                    }
                    free(buffer);
                } else {
                    printf("Read invalid type: 0xFF\n");
                }
            } else {
                printf("Read of header failed\n");
            }
        } else {
            printf("No length or crc specified for read\n");
        }
        fclose(file);
    }

    gpio = open(gpio_dev, O_WRONLY);
    if (gpio < 0) {
        perror("Error opening nreset gpio");
    } else {
        if (write_byte(gpio, '0') < 0)
            perror("Failed to set gpio to 0");
        close(gpio);
    }

    close(fd);

    return 0;
}
