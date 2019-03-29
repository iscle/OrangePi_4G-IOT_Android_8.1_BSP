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
package android.content.pm.cts.shortcutmanager.throttling;

import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.list;

import static junit.framework.Assert.assertTrue;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutManager;
import android.content.pm.cts.shortcutmanager.common.Constants;
import android.content.pm.cts.shortcutmanager.common.ReplyUtil;
import android.util.Log;


/**
 * Throttling test case.
 *
 * If we run it as a regular instrumentation test, the process would always considered to be in the
 * foreground and will never be throttled, so we use a broadcast to communicate from the
 * main test apk.
 */
public class ShortcutManagerThrottlingTestReceiver extends BroadcastReceiver {
    private ShortcutManager mManager;

    public ShortcutManager getManager(Context context) {
        if (mManager == null) {
            mManager = context.getSystemService(ShortcutManager.class);
        }
        return mManager;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Constants.ACTION_THROTTLING_TEST.equals(intent.getAction())) {
            final String replyAction = intent.getStringExtra(Constants.EXTRA_REPLY_ACTION);
            final String method = intent.getStringExtra(Constants.EXTRA_METHOD);
            switch (method) {
                case Constants.TEST_SET_DYNAMIC_SHORTCUTS:
                    testSetDynamicShortcuts(context, replyAction);
                    break;
                case Constants.TEST_ADD_DYNAMIC_SHORTCUTS:
                    testAddDynamicShortcuts(context, replyAction);
                    break;
                case Constants.TEST_UPDATE_SHORTCUTS:
                    testUpdateShortcuts(context, replyAction);
                    break;

                case Constants.TEST_BG_SERVICE_THROTTLED:
                    testBgServiceThrottled(context, replyAction);
                    break;

                case Constants.TEST_ACTIVITY_UNTHROTTLED:
                    testActivityUnthrottled(context, replyAction);
                    break;
                case Constants.TEST_FG_SERVICE_UNTHROTTLED:
                    testFgServiceUnthrottled(context, replyAction);
                    break;

                case Constants.TEST_INLINE_REPLY_SHOW:
                    testInlineReplyShow(context, replyAction);
                    break;
                case Constants.TEST_INLINE_REPLY_CHECK:
                    testInlineReplyCheck(context, replyAction);
                    break;

                default:
                    ReplyUtil.sendReply(context, replyAction, "Unknown test: " + method);
                    break;
            }
        }
    }

    public void testSetDynamicShortcuts(Context context, String replyAction) {
        Log.i(ThrottledTests.TAG, Constants.TEST_SET_DYNAMIC_SHORTCUTS);

        ReplyUtil.runTestAndReply(context, replyAction, () -> {
            ThrottledTests.assertThrottled(
                    context, () -> getManager(context).setDynamicShortcuts(list()));
        });
    }

    public void testAddDynamicShortcuts(Context context, String replyAction) {
        Log.i(ThrottledTests.TAG, Constants.TEST_ADD_DYNAMIC_SHORTCUTS);

        ReplyUtil.runTestAndReply(context, replyAction, () -> {
            ThrottledTests.assertThrottled(
                    context, () -> getManager(context).addDynamicShortcuts(list()));
        });
    }

    public void testUpdateShortcuts(Context context, String replyAction) {
        Log.i(ThrottledTests.TAG, Constants.TEST_UPDATE_SHORTCUTS);

        ReplyUtil.runTestAndReply(context, replyAction, () -> {
            ThrottledTests.assertThrottled(
                    context, () -> getManager(context).updateShortcuts(list()));
        });
    }

    public void testBgServiceThrottled(Context context, String replyAction) {
        ReplyUtil.runTestAndReply(context, replyAction, () -> {

            BgService.start(context, replyAction);
        });
    }

    public void testActivityUnthrottled(Context context, String replyAction) {
        ReplyUtil.runTestAndReply(context, replyAction, () -> {

            // First make sure the self is throttled.
            ThrottledTests.ensureThrottled(context);

            // Run the activity that runs the actual test.
            final Intent i =
                    new Intent().setComponent(new ComponentName(context, MyActivity.class))
                    .putExtra(Constants.EXTRA_REPLY_ACTION, replyAction)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            context.startActivity(i);
        });
    }

    public void testFgServiceUnthrottled(Context context, String replyAction) {
        ReplyUtil.runTestAndReply(context, replyAction, () -> {

            // First make sure the self is throttled.
            ThrottledTests.ensureThrottled(context);

            FgService.start(context, replyAction);
        });
    }

    public void testInlineReplyShow(Context context, String replyAction) {
        ReplyUtil.runTestAndReply(context, replyAction, () -> {
            // First make sure the self is throttled.
            ThrottledTests.ensureThrottled(context);

            InlineReply.showNotificationWithInlineReply(context);
        });
    }

    public void testInlineReplyCheck(Context context, String replyAction) {
        ReplyUtil.runTestAndReply(context, replyAction, () -> {
            // Throttling should be reset, can make calls now.
            assertTrue(context.getSystemService(ShortcutManager.class).setDynamicShortcuts(list()));
            assertTrue(context.getSystemService(ShortcutManager.class).addDynamicShortcuts(list()));
            assertTrue(context.getSystemService(ShortcutManager.class).updateShortcuts(list()));

            // Make sure it's not considered to be in the FG -> so eventually the caller should be
            // throttled.
            ThrottledTests.ensureThrottled(context);
        });
    }
}
