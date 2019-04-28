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

package com.android.bluetooth.mapapi;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.util.Log;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * A base implementation of the BluetoothMapContract.
 * A base class for a ContentProvider that allows access to Instant messages from a Bluetooth
 * device through the Message Access Profile.
 */
public abstract class BluetoothMapIMProvider extends ContentProvider {

    private static final String TAG = "BluetoothMapIMProvider";
    private static final boolean D = true;

    private static final int MATCH_ACCOUNT = 1;
    private static final int MATCH_MESSAGE = 3;
    private static final int MATCH_CONVERSATION = 4;
    private static final int MATCH_CONVOCONTACT = 5;

    protected ContentResolver mResolver;

    private Uri CONTENT_URI = null;
    private String mAuthority;
    private UriMatcher mMatcher;

    /**
     * @return the CONTENT_URI exposed. This will be used to send out notifications.
     */
    abstract protected Uri getContentUri();

    /**
     * Implementation is provided by the parent class.
     */
    @Override
    public void attachInfo(Context context, ProviderInfo info) {
       mAuthority = info.authority;

       mMatcher = new UriMatcher(UriMatcher.NO_MATCH);
       mMatcher.addURI(mAuthority, BluetoothMapContract.TABLE_ACCOUNT, MATCH_ACCOUNT);
       mMatcher.addURI(mAuthority, "#/"+ BluetoothMapContract.TABLE_MESSAGE, MATCH_MESSAGE);
       mMatcher.addURI(mAuthority, "#/"+ BluetoothMapContract.TABLE_CONVERSATION,
               MATCH_CONVERSATION);
       mMatcher.addURI(mAuthority, "#/"+ BluetoothMapContract.TABLE_CONVOCONTACT,
               MATCH_CONVOCONTACT);

       // Sanity check our setup
       if (!info.exported) {
           throw new SecurityException("Provider must be exported");
       }
       // Enforce correct permissions are used
       if (!android.Manifest.permission.BLUETOOTH_MAP.equals(info.writePermission)){
           throw new SecurityException("Provider must be protected by " +
                   android.Manifest.permission.BLUETOOTH_MAP);
       }
       if(D) Log.d(TAG,"attachInfo() mAuthority = " + mAuthority);

       mResolver = context.getContentResolver();
       super.attachInfo(context, info);
   }

    /**
     * This function shall be called when any Account database content have changed
     * to Notify any attached observers.
     * @param accountId the ID of the account that changed. Null is a valid value,
     *        if accountId is unknown or multiple accounts changed.
     */
    protected void onAccountChanged(String accountId) {
        Uri newUri = null;

        if(mAuthority == null){
            return;
        }
        if(accountId == null){
            newUri = BluetoothMapContract.buildAccountUri(mAuthority);
        } else {
            newUri = BluetoothMapContract.buildAccountUriwithId(mAuthority, accountId);
        }

        if(D) Log.d(TAG,"onAccountChanged() accountId = " + accountId + " URI: " + newUri);
        mResolver.notifyChange(newUri, null);
    }

    /**
     * This function shall be called when any Message database content have changed
     * to notify any attached observers.
     * @param accountId Null is a valid value, if accountId is unknown, but
     *        recommended for increased performance.
     * @param messageId Null is a valid value, if multiple messages changed or the
     *        messageId is unknown, but recommended for increased performance.
     */
    protected void onMessageChanged(String accountId, String messageId) {
        Uri newUri = null;

        if(mAuthority == null){
            return;
        }
        if(accountId == null){
            newUri = BluetoothMapContract.buildMessageUri(mAuthority);
        } else {
            if(messageId == null)
            {
                newUri = BluetoothMapContract.buildMessageUri(mAuthority,accountId);
            } else {
                newUri = BluetoothMapContract.buildMessageUriWithId(mAuthority,accountId,
                        messageId);
            }
        }
        if(D) Log.d(TAG,"onMessageChanged() accountId = " + accountId
                + " messageId = " + messageId + " URI: " + newUri);
        mResolver.notifyChange(newUri, null);
    }


    /**
     * This function shall be called when any Message database content have changed
     * to notify any attached observers.
     * @param accountId Null is a valid value, if accountId is unknown, but
     *        recommended for increased performance.
     * @param contactId Null is a valid value, if multiple contacts changed or the
     *        contactId is unknown, but recommended for increased performance.
     */
    protected void onContactChanged(String accountId, String contactId) {
        Uri newUri = null;

        if(mAuthority == null){
            return;
        }
        if(accountId == null){
            newUri = BluetoothMapContract.buildConvoContactsUri(mAuthority);
        } else {
            if(contactId == null)
            {
                newUri = BluetoothMapContract.buildConvoContactsUri(mAuthority,accountId);
            } else {
                newUri = BluetoothMapContract.buildConvoContactsUriWithId(mAuthority, accountId,
                        contactId);
            }
        }
        if(D) Log.d(TAG,"onContactChanged() accountId = " + accountId
                + " contactId = " + contactId + " URI: " + newUri);
        mResolver.notifyChange(newUri, null);
    }

    /**
     * Not used, this is just a dummy implementation.
     * TODO: We might need to something intelligent here after introducing IM
     */
    @Override
    public String getType(Uri uri) {
        return "InstantMessage";
    }

    /**
     * The MAP specification states that a delete request from MAP client is a folder shift to the
     * 'deleted' folder.
     * Only use case of delete() is when transparency is requested for push messages, then
     * message should not remain in sent folder and therefore must be deleted
     */
    @Override
    public int delete(Uri uri, String where, String[] selectionArgs) {
        if (D) Log.d(TAG, "delete(): uri=" + uri.toString() );
        int result = 0;

        String table = uri.getPathSegments().get(1);
        if(table == null)
            throw new IllegalArgumentException("Table missing in URI");
        // the id of the entry to be deleted from the database
        String messageId = uri.getLastPathSegment();
        if (messageId == null)
            throw new IllegalArgumentException("Message ID missing in update values!");

        String accountId = getAccountId(uri);
        if (accountId == null)
            throw new IllegalArgumentException("Account ID missing in update values!");

        long callingId = Binder.clearCallingIdentity();
        try {
            if(table.equals(BluetoothMapContract.TABLE_MESSAGE)) {
                return deleteMessage(accountId, messageId);
            } else {
                if (D) Log.w(TAG, "Unknown table name: " + table);
                return result;
            }
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    /**
     * This function deletes a message.
     * @param accountId the ID of the Account
     * @param messageId the ID of the message to delete.
     * @return the number of messages deleted - 0 if the message was not found.
     */
    abstract protected int deleteMessage(String accountId, String messageId);

    /**
     * Insert is used to add new messages to the data base.
     * Insert message approach:
     *   - Insert an empty message to get an _id with only a folder_id
     *   - Open the _id for write
     *   - Write the message content
     *     (When the writer completes, this provider should do an update of the message)
     */
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        String table = uri.getLastPathSegment();
        if(table == null)
            throw new IllegalArgumentException("Table missing in URI");

        String accountId = getAccountId(uri);
        if (accountId == null)
            throw new IllegalArgumentException("Account ID missing in URI");

        // TODO: validate values?

        String id; // the id of the entry inserted into the database
        long callingId = Binder.clearCallingIdentity();
        Log.d(TAG, "insert(): uri=" + uri.toString() + " - getLastPathSegment() = " +
                uri.getLastPathSegment());
        try {
            if(table.equals(BluetoothMapContract.TABLE_MESSAGE)) {
                id = insertMessage(accountId, values);
                if(D) Log.i(TAG, "insert() ID: " + id);
                return Uri.parse(uri.toString() + "/" + id);
            } else {
                Log.w(TAG, "Unknown table name: " + table);
                return null;
            }
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }


    /**
     * Inserts an empty message into the Message data base in the specified folder.
     * This is done before the actual message content is written by fileIO.
     * @param accountId the ID of the account
     * @param folderId the ID of the folder to create a new message in.
     * @return the message id as a string
     */
    abstract protected String insertMessage(String accountId, ContentValues values);

     /**
     * Utility function to build a projection based on a projectionMap.
     *
     *   "btColumnName" -> "imColumnName as btColumnName" for each entry.
     *
     * This supports SQL statements in the column name entry.
     * @param projection
     * @param projectionMap <string, string>
     * @return the converted projection
     */
    protected String[] convertProjection(String[] projection, Map<String,String> projectionMap) {
        String[] newProjection = new String[projection.length];
        for(int i = 0; i < projection.length; i++) {
            newProjection[i] = projectionMap.get(projection[i]) + " as " + projection[i];
        }
        return newProjection;
    }

    /**
     * This query needs to map from the data used in the e-mail client to
     * BluetoothMapContract type of data.
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        long callingId = Binder.clearCallingIdentity();
        try {
            String accountId = null;
            if(D)Log.w(TAG, "query(): uri =" + mAuthority + " uri=" + uri.toString());

            switch (mMatcher.match(uri)) {
                case MATCH_ACCOUNT:
                    return queryAccount(projection, selection, selectionArgs, sortOrder);
                case MATCH_MESSAGE:
                    // TODO: Extract account from URI
                    accountId = getAccountId(uri);
                    return queryMessage(accountId, projection, selection, selectionArgs, sortOrder);
                case MATCH_CONVERSATION:
                    accountId = getAccountId(uri);
                    String value;
                    String searchString =
                            uri.getQueryParameter(BluetoothMapContract.FILTER_ORIGINATOR_SUBSTRING);
                    Long periodBegin = null;
                    value = uri.getQueryParameter(BluetoothMapContract.FILTER_PERIOD_BEGIN);
                    if(value != null) {
                        periodBegin = Long.parseLong(value);
                    }
                    Long periodEnd = null;
                    value = uri.getQueryParameter(BluetoothMapContract.FILTER_PERIOD_END);
                    if(value != null) {
                        periodEnd = Long.parseLong(value);
                    }
                    Boolean read = null;
                    value = uri.getQueryParameter(BluetoothMapContract.FILTER_READ_STATUS);
                    if(value != null) {
                        read = value.equalsIgnoreCase("true");
                    }
                    Long threadId = null;
                    value = uri.getQueryParameter(BluetoothMapContract.FILTER_THREAD_ID);
                    if(value != null) {
                        threadId = Long.parseLong(value);
                    }
                    return queryConversation(accountId, threadId, read, periodEnd, periodBegin,
                            searchString, projection, sortOrder);
                case MATCH_CONVOCONTACT:
                    accountId = getAccountId(uri);
                    long contactId = 0;
                    return queryConvoContact(accountId, contactId, projection,
                            selection, selectionArgs, sortOrder);
                default:
                    throw new UnsupportedOperationException("Unsupported Uri " + uri);
            }
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    /**
     * Query account information.
     * This function shall return only exposable e-mail accounts. Hence shall not
     * return accounts that has policies suggesting not to be shared them.
     * @param projection
     * @param selection
     * @param selectionArgs
     * @param sortOrder
     * @return a cursor to the accounts that are subject to exposure over BT.
     */
    abstract protected Cursor queryAccount(String[] projection, String selection,
            String[] selectionArgs, String sortOrder);

    /**
     * For the message table the selection (where clause) can only include the following columns:
     *    date: less than, greater than and equals
     *    flagRead: = 1 or = 0
     *    flagPriority: = 1 or = 0
     *    folder_id: the ID of the folder only equals
     *    toList: partial name/address search
     *    fromList: partial name/address search
     * Additionally the COUNT and OFFSET shall be supported.
     * @param accountId the ID of the account
     * @param projection
     * @param selection
     * @param selectionArgs
     * @param sortOrder
     * @return a cursor to query result
     */
    abstract protected Cursor queryMessage(String accountId, String[] projection, String selection,
            String[] selectionArgs, String sortOrder);

    /**
     * For the Conversation table the selection (where clause) can only include
     * the following columns:
     *    _id: the ID of the conversation only equals
     *    name: partial name search
     *    last_activity: less than, greater than and equals
     *    version_counter: updated IDs are regenerated
     * Additionally the COUNT and OFFSET shall be supported.
     * @param accountId the ID of the account
     * @param threadId the ID of the conversation
     * @param projection
     * @param selection
     * @param selectionArgs
     * @param sortOrder
     * @return a cursor to query result
     */
//    abstract protected Cursor queryConversation(Long threadId, String[] projection,
//    String selection, String[] selectionArgs, String sortOrder);

    /**
     * Query for conversations with contact information. The expected result is a cursor pointing
     * to one row for each contact in a conversation.
     * E.g.:
     * ThreadId | ThreadName | ... | ContactName | ContactPrecence | ... |
     *        1 |  "Bowling" | ... |        Hans |               1 | ... |
     *        1 |  "Bowling" | ... |       Peter |               2 | ... |
     *        2 |         "" | ... |       Peter |               2 | ... |
     *        3 |         "" | ... |        Hans |               1 | ... |
     *
    * @param accountId the ID of the account
     * @param threadId filter on a single threadId - null if no filtering is needed.
     * @param read filter on a read status:
     *             null: no filtering on read is needed.
     *             true: return only threads that has NO unread messages.
     *             false: return only threads that has unread messages.
     * @param periodEnd   last_activity time stamp of the the newest thread to include in the
     *                    result.
     * @param periodBegin last_activity time stamp of the the oldest thread to include in the
     *                    result.
     * @param searchString if not null, include only threads that has contacts that matches the
     *                     searchString as part of the contact name or nickName.
     * @param projection A list of the columns that is needed in the result
     * @param sortOrder  the sort order
     * @return a Cursor representing the query result.
     */
    abstract protected Cursor queryConversation(String accountId, Long threadId, Boolean read,
            Long periodEnd, Long periodBegin, String searchString, String[] projection,
            String sortOrder);

    /**
     * For the ConvoContact table the selection (where clause) can only include the
     * following columns:
     *    _id: the ID of the contact only equals
     *    convo_id: id of conversation contact is part of
     *    name: partial name search
     *    x_bt_uid: the ID of the bt uid only equals
     *    chat_state: active, inactive, gone, composing, paused
     *    last_active: less than, greater than and equals
     *    presence_state: online, do_not_disturb, away, offline
     *    priority: level of priority 0 - 100
     *    last_online: less than, greater than and equals
     * @param accountId the ID of the account
     * @param contactId the ID of the contact
     * @param projection
     * @param selection
     * @param selectionArgs
     * @param sortOrder
     * @return a cursor to query result
     */
    abstract protected Cursor queryConvoContact(String accountId, Long contactId,
            String[] projection, String selection, String[] selectionArgs, String sortOrder);

    /**
     * update()
     * Messages can be modified in the following cases:
     *  - the folder_key of a message - hence the message can be moved to a new folder,
     *                                  but the content cannot be modified.
     *  - the FLAG_READ state can be changed.
     * Conversations can be modified in the following cases:
     *  - the read status - changing between read, unread
     *  - the last activity - the time stamp of last message sent of received in the conversation
     * ConvoContacts can be modified in the following cases:
     *  - the chat_state - chat status of the contact in conversation
     *  - the last_active - the time stamp of last action in the conversation
     *  - the presence_state - the time stamp of last time contact online
     *  - the status - the status text of the contact available in a conversation
     *  - the last_online - the time stamp of last time contact online
     * The selection statement will always be selection of a message ID, when updating a message,
     * hence this function will be called multiple times if multiple messages must be updated
     * due to the nature of the Bluetooth Message Access profile.
     */
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {

        String table = uri.getLastPathSegment();
        if(table == null){
            throw new IllegalArgumentException("Table missing in URI");
        }
        if(selection != null) {
            throw new IllegalArgumentException("selection shall not be used, ContentValues " +
                    "shall contain the data");
        }

        long callingId = Binder.clearCallingIdentity();
        if(D)Log.w(TAG, "update(): uri=" + uri.toString() + " - getLastPathSegment() = " +
                uri.getLastPathSegment());
        try {
            if(table.equals(BluetoothMapContract.TABLE_ACCOUNT)) {
                String accountId = values.getAsString(BluetoothMapContract.AccountColumns._ID);
                if(accountId == null) {
                    throw new IllegalArgumentException("Account ID missing in update values!");
                }
                Integer exposeFlag = values.getAsInteger(
                        BluetoothMapContract.AccountColumns.FLAG_EXPOSE);
                if(exposeFlag == null){
                    throw new IllegalArgumentException("Expose flag missing in update values!");
                }
                return updateAccount(accountId, exposeFlag);
            } else if(table.equals(BluetoothMapContract.TABLE_FOLDER)) {
                return 0; // We do not support changing folders
            } else if(table.equals(BluetoothMapContract.TABLE_MESSAGE)) {
                String accountId = getAccountId(uri);
                if(accountId == null) {
                    throw new IllegalArgumentException("Account ID missing in update values!");
                }
                Long messageId = values.getAsLong(BluetoothMapContract.MessageColumns._ID);
                if(messageId == null) {
                    throw new IllegalArgumentException("Message ID missing in update values!");
                }
                Long folderId = values.getAsLong(BluetoothMapContract.MessageColumns.FOLDER_ID);
                Boolean flagRead = values.getAsBoolean(
                        BluetoothMapContract.MessageColumns.FLAG_READ);
                return updateMessage(accountId, messageId, folderId, flagRead);
            } else if(table.equals(BluetoothMapContract.TABLE_CONVERSATION)) {
                return 0; // We do not support changing conversation
            } else if(table.equals(BluetoothMapContract.TABLE_CONVOCONTACT)) {
                return 0; // We do not support changing contacts
            } else {
                if(D)Log.w(TAG, "Unknown table name: " + table);
                return 0;
            }
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    /**
     * Update an entry in the account table. Only the expose flag will be
     * changed through this interface.
     * @param accountId the ID of the account to change.
     * @param flagExpose the updated value.
     * @return the number of entries changed - 0 if account not found or value cannot be changed.
     */
    abstract protected int updateAccount(String accountId, Integer flagExpose);

    /**
     * Update an entry in the message table.
     * @param accountId ID of the account to which the messageId relates
     * @param messageId the ID of the message to update
     * @param folderId the new folder ID value to set - ignore if null.
     * @param flagRead the new flagRead value to set - ignore if null.
     * @return
     */
    abstract protected int updateMessage(String accountId, Long messageId, Long folderId,
            Boolean flagRead);

    /**
     * Utility function to Creates a ContentValues object based on a modified valuesSet.
     * To be used after changing the keys and optionally values of a valueSet obtained
     * from a ContentValues object received in update().
     * @param valueSet the values as received in the contentProvider
     * @param keyMap the key map <btKey, emailKey>
     * @return a new ContentValues object with the keys replaced as specified in the
     * keyMap
     */
    protected ContentValues createContentValues(Set<Entry<String,Object>> valueSet,
            Map<String, String> keyMap) {
        ContentValues values = new ContentValues(valueSet.size());
        for(Entry<String,Object> ent : valueSet) {
            String key = keyMap.get(ent.getKey()); // Convert the key name
            Object value = ent.getValue();
            if(value == null) {
                values.putNull(key);
            } else if(ent.getValue() instanceof Boolean) {
                values.put(key, (Boolean) value);
            } else if(ent.getValue() instanceof Byte) {
                values.put(key, (Byte) value);
            } else if(ent.getValue() instanceof byte[]) {
                values.put(key, (byte[]) value);
            } else if(ent.getValue() instanceof Double) {
                values.put(key, (Double) value);
            } else if(ent.getValue() instanceof Float) {
                values.put(key, (Float) value);
            } else if(ent.getValue() instanceof Integer) {
                values.put(key, (Integer) value);
            } else if(ent.getValue() instanceof Long) {
                values.put(key, (Long) value);
            } else if(ent.getValue() instanceof Short) {
                values.put(key, (Short) value);
            } else if(ent.getValue() instanceof String) {
                values.put(key, (String) value);
            } else {
                throw new IllegalArgumentException("Unknown data type in content value");
            }
        }
        return values;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        long callingId = Binder.clearCallingIdentity();
        if(D)Log.w(TAG, "call(): method=" + method + " arg=" + arg + "ThreadId: "
                + Thread.currentThread().getId());
        int ret = -1;
        try {
            if(method.equals(BluetoothMapContract.METHOD_UPDATE_FOLDER)) {
                long accountId = extras.getLong(BluetoothMapContract.EXTRA_UPDATE_ACCOUNT_ID, -1);
                if(accountId == -1) {
                    Log.w(TAG, "No account ID in CALL");
                    return null;
                }
                long folderId = extras.getLong(BluetoothMapContract.EXTRA_UPDATE_FOLDER_ID, -1);
                if(folderId == -1) {
                    Log.w(TAG, "No folder ID in CALL");
                    return null;
                }
                ret = syncFolder(accountId, folderId);
            } else if (method.equals(BluetoothMapContract.METHOD_SET_OWNER_STATUS)) {
                int presenceState = extras.getInt(BluetoothMapContract.EXTRA_PRESENCE_STATE);
                String presenceStatus = extras.getString(
                        BluetoothMapContract.EXTRA_PRESENCE_STATUS);
                long lastActive = extras.getLong(BluetoothMapContract.EXTRA_LAST_ACTIVE);
                int chatState = extras.getInt(BluetoothMapContract.EXTRA_CHAT_STATE);
                String convoId = extras.getString(BluetoothMapContract.EXTRA_CONVERSATION_ID);
                ret = setOwnerStatus(presenceState, presenceStatus, lastActive, chatState, convoId);

            } else if (method.equals(BluetoothMapContract.METHOD_SET_BLUETOOTH_STATE)) {
                boolean bluetoothState = extras.getBoolean(
                        BluetoothMapContract.EXTRA_BLUETOOTH_STATE);
                ret = setBluetoothStatus(bluetoothState);
            }
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
        if(ret == 0) {
            return new Bundle();
        }
        return null;
    }

    /**
     * Trigger a sync of the specified folder.
     * @param accountId the ID of the account that owns the folder
     * @param folderId the ID of the folder.
     * @return 0 at success
     */
    abstract protected int syncFolder(long accountId, long folderId);

    /**
     * Set the properties that should change presence or chat state of owner
     * e.g. when the owner is active on a BT client device but not on the BT server device
     * where the IM application is installed, it should still be possible to show an active status.
     * @param presenceState should follow the contract specified values
     * @param presenceStatus string the owners current status
     * @param lastActive time stamp of the owners last activity
     * @param chatState should follow the contract specified values
     * @param convoId ID to the conversation to change
     * @return 0 at success
     */
    abstract protected int setOwnerStatus(int presenceState, String presenceStatus,
            long lastActive, int chatState, String convoId);

    /**
     * Notify the application of the Bluetooth state
     * @param bluetoothState 'on' of 'off'
     * @return 0 at success
     */
    abstract protected int setBluetoothStatus(boolean bluetoothState);



    /**
     * Need this to suppress warning in unit tests.
     */
    @Override
    public void shutdown() {
        // Don't call super.shutdown(), which emits a warning...
    }

    /**
     * Extract the BluetoothMapContract.AccountColumns._ID from the given URI.
     */
    public static String getAccountId(Uri uri) {
        final List<String> segments = uri.getPathSegments();
        if (segments.size() < 1) {
            throw new IllegalArgumentException("No AccountId pressent in URI: " + uri);
        }
        return segments.get(0);
    }
}
