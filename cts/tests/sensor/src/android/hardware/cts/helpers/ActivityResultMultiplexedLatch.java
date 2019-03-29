/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.hardware.cts.helpers;

import java.util.HashMap;
import java.util.concurrent.CountDownLatch;

/**
 * An abstraction on top of {@link CountDownLatch} to synchronize the results of Activities
 * started by a parent activity.
 *
 * It holds a {@link CountDownLatch} latch for each thread that requests synchronization.
 *
 * Each thread requests a {@link Latch} to synchronize an Activity that will be started, by invoking
 * {@link #bindThread()}, this guarantees that a latch is associated with the thread, and the result
 * can be retrieved.
 */
public class ActivityResultMultiplexedLatch {
    private static final String TAG = "ActivityResultMultiplexedLatch";

    private final HashMap<Integer, Entry> mActivityEntries = new HashMap<Integer, Entry>();

    /**
     * A latch for a bound thread.
     * Applications get an instance by invoking {@link ActivityResultMultiplexedLatch#bindThread()}.
     */
    public class Latch {
        private Entry mEntry;

        private Latch(Entry entry) {
            mEntry = entry;
        }

        /**
         * Awaits for the Activity bound to unblock the current thread.
         *
         * @return The result code of the Activity executed.
         */
        public int await() throws InterruptedException {
            mEntry.latch.await();
            return mEntry.resultCode;
        }

        /**
         * @return A request code for the bound thread. It can be passed to the Activity to start.
         */
        public int getRequestCode() {
            return mEntry.requestCode;
        }
    }

    /**
     * A class that represents the state for each thread/Activity being tracked.
     */
    private class Entry {
        public final CountDownLatch latch = new CountDownLatch(1);
        public final int requestCode;

        public volatile int resultCode;

        public Entry(int requestCode) {
            this.requestCode = requestCode;
        }
    }

    /**
     * Binds a thread with this object.
     *
     * @return A request code (or session Id) for the bound thread.
     */
    public Latch bindThread() {
        Entry entry;
        int requestCode = getRequestCode();

        synchronized (mActivityEntries) {
            if (mActivityEntries.containsKey(requestCode)) {
                throw new IllegalStateException("The thread has already been bound.");
            }
            entry = new Entry(requestCode);
            mActivityEntries.put(requestCode, entry);
        }

        return new Latch(entry);
    }

    /**
     * Used by the owner of the instance to record an Activity's result.
     */
    public void onActivityResult(int requestCode, int resultCode) {
        Entry entry;
        synchronized (mActivityEntries) {
            entry = mActivityEntries.remove(requestCode);
        }
        if (entry == null) {
            return;
        }

        entry.resultCode = resultCode;
        entry.latch.countDown();
    }

    // there is no need for a better request Id, only one Activity can be launched at any time
    private int getRequestCode() {
        return Thread.currentThread().hashCode();
    }
}
