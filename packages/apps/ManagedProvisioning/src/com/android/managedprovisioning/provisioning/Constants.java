/*
 * Copyright 2016, The Android Open Source Project
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

package com.android.managedprovisioning.provisioning;

/**
 * Constants used for communication between service and activity.
 */
public final class Constants {
    // Intents sent by the activity to the service
    /**
     * Intent action sent to the {@link ProvisioningService} to indicate that provisioning should be
     * started.
     */
    public static final String ACTION_START_PROVISIONING =
            "com.android.managedprovisioning.START_PROVISIONING";

    private Constants() {
        // Do not instantiate
    }
}
