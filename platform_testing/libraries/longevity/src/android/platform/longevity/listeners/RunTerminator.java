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
package android.platform.longevity.listeners;

import android.util.Log;

import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;

/**
 * An extension of the {@link RunListener} class that provides hooks to the {@code RunNotifier} for
 * killing tests early.
 */
public abstract class RunTerminator extends RunListener {
    private RunNotifier mNotifier;

    public RunTerminator(RunNotifier notifier) {
        mNotifier = notifier;
    }

    /**
     * Kills subsequent tests and logs a message for future debugging.
     */
    protected final void kill(String reason) {
        Log.d(getClass().getSimpleName(),
                String.format("Test run killed by %s because %s.",
                        getClass().getSimpleName(), reason));
        mNotifier.pleaseStop();
    }
}
