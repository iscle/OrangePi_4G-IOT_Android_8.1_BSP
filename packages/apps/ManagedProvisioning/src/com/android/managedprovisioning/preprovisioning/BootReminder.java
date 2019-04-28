/*
 * Copyright 2014, The Android Open Source Project
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
package com.android.managedprovisioning.preprovisioning;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Class that handles the resuming process that takes place after a reboot during the provisioning
 * process. The reboot could be an unexpected reboot or a reboot during the encryption process.
 *
 * <p>For encryption flows during setup wizard, this class acts as a backup to
 * PostEncryptionActivity in case the PackageManager has not yet written the package state to disk
 * when the reboot is triggered.
 */
public class BootReminder extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        EncryptionController.getInstance(context).resumeProvisioning();
    }
}

