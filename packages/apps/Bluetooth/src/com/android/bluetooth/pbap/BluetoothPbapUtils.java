/************************************************************************************
 *
 *  Copyright (C) 2009-2012 Broadcom Corporation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ************************************************************************************/
package com.android.bluetooth.pbap;

import android.content.Context;
import android.content.ContentResolver;
import android.content.res.AssetFileDescriptor;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.Profile;
import android.provider.ContactsContract.RawContactsEntity;

import android.util.Log;

import com.android.vcard.VCardComposer;
import com.android.vcard.VCardConfig;
import com.android.bluetooth.Utils;
import com.android.bluetooth.pbap.BluetoothPbapService;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.Math;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;

public class BluetoothPbapUtils {
    private static final String TAG = "BluetoothPbapUtils";
    private static final boolean V = BluetoothPbapService.VERBOSE;

    public static int FILTER_PHOTO = 3;
    public static int FILTER_TEL = 7;
    public static int FILTER_NICKNAME = 23;
    private static final long QUERY_CONTACT_RETRY_INTERVAL = 4000;

    protected static AtomicLong mDbIdentifier = new AtomicLong();

    protected static long primaryVersionCounter = 0;
    protected static long secondaryVersionCounter = 0;
    public static long totalContacts = 0;

    /* totalFields and totalSvcFields used to update primary/secondary version
     * counter between pbap sessions*/
    public static long totalFields = 0;
    public static long totalSvcFields = 0;
    public static long contactsLastUpdated = 0;
    public static boolean contactsLoaded = false;

    private static class ContactData {
        private String name;
        private ArrayList<String> email;
        private ArrayList<String> phone;
        private ArrayList<String> address;

        public ContactData() {
            phone = new ArrayList<String>();
            email = new ArrayList<String>();
            address = new ArrayList<String>();
        }

        public ContactData(String name, ArrayList<String> phone, ArrayList<String> email,
                ArrayList<String> address) {
            this.name = name;
            this.phone = phone;
            this.email = email;
            this.address = address;
        }
    }

    private static HashMap<String, ContactData> contactDataset = new HashMap<String, ContactData>();

    private static HashSet<String> ContactSet = new HashSet<String>();

    private static final String TYPE_NAME = "name";
    private static final String TYPE_PHONE = "phone";
    private static final String TYPE_EMAIL = "email";
    private static final String TYPE_ADDRESS = "address";

    public static boolean hasFilter(byte[] filter) {
        return filter != null && filter.length > 0;
    }

    public static boolean isNameAndNumberOnly(byte[] filter) {
        // For vcard 2.0: VERSION,N,TEL is mandatory
        // For vcard 3.0, VERSION,N,FN,TEL is mandatory
        // So we only need to make sure that no other fields except optionally
        // NICKNAME is set

        // Check that an explicit filter is not set. If not, this means
        // return everything
        if (!hasFilter(filter)) {
            Log.v(TAG, "No filter set. isNameAndNumberOnly=false");
            return false;
        }

        // Check bytes 0-4 are all 0
        for (int i = 0; i <= 4; i++) {
            if (filter[i] != 0) {
                return false;
            }
        }
        // On byte 5, only BIT_NICKNAME can be set, so make sure
        // rest of bits are not set
        if ((filter[5] & 0x7F) > 0) {
            return false;
        }

        // Check byte 6 is not set
        if (filter[6] != 0) {
            return false;
        }

        // Check if bit#3-6 is set. Return false if so.
        if ((filter[7] & 0x78) > 0) {
            return false;
        }

        return true;
    }

    public static boolean isFilterBitSet(byte[] filter, int filterBit) {
        if (hasFilter(filter)) {
            int byteNumber = 7 - filterBit / 8;
            int bitNumber = filterBit % 8;
            if (byteNumber < filter.length) {
                return (filter[byteNumber] & (1 << bitNumber)) > 0;
            }
        }
        return false;
    }

    public static VCardComposer createFilteredVCardComposer(final Context ctx,
            final int vcardType, final byte[] filter) {
        int vType = vcardType;
        boolean includePhoto = BluetoothPbapConfig.includePhotosInVcard()
                    && (!hasFilter(filter) || isFilterBitSet(filter, FILTER_PHOTO));
        if (!includePhoto) {
            if (V) Log.v(TAG, "Excluding images from VCardComposer...");
            vType |= VCardConfig.FLAG_REFRAIN_IMAGE_EXPORT;
        }
        return new VCardComposer(ctx, vType, true);
    }

    public static boolean isProfileSet(Context context) {
        Cursor c = context.getContentResolver().query(
                Profile.CONTENT_VCARD_URI, new String[] { Profile._ID }, null,
                null, null);
        boolean isSet = (c != null && c.getCount() > 0);
        if (c != null) {
            c.close();
            c = null;
        }
        return isSet;
    }

    public static String getProfileName(Context context) {
        Cursor c = context.getContentResolver().query(
                Profile.CONTENT_URI, new String[] { Profile.DISPLAY_NAME}, null,
                null, null);
        String ownerName =null;
        if (c!= null && c.moveToFirst()) {
            ownerName = c.getString(0);
        }
        if (c != null) {
            c.close();
            c = null;
        }
        return ownerName;
    }
    public static final String createProfileVCard(Context ctx, final int vcardType,final byte[] filter) {
        VCardComposer composer = null;
        String vcard = null;
        try {
            composer = createFilteredVCardComposer(ctx, vcardType, filter);
            if (composer
                    .init(Profile.CONTENT_URI, null, null, null, null, Uri
                            .withAppendedPath(Profile.CONTENT_URI,
                                    RawContactsEntity.CONTENT_URI
                                            .getLastPathSegment()))) {
                vcard = composer.createOneEntry();
            } else {
                Log.e(TAG,
                        "Unable to create profile vcard. Error initializing composer: "
                                + composer.getErrorReason());
            }
        } catch (Throwable t) {
            Log.e(TAG, "Unable to create profile vcard.", t);
        }
        if (composer != null) {
            try {
                composer.terminate();
            } catch (Throwable t) {

            }
        }
        return vcard;
    }

    public static boolean createProfileVCardFile(File file, Context context) {
        FileInputStream is = null;
        FileOutputStream os = null;
        boolean success = true;
        try {
            AssetFileDescriptor fd = context.getContentResolver()
                    .openAssetFileDescriptor(Profile.CONTENT_VCARD_URI, "r");

            if(fd == null)
            {
                return false;
            }
            is = fd.createInputStream();
            os = new FileOutputStream(file);
            Utils.copyStream(is, os, 200);
        } catch (Throwable t) {
            Log.e(TAG, "Unable to create default contact vcard file", t);
            success = false;
        }
        Utils.safeCloseStream(is);
        Utils.safeCloseStream(os);
        return success;
    }

    protected static void savePbapParams(Context ctx, long primaryCounter, long secondaryCounter,
            long dbIdentifier, long lastUpdatedTimestamp, long totalFields, long totalSvcFields,
            long totalContacts) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(ctx);
        Editor edit = pref.edit();
        edit.putLong("primary", primaryCounter);
        edit.putLong("secondary", secondaryCounter);
        edit.putLong("dbIdentifier", dbIdentifier);
        edit.putLong("totalContacts", totalContacts);
        edit.putLong("lastUpdatedTimestamp", lastUpdatedTimestamp);
        edit.putLong("totalFields", totalFields);
        edit.putLong("totalSvcFields", totalSvcFields);
        edit.apply();

        if (V)
            Log.v(TAG, "Saved Primary:" + primaryCounter + ", Secondary:" + secondaryCounter
                            + ", Database Identifier: " + dbIdentifier);
    }

    /* fetchPbapParams() loads preserved value of Database Identifiers and folder
     * version counters. Servers using a database identifier 0 or regenerating
     * one at each connection will not benefit from the resulting performance and
     * user experience improvements. So database identifier is set with current
     * timestamp and updated on rollover of folder version counter.*/
    protected static void fetchPbapParams(Context ctx) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(ctx);
        long timeStamp = Calendar.getInstance().getTimeInMillis();
        BluetoothPbapUtils.mDbIdentifier.set(pref.getLong("mDbIdentifier", timeStamp));
        BluetoothPbapUtils.primaryVersionCounter = pref.getLong("primary", 0);
        BluetoothPbapUtils.secondaryVersionCounter = pref.getLong("secondary", 0);
        BluetoothPbapUtils.totalFields = pref.getLong("totalContacts", 0);
        BluetoothPbapUtils.contactsLastUpdated = pref.getLong("lastUpdatedTimestamp", timeStamp);
        BluetoothPbapUtils.totalFields = pref.getLong("totalFields", 0);
        BluetoothPbapUtils.totalSvcFields = pref.getLong("totalSvcFields", 0);
        if (V) Log.v(TAG, " fetchPbapParams " + pref.getAll());
    }

    /* loadAllContacts() fetches data like name,phone,email or addrees related to
     * all contacts. It is required to determine which field of the contact is
     * added/updated/deleted to increment secondary version counter accordingly.*/
    protected static void loadAllContacts(Context mContext, Handler mHandler) {
        if (V) Log.v(TAG, "Loading Contacts ...");

        try {
            String[] projection = {Data.CONTACT_ID, Data.DATA1, Data.MIMETYPE};
            int contactCount = 0;
            if ((contactCount = fetchAndSetContacts(
                         mContext, mHandler, projection, null, null, true))
                    < 0)
                return;
            totalContacts = contactCount; // to set total contacts count fetched on Connect
            contactsLoaded = true;
        } catch (Exception e) {
            Log.e(TAG, "Exception occurred in load contacts: " + e);
        }
    }

    protected static void updateSecondaryVersionCounter(Context mContext, Handler mHandler) {
        try {
            /* updated_list stores list of contacts which are added/updated after
             * the time when contacts were last updated. (contactsLastUpdated
             * indicates the time when contact/contacts were last updated and
             * corresponding changes were reflected in Folder Version Counters).*/
            ArrayList<String> updated_list = new ArrayList<String>();
            HashSet<String> currentContactSet = new HashSet<String>();
            int currentContactCount = 0;

            String[] projection = {Contacts._ID, Contacts.CONTACT_LAST_UPDATED_TIMESTAMP};
            Cursor c = mContext.getContentResolver().query(
                    Contacts.CONTENT_URI, projection, null, null, null);

            if (c == null) {
                Log.d(TAG, "Failed to fetch data from contact database");
                return;
            }
            while (c.moveToNext()) {
                String contactId = c.getString(0);
                long lastUpdatedTime = c.getLong(1);
                if (lastUpdatedTime > contactsLastUpdated) {
                    updated_list.add(contactId);
                }
                currentContactSet.add(contactId);
            }
            currentContactCount = c.getCount();
            c.close();

            if (V) Log.v(TAG, "updated list =" + updated_list);
            String[] dataProjection = {Data.CONTACT_ID, Data.DATA1, Data.MIMETYPE};

            String whereClause = Data.CONTACT_ID + "=?";

            /* code to check if new contact/contacts are added */
            if (currentContactCount > totalContacts) {
                for (int i = 0; i < updated_list.size(); i++) {
                    String[] selectionArgs = {updated_list.get(i)};
                    fetchAndSetContacts(
                            mContext, mHandler, dataProjection, whereClause, selectionArgs, false);
                    secondaryVersionCounter++;
                    primaryVersionCounter++;
                    totalContacts = currentContactCount;
                }
                /* When contact/contacts are deleted */
            } else if (currentContactCount < totalContacts) {
                totalContacts = currentContactCount;
                ArrayList<String> svcFields = new ArrayList<String>(
                        Arrays.asList(StructuredName.CONTENT_ITEM_TYPE, Phone.CONTENT_ITEM_TYPE,
                                Email.CONTENT_ITEM_TYPE, StructuredPostal.CONTENT_ITEM_TYPE));
                HashSet<String> deletedContacts = new HashSet<String>(ContactSet);
                deletedContacts.removeAll(currentContactSet);
                primaryVersionCounter += deletedContacts.size();
                secondaryVersionCounter += deletedContacts.size();
                if (V) Log.v(TAG, "Deleted Contacts : " + deletedContacts);

                // to decrement totalFields and totalSvcFields count
                for (String deletedContact : deletedContacts) {
                    ContactSet.remove(deletedContact);
                    String[] selectionArgs = {deletedContact};
                    Cursor dataCursor = mContext.getContentResolver().query(
                            Data.CONTENT_URI, dataProjection, whereClause, selectionArgs, null);

                    if (dataCursor == null) {
                        Log.d(TAG, "Failed to fetch data from contact database");
                        return;
                    }

                    while (dataCursor.moveToNext()) {
                        if (svcFields.contains(
                                    dataCursor.getString(dataCursor.getColumnIndex(Data.MIMETYPE))))
                            totalSvcFields--;
                        totalFields--;
                    }
                    dataCursor.close();
                }

                /* When contacts are updated. i.e. Fields of existing contacts are
                 * added/updated/deleted */
            } else {
                for (int i = 0; i < updated_list.size(); i++) {
                    primaryVersionCounter++;
                    ArrayList<String> phone_tmp = new ArrayList<String>();
                    ArrayList<String> email_tmp = new ArrayList<String>();
                    ArrayList<String> address_tmp = new ArrayList<String>();
                    String name_tmp = null, updatedCID = updated_list.get(i);
                    boolean updated = false;

                    String[] selectionArgs = {updated_list.get(i)};
                    Cursor dataCursor = mContext.getContentResolver().query(
                            Data.CONTENT_URI, dataProjection, whereClause, selectionArgs, null);

                    if (dataCursor == null) {
                        Log.d(TAG, "Failed to fetch data from contact database");
                        return;
                    }
                    // fetch all updated contacts and compare with cached copy of contacts
                    int indexData = dataCursor.getColumnIndex(Data.DATA1);
                    int indexMimeType = dataCursor.getColumnIndex(Data.MIMETYPE);
                    String data;
                    String mimeType;
                    while (dataCursor.moveToNext()) {
                        data = dataCursor.getString(indexData);
                        mimeType = dataCursor.getString(indexMimeType);
                        switch (mimeType) {
                            case Email.CONTENT_ITEM_TYPE:
                                email_tmp.add(data);
                                break;
                            case Phone.CONTENT_ITEM_TYPE:
                                phone_tmp.add(data);
                                break;
                            case StructuredPostal.CONTENT_ITEM_TYPE:
                                address_tmp.add(data);
                                break;
                            case StructuredName.CONTENT_ITEM_TYPE:
                                name_tmp = new String(data);
                                break;
                        }
                    }
                    ContactData cData =
                            new ContactData(name_tmp, phone_tmp, email_tmp, address_tmp);
                    dataCursor.close();

                    if ((name_tmp == null && contactDataset.get(updatedCID).name != null)
                            || (name_tmp != null && contactDataset.get(updatedCID).name == null)
                            || (!(name_tmp == null && contactDataset.get(updatedCID).name == null)
                                       && !name_tmp.equals(contactDataset.get(updatedCID).name))) {
                        updated = true;
                    } else if (checkFieldUpdates(contactDataset.get(updatedCID).phone, phone_tmp)) {
                        updated = true;
                    } else if (checkFieldUpdates(contactDataset.get(updatedCID).email, email_tmp)) {
                        updated = true;
                    } else if (checkFieldUpdates(
                                       contactDataset.get(updatedCID).address, address_tmp)) {
                        updated = true;
                    }

                    if (updated) {
                        secondaryVersionCounter++;
                        contactDataset.put(updatedCID, cData);
                    }
                }
            }

            Log.d(TAG, "primaryVersionCounter = " + primaryVersionCounter
                            + ", secondaryVersionCounter=" + secondaryVersionCounter);

            // check if Primary/Secondary version Counter has rolled over
            if (secondaryVersionCounter < 0 || primaryVersionCounter < 0)
                mHandler.sendMessage(
                        mHandler.obtainMessage(BluetoothPbapService.ROLLOVER_COUNTERS));
        } catch (Exception e) {
            Log.e(TAG, "Exception while updating secondary version counter:" + e);
        }
    }

    /* checkFieldUpdates checks update contact fields of a particular contact.
     * Field update can be a field updated/added/deleted in an existing contact.
     * Returns true if any contact field is updated else return false. */
    protected static boolean checkFieldUpdates(
            ArrayList<String> oldFields, ArrayList<String> newFields) {
        if (newFields != null && oldFields != null) {
            if (newFields.size() != oldFields.size()) {
                totalSvcFields += Math.abs(newFields.size() - oldFields.size());
                totalFields += Math.abs(newFields.size() - oldFields.size());
                return true;
            }
            for (int i = 0; i < newFields.size(); i++) {
                if (!oldFields.contains(newFields.get(i))) {
                    return true;
                }
            }
            /* when all fields of type(phone/email/address) are deleted in a given contact*/
        } else if (newFields == null && oldFields != null && oldFields.size() > 0) {
            totalSvcFields += oldFields.size();
            totalFields += oldFields.size();
            return true;

            /* when new fields are added for a type(phone/email/address) in a contact
             * for which there were no fields of this type earliar.*/
        } else if (oldFields == null && newFields != null && newFields.size() > 0) {
            totalSvcFields += newFields.size();
            totalFields += newFields.size();
            return true;
        }
        return false;
    }

    /* fetchAndSetContacts reads contacts and caches them
     * isLoad = true indicates its loading all contacts
     * isLoad = false indiacates its caching recently added contact in database*/
    protected static int fetchAndSetContacts(Context mContext, Handler mHandler,
            String[] projection, String whereClause, String[] selectionArgs, boolean isLoad) {
        long currentTotalFields = 0, currentSvcFieldCount = 0;
        Cursor c = mContext.getContentResolver().query(
                Data.CONTENT_URI, projection, whereClause, selectionArgs, null);

        /* send delayed message to loadContact when ContentResolver is unable
         * to fetch data from contact database using the specified URI at that
         * moment (Case: immediate Pbap connect on system boot with BT ON)*/
        if (c == null) {
            Log.d(TAG, "Failed to fetch contacts data from database..");
            if (isLoad)
                mHandler.sendMessageDelayed(
                        mHandler.obtainMessage(BluetoothPbapService.LOAD_CONTACTS),
                        QUERY_CONTACT_RETRY_INTERVAL);
            return -1;
        }

        int indexCId = c.getColumnIndex(Data.CONTACT_ID);
        int indexData = c.getColumnIndex(Data.DATA1);
        int indexMimeType = c.getColumnIndex(Data.MIMETYPE);
        String contactId, data, mimeType;
        while (c.moveToNext()) {
            contactId = c.getString(indexCId);
            data = c.getString(indexData);
            mimeType = c.getString(indexMimeType);
            /* fetch phone/email/address/name information of the contact */
            switch (mimeType) {
                case Phone.CONTENT_ITEM_TYPE:
                    setContactFields(TYPE_PHONE, contactId, data);
                    currentSvcFieldCount++;
                    break;
                case Email.CONTENT_ITEM_TYPE:
                    setContactFields(TYPE_EMAIL, contactId, data);
                    currentSvcFieldCount++;
                    break;
                case StructuredPostal.CONTENT_ITEM_TYPE:
                    setContactFields(TYPE_ADDRESS, contactId, data);
                    currentSvcFieldCount++;
                    break;
                case StructuredName.CONTENT_ITEM_TYPE:
                    setContactFields(TYPE_NAME, contactId, data);
                    currentSvcFieldCount++;
                    break;
            }
            ContactSet.add(contactId);
            currentTotalFields++;
        }
        c.close();

        /* This code checks if there is any update in contacts after last pbap
         * disconnect has happenned (even if BT is turned OFF during this time)*/
        if (isLoad && currentTotalFields != totalFields) {
            primaryVersionCounter += Math.abs(totalContacts - ContactSet.size());

            if (currentSvcFieldCount != totalSvcFields)
                if (totalContacts != ContactSet.size())
                    secondaryVersionCounter += Math.abs(totalContacts - ContactSet.size());
                else
                    secondaryVersionCounter++;
            if (primaryVersionCounter < 0 || secondaryVersionCounter < 0) rolloverCounters();

            totalFields = currentTotalFields;
            totalSvcFields = currentSvcFieldCount;
            contactsLastUpdated = System.currentTimeMillis();
            Log.d(TAG, "Contacts updated between last BT OFF and current"
                            + "Pbap Connect, primaryVersionCounter=" + primaryVersionCounter
                            + ", secondaryVersionCounter=" + secondaryVersionCounter);
        } else if (!isLoad) {
            totalFields++;
            totalSvcFields++;
        }
        return ContactSet.size();
    }

    /* setContactFields() is used to store contacts data in local cache (phone,
     * email or address which is required for updating Secondary Version counter).
     * contactsFieldData - List of field data for phone/email/address.
     * contactId - Contact ID, data1 - field value from data table for phone/email/address*/

    protected static void setContactFields(String fieldType, String contactId, String data) {
        ContactData cData = null;
        if (contactDataset.containsKey(contactId))
            cData = contactDataset.get(contactId);
        else
            cData = new ContactData();

        switch (fieldType) {
            case TYPE_NAME:
                cData.name = data;
                break;
            case TYPE_PHONE:
                cData.phone.add(data);
                break;
            case TYPE_EMAIL:
                cData.email.add(data);
                break;
            case TYPE_ADDRESS:
                cData.address.add(data);
                break;
        }
        contactDataset.put(contactId, cData);
    }

    /* As per Pbap 1.2 specification, Database Identifies shall be
     * re-generated when a Folder Version Counter rolls over or starts over.*/

    protected static void rolloverCounters() {
        mDbIdentifier.set(Calendar.getInstance().getTimeInMillis());
        primaryVersionCounter = (primaryVersionCounter < 0) ? 0 : primaryVersionCounter;
        secondaryVersionCounter = (secondaryVersionCounter < 0) ? 0 : secondaryVersionCounter;
        if (V) Log.v(TAG, "mDbIdentifier rolled over to:" + mDbIdentifier);
    }
}
