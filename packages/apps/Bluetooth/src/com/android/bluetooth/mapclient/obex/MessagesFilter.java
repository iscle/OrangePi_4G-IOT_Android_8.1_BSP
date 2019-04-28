package com.android.bluetooth.mapclient;

import java.util.Date;

/**
 * Object representation of filters to be applied on message listing
 *
 * @see #getMessagesListing(String, int, MessagesFilter, int)
 * @see #getMessagesListing(String, int, MessagesFilter, int, int, int)
 */
public final class MessagesFilter {

    public final static byte MESSAGE_TYPE_ALL = 0x00;
    public final static byte MESSAGE_TYPE_SMS_GSM = 0x01;
    public final static byte MESSAGE_TYPE_SMS_CDMA = 0x02;
    public final static byte MESSAGE_TYPE_EMAIL = 0x04;
    public final static byte MESSAGE_TYPE_MMS = 0x08;

    public final static byte READ_STATUS_ANY = 0x00;
    public final static byte READ_STATUS_UNREAD = 0x01;
    public final static byte READ_STATUS_READ = 0x02;

    public final static byte PRIORITY_ANY = 0x00;
    public final static byte PRIORITY_HIGH = 0x01;
    public final static byte PRIORITY_NON_HIGH = 0x02;

    byte messageType = MESSAGE_TYPE_ALL;

    String periodBegin = null;

    String periodEnd = null;

    byte readStatus = READ_STATUS_ANY;

    String recipient = null;

    String originator = null;

    byte priority = PRIORITY_ANY;

    public MessagesFilter() {
    }

    public void setMessageType(byte filter) {
        messageType = filter;
    }

    public void setPeriod(Date filterBegin, Date filterEnd) {
        //Handle possible NPE for obexTime constructor utility
        if (filterBegin != null) {
            periodBegin = (new ObexTime(filterBegin)).toString();
        }
        if (filterEnd != null) {
            periodEnd = (new ObexTime(filterEnd)).toString();
        }
    }

    public void setReadStatus(byte readfilter) {
        readStatus = readfilter;
    }

    public void setRecipient(String filter) {
        if ("".equals(filter)) {
            recipient = null;
        } else {
            recipient = filter;
        }
    }

    public void setOriginator(String filter) {
        if ("".equals(filter)) {
            originator = null;
        } else {
            originator = filter;
        }
    }

    public void setPriority(byte filter) {
        priority = filter;
    }
}
