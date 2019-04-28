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

package com.android.documentsui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class DirectoryReloadLockTest {

    private DirectoryReloadLock lock = new DirectoryReloadLock();
    private boolean called;
    private Runnable callback = () -> {
        called = true;
    };

    @Before
    public void setUp() {
        called = false;
    }

    @Test
    public void testNotBlocking_callbackNotBlocked() {
        lock.tryUpdate(callback);
        assertTrue(called);
    }

    @Test
    public void testToggleBlocking_callbackNotBlocked() {
        lock.block();
        lock.unblock();
        lock.tryUpdate(callback);
        assertTrue(called);
    }

    @Test
    public void testBlocking_callbackBlocked() {
        lock.block();
        lock.tryUpdate(callback);
        assertFalse(called);
    }

    @Test
    public void testBlockthenUnblock_callbackNotBlocked() {
        lock.block();
        lock.tryUpdate(callback);
        lock.unblock();
        assertTrue(called);
    }
}
