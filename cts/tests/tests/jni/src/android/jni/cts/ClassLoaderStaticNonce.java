/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.util.Log;

/**
 * Class to help with class-loader check..
 */
public class ClassLoaderStaticNonce {

    static Object ctx;

    // Have a static initializer block.
    static {
        ctx = new Object();
        log("Initializing ClassLoaderStaticNonce");
    }

    private final static A a = new A();

    public static void log(String s) {
        Log.i("ClassLoaderStaticNone", s);
    }

    public static class A {
        // Have a finalizer. This will make the outer class not finalizable when we allocate for
        // a static field.
        public void finalize() throws Throwable {
            super.finalize();

            // Do something so that the finalizer can't be recognized as empty.
            toNull = null;
        }

        private Object toNull = new Object();
    }


    public static void setCtx(Object in) {
        ctx = in;
    }

    public static Object getCtx() {
        return ctx;
    }
}
