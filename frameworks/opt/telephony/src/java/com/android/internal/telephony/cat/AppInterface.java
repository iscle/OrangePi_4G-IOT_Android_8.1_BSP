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

import android.content.ComponentName;

/**
 * Interface for communication between STK App and CAT Telephony
 *
 * {@hide}
 */
public interface AppInterface {

    /*
     * Intent's actions which are broadcasted by the Telephony once a new CAT
     * proactive command, session end, ALPHA during STK CC arrive.
     */
    public static final String CAT_CMD_ACTION =
                                    "com.android.internal.stk.command";
    public static final String CAT_SESSION_END_ACTION =
                                    "com.android.internal.stk.session_end";
    public static final String CAT_ALPHA_NOTIFY_ACTION =
                                    "com.android.internal.stk.alpha_notify";

    //This is used to send ALPHA string from card to STK App.
    public static final String ALPHA_STRING = "alpha_string";

    // This is used to send refresh-result when MSG_ID_ICC_REFRESH is received.
    public static final String REFRESH_RESULT = "refresh_result";
    //This is used to send card status from card to STK App.
    public static final String CARD_STATUS = "card_status";
    //Intent's actions are broadcasted by Telephony once IccRefresh occurs.
    public static final String CAT_ICC_STATUS_CHANGE =
                                    "com.android.internal.stk.icc_status_change";

    // Permission required by STK command receiver
    public static final String STK_PERMISSION = "android.permission.RECEIVE_STK_COMMANDS";

    // Only forwards cat broadcast to the system default stk app
    public static ComponentName getDefaultSTKApplication() {
        return ComponentName.unflattenFromString("com.android.stk/.StkCmdReceiver");
    }

    /*
     * Callback function from app to telephony to pass a result code and user's
     * input back to the ICC.
     */
    void onCmdResponse(CatResponseMessage resMsg);

    /*
     * Enumeration for representing "Type of Command" of proactive commands.
     * Those are the only commands which are supported by the Telephony. Any app
     * implementation should support those.
     * Refer to ETSI TS 102.223 section 9.4
     */
    public static enum CommandType {
        DISPLAY_TEXT(0x21),
        GET_INKEY(0x22),
        GET_INPUT(0x23),
        LAUNCH_BROWSER(0x15),
        PLAY_TONE(0x20),
        REFRESH(0x01),
        SELECT_ITEM(0x24),
        SEND_SS(0x11),
        SEND_USSD(0x12),
        SEND_SMS(0x13),
        SEND_DTMF(0x14),
        SET_UP_EVENT_LIST(0x05),
        SET_UP_IDLE_MODE_TEXT(0x28),
        SET_UP_MENU(0x25),
        SET_UP_CALL(0x10),
        PROVIDE_LOCAL_INFORMATION(0x26),
        OPEN_CHANNEL(0x40),
        CLOSE_CHANNEL(0x41),
        RECEIVE_DATA(0x42),
        SEND_DATA(0x43),
        GET_CHANNEL_STATUS(0x44),
        // MTK-START
        /* Add all proactive type for extend */
        /**
         * Proactive command MORE_TIME.
         */
        MORE_TIME(0x02),
        /**
         * Proactive command POLL_INTERVAL.
         */
        POLL_INTERVAL(0x03),
        /**
         * Proactive command POLLING_OFF.
         */
        POLLING_OFF(0x04),
        /**
         * Proactive command TIMER_MANAGEMENT.
         */
        TIMER_MANAGEMENT(0x27),
        /**
         * Proactive command PERFORM_CARD_APDU.
         */
        PERFORM_CARD_APDU(0x30),
        /**
         * Proactive command POWER_ON_CARD.
         */
        POWER_ON_CARD(0x31),
        /**
         * Proactive command POWER_OFF_CARD.
         */
        POWER_OFF_CARD(0x32),
        /**
         * Proactive command GET_READER_STATUS.
         */
        GET_READER_STATUS(0x33),
        /**
         * Proactive command RUN_AT_COMMAND.
         */
        RUN_AT_COMMAND(0x34),
        /**
         * Proactive command LANGUAGE_NOTIFICATION.
         */
        LANGUAGE_NOTIFICATION(0x35),
        /**
         * Proactive command SERVICE_SEARCH.
         */
        SERVICE_SEARCH(0x45),
        /**
         * Proactive command GET_SERVICE_INFORMATION.
         */
        GET_SERVICE_INFORMATION(0x46),
        /**
         * Proactive command DECLARE_SERVICE.
         */
        DECLARE_SERVICE(0x47),
        /**
         * Proactive command SET_FRAME.
         */
        SET_FRAME(0x50),
        /**
         * Proactive command GET_FRAME_STATUS.
         */
        GET_FRAME_STATUS(0x51),
        /**
         * Proactive command RETRIEVE_MULTIMEDIA_MESSAGE.
         */
        RETRIEVE_MULTIMEDIA_MESSAGE(0x60),
        /**
         * Proactive command SUBMIT_MULTIMEDIA_MESSAGE.
         */
        SUBMIT_MULTIMEDIA_MESSAGE(0x61),
        /**
         * Proactive command DISPLAY_MULTIMEDIA_MESSAGE.
         */
        DISPLAY_MULTIMEDIA_MESSAGE(0x62),
        /**
         * Proactive command ACTIVATE.
         */
        ACTIVATE(0x70);
        // MTK-END
        private int mValue;

        CommandType(int value) {
            mValue = value;
        }

        public int value() {
            return mValue;
        }

        /**
         * Create a CommandType object.
         *
         * @param value Integer value to be converted to a CommandType object.
         * @return CommandType object whose "Type of Command" value is {@code
         *         value}. If no CommandType object has that value, null is
         *         returned.
         */
        public static CommandType fromInt(int value) {
            for (CommandType e : CommandType.values()) {
                if (e.mValue == value) {
                    return e;
                }
            }
            return null;
        }
    }
}
