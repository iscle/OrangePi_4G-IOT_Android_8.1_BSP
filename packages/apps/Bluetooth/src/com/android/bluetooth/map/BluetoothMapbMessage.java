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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

import android.os.Environment;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import com.android.bluetooth.map.BluetoothMapUtils.TYPE;

public abstract class BluetoothMapbMessage {

    protected static String TAG = "BluetoothMapbMessage";
    protected static final boolean D = BluetoothMapService.DEBUG;
    protected static final boolean V = BluetoothMapService.VERBOSE;

    private String mVersionString = "VERSION:1.0";

    public static int INVALID_VALUE = -1;

    protected int mAppParamCharset = BluetoothMapAppParams.INVALID_VALUE_PARAMETER;

    /* BMSG attributes */
    private String mStatus = null; // READ/UNREAD
    protected TYPE mType = null;   // SMS/MMS/EMAIL

    private String mFolder = null;

    /* BBODY attributes */
    private long mPartId = INVALID_VALUE;
    protected String mEncoding = null;
    protected String mCharset = null;
    private String mLanguage = null;

    private int mBMsgLength = INVALID_VALUE;

    private ArrayList<vCard> mOriginator = null;
    private ArrayList<vCard> mRecipient = null;


    public static class vCard {
        /* VCARD attributes */
        private String mVersion;
        private String mName = null;
        private String mFormattedName = null;
        private String[] mPhoneNumbers = {};
        private String[] mEmailAddresses = {};
        private int mEnvLevel = 0;
        private String[] mBtUcis = {};
        private String[] mBtUids = {};

        /**
         * Construct a version 3.0 vCard
         * @param name Structured
         * @param formattedName Formatted name
         * @param phoneNumbers a String[] of phone numbers
         * @param emailAddresses a String[] of email addresses
         * @param the bmessage envelope level (0 is the top/most outer level)
         */
        public vCard(String name, String formattedName, String[] phoneNumbers,
                String[] emailAddresses, int envLevel) {
            this.mEnvLevel = envLevel;
            this.mVersion = "3.0";
            this.mName = name != null ? name : "";
            this.mFormattedName = formattedName != null ? formattedName : "";
            setPhoneNumbers(phoneNumbers);
            if (emailAddresses != null)
                this.mEmailAddresses = emailAddresses;
        }

        /**
         * Construct a version 2.1 vCard
         * @param name Structured name
         * @param phoneNumbers a String[] of phone numbers
         * @param emailAddresses a String[] of email addresses
         * @param the bmessage envelope level (0 is the top/most outer level)
         */
        public vCard(String name, String[] phoneNumbers,
                String[] emailAddresses, int envLevel) {
            this.mEnvLevel = envLevel;
            this.mVersion = "2.1";
            this.mName = name != null ? name : "";
            setPhoneNumbers(phoneNumbers);
            if (emailAddresses != null)
                this.mEmailAddresses = emailAddresses;
        }

        /**
         * Construct a version 3.0 vCard
         * @param name Structured name
         * @param formattedName Formatted name
         * @param phoneNumbers a String[] of phone numbers
         * @param emailAddresses a String[] of email addresses if available, else null
         * @param btUids a String[] of X-BT-UIDs if available, else null
         * @param btUcis a String[] of X-BT-UCIs if available, else null
         */
        public vCard(String name, String formattedName,
                     String[] phoneNumbers,
                     String[] emailAddresses,
                     String[] btUids,
                     String[] btUcis) {
            this.mVersion = "3.0";
            this.mName = (name != null) ? name : "";
            this.mFormattedName = (formattedName != null) ? formattedName : "";
            setPhoneNumbers(phoneNumbers);
            if (emailAddresses != null) {
                this.mEmailAddresses = emailAddresses;
            }
            if (btUcis != null) {
                this.mBtUcis = btUcis;
            }
        }

        /**
         * Construct a version 2.1 vCard
         * @param name Structured Name
         * @param phoneNumbers a String[] of phone numbers
         * @param emailAddresses a String[] of email addresses
         */
        public vCard(String name, String[] phoneNumbers, String[] emailAddresses) {
            this.mVersion = "2.1";
            this.mName = name != null ? name : "";
            setPhoneNumbers(phoneNumbers);
            if (emailAddresses != null)
                this.mEmailAddresses = emailAddresses;
        }

        private void setPhoneNumbers(String[] numbers) {
            if(numbers != null && numbers.length > 0) {
                mPhoneNumbers = new String[numbers.length];
                for(int i = 0, n = numbers.length; i < n; i++){
                    String networkNumber = PhoneNumberUtils.extractNetworkPortion(numbers[i]);
                    /* extractNetworkPortion can return N if the number is a service
                     * "number" = a string with the a name in (i.e. "Some-Tele-company" would
                     * return N because of the N in compaNy)
                     * Hence we need to check if the number is actually a string with alpha chars.
                     * */
                    String strippedNumber = PhoneNumberUtils.stripSeparators(numbers[i]);
                    Boolean alpha = false;
                    if(strippedNumber != null){
                        alpha = strippedNumber.matches("[0-9]*[a-zA-Z]+[0-9]*");
                    }
                    if(networkNumber != null && networkNumber.length() > 1 && !alpha) {
                        mPhoneNumbers[i] = networkNumber;
                    } else {
                        mPhoneNumbers[i] = numbers[i];
                    }
                }
            }
        }

        public String getFirstPhoneNumber() {
            if(mPhoneNumbers.length > 0) {
                return mPhoneNumbers[0];
            } else
                return null;
        }

        public int getEnvLevel() {
            return mEnvLevel;
        }

        public String getName() {
            return mName;
        }

        public String getFirstEmail() {
            if(mEmailAddresses.length > 0) {
                return mEmailAddresses[0];
            } else
                return null;
        }
        public String getFirstBtUci() {
            if(mBtUcis.length > 0) {
                return mBtUcis[0];
            } else
                return null;
        }

        public String getFirstBtUid() {
            if(mBtUids.length > 0) {
                return mBtUids[0];
            } else
                return null;
        }

        public void encode(StringBuilder sb)
        {
            sb.append("BEGIN:VCARD").append("\r\n");
            sb.append("VERSION:").append(mVersion).append("\r\n");
            if(mVersion.equals("3.0") && mFormattedName != null)
            {
                sb.append("FN:").append(mFormattedName).append("\r\n");
            }
            if (mName != null)
                sb.append("N:").append(mName).append("\r\n");
            for(String phoneNumber : mPhoneNumbers)
            {
                sb.append("TEL:").append(phoneNumber).append("\r\n");
            }
            for(String emailAddress : mEmailAddresses)
            {
                sb.append("EMAIL:").append(emailAddress).append("\r\n");
            }
            for(String btUid : mBtUids)
            {
                sb.append("X-BT-UID:").append(btUid).append("\r\n");
            }
            for(String btUci : mBtUcis)
            {
                sb.append("X-BT-UCI:").append(btUci).append("\r\n");
            }
            sb.append("END:VCARD").append("\r\n");
        }

        /**
         * Parse a vCard from a BMgsReader, where a line containing "BEGIN:VCARD"
         * have just been read.
         * @param reader
         * @param envLevel
         * @return
         */
        public static vCard parseVcard(BMsgReader reader, int envLevel) {
            String formattedName = null;
            String name = null;
            ArrayList<String> phoneNumbers = null;
            ArrayList<String> emailAddresses = null;
            ArrayList<String> btUids = null;
            ArrayList<String> btUcis = null;
            String[] parts;
            String line = reader.getLineEnforce();

            while(!line.contains("END:VCARD")){
                line = line.trim();
                if(line.startsWith("N:")){
                    parts = line.split("[^\\\\]:"); // Split on "un-escaped" ':'
                    if(parts.length == 2) {
                        name = parts[1];
                    } else
                        name = "";
                }
                else if(line.startsWith("FN:")){
                    parts = line.split("[^\\\\]:"); // Split on "un-escaped" ':'
                    if(parts.length == 2) {
                        formattedName = parts[1];
                    } else
                        formattedName = "";
                }
                else if(line.startsWith("TEL:")){
                    parts = line.split("[^\\\\]:"); // Split on "un-escaped" ':'
                    if(parts.length == 2) {
                        String[] subParts = parts[1].split("[^\\\\];");
                        if(phoneNumbers == null)
                            phoneNumbers = new ArrayList<String>(1);
                        // only keep actual phone number
                        phoneNumbers.add(subParts[subParts.length-1]);
                    } else {}
                        // Empty phone number - ignore
                }
                else if(line.startsWith("EMAIL:")){
                    parts = line.split("[^\\\\]:"); // Split on "un-escaped" :
                    if(parts.length == 2) {
                        String[] subParts = parts[1].split("[^\\\\];");
                        if(emailAddresses == null)
                            emailAddresses = new ArrayList<String>(1);
                        // only keep actual email address
                        emailAddresses.add(subParts[subParts.length-1]);
                    } else {}
                        // Empty email address entry - ignore
                }
                else if(line.startsWith("X-BT-UCI:")){
                    parts = line.split("[^\\\\]:"); // Split on "un-escaped" :
                    if(parts.length == 2) {
                        String[] subParts = parts[1].split("[^\\\\];");
                        if(btUcis == null)
                            btUcis = new ArrayList<String>(1);
                        btUcis.add(subParts[subParts.length-1]); // only keep actual UCI
                    } else {}
                        // Empty UCIentry - ignore
                }
                else if(line.startsWith("X-BT-UID:")){
                    parts = line.split("[^\\\\]:"); // Split on "un-escaped" :
                    if(parts.length == 2) {
                        String[] subParts = parts[1].split("[^\\\\];");
                        if(btUids == null)
                            btUids = new ArrayList<String>(1);
                        btUids.add(subParts[subParts.length-1]); // only keep actual UID
                    } else {}
                        // Empty UID entry - ignore
                }


                line = reader.getLineEnforce();
            }
            return new vCard(name, formattedName,
                    phoneNumbers == null?
                            null : phoneNumbers.toArray(new String[phoneNumbers.size()]),
                    emailAddresses == null ?
                            null : emailAddresses.toArray(new String[emailAddresses.size()]),
                    envLevel);
        }
    };

    private static class BMsgReader {
        InputStream mInStream;
        public BMsgReader(InputStream is)
        {
            this.mInStream = is;
        }

        private byte[] getLineAsBytes() {
            int readByte;

            /* TODO: Actually the vCard spec. allows to break lines by using a newLine
             * followed by a white space character(space or tab). Not sure this is a good idea to
             * implement as the Bluetooth MAP spec. illustrates vCards using tab alignment,
             * hence actually showing an invalid vCard format...
             * If we read such a folded line, the folded part will be skipped in the parser
             * UPDATE: Check if we actually do unfold before parsing the input stream
             */

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try {
                while ((readByte = mInStream.read()) != -1) {
                    if (readByte == '\r') {
                        if ((readByte = mInStream.read()) != -1 && readByte == '\n') {
                            if(output.size() == 0)
                                continue; /* Skip empty lines */
                            else
                                break;
                        } else {
                            output.write('\r');
                        }
                    } else if (readByte == '\n' && output.size() == 0) {
                        /* Empty line - skip */
                        continue;
                    }

                    output.write(readByte);
                }
            } catch (IOException e) {
                Log.w(TAG, e);
                return null;
            }
            return output.toByteArray();
        }

        /**
         * Read a line of text from the BMessage.
         * @return the next line of text, or null at end of file, or if UTF-8 is not supported.
         */
        public String getLine() {
            try {
                byte[] line = getLineAsBytes();
                if (line.length == 0)
                    return null;
                else
                    return new String(line, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                Log.w(TAG, e);
                return null;
            }
        }

        /**
         * same as getLine(), but throws an exception, if we run out of lines.
         * Use this function when ever more lines are needed for the bMessage to be complete.
         * @return the next line
         */
        public String getLineEnforce() {
        String line = getLine();
        if (line == null)
            throw new IllegalArgumentException("Bmessage too short");

        return line;
        }


        /**
         * Reads a line from the InputStream, and examines if the subString
         * matches the line read.
         * @param subString
         * The string to match against the line.
         * @throws IllegalArgumentException
         * If the expected substring is not found.
         *
         */
        public void expect(String subString) throws IllegalArgumentException{
            String line = getLine();
            if(line == null || subString == null){
                throw new IllegalArgumentException("Line or substring is null");
            }else if(!line.toUpperCase().contains(subString.toUpperCase()))
                throw new IllegalArgumentException("Expected \"" + subString + "\" in: \""
                                                    + line + "\"");
        }

        /**
         * Same as expect(String), but with two strings.
         * @param subString
         * @param subString2
         * @throws IllegalArgumentException
         * If one or all of the strings are not found.
         */
        public void expect(String subString, String subString2) throws IllegalArgumentException{
            String line = getLine();
            if(!line.toUpperCase().contains(subString.toUpperCase()))
                throw new IllegalArgumentException("Expected \"" + subString + "\" in: \""
                                                   + line + "\"");
            if(!line.toUpperCase().contains(subString2.toUpperCase()))
                throw new IllegalArgumentException("Expected \"" + subString + "\" in: \""
                                                   + line + "\"");
        }

        /**
         * Read a part of the bMessage as raw data.
         * @param length the number of bytes to read
         * @return the byte[] containing the number of bytes or null if an error occurs or EOF is
         * reached before length bytes have been read.
         */
        public byte[] getDataBytes(int length) {
            byte[] data = new byte[length];
            try {
                int bytesRead;
                int offset=0;
                while ((bytesRead = mInStream.read(data, offset, length-offset))
                                 != (length - offset)) {
                    if(bytesRead == -1)
                        return null;
                    offset += bytesRead;
                }
            } catch (IOException e) {
                Log.w(TAG, e);
                return null;
            }
            return data;
        }
    };

    public BluetoothMapbMessage(){

    }

    public String getVersionString() {
        return mVersionString;
    }
    /**
     * Set the version string for VCARD
     * @param version the actual number part of the version string i.e. 1.0
     * */
    public void setVersionString(String version) {
        this.mVersionString = "VERSION:"+version;
    }

    public static BluetoothMapbMessage parse(InputStream bMsgStream,
                                             int appParamCharset) throws IllegalArgumentException{
        BMsgReader reader;
        String line = "";
        BluetoothMapbMessage newBMsg = null;
        boolean status = false;
        boolean statusFound = false;
        TYPE type = null;
        String folder = null;

        /* This section is used for debug. It will write the incoming message to a file on the
         * SD-card, hence should only be used for test/debug.
         * If an error occurs, it will result in a OBEX_HTTP_PRECON_FAILED to be send to the client,
         * even though the message might be formatted correctly, hence only enable this code for
         * test. */
        if(V) {
            /* Read the entire stream into a file on the SD card*/
            File sdCard = Environment.getExternalStorageDirectory();
            File dir = new File (sdCard.getAbsolutePath() + "/bluetooth/log/");
            dir.mkdirs();
            File file = new File(dir, "receivedBMessage.txt");
            FileOutputStream outStream = null;
            boolean failed = false;
            int writtenLen = 0;

            try {
                /* overwrite if it does already exist */
                outStream = new FileOutputStream(file, false);

                byte[] buffer = new byte[4*1024];
                int len = 0;
                while ((len = bMsgStream.read(buffer)) > 0) {
                    outStream.write(buffer, 0, len);
                    writtenLen += len;
                }
            } catch (FileNotFoundException e) {
                Log.e(TAG,"Unable to create output stream",e);
            } catch (IOException e) {
                Log.e(TAG,"Failed to copy the received message",e);
                if(writtenLen != 0)
                    failed = true; /* We failed to write the complete file,
                                      hence the received message is lost... */
            } finally {
                if(outStream != null)
                    try {
                        outStream.close();
                    } catch (IOException e) {
                    }
            }

            /* Return if we corrupted the incoming bMessage. */
            if(failed) {
                throw new IllegalArgumentException(); /* terminate this function with an error. */
            }

            if (outStream == null) {
                /* We failed to create the log-file, just continue using the original bMsgStream. */
            } else {
                /* overwrite the bMsgStream using the file written to the SD-Card */
                try {
                    bMsgStream.close();
                } catch (IOException e) {
                    /* Ignore if we cannot close the stream. */
                }
                /* Open the file and overwrite bMsgStream to read from the file */
                try {
                    bMsgStream = new FileInputStream(file);
                } catch (FileNotFoundException e) {
                    Log.e(TAG,"Failed to open the bMessage file", e);
                    /* terminate this function with an error */
                    throw new IllegalArgumentException();
                }
            }
            Log.i(TAG, "The incoming bMessage have been dumped to " + file.getAbsolutePath());
        } /* End of if(V) log-section */

        reader = new BMsgReader(bMsgStream);
        reader.expect("BEGIN:BMSG");
        reader.expect("VERSION");

        line = reader.getLineEnforce();
        // Parse the properties - which end with either a VCARD or a BENV
        while(!line.contains("BEGIN:VCARD") && !line.contains("BEGIN:BENV")) {
            if(line.contains("STATUS")){
                String arg[] = line.split(":");
                if (arg != null && arg.length == 2) {
                    if (arg[1].trim().equals("READ")) {
                        status = true;
                    } else if (arg[1].trim().equals("UNREAD")) {
                        status =false;
                    } else {
                        throw new IllegalArgumentException("Wrong value in 'STATUS': " + arg[1]);
                    }
                } else {
                    throw new IllegalArgumentException("Missing value for 'STATUS': " + line);
                }
            }
            if(line.contains("EXTENDEDDATA")){
                String arg[] = line.split(":");
                if (arg != null && arg.length == 2) {
                    String value = arg[1].trim();
                    //FIXME what should we do with this
                    Log.i(TAG,"We got extended data with: "+value);
                }
            }
            if(line.contains("TYPE")) {
                String arg[] = line.split(":");
                if (arg != null && arg.length == 2) {
                    String value = arg[1].trim();
                    /* Will throw IllegalArgumentException if value is wrong */
                    type = TYPE.valueOf(value);
                    if(appParamCharset == BluetoothMapAppParams.CHARSET_NATIVE
                            && type != TYPE.SMS_CDMA && type != TYPE.SMS_GSM) {
                        throw new IllegalArgumentException("Native appParamsCharset "
                                                             +"only supported for SMS");
                    }
                    switch(type) {
                    case SMS_CDMA:
                    case SMS_GSM:
                        newBMsg = new BluetoothMapbMessageSms();
                        break;
                    case MMS:
                        newBMsg = new BluetoothMapbMessageMime();
                        break;
                    case EMAIL:
                        newBMsg = new BluetoothMapbMessageEmail();
                        break;
                    case IM:
                        newBMsg = new BluetoothMapbMessageMime();
                        break;
                    default:
                        break;
                    }
                } else {
                    throw new IllegalArgumentException("Missing value for 'TYPE':" + line);
                }
            }
            if(line.contains("FOLDER")) {
                String[] arg = line.split(":");
                if (arg != null && arg.length == 2) {
                    folder = arg[1].trim();
                }
                // This can be empty for push message - hence ignore if there is no value
            }
            line = reader.getLineEnforce();
        }
        if(newBMsg == null)
            throw new IllegalArgumentException("Missing bMessage TYPE: "+
                                                    "- unable to parse body-content");
        newBMsg.setType(type);
        newBMsg.mAppParamCharset = appParamCharset;
        if(folder != null)
            newBMsg.setCompleteFolder(folder);
        if(statusFound)
            newBMsg.setStatus(status);

        // Now check for originator VCARDs
        while(line.contains("BEGIN:VCARD")){
            if(D) Log.d(TAG,"Decoding vCard");
            newBMsg.addOriginator(vCard.parseVcard(reader,0));
            line = reader.getLineEnforce();
        }
        if(line.contains("BEGIN:BENV")) {
            newBMsg.parseEnvelope(reader, 0);
        } else
            throw new IllegalArgumentException("Bmessage has no BEGIN:BENV - line:" + line);

        /* TODO: Do we need to validate the END:* tags? They are only needed if someone puts
         *        additional info below the END:MSG - in which case we don't handle it.
         *        We need to parse the message based on the length field, to ensure MAP 1.0
         *        compatibility, since this spec. do not suggest to escape the end-tag if it
         *        occurs inside the message text.
         */

        try {
            bMsgStream.close();
        } catch (IOException e) {
            /* Ignore if we cannot close the stream. */
        }

        return newBMsg;
    }

    private void parseEnvelope(BMsgReader reader, int level) {
        String line;
        line = reader.getLineEnforce();
        if(D) Log.d(TAG,"Decoding envelope level " + level);

       while(line.contains("BEGIN:VCARD")){
           if(D) Log.d(TAG,"Decoding recipient vCard level " + level);
            if(mRecipient == null)
                mRecipient = new ArrayList<vCard>(1);
            mRecipient.add(vCard.parseVcard(reader, level));
            line = reader.getLineEnforce();
        }
        if(line.contains("BEGIN:BENV")) {
            if(D) Log.d(TAG,"Decoding nested envelope");
            parseEnvelope(reader, ++level); // Nested BENV
        }
        if(line.contains("BEGIN:BBODY")){
            if(D) Log.d(TAG,"Decoding bbody");
            parseBody(reader);
        }
    }

    private void parseBody(BMsgReader reader) {
        String line;
        line = reader.getLineEnforce();
        parseMsgInit();
        while(!line.contains("END:")) {
            if(line.contains("PARTID:")) {
                String arg[] = line.split(":");
                if (arg != null && arg.length == 2) {
                    try {
                    mPartId = Long.parseLong(arg[1].trim());
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Wrong value in 'PARTID': " + arg[1]);
                    }
                } else {
                    throw new IllegalArgumentException("Missing value for 'PARTID': " + line);
                }
            }
            else if(line.contains("ENCODING:")) {
                String arg[] = line.split(":");
                if (arg != null && arg.length == 2) {
                    mEncoding = arg[1].trim();
                    // If needed validation will be done when the value is used
                } else {
                    throw new IllegalArgumentException("Missing value for 'ENCODING': " + line);
                }
            }
            else if(line.contains("CHARSET:")) {
                String arg[] = line.split(":");
                if (arg != null && arg.length == 2) {
                    mCharset = arg[1].trim();
                    // If needed validation will be done when the value is used
                } else {
                    throw new IllegalArgumentException("Missing value for 'CHARSET': " + line);
                }
            }
            else if(line.contains("LANGUAGE:")) {
                String arg[] = line.split(":");
                if (arg != null && arg.length == 2) {
                    mLanguage = arg[1].trim();
                    // If needed validation will be done when the value is used
                } else {
                    // M: no need to throw Exception
                    // throw new IllegalArgumentException("Missing value for 'LANGUAGE': " + line);
                    Log.i(TAG, "LANGUAGE value is missing.");
                    mLanguage = "";
                }
            }
            else if(line.contains("LENGTH:")) {
                String arg[] = line.split(":");
                if (arg != null && arg.length == 2) {
                    try {
                        mBMsgLength = Integer.parseInt(arg[1].trim());
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Wrong value in 'LENGTH': " + arg[1]);
                    }
                } else {
                    throw new IllegalArgumentException("Missing value for 'LENGTH': " + line);
                }
            }
            else if(line.contains("BEGIN:MSG")) {
                if (V) Log.v(TAG, "bMsgLength: " + mBMsgLength);
                if(mBMsgLength == INVALID_VALUE)
                    throw new IllegalArgumentException("Missing value for 'LENGTH'. " +
                            "Unable to read remaining part of the message");

                /* For SMS: Encoding of MSG is always UTF-8 compliant, regardless of any properties,
                   since PDUs are encodes as hex-strings */
                /* PTS has a bug regarding the message length, and sets it 2 bytes too short, hence
                 * using the length field to determine the amount of data to read, might not be the
                 * best solution.
                 * Errata ESR06 section 5.8.12 introduced escaping of END:MSG in the actual message
                 * content, it is now safe to use the END:MSG tag as terminator, and simply ignore
                 * the length field.*/

                // Read until we receive END:MSG as some carkits send bad message lengths
                String data = "";
                String message_line = "";
                while (!message_line.equals("END:MSG")) {
                    data += message_line;
                    message_line = reader.getLineEnforce();
                }

                // The MAP spec says that all END:MSG strings in the body
                // of the message must be escaped upon encoding and the
                // escape removed upon decoding
                data.replaceAll("([/]*)/END\\:MSG", "$1END:MSG");
                data.trim();

                parseMsgPart(data);
            }
            line = reader.getLineEnforce();
        }
    }

    /**
     * Parse the 'message' part of <bmessage-body-content>"
     * @param msgPart
     */
    public abstract void parseMsgPart(String msgPart);
    /**
     * Set initial values before parsing - will be called is a message body is found
     * during parsing.
     */
    public abstract void parseMsgInit();

    public abstract byte[] encode() throws UnsupportedEncodingException;

    public void setStatus(boolean read) {
        if(read)
            this.mStatus = "READ";
        else
            this.mStatus = "UNREAD";
    }

    public void setType(TYPE type) {
        this.mType = type;
    }

    /**
     * @return the type
     */
    public TYPE getType() {
        return mType;
    }

    public void setCompleteFolder(String folder) {
        this.mFolder = folder;
    }

    public void setFolder(String folder) {
        this.mFolder = "telecom/msg/" + folder;
    }

    public String getFolder() {
        return mFolder;
    }


    public void setEncoding(String encoding) {
        this.mEncoding = encoding;
    }

    public ArrayList<vCard> getOriginators() {
        return mOriginator;
    }

    public void addOriginator(vCard originator) {
        if(this.mOriginator == null)
            this.mOriginator = new ArrayList<vCard>();
        this.mOriginator.add(originator);
    }

    /**
     * Add a version 3.0 vCard with a formatted name
     * @param name e.g. Bonde;Casper
     * @param formattedName e.g. "Casper Bonde"
     * @param phoneNumbers
     * @param emailAddresses
     */
    public void addOriginator(String name, String formattedName,
                              String[] phoneNumbers,
                              String[] emailAddresses,
                              String[] btUids,
                              String[] btUcis) {
        if(mOriginator == null)
            mOriginator = new ArrayList<vCard>();
        mOriginator.add(new vCard(name, formattedName, phoneNumbers,
                    emailAddresses, btUids, btUcis));
    }


    public void addOriginator(String[] btUcis, String[] btUids) {
        if(mOriginator == null)
            mOriginator = new ArrayList<vCard>();
        mOriginator.add(new vCard(null,null,null,null,btUids, btUcis));
    }


    /** Add a version 2.1 vCard with only a name.
     *
     * @param name e.g. Bonde;Casper
     * @param phoneNumbers
     * @param emailAddresses
     */
    public void addOriginator(String name, String[] phoneNumbers, String[] emailAddresses) {
        if(mOriginator == null)
            mOriginator = new ArrayList<vCard>();
        mOriginator.add(new vCard(name, phoneNumbers, emailAddresses));
    }

    public ArrayList<vCard> getRecipients() {
        return mRecipient;
    }

    public void setRecipient(vCard recipient) {
        if(this.mRecipient == null)
            this.mRecipient = new ArrayList<vCard>();
        this.mRecipient.add(recipient);
    }
    public void addRecipient(String[] btUcis, String[] btUids) {
        if(mRecipient == null)
            mRecipient = new ArrayList<vCard>();
        mRecipient.add(new vCard(null,null,null,null,btUids, btUcis));
    }
    public void addRecipient(String name, String formattedName,
                             String[] phoneNumbers,
                             String[] emailAddresses,
                             String[] btUids,
                             String[] btUcis) {
        if(mRecipient == null)
            mRecipient = new ArrayList<vCard>();
        mRecipient.add(new vCard(name, formattedName, phoneNumbers,
                    emailAddresses,btUids, btUcis));
    }

    public void addRecipient(String name, String[] phoneNumbers, String[] emailAddresses) {
        if(mRecipient == null)
            mRecipient = new ArrayList<vCard>();
        mRecipient.add(new vCard(name, phoneNumbers, emailAddresses));
    }

    /**
     * Convert a byte[] of data to a hex string representation, converting each nibble to the
     * corresponding hex char.
     * NOTE: There is not need to escape instances of "\r\nEND:MSG" in the binary data represented
     * as a string as only the characters [0-9] and [a-f] is used.
     * @param pduData the byte-array of data.
     * @param scAddressData the byte-array of the encoded sc-Address.
     * @return the resulting string.
     */
    protected String encodeBinary(byte[] pduData, byte[] scAddressData) {
        StringBuilder out = new StringBuilder((pduData.length + scAddressData.length)*2);
        for(int i = 0; i < scAddressData.length; i++) {
            out.append(Integer.toString((scAddressData[i] >> 4) & 0x0f,16)); // MS-nibble first
            out.append(Integer.toString( scAddressData[i]       & 0x0f,16));
        }
        for(int i = 0; i < pduData.length; i++) {
            out.append(Integer.toString((pduData[i] >> 4) & 0x0f,16)); // MS-nibble first
            out.append(Integer.toString( pduData[i]       & 0x0f,16));
            /*out.append(Integer.toHexString(data[i]));*/ /* This is the same as above, but does not
                                                           * include the needed 0's
                                                           * e.g. it converts the value 3 to "3"
                                                           * and not "03" */
        }
        return out.toString();
    }

    /**
     * Decodes a binary hex-string encoded UTF-8 string to the represented binary data set.
     * @param data The string representation of the data - must have an even number of characters.
     * @return the byte[] represented in the data.
     */
    protected byte[] decodeBinary(String data) {
        byte[] out = new byte[data.length()/2];
        String value;
        if(D) Log.d(TAG,"Decoding binary data: START:" + data + ":END");
        for(int i = 0, j = 0, n = out.length; i < n; i++)
        {
            value = data.substring(j++, ++j);
            // same as data.substring(2*i, 2*i+1+1) - substring() uses end-1 for last index
            out[i] = (byte)(Integer.valueOf(value, 16) & 0xff);
        }
        if(D) {
            StringBuilder sb = new StringBuilder(out.length);
            for(int i = 0, n = out.length; i < n; i++)
            {
                sb.append(String.format("%02X",out[i] & 0xff));
            }
            Log.d(TAG,"Decoded binary data: START:" + sb.toString() + ":END");
        }
        return out;
    }

    public byte[] encodeGeneric(ArrayList<byte[]> bodyFragments) throws UnsupportedEncodingException
    {
        StringBuilder sb = new StringBuilder(256);
        byte[] msgStart, msgEnd;
        sb.append("BEGIN:BMSG").append("\r\n");

        sb.append(mVersionString).append("\r\n");
        sb.append("STATUS:").append(mStatus).append("\r\n");
        sb.append("TYPE:").append(mType.name()).append("\r\n");
        if(mFolder.length() > 512)
            sb.append("FOLDER:").append(
                    mFolder.substring(mFolder.length()-512, mFolder.length())).append("\r\n");
        else
            sb.append("FOLDER:").append(mFolder).append("\r\n");
        if(!mVersionString.contains("1.0")){
            sb.append("EXTENDEDDATA:").append("\r\n");
        }
        if(mOriginator != null){
            for(vCard element : mOriginator)
                element.encode(sb);
        }
        /* If we need the three levels of env. at some point - we do have a level in the
         *  vCards that could be used to determine the levels of the envelope.
         */

        sb.append("BEGIN:BENV").append("\r\n");
        if(mRecipient != null){
            for(vCard element : mRecipient) {
                if(V) Log.v(TAG, "encodeGeneric: recipient email" + element.getFirstEmail());
                element.encode(sb);
            }
        }
        sb.append("BEGIN:BBODY").append("\r\n");
        if(mEncoding != null && mEncoding != "")
            sb.append("ENCODING:").append(mEncoding).append("\r\n");
        if(mCharset != null && mCharset != "")
            sb.append("CHARSET:").append(mCharset).append("\r\n");


        int length = 0;
        /* 22 is the length of the 'BEGIN:MSG' and 'END:MSG' + 3*CRLF */
        for (byte[] fragment : bodyFragments) {
            length += fragment.length + 22;
        }
        sb.append("LENGTH:").append(length).append("\r\n");

        // Extract the initial part of the bMessage string
        msgStart = sb.toString().getBytes("UTF-8");

        sb = new StringBuilder(31);
        sb.append("END:BBODY").append("\r\n");
        sb.append("END:BENV").append("\r\n");
        sb.append("END:BMSG").append("\r\n");

        msgEnd = sb.toString().getBytes("UTF-8");

        try {

            ByteArrayOutputStream stream = new ByteArrayOutputStream(
                                                       msgStart.length + msgEnd.length + length);
            stream.write(msgStart);

            for (byte[] fragment : bodyFragments) {
                stream.write("BEGIN:MSG\r\n".getBytes("UTF-8"));
                stream.write(fragment);
                stream.write("\r\nEND:MSG\r\n".getBytes("UTF-8"));
            }
            stream.write(msgEnd);

            if(V) Log.v(TAG,stream.toString("UTF-8"));
            return stream.toByteArray();
        } catch (IOException e) {
            Log.w(TAG,e);
            return null;
        }
    }
}
