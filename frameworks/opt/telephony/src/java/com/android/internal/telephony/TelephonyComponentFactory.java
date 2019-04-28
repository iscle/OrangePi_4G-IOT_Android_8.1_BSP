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
 * Copyright (C) 2015 The Android Open Source Project
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

import android.content.ComponentName;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.IDeviceIdleController;
import android.os.Looper;
import android.os.ServiceManager;
import android.telephony.Rlog;
import android.util.Log;

import java.lang.reflect.Method;
import java.lang.reflect.Constructor;

import com.android.internal.telephony.cdma.CdmaSubscriptionSourceManager;
import com.android.internal.telephony.cdma.EriManager;
import com.android.internal.telephony.dataconnection.ApnSetting;
import com.android.internal.telephony.dataconnection.DataProfile;
import com.android.internal.telephony.dataconnection.DataConnection;
import com.android.internal.telephony.dataconnection.DcAsyncChannel;
import com.android.internal.telephony.dataconnection.DcController;
import com.android.internal.telephony.dataconnection.DcRequest;
import com.android.internal.telephony.dataconnection.DcTesterFailBringUpAll;
import com.android.internal.telephony.dataconnection.DcTracker;
import com.android.internal.telephony.dataconnection.TelephonyNetworkFactory;
import com.android.internal.telephony.DefaultPhoneNotifier;
import com.android.internal.telephony.imsphone.ImsExternalCallTracker;
import com.android.internal.telephony.imsphone.ImsPhone;
import com.android.internal.telephony.imsphone.ImsPhoneCallTracker;
import com.android.internal.telephony.uicc.IccCardProxy;
//MTK START: add-on
import com.android.internal.telephony.uicc.UiccController;
//MTK END
import com.android.internal.telephony.RIL;

// M: Revise for sub class
import com.android.internal.telephony.cdma.CdmaInboundSmsHandler;
import com.android.internal.telephony.cdma.CdmaSMSDispatcher;
import com.android.internal.telephony.gsm.GsmSMSDispatcher;
import com.android.internal.telephony.gsm.GsmInboundSmsHandler;
import com.android.internal.telephony.ImsSMSDispatcher;
import com.android.internal.telephony.SmsBroadcastUndelivered;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.SubscriptionInfoUpdater;
import com.android.internal.telephony.WapPushOverSms;
import com.android.internal.telephony.cat.CatService;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.gsm.GsmCellBroadcastHandler;

// MTK-START
import com.android.internal.telephony.cat.CommandParamsFactory;
import com.android.internal.telephony.cat.IconLoader;
import com.android.internal.telephony.cat.RilMessageDecoder;
import com.android.internal.telephony.RetryManager;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.UiccCardApplication;
// MTK-END

/**
 * This class has one-line methods to instantiate objects only. The purpose is to make code
 * unit-test friendly and use this class as a way to do dependency injection. Instantiating objects
 * this way makes it easier to mock them in tests.
 */
public class TelephonyComponentFactory {
    private static TelephonyComponentFactory sInstance;
    public static final String LOG_TAG = "TelephonyComponentFactory";

    public static TelephonyComponentFactory getInstance() {
        if (sInstance == null) {
            String className = "com.mediatek.internal.telephony.MtkTelephonyComponentFactory";
            String classPackage = "/system/framework/mediatek-telephony-common.jar";
            Class<?> clazz = null;
            try {
                clazz = Class.forName(className, false, ClassLoader.getSystemClassLoader());
                Rlog.d(LOG_TAG, "class = " + clazz);
                Constructor clazzConstructfunc = clazz.getConstructor();
                Rlog.d(LOG_TAG, "constructor function = " + clazzConstructfunc);
                sInstance = (TelephonyComponentFactory) clazzConstructfunc.newInstance();
            } catch (Exception  e) {
                Rlog.e(LOG_TAG, "No MtkTelephonyComponentFactory! Used AOSP for instead!");
                sInstance = new TelephonyComponentFactory();
            }
        }
        return sInstance;
    }
    //MTK START: add-on
    public UiccController makeUiccController(Context c, CommandsInterface[] ci) {
        Rlog.d(LOG_TAG , "makeUiccController aosp");
        return new UiccController(c, ci);
    }

    public SubscriptionController makeSubscriptionController(Phone phone) {
        return new SubscriptionController(phone);
    }

    public SubscriptionController makeSubscriptionController(Context c, CommandsInterface[] ci) {
        return new SubscriptionController(c);
    }

    public SubscriptionInfoUpdater makeSubscriptionInfoUpdater(Looper looper, Context context,
            Phone[] phone, CommandsInterface[] ci) {
        return new SubscriptionInfoUpdater(looper, context, phone, ci);
    }
    //MTK END
    public GsmCdmaCallTracker makeGsmCdmaCallTracker(GsmCdmaPhone phone) {
        return new GsmCdmaCallTracker(phone);
    }

    public SmsStorageMonitor makeSmsStorageMonitor(Phone phone) {
        return new SmsStorageMonitor(phone);
    }

    public SmsUsageMonitor makeSmsUsageMonitor(Context context) {
        return new SmsUsageMonitor(context);
    }

    public ServiceStateTracker makeServiceStateTracker(GsmCdmaPhone phone, CommandsInterface ci) {
        return new ServiceStateTracker(phone, ci);
    }

    public SimActivationTracker makeSimActivationTracker(Phone phone) {
        return new SimActivationTracker(phone);
    }

    public DcTracker makeDcTracker(Phone phone) {
        return new DcTracker(phone);
    }

    public CarrierSignalAgent makeCarrierSignalAgent(Phone phone) {
        return new CarrierSignalAgent(phone);
    }

    public CarrierActionAgent makeCarrierActionAgent(Phone phone) {
        return new CarrierActionAgent(phone);
    }

    public IccPhoneBookInterfaceManager makeIccPhoneBookInterfaceManager(Phone phone) {
        return new IccPhoneBookInterfaceManager(phone);
    }

    public IccSmsInterfaceManager makeIccSmsInterfaceManager(Phone phone) {
        return new IccSmsInterfaceManager(phone);
    }

    public IccCardProxy makeIccCardProxy(Context context, CommandsInterface ci, int phoneId) {
        return new IccCardProxy(context, ci, phoneId);
    }

    public EriManager makeEriManager(Phone phone, Context context, int eriFileSource) {
        return new EriManager(phone, context, eriFileSource);
    }

    public WspTypeDecoder makeWspTypeDecoder(byte[] pdu) {
        return new WspTypeDecoder(pdu);
    }

    /**
     * Create a tracker for a single-part SMS.
     */
    public InboundSmsTracker makeInboundSmsTracker(byte[] pdu, long timestamp, int destPort,
            boolean is3gpp2, boolean is3gpp2WapPdu, String address, String displayAddr,
            String messageBody) {
        return new InboundSmsTracker(pdu, timestamp, destPort, is3gpp2, is3gpp2WapPdu, address,
                displayAddr, messageBody);
    }

    /**
     * Create a tracker for a multi-part SMS.
     */
    public InboundSmsTracker makeInboundSmsTracker(byte[] pdu, long timestamp, int destPort,
            boolean is3gpp2, String address, String displayAddr, int referenceNumber,
            int sequenceNumber, int messageCount, boolean is3gpp2WapPdu, String messageBody) {
        return new InboundSmsTracker(pdu, timestamp, destPort, is3gpp2, address, displayAddr,
                referenceNumber, sequenceNumber, messageCount, is3gpp2WapPdu, messageBody);
    }

    /**
     * Create a tracker from a row of raw table
     */
    public InboundSmsTracker makeInboundSmsTracker(Cursor cursor, boolean isCurrentFormat3gpp2) {
        return new InboundSmsTracker(cursor, isCurrentFormat3gpp2);
    }

    public ImsPhoneCallTracker makeImsPhoneCallTracker(ImsPhone imsPhone) {
        return new ImsPhoneCallTracker(imsPhone);
    }

    public ImsExternalCallTracker makeImsExternalCallTracker(ImsPhone imsPhone) {

        return new ImsExternalCallTracker(imsPhone);
    }

    /**
     * Create an AppSmsManager for per-app SMS message.
     */
    public AppSmsManager makeAppSmsManager(Context context) {
        return new AppSmsManager(context);
    }

    public DeviceStateMonitor makeDeviceStateMonitor(Phone phone) {
        return new DeviceStateMonitor(phone);
    }

    public CdmaSubscriptionSourceManager
    getCdmaSubscriptionSourceManagerInstance(Context context, CommandsInterface ci, Handler h,
                                             int what, Object obj) {
        return CdmaSubscriptionSourceManager.getInstance(context, ci, h, what, obj);
    }

    public CdmaSubscriptionSourceManager
    makeCdmaSubscriptionSourceManager(Context context, CommandsInterface ci, Handler h,
            int what, Object obj) {
        return new CdmaSubscriptionSourceManager(context, ci);
    }

    public IDeviceIdleController getIDeviceIdleController() {
        return IDeviceIdleController.Stub.asInterface(
                ServiceManager.getService(Context.DEVICE_IDLE_CONTROLLER));
    }

    // MTK-START
    /**
     * Create a default phone.
     */
    public GsmCdmaPhone makePhone(Context context, CommandsInterface ci, PhoneNotifier notifier,
            int phoneId, int precisePhoneType,
            TelephonyComponentFactory telephonyComponentFactory) {
        return new GsmCdmaPhone(context, ci, notifier, phoneId, precisePhoneType,
                telephonyComponentFactory);
    }

    /**
     * Create a default RIL.
     */
    public RIL makeRil(Context context, int preferredNetworkType, int cdmaSubscription,
            Integer instanceId) {
        return new RIL(context, preferredNetworkType, cdmaSubscription, instanceId);
    }

    public DefaultPhoneNotifier makeDefaultPhoneNotifier() {
        Rlog.d(LOG_TAG , "makeDefaultPhoneNotifier aosp");
        return new DefaultPhoneNotifier();
    }

    public SmsHeader makeSmsHeader() {
        return new SmsHeader();
    }

    /**
     * Create ImsSMSDispatcher
     */
    public ImsSMSDispatcher makeImsSMSDispatcher(Phone phone,
            SmsStorageMonitor storageMonitor, SmsUsageMonitor usageMonitor) {
        return new ImsSMSDispatcher(phone, storageMonitor, usageMonitor);
    }

    /**
     * Create a dispatcher for CDMA SMS.
     */
    public CdmaSMSDispatcher makeCdmaSMSDispatcher(Phone phone, SmsUsageMonitor usageMonitor,
            ImsSMSDispatcher imsSMSDispatcher) {
        return new CdmaSMSDispatcher(phone, usageMonitor, imsSMSDispatcher);
    }

    /**
     * Create a dispatcher for GSM SMS.
     */
    public GsmSMSDispatcher makeGsmSMSDispatcher(Phone phone, SmsUsageMonitor usageMonitor,
            ImsSMSDispatcher imsSMSDispatcher, GsmInboundSmsHandler gsmInboundSmsHandler) {
        return new GsmSMSDispatcher(phone, usageMonitor, imsSMSDispatcher, gsmInboundSmsHandler);
    }

    /**
     * Create an object of SmsBroadcastUndelivered.
     */
    public void makeSmsBroadcastUndelivered(Context context,
            GsmInboundSmsHandler gsmInboundSmsHandler,
            CdmaInboundSmsHandler cdmaInboundSmsHandler) {
        SmsBroadcastUndelivered.initialize(context, gsmInboundSmsHandler, cdmaInboundSmsHandler);
    }

    public WapPushOverSms makeWapPushOverSms(Context context) {
        return new WapPushOverSms(context);
    }

    // Create GsmInboudSmsHandler
    public GsmInboundSmsHandler makeGsmInboundSmsHandler(Context context,
            SmsStorageMonitor storageMonitor, Phone phone) {
        return GsmInboundSmsHandler.makeInboundSmsHandler(context, storageMonitor, phone);
    }

    // Create GsmCellBroadcastHandler
    public GsmCellBroadcastHandler makeGsmCellBroadcastHandler(Context context,
            Phone phone) {
        return GsmCellBroadcastHandler.makeGsmCellBroadcastHandler(context, phone);
    }

    // M: Revise for add-on
    public IccInternalInterface makeIccProvider(UriMatcher urlMatcher, Context context) {
        Rlog.d(LOG_TAG , "makeIccProvider aosp");
        return null;
    }

    /*
    * Create RadioManager
    */
    public void initRadioManager(Context context, int numPhones,
            CommandsInterface[] sCommandsInterfaces) {
    }

    /// M: eMBMS feature
    /*
     * Create EmbmsAdaptor
     */
    public void initEmbmsAdaptor(Context context, CommandsInterface[] sCommandsInterfaces) {
    }
    /// M: eMBMS end

    /**
     * Create a default CatService.
     */
    public CatService makeCatService(CommandsInterface ci, UiccCardApplication ca, IccRecords ir,
            Context context, IccFileHandler fh, UiccCard ic, int slotId) {
        return new CatService(ci, ca, ir, context, fh, ic, slotId);
    }

    /**
     * Create a default RilMessageDecoder.
     */
    public RilMessageDecoder makeRilMessageDecoder(Handler caller, IccFileHandler fh,
            int slotId) {
        return new RilMessageDecoder(caller, fh);
    }

    /**
     * Create a default CommandParamsFactory.
     */
    public CommandParamsFactory makeCommandParamsFactory(RilMessageDecoder caller,
            IccFileHandler fh) {
        return new CommandParamsFactory(caller, fh);
    }

    /**
     * Create a default IconLoader
     */
    public IconLoader makeIconLoader(Looper looper , IccFileHandler fh) {
        return new IconLoader(looper, fh);
    }

    /**
     * Create telephony network factories
     */
    public TelephonyNetworkFactory makeTelephonyNetworkFactories(PhoneSwitcher phoneSwitcher,
            SubscriptionController subscriptionController, SubscriptionMonitor subscriptionMonitor,
            Looper looper, Context context, int phoneId, DcTracker dcTracker) {
        return new TelephonyNetworkFactory(phoneSwitcher, subscriptionController,
                subscriptionMonitor, looper, context, phoneId, dcTracker);
    }

    /**
     * Create a default phone switcher
     */
    public PhoneSwitcher makePhoneSwitcher(int maxActivePhones, int numPhones,
            Context context, SubscriptionController subscriptionController, Looper looper,
            ITelephonyRegistry tr, CommandsInterface[] cis, Phone[] phones) {
        return new PhoneSwitcher(maxActivePhones, numPhones, context, subscriptionController,
                looper, tr, cis, phones);
    }

    /**
     * Create a proxy controller for radio capability switch.
     */
    public ProxyController makeProxyController(Context context, Phone[] phone,
            UiccController uiccController, CommandsInterface[] ci, PhoneSwitcher ps) {
        return new ProxyController(context, phone, uiccController, ci, ps);
    }

    /**
     * Create CdmaInboundSmsHandler.
     *
     *  @param context  the context of the phone process
     *  @param storageMonitor  the object of SmsStorageMonitor
     *  @param phone  the object of the Phone
     *  @param smsDispatcher the object of the CdmaSMSDispatcher
     *
     *  @return the object of CdmaInboundSmsHandler
     */
    public CdmaInboundSmsHandler makeCdmaInboundSmsHandler(Context context,
            SmsStorageMonitor storageMonitor, Phone phone, CdmaSMSDispatcher smsDispatcher) {
        return new CdmaInboundSmsHandler(context, storageMonitor, phone, smsDispatcher);
    }

    /**
     * Create world phone instance .
     */
    public void makeWorldPhoneManager() {
    }

    /**
     * Create a data connection helper instance
     */
    public void makeDcHelper(Context context, Phone[] phones) {
    }

    /**
     * Create a supplementary service manager instance
     */
    public void makeSuppServManager(Context context, Phone[] phones) {
    }

    public ImsPhone makeImsPhone(Context context, PhoneNotifier phoneNotifier, Phone defaultPhone) {
        try {
            return new ImsPhone(context, phoneNotifier, defaultPhone);
        } catch (Exception e) {
            Rlog.e("TelephonyComponentFactory", "makeImsPhone", e);
            return null;
        }
    }

    /**
     * Create a data sub selector instance
     */
    public void makeDataSubSelector(Context context, int numPhones) {
    }

    /**
     * Create a smart data swtich assistant instance
     */
    public void makeSmartDataSwitchAssistant(Context context, Phone[] phones) {
    }

    public CallManager makeCallManager() {
        return new CallManager();
    }

    /**
     * Create a data profile instance
     */
    public DataProfile makeDataProfile(ApnSetting apn, int profileId) {
        return new DataProfile(apn, profileId);
    }

    /**
     * Create a retry manager instance
     */
    public RetryManager makeRetryManager(Phone phone, String apnType) {
        return new RetryManager(phone, apnType);
    }

    /**
     * Create a data connection instance
     */
    public DataConnection makeDataConnection(Phone phone, String name, int id,
            DcTracker dct, DcTesterFailBringUpAll failBringUpAll, DcController dcc) {
        return new DataConnection(phone, name, id, dct, failBringUpAll, dcc);
    }

    /**
     * Create a dc async channel instance
     */
    public DcAsyncChannel makeDcAsyncChannel(DataConnection dc, String logTag) {
        return new DcAsyncChannel(dc, logTag);
    }

    /**
     * Create a dc controller instance
     */
    public DcController makeDcController(String name, Phone phone, DcTracker dct, Handler handler) {
        return new DcController(name, phone, dct, handler);
    }

    /**
     * Create a dc request instance.
     * @param nr {@link NetworkRequest} describing this request.
     * @param context  the context of the phone process
     *
     * @return the object of DcRequest
     */
    public DcRequest makeDcRequest(NetworkRequest nr, Context context) {
        return new DcRequest(nr, context);
    }

    /** Create a ComponentName
     */
    public ComponentName makeConnectionServiceName() {
        return new ComponentName("com.android.phone",
                    "com.android.services.telephony.TelephonyConnectionService");
    }
}