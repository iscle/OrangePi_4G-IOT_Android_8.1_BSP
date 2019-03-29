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
 * limitations under the License
 */
package android.provider.cts.contacts;

import android.content.ContentValues;
import android.net.Uri;
import android.test.AndroidTestCase;
import android.test.MoreAsserts;

/**
 * Make sure the provider is protected.
 *
 * Run with:
 * cts-tradefed run cts --class android.provider.cts.ContactsMetadataProviderTest < /dev/null
 */
public class ContactsMetadataProviderTest extends AndroidTestCase {

    /** The authority for the contacts metadata */
    public static final String METADATA_AUTHORITY = "com.android.contacts.metadata";

    /** A content:// style uri to the authority for the contacts metadata */
    public static final Uri METADATA_AUTHORITY_URI = Uri.parse(
            "content://" + METADATA_AUTHORITY);

    /**
     * The content:// style URI for this table.
     */
    public static final Uri CONTENT_URI = Uri.withAppendedPath(METADATA_AUTHORITY_URI,
            "metadata_sync");

    public void testCallerCheck() {
        try {
            getContext().getContentResolver().query(CONTENT_URI, null, null, null, null);
            fail();
        } catch (SecurityException e) {
            MoreAsserts.assertContainsRegex("can't access ContactMetadataProvider", e.getMessage());
        }
        try {
            getContext().getContentResolver().insert(CONTENT_URI, new ContentValues());
            fail();
        } catch (SecurityException e) {
            MoreAsserts.assertContainsRegex("can't access ContactMetadataProvider", e.getMessage());
        }
        try {
            getContext().getContentResolver().update(CONTENT_URI, new ContentValues(), null, null);
            fail();
        } catch (SecurityException e) {
            MoreAsserts.assertContainsRegex("can't access ContactMetadataProvider", e.getMessage());
        }
        try {
            getContext().getContentResolver().delete(CONTENT_URI, null, null);
            fail();
        } catch (SecurityException e) {
            MoreAsserts.assertContainsRegex("can't access ContactMetadataProvider", e.getMessage());
        }
    }
}
