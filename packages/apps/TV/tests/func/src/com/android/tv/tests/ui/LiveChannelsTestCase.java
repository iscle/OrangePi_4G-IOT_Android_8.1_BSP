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

package com.android.tv.tests.ui;

import static com.android.tv.testing.uihelper.UiDeviceAsserts.assertWaitForCondition;

import android.content.Context;
import android.content.res.Resources;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.Until;
import android.test.InstrumentationTestCase;

import com.android.tv.testing.ChannelInfo;
import com.android.tv.testing.testinput.ChannelStateData;
import com.android.tv.testing.testinput.TestInputControlConnection;
import com.android.tv.testing.testinput.TestInputControlUtils;
import com.android.tv.testing.uihelper.Constants;
import com.android.tv.testing.uihelper.LiveChannelsUiDeviceHelper;
import com.android.tv.testing.uihelper.MenuHelper;
import com.android.tv.testing.uihelper.SidePanelHelper;
import com.android.tv.testing.uihelper.UiDeviceUtils;

/**
 * Base test case for LiveChannel UI tests.
 */
public abstract class LiveChannelsTestCase extends InstrumentationTestCase {
    protected final TestInputControlConnection mConnection = new TestInputControlConnection();

    protected UiDevice mDevice;
    protected Resources mTargetResources;
    protected MenuHelper mMenuHelper;
    protected SidePanelHelper mSidePanelHelper;
    protected LiveChannelsUiDeviceHelper mLiveChannelsHelper;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Context context = getInstrumentation().getContext();
        context.bindService(TestInputControlUtils.createIntent(), mConnection,
                Context.BIND_AUTO_CREATE);
        mDevice = UiDevice.getInstance(getInstrumentation());
        mTargetResources = getInstrumentation().getTargetContext().getResources();
        mMenuHelper = new MenuHelper(mDevice, mTargetResources);
        mSidePanelHelper = new SidePanelHelper(mDevice, mTargetResources);
        mLiveChannelsHelper = new LiveChannelsUiDeviceHelper(mDevice, mTargetResources, context);
    }

    @Override
    protected void tearDown() throws Exception {
        if (mConnection.isBound()) {
            getInstrumentation().getContext().unbindService(mConnection);
        }

        // TODO: robustly handle left over pops from failed tests.
        // Clear any side panel, menu, ...
        // Scene container should not be checked here because pressing the BACK key in some scenes
        // might launch the home screen.
        if (mDevice.hasObject(Constants.SIDE_PANEL) || mDevice.hasObject(Constants.MENU) || mDevice
                .hasObject(Constants.PROGRAM_GUIDE)) {
            mDevice.pressBack();
        }
        // To destroy the activity to make sure next test case's activity launch check works well.
        mDevice.pressBack();
        super.tearDown();
    }

    /**
     * Send the keys for the channel number of {@code channel} and press the DPAD
     * center.
     *
     * <p>Usually this will tune to the given channel.
     */
    protected void pressKeysForChannel(ChannelInfo channel) {
        UiDeviceUtils.pressKeys(mDevice, channel.number);
        assertWaitForCondition(mDevice, Until.hasObject(Constants.KEYPAD_CHANNEL_SWITCH));
        mDevice.pressDPadCenter();
    }

    /**
     * Update the channel state to {@code data} then tune to that channel.
     *
     * @param data    the state to update the channel with.
     * @param channel the channel to tune to
     */
    protected void updateThenTune(ChannelStateData data, ChannelInfo channel) {
        mConnection.updateChannelState(channel, data);
        pressKeysForChannel(channel);
    }
}
