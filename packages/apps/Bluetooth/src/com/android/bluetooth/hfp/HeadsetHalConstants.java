/*
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

package com.android.bluetooth.hfp;

/*
 * @hide
 */

final public class HeadsetHalConstants {
    // Do not modify without upating the HAL bt_hf.h files.

    // match up with bthf_connection_state_t enum of bt_hf.h
    final static int CONNECTION_STATE_DISCONNECTED = 0;
    final static int CONNECTION_STATE_CONNECTING = 1;
    final static int CONNECTION_STATE_CONNECTED = 2;
    final static int CONNECTION_STATE_SLC_CONNECTED = 3;
    final static int CONNECTION_STATE_DISCONNECTING = 4;

    // match up with bthf_audio_state_t enum of bt_hf.h
    final static int AUDIO_STATE_DISCONNECTED = 0;
    final static int AUDIO_STATE_CONNECTING = 1;
    final static int AUDIO_STATE_CONNECTED = 2;
    final static int AUDIO_STATE_DISCONNECTING = 3;

    // match up with bthf_vr_state_t enum of bt_hf.h
    final static int VR_STATE_STOPPED = 0;
    final static int VR_STATE_STARTED = 1;

    // match up with bthf_volume_type_t enum of bt_hf.h
    final static int VOLUME_TYPE_SPK = 0;
    final static int VOLUME_TYPE_MIC = 1;

    // match up with bthf_network_state_t enum of bt_hf.h
    final static int NETWORK_STATE_NOT_AVAILABLE = 0;
    final static int NETWORK_STATE_AVAILABLE = 1;

    // match up with bthf_service_type_t enum of bt_hf.h
    final static int SERVICE_TYPE_HOME = 0;
    final static int SERVICE_TYPE_ROAMING = 1;

    // match up with bthf_at_response_t of bt_hf.h
    final static int AT_RESPONSE_ERROR = 0;
    final static int AT_RESPONSE_OK = 1;

    // match up with bthf_call_state_t of bt_hf.h
    final static int CALL_STATE_ACTIVE = 0;
    final static int CALL_STATE_HELD = 1;
    final static int CALL_STATE_DIALING = 2;
    final static int CALL_STATE_ALERTING = 3;
    final static int CALL_STATE_INCOMING = 4;
    final static int CALL_STATE_WAITING = 5;
    final static int CALL_STATE_IDLE = 6;

    // Match up with bthf_hf_ind_type_t of bt_hf.h
    final static int HF_INDICATOR_ENHANCED_DRIVER_SAFETY = 1;
    public final static int HF_INDICATOR_BATTERY_LEVEL_STATUS = 2;
}
