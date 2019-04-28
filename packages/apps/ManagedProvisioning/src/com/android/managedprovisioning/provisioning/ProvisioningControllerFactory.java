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

import android.content.Context;
import android.os.UserHandle;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;

/**
 * Factory class to create an {@link AbstractProvisioningController} from a set of
 * {@link ProvisioningParams}.
 */
@VisibleForTesting
public class ProvisioningControllerFactory {

    private final Utils mUtils = new Utils();

    /**
     * This method constructs the controller used for the given type of provisioning.
     */
    @VisibleForTesting
    public AbstractProvisioningController createProvisioningController(
            Context context,
            ProvisioningParams params,
            ProvisioningControllerCallback callback) {
        if (mUtils.isDeviceOwnerAction(params.provisioningAction)) {
            return new DeviceOwnerProvisioningController(
                    context,
                    params,
                    UserHandle.myUserId(),
                    callback);
        } else {
            return new ProfileOwnerProvisioningController(
                    context,
                    params,
                    UserHandle.myUserId(),
                    callback);
        }
    }
}
