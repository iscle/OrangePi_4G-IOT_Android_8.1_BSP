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

package android.provider.cts.contacts;

import android.content.ContentUris;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Contacts;
import android.provider.ContactsContract;
import android.provider.ContactsContract.QuickContact;
import android.test.InstrumentationTestCase;
import android.test.UiThreadTest;
import android.view.View;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ContactsContract_QuickContactsTest extends InstrumentationTestCase {

    final String EXCLUDED_MIME_TYPES[] = {"exclude1", "exclude2"};
    final String PLAIN_MIME_TYPE = "text/plain";
    final Uri FAKE_CONTACT_URI = ContentUris.withAppendedId(Contacts.CONTENT_URI, 0);

    @UiThreadTest
    public void testPrioritizedMimeTypeAndExcludedMimeTypes() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(2);
        Context context = new ContextWrapper(getInstrumentation().getContext()) {
            @Override
            public void startActivity(Intent intent) {
                testCallback(intent);
            }

            @Override
            public void startActivityAsUser(Intent intent, UserHandle user) {
                testCallback(intent);
            }

            @Override
            public void startActivityAsUser(Intent intent, Bundle options, UserHandle user) {
                testCallback(intent);
            }

            private void testCallback(Intent intent) {
                assertEquals(PLAIN_MIME_TYPE, intent.getStringExtra(
                        QuickContact.EXTRA_PRIORITIZED_MIMETYPE));
                String excludedMimeTypes[]
                        = intent.getStringArrayExtra(QuickContact.EXTRA_EXCLUDE_MIMES);
                assertTrue(Arrays.equals(excludedMimeTypes, EXCLUDED_MIME_TYPES));
                latch.countDown();
            }
        };

        // Execute
        ContactsContract.QuickContact.showQuickContact(context, new View(context),
                FAKE_CONTACT_URI, EXCLUDED_MIME_TYPES, PLAIN_MIME_TYPE);
        ContactsContract.QuickContact.showQuickContact(context, (Rect) null,
                FAKE_CONTACT_URI, EXCLUDED_MIME_TYPES, PLAIN_MIME_TYPE);

        // Verify: the start activity call sets the prioritized mimetype and excludes mimetypes.
        // We don't know which method will be used to start the activity, so we check all options.
        assertTrue(latch.await(1, TimeUnit.SECONDS));
    }
}