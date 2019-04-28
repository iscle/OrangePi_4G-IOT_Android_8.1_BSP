/*
 * Copyright 2017, The Android Open Source Project
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
package com.android.managedprovisioning.common;

import static android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;

import static com.android.internal.util.Preconditions.checkNotNull;
import static com.android.internal.util.Preconditions.checkStringNotEmpty;

import android.content.Intent;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;

import com.android.managedprovisioning.preprovisioning.WebActivity;

/**
 * Parses HTML text using {@link Html} and sets URL links to be handled by {@link WebActivity}
 */
public class HtmlToSpannedParser {
    private static final int HTML_MODE = Html.FROM_HTML_MODE_COMPACT;

    private final ClickableSpanFactory mClickableSpanFactory;
    private final UrlIntentFactory mUrlIntentFactory;

    /**
     * Default constructor
     *
     * @param clickableSpanFactory Factory of {@link ClickableSpan} objects for urls
     * @param urlIntentFactory Factory of {@link Intent} objects for handling urls
     */
    public HtmlToSpannedParser(ClickableSpanFactory clickableSpanFactory,
            UrlIntentFactory urlIntentFactory) {
        mClickableSpanFactory = checkNotNull(clickableSpanFactory);
        mUrlIntentFactory = checkNotNull(urlIntentFactory);
    }

    /**
     * See {@link Html#fromHtml(String, int)} for caveats regarding limited HTML support
     */
    public Spanned parseHtml(String htmlContent) {
        Spanned spanned = Html.fromHtml(checkStringNotEmpty(htmlContent), HTML_MODE);
        if (spanned == null) {
            return null;
        }

        // Make html <a> tags open WebActivity
        SpannableStringBuilder result = new SpannableStringBuilder(spanned);

        URLSpan[] urlSpans = result.getSpans(0, result.length(), URLSpan.class);
        for (URLSpan urlSpan : urlSpans) {
            Intent intent = mUrlIntentFactory.create(urlSpan.getURL());
            if (intent != null) {
                int spanStart = result.getSpanStart(urlSpan);
                int spanEnd = result.getSpanEnd(urlSpan);
                result.setSpan(mClickableSpanFactory.create(intent), spanStart, spanEnd,
                        SPAN_EXCLUSIVE_EXCLUSIVE);
                result.removeSpan(urlSpan);
            }
        }

        return result;
    }

    /**
     * Allows to specify an intent to handle URLs
     */
    public interface UrlIntentFactory {
        /**
         * Creates an {@link Intent} based on a passed in {@link String} url
         */
        Intent create(String url);
    }
}