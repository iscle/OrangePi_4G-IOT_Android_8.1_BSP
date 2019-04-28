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

package com.android.internal.telephony.dataconnection;

import android.content.Intent;
import android.telephony.Rlog;

/**
 * A package visible class for supporting testing failing bringUp commands. This
 * saves the parameters from a action_fail_bringup intent. See
 * {@link DataConnection#doOnConnect} and {@see DcTesterFailBringUpAll} for more info.
 */
public class DcFailBringUp {
    private static final String LOG_TAG = "DcFailBringUp";
    private static final boolean DBG = true;

    static final String INTENT_BASE = DataConnection.class.getPackage().getName();

    static final String ACTION_FAIL_BRINGUP = "action_fail_bringup";

    // counter with its --ei option name and default value
    static final String COUNTER = "counter";
    static final int DEFAULT_COUNTER = 2;
    public int mCounter;

    // failCause with its --ei option name and default value
    static final String FAIL_CAUSE = "fail_cause";
    static final DcFailCause DEFAULT_FAIL_CAUSE = DcFailCause.ERROR_UNSPECIFIED;
    public DcFailCause mFailCause;

    // suggestedRetryTime with its --ei option name and default value
    static final String SUGGESTED_RETRY_TIME = "suggested_retry_time";
    static final int DEFAULT_SUGGESTED_RETRY_TIME = -1;
    public int mSuggestedRetryTime;

    // Get the Extra Intent parameters
    void saveParameters(Intent intent, String s) {
        if (DBG) log(s + ".saveParameters: action=" + intent.getAction());
        mCounter = intent.getIntExtra(COUNTER, DEFAULT_COUNTER);
        mFailCause = DcFailCause.fromInt(
                intent.getIntExtra(FAIL_CAUSE, DEFAULT_FAIL_CAUSE.getErrorCode()));
        mSuggestedRetryTime =
                intent.getIntExtra(SUGGESTED_RETRY_TIME, DEFAULT_SUGGESTED_RETRY_TIME);
        if (DBG) {
            log(s + ".saveParameters: " + this);
        }
    }

    public void saveParameters(int counter, int failCause, int suggestedRetryTime) {
        mCounter = counter;
        mFailCause = DcFailCause.fromInt(failCause);
        mSuggestedRetryTime = suggestedRetryTime;
    }

    @Override
    public String toString() {
        return "{mCounter=" + mCounter +
                " mFailCause=" + mFailCause +
                " mSuggestedRetryTime=" + mSuggestedRetryTime + "}";

    }

    private static void log(String s) {
        Rlog.d(LOG_TAG, s);
    }
}
