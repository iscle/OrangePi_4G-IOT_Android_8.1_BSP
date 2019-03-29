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

package android.print.cts;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.print.PrintJob;
import android.print.PrintManager;
import android.support.annotation.NonNull;
import android.util.Log;

/**
 * Utilities for print tests
 */
public class Utils {
    private static final String LOG_TAG = "Utils";

    /**
     * A {@link Runnable} that can throw an {@link Throwable}.
     */
    public interface Invokable {
        void run() throws Throwable;
    }

    /**
     * Run a {@link Invokable} and expect and {@link Throwable} of a certain type.
     *
     * @param r             The {@link Invokable} to run
     * @param expectedClass The expected {@link Throwable} type
     */
    public static void assertException(@NonNull Invokable r,
            @NonNull Class<? extends Throwable> expectedClass) throws Throwable {
        try {
            r.run();
        } catch (Throwable e) {
            if (e.getClass().isAssignableFrom(expectedClass)) {
                return;
            } else {
                Log.e(LOG_TAG, "Expected: " + expectedClass.getName() + ", got: "
                        + e.getClass().getName());
                throw e;
            }
        }

        throw new AssertionError("No throwable thrown");
    }

    /**
     * Run a {@link Invokable} on the main thread and forward the {@link Throwable} if one was
     * thrown.
     *
     * @param r The {@link Invokable} to run
     *
     * @throws Throwable If the {@link Runnable} caused an issue
     */
    static void runOnMainThread(@NonNull final Invokable r) throws Throwable {
        final Object synchronizer = new Object();
        final Throwable[] thrown = new Throwable[1];

        synchronized (synchronizer) {
            (new Handler(Looper.getMainLooper())).post(() -> {
                synchronized (synchronizer) {
                    try {
                        r.run();
                    } catch (Throwable t) {
                        thrown[0] = t;
                    }

                    synchronizer.notify();
                }
            });

            synchronizer.wait();
        }

        if (thrown[0] != null) {
            throw thrown[0];
        }
    }

    /**
     * Make sure that a {@link Invokable} eventually finishes without throwing a {@link Throwable}.
     *
     * @param r The {@link Invokable} to run.
     */
    public static void eventually(@NonNull Invokable r) throws Throwable {
        long start = System.currentTimeMillis();

        while (true) {
            try {
                r.run();
                break;
            } catch (Throwable e) {
                if (System.currentTimeMillis() - start < BasePrintTest.OPERATION_TIMEOUT_MILLIS) {
                    Log.e(LOG_TAG, "Ignoring exception", e);

                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e1) {
                        Log.e(LOG_TAG, "Interrupted", e);
                    }
                } else {
                    throw e;
                }
            }
        }
    }

    /**
     * @param name Name of print job
     *
     * @return The print job for the name
     *
     * @throws Exception If print job could not be found
     */
    static @NonNull PrintJob getPrintJob(@NonNull PrintManager pm, @NonNull String name)
            throws Exception {
        for (android.print.PrintJob job : pm.getPrintJobs()) {
            if (job.getInfo().getLabel().equals(name)) {
                return job;
            }
        }

        throw new Exception("Print job " + name + " not found in " + pm.getPrintJobs());
    }

    /**
     * @return The print manager
     */
    static @NonNull PrintManager getPrintManager(@NonNull Context context) {
        return (PrintManager) context.getSystemService(Context.PRINT_SERVICE);
    }
}
