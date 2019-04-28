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
package com.android.managedprovisioning.common;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.VisibleForTesting;

public class ManagedProvisioningSharedPreferences {
    public static final long DEFAULT_PROVISIONING_ID = 0L;

    @VisibleForTesting
    static final String KEY_PROVISIONING_ID = "provisioning_id";

    @VisibleForTesting
    static final String SHARED_PREFERENCE = "managed_profile_shared_preferences";

    /**
     * It's a process-wise in-memory write lock. No other processes will write the same file.
     */
    private static final Object sWriteLock = new Object();

    private final SharedPreferences mSharedPreferences;

    public ManagedProvisioningSharedPreferences(Context context) {
        mSharedPreferences = context.getSharedPreferences(SHARED_PREFERENCE, Context.MODE_PRIVATE);
    }

    @VisibleForTesting
    public long getProvisioningId() {
        return mSharedPreferences.getLong(KEY_PROVISIONING_ID, DEFAULT_PROVISIONING_ID);
    }

    /**
     * Can assume the id is unique across all provisioning sessions
     * @return a new provisioning id by incrementing the current id
     */
    public long incrementAndGetProvisioningId() {
        synchronized (sWriteLock) {
            long provisioningId = getProvisioningId();
            provisioningId++;
            // commit synchronously
            mSharedPreferences.edit().putLong(KEY_PROVISIONING_ID, provisioningId).commit();
            return provisioningId;
        }
    }
}
