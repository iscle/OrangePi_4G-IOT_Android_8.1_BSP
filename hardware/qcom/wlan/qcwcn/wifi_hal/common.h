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

#include "wifi_hal.h"

#ifndef __WIFI_HAL_COMMON_H__
#define __WIFI_HAL_COMMON_H__

#ifndef LOG_TAG
#define LOG_TAG  "WifiHAL"
#endif

#include <stdint.h>
#include <fcntl.h>
#include <inttypes.h>
#include <sys/socket.h>
#include <netlink/genl/genl.h>
#include <netlink/genl/family.h>
#include <netlink/genl/ctrl.h>
#include <linux/rtnetlink.h>
#include <netpacket/packet.h>
#include <linux/filter.h>
#include <linux/errqueue.h>

#include <linux/pkt_sched.h>
#include <netlink/object-api.h>
#include <netlink/netlink.h>
#include <netlink/socket.h>

#include "nl80211_copy.h"

#include <utils/Log.h>
#include "rb_wrapper.h"
#include "pkt_stats.h"
#include "wifihal_internal.h"

#define SOCKET_BUFFER_SIZE      (32768U)
#define RECV_BUF_SIZE           (4096)
#define DEFAULT_EVENT_CB_SIZE   (64)
#define DEFAULT_CMD_SIZE        (64)
#define NUM_RING_BUFS           5

#define MAC_ADDR_ARRAY(a) (a)[0], (a)[1], (a)[2], (a)[3], (a)[4], (a)[5]
#define MAC_ADDR_STR "%02x:%02x:%02x:%02x:%02x:%02x"
#define BIT(x) (1 << (x))

typedef int16_t s16;
typedef int32_t s32;
typedef int64_t s64;

typedef void (*wifi_internal_event_handler) (wifi_handle handle, int events);

class WifiCommand;

typedef struct {
    int nl_cmd;
    uint32_t vendor_id;
    int vendor_subcmd;
    nl_recvmsg_msg_cb_t cb_func;
    void *cb_arg;
} cb_info;

typedef struct {
    wifi_request_id id;
    WifiCommand *cmd;
} cmd_info;

typedef struct {
    wifi_handle handle;                             // handle to wifi data
    char name[IFNAMSIZ+1];                          // interface name + trailing null
    int  id;                                        // id to use when talking to driver
} interface_info;

typedef struct {
    wifi_gscan_capabilities gscan_capa;
    wifi_roaming_capabilities roaming_capa;
} wifi_capa;

struct gscan_event_handlers_s;
struct rssi_monitor_event_handler_s;
struct cld80211_ctx;

typedef struct hal_info_s {

    struct nl_sock *cmd_sock;                       // command socket object
    struct nl_sock *event_sock;                     // event socket object
    struct nl_sock *user_sock;                      // user socket object
    int nl80211_family_id;                          // family id for 80211 driver

    bool in_event_loop;                             // Indicates that event loop is active
    bool clean_up;                                  // Indication to clean up the socket

    wifi_internal_event_handler event_handler;      // default event handler
    wifi_cleaned_up_handler cleaned_up_handler;     // socket cleaned up handler

    cb_info *event_cb;                              // event callbacks
    int num_event_cb;                               // number of event callbacks
    int alloc_event_cb;                             // number of allocated callback objects
    pthread_mutex_t cb_lock;                        // mutex for the event_cb access

    cmd_info *cmd;                                  // Outstanding commands
    int num_cmd;                                    // number of commands
    int alloc_cmd;                                  // number of commands allocated

    interface_info **interfaces;                    // array of interfaces
    int num_interfaces;                             // number of interfaces

    feature_set supported_feature_set;
    // add other details
    int user_sock_arg;
    struct rb_info rb_infos[NUM_RING_BUFS];
    void (*on_ring_buffer_data) (char *ring_name, char *buffer, int buffer_size,
          wifi_ring_buffer_status *status);
    void (*on_alert) (wifi_request_id id, char *buffer, int buffer_size, int err_code);
    struct pkt_stats_s *pkt_stats;

    /* socket pair used to exit from blocking poll*/
    int exit_sockets[2];
    u32 rx_buf_size_allocated;
    u32 rx_buf_size_occupied;
    wifi_ring_buffer_entry *rx_aggr_pkts;
    rx_aggr_stats aggr_stats;
    u32 prev_seq_no;
    // pointer to structure having various gscan_event_handlers
    struct gscan_event_handlers_s *gscan_handlers;
    /* mutex for the log_handler access*/
    pthread_mutex_t lh_lock;
    /* mutex for the alert_handler access*/
    pthread_mutex_t ah_lock;
    u32 firmware_bus_max_size;
    bool fate_monitoring_enabled;
    packet_fate_monitor_info *pkt_fate_stats;
    /* mutex for the packet fate stats shared resource protection */
    pthread_mutex_t pkt_fate_stats_lock;
    struct rssi_monitor_event_handler_s *rssi_handlers;
    wifi_capa capa;
    struct cld80211_ctx *cldctx;
} hal_info;

wifi_error wifi_register_handler(wifi_handle handle, int cmd, nl_recvmsg_msg_cb_t func, void *arg);
wifi_error wifi_register_vendor_handler(wifi_handle handle,
            uint32_t id, int subcmd, nl_recvmsg_msg_cb_t func, void *arg);

void wifi_unregister_handler(wifi_handle handle, int cmd);
void wifi_unregister_vendor_handler(wifi_handle handle, uint32_t id, int subcmd);

wifi_error wifi_register_cmd(wifi_handle handle, int id, WifiCommand *cmd);
WifiCommand *wifi_unregister_cmd(wifi_handle handle, int id);
void wifi_unregister_cmd(wifi_handle handle, WifiCommand *cmd);

interface_info *getIfaceInfo(wifi_interface_handle);
wifi_handle getWifiHandle(wifi_interface_handle handle);
hal_info *getHalInfo(wifi_handle handle);
hal_info *getHalInfo(wifi_interface_handle handle);
wifi_handle getWifiHandle(hal_info *info);
wifi_interface_handle getIfaceHandle(interface_info *info);
wifi_error initializeGscanHandlers(hal_info *info);
wifi_error cleanupGscanHandlers(hal_info *info);
wifi_error initializeRSSIMonitorHandler(hal_info *info);
wifi_error cleanupRSSIMonitorHandler(hal_info *info);

lowi_cb_table_t *getLowiCallbackTable(u32 requested_lowi_capabilities);

wifi_error wifi_start_sending_offloaded_packet(wifi_request_id id,
        wifi_interface_handle iface, u8 *ip_packet, u16 ip_packet_len,
        u8 *src_mac_addr, u8 *dst_mac_addr, u32 period_msec);
wifi_error wifi_stop_sending_offloaded_packet(wifi_request_id id,
        wifi_interface_handle iface);
wifi_error wifi_start_rssi_monitoring(wifi_request_id id, wifi_interface_handle
        iface, s8 max_rssi, s8 min_rssi, wifi_rssi_event_handler eh);
wifi_error wifi_stop_rssi_monitoring(wifi_request_id id, wifi_interface_handle iface);
wifi_error mapErrorKernelToWifiHAL(int error);
// some common macros

#define min(x, y)       ((x) < (y) ? (x) : (y))
#define max(x, y)       ((x) > (y) ? (x) : (y))

#define REQUEST_ID_MAX 1000
#define get_requestid() ((arc4random()%REQUEST_ID_MAX) + 1)
#define WAIT_TIME_FOR_SET_REG_DOMAIN 50000

#ifndef UNUSED
#define UNUSED(x)    (void)(x)
#endif

#ifdef __cplusplus
extern "C"
{
#endif /* __cplusplus */
void hexdump(void *bytes, u16 len);
u8 get_rssi(u8 rssi_wo_noise_floor);
#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif

