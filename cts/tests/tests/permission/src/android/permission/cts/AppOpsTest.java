/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.permission.cts;

import android.app.AppOpsManager;
import android.content.Context;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.AttributeSet;
import junit.framework.AssertionFailedError;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class AppOpsTest extends AndroidTestCase {
    static final Class<?>[] sSetModeSignature = new Class[] {
            Context.class, AttributeSet.class};

    private AppOpsManager mAppOps;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mAppOps = (AppOpsManager)getContext().getSystemService(Context.APP_OPS_SERVICE);
        assertNotNull(mAppOps);
    }

    /**
     * Test that the app can not change the app op mode for itself.
     */
    @SmallTest
    public void testSetMode() {
        boolean gotToTest = false;
        try {
            Method setMode = mAppOps.getClass().getMethod("setMode", int.class, int.class,
                    String.class, int.class);
            int writeSmsOp = mAppOps.getClass().getField("OP_WRITE_SMS").getInt(mAppOps);
            gotToTest = true;
            setMode.invoke(mAppOps, writeSmsOp, android.os.Process.myUid(),
                    getContext().getPackageName(), AppOpsManager.MODE_ALLOWED);
            fail("Was able to set mode for self");
        } catch (NoSuchFieldException e) {
            throw new AssertionError("Unable to find OP_WRITE_SMS", e);
        } catch (NoSuchMethodException e) {
            throw new AssertionError("Unable to find setMode method", e);
        } catch (InvocationTargetException e) {
            if (!gotToTest) {
                throw new AssertionError("Whoops", e);
            }
            // If we got to the test, we want it to have thrown a security exception.
            // We need to look inside of the wrapper exception to see.
            Throwable t = e.getCause();
            if (!(t instanceof SecurityException)) {
                throw new AssertionError("Did not throw SecurityException", e);
            }
        } catch (IllegalAccessException e) {
            throw new AssertionError("Whoops", e);
        }
    }
}
