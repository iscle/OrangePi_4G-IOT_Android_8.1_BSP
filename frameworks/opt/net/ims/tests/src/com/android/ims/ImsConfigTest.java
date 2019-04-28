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
 * limitations under the License
 */

package com.android.ims;

import android.support.test.runner.AndroidJUnit4;

import com.android.ims.internal.IImsConfig;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Unit Tests for ImsConfig
 */
@RunWith(AndroidJUnit4.class)
public class ImsConfigTest extends ImsTestBase {

    @Mock IImsConfig mMockImsConfigInterface;

    ImsConfig mTestImsConfig;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        mTestImsConfig = new ImsConfig(mMockImsConfigInterface, mContext);
    }

    @After
    @Override
    public void tearDown() throws Exception {
        mTestImsConfig = null;
        super.tearDown();
    }

    @Test
    public void testImsConfigGetProvisionedValue() throws Exception {
        int testItem = 0;

        mTestImsConfig.getProvisionedValue(testItem);

        verify(mMockImsConfigInterface).getProvisionedValue(eq(testItem));
    }
}
