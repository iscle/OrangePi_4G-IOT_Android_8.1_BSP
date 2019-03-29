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
package com.android.server.cts;

/**
 * Tests error reporting.
 */
public class ErrorsTest extends ProtoDumpTestCase {
    private static final String TAG = "ErrorsTest";

    private static final String DEVICE_SIDE_TEST_APK = "CtsErrorsApp.apk";
    private static final String DEVICE_SIDE_TEST_PACKAGE
            = "com.android.server.cts.errors";

    @Override
    protected void tearDown() throws Exception {
        getDevice().uninstallPackage(DEVICE_SIDE_TEST_PACKAGE);

        super.tearDown();
    }

    public void testThrowException() throws Exception {
        installPackage(DEVICE_SIDE_TEST_APK, /* grantPermissions= */ true);

        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, ".ErrorsTests",
                "testException");
    }

    public void testANR() throws Exception {
        installPackage(DEVICE_SIDE_TEST_APK, /* grantPermissions= */ true);

        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, ".ErrorsTests",
                "testANR");
    }

    public void testNativeCrash() throws Exception {
        installPackage(DEVICE_SIDE_TEST_APK, /* grantPermissions= */ true);

        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, ".ErrorsTests",
                "testNativeCrash");
    }
}
