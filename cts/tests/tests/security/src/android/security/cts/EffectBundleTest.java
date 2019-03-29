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

import android.media.audiofx.AudioEffect;
import android.media.audiofx.EnvironmentalReverb;
import android.media.audiofx.Equalizer;
import android.media.MediaPlayer;
import android.platform.test.annotations.SecurityTest;
import android.test.InstrumentationTestCase;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

@SecurityTest
public class EffectBundleTest extends InstrumentationTestCase {
    private static final String TAG = "EffectBundleTest";
    private static final int[] INVALID_BAND_ARRAY = {Integer.MIN_VALUE, -10000, -100, -2, -1};
    private static final int mValue0 = 9999; //unlikely values. Should not change
    private static final int mValue1 = 13877;
    private static final int PRESET_CUSTOM = -1; //keep in sync AudioEqualizer.h

    private static final int MEDIA_SHORT = 0;
    private static final int MEDIA_LONG = 1;

    // should match audio_effect.h (native)
    private static final int EFFECT_CMD_SET_PARAM = 5;

    private static final int intSize = 4;

    //Testing security bug: 32436341
    public void testEqualizer_getParamCenterFreq() throws Exception {
        if (!hasEqualizer()) {
            return;
        }
        testGetParam(MEDIA_SHORT, Equalizer.PARAM_CENTER_FREQ, INVALID_BAND_ARRAY, mValue0,
                mValue1);
    }

    //Testing security bug: 32588352
    public void testEqualizer_getParamCenterFreq_long() throws Exception {
        if (!hasEqualizer()) {
            return;
        }
        testGetParam(MEDIA_LONG, Equalizer.PARAM_CENTER_FREQ, INVALID_BAND_ARRAY, mValue0, mValue1);
    }

    //Testing security bug: 32438598
    public void testEqualizer_getParamBandLevel() throws Exception {
        if (!hasEqualizer()) {
            return;
        }
        testGetParam(MEDIA_SHORT, Equalizer.PARAM_BAND_LEVEL, INVALID_BAND_ARRAY, mValue0, mValue1);
    }

    //Testing security bug: 32584034
    public void testEqualizer_getParamBandLevel_long() throws Exception {
        if (!hasEqualizer()) {
            return;
        }
        testGetParam(MEDIA_LONG, Equalizer.PARAM_BAND_LEVEL, INVALID_BAND_ARRAY, mValue0, mValue1);
    }

    //Testing security bug: 32247948
    public void testEqualizer_getParamFreqRange() throws Exception {
        if (!hasEqualizer()) {
            return;
        }
        testGetParam(MEDIA_SHORT, Equalizer.PARAM_BAND_FREQ_RANGE, INVALID_BAND_ARRAY, mValue0,
                mValue1);
    }

    //Testing security bug: 32588756
    public void testEqualizer_getParamFreqRange_long() throws Exception {
        if (!hasEqualizer()) {
            return;
        }
        testGetParam(MEDIA_LONG, Equalizer.PARAM_BAND_FREQ_RANGE, INVALID_BAND_ARRAY, mValue0,
                mValue1);
    }

    //Testing security bug: 32448258
    public void testEqualizer_getParamPresetName() throws Exception {
        if (!hasEqualizer()) {
            return;
        }
        testParamPresetName(MEDIA_SHORT);
    }

    //Testing security bug: 32588016
    public void testEqualizer_getParamPresetName_long() throws Exception {
        if (!hasEqualizer()) {
            return;
        }
        testParamPresetName(MEDIA_LONG);
    }

    private void testParamPresetName(int media) {
        final int command = Equalizer.PARAM_GET_PRESET_NAME;
        for (int invalidBand : INVALID_BAND_ARRAY)
        {
            final byte testValue = 7;
            byte reply[] = new byte[Equalizer.PARAM_STRING_SIZE_MAX];
            Arrays.fill(reply, testValue);
            if (!eqGetParam(media, command, invalidBand, reply)) {
                fail("getParam PARAM_GET_PRESET_NAME did not complete successfully");
            }
            //Compare
            if (invalidBand == PRESET_CUSTOM) {
                final String expectedName = "Custom";
                int length = 0;
                while (reply[length] != 0) length++;
                try {
                    final String presetName =  new String(reply, 0, length,
                            StandardCharsets.ISO_8859_1.name());
                    assertEquals("getPresetName custom preset name failed", expectedName,
                            presetName);
                } catch (Exception e) {
                    Log.w(TAG,"Problem creating reply string.");
                }
            } else {
                for (int i = 0; i < reply.length; i++) {
                    assertEquals(String.format("getParam should not change reply at byte %d", i),
                            testValue, reply[i]);
                }
            }
        }
    }

    //testing security bug: 32095626
    public void testEqualizer_setParamBandLevel() throws Exception {
        if (!hasEqualizer()) {
            return;
        }
        final int command = Equalizer.PARAM_BAND_LEVEL;
        short[] value = { 1000 };
        for (int invalidBand : INVALID_BAND_ARRAY)
        {
            if (!eqSetParam(MEDIA_SHORT, command, invalidBand, value)) {
                fail("setParam PARAM_BAND_LEVEL did not complete successfully");
            }
        }
    }

    //testing security bug: 32585400
    public void testEqualizer_setParamBandLevel_long() throws Exception {
        if (!hasEqualizer()) {
            return;
        }
        final int command = Equalizer.PARAM_BAND_LEVEL;
        short[] value = { 1000 };
        for (int invalidBand : INVALID_BAND_ARRAY)
        {
            if (!eqSetParam(MEDIA_LONG, command, invalidBand, value)) {
                fail("setParam PARAM_BAND_LEVEL did not complete successfully");
            }
        }
    }

    //testing security bug: 32705438
    public void testEqualizer_getParamFreqRangeCommand_short() throws Exception {
        if (!hasEqualizer()) {
            return;
        }
        assertTrue("testEqualizer_getParamFreqRangeCommand_short did not complete successfully",
                eqGetParamFreqRangeCommand(MEDIA_SHORT));
    }

    //testing security bug: 32703959
    public void testEqualizer_getParamFreqRangeCommand_long() throws Exception {
        if (!hasEqualizer()) {
            return;
        }
        assertTrue("testEqualizer_getParamFreqRangeCommand_long did not complete successfully",
                eqGetParamFreqRangeCommand(MEDIA_LONG));
    }

    //testing security bug: 37563371 (short media)
    public void testEqualizer_setParamProperties_short() throws Exception {
        if (!hasEqualizer()) {
            return;
        }
        assertTrue("testEqualizer_setParamProperties_long did not complete successfully",
                eqSetParamProperties(MEDIA_SHORT));
    }

    //testing security bug: 37563371 (long media)
    public void testEqualizer_setParamProperties_long() throws Exception {
        if (!hasEqualizer()) {
            return;
        }
        assertTrue("testEqualizer_setParamProperties_long did not complete successfully",
                eqSetParamProperties(MEDIA_LONG));
    }

    //Testing security bug: 63662938
    @SecurityTest
    public void testDownmix_setParameter() throws Exception {
        verifyZeroPVSizeRejectedForSetParameter(
                EFFECT_TYPE_DOWNMIX, new int[] { DOWNMIX_PARAM_TYPE });
    }

    /**
     * Definitions for the downmix effect. Taken from
     * system/media/audio/include/system/audio_effects/effect_downmix.h
     * This effect is normally not exposed to applications.
     */
    private static final UUID EFFECT_TYPE_DOWNMIX = UUID
            .fromString("381e49cc-a858-4aa2-87f6-e8388e7601b2");
    private static final int DOWNMIX_PARAM_TYPE = 0;

    //Testing security bug: 63526567
    @SecurityTest
    public void testEnvironmentalReverb_setParameter() throws Exception {
        verifyZeroPVSizeRejectedForSetParameter(
                AudioEffect.EFFECT_TYPE_ENV_REVERB, new int[] {
                  EnvironmentalReverb.PARAM_ROOM_LEVEL,
                  EnvironmentalReverb.PARAM_ROOM_HF_LEVEL,
                  EnvironmentalReverb.PARAM_DECAY_TIME,
                  EnvironmentalReverb.PARAM_DECAY_HF_RATIO,
                  EnvironmentalReverb.PARAM_REFLECTIONS_LEVEL,
                  EnvironmentalReverb.PARAM_REFLECTIONS_DELAY,
                  EnvironmentalReverb.PARAM_REVERB_LEVEL,
                  EnvironmentalReverb.PARAM_REVERB_DELAY,
                  EnvironmentalReverb.PARAM_DIFFUSION,
                  EnvironmentalReverb.PARAM_DENSITY,
                  10 // EnvironmentalReverb.PARAM_PROPERTIES
                }
        );
    }

    private boolean eqSetParamProperties(int media) {
        MediaPlayer mp = null;
        Equalizer eq = null;
        boolean status = false;
        try {
            mp = MediaPlayer.create(getInstrumentation().getContext(),  getMediaId(media));
            eq = new Equalizer(0 /*priority*/, mp.getAudioSessionId());

            int shortSize = 2; //bytes

            int cmdCode = EFFECT_CMD_SET_PARAM;
            byte command[] = concatArrays(/*status*/ intToByteArray(0),
                    /*psize*/ intToByteArray(1 * intSize),
                    /*vsize*/ intToByteArray(2 * shortSize),
                    /*data[0]*/ intToByteArray((int) 9 /*EQ_PARAM_PROPERTIES*/),
                    /*data[4]*/ shortToByteArray((short)-1 /*preset*/),
                    /*data[6]*/ shortToByteArray((short)5 /*FIVEBAND_NUMBANDS*/));
            byte reply[] = new byte[ 4 /*command.length*/];

            AudioEffect af = eq;
            Object o = AudioEffect.class.getDeclaredMethod("command", int.class, byte[].class,
                    byte[].class).invoke(af, cmdCode, command, reply);

            int replyValue = byteArrayToInt(reply, 0 /*offset*/);
            if (replyValue >= 0) {
                Log.w(TAG, "Reply Value: " + replyValue);
            }
            assertTrue("Negative replyValue was expected ", replyValue < 0);
            status = true;
        } catch (Exception e) {
            Log.w(TAG,"Problem setting parameter in equalizer");
        } finally {
            if (eq != null) {
                eq.release();
            }
            if (mp != null) {
                mp.release();
            }
        }
        return status;
    }

    private boolean eqGetParamFreqRangeCommand(int media) {
        MediaPlayer mp = null;
        Equalizer eq = null;
        boolean status = false;
        try {
            mp = MediaPlayer.create(getInstrumentation().getContext(), getMediaId(media));
            eq = new Equalizer(0 /*priority*/, mp.getAudioSessionId());

            short band = 2;

            //baseline
            int cmdCode = 8; // EFFECT_CMD_GET_PARAM
            byte command[] = concatArrays(/*status*/ intToByteArray(0),
                    /*psize*/ intToByteArray(2 * intSize),
                    /*vsize*/ intToByteArray(2 * intSize),
                    /*data[0]*/ intToByteArray(Equalizer.PARAM_BAND_FREQ_RANGE),
                    /*data[1]*/ intToByteArray((int) band));

            byte reply[] = new byte[command.length];

            AudioEffect af = eq;
            Object o = AudioEffect.class.getDeclaredMethod("command", int.class, byte[].class,
                    byte[].class).invoke(af, cmdCode, command, reply);

            int methodStatus = AudioEffect.ERROR;
            if (o != null) {
                methodStatus = Integer.valueOf(o.toString()).intValue();
            }

            assertTrue("Command expected to fail", methodStatus <= 0);
            int sum = 0;
            for (int i = 0; i < reply.length; i++) {
                sum += Math.abs(reply[i]);
            }

            assertEquals("reply expected to be all zeros", sum, 0);
            status = true;
        } catch (Exception e) {
            Log.w(TAG,"Problem testing eqGetParamFreqRangeCommand");
            status = false;
        } finally {
            if (eq != null) {
                eq.release();
            }
            if (mp != null) {
                mp.release();
            }
        }
        return status;
    }

    private boolean eqGetParam(int media, int command, int band, byte[] reply) {
        MediaPlayer mp = null;
        Equalizer eq = null;
        boolean status = false;
        try {
            mp = MediaPlayer.create(getInstrumentation().getContext(), getMediaId(media));
            eq = new Equalizer(0 /*priority*/, mp.getAudioSessionId());

            AudioEffect af = eq;
            int cmd[] = {command, band};

            AudioEffect.class.getDeclaredMethod("getParameter", int[].class,
                    byte[].class).invoke(af, cmd, reply);
            status = true;
        } catch (Exception e) {
            Log.w(TAG,"Problem testing equalizer");
            status = false;
        } finally {
            if (eq != null) {
                eq.release();
            }
            if (mp != null) {
                mp.release();
            }
        }
        return status;
    }

    private boolean eqGetParam(int media, int command, int band, int[] reply) {
        MediaPlayer mp = null;
        Equalizer eq = null;
        boolean status = false;
        try {
            mp = MediaPlayer.create(getInstrumentation().getContext(), getMediaId(media));
            eq = new Equalizer(0 /*priority*/, mp.getAudioSessionId());

            AudioEffect af = eq;
            int cmd[] = {command, band};

            AudioEffect.class.getDeclaredMethod("getParameter", int[].class,
                    int[].class).invoke(af, cmd, reply);
            status = true;
        } catch (Exception e) {
            Log.w(TAG,"Problem getting parameter from equalizer");
            status = false;
        } finally {
            if (eq != null) {
                eq.release();
            }
            if (mp != null) {
                mp.release();
            }
        }
        return status;
    }

    private void testGetParam(int media, int command, int[] bandArray, int value0, int value1) {
        int reply[] = {value0, value1};
        for (int invalidBand : INVALID_BAND_ARRAY)
        {
            if (!eqGetParam(media, command, invalidBand, reply)) {
                fail(String.format("getParam for command %d did not complete successfully",
                        command));
            }
            assertEquals("getParam should not change value0", value0, reply[0]);
            assertEquals("getParam should not change value1", value1, reply[1]);
        }
    }

    private boolean eqSetParam(int media, int command, int band, short[] value) {
        MediaPlayer mp = null;
        Equalizer eq = null;
        boolean status = false;
        try {
            mp = MediaPlayer.create(getInstrumentation().getContext(),  getMediaId(media));
            eq = new Equalizer(0 /*priority*/, mp.getAudioSessionId());

            AudioEffect af = eq;
            int cmd[] = {command, band};

            AudioEffect.class.getDeclaredMethod("setParameter", int[].class,
                    short[].class).invoke(af, cmd, value);
            status = true;
        } catch (Exception e) {
            Log.w(TAG,"Problem setting parameter in equalizer");
            status = false;
        } finally {
            if (eq != null) {
                eq.release();
            }
            if (mp != null) {
                mp.release();
            }
        }
        return status;
    }

    private int getMediaId(int media) {
        switch (media) {
            default:
            case MEDIA_SHORT:
                return R.raw.good;
            case MEDIA_LONG:
                return R.raw.onekhzsine_90sec;
        }
    }

    // Verifies that for all the effects of the specified type
    // an attempt to pass psize = 0 or vsize = 0 to 'set parameter' command
    // is rejected by the effect.
    private void verifyZeroPVSizeRejectedForSetParameter(
            UUID effectType, final int paramCodes[]) throws Exception {

        boolean effectFound = false;
        for (AudioEffect.Descriptor descriptor : AudioEffect.queryEffects()) {
            if (descriptor.type.compareTo(effectType) != 0) continue;

            effectFound = true;
            AudioEffect ae = null;
            MediaPlayer mp = null;
            try {
                mp = MediaPlayer.create(getInstrumentation().getContext(), R.raw.good);
                java.lang.reflect.Constructor ct = AudioEffect.class.getConstructor(
                        UUID.class, UUID.class, int.class, int.class);
                try {
                    ae = (AudioEffect) ct.newInstance(descriptor.type, descriptor.uuid,
                            /*priority*/ 0, mp.getAudioSessionId());
                } catch (Exception e) {
                    // Not every effect can be instantiated by apps.
                    Log.w(TAG, "Failed to create effect " + descriptor.uuid);
                    continue;
                }
                java.lang.reflect.Method command = AudioEffect.class.getDeclaredMethod(
                        "command", int.class, byte[].class, byte[].class);
                for (int paramCode : paramCodes) {
                    executeSetParameter(ae, command, intSize, 0, paramCode);
                    executeSetParameter(ae, command, 0, intSize, paramCode);
                }
            } finally {
                if (ae != null) {
                    ae.release();
                }
                if (mp != null) {
                    mp.release();
                }
            }
        }

        if (!effectFound) {
            Log.w(TAG, "No effect with type " + effectType + " was found");
        }
    }

    private void executeSetParameter(AudioEffect ae, java.lang.reflect.Method command,
            int paramSize, int valueSize, int paramCode) throws Exception {
        byte cmdBuf[] = concatArrays(/*status*/ intToByteArray(0),
                /*psize*/ intToByteArray(paramSize),
                /*vsize*/ intToByteArray(valueSize),
                /*data[0]*/ intToByteArray(paramCode));
        byte reply[] = new byte[intSize];
        Integer ret = (Integer)command.invoke(ae, EFFECT_CMD_SET_PARAM, cmdBuf, reply);
        if (ret >= 0) {
            int val = byteArrayToInt(reply, 0 /*offset*/);
            assertTrue("Negative reply value expected, effect " + ae.getDescriptor().uuid +
                    ", parameter " + paramCode + ", reply value " + val,
                    val < 0);
        } else {
            // Some effect implementations detect this condition at the command dispatch level,
            // and reject command execution. That's also OK, but log a message so the test
            // author can double check if 'paramCode' is correct.
            Log.w(TAG, "\"Set parameter\" command rejected for effect " + ae.getDescriptor().uuid +
                    ", parameter " + paramCode + ", return code " + ret);
        }
    }

    private boolean hasEqualizer() {
        boolean result = false;
        try {
            MediaPlayer mp = MediaPlayer.create(getInstrumentation().getContext(), R.raw.good);
            new Equalizer(0 /*priority*/, mp.getAudioSessionId());
            result = true;
        } catch (Exception e) {
            Log.d(TAG, "Cannot create equalizer");
        }
        return result;
    }

    private static byte[] intToByteArray(int value) {
        ByteBuffer converter = ByteBuffer.allocate(4);
        converter.order(ByteOrder.nativeOrder());
        converter.putInt(value);
        return converter.array();
    }

    public static int byteArrayToInt(byte[] valueBuf, int offset) {
        ByteBuffer converter = ByteBuffer.wrap(valueBuf);
        converter.order(ByteOrder.nativeOrder());
        return converter.getInt(offset);
    }

    private static byte[] shortToByteArray(short value) {
        ByteBuffer converter = ByteBuffer.allocate(2);
        converter.order(ByteOrder.nativeOrder());
        short sValue = (short) value;
        converter.putShort(sValue);
        return converter.array();
    }

    private static  byte[] concatArrays(byte[]... arrays) {
        int len = 0;
        for (byte[] a : arrays) {
            len += a.length;
        }
        byte[] b = new byte[len];

        int offs = 0;
        for (byte[] a : arrays) {
            System.arraycopy(a, 0, b, offs, a.length);
            offs += a.length;
        }
        return b;
    }
}
