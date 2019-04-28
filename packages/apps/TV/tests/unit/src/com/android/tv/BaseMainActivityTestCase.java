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
package com.android.tv;

import static android.support.test.InstrumentationRegistry.getInstrumentation;

import android.content.Context;
import android.os.SystemClock;
import android.support.test.rule.ActivityTestRule;
import android.text.TextUtils;

import com.android.tv.data.Channel;
import com.android.tv.data.ChannelDataManager;
import com.android.tv.testing.ChannelInfo;
import com.android.tv.testing.testinput.ChannelStateData;
import com.android.tv.testing.testinput.TestInputControlConnection;
import com.android.tv.testing.testinput.TestInputControlUtils;
import com.android.tv.testing.testinput.TvTestInputConstants;

import org.junit.Before;
import org.junit.Rule;

import java.util.List;

/**
 * Base TestCase for tests that need a {@link MainActivity}.
 */
public abstract class BaseMainActivityTestCase {
    private static final String TAG = "BaseMainActivityTest";
    private static final int CHANNEL_LOADING_CHECK_INTERVAL_MS = 10;

    @Rule
    public ActivityTestRule<MainActivity> mActivityTestRule =
            new ActivityTestRule<>(MainActivity.class);

    protected final TestInputControlConnection mConnection = new TestInputControlConnection();

    protected MainActivity mActivity;

    @Before
    public void setUp() {
        mActivity = mActivityTestRule.getActivity();
        // TODO: ensure the SampleInputs are setup.
        getInstrumentation().getTargetContext()
                .bindService(TestInputControlUtils.createIntent(), mConnection,
                        Context.BIND_AUTO_CREATE);
    }

    @Before
    public void tearDown() {
        if (mConnection.isBound()) {
            getInstrumentation().getTargetContext().unbindService(mConnection);
        }
    }

    /**
     * Tune to {@code channel}.
     *
     * @param channel the channel to tune to.
     */
    protected void tuneToChannel(final Channel channel) {
        // Run on UI thread so views can be modified
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mActivity.tuneToChannel(channel);
            }
        });
    }

    /**
     * Sleep until  @{@link ChannelDataManager#isDbLoadFinished()} is true.
     */
    protected void waitUntilChannelLoadingFinish() {
        ChannelDataManager channelDataManager = mActivity.getChannelDataManager();
        while (!channelDataManager.isDbLoadFinished()) {
            getInstrumentation().waitForIdleSync();
            SystemClock.sleep(CHANNEL_LOADING_CHECK_INTERVAL_MS);
        }
    }

    /**
     * Tune to the channel with {@code name}.
     *
     * @param name the name of the channel to find.
     */
    protected void tuneToChannel(String name) {
        Channel c = findChannelWithName(name);
        tuneToChannel(c);
    }

    /**
     * Tune to channel.
     */
    protected void tuneToChannel(ChannelInfo channel) {
        tuneToChannel(channel.name);
    }

    /**
     * Update the channel state to {@code data} then tune to that channel.
     *
     * @param data    the state to update the channel with.
     * @param channel the channel to tune to
     */
    protected void updateThenTune(ChannelStateData data, ChannelInfo channel) {
        if (channel.equals(TvTestInputConstants.CH_1_DEFAULT_DONT_MODIFY)) {
            throw new IllegalArgumentException(
                    "By convention " + TvTestInputConstants.CH_1_DEFAULT_DONT_MODIFY.name
                            + " should not be modified.");
        }
        mConnection.updateChannelState(channel, data);
        tuneToChannel(channel);
    }

    private Channel findChannelWithName(String displayName) {
        waitUntilChannelLoadingFinish();
        Channel channel = null;
        List <Channel> channelList = mActivity.getChannelDataManager().getChannelList();
        for (Channel c : channelList) {
            if (TextUtils.equals(c.getDisplayName(), displayName)) {
                channel = c;
                break;
            }
        }
        if (channel == null) {
            throw new AssertionError("'" + displayName + "' channel not found");
        }
        return channel;
    }
}
