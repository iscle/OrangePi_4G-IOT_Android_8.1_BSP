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

package com.android.managedprovisioning.ota;

import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.task.AbstractProvisioningTask;

/**
 * Class that executes the provisioning tasks during the OTA process.
 */
public class TaskExecutor implements AbstractProvisioningTask.Callback {

    public synchronized void execute(int userId, AbstractProvisioningTask task) {
        task.run(userId);
    }

    @Override
    public void onSuccess(AbstractProvisioningTask task) {
        ProvisionLogger.logd("Task ran successfully: " + task.getClass().getSimpleName());
    }

    @Override
    public void onError(AbstractProvisioningTask task, int errorMsg) {
        ProvisionLogger.logd("Error running task: " + task.getClass().getSimpleName());
    }
}
