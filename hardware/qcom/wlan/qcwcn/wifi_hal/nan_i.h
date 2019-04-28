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

#ifndef __NAN_I_H__
#define __NAN_I_H__

#include "common.h"
#include "cpp_bindings.h"
#include "wifi_hal.h"

#ifdef __cplusplus
extern "C"
{
#endif /* __cplusplus */

#ifndef PACKED
#define PACKED  __attribute__((packed))
#endif
#define BIT_NONE            0x00
#define BIT_0               0x01
#define BIT_1               0x02
#define BIT_2               0x04
#define BIT_3               0x08
#define BIT_4               0x10
#define BIT_5               0x20
#define BIT_6               0x40
#define BIT_7               0x80
#define BIT_8               0x0100
#define BIT_9               0x0200
#define BIT_10              0x0400
#define BIT_11              0x0800
#define BIT_12              0x1000
#define BIT_13              0x2000
#define BIT_14              0x4000
#define BIT_15              0x8000
#define BIT_16              0x010000
#define BIT_17              0x020000
#define BIT_18              0x040000
#define BIT_19              0x080000
#define BIT_20              0x100000
#define BIT_21              0x200000
#define BIT_22              0x400000
#define BIT_23              0x800000
#define BIT_24              0x01000000
#define BIT_25              0x02000000
#define BIT_26              0x04000000
#define BIT_27              0x08000000
#define BIT_28              0x10000000
#define BIT_29              0x20000000
#define BIT_30              0x40000000
#define BIT_31              0x80000000

/** macro to convert FW MAC address from WMI word format to User Space MAC char array */
#define FW_MAC_ADDR_TO_CHAR_ARRAY(fw_mac_addr, mac_addr) do { \
     (mac_addr)[0] =    ((fw_mac_addr).mac_addr31to0) & 0xff; \
     (mac_addr)[1] =  (((fw_mac_addr).mac_addr31to0) >> 8) & 0xff; \
     (mac_addr)[2] =  (((fw_mac_addr).mac_addr31to0) >> 16) & 0xff; \
     (mac_addr)[3] =  (((fw_mac_addr).mac_addr31to0) >> 24) & 0xff; \
     (mac_addr)[4] =    ((fw_mac_addr).mac_addr47to32) & 0xff; \
     (mac_addr)[5] =  (((fw_mac_addr).mac_addr47to32) >> 8) & 0xff; \
} while (0)

/** macro to convert User space MAC address from char array to FW WMI word format */
#define CHAR_ARRAY_TO_MAC_ADDR(mac_addr, fw_mac_addr)  do { \
    (fw_mac_addr).mac_addr31to0  =                   \
         ((mac_addr)[0] | ((mac_addr)[1] << 8)            \
           | ((mac_addr)[2] << 16) | ((mac_addr)[3] << 24));          \
    (fw_mac_addr).mac_addr47to32  =                  \
         ((mac_addr)[4] | ((mac_addr)[5] << 8));          \
} while (0)

/*---------------------------------------------------------------------------
* WLAN NAN CONSTANTS
*--------------------------------------------------------------------------*/

typedef enum
{
    NAN_MSG_ID_ERROR_RSP                    = 0,
    NAN_MSG_ID_CONFIGURATION_REQ            = 1,
    NAN_MSG_ID_CONFIGURATION_RSP            = 2,
    NAN_MSG_ID_PUBLISH_SERVICE_REQ          = 3,
    NAN_MSG_ID_PUBLISH_SERVICE_RSP          = 4,
    NAN_MSG_ID_PUBLISH_SERVICE_CANCEL_REQ   = 5,
    NAN_MSG_ID_PUBLISH_SERVICE_CANCEL_RSP   = 6,
    NAN_MSG_ID_PUBLISH_REPLIED_IND          = 7,
    NAN_MSG_ID_PUBLISH_TERMINATED_IND       = 8,
    NAN_MSG_ID_SUBSCRIBE_SERVICE_REQ        = 9,
    NAN_MSG_ID_SUBSCRIBE_SERVICE_RSP        = 10,
    NAN_MSG_ID_SUBSCRIBE_SERVICE_CANCEL_REQ = 11,
    NAN_MSG_ID_SUBSCRIBE_SERVICE_CANCEL_RSP = 12,
    NAN_MSG_ID_MATCH_IND                    = 13,
    NAN_MSG_ID_MATCH_EXPIRED_IND            = 14,
    NAN_MSG_ID_SUBSCRIBE_TERMINATED_IND     = 15,
    NAN_MSG_ID_DE_EVENT_IND                 = 16,
    NAN_MSG_ID_TRANSMIT_FOLLOWUP_REQ        = 17,
    NAN_MSG_ID_TRANSMIT_FOLLOWUP_RSP        = 18,
    NAN_MSG_ID_FOLLOWUP_IND                 = 19,
    NAN_MSG_ID_STATS_REQ                    = 20,
    NAN_MSG_ID_STATS_RSP                    = 21,
    NAN_MSG_ID_ENABLE_REQ                   = 22,
    NAN_MSG_ID_ENABLE_RSP                   = 23,
    NAN_MSG_ID_DISABLE_REQ                  = 24,
    NAN_MSG_ID_DISABLE_RSP                  = 25,
    NAN_MSG_ID_DISABLE_IND                  = 26,
    NAN_MSG_ID_TCA_REQ                      = 27,
    NAN_MSG_ID_TCA_RSP                      = 28,
    NAN_MSG_ID_TCA_IND                      = 29,
    NAN_MSG_ID_BEACON_SDF_REQ               = 30,
    NAN_MSG_ID_BEACON_SDF_RSP               = 31,
    NAN_MSG_ID_BEACON_SDF_IND               = 32,
    NAN_MSG_ID_CAPABILITIES_REQ             = 33,
    NAN_MSG_ID_CAPABILITIES_RSP             = 34,
    NAN_MSG_ID_SELF_TRANSMIT_FOLLOWUP_IND   = 35,
    NAN_MSG_ID_RANGING_REQUEST_RECEVD_IND   = 36,
    NAN_MSG_ID_RANGING_RESULT_IND           = 37,
    NAN_MSG_ID_TESTMODE_REQ                 = 1025,
    NAN_MSG_ID_TESTMODE_RSP                 = 1026
} NanMsgId;

/*
  Various TLV Type ID sent as part of NAN Stats Response
  or NAN TCA Indication
*/
typedef enum
{
    NAN_TLV_TYPE_FIRST = 0,

    /* Service Discovery Frame types */
    NAN_TLV_TYPE_SDF_FIRST = NAN_TLV_TYPE_FIRST,
    NAN_TLV_TYPE_SERVICE_NAME = NAN_TLV_TYPE_SDF_FIRST,
    NAN_TLV_TYPE_SDF_MATCH_FILTER,
    NAN_TLV_TYPE_TX_MATCH_FILTER,
    NAN_TLV_TYPE_RX_MATCH_FILTER,
    NAN_TLV_TYPE_SERVICE_SPECIFIC_INFO,
    NAN_TLV_TYPE_EXT_SERVICE_SPECIFIC_INFO =5,
    NAN_TLV_TYPE_VENDOR_SPECIFIC_ATTRIBUTE_TRANSMIT = 6,
    NAN_TLV_TYPE_VENDOR_SPECIFIC_ATTRIBUTE_RECEIVE = 7,
    NAN_TLV_TYPE_POST_NAN_CONNECTIVITY_CAPABILITIES_RECEIVE = 8,
    NAN_TLV_TYPE_POST_NAN_DISCOVERY_ATTRIBUTE_RECEIVE = 9,
    NAN_TLV_TYPE_BEACON_SDF_PAYLOAD_RECEIVE = 10,
    NAN_TLV_TYPE_NAN_DATA_PATH_PARAMS = 11,
    NAN_TLV_TYPE_NAN_DATA_SUPPORTED_BAND = 12,
    NAN_TLV_TYPE_2G_COMMITTED_DW = 13,
    NAN_TLV_TYPE_5G_COMMITTED_DW = 14,
    NAN_TLV_TYPE_NAN_DATA_RESPONDER_MODE = 15,
    NAN_TLV_TYPE_NAN_DATA_ENABLED_IN_MATCH = 16,
    NAN_TLV_TYPE_NAN_SERVICE_ACCEPT_POLICY = 17,
    NAN_TLV_TYPE_NAN_CSID = 18,
    NAN_TLV_TYPE_NAN_SCID = 19,
    NAN_TLV_TYPE_NAN_PMK = 20,
    NAN_TLV_TYPE_SDEA_CTRL_PARAMS = 21,
    NAN_TLV_TYPE_NAN_RANGING_CFG = 22,
    NAN_TLV_TYPE_CONFIG_DISCOVERY_INDICATIONS = 23,
    NAN_TLV_TYPE_NAN20_RANGING_REQUEST = 24,
    NAN_TLV_TYPE_NAN20_RANGING_RESULT = 25,
    NAN_TLV_TYPE_NAN20_RANGING_REQUEST_RECEIVED = 26,
    NAN_TLV_TYPE_NAN_PASSPHRASE = 27,
    NAN_TLV_TYPE_SDEA_SERVICE_SPECIFIC_INFO = 28,
    NAN_TLV_TYPE_SDF_LAST = 4095,

    /* Configuration types */
    NAN_TLV_TYPE_CONFIG_FIRST = 4096,
    NAN_TLV_TYPE_24G_SUPPORT = NAN_TLV_TYPE_CONFIG_FIRST,
    NAN_TLV_TYPE_24G_BEACON,
    NAN_TLV_TYPE_24G_SDF,
    NAN_TLV_TYPE_24G_RSSI_CLOSE,
    NAN_TLV_TYPE_24G_RSSI_MIDDLE = 4100,
    NAN_TLV_TYPE_24G_RSSI_CLOSE_PROXIMITY,
    NAN_TLV_TYPE_5G_SUPPORT,
    NAN_TLV_TYPE_5G_BEACON,
    NAN_TLV_TYPE_5G_SDF,
    NAN_TLV_TYPE_5G_RSSI_CLOSE,
    NAN_TLV_TYPE_5G_RSSI_MIDDLE,
    NAN_TLV_TYPE_5G_RSSI_CLOSE_PROXIMITY,
    NAN_TLV_TYPE_SID_BEACON,
    NAN_TLV_TYPE_HOP_COUNT_LIMIT,
    NAN_TLV_TYPE_MASTER_PREFERENCE = 4110,
    NAN_TLV_TYPE_CLUSTER_ID_LOW,
    NAN_TLV_TYPE_CLUSTER_ID_HIGH,
    NAN_TLV_TYPE_RSSI_AVERAGING_WINDOW_SIZE,
    NAN_TLV_TYPE_CLUSTER_OUI_NETWORK_ID,
    NAN_TLV_TYPE_SOURCE_MAC_ADDRESS,
    NAN_TLV_TYPE_CLUSTER_ATTRIBUTE_IN_SDF,
    NAN_TLV_TYPE_SOCIAL_CHANNEL_SCAN_PARAMS,
    NAN_TLV_TYPE_DEBUGGING_FLAGS,
    NAN_TLV_TYPE_POST_NAN_CONNECTIVITY_CAPABILITIES_TRANSMIT,
    NAN_TLV_TYPE_POST_NAN_DISCOVERY_ATTRIBUTE_TRANSMIT = 4120,
    NAN_TLV_TYPE_FURTHER_AVAILABILITY_MAP,
    NAN_TLV_TYPE_HOP_COUNT_FORCE,
    NAN_TLV_TYPE_RANDOM_FACTOR_FORCE,
    NAN_TLV_TYPE_RANDOM_UPDATE_TIME = 4124,
    NAN_TLV_TYPE_EARLY_WAKEUP,
    NAN_TLV_TYPE_PERIODIC_SCAN_INTERVAL,
    NAN_TLV_TYPE_DW_INTERVAL = 4128,
    NAN_TLV_TYPE_DB_INTERVAL,
    NAN_TLV_TYPE_FURTHER_AVAILABILITY,
    NAN_TLV_TYPE_24G_CHANNEL,
    NAN_TLV_TYPE_5G_CHANNEL,
    NAN_TLV_TYPE_DISC_MAC_ADDR_RANDOM_INTERVAL,
    NAN_TLV_TYPE_RANGING_AUTO_RESPONSE_CFG = 4134,
    NAN_TLV_TYPE_SUBSCRIBE_SID_BEACON = 4135,
    NAN_TLV_TYPE_CONFIG_LAST = 8191,

    /* Attributes types */
    NAN_TLV_TYPE_ATTRS_FIRST = 8192,
    NAN_TLV_TYPE_AVAILABILITY_INTERVALS_MAP = NAN_TLV_TYPE_ATTRS_FIRST,
    NAN_TLV_TYPE_WLAN_MESH_ID,
    NAN_TLV_TYPE_MAC_ADDRESS,
    NAN_TLV_TYPE_RECEIVED_RSSI_VALUE,
    NAN_TLV_TYPE_CLUSTER_ATTRIBUTE,
    NAN_TLV_TYPE_WLAN_INFRA_SSID,
    NAN_TLV_TYPE_ATTRS_LAST = 12287,

    /* Events Type */
    NAN_TLV_TYPE_EVENTS_FIRST = 12288,
    NAN_TLV_TYPE_EVENT_SELF_STATION_MAC_ADDRESS = NAN_TLV_TYPE_EVENTS_FIRST,
    NAN_TLV_TYPE_EVENT_STARTED_CLUSTER,
    NAN_TLV_TYPE_EVENT_JOINED_CLUSTER,
    NAN_TLV_TYPE_EVENT_CLUSTER_SCAN_RESULTS,
    NAN_TLV_TYPE_FAW_MEM_AVAIL,
    NAN_TLV_TYPE_EVENTS_LAST = 16383,

    /* TCA types */
    NAN_TLV_TYPE_TCA_FIRST = 16384,
    NAN_TLV_TYPE_CLUSTER_SIZE_REQ = NAN_TLV_TYPE_TCA_FIRST,
    NAN_TLV_TYPE_CLUSTER_SIZE_RSP,
    NAN_TLV_TYPE_TCA_LAST = 32767,

    /* Statistics types */
    NAN_TLV_TYPE_STATS_FIRST = 32768,
    NAN_TLV_TYPE_DE_PUBLISH_STATS = NAN_TLV_TYPE_STATS_FIRST,
    NAN_TLV_TYPE_DE_SUBSCRIBE_STATS,
    NAN_TLV_TYPE_DE_MAC_STATS,
    NAN_TLV_TYPE_DE_TIMING_SYNC_STATS,
    NAN_TLV_TYPE_DE_DW_STATS,
    NAN_TLV_TYPE_DE_STATS,
    NAN_TLV_TYPE_STATS_LAST = 36863,

    /* Testmode types */
    NAN_TLV_TYPE_TESTMODE_FIRST = 36864,
    NAN_TLV_TYPE_TESTMODE_GENERIC_CMD = NAN_TLV_TYPE_TESTMODE_FIRST,
    NAN_TLV_TYPE_TESTMODE_LAST = 37000,

    NAN_TLV_TYPE_LAST = 65535
} NanTlvType;

/* 8-byte control message header used by NAN*/
typedef struct PACKED
{
   u16 msgVersion:4;
   u16 msgId:12;
   u16 msgLen;
   u16 handle;
   u16 transactionId;
} NanMsgHeader, *pNanMsgHeader;

/* Enumeration for Version */
typedef enum
{
   NAN_MSG_VERSION1 = 1,
}NanMsgVersion;

typedef struct PACKED
{
    u16 type;
    u16 length;
    u8* value;
} NanTlv, *pNanTlv;

#define SIZEOF_TLV_HDR (sizeof(NanTlv::type) + sizeof(NanTlv::length))
/* NAN TLV Groups and Types */
typedef enum
{
    NAN_TLV_GROUP_FIRST = 0,
    NAN_TLV_GROUP_SDF = NAN_TLV_GROUP_FIRST,
    NAN_TLV_GROUP_CONFIG,
    NAN_TLV_GROUP_STATS,
    NAN_TLV_GROUP_ATTRS,
    NAN_TLV_NUM_GROUPS,
    NAN_TLV_GROUP_LAST = NAN_TLV_NUM_GROUPS
} NanTlvGroup;

/* NAN Miscellaneous Constants */
#define NAN_TTL_INFINITE            0
#define NAN_REPLY_COUNT_INFINITE    0

/* NAN Confguration 5G Channel Access Bit */
#define NAN_5G_CHANNEL_ACCESS_UNSUPPORTED   0
#define NAN_5G_CHANNEL_ACCESS_SUPPORTED     1

/* NAN Configuration Service IDs Enclosure Bit */
#define NAN_SIDS_NOT_ENCLOSED_IN_BEACONS    0
#define NAN_SIBS_ENCLOSED_IN_BEACONS        1

/* NAN Configuration Priority */
#define NAN_CFG_PRIORITY_SERVICE_DISCOVERY  0
#define NAN_CFG_PRIORITY_DATA_CONNECTION    1

/* NAN Configuration 5G Channel Usage */
#define NAN_5G_CHANNEL_USAGE_SYNC_AND_DISCOVERY 0
#define NAN_5G_CHANNEL_USAGE_DISCOVERY_ONLY     1

/* NAN Configuration TX_Beacon Content */
#define NAN_TX_BEACON_CONTENT_OLD_AM_INFO       0
#define NAN_TX_BEACON_CONTENT_UPDATED_AM_INFO   1

/* NAN Configuration Miscellaneous Constants */
#define NAN_MAC_INTERFACE_PERIODICITY_MIN   30
#define NAN_MAC_INTERFACE_PERIODICITY_MAX   255

#define NAN_DW_RANDOM_TIME_MIN  120
#define NAN_DW_RANDOM_TIME_MAX  240

#define NAN_INITIAL_SCAN_MIN_IDEAL_PERIOD   200
#define NAN_INITIAL_SCAN_MAX_IDEAL_PERIOD   300

#define NAN_ONGOING_SCAN_MIN_PERIOD 10
#define NAN_ONGOING_SCAN_MAX_PERIOD 30

#define NAN_HOP_COUNT_LIMIT 5

#define NAN_WINDOW_DW   0
#define NAN_WINDOW_FAW  1

/* NAN Error Rsp */
typedef struct PACKED
{
    NanMsgHeader fwHeader;
    u16 status;
    u16 value;
} NanErrorRspMsg, *pNanErrorRspMsg;

//* NAN Publish Service Req */
typedef struct PACKED
{
    u16 ttl;
    u16 period;
    u32 replyIndFlag:1;
    u32 publishType:2;
    u32 txType:1;
    u32 rssiThresholdFlag:1;
    u32 ota_flag:1;
    u32 matchAlg:2;
    u32 count:8;
    u32 connmap:8;
    u32 pubTerminatedIndDisableFlag:1;
    u32 pubMatchExpiredIndDisableFlag:1;
    u32 followupRxIndDisableFlag:1;
    u32 reserved2:5;
    /*
     * Excludes TLVs
     *
     * Required: Service Name,
     * Optional: Tx Match Filter, Rx Match Filter, Service Specific Info,
     */
} NanPublishServiceReqParams, *pNanPublishServiceReqParams;

typedef struct PACKED
{
    NanMsgHeader fwHeader;
    NanPublishServiceReqParams publishServiceReqParams;
    u8 ptlv[];
} NanPublishServiceReqMsg, *pNanPublishServiceReqMsg;

/* NAN Publish Service Rsp */
typedef struct PACKED
{
    NanMsgHeader fwHeader;
    /* status of the request */
    u16 status;
    u16 value;
} NanPublishServiceRspMsg, *pNanPublishServiceRspMsg;

/* NAN Publish Service Cancel Req */
typedef struct PACKED
{
    NanMsgHeader fwHeader;
} NanPublishServiceCancelReqMsg, *pNanPublishServiceCancelReqMsg;

/* NAN Publish Service Cancel Rsp */
typedef struct PACKED
{
    NanMsgHeader fwHeader;
    /* status of the request */
    u16 status;
    u16 value;
} NanPublishServiceCancelRspMsg, *pNanPublishServiceCancelRspMsg;

/* NAN Publish Terminated Ind */
typedef struct PACKED
{
    NanMsgHeader fwHeader;
    /* reason for the termination */
    u16 reason;
    u16 reserved;
} NanPublishTerminatedIndMsg, *pNanPublishTerminatedIndMsg;

/* Params for NAN Publish Replied Ind */
typedef struct PACKED
{
  u32  matchHandle;
} NanPublishRepliedIndParams;

/* NAN Publish Replied Ind */
typedef struct PACKED
{
    NanMsgHeader fwHeader;
    NanPublishRepliedIndParams publishRepliedIndParams;
    /*
     * Excludes TLVs
     *
     * Required: MAC Address
     * Optional: Received RSSI Value
     *
     */
    u8 ptlv[];
} NanPublishRepliedIndMsg, *pNanPublishRepliedIndMsg;

/* NAN Subscribe Service Req */
typedef struct PACKED
{
    u16 ttl;
    u16 period;
    u32 subscribeType:1;
    u32 srfAttr:1;
    u32 srfInclude:1;
    u32 srfSend:1;
    u32 ssiRequired:1;
    u32 matchAlg:2;
    u32 xbit:1;
    u32 count:8;
    u32 rssiThresholdFlag:1;
    u32 ota_flag:1;
    u32 subTerminatedIndDisableFlag:1;
    u32 subMatchExpiredIndDisableFlag:1;
    u32 followupRxIndDisableFlag:1;
    u32 reserved:3;
    u32 connmap:8;
    /*
     * Excludes TLVs
     *
     * Required: Service Name
     * Optional: Rx Match Filter, Tx Match Filter, Service Specific Info,
     */
} NanSubscribeServiceReqParams, *pNanSubscribeServiceReqParams;

typedef struct PACKED
{
    NanMsgHeader fwHeader;
    NanSubscribeServiceReqParams subscribeServiceReqParams;
    u8 ptlv[];
} NanSubscribeServiceReqMsg, *pNanSubscribeServiceReqMsg;

/* NAN Subscribe Service Rsp */
typedef struct PACKED
{
    NanMsgHeader fwHeader;
    /* status of the request */
    u16 status;
    u16 value;
} NanSubscribeServiceRspMsg, *pNanSubscribeServiceRspMsg;

/* NAN Subscribe Service Cancel Req */
typedef struct PACKED
{
    NanMsgHeader fwHeader;
} NanSubscribeServiceCancelReqMsg, *pNanSubscribeServiceCancelReqMsg;

/* NAN Subscribe Service Cancel Rsp */
typedef struct PACKED
{
    NanMsgHeader fwHeader;
    /* status of the request */
    u16 status;
    u16 value;
} NanSubscribeServiceCancelRspMsg, *pNanSubscribeServiceCancelRspMsg;

/* NAN Subscribe Match Ind */
typedef struct PACKED
{
    u32 matchHandle;
    u32 matchOccuredFlag:1;
    u32 outOfResourceFlag:1;
    u32 reserved:30;
} NanMatchIndParams;

typedef struct PACKED
{
    NanMsgHeader fwHeader;
    NanMatchIndParams matchIndParams;
    u8 ptlv[];
} NanMatchIndMsg, *pNanMatchIndMsg;

/* NAN Subscribe Unmatch Ind */
typedef struct PACKED
{
    u32 matchHandle;
} NanmatchExpiredIndParams;

typedef struct PACKED
{
    NanMsgHeader fwHeader;
    NanmatchExpiredIndParams matchExpiredIndParams;
} NanMatchExpiredIndMsg, *pNanMatchExpiredIndMsg;

/* NAN Subscribe Terminated Ind */
typedef struct PACKED
{
    NanMsgHeader fwHeader;
    /* reason for the termination */
    u16 reason;
    u16 reserved;
} NanSubscribeTerminatedIndMsg, *pNanSubscribeTerminatedIndMsg;

/* Event Ind */
typedef struct PACKED
{
    u32 eventId:8;
    u32 reserved:24;
} NanEventIndParams;

typedef struct PACKED
{
    NanMsgHeader fwHeader;
    u8 ptlv[];
} NanEventIndMsg, *pNanEventIndMsg;

/* NAN Transmit Followup Req */
typedef struct PACKED
{
    u32 matchHandle;
    u32 priority:4;
    u32 window:1;
    u32 followupTxRspDisableFlag:1;
    u32 reserved:26;
    /*
     * Excludes TLVs
     *
     * Required: Service Specific Info or Extended Service Specific Info
     */
} NanTransmitFollowupReqParams;

typedef struct PACKED
{
    NanMsgHeader fwHeader;
    NanTransmitFollowupReqParams transmitFollowupReqParams;
    u8 ptlv[];
} NanTransmitFollowupReqMsg, *pNanTransmitFollowupReqMsg;

/* NAN Transmit Followup Rsp */
typedef struct PACKED
{
    NanMsgHeader fwHeader;
    /* status of the request */
    u16 status;
    u16 value;
} NanTransmitFollowupRspMsg, *pNanTransmitFollowupRspMsg;

/* NAN Publish Followup Ind */
typedef struct PACKED
{
    u32 matchHandle;
    u32 window:1;
    u32 reserved:31;
    /*
     * Excludes TLVs
     *
     * Required: Service Specific Info or Extended Service Specific Info
     */
} NanFollowupIndParams;

typedef struct PACKED
{
    NanMsgHeader fwHeader;
    NanFollowupIndParams followupIndParams;
    u8 ptlv[];
} NanFollowupIndMsg, *pNanFollowupIndMsg;

/* NAN Statistics Req */
typedef struct PACKED
{
    u32 statsType:8;
    u32 clear:1;
    u32 reserved:23;
} NanStatsReqParams, *pNanStatsReqParams;

typedef struct PACKED
{
    NanMsgHeader fwHeader;
    NanStatsReqParams statsReqParams;
} NanStatsReqMsg, *pNanStatsReqMsg;

/* NAN Statistics Rsp */
typedef struct PACKED
{
    /* status of the request */
    u16 status;
    u16 value;
    u8 statsType;
    u8 reserved;
} NanStatsRspParams, *pNanStatsRspParams;

typedef struct PACKED
{
    NanMsgHeader fwHeader;
    NanStatsRspParams statsRspParams;
    u8 ptlv[];
} NanStatsRspMsg, *pNanStatsRspMsg;

typedef struct PACKED
{
    u8 count:7;
    u8 s:1;
} NanSidAttr, *pSidAttr;


/* NAN Configuration Req */
typedef struct PACKED
{
    NanMsgHeader fwHeader;
    /*
     * TLVs:
     *
     * Required: None.
     * Optional: SID, Random Time, Master Preference, WLAN Intra Attr,
     *           P2P Operation Attr, WLAN IBSS Attr, WLAN Mesh Attr
     */
    u8 ptlv[];
} NanConfigurationReqMsg, *pNanConfigurationReqMsg;

/*
 * Because the Configuration Req message has TLVs in it use the macro below
 * for the size argument to buffer allocation functions (vs. sizeof(msg)).
 */
#define NAN_MAX_CONFIGURATION_REQ_SIZE                       \
    (                                                        \
        sizeof(NanMsgHeader)                             +   \
        SIZEOF_TLV_HDR + sizeof(u8)  /* SID Beacon    */ +   \
        SIZEOF_TLV_HDR + sizeof(u8)  /* Random Time   */ +   \
        SIZEOF_TLV_HDR + sizeof(u8)  /* Master Pref   */     \
    )

/* NAN Configuration Rsp */
typedef struct PACKED
{
    NanMsgHeader fwHeader;
    /* status of the request */
    u16 status;
    u16 value;
} NanConfigurationRspMsg, *pNanConfigurationRspMsg;

/*
 * Because the Enable Req message has TLVs in it use the macro below for
 * the size argument to buffer allocation functions (vs. sizeof(msg)).
 */
#define NAN_MAX_ENABLE_REQ_SIZE                                 \
    (                                                           \
        sizeof(NanMsgHeader)                                +   \
        SIZEOF_TLV_HDR + sizeof(u16) /* Cluster Low   */    +   \
        SIZEOF_TLV_HDR + sizeof(u16) /* Cluster High  */    +   \
        SIZEOF_TLV_HDR + sizeof(u8)  /* Master Pref   */        \
    )

/* Config Discovery Indication */
 typedef struct PACKED
 {
    u32 disableDiscoveryMacAddressEvent:1;
    u32 disableDiscoveryStartedClusterEvent:1;
    u32 disableDiscoveryJoinedClusterEvent:1;
    u32 reserved:29;
 } NanConfigDiscoveryIndications;

/* NAN Enable Req */
typedef struct PACKED
{
    NanMsgHeader fwHeader;
    /*
     * TLVs:
     *
     * Required: Cluster Low, Cluster High, Master Preference,
     * Optional: 5G Support, SID, 5G Sync Disc, RSSI Close, RSSI Medium,
     *           Hop Count Limit, Random Time, Master Preference,
     *           WLAN Intra Attr, P2P Operation Attr, WLAN IBSS Attr,
     *           WLAN Mesh Attr
     */
    u8 ptlv[];
} NanEnableReqMsg, *pNanEnableReqMsg;

/* NAN Enable Rsp */
typedef struct PACKED
{
    NanMsgHeader fwHeader;
    /* status of the request */
    u16 status;
    u16 value;
} NanEnableRspMsg, *pNanEnableRspMsg;

/* NAN Disable Req */
typedef struct PACKED
{
    NanMsgHeader fwHeader;
} NanDisableReqMsg, *pNanDisableReqMsg;

/* NAN Disable Rsp */
typedef struct PACKED
{
    NanMsgHeader fwHeader;
    /* status of the request */
    u16 status;
    u16 reserved;
} NanDisableRspMsg, *pNanDisableRspMsg;

/* NAN Disable Ind */
typedef struct PACKED
{
    NanMsgHeader fwHeader;
    /* reason for the termination */
    u16 reason;
    u16 reserved;
} NanDisableIndMsg, *pNanDisableIndMsg;

typedef struct PACKED
{
    NanMsgHeader fwHeader;
    u8 ptlv[];
} NanTcaReqMsg, *pNanTcaReqMsg;

/* NAN TCA Rsp */
typedef struct PACKED
{
    NanMsgHeader   fwHeader;
    /* status of the request */
    u16 status;
    u16 value;
} NanTcaRspMsg, *pNanTcaRspMsg;

typedef struct PACKED
{
    NanMsgHeader fwHeader;
    /*
     * TLVs:
     *
     * Optional: Cluster size.
     */
    u8 ptlv[];
} NanTcaIndMsg, *pNanTcaIndMsg;

/*
 * Because the TCA Ind message has TLVs in it use the macro below for the
 * size argument to buffer allocation functions (vs. sizeof(msg)).
 */
#define NAN_MAX_TCA_IND_SIZE                                 \
    (                                                        \
        sizeof(NanMsgHeader)                             +   \
        sizeof(NanTcaIndParams)                          +   \
        SIZEOF_TLV_HDR + sizeof(u16) /* Cluster Size */      \
    )

/* Function Declarations */
u8* addTlv(u16 type, u16 length, const u8* value, u8* pOutTlv);
u16 NANTLV_ReadTlv(u8 *pInTlv, pNanTlv pOutTlv);
u16 NANTLV_WriteTlv(pNanTlv pInTlv, u8 *pOutTlv);

/* NAN Beacon Sdf Payload Req */
typedef struct PACKED
{
    NanMsgHeader fwHeader;
    /*
     * TLVs:
     *
     * Optional: Vendor specific attribute
     */
    u8 ptlv[];
} NanBeaconSdfPayloadReqMsg, *pNanBeaconSdfPayloadReqMsg;

/* NAN Beacon Sdf Payload Rsp */
typedef struct PACKED
{
    NanMsgHeader   fwHeader;
    /* status of the request */
    u16 status;
    u16 reserved;
} NanBeaconSdfPayloadRspMsg, *pNanBeaconSdfPayloadRspMsg;

/* NAN Beacon Sdf Payload Ind */
typedef struct PACKED
{
    NanMsgHeader fwHeader;
    /*
     * TLVs:
     *
     * Required: Mac address
     * Optional: Vendor specific attribute, sdf payload
     * receive
     */
    u8 ptlv[];
} NanBeaconSdfPayloadIndMsg, *pNanBeaconSdfPayloadIndMsg;

typedef struct PACKED
{
    u8 availIntDuration:2;
    u8 mapId:4;
    u8 reserved:2;
} NanApiEntryCtrl;

/*
 * Valid Operating Classes were derived from IEEE Std. 802.11-2012 Annex E
 * Table E-4 Global Operating Classe and, filtered by channel, are: 81, 83,
 * 84, 103, 114, 115, 116, 124, 125.
 */
typedef struct PACKED
{
    NanApiEntryCtrl entryCtrl;
    u8 opClass;
    u8 channel;
    u8 availIntBitmap[4];
} NanFurtherAvailabilityChan, *pNanFurtherAvailabilityChan;

typedef struct PACKED
{
    u8 numChan;
    u8 pFaChan[];
} NanFurtherAvailabilityMapAttrTlv, *pNanFurtherAvailabilityMapAttrTlv;

/* Publish statistics. */
typedef struct PACKED
{
    u32 validPublishServiceReqMsgs;
    u32 validPublishServiceRspMsgs;
    u32 validPublishServiceCancelReqMsgs;
    u32 validPublishServiceCancelRspMsgs;
    u32 validPublishRepliedIndMsgs;
    u32 validPublishTerminatedIndMsgs;
    u32 validActiveSubscribes;
    u32 validMatches;
    u32 validFollowups;
    u32 invalidPublishServiceReqMsgs;
    u32 invalidPublishServiceCancelReqMsgs;
    u32 invalidActiveSubscribes;
    u32 invalidMatches;
    u32 invalidFollowups;
    u32 publishCount;
    u32 publishNewMatchCount;
    u32 pubsubGlobalNewMatchCount;
} FwNanPublishStats, *pFwNanPublishStats;

/* Subscribe statistics. */
typedef struct PACKED
{
    u32 validSubscribeServiceReqMsgs;
    u32 validSubscribeServiceRspMsgs;
    u32 validSubscribeServiceCancelReqMsgs;
    u32 validSubscribeServiceCancelRspMsgs;
    u32 validSubscribeTerminatedIndMsgs;
    u32 validSubscribeMatchIndMsgs;
    u32 validSubscribeUnmatchIndMsgs;
    u32 validSolicitedPublishes;
    u32 validMatches;
    u32 validFollowups;
    u32 invalidSubscribeServiceReqMsgs;
    u32 invalidSubscribeServiceCancelReqMsgs;
    u32 invalidSubscribeFollowupReqMsgs;
    u32 invalidSolicitedPublishes;
    u32 invalidMatches;
    u32 invalidFollowups;
    u32 subscribeCount;
    u32 bloomFilterIndex;
    u32 subscribeNewMatchCount;
    u32 pubsubGlobalNewMatchCount;
} FwNanSubscribeStats, *pFwNanSubscribeStats;

/* NAN MAC Statistics. Used for MAC and DW statistics. */
typedef struct PACKED
{
    /* RX stats */
    u32 validFrames;
    u32 validActionFrames;
    u32 validBeaconFrames;
    u32 ignoredActionFrames;
    u32 ignoredBeaconFrames;
    u32 invalidFrames;
    u32 invalidActionFrames;
    u32 invalidBeaconFrames;
    u32 invalidMacHeaders;
    u32 invalidPafHeaders;
    u32 nonNanBeaconFrames;

    u32 earlyActionFrames;
    u32 inDwActionFrames;
    u32 lateActionFrames;

    /* TX stats */
    u32 framesQueued;
    u32 totalTRSpUpdates;
    u32 completeByTRSp;
    u32 completeByTp75DW;
    u32 completeByTendDW;
    u32 lateActionFramesTx;

    /* Misc stats - ignored for DW. */
    u32 twIncreases;
    u32 twDecreases;
    u32 twChanges;
    u32 twHighwater;
    u32 bloomFilterIndex;
} FwNanMacStats, *pFwNanMacStats;

/* NAN Sync and DW Statistics*/
typedef struct PACKED
{
    u64 currTsf;
    u64 myRank;
    u64 currAmRank;
    u64 lastAmRank;
    u32 currAmBTT;
    u32 lastAmBTT;
    u8  currAmHopCount;
    u8  currRole;
    u16 currClusterId;
    u32 reserved1;

    u64 timeSpentInCurrRole;
    u64 totalTimeSpentAsMaster;
    u64 totalTimeSpentAsNonMasterSync;
    u64 totalTimeSpentAsNonMasterNonSync;
    u32 transitionsToAnchorMaster;
    u32 transitionsToMaster;
    u32 transitionsToNonMasterSync;
    u32 transitionsToNonMasterNonSync;
    u32 amrUpdateCount;
    u32 amrUpdateRankChangedCount;
    u32 amrUpdateBTTChangedCount;
    u32 amrUpdateHcChangedCount;
    u32 amrUpdateNewDeviceCount;
    u32 amrExpireCount;
    u32 mergeCount;
    u32 beaconsAboveHcLimit;
    u32 beaconsBelowRssiThresh;
    u32 beaconsIgnoredNoSpace;
    u32 beaconsForOurCluster;
    u32 beaconsForOtherCluster;
    u32 beaconCancelRequests;
    u32 beaconCancelFailures;
    u32 beaconUpdateRequests;
    u32 beaconUpdateFailures;
    u32 syncBeaconTxAttempts;
    u32 syncBeaconTxFailures;
    u32 discBeaconTxAttempts;
    u32 discBeaconTxFailures;
    u32 amHopCountExpireCount;
    u32 ndpChannelFreq;
    u32 ndpChannelFreq2;
} FwNanSyncStats, *pFwNanSyncStats;

/* NAN Misc DE Statistics */
typedef struct PACKED
{
    u32 validErrorRspMsgs;
    u32 validTransmitFollowupReqMsgs;
    u32 validTransmitFollowupRspMsgs;
    u32 validFollowupIndMsgs;
    u32 validConfigurationReqMsgs;
    u32 validConfigurationRspMsgs;
    u32 validStatsReqMsgs;
    u32 validStatsRspMsgs;
    u32 validEnableReqMsgs;
    u32 validEnableRspMsgs;
    u32 validDisableReqMsgs;
    u32 validDisableRspMsgs;
    u32 validDisableIndMsgs;
    u32 validEventIndMsgs;
    u32 validTcaReqMsgs;
    u32 validTcaRspMsgs;
    u32 validTcaIndMsgs;
    u32 invalidTransmitFollowupReqMsgs;
    u32 invalidConfigurationReqMsgs;
    u32 invalidStatsReqMsgs;
    u32 invalidEnableReqMsgs;
    u32 invalidDisableReqMsgs;
    u32 invalidTcaReqMsgs;
} FwNanDeStats, *pFwNanDeStats;

/*
  Definition of various NanIndication(events)
*/
typedef enum {
    NAN_INDICATION_PUBLISH_REPLIED         =0,
    NAN_INDICATION_PUBLISH_TERMINATED      =1,
    NAN_INDICATION_MATCH                   =2,
    NAN_INDICATION_MATCH_EXPIRED           =3,
    NAN_INDICATION_SUBSCRIBE_TERMINATED    =4,
    NAN_INDICATION_DE_EVENT                =5,
    NAN_INDICATION_FOLLOWUP                =6,
    NAN_INDICATION_DISABLED                =7,
    NAN_INDICATION_TCA                     =8,
    NAN_INDICATION_BEACON_SDF_PAYLOAD      =9,
    NAN_INDICATION_SELF_TRANSMIT_FOLLOWUP  =10,
    NAN_INDICATION_RANGING_REQUEST_RECEIVED =11,
    NAN_INDICATION_RANGING_RESULT           =12,
    NAN_INDICATION_UNKNOWN                 =0xFFFF
} NanIndicationType;

/* NAN Capabilities Req */
typedef struct PACKED
{
    NanMsgHeader fwHeader;
} NanCapabilitiesReqMsg, *pNanCapabilitiesReqMsg;

/* NAN Capabilities Rsp */
typedef struct PACKED
{
    NanMsgHeader fwHeader;
    /* status of the request */
    u32 status;
    u32 value;
    u32 max_concurrent_nan_clusters;
    u32 max_publishes;
    u32 max_subscribes;
    u32 max_service_name_len;
    u32 max_match_filter_len;
    u32 max_total_match_filter_len;
    u32 max_service_specific_info_len;
    u32 max_vsa_data_len;
    u32 max_mesh_data_len;
    u32 max_ndi_interfaces;
    u32 max_ndp_sessions;
    u32 max_app_info_len;
    u32 max_queued_transmit_followup_msgs;
    u32 ndp_supported_bands;
    u32 cipher_suites_supported;
    u32 max_scid_len;
    u32 is_ndp_security_supported:1;
    u32 max_sdea_service_specific_info_len:16;
    u32 reserved:15;
    u32 max_subscribe_address;
} NanCapabilitiesRspMsg, *pNanCapabilitiesRspMsg;

/* NAN Self Transmit Followup */
typedef struct PACKED
{
    NanMsgHeader fwHeader;
    u32 reason;
} NanSelfTransmitFollowupIndMsg, *pNanSelfTransmitFollowupIndMsg;

/* NAN Cipher Suite Shared Key */
typedef struct PACKED
{
    u32 csid_type;
} NanCsidType;

/* Service Discovery Extended Attribute params */
typedef struct PACKED
{
    u32 fsd_required:1;
    u32 fsd_with_gas:1;
    u32 data_path_required:1;
    u32 data_path_type:1;
    u32 multicast_type:1;
    u32 qos_required:1;
    u32 security_required:1;
    u32 ranging_required:1;
    u32 range_limit_present:1;
    u32 range_report:1;
    u32 reserved:22;
} NanFWSdeaCtrlParams;

/* NAN Ranging Configuration params */
typedef struct PACKED
{
    u32  inner_threshold;
    u32  outer_threshold;
} NanFWGeoFenceDescriptor;

typedef struct PACKED
{
    u32 range_resolution;
    u32 range_interval;
    u32 ranging_indication_event;
    NanFWGeoFenceDescriptor geo_fence_threshold;
} NanFWRangeConfigParams;

typedef struct PACKED
{
    NanMsgHeader fwHeader;
    /*
      Excludes TLVs
      Optional: Nan Availability
    */
    u8 ptlv[];
} NanTestModeReqMsg, *pNanTestModeReqMsg;

/*
  NAN Status codes exchanged between firmware
  and WifiHal.
*/
typedef enum {
    /* NAN Protocol Response Codes */
    NAN_I_STATUS_SUCCESS = 0,
    NAN_I_STATUS_TIMEOUT = 1,
    NAN_I_STATUS_DE_FAILURE = 2,
    NAN_I_STATUS_INVALID_MSG_VERSION = 3,
    NAN_I_STATUS_INVALID_MSG_LEN = 4,
    NAN_I_STATUS_INVALID_MSG_ID = 5,
    NAN_I_STATUS_INVALID_HANDLE = 6,
    NAN_I_STATUS_NO_SPACE_AVAILABLE = 7,
    NAN_I_STATUS_INVALID_PUBLISH_TYPE = 8,
    NAN_I_STATUS_INVALID_TX_TYPE = 9,
    NAN_I_STATUS_INVALID_MATCH_ALGORITHM = 10,
    NAN_I_STATUS_DISABLE_IN_PROGRESS = 11,
    NAN_I_STATUS_INVALID_TLV_LEN = 12,
    NAN_I_STATUS_INVALID_TLV_TYPE = 13,
    NAN_I_STATUS_MISSING_TLV_TYPE = 14,
    NAN_I_STATUS_INVALID_TOTAL_TLVS_LEN = 15,
    NAN_I_STATUS_INVALID_REQUESTER_INSTANCE_ID= 16,
    NAN_I_STATUS_INVALID_TLV_VALUE = 17,
    NAN_I_STATUS_INVALID_TX_PRIORITY = 18,
    NAN_I_STATUS_INVALID_CONNECTION_MAP = 19,
    NAN_I_STATUS_INVALID_THRESHOLD_CROSSING_ALERT_ID = 20,
    NAN_I_STATUS_INVALID_STATS_ID = 21,
    NAN_I_STATUS_NAN_NOT_ALLOWED = 22,
    NAN_I_STATUS_NO_OTA_ACK = 23,
    NAN_I_STATUS_TX_FAIL = 24,
    NAN_I_STATUS_NAN_ALREADY_ENABLED = 25,
    NAN_I_STATUS_FOLLOWUP_QUEUE_FULL = 26,
    /* 27-4095 Reserved */
    /* NAN Configuration Response codes */
    NAN_I_STATUS_INVALID_RSSI_CLOSE_VALUE = 4096,
    NAN_I_STATUS_INVALID_RSSI_MIDDLE_VALUE = 4097,
    NAN_I_STATUS_INVALID_HOP_COUNT_LIMIT = 4098,
    NAN_I_STATUS_INVALID_MASTER_PREFERENCE_VALUE = 4099,
    NAN_I_STATUS_INVALID_LOW_CLUSTER_ID_VALUE = 4100,
    NAN_I_STATUS_INVALID_HIGH_CLUSTER_ID_VALUE = 4101,
    NAN_I_STATUS_INVALID_BACKGROUND_SCAN_PERIOD = 4102,
    NAN_I_STATUS_INVALID_RSSI_PROXIMITY_VALUE = 4103,
    NAN_I_STATUS_INVALID_SCAN_CHANNEL = 4104,
    NAN_I_STATUS_INVALID_POST_NAN_CONNECTIVITY_CAPABILITIES_BITMAP = 4105,
    NAN_I_STATUS_INVALID_FURTHER_AVAILABILITY_MAP_NUMCHAN_VALUE = 4106,
    NAN_I_STATUS_INVALID_FURTHER_AVAILABILITY_MAP_DURATION_VALUE = 4107,
    NAN_I_STATUS_INVALID_FURTHER_AVAILABILITY_MAP_CLASS_VALUE = 4108,
    NAN_I_STATUS_INVALID_FURTHER_AVAILABILITY_MAP_CHANNEL_VALUE = 4109,
    NAN_I_STATUS_INVALID_FURTHER_AVAILABILITY_MAP_AVAILABILITY_INTERVAL_BITMAP_VALUE = 4110,
    NAN_I_STATUS_INVALID_FURTHER_AVAILABILITY_MAP_MAP_ID = 4111,
    NAN_I_STATUS_INVALID_POST_NAN_DISCOVERY_CONN_TYPE_VALUE = 4112,
    NAN_I_STATUS_INVALID_POST_NAN_DISCOVERY_DEVICE_ROLE_VALUE = 4113,
    NAN_I_STATUS_INVALID_POST_NAN_DISCOVERY_DURATION_VALUE = 4114,
    NAN_I_STATUS_INVALID_POST_NAN_DISCOVERY_BITMAP_VALUE = 4115,
    NAN_I_STATUS_MISSING_FUTHER_AVAILABILITY_MAP = 4116,
    NAN_I_STATUS_INVALID_BAND_CONFIG_FLAGS = 4117,
    NAN_I_STATUS_INVALID_RANDOM_FACTOR_UPDATE_TIME_VALUE = 4118,
    NAN_I_STATUS_INVALID_ONGOING_SCAN_PERIOD = 4119,
    NAN_I_STATUS_INVALID_DW_INTERVAL_VALUE = 4120,
    NAN_I_STATUS_INVALID_DB_INTERVAL_VALUE = 4121,
    /* 4122-8191 RESERVED */
    NAN_I_PUBLISH_SUBSCRIBE_TERMINATED_REASON_INVALID = 8192,
    NAN_I_PUBLISH_SUBSCRIBE_TERMINATED_REASON_TIMEOUT = 8193,
    NAN_I_PUBLISH_SUBSCRIBE_TERMINATED_REASON_USER_REQUEST = 8194,
    NAN_I_PUBLISH_SUBSCRIBE_TERMINATED_REASON_FAILURE = 8195,
    NAN_I_PUBLISH_SUBSCRIBE_TERMINATED_REASON_COUNT_REACHED = 8196,
    NAN_I_PUBLISH_SUBSCRIBE_TERMINATED_REASON_DE_SHUTDOWN = 8197,
    NAN_I_PUBLISH_SUBSCRIBE_TERMINATED_REASON_DISABLE_IN_PROGRESS = 8198,
    NAN_I_PUBLISH_SUBSCRIBE_TERMINATED_REASON_POST_DISC_ATTR_EXPIRED = 8199,
    NAN_I_PUBLISH_SUBSCRIBE_TERMINATED_REASON_POST_DISC_LEN_EXCEEDED = 8200,
    NAN_I_PUBLISH_SUBSCRIBE_TERMINATED_REASON_FURTHER_AVAIL_MAP_EMPTY = 8201,
    /* 9000-9500 NDP Status type */
    NDP_I_UNSUPPORTED_CONCURRENCY = 9000,
    NDP_I_NAN_DATA_IFACE_CREATE_FAILED = 9001,
    NDP_I_NAN_DATA_IFACE_DELETE_FAILED = 9002,
    NDP_I_DATA_INITIATOR_REQUEST_FAILED = 9003,
    NDP_I_DATA_RESPONDER_REQUEST_FAILED = 9004,
    NDP_I_INVALID_SERVICE_INSTANCE_ID = 9005,
    NDP_I_INVALID_NDP_INSTANCE_ID = 9006,
    NDP_I_INVALID_RESPONSE_CODE = 9007,
    NDP_I_INVALID_APP_INFO_LEN = 9008,
    /* OTA failures and timeouts during negotiation */
    NDP_I_MGMT_FRAME_REQUEST_FAILED = 9009,
    NDP_I_MGMT_FRAME_RESPONSE_FAILED = 9010,
    NDP_I_MGMT_FRAME_CONFIRM_FAILED = 9011,
    NDP_I_END_FAILED = 9012,
    NDP_I_MGMT_FRAME_END_REQUEST_FAILED = 9013,
    NDP_I_MGMT_FRAME_SECURITY_INSTALL_FAILED = 9014,

    /* 9500 onwards vendor specific error codes */
    NDP_I_VENDOR_SPECIFIC_ERROR = 9500
} NanInternalStatusType;

/* This is the TLV used for range report */
typedef struct PACKED
{
    u32 publish_id;
    u32 event_type;
    u32 range_measurement;
} NanFWRangeReportParams;

typedef struct PACKED
{
    NanMsgHeader fwHeader;
    /*TLV Required:
        MANDATORY
            1. MAC_ADDRESS
            2. NanFWRangeReportParams
        OPTIONAL:
            1. A_UINT32 event type
    */
    u8 ptlv[1];
} NanFWRangeReportInd, *pNanFWRangeReportInd;

/** 2 word representation of MAC addr */
typedef struct {
    /** upper 4 bytes of  MAC address */
    u32 mac_addr31to0;
    /** lower 2 bytes of  MAC address */
    u32 mac_addr47to32;
} fw_mac_addr;

/* This is the TLV used to trigger ranging requests*/
typedef struct PACKED
{
    fw_mac_addr  range_mac_addr;
    u32 range_id; //Match handle in match_ind, publish_id in result ind
    u32 ranging_accept:1;
    u32 ranging_reject:1;
    u32 ranging_cancel:1;
    u32 reserved:29;
} NanFWRangeReqMsg, *pNanFWRangeReqMsg;

typedef struct PACKED
{
    fw_mac_addr  range_mac_addr;
    u32 range_id;//This will publish_id in case of receiving publish.
} NanFWRangeReqRecvdMsg, *pNanFWRangeReqRecvdMsg;

typedef struct PACKED
{
    NanMsgHeader fwHeader;
    /*TLV Required
       1. t_nan_range_req_recvd_msg
    */
    u8 ptlv[1];
} NanFWRangeReqRecvdInd, *pNanFWRangeReqRecvdInd;

/* Function for NAN error translation
   For NanResponse, NanPublishTerminatedInd, NanSubscribeTerminatedInd,
   NanDisabledInd, NanTransmitFollowupInd:
   function to translate firmware specific errors
   to generic freamework error along with the error string
*/
void NanErrorTranslation(NanInternalStatusType firmwareErrorRecvd,
                         u32 valueRcvd,
                         void *pRsp,
                         bool is_ndp_rsp);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* __NAN_I_H__ */

