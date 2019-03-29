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

package android.widget.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.ContentUris;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.widget.QuickContactBadge;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class QuickContactBadgeTest {
    @UiThreadTest
    @Test
    public void testPrioritizedMimetype() throws InterruptedException {
        final String plainMimeType = "text/plain";
        final Uri nonExistentContactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, 0);
        final CountDownLatch latch = new CountDownLatch(1);
        final Context context = new ContextWrapper(InstrumentationRegistry.getTargetContext()) {
            @Override
            public void startActivity(Intent intent) {
                testCallback(intent);
            }

            // @Override
            public void startActivityAsUser(Intent intent, UserHandle user) {
                testCallback(intent);
            }

            // @Override
            public void startActivityAsUser(Intent intent, Bundle options, UserHandle user) {
                testCallback(intent);
            }

            private void testCallback(Intent intent) {
                assertEquals(plainMimeType, intent.getStringExtra(
                        ContactsContract.QuickContact.EXTRA_PRIORITIZED_MIMETYPE));
                latch.countDown();
            }
        };

        // Execute: create QuickContactBadge with a prioritized mimetype and click on it
        QuickContactBadge badge = new QuickContactBadge(context);
        badge.setPrioritizedMimeType(plainMimeType);
        badge.assignContactUri(nonExistentContactUri);
        badge.onClick(badge);

        // Verify: the QuickContactBadge attempts to start an activity, and sets the
        // prioritized mimetype. We don't know which method will be used to start the activity,
        // so we check all options.
        assertTrue(latch.await(1, TimeUnit.SECONDS));
    }
}

