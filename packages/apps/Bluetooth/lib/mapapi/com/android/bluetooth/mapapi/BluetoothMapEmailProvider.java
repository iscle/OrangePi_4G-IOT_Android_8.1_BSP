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

package com.android.bluetooth.mapapi;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * A base implementation of the BluetoothMapEmailContract.
 * A base class for a ContentProvider that allows access to Email messages from a Bluetooth
 * device through the Message Access Profile.
 */
public abstract class BluetoothMapEmailProvider extends ContentProvider {

    private static final String TAG = "BluetoothMapEmailProvider";
    private static final boolean D = true;

    private static final int MATCH_ACCOUNT = 1;
    private static final int MATCH_MESSAGE = 2;
    private static final int MATCH_FOLDER = 3;

    protected ContentResolver mResolver;

    private Uri CONTENT_URI = null;
    private String mAuthority;
    private UriMatcher mMatcher;


    private PipeReader mPipeReader = new PipeReader();
    private PipeWriter mPipeWriter = new PipeWriter();

    /**
     * Write the content of a message to a stream as MIME encoded RFC-2822 data.
     * @param accountId the ID of the account to which the message belong
     * @param messageId the ID of the message to write to the stream
     * @param includeAttachment true if attachments should be included
     * @param download true if any missing part of the message shall be downloaded
     *        before written to the stream. The download flag will determine
     *        whether or not attachments shall be downloaded or only the message content.
     * @param out the FileOurputStream to write to.
     * @throws IOException
     */
    abstract protected void WriteMessageToStream(long accountId, long messageId,
            boolean includeAttachment, boolean download, FileOutputStream out)
        throws IOException;

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
       mMatcher.addURI(mAuthority, "#/"+BluetoothMapContract.TABLE_FOLDER, MATCH_FOLDER);
       mMatcher.addURI(mAuthority, "#/"+BluetoothMapContract.TABLE_MESSAGE, MATCH_MESSAGE);

       // Sanity check our setup
       if (!info.exported) {
           throw new SecurityException("Provider must be exported");
       }
       // Enforce correct permissions are used
       if (!android.Manifest.permission.BLUETOOTH_MAP.equals(info.writePermission)){
           throw new SecurityException("Provider must be protected by " +
                   android.Manifest.permission.BLUETOOTH_MAP);
       }
       mResolver = context.getContentResolver();
       super.attachInfo(context, info);
   }


    /**
     * Interface to write a stream of data to a pipe.  Use with
     * {@link ContentProvider#openPipeHelper}.
     */
    public interface PipeDataReader<T> {
        /**
         * Called from a background thread to stream data from a pipe.
         * Note that the pipe is blocking, so this thread can block on
         * reads for an arbitrary amount of time if the client is slow
         * at writing.
         *
         * @param input The pipe where data should be read. This will be
         * closed for you upon returning from this function.
         * @param uri The URI whose data is to be written.
         * @param mimeType The desired type of data to be written.
         * @param opts Options supplied by caller.
         * @param args Your own custom arguments.
         */
        public void readDataFromPipe(ParcelFileDescriptor input, Uri uri, String mimeType,
                Bundle opts, T args);
    }

    public class PipeReader implements PipeDataReader<Cursor> {
        /**
         * Read the data from the pipe and generate a message.
         * Use the message to do an update of the message specified by the URI.
         */
        @Override
        public void readDataFromPipe(ParcelFileDescriptor input, Uri uri,
                String mimeType, Bundle opts, Cursor args) {
            Log.v(TAG, "readDataFromPipe(): uri=" + uri.toString());
            FileInputStream fIn = null;
            try {
                fIn = new FileInputStream(input.getFileDescriptor());
                long messageId = Long.valueOf(uri.getLastPathSegment());
                long accountId = Long.valueOf(getAccountId(uri));
                UpdateMimeMessageFromStream(fIn, accountId, messageId);
            } catch (IOException e) {
                Log.w(TAG,"IOException: ", e);
                /* TODO: How to signal the error to the calling entity? Had expected readDataFromPipe
                 *       to throw IOException?
                 */
            } finally {
                try {
                    if(fIn != null)
                        fIn.close();
                } catch (IOException e) {
                    Log.w(TAG,e);
                }
            }
        }
    }

    /**
     * Read a MIME encoded RFC-2822 fileStream and update the message content.
     * The Date and/or From headers may not be present in the MIME encoded
     * message, and this function shall add appropriate values if the headers
     * are missing. From should be set to the owner of the account.
     *
     * @param input the file stream to read data from
     * @param accountId the accountId
     * @param messageId ID of the message to update
     */
    abstract protected void UpdateMimeMessageFromStream(FileInputStream input, long accountId,
            long messageId) throws IOException;

    public class PipeWriter implements PipeDataWriter<Cursor> {
        /**
         * Generate a message based on the cursor, and write the encoded data to the stream.
         */

        public void writeDataToPipe(ParcelFileDescriptor output, Uri uri, String mimeType,
                Bundle opts, Cursor c) {
            if (D) Log.d(TAG, "writeDataToPipe(): uri=" + uri.toString() +
                    " - getLastPathSegment() = " + uri.getLastPathSegment());

            FileOutputStream fout = null;

            try {
                fout = new FileOutputStream(output.getFileDescriptor());

                boolean includeAttachments = true;
                boolean download = false;
                List<String> segments = uri.getPathSegments();
                long messageId = Long.parseLong(segments.get(2));
                long accountId = Long.parseLong(getAccountId(uri));
                if(segments.size() >= 4) {
                    String format = segments.get(3);
                    if(format.equalsIgnoreCase(BluetoothMapContract.FILE_MSG_NO_ATTACHMENTS)) {
                        includeAttachments = false;
                    } else if(format.equalsIgnoreCase(BluetoothMapContract.FILE_MSG_DOWNLOAD_NO_ATTACHMENTS)) {
                        includeAttachments = false;
                        download = true;
                    } else if(format.equalsIgnoreCase(BluetoothMapContract.FILE_MSG_DOWNLOAD)) {
                        download = true;
                    }
                }

                WriteMessageToStream(accountId, messageId, includeAttachments, download, fout);
            } catch (IOException e) {
                Log.w(TAG, e);
                /* TODO: How to signal the error to the calling entity? Had expected writeDataToPipe
                 *       to throw IOException?
                 */
            } finally {
                try {
                    fout.flush();
                } catch (IOException e) {
                    Log.w(TAG, "IOException: ", e);
                }
                try {
                    fout.close();
                } catch (IOException e) {
                    Log.w(TAG, "IOException: ", e);
                }
            }
        }
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
                newUri = BluetoothMapContract.buildMessageUriWithId(mAuthority,accountId, messageId);
            }
        }
        if(D) Log.d(TAG,"onMessageChanged() accountId = " + accountId + " messageId = " + messageId + " URI: " + newUri);
        mResolver.notifyChange(newUri, null);
    }

    /**
     * Not used, this is just a dummy implementation.
     */
    @Override
    public String getType(Uri uri) {
        return "Email";
    }

    /**
     * Open a file descriptor to a message.
     * Two modes supported for read: With and without attachments.
     * One mode exist for write and the actual content will be with or without
     * attachments.
     *
     * Mode will be "r" or "w".
     *
     * URI format:
     * The URI scheme is as follows.
     * For messages with attachments:
     *   content://com.android.mail.bluetoothprovider/Messages/msgId#
     *
     * For messages without attachments:
     *   content://com.android.mail.bluetoothprovider/Messages/msgId#/NO_ATTACHMENTS
     *
     * UPDATE: For write.
     *         First create a message in the DB using insert into the message DB
     *         Then open a file handle to the #id
     *         write the data to a stream created from the fileHandle.
     *
     * @param uri the URI to open. ../Messages/#id
     * @param mode the mode to use. The following modes exist: - UPDATE do not work - use URI
     *  - "read_with_attachments" - to read an e-mail including any attachments
     *  - "read_no_attachments" - to read an e-mail excluding any attachments
     *  - "write" - to add a mime encoded message to the database. This write
     *              should not trigger the message to be send.
     * @return the ParcelFileDescriptor
     *  @throws FileNotFoundException
     */
    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        long callingId = Binder.clearCallingIdentity();
        if(D)Log.d(TAG, "openFile(): uri=" + uri.toString() + " - getLastPathSegment() = " +
                uri.getLastPathSegment());
        try {
            /* To be able to do abstraction of the file IO, we simply ignore the URI at this
             * point and let the read/write function implementations parse the URI. */
            if(mode.equals("w")) {
                return openInversePipeHelper(uri, null, null, null, mPipeReader);
            } else {
                return openPipeHelper (uri, null, null, null, mPipeWriter);
            }
        } catch (IOException e) {
            Log.w(TAG,e);
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
        return null;
    }

    /**
     * A helper function for implementing {@link #openFile}, for
     * creating a data pipe and background thread allowing you to stream
     * data back from the client.  This function returns a new
     * ParcelFileDescriptor that should be returned to the caller (the caller
     * is responsible for closing it).
     *
     * @param uri The URI whose data is to be written.
     * @param mimeType The desired type of data to be written.
     * @param opts Options supplied by caller.
     * @param args Your own custom arguments.
     * @param func Interface implementing the function that will actually
     * stream the data.
     * @return Returns a new ParcelFileDescriptor holding the read side of
     * the pipe.  This should be returned to the caller for reading; the caller
     * is responsible for closing it when done.
     */
    private <T> ParcelFileDescriptor openInversePipeHelper(final Uri uri, final String mimeType,
            final Bundle opts, final T args, final PipeDataReader<T> func)
            throws FileNotFoundException {
        try {
            final ParcelFileDescriptor[] fds = ParcelFileDescriptor.createPipe();

            AsyncTask<Object, Object, Object> task = new AsyncTask<Object, Object, Object>() {
                @Override
                protected Object doInBackground(Object... params) {
                    func.readDataFromPipe(fds[0], uri, mimeType, opts, args);
                    try {
                        fds[0].close();
                    } catch (IOException e) {
                        Log.w(TAG, "Failure closing pipe", e);
                    }
                    return null;
                }
            };
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Object[])null);

            return fds[1];
        } catch (IOException e) {
            throw new FileNotFoundException("failure making pipe");
        }
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
        if(table == null){
            throw new IllegalArgumentException("Table missing in URI");
        }
        String accountId = getAccountId(uri);
        Long folderId = values.getAsLong(BluetoothMapContract.MessageColumns.FOLDER_ID);
        if(folderId == null) {
            throw new IllegalArgumentException("FolderId missing in ContentValues");
        }

        String id; // the id of the entry inserted into the database
        long callingId = Binder.clearCallingIdentity();
        Log.d(TAG, "insert(): uri=" + uri.toString() + " - getLastPathSegment() = " +
                uri.getLastPathSegment());
        try {
            if(table.equals(BluetoothMapContract.TABLE_MESSAGE)) {
                id = insertMessage(accountId, folderId.toString());
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
    abstract protected String insertMessage(String accountId, String folderId);

     /**
     * Utility function to build a projection based on a projectionMap.
     *
     *   "btColumnName" -> "emailColumnName as btColumnName" for each entry.
     *
     * This supports SQL statements in the emailColumnName entry.
     * @param projection
     * @param projectionMap <btColumnName, emailColumnName>
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
     * This query needs to map from the data used in the e-mail client to BluetoothMapContract type of data.
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        long callingId = Binder.clearCallingIdentity();
        try {
            String accountId = null;
            switch (mMatcher.match(uri)) {
                case MATCH_ACCOUNT:
                    return queryAccount(projection, selection, selectionArgs, sortOrder);
                case MATCH_FOLDER:
                    accountId = getAccountId(uri);
                    return queryFolder(accountId, projection, selection, selectionArgs, sortOrder);
                case MATCH_MESSAGE:
                    accountId = getAccountId(uri);
                    return queryMessage(accountId, projection, selection, selectionArgs, sortOrder);
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
    abstract protected Cursor queryAccount(String[] projection, String selection, String[] selectionArgs,
            String sortOrder);

    /**
     * Filter out the non usable folders and ensure to name the mandatory folders
     * inbox, outbox, sent, deleted and draft.
     * @param accountId
     * @param projection
     * @param selection
     * @param selectionArgs
     * @param sortOrder
     * @return
     */
    abstract protected Cursor queryFolder(String accountId, String[] projection, String selection, String[] selectionArgs,
            String sortOrder);
    /**
     * For the message table the selection (where clause) can only include the following columns:
     *    date: less than, greater than and equals
     *    flagRead: = 1 or = 0
     *    flagPriority: = 1 or = 0
     *    folder_id: the ID of the folder only equals
     *    toList: partial name/address search
     *    ccList: partial name/address search
     *    bccList: partial name/address search
     *    fromList: partial name/address search
     * Additionally the COUNT and OFFSET shall be supported.
     * @param accountId the ID of the account
     * @param projection
     * @param selection
     * @param selectionArgs
     * @param sortOrder
     * @return a cursor to query result
     */
    abstract protected Cursor queryMessage(String accountId, String[] projection, String selection, String[] selectionArgs,
            String sortOrder);

    /**
     * update()
     * Messages can be modified in the following cases:
     *  - the folder_key of a message - hence the message can be moved to a new folder,
     *                                  but the content cannot be modified.
     *  - the FLAG_READ state can be changed.
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
            throw new IllegalArgumentException("selection shall not be used, ContentValues shall contain the data");
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
                Integer exposeFlag = values.getAsInteger(BluetoothMapContract.AccountColumns.FLAG_EXPOSE);
                if(exposeFlag == null){
                    throw new IllegalArgumentException("Expose flag missing in update values!");
                }
                return updateAccount(accountId, exposeFlag);
            } else if(table.equals(BluetoothMapContract.TABLE_FOLDER)) {
                return 0; // We do not support changing folders
            } else if(table.equals(BluetoothMapContract.TABLE_MESSAGE)) {
                String accountId = getAccountId(uri);
                Long messageId = values.getAsLong(BluetoothMapContract.MessageColumns._ID);
                if(messageId == null) {
                    throw new IllegalArgumentException("Message ID missing in update values!");
                }
                Long folderId = values.getAsLong(BluetoothMapContract.MessageColumns.FOLDER_ID);
                Boolean flagRead = values.getAsBoolean(BluetoothMapContract.MessageColumns.FLAG_READ);
                return updateMessage(accountId, messageId, folderId, flagRead);
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
    abstract protected int updateAccount(String accountId, int flagExpose);

    /**
     * Update an entry in the message table.
     * @param accountId ID of the account to which the messageId relates
     * @param messageId the ID of the message to update
     * @param folderId the new folder ID value to set - ignore if null.
     * @param flagRead the new flagRead value to set - ignore if null.
     * @return
     */
    abstract protected int updateMessage(String accountId, Long messageId, Long folderId, Boolean flagRead);


    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        long callingId = Binder.clearCallingIdentity();
        if(D)Log.d(TAG, "call(): method=" + method + " arg=" + arg + "ThreadId: " + Thread.currentThread().getId());

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
                int ret = syncFolder(accountId, folderId);
                if(ret == 0) {
                    return new Bundle();
                }
                return null;
            }
        } finally {
            Binder.restoreCallingIdentity(callingId);
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
