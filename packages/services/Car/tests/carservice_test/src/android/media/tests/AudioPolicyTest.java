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

package android.media.tests;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioPatch;
import android.media.AudioPort;
import android.media.audiopolicy.AudioMix;
import android.media.audiopolicy.AudioMixingRule;
import android.media.audiopolicy.AudioPolicy;
import android.os.Handler;
import android.os.Looper;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Log;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@MediumTest
public class AudioPolicyTest extends AndroidTestCase {
    private static final String TAG = AudioPolicyTest.class.getSimpleName();
    private static final long WAIT_TIMEOUT_MS = 1000;
    private AudioManager mAudioManager;
    private Handler mHandler;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mHandler = new Handler(Looper.getMainLooper());
        final Semaphore mWaitSemaphore = new Semaphore(0);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
                mWaitSemaphore.release();
            }
        });
        assertTrue(mWaitSemaphore.tryAcquire(WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    public void testAudioPorts() throws Exception {
        AudioPortUpdateListener listener = new AudioPortUpdateListener();
        mAudioManager.registerAudioPortUpdateListener(listener);
        ArrayList<AudioPort> initialPorts = dumpAudioPorts("initial state");
        AudioMix mediaMix = createAudioMix(AudioAttributes.CONTENT_TYPE_UNKNOWN,
                AudioAttributes.CONTENT_TYPE_MUSIC);
        AudioPolicy audioPolicy = new AudioPolicy.Builder(getContext())
                .addMix(mediaMix)
                .setLooper(Looper.getMainLooper())
                .build();
        mAudioManager.registerAudioPolicy(audioPolicy);
        dumpAudioPorts("policy set");
        mAudioManager.unregisterAudioPolicyAsync(audioPolicy);
        ArrayList<AudioPort> afterUnregisterPorts = dumpAudioPorts("policy unset");
        mAudioManager.unregisterAudioPortUpdateListener(listener);
    }

    private ArrayList<AudioPort> dumpAudioPorts(String msg) {
        Log.i(TAG, msg + ", dump audio ports");
        ArrayList<AudioPort> ports = new ArrayList<>();
        assertEquals(AudioManager.SUCCESS, AudioManager.listAudioPorts(ports));
        for (AudioPort port : ports) {
            Log.i(TAG, "port:" + port.toString() + " name:" + port.name());
        }
        return ports;
    }

    private static AudioMix createAudioMix(int contentType, int usage) {
        AudioAttributes.Builder audioAttributesBuilder = new AudioAttributes.Builder();
        audioAttributesBuilder.setContentType(contentType).setUsage(usage);
        AudioAttributes audioAttributes = audioAttributesBuilder.build();
        AudioFormat audioFormat = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .setSampleRate(48000)
                .build();
        AudioMixingRule audioMixingRule = new AudioMixingRule.Builder()
                .addRule(audioAttributes, AudioMixingRule.RULE_MATCH_ATTRIBUTE_USAGE)
                .build();
        return new AudioMix.Builder(audioMixingRule)
                .setFormat(audioFormat)
                .build();
    }

    private class AudioPortUpdateListener implements AudioManager.OnAudioPortUpdateListener {

        @Override
        public void onAudioPortListUpdate(AudioPort[] portList) {
            Log.i(TAG, "onAudioPortListUpdate");
            for (AudioPort port : portList) {
                Log.i(TAG, "port:" + port.toString() + " name:" + port.name());
            }
        }

        @Override
        public void onAudioPatchListUpdate(AudioPatch[] patchList) {
            Log.i(TAG, "onAudioPortListUpdate");
            for (AudioPatch patch : patchList) {
                Log.i(TAG, "patch:" + patch.toString());
            }
        }

        @Override
        public void onServiceDied() {
        }
    }
}
