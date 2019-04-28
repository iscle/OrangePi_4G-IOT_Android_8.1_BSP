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

package com.android.phone;

import static com.android.internal.telephony.PhoneConstants.SUBSCRIPTION_KEY;

import android.Manifest.permission;
import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.NetworkStats;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.PersistableBundle;
import android.os.ResultReceiver;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.WorkSource;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.service.carrier.CarrierIdentifier;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;
import android.telephony.CellInfo;
import android.telephony.ClientRequestStats;
import android.telephony.IccOpenLogicalChannelResponse;
import android.telephony.ModemActivityInfo;
import android.telephony.NeighboringCellInfo;
import android.telephony.NetworkScanRequest;
import android.telephony.RadioAccessFamily;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyHistogram;
import android.telephony.TelephonyManager;
import android.telephony.UssdResponse;
import android.telephony.VisualVoicemailSmsFilterSettings;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;
import android.util.Slog;

import com.android.ims.ImsManager;
import com.android.ims.internal.IImsServiceController;
import com.android.ims.internal.IImsServiceFeatureListener;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CellNetworkScanResult;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.DefaultPhoneNotifier;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.MccTable;
import com.android.internal.telephony.NetworkScanRequestTracker;
import com.android.internal.telephony.OperatorInfo;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstantConversions;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.ProxyController;
import com.android.internal.telephony.RIL;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.euicc.EuiccConnector;
import com.android.internal.telephony.uicc.IccIoResult;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.telephony.uicc.SIMRecords;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.telephony.util.VoicemailNotificationSettingsUtil;
import com.android.internal.util.HexDump;
import com.android.phone.vvm.PhoneAccountHandleConverter;
import com.android.phone.vvm.RemoteVvmTaskManager;
import com.android.phone.vvm.VisualVoicemailSettingsUtil;
import com.android.phone.vvm.VisualVoicemailSmsFilterConfig;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Implementation of the ITelephony interface.
 */
public class PhoneInterfaceManager extends ITelephony.Stub {
    private static final String LOG_TAG = "PhoneInterfaceManager";
    private static final boolean DBG = (PhoneGlobals.DBG_LEVEL >= 2);
    private static final boolean DBG_LOC = false;
    private static final boolean DBG_MERGE = false;

    // Message codes used with mMainThreadHandler
    private static final int CMD_HANDLE_PIN_MMI = 1;
    private static final int CMD_HANDLE_NEIGHBORING_CELL = 2;
    private static final int EVENT_NEIGHBORING_CELL_DONE = 3;
    private static final int CMD_ANSWER_RINGING_CALL = 4;
    private static final int CMD_END_CALL = 5;  // not used yet
    private static final int CMD_TRANSMIT_APDU_LOGICAL_CHANNEL = 7;
    private static final int EVENT_TRANSMIT_APDU_LOGICAL_CHANNEL_DONE = 8;
    private static final int CMD_OPEN_CHANNEL = 9;
    private static final int EVENT_OPEN_CHANNEL_DONE = 10;
    private static final int CMD_CLOSE_CHANNEL = 11;
    private static final int EVENT_CLOSE_CHANNEL_DONE = 12;
    private static final int CMD_NV_READ_ITEM = 13;
    private static final int EVENT_NV_READ_ITEM_DONE = 14;
    private static final int CMD_NV_WRITE_ITEM = 15;
    private static final int EVENT_NV_WRITE_ITEM_DONE = 16;
    private static final int CMD_NV_WRITE_CDMA_PRL = 17;
    private static final int EVENT_NV_WRITE_CDMA_PRL_DONE = 18;
    private static final int CMD_NV_RESET_CONFIG = 19;
    private static final int EVENT_NV_RESET_CONFIG_DONE = 20;
    private static final int CMD_GET_PREFERRED_NETWORK_TYPE = 21;
    private static final int EVENT_GET_PREFERRED_NETWORK_TYPE_DONE = 22;
    private static final int CMD_SET_PREFERRED_NETWORK_TYPE = 23;
    private static final int EVENT_SET_PREFERRED_NETWORK_TYPE_DONE = 24;
    private static final int CMD_SEND_ENVELOPE = 25;
    private static final int EVENT_SEND_ENVELOPE_DONE = 26;
    private static final int CMD_INVOKE_OEM_RIL_REQUEST_RAW = 27;
    private static final int EVENT_INVOKE_OEM_RIL_REQUEST_RAW_DONE = 28;
    private static final int CMD_TRANSMIT_APDU_BASIC_CHANNEL = 29;
    private static final int EVENT_TRANSMIT_APDU_BASIC_CHANNEL_DONE = 30;
    private static final int CMD_EXCHANGE_SIM_IO = 31;
    private static final int EVENT_EXCHANGE_SIM_IO_DONE = 32;
    private static final int CMD_SET_VOICEMAIL_NUMBER = 33;
    private static final int EVENT_SET_VOICEMAIL_NUMBER_DONE = 34;
    private static final int CMD_SET_NETWORK_SELECTION_MODE_AUTOMATIC = 35;
    private static final int EVENT_SET_NETWORK_SELECTION_MODE_AUTOMATIC_DONE = 36;
    private static final int CMD_GET_MODEM_ACTIVITY_INFO = 37;
    private static final int EVENT_GET_MODEM_ACTIVITY_INFO_DONE = 38;
    private static final int CMD_PERFORM_NETWORK_SCAN = 39;
    private static final int EVENT_PERFORM_NETWORK_SCAN_DONE = 40;
    private static final int CMD_SET_NETWORK_SELECTION_MODE_MANUAL = 41;
    private static final int EVENT_SET_NETWORK_SELECTION_MODE_MANUAL_DONE = 42;
    private static final int CMD_SET_ALLOWED_CARRIERS = 43;
    private static final int EVENT_SET_ALLOWED_CARRIERS_DONE = 44;
    private static final int CMD_GET_ALLOWED_CARRIERS = 45;
    private static final int EVENT_GET_ALLOWED_CARRIERS_DONE = 46;
    private static final int CMD_HANDLE_USSD_REQUEST = 47;
    private static final int CMD_GET_FORBIDDEN_PLMNS = 48;
    private static final int EVENT_GET_FORBIDDEN_PLMNS_DONE = 49;

    /** The singleton instance. */
    private static PhoneInterfaceManager sInstance;

    private PhoneGlobals mApp;
    private Phone mPhone;
    private CallManager mCM;
    private UserManager mUserManager;
    private AppOpsManager mAppOps;
    private MainThreadHandler mMainThreadHandler;
    private SubscriptionController mSubscriptionController;
    private SharedPreferences mTelephonySharedPreferences;

    private static final String PREF_CARRIERS_ALPHATAG_PREFIX = "carrier_alphtag_";
    private static final String PREF_CARRIERS_NUMBER_PREFIX = "carrier_number_";
    private static final String PREF_CARRIERS_SUBSCRIBER_PREFIX = "carrier_subscriber_";

    // The AID of ISD-R.
    private static final String ISDR_AID = "A0000005591010FFFFFFFF8900000100";

    private NetworkScanRequestTracker mNetworkScanRequestTracker;

    /**
     * A request object to use for transmitting data to an ICC.
     */
    private static final class IccAPDUArgument {
        public int channel, cla, command, p1, p2, p3;
        public String data;

        public IccAPDUArgument(int channel, int cla, int command,
                int p1, int p2, int p3, String data) {
            this.channel = channel;
            this.cla = cla;
            this.command = command;
            this.p1 = p1;
            this.p2 = p2;
            this.p3 = p3;
            this.data = data;
        }
    }

    /**
     * A request object to use for transmitting data to an ICC.
     */
    private static final class ManualNetworkSelectionArgument {
        public OperatorInfo operatorInfo;
        public boolean persistSelection;

        public ManualNetworkSelectionArgument(OperatorInfo operatorInfo, boolean persistSelection) {
            this.operatorInfo = operatorInfo;
            this.persistSelection = persistSelection;
        }
    }

    /**
     * A request object for use with {@link MainThreadHandler}. Requesters should wait() on the
     * request after sending. The main thread will notify the request when it is complete.
     */
    private static final class MainThreadRequest {
        /** The argument to use for the request */
        public Object argument;
        /** The result of the request that is run on the main thread */
        public Object result;
        // The subscriber id that this request applies to. Defaults to
        // SubscriptionManager.INVALID_SUBSCRIPTION_ID
        public Integer subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;

        public MainThreadRequest(Object argument) {
            this.argument = argument;
        }

        public MainThreadRequest(Object argument, Integer subId) {
            this.argument = argument;
            if (subId != null) {
                this.subId = subId;
            }
        }
    }

    private static final class IncomingThirdPartyCallArgs {
        public final ComponentName component;
        public final String callId;
        public final String callerDisplayName;

        public IncomingThirdPartyCallArgs(ComponentName component, String callId,
                String callerDisplayName) {
            this.component = component;
            this.callId = callId;
            this.callerDisplayName = callerDisplayName;
        }
    }

    /**
     * A handler that processes messages on the main thread in the phone process. Since many
     * of the Phone calls are not thread safe this is needed to shuttle the requests from the
     * inbound binder threads to the main thread in the phone process.  The Binder thread
     * may provide a {@link MainThreadRequest} object in the msg.obj field that they are waiting
     * on, which will be notified when the operation completes and will contain the result of the
     * request.
     *
     * <p>If a MainThreadRequest object is provided in the msg.obj field,
     * note that request.result must be set to something non-null for the calling thread to
     * unblock.
     */
    private final class MainThreadHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            MainThreadRequest request;
            Message onCompleted;
            AsyncResult ar;
            UiccCard uiccCard;
            IccAPDUArgument iccArgument;

            switch (msg.what) {
                 case CMD_HANDLE_USSD_REQUEST: {
                     request = (MainThreadRequest) msg.obj;
                     final Phone phone = getPhoneFromRequest(request);
                     Pair<String, ResultReceiver> ussdObject = (Pair) request.argument;
                     String ussdRequest =  ussdObject.first;
                     ResultReceiver wrappedCallback = ussdObject.second;

                     if (!isUssdApiAllowed(request.subId)) {
                         // Carrier does not support use of this API, return failure.
                         Rlog.w(LOG_TAG, "handleUssdRequest: carrier does not support USSD apis.");
                         UssdResponse response = new UssdResponse(ussdRequest, null);
                         Bundle returnData = new Bundle();
                         returnData.putParcelable(TelephonyManager.USSD_RESPONSE, response);
                         wrappedCallback.send(TelephonyManager.USSD_RETURN_FAILURE, returnData);

                         request.result = true;
                         synchronized (request) {
                             request.notifyAll();
                         }
                         return;
                     }

                     try {
                         request.result = phone != null ?
                                 phone.handleUssdRequest(ussdRequest, wrappedCallback)
                                 : false;
                     } catch (CallStateException cse) {
                         request.result = false;
                     }
                     // Wake up the requesting thread
                     synchronized (request) {
                         request.notifyAll();
                     }
                     break;
                }

                case CMD_HANDLE_PIN_MMI: {
                    request = (MainThreadRequest) msg.obj;
                    final Phone phone = getPhoneFromRequest(request);
                    request.result = phone != null ?
                            getPhoneFromRequest(request).handlePinMmi((String) request.argument)
                            : false;
                    // Wake up the requesting thread
                    synchronized (request) {
                        request.notifyAll();
                    }
                    break;
                }

                case CMD_HANDLE_NEIGHBORING_CELL:
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_NEIGHBORING_CELL_DONE,
                            request);
                    mPhone.getNeighboringCids(onCompleted, (WorkSource)request.argument);
                    break;

                case EVENT_NEIGHBORING_CELL_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    if (ar.exception == null && ar.result != null) {
                        request.result = ar.result;
                    } else {
                        // create an empty list to notify the waiting thread
                        request.result = new ArrayList<NeighboringCellInfo>(0);
                    }
                    // Wake up the requesting thread
                    synchronized (request) {
                        request.notifyAll();
                    }
                    break;

                case CMD_ANSWER_RINGING_CALL:
                    request = (MainThreadRequest) msg.obj;
                    int answer_subId = request.subId;
                    answerRingingCallInternal(answer_subId);
                    break;

                case CMD_END_CALL:
                    request = (MainThreadRequest) msg.obj;
                    int end_subId = request.subId;
                    final boolean hungUp;
                    Phone phone = getPhone(end_subId);
                    if (phone == null) {
                        if (DBG) log("CMD_END_CALL: no phone for id: " + end_subId);
                        break;
                    }
                    int phoneType = phone.getPhoneType();
                    if (phoneType == PhoneConstants.PHONE_TYPE_CDMA) {
                        // CDMA: If the user presses the Power button we treat it as
                        // ending the complete call session
                        hungUp = PhoneUtils.hangupRingingAndActive(getPhone(end_subId));
                    } else if (phoneType == PhoneConstants.PHONE_TYPE_GSM) {
                        // GSM: End the call as per the Phone state
                        hungUp = PhoneUtils.hangup(mCM);
                    } else {
                        throw new IllegalStateException("Unexpected phone type: " + phoneType);
                    }
                    if (DBG) log("CMD_END_CALL: " + (hungUp ? "hung up!" : "no call to hang up"));
                    request.result = hungUp;
                    // Wake up the requesting thread
                    synchronized (request) {
                        request.notifyAll();
                    }
                    break;

                case CMD_TRANSMIT_APDU_LOGICAL_CHANNEL:
                    request = (MainThreadRequest) msg.obj;
                    iccArgument = (IccAPDUArgument) request.argument;
                    uiccCard = getUiccCardFromRequest(request);
                    if (uiccCard == null) {
                        loge("iccTransmitApduLogicalChannel: No UICC");
                        request.result = new IccIoResult(0x6F, 0, (byte[])null);
                        synchronized (request) {
                            request.notifyAll();
                        }
                    } else {
                        onCompleted = obtainMessage(EVENT_TRANSMIT_APDU_LOGICAL_CHANNEL_DONE,
                            request);
                        uiccCard.iccTransmitApduLogicalChannel(
                            iccArgument.channel, iccArgument.cla, iccArgument.command,
                            iccArgument.p1, iccArgument.p2, iccArgument.p3, iccArgument.data,
                            onCompleted);
                    }
                    break;

                case EVENT_TRANSMIT_APDU_LOGICAL_CHANNEL_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    if (ar.exception == null && ar.result != null) {
                        request.result = ar.result;
                    } else {
                        request.result = new IccIoResult(0x6F, 0, (byte[])null);
                        if (ar.result == null) {
                            loge("iccTransmitApduLogicalChannel: Empty response");
                        } else if (ar.exception instanceof CommandException) {
                            loge("iccTransmitApduLogicalChannel: CommandException: " +
                                    ar.exception);
                        } else {
                            loge("iccTransmitApduLogicalChannel: Unknown exception");
                        }
                    }
                    synchronized (request) {
                        request.notifyAll();
                    }
                    break;

                case CMD_TRANSMIT_APDU_BASIC_CHANNEL:
                    request = (MainThreadRequest) msg.obj;
                    iccArgument = (IccAPDUArgument) request.argument;
                    uiccCard = getUiccCardFromRequest(request);
                    if (uiccCard == null) {
                        loge("iccTransmitApduBasicChannel: No UICC");
                        request.result = new IccIoResult(0x6F, 0, (byte[])null);
                        synchronized (request) {
                            request.notifyAll();
                        }
                    } else {
                        onCompleted = obtainMessage(EVENT_TRANSMIT_APDU_BASIC_CHANNEL_DONE,
                            request);
                        uiccCard.iccTransmitApduBasicChannel(
                            iccArgument.cla, iccArgument.command, iccArgument.p1, iccArgument.p2,
                            iccArgument.p3, iccArgument.data, onCompleted);
                    }
                    break;

                case EVENT_TRANSMIT_APDU_BASIC_CHANNEL_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    if (ar.exception == null && ar.result != null) {
                        request.result = ar.result;
                    } else {
                        request.result = new IccIoResult(0x6F, 0, (byte[])null);
                        if (ar.result == null) {
                            loge("iccTransmitApduBasicChannel: Empty response");
                        } else if (ar.exception instanceof CommandException) {
                            loge("iccTransmitApduBasicChannel: CommandException: " +
                                    ar.exception);
                        } else {
                            loge("iccTransmitApduBasicChannel: Unknown exception");
                        }
                    }
                    synchronized (request) {
                        request.notifyAll();
                    }
                    break;

                case CMD_EXCHANGE_SIM_IO:
                    request = (MainThreadRequest) msg.obj;
                    iccArgument = (IccAPDUArgument) request.argument;
                    uiccCard = getUiccCardFromRequest(request);
                    if (uiccCard == null) {
                        loge("iccExchangeSimIO: No UICC");
                        request.result = new IccIoResult(0x6F, 0, (byte[])null);
                        synchronized (request) {
                            request.notifyAll();
                        }
                    } else {
                        onCompleted = obtainMessage(EVENT_EXCHANGE_SIM_IO_DONE,
                                request);
                        uiccCard.iccExchangeSimIO(iccArgument.cla, /* fileID */
                                iccArgument.command, iccArgument.p1, iccArgument.p2, iccArgument.p3,
                                iccArgument.data, onCompleted);
                    }
                    break;

                case EVENT_EXCHANGE_SIM_IO_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    if (ar.exception == null && ar.result != null) {
                        request.result = ar.result;
                    } else {
                        request.result = new IccIoResult(0x6f, 0, (byte[])null);
                    }
                    synchronized (request) {
                        request.notifyAll();
                    }
                    break;

                case CMD_SEND_ENVELOPE:
                    request = (MainThreadRequest) msg.obj;
                    uiccCard = getUiccCardFromRequest(request);
                    if (uiccCard == null) {
                        loge("sendEnvelopeWithStatus: No UICC");
                        request.result = new IccIoResult(0x6F, 0, (byte[])null);
                        synchronized (request) {
                            request.notifyAll();
                        }
                    } else {
                        onCompleted = obtainMessage(EVENT_SEND_ENVELOPE_DONE, request);
                        uiccCard.sendEnvelopeWithStatus((String)request.argument, onCompleted);
                    }
                    break;

                case EVENT_SEND_ENVELOPE_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    if (ar.exception == null && ar.result != null) {
                        request.result = ar.result;
                    } else {
                        request.result = new IccIoResult(0x6F, 0, (byte[])null);
                        if (ar.result == null) {
                            loge("sendEnvelopeWithStatus: Empty response");
                        } else if (ar.exception instanceof CommandException) {
                            loge("sendEnvelopeWithStatus: CommandException: " +
                                    ar.exception);
                        } else {
                            loge("sendEnvelopeWithStatus: exception:" + ar.exception);
                        }
                    }
                    synchronized (request) {
                        request.notifyAll();
                    }
                    break;

                case CMD_OPEN_CHANNEL:
                    request = (MainThreadRequest) msg.obj;
                    uiccCard = getUiccCardFromRequest(request);
                    Pair<String, Integer> openChannelArgs = (Pair<String, Integer>) request.argument;
                    if (uiccCard == null) {
                        loge("iccOpenLogicalChannel: No UICC");
                        request.result = new IccOpenLogicalChannelResponse(-1,
                            IccOpenLogicalChannelResponse.STATUS_MISSING_RESOURCE, null);
                        synchronized (request) {
                            request.notifyAll();
                        }
                    } else {
                        onCompleted = obtainMessage(EVENT_OPEN_CHANNEL_DONE, request);
                        uiccCard.iccOpenLogicalChannel(openChannelArgs.first,
                                openChannelArgs.second, onCompleted);
                    }
                    break;

                case EVENT_OPEN_CHANNEL_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    IccOpenLogicalChannelResponse openChannelResp;
                    if (ar.exception == null && ar.result != null) {
                        int[] result = (int[]) ar.result;
                        int channelId = result[0];
                        byte[] selectResponse = null;
                        if (result.length > 1) {
                            selectResponse = new byte[result.length - 1];
                            for (int i = 1; i < result.length; ++i) {
                                selectResponse[i - 1] = (byte) result[i];
                            }
                        }
                        openChannelResp = new IccOpenLogicalChannelResponse(channelId,
                            IccOpenLogicalChannelResponse.STATUS_NO_ERROR, selectResponse);
                    } else {
                        if (ar.result == null) {
                            loge("iccOpenLogicalChannel: Empty response");
                        }
                        if (ar.exception != null) {
                            loge("iccOpenLogicalChannel: Exception: " + ar.exception);
                        }

                        int errorCode = IccOpenLogicalChannelResponse.STATUS_UNKNOWN_ERROR;
                        if (ar.exception instanceof CommandException) {
                            CommandException.Error error =
                                ((CommandException) (ar.exception)).getCommandError();
                            if (error == CommandException.Error.MISSING_RESOURCE) {
                                errorCode = IccOpenLogicalChannelResponse.STATUS_MISSING_RESOURCE;
                            } else if (error == CommandException.Error.NO_SUCH_ELEMENT) {
                                errorCode = IccOpenLogicalChannelResponse.STATUS_NO_SUCH_ELEMENT;
                            }
                        }
                        openChannelResp = new IccOpenLogicalChannelResponse(
                            IccOpenLogicalChannelResponse.INVALID_CHANNEL, errorCode, null);
                    }
                    request.result = openChannelResp;
                    synchronized (request) {
                        request.notifyAll();
                    }
                    break;

                case CMD_CLOSE_CHANNEL:
                    request = (MainThreadRequest) msg.obj;
                    uiccCard = getUiccCardFromRequest(request);
                    if (uiccCard == null) {
                        loge("iccCloseLogicalChannel: No UICC");
                        request.result = new IccIoResult(0x6F, 0, (byte[])null);
                        synchronized (request) {
                            request.notifyAll();
                        }
                    } else {
                        onCompleted = obtainMessage(EVENT_CLOSE_CHANNEL_DONE, request);
                        uiccCard.iccCloseLogicalChannel((Integer) request.argument, onCompleted);
                    }
                    break;

                case EVENT_CLOSE_CHANNEL_DONE:
                    handleNullReturnEvent(msg, "iccCloseLogicalChannel");
                    break;

                case CMD_NV_READ_ITEM:
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_NV_READ_ITEM_DONE, request);
                    mPhone.nvReadItem((Integer) request.argument, onCompleted);
                    break;

                case EVENT_NV_READ_ITEM_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    if (ar.exception == null && ar.result != null) {
                        request.result = ar.result;     // String
                    } else {
                        request.result = "";
                        if (ar.result == null) {
                            loge("nvReadItem: Empty response");
                        } else if (ar.exception instanceof CommandException) {
                            loge("nvReadItem: CommandException: " +
                                    ar.exception);
                        } else {
                            loge("nvReadItem: Unknown exception");
                        }
                    }
                    synchronized (request) {
                        request.notifyAll();
                    }
                    break;

                case CMD_NV_WRITE_ITEM:
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_NV_WRITE_ITEM_DONE, request);
                    Pair<Integer, String> idValue = (Pair<Integer, String>) request.argument;
                    mPhone.nvWriteItem(idValue.first, idValue.second, onCompleted);
                    break;

                case EVENT_NV_WRITE_ITEM_DONE:
                    handleNullReturnEvent(msg, "nvWriteItem");
                    break;

                case CMD_NV_WRITE_CDMA_PRL:
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_NV_WRITE_CDMA_PRL_DONE, request);
                    mPhone.nvWriteCdmaPrl((byte[]) request.argument, onCompleted);
                    break;

                case EVENT_NV_WRITE_CDMA_PRL_DONE:
                    handleNullReturnEvent(msg, "nvWriteCdmaPrl");
                    break;

                case CMD_NV_RESET_CONFIG:
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_NV_RESET_CONFIG_DONE, request);
                    mPhone.nvResetConfig((Integer) request.argument, onCompleted);
                    break;

                case EVENT_NV_RESET_CONFIG_DONE:
                    handleNullReturnEvent(msg, "nvResetConfig");
                    break;

                case CMD_GET_PREFERRED_NETWORK_TYPE:
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_GET_PREFERRED_NETWORK_TYPE_DONE, request);
                    getPhoneFromRequest(request).getPreferredNetworkType(onCompleted);
                    break;

                case EVENT_GET_PREFERRED_NETWORK_TYPE_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    if (ar.exception == null && ar.result != null) {
                        request.result = ar.result;     // Integer
                    } else {
                        request.result = null;
                        if (ar.result == null) {
                            loge("getPreferredNetworkType: Empty response");
                        } else if (ar.exception instanceof CommandException) {
                            loge("getPreferredNetworkType: CommandException: " +
                                    ar.exception);
                        } else {
                            loge("getPreferredNetworkType: Unknown exception");
                        }
                    }
                    synchronized (request) {
                        request.notifyAll();
                    }
                    break;

                case CMD_SET_PREFERRED_NETWORK_TYPE:
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_SET_PREFERRED_NETWORK_TYPE_DONE, request);
                    int networkType = (Integer) request.argument;
                    getPhoneFromRequest(request).setPreferredNetworkType(networkType, onCompleted);
                    break;

                case EVENT_SET_PREFERRED_NETWORK_TYPE_DONE:
                    handleNullReturnEvent(msg, "setPreferredNetworkType");
                    break;

                case CMD_INVOKE_OEM_RIL_REQUEST_RAW:
                    request = (MainThreadRequest)msg.obj;
                    onCompleted = obtainMessage(EVENT_INVOKE_OEM_RIL_REQUEST_RAW_DONE, request);
                    mPhone.invokeOemRilRequestRaw((byte[])request.argument, onCompleted);
                    break;

                case EVENT_INVOKE_OEM_RIL_REQUEST_RAW_DONE:
                    ar = (AsyncResult)msg.obj;
                    request = (MainThreadRequest)ar.userObj;
                    request.result = ar;
                    synchronized (request) {
                        request.notifyAll();
                    }
                    break;

                case CMD_SET_VOICEMAIL_NUMBER:
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_SET_VOICEMAIL_NUMBER_DONE, request);
                    Pair<String, String> tagNum = (Pair<String, String>) request.argument;
                    getPhoneFromRequest(request).setVoiceMailNumber(tagNum.first, tagNum.second,
                            onCompleted);
                    break;

                case EVENT_SET_VOICEMAIL_NUMBER_DONE:
                    handleNullReturnEvent(msg, "setVoicemailNumber");
                    break;

                case CMD_SET_NETWORK_SELECTION_MODE_AUTOMATIC:
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_SET_NETWORK_SELECTION_MODE_AUTOMATIC_DONE,
                            request);
                    getPhoneFromRequest(request).setNetworkSelectionModeAutomatic(onCompleted);
                    break;

                case EVENT_SET_NETWORK_SELECTION_MODE_AUTOMATIC_DONE:
                    handleNullReturnEvent(msg, "setNetworkSelectionModeAutomatic");
                    break;

                case CMD_PERFORM_NETWORK_SCAN:
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_PERFORM_NETWORK_SCAN_DONE, request);
                    getPhoneFromRequest(request).getAvailableNetworks(onCompleted);
                    break;

                case EVENT_PERFORM_NETWORK_SCAN_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    CellNetworkScanResult cellScanResult;
                    if (ar.exception == null && ar.result != null) {
                        cellScanResult = new CellNetworkScanResult(
                                CellNetworkScanResult.STATUS_SUCCESS,
                                (List<OperatorInfo>) ar.result);
                    } else {
                        if (ar.result == null) {
                            loge("getCellNetworkScanResults: Empty response");
                        }
                        if (ar.exception != null) {
                            loge("getCellNetworkScanResults: Exception: " + ar.exception);
                        }
                        int errorCode = CellNetworkScanResult.STATUS_UNKNOWN_ERROR;
                        if (ar.exception instanceof CommandException) {
                            CommandException.Error error =
                                ((CommandException) (ar.exception)).getCommandError();
                            if (error == CommandException.Error.RADIO_NOT_AVAILABLE) {
                                errorCode = CellNetworkScanResult.STATUS_RADIO_NOT_AVAILABLE;
                            } else if (error == CommandException.Error.GENERIC_FAILURE) {
                                errorCode = CellNetworkScanResult.STATUS_RADIO_GENERIC_FAILURE;
                            }
                        }
                        cellScanResult = new CellNetworkScanResult(errorCode, null);
                    }
                    request.result = cellScanResult;
                    synchronized (request) {
                        request.notifyAll();
                    }
                    break;

                case CMD_SET_NETWORK_SELECTION_MODE_MANUAL:
                    request = (MainThreadRequest) msg.obj;
                    ManualNetworkSelectionArgument selArg =
                            (ManualNetworkSelectionArgument) request.argument;
                    onCompleted = obtainMessage(EVENT_SET_NETWORK_SELECTION_MODE_MANUAL_DONE,
                            request);
                    getPhoneFromRequest(request).selectNetworkManually(selArg.operatorInfo,
                            selArg.persistSelection, onCompleted);
                    break;

                case EVENT_SET_NETWORK_SELECTION_MODE_MANUAL_DONE:
                    handleNullReturnEvent(msg, "setNetworkSelectionModeManual");
                    break;

                case CMD_GET_MODEM_ACTIVITY_INFO:
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_GET_MODEM_ACTIVITY_INFO_DONE, request);
                    mPhone.getModemActivityInfo(onCompleted);
                    break;

                case EVENT_GET_MODEM_ACTIVITY_INFO_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    if (ar.exception == null && ar.result != null) {
                        request.result = ar.result;
                    } else {
                        if (ar.result == null) {
                            loge("queryModemActivityInfo: Empty response");
                        } else if (ar.exception instanceof CommandException) {
                            loge("queryModemActivityInfo: CommandException: " +
                                    ar.exception);
                        } else {
                            loge("queryModemActivityInfo: Unknown exception");
                        }
                    }
                    // Result cannot be null. Return ModemActivityInfo with all fields set to 0.
                    if (request.result == null) {
                        request.result = new ModemActivityInfo(0, 0, 0, null, 0, 0);
                    }
                    synchronized (request) {
                        request.notifyAll();
                    }
                    break;

                case CMD_SET_ALLOWED_CARRIERS:
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_SET_ALLOWED_CARRIERS_DONE, request);
                    mPhone.setAllowedCarriers(
                            (List<CarrierIdentifier>) request.argument,
                            onCompleted);
                    break;

                case EVENT_SET_ALLOWED_CARRIERS_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    if (ar.exception == null && ar.result != null) {
                        request.result = ar.result;
                    } else {
                        if (ar.result == null) {
                            loge("setAllowedCarriers: Empty response");
                        } else if (ar.exception instanceof CommandException) {
                            loge("setAllowedCarriers: CommandException: " +
                                    ar.exception);
                        } else {
                            loge("setAllowedCarriers: Unknown exception");
                        }
                    }
                    // Result cannot be null. Return -1 on error.
                    if (request.result == null) {
                        request.result = new int[]{-1};
                    }
                    synchronized (request) {
                        request.notifyAll();
                    }
                    break;

                case CMD_GET_ALLOWED_CARRIERS:
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_GET_ALLOWED_CARRIERS_DONE, request);
                    mPhone.getAllowedCarriers(onCompleted);
                    break;

                case EVENT_GET_ALLOWED_CARRIERS_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    if (ar.exception == null && ar.result != null) {
                        request.result = ar.result;
                    } else {
                        if (ar.result == null) {
                            loge("getAllowedCarriers: Empty response");
                        } else if (ar.exception instanceof CommandException) {
                            loge("getAllowedCarriers: CommandException: " +
                                    ar.exception);
                        } else {
                            loge("getAllowedCarriers: Unknown exception");
                        }
                    }
                    // Result cannot be null. Return empty list of CarrierIdentifier.
                    if (request.result == null) {
                        request.result = new ArrayList<CarrierIdentifier>(0);
                    }
                    synchronized (request) {
                        request.notifyAll();
                    }
                    break;

                case EVENT_GET_FORBIDDEN_PLMNS_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    if (ar.exception == null && ar.result != null) {
                        request.result = ar.result;
                    } else {
                        request.result = new IllegalArgumentException(
                                "Failed to retrieve Forbidden Plmns");
                        if (ar.result == null) {
                            loge("getForbiddenPlmns: Empty response");
                        } else {
                            loge("getForbiddenPlmns: Unknown exception");
                        }
                    }
                    synchronized (request) {
                        request.notifyAll();
                    }
                    break;

                case CMD_GET_FORBIDDEN_PLMNS:
                    request = (MainThreadRequest) msg.obj;
                    uiccCard = getUiccCardFromRequest(request);
                    if (uiccCard == null) {
                        loge("getForbiddenPlmns() UiccCard is null");
                        request.result = new IllegalArgumentException(
                                "getForbiddenPlmns() UiccCard is null");
                        synchronized (request) {
                            request.notifyAll();
                        }
                        break;
                    }
                    Integer appType = (Integer) request.argument;
                    UiccCardApplication uiccApp = uiccCard.getApplicationByType(appType);
                    if (uiccApp == null) {
                        loge("getForbiddenPlmns() no app with specified type -- "
                                + appType);
                        request.result = new IllegalArgumentException("Failed to get UICC App");
                        synchronized (request) {
                            request.notifyAll();
                        }
                        break;
                    } else {
                        if (DBG) logv("getForbiddenPlmns() found app " + uiccApp.getAid()
                                + " specified type -- " + appType);
                    }
                    onCompleted = obtainMessage(EVENT_GET_FORBIDDEN_PLMNS_DONE, request);
                    ((SIMRecords) uiccApp.getIccRecords()).getForbiddenPlmns(
                              onCompleted);
                    break;

                default:
                    Log.w(LOG_TAG, "MainThreadHandler: unexpected message code: " + msg.what);
                    break;
            }
        }

        private void handleNullReturnEvent(Message msg, String command) {
            AsyncResult ar = (AsyncResult) msg.obj;
            MainThreadRequest request = (MainThreadRequest) ar.userObj;
            if (ar.exception == null) {
                request.result = true;
            } else {
                request.result = false;
                if (ar.exception instanceof CommandException) {
                    loge(command + ": CommandException: " + ar.exception);
                } else {
                    loge(command + ": Unknown exception");
                }
            }
            synchronized (request) {
                request.notifyAll();
            }
        }
    }

    /**
     * Posts the specified command to be executed on the main thread,
     * waits for the request to complete, and returns the result.
     * @see #sendRequestAsync
     */
    private Object sendRequest(int command, Object argument) {
        return sendRequest(command, argument, SubscriptionManager.INVALID_SUBSCRIPTION_ID);
    }

    /**
     * Posts the specified command to be executed on the main thread,
     * waits for the request to complete, and returns the result.
     * @see #sendRequestAsync
     */
    private Object sendRequest(int command, Object argument, Integer subId) {
        if (Looper.myLooper() == mMainThreadHandler.getLooper()) {
            throw new RuntimeException("This method will deadlock if called from the main thread.");
        }

        MainThreadRequest request = new MainThreadRequest(argument, subId);
        Message msg = mMainThreadHandler.obtainMessage(command, request);
        msg.sendToTarget();

        // Wait for the request to complete
        synchronized (request) {
            while (request.result == null) {
                try {
                    request.wait();
                } catch (InterruptedException e) {
                    // Do nothing, go back and wait until the request is complete
                }
            }
        }
        return request.result;
    }

    /**
     * Asynchronous ("fire and forget") version of sendRequest():
     * Posts the specified command to be executed on the main thread, and
     * returns immediately.
     * @see #sendRequest
     */
    private void sendRequestAsync(int command) {
        mMainThreadHandler.sendEmptyMessage(command);
    }

    /**
     * Same as {@link #sendRequestAsync(int)} except it takes an argument.
     * @see {@link #sendRequest(int,Object)}
     */
    private void sendRequestAsync(int command, Object argument) {
        MainThreadRequest request = new MainThreadRequest(argument);
        Message msg = mMainThreadHandler.obtainMessage(command, request);
        msg.sendToTarget();
    }

    /**
     * Initialize the singleton PhoneInterfaceManager instance.
     * This is only done once, at startup, from PhoneApp.onCreate().
     */
    /* package */ static PhoneInterfaceManager init(PhoneGlobals app, Phone phone) {
        synchronized (PhoneInterfaceManager.class) {
            if (sInstance == null) {
                sInstance = new PhoneInterfaceManager(app, phone);
            } else {
                Log.wtf(LOG_TAG, "init() called multiple times!  sInstance = " + sInstance);
            }
            return sInstance;
        }
    }

    /** Private constructor; @see init() */
    private PhoneInterfaceManager(PhoneGlobals app, Phone phone) {
        mApp = app;
        mPhone = phone;
        mCM = PhoneGlobals.getInstance().mCM;
        mUserManager = (UserManager) app.getSystemService(Context.USER_SERVICE);
        mAppOps = (AppOpsManager)app.getSystemService(Context.APP_OPS_SERVICE);
        mMainThreadHandler = new MainThreadHandler();
        mTelephonySharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(mPhone.getContext());
        mSubscriptionController = SubscriptionController.getInstance();
        mNetworkScanRequestTracker = new NetworkScanRequestTracker();

        publish();
    }

    private void publish() {
        if (DBG) log("publish: " + this);

        ServiceManager.addService("phone", this);
    }

    private Phone getPhoneFromRequest(MainThreadRequest request) {
        return (request.subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID)
                ? mPhone : getPhone(request.subId);
    }

    private UiccCard getUiccCardFromRequest(MainThreadRequest request) {
        Phone phone = getPhoneFromRequest(request);
        return phone == null ? null :
                UiccController.getInstance().getUiccCard(phone.getPhoneId());
    }

    // returns phone associated with the subId.
    private Phone getPhone(int subId) {
        return PhoneFactory.getPhone(mSubscriptionController.getPhoneId(subId));
    }
    //
    // Implementation of the ITelephony interface.
    //

    public void dial(String number) {
        dialForSubscriber(getPreferredVoiceSubscription(), number);
    }

    public void dialForSubscriber(int subId, String number) {
        if (DBG) log("dial: " + number);
        // No permission check needed here: This is just a wrapper around the
        // ACTION_DIAL intent, which is available to any app since it puts up
        // the UI before it does anything.

        String url = createTelUrl(number);
        if (url == null) {
            return;
        }

        // PENDING: should we just silently fail if phone is offhook or ringing?
        PhoneConstants.State state = mCM.getState(subId);
        if (state != PhoneConstants.State.OFFHOOK && state != PhoneConstants.State.RINGING) {
            Intent  intent = new Intent(Intent.ACTION_DIAL, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mApp.startActivity(intent);
        }
    }

    public void call(String callingPackage, String number) {
        callForSubscriber(getPreferredVoiceSubscription(), callingPackage, number);
    }

    public void callForSubscriber(int subId, String callingPackage, String number) {
        if (DBG) log("call: " + number);

        // This is just a wrapper around the ACTION_CALL intent, but we still
        // need to do a permission check since we're calling startActivity()
        // from the context of the phone app.
        enforceCallPermission();

        if (mAppOps.noteOp(AppOpsManager.OP_CALL_PHONE, Binder.getCallingUid(), callingPackage)
                != AppOpsManager.MODE_ALLOWED) {
            return;
        }

        String url = createTelUrl(number);
        if (url == null) {
            return;
        }

        boolean isValid = false;
        final List<SubscriptionInfo> slist = getActiveSubscriptionInfoList();
        if (slist != null) {
            for (SubscriptionInfo subInfoRecord : slist) {
                if (subInfoRecord.getSubscriptionId() == subId) {
                    isValid = true;
                    break;
                }
            }
        }
        if (isValid == false) {
            return;
        }

        Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse(url));
        intent.putExtra(SUBSCRIPTION_KEY, subId);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mApp.startActivity(intent);
    }

    /**
     * End a call based on call state
     * @return true is a call was ended
     */
    public boolean endCall() {
        return endCallForSubscriber(getDefaultSubscription());
    }

    /**
     * End a call based on the call state of the subId
     * @return true is a call was ended
     */
    public boolean endCallForSubscriber(int subId) {
        enforceCallPermission();
        return (Boolean) sendRequest(CMD_END_CALL, null, new Integer(subId));
    }

    public void answerRingingCall() {
        answerRingingCallForSubscriber(getDefaultSubscription());
    }

    public void answerRingingCallForSubscriber(int subId) {
        if (DBG) log("answerRingingCall...");
        // TODO: there should eventually be a separate "ANSWER_PHONE" permission,
        // but that can probably wait till the big TelephonyManager API overhaul.
        // For now, protect this call with the MODIFY_PHONE_STATE permission.
        enforceModifyPermission();
        sendRequest(CMD_ANSWER_RINGING_CALL, null, new Integer(subId));
    }

    /**
     * Make the actual telephony calls to implement answerRingingCall().
     * This should only be called from the main thread of the Phone app.
     * @see #answerRingingCall
     *
     * TODO: it would be nice to return true if we answered the call, or
     * false if there wasn't actually a ringing incoming call, or some
     * other error occurred.  (In other words, pass back the return value
     * from PhoneUtils.answerCall() or PhoneUtils.answerAndEndActive().)
     * But that would require calling this method via sendRequest() rather
     * than sendRequestAsync(), and right now we don't actually *need* that
     * return value, so let's just return void for now.
     */
    private void answerRingingCallInternal(int subId) {
        final boolean hasRingingCall = !getPhone(subId).getRingingCall().isIdle();
        if (hasRingingCall) {
            final boolean hasActiveCall = !getPhone(subId).getForegroundCall().isIdle();
            final boolean hasHoldingCall = !getPhone(subId).getBackgroundCall().isIdle();
            if (hasActiveCall && hasHoldingCall) {
                // Both lines are in use!
                // TODO: provide a flag to let the caller specify what
                // policy to use if both lines are in use.  (The current
                // behavior is hardwired to "answer incoming, end ongoing",
                // which is how the CALL button is specced to behave.)
                PhoneUtils.answerAndEndActive(mCM, mCM.getFirstActiveRingingCall());
                return;
            } else {
                // answerCall() will automatically hold the current active
                // call, if there is one.
                PhoneUtils.answerCall(mCM.getFirstActiveRingingCall());
                return;
            }
        } else {
            // No call was ringing.
            return;
        }
    }

    /**
     * This method is no longer used and can be removed once TelephonyManager stops referring to it.
     */
    public void silenceRinger() {
        Log.e(LOG_TAG, "silenseRinger not supported");
    }

    @Override
    public boolean isOffhook(String callingPackage) {
        return isOffhookForSubscriber(getDefaultSubscription(), callingPackage);
    }

    @Override
    public boolean isOffhookForSubscriber(int subId, String callingPackage) {
        if (!canReadPhoneState(callingPackage, "isOffhookForSubscriber")) {
            return false;
        }

        final Phone phone = getPhone(subId);
        if (phone != null) {
            return (phone.getState() == PhoneConstants.State.OFFHOOK);
        } else {
            return false;
        }
    }

    @Override
    public boolean isRinging(String callingPackage) {
        return (isRingingForSubscriber(getDefaultSubscription(), callingPackage));
    }

    @Override
    public boolean isRingingForSubscriber(int subId, String callingPackage) {
        if (!canReadPhoneState(callingPackage, "isRingingForSubscriber")) {
            return false;
        }

        final Phone phone = getPhone(subId);
        if (phone != null) {
            return (phone.getState() == PhoneConstants.State.RINGING);
        } else {
            return false;
        }
    }

    @Override
    public boolean isIdle(String callingPackage) {
        return isIdleForSubscriber(getDefaultSubscription(), callingPackage);
    }

    @Override
    public boolean isIdleForSubscriber(int subId, String callingPackage) {
        if (!canReadPhoneState(callingPackage, "isIdleForSubscriber")) {
            return false;
        }

        final Phone phone = getPhone(subId);
        if (phone != null) {
            return (phone.getState() == PhoneConstants.State.IDLE);
        } else {
            return false;
        }
    }

    public boolean supplyPin(String pin) {
        return supplyPinForSubscriber(getDefaultSubscription(), pin);
    }

    public boolean supplyPinForSubscriber(int subId, String pin) {
        int [] resultArray = supplyPinReportResultForSubscriber(subId, pin);
        return (resultArray[0] == PhoneConstants.PIN_RESULT_SUCCESS) ? true : false;
    }

    public boolean supplyPuk(String puk, String pin) {
        return supplyPukForSubscriber(getDefaultSubscription(), puk, pin);
    }

    public boolean supplyPukForSubscriber(int subId, String puk, String pin) {
        int [] resultArray = supplyPukReportResultForSubscriber(subId, puk, pin);
        return (resultArray[0] == PhoneConstants.PIN_RESULT_SUCCESS) ? true : false;
    }

    /** {@hide} */
    public int[] supplyPinReportResult(String pin) {
        return supplyPinReportResultForSubscriber(getDefaultSubscription(), pin);
    }

    public int[] supplyPinReportResultForSubscriber(int subId, String pin) {
        enforceModifyPermission();
        final UnlockSim checkSimPin = new UnlockSim(getPhone(subId).getIccCard());
        checkSimPin.start();
        return checkSimPin.unlockSim(null, pin);
    }

    /** {@hide} */
    public int[] supplyPukReportResult(String puk, String pin) {
        return supplyPukReportResultForSubscriber(getDefaultSubscription(), puk, pin);
    }

    public int[] supplyPukReportResultForSubscriber(int subId, String puk, String pin) {
        enforceModifyPermission();
        final UnlockSim checkSimPuk = new UnlockSim(getPhone(subId).getIccCard());
        checkSimPuk.start();
        return checkSimPuk.unlockSim(puk, pin);
    }

    /**
     * Helper thread to turn async call to SimCard#supplyPin into
     * a synchronous one.
     */
    private static class UnlockSim extends Thread {

        private final IccCard mSimCard;

        private boolean mDone = false;
        private int mResult = PhoneConstants.PIN_GENERAL_FAILURE;
        private int mRetryCount = -1;

        // For replies from SimCard interface
        private Handler mHandler;

        // For async handler to identify request type
        private static final int SUPPLY_PIN_COMPLETE = 100;

        public UnlockSim(IccCard simCard) {
            mSimCard = simCard;
        }

        @Override
        public void run() {
            Looper.prepare();
            synchronized (UnlockSim.this) {
                mHandler = new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        AsyncResult ar = (AsyncResult) msg.obj;
                        switch (msg.what) {
                            case SUPPLY_PIN_COMPLETE:
                                Log.d(LOG_TAG, "SUPPLY_PIN_COMPLETE");
                                synchronized (UnlockSim.this) {
                                    mRetryCount = msg.arg1;
                                    if (ar.exception != null) {
                                        if (ar.exception instanceof CommandException &&
                                                ((CommandException)(ar.exception)).getCommandError()
                                                == CommandException.Error.PASSWORD_INCORRECT) {
                                            mResult = PhoneConstants.PIN_PASSWORD_INCORRECT;
                                        } else {
                                            mResult = PhoneConstants.PIN_GENERAL_FAILURE;
                                        }
                                    } else {
                                        mResult = PhoneConstants.PIN_RESULT_SUCCESS;
                                    }
                                    mDone = true;
                                    UnlockSim.this.notifyAll();
                                }
                                break;
                        }
                    }
                };
                UnlockSim.this.notifyAll();
            }
            Looper.loop();
        }

        /*
         * Use PIN or PUK to unlock SIM card
         *
         * If PUK is null, unlock SIM card with PIN
         *
         * If PUK is not null, unlock SIM card with PUK and set PIN code
         */
        synchronized int[] unlockSim(String puk, String pin) {

            while (mHandler == null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            Message callback = Message.obtain(mHandler, SUPPLY_PIN_COMPLETE);

            if (puk == null) {
                mSimCard.supplyPin(pin, callback);
            } else {
                mSimCard.supplyPuk(puk, pin, callback);
            }

            while (!mDone) {
                try {
                    Log.d(LOG_TAG, "wait for done");
                    wait();
                } catch (InterruptedException e) {
                    // Restore the interrupted status
                    Thread.currentThread().interrupt();
                }
            }
            Log.d(LOG_TAG, "done");
            int[] resultArray = new int[2];
            resultArray[0] = mResult;
            resultArray[1] = mRetryCount;
            return resultArray;
        }
    }

    public void updateServiceLocation() {
        updateServiceLocationForSubscriber(getDefaultSubscription());

    }

    public void updateServiceLocationForSubscriber(int subId) {
        // No permission check needed here: this call is harmless, and it's
        // needed for the ServiceState.requestStateUpdate() call (which is
        // already intentionally exposed to 3rd parties.)
        final Phone phone = getPhone(subId);
        if (phone != null) {
            phone.updateServiceLocation();
        }
    }

    @Override
    public boolean isRadioOn(String callingPackage) {
        return isRadioOnForSubscriber(getDefaultSubscription(), callingPackage);
    }

    @Override
    public boolean isRadioOnForSubscriber(int subId, String callingPackage) {
        if (!canReadPhoneState(callingPackage, "isRadioOnForSubscriber")) {
            return false;
        }
        return isRadioOnForSubscriber(subId);
    }

    private boolean isRadioOnForSubscriber(int subId) {
        final Phone phone = getPhone(subId);
        if (phone != null) {
            return phone.getServiceState().getState() != ServiceState.STATE_POWER_OFF;
        } else {
            return false;
        }
    }

    public void toggleRadioOnOff() {
        toggleRadioOnOffForSubscriber(getDefaultSubscription());

    }

    public void toggleRadioOnOffForSubscriber(int subId) {
        enforceModifyPermission();
        final Phone phone = getPhone(subId);
        if (phone != null) {
            phone.setRadioPower(!isRadioOnForSubscriber(subId));
        }
    }

    public boolean setRadio(boolean turnOn) {
        return setRadioForSubscriber(getDefaultSubscription(), turnOn);
    }

    public boolean setRadioForSubscriber(int subId, boolean turnOn) {
        enforceModifyPermission();
        final Phone phone = getPhone(subId);
        if (phone == null) {
            return false;
        }
        if ((phone.getServiceState().getState() !=
                ServiceState.STATE_POWER_OFF) != turnOn) {
            toggleRadioOnOffForSubscriber(subId);
        }
        return true;
    }

    public boolean needMobileRadioShutdown() {
        /*
         * If any of the Radios are available, it will need to be
         * shutdown. So return true if any Radio is available.
         */
        for (int i = 0; i < TelephonyManager.getDefault().getPhoneCount(); i++) {
            Phone phone = PhoneFactory.getPhone(i);
            if (phone != null && phone.isRadioAvailable()) return true;
        }
        logv(TelephonyManager.getDefault().getPhoneCount() + " Phones are shutdown.");
        return false;
    }

    public void shutdownMobileRadios() {
        for (int i = 0; i < TelephonyManager.getDefault().getPhoneCount(); i++) {
            logv("Shutting down Phone " + i);
            shutdownRadioUsingPhoneId(i);
        }
    }

    private void shutdownRadioUsingPhoneId(int phoneId) {
        enforceModifyPermission();
        Phone phone = PhoneFactory.getPhone(phoneId);
        if (phone != null && phone.isRadioAvailable()) {
            phone.shutdownRadio();
        }
    }

    public boolean setRadioPower(boolean turnOn) {
        enforceModifyPermission();
        final Phone defaultPhone = PhoneFactory.getDefaultPhone();
        if (defaultPhone != null) {
            defaultPhone.setRadioPower(turnOn);
            return true;
        } else {
            loge("There's no default phone.");
            return false;
        }
    }

    public boolean setRadioPowerForSubscriber(int subId, boolean turnOn) {
        enforceModifyPermission();
        final Phone phone = getPhone(subId);
        if (phone != null) {
            phone.setRadioPower(turnOn);
            return true;
        } else {
            return false;
        }
    }

    // FIXME: subId version needed
    @Override
    public boolean enableDataConnectivity() {
        enforceModifyPermission();
        int subId = mSubscriptionController.getDefaultDataSubId();
        final Phone phone = getPhone(subId);
        if (phone != null) {
            phone.setDataEnabled(true);
            return true;
        } else {
            return false;
        }
    }

    // FIXME: subId version needed
    @Override
    public boolean disableDataConnectivity() {
        enforceModifyPermission();
        int subId = mSubscriptionController.getDefaultDataSubId();
        final Phone phone = getPhone(subId);
        if (phone != null) {
            phone.setDataEnabled(false);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean isDataConnectivityPossible(int subId) {
        final Phone phone = getPhone(subId);
        if (phone != null) {
            return phone.isDataAllowed();
        } else {
            return false;
        }
    }

    public boolean handlePinMmi(String dialString) {
        return handlePinMmiForSubscriber(getDefaultSubscription(), dialString);
    }

    public void handleUssdRequest(int subId, String ussdRequest, ResultReceiver wrappedCallback) {
      enforceCallPermission();
      if (!SubscriptionManager.isValidSubscriptionId(subId)) {
          return;
      }
      Pair<String, ResultReceiver> ussdObject = new Pair(ussdRequest, wrappedCallback);
      sendRequest(CMD_HANDLE_USSD_REQUEST, ussdObject, subId);
    };

    public boolean handlePinMmiForSubscriber(int subId, String dialString) {
        enforceModifyPermission();
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            return false;
        }
        return (Boolean) sendRequest(CMD_HANDLE_PIN_MMI, dialString, subId);
    }

    public int getCallState() {
        return getCallStateForSlot(getSlotForDefaultSubscription());
    }

    public int getCallStateForSlot(int slotIndex) {
        Phone phone = PhoneFactory.getPhone(slotIndex);
        return phone == null ? TelephonyManager.CALL_STATE_IDLE :
            PhoneConstantConversions.convertCallState(phone.getState());
    }

    @Override
    public int getDataState() {
        Phone phone = getPhone(mSubscriptionController.getDefaultDataSubId());
        if (phone != null) {
            return PhoneConstantConversions.convertDataState(phone.getDataConnectionState());
        } else {
            return PhoneConstantConversions.convertDataState(PhoneConstants.DataState.DISCONNECTED);
        }
    }

    @Override
    public int getDataActivity() {
        Phone phone = getPhone(mSubscriptionController.getDefaultDataSubId());
        if (phone != null) {
            return DefaultPhoneNotifier.convertDataActivityState(phone.getDataActivityState());
        } else {
            return TelephonyManager.DATA_ACTIVITY_NONE;
        }
    }

    @Override
    public Bundle getCellLocation(String callingPackage) {
        if (!LocationAccessPolicy.canAccessCellLocation(mPhone.getContext(),
                callingPackage, Binder.getCallingUid(), "getCellLocation")) {
            return null;
        }

        if (DBG_LOC) log("getCellLocation: is active user");
        Bundle data = new Bundle();
        Phone phone = getPhone(mSubscriptionController.getDefaultDataSubId());
        if (phone == null) {
            return null;
        }

        WorkSource workSource = getWorkSource(null, Binder.getCallingUid());
        phone.getCellLocation(workSource).fillInNotifierBundle(data);
        return data;
    }

    @Override
    public String getNetworkCountryIsoForPhone(int phoneId) {
        // Reporting the correct network country is ambiguous when IWLAN could conflict with
        // registered cell info, so return a NULL country instead.
        final long identity = Binder.clearCallingIdentity();
        try {
            final int subId = mSubscriptionController.getSubIdUsingPhoneId(phoneId);
            if (TelephonyManager.NETWORK_TYPE_IWLAN
                    == getVoiceNetworkTypeForSubscriber(subId, mApp.getPackageName())) {
                return "";
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
        return TelephonyManager.getTelephonyProperty(
                phoneId, TelephonyProperties.PROPERTY_OPERATOR_ISO_COUNTRY, "");
    }

    @Override
    public void enableLocationUpdates() {
        enableLocationUpdatesForSubscriber(getDefaultSubscription());
    }

    @Override
    public void enableLocationUpdatesForSubscriber(int subId) {
        mApp.enforceCallingOrSelfPermission(
                android.Manifest.permission.CONTROL_LOCATION_UPDATES, null);
        final Phone phone = getPhone(subId);
        if (phone != null) {
            phone.enableLocationUpdates();
        }
    }

    @Override
    public void disableLocationUpdates() {
        disableLocationUpdatesForSubscriber(getDefaultSubscription());
    }

    @Override
    public void disableLocationUpdatesForSubscriber(int subId) {
        mApp.enforceCallingOrSelfPermission(
                android.Manifest.permission.CONTROL_LOCATION_UPDATES, null);
        final Phone phone = getPhone(subId);
        if (phone != null) {
            phone.disableLocationUpdates();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<NeighboringCellInfo> getNeighboringCellInfo(String callingPackage) {
        if (!LocationAccessPolicy.canAccessCellLocation(mPhone.getContext(),
                callingPackage, Binder.getCallingUid(), "getNeighboringCellInfo")) {
            return null;
        }

        if (mAppOps.noteOp(AppOpsManager.OP_NEIGHBORING_CELLS, Binder.getCallingUid(),
                callingPackage) != AppOpsManager.MODE_ALLOWED) {
            return null;
        }

        if (DBG_LOC) log("getNeighboringCellInfo: is active user");

        ArrayList<NeighboringCellInfo> cells = null;

        WorkSource workSource = getWorkSource(null, Binder.getCallingUid());
        try {
            cells = (ArrayList<NeighboringCellInfo>) sendRequest(
                    CMD_HANDLE_NEIGHBORING_CELL, workSource,
                    SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        } catch (RuntimeException e) {
            Log.e(LOG_TAG, "getNeighboringCellInfo " + e);
        }
        return cells;
    }


    @Override
    public List<CellInfo> getAllCellInfo(String callingPackage) {
        if (!LocationAccessPolicy.canAccessCellLocation(mPhone.getContext(),
                callingPackage, Binder.getCallingUid(), "getAllCellInfo")) {
            return null;
        }

        if (DBG_LOC) log("getAllCellInfo: is active user");
        WorkSource workSource = getWorkSource(null, Binder.getCallingUid());
        List<CellInfo> cellInfos = new ArrayList<CellInfo>();
        for (Phone phone : PhoneFactory.getPhones()) {
            final List<CellInfo> info = phone.getAllCellInfo(workSource);
            if (info != null) cellInfos.addAll(info);
        }
        return cellInfos;
    }

    @Override
    public void setCellInfoListRate(int rateInMillis) {
        enforceModifyPermission();
        WorkSource workSource = getWorkSource(null, Binder.getCallingUid());
        mPhone.setCellInfoListRate(rateInMillis, workSource);
    }

    @Override
    public String getImeiForSlot(int slotIndex, String callingPackage) {
      if (!canReadPhoneState(callingPackage, "getImeiForSlot")) {
          return null;
      }
      Phone phone = PhoneFactory.getPhone(slotIndex);
      return phone == null ? null : phone.getImei();
    }

    @Override
    public String getMeidForSlot(int slotIndex, String callingPackage) {
        if (!canReadPhoneState(callingPackage, "getMeidForSlot")) {
            return null;
        }
        Phone phone = PhoneFactory.getPhone(slotIndex);
        return phone == null ? null : phone.getMeid();
    }

    @Override
    public String getDeviceSoftwareVersionForSlot(int slotIndex, String callingPackage) {
      if (!canReadPhoneState(callingPackage, "getDeviceSoftwareVersionForSlot")) {
          return null;
      }
      Phone phone = PhoneFactory.getPhone(slotIndex);
      return phone == null ? null : phone.getDeviceSvn();
    }

    //
    // Internal helper methods.
    //

    /**
     * Make sure the caller has the MODIFY_PHONE_STATE permission.
     *
     * @throws SecurityException if the caller does not have the required permission
     */
    private void enforceModifyPermission() {
        mApp.enforceCallingOrSelfPermission(android.Manifest.permission.MODIFY_PHONE_STATE, null);
    }

    /**
     * Make sure either system app or the caller has carrier privilege.
     *
     * @throws SecurityException if the caller does not have the required permission/privilege
     */
    private void enforceModifyPermissionOrCarrierPrivilege(int subId) {
        int permission = mApp.checkCallingOrSelfPermission(
                android.Manifest.permission.MODIFY_PHONE_STATE);
        if (permission == PackageManager.PERMISSION_GRANTED) {
            return;
        }

        log("No modify permission, check carrier privilege next.");
        enforceCarrierPrivilege(subId);
    }

    /**
     * Make sure the caller has carrier privilege.
     *
     * @throws SecurityException if the caller does not have the required permission
     */
    private void enforceCarrierPrivilege(int subId) {
        if (getCarrierPrivilegeStatus(subId) !=
                    TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS) {
            loge("No Carrier Privilege.");
            throw new SecurityException("No Carrier Privilege.");
        }
    }

    /**
     * Make sure the caller has the CALL_PHONE permission.
     *
     * @throws SecurityException if the caller does not have the required permission
     */
    private void enforceCallPermission() {
        mApp.enforceCallingOrSelfPermission(android.Manifest.permission.CALL_PHONE, null);
    }

    private void enforceConnectivityInternalPermission() {
        mApp.enforceCallingOrSelfPermission(
                android.Manifest.permission.CONNECTIVITY_INTERNAL,
                "ConnectivityService");
    }

    private String createTelUrl(String number) {
        if (TextUtils.isEmpty(number)) {
            return null;
        }

        return "tel:" + number;
    }

    private static void log(String msg) {
        Log.d(LOG_TAG, "[PhoneIntfMgr] " + msg);
    }

    private static void logv(String msg) {
        Log.v(LOG_TAG, "[PhoneIntfMgr] " + msg);
    }

    private static void loge(String msg) {
        Log.e(LOG_TAG, "[PhoneIntfMgr] " + msg);
    }

    @Override
    public int getActivePhoneType() {
        return getActivePhoneTypeForSlot(getSlotForDefaultSubscription());
    }

    @Override
    public int getActivePhoneTypeForSlot(int slotIndex) {
        final Phone phone = PhoneFactory.getPhone(slotIndex);
        if (phone == null) {
            return PhoneConstants.PHONE_TYPE_NONE;
        } else {
            return phone.getPhoneType();
        }
    }

    /**
     * Returns the CDMA ERI icon index to display
     */
    @Override
    public int getCdmaEriIconIndex(String callingPackage) {
        return getCdmaEriIconIndexForSubscriber(getDefaultSubscription(), callingPackage);
    }

    @Override
    public int getCdmaEriIconIndexForSubscriber(int subId, String callingPackage) {
        if (!canReadPhoneState(callingPackage, "getCdmaEriIconIndexForSubscriber")) {
            return -1;
        }
        final Phone phone = getPhone(subId);
        if (phone != null) {
            return phone.getCdmaEriIconIndex();
        } else {
            return -1;
        }
    }

    /**
     * Returns the CDMA ERI icon mode,
     * 0 - ON
     * 1 - FLASHING
     */
    @Override
    public int getCdmaEriIconMode(String callingPackage) {
        return getCdmaEriIconModeForSubscriber(getDefaultSubscription(), callingPackage);
    }

    @Override
    public int getCdmaEriIconModeForSubscriber(int subId, String callingPackage) {
        if (!canReadPhoneState(callingPackage, "getCdmaEriIconModeForSubscriber")) {
            return -1;
        }
        final Phone phone = getPhone(subId);
        if (phone != null) {
            return phone.getCdmaEriIconMode();
        } else {
            return -1;
        }
    }

    /**
     * Returns the CDMA ERI text,
     */
    @Override
    public String getCdmaEriText(String callingPackage) {
        return getCdmaEriTextForSubscriber(getDefaultSubscription(), callingPackage);
    }

    @Override
    public String getCdmaEriTextForSubscriber(int subId, String callingPackage) {
        if (!canReadPhoneState(callingPackage, "getCdmaEriIconTextForSubscriber")) {
            return null;
        }
        final Phone phone = getPhone(subId);
        if (phone != null) {
            return phone.getCdmaEriText();
        } else {
            return null;
        }
    }

    /**
     * Returns the CDMA MDN.
     */
    @Override
    public String getCdmaMdn(int subId) {
        enforceModifyPermissionOrCarrierPrivilege(subId);
        final Phone phone = getPhone(subId);
        if (mPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA && phone != null) {
            return phone.getLine1Number();
        } else {
            return null;
        }
    }

    /**
     * Returns the CDMA MIN.
     */
    @Override
    public String getCdmaMin(int subId) {
        enforceModifyPermissionOrCarrierPrivilege(subId);
        final Phone phone = getPhone(subId);
        if (phone != null && phone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
            return phone.getCdmaMin();
        } else {
            return null;
        }
    }

    /**
     * Returns true if CDMA provisioning needs to run.
     */
    public boolean needsOtaServiceProvisioning() {
        return mPhone.needsOtaServiceProvisioning();
    }

    /**
     * Sets the voice mail number of a given subId.
     */
    @Override
    public boolean setVoiceMailNumber(int subId, String alphaTag, String number) {
        enforceCarrierPrivilege(subId);
        Boolean success = (Boolean) sendRequest(CMD_SET_VOICEMAIL_NUMBER,
                new Pair<String, String>(alphaTag, number), new Integer(subId));
        return success;
    }

    @Override
    public Bundle getVisualVoicemailSettings(String callingPackage, int subId) {
        mAppOps.checkPackage(Binder.getCallingUid(), callingPackage);
        String systemDialer = TelecomManager.from(mPhone.getContext()).getSystemDialerPackage();
        if (!TextUtils.equals(callingPackage, systemDialer)) {
            throw new SecurityException("caller must be system dialer");
        }
        PhoneAccountHandle phoneAccountHandle = PhoneAccountHandleConverter.fromSubId(subId);
        if (phoneAccountHandle == null){
            return null;
        }
        return VisualVoicemailSettingsUtil.dump(mPhone.getContext(), phoneAccountHandle);
    }

    @Override
    public String getVisualVoicemailPackageName(String callingPackage, int subId) {
        mAppOps.checkPackage(Binder.getCallingUid(), callingPackage);
        if (!canReadPhoneState(callingPackage, "getVisualVoicemailPackageName")) {
            return null;
        }
        return RemoteVvmTaskManager.getRemotePackage(mPhone.getContext(), subId).getPackageName();
    }

    @Override
    public void enableVisualVoicemailSmsFilter(String callingPackage, int subId,
            VisualVoicemailSmsFilterSettings settings) {
        mAppOps.checkPackage(Binder.getCallingUid(), callingPackage);
        VisualVoicemailSmsFilterConfig
                .enableVisualVoicemailSmsFilter(mPhone.getContext(), callingPackage, subId,
                        settings);
    }

    @Override
    public void disableVisualVoicemailSmsFilter(String callingPackage, int subId) {
        mAppOps.checkPackage(Binder.getCallingUid(), callingPackage);
        VisualVoicemailSmsFilterConfig
                .disableVisualVoicemailSmsFilter(mPhone.getContext(), callingPackage, subId);
    }

    @Override
    public VisualVoicemailSmsFilterSettings getVisualVoicemailSmsFilterSettings(
            String callingPackage, int subId) {
        mAppOps.checkPackage(Binder.getCallingUid(), callingPackage);
        return VisualVoicemailSmsFilterConfig
                .getVisualVoicemailSmsFilterSettings(mPhone.getContext(), callingPackage, subId);
    }

    @Override
    public VisualVoicemailSmsFilterSettings getActiveVisualVoicemailSmsFilterSettings(int subId) {
        enforceReadPrivilegedPermission();
        return VisualVoicemailSmsFilterConfig
                .getActiveVisualVoicemailSmsFilterSettings(mPhone.getContext(), subId);
    }

    @Override
    public void sendVisualVoicemailSmsForSubscriber(String callingPackage, int subId,
            String number, int port, String text, PendingIntent sentIntent) {
        mAppOps.checkPackage(Binder.getCallingUid(), callingPackage);
        enforceVisualVoicemailPackage(callingPackage, subId);
        enforceSendSmsPermission();
        // Make the calls as the phone process.
        final long identity = Binder.clearCallingIdentity();
        try {
            SmsManager smsManager = SmsManager.getSmsManagerForSubscriptionId(subId);
            if (port == 0) {
                smsManager.sendTextMessageWithSelfPermissions(number, null, text,
                        sentIntent, null, false);
            } else {
                byte[] data = text.getBytes(StandardCharsets.UTF_8);
                smsManager.sendDataMessageWithSelfPermissions(number, null,
                        (short) port, data, sentIntent, null);
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }
    /**
     * Sets the voice activation state of a given subId.
     */
    @Override
    public void setVoiceActivationState(int subId, int activationState) {
        enforceModifyPermissionOrCarrierPrivilege(subId);
        final Phone phone = getPhone(subId);
        if (phone != null) {
            phone.setVoiceActivationState(activationState);
        } else {
            loge("setVoiceActivationState fails with invalid subId: " + subId);
        }
    }

    /**
     * Sets the data activation state of a given subId.
     */
    @Override
    public void setDataActivationState(int subId, int activationState) {
        enforceModifyPermissionOrCarrierPrivilege(subId);
        final Phone phone = getPhone(subId);
        if (phone != null) {
            phone.setDataActivationState(activationState);
        } else {
            loge("setVoiceActivationState fails with invalid subId: " + subId);
        }
    }

    /**
     * Returns the voice activation state of a given subId.
     */
    @Override
    public int getVoiceActivationState(int subId, String callingPackage) {
        if (!canReadPhoneState(callingPackage, "getVoiceActivationStateForSubscriber")) {
            return TelephonyManager.SIM_ACTIVATION_STATE_UNKNOWN;
        }
        final Phone phone = getPhone(subId);
        if (phone != null) {
            return phone.getVoiceActivationState();
        } else {
            return TelephonyManager.SIM_ACTIVATION_STATE_UNKNOWN;
        }
    }

    /**
     * Returns the data activation state of a given subId.
     */
    @Override
    public int getDataActivationState(int subId, String callingPackage) {
        if (!canReadPhoneState(callingPackage, "getDataActivationStateForSubscriber")) {
            return TelephonyManager.SIM_ACTIVATION_STATE_UNKNOWN;
        }
        final Phone phone = getPhone(subId);
        if (phone != null) {
            return phone.getDataActivationState();
        } else {
            return TelephonyManager.SIM_ACTIVATION_STATE_UNKNOWN;
        }
    }

    /**
     * Returns the unread count of voicemails
     */
    public int getVoiceMessageCount() {
        return getVoiceMessageCountForSubscriber(getDefaultSubscription());
    }

    /**
     * Returns the unread count of voicemails for a subId
     */
    @Override
    public int getVoiceMessageCountForSubscriber( int subId) {
        final Phone phone = getPhone(subId);
        if (phone != null) {
            return phone.getVoiceMessageCount();
        } else {
            return 0;
        }
    }

    /**
      * returns true, if the device is in a state where both voice and data
      * are supported simultaneously. This can change based on location or network condition.
     */
    @Override
    public boolean isConcurrentVoiceAndDataAllowed(int subId) {
        final Phone phone = getPhone(subId);
        return (phone == null ? false : phone.isConcurrentVoiceAndDataAllowed());
    }

    /**
     * Send the dialer code if called from the current default dialer or the caller has
     * carrier privilege.
     * @param inputCode The dialer code to send
     */
    @Override
    public void sendDialerSpecialCode(String callingPackage, String inputCode) {
        mAppOps.checkPackage(Binder.getCallingUid(), callingPackage);
        String defaultDialer = TelecomManager.from(mPhone.getContext()).getDefaultDialerPackage();
        if (!TextUtils.equals(callingPackage, defaultDialer)) {
            enforceCarrierPrivilege(getDefaultSubscription());
        }
        mPhone.sendDialerSpecialCode(inputCode);
    }

    /**
     * Returns the data network type.
     * Legacy call, permission-free.
     *
     * @Deprecated to be removed Q3 2013 use {@link #getDataNetworkType}.
     */
    @Override
    public int getNetworkType() {
        final Phone phone = getPhone(getDefaultSubscription());
        if (phone != null) {
            return phone.getServiceState().getDataNetworkType();
        } else {
            return TelephonyManager.NETWORK_TYPE_UNKNOWN;
        }
    }

    /**
     * Returns the network type for a subId
     */
    @Override
    public int getNetworkTypeForSubscriber(int subId, String callingPackage) {
        if (!canReadPhoneState(callingPackage, "getNetworkTypeForSubscriber")) {
            return TelephonyManager.NETWORK_TYPE_UNKNOWN;
        }

        final Phone phone = getPhone(subId);
        if (phone != null) {
            return phone.getServiceState().getDataNetworkType();
        } else {
            return TelephonyManager.NETWORK_TYPE_UNKNOWN;
        }
    }

    /**
     * Returns the data network type
     */
    @Override
    public int getDataNetworkType(String callingPackage) {
        return getDataNetworkTypeForSubscriber(getDefaultSubscription(), callingPackage);
    }

    /**
     * Returns the data network type for a subId
     */
    @Override
    public int getDataNetworkTypeForSubscriber(int subId, String callingPackage) {
        if (!canReadPhoneState(callingPackage, "getDataNetworkTypeForSubscriber")) {
            return TelephonyManager.NETWORK_TYPE_UNKNOWN;
        }

        final Phone phone = getPhone(subId);
        if (phone != null) {
            return phone.getServiceState().getDataNetworkType();
        } else {
            return TelephonyManager.NETWORK_TYPE_UNKNOWN;
        }
    }

    /**
     * Returns the Voice network type for a subId
     */
    @Override
    public int getVoiceNetworkTypeForSubscriber(int subId, String callingPackage) {
        if (!canReadPhoneState(callingPackage, "getDataNetworkTypeForSubscriber")) {
            return TelephonyManager.NETWORK_TYPE_UNKNOWN;
        }

        final Phone phone = getPhone(subId);
        if (phone != null) {
            return phone.getServiceState().getVoiceNetworkType();
        } else {
            return TelephonyManager.NETWORK_TYPE_UNKNOWN;
        }
    }

    /**
     * @return true if a ICC card is present
     */
    public boolean hasIccCard() {
        // FIXME Make changes to pass defaultSimId of type int
        return hasIccCardUsingSlotIndex(mSubscriptionController.getSlotIndex(
                getDefaultSubscription()));
    }

    /**
     * @return true if a ICC card is present for a slotIndex
     */
    @Override
    public boolean hasIccCardUsingSlotIndex(int slotIndex) {
        final Phone phone = PhoneFactory.getPhone(slotIndex);
        if (phone != null) {
            return phone.getIccCard().hasIccCard();
        } else {
            return false;
        }
    }

    /**
     * Return if the current radio is LTE on CDMA. This
     * is a tri-state return value as for a period of time
     * the mode may be unknown.
     *
     * @param callingPackage the name of the package making the call.
     * @return {@link Phone#LTE_ON_CDMA_UNKNOWN}, {@link Phone#LTE_ON_CDMA_FALSE}
     * or {@link Phone#LTE_ON_CDMA_TRUE}
     */
    @Override
    public int getLteOnCdmaMode(String callingPackage) {
        return getLteOnCdmaModeForSubscriber(getDefaultSubscription(), callingPackage);
    }

    @Override
    public int getLteOnCdmaModeForSubscriber(int subId, String callingPackage) {
        if (!canReadPhoneState(callingPackage, "getLteOnCdmaModeForSubscriber")) {
            return PhoneConstants.LTE_ON_CDMA_UNKNOWN;
        }

        final Phone phone = getPhone(subId);
        if (phone == null) {
            return PhoneConstants.LTE_ON_CDMA_UNKNOWN;
        } else {
            return phone.getLteOnCdmaMode();
        }
    }

    public void setPhone(Phone phone) {
        mPhone = phone;
    }

    /**
     * {@hide}
     * Returns Default subId, 0 in the case of single standby.
     */
    private int getDefaultSubscription() {
        return mSubscriptionController.getDefaultSubId();
    }

    private int getSlotForDefaultSubscription() {
        return mSubscriptionController.getPhoneId(getDefaultSubscription());
    }

    private int getPreferredVoiceSubscription() {
        return mSubscriptionController.getDefaultVoiceSubId();
    }

    /**
     * @see android.telephony.TelephonyManager.WifiCallingChoices
     */
    public int getWhenToMakeWifiCalls() {
        return Settings.System.getInt(mPhone.getContext().getContentResolver(),
                Settings.System.WHEN_TO_MAKE_WIFI_CALLS, getWhenToMakeWifiCallsDefaultPreference());
    }

    /**
     * @see android.telephony.TelephonyManager.WifiCallingChoices
     */
    public void setWhenToMakeWifiCalls(int preference) {
        if (DBG) log("setWhenToMakeWifiCallsStr, storing setting = " + preference);
        Settings.System.putInt(mPhone.getContext().getContentResolver(),
                Settings.System.WHEN_TO_MAKE_WIFI_CALLS, preference);
    }

    private static int getWhenToMakeWifiCallsDefaultPreference() {
        // TODO: Use a build property to choose this value.
        return TelephonyManager.WifiCallingChoices.ALWAYS_USE;
    }

    @Override
    public IccOpenLogicalChannelResponse iccOpenLogicalChannel(
            int subId, String callingPackage, String aid, int p2) {
        enforceModifyPermissionOrCarrierPrivilege(subId);

        if (TextUtils.equals(ISDR_AID, aid)) {
            // Only allows LPA to open logical channel to ISD-R.
            mAppOps.checkPackage(Binder.getCallingUid(), callingPackage);
            ComponentInfo bestComponent =
                    EuiccConnector.findBestComponent(mPhone.getContext().getPackageManager());
            if (bestComponent == null
                    || !TextUtils.equals(callingPackage, bestComponent.packageName)) {
                loge("The calling package is not allowed to access ISD-R.");
                throw new SecurityException("The calling package is not allowed to access ISD-R.");
            }
        }

        if (DBG) log("iccOpenLogicalChannel: subId=" + subId + " aid=" + aid + " p2=" + p2);
        IccOpenLogicalChannelResponse response = (IccOpenLogicalChannelResponse)sendRequest(
                CMD_OPEN_CHANNEL, new Pair<String, Integer>(aid, p2), subId);
        if (DBG) log("iccOpenLogicalChannel: " + response);
        return response;
    }

    @Override
    public boolean iccCloseLogicalChannel(int subId, int channel) {
        enforceModifyPermissionOrCarrierPrivilege(subId);

        if (DBG) log("iccCloseLogicalChannel: subId=" + subId + " chnl=" + channel);
        if (channel < 0) {
          return false;
        }
        Boolean success = (Boolean)sendRequest(CMD_CLOSE_CHANNEL, channel, subId);
        if (DBG) log("iccCloseLogicalChannel: " + success);
        return success;
    }

    @Override
    public String iccTransmitApduLogicalChannel(int subId, int channel, int cla,
            int command, int p1, int p2, int p3, String data) {
        enforceModifyPermissionOrCarrierPrivilege(subId);

        if (DBG) {
            log("iccTransmitApduLogicalChannel: subId=" + subId + " chnl=" + channel +
                    " cla=" + cla + " cmd=" + command + " p1=" + p1 + " p2=" + p2 + " p3=" + p3 +
                    " data=" + data);
        }

        if (channel < 0) {
            return "";
        }

        IccIoResult response = (IccIoResult)sendRequest(CMD_TRANSMIT_APDU_LOGICAL_CHANNEL,
                new IccAPDUArgument(channel, cla, command, p1, p2, p3, data), subId);
        if (DBG) log("iccTransmitApduLogicalChannel: " + response);

        // Append the returned status code to the end of the response payload.
        String s = Integer.toHexString(
                (response.sw1 << 8) + response.sw2 + 0x10000).substring(1);
        if (response.payload != null) {
            s = IccUtils.bytesToHexString(response.payload) + s;
        }
        return s;
    }

    @Override
    public String iccTransmitApduBasicChannel(int subId, int cla, int command, int p1, int p2,
                int p3, String data) {
        enforceModifyPermissionOrCarrierPrivilege(subId);

        if (DBG) {
            log("iccTransmitApduBasicChannel: subId=" + subId + " cla=" + cla + " cmd=" + command
                    + " p1=" + p1 + " p2=" + p2 + " p3=" + p3 + " data=" + data);
        }

        IccIoResult response = (IccIoResult)sendRequest(CMD_TRANSMIT_APDU_BASIC_CHANNEL,
                new IccAPDUArgument(0, cla, command, p1, p2, p3, data), subId);
        if (DBG) log("iccTransmitApduBasicChannel: " + response);

        // Append the returned status code to the end of the response payload.
        String s = Integer.toHexString(
                (response.sw1 << 8) + response.sw2 + 0x10000).substring(1);
        if (response.payload != null) {
            s = IccUtils.bytesToHexString(response.payload) + s;
        }
        return s;
    }

    @Override
    public byte[] iccExchangeSimIO(int subId, int fileID, int command, int p1, int p2, int p3,
            String filePath) {
        enforceModifyPermissionOrCarrierPrivilege(subId);

        if (DBG) {
            log("Exchange SIM_IO " + subId + ":" + fileID + ":" + command + " " +
                p1 + " " + p2 + " " + p3 + ":" + filePath);
        }

        IccIoResult response =
            (IccIoResult)sendRequest(CMD_EXCHANGE_SIM_IO,
                    new IccAPDUArgument(-1, fileID, command, p1, p2, p3, filePath),
                    subId);

        if (DBG) {
          log("Exchange SIM_IO [R]" + response);
        }

        byte[] result = null;
        int length = 2;
        if (response.payload != null) {
            length = 2 + response.payload.length;
            result = new byte[length];
            System.arraycopy(response.payload, 0, result, 0, response.payload.length);
        } else {
            result = new byte[length];
        }

        result[length - 1] = (byte) response.sw2;
        result[length - 2] = (byte) response.sw1;
        return result;
    }

    /**
     * Get the forbidden PLMN List from the given app type (ex APPTYPE_USIM)
     * on a particular subscription
     */
    public String[] getForbiddenPlmns(int subId, int appType) {
        mApp.enforceCallingOrSelfPermission(android.Manifest.permission.READ_PHONE_STATE,
                "Requires READ_PHONE_STATE");
        if (appType != TelephonyManager.APPTYPE_USIM && appType != TelephonyManager.APPTYPE_SIM) {
            loge("getForbiddenPlmnList(): App Type must be USIM or SIM");
            return null;
        }
        Object response = sendRequest(
            CMD_GET_FORBIDDEN_PLMNS, new Integer(appType), subId);
        if (response instanceof String[]) {
            return (String[]) response;
        }
        // Response is an Exception of some kind, which is signalled to the user as a NULL retval
        return null;
    }

    @Override
    public String sendEnvelopeWithStatus(int subId, String content) {
        enforceModifyPermissionOrCarrierPrivilege(subId);

        IccIoResult response = (IccIoResult)sendRequest(CMD_SEND_ENVELOPE, content, subId);
        if (response.payload == null) {
          return "";
        }

        // Append the returned status code to the end of the response payload.
        String s = Integer.toHexString(
                (response.sw1 << 8) + response.sw2 + 0x10000).substring(1);
        s = IccUtils.bytesToHexString(response.payload) + s;
        return s;
    }

    /**
     * Read one of the NV items defined in {@link com.android.internal.telephony.RadioNVItems}
     * and {@code ril_nv_items.h}. Used for device configuration by some CDMA operators.
     *
     * @param itemID the ID of the item to read
     * @return the NV item as a String, or null on error.
     */
    @Override
    public String nvReadItem(int itemID) {
        enforceModifyPermissionOrCarrierPrivilege(getDefaultSubscription());
        if (DBG) log("nvReadItem: item " + itemID);
        String value = (String) sendRequest(CMD_NV_READ_ITEM, itemID);
        if (DBG) log("nvReadItem: item " + itemID + " is \"" + value + '"');
        return value;
    }

    /**
     * Write one of the NV items defined in {@link com.android.internal.telephony.RadioNVItems}
     * and {@code ril_nv_items.h}. Used for device configuration by some CDMA operators.
     *
     * @param itemID the ID of the item to read
     * @param itemValue the value to write, as a String
     * @return true on success; false on any failure
     */
    @Override
    public boolean nvWriteItem(int itemID, String itemValue) {
        enforceModifyPermissionOrCarrierPrivilege(getDefaultSubscription());
        if (DBG) log("nvWriteItem: item " + itemID + " value \"" + itemValue + '"');
        Boolean success = (Boolean) sendRequest(CMD_NV_WRITE_ITEM,
                new Pair<Integer, String>(itemID, itemValue));
        if (DBG) log("nvWriteItem: item " + itemID + ' ' + (success ? "ok" : "fail"));
        return success;
    }

    /**
     * Update the CDMA Preferred Roaming List (PRL) in the radio NV storage.
     * Used for device configuration by some CDMA operators.
     *
     * @param preferredRoamingList byte array containing the new PRL
     * @return true on success; false on any failure
     */
    @Override
    public boolean nvWriteCdmaPrl(byte[] preferredRoamingList) {
        enforceModifyPermissionOrCarrierPrivilege(getDefaultSubscription());
        if (DBG) log("nvWriteCdmaPrl: value: " + HexDump.toHexString(preferredRoamingList));
        Boolean success = (Boolean) sendRequest(CMD_NV_WRITE_CDMA_PRL, preferredRoamingList);
        if (DBG) log("nvWriteCdmaPrl: " + (success ? "ok" : "fail"));
        return success;
    }

    /**
     * Perform the specified type of NV config reset.
     * Used for device configuration by some CDMA operators.
     *
     * @param resetType the type of reset to perform (1 == factory reset; 2 == NV-only reset)
     * @return true on success; false on any failure
     */
    @Override
    public boolean nvResetConfig(int resetType) {
        enforceModifyPermissionOrCarrierPrivilege(getDefaultSubscription());
        if (DBG) log("nvResetConfig: type " + resetType);
        Boolean success = (Boolean) sendRequest(CMD_NV_RESET_CONFIG, resetType);
        if (DBG) log("nvResetConfig: type " + resetType + ' ' + (success ? "ok" : "fail"));
        return success;
    }

    /**
     * {@hide}
     * Returns Default sim, 0 in the case of single standby.
     */
    public int getDefaultSim() {
        //TODO Need to get it from Telephony Devcontroller
        return 0;
    }

    public String[] getPcscfAddress(String apnType, String callingPackage) {
        if (!canReadPhoneState(callingPackage, "getPcscfAddress")) {
            return new String[0];
        }


        return mPhone.getPcscfAddress(apnType);
    }

    /**
     * Returns the {@link IImsServiceController} that corresponds to the given slot Id and IMS
     * feature or {@link null} if the service is not available. If an ImsServiceController is
     * available, the {@link IImsServiceFeatureListener} callback is registered as a listener for
     * feature updates.
     */
    public IImsServiceController getImsServiceControllerAndListen(int slotIndex, int feature,
            IImsServiceFeatureListener callback) {
        enforceModifyPermission();
        return PhoneFactory.getImsResolver().getImsServiceControllerAndListen(slotIndex, feature,
                callback);
    }

    public void setImsRegistrationState(boolean registered) {
        enforceModifyPermission();
        mPhone.setImsRegistrationState(registered);
    }

    /**
     * Set the network selection mode to automatic.
     *
     */
    @Override
    public void setNetworkSelectionModeAutomatic(int subId) {
        enforceModifyPermissionOrCarrierPrivilege(subId);
        if (DBG) log("setNetworkSelectionModeAutomatic: subId " + subId);
        sendRequest(CMD_SET_NETWORK_SELECTION_MODE_AUTOMATIC, null, subId);
    }

    /**
     * Set the network selection mode to manual with the selected carrier.
     */
    @Override
    public boolean setNetworkSelectionModeManual(int subId, OperatorInfo operator,
            boolean persistSelection) {
        enforceModifyPermissionOrCarrierPrivilege(subId);
        if (DBG) log("setNetworkSelectionModeManual: subId:" + subId + " operator:" + operator);
        ManualNetworkSelectionArgument arg = new ManualNetworkSelectionArgument(operator,
                persistSelection);
        return (Boolean) sendRequest(CMD_SET_NETWORK_SELECTION_MODE_MANUAL, arg, subId);
    }

    /**
     * Scans for available networks.
     */
    @Override
    public CellNetworkScanResult getCellNetworkScanResults(int subId) {
        enforceModifyPermissionOrCarrierPrivilege(subId);
        if (DBG) log("getCellNetworkScanResults: subId " + subId);
        CellNetworkScanResult result = (CellNetworkScanResult) sendRequest(
                CMD_PERFORM_NETWORK_SCAN, null, subId);
        return result;
    }

    /**
     * Starts a new network scan and returns the id of this scan.
     *
     * @param subId id of the subscription
     * @param request contains the radio access networks with bands/channels to scan
     * @param messenger callback messenger for scan results or errors
     * @param binder for the purpose of auto clean when the user thread crashes
     * @return the id of the requested scan which can be used to stop the scan.
     */
    @Override
    public int requestNetworkScan(int subId, NetworkScanRequest request, Messenger messenger,
            IBinder binder) {
        enforceModifyPermissionOrCarrierPrivilege(subId);
        return mNetworkScanRequestTracker.startNetworkScan(
                request, messenger, binder, getPhone(subId));
    }

    /**
     * Stops an existing network scan with the given scanId.
     *
     * @param subId id of the subscription
     * @param scanId id of the scan that needs to be stopped
     */
    @Override
    public void stopNetworkScan(int subId, int scanId) {
        enforceModifyPermissionOrCarrierPrivilege(subId);
        mNetworkScanRequestTracker.stopNetworkScan(scanId);
    }

    /**
     * Get the calculated preferred network type.
     * Used for debugging incorrect network type.
     *
     * @return the preferred network type, defined in RILConstants.java.
     */
    @Override
    public int getCalculatedPreferredNetworkType(String callingPackage) {
        if (!canReadPhoneState(callingPackage, "getCalculatedPreferredNetworkType")) {
            return RILConstants.PREFERRED_NETWORK_MODE;
        }

        return PhoneFactory.calculatePreferredNetworkType(mPhone.getContext(), 0); // wink FIXME: need to get SubId from somewhere.
    }

    /**
     * Get the preferred network type.
     * Used for device configuration by some CDMA operators.
     *
     * @return the preferred network type, defined in RILConstants.java.
     */
    @Override
    public int getPreferredNetworkType(int subId) {
        enforceModifyPermissionOrCarrierPrivilege(subId);
        if (DBG) log("getPreferredNetworkType");
        int[] result = (int[]) sendRequest(CMD_GET_PREFERRED_NETWORK_TYPE, null, subId);
        int networkType = (result != null ? result[0] : -1);
        if (DBG) log("getPreferredNetworkType: " + networkType);
        return networkType;
    }

    /**
     * Set the preferred network type.
     * Used for device configuration by some CDMA operators.
     *
     * @param networkType the preferred network type, defined in RILConstants.java.
     * @return true on success; false on any failure.
     */
    @Override
    public boolean setPreferredNetworkType(int subId, int networkType) {
        enforceModifyPermissionOrCarrierPrivilege(subId);
        if (DBG) log("setPreferredNetworkType: subId " + subId + " type " + networkType);
        Boolean success = (Boolean) sendRequest(CMD_SET_PREFERRED_NETWORK_TYPE, networkType, subId);
        if (DBG) log("setPreferredNetworkType: " + (success ? "ok" : "fail"));
        if (success) {
            Settings.Global.putInt(mPhone.getContext().getContentResolver(),
                    Settings.Global.PREFERRED_NETWORK_MODE + subId, networkType);
        }
        return success;
    }

    /**
     * Check TETHER_DUN_REQUIRED and TETHER_DUN_APN settings, net.tethering.noprovisioning
     * SystemProperty, and config_tether_apndata to decide whether DUN APN is required for
     * tethering.
     *
     * @return 0: Not required. 1: required. 2: Not set.
     * @hide
     */
    @Override
    public int getTetherApnRequired() {
        enforceModifyPermission();
        int dunRequired = Settings.Global.getInt(mPhone.getContext().getContentResolver(),
                Settings.Global.TETHER_DUN_REQUIRED, 2);
        // If not set, check net.tethering.noprovisioning, TETHER_DUN_APN setting and
        // config_tether_apndata.
        if (dunRequired == 2 && mPhone.hasMatchedTetherApnSetting()) {
            dunRequired = 1;
        }
        return dunRequired;
    }

    /**
     * Set mobile data enabled
     * Used by the user through settings etc to turn on/off mobile data
     *
     * @param enable {@code true} turn turn data on, else {@code false}
     */
    @Override
    public void setDataEnabled(int subId, boolean enable) {
        enforceModifyPermissionOrCarrierPrivilege(subId);
        int phoneId = mSubscriptionController.getPhoneId(subId);
        if (DBG) log("getDataEnabled: subId=" + subId + " phoneId=" + phoneId);
        Phone phone = PhoneFactory.getPhone(phoneId);
        if (phone != null) {
            if (DBG) log("setDataEnabled: subId=" + subId + " enable=" + enable);
            phone.setDataEnabled(enable);
        } else {
            loge("setDataEnabled: no phone for subId=" + subId);
        }
    }

    /**
     * Get whether mobile data is enabled.
     *
     * Accepts either ACCESS_NETWORK_STATE, MODIFY_PHONE_STATE or carrier privileges.
     *
     * @return {@code true} if data is enabled else {@code false}
     */
    @Override
    public boolean getDataEnabled(int subId) {
        try {
            mApp.enforceCallingOrSelfPermission(android.Manifest.permission.ACCESS_NETWORK_STATE,
                    null);
        } catch (Exception e) {
            enforceModifyPermissionOrCarrierPrivilege(subId);
        }
        int phoneId = mSubscriptionController.getPhoneId(subId);
        if (DBG) log("getDataEnabled: subId=" + subId + " phoneId=" + phoneId);
        Phone phone = PhoneFactory.getPhone(phoneId);
        if (phone != null) {
            boolean retVal = phone.getDataEnabled();
            if (DBG) log("getDataEnabled: subId=" + subId + " retVal=" + retVal);
            return retVal;
        } else {
            if (DBG) loge("getDataEnabled: no phone subId=" + subId + " retVal=false");
            return false;
        }
    }

    @Override
    public int getCarrierPrivilegeStatus(int subId) {
        final Phone phone = getPhone(subId);
        if (phone == null) {
            loge("getCarrierPrivilegeStatus: Invalid subId");
            return TelephonyManager.CARRIER_PRIVILEGE_STATUS_NO_ACCESS;
        }
        UiccCard card = UiccController.getInstance().getUiccCard(phone.getPhoneId());
        if (card == null) {
            loge("getCarrierPrivilegeStatus: No UICC");
            return TelephonyManager.CARRIER_PRIVILEGE_STATUS_RULES_NOT_LOADED;
        }
        return card.getCarrierPrivilegeStatusForCurrentTransaction(
                phone.getContext().getPackageManager());
    }

    @Override
    public int checkCarrierPrivilegesForPackage(String pkgName) {
        if (TextUtils.isEmpty(pkgName))
            return TelephonyManager.CARRIER_PRIVILEGE_STATUS_NO_ACCESS;
        UiccCard card = UiccController.getInstance().getUiccCard(mPhone.getPhoneId());
        if (card == null) {
            loge("checkCarrierPrivilegesForPackage: No UICC");
            return TelephonyManager.CARRIER_PRIVILEGE_STATUS_RULES_NOT_LOADED;
        }
        return card.getCarrierPrivilegeStatus(mPhone.getContext().getPackageManager(), pkgName);
    }

    @Override
    public int checkCarrierPrivilegesForPackageAnyPhone(String pkgName) {
        if (TextUtils.isEmpty(pkgName))
            return TelephonyManager.CARRIER_PRIVILEGE_STATUS_NO_ACCESS;
        int result = TelephonyManager.CARRIER_PRIVILEGE_STATUS_RULES_NOT_LOADED;
        for (int i = 0; i < TelephonyManager.getDefault().getPhoneCount(); i++) {
            UiccCard card = UiccController.getInstance().getUiccCard(i);
            if (card == null) {
              // No UICC in that slot.
              continue;
            }

            result = card.getCarrierPrivilegeStatus(
                mPhone.getContext().getPackageManager(), pkgName);
            if (result == TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS) {
                break;
            }
        }

        return result;
    }

    @Override
    public List<String> getCarrierPackageNamesForIntentAndPhone(Intent intent, int phoneId) {
        if (!SubscriptionManager.isValidPhoneId(phoneId)) {
            loge("phoneId " + phoneId + " is not valid.");
            return null;
        }
        UiccCard card = UiccController.getInstance().getUiccCard(phoneId);
        if (card == null) {
            loge("getCarrierPackageNamesForIntent: No UICC");
            return null ;
        }
        return card.getCarrierPackageNamesForIntent(
                mPhone.getContext().getPackageManager(), intent);
    }

    @Override
    public List<String> getPackagesWithCarrierPrivileges() {
        PackageManager pm = mPhone.getContext().getPackageManager();
        List<String> privilegedPackages = new ArrayList<>();
        List<PackageInfo> packages = null;
        for (int i = 0; i < TelephonyManager.getDefault().getPhoneCount(); i++) {
            UiccCard card = UiccController.getInstance().getUiccCard(i);
            if (card == null) {
                // No UICC in that slot.
                continue;
            }
            if (card.hasCarrierPrivilegeRules()) {
                if (packages == null) {
                    // Only check packages in user 0 for now
                    packages = pm.getInstalledPackagesAsUser(
                            PackageManager.MATCH_DISABLED_COMPONENTS
                            | PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                            | PackageManager.GET_SIGNATURES, UserHandle.USER_SYSTEM);
                }
                for (int p = packages.size() - 1; p >= 0; p--) {
                    PackageInfo pkgInfo = packages.get(p);
                    if (pkgInfo != null && pkgInfo.packageName != null
                            && card.getCarrierPrivilegeStatus(pkgInfo)
                                == TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS) {
                        privilegedPackages.add(pkgInfo.packageName);
                    }
                }
            }
        }
        return privilegedPackages;
    }

    private String getIccId(int subId) {
        final Phone phone = getPhone(subId);
        UiccCard card = phone == null ? null : phone.getUiccCard();
        if (card == null) {
            loge("getIccId: No UICC");
            return null;
        }
        String iccId = card.getIccId();
        if (TextUtils.isEmpty(iccId)) {
            loge("getIccId: ICC ID is null or empty.");
            return null;
        }
        return iccId;
    }

    @Override
    public boolean setLine1NumberForDisplayForSubscriber(int subId, String alphaTag,
            String number) {
        enforceCarrierPrivilege(subId);

        final String iccId = getIccId(subId);
        final Phone phone = getPhone(subId);
        if (phone == null) {
            return false;
        }
        final String subscriberId = phone.getSubscriberId();

        if (DBG_MERGE) {
            Slog.d(LOG_TAG, "Setting line number for ICC=" + iccId + ", subscriberId="
                    + subscriberId + " to " + number);
        }

        if (TextUtils.isEmpty(iccId)) {
            return false;
        }

        final SharedPreferences.Editor editor = mTelephonySharedPreferences.edit();

        final String alphaTagPrefKey = PREF_CARRIERS_ALPHATAG_PREFIX + iccId;
        if (alphaTag == null) {
            editor.remove(alphaTagPrefKey);
        } else {
            editor.putString(alphaTagPrefKey, alphaTag);
        }

        // Record both the line number and IMSI for this ICCID, since we need to
        // track all merged IMSIs based on line number
        final String numberPrefKey = PREF_CARRIERS_NUMBER_PREFIX + iccId;
        final String subscriberPrefKey = PREF_CARRIERS_SUBSCRIBER_PREFIX + iccId;
        if (number == null) {
            editor.remove(numberPrefKey);
            editor.remove(subscriberPrefKey);
        } else {
            editor.putString(numberPrefKey, number);
            editor.putString(subscriberPrefKey, subscriberId);
        }

        editor.commit();
        return true;
    }

    @Override
    public String getLine1NumberForDisplay(int subId, String callingPackage) {
        // This is open to apps with WRITE_SMS.
        if (!canReadPhoneNumber(callingPackage, "getLine1NumberForDisplay")) {
            if (DBG_MERGE) log("getLine1NumberForDisplay returning null due to permission");
            return null;
        }

        String iccId = getIccId(subId);
        if (iccId != null) {
            String numberPrefKey = PREF_CARRIERS_NUMBER_PREFIX + iccId;
            if (DBG_MERGE) {
                log("getLine1NumberForDisplay returning " +
                        mTelephonySharedPreferences.getString(numberPrefKey, null));
            }
            return mTelephonySharedPreferences.getString(numberPrefKey, null);
        }
        if (DBG_MERGE) log("getLine1NumberForDisplay returning null as iccId is null");
        return null;
    }

    @Override
    public String getLine1AlphaTagForDisplay(int subId, String callingPackage) {
        if (!canReadPhoneState(callingPackage, "getLine1AlphaTagForDisplay")) {
            return null;
        }

        String iccId = getIccId(subId);
        if (iccId != null) {
            String alphaTagPrefKey = PREF_CARRIERS_ALPHATAG_PREFIX + iccId;
            return mTelephonySharedPreferences.getString(alphaTagPrefKey, null);
        }
        return null;
    }

    @Override
    public String[] getMergedSubscriberIds(String callingPackage) {
        if (!canReadPhoneState(callingPackage, "getMergedSubscriberIds")) {
            return null;
        }
        final Context context = mPhone.getContext();
        final TelephonyManager tele = TelephonyManager.from(context);
        final SubscriptionManager sub = SubscriptionManager.from(context);

        // Figure out what subscribers are currently active
        final ArraySet<String> activeSubscriberIds = new ArraySet<>();
        // Clear calling identity, when calling TelephonyManager, because callerUid must be
        // the process, where TelephonyManager was instantiated. Otherwise AppOps check will fail.
        final long identity  = Binder.clearCallingIdentity();
        try {
            final int[] subIds = sub.getActiveSubscriptionIdList();
            for (int subId : subIds) {
                activeSubscriberIds.add(tele.getSubscriberId(subId));
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }

        // First pass, find a number override for an active subscriber
        String mergeNumber = null;
        final Map<String, ?> prefs = mTelephonySharedPreferences.getAll();
        for (String key : prefs.keySet()) {
            if (key.startsWith(PREF_CARRIERS_SUBSCRIBER_PREFIX)) {
                final String subscriberId = (String) prefs.get(key);
                if (activeSubscriberIds.contains(subscriberId)) {
                    final String iccId = key.substring(PREF_CARRIERS_SUBSCRIBER_PREFIX.length());
                    final String numberKey = PREF_CARRIERS_NUMBER_PREFIX + iccId;
                    mergeNumber = (String) prefs.get(numberKey);
                    if (DBG_MERGE) {
                        Slog.d(LOG_TAG, "Found line number " + mergeNumber
                                + " for active subscriber " + subscriberId);
                    }
                    if (!TextUtils.isEmpty(mergeNumber)) {
                        break;
                    }
                }
            }
        }

        // Shortcut when no active merged subscribers
        if (TextUtils.isEmpty(mergeNumber)) {
            return null;
        }

        // Second pass, find all subscribers under that line override
        final ArraySet<String> result = new ArraySet<>();
        for (String key : prefs.keySet()) {
            if (key.startsWith(PREF_CARRIERS_NUMBER_PREFIX)) {
                final String number = (String) prefs.get(key);
                if (mergeNumber.equals(number)) {
                    final String iccId = key.substring(PREF_CARRIERS_NUMBER_PREFIX.length());
                    final String subscriberKey = PREF_CARRIERS_SUBSCRIBER_PREFIX + iccId;
                    final String subscriberId = (String) prefs.get(subscriberKey);
                    if (!TextUtils.isEmpty(subscriberId)) {
                        result.add(subscriberId);
                    }
                }
            }
        }

        final String[] resultArray = result.toArray(new String[result.size()]);
        Arrays.sort(resultArray);
        if (DBG_MERGE) {
            Slog.d(LOG_TAG, "Found subscribers " + Arrays.toString(resultArray) + " after merge");
        }
        return resultArray;
    }

    @Override
    public boolean setOperatorBrandOverride(int subId, String brand) {
        enforceCarrierPrivilege(subId);
        final Phone phone = getPhone(subId);
        return phone == null ? false : phone.setOperatorBrandOverride(brand);
    }

    @Override
    public boolean setRoamingOverride(int subId, List<String> gsmRoamingList,
            List<String> gsmNonRoamingList, List<String> cdmaRoamingList,
            List<String> cdmaNonRoamingList) {
        enforceCarrierPrivilege(subId);
        final Phone phone = getPhone(subId);
        if (phone == null) {
            return false;
        }
        return phone.setRoamingOverride(gsmRoamingList, gsmNonRoamingList, cdmaRoamingList,
                cdmaNonRoamingList);
    }

    @Override
    @Deprecated
    public int invokeOemRilRequestRaw(byte[] oemReq, byte[] oemResp) {
        enforceModifyPermission();

        int returnValue = 0;
        try {
            AsyncResult result = (AsyncResult)sendRequest(CMD_INVOKE_OEM_RIL_REQUEST_RAW, oemReq);
            if(result.exception == null) {
                if (result.result != null) {
                    byte[] responseData = (byte[])(result.result);
                    if(responseData.length > oemResp.length) {
                        Log.w(LOG_TAG, "Buffer to copy response too small: Response length is " +
                                responseData.length +  "bytes. Buffer Size is " +
                                oemResp.length + "bytes.");
                    }
                    System.arraycopy(responseData, 0, oemResp, 0, responseData.length);
                    returnValue = responseData.length;
                }
            } else {
                CommandException ex = (CommandException) result.exception;
                returnValue = ex.getCommandError().ordinal();
                if(returnValue > 0) returnValue *= -1;
            }
        } catch (RuntimeException e) {
            Log.w(LOG_TAG, "sendOemRilRequestRaw: Runtime Exception");
            returnValue = (CommandException.Error.GENERIC_FAILURE.ordinal());
            if(returnValue > 0) returnValue *= -1;
        }

        return returnValue;
    }

    @Override
    public void setRadioCapability(RadioAccessFamily[] rafs) {
        try {
            ProxyController.getInstance().setRadioCapability(rafs);
        } catch (RuntimeException e) {
            Log.w(LOG_TAG, "setRadioCapability: Runtime Exception");
        }
    }

    @Override
    public int getRadioAccessFamily(int phoneId, String callingPackage) {
        if (!canReadPhoneState(callingPackage, "getRadioAccessFamily")) {
            return RadioAccessFamily.RAF_UNKNOWN;
        }

        return ProxyController.getInstance().getRadioAccessFamily(phoneId);
    }

    @Override
    public void enableVideoCalling(boolean enable) {
        enforceModifyPermission();
        ImsManager.setVtSetting(mPhone.getContext(), enable);
    }

    @Override
    public boolean isVideoCallingEnabled(String callingPackage) {
        if (!canReadPhoneState(callingPackage, "isVideoCallingEnabled")) {
            return false;
        }

        // Check the user preference and the  system-level IMS setting. Even if the user has
        // enabled video calling, if IMS is disabled we aren't able to support video calling.
        // In the long run, we may instead need to check if there exists a connection service
        // which can support video calling.
        return ImsManager.isVtEnabledByPlatform(mPhone.getContext())
                && ImsManager.isEnhanced4gLteModeSettingEnabledByUser(mPhone.getContext())
                && ImsManager.isVtEnabledByUser(mPhone.getContext());
    }

    @Override
    public boolean canChangeDtmfToneLength() {
        return mApp.getCarrierConfig().getBoolean(CarrierConfigManager.KEY_DTMF_TYPE_ENABLED_BOOL);
    }

    @Override
    public boolean isWorldPhone() {
        return mApp.getCarrierConfig().getBoolean(CarrierConfigManager.KEY_WORLD_PHONE_BOOL);
    }

    @Override
    public boolean isTtyModeSupported() {
        TelecomManager telecomManager = TelecomManager.from(mPhone.getContext());
        TelephonyManager telephonyManager =
                (TelephonyManager) mPhone.getContext().getSystemService(Context.TELEPHONY_SERVICE);
        return telecomManager.isTtySupported();
    }

    @Override
    public boolean isHearingAidCompatibilitySupported() {
        return mPhone.getContext().getResources().getBoolean(R.bool.hac_enabled);
    }

    /**
     * Returns the unique device ID of phone, for example, the IMEI for
     * GSM and the MEID for CDMA phones. Return null if device ID is not available.
     *
     * <p>Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     */
    @Override
    public String getDeviceId(String callingPackage) {
        if (!canReadPhoneState(callingPackage, "getDeviceId")) {
            return null;
        }

        final Phone phone = PhoneFactory.getPhone(0);
        if (phone != null) {
            return phone.getDeviceId();
        } else {
            return null;
        }
    }

    /*
     * {@hide}
     * Returns the IMS Registration Status
     */
    @Override
    public boolean isImsRegistered() {
        return mPhone.isImsRegistered();
    }

    @Override
    public int getSubIdForPhoneAccount(PhoneAccount phoneAccount) {
        return PhoneUtils.getSubIdForPhoneAccount(phoneAccount);
    }

    /*
     * {@hide}
     * Returns the IMS Registration Status
     */
    public boolean isWifiCallingAvailable() {
        return mPhone.isWifiCallingEnabled();
    }

    /*
     * {@hide}
     * Returns the IMS Registration Status
     */
    public boolean isVolteAvailable() {
        return mPhone.isVolteEnabled();
    }

    /*
     * {@hide} Returns the IMS Registration Status
     */
    public boolean isVideoTelephonyAvailable() {
        return mPhone.isVideoEnabled();
    }

    private boolean canReadPhoneState(String callingPackage, String message) {
        try {
            mApp.enforceCallingOrSelfPermission(
                    android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE, message);

            // SKIP checking for run-time permission since caller or self has PRIVILEDGED permission
            return true;
        } catch (SecurityException e) {
            mApp.enforceCallingOrSelfPermission(android.Manifest.permission.READ_PHONE_STATE,
                    message);
        }

        if (mAppOps.noteOp(AppOpsManager.OP_READ_PHONE_STATE, Binder.getCallingUid(),
                callingPackage) != AppOpsManager.MODE_ALLOWED) {
            return false;
        }

        return true;
    }

    /**
     * Besides READ_PHONE_STATE, WRITE_SMS and READ_SMS also allow apps to get phone numbers.
     */
    private boolean canReadPhoneNumber(String callingPackage, String message) {
        // Default SMS app can always read it.
        if (mAppOps.noteOp(AppOpsManager.OP_WRITE_SMS,
                Binder.getCallingUid(), callingPackage) == AppOpsManager.MODE_ALLOWED) {
            return true;
        }

        try {
            return canReadPhoneState(callingPackage, message);
        } catch (SecurityException readPhoneStateSecurityException) {
        }
        // Can be read with READ_SMS too.
        try {
            mApp.enforceCallingOrSelfPermission(android.Manifest.permission.READ_SMS, message);
            int opCode = mAppOps.permissionToOpCode(android.Manifest.permission.READ_SMS);
            if (opCode != AppOpsManager.OP_NONE) {
                return mAppOps.noteOp(opCode, Binder.getCallingUid(), callingPackage)
                        == AppOpsManager.MODE_ALLOWED;
            } else {
                return true;
            }
        } catch (SecurityException readSmsSecurityException) {
        }
        // Can be read with READ_PHONE_NUMBERS too.
        try {
            mApp.enforceCallingOrSelfPermission(android.Manifest.permission.READ_PHONE_NUMBERS,
                    message);
            int opCode = mAppOps.permissionToOpCode(android.Manifest.permission.READ_PHONE_NUMBERS);
            if (opCode != AppOpsManager.OP_NONE) {
                return mAppOps.noteOp(opCode, Binder.getCallingUid(), callingPackage)
                        == AppOpsManager.MODE_ALLOWED;
            } else {
                return true;
            }
        } catch (SecurityException readPhoneNumberSecurityException) {
        }

        throw new SecurityException(message + ": Neither user " + Binder.getCallingUid() +
                " nor current process has" + android.Manifest.permission.READ_PHONE_STATE +
                ", " + android.Manifest.permission.READ_SMS + ", or " +
                android.Manifest.permission.READ_PHONE_NUMBERS);
    }

    @Override
    public void factoryReset(int subId) {
        enforceConnectivityInternalPermission();
        if (mUserManager.hasUserRestriction(UserManager.DISALLOW_NETWORK_RESET)) {
            return;
        }

        final long identity = Binder.clearCallingIdentity();
        try {
            if (SubscriptionManager.isUsableSubIdValue(subId) && !mUserManager.hasUserRestriction(
                    UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS)) {
                // Enable data
                setDataEnabled(subId, true);
                // Set network selection mode to automatic
                setNetworkSelectionModeAutomatic(subId);
                // Set preferred mobile network type to the best available
                setPreferredNetworkType(subId, Phone.PREFERRED_NT_MODE);
                // Turn off roaming
                mPhone.setDataRoamingEnabled(false);
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public String getLocaleFromDefaultSim() {
        // We query all subscriptions instead of just the active ones, because
        // this might be called early on in the provisioning flow when the
        // subscriptions potentially aren't active yet.
        final List<SubscriptionInfo> slist = getAllSubscriptionInfoList();
        if (slist == null || slist.isEmpty()) {
            return null;
        }

        // This function may be called very early, say, from the setup wizard, at
        // which point we won't have a default subscription set. If that's the case
        // we just choose the first, which will be valid in "most cases".
        final int defaultSubId = getDefaultSubscription();
        SubscriptionInfo info = null;
        if (defaultSubId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            info = slist.get(0);
        } else {
            for (SubscriptionInfo item : slist) {
                if (item.getSubscriptionId() == defaultSubId) {
                    info = item;
                    break;
                }
            }

            if (info == null) {
                return null;
            }
        }

        // Try and fetch the locale from the carrier properties or from the SIM language
        // preferences (EF-PL and EF-LI)...
        final int mcc = info.getMcc();
        final Phone defaultPhone = getPhone(info.getSubscriptionId());
        String simLanguage = null;
        if (defaultPhone != null) {
            final Locale localeFromDefaultSim = defaultPhone.getLocaleFromSimAndCarrierPrefs();
            if (localeFromDefaultSim != null) {
                if (!localeFromDefaultSim.getCountry().isEmpty()) {
                    if (DBG) log("Using locale from default SIM:" + localeFromDefaultSim);
                    return localeFromDefaultSim.toLanguageTag();
                } else {
                    simLanguage = localeFromDefaultSim.getLanguage();
                }
            }
        }

        // The SIM language preferences only store a language (e.g. fr = French), not an
        // exact locale (e.g. fr_FR = French/France). So, if the locale returned from
        // the SIM and carrier preferences does not include a country we add the country
        // determined from the SIM MCC to provide an exact locale.
        final Locale mccLocale = MccTable.getLocaleFromMcc(mPhone.getContext(), mcc, simLanguage);
        if (mccLocale != null) {
            if (DBG) log("No locale from default SIM, using mcc locale:" + mccLocale);
            return mccLocale.toLanguageTag();
        }

        if (DBG) log("No locale found - returning null");
        return null;
    }

    private List<SubscriptionInfo> getAllSubscriptionInfoList() {
        final long identity = Binder.clearCallingIdentity();
        try {
            return mSubscriptionController.getAllSubInfoList(
                    mPhone.getContext().getOpPackageName());
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private List<SubscriptionInfo> getActiveSubscriptionInfoList() {
        final long identity = Binder.clearCallingIdentity();
        try {
            return mSubscriptionController.getActiveSubscriptionInfoList(
                    mPhone.getContext().getOpPackageName());
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Responds to the ResultReceiver with the {@link android.telephony.ModemActivityInfo} object
     * representing the state of the modem.
     *
     * NOTE: This clears the modem state, so there should only every be one caller.
     * @hide
     */
    @Override
    public void requestModemActivityInfo(ResultReceiver result) {
        enforceModifyPermission();

        ModemActivityInfo info = (ModemActivityInfo) sendRequest(CMD_GET_MODEM_ACTIVITY_INFO, null);
        Bundle bundle = new Bundle();
        bundle.putParcelable(TelephonyManager.MODEM_ACTIVITY_RESULT_KEY, info);
        result.send(0, bundle);
    }

    /**
     * {@hide}
     * Returns the service state information on specified subscription.
     */
    @Override
    public ServiceState getServiceStateForSubscriber(int subId, String callingPackage) {

        if (!canReadPhoneState(callingPackage, "getServiceStateForSubscriber")) {
            return null;
        }

        final Phone phone = getPhone(subId);
        if (phone == null) {
            return null;
        }

        return phone.getServiceState();
    }

    /**
     * Returns the URI for the per-account voicemail ringtone set in Phone settings.
     *
     * @param accountHandle The handle for the {@link PhoneAccount} for which to retrieve the
     * voicemail ringtone.
     * @return The URI for the ringtone to play when receiving a voicemail from a specific
     * PhoneAccount.
     */
    @Override
    public Uri getVoicemailRingtoneUri(PhoneAccountHandle accountHandle) {
        Phone phone = PhoneUtils.getPhoneForPhoneAccountHandle(accountHandle);
        if (phone == null) {
            phone = mPhone;
        }

        return VoicemailNotificationSettingsUtil.getRingtoneUri(phone.getContext());
    }

    /**
     * Sets the per-account voicemail ringtone.
     *
     * <p>Requires that the calling app is the default dialer, or has carrier privileges, or
     * has permission {@link android.Manifest.permission#MODIFY_PHONE_STATE MODIFY_PHONE_STATE}.
     *
     * @param phoneAccountHandle The handle for the {@link PhoneAccount} for which to set the
     * voicemail ringtone.
     * @param uri The URI for the ringtone to play when receiving a voicemail from a specific
     * PhoneAccount.
     */
    @Override
    public void setVoicemailRingtoneUri(String callingPackage,
            PhoneAccountHandle phoneAccountHandle, Uri uri) {
        mAppOps.checkPackage(Binder.getCallingUid(), callingPackage);
        if (!TextUtils.equals(callingPackage,
                TelecomManager.from(mPhone.getContext()).getDefaultDialerPackage())) {
            enforceModifyPermissionOrCarrierPrivilege(
                    PhoneUtils.getSubIdForPhoneAccountHandle(phoneAccountHandle));
        }
        Phone phone = PhoneUtils.getPhoneForPhoneAccountHandle(phoneAccountHandle);
        if (phone == null){
           phone = mPhone;
        }
        VoicemailNotificationSettingsUtil.setRingtoneUri(phone.getContext(), uri);
    }

    /**
     * Returns whether vibration is set for voicemail notification in Phone settings.
     *
     * @param accountHandle The handle for the {@link PhoneAccount} for which to retrieve the
     * voicemail vibration setting.
     * @return {@code true} if the vibration is set for this PhoneAccount, {@code false} otherwise.
     */
    @Override
    public boolean isVoicemailVibrationEnabled(PhoneAccountHandle accountHandle) {
        Phone phone = PhoneUtils.getPhoneForPhoneAccountHandle(accountHandle);
        if (phone == null) {
            phone = mPhone;
        }

        return VoicemailNotificationSettingsUtil.isVibrationEnabled(phone.getContext());
    }

    /**
     * Sets the per-account voicemail vibration.
     *
     * <p>Requires that the calling app is the default dialer, or has carrier privileges, or
     * has permission {@link android.Manifest.permission#MODIFY_PHONE_STATE MODIFY_PHONE_STATE}.
     *
     * @param phoneAccountHandle The handle for the {@link PhoneAccount} for which to set the
     * voicemail vibration setting.
     * @param enabled Whether to enable or disable vibration for voicemail notifications from a
     * specific PhoneAccount.
     */
    @Override
    public void setVoicemailVibrationEnabled(String callingPackage,
            PhoneAccountHandle phoneAccountHandle, boolean enabled) {
        mAppOps.checkPackage(Binder.getCallingUid(), callingPackage);
        if (!TextUtils.equals(callingPackage,
                TelecomManager.from(mPhone.getContext()).getDefaultDialerPackage())) {
            enforceModifyPermissionOrCarrierPrivilege(
                    PhoneUtils.getSubIdForPhoneAccountHandle(phoneAccountHandle));
        }

        Phone phone = PhoneUtils.getPhoneForPhoneAccountHandle(phoneAccountHandle);
        if (phone == null){
            phone = mPhone;
        }
        VoicemailNotificationSettingsUtil.setVibrationEnabled(phone.getContext(), enabled);
    }

    /**
     * Make sure either called from same process as self (phone) or IPC caller has read privilege.
     *
     * @throws SecurityException if the caller does not have the required permission
     */
    private void enforceReadPrivilegedPermission() {
        mApp.enforceCallingOrSelfPermission(android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE,
                null);
    }

    /**
     * Make sure either called from same process as self (phone) or IPC caller has send SMS
     * permission.
     *
     * @throws SecurityException if the caller does not have the required permission
     */
    private void enforceSendSmsPermission() {
        mApp.enforceCallingOrSelfPermission(permission.SEND_SMS, null);
    }

    /**
     * Make sure called from the package in charge of visual voicemail.
     *
     * @throws SecurityException if the caller is not the visual voicemail package.
     */
    private void enforceVisualVoicemailPackage(String callingPackage, int subId) {
        ComponentName componentName =
                RemoteVvmTaskManager.getRemotePackage(mPhone.getContext(), subId);
        if(componentName == null) {
            throw new SecurityException("Caller not current active visual voicemail package[null]");
        }
        String vvmPackage = componentName.getPackageName();
        if (!callingPackage.equals(vvmPackage)) {
            throw new SecurityException("Caller not current active visual voicemail package[" +
                    vvmPackage + "]");
        }
    }

    /**
     * Return the application ID for the app type.
     *
     * @param subId the subscription ID that this request applies to.
     * @param appType the uicc app type.
     * @return Application ID for specificied app type, or null if no uicc.
     */
    @Override
    public String getAidForAppType(int subId, int appType) {
        enforceReadPrivilegedPermission();
        Phone phone = getPhone(subId);
        if (phone == null) {
            return null;
        }
        String aid = null;
        try {
            aid = UiccController.getInstance().getUiccCard(phone.getPhoneId())
                    .getApplicationByType(appType).getAid();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Not getting aid. Exception ex=" + e);
        }
        return aid;
    }

    /**
     * Return the Electronic Serial Number.
     *
     * @param subId the subscription ID that this request applies to.
     * @return ESN or null if error.
     */
    @Override
    public String getEsn(int subId) {
        enforceReadPrivilegedPermission();
        Phone phone = getPhone(subId);
        if (phone == null) {
            return null;
        }
        String esn = null;
        try {
            esn = phone.getEsn();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Not getting ESN. Exception ex=" + e);
        }
        return esn;
    }

    /**
     * Return the Preferred Roaming List Version.
     *
     * @param subId the subscription ID that this request applies to.
     * @return PRLVersion or null if error.
     */
    @Override
    public String getCdmaPrlVersion(int subId) {
        enforceReadPrivilegedPermission();
        Phone phone = getPhone(subId);
        if (phone == null) {
            return null;
        }
        String cdmaPrlVersion = null;
        try {
            cdmaPrlVersion = phone.getCdmaPrlVersion();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Not getting PRLVersion", e);
        }
        return cdmaPrlVersion;
    }

    /**
     * Get snapshot of Telephony histograms
     * @return List of Telephony histograms
     * @hide
     */
    @Override
    public List<TelephonyHistogram> getTelephonyHistograms() {
        enforceModifyPermissionOrCarrierPrivilege(getDefaultSubscription());
        return RIL.getTelephonyRILTimingHistograms();
    }

    /**
     * {@hide}
     * Set the allowed carrier list for slotIndex
     * Require system privileges. In the future we may add this to carrier APIs.
     *
     * @return The number of carriers set successfully, should match length of carriers
     */
    @Override
    public int setAllowedCarriers(int slotIndex, List<CarrierIdentifier> carriers) {
        enforceModifyPermission();

        if (carriers == null) {
            throw new NullPointerException("carriers cannot be null");
        }

        int subId = SubscriptionManager.getSubId(slotIndex)[0];
        int[] retVal = (int[]) sendRequest(CMD_SET_ALLOWED_CARRIERS, carriers, subId);
        return retVal[0];
    }

    /**
     * {@hide}
     * Get the allowed carrier list for slotIndex.
     * Require system privileges. In the future we may add this to carrier APIs.
     *
     * @return List of {@link android.service.telephony.CarrierIdentifier}; empty list
     * means all carriers are allowed.
     */
    @Override
    public List<CarrierIdentifier> getAllowedCarriers(int slotIndex) {
        enforceReadPrivilegedPermission();
        int subId = SubscriptionManager.getSubId(slotIndex)[0];
        return (List<CarrierIdentifier>) sendRequest(CMD_GET_ALLOWED_CARRIERS, null, subId);
    }

    /**
     * Action set from carrier signalling broadcast receivers to enable/disable metered apns
     * @param subId the subscription ID that this action applies to.
     * @param enabled control enable or disable metered apns.
     * {@hide}
     */
    @Override
    public void carrierActionSetMeteredApnsEnabled(int subId, boolean enabled) {
        enforceModifyPermission();
        final Phone phone = getPhone(subId);
        if (phone == null) {
            loge("carrierAction: SetMeteredApnsEnabled fails with invalid subId: " + subId);
            return;
        }
        try {
            phone.carrierActionSetMeteredApnsEnabled(enabled);
        } catch (Exception e) {
            Log.e(LOG_TAG, "carrierAction: SetMeteredApnsEnabled fails. Exception ex=" + e);
        }
    }

    /**
     * Action set from carrier signalling broadcast receivers to enable/disable radio
     * @param subId the subscription ID that this action applies to.
     * @param enabled control enable or disable radio.
     * {@hide}
     */
    @Override
    public void carrierActionSetRadioEnabled(int subId, boolean enabled) {
        enforceModifyPermission();
        final Phone phone = getPhone(subId);
        if (phone == null) {
            loge("carrierAction: SetRadioEnabled fails with invalid sibId: " + subId);
            return;
        }
        try {
            phone.carrierActionSetRadioEnabled(enabled);
        } catch (Exception e) {
            Log.e(LOG_TAG, "carrierAction: SetRadioEnabled fails. Exception ex=" + e);
        }
    }

    /**
     * Action set from carrier signalling broadcast receivers to start/stop reporting the default
     * network status based on which carrier apps could apply actions accordingly,
     * enable/disable default url handler for example.
     *
     * @param subId the subscription ID that this action applies to.
     * @param report control start/stop reporting the default network status.
     * {@hide}
     */
    @Override
    public void carrierActionReportDefaultNetworkStatus(int subId, boolean report) {
        enforceModifyPermission();
        final Phone phone = getPhone(subId);
        if (phone == null) {
            loge("carrierAction: ReportDefaultNetworkStatus fails with invalid sibId: " + subId);
            return;
        }
        try {
            phone.carrierActionReportDefaultNetworkStatus(report);
        } catch (Exception e) {
            Log.e(LOG_TAG, "carrierAction: ReportDefaultNetworkStatus fails. Exception ex=" + e);
        }
    }

    /**
     * Called when "adb shell dumpsys phone" is invoked. Dump is also automatically invoked when a
     * bug report is being generated.
     */
    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        if (mPhone.getContext().checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {
            writer.println("Permission Denial: can't dump Phone from pid="
                    + Binder.getCallingPid()
                    + ", uid=" + Binder.getCallingUid()
                    + "without permission "
                    + android.Manifest.permission.DUMP);
            return;
        }
        DumpsysHandler.dump(mPhone.getContext(), fd, writer, args);
    }

    /**
     * Get aggregated video call data usage since boot.
     *
     * @param perUidStats True if requesting data usage per uid, otherwise overall usage.
     * @return Snapshot of video call data usage
     * {@hide}
     */
    @Override
    public NetworkStats getVtDataUsage(int subId, boolean perUidStats) {
        mApp.enforceCallingOrSelfPermission(android.Manifest.permission.READ_NETWORK_USAGE_HISTORY,
                null);

        // NetworkStatsService keeps tracking the active network interface and identity. It
        // records the delta with the corresponding network identity. We just return the total video
        // call data usage snapshot since boot.
        Phone phone = getPhone(subId);
        if (phone != null) {
            return phone.getVtDataUsage(perUidStats);
        }
        return null;
    }

    /**
     * Policy control of data connection. Usually used when data limit is passed.
     * @param enabled True if enabling the data, otherwise disabling.
     * @param subId Subscription index
     * {@hide}
     */
    @Override
    public void setPolicyDataEnabled(boolean enabled, int subId) {
        enforceModifyPermission();
        Phone phone = getPhone(subId);
        if (phone != null) {
            phone.setPolicyDataEnabled(enabled);
        }
    }

    /**
     * Get Client request stats
     * @return List of Client Request Stats
     * @hide
     */
    @Override
    public List<ClientRequestStats> getClientRequestStats(String callingPackage, int subId) {
        if (!canReadPhoneState(callingPackage, "getClientRequestStats")) {
            return null;
        }

        Phone phone = getPhone(subId);
        if (phone != null) {
            return phone.getClientRequestStats();
        }

        return null;
    }

    private WorkSource getWorkSource(WorkSource workSource, int uid) {
        if (workSource != null) {
            return workSource;
        }

        String packageName = mPhone.getContext().getPackageManager().getNameForUid(uid);
        workSource = new WorkSource(uid, packageName);
        return workSource;
    }

    /**
     * Set SIM card power state.
     *
     * @param slotIndex SIM slot id.
     * @param state  State of SIM (power down, power up, pass through)
     * - {@link android.telephony.TelephonyManager#CARD_POWER_DOWN}
     * - {@link android.telephony.TelephonyManager#CARD_POWER_UP}
     * - {@link android.telephony.TelephonyManager#CARD_POWER_UP_PASS_THROUGH}
     *
     **/
    @Override
    public void setSimPowerStateForSlot(int slotIndex, int state) {
        enforceModifyPermission();
        Phone phone = PhoneFactory.getPhone(slotIndex);

        if (phone != null) {
            phone.setSimPowerState(state);
        }
    }

    private boolean isUssdApiAllowed(int subId) {
        CarrierConfigManager configManager =
                (CarrierConfigManager) mPhone.getContext().getSystemService(
                        Context.CARRIER_CONFIG_SERVICE);
        if (configManager == null) {
            return false;
        }
        PersistableBundle pb = configManager.getConfigForSubId(subId);
        if (pb == null) {
            return false;
        }
        return pb.getBoolean(
                CarrierConfigManager.KEY_ALLOW_USSD_REQUESTS_VIA_TELEPHONY_MANAGER_BOOL);
    }

    /**
     * Check if phone is in emergency callback mode
     * @return true if phone is in emergency callback mode
     * @param subId sub id
     */
    public boolean getEmergencyCallbackMode(int subId) {
        final Phone phone = getPhone(subId);
        if (phone != null) {
            return phone.isInEcm();
        } else {
            return false;
        }
    }

    /**
     * Get the current signal strength information for the given subscription.
     * Because this information is not updated when the device is in a low power state
     * it should not be relied-upon to be current.
     * @param subId Subscription index
     * @return the most recent cached signal strength info from the modem
     */
    @Override
    public SignalStrength getSignalStrength(int subId) {
        Phone p = getPhone(subId);
        if (p == null) {
            return null;
        }

        return p.getSignalStrength();
    }
}
