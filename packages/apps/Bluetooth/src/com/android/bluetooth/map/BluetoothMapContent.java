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
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.net.Uri.Builder;
import android.os.ParcelFileDescriptor;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Sms;
import android.provider.Telephony.MmsSms;
import android.provider.Telephony.CanonicalAddressesColumns;
import android.provider.Telephony.Threads;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.util.Rfc822Token;
import android.text.util.Rfc822Tokenizer;
import android.text.TextUtils;
import android.util.Log;

import com.android.bluetooth.SignedLongLong;
import com.android.bluetooth.map.BluetoothMapUtils.TYPE;
import com.android.bluetooth.map.BluetoothMapbMessageMime.MimePart;
import com.android.bluetooth.mapapi.BluetoothMapContract;
import com.android.bluetooth.mapapi.BluetoothMapContract.ConversationColumns;
import com.google.android.mms.pdu.CharacterSets;
import com.google.android.mms.pdu.PduHeaders;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

@TargetApi(19)
public class BluetoothMapContent {

    private static final String TAG = "BluetoothMapContent";

    private static final boolean D = BluetoothMapService.DEBUG;
    private static final boolean V = BluetoothMapService.VERBOSE;

    // Parameter Mask for selection of parameters to return in listings
    private static final int MASK_SUBJECT               = 0x00000001;
    private static final int MASK_DATETIME              = 0x00000002;
    private static final int MASK_SENDER_NAME           = 0x00000004;
    private static final int MASK_SENDER_ADDRESSING     = 0x00000008;
    private static final int MASK_RECIPIENT_NAME        = 0x00000010;
    private static final int MASK_RECIPIENT_ADDRESSING  = 0x00000020;
    private static final int MASK_TYPE                  = 0x00000040;
    private static final int MASK_SIZE                  = 0x00000080;
    private static final int MASK_RECEPTION_STATUS      = 0x00000100;
    private static final int MASK_TEXT                  = 0x00000200;
    private static final int MASK_ATTACHMENT_SIZE       = 0x00000400;
    private static final int MASK_PRIORITY              = 0x00000800;
    private static final int MASK_READ                  = 0x00001000;
    private static final int MASK_SENT                  = 0x00002000;
    private static final int MASK_PROTECTED             = 0x00004000;
    private static final int MASK_REPLYTO_ADDRESSING    = 0x00008000;
    // TODO: Duplicate in proposed spec
    // private static final int MASK_RECEPTION_STATE       = 0x00010000;
    private static final int MASK_DELIVERY_STATUS       = 0x00020000;
    private static final int MASK_CONVERSATION_ID       = 0x00040000;
    private static final int MASK_CONVERSATION_NAME     = 0x00080000;
    private static final int MASK_FOLDER_TYPE           = 0x00100000;
    // TODO: about to be removed from proposed spec
    // private static final int MASK_SEQUENCE_NUMBER       = 0x00200000;
    private static final int MASK_ATTACHMENT_MIME       = 0x00400000;

    private static final int  CONVO_PARAM_MASK_CONVO_NAME              = 0x00000001;
    private static final int  CONVO_PARAM_MASK_CONVO_LAST_ACTIVITY     = 0x00000002;
    private static final int  CONVO_PARAM_MASK_CONVO_READ_STATUS       = 0x00000004;
    private static final int  CONVO_PARAM_MASK_CONVO_VERSION_COUNTER   = 0x00000008;
    private static final int  CONVO_PARAM_MASK_CONVO_SUMMARY           = 0x00000010;
    private static final int  CONVO_PARAM_MASK_PARTTICIPANTS           = 0x00000020;
    private static final int  CONVO_PARAM_MASK_PART_UCI                = 0x00000040;
    private static final int  CONVO_PARAM_MASK_PART_DISP_NAME          = 0x00000080;
    private static final int  CONVO_PARAM_MASK_PART_CHAT_STATE         = 0x00000100;
    private static final int  CONVO_PARAM_MASK_PART_LAST_ACTIVITY      = 0x00000200;
    private static final int  CONVO_PARAM_MASK_PART_X_BT_UID           = 0x00000400;
    private static final int  CONVO_PARAM_MASK_PART_NAME               = 0x00000800;
    private static final int  CONVO_PARAM_MASK_PART_PRESENCE           = 0x00001000;
    private static final int  CONVO_PARAM_MASK_PART_PRESENCE_TEXT      = 0x00002000;
    private static final int  CONVO_PARAM_MASK_PART_PRIORITY           = 0x00004000;

    /* Default values for omitted or 0 parameterMask application parameters */
    // MAP specification states that the default value for parameter mask are
    // the #REQUIRED attributes in the DTD, and not all enabled
    public static final long PARAMETER_MASK_ALL_ENABLED = 0xFFFFFFFFL;
    public static final long CONVO_PARAMETER_MASK_ALL_ENABLED = 0xFFFFFFFFL;
    public static final long CONVO_PARAMETER_MASK_DEFAULT =
            CONVO_PARAM_MASK_CONVO_NAME |
            CONVO_PARAM_MASK_PARTTICIPANTS |
            CONVO_PARAM_MASK_PART_UCI |
            CONVO_PARAM_MASK_PART_DISP_NAME;




    private static final int FILTER_READ_STATUS_UNREAD_ONLY = 0x01;
    private static final int FILTER_READ_STATUS_READ_ONLY   = 0x02;
    private static final int FILTER_READ_STATUS_ALL         = 0x00;

    /* Type of MMS address. From Telephony.java it must be one of PduHeaders.BCC, */
    /* PduHeaders.CC, PduHeaders.FROM, PduHeaders.TO. These are from PduHeaders.java */
    public static final int MMS_FROM    = 0x89;
    public static final int MMS_TO      = 0x97;
    public static final int MMS_BCC     = 0x81;
    public static final int MMS_CC      = 0x82;

    /* OMA-TS-MMS-ENC defined many types in X-Mms-Message-Type.
       Only m-send-req (128) m-retrieve-conf (132), m-notification-ind (130)
       are interested by user */
    private static final String INTERESTED_MESSAGE_TYPE_CLAUSE = String
            .format("( %s = %d OR %s = %d OR %s = %d )", Mms.MESSAGE_TYPE,
            PduHeaders.MESSAGE_TYPE_SEND_REQ, Mms.MESSAGE_TYPE,
            PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF, Mms.MESSAGE_TYPE,
            PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND );

    public static final String INSERT_ADDRES_TOKEN = "insert-address-token";

    private final Context mContext;
    private final ContentResolver mResolver;
    private final String mBaseUri;
    private final BluetoothMapAccountItem mAccount;
    /* The MasInstance reference is used to update persistent (over a connection) version counters*/
    private final BluetoothMapMasInstance mMasInstance;
    private String mMessageVersion = BluetoothMapUtils.MAP_V10_STR;

    private int mRemoteFeatureMask = BluetoothMapUtils.MAP_FEATURE_DEFAULT_BITMASK;
    private int mMsgListingVersion = BluetoothMapUtils.MAP_MESSAGE_LISTING_FORMAT_V10;

    static final String[] SMS_PROJECTION = new String[] {
        BaseColumns._ID,
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

    static final String[] MMS_PROJECTION = new String[] {
        BaseColumns._ID,
        Mms.THREAD_ID,
        Mms.MESSAGE_ID,
        Mms.MESSAGE_SIZE,
        Mms.SUBJECT,
        Mms.CONTENT_TYPE,
        Mms.TEXT_ONLY,
        Mms.DATE,
        Mms.DATE_SENT,
        Mms.READ,
        Mms.MESSAGE_BOX,
        Mms.STATUS,
        Mms.PRIORITY,
    };

    static final String[] SMS_CONVO_PROJECTION = new String[] {
        BaseColumns._ID,
        Sms.THREAD_ID,
        Sms.ADDRESS,
        Sms.DATE,
        Sms.READ,
        Sms.TYPE,
        Sms.STATUS,
        Sms.LOCKED,
        Sms.ERROR_CODE
    };

    static final String[] MMS_CONVO_PROJECTION = new String[] {
        BaseColumns._ID,
        Mms.THREAD_ID,
        Mms.MESSAGE_ID,
        Mms.MESSAGE_SIZE,
        Mms.SUBJECT,
        Mms.CONTENT_TYPE,
        Mms.TEXT_ONLY,
        Mms.DATE,
        Mms.DATE_SENT,
        Mms.READ,
        Mms.MESSAGE_BOX,
        Mms.STATUS,
        Mms.PRIORITY,
        Mms.Addr.ADDRESS
    };

    /* CONVO LISTING projections and column indexes */
    private static final String[] MMS_SMS_THREAD_PROJECTION = {
        Threads._ID,
        Threads.DATE,
        Threads.SNIPPET,
        Threads.SNIPPET_CHARSET,
        Threads.READ,
        Threads.RECIPIENT_IDS
    };

    private static final String[] CONVO_VERSION_PROJECTION = new String[] {
        /* Thread information */
        ConversationColumns.THREAD_ID,
        ConversationColumns.THREAD_NAME,
        ConversationColumns.READ_STATUS,
        ConversationColumns.LAST_THREAD_ACTIVITY,
        ConversationColumns.SUMMARY,
    };

    /* Optimize the Cursor access to avoid the need to do a getColumnIndex() */
    private static final int MMS_SMS_THREAD_COL_ID;
    private static final int MMS_SMS_THREAD_COL_DATE;
    private static final int MMS_SMS_THREAD_COL_SNIPPET;
    private static final int MMS_SMS_THREAD_COL_SNIPPET_CS;
    private static final int MMS_SMS_THREAD_COL_READ;
    private static final int MMS_SMS_THREAD_COL_RECIPIENT_IDS;
    static {
        // TODO: This might not work, if the projection is mapped in the content provider...
        //       Change to init at first query? (Current use in the AOSP code is hard coded values
        //       unrelated to the projection used)
        List<String> projection = Arrays.asList(MMS_SMS_THREAD_PROJECTION);
        MMS_SMS_THREAD_COL_ID = projection.indexOf(Threads._ID);
        MMS_SMS_THREAD_COL_DATE = projection.indexOf(Threads.DATE);
        MMS_SMS_THREAD_COL_SNIPPET = projection.indexOf(Threads.SNIPPET);
        MMS_SMS_THREAD_COL_SNIPPET_CS = projection.indexOf(Threads.SNIPPET_CHARSET);
        MMS_SMS_THREAD_COL_READ = projection.indexOf(Threads.READ);
        MMS_SMS_THREAD_COL_RECIPIENT_IDS = projection.indexOf(Threads.RECIPIENT_IDS);
    }

    private class FilterInfo {
        public static final int TYPE_SMS    = 0;
        public static final int TYPE_MMS    = 1;
        public static final int TYPE_EMAIL  = 2;
        public static final int TYPE_IM     = 3;

        // TODO: Change to ENUM, to ensure correct usage
        int mMsgType = TYPE_SMS;
        int mPhoneType = 0;
        String mPhoneNum = null;
        String mPhoneAlphaTag = null;
        /*column indices used to optimize queries */
        public int mMessageColId                = -1;
        public int mMessageColDate              = -1;
        public int mMessageColBody              = -1;
        public int mMessageColSubject           = -1;
        public int mMessageColFolder            = -1;
        public int mMessageColRead              = -1;
        public int mMessageColSize              = -1;
        public int mMessageColFromAddress       = -1;
        public int mMessageColToAddress         = -1;
        public int mMessageColCcAddress         = -1;
        public int mMessageColBccAddress        = -1;
        public int mMessageColReplyTo           = -1;
        public int mMessageColAccountId         = -1;
        public int mMessageColAttachment        = -1;
        public int mMessageColAttachmentSize    = -1;
        public int mMessageColAttachmentMime    = -1;
        public int mMessageColPriority          = -1;
        public int mMessageColProtected         = -1;
        public int mMessageColReception         = -1;
        public int mMessageColDelivery          = -1;
        public int mMessageColThreadId          = -1;
        public int mMessageColThreadName        = -1;

        public int mSmsColFolder            = -1;
        public int mSmsColRead              = -1;
        public int mSmsColId                = -1;
        public int mSmsColSubject           = -1;
        public int mSmsColAddress           = -1;
        public int mSmsColDate              = -1;
        public int mSmsColType              = -1;
        public int mSmsColThreadId          = -1;

        public int mMmsColRead              = -1;
        public int mMmsColFolder            = -1;
        public int mMmsColAttachmentSize    = -1;
        public int mMmsColTextOnly          = -1;
        public int mMmsColId                = -1;
        public int mMmsColSize              = -1;
        public int mMmsColDate              = -1;
        public int mMmsColSubject           = -1;
        public int mMmsColThreadId          = -1;

        public int mConvoColConvoId         = -1;
        public int mConvoColLastActivity    = -1;
        public int mConvoColName            = -1;
        public int mConvoColRead            = -1;
        public int mConvoColVersionCounter  = -1;
        public int mConvoColSummary         = -1;
        public int mContactColBtUid         = -1;
        public int mContactColChatState     = -1;
        public int mContactColContactUci    = -1;
        public int mContactColNickname      = -1;
        public int mContactColLastActive    = -1;
        public int mContactColName          = -1;
        public int mContactColPresenceState = -1;
        public int mContactColPresenceText  = -1;
        public int mContactColPriority      = -1;


        public void setMessageColumns(Cursor c) {
            mMessageColId               = c.getColumnIndex(
                    BluetoothMapContract.MessageColumns._ID);
            mMessageColDate             = c.getColumnIndex(
                    BluetoothMapContract.MessageColumns.DATE);
            mMessageColSubject          = c.getColumnIndex(
                    BluetoothMapContract.MessageColumns.SUBJECT);
            mMessageColFolder           = c.getColumnIndex(
                    BluetoothMapContract.MessageColumns.FOLDER_ID);
            mMessageColRead             = c.getColumnIndex(
                    BluetoothMapContract.MessageColumns.FLAG_READ);
            mMessageColSize             = c.getColumnIndex(
                    BluetoothMapContract.MessageColumns.MESSAGE_SIZE);
            mMessageColFromAddress      = c.getColumnIndex(
                    BluetoothMapContract.MessageColumns.FROM_LIST);
            mMessageColToAddress        = c.getColumnIndex(
                    BluetoothMapContract.MessageColumns.TO_LIST);
            mMessageColAttachment       = c.getColumnIndex(
                    BluetoothMapContract.MessageColumns.FLAG_ATTACHMENT);
            mMessageColAttachmentSize   = c.getColumnIndex(
                    BluetoothMapContract.MessageColumns.ATTACHMENT_SIZE);
            mMessageColPriority         = c.getColumnIndex(
                    BluetoothMapContract.MessageColumns.FLAG_HIGH_PRIORITY);
            mMessageColProtected        = c.getColumnIndex(
                    BluetoothMapContract.MessageColumns.FLAG_PROTECTED);
            mMessageColReception        = c.getColumnIndex(
                    BluetoothMapContract.MessageColumns.RECEPTION_STATE);
            mMessageColDelivery         = c.getColumnIndex(
                    BluetoothMapContract.MessageColumns.DEVILERY_STATE);
            mMessageColThreadId         = c.getColumnIndex(
                    BluetoothMapContract.MessageColumns.THREAD_ID);
        }

        public void setEmailMessageColumns(Cursor c) {
            setMessageColumns(c);
            mMessageColCcAddress        = c.getColumnIndex(
                    BluetoothMapContract.MessageColumns.CC_LIST);
            mMessageColBccAddress       = c.getColumnIndex(
                    BluetoothMapContract.MessageColumns.BCC_LIST);
            mMessageColReplyTo          = c.getColumnIndex(
                    BluetoothMapContract.MessageColumns.REPLY_TO_LIST);
        }

        public void setImMessageColumns(Cursor c) {
            setMessageColumns(c);
            mMessageColThreadName       = c.getColumnIndex(
                    BluetoothMapContract.MessageColumns.THREAD_NAME);
            mMessageColAttachmentMime   = c.getColumnIndex(
                    BluetoothMapContract.MessageColumns.ATTACHMENT_MINE_TYPES);
            //TODO this is temporary as text should come from parts table instead
            mMessageColBody = c.getColumnIndex(BluetoothMapContract.MessageColumns.BODY);

        }

        public void setEmailImConvoColumns(Cursor c) {
            mConvoColConvoId            = c.getColumnIndex(
                    BluetoothMapContract.ConversationColumns.THREAD_ID);
            mConvoColLastActivity       = c.getColumnIndex(
                    BluetoothMapContract.ConversationColumns.LAST_THREAD_ACTIVITY);
            mConvoColName               = c.getColumnIndex(
                    BluetoothMapContract.ConversationColumns.THREAD_NAME);
            mConvoColRead               = c.getColumnIndex(
                    BluetoothMapContract.ConversationColumns.READ_STATUS);
            mConvoColVersionCounter     = c.getColumnIndex(
                    BluetoothMapContract.ConversationColumns.VERSION_COUNTER);
            mConvoColSummary            = c.getColumnIndex(
                    BluetoothMapContract.ConversationColumns.SUMMARY);
            setEmailImConvoContactColumns(c);
        }

        public void setEmailImConvoContactColumns(Cursor c){
            mContactColBtUid         = c.getColumnIndex(
                    BluetoothMapContract.ConvoContactColumns.X_BT_UID);
            mContactColChatState     = c.getColumnIndex(
                    BluetoothMapContract.ConvoContactColumns.CHAT_STATE);
            mContactColContactUci     = c.getColumnIndex(
                    BluetoothMapContract.ConvoContactColumns.UCI);
            mContactColNickname      = c.getColumnIndex(
                    BluetoothMapContract.ConvoContactColumns.NICKNAME);
            mContactColLastActive    = c.getColumnIndex(
                    BluetoothMapContract.ConvoContactColumns.LAST_ACTIVE);
            mContactColName          = c.getColumnIndex(
                    BluetoothMapContract.ConvoContactColumns.NAME);
            mContactColPresenceState = c.getColumnIndex(
                    BluetoothMapContract.ConvoContactColumns.PRESENCE_STATE);
            mContactColPresenceText = c.getColumnIndex(
                    BluetoothMapContract.ConvoContactColumns.STATUS_TEXT);
            mContactColPriority      = c.getColumnIndex(
                    BluetoothMapContract.ConvoContactColumns.PRIORITY);
        }

        public void setSmsColumns(Cursor c) {
            mSmsColId      = c.getColumnIndex(BaseColumns._ID);
            mSmsColFolder  = c.getColumnIndex(Sms.TYPE);
            mSmsColRead    = c.getColumnIndex(Sms.READ);
            mSmsColSubject = c.getColumnIndex(Sms.BODY);
            mSmsColAddress = c.getColumnIndex(Sms.ADDRESS);
            mSmsColDate    = c.getColumnIndex(Sms.DATE);
            mSmsColType    = c.getColumnIndex(Sms.TYPE);
            mSmsColThreadId= c.getColumnIndex(Sms.THREAD_ID);
        }

        public void setMmsColumns(Cursor c) {
            mMmsColId              = c.getColumnIndex(BaseColumns._ID);
            mMmsColFolder          = c.getColumnIndex(Mms.MESSAGE_BOX);
            mMmsColRead            = c.getColumnIndex(Mms.READ);
            mMmsColAttachmentSize  = c.getColumnIndex(Mms.MESSAGE_SIZE);
            mMmsColTextOnly        = c.getColumnIndex(Mms.TEXT_ONLY);
            mMmsColSize            = c.getColumnIndex(Mms.MESSAGE_SIZE);
            mMmsColDate            = c.getColumnIndex(Mms.DATE);
            mMmsColSubject         = c.getColumnIndex(Mms.SUBJECT);
            mMmsColThreadId        = c.getColumnIndex(Mms.THREAD_ID);
        }
    }

    public BluetoothMapContent(final Context context, BluetoothMapAccountItem account,
            BluetoothMapMasInstance mas) {
        mContext = context;
        mResolver = mContext.getContentResolver();
        mMasInstance = mas;
        if (mResolver == null) {
            if (D) Log.d(TAG, "getContentResolver failed");
        }

        if(account != null){
            mBaseUri = account.mBase_uri + "/";
            mAccount = account;
        } else {
            mBaseUri = null;
            mAccount = null;
        }
    }
    private static void close(Closeable c) {
        try {
          if (c != null) c.close();
        } catch (IOException e) {
        }
    }
    private void setProtected(BluetoothMapMessageListingElement e, Cursor c,
            FilterInfo fi, BluetoothMapAppParams ap) {
        if ((ap.getParameterMask() & MASK_PROTECTED) != 0) {
            String protect = "no";
            if (fi.mMsgType == FilterInfo.TYPE_EMAIL ||
                fi.mMsgType == FilterInfo.TYPE_IM) {
                int flagProtected = c.getInt(fi.mMessageColProtected);
                if (flagProtected == 1) {
                    protect = "yes";
                }
            }
            if (V) Log.d(TAG, "setProtected: " + protect + "\n");
            e.setProtect(protect);
        }
    }

    private void setThreadId(BluetoothMapMessageListingElement e, Cursor c,
            FilterInfo fi, BluetoothMapAppParams ap) {
        if ((ap.getParameterMask() & MASK_CONVERSATION_ID) != 0) {
            long threadId = 0;
            TYPE type = TYPE.SMS_GSM; // Just used for handle encoding
            if (fi.mMsgType == FilterInfo.TYPE_SMS) {
                threadId = c.getLong(fi.mSmsColThreadId);
            } else if (fi.mMsgType == FilterInfo.TYPE_MMS) {
                threadId = c.getLong(fi.mMmsColThreadId);
                type = TYPE.MMS;// Just used for handle encoding
            } else if (fi.mMsgType == FilterInfo.TYPE_EMAIL ||
                       fi.mMsgType == FilterInfo.TYPE_IM) {
                threadId = c.getLong(fi.mMessageColThreadId);
                type = TYPE.EMAIL;// Just used for handle encoding
            }
            e.setThreadId(threadId,type);
            if (V) Log.d(TAG, "setThreadId: " + threadId + "\n");
        }
    }

    private void setThreadName(BluetoothMapMessageListingElement e, Cursor c,
            FilterInfo fi, BluetoothMapAppParams ap) {
        // TODO: Maybe this should be valid for SMS/MMS
        if ((ap.getParameterMask() & MASK_CONVERSATION_NAME) != 0) {
            if (fi.mMsgType == FilterInfo.TYPE_IM) {
                String threadName = c.getString(fi.mMessageColThreadName);
                e.setThreadName(threadName);
                if (V) Log.d(TAG, "setThreadName: " + threadName + "\n");
            }
        }
    }


    private void setSent(BluetoothMapMessageListingElement e, Cursor c,
            FilterInfo fi, BluetoothMapAppParams ap) {
        if ((ap.getParameterMask() & MASK_SENT) != 0) {
            int msgType = 0;
            if (fi.mMsgType == FilterInfo.TYPE_SMS) {
                msgType = c.getInt(fi.mSmsColFolder);
            } else if (fi.mMsgType == FilterInfo.TYPE_MMS) {
                msgType = c.getInt(fi.mMmsColFolder);
            } else if (fi.mMsgType == FilterInfo.TYPE_EMAIL ||
                       fi.mMsgType == FilterInfo.TYPE_IM) {
                msgType = c.getInt(fi.mMessageColFolder);
            }
            String sent = null;
            if (msgType == 2) {
                sent = "yes";
            } else {
                sent = "no";
            }
            if (V) Log.d(TAG, "setSent: " + sent);
            e.setSent(sent);
        }
    }

    private void setRead(BluetoothMapMessageListingElement e, Cursor c,
            FilterInfo fi, BluetoothMapAppParams ap) {
        int read = 0;
        if (fi.mMsgType == FilterInfo.TYPE_SMS) {
            read = c.getInt(fi.mSmsColRead);
        } else if (fi.mMsgType == FilterInfo.TYPE_MMS) {
            read = c.getInt(fi.mMmsColRead);
        } else if (fi.mMsgType == FilterInfo.TYPE_EMAIL ||
                   fi.mMsgType == FilterInfo.TYPE_IM) {
            read = c.getInt(fi.mMessageColRead);
        }
        String setread = null;

        if (V) Log.d(TAG, "setRead: " + setread);
        e.setRead((read==1?true:false), ((ap.getParameterMask() & MASK_READ) != 0));
    }
    private void setConvoRead(BluetoothMapConvoListingElement e, Cursor c,
            FilterInfo fi, BluetoothMapAppParams ap) {
        String setread = null;
        int read = 0;
            read = c.getInt(fi.mConvoColRead);


        if (V) Log.d(TAG, "setRead: " + setread);
        e.setRead((read==1?true:false), ((ap.getParameterMask() & MASK_READ) != 0));
    }

    private void setPriority(BluetoothMapMessageListingElement e, Cursor c,
            FilterInfo fi, BluetoothMapAppParams ap) {
        if ((ap.getParameterMask() & MASK_PRIORITY) != 0) {
            String priority = "no";
            if (fi.mMsgType == FilterInfo.TYPE_EMAIL ||
                fi.mMsgType == FilterInfo.TYPE_IM) {
                int highPriority = c.getInt(fi.mMessageColPriority);
                if (highPriority == 1) {
                    priority = "yes";
                }
            }
            int pri = 0;
            if (fi.mMsgType == FilterInfo.TYPE_MMS) {
                pri = c.getInt(c.getColumnIndex(Mms.PRIORITY));
            }
            if (pri == PduHeaders.PRIORITY_HIGH) {
                priority = "yes";
            }
            if (V) Log.d(TAG, "setPriority: " + priority);
            e.setPriority(priority);
        }
    }

    /**
     * For SMS we set the attachment size to 0, as all data will be text data, hence
     * attachments for SMS is not possible.
     * For MMS all data is actually attachments, hence we do set the attachment size to
     * the total message size. To provide a more accurate attachment size, one could
     * extract the length (in bytes) of the text parts.
     */
    private void setAttachment(BluetoothMapMessageListingElement e, Cursor c,
            FilterInfo fi, BluetoothMapAppParams ap) {
        if ((ap.getParameterMask() & MASK_ATTACHMENT_SIZE) != 0) {
            int size = 0;
            String attachmentMimeTypes = null;
            if (fi.mMsgType == FilterInfo.TYPE_MMS) {
                if(c.getInt(fi.mMmsColTextOnly) == 0) {
                    size = c.getInt(fi.mMmsColAttachmentSize);
                    if(size <= 0) {
                        // We know there are attachments, since it is not TextOnly
                        // Hence the size in the database must be wrong.
                        // Set size to 1 to indicate to the client, that attachments are present
                        if (D) Log.d(TAG, "Error in message database, size reported as: " + size
                                + " Changing size to 1");
                        size = 1;
                    }
                    // TODO: Add handling of attachemnt mime types
                }
            } else if (fi.mMsgType == FilterInfo.TYPE_EMAIL) {
                int attachment = c.getInt(fi.mMessageColAttachment);
                size = c.getInt(fi.mMessageColAttachmentSize);
                if(attachment == 1 && size == 0) {
                    if (D) Log.d(TAG, "Error in message database, attachment size reported as: " + size
                            + " Changing size to 1");
                    size = 1; /* Ensure we indicate we have attachments in the size, if the
                                 message has attachments, in case the e-mail client do not
                                 report a size */
                }
            } else if (fi.mMsgType == FilterInfo.TYPE_IM) {
                int attachment = c.getInt(fi.mMessageColAttachment);
                size = c.getInt(fi.mMessageColAttachmentSize);
                if(attachment == 1 && size == 0) {
                    size = 1; /* Ensure we indicate we have attachments in the size, it the
                                  message has attachments, in case the e-mail client do not
                                  report a size */
                    attachmentMimeTypes =  c.getString(fi.mMessageColAttachmentMime);
                }
            }
            if (V) Log.d(TAG, "setAttachmentSize: " + size + "\n" +
                              "setAttachmentMimeTypes: " + attachmentMimeTypes );
            e.setAttachmentSize(size);

            if( (mMsgListingVersion > BluetoothMapUtils.MAP_MESSAGE_LISTING_FORMAT_V10)
                    && ((ap.getParameterMask() & MASK_ATTACHMENT_MIME) != 0) ){
                e.setAttachmentMimeTypes(attachmentMimeTypes);
            }
        }
    }

    private void setText(BluetoothMapMessageListingElement e, Cursor c,
            FilterInfo fi, BluetoothMapAppParams ap) {
        if ((ap.getParameterMask() & MASK_TEXT) != 0) {
            String hasText = "";
            if (fi.mMsgType == FilterInfo.TYPE_SMS) {
                hasText = "yes";
            } else if (fi.mMsgType == FilterInfo.TYPE_MMS) {
                int textOnly = c.getInt(fi.mMmsColTextOnly);
                if (textOnly == 1) {
                    hasText = "yes";
                } else {
                    long id = c.getLong(fi.mMmsColId);
                    String text = getTextPartsMms(mResolver, id);
                    if (text != null && text.length() > 0) {
                        hasText = "yes";
                    } else {
                        hasText = "no";
                    }
                }
            } else if (fi.mMsgType == FilterInfo.TYPE_EMAIL ||
                       fi.mMsgType == FilterInfo.TYPE_IM) {
                hasText = "yes";
            }
            if (V) Log.d(TAG, "setText: " + hasText);
            e.setText(hasText);
        }
    }

    private void setReceptionStatus(BluetoothMapMessageListingElement e, Cursor c,
        FilterInfo fi, BluetoothMapAppParams ap) {
        if ((ap.getParameterMask() & MASK_RECEPTION_STATUS) != 0) {
            String status = "complete";
            if (V) Log.d(TAG, "setReceptionStatus: " + status);
            e.setReceptionStatus(status);
        }
    }

    private void setDeliveryStatus(BluetoothMapMessageListingElement e, Cursor c,
            FilterInfo fi, BluetoothMapAppParams ap) {
        if ((ap.getParameterMask() & MASK_DELIVERY_STATUS) != 0) {
            String deliveryStatus = "delivered";
            // TODO: Should be handled for SMS and MMS as well
            if (fi.mMsgType == FilterInfo.TYPE_EMAIL ||
                fi.mMsgType == FilterInfo.TYPE_IM) {
                deliveryStatus = c.getString(fi.mMessageColDelivery);
            }
            if (V) Log.d(TAG, "setDeliveryStatus: " + deliveryStatus);
            e.setDeliveryStatus(deliveryStatus);
        }
    }

    private void setSize(BluetoothMapMessageListingElement e, Cursor c,
        FilterInfo fi, BluetoothMapAppParams ap) {
        if ((ap.getParameterMask() & MASK_SIZE) != 0) {
            int size = 0;
            if (fi.mMsgType == FilterInfo.TYPE_SMS) {
                String subject = c.getString(fi.mSmsColSubject);
                size = subject.length();
            } else if (fi.mMsgType == FilterInfo.TYPE_MMS) {
                size = c.getInt(fi.mMmsColSize);
                //MMS complete size = attachment_size + subject length
                String subject = e.getSubject();
                if (subject == null || subject.length() == 0 ) {
                    // Handle setSubject if not done case
                    setSubject(e, c, fi, ap);
                }
                if (subject != null && subject.length() != 0 )
                    size += subject.length();
            } else if (fi.mMsgType == FilterInfo.TYPE_EMAIL ||
                       fi.mMsgType == FilterInfo.TYPE_IM) {
                size = c.getInt(fi.mMessageColSize);
            }
            if(size <= 0) {
                // A message cannot have size 0
                // Hence the size in the database must be wrong.
                // Set size to 1 to indicate to the client, that the message has content.
                if (D) Log.d(TAG, "Error in message database, size reported as: " + size
                        + " Changing size to 1");
                size = 1;
            }
            if (V) Log.d(TAG, "setSize: " + size);
            e.setSize(size);
        }
    }

    private TYPE getType(Cursor c, FilterInfo fi) {
        TYPE type = null;
        if (V) Log.d(TAG, "getType: for filterMsgType" + fi.mMsgType);
        if (fi.mMsgType == FilterInfo.TYPE_SMS) {
            if (V) Log.d(TAG, "getType: phoneType for SMS " + fi.mPhoneType);
            if (fi.mPhoneType == TelephonyManager.PHONE_TYPE_CDMA) {
                type = TYPE.SMS_CDMA;
            } else {
                type = TYPE.SMS_GSM;
            }
        } else if (fi.mMsgType == FilterInfo.TYPE_MMS) {
            type = TYPE.MMS;
        } else if (fi.mMsgType == FilterInfo.TYPE_EMAIL) {
            type = TYPE.EMAIL;
        } else if (fi.mMsgType == FilterInfo.TYPE_IM) {
            type = TYPE.IM;
        }
        if (V) Log.d(TAG, "getType: " + type);

        return type;
    }
    private void setFolderType(BluetoothMapMessageListingElement e, Cursor c,
            FilterInfo fi, BluetoothMapAppParams ap) {
        if ((ap.getParameterMask() & MASK_FOLDER_TYPE) != 0) {
            String folderType = null;
            int folderId = 0;
            if (fi.mMsgType == FilterInfo.TYPE_SMS) {
                folderId = c.getInt(fi.mSmsColFolder);
                if (folderId == 1)
                    folderType = BluetoothMapContract.FOLDER_NAME_INBOX;
                else if (folderId == 2)
                    folderType = BluetoothMapContract.FOLDER_NAME_SENT;
                else if (folderId == 3)
                    folderType = BluetoothMapContract.FOLDER_NAME_DRAFT;
                else if (folderId == 4 || folderId == 5 || folderId == 6)
                    folderType = BluetoothMapContract.FOLDER_NAME_OUTBOX;
                else
                    folderType = BluetoothMapContract.FOLDER_NAME_DELETED;
            } else if (fi.mMsgType == FilterInfo.TYPE_MMS) {
                folderId = c.getInt(fi.mMmsColFolder);
                if (folderId == 1)
                    folderType = BluetoothMapContract.FOLDER_NAME_INBOX;
                else if (folderId == 2)
                    folderType = BluetoothMapContract.FOLDER_NAME_SENT;
                else if (folderId == 3)
                    folderType = BluetoothMapContract.FOLDER_NAME_DRAFT;
                else if (folderId == 4)
                    folderType = BluetoothMapContract.FOLDER_NAME_OUTBOX;
                else
                    folderType = BluetoothMapContract.FOLDER_NAME_DELETED;
            } else if (fi.mMsgType == FilterInfo.TYPE_EMAIL) {
                // TODO: need to find name from id and then set folder type
            } else if (fi.mMsgType == FilterInfo.TYPE_IM) {
                folderId = c.getInt(fi.mMessageColFolder);
                if (folderId == BluetoothMapContract.FOLDER_ID_INBOX)
                    folderType = BluetoothMapContract.FOLDER_NAME_INBOX;
                else if (folderId == BluetoothMapContract.FOLDER_ID_SENT)
                    folderType = BluetoothMapContract.FOLDER_NAME_SENT;
                else if (folderId == BluetoothMapContract.FOLDER_ID_DRAFT)
                    folderType = BluetoothMapContract.FOLDER_NAME_DRAFT;
                else if (folderId == BluetoothMapContract.FOLDER_ID_OUTBOX)
                    folderType = BluetoothMapContract.FOLDER_NAME_OUTBOX;
                else if (folderId == BluetoothMapContract.FOLDER_ID_DELETED)
                    folderType = BluetoothMapContract.FOLDER_NAME_DELETED;
                else
                    folderType = BluetoothMapContract.FOLDER_NAME_OTHER;
            }
            if (V) Log.d(TAG, "setFolderType: " + folderType);
            e.setFolderType(folderType);
        }
    }

 private String getRecipientNameEmail(BluetoothMapMessageListingElement e,
                                      Cursor c,
                                      FilterInfo fi) {

        String toAddress, ccAddress, bccAddress;
        toAddress = c.getString(fi.mMessageColToAddress);
        ccAddress = c.getString(fi.mMessageColCcAddress);
        bccAddress = c.getString(fi.mMessageColBccAddress);

        StringBuilder sb = new StringBuilder();
        if (toAddress != null) {
            Rfc822Token tokens[] = Rfc822Tokenizer.tokenize(toAddress);
            if (tokens.length != 0) {
                if(D) Log.d(TAG, "toName count= " + tokens.length);
                int i = 0;
                boolean first = true;
                while (i < tokens.length) {
                    if(V) Log.d(TAG, "ToName = " + tokens[i].toString());
                    String name = tokens[i].getName();
                    if(!first) sb.append("; "); //Delimiter
                    sb.append(name);
                    first = false;
                    i++;
                }
            }

            if (ccAddress != null) {
                sb.append("; ");
            }
        }
        if (ccAddress != null) {
            Rfc822Token tokens[] = Rfc822Tokenizer.tokenize(ccAddress);
            if (tokens.length != 0) {
                if(D) Log.d(TAG, "ccName count= " + tokens.length);
                int i = 0;
                boolean first = true;
                while (i < tokens.length) {
                    if(V) Log.d(TAG, "ccName = " + tokens[i].toString());
                    String name = tokens[i].getName();
                    if(!first) sb.append("; "); //Delimiter
                    sb.append(name);
                    first = false;
                    i++;
                }
            }
            if (bccAddress != null) {
                sb.append("; ");
            }
        }
        if (bccAddress != null) {
            Rfc822Token tokens[] = Rfc822Tokenizer.tokenize(bccAddress);
            if (tokens.length != 0) {
                if(D) Log.d(TAG, "bccName count= " + tokens.length);
                int i = 0;
                boolean first = true;
                while (i < tokens.length) {
                    if(V) Log.d(TAG, "bccName = " + tokens[i].toString());
                    String name = tokens[i].getName();
                    if(!first) sb.append("; "); //Delimiter
                    sb.append(name);
                    first = false;
                    i++;
                }
            }
        }
        return sb.toString();
    }

    private String getRecipientAddressingEmail(BluetoothMapMessageListingElement e,
                                               Cursor c,
                                               FilterInfo fi) {
        String toAddress, ccAddress, bccAddress;
        toAddress = c.getString(fi.mMessageColToAddress);
        ccAddress = c.getString(fi.mMessageColCcAddress);
        bccAddress = c.getString(fi.mMessageColBccAddress);

        StringBuilder sb = new StringBuilder();
        if (toAddress != null) {
            Rfc822Token tokens[] = Rfc822Tokenizer.tokenize(toAddress);
            if (tokens.length != 0) {
                if(D) Log.d(TAG, "toAddress count= " + tokens.length);
                int i = 0;
                boolean first = true;
                while (i < tokens.length) {
                    if(V) Log.d(TAG, "ToAddress = " + tokens[i].toString());
                    String email = tokens[i].getAddress();
                    if(!first) sb.append("; "); //Delimiter
                    sb.append(email);
                    first = false;
                    i++;
                }
            }

            if (ccAddress != null) {
                sb.append("; ");
            }
        }
        if (ccAddress != null) {
            Rfc822Token tokens[] = Rfc822Tokenizer.tokenize(ccAddress);
            if (tokens.length != 0) {
                if(D) Log.d(TAG, "ccAddress count= " + tokens.length);
                int i = 0;
                boolean first = true;
                while (i < tokens.length) {
                    if(V) Log.d(TAG, "ccAddress = " + tokens[i].toString());
                    String email = tokens[i].getAddress();
                    if(!first) sb.append("; "); //Delimiter
                    sb.append(email);
                    first = false;
                    i++;
                }
            }
            if (bccAddress != null) {
                sb.append("; ");
            }
        }
        if (bccAddress != null) {
            Rfc822Token tokens[] = Rfc822Tokenizer.tokenize(bccAddress);
            if (tokens.length != 0) {
                if(D) Log.d(TAG, "bccAddress count= " + tokens.length);
                int i = 0;
                boolean first = true;
                while (i < tokens.length) {
                    if(V) Log.d(TAG, "bccAddress = " + tokens[i].toString());
                    String email = tokens[i].getAddress();
                    if(!first) sb.append("; "); //Delimiter
                    sb.append(email);
                    first = false;
                    i++;
                }
            }
        }
        return sb.toString();
    }

    private void setRecipientAddressing(BluetoothMapMessageListingElement e, Cursor c,
        FilterInfo fi, BluetoothMapAppParams ap) {
        if ((ap.getParameterMask() & MASK_RECIPIENT_ADDRESSING) != 0) {
            String address = null;
            if (fi.mMsgType == FilterInfo.TYPE_SMS) {
                int msgType = c.getInt(fi.mSmsColType);
                if (msgType == Sms.MESSAGE_TYPE_INBOX ) {
                    address = fi.mPhoneNum;
                } else {
                    address = c.getString(c.getColumnIndex(Sms.ADDRESS));
                }
                if ((address == null) && msgType == Sms.MESSAGE_TYPE_DRAFT) {
                    // Fetch address for Drafts folder from "canonical_address" table
                    int threadIdInd = c.getColumnIndex(Sms.THREAD_ID);
                    String threadIdStr = c.getString(threadIdInd);
                    // If a draft message has no recipient, it has no thread ID
                    // hence threadIdStr could possibly be null
                    if (threadIdStr != null) {
                        address = getCanonicalAddressSms(mResolver, Integer.valueOf(threadIdStr));
                    }
                    if(V)  Log.v(TAG, "threadId = " + threadIdStr + " adress:" + address +"\n");
                }
            } else if (fi.mMsgType == FilterInfo.TYPE_MMS) {
                long id = c.getLong(c.getColumnIndex(BaseColumns._ID));
                address = getAddressMms(mResolver, id, MMS_TO);
            } else if (fi.mMsgType == FilterInfo.TYPE_EMAIL) {
                /* Might be another way to handle addresses */
                address = getRecipientAddressingEmail(e, c, fi);
            }
            if (V) Log.v(TAG, "setRecipientAddressing: " + address);
            if(address == null)
                address = "";
            e.setRecipientAddressing(address);
        }
    }

    private void setRecipientName(BluetoothMapMessageListingElement e, Cursor c,
        FilterInfo fi, BluetoothMapAppParams ap) {
        if ((ap.getParameterMask() & MASK_RECIPIENT_NAME) != 0) {
            String name = null;
            if (fi.mMsgType == FilterInfo.TYPE_SMS) {
                int msgType = c.getInt(fi.mSmsColType);
                if (msgType != 1) {
                    String phone = c.getString(fi.mSmsColAddress);
                    if (phone != null && !phone.isEmpty())
                        name = getContactNameFromPhone(phone, mResolver);
                } else {
                    name = fi.mPhoneAlphaTag;
                }
            } else if (fi.mMsgType == FilterInfo.TYPE_MMS) {
                long id = c.getLong(fi.mMmsColId);
                String phone;
                if(e.getRecipientAddressing() != null){
                    phone = getAddressMms(mResolver, id, MMS_TO);
                } else {
                    phone = e.getRecipientAddressing();
                }
                if (phone != null && !phone.isEmpty())
                    name = getContactNameFromPhone(phone, mResolver);
            } else if (fi.mMsgType == FilterInfo.TYPE_EMAIL) {
                /* Might be another way to handle address and names */
                name = getRecipientNameEmail(e,c,fi);
            }
            if (V) Log.v(TAG, "setRecipientName: " + name);
            if(name == null)
                name = "";
            e.setRecipientName(name);
        }
    }

    private void setSenderAddressing(BluetoothMapMessageListingElement e, Cursor c,
            FilterInfo fi, BluetoothMapAppParams ap) {
        if ((ap.getParameterMask() & MASK_SENDER_ADDRESSING) != 0) {
            String address = "";
            String tempAddress;
            if (fi.mMsgType == FilterInfo.TYPE_SMS) {
                int msgType = c.getInt(fi.mSmsColType);
                if (msgType == 1) { // INBOX
                    tempAddress = c.getString(fi.mSmsColAddress);
                } else {
                    tempAddress = fi.mPhoneNum;
                }
                if(tempAddress == null) {
                    /* This can only happen on devices with no SIM -
                       hence will typically not have any SMS messages. */
                } else {
                    address = PhoneNumberUtils.extractNetworkPortion(tempAddress);
                    /* extractNetworkPortion can return N if the number is a service "number" =
                     * a string with the a name in (i.e. "Some-Tele-company" would return N
                     * because of the N in compaNy)
                     * Hence we need to check if the number is actually a string with alpha chars.
                     * */
                    Boolean alpha = PhoneNumberUtils.stripSeparators(tempAddress).matches(
                            "[0-9]*[a-zA-Z]+[0-9]*");

                    if(address == null || address.length() < 2 || alpha) {
                        address = tempAddress; // if the number is a service acsii text just use it
                    }
                }
            } else if (fi.mMsgType == FilterInfo.TYPE_MMS) {
                long id = c.getLong(fi.mMmsColId);
                tempAddress = getAddressMms(mResolver, id, MMS_FROM);
                address = PhoneNumberUtils.extractNetworkPortion(tempAddress);
                if(address == null || address.length() < 1){
                    address = tempAddress; // if the number is a service acsii text just use it
                }
            } else if (fi.mMsgType == FilterInfo.TYPE_EMAIL/* ||
                       fi.mMsgType == FilterInfo.TYPE_IM*/) {
                String nameEmail = c.getString(fi.mMessageColFromAddress);
                Rfc822Token tokens[] = Rfc822Tokenizer.tokenize(nameEmail);
                if (tokens.length != 0) {
                    if(D) Log.d(TAG, "Originator count= " + tokens.length);
                    int i = 0;
                    boolean first = true;
                    while (i < tokens.length) {
                        if(V) Log.d(TAG, "SenderAddress = " + tokens[i].toString());
                        String[] emails = new String[1];
                        emails[0] = tokens[i].getAddress();
                        String name = tokens[i].getName();
                        if(!first) address += "; "; //Delimiter
                        address += emails[0];
                        first = false;
                        i++;
                    }
                }
            } else if(fi.mMsgType == FilterInfo.TYPE_IM) {
                // TODO: For IM we add the contact ID in the addressing
                long contact_id = c.getLong(fi.mMessageColFromAddress);
                // TODO: This is a BAD hack, that we map the contact ID to a conversation ID!!!
                //       We need to reach a conclusion on what to do
                Uri contactsUri = Uri.parse(mBaseUri + BluetoothMapContract.TABLE_CONVOCONTACT);
                Cursor contacts = mResolver.query(contactsUri,
                                           BluetoothMapContract.BT_CONTACT_PROJECTION,
                                           BluetoothMapContract.ConvoContactColumns.CONVO_ID
                                           + " = " + contact_id, null, null);
                try {
                    // TODO this will not work for group-chats
                    if(contacts != null && contacts.moveToFirst()){
                        address = contacts.getString(
                                contacts.getColumnIndex(
                                        BluetoothMapContract.ConvoContactColumns.UCI));
                    }
                } finally {
                    if (contacts != null) contacts.close();
                }

            }
            if (V) Log.v(TAG, "setSenderAddressing: " + address);
            if(address == null)
                address = "";
            e.setSenderAddressing(address);
        }
    }

    private void setSenderName(BluetoothMapMessageListingElement e, Cursor c,
            FilterInfo fi, BluetoothMapAppParams ap) {
        if ((ap.getParameterMask() & MASK_SENDER_NAME) != 0) {
            String name = "";
            if (fi.mMsgType == FilterInfo.TYPE_SMS) {
                int msgType = c.getInt(c.getColumnIndex(Sms.TYPE));
                if (msgType == 1) {
                    String phone = c.getString(fi.mSmsColAddress);
                    if (phone != null && !phone.isEmpty())
                        name = getContactNameFromPhone(phone, mResolver);
                } else {
                    name = fi.mPhoneAlphaTag;
                }
            } else if (fi.mMsgType == FilterInfo.TYPE_MMS) {
                long id = c.getLong(fi.mMmsColId);
                String phone;
                if(e.getSenderAddressing() != null){
                    phone = getAddressMms(mResolver, id, MMS_FROM);
                } else {
                    phone = e.getSenderAddressing();
                }
                if (phone != null && !phone.isEmpty() )
                    name = getContactNameFromPhone(phone, mResolver);
            } else if (fi.mMsgType == FilterInfo.TYPE_EMAIL/*  ||
                       fi.mMsgType == FilterInfo.TYPE_IM*/) {
                String nameEmail = c.getString(fi.mMessageColFromAddress);
                Rfc822Token tokens[] = Rfc822Tokenizer.tokenize(nameEmail);
                if (tokens.length != 0) {
                    if(D) Log.d(TAG, "Originator count= " + tokens.length);
                    int i = 0;
                    boolean first = true;
                    while (i < tokens.length) {
                        if(V) Log.d(TAG, "senderName = " + tokens[i].toString());
                        String[] emails = new String[1];
                        emails[0] = tokens[i].getAddress();
                        String nameIn = tokens[i].getName();
                        if(!first) name += "; "; //Delimiter
                        name += nameIn;
                        first = false;
                        i++;
                    }
                }
            } else if(fi.mMsgType == FilterInfo.TYPE_IM) {
                // For IM we add the contact ID in the addressing
                long contact_id = c.getLong(fi.mMessageColFromAddress);
                Uri contactsUri = Uri.parse(mBaseUri + BluetoothMapContract.TABLE_CONVOCONTACT);
                Cursor contacts = mResolver.query(contactsUri,
                                           BluetoothMapContract.BT_CONTACT_PROJECTION,
                                           BluetoothMapContract.ConvoContactColumns.CONVO_ID
                                           + " = " + contact_id, null, null);
                try {
                    // TODO this will not work for group-chats
                    if(contacts != null && contacts.moveToFirst()){
                        name = contacts.getString(
                                contacts.getColumnIndex(
                                        BluetoothMapContract.ConvoContactColumns.NAME));
                    }
                } finally {
                    if (contacts != null) contacts.close();
                }
            }
            if (V) Log.v(TAG, "setSenderName: " + name);
            if(name == null)
                name = "";
            e.setSenderName(name);
        }
    }




    private void setDateTime(BluetoothMapMessageListingElement e, Cursor c,
            FilterInfo fi, BluetoothMapAppParams ap) {
        if ((ap.getParameterMask() & MASK_DATETIME) != 0) {
            long date = 0;
            if (fi.mMsgType == FilterInfo.TYPE_SMS) {
                date = c.getLong(fi.mSmsColDate);
            } else if (fi.mMsgType == FilterInfo.TYPE_MMS) {
                /* Use Mms.DATE for all messages. Although contract class states */
                /* Mms.DATE_SENT are for outgoing messages. But that is not working. */
                date = c.getLong(fi.mMmsColDate) * 1000L;

                /* int msgBox = c.getInt(c.getColumnIndex(Mms.MESSAGE_BOX)); */
                /* if (msgBox == Mms.MESSAGE_BOX_INBOX) { */
                /*     date = c.getLong(c.getColumnIndex(Mms.DATE)) * 1000L; */
                /* } else { */
                /*     date = c.getLong(c.getColumnIndex(Mms.DATE_SENT)) * 1000L; */
                /* } */
            } else if (fi.mMsgType == FilterInfo.TYPE_EMAIL ||
                       fi.mMsgType == FilterInfo.TYPE_IM) {
                date = c.getLong(fi.mMessageColDate);
            }
            e.setDateTime(date);
        }
    }


    private void setLastActivity(BluetoothMapConvoListingElement e, Cursor c,
            FilterInfo fi, BluetoothMapAppParams ap) {
        long date = 0;
        if (fi.mMsgType == FilterInfo.TYPE_SMS ||
                fi.mMsgType == FilterInfo.TYPE_MMS ) {
            date = c.getLong(MMS_SMS_THREAD_COL_DATE);
        } else if (fi.mMsgType == FilterInfo.TYPE_EMAIL||
                fi.mMsgType == FilterInfo.TYPE_IM) {
            date = c.getLong(fi.mConvoColLastActivity);
        }
        e.setLastActivity(date);
        if (V) Log.v(TAG, "setDateTime: " + e.getLastActivityString());

    }

    static public String getTextPartsMms(ContentResolver r, long id) {
        String text = "";
        String selection = new String("mid=" + id);
        String uriStr = new String(Mms.CONTENT_URI + "/" + id + "/part");
        Uri uriAddress = Uri.parse(uriStr);
        // TODO: maybe use a projection with only "ct" and "text"
        Cursor c = r.query(uriAddress, null, selection,
            null, null);
        try {
            if (c != null && c.moveToFirst()) {
                do {
                    String ct = c.getString(c.getColumnIndex("ct"));
                    if (ct.equals("text/plain")) {
                        String part = c.getString(c.getColumnIndex("text"));
                        if(part != null) {
                            text += part;
                        }
                    }
                } while(c.moveToNext());
            }
        } finally {
            if (c != null) c.close();
        }

        return text;
    }

    private void setSubject(BluetoothMapMessageListingElement e, Cursor c,
            FilterInfo fi, BluetoothMapAppParams ap) {
        String subject = "";
        int subLength = ap.getSubjectLength();
        if(subLength == BluetoothMapAppParams.INVALID_VALUE_PARAMETER)
            subLength = 256;

        if ((ap.getParameterMask() & MASK_SUBJECT) != 0) {
            if (fi.mMsgType == FilterInfo.TYPE_SMS) {
                subject = c.getString(fi.mSmsColSubject);
            } else if (fi.mMsgType == FilterInfo.TYPE_MMS) {
                subject = c.getString(fi.mMmsColSubject);
                if (subject == null || subject.length() == 0) {
                    /* Get subject from mms text body parts - if any exists */
                    long id = c.getLong(fi.mMmsColId);
                    subject = getTextPartsMms(mResolver, id);
                }
            } else if (fi.mMsgType == FilterInfo.TYPE_EMAIL  ||
                       fi.mMsgType == FilterInfo.TYPE_IM) {
                subject = c.getString(fi.mMessageColSubject);
            }
            if (subject != null && subject.length() > subLength) {
                subject = subject.substring(0, subLength);
            } else if (subject == null ) {
                subject = "";
            }
            if (V) Log.d(TAG, "setSubject: " + subject);
            e.setSubject(subject);
        }
    }

    private void setHandle(BluetoothMapMessageListingElement e, Cursor c,
            FilterInfo fi, BluetoothMapAppParams ap) {
        long handle = -1;
        if (fi.mMsgType == FilterInfo.TYPE_SMS) {
            handle = c.getLong(fi.mSmsColId);
        } else if (fi.mMsgType == FilterInfo.TYPE_MMS) {
            handle = c.getLong(fi.mMmsColId);
        } else if (fi.mMsgType == FilterInfo.TYPE_EMAIL ||
                   fi.mMsgType == FilterInfo.TYPE_IM) {
            handle = c.getLong(fi.mMessageColId);
        }
        if (V) Log.d(TAG, "setHandle: " + handle );
        e.setHandle(handle);
    }

    private BluetoothMapMessageListingElement element(Cursor c, FilterInfo fi,
            BluetoothMapAppParams ap) {
        BluetoothMapMessageListingElement e = new BluetoothMapMessageListingElement();
        setHandle(e, c, fi, ap);
        setDateTime(e, c, fi, ap);
        e.setType(getType(c, fi), ((ap.getParameterMask() & MASK_TYPE) != 0) ? true : false);
        setRead(e, c, fi, ap);
        // we set number and name for sender/recipient later
        // they require lookup on contacts so no need to
        // do it for all elements unless they are to be used.
        e.setCursorIndex(c.getPosition());
        return e;
    }

    private BluetoothMapConvoListingElement createConvoElement(Cursor c, FilterInfo fi,
            BluetoothMapAppParams ap) {
        BluetoothMapConvoListingElement e = new BluetoothMapConvoListingElement();
        setLastActivity(e, c, fi, ap);
        e.setType(getType(c, fi));
//        setConvoRead(e, c, fi, ap);
        e.setCursorIndex(c.getPosition());
        return e;
    }

    /* TODO: Change to use SmsMmsContacts.getContactNameFromPhone() with proper use of
     *       caching. */
    public static String getContactNameFromPhone(String phone, ContentResolver resolver) {
        String name = null;
        //Handle possible exception for empty phone address
        if (TextUtils.isEmpty(phone)) {
            return name;
        }

        Uri uri = Uri.withAppendedPath(PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI,
                Uri.encode(phone));

        String[] projection = {Contacts._ID, Contacts.DISPLAY_NAME};
        String selection = Contacts.IN_VISIBLE_GROUP + "=1";
        String orderBy = Contacts.DISPLAY_NAME + " ASC";
        Cursor c = null;
        try {
            c = resolver.query(uri, projection, selection, null, orderBy);
            if(c != null) {
                int colIndex = c.getColumnIndex(Contacts.DISPLAY_NAME);
                if (c.getCount() >= 1) {
                    c.moveToFirst();
                    name = c.getString(colIndex);
                }
            }
        } finally {
            if(c != null) c.close();
        }
        return name;
    }
    /**
     * Get SMS RecipientAddresses for DRAFT folder based on threadId
     *
    */
    static public String getCanonicalAddressSms(ContentResolver r,  int threadId) {
       String [] RECIPIENT_ID_PROJECTION = { Threads.RECIPIENT_IDS };
        /*
         1. Get Recipient Ids from Threads.CONTENT_URI
         2. Get Recipient Address for corresponding Id from canonical-addresses table.
        */

        //Uri sAllCanonical = Uri.parse("content://mms-sms/canonical-addresses");
        Uri sAllCanonical =
                MmsSms.CONTENT_URI.buildUpon().appendPath("canonical-addresses").build();
        Uri sAllThreadsUri =
                Threads.CONTENT_URI.buildUpon().appendQueryParameter("simple", "true").build();
        Cursor cr = null;
        String recipientAddress = "";
        String recipientIds = null;
        String whereClause = "_id="+threadId;
        if (V) Log.v(TAG, "whereClause is "+ whereClause);
        try {
            cr = r.query(sAllThreadsUri, RECIPIENT_ID_PROJECTION, whereClause, null, null);
            if (cr != null && cr.moveToFirst()) {
                recipientIds = cr.getString(0);
                if (V) Log.v(TAG, "cursor.getCount(): " + cr.getCount() + "recipientIds: "
                        + recipientIds + "selection: "+ whereClause );
            }
        } finally {
            if(cr != null) {
                cr.close();
                cr = null;
            }
        }
        if (V) Log.v(TAG, "recipientIds with spaces: "+ recipientIds +"\n");
        if(recipientIds != null) {
            String recipients[] = null;
            whereClause = "";
            recipients = recipientIds.split(" ");
            for (String id: recipients) {
                if(whereClause.length() != 0)
                    whereClause +=" OR ";
                whereClause +="_id="+id;
            }
            if (V) Log.v(TAG, "whereClause is "+ whereClause);
            try {
                cr = r.query(sAllCanonical , null, whereClause, null, null);
                if (cr != null && cr.moveToFirst()) {
                    do {
                        //TODO: Multiple Recipeints are appended with ";" for now.
                        if(recipientAddress.length() != 0 )
                           recipientAddress+=";";
                        recipientAddress += cr.getString(
                                cr.getColumnIndex(CanonicalAddressesColumns.ADDRESS));
                    } while(cr.moveToNext());
                }
           } finally {
               if(cr != null)
                   cr.close();
           }
        }

        if(V) Log.v(TAG,"Final recipientAddress : "+ recipientAddress);
        return recipientAddress;
     }

    static public String getAddressMms(ContentResolver r, long id, int type) {
        String selection = new String("msg_id=" + id + " AND type=" + type);
        String uriStr = new String(Mms.CONTENT_URI + "/" + id + "/addr");
        Uri uriAddress = Uri.parse(uriStr);
        String addr = null;
        String[] projection = {Mms.Addr.ADDRESS};
        Cursor c = null;
        try {
            c = r.query(uriAddress, projection, selection, null, null); // TODO: Add projection
            int colIndex = c.getColumnIndex(Mms.Addr.ADDRESS);
            if (c != null) {
                if(c.moveToFirst()) {
                    addr = c.getString(colIndex);
                    if(addr.equals(INSERT_ADDRES_TOKEN)) {
                        addr  = "";
                    }
                }
            }
        } finally {
            if (c != null) c.close();
        }
        return addr;
    }

    /**
     * Matching functions for originator and recipient for MMS
     * @return true if found a match
     */
    private boolean matchRecipientMms(Cursor c, FilterInfo fi, String recip) {
        boolean res;
        long id = c.getLong(c.getColumnIndex(BaseColumns._ID));
        String phone = getAddressMms(mResolver, id, MMS_TO);
        if (phone != null && phone.length() > 0) {
            if (phone.matches(recip)) {
                if (V) Log.v(TAG, "matchRecipientMms: match recipient phone = " + phone);
                res = true;
            } else {
                String name = getContactNameFromPhone(phone, mResolver);
                if (name != null && name.length() > 0 && name.matches(recip)) {
                    if (V) Log.v(TAG, "matchRecipientMms: match recipient name = " + name);
                    res = true;
                } else {
                    res = false;
                }
            }
        } else {
            res = false;
        }
        return res;
    }

    private boolean matchRecipientSms(Cursor c, FilterInfo fi, String recip) {
        boolean res;
        int msgType = c.getInt(c.getColumnIndex(Sms.TYPE));
        if (msgType == 1) {
            String phone = fi.mPhoneNum;
            String name = fi.mPhoneAlphaTag;
            if (phone != null && phone.length() > 0 && phone.matches(recip)) {
                if (V) Log.v(TAG, "matchRecipientSms: match recipient phone = " + phone);
                res = true;
            } else if (name != null && name.length() > 0 && name.matches(recip)) {
                if (V) Log.v(TAG, "matchRecipientSms: match recipient name = " + name);
                res = true;
            } else {
                res = false;
            }
        } else {
            String phone = c.getString(c.getColumnIndex(Sms.ADDRESS));
            if (phone != null && phone.length() > 0) {
                if (phone.matches(recip)) {
                    if (V) Log.v(TAG, "matchRecipientSms: match recipient phone = " + phone);
                    res = true;
                } else {
                    String name = getContactNameFromPhone(phone, mResolver);
                    if (name != null && name.length() > 0 && name.matches(recip)) {
                        if (V) Log.v(TAG, "matchRecipientSms: match recipient name = " + name);
                        res = true;
                    } else {
                        res = false;
                    }
                }
            } else {
                res = false;
            }
        }
        return res;
    }

    private boolean matchRecipient(Cursor c, FilterInfo fi, BluetoothMapAppParams ap) {
        boolean res;
        String recip = ap.getFilterRecipient();
        if (recip != null && recip.length() > 0) {
            recip = recip.replace("*", ".*");
            recip = ".*" + recip + ".*";
            if (fi.mMsgType == FilterInfo.TYPE_SMS) {
                res = matchRecipientSms(c, fi, recip);
            } else if (fi.mMsgType == FilterInfo.TYPE_MMS) {
                res = matchRecipientMms(c, fi, recip);
            } else {
                if (D) Log.d(TAG, "matchRecipient: Unknown msg type: " + fi.mMsgType);
                res = false;
            }
        } else {
            res = true;
        }
        return res;
    }

    private boolean matchOriginatorMms(Cursor c, FilterInfo fi, String orig) {
        boolean res;
        long id = c.getLong(c.getColumnIndex(BaseColumns._ID));
        String phone = getAddressMms(mResolver, id, MMS_FROM);
        if (phone != null && phone.length() > 0) {
            if (phone.matches(orig)) {
                if (V) Log.v(TAG, "matchOriginatorMms: match originator phone = " + phone);
                res = true;
            } else {
                String name = getContactNameFromPhone(phone, mResolver);
                if (name != null && name.length() > 0 && name.matches(orig)) {
                    if (V) Log.v(TAG, "matchOriginatorMms: match originator name = " + name);
                    res = true;
                } else {
                    res = false;
                }
            }
        } else {
            res = false;
        }
        return res;
    }

    private boolean matchOriginatorSms(Cursor c, FilterInfo fi, String orig) {
        boolean res;
        int msgType = c.getInt(c.getColumnIndex(Sms.TYPE));
        if (msgType == 1) {
            String phone = c.getString(c.getColumnIndex(Sms.ADDRESS));
            if (phone !=null && phone.length() > 0) {
                if (phone.matches(orig)) {
                    if (V) Log.v(TAG, "matchOriginatorSms: match originator phone = " + phone);
                    res = true;
                } else {
                    String name = getContactNameFromPhone(phone, mResolver);
                    if (name != null && name.length() > 0 && name.matches(orig)) {
                        if (V) Log.v(TAG, "matchOriginatorSms: match originator name = " + name);
                        res = true;
                    } else {
                        res = false;
                    }
                }
            } else {
                res = false;
            }
        } else {
            String phone = fi.mPhoneNum;
            String name = fi.mPhoneAlphaTag;
            if (phone != null && phone.length() > 0 && phone.matches(orig)) {
                if (V) Log.v(TAG, "matchOriginatorSms: match originator phone = " + phone);
                res = true;
            } else if (name != null && name.length() > 0 && name.matches(orig)) {
                if (V) Log.v(TAG, "matchOriginatorSms: match originator name = " + name);
                res = true;
            } else {
                res = false;
            }
        }
        return res;
    }

   private boolean matchOriginator(Cursor c, FilterInfo fi, BluetoothMapAppParams ap) {
        boolean res;
        String orig = ap.getFilterOriginator();
        if (orig != null && orig.length() > 0) {
            orig = orig.replace("*", ".*");
            orig = ".*" + orig + ".*";
            if (fi.mMsgType == FilterInfo.TYPE_SMS) {
                res = matchOriginatorSms(c, fi, orig);
            } else if (fi.mMsgType == FilterInfo.TYPE_MMS) {
                res = matchOriginatorMms(c, fi, orig);
            } else {
                if(D) Log.d(TAG, "matchOriginator: Unknown msg type: " + fi.mMsgType);
                res = false;
            }
        } else {
            res = true;
        }
        return res;
    }

    private boolean matchAddresses(Cursor c, FilterInfo fi, BluetoothMapAppParams ap) {
        if (matchOriginator(c, fi, ap) && matchRecipient(c, fi, ap)) {
            return true;
        } else {
            return false;
        }
    }

    /*
     * Where filter functions
     * */
    private String setWhereFilterFolderTypeSms(String folder) {
        String where = "";
        if (BluetoothMapContract.FOLDER_NAME_INBOX.equalsIgnoreCase(folder)) {
            where = Sms.TYPE + " = 1 AND " + Sms.THREAD_ID + " <> -1";
        } else if (BluetoothMapContract.FOLDER_NAME_OUTBOX.equalsIgnoreCase(folder)) {
            where = "(" + Sms.TYPE + " = 4 OR " + Sms.TYPE + " = 5 OR "
                    + Sms.TYPE + " = 6) AND " + Sms.THREAD_ID + " <> -1";
        } else if (BluetoothMapContract.FOLDER_NAME_SENT.equalsIgnoreCase(folder)) {
            where = Sms.TYPE + " = 2 AND " + Sms.THREAD_ID + " <> -1";
        } else if (BluetoothMapContract.FOLDER_NAME_DRAFT.equalsIgnoreCase(folder)) {
            where = Sms.TYPE + " = 3 AND " +
                "(" + Sms.THREAD_ID + " IS NULL OR " + Sms.THREAD_ID + " <> -1 )";
        } else if (BluetoothMapContract.FOLDER_NAME_DELETED.equalsIgnoreCase(folder)) {
            where = Sms.THREAD_ID + " = -1";
        }

        return where;
    }

    private String setWhereFilterFolderTypeMms(String folder) {
        String where = "";
        if (BluetoothMapContract.FOLDER_NAME_INBOX.equalsIgnoreCase(folder)) {
            where = Mms.MESSAGE_BOX + " = 1 AND " + Mms.THREAD_ID + " <> -1";
        } else if (BluetoothMapContract.FOLDER_NAME_OUTBOX.equalsIgnoreCase(folder)) {
            where = Mms.MESSAGE_BOX + " = 4 AND " + Mms.THREAD_ID + " <> -1";
        } else if (BluetoothMapContract.FOLDER_NAME_SENT.equalsIgnoreCase(folder)) {
            where = Mms.MESSAGE_BOX + " = 2 AND " + Mms.THREAD_ID + " <> -1";
        } else if (BluetoothMapContract.FOLDER_NAME_DRAFT.equalsIgnoreCase(folder)) {
            where = Mms.MESSAGE_BOX + " = 3 AND " +
                "(" + Mms.THREAD_ID + " IS NULL OR " + Mms.THREAD_ID + " <> -1 )";
        } else if (BluetoothMapContract.FOLDER_NAME_DELETED.equalsIgnoreCase(folder)) {
            where = Mms.THREAD_ID + " = -1";
        }

        return where;
    }

    private String setWhereFilterFolderTypeEmail(long folderId) {
        String where = "";
        if (folderId >= 0) {
            where = BluetoothMapContract.MessageColumns.FOLDER_ID + " = " + folderId;
        } else {
            Log.e(TAG, "setWhereFilterFolderTypeEmail: not valid!" );
            throw new IllegalArgumentException("Invalid folder ID");
        }
        return where;
    }

    private String setWhereFilterFolderTypeIm(long folderId) {
        String where = "";
        if (folderId > BluetoothMapContract.FOLDER_ID_OTHER) {
            where = BluetoothMapContract.MessageColumns.FOLDER_ID + " = " + folderId;
        } else {
            Log.e(TAG, "setWhereFilterFolderTypeIm: not valid!" );
            throw new IllegalArgumentException("Invalid folder ID");
        }
        return where;
    }

    private String setWhereFilterFolderType(BluetoothMapFolderElement folderElement,
                                            FilterInfo fi) {
        String where = "1=1";
        if (!folderElement.shouldIgnore()) {
            if (fi.mMsgType == FilterInfo.TYPE_SMS) {
                where = setWhereFilterFolderTypeSms(folderElement.getName());
            } else if (fi.mMsgType == FilterInfo.TYPE_MMS) {
                where = setWhereFilterFolderTypeMms(folderElement.getName());
            } else if (fi.mMsgType == FilterInfo.TYPE_EMAIL) {
                where = setWhereFilterFolderTypeEmail(folderElement.getFolderId());
            } else if (fi.mMsgType == FilterInfo.TYPE_IM) {
                where = setWhereFilterFolderTypeIm(folderElement.getFolderId());
            }
        }

        return where;
    }

    private String setWhereFilterReadStatus(BluetoothMapAppParams ap, FilterInfo fi) {
        String where = "";
        if (ap.getFilterReadStatus() != -1) {
            if (fi.mMsgType == FilterInfo.TYPE_SMS) {
                if ((ap.getFilterReadStatus() & 0x01) != 0) {
                    where = " AND " + Sms.READ + "= 0";
                }

                if ((ap.getFilterReadStatus() & 0x02) != 0) {
                    where = " AND " + Sms.READ + "= 1";
                }
            } else if (fi.mMsgType == FilterInfo.TYPE_MMS) {
                if ((ap.getFilterReadStatus() & 0x01) != 0) {
                    where = " AND " + Mms.READ + "= 0";
                }

                if ((ap.getFilterReadStatus() & 0x02) != 0) {
                    where = " AND " + Mms.READ + "= 1";
                }
            } else if (fi.mMsgType == FilterInfo.TYPE_EMAIL ||
                       fi.mMsgType == FilterInfo.TYPE_IM) {
                if ((ap.getFilterReadStatus() & 0x01) != 0) {
                    where = " AND " + BluetoothMapContract.MessageColumns.FLAG_READ + "= 0";
                }
                if ((ap.getFilterReadStatus() & 0x02) != 0) {
                    where = " AND " + BluetoothMapContract.MessageColumns.FLAG_READ + "= 1";
                }
            }
        }
        return where;
    }

    private String setWhereFilterPeriod(BluetoothMapAppParams ap, FilterInfo fi) {
        String where = "";

        if ((ap.getFilterPeriodBegin() != -1)) {
            if (fi.mMsgType == FilterInfo.TYPE_SMS) {
                where = " AND " + Sms.DATE + " >= " + ap.getFilterPeriodBegin();
            } else if (fi.mMsgType == FilterInfo.TYPE_MMS) {
                where = " AND " + Mms.DATE + " >= " + (ap.getFilterPeriodBegin() / 1000L);
            } else if (fi.mMsgType == FilterInfo.TYPE_EMAIL||
                       fi.mMsgType == FilterInfo.TYPE_IM) {
                where = " AND " + BluetoothMapContract.MessageColumns.DATE +
                        " >= " + (ap.getFilterPeriodBegin());
            }
        }

        if ((ap.getFilterPeriodEnd() != -1)) {
            if (fi.mMsgType == FilterInfo.TYPE_SMS) {
                where += " AND " + Sms.DATE + " < " + ap.getFilterPeriodEnd();
            } else if (fi.mMsgType == FilterInfo.TYPE_MMS) {
                where += " AND " + Mms.DATE + " < " + (ap.getFilterPeriodEnd() / 1000L);
            } else if (fi.mMsgType == FilterInfo.TYPE_EMAIL||
                       fi.mMsgType == FilterInfo.TYPE_IM) {
                where += " AND " + BluetoothMapContract.MessageColumns.DATE +
                        " < " + (ap.getFilterPeriodEnd());
            }
        }
        return where;
    }
    private String setWhereFilterLastActivity(BluetoothMapAppParams ap, FilterInfo fi) {
            String where = "";
        if ((ap.getFilterLastActivityBegin() != -1)) {
            if (fi.mMsgType == FilterInfo.TYPE_SMS) {
                where = " AND " + Sms.DATE + " >= " + ap.getFilterLastActivityBegin();
            } else if (fi.mMsgType == FilterInfo.TYPE_MMS) {
                where = " AND " + Mms.DATE + " >= " + (ap.getFilterLastActivityBegin() / 1000L);
            } else if (fi.mMsgType == FilterInfo.TYPE_EMAIL||
                      fi.mMsgType == FilterInfo.TYPE_IM ) {
                where = " AND " + BluetoothMapContract.ConversationColumns.LAST_THREAD_ACTIVITY +
                        " >= " + (ap.getFilterPeriodBegin());
            }
        }
        if ((ap.getFilterLastActivityEnd() != -1)) {
            if (fi.mMsgType == FilterInfo.TYPE_SMS) {
                where += " AND " + Sms.DATE + " < " + ap.getFilterLastActivityEnd();
            } else if (fi.mMsgType == FilterInfo.TYPE_MMS) {
                where += " AND " + Mms.DATE + " < " + (ap.getFilterPeriodEnd() / 1000L);
            } else if (fi.mMsgType == FilterInfo.TYPE_EMAIL||fi.mMsgType == FilterInfo.TYPE_IM) {
                where += " AND " + BluetoothMapContract.ConversationColumns.LAST_THREAD_ACTIVITY
                      + " < " + (ap.getFilterLastActivityEnd());
            }
        }
        return where;
    }


    private String setWhereFilterOriginatorEmail(BluetoothMapAppParams ap) {
        String where = "";
        String orig = ap.getFilterOriginator();

        /* Be aware of wild cards in the beginning of string, may not be valid? */
        if (orig != null && orig.length() > 0) {
            orig = orig.replace("*", "%");
            where = " AND " + BluetoothMapContract.MessageColumns.FROM_LIST
                    + " LIKE '%" +  orig + "%'";
        }
        return where;
    }

    private String setWhereFilterOriginatorIM(BluetoothMapAppParams ap) {
        String where = "";
        String orig = ap.getFilterOriginator();

        /* Be aware of wild cards in the beginning of string, may not be valid? */
        if (orig != null && orig.length() > 0) {
            orig = orig.replace("*", "%");
            where = " AND " + BluetoothMapContract.MessageColumns.FROM_LIST
                    + " LIKE '%" +  orig + "%'";
        }
        return where;
    }

    private String setWhereFilterPriority(BluetoothMapAppParams ap, FilterInfo fi) {
        String where = "";
        int pri = ap.getFilterPriority();
        /*only MMS have priority info */
        if(fi.mMsgType == FilterInfo.TYPE_MMS)
        {
            if(pri == 0x0002)
            {
                where += " AND " + Mms.PRIORITY + "<=" +
                    Integer.toString(PduHeaders.PRIORITY_NORMAL);
            }else if(pri == 0x0001) {
                where += " AND " + Mms.PRIORITY + "=" +
                    Integer.toString(PduHeaders.PRIORITY_HIGH);
            }
        }
        if(fi.mMsgType == FilterInfo.TYPE_EMAIL ||
           fi.mMsgType == FilterInfo.TYPE_IM)
        {
            if(pri == 0x0002)
            {
                where += " AND " + BluetoothMapContract.MessageColumns.FLAG_HIGH_PRIORITY + "!=1";
            }else if(pri == 0x0001) {
                where += " AND " + BluetoothMapContract.MessageColumns.FLAG_HIGH_PRIORITY + "=1";
            }
        }
        // TODO: no priority filtering in IM
        return where;
    }

    private String setWhereFilterRecipientEmail(BluetoothMapAppParams ap) {
        String where = "";
        String recip = ap.getFilterRecipient();

        /* Be aware of wild cards in the beginning of string, may not be valid? */
        if (recip != null && recip.length() > 0) {
            recip = recip.replace("*", "%");
            where = " AND ("
            + BluetoothMapContract.MessageColumns.TO_LIST  + " LIKE '%" + recip + "%' OR "
            + BluetoothMapContract.MessageColumns.CC_LIST  + " LIKE '%" + recip + "%' OR "
            + BluetoothMapContract.MessageColumns.BCC_LIST + " LIKE '%" + recip + "%' )";
        }
        return where;
    }

    private String setWhereFilterMessageHandle(BluetoothMapAppParams ap, FilterInfo fi) {
        String where = "";
        long id = -1;
        String msgHandle = ap.getFilterMsgHandleString();
        if(msgHandle != null) {
            id = BluetoothMapUtils.getCpHandle(msgHandle);
            if(D)Log.d(TAG,"id: " + id);
        }
        if(id != -1) {
            if (fi.mMsgType == FilterInfo.TYPE_SMS) {
               where = " AND " + Sms._ID + " = " + id;
            } else if (fi.mMsgType == FilterInfo.TYPE_MMS) {
                where = " AND " + Mms._ID + " = " + id;
            } else if (fi.mMsgType == FilterInfo.TYPE_EMAIL ||
                       fi.mMsgType == FilterInfo.TYPE_IM) {
                where = " AND " + BluetoothMapContract.MessageColumns._ID + " = " + id;
            }
        }
        return where;
    }

    private String setWhereFilterThreadId(BluetoothMapAppParams ap, FilterInfo fi) {
        String where = "";
        long id = -1;
        String msgHandle = ap.getFilterConvoIdString();
        if(msgHandle != null) {
            id = BluetoothMapUtils.getMsgHandleAsLong(msgHandle);
            if(D)Log.d(TAG,"id: " + id);
        }
        if(id > 0) {
            if (fi.mMsgType == FilterInfo.TYPE_SMS) {
               where = " AND " + Sms.THREAD_ID + " = " + id;
            } else if (fi.mMsgType == FilterInfo.TYPE_MMS) {
                where = " AND " + Mms.THREAD_ID + " = " + id;
            } else if (fi.mMsgType == FilterInfo.TYPE_EMAIL ||
                       fi.mMsgType == FilterInfo.TYPE_IM) {
                where = " AND " + BluetoothMapContract.MessageColumns.THREAD_ID + " = " + id;
            }
        }

        return where;
    }

    private String setWhereFilter(BluetoothMapFolderElement folderElement,
            FilterInfo fi, BluetoothMapAppParams ap) {
        String where = "";
        where += setWhereFilterFolderType(folderElement, fi);

        String msgHandleWhere = setWhereFilterMessageHandle(ap, fi);
        /* if message handle filter is available, the other filters should be ignored */
        if(msgHandleWhere.isEmpty()) {
            where += setWhereFilterReadStatus(ap, fi);
            where += setWhereFilterPriority(ap,fi);
            where += setWhereFilterPeriod(ap, fi);
            if (fi.mMsgType == FilterInfo.TYPE_EMAIL) {
                where += setWhereFilterOriginatorEmail(ap);
                where += setWhereFilterRecipientEmail(ap);
            }
            if (fi.mMsgType == FilterInfo.TYPE_IM) {
                where += setWhereFilterOriginatorIM(ap);
                // TODO: set 'where' filer recipient?
            }
            where += setWhereFilterThreadId(ap, fi);
        } else {
            where += msgHandleWhere;
        }

        return where;
    }


    /* Used only for SMS/MMS */
    private void setConvoWhereFilterSmsMms(StringBuilder selection, ArrayList<String> selectionArgs,
            FilterInfo fi, BluetoothMapAppParams ap) {

        if (smsSelected(fi, ap) || mmsSelected(ap)) {

            // Filter Read Status
            if(ap.getFilterReadStatus() != BluetoothMapAppParams.INVALID_VALUE_PARAMETER) {
                if ((ap.getFilterReadStatus() & FILTER_READ_STATUS_UNREAD_ONLY) != 0) {
                    selection.append(" AND ").append(Threads.READ).append(" = 0");
                }
                if ((ap.getFilterReadStatus() & FILTER_READ_STATUS_READ_ONLY) != 0) {
                    selection.append(" AND ").append(Threads.READ).append(" = 1");
                }
            }

            // Filter time
            if ((ap.getFilterLastActivityBegin() != BluetoothMapAppParams.INVALID_VALUE_PARAMETER)){
                selection.append(" AND ").append(Threads.DATE).append(" >= ")
                .append(ap.getFilterLastActivityBegin());
            }
            if ((ap.getFilterLastActivityEnd() != BluetoothMapAppParams.INVALID_VALUE_PARAMETER)) {
                selection.append(" AND ").append(Threads.DATE).append(" <= ")
                .append(ap.getFilterLastActivityEnd());
            }

            // Filter ConvoId
            long convoId = -1;
            if(ap.getFilterConvoId() != null) {
                convoId = ap.getFilterConvoId().getLeastSignificantBits();
            }
            if(convoId > 0) {
                selection.append(" AND ").append(Threads._ID).append(" = ")
                .append(Long.toString(convoId));
            }
        }
    }



    /**
     * Determine from application parameter if sms should be included.
     * The filter mask is set for message types not selected
     * @param fi
     * @param ap
     * @return boolean true if sms is selected, false if not
     */
    private boolean smsSelected(FilterInfo fi, BluetoothMapAppParams ap) {
        int msgType = ap.getFilterMessageType();
        int phoneType = fi.mPhoneType;

        if (D) Log.d(TAG, "smsSelected msgType: " + msgType);

        if (msgType == BluetoothMapAppParams.INVALID_VALUE_PARAMETER)
            return true;

        if ((msgType & (BluetoothMapAppParams.FILTER_NO_SMS_CDMA
                |BluetoothMapAppParams.FILTER_NO_SMS_GSM)) == 0)
            return true;

        if (((msgType & BluetoothMapAppParams.FILTER_NO_SMS_GSM) == 0)
                && (phoneType == TelephonyManager.PHONE_TYPE_GSM))
            return true;

        if (((msgType & BluetoothMapAppParams.FILTER_NO_SMS_CDMA) == 0)
                && (phoneType == TelephonyManager.PHONE_TYPE_CDMA))
            return true;

        return false;
    }

    /**
     * Determine from application parameter if mms should be included.
     * The filter mask is set for message types not selected
     * @param fi
     * @param ap
     * @return boolean true if mms is selected, false if not
     */
    private boolean mmsSelected(BluetoothMapAppParams ap) {
        int msgType = ap.getFilterMessageType();

        if (D) Log.d(TAG, "mmsSelected msgType: " + msgType);

        if (msgType == BluetoothMapAppParams.INVALID_VALUE_PARAMETER)
            return true;

        if ((msgType & BluetoothMapAppParams.FILTER_NO_MMS) == 0)
            return true;

        return false;
    }

    /**
     * Determine from application parameter if email should be included.
     * The filter mask is set for message types not selected
     * @param fi
     * @param ap
     * @return boolean true if email is selected, false if not
     */
    private boolean emailSelected(BluetoothMapAppParams ap) {
        int msgType = ap.getFilterMessageType();

        if (D) Log.d(TAG, "emailSelected msgType: " + msgType);

        if (msgType == BluetoothMapAppParams.INVALID_VALUE_PARAMETER)
            return true;

        if ((msgType & BluetoothMapAppParams.FILTER_NO_EMAIL) == 0)
            return true;

        return false;
    }

    /**
     * Determine from application parameter if IM should be included.
     * The filter mask is set for message types not selected
     * @param fi
     * @param ap
     * @return boolean true if im is selected, false if not
     */
    private boolean imSelected(BluetoothMapAppParams ap) {
        int msgType = ap.getFilterMessageType();

        if (D) Log.d(TAG, "imSelected msgType: " + msgType);

        if (msgType == BluetoothMapAppParams.INVALID_VALUE_PARAMETER)
            return true;

        if ((msgType & BluetoothMapAppParams.FILTER_NO_IM) == 0)
            return true;

        return false;
    }

    private void setFilterInfo(FilterInfo fi) {
        TelephonyManager tm =
            (TelephonyManager)mContext.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm != null) {
            fi.mPhoneType = tm.getPhoneType();
            fi.mPhoneNum = tm.getLine1Number();
            fi.mPhoneAlphaTag = tm.getLine1AlphaTag();
            if (D) Log.d(TAG, "phone type = " + fi.mPhoneType +
                " phone num = " + fi.mPhoneNum +
                " phone alpha tag = " + fi.mPhoneAlphaTag);
        }
    }

    /**
     * Get a listing of message in folder after applying filter.
     * @param folder Must contain a valid folder string != null
     * @param ap Parameters specifying message content and filters
     * @return Listing object containing requested messages
     */
    public BluetoothMapMessageListing msgListing(BluetoothMapFolderElement folderElement,
            BluetoothMapAppParams ap) {
        if (D) Log.d(TAG, "msgListing: messageType = " + ap.getFilterMessageType() );

        BluetoothMapMessageListing bmList = new BluetoothMapMessageListing();

        /* We overwrite the parameter mask here if it is 0 or not present, as this
         * should cause all parameters to be included in the message list. */
        if(ap.getParameterMask() == BluetoothMapAppParams.INVALID_VALUE_PARAMETER ||
                ap.getParameterMask() == 0) {
            ap.setParameterMask(PARAMETER_MASK_ALL_ENABLED);
            if (V) Log.v(TAG, "msgListing(): appParameterMask is zero or not present, " +
                    "changing to all enabled by default: " + ap.getParameterMask());
        }
        if (V) Log.v(TAG, "folderElement hasSmsMmsContent = " + folderElement.hasSmsMmsContent() +
                " folderElement.hasEmailContent = " + folderElement.hasEmailContent() +
                " folderElement.hasImContent = " + folderElement.hasImContent());

        /* Cache some info used throughout filtering */
        FilterInfo fi = new FilterInfo();
        setFilterInfo(fi);
        Cursor smsCursor = null;
        Cursor mmsCursor = null;
        Cursor emailCursor = null;
        Cursor imCursor = null;
        String limit = "";
        int countNum = ap.getMaxListCount();
        int offsetNum = ap.getStartOffset();
        if(ap.getMaxListCount()>0){
            limit=" LIMIT "+ (ap.getMaxListCount()+ap.getStartOffset());
        }
        try{
            if (smsSelected(fi, ap) && folderElement.hasSmsMmsContent()) {
                if(ap.getFilterMessageType() == (BluetoothMapAppParams.FILTER_NO_EMAIL|
                                                 BluetoothMapAppParams.FILTER_NO_MMS|
                                                 BluetoothMapAppParams.FILTER_NO_SMS_GSM|
                                                 BluetoothMapAppParams.FILTER_NO_IM)||
                   ap.getFilterMessageType() == (BluetoothMapAppParams.FILTER_NO_EMAIL|
                                                 BluetoothMapAppParams.FILTER_NO_MMS|
                                                 BluetoothMapAppParams.FILTER_NO_SMS_CDMA|
                                                 BluetoothMapAppParams.FILTER_NO_IM)){
                    //set real limit and offset if only this type is used
                    // (only if offset/limit is used)
                    limit = " LIMIT " + ap.getMaxListCount()+" OFFSET "+ ap.getStartOffset();
                    if(D) Log.d(TAG, "SMS Limit => "+limit);
                    offsetNum = 0;
                }
                fi.mMsgType = FilterInfo.TYPE_SMS;
                if(ap.getFilterPriority() != 1){ /*SMS cannot have high priority*/
                    String where = setWhereFilter(folderElement, fi, ap);
                    if (D) Log.d(TAG, "msgType: " + fi.mMsgType + " where: " + where);
                    smsCursor = mResolver.query(Sms.CONTENT_URI,
                            SMS_PROJECTION, where, null, Sms.DATE + " DESC" + limit);
                    if (smsCursor != null) {
                        BluetoothMapMessageListingElement e = null;
                        // store column index so we dont have to look them up anymore (optimization)
                        if(D) Log.d(TAG, "Found " + smsCursor.getCount() + " sms messages.");
                        fi.setSmsColumns(smsCursor);
                        while (smsCursor.moveToNext()) {
                            if (matchAddresses(smsCursor, fi, ap)) {
                                if(V) BluetoothMapUtils.printCursor(smsCursor);
                                e = element(smsCursor, fi, ap);
                                bmList.add(e);
                            }
                        }
                    }
                }
            }

            if (mmsSelected(ap) && folderElement.hasSmsMmsContent()) {
                if(ap.getFilterMessageType() == (BluetoothMapAppParams.FILTER_NO_EMAIL|
                                                 BluetoothMapAppParams.FILTER_NO_SMS_CDMA|
                                                 BluetoothMapAppParams.FILTER_NO_SMS_GSM|
                                                 BluetoothMapAppParams.FILTER_NO_IM)){
                    //set real limit and offset if only this type is used
                    //(only if offset/limit is used)
                    limit = " LIMIT " + ap.getMaxListCount()+" OFFSET "+ ap.getStartOffset();
                    if(D) Log.d(TAG, "MMS Limit => "+limit);
                    offsetNum = 0;
                }
                fi.mMsgType = FilterInfo.TYPE_MMS;
                String where = setWhereFilter(folderElement, fi, ap);
                where += " AND " + INTERESTED_MESSAGE_TYPE_CLAUSE;
                if(!where.isEmpty()) {
                    if (D) Log.d(TAG, "msgType: " + fi.mMsgType + " where: " + where);
                    mmsCursor = mResolver.query(Mms.CONTENT_URI,
                            MMS_PROJECTION, where, null, Mms.DATE + " DESC" + limit);
                    if (mmsCursor != null) {
                        BluetoothMapMessageListingElement e = null;
                        // store column index so we dont have to look them up anymore (optimization)
                        fi.setMmsColumns(mmsCursor);
                        if(D) Log.d(TAG, "Found " + mmsCursor.getCount() + " mms messages.");
                        while (mmsCursor.moveToNext()) {
                            if (matchAddresses(mmsCursor, fi, ap)) {
                                if(V) BluetoothMapUtils.printCursor(mmsCursor);
                                e = element(mmsCursor, fi, ap);
                                bmList.add(e);
                            }
                        }
                    }
                }
            }

            if (emailSelected(ap) && folderElement.hasEmailContent()) {
                if(ap.getFilterMessageType() == (BluetoothMapAppParams.FILTER_NO_MMS|
                                                 BluetoothMapAppParams.FILTER_NO_SMS_CDMA|
                                                 BluetoothMapAppParams.FILTER_NO_SMS_GSM|
                                                 BluetoothMapAppParams.FILTER_NO_IM)){
                    //set real limit and offset if only this type is used
                    //(only if offset/limit is used)
                    limit = " LIMIT " + ap.getMaxListCount()+" OFFSET "+ ap.getStartOffset();
                    if(D) Log.d(TAG, "Email Limit => "+limit);
                    offsetNum = 0;
                }
                fi.mMsgType = FilterInfo.TYPE_EMAIL;
                String where = setWhereFilter(folderElement, fi, ap);

                if(!where.isEmpty()) {
                    if (D) Log.d(TAG, "msgType: " + fi.mMsgType + " where: " + where);
                    Uri contentUri = Uri.parse(mBaseUri + BluetoothMapContract.TABLE_MESSAGE);
                    emailCursor = mResolver.query(contentUri,
                            BluetoothMapContract.BT_MESSAGE_PROJECTION, where, null,
                            BluetoothMapContract.MessageColumns.DATE + " DESC" + limit);
                    if (emailCursor != null) {
                        BluetoothMapMessageListingElement e = null;
                        // store column index so we dont have to look them up anymore (optimization)
                        fi.setEmailMessageColumns(emailCursor);
                        int cnt = 0;
                        if(D) Log.d(TAG, "Found " + emailCursor.getCount() + " email messages.");
                        while (emailCursor.moveToNext()) {
                            if(V) BluetoothMapUtils.printCursor(emailCursor);
                            e = element(emailCursor, fi, ap);
                            bmList.add(e);
                        }
                    //   emailCursor.close();
                    }
                }
            }

            if (imSelected(ap) && folderElement.hasImContent()) {
                if(ap.getFilterMessageType() == (BluetoothMapAppParams.FILTER_NO_MMS|
                                                 BluetoothMapAppParams.FILTER_NO_SMS_CDMA|
                                                 BluetoothMapAppParams.FILTER_NO_SMS_GSM|
                                                 BluetoothMapAppParams.FILTER_NO_EMAIL)){
                    //set real limit and offset if only this type is used
                    //(only if offset/limit is used)
                    limit = " LIMIT " + ap.getMaxListCount() + " OFFSET "+ ap.getStartOffset();
                    if(D) Log.d(TAG, "IM Limit => "+limit);
                    offsetNum = 0;
                }
                fi.mMsgType = FilterInfo.TYPE_IM;
                String where = setWhereFilter(folderElement, fi, ap);
                if (D) Log.d(TAG, "msgType: " + fi.mMsgType + " where: " + where);

                Uri contentUri = Uri.parse(mBaseUri + BluetoothMapContract.TABLE_MESSAGE);
                imCursor = mResolver.query(contentUri,
                        BluetoothMapContract.BT_INSTANT_MESSAGE_PROJECTION,
                        where, null, BluetoothMapContract.MessageColumns.DATE + " DESC" + limit);
                if (imCursor != null) {
                    BluetoothMapMessageListingElement e = null;
                    // store column index so we dont have to look them up anymore (optimization)
                    fi.setImMessageColumns(imCursor);
                    if (D) Log.d(TAG, "Found " + imCursor.getCount() + " im messages.");
                    while (imCursor.moveToNext()) {
                        if (V) BluetoothMapUtils.printCursor(imCursor);
                        e = element(imCursor, fi, ap);
                        bmList.add(e);
                    }
                }
            }

            /* Enable this if post sorting and segmenting needed */
            bmList.sort();
            bmList.segment(ap.getMaxListCount(), offsetNum);
            List<BluetoothMapMessageListingElement> list = bmList.getList();
            int listSize = list.size();
            Cursor tmpCursor = null;
            for(int x=0;x<listSize;x++){
                BluetoothMapMessageListingElement ele = list.get(x);
                /* If OBEX "GET" request header includes "ParameterMask" with 'Type' NOT set,
                 * then ele.getType() returns "null" even for a valid cursor.
                 * Avoid NullPointerException in equals() check when 'mType' value is "null" */
                TYPE tmpType = ele.getType();
                if (smsCursor!= null &&
                        ((TYPE.SMS_GSM).equals(tmpType) || (TYPE.SMS_CDMA).equals(tmpType))) {
                    tmpCursor = smsCursor;
                    fi.mMsgType = FilterInfo.TYPE_SMS;
                } else if(mmsCursor != null && (TYPE.MMS).equals(tmpType)) {
                    tmpCursor = mmsCursor;
                    fi.mMsgType = FilterInfo.TYPE_MMS;
                } else if(emailCursor != null && ((TYPE.EMAIL).equals(tmpType))) {
                    tmpCursor = emailCursor;
                    fi.mMsgType = FilterInfo.TYPE_EMAIL;
                } else if(imCursor != null && ((TYPE.IM).equals(tmpType))) {
                    tmpCursor = imCursor;
                    fi.mMsgType = FilterInfo.TYPE_IM;
                }
                if(tmpCursor != null){
                    tmpCursor.moveToPosition(ele.getCursorIndex());
                    setSenderAddressing(ele, tmpCursor, fi, ap);
                    setSenderName(ele, tmpCursor, fi, ap);
                    setRecipientAddressing(ele, tmpCursor, fi, ap);
                    setRecipientName(ele, tmpCursor, fi, ap);
                    setSubject(ele, tmpCursor, fi, ap);
                    setSize(ele, tmpCursor, fi, ap);
                    setText(ele, tmpCursor, fi, ap);
                    setPriority(ele, tmpCursor, fi, ap);
                    setSent(ele, tmpCursor, fi, ap);
                    setProtected(ele, tmpCursor, fi, ap);
                    setReceptionStatus(ele, tmpCursor, fi, ap);
                    setAttachment(ele, tmpCursor, fi, ap);

                    if(mMsgListingVersion > BluetoothMapUtils.MAP_MESSAGE_LISTING_FORMAT_V10 ){
                        setDeliveryStatus(ele, tmpCursor, fi, ap);
                        setThreadId(ele, tmpCursor, fi, ap);
                        setThreadName(ele, tmpCursor, fi, ap);
                        setFolderType(ele, tmpCursor, fi, ap);
                    }
                }
            }
        } finally {
            if(emailCursor != null)emailCursor.close();
            if(smsCursor != null)smsCursor.close();
            if(mmsCursor != null)mmsCursor.close();
            if(imCursor != null)imCursor.close();
        }


        if(D)Log.d(TAG, "messagelisting end");
        return bmList;
    }

    /**
     * Get the size of the message listing
     * @param folder Must contain a valid folder string != null
     * @param ap Parameters specifying message content and filters
     * @return Integer equal to message listing size
     */
    public int msgListingSize(BluetoothMapFolderElement folderElement,
            BluetoothMapAppParams ap) {
        if (D) Log.d(TAG, "msgListingSize: folder = " + folderElement.getName());
        int cnt = 0;

        /* Cache some info used throughout filtering */
        FilterInfo fi = new FilterInfo();
        setFilterInfo(fi);

        if (smsSelected(fi, ap) && folderElement.hasSmsMmsContent()) {
            fi.mMsgType = FilterInfo.TYPE_SMS;
            String where = setWhereFilter(folderElement, fi, ap);
            Cursor c = mResolver.query(Sms.CONTENT_URI,
                    SMS_PROJECTION, where, null, Sms.DATE + " DESC");
            try {
                if (c != null) {
                    cnt = c.getCount();
                }
            } finally {
                if (c != null) c.close();
            }
        }

        if (mmsSelected(ap)  && folderElement.hasSmsMmsContent()) {
            fi.mMsgType = FilterInfo.TYPE_MMS;
            String where = setWhereFilter(folderElement, fi, ap);
            Cursor c = mResolver.query(Mms.CONTENT_URI,
                    MMS_PROJECTION, where, null, Mms.DATE + " DESC");
            try {
                if (c != null) {
                    cnt += c.getCount();
                }
            } finally {
                if (c != null) c.close();
            }
        }

        if (emailSelected(ap) && folderElement.hasEmailContent()) {
            fi.mMsgType = FilterInfo.TYPE_EMAIL;
            String where = setWhereFilter(folderElement, fi, ap);
            if(!where.isEmpty()) {
                Uri contentUri = Uri.parse(mBaseUri + BluetoothMapContract.TABLE_MESSAGE);
                Cursor c = mResolver.query(contentUri, BluetoothMapContract.BT_MESSAGE_PROJECTION,
                        where, null, BluetoothMapContract.MessageColumns.DATE + " DESC");
                try {
                    if (c != null) {
                        cnt += c.getCount();
                    }
                } finally {
                    if (c != null) c.close();
                }
            }
        }

        if (imSelected(ap) && folderElement.hasImContent()) {
            fi.mMsgType = FilterInfo.TYPE_IM;
            String where = setWhereFilter(folderElement, fi, ap);
            if(!where.isEmpty()) {
                Uri contentUri = Uri.parse(mBaseUri + BluetoothMapContract.TABLE_MESSAGE);
                Cursor c = mResolver.query(contentUri,
                        BluetoothMapContract.BT_INSTANT_MESSAGE_PROJECTION,
                        where, null, BluetoothMapContract.MessageColumns.DATE + " DESC");
                try {
                    if (c != null) {
                        cnt += c.getCount();
                    }
                } finally {
                    if (c != null) c.close();
                }
            }
        }

        if (D) Log.d(TAG, "msgListingSize: size = " + cnt);
        return cnt;
    }

    /**
     * Return true if there are unread messages in the requested list of messages
     * @param folder folder where the message listing should come from
     * @param ap application parameter object
     * @return true if unread messages are in the list, else false
     */
    public boolean msgListingHasUnread(BluetoothMapFolderElement folderElement,
            BluetoothMapAppParams ap) {
        if (D) Log.d(TAG, "msgListingHasUnread: folder = " + folderElement.getName());
        int cnt = 0;

        /* Cache some info used throughout filtering */
        FilterInfo fi = new FilterInfo();
        setFilterInfo(fi);

       if (smsSelected(fi, ap)  && folderElement.hasSmsMmsContent()) {
            fi.mMsgType = FilterInfo.TYPE_SMS;
            String where = setWhereFilterFolderType(folderElement, fi);
            where += " AND " + Sms.READ + "=0 ";
            where += setWhereFilterPeriod(ap, fi);
            Cursor c = mResolver.query(Sms.CONTENT_URI,
                SMS_PROJECTION, where, null, Sms.DATE + " DESC");
            try {
                if (c != null) {
                    cnt = c.getCount();
                }
            } finally {
                if (c != null) c.close();
            }
        }

        if (mmsSelected(ap)  && folderElement.hasSmsMmsContent()) {
            fi.mMsgType = FilterInfo.TYPE_MMS;
            String where = setWhereFilterFolderType(folderElement, fi);
            where += " AND " + Mms.READ + "=0 ";
            where += setWhereFilterPeriod(ap, fi);
            Cursor c = mResolver.query(Mms.CONTENT_URI,
                MMS_PROJECTION, where, null, Sms.DATE + " DESC");
            try {
                if (c != null) {
                    cnt += c.getCount();
                }
            } finally {
                if (c != null) c.close();
            }
        }


        if (emailSelected(ap) && folderElement.getFolderId() != -1) {
            fi.mMsgType = FilterInfo.TYPE_EMAIL;
            String where = setWhereFilterFolderType(folderElement, fi);
            if(!where.isEmpty()) {
                where += " AND " + BluetoothMapContract.MessageColumns.FLAG_READ + "=0 ";
                where += setWhereFilterPeriod(ap, fi);
                Uri contentUri = Uri.parse(mBaseUri + BluetoothMapContract.TABLE_MESSAGE);
                Cursor c = mResolver.query(contentUri, BluetoothMapContract.BT_MESSAGE_PROJECTION,
                        where, null, BluetoothMapContract.MessageColumns.DATE + " DESC");
                try {
                    if (c != null) {
                        cnt += c.getCount();
                    }
                } finally {
                    if (c != null) c.close();
                }
            }
        }

        if (imSelected(ap) && folderElement.hasImContent()) {
            fi.mMsgType = FilterInfo.TYPE_IM;
            String where = setWhereFilter(folderElement, fi, ap);
            if(!where.isEmpty()) {
                where += " AND " + BluetoothMapContract.MessageColumns.FLAG_READ + "=0 ";
                where += setWhereFilterPeriod(ap, fi);
                Uri contentUri = Uri.parse(mBaseUri + BluetoothMapContract.TABLE_MESSAGE);
                Cursor c = mResolver.query(contentUri,
                        BluetoothMapContract.BT_INSTANT_MESSAGE_PROJECTION,
                        where, null, BluetoothMapContract.MessageColumns.DATE + " DESC");
                try {
                    if (c != null) {
                        cnt += c.getCount();
                    }
                } finally {
                    if (c != null) c.close();
                }
            }
        }

        if (D) Log.d(TAG, "msgListingHasUnread: numUnread = " + cnt);
        return (cnt>0)?true:false;
    }

    /**
     * Build the conversation listing.
     * @param ap The Application Parameters
     * @param sizeOnly TRUE: don't populate the list members, only build the list to get the size.
     * @return
     */
    public BluetoothMapConvoListing convoListing(BluetoothMapAppParams ap, boolean sizeOnly) {

        if (D) Log.d(TAG, "convoListing: " + " messageType = " + ap.getFilterMessageType() );
        BluetoothMapConvoListing convoList = new BluetoothMapConvoListing();

        /* We overwrite the parameter mask here if it is 0 or not present, as this
         * should cause all parameters to be included in the message list. */
        if(ap.getConvoParameterMask() == BluetoothMapAppParams.INVALID_VALUE_PARAMETER ||
                ap.getConvoParameterMask() == 0) {
            ap.setConvoParameterMask(CONVO_PARAMETER_MASK_DEFAULT);
            if (D) Log.v(TAG, "convoListing(): appParameterMask is zero or not present, " +
                    "changing to default: " + ap.getConvoParameterMask());
        }

        /* Possible filters:
         *  - Recipient name (contacts DB) or id (for SMS/MMS this is the thread-id contact-id)
         *  - Activity start/begin
         *  - Read status
         *  - Thread_id
         * The strategy for SMS/MMS
         *   With no filter on name - use limit and offset.
         *   With a filter on name - build the complete list of conversations and create a filter
         *                           mechanism
         *
         * The strategy for IM:
         *   Join the conversation table with the contacts table in a way that makes it possible to
         *   get the data needed in a single query.
         *   Manually handle limit/offset
         * */

        /* Cache some info used throughout filtering */
        FilterInfo fi = new FilterInfo();
        setFilterInfo(fi);
        Cursor smsMmsCursor = null;
        Cursor imEmailCursor = null;
        int offsetNum;
        if(sizeOnly) {
            offsetNum = 0;
        } else {
            offsetNum = ap.getStartOffset();
        }
        // Inverse meaning - hence a 1 is include.
        int msgTypesInclude = ((~ap.getFilterMessageType())
                & BluetoothMapAppParams.FILTER_MSG_TYPE_MASK);
        int maxThreads = ap.getMaxListCount()+ap.getStartOffset();


        try {
            if (smsSelected(fi, ap) || mmsSelected(ap)) {
                String limit = "";
                if((sizeOnly == false) && (ap.getMaxListCount()>0) &&
                        (ap.getFilterRecipient()==null)){
                    /* We can only use limit if we do not have a contacts filter */
                    limit=" LIMIT " + maxThreads;
                }
                StringBuilder sortOrder = new StringBuilder(Threads.DATE + " DESC");
                if((sizeOnly == false) &&
                        ((msgTypesInclude & ~(BluetoothMapAppParams.FILTER_NO_SMS_GSM |
                        BluetoothMapAppParams.FILTER_NO_SMS_CDMA) |
                        BluetoothMapAppParams.FILTER_NO_MMS) == 0)
                        && ap.getFilterRecipient() == null){
                    // SMS/MMS messages only and no recipient filter - use optimization.
                    limit = " LIMIT " + ap.getMaxListCount()+" OFFSET "+ ap.getStartOffset();
                    if(D) Log.d(TAG, "SMS Limit => "+limit);
                    offsetNum = 0;
                }
                StringBuilder selection = new StringBuilder(120); // This covers most cases
                ArrayList<String> selectionArgs = new ArrayList<String>(12); // Covers all cases
                selection.append("1=1 "); // just to simplify building the where-clause
                setConvoWhereFilterSmsMms(selection, selectionArgs, fi, ap);
                String[] args = null;
                if(selectionArgs.size() > 0) {
                    args = new String[selectionArgs.size()];
                    selectionArgs.toArray(args);
                }
                Uri uri = Threads.CONTENT_URI.buildUpon()
                        .appendQueryParameter("simple", "true").build();
                sortOrder.append(limit);
                if(D) Log.d(TAG, "Query using selection: " + selection.toString() +
                        " - sortOrder: " + sortOrder.toString());
                // TODO: Optimize: Reduce projection based on convo parameter mask
                smsMmsCursor = mResolver.query(uri, MMS_SMS_THREAD_PROJECTION, selection.toString(),
                        args, sortOrder.toString());
                if (smsMmsCursor != null) {
                    // store column index so we don't have to look them up anymore (optimization)
                    if(D) Log.d(TAG, "Found " + smsMmsCursor.getCount()
                            + " sms/mms conversations.");
                    BluetoothMapConvoListingElement convoElement = null;
                    smsMmsCursor.moveToPosition(-1);
                    if(ap.getFilterRecipient() == null) {
                        int count = 0;
                        // We have no Recipient filter, add contacts after the list is reduced
                        while (smsMmsCursor.moveToNext()) {
                            convoElement = createConvoElement(smsMmsCursor, fi, ap);
                            convoList.add(convoElement);
                            count++;
                            if(sizeOnly == false && count >= maxThreads) {
                                break;
                            }
                        }
                    } else {
                        // We must be able to filter on recipient, add contacts now
                        SmsMmsContacts contacts = new SmsMmsContacts();
                        while (smsMmsCursor.moveToNext()) {
                            int count = 0;
                            convoElement = createConvoElement(smsMmsCursor, fi, ap);
                            String idsStr =
                                    smsMmsCursor.getString(MMS_SMS_THREAD_COL_RECIPIENT_IDS);
                            // Add elements only if we do find a contact - if not we cannot apply
                            // the filter, hence the item is irrelevant
                            // TODO: Perhaps the spec. should be changes to be able to search on
                            //       phone number as well?
                            if(addSmsMmsContacts(convoElement, contacts, idsStr,
                                    ap.getFilterRecipient(), ap)) {
                                convoList.add(convoElement);
                                if(sizeOnly == false && count >= maxThreads) {
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            if (emailSelected(ap) || imSelected(ap)) {
                int count = 0;
                if(emailSelected(ap)) {
                    fi.mMsgType = FilterInfo.TYPE_EMAIL;
                } else if(imSelected(ap)) {
                    fi.mMsgType = FilterInfo.TYPE_IM;
                }
                if (D) Log.d(TAG, "msgType: " + fi.mMsgType);
                Uri contentUri = Uri.parse(mBaseUri + BluetoothMapContract.TABLE_CONVERSATION);

                contentUri = appendConvoListQueryParameters(ap, contentUri);
                if(V) Log.v(TAG, "URI with parameters: " + contentUri.toString());
                // TODO: Optimize: Reduce projection based on convo parameter mask
                imEmailCursor = mResolver.query(contentUri,
                        BluetoothMapContract.BT_CONVERSATION_PROJECTION,
                        null, null, BluetoothMapContract.ConversationColumns.LAST_THREAD_ACTIVITY
                        + " DESC, " + BluetoothMapContract.ConversationColumns.THREAD_ID
                        + " ASC");
                if (imEmailCursor != null) {
                    BluetoothMapConvoListingElement e = null;
                    // store column index so we don't have to look them up anymore (optimization)
                    // Here we rely on only a single account-based message type for each MAS.
                    fi.setEmailImConvoColumns(imEmailCursor);
                    boolean isValid = imEmailCursor.moveToNext();
                    if(D) Log.d(TAG, "Found " + imEmailCursor.getCount()
                            + " EMAIL/IM conversations. isValid = " + isValid);
                    while (isValid && ((sizeOnly == true) || (count < maxThreads))) {
                        long threadId = imEmailCursor.getLong(fi.mConvoColConvoId);
                        long nextThreadId;
                        count ++;
                        e = createConvoElement(imEmailCursor, fi, ap);
                        convoList.add(e);

                        do {
                            nextThreadId = imEmailCursor.getLong(fi.mConvoColConvoId);
                            if(V) Log.i(TAG, "  threadId = " + threadId + " newThreadId = " +
                                    nextThreadId);
                            // TODO: This seems rather inefficient in the case where we do not need
                            //       to reduce the list.
                        } while ((nextThreadId == threadId) &&
                                (isValid = imEmailCursor.moveToNext() == true));
                    }
                }
            }

            if(D) Log.d(TAG, "Done adding conversations - list size:" +
                    convoList.getCount());

            // If sizeOnly - we are all done here - return the list as is - no need to populate the
            // list.
            if(sizeOnly) {
                return convoList;
            }

            /* Enable this if post sorting and segmenting needed */
            /* This is too early */
            convoList.sort();
            convoList.segment(ap.getMaxListCount(), offsetNum);
            List<BluetoothMapConvoListingElement> list = convoList.getList();
            int listSize = list.size();
            if(V) Log.i(TAG, "List Size:" + listSize);
            Cursor tmpCursor = null;
            SmsMmsContacts contacts = new SmsMmsContacts();
            for(int x=0;x<listSize;x++){
                BluetoothMapConvoListingElement ele = list.get(x);
                TYPE type = ele.getType();
                switch(type) {
                case SMS_CDMA:
                case SMS_GSM:
                case MMS: {
                    tmpCursor = null; // SMS/MMS needs special treatment
                    if(smsMmsCursor != null) {
                        populateSmsMmsConvoElement(ele, smsMmsCursor, ap, contacts);
                    }
                    if(D) fi.mMsgType = FilterInfo.TYPE_IM;
                    break;
                }
                case EMAIL:
                    tmpCursor = imEmailCursor;
                    fi.mMsgType = FilterInfo.TYPE_EMAIL;
                    break;
                case IM:
                    tmpCursor = imEmailCursor;
                    fi.mMsgType = FilterInfo.TYPE_IM;
                    break;
                default:
                    tmpCursor = null;
                    break;
                }

                if(D) Log.d(TAG, "Working on cursor of type " + fi.mMsgType);

                if(tmpCursor != null){
                    populateImEmailConvoElement(ele, tmpCursor, ap, fi);
                }else {
                    // No, it will be for SMS/MMS at the moment
                    if(D) Log.d(TAG, "tmpCursor is Null - something is wrong - or the message is" +
                            " of type SMS/MMS");
                }
            }
        } finally {
            if(imEmailCursor != null)imEmailCursor.close();
            if(smsMmsCursor != null)smsMmsCursor.close();
            if(D)Log.d(TAG, "conversation end");
        }
        return convoList;
    }


    /**
     * Refreshes the entire list of SMS/MMS conversation version counters. Use it to generate a
     * new ConvoListVersinoCounter in mSmsMmsConvoListVersion
     * @return
     */
    /* package */
    boolean refreshSmsMmsConvoVersions() {
        boolean listChangeDetected = false;
        Cursor cursor = null;
        Uri uri = Threads.CONTENT_URI.buildUpon()
                .appendQueryParameter("simple", "true").build();
        cursor = mResolver.query(uri, MMS_SMS_THREAD_PROJECTION, null,
                null, Threads.DATE + " DESC");
        try {
            if (cursor != null) {
                // store column index so we don't have to look them up anymore (optimization)
                if(D) Log.d(TAG, "Found " + cursor.getCount()
                        + " sms/mms conversations.");
                BluetoothMapConvoListingElement convoElement = null;
                cursor.moveToPosition(-1);
                synchronized (getSmsMmsConvoList()) {
                    int size = Math.max(getSmsMmsConvoList().size(), cursor.getCount());
                    HashMap<Long,BluetoothMapConvoListingElement> newList =
                            new HashMap<Long,BluetoothMapConvoListingElement>(size);
                    while (cursor.moveToNext()) {
                        // TODO: Extract to function, that can be called at listing, which returns
                        //       the versionCounter(existing or new).
                        boolean convoChanged = false;
                        Long id = cursor.getLong(MMS_SMS_THREAD_COL_ID);
                        convoElement = getSmsMmsConvoList().remove(id);
                        if(convoElement == null) {
                            // New conversation added
                            convoElement = new BluetoothMapConvoListingElement();
                            convoElement.setConvoId(BluetoothMapUtils.CONVO_ID_TYPE_SMS_MMS, id);
                            listChangeDetected = true;
                            convoElement.setVersionCounter(0);
                        }
                        // Currently we only need to compare name, last_activity and read_status, and
                        // name is not used for SMS/MMS.
                        // msg delete will be handled by update folderVersionCounter().
                        long last_activity = cursor.getLong(MMS_SMS_THREAD_COL_DATE);
                        boolean read = (cursor.getInt(MMS_SMS_THREAD_COL_READ) == 1) ?
                                true : false;

                        if(last_activity != convoElement.getLastActivity()) {
                            convoChanged = true;
                            convoElement.setLastActivity(last_activity);
                        }

                        if(read != convoElement.getReadBool()) {
                            convoChanged = true;
                            convoElement.setRead(read, false);
                        }

                        String idsStr = cursor.getString(MMS_SMS_THREAD_COL_RECIPIENT_IDS);
                        if(!idsStr.equals(convoElement.getSmsMmsContacts())) {
                            // This should not trigger a change in conversationVersionCounter only the
                            // ConvoListVersionCounter.
                            listChangeDetected = true;
                            convoElement.setSmsMmsContacts(idsStr);
                        }

                        if(convoChanged) {
                            listChangeDetected = true;
                            convoElement.incrementVersionCounter();
                        }
                        newList.put(id, convoElement);
                    }
                    // If we still have items on the old list, something was deleted
                    if(getSmsMmsConvoList().size() != 0) {
                        listChangeDetected = true;
                    }
                    setSmsMmsConvoList(newList);
                }

                if(listChangeDetected) {
                    mMasInstance.updateSmsMmsConvoListVersionCounter();
                }
            }
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }
        return listChangeDetected;
    }

    /**
     * Refreshes the entire list of SMS/MMS conversation version counters. Use it to generate a
     * new ConvoListVersinoCounter in mSmsMmsConvoListVersion
     * @return
     */
    /* package */
    boolean refreshImEmailConvoVersions() {
        boolean listChangeDetected = false;
        FilterInfo fi = new FilterInfo();

        Uri contentUri = Uri.parse(mBaseUri + BluetoothMapContract.TABLE_CONVERSATION);

        if(V) Log.v(TAG, "URI with parameters: " + contentUri.toString());
        Cursor imEmailCursor = mResolver.query(contentUri,
                CONVO_VERSION_PROJECTION,
                null, null, BluetoothMapContract.ConversationColumns.LAST_THREAD_ACTIVITY
                + " DESC, " + BluetoothMapContract.ConversationColumns.THREAD_ID
                + " ASC");
        try {
            if (imEmailCursor != null) {
                BluetoothMapConvoListingElement convoElement = null;
                // store column index so we don't have to look them up anymore (optimization)
                // Here we rely on only a single account-based message type for each MAS.
                fi.setEmailImConvoColumns(imEmailCursor);
                boolean isValid = imEmailCursor.moveToNext();
                if(V) Log.d(TAG, "Found " + imEmailCursor.getCount()
                        + " EMAIL/IM conversations. isValid = " + isValid);
                synchronized (getImEmailConvoList()) {
                    int size = Math.max(getImEmailConvoList().size(), imEmailCursor.getCount());
                    boolean convoChanged = false;
                    HashMap<Long,BluetoothMapConvoListingElement> newList =
                            new HashMap<Long,BluetoothMapConvoListingElement>(size);
                    while (isValid) {
                        long id = imEmailCursor.getLong(fi.mConvoColConvoId);
                        long nextThreadId;
                        convoElement = getImEmailConvoList().remove(id);
                        if(convoElement == null) {
                            // New conversation added
                            convoElement = new BluetoothMapConvoListingElement();
                            convoElement.setConvoId(BluetoothMapUtils.CONVO_ID_TYPE_EMAIL_IM, id);
                            listChangeDetected = true;
                            convoElement.setVersionCounter(0);
                        }
                        String name = imEmailCursor.getString(fi.mConvoColName);
                        String summary = imEmailCursor.getString(fi.mConvoColSummary);
                        long last_activity = imEmailCursor.getLong(fi.mConvoColLastActivity);
                        boolean read = (imEmailCursor.getInt(fi.mConvoColRead) == 1) ?
                                true : false;

                        if(last_activity != convoElement.getLastActivity()) {
                            convoChanged = true;
                            convoElement.setLastActivity(last_activity);
                        }

                        if(read != convoElement.getReadBool()) {
                            convoChanged = true;
                            convoElement.setRead(read, false);
                        }

                        if(name != null && !name.equals(convoElement.getName())) {
                            convoChanged = true;
                            convoElement.setName(name);
                        }

                        if(summary != null && !summary.equals(convoElement.getFullSummary())) {
                            convoChanged = true;
                            convoElement.setSummary(summary);
                        }
                        /* If the query returned one row for each contact, skip all the dublicates */
                        do {
                            nextThreadId = imEmailCursor.getLong(fi.mConvoColConvoId);
                            if(V) Log.i(TAG, "  threadId = " + id + " newThreadId = " +
                                    nextThreadId);
                        } while ((nextThreadId == id) &&
                                (isValid = imEmailCursor.moveToNext() == true));

                        if(convoChanged) {
                            listChangeDetected = true;
                            convoElement.incrementVersionCounter();
                        }
                        newList.put(id, convoElement);
                    }
                    // If we still have items on the old list, something was deleted
                    if(getImEmailConvoList().size() != 0) {
                        listChangeDetected = true;
                    }
                    setImEmailConvoList(newList);
                }
            }
        } finally {
            if(imEmailCursor != null) {
                imEmailCursor.close();
            }
        }

        if(listChangeDetected) {
            mMasInstance.updateImEmailConvoListVersionCounter();
        }
        return listChangeDetected;
    }

    /**
     * Update the convoVersionCounter within the element passed as parameter.
     * This function has the side effect to update the ConvoListVersionCounter if needed.
     * This function ignores changes to contacts as this shall not change the convoVersionCounter,
     * only the convoListVersion counter, which will be updated upon request.
     * @param ele Element to update shall not be null.
     */
    private void updateSmsMmsConvoVersion(Cursor cursor, BluetoothMapConvoListingElement ele) {
        long id = ele.getCpConvoId();
        BluetoothMapConvoListingElement convoElement = getSmsMmsConvoList().get(id);
        boolean listChangeDetected = false;
        boolean convoChanged = false;
        if(convoElement == null) {
            // New conversation added
            convoElement = new BluetoothMapConvoListingElement();
            getSmsMmsConvoList().put(id, convoElement);
            convoElement.setConvoId(BluetoothMapUtils.CONVO_ID_TYPE_SMS_MMS, id);
            listChangeDetected = true;
            convoElement.setVersionCounter(0);
        }
        long last_activity = cursor.getLong(MMS_SMS_THREAD_COL_DATE);
        boolean read = (cursor.getInt(MMS_SMS_THREAD_COL_READ) == 1) ?
                true : false;

        if(last_activity != convoElement.getLastActivity()) {
            convoChanged = true;
            convoElement.setLastActivity(last_activity);
        }

        if(read != convoElement.getReadBool()) {
            convoChanged = true;
            convoElement.setRead(read, false);
        }

        if(convoChanged) {
            listChangeDetected = true;
            convoElement.incrementVersionCounter();
        }
        if(listChangeDetected) {
            mMasInstance.updateSmsMmsConvoListVersionCounter();
        }
        ele.setVersionCounter(convoElement.getVersionCounter());
    }

    /**
     * Update the convoVersionCounter within the element passed as parameter.
     * This function has the side effect to update the ConvoListVersionCounter if needed.
     * This function ignores changes to contacts as this shall not change the convoVersionCounter,
     * only the convoListVersion counter, which will be updated upon request.
     * @param ele Element to update shall not be null.
     */
    private void updateImEmailConvoVersion(Cursor cursor, FilterInfo fi,
            BluetoothMapConvoListingElement ele) {
        long id = ele.getCpConvoId();
        BluetoothMapConvoListingElement convoElement = getImEmailConvoList().get(id);
        boolean listChangeDetected = false;
        boolean convoChanged = false;
        if(convoElement == null) {
            // New conversation added
            if(V) Log.d(TAG, "Added new conversation with ID = " + id);
            convoElement = new BluetoothMapConvoListingElement();
            convoElement.setConvoId(BluetoothMapUtils.CONVO_ID_TYPE_EMAIL_IM, id);
            getImEmailConvoList().put(id, convoElement);
            listChangeDetected = true;
            convoElement.setVersionCounter(0);
        }
        String name = cursor.getString(fi.mConvoColName);
        long last_activity = cursor.getLong(fi.mConvoColLastActivity);
        boolean read = (cursor.getInt(fi.mConvoColRead) == 1) ?
                true : false;

        if(last_activity != convoElement.getLastActivity()) {
            convoChanged = true;
            convoElement.setLastActivity(last_activity);
        }

        if(read != convoElement.getReadBool()) {
            convoChanged = true;
            convoElement.setRead(read, false);
        }

        if(name != null && !name.equals(convoElement.getName())) {
            convoChanged = true;
            convoElement.setName(name);
        }

        if(convoChanged) {
            listChangeDetected = true;
            if(V) Log.d(TAG, "conversation with ID = " + id + " changed");
            convoElement.incrementVersionCounter();
        }
        if(listChangeDetected) {
            mMasInstance.updateImEmailConvoListVersionCounter();
        }
        ele.setVersionCounter(convoElement.getVersionCounter());
    }

    /**
     * @param ele
     * @param smsMmsCursor
     * @param ap
     * @param contacts
     */
    private void populateSmsMmsConvoElement(BluetoothMapConvoListingElement ele,
            Cursor smsMmsCursor, BluetoothMapAppParams ap,
            SmsMmsContacts contacts) {
        smsMmsCursor.moveToPosition(ele.getCursorIndex());
        // TODO: If we ever get beyond 31 bit, change to long
        int parameterMask = (int) ap.getConvoParameterMask(); // We always set a default value

        // TODO: How to determine whether the convo-IDs can be used across message
        //       types?
        ele.setConvoId(BluetoothMapUtils.CONVO_ID_TYPE_SMS_MMS,
                smsMmsCursor.getLong(MMS_SMS_THREAD_COL_ID));

        boolean read = (smsMmsCursor.getInt(MMS_SMS_THREAD_COL_READ) == 1) ?
                true : false;
        if((parameterMask & CONVO_PARAM_MASK_CONVO_READ_STATUS) != 0) {
            ele.setRead(read, true);
        } else {
            ele.setRead(read, false);
        }

        if((parameterMask & CONVO_PARAM_MASK_CONVO_LAST_ACTIVITY) != 0) {
            long timeStamp = smsMmsCursor.getLong(MMS_SMS_THREAD_COL_DATE);
            ele.setLastActivity(timeStamp);
        } else {
            // We need to delete the time stamp, if it was added for multi msg-type
            ele.setLastActivity(-1);
        }

        if((parameterMask & CONVO_PARAM_MASK_CONVO_VERSION_COUNTER) != 0) {
            updateSmsMmsConvoVersion(smsMmsCursor, ele);
        }

        if((parameterMask & CONVO_PARAM_MASK_CONVO_NAME) != 0) {
            ele.setName(""); // We never have a thread name for SMS/MMS
        }

        if((parameterMask & CONVO_PARAM_MASK_CONVO_SUMMARY) != 0) {
            String summary = smsMmsCursor.getString(MMS_SMS_THREAD_COL_SNIPPET);
            String cs = smsMmsCursor.getString(MMS_SMS_THREAD_COL_SNIPPET_CS);
            if(summary != null && cs != null && !cs.equals("UTF-8")) {
                try {
                    // TODO: Not sure this is how to convert to UTF-8
                    summary = new String(summary.getBytes(cs),"UTF-8");
                } catch (UnsupportedEncodingException e){/*Cannot happen*/}
            }
            ele.setSummary(summary);
        }

        if((parameterMask & CONVO_PARAM_MASK_PARTTICIPANTS) != 0) {
            if(ap.getFilterRecipient() == null) {
                // Add contacts only if not already added
                String idsStr =
                        smsMmsCursor.getString(MMS_SMS_THREAD_COL_RECIPIENT_IDS);
                addSmsMmsContacts(ele, contacts, idsStr, null, ap);
            }
        }
    }

    /**
     * @param ele
     * @param tmpCursor
     * @param fi
     */
    private void populateImEmailConvoElement( BluetoothMapConvoListingElement ele,
            Cursor tmpCursor, BluetoothMapAppParams ap, FilterInfo fi) {
        tmpCursor.moveToPosition(ele.getCursorIndex());
        // TODO: If we ever get beyond 31 bit, change to long
        int parameterMask = (int) ap.getConvoParameterMask(); // We always set a default value
        long threadId = tmpCursor.getLong(fi.mConvoColConvoId);

        // Mandatory field
        ele.setConvoId(BluetoothMapUtils.CONVO_ID_TYPE_EMAIL_IM, threadId);

        if((parameterMask & CONVO_PARAM_MASK_CONVO_NAME) != 0) {
            ele.setName(tmpCursor.getString(fi.mConvoColName));
        }

        boolean reportRead = false;
        if((parameterMask & CONVO_PARAM_MASK_CONVO_READ_STATUS) != 0) {
            reportRead = true;
        }
        ele.setRead(((1==tmpCursor.getInt(fi.mConvoColRead))?true:false), reportRead);

        long timestamp = tmpCursor.getLong(fi.mConvoColLastActivity);
        if((parameterMask & CONVO_PARAM_MASK_CONVO_LAST_ACTIVITY) != 0) {
            ele.setLastActivity(timestamp);
        } else {
            // We need to delete the time stamp, if it was added for multi msg-type
            ele.setLastActivity(-1);
        }


        if((parameterMask & CONVO_PARAM_MASK_CONVO_VERSION_COUNTER) != 0) {
            updateImEmailConvoVersion(tmpCursor, fi, ele);
        }
        if((parameterMask & CONVO_PARAM_MASK_CONVO_SUMMARY) != 0) {
            ele.setSummary(tmpCursor.getString(fi.mConvoColSummary));
        }
        // TODO: For optimization, we could avoid joining the contact and convo tables
        //       if we have no filter nor this bit is set.
        if((parameterMask & CONVO_PARAM_MASK_PARTTICIPANTS) != 0) {
            do {
                BluetoothMapConvoContactElement c = new BluetoothMapConvoContactElement();
                if((parameterMask & CONVO_PARAM_MASK_PART_X_BT_UID) != 0) {
                    c.setBtUid(new SignedLongLong(tmpCursor.getLong(fi.mContactColBtUid),0));
                }
                if((parameterMask & CONVO_PARAM_MASK_PART_CHAT_STATE) != 0) {
                    c.setChatState(tmpCursor.getInt(fi.mContactColChatState));
                }
                if((parameterMask & CONVO_PARAM_MASK_PART_PRESENCE) != 0) {
                    c.setPresenceAvailability(tmpCursor.getInt(fi.mContactColPresenceState));
                }
                if((parameterMask & CONVO_PARAM_MASK_PART_PRESENCE_TEXT) != 0) {
                    c.setPresenceStatus(tmpCursor.getString(fi.mContactColPresenceText));
                }
                if((parameterMask & CONVO_PARAM_MASK_PART_PRIORITY) != 0) {
                    c.setPriority(tmpCursor.getInt(fi.mContactColPriority));
                }
                if((parameterMask & CONVO_PARAM_MASK_PART_DISP_NAME) != 0) {
                    c.setDisplayName(tmpCursor.getString(fi.mContactColNickname));
                }
                if((parameterMask & CONVO_PARAM_MASK_PART_UCI) != 0) {
                    c.setContactId(tmpCursor.getString(fi.mContactColContactUci));
                }
                if((parameterMask & CONVO_PARAM_MASK_PART_LAST_ACTIVITY) != 0) {
                    c.setLastActivity(tmpCursor.getLong(fi.mContactColLastActive));
                }
                if((parameterMask & CONVO_PARAM_MASK_PART_NAME) != 0) {
                    c.setName(tmpCursor.getString(fi.mContactColName));
                }
                ele.addContact(c);
            } while (tmpCursor.moveToNext() == true
                    && tmpCursor.getLong(fi.mConvoColConvoId) == threadId);
        }
    }

    /**
     * Extract the ConvoList parameters from appParams and build the matching URI with
     * query parameters.
     * @param ap the appParams from the request
     * @param contentUri the URI to append parameters to
     * @return the new URI with the appended parameters (if any)
     */
    private Uri appendConvoListQueryParameters(BluetoothMapAppParams ap,
            Uri contentUri) {
        Builder newUri = contentUri.buildUpon();
        String str = ap.getFilterRecipient();
        if(str != null) {
            str = str.trim();
            str = str.replace("*", "%");
            newUri.appendQueryParameter(BluetoothMapContract.FILTER_ORIGINATOR_SUBSTRING, str);
        }
        long time = ap.getFilterLastActivityBegin();
        if(time > 0) {
            newUri.appendQueryParameter(BluetoothMapContract.FILTER_PERIOD_BEGIN,
                    Long.toString(time));
        }
        time = ap.getFilterLastActivityEnd();
        if(time > 0) {
            newUri.appendQueryParameter(BluetoothMapContract.FILTER_PERIOD_END,
                    Long.toString(time));
        }
        int readStatus = ap.getFilterReadStatus();
        if(readStatus > 0) {
            if(readStatus == 1) {
                // Conversations with Unread messages only
                newUri.appendQueryParameter(BluetoothMapContract.FILTER_READ_STATUS,
                        "false");
            }else if(readStatus == 2) {
                // Conversations with all read messages only
                newUri.appendQueryParameter(BluetoothMapContract.FILTER_READ_STATUS,
                        "true");
            }
            // if both are set it will be the same as requesting an empty list, but
            // as it makes no sense with such a structure in a bit mask, we treat
            // requesting both the same as no filtering.
        }
        long convoId = -1;
        if(ap.getFilterConvoId() != null) {
            convoId = ap.getFilterConvoId().getLeastSignificantBits();
        }
        if(convoId > 0) {
            newUri.appendQueryParameter(BluetoothMapContract.FILTER_THREAD_ID,
                    Long.toString(convoId));
        }
        return newUri.build();
    }

    /**
     * Procedure if we have a filter:
     *  - loop through all ids to examine if there is a match (this will build the cache)
     *  - If there is a match loop again to add all contacts.
     *
     * Procedure if we don't have a filter
     *  - Add all contacts
     *
     * @param convoElement
     * @param contacts
     * @param idsStr
     * @param recipientFilter
     * @return
     */
    private boolean addSmsMmsContacts( BluetoothMapConvoListingElement convoElement,
            SmsMmsContacts contacts, String idsStr, String recipientFilter,
            BluetoothMapAppParams ap) {
        BluetoothMapConvoContactElement contactElement;
        int parameterMask = (int) ap.getConvoParameterMask(); // We always set a default value
        boolean foundContact = false;
        String[] ids = idsStr.split(" ");
        long[] longIds = new long[ids.length];
        if(recipientFilter != null) {
            recipientFilter = recipientFilter.trim();
        }

        for (int i = 0; i < ids.length; i++) {
            long longId;
            try {
                longId = Long.parseLong(ids[i]);
                longIds[i] = longId;
                if(recipientFilter == null) {
                    // If there is not filter, all we need to do is to parse the ids
                    foundContact = true;
                    continue;
                }
                String addr = contacts.getPhoneNumber(mResolver, longId);
                if(addr == null) {
                    // This can only happen if all messages from a contact is deleted while
                    // performing the query.
                    continue;
                }
                MapContact contact =
                        contacts.getContactNameFromPhone(addr, mResolver, recipientFilter);
                if(D) {
                    Log.d(TAG, "  id " + longId + ": " + addr);
                    if(contact != null) {
                        Log.d(TAG,"  contact name: " + contact.getName() + "  X-BT-UID: "
                                + contact.getXBtUid());
                    }
                }
                if(contact == null) {
                    continue;
                }
                foundContact = true;
            } catch (NumberFormatException ex) {
                // skip this id
                continue;
            }
        }

        if(foundContact == true) {
            foundContact = false;
            for (long id : longIds) {
                String addr = contacts.getPhoneNumber(mResolver, id);
                if(addr == null) {
                    // This can only happen if all messages from a contact is deleted while
                    // performing the query.
                    continue;
                }
                foundContact = true;
                MapContact contact = contacts.getContactNameFromPhone(addr, mResolver);

                if(contact == null) {
                    // We do not have a contact, we need to manually add one
                    contactElement = new BluetoothMapConvoContactElement();
                    if((parameterMask & CONVO_PARAM_MASK_PART_NAME) != 0) {
                        contactElement.setName(addr); // Use the phone number as name
                    }
                    if((parameterMask & CONVO_PARAM_MASK_PART_UCI) != 0) {
                        contactElement.setContactId(addr);
                    }
                } else {
                    contactElement = BluetoothMapConvoContactElement
                            .createFromMapContact(contact, addr);
                    // Remove the parameters not to be reported
                    if((parameterMask & CONVO_PARAM_MASK_PART_UCI) == 0) {
                        contactElement.setContactId(null);
                    }
                    if((parameterMask & CONVO_PARAM_MASK_PART_X_BT_UID) == 0) {
                        contactElement.setBtUid(null);
                    }
                    if((parameterMask & CONVO_PARAM_MASK_PART_DISP_NAME) == 0) {
                        contactElement.setDisplayName(null);
                    }
                }
                convoElement.addContact(contactElement);
            }
        }
        return foundContact;
    }

    /**
     * Get the folder name of an SMS message or MMS message.
     * @param c the cursor pointing at the message
     * @return the folder name.
     */
    private String getFolderName(int type, int threadId) {

        if(threadId == -1)
            return BluetoothMapContract.FOLDER_NAME_DELETED;

        switch(type) {
        case 1:
            return BluetoothMapContract.FOLDER_NAME_INBOX;
        case 2:
            return BluetoothMapContract.FOLDER_NAME_SENT;
        case 3:
            return BluetoothMapContract.FOLDER_NAME_DRAFT;
        case 4: // Just name outbox, failed and queued "outbox"
        case 5:
        case 6:
            return BluetoothMapContract.FOLDER_NAME_OUTBOX;
        }
        return "";
    }

    public byte[] getMessage(String handle, BluetoothMapAppParams appParams,
            BluetoothMapFolderElement folderElement, String version)
            throws UnsupportedEncodingException{
        TYPE type = BluetoothMapUtils.getMsgTypeFromHandle(handle);
        mMessageVersion = version;
        long id = BluetoothMapUtils.getCpHandle(handle);
        if(appParams.getFractionRequest() == BluetoothMapAppParams.FRACTION_REQUEST_NEXT) {
            throw new IllegalArgumentException("FRACTION_REQUEST_NEXT does not make sence as" +
                                               " we always return the full message.");
        }
        switch(type) {
        case SMS_GSM:
        case SMS_CDMA:
            return getSmsMessage(id, appParams.getCharset());
        case MMS:
            return getMmsMessage(id, appParams);
        case EMAIL:
            return getEmailMessage(id, appParams, folderElement);
        case IM:
            return getIMMessage(id, appParams, folderElement);
        }
        throw new IllegalArgumentException("Invalid message handle.");
    }

    private String setVCardFromPhoneNumber(BluetoothMapbMessage message,
            String phone, boolean incoming) {
        String contactId = null, contactName = null;
        String[] phoneNumbers = new String[1];
        //Handle possible exception for empty phone address
        if (TextUtils.isEmpty(phone)) {
            return contactName;
        }
        //
        // Use only actual phone number, because the MCE cannot know which
        // number the message is from.
        //
        phoneNumbers[0] = phone;
        String[] emailAddresses = null;
        Cursor p;

        Uri uri = Uri
                .withAppendedPath(PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI,
                Uri.encode(phone));

        String[] projection = {Contacts._ID, Contacts.DISPLAY_NAME};
        String selection = Contacts.IN_VISIBLE_GROUP + "=1";
        String orderBy = Contacts._ID + " ASC";

        // Get the contact _ID and name
        p = mResolver.query(uri, projection, selection, null, orderBy);
        try {
            if (p != null && p.moveToFirst()) {
                contactId = p.getString(p.getColumnIndex(Contacts._ID));
                contactName = p.getString(p.getColumnIndex(Contacts.DISPLAY_NAME));
            }
        } finally {
            close(p);
        }
        // Bail out if we are unable to find a contact, based on the phone number
        if (contactId != null) {
            Cursor q = null;
            // Fetch the contact e-mail addresses
            try {
                q = mResolver.query(ContactsContract.CommonDataKinds.Email.CONTENT_URI, null,
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                        new String[]{contactId},
                        null);
                if (q != null && q.moveToFirst()) {
                    int i = 0;
                    emailAddresses = new String[q.getCount()];
                    do {
                        String emailAddress = q.getString(q.getColumnIndex(
                                ContactsContract.CommonDataKinds.Email.ADDRESS));
                        emailAddresses[i++] = emailAddress;
                    } while (q != null && q.moveToNext());
                }
            } finally {
                close(q);
            }
        }

        if (incoming == true) {
            if(V) Log.d(TAG, "Adding originator for phone:" + phone);
            // Use version 3.0 as we only have a formatted name
            message.addOriginator(contactName, contactName, phoneNumbers, emailAddresses,null,null);
        } else {
            if(V) Log.d(TAG, "Adding recipient for phone:" + phone);
            // Use version 3.0 as we only have a formatted name
            message.addRecipient(contactName, contactName, phoneNumbers, emailAddresses,null,null);
        }
        return contactName;
    }

    public static final int MAP_MESSAGE_CHARSET_NATIVE = 0;
    public static final int MAP_MESSAGE_CHARSET_UTF8 = 1;

    public byte[] getSmsMessage(long id, int charset) throws UnsupportedEncodingException{
        int type, threadId;
        long time = -1;
        String msgBody;
        BluetoothMapbMessageSms message = new BluetoothMapbMessageSms();
        TelephonyManager tm = (TelephonyManager)mContext.getSystemService(Context.TELEPHONY_SERVICE);

        Cursor c = mResolver.query(Sms.CONTENT_URI, SMS_PROJECTION, "_ID = " + id, null, null);
        if (c == null || !c.moveToFirst()) {
            throw new IllegalArgumentException("SMS handle not found");
        }

        try{
            if(c != null && c.moveToFirst())
            {
                if(V) Log.v(TAG,"c.count: " + c.getCount());

                if (tm.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) {
                    message.setType(TYPE.SMS_GSM);
                } else if (tm.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
                    message.setType(TYPE.SMS_CDMA);
                }
                message.setVersionString(mMessageVersion);
                String read = c.getString(c.getColumnIndex(Sms.READ));
                if (read.equalsIgnoreCase("1"))
                    message.setStatus(true);
                else
                    message.setStatus(false);

                type = c.getInt(c.getColumnIndex(Sms.TYPE));
                threadId = c.getInt(c.getColumnIndex(Sms.THREAD_ID));
                message.setFolder(getFolderName(type, threadId));

                msgBody = c.getString(c.getColumnIndex(Sms.BODY));

                String phone = c.getString(c.getColumnIndex(Sms.ADDRESS));
                if ((phone == null) && type == Sms.MESSAGE_TYPE_DRAFT) {
                    //Fetch address for Drafts folder from "canonical_address" table
                    phone  = getCanonicalAddressSms(mResolver, threadId);
                }
                time = c.getLong(c.getColumnIndex(Sms.DATE));
                if(type == 1) // Inbox message needs to set the vCard as originator
                    setVCardFromPhoneNumber(message, phone, true);
                else          // Other messages sets the vCard as the recipient
                    setVCardFromPhoneNumber(message, phone, false);

                if(charset == MAP_MESSAGE_CHARSET_NATIVE) {
                    if(type == 1) //Inbox
                        message.setSmsBodyPdus(BluetoothMapSmsPdu.getDeliverPdus(msgBody,
                                    phone, time));
                    else
                        message.setSmsBodyPdus(BluetoothMapSmsPdu.getSubmitPdus(msgBody, phone));
                } else /*if (charset == MAP_MESSAGE_CHARSET_UTF8)*/ {
                    message.setSmsBody(msgBody);
                }
                return message.encode();
            }
        } finally {
            if (c != null) c.close();
        }

        return message.encode();
    }

    private void extractMmsAddresses(long id, BluetoothMapbMessageMime message) {
        final String[] projection = null;
        String selection = new String(Mms.Addr.MSG_ID + "=" + id);
        String uriStr = new String(Mms.CONTENT_URI + "/" + id + "/addr");
        Uri uriAddress = Uri.parse(uriStr);
        String contactName = null;

        Cursor c = mResolver.query( uriAddress, projection, selection, null, null);
        try {
            if (c.moveToFirst()) {
                do {
                    String address = c.getString(c.getColumnIndex(Mms.Addr.ADDRESS));
                    if(address.equals(INSERT_ADDRES_TOKEN))
                        continue;
                    Integer type = c.getInt(c.getColumnIndex(Mms.Addr.TYPE));
                    switch(type) {
                    case MMS_FROM:
                        contactName = setVCardFromPhoneNumber(message, address, true);
                        message.addFrom(contactName, address);
                        break;
                    case MMS_TO:
                        contactName = setVCardFromPhoneNumber(message, address, false);
                        message.addTo(contactName, address);
                        break;
                    case MMS_CC:
                        contactName = setVCardFromPhoneNumber(message, address, false);
                        message.addCc(contactName, address);
                        break;
                    case MMS_BCC:
                        contactName = setVCardFromPhoneNumber(message, address, false);
                        message.addBcc(contactName, address);
                        break;
                    default:
                        break;
                    }
                } while(c.moveToNext());
            }
        } finally {
            if (c != null) c.close();
        }
    }


    /**
     * Read out a mime data part and return the data in a byte array.
     * @param contentPartUri TODO
     * @param partid the content provider id of the Mime Part.
     * @return
     */
    private byte[] readRawDataPart(Uri contentPartUri, long partid) {
        String uriStr = new String(contentPartUri+"/"+ partid);
        Uri uriAddress = Uri.parse(uriStr);
        InputStream is = null;
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        int bufferSize = 8192;
        byte[] buffer = new byte[bufferSize];
        byte[] retVal = null;

        try {
            is = mResolver.openInputStream(uriAddress);
            int len = 0;
            while ((len = is.read(buffer)) != -1) {
              os.write(buffer, 0, len); // We need to specify the len, as it can be != bufferSize
            }
            retVal = os.toByteArray();
        } catch (IOException e) {
            // do nothing for now
            Log.w(TAG,"Error reading part data",e);
        } finally {
            close(os);
            close(is);
        }
        return retVal;
    }

    /**
     * Read out the mms parts and update the bMessage object provided i {@linkplain message}
     * @param id the content provider ID of the message
     * @param message the bMessage object to add the information to
     */
    private void extractMmsParts(long id, BluetoothMapbMessageMime message)
    {
        /* Handling of filtering out non-text parts for exclude
         * attachments is handled within the bMessage object. */
        final String[] projection = null;
        String selection = new String(Mms.Part.MSG_ID + "=" + id);
        String uriStr = new String(Mms.CONTENT_URI + "/"+ id + "/part");
        Uri uriAddress = Uri.parse(uriStr);
        BluetoothMapbMessageMime.MimePart part;
        Cursor c = mResolver.query(uriAddress, projection, selection, null, null);
        try {
            if (c.moveToFirst()) {
                do {
                    Long partId = c.getLong(c.getColumnIndex(BaseColumns._ID));
                    String contentType = c.getString(c.getColumnIndex(Mms.Part.CONTENT_TYPE));
                    String name = c.getString(c.getColumnIndex(Mms.Part.NAME));
                    String charset = c.getString(c.getColumnIndex(Mms.Part.CHARSET));
                    String filename = c.getString(c.getColumnIndex(Mms.Part.FILENAME));
                    String text = c.getString(c.getColumnIndex(Mms.Part.TEXT));
                    Integer fd = c.getInt(c.getColumnIndex(Mms.Part._DATA));
                    String cid = c.getString(c.getColumnIndex(Mms.Part.CONTENT_ID));
                    String cl = c.getString(c.getColumnIndex(Mms.Part.CONTENT_LOCATION));
                    String cdisp = c.getString(c.getColumnIndex(Mms.Part.CONTENT_DISPOSITION));

                    if(V) Log.d(TAG, "     _id : " + partId +
                            "\n     ct : " + contentType +
                            "\n     partname : " + name +
                            "\n     charset : " + charset +
                            "\n     filename : " + filename +
                            "\n     text : " + text +
                            "\n     fd : " + fd +
                            "\n     cid : " + cid +
                            "\n     cl : " + cl +
                            "\n     cdisp : " + cdisp);

                    part = message.addMimePart();
                    part.mContentType = contentType;
                    part.mPartName = name;
                    part.mContentId = cid;
                    part.mContentLocation = cl;
                    part.mContentDisposition = cdisp;

                    try {
                        if(text != null) {
                            part.mData = text.getBytes("UTF-8");
                            part.mCharsetName = "utf-8";
                        } else {
                            part.mData =
                                    readRawDataPart(Uri.parse(Mms.CONTENT_URI+"/part"), partId);
                            if(charset != null) {
                                part.mCharsetName =
                                        CharacterSets.getMimeName(Integer.parseInt(charset));
                            }
                        }
                    } catch (NumberFormatException e) {
                        Log.d(TAG,"extractMmsParts",e);
                        part.mData = null;
                        part.mCharsetName = null;
                    } catch (UnsupportedEncodingException e) {
                        Log.d(TAG,"extractMmsParts",e);
                        part.mData = null;
                        part.mCharsetName = null;
                    } finally {
                    }
                    part.mFileName = filename;
                } while(c.moveToNext());
                message.updateCharset();
            }

        } finally {
            if(c != null) c.close();
        }
    }
    /**
     * Read out the mms parts and update the bMessage object provided i {@linkplain message}
     * @param id the content provider ID of the message
     * @param message the bMessage object to add the information to
     */
    private void extractIMParts(long id, BluetoothMapbMessageMime message)
    {
        /* Handling of filtering out non-text parts for exclude
         * attachments is handled within the bMessage object. */
        final String[] projection = null;
        String selection = new String(BluetoothMapContract.MessageColumns._ID + "=" + id);
        String uriStr = new String(mBaseUri
                                         + BluetoothMapContract.TABLE_MESSAGE + "/"+ id + "/part");
        Uri uriAddress = Uri.parse(uriStr);
        BluetoothMapbMessageMime.MimePart part;
        Cursor c = mResolver.query(uriAddress, projection, selection, null, null);
        try{
            if (c.moveToFirst()) {
                do {
                    Long partId = c.getLong(
                                  c.getColumnIndex(BluetoothMapContract.MessagePartColumns._ID));
                    String charset = c.getString(
                           c.getColumnIndex(BluetoothMapContract.MessagePartColumns.CHARSET));
                    String filename = c.getString(
                           c.getColumnIndex(BluetoothMapContract.MessagePartColumns.FILENAME));
                    String text = c.getString(
                           c.getColumnIndex(BluetoothMapContract.MessagePartColumns.TEXT));
                    String body = c.getString(
                           c.getColumnIndex(BluetoothMapContract.MessagePartColumns.RAW_DATA));
                    String cid = c.getString(
                           c.getColumnIndex(BluetoothMapContract.MessagePartColumns.CONTENT_ID));

                    if(V) Log.d(TAG, "     _id : " + partId +
                            "\n     charset : " + charset +
                            "\n     filename : " + filename +
                            "\n     text : " + text +
                            "\n     cid : " + cid);

                    part = message.addMimePart();
                    part.mContentId = cid;
                    try {
                        if(text.equalsIgnoreCase("yes")) {
                            part.mData = body.getBytes("UTF-8");
                            part.mCharsetName = "utf-8";
                        } else {
                            part.mData = readRawDataPart(Uri.parse(mBaseUri
                                             + BluetoothMapContract.TABLE_MESSAGE_PART) , partId);
                            if(charset != null)
                                part.mCharsetName = CharacterSets.getMimeName(
                                                                        Integer.parseInt(charset));
                        }
                    } catch (NumberFormatException e) {
                        Log.d(TAG,"extractIMParts",e);
                        part.mData = null;
                        part.mCharsetName = null;
                    } catch (UnsupportedEncodingException e) {
                        Log.d(TAG,"extractIMParts",e);
                        part.mData = null;
                        part.mCharsetName = null;
                    } finally {
                    }
                    part.mFileName = filename;
                } while(c.moveToNext());
            }
        } finally {
            if(c != null) c.close();
        }

        message.updateCharset();
    }

    /**
     *
     * @param id the content provider id for the message to fetch.
     * @param appParams The application parameter object received from the client.
     * @return a byte[] containing the utf-8 encoded bMessage to send to the client.
     * @throws UnsupportedEncodingException if UTF-8 is not supported,
     * which is guaranteed to be supported on an android device
     */
    public byte[] getMmsMessage(long id,BluetoothMapAppParams appParams)
                                                        throws UnsupportedEncodingException {
        int msgBox, threadId;
        if (appParams.getCharset() == MAP_MESSAGE_CHARSET_NATIVE)
            throw new IllegalArgumentException("MMS charset native not allowed for MMS"
                                                                            +" - must be utf-8");

        BluetoothMapbMessageMime message = new BluetoothMapbMessageMime();
        Cursor c = mResolver.query(Mms.CONTENT_URI, MMS_PROJECTION, "_ID = " + id, null, null);
        try {
            if(c != null && c.moveToFirst())
            {
                message.setType(TYPE.MMS);
                message.setVersionString(mMessageVersion);

                // The MMS info:
                String read = c.getString(c.getColumnIndex(Mms.READ));
                if (read.equalsIgnoreCase("1"))
                    message.setStatus(true);
                else
                    message.setStatus(false);

                msgBox = c.getInt(c.getColumnIndex(Mms.MESSAGE_BOX));
                threadId = c.getInt(c.getColumnIndex(Mms.THREAD_ID));
                message.setFolder(getFolderName(msgBox, threadId));
                message.setSubject(c.getString(c.getColumnIndex(Mms.SUBJECT)));
                message.setMessageId(c.getString(c.getColumnIndex(Mms.MESSAGE_ID)));
                message.setContentType(c.getString(c.getColumnIndex(Mms.CONTENT_TYPE)));
                message.setDate(c.getLong(c.getColumnIndex(Mms.DATE)) * 1000L);
                message.setTextOnly(c.getInt(c.getColumnIndex(Mms.TEXT_ONLY)) == 0 ? false : true);
                message.setIncludeAttachments(appParams.getAttachment() == 0 ? false : true);
                // c.getLong(c.getColumnIndex(Mms.DATE_SENT)); - this is never used
                // c.getInt(c.getColumnIndex(Mms.STATUS)); - don't know what this is

                // The parts
                extractMmsParts(id, message);

                // The addresses
                extractMmsAddresses(id, message);


                return message.encode();
            }
        } finally {
            if (c != null) c.close();
        }

        return message.encode();
    }

    /**
    *
    * @param id the content provider id for the message to fetch.
    * @param appParams The application parameter object received from the client.
    * @return a byte[] containing the utf-8 encoded bMessage to send to the client.
    * @throws UnsupportedEncodingException if UTF-8 is not supported,
    * which is guaranteed to be supported on an android device
    */
   public byte[] getEmailMessage(long id, BluetoothMapAppParams appParams,
           BluetoothMapFolderElement currentFolder) throws UnsupportedEncodingException {
       // Log print out of application parameters set
       if(D && appParams != null) {
           Log.d(TAG,"TYPE_MESSAGE (GET): Attachment = " + appParams.getAttachment() +
                   ", Charset = " + appParams.getCharset() +
                   ", FractionRequest = " + appParams.getFractionRequest());
       }

       // Throw exception if requester NATIVE charset for Email
       // Exception is caught by MapObexServer sendGetMessageResp
       if (appParams.getCharset() == MAP_MESSAGE_CHARSET_NATIVE)
           throw new IllegalArgumentException("EMAIL charset not UTF-8");

       BluetoothMapbMessageEmail message = new BluetoothMapbMessageEmail();
       Uri contentUri = Uri.parse(mBaseUri + BluetoothMapContract.TABLE_MESSAGE);
       Cursor c = mResolver.query(contentUri, BluetoothMapContract.BT_MESSAGE_PROJECTION, "_ID = "
               + id, null, null);
       try {
           if(c != null && c.moveToFirst())
           {
               BluetoothMapFolderElement folderElement;
               FileInputStream is = null;
               ParcelFileDescriptor fd = null;
               try {
                   // Handle fraction requests
                   int fractionRequest = appParams.getFractionRequest();
                   if (fractionRequest != BluetoothMapAppParams.INVALID_VALUE_PARAMETER) {
                       // Fraction requested
                       if(V) {
                           String fractionStr = (fractionRequest == 0) ? "FIRST" : "NEXT";
                           Log.v(TAG, "getEmailMessage - FractionRequest " + fractionStr
                                   +  " - send compete message" );
                       }
                       // Check if message is complete and if not - request message from server
                       if (c.getString(c.getColumnIndex(
                               BluetoothMapContract.MessageColumns.RECEPTION_STATE)).equalsIgnoreCase(
                                       BluetoothMapContract.RECEPTION_STATE_COMPLETE) == false)  {
                           // TODO: request message from server
                           Log.w(TAG, "getEmailMessage - receptionState not COMPLETE -  Not Implemented!" );
                       }
                   }
                   // Set read status:
                   String read = c.getString(
                                        c.getColumnIndex(BluetoothMapContract.MessageColumns.FLAG_READ));
                   if (read != null && read.equalsIgnoreCase("1"))
                       message.setStatus(true);
                   else
                       message.setStatus(false);

                   // Set message type:
                   message.setType(TYPE.EMAIL);
                   message.setVersionString(mMessageVersion);
                   // Set folder:
                   long folderId = c.getLong(
                                       c.getColumnIndex(BluetoothMapContract.MessageColumns.FOLDER_ID));
                   folderElement = currentFolder.getFolderById(folderId);
                   message.setCompleteFolder(folderElement.getFullPath());

                   // Set recipient:
                   String nameEmail = c.getString(
                                       c.getColumnIndex(BluetoothMapContract.MessageColumns.TO_LIST));
                   Rfc822Token tokens[] = Rfc822Tokenizer.tokenize(nameEmail);
                   if (tokens.length != 0) {
                       if(D) Log.d(TAG, "Recipient count= " + tokens.length);
                       int i = 0;
                       while (i < tokens.length) {
                           if(V) Log.d(TAG, "Recipient = " + tokens[i].toString());
                           String[] emails = new String[1];
                           emails[0] = tokens[i].getAddress();
                           String name = tokens[i].getName();
                           message.addRecipient(name, name, null, emails, null, null);
                           i++;
                       }
                   }

                   // Set originator:
                   nameEmail = c.getString(c.getColumnIndex(BluetoothMapContract.MessageColumns.FROM_LIST));
                   tokens = Rfc822Tokenizer.tokenize(nameEmail);
                   if (tokens.length != 0) {
                       if(D) Log.d(TAG, "Originator count= " + tokens.length);
                       int i = 0;
                       while (i < tokens.length) {
                           if(V) Log.d(TAG, "Originator = " + tokens[i].toString());
                           String[] emails = new String[1];
                           emails[0] = tokens[i].getAddress();
                           String name = tokens[i].getName();
                           message.addOriginator(name, name, null, emails, null, null);
                           i++;
                       }
                   }
               } finally {
                   if(c != null) c.close();
               }
               // Find out if we get attachments
               String attStr = (appParams.getAttachment() == 0) ?
                                           "/" +  BluetoothMapContract.FILE_MSG_NO_ATTACHMENTS : "";
               Uri uri = Uri.parse(contentUri + "/" + id + attStr);

               // Get email message body content
               int count = 0;
               try {
                   fd = mResolver.openFileDescriptor(uri, "r");
                   is = new FileInputStream(fd.getFileDescriptor());
                   StringBuilder email = new StringBuilder("");
                   byte[] buffer = new byte[1024];
                   while((count = is.read(buffer)) != -1) {
                       // TODO: Handle breaks within a UTF8 character
                       email.append(new String(buffer,0,count));
                       if(V) Log.d(TAG, "Email part = "
                                         + new String(buffer,0,count)
                                         + " count=" + count);
                   }
                   // Set email message body:
                   message.setEmailBody(email.toString());
               } catch (FileNotFoundException e) {
                   Log.w(TAG, e);
               } catch (NullPointerException e) {
                   Log.w(TAG, e);
               } catch (IOException e) {
                   Log.w(TAG, e);
               } finally {
                   try {
                       if(is != null) is.close();
                   } catch (IOException e) {}
                   try {
                       if(fd != null) fd.close();
                   } catch (IOException e) {}
               }
               return message.encode();
           }
       } finally {
           if (c != null) c.close();
       }
       throw new IllegalArgumentException("EMAIL handle not found");
   }
   /**
   *
   * @param id the content provider id for the message to fetch.
   * @param appParams The application parameter object received from the client.
   * @return a byte[] containing the UTF-8 encoded bMessage to send to the client.
   * @throws UnsupportedEncodingException if UTF-8 is not supported,
   * which is guaranteed to be supported on an android device
   */

   /**
   *
   * @param id the content provider id for the message to fetch.
   * @param appParams The application parameter object received from the client.
   * @return a byte[] containing the utf-8 encoded bMessage to send to the client.
   * @throws UnsupportedEncodingException if UTF-8 is not supported,
   * which is guaranteed to be supported on an android device
   */
   public byte[] getIMMessage(long id,
           BluetoothMapAppParams appParams,
           BluetoothMapFolderElement folderElement)
                   throws UnsupportedEncodingException {
       long threadId, folderId;

       if (appParams.getCharset() == MAP_MESSAGE_CHARSET_NATIVE)
           throw new IllegalArgumentException(
                   "IM charset native not allowed for IM - must be utf-8");

       BluetoothMapbMessageMime message = new BluetoothMapbMessageMime();
       Uri contentUri = Uri.parse(mBaseUri + BluetoothMapContract.TABLE_MESSAGE);
       Cursor c = mResolver.query(contentUri,
               BluetoothMapContract.BT_MESSAGE_PROJECTION, "_ID = " + id, null, null);
       Cursor contacts = null;
       try {
           if(c != null && c.moveToFirst()) {
               message.setType(TYPE.IM);
               message.setVersionString(mMessageVersion);

               // The IM message info:
               int read =
                       c.getInt(c.getColumnIndex(BluetoothMapContract.MessageColumns.FLAG_READ));
               if (read == 1)
                   message.setStatus(true);
               else
                   message.setStatus(false);

               threadId =
                       c.getInt(c.getColumnIndex(BluetoothMapContract.MessageColumns.THREAD_ID));
               folderId =
                       c.getLong(c.getColumnIndex(BluetoothMapContract.MessageColumns.FOLDER_ID));
               folderElement = folderElement.getFolderById(folderId);
               message.setCompleteFolder(folderElement.getFullPath());
               message.setSubject(c.getString(
                       c.getColumnIndex(BluetoothMapContract.MessageColumns.SUBJECT)));
               message.setMessageId(c.getString(
                       c.getColumnIndex(BluetoothMapContract.MessageColumns._ID)));
               message.setDate(c.getLong(
                       c.getColumnIndex(BluetoothMapContract.MessageColumns.DATE)));
               message.setTextOnly(c.getInt(c.getColumnIndex(
                       BluetoothMapContract.MessageColumns.ATTACHMENT_SIZE)) != 0 ? false : true);

               message.setIncludeAttachments(appParams.getAttachment() == 0 ? false : true);

               // c.getLong(c.getColumnIndex(Mms.DATE_SENT)); - this is never used
               // c.getInt(c.getColumnIndex(Mms.STATUS)); - don't know what this is

               // The parts

               //FIXME use the parts when ready - until then use the body column for text-only
               //  extractIMParts(id, message);
               //FIXME next few lines are temporary code
               MimePart part = message.addMimePart();
               part.mData = c.getString((c.getColumnIndex(
                       BluetoothMapContract.MessageColumns.BODY))).getBytes("UTF-8");
               part.mCharsetName = "utf-8";
               part.mContentId = "0";
               part.mContentType = "text/plain";
               message.updateCharset();
               // FIXME end temp code

               Uri contactsUri = Uri.parse(mBaseUri + BluetoothMapContract.TABLE_CONVOCONTACT);
               contacts = mResolver.query(contactsUri,
                       BluetoothMapContract.BT_CONTACT_PROJECTION,
                       BluetoothMapContract.ConvoContactColumns.CONVO_ID
                       + " = " + threadId, null, null);
               // TODO this will not work for group-chats
               if(contacts != null && contacts.moveToFirst()){
                   String name = contacts.getString(contacts.getColumnIndex(
                           BluetoothMapContract.ConvoContactColumns.NAME));
                   String btUid[] = new String[1];
                   btUid[0]= contacts.getString(contacts.getColumnIndex(
                           BluetoothMapContract.ConvoContactColumns.X_BT_UID));
                   String nickname = contacts.getString(contacts.getColumnIndex(
                           BluetoothMapContract.ConvoContactColumns.NICKNAME));
                   String btUci[] = new String[1];
                   String btOwnUci[] = new String[1];
                   btOwnUci[0] = mAccount.getUciFull();
                   btUci[0] = contacts.getString(contacts.getColumnIndex(
                           BluetoothMapContract.ConvoContactColumns.UCI));
                   if(folderId == BluetoothMapContract.FOLDER_ID_SENT
                           || folderId == BluetoothMapContract.FOLDER_ID_OUTBOX) {
                       message.addRecipient(nickname,name,null, null, btUid, btUci);
                       message.addOriginator(null, btOwnUci);

                   }else {
                       message.addOriginator(nickname,name,null, null, btUid, btUci);
                       message.addRecipient(null, btOwnUci);

                   }
               }
               return message.encode();
           }
       } finally {
           if(c != null) c.close();
           if(contacts != null) contacts.close();
       }

       throw new IllegalArgumentException("IM handle not found");
   }

   public void setRemoteFeatureMask(int featureMask){
       this.mRemoteFeatureMask = featureMask;
       if(V) Log.d(TAG, "setRemoteFeatureMask");
       if((this.mRemoteFeatureMask & BluetoothMapUtils.MAP_FEATURE_MESSAGE_LISTING_FORMAT_V11_BIT)
               == BluetoothMapUtils.MAP_FEATURE_MESSAGE_LISTING_FORMAT_V11_BIT) {
           if(V) Log.d(TAG, "setRemoteFeatureMask MAP_MESSAGE_LISTING_FORMAT_V11");
           this.mMsgListingVersion = BluetoothMapUtils.MAP_MESSAGE_LISTING_FORMAT_V11;
       }
   }

   public int getRemoteFeatureMask(){
       return this.mRemoteFeatureMask;
   }

    HashMap<Long,BluetoothMapConvoListingElement> getSmsMmsConvoList() {
        return mMasInstance.getSmsMmsConvoList();
    }

    void setSmsMmsConvoList(HashMap<Long,BluetoothMapConvoListingElement> smsMmsConvoList) {
        mMasInstance.setSmsMmsConvoList(smsMmsConvoList);
    }

    HashMap<Long,BluetoothMapConvoListingElement> getImEmailConvoList() {
        return mMasInstance.getImEmailConvoList();
    }

    void setImEmailConvoList(HashMap<Long,BluetoothMapConvoListingElement> imEmailConvoList) {
        mMasInstance.setImEmailConvoList(imEmailConvoList);
    }
}
