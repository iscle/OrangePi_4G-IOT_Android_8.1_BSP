/*
 * Copyright 2017, The Android Open Source Project
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
package com.android.managedprovisioning.common;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import android.graphics.Color;
import android.support.test.InstrumentationRegistry;
import android.text.Spanned;

import com.android.managedprovisioning.preprovisioning.WebActivity;

import org.junit.Before;
import org.junit.Test;

public class HtmlToSpannedParserTest {
    private static final int SAMPLE_COLOR = Color.MAGENTA;
    private HtmlToSpannedParser mHtmlToSpannedParser;

    @Before
    public void setUp() throws Exception {
        mHtmlToSpannedParser =
                new HtmlToSpannedParser(new ClickableSpanFactory(SAMPLE_COLOR),
                        url -> WebActivity.createIntent(InstrumentationRegistry.getTargetContext(),
                                url, SAMPLE_COLOR));
    }

    @Test(expected = IllegalArgumentException.class)
    public void throwsExceptionForEmptyInputs1() {
        mHtmlToSpannedParser.parseHtml(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void throwsExceptionForEmptyInputs2() {
        mHtmlToSpannedParser.parseHtml("");
    }

    @Test
    public void handlesSimpleText() {
        String inputHtml = "bb\n\ncc\ndd";
        String textRaw = "bb cc dd"; // whitespace stripped out in the process of HTML conversion

        assertRawTextCorrect(inputHtml, textRaw);
    }

    @Test
    public void handlesComplexHtml() {
        String inputHtml = "a <b> b </b> <h1> ch1 </h1> <ol> <li> i1 </li> </ol> e";
        String textRaw = "a b \nch1 \ni1 \ne";

        assertRawTextCorrect(inputHtml, textRaw);
        // TODO: add testing of formatting
    }

    private void assertRawTextCorrect(String inputHtml, String textRaw) {
        Spanned spanned = mHtmlToSpannedParser.parseHtml(inputHtml);
        assertThat(spanned.toString(), equalTo(textRaw));
    }
}