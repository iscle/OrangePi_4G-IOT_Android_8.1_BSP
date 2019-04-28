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

/******************************************************************************
 *
 *  Filename:      hardware.c
 *
 *  Description:   Contains controller-specific functions, like
 *                      firmware patch download
 *                      low power mode operations
 *
 ******************************************************************************/

#define LOG_TAG "bt_vendor"

#include <utils/Log.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <signal.h>
#include <time.h>
#include <errno.h>
#include <fcntl.h>
#include <dirent.h>
#include <ctype.h>
#include <cutils/properties.h>
#include <stdlib.h>
#include "bt_hci_bdroid.h"
#include "bt_vendor_qcom.h"
#include <string.h>
#define MAX_CNT_RETRY 100

int hw_config(int nState)
{
    char *szState[] = {"true", "false"};
    char *szReqSt = NULL;
    char szBtSocStatus[PROPERTY_VALUE_MAX] = {'\0', };

    if(nState == BT_VND_PWR_OFF)
        szReqSt = szState[1];
    else
        szReqSt = szState[0];

    if((property_get("bluetooth.status", szBtSocStatus, "") <= 0))
    {
       if(nState == BT_VND_PWR_ON ) {
          ALOGW("Hw_config: First Time BT on after boot.Starting hciattach daemon BTStatus=%s",szBtSocStatus);
          if (property_set("bluetooth.hciattach", szReqSt) < 0)
          {
              ALOGE("Hw_config: Property Setting fail");
              return -1;
          }
       }
    } else if( !(strncmp(szBtSocStatus, "on", strlen("on")))) {
          //BTSOC is already on
          ALOGW("Hw_config: nState = %d", nState);
    } else {
          ALOGW("Hw_config: trigerring hciattach");
          if (property_set("bluetooth.hciattach", szReqSt) < 0)
          {
              ALOGE("Hw_config: Property Setting fail");
              return -1;
          }
    }

    return 0;
}

int readTrpState()
{
    char szBtStatus[PROPERTY_VALUE_MAX] = {0, };
    if(property_get("bluetooth.status", szBtStatus, "") < 0){
        ALOGE("Fail to get bluetooth status");
        return FALSE;
    }

    if(!strncmp(szBtStatus, "on", strlen("on"))){
        ALOGI("bluetooth status is on");
        return TRUE;
    }
    return FALSE;
}

int is_hw_ready()
{
    int i=0;
    char szStatus[10] = {0,};

    for(i=MAX_CNT_RETRY; i>0; i--){
       //TODO :: checking routine
       if(readTrpState()==TRUE){
           break;
       }
       usleep(50*1000);
    }
    return (i==0)? FALSE:TRUE;
}

#if (HW_NEED_END_WITH_HCI_RESET == TRUE)
/*******************************************************************************
**
** Function         hw_epilog_cback
**
** Description      Callback function for Command Complete Events from HCI
**                  commands sent in epilog process.
**
** Returns          None
**
*******************************************************************************/
void hw_epilog_cback(void *p_mem)
{
    HC_BT_HDR *p_evt_buf = (HC_BT_HDR *) p_mem;
    char        *p_name, *p_tmp;
    uint8_t     *p, status;
    uint16_t    opcode;

    status = *((uint8_t *)(p_evt_buf + 1) + HCI_EVT_CMD_CMPL_STATUS_RET_BYTE);
    p = (uint8_t *)(p_evt_buf + 1) + HCI_EVT_CMD_CMPL_OPCODE;
    STREAM_TO_UINT16(opcode,p);

    ALOGI("%s Opcode:0x%04X Status: %d", __FUNCTION__, opcode, status);

#ifdef BT_THREADLOCK_SAFE
    pthread_mutex_lock(&q_lock);
#endif
    if (!q) {
        ALOGE("hw_epilog_cback called with NULL context");
        goto out;
    }
    /* Must free the RX event buffer */
    q->cb->dealloc(p_evt_buf);

    /* Once epilog process is done, must call callback to notify caller */
    q->cb->epilog_cb(BT_VND_OP_RESULT_SUCCESS);
out:
#ifdef BT_THREADLOCK_SAFE
    pthread_mutex_unlock(&q_lock);
#endif
    return;
}

/*******************************************************************************
**
** Function         hw_epilog_process
**
** Description      Sample implementation of epilog process. This process is
**                  called with q_lock held and q->cb is assumed to be valid.
**
** Returns          None
**
*******************************************************************************/
void __hw_epilog_process(void)
{
    HC_BT_HDR  *p_buf = NULL;
    uint8_t     *p;

    ALOGI("hw_epilog_process");

    /* Sending a HCI_RESET */
    /* Must allocate command buffer via HC's alloc API */
    p_buf = (HC_BT_HDR *) q->cb->alloc(BT_HC_HDR_SIZE + HCI_CMD_PREAMBLE_SIZE);
    if (p_buf)
    {
        p_buf->event = MSG_STACK_TO_HC_HCI_CMD;
        p_buf->offset = 0;
        p_buf->layer_specific = 0;
        p_buf->len = HCI_CMD_PREAMBLE_SIZE;

        p = (uint8_t *) (p_buf + 1);
        UINT16_TO_STREAM(p, HCI_RESET);
        *p = 0; /* parameter length */

        /* Send command via HC's xmit_cb API */
        q->cb->xmit_cb(HCI_RESET, p_buf, hw_epilog_cback);
    }
    else
    {
        ALOGE("vendor lib epilog process aborted [no buffer]");
        q->cb->epilog_cb(BT_VND_OP_RESULT_FAIL);
    }
}
#endif // (HW_NEED_END_WITH_HCI_RESET == TRUE)
