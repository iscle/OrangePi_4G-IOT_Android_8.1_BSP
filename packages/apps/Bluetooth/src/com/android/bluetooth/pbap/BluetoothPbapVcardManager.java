/*
 * Copyright (c) 2008-2009, Motorola, Inc.
 * Copyright (C) 2009-2012, Broadcom Corporation
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * - Neither the name of the Motorola, Inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.bluetooth.pbap;

import com.android.bluetooth.R;
import com.android.bluetooth.util.DevicePolicyUtils;
import com.android.vcard.VCardComposer;
import com.android.vcard.VCardConfig;
import com.android.vcard.VCardPhoneNumberTranslationCallback;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.CursorWindowAllocationException;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.ContactsContract.RawContactsEntity;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Comparator;
import com.android.bluetooth.R;
import com.android.vcard.VCardComposer;
import com.android.vcard.VCardConfig;
import com.android.vcard.VCardPhoneNumberTranslationCallback;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

import javax.obex.Operation;
import javax.obex.ResponseCodes;
import javax.obex.ServerOperation;

//M: ALPS02620356: Add wakeLock to prevent cpu suspend when pulling Vcard
import android.os.PowerManager;

public class BluetoothPbapVcardManager {
    private static final String TAG = "BluetoothPbapVcardManager";

    private static final boolean V = BluetoothPbapService.VERBOSE;

    private ContentResolver mResolver;

    private Context mContext;

    private static final int PHONE_NUMBER_COLUMN_INDEX = 3;

    static final String SORT_ORDER_PHONE_NUMBER = CommonDataKinds.Phone.NUMBER + " ASC";

    static final String[] PHONES_CONTACTS_PROJECTION = new String[] {
            Phone.CONTACT_ID, // 0
            Phone.DISPLAY_NAME, // 1
    };

    static final String[] PHONE_LOOKUP_PROJECTION = new String[] {
            PhoneLookup._ID, PhoneLookup.DISPLAY_NAME
    };

    static final int CONTACTS_ID_COLUMN_INDEX = 0;

    static final int CONTACTS_NAME_COLUMN_INDEX = 1;

    static long LAST_FETCHED_TIME_STAMP;

    // call histories use dynamic handles, and handles should order by date; the
    // most recently one should be the first handle. In table "calls", _id and
    // date are consistent in ordering, to implement simply, we sort by _id
    // here.
    static final String CALLLOG_SORT_ORDER = Calls._ID + " DESC";

    private static final int NEED_SEND_BODY = -1;

    //M: ALPS02620356: Add wakeLock to prevent cpu suspend when pulling Vcard
    private PowerManager.WakeLock mWakeLock = null;

    public BluetoothPbapVcardManager(final Context context) {
        mContext = context;
        mResolver = mContext.getContentResolver();
        LAST_FETCHED_TIME_STAMP = System.currentTimeMillis();
    }

    /**
     * Create an owner vcard from the configured profile
     * @param vcardType21
     * @return
     */
    private final String getOwnerPhoneNumberVcardFromProfile(
            final boolean vcardType21, final byte[] filter) {
        // Currently only support Generic Vcard 2.1 and 3.0
        int vcardType;
        if (vcardType21) {
            vcardType = VCardConfig.VCARD_TYPE_V21_GENERIC;
        } else {
            vcardType = VCardConfig.VCARD_TYPE_V30_GENERIC;
        }

        if (!BluetoothPbapConfig.includePhotosInVcard()) {
            vcardType |= VCardConfig.FLAG_REFRAIN_IMAGE_EXPORT;
        }

        return BluetoothPbapUtils.createProfileVCard(mContext, vcardType,filter);
    }

    public final String getOwnerPhoneNumberVcard(final boolean vcardType21, final byte[] filter) {
        //Owner vCard enhancement: Use "ME" profile if configured
        if (BluetoothPbapConfig.useProfileForOwnerVcard()) {
            String vcard = getOwnerPhoneNumberVcardFromProfile(vcardType21, filter);
            if (vcard != null && vcard.length() != 0) {
                return vcard;
            }
        }
        //End enhancement

        BluetoothPbapCallLogComposer composer = new BluetoothPbapCallLogComposer(mContext);
        String name = BluetoothPbapService.getLocalPhoneName();
        String number = BluetoothPbapService.getLocalPhoneNum();
        String vcard = composer.composeVCardForPhoneOwnNumber(Phone.TYPE_MOBILE, name, number,
                vcardType21);
        return vcard;
    }

    public final int getPhonebookSize(final int type) {
        int size;
        switch (type) {
            case BluetoothPbapObexServer.ContentType.PHONEBOOK:
                size = getContactsSize();
                break;
            default:
                size = getCallHistorySize(type);
                break;
        }
        if (V) Log.v(TAG, "getPhonebookSize size = " + size + " type = " + type);
        return size;
    }

    public final int getContactsSize() {
        final Uri myUri = DevicePolicyUtils.getEnterprisePhoneUri(mContext);
        Cursor contactCursor = null;
        try {
            contactCursor = mResolver.query(
                    myUri, new String[] {Phone.CONTACT_ID}, null, null, Phone.CONTACT_ID);
            if (contactCursor == null) {
                return 0;
            }
            return getDistinctContactIdSize(contactCursor) + 1; // always has the 0.vcf
        } catch (CursorWindowAllocationException e) {
            Log.e(TAG, "CursorWindowAllocationException while getting Contacts size");
        } finally {
            if (contactCursor != null) {
                contactCursor.close();
            }
        }
        return 0;
    }

    public final int getCallHistorySize(final int type) {
        final Uri myUri = CallLog.Calls.CONTENT_URI;
        String selection = BluetoothPbapObexServer.createSelectionPara(type);
        int size = 0;
        Cursor callCursor = null;
        try {
            callCursor = mResolver.query(myUri, null, selection, null,
                    CallLog.Calls.DEFAULT_SORT_ORDER);
            if (callCursor != null) {
                size = callCursor.getCount();
            }
        } catch (CursorWindowAllocationException e) {
            Log.e(TAG, "CursorWindowAllocationException while getting CallHistory size");
        } finally {
            if (callCursor != null) {
                callCursor.close();
                callCursor = null;
            }
        }
        return size;
    }

    public final ArrayList<String> loadCallHistoryList(final int type) {
        final Uri myUri = CallLog.Calls.CONTENT_URI;
        String selection = BluetoothPbapObexServer.createSelectionPara(type);
        String[] projection = new String[] {
                Calls.NUMBER, Calls.CACHED_NAME, Calls.NUMBER_PRESENTATION
        };
        final int CALLS_NUMBER_COLUMN_INDEX = 0;
        final int CALLS_NAME_COLUMN_INDEX = 1;
        final int CALLS_NUMBER_PRESENTATION_COLUMN_INDEX = 2;

        Cursor callCursor = null;
        ArrayList<String> list = new ArrayList<String>();
        try {
            callCursor = mResolver.query(myUri, projection, selection, null,
                    CALLLOG_SORT_ORDER);
            if (callCursor != null) {
                for (callCursor.moveToFirst(); !callCursor.isAfterLast();
                        callCursor.moveToNext()) {
                    String name = callCursor.getString(CALLS_NAME_COLUMN_INDEX);
                    if (TextUtils.isEmpty(name)) {
                        // name not found, use number instead
                        final int numberPresentation = callCursor.getInt(
                                CALLS_NUMBER_PRESENTATION_COLUMN_INDEX);
                        if (numberPresentation != Calls.PRESENTATION_ALLOWED) {
                            name = mContext.getString(R.string.unknownNumber);
                        } else {
                            name = callCursor.getString(CALLS_NUMBER_COLUMN_INDEX);
                        }
                    }
                    list.add(name);
                }
            }
        } catch (CursorWindowAllocationException e) {
            Log.e(TAG, "CursorWindowAllocationException while loading CallHistory");
        } finally {
            if (callCursor != null) {
                callCursor.close();
                callCursor = null;
            }
        }
        return list;
    }

    public final ArrayList<String> getPhonebookNameList(final int orderByWhat) {
        ArrayList<String> nameList = new ArrayList<String>();
        //Owner vCard enhancement. Use "ME" profile if configured
        String ownerName = null;
        if (BluetoothPbapConfig.useProfileForOwnerVcard()) {
            ownerName = BluetoothPbapUtils.getProfileName(mContext);
        }
        if (ownerName == null || ownerName.length()==0) {
            ownerName = BluetoothPbapService.getLocalPhoneName();
        }
        nameList.add(ownerName);
        //End enhancement

        final Uri myUri = DevicePolicyUtils.getEnterprisePhoneUri(mContext);
        Cursor contactCursor = null;
        // By default order is indexed
        String orderBy = Phone.CONTACT_ID;
        try {
            if (orderByWhat == BluetoothPbapObexServer.ORDER_BY_ALPHABETICAL) {
                orderBy = Phone.DISPLAY_NAME;
            }
            contactCursor = mResolver.query(myUri, PHONES_CONTACTS_PROJECTION, null, null, orderBy);
            if (contactCursor != null) {
                appendDistinctNameIdList(nameList,
                        mContext.getString(android.R.string.unknownName),
                        contactCursor);
            }
        } catch (CursorWindowAllocationException e) {
            Log.e(TAG, "CursorWindowAllocationException while getting phonebook name list");
        } catch (Exception e) {
            Log.e(TAG, "Exception while getting phonebook name list", e);
        } finally {
            if (contactCursor != null) {
                contactCursor.close();
                contactCursor = null;
            }
        }
        return nameList;
    }

    final ArrayList<String> getSelectedPhonebookNameList(final int orderByWhat,
            final boolean vcardType21, int needSendBody, int pbSize, byte[] selector,
            String vcardselectorop) {
        ArrayList<String> nameList = new ArrayList<String>();
        PropertySelector vcardselector = new PropertySelector(selector);
        VCardComposer composer = null;
        int vcardType;

        if (vcardType21) {
            vcardType = VCardConfig.VCARD_TYPE_V21_GENERIC;
        } else {
            vcardType = VCardConfig.VCARD_TYPE_V30_GENERIC;
        }

        composer = BluetoothPbapUtils.createFilteredVCardComposer(mContext, vcardType, null);
        composer.setPhoneNumberTranslationCallback(new VCardPhoneNumberTranslationCallback() {

            public String onValueReceived(
                    String rawValue, int type, String label, boolean isPrimary) {
                String numberWithControlSequence = rawValue.replace(PhoneNumberUtils.PAUSE, 'p')
                                                           .replace(PhoneNumberUtils.WAIT, 'w');
                return numberWithControlSequence;
            }
        });

        // Owner vCard enhancement. Use "ME" profile if configured
        String ownerName = null;
        if (BluetoothPbapConfig.useProfileForOwnerVcard()) {
            ownerName = BluetoothPbapUtils.getProfileName(mContext);
        }
        if (ownerName == null || ownerName.length() == 0) {
            ownerName = BluetoothPbapService.getLocalPhoneName();
        }
        nameList.add(ownerName);
        // End enhancement

        final Uri myUri = DevicePolicyUtils.getEnterprisePhoneUri(mContext);
        Cursor contactCursor = null;
        try {
            contactCursor = mResolver.query(
                    myUri, PHONES_CONTACTS_PROJECTION, null, null, Phone.CONTACT_ID);

            if (contactCursor != null) {
                if (!composer.initWithCallback(
                            contactCursor, new EnterpriseRawContactEntitlesInfoCallback())) {
                    return nameList;
                }

                while (!composer.isAfterLast()) {
                    String vcard = composer.createOneEntry();
                    if (vcard == null) {
                        Log.e(TAG, "Failed to read a contact. Error reason: "
                                        + composer.getErrorReason());
                        return nameList;
                    }
                    if (V) Log.v(TAG, "Checking selected bits in the vcard composer" + vcard);

                    if (!vcardselector.CheckVcardSelector(vcard, vcardselectorop)) {
                        Log.e(TAG, "vcard selector check fail");
                        vcard = null;
                        pbSize--;
                        continue;
                    } else {
                        String name = vcardselector.getName(vcard);
                        if (TextUtils.isEmpty(name)) {
                            name = mContext.getString(android.R.string.unknownName);
                        }
                        nameList.add(name);
                    }
                }
                if (orderByWhat == BluetoothPbapObexServer.ORDER_BY_INDEXED) {
                    if (V) Log.v(TAG, "getPhonebookNameList, order by index");
                    // Do not need to do anything, as we sort it by index already
                } else if (orderByWhat == BluetoothPbapObexServer.ORDER_BY_ALPHABETICAL) {
                    if (V) Log.v(TAG, "getPhonebookNameList, order by alpha");
                    Collections.sort(nameList);
                }
            }
        } catch (CursorWindowAllocationException e) {
            Log.e(TAG, "CursorWindowAllocationException while getting Phonebook name list");
        } finally {
            if (contactCursor != null) {
                contactCursor.close();
                contactCursor = null;
            }
        }
        return nameList;
    }

    public final ArrayList<String> getContactNamesByNumber(final String phoneNumber) {
        ArrayList<String> nameList = new ArrayList<String>();
        ArrayList<String> tempNameList = new ArrayList<String>();

        Cursor contactCursor = null;
        Uri uri = null;
        String[] projection = null;

        if (TextUtils.isEmpty(phoneNumber)) {
            uri = DevicePolicyUtils.getEnterprisePhoneUri(mContext);
            projection = PHONES_CONTACTS_PROJECTION;
        } else {
            uri = Uri.withAppendedPath(getPhoneLookupFilterUri(),
                Uri.encode(phoneNumber));
            projection = PHONE_LOOKUP_PROJECTION;
        }

        try {
            contactCursor = mResolver.query(uri, projection, null, null, Phone.CONTACT_ID);

            if (contactCursor != null) {
                appendDistinctNameIdList(nameList,
                        mContext.getString(android.R.string.unknownName),
                        contactCursor);
                if (V) {
                    for (String nameIdStr : nameList) {
                        Log.v(TAG, "got name " + nameIdStr + " by number " + phoneNumber);
                    }
                }
            }
        } catch (CursorWindowAllocationException e) {
            Log.e(TAG, "CursorWindowAllocationException while getting contact names");
        } finally {
            if (contactCursor != null) {
                contactCursor.close();
                contactCursor = null;
            }
        }
        int tempListSize = tempNameList.size();
        for (int index = 0; index < tempListSize; index++) {
            String object = tempNameList.get(index);
            if (!nameList.contains(object))
                nameList.add(object);
        }

        return nameList;
    }

    byte[] getCallHistoryPrimaryFolderVersion(final int type) {
        final Uri myUri = CallLog.Calls.CONTENT_URI;
        String selection = BluetoothPbapObexServer.createSelectionPara(type);
        selection = selection + " AND date >= " + LAST_FETCHED_TIME_STAMP;

        Log.d(TAG, "LAST_FETCHED_TIME_STAMP is " + LAST_FETCHED_TIME_STAMP);
        Cursor callCursor = null;
        long count = 0;
        long primaryVcMsb = 0;
        ArrayList<String> list = new ArrayList<String>();
        try {
            callCursor = mResolver.query(myUri, null, selection, null, null);
            while (callCursor != null && callCursor.moveToNext()) {
                count = count + 1;
            }
        } catch (Exception e) {
            Log.e(TAG, "exception while fetching callHistory pvc");
        } finally {
            if (callCursor != null) {
                callCursor.close();
                callCursor = null;
            }
        }

        LAST_FETCHED_TIME_STAMP = System.currentTimeMillis();
        Log.d(TAG, "getCallHistoryPrimaryFolderVersion count is " + count + " type is " + type);
        ByteBuffer pvc = ByteBuffer.allocate(16);
        pvc.putLong(primaryVcMsb);
        Log.d(TAG, "primaryVersionCounter is " + BluetoothPbapUtils.primaryVersionCounter);
        pvc.putLong(count);
        return pvc.array();
    }

    final int composeAndSendSelectedCallLogVcards(final int type, Operation op,
            final int startPoint, final int endPoint, final boolean vcardType21, int needSendBody,
            int pbSize, boolean ignorefilter, byte[] filter, byte[] vcardselector,
            String vcardselectorop, boolean vcardselect) {
        if (startPoint < 1 || startPoint > endPoint) {
            Log.e(TAG, "internal error: startPoint or endPoint is not correct.");
            return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
        }
        String typeSelection = BluetoothPbapObexServer.createSelectionPara(type);

        final Uri myUri = CallLog.Calls.CONTENT_URI;
        final String[] CALLLOG_PROJECTION = new String[] {
            CallLog.Calls._ID, // 0
        };
        final int ID_COLUMN_INDEX = 0;

        Cursor callsCursor = null;
        long startPointId = 0;
        long endPointId = 0;
        try {
            // Need test to see if order by _ID is ok here, or by date?
            callsCursor = mResolver.query(myUri, CALLLOG_PROJECTION, typeSelection, null,
                    CALLLOG_SORT_ORDER);
            if (callsCursor != null) {
                callsCursor.moveToPosition(startPoint - 1);
                startPointId = callsCursor.getLong(ID_COLUMN_INDEX);
                if (V) Log.v(TAG, "Call Log query startPointId = " + startPointId);
                if (startPoint == endPoint) {
                    endPointId = startPointId;
                } else {
                    callsCursor.moveToPosition(endPoint - 1);
                    endPointId = callsCursor.getLong(ID_COLUMN_INDEX);
                }
                if (V) Log.v(TAG, "Call log query endPointId = " + endPointId);
            }
        } catch (CursorWindowAllocationException e) {
            Log.e(TAG, "CursorWindowAllocationException while composing calllog vcards");
        } finally {
            if (callsCursor != null) {
                callsCursor.close();
                callsCursor = null;
            }
        }

        String recordSelection;
        if (startPoint == endPoint) {
            recordSelection = Calls._ID + "=" + startPointId;
        } else {
            // The query to call table is by "_id DESC" order, so change
            // correspondingly.
            recordSelection = Calls._ID + ">=" + endPointId + " AND " + Calls._ID + "<="
                    + startPointId;
        }

        String selection;
        if (typeSelection == null) {
            selection = recordSelection;
        } else {
            selection = "(" + typeSelection + ") AND (" + recordSelection + ")";
        }

        if (V) Log.v(TAG, "Call log query selection is: " + selection);

        return composeCallLogsAndSendSelectedVCards(op, selection, vcardType21, needSendBody,
                pbSize, null, ignorefilter, filter, vcardselector, vcardselectorop, vcardselect);
    }

    final int composeAndSendPhonebookVcards(Operation op, final int startPoint, final int endPoint,
            final boolean vcardType21, String ownerVCard, int needSendBody, int pbSize,
            boolean ignorefilter, byte[] filter, byte[] vcardselector, String vcardselectorop,
            boolean vcardselect) {
        if (startPoint < 1 || startPoint > endPoint) {
            Log.e(TAG, "internal error: startPoint or endPoint is not correct.");
            return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
        }

        final Uri myUri = DevicePolicyUtils.getEnterprisePhoneUri(mContext);
        Cursor contactCursor = null;
        Cursor contactIdCursor = new MatrixCursor(new String[] {
            Phone.CONTACT_ID
        });
        try {
            contactCursor = mResolver.query(
                    myUri, PHONES_CONTACTS_PROJECTION, null, null, Phone.CONTACT_ID);
            if (contactCursor != null) {
                contactIdCursor = ContactCursorFilter.filterByRange(contactCursor, startPoint,
                        endPoint);
            }
        } catch (CursorWindowAllocationException e) {
            Log.e(TAG, "CursorWindowAllocationException while composing phonebook vcards");
        } finally {
            if (contactCursor != null) {
                contactCursor.close();
            }
        }

        if (vcardselect)
            return composeContactsAndSendSelectedVCards(op, contactIdCursor, vcardType21,
                    ownerVCard, needSendBody, pbSize, ignorefilter, filter, vcardselector,
                    vcardselectorop);
        else
            return composeContactsAndSendVCards(
                    op, contactIdCursor, vcardType21, ownerVCard, ignorefilter, filter);
    }

    final int composeAndSendPhonebookOneVcard(Operation op, final int offset,
            final boolean vcardType21, String ownerVCard, int orderByWhat, boolean ignorefilter,
            byte[] filter) {
        if (offset < 1) {
            Log.e(TAG, "Internal error: offset is not correct.");
            return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
        }
        final Uri myUri = DevicePolicyUtils.getEnterprisePhoneUri(mContext);

        Cursor contactCursor = null;
        Cursor contactIdCursor = new MatrixCursor(new String[] {
            Phone.CONTACT_ID
        });
        // By default order is indexed
        String orderBy = Phone.CONTACT_ID;
        try {
            if (orderByWhat == BluetoothPbapObexServer.ORDER_BY_ALPHABETICAL) {
                orderBy = Phone.DISPLAY_NAME;
            }
            contactCursor = mResolver.query(myUri, PHONES_CONTACTS_PROJECTION, null, null, orderBy);
        } catch (CursorWindowAllocationException e) {
            Log.e(TAG,
                "CursorWindowAllocationException while composing phonebook one vcard");
        } finally {
            if (contactCursor != null) {
                contactIdCursor = ContactCursorFilter.filterByOffset(contactCursor, offset);
                contactCursor.close();
                contactCursor = null;
            }
        }
        return composeContactsAndSendVCards(
                op, contactIdCursor, vcardType21, ownerVCard, ignorefilter, filter);
    }

    /**
     * Filter contact cursor by certain condition.
     */
    private static final class ContactCursorFilter {
        /**
         *
         * @param contactCursor
         * @param offset
         * @return a cursor containing contact id of {@code offset} contact.
         */
        public static Cursor filterByOffset(Cursor contactCursor, int offset) {
            return filterByRange(contactCursor, offset, offset);
        }

        /**
         *
         * @param contactCursor
         * @param startPoint
         * @param endPoint
         * @return a cursor containing contact ids of {@code startPoint}th to {@code endPoint}th
         * contact.
         */
        public static Cursor filterByRange(Cursor contactCursor, int startPoint, int endPoint) {
            final int contactIdColumn = contactCursor.getColumnIndex(Data.CONTACT_ID);
            long previousContactId = -1;
            // As startPoint, endOffset index starts from 1 to n, we set
            // currentPoint base as 1 not 0
            int currentOffset = 1;
            final MatrixCursor contactIdsCursor = new MatrixCursor(new String[]{
                    Phone.CONTACT_ID
            });
            while (contactCursor.moveToNext() && currentOffset <= endPoint) {
                long currentContactId = contactCursor.getLong(contactIdColumn);
                if (previousContactId != currentContactId) {
                    previousContactId = currentContactId;
                    if (currentOffset >= startPoint) {
                        contactIdsCursor.addRow(new Long[]{currentContactId});
                        if (V) Log.v(TAG, "contactIdsCursor.addRow: " + currentContactId);
                    }
                    currentOffset++;
                }
            }
            return contactIdsCursor;
        }
    }

    /**
     * Handler enterprise contact id in VCardComposer
     */
    private static class EnterpriseRawContactEntitlesInfoCallback implements
            VCardComposer.RawContactEntitlesInfoCallback {
        @Override
        public VCardComposer.RawContactEntitlesInfo getRawContactEntitlesInfo(long contactId) {
            if (Contacts.isEnterpriseContactId(contactId)) {
                return new VCardComposer.RawContactEntitlesInfo(RawContactsEntity.CORP_CONTENT_URI,
                        contactId - Contacts.ENTERPRISE_CONTACT_ID_BASE);
            } else {
                return new VCardComposer.RawContactEntitlesInfo(RawContactsEntity.CONTENT_URI, contactId);
            }
        }
    }

    private final int composeContactsAndSendVCards(Operation op, final Cursor contactIdCursor,
            final boolean vcardType21, String ownerVCard, boolean ignorefilter, byte[] filter) {
        long timestamp = 0;
        if (V) timestamp = System.currentTimeMillis();

        VCardComposer composer = null;
        VCardFilter vcardfilter = new VCardFilter(ignorefilter ? null : filter);

        HandlerForStringBuffer buffer = null;
        try {
            // Currently only support Generic Vcard 2.1 and 3.0
            int vcardType;
            if (vcardType21) {
                vcardType = VCardConfig.VCARD_TYPE_V21_GENERIC;
            } else {
                vcardType = VCardConfig.VCARD_TYPE_V30_GENERIC;
            }
            if (!vcardfilter.isPhotoEnabled()) {
                vcardType |= VCardConfig.FLAG_REFRAIN_IMAGE_EXPORT;
            }

            // Enhancement: customize Vcard based on preferences/settings and
            // input from caller
            composer = BluetoothPbapUtils.createFilteredVCardComposer(mContext, vcardType, null);
            // End enhancement

            // BT does want PAUSE/WAIT conversion while it doesn't want the
            // other formatting
            // done by vCard library by default.
            composer.setPhoneNumberTranslationCallback(new VCardPhoneNumberTranslationCallback() {
                public String onValueReceived(String rawValue, int type, String label,
                        boolean isPrimary) {
                    // 'p' and 'w' are the standard characters for pause and
                    // wait
                    // (see RFC 3601)
                    // so use those when exporting phone numbers via vCard.
                    String numberWithControlSequence = rawValue
                            .replace(PhoneNumberUtils.PAUSE, 'p').replace(PhoneNumberUtils.WAIT,
                                    'w');
                    return numberWithControlSequence;
                }
            });
            buffer = new HandlerForStringBuffer(op, ownerVCard);
            Log.v(TAG, "contactIdCursor size: " + contactIdCursor.getCount());
            if (!composer.initWithCallback(contactIdCursor,
                    new EnterpriseRawContactEntitlesInfoCallback())
                    || !buffer.onInit(mContext)) {
                return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
            }

            //M: ALPS02620356: Add wakeLock to prevent cpu suspend when pulling Vcard
            if (mWakeLock == null) {
                PowerManager pm = (PowerManager)mContext.getSystemService(Context.POWER_SERVICE);
                mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                        "composeContactsAndSendVCards");
                Log.v(TAG, "acquire wakeLock");
                mWakeLock.acquire();
            }
            while (!composer.isAfterLast()) {
                if (BluetoothPbapObexServer.sIsAborted) {
                    ((ServerOperation) op).isAborted = true;
                    BluetoothPbapObexServer.sIsAborted = false;
                    break;
                }
                String vcard = composer.createOneEntry();
                if (vcard == null) {
                    Log.e(TAG,
                            "Failed to read a contact. Error reason: " + composer.getErrorReason());
                    return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
                }
                if (V) Log.v(TAG, "vCard from composer: " + vcard);

                vcard = vcardfilter.apply(vcard, vcardType21);
                vcard = StripTelephoneNumber(vcard);

                if (V) Log.v(TAG, "vCard after cleanup: " + vcard);

                if (!buffer.onEntryCreated(vcard)) {
                    // onEntryCreate() already emits error.
                    return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
                }
            }
        } finally {
            //M: ALPS02620356: Add wakeLock to prevent cpu suspend when pulling Vcard
            if (mWakeLock != null) {
                Log.v(TAG, "release wakeLock");
                mWakeLock.release();
                mWakeLock = null;
            }
            if (composer != null) {
                composer.terminate();
            }
            if (buffer != null) {
                buffer.onTerminate();
            }
        }

        if (V) Log.v(TAG, "Total vcard composing and sending out takes "
                    + (System.currentTimeMillis() - timestamp) + " ms");

        return ResponseCodes.OBEX_HTTP_OK;
    }

    private final int composeContactsAndSendSelectedVCards(Operation op,
            final Cursor contactIdCursor, final boolean vcardType21, String ownerVCard,
            int needSendBody, int pbSize, boolean ignorefilter, byte[] filter, byte[] selector,
            String vcardselectorop) {
        long timestamp = 0;
        if (V) timestamp = System.currentTimeMillis();

        VCardComposer composer = null;
        VCardFilter vcardfilter = new VCardFilter(ignorefilter ? null : filter);
        PropertySelector vcardselector = new PropertySelector(selector);

        HandlerForStringBuffer buffer = null;

        try {
            // Currently only support Generic Vcard 2.1 and 3.0
            int vcardType;
            if (vcardType21) {
                vcardType = VCardConfig.VCARD_TYPE_V21_GENERIC;
            } else {
                vcardType = VCardConfig.VCARD_TYPE_V30_GENERIC;
            }
            if (!vcardfilter.isPhotoEnabled()) {
                vcardType |= VCardConfig.FLAG_REFRAIN_IMAGE_EXPORT;
            }

            // Enhancement: customize Vcard based on preferences/settings and
            // input from caller
            composer = BluetoothPbapUtils.createFilteredVCardComposer(mContext, vcardType, null);
            // End enhancement

            /* BT does want PAUSE/WAIT conversion while it doesn't want the
             * other formatting done by vCard library by default. */
            composer.setPhoneNumberTranslationCallback(new VCardPhoneNumberTranslationCallback() {
                public String onValueReceived(
                        String rawValue, int type, String label, boolean isPrimary) {
                    /* 'p' and 'w' are the standard characters for pause and wait
                     * (see RFC 3601) so use those when exporting phone numbers via vCard.*/
                    String numberWithControlSequence = rawValue.replace(PhoneNumberUtils.PAUSE, 'p')
                                                               .replace(PhoneNumberUtils.WAIT, 'w');
                    return numberWithControlSequence;
                }
            });
            buffer = new HandlerForStringBuffer(op, ownerVCard);
            Log.v(TAG, "contactIdCursor size: " + contactIdCursor.getCount());
            if (!composer.initWithCallback(
                        contactIdCursor, new EnterpriseRawContactEntitlesInfoCallback())
                    || !buffer.onInit(mContext)) {
                return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
            }

            while (!composer.isAfterLast()) {
                if (BluetoothPbapObexServer.sIsAborted) {
                    ((ServerOperation) op).isAborted = true;
                    BluetoothPbapObexServer.sIsAborted = false;
                    break;
                }
                String vcard = composer.createOneEntry();
                if (vcard == null) {
                    Log.e(TAG,
                            "Failed to read a contact. Error reason: " + composer.getErrorReason());
                    return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
                }
                if (V) Log.v(TAG, "Checking selected bits in the vcard composer" + vcard);

                if (!vcardselector.CheckVcardSelector(vcard, vcardselectorop)) {
                    Log.e(TAG, "vcard selector check fail");
                    vcard = null;
                    pbSize--;
                    continue;
                }

                Log.e(TAG, "vcard selector check pass");

                if (needSendBody == NEED_SEND_BODY) {
                    vcard = vcardfilter.apply(vcard, vcardType21);
                    vcard = StripTelephoneNumber(vcard);

                    if (V) Log.v(TAG, "vCard after cleanup: " + vcard);

                    if (!buffer.onEntryCreated(vcard)) {
                        // onEntryCreate() already emits error.
                        return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
                    }
                }
            }

            if (needSendBody != NEED_SEND_BODY) return pbSize;
        } finally {
            if (composer != null) {
                composer.terminate();
            }
            if (buffer != null) {
                buffer.onTerminate();
            }
        }

        if (V)
            Log.v(TAG, "Total vcard composing and sending out takes "
                            + (System.currentTimeMillis() - timestamp) + " ms");

        return ResponseCodes.OBEX_HTTP_OK;
    }

    private final int composeCallLogsAndSendSelectedVCards(Operation op, final String selection,
            final boolean vcardType21, int needSendBody, int pbSize, String ownerVCard,
            boolean ignorefilter, byte[] filter, byte[] selector, String vcardselectorop,
            boolean vCardSelct) {
        long timestamp = 0;
        if (V) timestamp = System.currentTimeMillis();

        BluetoothPbapCallLogComposer composer = null;
        HandlerForStringBuffer buffer = null;

        try {
            VCardFilter vcardfilter = new VCardFilter(ignorefilter ? null : filter);
            PropertySelector vcardselector = new PropertySelector(selector);
            composer = new BluetoothPbapCallLogComposer(mContext);
            buffer = new HandlerForStringBuffer(op, ownerVCard);
            if (!composer.init(CallLog.Calls.CONTENT_URI, selection, null, CALLLOG_SORT_ORDER)
                    || !buffer.onInit(mContext)) {
                return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
            }

            while (!composer.isAfterLast()) {
                if (BluetoothPbapObexServer.sIsAborted) {
                    ((ServerOperation) op).isAborted = true;
                    BluetoothPbapObexServer.sIsAborted = false;
                    break;
                }
                String vcard = composer.createOneEntry(vcardType21);
                if (vCardSelct) {
                    if (!vcardselector.CheckVcardSelector(vcard, vcardselectorop)) {
                        Log.e(TAG, "Checking vcard selector for call log");
                        vcard = null;
                        pbSize--;
                        continue;
                    }
                    if (needSendBody == NEED_SEND_BODY) {
                        if (vcard != null) {
                            vcard = vcardfilter.apply(vcard, vcardType21);
                        }
                        if (vcard == null) {
                            Log.e(TAG, "Failed to read a contact. Error reason: "
                                            + composer.getErrorReason());
                            return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
                        }
                        if (V) {
                            Log.v(TAG, "Vcard Entry:");
                            Log.v(TAG, vcard);
                        }
                        buffer.onEntryCreated(vcard);
                    }
                } else {
                    if (vcard == null) {
                        Log.e(TAG, "Failed to read a contact. Error reason: "
                                        + composer.getErrorReason());
                        return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
                    }
                    if (V) {
                        Log.v(TAG, "Vcard Entry:");
                        Log.v(TAG, vcard);
                    }
                    buffer.onEntryCreated(vcard);
                }
            }
            if (needSendBody != NEED_SEND_BODY && vCardSelct) return pbSize;
        } finally {
            if (composer != null) {
                composer.terminate();
            }
            if (buffer != null) {
                buffer.onTerminate();
            }
        }

        if (V) Log.v(TAG, "Total vcard composing and sending out takes "
                + (System.currentTimeMillis() - timestamp) + " ms");
        return ResponseCodes.OBEX_HTTP_OK;
    }

    public String StripTelephoneNumber (String vCard){
        String attr [] = vCard.split(System.getProperty("line.separator"));
        String Vcard = "";
            for (int i=0; i < attr.length; i++) {
                if(attr[i].startsWith("TEL")) {
                    attr[i] = attr[i].replace("(", "");
                    attr[i] = attr[i].replace(")", "");
                    attr[i] = attr[i].replace("-", "");
                    attr[i] = attr[i].replace(" ", "");
                }
            }

            for (int i=0; i < attr.length; i++) {
                if(!attr[i].equals("")){
                    Vcard = Vcard.concat(attr[i] + "\n");
                }
            }
        if (V) Log.v(TAG, "Vcard with stripped telephone no.: " + Vcard);
        return Vcard;
    }

    /**
     * Handler to emit vCards to PCE.
     */
    public class HandlerForStringBuffer {
        private Operation operation;

        private OutputStream outputStream;

        private String phoneOwnVCard = null;

        public HandlerForStringBuffer(Operation op, String ownerVCard) {
            operation = op;
            if (ownerVCard != null) {
                phoneOwnVCard = ownerVCard;
                if (V) Log.v(TAG, "phone own number vcard:");
                if (V) Log.v(TAG, phoneOwnVCard);
            }
        }

        private boolean write(String vCard) {
            try {
                if (vCard != null) {
                    outputStream.write(vCard.getBytes());
                    return true;
                }
            } catch (IOException e) {
                Log.e(TAG, "write outputstrem failed" + e.toString());
            }
            return false;
        }

        public boolean onInit(Context context) {
            try {
                outputStream = operation.openOutputStream();
                if (phoneOwnVCard != null) {
                    return write(phoneOwnVCard);
                }
                return true;
            } catch (IOException e) {
                Log.e(TAG, "open outputstrem failed" + e.toString());
            }
            return false;
        }

        public boolean onEntryCreated(String vcard) {
            return write(vcard);
        }

        public void onTerminate() {
            if (!BluetoothPbapObexServer.closeStream(outputStream, operation)) {
                if (V) Log.v(TAG, "CloseStream failed!");
            } else {
                if (V) Log.v(TAG, "CloseStream ok!");
            }
        }
    }

    public static class VCardFilter {
        private static enum FilterBit {
            //       bit  property                  onlyCheckV21  excludeForV21
            FN (       1, "FN",                       true,         false),
            PHOTO(     3, "PHOTO",                    false,        false),
            BDAY(      4, "BDAY",                     false,        false),
            ADR(       5, "ADR",                      false,        false),
            EMAIL(     8, "EMAIL",                    false,        false),
            TITLE(    12, "TITLE",                    false,        false),
            ORG(      16, "ORG",                      false,        false),
            NOTE(     17, "NOTE",                     false,        false),
            URL(      20, "URL",                      false,        false),
            NICKNAME( 23, "NICKNAME",                 false,        true),
            DATETIME( 28, "X-IRMC-CALL-DATETIME",     false,        false);

            public final int pos;
            public final String prop;
            public final boolean onlyCheckV21;
            public final boolean excludeForV21;

            FilterBit(int pos, String prop, boolean onlyCheckV21, boolean excludeForV21) {
                this.pos = pos;
                this.prop = prop;
                this.onlyCheckV21 = onlyCheckV21;
                this.excludeForV21 = excludeForV21;
            }
        }

        private static final String SEPARATOR = System.getProperty("line.separator");
        private final byte[] filter;

        //This function returns true if the attributes needs to be included in the filtered vcard.
        private boolean isFilteredIn(FilterBit bit, boolean vCardType21) {
            final int offset = (bit.pos / 8) + 1;
            final int bit_pos = bit.pos % 8;
            if (!vCardType21 && bit.onlyCheckV21) return true;
            if (vCardType21 && bit.excludeForV21) return false;
            if (filter == null || offset >= filter.length) return true;
            return ((filter[filter.length - offset] >> bit_pos) & 0x01) != 0;
        }

        VCardFilter(byte[] filter) {
            this.filter = filter;
        }

        public boolean isPhotoEnabled() {
            return isFilteredIn(FilterBit.PHOTO, false);
        }

        public String apply(String vCard, boolean vCardType21){
            if (filter == null) return vCard;
            String lines[] = vCard.split(SEPARATOR);
            StringBuilder filteredVCard = new StringBuilder();
            boolean filteredIn = false;

            for (String line : lines) {
                // Check whether the current property is changing (ignoring multi-line properties)
                // and determine if the current property is filtered in.
                if (!Character.isWhitespace(line.charAt(0)) && !line.startsWith("=")) {
                    String currentProp = line.split("[;:]")[0];
                    filteredIn = true;

                    for (FilterBit bit : FilterBit.values()) {
                        if (bit.prop.equals(currentProp)) {
                            filteredIn = isFilteredIn(bit, vCardType21);
                            break;
                        }
                    }

                    // Since PBAP does not have filter bits for IM and SIP,
                    // exclude them by default. Easiest way is to exclude all
                    // X- fields, except date time....
                    if (currentProp.startsWith("X-")) {
                        filteredIn = false;
                        if (currentProp.equals("X-IRMC-CALL-DATETIME")) {
                            filteredIn = true;
                        }
                    }
                }

                // Build filtered vCard
                if (filteredIn) {
                    filteredVCard.append(line + SEPARATOR);
                }
            }

            return filteredVCard.toString();
        }
    }

    private static class PropertySelector {
        private static enum PropertyMask {
            //               bit    property
            VERSION(0, "VERSION"),
            FN(1, "FN"),
            NAME(2, "N"),
            PHOTO(3, "PHOTO"),
            BDAY(4, "BDAY"),
            ADR(5, "ADR"),
            LABEL(6, "LABEL"),
            TEL(7, "TEL"),
            EMAIL(8, "EMAIL"),
            TITLE(12, "TITLE"),
            ORG(16, "ORG"),
            NOTE(17, "NOTE"),
            URL(20, "URL"),
            NICKNAME(23, "NICKNAME"),
            DATETIME(28, "DATETIME");

            public final int pos;
            public final String prop;

            PropertyMask(int pos, String prop) {
                this.pos = pos;
                this.prop = prop;
            }
        }

        private static final String SEPARATOR = System.getProperty("line.separator");
        private final byte[] selector;

        PropertySelector(byte[] selector) {
            this.selector = selector;
        }

        private boolean checkbit(int attr_bit, byte[] selector) {
            int selectorlen = selector.length;
            if (((selector[selectorlen - 1 - ((int) attr_bit / 8)] >> (attr_bit % 8)) & 0x01)
                    == 0) {
                return false;
            }
            return true;
        }

        private boolean checkprop(String vcard, String prop) {
            String lines[] = vcard.split(SEPARATOR);
            boolean isPresent = false;
            for (String line : lines) {
                if (!Character.isWhitespace(line.charAt(0)) && !line.startsWith("=")) {
                    String currentProp = line.split("[;:]")[0];
                    if (prop.equals(currentProp)) {
                        Log.d(TAG, "bit.prop.equals current prop :" + prop);
                        isPresent = true;
                        return isPresent;
                    }
                }
            }

            return isPresent;
        }

        private boolean CheckVcardSelector(String vcard, String vcardselectorop) {
            boolean selectedIn = true;

            for (PropertyMask bit : PropertyMask.values()) {
                if (checkbit(bit.pos, selector)) {
                    Log.d(TAG, "checking for prop :" + bit.prop);
                    if (vcardselectorop.equals("0")) {
                        if (checkprop(vcard, bit.prop)) {
                            Log.d(TAG, "bit.prop.equals current prop :" + bit.prop);
                            selectedIn = true;
                            break;
                        } else {
                            selectedIn = false;
                        }
                    } else if (vcardselectorop.equals("1")) {
                        if (!checkprop(vcard, bit.prop)) {
                            Log.d(TAG, "bit.prop.notequals current prop" + bit.prop);
                            selectedIn = false;
                            return selectedIn;
                        } else {
                            selectedIn = true;
                        }
                    }
                }
            }
            return selectedIn;
        }

        private String getName(String vcard) {
            String lines[] = vcard.split(SEPARATOR);
            String name = "";
            for (String line : lines) {
                if (!Character.isWhitespace(line.charAt(0)) && !line.startsWith("=")) {
                    if (line.startsWith("N:"))
                        name = line.substring(line.lastIndexOf(':'), line.length());
                }
            }
            Log.d(TAG, "returning name: " + name);
            return name;
        }
    }

    private static final Uri getPhoneLookupFilterUri() {
        return PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI;
    }

    /**
     * Get size of the cursor without duplicated contact id. This assumes the
     * given cursor is sorted by CONTACT_ID.
     */
    private static final int getDistinctContactIdSize(Cursor cursor) {
        final int contactIdColumn = cursor.getColumnIndex(Data.CONTACT_ID);
        final int idColumn = cursor.getColumnIndex(Data._ID);
        long previousContactId = -1;
        int count = 0;
        cursor.moveToPosition(-1);
        while (cursor.moveToNext()) {
            final long contactId = cursor.getLong(contactIdColumn != -1 ? contactIdColumn : idColumn);
            if (previousContactId != contactId) {
                count++;
                previousContactId = contactId;
            }
        }
        if (V) {
            Log.i(TAG, "getDistinctContactIdSize result: " + count);
        }
        return count;
    }

    /**
     * Append "display_name,contact_id" string array from cursor to ArrayList.
     * This assumes the given cursor is sorted by CONTACT_ID.
     */
    private static void appendDistinctNameIdList(ArrayList<String> resultList,
            String defaultName, Cursor cursor) {
        final int contactIdColumn = cursor.getColumnIndex(Data.CONTACT_ID);
        final int idColumn = cursor.getColumnIndex(Data._ID);
        final int nameColumn = cursor.getColumnIndex(Data.DISPLAY_NAME);
        cursor.moveToPosition(-1);
        while (cursor.moveToNext()) {
            final long contactId = cursor.getLong(contactIdColumn != -1 ? contactIdColumn : idColumn);
            String displayName = nameColumn != -1 ? cursor.getString(nameColumn) : defaultName;
            if (TextUtils.isEmpty(displayName)) {
                displayName = defaultName;
            }

            String newString = displayName + "," + contactId;
            if (!resultList.contains(newString)) {
                resultList.add(newString);
            }
        }
        if (V) {
            for (String nameId : resultList) {
                Log.i(TAG, "appendDistinctNameIdList result: " + nameId);
            }
        }
    }
}
