/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.os.cts;

import android.support.annotation.NonNull;
import android.os.AsyncTask;
import android.test.InstrumentationTestCase;

import com.android.compatibility.common.util.PollingCheck;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public class AsyncTaskTest extends InstrumentationTestCase {
    private static final long COMPUTE_TIME = 1000;
    private static final long RESULT = 1000;
    private static final Integer[] UPDATE_VALUE = { 0, 1, 2 };
    private static final long DURATION = 2000;
    private static final String[] PARAM = { "Test" };

    private static AsyncTask mAsyncTask;
    private static MyAsyncTask mMyAsyncTask;

    public void testAsyncTask() throws Throwable {
        doTestAsyncTask(0);
    }

    public void testAsyncTaskWithTimeout() throws Throwable {
        doTestAsyncTask(DURATION);
    }

    private void doTestAsyncTask(final long timeout) throws Throwable {
        startAsyncTask();
        if (timeout > 0) {
            assertEquals(RESULT, mMyAsyncTask.get(DURATION, TimeUnit.MILLISECONDS).longValue());
        } else {
            assertEquals(RESULT, mMyAsyncTask.get().longValue());
        }

        // wait for the task to finish completely (including onPostResult()).
        new PollingCheck(DURATION) {
            protected boolean check() {
                return mMyAsyncTask.getStatus() == AsyncTask.Status.FINISHED;
            }
        }.run();

        assertTrue(mMyAsyncTask.isOnPreExecuteCalled);
        assert(mMyAsyncTask.hasRun);
        assertEquals(PARAM.length, mMyAsyncTask.parameters.length);
        for (int i = 0; i < PARAM.length; i++) {
            assertEquals(PARAM[i], mMyAsyncTask.parameters[i]);
        }
        // even though the background task has run, the onPostExecute() may not have been
        // executed yet and the progress update may not have been processed. Wait until the task
        // has completed, which guarantees that onPostExecute has been called.

        assertEquals(RESULT, mMyAsyncTask.postResult.longValue());
        assertEquals(AsyncTask.Status.FINISHED, mMyAsyncTask.getStatus());

        if (mMyAsyncTask.exception != null) {
            throw mMyAsyncTask.exception;
        }

        // wait for progress update to be processed (happens asynchronously)
        new PollingCheck(DURATION) {
            protected boolean check() {
                return mMyAsyncTask.updateValue != null;
            }
        }.run();
        assertEquals(UPDATE_VALUE.length, mMyAsyncTask.updateValue.length);
        for (int i = 0; i < UPDATE_VALUE.length; i++) {
            assertEquals(UPDATE_VALUE[i], mMyAsyncTask.updateValue[i]);
        }

        runTestOnUiThread(new Runnable() {
            public void run() {
                try {
                    // task should not be allowed to execute twice
                    mMyAsyncTask.execute(PARAM);
                    fail("Failed to throw exception!");
                } catch (IllegalStateException e) {
                    // expected
                }
            }
        });
    }

    public void testCancelWithInterrupt() throws Throwable {
        startAsyncTask();
        Thread.sleep(COMPUTE_TIME / 2);
        assertTrue(mMyAsyncTask.cancel(true));
        // already cancelled
        assertFalse(mMyAsyncTask.cancel(true));
        Thread.sleep(DURATION);
        assertTrue(mMyAsyncTask.isCancelled());
        assertTrue(mMyAsyncTask.isOnCancelledCalled);
        assertNotNull(mMyAsyncTask.exception);
        assertTrue(mMyAsyncTask.exception instanceof InterruptedException);
    }

    public void testCancel() throws Throwable {
        startAsyncTask();
        Thread.sleep(COMPUTE_TIME / 2);
        assertTrue(mMyAsyncTask.cancel(false));
        // already cancelled
        assertFalse(mMyAsyncTask.cancel(false));
        Thread.sleep(DURATION);
        assertTrue(mMyAsyncTask.isCancelled());
        assertTrue(mMyAsyncTask.isOnCancelledCalled);
        assertNull(mMyAsyncTask.exception);
    }

    public void testCancelTooLate() throws Throwable {
        startAsyncTask();
        Thread.sleep(DURATION);
        assertFalse(mMyAsyncTask.cancel(false));
        assertTrue(mMyAsyncTask.isCancelled());
        assertFalse(mMyAsyncTask.isOnCancelledCalled);
        assertNull(mMyAsyncTask.exception);
    }

    public void testCancellationWithException() throws Throwable {
        final CountDownLatch readyToCancel = new CountDownLatch(1);
        final CountDownLatch readyToThrow = new CountDownLatch(1);
        final CountDownLatch calledOnCancelled = new CountDownLatch(1);
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                mAsyncTask = new AsyncTask() {
                    @Override
                    protected Object doInBackground(Object... params) {
                        readyToCancel.countDown();
                        try {
                            readyToThrow.await();
                        } catch (InterruptedException e) {}
                        // This exception is expected to be caught and ignored
                        throw new RuntimeException();
                    }

                    @Override
                    protected void onCancelled(Object o) {
                        calledOnCancelled.countDown();
                    }
                };
            }
        });

        mAsyncTask.execute();
        if (!readyToCancel.await(5, TimeUnit.SECONDS)) {
            fail("Test failure: doInBackground did not run in time.");
        }
        mAsyncTask.cancel(false);
        readyToThrow.countDown();
        if (!calledOnCancelled.await(5, TimeUnit.SECONDS)) {
            fail("onCancelled not called!");
        }
    }

    public void testException() throws Throwable {
        final CountDownLatch calledOnCancelled = new CountDownLatch(1);
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                mAsyncTask = new AsyncTask() {
                    @Override
                    protected Object doInBackground(Object... params) {
                        throw new RuntimeException();
                    }

                    @Override
                    protected void onPostExecute(Object o) {
                        fail("onPostExecute should not be called");
                    }

                    @Override
                    protected void onCancelled(Object o) {
                        calledOnCancelled.countDown();
                    }
                };
            }
        });

        mAsyncTask.executeOnExecutor(new Executor() {
            @Override
            public void execute(@NonNull Runnable command) {
                try {
                    command.run();
                    fail("Exception not thrown");
                } catch (Exception tr) {
                    // expected
                }
            }
        });

        if (!calledOnCancelled.await(5, TimeUnit.SECONDS)) {
            fail("onCancelled not called!");
        }
    }

    private void startAsyncTask() throws Throwable {
        runTestOnUiThread(new Runnable() {
            public void run() {
                mMyAsyncTask = new MyAsyncTask();
                assertEquals(AsyncTask.Status.PENDING, mMyAsyncTask.getStatus());
                assertEquals(mMyAsyncTask, mMyAsyncTask.execute(PARAM));
                assertEquals(AsyncTask.Status.RUNNING, mMyAsyncTask.getStatus());
            }
        });
    }

    private static class MyAsyncTask extends AsyncTask<String, Integer, Long> {
        public boolean isOnCancelledCalled;
        public boolean isOnPreExecuteCalled;
        public boolean hasRun;
        public Exception exception;
        public Long postResult;
        public Integer[] updateValue;
        public String[] parameters;

        @Override
        protected Long doInBackground(String... params) {
            hasRun = true;
            parameters = params;
            try {
                publishProgress(UPDATE_VALUE);
                Thread.sleep(COMPUTE_TIME);
            } catch (Exception e) {
                exception = e;
            }
            return RESULT;
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            isOnCancelledCalled = true;
        }

        @Override
        protected void onPostExecute(Long result) {
            super.onPostExecute(result);
            postResult = result;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            isOnPreExecuteCalled = true;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            updateValue = values;
        }
    }
}
