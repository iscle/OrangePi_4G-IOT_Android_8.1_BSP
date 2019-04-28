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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.support.v4.text.BidiFormatter;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.TextAppearanceSpan;

import com.android.mail.R;
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.ConversationInfo;
import com.android.mail.providers.ParticipantInfo;
import com.android.mail.providers.UIProvider;
import com.android.mail.utils.ObjectCache;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SendersView {
    /** The maximum number of senders to display for a given conversation */
    private static final int MAX_SENDER_COUNT = 4;

    private static final Integer DOES_NOT_EXIST = -5;
    // FIXME(ath): make all of these statics instance variables, and have callers hold onto this
    // instance as long as appropriate (e.g. activity lifetime).
    // no need to listen for configuration changes.
    private static String sSendersSplitToken;
    private static CharSequence sDraftSingularString;
    private static CharSequence sDraftPluralString;
    private static CharSequence sSendingString;
    private static CharSequence sRetryingString;
    private static CharSequence sFailedString;
    private static String sDraftCountFormatString;
    private static CharacterStyle sDraftsStyleSpan;
    private static CharacterStyle sSendingStyleSpan;
    private static CharacterStyle sRetryingStyleSpan;
    private static CharacterStyle sFailedStyleSpan;
    private static TextAppearanceSpan sUnreadStyleSpan;
    private static CharacterStyle sReadStyleSpan;
    private static String sMeSubjectString;
    private static String sMeObjectString;
    private static String sToHeaderString;
    private static String sMessageCountSpacerString;
    public static CharSequence sElidedString;
    private static BroadcastReceiver sConfigurationChangedReceiver;
    private static TextAppearanceSpan sMessageInfoReadStyleSpan;
    private static TextAppearanceSpan sMessageInfoUnreadStyleSpan;
    private static BidiFormatter sBidiFormatter;

    // We only want to have at most 2 Priority to length maps.  This will handle the case where
    // there is a widget installed on the launcher while the user is scrolling in the app
    private static final int MAX_PRIORITY_LENGTH_MAP_LIST = 2;

    // Cache of priority to length maps.  We can't just use a single instance as it may be
    // modified from different threads
    private static final ObjectCache<Map<Integer, Integer>> PRIORITY_LENGTH_MAP_CACHE =
            new ObjectCache<Map<Integer, Integer>>(
                    new ObjectCache.Callback<Map<Integer, Integer>>() {
                        @Override
                        public Map<Integer, Integer> newInstance() {
                            return Maps.newHashMap();
                        }
                        @Override
                        public void onObjectReleased(Map<Integer, Integer> object) {
                            object.clear();
                        }
                    }, MAX_PRIORITY_LENGTH_MAP_LIST);

    public static Typeface getTypeface(boolean isUnread) {
        return isUnread ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT;
    }

    private static synchronized void getSenderResources(
            Context context, final boolean resourceCachingRequired) {
        if (sConfigurationChangedReceiver == null && resourceCachingRequired) {
            sConfigurationChangedReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    sDraftSingularString = null;
                    getSenderResources(context, true);
                }
            };
            context.registerReceiver(sConfigurationChangedReceiver, new IntentFilter(
                    Intent.ACTION_CONFIGURATION_CHANGED));
        }
        if (sDraftSingularString == null) {
            Resources res = context.getResources();
            sSendersSplitToken = res.getString(R.string.senders_split_token);
            sElidedString = res.getString(R.string.senders_elided);
            sDraftSingularString = res.getQuantityText(R.plurals.draft, 1);
            sDraftPluralString = res.getQuantityText(R.plurals.draft, 2);
            sDraftCountFormatString = res.getString(R.string.draft_count_format);
            sMeSubjectString = res.getString(R.string.me_subject_pronoun);
            sMeObjectString = res.getString(R.string.me_object_pronoun);
            sToHeaderString = res.getString(R.string.to_heading);
            sMessageInfoUnreadStyleSpan = new TextAppearanceSpan(context,
                    R.style.MessageInfoUnreadTextAppearance);
            sMessageInfoReadStyleSpan = new TextAppearanceSpan(context,
                    R.style.MessageInfoReadTextAppearance);
            sDraftsStyleSpan = new TextAppearanceSpan(context, R.style.DraftTextAppearance);
            sUnreadStyleSpan = new TextAppearanceSpan(context, R.style.SendersAppearanceUnreadStyle);
            sSendingStyleSpan = new TextAppearanceSpan(context, R.style.SendingTextAppearance);
            sRetryingStyleSpan = new TextAppearanceSpan(context, R.style.RetryingTextAppearance);
            sFailedStyleSpan = new TextAppearanceSpan(context, R.style.FailedTextAppearance);
            sReadStyleSpan = new TextAppearanceSpan(context, R.style.SendersAppearanceReadStyle);
            sMessageCountSpacerString = res.getString(R.string.message_count_spacer);
            sSendingString = res.getString(R.string.sending);
            sRetryingString = res.getString(R.string.message_retrying);
            sFailedString = res.getString(R.string.message_failed);
            sBidiFormatter = BidiFormatter.getInstance();
        }
    }

    public static SpannableStringBuilder createMessageInfo(Context context, Conversation conv,
            final boolean resourceCachingRequired) {
        SpannableStringBuilder messageInfo = new SpannableStringBuilder();

        try {
            final ConversationInfo conversationInfo = conv.conversationInfo;
            final int sendingStatus = conv.sendingState;
            boolean hasSenders = false;
            // This covers the case where the sender is "me" and this is a draft
            // message, which means this will only run once most of the time.
            for (ParticipantInfo p : conversationInfo.participantInfos) {
                if (!TextUtils.isEmpty(p.name)) {
                    hasSenders = true;
                    break;
                }
            }
            getSenderResources(context, resourceCachingRequired);
            final int count = conversationInfo.messageCount;
            final int draftCount = conversationInfo.draftCount;
            if (count > 1) {
                appendMessageInfo(messageInfo, Integer.toString(count), CharacterStyle.wrap(
                        conv.read ? sMessageInfoReadStyleSpan : sMessageInfoUnreadStyleSpan),
                        false, conv.read);
            }

            boolean appendSplitToken = hasSenders || count > 1;
            if (draftCount > 0) {
                final CharSequence draftText;
                if (draftCount == 1) {
                    draftText = sDraftSingularString;
                } else {
                    draftText = sDraftPluralString +
                            String.format(sDraftCountFormatString, draftCount);
                }

                appendMessageInfo(messageInfo, draftText, sDraftsStyleSpan, appendSplitToken,
                        conv.read);
            }

            final boolean showState = sendingStatus == UIProvider.ConversationSendingState.SENDING ||
                    sendingStatus == UIProvider.ConversationSendingState.RETRYING ||
                    sendingStatus == UIProvider.ConversationSendingState.SEND_ERROR;
            if (showState) {
                appendSplitToken |= draftCount > 0;

                final CharSequence statusText;
                final Object span;
                if (sendingStatus == UIProvider.ConversationSendingState.SENDING) {
                    statusText = sSendingString;
                    span = sSendingStyleSpan;
                } else if (sendingStatus == UIProvider.ConversationSendingState.RETRYING) {
                    statusText = sSendingString;
                    span = sSendingStyleSpan;
                } else {
                    statusText = sFailedString;
                    span = sFailedStyleSpan;
                }

                appendMessageInfo(messageInfo, statusText, span, appendSplitToken, conv.read);
            }

            // Prepend a space if we are showing other message info text.
            if (count > 1 || (draftCount > 0 && hasSenders) || showState) {
                messageInfo.insert(0, sMessageCountSpacerString);
            }
        } finally {
            if (!resourceCachingRequired) {
                clearResourceCache();
            }
        }

        return messageInfo;
    }

    private static void appendMessageInfo(SpannableStringBuilder sb, CharSequence text,
            Object span, boolean appendSplitToken, boolean convRead) {
        int startIndex = sb.length();
        if (appendSplitToken) {
            sb.append(sSendersSplitToken);
            sb.setSpan(CharacterStyle.wrap(convRead ?
                    sMessageInfoReadStyleSpan : sMessageInfoUnreadStyleSpan),
                    startIndex, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        startIndex = sb.length();
        sb.append(text);
        sb.setSpan(span, startIndex, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    public static void format(Context context, ConversationInfo conversationInfo,
            String messageInfo, int maxChars, ArrayList<SpannableString> styledSenders,
            ArrayList<String> displayableSenderNames,
            ConversationItemViewModel.SenderAvatarModel senderAvatarModel,
            Account account, final boolean showToHeader, final boolean resourceCachingRequired) {
        try {
            getSenderResources(context, resourceCachingRequired);
            format(context, conversationInfo, messageInfo, maxChars, styledSenders,
                    displayableSenderNames, senderAvatarModel, account,
                    sUnreadStyleSpan, sReadStyleSpan, showToHeader, resourceCachingRequired);
        } finally {
            if (!resourceCachingRequired) {
                clearResourceCache();
            }
        }
    }

    public static void format(Context context, ConversationInfo conversationInfo,
            String messageInfo, int maxChars, ArrayList<SpannableString> styledSenders,
            ArrayList<String> displayableSenderNames,
            ConversationItemViewModel.SenderAvatarModel senderAvatarModel,
            Account account, final TextAppearanceSpan notificationUnreadStyleSpan,
            final CharacterStyle notificationReadStyleSpan, final boolean showToHeader,
            final boolean resourceCachingRequired) {
        try {
            getSenderResources(context, resourceCachingRequired);
            handlePriority(maxChars, messageInfo, conversationInfo, styledSenders,
                    displayableSenderNames, senderAvatarModel, account,
                    notificationUnreadStyleSpan, notificationReadStyleSpan, showToHeader);
        } finally {
            if (!resourceCachingRequired) {
                clearResourceCache();
            }
        }
    }

    private static void handlePriority(int maxChars, String messageInfoString,
            ConversationInfo conversationInfo, ArrayList<SpannableString> styledSenders,
            ArrayList<String> displayableSenderNames,
            ConversationItemViewModel.SenderAvatarModel senderAvatarModel,
            Account account, final TextAppearanceSpan unreadStyleSpan,
            final CharacterStyle readStyleSpan, final boolean showToHeader) {
        final boolean shouldSelectSenders = displayableSenderNames != null;
        final boolean shouldSelectAvatar = senderAvatarModel != null;
        int maxPriorityToInclude = -1; // inclusive
        int numCharsUsed = messageInfoString.length(); // draft, number drafts,
                                                       // count
        int numSendersUsed = 0;
        int numCharsToRemovePerWord = 0;
        int maxFoundPriority = 0;
        if (numCharsUsed > maxChars) {
            numCharsToRemovePerWord = numCharsUsed - maxChars;
        }

        final Map<Integer, Integer> priorityToLength = PRIORITY_LENGTH_MAP_CACHE.get();
        try {
            priorityToLength.clear();
            int senderLength;
            for (ParticipantInfo info : conversationInfo.participantInfos) {
                final String senderName = info.name;
                senderLength = !TextUtils.isEmpty(senderName) ? senderName.length() : 0;
                priorityToLength.put(info.priority, senderLength);
                maxFoundPriority = Math.max(maxFoundPriority, info.priority);
            }
            while (maxPriorityToInclude < maxFoundPriority) {
                if (priorityToLength.containsKey(maxPriorityToInclude + 1)) {
                    int length = numCharsUsed + priorityToLength.get(maxPriorityToInclude + 1);
                    if (numCharsUsed > 0)
                        length += 2;
                    // We must show at least two senders if they exist. If we don't
                    // have space for both
                    // then we will truncate names.
                    if (length > maxChars && numSendersUsed >= 2) {
                        break;
                    }
                    numCharsUsed = length;
                    numSendersUsed++;
                }
                maxPriorityToInclude++;
            }
        } finally {
            PRIORITY_LENGTH_MAP_CACHE.release(priorityToLength);
        }

        SpannableString spannableDisplay;
        boolean appendedElided = false;
        final Map<String, Integer> displayHash = Maps.newHashMap();
        final List<String> senderEmails = Lists.newArrayListWithExpectedSize(MAX_SENDER_COUNT);
        String firstSenderEmail = null;
        String firstSenderName = null;
        for (int i = 0; i < conversationInfo.participantInfos.size(); i++) {
            final ParticipantInfo currentParticipant = conversationInfo.participantInfos.get(i);
            final String currentEmail = currentParticipant.email;

            final String currentName = currentParticipant.name;
            String nameString = !TextUtils.isEmpty(currentName) ? currentName : "";
            if (nameString.length() == 0) {
                // if we're showing the To: header, show the object version of me.
                nameString = getMe(showToHeader /* useObjectMe */);
            }
            if (numCharsToRemovePerWord != 0) {
                nameString = nameString.substring(0,
                        Math.max(nameString.length() - numCharsToRemovePerWord, 0));
            }

            final int priority = currentParticipant.priority;
            final CharacterStyle style = CharacterStyle.wrap(currentParticipant.readConversation ?
                    readStyleSpan : unreadStyleSpan);
            if (priority <= maxPriorityToInclude) {
                spannableDisplay = new SpannableString(sBidiFormatter.unicodeWrap(nameString));
                // Don't duplicate senders; leave the first instance, unless the
                // current instance is also unread.
                int oldPos = displayHash.containsKey(currentName) ? displayHash
                        .get(currentName) : DOES_NOT_EXIST;
                // If this sender doesn't exist OR the current message is
                // unread, add the sender.
                if (oldPos == DOES_NOT_EXIST || !currentParticipant.readConversation) {
                    // If the sender entry already existed, and is right next to the
                    // current sender, remove the old entry.
                    if (oldPos != DOES_NOT_EXIST && i > 0 && oldPos == i - 1
                            && oldPos < styledSenders.size()) {
                        // Remove the old one!
                        styledSenders.set(oldPos, null);
                        if (shouldSelectSenders && !TextUtils.isEmpty(currentEmail)) {
                            senderEmails.remove(currentEmail);
                            displayableSenderNames.remove(currentName);
                        }
                    }
                    displayHash.put(currentName, i);
                    spannableDisplay.setSpan(style, 0, spannableDisplay.length(), 0);
                    styledSenders.add(spannableDisplay);
                }
            } else {
                if (!appendedElided) {
                    spannableDisplay = new SpannableString(sElidedString);
                    spannableDisplay.setSpan(style, 0, spannableDisplay.length(), 0);
                    appendedElided = true;
                    styledSenders.add(spannableDisplay);
                }
            }

            final String senderEmail = TextUtils.isEmpty(currentName) ? account.getEmailAddress() :
                    TextUtils.isEmpty(currentEmail) ? currentName : currentEmail;

            if (shouldSelectSenders) {
                if (i == 0) {
                    // Always add the first sender!
                    firstSenderEmail = senderEmail;
                    firstSenderName = currentName;
                } else {
                    if (!Objects.equal(firstSenderEmail, senderEmail)) {
                        int indexOf = senderEmails.indexOf(senderEmail);
                        if (indexOf > -1) {
                            senderEmails.remove(indexOf);
                            displayableSenderNames.remove(indexOf);
                        }
                        senderEmails.add(senderEmail);
                        displayableSenderNames.add(currentName);
                        if (senderEmails.size() > MAX_SENDER_COUNT) {
                            senderEmails.remove(0);
                            displayableSenderNames.remove(0);
                        }
                    }
                }
            }

            // if the corresponding message from this participant is unread and no sender avatar
            // is yet chosen, choose this one
            if (shouldSelectAvatar && senderAvatarModel.isNotPopulated() &&
                    !currentParticipant.readConversation) {
                senderAvatarModel.populate(currentName, senderEmail);
            }
        }

        // always add the first sender to the display
        if (shouldSelectSenders && !TextUtils.isEmpty(firstSenderEmail)) {
            if (displayableSenderNames.size() < MAX_SENDER_COUNT) {
                displayableSenderNames.add(0, firstSenderName);
            } else {
                displayableSenderNames.set(0, firstSenderName);
            }
        }

        // if all messages in the thread were read, we must search for an appropriate avatar
        if (shouldSelectAvatar && senderAvatarModel.isNotPopulated()) {
            // search for the last sender that is not the current account
            for (int i = conversationInfo.participantInfos.size() - 1; i >= 0; i--) {
                final ParticipantInfo participant = conversationInfo.participantInfos.get(i);
                // empty name implies it is the current account and should not be chosen
                if (!TextUtils.isEmpty(participant.name)) {
                    // use the participant name in place of unusable email addresses
                    final String senderEmail = TextUtils.isEmpty(participant.email) ?
                            participant.name : participant.email;
                    senderAvatarModel.populate(participant.name, senderEmail);
                    break;
                }
            }

            // if we still don't have an avatar, the account is emailing itself
            if (senderAvatarModel.isNotPopulated()) {
                senderAvatarModel.populate(account.getDisplayName(), account.getEmailAddress());
            }
        }
    }

    static String getMe(boolean useObjectMe) {
        return useObjectMe ? sMeObjectString : sMeSubjectString;
    }

    public static SpannableString getFormattedToHeader() {
        final SpannableString formattedToHeader = new SpannableString(sToHeaderString);
        final CharacterStyle readStyle = CharacterStyle.wrap(sReadStyleSpan);
        formattedToHeader.setSpan(readStyle, 0, formattedToHeader.length(), 0);
        return formattedToHeader;
    }

    public static SpannableString getSingularDraftString(Context context) {
        getSenderResources(context, true /* resourceCachingRequired */);
        final SpannableString formattedDraftString = new SpannableString(sDraftSingularString);
        final CharacterStyle readStyle = CharacterStyle.wrap(sDraftsStyleSpan);
        formattedDraftString.setSpan(readStyle, 0, formattedDraftString.length(), 0);
        return formattedDraftString;
    }

    private static void clearResourceCache() {
        sDraftSingularString = null;
    }
}
