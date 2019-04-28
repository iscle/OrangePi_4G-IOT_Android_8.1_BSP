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
 * Copyright (C) 2007-2008 Esmertec AG.
 * Copyright (C) 2007-2008 The Android Open Source Project
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

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

/**
 * Encoded-string-value = Text-string | Value-length Char-set Text-string
 */
public class EncodedStringValue implements Cloneable {
    private static final String TAG = "EncodedStringValue";
    private static final boolean DEBUG = false;
    private static final boolean LOCAL_LOGV = false;

   /*MTK Change access type*/
    /**
     * The Char-set value.
     */
    protected int mCharacterSet;

    /*MTK Change access type*/
    /**
     * The Text-string value.
     */
    protected byte[] mData;

    /**
     * Constructor.
     *
     * @param charset the Char-set value
     * @param data the Text-string value
     * @throws NullPointerException if Text-string value is null.
     */
    public EncodedStringValue(int charset, byte[] data) {
        // TODO: CharSet needs to be validated against MIBEnum.
        if(null == data) {
            throw new NullPointerException("EncodedStringValue: Text-string is null.");
        }

        mCharacterSet = charset;
        mData = new byte[data.length];
        System.arraycopy(data, 0, mData, 0, data.length);
    }

    /**
     * Constructor.
     *
     * @param data the Text-string value
     * @throws NullPointerException if Text-string value is null.
     */
    public EncodedStringValue(byte[] data) {
        this(CharacterSets.DEFAULT_CHARSET, data);
    }

    public EncodedStringValue(String data) {
        try {
            mData = data.getBytes(CharacterSets.DEFAULT_CHARSET_NAME);
            mCharacterSet = CharacterSets.DEFAULT_CHARSET;
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Default encoding must be supported.", e);
        }
    }

    /**
     * Get Char-set value.
     *
     * @return the value
     */
    public int getCharacterSet() {
        return mCharacterSet;
    }

    /**
     * Set Char-set value.
     *
     * @param charset the Char-set value
     */
    public void setCharacterSet(int charset) {
        // TODO: CharSet needs to be validated against MIBEnum.
        mCharacterSet = charset;
    }

    /**
     * Get Text-string value.
     *
     * @return the value
     */
    public byte[] getTextString() {
        byte[] byteArray = new byte[mData.length];

        System.arraycopy(mData, 0, byteArray, 0, mData.length);
        return byteArray;
    }

    /**
     * Set Text-string value.
     *
     * @param textString the Text-string value
     * @throws NullPointerException if Text-string value is null.
     */
    public void setTextString(byte[] textString) {
        if(null == textString) {
            throw new NullPointerException("EncodedStringValue: Text-string is null.");
        }

        mData = new byte[textString.length];
        System.arraycopy(textString, 0, mData, 0, textString.length);
    }

    /**
     * Convert this object to a {@link java.lang.String}. If the encoding of
     * the EncodedStringValue is null or unsupported, it will be
     * treated as iso-8859-1 encoding.
     *
     * @return The decoded String.
     */
    public String getString()  {
        if (CharacterSets.ANY_CHARSET == mCharacterSet) {
            return new String(mData); // system default encoding.
        } else {
            try {
                String name = CharacterSets.getMimeName(mCharacterSet);
                return new String(mData, name);
            } catch (UnsupportedEncodingException e) {
            	if (LOCAL_LOGV) {
            		Log.v(TAG, e.getMessage(), e);
            	}
            	try {
                    return new String(mData, CharacterSets.MIMENAME_ISO_8859_1);
                } catch (UnsupportedEncodingException e2) {
                    return new String(mData); // system default encoding.
                }
            }
        }
    }

    /**
     * Append to Text-string.
     *
     * @param textString the textString to append
     * @throws NullPointerException if the text String is null
     *                      or an IOException occured.
     */
    public void appendTextString(byte[] textString) {
        if(null == textString) {
            throw new NullPointerException("Text-string is null.");
        }

        if(null == mData) {
            mData = new byte[textString.length];
            System.arraycopy(textString, 0, mData, 0, textString.length);
        } else {
            ByteArrayOutputStream newTextString = new ByteArrayOutputStream();
            try {
                newTextString.write(mData);
                newTextString.write(textString);
            } catch (IOException e) {
                e.printStackTrace();
                throw new NullPointerException(
                        "appendTextString: failed when write a new Text-string");
            }

            mData = newTextString.toByteArray();
        }
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#clone()
     */
    @Override
    public Object clone() throws CloneNotSupportedException {
        super.clone();
        int len = mData.length;
        byte[] dstBytes = new byte[len];
        System.arraycopy(mData, 0, dstBytes, 0, len);

        try {
            return new EncodedStringValue(mCharacterSet, dstBytes);
        } catch (Exception e) {
            Log.e(TAG, "failed to clone an EncodedStringValue: " + this);
            e.printStackTrace();
            throw new CloneNotSupportedException(e.getMessage());
        }
    }

    /**
     * Split this encoded string around matches of the given pattern.
     *
     * @param pattern the delimiting pattern
     * @return the array of encoded strings computed by splitting this encoded
     *         string around matches of the given pattern
     */
    public EncodedStringValue[] split(String pattern) {
        String[] temp = getString().split(pattern);
        EncodedStringValue[] ret = new EncodedStringValue[temp.length];
        for (int i = 0; i < ret.length; ++i) {
            try {
                ret[i] = new EncodedStringValue(mCharacterSet,
                        temp[i].getBytes());
            } catch (NullPointerException e) {
                // Can't arrive here
                return null;
            }
        }
        return ret;
    }

    /**
     * Extract an EncodedStringValue[] from a given String.
     */
    public static EncodedStringValue[] extract(String src) {
        String[] values = src.split(";");

        ArrayList<EncodedStringValue> list = new ArrayList<EncodedStringValue>();
        for (int i = 0; i < values.length; i++) {
            if (values[i].length() > 0) {
                list.add(new EncodedStringValue(values[i]));
            }
        }

        int len = list.size();
        if (len > 0) {
            return list.toArray(new EncodedStringValue[len]);
        } else {
            return null;
        }
    }

    /**
     * Concatenate an EncodedStringValue[] into a single String.
     */
    public static String concat(EncodedStringValue[] addr) {
        StringBuilder sb = new StringBuilder();
        int maxIndex = addr.length - 1;
        for (int i = 0; i <= maxIndex; i++) {
            sb.append(addr[i].getString());
            if (i < maxIndex) {
                sb.append(";");
            }
        }

        return sb.toString();
    }

    public static EncodedStringValue copy(EncodedStringValue value) {
        if (value == null) {
            return null;
        }

        return new EncodedStringValue(value.mCharacterSet, value.mData);
    }
    
    public static EncodedStringValue[] encodeStrings(String[] array) {
        int count = array.length;
        if (count > 0) {
            EncodedStringValue[] encodedArray = new EncodedStringValue[count];
            for (int i = 0; i < count; i++) {
                encodedArray[i] = new EncodedStringValue(array[i]);
            }
            return encodedArray;
        }
        return null;
    }
}
