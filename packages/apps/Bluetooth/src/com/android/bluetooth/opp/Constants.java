/*
 * Copyright (c) 2008-2009, Motorola, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * - Neither the name of the Motorola, Inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.bluetooth.opp;

import java.io.IOException;
import java.util.regex.Pattern;

import javax.obex.HeaderSet;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.os.SystemProperties;

/**
 * Bluetooth OPP internal constants definition
 */
public class Constants {
    /** Tag used for debugging/logging */
    public static final String TAG = "BluetoothOpp";

    /**
     * The intent that gets sent when the service must wake up for a retry Note:
     * only retry Outbound transfer
     */
    public static final String ACTION_RETRY = "android.btopp.intent.action.RETRY";

    /** the intent that gets sent when clicking a successful transfer */
    public static final String ACTION_OPEN = "android.btopp.intent.action.OPEN";

    /** the intent that gets sent when clicking outbound transfer notification */
    public static final String ACTION_OPEN_OUTBOUND_TRANSFER = "android.btopp.intent.action.OPEN_OUTBOUND";

    /** the intent that gets sent when clicking a inbound transfer notification */
    public static final String ACTION_OPEN_INBOUND_TRANSFER = "android.btopp.intent.action.OPEN_INBOUND";

    /** the intent that gets sent from the Settings app to show the received files */
    public static final String ACTION_OPEN_RECEIVED_FILES = "android.btopp.intent.action.OPEN_RECEIVED_FILES";

    /** the intent that whitelists a remote bluetooth device for auto-receive confirmation (NFC) */
    public static final String ACTION_WHITELIST_DEVICE = "android.btopp.intent.action.WHITELIST_DEVICE";

    /** the intent that can be sent by handover requesters to stop a BTOPP transfer */
    public static final String ACTION_STOP_HANDOVER = "android.btopp.intent.action.STOP_HANDOVER_TRANSFER";

    /** the intent extra to show all received files in the transfer history */
    public static final String EXTRA_SHOW_ALL_FILES = "android.btopp.intent.extra.SHOW_ALL";

    /** the intent that gets sent when clicking an incomplete/failed transfer */
    public static final String ACTION_LIST = "android.btopp.intent.action.LIST";

    /** the intent that is used for initiating a handover transfer */
    public static final String ACTION_HANDOVER_SEND =
            "android.nfc.handover.intent.action.HANDOVER_SEND";

    /** the intent that is used for initiating a multi-uri handover transfer */
    public static final String ACTION_HANDOVER_SEND_MULTIPLE =
            "android.nfc.handover.intent.action.HANDOVER_SEND_MULTIPLE";

    /** the intent that is used for indicating an incoming transfer*/
    public static final String ACTION_HANDOVER_STARTED =
            "android.nfc.handover.intent.action.HANDOVER_STARTED";

    /** intent action used to indicate the progress of a handover transfer */
    public static final String ACTION_BT_OPP_TRANSFER_PROGRESS =
            "android.nfc.handover.intent.action.TRANSFER_PROGRESS";

    /** intent action used to indicate the completion of a handover transfer */
    public static final String ACTION_BT_OPP_TRANSFER_DONE =
            "android.nfc.handover.intent.action.TRANSFER_DONE";

    /** intent extra used to indicate the success of a handover transfer */
    public static final String EXTRA_BT_OPP_TRANSFER_STATUS =
            "android.nfc.handover.intent.extra.TRANSFER_STATUS";

    /** intent extra used to indicate the address associated with the transfer */
    public static final String EXTRA_BT_OPP_ADDRESS =
            "android.nfc.handover.intent.extra.ADDRESS";

    public static final String EXTRA_BT_OPP_OBJECT_COUNT =
            "android.nfc.handover.intent.extra.OBJECT_COUNT";

    public static final int COUNT_HEADER_UNAVAILABLE = -1;
    public static final int HANDOVER_TRANSFER_STATUS_SUCCESS = 0;

    public static final int HANDOVER_TRANSFER_STATUS_FAILURE = 1;

    /** intent extra used to indicate the direction of a handover transfer */
    public static final String EXTRA_BT_OPP_TRANSFER_DIRECTION =
            "android.nfc.handover.intent.extra.TRANSFER_DIRECTION";

    public static final int DIRECTION_BLUETOOTH_INCOMING = 0;

    public static final int DIRECTION_BLUETOOTH_OUTGOING = 1;

    /** intent extra used to provide a unique ID for the transfer */
    public static final String EXTRA_BT_OPP_TRANSFER_ID =
            "android.nfc.handover.intent.extra.TRANSFER_ID";

    /** intent extra used to provide progress of the transfer */
    public static final String EXTRA_BT_OPP_TRANSFER_PROGRESS =
            "android.nfc.handover.intent.extra.TRANSFER_PROGRESS";

    /** intent extra used to provide the Uri where the data was stored
     * by the handover transfer */
    public static final String EXTRA_BT_OPP_TRANSFER_URI =
            "android.nfc.handover.intent.extra.TRANSFER_URI";

    /** intent extra used to provide the mime-type of the data in
     *  the handover transfer */
    public static final String EXTRA_BT_OPP_TRANSFER_MIMETYPE =
            "android.nfc.handover.intent.extra.TRANSFER_MIME_TYPE";

    /** permission needed to be able to receive handover status requests */
    public static final String HANDOVER_STATUS_PERMISSION =
            "android.permission.NFC_HANDOVER_STATUS";

    /** intent extra that indicates this transfer is a handover from another
      * transport (NFC, WIFI)
      */
    public static final String EXTRA_CONNECTION_HANDOVER =
            "com.android.intent.extra.CONNECTION_HANDOVER";

    /**
     * the intent that gets sent when deleting the incoming file confirmation notification
     */
    public static final String ACTION_HIDE = "android.btopp.intent.action.HIDE";

    /**
     * the intent that gets sent when accepting the incoming file confirmation notification
     */
    public static final String ACTION_ACCEPT = "android.btopp.intent.action.ACCEPT";

    /**
     * the intent that gets sent when declining the incoming file confirmation notification
     */
    public static final String ACTION_DECLINE = "android.btopp.intent.action.DECLINE";

    /**
     * the intent that gets sent when deleting the notifications of outbound and
     * inbound completed transfer
     */
    public static final String ACTION_COMPLETE_HIDE = "android.btopp.intent.action.HIDE_COMPLETE";

    /**
     * the intent that gets sent when clicking a incoming file confirm
     * notification
     */
    public static final String ACTION_INCOMING_FILE_CONFIRM = "android.btopp.intent.action.CONFIRM";

    public static final String THIS_PACKAGE_NAME = "com.android.bluetooth";

    /**
     * The column that is used to remember whether the media scanner was invoked
     */
    public static final String MEDIA_SCANNED = "scanned";

    public static final int MEDIA_SCANNED_NOT_SCANNED = 0;

    public static final int MEDIA_SCANNED_SCANNED_OK = 1;

    public static final int MEDIA_SCANNED_SCANNED_FAILED = 2;

    /**
     * The MIME type(s) of we could accept from other device.
     * This is in essence a "white list" of acceptable types.
     * Today, restricted to images, audio, video and certain text types.
     */
    public static final String[] ACCEPTABLE_SHARE_INBOUND_TYPES = new String[] {
        "image/*",
        "video/*",
        "audio/*",
        "text/x-vcard",
        "text/x-vcalendar",
        "text/calendar",
        "text/plain",
        "text/html",
        "text/xml",
        "application/zip",
        "application/vnd.ms-excel",
        "application/msword",
        "application/vnd.ms-powerpoint",
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/x-hwp",
    };

    /**
     * The MIME type(s) of we could not accept from other device. TODO: define
     * correct type list
     */
    public static final String[] UNACCEPTABLE_SHARE_INBOUND_TYPES = new String[] {};

    /** Where we store Bluetooth received files on the external storage */
    public static final String DEFAULT_STORE_SUBDIR = "/bluetooth";

    /**
     * Debug level logging
     */
    public static final boolean DEBUG = true;

    /**
     * Verbose level logging
     */
    public static final boolean VERBOSE = !SystemProperties.get("ro.build.type", "").equals("user");

    /** use TCP socket instead of Rfcomm Socket to develop */
    public static final boolean USE_TCP_DEBUG = false;

    /** use simple TCP server started from TestActivity */
    public static final boolean USE_TCP_SIMPLE_SERVER = false;

    /** Test TCP socket port */
    public static final int TCP_DEBUG_PORT = 6500;

    /** use emulator to debug */
    public static final boolean USE_EMULATOR_DEBUG = false;

    public static final int MAX_RECORDS_IN_DATABASE = 1000;

    public static final int BATCH_STATUS_PENDING = 0;

    public static final int BATCH_STATUS_RUNNING = 1;

    public static final int BATCH_STATUS_FINISHED = 2;

    public static final int BATCH_STATUS_FAILED = 3;

    public static final String BLUETOOTHOPP_NAME_PREFERENCE = "btopp_names";

    public static final String BLUETOOTHOPP_CHANNEL_PREFERENCE = "btopp_channels";

    public static String filename_SEQUENCE_SEPARATOR = "-";

    public static void updateShareStatus(Context context, int id, int status) {
        Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + id);
        ContentValues updateValues = new ContentValues();
        updateValues.put(BluetoothShare.STATUS, status);
        context.getContentResolver().update(contentUri, updateValues, null, null);
        Constants.sendIntentIfCompleted(context, contentUri, status);
    }

    /*
     * This function should be called whenever transfer status change to
     * completed.
     */
    public static void sendIntentIfCompleted(Context context, Uri contentUri, int status) {
        if (BluetoothShare.isStatusCompleted(status)) {
            Intent intent = new Intent(BluetoothShare.TRANSFER_COMPLETED_ACTION);
            intent.setClassName(THIS_PACKAGE_NAME, BluetoothOppReceiver.class.getName());
            intent.setDataAndNormalize(contentUri);
            context.sendBroadcast(intent);
        }
    }

    public static boolean mimeTypeMatches(String mimeType, String[] matchAgainst) {
        for (String matchType : matchAgainst) {
            if (mimeTypeMatches(mimeType, matchType)) {
                return true;
            }
        }
        return false;
    }

    public static boolean mimeTypeMatches(String mimeType, String matchAgainst) {
        Pattern p = Pattern.compile(matchAgainst.replaceAll("\\*", "\\.\\*"),
                Pattern.CASE_INSENSITIVE);
        return p.matcher(mimeType).matches();
    }

    public static void logHeader(HeaderSet hs) {
        Log.v(TAG, "Dumping HeaderSet " + hs.toString());
        try {

            Log.v(TAG, "COUNT : " + hs.getHeader(HeaderSet.COUNT));
            Log.v(TAG, "NAME : " + hs.getHeader(HeaderSet.NAME));
            Log.v(TAG, "TYPE : " + hs.getHeader(HeaderSet.TYPE));
            Log.v(TAG, "LENGTH : " + hs.getHeader(HeaderSet.LENGTH));
            Log.v(TAG, "TIME_ISO_8601 : " + hs.getHeader(HeaderSet.TIME_ISO_8601));
            Log.v(TAG, "TIME_4_BYTE : " + hs.getHeader(HeaderSet.TIME_4_BYTE));
            Log.v(TAG, "DESCRIPTION : " + hs.getHeader(HeaderSet.DESCRIPTION));
            Log.v(TAG, "TARGET : " + hs.getHeader(HeaderSet.TARGET));
            Log.v(TAG, "HTTP : " + hs.getHeader(HeaderSet.HTTP));
            Log.v(TAG, "WHO : " + hs.getHeader(HeaderSet.WHO));
            Log.v(TAG, "OBJECT_CLASS : " + hs.getHeader(HeaderSet.OBJECT_CLASS));
            Log.v(TAG, "APPLICATION_PARAMETER : " + hs.getHeader(HeaderSet.APPLICATION_PARAMETER));
        } catch (IOException e) {
            Log.e(TAG, "dump HeaderSet error " + e);
        }
    }
}
