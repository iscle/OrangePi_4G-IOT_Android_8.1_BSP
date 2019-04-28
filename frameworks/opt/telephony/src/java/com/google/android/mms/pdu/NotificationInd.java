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
 * Copyright (C) 2007 Esmertec AG.
 * Copyright (C) 2007 The Android Open Source Project
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

package com.google.android.mms.pdu;

import com.google.android.mms.InvalidHeaderValueException;

/**
 * M-Notification.ind PDU.
 */
public class NotificationInd extends GenericPdu {
    /**
     * Empty constructor.
     * Since the Pdu corresponding to this class is constructed
     * by the Proxy-Relay server, this class is only instantiated
     * by the Pdu Parser.
     *
     * @throws InvalidHeaderValueException if error occurs.
     *         RuntimeException if an undeclared error occurs.
     */
    public NotificationInd() throws InvalidHeaderValueException {
        super();
        setMessageType(PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND);
    }

    /*MTK Change access type*/
    /**
     * Constructor with given headers.
     *
     * @param headers Headers for this PDU.
     */
    public NotificationInd(PduHeaders headers) {
        super(headers);
    }

    /**
     * Get X-Mms-Content-Class Value.
     *
     * @return the value
     */
    public int getContentClass() {
        return mPduHeaders.getOctet(PduHeaders.CONTENT_CLASS);
    }

    /**
     * Set X-Mms-Content-Class Value.
     *
     * @param value the value
     * @throws InvalidHeaderValueException if the value is invalid.
     *         RuntimeException if an undeclared error occurs.
     */
    public void setContentClass(int value) throws InvalidHeaderValueException {
        mPduHeaders.setOctet(value, PduHeaders.CONTENT_CLASS);
    }

    /**
     * Get X-Mms-Content-Location value.
     * When used in a PDU other than M-Mbox-Delete.conf and M-Delete.conf:
     * Content-location-value = Uri-value
     *
     * @return the value
     */
    public byte[] getContentLocation() {
        return mPduHeaders.getTextString(PduHeaders.CONTENT_LOCATION);
    }

    /**
     * Set X-Mms-Content-Location value.
     *
     * @param value the value
     * @throws NullPointerException if the value is null.
     *         RuntimeException if an undeclared error occurs.
     */
    public void setContentLocation(byte[] value) {
        mPduHeaders.setTextString(value, PduHeaders.CONTENT_LOCATION);
    }

    /**
     * Get X-Mms-Expiry value.
     *
     * Expiry-value = Value-length
     *      (Absolute-token Date-value | Relative-token Delta-seconds-value)
     *
     * @return the value
     */
    public long getExpiry() {
        return mPduHeaders.getLongInteger(PduHeaders.EXPIRY);
    }

    /**
     * Set X-Mms-Expiry value.
     *
     * @param value the value
     * @throws RuntimeException if an undeclared error occurs.
     */
    public void setExpiry(long value) {
        mPduHeaders.setLongInteger(value, PduHeaders.EXPIRY);
    }

    /**
     * Get From value.
     * From-value = Value-length
     *      (Address-present-token Encoded-string-value | Insert-address-token)
     *
     * @return the value
     */
    public EncodedStringValue getFrom() {
       return mPduHeaders.getEncodedStringValue(PduHeaders.FROM);
    }

    /**
     * Set From value.
     *
     * @param value the value
     * @throws NullPointerException if the value is null.
     *         RuntimeException if an undeclared error occurs.
     */
    public void setFrom(EncodedStringValue value) {
        mPduHeaders.setEncodedStringValue(value, PduHeaders.FROM);
    }

    /**
     * Get X-Mms-Message-Class value.
     * Message-class-value = Class-identifier | Token-text
     * Class-identifier = Personal | Advertisement | Informational | Auto
     *
     * @return the value
     */
    public byte[] getMessageClass() {
        return mPduHeaders.getTextString(PduHeaders.MESSAGE_CLASS);
    }

    /**
     * Set X-Mms-Message-Class value.
     *
     * @param value the value
     * @throws NullPointerException if the value is null.
     *         RuntimeException if an undeclared error occurs.
     */
    public void setMessageClass(byte[] value) {
        mPduHeaders.setTextString(value, PduHeaders.MESSAGE_CLASS);
    }

    /**
     * Get X-Mms-Message-Size value.
     * Message-size-value = Long-integer
     *
     * @return the value
     */
    public long getMessageSize() {
        return mPduHeaders.getLongInteger(PduHeaders.MESSAGE_SIZE);
    }

    /**
     * Set X-Mms-Message-Size value.
     *
     * @param value the value
     * @throws RuntimeException if an undeclared error occurs.
     */
    public void setMessageSize(long value) {
        mPduHeaders.setLongInteger(value, PduHeaders.MESSAGE_SIZE);
    }

    /**
     * Get subject.
     *
     * @return the value
     */
    public EncodedStringValue getSubject() {
        return mPduHeaders.getEncodedStringValue(PduHeaders.SUBJECT);
    }

    /**
     * Set subject.
     *
     * @param value the value
     * @throws NullPointerException if the value is null.
     *         RuntimeException if an undeclared error occurs.
     */
    public void setSubject(EncodedStringValue value) {
        mPduHeaders.setEncodedStringValue(value, PduHeaders.SUBJECT);
    }

    /**
     * Get X-Mms-Transaction-Id.
     *
     * @return the value
     */
    public byte[] getTransactionId() {
        return mPduHeaders.getTextString(PduHeaders.TRANSACTION_ID);
    }

    /**
     * Set X-Mms-Transaction-Id.
     *
     * @param value the value
     * @throws NullPointerException if the value is null.
     *         RuntimeException if an undeclared error occurs.
     */
    public void setTransactionId(byte[] value) {
        mPduHeaders.setTextString(value, PduHeaders.TRANSACTION_ID);
    }

    /**
     * Get X-Mms-Delivery-Report Value.
     *
     * @return the value
     */
    public int getDeliveryReport() {
        return mPduHeaders.getOctet(PduHeaders.DELIVERY_REPORT);
    }

    /**
     * Set X-Mms-Delivery-Report Value.
     *
     * @param value the value
     * @throws InvalidHeaderValueException if the value is invalid.
     *         RuntimeException if an undeclared error occurs.
     */
    public void setDeliveryReport(int value) throws InvalidHeaderValueException {
        mPduHeaders.setOctet(value, PduHeaders.DELIVERY_REPORT);
    }

    /*
     * Optional, not supported header fields:
     *
     *     public byte[] getApplicId() {return null;}
     *     public void setApplicId(byte[] value) {}
     *
     *     public byte[] getAuxApplicId() {return null;}
     *     public void getAuxApplicId(byte[] value) {}
     *
     *     public byte getDrmContent() {return 0x00;}
     *     public void setDrmContent(byte value) {}
     *
     *     public byte getDistributionIndicator() {return 0x00;}
     *     public void setDistributionIndicator(byte value) {}
     *
     *     public ElementDescriptorValue getElementDescriptor() {return null;}
     *     public void getElementDescriptor(ElementDescriptorValue value) {}
     *
     *     public byte getPriority() {return 0x00;}
     *     public void setPriority(byte value) {}
     *
     *     public byte getRecommendedRetrievalMode() {return 0x00;}
     *     public void setRecommendedRetrievalMode(byte value) {}
     *
     *     public byte getRecommendedRetrievalModeText() {return 0x00;}
     *     public void setRecommendedRetrievalModeText(byte value) {}
     *
     *     public byte[] getReplaceId() {return 0x00;}
     *     public void setReplaceId(byte[] value) {}
     *
     *     public byte[] getReplyApplicId() {return 0x00;}
     *     public void setReplyApplicId(byte[] value) {}
     *
     *     public byte getReplyCharging() {return 0x00;}
     *     public void setReplyCharging(byte value) {}
     *
     *     public byte getReplyChargingDeadline() {return 0x00;}
     *     public void setReplyChargingDeadline(byte value) {}
     *
     *     public byte[] getReplyChargingId() {return 0x00;}
     *     public void setReplyChargingId(byte[] value) {}
     *
     *     public long getReplyChargingSize() {return 0;}
     *     public void setReplyChargingSize(long value) {}
     *
     *     public byte getStored() {return 0x00;}
     *     public void setStored(byte value) {}
     */
}
