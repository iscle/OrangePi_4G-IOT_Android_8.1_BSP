/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.autofillservice.cts;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper used to catch multiple exceptions that might have happened in a test case.
 */
// TODO: move to common CTS code (and add test cases to it)
public final class MultipleExceptionsCatcher {

    private static final String TAG = "MultipleExceptionsCatcher";

    private final List<Throwable> mThrowables = new ArrayList<>();

    /**
     * Runs {@code r} postponing any thrown exception to {@link #throwIfAny()}.
     */
    public MultipleExceptionsCatcher run(@NonNull Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            mThrowables.add(t);
        }
        return this;
    }

    /**
     * Adds an exception - if it's not {@code null} to the exceptions thrown by
     * {@link #throwIfAny()}.
     */
    public MultipleExceptionsCatcher add(@Nullable Throwable t) {
        if (t != null) {
            mThrowables.add(t);
        }
        return this;
    }

    /**
     * Throws one exception merging all exceptions thrown or added so far, if any.
     */
    public void throwIfAny() throws Throwable {
        if (mThrowables.isEmpty()) return;

        final int numberExceptions = mThrowables.size();
        if (numberExceptions == 1) {
            throw mThrowables.get(0);
        }

        String msg = "D'OH!";
        try {
            try (StringWriter sw = new StringWriter(); PrintWriter pw = new PrintWriter(sw)) {
                sw.write("Caught " + numberExceptions + " exceptions\n");
                for (int i = 0; i < numberExceptions; i++) {
                    sw.write("\n---- Begin of exception #" + (i + 1) + " ----\n");
                    final Throwable exception = mThrowables.get(i);
                    exception.printStackTrace(pw);
                    sw.write("---- End of exception #" + (i + 1) + " ----\n\n");
                }
                msg = sw.toString();
            }
        } catch (IOException e) {
            // ignore close() errors - should not happen...
            Log.e(TAG, "Exception closing StringWriter: " + e);
        }
        throw new AssertionError(msg);
    }
}
