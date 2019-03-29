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

package com.android.cts.usepermission;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

public class BasePermissionActivity extends Activity {
    private static final long OPERATION_TIMEOUT_MILLIS = 5000;

    private final SynchronousQueue<Result> mResult = new SynchronousQueue<>();
    private final CountDownLatch mOnCreateSync = new CountDownLatch(1);

    public static class Result {
        public final int requestCode;
        public final String[] permissions;
        public final int[] grantResults;

        public Result(int requestCode, String[] permissions, int[] grantResults) {
            this.requestCode = requestCode;
            this.permissions = permissions;
            this.grantResults = grantResults;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

        mOnCreateSync.countDown();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        try {
            mResult.offer(new Result(requestCode, permissions, grantResults), 5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void waitForOnCreate() {
        try {
            mOnCreateSync.await(OPERATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public Result getResult() {
        try {
            return mResult.poll(OPERATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
