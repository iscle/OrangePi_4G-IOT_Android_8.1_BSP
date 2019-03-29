/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.cts.monkey;

import com.android.tradefed.device.CollectingOutputReceiver;

import java.util.concurrent.TimeUnit;

public class CategoryTest extends AbstractMonkeyTest {

    private static final long MAX_TIMEOUT = 5 * 60 * 1000; // 5 min

    public void testDefaultCategories() throws Exception {
        String cmd = MONKEY_CMD + " -v -p " + PKGS[0] + " 5000";
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        try {
            mDevice.executeShellCommand(cmd, receiver, MAX_TIMEOUT, TimeUnit.MILLISECONDS, 0);
            String out = receiver.getOutput();
            assertTrue(out.contains("cmp=com.android.cts.monkey/.MonkeyActivity"));
            assertTrue(out.contains("cmp=com.android.cts.monkey/.BaboonActivity"));
        } finally {
            receiver.cancel();
            receiver.clearBuffer();
            receiver = null;
        }
    }

    public void testSingleCategory() throws Exception {
        String cmd = MONKEY_CMD + " -v -p " + PKGS[0]
                + " -c android.intent.category.LAUNCHER 5000";
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        try {
            mDevice.executeShellCommand(cmd, receiver, MAX_TIMEOUT, TimeUnit.MILLISECONDS, 0);
            String out = receiver.getOutput();
            assertTrue(out.contains("cmp=com.android.cts.monkey/.MonkeyActivity"));
            assertFalse(out.contains("cmp=com.android.cts.monkey/.BaboonActivity"));
        } finally {
            receiver.cancel();
            receiver.clearBuffer();
            receiver = null;
        }

        CollectingOutputReceiver receiver2 = new CollectingOutputReceiver();
        try {
            mDevice.executeShellCommand(MONKEY_CMD + " -v -p " + PKGS[0]
                    + " -c android.intent.category.MONKEY 5000", receiver2, MAX_TIMEOUT,
                    TimeUnit.MILLISECONDS, 0);
            String out = receiver2.getOutput();
            assertFalse(out.contains("cmp=com.android.cts.monkey/.MonkeyActivity"));
            assertTrue(out.contains("cmp=com.android.cts.monkey/.BaboonActivity"));
        } finally {
            receiver2.cancel();
            receiver2.clearBuffer();
            receiver2 = null;
        }
    }

    public void testMultipleCategories() throws Exception {
        String cmd = MONKEY_CMD + " -v -p " + PKGS[0]
                + " -c android.intent.category.LAUNCHER"
                + " -c android.intent.category.MONKEY 5000";
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        try {
            mDevice.executeShellCommand(cmd, receiver, MAX_TIMEOUT, TimeUnit.MILLISECONDS, 0);
            String out = receiver.getOutput();
            assertTrue(out.contains("cmp=com.android.cts.monkey/.MonkeyActivity"));
            assertTrue(out.contains("cmp=com.android.cts.monkey/.BaboonActivity"));
        } finally {
            receiver.cancel();
            receiver.clearBuffer();
            receiver = null;
        }
    }
}
