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
package android.content.pm.cts.shortcutmanager;


import static android.content.pm.cts.shortcutmanager.common.Constants.INLINE_REPLY_REMOTE_INPUT_CAPTION;

import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.resetThrottling;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.runCommandForNoOutput;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.cts.shortcutmanager.common.Constants;
import android.content.pm.cts.shortcutmanager.common.ReplyUtil;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.test.suitebuilder.annotation.SmallTest;
import android.test.suitebuilder.annotation.Suppress;
import android.view.KeyEvent;

/**
 * The actual test is implemented in the CtsShortcutManagerThrottlingTest module.
 * This class uses broadcast receivers to communicate with it, because if we just used an
 * instrumentation test, the target process would never been throttled.
 */
@SmallTest
public class ShortcutManagerThrottlingTest extends ShortcutManagerCtsTestsBase {

    private static final int UI_TIMEOUT = 5000;

    private static final String TARGET_PACKAGE =
            "android.content.pm.cts.shortcutmanager.throttling";

    private void callTest(String method) {
        ReplyUtil.invokeAndWaitForReply(getTestContext(), (replyAction) -> {
            final Intent i = new Intent(Constants.ACTION_THROTTLING_TEST);
            i.putExtra(Constants.EXTRA_METHOD, method);
            i.putExtra(Constants.EXTRA_REPLY_ACTION, replyAction);
            i.setComponent(ComponentName.unflattenFromString(
                    TARGET_PACKAGE + "/.ShortcutManagerThrottlingTestReceiver"));
            getTestContext().sendBroadcast(i);
        });
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        resetThrottling(getInstrumentation());

        UiDevice.getInstance(getInstrumentation()).pressHome();

        runCommandForNoOutput(getInstrumentation(), "am force-stop " + TARGET_PACKAGE);
    }

    public void testSetDynamicShortcuts() throws InterruptedException {
        callTest(Constants.TEST_SET_DYNAMIC_SHORTCUTS);
    }

    public void testAddDynamicShortcuts() throws InterruptedException {
        callTest(Constants.TEST_ADD_DYNAMIC_SHORTCUTS);
    }

    public void testUpdateShortcuts() throws InterruptedException {
        callTest(Constants.TEST_UPDATE_SHORTCUTS);
    }

    public void testBgServiceThrottled() throws InterruptedException {
        callTest(Constants.TEST_BG_SERVICE_THROTTLED);
    }

    public void testActivityUnthrottled() throws InterruptedException {
        callTest(Constants.TEST_ACTIVITY_UNTHROTTLED);
    }

    public void testFgServiceUnthrottled() throws InterruptedException {
        callTest(Constants.TEST_FG_SERVICE_UNTHROTTLED);
    }

    /**
     * Flakey and may not work on OEM devices, so disabled.
     */
    @Suppress
    public void testInlineReply() throws Exception {
        clearNotifications();

        callTest(Constants.TEST_INLINE_REPLY_SHOW);

        performInlineReply();

        callTest(Constants.TEST_INLINE_REPLY_CHECK);
    }

    private void clearNotifications() throws InterruptedException {
        final UiDevice ud = UiDevice.getInstance(getInstrumentation());

        // Open the notification shade.
        ud.openNotification();

        // Press "clear all", if found.
        final UiObject2 clearAll = ud.wait(Until.findObject(By.text("CLEAR ALL")), UI_TIMEOUT);

        // Just skip if not found.
        if (clearAll != null) {
            clearAll.clear();
            ud.wait(Until.gone(By.text("CLEAR ALL")), UI_TIMEOUT);
            Thread.sleep(1000);
        }
        // Close the notification.
        ud.pressHome();
        Thread.sleep(1000);
    }

    private void performInlineReply() throws InterruptedException {
        final UiDevice ud = UiDevice.getInstance(getInstrumentation());

        // Open the notification shade.
        Thread.sleep(1000);
        ud.openNotification();

        // Find the inline reply part.
        ud.wait(Until.findObject(By.text(INLINE_REPLY_REMOTE_INPUT_CAPTION)), UI_TIMEOUT).click();

        Thread.sleep(1000);

        // Type something.
        ud.pressKeyCode(KeyEvent.KEYCODE_A);
        ud.pressKeyCode(KeyEvent.KEYCODE_B);
        ud.pressKeyCode(KeyEvent.KEYCODE_C);
        ud.pressEnter();

        Thread.sleep(1000);
        ud.pressHome();

        ud.wait(Until.gone(By.text(INLINE_REPLY_REMOTE_INPUT_CAPTION)), UI_TIMEOUT);

        Thread.sleep(1000);
    }
}
