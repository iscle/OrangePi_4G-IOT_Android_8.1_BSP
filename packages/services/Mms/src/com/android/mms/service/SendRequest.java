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

package com.android.mms.service;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.Telephony;
import android.service.carrier.CarrierMessagingService;
import android.service.carrier.ICarrierMessagingService;
import android.telephony.CarrierMessagingServiceManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import android.text.TextUtils;

import com.android.internal.telephony.AsyncEmergencyContactNotifier;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.SmsApplication;
import com.android.internal.telephony.SmsNumberUtils;
import com.android.mms.service.exception.MmsHttpException;
import com.google.android.mms.MmsException;
import com.google.android.mms.pdu.EncodedStringValue;
import com.google.android.mms.pdu.GenericPdu;
import com.google.android.mms.pdu.PduComposer;
import com.google.android.mms.pdu.PduHeaders;
import com.google.android.mms.pdu.PduParser;
import com.google.android.mms.pdu.PduPersister;
import com.google.android.mms.pdu.SendConf;
import com.google.android.mms.pdu.SendReq;
import com.google.android.mms.util.SqliteWrapper;

/**
 * Request to send an MMS
 */
public class SendRequest extends MmsRequest {
    private final Uri mPduUri;
    private byte[] mPduData;
    private final String mLocationUrl;
    private final PendingIntent mSentIntent;

    public SendRequest(RequestManager manager, int subId, Uri contentUri, String locationUrl,
            PendingIntent sentIntent, String creator, Bundle configOverrides, Context context) {
        super(manager, subId, creator, configOverrides, context);
        mPduUri = contentUri;
        mPduData = null;
        mLocationUrl = locationUrl;
        mSentIntent = sentIntent;
    }

    @Override
    protected byte[] doHttp(Context context, MmsNetworkManager netMgr, ApnSettings apn)
            throws MmsHttpException {
        final String requestId = getRequestId();
        final MmsHttpClient mmsHttpClient = netMgr.getOrCreateHttpClient();
        if (mmsHttpClient == null) {
            LogUtil.e(requestId, "MMS network is not ready!");
            throw new MmsHttpException(0/*statusCode*/, "MMS network is not ready");
        }
        final GenericPdu parsedPdu = parsePdu();
        notifyIfEmergencyContactNoThrow(parsedPdu);
        updateDestinationAddress(parsedPdu);
        return mmsHttpClient.execute(
                mLocationUrl != null ? mLocationUrl : apn.getMmscUrl(),
                mPduData,
                MmsHttpClient.METHOD_POST,
                apn.isProxySet(),
                apn.getProxyAddress(),
                apn.getProxyPort(),
                mMmsConfig,
                mSubId,
                requestId);
    }

    private GenericPdu parsePdu() {
        final String requestId = getRequestId();
        try {
            if (mPduData == null) {
                LogUtil.w(requestId, "Empty PDU raw data");
                return null;
            }
            final boolean supportContentDisposition =
                    mMmsConfig.getBoolean(SmsManager.MMS_CONFIG_SUPPORT_MMS_CONTENT_DISPOSITION);
            return new PduParser(mPduData, supportContentDisposition).parse();
        } catch (final Exception e) {
            LogUtil.w(requestId, "Failed to parse PDU raw data");
        }
        return null;
    }

    /**
     * If the MMS is being sent to an emergency number, the blocked number provider is notified
     * so that it can disable number blocking.
     */
    private void notifyIfEmergencyContactNoThrow(final GenericPdu parsedPdu) {
        try {
            notifyIfEmergencyContact(parsedPdu);
        } catch (Exception e) {
            LogUtil.w(getRequestId(), "Error in notifyIfEmergencyContact", e);
        }
    }

    private void notifyIfEmergencyContact(final GenericPdu parsedPdu) {
        if (parsedPdu != null && parsedPdu.getMessageType() == PduHeaders.MESSAGE_TYPE_SEND_REQ) {
            SendReq sendReq = (SendReq) parsedPdu;
            for (EncodedStringValue encodedStringValue : sendReq.getTo()) {
                if (isEmergencyNumber(encodedStringValue.getString())) {
                    LogUtil.i(getRequestId(), "Notifying emergency contact");
                    new AsyncEmergencyContactNotifier(mContext).execute();
                    return;
                }
            }
        }
    }

    private boolean isEmergencyNumber(String address) {
        return !TextUtils.isEmpty(address) && PhoneNumberUtils.isEmergencyNumber(mSubId, address);
    }

    @Override
    protected PendingIntent getPendingIntent() {
        return mSentIntent;
    }

    @Override
    protected int getQueueType() {
        return MmsService.QUEUE_INDEX_SEND;
    }

    @Override
    protected Uri persistIfRequired(Context context, int result, byte[] response) {
        final String requestId = getRequestId();
        if (!SmsApplication.shouldWriteMessageForPackage(mCreator, context)) {
            // Not required to persist
            return null;
        }
        LogUtil.d(requestId, "persistIfRequired");
        if (mPduData == null) {
            LogUtil.e(requestId, "persistIfRequired: empty PDU");
            return null;
        }
        final long identity = Binder.clearCallingIdentity();
        try {
            final boolean supportContentDisposition =
                    mMmsConfig.getBoolean(SmsManager.MMS_CONFIG_SUPPORT_MMS_CONTENT_DISPOSITION);
            // Persist the request PDU first
            GenericPdu pdu = (new PduParser(mPduData, supportContentDisposition)).parse();
            if (pdu == null) {
                LogUtil.e(requestId, "persistIfRequired: can't parse input PDU");
                return null;
            }
            if (!(pdu instanceof SendReq)) {
                LogUtil.d(requestId, "persistIfRequired: not SendReq");
                return null;
            }
            final PduPersister persister = PduPersister.getPduPersister(context);
            final Uri messageUri = persister.persist(
                    pdu,
                    Telephony.Mms.Sent.CONTENT_URI,
                    true/*createThreadId*/,
                    true/*groupMmsEnabled*/,
                    null/*preOpenedFiles*/);
            if (messageUri == null) {
                LogUtil.e(requestId, "persistIfRequired: can not persist message");
                return null;
            }
            // Update the additional columns based on the send result
            final ContentValues values = new ContentValues();
            SendConf sendConf = null;
            if (response != null && response.length > 0) {
                pdu = (new PduParser(response, supportContentDisposition)).parse();
                if (pdu != null && pdu instanceof SendConf) {
                    sendConf = (SendConf) pdu;
                }
            }
            if (result != Activity.RESULT_OK
                    || sendConf == null
                    || sendConf.getResponseStatus() != PduHeaders.RESPONSE_STATUS_OK) {
                // Since we can't persist a message directly into FAILED box,
                // we have to update the column after we persist it into SENT box.
                // The gap between the state change is tiny so I would not expect
                // it to cause any serious problem
                // TODO: we should add a "failed" URI for this in MmsProvider?
                values.put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_FAILED);
            }
            if (sendConf != null) {
                values.put(Telephony.Mms.RESPONSE_STATUS, sendConf.getResponseStatus());
                byte[] messageId = sendConf.getMessageId();
                if (messageId != null) {
                    values.put(Telephony.Mms.MESSAGE_ID, PduPersister.toIsoString(messageId));
                }
            }
            values.put(Telephony.Mms.DATE, System.currentTimeMillis() / 1000L);
            values.put(Telephony.Mms.READ, 1);
            values.put(Telephony.Mms.SEEN, 1);
            if (!TextUtils.isEmpty(mCreator)) {
                values.put(Telephony.Mms.CREATOR, mCreator);
            }
            values.put(Telephony.Mms.SUBSCRIPTION_ID, mSubId);
            if (SqliteWrapper.update(context, context.getContentResolver(), messageUri, values,
                    null/*where*/, null/*selectionArg*/) != 1) {
                LogUtil.e(requestId, "persistIfRequired: failed to update message");
            }
            return messageUri;
        } catch (MmsException e) {
            LogUtil.e(requestId, "persistIfRequired: can not persist message", e);
        } catch (RuntimeException e) {
            LogUtil.e(requestId, "persistIfRequired: unexpected parsing failure", e);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
        return null;
    }

    /**
     * Update the destination Address of MO MMS before sending.
     * This is special for VZW requirement. Follow the specificaitons of assisted dialing
     * of MO MMS while traveling on VZW CDMA, international CDMA or GSM markets.
     */
    private void updateDestinationAddress(final GenericPdu pdu) {
        final String requestId = getRequestId();
        if (pdu == null) {
            LogUtil.e(requestId, "updateDestinationAddress: can't parse input PDU");
            return ;
        }
        if (!(pdu instanceof SendReq)) {
            LogUtil.i(requestId, "updateDestinationAddress: not SendReq");
            return;
        }

       boolean isUpdated = updateDestinationAddressPerType((SendReq)pdu, PduHeaders.TO);
       isUpdated = updateDestinationAddressPerType((SendReq)pdu, PduHeaders.CC) || isUpdated;
       isUpdated = updateDestinationAddressPerType((SendReq)pdu, PduHeaders.BCC) || isUpdated;

       if (isUpdated) {
           mPduData = new PduComposer(mContext, (SendReq)pdu).make();
       }
   }

    private boolean updateDestinationAddressPerType(SendReq pdu, int type) {
        boolean isUpdated = false;
        EncodedStringValue[] recipientNumbers = null;

        switch (type) {
            case PduHeaders.TO:
                recipientNumbers = pdu.getTo();
                break;
            case PduHeaders.CC:
                recipientNumbers = pdu.getCc();
                break;
            case PduHeaders.BCC:
                recipientNumbers = pdu.getBcc();
                break;
            default:
                return false;
        }

        if (recipientNumbers != null) {
            int nNumberCount = recipientNumbers.length;
            if (nNumberCount > 0) {
                Phone phone = PhoneFactory.getDefaultPhone();
                EncodedStringValue[] newNumbers = new EncodedStringValue[nNumberCount];
                String toNumber;
                String newToNumber;
                for (int i = 0; i < nNumberCount; i++) {
                    toNumber = recipientNumbers[i].getString();
                    newToNumber = SmsNumberUtils.filterDestAddr(phone, toNumber);
                    if (!TextUtils.equals(toNumber, newToNumber)) {
                        isUpdated = true;
                        newNumbers[i] = new EncodedStringValue(newToNumber);
                    } else {
                        newNumbers[i] = recipientNumbers[i];
                    }
                }
                switch (type) {
                    case PduHeaders.TO:
                        pdu.setTo(newNumbers);
                        break;
                    case PduHeaders.CC:
                        pdu.setCc(newNumbers);
                        break;
                    case PduHeaders.BCC:
                        pdu.setBcc(newNumbers);
                        break;
                }
            }
        }

        return isUpdated;
    }

    /**
     * Read the pdu from the file descriptor and cache pdu bytes in request
     * @return true if pdu read successfully
     */
    private boolean readPduFromContentUri() {
        if (mPduData != null) {
            return true;
        }
        final int bytesTobeRead = mMmsConfig.getInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE);
        mPduData = mRequestManager.readPduFromContentUri(mPduUri, bytesTobeRead);
        return (mPduData != null);
    }

    /**
     * Transfer the received response to the caller (for send requests the pdu is small and can
     *  just include bytes as extra in the "returned" intent).
     *
     * @param fillIn the intent that will be returned to the caller
     * @param response the pdu to transfer
     */
    @Override
    protected boolean transferResponse(Intent fillIn, byte[] response) {
        // SendConf pdus are always small and can be included in the intent
        if (response != null) {
            fillIn.putExtra(SmsManager.EXTRA_MMS_DATA, response);
        }
        return true;
    }

    /**
     * Read the data from the file descriptor if not yet done
     * @return whether data successfully read
     */
    @Override
    protected boolean prepareForHttpRequest() {
        return readPduFromContentUri();
    }

    /**
     * Try sending via the carrier app
     *
     * @param context the context
     * @param carrierMessagingServicePackage the carrier messaging service sending the MMS
     */
    public void trySendingByCarrierApp(Context context, String carrierMessagingServicePackage) {
        final CarrierSendManager carrierSendManger = new CarrierSendManager();
        final CarrierSendCompleteCallback sendCallback = new CarrierSendCompleteCallback(
                context, carrierSendManger);
        carrierSendManger.sendMms(context, carrierMessagingServicePackage, sendCallback);
    }

    @Override
    protected void revokeUriPermission(Context context) {
        if (mPduUri != null) {
            context.revokeUriPermission(mPduUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
    }

    /**
     * Sends the MMS through through the carrier app.
     */
    private final class CarrierSendManager extends CarrierMessagingServiceManager {
        // Initialized in sendMms
        private volatile CarrierSendCompleteCallback mCarrierSendCompleteCallback;

        void sendMms(Context context, String carrierMessagingServicePackage,
                CarrierSendCompleteCallback carrierSendCompleteCallback) {
            mCarrierSendCompleteCallback = carrierSendCompleteCallback;
            if (bindToCarrierMessagingService(context, carrierMessagingServicePackage)) {
                LogUtil.v("bindService() for carrier messaging service succeeded");
            } else {
                LogUtil.e("bindService() for carrier messaging service failed");
                carrierSendCompleteCallback.onSendMmsComplete(
                        CarrierMessagingService.SEND_STATUS_RETRY_ON_CARRIER_NETWORK,
                        null /* no sendConfPdu */);
            }
        }

        @Override
        protected void onServiceReady(ICarrierMessagingService carrierMessagingService) {
            try {
                Uri locationUri = null;
                if (mLocationUrl != null) {
                    locationUri = Uri.parse(mLocationUrl);
                }
                carrierMessagingService.sendMms(mPduUri, mSubId, locationUri,
                        mCarrierSendCompleteCallback);
            } catch (RemoteException e) {
                LogUtil.e("Exception sending MMS using the carrier messaging service: " + e, e);
                mCarrierSendCompleteCallback.onSendMmsComplete(
                        CarrierMessagingService.SEND_STATUS_RETRY_ON_CARRIER_NETWORK,
                        null /* no sendConfPdu */);
            }
        }
    }

    /**
     * A callback which notifies carrier messaging app send result. Once the result is ready, the
     * carrier messaging service connection is disposed.
     */
    private final class CarrierSendCompleteCallback extends
            MmsRequest.CarrierMmsActionCallback {
        private final Context mContext;
        private final CarrierSendManager mCarrierSendManager;

        public CarrierSendCompleteCallback(Context context, CarrierSendManager carrierSendManager) {
            mContext = context;
            mCarrierSendManager = carrierSendManager;
        }

        @Override
        public void onSendMmsComplete(int result, byte[] sendConfPdu) {
            LogUtil.d("Carrier app result for send: " + result);
            mCarrierSendManager.disposeConnection(mContext);

            if (!maybeFallbackToRegularDelivery(result)) {
                processResult(mContext, toSmsManagerResult(result), sendConfPdu,
                        0/* httpStatusCode */);
            }
        }

        @Override
        public void onDownloadMmsComplete(int result) {
            LogUtil.e("Unexpected onDownloadMmsComplete call with result: " + result);
        }
    }
}
