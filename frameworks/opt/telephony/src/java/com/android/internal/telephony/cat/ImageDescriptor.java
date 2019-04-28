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

/**
 * {@hide}
 */
public class ImageDescriptor {
    // members
    // MTK-START
    public int mWidth;
    public  int mHeight;
    public int mCodingScheme;
    public int mImageId;
    public int mHighOffset;
    public int mLowOffset;
    public int mLength;

    // constants
    public static final int CODING_SCHEME_BASIC = 0x11;
    public static final int CODING_SCHEME_COLOUR = 0x21;
   // MTK-END
    // public static final int ID_LENGTH = 9;
    // ID_LENGTH substituted by IccFileHandlerBase.GET_RESPONSE_EF_IMG_SIZE_BYTES

    ImageDescriptor() {
        mWidth = 0;
        mHeight = 0;
        mCodingScheme = 0;
        mImageId = 0;
        mHighOffset = 0;
        mLowOffset = 0;
        mLength = 0;
    }

    /**
     * Extract descriptor information about image instance.
     *
     * @param rawData
     * @param valueIndex
     * @return ImageDescriptor
     */
    // MTK-START
    public static ImageDescriptor parse(byte[] rawData, int valueIndex) {
    // MTK-END
        ImageDescriptor d = new ImageDescriptor();
        try {
            d.mWidth = rawData[valueIndex++] & 0xff;
            d.mHeight = rawData[valueIndex++] & 0xff;
            d.mCodingScheme = rawData[valueIndex++] & 0xff;

            // parse image id
            d.mImageId = (rawData[valueIndex++] & 0xff) << 8;
            d.mImageId |= rawData[valueIndex++] & 0xff;
            // parse offset
            d.mHighOffset = (rawData[valueIndex++] & 0xff); // high byte offset
            d.mLowOffset = rawData[valueIndex++] & 0xff; // low byte offset

            d.mLength = ((rawData[valueIndex++] & 0xff) << 8 | (rawData[valueIndex++] & 0xff));
            CatLog.d("ImageDescriptor", "parse; Descriptor : " + d.mWidth + ", " + d.mHeight +
                    ", " + d.mCodingScheme + ", 0x" + Integer.toHexString(d.mImageId) + ", " +
                    d.mHighOffset + ", " + d.mLowOffset + ", " + d.mLength);
        } catch (IndexOutOfBoundsException e) {
            CatLog.d("ImageDescriptor", "parse; failed parsing image descriptor");
            d = null;
        }
        return d;
    }
}
