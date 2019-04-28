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

package com.android.documentsui.files;

import static org.junit.Assert.assertTrue;

import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.KeyEvent;
import android.view.MotionEvent;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class ActivityInputHandlerTest {

    private ActivityInputHandler mActivityInputHandler;
    private boolean mDeleteHappened;

    @Before
    public void setUp() {
        mDeleteHappened = false;
        mActivityInputHandler = new ActivityInputHandler(() -> {
            mDeleteHappened = true;
        });
    }

    @Test
    public void testDelete() {
        KeyEvent event = new KeyEvent(0, 0, MotionEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL, 0,
                KeyEvent.META_ALT_ON);
        assertTrue(mActivityInputHandler.onKeyDown(event.getKeyCode(), event));
        assertTrue(mDeleteHappened);
    }
}
