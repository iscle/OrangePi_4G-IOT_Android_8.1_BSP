/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tv.util;

import android.support.annotation.NonNull;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A thread factory that creates threads with a suffix.
 */
public class NamedThreadFactory implements ThreadFactory {
    private final AtomicInteger mCount = new AtomicInteger(0);
    private final ThreadFactory mDefaultThreadFactory;
    private final String mPrefix;

    public NamedThreadFactory(final String baseName) {
        mDefaultThreadFactory = Executors.defaultThreadFactory();
        mPrefix = baseName + "-";
    }

    @Override
    public Thread newThread(@NonNull final Runnable runnable) {
        final Thread thread = mDefaultThreadFactory.newThread(runnable);
        thread.setName(mPrefix + mCount.getAndIncrement());
        return thread;
    }

    public boolean namedWithPrefix(Thread thread) {
        return thread.getName().startsWith(mPrefix);
    }
}
