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

package com.android.cts.verifier.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.android.cts.verifier.R;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test to make sure, when an inline reply happens, the shortcut manager rate-limiting must
 * be reset.
 *
 * We use the "BOT" apk here, because rate-limiting will be reset when an app shows an activity
 * too -- so as long as this (or any) test activity is shown, CTS verifier won't be rate-limited.
 */
public class ShortcutThrottlingResetActivity extends InteractiveVerifierActivity {
    private static final String TAG = "ShortcutThrottlingReset";

    private static final String NOTIFICATION_BOT_PACKAGE = "com.android.cts.robot";

    private static final String ACTION_RESET_SETUP_NOTIFICATION =
            "com.android.cts.robot.ACTION_RESET_SETUP_NOTIFICATION";

    private static final String EXTRA_NOTIFICATION_TITLE = "EXTRA_NOTIFICATION_TITLE";
    private static final String EXTRA_RESET_REPLY_PACKAGE = "EXTRA_RESET_REPLY_PACKAGE";
    private static final String EXTRA_RESET_REPLY_ACTION = "EXTRA_RESET_REPLY_ACTION";
    private static final String EXTRA_RESET_REPLY_ERROR = "EXTRA_RESET_REPLY_ERROR";

    private static final String SUCCESS = "**SUCCESS**";

    private String mReplyAction;

    private final AtomicReference<Intent> mReplyIntent = new AtomicReference<>(null);

    @Override
    int getTitleResource() {
        return R.string.shortcut_reset_test;
    }

    @Override
    int getInstructionsResource() {
        return R.string.shortcut_reset_info;
    }

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        // Generate an unique reply action and register the reply receiver.
        mReplyAction = "reply_" + new SecureRandom().nextLong();
        final IntentFilter replyFilter = new IntentFilter(mReplyAction);
        registerReceiver(mReplyReceiver, replyFilter);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mReplyReceiver);
        super.onDestroy();
    }

    @Override
    protected List<InteractiveTestCase> createTestItems() {
        List<InteractiveTestCase> tests = new ArrayList<>();
        tests.add(new CheckForBot());
        tests.add(new SetupNotification());
        tests.add(new WaitForTestReply());
        tests.add(new CheckResult());
        return tests;
    }


    private final BroadcastReceiver mReplyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Received reply from robot helper: " + intent);
            mReplyIntent.set(intent);
        }
    };


    /** Make sure the helper package is installed. */
    protected class CheckForBot extends InteractiveTestCase {
        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.shortcut_reset_bot);
        }

        @Override
        void test() {
            PackageManager pm = mContext.getPackageManager();
            try {
                pm.getPackageInfo(NOTIFICATION_BOT_PACKAGE, 0);
                status = PASS;
            } catch (PackageManager.NameNotFoundException e) {
                status = FAIL;
                logFail("You must install the CTS Robot helper, aka " + NOTIFICATION_BOT_PACKAGE);
            }
            next();
        }
    }

    /**
     * Request the bot apk to show the notification.
     */
    protected class SetupNotification extends InteractiveTestCase {
        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.shortcut_reset_start);
        }

        @Override
        void test() {
            final Intent intent = new Intent(ACTION_RESET_SETUP_NOTIFICATION);
            intent.setPackage(NOTIFICATION_BOT_PACKAGE);

            intent.putExtra(EXTRA_NOTIFICATION_TITLE, getResources().getString(getTitleResource()));

            intent.putExtra(EXTRA_RESET_REPLY_PACKAGE, getPackageName());
            intent.putExtra(EXTRA_RESET_REPLY_ACTION, mReplyAction);

            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            sendBroadcast(intent);
            status = PASS;
            next();
        }
    }

    /**
     * Let the human tester do an inline reply, and wait for the reply broadcast from the bot apk.
     */
    protected class WaitForTestReply extends InteractiveTestCase {
        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.shortcut_reset_prompt_inline_reply);
        }

        @Override
        void test() {
            final Intent replyIntent = mReplyIntent.get();
            if (replyIntent == null) {
                // Reply not received yet.
                status = RETEST;
                delay();
                return;
            }
            status = PASS;
            next();
        }
    }

    /**
     * Check the reply from the bot apk.
     */
    protected class CheckResult extends InteractiveTestCase {
        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.shortcut_reset_check_result);
        }

        @Override
        void test() {
            final Intent replyIntent = mReplyIntent.get();
            if (replyIntent == null) {
                logFail("Internal error, replyIntent shouldn't be null here.");
                status = FAIL;
                return;
            }
            final String error = replyIntent.getStringExtra(EXTRA_RESET_REPLY_ERROR);
            if (SUCCESS.equals(error)) {
                status = PASS;
                next();
                return;
            }
            logFail("Test failed. Error message=" + error);
            status = FAIL;
            next();
        }
    }
}
