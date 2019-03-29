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

package com.android.cts.core.runner;

import android.support.test.internal.runner.ClassPathScanner;
import android.support.test.internal.runner.ClassPathScanner.ClassNameFilter;
import android.util.Log;

import com.android.cts.core.internal.runner.TestLoader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

import dalvik.system.DexFile;

/**
 * Find tests in the current APK.
 */
public class TestClassFinder {

    private static final String TAG = "TestClassFinder";
    private static final boolean DEBUG = false;

    // Excluded test packages
    private static final String[] DEFAULT_EXCLUDED_PACKAGES = {
            "junit",
            "org.junit",
            "org.hamcrest",
            "org.mockito",// exclude Mockito for performance and to prevent JVM related errors
            "android.support.test.internal.runner.junit3",// always skip AndroidTestSuite
    };

    static Collection<Class<?>> getClasses(List<String> apks, ClassLoader loader) {
        if (DEBUG) {
          Log.d(TAG, "getClasses: =======================================");

          for (String apkPath : apks) {
            Log.d(TAG, "getClasses: -------------------------------");
            Log.d(TAG, "getClasses: APK " + apkPath);

            DexFile dexFile = null;
            try {
                dexFile = new DexFile(apkPath);
                Enumeration<String> apkClassNames = dexFile.entries();
                while (apkClassNames.hasMoreElements()) {
                    String apkClassName = apkClassNames.nextElement();
                    Log.d(TAG, "getClasses: DexClass element " + apkClassName);
                }
            } catch (IOException e) {
              throw new AssertionError(e);
            } finally {
                if (dexFile != null) {
                  try {
                    dexFile.close();
                  } catch (IOException e) {
                    throw new AssertionError(e);
                  }
                }
            }
            Log.d(TAG, "getClasses: -------------------------------");
          }
        }  // if DEBUG

        List<Class<?>> classes = new ArrayList<>();
        ClassPathScanner scanner = new ClassPathScanner(apks);

        ClassPathScanner.ChainedClassNameFilter filter =
                new ClassPathScanner.ChainedClassNameFilter();
        // exclude inner classes
        filter.add(new ClassPathScanner.ExternalClassNameFilter());

        // exclude default classes
        for (String defaultExcludedPackage : DEFAULT_EXCLUDED_PACKAGES) {
            filter.add(new ExcludePackageNameFilter(defaultExcludedPackage));
        }

        // exclude any classes that aren't a "test class" (see #loadIfTest)
        TestLoader testLoader = new TestLoader();
        testLoader.setClassLoader(loader);

        try {
            Set<String> classNames = scanner.getClassPathEntries(filter);
            for (String className : classNames) {
                // Important: This further acts as an additional filter;
                // classes that aren't a "test class" are never loaded.
                Class<?> cls = testLoader.loadIfTest(className);
                if (cls != null) {
                    classes.add(cls);

                    if (DEBUG) {
                      Log.d(TAG, "getClasses: Loaded " + className);
                    }
                } else if (DEBUG) {
                  Log.d(TAG, "getClasses: Failed to load class " + className);
                }
            }
            return classes;
        } catch (IOException e) {
            Log.e(CoreTestRunner.TAG, "Failed to scan classes", e);
        }


        if (DEBUG) {
            Log.d(TAG, "getClasses: =======================================");
        }

        return testLoader.getLoadedClasses();
    }

    /**
     * A {@link ClassNameFilter} that only rejects a given package names within the given namespace.
     */
    public static class ExcludePackageNameFilter implements ClassNameFilter {

        private final String mPkgName;

        ExcludePackageNameFilter(String pkgName) {
            if (!pkgName.endsWith(".")) {
                mPkgName = String.format("%s.", pkgName);
            } else {
                mPkgName = pkgName;
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean accept(String pathName) {
            return !pathName.startsWith(mPkgName);
        }
    }
}
