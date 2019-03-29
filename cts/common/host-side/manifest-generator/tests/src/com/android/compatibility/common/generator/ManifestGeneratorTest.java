/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.compatibility.common.generator;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

/** Unit tests for {@link ManifestGenerator}. */
public class ManifestGeneratorTest extends TestCase {

    private static final String PACKAGE = "test.package";
    private static final String INSTRUMENT = "test.package.TestInstrument";
    private static final String MIN_SDK = "8";
    private static final String TARGET_SDK = "9";
    private static final String MANIFEST = "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>\r\n"
        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" "
        + "package=\"test.package\">\r\n"
        + "  <uses-sdk android:minSdkVersion=\"8\" android:targetSdkVersion=\"9\" />\r\n"
        + "%s"
        + "  <application>\r\n"
        + "%s"
        + "  </application>\r\n"
        + "  <instrumentation android:name=\"test.package.TestInstrument\" "
        + "android:targetPackage=\"test.package\" />\r\n"
        + "</manifest>";
    private static final String PERMISSION = "  <uses-permission android:name=\"%s\" />\r\n";
    private static final String PERMISSION_A = "android.permission.PermissionA";
    private static final String PERMISSION_B = "android.permission.PermissionB";
    private static final String ACTIVITY = "    <activity android:name=\"%s\" />\r\n";
    private static final String ACTIVITY_A = "test.package.ActivityA";
    private static final String ACTIVITY_B = "test.package.ActivityB";

    public void testManifest() throws Exception {
        List<String> permissions = new ArrayList<>();
        permissions.add(PERMISSION_A);
        permissions.add(PERMISSION_B);
        List<String> activities = new ArrayList<>();
        activities.add(ACTIVITY_A);
        activities.add(ACTIVITY_B);
        OutputStream output = new OutputStream() {
            private StringBuilder string = new StringBuilder();
            @Override
            public void write(int b) throws IOException {
                this.string.append((char) b);
            }

            @Override
            public String toString(){
                return this.string.toString();
            }
        };
        ManifestGenerator.generate(output, PACKAGE, INSTRUMENT, MIN_SDK, TARGET_SDK,
            permissions, activities);
        String permissionXml = String.format(PERMISSION, PERMISSION_A)
                + String.format(PERMISSION, PERMISSION_B);
        String activityXml = String.format(ACTIVITY, ACTIVITY_A)
                + String.format(ACTIVITY, ACTIVITY_B);
        String expected = String.format(MANIFEST, permissionXml, activityXml);
        assertEquals("Wrong manifest output", expected, output.toString());
    }

}
