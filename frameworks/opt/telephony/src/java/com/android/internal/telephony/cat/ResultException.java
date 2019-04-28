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
 * Class for errors in the Result object.
 *
 * {@hide}
 */
public class ResultException extends CatException {
     // MTK-START
     protected /*private*/ ResultCode mResult;
     protected /*private*/ int mAdditionalInfo;
     protected /*private*/ String mExplanation;
     // MTK-END

    public ResultException(ResultCode result) {
        super();

        // ETSI TS 102 223, 8.12 -- For the general results '20', '21', '26',
        // '38', '39', '3A', '3C', and '3D', it is mandatory for the terminal
        // to provide a specific cause value as additional information.
        switch (result) {
            case TERMINAL_CRNTLY_UNABLE_TO_PROCESS:    // 0x20
            case NETWORK_CRNTLY_UNABLE_TO_PROCESS:     // 0x21
            case LAUNCH_BROWSER_ERROR:                 // 0x26
            case MULTI_CARDS_CMD_ERROR:                // 0x38
            case USIM_CALL_CONTROL_PERMANENT:          // 0x39
            case BIP_ERROR:                            // 0x3a
            case FRAMES_ERROR:                         // 0x3c
            case MMS_ERROR:                            // 0x3d
                throw new AssertionError(
                        "For result code, " + result +
                        ", additional information must be given!");
            default:
                break;
        }

        mResult = result;
        mAdditionalInfo = -1;
        mExplanation = "";
    }

    public ResultException(ResultCode result, String explanation) {
        this(result);
        mExplanation = explanation;
    }

    public ResultException(ResultCode result, int additionalInfo) {
        this(result);

        if (additionalInfo < 0) {
            throw new AssertionError(
                    "Additional info must be greater than zero!");
        }

        mAdditionalInfo = additionalInfo;
    }

    public ResultException(ResultCode result, int additionalInfo, String explanation) {
        this(result, additionalInfo);
        mExplanation = explanation;
    }

    public ResultCode result() {
        return mResult;
    }

    public boolean hasAdditionalInfo() {
        return mAdditionalInfo >= 0;
    }

    public int additionalInfo() {
        return mAdditionalInfo;
    }

    public String explanation() {
        return mExplanation;
    }

    @Override
    public String toString() {
        return "result=" + mResult + " additionalInfo=" + mAdditionalInfo +
                " explantion=" + mExplanation;
    }
}
