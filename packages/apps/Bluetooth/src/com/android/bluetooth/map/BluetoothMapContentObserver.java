/*
 * Copyright (C) 2014 Samsung System LSI
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
package com.android.bluetooth.map;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserManager;
import android.provider.Telephony;
import android.provider.Telephony.Mms;
import android.provider.Telephony.MmsSms;
import android.provider.Telephony.Sms;
import android.provider.Telephony.Sms.Inbox;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.text.format.DateUtils;
import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlSerializer;

import com.android.bluetooth.map.BluetoothMapUtils.TYPE;
import com.android.bluetooth.map.BluetoothMapbMessageMime.MimePart;
import com.android.bluetooth.mapapi.BluetoothMapContract;
import com.android.bluetooth.mapapi.BluetoothMapContract.MessageColumns;
import com.google.android.mms.pdu.PduHeaders;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.obex.ResponseCodes;

@TargetApi(19)
public class BluetoothMapContentObserver {
    private static final String TAG = "BluetoothMapContentObserver";

    private static final boolean D = BluetoothMapService.DEBUG;
    private static final boolean V = BluetoothMapService.VERBOSE;

    private static final String EVENT_TYPE_NEW              = "NewMessage";
    private static final String EVENT_TYPE_DELETE           = "MessageDeleted";
    private static final String EVENT_TYPE_REMOVED          = "MessageRemoved";
    private static final String EVENT_TYPE_SHIFT            = "MessageShift";
    private static final String EVENT_TYPE_DELEVERY_SUCCESS = "DeliverySuccess";
    private static final String EVENT_TYPE_SENDING_SUCCESS  = "SendingSuccess";
    private static final String EVENT_TYPE_SENDING_FAILURE  = "SendingFailure";
    private static final String EVENT_TYPE_DELIVERY_FAILURE = "DeliveryFailure";
    private static final String EVENT_TYPE_READ_STATUS      = "ReadStatusChanged";
    private static final String EVENT_TYPE_CONVERSATION     = "ConversationChanged";
    private static final String EVENT_TYPE_PRESENCE         = "ParticipantPresenceChanged";
    private static final String EVENT_TYPE_CHAT_STATE       = "ParticipantChatStateChanged";

    private static final long EVENT_FILTER_NEW_MESSAGE                  = 1L;
    private static final long EVENT_FILTER_MESSAGE_DELETED              = 1L<<1;
    private static final long EVENT_FILTER_MESSAGE_SHIFT                = 1L<<2;
    private static final long EVENT_FILTER_SENDING_SUCCESS              = 1L<<3;
    private static final long EVENT_FILTER_SENDING_FAILED               = 1L<<4;
    private static final long EVENT_FILTER_DELIVERY_SUCCESS             = 1L<<5;
    private static final long EVENT_FILTER_DELIVERY_FAILED              = 1L<<6;
    private static final long EVENT_FILTER_MEMORY_FULL                  = 1L<<7; // Unused
    private static final long EVENT_FILTER_MEMORY_AVAILABLE             = 1L<<8; // Unused
    private static final long EVENT_FILTER_READ_STATUS_CHANGED          = 1L<<9;
    private static final long EVENT_FILTER_CONVERSATION_CHANGED         = 1L<<10;
    private static final long EVENT_FILTER_PARTICIPANT_PRESENCE_CHANGED = 1L<<11;
    private static final long EVENT_FILTER_PARTICIPANT_CHATSTATE_CHANGED= 1L<<12;
    private static final long EVENT_FILTER_MESSAGE_REMOVED              = 1L<<13;

    // TODO: If we are requesting a large message from the network, on a slow connection
    //       20 seconds might not be enough... But then again 20 seconds is long for other
    //       cases.
    private static final long PROVIDER_ANR_TIMEOUT = 20 * DateUtils.SECOND_IN_MILLIS;

    private Context mContext;
    private ContentResolver mResolver;
    private ContentProviderClient mProviderClient = null;
    private BluetoothMnsObexClient mMnsClient;
    private BluetoothMapMasInstance mMasInstance = null;
    private int mMasId;
    private boolean mEnableSmsMms = false;
    private boolean mObserverRegistered = false;
    private BluetoothMapAccountItem mAccount;
    private String mAuthority = null;

    // Default supported feature bit mask is 0x1f
    private int mMapSupportedFeatures = BluetoothMapUtils.MAP_FEATURE_DEFAULT_BITMASK;
    // Default event report version is 1.0
    private int mMapEventReportVersion = BluetoothMapUtils.MAP_EVENT_REPORT_V10;

    private BluetoothMapFolderElement mFolders =
            new BluetoothMapFolderElement("DUMMY", null); // Will be set by the MAS when generated.
    private Uri mMessageUri = null;
    private Uri mContactUri = null;

    private boolean mTransmitEvents = true;

    /* To make the filter update atomic, we declare it volatile.
     * To avoid a penalty when using it, copy the value to a local
     * non-volatile variable when used more than once.
     * Actually we only ever use the lower 4 bytes of this variable,
     * hence we could manage without the volatile keyword, but as
     * we tend to copy ways of doing things, we better do it right:-) */
    private volatile long mEventFilter = 0xFFFFFFFFL;

    public static final int DELETED_THREAD_ID = -1;

    // X-Mms-Message-Type field types. These are from PduHeaders.java
    public static final int MESSAGE_TYPE_RETRIEVE_CONF = 0x84;

    // Text only MMS converted to SMS if sms parts less than or equal to defined count
    private static final int CONVERT_MMS_TO_SMS_PART_COUNT = 10;

    private TYPE mSmsType;

    private static final String ACTION_MESSAGE_DELIVERY =
            "com.android.bluetooth.BluetoothMapContentObserver.action.MESSAGE_DELIVERY";
    /*package*/ static final String ACTION_MESSAGE_SENT =
        "com.android.bluetooth.BluetoothMapContentObserver.action.MESSAGE_SENT";

    public static final String EXTRA_MESSAGE_SENT_HANDLE = "HANDLE";
    public static final String EXTRA_MESSAGE_SENT_RESULT = "result";
    public static final String EXTRA_MESSAGE_SENT_MSG_TYPE = "type";
    public static final String EXTRA_MESSAGE_SENT_URI = "uri";
    public static final String EXTRA_MESSAGE_SENT_RETRY = "retry";
    public static final String EXTRA_MESSAGE_SENT_TRANSPARENT = "transparent";
    public static final String EXTRA_MESSAGE_SENT_TIMESTAMP = "timestamp";

    private SmsBroadcastReceiver mSmsBroadcastReceiver = new SmsBroadcastReceiver();
    private CeBroadcastReceiver mCeBroadcastReceiver = new CeBroadcastReceiver();

    private boolean mStorageUnlocked = false;
    private boolean mInitialized = false;


    static final String[] SMS_PROJECTION = new String[] {
        Sms._ID,
        Sms.THREAD_ID,
        Sms.ADDRESS,
        Sms.BODY,
        Sms.DATE,
        Sms.READ,
        Sms.TYPE,
        Sms.STATUS,
        Sms.LOCKED,
        Sms.ERROR_CODE
    };

    static final String[] SMS_PROJECTION_SHORT = new String[] {
        Sms._ID,
        Sms.THREAD_ID,
        Sms.TYPE,
        Sms.READ
    };

    static final String[] SMS_PROJECTION_SHORT_EXT = new String[] {
        Sms._ID,
        Sms.THREAD_ID,
        Sms.ADDRESS,
        Sms.BODY,
        Sms.DATE,
        Sms.READ,
        Sms.TYPE,
    };

    static final String[] MMS_PROJECTION_SHORT = new String[] {
        Mms._ID,
        Mms.THREAD_ID,
        Mms.MESSAGE_TYPE,
        Mms.MESSAGE_BOX,
        Mms.READ
    };

    static final String[] MMS_PROJECTION_SHORT_EXT = new String[] {
        Mms._ID,
        Mms.THREAD_ID,
        Mms.MESSAGE_TYPE,
        Mms.MESSAGE_BOX,
        Mms.READ,
        Mms.DATE,
        Mms.SUBJECT,
        Mms.PRIORITY
    };

    static final String[] MSG_PROJECTION_SHORT = new String[] {
        BluetoothMapContract.MessageColumns._ID,
        BluetoothMapContract.MessageColumns.FOLDER_ID,
        BluetoothMapContract.MessageColumns.FLAG_READ
    };

    static final String[] MSG_PROJECTION_SHORT_EXT = new String[] {
        BluetoothMapContract.MessageColumns._ID,
        BluetoothMapContract.MessageColumns.FOLDER_ID,
        BluetoothMapContract.MessageColumns.FLAG_READ,
        BluetoothMapContract.MessageColumns.DATE,
        BluetoothMapContract.MessageColumns.SUBJECT,
        BluetoothMapContract.MessageColumns.FROM_LIST,
        BluetoothMapContract.MessageColumns.FLAG_HIGH_PRIORITY
    };

    static final String[] MSG_PROJECTION_SHORT_EXT2 = new String[] {
        BluetoothMapContract.MessageColumns._ID,
        BluetoothMapContract.MessageColumns.FOLDER_ID,
        BluetoothMapContract.MessageColumns.FLAG_READ,
        BluetoothMapContract.MessageColumns.DATE,
        BluetoothMapContract.MessageColumns.SUBJECT,
        BluetoothMapContract.MessageColumns.FROM_LIST,
        BluetoothMapContract.MessageColumns.FLAG_HIGH_PRIORITY,
        BluetoothMapContract.MessageColumns.THREAD_ID,
        BluetoothMapContract.MessageColumns.THREAD_NAME
    };

    public BluetoothMapContentObserver(final Context context,
            BluetoothMnsObexClient mnsClient,
            BluetoothMapMasInstance masInstance,
            BluetoothMapAccountItem account,
            boolean enableSmsMms) throws RemoteException {
        mContext = context;
        mResolver = mContext.getContentResolver();
        mAccount = account;
        mMasInstance = masInstance;
        mMasId = mMasInstance.getMasId();

        mMapSupportedFeatures = mMasInstance.getRemoteFeatureMask();
        if (D) Log.d(TAG, "BluetoothMapContentObserver: Supported features " +
                Integer.toHexString(mMapSupportedFeatures) ) ;

        if((BluetoothMapUtils.MAP_FEATURE_EXTENDED_EVENT_REPORT_11_BIT
                & mMapSupportedFeatures) != 0){
            mMapEventReportVersion = BluetoothMapUtils.MAP_EVENT_REPORT_V11;
        }
        // Make sure support for all formats result in latest version returned
        if((BluetoothMapUtils.MAP_FEATURE_EVENT_REPORT_V12_BIT
                & mMapSupportedFeatures) != 0){
            mMapEventReportVersion = BluetoothMapUtils.MAP_EVENT_REPORT_V12;
        }

        if(account != null) {
            mAuthority = Uri.parse(account.mBase_uri).getAuthority();
            mMessageUri = Uri.parse(account.mBase_uri + "/" + BluetoothMapContract.TABLE_MESSAGE);
            if (mAccount.getType() == TYPE.IM) {
                mContactUri = Uri.parse(account.mBase_uri + "/"
                        + BluetoothMapContract.TABLE_CONVOCONTACT);
            }
            // TODO: We need to release this again!
            mProviderClient = mResolver.acquireUnstableContentProviderClient(mAuthority);
            if (mProviderClient == null) {
                throw new RemoteException("Failed to acquire provider for " + mAuthority);
            }
            mProviderClient.setDetectNotResponding(PROVIDER_ANR_TIMEOUT);
            mContactList = mMasInstance.getContactList();
            if(mContactList == null) {
                setContactList(new HashMap<String, BluetoothMapConvoContactElement>(), false);
                initContactsList();
            }
        }
        mEnableSmsMms = enableSmsMms;
        mSmsType = getSmsType();
        mMnsClient = mnsClient;
        /* Get the cached list - if any, else create */
        mMsgListSms = mMasInstance.getMsgListSms();
        boolean doInit = false;
        if(mEnableSmsMms) {
            if(mMsgListSms == null) {
                setMsgListSms(new HashMap<Long, Msg>(), false);
                doInit = true;
            }
            mMsgListMms = mMasInstance.getMsgListMms();
            if(mMsgListMms == null) {
                setMsgListMms(new HashMap<Long, Msg>(), false);
                doInit = true;
            }
        }
        if(mAccount != null) {
            mMsgListMsg = mMasInstance.getMsgListMsg();
            if(mMsgListMsg == null) {
                setMsgListMsg(new HashMap<Long, Msg>(), false);
                doInit = true;
            }
        }
        if(doInit) {
            initMsgList();
        }
    }

    public int getObserverRemoteFeatureMask() {
        if (V) Log.v(TAG, "getObserverRemoteFeatureMask : " + mMapEventReportVersion
            + " mMapSupportedFeatures: " + mMapSupportedFeatures);
        return mMapSupportedFeatures;
    }

    public void setObserverRemoteFeatureMask(int remoteSupportedFeatures) {
        mMapSupportedFeatures = remoteSupportedFeatures;
        if ((BluetoothMapUtils.MAP_FEATURE_EXTENDED_EVENT_REPORT_11_BIT
                & mMapSupportedFeatures) != 0) {
            mMapEventReportVersion = BluetoothMapUtils.MAP_EVENT_REPORT_V11;
        }
        // Make sure support for all formats result in latest version returned
        if ((BluetoothMapUtils.MAP_FEATURE_EVENT_REPORT_V12_BIT
                & mMapSupportedFeatures) != 0) {
            mMapEventReportVersion = BluetoothMapUtils.MAP_EVENT_REPORT_V12;
        }
        if (V) Log.d(TAG, "setObserverRemoteFeatureMask : " + mMapEventReportVersion
            + " mMapSupportedFeatures : " + mMapSupportedFeatures);
    }

    private Map<Long, Msg> getMsgListSms() {
        return mMsgListSms;
    }

    private void setMsgListSms(Map<Long, Msg> msgListSms, boolean changesDetected) {
        mMsgListSms = msgListSms;
        if(changesDetected) {
            mMasInstance.updateFolderVersionCounter();
        }
        mMasInstance.setMsgListSms(msgListSms);
    }


    private Map<Long, Msg> getMsgListMms() {
        return mMsgListMms;
    }


    private void setMsgListMms(Map<Long, Msg> msgListMms, boolean changesDetected) {
        mMsgListMms = msgListMms;
        if(changesDetected) {
            mMasInstance.updateFolderVersionCounter();
        }
        mMasInstance.setMsgListMms(msgListMms);
    }


    private Map<Long, Msg> getMsgListMsg() {
        return mMsgListMsg;
    }


    private void setMsgListMsg(Map<Long, Msg> msgListMsg, boolean changesDetected) {
        mMsgListMsg = msgListMsg;
        if(changesDetected) {
            mMasInstance.updateFolderVersionCounter();
        }
        mMasInstance.setMsgListMsg(msgListMsg);
    }

    private Map<String, BluetoothMapConvoContactElement> getContactList() {
        return mContactList;
    }


    /**
     * Currently we only have data for IM / email contacts
     * @param contactList
     * @param changesDetected that is not chat state changed nor presence state changed.
     */
    private void setContactList(Map<String, BluetoothMapConvoContactElement> contactList,
            boolean changesDetected) {
        mContactList = contactList;
        if(changesDetected) {
            mMasInstance.updateImEmailConvoListVersionCounter();
        }
        mMasInstance.setContactList(contactList);
    }

    private static boolean sendEventNewMessage(long eventFilter) {
        return ((eventFilter & EVENT_FILTER_NEW_MESSAGE) > 0);
    }

    private static boolean sendEventMessageDeleted(long eventFilter) {
        return ((eventFilter & EVENT_FILTER_MESSAGE_DELETED) > 0);
    }

    private static boolean sendEventMessageShift(long eventFilter) {
        return ((eventFilter & EVENT_FILTER_MESSAGE_SHIFT) > 0);
    }

    private static boolean sendEventSendingSuccess(long eventFilter) {
        return ((eventFilter & EVENT_FILTER_SENDING_SUCCESS) > 0);
    }

    private static boolean sendEventSendingFailed(long eventFilter) {
        return ((eventFilter & EVENT_FILTER_SENDING_FAILED) > 0);
    }

    private static boolean sendEventDeliverySuccess(long eventFilter) {
        return ((eventFilter & EVENT_FILTER_DELIVERY_SUCCESS) > 0);
    }

    private static boolean sendEventDeliveryFailed(long eventFilter) {
        return ((eventFilter & EVENT_FILTER_DELIVERY_FAILED) > 0);
    }

    private static boolean sendEventReadStatusChanged(long eventFilter) {
        return ((eventFilter & EVENT_FILTER_READ_STATUS_CHANGED) > 0);
    }

    private static boolean sendEventConversationChanged(long eventFilter) {
        return ((eventFilter & EVENT_FILTER_CONVERSATION_CHANGED) > 0);
    }

    private static boolean sendEventParticipantPresenceChanged(long eventFilter) {
        return ((eventFilter & EVENT_FILTER_PARTICIPANT_PRESENCE_CHANGED) > 0);
    }

    private static boolean sendEventParticipantChatstateChanged(long eventFilter) {
        return ((eventFilter & EVENT_FILTER_PARTICIPANT_CHATSTATE_CHANGED) > 0);
    }

    private static boolean sendEventMessageRemoved(long eventFilter) {
        return ((eventFilter & EVENT_FILTER_MESSAGE_REMOVED) > 0);
    }

    private TYPE getSmsType() {
        TYPE smsType = null;
        TelephonyManager tm = (TelephonyManager) mContext.getSystemService(
                Context.TELEPHONY_SERVICE);

        if (tm.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
            smsType = TYPE.SMS_CDMA;
        } else {
            smsType = TYPE.SMS_GSM;
        }

        return smsType;
    }

    private final ContentObserver mObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            onChange(selfChange, null);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if(uri == null) {
                Log.w(TAG, "onChange() with URI == null - not handled.");
                return;
            }

            if (!mStorageUnlocked) {
                Log.v(TAG, "Ignore events until storage is completely unlocked");
                return;
            }

            if (V) Log.d(TAG, "onChange on thread: " + Thread.currentThread().getId()
                    + " Uri: " + uri.toString() + " selfchange: " + selfChange);

            if(uri.toString().contains(BluetoothMapContract.TABLE_CONVOCONTACT))
                handleContactListChanges(uri);
            else
                handleMsgListChanges(uri);
        }
    };

    private static final HashMap<Integer, String> FOLDER_SMS_MAP;
    static {
        FOLDER_SMS_MAP = new HashMap<Integer, String>();
        FOLDER_SMS_MAP.put(Sms.MESSAGE_TYPE_INBOX,  BluetoothMapContract.FOLDER_NAME_INBOX);
        FOLDER_SMS_MAP.put(Sms.MESSAGE_TYPE_SENT,  BluetoothMapContract.FOLDER_NAME_SENT);
        FOLDER_SMS_MAP.put(Sms.MESSAGE_TYPE_DRAFT,  BluetoothMapContract.FOLDER_NAME_DRAFT);
        FOLDER_SMS_MAP.put(Sms.MESSAGE_TYPE_OUTBOX,  BluetoothMapContract.FOLDER_NAME_OUTBOX);
        FOLDER_SMS_MAP.put(Sms.MESSAGE_TYPE_FAILED,  BluetoothMapContract.FOLDER_NAME_OUTBOX);
        FOLDER_SMS_MAP.put(Sms.MESSAGE_TYPE_QUEUED,  BluetoothMapContract.FOLDER_NAME_OUTBOX);
    }

    private static String getSmsFolderName(int type) {
        String name = FOLDER_SMS_MAP.get(type);
        if(name != null) {
            return name;
        }
        Log.e(TAG, "New SMS mailbox types have been introduced, without an update in BT...");
        return "Unknown";
    }


    private static final HashMap<Integer, String> FOLDER_MMS_MAP;
    static {
        FOLDER_MMS_MAP = new HashMap<Integer, String>();
        FOLDER_MMS_MAP.put(Mms.MESSAGE_BOX_INBOX,  BluetoothMapContract.FOLDER_NAME_INBOX);
        FOLDER_MMS_MAP.put(Mms.MESSAGE_BOX_SENT,   BluetoothMapContract.FOLDER_NAME_SENT);
        FOLDER_MMS_MAP.put(Mms.MESSAGE_BOX_DRAFTS, BluetoothMapContract.FOLDER_NAME_DRAFT);
        FOLDER_MMS_MAP.put(Mms.MESSAGE_BOX_OUTBOX, BluetoothMapContract.FOLDER_NAME_OUTBOX);
    }

    private static String getMmsFolderName(int mailbox) {
        String name = FOLDER_MMS_MAP.get(mailbox);
        if(name != null) {
            return name;
        }
        Log.e(TAG, "New MMS mailboxes have been introduced, without an update in BT...");
        return "Unknown";
    }

    /**
     * Set the folder structure to be used for this instance.
     * @param folderStructure
     */
    public void setFolderStructure(BluetoothMapFolderElement folderStructure) {
        this.mFolders = folderStructure;
    }

    private class ConvoContactInfo {
        public int mConvoColConvoId         = -1;
        public int mConvoColLastActivity    = -1;
        public int mConvoColName            = -1;
        //        public int mConvoColRead            = -1;
        //        public int mConvoColVersionCounter  = -1;
        public int mContactColUci           = -1;
        public int mContactColConvoId       = -1;
        public int mContactColName          = -1;
        public int mContactColNickname      = -1;
        public int mContactColBtUid         = -1;
        public int mContactColChatState     = -1;
        public int mContactColContactId     = -1;
        public int mContactColLastActive    = -1;
        public int mContactColPresenceState = -1;
        public int mContactColPresenceText  = -1;
        public int mContactColPriority      = -1;
        public int mContactColLastOnline    = -1;

        public void setConvoColunms(Cursor c) {
            //            mConvoColConvoId         = c.getColumnIndex(
            //                    BluetoothMapContract.ConversationColumns.THREAD_ID);
            //            mConvoColLastActivity    = c.getColumnIndex(
            //                    BluetoothMapContract.ConversationColumns.LAST_THREAD_ACTIVITY);
            //            mConvoColName            = c.getColumnIndex(
            //                    BluetoothMapContract.ConversationColumns.THREAD_NAME);
            mContactColConvoId       = c.getColumnIndex(
                    BluetoothMapContract.ConvoContactColumns.CONVO_ID);
            mContactColName          = c.getColumnIndex(
                    BluetoothMapContract.ConvoContactColumns.NAME);
            mContactColNickname      = c.getColumnIndex(
                    BluetoothMapContract.ConvoContactColumns.NICKNAME);
            mContactColBtUid         = c.getColumnIndex(
                    BluetoothMapContract.ConvoContactColumns.X_BT_UID);
            mContactColChatState     = c.getColumnIndex(
                    BluetoothMapContract.ConvoContactColumns.CHAT_STATE);
            mContactColUci           = c.getColumnIndex(
                    BluetoothMapContract.ConvoContactColumns.UCI);
            mContactColNickname      = c.getColumnIndex(
                    BluetoothMapContract.ConvoContactColumns.NICKNAME);
            mContactColLastActive    = c.getColumnIndex(
                    BluetoothMapContract.ConvoContactColumns.LAST_ACTIVE);
            mContactColName          = c.getColumnIndex(
                    BluetoothMapContract.ConvoContactColumns.NAME);
            mContactColPresenceState = c.getColumnIndex(
                    BluetoothMapContract.ConvoContactColumns.PRESENCE_STATE);
            mContactColPresenceText  = c.getColumnIndex(
                    BluetoothMapContract.ConvoContactColumns.STATUS_TEXT);
            mContactColPriority      = c.getColumnIndex(
                    BluetoothMapContract.ConvoContactColumns.PRIORITY);
            mContactColLastOnline    = c.getColumnIndex(
                    BluetoothMapContract.ConvoContactColumns.LAST_ONLINE);
        }
    }

    private class Event {
        String eventType;
        long handle;
        String folder = null;
        String oldFolder = null;
        TYPE msgType;
        /* Extended event parameters in MAP Event version 1.1 */
        String datetime = null; // OBEX time "YYYYMMDDTHHMMSS"
        String uci = null;
        String subject = null;
        String senderName = null;
        String priority = null;
        /* Event parameters in MAP Event version 1.2 */
        String conversationName = null;
        long conversationID = -1;
        int presenceState = BluetoothMapContract.PresenceState.UNKNOWN;
        String presenceStatus = null;
        int chatState = BluetoothMapContract.ChatState.UNKNOWN;

        final static String PATH = "telecom/msg/";

        private void setFolderPath(String name, TYPE type) {
            if (name != null) {
                if(type == TYPE.EMAIL || type == TYPE.IM) {
                    this.folder = name;
                } else {
                    this.folder = PATH + name;
                }
            } else {
                this.folder = null;
            }
        }

        public Event(String eventType, long handle, String folder,
                String oldFolder, TYPE msgType) {
            this.eventType = eventType;
            this.handle = handle;
            setFolderPath(folder, msgType);
            if (oldFolder != null) {
                if(msgType == TYPE.EMAIL || msgType == TYPE.IM) {
                    this.oldFolder = oldFolder;
                } else {
                    this.oldFolder = PATH + oldFolder;
                }
            } else {
                this.oldFolder = null;
            }
            this.msgType = msgType;
        }

        public Event(String eventType, long handle, String folder, TYPE msgType) {
            this.eventType = eventType;
            this.handle = handle;
            setFolderPath(folder, msgType);
            this.msgType = msgType;
        }

        /* extended event type 1.1 */
        public Event(String eventType, long handle, String folder, TYPE msgType,
                String datetime, String subject, String senderName, String priority) {
            this.eventType = eventType;
            this.handle = handle;
            setFolderPath(folder, msgType);
            this.msgType = msgType;
            this.datetime = datetime;
            if (subject != null) {
                this.subject = BluetoothMapUtils.stripInvalidChars(subject);
            }
            if (senderName != null) {
                this.senderName = BluetoothMapUtils.stripInvalidChars(senderName);
            }
            this.priority = priority;
        }

        /* extended event type 1.2 message events */
        public Event(String eventType, long handle, String folder, TYPE msgType,
                String datetime, String subject, String senderName, String priority,
                long conversationID, String conversationName) {
            this.eventType = eventType;
            this.handle = handle;
            setFolderPath(folder, msgType);
            this.msgType = msgType;
            this.datetime = datetime;
            if (subject != null) {
                this.subject = BluetoothMapUtils.stripInvalidChars(subject);
            }
            if (senderName != null) {
                this.senderName = BluetoothMapUtils.stripInvalidChars(senderName);
            }
            if (conversationID != 0) {
                this.conversationID = conversationID;
            }
            if (conversationName != null) {
                this.conversationName = BluetoothMapUtils.stripInvalidChars(conversationName);
            }
            this.priority = priority;
        }

        /* extended event type 1.2 for conversation, presence or chat state changed events */
        public Event(String eventType, String uci, TYPE msgType, String name, String priority,
                String lastActivity, long conversationID, String conversationName,
                int presenceState, String presenceStatus, int chatState) {
            this.eventType = eventType;
            this.uci = uci;
            this.msgType = msgType;
            if (name != null) {
                this.senderName = BluetoothMapUtils.stripInvalidChars(name);
            }
            this.priority = priority;
            this.datetime = lastActivity;
            if (conversationID != 0) {
                this.conversationID = conversationID;
            }
            if (conversationName != null) {
                this.conversationName = BluetoothMapUtils.stripInvalidChars(conversationName);
            }
            if (presenceState != BluetoothMapContract.PresenceState.UNKNOWN) {
                this.presenceState = presenceState;
            }
            if (presenceStatus != null) {
                this.presenceStatus = BluetoothMapUtils.stripInvalidChars(presenceStatus);
            }
            if (chatState != BluetoothMapContract.ChatState.UNKNOWN) {
                this.chatState = chatState;
            }
        }

        public byte[] encode() throws UnsupportedEncodingException {
            StringWriter sw = new StringWriter();
            XmlSerializer xmlEvtReport = Xml.newSerializer();

            try {
                xmlEvtReport.setOutput(sw);
                xmlEvtReport.startDocument("UTF-8", true);
                xmlEvtReport.text("\r\n");
                xmlEvtReport.startTag("", "MAP-event-report");
                if (mMapEventReportVersion == BluetoothMapUtils.MAP_EVENT_REPORT_V10) {
                    xmlEvtReport.attribute("", "version", BluetoothMapUtils.MAP_V10_STR);
                } else if (mMapEventReportVersion == BluetoothMapUtils.MAP_EVENT_REPORT_V11) {
                    xmlEvtReport.attribute("", "version", BluetoothMapUtils.MAP_V11_STR);
                } else {
                    xmlEvtReport.attribute("", "version", BluetoothMapUtils.MAP_V12_STR);
                }
                xmlEvtReport.startTag("", "event");
                xmlEvtReport.attribute("", "type", eventType);
                if (eventType.equals(EVENT_TYPE_CONVERSATION) ||
                        eventType.equals(EVENT_TYPE_PRESENCE) ||
                        eventType.equals(EVENT_TYPE_CHAT_STATE)) {
                    xmlEvtReport.attribute("", "participant_uci", uci);
                } else {
                    xmlEvtReport.attribute("", "handle",
                            BluetoothMapUtils.getMapHandle(handle, msgType));
                }

                if (folder != null) {
                    xmlEvtReport.attribute("", "folder", folder);
                }
                if (oldFolder != null) {
                    xmlEvtReport.attribute("", "old_folder", oldFolder);
                }
                /* Avoid possible NPE for "msgType" "null" value. "msgType"
                 * is a implied attribute and will be set "null" for events
                 * like "memory full" or "memory available" */
                if (msgType != null) {
                    xmlEvtReport.attribute("", "msg_type", msgType.name());
                }
                /* If MAP event report version is above 1.0 send
                 * extended event report parameters */
                if (datetime != null) {
                    xmlEvtReport.attribute("", "datetime", datetime);
                }
                if (subject != null) {
                    xmlEvtReport.attribute("", "subject",
                            subject.substring(0,subject.length() < 256 ? subject.length() : 256));
                }
                if (senderName != null) {
                    xmlEvtReport.attribute("", "sender_name", senderName);
                }
                if (priority != null) {
                    xmlEvtReport.attribute("", "priority", priority);
                }

                //}
                /* Include conversation information from event version 1.2 */
                if (mMapEventReportVersion > BluetoothMapUtils.MAP_EVENT_REPORT_V11 ) {
                    if (conversationName != null) {
                        xmlEvtReport.attribute("", "conversation_name", conversationName);
                    }
                    if (conversationID != -1) {
                        // Convert provider conversation handle to string incl type
                        xmlEvtReport.attribute("", "conversation_id",
                                BluetoothMapUtils.getMapConvoHandle(conversationID, msgType));
                    }
                    if (eventType.equals(EVENT_TYPE_PRESENCE)) {
                        if (presenceState != 0) {
                            // Convert provider conversation handle to string incl type
                            xmlEvtReport.attribute("", "presence_availability",
                                    String.valueOf(presenceState));
                        }
                        if (presenceStatus != null) {
                            // Convert provider conversation handle to string incl type
                            xmlEvtReport.attribute("", "presence_status",
                                    presenceStatus.substring(
                                            0,presenceStatus.length() < 256 ? subject.length() : 256));
                        }
                    }
                    if (eventType.equals(EVENT_TYPE_PRESENCE)) {
                        if (chatState != 0) {
                            // Convert provider conversation handle to string incl type
                            xmlEvtReport.attribute("", "chat_state", String.valueOf(chatState));
                        }
                    }

                }
                xmlEvtReport.endTag("", "event");
                xmlEvtReport.endTag("", "MAP-event-report");
                xmlEvtReport.endDocument();
            } catch (IllegalArgumentException e) {
                if(D) Log.w(TAG,e);
            } catch (IllegalStateException e) {
                if(D) Log.w(TAG,e);
            } catch (IOException e) {
                if(D) Log.w(TAG,e);
            }

            if (V) Log.d(TAG, sw.toString());

            return sw.toString().getBytes("UTF-8");
        }
    }

    /*package*/ class Msg {
        long id;
        int type;               // Used as folder for SMS/MMS
        int threadId;           // Used for SMS/MMS at delete
        long folderId = -1;     // Email folder ID
        long oldFolderId = -1;  // Used for email undelete
        boolean localInitiatedSend = false; // Used for MMS to filter out events
        boolean transparent = false; // Used for EMAIL to delete message sent with transparency
        int flagRead = -1;      // Message status read/unread

        public Msg(long id, int type, int threadId, int readFlag) {
            this.id = id;
            this.type = type;
            this.threadId = threadId;
            this.flagRead = readFlag;
        }
        public Msg(long id, long folderId, int readFlag) {
            this.id = id;
            this.folderId = folderId;
            this.flagRead = readFlag;
        }

        /* Eclipse generated hashCode() and equals() to make
         * hashMap lookup work independent of whether the obj
         * is used for email or SMS/MMS and whether or not the
         * oldFolder is set. */
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + (int) (id ^ (id >>> 32));
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Msg other = (Msg) obj;
            if (id != other.id)
                return false;
            return true;
        }
    }

    private Map<Long, Msg> mMsgListSms = null;

    private Map<Long, Msg> mMsgListMms = null;

    private Map<Long, Msg> mMsgListMsg = null;

    private Map<String, BluetoothMapConvoContactElement> mContactList = null;

    public int setNotificationRegistration(int notificationStatus) throws RemoteException {
        // Forward the request to the MNS thread as a message - including the MAS instance ID.
        if(D) Log.d(TAG,"setNotificationRegistration() enter");
        if (mMnsClient == null) {
            return ResponseCodes.OBEX_HTTP_UNAVAILABLE;
        }
        Handler mns = mMnsClient.getMessageHandler();
        if (mns != null) {
            Message msg = mns.obtainMessage();
            if (mMnsClient.isValidMnsRecord()) {
                msg.what = BluetoothMnsObexClient.MSG_MNS_NOTIFICATION_REGISTRATION;
            } else {
                //Trigger SDP Search and notificaiton registration , if SDP record not found.
                msg.what = BluetoothMnsObexClient.MSG_MNS_SDP_SEARCH_REGISTRATION;
                if (mMnsClient.mMnsLstRegRqst != null &&
                        (mMnsClient.mMnsLstRegRqst.isSearchInProgress())) {
                    /*  1. Disallow next Notification ON Request :
                     *     - Respond "Service Unavailable" as SDP Search and last notification
                     *       registration ON request is already InProgress.
                     *     - Next notification ON Request will be allowed ONLY after search
                     *       and connect for last saved request [Replied with OK ] is processed.
                     */
                    if (notificationStatus == BluetoothMapAppParams.NOTIFICATION_STATUS_YES) {
                        return ResponseCodes.OBEX_HTTP_UNAVAILABLE;
                    } else {
                        /*  2. Allow next Notification OFF Request:
                         *    - Keep the SDP search still in progress.
                         *    - Disconnect and Deregister the contentObserver.
                         */
                        msg.what = BluetoothMnsObexClient.MSG_MNS_NOTIFICATION_REGISTRATION;
                    }
                }
            }
            msg.arg1 = mMasId;
            msg.arg2 = notificationStatus;
            mns.sendMessageDelayed(msg, 10); // Send message without forcing a context switch
            /* Some devices - e.g. PTS needs to get the unregister confirm before we actually
             * disconnect the MNS. */
            if(D) Log.d(TAG,"setNotificationRegistration() send : " + msg.what + " to MNS ");
            return ResponseCodes.OBEX_HTTP_OK;
        } else {
            // This should not happen except at shutdown.
            if(D) Log.d(TAG,"setNotificationRegistration() Unable to send registration request");
            return ResponseCodes.OBEX_HTTP_UNAVAILABLE;
        }
    }

    boolean eventMaskContainsContacts(long mask) {
        return sendEventParticipantPresenceChanged(mask);
    }

    boolean eventMaskContainsCovo(long mask) {
        return (sendEventConversationChanged(mask)
                || sendEventParticipantChatstateChanged(mask));
    }

    /* Overwrite the existing notification filter. Will register/deregister observers for
     * the Contacts and Conversation table as needed. We keep the message observer
     * at all times. */
    /*package*/ synchronized void setNotificationFilter(long newFilter) {
        long oldFilter = mEventFilter;
        mEventFilter = newFilter;
        /* Contacts */
        if(!eventMaskContainsContacts(oldFilter) &&
                eventMaskContainsContacts(newFilter)) {
            // TODO:
            // Enable the observer
            // Reset the contacts list
        }
        /* Conversations */
        if(!eventMaskContainsCovo(oldFilter) &&
                eventMaskContainsCovo(newFilter)) {
            // TODO:
            // Enable the observer
            // Reset the conversations list
        }
    }

    public void registerObserver() throws RemoteException{
        if (V) Log.d(TAG, "registerObserver");

        if (mObserverRegistered)
            return;

        if(mAccount != null) {

            mProviderClient = mResolver.acquireUnstableContentProviderClient(mAuthority);
            if (mProviderClient == null) {
                throw new RemoteException("Failed to acquire provider for " + mAuthority);
            }
            mProviderClient.setDetectNotResponding(PROVIDER_ANR_TIMEOUT);

            // If there is a change in the database before we init the lists we will be sending
            // loads of events - hence init before register.
            if(mAccount.getType() == TYPE.IM) {
                // Further add contact list tracking
                initContactsList();
            }
        }
        // If there is a change in the database before we init the lists we will be sending
        // loads of events - hence init before register.
        initMsgList();

        /* Use MmsSms Uri since the Sms Uri is not notified on deletes */
        if(mEnableSmsMms){
            //this is sms/mms
            mResolver.registerContentObserver(MmsSms.CONTENT_URI, false, mObserver);
            mObserverRegistered = true;
        }

        if(mAccount != null) {
            /* For URI's without account ID */
            Uri uri = Uri.parse(mAccount.mBase_uri_no_account + "/"
                    + BluetoothMapContract.TABLE_MESSAGE);
            if(D) Log.d(TAG, "Registering observer for: " + uri);
            mResolver.registerContentObserver(uri, true, mObserver);

            /* For URI's with account ID - is handled the same way as without ID, but is
             * only triggered for MAS instances with matching account ID. */
            uri = Uri.parse(mAccount.mBase_uri + "/" + BluetoothMapContract.TABLE_MESSAGE);
            if(D) Log.d(TAG, "Registering observer for: " + uri);
            mResolver.registerContentObserver(uri, true, mObserver);

            if(mAccount.getType() == TYPE.IM) {

                uri = Uri.parse(mAccount.mBase_uri_no_account + "/"
                        + BluetoothMapContract.TABLE_CONVOCONTACT);
                if(D) Log.d(TAG, "Registering observer for: " + uri);
                mResolver.registerContentObserver(uri, true, mObserver);

                /* For URI's with account ID - is handled the same way as without ID, but is
                 * only triggered for MAS instances with matching account ID. */
                uri = Uri.parse(mAccount.mBase_uri + "/" + BluetoothMapContract.TABLE_CONVOCONTACT);
                if(D) Log.d(TAG, "Registering observer for: " + uri);
                mResolver.registerContentObserver(uri, true, mObserver);
            }

            mObserverRegistered = true;
        }
    }

    public void unregisterObserver() {
        if (V) Log.d(TAG, "unregisterObserver");
        mResolver.unregisterContentObserver(mObserver);
        mObserverRegistered = false;
        if(mProviderClient != null){
            mProviderClient.release();
            mProviderClient = null;
        }
    }

    /**
     * Per design it is only possible to call the refreshXxxx functions sequentially, hence it
     * is safe to modify mTransmitEvents without synchronization.
     */
    /* package */ void refreshFolderVersionCounter() {
        if (mObserverRegistered) {
            // As we have observers, we already keep the counter up-to-date.
            return;
        }
        /* We need to perform the same functionality, as when we receive a notification change,
           hence we:
            - disable the event transmission
            - triggers the code for updates
            - enable the event transmission */
        mTransmitEvents = false;
        try {
            if(mEnableSmsMms) {
                handleMsgListChangesSms();
                handleMsgListChangesMms();
            }
            if(mAccount != null) {
                try {
                    handleMsgListChangesMsg(mMessageUri);
                } catch (RemoteException e) {
                    Log.e(TAG, "Unable to update FolderVersionCounter. - Not fatal, but can cause" +
                            " undesirable user experience!", e);
                }
            }
        } finally {
            // Ensure we always enable events again
            mTransmitEvents = true;
        }
    }

    /* package */ void refreshConvoListVersionCounter() {
        if (mObserverRegistered) {
            // As we have observers, we already keep the counter up-to-date.
            return;
        }
        /* We need to perform the same functionality, as when we receive a notification change,
        hence we:
         - disable event transmission
         - triggers the code for updates
         - enable event transmission */
        mTransmitEvents = false;
        try {
            if((mAccount != null) && (mContactUri != null)) {
                handleContactListChanges(mContactUri);
            }
        } finally {
            // Ensure we always enable events again
            mTransmitEvents = true;
        }
    }

    private void sendEvent(Event evt) {

        if(mTransmitEvents == false) {
            if(V) Log.v(TAG, "mTransmitEvents == false - don't send event.");
            return;
        }

        if(D)Log.d(TAG, "sendEvent: " + evt.eventType + " " + evt.handle + " " + evt.folder + " "
                + evt.oldFolder + " " + evt.msgType.name() + " " + evt.datetime + " "
                + evt.subject + " " + evt.senderName + " " + evt.priority );

        if (mMnsClient == null || mMnsClient.isConnected() == false) {
            Log.d(TAG, "sendEvent: No MNS client registered or connected- don't send event");
            return;
        }

        /* Enable use of the cache for checking the filter */
        long eventFilter = mEventFilter;

        /* This should have been a switch on the string, but it is not allowed in Java 1.6 */
        /* WARNING: Here we do pointer compare for the string to speed up things, that is.
         * HENCE: always use the EVENT_TYPE_"defines" */
        if(evt.eventType == EVENT_TYPE_NEW) {
            if(!sendEventNewMessage(eventFilter)) {
                if(D)Log.d(TAG, "Skip sending event of type: " + evt.eventType);
                return;
            }
        } else if(evt.eventType == EVENT_TYPE_DELETE) {
            if(!sendEventMessageDeleted(eventFilter)) {
                if(D)Log.d(TAG, "Skip sending event of type: " + evt.eventType);
                return;
            }
        } else if(evt.eventType == EVENT_TYPE_REMOVED) {
            if(!sendEventMessageRemoved(eventFilter)) {
                if(D)Log.d(TAG, "Skip sending event of type: " + evt.eventType);
                return;
            }
        } else if(evt.eventType == EVENT_TYPE_SHIFT) {
            if(!sendEventMessageShift(eventFilter)) {
                if(D)Log.d(TAG, "Skip sending event of type: " + evt.eventType);
                return;
            }
        } else if(evt.eventType == EVENT_TYPE_DELEVERY_SUCCESS) {
            if(!sendEventDeliverySuccess(eventFilter)) {
                if(D)Log.d(TAG, "Skip sending event of type: " + evt.eventType);
                return;
            }
        } else if(evt.eventType == EVENT_TYPE_SENDING_SUCCESS) {
            if(!sendEventSendingSuccess(eventFilter)) {
                if(D)Log.d(TAG, "Skip sending event of type: " + evt.eventType);
                return;
            }
        } else if(evt.eventType == EVENT_TYPE_SENDING_FAILURE) {
            if(!sendEventSendingFailed(eventFilter)) {
                if(D)Log.d(TAG, "Skip sending event of type: " + evt.eventType);
                return;
            }
        } else if(evt.eventType == EVENT_TYPE_DELIVERY_FAILURE) {
            if(!sendEventDeliveryFailed(eventFilter)) {
                if(D)Log.d(TAG, "Skip sending event of type: " + evt.eventType);
                return;
            }
        } else if(evt.eventType == EVENT_TYPE_READ_STATUS) {
            if(!sendEventReadStatusChanged(eventFilter)) {
                if(D)Log.d(TAG, "Skip sending event of type: " + evt.eventType);
                return;
            }
        } else if(evt.eventType == EVENT_TYPE_CONVERSATION) {
            if(!sendEventConversationChanged(eventFilter)) {
                if(D)Log.d(TAG, "Skip sending event of type: " + evt.eventType);
                return;
            }
        } else if(evt.eventType == EVENT_TYPE_PRESENCE) {
            if(!sendEventParticipantPresenceChanged(eventFilter)) {
                if(D)Log.d(TAG, "Skip sending event of type: " + evt.eventType);
                return;
            }
        } else if(evt.eventType == EVENT_TYPE_CHAT_STATE) {
            if(!sendEventParticipantChatstateChanged(eventFilter)) {
                if(D)Log.d(TAG, "Skip sending event of type: " + evt.eventType);
                return;
            }
        }

        try {
            mMnsClient.sendEvent(evt.encode(), mMasId);
        } catch (UnsupportedEncodingException ex) {
            /* do nothing */
            if (D) Log.e(TAG, "Exception - should not happen: ",ex);
        }
    }

    private void initMsgList() throws RemoteException {
        if (V) Log.d(TAG, "initMsgList");
        UserManager manager = UserManager.get(mContext);
        if (manager == null || !manager.isUserUnlocked()) return;

        if (mEnableSmsMms) {
            HashMap<Long, Msg> msgListSms = new HashMap<Long, Msg>();

            Cursor c;
            try {
                c = mResolver.query(Sms.CONTENT_URI,
                    SMS_PROJECTION_SHORT, null, null, null);
            } catch (SQLiteException e) {
                Log.e(TAG, "Failed to initialize the list of messages: " + e.toString());
                return;
            }

            try {
                if (c != null && c.moveToFirst()) {
                    do {
                        long id = c.getLong(c.getColumnIndex(Sms._ID));
                        int type = c.getInt(c.getColumnIndex(Sms.TYPE));
                        int threadId = c.getInt(c.getColumnIndex(Sms.THREAD_ID));
                        int read = c.getInt(c.getColumnIndex(Sms.READ));

                        Msg msg = new Msg(id, type, threadId, read);
                        msgListSms.put(id, msg);
                    } while (c.moveToNext());
                }
            } finally {
                if (c != null) c.close();
            }

            synchronized(getMsgListSms()) {
                getMsgListSms().clear();
                setMsgListSms(msgListSms, true); // Set initial folder version counter
            }

            HashMap<Long, Msg> msgListMms = new HashMap<Long, Msg>();

            c = mResolver.query(Mms.CONTENT_URI, MMS_PROJECTION_SHORT, null, null, null);
            try {
                if (c != null && c.moveToFirst()) {
                    do {
                        long id = c.getLong(c.getColumnIndex(Mms._ID));
                        int type = c.getInt(c.getColumnIndex(Mms.MESSAGE_BOX));
                        int threadId = c.getInt(c.getColumnIndex(Mms.THREAD_ID));
                        int read = c.getInt(c.getColumnIndex(Mms.READ));

                        Msg msg = new Msg(id, type, threadId, read);
                        msgListMms.put(id, msg);
                    } while (c.moveToNext());
                }
            } finally {
                if (c != null) c.close();
            }

            synchronized(getMsgListMms()) {
                getMsgListMms().clear();
                setMsgListMms(msgListMms, true); // Set initial folder version counter
            }
        }

        if(mAccount != null) {
            HashMap<Long, Msg> msgList = new HashMap<Long, Msg>();
            Uri uri = mMessageUri;
            Cursor c = mProviderClient.query(uri, MSG_PROJECTION_SHORT, null, null, null);

            try {
                if (c != null && c.moveToFirst()) {
                    do {
                        long id = c.getLong(c.getColumnIndex(MessageColumns._ID));
                        long folderId = c.getInt(c.getColumnIndex(
                                BluetoothMapContract.MessageColumns.FOLDER_ID));
                        int readFlag = c.getInt(c.getColumnIndex(
                                BluetoothMapContract.MessageColumns.FLAG_READ));
                        Msg msg = new Msg(id, folderId, readFlag);
                        msgList.put(id, msg);
                    } while (c.moveToNext());
                }
            } finally {
                if (c != null) c.close();
            }

            synchronized(getMsgListMsg()) {
                getMsgListMsg().clear();
                setMsgListMsg(msgList, true);
            }
        }
    }

    private void initContactsList() throws RemoteException {
        if (V) Log.d(TAG, "initContactsList");
        if(mContactUri == null) {
            if (D) Log.d(TAG, "initContactsList() no mContactUri - nothing to init");
            return;
        }
        Uri uri = mContactUri;
        Cursor c = mProviderClient.query(uri,
                BluetoothMapContract.BT_CONTACT_CHATSTATE_PRESENCE_PROJECTION,
                null, null, null);
        Map<String, BluetoothMapConvoContactElement> contactList =
                new HashMap<String, BluetoothMapConvoContactElement>();
        try {
            if (c != null && c.moveToFirst()) {
                ConvoContactInfo cInfo = new ConvoContactInfo();
                cInfo.setConvoColunms(c);
                do {
                    long convoId = c.getLong(cInfo.mContactColConvoId);
                    if (convoId == 0)
                        continue;
                    if (V) BluetoothMapUtils.printCursor(c);
                    String uci = c.getString(cInfo.mContactColUci);
                    String name = c.getString(cInfo.mContactColName);
                    String displayName = c.getString(cInfo.mContactColNickname);
                    String presenceStatus = c.getString(cInfo.mContactColPresenceText);
                    int presenceState = c.getInt(cInfo.mContactColPresenceState);
                    long lastActivity = c.getLong(cInfo.mContactColLastActive);
                    int chatState = c.getInt(cInfo.mContactColChatState);
                    int priority = c.getInt(cInfo.mContactColPriority);
                    String btUid = c.getString(cInfo.mContactColBtUid);
                    BluetoothMapConvoContactElement contact =
                            new BluetoothMapConvoContactElement(uci, name, displayName,
                                    presenceStatus, presenceState, lastActivity, chatState,
                                    priority, btUid);
                    contactList.put(uci, contact);
                } while (c.moveToNext());
            }
        } finally {
            if (c != null) c.close();
        }
        synchronized(getContactList()) {
            getContactList().clear();
            setContactList(contactList, true);
        }
    }

    private void handleMsgListChangesSms() {
        if (V) Log.d(TAG, "handleMsgListChangesSms");

        HashMap<Long, Msg> msgListSms = new HashMap<Long, Msg>();
        boolean listChanged = false;

        Cursor c;
        synchronized(getMsgListSms()) {
            if (mMapEventReportVersion == BluetoothMapUtils.MAP_EVENT_REPORT_V10) {
                c = mResolver.query(Sms.CONTENT_URI,
                        SMS_PROJECTION_SHORT, null, null, null);
            } else {
                c = mResolver.query(Sms.CONTENT_URI,
                        SMS_PROJECTION_SHORT_EXT, null, null, null);
            }
            try {
                if (c != null && c.moveToFirst()) {
                    do {
                        long id = c.getLong(c.getColumnIndex(Sms._ID));
                        int type = c.getInt(c.getColumnIndex(Sms.TYPE));
                        int threadId = c.getInt(c.getColumnIndex(Sms.THREAD_ID));
                        int read = c.getInt(c.getColumnIndex(Sms.READ));

                        Msg msg = getMsgListSms().remove(id);

                        /* We must filter out any actions made by the MCE, hence do not send e.g.
                         * a message deleted and/or MessageShift for messages deleted by the MCE. */

                        if (msg == null) {
                            /* New message */
                            msg = new Msg(id, type, threadId, read);
                            msgListSms.put(id, msg);
                            listChanged = true;
                            Event evt;
                            if (mTransmitEvents == true && // extract contact details only if needed
                                    mMapEventReportVersion >
                            BluetoothMapUtils.MAP_EVENT_REPORT_V10) {
                                String date = BluetoothMapUtils.getDateTimeString(
                                        c.getLong(c.getColumnIndex(Sms.DATE)));
                                String subject = c.getString(c.getColumnIndex(Sms.BODY));
                                String name = "";
                                String phone = "";
                                if (type == 1) { //inbox
                                    phone = c.getString(c.getColumnIndex(Sms.ADDRESS));
                                    if (phone != null && !phone.isEmpty()) {
                                        name = BluetoothMapContent.getContactNameFromPhone(phone,
                                                mResolver);
                                        if(name == null || name.isEmpty()){
                                            name = phone;
                                        }
                                    }else{
                                        name = phone;
                                    }
                                } else {
                                    TelephonyManager tm =
                                            (TelephonyManager)mContext.getSystemService(
                                            Context.TELEPHONY_SERVICE);
                                    if (tm != null) {
                                        phone = tm.getLine1Number();
                                        name = tm.getLine1AlphaTag();
                                        if(name == null || name.isEmpty()){
                                            name = phone;
                                        }
                                    }
                                }
                                String priority = "no";// no priority for sms
                                /* Incoming message from the network */
                                if (mMapEventReportVersion ==
                                        BluetoothMapUtils.MAP_EVENT_REPORT_V11) {
                                    evt = new Event(EVENT_TYPE_NEW, id, getSmsFolderName(type),
                                            mSmsType, date, subject, name, priority);
                                } else {
                                    evt = new Event(EVENT_TYPE_NEW, id, getSmsFolderName(type),
                                            mSmsType, date, subject, name, priority,
                                            (long)threadId, null);
                                }
                            } else {
                                /* Incoming message from the network */
                                evt = new Event(EVENT_TYPE_NEW, id, getSmsFolderName(type),
                                        null, mSmsType);
                            }
                            sendEvent(evt);
                        } else {
                            /* Existing message */
                            if (type != msg.type) {
                                listChanged = true;
                                Log.d(TAG, "new type: " + type + " old type: " + msg.type);
                                String oldFolder = getSmsFolderName(msg.type);
                                String newFolder = getSmsFolderName(type);
                                // Filter out the intermediate outbox steps
                                if(!oldFolder.equalsIgnoreCase(newFolder)) {
                                    Event evt = new Event(EVENT_TYPE_SHIFT, id,
                                            getSmsFolderName(type), oldFolder, mSmsType);
                                    sendEvent(evt);
                                }
                                msg.type = type;
                            } else if(threadId != msg.threadId) {
                                listChanged = true;
                                Log.d(TAG, "Message delete change: type: " + type
                                        + " old type: " + msg.type
                                        + "\n    threadId: " + threadId
                                        + " old threadId: " + msg.threadId);
                                if(threadId == DELETED_THREAD_ID) { // Message deleted
                                    // TODO:
                                    // We shall only use the folder attribute, but can't remember
                                    // wether to set it to "deleted" or the name of the folder
                                    // from which the message have been deleted.
                                    // "old_folder" used only for MessageShift event
                                    Event evt = new Event(EVENT_TYPE_DELETE, id,
                                            getSmsFolderName(msg.type), null, mSmsType);
                                    sendEvent(evt);
                                    msg.threadId = threadId;
                                } else { // Undelete
                                    Event evt = new Event(EVENT_TYPE_SHIFT, id,
                                            getSmsFolderName(msg.type),
                                            BluetoothMapContract.FOLDER_NAME_DELETED, mSmsType);
                                    sendEvent(evt);
                                    msg.threadId = threadId;
                                }
                            }
                            if(read != msg.flagRead) {
                                listChanged = true;
                                msg.flagRead = read;
                                if (mMapEventReportVersion >
                                        BluetoothMapUtils.MAP_EVENT_REPORT_V10) {
                                    Event evt = new Event(EVENT_TYPE_READ_STATUS, id,
                                            getSmsFolderName(msg.type), mSmsType);
                                    sendEvent(evt);
                                }
                            }
                            msgListSms.put(id, msg);
                        }
                    } while (c.moveToNext());
                }
            } finally {
                if (c != null) c.close();
            }

            for (Msg msg : getMsgListSms().values()) {
                // "old_folder" used only for MessageShift event
                Event evt = new Event(EVENT_TYPE_DELETE, msg.id,
                        getSmsFolderName(msg.type), null, mSmsType);
                sendEvent(evt);
                listChanged = true;
            }

            setMsgListSms(msgListSms, listChanged);
        }
    }

    private void handleMsgListChangesMms() {
        if (V) Log.d(TAG, "handleMsgListChangesMms");

        HashMap<Long, Msg> msgListMms = new HashMap<Long, Msg>();
        boolean listChanged = false;
        Cursor c;
        synchronized(getMsgListMms()) {
            if (mMapEventReportVersion == BluetoothMapUtils.MAP_EVENT_REPORT_V10) {
                c = mResolver.query(Mms.CONTENT_URI,
                        MMS_PROJECTION_SHORT, null, null, null);
            } else {
                c = mResolver.query(Mms.CONTENT_URI,
                        MMS_PROJECTION_SHORT_EXT, null, null, null);
            }

            try{
                if (c != null && c.moveToFirst()) {
                    do {
                        long id = c.getLong(c.getColumnIndex(Mms._ID));
                        int type = c.getInt(c.getColumnIndex(Mms.MESSAGE_BOX));
                        int mtype = c.getInt(c.getColumnIndex(Mms.MESSAGE_TYPE));
                        int threadId = c.getInt(c.getColumnIndex(Mms.THREAD_ID));
                        // TODO: Go through code to see if we have an issue with mismatch in types
                        //       for threadId. Seems to be a long in DB??
                        int read = c.getInt(c.getColumnIndex(Mms.READ));

                        Msg msg = getMsgListMms().remove(id);

                        /* We must filter out any actions made by the MCE, hence do not send
                         * e.g. a message deleted and/or MessageShift for messages deleted by the
                         * MCE.*/

                        if (msg == null) {
                            /* New message - only notify on retrieve conf */
                            listChanged = true;
                            if (getMmsFolderName(type).equalsIgnoreCase(
                                    BluetoothMapContract.FOLDER_NAME_INBOX) &&
                                    mtype != MESSAGE_TYPE_RETRIEVE_CONF) {
                                continue;
                            }
                            msg = new Msg(id, type, threadId, read);
                            msgListMms.put(id, msg);
                            Event evt;
                            if (mTransmitEvents == true && // extract contact details only if needed
                                    mMapEventReportVersion !=
                                    BluetoothMapUtils.MAP_EVENT_REPORT_V10) {
                                String date = BluetoothMapUtils.getDateTimeString(
                                        c.getLong(c.getColumnIndex(Mms.DATE)));
                                String subject = c.getString(c.getColumnIndex(Mms.SUBJECT));
                                if (subject == null || subject.length() == 0) {
                                    /* Get subject from mms text body parts - if any exists */
                                    subject = BluetoothMapContent.getTextPartsMms(mResolver, id);
                                }
                                int tmpPri = c.getInt(c.getColumnIndex(Mms.PRIORITY));
                                Log.d(TAG, "TEMP handleMsgListChangesMms, " +
                                        "newMessage 'read' state: " + read +
                                        "priority: " + tmpPri);

                                String address = BluetoothMapContent.getAddressMms(
                                        mResolver,id,BluetoothMapContent.MMS_FROM);
                                String priority = "no";
                                if(tmpPri == PduHeaders.PRIORITY_HIGH)
                                    priority = "yes";

                                /* Incoming message from the network */
                                if (mMapEventReportVersion ==
                                        BluetoothMapUtils.MAP_EVENT_REPORT_V11) {
                                    evt = new Event(EVENT_TYPE_NEW, id, getMmsFolderName(type),
                                            TYPE.MMS, date, subject, address, priority);
                                } else {
                                    evt = new Event(EVENT_TYPE_NEW, id, getMmsFolderName(type),
                                            TYPE.MMS, date, subject, address, priority,
                                            (long)threadId, null);
                                }

                            } else {
                                /* Incoming message from the network */
                                evt = new Event(EVENT_TYPE_NEW, id, getMmsFolderName(type),
                                        null, TYPE.MMS);
                            }

                            sendEvent(evt);
                        } else {
                            /* Existing message */
                            if (type != msg.type) {
                                Log.d(TAG, "new type: " + type + " old type: " + msg.type);
                                Event evt;
                                listChanged = true;
                                if(msg.localInitiatedSend == false) {
                                    // Only send events about local initiated changes
                                    evt = new Event(EVENT_TYPE_SHIFT, id, getMmsFolderName(type),
                                            getMmsFolderName(msg.type), TYPE.MMS);
                                    sendEvent(evt);
                                }
                                msg.type = type;

                                if (getMmsFolderName(type).equalsIgnoreCase(
                                        BluetoothMapContract.FOLDER_NAME_SENT)
                                        && msg.localInitiatedSend == true) {
                                    // Stop tracking changes for this message
                                    msg.localInitiatedSend = false;
                                    evt = new Event(EVENT_TYPE_SENDING_SUCCESS, id,
                                            getMmsFolderName(type), null, TYPE.MMS);
                                    sendEvent(evt);
                                }
                            } else if(threadId != msg.threadId) {
                                Log.d(TAG, "Message delete change: type: " + type + " old type: "
                                        + msg.type
                                        + "\n    threadId: " + threadId + " old threadId: "
                                        + msg.threadId);
                                listChanged = true;
                                if(threadId == DELETED_THREAD_ID) { // Message deleted
                                    // "old_folder" used only for MessageShift event
                                    Event evt = new Event(EVENT_TYPE_DELETE, id,
                                            getMmsFolderName(msg.type), null, TYPE.MMS);
                                    sendEvent(evt);
                                    msg.threadId = threadId;
                                } else { // Undelete
                                    Event evt = new Event(EVENT_TYPE_SHIFT, id,
                                            getMmsFolderName(msg.type),
                                            BluetoothMapContract.FOLDER_NAME_DELETED, TYPE.MMS);
                                    sendEvent(evt);
                                    msg.threadId = threadId;
                                }
                            }
                            if(read != msg.flagRead) {
                                listChanged = true;
                                msg.flagRead = read;
                                if (mMapEventReportVersion >
                                        BluetoothMapUtils.MAP_EVENT_REPORT_V10) {
                                    Event evt = new Event(EVENT_TYPE_READ_STATUS, id,
                                            getMmsFolderName(msg.type), TYPE.MMS);
                                    sendEvent(evt);
                                }
                            }
                            msgListMms.put(id, msg);
                        }
                    } while (c.moveToNext());

                }
            } finally {
                if (c != null) c.close();
            }
            for (Msg msg : getMsgListMms().values()) {
                // "old_folder" used only for MessageShift event
                Event evt = new Event(EVENT_TYPE_DELETE, msg.id,
                        getMmsFolderName(msg.type), null, TYPE.MMS);
                sendEvent(evt);
                listChanged = true;
            }
            setMsgListMms(msgListMms, listChanged);
        }
    }

    private void handleMsgListChangesMsg(Uri uri)  throws RemoteException{
        if (V) Log.v(TAG, "handleMsgListChangesMsg uri: " + uri.toString());

        // TODO: Change observer to handle accountId and message ID if present

        HashMap<Long, Msg> msgList = new HashMap<Long, Msg>();
        Cursor c;
        boolean listChanged = false;
        if (mMapEventReportVersion == BluetoothMapUtils.MAP_EVENT_REPORT_V10) {
            c = mProviderClient.query(mMessageUri, MSG_PROJECTION_SHORT, null, null, null);
        } else if (mMapEventReportVersion == BluetoothMapUtils.MAP_EVENT_REPORT_V11) {
            c = mProviderClient.query(mMessageUri, MSG_PROJECTION_SHORT_EXT, null, null, null);
        } else {
            c = mProviderClient.query(mMessageUri, MSG_PROJECTION_SHORT_EXT2, null, null, null);
        }
        synchronized(getMsgListMsg()) {
            try {
                if (c != null && c.moveToFirst()) {
                    do {
                        long id = c.getLong(c.getColumnIndex(
                                BluetoothMapContract.MessageColumns._ID));
                        int folderId = c.getInt(c.getColumnIndex(
                                BluetoothMapContract.MessageColumns.FOLDER_ID));
                        int readFlag = c.getInt(c.getColumnIndex(
                                BluetoothMapContract.MessageColumns.FLAG_READ));
                        Msg msg = getMsgListMsg().remove(id);
                        BluetoothMapFolderElement folderElement = mFolders.getFolderById(folderId);
                        String newFolder;
                        if(folderElement != null) {
                            newFolder = folderElement.getFullPath();
                        } else {
                            // This can happen if a new folder is created while connected
                            newFolder = "unknown";
                        }
                        /* We must filter out any actions made by the MCE, hence do not send e.g.
                         * a message deleted and/or MessageShift for messages deleted by the MCE. */
                        if (msg == null) {
                            listChanged = true;
                            /* New message - created with message unread */
                            msg = new Msg(id, folderId, 0, readFlag);
                            msgList.put(id, msg);
                            Event evt;
                            /* Incoming message from the network */
                            if (mMapEventReportVersion != BluetoothMapUtils.MAP_EVENT_REPORT_V10) {
                                String date = BluetoothMapUtils.getDateTimeString(
                                        c.getLong(c.getColumnIndex(
                                                BluetoothMapContract.MessageColumns.DATE)));
                                String subject = c.getString(c.getColumnIndex(
                                        BluetoothMapContract.MessageColumns.SUBJECT));
                                String address = c.getString(c.getColumnIndex(
                                        BluetoothMapContract.MessageColumns.FROM_LIST));
                                String priority = "no";
                                if(c.getInt(c.getColumnIndex(
                                        BluetoothMapContract.MessageColumns.FLAG_HIGH_PRIORITY))
                                        == 1)
                                    priority = "yes";
                                if (mMapEventReportVersion ==
                                        BluetoothMapUtils.MAP_EVENT_REPORT_V11) {
                                    evt = new Event(EVENT_TYPE_NEW, id, newFolder,
                                            mAccount.getType(), date, subject, address, priority);
                                } else {
                                    long thread_id = c.getLong(c.getColumnIndex(
                                            BluetoothMapContract.MessageColumns.THREAD_ID));
                                    String thread_name = c.getString(c.getColumnIndex(
                                            BluetoothMapContract.MessageColumns.THREAD_NAME));
                                    evt = new Event(EVENT_TYPE_NEW, id, newFolder,
                                            mAccount.getType(), date, subject, address, priority,
                                            thread_id, thread_name);
                                }
                            } else {
                                evt = new Event(EVENT_TYPE_NEW, id, newFolder, null, TYPE.EMAIL);
                            }
                            sendEvent(evt);
                        } else {
                            /* Existing message */
                            if (folderId != msg.folderId && msg.folderId != -1) {
                                if (D) Log.d(TAG, "new folderId: " + folderId + " old folderId: "
                                        + msg.folderId);
                                BluetoothMapFolderElement oldFolderElement =
                                        mFolders.getFolderById(msg.folderId);
                                String oldFolder;
                                listChanged = true;
                                if(oldFolderElement != null) {
                                    oldFolder = oldFolderElement.getFullPath();
                                } else {
                                    // This can happen if a new folder is created while connected
                                    oldFolder = "unknown";
                                }
                                BluetoothMapFolderElement deletedFolder =
                                        mFolders.getFolderByName(
                                                BluetoothMapContract.FOLDER_NAME_DELETED);
                                BluetoothMapFolderElement sentFolder =
                                        mFolders.getFolderByName(
                                                BluetoothMapContract.FOLDER_NAME_SENT);
                                /*
                                 *  If the folder is now 'deleted', send a deleted-event in stead of
                                 *  a shift or if message is sent initiated by MAP Client, then send
                                 *  sending-success otherwise send folderShift
                                 */
                                if(deletedFolder != null && deletedFolder.getFolderId()
                                        == folderId) {
                                    // "old_folder" used only for MessageShift event
                                    Event evt = new Event(EVENT_TYPE_DELETE, msg.id, oldFolder,
                                            null, mAccount.getType());
                                    sendEvent(evt);
                                } else if(sentFolder != null
                                        && sentFolder.getFolderId() == folderId
                                        && msg.localInitiatedSend == true) {
                                    if(msg.transparent) {
                                        mResolver.delete(
                                                ContentUris.withAppendedId(mMessageUri, id),
                                                null, null);
                                    } else {
                                        msg.localInitiatedSend = false;
                                        Event evt = new Event(EVENT_TYPE_SENDING_SUCCESS, msg.id,
                                                oldFolder, null, mAccount.getType());
                                        sendEvent(evt);
                                    }
                                } else {
                                    if (!oldFolder.equalsIgnoreCase("root")) {
                                        Event evt = new Event(EVENT_TYPE_SHIFT, id, newFolder,
                                                oldFolder, mAccount.getType());
                                        sendEvent(evt);
                                    }
                                }
                                msg.folderId = folderId;
                            }
                            if(readFlag != msg.flagRead) {
                                listChanged = true;

                                if (mMapEventReportVersion >
                                BluetoothMapUtils.MAP_EVENT_REPORT_V10) {
                                    Event evt = new Event(EVENT_TYPE_READ_STATUS, id, newFolder,
                                            mAccount.getType());
                                    sendEvent(evt);
                                    msg.flagRead = readFlag;
                                }
                            }

                            msgList.put(id, msg);
                        }
                    } while (c.moveToNext());
                }
            } finally {
                if (c != null) c.close();
            }
            // For all messages no longer in the database send a delete notification
            for (Msg msg : getMsgListMsg().values()) {
                BluetoothMapFolderElement oldFolderElement = mFolders.getFolderById(msg.folderId);
                String oldFolder;
                listChanged = true;
                if(oldFolderElement != null) {
                    oldFolder = oldFolderElement.getFullPath();
                } else {
                    oldFolder = "unknown";
                }
                /* Some e-mail clients delete the message after sending, and creates a
                 * new message in sent. We cannot track the message anymore, hence send both a
                 * send success and delete message.
                 */
                if(msg.localInitiatedSend == true) {
                    msg.localInitiatedSend = false;
                    // If message is send with transparency don't set folder as message is deleted
                    if (msg.transparent)
                        oldFolder = null;
                    Event evt = new Event(EVENT_TYPE_SENDING_SUCCESS, msg.id, oldFolder, null,
                            mAccount.getType());
                    sendEvent(evt);
                }
                /* As this message deleted is only send on a real delete - don't set folder.
                 *  - only send delete event if message is not sent with transparency
                 */
                if (!msg.transparent) {

                    // "old_folder" used only for MessageShift event
                    Event evt = new Event(EVENT_TYPE_DELETE, msg.id, oldFolder,
                            null, mAccount.getType());
                    sendEvent(evt);
                }
            }
            setMsgListMsg(msgList, listChanged);
        }
    }

    private void handleMsgListChanges(Uri uri) {
        if(uri.getAuthority().equals(mAuthority)) {
            try {
                if(D) Log.d(TAG, "handleMsgListChanges: account type = "
                        + mAccount.getType().toString());
                handleMsgListChangesMsg(uri);
            } catch(RemoteException e) {
                mMasInstance.restartObexServerSession();
                Log.w(TAG, "Problems contacting the ContentProvider in mas Instance "
                        + mMasId + " restaring ObexServerSession");
            }

        }
        // TODO: check to see if there could be problem with IM and SMS in one instance
        if (mEnableSmsMms) {
            handleMsgListChangesSms();
            handleMsgListChangesMms();
        }
    }

    private void handleContactListChanges(Uri uri) {
        if (uri.getAuthority().equals(mAuthority)) {
            try {
                if (V) Log.v(TAG,"handleContactListChanges uri: " + uri.toString());
                Cursor c = null;
                boolean listChanged = false;
                try {
                    ConvoContactInfo cInfo = new ConvoContactInfo();

                    if (mMapEventReportVersion != BluetoothMapUtils.MAP_EVENT_REPORT_V10
                            && mMapEventReportVersion != BluetoothMapUtils.MAP_EVENT_REPORT_V11) {
                        c = mProviderClient
                                .query(mContactUri,
                                        BluetoothMapContract.
                                        BT_CONTACT_CHATSTATE_PRESENCE_PROJECTION,
                                        null, null, null);
                        cInfo.setConvoColunms(c);
                    } else {
                        if (V) Log.v(TAG,"handleContactListChanges MAP version does not" +
                                "support convocontact notifications");
                        return;
                    }

                    HashMap<String, BluetoothMapConvoContactElement> contactList =
                            new HashMap<String,
                            BluetoothMapConvoContactElement>(getContactList().size());

                    synchronized (getContactList()) {
                        if (c != null && c.moveToFirst()) {
                            do {
                                String uci = c.getString(cInfo.mContactColUci);
                                long convoId = c.getLong(cInfo.mContactColConvoId);
                                if (convoId == 0)
                                    continue;

                                if (V) BluetoothMapUtils.printCursor(c);

                                BluetoothMapConvoContactElement contact =
                                        getContactList().remove(uci);

                                /*
                                 * We must filter out any actions made by the
                                 * MCE, hence do not send e.g. a message deleted
                                 * and/or MessageShift for messages deleted by
                                 * the MCE.
                                 */
                                if (contact == null) {
                                    listChanged = true;
                                    /*
                                     * New contact - added to conversation and
                                     * tracked here
                                     */
                                    if (mMapEventReportVersion
                                            != BluetoothMapUtils.MAP_EVENT_REPORT_V10
                                            && mMapEventReportVersion
                                            != BluetoothMapUtils.MAP_EVENT_REPORT_V11) {
                                        Event evt;
                                        String name = c
                                                .getString(cInfo.mContactColName);
                                        String displayName = c
                                                .getString(cInfo.mContactColNickname);
                                        String presenceStatus = c
                                                .getString(cInfo.mContactColPresenceText);
                                        int presenceState = c
                                                .getInt(cInfo.mContactColPresenceState);
                                        long lastActivity = c
                                                .getLong(cInfo.mContactColLastActive);
                                        int chatState = c
                                                .getInt(cInfo.mContactColChatState);
                                        int priority = c
                                                .getInt(cInfo.mContactColPriority);
                                        String btUid = c
                                                .getString(cInfo.mContactColBtUid);

                                        // Get Conversation information for
                                        // event
//                                        Uri convoUri = Uri
//                                                .parse(mAccount.mBase_uri
//                                                        + "/"
//                                                        + BluetoothMapContract.TABLE_CONVERSATION);
//                                        String whereClause = "contacts._id = "
//                                                + convoId;
//                                        Cursor cConvo = mProviderClient
//                                                .query(convoUri,
//                                                       BluetoothMapContract.BT_CONVERSATION_PROJECTION,
//                                                       whereClause, null, null);
                                        // TODO: will move out of the loop when merged with CB's
                                        // changes make sure to look up col index out side loop
                                        String convoName = null;
//                                        if (cConvo != null
//                                                && cConvo.moveToFirst()) {
//                                            convoName = cConvo
//                                                    .getString(cConvo
//                                                            .getColumnIndex(BluetoothMapContract.ConvoContactColumns.NAME));
//                                        }

                                        contact = new BluetoothMapConvoContactElement(
                                                uci, name, displayName,
                                                presenceStatus, presenceState,
                                                lastActivity, chatState,
                                                priority, btUid);

                                        contactList.put(uci, contact);

                                        evt = new Event(
                                                EVENT_TYPE_CONVERSATION,
                                                uci,
                                                mAccount.getType(),
                                                name,
                                                String.valueOf(priority),
                                                BluetoothMapUtils
                                                .getDateTimeString(lastActivity),
                                                convoId, convoName,
                                                presenceState, presenceStatus,
                                                chatState);

                                        sendEvent(evt);
                                    }

                                } else {
                                    // Not new - compare updated content
//                                    Uri convoUri = Uri
//                                            .parse(mAccount.mBase_uri
//                                                    + "/"
//                                                    + BluetoothMapContract.TABLE_CONVERSATION);
                                    // TODO: Should be changed to own provider interface name
//                                    String whereClause = "contacts._id = "
//                                            + convoId;
//                                    Cursor cConvo = mProviderClient
//                                            .query(convoUri,
//                                                    BluetoothMapContract.BT_CONVERSATION_PROJECTION,
//                                                    whereClause, null, null);
//                                    // TODO: will move out of the loop when merged with CB's
//                                    // changes make sure to look up col index out side loop
                                    String convoName = null;
//                                    if (cConvo != null && cConvo.moveToFirst()) {
//                                        convoName = cConvo
//                                                .getString(cConvo
//                                                        .getColumnIndex(BluetoothMapContract.ConvoContactColumns.NAME));
//                                    }

                                    // Check if presence is updated
                                    int presenceState = c.getInt(cInfo.mContactColPresenceState);
                                    String presenceStatus = c.getString(
                                            cInfo.mContactColPresenceText);
                                    String currentPresenceStatus = contact
                                            .getPresenceStatus();
                                    if (contact.getPresenceAvailability() != presenceState
                                            || currentPresenceStatus != presenceStatus) {
                                        long lastOnline = c
                                                .getLong(cInfo.mContactColLastOnline);
                                        contact.setPresenceAvailability(presenceState);
                                        contact.setLastActivity(lastOnline);
                                        if (currentPresenceStatus != null
                                                && !currentPresenceStatus
                                                .equals(presenceStatus)) {
                                            contact.setPresenceStatus(presenceStatus);
                                        }
                                        Event evt = new Event(
                                                EVENT_TYPE_PRESENCE,
                                                uci,
                                                mAccount.getType(),
                                                contact.getName(),
                                                String.valueOf(contact
                                                        .getPriority()),
                                                        BluetoothMapUtils
                                                        .getDateTimeString(lastOnline),
                                                        convoId, convoName,
                                                        presenceState, presenceStatus,
                                                        0);
                                        sendEvent(evt);
                                    }

                                    // Check if chat state is updated
                                    int chatState = c.getInt(cInfo.mContactColChatState);
                                    if (contact.getChatState() != chatState) {
                                        // Get DB timestamp
                                        long lastActivity = c.getLong(cInfo.mContactColLastActive);
                                        contact.setLastActivity(lastActivity);
                                        contact.setChatState(chatState);
                                        Event evt = new Event(
                                                EVENT_TYPE_CHAT_STATE,
                                                uci,
                                                mAccount.getType(),
                                                contact.getName(),
                                                String.valueOf(contact
                                                        .getPriority()),
                                                        BluetoothMapUtils
                                                        .getDateTimeString(lastActivity),
                                                        convoId, convoName, 0, null,
                                                        chatState);
                                        sendEvent(evt);
                                    }
                                    contactList.put(uci, contact);
                                }
                            } while (c.moveToNext());
                        }
                        if(getContactList().size() > 0) {
                            // one or more contacts were deleted, hence the conversation listing
                            // version counter should change.
                            listChanged = true;
                        }
                        setContactList(contactList, listChanged);
                    } // end synchronized
                } finally {
                    if (c != null) c.close();
                }
            } catch (RemoteException e) {
                mMasInstance.restartObexServerSession();
                Log.w(TAG,
                        "Problems contacting the ContentProvider in mas Instance "
                                + mMasId + " restaring ObexServerSession");
            }

        }
        // TODO: conversation contact updates if IM and SMS(MMS in one instance
    }

    private boolean setEmailMessageStatusDelete(BluetoothMapFolderElement mCurrentFolder,
            String uriStr, long handle, int status) {
        boolean res = false;
        Uri uri = Uri.parse(uriStr + BluetoothMapContract.TABLE_MESSAGE);

        int updateCount = 0;
        ContentValues contentValues = new ContentValues();
        BluetoothMapFolderElement deleteFolder = mFolders.
                getFolderByName(BluetoothMapContract.FOLDER_NAME_DELETED);
        contentValues.put(BluetoothMapContract.MessageColumns._ID, handle);
        synchronized(getMsgListMsg()) {
            Msg msg = getMsgListMsg().get(handle);
            if (status == BluetoothMapAppParams.STATUS_VALUE_YES) {
                /* Set deleted folder id */
                long folderId = -1;
                if(deleteFolder != null) {
                    folderId = deleteFolder.getFolderId();
                }
                contentValues.put(BluetoothMapContract.MessageColumns.FOLDER_ID,folderId);
                updateCount = mResolver.update(uri, contentValues, null, null);
                /* The race between updating the value in our cached values and the database
                 * is handled by the synchronized statement. */
                if(updateCount > 0) {
                    res = true;
                    if (msg != null) {
                        msg.oldFolderId = msg.folderId;
                        /* Update the folder ID to avoid triggering an event for MCE
                         * initiated actions. */
                        msg.folderId = folderId;
                    }
                    if(D) Log.d(TAG, "Deleted MSG: " + handle + " from folderId: " + folderId);
                } else {
                    Log.w(TAG, "Msg: " + handle + " - Set delete status " + status
                            + " failed for folderId " + folderId);
                }
            } else if (status == BluetoothMapAppParams.STATUS_VALUE_NO) {
                /* Undelete message. move to old folder if we know it,
                 * else move to inbox - as dictated by the spec. */
                if(msg != null && deleteFolder != null &&
                        msg.folderId == deleteFolder.getFolderId()) {
                    /* Only modify messages in the 'Deleted' folder */
                    long folderId = -1;
                    BluetoothMapFolderElement inboxFolder = mCurrentFolder.
                            getFolderByName(BluetoothMapContract.FOLDER_NAME_INBOX);
                    if (msg != null && msg.oldFolderId != -1) {
                        folderId = msg.oldFolderId;
                    } else {
                        if(inboxFolder != null) {
                            folderId = inboxFolder.getFolderId();
                        }
                        if(D)Log.d(TAG,"We did not delete the message, hence the old folder " +
                                "is unknown. Moving to inbox.");
                    }
                    contentValues.put(BluetoothMapContract.MessageColumns.FOLDER_ID, folderId);
                    updateCount = mResolver.update(uri, contentValues, null, null);
                    if(updateCount > 0) {
                        res = true;
                        /* Update the folder ID to avoid triggering an event for MCE
                         * initiated actions. */
                        /* UPDATE: Actually the BT-Spec. states that an undelete is a move of the
                         * message to INBOX - clearified in errata 5591.
                         * Therefore we update the cache to INBOX-folderId - to trigger a message
                         * shift event to the old-folder. */
                        if(inboxFolder != null) {
                            msg.folderId = inboxFolder.getFolderId();
                        } else {
                            msg.folderId = folderId;
                        }
                    } else {
                        if(D)Log.d(TAG,"We did not delete the message, hence the old folder " +
                                "is unknown. Moving to inbox.");
                    }
                }
            }
            if(V) {
                BluetoothMapFolderElement folderElement;
                String folderName = "unknown";
                if (msg != null) {
                    folderElement = mCurrentFolder.getFolderById(msg.folderId);
                    if(folderElement != null) {
                        folderName = folderElement.getName();
                    }
                }
                Log.d(TAG,"setEmailMessageStatusDelete: " + handle + " from " + folderName
                        + " status: " + status);
            }
        }
        if(res == false) {
            Log.w(TAG, "Set delete status " + status + " failed.");
        }
        return res;
    }

    private void updateThreadId(Uri uri, String valueString, long threadId) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(valueString, threadId);
        mResolver.update(uri, contentValues, null, null);
    }

    private boolean deleteMessageMms(long handle) {
        boolean res = false;
        Uri uri = ContentUris.withAppendedId(Mms.CONTENT_URI, handle);
        Cursor c = mResolver.query(uri, null, null, null, null);
        try {
            if (c != null && c.moveToFirst()) {
                /* Move to deleted folder, or delete if already in deleted folder */
                int threadId = c.getInt(c.getColumnIndex(Mms.THREAD_ID));
                if (threadId != DELETED_THREAD_ID) {
                    /* Set deleted thread id */
                    synchronized(getMsgListMms()) {
                        Msg msg = getMsgListMms().get(handle);
                        if(msg != null) { // This will always be the case
                            msg.threadId = DELETED_THREAD_ID;
                        }
                    }
                    updateThreadId(uri, Mms.THREAD_ID, DELETED_THREAD_ID);
                } else {
                    /* Delete from observer message list to avoid delete notifications */
                    synchronized(getMsgListMms()) {
                        getMsgListMms().remove(handle);
                    }
                    /* Delete message */
                    mResolver.delete(uri, null, null);
                }
                res = true;
            }
        } finally {
            if (c != null) c.close();
        }

        return res;
    }

    private boolean unDeleteMessageMms(long handle) {
        boolean res = false;
        Uri uri = ContentUris.withAppendedId(Mms.CONTENT_URI, handle);
        Cursor c = mResolver.query(uri, null, null, null, null);
        try {
            if (c != null && c.moveToFirst()) {
                int threadId = c.getInt(c.getColumnIndex(Mms.THREAD_ID));
                if (threadId == DELETED_THREAD_ID) {
                    /* Restore thread id from address, or if no thread for address
                     * create new thread by insert and remove of fake message */
                    String address;
                    long id = c.getLong(c.getColumnIndex(Mms._ID));
                    int msgBox = c.getInt(c.getColumnIndex(Mms.MESSAGE_BOX));
                    if (msgBox == Mms.MESSAGE_BOX_INBOX) {
                        address = BluetoothMapContent.getAddressMms(mResolver, id,
                                BluetoothMapContent.MMS_FROM);
                    } else {
                        address = BluetoothMapContent.getAddressMms(mResolver, id,
                                BluetoothMapContent.MMS_TO);
                    }
                    Set<String> recipients = new HashSet<String>();
                    recipients.addAll(Arrays.asList(address));
                    Long oldThreadId = Telephony.Threads.getOrCreateThreadId(mContext, recipients);
                    synchronized(getMsgListMms()) {
                        Msg msg = getMsgListMms().get(handle);
                        if(msg != null) { // This will always be the case
                            msg.threadId = oldThreadId.intValue();
                            // Spec. states that undelete shall shift the message to Inbox.
                            // Hence we need to trigger a message shift from INBOX to old-folder
                            // after undelete.
                            // We do this by changing the cached folder value to being inbox - hence
                            // the event handler will se the update as the message have been shifted
                            // from INBOX to old-folder. (Errata 5591 clearifies this)
                            msg.type = Mms.MESSAGE_BOX_INBOX;
                        }
                    }
                    updateThreadId(uri, Mms.THREAD_ID, oldThreadId);
                } else {
                    Log.d(TAG, "Message not in deleted folder: handle " + handle
                            + " threadId " + threadId);
                }
                res = true;
            }
        } finally {
            if (c != null) c.close();
        }
        return res;
    }

    private boolean deleteMessageSms(long handle) {
        boolean res = false;
        Uri uri = ContentUris.withAppendedId(Sms.CONTENT_URI, handle);
        Cursor c = mResolver.query(uri, null, null, null, null);
        try {
            if (c != null && c.moveToFirst()) {
                /* Move to deleted folder, or delete if already in deleted folder */
                int threadId = c.getInt(c.getColumnIndex(Sms.THREAD_ID));
                if (threadId != DELETED_THREAD_ID) {
                    synchronized(getMsgListSms()) {
                        Msg msg = getMsgListSms().get(handle);
                        if(msg != null) { // This will always be the case
                            msg.threadId = DELETED_THREAD_ID;
                        }
                    }
                    /* Set deleted thread id */
                    updateThreadId(uri, Sms.THREAD_ID, DELETED_THREAD_ID);
                } else {
                    /* Delete from observer message list to avoid delete notifications */
                    synchronized(getMsgListSms()) {
                        getMsgListSms().remove(handle);
                    }
                    /* Delete message */
                    mResolver.delete(uri, null, null);
                }
                res = true;
            }
        } finally {
            if (c != null) c.close();
        }
        return res;
    }

    private boolean unDeleteMessageSms(long handle) {
        boolean res = false;
        Uri uri = ContentUris.withAppendedId(Sms.CONTENT_URI, handle);
        Cursor c = mResolver.query(uri, null, null, null, null);
        try {
            if (c != null && c.moveToFirst()) {
                int threadId = c.getInt(c.getColumnIndex(Sms.THREAD_ID));
                if (threadId == DELETED_THREAD_ID) {
                    String address = c.getString(c.getColumnIndex(Sms.ADDRESS));
                    Set<String> recipients = new HashSet<String>();
                    recipients.addAll(Arrays.asList(address));
                    Long oldThreadId = Telephony.Threads.getOrCreateThreadId(mContext, recipients);
                    synchronized(getMsgListSms()) {
                        Msg msg = getMsgListSms().get(handle);
                        if(msg != null) {
                            msg.threadId = oldThreadId.intValue();
                            /* This will always be the case
                             * The threadId is specified as an int, so it is safe to truncate
                             * TODO: Test that this will trigger a message-shift from Inbox
                             * to old-folder
                             **/
                            /* Spec. states that undelete shall shift the message to Inbox.
                             * Hence we need to trigger a message shift from INBOX to old-folder
                             * after undelete.
                             * We do this by changing the cached folder value to being inbox - hence
                             * the event handler will se the update as the message have been shifted
                             * from INBOX to old-folder. (Errata 5591 clearifies this)
                             * */
                            msg.type = Sms.MESSAGE_TYPE_INBOX;
                        }
                    }
                    updateThreadId(uri, Sms.THREAD_ID, oldThreadId);
                } else {
                    Log.d(TAG, "Message not in deleted folder: handle " + handle
                            + " threadId " + threadId);
                }
                res = true;
            }
        } finally {
            if (c != null) c.close();
        }
        return res;
    }

    /**
     *
     * @param handle
     * @param type
     * @param mCurrentFolder
     * @param uriStr
     * @param statusValue
     * @return true is success
     */
    public boolean setMessageStatusDeleted(long handle, TYPE type,
            BluetoothMapFolderElement mCurrentFolder, String uriStr, int statusValue) {
        boolean res = false;
        if (D) Log.d(TAG, "setMessageStatusDeleted: handle " + handle
                + " type " + type + " value " + statusValue);

        if (type == TYPE.EMAIL) {
            res = setEmailMessageStatusDelete(mCurrentFolder, uriStr, handle, statusValue);
        } else if (type == TYPE.IM) {
            // TODO: to do when deleting IM message
            if (D) Log.d(TAG, "setMessageStatusDeleted: IM not handled" );
        } else {
            if (statusValue == BluetoothMapAppParams.STATUS_VALUE_YES) {
                if (type == TYPE.SMS_GSM || type == TYPE.SMS_CDMA) {
                    res = deleteMessageSms(handle);
                } else if (type == TYPE.MMS) {
                    res = deleteMessageMms(handle);
                }
            } else if (statusValue == BluetoothMapAppParams.STATUS_VALUE_NO) {
                if (type == TYPE.SMS_GSM || type == TYPE.SMS_CDMA) {
                    res = unDeleteMessageSms(handle);
                } else if (type == TYPE.MMS) {
                    res = unDeleteMessageMms(handle);
                }
            }
        }
        return res;
    }

    /**
     *
     * @param handle
     * @param type
     * @param uriStr
     * @param statusValue
     * @return true at success
     */
    public boolean setMessageStatusRead(long handle, TYPE type, String uriStr, int statusValue)
            throws RemoteException{
        int count = 0;

        if (D) Log.d(TAG, "setMessageStatusRead: handle " + handle
                + " type " + type + " value " + statusValue);

        /* Approved MAP spec errata 3445 states that read status initiated
         * by the MCE shall change the MSE read status. */
        if (type == TYPE.SMS_GSM || type == TYPE.SMS_CDMA) {
            Uri uri = ContentUris.withAppendedId(Sms.CONTENT_URI, handle);
            ContentValues contentValues = new ContentValues();
            contentValues.put(Sms.READ, statusValue);
            contentValues.put(Sms.SEEN, statusValue);
            String values = contentValues.toString();
            if (D) Log.d(TAG, " -> SMS Uri: " + uri.toString() + " values " + values);
            synchronized(getMsgListSms()) {
                Msg msg = getMsgListSms().get(handle);
                if(msg != null) { // This will always be the case
                    msg.flagRead = statusValue;
                }
            }
            count = mResolver.update(uri, contentValues, null, null);
            if (D) Log.d(TAG, " -> "+count +" rows updated!");

        } else if (type == TYPE.MMS) {
            Uri uri = ContentUris.withAppendedId(Mms.CONTENT_URI, handle);
            if (D) Log.d(TAG, " -> MMS Uri: " + uri.toString());
            ContentValues contentValues = new ContentValues();
            contentValues.put(Mms.READ, statusValue);
            synchronized(getMsgListMms()) {
                Msg msg = getMsgListMms().get(handle);
                if(msg != null) { // This will always be the case
                    msg.flagRead = statusValue;
                }
            }
            count = mResolver.update(uri, contentValues, null, null);
            if (D) Log.d(TAG, " -> "+count +" rows updated!");
        } else if (type == TYPE.EMAIL ||
                type == TYPE.IM) {
            Uri uri = mMessageUri;
            ContentValues contentValues = new ContentValues();
            contentValues.put(BluetoothMapContract.MessageColumns.FLAG_READ, statusValue);
            contentValues.put(BluetoothMapContract.MessageColumns._ID, handle);
            synchronized(getMsgListMsg()) {
                Msg msg = getMsgListMsg().get(handle);
                if(msg != null) { // This will always be the case
                    msg.flagRead = statusValue;
                }
            }
            count = mProviderClient.update(uri, contentValues, null, null);
        }

        return (count > 0);
    }

    private class PushMsgInfo {
        long id;
        int transparent;
        int retry;
        String phone;
        Uri uri;
        long timestamp;
        int parts;
        int partsSent;
        int partsDelivered;
        boolean resend;
        boolean sendInProgress;
        boolean failedSent; // Set to true if a single part sent fail is received.
        int statusDelivered; // Set to != 0 if a single part deliver fail is received.

        public PushMsgInfo(long id, int transparent,
                int retry, String phone, Uri uri) {
            this.id = id;
            this.transparent = transparent;
            this.retry = retry;
            this.phone = phone;
            this.uri = uri;
            this.resend = false;
            this.sendInProgress = false;
            this.failedSent = false;
            this.statusDelivered = 0; /* Assume success */
            this.timestamp = 0;
        };
    }

    private Map<Long, PushMsgInfo> mPushMsgList =
            Collections.synchronizedMap(new HashMap<Long, PushMsgInfo>());

    public long pushMessage(BluetoothMapbMessage msg, BluetoothMapFolderElement folderElement,
            BluetoothMapAppParams ap, String emailBaseUri)
                    throws IllegalArgumentException, RemoteException, IOException {
        if (D) Log.d(TAG, "pushMessage");
        ArrayList<BluetoothMapbMessage.vCard> recipientList = msg.getRecipients();
        int transparent = (ap.getTransparent() == BluetoothMapAppParams.INVALID_VALUE_PARAMETER) ?
                0 : ap.getTransparent();
        int retry = ap.getRetry();
        int charset = ap.getCharset();
        long handle = -1;
        long folderId = -1;

        if (recipientList == null) {
            if (folderElement.getName().equalsIgnoreCase(BluetoothMapContract.FOLDER_NAME_DRAFT)) {
                BluetoothMapbMessage.vCard empty =
                    new BluetoothMapbMessage.vCard("", "", null, null, 0);
                recipientList = new ArrayList<BluetoothMapbMessage.vCard>();
                recipientList.add(empty);
                Log.w(TAG, "Added empty recipient to draft message");
            } else {
                Log.e(TAG, "Trying to send a message with no recipients");
                return -1;
            }
        }

        if ( msg.getType().equals(TYPE.EMAIL) ) {
            /* Write the message to the database */
            String msgBody = ((BluetoothMapbMessageEmail) msg).getEmailBody();
            if (V) {
                int length = msgBody.length();
                Log.v(TAG, "pushMessage: message string length = " + length);
                String messages[] = msgBody.split("\r\n");
                Log.v(TAG, "pushMessage: messages count=" + messages.length);
                for(int i = 0; i < messages.length; i++) {
                    Log.v(TAG, "part " + i + ":" + messages[i]);
                }
            }
            FileOutputStream os = null;
            ParcelFileDescriptor fdOut = null;
            Uri uriInsert = Uri.parse(emailBaseUri + BluetoothMapContract.TABLE_MESSAGE);
            if (D) Log.d(TAG, "pushMessage - uriInsert= " + uriInsert.toString() +
                    ", intoFolder id=" + folderElement.getFolderId());

            synchronized(getMsgListMsg()) {
                // Now insert the empty message into folder
                ContentValues values = new ContentValues();
                folderId = folderElement.getFolderId();
                values.put(BluetoothMapContract.MessageColumns.FOLDER_ID, folderId);
                Uri uriNew = mProviderClient.insert(uriInsert, values);
                if (D) Log.d(TAG, "pushMessage - uriNew= " + uriNew.toString());
                handle =  Long.parseLong(uriNew.getLastPathSegment());

                try {
                    fdOut = mProviderClient.openFile(uriNew, "w");
                    os = new FileOutputStream(fdOut.getFileDescriptor());
                    // Write Email to DB
                    os.write(msgBody.getBytes(), 0, msgBody.getBytes().length);
                } catch (FileNotFoundException e) {
                    Log.w(TAG, e);
                    throw(new IOException("Unable to open file stream"));
                } catch (NullPointerException e) {
                    Log.w(TAG, e);
                    throw(new IllegalArgumentException("Unable to parse message."));
                } finally {
                    try {
                        if(os != null)
                            os.close();
                    } catch (IOException e) {Log.w(TAG, e);}
                    try {
                        if(fdOut != null)
                            fdOut.close();
                    } catch (IOException e) {Log.w(TAG, e);}
                }

                /* Extract the data for the inserted message, and store in local mirror, to
                 * avoid sending a NewMessage Event. */
                /*TODO: We need to add the new 1.1 parameter as well:-) e.g. read*/
                Msg newMsg = new Msg(handle, folderId, 1); // TODO: Create define for read-state
                newMsg.transparent = (transparent == 1) ? true : false;
                if ( folderId == folderElement.getFolderByName(
                        BluetoothMapContract.FOLDER_NAME_OUTBOX).getFolderId() ) {
                    newMsg.localInitiatedSend = true;
                }
                getMsgListMsg().put(handle, newMsg);
            }
        } else { // type SMS_* of MMS
            for (BluetoothMapbMessage.vCard recipient : recipientList) {
                // Only send the message to the top level recipient
                if(recipient.getEnvLevel() == 0)
                {
                    /* Only send to first address */
                    String phone = recipient.getFirstPhoneNumber();
                    String email = recipient.getFirstEmail();
                    String folder = folderElement.getName();
                    boolean read = false;
                    boolean deliveryReport = true;
                    String msgBody = null;

                    /* If MMS contains text only and the size is less than ten SMS's
                     * then convert the MMS to type SMS and then proceed
                     */
                    if (msg.getType().equals(TYPE.MMS) &&
                            (((BluetoothMapbMessageMime) msg).getTextOnly() == true)) {
                        msgBody = ((BluetoothMapbMessageMime) msg).getMessageAsText();
                        SmsManager smsMng = SmsManager.getDefault();
                        ArrayList<String> parts = smsMng.divideMessage(msgBody);
                        int smsParts = parts.size();
                        if (smsParts  <= CONVERT_MMS_TO_SMS_PART_COUNT ) {
                            if (D) Log.d(TAG, "pushMessage - converting MMS to SMS, sms parts="
                                    + smsParts );
                            msg.setType(mSmsType);
                        } else {
                            if (D) Log.d(TAG, "pushMessage - MMS text only but to big to " +
                                    "convert to SMS");
                            msgBody = null;
                        }

                    }

                    if (msg.getType().equals(TYPE.MMS)) {
                        /* Send message if folder is outbox else just store in draft*/
                        handle = sendMmsMessage(folder, phone, (BluetoothMapbMessageMime)msg,
                                transparent, retry);
                    } else if (msg.getType().equals(TYPE.SMS_GSM) ||
                            msg.getType().equals(TYPE.SMS_CDMA) ) {
                        /* Add the message to the database */
                        if(msgBody == null)
                            msgBody = ((BluetoothMapbMessageSms) msg).getSmsBody();

                        if (TextUtils.isEmpty(msgBody)) {
                            Log.d(TAG, "PushMsg: Empty msgBody ");
                            /* not allowed to push empty message */
                            throw new IllegalArgumentException("push EMPTY message: Invalid Body");
                        }
                        /* We need to lock the SMS list while updating the database,
                         * to avoid sending events on MCE initiated operation. */
                        Uri contentUri = Uri.parse(Sms.CONTENT_URI+ "/" + folder);
                        Uri uri;
                        synchronized(getMsgListSms()) {
                            uri = Sms.addMessageToUri(mResolver, contentUri, phone, msgBody,
                                    "", System.currentTimeMillis(), read, deliveryReport);

                            if(V) Log.v(TAG, "Sms.addMessageToUri() returned: " + uri);
                            if (uri == null) {
                                if (D) Log.d(TAG, "pushMessage - failure on add to uri "
                                        + contentUri);
                                return -1;
                            }
                            Cursor c = mResolver.query(uri, SMS_PROJECTION_SHORT, null, null, null);

                            /* Extract the data for the inserted message, and store in local mirror,
                             * to avoid sending a NewMessage Event. */
                            try {
                                if (c != null && c.moveToFirst()) {
                                    long id = c.getLong(c.getColumnIndex(Sms._ID));
                                    int type = c.getInt(c.getColumnIndex(Sms.TYPE));
                                    int threadId = c.getInt(c.getColumnIndex(Sms.THREAD_ID));
                                    int readFlag = c.getInt(c.getColumnIndex(Sms.READ));
                                    if(V) Log.v(TAG, "add message with id=" + id +
                                            " type=" + type + " threadId=" + threadId +
                                            " readFlag=" + readFlag + "to mMsgListSms");
                                    Msg newMsg = new Msg(id, type, threadId, readFlag);
                                    getMsgListSms().put(id, newMsg);
                                    c.close();
                                } else {
                                    Log.w(TAG,"Message: " + uri + " no longer exist!");
                                    /* This can only happen, if the message is deleted
                                     * just as it is added */
                                    return -1;
                                }
                            } finally {
                                if (c != null) c.close();
                            }

                            handle = Long.parseLong(uri.getLastPathSegment());

                            /* Send message if folder is outbox */
                            if (folder.equalsIgnoreCase(BluetoothMapContract.FOLDER_NAME_OUTBOX)) {
                                PushMsgInfo msgInfo = new PushMsgInfo(handle, transparent,
                                        retry, phone, uri);
                                mPushMsgList.put(handle, msgInfo);
                                sendMessage(msgInfo, msgBody);
                                if(V) Log.v(TAG, "sendMessage returned...");
                            } /* else just added to draft */

                            /* sendMessage causes the message to be deleted and reinserted,
                             * hence we need to lock the list while this is happening. */
                        }
                    } else {
                        if (D) Log.d(TAG, "pushMessage - failure on type " );
                        return -1;
                    }
                }
            }
        }

        /* If multiple recipients return handle of last */
        return handle;
    }

    public long sendMmsMessage(String folder, String to_address, BluetoothMapbMessageMime msg,
            int transparent, int retry) {
        /*
         *strategy:
         *1) parse message into parts
         *if folder is outbox/drafts:
         *2) push message to draft
         *if folder is outbox:
         *3) move message to outbox (to trigger the mms app to add msg to pending_messages list)
         *4) send intent to mms app in order to wake it up.
         *else if folder !outbox:
         *1) push message to folder
         * */
        if (folder != null && (folder.equalsIgnoreCase(BluetoothMapContract.FOLDER_NAME_OUTBOX)
                ||  folder.equalsIgnoreCase(BluetoothMapContract.FOLDER_NAME_DRAFT))) {
            long handle = pushMmsToFolder(Mms.MESSAGE_BOX_DRAFTS, to_address, msg);
            /* if invalid handle (-1) then just return the handle
             * - else continue sending (if folder is outbox) */
            if (BluetoothMapAppParams.INVALID_VALUE_PARAMETER != handle &&
                    folder.equalsIgnoreCase(BluetoothMapContract.FOLDER_NAME_OUTBOX)) {
                Uri btMmsUri = MmsFileProvider.CONTENT_URI.buildUpon()
                        .appendPath(Long.toString(handle)).build();
                Intent sentIntent = new Intent(ACTION_MESSAGE_SENT);
                // TODO: update the mmsMsgList <- done in pushMmsToFolder() but check
                sentIntent.setType("message/" + Long.toString(handle));
                sentIntent.putExtra(EXTRA_MESSAGE_SENT_MSG_TYPE, TYPE.MMS.ordinal());
                sentIntent.putExtra(EXTRA_MESSAGE_SENT_HANDLE, handle); // needed for notification
                sentIntent.putExtra(EXTRA_MESSAGE_SENT_TRANSPARENT, transparent);
                sentIntent.putExtra(EXTRA_MESSAGE_SENT_RETRY, retry);
                //sentIntent.setDataAndNormalize(btMmsUri);
                PendingIntent pendingSendIntent = PendingIntent.getBroadcast(mContext, 0,
                        sentIntent, 0);
                SmsManager.getDefault().sendMultimediaMessage(mContext,
                        btMmsUri, null/*locationUrl*/, null/*configOverrides*/,
                        pendingSendIntent);
            }
            return handle;
        } else {
            /* not allowed to push mms to anything but outbox/draft */
            throw  new IllegalArgumentException("Cannot push message to other " +
                    "folders than outbox/draft");
        }
    }

    private void moveDraftToOutbox(long handle) {
        moveMmsToFolder(handle, mResolver, Mms.MESSAGE_BOX_OUTBOX);
    }

    /**
     * Move a MMS to another folder.
     * @param handle the CP handle of the message to move
     * @param resolver the ContentResolver to use
     * @param folder the destination folder - use Mms.MESSAGE_BOX_xxx
     */
    private static void moveMmsToFolder(long handle, ContentResolver resolver, int folder) {
        /*Move message by changing the msg_box value in the content provider database */
        if (handle != -1) {
            String whereClause = " _id= " + handle;
            Uri uri = Mms.CONTENT_URI;
            Cursor queryResult = resolver.query(uri, null, whereClause, null, null);
            try {
                if (queryResult != null) {
                    if (queryResult.getCount() > 0) {
                        queryResult.moveToFirst();
                        ContentValues data = new ContentValues();
                        /* set folder to be outbox */
                        data.put(Mms.MESSAGE_BOX, folder);
                        resolver.update(uri, data, whereClause, null);
                        if (D) Log.d(TAG, "moved MMS message to " + getMmsFolderName(folder));
                    }
                } else {
                    Log.w(TAG, "Could not move MMS message to " + getMmsFolderName(folder));
                }
            } finally {
                if (queryResult != null) queryResult.close();
            }
        }
    }
    private long pushMmsToFolder(int folder, String to_address, BluetoothMapbMessageMime msg) {
        /**
         * strategy:
         * 1) parse msg into parts + header
         * 2) create thread id (abuse the ease of adding an SMS to get id for thread)
         * 3) push parts into content://mms/parts/ table
         * 3)
         */

        ContentValues values = new ContentValues();
        values.put(Mms.MESSAGE_BOX, folder);
        values.put(Mms.READ, 0);
        values.put(Mms.SEEN, 0);
        if(msg.getSubject() != null) {
            values.put(Mms.SUBJECT, msg.getSubject());
        } else {
            values.put(Mms.SUBJECT, "");
        }

        if(msg.getSubject() != null && msg.getSubject().length() > 0) {
            values.put(Mms.SUBJECT_CHARSET, 106);
        }
        values.put(Mms.CONTENT_TYPE, "application/vnd.wap.multipart.related");
        values.put(Mms.EXPIRY, 604800);
        values.put(Mms.MESSAGE_CLASS, PduHeaders.MESSAGE_CLASS_PERSONAL_STR);
        values.put(Mms.MESSAGE_TYPE, PduHeaders.MESSAGE_TYPE_SEND_REQ);
        values.put(Mms.MMS_VERSION, PduHeaders.CURRENT_MMS_VERSION);
        values.put(Mms.PRIORITY, PduHeaders.PRIORITY_NORMAL);
        values.put(Mms.READ_REPORT, PduHeaders.VALUE_NO);
        values.put(Mms.TRANSACTION_ID, "T"+ Long.toHexString(System.currentTimeMillis()));
        values.put(Mms.DELIVERY_REPORT, PduHeaders.VALUE_NO);
        values.put(Mms.LOCKED, 0);
        if(msg.getTextOnly() == true)
            values.put(Mms.TEXT_ONLY, true);
        values.put(Mms.MESSAGE_SIZE, msg.getSize());

        // Get thread id
        Set<String> recipients = new HashSet<String>();
        recipients.addAll(Arrays.asList(to_address));
        values.put(Mms.THREAD_ID, Telephony.Threads.getOrCreateThreadId(mContext, recipients));
        Uri uri = Mms.CONTENT_URI;

        synchronized (getMsgListMms()) {

            uri = mResolver.insert(uri, values);

            if (uri == null) {
                // unable to insert MMS
                Log.e(TAG, "Unabled to insert MMS " + values + "Uri: " + uri);
                return -1;
            }
            /* As we already have all the values we need, we could skip the query, but
               doing the query ensures we get any changes made by the content provider
               at insert. */
            Cursor c = mResolver.query(uri, MMS_PROJECTION_SHORT, null, null, null);
            try {
                if (c != null && c.moveToFirst()) {
                    long id = c.getLong(c.getColumnIndex(Mms._ID));
                    int type = c.getInt(c.getColumnIndex(Mms.MESSAGE_BOX));
                    int threadId = c.getInt(c.getColumnIndex(Mms.THREAD_ID));
                    int readStatus = c.getInt(c.getColumnIndex(Mms.READ));

                    /* We must filter out any actions made by the MCE. Add the new message to
                     * the list of known messages. */

                    Msg newMsg = new Msg(id, type, threadId, readStatus);
                    newMsg.localInitiatedSend = true;
                    getMsgListMms().put(id, newMsg);
                    c.close();
                }
            } finally {
                if (c != null) c.close();
            }
        } // Done adding changes, unlock access to mMsgListMms to allow sending MMS events again

        long handle = Long.parseLong(uri.getLastPathSegment());
        if (V) Log.v(TAG, " NEW URI " + uri.toString());

        try {
            if(msg.getMimeParts() == null) {
                /* Perhaps this message have been deleted, and no longer have any content,
                 * but only headers */
                Log.w(TAG, "No MMS parts present...");
            } else {
                if(V) Log.v(TAG, "Adding " + msg.getMimeParts().size()
                        + " parts to the data base.");
                for(MimePart part : msg.getMimeParts()) {
                    int count = 0;
                    count++;
                    values.clear();
                    if(part.mContentType != null &&
                            part.mContentType.toUpperCase().contains("TEXT")) {
                        values.put(Mms.Part.CONTENT_TYPE, "text/plain");
                        values.put(Mms.Part.CHARSET, 106);
                        if(part.mPartName != null) {
                            values.put(Mms.Part.FILENAME, part.mPartName);
                            values.put(Mms.Part.NAME, part.mPartName);
                        } else {
                            values.put(Mms.Part.FILENAME, "text_" + count +".txt");
                            values.put(Mms.Part.NAME, "text_" + count +".txt");
                        }
                        // Ensure we have "ci" set
                        if(part.mContentId != null) {
                            values.put(Mms.Part.CONTENT_ID, part.mContentId);
                        } else {
                            if(part.mPartName != null) {
                                values.put(Mms.Part.CONTENT_ID, "<" + part.mPartName + ">");
                            } else {
                                values.put(Mms.Part.CONTENT_ID, "<text_" + count + ">");
                            }
                        }
                        // Ensure we have "cl" set
                        if(part.mContentLocation != null) {
                            values.put(Mms.Part.CONTENT_LOCATION, part.mContentLocation);
                        } else {
                            if(part.mPartName != null) {
                                values.put(Mms.Part.CONTENT_LOCATION, part.mPartName + ".txt");
                            } else {
                                values.put(Mms.Part.CONTENT_LOCATION, "text_" + count + ".txt");
                            }
                        }

                        if(part.mContentDisposition != null) {
                            values.put(Mms.Part.CONTENT_DISPOSITION, part.mContentDisposition);
                        }
                        values.put(Mms.Part.TEXT, part.getDataAsString());
                        uri = Uri.parse(Mms.CONTENT_URI + "/" + handle + "/part");
                        uri = mResolver.insert(uri, values);
                        if(V) Log.v(TAG, "Added TEXT part");

                    } else if (part.mContentType != null &&
                            part.mContentType.toUpperCase().contains("SMIL")){
                        values.put(Mms.Part.SEQ, -1);
                        values.put(Mms.Part.CONTENT_TYPE, "application/smil");
                        if(part.mContentId != null) {
                            values.put(Mms.Part.CONTENT_ID, part.mContentId);
                        } else {
                            values.put(Mms.Part.CONTENT_ID, "<smil_" + count + ">");
                        }
                        if(part.mContentLocation != null) {
                            values.put(Mms.Part.CONTENT_LOCATION, part.mContentLocation);
                        } else {
                            values.put(Mms.Part.CONTENT_LOCATION, "smil_" + count + ".xml");
                        }

                        if(part.mContentDisposition != null)
                            values.put(Mms.Part.CONTENT_DISPOSITION, part.mContentDisposition);
                        values.put(Mms.Part.FILENAME, "smil.xml");
                        values.put(Mms.Part.NAME, "smil.xml");
                        values.put(Mms.Part.TEXT, new String(part.mData, "UTF-8"));

                        uri = Uri.parse(Mms.CONTENT_URI+ "/" + handle + "/part");
                        uri = mResolver.insert(uri, values);
                        if (V) Log.v(TAG, "Added SMIL part");

                    }else /*VIDEO/AUDIO/IMAGE*/ {
                        writeMmsDataPart(handle, part, count);
                        if (V) Log.v(TAG, "Added OTHER part");
                    }
                    if (uri != null){
                        if (V) Log.v(TAG, "Added part with content-type: " + part.mContentType
                                + " to Uri: " + uri.toString());
                    }
                }
            }
        } catch (UnsupportedEncodingException e) {
            Log.w(TAG, e);
        } catch (IOException e) {
            Log.w(TAG, e);
        }

        values.clear();
        values.put(Mms.Addr.CONTACT_ID, "null");
        values.put(Mms.Addr.ADDRESS, "insert-address-token");
        values.put(Mms.Addr.TYPE, BluetoothMapContent.MMS_FROM);
        values.put(Mms.Addr.CHARSET, 106);

        uri = Uri.parse(Mms.CONTENT_URI + "/"  + handle + "/addr");
        uri = mResolver.insert(uri, values);
        if (uri != null && V){
            Log.v(TAG, " NEW URI " + uri.toString());
        }

        values.clear();
        values.put(Mms.Addr.CONTACT_ID, "null");
        values.put(Mms.Addr.ADDRESS, to_address);
        values.put(Mms.Addr.TYPE, BluetoothMapContent.MMS_TO);
        values.put(Mms.Addr.CHARSET, 106);

        uri = Uri.parse(Mms.CONTENT_URI + "/"  + handle + "/addr");
        uri = mResolver.insert(uri, values);
        if (uri != null && V){
            Log.v(TAG, " NEW URI " + uri.toString());
        }
        return handle;
    }


    private void writeMmsDataPart(long handle, MimePart part, int count) throws IOException{
        ContentValues values = new ContentValues();
        values.put(Mms.Part.MSG_ID, handle);
        if(part.mContentType != null) {
            values.put(Mms.Part.CONTENT_TYPE, part.mContentType);
        } else {
            Log.w(TAG, "MMS has no CONTENT_TYPE for part " + count);
        }
        if(part.mContentId != null) {
            values.put(Mms.Part.CONTENT_ID, part.mContentId);
        } else {
            if(part.mPartName != null) {
                values.put(Mms.Part.CONTENT_ID, "<" + part.mPartName + ">");
            } else {
                values.put(Mms.Part.CONTENT_ID, "<part_" + count + ">");
            }
        }

        if(part.mContentLocation != null) {
            values.put(Mms.Part.CONTENT_LOCATION, part.mContentLocation);
        } else {
            if(part.mPartName != null) {
                values.put(Mms.Part.CONTENT_LOCATION, part.mPartName + ".dat");
            } else {
                values.put(Mms.Part.CONTENT_LOCATION, "part_" + count + ".dat");
            }
        }
        if(part.mContentDisposition != null)
            values.put(Mms.Part.CONTENT_DISPOSITION, part.mContentDisposition);
        if(part.mPartName != null) {
            values.put(Mms.Part.FILENAME, part.mPartName);
            values.put(Mms.Part.NAME, part.mPartName);
        } else {
            /* We must set at least one part identifier */
            values.put(Mms.Part.FILENAME, "part_" + count + ".dat");
            values.put(Mms.Part.NAME, "part_" + count + ".dat");
        }
        Uri partUri = Uri.parse(Mms.CONTENT_URI + "/" + handle + "/part");
        Uri res = mResolver.insert(partUri, values);

        // Add data to part
        OutputStream os = mResolver.openOutputStream(res);
        os.write(part.mData);
        os.close();
    }


    public void sendMessage(PushMsgInfo msgInfo, String msgBody) {

        SmsManager smsMng = SmsManager.getDefault();
        ArrayList<String> parts = smsMng.divideMessage(msgBody);
        msgInfo.parts = parts.size();
        // We add a time stamp to differentiate delivery reports from each other for resent messages
        msgInfo.timestamp = Calendar.getInstance().getTime().getTime();
        msgInfo.partsDelivered = 0;
        msgInfo.partsSent = 0;

        ArrayList<PendingIntent> deliveryIntents = new ArrayList<PendingIntent>(msgInfo.parts);
        ArrayList<PendingIntent> sentIntents = new ArrayList<PendingIntent>(msgInfo.parts);

        /*       We handle the SENT intent in the MAP service, as this object
         *       is destroyed at disconnect, hence if a disconnect occur while sending
         *       a message, there is no intent handler to move the message from outbox
         *       to the correct folder.
         *       The correct solution would be to create a service that will start based on
         *       the intent, if BT is turned off. */

        if (parts != null && parts.size() > 0) {
            for (int i = 0; i < msgInfo.parts; i++) {
                Intent intentDelivery, intentSent;

                intentDelivery = new Intent(ACTION_MESSAGE_DELIVERY, null);
                /* Add msgId and part number to ensure the intents are different, and we
                 * thereby get an intent for each msg part.
                 * setType is needed to create different intents for each message id/ time stamp,
                 * as the extras are not used when comparing. */
                intentDelivery.setType("message/" + Long.toString(msgInfo.id) +
                        msgInfo.timestamp + i);
                intentDelivery.putExtra(EXTRA_MESSAGE_SENT_HANDLE, msgInfo.id);
                intentDelivery.putExtra(EXTRA_MESSAGE_SENT_TIMESTAMP, msgInfo.timestamp);
                PendingIntent pendingIntentDelivery = PendingIntent.getBroadcast(mContext, 0,
                        intentDelivery, PendingIntent.FLAG_UPDATE_CURRENT);

                intentSent = new Intent(ACTION_MESSAGE_SENT, null);
                /* Add msgId and part number to ensure the intents are different, and we
                 * thereby get an intent for each msg part.
                 * setType is needed to create different intents for each message id/ time stamp,
                 * as the extras are not used when comparing. */
                intentSent.setType("message/" + Long.toString(msgInfo.id) + msgInfo.timestamp + i);
                intentSent.putExtra(EXTRA_MESSAGE_SENT_HANDLE, msgInfo.id);
                intentSent.putExtra(EXTRA_MESSAGE_SENT_URI, msgInfo.uri.toString());
                intentSent.putExtra(EXTRA_MESSAGE_SENT_RETRY, msgInfo.retry);
                intentSent.putExtra(EXTRA_MESSAGE_SENT_TRANSPARENT, msgInfo.transparent);

                PendingIntent pendingIntentSent = PendingIntent.getBroadcast(mContext, 0,
                        intentSent, PendingIntent.FLAG_UPDATE_CURRENT);

                // We use the same pending intent for all parts, but do not set the one shot flag.
                deliveryIntents.add(pendingIntentDelivery);
                sentIntents.add(pendingIntentSent);
            }

            Log.d(TAG, "sendMessage to " + msgInfo.phone);

            smsMng.sendMultipartTextMessage(msgInfo.phone, null, parts, sentIntents,
                    deliveryIntents);
        }
    }

    private class SmsBroadcastReceiver extends BroadcastReceiver {
        private final String[] ID_PROJECTION = new String[] { Sms._ID };
        private final Uri UPDATE_STATUS_URI = Uri.withAppendedPath(Sms.CONTENT_URI, "/status");

        public void register() {
            Handler handler = new Handler(Looper.getMainLooper());

            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(ACTION_MESSAGE_DELIVERY);
            try{
                intentFilter.addDataType("message/*");
            } catch (MalformedMimeTypeException e) {
                Log.e(TAG, "Wrong mime type!!!", e);
            }

            mContext.registerReceiver(this, intentFilter, null, handler);
        }

        public void unregister() {
            try {
                mContext.unregisterReceiver(this);
            } catch (IllegalArgumentException e) {
                /* do nothing */
            }
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            long handle = intent.getLongExtra(EXTRA_MESSAGE_SENT_HANDLE, -1);
            PushMsgInfo msgInfo = mPushMsgList.get(handle);

            Log.d(TAG, "onReceive: action"  + action);

            if (msgInfo == null) {
                Log.d(TAG, "onReceive: no msgInfo found for handle " + handle);
                return;
            }

            if (action.equals(ACTION_MESSAGE_SENT)) {
                int result = intent.getIntExtra(EXTRA_MESSAGE_SENT_RESULT,
                        Activity.RESULT_CANCELED);
                msgInfo.partsSent++;
                if(result != Activity.RESULT_OK) {
                    /* If just one of the parts in the message fails, we need to send the
                     * entire message again
                     */
                    msgInfo.failedSent = true;
                }
                if(D) Log.d(TAG, "onReceive: msgInfo.partsSent = " + msgInfo.partsSent
                        + ", msgInfo.parts = " + msgInfo.parts + " result = " + result);

                if (msgInfo.partsSent == msgInfo.parts) {
                    actionMessageSent(context, intent, msgInfo);
                }
            } else if (action.equals(ACTION_MESSAGE_DELIVERY)) {
                long timestamp = intent.getLongExtra(EXTRA_MESSAGE_SENT_TIMESTAMP, 0);
                int status = -1;
                if(msgInfo.timestamp == timestamp) {
                    msgInfo.partsDelivered++;
                    byte[] pdu = intent.getByteArrayExtra("pdu");
                    String format = intent.getStringExtra("format");

                    SmsMessage message = SmsMessage.createFromPdu(pdu, format);
                    if (message == null) {
                        Log.d(TAG, "actionMessageDelivery: Can't get message from pdu");
                        return;
                    }
                    status = message.getStatus();
                    if(status != 0/*0 is success*/) {
                        msgInfo.statusDelivered = status;
                        if(D) Log.d(TAG, "msgInfo.statusDelivered = " + status);
                        Sms.moveMessageToFolder(mContext, msgInfo.uri, Sms.MESSAGE_TYPE_FAILED, 0);
                    } else {
                        Sms.moveMessageToFolder(mContext, msgInfo.uri, Sms.MESSAGE_TYPE_SENT, 0);
                    }
                }
                if (msgInfo.partsDelivered == msgInfo.parts) {
                    actionMessageDelivery(context, intent, msgInfo);
                }
            } else {
                Log.d(TAG, "onReceive: Unknown action " + action);
            }
        }

        private void actionMessageSent(Context context, Intent intent, PushMsgInfo msgInfo) {
            /* As the MESSAGE_SENT intent is forwarded from the MAP service, we use the intent
             * to carry the result, as getResult() will not return the correct value.
             */
            boolean delete = false;

            if(D) Log.d(TAG,"actionMessageSent(): msgInfo.failedSent = " + msgInfo.failedSent);

            msgInfo.sendInProgress = false;

            if (msgInfo.failedSent == false) {
                if(D) Log.d(TAG, "actionMessageSent: result OK");
                if (msgInfo.transparent == 0) {
                    if (!Sms.moveMessageToFolder(context, msgInfo.uri,
                            Sms.MESSAGE_TYPE_SENT, 0)) {
                        Log.w(TAG, "Failed to move " + msgInfo.uri + " to SENT");
                    }
                } else {
                    delete = true;
                }

                Event evt = new Event(EVENT_TYPE_SENDING_SUCCESS, msgInfo.id,
                        getSmsFolderName(Sms.MESSAGE_TYPE_SENT), null, mSmsType);
                sendEvent(evt);

            } else {
                if (msgInfo.retry == 1) {
                    /* Notify failure, but keep message in outbox for resending */
                    msgInfo.resend = true;
                    msgInfo.partsSent = 0; // Reset counter for the retry
                    msgInfo.failedSent = false;
                    Event evt = new Event(EVENT_TYPE_SENDING_FAILURE, msgInfo.id,
                            getSmsFolderName(Sms.MESSAGE_TYPE_OUTBOX), null, mSmsType);
                    sendEvent(evt);
                } else {
                    if (msgInfo.transparent == 0) {
                        if (!Sms.moveMessageToFolder(context, msgInfo.uri,
                                Sms.MESSAGE_TYPE_FAILED, 0)) {
                            Log.w(TAG, "Failed to move " + msgInfo.uri + " to FAILED");
                        }
                    } else {
                        delete = true;
                    }

                    Event evt = new Event(EVENT_TYPE_SENDING_FAILURE, msgInfo.id,
                            getSmsFolderName(Sms.MESSAGE_TYPE_FAILED), null, mSmsType);
                    sendEvent(evt);
                }
            }

            if (delete == true) {
                /* Delete from Observer message list to avoid delete notifications */
                synchronized(getMsgListSms()) {
                    getMsgListSms().remove(msgInfo.id);
                }

                /* Delete from DB */
                mResolver.delete(msgInfo.uri, null, null);
            }
        }

        private void actionMessageDelivery(Context context, Intent intent, PushMsgInfo msgInfo) {
            Uri messageUri = intent.getData();
            msgInfo.sendInProgress = false;

            Cursor cursor = mResolver.query(msgInfo.uri, ID_PROJECTION, null, null, null);

            try {
                if (cursor.moveToFirst()) {
                    int messageId = cursor.getInt(0);

                    Uri updateUri = ContentUris.withAppendedId(UPDATE_STATUS_URI, messageId);

                    if(D) Log.d(TAG, "actionMessageDelivery: uri=" + messageUri + ", status="
                            + msgInfo.statusDelivered);

                    ContentValues contentValues = new ContentValues(2);

                    contentValues.put(Sms.STATUS, msgInfo.statusDelivered);
                    contentValues.put(Inbox.DATE_SENT, System.currentTimeMillis());
                    mResolver.update(updateUri, contentValues, null, null);
                } else {
                    Log.d(TAG, "Can't find message for status update: " + messageUri);
                }
            } finally {
                if (cursor != null) cursor.close();
            }

            if (msgInfo.statusDelivered == 0) {
                Event evt = new Event(EVENT_TYPE_DELEVERY_SUCCESS, msgInfo.id,
                        getSmsFolderName(Sms.MESSAGE_TYPE_SENT), null, mSmsType);
                sendEvent(evt);
            } else {
                Event evt = new Event(EVENT_TYPE_DELIVERY_FAILURE, msgInfo.id,
                        getSmsFolderName(Sms.MESSAGE_TYPE_SENT), null, mSmsType);
                sendEvent(evt);
            }

            mPushMsgList.remove(msgInfo.id);
        }
    }

    private class CeBroadcastReceiver extends BroadcastReceiver {
        public void register() {
            UserManager manager = UserManager.get(mContext);
            if (manager == null || manager.isUserUnlocked()) {
                mStorageUnlocked = true;
                return;
            }

            Handler handler = new Handler(Looper.getMainLooper());
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(Intent.ACTION_BOOT_COMPLETED);
            mContext.registerReceiver(this, intentFilter, null, handler);
        }

        public void unregister() {
            try {
                mContext.unregisterReceiver(this);
            } catch (IllegalArgumentException e) {
                /* do nothing */
            }
        }

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "onReceive: action"  + action);

            if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
                try {
                    initMsgList();
                } catch (RemoteException e) {
                    Log.e(TAG, "Error initializing SMS/MMS message lists.");
                }

                for (String folder : FOLDER_SMS_MAP.values()) {
                    Event evt = new Event(EVENT_TYPE_NEW, -1, folder, mSmsType);
                    sendEvent(evt);
                }
                mStorageUnlocked = true;
                /* After unlock this BroadcastReceiver is never needed */
                unregister();
            } else {
                Log.d(TAG, "onReceive: Unknown action " + action);
            }
        }
    }

    /**
     * Handle MMS sent intents in disconnected(MNS) state, where we do not need to send any
     * notifications.
     * @param context The context to use for provider operations
     * @param intent The intent received
     * @param result The result
     */
    static public void actionMmsSent(Context context, Intent intent, int result,
            Map<Long, Msg> mmsMsgList) {
        /*
         * if transparent:
         *   delete message and send notification(regardless of result)
         * else
         *   Result == Success:
         *     move to sent folder (will trigger notification)
         *   Result == Fail:
         *     move to outbox (send delivery fail notification)
         */
        if(D) Log.d(TAG,"actionMmsSent()");
        int transparent = intent.getIntExtra(EXTRA_MESSAGE_SENT_TRANSPARENT, 0);
        long handle = intent.getLongExtra(EXTRA_MESSAGE_SENT_HANDLE, -1);
        if(handle < 0) {
            Log.w(TAG, "Intent received for an invalid handle");
            return;
        }
        ContentResolver resolver = context.getContentResolver();
        if(transparent == 1) {
            /* The specification is a bit unclear about the transparent flag. If it is set
             * no copy of the message shall be kept in the send folder after the message
             * was send, but in the case of a send error, it is unclear what to do.
             * As it will not be transparent if we keep the message in any folder,
             * we delete the message regardless of the result.
             * If we however do have a MNS connection we need to send a notification. */
            Uri uri = ContentUris.withAppendedId(Mms.CONTENT_URI, handle);
            /* Delete from observer message list to avoid delete notifications */
            if(mmsMsgList != null) {
                synchronized(mmsMsgList) {
                    mmsMsgList.remove(handle);
                }
            }
            /* Delete message */
            if(D) Log.d(TAG,"Transparent in use - delete");
            resolver.delete(uri, null, null);
        } else if (result == Activity.RESULT_OK) {
            /* This will trigger a notification */
            moveMmsToFolder(handle, resolver, Mms.MESSAGE_BOX_SENT);
        } else {
            if(mmsMsgList != null) {
                synchronized(mmsMsgList) {
                    Msg msg = mmsMsgList.get(handle);
                    if(msg != null) {
                    msg.type=Mms.MESSAGE_BOX_OUTBOX;
                    }
                }
            }
            /* Hand further retries over to the MMS application */
            moveMmsToFolder(handle, resolver, Mms.MESSAGE_BOX_OUTBOX);
        }
    }

    static public void actionMessageSentDisconnected(Context context, Intent intent, int result) {
        TYPE type = TYPE.fromOrdinal(
        intent.getIntExtra(EXTRA_MESSAGE_SENT_MSG_TYPE, TYPE.NONE.ordinal()));
        if(type == TYPE.MMS) {
            actionMmsSent(context, intent, result, null);
        } else {
            actionSmsSentDisconnected(context, intent, result);
        }
    }

    static public void actionSmsSentDisconnected(Context context, Intent intent, int result) {
        /* Check permission for message deletion. */
        if ((Binder.getCallingPid() != Process.myPid()) ||
            (context.checkCallingOrSelfPermission("android.Manifest.permission.WRITE_SMS")
                    != PackageManager.PERMISSION_GRANTED)) {
            Log.w(TAG, "actionSmsSentDisconnected: Not allowed to delete SMS/MMS messages");
            return;
        }

        boolean delete = false;
        //int retry = intent.getIntExtra(EXTRA_MESSAGE_SENT_RETRY, 0);
        int transparent = intent.getIntExtra(EXTRA_MESSAGE_SENT_TRANSPARENT, 0);
        String uriString = intent.getStringExtra(EXTRA_MESSAGE_SENT_URI);
        if(uriString == null) {
            // Nothing we can do about it, just bail out
            return;
        }
        Uri uri = Uri.parse(uriString);

        if (result == Activity.RESULT_OK) {
            Log.d(TAG, "actionMessageSentDisconnected: result OK");
            if (transparent == 0) {
                if (!Sms.moveMessageToFolder(context, uri,
                        Sms.MESSAGE_TYPE_SENT, 0)) {
                    Log.d(TAG, "Failed to move " + uri + " to SENT");
                }
            } else {
                delete = true;
            }
        } else {
            /*if (retry == 1) {
                 The retry feature only works while connected, else we fail the send,
             * and move the message to failed, to let the user/app resend manually later.
            } else */{
                if (transparent == 0) {
                    if (!Sms.moveMessageToFolder(context, uri,
                            Sms.MESSAGE_TYPE_FAILED, 0)) {
                        Log.d(TAG, "Failed to move " + uri + " to FAILED");
                    }
                } else {
                    delete = true;
                }
            }
        }

        if (delete) {
            /* Delete from DB */
            ContentResolver resolver = context.getContentResolver();
            if (resolver != null) {
                resolver.delete(uri, null, null);
            } else {
                Log.w(TAG, "Unable to get resolver");
            }
        }
    }

    private void registerPhoneServiceStateListener() {
        TelephonyManager tm = (TelephonyManager)mContext.getSystemService(
                Context.TELEPHONY_SERVICE);
        tm.listen(mPhoneListener, PhoneStateListener.LISTEN_SERVICE_STATE);
    }

    private void unRegisterPhoneServiceStateListener() {
        TelephonyManager tm = (TelephonyManager)mContext.getSystemService(
                Context.TELEPHONY_SERVICE);
        tm.listen(mPhoneListener, PhoneStateListener.LISTEN_NONE);
    }

    private void resendPendingMessages() {
        /* Send pending messages in outbox */
        String where = "type = " + Sms.MESSAGE_TYPE_OUTBOX;
        UserManager manager = UserManager.get(mContext);
        if (manager == null || !manager.isUserUnlocked()) return;
        Cursor c = mResolver.query(Sms.CONTENT_URI, SMS_PROJECTION, where, null,
                null);
        try {
            if (c != null && c.moveToFirst()) {
                do {
                    long id = c.getLong(c.getColumnIndex(Sms._ID));
                    String msgBody = c.getString(c.getColumnIndex(Sms.BODY));
                    PushMsgInfo msgInfo = mPushMsgList.get(id);
                    if (msgInfo == null || msgInfo.resend == false ||
                            msgInfo.sendInProgress == true) {
                        continue;
                    }
                    msgInfo.sendInProgress = true;
                    sendMessage(msgInfo, msgBody);
                } while (c.moveToNext());
            }
        } finally {
            if (c != null) c.close();
        }


    }

    private void failPendingMessages() {
        /* Move pending messages from outbox to failed */
        String where = "type = " + Sms.MESSAGE_TYPE_OUTBOX;
        Cursor c = mResolver.query(Sms.CONTENT_URI, SMS_PROJECTION, where, null,
                null);
        try {
            if (c != null && c.moveToFirst()) {
                do {
                    long id = c.getLong(c.getColumnIndex(Sms._ID));
                    String msgBody = c.getString(c.getColumnIndex(Sms.BODY));
                    PushMsgInfo msgInfo = mPushMsgList.get(id);
                    if (msgInfo == null || msgInfo.resend == false) {
                        continue;
                    }
                    Sms.moveMessageToFolder(mContext, msgInfo.uri,
                            Sms.MESSAGE_TYPE_FAILED, 0);
                } while (c.moveToNext());
            }
        } finally {
            if (c != null) c.close();
        }

    }

    private void removeDeletedMessages() {
        /* Remove messages from virtual "deleted" folder (thread_id -1) */
        mResolver.delete(Sms.CONTENT_URI,
                "thread_id = " + DELETED_THREAD_ID, null);
    }

    private PhoneStateListener mPhoneListener = new PhoneStateListener() {
        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
            Log.d(TAG, "Phone service state change: " + serviceState.getState());
            if (serviceState.getState() == ServiceState.STATE_IN_SERVICE) {
                resendPendingMessages();
            }
        }
    };

    public void init() {
        if (mSmsBroadcastReceiver != null) {
            mSmsBroadcastReceiver.register();
        }

        if (mCeBroadcastReceiver != null) {
            mCeBroadcastReceiver.register();
        }

        registerPhoneServiceStateListener();
        mInitialized = true;
    }

    public void deinit() {
        mInitialized = false;
        unregisterObserver();
        if (mSmsBroadcastReceiver != null) {
            mSmsBroadcastReceiver.unregister();
        }
        unRegisterPhoneServiceStateListener();
        failPendingMessages();
        removeDeletedMessages();
    }

    public boolean handleSmsSendIntent(Context context, Intent intent){
        TYPE type = TYPE.fromOrdinal(
            intent.getIntExtra(EXTRA_MESSAGE_SENT_MSG_TYPE, TYPE.NONE.ordinal()));
        if(type == TYPE.MMS) {
            return handleMmsSendIntent(context, intent);
        } else {
            if(mInitialized) {
                mSmsBroadcastReceiver.onReceive(context, intent);
                return true;
            }
        }
        return false;
    }

    public boolean handleMmsSendIntent(Context context, Intent intent){
        if(D) Log.w(TAG, "handleMmsSendIntent()");
        if(mMnsClient.isConnected() == false) {
            // No need to handle notifications, just use default handling
            if(D) Log.w(TAG, "MNS not connected - use static handling");
            return false;
        }
        long handle = intent.getLongExtra(EXTRA_MESSAGE_SENT_HANDLE, -1);
        int result = intent.getIntExtra(EXTRA_MESSAGE_SENT_RESULT, Activity.RESULT_CANCELED);
        actionMmsSent(context, intent, result, getMsgListMms());
        if(handle < 0) {
            Log.w(TAG, "Intent received for an invalid handle");
            return true;
        }
        if(result != Activity.RESULT_OK) {
            if(mObserverRegistered) {
                Event evt = new Event(EVENT_TYPE_SENDING_FAILURE, handle,
                        getMmsFolderName(Mms.MESSAGE_BOX_OUTBOX), null, TYPE.MMS);
                sendEvent(evt);
            }
        } else {
            int transparent = intent.getIntExtra(EXTRA_MESSAGE_SENT_TRANSPARENT, 0);
            if(transparent != 0) {
                if(mObserverRegistered) {
                    Event evt = new Event(EVENT_TYPE_SENDING_SUCCESS, handle,
                            getMmsFolderName(Mms.MESSAGE_BOX_OUTBOX), null, TYPE.MMS);
                    sendEvent(evt);
                }
            }
        }
        return true;
    }

}
