/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.media.tv.cts;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvInputService;
import android.media.tv.TvView;
import android.media.tv.cts.HardwareSessionTest.HardwareProxyTvInputService.CountingSession;
import android.net.Uri;
import android.test.ActivityInstrumentationTestCase2;

import android.tv.cts.R;

import com.android.compatibility.common.util.PollingCheck;

import java.util.ArrayList;
import java.util.List;

/**
 * Test {@link android.media.tv.TvInputService.HardwareSession}.
 */
public class HardwareSessionTest extends ActivityInstrumentationTestCase2<TvViewStubActivity> {
    /** The maximum time to wait for an operation. */
    private static final long TIME_OUT = 15000L;

    private TvView mTvView;
    private Activity mActivity;
    private Instrumentation mInstrumentation;
    private TvInputManager mManager;
    private TvInputInfo mStubInfo;
    private final List<TvInputInfo> mPassthroughInputList = new ArrayList<>();

    public HardwareSessionTest() {
        super(TvViewStubActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (!Utils.hasTvInputFramework(getActivity())) {
            return;
        }
        mActivity = getActivity();
        mInstrumentation = getInstrumentation();
        mTvView = (TvView) mActivity.findViewById(R.id.tvview);
        mManager = (TvInputManager) mActivity.getSystemService(Context.TV_INPUT_SERVICE);
        for (TvInputInfo info : mManager.getTvInputList()) {
            if (info.getServiceInfo().name.equals(HardwareProxyTvInputService.class.getName())) {
                mStubInfo = info;
            }
            if (info.isPassthroughInput()) {
                mPassthroughInputList.add(info);
            }
        }
        assertNotNull(mStubInfo);
    }

    public void testHardwareProxyTvInputService() throws Throwable {
        if (!Utils.hasTvInputFramework(getActivity())) {
            return;
        }
        for (final TvInputInfo info : mPassthroughInputList) {
            verifyCommandTuneAndHardwareVideoAvailability(info);
        }
    }

    public void verifyCommandTuneAndHardwareVideoAvailability(TvInputInfo passthroughInfo) throws
            Throwable {
        HardwareProxyTvInputService.sHardwareInputId = passthroughInfo.getId();
        Uri fakeChannelUri = TvContract.buildChannelUri(0);
        mTvView.tune(mStubInfo.getId(), fakeChannelUri);
        mInstrumentation.waitForIdleSync();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                CountingSession session = HardwareProxyTvInputService.sSession;
                return session != null && session.mTuneCount > 0
                        && (session.mHardwareVideoAvailableCount > 0
                                || session.mHardwareVideoUnavailableCount > 0);
            }
        }.run();
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                mTvView.reset();
            }
        });
        mInstrumentation.waitForIdleSync();
        HardwareProxyTvInputService.sSession = null;
    }

    public static class HardwareProxyTvInputService extends TvInputService {
        static String sHardwareInputId;
        static CountingSession sSession;

        @Override
        public Session onCreateSession(String inputId) {
            sSession = new CountingSession(this);
            return sSession;
        }

        public static class CountingSession extends HardwareSession {
            public volatile int mTuneCount;
            public volatile int mHardwareVideoAvailableCount;
            public volatile int mHardwareVideoUnavailableCount;

            CountingSession(Context context) {
                super(context);
            }

            @Override
            public void onRelease() {
            }

            @Override
            public void onSetCaptionEnabled(boolean enabled) {
            }

            @Override
            public String getHardwareInputId() {
                return sHardwareInputId;
            }

            @Override
            public void onSetStreamVolume(float volume) {
            }

            @Override
            public boolean onTune(Uri channelUri) {
                mTuneCount++;
                return true;
            }

            @Override
            public void onHardwareVideoAvailable() {
                mHardwareVideoAvailableCount++;
            }

            @Override
            public void onHardwareVideoUnavailable(int reason) {
                mHardwareVideoUnavailableCount++;
            }
        }
    }
}
