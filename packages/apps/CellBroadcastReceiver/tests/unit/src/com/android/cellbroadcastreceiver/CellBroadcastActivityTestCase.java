/*
 * Copyright (C) 2016 Google Inc.
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

package com.android.cellbroadcastreceiver;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.os.Bundle;
import android.test.ActivityUnitTestCase;
import android.util.Log;

import java.util.HashMap;

public class CellBroadcastActivityTestCase<T extends Activity> extends ActivityUnitTestCase<T> {

    protected TestContext mContext;

    private T mActivity;

    CellBroadcastActivityTestCase(Class<T> activityClass) {
        super(activityClass);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = new TestContext(getInstrumentation().getTargetContext());
        setActivityContext(mContext);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    protected T startActivity() throws Throwable {
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                mActivity = startActivity(createActivityIntent(), null, null);
            }
        });
        return mActivity;
    }

    protected void stopActivity() throws Exception {
        getInstrumentation().callActivityOnStop(mActivity);
    }

    public static void waitForMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
        }
    }

    protected Intent createActivityIntent() {
        Intent intent = new Intent();
        return intent;
    }

    protected <S> void injectSystemService(Class<S> cls, S service) {
        mContext.injectSystemService(cls, service);
    }

    public static class TestContext extends ContextWrapper {

        private static final String TAG = TestContext.class.getSimpleName();

        private HashMap<String, Object> mInjectedSystemServices = new HashMap<>();

        public TestContext(Context base) {
            super(base);
        }

        public <S> void injectSystemService(Class<S> cls, S service) {
            final String name = getSystemServiceName(cls);
            mInjectedSystemServices.put(name, service);
        }

        @Override
        public Context getApplicationContext() {
            return this;
        }

        @Override
        public Object getSystemService(String name) {
            if (mInjectedSystemServices.containsKey(name)) {
                Log.d(TAG, "return mocked system service for " + name);
                return mInjectedSystemServices.get(name);
            }
            Log.d(TAG, "return real system service for " + name);
            return super.getSystemService(name);
        }
    }
}