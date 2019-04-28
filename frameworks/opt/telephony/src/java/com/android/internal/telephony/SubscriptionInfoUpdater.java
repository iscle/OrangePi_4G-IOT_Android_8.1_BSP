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
* Copyright (C) 2014 The Android Open Source Project
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

import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.UserSwitchObserver;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.IPackageManager;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.IRemoteCallback;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.service.euicc.EuiccProfileInfo;
import android.service.euicc.EuiccService;
import android.service.euicc.GetEuiccProfileInfoListResult;
import android.telephony.CarrierConfigManager;
import android.telephony.Rlog;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.UiccAccessRule;
import android.telephony.euicc.EuiccManager;
import android.text.TextUtils;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.euicc.EuiccController;
import com.android.internal.telephony.uicc.IccCardProxy;
import com.android.internal.telephony.uicc.IccConstants;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IccUtils;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 *@hide
 */
public class SubscriptionInfoUpdater extends Handler {
    private static final String LOG_TAG = "SubscriptionInfoUpdater";
    //MTK-START
    protected /*private*/ static final int PROJECT_SIM_NUM
            = TelephonyManager.getDefault().getPhoneCount();

    protected /*private*/ static final int EVENT_SIM_LOCKED_QUERY_ICCID_DONE = 1;
    protected /*private*/ static final int EVENT_GET_NETWORK_SELECTION_MODE_DONE = 2;
    protected /*private*/ static final int EVENT_SIM_LOADED = 3;
    protected /*private*/ static final int EVENT_SIM_ABSENT = 4;
    protected /*private*/ static final int EVENT_SIM_LOCKED = 5;
    protected /*private*/ static final int EVENT_SIM_IO_ERROR = 6;
    protected /*private*/ static final int EVENT_SIM_UNKNOWN = 7;
    protected /*private*/ static final int EVENT_SIM_RESTRICTED = 8;
    // MTK-END
    private static final int EVENT_REFRESH_EMBEDDED_SUBSCRIPTIONS = 9;

    private static final String ICCID_STRING_FOR_NO_SIM = "";
    /**
     *  int[] sInsertSimState maintains all slots' SIM inserted status currently,
     *  it may contain 4 kinds of values:
     *    SIM_NOT_INSERT : no SIM inserted in slot i now
     *    SIM_CHANGED    : a valid SIM insert in slot i and is different SIM from last time
     *                     it will later become SIM_NEW or SIM_REPOSITION during update procedure
     *    SIM_NOT_CHANGE : a valid SIM insert in slot i and is the same SIM as last time
     *    SIM_NEW        : a valid SIM insert in slot i and is a new SIM
     *    SIM_REPOSITION : a valid SIM insert in slot i and is inserted in different slot last time
     *    positive integer #: index to distinguish SIM cards with the same IccId
     */
    public static final int SIM_NOT_CHANGE = 0;
    public static final int SIM_CHANGED    = -1;
    public static final int SIM_NEW        = -2;
    public static final int SIM_REPOSITION = -3;
    public static final int SIM_NOT_INSERT = -99;

    public static final int STATUS_NO_SIM_INSERTED = 0x00;
    public static final int STATUS_SIM1_INSERTED = 0x01;
    public static final int STATUS_SIM2_INSERTED = 0x02;
    public static final int STATUS_SIM3_INSERTED = 0x04;
    public static final int STATUS_SIM4_INSERTED = 0x08;

    // Key used to read/write the current IMSI. Updated on SIM_STATE_CHANGED - LOADED.
    public static final String CURR_SUBID = "curr_subid";

    // MTK-START
    protected /*private*/ static Phone[] mPhone;
    protected /*private*/ static Context mContext = null;
    protected /*private*/ static String mIccId[] = new String[PROJECT_SIM_NUM];
    protected /*private*/ static int[] mInsertSimState = new int[PROJECT_SIM_NUM];
    protected /*private*/ SubscriptionManager mSubscriptionManager = null;
    // MTK-END
    private EuiccManager mEuiccManager;
    // MTK-START
    protected /*private*/ IPackageManager mPackageManager;
    // MTK-END

    // The current foreground user ID.
    // MTK-START
    protected /*private*/ int mCurrentlyActiveUserId;
    // MTK-END
    private CarrierServiceBindHelper mCarrierServiceBindHelper;

    public SubscriptionInfoUpdater(
            Looper looper, Context context, Phone[] phone, CommandsInterface[] ci) {
        super(looper);
        logd("Constructor invoked");

        mContext = context;
        mPhone = phone;
        mSubscriptionManager = SubscriptionManager.from(mContext);
        mEuiccManager = (EuiccManager) mContext.getSystemService(Context.EUICC_SERVICE);
        mPackageManager = IPackageManager.Stub.asInterface(ServiceManager.getService("package"));

        IntentFilter intentFilter = new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        intentFilter.addAction(IccCardProxy.ACTION_INTERNAL_SIM_STATE_CHANGED);
        mContext.registerReceiver(sReceiver, intentFilter);

        mCarrierServiceBindHelper = new CarrierServiceBindHelper(mContext);
        initializeCarrierApps();
    }

    private void initializeCarrierApps() {
        // Initialize carrier apps:
        // -Now (on system startup)
        // -Whenever new carrier privilege rules might change (new SIM is loaded)
        // -Whenever we switch to a new user
        mCurrentlyActiveUserId = 0;
        try {
            ActivityManager.getService().registerUserSwitchObserver(new UserSwitchObserver() {
                @Override
                public void onUserSwitching(int newUserId, IRemoteCallback reply)
                        throws RemoteException {
                    mCurrentlyActiveUserId = newUserId;
                    CarrierAppUtils.disableCarrierAppsUntilPrivileged(mContext.getOpPackageName(),
                            mPackageManager, TelephonyManager.getDefault(),
                            mContext.getContentResolver(), mCurrentlyActiveUserId);

                    if (reply != null) {
                        try {
                            reply.sendResult(null);
                        } catch (RemoteException e) {
                        }
                    }
                }
            }, LOG_TAG);
            mCurrentlyActiveUserId = ActivityManager.getService().getCurrentUser().id;
        } catch (RemoteException e) {
            logd("Couldn't get current user ID; guessing it's 0: " + e.getMessage());
        }
        CarrierAppUtils.disableCarrierAppsUntilPrivileged(mContext.getOpPackageName(),
                mPackageManager, TelephonyManager.getDefault(), mContext.getContentResolver(),
                mCurrentlyActiveUserId);
    }

    // MTK-START
    protected /*private*/ final BroadcastReceiver sReceiver = new  BroadcastReceiver() {
    // MTK-END
        @Override
        public void onReceive(Context context, Intent intent) {
            logd("[Receiver]+");
            String action = intent.getAction();
            logd("Action: " + action);

            if (!action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED) &&
                    !action.equals(IccCardProxy.ACTION_INTERNAL_SIM_STATE_CHANGED)) {
                return;
            }

            int slotIndex = intent.getIntExtra(PhoneConstants.PHONE_KEY,
                    SubscriptionManager.INVALID_SIM_SLOT_INDEX);
            logd("slotIndex: " + slotIndex);
            if (!SubscriptionManager.isValidSlotIndex(slotIndex)) {
                logd("ACTION_SIM_STATE_CHANGED contains invalid slotIndex: " + slotIndex);
                return;
            }

            String simStatus = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
            logd("simStatus: " + simStatus);

            // TODO: All of the below should be converted to ACTION_INTERNAL_SIM_STATE_CHANGED to
            // ensure that the SubscriptionInfo is updated before the broadcasts are sent out.
            if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
                if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(simStatus)) {
                    sendMessage(obtainMessage(EVENT_SIM_ABSENT, slotIndex, -1));
                } else if (IccCardConstants.INTENT_VALUE_ICC_UNKNOWN.equals(simStatus)) {
                    sendMessage(obtainMessage(EVENT_SIM_UNKNOWN, slotIndex, -1));
                } else if (IccCardConstants.INTENT_VALUE_ICC_CARD_IO_ERROR.equals(simStatus)) {
                    sendMessage(obtainMessage(EVENT_SIM_IO_ERROR, slotIndex, -1));
                } else if (IccCardConstants.INTENT_VALUE_ICC_CARD_RESTRICTED.equals(simStatus)) {
                    sendMessage(obtainMessage(EVENT_SIM_RESTRICTED, slotIndex, -1));
                } else if (IccCardConstants.INTENT_VALUE_ICC_NOT_READY.equals(simStatus)) {
                    // ICC_NOT_READY is a terminal state for an eSIM on the boot profile. At this
                    // phase, the subscription list is accessible.
                    // TODO(b/64216093): Clean up this special case, likely by treating NOT_READY
                    // as equivalent to ABSENT, once the rest of the system can handle it. Currently
                    // this breaks SystemUI which shows a "No SIM" icon.
                    sendEmptyMessage(EVENT_REFRESH_EMBEDDED_SUBSCRIPTIONS);
                } else {
                    logd("Ignoring simStatus: " + simStatus);
                }
            } else if (action.equals(IccCardProxy.ACTION_INTERNAL_SIM_STATE_CHANGED)) {
                if (IccCardConstants.INTENT_VALUE_ICC_LOCKED.equals(simStatus)) {
                    String reason = intent.getStringExtra(
                        IccCardConstants.INTENT_KEY_LOCKED_REASON);
                    sendMessage(obtainMessage(EVENT_SIM_LOCKED, slotIndex, -1, reason));
                } else if (IccCardConstants.INTENT_VALUE_ICC_LOADED.equals(simStatus)) {
                    sendMessage(obtainMessage(EVENT_SIM_LOADED, slotIndex, -1));
                } else {
                    logd("Ignoring simStatus: " + simStatus);
                }
            }
            logd("[Receiver]-");
        }
    };

    // MTK-START for BSP+
    protected /*private*/ boolean isAllIccIdQueryDone() {
    // MTK-END
        for (int i = 0; i < PROJECT_SIM_NUM; i++) {
            if (mIccId[i] == null) {
                logd("Wait for SIM" + (i + 1) + " IccId");
                return false;
            }
        }
        logd("All IccIds query complete");

        return true;
    }

    public void setDisplayNameForNewSub(String newSubName, int subId, int newNameSource) {
        SubscriptionInfo subInfo = mSubscriptionManager.getActiveSubscriptionInfo(subId);
        if (subInfo != null) {
            // overwrite SIM display name if it is not assigned by user
            int oldNameSource = subInfo.getNameSource();
            CharSequence oldSubName = subInfo.getDisplayName();
            logd("[setDisplayNameForNewSub] subId = " + subInfo.getSubscriptionId()
                    + ", oldSimName = " + oldSubName + ", oldNameSource = " + oldNameSource
                    + ", newSubName = " + newSubName + ", newNameSource = " + newNameSource);
            if (oldSubName == null ||
                (oldNameSource ==
                    SubscriptionManager.NAME_SOURCE_DEFAULT_SOURCE && newSubName != null) ||
                (oldNameSource == SubscriptionManager.NAME_SOURCE_SIM_SOURCE && newSubName != null
                        && !newSubName.equals(oldSubName))) {
                mSubscriptionManager.setDisplayName(newSubName, subInfo.getSubscriptionId(),
                        newNameSource);
            }
        } else {
            logd("SUB" + (subId + 1) + " SubInfo not created yet");
        }
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case EVENT_SIM_LOCKED_QUERY_ICCID_DONE: {
                AsyncResult ar = (AsyncResult)msg.obj;
                QueryIccIdUserObj uObj = (QueryIccIdUserObj) ar.userObj;
                int slotId = uObj.slotId;
                logd("handleMessage : <EVENT_SIM_LOCKED_QUERY_ICCID_DONE> SIM" + (slotId + 1));
                if (ar.exception == null) {
                    if (ar.result != null) {
                        byte[] data = (byte[])ar.result;
                        mIccId[slotId] = IccUtils.bcdToString(data, 0, data.length);
                    } else {
                        logd("Null ar");
                        mIccId[slotId] = ICCID_STRING_FOR_NO_SIM;
                    }
                } else {
                    mIccId[slotId] = ICCID_STRING_FOR_NO_SIM;
                    logd("Query IccId fail: " + ar.exception);
                }
                logd("sIccId[" + slotId + "] = " + mIccId[slotId]);
                if (isAllIccIdQueryDone()) {
                    updateSubscriptionInfoByIccId();
                }
                broadcastSimStateChanged(slotId, IccCardConstants.INTENT_VALUE_ICC_LOCKED,
                                         uObj.reason);
                if (!ICCID_STRING_FOR_NO_SIM.equals(mIccId[slotId])) {
                    updateCarrierServices(slotId, IccCardConstants.INTENT_VALUE_ICC_LOCKED);
                }
                break;
            }

            case EVENT_GET_NETWORK_SELECTION_MODE_DONE: {
                AsyncResult ar = (AsyncResult)msg.obj;
                Integer slotId = (Integer)ar.userObj;
                if (ar.exception == null && ar.result != null) {
                    int[] modes = (int[])ar.result;
                    if (modes[0] == 1) {  // Manual mode.
                        mPhone[slotId].setNetworkSelectionModeAutomatic(null);
                    }
                } else {
                    logd("EVENT_GET_NETWORK_SELECTION_MODE_DONE: error getting network mode.");
                }
                break;
            }

           case EVENT_SIM_LOADED:
                handleSimLoaded(msg.arg1);
                break;

            case EVENT_SIM_ABSENT:
                handleSimAbsent(msg.arg1);
                break;

            case EVENT_SIM_LOCKED:
                handleSimLocked(msg.arg1, (String) msg.obj);
                break;

            case EVENT_SIM_UNKNOWN:
                updateCarrierServices(msg.arg1, IccCardConstants.INTENT_VALUE_ICC_UNKNOWN);
                break;

            case EVENT_SIM_IO_ERROR:
                handleSimError(msg.arg1);
                break;

            case EVENT_SIM_RESTRICTED:
                updateCarrierServices(msg.arg1, IccCardConstants.INTENT_VALUE_ICC_CARD_RESTRICTED);
                break;

            case EVENT_REFRESH_EMBEDDED_SUBSCRIPTIONS:
                if (updateEmbeddedSubscriptions()) {
                    SubscriptionController.getInstance().notifySubscriptionInfoChanged();
                }
                if (msg.obj != null) {
                    ((Runnable) msg.obj).run();
                }
                break;

            default:
                logd("Unknown msg:" + msg.what);
        }
    }

    void requestEmbeddedSubscriptionInfoListRefresh(@Nullable Runnable callback) {
        sendMessage(obtainMessage(EVENT_REFRESH_EMBEDDED_SUBSCRIPTIONS, callback));
    }

    // MTK-START
    protected /*private*/ static class QueryIccIdUserObj {
    // MTK-END
        public String reason;
        public int slotId;

        // MTK-START
        public QueryIccIdUserObj(String reason, int slotId) {
        // MTK-END
            this.reason = reason;
            this.slotId = slotId;
        }
    };

    // MTK-START
    protected /*private*/ void handleSimLocked(int slotId, String reason) {
    // MTK-END
        if (mIccId[slotId] != null && mIccId[slotId].equals(ICCID_STRING_FOR_NO_SIM)) {
            logd("SIM" + (slotId + 1) + " hot plug in");
            mIccId[slotId] = null;
        }


        IccFileHandler fileHandler = mPhone[slotId].getIccCard() == null ? null :
                mPhone[slotId].getIccCard().getIccFileHandler();

        if (fileHandler != null) {
            String iccId = mIccId[slotId];
            if (iccId == null) {
                logd("Querying IccId");
                fileHandler.loadEFTransparent(IccConstants.EF_ICCID,
                        obtainMessage(EVENT_SIM_LOCKED_QUERY_ICCID_DONE,
                                new QueryIccIdUserObj(reason, slotId)));
            } else {
                logd("NOT Querying IccId its already set sIccid[" + slotId + "]=" + iccId);
                updateCarrierServices(slotId, IccCardConstants.INTENT_VALUE_ICC_LOCKED);
                broadcastSimStateChanged(slotId, IccCardConstants.INTENT_VALUE_ICC_LOCKED, reason);
            }
        } else {
            logd("sFh[" + slotId + "] is null, ignore");
        }
    }

    // MTK-START
    protected /*private*/ void handleSimLoaded(int slotId) {
    // MTK-END
        logd("handleSimLoaded: slotId: " + slotId);

        // The SIM should be loaded at this state, but it is possible in cases such as SIM being
        // removed or a refresh RESET that the IccRecords could be null. The right behavior is to
        // not broadcast the SIM loaded.
        IccRecords records = mPhone[slotId].getIccCard().getIccRecords();
        if (records == null) {  // Possibly a race condition.
            logd("handleSimLoaded: IccRecords null");
            return;
        }
        if (records.getIccId() == null) {
            logd("handleSimLoaded: IccID null");
            return;
        }
        mIccId[slotId] = records.getIccId();

        if (isAllIccIdQueryDone()) {
            updateSubscriptionInfoByIccId();
            int[] subIds = mSubscriptionManager.getActiveSubscriptionIdList();
            for (int subId : subIds) {
                TelephonyManager tm = TelephonyManager.getDefault();

                String operator = tm.getSimOperatorNumeric(subId);
                // MTK-START
                // AOSP issue, example: slot1 loaded, slot2 locked but iccid is ok that means
                // has add into db. So slot1 load will cause the slot2 runing the code in for loop
                // and broadcast slot2 loaded at last of for loop, but it is locked yet.
                // slotId = SubscriptionController.getInstance().getPhoneId(subId);
                int tempSlotId = SubscriptionManager.INVALID_PHONE_INDEX;
                tempSlotId = SubscriptionController.getInstance().getPhoneId(subId);
                if (tempSlotId != slotId) {
                    continue;
                }
                // MTK-END

                if (!TextUtils.isEmpty(operator)) {
                    if (subId == SubscriptionController.getInstance().getDefaultSubId()) {
                        MccTable.updateMccMncConfiguration(mContext, operator, false);
                    }
                    SubscriptionController.getInstance().setMccMnc(operator, subId);
                } else {
                    logd("EVENT_RECORDS_LOADED Operator name is null");
                }

                String msisdn = tm.getLine1Number(subId);
                ContentResolver contentResolver = mContext.getContentResolver();

                if (msisdn != null) {
                    ContentValues number = new ContentValues(1);
                    number.put(SubscriptionManager.NUMBER, msisdn);
                    contentResolver.update(SubscriptionManager.CONTENT_URI, number,
                            SubscriptionManager.UNIQUE_KEY_SUBSCRIPTION_ID + "="
                                    + Long.toString(subId), null);

                    // refresh Cached Active Subscription Info List
                    SubscriptionController.getInstance().refreshCachedActiveSubscriptionInfoList();
                }

                SubscriptionInfo subInfo = mSubscriptionManager.getActiveSubscriptionInfo(subId);
                String nameToSet;
                String simCarrierName = tm.getSimOperatorName(subId);
                ContentValues name = new ContentValues(1);

                if (subInfo != null && subInfo.getNameSource() !=
                        SubscriptionManager.NAME_SOURCE_USER_INPUT) {
                    if (!TextUtils.isEmpty(simCarrierName)) {
                        nameToSet = simCarrierName;
                    } else {
                        nameToSet = "CARD " + Integer.toString(slotId + 1);
                    }
                    name.put(SubscriptionManager.DISPLAY_NAME, nameToSet);
                    logd("sim name = " + nameToSet);
                    contentResolver.update(SubscriptionManager.CONTENT_URI, name,
                            SubscriptionManager.UNIQUE_KEY_SUBSCRIPTION_ID
                                    + "=" + Long.toString(subId), null);

                    // refresh Cached Active Subscription Info List
                    SubscriptionController.getInstance().refreshCachedActiveSubscriptionInfoList();
                }

                /* Update preferred network type and network selection mode on SIM change.
                 * Storing last subId in SharedPreference for now to detect SIM change. */
                SharedPreferences sp =
                        PreferenceManager.getDefaultSharedPreferences(mContext);
                int storedSubId = sp.getInt(CURR_SUBID + slotId, -1);

                if (storedSubId != subId) {
                    int networkType = RILConstants.PREFERRED_NETWORK_MODE;

                    // Set the modem network mode
                    mPhone[slotId].setPreferredNetworkType(networkType, null);
                    Settings.Global.putInt(mPhone[slotId].getContext().getContentResolver(),
                            Settings.Global.PREFERRED_NETWORK_MODE + subId,
                            networkType);

                    // Only support automatic selection mode on SIM change.
                    mPhone[slotId].getNetworkSelectionMode(
                            obtainMessage(EVENT_GET_NETWORK_SELECTION_MODE_DONE,
                                    new Integer(slotId)));

                    // Update stored subId
                    SharedPreferences.Editor editor = sp.edit();
                    editor.putInt(CURR_SUBID + slotId, subId);
                    editor.apply();
                }

                // Update set of enabled carrier apps now that the privilege rules may have changed.
                CarrierAppUtils.disableCarrierAppsUntilPrivileged(mContext.getOpPackageName(),
                        mPackageManager, TelephonyManager.getDefault(),
                        mContext.getContentResolver(), mCurrentlyActiveUserId);

                broadcastSimStateChanged(slotId, IccCardConstants.INTENT_VALUE_ICC_LOADED, null);
                updateCarrierServices(slotId, IccCardConstants.INTENT_VALUE_ICC_LOADED);
            }
        }
    }

    // MTK-START
    protected /*private*/ void updateCarrierServices(int slotId, String simState) {
    // MTK-END
        CarrierConfigManager configManager = (CarrierConfigManager)
                mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        configManager.updateConfigForPhoneId(slotId, simState);
        mCarrierServiceBindHelper.updateForPhoneId(slotId, simState);
    }

    // MTK-START
    protected /*private*/ void handleSimAbsent(int slotId) {
    // MTK-END
        if (mIccId[slotId] != null && !mIccId[slotId].equals(ICCID_STRING_FOR_NO_SIM)) {
            logd("SIM" + (slotId + 1) + " hot plug out");
        }
        mIccId[slotId] = ICCID_STRING_FOR_NO_SIM;
        if (isAllIccIdQueryDone()) {
            updateSubscriptionInfoByIccId();
        }
        updateCarrierServices(slotId, IccCardConstants.INTENT_VALUE_ICC_ABSENT);
    }

    private void handleSimError(int slotId) {
        if (mIccId[slotId] != null && !mIccId[slotId].equals(ICCID_STRING_FOR_NO_SIM)) {
            logd("SIM" + (slotId + 1) + " Error ");
        }
        mIccId[slotId] = ICCID_STRING_FOR_NO_SIM;
        if (isAllIccIdQueryDone()) {
            updateSubscriptionInfoByIccId();
        }
        updateCarrierServices(slotId, IccCardConstants.INTENT_VALUE_ICC_CARD_IO_ERROR);
    }

    /**
     * TODO: Simplify more, as no one is interested in what happened
     * only what the current list contains.
     */
    // MTK-START
    synchronized protected /*private*/ void updateSubscriptionInfoByIccId() {
    // MTK-END
        logd("updateSubscriptionInfoByIccId:+ Start");

        for (int i = 0; i < PROJECT_SIM_NUM; i++) {
            mInsertSimState[i] = SIM_NOT_CHANGE;
        }

        int insertedSimCount = PROJECT_SIM_NUM;
        for (int i = 0; i < PROJECT_SIM_NUM; i++) {
            if (ICCID_STRING_FOR_NO_SIM.equals(mIccId[i])) {
                insertedSimCount--;
                mInsertSimState[i] = SIM_NOT_INSERT;
            }
        }
        logd("insertedSimCount = " + insertedSimCount);

        // We only clear the slot-to-sub map when one/some SIM was removed. Note this is a
        // workaround for some race conditions that the empty map was accessed while we are
        // rebuilding the map.
        if (SubscriptionController.getInstance().getActiveSubIdList().length > insertedSimCount) {
            SubscriptionController.getInstance().clearSubInfo();
        }

        int index = 0;
        for (int i = 0; i < PROJECT_SIM_NUM; i++) {
            if (mInsertSimState[i] == SIM_NOT_INSERT) {
                continue;
            }
            index = 2;
            for (int j = i + 1; j < PROJECT_SIM_NUM; j++) {
                if (mInsertSimState[j] == SIM_NOT_CHANGE && mIccId[i].equals(mIccId[j])) {
                    mInsertSimState[i] = 1;
                    mInsertSimState[j] = index;
                    index++;
                }
            }
        }

        ContentResolver contentResolver = mContext.getContentResolver();
        String[] oldIccId = new String[PROJECT_SIM_NUM];
        for (int i = 0; i < PROJECT_SIM_NUM; i++) {
            oldIccId[i] = null;
            List<SubscriptionInfo> oldSubInfo =
                    SubscriptionController.getInstance().getSubInfoUsingSlotIndexWithCheck(i, false,
                    mContext.getOpPackageName());
            if (oldSubInfo != null && oldSubInfo.size() > 0) {
                oldIccId[i] = oldSubInfo.get(0).getIccId();
                logd("updateSubscriptionInfoByIccId: oldSubId = "
                        + oldSubInfo.get(0).getSubscriptionId());
                if (mInsertSimState[i] == SIM_NOT_CHANGE && !mIccId[i].equals(oldIccId[i])) {
                    mInsertSimState[i] = SIM_CHANGED;
                }
                if (mInsertSimState[i] != SIM_NOT_CHANGE) {
                    ContentValues value = new ContentValues(1);
                    value.put(SubscriptionManager.SIM_SLOT_INDEX,
                            SubscriptionManager.INVALID_SIM_SLOT_INDEX);
                    contentResolver.update(SubscriptionManager.CONTENT_URI, value,
                            SubscriptionManager.UNIQUE_KEY_SUBSCRIPTION_ID + "="
                            + Integer.toString(oldSubInfo.get(0).getSubscriptionId()), null);

                    // refresh Cached Active Subscription Info List
                    SubscriptionController.getInstance().refreshCachedActiveSubscriptionInfoList();
                }
            } else {
                if (mInsertSimState[i] == SIM_NOT_CHANGE) {
                    // no SIM inserted last time, but there is one SIM inserted now
                    mInsertSimState[i] = SIM_CHANGED;
                }
                oldIccId[i] = ICCID_STRING_FOR_NO_SIM;
                logd("updateSubscriptionInfoByIccId: No SIM in slot " + i + " last time");
            }
        }

        for (int i = 0; i < PROJECT_SIM_NUM; i++) {
            logd("updateSubscriptionInfoByIccId: oldIccId[" + i + "] = " + oldIccId[i] +
                    ", sIccId[" + i + "] = " + mIccId[i]);
        }

        //check if the inserted SIM is new SIM
        int nNewCardCount = 0;
        int nNewSimStatus = 0;
        for (int i = 0; i < PROJECT_SIM_NUM; i++) {
            if (mInsertSimState[i] == SIM_NOT_INSERT) {
                logd("updateSubscriptionInfoByIccId: No SIM inserted in slot " + i + " this time");
            } else {
                if (mInsertSimState[i] > 0) {
                    //some special SIMs may have the same IccIds, add suffix to distinguish them
                    //FIXME: addSubInfoRecord can return an error.
                    mSubscriptionManager.addSubscriptionInfoRecord(mIccId[i]
                            + Integer.toString(mInsertSimState[i]), i);
                    logd("SUB" + (i + 1) + " has invalid IccId");
                } else /*if (sInsertSimState[i] != SIM_NOT_INSERT)*/ {
                    mSubscriptionManager.addSubscriptionInfoRecord(mIccId[i], i);
                }
                if (isNewSim(mIccId[i], oldIccId)) {
                    nNewCardCount++;
                    switch (i) {
                        case PhoneConstants.SUB1:
                            nNewSimStatus |= STATUS_SIM1_INSERTED;
                            break;
                        case PhoneConstants.SUB2:
                            nNewSimStatus |= STATUS_SIM2_INSERTED;
                            break;
                        case PhoneConstants.SUB3:
                            nNewSimStatus |= STATUS_SIM3_INSERTED;
                            break;
                        //case PhoneConstants.SUB3:
                        //    nNewSimStatus |= STATUS_SIM4_INSERTED;
                        //    break;
                    }

                    mInsertSimState[i] = SIM_NEW;
                }
            }
        }

        for (int i = 0; i < PROJECT_SIM_NUM; i++) {
            if (mInsertSimState[i] == SIM_CHANGED) {
                mInsertSimState[i] = SIM_REPOSITION;
            }
            logd("updateSubscriptionInfoByIccId: sInsertSimState[" + i + "] = "
                    + mInsertSimState[i]);
        }

        List<SubscriptionInfo> subInfos = mSubscriptionManager.getActiveSubscriptionInfoList();
        int nSubCount = (subInfos == null) ? 0 : subInfos.size();
        logd("updateSubscriptionInfoByIccId: nSubCount = " + nSubCount);
        for (int i=0; i < nSubCount; i++) {
            SubscriptionInfo temp = subInfos.get(i);

            String msisdn = TelephonyManager.getDefault().getLine1Number(
                    temp.getSubscriptionId());

            if (msisdn != null) {
                ContentValues value = new ContentValues(1);
                value.put(SubscriptionManager.NUMBER, msisdn);
                contentResolver.update(SubscriptionManager.CONTENT_URI, value,
                        SubscriptionManager.UNIQUE_KEY_SUBSCRIPTION_ID + "="
                        + Integer.toString(temp.getSubscriptionId()), null);

                // refresh Cached Active Subscription Info List
                SubscriptionController.getInstance().refreshCachedActiveSubscriptionInfoList();
            }
        }

        // Ensure the modems are mapped correctly
        mSubscriptionManager.setDefaultDataSubId(
                mSubscriptionManager.getDefaultDataSubscriptionId());

        // No need to check return value here as we notify for the above changes anyway.
        updateEmbeddedSubscriptions();

        SubscriptionController.getInstance().notifySubscriptionInfoChanged();
        logd("updateSubscriptionInfoByIccId:- SubscriptionInfo update complete");
    }

    /**
     * Update the cached list of embedded subscriptions.
     *
     * @return true if changes may have been made. This is not a guarantee that changes were made,
     * but notifications about subscription changes may be skipped if this returns false as an
     * optimization to avoid spurious notifications.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    public boolean updateEmbeddedSubscriptions() {
        // Do nothing if eUICCs are disabled. (Previous entries may remain in the cache, but they
        // are filtered out of list calls as long as EuiccManager.isEnabled returns false).
        if (!mEuiccManager.isEnabled()) {
            return false;
        }

        GetEuiccProfileInfoListResult result =
                EuiccController.get().blockingGetEuiccProfileInfoList();
        if (result == null) {
            // IPC to the eUICC controller failed.
            return false;
        }

        final EuiccProfileInfo[] embeddedProfiles;
        if (result.result == EuiccService.RESULT_OK) {
            embeddedProfiles = result.profiles;
        } else {
            logd("updatedEmbeddedSubscriptions: error " + result.result + " listing profiles");
            // If there's an error listing profiles, treat it equivalently to a successful
            // listing which returned no profiles under the assumption that none are currently
            // accessible.
            embeddedProfiles = new EuiccProfileInfo[0];
        }
        final boolean isRemovable = result.isRemovable;

        final String[] embeddedIccids = new String[embeddedProfiles.length];
        for (int i = 0; i < embeddedProfiles.length; i++) {
            embeddedIccids[i] = embeddedProfiles[i].iccid;
        }

        // Note that this only tracks whether we make any writes to the DB. It's possible this will
        // be set to true for an update even when the row contents remain exactly unchanged from
        // before, since we don't compare against the previous value. Since this is only intended to
        // avoid some spurious broadcasts (particularly for users who don't use eSIM at all), this
        // is fine.
        boolean hasChanges = false;

        // Update or insert records for all embedded subscriptions (except non-removable ones if the
        // current eUICC is non-removable, since we assume these are still accessible though not
        // returned by the eUICC controller).
        List<SubscriptionInfo> existingSubscriptions = SubscriptionController.getInstance()
                .getSubscriptionInfoListForEmbeddedSubscriptionUpdate(embeddedIccids, isRemovable);
        ContentResolver contentResolver = mContext.getContentResolver();
        for (EuiccProfileInfo embeddedProfile : embeddedProfiles) {
            int index =
                    findSubscriptionInfoForIccid(existingSubscriptions, embeddedProfile.iccid);
            if (index < 0) {
                // No existing entry for this ICCID; create an empty one.
                SubscriptionController.getInstance().insertEmptySubInfoRecord(
                        embeddedProfile.iccid, SubscriptionManager.SIM_NOT_INSERTED);
            } else {
                existingSubscriptions.remove(index);
            }
            ContentValues values = new ContentValues();
            values.put(SubscriptionManager.IS_EMBEDDED, 1);
            values.put(SubscriptionManager.ACCESS_RULES,
                    embeddedProfile.accessRules == null ? null :
                            UiccAccessRule.encodeRules(embeddedProfile.accessRules));
            values.put(SubscriptionManager.IS_REMOVABLE, isRemovable);
            values.put(SubscriptionManager.DISPLAY_NAME, embeddedProfile.nickname);
            values.put(SubscriptionManager.NAME_SOURCE, SubscriptionManager.NAME_SOURCE_USER_INPUT);
            hasChanges = true;
            contentResolver.update(SubscriptionManager.CONTENT_URI, values,
                    SubscriptionManager.ICC_ID + "=\"" + embeddedProfile.iccid + "\"", null);

            // refresh Cached Active Subscription Info List
            SubscriptionController.getInstance().refreshCachedActiveSubscriptionInfoList();
        }

        // Remove all remaining subscriptions which have embedded = true. We set embedded to false
        // to ensure they are not returned in the list of embedded subscriptions (but keep them
        // around in case the subscription is added back later, which is equivalent to a removable
        // SIM being removed and reinserted).
        if (!existingSubscriptions.isEmpty()) {
            List<String> iccidsToRemove = new ArrayList<>();
            for (int i = 0; i < existingSubscriptions.size(); i++) {
                SubscriptionInfo info = existingSubscriptions.get(i);
                if (info.isEmbedded()) {
                    iccidsToRemove.add("\"" + info.getIccId() + "\"");
                }
            }
            String whereClause = SubscriptionManager.ICC_ID + " IN ("
                    + TextUtils.join(",", iccidsToRemove) + ")";
            ContentValues values = new ContentValues();
            values.put(SubscriptionManager.IS_EMBEDDED, 0);
            hasChanges = true;
            contentResolver.update(SubscriptionManager.CONTENT_URI, values, whereClause, null);

            // refresh Cached Active Subscription Info List
            SubscriptionController.getInstance().refreshCachedActiveSubscriptionInfoList();
        }

        return hasChanges;
    }

    private static int findSubscriptionInfoForIccid(List<SubscriptionInfo> list, String iccid) {
        for (int i = 0; i < list.size(); i++) {
            if (TextUtils.equals(iccid, list.get(i).getIccId())) {
                return i;
            }
        }
        return -1;
    }

    // MTK-START
    protected /*private*/ boolean isNewSim(String iccId, String[] oldIccId) {
    // MTK-END
        boolean newSim = true;
        for(int i = 0; i < PROJECT_SIM_NUM; i++) {
            if(iccId.equals(oldIccId[i])) {
                newSim = false;
                break;
            }
        }
        logd("newSim = " + newSim);

        return newSim;
    }

    // MTK-START
    protected /*private*/ void broadcastSimStateChanged(int slotId, String state, String reason) {
    // MTK-END
        Intent i = new Intent(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        // TODO - we'd like this intent to have a single snapshot of all sim state,
        // but until then this should not use REPLACE_PENDING or we may lose
        // information
        // i.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING
        //         | Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        i.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        i.putExtra(PhoneConstants.PHONE_NAME_KEY, "Phone");
        i.putExtra(IccCardConstants.INTENT_KEY_ICC_STATE, state);
        i.putExtra(IccCardConstants.INTENT_KEY_LOCKED_REASON, reason);
        SubscriptionManager.putPhoneIdAndSubIdExtra(i, slotId);
        logd("Broadcasting intent ACTION_SIM_STATE_CHANGED " + state + " reason " + reason +
             " for mCardIndex: " + slotId);
        IntentBroadcaster.getInstance().broadcastStickyIntent(i, slotId);
    }

    public void dispose() {
        logd("[dispose]");
        mContext.unregisterReceiver(sReceiver);
    }

    private void logd(String message) {
        Rlog.d(LOG_TAG, message);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("SubscriptionInfoUpdater:");
        mCarrierServiceBindHelper.dump(fd, pw, args);
    }
}
