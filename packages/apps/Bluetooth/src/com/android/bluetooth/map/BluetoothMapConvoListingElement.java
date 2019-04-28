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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import android.util.Log;

import com.android.bluetooth.SignedLongLong;
import com.android.bluetooth.map.BluetoothMapUtils.TYPE;
import com.android.internal.util.XmlUtils;

public class BluetoothMapConvoListingElement
    implements Comparable<BluetoothMapConvoListingElement> {

    public static final String XML_TAG_CONVERSATION = "conversation";
    private static final String XML_ATT_LAST_ACTIVITY = "last_activity";
    private static final String XML_ATT_NAME = "name";
    private static final String XML_ATT_ID = "id";
    private static final String XML_ATT_READ = "readstatus";
    private static final String XML_ATT_VERSION_COUNTER = "version_counter";
    private static final String XML_ATT_SUMMARY = "summary";
    private static final String TAG = "BluetoothMapConvoListingElement";
    private static final boolean D = BluetoothMapService.DEBUG;
    private static final boolean V = BluetoothMapService.VERBOSE;

    private SignedLongLong mId = null;
    private String mName = ""; //title of the conversation #REQUIRED, but allowed empty
    private long mLastActivity = -1;
    private boolean mRead = false;
    private boolean mReportRead = false; // TODO: Is this needed? - false means UNKNOWN
    private List<BluetoothMapConvoContactElement> mContacts;
    private long mVersionCounter = -1;
    private int mCursorIndex = 0;
    private TYPE mType = null;
    private String mSummary = null;

 // Used only to keep track of changes to convoListVersionCounter;
    private String mSmsMmsContacts = null;

    public int getCursorIndex() {
        return mCursorIndex;
    }

    public void setCursorIndex(int cursorIndex) {
        this.mCursorIndex = cursorIndex;
        if(D) Log.d(TAG, "setCursorIndex: " + cursorIndex);
    }

    public long getVersionCounter(){
        return mVersionCounter;
    }

    public void setVersionCounter(long vcount){
        if(D) Log.d(TAG, "setVersionCounter: " + vcount);
        this.mVersionCounter = vcount;
    }

    public void incrementVersionCounter() {
        mVersionCounter++;
    }

    private void setVersionCounter(String vcount){
        if(D) Log.d(TAG, "setVersionCounter: " + vcount);
        try {
            this.mVersionCounter = Long.parseLong(vcount);
        } catch (NumberFormatException e) {
            Log.w(TAG, "unable to parse XML versionCounter:" + vcount);
            mVersionCounter = -1;
        }
    }

    public String getName() {
        return mName;
    }

    public void setName(String name) {
        if(D) Log.d(TAG, "setName: " + name);
        this.mName = name;
    }

    public TYPE getType() {
        return mType;
    }

    public void setType(TYPE type) {
        this.mType = type;
    }

    public List<BluetoothMapConvoContactElement> getContacts() {
        return mContacts;
    }

    public void setContacts(List<BluetoothMapConvoContactElement> contacts) {
        this.mContacts = contacts;
    }

    public void addContact(BluetoothMapConvoContactElement contact){
        if(mContacts == null)
            mContacts = new ArrayList<BluetoothMapConvoContactElement>();
        mContacts.add(contact);
    }

    public void removeContact(BluetoothMapConvoContactElement contact){
        mContacts.remove(contact);
    }

    public void removeContact(int index){
        mContacts.remove(index);
    }


    public long getLastActivity() {
        return mLastActivity;
    }

    public String getLastActivityString() {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
        Date date = new Date(mLastActivity);
        return format.format(date); // Format to YYYYMMDDTHHMMSS local time
    }

    public void setLastActivity(long last) {
        if(D) Log.d(TAG, "setLastActivity: " + last);
        this.mLastActivity = last;
    }

    public void setLastActivity(String lastActivity)throws ParseException {
        // TODO: Encode with time-zone if MCE requests it
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
        Date date = format.parse(lastActivity);
        this.mLastActivity = date.getTime();
    }

    public String getRead() {
        if(mReportRead == false) {
            return "UNKNOWN";
        }
        return (mRead?"READ":"UNREAD");
    }

    public boolean getReadBool() {
        return mRead;
    }

    public void setRead(boolean read, boolean reportRead) {
        this.mRead = read;
        if(D) Log.d(TAG, "setRead: " + read);
        this.mReportRead = reportRead;
    }

    private void setRead(String value) {
        if(value.trim().equalsIgnoreCase("yes")) {
            mRead = true;
        } else {
            mRead = false;
        }
        mReportRead = true;
    }

    /**
     * Set the conversation ID
     * @param type 0 if the thread ID is valid across all message types in the instance - else
     * use one of the CONVO_ID_xxx types.
     * @param threadId the conversation ID
     */
    public void setConvoId(long type, long threadId) {
        this.mId = new SignedLongLong(threadId,type);
        if(D) Log.d(TAG, "setConvoId: " + threadId + " type:" + type);
    }

    public String getConvoId(){
        return mId.toHexString();
    }

    public long getCpConvoId() {
        return mId.getLeastSignificantBits();
    }

    public void setSummary(String summary) {
        mSummary = summary;
    }

    public String getFullSummary() {
        return mSummary;
    }

    /* Get a valid UTF-8 string of maximum 256 bytes */
    private String getSummary() {
        if(mSummary != null) {
            try {
                return new String(BluetoothMapUtils.truncateUtf8StringToBytearray(mSummary, 256),
                        "UTF-8");
            } catch (UnsupportedEncodingException e) {
                // This cannot happen on an Android platform - UTF-8 is mandatory
                Log.e(TAG,"Missing UTF-8 support on platform", e);
            }
        }
        return null;
    }

    public String getSmsMmsContacts() {
        return mSmsMmsContacts;
    }

    public void setSmsMmsContacts(String smsMmsContacts) {
        mSmsMmsContacts = smsMmsContacts;
    }

    public int compareTo(BluetoothMapConvoListingElement e) {
        if (this.mLastActivity < e.mLastActivity) {
            return 1;
        } else if (this.mLastActivity > e.mLastActivity) {
            return -1;
        } else {
            return 0;
        }
    }

    /* Encode the MapMessageListingElement into the StringBuilder reference.
     * Here we have taken the choice not to report empty attributes, to reduce the
     * amount of data to be transfered over BT. */
    public void encode(XmlSerializer xmlConvoElement)
            throws IllegalArgumentException, IllegalStateException, IOException
    {

            // contruct the XML tag for a single conversation in the convolisting
            xmlConvoElement.startTag(null, XML_TAG_CONVERSATION);
            xmlConvoElement.attribute(null, XML_ATT_ID, mId.toHexString());
            if(mName != null) {
                xmlConvoElement.attribute(null, XML_ATT_NAME,
                        BluetoothMapUtils.stripInvalidChars(mName));
            }
            if(mLastActivity != -1) {
                xmlConvoElement.attribute(null, XML_ATT_LAST_ACTIVITY,
                        getLastActivityString());
            }
            // Even though this is implied, the value "UNKNOWN" kind of indicated it is required.
            if(mReportRead == true) {
                xmlConvoElement.attribute(null, XML_ATT_READ, getRead());
            }
            if(mVersionCounter != -1) {
                xmlConvoElement.attribute(null, XML_ATT_VERSION_COUNTER,
                        Long.toString(getVersionCounter()));
            }
            if(mSummary != null) {
                xmlConvoElement.attribute(null, XML_ATT_SUMMARY, getSummary());
            }
            if(mContacts != null){
                for(BluetoothMapConvoContactElement contact:mContacts){
                   contact.encode(xmlConvoElement);
                }
            }
            xmlConvoElement.endTag(null, XML_TAG_CONVERSATION);

    }

    /**
     * Consumes a conversation tag. It is expected that the parser is beyond the start-tag event,
     * with the name "conversation".
     * @param parser
     * @return
     * @throws XmlPullParserException
     * @throws IOException
     */
    public static BluetoothMapConvoListingElement createFromXml(XmlPullParser parser)
            throws XmlPullParserException, IOException, ParseException {
        BluetoothMapConvoListingElement newElement = new BluetoothMapConvoListingElement();
        int count = parser.getAttributeCount();
        int type;
        for (int i = 0; i<count; i++) {
            String attributeName = parser.getAttributeName(i).trim();
            String attributeValue = parser.getAttributeValue(i);
            if(attributeName.equalsIgnoreCase(XML_ATT_ID)) {
                newElement.mId = SignedLongLong.fromString(attributeValue);
            } else if(attributeName.equalsIgnoreCase(XML_ATT_NAME)) {
                newElement.mName = attributeValue;
            } else if(attributeName.equalsIgnoreCase(XML_ATT_LAST_ACTIVITY)) {
                newElement.setLastActivity(attributeValue);
            } else if(attributeName.equalsIgnoreCase(XML_ATT_READ)) {
                newElement.setRead(attributeValue);
            } else if(attributeName.equalsIgnoreCase(XML_ATT_VERSION_COUNTER)) {
                newElement.setVersionCounter(attributeValue);
            } else if(attributeName.equalsIgnoreCase(XML_ATT_SUMMARY)) {
                newElement.setSummary(attributeValue);
            } else {
                if(D) Log.i(TAG,"Unknown XML attribute: " + parser.getAttributeName(i));
            }
        }

        // Now determine if we get an end-tag, or a new start tag for contacts
        while((type=parser.next()) != XmlPullParser.END_TAG
                && type != XmlPullParser.END_DOCUMENT ) {
            // Skip until we get a start tag
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            // Skip until we get a convocontact tag
            String name = parser.getName().trim();
            if(name.equalsIgnoreCase(BluetoothMapConvoContactElement.XML_TAG_CONVOCONTACT)){
                newElement.addContact(BluetoothMapConvoContactElement.createFromXml(parser));
            } else {
                if(D) Log.i(TAG,"Unknown XML tag: " + name);
                XmlUtils.skipCurrentTag(parser);
                continue;
            }
        }
        // As we have extracted all attributes, we should expect an end-tag
        // parser.nextTag(); // consume the end-tag
        // TODO: Is this needed? - we should already be at end-tag, as this is the top condition

        return newElement;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        BluetoothMapConvoListingElement other = (BluetoothMapConvoListingElement) obj;
        if (mContacts == null) {
            if (other.mContacts != null) {
                return false;
            }
        } else if (!mContacts.equals(other.mContacts)) {
            return false;
        }
        /* As we use equals only for test, we don't compare auto assigned values
         * if (mId == null) {
            if (other.mId != null) {
                return false;
            }
        } else if (!mId.equals(other.mId)) {
            return false;
        } */

        if (mLastActivity != other.mLastActivity) {
            return false;
        }
        if (mName == null) {
            if (other.mName != null) {
                return false;
            }
        } else if (!mName.equals(other.mName)) {
            return false;
        }
        if (mRead != other.mRead) {
            return false;
        }
        return true;
    }

/*    @Override
    public boolean equals(Object o) {

        return true;
    };
    */

}


