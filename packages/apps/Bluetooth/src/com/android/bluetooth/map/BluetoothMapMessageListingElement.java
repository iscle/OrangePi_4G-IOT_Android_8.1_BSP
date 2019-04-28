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

import java.text.SimpleDateFormat;
import java.util.Date;

import org.xmlpull.v1.XmlSerializer;

import com.android.bluetooth.map.BluetoothMapUtils.TYPE;
import com.android.bluetooth.util.Interop;

public class BluetoothMapMessageListingElement
    implements Comparable<BluetoothMapMessageListingElement> {

    private static final String TAG = "BluetoothMapMessageListingElement";
    private static final boolean D = false;
    private static final boolean V = false;

    private long mCpHandle = 0; /* The content provider handle - without type information */
    private String mSubject = null;
    private long mDateTime = 0;
    private String mSenderName = null;
    private String mSenderAddressing = null;
    private String mReplytoAddressing = null;
    private String mRecipientName = null;
    private String mRecipientAddressing = null;
    private TYPE mType = null;
    private boolean mMsgTypeAppParamSet = false;
    private int mSize = -1;
    private String mText = null;
    private String mReceptionStatus = null;
    private String mDeliveryStatus = null;
    private int mAttachmentSize = -1;
    private String mPriority = null;
    private boolean mRead = false;
    private String mSent = null;
    private String mProtect = null;
    private String mFolderType = null;
    private String mThreadId = null;
    private String mThreadName = null;
    private String mAttachmentMimeTypes = null;

    private boolean mReportRead = false;
    private int mCursorIndex = 0;

    public int getCursorIndex() {
        return mCursorIndex;
    }

    public void setCursorIndex(int cursorIndex) {
        this.mCursorIndex = cursorIndex;
    }

    public long getHandle() {
        return mCpHandle;
    }

    public void setHandle(long handle) {
        this.mCpHandle = handle;
    }

    public long getDateTime() {
        return mDateTime;
    }

    public String getDateTimeString() {
        /* TODO: if the feature bit mask of the client supports it, add the time-zone
         *       (as for MSETime) */
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
        Date date = new Date(mDateTime);
        return format.format(date); // Format to YYYYMMDDTHHMMSS local time
    }

    public void setDateTime(long dateTime) {
        this.mDateTime = dateTime;
    }

    public String getSubject() {
        return mSubject;
    }

    public void setSubject(String subject) {
        this.mSubject = subject;
    }

    public String getSenderName() {
        return mSenderName;
    }

    public void setSenderName(String senderName) {
        this.mSenderName = senderName;
    }

    public String getSenderAddressing() {
        return mSenderAddressing;
    }

    public void setSenderAddressing(String senderAddressing) {
        this.mSenderAddressing = senderAddressing;
    }

    public String getReplyToAddressing() {
        return mReplytoAddressing;
    }

    public void setReplytoAddressing(String replytoAddressing) {
        this.mReplytoAddressing = replytoAddressing;
    }

    public String getRecipientName() {
        return mRecipientName;
    }

    public void setRecipientName(String recipientName) {
        this.mRecipientName = recipientName;
    }

    public String getRecipientAddressing() {
        return mRecipientAddressing;
    }

    public void setRecipientAddressing(String recipientAddressing) {
        this.mRecipientAddressing = recipientAddressing;
    }

    public TYPE getType() {
        return mType;
    }

    public void setType(TYPE type, boolean appParamSet) {
        this.mMsgTypeAppParamSet = appParamSet;
        this.mType = type;
    }

    public int getSize() {
        return mSize;
    }

    public void setSize(int size) {
        this.mSize = size;
    }

    public String getText() {
        return mText;
    }

    public void setText(String text) {
        this.mText = text;
    }

    public String getReceptionStatus() {
        return mReceptionStatus;
    }

    public void setReceptionStatus(String receptionStatus) {
        this.mReceptionStatus = receptionStatus;
    }

    public String getDeliveryStatus() {
        return mDeliveryStatus;
    }

    public void setDeliveryStatus(String deliveryStatus) {
        this.mDeliveryStatus = deliveryStatus;
    }

    public int getAttachmentSize() {
        return mAttachmentSize;
    }

    public void setAttachmentSize(int attachmentSize) {
        this.mAttachmentSize = attachmentSize;
    }

    public String getAttachmentMimeTypes() {
        return mAttachmentMimeTypes;
    }

    public void setAttachmentMimeTypes(String attachmentMimeTypes) {
        this.mAttachmentMimeTypes = attachmentMimeTypes;
    }

    public String getPriority() {
        return mPriority;
    }

    public void setPriority(String priority) {
        this.mPriority = priority;
    }

    public String getRead() {
        return (mRead?"yes":"no");
    }
    public boolean getReadBool() {
        return mRead;
    }
    public void setRead(boolean read, boolean reportRead) {
        this.mRead = read;
        this.mReportRead = reportRead;
    }

    public String getSent() {
        return mSent;
    }

    public void setSent(String sent) {
        this.mSent = sent;
    }

    public String getProtect() {
        return mProtect;
    }

    public void setProtect(String protect) {
        this.mProtect = protect;
    }

    public void setThreadId(long threadId, TYPE type) {
        if(threadId != -1) {
            this.mThreadId = BluetoothMapUtils.getMapConvoHandle(threadId, type);
        }
    }

    public String getThreadName() {
        return mThreadName;
    }

    public void setThreadName(String name) {
        this.mThreadName = name;
    }

    public String getFolderType() {
        return mFolderType;
    }

    public void setFolderType(String folderType) {
        this.mFolderType = folderType;
    }

    public int compareTo(BluetoothMapMessageListingElement e) {
        if (this.mDateTime < e.mDateTime) {
            return 1;
        } else if (this.mDateTime > e.mDateTime) {
            return -1;
        } else {
            return 0;
        }
    }

    /* Encode the MapMessageListingElement into the StringBuilder reference.
     * */
    public void encode(XmlSerializer xmlMsgElement, boolean includeThreadId)
            throws IllegalArgumentException, IllegalStateException, IOException
    {
            // contruct the XML tag for a single msg in the msglisting
            xmlMsgElement.startTag(null, "msg");
            xmlMsgElement.attribute(null, "handle",
                    BluetoothMapUtils.getMapHandle(mCpHandle, mType));
            if(mSubject != null){
                String stripped = BluetoothMapUtils.stripInvalidChars(mSubject);

                if (Interop.matchByAddress(Interop.INTEROP_MAP_ASCIIONLY,
                        BluetoothMapService.getRemoteDevice().getAddress())) {
                    stripped = stripped.replaceAll("[\\P{ASCII}&\"><]", "");
                    if (stripped.isEmpty()) stripped = "---";
                }

                xmlMsgElement.attribute(null, "subject",
                        stripped.substring(0,  stripped.length() < 256 ? stripped.length() : 256));
            }

            if(mDateTime != 0)
                xmlMsgElement.attribute(null, "datetime", this.getDateTimeString());
            if(mSenderName != null)
                xmlMsgElement.attribute(null, "sender_name",
                        BluetoothMapUtils.stripInvalidChars(mSenderName));
            if(mSenderAddressing != null)
                xmlMsgElement.attribute(null, "sender_addressing", mSenderAddressing);
            if(mReplytoAddressing != null)
                xmlMsgElement.attribute(null, "replyto_addressing",mReplytoAddressing);
            if(mRecipientName != null)
                xmlMsgElement.attribute(null, "recipient_name",
                        BluetoothMapUtils.stripInvalidChars(mRecipientName));
            if(mRecipientAddressing != null)
                xmlMsgElement.attribute(null, "recipient_addressing", mRecipientAddressing);
            /* Avoid NPE for possible "null" value of mType */
            if(mMsgTypeAppParamSet == true && mType != null)
                xmlMsgElement.attribute(null, "type", mType.name());
            if(mSize != -1)
                xmlMsgElement.attribute(null, "size", Integer.toString(mSize));
            if(mText != null)
                xmlMsgElement.attribute(null, "text", mText);
            if(mReceptionStatus != null)
                xmlMsgElement.attribute(null, "reception_status", mReceptionStatus);
            if(mDeliveryStatus != null)
                xmlMsgElement.attribute(null, "delivery_status", mDeliveryStatus);
            if(mAttachmentSize != -1)
                xmlMsgElement.attribute(null, "attachment_size",
                        Integer.toString(mAttachmentSize));
            if(mAttachmentMimeTypes != null)
                xmlMsgElement.attribute(null, "attachment_mime_types", mAttachmentMimeTypes);
            if(mPriority != null)
                xmlMsgElement.attribute(null, "priority", mPriority);
            if(mReportRead)
                xmlMsgElement.attribute(null, "read", getRead());
            if(mSent != null)
                xmlMsgElement.attribute(null, "sent", mSent);
            if(mProtect != null)
                xmlMsgElement.attribute(null, "protected", mProtect);
            if(mThreadId != null && includeThreadId == true)
                xmlMsgElement.attribute(null, "conversation_id", mThreadId);
            if(mThreadName != null && includeThreadId == true)
                xmlMsgElement.attribute(null, "conversation_name", mThreadName);
            if(mFolderType != null )
                xmlMsgElement.attribute(null, "folder_type", mFolderType);
            xmlMsgElement.endTag(null, "msg");

    }
}


