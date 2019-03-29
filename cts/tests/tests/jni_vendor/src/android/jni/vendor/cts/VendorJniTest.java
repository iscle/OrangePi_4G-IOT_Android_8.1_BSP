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

package android.jni.vendor.cts;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import android.os.Process;

public class VendorJniTest extends TestCase {
    private List<String> llndkLibraries;
    private List<String> publicLibraries;
    private List<String> vndkspLibraries;
    private List<String> vndkLibraries;
    private List<String> frameworkOnlyLibraries;

    protected void setUp() throws Exception {
        llndkLibraries = Files.readAllLines(FileSystems.getDefault().getPath(
                "/system/etc/llndk.libraries.txt"));
        publicLibraries = Files.readAllLines(FileSystems.getDefault().getPath(
                "/system/etc/public.libraries.txt"));
        vndkspLibraries = Files.readAllLines(FileSystems.getDefault().getPath(
                "/system/etc/vndksp.libraries.txt"));

        String systemLibDir = Process.is64Bit() ? "/system/lib64" : "/system/lib";

        vndkLibraries = new ArrayList<>();
        for(File f : (new File(systemLibDir + "/vndk")).listFiles()) {
            if (f.isFile()) {
                String name = f.getName();
                if (name.endsWith(".so")) {
                    vndkLibraries.add(name);
                }
            }
        }

        frameworkOnlyLibraries = new ArrayList<>();
        for(File f : (new File(systemLibDir)).listFiles()) {
            if (f.isFile()) {
                String name = f.getName();
                if (name.endsWith(".so") && !llndkLibraries.contains(name)
                        && !vndkspLibraries.contains(name)
                        && !publicLibraries.contains(name)) {
                    frameworkOnlyLibraries.add(name);
                }
            }
        }

        System.loadLibrary("vendorjnitest");
    }

    public static native String dlopen(String name);

    /* test if llndk libraries are all accessible */
    public void test_llndkLibraries() {
        for(String lib : llndkLibraries) {
            assertEquals("", dlopen(lib));
        }
    }

    /* test if vndk-sp libs are accessible */
    public void test_vndkSpLibraries() {
        for(String lib : vndkspLibraries) {
            String error = dlopen(lib);
            if (lib.equals("android.hidl.memory@1.0-impl.so")) {
                // This won't be accessible to vendor JNI. This lib is only accessible from the
                // 'sphal' namespace which isn't linked to the vendor-classloader-namespace.
                if (error.equals("")) {
                    fail(lib + " must not be accessible to vendor apks, but was accessible.");
                }
                continue;
            }
            assertEquals("", error);
        }
    }

    /* test if vndk libs are not accessible */
    public void test_vndkLibraries() {
        for(String lib : vndkLibraries) {
            String error = dlopen(lib);
            if (error.equals("")) {
                fail(lib + " must not be accessible to vendor apks, but was accessible.");
            }
        }
    }

    /* test if framework-only libs are not accessible */
    public void test_frameworkOnlyLibraries() {
        for(String lib : frameworkOnlyLibraries) {
            String error = dlopen(lib);
            if (error.equals("")) {
                fail(lib + " must not be accessible to vendor apks, but was accessible.");
            }
        }
    }
}
