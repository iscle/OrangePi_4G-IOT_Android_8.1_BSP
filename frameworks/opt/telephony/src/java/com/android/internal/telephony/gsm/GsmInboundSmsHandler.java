/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2017. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */
/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.internal.telephony.gsm;

import android.app.Activity;
import android.content.Context;
import android.os.Message;
import android.provider.Telephony.Sms.Intents;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.InboundSmsHandler;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.SmsConstants;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.SmsMessageBase;
import com.android.internal.telephony.SmsStorageMonitor;
import com.android.internal.telephony.VisualVoicemailSmsFilter;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.telephony.uicc.UsimServiceTable;

// MTK-START
// Add for sub class
import com.android.internal.telephony.TelephonyComponentFactory;
// MTK-END

/**
 * This class broadcasts incoming SMS messages to interested apps after storing them in
 * the SmsProvider "raw" table and ACKing them to the SMSC. After each message has been
 */
public class GsmInboundSmsHandler extends InboundSmsHandler {

    /** Handler for SMS-PP data download messages to UICC. */
    // MTK-START
    // Modification for sub class
    protected final UsimDataDownloadHandler mDataDownloadHandler;
    // MTK-END

    /**
     * Create a new GSM inbound SMS handler.
     */
    private GsmInboundSmsHandler(Context context, SmsStorageMonitor storageMonitor,
            Phone phone) {
        // MTK-START
        // Modification for sub class
        super("GsmInboundSmsHandler", context, storageMonitor, phone,
                TelephonyComponentFactory.getInstance().makeGsmCellBroadcastHandler(
                context, phone));
        // MTK-END
        phone.mCi.setOnNewGsmSms(getHandler(), EVENT_NEW_SMS, null);
        mDataDownloadHandler = new UsimDataDownloadHandler(phone.mCi);
    }

    // MTK-START
    // Add a dummy constructor for sub class
    protected GsmInboundSmsHandler(String name, Context context,
            SmsStorageMonitor storageMonitor, Phone phone) {
        super(name, context, storageMonitor, phone,
                TelephonyComponentFactory.getInstance().makeGsmCellBroadcastHandler(context, phone),
                null);
        phone.mCi.setOnNewGsmSms(getHandler(), EVENT_NEW_SMS, null);
        mDataDownloadHandler = new UsimDataDownloadHandler(phone.mCi);
    }
    // MTK-END

    /**
     * Unregister for GSM SMS.
     */
    @Override
    protected void onQuitting() {
        mPhone.mCi.unSetOnNewGsmSms(getHandler());
        mCellBroadcastHandler.dispose();

        if (DBG) log("unregistered for 3GPP SMS");
        super.onQuitting();     // release wakelock
    }

    /**
     * Wait for state machine to enter startup state. We can't send any messages until then.
     */
    public static GsmInboundSmsHandler makeInboundSmsHandler(Context context,
            SmsStorageMonitor storageMonitor, Phone phone) {
        GsmInboundSmsHandler handler = new GsmInboundSmsHandler(context, storageMonitor, phone);
        handler.start();
        return handler;
    }

    /**
     * Return true if this handler is for 3GPP2 messages; false for 3GPP format.
     * @return false (3GPP)
     */
    @Override
    protected boolean is3gpp2() {
        return false;
    }

    /**
     * Handle type zero, SMS-PP data download, and 3GPP/CPHS MWI type SMS. Normal SMS messages
     * are handled by {@link #dispatchNormalMessage} in parent class.
     *
     * @param smsb the SmsMessageBase object from the RIL
     * @return a result code from {@link android.provider.Telephony.Sms.Intents},
     *  or {@link Activity#RESULT_OK} for delayed acknowledgment to SMSC
     */
    @Override
    protected int dispatchMessageRadioSpecific(SmsMessageBase smsb) {
        SmsMessage sms = (SmsMessage) smsb;

        if (sms.isTypeZero()) {
            // Some carriers will send visual voicemail SMS as type zero.
            int destPort = -1;
            SmsHeader smsHeader = sms.getUserDataHeader();
            if (smsHeader != null && smsHeader.portAddrs != null) {
                // The message was sent to a port.
                destPort = smsHeader.portAddrs.destPort;
            }
            VisualVoicemailSmsFilter
                    .filter(mContext, new byte[][]{sms.getPdu()}, SmsConstants.FORMAT_3GPP,
                            destPort, mPhone.getSubId());
            // As per 3GPP TS 23.040 9.2.3.9, Type Zero messages should not be
            // Displayed/Stored/Notified. They should only be acknowledged.
            log("Received short message type 0, Don't display or store it. Send Ack");
            return Intents.RESULT_SMS_HANDLED;
        }

        // Send SMS-PP data download messages to UICC. See 3GPP TS 31.111 section 7.1.1.
        if (sms.isUsimDataDownload()) {
            UsimServiceTable ust = mPhone.getUsimServiceTable();
            return mDataDownloadHandler.handleUsimDataDownload(ust, sms);
        }

        boolean handled = false;
        if (sms.isMWISetMessage()) {
            updateMessageWaitingIndicator(sms.getNumOfVoicemails());
            handled = sms.isMwiDontStore();
            if (DBG) log("Received voice mail indicator set SMS shouldStore=" + !handled);
        } else if (sms.isMWIClearMessage()) {
            updateMessageWaitingIndicator(0);
            handled = sms.isMwiDontStore();
            if (DBG) log("Received voice mail indicator clear SMS shouldStore=" + !handled);
        }
        if (handled) {
            return Intents.RESULT_SMS_HANDLED;
        }

        if (!mStorageMonitor.isStorageAvailable() &&
                sms.getMessageClass() != SmsConstants.MessageClass.CLASS_0) {
            // It's a storable message and there's no storage available.  Bail.
            // (See TS 23.038 for a description of class 0 messages.)
            return Intents.RESULT_SMS_OUT_OF_MEMORY;
        }

        return dispatchNormalMessage(smsb);
    }

    private void updateMessageWaitingIndicator(int voicemailCount) {
        // range check
        if (voicemailCount < 0) {
            voicemailCount = -1;
        } else if (voicemailCount > 0xff) {
            // TS 23.040 9.2.3.24.2
            // "The value 255 shall be taken to mean 255 or greater"
            voicemailCount = 0xff;
        }
        // update voice mail count in Phone
        mPhone.setVoiceMessageCount(voicemailCount);
        // store voice mail count in SIM & shared preferences
        IccRecords records = UiccController.getInstance().getIccRecords(
                mPhone.getPhoneId(), UiccController.APP_FAM_3GPP);
        if (records != null) {
            log("updateMessageWaitingIndicator: updating SIM Records");
            records.setVoiceMessageWaiting(1, voicemailCount);
        } else {
            log("updateMessageWaitingIndicator: SIM Records not found");
        }
    }

    /**
     * Send an acknowledge message.
     * @param success indicates that last message was successfully received.
     * @param result result code indicating any error
     * @param response callback message sent when operation completes.
     */
    @Override
    protected void acknowledgeLastIncomingSms(boolean success, int result, Message response) {
        mPhone.mCi.acknowledgeLastIncomingGsmSms(success, resultToCause(result), response);
    }

    /**
     * Called when the phone changes the default method updates mPhone
     * mStorageMonitor and mCellBroadcastHandler.updatePhoneObject.
     * Override if different or other behavior is desired.
     *
     * @param phone
     */
    @Override
    protected void onUpdatePhoneObject(Phone phone) {
        super.onUpdatePhoneObject(phone);
        log("onUpdatePhoneObject: dispose of old CellBroadcastHandler and make a new one");
        mCellBroadcastHandler.dispose();
        // MTK-START
        // Modification for sub class
        mCellBroadcastHandler = TelephonyComponentFactory.getInstance()
                .makeGsmCellBroadcastHandler(mContext, phone);
        // MTK-END
    }

    /**
     * Convert Android result code to 3GPP SMS failure cause.
     * @param rc the Android SMS intent result value
     * @return 0 for success, or a 3GPP SMS failure cause value
     */
    private static int resultToCause(int rc) {
        switch (rc) {
            case Activity.RESULT_OK:
            case Intents.RESULT_SMS_HANDLED:
                // Cause code is ignored on success.
                return 0;
            case Intents.RESULT_SMS_OUT_OF_MEMORY:
                return CommandsInterface.GSM_SMS_FAIL_CAUSE_MEMORY_CAPACITY_EXCEEDED;
            case Intents.RESULT_SMS_GENERIC_ERROR:
            default:
                return CommandsInterface.GSM_SMS_FAIL_CAUSE_UNSPECIFIED_ERROR;
        }
    }
}
