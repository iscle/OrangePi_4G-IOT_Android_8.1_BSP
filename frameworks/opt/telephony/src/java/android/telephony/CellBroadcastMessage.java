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
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package android.telephony;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.Telephony;
import android.text.format.DateUtils;

/**
 * Application wrapper for {@link SmsCbMessage}. This is Parcelable so that
 * decoded broadcast message objects can be passed between running Services.
 * New broadcasts are received by the CellBroadcastReceiver app, which exports
 * the database of previously received broadcasts at "content://cellbroadcasts/".
 * The "android.permission.READ_CELL_BROADCASTS" permission is required to read
 * from the ContentProvider, and writes to the database are not allowed.<p>
 *
 * Use {@link #createFromCursor} to create CellBroadcastMessage objects from rows
 * in the database cursor returned by the ContentProvider.
 *
 * {@hide}
 */
public class CellBroadcastMessage implements Parcelable {

    /** Identifier for getExtra() when adding this object to an Intent. */
    public static final String SMS_CB_MESSAGE_EXTRA =
            "com.android.cellbroadcastreceiver.SMS_CB_MESSAGE";

    /** SmsCbMessage. */
    // MTK-START
    // Modification for sub class
    protected SmsCbMessage mSmsCbMessage;

    protected long mDeliveryTime;
    protected boolean mIsRead;


    /**
     * Indicates the subId
     *
     * @hide
     */
    protected int mSubId = 0;

    // Add dummy constructor for sub class
    public CellBroadcastMessage() {};
    // MTK-END

    /**
     * set Subscription information
     *
     * @hide
     */
    public void setSubId(int subId) {
        mSubId = subId;
    }

    /**
     * get Subscription information
     *
     * @hide
     */
    public int getSubId() {
        return mSubId;
    }

    public CellBroadcastMessage(SmsCbMessage message) {
        mSmsCbMessage = message;
        mDeliveryTime = System.currentTimeMillis();
        mIsRead = false;
    }

    private CellBroadcastMessage(SmsCbMessage message, long deliveryTime, boolean isRead) {
        mSmsCbMessage = message;
        mDeliveryTime = deliveryTime;
        mIsRead = isRead;
    }

    private CellBroadcastMessage(Parcel in) {
        mSmsCbMessage = new SmsCbMessage(in);
        mDeliveryTime = in.readLong();
        mIsRead = (in.readInt() != 0);
        mSubId = in.readInt();
    }

    /** Parcelable: no special flags. */
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        mSmsCbMessage.writeToParcel(out, flags);
        out.writeLong(mDeliveryTime);
        out.writeInt(mIsRead ? 1 : 0);
        out.writeInt(mSubId);
    }

    public static final Parcelable.Creator<CellBroadcastMessage> CREATOR
            = new Parcelable.Creator<CellBroadcastMessage>() {
        @Override
        public CellBroadcastMessage createFromParcel(Parcel in) {
            return new CellBroadcastMessage(in);
        }

        @Override
        public CellBroadcastMessage[] newArray(int size) {
            return new CellBroadcastMessage[size];
        }
    };

    /**
     * Create a CellBroadcastMessage from a row in the database.
     * @param cursor an open SQLite cursor pointing to the row to read
     * @return the new CellBroadcastMessage
     * @throws IllegalArgumentException if one of the required columns is missing
     */
    public static CellBroadcastMessage createFromCursor(Cursor cursor) {
        int geoScope = cursor.getInt(
                cursor.getColumnIndexOrThrow(Telephony.CellBroadcasts.GEOGRAPHICAL_SCOPE));
        int serialNum = cursor.getInt(
                cursor.getColumnIndexOrThrow(Telephony.CellBroadcasts.SERIAL_NUMBER));
        int category = cursor.getInt(
                cursor.getColumnIndexOrThrow(Telephony.CellBroadcasts.SERVICE_CATEGORY));
        String language = cursor.getString(
                cursor.getColumnIndexOrThrow(Telephony.CellBroadcasts.LANGUAGE_CODE));
        String body = cursor.getString(
                cursor.getColumnIndexOrThrow(Telephony.CellBroadcasts.MESSAGE_BODY));
        int format = cursor.getInt(
                cursor.getColumnIndexOrThrow(Telephony.CellBroadcasts.MESSAGE_FORMAT));
        int priority = cursor.getInt(
                cursor.getColumnIndexOrThrow(Telephony.CellBroadcasts.MESSAGE_PRIORITY));

        String plmn;
        int plmnColumn = cursor.getColumnIndex(Telephony.CellBroadcasts.PLMN);
        if (plmnColumn != -1 && !cursor.isNull(plmnColumn)) {
            plmn = cursor.getString(plmnColumn);
        } else {
            plmn = null;
        }

        int lac;
        int lacColumn = cursor.getColumnIndex(Telephony.CellBroadcasts.LAC);
        if (lacColumn != -1 && !cursor.isNull(lacColumn)) {
            lac = cursor.getInt(lacColumn);
        } else {
            lac = -1;
        }

        int cid;
        int cidColumn = cursor.getColumnIndex(Telephony.CellBroadcasts.CID);
        if (cidColumn != -1 && !cursor.isNull(cidColumn)) {
            cid = cursor.getInt(cidColumn);
        } else {
            cid = -1;
        }

        SmsCbLocation location = new SmsCbLocation(plmn, lac, cid);

        SmsCbEtwsInfo etwsInfo;
        int etwsWarningTypeColumn = cursor.getColumnIndex(
                Telephony.CellBroadcasts.ETWS_WARNING_TYPE);
        if (etwsWarningTypeColumn != -1 && !cursor.isNull(etwsWarningTypeColumn)) {
            int warningType = cursor.getInt(etwsWarningTypeColumn);
            etwsInfo = new SmsCbEtwsInfo(warningType, false, false, false, null);
        } else {
            etwsInfo = null;
        }

        SmsCbCmasInfo cmasInfo;
        int cmasMessageClassColumn = cursor.getColumnIndex(
                Telephony.CellBroadcasts.CMAS_MESSAGE_CLASS);
        if (cmasMessageClassColumn != -1 && !cursor.isNull(cmasMessageClassColumn)) {
            int messageClass = cursor.getInt(cmasMessageClassColumn);

            int cmasCategory;
            int cmasCategoryColumn = cursor.getColumnIndex(
                    Telephony.CellBroadcasts.CMAS_CATEGORY);
            if (cmasCategoryColumn != -1 && !cursor.isNull(cmasCategoryColumn)) {
                cmasCategory = cursor.getInt(cmasCategoryColumn);
            } else {
                cmasCategory = SmsCbCmasInfo.CMAS_CATEGORY_UNKNOWN;
            }

            int responseType;
            int cmasResponseTypeColumn = cursor.getColumnIndex(
                    Telephony.CellBroadcasts.CMAS_RESPONSE_TYPE);
            if (cmasResponseTypeColumn != -1 && !cursor.isNull(cmasResponseTypeColumn)) {
                responseType = cursor.getInt(cmasResponseTypeColumn);
            } else {
                responseType = SmsCbCmasInfo.CMAS_RESPONSE_TYPE_UNKNOWN;
            }

            int severity;
            int cmasSeverityColumn = cursor.getColumnIndex(
                    Telephony.CellBroadcasts.CMAS_SEVERITY);
            if (cmasSeverityColumn != -1 && !cursor.isNull(cmasSeverityColumn)) {
                severity = cursor.getInt(cmasSeverityColumn);
            } else {
                severity = SmsCbCmasInfo.CMAS_SEVERITY_UNKNOWN;
            }

            int urgency;
            int cmasUrgencyColumn = cursor.getColumnIndex(
                    Telephony.CellBroadcasts.CMAS_URGENCY);
            if (cmasUrgencyColumn != -1 && !cursor.isNull(cmasUrgencyColumn)) {
                urgency = cursor.getInt(cmasUrgencyColumn);
            } else {
                urgency = SmsCbCmasInfo.CMAS_URGENCY_UNKNOWN;
            }

            int certainty;
            int cmasCertaintyColumn = cursor.getColumnIndex(
                    Telephony.CellBroadcasts.CMAS_CERTAINTY);
            if (cmasCertaintyColumn != -1 && !cursor.isNull(cmasCertaintyColumn)) {
                certainty = cursor.getInt(cmasCertaintyColumn);
            } else {
                certainty = SmsCbCmasInfo.CMAS_CERTAINTY_UNKNOWN;
            }

            cmasInfo = new SmsCbCmasInfo(messageClass, cmasCategory, responseType, severity,
                    urgency, certainty);
        } else {
            cmasInfo = null;
        }

        SmsCbMessage msg = new SmsCbMessage(format, geoScope, serialNum, location, category,
                language, body, priority, etwsInfo, cmasInfo);

        long deliveryTime = cursor.getLong(cursor.getColumnIndexOrThrow(
                Telephony.CellBroadcasts.DELIVERY_TIME));
        boolean isRead = (cursor.getInt(cursor.getColumnIndexOrThrow(
                Telephony.CellBroadcasts.MESSAGE_READ)) != 0);

        return new CellBroadcastMessage(msg, deliveryTime, isRead);
    }

    /**
     * Return a ContentValues object for insertion into the database.
     * @return a new ContentValues object containing this object's data
     */
    public ContentValues getContentValues() {
        ContentValues cv = new ContentValues(16);
        SmsCbMessage msg = mSmsCbMessage;
        cv.put(Telephony.CellBroadcasts.GEOGRAPHICAL_SCOPE, msg.getGeographicalScope());
        SmsCbLocation location = msg.getLocation();
        if (location.getPlmn() != null) {
            cv.put(Telephony.CellBroadcasts.PLMN, location.getPlmn());
        }
        if (location.getLac() != -1) {
            cv.put(Telephony.CellBroadcasts.LAC, location.getLac());
        }
        if (location.getCid() != -1) {
            cv.put(Telephony.CellBroadcasts.CID, location.getCid());
        }
        cv.put(Telephony.CellBroadcasts.SERIAL_NUMBER, msg.getSerialNumber());
        cv.put(Telephony.CellBroadcasts.SERVICE_CATEGORY, msg.getServiceCategory());
        cv.put(Telephony.CellBroadcasts.LANGUAGE_CODE, msg.getLanguageCode());
        cv.put(Telephony.CellBroadcasts.MESSAGE_BODY, msg.getMessageBody());
        cv.put(Telephony.CellBroadcasts.DELIVERY_TIME, mDeliveryTime);
        cv.put(Telephony.CellBroadcasts.MESSAGE_READ, mIsRead);
        cv.put(Telephony.CellBroadcasts.MESSAGE_FORMAT, msg.getMessageFormat());
        cv.put(Telephony.CellBroadcasts.MESSAGE_PRIORITY, msg.getMessagePriority());

        SmsCbEtwsInfo etwsInfo = mSmsCbMessage.getEtwsWarningInfo();
        if (etwsInfo != null) {
            cv.put(Telephony.CellBroadcasts.ETWS_WARNING_TYPE, etwsInfo.getWarningType());
        }

        SmsCbCmasInfo cmasInfo = mSmsCbMessage.getCmasWarningInfo();
        if (cmasInfo != null) {
            cv.put(Telephony.CellBroadcasts.CMAS_MESSAGE_CLASS, cmasInfo.getMessageClass());
            cv.put(Telephony.CellBroadcasts.CMAS_CATEGORY, cmasInfo.getCategory());
            cv.put(Telephony.CellBroadcasts.CMAS_RESPONSE_TYPE, cmasInfo.getResponseType());
            cv.put(Telephony.CellBroadcasts.CMAS_SEVERITY, cmasInfo.getSeverity());
            cv.put(Telephony.CellBroadcasts.CMAS_URGENCY, cmasInfo.getUrgency());
            cv.put(Telephony.CellBroadcasts.CMAS_CERTAINTY, cmasInfo.getCertainty());
        }

        return cv;
    }

    /**
     * Set or clear the "read message" flag.
     * @param isRead true if the message has been read; false if not
     */
    public void setIsRead(boolean isRead) {
        mIsRead = isRead;
    }

    public String getLanguageCode() {
        return mSmsCbMessage.getLanguageCode();
    }

    public int getServiceCategory() {
        return mSmsCbMessage.getServiceCategory();
    }

    public long getDeliveryTime() {
        return mDeliveryTime;
    }

    public String getMessageBody() {
        return mSmsCbMessage.getMessageBody();
    }

    public boolean isRead() {
        return mIsRead;
    }

    public int getSerialNumber() {
        return mSmsCbMessage.getSerialNumber();
    }

    public SmsCbCmasInfo getCmasWarningInfo() {
        return mSmsCbMessage.getCmasWarningInfo();
    }

    public SmsCbEtwsInfo getEtwsWarningInfo() {
        return mSmsCbMessage.getEtwsWarningInfo();
    }

    /**
     * Return whether the broadcast is an emergency (PWS) message type.
     * This includes lower priority test messages and Amber alerts.
     *
     * All public alerts show the flashing warning icon in the dialog,
     * but only emergency alerts play the alert sound and speak the message.
     *
     * @return true if the message is PWS type; false otherwise
     */
    public boolean isPublicAlertMessage() {
        return mSmsCbMessage.isEmergencyMessage();
    }

    /**
     * Returns whether the broadcast is an emergency (PWS) message type,
     * including test messages and AMBER alerts.
     *
     * @return true if the message is PWS type (ETWS or CMAS)
     */
    public boolean isEmergencyAlertMessage() {
        return mSmsCbMessage.isEmergencyMessage();
    }

    /**
     * Return whether the broadcast is an ETWS emergency message type.
     * @return true if the message is ETWS emergency type; false otherwise
     */
    public boolean isEtwsMessage() {
        return mSmsCbMessage.isEtwsMessage();
    }

    /**
     * Return whether the broadcast is a CMAS emergency message type.
     * @return true if the message is CMAS emergency type; false otherwise
     */
    public boolean isCmasMessage() {
        return mSmsCbMessage.isCmasMessage();
    }

    /**
     * Return the CMAS message class.
     * @return the CMAS message class, e.g. {@link SmsCbCmasInfo#CMAS_CLASS_SEVERE_THREAT}, or
     *  {@link SmsCbCmasInfo#CMAS_CLASS_UNKNOWN} if this is not a CMAS alert
     */
    public int getCmasMessageClass() {
        if (mSmsCbMessage.isCmasMessage()) {
            return mSmsCbMessage.getCmasWarningInfo().getMessageClass();
        } else {
            return SmsCbCmasInfo.CMAS_CLASS_UNKNOWN;
        }
    }

    /**
     * Return whether the broadcast is an ETWS popup alert.
     * This method checks the message ID and the message code.
     * @return true if the message indicates an ETWS popup alert
     */
    public boolean isEtwsPopupAlert() {
        SmsCbEtwsInfo etwsInfo = mSmsCbMessage.getEtwsWarningInfo();
        return etwsInfo != null && etwsInfo.isPopupAlert();
    }

    /**
     * Return whether the broadcast is an ETWS emergency user alert.
     * This method checks the message ID and the message code.
     * @return true if the message indicates an ETWS emergency user alert
     */
    public boolean isEtwsEmergencyUserAlert() {
        SmsCbEtwsInfo etwsInfo = mSmsCbMessage.getEtwsWarningInfo();
        return etwsInfo != null && etwsInfo.isEmergencyUserAlert();
    }

    /**
     * Return whether the broadcast is an ETWS test message.
     * @return true if the message is an ETWS test message; false otherwise
     */
    public boolean isEtwsTestMessage() {
        SmsCbEtwsInfo etwsInfo = mSmsCbMessage.getEtwsWarningInfo();
        return etwsInfo != null &&
                etwsInfo.getWarningType() == SmsCbEtwsInfo.ETWS_WARNING_TYPE_TEST_MESSAGE;
    }

    /**
     * Return the abbreviated date string for the message delivery time.
     * @param context the context object
     * @return a String to use in the broadcast list UI
     */
    public String getDateString(Context context) {
        int flags = DateUtils.FORMAT_NO_NOON_MIDNIGHT | DateUtils.FORMAT_SHOW_TIME |
                DateUtils.FORMAT_ABBREV_ALL | DateUtils.FORMAT_SHOW_DATE |
                DateUtils.FORMAT_CAP_AMPM;
        return DateUtils.formatDateTime(context, mDeliveryTime, flags);
    }

    /**
     * Return the date string for the message delivery time, suitable for text-to-speech.
     * @param context the context object
     * @return a String for populating the list item AccessibilityEvent for TTS
     */
    public String getSpokenDateString(Context context) {
        int flags = DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE;
        return DateUtils.formatDateTime(context, mDeliveryTime, flags);
    }
}
