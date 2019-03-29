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

package android.app.cts;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import android.app.stubs.NewDocumentTestActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.test.AndroidTestCase;

public class NewDocumentTest extends AndroidTestCase {
    private static Uri TEST_URI = Uri.parse("test_uri");
    private static long TIMEOUT_MS = 3000;

    public void testNewDocument() throws InterruptedException {
        final Intent intent = new Intent();
        intent.setClass(getContext(), NewDocumentTestActivity.class);
        intent.setData(TEST_URI);

        try (final Receiver receiver = new Receiver(NewDocumentTestActivity.NOTIFY_RESUME)) {
            getContext().startActivity(intent);
            receiver.await();
        }

        try (final Receiver receiver = new Receiver(NewDocumentTestActivity.NOTIFY_NEW_INTENT)) {
            getContext().startActivity(intent);
            receiver.await();
        }
    }

    private class Receiver extends BroadcastReceiver implements AutoCloseable {
        private final CountDownLatch latch = new CountDownLatch(1);

        Receiver(String action) {
            getContext().registerReceiver(this, new IntentFilter(action));
        }

        void await() throws InterruptedException {
            assertTrue(
                    "Timeout for broadcast from activity",
                    latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            latch.countDown();
        }

        @Override
        public void close() {
            getContext().unregisterReceiver(this);
        }
    }
}
