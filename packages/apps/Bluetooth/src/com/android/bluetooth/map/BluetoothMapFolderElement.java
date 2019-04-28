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
import java.util.HashMap;
import java.util.Locale;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import android.util.Log;
import android.util.Xml;

import com.android.internal.util.FastXmlSerializer;
import com.android.internal.util.XmlUtils;


/**
 * Class to contain a single folder element representation.
 *
 */
public class BluetoothMapFolderElement implements Comparable<BluetoothMapFolderElement>{
    private String mName;
    private BluetoothMapFolderElement mParent = null;
    private long mFolderId = -1;
    private boolean mHasSmsMmsContent = false;
    private boolean mHasImContent = false;
    private boolean mHasEmailContent = false;

    private boolean mIgnore = false;

    private HashMap<String, BluetoothMapFolderElement> mSubFolders;

    private static final boolean D = BluetoothMapService.DEBUG;
    private static final boolean V = BluetoothMapService.VERBOSE;

    private final static String TAG = "BluetoothMapFolderElement";

    public BluetoothMapFolderElement( String name, BluetoothMapFolderElement parrent){
        this.mName = name;
        this.mParent = parrent;
        mSubFolders = new HashMap<String, BluetoothMapFolderElement>();
    }

    public void setIngore(boolean ignore) {
        mIgnore = ignore;
    }

    public boolean shouldIgnore() {
        return mIgnore;
    }

    public String getName() {
        return mName;
    }

    public boolean hasSmsMmsContent(){
        return mHasSmsMmsContent;
    }

    public long getFolderId(){
        return mFolderId;
    }
    public boolean hasEmailContent(){
        return mHasEmailContent;
    }

    public void setFolderId(long folderId) {
        this.mFolderId = folderId;
    }
    public void setHasSmsMmsContent(boolean hasSmsMmsContent) {
        this.mHasSmsMmsContent = hasSmsMmsContent;
    }
    public void setHasEmailContent(boolean hasEmailContent) {
        this.mHasEmailContent = hasEmailContent;
    }
    public void setHasImContent(boolean hasImContent) {
        this.mHasImContent = hasImContent;
    }

    public boolean hasImContent(){
        return mHasImContent;
    }

    /**
     * Fetch the parent folder.
     * @return the parent folder or null if we are at the root folder.
     */
    public BluetoothMapFolderElement getParent() {
        return mParent;
    }

    /**
     * Build the full path to this folder
     * @return a string representing the full path.
     */
    public String getFullPath() {
        StringBuilder sb = new StringBuilder(mName);
        BluetoothMapFolderElement current = mParent;
        while(current != null) {
            if(current.getParent() != null) {
                sb.insert(0, current.mName + "/");
            }
            current = current.getParent();
        }
        //sb.insert(0, "/"); Should this be included? The MAP spec. do not include it in examples.
        return sb.toString();
    }


    public BluetoothMapFolderElement getFolderByName(String name) {
        BluetoothMapFolderElement folderElement = this.getRoot();
        folderElement = folderElement.getSubFolder("telecom");
        folderElement = folderElement.getSubFolder("msg");
        folderElement = folderElement.getSubFolder(name);
        if (folderElement != null && folderElement.getFolderId() == -1 )
            folderElement = null;
        return folderElement;
    }

    public BluetoothMapFolderElement getFolderById(long id) {
        return getFolderById(id, this);
    }

    public static BluetoothMapFolderElement getFolderById(long id,
            BluetoothMapFolderElement folderStructure) {
        if(folderStructure == null) {
            return null;
        }
        return findFolderById(id, folderStructure.getRoot());
    }

    private static BluetoothMapFolderElement findFolderById(long id,
            BluetoothMapFolderElement folder) {
        if(folder.getFolderId() == id) {
            return folder;
        }
        /* Else */
        for(BluetoothMapFolderElement subFolder : folder.mSubFolders.values().toArray(
                new BluetoothMapFolderElement[folder.mSubFolders.size()]))
        {
            BluetoothMapFolderElement ret = findFolderById(id, subFolder);
            if(ret != null) {
                return ret;
            }
        }
        return null;
    }


    /**
     * Fetch the root folder.
     * @return the root folder.
     */
    public BluetoothMapFolderElement getRoot() {
        BluetoothMapFolderElement rootFolder = this;
        while(rootFolder.getParent() != null)
            rootFolder = rootFolder.getParent();
        return rootFolder;
    }

    /**
     * Add a virtual folder.
     * @param name the name of the folder to add.
     * @return the added folder element.
     */
    public BluetoothMapFolderElement addFolder(String name){
        name = name.toLowerCase(Locale.US);
        BluetoothMapFolderElement newFolder = mSubFolders.get(name);
        if(newFolder == null) {
            if(D) Log.i(TAG,"addFolder():" + name);
            newFolder = new BluetoothMapFolderElement(name, this);
            mSubFolders.put(name, newFolder);
        } else {
            if(D) Log.i(TAG,"addFolder():" + name + " already added");
        }
        return newFolder;
    }

    /**
     * Add a sms/mms folder.
     * @param name the name of the folder to add.
     * @return the added folder element.
     */
    public BluetoothMapFolderElement addSmsMmsFolder(String name){
        if(D) Log.i(TAG,"addSmsMmsFolder()");
        BluetoothMapFolderElement newFolder = addFolder(name);
        newFolder.setHasSmsMmsContent(true);
        return newFolder;
    }

    /**
     * Add a im folder.
     * @param name the name of the folder to add.
     * @return the added folder element.
     */
    public BluetoothMapFolderElement addImFolder(String name, long idFolder){
        if(D) Log.i(TAG,"addImFolder() id = " + idFolder);
        BluetoothMapFolderElement newFolder = addFolder(name);
        newFolder.setHasImContent(true);
        newFolder.setFolderId(idFolder);
        return newFolder;
    }

    /**
     * Add an Email folder.
     * @param name the name of the folder to add.
     * @return the added folder element.
     */
    public BluetoothMapFolderElement addEmailFolder(String name, long emailFolderId){
        if(V) Log.v(TAG,"addEmailFolder() id = " + emailFolderId);
        BluetoothMapFolderElement newFolder = addFolder(name);
        newFolder.setFolderId(emailFolderId);
        newFolder.setHasEmailContent(true);
        return newFolder;
    }
    /**
     * Fetch the number of sub folders.
     * @return returns the number of sub folders.
     */
    public int getSubFolderCount(){
        return mSubFolders.size();
    }

    /**
     * Returns the subFolder element matching the supplied folder name.
     * @param folderName the name of the subFolder to find.
     * @return the subFolder element if found {@code null} otherwise.
     */
    public BluetoothMapFolderElement getSubFolder(String folderName){
        return mSubFolders.get(folderName.toLowerCase());
    }

    public byte[] encode(int offset, int count) throws UnsupportedEncodingException {
        StringWriter sw = new StringWriter();
        XmlSerializer xmlMsgElement = new FastXmlSerializer();
        int i, stopIndex;
        // We need index based access to the subFolders
        BluetoothMapFolderElement[] folders = mSubFolders.values().toArray(new BluetoothMapFolderElement[mSubFolders.size()]);

        if(offset > mSubFolders.size())
            throw new IllegalArgumentException("FolderListingEncode: offset > subFolders.size()");

        stopIndex = offset + count;
        if(stopIndex > mSubFolders.size())
            stopIndex = mSubFolders.size();

        try {
            xmlMsgElement.setOutput(sw);
            xmlMsgElement.startDocument("UTF-8", true);
            xmlMsgElement.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
            xmlMsgElement.startTag(null, "folder-listing");
            xmlMsgElement.attribute(null, "version", BluetoothMapUtils.MAP_V10_STR);
            for(i = offset; i<stopIndex; i++)
            {
                xmlMsgElement.startTag(null, "folder");
                xmlMsgElement.attribute(null, "name", folders[i].getName());
                xmlMsgElement.endTag(null, "folder");
            }
            xmlMsgElement.endTag(null, "folder-listing");
            xmlMsgElement.endDocument();
        } catch (IllegalArgumentException e) {
            if(D) Log.w(TAG,e);
            throw new IllegalArgumentException("error encoding folderElement");
        } catch (IllegalStateException e) {
            if(D) Log.w(TAG,e);
            throw new IllegalArgumentException("error encoding folderElement");
        } catch (IOException e) {
            if(D) Log.w(TAG,e);
            throw new IllegalArgumentException("error encoding folderElement");
        }
        return sw.toString().getBytes("UTF-8");
    }

    /* The functions below are useful for implementing a MAP client, reusing the object.
     * Currently they are only used for test purposes.
     * */

    /**
     * Append sub folders from an XML document as specified in the MAP specification.
     * Attributes will be inherited from parent folder - with regards to message types in the
     * folder.
     * @param xmlDocument - InputStream with the document
     *
     * @throws XmlPullParserException
     * @throws IOException
     */
    public void appendSubfolders(InputStream xmlDocument)
            throws XmlPullParserException, IOException {
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
                if(!name.equalsIgnoreCase("folder-listing")) {
                    if(D) Log.i(TAG,"Unknown XML tag: " + name);
                    XmlUtils.skipCurrentTag(parser);
                }
                readFolders(parser);
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
     */
    public void readFolders(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        int type;
        if(D) Log.i(TAG,"readFolders(): ");
        while((type=parser.next()) != XmlPullParser.END_TAG
                && type != XmlPullParser.END_DOCUMENT ) {
            // Skip until we get a start tag
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            // Skip until we get a folder-listing tag
            String name = parser.getName();
            if(name.trim().equalsIgnoreCase("folder") == false) {
                if(D) Log.i(TAG,"Unknown XML tag: " + name);
                XmlUtils.skipCurrentTag(parser);
                continue;
            }
            int count = parser.getAttributeCount();
            for (int i = 0; i<count; i++) {
                if(parser.getAttributeName(i).trim().equalsIgnoreCase("name")) {
                    // We found a folder, append to sub folders.
                    BluetoothMapFolderElement element =
                            addFolder(parser.getAttributeValue(i).trim());
                    element.setHasEmailContent(mHasEmailContent);
                    element.setHasImContent(mHasImContent);
                    element.setHasSmsMmsContent(mHasSmsMmsContent);
                } else {
                    if(D) Log.i(TAG,"Unknown XML attribute: " + parser.getAttributeName(i));
                }
            }
            parser.nextTag();
        }
    }

    /**
     * Recursive compare of all folder names
     */
    @Override
    public int compareTo(BluetoothMapFolderElement another) {
        if(another == null) return 1;
        int ret = mName.compareToIgnoreCase(another.mName);
        // TODO: Do we want to add compare of folder type?
        if(ret == 0) {
            ret = mSubFolders.size() - another.mSubFolders.size();
            if(ret == 0) {
                // Compare all sub folder elements (will do nothing if mSubFolders is empty)
                for(BluetoothMapFolderElement subfolder : mSubFolders.values()) {
                    BluetoothMapFolderElement subfolderAnother =
                            another.mSubFolders.get(subfolder.getName());
                    if(subfolderAnother == null) {
                        if(D) Log.i(TAG, subfolder.getFullPath() + " not in another");
                        return 1;
                    }
                    ret = subfolder.compareTo(subfolderAnother);
                    if(ret != 0) {
                        if(D) Log.i(TAG, subfolder.getFullPath() + " filed compareTo()");
                        return ret;
                    }
                }
            } else {
                if(D) Log.i(TAG, "mSubFolders.size(): " + mSubFolders.size() +
                        " another.mSubFolders.size(): " + another.mSubFolders.size());
            }
        } else {
            if(D) Log.i(TAG, "mName: " + mName + " another.mName: " + another.mName);
        }
        return ret;
    }
}
