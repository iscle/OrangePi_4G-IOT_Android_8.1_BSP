/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.server.telecom.tests;

import org.mockito.MockitoAnnotations;

import android.os.Handler;
import android.telecom.Log;
import android.test.AndroidTestCase;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public abstract class TelecomTestCase extends AndroidTestCase {
    protected static final String TESTING_TAG = "Telecom-TEST";

    MockitoHelper mMockitoHelper = new MockitoHelper();
    ComponentContextFixture mComponentContextFixture;

    @Override
    public void setUp() throws Exception {
        Log.setTag(TESTING_TAG);
        mMockitoHelper.setUp(getContext(), getClass());
        mComponentContextFixture = new ComponentContextFixture();
        Log.setSessionContext(mComponentContextFixture.getTestDouble().getApplicationContext());
        Log.getSessionManager().mCleanStaleSessions = null;
        MockitoAnnotations.initMocks(this);
    }

    @Override
    public void tearDown() throws Exception {
        mComponentContextFixture = null;
        mMockitoHelper.tearDown();
    }

    protected final void waitForHandlerAction(Handler h, long timeoutMillis) {
        final CountDownLatch lock = new CountDownLatch(1);
        h.post(lock::countDown);
        while (lock.getCount() > 0) {
            try {
                lock.await(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                // do nothing
            }
        }
    }
}
