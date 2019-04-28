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
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.internal.telephony.cat;

import java.util.List;

/**
 * Class for representing BER-TLV objects.
 *
 * @see "ETSI TS 102 223 Annex C" for more information.
 *
 * {@hide}
 */
// MTK-START
public class BerTlv {
// MTK-END
    private int mTag = BER_UNKNOWN_TAG;
    private List<ComprehensionTlv> mCompTlvs = null;
    private boolean mLengthValid = true;

    public static final int BER_UNKNOWN_TAG             = 0x00;
    public static final int BER_PROACTIVE_COMMAND_TAG   = 0xd0;
    public static final int BER_MENU_SELECTION_TAG      = 0xd3;
    public static final int BER_EVENT_DOWNLOAD_TAG      = 0xd6;

    private BerTlv(int tag, List<ComprehensionTlv> ctlvs, boolean lengthValid) {
        mTag = tag;
        mCompTlvs = ctlvs;
        mLengthValid = lengthValid;
    }

    /**
     * Gets a list of ComprehensionTlv objects contained in this BER-TLV object.
     *
     * @return A list of COMPREHENSION-TLV object
     */
    public List<ComprehensionTlv> getComprehensionTlvs() {
        return mCompTlvs;
    }

    /**
     * Gets a tag id of the BER-TLV object.
     *
     * @return A tag integer.
     */
    public int getTag() {
        return mTag;
    }

    /**
     * Gets if the length of the BER-TLV object is valid
     *
     * @return if length valid
     */
     public boolean isLengthValid() {
         return mLengthValid;
     }

    /**
     * Decodes a BER-TLV object from a byte array.
     *
     * @param data A byte array to decode from
     * @return A BER-TLV object decoded
     * @throws ResultException
     */
    public static BerTlv decode(byte[] data) throws ResultException {
        int curIndex = 0;
        int endIndex = data.length;
        int tag, length = 0;
        boolean isLengthValid = true;

        try {
            /* tag */
            tag = data[curIndex++] & 0xff;
            if (tag == BER_PROACTIVE_COMMAND_TAG) {
                /* length */
                int temp = data[curIndex++] & 0xff;
                if (temp < 0x80) {
                    length = temp;
                } else if (temp == 0x81) {
                    temp = data[curIndex++] & 0xff;
                    if (temp < 0x80) {
                        throw new ResultException(
                                ResultCode.CMD_DATA_NOT_UNDERSTOOD,
                                "length < 0x80 length=" + Integer.toHexString(length) +
                                " curIndex=" + curIndex + " endIndex=" + endIndex);

                    }
                    length = temp;
                } else {
                    throw new ResultException(
                            ResultCode.CMD_DATA_NOT_UNDERSTOOD,
                            "Expected first byte to be length or a length tag and < 0x81" +
                            " byte= " + Integer.toHexString(temp) + " curIndex=" + curIndex +
                            " endIndex=" + endIndex);
                }
            } else {
                if (ComprehensionTlvTag.COMMAND_DETAILS.value() == (tag & ~0x80)) {
                    tag = BER_UNKNOWN_TAG;
                    curIndex = 0;
                }
            }
        } catch (IndexOutOfBoundsException e) {
            throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING,
                    "IndexOutOfBoundsException " +
                    " curIndex=" + curIndex + " endIndex=" + endIndex);
        } catch (ResultException e) {
            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD, e.explanation());
        }

        /* COMPREHENSION-TLVs */
        if (endIndex - curIndex < length) {
            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD,
                    "Command had extra data endIndex=" + endIndex + " curIndex=" + curIndex +
                    " length=" + length);
        }

        List<ComprehensionTlv> ctlvs = ComprehensionTlv.decodeMany(data,
                curIndex);

        if (tag == BER_PROACTIVE_COMMAND_TAG) {
            int totalLength = 0;
            for (ComprehensionTlv item : ctlvs) {
                int itemLength = item.getLength();
                if (itemLength >= 0x80 && itemLength <= 0xFF) {
                    totalLength += itemLength + 3; //3: 'tag'(1 byte) and 'length'(2 bytes).
                } else if (itemLength >= 0 && itemLength < 0x80) {
                    totalLength += itemLength + 2; //2: 'tag'(1 byte) and 'length'(1 byte).
                } else {
                    isLengthValid = false;
                    break;
                }
            }

            // According to 3gpp11.14, chapter 6.10.6 "Length errors",

            // If the total lengths of the SIMPLE-TLV data objects are not
            // consistent with the length given in the BER-TLV data object,
            // then the whole BER-TLV data object shall be rejected. The
            // result field in the TERMINAL RESPONSE shall have the error
            // condition "Command data not understood by ME".
            if (length != totalLength) {
                isLengthValid = false;
            }
        }

        return new BerTlv(tag, ctlvs, isLengthValid);
    }
}
