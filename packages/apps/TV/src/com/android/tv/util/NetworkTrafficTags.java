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
 * limitations under the License
 */

package com.android.tv.util;

import android.net.TrafficStats;
import android.support.annotation.NonNull;

import java.util.concurrent.Executor;

/** Constants for tagging network traffic in the Live channels app. */
public final class NetworkTrafficTags {

    public static final int DEFAULT_LIVE_CHANNELS = 1;
    public static final int LOGO_FETCHER = 2;
    public static final int HDHOMERUN = 3;
    public static final int EPG_FETCH = 4;

    /**
     * An executor which simply wraps a provided delegate executor, but calls {@link
     * TrafficStats#setThreadStatsTag(int)} before executing any task.
     */
    public static class TrafficStatsTaggingExecutor implements Executor {
        private final Executor delegateExecutor;
        private final int tag;

        public TrafficStatsTaggingExecutor(Executor delegateExecutor, int tag) {
            this.delegateExecutor = delegateExecutor;
            this.tag = tag;
        }

        @Override
        public void execute(final @NonNull Runnable command) {
            // TODO(b/62038127): robolectric does not support lamdas in unbundled apps
            delegateExecutor.execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            TrafficStats.setThreadStatsTag(tag);
                            try {
                                command.run();
                            } finally {
                                TrafficStats.clearThreadStatsTag();
                            }
                        }
                    });
        }
    }

    private NetworkTrafficTags() {}
}
