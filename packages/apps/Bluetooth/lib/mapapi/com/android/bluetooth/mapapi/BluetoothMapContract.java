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

import android.content.ContentResolver;
import android.net.Uri;


/**
 * This class defines the minimum sets of data needed for a client to
 * implement to claim support for the Bluetooth Message Access Profile.
 * Access to three data sets are needed:
 * <ul>
 *   <li>Message data set containing lists of messages.</li>
 *   <li>Account data set containing info on the existing accounts, and whether to expose
 *     these accounts. The content of the account data set is often sensitive information,
 *     hence care must be taken, not to reveal any personal information nor passwords.
 *     The accounts in this data base will be exposed in the settings menu, where the user
 *     is able to enable and disable the EXPOSE_FLAG, and thereby provide access to an
 *     account from another device, without any password protection the e-mail client
 *     might provide.</li>
 *   <li>Folder data set with the folder structure for the messages. Each message is linked to an
 *     entry in this data set.</li>
 *   <li>Conversation data set with the thread structure of the messages. Each message is linked
 *     to an entry in this data set.</li>
 * </ul>
 *
 * To enable that the Bluetooth Message Access Server can detect the content provider implementing
 * this interface, the {@code provider} tag for the Bluetooth related content provider must
 * have an intent-filter like the following in the manifest:
 * <pre class="prettyprint">&lt;provider  android:authorities="[PROVIDER AUTHORITY]"
              android:exported="true"
              android:enabled="true"
              android:permission="android.permission.BLUETOOTH_MAP"&gt;
 *   ...
 *      &lt;intent-filter&gt;
           &lt;action android:name="android.content.action.BLEUETOOT_MAP_PROVIDER" /&gt;
        &lt;/intent-filter&gt;
 *   ...
 *   &lt;/provider&gt;
 * [PROVIDER AUTHORITY] shall be the providers authority value which implements this
 * contract. Only a single authority shall be used. The android.permission.BLUETOOTH_MAP
 * permission is needed for the provider.
 */
public final class BluetoothMapContract {
    /**
     * Constructor - should not be used
     */
    private BluetoothMapContract(){
      /* class should not be instantiated */
    }

    /**
     * Provider interface that should be used as intent-filter action in the provider section
     * of the manifest file.
     */
    public static final String PROVIDER_INTERFACE_EMAIL =
            "android.bluetooth.action.BLUETOOTH_MAP_PROVIDER";
    public static final String PROVIDER_INTERFACE_IM =
            "android.bluetooth.action.BLUETOOTH_MAP_IM_PROVIDER";
    /**
     * The Bluetooth Message Access profile allows a remote BT-MAP client to trigger
     * an update of a folder for a specific e-mail account, register for reception
     * of new messages from the server.
     *
     * Additionally the Bluetooth Message Access profile allows a remote BT-MAP client
     * to push a message to a folder - e.g. outbox or draft. The Bluetooth profile
     * implementation will place a new message in one of these existing folders through
     * the content provider.
     *
     * ContentProvider.call() is used for these purposes, and the METHOD_UPDATE_FOLDER
     * method name shall trigger an update of the specified folder for a specified
     * account.
     *
     * This shall be a non blocking call simply starting the update, and the update should
     * both send and receive messages, depending on what makes sense for the specified
     * folder.
     * Bundle extra parameter will carry two INTEGER (long) values:
     *   EXTRA_UPDATE_ACCOUNT_ID containing the account_id
     *   EXTRA_UPDATE_FOLDER_ID containing the folder_id of the folder to update
     *
     * The status for send complete of messages shall be reported by updating the sent-flag
     * and e.g. for outbox messages, move them to the sent folder in the message table of the
     * content provider and trigger a change notification to any attached content observer.
     */
    public static final String METHOD_UPDATE_FOLDER = "UpdateFolder";
    public static final String EXTRA_UPDATE_ACCOUNT_ID = "UpdateAccountId";
    public static final String EXTRA_UPDATE_FOLDER_ID = "UpdateFolderId";

    /**
     * The Bluetooth Message Access profile allows a remote BT-MAP Client to update
     * the owners presence and chat state
     *
     * ContentProvider.call() is used for these purposes, and the METHOD_SET_OWNER_STATUS
     * method name shall trigger a change in owner/users presence or chat properties for an
     * account or conversation.
     *
     * This shall be a non blocking call simply setting the properties, and the change should
     * be sent to the remote server/users, depending on what property is changed.
     * Bundle extra parameter will carry following values:
     *   EXTRA_ACCOUNT_ID containing the account_id
     *   EXTRA_PRESENCE_STATE containing the presence state of the owner account
     *   EXTRA_PRESENCE_STATUS containing the presence status text from the owner
     *   EXTRA_LAST_ACTIVE containing the last activity time stamp of the owner account
     *   EXTRA_CHAT_STATE containing the chat state of a specific conversation
     *   EXTRA_CONVERSATION_ID containing the conversation that is changed
     */
    public static final String METHOD_SET_OWNER_STATUS = "SetOwnerStatus";
    public static final String EXTRA_ACCOUNT_ID = "AccountId"; // Is this needed
    public static final String EXTRA_PRESENCE_STATE = "PresenceState";
    public static final String EXTRA_PRESENCE_STATUS = "PresenceStatus";
    public static final String EXTRA_LAST_ACTIVE = "LastActive";
    public static final String EXTRA_CHAT_STATE = "ChatState";
    public static final String EXTRA_CONVERSATION_ID = "ConversationId";

    /**
     * The Bluetooth Message Access profile can inform the messaging application of the Bluetooth
     * state, whether is is turned 'on' or 'off'
     *
     * ContentProvider.call() is used for these purposes, and the METHOD_SET_BLUETOOTH_STATE
     * method name shall trigger a change in owner/users presence or chat properties for an
     * account or conversation.
     *
     * This shall be a non blocking call simply setting the properties.
     *
     * Bundle extra parameter will carry following values:
     *   EXTRA_BLUETOOTH_STATE containing the state of the Bluetooth connectivity
     */
    public static final String METHOD_SET_BLUETOOTH_STATE = "SetBtState";
    public static final String EXTRA_BLUETOOTH_STATE = "BluetoothState";

    /**
     * These column names are used as last path segment of the URI (getLastPathSegment()).
     * Access to a specific row in the tables is done by using the where-clause, hence
     * support for .../#id if not needed for the Email clients.
     * The URI format for accessing the tables are as follows:
     *   content://ProviderAuthority/TABLE_ACCOUNT
     *   content://ProviderAuthority/account_id/TABLE_MESSAGE
     *   content://ProviderAuthority/account_id/TABLE_FOLDER
     *   content://ProviderAuthority/account_id/TABLE_CONVERSATION
     *   content://ProviderAuthority/account_id/TABLE_CONVOCONTACT
     **/

    /**
     * Build URI representing the given Accounts data-set in a
     * Bluetooth provider. When queried, the direct URI for the account
     * with the given accountID is returned.
     */
    public static Uri buildAccountUri(String authority) {
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(authority).appendPath(TABLE_ACCOUNT).build();
    }
    /**
     * Build URI representing the given Account data-set with specific Id in a
     * Bluetooth provider. When queried, the direct URI for the account
     * with the given accountID is returned.
     */
    public static Uri buildAccountUriwithId(String authority, String accountId) {
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(authority)
                .appendPath(TABLE_ACCOUNT)
                .appendPath(accountId)
                .build();
    }
    /**
     * Build URI representing the entire Message table in a
     * Bluetooth provider.
     */
    public static Uri buildMessageUri(String authority) {
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(authority)
                .appendPath(TABLE_MESSAGE)
                .build();
    }
    /**
     * Build URI representing the given Message data-set in a
     * Bluetooth provider. When queried, the URI for the Messages
     * with the given accountID is returned.
     */
    public static Uri buildMessageUri(String authority, String accountId) {
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(authority)
                .appendPath(accountId)
                .appendPath(TABLE_MESSAGE)
                .build();
    }
    /**
     * Build URI representing the given Message data-set with specific messageId in a
     * Bluetooth provider. When queried, the direct URI for the account
     * with the given accountID is returned.
     */
    public static Uri buildMessageUriWithId(String authority, String accountId,String messageId) {
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(authority)
                .appendPath(accountId)
                .appendPath(TABLE_MESSAGE)
                .appendPath(messageId)
                .build();
    }
    /**
     * Build URI representing the given Message data-set in a
     * Bluetooth provider. When queried, the direct URI for the folder
     * with the given accountID is returned.
     */
    public static Uri buildFolderUri(String authority, String accountId) {
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(authority)
                .appendPath(accountId)
                .appendPath(TABLE_FOLDER)
                .build();
    }

    /**
     * Build URI representing the given Message data-set in a
     * Bluetooth provider. When queried, the direct URI for the conversation
     * with the given accountID is returned.
     */
    public static Uri buildConversationUri(String authority, String accountId) {
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(authority)
                .appendPath(accountId)
                .appendPath(TABLE_CONVERSATION)
                .build();
    }

    /**
     * Build URI representing the given Contact data-set in a
     * Bluetooth provider. When queried, the direct URI for the contacts
     * with the given accountID is returned.
     */
    public static Uri buildConvoContactsUri(String authority) {
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(authority)
                .appendPath(TABLE_CONVOCONTACT)
                .build();
    }

    /**
     * Build URI representing the given Contact data-set in a
     * Bluetooth provider. When queried, the direct URI for the contacts
     * with the given accountID is returned.
     */
    public static Uri buildConvoContactsUri(String authority, String accountId) {
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(authority)
                .appendPath(accountId)
                .appendPath(TABLE_CONVOCONTACT)
                .build();
    }
    /**
     * Build URI representing the given Contact data-set in a
     * Bluetooth provider. When queried, the direct URI for the contact
     * with the given contactID and accountID is returned.
     */
    public static Uri buildConvoContactsUriWithId(String authority, String accountId,
            String contactId) {
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(authority)
                .appendPath(accountId)
                .appendPath(TABLE_CONVOCONTACT)
                .appendPath(contactId)
                .build();
    }
    /**
     *  @hide
     */
    public static final String TABLE_ACCOUNT        = "Account";
    public static final String TABLE_MESSAGE        = "Message";
    public static final String TABLE_MESSAGE_PART   = "Part";
    public static final String TABLE_FOLDER         = "Folder";
    public static final String TABLE_CONVERSATION   = "Conversation";
    public static final String TABLE_CONVOCONTACT   = "ConvoContact";


    /**
     * Mandatory folders for the Bluetooth message access profile.
     * The email client shall at least implement the following root folders.
     * E.g. as a mapping for them such that the naming will match the underlying
     * matching folder ID's.
     */
    public static final String FOLDER_NAME_INBOX   = "INBOX";
    public static final String FOLDER_NAME_SENT    = "SENT";
    public static final String FOLDER_NAME_OUTBOX  = "OUTBOX";
    public static final String FOLDER_NAME_DRAFT   = "DRAFT";
    public static final String FOLDER_NAME_DELETED = "DELETED";
    public static final String FOLDER_NAME_OTHER   = "OTHER";

    /**
     * Folder IDs to be used with Instant Messaging virtual folders
     */
    public static final long FOLDER_ID_OTHER      = 0;
    public static final long FOLDER_ID_INBOX      = 1;
    public static final long FOLDER_ID_SENT       = 2;
    public static final long FOLDER_ID_DRAFT      = 3;
    public static final long FOLDER_ID_OUTBOX     = 4;
    public static final long FOLDER_ID_DELETED    = 5;


    /**
     * To push RFC2822 encoded messages into a folder and read RFC2822 encoded messages from
     * a folder, the openFile() interface will be used as follows:
     * Open a file descriptor to a message.
     * Two modes supported for read: With and without attachments.
     * One mode exist for write and the actual content will be with or without
     * attachments.
     *
     * mode will be "r" for read and "w" for write, never "rw".
     *
     * URI format:
     * The URI scheme is as follows.
     * For reading messages with attachments:
     *   content://ProviderAuthority/account_id/TABLE_MESSAGE/msgId
     *   Note: This shall be an offline operation, including only message parts and attachments
     *         already downloaded to the device.
     *
     * For reading messages without attachments:
     *   content://ProviderAuthority/account_id/TABLE_MESSAGE/msgId/FILE_MSG_NO_ATTACHMENTS
     *   Note: This shall be an offline operation, including only message parts already
     *         downloaded to the device.
     *
     * For downloading and reading messages with attachments:
     *   content://ProviderAuthority/account_id/TABLE_MESSAGE/msgId/FILE_MSG_DOWNLOAD
     *   Note: This shall download the message content and all attachments if possible,
     *         else throw an IOException.
     *
     * For downloading and reading messages without attachments:
     *   content://ProviderAuthority/account_id/TABLE_MESSAGE/msgId/FILE_MSG_DOWNLOAD_NO_ATTACHMENTS
     *   Note: This shall download the message content if possible, else throw an IOException.
     *
     * When reading from the file descriptor, the content provider shall return a stream
     * of bytes containing a RFC2822 encoded message, as if the message was send to an email
     * server.
     *
     * When a byte stream is written to the file descriptor, the content provider shall
     * decode the RFC2822 encoded data and insert the message into the TABLE_MESSAGE at the ID
     * supplied in URI - additionally the message content shall be stored in the underlying
     * data base structure as if the message was received from an email server. The Message ID
     * will be created using a insert on the TABLE_MESSAGE prior to calling openFile().
     * Hence the procedure for inserting a message is:
     *  - uri/msgId = insert(uri, value: folderId=xxx)
     *  - fd = openFile(uri/msgId)
     *  - fd.write (RFC2822 encoded data)
     *
     *  The Bluetooth Message Access Client might not know what to put into the From:
     *  header nor have a valid time stamp, hence the content provider shall check
     *  if the From: and Date: headers have been set by the message written, else
     *  it must fill in appropriate values.
     */
    public static final String FILE_MSG_NO_ATTACHMENTS = "NO_ATTACHMENTS";
    public static final String FILE_MSG_DOWNLOAD = "DOWNLOAD";
    public static final String FILE_MSG_DOWNLOAD_NO_ATTACHMENTS = "DOWNLOAD_NO_ATTACHMENTS";

    /**
     * Account Table
     * The columns needed to supply account information.
     * The e-mail client app may choose to expose all e-mails as being from the same account,
     * but it is not recommended, as this would be a violation of the Bluetooth specification.
     * The Bluetooth Message Access settings activity will provide the user the ability to
     * change the FLAG_EXPOSE values for each account in this table.
     * The Bluetooth Message Access service will read the values when Bluetooth is turned on,
     * and again on every notified change through the content observer interface.
     */
    public interface AccountColumns {

        /**
         * The unique ID for a row.
         * <P>Type: INTEGER (long)</P>
         */
        public static final String _ID = "_id";

        /**
         * The account name to display to the user on the device when selecting whether
         * or not to share the account over Bluetooth.
         *
         * The account display name should not reveal any sensitive information e.g. email-
         * address, as it will be added to the Bluetooth SDP record, which can be read by
         * any Bluetooth enabled device. (Access to any account content is only provided to
         * authenticated devices). It is recommended that if the email client uses the email
         * address as account name, then the address should be obfuscated (i.e. replace "@"
         * with ".")
         * <P>Type: TEXT</P>
         * read-only
         */
        public static final String ACCOUNT_DISPLAY_NAME = "account_display_name";

        /**
         * Expose this account to other authenticated Bluetooth devices. If the expose flag
         * is set, this account will be listed as an available account to access from another
         * Bluetooth device.
         *
         * This is a read/write flag, that can be set either from within the E-mail client
         * UI or the Bluetooth settings menu.
         *
         * It is recommended to either ask the user whether to expose the account, or set this
         * to "show" as default.
         *
         * This setting shall not be used to enforce whether or not an account should be shared
         * or not if the account is bound by an administrative security policy. In this case
         * the email app should not list the account at all if it is not to be sharable over BT.
         *
         * <P>Type: INTEGER (boolean) hide = 0, show = 1</P>
         */
        public static final String FLAG_EXPOSE = "flag_expose";


        /**
         * The account unique identifier representing this account. For most IM clients this will be
         * the fully qualified user name to which an invite message can be sent, from another use.
         *
         * e.g.: "map_test_user_12345@gmail.com" - for a Hangouts account
         *
         * This value will only be visible to authenticated Bluetooth devices, and will be
         * transmitted using an encrypted link.
         * <P>Type: TEXT</P>
         * read-only
         */
        public static final String ACCOUNT_UCI = "account_uci";


        /**
         * The Bluetooth SIG maintains a list of assigned numbers(text strings) for IM clients.
         * If your client/account has such a string, this is the place to return it.
         * If supported by both devices, the presence of this prefix will make it possible to
         * respond to a message by making a voice-call, using the same account information.
         * (The call will be made using the HandsFree profile)
         * https://www.bluetooth.org/en-us/specification/assigned-numbers/uniform-caller-identifiers
         *
         * e.g.: "hgus" - for Hangouts
         *
         * <P>Type: TEXT</P>
         * read-only
         */
        public static final String ACCOUNT_UCI_PREFIX = "account_uci_PREFIX";

    }

    /**
     * Message Data Parts Table
     * The columns needed to contain the actual data of the messageparts in IM messages.
     * Each "part" has its own row and represent a single mime-part in a multipart-mime
     * formatted message.
     *
     */
    public interface MessagePartColumns {

        /**
         * The unique ID for a row.
         * <P>Type: INTEGER (long)</P>
         * read-only
         */
        public static final String _ID = "_id";
        // FIXME add message parts for IM attachments
        /**
         * is this a text part  yes/no?
         * <P>Type: TEXT</P>
         * read-only
         */
        public static final String TEXT = "text";

        /**
         * The charset used in the content if it is text or 8BIT if it is
         * binary data
         *
         * <P>Type: TEXT</P>
         * read-only
         */
        public static final String CHARSET = "charset";

        /**
         * The filename representing the data file of the raw data in the database
         * If this is empty, then it must be text and part of the message body.
         * This is the name that the data will have when it is included as attachment
         *
         * <P>Type: TEXT</P>
         * read-only
         */

        public static final String FILENAME = "filename";

        /**
         * Identifier for the content in the data. This can be used to
         * refer directly to the data in the body part.
         *
         * <P>Type: TEXT</P>
         * read-only
         */

        public static final String CONTENT_ID = "cid";

        /**
         * The raw data in either text format or binary format
         *
         * <P>Type: BLOB</P>
         * read-only
         */
        public static final String RAW_DATA = "raw_data";

    }
    /**
     * The actual message table containing all messages.
     * Content that must support filtering using WHERE clauses:
     *   - To, From, Cc, Bcc, Date, ReadFlag, PriorityFlag, folder_id, account_id
     * Additional content that must be supplied:
     *   - Subject, AttachmentFlag, LoadedState, MessageSize, AttachmentSize
     * Content that must support update:
     *   - FLAG_READ and FOLDER_ID (FOLDER_ID is used to move a message to deleted)
     * Additional insert of a new message with the following values shall be supported:
     *   - FOLDER_ID
     *
     * When doing an insert on this table, the actual content of the message (subject,
     * date etc) written through file-i/o takes precedence over the inserted values and should
     * overwrite them.
     */
    public interface MessageColumns extends EmailMessageColumns {

        /**
         * The unique ID for a row.
         * <P>Type: INTEGER (long)</P>
         */
        public static final String _ID = "_id";

        /**
         * The date the message was received as a unix timestamp
         * (miliseconds since 00:00:00 UTC 1/1-1970).
         *
         * <P>Type: INTEGER (long)</P>
         * read-only
         */
        public static final String DATE = "date";

        //TODO REMOVE WHEN Parts Table is in place
        /**
         * Message body. Used by Instant Messaging
         * <P>Type: TEXT</P>
         * read-only.
         */
        public static final String BODY = "body";

        /**
         * Message subject.
         * <P>Type: TEXT</P>
         * read-only.
         */
        public static final String SUBJECT = "subject";

        /**
         * Message Read flag
         * <P>Type: INTEGER (boolean) unread = 0, read = 1</P>
         *  read/write
         */
        public static final String FLAG_READ = "flag_read";

        /**
         * Message Priority flag
         * <P>Type: INTEGER (boolean) normal priority = 0, high priority = 1</P>
         * read-only
         */
        public static final String FLAG_HIGH_PRIORITY = "high_priority";

        /**
         * Reception state - the amount of the message that have been loaded from the server.
         * <P>Type: TEXT see RECEPTION_STATE_* constants below </P>
         * read-only
         */
        public static final String RECEPTION_STATE = "reception_state";

        /**
         * Delivery state - the amount of the message that have been loaded from the server.
         * <P>Type: TEXT see DELIVERY_STATE_* constants below </P>
         * read-only
         */
        public static final String DEVILERY_STATE = "delivery_state";

        /** To be able to filter messages with attachments, we need this flag.
         * <P>Type: INTEGER (boolean) no attachment = 0, attachment = 1 </P>
         * read-only
         */
        public static final String FLAG_ATTACHMENT = "flag_attachment";

        /** The overall size in bytes of the attachments of the message.
         * <P>Type: INTEGER </P>
         */
        public static final String ATTACHMENT_SIZE = "attachment_size";

        /** The mine type of the attachments for the message.
         * <P>Type: TEXT </P>
         * read-only
         */
        public static final String ATTACHMENT_MINE_TYPES = "attachment_mime_types";

        /** The overall size in bytes of the message including any attachments.
         * This value is informative only and should be the size an email client
         * would display as size for the message.
         * <P>Type: INTEGER </P>
         * read-only
         */
        public static final String MESSAGE_SIZE = "message_size";

        /** Indicates that the message or a part of it is protected by a DRM scheme.
         * <P>Type: INTEGER (boolean) no DRM = 0, DRM protected = 1 </P>
         * read-only
         */
        public static final String FLAG_PROTECTED = "flag_protected";

        /**
         * A comma-delimited list of FROM addresses in RFC2822 format.
         * The list must be compatible with Rfc822Tokenizer.tokenize();
         * <P>Type: TEXT</P>
         * read-only
         */
        public static final String FROM_LIST = "from_list";

        /**
         * A comma-delimited list of TO addresses in RFC2822 format.
         * The list must be compatible with Rfc822Tokenizer.tokenize();
         * <P>Type: TEXT</P>
         * read-only
         */
        public static final String TO_LIST = "to_list";

        /**
         * The unique ID for a row in the folder table in which this message belongs.
         * <P>Type: INTEGER (long)</P>
         * read/write
         */
        public static final String FOLDER_ID = "folder_id";

        /**
         * The unique ID for a row in the account table which owns this message.
         * <P>Type: INTEGER (long)</P>
         * read-only
         */
        public static final String ACCOUNT_ID = "account_id";

        /**
         * The ID identify the thread/conversation a message belongs to.
         * If no thread id is available, set value to "-1"
         * <P>Type: INTEGER (long)</P>
         * read-only
         */
        public static final String THREAD_ID = "thread_id";

        /**
         * The Name of the thread/conversation a message belongs to.
         * <P>Type: TEXT</P>
         * read-only
         */
        public static final String THREAD_NAME = "thread_name";
    }

    public interface EmailMessageColumns {



        /**
         * A comma-delimited list of CC addresses in RFC2822 format.
         * The list must be compatible with Rfc822Tokenizer.tokenize();
         * <P>Type: TEXT</P>
         * read-only
         */
        public static final String CC_LIST = "cc_list";

        /**
         * A comma-delimited list of BCC addresses in RFC2822 format.
         * The list must be compatible with Rfc822Tokenizer.tokenize();
         * <P>Type: TEXT</P>
         * read-only
         */
        public static final String BCC_LIST = "bcc_list";

        /**
         * A comma-delimited list of REPLY-TO addresses in RFC2822 format.
         * The list must be compatible with Rfc822Tokenizer.tokenize();
         * <P>Type: TEXT</P>
         * read-only
         */
        public static final String REPLY_TO_LIST = "reply_to_List";


    }

    /**
     * Indicates the complete message has been delivered to the recipient.
     */
    public static final String DELIVERY_STATE_DELIVERED = "delivered";
    /**
     * Indicates that the complete message has been sent from the MSE to the remote network.
     */
    public static final String DELIVERY_STATE_SENT = "sent";

    /**
     * Indicates that the message, including any attachments, has been received from the
     * server to the device.
     */
    public static final String RECEPTION_STATE_COMPLETE = "complete";
    /**
     * Indicates the message is partially received from the email server.
     */
    public static final String RECEPTION_STATE_FRACTIONED = "fractioned";
    /**
     * Indicates that only a notification about the message have been received.
     */
    public static final String RECEPTION_STATE_NOTIFICATION = "notification";

    /**
     * Message folder structure
     * MAP enforces use of a folder structure with mandatory folders:
     *   - inbox, outbox, sent, deleted, draft
     * User defined folders are supported.
     * The folder table must provide filtering (use of WHERE clauses) of the following entries:
     *   - account_id (linking the folder to an e-mail account)
     *   - parent_id (linking the folders individually)
     * The folder table must have a folder name for each entry, and the mandatory folders
     * MUST exist for each account_id. The folders may be empty.
     * Use the FOLDER_NAME_xxx constants for the mandatory folders. Their names must
     * not be translated into other languages, as the folder browsing is string based, and
     * many Bluetooth Message Clients will use these strings to navigate to the folders.
     */
    public interface FolderColumns {

        /**
         * The unique ID for a row.
         * <P>Type: INTEGER (long)</P>
         * read-only
         */
        public static final String _ID = "_id";

        /**
         * The folder display name to present to the user.
         * <P>Type: TEXT</P>
         * read-only
         */
        public static final String NAME = "name";

        /**
         * The _id-key to the account this folder refers to.
         * <P>Type: INTEGER (long)</P>
         * read-only
         */
        public static final String ACCOUNT_ID = "account_id";

        /**
         * The _id-key to the parent folder. -1 for root folders.
         * <P>Type: INTEGER (long)</P>
         * read-only
         */
        public static final String PARENT_FOLDER_ID = "parent_id";
    }

    /**
     * Message conversation structure. Enables use of a conversation structure for messages across
     * folders, further binding contacts to conversations.
     * Content that must be supplied:
     *   - Name, LastActivity, ReadStatus, VersionCounter
     * Content that must support update:
     *   - READ_STATUS, LAST_ACTIVITY and VERSION_COUNTER (VERSION_COUNTER used to validity of _ID)
     * Additional insert of a new conversation with the following values shall be supported:
     *   - FOLDER_ID
     * When querying this table, the cursor returned must contain one row for each contact member
     * in a thread.
     * For filter/search parameters attributes to the URI will be used. The following columns must
     * support filtering:
     *  - ConvoContactColumns.NAME
     *  - ConversationColumns.THREAD_ID
     *  - ConversationColumns.LAST_ACTIVITY
     *  - ConversationColumns.READ_STATUS
     */
    public interface ConversationColumns extends ConvoContactColumns {

        /**
         * The unique ID for a row.
         * <P>Type: INTEGER (long)</P>
         * read-only
         */
// Should not be needed anymore        public static final String _ID = "_id";

        /**
         * The unique ID for a Thread.
         * <P>Type: INTEGER (long)</P>
         * read-only
         */
        public static final String THREAD_ID = "thread_id";

        /**
         * The unique ID for a row.
         * <P>Type: INTEGER (long)</P>
         * read-only
         */
// TODO: IS THIS NECESSARY - or do we need the thread ID to hold thread Id from message
//       or can we be sure we are in control and can use the _ID and put that in the message DB
        //public static final String THREAD_ID = "thread_id";

        /**
         * The type of conversation, see {@link ConversationType}
         * <P>Type: TEXT</P>
         * read-only
         */
// TODO: IS THIS NECESSARY - no conversation type is available in the latest,
//        guess it can be found from number of contacts in the conversation
        //public static final String TYPE = "type";

        /**
         * The name of the conversation, e.g. group name in case of group chat
         * <P>Type: TEXT</P>
         * read-only
         */
        public static final String THREAD_NAME = "thread_name";

        /**
         * The time stamp of the last activity in the conversation as a unix timestamp
         * (miliseconds since 00:00:00 UTC 1/1-1970)
         * <P>Type: INTEGER (long)</P>
         * read-only
         */
        public static final String LAST_THREAD_ACTIVITY = "last_thread_activity";

        /**
         * The status on the conversation, either 'read' or 'unread'
         *  <P>Type: INTEGER (boolean) unread = 0, read = 1</P>
         * read/write
         */
        public static final String READ_STATUS = "read_status";

        /**
         * A counter that keep tack of version of the table content, count up on ID reuse
         * <P>Type: INTEGER (long)</P>
         * read-only
         */
// TODO: IS THIS NECESSARY - skal den ligge i databasen?
        // CB: If we need it, it must be in the database, or initialized with a random value at
        //     BT-ON
        // UPDATE: TODO: Change to the last_activity time stamp (as a long value). This will
        //         provide the information needed for BT clients - currently unused
        public static final String VERSION_COUNTER = "version_counter";

        /**
         * A short description of the latest activity on conversation - typically
         * part of the last message.
         * <P>Type: TEXT</P>
         * read-only
         */
        public static final String SUMMARY = "convo_summary";


    }

    /**
     * MAP enables access to contacts for the conversation
     * The conversation table must provide filtering (using WHERE clauses) of following entries:
     *   - convo_id linking contacts to conversations
     *   - x_bt_uid linking contacts to PBAP contacts
     * The conversation contact table must have a convo_id and a name for each entry.
     */
    public interface ConvoContactColumns extends ChatStatusColumns, PresenceColumns {
        /**
         * The unique ID for a contact in Conversation
         * <P>Type: INTEGER (long)</P>
         * read-only
         */
// Should not be needed anymore        public static final String _ID = "_id";

        /**
        * The ID of the conversation the contact is part of.
        * <P>Type: INTEGER (long)</P>
        * read-only
        */
        public static final String CONVO_ID = "convo_id";

        /**
         * The name of contact in instant message application
         * <P>Type: TEXT</P>
         * read-only
         */
        public static final String NAME = "name";

        /**
         * The nickname of contact in instant message group chat conversation.
         * <P>Type: TEXT</P>
         * read-only
         */
        public static final String NICKNAME = "nickname";


        /**
         * The unique ID for all Bluetooth contacts available through PBAP.
         * <P>Type: INTEGER (long)</P>
         * read-only
         */
        public static final String X_BT_UID = "x_bt_uid";

        /**
         * The unique ID for the contact within the domain of the interfacing service.
         * (UCI: Unique Call Identity)
         * It is expected that a message send to this ID will reach the recipient regardless
         * through which interface the message is send.
         * For E-mail this will be the e-mail address, for Google+ this will be the e-mail address
         * associated with the contact account.
         * This ID
         * <P>Type: TEXT</P>
         * read-only
         */
        public static final String UCI = "x_bt_uci";
    }

    /**
     * The name of query parameter used to filter on recipient
     */
    public static final String FILTER_RECIPIENT_SUBSTRING = "rec_sub_str";

    /**
     * The name of query parameter used to filter on originator
     */
    public static final String FILTER_ORIGINATOR_SUBSTRING = "org_sub_str";

    /**
     * The name of query parameter used to filter on read status.
     *  - true - return only threads with all messages marked as read
     *  - false - return only threads with one or more unread messages
     *  - omitted as query parameter - do not filter on read status
     */
    public static final String FILTER_READ_STATUS = "read";

    /**
     * Time in ms since epoch. For conversations this will be for last activity
     * as a unix timestamp (miliseconds since 00:00:00 UTC 1/1-1970)
     */
    public static final String FILTER_PERIOD_BEGIN = "t_begin";

    /**
     * Time in ms since epoch. For conversations this will be for last activity
     * as a unix timestamp (miliseconds since 00:00:00 UTC 1/1-1970)
     */
    public static final String FILTER_PERIOD_END = "t_end";

    /**
     * Filter for a specific ThreadId
     */
    public static final String FILTER_THREAD_ID = "thread_id";


    public interface ChatState {
        int UNKNOWN     = 0;
        int INACITVE    = 1;
        int ACITVE      = 2;
        int COMPOSING   = 3;
        int PAUSED      = 4;
        int GONE        = 5;
    }

    /**
     * Instant Messaging contact chat state information
     * MAP enables access to contacts chat state for the instant messaging application
     * The chat state table must provide filtering (use of WHERE clauses) of the following entries:
     *   - contact_id (linking chat state to contacts)
     *   - thread_id (linking chat state to conversations and messages)
     * The presence table must have a contact_id for each entry.
     */
    public interface ChatStatusColumns {

//        /**
//         * The contact ID of a instant messaging contact.
//         * <P>Type: TEXT </P>
//         * read-only
//         */
//        public static final String CONTACT_ID = "contact_id";
//
//        /**
//         * The thread id for a conversation.
//         * <P>Type: INTEGER (long)</P>
//         * read-only
//         */
//        public static final String CONVO_ID = "convo_id";

        /**
         * The chat state of contact in conversation, see {@link ChatState}
         * <P>Type: INTERGER</P>
         * read-only
         */
        public static final String CHAT_STATE = "chat_state";

//        /**
//         * The geo location of the contact
//         * <P>Type: TEXT</P>
//         * read-only
//         */
//// TODO: IS THIS NEEDED - not in latest specification
//        public static final String GEOLOC = "geoloc";

        /**
         * The time stamp of the last time this contact was active in the conversation
         * <P>Type: INTEGER (long)</P>
         * read-only
         */
        public static final String LAST_ACTIVE = "last_active";

    }

    public interface PresenceState {
        int UNKNOWN         = 0;
        int OFFLINE         = 1;
        int ONLINE          = 2;
        int AWAY            = 3;
        int DO_NOT_DISTURB  = 4;
        int BUSY            = 5;
        int IN_A_MEETING    = 6;
    }

    /**
     * Instant Messaging contact presence information
     * MAP enables access to contacts presences information for the instant messaging application
     * The presence table must provide filtering (use of WHERE clauses) of the following entries:
     *   - contact_id (linking contacts to presence)
     * The presence table must have a contact_id for each entry.
     */
    public interface PresenceColumns {

//        /**
//         * The contact ID of a instant messaging contact.
//         * <P>Type: TEXT </P>
//         * read-only
//         */
//        public static final String CONTACT_ID = "contact_id";

        /**
         * The presence state of contact, see {@link PresenceState}
         * <P>Type: INTERGER</P>
         * read-only
         */
        public static final String PRESENCE_STATE = "presence_state";

        /**
         * The priority of contact presence
         * <P>Type: INTERGER</P>
         * read-only
         */
// TODO: IS THIS NEEDED - not in latest specification
        public static final String PRIORITY = "priority";

        /**
         * The last status text from contact
         * <P>Type: TEXT</P>
         * read-only
         */
        public static final String STATUS_TEXT = "status_text";

        /**
         * The time stamp of the last time the contact was online
         * <P>Type: INTEGER (long)</P>
         * read-only
         */
        public static final String LAST_ONLINE = "last_online";

    }


    /**
     * A projection of all the columns in the Message table
     */
    public static final String[] BT_MESSAGE_PROJECTION = new String[] {
        MessageColumns._ID,
        MessageColumns.DATE,
        MessageColumns.SUBJECT,
        //TODO REMOVE WHEN Parts Table is in place
        MessageColumns.BODY,
        MessageColumns.MESSAGE_SIZE,
        MessageColumns.FOLDER_ID,
        MessageColumns.FLAG_READ,
        MessageColumns.FLAG_PROTECTED,
        MessageColumns.FLAG_HIGH_PRIORITY,
        MessageColumns.FLAG_ATTACHMENT,
        MessageColumns.ATTACHMENT_SIZE,
        MessageColumns.FROM_LIST,
        MessageColumns.TO_LIST,
        MessageColumns.CC_LIST,
        MessageColumns.BCC_LIST,
        MessageColumns.REPLY_TO_LIST,
        MessageColumns.RECEPTION_STATE,
        MessageColumns.DEVILERY_STATE,
        MessageColumns.THREAD_ID
    };

    public static final String[] BT_INSTANT_MESSAGE_PROJECTION = new String[] {
        MessageColumns._ID,
        MessageColumns.DATE,
        MessageColumns.SUBJECT,
        MessageColumns.MESSAGE_SIZE,
        MessageColumns.FOLDER_ID,
        MessageColumns.FLAG_READ,
        MessageColumns.FLAG_PROTECTED,
        MessageColumns.FLAG_HIGH_PRIORITY,
        MessageColumns.FLAG_ATTACHMENT,
        MessageColumns.ATTACHMENT_SIZE,
        MessageColumns.ATTACHMENT_MINE_TYPES,
        MessageColumns.FROM_LIST,
        MessageColumns.TO_LIST,
        MessageColumns.RECEPTION_STATE,
        MessageColumns.DEVILERY_STATE,
        MessageColumns.THREAD_ID,
        MessageColumns.THREAD_NAME
    };

    /**
     * A projection of all the columns in the Account table
     */
    public static final String[] BT_ACCOUNT_PROJECTION = new String[] {
        AccountColumns._ID,
        AccountColumns.ACCOUNT_DISPLAY_NAME,
        AccountColumns.FLAG_EXPOSE,
    };

    /**
     * A projection of all the columns in the Account table
     * TODO: Is this the way to differentiate
     */
    public static final String[] BT_IM_ACCOUNT_PROJECTION = new String[] {
        AccountColumns._ID,
        AccountColumns.ACCOUNT_DISPLAY_NAME,
        AccountColumns.FLAG_EXPOSE,
        AccountColumns.ACCOUNT_UCI,
        AccountColumns.ACCOUNT_UCI_PREFIX
    };

    /**
     * A projection of all the columns in the Folder table
     */
    public static final String[] BT_FOLDER_PROJECTION = new String[] {
        FolderColumns._ID,
        FolderColumns.NAME,
        FolderColumns.ACCOUNT_ID,
        FolderColumns.PARENT_FOLDER_ID
    };


    /**
     * A projection of all the columns in the Conversation table
     */
    public static final String[] BT_CONVERSATION_PROJECTION = new String[] {
        /* Thread information */
        ConversationColumns.THREAD_ID,
        ConversationColumns.THREAD_NAME,
        ConversationColumns.READ_STATUS,
        ConversationColumns.LAST_THREAD_ACTIVITY,
        ConversationColumns.VERSION_COUNTER,
        ConversationColumns.SUMMARY,
        /* Contact information */
        ConversationColumns.UCI,
        ConversationColumns.NAME,
        ConversationColumns.NICKNAME,
        ConversationColumns.CHAT_STATE,
        ConversationColumns.LAST_ACTIVE,
        ConversationColumns.X_BT_UID,
        ConversationColumns.PRESENCE_STATE,
        ConversationColumns.STATUS_TEXT,
        ConversationColumns.PRIORITY
    };

    /**
     * A projection of the Contact Info and Presence columns in the Contact Info in table
     */
    public static final String[] BT_CONTACT_CHATSTATE_PRESENCE_PROJECTION = new String[] {
        ConvoContactColumns.UCI,
        ConvoContactColumns.CONVO_ID,
        ConvoContactColumns.NAME,
        ConvoContactColumns.NICKNAME,
        ConvoContactColumns.X_BT_UID,
        ConvoContactColumns.CHAT_STATE,
        ConvoContactColumns.LAST_ACTIVE,
        ConvoContactColumns.PRESENCE_STATE,
        ConvoContactColumns.PRIORITY,
        ConvoContactColumns.STATUS_TEXT,
        ConvoContactColumns.LAST_ONLINE
    };

    /**
     * A projection of the Contact Info the columns in Contacts Info table
     */
    public static final String[] BT_CONTACT_PROJECTION = new String[] {
        ConvoContactColumns.UCI,
        ConvoContactColumns.CONVO_ID,
        ConvoContactColumns.X_BT_UID,
        ConvoContactColumns.NAME,
        ConvoContactColumns.NICKNAME
    };


    /**
     * A projection of all the columns in the Chat Status table
     */
    public static final String[] BT_CHATSTATUS_PROJECTION = new String[] {
        ChatStatusColumns.CHAT_STATE,
        ChatStatusColumns.LAST_ACTIVE,
    };

    /**
     * A projection of all the columns in the Presence table
     */
    public static final String[] BT_PRESENCE_PROJECTION = new String[] {
        PresenceColumns.PRESENCE_STATE,
        PresenceColumns.PRIORITY,
        PresenceColumns.STATUS_TEXT,
        PresenceColumns.LAST_ONLINE
    };

}
