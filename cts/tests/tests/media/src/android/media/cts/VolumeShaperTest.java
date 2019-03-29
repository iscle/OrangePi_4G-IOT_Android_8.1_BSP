/*
 * Copyright 2017 The Android Open Source Project
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

import static org.testng.Assert.assertThrows;

import android.media.cts.R;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioSystem;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.media.VolumeShaper;
import android.os.Parcel;
import android.os.PowerManager;
import android.support.test.filters.FlakyTest;
import android.support.test.filters.LargeTest;
import android.support.test.filters.MediumTest;
import android.support.test.filters.SmallTest;
import android.util.Log;

import com.android.compatibility.common.util.CtsAndroidTestCase;

import java.lang.AutoCloseable;
import java.util.Arrays;

/**
 * VolumeShaperTest is automated using VolumeShaper.getVolume() to verify that a ramp
 * or a duck is at the expected volume level. Listening to some tests is also possible,
 * as we logcat the expected volume change.
 *
 * To see the listening messages:
 *
 * adb logcat | grep VolumeShaperTest
 */
public class VolumeShaperTest extends CtsAndroidTestCase {
    private static final String TAG = "VolumeShaperTest";

    // ramp or duck time (duration) used in tests.
    private static final long RAMP_TIME_MS = 3000;

    // volume tolerance for completion volume checks.
    private static final float VOLUME_TOLERANCE = 0.0000001f;

    // volume difference permitted on replace() with join.
    private static final float JOIN_VOLUME_TOLERANCE = 0.1f;

    // time to wait for player state change
    private static final long WARMUP_TIME_MS = 300;

    private static final VolumeShaper.Configuration SILENCE =
            new VolumeShaper.Configuration.Builder()
                .setInterpolatorType(VolumeShaper.Configuration.INTERPOLATOR_TYPE_LINEAR)
                .setCurve(new float[] { 0.f, 1.f } /* times */,
                        new float[] { 0.f, 0.f } /* volumes */)
                .setDuration(RAMP_TIME_MS)
                .build();

    // Duck configurations go from 1.f down to 0.2f (not full ramp down).
    private static final VolumeShaper.Configuration LINEAR_DUCK =
            new VolumeShaper.Configuration.Builder()
                .setInterpolatorType(VolumeShaper.Configuration.INTERPOLATOR_TYPE_LINEAR)
                .setCurve(new float[] { 0.f, 1.f } /* times */,
                        new float[] { 1.f, 0.2f } /* volumes */)
                .setDuration(RAMP_TIME_MS)
                .build();

    // Ramp configurations go from 0.f up to 1.f
    private static final VolumeShaper.Configuration LINEAR_RAMP =
            new VolumeShaper.Configuration.Builder(VolumeShaper.Configuration.LINEAR_RAMP)
                .setDuration(RAMP_TIME_MS)
                .build();

    private static final VolumeShaper.Configuration CUBIC_RAMP =
            new VolumeShaper.Configuration.Builder(VolumeShaper.Configuration.CUBIC_RAMP)
                .setDuration(RAMP_TIME_MS)
                .build();

    private static final VolumeShaper.Configuration SINE_RAMP =
            new VolumeShaper.Configuration.Builder(VolumeShaper.Configuration.SINE_RAMP)
                .setDuration(RAMP_TIME_MS)
                .build();

    private static final VolumeShaper.Configuration SCURVE_RAMP =
            new VolumeShaper.Configuration.Builder(VolumeShaper.Configuration.SCURVE_RAMP)
            .setDuration(RAMP_TIME_MS)
            .build();

    // internal use only
    private static final VolumeShaper.Configuration LOG_RAMP =
            new VolumeShaper.Configuration.Builder()
                .setInterpolatorType(VolumeShaper.Configuration.INTERPOLATOR_TYPE_LINEAR)
                .setOptionFlags(VolumeShaper.Configuration.OPTION_FLAG_VOLUME_IN_DBFS)
                .setCurve(new float[] { 0.f, 1.f } /* times */,
                        new float[] { -80.f, 0.f } /* volumes */)
                .setDuration(RAMP_TIME_MS)
                .build();

    // a step ramp is not continuous, so we have a different test for it.
    private static final VolumeShaper.Configuration STEP_RAMP =
            new VolumeShaper.Configuration.Builder()
                .setInterpolatorType(VolumeShaper.Configuration.INTERPOLATOR_TYPE_STEP)
                .setCurve(new float[] { 0.f, 1.f } /* times */,
                        new float[] { 0.f, 1.f } /* volumes */)
                .setDuration(RAMP_TIME_MS)
                .build();

    private static final VolumeShaper.Configuration[] ALL_STANDARD_RAMPS = {
        LINEAR_RAMP,
        CUBIC_RAMP,
        SINE_RAMP,
        SCURVE_RAMP,
    };

    private static final VolumeShaper.Configuration[] TEST_DUCKS = {
        LINEAR_DUCK,
    };

    // this ramp should result in non-monotonic behavior with a typical cubic spline.
    private static final VolumeShaper.Configuration MONOTONIC_TEST =
            new VolumeShaper.Configuration.Builder()
                .setInterpolatorType(VolumeShaper.Configuration.INTERPOLATOR_TYPE_CUBIC_MONOTONIC)
                .setCurve(new float[] { 0.f, 0.3f, 0.7f, 1.f } /* times */,
                        new float[] { 0.f, 0.5f, 0.5f, 1.f } /* volumes */)
                .setDuration(RAMP_TIME_MS)
                .build();

    private static final VolumeShaper.Configuration MONOTONIC_TEST_FAIL =
            new VolumeShaper.Configuration.Builder(MONOTONIC_TEST)
                .setInterpolatorType(VolumeShaper.Configuration.INTERPOLATOR_TYPE_CUBIC)
                .build();

    private static final VolumeShaper.Operation[] ALL_STANDARD_OPERATIONS = {
        VolumeShaper.Operation.PLAY,
        VolumeShaper.Operation.REVERSE,
    };

    private boolean hasAudioOutput() {
        return getContext().getPackageManager()
            .hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT);
    }

    private boolean isLowRamDevice() {
        return ((ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE))
                .isLowRamDevice();
    }

    private static AudioTrack createSineAudioTrack() {
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_FLOAT;
        final int TEST_MODE = AudioTrack.MODE_STATIC;
        final int TEST_SR = 48000;
        final AudioFormat format = new AudioFormat.Builder()
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(TEST_FORMAT)
                .setSampleRate(TEST_SR)
                .build();

        final int frameCount = AudioHelper.frameCountFromMsec(100 /*ms*/, format);
        final int frameSize = AudioHelper.frameSizeFromFormat(format);

        final AudioTrack audioTrack = new AudioTrack.Builder()
            .setAudioFormat(format)
            .setBufferSizeInBytes(frameCount * frameSize)
            .setTransferMode(TEST_MODE)
            .build();
        // create float array and write it
        final int sampleCount = frameCount * format.getChannelCount();
        final float[] vaf = AudioHelper.createSoundDataInFloatArray(
                sampleCount, TEST_SR,
                600 * format.getChannelCount() /* frequency */, 0 /* sweep */);
        assertEquals(vaf.length, audioTrack.write(vaf, 0 /* offsetInFloats */, vaf.length,
                AudioTrack.WRITE_NON_BLOCKING));
        audioTrack.setLoopPoints(0, frameCount, -1 /* loopCount */);
        return audioTrack;
    }

    private MediaPlayer createMediaPlayer(boolean offloaded) {
        // MP3 resource should be greater than 1m to introduce offloading
        final int RESOURCE_ID = R.raw.test1m1s;

        final MediaPlayer mediaPlayer = MediaPlayer.create(getContext(),
                RESOURCE_ID,
                new AudioAttributes.Builder()
                    .setUsage(offloaded ?
                            AudioAttributes.USAGE_MEDIA  // offload allowed
                            : AudioAttributes.USAGE_NOTIFICATION) // offload not allowed
                    .build(),
                AudioSystem.newAudioSessionId());
        mediaPlayer.setWakeMode(getContext(), PowerManager.PARTIAL_WAKE_LOCK);
        mediaPlayer.setLooping(true);
        return mediaPlayer;
    }

    private static void checkEqual(String testName,
            VolumeShaper.Configuration expected, VolumeShaper.Configuration actual) {
        assertEquals(testName + " configuration should be equal",
                expected, actual);
        assertEquals(testName + " configuration.hashCode() should be equal",
                expected.hashCode(), actual.hashCode());
        assertEquals(testName + " configuration.toString() should be equal",
                expected.toString(), actual.toString());
    }

    private static void checkNotEqual(String testName,
            VolumeShaper.Configuration notEqual, VolumeShaper.Configuration actual) {
        assertTrue(testName + " configuration should not be equal",
                !actual.equals(notEqual));
        assertTrue(testName + " configuration.hashCode() should not be equal",
                actual.hashCode() != notEqual.hashCode());
        assertTrue(testName + " configuration.toString() should not be equal",
                !actual.toString().equals(notEqual.toString()));
    }

    // generic player class to simplify testing
    private interface Player extends AutoCloseable {
        public void start();
        public void pause();
        public void stop();
        @Override public void close();
        public VolumeShaper createVolumeShaper(VolumeShaper.Configuration configuration);
        public String name();
    }

    private static class AudioTrackPlayer implements Player {
        public AudioTrackPlayer() {
            mTrack = createSineAudioTrack();
            mName = new String("AudioTrack");
        }

        @Override public void start() {
            mTrack.play();
        }

        @Override public void pause() {
            mTrack.pause();
        }

        @Override public void stop() {
            mTrack.stop();
        }

        @Override public void close() {
            mTrack.release();
        }

        @Override
        public VolumeShaper createVolumeShaper(VolumeShaper.Configuration configuration) {
            return mTrack.createVolumeShaper(configuration);
        }

        @Override public String name() {
            return mName;
        }

        private final AudioTrack mTrack;
        private final String mName;
    }

    private class MediaPlayerPlayer implements Player {
        public MediaPlayerPlayer(boolean offloaded) {
            mPlayer = createMediaPlayer(offloaded);
            mName = new String("MediaPlayer" + (offloaded ? "Offloaded" : "NonOffloaded"));
        }

        @Override public void start() {
            mPlayer.start();
        }

        @Override public void pause() {
            mPlayer.pause();
        }

        @Override public void stop() {
            mPlayer.stop();
        }

        @Override public void close() {
            mPlayer.release();
        }

        @Override
        public VolumeShaper createVolumeShaper(VolumeShaper.Configuration configuration) {
            return mPlayer.createVolumeShaper(configuration);
        }

        @Override public String name() {
            return mName;
        }

        private final MediaPlayer mPlayer;
        private final String mName;
    }

    private static final int PLAYER_TYPES = 3;
    private static final int PLAYER_TYPE_AUDIO_TRACK = 0;
    private static final int PLAYER_TYPE_MEDIA_PLAYER_NON_OFFLOADED = 1;
    private static final int PLAYER_TYPE_MEDIA_PLAYER_OFFLOADED = 2;

    private Player createPlayer(int type) {
        switch (type) {
            case PLAYER_TYPE_AUDIO_TRACK:
                return new AudioTrackPlayer();
            case PLAYER_TYPE_MEDIA_PLAYER_NON_OFFLOADED:
                return new MediaPlayerPlayer(false /* offloaded */);
            case PLAYER_TYPE_MEDIA_PLAYER_OFFLOADED:
                return new MediaPlayerPlayer(true /* offloaded */);
            default:
                return null;
        }
    }

    private static void testBuildRamp(int points) {
        float[] ramp = new float[points];
        final float fscale = 1.f / (points - 1);
        for (int i = 0; i < points; ++i) {
            ramp[i] = i * fscale;
        }
        ramp[points - 1] = 1.f;
        // does it build?
        final VolumeShaper.Configuration config = new VolumeShaper.Configuration.Builder()
                .setCurve(ramp, ramp)
                .build();
    }

    @SmallTest
    public void testVolumeShaperConfigurationBuilder() throws Exception {
        final String TEST_NAME = "testVolumeShaperConfigurationBuilder";

        // Verify that IllegalStateExceptions are properly triggered
        // for methods with no arguments.

        Log.d(TAG, TEST_NAME + " configuration builder should throw ISE if no curve specified");
        assertThrows(IllegalStateException.class,
                new VolumeShaper.Configuration.Builder()
                    ::build);

        assertThrows(IllegalStateException.class,
                new VolumeShaper.Configuration.Builder()
                    ::invertVolumes);

        assertThrows(IllegalStateException.class,
                new VolumeShaper.Configuration.Builder()
                    ::reflectTimes);

        Log.d(TAG, TEST_NAME + " configuration builder should IAE on invalid curve");
        // Verify IllegalArgumentExceptions are properly triggered
        // for methods with arguments.
        final float[] ohOne = { 0.f, 1.f };
        final float[][] invalidCurves = {
                { -1.f, 1.f },
                { 0.5f },
                { 0.f, 2.f },
        };
        for (float[] invalidCurve : invalidCurves) {
            assertThrows(IllegalArgumentException.class,
                    () -> {
                        new VolumeShaper.Configuration.Builder()
                            .setCurve(invalidCurve, ohOne)
                            .build(); });

            assertThrows(IllegalArgumentException.class,
                    () -> {
                        new VolumeShaper.Configuration.Builder()
                            .setCurve(ohOne, invalidCurve)
                            .build(); });
        }

        Log.d(TAG, TEST_NAME + " configuration builder should throw IAE on invalid duration");
        assertThrows(IllegalArgumentException.class,
                () -> {
                    new VolumeShaper.Configuration.Builder()
                        .setCurve(ohOne, ohOne)
                        .setDuration(-1)
                        .build(); });

        Log.d(TAG, TEST_NAME + " configuration builder should throw IAE on invalid interpolator");
        assertThrows(IllegalArgumentException.class,
                () -> {
                    new VolumeShaper.Configuration.Builder()
                        .setCurve(ohOne, ohOne)
                        .setInterpolatorType(-1)
                        .build(); });

        // Verify defaults.
        // Use the Builder with setCurve(ohOne, ohOne).
        final VolumeShaper.Configuration config =
                new VolumeShaper.Configuration.Builder().setCurve(ohOne, ohOne).build();
        assertEquals(TEST_NAME + " default interpolation should be cubic",
                VolumeShaper.Configuration.INTERPOLATOR_TYPE_CUBIC, config.getInterpolatorType());
        assertEquals(TEST_NAME + " default duration should be 1000 ms",
                1000, config.getDuration());
        assertTrue(TEST_NAME + " times should be { 0.f, 1.f }",
                Arrays.equals(ohOne, config.getTimes()));
        assertTrue(TEST_NAME + " volumes should be { 0.f, 1.f }",
                Arrays.equals(ohOne, config.getVolumes()));

        // Due to precision problems, we cannot have ramps that do not have
        // perfect binary representation for equality comparison.
        // (For example, 0.1 is a repeating mantissa in binary,
        //  but 0.25, 0.5 can be expressed with few mantissa bits).
        final float[] binaryCurve1 = { 0.f, 0.25f, 0.5f, 0.625f,  1.f };
        final float[] binaryCurve2 = { 0.f, 0.125f, 0.375f, 0.75f, 1.f };
        final VolumeShaper.Configuration[] BINARY_RAMPS = {
            LINEAR_RAMP,
            CUBIC_RAMP,
            new VolumeShaper.Configuration.Builder()
                    .setCurve(binaryCurve1, binaryCurve2)
                    .build(),
        };

        // Verify volume inversion and time reflection work as expected
        // with ramps (which start at { 0.f, 0.f } and end at { 1.f, 1.f }).
        for (VolumeShaper.Configuration testRamp : BINARY_RAMPS) {
            VolumeShaper.Configuration ramp;
            ramp = new VolumeShaper.Configuration.Builder(testRamp).build();
            checkEqual(TEST_NAME, testRamp, ramp);

            ramp = new VolumeShaper.Configuration.Builder(testRamp)
                    .setDuration(10)
                    .build();
            checkNotEqual(TEST_NAME, testRamp, ramp);

            ramp = new VolumeShaper.Configuration.Builder(testRamp).build();
            checkEqual(TEST_NAME, testRamp, ramp);

            ramp = new VolumeShaper.Configuration.Builder(testRamp)
                    .invertVolumes()
                    .build();
            checkNotEqual(TEST_NAME, testRamp, ramp);

            ramp = new VolumeShaper.Configuration.Builder(testRamp)
                    .invertVolumes()
                    .invertVolumes()
                    .build();
            checkEqual(TEST_NAME, testRamp, ramp);

            ramp = new VolumeShaper.Configuration.Builder(testRamp)
                    .reflectTimes()
                    .build();
            checkNotEqual(TEST_NAME, testRamp, ramp);

            ramp = new VolumeShaper.Configuration.Builder(testRamp)
                    .reflectTimes()
                    .reflectTimes()
                    .build();
            checkEqual(TEST_NAME, testRamp, ramp);

            // check scaling start and end volumes
            ramp = new VolumeShaper.Configuration.Builder(testRamp)
                    .scaleToStartVolume(0.5f)
                    .build();
            checkNotEqual(TEST_NAME, testRamp, ramp);

            ramp = new VolumeShaper.Configuration.Builder(testRamp)
                    .scaleToStartVolume(0.5f)
                    .scaleToStartVolume(0.f)
                    .build();
            checkEqual(TEST_NAME, testRamp, ramp);

            ramp = new VolumeShaper.Configuration.Builder(testRamp)
                    .scaleToStartVolume(0.5f)
                    .scaleToEndVolume(0.f)
                    .scaleToStartVolume(1.f)
                    .invertVolumes()
                    .build();
            checkEqual(TEST_NAME, testRamp, ramp);
        }

        // check that getMaximumCurvePoints() returns the correct value
        final int maxPoints = VolumeShaper.Configuration.getMaximumCurvePoints();

        testBuildRamp(maxPoints); // no exceptions here.

        if (maxPoints < Integer.MAX_VALUE) {
            Log.d(TAG, TEST_NAME + " configuration builder "
                    + "should throw IAE if getMaximumCurvePoints() exceeded");
            assertThrows(IllegalArgumentException.class,
                    () -> { testBuildRamp(maxPoints + 1); });
        }
    } // testVolumeShaperConfigurationBuilder

    @SmallTest
    public void testVolumeShaperConfigurationParcelable() throws Exception {
        final String TEST_NAME = "testVolumeShaperConfigurationParcelable";

        for (VolumeShaper.Configuration config : ALL_STANDARD_RAMPS) {
            assertEquals(TEST_NAME + " no parceled file descriptors",
                    0 /* expected */, config.describeContents());

            final Parcel srcParcel = Parcel.obtain();
            config.writeToParcel(srcParcel, 0 /* flags */);

            final byte[] marshallBuffer = srcParcel.marshall();

            final Parcel dstParcel = Parcel.obtain();
            dstParcel.unmarshall(marshallBuffer, 0 /* offset */, marshallBuffer.length);
            dstParcel.setDataPosition(0);

            final VolumeShaper.Configuration restoredConfig =
                    VolumeShaper.Configuration.CREATOR.createFromParcel(dstParcel);
            assertEquals(TEST_NAME +
                    " marshalled/restored VolumeShaper.Configuration should match",
                    config, restoredConfig);
        }
    } // testVolumeShaperConfigurationParcelable

    @SmallTest
    public void testVolumeShaperOperationParcelable() throws Exception {
        final String TEST_NAME = "testVolumeShaperOperationParcelable";

        for (VolumeShaper.Operation operation : ALL_STANDARD_OPERATIONS) {
            assertEquals(TEST_NAME + " no parceled file descriptors",
                    0 /* expected */, operation.describeContents());

            final Parcel srcParcel = Parcel.obtain();
            operation.writeToParcel(srcParcel, 0 /* flags */);

            final byte[] marshallBuffer = srcParcel.marshall();

            final Parcel dstParcel = Parcel.obtain();
            dstParcel.unmarshall(marshallBuffer, 0 /* offset */, marshallBuffer.length);
            dstParcel.setDataPosition(0);

            final VolumeShaper.Operation restoredOperation =
                    VolumeShaper.Operation.CREATOR.createFromParcel(dstParcel);
            assertEquals(TEST_NAME +
                    " marshalled/restored VolumeShaper.Operation should match",
                    operation, restoredOperation);
        }
    } // testVolumeShaperOperationParcelable

    // This tests that we can't create infinite shapers and cause audioserver
    // to crash due to memory or performance issues.  Typically around 16 app based
    // shapers are allowed by the audio server.
    @SmallTest
    public void testMaximumShapers() {
        final String TEST_NAME = "testMaximumShapers";
        if (!hasAudioOutput()) {
            Log.w(TAG, "AUDIO_OUTPUT feature not found. This system might not have a valid "
                    + "audio output HAL");
            return;
        }

        final int WAY_TOO_MANY_SHAPERS = 1000;

        for (int p = 0; p < PLAYER_TYPES; ++p) {
            try (Player player = createPlayer(p)) {
                final String testName = TEST_NAME + " " + player.name();
                final VolumeShaper[] shapers = new VolumeShaper[WAY_TOO_MANY_SHAPERS];
                int i = 0;
                try {
                    for (; i < shapers.length; ++i) {
                        shapers[i] = player.createVolumeShaper(SILENCE);
                    }
                    fail(testName + " should not be able to create "
                            + shapers.length + " shapers");
                } catch (IllegalStateException ise) {
                    Log.d(TAG, testName + " " + i + " shapers created before failure (OK)");
                }
            }
            // volume shapers close when player closes.
        }
    } // testMaximumShapers

    @LargeTest
    public void testPlayerDuck() throws Exception {
        final String TEST_NAME = "testPlayerDuck";
        if (!hasAudioOutput()) {
            Log.w(TAG, "AUDIO_OUTPUT feature not found. This system might not have a valid "
                    + "audio output HAL");
            return;
        }

        for (int p = 0; p < PLAYER_TYPES; ++p) {
            try (   Player player = createPlayer(p);
                    VolumeShaper volumeShaper = player.createVolumeShaper(SILENCE);
                    ) {
                final String testName = TEST_NAME + " " + player.name();

                Log.d(TAG, testName + " starting");
                player.start();
                Thread.sleep(WARMUP_TIME_MS);

                runDuckTest(testName, volumeShaper);
                runCloseTest(testName, volumeShaper);
            }
        }
    } // testPlayerDuck

    @LargeTest
    public void testPlayerRamp() throws Exception {
        final String TEST_NAME = "testPlayerRamp";
        if (!hasAudioOutput()) {
            Log.w(TAG, "AUDIO_OUTPUT feature not found. This system might not have a valid "
                    + "audio output HAL");
            return;
        }

        for (int p = 0; p < PLAYER_TYPES; ++p) {
            try (   Player player = createPlayer(p);
                    VolumeShaper volumeShaper = player.createVolumeShaper(SILENCE);
                    ) {
                final String testName = TEST_NAME + " " + player.name();

                Log.d(TAG, testName + " starting");
                player.start();
                Thread.sleep(WARMUP_TIME_MS);

                runRampTest(testName, volumeShaper);
                runCloseTest(testName, volumeShaper);
            }
        }
    } // testPlayerRamp

    @LargeTest
    public void testPlayerCornerCase() throws Exception {
        final String TEST_NAME = "testPlayerCornerCase";
        if (!hasAudioOutput()) {
            Log.w(TAG, "AUDIO_OUTPUT feature not found. This system might not have a valid "
                    + "audio output HAL");
            return;
        }

        final VolumeShaper.Configuration config = LINEAR_RAMP;

        for (int p = 0; p < PLAYER_TYPES; ++p) {
            Player player = null;
            VolumeShaper volumeShaper = null;
            try {
                player = createPlayer(p);
                volumeShaper = player.createVolumeShaper(config);
                final String testName = TEST_NAME + " " + player.name();

                runStartIdleTest(testName, volumeShaper, player);
                runRampCornerCaseTest(testName, volumeShaper, config);
                runCloseTest(testName, volumeShaper);

                Log.d(TAG, testName + " recreating VolumeShaper and repeating with pause");
                volumeShaper = player.createVolumeShaper(config);
                player.pause();
                Thread.sleep(100 /* millis */);
                runStartIdleTest(testName, volumeShaper, player);

                // volumeShaper not explicitly closed, will close upon finalize or player close.
                Log.d(TAG, testName + " recreating VolumeShaper and repeating with stop");
                volumeShaper = player.createVolumeShaper(config);
                player.stop();
                Thread.sleep(100 /* millis */);
                runStartIdleTest(testName, volumeShaper, player);

                Log.d(TAG, testName + " closing Player before VolumeShaper");
                player.close();
                runCloseTest2(testName, volumeShaper);
            } finally {
                if (volumeShaper != null) {
                    volumeShaper.close();
                }
                if (player != null) {
                    player.close();
                }
            }
        }
    } // testPlayerCornerCase

    @LargeTest
    public void testPlayerCornerCase2() throws Exception {
        final String TEST_NAME = "testPlayerCornerCase2";
        if (!hasAudioOutput()) {
            Log.w(TAG, "AUDIO_OUTPUT feature not found. This system might not have a valid "
                    + "audio output HAL");
            return;
        }

        final VolumeShaper.Configuration config = LINEAR_RAMP;

        for (int p = 0; p < PLAYER_TYPES; ++p) {
            Player player = null;
            VolumeShaper volumeShaper = null;
            try {
                player = createPlayer(p);
                volumeShaper = player.createVolumeShaper(config);
                final String testName = TEST_NAME + " " + player.name();

                runStartSyncTest(testName, volumeShaper, player);
                runCloseTest(testName, volumeShaper);

                Log.d(TAG, testName + " recreating VolumeShaper and repeating with pause");
                volumeShaper = player.createVolumeShaper(config);
                player.pause();
                Thread.sleep(100 /* millis */);
                runStartSyncTest(testName, volumeShaper, player);

                Log.d(TAG, testName + " closing Player before VolumeShaper");
                player.close();
                runCloseTest2(testName, volumeShaper);
            } finally {
                if (volumeShaper != null) {
                    volumeShaper.close();
                }
                if (player != null) {
                    player.close();
                }
            }
        }
    } // testPlayerCornerCase2

    @FlakyTest
    @LargeTest
    public void testPlayerJoin() throws Exception {
        final String TEST_NAME = "testPlayerJoin";
        if (!hasAudioOutput()) {
            Log.w(TAG, "AUDIO_OUTPUT feature not found. This system might not have a valid "
                    + "audio output HAL");
            return;
        }

        for (int p = 0; p < PLAYER_TYPES; ++p) {
            try (   Player player = createPlayer(p);
                    VolumeShaper volumeShaper = player.createVolumeShaper(SILENCE);
                    ) {
                final String testName = TEST_NAME + " " + player.name();
                volumeShaper.apply(VolumeShaper.Operation.PLAY);
                player.start();
                Thread.sleep(WARMUP_TIME_MS);

                Log.d(TAG, " we join several LINEAR_RAMPS together "
                        + " this effectively is one LINEAR_RAMP (volume increasing).");
                final long durationMs = 10000;
                final long incrementMs = 1000;
                for (long i = 0; i < durationMs; i += incrementMs) {
                    Log.d(TAG, testName + " Play - join " + i);
                    volumeShaper.replace(new VolumeShaper.Configuration.Builder(LINEAR_RAMP)
                                    .setDuration(durationMs - i)
                                    .build(),
                            VolumeShaper.Operation.PLAY, true /* join */);
                    assertEquals(testName + " linear ramp should continue on join",
                            (float)i / durationMs, volumeShaper.getVolume(), 0.05 /* epsilon */);
                    Thread.sleep(incrementMs);
                }
                Log.d(TAG, testName + "volume at max level now (closing player)");
            }
        }
    } // testPlayerJoin

    @LargeTest
    public void testPlayerCubicMonotonic() throws Exception {
        final String TEST_NAME = "testPlayerCubicMonotonic";
        if (!hasAudioOutput()) {
            Log.w(TAG, "AUDIO_OUTPUT feature not found. This system might not have a valid "
                    + "audio output HAL");
            return;
        }

        final VolumeShaper.Configuration configurations[] =
                new VolumeShaper.Configuration[] {
                MONOTONIC_TEST,
                CUBIC_RAMP,
                SCURVE_RAMP,
                SINE_RAMP,
        };

        for (int p = 0; p < PLAYER_TYPES; ++p) {
            try (   Player player = createPlayer(p);
                    VolumeShaper volumeShaper = player.createVolumeShaper(SILENCE);
                    ) {
                final String testName = TEST_NAME + " " + player.name();
                volumeShaper.apply(VolumeShaper.Operation.PLAY);
                player.start();
                Thread.sleep(WARMUP_TIME_MS);

                for (VolumeShaper.Configuration configuration : configurations) {
                    // test configurations known monotonic
                    Log.d(TAG, testName + " starting test");

                    float lastVolume = 0;
                    final long incrementMs = 100;

                    volumeShaper.replace(configuration,
                            VolumeShaper.Operation.PLAY, true /* join */);
                    // monotonicity test
                    for (long i = 0; i < RAMP_TIME_MS; i += incrementMs) {
                        final float volume = volumeShaper.getVolume();
                        assertTrue(testName + " montonic volume should increase "
                                + volume + " >= " + lastVolume,
                                (volume >= lastVolume));
                        lastVolume = volume;
                        Thread.sleep(incrementMs);
                    }
                    Thread.sleep(WARMUP_TIME_MS);
                    lastVolume = volumeShaper.getVolume();
                    assertEquals(testName
                            + " final monotonic value should be 1.f, but is " + lastVolume,
                            1.f, lastVolume, VOLUME_TOLERANCE);

                    Log.d(TAG, "invert");
                    // invert
                    VolumeShaper.Configuration newConfiguration =
                            new VolumeShaper.Configuration.Builder(configuration)
                    .invertVolumes()
                    .build();
                    volumeShaper.replace(newConfiguration,
                            VolumeShaper.Operation.PLAY, true /* join */);
                    // monotonicity test
                    for (long i = 0; i < RAMP_TIME_MS; i += incrementMs) {
                        final float volume = volumeShaper.getVolume();
                        assertTrue(testName + " montonic volume should decrease "
                                + volume + " <= " + lastVolume,
                                (volume <= lastVolume));
                        lastVolume = volume;
                        Thread.sleep(incrementMs);
                    }
                    Thread.sleep(WARMUP_TIME_MS);
                    lastVolume = volumeShaper.getVolume();
                    assertEquals(testName
                            + " final monotonic value should be 0.f, but is " + lastVolume,
                            0.f, lastVolume, VOLUME_TOLERANCE);

                    // invert + reflect
                    Log.d(TAG, "invert and reflect");
                    newConfiguration =
                            new VolumeShaper.Configuration.Builder(configuration)
                    .invertVolumes()
                    .reflectTimes()
                    .build();
                    volumeShaper.replace(newConfiguration,
                            VolumeShaper.Operation.PLAY, true /* join */);
                    // monotonicity test
                    for (long i = 0; i < RAMP_TIME_MS; i += incrementMs) {
                        final float volume = volumeShaper.getVolume();
                        assertTrue(testName + " montonic volume should increase "
                                + volume + " >= " + lastVolume,
                                (volume >= lastVolume - VOLUME_TOLERANCE));
                        lastVolume = volume;
                        Thread.sleep(incrementMs);
                    }
                    Thread.sleep(WARMUP_TIME_MS);
                    lastVolume = volumeShaper.getVolume();
                    assertEquals(testName
                            + " final monotonic value should be 1.f, but is " + lastVolume,
                            1.f, lastVolume, VOLUME_TOLERANCE);

                    // reflect
                    Log.d(TAG, "reflect");
                    newConfiguration =
                            new VolumeShaper.Configuration.Builder(configuration)
                    .reflectTimes()
                    .build();
                    volumeShaper.replace(newConfiguration,
                            VolumeShaper.Operation.PLAY, true /* join */);
                    // monotonicity test
                    for (long i = 0; i < RAMP_TIME_MS; i += incrementMs) {
                        final float volume = volumeShaper.getVolume();
                        assertTrue(testName + " montonic volume should decrease "
                                + volume + " <= " + lastVolume,
                                (volume <= lastVolume));
                        lastVolume = volume;
                        Thread.sleep(incrementMs);
                    }
                    Thread.sleep(WARMUP_TIME_MS);
                    lastVolume = volumeShaper.getVolume();
                    assertEquals(testName
                            + " final monotonic value should be 0.f, but is " + lastVolume,
                            0.f, lastVolume, VOLUME_TOLERANCE);
                }
            }
        }
    } // testPlayerCubicMonotonic

    @LargeTest
    public void testPlayerStepRamp() throws Exception {
        final String TEST_NAME = "testPlayerStepRamp";
        if (!hasAudioOutput()) {
            Log.w(TAG, "AUDIO_OUTPUT feature not found. This system might not have a valid "
                    + "audio output HAL");
            return;
        }

        // We test that the step ramp persists on value until the next control point.
        // The STEP_RAMP has only 2 control points (at time 0.f and at 1.f).
        // It should suddenly jump to full volume at 1.f (full duration).
        // Note: invertVolumes() and reflectTimes() are not symmetric for STEP interpolation;
        // however, VolumeShaper.Operation.REVERSE will behave symmetrically.
        for (int p = 0; p < PLAYER_TYPES; ++p) {
            try (   Player player = createPlayer(p);
                    VolumeShaper volumeShaper = player.createVolumeShaper(SILENCE);
                    ) {
                final String testName = TEST_NAME + " " + player.name();
                volumeShaper.apply(VolumeShaper.Operation.PLAY);
                player.start();
                Thread.sleep(WARMUP_TIME_MS);

                final VolumeShaper.Configuration configuration = STEP_RAMP;
                Log.d(TAG, testName + " starting test (sudden jump to full after "
                        + RAMP_TIME_MS + " milliseconds)");

                volumeShaper.replace(configuration,
                        VolumeShaper.Operation.PLAY, true /* join */);

                Thread.sleep(RAMP_TIME_MS / 2);
                float lastVolume = volumeShaper.getVolume();
                assertEquals(testName
                        + " middle value should be 0.f, but is " + lastVolume,
                        0.f, lastVolume, VOLUME_TOLERANCE);

                Thread.sleep(RAMP_TIME_MS / 2 + 1000);
                lastVolume = volumeShaper.getVolume();
                assertEquals(testName
                        + " final value should be 1.f, but is " + lastVolume,
                        1.f, lastVolume, VOLUME_TOLERANCE);

                Log.d(TAG, "invert (sudden jump to silence after "
                        + RAMP_TIME_MS + " milliseconds)");
                // invert
                VolumeShaper.Configuration newConfiguration =
                        new VolumeShaper.Configuration.Builder(configuration)
                            .invertVolumes()
                            .build();
                volumeShaper.replace(newConfiguration,
                        VolumeShaper.Operation.PLAY, true /* join */);

                Thread.sleep(RAMP_TIME_MS / 2);
                lastVolume = volumeShaper.getVolume();
                assertEquals(testName
                        + " middle value should be 1.f, but is " + lastVolume,
                        1.f, lastVolume, VOLUME_TOLERANCE);

                Thread.sleep(RAMP_TIME_MS / 2 + 1000);
                lastVolume = volumeShaper.getVolume();
                assertEquals(testName
                        + " final value should be 0.f, but is " + lastVolume,
                        0.f, lastVolume, VOLUME_TOLERANCE);

                // invert + reflect
                Log.d(TAG, "invert and reflect (sudden jump to full after "
                        + RAMP_TIME_MS + " milliseconds)");
                newConfiguration =
                        new VolumeShaper.Configuration.Builder(configuration)
                            .invertVolumes()
                            .reflectTimes()
                            .build();
                volumeShaper.replace(newConfiguration,
                        VolumeShaper.Operation.PLAY, true /* join */);

                Thread.sleep(RAMP_TIME_MS / 2);
                lastVolume = volumeShaper.getVolume();
                assertEquals(testName
                        + " middle value should be 0.f, but is " + lastVolume,
                        0.f, lastVolume, VOLUME_TOLERANCE);

                Thread.sleep(RAMP_TIME_MS / 2 + 1000);
                lastVolume = volumeShaper.getVolume();
                assertEquals(testName
                        + " final value should be 1.f, but is " + lastVolume,
                        1.f, lastVolume, VOLUME_TOLERANCE);

                // reflect
                Log.d(TAG, "reflect (sudden jump to silence after "
                        + RAMP_TIME_MS + " milliseconds)");
                newConfiguration =
                        new VolumeShaper.Configuration.Builder(configuration)
                            .reflectTimes()
                            .build();
                volumeShaper.replace(newConfiguration,
                        VolumeShaper.Operation.PLAY, true /* join */);

                Thread.sleep(RAMP_TIME_MS / 2);
                lastVolume = volumeShaper.getVolume();
                assertEquals(testName
                        + " middle value should be 1.f, but is " + lastVolume,
                        1.f, lastVolume, VOLUME_TOLERANCE);

                Thread.sleep(RAMP_TIME_MS / 2 + 1000);
                lastVolume = volumeShaper.getVolume();
                assertEquals(testName
                        + " final value should be 0.f, but is " + lastVolume,
                        0.f, lastVolume, VOLUME_TOLERANCE);

                Log.d(TAG, "reverse (immediate jump to full)");
                volumeShaper.apply(VolumeShaper.Operation.REVERSE);
                Thread.sleep(RAMP_TIME_MS / 2);
                lastVolume = volumeShaper.getVolume();
                assertEquals(testName
                        + " middle value should be 1.f, but is " + lastVolume,
                        1.f, lastVolume, VOLUME_TOLERANCE);

                Thread.sleep(RAMP_TIME_MS / 2 + 1000);
                lastVolume = volumeShaper.getVolume();
                assertEquals(testName
                        + " final value should be 1.f, but is " + lastVolume,
                        1.f, lastVolume, VOLUME_TOLERANCE);
            }
        }
    } // testPlayerStepRamp

    @LargeTest
    public void testPlayerTwoShapers() throws Exception {
        final String TEST_NAME = "testPlayerTwoShapers";
        if (!hasAudioOutput()) {
            Log.w(TAG, "AUDIO_OUTPUT feature not found. This system might not have a valid "
                    + "audio output HAL");
            return;
        }

        final long durationMs = 10000;

        // Ramp configurations go from 0.f up to 1.f, Duck from 1.f to 0.f
        // With the two ramps combined, the audio should rise and then fall.
        final VolumeShaper.Configuration LONG_RAMP =
                new VolumeShaper.Configuration.Builder(VolumeShaper.Configuration.LINEAR_RAMP)
                    .setDuration(durationMs)
                    .build();
        final VolumeShaper.Configuration LONG_DUCK =
                new VolumeShaper.Configuration.Builder(LONG_RAMP)
                    .reflectTimes()
                    .build();

        for (int p = 0; p < PLAYER_TYPES; ++p) {
            try (   Player player = createPlayer(p);
                    VolumeShaper volumeShaperRamp = player.createVolumeShaper(LONG_RAMP);
                    VolumeShaper volumeShaperDuck = player.createVolumeShaper(LONG_DUCK);
                    ) {
                final String testName = TEST_NAME + " " + player.name();

                final float firstVolumeRamp = volumeShaperRamp.getVolume();
                final float firstVolumeDuck = volumeShaperDuck.getVolume();
                assertEquals(testName
                        + " first ramp value should be 0.f, but is " + firstVolumeRamp,
                        0.f, firstVolumeRamp, VOLUME_TOLERANCE);
                assertEquals(testName
                        + " first duck value should be 1.f, but is " + firstVolumeDuck,
                        1.f, firstVolumeDuck, VOLUME_TOLERANCE);
                player.start();

                Thread.sleep(1000);

                final float lastVolumeRamp = volumeShaperRamp.getVolume();
                final float lastVolumeDuck = volumeShaperDuck.getVolume();
                assertEquals(testName
                        + " no-play ramp value should be 0.f, but is " + lastVolumeRamp,
                        0.f, lastVolumeRamp, VOLUME_TOLERANCE);
                assertEquals(testName
                        + " no-play duck value should be 1.f, but is " + lastVolumeDuck,
                        1.f, lastVolumeDuck, VOLUME_TOLERANCE);

                Log.d(TAG, testName + " volume should be silent and start increasing now");

                // we actually start now!
                volumeShaperRamp.apply(VolumeShaper.Operation.PLAY);
                volumeShaperDuck.apply(VolumeShaper.Operation.PLAY);
                Thread.sleep(durationMs / 2);

                Log.d(TAG, testName + " volume should be > 0 and about maximum here");
                final float lastVolumeRamp2 = volumeShaperRamp.getVolume();
                final float lastVolumeDuck2 = volumeShaperDuck.getVolume();
                assertTrue(testName
                        + " last ramp value should be > 0.f " + lastVolumeRamp2,
                        lastVolumeRamp2 > 0.f);
                assertTrue(testName
                        + " last duck value should be < 1.f " + lastVolumeDuck2,
                        lastVolumeDuck2 < 1.f);

                Log.d(TAG, testName + " volume should start decreasing shortly");
                Thread.sleep(durationMs / 2 + 1000);

                Log.d(TAG, testName + " volume should be silent now");
                final float lastVolumeRamp3 = volumeShaperRamp.getVolume();
                final float lastVolumeDuck3 = volumeShaperDuck.getVolume();
                assertEquals(testName
                        + " last ramp value should be 1.f, but is " + lastVolumeRamp3,
                        1.f, lastVolumeRamp3, VOLUME_TOLERANCE);
                assertEquals(testName
                        + " last duck value should be 0.f, but is " + lastVolumeDuck3,
                        0.f, lastVolumeDuck3, VOLUME_TOLERANCE);

                runCloseTest(testName, volumeShaperRamp);
                runCloseTest(testName, volumeShaperDuck);
            }
        }
    } // testPlayerTwoShapers

    // tests that shaper advances in the presence of pause and stop (time based after start).
    @LargeTest
    public void testPlayerRunDuringPauseStop() throws Exception {
        final String TEST_NAME = "testPlayerRunDuringPauseStop";
        if (!hasAudioOutput()) {
            Log.w(TAG, "AUDIO_OUTPUT feature not found. This system might not have a valid "
                    + "audio output HAL");
            return;
        }

        final VolumeShaper.Configuration config = LINEAR_RAMP;

        for (int p = 0; p < PLAYER_TYPES; ++p) {
            for (int pause = 0; pause < 2; ++pause) {

                if ((p == PLAYER_TYPE_MEDIA_PLAYER_NON_OFFLOADED
                        || p == PLAYER_TYPE_MEDIA_PLAYER_OFFLOADED) && pause == 0) {
                    // Do not test stop and MediaPlayer because a
                    // MediaPlayer stop requires prepare before starting.
                    continue;
                }

                try (   Player player = createPlayer(p);
                        VolumeShaper volumeShaper = player.createVolumeShaper(config);
                        ) {
                    final String testName = TEST_NAME + " " + player.name();

                    Log.d(TAG, testName + " starting volume, should ramp up");
                    volumeShaper.apply(VolumeShaper.Operation.PLAY);
                    assertEquals(testName + " volume should be 0.f",
                            0.f, volumeShaper.getVolume(), VOLUME_TOLERANCE);

                    player.start();
                    Thread.sleep(WARMUP_TIME_MS);

                    Log.d(TAG, testName + " applying " + (pause != 0 ? "pause" : "stop"));
                    if (pause == 1) {
                        player.pause();
                    } else {
                        player.stop();
                    }
                    Thread.sleep(RAMP_TIME_MS);

                    Log.d(TAG, testName + " starting again");
                    player.start();
                    Thread.sleep(WARMUP_TIME_MS * 2);

                    Log.d(TAG, testName + " should be full volume");
                    assertEquals(testName + " volume should be 1.f",
                            1.f, volumeShaper.getVolume(), VOLUME_TOLERANCE);
                }
            }
        }
    } // testPlayerRunDuringPauseStop

    // Player should be started before calling (as it is not an argument to method).
    private void runRampTest(String testName, VolumeShaper volumeShaper) throws Exception {
        for (VolumeShaper.Configuration config : ALL_STANDARD_RAMPS) {
            // This replaces with play.
            Log.d(TAG, testName + " Replace + Play (volume should increase)");
            volumeShaper.replace(config, VolumeShaper.Operation.PLAY, false /* join */);
            Thread.sleep(RAMP_TIME_MS / 2);

            // Reverse the direction of the volume shaper curve
            Log.d(TAG, testName + " Reverse (volume should decrease)");
            volumeShaper.apply(VolumeShaper.Operation.REVERSE);
            Thread.sleep(RAMP_TIME_MS / 2 + 1000);

            Log.d(TAG, testName + " Check Volume (silent)");
            assertEquals(testName + " volume should be 0.f",
                    0.f, volumeShaper.getVolume(), VOLUME_TOLERANCE);

            // Forwards
            Log.d(TAG, testName + " Play (volume should increase)");
            volumeShaper.apply(VolumeShaper.Operation.PLAY);
            Thread.sleep(RAMP_TIME_MS + 1000);

            Log.d(TAG, testName + " Check Volume (volume at max)");
            assertEquals(testName + " volume should be 1.f",
                    1.f, volumeShaper.getVolume(), VOLUME_TOLERANCE);

            // Reverse
            Log.d(TAG, testName + " Reverse (volume should decrease)");
            volumeShaper.apply(VolumeShaper.Operation.REVERSE);
            Thread.sleep(RAMP_TIME_MS + 1000);

            Log.d(TAG, testName + " Check Volume (volume should be silent)");
            assertEquals(testName + " volume should be 0.f",
                    0.f, volumeShaper.getVolume(), VOLUME_TOLERANCE);

            // Forwards
            Log.d(TAG, testName + " Play (volume should increase)");
            volumeShaper.apply(VolumeShaper.Operation.PLAY);
            Thread.sleep(RAMP_TIME_MS + 1000);

            // Comment out for headset plug/unplug test
            // Log.d(TAG, testName + " headset check"); Thread.sleep(10000 /* millis */);
            //

            Log.d(TAG, testName + " Check Volume (volume at max)");
            assertEquals(testName + " volume should be 1.f",
                    1.f, volumeShaper.getVolume(), VOLUME_TOLERANCE);

            Log.d(TAG, testName + " done");
        }
    } // runRampTest

    // Player should be started before calling (as it is not an argument to method).
    private void runDuckTest(String testName, VolumeShaper volumeShaper) throws Exception {
        final VolumeShaper.Configuration[] configs = new VolumeShaper.Configuration[] {
                LINEAR_DUCK,
        };

        for (VolumeShaper.Configuration config : configs) {
            Log.d(TAG, testName + " Replace + Reverse (volume at max)");
            // CORNER CASE: When you replace with REVERSE, it stays at the initial point.
            volumeShaper.replace(config, VolumeShaper.Operation.REVERSE, false /* join */);
            Thread.sleep(RAMP_TIME_MS / 2);
            assertEquals(testName + " volume should be 1.f",
                    1.f, volumeShaper.getVolume(), VOLUME_TOLERANCE);

            // CORNER CASE: reverse twice doesn't do anything.
            Thread.sleep(RAMP_TIME_MS / 2);
            Log.d(TAG, testName + " Reverse after reverse (volume at max)");
            volumeShaper.apply(VolumeShaper.Operation.REVERSE);
            assertEquals(testName + " volume should be 1.f",
                    1.f, volumeShaper.getVolume(), VOLUME_TOLERANCE);

            Log.d(TAG, testName + " Duck from start (volume should decrease)");
            volumeShaper.apply(VolumeShaper.Operation.PLAY);
            Thread.sleep(RAMP_TIME_MS * 2);

            Log.d(TAG, testName + " Duck done (volume should be low, 0.2f)");
            assertEquals(testName + " volume should be 0.2f",
                    0.2f, volumeShaper.getVolume(), VOLUME_TOLERANCE);

            Log.d(TAG, testName + " Unduck (volume should increase)");
            volumeShaper.apply(VolumeShaper.Operation.REVERSE);
            Thread.sleep(RAMP_TIME_MS * 2);

            // Comment out for headset plug/unplug test
            // Log.d(TAG, testName + " headset check"); Thread.sleep(10000 /* millis */);
            //
            Log.d(TAG, testName + " Unduck done (volume at max)");
            assertEquals(testName + " volume should be 1.f",
                    1.f, volumeShaper.getVolume(), VOLUME_TOLERANCE);
        }
    } // runDuckTest

    // VolumeShaper should not be started prior to this test; it is replaced
    // in this test.
    private void runRampCornerCaseTest(
            String testName, VolumeShaper volumeShaper, VolumeShaper.Configuration config)
                    throws Exception {
        // ramps start at 0.f
        assertEquals(testName + " volume should be 0.f",
                0.f, volumeShaper.getVolume(), VOLUME_TOLERANCE);

        Log.d(TAG, testName + " Reverse at start (quiet now)");
        // CORNER CASE: When you begin with REVERSE, it stays at the initial point.
        volumeShaper.apply(VolumeShaper.Operation.REVERSE);
        Thread.sleep(RAMP_TIME_MS / 2);
        assertEquals(testName + " volume should be 0.f",
                0.f, volumeShaper.getVolume(), VOLUME_TOLERANCE);

        // CORNER CASE: reverse twice doesn't do anything.
        Thread.sleep(RAMP_TIME_MS / 2);
        Log.d(TAG, testName + " Reverse after reverse (still quiet)");
        volumeShaper.apply(VolumeShaper.Operation.REVERSE);
        assertEquals(testName + " volume should be 0.f",
                0.f, volumeShaper.getVolume(), VOLUME_TOLERANCE);

        Log.d(TAG, testName + " Ramp from start (volume should increase)");
        volumeShaper.apply(VolumeShaper.Operation.PLAY);
        Thread.sleep(RAMP_TIME_MS * 2);

        Log.d(TAG, testName + " Volume persists at maximum 1.f");
        assertEquals(testName + " volume should be 1.f",
                1.f, volumeShaper.getVolume(), VOLUME_TOLERANCE);

        Log.d(TAG, testName + " Reverse ramp (volume should decrease)");
        volumeShaper.apply(VolumeShaper.Operation.REVERSE);
        Thread.sleep(RAMP_TIME_MS / 2);

        // join in REVERSE should freeze
        final float volume = volumeShaper.getVolume();
        Log.d(TAG, testName + " Replace ramp with join in REVERSE (volume steady)");
        volumeShaper.replace(config, VolumeShaper.Operation.REVERSE, true /* join */);

        Thread.sleep(RAMP_TIME_MS / 2);
        // Are we frozen?
        final float volume2 = volumeShaper.getVolume();
        assertEquals(testName + " volume should be the same (volume steady)",
                volume, volume2, JOIN_VOLUME_TOLERANCE);

        // Begin playing
        Log.d(TAG, testName + " Play joined ramp (volume should increase)");
        volumeShaper.apply(VolumeShaper.Operation.PLAY);
        Thread.sleep(RAMP_TIME_MS * 2);

        // Reverse to get back to start of the joined curve.
        Log.d(TAG, testName + " Reverse joined ramp (volume should decrease)");
        volumeShaper.apply(VolumeShaper.Operation.REVERSE);
        Thread.sleep(RAMP_TIME_MS * 2);

        // At this time, we are back to the join point.
        // We check now that the scaling for the join is permanent.
        Log.d(TAG, testName + " Joined ramp at start (volume same as at join)");
        final float volume3 = volumeShaper.getVolume();
        assertEquals(testName + " volume should be same as start for joined ramp",
                volume2, volume3, JOIN_VOLUME_TOLERANCE);
    } // runRampCornerCaseTest

    // volumeShaper is closed in this test.
    private void runCloseTest(String testName, VolumeShaper volumeShaper) throws Exception {
        Log.d(TAG, testName + " closing");
        volumeShaper.close();
        runCloseTest2(testName, volumeShaper);
    } // runCloseTest

    // VolumeShaper should be closed prior to this test.
    private void runCloseTest2(String testName, VolumeShaper volumeShaper) throws Exception {
        // CORNER CASE:
        // VolumeShaper methods should throw ISE after closing.
        Log.d(TAG, testName + " getVolume() after close should throw ISE");
        assertThrows(IllegalStateException.class,
                volumeShaper::getVolume);

        Log.d(TAG, testName + " apply() after close should throw ISE");
        assertThrows(IllegalStateException.class,
                ()->{ volumeShaper.apply(VolumeShaper.Operation.REVERSE); });

        Log.d(TAG, testName + " replace() after close should throw ISE");
        assertThrows(IllegalStateException.class,
                ()->{ volumeShaper.replace(
                        LINEAR_RAMP, VolumeShaper.Operation.PLAY, false /* join */); });

        Log.d(TAG, testName + " closing x2 is OK");
        volumeShaper.close(); // OK to close twice.
        Log.d(TAG, testName + " closing x3 is OK");
        volumeShaper.close(); // OK to close thrice.
    } // runCloseTest2

    // Player should not be started prior to calling (it is started in this test)
    // VolumeShaper should not be started prior to calling (it is not started in this test).
    private void runStartIdleTest(String testName, VolumeShaper volumeShaper, Player player)
            throws Exception {
        Log.d(TAG, testName + " volume after creation or pause doesn't advance (silent now)");
        // CORNER CASE:
        // volumeShaper volume after creation or pause doesn't advance.
        Thread.sleep(WARMUP_TIME_MS);
        assertEquals(testName + " volume should be 0.f",
                0.f, volumeShaper.getVolume(), VOLUME_TOLERANCE);

        player.start();
        Thread.sleep(WARMUP_TIME_MS);

        Log.d(TAG, testName + " volume after player start doesn't advance if play isn't called."
                + " (still silent)");
        // CORNER CASE:
        // volumeShaper volume after creation doesn't or pause doesn't advance even
        // after the player starts.
        Thread.sleep(WARMUP_TIME_MS);
        assertEquals(testName + " volume should be 0.f",
                0.f, volumeShaper.getVolume(), VOLUME_TOLERANCE);
    } // runStartIdleTest

    // Player should not be running prior to calling (it is started in this test).
    // VolumeShaper is also started in this test.
    private void runStartSyncTest(String testName, VolumeShaper volumeShaper, Player player)
            throws Exception {
        Log.d(TAG, testName + " volume after creation or pause doesn't advance "
                + "if player isn't started. (silent now)");
        volumeShaper.apply(VolumeShaper.Operation.PLAY);
        // CORNER CASE:
        // volumeShaper volume after creation or pause doesn't advance
        // even after play is called.
        Thread.sleep(WARMUP_TIME_MS);
        assertEquals(testName + " volume should be 0.f",
                0.f, volumeShaper.getVolume(), VOLUME_TOLERANCE);

        Log.d(TAG, testName + " starting now (volume should increase)");
        player.start();
        Thread.sleep(WARMUP_TIME_MS);

        Log.d(TAG, testName + " volume after player start advances if play is called.");
        // CORNER CASE:
        // Now volume should have advanced since play is called.
        Thread.sleep(WARMUP_TIME_MS);
        assertTrue(testName + " volume should be greater than 0.f",
                volumeShaper.getVolume() > 0.f);
    } // runStartSyncTest
}
