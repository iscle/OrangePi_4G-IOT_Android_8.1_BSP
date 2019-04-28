/*
 * Copyright (c) 2014 The Android Open Source Project
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.bluetooth.hfpclient;

/*
 * @hide
 */

final public class HeadsetClientHalConstants {
    // Do not modify without updating the HAL bt_hf_client.h files.

    // match up with bthf_client_connection_state_t enum of bt_hf_client.h
    final static int CONNECTION_STATE_DISCONNECTED = 0;
    final static int CONNECTION_STATE_CONNECTING = 1;
    final static int CONNECTION_STATE_CONNECTED = 2;
    final static int CONNECTION_STATE_SLC_CONNECTED = 3;
    final static int CONNECTION_STATE_DISCONNECTING = 4;

    // match up with bthf_client_audio_state_t enum of bt_hf_client.h
    final static int AUDIO_STATE_DISCONNECTED = 0;
    final static int AUDIO_STATE_CONNECTING = 1;
    final static int AUDIO_STATE_CONNECTED = 2;
    final static int AUDIO_STATE_CONNECTED_MSBC = 3;

    // match up with bthf_client_vr_state_t enum of bt_hf_client.h
    final static int VR_STATE_STOPPED = 0;
    final static int VR_STATE_STARTED = 1;

    // match up with bthf_client_volume_type_t enum of bt_hf_client.h
    final static int VOLUME_TYPE_SPK = 0;
    final static int VOLUME_TYPE_MIC = 1;

    // match up with bthf_client_network_state_t enum of bt_hf_client.h
    final static int NETWORK_STATE_NOT_AVAILABLE = 0;
    final static int NETWORK_STATE_AVAILABLE = 1;

    // match up with bthf_client_service_type_t enum of bt_hf_client.h
    final static int SERVICE_TYPE_HOME = 0;
    final static int SERVICE_TYPE_ROAMING = 1;

    // match up with bthf_client_call_state_t enum of bt_hf_client.h
    final static int CALL_STATE_ACTIVE = 0;
    final static int CALL_STATE_HELD = 1;
    final static int CALL_STATE_DIALING = 2;
    final static int CALL_STATE_ALERTING = 3;
    final static int CALL_STATE_INCOMING = 4;
    final static int CALL_STATE_WAITING = 5;
    final static int CALL_STATE_HELD_BY_RESP_HOLD = 6;

    // match up with bthf_client_call_t enum of bt_hf_client.h
    final static int CALL_NO_CALLS_IN_PROGRESS = 0;
    final static int CALL_CALLS_IN_PROGRESS = 1;

    // match up with bthf_client_callsetup_t enum of bt_hf_client.h
    final static int CALLSETUP_NONE = 0;
    final static int CALLSETUP_INCOMING = 1;
    final static int CALLSETUP_OUTGOING = 2;
    final static int CALLSETUP_ALERTING = 3;

    // match up with bthf_client_callheld_t enum of bt_hf_client.h
    final static int CALLHELD_NONE = 0;
    final static int CALLHELD_HOLD_AND_ACTIVE = 1;
    final static int CALLHELD_HOLD = 2;

    // match up with btrh_client_resp_and_hold_t of bt_hf_client.h
    final static int RESP_AND_HOLD_HELD = 0;
    final static int RESP_AND_HOLD_ACCEPT = 1;
    final static int RESP_AND_HOLD_REJECT = 2;

    // match up with bthf_client_call_direction_t enum of bt_hf_client.h
    final static int CALL_DIRECTION_OUTGOING = 0;
    final static int CALL_DIRECTION_INCOMING = 1;

    // match up with bthf_client_call_mpty_type_t enum of bt_hf_client.h
    final static int CALL_MPTY_TYPE_SINGLE = 0;
    final static int CALL_MPTY_TYPE_MULTI = 1;

    // match up with bthf_client_cmd_complete_t enum of bt_hf_client.h
    final static int CMD_COMPLETE_OK = 0;
    final static int CMD_COMPLETE_ERROR = 1;
    final static int CMD_COMPLETE_ERROR_NO_CARRIER = 2;
    final static int CMD_COMPLETE_ERROR_BUSY = 3;
    final static int CMD_COMPLETE_ERROR_NO_ANSWER = 4;
    final static int CMD_COMPLETE_ERROR_DELAYED = 5;
    final static int CMD_COMPLETE_ERROR_BLACKLISTED = 6;
    final static int CMD_COMPLETE_ERROR_CME = 7;

    // match up with bthf_client_call_action_t enum of bt_hf_client.h
    final static int CALL_ACTION_CHLD_0 = 0;
    final static int CALL_ACTION_CHLD_1 = 1;
    final static int CALL_ACTION_CHLD_2 = 2;
    final static int CALL_ACTION_CHLD_3 = 3;
    final static int CALL_ACTION_CHLD_4 = 4;
    final static int CALL_ACTION_CHLD_1x = 5;
    final static int CALL_ACTION_CHLD_2x = 6;
    final static int CALL_ACTION_ATA = 7;
    final static int CALL_ACTION_CHUP = 8;
    final static int CALL_ACTION_BTRH_0 = 9;
    final static int CALL_ACTION_BTRH_1 = 10;
    final static int CALL_ACTION_BTRH_2 = 11;

    // match up with bthf_client_subscriber_service_type_t enum of
    // bt_hf_client.h
    final static int SUBSCRIBER_SERVICE_TYPE_UNKNOWN = 0;
    final static int SUBSCRIBER_SERVICE_TYPE_VOICE = 1;
    final static int SUBSCRIBER_SERVICE_TYPE_FAX = 2;

    // match up with bthf_client_in_band_ring_state_t enum in bt_hf_client.h
    final static int IN_BAND_RING_NOT_PROVIDED = 0;
    final static int IN_BAND_RING_PROVIDED = 1;

    // AG features masks
    // match up with masks in bt_hf_client.h
    // Three-way calling
    final static int PEER_FEAT_3WAY     = 0x00000001;
    // Echo cancellation and/or noise reduction
    final static int PEER_FEAT_ECNR     = 0x00000002;
    // Voice recognition
    final static int PEER_FEAT_VREC     = 0x00000004;
    // In-band ring tone
    final static int PEER_FEAT_INBAND   = 0x00000008;
    // Attach a phone number to a voice tag
    final static int PEER_FEAT_VTAG     = 0x00000010;
    // Ability to reject incoming call
    final static int PEER_FEAT_REJECT   = 0x00000020;
    // Enhanced Call Status
    final static int PEER_FEAT_ECS      = 0x00000040;
    // Enhanced Call Control
    final static int PEER_FEAT_ECC      = 0x00000080;
    // Extended error codes
    final static int PEER_FEAT_EXTERR   = 0x00000100;
    // Codec Negotiation
    final static int PEER_FEAT_CODEC    = 0x00000200;

    // AG's 3WC features masks
    // match up with masks in bt_hf_client.h
    // 0  Release waiting call or held calls
    final static int CHLD_FEAT_REL           = 0x00000001;
    // 1  Release active calls and accept other (waiting or held) cal
    final static int CHLD_FEAT_REL_ACC       = 0x00000002;
    // 1x Release specified active call only
    final static int CHLD_FEAT_REL_X         = 0x00000004;
    // 2  Active calls on hold and accept other (waiting or held) call
    final static int CHLD_FEAT_HOLD_ACC      = 0x00000008;
    // 2x Request private mode with specified call (put the rest on hold)
    final static int CHLD_FEAT_PRIV_X        = 0x00000010;
    // 3  Add held call to multiparty */
    final static int CHLD_FEAT_MERGE         = 0x00000020;
    // 4  Connect two calls and leave (disconnect from) multiparty */
    final static int CHLD_FEAT_MERGE_DETACH  = 0x00000040;

    // AT Commands
    // These Commands values must match with Constants defined in
    // tBTA_HF_CLIENT_AT_CMD_TYPE in bta_hf_client_api.h
    // used for sending vendor specific AT cmds to AG.

    final static int HANDSFREECLIENT_AT_CMD_NREC = 15;

    // Flag to check for local NREC support
    final static boolean HANDSFREECLIENT_NREC_SUPPORTED = true;
}
