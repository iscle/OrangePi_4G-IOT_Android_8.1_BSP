/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.documentsui.base;


import static org.junit.Assert.assertArrayEquals;

import android.content.Intent;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class StateTest {

    private static final String[] MIME_TYPES = { "image/gif", "image/jpg" };

    private Intent mIntent;
    private State mState;

    @Before
    public void setUp() {
        mIntent = new Intent();
        mState = new State();
    }

    @Test
    public void testAcceptGivenMimeTypesInExtra() {
        mIntent.putExtra(Intent.EXTRA_MIME_TYPES, MIME_TYPES);

        mState.initAcceptMimes(mIntent, "*/*");

        assertArrayEquals(MIME_TYPES, mState.acceptMimes);
    }

    @Test
    public void testAcceptIntentTypeWithoutExtra() {
        mState.initAcceptMimes(mIntent, MIME_TYPES[0]);

        assertArrayEquals(new String[] { MIME_TYPES[0] }, mState.acceptMimes);
    }
}
