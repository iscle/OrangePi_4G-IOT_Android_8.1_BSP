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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.WindowManager.BadTokenException;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class WindowManager_BadTokenExceptionTest {
    @Test
    public void testBadTokenException(){
        BadTokenException badTokenException = new BadTokenException();
        try {
            throw badTokenException;
        } catch (BadTokenException e) {
            // expected
            assertNull(e.getMessage());
        }

        String message = "WindowManager_BadTokenExceptionTest";
        badTokenException = new BadTokenException(message);

        try {
            throw badTokenException;
        } catch (BadTokenException e) {
            // expected
            assertEquals(message, e.getMessage());
        }
    }
}
