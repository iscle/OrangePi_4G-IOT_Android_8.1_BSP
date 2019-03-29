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

package android.text.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.Editable;
import android.text.Editable.Factory;
import android.text.SpannableStringBuilder;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class Editable_FactoryTest {
    private Factory mFactory;

    @Test
    public void testNewEditable() {
        CharSequence source = "abc";
        // set the expected value
        Editable expected = new SpannableStringBuilder(source);

        mFactory = new Editable.Factory();
        Editable actual = mFactory.newEditable(source);
        assertEquals(expected.toString(), actual.toString());

    }

    @Test
    public void testGetInstance() {
        mFactory = Factory.getInstance();
        assertTrue(mFactory instanceof Editable.Factory);
    }

}

