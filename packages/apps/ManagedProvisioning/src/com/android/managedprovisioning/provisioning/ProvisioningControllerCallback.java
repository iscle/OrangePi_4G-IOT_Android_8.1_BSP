/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.managedprovisioning.provisioning;

/**
 * Interface for communication of updates from the provisioning controller.
 */
public interface ProvisioningControllerCallback extends ProvisioningManagerCallback {
    /**
     * Method called when the provisioning process was successfully clean up. This can occur after
     * an error or after the user cancelled progress.
     */
    void cleanUpCompleted();

    /**
     * Method called to indicate that the provisioning tasks have been completed.
     */
    void provisioningTasksCompleted();
}
