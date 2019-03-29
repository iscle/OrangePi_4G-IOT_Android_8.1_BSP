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

package android.os.cts;

import java.io.*;
import java.lang.reflect.*;

import android.platform.test.annotations.Presubmit;
import android.test.AndroidTestCase;

public class CustomClassLoaderTest extends AndroidTestCase {
    File tf;

    @Override
    public void setUp() throws Exception {
        /*
         * dex1.bytes is a jar file with a classes.dex in it.
         * The classes.dex has been javac'ed and dx'ed
         * with the following java file:
         *
         * package android.os.cts;
         *   public class TestClass {
         *     public static final String MESSAGE = "expected_field";
         *   }
         *
         */

        super.setUp();
        // Extract the packaged dex/jar file to a temporary file so we
        // can use it with our classloader.
        tf = File.createTempFile("CustomClassLoaderTest_TestClass", ".dex");
        tf.deleteOnExit();
        InputStream is = mContext.getAssets().open("dex1.bytes");
        assertNotNull(is);
        OutputStream fos = new FileOutputStream(tf);
        byte[] buffer = new byte[8192];
        int len = is.read(buffer);
        while (len != -1) {
            fos.write(buffer, 0, len);
            len = is.read(buffer);
        }
        fos.flush();
        fos.close();
    }

    /* Test a custom class loader based on the DexClassLoader.
     */
    public void testCustomDexClassLoader() throws Exception {
        // Try to load the TestClass class by the CustomDexClassLoader.
        try {
            CustomDexClassLoader cl = new CustomDexClassLoader(
                    tf.getAbsolutePath(),
                    mContext.getCodeCacheDir().getAbsolutePath(),
                    ClassLoader.getSystemClassLoader().getParent());
            // Load the class and get the field 'MESSAGE' and
            // check that it is from the dex1.bytes .jar file.
            Field field = cl.loadClass("android.os.cts.TestClass").getField("MESSAGE");
            assertTrue(((String)field.get(null)).equals("expected_field"));
        } catch (Exception e) {
            throw new RuntimeException("test exception", e);
        }
    }

    /* Test a custom class loader based on the PathClassLoader.
     */
    @Presubmit
    public void testCustomPathClassLoader() throws Exception {
        // Try to load the TestClass class by the CustomPathClassLoader.
        try {
            CustomPathClassLoader cl = new CustomPathClassLoader(
                    tf.getAbsolutePath(),
                    ClassLoader.getSystemClassLoader().getParent());
            // Load the class and get the field 'MESSAGE' and
            // check that it is from the dex1.bytes .jar file.
            Field field = cl.loadClass("android.os.cts.TestClass").getField("MESSAGE");
            assertTrue(((String)field.get(null)).equals("expected_field"));
        } catch (Exception e) {
            throw new RuntimeException("test exception", e);
        }
    }
}
