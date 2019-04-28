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
package com.android.managedprovisioning.common;

public final class Globals {
    private Globals() {}

    /**
     * Make this true to enable extra logging. Do not submit when this is true.
     */
    public static final boolean DEBUG = false;

    public static final String ACTION_RESUME_PROVISIONING =
            "com.android.managedprovisioning.action.RESUME_PROVISIONING";

    public static final String MANAGED_PROVISIONING_PACKAGE_NAME =
            "com.android.managedprovisioning";
}
