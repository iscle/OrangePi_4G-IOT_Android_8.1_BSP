/*
* Copyright (C) 2015 Samsung System LSI
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
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import android.util.Log;
import android.util.Xml;

import com.android.internal.util.FastXmlSerializer;
import com.android.internal.util.XmlUtils;

public class BluetoothMapConvoListing {
    private boolean hasUnread = false;
    private static final String TAG = "BluetoothMapConvoListing";
    private static final boolean D = BluetoothMapService.DEBUG;
    private static final String XML_TAG = "MAP-convo-listing";

    private List<BluetoothMapConvoListingElement> mList;

    public BluetoothMapConvoListing(){
     mList = new ArrayList<BluetoothMapConvoListingElement>();
    }
    public void add(BluetoothMapConvoListingElement element) {
        mList.add(element);
        /* update info regarding whether the list contains unread conversations */
        if (element.getReadBool())
        {
            hasUnread = true;
        }
    }

    /**
     * Used to fetch the number of BluetoothMapConvoListingElement elements in the list.
     * @return the number of elements in the list.
     */
    public int getCount() {
        if(mList != null)
        {
            return mList.size();
        }
        return 0;
    }

    /**
     * does the list contain any unread messages
     * @return true if unread messages have been added to the list, else false
     */
    public boolean hasUnread()
    {
        return hasUnread;
    }


    /**
     *  returns the entire list as a list
     * @return list
     */
    public List<BluetoothMapConvoListingElement> getList(){
        return mList;
    }

    /**
     * Encode the list of BluetoothMapMessageListingElement(s) into a UTF-8
     * formatted XML-string in a trimmed byte array
     *
     * @return a reference to the encoded byte array.
     * @throws UnsupportedEncodingException
     *             if UTF-8 encoding is unsupported on the platform.
     */
    public byte[] encode() throws UnsupportedEncodingException {
        StringWriter sw = new StringWriter();
        XmlSerializer xmlConvoElement = new FastXmlSerializer();
        try {
            xmlConvoElement.setOutput(sw);
            xmlConvoElement.startDocument("UTF-8", true);
            xmlConvoElement.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output",
                    true);
            xmlConvoElement.startTag(null, XML_TAG);
            xmlConvoElement.attribute(null, "version", "1.0");
            // Do the XML encoding of list
            for (BluetoothMapConvoListingElement element : mList) {
                element.encode(xmlConvoElement); // Append the list element
            }
            xmlConvoElement.endTag(null, XML_TAG);
            xmlConvoElement.endDocument();
        } catch (IllegalArgumentException e) {
            Log.w(TAG, e);
        } catch (IllegalStateException e) {
            Log.w(TAG, e);
        } catch (IOException e) {
            Log.w(TAG, e);
        }
        return sw.toString().getBytes("UTF-8");
    }

    public void sort() {
        Collections.sort(mList);
    }

    public void segment(int count, int offset) {
        count = Math.min(count, mList.size() - offset);
        if (count > 0) {
            mList = mList.subList(offset, offset + count);
            if(mList == null) {
                mList = new ArrayList<BluetoothMapConvoListingElement>(); // Return an empty list
            }
        } else {
            if(offset > mList.size()) {
               mList = new ArrayList<BluetoothMapConvoListingElement>();
               Log.d(TAG, "offset greater than list size. Returning empty list");
            } else {
               mList = mList.subList(offset, mList.size());
            }
        }
    }

    public void appendFromXml(InputStream xmlDocument)
            throws XmlPullParserException, IOException, ParseException {
        try {
            XmlPullParser parser = Xml.newPullParser();
            int type;
            parser.setInput(xmlDocument, "UTF-8");

            // First find the folder-listing
            while((type=parser.next()) != XmlPullParser.END_TAG
                    && type != XmlPullParser.END_DOCUMENT ) {
                // Skip until we get a start tag
                if (parser.getEventType() != XmlPullParser.START_TAG) {
                    continue;
                }
                // Skip until we get a folder-listing tag
                String name = parser.getName();
                if(!name.equalsIgnoreCase(XML_TAG)) {
                    if(D) Log.i(TAG,"Unknown XML tag: " + name);
                    XmlUtils.skipCurrentTag(parser);
                }
                readConversations(parser);
            }
        } finally {
            xmlDocument.close();
        }
    }

    /**
     * Parses folder elements, and add to mSubFolders.
     * @param parser the Xml Parser currently pointing to an folder-listing tag.
     * @throws XmlPullParserException
     * @throws IOException
     * @throws
     */
    private void readConversations(XmlPullParser parser)
            throws XmlPullParserException, IOException, ParseException {
        int type;
        if(D) Log.i(TAG,"readConversations(): ");
        while((type=parser.next()) != XmlPullParser.END_TAG
                && type != XmlPullParser.END_DOCUMENT ) {
            // Skip until we get a start tag
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            // Skip until we get a folder-listing tag
            String name = parser.getName();
            if(name.trim().equalsIgnoreCase(BluetoothMapConvoListingElement.XML_TAG_CONVERSATION)
                    == false) {
                if(D) Log.i(TAG,"Unknown XML tag: " + name);
                XmlUtils.skipCurrentTag(parser);
                continue;
            }
            // Add a single conversation
            add(BluetoothMapConvoListingElement.createFromXml(parser));
        }
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
        BluetoothMapConvoListing other = (BluetoothMapConvoListing) obj;
        if (hasUnread != other.hasUnread) {
            return false;
        }
        if (mList == null) {
            if (other.mList != null) {
                return false;
            }
        } else if (!mList.equals(other.mList)) {
            return false;
        }
        return true;
    }

}
