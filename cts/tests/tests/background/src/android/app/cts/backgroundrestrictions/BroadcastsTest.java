/*
 * Copyright (C) 2017 The Android Open Source Project
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
package android.app.cts.backgroundrestrictions;

import static junit.framework.Assert.assertFalse;

import static org.junit.Assert.assertTrue;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@RunWith(AndroidJUnit4.class)
public class BroadcastsTest {
    private static final String TAG = "BroadcastsTest";

    private final int BROADCASTS_TIMEOUT_SECOND = 3 * 60;

    private static Context getContext() {
        return InstrumentationRegistry.getContext();
    }

    /**
     * Make sure "com.android.launcher.action.INSTALL_SHORTCUT" won't be delivered to a runtime
     * receiver.
     */
    @Test
    public void testNonSupportedBroadcastsNotDelivered_runtimeReceiver() throws Exception {

        // Need a reference here to initialize it in a lambda.
        final AtomicReference<BroadcastReceiver> receiverRef = new AtomicReference<>();

        testNonSupportedBroadcastsNotDelivered(
                (filter, callback) -> {
                    final BroadcastReceiver receiver = new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            callback.accept(intent);
                        }
                    };
                    receiverRef.set(receiver);

                    getContext().registerReceiver(receiver, filter);
                },
                (intent) -> {},
                () -> getContext().unregisterReceiver(receiverRef.get()));
    }

    /**
     * Make sure "com.android.launcher.action.INSTALL_SHORTCUT" won't be delivered to a manifest
     * receiver, even if an intent is targeted to the component.
     */
    @Test
    public void testNonSupportedBroadcastsNotDelivered_manifestReceiver() throws Exception {
        // Need a reference here to initialize it in a lambda.
        final AtomicReference<BroadcastReceiver> receiverRef = new AtomicReference<>();

        testNonSupportedBroadcastsNotDelivered(
                (filter, callback) -> {
                    MyReceiver.setCallback((intent) -> callback.accept(intent));
                },
                (intent) -> intent.setComponent(MyReceiver.getComponent()),
                () -> MyReceiver.clearCallback());
    }

    private void testNonSupportedBroadcastsNotDelivered(
            BiConsumer<IntentFilter, Consumer<Intent>> receiverInitializer,
            Consumer<Intent> intentInitializer,
            Runnable receiverDeinitializer) throws Exception {
        // This broadcast should be delivered.
        final String[] UNSUPPORTED_BROADCASTS = new String[]{
                "com.android.launcher.action.INSTALL_SHORTCUT",
        };
        // These broadcasts should be delivered.
        final String[] SUPPORTED_BROADCASTS = new String[]{
                Intent.ACTION_VIEW,
                Intent.ACTION_SEND,
        };
        final String[][] ALL_BROADCASTS = new String[][]{
                UNSUPPORTED_BROADCASTS,
                SUPPORTED_BROADCASTS,
        };

        // GuardedBy receivedBroadcasts
        final ArrayList<String> receivedBroadcasts = new ArrayList<>();

        final CountDownLatch latch = new CountDownLatch(SUPPORTED_BROADCASTS.length);

        // Register a receiver for all the actions.
        final IntentFilter filter = new IntentFilter();
        for (String[] list : ALL_BROADCASTS) {
            for (String action : list) {
                filter.addAction(action);
            }
        }

        // This is what's called when a receiver receives an intent.
        final Consumer<Intent> callbackHandler = (intent) -> {
            Log.i(TAG, "Intent " + intent + " received.");
            synchronized (receivedBroadcasts) {
                receivedBroadcasts.add(intent.getAction());
            }

            latch.countDown();
        };

        receiverInitializer.accept(filter, callbackHandler);
        try {
            // Send all broadcasts one by one.
            for (String[] list : ALL_BROADCASTS) {
                for (String action : list) {
                    final Intent intent = new Intent(action)
                        .setFlags(Intent.FLAG_RECEIVER_FOREGROUND);

                    intentInitializer.accept(intent);

                    getContext().sendBroadcast(intent);
                }
            }
            assertTrue(latch.await(BROADCASTS_TIMEOUT_SECOND, TimeUnit.SECONDS));

            final String history =
                    SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(),
                    "dumpsys activity broadcasts history");
            Log.v(TAG, "Broadcast history:\n");
            for (String line : history.split("\n")) {
                Log.v(TAG, line);
            }


            // Verify the received lists.
            // The supported ones should be delivered, and show up in the history.
            // The unsupported should not.
            synchronized (receivedBroadcasts) {
                for (String action : SUPPORTED_BROADCASTS) {
                    assertTrue(receivedBroadcasts.contains(action));
                    assertTrue(history.contains(action));
                }

                for (String action : UNSUPPORTED_BROADCASTS) {
                    assertFalse(receivedBroadcasts.contains(action));
                    assertFalse(history.contains(action));
                }
            }

        } finally {
            receiverDeinitializer.run();
        }
    }
}
