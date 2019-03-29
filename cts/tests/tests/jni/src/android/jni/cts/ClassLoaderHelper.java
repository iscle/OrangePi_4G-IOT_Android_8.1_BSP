/*
 * Copyright (C) 2011 The Android Open Source Project
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

class ClassLoaderHelper {

    // Note: To not hava a dependency on other classes, assume that if we do initialize then
    // it's OK to load the library.
    static {
        System.loadLibrary("jnitest");
    }

    public static boolean run() {
        ClassLoaderHelper clh = new ClassLoaderHelper();

        int firstHashCode = clh.getHashCodeDirect();

        Runtime.getRuntime().gc();

        int secondHashCode = clh.getHashCodeNative();

        return (firstHashCode == secondHashCode);
    }

    // Simple helpers to avoid keeping references alive because of dex registers.
    private int getHashCodeDirect() {
        return ClassLoaderStaticNonce.class.hashCode();
    }
    private int getHashCodeNative() {
        ClassLoader loader = getClass().getClassLoader();
        return nativeGetHashCode(loader, loader.getClass());
    }

    private native int nativeGetHashCode(ClassLoader loader, Class<?> classLoaderClass);
}