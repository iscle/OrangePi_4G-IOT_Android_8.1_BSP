/*
 * Copyright 2012 The Android Open Source Project
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

#ifndef BT_VENDOR_QCOM_H
#define BT_VENDOR_QCOM_H

#include <stdbool.h>
#include "bt_vendor_lib.h"

#ifndef FALSE
#define FALSE  0
#endif

#ifndef TRUE
#define TRUE   (!FALSE)
#endif

#define STREAM_TO_UINT16(u16, p) {u16 = ((uint16_t)(*(p)) + (((uint16_t)(*((p) + 1))) << 8)); (p) += 2;}
#define UINT16_TO_STREAM(p, u16) {*(p)++ = (uint8_t)(u16); *(p)++ = (uint8_t)((u16) >> 8);}
#define UINT32_TO_STREAM(p, u32) {*(p)++ = (uint8_t)(u32); *(p)++ = (uint8_t)((u32) >> 8); *(p)++ = (uint8_t)((u32) >> 16); *(p)++ = (uint8_t)((u32) >> 24);}

typedef enum {
    BT_SOC_DEFAULT = 0,
    BT_SOC_SMD = BT_SOC_DEFAULT,
    BT_SOC_AR3K,
    BT_SOC_ROME,
    BT_SOC_CHEROKEE,
    /* Add chipset type here */
    BT_SOC_RESERVED
}bt_soc_type;

typedef enum {
    FM_VND_OP_POWER_CTRL = (unsigned int)BT_VND_OP_A2DP_OFFLOAD_STOP + 1,
    BT_VND_OP_FM_USERIAL_OPEN,
    BT_VND_OP_FM_USERIAL_CLOSE,
}bt_fm_serial;

typedef enum {
    BT_VND_OP_ANT_USERIAL_OPEN = 254,
    BT_VND_OP_ANT_USERIAL_CLOSE
}ant_serial;

/* HW_NEED_END_WITH_HCI_RESET

    code implementation of sending a HCI_RESET command during the epilog
    process. It calls back to the callers after command complete of HCI_RESET
    is received.

    Default TRUE .
*/
#ifndef HW_NEED_END_WITH_HCI_RESET
#define HW_NEED_END_WITH_HCI_RESET TRUE
#endif

#define HCI_RESET  0x0C03
#define HCI_CMD_PREAMBLE_SIZE 3
#define HCI_EVT_CMD_CMPL_STATUS_RET_BYTE   5
#define HCI_EVT_CMD_CMPL_OPCODE        3
#define BT_PWR_CNTRL_DEVICE    "/dev/btpower"

enum {
    BT_STATUS_SUCCESS = 0,
    BT_STATUS_FAIL,
    BT_STATUS_INVAL,
    BT_STATUS_NOMEM,
    BT_STATUS_PROP_FAILURE,
};
#define BT_CMD_PWR_CTRL         0xbfad
struct bt_qcom_struct {
    int fd[2];
    int ant_fd;
    int fm_fd;
    bt_vendor_callbacks_t *cb;
    uint8_t bdaddr[6];
    int soc_type;
    int rfkill_id;
    char *rfkill_state;
    bool enable_extldo;
};
extern struct bt_qcom_struct q;

#endif /* BT_VENDOR_QCOM_H */

