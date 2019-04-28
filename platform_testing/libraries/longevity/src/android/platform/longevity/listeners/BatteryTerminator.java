/*
 * Copyright (C) 2017 The Android Open Source Project
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
package android.platform.longevity.listeners;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.VisibleForTesting;

import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;

/**
 * An {@link ActionListener} for terminating early on test end due to low battery.
 */
public final class BatteryTerminator extends RunTerminator {
    @VisibleForTesting
    static final String OPTION = "min-battery";
    private static final double DEFAULT = 0.05; // 5% battery

    private final Context mContext;
    private final double mMinBattery;

    public BatteryTerminator(RunNotifier notifier, Bundle args, Context context) {
        super(notifier);
        mMinBattery = Double.parseDouble(args.getString(OPTION, String.valueOf(DEFAULT)));
        mContext = context;
    }

    /**
     * Returns the battery level of the current device, in percent format (0.05 = 5%).
     */
    private double getBatteryLevel() {
        Intent batteryIntent =
            mContext.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = batteryIntent.getIntExtra("level", -1);
        int scale = batteryIntent.getIntExtra("scale", -1);
        if (level < 0 || scale <= 0) {
            throw new RuntimeException("Failed to get proper battery levels.");
        }
        return (double)level / (double)scale;
    }

    @Override
    public void testFinished(Description description) {
        if (getBatteryLevel() < mMinBattery) {
            kill(String.format("battery fell below %.2f%%", mMinBattery * 100.0f));
        }
    }
}
