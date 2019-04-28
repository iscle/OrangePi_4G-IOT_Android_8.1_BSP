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

package com.android.tv.common;

/**
 * Interface for the fine-grained memory management.
 * The class which wants to release memory based on the system constraints should inherit
 * this interface and implement {@link #performTrimMemory}.
 */
public interface MemoryManageable {
    /**
     * For more information, see {@link android.content.ComponentCallbacks2#onTrimMemory}.
     */
    void performTrimMemory(int level);
}
