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
package com.android.cts.verifier.bluetooth;

import android.os.Handler;
import android.os.HandlerThread;

import java.util.ArrayList;

/**
 * TestTaskQueue runs asynchronous operations on background thread.
 *
 * TestTaskQueue holds Handler which runs on background thread.
 * Asynchronous operations will be run by adding operation by addTask().
 * Each operations will be managed by Handler.
 */
public class TestTaskQueue {

    private Handler mHandler;
    private ArrayList<Runnable> mTasks = new ArrayList<>();

    public TestTaskQueue(String threadName) {
        HandlerThread th = new HandlerThread(threadName);
        th.start();
        mHandler = new Handler(th.getLooper());
    }

    /**
     * Cancels all pending operations.
     */
    public synchronized void quit() {
        // cancel all pending operations.
        for (Runnable task : mTasks) {
            mHandler.removeCallbacks(task);
        }
        mTasks.clear();

        // terminate Handler
        mHandler.getLooper().quit();
        mHandler = null;
    }

    /**
     * Reserves new asynchronous operation.
     * Operations will be run sequentially.
     *
     * @param r new operation
     */
    public synchronized void addTask(Runnable r) {
        addTask(r, 0);
    }

    /**
     * Reserves new asynchronous operation.
     * Operations will be run sequentially.
     *
     * @param r new operation
     * @param delay delay for execution
     */
    public synchronized void addTask(final Runnable r, long delay) {
        if ((mHandler != null) && (r != null)) {
            Runnable task = new Runnable() {
                @Override
                public void run() {
                    mTasks.remove(this);
                    r.run();
                }
            };
            mTasks.add(task);
            mHandler.postDelayed(task, delay);
        }
    }
}
