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

package com.android.tv.config;

import android.content.Context;

/**
 * Stub Remote Config.
 */
public class DefaultConfigManager {
    public static final long DEFAULT_LONG_VALUE = 0;
    public static DefaultConfigManager createInstance(Context context) {
        return new DefaultConfigManager();
    }

    private StubRemoteConfig mRemoteConfig = new StubRemoteConfig();

    public RemoteConfig getRemoteConfig() {
        return mRemoteConfig;
    }

    private static class StubRemoteConfig implements RemoteConfig {
        @Override
        public void fetch(OnRemoteConfigUpdatedListener listener) {

        }

        @Override
        public String getString(String key) {
            return null;
        }

        @Override
        public boolean getBoolean(String key) {
            return false;
        }

        @Override
        public long getLong(String key) {
            return DEFAULT_LONG_VALUE;
        }
    }
}




