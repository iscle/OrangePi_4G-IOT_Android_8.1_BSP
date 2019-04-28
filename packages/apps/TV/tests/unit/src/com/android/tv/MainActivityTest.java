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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.support.test.filters.MediumTest;
import android.view.View;
import android.widget.TextView;

import com.android.tv.data.Channel;
import com.android.tv.testing.testinput.TvTestInputConstants;
import com.android.tv.ui.ChannelBannerView;

import org.junit.Test;

import java.util.List;

/**
 * Tests for {@link MainActivity}.
 */
@MediumTest
public class MainActivityTest extends BaseMainActivityTestCase {
    @Test
    public void testInitialConditions() {
        waitUntilChannelLoadingFinish();
        List<Channel> channelList = mActivity.getChannelDataManager().getChannelList();
        assertTrue("Expected at least one channel", channelList.size() > 0);
    }

    @Test
    public void testTuneToChannel() {
        tuneToChannel(TvTestInputConstants.CH_2);
        assertChannelBannerShown(true);
        assertChannelName(TvTestInputConstants.CH_2.name);
    }

    @Test
    public void testShowProgramGuide() {
        tuneToChannel(TvTestInputConstants.CH_2);
        showProgramGuide();
        getInstrumentation().waitForIdleSync();
        assertChannelBannerShown(false);
        assertProgramGuide(true);
    }

    private void showProgramGuide() {
        // Run on UI thread so views can be modified
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mActivity.getOverlayManager().showProgramGuide();
            }
        });
    }

    private void assertChannelName(String displayName) {
        TextView channelNameView = (TextView) mActivity.findViewById(R.id.channel_name);
        assertEquals("Channel Name", displayName, channelNameView.getText());
    }

    private void assertProgramGuide(boolean isShown) {
        assertViewIsShown("Program Guide", R.id.program_guide, isShown);
    }

    private ChannelBannerView assertChannelBannerShown(boolean isShown) {
        View v = assertExpectedBannerSceneClassShown(ChannelBannerView.class, isShown);
        return (ChannelBannerView) v;
    }

    private View assertExpectedBannerSceneClassShown(Class<ChannelBannerView> expectedClass,
            boolean expectedShown) {
        View v = assertViewIsShown(expectedClass.getSimpleName(), R.id.scene_transition_common,
                expectedShown);
        if (v != null) {
            assertEquals(expectedClass, v.getClass());
        }
        return v;
    }

    private View assertViewIsShown(String viewName, int viewId, boolean expected) {
        View view = mActivity.findViewById(viewId);
        if (view == null) {
            if (expected) {
                throw new AssertionError("View " + viewName + " not found");
            } else {
                return null;
            }
        }
        assertEquals(viewName + " shown", expected, view.isShown());
        return view;
    }
}
