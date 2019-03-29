/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.media.cts;

import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Parcel;
import android.util.Log;
import android.media.AudioPlaybackConfiguration;

import com.android.compatibility.common.util.CtsAndroidTestCase;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class AudioPlaybackConfigurationTest extends CtsAndroidTestCase {
    private final static String TAG = "AudioPlaybackConfigurationTest";

    private final static int TEST_TIMING_TOLERANCE_MS = 50;
    private final static int TEST_TIMEOUT_SOUNDPOOL_LOAD_MS = 3000;

    // not declared inside test so it can be released in case of failure
    private MediaPlayer mMp;
    private SoundPool mSp;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        if (mMp != null) {
            mMp.stop();
            mMp.release();
            mMp = null;
        }
        if (mSp != null) {
            mSp.release();
            mSp = null;
        }
    }

    private final static int TEST_USAGE = AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_DELAYED;
    private final static int TEST_CONTENT = AudioAttributes.CONTENT_TYPE_SPEECH;

    // test marshalling/unmarshalling of an AudioPlaybackConfiguration instance. Since we can't
    // create an AudioPlaybackConfiguration directly, we first need to play something to get one.
    public void testParcelableWriteToParcel() throws Exception {
        if (!isValidPlatform("testParcelableWriteToParcel")) return;

        // create a player, make it play so we can get an AudioPlaybackConfiguration instance
        AudioManager am = new AudioManager(getContext());
        assertNotNull("Could not create AudioManager", am);
        final AudioAttributes aa = (new AudioAttributes.Builder())
                .setUsage(TEST_USAGE)
                .setContentType(TEST_CONTENT)
                .build();
        mMp = MediaPlayer.create(getContext(), R.raw.sine1khzs40dblong,
                aa, am.generateAudioSessionId());
        mMp.start();
        Thread.sleep(2*TEST_TIMING_TOLERANCE_MS);// waiting for playback to start
        List<AudioPlaybackConfiguration> configs = am.getActivePlaybackConfigurations();
        mMp.stop();
        assertTrue("No playback reported", configs.size() > 0);
        AudioPlaybackConfiguration configToMarshall = null;
        for (AudioPlaybackConfiguration config : configs) {
            if (config.getAudioAttributes().equals(aa)) {
                configToMarshall = config;
                break;
            }
        }

        assertNotNull("Configuration not found during playback", configToMarshall);
        assertEquals(0, configToMarshall.describeContents());

        final Parcel srcParcel = Parcel.obtain();
        final Parcel dstParcel = Parcel.obtain();
        final byte[] mbytes;

        configToMarshall.writeToParcel(srcParcel, 0 /*no public flags for marshalling*/);
        mbytes = srcParcel.marshall();
        dstParcel.unmarshall(mbytes, 0, mbytes.length);
        dstParcel.setDataPosition(0);
        final AudioPlaybackConfiguration restoredConfig =
                AudioPlaybackConfiguration.CREATOR.createFromParcel(dstParcel);

        assertEquals("Marshalled/restored AudioAttributes don't match",
                configToMarshall.getAudioAttributes(), restoredConfig.getAudioAttributes());
    }

    public void testGetterMediaPlayer() throws Exception {
        if (!isValidPlatform("testGetterMediaPlayer")) return;

        AudioManager am = new AudioManager(getContext());
        assertNotNull("Could not create AudioManager", am);

        final AudioAttributes aa = (new AudioAttributes.Builder())
                .setUsage(TEST_USAGE)
                .setContentType(TEST_CONTENT)
                .build();

        List<AudioPlaybackConfiguration> configs = am.getActivePlaybackConfigurations();
        final int nbActivePlayersBeforeStart = configs.size();

        mMp = MediaPlayer.create(getContext(), R.raw.sine1khzs40dblong,
                aa, am.generateAudioSessionId());
        configs = am.getActivePlaybackConfigurations();
        assertEquals("inactive MediaPlayer, number of configs shouldn't have changed",
                nbActivePlayersBeforeStart /*expected*/, configs.size());

        mMp.start();
        Thread.sleep(2*TEST_TIMING_TOLERANCE_MS);// waiting for playback to start
        configs = am.getActivePlaybackConfigurations();
        assertEquals("active MediaPlayer, number of configs should have increased",
                nbActivePlayersBeforeStart + 1 /*expected*/,
                configs.size());
        assertTrue("Active player, attributes not found", hasAttr(configs, aa));

        // verify "privileged" fields aren't available through reflection
        final AudioPlaybackConfiguration config = configs.get(0);
        final Class<?> confClass = config.getClass();
        final Method getClientUidMethod = confClass.getDeclaredMethod("getClientUid");
        final Method getClientPidMethod = confClass.getDeclaredMethod("getClientPid");
        final Method getPlayerTypeMethod = confClass.getDeclaredMethod("getPlayerType");
        try {
            Integer uid = (Integer) getClientUidMethod.invoke(config, null);
            assertEquals("uid isn't protected", -1 /*expected*/, uid.intValue());
            Integer pid = (Integer) getClientPidMethod.invoke(config, null);
            assertEquals("pid isn't protected", -1 /*expected*/, pid.intValue());
            Integer type = (Integer) getPlayerTypeMethod.invoke(config, null);
            assertEquals("player type isn't protected", -1 /*expected*/, type.intValue());
        } catch (Exception e) {
            fail("Exception thrown during reflection on config privileged fields"+ e);
        }
    }

    public void testCallbackMediaPlayer() throws Exception {
        if (!isValidPlatform("testCallbackMediaPlayer")) return;
        doTestCallbackMediaPlayer(false /* no custom Handler for callback */);
    }

    public void testCallbackMediaPlayerHandler() throws Exception {
        if (!isValidPlatform("testCallbackMediaPlayerHandler")) return;
        doTestCallbackMediaPlayer(true /* use custom Handler for callback */);
    }

    private void doTestCallbackMediaPlayer(boolean useHandlerInCallback) throws Exception {
        final Handler h;
        if (useHandlerInCallback) {
            HandlerThread handlerThread = new HandlerThread(TAG);
            handlerThread.start();
            h = new Handler(handlerThread.getLooper());
        } else {
            h = null;
        }

        try {
            AudioManager am = new AudioManager(getContext());
            assertNotNull("Could not create AudioManager", am);

            final AudioAttributes aa = (new AudioAttributes.Builder())
                    .setUsage(TEST_USAGE)
                    .setContentType(TEST_CONTENT)
                    .build();

            mMp = MediaPlayer.create(getContext(), R.raw.sine1khzs40dblong,
                    aa, am.generateAudioSessionId());

            MyAudioPlaybackCallback callback = new MyAudioPlaybackCallback();
            am.registerAudioPlaybackCallback(callback, h /*handler*/);

            // query how many active players before starting the MediaPlayer
            List<AudioPlaybackConfiguration> configs = am.getActivePlaybackConfigurations();
            final int nbActivePlayersBeforeStart = configs.size();

            mMp.start();
            Thread.sleep(TEST_TIMING_TOLERANCE_MS);

            assertEquals("onPlaybackConfigChanged call count not expected",
                    1/*expected*/, callback.getCbInvocationNumber()); //only one start call
            assertEquals("number of active players not expected",
                    // one more player active
                    nbActivePlayersBeforeStart + 1/*expected*/, callback.getNbConfigs());
            assertTrue("Active player, attributes not found", hasAttr(callback.getConfigs(), aa));

            // stopping recording: callback is called with no match
            callback.reset();
            mMp.pause();
            Thread.sleep(TEST_TIMING_TOLERANCE_MS);

            assertEquals("onPlaybackConfigChanged call count not expected after pause",
                    1/*expected*/, callback.getCbInvocationNumber());//only 1 pause call since reset
            assertEquals("number of active players not expected after pause",
                    nbActivePlayersBeforeStart/*expected*/, callback.getNbConfigs());

            // unregister callback and start recording again
            am.unregisterAudioPlaybackCallback(callback);
            Thread.sleep(TEST_TIMING_TOLERANCE_MS);
            callback.reset();
            mMp.start();
            Thread.sleep(TEST_TIMING_TOLERANCE_MS);
            assertEquals("onPlaybackConfigChanged call count not expected after unregister",
                    0/*expected*/, callback.getCbInvocationNumber()); //callback is unregistered

            // just call the callback once directly so it's marked as tested
            final AudioManager.AudioPlaybackCallback apc =
                    (AudioManager.AudioPlaybackCallback) callback;
            apc.onPlaybackConfigChanged(new ArrayList<AudioPlaybackConfiguration>());
        } finally {
            if (h != null) {
                h.getLooper().quit();
            }
        }
    }

    public void testGetterSoundPool() throws Exception {
        if (!isValidPlatform("testSoundPool")) return;

        AudioManager am = new AudioManager(getContext());
        assertNotNull("Could not create AudioManager", am);
        MyAudioPlaybackCallback callback = new MyAudioPlaybackCallback();
        am.registerAudioPlaybackCallback(callback, null /*handler*/);

        // query how many active players before starting the SoundPool
        List<AudioPlaybackConfiguration> configs = am.getActivePlaybackConfigurations();
        int nbActivePlayersBeforeStart = 0;
        for (AudioPlaybackConfiguration apc : configs) {
            if (apc.getPlayerState() == AudioPlaybackConfiguration.PLAYER_STATE_STARTED) {
                nbActivePlayersBeforeStart++;
            }
        }

        final AudioAttributes aa = (new AudioAttributes.Builder())
                .setUsage(TEST_USAGE)
                .setContentType(TEST_CONTENT)
                .build();

        mSp = new SoundPool.Builder()
                .setAudioAttributes(aa)
                .setMaxStreams(1)
                .build();
        final Object loadLock = new Object();
        final SoundPool zepool = mSp;
        // load a sound and play it once load completion is reported
        mSp.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                assertEquals("Receiving load completion for wrong SoundPool", zepool, mSp);
                assertEquals("Load completion error", 0 /*success expected*/, status);
                synchronized (loadLock) {
                    loadLock.notify();
                }
            }
        });
        final int loadId = mSp.load(getContext(), R.raw.sine1320hz5sec, 1/*priority*/);
        synchronized (loadLock) {
            loadLock.wait(TEST_TIMEOUT_SOUNDPOOL_LOAD_MS);
        }
        int res = mSp.play(loadId, 1.0f /*leftVolume*/, 1.0f /*rightVolume*/, 1 /*priority*/,
                0 /*loop*/, 1.0f/*rate*/);
        // FIXME SoundPool activity is not reported yet, but exercise creation/release with
        //       an AudioPlaybackCallback registered
        assertTrue("Error playing sound through SoundPool", res > 0);
        Thread.sleep(TEST_TIMING_TOLERANCE_MS);

        mSp.autoPause();
        Thread.sleep(TEST_TIMING_TOLERANCE_MS);
        // query how many active players after pausing
        configs = am.getActivePlaybackConfigurations();
        int nbActivePlayersAfterPause = 0;
        for (AudioPlaybackConfiguration apc : configs) {
            if (apc.getPlayerState() == AudioPlaybackConfiguration.PLAYER_STATE_STARTED) {
                nbActivePlayersAfterPause++;
            }
        }
        assertEquals("Number of active players changed after pausing SoundPool",
                nbActivePlayersBeforeStart, nbActivePlayersAfterPause);
    }

    private static class MyAudioPlaybackCallback extends AudioManager.AudioPlaybackCallback {
        private int mCalled = 0;
        private int mNbConfigs = 0;
        private List<AudioPlaybackConfiguration> mConfigs;
        private final Object mCbLock = new Object();

        void reset() {
            synchronized (mCbLock) {
                mCalled = 0;
                mNbConfigs = 0;
                mConfigs.clear();
            }
        }

        int getCbInvocationNumber() {
            synchronized (mCbLock) {
                return mCalled;
            }
        }

        int getNbConfigs() {
            synchronized (mCbLock) {
                return mNbConfigs;
            }
        }

        List<AudioPlaybackConfiguration> getConfigs() {
            synchronized (mCbLock) {
                return mConfigs;
            }
        }

        MyAudioPlaybackCallback() {
        }

        @Override
        public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs) {
            synchronized (mCbLock) {
                mCalled++;
                mNbConfigs = configs.size();
                mConfigs = configs;
            }
        }
    }

    private static boolean hasAttr(List<AudioPlaybackConfiguration> configs, AudioAttributes aa) {
        Iterator<AudioPlaybackConfiguration> it = configs.iterator();
        while (it.hasNext()) {
            final AudioPlaybackConfiguration apc = it.next();
            if (apc.getAudioAttributes().getContentType() == aa.getContentType()
                    && apc.getAudioAttributes().getUsage() == aa.getUsage()) {
                return true;
            }
        }
        return false;
    }

    private boolean isValidPlatform(String testName) {
        if (!getContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)) {
            Log.w(TAG,"AUDIO_OUTPUT feature not found. This system might not have a valid "
                    + "audio output HAL, skipping test " + testName);
            return false;
        }
        return true;
    }
}
