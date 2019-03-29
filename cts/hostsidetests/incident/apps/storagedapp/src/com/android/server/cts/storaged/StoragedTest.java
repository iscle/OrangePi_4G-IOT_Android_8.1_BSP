/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.server.cts.storaged;

import android.content.Context;
import android.content.Intent;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Used by StoragedTest.
 */
@RunWith(AndroidJUnit4.class)
public class StoragedTest {
    private static final String TAG = "StoragedTest";

    protected Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
    }

    @Test
    public void testForegroundIO() throws Exception {
        Intent intent = new Intent();
        intent.setClass(mContext, SimpleIOActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                        Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
        Thread.sleep(3000);
    }

    @Test
    public void testBackgroundIO() throws Exception {
        Intent intent = new Intent();
        intent.setClass(mContext, SimpleIOService.class);
        mContext.startService(intent);
        Thread.sleep(3000);
    }
}
