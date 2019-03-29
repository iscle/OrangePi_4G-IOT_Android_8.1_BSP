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
import android.database.Cursor;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvTrackInfo;
import android.media.tv.TvView;
import android.media.tv.TvView.TvInputCallback;
import android.net.Uri;
import android.os.Bundle;
import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;
import android.util.ArrayMap;
import android.util.SparseIntArray;
import android.view.InputEvent;
import android.view.KeyEvent;

import android.tv.cts.R;

import com.android.compatibility.common.util.PollingCheck;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Test {@link android.media.tv.TvView}.
 */
public class TvViewTest extends ActivityInstrumentationTestCase2<TvViewStubActivity> {
    /** The maximum time to wait for an operation. */
    private static final long TIME_OUT_MS = 15000L;

    private TvView mTvView;
    private Activity mActivity;
    private Instrumentation mInstrumentation;
    private TvInputManager mManager;
    private TvInputInfo mStubInfo;
    private TvInputInfo mFaultyStubInfo;
    private final MockCallback mCallback = new MockCallback();

    private static class MockCallback extends TvInputCallback {
        private final Map<String, Boolean> mVideoAvailableMap = new ArrayMap<>();
        private final Map<String, SparseIntArray> mSelectedTrackGenerationMap = new ArrayMap<>();
        private final Map<String, Integer> mTracksGenerationMap = new ArrayMap<>();
        private final Object mLock = new Object();
        private volatile int mConnectionFailedCount;
        private volatile int mDisconnectedCount;

        public boolean isVideoAvailable(String inputId) {
            synchronized (mLock) {
                Boolean available = mVideoAvailableMap.get(inputId);
                return available == null ? false : available.booleanValue();
            }
        }

        public int getSelectedTrackGeneration(String inputId, int type) {
            synchronized (mLock) {
                SparseIntArray selectedTrackGenerationMap =
                        mSelectedTrackGenerationMap.get(inputId);
                if (selectedTrackGenerationMap == null) {
                    return 0;
                }
                return selectedTrackGenerationMap.get(type, 0);
            }
        }

        public void resetCount() {
            mConnectionFailedCount = 0;
            mDisconnectedCount = 0;
        }

        public int getConnectionFailedCount() {
            return mConnectionFailedCount;
        }

        public int getDisconnectedCount() {
            return mDisconnectedCount;
        }

        @Override
        public void onConnectionFailed(String inputId) {
            mConnectionFailedCount++;
        }

        @Override
        public void onDisconnected(String inputId) {
            mDisconnectedCount++;
        }

        @Override
        public void onVideoAvailable(String inputId) {
            synchronized (mLock) {
                mVideoAvailableMap.put(inputId, true);
            }
        }

        @Override
        public void onVideoUnavailable(String inputId, int reason) {
            synchronized (mLock) {
                mVideoAvailableMap.put(inputId, false);
            }
        }

        @Override
        public void onTrackSelected(String inputId, int type, String trackId) {
            synchronized (mLock) {
                SparseIntArray selectedTrackGenerationMap =
                        mSelectedTrackGenerationMap.get(inputId);
                if (selectedTrackGenerationMap == null) {
                    selectedTrackGenerationMap = new SparseIntArray();
                    mSelectedTrackGenerationMap.put(inputId, selectedTrackGenerationMap);
                }
                int currentGeneration = selectedTrackGenerationMap.get(type, 0);
                selectedTrackGenerationMap.put(type, currentGeneration + 1);
            }
        }

        @Override
        public void onTracksChanged(String inputId, List<TvTrackInfo> trackList) {
            synchronized (mLock) {
                Integer tracksGeneration = mTracksGenerationMap.get(inputId);
                mTracksGenerationMap.put(inputId,
                        tracksGeneration == null ? 1 : (tracksGeneration + 1));
            }
        }
    }

    /**
     * Instantiates a new TV view test.
     */
    public TvViewTest() {
        super(TvViewStubActivity.class);
    }

    /**
     * Find the TV view specified by id.
     *
     * @param id the id
     * @return the TV view
     */
    private TvView findTvViewById(int id) {
        return (TvView) mActivity.findViewById(id);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mActivity = getActivity();
        if (!Utils.hasTvInputFramework(mActivity)) {
            return;
        }
        mInstrumentation = getInstrumentation();
        mTvView = findTvViewById(R.id.tvview);
        mManager = (TvInputManager) mActivity.getSystemService(Context.TV_INPUT_SERVICE);
        for (TvInputInfo info : mManager.getTvInputList()) {
            if (info.getServiceInfo().name.equals(StubTunerTvInputService.class.getName())) {
                mStubInfo = info;
            }
            if (info.getServiceInfo().name.equals(FaultyTvInputService.class.getName())) {
                mFaultyStubInfo = info;
            }
            if (mStubInfo != null && mFaultyStubInfo != null) {
                break;
            }
        }
        assertNotNull(mStubInfo);
        mTvView.setCallback(mCallback);
    }

    @Override
    protected void tearDown() throws Exception {
        if (!Utils.hasTvInputFramework(getActivity())) {
            super.tearDown();
            return;
        }
        StubTunerTvInputService.deleteChannels(mActivity.getContentResolver(), mStubInfo);
        StubTunerTvInputService.clearTracks();
        try {
            runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mTvView.reset();
                }
            });
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
        mInstrumentation.waitForIdleSync();
        super.tearDown();
    }

    @UiThreadTest
    public void testConstructor() throws Exception {
        if (!Utils.hasTvInputFramework(getActivity())) {
            return;
        }
        new TvView(mActivity);

        new TvView(mActivity, null);

        new TvView(mActivity, null, 0);
    }

    private void tryTuneAllChannels(Bundle params, Runnable runOnEachChannel) throws Throwable {
        StubTunerTvInputService.insertChannels(mActivity.getContentResolver(), mStubInfo);

        Uri uri = TvContract.buildChannelsUriForInput(mStubInfo.getId());
        String[] projection = { TvContract.Channels._ID };
        try (Cursor cursor = mActivity.getContentResolver().query(
                uri, projection, null, null, null)) {
            while (cursor != null && cursor.moveToNext()) {
                long channelId = cursor.getLong(0);
                Uri channelUri = TvContract.buildChannelUri(channelId);
                if (params != null) {
                    mTvView.tune(mStubInfo.getId(), channelUri, params);
                } else {
                    mTvView.tune(mStubInfo.getId(), channelUri);
                }
                mInstrumentation.waitForIdleSync();
                new PollingCheck(TIME_OUT_MS) {
                    @Override
                    protected boolean check() {
                        return mCallback.isVideoAvailable(mStubInfo.getId());
                    }
                }.run();

                if (runOnEachChannel != null) {
                    runOnEachChannel.run();
                }
            }
        }
    }

    public void testSimpleTune() throws Throwable {
        if (!Utils.hasTvInputFramework(getActivity())) {
            return;
        }
        tryTuneAllChannels(null, null);
    }

    public void testSimpleTuneWithBundle() throws Throwable {
        if (!Utils.hasTvInputFramework(getActivity())) {
            return;
        }
        Bundle params = new Bundle();
        params.putString("android.media.tv.cts.TvViewTest.inputId", mStubInfo.getId());
        tryTuneAllChannels(params, null);
    }

    private void selectTrackAndVerify(final int type, final TvTrackInfo track,
            List<TvTrackInfo> tracks) {
        String selectedTrackId = mTvView.getSelectedTrack(type);
        final int previousGeneration = mCallback.getSelectedTrackGeneration(
                mStubInfo.getId(), type);
        mTvView.selectTrack(type, track == null ? null : track.getId());

        if ((track == null && selectedTrackId != null)
                || (track != null && !track.getId().equals(selectedTrackId))) {
            // Check generation change only if we're actually changing track.
            new PollingCheck(TIME_OUT_MS) {
                @Override
                protected boolean check() {
                    return mCallback.getSelectedTrackGeneration(
                            mStubInfo.getId(), type) > previousGeneration;
                }
            }.run();
        }

        selectedTrackId = mTvView.getSelectedTrack(type);
        assertEquals(selectedTrackId, track == null ? null : track.getId());
        if (selectedTrackId != null) {
            TvTrackInfo selectedTrack = null;
            for (TvTrackInfo item : tracks) {
                if (item.getId().equals(selectedTrackId)) {
                    selectedTrack = item;
                    break;
                }
            }
            assertNotNull(selectedTrack);
            assertEquals(track.getType(), selectedTrack.getType());
            assertEquals(track.getExtra(), selectedTrack.getExtra());
            switch (track.getType()) {
                case TvTrackInfo.TYPE_VIDEO:
                    assertEquals(track.getVideoHeight(), selectedTrack.getVideoHeight());
                    assertEquals(track.getVideoWidth(), selectedTrack.getVideoWidth());
                    assertEquals(track.getVideoPixelAspectRatio(),
                            selectedTrack.getVideoPixelAspectRatio(), 0.001f);
                    break;
                case TvTrackInfo.TYPE_AUDIO:
                    assertEquals(track.getAudioChannelCount(),
                            selectedTrack.getAudioChannelCount());
                    assertEquals(track.getAudioSampleRate(), selectedTrack.getAudioSampleRate());
                    assertEquals(track.getLanguage(), selectedTrack.getLanguage());
                    break;
                case TvTrackInfo.TYPE_SUBTITLE:
                    assertEquals(track.getLanguage(), selectedTrack.getLanguage());
                    assertEquals(track.getDescription(), selectedTrack.getDescription());
                    break;
                default:
                    fail("Unrecognized type: " + track.getType());
            }
        }
    }

    public void testTrackChange() throws Throwable {
        if (!Utils.hasTvInputFramework(getActivity())) {
            return;
        }
        TvTrackInfo videoTrack1 = new TvTrackInfo.Builder(TvTrackInfo.TYPE_VIDEO, "video-HD")
                .setVideoHeight(1920).setVideoWidth(1080).build();
        TvTrackInfo videoTrack2 = new TvTrackInfo.Builder(TvTrackInfo.TYPE_VIDEO, "video-SD")
                .setVideoHeight(640).setVideoWidth(360).setVideoPixelAspectRatio(1.09f).build();
        TvTrackInfo audioTrack1 =
                new TvTrackInfo.Builder(TvTrackInfo.TYPE_AUDIO, "audio-stereo-eng")
                .setLanguage("eng").setAudioChannelCount(2).setAudioSampleRate(48000).build();
        TvTrackInfo audioTrack2 = new TvTrackInfo.Builder(TvTrackInfo.TYPE_AUDIO, "audio-mono-esp")
                .setLanguage("esp").setAudioChannelCount(1).setAudioSampleRate(48000).build();
        TvTrackInfo subtitleTrack1 =
                new TvTrackInfo.Builder(TvTrackInfo.TYPE_SUBTITLE, "subtitle-eng")
                .setLanguage("eng").build();
        TvTrackInfo subtitleTrack2 =
                new TvTrackInfo.Builder(TvTrackInfo.TYPE_SUBTITLE, "subtitle-esp")
                .setLanguage("esp").build();
        TvTrackInfo subtitleTrack3 =
                new TvTrackInfo.Builder(TvTrackInfo.TYPE_SUBTITLE, "subtitle-eng2")
                .setLanguage("eng").setDescription("audio commentary").build();

        StubTunerTvInputService.injectTrack(videoTrack1, videoTrack2, audioTrack1, audioTrack2,
                subtitleTrack1, subtitleTrack2);

        final List<TvTrackInfo> tracks = new ArrayList<TvTrackInfo>();
        Collections.addAll(tracks, videoTrack1, videoTrack2, audioTrack1, audioTrack2,
                subtitleTrack1, subtitleTrack2, subtitleTrack3);
        tryTuneAllChannels(null, new Runnable() {
            @Override
            public void run() {
                new PollingCheck(TIME_OUT_MS) {
                    @Override
                    protected boolean check() {
                        return mTvView.getTracks(TvTrackInfo.TYPE_AUDIO) != null;
                    }
                }.run();
                final int[] types = { TvTrackInfo.TYPE_AUDIO, TvTrackInfo.TYPE_VIDEO,
                    TvTrackInfo.TYPE_SUBTITLE };
                for (int type : types) {
                    for (TvTrackInfo track : mTvView.getTracks(type)) {
                        selectTrackAndVerify(type, track, tracks);
                    }
                    selectTrackAndVerify(TvTrackInfo.TYPE_SUBTITLE, null, tracks);
                }
            }
        });
    }

    private void verifyKeyEvent(final KeyEvent keyEvent, final InputEvent[] unhandledEvent) {
        unhandledEvent[0] = null;
        mInstrumentation.sendKeySync(keyEvent);
        mInstrumentation.waitForIdleSync();
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return unhandledEvent[0] != null;
            }
        }.run();
        assertTrue(unhandledEvent[0] instanceof KeyEvent);
        KeyEvent unhandled = (KeyEvent) unhandledEvent[0];
        assertEquals(unhandled.getAction(), keyEvent.getAction());
        assertEquals(unhandled.getKeyCode(), keyEvent.getKeyCode());
    }

    public void testOnUnhandledInputEventListener() throws Throwable {
        if (!Utils.hasTvInputFramework(getActivity())) {
            return;
        }
        final InputEvent[] unhandledEvent = { null };
        mTvView.setOnUnhandledInputEventListener(new TvView.OnUnhandledInputEventListener() {
            @Override
            public boolean onUnhandledInputEvent(InputEvent event) {
                unhandledEvent[0] = event;
                return true;
            }
        });

        StubTunerTvInputService.insertChannels(mActivity.getContentResolver(), mStubInfo);

        Uri uri = TvContract.buildChannelsUriForInput(mStubInfo.getId());
        String[] projection = { TvContract.Channels._ID };
        try (Cursor cursor = mActivity.getContentResolver().query(
                uri, projection, null, null, null)) {
            assertNotNull(cursor);
            assertTrue(cursor.moveToNext());
            long channelId = cursor.getLong(0);
            Uri channelUri = TvContract.buildChannelUri(channelId);
            mTvView.tune(mStubInfo.getId(), channelUri);
            mInstrumentation.waitForIdleSync();
            new PollingCheck(TIME_OUT_MS) {
                @Override
                protected boolean check() {
                    return mCallback.isVideoAvailable(mStubInfo.getId());
                }
            }.run();
        }
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                mTvView.setFocusable(true);
                mTvView.requestFocus();
            }
        });
        mInstrumentation.waitForIdleSync();
        assertTrue(mTvView.isFocused());

        verifyKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_GUIDE), unhandledEvent);
        verifyKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_GUIDE), unhandledEvent);
    }

    public void testConnectionFailed() throws Throwable {
        if (!Utils.hasTvInputFramework(getActivity())) {
            return;
        }
        mCallback.resetCount();
        mTvView.tune("invalid_input_id", TvContract.Channels.CONTENT_URI);
        mInstrumentation.waitForIdleSync();
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return mCallback.getConnectionFailedCount() > 0;
            }
        }.run();
    }

    public void testDisconnected() throws Throwable {
        if (!Utils.hasTvInputFramework(getActivity())) {
            return;
        }
        mCallback.resetCount();
        Uri fakeChannelUri = TvContract.buildChannelUri(0);
        mTvView.tune(mFaultyStubInfo.getId(), fakeChannelUri);
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return mCallback.getDisconnectedCount() > 0;
            }
        }.run();
    }

    public void testSetZOrderMediaOverlay() throws Exception {
        if (!Utils.hasTvInputFramework(getActivity())) {
            return;
        }
        // Verifying the z-order from app is not possible. Here we just check if calling APIs does
        // not lead to any break.
        mTvView.setZOrderMediaOverlay(true);
        mInstrumentation.waitForIdleSync();
        mTvView.setZOrderMediaOverlay(false);
        mInstrumentation.waitForIdleSync();
    }

    public void testSetZOrderOnTop() throws Exception {
        if (!Utils.hasTvInputFramework(getActivity())) {
            return;
        }
        // Verifying the z-order from app is not possible. Here we just check if calling APIs does
        // not lead to any break.
        mTvView.setZOrderOnTop(true);
        mInstrumentation.waitForIdleSync();
        mTvView.setZOrderOnTop(false);
        mInstrumentation.waitForIdleSync();
    }

    @UiThreadTest
    public void testUnhandledInputEvent() throws Exception {
        if (!Utils.hasTvInputFramework(getActivity())) {
            return;
        }
        boolean result = mTvView.dispatchUnhandledInputEvent(null);
        assertFalse(result);
        result = new InputEventHandlingTvView(mActivity).dispatchUnhandledInputEvent(null);
        assertTrue(result);
    }

    private static class InputEventHandlingTvView extends TvView {
        public InputEventHandlingTvView(Context context) {
            super(context);
        }

        @Override
        public boolean onUnhandledInputEvent(InputEvent event) {
            return true;
        }
    }
}
