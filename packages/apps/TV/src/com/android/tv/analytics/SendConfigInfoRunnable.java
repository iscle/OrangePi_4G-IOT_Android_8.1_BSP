/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.tv.analytics;

import android.media.tv.TvInputInfo;

import com.android.tv.util.TvInputManagerHelper;

import java.util.List;

/**
 * Sends ConfigurationInfo once a day.
 */
public class SendConfigInfoRunnable implements Runnable {
    private final Tracker mTracker;
    private final TvInputManagerHelper mTvInputManagerHelper;

    public SendConfigInfoRunnable(Tracker tracker, TvInputManagerHelper tvInputManagerHelper) {
        this.mTracker = tracker;
        this.mTvInputManagerHelper = tvInputManagerHelper;
    }

    @Override
    public void run() {
        List<TvInputInfo> infoList = mTvInputManagerHelper.getTvInputInfos(false, false);
        int systemInputCount = 0;
        int nonSystemInputCount = 0;
        for (TvInputInfo info : infoList) {
            if (mTvInputManagerHelper.isSystemInput(info)) {
                systemInputCount++;
            } else {
                nonSystemInputCount++;
            }
        }
        ConfigurationInfo configurationInfo = new ConfigurationInfo(systemInputCount,
                nonSystemInputCount);
        mTracker.sendConfigurationInfo(configurationInfo);
    }
}
