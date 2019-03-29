/*
 * Copyright (C) 2015 The Android Open Source Project
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


package android.permission2.cts;

import android.content.ContentValues;
import android.provider.ContactsContract;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

/**
 * Verify that deprecated contacts permissions are not enforced.
 */
public class ContactsProviderTest extends AndroidTestCase {

    /**
     * Verifies that query(ContactsContract.Contacts.CONTENT_URI) only requires
     * permission {@link android.Manifest.permission#READ_CONTACTS}.
     */
    @SmallTest
    public void testQueryContacts() {
        getContext().getContentResolver().query(ContactsContract.Contacts.CONTENT_URI,
                null, null, null, null);
    }

    /**
     * Verifies that insert(ContactsContract.Contacts.CONTENT_URI) only requires
     * permission {@link android.Manifest.permission#WRITE_CONTACTS}.
     */
    @SmallTest
    public void testInsertContacts() {
        try {
            getContext().getContentResolver().insert(ContactsContract.Contacts.CONTENT_URI,
                    new ContentValues());
        } catch (SecurityException e) {
            fail("insert(ContactsContract.Contacts.CONTENT_URI) threw SecurityException");
        } catch (UnsupportedOperationException e) {
            // It is okay for this fail in this manner.
        }
    }

    /**
     * Verifies that query(ContactsContract.Profile.CONTENT_URI) only requires
     * permission {@link android.Manifest.permission#READ_CONTACTS}.
     */
    @SmallTest
    public void testQueryProfile() {
        getContext().getContentResolver().query(ContactsContract.Profile.CONTENT_URI,
                null, null, null, null);
    }

    /**
     * Verifies that insert(ContactsContract.Profile.CONTENT_URI) only requires
     * permission {@link android.Manifest.permission#WRITE_CONTACTS}. The provider won't
     * actually let us execute this. But at least it shouldn't throw a security exception.
     */
    @SmallTest
    public void testInsertProfile() {
     try {
         getContext().getContentResolver().insert(ContactsContract.Profile.CONTENT_URI,
                new ContentValues(0));
        } catch (SecurityException e) {
            fail("insert(ContactsContract.Profile.CONTENT_URI) threw SecurityException");
        } catch (UnsupportedOperationException e) {
            // It is okay for this fail in this manner.
        }
    }

    /**
     * Verifies that update(ContactsContract.Profile.CONTENT_URI) only requires
     * permission {@link android.Manifest.permission#WRITE_CONTACTS}.
     */
    @SmallTest
    public void testUpdateProfile() {
        getContext().getContentResolver().update(ContactsContract.Profile.CONTENT_URI,
                new ContentValues(0), null, null);
    }
}
