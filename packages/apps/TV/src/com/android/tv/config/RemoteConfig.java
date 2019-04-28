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

/**
 * Manages Live TV Configuration, allowing remote updates.
 *
 * <p>This is a thin wrapper around
 * <a href="https://firebase.google.com/docs/remote-config/"></a>Firebase Remote Config</a>
 */
public interface RemoteConfig {

    /**
     * Notified on successful completion of a {@link #fetch)}
     */
    interface OnRemoteConfigUpdatedListener {
        void onRemoteConfigUpdated();
    }

    /**
     * Starts a fetch and notifies {@code listener} on successful completion.
     */
    void fetch(OnRemoteConfigUpdatedListener listener);

    /**
     * Gets value as a string corresponding to the specified key.
     */
    String getString(String key);

    /**
     * Gets value as a boolean corresponding to the specified key.
     */
    boolean getBoolean(String key);

    /** Gets value as a long corresponding to the specified key. */
    long getLong(String key);
}
