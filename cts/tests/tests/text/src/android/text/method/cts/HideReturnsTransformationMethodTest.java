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

package android.text.method.cts;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.method.HideReturnsTransformationMethod;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link HideReturnsTransformationMethod}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class HideReturnsTransformationMethodTest {
    @Test
    public void testConstructor() {
        new HideReturnsTransformationMethod();
    }

    @Test
    public void testGetOriginal() {
        MyHideReturnsTranformationMethod method = new MyHideReturnsTranformationMethod();
        assertArrayEquals(new char[] { '\r' }, method.getOriginal());
    }

    @Test
    public void testGetInstance() {
        HideReturnsTransformationMethod method0 = HideReturnsTransformationMethod.getInstance();
        assertNotNull(method0);

        HideReturnsTransformationMethod method1 = HideReturnsTransformationMethod.getInstance();
        assertSame(method0, method1);
    }

    @Test
    public void testGetReplacement() {
        MyHideReturnsTranformationMethod method = new MyHideReturnsTranformationMethod();
        assertArrayEquals(new char[] { '\uFEFF' }, method.getReplacement());
    }

    private static class MyHideReturnsTranformationMethod extends HideReturnsTransformationMethod {
        @Override
        protected char[] getOriginal() {
            return super.getOriginal();
        }

        @Override
        protected char[] getReplacement() {
            return super.getReplacement();
        }
    }
}
