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
 * limitations under the License.
 */

package com.android.tv.analytics;

import android.support.annotation.VisibleForTesting;

import com.android.tv.TimeShiftManager;
import com.android.tv.data.Channel;

/**
 * A implementation of Tracker that does nothing.
 */
@VisibleForTesting
public class StubTracker implements Tracker {
    @Override
    public void sendChannelCount(int browsableChannelCount, int totalChannelCount) { }

    @Override
    public void sendConfigurationInfo(ConfigurationInfo info) { }

    @Override
    public void sendMainStart() { }

    @Override
    public void sendMainStop(long durationMs) { }

    @Override
    public void sendScreenView(String screenName) { }

    @Override
    public void sendChannelViewStart(Channel channel, boolean tunedByRecommendation) { }

    @Override
    public void sendChannelTuneTime(Channel channel, long durationMs) { }

    @Override
    public void sendChannelViewStop(Channel channel, long durationMs) { }

    @Override
    public void sendChannelUp() { }

    @Override
    public void sendChannelDown() { }

    @Override
    public void sendShowMenu() { }

    @Override
    public void sendHideMenu(long durationMs) { }

    @Override
    public void sendMenuClicked(String label) { }

    @Override
    public void sendMenuClicked(int labelResId) { }

    @Override
    public void sendShowEpg() { }

    @Override
    public void sendEpgItemClicked() { }

    @Override
    public void sendHideEpg(long durationMs) { }

    @Override
    public void sendShowChannelSwitch() { }

    @Override
    public void sendHideChannelSwitch(long durationMs) { }

    @Override
    public void sendChannelNumberInput() { }

    @Override
    public void sendChannelInputNavigated() { }

    @Override
    public void sendChannelNumberItemClicked() { }

    @Override
    public void sendChannelNumberItemChosenByTimeout() { }

    @Override
    public void sendChannelVideoUnavailable(Channel channel, int reason) { }

    @Override
    public void sendAc3PassthroughCapabilities(boolean isSupported) { }

    @Override
    public void sendInputConnectionFailure(String inputId) { }

    @Override
    public void sendInputDisconnected(String inputId) { }

    @Override
    public void sendShowInputSelection() { }

    @Override
    public void sendHideInputSelection(long durationMs) { }

    @Override
    public void sendInputSelected(String inputLabel) { }

    @Override
    public void sendShowSidePanel(HasTrackerLabel trackerLabel) { }

    @Override
    public void sendHideSidePanel(HasTrackerLabel trackerLabel, long durationMs) { }

    @Override
    public void sendTimeShiftAction(@TimeShiftManager.TimeShiftActionId int actionId) { }
}
