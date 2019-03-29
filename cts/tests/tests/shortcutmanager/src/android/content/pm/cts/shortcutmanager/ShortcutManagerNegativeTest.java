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

import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.assertWith;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.concatResult;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.list;

import android.content.pm.ShortcutManager;
import android.test.MoreAsserts;
import android.test.suitebuilder.annotation.SmallTest;
import android.test.suitebuilder.annotation.Suppress;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

@SmallTest
public class ShortcutManagerNegativeTest extends ShortcutManagerCtsTestsBase {
    private static final String TAG = "ShortcutNegativeCTS";

    /**
     * If true, reflection errors such as "field not found" will be a failure.  This is useful
     * during development, but should be turned off in the actual code, since these fields/methods
     * don't have to exist.
     */
    private static final boolean DISALLOW_REFLECTION_ERROR = false; // don't submit with true

    private static Object readField(Object instance, String field)
            throws NoSuchFieldException, IllegalAccessException {
        final Field f = instance.getClass().getDeclaredField(field);
        f.setAccessible(true);
        final Object ret = f.get(instance);
        if (ret == null) {
            throw new NoSuchFieldError();
        }
        return ret;
    }

    private static void callMethodExpectingSecurityException(Object instance, String name,
            String expectedMessage, Object... args)
            throws NoSuchMethodException, IllegalAccessException {

        Method m = null;
        for (Method method : instance.getClass().getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                m = method;
                break;
            }
        }
        if (m == null) {
            throw new NoSuchMethodError();
        }

        m.setAccessible(true);
        try {
            m.invoke(instance, args);
        } catch (InvocationTargetException e) {
            if (e.getTargetException() instanceof SecurityException) {
                MoreAsserts.assertContainsRegex(expectedMessage,
                        e.getTargetException().getMessage());
                return; // Pass
            }
        }
        fail("Didn't throw exception");
    }

    private void checkAidlCall(String method, String expectedMessage, Object... args)
            throws IllegalAccessException {
        final ShortcutManager manager = getTestContext().getSystemService(ShortcutManager.class);

        try {
            callMethodExpectingSecurityException(readField(manager, "mService"), method,
                    expectedMessage, args);
        } catch (NoSuchFieldException|NoSuchMethodException e) {
            if (DISALLOW_REFLECTION_ERROR) {
                throw new RuntimeException(e);
            } else {
                Log.w(TAG, "Reflection failed, which is okay", e);
            }
        }
    }

    /**
     * Make sure the internal AIDL methods are protected.
     */
    public void testDirectAidlCalls() throws IllegalAccessException {
        checkAidlCall("resetThrottling", "Caller must be");

        checkAidlCall("onApplicationActive", "does not have",
                "package", getTestContext().getUserId());

        checkAidlCall("getBackupPayload", "Caller must be", getTestContext().getUserId());

        checkAidlCall("applyRestore", "Caller must be", null, getTestContext().getUserId());
    }

    private String runCommand(String command) throws IOException, InterruptedException {
        final Process p = Runtime.getRuntime().exec(command);

        final ArrayList<String> ret = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) {
                ret.add(line);
            }
        };
        p.waitFor();
        return concatResult(ret);
    }

    /**
     * Make sure cmd shortcut can't be called.
     */
    @Suppress // calling "cmd shortcut" from this UID seems to hang now.
    public void testCommand() throws Exception {
        runWithCaller(mPackageContext1, () -> {
            assertTrue(getManager().setDynamicShortcuts(list(
                    makeShortcut("s1"))));
        });

        // cmd shortcut will fail silently, with no error outputs.
        MoreAsserts.assertNotContainsRegex("Success", runCommand("cmd shortcut clear-shortcuts " +
                mPackageContext1.getPackageName()));

        // Shortcuts shouldn't be cleared.
        runWithCaller(mPackageContext1, () -> {
            assertWith(getManager().getDynamicShortcuts())
                    .haveIds("s1");
        });
    }

    /**
     * Make sure AIDL methods can't be called for other users.
     */
    public void testUserIdSpoofing() throws IllegalAccessException {
        checkAidlCall("getDynamicShortcuts", "Invalid user-ID",
                mPackageContext1.getPackageName(), /* user-id*/ 10);
    }
}
