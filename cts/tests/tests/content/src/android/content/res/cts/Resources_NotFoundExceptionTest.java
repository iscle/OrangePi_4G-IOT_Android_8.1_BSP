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

package android.content.res.cts;

import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import junit.framework.TestCase;

public class Resources_NotFoundExceptionTest extends TestCase {

    public void testNotFoundException() {
        NotFoundException ne;
        boolean wasThrown;

        wasThrown = false;
        ne = new NotFoundException();

        try {
            throw ne;
        } catch (NotFoundException e) {
            // expected
            assertSame(ne, e);
            wasThrown = true;
        } finally {
            if (!wasThrown) {
                fail("should throw out NotFoundException");
            }
        }

        final String MESSAGE = "test";
        wasThrown = false;
        ne = new NotFoundException(MESSAGE);

        try {
            throw ne;
        } catch (NotFoundException e) {
            // expected
            assertSame(ne, e);
            assertEquals(MESSAGE, e.getMessage());
            wasThrown = true;
        } finally {
            if (!wasThrown) {
                fail("should throw out NotFoundException");
            }
        }

        final Exception CAUSE = new NullPointerException();
        wasThrown = false;
        ne = new NotFoundException(MESSAGE, CAUSE);

        try {
            throw ne;
        } catch (NotFoundException e) {
            // expected
            assertSame(ne, e);
            assertEquals(MESSAGE, e.getMessage());
            assertEquals(CAUSE, e.getCause());
            wasThrown = true;
        } finally {
            if (!wasThrown) {
                fail("should throw out NotFoundException");
            }
        }
    }
}
