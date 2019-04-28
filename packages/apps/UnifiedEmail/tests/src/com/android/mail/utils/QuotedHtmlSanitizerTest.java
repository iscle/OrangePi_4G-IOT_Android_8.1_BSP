package com.android.mail.utils;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

/**
 * These test cases verify that the HTML email body is transformed correctly to support toggling
 * the visibility of quoted text.
 */
@SmallTest
public class QuotedHtmlSanitizerTest extends AndroidTestCase {
    /**
     * Random garbage in a class attribute of a div is stripped.
     */
    public void testGarbageDiv() {
        // any random class value is disallowed
        sanitize("<div class=\"garbage\"></div>", "<div></div>");
    }

    /**
     * For Gmail, <div class="gmail_quote"> indicates the block of quoted text.
     */
    public void testGmailQuotedTextDiv() {
        sanitize("<div class=\"gmail_quote\"></div>", "<div class=\"elided-text\"></div>");
    }

    /**
     * For Yahoo, <div class="yahoo_quoted"> indicates the block of quoted text.
     */
    public void testYahooQuotedTextDiv() {
        sanitize("<div class=\"yahoo_quoted\"></div>", "<div class=\"elided-text\"></div>");
    }

    /**
     * For AOL, <div id="AOLMsgPart_RANDOM_GUID"> indicates the block of quoted text.
     */
    public void testAOLQuotedTextDiv() {
        sanitize("<div id=\"AOLMsgPart_1_59da800c-ba5d-45c5-9ff7-29a8264a5bd9\"></div>",
                "<div class=\"elided-text\"></div>");
        sanitize("<div id=\"AOLMsgPart_1_b916b4c7-3047-43a9-b24d-83b7ffd2b9b7\"></div>",
                "<div class=\"elided-text\"></div>");
    }

    private void sanitize(String dirtyHTML, String expectedHTML) {
        final String cleansedHTML = HtmlSanitizer.sanitizeHtml(dirtyHTML);
        assertEquals(expectedHTML, cleansedHTML);
    }
}
