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
import android.view.InflateException;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class InflateExceptionTest {
    @Test
    public void testInflateException() {
        InflateException ne = null;
        boolean isThrown = false;

        try {
            ne = new InflateException();
            throw ne;
        } catch (InflateException e) {
            assertSame(ne, e);
            isThrown = true;
        } finally {
            if (!isThrown) {
                fail("should throw out InflateException");
            }
        }

        String detailMessage = "testInflateException";
        Throwable throwable = new Exception();

        isThrown = false;

        try {
            ne = new InflateException(detailMessage, throwable);
            throw ne;
        } catch (InflateException e) {
            assertSame(ne, e);
            isThrown = true;
        } finally {
            if (!isThrown) {
                fail("should throw out InflateException");
            }
        }

        isThrown = false;

        try {
            ne = new InflateException(detailMessage);
            throw ne;
        } catch (InflateException e) {
            assertSame(ne, e);
            isThrown = true;
        } finally {
            if (!isThrown) {
                fail("should throw out InflateException");
            }
        }

        isThrown = false;

        try {
            ne = new InflateException(throwable);
            throw ne;
        } catch (InflateException e) {
            assertSame(ne, e);
            isThrown = true;
        } finally {
            if (!isThrown) {
                fail("should throw out InflateException");
            }
        }
    }
}
