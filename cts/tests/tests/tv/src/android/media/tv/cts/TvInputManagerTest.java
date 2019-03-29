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

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.tv.TvContentRating;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvInputService;
import android.os.Handler;
import android.test.ActivityInstrumentationTestCase2;

import com.android.compatibility.common.util.PollingCheck;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.xmlpull.v1.XmlPullParserException;

/**
 * Test for {@link android.media.tv.TvInputManager}.
 */
public class TvInputManagerTest extends ActivityInstrumentationTestCase2<TvViewStubActivity> {
    /** The maximum time to wait for an operation. */
    private static final long TIME_OUT_MS = 15000L;

    private static final String[] VALID_TV_INPUT_SERVICES = {
        StubTunerTvInputService.class.getName()
    };
    private static final String[] INVALID_TV_INPUT_SERVICES = {
        NoMetadataTvInputService.class.getName(), NoPermissionTvInputService.class.getName()
    };
    private static final TvContentRating DUMMY_RATING = TvContentRating.createRating(
            "com.android.tv", "US_TV", "US_TV_PG", "US_TV_D", "US_TV_L");

    private String mStubId;
    private TvInputManager mManager;
    private LoggingCallback mCallback = new LoggingCallback();

    private static TvInputInfo getInfoForClassName(List<TvInputInfo> list, String name) {
        for (TvInputInfo info : list) {
            if (info.getServiceInfo().name.equals(name)) {
                return info;
            }
        }
        return null;
    }

    public TvInputManagerTest() {
        super(TvViewStubActivity.class);
    }

    @Override
    public void setUp() throws Exception {
        if (!Utils.hasTvInputFramework(getActivity())) {
            return;
        }
        mManager = (TvInputManager) getActivity().getSystemService(Context.TV_INPUT_SERVICE);
        mStubId = getInfoForClassName(
                mManager.getTvInputList(), StubTvInputService2.class.getName()).getId();
    }

    public void testGetInputState() throws Exception {
        if (!Utils.hasTvInputFramework(getActivity())) {
            return;
        }
        assertEquals(mManager.getInputState(mStubId), TvInputManager.INPUT_STATE_CONNECTED);
    }

    public void testGetTvInputInfo() throws Exception {
        if (!Utils.hasTvInputFramework(getActivity())) {
            return;
        }
        TvInputInfo expected = mManager.getTvInputInfo(mStubId);
        TvInputInfo actual = getInfoForClassName(mManager.getTvInputList(),
                StubTvInputService2.class.getName());
        assertTrue("expected=" + expected + " actual=" + actual,
                TvInputInfoTest.compareTvInputInfos(getActivity(), expected, actual));
    }

    public void testGetTvInputList() throws Exception {
        if (!Utils.hasTvInputFramework(getActivity())) {
            return;
        }
        List<TvInputInfo> list = mManager.getTvInputList();
        for (String name : VALID_TV_INPUT_SERVICES) {
            assertNotNull("getTvInputList() doesn't contain valid input: " + name,
                    getInfoForClassName(list, name));
        }
        for (String name : INVALID_TV_INPUT_SERVICES) {
            assertNull("getTvInputList() contains invalind input: " + name,
                    getInfoForClassName(list, name));
        }
    }

    public void testIsParentalControlsEnabled() {
        if (!Utils.hasTvInputFramework(getActivity())) {
            return;
        }
        try {
            mManager.isParentalControlsEnabled();
        } catch (Exception e) {
            fail();
        }
    }

    public void testIsRatingBlocked() {
        if (!Utils.hasTvInputFramework(getActivity())) {
            return;
        }
        try {
            mManager.isRatingBlocked(DUMMY_RATING);
        } catch (Exception e) {
            fail();
        }
    }

    public void testRegisterUnregisterCallback() {
        if (!Utils.hasTvInputFramework(getActivity())) {
            return;
        }
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    mManager.registerCallback(mCallback, new Handler());
                    mManager.unregisterCallback(mCallback);
                } catch (Exception e) {
                    fail();
                }
            }
        });
        getInstrumentation().waitForIdleSync();
    }

    public void testInputAddedAndRemoved() {
        if (!Utils.hasTvInputFramework(getActivity())) {
            return;
        }
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mManager.registerCallback(mCallback, new Handler());
            }
        });
        getInstrumentation().waitForIdleSync();

        // Test if onInputRemoved() is called.
        mCallback.resetLogs();
        PackageManager pm = getActivity().getPackageManager();
        ComponentName component = new ComponentName(getActivity(), StubTvInputService2.class);
        assertTrue(PackageManager.COMPONENT_ENABLED_STATE_DISABLED != pm.getComponentEnabledSetting(
                component));
        pm.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return mCallback.isInputRemoved(mStubId);
            }
        }.run();

        // Test if onInputAdded() is called.
        mCallback.resetLogs();
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, pm.getComponentEnabledSetting(
                component));
        pm.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return mCallback.isInputAdded(mStubId);
            }
        }.run();

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mManager.unregisterCallback(mCallback);
            }
        });
        getInstrumentation().waitForIdleSync();
    }

    public void testTvInputInfoUpdated() throws IOException, XmlPullParserException {
        if (!Utils.hasTvInputFramework(getActivity())) {
            return;
        }
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mManager.registerCallback(mCallback, new Handler());
            }
        });
        getInstrumentation().waitForIdleSync();

        mCallback.resetLogs();
        TvInputInfo defaultInfo = new TvInputInfo.Builder(getActivity(),
                new ComponentName(getActivity(), StubTunerTvInputService.class)).build();
        TvInputInfo updatedInfo = new TvInputInfo.Builder(getActivity(),
                new ComponentName(getActivity(), StubTunerTvInputService.class))
                        .setTunerCount(10).setCanRecord(true).build();

        mManager.updateTvInputInfo(updatedInfo);
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                TvInputInfo info = mCallback.getLastUpdatedTvInputInfo();
                return info !=  null && info.getTunerCount() == 10 && info.canRecord();
            }
        }.run();

        mManager.updateTvInputInfo(defaultInfo);
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                TvInputInfo info = mCallback.getLastUpdatedTvInputInfo();
                return info !=  null && info.getTunerCount() == 1 && !info.canRecord();
            }
        }.run();

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mManager.unregisterCallback(mCallback);
            }
        });
        getInstrumentation().waitForIdleSync();
    }

    private static class LoggingCallback extends TvInputManager.TvInputCallback {
        private final List<String> mAddedInputs = new ArrayList<>();
        private final List<String> mRemovedInputs = new ArrayList<>();
        private TvInputInfo mLastUpdatedTvInputInfo;

        @Override
        public synchronized void onInputAdded(String inputId) {
            mAddedInputs.add(inputId);
        }

        @Override
        public synchronized void onInputRemoved(String inputId) {
            mRemovedInputs.add(inputId);
        }

        @Override
        public synchronized void onTvInputInfoUpdated(TvInputInfo info) {
            mLastUpdatedTvInputInfo = info;
        }

        public synchronized void resetLogs() {
            mAddedInputs.clear();
            mRemovedInputs.clear();
            mLastUpdatedTvInputInfo = null;
        }

        public synchronized boolean isInputAdded(String inputId) {
            return mRemovedInputs.isEmpty() && mAddedInputs.size() == 1 && mAddedInputs.contains(
                    inputId);
        }

        public synchronized boolean isInputRemoved(String inputId) {
            return mAddedInputs.isEmpty() && mRemovedInputs.size() == 1 && mRemovedInputs.contains(
                    inputId);
        }

        public synchronized TvInputInfo getLastUpdatedTvInputInfo() {
            return mLastUpdatedTvInputInfo;
        }
    }

    public static class StubTvInputService2 extends StubTvInputService {
        @Override
        public Session onCreateSession(String inputId) {
            return null;
        }
    }
}
