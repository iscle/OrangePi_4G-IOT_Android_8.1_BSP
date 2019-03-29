/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.content.Context;
import android.media.audiofx.AudioEffect;
import android.media.AudioFormat;
import android.media.audiofx.Virtualizer;
import android.os.Looper;
import android.test.AndroidTestCase;
import android.util.Log;

import android.media.cts.R;

import java.util.Arrays;

public class VirtualizerTest extends PostProcTestBase {

    private String TAG = "VirtualizerTest";
    private final static short TEST_STRENGTH = 500;
    private final static short TEST_STRENGTH2 = 1000;
    private final static float STRENGTH_TOLERANCE = 1.1f;  // 10%
    private final static int MAX_LOOPER_WAIT_COUNT = 10;

    private Virtualizer mVirtualizer = null;
    private Virtualizer mVirtualizer2 = null;
    private ListenerThread mEffectListenerLooper = null;

    //-----------------------------------------------------------------
    // VIRTUALIZER TESTS:
    //----------------------------------

    //-----------------------------------------------------------------
    // 0 - constructor
    //----------------------------------

    //Test case 0.0: test constructor and release
    public void test0_0ConstructorAndRelease() throws Exception {
        if (!isVirtualizerAvailable()) {
            return;
        }

        Virtualizer eq = null;
        try {
            eq = new Virtualizer(0, getSessionId());
            try {
                assertTrue(" invalid effect ID", (eq.getId() != 0));
            } catch (IllegalStateException e) {
                fail("Virtualizer not initialized");
            }
        } catch (IllegalArgumentException e) {
            fail("Virtualizer not found");
        } catch (UnsupportedOperationException e) {
            fail("Effect library not loaded");
        } finally {
            if (eq != null) {
                eq.release();
            }
        }
    }


    //-----------------------------------------------------------------
    // 1 - get/set parameters
    //----------------------------------

    //Test case 1.0: test strength
    public void test1_0Strength() throws Exception {
        if (!isVirtualizerAvailable()) {
            return;
        }

        getVirtualizer(getSessionId());
        try {
            if (mVirtualizer.getStrengthSupported()) {
                short strength = mVirtualizer.getRoundedStrength();
                strength = (strength == TEST_STRENGTH) ? TEST_STRENGTH2 : TEST_STRENGTH;
                mVirtualizer.setStrength((short)strength);
                short strength2 = mVirtualizer.getRoundedStrength();
                // allow STRENGTH_TOLERANCE difference between set strength and rounded strength
                assertTrue("got incorrect strength",
                        ((float)strength2 > (float)strength / STRENGTH_TOLERANCE) &&
                        ((float)strength2 < (float)strength * STRENGTH_TOLERANCE));
            } else {
                short strength = mVirtualizer.getRoundedStrength();
                assertTrue("got incorrect strength", strength >= 0 && strength <= 1000);
            }
        } catch (IllegalArgumentException e) {
            fail("Bad parameter value");
        } catch (UnsupportedOperationException e) {
            fail("get parameter() rejected");
        } catch (IllegalStateException e) {
            fail("get parameter() called in wrong state");
        } finally {
            releaseVirtualizer();
        }
    }

    //Test case 1.1: test properties
    public void test1_1Properties() throws Exception {
        if (!isVirtualizerAvailable()) {
            return;
        }

        getVirtualizer(getSessionId());
        try {
            Virtualizer.Settings settings = mVirtualizer.getProperties();
            String str = settings.toString();
            settings = new Virtualizer.Settings(str);

            short strength = settings.strength;
            if (mVirtualizer.getStrengthSupported()) {
                strength = (strength == TEST_STRENGTH) ? TEST_STRENGTH2 : TEST_STRENGTH;
            }
            settings.strength = strength;
            mVirtualizer.setProperties(settings);
            settings = mVirtualizer.getProperties();

            if (mVirtualizer.getStrengthSupported()) {
                // allow STRENGTH_TOLERANCE difference between set strength and rounded strength
                assertTrue("got incorrect strength",
                        ((float)settings.strength > (float)strength / STRENGTH_TOLERANCE) &&
                        ((float)settings.strength < (float)strength * STRENGTH_TOLERANCE));
            }
        } catch (IllegalArgumentException e) {
            fail("Bad parameter value");
        } catch (UnsupportedOperationException e) {
            fail("get parameter() rejected");
        } catch (IllegalStateException e) {
            fail("get parameter() called in wrong state");
        } finally {
            releaseVirtualizer();
        }
    }

    //Test case 1.2: test setStrength() throws exception after release
    public void test1_2SetStrengthAfterRelease() throws Exception {
        if (!isVirtualizerAvailable()) {
            return;
        }

        getVirtualizer(getSessionId());
        mVirtualizer.release();
        try {
            mVirtualizer.setStrength(TEST_STRENGTH);
        } catch (IllegalStateException e) {
            // test passed
        } finally {
            releaseVirtualizer();
        }
    }

    //-----------------------------------------------------------------
    // 2 - Effect enable/disable
    //----------------------------------

    //Test case 2.0: test setEnabled() and getEnabled() in valid state
    public void test2_0SetEnabledGetEnabled() throws Exception {
        if (!isVirtualizerAvailable()) {
            return;
        }

        getVirtualizer(getSessionId());
        try {
            mVirtualizer.setEnabled(true);
            assertTrue(" invalid state from getEnabled", mVirtualizer.getEnabled());
            mVirtualizer.setEnabled(false);
            assertFalse(" invalid state to getEnabled", mVirtualizer.getEnabled());
        } catch (IllegalStateException e) {
            fail("setEnabled() in wrong state");
        } finally {
            releaseVirtualizer();
        }
    }

    //Test case 2.1: test setEnabled() throws exception after release
    public void test2_1SetEnabledAfterRelease() throws Exception {
        if (!isVirtualizerAvailable()) {
            return;
        }

        getVirtualizer(getSessionId());
        mVirtualizer.release();
        try {
            mVirtualizer.setEnabled(true);
        } catch (IllegalStateException e) {
            // test passed
        } finally {
            releaseVirtualizer();
        }
    }

    //-----------------------------------------------------------------
    // 3 priority and listeners
    //----------------------------------

    //Test case 3.0: test control status listener
    public void test3_0ControlStatusListener() throws Exception {
        if (!isVirtualizerAvailable()) {
            return;
        }

        synchronized(mLock) {
            mHasControl = true;
            mInitialized = false;
            createListenerLooper(true, false, false);
            waitForLooperInitialization_l();

            getVirtualizer(mSession);
            int looperWaitCount = MAX_LOOPER_WAIT_COUNT;
            while (mHasControl && (looperWaitCount-- > 0)) {
                try {
                    mLock.wait();
                } catch(Exception e) {
                }
            }
            terminateListenerLooper();
            releaseVirtualizer();
        }
        assertFalse("effect control not lost by effect1", mHasControl);
    }

    //Test case 3.1: test enable status listener
    public void test3_1EnableStatusListener() throws Exception {
        if (!isVirtualizerAvailable()) {
            return;
        }

        synchronized(mLock) {
            mInitialized = false;
            createListenerLooper(false, true, false);
            waitForLooperInitialization_l();

            mVirtualizer2.setEnabled(true);
            mIsEnabled = true;
            getVirtualizer(mSession);
            mVirtualizer.setEnabled(false);
            int looperWaitCount = MAX_LOOPER_WAIT_COUNT;
            while (mIsEnabled && (looperWaitCount-- > 0)) {
                try {
                    mLock.wait();
                } catch(Exception e) {
                }
            }
            terminateListenerLooper();
            releaseVirtualizer();
        }
        assertFalse("enable status not updated", mIsEnabled);
    }

    //Test case 3.2: test parameter changed listener
    public void test3_2ParameterChangedListener() throws Exception {
        if (!isVirtualizerAvailable()) {
            return;
        }

        synchronized(mLock) {
            mInitialized = false;
            createListenerLooper(false, false, true);
            waitForLooperInitialization_l();

            getVirtualizer(mSession);
            mChangedParameter = -1;
            mVirtualizer.setStrength(TEST_STRENGTH);

            int looperWaitCount = MAX_LOOPER_WAIT_COUNT;
            while ((mChangedParameter == -1) && (looperWaitCount-- > 0)) {
                try {
                    mLock.wait();
                } catch(Exception e) {
                }
            }
            terminateListenerLooper();
            releaseVirtualizer();
        }
        assertEquals("parameter change not received",
                Virtualizer.PARAM_STRENGTH, mChangedParameter);
    }

    //-----------------------------------------------------------------
    // 4 virtualizer capabilities
    //----------------------------------

    //Test case 4.0: test virtualization format / mode query: at least one of the following
    //   combinations must be supported, otherwise the effect doesn't really qualify as
    //   a virtualizer: AudioFormat.CHANNEL_OUT_STEREO or the quad and 5.1 side/back variants,
    //   in VIRTUALIZATION_MODE_BINAURAL or VIRTUALIZATION_MODE_TRANSAURAL
    public void test4_0FormatModeQuery() throws Exception {
        if (!isVirtualizerAvailable()) {
            return;
        }
        getVirtualizer(getSessionId());
        try {
            boolean isAtLeastOneConfigSupported = false;
            boolean isConfigSupported = false;

            // testing combinations of input channel mask and virtualization mode
            for (int m = 0 ; m < VIRTUALIZATION_MODES.length ; m++) {
                for (int i = 0 ; i < CHANNEL_MASKS.length ; i++) {
                    isConfigSupported = mVirtualizer.canVirtualize(CHANNEL_MASKS[i],
                            VIRTUALIZATION_MODES[m]);
                    isAtLeastOneConfigSupported |= isConfigSupported;
                    // optional logging
                    String channelMask = Integer.toHexString(CHANNEL_MASKS[i]).toUpperCase();
                    String nativeChannelMask =
                            Integer.toHexString(CHANNEL_MASKS[i] >> 2).toUpperCase();
                    Log.d(TAG, "content channel mask: 0x" + channelMask + " (native 0x"
                            + nativeChannelMask
                            + ") mode: " + VIRTUALIZATION_MODES[m]
                            + " supported=" + isConfigSupported);
                }
            }

            assertTrue("no valid configuration supported", isAtLeastOneConfigSupported);
        } catch (IllegalArgumentException e) {
            fail("bad parameter value");
        } catch (UnsupportedOperationException e) {
            fail("command not supported");
        } catch (IllegalStateException e) {
            fail("command called in wrong state");
        } finally {
            releaseVirtualizer();
        }
    }

    //Test case 4.1: test that the capabilities reported by Virtualizer.canVirtualize(int,int)
    //   matches those returned by Virtualizer.getSpeakerAngles(int, int, int[])
    public void test4_1SpeakerAnglesCapaMatchesFormatModeCapa() throws Exception {
        if (!isVirtualizerAvailable()) {
            return;
        }
        getVirtualizer(getSessionId());
        try {
            // 3: see size requirement in Virtualizer.getSpeakerAngles(int, int, int[])
            // 6: for number of channels of 5.1 masks in CHANNEL_MASKS
            int[] angles = new int[3*6];
            for (int m = 0 ; m < VIRTUALIZATION_MODES.length ; m++) {
                for (int i = 0 ; i < CHANNEL_MASKS.length ; i++) {
                    Arrays.fill(angles,AudioFormat.CHANNEL_INVALID);
                    boolean canVirtualize = mVirtualizer.canVirtualize(CHANNEL_MASKS[i],
                            VIRTUALIZATION_MODES[m]);
                    boolean canGetAngles = mVirtualizer.getSpeakerAngles(CHANNEL_MASKS[i],
                            VIRTUALIZATION_MODES[m], angles);
                    assertTrue("mismatch capability between canVirtualize() and getSpeakerAngles()",
                            canVirtualize == canGetAngles);
                    if(canGetAngles) {
                        //check if the number of angles matched the expected number of channels for
                        //each CHANNEL_MASKS
                        int expectedChannelCount = CHANNEL_MASKS_CHANNEL_COUNT[i];
                        for(int k=0; k<expectedChannelCount; k++) {
                            int speakerIdentification = angles[k*3];
                            assertTrue("found unexpected speaker identification or channel count",
                                    speakerIdentification !=AudioFormat.CHANNEL_INVALID );
                        }
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            fail("bad parameter value");
        } catch (UnsupportedOperationException e) {
            fail("command not supported");
        } catch (IllegalStateException e) {
            fail("command called in wrong state");
        } finally {
            releaseVirtualizer();
        }
    }

    //Test case 4.2: test forcing virtualization mode: at least binaural or transaural must be
    //   supported
    public void test4_2VirtualizationMode() throws Exception {
        if (!isVirtualizerAvailable()) {
            return;
        }
        getVirtualizer(getSessionId());
        try {
            mVirtualizer.setEnabled(true);
            assertTrue("invalid state from getEnabled", mVirtualizer.getEnabled());
            // testing binaural
            boolean binauralSupported =
                    mVirtualizer.forceVirtualizationMode(Virtualizer.VIRTUALIZATION_MODE_BINAURAL);
            // testing transaural
            boolean transauralSupported = mVirtualizer
                    .forceVirtualizationMode(Virtualizer.VIRTUALIZATION_MODE_TRANSAURAL);
            // testing at least one supported
            assertTrue("doesn't support binaural nor transaural",
                    binauralSupported || transauralSupported);
        } catch (IllegalArgumentException e) {
            fail("bad parameter value");
        } catch (UnsupportedOperationException e) {
            fail("command not supported");
        } catch (IllegalStateException e) {
            fail("command called in wrong state");
        } finally {
            releaseVirtualizer();
        }
    }

    //Test case 4.3: test disabling virtualization maps to VIRTUALIZATION_MODE_OFF
    public void test4_3DisablingVirtualizationOff() throws Exception {
        if (!isVirtualizerAvailable()) {
            return;
        }
        getVirtualizer(getSessionId());
        try {
            mVirtualizer.setEnabled(false);
            assertFalse("invalid state from getEnabled", mVirtualizer.getEnabled());
            int virtMode = mVirtualizer.getVirtualizationMode();
            assertTrue("disabled virtualization isn't reported as OFF",
                    virtMode == Virtualizer.VIRTUALIZATION_MODE_OFF);
        } catch (IllegalArgumentException e) {
            fail("bad parameter value");
        } catch (UnsupportedOperationException e) {
            fail("command not supported");
        } catch (IllegalStateException e) {
            fail("command called in wrong state");
        } finally {
            releaseVirtualizer();
        }
    }

    //Test case 4.4: test forcing virtualization mode to AUTO
    public void test4_4VirtualizationModeAuto() throws Exception {
        if (!isVirtualizerAvailable()) {
            return;
        }
        getVirtualizer(getSessionId());
        try {
            mVirtualizer.setEnabled(true);
            assertTrue("invalid state from getEnabled", mVirtualizer.getEnabled());
            boolean forceRes =
                    mVirtualizer.forceVirtualizationMode(Virtualizer.VIRTUALIZATION_MODE_AUTO);
            assertTrue("can't set virtualization to AUTO", forceRes);

        } catch (IllegalArgumentException e) {
            fail("bad parameter value");
        } catch (UnsupportedOperationException e) {
            fail("command not supported");
        } catch (IllegalStateException e) {
            fail("command called in wrong state");
        } finally {
            releaseVirtualizer();
        }
    }

    //Test case 4.5: test for consistent capabilities if virtualizer is enabled or disabled
    public void test4_5ConsistentCapabilitiesWithEnabledDisabled() throws Exception {
        if (!isVirtualizerAvailable()) {
            return;
        }
        getVirtualizer(getSessionId());
        try {
            // 3: see size requirement in Virtualizer.getSpeakerAngles(int, int, int[])
            // 6: for number of channels of 5.1 masks in CHANNEL_MASKS
            int[] angles = new int[3*6];
            boolean[][] values = new boolean[VIRTUALIZATION_MODES.length][CHANNEL_MASKS.length];
            mVirtualizer.setEnabled(true);
            assertTrue("invalid state from getEnabled", mVirtualizer.getEnabled());
            for (int m = 0 ; m < VIRTUALIZATION_MODES.length ; m++) {
                for (int i = 0 ; i < CHANNEL_MASKS.length ; i++) {
                    Arrays.fill(angles,AudioFormat.CHANNEL_INVALID);
                    boolean canVirtualize = mVirtualizer.canVirtualize(CHANNEL_MASKS[i],
                            VIRTUALIZATION_MODES[m]);
                    boolean canGetAngles = mVirtualizer.getSpeakerAngles(CHANNEL_MASKS[i],
                            VIRTUALIZATION_MODES[m], angles);
                    assertTrue("mismatch capability between canVirtualize() and getSpeakerAngles()",
                            canVirtualize == canGetAngles);
                    values[m][i] = canVirtualize;
                }
            }

            mVirtualizer.setEnabled(false);
            assertTrue("invalid state from getEnabled", !mVirtualizer.getEnabled());
            for (int m = 0 ; m < VIRTUALIZATION_MODES.length ; m++) {
                for (int i = 0 ; i < CHANNEL_MASKS.length ; i++) {
                    Arrays.fill(angles,AudioFormat.CHANNEL_INVALID);
                    boolean canVirtualize = mVirtualizer.canVirtualize(CHANNEL_MASKS[i],
                            VIRTUALIZATION_MODES[m]);
                    boolean canGetAngles = mVirtualizer.getSpeakerAngles(CHANNEL_MASKS[i],
                            VIRTUALIZATION_MODES[m], angles);
                    assertTrue("mismatch capability between canVirtualize() and getSpeakerAngles()",
                            canVirtualize == canGetAngles);
                    assertTrue("mismatch capability between enabled and disabled virtualizer",
                            canVirtualize == values[m][i]);
                }
            }

        } catch (IllegalArgumentException e) {
            fail("bad parameter value");
        } catch (UnsupportedOperationException e) {
            fail("command not supported");
        } catch (IllegalStateException e) {
            fail("command called in wrong state");
        } finally {
            releaseVirtualizer();
        }
    }

    //-----------------------------------------------------------------
    // private data
    //----------------------------------
    // channel masks to test at input of virtualizer
    private static final int[] CHANNEL_MASKS = {
            AudioFormat.CHANNEL_OUT_STEREO, //2 channels
            AudioFormat.CHANNEL_OUT_QUAD, //4 channels
            //AudioFormat.CHANNEL_OUT_QUAD_SIDE (definition is not public)
            (AudioFormat.CHANNEL_OUT_FRONT_LEFT | AudioFormat.CHANNEL_OUT_FRONT_RIGHT |
                    AudioFormat.CHANNEL_OUT_SIDE_LEFT | AudioFormat.CHANNEL_OUT_SIDE_RIGHT), //4 channels
            AudioFormat.CHANNEL_OUT_5POINT1, //6 channels
            //AudioFormat.CHANNEL_OUT_5POINT1_SIDE (definition is not public)
            (AudioFormat.CHANNEL_OUT_FRONT_LEFT | AudioFormat.CHANNEL_OUT_FRONT_RIGHT |
                    AudioFormat.CHANNEL_OUT_FRONT_CENTER |
                    AudioFormat.CHANNEL_OUT_LOW_FREQUENCY |
                    AudioFormat.CHANNEL_OUT_SIDE_LEFT | AudioFormat.CHANNEL_OUT_SIDE_RIGHT) //6 channels
    };

    private static final int[] CHANNEL_MASKS_CHANNEL_COUNT = {
        2,
        4,
        4,
        6,
        6
    };

    private static final int[] VIRTUALIZATION_MODES = {
            Virtualizer.VIRTUALIZATION_MODE_BINAURAL,
            Virtualizer.VIRTUALIZATION_MODE_TRANSAURAL
    };

    //-----------------------------------------------------------------
    // private methods
    //----------------------------------

    private void getVirtualizer(int session) {
         if (mVirtualizer == null || session != mSession) {
             if (session != mSession && mVirtualizer != null) {
                 mVirtualizer.release();
                 mVirtualizer = null;
             }
             try {
                mVirtualizer = new Virtualizer(0, session);
                mSession = session;
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "getVirtualizer() Virtualizer not found exception: "+e);
            } catch (UnsupportedOperationException e) {
                Log.e(TAG, "getVirtualizer() Effect library not loaded exception: "+e);
            }
         }
         assertNotNull("could not create mVirtualizer", mVirtualizer);
    }

    private void releaseVirtualizer() {
        if (mVirtualizer != null) {
            mVirtualizer.release();
            mVirtualizer = null;
        }
    }

    private void waitForLooperInitialization_l() {
        int looperWaitCount = MAX_LOOPER_WAIT_COUNT;
        while (!mInitialized && (looperWaitCount-- > 0)) {
            try {
                mLock.wait();
            } catch(Exception e) {
            }
        }
        assertTrue(mInitialized);
    }

    // Initializes the virtualizer listener looper
    class ListenerThread extends Thread {
        boolean mControl;
        boolean mEnable;
        boolean mParameter;

        public ListenerThread(boolean control, boolean enable, boolean parameter) {
            super();
            mControl = control;
            mEnable = enable;
            mParameter = parameter;
        }

        public void cleanUp() {
            if (mVirtualizer2 != null) {
                mVirtualizer2.setControlStatusListener(null);
                mVirtualizer2.setEnableStatusListener(null);
                mVirtualizer2.setParameterListener(
                        (Virtualizer.OnParameterChangeListener)null);
            }
        }
    }

    private void createListenerLooper(boolean control, boolean enable, boolean parameter) {
        mEffectListenerLooper = new ListenerThread(control, enable, parameter) {
            @Override
            public void run() {
                // Set up a looper
                Looper.prepare();

                // Save the looper so that we can terminate this thread
                // after we are done with it.
                mLooper = Looper.myLooper();

                mSession = getSessionId();
                mVirtualizer2 = new Virtualizer(0, mSession);

                synchronized(mLock) {
                    if (mControl) {
                        mVirtualizer2.setControlStatusListener(
                                new AudioEffect.OnControlStatusChangeListener() {
                            public void onControlStatusChange(
                                    AudioEffect effect, boolean controlGranted) {
                                synchronized(mLock) {
                                    if (effect == mVirtualizer2) {
                                        mHasControl = controlGranted;
                                        mLock.notify();
                                    }
                                }
                            }
                        });
                    }
                    if (mEnable) {
                        mVirtualizer2.setEnableStatusListener(
                                new AudioEffect.OnEnableStatusChangeListener() {
                            public void onEnableStatusChange(AudioEffect effect, boolean enabled) {
                                synchronized(mLock) {
                                    if (effect == mVirtualizer2) {
                                        mIsEnabled = enabled;
                                        mLock.notify();
                                    }
                                }
                            }
                        });
                    }
                    if (mParameter) {
                        mVirtualizer2.setParameterListener(new Virtualizer.OnParameterChangeListener() {
                            public void onParameterChange(Virtualizer effect, int status,
                                    int param, short value)
                            {
                                synchronized(mLock) {
                                    if (effect == mVirtualizer2) {
                                        mChangedParameter = param;
                                        mLock.notify();
                                    }
                                }
                            }
                        });
                    }

                    mInitialized = true;
                    mLock.notify();
                }
                Looper.loop();  // Blocks forever until Looper.quit() is called.
            }
        };
        mEffectListenerLooper.start();
    }

    // Terminates the listener looper thread.
    private void terminateListenerLooper() {
        if (mEffectListenerLooper != null) {
            mEffectListenerLooper.cleanUp();
            if (mLooper != null) {
                mLooper.quit();
                mLooper = null;
            }
            try {
                mEffectListenerLooper.join();
            } catch(InterruptedException e) {
            }
            mEffectListenerLooper = null;
        }
        if (mVirtualizer2 != null) {
            mVirtualizer2.release();
            mVirtualizer2 = null;
        }
    }

}
