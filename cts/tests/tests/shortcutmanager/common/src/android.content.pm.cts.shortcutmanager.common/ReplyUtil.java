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
package android.content.pm.cts.shortcutmanager.common;

import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.retryUntil;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;

import junit.framework.Assert;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class ReplyUtil {
    private ReplyUtil() {
    }

    private static final String MAIN_CTS_PACKAGE = "android.content.pm.cts.shortcutmanager";

    public static void runTestAndReply(Context context, String replyAction, Runnable test) {
        try {
            test.run();

            sendReply(context, replyAction, null);
        } catch (Throwable e) {
            String error = "Test failed: " + e.getMessage() + "\n" + Log.getStackTraceString(e);
            sendReply(context, replyAction, error);
        }
    }

    public static void sendReply(Context context, String replyAction,
            String failureMessageOrNullForSuccess) {
        // Create the reply bundle.
        final Bundle ret = new Bundle();
        if (failureMessageOrNullForSuccess == null) {
            ret.putBoolean("success", true);
        } else {
            ret.putString("error", failureMessageOrNullForSuccess);
        }

        // Send reply
        final Intent reply = new Intent(replyAction).setPackage(MAIN_CTS_PACKAGE);
        reply.putExtras(ret);

        context.sendBroadcast(reply);
    }

    public static void sendSuccessReply(Context context, String replyAction) {
        sendReply(context, replyAction, null);
    }

    public static void invokeAndWaitForReply(Context context, Consumer<String> r) {
        final AtomicReference<Intent> ret = new AtomicReference<>();

        // Register the reply receiver

        // Use a random reply action every time.
        final String replyAction = Constants.ACTION_REPLY + Constants.sRandom.nextLong();
        final IntentFilter filter = new IntentFilter(replyAction);

        final BroadcastReceiver resultReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                ret.set(intent);
            }
        };

        context.registerReceiver(resultReceiver, filter);

        try {
            // Run the code.
            r.accept(replyAction);

            // Wait for the response.
            retryUntil(() -> ret.get() != null, "Didn't receive result broadcast", 120);

            if (ret.get().getExtras().getBoolean("success")) {
                return;
            }
            Assert.fail(ret.get().getExtras().getString("error"));
        } finally {
            context.unregisterReceiver(resultReceiver);
        }
    }
}
