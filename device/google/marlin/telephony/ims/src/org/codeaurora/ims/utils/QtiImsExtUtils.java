/**
 * Copyright (c) 2015, The Linux Foundation. All rights reserved.
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

package org.codeaurora.ims.utils;

import android.content.ContentResolver;
import android.content.Context;

/**
 * This class contains QtiImsExt specific utiltity functions.
 */
public class QtiImsExtUtils {

    private static String LOG_TAG = "QtiImsExtUtils";

    public static final String QTI_IMS_CALL_DEFLECT_NUMBER =
            "ims_call_deflect_number";

    /* Call deflect setting name */
    public static final String QTI_IMS_DEFLECT_ENABLED = "qti.ims.call_deflect";

    /* Default success value */
    public static final int QTI_IMS_REQUEST_SUCCESS = 0;

    /* Default error value */
    public static final int QTI_IMS_REQUEST_ERROR = 1;

    /* Call RAT extra key */
    public static final String QTI_IMS_CALL_RAT_EXTRA_KEY = "CallRadioTech";

    /**
     * Definitions for the call transfer type. For easier implementation,
     * the transfer type is defined as a bit mask value.
     */
    //Value representing blind call transfer type
    public static final int QTI_IMS_BLIND_TRANSFER = 0x01;
    //Value representing assured call transfer type
    public static final int QTI_IMS_ASSURED_TRANSFER = 0x02;
    //Value representing consultative call transfer type
    public static final int QTI_IMS_CONSULTATIVE_TRANSFER = 0x04;

    /* Call transfer extra key */
    public static final String QTI_IMS_TRANSFER_EXTRA_KEY = "transferType";

    /**
     * Private constructor for QtiImsExtUtils as we don't want to instantiate this class
     */
    private QtiImsExtUtils() {
    }

    /**
     * Retrieves the call deflection stored by the user
     * Returns stored number, or null otherwise.
     */
    public static String getCallDeflectNumber(ContentResolver contentResolver) {
        String deflectcall = android.provider.Settings.Global.getString(contentResolver,
                                     QTI_IMS_CALL_DEFLECT_NUMBER);

        /* Consider being null or empty as "Not Set" */
        if ((deflectcall != null) && (deflectcall.isEmpty())) {
            deflectcall = null;
        }

        return deflectcall;
    }

    /* Stores the call deflection provided by the user */
    public static void setCallDeflectNumber(ContentResolver contentResolver, String value) {
        String deflectNum = value;

        if (value == null || value.isEmpty()) {
            deflectNum = "";
        }

        android.provider.Settings.Global.putString(contentResolver,
                QTI_IMS_CALL_DEFLECT_NUMBER, deflectNum);
    }

    /***
     * Checks if the IMS call transfer property is enabled or not.
     * Returns true if enabled, or false otherwise.
     */
    public static boolean isCallTransferEnabled(Context context) {
        return false;
    }
}
