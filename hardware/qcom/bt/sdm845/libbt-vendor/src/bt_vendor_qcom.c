/*
 * Copyright 2012 The Android Open Source Project
 * Copyright (c) 2013, The Linux Foundation. All rights reserved.
 * Not a Contribution.
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
 *  Filename:      bt_vendor_qcom.c
 *
 *  Description:   vendor specific library implementation
 *
 ******************************************************************************/
#define LOG_TAG "bt_vendor"
#define BLUETOOTH_MAC_ADDR_BOOT_PROPERTY "ro.boot.btmacaddr"

#include <utils/Log.h>
#include <cutils/properties.h>
#include <fcntl.h>
#include <termios.h>
#include "bt_vendor_qcom.h"
#include "hci_uart.h"
#include "hci_smd.h"
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <cutils/sockets.h>
#include <linux/un.h>
#include "bt_vendor_persist.h"
#include "hw_rome.h"
#include "bt_vendor_lib.h"
#define WAIT_TIMEOUT 200000
#define BT_VND_OP_GET_LINESPEED 30

#define STOP_WCNSS_FILTER 0xDD
#define STOP_WAIT_TIMEOUT   1000

#define SOC_INIT_PROPERTY "wc_transport.soc_initialized"

#define BT_VND_FILTER_START "wc_transport.start_hci"

#define CMD_TIMEOUT  0x22

static void wait_for_patch_download(bool is_ant_req);
static bool is_debug_force_special_bytes(void);
int connect_to_local_socket(char* name);
/******************************************************************************
**  Externs
******************************************************************************/
extern int hw_config(int nState);
extern int is_hw_ready();
extern int chipset_ver;

/******************************************************************************
**  Variables
******************************************************************************/
struct bt_qcom_struct *q = NULL;
pthread_mutex_t q_lock = PTHREAD_MUTEX_INITIALIZER;

int userial_clock_operation(int fd, int cmd);
int ath3k_init(int fd, int speed, int init_speed, char *bdaddr, struct termios *ti);
int userial_vendor_get_baud(void);
int readTrpState();
void lpm_set_ar3k(uint8_t pio, uint8_t action, uint8_t polarity);
bool is_download_progress();

static const tUSERIAL_CFG userial_init_cfg =
{
    (USERIAL_DATABITS_8 | USERIAL_PARITY_NONE | USERIAL_STOPBITS_1),
    USERIAL_BAUD_115200
};

#if (HW_NEED_END_WITH_HCI_RESET == TRUE)
void __hw_epilog_process(void);
#endif

#ifdef WIFI_BT_STATUS_SYNC
#include <string.h>
#include <errno.h>
#include <dlfcn.h>
#include "cutils/properties.h"

static const char WIFI_PROP_NAME[]    = "wlan.driver.status";
static const char SERVICE_PROP_NAME[]    = "bluetooth.hsic_ctrl";
static const char BT_STATUS_NAME[]    = "bluetooth.enabled";
static const char WIFI_SERVICE_PROP[] = "wlan.hsic_ctrl";

#define WIFI_BT_STATUS_LOCK    "/data/connectivity/wifi_bt_lock"
int isInit=0;
#endif /* WIFI_BT_STATUS_SYNC */
bool is_soc_initialized(void);

/******************************************************************************
**  Local type definitions
******************************************************************************/

/******************************************************************************
**  Functions
******************************************************************************/
#ifdef WIFI_BT_STATUS_SYNC
int bt_semaphore_create(void)
{
    int fd;

    fd = open(WIFI_BT_STATUS_LOCK, O_RDONLY);

    if (fd < 0)
        ALOGE("can't create file\n");

    return fd;
}

int bt_semaphore_get(int fd)
{
    int ret;

    if (fd < 0)
        return -1;

    ret = flock(fd, LOCK_EX);
    if (ret != 0) {
        ALOGE("can't hold lock: %s\n", strerror(errno));
        return -1;
    }

    return ret;
}

int bt_semaphore_release(int fd)
{
    int ret;

    if (fd < 0)
        return -1;

    ret = flock(fd, LOCK_UN);
    if (ret != 0) {
        ALOGE("can't release lock: %s\n", strerror(errno));
        return -1;
    }

    return ret;
}

int bt_semaphore_destroy(int fd)
{
    if (fd < 0)
        return -1;

    return close (fd);
}

int bt_wait_for_service_done(void)
{
    char service_status[PROPERTY_VALUE_MAX];
    int count = 30;

    ALOGE("%s: check\n", __func__);

    /* wait for service done */
    while (count-- > 0) {
        property_get(WIFI_SERVICE_PROP, service_status, NULL);

        if (strcmp(service_status, "") != 0) {
            usleep(200000);
        } else {
            break;
        }
    }

    return 0;
}

#endif /* WIFI_BT_STATUS_SYNC */

/** Get Bluetooth SoC type from system setting */
static int get_bt_soc_type()
{
    int ret = 0;
    char bt_soc_type[PROPERTY_VALUE_MAX];

    ALOGI("bt-vendor : get_bt_soc_type");

    ret = property_get("qcom.bluetooth.soc", bt_soc_type, NULL);
    if (ret != 0) {
        ALOGI("qcom.bluetooth.soc set to %s\n", bt_soc_type);
        if (!strncasecmp(bt_soc_type, "rome", sizeof("rome"))) {
            return BT_SOC_ROME;
        }
        else if (!strncasecmp(bt_soc_type, "cherokee", sizeof("cherokee"))) {
            return BT_SOC_CHEROKEE;
        }
        else if (!strncasecmp(bt_soc_type, "ath3k", sizeof("ath3k"))) {
            return BT_SOC_AR3K;
        }
        else if (!strncasecmp(bt_soc_type, "cherokee", sizeof("cherokee"))) {
            return BT_SOC_CHEROKEE;
        }
        else {
            ALOGI("qcom.bluetooth.soc not set, so using default.\n");
            return BT_SOC_DEFAULT;
        }
    }
    else {
        ALOGE("%s: Failed to get soc type", __FUNCTION__);
        ret = BT_SOC_DEFAULT;
    }

    return ret;
}

bool can_perform_action(char action) {
    bool can_perform = false;
    char ref_count[PROPERTY_VALUE_MAX];
    char inProgress[PROPERTY_VALUE_MAX] = {'\0'};
    int value, ret;

    property_get("wc_transport.ref_count", ref_count, "0");

    value = atoi(ref_count);
    ALOGV("%s: ref_count: %s\n",__func__,  ref_count);

    if(action == '1') {
        ALOGV("%s: on : value is: %d", __func__, value);
        if(value == 1)
        {
            if ((is_soc_initialized() == true)
               || is_download_progress() || get_bt_soc_type() == BT_SOC_CHEROKEE)
          {
            value++;
            ALOGV("%s: on : value is incremented to : %d", __func__, value);
          }
        }
        else
        {
             value++;
        }

        if (value == 1)
            can_perform = true;
        else if (value > 3)
            return false;
    }
    else {
        ALOGV("%s: off : value is: %d", __func__, value);
        if (--value <= 0) {
            ALOGE("%s: BT turn off twice before BT On(ref_count=%d)\n",
                    __func__, value);
            value = 0;
            can_perform = true;
        }
    }

    snprintf(ref_count, 3, "%d", value);
    ALOGV("%s: updated ref_count is: %s", __func__, ref_count);

    ret  = property_set("wc_transport.ref_count", ref_count);
    if (ret < 0) {
        ALOGE("%s: Error while updating property: %d\n", __func__, ret);
        return false;
    }
    ALOGV("%s returning %d", __func__, can_perform);
    return can_perform;
}

void stop_hci_filter() {
       char value[PROPERTY_VALUE_MAX] = {'\0'};
       int retval, filter_ctrl, i;
       char stop_val = STOP_WCNSS_FILTER;
       int soc_type = BT_SOC_DEFAULT;

       ALOGV("%s: Entry ", __func__);

       if ((soc_type = get_bt_soc_type()) == BT_SOC_CHEROKEE) {
           property_get("wc_transport.hci_filter_status", value, "0");
           if (strcmp(value, "0") == 0) {
               ALOGI("%s: hci_filter has been stopped already", __func__);
           }
           else {
               filter_ctrl = connect_to_local_socket("wcnssfilter_ctrl");
               if (filter_ctrl < 0) {
                   ALOGI("%s: Error while connecting to CTRL_SOCK, filter should stopped: %d",
                          __func__, filter_ctrl);
               }
               else {
                   retval = write(filter_ctrl, &stop_val, 1);
                   if (retval != 1) {
                       ALOGI("%s: problem writing to CTRL_SOCK, ignore: %d", __func__, retval);
                       //Ignore and fallback
                   }

                   close(filter_ctrl);
               }
           }

           /* Ensure Filter is closed by checking the status before
              RFKILL 0 operation. this should ideally comeout very
              quick */
           for(i=0; i<500; i++) {
               property_get(BT_VND_FILTER_START, value, "false");
               if (strcmp(value, "false") == 0) {
                   ALOGI("%s: WCNSS_FILTER stopped", __func__);
                   usleep(STOP_WAIT_TIMEOUT * 10);
                   break;
               } else {
                   /*sleep of 1ms, This should give enough time for FILTER to
                   exit with all necessary cleanup*/
                   usleep(STOP_WAIT_TIMEOUT);
               }
           }

           /*Never use SIGKILL to stop the filter*/
           /* Filter will be stopped by below two conditions
            - by Itself, When it realizes there are no CONNECTED clients
            - Or through STOP_WCNSS_FILTER byte on Control socket
            both of these ensure clean shutdown of chip
           */
           //property_set(BT_VND_FILTER_START, "false");
       } else if (soc_type == BT_SOC_ROME) {
           property_set(BT_VND_FILTER_START, "false");
       } else {
           ALOGI("%s: Unknown soc type %d, Unexpected!", __func__, soc_type);
       }

       ALOGV("%s: Exit ", __func__);
}

int start_hci_filter() {
       ALOGV("%s: Entry ", __func__);
       int i, init_success = -1;
       char value[PROPERTY_VALUE_MAX] = {'\0'};

       property_get(BT_VND_FILTER_START, value, false);

       if (strcmp(value, "true") == 0) {
           ALOGI("%s: hci_filter has been started already", __func__);
           //Filter should have been started OR in the process of initializing
           //Make sure of hci_filter_status and return the state based on it
       } else {

           property_set("wc_transport.hci_filter_status", "0");
           property_set(BT_VND_FILTER_START, "true");
           ALOGV("%s: %s set to true ", __func__, BT_VND_FILTER_START );
       }

       /*If there are back to back ON requests from different clients,
         All client should come and stuck in this while loop till FILTER
         comesup and ready to accept the connections */
       //sched_yield();
       for(i=0; i<45; i++) {
          property_get("wc_transport.hci_filter_status", value, "0");
          if (strcmp(value, "1") == 0) {
               init_success = 1;
               break;
           } else {
               usleep(WAIT_TIMEOUT);
           }
        }
        ALOGV("start_hcifilter status:%d after %f seconds \n", init_success, 0.2*i);

        ALOGV("%s: Exit ", __func__);
        return init_success;
}

/*
 * Bluetooth Controller power up or shutdown, this function is called with
 * q_lock held and q is non-NULL
 */
static int bt_powerup(int en )
{
    char rfkill_type[64], *enable_ldo_path = NULL;
    char type[16], enable_ldo[6];
    int fd = 0, size, i, ret, fd_ldo, fd_btpower;

    char disable[PROPERTY_VALUE_MAX];
    char state;
    char on = (en)?'1':'0';

#ifdef WIFI_BT_STATUS_SYNC
    char wifi_status[PROPERTY_VALUE_MAX];
    int lock_fd;
#endif /*WIFI_BT_STATUS_SYNC*/

    ALOGI("bt_powerup: %c", on);

    /* Check if rfkill has been disabled */
    ret = property_get("ro.rfkilldisabled", disable, "0");
    if (!ret ){
        ALOGE("Couldn't get ro.rfkilldisabled (%d)", ret);
        return -1;
    }
    /* In case rfkill disabled, then no control power*/
    if (strcmp(disable, "1") == 0) {
        ALOGI("ro.rfkilldisabled : %s", disable);
        return -1;
    }

#ifdef WIFI_BT_STATUS_SYNC
    lock_fd = bt_semaphore_create();
    bt_semaphore_get(lock_fd);
    bt_wait_for_service_done();
#endif

    /* Assign rfkill_id and find bluetooth rfkill state path*/
    for(i = 0; (q->rfkill_id == -1) && (q->rfkill_state == NULL); i++)
    {
        snprintf(rfkill_type, sizeof(rfkill_type), "/sys/class/rfkill/rfkill%d/type", i);
        if ((fd = open(rfkill_type, O_RDONLY)) < 0)
        {
            ALOGE("open(%s) failed: %s (%d)\n", rfkill_type, strerror(errno), errno);

#ifdef WIFI_BT_STATUS_SYNC
            bt_semaphore_release(lock_fd);
            bt_semaphore_destroy(lock_fd);
#endif
            return -1;
        }

        size = read(fd, &type, sizeof(type));
        close(fd);

        if ((size >= 9) && !memcmp(type, "bluetooth", 9))
        {
            asprintf(&q->rfkill_state, "/sys/class/rfkill/rfkill%d/state", q->rfkill_id = i);
            break;
        }
    }

    /* Get rfkill State to control */
    if (q->rfkill_state != NULL)
    {
        if ((fd = open(q->rfkill_state, O_RDWR)) < 0)
        {
            ALOGE("open(%s) for write failed: %s (%d)", q->rfkill_state, strerror(errno), errno);
#ifdef WIFI_BT_STATUS_SYNC
            bt_semaphore_release(lock_fd);
            bt_semaphore_destroy(lock_fd);
#endif

            return -1;
        }
    }
    /* Always perform BT power action so as to have the chance to 
       recover BT power properly from un-expected error. */
#ifdef CHECK_BT_POWER_PERFORM_ACTION
    if(can_perform_action(on) == false) {
        ALOGE("%s:can't perform action as it is being used by other clients", __func__);
#ifdef WIFI_BT_STATUS_SYNC
            bt_semaphore_release(lock_fd);
            bt_semaphore_destroy(lock_fd);
#endif
            goto done;
    }
#else
    ALOGI("%s: always perform action", __func__);
#endif
    ret = asprintf(&enable_ldo_path, "/sys/class/rfkill/rfkill%d/device/extldo", q->rfkill_id);
    if( (ret < 0 ) || (enable_ldo_path == NULL) )
    {
        ALOGE("Memory Allocation failure");
        return -1;
    }
    if ((fd_ldo = open(enable_ldo_path, O_RDWR)) < 0) {
        ALOGE("open(%s) failed: %s (%d)", enable_ldo_path, strerror(errno), errno);
        return -1;
    }
    size = read(fd_ldo, &enable_ldo, sizeof(enable_ldo));
    close(fd_ldo);
    if (size <= 0) {
        ALOGE("read(%s) failed: %s (%d)", enable_ldo_path, strerror(errno), errno);
        return -1;
    }
    if (!memcmp(enable_ldo, "true", 4)) {
        ALOGI("External LDO has been configured");
        ret = property_set("wc_transport.extldo", "enabled");
        if (ret < 0) {
            ALOGI("%s: Not able to set property wc_transport.extldo\n", __func__);
        }
        q->enable_extldo = TRUE;
    }

    if(on == '0'){
        ALOGE("Stopping HCI filter as part of CTRL:OFF");
        stop_hci_filter();
        property_set("wc_transport.soc_initialized", "0");
    }

    if (q->soc_type >= BT_SOC_CHEROKEE && q->soc_type < BT_SOC_RESERVED) {
       ALOGI("open bt power devnode,send ioctl power op  :%d ",en);
       fd_btpower = open(BT_PWR_CNTRL_DEVICE, O_RDWR, O_NONBLOCK);
       if (fd_btpower < 0) {
           ALOGE("\nfailed to open bt device error = (%s)\n",strerror(errno));
#ifdef WIFI_BT_STATUS_SYNC
           bt_semaphore_release(lock_fd);
           bt_semaphore_destroy(lock_fd);
#endif
           return -1;
       }
       ret = ioctl(fd_btpower, BT_CMD_PWR_CTRL, (unsigned long)en);
        if (ret < 0) {
            ALOGE(" ioctl failed to power control:%d error =(%s)",ret,strerror(errno));
        }
        close(fd_btpower);
    } else {
       ALOGI("Write %c to rfkill\n", on);
       /* Write value to control rfkill */
       if(fd >= 0) {
           if ((size = write(fd, &on, 1)) < 0) {
               ALOGE("write(%s) failed: %s (%d)", q->rfkill_state, strerror(errno), errno);
#ifdef WIFI_BT_STATUS_SYNC
               bt_semaphore_release(lock_fd);
               bt_semaphore_destroy(lock_fd);
#endif
               return -1;
           }
       }
   }
#ifdef WIFI_BT_STATUS_SYNC
    /* query wifi status */
    property_get(WIFI_PROP_NAME, wifi_status, "");

    ALOGE("bt get wifi status: %s, isInit: %d\n",  wifi_status, isInit);

    /* If wlan driver is not loaded, and bt is changed from off => on */
    if (strncmp(wifi_status, "unloaded", strlen("unloaded")) == 0 || strlen(wifi_status) == 0) {
        if (on == '1') {
            ALOGI("%s: BT_VND_PWR_ON\n", __func__);
            if(property_set(SERVICE_PROP_NAME, "load_wlan") < 0) {
                ALOGE("%s Property setting failed", SERVICE_PROP_NAME);
                close(fd);
                bt_semaphore_release(lock_fd);
                bt_semaphore_destroy(lock_fd);
                return -1;
            }
        }
        else if (isInit == 0 && on == '0') {
            ALOGI("%s: BT_VND_PWR_OFF\n", __func__);
            if(property_set(SERVICE_PROP_NAME, "unbind_hsic") < 0) {
                ALOGE("%s Property setting failed", SERVICE_PROP_NAME);
                close(fd);
                bt_semaphore_release(lock_fd);
                bt_semaphore_destroy(lock_fd);
                return -1;
            }
       }
    }

    if (isInit == 0 && on == '0')
        property_set(BT_STATUS_NAME, "false");
    else if (on == '1')
        property_set(BT_STATUS_NAME, "true");

    bt_semaphore_release(lock_fd);
    bt_semaphore_destroy(lock_fd);
#endif /* WIFI_BT_STATUS_SYNC */

done:
    if (fd >= 0)
        close(fd);
    return 0;
}

static inline void soc_init(int soc_type)
{
    switch (soc_type)
    {
    case BT_SOC_CHEROKEE:
    case BT_SOC_ROME:
    case BT_SOC_AR3K:
        ALOGI("bt-vendor : Initializing UART transport layer");
        userial_vendor_init();
        break;
    case BT_SOC_DEFAULT:
        break;
    default:
        ALOGE("Unknown soc yype: %d", soc_type);
        break;
    }
}

/* Copy BD Address as little-endian byte order */
static inline void le2bd(unsigned char *src, unsigned char *dst)
{
    int i;
    for (i = 0; i < 6; i++)
        dst[i] = src[5-i];
}

static inline void print_bdaddr(unsigned char *addr)
{
    ALOGI("BD Address: %.2x:%.2x:%.2x:%.2x:%.2x:%.2x", addr[0], addr[1],
            addr[2], addr[3], addr[4], addr[5]);
}

/*****************************************************************************
**
**   BLUETOOTH VENDOR INTERFACE LIBRARY FUNCTIONS
**
*****************************************************************************/

static int init(const bt_vendor_callbacks_t *cb, unsigned char *bdaddr)
{
    char prop[PROPERTY_VALUE_MAX] = {0};
    struct bt_qcom_struct *temp = NULL;
    int ret = BT_STATUS_SUCCESS, i;

    ALOGI("++%s", __FUNCTION__);

    if (!cb || !bdaddr) {
        ALOGE("Invalid input args cb %p bdaddr %p", cb, bdaddr);
        ret = -BT_STATUS_INVAL;
        goto out;
    }

    temp = (struct bt_qcom_struct *) malloc(sizeof(*q));
    if (!temp) {
        ALOGE("Failed to allocate memory. err %s(%d)", strerror(errno), errno);
        ret = -BT_STATUS_NOMEM;
        goto out;
    }
    memset(temp, 0, sizeof(*temp));

    temp->rfkill_id = -1;
    temp->enable_extldo = FALSE;
    temp->cb = cb;
    temp->ant_fd = -1;
    temp->soc_type = get_bt_soc_type();
    soc_init(temp->soc_type);

    le2bd(bdaddr, temp->bdaddr);
    print_bdaddr(temp->bdaddr);
    snprintf(prop, sizeof(prop), "%02x:%02x:%02x:%02x:%02x:%02x",
             temp->bdaddr[0], temp->bdaddr[1], temp->bdaddr[2],
             temp->bdaddr[3], temp->bdaddr[4], temp->bdaddr[5]);
    ret = property_set("wc_transport.stack_bdaddr", prop);
    if (ret < 0) {
        ALOGE("Failed to set wc_transport.stack_bdaddr prop, ret = %d", ret);
        ret = -BT_STATUS_PROP_FAILURE;
        goto out;
    }

/* TODO: Move these fields inside bt_qcom context */
#ifdef WIFI_BT_STATUS_SYNC
    isInit = 1;
#endif /* WIFI_BT_STATUS_SYNC */

    /* Everything successful */
    q = temp;
    return ret;

out:
    if (temp)
        free(temp);
    ALOGI("--%s ret %d", __FUNCTION__, ret);
    return ret;
}

#ifdef READ_BT_ADDR_FROM_PROP
static bool validate_tok(char* bdaddr_tok) {
    int i = 0;
    bool ret;

    if (strlen(bdaddr_tok) != 2) {
        ret = FALSE;
        ALOGE("Invalid token length");
    } else {
        ret = TRUE;
        for (i=0; i<2; i++) {
            if ((bdaddr_tok[i] >= '0' && bdaddr_tok[i] <= '9') ||
                (bdaddr_tok[i] >= 'A' && bdaddr_tok[i] <= 'F') ||
                (bdaddr_tok[i] >= 'a' && bdaddr_tok[i] <= 'f')) {
                ret = TRUE;
                ALOGV("%s: tok %s @ %d is good", __func__, bdaddr_tok, i);
             } else {
                ret = FALSE;
                ALOGE("invalid character in tok: %s at ind: %d", bdaddr_tok, i);
                break;
             }
        }
    }
    return ret;
}
#endif /*READ_BT_ADDR_FROM_PROP*/

int connect_to_local_socket(char* name) {
       socklen_t len; int sk = -1;

       ALOGE("%s: ACCEPT ", __func__);
       sk  = socket(AF_LOCAL, SOCK_STREAM, 0);
       if (sk < 0) {
           ALOGE("Socket creation failure");
           return -1;
       }

        if(socket_local_client_connect(sk, name,
            ANDROID_SOCKET_NAMESPACE_ABSTRACT, SOCK_STREAM) < 0)
        {
             ALOGE("failed to connect (%s)", strerror(errno));
             close(sk);
             sk = -1;
        } else {
                ALOGE("%s: Connection succeeded\n", __func__);
        }
        return sk;
}

bool is_soc_initialized() {
    bool init = false;
    char init_value[PROPERTY_VALUE_MAX];
    int ret;

    ALOGI("bt-vendor : is_soc_initialized");

    ret = property_get(SOC_INIT_PROPERTY, init_value, NULL);
    if (ret != 0) {
        ALOGI("%s set to %s\n", SOC_INIT_PROPERTY, init_value);
        if (!strncasecmp(init_value, "1", sizeof("1"))) {
            init = true;
        }
    }
    else {
        ALOGE("%s: Failed to get %s", __FUNCTION__, SOC_INIT_PROPERTY);
    }

    return init;
}

/* flavor of op without locks */
static int __op(bt_vendor_opcode_t opcode, void *param)
{
    int retval = BT_STATUS_SUCCESS;
    int nCnt = 0;
    int nState = -1;
    bool is_ant_req = false;
    bool is_fm_req = false;
    char wipower_status[PROPERTY_VALUE_MAX];
    char emb_wp_mode[PROPERTY_VALUE_MAX];
    char bt_version[PROPERTY_VALUE_MAX];
    char lpm_config[PROPERTY_VALUE_MAX];
    bool ignore_boot_prop = TRUE;
#ifdef READ_BT_ADDR_FROM_PROP
    int i = 0;
    static char bd_addr[PROPERTY_VALUE_MAX];
    uint8_t local_bd_addr_from_prop[6];
    char* tok;
#endif
    bool skip_init = true;
    int  opcode_init = opcode;
    ALOGV("++%s opcode %d", __FUNCTION__, opcode);

    switch(opcode_init)
    {
#ifdef FM_OVER_UART
        case FM_VND_OP_POWER_CTRL:
            {
              is_fm_req = true;
              if (is_soc_initialized()) {
                  // add any FM specific actions  if needed in future
                  break;
              }
            }
#endif
        case BT_VND_OP_POWER_CTRL:
            {
                if (!param) {
                    ALOGE("opcode = %d: param is null", opcode_init);
                    break;
                }
                nState = *(int *) param;
                ALOGI("bt-vendor : BT_VND_OP_POWER_CTRL: %s",
                        (nState == BT_VND_PWR_ON)? "On" : "Off" );

                switch(q->soc_type)
                {
                    case BT_SOC_DEFAULT:
                        if (readTrpState())
                        {
                           ALOGI("bt-vendor : resetting BT status");
                           hw_config(BT_VND_PWR_OFF);
                        }
                        retval = hw_config(nState);
                        if(nState == BT_VND_PWR_ON
                           && retval == 0
                           && is_hw_ready() == TRUE){
                            retval = 0;
                        }
                        else {
                            retval = -1;
                        }
                        break;
                    case BT_SOC_ROME:
                    case BT_SOC_AR3K:
                    case BT_SOC_CHEROKEE:
                        if (q->soc_type == BT_SOC_ROME)
                        {
                            if (nState == BT_VND_PWR_ON)
                            {
                                /* Always power BT off before power on. */
                                ALOGI("bt-vendor: always power off before power on");
                                bt_powerup(BT_VND_PWR_OFF);
                            }
                        }

                        /* BT Chipset Power Control through Device Tree Node */
                        retval = bt_powerup(nState);
                    default:
                        break;
                }
            }
            break;

        case BT_VND_OP_FW_CFG: {
                /* call hciattach to initalize the stack */
                if (q->soc_type == BT_SOC_ROME) {
                    if (is_soc_initialized()) {
                        ALOGI("Bluetooth FW and transport layer are initialized");
                        q->cb->fwcfg_cb(BT_VND_OP_RESULT_SUCCESS);
                    } else {
                        ALOGE("bt_vendor_cbacks is null or SoC not initialized");
                        ALOGE("Error : hci, smd initialization Error");
                        retval = -1;
                    }
                } else {
                    ALOGI("Bluetooth FW and transport layer are initialized");
                    q->cb->fwcfg_cb(BT_VND_OP_RESULT_SUCCESS);
                }
        }
            break;

        case BT_VND_OP_SCO_CFG:
            q->cb->scocfg_cb(BT_VND_OP_RESULT_SUCCESS); //dummy
            break;
#ifdef ENABLE_ANT
        case BT_VND_OP_ANT_USERIAL_OPEN:
                ALOGI("bt-vendor : BT_VND_OP_ANT_USERIAL_OPEN");
                is_ant_req = true;
                goto userial_open;
#endif
#ifdef FM_OVER_UART
        case BT_VND_OP_FM_USERIAL_OPEN:
                ALOGI("bt-vendor : BT_VND_OP_FM_USERIAL_OPEN");
                is_fm_req = true;
                goto userial_open;
#endif
userial_open:
        case BT_VND_OP_USERIAL_OPEN:
            {
                if (!param) {
                    ALOGE("opcode = %d: param is null", opcode_init);
                    break;
                }
                int (*fd_array)[] = (int (*)[]) param;
                int idx, fd = -1, fd_filter = -1;
                ALOGI("bt-vendor : BT_VND_OP_USERIAL_OPEN");
                switch(q->soc_type)
                {
                    case BT_SOC_DEFAULT:
                        {
                            if(bt_hci_init_transport(q->fd) != -1){
                                int (*fd_array)[] = (int (*) []) param;

                                    (*fd_array)[CH_CMD] = q->fd[0];
                                    (*fd_array)[CH_EVT] = q->fd[0];
                                    (*fd_array)[CH_ACL_OUT] = q->fd[1];
                                    (*fd_array)[CH_ACL_IN] = q->fd[1];
                            }
                            else {
                                retval = -1;
                                break;
                            }
                            retval = 2;
                        }
                        break;
                    case BT_SOC_AR3K:
                        {
                            fd = userial_vendor_open((tUSERIAL_CFG *) &userial_init_cfg);
                            if (fd != -1) {
                                for (idx=0; idx < CH_MAX; idx++)
                                    (*fd_array)[idx] = fd;
                                     retval = 1;
                            }
                            else {
                                retval = -1;
                                break;
                            }

                            /* Vendor Specific Process should happened during userial_open process
                                After userial_open, rx read thread is running immediately,
                                so it will affect VS event read process.
                            */
                            if(ath3k_init(fd,3000000,115200,NULL,&vnd_userial.termios)<0)
                                retval = -1;
                        }
                        break;
                    case BT_SOC_ROME:
                        {
                            wait_for_patch_download(is_ant_req);
                            property_get("ro.bluetooth.emb_wp_mode", emb_wp_mode, false);
                            if (!is_soc_initialized()) {
                                char* dlnd_inprog = is_ant_req ? "ant" : "bt";
                                if (property_set("wc_transport.patch_dnld_inprog", dlnd_inprog) < 0) {
                                    ALOGE("%s: Failed to set dnld_inprog %s", __FUNCTION__, dlnd_inprog);
                                }

                                fd = userial_vendor_open((tUSERIAL_CFG *) &userial_init_cfg);
                                if (fd < 0) {
                                    ALOGE("userial_vendor_open returns err");
                                    retval = -1;
                                    break;
                                }

                                /* Clock on */
                                userial_clock_operation(fd, USERIAL_OP_CLK_ON);

                                if(strcmp(emb_wp_mode, "true") == 0) {
                                    property_get("ro.bluetooth.wipower", wipower_status, false);
                                    if(strcmp(wipower_status, "true") == 0) {
                                        check_embedded_mode(fd);
                                    } else {
                                        ALOGI("Wipower not enabled");
                                    }
                                }
                                ALOGV("rome_soc_init is started");
                                property_set("wc_transport.soc_initialized", "0");
#ifdef READ_BT_ADDR_FROM_PROP
                                /*Give priority to read BD address from boot property*/
                                ignore_boot_prop = FALSE;
                                if (property_get(BLUETOOTH_MAC_ADDR_BOOT_PROPERTY, bd_addr, NULL)) {
                                    ALOGV("BD address read from Boot property: %s\n", bd_addr);
                                    tok =  strtok(bd_addr, ":");
                                    while (tok != NULL) {
                                        ALOGV("bd add [%d]: %d ", i, strtol(tok, NULL, 16));
                                        if (i>=6) {
                                            ALOGE("bd property of invalid length");
                                            ignore_boot_prop = TRUE;
                                            break;
                                        }
                                        if (i == 6 && !ignore_boot_prop) {
                                            ALOGV("Valid BD address read from prop");
                                            memcpy(q->bdaddr, local_bd_addr_from_prop, sizeof(vnd_local_bd_addr));
                                            ignore_boot_prop = FALSE;
                                        } else {
                                            ALOGE("There are not enough tokens in BD addr");
                                            ignore_boot_prop = TRUE;
                                            break;
                                        }
                                        local_bd_addr_from_prop[5-i] = strtol(tok, NULL, 16);
                                        tok = strtok(NULL, ":");
                                        i++;
                                    }
                                    if (i == 6 && !ignore_boot_prop) {
                                        ALOGV("Valid BD address read from prop");
                                        memcpy(vnd_local_bd_addr, local_bd_addr_from_prop, sizeof(vnd_local_bd_addr));
                                        ignore_boot_prop = FALSE;
                                    } else {
                                        ALOGE("There are not enough tokens in BD addr");
                                        ignore_boot_prop = TRUE;
                                    }
                                }
                                else {
                                     ALOGE("BD address boot property not set");
                                     ignore_boot_prop = TRUE;
                                }
#endif //READ_BT_ADDR_FROM_PROP
                                    /* Always read BD address from NV file */
                                if(ignore_boot_prop && !bt_vendor_nv_read(1, q->bdaddr))
                                {
                                   /* Since the BD address is configured in boot time We should not be here */
                                   ALOGI("Failed to read BD address. Use the one from bluedroid stack/ftm");
                                }
                                if(rome_soc_init(fd, (char*)q->bdaddr)<0) {
                                    retval = -1;
                                } else {
                                    ALOGV("rome_soc_init is completed");
                                    property_set("wc_transport.soc_initialized", "1");
                                    skip_init = false;
                                }
                            }
                            if (property_set("wc_transport.patch_dnld_inprog", "null") < 0) {
                                ALOGE("%s: Failed to set property", __FUNCTION__);
                            }

                            property_set("wc_transport.clean_up","0");
                            if (retval != -1) {

                                retval = start_hci_filter();
                                if (retval < 0) {
                                    ALOGE("%s: WCNSS_FILTER wouldn't have started in time\n", __func__);
                                } else {
#ifdef ENABLE_ANT
                                    if (is_ant_req) {
                                        ALOGI("%s: connect to ant channel", __func__);
                                        q->ant_fd = fd_filter = connect_to_local_socket("ant_sock");
                                    }
                                    else
#endif
                                    {
                                        ALOGI("%s: connect to bt channel", __func__);
                                        vnd_userial.fd = fd_filter = connect_to_local_socket("bt_sock");
                                    }

                                    if (fd_filter != -1) {
                                        ALOGI("%s: received the socket fd: %d is_ant_req: %d is_fm_req: %d\n",
                                                             __func__, fd_filter, is_ant_req,is_fm_req);
                                        if((strcmp(emb_wp_mode, "true") == 0) && !is_ant_req && !is_fm_req) {
                                             if (chipset_ver >= ROME_VER_3_0) {
                                                /* get rome supported feature request */
                                                ALOGE("%s: %x08 %0x", __FUNCTION__,chipset_ver, ROME_VER_3_0);
                                                rome_get_addon_feature_list(fd_filter);
                                            }
                                        }
                                        if (!skip_init) {
                                            /*Skip if already sent*/
                                            enable_controller_log(fd_filter, (is_ant_req || is_fm_req) );
                                            skip_init = true;
                                        }
                                        for (idx=0; idx < CH_MAX; idx++)
                                            (*fd_array)[idx] = fd_filter;
                                            retval = 1;
                                    }
                                    else {
                                        if (is_ant_req)
                                            ALOGE("Unable to connect to ANT Server Socket!!!");
                                        else
                                            ALOGE("Unable to connect to BT Server Socket!!!");
                                        retval = -1;
                                    }
                                }
                            } else {
                                if (q->soc_type == BT_SOC_ROME)
                                    ALOGE("Failed to initialize ROME Controller!!!");
                            }

                            if (fd >= 0) {
                                userial_clock_operation(fd, USERIAL_OP_CLK_OFF);
                                 /*Close the UART port*/
                                 close(fd);
                            }
                        }
                        break;
                    case BT_SOC_CHEROKEE:
                        {
                            property_get("ro.bluetooth.emb_wp_mode", emb_wp_mode, false);
                            retval = start_hci_filter();
                            if (retval < 0) {
                                ALOGE("WCNSS_FILTER wouldn't have started in time\n");

                            } else {
#ifdef ENABLE_ANT
                                if (is_ant_req) {
                                    ALOGI("%s: connect to ant channel", __func__);
                                    q->ant_fd = fd_filter = connect_to_local_socket("ant_sock");
                                }
                                else
#endif
#ifdef FM_OVER_UART
                                if (is_fm_req && (q->soc_type >=BT_SOC_ROME && q->soc_type < BT_SOC_RESERVED)) {
                                    ALOGI("%s: connect to fm channel", __func__);
                                    q->fm_fd = fd_filter = connect_to_local_socket("fm_sock");
                                }
                                else
#endif
                                {
                                    ALOGI("%s: connect to bt channel", __func__);
                                    vnd_userial.fd = fd_filter = connect_to_local_socket("bt_sock");

                                }
                                if (fd_filter != -1) {
                                    ALOGV("%s: received the socket fd: %d \n",
                                                             __func__, fd_filter);

                                    for (idx=0; idx < CH_MAX; idx++) {
                                        (*fd_array)[idx] = fd_filter;
                                    }
                                    retval = 1;
                                }
                                else {
#ifdef ENABLE_ANT
                                    if (is_ant_req)
                                        ALOGE("Unable to connect to ANT Server Socket!!!");
                                    else
#endif
#ifdef FM_OVER_UART
                                    if (is_fm_req)
                                        ALOGE("Unable to connect to FM Server Socket!!!");
                                    else
#endif
                                        ALOGE("Unable to connect to BT Server Socket!!!");
                                    retval = -1;
                                }
                            }
                        }
                        break;
                    default:
                        ALOGE("Unknown soc_type: 0x%x", q->soc_type);
                        break;
                  }
            } break;
#ifdef ENABLE_ANT
        case BT_VND_OP_ANT_USERIAL_CLOSE:
            {
                ALOGI("bt-vendor : BT_VND_OP_ANT_USERIAL_CLOSE");
                property_set("wc_transport.clean_up","1");
                if (q->ant_fd != -1) {
                    ALOGE("closing ant_fd");
                    close(q->ant_fd);
                    q->ant_fd = -1;
                }
            }
            break;
#endif
#ifdef FM_OVER_UART
        case BT_VND_OP_FM_USERIAL_CLOSE:
            {
                ALOGI("bt-vendor : BT_VND_OP_FM_USERIAL_CLOSE");
                property_set("wc_transport.clean_up","1");
                if (q->fm_fd != -1) {
                    ALOGE("closing fm_fd");
                    close(q->fm_fd);
                    q->fm_fd = -1;
                }
                break;
            }
#endif
        case BT_VND_OP_USERIAL_CLOSE:
            {
                ALOGI("bt-vendor : BT_VND_OP_USERIAL_CLOSE soc_type: %d", q->soc_type);
                switch(q->soc_type)
                {
                    case BT_SOC_DEFAULT:
                        bt_hci_deinit_transport(q->fd);
                        break;
                    case BT_SOC_ROME:
                    case BT_SOC_AR3K:
                    case BT_SOC_CHEROKEE:
                    {
                        property_set("wc_transport.clean_up","1");
                        userial_vendor_close();
                        break;
                    }
                    default:
                        ALOGE("Unknown soc_type: 0x%x", q->soc_type);
                        break;
                }
            }
            break;

        case BT_VND_OP_GET_LPM_IDLE_TIMEOUT:
            {
                if (!param) {
                    ALOGE("opcode = %d: param is null", opcode_init);
                    break;
                }
                uint32_t *timeout_ms = (uint32_t *) param;
                *timeout_ms = 1000;
            }

            break;

        case BT_VND_OP_LPM_SET_MODE:
            if (q->soc_type == BT_SOC_AR3K) {
                if (!param) {
                    ALOGE("opcode = %d: param is null", opcode_init);
                    break;
                }
                uint8_t *mode = (uint8_t *) param;

                if (*mode) {
                    lpm_set_ar3k(UPIO_LPM_MODE, UPIO_ASSERT, 0);
                }
                else {
                    lpm_set_ar3k(UPIO_LPM_MODE, UPIO_DEASSERT, 0);
                }
                q->cb->lpm_cb(BT_VND_OP_RESULT_SUCCESS);
            } else {
                int lpm_result = BT_VND_OP_RESULT_SUCCESS;

                property_get("persist.service.bdroid.lpmcfg", lpm_config, "all");
                ALOGI("%s: property_get: persist.service.bdroid.lpmcfg: %s",
                            __func__, lpm_config);

                if (!strcmp(lpm_config, "all")) {
                    // respond with success since we want to hold wake lock through LPM
                    lpm_result = BT_VND_OP_RESULT_SUCCESS;
                }
                else {
                    lpm_result = BT_VND_OP_RESULT_FAIL;
                }

                q->cb->lpm_cb(lpm_result);
            }
            break;

        case BT_VND_OP_LPM_WAKE_SET_STATE: {
            switch(q->soc_type) {
            case BT_SOC_CHEROKEE:
            case BT_SOC_ROME: {
                if (!param) {
                    ALOGE("opcode = %d: param is null", opcode_init);
                    break;
                }
                uint8_t *state = (uint8_t *) param;
                uint8_t wake_assert = (*state == BT_VND_LPM_WAKE_ASSERT) ? \
                            BT_VND_LPM_WAKE_ASSERT : BT_VND_LPM_WAKE_DEASSERT;

                if (wake_assert == 0)
                    ALOGV("ASSERT: Waking up BT-Device");
                else if (wake_assert == 1)
                    ALOGV("DEASSERT: Allowing BT-Device to Sleep");

#ifdef QCOM_BT_SIBS_ENABLE
                ALOGI("Invoking HCI H4 callback function");
                q->cb->lpm_set_state_cb(wake_assert);
#endif
            }
            break;
            case BT_SOC_AR3K: {
                if (!param) {
                    ALOGE("opcode = %d: param is null", opcode_init);
                    break;
                }
                uint8_t *state = (uint8_t *) param;
                uint8_t wake_assert = (*state == BT_VND_LPM_WAKE_ASSERT) ? \
                                                UPIO_ASSERT : UPIO_DEASSERT;
                lpm_set_ar3k(UPIO_BT_WAKE, wake_assert, 0);
            }
            case BT_SOC_DEFAULT:
                break;
            default:
                ALOGE("Unknown soc_type: 0x%x", q->soc_type);
                break;
            }
        }
            break;
        case BT_VND_OP_EPILOG: {
#if (HW_NEED_END_WITH_HCI_RESET == FALSE)
            q->cb->epilog_cb(BT_VND_OP_RESULT_SUCCESS);
#else
                switch(q->soc_type)
                {
                  case BT_SOC_CHEROKEE:
                  case BT_SOC_ROME:
                       {
                           char value[PROPERTY_VALUE_MAX] = {'\0'};
                           property_get("wc_transport.hci_filter_status", value, "0");
                           if(is_soc_initialized()&& (strcmp(value,"1") == 0))
                           {
                              __hw_epilog_process();
                           }
                           else
                           {
                                q->cb->epilog_cb(BT_VND_OP_RESULT_SUCCESS);
                           }
                       }
                       break;
                  default:
                       __hw_epilog_process();
                       break;
                }
#endif
            }
            break;
        case BT_VND_OP_GET_LINESPEED:
            {
                retval = -1;
                if(!is_soc_initialized()) {
                     ALOGE("BT_VND_OP_GET_LINESPEED: error"
                         " - transport driver not initialized!");
                     break;
                }

                switch(q->soc_type)
                {
                    case BT_SOC_CHEROKEE:
                            retval = 3200000;
                        break;
                    case BT_SOC_ROME:
                            retval = 3000000;
                        break;
                    default:
                        retval = userial_vendor_get_baud();
                        break;
                 }
                break;
            }
    }

out:
    ALOGV("--%s", __FUNCTION__);
    return retval;
}

static int op(bt_vendor_opcode_t opcode, void *param)
{
    int ret;
    ALOGV("++%s", __FUNCTION__);
#ifdef BT_THREADLOCK_SAFE
    pthread_mutex_lock(&q_lock);
#endif
    if (!q) {
        ALOGE("op called with NULL context");
        ret = -BT_STATUS_INVAL;
        goto out;
    }
    ret = __op(opcode, param);
out:
#ifdef BT_THREADLOCK_SAFE
    pthread_mutex_unlock(&q_lock);
#endif
    ALOGV("--%s ret = 0x%x", __FUNCTION__, ret);
    return ret;
}

static void ssr_cleanup(int reason)
{
    int pwr_state = BT_VND_PWR_OFF;
    int ret;
    unsigned char trig_ssr = 0xEE;

    ALOGI("++%s", __FUNCTION__);

    pthread_mutex_lock(&q_lock);
    if (!q) {
        ALOGE("ssr_cleanup called with NULL context");
        goto out;
    }
    if (property_set("wc_transport.patch_dnld_inprog", "null") < 0) {
        ALOGE("Failed to set property");
    }

    if (q->soc_type >= BT_SOC_ROME && q->soc_type < BT_SOC_RESERVED) {
#ifdef ENABLE_ANT
        /*Indicate to filter by sending special byte */
        if (reason == CMD_TIMEOUT) {
            trig_ssr = 0xEE;
            ret = write (vnd_userial.fd, &trig_ssr, 1);
            ALOGI("Trig_ssr is being sent to BT socket, ret %d err %s",
                        ret, strerror(errno));

            if (is_debug_force_special_bytes()) {
                /*
                 * Then we should send special byte to crash SOC in
                 * WCNSS_Filter, so we do not need to power off UART here.
                 */
                goto out;
            }
        }

        /* Close both ANT channel */
        __op(BT_VND_OP_ANT_USERIAL_CLOSE, NULL);
#endif
        /* Close both BT channel */
        __op(BT_VND_OP_USERIAL_CLOSE, NULL);

#ifdef FM_OVER_UART
        __op(BT_VND_OP_FM_USERIAL_CLOSE, NULL);
#endif
        /*CTRL OFF twice to make sure hw
         * turns off*/
#ifdef ENABLE_ANT
        __op(BT_VND_OP_POWER_CTRL, &pwr_state);
#endif
    }
    /*Generally switching of chip should be enough*/
    __op(BT_VND_OP_POWER_CTRL, &pwr_state);

out:
    pthread_mutex_unlock(&q_lock);
    ALOGI("--%s", __FUNCTION__);
}

/** Closes the interface */
static void cleanup(void)
{
    ALOGI("cleanup");

    pthread_mutex_lock(&q_lock);
    q->cb = NULL;
    free(q);
    q = NULL;
    pthread_mutex_unlock(&q_lock);

#ifdef WIFI_BT_STATUS_SYNC
    isInit = 0;
#endif /* WIFI_BT_STATUS_SYNC */
}

/* Check for one of the cients ANT/BT patch download is already in
** progress if yes wait till complete
*/
void wait_for_patch_download(bool is_ant_req) {
    ALOGV("%s:", __FUNCTION__);
    char inProgress[PROPERTY_VALUE_MAX] = {'\0'};
    while (1) {
        property_get("wc_transport.patch_dnld_inprog", inProgress, "null");

        if(is_ant_req && !(strcmp(inProgress,"bt"))) {
           //ANT request, wait for BT to finish
           usleep(50000);
        }
        else if(!is_ant_req && !(strcmp(inProgress,"ant"))) {
          //BT request, wait for ANT to finish
           usleep(50000);
        }
        else {
           ALOGI("%s: patch download completed", __FUNCTION__);
           break;
        }
    }
}

bool is_download_progress () {
    char inProgress[PROPERTY_VALUE_MAX] = {'\0'};
    bool retval = false;

    ALOGV("%s:", __FUNCTION__);

    if ((q->soc_type = get_bt_soc_type()) < 0) {
        ALOGE("%s: Failed to detect BT SOC Type", __FUNCTION__);
        return -1;
    }

    switch(q->soc_type)
    {
        case BT_SOC_ROME:
            ALOGI("%s: ROME case", __func__);
            property_get("wc_transport.patch_dnld_inprog", inProgress, "null");
            if(strcmp(inProgress,"null") == 0) {
                retval = false;
            } else {
                 retval = true;
            }
            break;
        case BT_SOC_CHEROKEE:
            ALOGI("%s: CHEROKEE case", __func__);
            break;
        case BT_SOC_DEFAULT:
            break;
        default:
            ALOGE("Unknown btSocType: 0x%x", q->soc_type);
            break;
    }
    return retval;
}

static bool is_debug_force_special_bytes() {
    int ret = 0;
    char value[PROPERTY_VALUE_MAX] = {'\0'};
    bool enabled = false;
#ifdef ENABLE_DBG_FLAGS
    enabled = true;
#endif

    ret = property_get("wc_transport.force_special_byte", value, NULL);

    if (ret) {
        enabled = (strcmp(value, "false") ==0) ? false : true;
        ALOGV("%s: wc_transport.force_special_byte: %s, enabled: %d ",
            __func__, value, enabled);
    }

    return enabled;
}

// Entry point of DLib
/* Remove 'ssr_cleanup' because it's not defined in 'bt_vendor_interface_t'. */
const bt_vendor_interface_t BLUETOOTH_VENDOR_LIB_INTERFACE = {
    sizeof(bt_vendor_interface_t),
    init,
    op,
    cleanup
};
