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

package android.jni.cts;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.support.test.InstrumentationRegistry;
import dalvik.system.PathClassLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class LinkerNamespacesHelper {
    private final static String VENDOR_CONFIG_FILE = "/vendor/etc/public.libraries.txt";
    private final static String[] PUBLIC_SYSTEM_LIBRARIES = {
        "libaaudio.so",
        "libandroid.so",
        "libc.so",
        "libcamera2ndk.so",
        "libdl.so",
        "libEGL.so",
        "libGLESv1_CM.so",
        "libGLESv2.so",
        "libGLESv3.so",
        "libicui18n.so",
        "libicuuc.so",
        "libjnigraphics.so",
        "liblog.so",
        "libmediandk.so",
        "libm.so",
        "libnativewindow.so",
        "libneuralnetworks.so",
        "libOpenMAXAL.so",
        "libOpenSLES.so",
        "libRS.so",
        "libstdc++.so",
        "libsync.so",
        "libvulkan.so",
        "libz.so"
    };

    private final static String WEBVIEW_PLAT_SUPPORT_LIB = "libwebviewchromium_plat_support.so";

    public static String runAccessibilityTest() throws IOException {
        List<String> vendorLibs = new ArrayList<>();
        File file = new File(VENDOR_CONFIG_FILE);
        if (file.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    vendorLibs.add(line);
                }
            }
        }

        List<String> systemLibs = new ArrayList<>();
        Collections.addAll(systemLibs, PUBLIC_SYSTEM_LIBRARIES);

        if (InstrumentationRegistry.getContext().getPackageManager().
                hasSystemFeature(PackageManager.FEATURE_WEBVIEW)) {
            systemLibs.add(WEBVIEW_PLAT_SUPPORT_LIB);
        }

        return runAccessibilityTestImpl(systemLibs.toArray(new String[systemLibs.size()]),
                                        vendorLibs.toArray(new String[vendorLibs.size()]));
    }

    private static native String runAccessibilityTestImpl(String[] publicSystemLibs,
                                                          String[] publicVendorLibs);

    private static void invokeIncrementGlobal(Class<?> clazz) throws Exception {
        clazz.getMethod("incrementGlobal").invoke(null);
    }
    private static int invokeGetGlobal(Class<?> clazz) throws Exception  {
        return (Integer)clazz.getMethod("getGlobal").invoke(null);
    }

    private static ApplicationInfo getApplicationInfo(String packageName) {
        PackageManager pm = InstrumentationRegistry.getContext().getPackageManager();
        try {
            return pm.getApplicationInfo(packageName, 0);
        } catch (NameNotFoundException nnfe) {
            throw new RuntimeException(nnfe);
        }
    }

    private static String getSourcePath(String packageName) {
        String sourcePath = getApplicationInfo(packageName).sourceDir;
        if (sourcePath == null) {
            throw new IllegalStateException("No source path path found for " + packageName);
        }
        return sourcePath;
    }

    private static String getNativePath(String packageName) {
        String nativePath = getApplicationInfo(packageName).nativeLibraryDir;
        if (nativePath == null) {
            throw new IllegalStateException("No native path path found for " + packageName);
        }
        return nativePath;
    }

    // Verify the behaviour of native library loading in class loaders.
    // In this test:
    //    - libjninamespacea1, libjninamespacea2 and libjninamespaceb depend on libjnicommon
    //    - loaderA will load ClassNamespaceA1 (loading libjninamespacea1)
    //    - loaderA will load ClassNamespaceA2 (loading libjninamespacea2)
    //    - loaderB will load ClassNamespaceB (loading libjninamespaceb)
    //    - incrementGlobal/getGlobal operate on a static global from libjnicommon
    //      and each class should get its own view on it.
    //
    // This is a test case for 2 different scenarios:
    //    - loading native libraries in different class loaders
    //    - loading native libraries in the same class loader
    // Ideally we would have 2 different tests but JNI doesn't allow loading the same library in
    // different class loaders. So to keep the number of native libraries manageable we just
    // re-use the same class loaders for the two tests.
    public static String runClassLoaderNamespaces() throws Exception {
        // Test for different class loaders.
        // Verify that common dependencies get a separate copy in each class loader.
        // libjnicommon should be loaded twice:
        // in the namespace for loaderA and the one for loaderB.
        String apkPath = getSourcePath("android.jni.cts");
        String nativePath = getNativePath("android.jni.cts");
        PathClassLoader loaderA = new PathClassLoader(
                apkPath, nativePath, ClassLoader.getSystemClassLoader());
        Class<?> testA1Class = loaderA.loadClass("android.jni.cts.ClassNamespaceA1");
        PathClassLoader loaderB = new PathClassLoader(
                apkPath, nativePath, ClassLoader.getSystemClassLoader());
        Class<?> testBClass = loaderB.loadClass("android.jni.cts.ClassNamespaceB");

        int globalA1 = invokeGetGlobal(testA1Class);
        int globalB = invokeGetGlobal(testBClass);
        if (globalA1 != 0 || globalB != 0) {
            return "Expected globals to be 0/0: globalA1=" + globalA1 + " globalB=" + globalB;
        }

        invokeIncrementGlobal(testA1Class);
        globalA1 = invokeGetGlobal(testA1Class);
        globalB = invokeGetGlobal(testBClass);
        if (globalA1 != 1 || globalB != 0) {
            return "Expected globals to be 1/0: globalA1=" + globalA1 + " globalB=" + globalB;
        }

        invokeIncrementGlobal(testBClass);
        globalA1 = invokeGetGlobal(testA1Class);
        globalB = invokeGetGlobal(testBClass);
        if (globalA1 != 1 || globalB != 1) {
            return "Expected globals to be 1/1: globalA1=" + globalA1 + " globalB=" + globalB;
        }

        // Test for the same class loaders.
        // Verify that if we load ClassNamespaceA2 into loaderA we get the same view on the
        // globals.
        Class<?> testA2Class = loaderA.loadClass("android.jni.cts.ClassNamespaceA2");

        int globalA2 = invokeGetGlobal(testA2Class);
        if (globalA1 != 1 || globalA2 !=1) {
            return "Expected globals to be 1/1: globalA1=" + globalA1 + " globalA2=" + globalA2;
        }

        invokeIncrementGlobal(testA1Class);
        globalA1 = invokeGetGlobal(testA1Class);
        globalA2 = invokeGetGlobal(testA2Class);
        if (globalA1 != 2 || globalA2 != 2) {
            return "Expected globals to be 2/2: globalA1=" + globalA1 + " globalA2=" + globalA2;
        }

        invokeIncrementGlobal(testA2Class);
        globalA1 = invokeGetGlobal(testA1Class);
        globalA2 = invokeGetGlobal(testA2Class);
        if (globalA1 != 3 || globalA2 != 3) {
            return "Expected globals to be 2/2: globalA1=" + globalA1 + " globalA2=" + globalA2;
        }
        // On success we return null.
        return null;
    }
}

class ClassNamespaceA1 {
    static {
        System.loadLibrary("jninamespacea1");
    }

    public static native void incrementGlobal();
    public static native int getGlobal();
}

class ClassNamespaceA2 {
    static {
        System.loadLibrary("jninamespacea2");
    }

    public static native void incrementGlobal();
    public static native int getGlobal();
}

class ClassNamespaceB {
    static {
        System.loadLibrary("jninamespaceb");
    }

    public static native void incrementGlobal();
    public static native int getGlobal();
}
