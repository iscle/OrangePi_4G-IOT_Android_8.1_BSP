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

package com.android.mail.ui;

import android.content.Context;
import android.content.res.Resources;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Formatter;

/**
 * Abstract class to support common functionality for both
 * {@link com.android.mail.ui.HtmlConversationTemplates} and
 * {@link com.android.mail.print.HtmlPrintTemplates}.
 *
 * Renders data into very simple string-substitution HTML templates.
 *
 * Templates should be UTF-8 encoded HTML with '%s' placeholders to be substituted upon render.
 * Plain-jane string substitution with '%s' is slightly faster than typed substitution.
 */
public abstract class AbstractHtmlTemplates {
    // TODO: refine. too expensive to iterate over cursor and pre-calculate total. so either
    // estimate it, or defer assembly until the end when size is known (deferring increases
    // working set size vs. estimation but is exact).
    private static final int BUFFER_SIZE_CHARS = 64 * 1024;

    protected Context mContext;
    protected Formatter mFormatter;
    protected StringBuilder mBuilder;
    protected boolean mInProgress = false;

    public AbstractHtmlTemplates(Context context) {
        mContext = context;
    }

    public String emit() {
        final String out = mFormatter.toString();
        // release the builder memory ASAP
        mFormatter = null;
        mBuilder = null;
        return out;
    }

    public void reset() {
        mBuilder = new StringBuilder(BUFFER_SIZE_CHARS);
        mFormatter = new Formatter(mBuilder, null /* no localization */);
    }

    protected String readTemplate(int id) throws Resources.NotFoundException {
        final StringBuilder out = new StringBuilder();
        InputStreamReader in = null;
        try {
            try {
                in = new InputStreamReader(
                        mContext.getResources().openRawResource(id), "UTF-8");
                final char[] buf = new char[4096];
                int chars;

                while ((chars=in.read(buf)) > 0) {
                    out.append(buf, 0, chars);
                }

                return out.toString();

            } finally {
                if (in != null) {
                    in.close();
                }
            }
        } catch (IOException e) {
            throw new Resources.NotFoundException("Unable to open template id="
                    + Integer.toHexString(id) + " exception=" + e.getMessage());
        }
    }

    protected void append(String template, Object... args) {
        mFormatter.format(template, args);
    }
}
