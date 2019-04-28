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
 * Interface for listeners to the {@link ProvisioningManager}. A listener can be registered for
 * updated via {@link ProvisioningManager#registerListener(ProvisioningManagerCallback)}.
 */
public interface ProvisioningManagerCallback {
    /**
     * Method called when an error was encountered during the provisioning process.
     *
     * @param dialogTitleId resource id of the error title to be displayed to the user.
     * @param errorMessageId resource id of the error message to be displayed to the user.
     * @param factoryResetRequired indicating whether a factory reset is necessary.
     */
    void error(int dialogTitleId, int errorMessageId, boolean factoryResetRequired);

    /**
     * Method called to indicate a progress update in the provisioning process.
     *
     * @param progressMessageId resource id of the progress message to be displayed to the user.
     */
    void progressUpdate(int progressMessageId);

    /**
     * Method called to indicate that pre-finalization has completed.
     */
    void preFinalizationCompleted();
}
