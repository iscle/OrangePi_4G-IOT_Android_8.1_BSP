/*
 * Copyright (C) 2008 The Android Open Source Project
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
package android.view.cts;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.SurfaceHolder.BadSurfaceTypeException;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class SurfaceHolder_BadSurfaceTypeExceptionTest {
    @Test
    public void testBadSurfaceTypeException(){
        BadSurfaceTypeException ne = null;
        boolean isThrown = false;

        try {
            ne = new BadSurfaceTypeException();
            throw ne;
        } catch (BadSurfaceTypeException e) {
            assertSame(ne, e);
            isThrown = true;
        } finally {
            if (!isThrown) {
                fail("should throw out InflateException");
            }
        }

        String name = "SurfaceHolder_BadSurfaceTypeExceptionTest";
        isThrown = false;

        try {
            ne = new BadSurfaceTypeException(name);
            throw ne;
        } catch (BadSurfaceTypeException e) {
            assertSame(ne, e);
            isThrown = true;
        } finally {
            if (!isThrown) {
                fail("should throw out InflateException");
            }
        }
    }
}
