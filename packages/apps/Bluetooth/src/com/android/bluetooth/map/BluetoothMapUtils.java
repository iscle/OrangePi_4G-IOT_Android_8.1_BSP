/*
* Copyright (C) 2013 Samsung System LSI
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
package com.android.bluetooth.map;

import android.database.Cursor;
import android.util.Base64;
import android.util.Log;

import com.android.bluetooth.mapapi.BluetoothMapContract;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Various utility methods and generic defines that can be used throughout MAPS
 */
public class BluetoothMapUtils {

    private static final String TAG = "BluetoothMapUtils";
    private static final boolean D = BluetoothMapService.DEBUG;
    private static final boolean V = BluetoothMapService.VERBOSE;
    /* We use the upper 4 bits for the type mask.
     * TODO: When more types are needed, consider just using a number
     *       in stead of a bit to indicate the message type. Then 4
     *       bit can be use for 16 different message types.
     */
    private static final long HANDLE_TYPE_MASK            = (((long)0xff)<<56);
    private static final long HANDLE_TYPE_MMS_MASK        = (((long)0x01)<<56);
    private static final long HANDLE_TYPE_EMAIL_MASK      = (((long)0x02)<<56);
    private static final long HANDLE_TYPE_SMS_GSM_MASK    = (((long)0x04)<<56);
    private static final long HANDLE_TYPE_SMS_CDMA_MASK   = (((long)0x08)<<56);
    private static final long HANDLE_TYPE_IM_MASK         = (((long)0x10)<<56);

    public static final long CONVO_ID_TYPE_SMS_MMS = 1;
    public static final long CONVO_ID_TYPE_EMAIL_IM= 2;

    // MAP supported feature bit - included from MAP Spec 1.2
    static final int MAP_FEATURE_DEFAULT_BITMASK                    = 0x0000001F;

    static final int MAP_FEATURE_NOTIFICATION_REGISTRATION_BIT      = 1 << 0;
    static final int MAP_FEATURE_NOTIFICATION_BIT                   = 1 << 1;
    static final int MAP_FEATURE_BROWSING_BIT                       = 1 << 2;
    static final int MAP_FEATURE_UPLOADING_BIT                      = 1 << 3;
    static final int MAP_FEATURE_DELETE_BIT                         = 1 << 4;
    static final int MAP_FEATURE_INSTANCE_INFORMATION_BIT           = 1 << 5;
    static final int MAP_FEATURE_EXTENDED_EVENT_REPORT_11_BIT       = 1 << 6;
    static final int MAP_FEATURE_EVENT_REPORT_V12_BIT               = 1 << 7;
    static final int MAP_FEATURE_MESSAGE_FORMAT_V11_BIT             = 1 << 8;
    static final int MAP_FEATURE_MESSAGE_LISTING_FORMAT_V11_BIT     = 1 << 9;
    static final int MAP_FEATURE_PERSISTENT_MESSAGE_HANDLE_BIT      = 1 << 10;
    static final int MAP_FEATURE_DATABASE_INDENTIFIER_BIT           = 1 << 11;
    static final int MAP_FEATURE_FOLDER_VERSION_COUNTER_BIT         = 1 << 12;
    static final int MAP_FEATURE_CONVERSATION_VERSION_COUNTER_BIT   = 1 << 13;
    static final int MAP_FEATURE_PARTICIPANT_PRESENCE_CHANGE_BIT    = 1 << 14;
    static final int MAP_FEATURE_PARTICIPANT_CHAT_STATE_CHANGE_BIT  = 1 << 15;

    static final int MAP_FEATURE_PBAP_CONTACT_CROSS_REFERENCE_BIT   = 1 << 16;
    static final int MAP_FEATURE_NOTIFICATION_FILTERING_BIT         = 1 << 17;
    static final int MAP_FEATURE_DEFINED_TIMESTAMP_FORMAT_BIT       = 1 << 18;

    static final String MAP_V10_STR = "1.0";
    static final String MAP_V11_STR = "1.1";
    static final String MAP_V12_STR = "1.2";

    // Event Report versions
    static final int MAP_EVENT_REPORT_V10           = 10; // MAP spec 1.1
    static final int MAP_EVENT_REPORT_V11           = 11; // MAP spec 1.2
    static final int MAP_EVENT_REPORT_V12           = 12; // MAP spec 1.3 'to be' incl. IM

    // Message Format versions
    static final int MAP_MESSAGE_FORMAT_V10         = 10; // MAP spec below 1.3
    static final int MAP_MESSAGE_FORMAT_V11         = 11; // MAP spec 1.3

    // Message Listing Format versions
    static final int MAP_MESSAGE_LISTING_FORMAT_V10 = 10; // MAP spec below 1.3
    static final int MAP_MESSAGE_LISTING_FORMAT_V11 = 11; // MAP spec 1.3

    /**
     * This enum is used to convert from the bMessage type property to a type safe
     * type. Hence do not change the names of the enum values.
     */
    public enum TYPE{
        NONE,
        EMAIL,
        SMS_GSM,
        SMS_CDMA,
        MMS,
        IM;
        private static TYPE[] allValues = values();
        public static TYPE fromOrdinal(int n) {
            if(n < allValues.length)
               return allValues[n];
            return NONE;
        }
    }

    static public String getDateTimeString(long timestamp) {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
        Date date = new Date(timestamp);
        return format.format(date); // Format to YYYYMMDDTHHMMSS local time
    }


    public static void printCursor(Cursor c) {
        if (D) {
            StringBuilder sb = new StringBuilder();
            sb.append("\nprintCursor:\n");
            for(int i = 0; i < c.getColumnCount(); i++) {
                if(c.getColumnName(i).equals(BluetoothMapContract.MessageColumns.DATE) ||
                   c.getColumnName(i).equals(
                           BluetoothMapContract.ConversationColumns.LAST_THREAD_ACTIVITY) ||
                   c.getColumnName(i).equals(BluetoothMapContract.ChatStatusColumns.LAST_ACTIVE) ||
                   c.getColumnName(i).equals(BluetoothMapContract.PresenceColumns.LAST_ONLINE) ){
                    sb.append("  ").append(c.getColumnName(i)).append(" : ").append(
                            getDateTimeString(c.getLong(i))).append("\n");
                } else {
                    sb.append("  ").append(c.getColumnName(i)).append(" : ").append(
                            c.getString(i)).append("\n");
                }
            }
            Log.d(TAG, sb.toString());
        }
    }

    public static String getLongAsString(long v) {
        char[] result = new char[16];
        int v1 = (int) (v & 0xffffffff);
        int v2 = (int) ((v>>32) & 0xffffffff);
        int c;
        for (int i = 0; i < 8; i++) {
            c = v2 & 0x0f;
            c += (c < 10) ? '0' : ('A'-10);
            result[7 - i] = (char) c;
            v2 >>= 4;
            c = v1 & 0x0f;
            c += (c < 10) ? '0' : ('A'-10);
            result[15 - i] = (char)c;
            v1 >>= 4;
        }
        return new String(result);
    }

    /**
     * Converts a hex-string to a long - please mind that Java has no unsigned data types, hence
     * any value passed to this function, which has the upper bit set, will return a negative value.
     * The bitwise content of the variable will however be the same.
     * Will ignore any white-space characters as well as '-' seperators
     * @param valueStr a hexstring - NOTE: shall not contain any "0x" prefix.
     * @return
     * @throws UnsupportedEncodingException if "US-ASCII" charset is not supported,
     * NullPointerException if a null pointer is passed to the function,
     * NumberFormatException if the string contains invalid characters.
     *
     */
    public static long getLongFromString(String valueStr) throws UnsupportedEncodingException {
        if(valueStr == null) throw new NullPointerException();
        if(V) Log.i(TAG, "getLongFromString(): converting: " + valueStr);
        byte[] nibbles;
        nibbles = valueStr.getBytes("US-ASCII");
        if(V) Log.i(TAG, "  byte values: " + Arrays.toString(nibbles));
        byte c;
        int count = 0;
        int length = nibbles.length;
        long value = 0;
        for(int i = 0; i != length; i++) {
            c = nibbles[i];
            if(c >= '0' && c <= '9') {
                c -= '0';
            } else if(c >= 'A' && c <= 'F') {
                c -= ('A'-10);
            } else if(c >= 'a' && c <= 'f') {
                c -= ('a'-10);
            } else if(c <= ' ' || c == '-') {
                if(V)Log.v(TAG, "Skipping c = '" + new String(new byte[]{ (byte)c }, "US-ASCII")
                        + "'");
                continue; // Skip any whitespace and '-' (which is used for UUIDs)
            } else {
                throw new NumberFormatException("Invalid character:" + c);
            }
            value = value << 4; // The last nibble shall not be shifted
            value += c;
            count++;
            if(count > 16) throw new NullPointerException("String to large - count: " + count);
        }
        if(V) Log.i(TAG, "  length: " + count);
        return value;
    }
    private static final int LONG_LONG_LENGTH = 32;
    public static String getLongLongAsString(long vLow, long vHigh) {
        char[] result = new char[LONG_LONG_LENGTH];
        int v1 = (int) (vLow & 0xffffffff);
        int v2 = (int) ((vLow>>32) & 0xffffffff);
        int v3 = (int) (vHigh & 0xffffffff);
        int v4 = (int) ((vHigh>>32) & 0xffffffff);
        int c,d,i;
        // Handle the lower bytes
        for (i = 0; i < 8; i++) {
            c = v2 & 0x0f;
            c += (c < 10) ? '0' : ('A'-10);
            d = v4 & 0x0f;
            d += (d < 10) ? '0' : ('A'-10);
            result[23 - i] = (char) c;
            result[7 - i] = (char) d;
            v2 >>= 4;
            v4 >>= 4;
            c = v1 & 0x0f;
            c += (c < 10) ? '0' : ('A'-10);
            d = v3 & 0x0f;
            d += (d < 10) ? '0' : ('A'-10);
            result[31 - i] = (char)c;
            result[15 - i] = (char)d;
            v1 >>= 4;
            v3 >>= 4;
        }
        // Remove any leading 0's
        for(i = 0; i < LONG_LONG_LENGTH; i++) {
            if(result[i] != '0') {
                break;
            }
        }
        return new String(result, i, LONG_LONG_LENGTH-i);
    }


    /**
     * Convert a Content Provider handle and a Messagetype into a unique handle
     * @param cpHandle content provider handle
     * @param messageType message type (TYPE_MMS/TYPE_SMS_GSM/TYPE_SMS_CDMA/TYPE_EMAIL)
     * @return String Formatted Map Handle
     */
    public static String getMapHandle(long cpHandle, TYPE messageType){
        String mapHandle = "-1";
        /* Avoid NPE for possible "null" value of messageType */
        if(messageType != null) {
            switch(messageType)
            {
                case MMS:
                    mapHandle = getLongAsString(cpHandle | HANDLE_TYPE_MMS_MASK);
                    break;
                case SMS_GSM:
                    mapHandle = getLongAsString(cpHandle | HANDLE_TYPE_SMS_GSM_MASK);
                    break;
                case SMS_CDMA:
                    mapHandle = getLongAsString(cpHandle | HANDLE_TYPE_SMS_CDMA_MASK);
                    break;
                case EMAIL:
                    mapHandle = getLongAsString(cpHandle | HANDLE_TYPE_EMAIL_MASK);
                    break;
                case IM:
                    mapHandle = getLongAsString(cpHandle | HANDLE_TYPE_IM_MASK);
                    break;
                case NONE:
                    break;
                default:
                    throw new IllegalArgumentException("Message type not supported");
            }
        } else {
            if(D)Log.e(TAG," Invalid messageType input");
        }
        return mapHandle;

    }

    /**
     * Convert a Content Provider handle and a Messagetype into a unique handle
     * @param cpHandle content provider handle
     * @param messageType message type (TYPE_MMS/TYPE_SMS_GSM/TYPE_SMS_CDMA/TYPE_EMAIL)
     * @return String Formatted Map Handle
     */
    public static String getMapConvoHandle(long cpHandle, TYPE messageType){
        String mapHandle = "-1";
        switch(messageType)
        {
            case MMS:
            case SMS_GSM:
            case SMS_CDMA:
                mapHandle = getLongLongAsString(cpHandle, CONVO_ID_TYPE_SMS_MMS);
                break;
            case EMAIL:
            case IM:
                mapHandle = getLongLongAsString(cpHandle, CONVO_ID_TYPE_EMAIL_IM);
                break;
            default:
                throw new IllegalArgumentException("Message type not supported");
        }
        return mapHandle;

    }

    /**
     * Convert a handle string the the raw long representation, including the type bit.
     * @param mapHandle the handle string
     * @return the handle value
     */
    static public long getMsgHandleAsLong(String mapHandle){
        return Long.parseLong(mapHandle, 16);
    }
    /**
     * Convert a Map Handle into a content provider Handle
     * @param mapHandle handle to convert from
     * @return content provider handle without message type mask
     */
    static public long getCpHandle(String mapHandle)
    {
        long cpHandle = getMsgHandleAsLong(mapHandle);
        if(D)Log.d(TAG,"-> MAP handle:"+mapHandle);
        /* remove masks as the call should already know what type of message this handle is for */
        cpHandle &= ~HANDLE_TYPE_MASK;
        if(D)Log.d(TAG,"->CP handle:"+cpHandle);

        return cpHandle;
    }

    /**
     * Extract the message type from the handle.
     * @param mapHandle
     * @return
     */
    static public TYPE getMsgTypeFromHandle(String mapHandle) {
        long cpHandle = getMsgHandleAsLong(mapHandle);

        if((cpHandle & HANDLE_TYPE_MMS_MASK) != 0)
            return TYPE.MMS;
        if((cpHandle & HANDLE_TYPE_EMAIL_MASK) != 0)
            return TYPE.EMAIL;
        if((cpHandle & HANDLE_TYPE_SMS_GSM_MASK) != 0)
            return TYPE.SMS_GSM;
        if((cpHandle & HANDLE_TYPE_SMS_CDMA_MASK) != 0)
            return TYPE.SMS_CDMA;
        if((cpHandle & HANDLE_TYPE_IM_MASK) != 0)
            return TYPE.IM;

        throw new IllegalArgumentException("Message type not found in handle string.");
    }

    /**
     * TODO: Is this still needed after changing to another XML encoder? It should escape illegal
     *       characters.
     * Strip away any illegal XML characters, that would otherwise cause the
     * xml serializer to throw an exception.
     * Examples of such characters are the emojis used on Android.
     * @param text The string to validate
     * @return the same string if valid, otherwise a new String stripped for
     * any illegal characters. If a null pointer is passed an empty string will be returned.
     */
    static public String stripInvalidChars(String text) {
        if(text == null) {
            return "";
        }
        char out[] = new char[text.length()];
        int i, o, l;
        for(i=0, o=0, l=text.length(); i<l; i++){
            char c = text.charAt(i);
            if((c >= 0x20 && c <= 0xd7ff) || (c >= 0xe000 && c <= 0xfffd)) {
                out[o++] = c;
            } // Else we skip the character
        }

        if(i==o) {
            return text;
        } else { // We removed some characters, create the new string
            return new String(out,0,o);
        }
    }

    /**
     * Truncate UTF-8 string encoded byte array to desired length
     * @param utf8String String to convert to bytes array h
     * @param length Max length of byte array returned including null termination
     * @return byte array containing valid utf8 characters with max length
     * @throws UnsupportedEncodingException
     */
    static public byte[] truncateUtf8StringToBytearray(String utf8String, int maxLength)
            throws UnsupportedEncodingException {

        byte[] utf8Bytes = new byte[utf8String.length() + 1];
        try {
            System.arraycopy(utf8String.getBytes("UTF-8"), 0,
                             utf8Bytes, 0, utf8String.length());
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG,"truncateUtf8StringToBytearray: getBytes exception ", e);
            throw e;
        }

        if (utf8Bytes.length > maxLength) {
            /* if 'continuation' byte is in place 200,
             * then strip previous bytes until utf-8 start byte is found */
            if ( (utf8Bytes[maxLength - 1] & 0xC0) == 0x80 ) {
                for (int i = maxLength - 2; i >= 0; i--) {
                    if ((utf8Bytes[i] & 0xC0) == 0xC0) {
                        /* first byte in utf-8 character found,
                         * now copy i - 1 bytes to outBytes and add null termination */
                        utf8Bytes = Arrays.copyOf(utf8Bytes, i+1);
                        utf8Bytes[i] = 0;
                        break;
                    }
                }
            } else {
                /* copy bytes to outBytes and null terminate */
                utf8Bytes = Arrays.copyOf(utf8Bytes, maxLength);
                utf8Bytes[maxLength - 1] = 0;
            }
        }
        return utf8Bytes;
    }
    private static Pattern p = Pattern.compile("=\\?(.+?)\\?(.)\\?(.+?(?=\\?=))\\?=");

    /**
     * Method for converting quoted printable og base64 encoded string from headers.
     * @param in the string with encoding
     * @return decoded string if success - else the same string as was as input.
     */
    static public String stripEncoding(String in){
        String str = null;
        if(in.contains("=?") && in.contains("?=")){
            String encoding;
            String charset;
            String encodedText;
            String match;
            Matcher m = p.matcher(in);
            while(m.find()){
                match = m.group(0);
                charset = m.group(1);
                encoding = m.group(2);
                encodedText = m.group(3);
                Log.v(TAG, "Matching:" + match +"\nCharset: "+charset +"\nEncoding : " +encoding
                        + "\nText: " + encodedText);
                if(encoding.equalsIgnoreCase("Q")){
                    //quoted printable
                    Log.d(TAG,"StripEncoding: Quoted Printable string : " + encodedText);
                    str = new String(quotedPrintableToUtf8(encodedText,charset));
                    in = in.replace(match, str);
                }else if(encoding.equalsIgnoreCase("B")){
                    // base64
                    try{

                        Log.d(TAG,"StripEncoding: base64 string : " + encodedText);
                        str = new String(Base64.decode(encodedText.getBytes(charset),
                                Base64.DEFAULT), charset);
                        Log.d(TAG,"StripEncoding: decoded string : " + str);
                        in = in.replace(match, str);
                    }catch(UnsupportedEncodingException e){
                        Log.e(TAG, "stripEncoding: Unsupported charset: " + charset);
                    }catch (IllegalArgumentException e){
                        Log.e(TAG,"stripEncoding: string not encoded as base64: " +encodedText);
                    }
                }else{
                    Log.e(TAG, "stripEncoding: Hit unknown encoding: "+encoding);
                }
            }
        }
        return in;
    }


    /**
     * Convert a quoted-printable encoded string to a UTF-8 string:
     *  - Remove any soft line breaks: "=<CRLF>"
     *  - Convert all "=xx" to the corresponding byte
     * @param text quoted-printable encoded UTF-8 text
     * @return decoded UTF-8 string
     */
    public static byte[] quotedPrintableToUtf8(String text, String charset) {
        byte[] output = new byte[text.length()]; // We allocate for the worst case memory need
        byte[] input = null;
        try {
            input = text.getBytes("US-ASCII");
        } catch (UnsupportedEncodingException e) {
            /* This cannot happen as "US-ASCII" is supported for all Java implementations */ }

        if(input == null){
            return "".getBytes();
        }

        int in, out, stopCnt = input.length-2; // Leave room for peaking the next two bytes

        /* Algorithm:
         *  - Search for token, copying all non token chars
         * */
        for(in=0, out=0; in < stopCnt; in++){
            byte b0 = input[in];
            if(b0 == '=') {
                byte b1 = input[++in];
                byte b2 = input[++in];
                if(b1 == '\r' && b2 == '\n') {
                    continue; // soft line break, remove all tree;
                }
                if(((b1 >= '0' && b1 <= '9') || (b1 >= 'A' && b1 <= 'F')
                        || (b1 >= 'a' && b1 <= 'f'))
                        && ((b2 >= '0' && b2 <= '9') || (b2 >= 'A' && b2 <= 'F')
                        || (b2 >= 'a' && b2 <= 'f'))) {
                    if(V)Log.v(TAG, "Found hex number: " + String.format("%c%c", b1, b2));
                    if(b1 <= '9')       b1 = (byte) (b1 - '0');
                    else if (b1 <= 'F') b1 = (byte) (b1 - 'A' + 10);
                    else if (b1 <= 'f') b1 = (byte) (b1 - 'a' + 10);

                    if(b2 <= '9')       b2 = (byte) (b2 - '0');
                    else if (b2 <= 'F') b2 = (byte) (b2 - 'A' + 10);
                    else if (b2 <= 'f') b2 = (byte) (b2 - 'a' + 10);

                    if(V)Log.v(TAG, "Resulting nibble values: " +
                            String.format("b1=%x b2=%x", b1, b2));

                    output[out++] = (byte)(b1<<4 | b2); // valid hex char, append
                    if(V)Log.v(TAG, "Resulting value: "  + String.format("0x%2x", output[out-1]));
                    continue;
                }
                Log.w(TAG, "Received wrongly quoted printable encoded text. " +
                        "Continuing at best effort...");
                /* If we get a '=' without either a hex value or CRLF following, just add it and
                 * rewind the in counter. */
                output[out++] = b0;
                in -= 2;
                continue;
            } else {
                output[out++] = b0;
                continue;
            }
        }

        // Just add any remaining characters. If they contain any encoding, it is invalid,
        // and best effort would be just to display the characters.
        while (in < input.length) {
            output[out++] = input[in++];
        }

        String result = null;
        // Figure out if we support the charset, else fall back to UTF-8, as this is what
        // the MAP specification suggest to use, and is compatible with US-ASCII.
        if(charset == null){
            charset = "UTF-8";
        } else {
            charset = charset.toUpperCase();
            try {
                if(Charset.isSupported(charset) == false) {
                    charset = "UTF-8";
                }
            } catch (IllegalCharsetNameException e) {
                Log.w(TAG, "Received unknown charset: " + charset + " - using UTF-8.");
                charset = "UTF-8";
            }
        }
        try{
            result = new String(output, 0, out, charset);
        } catch (UnsupportedEncodingException e) {
            /* This cannot happen unless Charset.isSupported() is out of sync with String */
            try{
                result = new String(output, 0, out, "UTF-8");
            } catch (UnsupportedEncodingException e2) {/* This cannot happen */}
        }
        return result.getBytes(); /* return the result as "UTF-8" bytes */
    }

    /**
     * Encodes an array of bytes into an array of quoted-printable 7-bit characters.
     * Unsafe characters are escaped.
     * Simplified version of encoder from QuetedPrintableCodec.java (Apache external)
     *
     * @param bytes
     *                  array of bytes to be encoded
     * @return UTF-8 string containing quoted-printable characters
     */

    private static byte ESCAPE_CHAR = '=';
    private static byte TAB = 9;
    private static byte SPACE = 32;

    public static final String encodeQuotedPrintable(byte[] bytes) {
        if (bytes == null) {
            return null;
        }

        BitSet printable = new BitSet(256);
        // alpha characters
        for (int i = 33; i <= 60; i++) {
            printable.set(i);
        }
        for (int i = 62; i <= 126; i++) {
            printable.set(i);
        }
        printable.set(TAB);
        printable.set(SPACE);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        for (int i = 0; i < bytes.length; i++) {
            int b = bytes[i];
            if (b < 0) {
                b = 256 + b;
            }
            if (printable.get(b)) {
                buffer.write(b);
            } else {
                buffer.write(ESCAPE_CHAR);
                char hex1 = Character.toUpperCase(Character.forDigit((b >> 4) & 0xF, 16));
                char hex2 = Character.toUpperCase(Character.forDigit(b & 0xF, 16));
                buffer.write(hex1);
                buffer.write(hex2);
            }
        }
        try {
            return buffer.toString("UTF-8");
        } catch (UnsupportedEncodingException e) {
            //cannot happen
            return "";
        }
    }

}

