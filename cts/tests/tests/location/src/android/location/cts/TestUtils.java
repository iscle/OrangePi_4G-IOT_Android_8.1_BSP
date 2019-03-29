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

package android.location.cts;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class TestUtils {
    private static final long STANDARD_WAIT_TIME_MS = 50;
    private static final long STANDARD_SLEEP_TIME_MS = 50;

    public static boolean waitFor(CountDownLatch latch, int timeInSec) throws InterruptedException {
        // Since late 2014, if the main thread has been occupied for long enough, Android will
        // increase its priority. Such new behavior can causes starvation to the background thread -
        // even if the main thread has called await() to yield its execution, the background thread
        // still can't get scheduled.
        //
        // Here we're trying to wait on the main thread for a PendingIntent from a background
        // thread. Because of the starvation problem, the background thread may take up to 5 minutes
        // to deliver the PendingIntent if we simply call await() on the main thread. In order to
        // give the background thread a chance to run, we call Thread.sleep() in a loop. Such dirty
        // hack isn't ideal, but at least it can work.
        //
        // See also: b/17423027
        long waitTimeRounds = (TimeUnit.SECONDS.toMillis(timeInSec)) /
                (STANDARD_WAIT_TIME_MS + STANDARD_SLEEP_TIME_MS);
        for (int i = 0; i < waitTimeRounds; ++i) {
            Thread.sleep(STANDARD_SLEEP_TIME_MS);
            if (latch.await(STANDARD_WAIT_TIME_MS, TimeUnit.MILLISECONDS)) {
                return true;
            }
        }
        return false;
    }
    public static boolean waitForWithCondition(int timeInSec, Callable<Boolean> callback)
        throws Exception {
        long waitTimeRounds = (TimeUnit.SECONDS.toMillis(timeInSec)) / STANDARD_SLEEP_TIME_MS;
        for (int i = 0; i < waitTimeRounds; ++i) {
            Thread.sleep(STANDARD_SLEEP_TIME_MS);
            if(callback.call()) return true;
        }
        return false;
    }

}
