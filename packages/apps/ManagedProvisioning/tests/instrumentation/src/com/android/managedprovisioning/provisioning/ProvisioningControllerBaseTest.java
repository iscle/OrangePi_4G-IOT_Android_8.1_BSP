/*
 * Copyright 2016, The Android Open Source Project
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

package com.android.managedprovisioning.provisioning;

import static com.android.managedprovisioning.provisioning.AbstractProvisioningController.MSG_RUN_TASK;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.test.AndroidTestCase;

import com.android.managedprovisioning.task.AbstractProvisioningTask;

import org.mockito.MockitoAnnotations;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Base class for provisioning controller tests.
 */
public abstract class ProvisioningControllerBaseTest extends AndroidTestCase {

    private HandlerThread mHandlerThread;
    protected FakeTaskHandler mHandler;
    protected AbstractProvisioningController mController;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        // this is necessary for mockito to work
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().toString());
        MockitoAnnotations.initMocks(this);
        mHandlerThread = new HandlerThread("TestHandler");
        mHandlerThread.start();
        mHandler = new FakeTaskHandler(mHandlerThread.getLooper());
    }

    @Override
    public void tearDown() throws Exception {
        mHandlerThread.quitSafely();
        super.tearDown();
    }

    protected void taskSucceeded(Class expected) throws Exception {
        AbstractProvisioningTask task = verifyTaskRun(expected);
        // WHEN the task completes successfully
        mController.onSuccess(task);
    }

    protected AbstractProvisioningTask verifyTaskRun(Class expected) throws Exception {
        AbstractProvisioningTask task = mHandler.getLastTask();
        assertNotNull(task);
        assertEquals(expected, task.getClass());
        return task;
    }

    protected class FakeTaskHandler extends Handler {

        FakeTaskHandler(Looper looper) {
            super(looper);
        }

        private BlockingQueue<AbstractProvisioningTask> mBlockingQueue
                = new ArrayBlockingQueue<>(1);

        public AbstractProvisioningTask getLastTask() throws Exception {
            return mBlockingQueue.poll(10, TimeUnit.SECONDS);
        }

        public void handleMessage(Message msg) {
            if (msg.what == MSG_RUN_TASK) {
                assertTrue(mBlockingQueue.add((AbstractProvisioningTask) msg.obj));
            } else {
                fail("Unknown message " + msg.what);
            }
        }
    }
}
