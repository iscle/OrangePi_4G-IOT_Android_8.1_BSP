/*
 * Copyright 2012 The Android Open Source Project
 * Copyright (c) 2013, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
#ifndef HW_AR3K_H
#define HW_AR3K_H

#include <sys/socket.h>

/******************************************************************************
**  Constants & Macros
******************************************************************************/
#define MAX_CNT_RETRY 100

#define HCI_MAX_CMD_SIZE   260
#define HCI_MAX_EVENT_SIZE  260
#define HCI_CHG_BAUD_CMD_OCF 0x0C
#define HCI_VENDOR_CMD_OGF 0x3F
#define WRITE_BDADDR_CMD_LEN 14
#define WRITE_BAUD_CMD_LEN   6
#define MAX_CMD_LEN WRITE_BDADDR_CMD_LEN
#define GET_VERSION_OCF 0x1E
/* Byte order conversions */
#if __BYTE_ORDER == __LITTLE_ENDIAN
#define htobs(d)  (d)
#define htobl(d)  (d)
#define btohs(d)  (d)
#define btohl(d)  (d)
#elif __BYTE_ORDER == __BIG_ENDIAN
#define htobs(d)  bswap_16(d)
#define htobl(d)  bswap_32(d)
#define btohs(d)  bswap_16(d)
#define btohl(d)  bswap_32(d)
#else
#error "Unknown byte order"
#endif

#define FW_PATH "/system/etc/firmware/ar3k/"

#define STREAM_TO_UINT16(u16, p) \
    {u16 = ((uint16_t)(*(p)) + (((uint16_t)(*((p) + 1))) << 8)); (p) += 2;}
#define UINT16_TO_STREAM(p, u16) \
    {*(p)++ = (uint8_t)(u16); *(p)++ = (uint8_t)((u16) >> 8);}
#define UINT32_TO_STREAM(p, u32) \
    {*(p)++ = (uint8_t)(u32); *(p)++ = (uint8_t)((u32) >> 8);\
    *(p)++ = (uint8_t)((u32) >> 16); *(p)++ = (uint8_t)((u32) >> 24);}

#define MAX_TAGS              50
#define PS_HDR_LEN            4
#define HCI_VENDOR_CMD_OGF    0x3F
#define HCI_PS_CMD_OCF        0x0B

#define VERIFY_CRC   9
#define PS_REGION    1
#define PATCH_REGION 2
#define BDADDR_FILE "ar3kbdaddr.pst"


#define MAX_PATCH_CMD 244
struct patch_entry {
    int16_t len;
    uint8_t data[MAX_PATCH_CMD];
};
#define HCI_UART_RAW_DEVICE    0
#define HCI_COMMAND_HDR_SIZE 3
#define PS_WRITE           1
#define PS_RESET           2
#define WRITE_PATCH        8
#define ENABLE_PATCH       11

#define HCI_PS_CMD_HDR_LEN 7
#define HCI_CMD_MAX_LEN             258
#define PS_RESET_PARAM_LEN 6
#define PS_RESET_CMD_LEN   (HCI_PS_CMD_HDR_LEN + PS_RESET_PARAM_LEN)

#define PS_ID_MASK         0xFF


#define LOCAL_NAME_BUFFER_LEN                   32
#define DEV_REGISTER      0x4FFC
#define GET_DEV_TYPE_OCF  0x05

#define HCIDEVUP            _IOW('H', 201, int)
#define OGF_VENDOR_CMD                  0x3f
#define EVT_CMD_COMPLETE_SIZE     3
#define EVT_CMD_STATUS                   0x0F
#define EVT_CMD_STATUS_SIZE         4
#define HCI_COMMAND_HDR_SIZE      3
#define HCI_EVENT_HDR_SIZE            2
#define HCI_EV_SUCCESS                    0x00
/* HCI Socket options */
#define HCI_DATA_DIR        1
#define HCI_FILTER              2
#define HCI_TIME_STAMP    3

/* HCI CMSG flags */
#define HCI_CMSG_DIR            0x0001
#define HCI_CMSG_TSTAMP     0x0002

#ifndef VENDOR_LPM_PROC_NODE
#define VENDOR_LPM_PROC_NODE "/sys/module/hci_uart/parameters/ath_lpm"
#endif

/* proc fs node for notifying write request */
#ifndef VENDOR_BTWRITE_PROC_NODE
#define VENDOR_BTWRITE_PROC_NODE "/sys/module/hci_uart/parameters/ath_btwrite"
#endif

/******************************************************************************
**  Local type definitions
******************************************************************************/
typedef struct {
    uint8_t b[6];
} __attribute__((packed)) bdaddr_t;

struct sockaddr_hci {
    sa_family_t hci_family;
    unsigned short  hci_dev;
    unsigned short  hci_channel;
};

struct tag_info {
    unsigned section;
    unsigned line_count;
    unsigned char_cnt;
    unsigned byte_count;
};

struct ps_cfg_entry {
    uint32_t id;
    uint32_t len;
    uint8_t *data;
};

struct ps_entry_type {
    unsigned char type;
    unsigned char array;
};

struct uart_t {
    char *type;
    int  m_id;
    int  p_id;
    int  proto;
    int  init_speed;
    int  speed;
    int  flags;
    int  pm;
    char *bdaddr;
    int  (*init) (int fd, struct uart_t *u, struct termios *ti);
    int  (*post) (int fd, struct uart_t *u, struct termios *ti);
};
#endif /* HW_AR3K_H */
