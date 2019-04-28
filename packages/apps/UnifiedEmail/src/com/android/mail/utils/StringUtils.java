package com.android.mail.utils;

import android.support.v4.text.BidiFormatter;

/**
 * A small utility class for working with strings.
 */
public class StringUtils {

    /**
     * Returns a string containing the tokens joined by delimiters.
     * Additionally, each token is first passed through {@link BidiFormatter#unicodeWrap(String)}
     * before appending to the string.
     */
    public static String joinAndBidiFormat(String delimiter, Iterable<String> tokens) {
        return joinAndBidiFormat(delimiter, tokens, BidiFormatter.getInstance());
    }

    /**
     * Returns a string containing the tokens joined by delimiters.
     * Additionally, each token is first passed through {@link BidiFormatter#unicodeWrap(String)}
     * before appending to the string.
     */
    public static String joinAndBidiFormat(
            String delimiter, Iterable<String> tokens, BidiFormatter bidiFormatter) {
        final StringBuilder sb = new StringBuilder();
        boolean firstTime = true;
        for (String token : tokens) {
            if (firstTime) {
                firstTime = false;
            } else {
                sb.append(delimiter);
            }
            sb.append(bidiFormatter.unicodeWrap(token));
        }
        return sb.toString();
    }
}
