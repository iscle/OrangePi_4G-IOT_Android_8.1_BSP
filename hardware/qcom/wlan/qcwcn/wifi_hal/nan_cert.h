/*
 * Copyright (c) 2016-2017, The Linux Foundation. All rights reserved.

 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.

 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#ifndef __NAN_CERT_H__
#define __NAN_CERT_H__


#ifdef __cplusplus
extern "C"
{
#endif /* __cplusplus */

#define NAN_CERT_VERSION                        2
#define NAN_MAX_DEBUG_MESSAGE_DATA_LEN          100

typedef struct {
    /* NAN master rank being advertised by DE */
    u64 master_rank;
    /* NAN master preference being advertised by DE */
    u8 master_pref;
    /* random value being advertised by DE */
    u8 random_factor;
    /* hop_count from anchor master */
    u8 hop_count;
    u32 beacon_transmit_time;
    /* NDP channel Frequency */
    u32 ndp_channel_freq;
} NanStaParameter;

/* NAN Data Path Supported Band */
typedef enum {
    NAN_DATA_PATH_SUPPORTED_BAND_2G = 1,
    NAN_DATA_PATH_SUPPORTED_BAND_5G = 2,
    NAN_DATA_PATH_SUPPORT_DUAL_BAND = 3
} NdpSupportedBand;

/* NAN Responder mode policy */
typedef enum {
    NAN_DATA_RESPONDER_MODE_AUTO    = 0,
    NAN_DATA_RESPONDER_MODE_ACCEPT  = 1,
    NAN_DATA_RESPONDER_MODE_REJECT  = 2,
    NAN_DATA_RESPONDER_MODE_COUNTER = 3,
    NAN_DATA_RESPONDER_MODE_COUNTER_NO_CHANNEL_CHANGE = 4
} NanDataResponderMode;

/* NAN Data Path M4 response type */
typedef enum {
    NAN_DATA_PATH_M4_RESPONSE_ACCEPT = 1,
    NAN_DATA_PATH_M4_RESPONSE_REJECT = 2,
    NAN_DATA_PATH_M4_RESPONSE_BADMIC = 3
} NdpM4ResponseType;

/* NAN NMF Security Clear type */
typedef enum {
    NAN_NMF_CLEAR_DISABLE = 0,
    NAN_NMF_CLEAR_ENABLE = 1
} NanNmfClearConfig;

/* NAN Schedule type */
typedef enum {
    NAN_SCHED_VALID = 0,
    NAN_SCHED_INVALID_BAD_FA = 1,
    NAN_SCHED_INVALID_BAD_NDC = 2,
    NAN_SCHED_INVALID_BAD_IMMU = 3
} NanSchedType;

 /*
  * Definitions of debug subcommand type for the
  * generic debug command.
  */
typedef enum {
    NAN_TEST_MODE_CMD_NAN_AVAILABILITY = 1,
    NAN_TEST_MODE_CMD_NDP_INCLUDE_IMMUTABLE = 2,
    NAN_TEST_MODE_CMD_NDP_AVOID_CHANNEL = 3,
    NAN_TEST_MODE_CMD_NAN_SUPPORTED_BANDS = 4,
    NAN_TEST_MODE_CMD_AUTO_RESPONDER_MODE = 5,
    NAN_TEST_MODE_CMD_M4_RESPONSE_TYPE = 6,
    NAN_TEST_MODE_CMD_NAN_SCHED_TYPE = 7,
    NAN_TEST_MODE_CMD_NAN_NMF_CLEAR_CONFIG = 8,
    NAN_TEST_MODE_CMD_NAN_SCHED_UPDATE_ULW_NOTIFY = 9,
    NAN_TEST_MODE_CMD_NAN_SCHED_UPDATE_NDL_NEGOTIATE = 10,
    NAN_TEST_MODE_CMD_NAN_SCHED_UPDATE_NDL_NOTIFY = 11,
    NAN_TEST_MODE_CMD_NAN_AVAILABILITY_MAP_ORDER = 12
} NanDebugModeCmd;

/*
 * This debug command carries any one command type
 * followed by corresponding command data content
 * as indicated below.
 *
 * command: NAN_TEST_MODE_CMD_NAN_AVAILABILITY
 * content: NAN Avaiability attribute blob
 *
 * command: NAN_TEST_MODE_CMD_NDP_INCLUDE_IMMUTABLE
 * content: u32 value (0 - Ignore 1 - Include immuatable,
 *                     2 - Don't include immutable)
 *
 * command: NAN_TEST_MODE_CMD_NDP_AVOID_CHANNEL
 * content: u32 channel_frequency; (0 - Ignore)
 *
 * command: NAN_TEST_MODE_CMD_NAN_SUPPORTED_BANDS
 * content: u32 supported_bands; (0 . Ignore, 1 . 2g,
 *                                2 . 5g, 3 . 2g & 5g)
 *
 * command: NAN_TEST_MODE_CMD_AUTO_RESPONDER_MODE
 * content: u32 auto_resp_mode; (0 . Auto, 1 . Accept,
 *                               2 . Reject, 3 . Counter)
 *
 * command: NAN_TEST_MODE_CMD_M4_RESPONSE_TYPE
 * content: u32 m4_response_type; (0.Ignore, 1.Accept,
 *                                 2.Reject, 3.BadMic)
 *
 * command: NAN_TEST_MODE_CMD_NAN_SCHED_TYPE
 * content: u32 invalid_nan_schedule; (0. Valid sched,
 *                                     1.Invalid Sched bad FA,
 *                                     2.Invalid schedbad NDC,
 *                                     3.Invalid sched bad Immutable)
 *
 * command: NAN_TEST_MODE_CMD_NAN_NMF_CLEAR_CONFIG
 * content: u32 nmf_security_config_val;(0:NAN_NMF_CLEAR_DISABLE,
 *                                       1:NAN_NMF_CLEAR_ENABLE)
 *
 * command: NAN_TEST_MODE_CMD_NAN_SCHED_UPDATE_ULW_NOTIFY
 * content: u32 channel_availability;(0/1)
 *
 * command: NAN_TEST_MODE_CMD_NAN_SCHED_UPDATE_NDL_NEGOTIATE
 * content: responder_nmi_mac (Responder NMI Mac Address)
 *
 * command: NAN_TEST_MODE_CMD_NAN_SCHED_UPDATE_NDL_NOTIFY
 * content: NONE
 *
 * command: NAN_TEST_MODE_CMD_NAN_AVAILABILITY_MAP_ORDER
 * content: u32 map_order_val; (0/1)
 *
 */
typedef struct PACKED {
    /*
     * To indicate the debug command type.
     */
    u32 cmd;
    /*
     * To hold the data for the above command
     * type.
     */
    u8 debug_cmd_data[NAN_MAX_DEBUG_MESSAGE_DATA_LEN];
} NanDebugParams;

/*
   Function to get the sta_parameter expected by Sigma
   as per CAPI spec.
*/
wifi_error nan_get_sta_parameter(transaction_id id,
                                 wifi_interface_handle iface,
                                 NanStaParameter* msg);

wifi_error nan_debug_command_config(transaction_id id,
                                   wifi_interface_handle iface,
                                   NanDebugParams msg,
                                   int debug_msg_length);
#ifdef __cplusplus
}
#endif /* __cplusplus */
#endif /* __NAN_CERT_H__ */

