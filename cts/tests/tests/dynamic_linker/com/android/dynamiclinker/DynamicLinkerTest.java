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

package com.android.dynamiclinker;

import junit.framework.TestCase;
import android.support.test.InstrumentationRegistry;

public class DynamicLinkerTest extends TestCase {

  private native int functionA();
  private native int functionB();

  public void testLoadLibInApkByLibName() {
    System.loadLibrary("dynamiclinker_native_lib_a");
    assertEquals(1, functionA());
  }

  public void testLoadLibInApkByFileName() {
    String arch = System.getProperty("os.arch");
    String apkPath = InstrumentationRegistry.getContext().getPackageResourcePath();
    if (arch.equals("aarch64")) {
      System.load(apkPath + "!/lib/arm64-v8a/libdynamiclinker_native_lib_b.so");
    } else if (arch.startsWith("arm")) {
      System.load(apkPath + "!/lib/armeabi-v7a/libdynamiclinker_native_lib_b.so");
    } else if (arch.equals("x86_64")) {
      System.load(apkPath + "!/lib/x86_64/libdynamiclinker_native_lib_b.so");
    } else if (arch.endsWith("86")) {
      System.load(apkPath + "!/lib/x86/libdynamiclinker_native_lib_b.so");
    } else {
      // Don't know which lib to load on this arch.
      return;
    }
    assertEquals(1, functionB());
  }

}
