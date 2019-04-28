/*
 * Copyright (C) 2014 Google Inc.
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

package com.android.mail.utils;

import android.text.Spannable;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.view.View;
import android.widget.TextView;

import com.android.mail.text.LinkStyleSpan;

/**
 * Utility class for styling UI.
 */
public class StyleUtils {
    /**
     * Removes any {@link android.text.style.URLSpan}s from the text view and replaces them with a
     * non-underline version {@link LinkStyleSpan} which calls the supplied listener when clicked.
     */
    public static void stripUnderlinesAndLinkUrls(TextView textView,
            View.OnClickListener onClickListener) {
        final Spannable spannable = (Spannable) textView.getText();
        stripUnderlinesAndLinkUrls(spannable, onClickListener);
    }

    /**
     * Removes any {@link android.text.style.URLSpan}s from the Spannable and replaces them with a
     * non-underline version {@link LinkStyleSpan} which calls the supplied listener when clicked.
     */
    public static void stripUnderlinesAndLinkUrls(Spannable input,
            View.OnClickListener onClickListener) {
        final URLSpan[] urls = input.getSpans(0, input.length(), URLSpan.class);

        for (URLSpan span : urls) {
            final int start = input.getSpanStart(span);
            final int end = input.getSpanEnd(span);
            input.removeSpan(span);
            input.setSpan(new LinkStyleSpan(onClickListener), start, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    /**
     * Removes any {@link android.text.style.URLSpan}s from the text view and replaces them with a
     * non-underline version {@link LinkStyleSpan} that does nothing when clicked.
     */
    public static void stripUnderlinesAndUrl(TextView textView) {
        stripUnderlinesAndLinkUrls(textView, null /* onClickListener */);
    }
}
