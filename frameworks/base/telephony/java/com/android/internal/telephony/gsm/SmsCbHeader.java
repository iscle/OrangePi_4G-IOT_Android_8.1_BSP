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
 * Copyright (C) 2010 The Android Open Source Project
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

import android.telephony.SmsCbCmasInfo;
import android.telephony.SmsCbEtwsInfo;

import java.util.Arrays;

/**
 * Parses a 3GPP TS 23.041 cell broadcast message header. This class is public for use by
 * CellBroadcastReceiver test cases, but should not be used by applications.
 *
 * All relevant header information is now sent as a Parcelable
 * {@link android.telephony.SmsCbMessage} object in the "message" extra of the
 * {@link android.provider.Telephony.Sms.Intents#SMS_CB_RECEIVED_ACTION} or
 * {@link android.provider.Telephony.Sms.Intents#SMS_EMERGENCY_CB_RECEIVED_ACTION} intent.
 * The raw PDU is no longer sent to SMS CB applications.
 */
public class SmsCbHeader {

    /**
     * Length of SMS-CB header
     */
    // MTK-START
    // Modification for sub class
    protected static final int PDU_HEADER_LENGTH = 6;

    /**
     * GSM pdu format, as defined in 3gpp TS 23.041, section 9.4.1
     */
    protected static final int FORMAT_GSM = 1;

    /**
     * UMTS pdu format, as defined in 3gpp TS 23.041, section 9.4.2
     */
    protected static final int FORMAT_UMTS = 2;

    /**
     * GSM pdu format, as defined in 3gpp TS 23.041, section 9.4.1.3
     */
    protected static final int FORMAT_ETWS_PRIMARY = 3;

    /**
     * Message type value as defined in 3gpp TS 25.324, section 11.1.
     */
    protected static final int MESSAGE_TYPE_CBS_MESSAGE = 1;

    /**
     * Length of GSM pdus
     */
    protected static final int PDU_LENGTH_GSM = 88;

    /**
     * Maximum length of ETWS primary message GSM pdus
     */
    protected static final int PDU_LENGTH_ETWS = 56;

    // Remove "final" for sub class - [Start]
    protected int mGeographicalScope;

    /** The serial number combines geographical scope, message code, and update number. */
    protected int mSerialNumber;

    /** The Message Identifier in 3GPP is the same as the Service Category in CDMA. */
    protected int mMessageIdentifier;

    protected int mDataCodingScheme;

    protected int mPageIndex;

    protected int mNrOfPages;

    protected int mFormat;

    /** ETWS warning notification info. */
    protected SmsCbEtwsInfo mEtwsInfo;

    /** CMAS warning notification info. */
    protected SmsCbCmasInfo mCmasInfo;
    // Remove "final" for sub class - [End]
    // MTK-END

    public SmsCbHeader(byte[] pdu) throws IllegalArgumentException {
        if (pdu == null || pdu.length < PDU_HEADER_LENGTH) {
            throw new IllegalArgumentException("Illegal PDU");
        }

        if (pdu.length <= PDU_LENGTH_GSM) {
            // can be ETWS or GSM format.
            // Per TS23.041 9.4.1.2 and 9.4.1.3.2, GSM and ETWS format both
            // contain serial number which contains GS, Message Code, and Update Number
            // per 9.4.1.2.1, and message identifier in same octets
            mGeographicalScope = (pdu[0] & 0xc0) >>> 6;
            mSerialNumber = ((pdu[0] & 0xff) << 8) | (pdu[1] & 0xff);
            mMessageIdentifier = ((pdu[2] & 0xff) << 8) | (pdu[3] & 0xff);
            if (isEtwsMessage() && pdu.length <= PDU_LENGTH_ETWS) {
                mFormat = FORMAT_ETWS_PRIMARY;
                mDataCodingScheme = -1;
                mPageIndex = -1;
                mNrOfPages = -1;
                boolean emergencyUserAlert = (pdu[4] & 0x1) != 0;
                boolean activatePopup = (pdu[5] & 0x80) != 0;
                int warningType = (pdu[4] & 0xfe) >>> 1;
                byte[] warningSecurityInfo;
                // copy the Warning-Security-Information, if present
                if (pdu.length > PDU_HEADER_LENGTH) {
                    warningSecurityInfo = Arrays.copyOfRange(pdu, 6, pdu.length);
                } else {
                    warningSecurityInfo = null;
                }
                mEtwsInfo = new SmsCbEtwsInfo(warningType, emergencyUserAlert, activatePopup,
                        true, warningSecurityInfo);
                mCmasInfo = null;
                return;     // skip the ETWS/CMAS initialization code for regular notifications
            } else {
                // GSM pdus are no more than 88 bytes
                mFormat = FORMAT_GSM;
                mDataCodingScheme = pdu[4] & 0xff;

                // Check for invalid page parameter
                int pageIndex = (pdu[5] & 0xf0) >>> 4;
                int nrOfPages = pdu[5] & 0x0f;

                if (pageIndex == 0 || nrOfPages == 0 || pageIndex > nrOfPages) {
                    pageIndex = 1;
                    nrOfPages = 1;
                }

                mPageIndex = pageIndex;
                mNrOfPages = nrOfPages;
            }
        } else {
            // UMTS pdus are always at least 90 bytes since the payload includes
            // a number-of-pages octet and also one length octet per page
            mFormat = FORMAT_UMTS;

            int messageType = pdu[0];

            if (messageType != MESSAGE_TYPE_CBS_MESSAGE) {
                throw new IllegalArgumentException("Unsupported message type " + messageType);
            }

            mMessageIdentifier = ((pdu[1] & 0xff) << 8) | pdu[2] & 0xff;
            mGeographicalScope = (pdu[3] & 0xc0) >>> 6;
            mSerialNumber = ((pdu[3] & 0xff) << 8) | (pdu[4] & 0xff);
            mDataCodingScheme = pdu[5] & 0xff;

            // We will always consider a UMTS message as having one single page
            // since there's only one instance of the header, even though the
            // actual payload may contain several pages.
            mPageIndex = 1;
            mNrOfPages = 1;
        }

        if (isEtwsMessage()) {
            boolean emergencyUserAlert = isEtwsEmergencyUserAlert();
            boolean activatePopup = isEtwsPopupAlert();
            int warningType = getEtwsWarningType();
            mEtwsInfo = new SmsCbEtwsInfo(warningType, emergencyUserAlert, activatePopup,
                    false, null);
            mCmasInfo = null;
        } else if (isCmasMessage()) {
            int messageClass = getCmasMessageClass();
            int severity = getCmasSeverity();
            int urgency = getCmasUrgency();
            int certainty = getCmasCertainty();
            mEtwsInfo = null;
            mCmasInfo = new SmsCbCmasInfo(messageClass, SmsCbCmasInfo.CMAS_CATEGORY_UNKNOWN,
                    SmsCbCmasInfo.CMAS_RESPONSE_TYPE_UNKNOWN, severity, urgency, certainty);
        } else {
            mEtwsInfo = null;
            mCmasInfo = null;
        }
    }

    // MTK-START
    // Modification for sub class
    public int getGeographicalScope() {
        return mGeographicalScope;
    }

    public int getSerialNumber() {
        return mSerialNumber;
    }

    public int getServiceCategory() {
        return mMessageIdentifier;
    }

    public int getDataCodingScheme() {
        return mDataCodingScheme;
    }

    public int getPageIndex() {
        return mPageIndex;
    }

    public int getNumberOfPages() {
        return mNrOfPages;
    }

    public SmsCbEtwsInfo getEtwsInfo() {
        return mEtwsInfo;
    }

    public SmsCbCmasInfo getCmasInfo() {
        return mCmasInfo;
    }
    // MTK-END

    /**
     * Return whether this broadcast is an emergency (PWS) message type.
     * @return true if this message is emergency type; false otherwise
     */
    // MTK-START
    // Modification for sub class
    protected boolean isEmergencyMessage() {
    // MTK-END
        return mMessageIdentifier >= SmsCbConstants.MESSAGE_ID_PWS_FIRST_IDENTIFIER
                && mMessageIdentifier <= SmsCbConstants.MESSAGE_ID_PWS_LAST_IDENTIFIER;
    }

    /**
     * Return whether this broadcast is an ETWS emergency message type.
     * @return true if this message is ETWS emergency type; false otherwise
     */
    // MTK-START
    // Modification for sub class
    protected boolean isEtwsMessage() {
    // MTK-END
        return (mMessageIdentifier & SmsCbConstants.MESSAGE_ID_ETWS_TYPE_MASK)
                == SmsCbConstants.MESSAGE_ID_ETWS_TYPE;
    }

    /**
     * Return whether this broadcast is an ETWS primary notification.
     * @return true if this message is an ETWS primary notification; false otherwise
     */
    protected boolean isEtwsPrimaryNotification() {
        return mFormat == FORMAT_ETWS_PRIMARY;
    }

    /**
     * Return whether this broadcast is in UMTS format.
     * @return true if this message is in UMTS format; false otherwise
     */
    protected boolean isUmtsFormat() {
        return mFormat == FORMAT_UMTS;
    }

    /**
     * Return whether this message is a CMAS emergency message type.
     * @return true if this message is CMAS emergency type; false otherwise
     */
    // MTK-START
    // Modification for sub class
    protected boolean isCmasMessage() {
    // MTK-END
        return mMessageIdentifier >= SmsCbConstants.MESSAGE_ID_CMAS_FIRST_IDENTIFIER
                && mMessageIdentifier <= SmsCbConstants.MESSAGE_ID_CMAS_LAST_IDENTIFIER;
    }

    /**
     * Return whether the popup alert flag is set for an ETWS warning notification.
     * This method assumes that the message ID has already been checked for ETWS type.
     *
     * @return true if the message code indicates a popup alert should be displayed
     */
    // MTK-START
    // Modification for sub class
    protected boolean isEtwsPopupAlert() {
    // MTK-END
        return (mSerialNumber & SmsCbConstants.SERIAL_NUMBER_ETWS_ACTIVATE_POPUP) != 0;
    }

    /**
     * Return whether the emergency user alert flag is set for an ETWS warning notification.
     * This method assumes that the message ID has already been checked for ETWS type.
     *
     * @return true if the message code indicates an emergency user alert
     */
    // MTK-START
    // Modification for sub class
    protected boolean isEtwsEmergencyUserAlert() {
    // MTK-END
        return (mSerialNumber & SmsCbConstants.SERIAL_NUMBER_ETWS_EMERGENCY_USER_ALERT) != 0;
    }

    /**
     * Returns the warning type for an ETWS warning notification.
     * This method assumes that the message ID has already been checked for ETWS type.
     *
     * @return the ETWS warning type defined in 3GPP TS 23.041 section 9.3.24
     */
    // MTK-START
    // Modification for sub class
    protected int getEtwsWarningType() {
    // MTK-END
        return mMessageIdentifier - SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_WARNING;
    }

    /**
     * Returns the message class for a CMAS warning notification.
     * This method assumes that the message ID has already been checked for CMAS type.
     * @return the CMAS message class as defined in {@link SmsCbCmasInfo}
     */
    // MTK-START
    // Modification for sub class
    protected int getCmasMessageClass() {
    // MTK-END
        switch (mMessageIdentifier) {
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL_LANGUAGE:
                return SmsCbCmasInfo.CMAS_CLASS_PRESIDENTIAL_LEVEL_ALERT;

            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED_LANGUAGE:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY_LANGUAGE:
                return SmsCbCmasInfo.CMAS_CLASS_EXTREME_THREAT;

            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED_LANGUAGE:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_LIKELY:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_LIKELY_LANGUAGE:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_OBSERVED:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_OBSERVED_LANGUAGE:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_LIKELY:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_LIKELY_LANGUAGE:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_OBSERVED:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_OBSERVED_LANGUAGE:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY_LANGUAGE:
                return SmsCbCmasInfo.CMAS_CLASS_SEVERE_THREAT;

            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY_LANGUAGE:
                return SmsCbCmasInfo.CMAS_CLASS_CHILD_ABDUCTION_EMERGENCY;

            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_REQUIRED_MONTHLY_TEST:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_REQUIRED_MONTHLY_TEST_LANGUAGE:
                return SmsCbCmasInfo.CMAS_CLASS_REQUIRED_MONTHLY_TEST;

            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXERCISE:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXERCISE_LANGUAGE:
                return SmsCbCmasInfo.CMAS_CLASS_CMAS_EXERCISE;

            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_OPERATOR_DEFINED_USE:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_OPERATOR_DEFINED_USE_LANGUAGE:
                return SmsCbCmasInfo.CMAS_CLASS_OPERATOR_DEFINED_USE;

            default:
                return SmsCbCmasInfo.CMAS_CLASS_UNKNOWN;
        }
    }

    /**
     * Returns the severity for a CMAS warning notification. This is only available for extreme
     * and severe alerts, not for other types such as Presidential Level and AMBER alerts.
     * This method assumes that the message ID has already been checked for CMAS type.
     * @return the CMAS severity as defined in {@link SmsCbCmasInfo}
     */
    // MTK-START
    // Modification for sub class
    protected int getCmasSeverity() {
    // MTK-END
        switch (mMessageIdentifier) {
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED_LANGUAGE:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY_LANGUAGE:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED_LANGUAGE:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_LIKELY:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_LIKELY_LANGUAGE:
                return SmsCbCmasInfo.CMAS_SEVERITY_EXTREME;

            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_OBSERVED:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_OBSERVED_LANGUAGE:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_LIKELY:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_LIKELY_LANGUAGE:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_OBSERVED:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_OBSERVED_LANGUAGE:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY_LANGUAGE:
                return SmsCbCmasInfo.CMAS_SEVERITY_SEVERE;

            default:
                return SmsCbCmasInfo.CMAS_SEVERITY_UNKNOWN;
        }
    }

    /**
     * Returns the urgency for a CMAS warning notification. This is only available for extreme
     * and severe alerts, not for other types such as Presidential Level and AMBER alerts.
     * This method assumes that the message ID has already been checked for CMAS type.
     * @return the CMAS urgency as defined in {@link SmsCbCmasInfo}
     */
    // MTK-START
    // Modification for sub class
    protected int getCmasUrgency() {
    // MTK-END
        switch (mMessageIdentifier) {
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED_LANGUAGE:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY_LANGUAGE:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_OBSERVED:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_OBSERVED_LANGUAGE:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_LIKELY:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_LIKELY_LANGUAGE:
                return SmsCbCmasInfo.CMAS_URGENCY_IMMEDIATE;

            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED_LANGUAGE:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_LIKELY:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_LIKELY_LANGUAGE:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_OBSERVED:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_OBSERVED_LANGUAGE:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY_LANGUAGE:
                return SmsCbCmasInfo.CMAS_URGENCY_EXPECTED;

            default:
                return SmsCbCmasInfo.CMAS_URGENCY_UNKNOWN;
        }
    }

    /**
     * Returns the certainty for a CMAS warning notification. This is only available for extreme
     * and severe alerts, not for other types such as Presidential Level and AMBER alerts.
     * This method assumes that the message ID has already been checked for CMAS type.
     * @return the CMAS certainty as defined in {@link SmsCbCmasInfo}
     */
    // MTK-START
    // Modification for sub class
    protected int getCmasCertainty() {
    // MTK-END
        switch (mMessageIdentifier) {
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED_LANGUAGE:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED_LANGUAGE:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_OBSERVED:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_OBSERVED_LANGUAGE:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_OBSERVED:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_OBSERVED_LANGUAGE:
                return SmsCbCmasInfo.CMAS_CERTAINTY_OBSERVED;

            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY_LANGUAGE:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_LIKELY:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_LIKELY_LANGUAGE:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_LIKELY:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_LIKELY_LANGUAGE:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY_LANGUAGE:
                return SmsCbCmasInfo.CMAS_CERTAINTY_LIKELY;

            default:
                return SmsCbCmasInfo.CMAS_CERTAINTY_UNKNOWN;
        }
    }

    @Override
    public String toString() {
        return "SmsCbHeader{GS=" + mGeographicalScope + ", serialNumber=0x" +
                Integer.toHexString(mSerialNumber) +
                ", messageIdentifier=0x" + Integer.toHexString(mMessageIdentifier) +
                ", DCS=0x" + Integer.toHexString(mDataCodingScheme) +
                ", page " + mPageIndex + " of " + mNrOfPages + '}';
    }

    // MTK-START
    // Added for sub class
    public SmsCbHeader() {
    }
    // MTK-END
}