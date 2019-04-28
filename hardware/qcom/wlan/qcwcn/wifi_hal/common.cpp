/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <stdlib.h>
#include <linux/pkt_sched.h>
#include <netlink/object-api.h>
#include <netlink-private/object-api.h>
#include <netlink-private/types.h>
#include <dlfcn.h>
#include <pthread.h>

#include "wifi_hal.h"
#include "common.h"
#include <errno.h>

interface_info *getIfaceInfo(wifi_interface_handle handle)
{
    return (interface_info *)handle;
}

wifi_handle getWifiHandle(wifi_interface_handle handle)
{
    return getIfaceInfo(handle)->handle;
}

hal_info *getHalInfo(wifi_handle handle)
{
    return (hal_info *)handle;
}

hal_info *getHalInfo(wifi_interface_handle handle)
{
    return getHalInfo(getWifiHandle(handle));
}

wifi_handle getWifiHandle(hal_info *info)
{
    return (wifi_handle)info;
}

wifi_interface_handle getIfaceHandle(interface_info *info)
{
    return (wifi_interface_handle)info;
}

wifi_error wifi_register_handler(wifi_handle handle, int cmd, nl_recvmsg_msg_cb_t func, void *arg)
{
    hal_info *info = (hal_info *)handle;

    pthread_mutex_lock(&info->cb_lock);

    wifi_error result = WIFI_ERROR_OUT_OF_MEMORY;

    for (int i = 0; i < info->num_event_cb; i++) {
        if(info->event_cb[i].nl_cmd == cmd &&
           info->event_cb[i].cb_arg == arg) {
            info->event_cb[i].cb_func = func;
            ALOGV("Updated event handler %p for nl_cmd 0x%0x"
                    " and arg %p", func, cmd, arg);
            pthread_mutex_unlock(&info->cb_lock);
            return WIFI_SUCCESS;
        }
    }

    if (info->num_event_cb < info->alloc_event_cb) {
        info->event_cb[info->num_event_cb].nl_cmd  = cmd;
        info->event_cb[info->num_event_cb].vendor_id  = 0;
        info->event_cb[info->num_event_cb].vendor_subcmd  = 0;
        info->event_cb[info->num_event_cb].cb_func = func;
        info->event_cb[info->num_event_cb].cb_arg  = arg;
        info->num_event_cb++;
        ALOGV("Successfully added event handler %p for command %d", func, cmd);
        result = WIFI_SUCCESS;
    } else {
        result = WIFI_ERROR_OUT_OF_MEMORY;
    }

    pthread_mutex_unlock(&info->cb_lock);
    return result;
}

wifi_error wifi_register_vendor_handler(wifi_handle handle,
        uint32_t id, int subcmd, nl_recvmsg_msg_cb_t func, void *arg)
{
    hal_info *info = (hal_info *)handle;

    pthread_mutex_lock(&info->cb_lock);

    wifi_error result = WIFI_ERROR_OUT_OF_MEMORY;

    for (int i = 0; i < info->num_event_cb; i++) {
        if(info->event_cb[i].vendor_id  == id &&
           info->event_cb[i].vendor_subcmd == subcmd)
        {
            info->event_cb[i].cb_func = func;
            info->event_cb[i].cb_arg  = arg;
            ALOGV("Updated event handler %p for vendor 0x%0x, subcmd 0x%0x"
                " and arg %p", func, id, subcmd, arg);
            pthread_mutex_unlock(&info->cb_lock);
            return WIFI_SUCCESS;
        }
    }

    if (info->num_event_cb < info->alloc_event_cb) {
        info->event_cb[info->num_event_cb].nl_cmd  = NL80211_CMD_VENDOR;
        info->event_cb[info->num_event_cb].vendor_id  = id;
        info->event_cb[info->num_event_cb].vendor_subcmd  = subcmd;
        info->event_cb[info->num_event_cb].cb_func = func;
        info->event_cb[info->num_event_cb].cb_arg  = arg;
        info->num_event_cb++;
        ALOGV("Added event handler %p for vendor 0x%0x, subcmd 0x%0x and arg"
            " %p", func, id, subcmd, arg);
        result = WIFI_SUCCESS;
    } else {
        result = WIFI_ERROR_OUT_OF_MEMORY;
    }

    pthread_mutex_unlock(&info->cb_lock);
    return result;
}

void wifi_unregister_handler(wifi_handle handle, int cmd)
{
    hal_info *info = (hal_info *)handle;

    if (cmd == NL80211_CMD_VENDOR) {
        ALOGE("Must use wifi_unregister_vendor_handler to remove vendor handlers");
        return;
    }

    pthread_mutex_lock(&info->cb_lock);

    for (int i = 0; i < info->num_event_cb; i++) {
        if (info->event_cb[i].nl_cmd == cmd) {
            if(i < info->num_event_cb-1) {
                /* No need to memmove if only one entry exist and deleting
                 * the same, as the num_event_cb will become 0 in this case.
                 */
                memmove(&info->event_cb[i], &info->event_cb[i+1],
                        (info->num_event_cb - i) * sizeof(cb_info));
            }
            info->num_event_cb--;
            ALOGV("Successfully removed event handler for command %d", cmd);
            break;
        }
    }

    pthread_mutex_unlock(&info->cb_lock);
}

void wifi_unregister_vendor_handler(wifi_handle handle, uint32_t id, int subcmd)
{
    hal_info *info = (hal_info *)handle;

    pthread_mutex_lock(&info->cb_lock);

    for (int i = 0; i < info->num_event_cb; i++) {

        if (info->event_cb[i].nl_cmd == NL80211_CMD_VENDOR
                && info->event_cb[i].vendor_id == id
                && info->event_cb[i].vendor_subcmd == subcmd) {
            if(i < info->num_event_cb-1) {
                /* No need to memmove if only one entry exist and deleting
                 * the same, as the num_event_cb will become 0 in this case.
                 */
                memmove(&info->event_cb[i], &info->event_cb[i+1],
                        (info->num_event_cb - i) * sizeof(cb_info));
            }
            info->num_event_cb--;
            ALOGV("Successfully removed event handler for vendor 0x%0x", id);
            break;
        }
    }

    pthread_mutex_unlock(&info->cb_lock);
}


wifi_error wifi_register_cmd(wifi_handle handle, int id, WifiCommand *cmd)
{
    hal_info *info = (hal_info *)handle;

    if (info->num_cmd < info->alloc_cmd) {
        info->cmd[info->num_cmd].id   = id;
        info->cmd[info->num_cmd].cmd  = cmd;
        info->num_cmd++;
        ALOGV("Successfully added command %d: %p", id, cmd);
        return WIFI_SUCCESS;
    } else {
        return WIFI_ERROR_OUT_OF_MEMORY;
    }
}

WifiCommand *wifi_unregister_cmd(wifi_handle handle, int id)
{
    hal_info *info = (hal_info *)handle;

    for (int i = 0; i < info->num_cmd; i++) {
        if (info->cmd[i].id == id) {
            WifiCommand *cmd = info->cmd[i].cmd;
            memmove(&info->cmd[i], &info->cmd[i+1], (info->num_cmd - i) * sizeof(cmd_info));
            info->num_cmd--;
            ALOGV("Successfully removed command %d: %p", id, cmd);
            return cmd;
        }
    }

    return NULL;
}

void wifi_unregister_cmd(wifi_handle handle, WifiCommand *cmd)
{
    hal_info *info = (hal_info *)handle;

    for (int i = 0; i < info->num_cmd; i++) {
        if (info->cmd[i].cmd == cmd) {
            int id = info->cmd[i].id;
            memmove(&info->cmd[i], &info->cmd[i+1], (info->num_cmd - i) * sizeof(cmd_info));
            info->num_cmd--;
            ALOGV("Successfully removed command %d: %p", id, cmd);
            return;
        }
    }
}

#ifdef __cplusplus
extern "C"
{
#endif /* __cplusplus */

void hexdump(void *buf, u16 len)
{
    int i=0;
    char *bytes = (char *)buf;

    if (len) {
        ALOGV("******HexDump len:%d*********", len);
        for (i = 0; ((i + 7) < len); i+=8) {
            ALOGV("%02x %02x %02x %02x   %02x %02x %02x %02x",
                bytes[i], bytes[i+1],
                bytes[i+2], bytes[i+3],
                bytes[i+4], bytes[i+5],
                bytes[i+6], bytes[i+7]);
        }
        if ((len - i) >= 4) {
            ALOGV("%02x %02x %02x %02x",
                bytes[i], bytes[i+1],
                bytes[i+2], bytes[i+3]);
            i+=4;
        }
        for (;i < len;i++) {
            ALOGV("%02x", bytes[i]);
        }
        ALOGV("******HexDump End***********");
    } else {
        return;
    }
}

/* Firmware sends RSSI value without noise floor.
 * Add noise floor to the same and return absolute values.
 */
u8 get_rssi(u8 rssi_wo_noise_floor)
{
    return abs((int)rssi_wo_noise_floor - 96);
}

#ifdef __cplusplus
}
#endif /* __cplusplus */

/* Pointer to the table of LOWI callback funcs */
lowi_cb_table_t *LowiWifiHalApi = NULL;
/* LowiSupportedCapabilities read */
u32 lowiSupportedCapabilities = 0;

int compareLowiVersion(u16 major, u16 minor, u16 micro)
{
    u32 currVersion = 0x10000*(WIFIHAL_LOWI_MAJOR_VERSION) + \
                      0x100*(WIFIHAL_LOWI_MINOR_VERSION) + \
                      WIFIHAL_LOWI_MICRO_VERSION;

    u32 lowiVersion = 0x10000*(major) + \
                      0x100*(minor) + \
                      micro;

    return (memcmp(&currVersion, &lowiVersion, sizeof(u32)));
}

/*
 * This function will open the lowi shared library and obtain the
 * Lowi Callback table and the capabilities supported.
 * A version check is also performed in this function and if the version
 * check fails then the callback table returned will be NULL.
 */
wifi_error fetchLowiCbTableAndCapabilities(lowi_cb_table_t **lowi_wifihal_api,
                                           bool *lowi_get_capa_supported)
{
    getCbTable_t* lowiCbTable = NULL;
    int ret = 0;
    wifi_error retVal = WIFI_SUCCESS;

    *lowi_wifihal_api = NULL;
    *lowi_get_capa_supported = false;

#if __WORDSIZE == 64
    void* lowi_handle = dlopen("/vendor/lib64/liblowi_wifihal.so", RTLD_NOW);
#else
    void* lowi_handle = dlopen("/vendor/lib/liblowi_wifihal.so", RTLD_NOW);
#endif
    if (!lowi_handle) {
        ALOGE("%s: NULL lowi_handle, err: %s", __FUNCTION__, dlerror());
        return WIFI_ERROR_UNKNOWN;
    }

    lowiCbTable = (getCbTable_t*)dlsym(lowi_handle,
                                       "lowi_wifihal_get_cb_table");
    if (!lowiCbTable) {
        ALOGE("%s: NULL lowi callback table", __FUNCTION__);
        return WIFI_ERROR_UNKNOWN;
    }

    *lowi_wifihal_api = lowiCbTable();

    /* First check whether lowi module implements the get_lowi_version
     * function. All the functions in lowi module starts with
     * "lowi_wifihal_" prefix thus the below function name.
     */
    if ((dlsym(lowi_handle, "lowi_wifihal_get_lowi_version") != NULL) &&
        ((*lowi_wifihal_api)->get_lowi_version != NULL)) {
        u16 lowiMajorVersion = WIFIHAL_LOWI_MAJOR_VERSION;
        u16 lowiMinorVersion = WIFIHAL_LOWI_MINOR_VERSION;
        u16 lowiMicroVersion = WIFIHAL_LOWI_MICRO_VERSION;
        int versionCheck = -1;

        ret = (*lowi_wifihal_api)->get_lowi_version(&lowiMajorVersion,
                                                    &lowiMinorVersion,
                                                    &lowiMicroVersion);
        if (ret) {
            ALOGE("%s: get_lowi_version returned error:%d",
                  __FUNCTION__, ret);
            retVal = WIFI_ERROR_NOT_SUPPORTED;
            goto cleanup;
        }
        ALOGV("%s: Lowi version:%d.%d.%d", __FUNCTION__,
              lowiMajorVersion, lowiMinorVersion,
              lowiMicroVersion);

        /* Compare the version with version in wifihal_internal.h */
        versionCheck = compareLowiVersion(lowiMajorVersion,
                                          lowiMinorVersion,
                                          lowiMicroVersion);
        if (versionCheck < 0) {
            ALOGE("%s: Version Check failed:%d", __FUNCTION__,
                  versionCheck);
            retVal = WIFI_ERROR_NOT_SUPPORTED;
            goto cleanup;
        }
    }
    else {
        ALOGV("%s: lowi_wifihal_get_lowi_version not present",
              __FUNCTION__);
    }


    /* Check if get_lowi_capabilities func pointer exists in
     * the lowi lib and populate lowi_get_capa_supported
     * All the functions in lowi modules starts with
     * "lowi_wifihal_ prefix" thus the below function name.
     */
    if (dlsym(lowi_handle, "lowi_wifihal_get_lowi_capabilities") != NULL) {
        *lowi_get_capa_supported = true;
    }
    else {
        ALOGV("lowi_wifihal_get_lowi_capabilities() is not supported.");
        *lowi_get_capa_supported = false;
    }
cleanup:
    if (retVal) {
        *lowi_wifihal_api = NULL;
    }
    return retVal;
}

lowi_cb_table_t *getLowiCallbackTable(u32 requested_lowi_capabilities)
{
    int ret = WIFI_SUCCESS;
    bool lowi_get_capabilities_support = false;

    if (LowiWifiHalApi == NULL) {
        ALOGV("%s: LowiWifiHalApi Null, Initialize Lowi",
              __FUNCTION__);
        ret = fetchLowiCbTableAndCapabilities(&LowiWifiHalApi,
                                              &lowi_get_capabilities_support);
        if (ret != WIFI_SUCCESS || LowiWifiHalApi == NULL ||
            LowiWifiHalApi->init == NULL) {
            ALOGE("%s: LOWI is not supported.", __FUNCTION__);
            goto cleanup;
        }
        /* Initialize LOWI if it isn't up already. */
        ret = LowiWifiHalApi->init();
        if (ret) {
            ALOGE("%s: failed lowi initialization. "
                "Returned error:%d. Exit.", __FUNCTION__, ret);
            goto cleanup;
        }
        if (!lowi_get_capabilities_support ||
            LowiWifiHalApi->get_lowi_capabilities == NULL) {
                ALOGV("%s: Allow rtt APIs thru LOWI to proceed even though "
                      "get_lowi_capabilities() is not supported. Returning",
                      __FUNCTION__);
                lowiSupportedCapabilities |=
                    (ONE_SIDED_RANGING_SUPPORTED|DUAL_SIDED_RANGING_SUPPORED);
                return LowiWifiHalApi;
        }
        ret =
            LowiWifiHalApi->get_lowi_capabilities(&lowiSupportedCapabilities);
        if (ret) {
            ALOGV("%s: failed to get lowi supported capabilities."
                "Returned error:%d. Exit.", __FUNCTION__, ret);
            goto cleanup;
        }
    }

    if ((lowiSupportedCapabilities & requested_lowi_capabilities) == 0) {
        return NULL;
    }
    return LowiWifiHalApi;

cleanup:
    if (LowiWifiHalApi && LowiWifiHalApi->destroy) {
        ret = LowiWifiHalApi->destroy();
    }
    LowiWifiHalApi = NULL;
    lowiSupportedCapabilities = 0;
    return LowiWifiHalApi;
}

wifi_error mapErrorKernelToWifiHAL(int error)
{
    if (error >= 0)
        return WIFI_ERROR_NONE;

    switch (error) {
        case -EOPNOTSUPP:
            return WIFI_ERROR_NOT_SUPPORTED;
        case -EAGAIN:
            return WIFI_ERROR_NOT_AVAILABLE;
        case -EINVAL:
            return WIFI_ERROR_INVALID_ARGS;
        case -ETIMEDOUT:
            return WIFI_ERROR_TIMED_OUT;
        case -ENOMEM:
            return WIFI_ERROR_OUT_OF_MEMORY;
        case -EBUSY:
            return WIFI_ERROR_BUSY;
        default:
            return WIFI_ERROR_UNKNOWN;
    }
    return WIFI_ERROR_UNKNOWN;
}
