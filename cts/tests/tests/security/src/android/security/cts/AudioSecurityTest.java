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
package android.security.cts;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.Equalizer;
import android.platform.test.annotations.SecurityTest;
import android.util.Log;

import com.android.compatibility.common.util.CtsAndroidTestCase;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.UUID;

@SecurityTest
public class AudioSecurityTest extends CtsAndroidTestCase {
    private static final String TAG = "AudioSecurityTest";

    private static final int ERROR_DEAD_OBJECT = -7; // AudioEffect.ERROR_DEAD_OBJECT

    // should match audio_effect.h (native)
    private static final int EFFECT_CMD_SET_PARAM = 5;
    private static final int EFFECT_CMD_GET_PARAM = 8;
    private static final int EFFECT_CMD_OFFLOAD   = 20;
    private static final int SIZEOF_EFFECT_PARAM_T = 12;

    private static void verifyZeroReply(byte[] reply) throws Exception {
        int count = 0;
        for (byte b : reply) {
            if (b != 0) {
                count++;
            }
        }
        assertEquals("reply has " + count + " nonzero values", 0 /* expected */, count);
    }

    // @FunctionalInterface
    private interface TestEffect {
        void test(AudioEffect audioEffect) throws Exception;
    }

    private static void testAllEffects(String testName, TestEffect testEffect) throws Exception {
        int failures = 0;
        for (AudioEffect.Descriptor descriptor : AudioEffect.queryEffects()) {
            final AudioEffect audioEffect;
            try {
                audioEffect = (AudioEffect)AudioEffect.class.getConstructor(
                        UUID.class, UUID.class, int.class, int.class).newInstance(
                                descriptor.type,
                                descriptor.uuid, // uuid overrides type
                                0 /* priority */, 0 /* audioSession */);
            } catch (Exception e) {
                Log.w(TAG, "effect " + testName + " " + descriptor.name
                        + " cannot be created (ignoring)");
                continue; // OK;
            }
            try {
                testEffect.test(audioEffect);
                Log.d(TAG, "effect " + testName + " " + descriptor.name + " success");
            } catch (Exception e) {
                Log.e(TAG, "effect " + testName + " " + descriptor.name + " exception failed!",
                        e);
                ++failures;
            } catch (AssertionError e) {
                Log.e(TAG, "effect " + testName + " " + descriptor.name + " assert failed!",
                        e);
                ++failures;
            }
        }
        assertEquals("found " + testName + " " + failures + " failures",
                0 /* expected */, failures);
    }

    // b/28173666
    public void testAllEffectsGetParameterAttemptOffload_CVE_2016_3745() throws Exception {
        testAllEffects("get parameter attempt offload",
                new TestEffect() {
            @Override
            public void test(AudioEffect audioEffect) throws Exception {
                testAudioEffectGetParameter(audioEffect, true /* offload */);
            }
        });
    }

    // b/32438594
    // b/32624850
    // b/32635664
    public void testAllEffectsGetParameter2AttemptOffload_CVE_2017_0398() throws Exception {
        testAllEffects("get parameter2 attempt offload",
                new TestEffect() {
            @Override
            public void test(AudioEffect audioEffect) throws Exception {
                testAudioEffectGetParameter2(audioEffect, true /* offload */);
            }
        });
    }

    // b/30204301
    public void testAllEffectsSetParameterAttemptOffload_CVE_2016_3924() throws Exception {
        testAllEffects("set parameter attempt offload",
                new TestEffect() {
            @Override
            public void test(AudioEffect audioEffect) throws Exception {
                testAudioEffectSetParameter(audioEffect, true /* offload */);
            }
        });
    }

    // b/37536407
    public void testAllEffectsEqualizer_CVE_2017_0401() throws Exception {
        testAllEffects("equalizer get parameter name",
                new TestEffect() {
            @Override
            public void test(AudioEffect audioEffect) throws Exception {
                testAudioEffectEqualizerGetParameterName(audioEffect);
            }
        });
    }

    private static void testAudioEffectGetParameter(
            AudioEffect audioEffect, boolean offload) throws Exception {
        if (audioEffect == null) {
            return;
        }
        try {
            // 1) set offload_enabled
            if (offload) {
                byte command[] = new byte[8];
                Arrays.fill(command, (byte)1);
                byte reply[] = new byte[4]; // ignored

                /* ignored */ AudioEffect.class.getDeclaredMethod(
                        "command", int.class, byte[].class, byte[].class).invoke(
                                audioEffect, EFFECT_CMD_OFFLOAD, command, reply);
            }

            // 2) get parameter with invalid psize
            {
                byte command[] = new byte[30];
                Arrays.fill(command, (byte)0xDD);
                byte reply[] = new byte[30];

                Integer ret = (Integer) AudioEffect.class.getDeclaredMethod(
                        "command", int.class, byte[].class, byte[].class).invoke(
                                audioEffect, EFFECT_CMD_GET_PARAM, command, reply);

                assertTrue("Audio server might have crashed", ret != ERROR_DEAD_OBJECT);
                verifyZeroReply(reply);
            }

            // NOTE: an alternative way of checking crash:
            //
            // Thread.sleep(1000 /* millis */);
            // assertTrue("Audio server might have crashed",
            //        audioEffect.setEnabled(false) != AudioEffect.ERROR_DEAD_OBJECT);
        } catch (NoSuchMethodException e) {
            Log.w(TAG, "AudioEffect.command() does not exist (ignoring)"); // OK
        } finally {
            audioEffect.release();
        }
    }

    private static void testAudioEffectGetParameter2(
            AudioEffect audioEffect, boolean offload) throws Exception {
        if (audioEffect == null) {
            return;
        }
        try {
            // 1) set offload_enabled
            if (offload) {
                byte command[] = new byte[8];
                Arrays.fill(command, (byte)1);
                byte reply[] = new byte[4]; // ignored

                /* ignored */ AudioEffect.class.getDeclaredMethod(
                        "command", int.class, byte[].class, byte[].class).invoke(
                                audioEffect, EFFECT_CMD_OFFLOAD, command, reply);
            }

            // 2) get parameter with small command size but large psize
            {
                final int parameterSize = 0x100000;

                byte command[] = ByteBuffer.allocate(5 * 4 /* capacity */)
                        .order(ByteOrder.nativeOrder())
                        .putInt(0)             // status (unused)
                        .putInt(parameterSize) // psize (very large)
                        .putInt(0)             // vsize
                        .putInt(0x04030201)    // data[0] (param too small for psize)
                        .putInt(0x08070605)    // data[4]
                        .array();
                byte reply[] = new byte[parameterSize + SIZEOF_EFFECT_PARAM_T];

                Integer ret = (Integer) AudioEffect.class.getDeclaredMethod(
                        "command", int.class, byte[].class, byte[].class).invoke(
                                audioEffect, EFFECT_CMD_GET_PARAM, command, reply);

                verifyZeroReply(reply);
                assertTrue("Audio server might have crashed", ret != ERROR_DEAD_OBJECT);
            }
        } catch (NoSuchMethodException e) {
            Log.w(TAG, "AudioEffect.command() does not exist (ignoring)"); // OK
        } finally {
            audioEffect.release();
        }
    }

    private static void testAudioEffectGetParameter3(AudioEffect audioEffect) throws Exception {
        if (audioEffect == null) {
            return;
        }
        try {
            // 1) get parameter with zero command size
            {
                final int parameterSize = 0x10;

                Integer ret = (Integer) AudioEffect.class.getDeclaredMethod(
                        "command", int.class, byte[].class, byte[].class).invoke(
                                audioEffect,
                                EFFECT_CMD_GET_PARAM,
                                new byte[0] /* command */,
                                new byte[parameterSize + SIZEOF_EFFECT_PARAM_T] /* reply */);

                assertTrue("Audio server might have crashed", ret != ERROR_DEAD_OBJECT);
            }
        } catch (NoSuchMethodException e) {
            Log.w(TAG, "AudioEffect.command() does not exist (ignoring)"); // OK
        } finally {
            audioEffect.release();
        }
    }

    private static void testAudioEffectSetParameter(
            AudioEffect audioEffect, boolean offload) throws Exception {
        if (audioEffect == null) {
            return;
        }
        try {
            // 1) set offload_enabled
            if (offload) {
                byte command[] = new byte[8];
                Arrays.fill(command, (byte)1);
                byte reply[] = new byte[4]; // ignored

                /* ignored */ AudioEffect.class.getDeclaredMethod(
                        "command", int.class, byte[].class, byte[].class).invoke(
                                audioEffect, EFFECT_CMD_OFFLOAD, command, reply);
            }

            // 2) set parameter with invalid psize
            {
                byte command[] = ByteBuffer.allocate(5 * 4 /* capacity */)
                        .order(ByteOrder.nativeOrder())
                        .putInt(0)          // status (unused)
                        .putInt(0xdddddddd) // psize (very large)
                        .putInt(4)          // vsize
                        .putInt(1)          // data[0] (param too small for psize)
                        .putInt(0)          // data[4]
                        .array();
                byte reply[] = new byte[4]; // returns status code (ignored)

                Integer ret = (Integer) AudioEffect.class.getDeclaredMethod(
                        "command", int.class, byte[].class, byte[].class).invoke(
                                audioEffect, EFFECT_CMD_SET_PARAM, command, reply);

                assertTrue("Audio server might have crashed", ret != ERROR_DEAD_OBJECT);
                // on failure reply may contain the status code.
            }
        } catch (NoSuchMethodException e) {
            Log.w(TAG, "AudioEffect.command() does not exist (ignoring)"); // OK
        } finally {
            audioEffect.release();
        }
    }

    private static void testAudioEffectSetOffload(AudioEffect audioEffect) throws Exception {
        if (audioEffect == null) {
            return;
        }
        try {
            // 1) set offload_enabled with zero command and reply size
            {
                Integer ret = (Integer) AudioEffect.class.getDeclaredMethod(
                        "command", int.class, byte[].class, byte[].class).invoke(
                                audioEffect,
                                EFFECT_CMD_OFFLOAD,
                                new byte[0] /* command */,
                                new byte[0] /* reply */);

                assertTrue("Audio server might have crashed", ret != ERROR_DEAD_OBJECT);
            }
         } catch (NoSuchMethodException e) {
            Log.w(TAG, "AudioEffect.command() does not exist (ignoring)"); // OK
        } finally {
            audioEffect.release();
        }
    }

    private static void testAudioEffectEqualizerGetParameterName(
            AudioEffect audioEffect) throws Exception {
        if (audioEffect == null) {
            return;
        }
        try {
            // get parameter name with zero vsize
            {
                final int param = Equalizer.PARAM_GET_PRESET_NAME;
                final int band = 0;
                byte command[] = ByteBuffer.allocate(5 * 4 /* capacity */)
                        .order(ByteOrder.nativeOrder())
                        .putInt(0)          // status (unused)
                        .putInt(8)          // psize (param, band)
                        .putInt(0)          // vsize
                        .putInt(param)      // equalizer param
                        .putInt(band)       // equalizer band
                        .array();
                Integer ret = (Integer) AudioEffect.class.getDeclaredMethod(
                        "command", int.class, byte[].class, byte[].class).invoke(
                                audioEffect, EFFECT_CMD_GET_PARAM, command,
                                new byte[5 * 4] /* reply - ignored */);
                assertTrue("Audio server might have crashed", ret != ERROR_DEAD_OBJECT);
            }
        } catch (NoSuchMethodException e) {
            Log.w(TAG, "AudioEffect.command() does not exist (ignoring)"); // OK
        } finally {
            audioEffect.release();
        }
    }

    // should match effect_visualizer.h (native)
    private static final String VISUALIZER_TYPE = "e46b26a0-dddd-11db-8afd-0002a5d5c51b";
    private static final int VISUALIZER_CMD_CAPTURE = 0x10000;
    private static final int VISUALIZER_PARAM_CAPTURE_SIZE = 0;

    // b/31781965
    public void testVisualizerCapture_CVE_2017_0396() throws Exception {
        // Capture params
        final int CAPTURE_SIZE = 1 << 24; // 16MB seems to be large enough to cause a SEGV.
        final byte[] captureBuf = new byte[CAPTURE_SIZE];

        // Track params
        final int sampleRate = 48000;
        final int format = AudioFormat.ENCODING_PCM_16BIT;
        final int loops = 1;
        final int seconds = 1;
        final int channelCount = 2;
        final int bufferFrames = seconds * sampleRate;
        final int bufferSamples = bufferFrames * channelCount;
        final int bufferSize = bufferSamples * 2; // bytes per sample for 16 bits
        final short data[] = new short[bufferSamples]; // zero data

        for (AudioEffect.Descriptor descriptor : AudioEffect.queryEffects()) {
            if (descriptor.type.compareTo(UUID.fromString(VISUALIZER_TYPE)) != 0) {
                continue;
            }

            AudioEffect audioEffect = null;
            AudioTrack audioTrack = null;

            try {
                // create track and play
                {
                    audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate,
                            AudioFormat.CHANNEL_OUT_STEREO, format, bufferSize,
                            AudioTrack.MODE_STATIC);
                    assertEquals("Cannot write to audio track",
                            bufferSamples,
                            audioTrack.write(data, 0 /* offsetInBytes */, data.length));
                    assertEquals("AudioTrack not initialized",
                            AudioTrack.STATE_INITIALIZED,
                            audioTrack.getState());
                    assertEquals("Cannot set loop points",
                            android.media.AudioTrack.SUCCESS,
                            audioTrack.setLoopPoints(0 /* startInFrames */, bufferFrames, loops));
                    audioTrack.play();
                }

                // wait for track to really begin playing
                Thread.sleep(200 /* millis */);

                // create effect
                {
                    audioEffect = (AudioEffect) AudioEffect.class.getConstructor(
                            UUID.class, UUID.class, int.class, int.class).newInstance(
                                    descriptor.type, descriptor.uuid, 0 /* priority */,
                                    audioTrack.getAudioSessionId());
                }

                // set capture size
                {
                    byte command[] = ByteBuffer.allocate(5 * 4 /* capacity */)
                            .order(ByteOrder.nativeOrder())
                            .putInt(0)                             // status (unused)
                            .putInt(4)                             // psize (sizeof(param))
                            .putInt(4)                             // vsize (sizeof(value))
                            .putInt(VISUALIZER_PARAM_CAPTURE_SIZE) // data[0] (param)
                            .putInt(CAPTURE_SIZE)                  // data[4] (value)
                            .array();

                    Integer ret = (Integer) AudioEffect.class.getDeclaredMethod(
                            "command", int.class, byte[].class, byte[].class).invoke(
                                    audioEffect,
                                    EFFECT_CMD_SET_PARAM,
                                    command, new byte[4] /* reply */);
                    Log.d(TAG, "setparam returns " + ret);
                    assertTrue("Audio server might have crashed", ret != ERROR_DEAD_OBJECT);
                }

                // enable effect
                {
                    final int ret = audioEffect.setEnabled(true);
                    assertEquals("Cannot enable audio effect", 0 /* expected */, ret);
                }

                // wait for track audio data to be processed, otherwise capture
                // will not really return audio data.
                Thread.sleep(200 /* millis */);

                // capture data
                {
                    Integer ret = (Integer) AudioEffect.class.getDeclaredMethod(
                            "command", int.class, byte[].class, byte[].class).invoke(
                                    audioEffect,
                                    VISUALIZER_CMD_CAPTURE,
                                    new byte[0] /* command */, captureBuf /* reply */);
                    Log.d(TAG, "capture returns " + ret);
                    assertTrue("Audio server might have crashed", ret != ERROR_DEAD_OBJECT);
                }
            } finally {
                if (audioEffect != null) {
                    audioEffect.release();
                }
                if (audioTrack != null) {
                    audioTrack.release();
                }
            }
        }
    }
}
