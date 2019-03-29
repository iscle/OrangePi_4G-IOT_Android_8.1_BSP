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
 * limitations under the License
 */

package android.inputmethodservice.cts.devicetest;

import static android.inputmethodservice.cts.DeviceEvent.isFrom;
import static android.inputmethodservice.cts.DeviceEvent.isType;
import static android.inputmethodservice.cts.common.DeviceEventConstants.DeviceEventType.TEST_START;

import android.app.Instrumentation;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.inputmethodservice.cts.DeviceEvent;
import android.inputmethodservice.cts.common.DeviceEventConstants.DeviceEventType;
import android.inputmethodservice.cts.common.EventProviderConstants.EventTableConstants;
import android.inputmethodservice.cts.common.test.TestInfo;
import android.net.Uri;
import android.support.annotation.IdRes;
import android.support.test.InstrumentationRegistry;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Helper object for device side test.
 */
final class TestHelper {

    /** Content URI of device event provider. */
    private static final Uri DEVICE_EVENTS_CONTENT_URI = Uri.parse(EventTableConstants.CONTENT_URI);
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);

    private final TestInfo mTestInfo;
    private final ContentResolver mResolver;
    private final Context mTargetContext;
    private final UiDevice mUiDevice;
    private final Instrumentation mInstrumentation;

    /**
     * Construct a helper object of specified test method.
     *
     * @param testClass a {@link Class} of test.
     * @param testMethod a name of test method.
     */
    TestHelper(final Class<?> testClass, final String testMethod) {
        final Context testContext = InstrumentationRegistry.getContext();
        mTestInfo = new TestInfo(testContext.getPackageName(), testClass.getName(), testMethod);
        mResolver = testContext.getContentResolver();
        mTargetContext = InstrumentationRegistry.getTargetContext();
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mUiDevice = UiDevice.getInstance(mInstrumentation);
    }

    /**
     * Execute a shell {@code command} and return its standard out.
     * @param command a shell command text to execute.
     * @return command's standard output without ending newline.
     * @throws IOException
     */
    String shell(final String command) throws IOException {
        return mUiDevice.executeShellCommand(command).trim();
    }

    /**
     * Launching an Activity for test, and wait for completions of launch.
     * @param packageName activity's app package name.
     * @param className activity's class name.
     */
    void launchActivity(final String packageName, final String className) {
        final Intent intent = new Intent()
                .setAction(Intent.ACTION_MAIN)
                .setClassName(packageName, className)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        InstrumentationRegistry.getContext().startActivity(intent);
        mUiDevice.wait(Until.hasObject(By.pkg(packageName).depth(0)), TIMEOUT);
    }

    /**
     * Find an UI element from resource ID.
     * @param resId id of finding UI element.
     * @return {@link UiObject2} of found UI element.
     */
    UiObject2 findUiObject(@IdRes int resId) {
        final String resourceName = mTargetContext.getResources().getResourceName(resId);
        return mUiDevice.findObject(By.res(resourceName));
    }

    /**
     * Launch test activity synchronously.
     *
     * @param packageName activity's app package name.
     * @param className   activity's class name.
     * @return instance of Activity
     */
    Activity launchActivitySync(final String packageName, final String className) {
        final Intent intent = new Intent()
                .setAction(Intent.ACTION_MAIN)
                .setClassName(packageName, className)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return mInstrumentation.startActivitySync(intent);
    }

    /**
     * Return all device events as {@link Stream}
     * @return {@link Stream<DeviceEvent>} of all device events.
     */
    Stream<DeviceEvent> queryAllEvents() {
        try (final Cursor cursor = mResolver.query(
                DEVICE_EVENTS_CONTENT_URI,
                null /* projection */,
                null /* selection */,
                null /* selectionArgs */,
                null /* sortOrder */)) {
            return DeviceEvent.buildStream(cursor);
        }
    }

    /**
     * Build a {@link Predicate} can be used for skipping device events in {@link Stream} until
     * {@link DeviceEventType#TEST_START TEST_START} device event of this test method.
     * @return {@llink Predicate<DeviceEvent>} that return true after accepting
     *         {@link DeviceEventType#TEST_START TEST_START} of this test method.
     */
    Predicate<DeviceEvent> isStartOfTest() {
        return isFrom(mTestInfo.getTestName()).and(isType(TEST_START));
    }
}
