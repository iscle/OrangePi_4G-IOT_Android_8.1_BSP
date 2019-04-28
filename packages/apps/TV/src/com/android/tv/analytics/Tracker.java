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

import com.android.tv.TimeShiftManager;
import com.android.tv.data.Channel;

/**
 * Interface for sending user activity for analysis.
 */
public interface Tracker {

    /**
     * Send the number of channels that doesn't change often.
     *
     * <p>Because the number of channels does not change often, this method should not be called
     * more than once a day.
     *
     * @param browsableChannelCount the number of browsable channels.
     * @param totalChannelCount the number of all channels.
     */
    void sendChannelCount(int browsableChannelCount, int totalChannelCount);

    /**
     * Send data that doesn't change often.
     *
     * <p>Because configuration info does not change often, this method should not be called more
     * than once a day.
     *
     * @param info the configuration info.
     */
    void sendConfigurationInfo(ConfigurationInfo info);

    /**
     * Sends tracking information for starting the MainActivity.
     */
    void sendMainStart();

    /**
     * Sends tracking for stopping MainActivity.
     *
     * @param durationMs The time main activity was "started" in milliseconds.
     */
    void sendMainStop( long durationMs);

    /**
     * Sets the screen name and sends a ScreenView hit.
     */
    void sendScreenView(String screenName);

    /**
     * Sends tracking information for starting to view a channel.
     *
     * @param channel the current channel
     * @param tunedByRecommendation True, if the channel was tuned by the recommendation.
     */
    void sendChannelViewStart(Channel channel, boolean tunedByRecommendation);

    /**
     * Sends tracking information for tuning to a channel.
     *
     * @param channel The channel that was being tuned.
     * @param durationMs The time the channel took to tune in milliseconds.
     */
    void sendChannelTuneTime(Channel channel, long durationMs);

    /**
     * Sends tracking information for stopping viewing a channel.
     *
     * @param channel The channel that was being watched.
     * @param durationMs The time the channel was watched in milliseconds.
     */
    void sendChannelViewStop(Channel channel, long durationMs);

    /**
     * Sends tracking information for pressing channel up.
     */
    void sendChannelUp();

    /**
     * Sends tracking information for pressing channel down.
     */
    void sendChannelDown();

    /**
     * Sends tracking information for showing the main menu.
     */
    void sendShowMenu();

    /**
     * Sends tracking for hiding the main menu.
     *
     * @param durationMs The duration the menu was shown in milliseconds.
     */
    void sendHideMenu(long durationMs);

    /**
     * Sends tracking for clicking a menu item.
     *
     * <p><strong>WARNING</strong> callers must ensure no PII is included in the label.
     *
     * @param label The label of the item clicked.
     */
    void sendMenuClicked(String label);

    /**
     * Sends tracking for clicking a menu item.
     *
     * <p>NOTE: the tracker will use the english version of the label.
     *
     * @param labelResId The resource Id of the label for the menu item.
     */
    void sendMenuClicked(int labelResId);

    /**
     * Sends tracking information for showing the Electronic Program Guide (EPG).
     */
    void sendShowEpg();

    /**
     * Sends tracking information for clicking an Electronic Program Guide (EPG) item.
     */
    void sendEpgItemClicked();

    /**
     * Sends tracking for hiding the Electronic Program Guide (EPG).
     *
     * @param durationMs The duration the EPG was shown in milliseconds.
     */
    void sendHideEpg(long durationMs);

    /**
     * Sends tracking information for showing the channel switch view.
     */
    void sendShowChannelSwitch();

    /**
     * Sends tracking for hiding the channel switch view.
     *
     * @param durationMs The duration the channel switch view was shown in milliseconds.
     */
    void sendHideChannelSwitch(long durationMs);

    /**
     * Sends tracking for each channel number or delimiter pressed.
     */
    void sendChannelNumberInput();

    /**
     * Sends tracking for navigating during channel number input.
     *
     * <p>This is sent once per channel input viewing.
     */
    void sendChannelInputNavigated();

    /**
     * Sends tracking for channel clicked.
     */
    void sendChannelNumberItemClicked();

    /**
     * Sends tracking for channel chosen (tuned) because the channel switch view timed out.
     */
    void sendChannelNumberItemChosenByTimeout();

    /**
     * Sends tracking for the reason video is unavailable on a channel.
     */
    void sendChannelVideoUnavailable(Channel channel, int reason);

    /**
     * Sends HDMI AC3 passthrough capabilities.
     *
     * @param isSupported {@code true} if the feature is supported; otherwise {@code false}.
     */
    void sendAc3PassthroughCapabilities(boolean isSupported);

    /**
     * Sends tracking for input a connection failure.
     * <p><strong>WARNING</strong> callers must ensure no PII is included in the inputId.
     *
     * @param inputId the input the failure happened on
     */
    void sendInputConnectionFailure(String inputId);

    /**
     * Sends tracking for input disconnected.
     * <p><strong>WARNING</strong> callers must ensure no PII is included in the inputId.
     *
     * @param inputId the input the failure happened on
     */
    void sendInputDisconnected(String inputId);

    /**
     * Sends tracking information for showing the input selection view.
     */
    void sendShowInputSelection();

    /**
     * Sends tracking for hiding the input selection view.
     *
     * @param durationMs The duration the input selection view was shown in milliseconds.
     */
    void sendHideInputSelection(long durationMs);

    /**
     * Sends tracking for input selected by the selection view.
     *
     * <p><strong>WARNING</strong> callers must ensure no PII is included in the label.
     *
     * @param inputLabel the label of the TV input selected
     */
    void sendInputSelected(String inputLabel);

    /**
     * Sends tracking information for showing a side panel.
     *
     * @param trackerLabel the label of the side panel.
     */
    void sendShowSidePanel(HasTrackerLabel trackerLabel);

    /**
     * Sends tracking for hiding a side panel.
     *
     * @param trackerLabel The label of the side panel
     * @param durationMs The duration the side panel was shown in milliseconds.
     */
    void sendHideSidePanel(HasTrackerLabel trackerLabel, long durationMs);

    /**
     * Sends time shift action (pause, ff, etc).
     *
     * @param actionId The {@link com.android.tv.TimeShiftManager.TimeShiftActionId}
     */
    void sendTimeShiftAction(@TimeShiftManager.TimeShiftActionId int actionId);
}
