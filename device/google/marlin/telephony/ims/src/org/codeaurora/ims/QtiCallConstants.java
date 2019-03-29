/* Copyright (c) 2015, The Linux Foundation. All rights reserved.
 *
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
 *
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

package org.codeaurora.ims;

import com.android.ims.ImsReasonInfo;

/**
 * The class contains definitions for Qti specific constants related to any
 * value added features for video telephony.
 */
/**
 * @hide
 */
public class QtiCallConstants {

    /**
     * Call substate bitmask values
     */

    /* Default case */
    public static final int CALL_SUBSTATE_NONE = 0;

    /* Indicates that the call is connected but audio attribute is suspended */
    public static final int CALL_SUBSTATE_AUDIO_CONNECTED_SUSPENDED = 0x1;

    /* Indicates that the call is connected but video attribute is suspended */
    public static final int CALL_SUBSTATE_VIDEO_CONNECTED_SUSPENDED = 0x2;

    /* Indicates that the call is established but media retry is needed */
    public static final int CALL_SUBSTATE_AVP_RETRY = 0x4;

    /* Indicates that the call is multitasking */
    public static final int CALL_SUBSTATE_MEDIA_PAUSED = 0x8;

    /* Mask containing all the call substate bits set */
    public static final int CALL_SUBSTATE_ALL = CALL_SUBSTATE_AUDIO_CONNECTED_SUSPENDED |
        CALL_SUBSTATE_VIDEO_CONNECTED_SUSPENDED | CALL_SUBSTATE_AVP_RETRY |
        CALL_SUBSTATE_MEDIA_PAUSED;

    /* Call substate extra key name */
    public static final String CALL_SUBSTATE_EXTRA_KEY = "CallSubstate";

    /* Call encryption status extra key. The value will be a boolean. */
    public static final String CALL_ENCRYPTION_EXTRA_KEY = "CallEncryption";

    /* Call fail code extra key name */
    public static final String EXTRAS_KEY_CALL_FAIL_EXTRA_CODE  = "CallFailExtraCode";

    /* Call fail error code for handover not feasible */
    public static final int CALL_FAIL_EXTRA_CODE_LTE_3G_HA_FAILED = 149;

    /* Call fail error code for Retry CS call*/
    public static final int CALL_FAIL_EXTRA_CODE_CALL_CS_RETRY_REQUIRED =
            ImsReasonInfo.CODE_LOCAL_CALL_CS_RETRY_REQUIRED;

    /* Unknown disconnect cause */
    public static final int DISCONNECT_CAUSE_UNSPECIFIED = -1;

    /**
     * Whether the IMS to CS retry is enabled
     * <p>
     * Type: int (0 for false, 1 for true)
     * @hide
     */
    public static final String IMS_TO_CS_RETRY_ENABLED = "qti.settings.cs_retry";

    /* Default camera zoom max */
    public static final int CAMERA_MAX_ZOOM = 100;

    /**
     * Controls dial request route for CS calls.
     * 0 - Use the default routing strategy.
     * 1 - Place the call over CS path
     * 2 - Place the call over PS path
     */
    public static final String EXTRA_CALL_DOMAIN =
            "org.codeaurora.extra.CALL_DOMAIN";
    public static final int DOMAIN_AUTOMATIC = 0;
    public static final int DOMAIN_CS = 1;
    public static final int DOMAIN_PS = 2;

    /**
     * Call supplementary services failures.
     * TODO: Rename the file to QtiCallConstants.java as generic IMS constants are added. This
     *  will be handled when we move the file to vendor/codeaurora/telephony project.
     */
    public static final int ERROR_CALL_CODE_UNSPECIFIED = -1;
    public static final int ERROR_CALL_SUPP_SVC_FAILED = 1;
    public static final int ERROR_CALL_SUPP_SVC_CANCELLED = 2;
    public static final int ERROR_CALL_SUPP_SVC_REINVITE_COLLISION = 3;

    /**
     * Private constructor. This class should not be instantiated.
     */
    private QtiCallConstants() {
    }

    /* UI Orientation Modes */
    public static final int ORIENTATION_MODE_UNSPECIFIED = -1;
    public static final int ORIENTATION_MODE_LANDSCAPE = 1;
    public static final int ORIENTATION_MODE_PORTRAIT = 2;
    public static final int ORIENTATION_MODE_DYNAMIC = 3;

    /* Orientation mode extra key name */
    public static final String ORIENTATION_MODE_EXTRA_KEY = "OrientationMode";

    /* low battery extra key name that contains a boolean value,
       TRUE meaning battery is low else FALSE */
    public static final String LOW_BATTERY_EXTRA_KEY = "LowBattery";

   /* Upgrade/downgrade of a volte/vt call due to unknown reason. */
    public static final int CAUSE_CODE_UNSPECIFIED = 0;

    /* Upgrade of a volte call on request from local end */
    public static final int CAUSE_CODE_SESSION_MODIFY_UPGRADE_LOCAL_REQ = 1;

    /* Upgrade of a volte call on request from remote end */
    public static final int CAUSE_CODE_SESSION_MODIFY_UPGRADE_REMOTE_REQ = 2;

    /* Downgrade of a vt call on request from local end */
    public static final int CAUSE_CODE_SESSION_MODIFY_DOWNGRADE_LOCAL_REQ = 3;

    /* Downgrade of a vt call on request from remote end */
    public static final int CAUSE_CODE_SESSION_MODIFY_DOWNGRADE_REMOTE_REQ = 4;

    /* Downgrade of a vt call due to RTP/RTCP Timeout for Video stream */
    public static final int CAUSE_CODE_SESSION_MODIFY_DOWNGRADE_RTP_TIMEOUT = 5;

    /* Downgrade of a vt call due to QOS for Video stream */
    public static final int CAUSE_CODE_SESSION_MODIFY_DOWNGRADE_QOS = 6;

    /* Downgrade of a vt call due to PACKET LOSS for Video stream */
    public static final int CAUSE_CODE_SESSION_MODIFY_DOWNGRADE_PACKET_LOSS = 7;

    /* Downgrade of a vt call due to Low throughput for Video stream */
    public static final int CAUSE_CODE_SESSION_MODIFY_DOWNGRADE_LOW_THRPUT = 8;

    /* Downgrade of a vt call due to Thermal Mitigation */
    public static final int CAUSE_CODE_SESSION_MODIFY_DOWNGRADE_THERM_MITIGATION = 9;

    /* Downgrade of a vt call due to Lip-sync */
    public static final int CAUSE_CODE_SESSION_MODIFY_DOWNGRADE_LIPSYNC = 10;

    /* Downgrade of a vt call due to generic error */
    public static final int CAUSE_CODE_SESSION_MODIFY_DOWNGRADE_GENERIC_ERROR = 11;

    /* Upgrade/downgrade of a volte/vt call is rejected due to low battery */
    public static final int CAUSE_CODE_SESSION_MODIFY_REQUEST_LOW_BATTERY = 12;

    /* Session modification cause extra key name */
    public static final String SESSION_MODIFICATION_CAUSE_EXTRA_KEY = "SessionModificationCause";

    /**
     * Local device supports downgrade to voice
     */
    public static final int CAPABILITY_SUPPORTS_DOWNGRADE_TO_VOICE_LOCAL = 0x00800000;

    /**
      * Remote device supports downgrade to voice
      */
    public static final int CAPABILITY_SUPPORTS_DOWNGRADE_TO_VOICE_REMOTE = 0x01000000;

    /**
     * Add participant in an active or conference call option
     */
    public static final int CAPABILITY_ADD_PARTICIPANT = 0x02000000;
}

