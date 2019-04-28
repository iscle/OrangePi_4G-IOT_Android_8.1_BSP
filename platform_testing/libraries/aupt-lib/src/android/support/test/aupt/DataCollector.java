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
 * limitations under the License.
 */

package android.support.test.aupt;

import android.app.Instrumentation;
import android.os.SystemClock;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class DataCollector {
    private static final String TAG = "AuptDataCollector";

    private final AtomicBoolean mStopped = new AtomicBoolean(true);
    private final Map<LogGenerator, Long> generatorsWithIntervals = new HashMap<>();
    private final Map<LogGenerator, Long> mLastUpdate = new HashMap<>();
    private final Instrumentation instrumentation;
    private final String resultsDirectory;
    private final long mSleepInterval;

    private Thread mThread;

    /**
     * Add a generator iff the interval is valid (i.e. > 0).
     */
    private void put(LogGenerator key, Long interval) {
        if (interval > 0) {
            generatorsWithIntervals.put(key, interval);
        }
    }

    public DataCollector(long bugreportInterval, long graphicsInterval,      long meminfoInterval,
                         long cpuinfoInterval,   long fragmentationInterval, long ionHeapInterval,
                         long pagetypeinfoInterval, long traceInterval,
                         long bugreportzInterval, File outputLocation, Instrumentation instr) {

        resultsDirectory = outputLocation.getPath();
        instrumentation = instr;

        if (bugreportzInterval > 0) {
            put(LogGenerator.BUGREPORTZ, bugreportzInterval);
            if (bugreportInterval > 0) {
                Log.w(TAG, String.format("Both zipped and flat bugreports are enabled. Defaulting"
                        + " to use zipped bugreports, at %s ms interval.", bugreportzInterval));
            }
        } else if (bugreportInterval > 0) {
            put(LogGenerator.BUGREPORT, bugreportInterval);
        }
        put(LogGenerator.CPU_INFO, cpuinfoInterval);
        put(LogGenerator.FRAGMENTATION, fragmentationInterval);
        put(LogGenerator.GRAPHICS_STATS, graphicsInterval);
        put(LogGenerator.ION_HEAP, ionHeapInterval);
        put(LogGenerator.MEM_INFO, meminfoInterval);
        put(LogGenerator.PAGETYPE_INFO, pagetypeinfoInterval);
        put(LogGenerator.TRACE, traceInterval);

        mSleepInterval = gcd(generatorsWithIntervals.values());
    }

    public synchronized void start() {
        if (mStopped.getAndSet(false)) {
            /* Initialize the LastUpdates to the current time */
            for (Map.Entry<LogGenerator, Long> entry : generatorsWithIntervals.entrySet()) {
                if (entry.getValue() > 0) {
                    Log.d(TAG, "Collecting " + entry.getKey() + " logs every " +
                        entry.getValue() + " milliseconds");

                    mLastUpdate.put(entry.getKey(), SystemClock.uptimeMillis());
                }
            }

            mThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    loop();
                }
            });
            mThread.start();
        } else {
            Log.e(TAG, "Tried to start a started DataCollector!");
        }
    }

    public synchronized void stop() {
        if (!mStopped.getAndSet(true)) {
            mThread.interrupt();

            try {
                mThread.join();
            } catch (InterruptedException e) {
                // ignore
            }
        } else {
            Log.e(TAG, "Tried to stop a stoppped DataCollector!");
        }
    }

    private void loop() {
        if (mSleepInterval <= 0) {
            return;
        }

        while (!mStopped.get()) {
            try {
                for (Map.Entry<LogGenerator, Long> entry : generatorsWithIntervals.entrySet()) {
                    Long t = SystemClock.uptimeMillis() - mLastUpdate.get(entry.getKey());

                    if (entry.getValue() > 0 && t >= entry.getValue()) {
                        try {
                            entry.getKey().save(instrumentation, resultsDirectory);
                        } catch (IOException ex) {
                            Log.e(TAG, "Error writing results in " + resultsDirectory +
                                    ": " + ex.toString());
                        }

                        mLastUpdate.put(entry.getKey(), SystemClock.uptimeMillis());
                    }
                }

                Thread.sleep(mSleepInterval);
            } catch (InterruptedException e) {
                // Ignore.
            }
        }
    }

    private long gcd(Collection<Long> values) {
        if (values.size() < 1) {
            return 0;
        }

        long gcdSoFar = values.iterator().next();

        for (Long value : values) {
            gcdSoFar = gcd(gcdSoFar, value);
        }

        return gcdSoFar;
    }

    private long gcd(long a, long b) {
        if (a == 0) {
            return b;
        } else if (b == 0) {
            return a;
        } else if (a > b) {
            return gcd(b, a % b);
        } else {
            return gcd(a, b % a);
        }
    }
}
