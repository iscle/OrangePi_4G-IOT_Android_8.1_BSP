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

package com.android.managedprovisioning.task;

import android.app.AlarmManager;
import android.content.Context;

import com.android.internal.app.LocalePicker;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.model.ProvisioningParams;

import java.util.Locale;

/**
 * Initialization of locale and timezone.
 */
public class DeviceOwnerInitializeProvisioningTask extends AbstractProvisioningTask {

    public DeviceOwnerInitializeProvisioningTask(Context context, ProvisioningParams params,
            Callback callback) {
        super(context, params, callback);
    }

    @Override
    public int getStatusMsgId() {
        return R.string.progress_initialize;
    }

    @Override
    public void run(int userId) {
        setTimeAndTimezone(mProvisioningParams.timeZone, mProvisioningParams.localTime);
        setLocale(mProvisioningParams.locale);

        success();
    }

    private void setTimeAndTimezone(String timeZone, long localTime) {
        try {
            final AlarmManager alarmManager = mContext.getSystemService(AlarmManager.class);
            if (timeZone != null) {
                alarmManager.setTimeZone(timeZone);
            }
            if (localTime > 0) {
                alarmManager.setTime(localTime);
            }
        } catch (Exception e) {
            ProvisionLogger.loge("Alarm manager failed to set the system time/timezone.", e);
            // Do not stop provisioning process, but ignore this error.
        }
    }

    private void setLocale(Locale locale) {
        if (locale == null || locale.equals(Locale.getDefault())) {
            return;
        }
        try {
            // If locale is different from current locale this results in a configuration change,
            // which will trigger the restarting of the activity.
            LocalePicker.updateLocale(locale);
        } catch (Exception e) {
            ProvisionLogger.loge("Failed to set the system locale.", e);
            // Do not stop provisioning process, but ignore this error.
        }
    }
}
