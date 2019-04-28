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

package com.android.documentsui.testing;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TestScheduledExecutorService implements ScheduledExecutorService {

    private List<TestFuture> scheduled = new ArrayList<>();
    private boolean shutdown;

    @Override
    public void shutdown() {
        this.shutdown = true;
    }

    @Override
    public List<Runnable> shutdownNow() {
        this.shutdown = true;
        return Collections.emptyList();
    }

    public void assertShutdown() {
        assertTrue("Executor wasn't shut down.", shutdown);
    }

    @Override
    public boolean isShutdown() {
        return shutdown;
    }

    @Override
    public boolean isTerminated() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Future<?> submit(Runnable task) {
        return schedule(task, 0, TimeUnit.MILLISECONDS);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout,
            TimeUnit unit) throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void execute(Runnable command) {
        schedule(command, 0, TimeUnit.MILLISECONDS);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        TestFuture future = new TestFuture(command, delay, unit);
        scheduled.add(future);
        return future;
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period,
            TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay,
            long delay, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    public void runAll() {
        while (!scheduled.isEmpty()) {
            TestFuture future = scheduled.remove(scheduled.size() - 1);
            future.runnable.run();
        }
    }

    public void run(int taskIndex) {
        scheduled.get(taskIndex).runnable.run();

        scheduled.remove(taskIndex);
    }

    public void assertAlive() {
        assertFalse(isShutdown());
    }

    public void waitForTasks(long millisTimeout) throws Exception {
        millisTimeout = (millisTimeout > 0) ? millisTimeout : Long.MAX_VALUE;

        final long startTime = SystemClock.uptimeMillis();

        // We need to wait on all AsyncTasks to finish AND to post results back.
        // *** Results are posted on main thread ***, but tests run in their own
        // thread. So even with our test executor we still have races.
        //
        // To work around this issue post our own runnable to the main thread
        // which we presume will be the *last* runnable (after any from AsyncTasks)
        // and then wait for our runnable to be called.
        while (!scheduled.isEmpty() && millisTimeout > 0) {
            CountDownLatch latch = new CountDownLatch(1);
            runAll();
            new Handler(Looper.getMainLooper()).post(latch::countDown);
            latch.await(millisTimeout, TimeUnit.MILLISECONDS);

            millisTimeout -= (SystemClock.uptimeMillis() - startTime);
        }
    }

    static class TestFuture implements ScheduledFuture<Void> {

        final Runnable runnable;
        final long delay;
        final TimeUnit unit;

        public TestFuture(Runnable runnable, long delay, TimeUnit unit) {
            this.runnable = runnable;
            this.delay = delay;
            this.unit = unit;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(Delayed arg0) {
            return 0;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public Void get() throws InterruptedException, ExecutionException {
            return null;
        }

        @Override
        public Void get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            return null;
        }
    }
}
