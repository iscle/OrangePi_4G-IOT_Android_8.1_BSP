/*
* Copyright (C) 2015 Samsung System LSI
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

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.os.UserManager;
import android.text.format.DateUtils;
import android.util.Log;

import com.android.bluetooth.SignedLongLong;
import com.android.bluetooth.map.BluetoothMapUtils.TYPE;
import com.android.bluetooth.map.BluetoothMapMasInstance;
import com.android.bluetooth.mapapi.BluetoothMapContract;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Calendar;

import javax.obex.HeaderSet;
import javax.obex.Operation;
import javax.obex.ResponseCodes;
import javax.obex.ServerRequestHandler;


public class BluetoothMapObexServer extends ServerRequestHandler {

    private static final String TAG = "BluetoothMapObexServer";

    private static final boolean D = BluetoothMapService.DEBUG;
    private static final boolean V = BluetoothMapService.VERBOSE;

    private static final int UUID_LENGTH = 16;

    private static final long PROVIDER_ANR_TIMEOUT = 20 * DateUtils.SECOND_IN_MILLIS;

    /* OBEX header and value used to detect clients that support threadId in the message listing. */
    private static final int THREADED_MAIL_HEADER_ID = 0xFA;
    private static final long THREAD_MAIL_KEY = 0x534c5349;

    // 128 bit UUID for MAP
    private static final byte[] MAP_TARGET = new byte[] {
             (byte)0xBB, (byte)0x58, (byte)0x2B, (byte)0x40,
             (byte)0x42, (byte)0x0C, (byte)0x11, (byte)0xDB,
             (byte)0xB0, (byte)0xDE, (byte)0x08, (byte)0x00,
             (byte)0x20, (byte)0x0C, (byte)0x9A, (byte)0x66
             };
    public static final ParcelUuid MAP =
            ParcelUuid.fromString("00001134-0000-1000-8000-00805F9B34FB");
    public static final ParcelUuid MNS =
            ParcelUuid.fromString("00001133-0000-1000-8000-00805F9B34FB");
    public static final ParcelUuid MAS =
            ParcelUuid.fromString("00001132-0000-1000-8000-00805F9B34FB");
    /* Message types */
    private static final String TYPE_GET_FOLDER_LISTING              = "x-obex/folder-listing";
    private static final String TYPE_GET_MESSAGE_LISTING             = "x-bt/MAP-msg-listing";
    private static final String TYPE_GET_CONVO_LISTING               = "x-bt/MAP-convo-listing";
    private static final String TYPE_MESSAGE                         = "x-bt/message";
    private static final String TYPE_SET_MESSAGE_STATUS              = "x-bt/messageStatus";
    private static final String TYPE_SET_NOTIFICATION_REGISTRATION
            = "x-bt/MAP-NotificationRegistration";
    private static final String TYPE_MESSAGE_UPDATE                  = "x-bt/MAP-messageUpdate";
    private static final String TYPE_GET_MAS_INSTANCE_INFORMATION
            = "x-bt/MASInstanceInformation";
    private static final String TYPE_SET_OWNER_STATUS                = "x-bt/participant";
    private static final String TYPE_SET_NOTIFICATION_FILTER
            = "x-bt/MAP-notification-filter";

    private static final int MAS_INSTANCE_INFORMATION_LENGTH = 200;

    private BluetoothMapFolderElement mCurrentFolder;
    private BluetoothMapContentObserver mObserver = null;
    private Handler mCallback = null;
    private Context mContext;
    private boolean mIsAborted = false;
    BluetoothMapContent mOutContent;
    private String mBaseUriString = null;
    private long mAccountId = 0;
    private BluetoothMapAccountItem mAccount = null;
    private Uri mEmailFolderUri = null;
    private int mMasId = 0;
    private BluetoothMapMasInstance mMasInstance; // TODO: change to interface?
    // updated during connect if remote has alternative value
    private int mRemoteFeatureMask = BluetoothMapUtils.MAP_FEATURE_DEFAULT_BITMASK;
    private boolean mEnableSmsMms = false;
    private boolean mThreadIdSupport = false; // true if peer supports threadId in msg listing
    // Defaults message version is 1.0 but 1.1+ if feature bit is set
    private String mMessageVersion = BluetoothMapUtils.MAP_V10_STR;
    private String mAuthority;
    private ContentResolver mResolver;
    private ContentProviderClient mProviderClient = null;

    public BluetoothMapObexServer(Handler callback,
                                  Context context,
                                  BluetoothMapContentObserver observer,
                                  BluetoothMapMasInstance mas,
                                  BluetoothMapAccountItem account,
                                  boolean enableSmsMms) throws RemoteException {
        super();
        mCallback = callback;
        mContext = context;
        mObserver = observer;
        mEnableSmsMms = enableSmsMms;
        mAccount = account;
        mMasId = mas.getMasId();
        mMasInstance = mas;
        mRemoteFeatureMask = mMasInstance.getRemoteFeatureMask();

        if(account != null && account.getProviderAuthority() != null) {
            mAccountId = account.getAccountId();
            mAuthority = account.getProviderAuthority();
            mResolver = mContext.getContentResolver();
            if (D) Log.d(TAG, "BluetoothMapObexServer(): accountId=" + mAccountId);
            mBaseUriString = account.mBase_uri + "/";
            if (D) Log.d(TAG, "BluetoothMapObexServer(): baseUri=" + mBaseUriString);
            if (account.getType() == TYPE.EMAIL) {
                mEmailFolderUri = BluetoothMapContract.buildFolderUri(mAuthority,
                                                                 Long.toString(mAccountId));
                if (D) Log.d(TAG, "BluetoothMapObexServer(): mEmailFolderUri=" + mEmailFolderUri);
            }
            mProviderClient = acquireUnstableContentProviderOrThrow();
        }

        buildFolderStructure(); /* Build the default folder structure, and set
                                   mCurrentFolder to root folder */
        mObserver.setFolderStructure(mCurrentFolder.getRoot());

        mOutContent = new BluetoothMapContent(mContext, mAccount, mMasInstance);

    }

    /**
     *
     */
    private ContentProviderClient acquireUnstableContentProviderOrThrow() throws RemoteException {
        ContentProviderClient providerClient =
                            mResolver.acquireUnstableContentProviderClient(mAuthority);
        if (providerClient == null) {
            throw new RemoteException("Failed to acquire provider for " + mAuthority);
        }
        providerClient.setDetectNotResponding(PROVIDER_ANR_TIMEOUT);
        return providerClient;
    }

    /**
     * Build the default minimal folder structure, as defined in the MAP specification.
     */
    private void buildFolderStructure() throws RemoteException {
        mCurrentFolder = new BluetoothMapFolderElement("root", null);//This will be the root element
        mCurrentFolder.setHasSmsMmsContent(mEnableSmsMms);
        boolean hasIM = false;
        boolean hasEmail = false;
        if (mAccount != null) {
           if (mAccount.getType() == TYPE.IM)
               hasIM = true;
           if( mAccount.getType() == TYPE.EMAIL)
               hasEmail = true;
        }
        mCurrentFolder.setHasImContent(hasIM);
        mCurrentFolder.setHasEmailContent(hasEmail);

        BluetoothMapFolderElement tmpFolder;
        tmpFolder = mCurrentFolder.addFolder("telecom"); // root/telecom
        tmpFolder.setHasSmsMmsContent(mEnableSmsMms);
        tmpFolder.setHasImContent(hasIM);
        tmpFolder.setHasEmailContent(hasEmail);

        tmpFolder = tmpFolder.addFolder("msg");          // root/telecom/msg
        tmpFolder.setHasSmsMmsContent(mEnableSmsMms);
        tmpFolder.setHasImContent(hasIM);
        tmpFolder.setHasEmailContent(hasEmail);

        // Add the mandatory folders
        addBaseFolders(tmpFolder);
        if (mEnableSmsMms) {
            addSmsMmsFolders(tmpFolder);
        }
        if (hasEmail) {
            if (D) Log.d(TAG, "buildFolderStructure(): " + mEmailFolderUri.toString());
            addEmailFolders(tmpFolder);
        }
        if (hasIM) {
            addImFolders(tmpFolder);
        }
    }

    /**
     * Add base (Inbox/Outbox/Sent/Deleted)
     * @param root
     */
    private void addBaseFolders(BluetoothMapFolderElement root) {
        root.addFolder(BluetoothMapContract.FOLDER_NAME_INBOX);         // root/telecom/msg/inbox
        root.addFolder(BluetoothMapContract.FOLDER_NAME_OUTBOX);
        root.addFolder(BluetoothMapContract.FOLDER_NAME_SENT);
        root.addFolder(BluetoothMapContract.FOLDER_NAME_DELETED);
    }

    /**
     * Add SMS / MMS Base folders
     * @param root
     */
    private void addSmsMmsFolders(BluetoothMapFolderElement root) {
        root.addSmsMmsFolder(BluetoothMapContract.FOLDER_NAME_INBOX);   // root/telecom/msg/inbox
        root.addSmsMmsFolder(BluetoothMapContract.FOLDER_NAME_OUTBOX);
        root.addSmsMmsFolder(BluetoothMapContract.FOLDER_NAME_SENT);
        root.addSmsMmsFolder(BluetoothMapContract.FOLDER_NAME_DELETED);
        root.addSmsMmsFolder(BluetoothMapContract.FOLDER_NAME_DRAFT);
    }


    private void addImFolders(BluetoothMapFolderElement root) throws RemoteException {
        // Select all parent folders
        root.addImFolder(BluetoothMapContract.FOLDER_NAME_INBOX,
                BluetoothMapContract.FOLDER_ID_INBOX);       // root/telecom/msg/inbox
        root.addImFolder(BluetoothMapContract.FOLDER_NAME_OUTBOX,
                BluetoothMapContract.FOLDER_ID_OUTBOX);
        root.addImFolder(BluetoothMapContract.FOLDER_NAME_SENT,
                BluetoothMapContract.FOLDER_ID_SENT);
        root.addImFolder(BluetoothMapContract.FOLDER_NAME_DELETED,
                BluetoothMapContract.FOLDER_ID_DELETED);
        root.addImFolder(BluetoothMapContract.FOLDER_NAME_DRAFT,
                BluetoothMapContract.FOLDER_ID_DRAFT);
    }

    /**
     * Recursively adds folders based on the folders in the email content provider.
     *       Add a content observer? - to refresh the folder list if any change occurs.
     *       Consider simply deleting the entire table, and then rebuild using
     *       buildFolderStructure()
     *       WARNING: there is no way to notify the client about these changes - hence
     *       we need to either keep the folder structure constant, disconnect or fail anything
     *       referring to currentFolder.
     *       It is unclear what to set as current folder to be able to go one level up...
     *       The best solution would be to keep the folder structure constant during a connection.
     * @param folder the parent folder to which subFolders needs to be added. The
     *        folder.getFolderId() will be used to query sub-folders.
     *        Use a parentFolder with id -1 to get all folders from root.
     */
    private void addEmailFolders(BluetoothMapFolderElement parentFolder) throws RemoteException {
        // Select all parent folders
        BluetoothMapFolderElement newFolder;

        String where = BluetoothMapContract.FolderColumns.PARENT_FOLDER_ID +
                        " = " + parentFolder.getFolderId();
        Cursor c = mProviderClient.query(mEmailFolderUri,
                        BluetoothMapContract.BT_FOLDER_PROJECTION, where, null, null);
        try {
            if (c != null) {
                c.moveToPosition(-1);
                while (c.moveToNext()) {
                    String name = c.getString(c.getColumnIndex(
                            BluetoothMapContract.FolderColumns.NAME));
                    long id = c.getLong(c.getColumnIndex(BluetoothMapContract.FolderColumns._ID));
                    newFolder = parentFolder.addEmailFolder(name, id);
                    addEmailFolders(newFolder); // Use recursion to add any sub folders
                }

            } else {
                if (D) Log.d(TAG, "addEmailFolders(): no elements found");
            }
        } finally {
            if (c != null) c.close();
        }
    }

    @Override
    public boolean isSrmSupported() {
        // TODO: Update based on the transport used
        return true;
    }

    public int getRemoteFeatureMask() {
        return mRemoteFeatureMask;
    }

    public void setRemoteFeatureMask(int mRemoteFeatureMask) {
        if(D) Log.d(TAG, "setRemoteFeatureMask() " + Integer.toHexString(mRemoteFeatureMask));
        this.mRemoteFeatureMask = mRemoteFeatureMask;
        this.mOutContent.setRemoteFeatureMask(mRemoteFeatureMask);
    }

    @Override
    public int onConnect(final HeaderSet request, HeaderSet reply) {
        if (D) Log.d(TAG, "onConnect():");
        if (V) logHeader(request);
        mThreadIdSupport = false; // Always assume not supported at new connect.
        mMessageVersion = BluetoothMapUtils.MAP_V10_STR;//always assume version 1.0 to start with
        notifyUpdateWakeLock();
        Long threadedMailKey = null;
        try {
            byte[] uuid = (byte[])request.getHeader(HeaderSet.TARGET);
            threadedMailKey = (Long)request.getHeader(THREADED_MAIL_HEADER_ID);
            if (uuid == null) {
                return ResponseCodes.OBEX_HTTP_NOT_ACCEPTABLE;
            }
            if (D) Log.d(TAG, "onConnect(): uuid=" + Arrays.toString(uuid));

            if (uuid.length != UUID_LENGTH) {
                Log.w(TAG, "Wrong UUID length");
                return ResponseCodes.OBEX_HTTP_NOT_ACCEPTABLE;
            }
            for (int i = 0; i < UUID_LENGTH; i++) {
                if (uuid[i] != MAP_TARGET[i]) {
                    Log.w(TAG, "Wrong UUID");
                    return ResponseCodes.OBEX_HTTP_NOT_ACCEPTABLE;
                }
            }
            reply.setHeader(HeaderSet.WHO, uuid);
        } catch (IOException e) {
            Log.e(TAG,"Exception during onConnect:", e);
            return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
        }

        try {
            byte[] remote = (byte[])request.getHeader(HeaderSet.WHO);
            if (remote != null) {
                if (D) Log.d(TAG, "onConnect(): remote=" + Arrays.toString(remote));
                reply.setHeader(HeaderSet.TARGET, remote);
            }
            if(threadedMailKey != null && threadedMailKey.longValue() == THREAD_MAIL_KEY)
            {
                /* If the client provides the correct key we enable threaded e-mail support
                 * and reply to the client that we support the requested feature.
                 * This is currently an Android only feature. */
                mThreadIdSupport = true;
                reply.setHeader(THREADED_MAIL_HEADER_ID, THREAD_MAIL_KEY);
            }
        } catch (IOException e) {
            Log.e(TAG,"Exception during onConnect:", e);
            mThreadIdSupport = false;
            return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
        }

        if((mRemoteFeatureMask & BluetoothMapUtils.MAP_FEATURE_MESSAGE_LISTING_FORMAT_V11_BIT)
                == BluetoothMapUtils.MAP_FEATURE_MESSAGE_LISTING_FORMAT_V11_BIT) {
            mThreadIdSupport = true;
        }

        if((mRemoteFeatureMask & BluetoothMapUtils.MAP_FEATURE_MESSAGE_FORMAT_V11_BIT)
                == BluetoothMapUtils.MAP_FEATURE_MESSAGE_FORMAT_V11_BIT){
            mMessageVersion = BluetoothMapUtils.MAP_V11_STR;
        }

        if (V) Log.v(TAG, "onConnect(): uuid is ok, will send out " +
                "MSG_SESSION_ESTABLISHED msg.");

        if(mCallback != null) {
            Message msg = Message.obtain(mCallback);
            msg.what = BluetoothMapService.MSG_SESSION_ESTABLISHED;
            msg.sendToTarget();
        }

        return ResponseCodes.OBEX_HTTP_OK;
    }

    @Override
    public void onDisconnect(final HeaderSet req, final HeaderSet resp) {
        if (D) Log.d(TAG, "onDisconnect(): enter");
        if (V) logHeader(req);
        notifyUpdateWakeLock();
        resp.responseCode = ResponseCodes.OBEX_HTTP_OK;
        if (mCallback != null) {
            Message msg = Message.obtain(mCallback);
            msg.what = BluetoothMapService.MSG_SESSION_DISCONNECTED;
            msg.sendToTarget();
            if (V) Log.v(TAG, "onDisconnect(): msg MSG_SESSION_DISCONNECTED sent out.");
        }
    }

    @Override
    public int onAbort(HeaderSet request, HeaderSet reply) {
        if (D) Log.d(TAG, "onAbort(): enter.");
        notifyUpdateWakeLock();
        mIsAborted = true;
        return ResponseCodes.OBEX_HTTP_OK;
    }

    private boolean isUserUnlocked() {
        UserManager manager = UserManager.get(mContext);
        return (manager == null || manager.isUserUnlocked());
    }

    @Override
    public int onPut(final Operation op) {
        if (D) Log.d(TAG, "onPut(): enter");
        mIsAborted = false;
        notifyUpdateWakeLock();
        HeaderSet request = null;
        String type, name;
        byte[] appParamRaw;
        BluetoothMapAppParams appParams = null;

        try {
            request = op.getReceivedHeader();
            if (V) logHeader(request);
            type = (String)request.getHeader(HeaderSet.TYPE);
            name = (String)request.getHeader(HeaderSet.NAME);
            appParamRaw = (byte[])request.getHeader(HeaderSet.APPLICATION_PARAMETER);
            if(appParamRaw != null)
                appParams = new BluetoothMapAppParams(appParamRaw);
            if(D) Log.d(TAG,"type = " + type + ", name = " + name);
            if (type.equals(TYPE_MESSAGE_UPDATE)) {
                if(V) {
                    Log.d(TAG,"TYPE_MESSAGE_UPDATE:");
                }
                return updateInbox();
            } else if (type.equals(TYPE_SET_NOTIFICATION_REGISTRATION)) {
                if(V) {
                    Log.d(TAG,"TYPE_SET_NOTIFICATION_REGISTRATION: NotificationStatus: "
                            + appParams.getNotificationStatus());
                }
                return mObserver.setNotificationRegistration(appParams.getNotificationStatus());
            } else if (type.equals(TYPE_SET_NOTIFICATION_FILTER)) {
                if(V) {
                    Log.d(TAG,"TYPE_SET_NOTIFICATION_FILTER: NotificationFilter: "
                            + appParams.getNotificationFilter());
                }
                if (!isUserUnlocked()) {
                    Log.e(TAG, "Storage locked, " + type + " failed");
                    return ResponseCodes.OBEX_HTTP_UNAVAILABLE;
                }
                mObserver.setNotificationFilter(appParams.getNotificationFilter());
                return ResponseCodes.OBEX_HTTP_OK;
            } else if (type.equals(TYPE_SET_MESSAGE_STATUS)) {
                if(V) {
                    Log.d(TAG,"TYPE_SET_MESSAGE_STATUS: " +
                              "StatusIndicator: " + appParams.getStatusIndicator()
                            + ", StatusValue: " + appParams.getStatusValue()
                            + ", ExtentedData: " + "" ); // TODO:   appParams.getExtendedImData());
                }
                if (!isUserUnlocked()) {
                    Log.e(TAG, "Storage locked, " + type + " failed");
                    return ResponseCodes.OBEX_HTTP_UNAVAILABLE;
                }
                return setMessageStatus(name, appParams);
            } else if (type.equals(TYPE_MESSAGE)) {
                if(V) {
                    Log.d(TAG,"TYPE_MESSAGE: Transparet: " + appParams.getTransparent()
                            + ", retry: " + appParams.getRetry()
                            + ", charset: " + appParams.getCharset());
                }
                if (!isUserUnlocked()) {
                    Log.e(TAG, "Storage locked, " + type + " failed");
                    return ResponseCodes.OBEX_HTTP_UNAVAILABLE;
                }
                return pushMessage(op, name, appParams, mMessageVersion);
            } else if (type.equals(TYPE_SET_OWNER_STATUS)) {
                if(V) {
                    Log.d(TAG,"TYPE_SET_OWNER_STATUS:" +
                          " PresenceAvailability " + appParams.getPresenceAvailability() +
                          ", PresenceStatus: " + appParams.getPresenceStatus() +
                          ", LastActivity: " + appParams.getLastActivityString() +
                          ", ChatStatus: " + appParams.getChatState() +
                          ", ChatStatusConvoId: " + appParams.getChatStateConvoIdString());
                }
                return setOwnerStatus(name, appParams);
            }

        } catch (RemoteException e){
            //reload the providerClient and return error
            try {
                mProviderClient = acquireUnstableContentProviderOrThrow();
            }catch (RemoteException e2){
                //should not happen
            }
            return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        }catch (Exception e) {

            if(D) {
                Log.e(TAG, "Exception occured while handling request",e);
            } else {
                Log.e(TAG, "Exception occured while handling request");
            }
            if(mIsAborted) {
                return ResponseCodes.OBEX_HTTP_OK;
            } else {
                return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
            }
        }
        return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
    }

    private int updateInbox() throws RemoteException{
        if (mAccount != null) {
            BluetoothMapFolderElement inboxFolder = mCurrentFolder.getFolderByName(
                    BluetoothMapContract.FOLDER_NAME_INBOX);
            if (inboxFolder != null) {
                long accountId = mAccountId;
                if (D) Log.d(TAG,"updateInbox inbox=" + inboxFolder.getName() + "id="
                        + inboxFolder.getFolderId());

                final Bundle extras = new Bundle(2);
                if (accountId != -1) {
                    if (D) Log.d(TAG,"updateInbox accountId=" + accountId);
                    extras.putLong(BluetoothMapContract.EXTRA_UPDATE_FOLDER_ID,
                            inboxFolder.getFolderId());
                    extras.putLong(BluetoothMapContract.EXTRA_UPDATE_ACCOUNT_ID, accountId);
                } else {
                    // Only error code allowed on an UpdateInbox is OBEX_HTTP_NOT_IMPLEMENTED,
                    // i.e. if e.g. update not allowed on the mailbox
                    if (D) Log.d(TAG,"updateInbox accountId=0 -> OBEX_HTTP_NOT_IMPLEMENTED");
                    return ResponseCodes.OBEX_HTTP_NOT_IMPLEMENTED;
                }

                Uri emailUri = Uri.parse(mBaseUriString);
                if (D) Log.d(TAG,"updateInbox in: " + emailUri.toString());
                try {
                    if (D) Log.d(TAG,"updateInbox call()...");
                    Bundle myBundle = mProviderClient.call(
                            BluetoothMapContract.METHOD_UPDATE_FOLDER, null, extras);
                    if (myBundle != null)
                        return ResponseCodes.OBEX_HTTP_OK;
                    else {
                        if (D) Log.d(TAG,"updateInbox call failed");
                        return ResponseCodes.OBEX_HTTP_NOT_IMPLEMENTED;
                    }
                } catch (RemoteException e){
                    mProviderClient = acquireUnstableContentProviderOrThrow();
                    return ResponseCodes.OBEX_HTTP_UNAVAILABLE;
                }catch (NullPointerException e) {
                    if(D) Log.e(TAG, "UpdateInbox - if uri or method is null", e);
                    return ResponseCodes.OBEX_HTTP_UNAVAILABLE;

                } catch (IllegalArgumentException e) {
                    if(D) Log.e(TAG, "UpdateInbox - if uri is not known", e);
                    return ResponseCodes.OBEX_HTTP_UNAVAILABLE;
                }
            }
        }

        return ResponseCodes.OBEX_HTTP_NOT_IMPLEMENTED;
    }

     private BluetoothMapFolderElement getFolderElementFromName(String folderName) {
        BluetoothMapFolderElement folderElement = null;

        if(folderName == null || folderName.trim().isEmpty() ) {
            folderElement = mCurrentFolder;
            if(D) Log.d(TAG, "no folder name supplied, setting folder to current: "
                             + folderElement.getName());
        } else {
            folderElement = mCurrentFolder.getSubFolder(folderName);
            if (folderElement != null) {
                if(D) Log.d(TAG, "Folder name: " + folderName +
                                 " resulted in this element: " + folderElement.getName());
            }
        }
        return folderElement;
    }

    private int pushMessage(final Operation op, String folderName,
            BluetoothMapAppParams appParams, String messageVersion) {
        if(appParams.getCharset() == BluetoothMapAppParams.INVALID_VALUE_PARAMETER) {
            if(D) Log.d(TAG, "pushMessage: Missing charset - unable to decode message content. " +
                    "appParams.getCharset() = " + appParams.getCharset());
            return ResponseCodes.OBEX_HTTP_PRECON_FAILED;
        }
        InputStream bMsgStream = null;
        try {
            BluetoothMapFolderElement folderElement = getFolderElementFromName(folderName);
            if(folderElement == null) {
                Log.w(TAG,"pushMessage: folderElement == null - sending OBEX_HTTP_PRECON_FAILED");
                return ResponseCodes.OBEX_HTTP_PRECON_FAILED;
            } else {
                folderName = folderElement.getName();
            }
            if (!folderName.equalsIgnoreCase(BluetoothMapContract.FOLDER_NAME_OUTBOX) &&
                    !folderName.equalsIgnoreCase(BluetoothMapContract.FOLDER_NAME_DRAFT)) {
                if(D) Log.d(TAG, "pushMessage: Is only allowed to outbox and draft. " +
                        "folderName=" + folderName);
                return ResponseCodes.OBEX_HTTP_NOT_ACCEPTABLE;
            }

            /*  - Read out the message
             *  - Decode into a bMessage
             *  - send it.
             */
            BluetoothMapbMessage message;
            bMsgStream = op.openInputStream();
            // Decode the messageBody
            message = BluetoothMapbMessage.parse(bMsgStream, appParams.getCharset());
            message.setVersionString(messageVersion);
            // Send message
            if (mObserver == null || message == null) {
                // Should not happen except at shutdown.
                if(D) Log.w(TAG, "mObserver or parsed message not available" );
                return ResponseCodes.OBEX_HTTP_UNAVAILABLE;
            }

            if ((message.getType().equals(TYPE.EMAIL) && (folderElement.getFolderId() == -1))
                    || ((message.getType().equals(TYPE.SMS_GSM) ||
                         message.getType().equals(TYPE.SMS_CDMA) ||
                         message.getType().equals(TYPE.MMS))
                         && !folderElement.hasSmsMmsContent()) ) {
                if(D) Log.w(TAG, "Wrong message type recieved" );
                return ResponseCodes.OBEX_HTTP_NOT_ACCEPTABLE;
            }

            long handle = mObserver.pushMessage(message, folderElement, appParams, mBaseUriString);
            if (D) Log.d(TAG, "pushMessage handle: " + handle);
            if (handle < 0) {
                if(D) Log.w(TAG, "Message  handle not created" );
                return ResponseCodes.OBEX_HTTP_UNAVAILABLE; // Should not happen.
            }
            HeaderSet replyHeaders = new HeaderSet();
            String handleStr = BluetoothMapUtils.getMapHandle(handle, message.getType());
            if (D) Log.d(TAG, "handleStr: " + handleStr + " message.getType(): "
                               + message.getType());
            replyHeaders.setHeader(HeaderSet.NAME, handleStr);
            op.sendHeaders(replyHeaders);

        } catch (RemoteException e) {
            //reload the providerClient and return error
            try {
                mProviderClient = acquireUnstableContentProviderOrThrow();
            }catch (RemoteException e2){
                //should not happen
                if(D) Log.w(TAG, "acquireUnstableContentProviderOrThrow FAILED");
            }
            return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        } catch (IllegalArgumentException e) {
            if (D) Log.e(TAG, "Wrongly formatted bMessage received", e);
            return ResponseCodes.OBEX_HTTP_PRECON_FAILED;
        } catch (IOException e) {
            if (D) Log.e(TAG, "Exception occured: ", e);
            if(mIsAborted == true) {
                if(D) Log.d(TAG, "PushMessage Operation Aborted");
                return ResponseCodes.OBEX_HTTP_OK;
            } else {
                return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
            }
        } catch (Exception e) {
            if (D) Log.e(TAG, "Exception:", e);
            return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        } finally {
            if(bMsgStream != null) {
                try {
                    bMsgStream.close();
                } catch (IOException e) {}
            }
        }
        return ResponseCodes.OBEX_HTTP_OK;
    }

    private int setMessageStatus(String msgHandle, BluetoothMapAppParams appParams) {
        int indicator = appParams.getStatusIndicator();
        int value = appParams.getStatusValue();
        String extendedData = ""; // TODO: appParams.getExtendedImData();

        long handle;
        BluetoothMapUtils.TYPE msgType;

        if (msgHandle == null) {
            return ResponseCodes.OBEX_HTTP_PRECON_FAILED;
        } else if ((indicator == BluetoothMapAppParams.INVALID_VALUE_PARAMETER ||
                   value == BluetoothMapAppParams.INVALID_VALUE_PARAMETER) &&
                   extendedData == null) {
            return ResponseCodes.OBEX_HTTP_PRECON_FAILED;
        }
        if (mObserver == null) {
            if(D) Log.e(TAG, "Error: no mObserver!");
            return ResponseCodes.OBEX_HTTP_UNAVAILABLE; // Should not happen.
        }

        try {
            handle = BluetoothMapUtils.getCpHandle(msgHandle);
            msgType = BluetoothMapUtils.getMsgTypeFromHandle(msgHandle);
            if(D) Log.d(TAG,"setMessageStatus. Handle:" + handle+", MsgType: "+ msgType);
        } catch (NumberFormatException e) {
            Log.w(TAG, "Wrongly formatted message handle: " + msgHandle);
            return ResponseCodes.OBEX_HTTP_PRECON_FAILED;
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Message type not found in handle string: " + msgHandle);
            return ResponseCodes.OBEX_HTTP_PRECON_FAILED;
        }

        if( indicator == BluetoothMapAppParams.STATUS_INDICATOR_DELETED) {
            if (!mObserver.setMessageStatusDeleted(handle, msgType, mCurrentFolder,
                    mBaseUriString, value)) {
                if(D) Log.w(TAG,"setMessageStatusDeleted failed");
                return ResponseCodes.OBEX_HTTP_UNAVAILABLE;
            }
        } else if( indicator == BluetoothMapAppParams.STATUS_INDICATOR_READ) {
            try {
                if (!mObserver.setMessageStatusRead(handle, msgType, mBaseUriString, value)) {
                    if(D) Log.w(TAG,"not able to update the message");
                    return ResponseCodes.OBEX_HTTP_UNAVAILABLE;
                }
            } catch (RemoteException e) {
                if(D) Log.w(TAG,"Error in setMessageStatusRead()", e);
                return ResponseCodes.OBEX_HTTP_UNAVAILABLE;
            }
        }
        if (extendedData != null) {

        }

        return ResponseCodes.OBEX_HTTP_OK;
    }

    private int setOwnerStatus(String msgHandle, BluetoothMapAppParams appParams)
            throws RemoteException{
        // This does only work for IM
        if (mAccount != null && mAccount.getType() == BluetoothMapUtils.TYPE.IM) {
            final Bundle extras = new Bundle(5);

            int presenceState = appParams.getPresenceAvailability();
            String presenceStatus = appParams.getPresenceStatus();
            long lastActivity = appParams.getLastActivity();
            int chatState = appParams.getChatState();
            String chatStatusConvoId = appParams.getChatStateConvoIdString();

            if(presenceState == BluetoothMapAppParams.INVALID_VALUE_PARAMETER &&
               presenceStatus == null &&
               lastActivity == BluetoothMapAppParams.INVALID_VALUE_PARAMETER &&
               chatState == BluetoothMapAppParams.INVALID_VALUE_PARAMETER &&
               chatStatusConvoId == null) {
                return ResponseCodes.OBEX_HTTP_PRECON_FAILED;
            }

            if(presenceState != BluetoothMapAppParams.INVALID_VALUE_PARAMETER) {
                extras.putInt(BluetoothMapContract.EXTRA_PRESENCE_STATE, presenceState);
            }
            if (presenceStatus != null){
                extras.putString(BluetoothMapContract.EXTRA_PRESENCE_STATUS, presenceStatus);
            }
            if (lastActivity != BluetoothMapAppParams.INVALID_VALUE_PARAMETER){
                extras.putLong(BluetoothMapContract.EXTRA_LAST_ACTIVE, lastActivity);
            }
            if (chatState != BluetoothMapAppParams.INVALID_VALUE_PARAMETER &&
                chatStatusConvoId != null){
                extras.putInt(BluetoothMapContract.EXTRA_CHAT_STATE, chatState);
                extras.putString(BluetoothMapContract.EXTRA_CONVERSATION_ID, chatStatusConvoId);
            }

            Uri uri = Uri.parse(mBaseUriString);
            if (D) Log.d(TAG,"setOwnerStatus in: " + uri.toString());
            try {
                if (D) Log.d(TAG,"setOwnerStatus call()...");
                Bundle myBundle = mProviderClient.call(
                        BluetoothMapContract.METHOD_SET_OWNER_STATUS, null, extras);
                if (myBundle != null)
                    return ResponseCodes.OBEX_HTTP_OK;
                else {
                    if (D) Log.d(TAG,"setOwnerStatus call failed");
                    return ResponseCodes.OBEX_HTTP_NOT_IMPLEMENTED;
                }
            } catch (RemoteException e){
                mProviderClient = acquireUnstableContentProviderOrThrow();
                return ResponseCodes.OBEX_HTTP_UNAVAILABLE;
            } catch (NullPointerException e) {
                if(D) Log.e(TAG, "setOwnerStatus - if uri or method is null", e);
                return ResponseCodes.OBEX_HTTP_UNAVAILABLE;
            } catch (IllegalArgumentException e) {
                if(D) Log.e(TAG, "setOwnerStatus - if uri is not known", e);
                return ResponseCodes.OBEX_HTTP_UNAVAILABLE;
            }
        }
        return ResponseCodes.OBEX_HTTP_UNAVAILABLE;
    }

    @Override
    public int onSetPath(final HeaderSet request, final HeaderSet reply, final boolean backup,
            final boolean create) {
        String folderName;
        BluetoothMapFolderElement folder;
        notifyUpdateWakeLock();
        try {
            folderName = (String)request.getHeader(HeaderSet.NAME);
        } catch (Exception e) {
            if(D) {
                Log.e(TAG, "request headers error" , e);
            } else {
                Log.e(TAG, "request headers error");
            }
            return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        }

        if (V) logHeader(request);
        if (D) Log.d(TAG, "onSetPath name is " + folderName +
                          " backup: " + backup +
                          " create: " + create);

        if(backup == true){
            if(mCurrentFolder.getParent() != null)
                mCurrentFolder = mCurrentFolder.getParent();
            else
                return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        }

        if (folderName == null || folderName.trim().isEmpty()) {
            if(backup == false)
                mCurrentFolder = mCurrentFolder.getRoot();
        }
        else {
            folder = mCurrentFolder.getSubFolder(folderName);
            if(folder != null)
                mCurrentFolder = folder;
            else
                return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        }
        if (V) Log.d(TAG, "Current Folder: " + mCurrentFolder.getName());
        return ResponseCodes.OBEX_HTTP_OK;
    }

    @Override
    public void onClose() {
        if (mCallback != null) {
            Message msg = Message.obtain(mCallback);
            msg.what = BluetoothMapService.MSG_SERVERSESSION_CLOSE;
            msg.arg1 = mMasId;
            msg.sendToTarget();
            if (D) Log.d(TAG, "onClose(): msg MSG_SERVERSESSION_CLOSE sent out.");

        }
        if(mProviderClient != null){
            mProviderClient.release();
            mProviderClient = null;
        }

    }

    @Override
    public int onGet(Operation op) {
        notifyUpdateWakeLock();
        mIsAborted = false;
        HeaderSet request;
        String type;
        String name;
        byte[] appParamRaw = null;
        BluetoothMapAppParams appParams = null;
        try {
            request = op.getReceivedHeader();
            type = (String)request.getHeader(HeaderSet.TYPE);

            appParamRaw = (byte[])request.getHeader(HeaderSet.APPLICATION_PARAMETER);
            if(appParamRaw != null)
                appParams = new BluetoothMapAppParams(appParamRaw);

            if (V) logHeader(request);
            if (D) Log.d(TAG, "OnGet type is " + type );

            if (type == null) {
                if (V) Log.d(TAG, "type is null?" + type);
                return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
            }

            if (type.equals(TYPE_GET_FOLDER_LISTING)) {
                if (V && appParams != null) {
                    Log.d(TAG,"TYPE_GET_FOLDER_LISTING: MaxListCount = "
                            + appParams.getMaxListCount()
                            + ", ListStartOffset = " + appParams.getStartOffset());
                }
                // Block until all packets have been send.
                return sendFolderListingRsp(op, appParams);
            } else if (type.equals(TYPE_GET_MESSAGE_LISTING)){
                name = (String)request.getHeader(HeaderSet.NAME);
                if (V && appParams != null) {
                    Log.d(TAG,"TYPE_GET_MESSAGE_LISTING: folder name is: " + name +
                            ", MaxListCount = " + appParams.getMaxListCount() +
                            ", ListStartOffset = " + appParams.getStartOffset());
                    Log.d(TAG,"SubjectLength = " + appParams.getSubjectLength() +
                            ", ParameterMask = " + appParams.getParameterMask());
                    Log.d(TAG,"FilterMessageType = " + appParams.getFilterMessageType() );
                    Log.d(TAG,"FilterPeriodBegin = " + appParams.getFilterPeriodBeginString() +
                            ", FilterPeriodEnd = " + appParams.getFilterPeriodEndString() +
                            ", FilterReadStatus = " + appParams.getFilterReadStatus());
                    Log.d(TAG,"FilterRecipient = " + appParams.getFilterRecipient() +
                            ", FilterOriginator = " + appParams.getFilterOriginator());
                    Log.d(TAG,"FilterPriority = " + appParams.getFilterPriority());
                    long tmpLong = appParams.getFilterMsgHandle();
                    Log.d(TAG,"FilterMsgHandle = " + (
                            (tmpLong == BluetoothMapAppParams.INVALID_VALUE_PARAMETER) ? "" :
                                Long.toHexString(tmpLong)));
                    SignedLongLong tmpLongLong = appParams.getFilterConvoId();
                    Log.d(TAG,"FilterConvoId = " + ((tmpLongLong == null) ? "" :
                        Long.toHexString(tmpLongLong.getLeastSignificantBits()) ) );
                }
                if (!isUserUnlocked()) {
                    Log.e(TAG, "Storage locked, " +  type + " failed");
                    return ResponseCodes.OBEX_HTTP_UNAVAILABLE;
                }
                // Block until all packets have been send.
                return sendMessageListingRsp(op, appParams, name);

            } else if (type.equals(TYPE_GET_CONVO_LISTING)){
                name = (String)request.getHeader(HeaderSet.NAME);
                if (V && appParams != null) {
                    Log.d(TAG,"TYPE_GET_CONVO_LISTING: name is" + name +
                            ", MaxListCount = " + appParams.getMaxListCount() +
                            ", ListStartOffset = " + appParams.getStartOffset());
                    Log.d(TAG,"FilterLastActivityBegin = "+appParams.getFilterLastActivityBegin());
                    Log.d(TAG,"FilterLastActivityEnd = " + appParams.getFilterLastActivityEnd());
                    Log.d(TAG,"FilterReadStatus = " + appParams.getFilterReadStatus());
                    Log.d(TAG,"FilterRecipient = " + appParams.getFilterRecipient());
                }
                if (!isUserUnlocked()) {
                    Log.e(TAG, "Storage locked, " + type + " failed");
                    return ResponseCodes.OBEX_HTTP_UNAVAILABLE;
                }
                // Block until all packets have been send.
                return sendConvoListingRsp(op, appParams,name);
            } else if (type.equals(TYPE_GET_MAS_INSTANCE_INFORMATION)) {
                if(V && appParams != null) {
                    Log.d(TAG,"TYPE_MESSAGE (GET): MASInstandeId = "
                            + appParams.getMasInstanceId());
                }
                // Block until all packets have been send.
                return sendMASInstanceInformationRsp(op, appParams);
            } else if (type.equals(TYPE_MESSAGE)){
                name = (String)request.getHeader(HeaderSet.NAME);
                if(V && appParams != null) {
                    Log.d(TAG,"TYPE_MESSAGE (GET): name is" + name +
                            ", Attachment = " + appParams.getAttachment() +
                            ", Charset = " + appParams.getCharset() +
                            ", FractionRequest = " + appParams.getFractionRequest());
                }
                if (!isUserUnlocked()) {
                    Log.e(TAG, "Storage locked, " + type + " failed");
                    return ResponseCodes.OBEX_HTTP_UNAVAILABLE;
                }
                // Block until all packets have been send.
                return sendGetMessageRsp(op, name, appParams, mMessageVersion);
            } else {
                Log.w(TAG, "unknown type request: " + type);
                return ResponseCodes.OBEX_HTTP_NOT_ACCEPTABLE;
            }

        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Exception:", e);
            return ResponseCodes.OBEX_HTTP_PRECON_FAILED;
        } catch (ParseException e) {
            Log.e(TAG, "Exception:", e);
            return ResponseCodes.OBEX_HTTP_PRECON_FAILED;
        } catch (Exception e) {
            if(D) {
                Log.e(TAG, "Exception occured while handling request",e);
            } else {
                Log.e(TAG, "Exception occured while handling request");
            }
            if(mIsAborted == true) {
                if(D) Log.d(TAG, "onGet Operation Aborted");
                return ResponseCodes.OBEX_HTTP_OK;
            } else {
                return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
            }
        }
    }

    /**
     * Generate and send the message listing response based on an application
     * parameter header. This function call will block until complete or aborted
     * by the peer. Fragmentation of packets larger than the obex packet size
     * will be handled by this function.
     *
     * @param op
     *            The OBEX operation.
     * @param appParams
     *            The application parameter header
     * @return {@link ResponseCodes.OBEX_HTTP_OK} on success or
     *         {@link ResponseCodes.OBEX_HTTP_BAD_REQUEST} on error.
     */
    private int sendMessageListingRsp(Operation op,
                                      BluetoothMapAppParams appParams,
                                      String folderName){
        OutputStream outStream = null;
        byte[] outBytes = null;
        int maxChunkSize, bytesToWrite, bytesWritten = 0, listSize;
        boolean hasUnread = false;
        HeaderSet replyHeaders = new HeaderSet();
        BluetoothMapAppParams outAppParams = new BluetoothMapAppParams();
        BluetoothMapMessageListing outList;
        if(appParams == null){
            appParams = new BluetoothMapAppParams();
            appParams.setMaxListCount(1024);
            appParams.setStartOffset(0);
        }

        /* MAP Spec 1.3 introduces the following
         * Messagehandle filtering:
         * msgListing (messageHandle=X) -> other allowed filters: parametereMask, subjectMaxLength
         * ConversationID filtering:
         * msgListing (convoId empty) -> should work as normal msgListing in valid folders
         * msgListing (convoId=0, no other filters) -> should return all messages in all folders
         * msgListing (convoId=N, other filters) -> should return all messages in conversationID=N
         *                                          according to filters requested
         */
        BluetoothMapFolderElement folderToList = null;
        if (appParams.getFilterMsgHandle() != BluetoothMapAppParams.INVALID_VALUE_PARAMETER
            || appParams.getFilterConvoId() != null) {
            // If messageHandle or convoId filtering ignore folder
            Log.v(TAG,"sendMessageListingRsp: ignore folder ");
            folderToList = mCurrentFolder.getRoot();
            folderToList.setIngore(true);
        } else {
            folderToList = getFolderElementFromName(folderName);
            if(folderToList == null) {
                Log.w(TAG,"sendMessageListingRsp: folderToList == "+
                        "null-sending OBEX_HTTP_BAD_REQUEST");
                return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
            }
            Log.v(TAG,"sendMessageListingRsp: has sms " + folderToList.hasSmsMmsContent() +
                    ", has email " + folderToList.hasEmailContent() +
                    ", has IM " + folderToList.hasImContent() );
        }

        try {
            // Open the OBEX body stream
            outStream = op.openOutputStream();

            if(appParams.getMaxListCount() == BluetoothMapAppParams.INVALID_VALUE_PARAMETER)
                appParams.setMaxListCount(1024);

            if(appParams.getStartOffset() == BluetoothMapAppParams.INVALID_VALUE_PARAMETER)
                appParams.setStartOffset(0);

            // Check to see if we only need to send the size - hence no need to encode.
            if(appParams.getMaxListCount() != 0) {
                outList = mOutContent.msgListing(folderToList, appParams);
                // Generate the byte stream
                outAppParams.setMessageListingSize(outList.getCount());
                String version;
                if(0 < (mRemoteFeatureMask &
                        BluetoothMapUtils.MAP_FEATURE_MESSAGE_LISTING_FORMAT_V11_BIT)) {
                    version = BluetoothMapUtils.MAP_V11_STR;
                } else {
                    version = BluetoothMapUtils.MAP_V10_STR;
                }
                /* This will only set the version, the bit must also be checked before adding any
                 * 1.1 bits to the listing. */
                outBytes = outList.encode(mThreadIdSupport, version);
                hasUnread = outList.hasUnread();
            } else {
                listSize = mOutContent.msgListingSize(folderToList, appParams);
                hasUnread = mOutContent.msgListingHasUnread(folderToList, appParams);
                outAppParams.setMessageListingSize(listSize);
                op.noBodyHeader();
            }
            folderToList.setIngore(false);
            // Build the application parameter header
            // let the peer know if there are unread messages in the list
            if(hasUnread) {
                outAppParams.setNewMessage(1);
            }else{
                outAppParams.setNewMessage(0);
            }
            if ((mRemoteFeatureMask & BluetoothMapUtils.MAP_FEATURE_DATABASE_INDENTIFIER_BIT)
                    == BluetoothMapUtils.MAP_FEATURE_DATABASE_INDENTIFIER_BIT ) {
                outAppParams.setDatabaseIdentifier(0, mMasInstance.getDbIdentifier());
            }
            if((mRemoteFeatureMask & BluetoothMapUtils.MAP_FEATURE_FOLDER_VERSION_COUNTER_BIT)
                    == BluetoothMapUtils.MAP_FEATURE_FOLDER_VERSION_COUNTER_BIT) {
                // Force update of version counter if needed
                mObserver.refreshFolderVersionCounter();
                outAppParams.setFolderVerCounter(mMasInstance.getFolderVersionCounter(), 0);
            }
            outAppParams.setMseTime(Calendar.getInstance().getTime().getTime());
            replyHeaders.setHeader(HeaderSet.APPLICATION_PARAMETER, outAppParams.EncodeParams());
            op.sendHeaders(replyHeaders);

        } catch (IOException e) {
            Log.w(TAG,"sendMessageListingRsp: IOException - sending OBEX_HTTP_BAD_REQUEST", e);
            if(outStream != null) { try { outStream.close(); } catch (IOException ex) {} }
            if(mIsAborted == true) {
                if(D) Log.d(TAG, "sendMessageListingRsp Operation Aborted");
                return ResponseCodes.OBEX_HTTP_OK;
            } else {
                return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
            }
        } catch (IllegalArgumentException e) {
            Log.w(TAG,"sendMessageListingRsp: IllegalArgumentException"+
                                            " - sending OBEX_HTTP_BAD_REQUEST", e);
            if(outStream != null) { try { outStream.close(); } catch (IOException ex) {} }
            return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        }

        maxChunkSize = op.getMaxPacketSize(); // This must be called after setting the headers.
        if(outBytes != null) {
            try {
                while (bytesWritten < outBytes.length && mIsAborted == false) {
                    bytesToWrite = Math.min(maxChunkSize, outBytes.length - bytesWritten);
                    outStream.write(outBytes, bytesWritten, bytesToWrite);
                    bytesWritten += bytesToWrite;
                }
            } catch (IOException e) {
                if(D) Log.w(TAG,e);
                // We were probably aborted or disconnected
            } finally {
                if(outStream != null) { try { outStream.close(); } catch (IOException e) {} }
            }
            if(bytesWritten != outBytes.length && !mIsAborted) {
                Log.w(TAG,"sendMessageListingRsp: bytesWritten != outBytes.length" +
                        " - sending OBEX_HTTP_BAD_REQUEST");
                return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
            }
        } else {
            if(outStream != null) { try { outStream.close(); } catch (IOException e) {} }
        }
        return ResponseCodes.OBEX_HTTP_OK;
    }

    /**
     * Update the {@link BluetoothMapAppParams} object message type filter mask to only contain
     * message types supported by this mas instance.
     * Could the folder be used in stead?
     * @param appParams Reference to the object to update
     * @param overwrite True: The msgType will be overwritten to match the message types supported
     * by this MAS instance. False: any unsupported message types will be masked out.
     */
    private void setMsgTypeFilterParams(BluetoothMapAppParams appParams, boolean overwrite) {
        int masFilterMask = 0;
        if(!mEnableSmsMms) {
            masFilterMask |= BluetoothMapAppParams.FILTER_NO_SMS_CDMA;
            masFilterMask |= BluetoothMapAppParams.FILTER_NO_SMS_GSM;
            masFilterMask |= BluetoothMapAppParams.FILTER_NO_MMS;
        }
        if(mAccount==null){
            masFilterMask |= BluetoothMapAppParams.FILTER_NO_EMAIL;
            masFilterMask |= BluetoothMapAppParams.FILTER_NO_IM;
        } else {
            if(!(mAccount.getType() == BluetoothMapUtils.TYPE.EMAIL)) {
                masFilterMask |= BluetoothMapAppParams.FILTER_NO_EMAIL;
            }
            if(!(mAccount.getType() == BluetoothMapUtils.TYPE.IM)) {
                masFilterMask |= BluetoothMapAppParams.FILTER_NO_IM;
            }
        }
        if(overwrite) {
            appParams.setFilterMessageType(masFilterMask);
        } else {
            int newMask = appParams.getFilterMessageType();
            if(newMask == BluetoothMapAppParams.INVALID_VALUE_PARAMETER) {
                appParams.setFilterMessageType(newMask);
            } else {
                newMask |= masFilterMask;
                appParams.setFilterMessageType(newMask);
            }
        }
    }

    /**
     * Generate and send the Conversation listing response based on an application
     * parameter header. This function call will block until complete or aborted
     * by the peer. Fragmentation of packets larger than the obex packet size
     * will be handled by this function.
     *
     * @param op
     *            The OBEX operation.
     * @param appParams
     *            The application parameter header
     * @return {@link ResponseCodes.OBEX_HTTP_OK} on success or
     *         {@link ResponseCodes.OBEX_HTTP_BAD_REQUEST} on error.
     */
    private int sendConvoListingRsp(Operation op,
                                    BluetoothMapAppParams appParams,
                                    String folderName){
        OutputStream outStream = null;
        byte[] outBytes = null;
        int maxChunkSize, bytesToWrite, bytesWritten = 0;
        //boolean hasUnread = false;
        HeaderSet replyHeaders = new HeaderSet();
        BluetoothMapAppParams outAppParams = new BluetoothMapAppParams();
        BluetoothMapConvoListing outList;
        if(appParams == null){
            appParams = new BluetoothMapAppParams();
            appParams.setMaxListCount(1024);
            appParams.setStartOffset(0);
        }
        // As the app parameters do not carry which message types to list, we set the filter here
        // to all message types supported by this instance.
        setMsgTypeFilterParams(appParams, true);

        // Check to see if we only need to send the size - hence no need to encode.
        try {
            // Open the OBEX body stream
            outStream = op.openOutputStream();

            if(appParams.getMaxListCount() == BluetoothMapAppParams.INVALID_VALUE_PARAMETER)
                appParams.setMaxListCount(1024);

            if(appParams.getStartOffset() == BluetoothMapAppParams.INVALID_VALUE_PARAMETER)
                appParams.setStartOffset(0);

            if(appParams.getMaxListCount() != 0) {
                outList = mOutContent.convoListing(appParams, false);
                outAppParams.setConvoListingSize(outList.getCount());
                // Generate the byte stream
                outBytes = outList.encode(); // Include thread ID for clients that supports it.
                if(D) Log.d(TAG, "outBytes size:"+ outBytes.length);
            } else {
                outList = mOutContent.convoListing(appParams, true);
                outAppParams.setConvoListingSize(outList.getCount());
                if(mEnableSmsMms) {
                    mOutContent.refreshSmsMmsConvoVersions();
                }
                if(mAccount != null) {
                    mOutContent.refreshImEmailConvoVersions();
                }
                // Force update of version counter if needed
                mObserver.refreshConvoListVersionCounter();
                if(0 < (mRemoteFeatureMask &
                        BluetoothMapUtils.MAP_FEATURE_CONVERSATION_VERSION_COUNTER_BIT)) {
                    outAppParams.setConvoListingVerCounter(
                            mMasInstance.getCombinedConvoListVersionCounter(), 0);
                }
                op.noBodyHeader();
            }
            if(D) Log.d(TAG, "outList size:"+ outList.getCount()
                    + " MaxListCount: "+appParams.getMaxListCount());
            outList = null; // We don't need it anymore - we might as well give it up for GC
            outAppParams.setDatabaseIdentifier(0, mMasInstance.getDbIdentifier());

            // Build the application parameter header
            // The MseTime is not in the CR - but I think it is missing.
            outAppParams.setMseTime(Calendar.getInstance().getTime().getTime());
            replyHeaders.setHeader(HeaderSet.APPLICATION_PARAMETER, outAppParams.EncodeParams());
            op.sendHeaders(replyHeaders);

        } catch (IOException e) {
            Log.w(TAG,"sendConvoListingRsp: IOException - sending OBEX_HTTP_BAD_REQUEST", e);
            if(outStream != null) { try { outStream.close(); } catch (IOException ex) {} }
            if(mIsAborted == true) {
                if(D) Log.d(TAG, "sendConvoListingRsp Operation Aborted");
                return ResponseCodes.OBEX_HTTP_OK;
            } else {
                return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
            }
        } catch (IllegalArgumentException e) {
            Log.w(TAG,"sendConvoListingRsp: IllegalArgumentException" +
                    " - sending OBEX_HTTP_BAD_REQUEST", e);
            if(outStream != null) { try { outStream.close(); } catch (IOException ex) {} }
            return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        }

        maxChunkSize = op.getMaxPacketSize(); // This must be called after setting the headers.
        if(outBytes != null) {
            try {
                while (bytesWritten < outBytes.length && mIsAborted == false) {
                    bytesToWrite = Math.min(maxChunkSize, outBytes.length - bytesWritten);
                    outStream.write(outBytes, bytesWritten, bytesToWrite);
                    bytesWritten += bytesToWrite;
                }
            } catch (IOException e) {
                if(D) Log.w(TAG,e);
                // We were probably aborted or disconnected
            } finally {
                if(outStream != null) { try { outStream.close(); } catch (IOException e) {} }
            }
            if(bytesWritten != outBytes.length && !mIsAborted) {
                Log.w(TAG,"sendConvoListingRsp: bytesWritten != outBytes.length" +
                        " - sending OBEX_HTTP_BAD_REQUEST");
                return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
            }
        } else {
            if(outStream != null) { try { outStream.close(); } catch (IOException e) {} }
        }
        return ResponseCodes.OBEX_HTTP_OK;
    }

    /**
     * Generate and send the Folder listing response based on an application
     * parameter header. This function call will block until complete or aborted
     * by the peer. Fragmentation of packets larger than the obex packet size
     * will be handled by this function.
     *
     * @param op
     *            The OBEX operation.
     * @param appParams
     *            The application parameter header
     * @return {@link ResponseCodes.OBEX_HTTP_OK} on success or
     *         {@link ResponseCodes.OBEX_HTTP_BAD_REQUEST} on error.
     */
    private int sendFolderListingRsp(Operation op, BluetoothMapAppParams appParams){
        OutputStream outStream = null;
        byte[] outBytes = null;
        BluetoothMapAppParams outAppParams = new BluetoothMapAppParams();
        int maxChunkSize, bytesWritten = 0;
        HeaderSet replyHeaders = new HeaderSet();
        int bytesToWrite, maxListCount, listStartOffset;
        if(appParams == null){
            appParams = new BluetoothMapAppParams();
            appParams.setMaxListCount(1024);
        }

        if(V)
            Log.v(TAG,"sendFolderList for " + mCurrentFolder.getName());

        try {
            maxListCount = appParams.getMaxListCount();
            listStartOffset = appParams.getStartOffset();

            if(listStartOffset == BluetoothMapAppParams.INVALID_VALUE_PARAMETER)
                listStartOffset = 0;

            if(maxListCount == BluetoothMapAppParams.INVALID_VALUE_PARAMETER)
                maxListCount = 1024;

            if(maxListCount != 0)
            {
                outBytes = mCurrentFolder.encode(listStartOffset, maxListCount);
                outStream = op.openOutputStream();
            } else {
                // ESR08 specified that this shall only be included for MaxListCount=0
                outAppParams.setFolderListingSize(mCurrentFolder.getSubFolderCount());
                op.noBodyHeader();
            }

            // Build and set the application parameter header
            replyHeaders.setHeader(HeaderSet.APPLICATION_PARAMETER, outAppParams.EncodeParams());
            op.sendHeaders(replyHeaders);

        } catch (IOException e1) {
            Log.w(TAG,"sendFolderListingRsp: IOException" +
                    " - sending OBEX_HTTP_BAD_REQUEST Exception:", e1);
            if(outStream != null) { try { outStream.close(); } catch (IOException e) {} }
            if(mIsAborted == true) {
                if(D) Log.d(TAG, "sendFolderListingRsp Operation Aborted");
                return ResponseCodes.OBEX_HTTP_OK;
            } else {
                return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
            }
        } catch (IllegalArgumentException e1) {
            Log.w(TAG,"sendFolderListingRsp: IllegalArgumentException" +
                    " - sending OBEX_HTTP_BAD_REQUEST Exception:", e1);
            if(outStream != null) { try { outStream.close(); } catch (IOException e) {} }
            return ResponseCodes.OBEX_HTTP_PRECON_FAILED;
        }

        maxChunkSize = op.getMaxPacketSize(); // This must be called after setting the headers.

        if(outBytes != null) {
            try {
                while (bytesWritten < outBytes.length && mIsAborted == false) {
                    bytesToWrite = Math.min(maxChunkSize, outBytes.length - bytesWritten);
                    outStream.write(outBytes, bytesWritten, bytesToWrite);
                    bytesWritten += bytesToWrite;
                }
            } catch (IOException e) {
                // We were probably aborted or disconnected
            } finally {
                if(outStream != null) { try { outStream.close(); } catch (IOException e) {} }
            }
            if(V)
                Log.v(TAG,"sendFolderList sent " + bytesWritten+" bytes out of "+ outBytes.length);
            if(bytesWritten == outBytes.length || mIsAborted)
                return ResponseCodes.OBEX_HTTP_OK;
            else
                return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        }

        return ResponseCodes.OBEX_HTTP_OK;
    }

    /**
     * Generate and send the get MAS Instance Information response based on an MAS Instance
     *
     * @param op
     *            The OBEX operation.
     * @param appParams
     *            The application parameter header
     * @return {@link ResponseCodes.OBEX_HTTP_OK} on success or
     *         {@link ResponseCodes.OBEX_HTTP_BAD_REQUEST} on error.
     */
    private int sendMASInstanceInformationRsp(Operation op, BluetoothMapAppParams appParams){

        OutputStream outStream = null;
        byte[] outBytes = null;
        String outString = null;
        int maxChunkSize, bytesToWrite, bytesWritten = 0;

        try {
            if(mMasId == appParams.getMasInstanceId()) {
                if(mAccount != null) {
                    if(mAccount.getType() == TYPE.EMAIL) {
                        outString = (mAccount.getName() != null) ? mAccount.getName() :
                            BluetoothMapMasInstance.TYPE_EMAIL_STR;
                    } else if(mAccount.getType() == TYPE.IM){
                        outString = mAccount.getUciFull();
                        if(outString == null) {
                            String uci = mAccount.getUci();
                            // TODO: Do we need to align this with HF/PBAP
                            StringBuilder sb =
                                    new StringBuilder(uci == null ? 5 : 5 + uci.length());
                            sb.append("un");
                            if(mMasId < 10) {
                                sb.append("00");
                            } else if(mMasId < 100) {
                                sb.append("0");
                            }
                            sb.append(mMasId);
                            if(uci != null) {
                                sb.append(":").append(uci);
                            }
                            outString = sb.toString();
                        }
                    }
                } else {
                    outString = BluetoothMapMasInstance.TYPE_SMS_MMS_STR;
                    // TODO: Add phone number if possible
                }
            } else {
                return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
            }

            /* Ensure byte array max length is 200 containing valid UTF-8 characters */
            outBytes = BluetoothMapUtils.truncateUtf8StringToBytearray(outString,
                    MAS_INSTANCE_INFORMATION_LENGTH);

            // Open the OBEX body stream
            outStream = op.openOutputStream();

        } catch (IOException e) {
            Log.w(TAG,"sendMASInstanceInformationRsp: IOException" +
                    " - sending OBEX_HTTP_BAD_REQUEST", e);
            if(mIsAborted == true) {
                if(D) Log.d(TAG, "sendMASInstanceInformationRsp Operation Aborted");
                return ResponseCodes.OBEX_HTTP_OK;
            } else {
                return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
            }
        }

        maxChunkSize = op.getMaxPacketSize(); // This must be called after setting the headers.

        if(outBytes != null) {
            try {
                while (bytesWritten < outBytes.length && mIsAborted == false) {
                    bytesToWrite = Math.min(maxChunkSize, outBytes.length - bytesWritten);
                    outStream.write(outBytes, bytesWritten, bytesToWrite);
                    bytesWritten += bytesToWrite;
                }
            } catch (IOException e) {
                // We were probably aborted or disconnected
            } finally {
                if(outStream != null) { try { outStream.close(); } catch (IOException e) {} }
            }
            if(V)
                Log.v(TAG,"sendMASInstanceInformationRsp sent " + bytesWritten +
                        " bytes out of "+ outBytes.length);
            if(bytesWritten == outBytes.length || mIsAborted)
                return ResponseCodes.OBEX_HTTP_OK;
            else
                return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        }
        return ResponseCodes.OBEX_HTTP_OK;
    }

    /**
     * Generate and send the get message response based on an application
     * parameter header and a handle.
     *
     * @param op
     *            The OBEX operation.
     * @param handle
     *            The handle of the requested message
     * @param appParams
     *            The application parameter header
     * @param version
     *              The string representation of the version number(i.e. "1.0")
     * @return {@link ResponseCodes.OBEX_HTTP_OK} on success or
     *         {@link ResponseCodes.OBEX_HTTP_BAD_REQUEST} on error.
     */
    private int sendGetMessageRsp(Operation op, String handle,
            BluetoothMapAppParams appParams, String version){
        OutputStream outStream = null;
        byte[] outBytes = null;
        int maxChunkSize, bytesToWrite, bytesWritten = 0;

        try {
            outBytes = mOutContent.getMessage(handle, appParams, mCurrentFolder, version);
            outStream = op.openOutputStream();

            // If it is a fraction request of Email message, set header before responding
            if ((BluetoothMapUtils.getMsgTypeFromHandle(handle).equals(TYPE.EMAIL)||
                    (BluetoothMapUtils.getMsgTypeFromHandle(handle).equals(TYPE.IM))) &&
                    (appParams.getFractionRequest() ==
                    BluetoothMapAppParams.FRACTION_REQUEST_FIRST)) {
                BluetoothMapAppParams outAppParams  = new BluetoothMapAppParams();
                HeaderSet replyHeaders = new HeaderSet();
                outAppParams.setFractionDeliver(BluetoothMapAppParams.FRACTION_DELIVER_LAST);
                // Build and set the application parameter header
                replyHeaders.setHeader(HeaderSet.APPLICATION_PARAMETER,
                        outAppParams.EncodeParams());
                op.sendHeaders(replyHeaders);
                if(V) Log.v(TAG,"sendGetMessageRsp fractionRequest - " +
                        "set FRACTION_DELIVER_LAST header");
            }

        } catch (IOException e) {
            Log.w(TAG,"sendGetMessageRsp: IOException - sending OBEX_HTTP_BAD_REQUEST", e);
            if(outStream != null) { try { outStream.close(); } catch (IOException ex) {} }
            if(mIsAborted == true) {
                if(D) Log.d(TAG, "sendGetMessageRsp Operation Aborted");
                return ResponseCodes.OBEX_HTTP_OK;
            } else {
                return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
            }
        } catch (IllegalArgumentException e) {
            Log.w(TAG,"sendGetMessageRsp: IllegalArgumentException (e.g. invalid handle) - " +
                    "sending OBEX_HTTP_BAD_REQUEST", e);
            if(outStream != null) { try { outStream.close(); } catch (IOException ex) {} }
            return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        }

        maxChunkSize = op.getMaxPacketSize(); // This must be called after setting the headers.

        if(outBytes != null) {
            try {
                while (bytesWritten < outBytes.length && mIsAborted == false) {
                    bytesToWrite = Math.min(maxChunkSize, outBytes.length - bytesWritten);
                    outStream.write(outBytes, bytesWritten, bytesToWrite);
                    bytesWritten += bytesToWrite;
                }
            } catch (IOException e) {
                // We were probably aborted or disconnected
                if(D && e.getMessage().equals("Abort Received")) {
                    Log.w(TAG, "getMessage() Aborted...", e);
                }
            } finally {
                if(outStream != null) { try { outStream.close(); } catch (IOException e) {} }
            }
            if(bytesWritten == outBytes.length || mIsAborted)
                return ResponseCodes.OBEX_HTTP_OK;
            else
                return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        }

        return ResponseCodes.OBEX_HTTP_OK;
    }

    @Override
    public int onDelete(HeaderSet request, HeaderSet reply) {
        if(D) Log.v(TAG, "onDelete() " + request.toString());
        mIsAborted = false;
        notifyUpdateWakeLock();
        String type, name;
        byte[] appParamRaw;
        BluetoothMapAppParams appParams = null;

        /* TODO: If this is to be placed here, we need to cleanup - e.g. the exception handling */
        try {
            type = (String)request.getHeader(HeaderSet.TYPE);

            name = (String)request.getHeader(HeaderSet.NAME);
            appParamRaw = (byte[])request.getHeader(HeaderSet.APPLICATION_PARAMETER);
            if(appParamRaw != null)
                appParams = new BluetoothMapAppParams(appParamRaw);
            if(D) Log.d(TAG,"type = " + type + ", name = " + name);
            if(type.equals(TYPE_SET_NOTIFICATION_FILTER)) {
                if(V) {
                    Log.d(TAG,"TYPE_SET_NOTIFICATION_FILTER: NotificationFilter: "
                            + appParams.getNotificationFilter());
                }
                mObserver.setNotificationFilter(appParams.getNotificationFilter());
                return ResponseCodes.OBEX_HTTP_OK;
            }  else if (type.equals(TYPE_SET_OWNER_STATUS)) {
                if(V) {
                    Log.d(TAG,"TYPE_SET_OWNER_STATUS:" +
                          " PresenceAvailability " + appParams.getPresenceAvailability() +
                          ", PresenceStatus: " + appParams.getPresenceStatus() +
                          ", LastActivity: " + appParams.getLastActivityString() +
                          ", ChatStatus: " + appParams.getChatState() +
                          ", ChatStatusConvoId: " + appParams.getChatStateConvoIdString());
                }
                return setOwnerStatus(name, appParams);
            }

        } catch (RemoteException e){
            //reload the providerClient and return error
            try {
                mProviderClient = acquireUnstableContentProviderOrThrow();
            }catch (RemoteException e2){
                //should not happen
            }
            return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        }catch (Exception e) {

            if(D) {
                Log.e(TAG, "Exception occured while handling request",e);
            } else {
                Log.e(TAG, "Exception occured while handling request");
            }
            if(mIsAborted) {
                return ResponseCodes.OBEX_HTTP_OK;
            } else {
                return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
            }
        }
        return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
    }

    private void notifyUpdateWakeLock() {
        if(mCallback != null) {
            Message msg = Message.obtain(mCallback);
            msg.what = BluetoothMapService.MSG_ACQUIRE_WAKE_LOCK;
            msg.sendToTarget();
        }
    }

    private static final void logHeader(HeaderSet hs) {
        Log.v(TAG, "Dumping HeaderSet " + hs.toString());
        try {
            Log.v(TAG, "CONNECTION_ID : " + hs.getHeader(HeaderSet.CONNECTION_ID));
            Log.v(TAG, "NAME : " + hs.getHeader(HeaderSet.NAME));
            Log.v(TAG, "TYPE : " + hs.getHeader(HeaderSet.TYPE));
            Log.v(TAG, "TARGET : " + hs.getHeader(HeaderSet.TARGET));
            Log.v(TAG, "WHO : " + hs.getHeader(HeaderSet.WHO));
            Log.v(TAG, "APPLICATION_PARAMETER : " + hs.getHeader(HeaderSet.APPLICATION_PARAMETER));
        } catch (IOException e) {
            Log.e(TAG, "dump HeaderSet error " + e);
        }
        Log.v(TAG, "NEW!!! Dumping HeaderSet END");
    }
}
