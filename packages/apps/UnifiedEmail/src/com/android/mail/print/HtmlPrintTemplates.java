/**
 * Copyright (C) 2013 Google Inc.
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

package com.android.mail.print;

import android.content.Context;
import android.content.res.Resources;

import com.android.mail.R;
import com.android.mail.providers.Conversation;
import com.android.mail.ui.AbstractHtmlTemplates;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;

/**
 * Renders data into very simple string-substitution HTML templates for printing conversations.
 */
public class HtmlPrintTemplates extends AbstractHtmlTemplates {

    private static final String TAG = LogTag.getLogTag();

    private final String mConversationUpper;
    private final String mMessage;
    private final String mConversationLower;
    private final String mConversationLowerNoJs;
    private final String mLogo;

    public HtmlPrintTemplates(Context context) {
        super(context);

        mConversationUpper = readTemplate(R.raw.template_print_conversation_upper);
        mMessage = readTemplate(R.raw.template_print_message);
        mConversationLower = readTemplate(R.raw.template_print_conversation_lower);
        mConversationLowerNoJs = readTemplate(R.raw.template_print_conversation_lower_no_js);
        mLogo = readTemplate(R.raw.logo);
    }

    /**
     * Start building the html for a printed conversation. Can only be called once
     * until {@link #endPrintConversation()} or {@link #endPrintConversationNoJavascript()}
     * is called.
     */
    public void startPrintConversation(String subject, int numMessages) {
        if (mInProgress) {
            throw new IllegalStateException("Should not call startPrintConversation twice");
        }

        reset();

        final Resources res = mContext.getResources();
        final String numMessageString = res.getQuantityString(
                R.plurals.num_messages, numMessages, numMessages);

        final String printedSubject =
                Conversation.getSubjectForDisplay(mContext, null /* badgeText */, subject);

        append(mConversationUpper, mLogo, mContext.getString(R.string.app_name),
                printedSubject, numMessageString);

        mInProgress = true;
    }

    /**
     * Add a message to the html for this printed conversation.
     */
    public void appendMessage(String senderName, String senderAddress, String date,
            String recipients, String bodyHtml, String attachments) {
        append(mMessage, senderName, senderAddress, date, recipients, bodyHtml, attachments);
    }

    /**
     * Adds the end of the printed conversation to the html. NOTE: this method
     * includes JavaScript. If you need a version without JavaScript,
     * use {@link #endPrintConversationNoJavascript()}.<br/><br/>
     *
     * One example where we use JavaScript is to hide quoted text.
     *
     * @return a {@link String} containing the html for the conversation.
     */
    public String endPrintConversation() {
        if (!mInProgress) {
            throw new IllegalStateException("must call startConversation first");
        }

        append(mConversationLower, mContext.getString(R.string.quoted_text_hidden_print));

        mInProgress = false;

        LogUtils.d(TAG, "rendered conversation of %d bytes, buffer capacity=%d",
                mBuilder.length() << 1, mBuilder.capacity() << 1);

        return emit();
    }

    /**
     * Adds the end of the printed conversation to the html. NOTE: this method
     * does not include any JavaScript. If you need a version with JavaScript,
     * use {@link #endPrintConversation()}.
     * @return a {@link String} containing the html for the conversation.
     */
    public String endPrintConversationNoJavascript() {
        if (!mInProgress) {
            throw new IllegalStateException("must call startConversation first");
        }

        append(mConversationLowerNoJs);

        mInProgress = false;

        LogUtils.d(TAG, "rendered conversation of %d bytes, buffer capacity=%d",
                mBuilder.length() << 1, mBuilder.capacity() << 1);

        return emit();
    }
}
