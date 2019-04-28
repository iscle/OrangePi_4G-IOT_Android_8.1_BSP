/*
 * Copyright (C) 2016 Google Inc.
 * Licensed to The Android Open Source Project.
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

package com.android.storagemanager.utils;

/**
 * Contains constants used across multiple classes in StorageManager.
 */
public class Constants {
    /**
     * A string to use for getting shared preferences. Beware key collisions when using this.
     */
    public static final String SHARED_PREFERENCE_NAME = "StorageManager";

    /**
     * Read-only property for if we need to show the storage manager in Settings. This value
     * cannot be changed due to it being used in Setup Wizard.
     */
    public static final String STORAGE_MANAGER_VISIBLE_PROPERTY = "ro.storage_manager.enabled";
}
