/**
 * Copyright (C) 2017 The Android Open Source Project
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
#define _GNU_SOURCE
#include <fcntl.h>
#include <netinet/in.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

#include <errno.h>
#include <linux/genetlink.h>
#include <linux/netlink.h>
#include <linux/wireless.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <sys/types.h>
#include "local_poc.h"

typedef signed char s8;
typedef unsigned char u8;

typedef signed short s16;
typedef unsigned short u16;

typedef signed int s32;
typedef unsigned int u32;

typedef signed long long s64;
typedef unsigned long long u64;

typedef s8 __s8;
typedef u8 __u8;
typedef s16 __s16;
typedef u16 __u16;
typedef s32 __s32;
typedef u32 __u32;
typedef s64 __s64;
typedef u64 __u64;

#define PARAM_MAX QCA_WLAN_VENDOR_ATTR_EXTSCAN_SUBCMD_CONFIG_PARAM_MAX
#define PARAM_REQUEST_ID \
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_SUBCMD_CONFIG_PARAM_REQUEST_ID
#define PARAMS_LOST_SSID_SAMPLE_SIZE \
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_SSID_HOTLIST_PARAMS_LOST_SSID_SAMPLE_SIZE
#define PARAMS_NUM_SSID \
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_SSID_HOTLIST_PARAMS_NUM_SSID
#define THRESHOLD_PARAM QCA_WLAN_VENDOR_ATTR_EXTSCAN_SSID_THRESHOLD_PARAM
#define PARAM_SSID QCA_WLAN_VENDOR_ATTR_EXTSCAN_SSID_THRESHOLD_PARAM_SSID
#define PARAM_BAND QCA_WLAN_VENDOR_ATTR_EXTSCAN_SSID_THRESHOLD_PARAM_BAND
#define PARAM_RSSI_LOW \
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_SSID_THRESHOLD_PARAM_RSSI_LOW
#define PARAM_RSSI_HIGH \
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_SSID_THRESHOLD_PARAM_RSSI_HIGH

enum qca_wlan_vendor_attr_extscan_config_params {
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_SUBCMD_CONFIG_PARAM_INVALID = 0,

  /* Unsigned 32-bit value; Middleware provides it to the driver. Middle ware
   * either gets it from caller, e.g., framework, or generates one if
   * framework doesn't provide it.
   */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_SUBCMD_CONFIG_PARAM_REQUEST_ID,

  /* NL attributes for data used by
   * QCA_NL80211_VENDOR_SUBCMD_EXTSCAN_GET_VALID_CHANNELS sub command.
   */
  /* Unsigned 32-bit value */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_GET_VALID_CHANNELS_CONFIG_PARAM_WIFI_BAND,
  /* Unsigned 32-bit value */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_GET_VALID_CHANNELS_CONFIG_PARAM_MAX_CHANNELS,

  /* NL attributes for input params used by
   * QCA_NL80211_VENDOR_SUBCMD_EXTSCAN_START sub command.
   */

  /* Unsigned 32-bit value; channel frequency */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_CHANNEL_SPEC_CHANNEL,
  /* Unsigned 32-bit value; dwell time in ms. */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_CHANNEL_SPEC_DWELL_TIME,
  /* Unsigned 8-bit value; 0: active; 1: passive; N/A for DFS */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_CHANNEL_SPEC_PASSIVE,
  /* Unsigned 8-bit value; channel class */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_CHANNEL_SPEC_CLASS,

  /* Unsigned 8-bit value; bucket index, 0 based */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_BUCKET_SPEC_INDEX,
  /* Unsigned 8-bit value; band. */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_BUCKET_SPEC_BAND,
  /* Unsigned 32-bit value; desired period, in ms. */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_BUCKET_SPEC_PERIOD,
  /* Unsigned 8-bit value; report events semantics. */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_BUCKET_SPEC_REPORT_EVENTS,
  /* Unsigned 32-bit value.
   * Followed by a nested array of EXTSCAN_CHANNEL_SPEC_* attributes.
   */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_BUCKET_SPEC_NUM_CHANNEL_SPECS,

  /* Array of QCA_WLAN_VENDOR_ATTR_EXTSCAN_CHANNEL_SPEC_* attributes.
   * Array size: QCA_WLAN_VENDOR_ATTR_EXTSCAN_BUCKET_SPEC_NUM_CHANNEL_SPECS
   */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_CHANNEL_SPEC,

  /* Unsigned 32-bit value; base timer period in ms. */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_SCAN_CMD_PARAMS_BASE_PERIOD,
  /* Unsigned 32-bit value; number of APs to store in each scan in the
   * BSSID/RSSI history buffer (keep the highest RSSI APs).
   */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_SCAN_CMD_PARAMS_MAX_AP_PER_SCAN,
  /* Unsigned 8-bit value; in %, when scan buffer is this much full, wake up
   * APPS.
   */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_SCAN_CMD_PARAMS_REPORT_THRESHOLD_PERCENT,
  /* Unsigned 8-bit value; number of scan bucket specs; followed by a nested
   * array of_EXTSCAN_BUCKET_SPEC_* attributes and values. The size of the
   * array is determined by NUM_BUCKETS.
   */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_SCAN_CMD_PARAMS_NUM_BUCKETS,

  /* Array of QCA_WLAN_VENDOR_ATTR_EXTSCAN_BUCKET_SPEC_* attributes.
   * Array size: QCA_WLAN_VENDOR_ATTR_EXTSCAN_SCAN_CMD_PARAMS_NUM_BUCKETS
   */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_BUCKET_SPEC,

  /* Unsigned 8-bit value */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_GET_CACHED_SCAN_RESULTS_CONFIG_PARAM_FLUSH,
  /* Unsigned 32-bit value; maximum number of results to be returned. */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_GET_CACHED_SCAN_RESULTS_CONFIG_PARAM_MAX,

  /* An array of 6 x Unsigned 8-bit value */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_AP_THRESHOLD_PARAM_BSSID,
  /* Signed 32-bit value */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_AP_THRESHOLD_PARAM_RSSI_LOW,
  /* Signed 32-bit value */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_AP_THRESHOLD_PARAM_RSSI_HIGH,
  /* Unsigned 32-bit value */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_AP_THRESHOLD_PARAM_CHANNEL,

  /* Number of hotlist APs as unsigned 32-bit value, followed by a nested
   * array of AP_THRESHOLD_PARAM attributes and values. The size of the
   * array is determined by NUM_AP.
   */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_BSSID_HOTLIST_PARAMS_NUM_AP,

  /* Array of QCA_WLAN_VENDOR_ATTR_EXTSCAN_AP_THRESHOLD_PARAM_* attributes.
   * Array size: QCA_WLAN_VENDOR_ATTR_EXTSCAN_BUCKET_SPEC_NUM_CHANNEL_SPECS
   */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_AP_THRESHOLD_PARAM,

  /* Unsigned 32bit value; number of samples for averaging RSSI. */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_SIGNIFICANT_CHANGE_PARAMS_RSSI_SAMPLE_SIZE,
  /* Unsigned 32bit value; number of samples to confirm AP loss. */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_SIGNIFICANT_CHANGE_PARAMS_LOST_AP_SAMPLE_SIZE,
  /* Unsigned 32bit value; number of APs breaching threshold. */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_SIGNIFICANT_CHANGE_PARAMS_MIN_BREACHING,
  /* Unsigned 32bit value; number of APs. Followed by an array of
   * AP_THRESHOLD_PARAM attributes. Size of the array is NUM_AP.
   */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_SIGNIFICANT_CHANGE_PARAMS_NUM_AP,
  /* Unsigned 32bit value; number of samples to confirm AP loss. */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_BSSID_HOTLIST_PARAMS_LOST_AP_SAMPLE_SIZE,

  /* Unsigned 32-bit value. If max_period is non zero or different than
   * period, then this bucket is an exponential backoff bucket.
   */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_BUCKET_SPEC_MAX_PERIOD,
  /* Unsigned 32-bit value. */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_BUCKET_SPEC_BASE,
  /* Unsigned 32-bit value. For exponential back off bucket, number of scans
   * to performed for a given period.
   */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_BUCKET_SPEC_STEP_COUNT,
  /* Unsigned 8-bit value; in number of scans, wake up AP after these
   * many scans.
   */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_SCAN_CMD_PARAMS_REPORT_THRESHOLD_NUM_SCANS,

  /* NL attributes for data used by
   * QCA_NL80211_VENDOR_SUBCMD_EXTSCAN_SET_SSID_HOTLIST sub command.
   */
  /* Unsigned 32bit value; number of samples to confirm SSID loss. */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_SSID_HOTLIST_PARAMS_LOST_SSID_SAMPLE_SIZE,
  /* Number of hotlist SSIDs as unsigned 32-bit value, followed by a nested
   * array of SSID_THRESHOLD_PARAM_* attributes and values. The size of the
   * array is determined by NUM_SSID.
   */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_SSID_HOTLIST_PARAMS_NUM_SSID,
  /* Array of QCA_WLAN_VENDOR_ATTR_EXTSCAN_SSID_THRESHOLD_PARAM_* attributes.
   * Array size: QCA_WLAN_VENDOR_ATTR_EXTSCAN_SSID_HOTLIST_PARAMS_NUM_SSID
   */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_SSID_THRESHOLD_PARAM,

  /* An array of 33 x Unsigned 8-bit value; NULL terminated SSID */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_SSID_THRESHOLD_PARAM_SSID,
  /* Unsigned 8-bit value */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_SSID_THRESHOLD_PARAM_BAND,
  /* Signed 32-bit value */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_SSID_THRESHOLD_PARAM_RSSI_LOW,
  /* Signed 32-bit value */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_SSID_THRESHOLD_PARAM_RSSI_HIGH,

  /* Unsigned 32-bit value; a bitmask w/additional extscan config flag. */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_CONFIGURATION_FLAGS,

  /* keep last */
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_SUBCMD_CONFIG_PARAM_AFTER_LAST,
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_SUBCMD_CONFIG_PARAM_MAX =
      QCA_WLAN_VENDOR_ATTR_EXTSCAN_SUBCMD_CONFIG_PARAM_AFTER_LAST - 1,
};

enum qca_nl80211_vendor_subcmds {
  QCA_NL80211_VENDOR_SUBCMD_UNSPEC = 0,
  QCA_NL80211_VENDOR_SUBCMD_TEST = 1,
  /* subcmds 2..8 not yet allocated */
  QCA_NL80211_VENDOR_SUBCMD_ROAMING = 9,
  QCA_NL80211_VENDOR_SUBCMD_AVOID_FREQUENCY = 10,
  QCA_NL80211_VENDOR_SUBCMD_DFS_CAPABILITY = 11,
  QCA_NL80211_VENDOR_SUBCMD_NAN = 12,
  QCA_NL80211_VENDOR_SUBCMD_STATS_EXT = 13,
  /* subcommands for link layer statistics start here */
  QCA_NL80211_VENDOR_SUBCMD_LL_STATS_SET = 14,
  QCA_NL80211_VENDOR_SUBCMD_LL_STATS_GET = 15,
  QCA_NL80211_VENDOR_SUBCMD_LL_STATS_CLR = 16,
  QCA_NL80211_VENDOR_SUBCMD_LL_STATS_RADIO_RESULTS = 17,
  QCA_NL80211_VENDOR_SUBCMD_LL_STATS_IFACE_RESULTS = 18,
  QCA_NL80211_VENDOR_SUBCMD_LL_STATS_PEERS_RESULTS = 19,
  /* subcommands for extscan start here */
  QCA_NL80211_VENDOR_SUBCMD_EXTSCAN_START = 20,
  QCA_NL80211_VENDOR_SUBCMD_EXTSCAN_STOP = 21,
  QCA_NL80211_VENDOR_SUBCMD_EXTSCAN_GET_VALID_CHANNELS = 22,
  QCA_NL80211_VENDOR_SUBCMD_EXTSCAN_GET_CAPABILITIES = 23,
  QCA_NL80211_VENDOR_SUBCMD_EXTSCAN_GET_CACHED_RESULTS = 24,
  /* Used when report_threshold is reached in scan cache. */
  QCA_NL80211_VENDOR_SUBCMD_EXTSCAN_SCAN_RESULTS_AVAILABLE = 25,
  /* Used to report scan results when each probe rsp. is received,
   * if report_events enabled in wifi_scan_cmd_params.
   */
  QCA_NL80211_VENDOR_SUBCMD_EXTSCAN_FULL_SCAN_RESULT = 26,
  /* Indicates progress of scanning state-machine. */
  QCA_NL80211_VENDOR_SUBCMD_EXTSCAN_SCAN_EVENT = 27,
  /* Indicates BSSID Hotlist. */
  QCA_NL80211_VENDOR_SUBCMD_EXTSCAN_HOTLIST_AP_FOUND = 28,
  QCA_NL80211_VENDOR_SUBCMD_EXTSCAN_SET_BSSID_HOTLIST = 29,
  QCA_NL80211_VENDOR_SUBCMD_EXTSCAN_RESET_BSSID_HOTLIST = 30,
  QCA_NL80211_VENDOR_SUBCMD_EXTSCAN_SIGNIFICANT_CHANGE = 31,
  QCA_NL80211_VENDOR_SUBCMD_EXTSCAN_SET_SIGNIFICANT_CHANGE = 32,
  QCA_NL80211_VENDOR_SUBCMD_EXTSCAN_RESET_SIGNIFICANT_CHANGE = 33,
  /* EXT TDLS */
  QCA_NL80211_VENDOR_SUBCMD_TDLS_ENABLE = 34,
  QCA_NL80211_VENDOR_SUBCMD_TDLS_DISABLE = 35,
  QCA_NL80211_VENDOR_SUBCMD_TDLS_GET_STATUS = 36,
  QCA_NL80211_VENDOR_SUBCMD_TDLS_STATE = 37,
  /* Get supported features */
  QCA_NL80211_VENDOR_SUBCMD_GET_SUPPORTED_FEATURES = 38,

  /* Set scanning_mac_oui */
  QCA_NL80211_VENDOR_SUBCMD_SCANNING_MAC_OUI = 39,
  /* Set nodfs_flag */
  QCA_NL80211_VENDOR_SUBCMD_NO_DFS_FLAG = 40,

  QCA_NL80211_VENDOR_SUBCMD_EXTSCAN_HOTLIST_AP_LOST = 41,

  /* Get Concurrency Matrix */
  QCA_NL80211_VENDOR_SUBCMD_GET_CONCURRENCY_MATRIX = 42,

  /* Get the security keys for key management offload */
  QCA_NL80211_VENDOR_SUBCMD_KEY_MGMT_SET_KEY = 50,

  /* Send the roaming and authentication info after roaming */
  QCA_NL80211_VENDOR_SUBCMD_KEY_MGMT_ROAM_AUTH = 51,

  QCA_NL80211_VENDOR_SUBCMD_APFIND = 52,

  /* Deprecated */
  QCA_NL80211_VENDOR_SUBCMD_OCB_SET_SCHED = 53,

  QCA_NL80211_VENDOR_SUBCMD_DO_ACS = 54,

  /* Get the supported features by the driver */
  QCA_NL80211_VENDOR_SUBCMD_GET_FEATURES = 55,

  /* Off loaded DFS events */
  QCA_NL80211_VENDOR_SUBCMD_DFS_OFFLOAD_CAC_STARTED = 56,
  QCA_NL80211_VENDOR_SUBCMD_DFS_OFFLOAD_CAC_FINISHED = 57,
  QCA_NL80211_VENDOR_SUBCMD_DFS_OFFLOAD_CAC_ABORTED = 58,
  QCA_NL80211_VENDOR_SUBCMD_DFS_OFFLOAD_CAC_NOP_FINISHED = 59,
  QCA_NL80211_VENDOR_SUBCMD_DFS_OFFLOAD_RADAR_DETECTED = 60,

  /* Get Wifi Specific Info */
  QCA_NL80211_VENDOR_SUBCMD_GET_WIFI_INFO = 61,
  /* Start Wifi Logger */
  QCA_NL80211_VENDOR_SUBCMD_WIFI_LOGGER_START = 62,
  /* Start Wifi Memory Dump */
  QCA_NL80211_VENDOR_SUBCMD_WIFI_LOGGER_MEMORY_DUMP = 63,
  QCA_NL80211_VENDOR_SUBCMD_ROAM = 64,
  QCA_NL80211_VENDOR_SUBCMD_EXTSCAN_SET_SSID_HOTLIST = 65,
  QCA_NL80211_VENDOR_SUBCMD_EXTSCAN_RESET_SSID_HOTLIST = 66,
  QCA_NL80211_VENDOR_SUBCMD_EXTSCAN_HOTLIST_SSID_FOUND = 67,
  QCA_NL80211_VENDOR_SUBCMD_EXTSCAN_HOTLIST_SSID_LOST = 68,
  QCA_NL80211_VENDOR_SUBCMD_EXTSCAN_PNO_SET_LIST = 69,
  QCA_NL80211_VENDOR_SUBCMD_EXTSCAN_PNO_SET_PASSPOINT_LIST = 70,
  QCA_NL80211_VENDOR_SUBCMD_EXTSCAN_PNO_RESET_PASSPOINT_LIST = 71,
  QCA_NL80211_VENDOR_SUBCMD_EXTSCAN_PNO_NETWORK_FOUND = 72,
  QCA_NL80211_VENDOR_SUBCMD_EXTSCAN_PNO_PASSPOINT_NETWORK_FOUND = 73,

  /* Wi-Fi Configuration subcommands */
  QCA_NL80211_VENDOR_SUBCMD_SET_WIFI_CONFIGURATION = 74,
  QCA_NL80211_VENDOR_SUBCMD_GET_WIFI_CONFIGURATION = 75,

  QCA_NL80211_VENDOR_SUBCMD_GET_LOGGER_FEATURE_SET = 76,
  QCA_NL80211_VENDOR_SUBCMD_GET_RING_DATA = 77,
  QCA_NL80211_VENDOR_SUBCMD_TDLS_GET_CAPABILITIES = 78,

  QCA_NL80211_VENDOR_SUBCMD_OFFLOADED_PACKETS = 79,
  QCA_NL80211_VENDOR_SUBCMD_MONITOR_RSSI = 80,
  QCA_NL80211_VENDOR_SUBCMD_NDP = 81,

  /* NS Offload enable/disable cmd */
  QCA_NL80211_VENDOR_SUBCMD_ND_OFFLOAD = 82,

  QCA_NL80211_VENDOR_SUBCMD_PACKET_FILTER = 83,
  QCA_NL80211_VENDOR_SUBCMD_GET_BUS_SIZE = 84,

  QCA_NL80211_VENDOR_SUBCMD_GET_WAKE_REASON_STATS = 85,

  /* OCB commands */
  QCA_NL80211_VENDOR_SUBCMD_OCB_SET_CONFIG = 92,
  QCA_NL80211_VENDOR_SUBCMD_OCB_SET_UTC_TIME = 93,
  QCA_NL80211_VENDOR_SUBCMD_OCB_START_TIMING_ADVERT = 94,
  QCA_NL80211_VENDOR_SUBCMD_OCB_STOP_TIMING_ADVERT = 95,
  QCA_NL80211_VENDOR_SUBCMD_OCB_GET_TSF_TIMER = 96,
  QCA_NL80211_VENDOR_SUBCMD_DCC_GET_STATS = 97,
  QCA_NL80211_VENDOR_SUBCMD_DCC_CLEAR_STATS = 98,
  QCA_NL80211_VENDOR_SUBCMD_DCC_UPDATE_NDL = 99,
  QCA_NL80211_VENDOR_SUBCMD_DCC_STATS_EVENT = 100,

  /* subcommand to get link properties */
  QCA_NL80211_VENDOR_SUBCMD_LINK_PROPERTIES = 101,
  QCA_NL80211_VENDOR_SUBCMD_SETBAND = 105,
  QCA_NL80211_VENDOR_SUBCMD_SET_SAP_CONFIG = 118,
};

enum qca_wlan_vendor_attr {
  QCA_WLAN_VENDOR_ATTR_INVALID = 0,
  /* used by QCA_NL80211_VENDOR_SUBCMD_DFS_CAPABILITY */
  QCA_WLAN_VENDOR_ATTR_DFS = 1,
  /* used by QCA_NL80211_VENDOR_SUBCMD_NAN */
  QCA_WLAN_VENDOR_ATTR_NAN = 2,
  /* used by QCA_NL80211_VENDOR_SUBCMD_STATS_EXT */
  QCA_WLAN_VENDOR_ATTR_STATS_EXT = 3,
  /* used by QCA_NL80211_VENDOR_SUBCMD_STATS_EXT */
  QCA_WLAN_VENDOR_ATTR_IFINDEX = 4,

  /* used by QCA_NL80211_VENDOR_SUBCMD_ROAMING */
  QCA_WLAN_VENDOR_ATTR_ROAMING_POLICY = 5,
  QCA_WLAN_VENDOR_ATTR_MAC_ADDR = 6,

  /* used by QCA_NL80211_VENDOR_SUBCMD_GET_FEATURES */
  QCA_WLAN_VENDOR_ATTR_FEATURE_FLAGS = 7,

  /* Unsigned 32-bit value from enum qca_set_band */
  QCA_WLAN_VENDOR_ATTR_SETBAND_VALUE = 12,

  /* keep last */
  QCA_WLAN_VENDOR_ATTR_AFTER_LAST,
  QCA_WLAN_VENDOR_ATTR_MAX = QCA_WLAN_VENDOR_ATTR_AFTER_LAST - 1
};

#define NETLINK_KERNEL_SOCKET 0x1
#define NETLINK_RECV_PKTINFO 0x2
#define NETLINK_BROADCAST_SEND_ERROR 0x4
#define NETLINK_RECV_NO_ENOBUFS 0x8

#define NLMSG_MIN_TYPE 0x10

#define GENL_ID_GENERATE 0
#define GENL_ID_CTRL NLMSG_MIN_TYPE
#define GENL_ID_VFS_DQUOT (NLMSG_MIN_TYPE + 1)
#define GENL_ID_PMCRAID (NLMSG_MIN_TYPE + 2)

#define GENLMSG_DATA(glh) ((void *)(NLMSG_DATA(glh) + GENL_HDRLEN))
#define NLA_DATA(na) ((void *)((char *)(na) + NLA_HDRLEN))
#define QCA_NL80211_VENDOR_ID 0x001374

#define MAX_MSG_SIZE 1024

struct msgtemplate {
  struct nlmsghdr n;
  struct genlmsghdr g;
  char buf[MAX_MSG_SIZE];
};

enum qca_wlan_vendor_attr_ndp_params {
  QCA_WLAN_VENDOR_ATTR_NDP_PARAM_INVALID = 0,
  QCA_WLAN_VENDOR_ATTR_NDP_SUBCMD,
  QCA_WLAN_VENDOR_ATTR_NDP_TRANSACTION_ID,
  QCA_WLAN_VENDOR_ATTR_NDP_SERVICE_INSTANCE_ID,
  QCA_WLAN_VENDOR_ATTR_NDP_CHANNEL,
  QCA_WLAN_VENDOR_ATTR_NDP_PEER_DISCOVERY_MAC_ADDR,
  QCA_WLAN_VENDOR_ATTR_NDP_IFACE_STR,
  QCA_WLAN_VENDOR_ATTR_NDP_CONFIG_SECURITY,
  QCA_WLAN_VENDOR_ATTR_NDP_CONFIG_QOS,
  QCA_WLAN_VENDOR_ATTR_NDP_APP_INFO_LEN,
  QCA_WLAN_VENDOR_ATTR_NDP_APP_INFO,
  QCA_WLAN_VENDOR_ATTR_NDP_INSTANCE_ID,
  QCA_WLAN_VENDOR_ATTR_NDP_NUM_INSTANCE_ID,
  QCA_WLAN_VENDOR_ATTR_NDP_INSTANCE_ID_ARRAY,
  QCA_WLAN_VENDOR_ATTR_NDP_RESPONSE_CODE,
  QCA_WLAN_VENDOR_ATTR_NDP_SCHEDULE_STATUS_CODE,
  QCA_WLAN_VENDOR_ATTR_NDP_NDI_MAC_ADDR,
  QCA_WLAN_VENDOR_ATTR_NDP_DRV_RETURN_TYPE,
  QCA_WLAN_VENDOR_ATTR_NDP_DRV_RETURN_VALUE,

  QCA_WLAN_VENDOR_ATTR_NDP_PARAMS_AFTER_LAST,
  QCA_WLAN_VENDOR_ATTR_NDP_PARAMS_MAX =
      QCA_WLAN_VENDOR_ATTR_NDP_PARAMS_AFTER_LAST - 1,
};

enum qca_wlan_vendor_attr_dcc_update_ndl {
  QCA_WLAN_VENDOR_ATTR_DCC_UPDATE_NDL_INVALID = 0,
  QCA_WLAN_VENDOR_ATTR_DCC_UPDATE_NDL_CHANNEL_COUNT,
  QCA_WLAN_VENDOR_ATTR_DCC_UPDATE_NDL_CHANNEL_ARRAY,
  QCA_WLAN_VENDOR_ATTR_DCC_UPDATE_NDL_ACTIVE_STATE_ARRAY,
  QCA_WLAN_VENDOR_ATTR_DCC_UPDATE_NDL_AFTER_LAST,
  QCA_WLAN_VENDOR_ATTR_DCC_UPDATE_NDL_MAX =
      QCA_WLAN_VENDOR_ATTR_DCC_UPDATE_NDL_AFTER_LAST - 1,
};

#define PARAM_MAX QCA_WLAN_VENDOR_ATTR_EXTSCAN_SUBCMD_CONFIG_PARAM_MAX
#define PARAM_REQUEST_ID \
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_SUBCMD_CONFIG_PARAM_REQUEST_ID
#define PARAM_BASE_PERIOD \
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_SCAN_CMD_PARAMS_BASE_PERIOD
#define PARAM_MAX_AP_PER_SCAN \
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_SCAN_CMD_PARAMS_MAX_AP_PER_SCAN
#define PARAM_RPT_THRHLD_PERCENT \
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_SCAN_CMD_PARAMS_REPORT_THRESHOLD_PERCENT
#define PARAM_RPT_THRHLD_NUM_SCANS \
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_SCAN_CMD_PARAMS_REPORT_THRESHOLD_NUM_SCANS
#define PARAM_NUM_BUCKETS \
  QCA_WLAN_VENDOR_ATTR_EXTSCAN_SCAN_CMD_PARAMS_NUM_BUCKETS
#define PARAM_CONFIG_FLAGS QCA_WLAN_VENDOR_ATTR_EXTSCAN_CONFIGURATION_FLAGS

static int send_cmd(int sd, __u16 nlmsg_type, __u32 nlmsg_pid, __u8 genl_cmd,
                    __u16 nla_type, void *nla_data, int nla_len) {
  printf("send_cmd %s %d\n", nla_data, nla_len);
  struct nlattr *na;
  struct sockaddr_nl nladdr;
  int r, buflen;
  char *buf;

  struct msgtemplate msg;

  msg.n.nlmsg_len = NLMSG_LENGTH(GENL_HDRLEN);
  msg.n.nlmsg_type = nlmsg_type;
  msg.n.nlmsg_flags = NLM_F_REQUEST;
  msg.n.nlmsg_seq = 0;
  msg.n.nlmsg_pid = nlmsg_pid;
  msg.g.cmd = genl_cmd;
  msg.g.version = 0x1;
  na = (struct nlattr *)GENLMSG_DATA(&msg);
  na->nla_type = nla_type;
  na->nla_len = nla_len + 1 + NLA_HDRLEN;
  memcpy(NLA_DATA(na), nla_data, nla_len);
  msg.n.nlmsg_len += NLMSG_ALIGN(na->nla_len);

  buf = (char *)&msg;
  buflen = msg.n.nlmsg_len;
  memset(&nladdr, 0, sizeof(nladdr));
  nladdr.nl_family = AF_NETLINK;

  if (buflen !=
      sendto(sd, buf, buflen, 0, (struct sockaddr *)&nladdr, sizeof(nladdr))) {
    return -1;
  }
  return 0;
}

static int get_family_id(int sd) {
  int id = 0, rc;
  struct nlattr *na;
  int rep_len;
  struct msgtemplate ans;

  memset(&ans, 0, sizeof(struct msgtemplate));

  rc = send_cmd(sd, GENL_ID_CTRL, getpid(), CTRL_CMD_GETFAMILY,
                CTRL_ATTR_FAMILY_NAME, (void *)NL80211_GENL_NAME,
                strlen(NL80211_GENL_NAME) + 1);
  if (rc < 0) {
    return 0; /* sendto() failure? */
  }

  rep_len = recv(sd, &ans, sizeof(ans), 0);
  if (rep_len < 0) {
    return -1;
  }

  na = (struct nlattr *)GENLMSG_DATA(&ans);
  na = (struct nlattr *)((char *)na + NLA_ALIGN(na->nla_len));
  if (na->nla_type == CTRL_ATTR_FAMILY_ID) {
    id = *(__u16 *)NLA_DATA(na);
  }
  na = (struct nlattr *)((char *)na + NLA_ALIGN(na->nla_len));
  return id;
}

int start_p2p(int id, int fd) {
  struct nlattr *na;
  struct nlattr *na_data;
  struct sockaddr_nl nladdr;
  int r, buflen, ret;
  char *buf;
  struct msgtemplate msg, ans;

  msg.n.nlmsg_len = NLMSG_LENGTH(GENL_HDRLEN);
  msg.n.nlmsg_type = id;
  msg.n.nlmsg_flags = NLM_F_REQUEST;
  msg.n.nlmsg_seq = 0;
  msg.n.nlmsg_pid = getpid();
  msg.g.cmd = NL80211_CMD_START_P2P_DEVICE;
  msg.g.version = 1;

  na = (struct nlattr *)GENLMSG_DATA(&msg);
  na->nla_type = NL80211_ATTR_WIPHY;
  na->nla_len = 4 + NLA_HDRLEN;
  *(u32 *)NLA_DATA(na) = 0;
  msg.n.nlmsg_len += NLMSG_ALIGN(na->nla_len);

  na = (struct nlattr *)((char *)na + NLMSG_ALIGN(na->nla_len));
  na->nla_type = NL80211_ATTR_IFINDEX;
  na->nla_len = 4 + NLA_HDRLEN;
  *(u32 *)NLA_DATA(na) = 24;
  msg.n.nlmsg_len += NLMSG_ALIGN(na->nla_len);

  buf = (char *)&msg;
  buflen = msg.n.nlmsg_len;
  memset(&nladdr, 0, sizeof(nladdr));
  nladdr.nl_family = AF_NETLINK;
  ret = sendto(fd, buf, buflen, 0, (struct sockaddr *)&nladdr, sizeof(nladdr));
  if (ret < 0) {
    return -1;
  }

  ret = recv(fd, &ans, sizeof(ans), 0);
  return ret;
}

unsigned if_nametoindex(const char *ifname) {
  int s = socket(AF_INET, SOCK_DGRAM | SOCK_CLOEXEC, 0);
  if (s == -1) return 0;
  int ret = 0;
  struct ifreq ifr;

  memset(&ifr, 0, sizeof(ifr));
  strncpy(ifr.ifr_name, ifname, sizeof(ifr.ifr_name));
  ifr.ifr_name[IFNAMSIZ - 1] = 0;

  ret = ioctl(s, SIOCGIFINDEX, &ifr);
  close(s);
  return (ret == -1) ? 0 : ifr.ifr_ifindex;
}

int get_wiphy_idx(int id, int fd, int *ifindex, int *wiphyid, char *ifname) {
  struct nlattr *na;
  struct nlattr *na_data;
  struct sockaddr_nl nladdr;
  int r, buflen, ret;
  char *buf;
  struct msgtemplate msg, ans;

  int if_index = if_nametoindex("wlan0");
  msg.n.nlmsg_len = NLMSG_LENGTH(GENL_HDRLEN);
  msg.n.nlmsg_type = id;
  msg.n.nlmsg_flags = NLM_F_REQUEST;
  msg.n.nlmsg_seq = 0;
  msg.n.nlmsg_pid = getpid();
  msg.g.cmd = NL80211_CMD_GET_INTERFACE;
  msg.g.version = 1;

  na = (struct nlattr *)GENLMSG_DATA(&msg);
  na->nla_type = NL80211_ATTR_IFINDEX;
  na->nla_len = 4 + NLA_HDRLEN;
  *(u32 *)NLA_DATA(na) = if_index;
  msg.n.nlmsg_len += NLMSG_ALIGN(na->nla_len);

  buf = (char *)&msg;
  buflen = msg.n.nlmsg_len;
  memset(&nladdr, 0, sizeof(nladdr));
  nladdr.nl_family = AF_NETLINK;
  ret = sendto(fd, buf, buflen, 0, (struct sockaddr *)&nladdr, sizeof(nladdr));
  if (ret < 0) {
    return -1;
  }

  memset(&ans, 0, sizeof(ans));
  ret = recv(fd, &ans, sizeof(ans), 0);

  na = (struct nlattr *)GENLMSG_DATA(&ans);
  *ifindex = *(u32 *)NLA_DATA(na);

  na = (struct nlattr *)((char *)na + NLA_ALIGN(na->nla_len));
  strcpy(ifname, NLA_DATA(na));

  na = (struct nlattr *)((char *)na + NLA_ALIGN(na->nla_len));
  *wiphyid = *(u32 *)NLA_DATA(na);

  return ret;
}

int main(int argc, const char *argv[]) {
  int ret;

  int fd;
  struct sockaddr_nl local;
  struct msgtemplate ans;

  /* create socket */
  fd = socket(AF_NETLINK, SOCK_RAW, NETLINK_GENERIC);
  if (fd < 0) return -1;

  memset(&local, 0, sizeof(local));
  local.nl_family = AF_NETLINK;

  if (bind(fd, (struct sockaddr *)&local, sizeof(local)) < 0) {
    return -1;
  }

  int id = get_family_id(fd);

  int ifindex, wiphyid;
  char ifname[64];
  get_wiphy_idx(id, fd, &ifindex, &wiphyid, ifname);

  struct nlattr *na;
  struct nlattr *na_data;
  struct sockaddr_nl nladdr;
  int r, buflen;
  char *buf;
  struct msgtemplate msg;

  msg.n.nlmsg_len = NLMSG_LENGTH(GENL_HDRLEN);
  msg.n.nlmsg_type = id;
  msg.n.nlmsg_flags = NLM_F_REQUEST | NLM_F_ACK;
  msg.n.nlmsg_seq = time(0);
  msg.n.nlmsg_pid = getpid();
  msg.g.cmd = NL80211_CMD_VENDOR;
  msg.g.version = 1;

  na = (struct nlattr *)GENLMSG_DATA(&msg);
  na->nla_type = NL80211_ATTR_VENDOR_ID;
  na->nla_len = 4 + NLA_HDRLEN;
  *(u32 *)NLA_DATA(na) = QCA_NL80211_VENDOR_ID;
  msg.n.nlmsg_len += NLMSG_ALIGN(na->nla_len);

  na = (struct nlattr *)((char *)na + NLMSG_ALIGN(na->nla_len));
  na->nla_type = NL80211_ATTR_VENDOR_SUBCMD;
  na->nla_len = 4 + NLA_HDRLEN;
  *(u32 *)NLA_DATA(na) = QCA_NL80211_VENDOR_SUBCMD_EXTSCAN_START;
  msg.n.nlmsg_len += NLMSG_ALIGN(na->nla_len);

  na = (struct nlattr *)((char *)na + NLMSG_ALIGN(na->nla_len));
  na->nla_type = NL80211_ATTR_IFINDEX;
  na->nla_len = 4 + NLA_HDRLEN;
  *(u32 *)NLA_DATA(na) = ifindex;
  msg.n.nlmsg_len += NLMSG_ALIGN(na->nla_len);

  char data[1024] = {0};
  int data_size = 0;
  na_data = data;

  na_data = (struct nlattr *)((char *)na_data + NLMSG_ALIGN(na_data->nla_len));
  na_data->nla_type = PARAM_REQUEST_ID;
  na_data->nla_len = 4 + NLA_HDRLEN;
  *(u32 *)NLA_DATA(na_data) = -1;
  data_size += NLMSG_ALIGN(na_data->nla_len);

  na_data = (struct nlattr *)((char *)na_data + NLMSG_ALIGN(na_data->nla_len));
  na_data->nla_type = PARAM_BASE_PERIOD;
  na_data->nla_len = 4 + NLA_HDRLEN;
  *(u32 *)NLA_DATA(na_data) = -1;
  data_size += NLMSG_ALIGN(na_data->nla_len);

  na_data = (struct nlattr *)((char *)na_data + NLMSG_ALIGN(na_data->nla_len));
  na_data->nla_type = PARAM_MAX_AP_PER_SCAN;
  na_data->nla_len = 4 + NLA_HDRLEN;
  *(u32 *)NLA_DATA(na_data) = -1;
  data_size += NLMSG_ALIGN(na_data->nla_len);

  na_data = (struct nlattr *)((char *)na_data + NLMSG_ALIGN(na_data->nla_len));
  na_data->nla_type = PARAM_RPT_THRHLD_PERCENT;
  na_data->nla_len = 1 + NLA_HDRLEN;
  *(u32 *)NLA_DATA(na_data) = -1;
  data_size += NLMSG_ALIGN(na_data->nla_len);

  na_data = (struct nlattr *)((char *)na_data + NLMSG_ALIGN(na_data->nla_len));
  na_data->nla_type = PARAM_RPT_THRHLD_NUM_SCANS;
  na_data->nla_len = 1 + NLA_HDRLEN;
  *(u32 *)NLA_DATA(na_data) = -1;
  data_size += NLMSG_ALIGN(na_data->nla_len);

  na_data = (struct nlattr *)((char *)na_data + NLMSG_ALIGN(na_data->nla_len));
  na_data->nla_type = PARAM_NUM_BUCKETS;
  na_data->nla_len = 1 + NLA_HDRLEN;
  *(u32 *)NLA_DATA(na_data) = 1;
  data_size += NLMSG_ALIGN(na_data->nla_len);

  na_data = (struct nlattr *)((char *)na_data + NLMSG_ALIGN(na_data->nla_len));
  na_data->nla_type = PARAM_CONFIG_FLAGS;
  na_data->nla_len = 4 + NLA_HDRLEN;
  *(u32 *)NLA_DATA(na_data) = -1;
  data_size += NLMSG_ALIGN(na_data->nla_len);

  char apTh[256] = {0};
  int apTh_size = 0;
  struct nlattr *na_apTh = apTh;

  na_apTh = (struct nlattr *)((char *)na_apTh + NLMSG_ALIGN(na_apTh->nla_len));
  na_apTh->nla_type = QCA_WLAN_VENDOR_ATTR_EXTSCAN_BUCKET_SPEC_BAND;
  na_apTh->nla_len = 4 + NLA_HDRLEN;
  *(u32 *)NLA_DATA(na_apTh) = 0x1;
  apTh_size += NLMSG_ALIGN(na_apTh->nla_len);

  na_apTh = (struct nlattr *)((char *)na_apTh + NLMSG_ALIGN(na_apTh->nla_len));
  na_apTh->nla_type = QCA_WLAN_VENDOR_ATTR_EXTSCAN_BUCKET_SPEC_PERIOD;
  na_apTh->nla_len = 0 + NLA_HDRLEN;
  *(u32 *)NLA_DATA(na_apTh) = -1;
  apTh_size += NLMSG_ALIGN(na_apTh->nla_len);

  char middlebuf[256] = {0};
  int middlebuf_size = 0;
  struct nlattr *na_middle = middlebuf;

  na_middle =
      (struct nlattr *)((char *)na_middle + NLMSG_ALIGN(na_middle->nla_len));
  na_middle->nla_type = 0;
  na_middle->nla_len = apTh_size + NLA_HDRLEN;
  memcpy(NLA_DATA(na_middle), apTh, apTh_size);
  middlebuf_size += NLMSG_ALIGN(na_middle->nla_len);

  na_data = (struct nlattr *)((char *)na_data + NLMSG_ALIGN(na_data->nla_len));
  na_data->nla_type = QCA_WLAN_VENDOR_ATTR_EXTSCAN_BUCKET_SPEC;
  na_data->nla_len = middlebuf_size + NLA_HDRLEN;
  memcpy(NLA_DATA(na_data), middlebuf, middlebuf_size);
  data_size += NLMSG_ALIGN(na_data->nla_len);

  na = (struct nlattr *)((char *)na + NLMSG_ALIGN(na->nla_len));
  na->nla_type = NL80211_ATTR_VENDOR_DATA;
  na->nla_len = data_size + NLA_HDRLEN;
  memcpy(NLA_DATA(na), data, data_size);
  msg.n.nlmsg_len += NLMSG_ALIGN(na->nla_len);

  buf = (char *)&msg;
  buflen = msg.n.nlmsg_len;
  memset(&nladdr, 0, sizeof(nladdr));
  nladdr.nl_family = AF_NETLINK;
  ret = sendto(fd, buf, buflen, 0, (struct sockaddr *)&nladdr, sizeof(nladdr));
  if (ret < 0) {
    return -1;
  }

  memset(&ans, 0, sizeof(ans));

  ret = recv(fd, &ans, sizeof(ans), 0);
  na = (struct nlattr *)GENLMSG_DATA(&ans);
  char *temp = na;

  return ret;
}
