/*
 * Copyright (C) 2012 Google Inc.
 * Licensed to The Android Open Source Project.
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

package com.android.mail.browse;

import android.content.Context;
import android.graphics.Bitmap;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.LruCache;
import android.util.Pair;

import com.android.mail.R;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.ParticipantInfo;
import com.android.mail.providers.UIProvider;
import com.android.mail.utils.FolderUri;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;

import java.util.ArrayList;
import java.util.List;

/**
 * This is the view model for the conversation header. It includes all the
 * information needed to layout a conversation header view. Each view model is
 * associated with a conversation and is cached to improve the relayout time.
 */
public class ConversationItemViewModel {
    private static final int MAX_CACHE_SIZE = 100;

    @VisibleForTesting
    static LruCache<Pair<String, Long>, ConversationItemViewModel> sConversationHeaderMap
        = new LruCache<Pair<String, Long>, ConversationItemViewModel>(MAX_CACHE_SIZE);

    /**
     * The Folder associated with the cache of models.
     */
    private static Folder sCachedModelsFolder;

    // The hashcode used to detect if the conversation has changed.
    private int mDataHashCode;
    private int mLayoutHashCode;

    // Unread
    public boolean unread;

    // Date
    CharSequence dateText;
    public boolean showDateText = true;

    // Personal level
    Bitmap personalLevelBitmap;

    public Bitmap infoIcon;

    public String badgeText;

    public int insetPadding = 0;

    // Paperclip
    Bitmap paperclip;

    /** If <code>true</code>, we will not apply any formatting to {@link #sendersText}. */
    public boolean preserveSendersText = false;

    // Senders
    public String sendersText;

    SpannableStringBuilder sendersDisplayText;
    StaticLayout sendersDisplayLayout;

    boolean hasDraftMessage;

    // View Width
    public int viewWidth;

    // Standard scaled dimen used to detect if the scale of text has changed.
    @Deprecated
    public int standardScaledDimen;

    public long maxMessageId;

    public int gadgetMode;

    public Conversation conversation;

    public ConversationItemView.ConversationItemFolderDisplayer folderDisplayer;

    public boolean hasBeenForwarded;

    public boolean hasBeenRepliedTo;

    public boolean isInvite;

    public SpannableStringBuilder messageInfoString;

    public int styledMessageInfoStringOffset;

    private String mContentDescription;

    /**
     * The email address and name of the sender whose avatar will be drawn as a conversation icon.
     */
    public final SenderAvatarModel mSenderAvatarModel = new SenderAvatarModel();

    /**
     * Display names corresponding to the email address for the senders/recipients that will be
     * displayed on the top line.
     */
    public final ArrayList<String> displayableNames = new ArrayList<>();

    /**
     * A styled version of the {@link #displayableNames} to be displayed on the top line.
     */
    public final ArrayList<SpannableString> styledNames = new ArrayList<>();

    /**
     * Returns the view model for a conversation. If the model doesn't exist for this conversation
     * null is returned. Note: this should only be called from the UI thread.
     *
     * @param account the account contains this conversation
     * @param conversationId the Id of this conversation
     * @return the view model for this conversation, or null
     */
    @VisibleForTesting
    static ConversationItemViewModel forConversationIdOrNull(String account, long conversationId) {
        final Pair<String, Long> key = new Pair<String, Long>(account, conversationId);
        synchronized(sConversationHeaderMap) {
            return sConversationHeaderMap.get(key);
        }
    }

    static ConversationItemViewModel forConversation(String account, Conversation conv) {
        ConversationItemViewModel header = ConversationItemViewModel.forConversationId(account,
                conv.id);
        header.conversation = conv;
        header.unread = !conv.read;
        header.hasBeenForwarded =
                (conv.convFlags & UIProvider.ConversationFlags.FORWARDED)
                == UIProvider.ConversationFlags.FORWARDED;
        header.hasBeenRepliedTo =
                (conv.convFlags & UIProvider.ConversationFlags.REPLIED)
                == UIProvider.ConversationFlags.REPLIED;
        header.isInvite =
                (conv.convFlags & UIProvider.ConversationFlags.CALENDAR_INVITE)
                == UIProvider.ConversationFlags.CALENDAR_INVITE;
        return header;
    }

    /**
     * Returns the view model for a conversation. If this is the first time
     * call, a new view model will be returned. Note: this should only be called
     * from the UI thread.
     *
     * @param account the account contains this conversation
     * @param conversationId the Id of this conversation
     * @return the view model for this conversation
     */
    static ConversationItemViewModel forConversationId(String account, long conversationId) {
        synchronized(sConversationHeaderMap) {
            ConversationItemViewModel header =
                    forConversationIdOrNull(account, conversationId);
            if (header == null) {
                final Pair<String, Long> key = new Pair<String, Long>(account, conversationId);
                header = new ConversationItemViewModel();
                sConversationHeaderMap.put(key, header);
            }
            return header;
        }
    }

    /**
     * Returns the hashcode to compare if the data in the header is valid.
     */
    private static int getHashCode(CharSequence dateText, Object convInfo,
            List<Folder> rawFolders, boolean starred, boolean read, int priority,
            int sendingState) {
        if (dateText == null) {
            return -1;
        }
        return Objects.hashCode(convInfo, dateText, rawFolders, starred, read, priority,
                sendingState);
    }

    /**
     * Returns the layout hashcode to compare to see if the layout state has changed.
     */
    private int getLayoutHashCode() {
        return Objects.hashCode(mDataHashCode, viewWidth, standardScaledDimen, gadgetMode);
    }

    /**
     * Marks this header as having valid data and layout.
     */
    void validate() {
        mDataHashCode = getHashCode(dateText,
                conversation.conversationInfo, conversation.getRawFolders(), conversation.starred,
                conversation.read, conversation.priority, conversation.sendingState);
        mLayoutHashCode = getLayoutHashCode();
    }

    /**
     * Returns if the data in this model is valid.
     */
    boolean isDataValid() {
        return mDataHashCode == getHashCode(dateText,
                conversation.conversationInfo, conversation.getRawFolders(), conversation.starred,
                conversation.read, conversation.priority, conversation.sendingState);
    }

    /**
     * Returns if the layout in this model is valid.
     */
    boolean isLayoutValid() {
        return isDataValid() && mLayoutHashCode == getLayoutHashCode();
    }

    /**
     * Reset the content description; enough content has changed that we need to
     * regenerate it.
     */
    public void resetContentDescription() {
        mContentDescription = null;
    }

    /**
     * Get conversation information to use for accessibility.
     */
    public CharSequence getContentDescription(Context context, boolean showToHeader,
            String foldersDesc) {
        if (mContentDescription == null) {
            // If any are unread, get the first unread sender.
            // If all are unread, get the first sender.
            // If all are read, get the last sender.
            String participant = "";
            String lastParticipant = "";
            int last = conversation.conversationInfo.participantInfos != null ?
                    conversation.conversationInfo.participantInfos.size() - 1 : -1;
            if (last != -1) {
                lastParticipant = conversation.conversationInfo.participantInfos.get(last).name;
            }
            if (conversation.read) {
                participant = TextUtils.isEmpty(lastParticipant) ?
                        SendersView.getMe(showToHeader /* useObjectMe */) : lastParticipant;
            } else {
                ParticipantInfo firstUnread = null;
                for (ParticipantInfo p : conversation.conversationInfo.participantInfos) {
                    if (!p.readConversation) {
                        firstUnread = p;
                        break;
                    }
                }
                if (firstUnread != null) {
                    participant = TextUtils.isEmpty(firstUnread.name) ?
                            SendersView.getMe(showToHeader /* useObjectMe */) : firstUnread.name;
                }
            }
            if (TextUtils.isEmpty(participant)) {
                // Just take the last sender
                participant = lastParticipant;
            }

            // the toHeader should read "To: " if requested
            String toHeader = "";
            if (showToHeader && !TextUtils.isEmpty(participant)) {
                toHeader = SendersView.getFormattedToHeader().toString();
            }

            boolean isToday = DateUtils.isToday(conversation.dateMs);
            String date = DateUtils.getRelativeTimeSpanString(context, conversation.dateMs)
                    .toString();
            String readString = context.getString(
                    conversation.read ? R.string.read_string : R.string.unread_string);
            final int res;
            if (foldersDesc == null) {
                res = isToday ? R.string.content_description_today : R.string.content_description;
            } else {
                res = isToday ? R.string.content_description_today_with_folders :
                        R.string.content_description_with_folders;
            }
            mContentDescription = context.getString(res, toHeader, participant,
                    conversation.subject, conversation.getSnippet(), date, readString,
                    foldersDesc);
        }
        return mContentDescription;
    }

    /**
     * Clear cached header model objects when accessibility changes.
     */

    public static void onAccessibilityUpdated() {
        sConversationHeaderMap.evictAll();
    }

    /**
     * Clear cached header model objects when the folder changes.
     */
    public static void onFolderUpdated(Folder folder) {
        final FolderUri old = sCachedModelsFolder != null
                ? sCachedModelsFolder.folderUri : FolderUri.EMPTY;
        final FolderUri newUri = folder != null ? folder.folderUri : FolderUri.EMPTY;
        if (!old.equals(newUri)) {
            sCachedModelsFolder = folder;
            sConversationHeaderMap.evictAll();
        }
    }

    /**
     * This mutable model stores the name and email address of the sender for whom an avatar will
     * be drawn as the conversation icon.
     */
    public static final class SenderAvatarModel {
        private String mEmailAddress;
        private String mName;

        public String getEmailAddress() {
            return mEmailAddress;
        }

        public String getName() {
            return mName;
        }

        /**
         * Removes the name and email address of the participant of this avatar.
         */
        public void clear() {
            mName = null;
            mEmailAddress = null;
        }

        /**
         * @param name the name of the participant of this avatar
         * @param emailAddress the email address of the participant of this avatar; may not be null
         */
        public void populate(String name, String emailAddress) {
            if (TextUtils.isEmpty(emailAddress)) {
                throw new IllegalArgumentException("email address may not be null or empty");
            }

            mName = name;
            mEmailAddress = emailAddress;
        }

        /**
         * @return <tt>true</tt> if this model does not yet contain enough data to produce an
         *      avatar image; <tt>false</tt> otherwise
         */
        public boolean isNotPopulated() {
            return TextUtils.isEmpty(mEmailAddress);
        }
    }
}
