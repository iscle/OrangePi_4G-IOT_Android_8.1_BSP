/**
 * Copyright (c) 2014, Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mail.utils;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.QuoteSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;

import com.android.mail.analytics.AnalyticsTimer;
import com.google.android.mail.common.base.CharMatcher;
import com.google.android.mail.common.html.parser.HTML;
import com.google.android.mail.common.html.parser.HTML4;
import com.google.android.mail.common.html.parser.HtmlDocument;
import com.google.android.mail.common.html.parser.HtmlTree;
import com.google.common.collect.Lists;

import java.util.LinkedList;

public class HtmlUtils {

    static final String LOG_TAG = LogTag.getLogTag();

    /**
     * Use our custom SpannedConverter to process the HtmlNode results from HtmlTree.
     * @param html
     * @return processed HTML as a Spanned
     */
    public static Spanned htmlToSpan(String html, HtmlTree.ConverterFactory factory) {
        AnalyticsTimer.getInstance().trackStart(AnalyticsTimer.COMPOSE_HTML_TO_SPAN);
        // Get the html "tree"
        final HtmlTree htmlTree = com.android.mail.utils.Utils.getHtmlTree(html);
        htmlTree.setConverterFactory(factory);
        final Spanned spanned = htmlTree.getSpanned();
        AnalyticsTimer.getInstance().logDuration(AnalyticsTimer.COMPOSE_HTML_TO_SPAN, true,
                "compose", "html_to_span", null);
        LogUtils.i(LOG_TAG, "htmlToSpan completed, input: %d, result: %d", html.length(),
                spanned.length());
        return spanned;
    }

    /**
     * Class that handles converting the html into a Spanned.
     * This class will only handle a subset of the html tags. Below is the full list:
     *   - bold
     *   - italic
     *   - underline
     *   - font size
     *   - font color
     *   - font face
     *   - a
     *   - blockquote
     *   - p
     *   - div
     */
    public static class SpannedConverter implements HtmlTree.Converter<Spanned> {
        // Pinto normal text size is 2 while normal for AbsoluteSizeSpan is 12.
        // So 6 seems to be the magic number here. Html.toHtml also uses 6 as divider.
        private static final int WEB_TO_ANDROID_SIZE_MULTIPLIER = 6;

        protected final SpannableStringBuilder mBuilder = new SpannableStringBuilder();
        private final LinkedList<TagWrapper> mSeenTags = Lists.newLinkedList();

        private final HtmlTree.DefaultPlainTextConverter mTextConverter =
                new HtmlTree.DefaultPlainTextConverter();
        private int mTextConverterIndex = 0;

        @Override
        public void addNode(HtmlDocument.Node n, int nodeNum, int endNum) {
            // Feed it into the plain text converter
            mTextConverter.addNode(n, nodeNum, endNum);
            if (n instanceof HtmlDocument.Tag) {
                handleStart((HtmlDocument.Tag) n);
            } else if (n instanceof HtmlDocument.EndTag) {
                handleEnd((HtmlDocument.EndTag) n);
            }
            appendPlainTextFromConverter();
        }

        private void appendPlainTextFromConverter() {
            String textString = mTextConverter.getObject();
            if (textString.length() > mTextConverterIndex) {
                mBuilder.append(textString.substring(mTextConverterIndex));
                mTextConverterIndex = textString.length();
            }
        }

        /**
         * Helper function to handle start tag
         */
        protected void handleStart(HtmlDocument.Tag tag) {
            if (!tag.isSelfTerminating()) {
                // Add to the stack of tags needing closing tag
                mSeenTags.push(new TagWrapper(tag, mBuilder.length()));
            }
        }

        /**
         * Helper function to handle end tag
         */
        protected void handleEnd(HtmlDocument.EndTag tag) {
            TagWrapper lastSeen;
            HTML.Element element = tag.getElement();
            while ((lastSeen = mSeenTags.poll()) != null && lastSeen.tag.getElement() != null &&
                    !lastSeen.tag.getElement().equals(element)) { }

            // Misformatted html, just ignore this tag
            if (lastSeen == null) {
                return;
            }

            Object marker = null;
            if (HTML4.B_ELEMENT.equals(element)) {
                // BOLD
                marker = new StyleSpan(Typeface.BOLD);
            } else if (HTML4.I_ELEMENT.equals(element)) {
                // ITALIC
                marker = new StyleSpan(Typeface.ITALIC);
            } else if (HTML4.U_ELEMENT.equals(element)) {
                // UNDERLINE
                marker = new UnderlineSpan();
            } else if (HTML4.A_ELEMENT.equals(element)) {
                // A HREF
                HtmlDocument.TagAttribute attr = lastSeen.tag.getAttribute(HTML4.HREF_ATTRIBUTE);
                // Ignore this tag if it doesn't have a link
                if (attr == null) {
                    return;
                }
                marker = new URLSpan(attr.getValue());
            } else if (HTML4.BLOCKQUOTE_ELEMENT.equals(element)) {
                // BLOCKQUOTE
                marker = new QuoteSpan();
            } else if (HTML4.FONT_ELEMENT.equals(element)) {
                // FONT SIZE/COLOR/FACE, since this can insert more than one span
                // we special case it and return
                handleFont(lastSeen);
            }

            final int start = lastSeen.startIndex;
            final int end = mBuilder.length();
            if (marker != null && start != end) {
                mBuilder.setSpan(marker, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        /**
         * Helper function to handle end font tags
         */
        private void handleFont(TagWrapper wrapper) {
            final int start = wrapper.startIndex;
            final int end = mBuilder.length();

            // check font color
            HtmlDocument.TagAttribute attr = wrapper.tag.getAttribute(HTML4.COLOR_ATTRIBUTE);
            if (attr != null) {
                int c = Color.parseColor(attr.getValue());
                if (c != -1) {
                    mBuilder.setSpan(new ForegroundColorSpan(c | 0xFF000000), start, end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }

            // check font size
            attr = wrapper.tag.getAttribute(HTML4.SIZE_ATTRIBUTE);
            if (attr != null) {
                int i = Integer.parseInt(attr.getValue());
                if (i != -1) {
                    mBuilder.setSpan(new AbsoluteSizeSpan(i * WEB_TO_ANDROID_SIZE_MULTIPLIER,
                            true /* use dip */), start, end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }

            // check font typeface
            attr = wrapper.tag.getAttribute(HTML4.FACE_ATTRIBUTE);
            if (attr != null) {
                String[] families = attr.getValue().split(",");
                for (String family : families) {
                    mBuilder.setSpan(new TypefaceSpan(family.trim()), start, end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }

        @Override
        public int getPlainTextLength() {
            return mBuilder.length();
        }

        @Override
        public Spanned getObject() {
            return mBuilder;
        }

        private static class TagWrapper {
            final HtmlDocument.Tag tag;
            final int startIndex;

            TagWrapper(HtmlDocument.Tag tag, int startIndex) {
                this.tag = tag;
                this.startIndex = startIndex;
            }
        }
    }
}
