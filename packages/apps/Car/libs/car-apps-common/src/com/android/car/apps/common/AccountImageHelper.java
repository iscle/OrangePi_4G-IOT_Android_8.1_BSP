/*
 * Copyright (C) 2016 The Android Open Source Project
 *
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
package com.android.car.apps.common;

import android.accounts.Account;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.Intent.ShortcutIconResource;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;

/**
 * Utility functions for retrieving account pictures.
 * @hide
 */
public final class AccountImageHelper {

    static final String[] CONTACT_PROJECTION_DATA = new String[] {
        ContactsContract.Data._ID,
        ContactsContract.Data.CONTACT_ID,
        ContactsContract.Data.RAW_CONTACT_ID,
        ContactsContract.Data.LOOKUP_KEY,
        ContactsContract.Data.PHOTO_URI,
        ContactsContract.Data.PHOTO_FILE_ID
    };
    static final String CONTACT_SELECTION =
            ContactsContract.CommonDataKinds.Email.ADDRESS + " LIKE ?";

    /**
     * Non instantiable.
     */
    private AccountImageHelper() {
    }

    /**
     * Tries to retrieve the Picture for the provided account, from the Contacts database.
     */
    public static String getAccountPictureUri(Context context, Account account) {
        // Look up this account in the contacts database.

        String[] selectionArgs = new String[] {
        account.name };
        Cursor c = null;
        long contactId = -1;
        String lookupKey = null;
        String photoUri = null;
        int photoFileId = 0;
        long rawContactId = 0;
        try {
            c = context.getContentResolver().query(ContactsContract.Data.CONTENT_URI,
                    CONTACT_PROJECTION_DATA, CONTACT_SELECTION, selectionArgs, null);
            if (c.moveToNext()) {
                contactId = c.getLong(1);
                rawContactId = c.getLong(2);
                lookupKey = c.getString(3);
                photoUri = c.getString(4);
                photoFileId = c.getInt(5);
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }

        if (contactId != -1 && !TextUtils.isEmpty(lookupKey) && !TextUtils.isEmpty(photoUri)) {
            if (photoFileId == 0) {
                // Trigger a VIEW action on this photo, which will force the Contacts
                // Sync adapter to sync the HiRes version of the contact photo.
                syncContactHiResPhoto(context, rawContactId);
            }
            return photoUri;
        }
        return getDefaultPictureUri(context);
    }

    private static void syncContactHiResPhoto(Context context, long rawContactId) {
        final String serviceName = "com.google.android.syncadapters.contacts." +
                "SyncHighResPhotoIntentService";
        final String servicePackageName = "com.google.android.syncadapters.contacts";
        final Uri uri = ContentUris.withAppendedId(RawContacts.CONTENT_URI,
                rawContactId);
        final Intent intent = new Intent();
        intent.setClassName(servicePackageName, serviceName);
        intent.setAction(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, RawContacts.CONTENT_ITEM_TYPE);
        try {
            context.startService(intent);
        } catch (Exception e) {

        }
    }

    /**
     * Returns a default image to be used when an account has no picture associated with it.
     */
    public static String getDefaultPictureUri(Context context) {
        // TODO: get a better default image.
        ShortcutIconResource iconResource = new ShortcutIconResource();
        iconResource.packageName = context.getPackageName();
        iconResource.resourceName = context.getResources().getResourceName(
                R.drawable.ic_contact);
        return UriUtils.getShortcutIconResourceUri(iconResource).toString();
    }
}
