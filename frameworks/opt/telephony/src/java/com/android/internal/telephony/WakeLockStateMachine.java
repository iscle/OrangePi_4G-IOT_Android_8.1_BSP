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

package com.android.internal.telephony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Message;
import android.os.PowerManager;
import android.telephony.Rlog;

import com.android.internal.util.State;
import com.android.internal.util.StateMachine;

/**
 * Generic state machine for handling messages and waiting for ordered broadcasts to complete.
 * Subclasses implement {@link #handleSmsMessage}, which returns true to transition into waiting
 * state, or false to remain in idle state. The wakelock is acquired on exit from idle state,
 * and is released a few seconds after returning to idle state, or immediately upon calling
 * {@link #quit}.
 */
public abstract class WakeLockStateMachine extends StateMachine {
    protected static final boolean DBG = true;    // TODO: change to false

    private final PowerManager.WakeLock mWakeLock;

    /** New message to process. */
    public static final int EVENT_NEW_SMS_MESSAGE = 1;

    /** Result receiver called for current cell broadcast. */
    protected static final int EVENT_BROADCAST_COMPLETE = 2;

    /** Release wakelock after a short timeout when returning to idle state. */
    static final int EVENT_RELEASE_WAKE_LOCK = 3;

    static final int EVENT_UPDATE_PHONE_OBJECT = 4;

    protected Phone mPhone;

    protected Context mContext;

    /** Wakelock release delay when returning to idle state. */
    private static final int WAKE_LOCK_TIMEOUT = 3000;

    // MTK-START
    // Change the attribute and remove keyword final for sub class
    protected DefaultState mDefaultState = new DefaultState();
    protected IdleState mIdleState = new IdleState();
    protected WaitingState mWaitingState = new WaitingState();
    // MTK-END

    protected WakeLockStateMachine(String debugTag, Context context, Phone phone) {
        super(debugTag);

        mContext = context;
        mPhone = phone;

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, debugTag);
        mWakeLock.acquire();    // wake lock released after we enter idle state

        addState(mDefaultState);
        addState(mIdleState, mDefaultState);
        addState(mWaitingState, mDefaultState);
        setInitialState(mIdleState);
    }

    // MTK-START
    // Add dummy constructor for sub class
    protected WakeLockStateMachine(String debugTag, Context context, Phone phone,
            Object dummy) {
        super(debugTag);

        mContext = context;
        mPhone = phone;

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, debugTag);
        mWakeLock.acquire();    // wake lock released after we enter idle state
    }
    // MTK-END

    public void updatePhoneObject(Phone phone) {
        sendMessage(EVENT_UPDATE_PHONE_OBJECT, phone);
    }

    /**
     * Tell the state machine to quit after processing all messages.
     */
    public final void dispose() {
        quit();
    }

    @Override
    protected void onQuitting() {
        // fully release the wakelock
        while (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
    }

    /**
     * Send a message with the specified object for {@link #handleSmsMessage}.
     * @param obj the object to pass in the msg.obj field
     */
    public final void dispatchSmsMessage(Object obj) {
        sendMessage(EVENT_NEW_SMS_MESSAGE, obj);
    }

    /**
     * This parent state throws an exception (for debug builds) or prints an error for unhandled
     * message types.
     */
    // MTK-START
    // Modification for sub class
    public class DefaultState extends State {
    // MTK-END
        @Override
        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case EVENT_UPDATE_PHONE_OBJECT: {
                    mPhone = (Phone) msg.obj;
                    log("updatePhoneObject: phone=" + mPhone.getClass().getSimpleName());
                    break;
                }
                default: {
                    String errorText = "processMessage: unhandled message type " + msg.what;
                    if (Build.IS_DEBUGGABLE) {
                        throw new RuntimeException(errorText);
                    } else {
                        loge(errorText);
                    }
                    break;
                }
            }
            return HANDLED;
        }
    }

    /**
     * Idle state delivers Cell Broadcasts to receivers. It acquires the wakelock, which is
     * released when the broadcast completes.
     */
    // MTK-START
    // Modification for sub class
    public class IdleState extends State {
    // MTK-END
        @Override
        public void enter() {
            sendMessageDelayed(EVENT_RELEASE_WAKE_LOCK, WAKE_LOCK_TIMEOUT);
        }

        @Override
        public void exit() {
            mWakeLock.acquire();
            if (DBG) log("acquired wakelock, leaving Idle state");
        }

        @Override
        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case EVENT_NEW_SMS_MESSAGE:
                    // transition to waiting state if we sent a broadcast
                    if (handleSmsMessage(msg)) {
                        transitionTo(mWaitingState);
                    }
                    return HANDLED;

                case EVENT_RELEASE_WAKE_LOCK:
                    mWakeLock.release();
                    if (DBG) {
                        if (mWakeLock.isHeld()) {
                            // this is okay as long as we call release() for every acquire()
                            log("mWakeLock is still held after release");
                        } else {
                            log("mWakeLock released");
                        }
                    }
                    return HANDLED;

                default:
                    return NOT_HANDLED;
            }
        }
    }

    /**
     * Waiting state waits for the result receiver to be called for the current cell broadcast.
     * In this state, any new cell broadcasts are deferred until we return to Idle state.
     */
    // MTK-START
    // Modification for sub class
    public class WaitingState extends State {
    // MTK-END
        @Override
        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case EVENT_NEW_SMS_MESSAGE:
                    log("deferring message until return to idle");
                    deferMessage(msg);
                    return HANDLED;

                case EVENT_BROADCAST_COMPLETE:
                    log("broadcast complete, returning to idle");
                    transitionTo(mIdleState);
                    return HANDLED;

                case EVENT_RELEASE_WAKE_LOCK:
                    mWakeLock.release();    // decrement wakelock from previous entry to Idle
                    if (!mWakeLock.isHeld()) {
                        // wakelock should still be held until 3 seconds after we enter Idle
                        loge("mWakeLock released while still in WaitingState!");
                    }
                    return HANDLED;

                default:
                    return NOT_HANDLED;
            }
        }
    }

    /**
     * Implemented by subclass to handle messages in {@link IdleState}.
     * @param message the message to process
     * @return true to transition to {@link WaitingState}; false to stay in {@link IdleState}
     */
    protected abstract boolean handleSmsMessage(Message message);

    /**
     * BroadcastReceiver to send message to return to idle state.
     */
    protected final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            sendMessage(EVENT_BROADCAST_COMPLETE);
        }
    };

    /**
     * Log with debug level.
     * @param s the string to log
     */
    @Override
    protected void log(String s) {
        Rlog.d(getName(), s);
    }

    /**
     * Log with error level.
     * @param s the string to log
     */
    @Override
    protected void loge(String s) {
        Rlog.e(getName(), s);
    }

    /**
     * Log with error level.
     * @param s the string to log
     * @param e is a Throwable which logs additional information.
     */
    @Override
    protected void loge(String s, Throwable e) {
        Rlog.e(getName(), s, e);
    }
}
