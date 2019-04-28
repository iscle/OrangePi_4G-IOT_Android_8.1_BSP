/**
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

package com.android.mail.ui;

import android.content.Context;
import android.support.v4.text.TextUtilsCompat;
import android.support.v4.view.ViewCompat;

import com.android.mail.R;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;
import com.google.common.annotations.VisibleForTesting;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Renders data into very simple string-substitution HTML templates for conversation view.
 */
public class HtmlConversationTemplates extends AbstractHtmlTemplates {

    /**
     * Prefix applied to a message id for use as a div id
     */
    public static final String MESSAGE_PREFIX = "m";
    public static final int MESSAGE_PREFIX_LENGTH = MESSAGE_PREFIX.length();

    private static final String TAG = LogTag.getLogTag();

    /**
     * Pattern for HTML img tags with a "src" attribute where the value is an absolutely-specified
     * HTTP or HTTPS URL. In other words, these are images with valid URLs that we should munge to
     * prevent WebView from firing bad onload handlers for them. Part of the workaround for
     * b/5522414.
     *
     * Pattern documentation:
     * There are 3 top-level parts of the pattern:
     * 1. required preceding string
     * 2. the literal string "src"
     * 3. required trailing string
     *
     * The preceding string must be an img tag "<img " with intermediate spaces allowed. The
     * trailing whitespace is required.
     * Non-whitespace chars are allowed before "src", but if they are present, they must be followed
     * by another whitespace char. The idea is to allow other attributes, and avoid matching on
     * "src" in a later attribute value as much as possible.
     *
     * The following string must contain "=" and "http", with intermediate whitespace and single-
     * and double-quote allowed in between. The idea is to avoid matching Gmail-hosted relative URLs
     * for inline attachment images of the form "?view=KEYVALUES".
     *
     */
    private static final Pattern sAbsoluteImgUrlPattern = Pattern.compile(
            "(<\\s*img\\s+(?:[^>]*\\s+)?)src(\\s*=[\\s'\"]*http)", Pattern.CASE_INSENSITIVE
                    | Pattern.MULTILINE);
    /**
     * The text replacement for {@link #sAbsoluteImgUrlPattern}. The "src" attribute is set to
     * something inert and not left unset to minimize interactions with existing JS.
     */
    private static final String IMG_URL_REPLACEMENT = "$1src='data:' blocked-src$2";

    private static final String LEFT_TO_RIGHT_TRIANGLE = "\u25B6 ";
    private static final String RIGHT_TO_LEFT_TRIANGLE = "\u25C0 ";

    private static boolean sLoadedTemplates;
    private static String sSuperCollapsed;
    private static String sMessage;
    private static String sConversationUpper;
    private static String sConversationLower;

    public HtmlConversationTemplates(Context context) {
        super(context);

        // The templates are small (~2KB total in ICS MR2), so it's okay to load them once and keep
        // them in memory.
        if (!sLoadedTemplates) {
            sLoadedTemplates = true;
            sSuperCollapsed = readTemplate(R.raw.template_super_collapsed);
            sMessage = readTemplate(R.raw.template_message);
            sConversationUpper = readTemplate(R.raw.template_conversation_upper);
            sConversationLower = readTemplate(R.raw.template_conversation_lower);
        }
    }

    public void appendSuperCollapsedHtml(int firstCollapsed, int blockHeight) {
        if (!mInProgress) {
            throw new IllegalStateException("must call startConversation first");
        }

        append(sSuperCollapsed, firstCollapsed, blockHeight);
    }

    @VisibleForTesting
    static String replaceAbsoluteImgUrls(final String html) {
        return sAbsoluteImgUrlPattern.matcher(html).replaceAll(IMG_URL_REPLACEMENT);
    }

    /**
     * Wrap a given message body string to prevent its contents from flowing out of the current DOM
     * block context.
     *
     */
    public static String wrapMessageBody(String msgBody) {
        // FIXME: this breaks RTL for an as-yet undetermined reason. b/13678928
        // no-op for now.
        return msgBody;

//        final StringBuilder sb = new StringBuilder("<div style=\"display: table-cell;\">");
//        sb.append(msgBody);
//        sb.append("</div>");
//        return sb.toString();
    }

    public void appendMessageHtml(HtmlMessage message, boolean isExpanded,
            boolean safeForImages, int headerHeight, int footerHeight) {

        final String bodyDisplay = isExpanded ? "block" : "none";
        final String expandedClass = isExpanded ? "expanded" : "";
        final String showImagesClass = safeForImages ? "mail-show-images" : "";

        String body = message.getBodyAsHtml();

        /* Work around a WebView bug (5522414) in setBlockNetworkImage that causes img onload event
         * handlers to fire before an image is loaded.
         * WebView will report bad dimensions when revealing inline images with absolute URLs, but
         * we can prevent WebView from ever seeing those images by changing all img "src" attributes
         * into "gm-src" before loading the HTML. Parsing the potentially dirty HTML input is
         * prohibitively expensive with TagSoup, so use a little regular expression instead.
         *
         * To limit the scope of this workaround, only use it on messages that the server claims to
         * have external resources, and even then, only use it on img tags where the src is absolute
         * (i.e. url does not begin with "?"). The existing JavaScript implementation of this
         * attribute swap will continue to handle inline image attachments (they have relative
         * URLs) and any false negatives that the regex misses. This maintains overall security
         * level by not relying solely on the regex.
         */
        if (!safeForImages && message.embedsExternalResources()) {
            body = replaceAbsoluteImgUrls(body);
        }

        append(sMessage,
                getMessageDomId(message),
                expandedClass,
                headerHeight,
                showImagesClass,
                bodyDisplay,
                wrapMessageBody(body),
                bodyDisplay,
                footerHeight
        );
    }

    public String getMessageDomId(HtmlMessage msg) {
        return MESSAGE_PREFIX + msg.getId();
    }

    public String getMessageIdForDomId(String domMessageId) {
        return domMessageId.substring(MESSAGE_PREFIX_LENGTH);
    }

    public void startConversation(int viewportWidth, int sideMargin, int conversationHeaderHeight) {
        if (mInProgress) {
            throw new IllegalStateException(
                    "Should not call start conversation until end conversation has been called");
        }

        reset();
        final String border = Utils.isRunningKitkatOrLater() ?
                "img[blocked-src] { border: 1px solid #CCCCCC; }" : "";
        append(sConversationUpper, viewportWidth, border, sideMargin, conversationHeaderHeight);
        mInProgress = true;
    }

    public String endConversation(int convFooterPx, String docBaseUri, String conversationBaseUri,
            int viewportWidth, int webviewWidth, boolean enableContentReadySignal,
            boolean normalizeMessageWidths, boolean enableMungeTables, boolean enableMungeImages) {
        if (!mInProgress) {
            throw new IllegalStateException("must call startConversation first");
        }

        final String contentReadyClass = enableContentReadySignal ? "initial-load" : "";

        final boolean isRtl = TextUtilsCompat.getLayoutDirectionFromLocale(Locale.getDefault())
                == ViewCompat.LAYOUT_DIRECTION_RTL;
        final String showElided = (isRtl ? RIGHT_TO_LEFT_TRIANGLE : LEFT_TO_RIGHT_TRIANGLE) +
                mContext.getString(R.string.show_elided);
        append(sConversationLower, convFooterPx, contentReadyClass,
                mContext.getString(R.string.hide_elided),
                showElided, docBaseUri, conversationBaseUri, viewportWidth, webviewWidth,
                enableContentReadySignal, normalizeMessageWidths,
                enableMungeTables, enableMungeImages, Utils.isRunningKitkatOrLater(),
                mContext.getString(R.string.forms_are_disabled));

        mInProgress = false;

        LogUtils.d(TAG, "rendered conversation of %d bytes, buffer capacity=%d",
                mBuilder.length() << 1, mBuilder.capacity() << 1);

        return emit();
    }
}
