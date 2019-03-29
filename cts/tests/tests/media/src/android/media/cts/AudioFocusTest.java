/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.os.Handler;
import android.os.HandlerThread;

import com.android.compatibility.common.util.CtsAndroidTestCase;

public class AudioFocusTest extends CtsAndroidTestCase {
    private static final String TAG = "AudioFocusTest";

    private static final int TEST_TIMING_TOLERANCE_MS = 100;

    private static final AudioAttributes ATTR_DRIVE_DIR = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build();
    private static final AudioAttributes ATTR_MEDIA = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build();


    public void testInvalidAudioFocusRequestDelayNoListener() throws Exception {
        AudioFocusRequest req = null;
        Exception ex = null;
        try {
            req = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAcceptsDelayedFocusGain(true).build();
        } catch (Exception e) {
            // expected
            ex = e;
        }
        assertNotNull("No exception was thrown for an invalid build", ex);
        assertEquals("Wrong exception thrown", ex.getClass(), IllegalStateException.class);
        assertNull("Shouldn't be able to create delayed request without listener", req);
    }

    public void testInvalidAudioFocusRequestPauseOnDuckNoListener() throws Exception {
        AudioFocusRequest req = null;
        Exception ex = null;
        try {
            req = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setWillPauseWhenDucked(true).build();
        } catch (Exception e) {
            // expected
            ex = e;
        }
        assertNotNull("No exception was thrown for an invalid build", ex);
        assertEquals("Wrong exception thrown", ex.getClass(), IllegalStateException.class);
        assertNull("Shouldn't be able to create pause-on-duck request without listener", req);
    }

    public void testAudioFocusRequestBuilderDefault() throws Exception {
        final AudioFocusRequest reqDefaults =
                new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).build();
        assertEquals("Focus gain differs", AudioManager.AUDIOFOCUS_GAIN,
                reqDefaults.getFocusGain());
        assertEquals("Listener differs", null, reqDefaults.getOnAudioFocusChangeListener());
        assertEquals("Handler differs", null, reqDefaults.getOnAudioFocusChangeListenerHandler());
        assertEquals("Duck behavior differs", false, reqDefaults.willPauseWhenDucked());
        assertEquals("Delayed focus differs", false, reqDefaults.acceptsDelayedFocusGain());
    }


    public void testAudioFocusRequestCopyBuilder() throws Exception {
        final FocusChangeListener focusListener = new FocusChangeListener();
        final int focusGain = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK;
        final AudioFocusRequest reqToCopy =
                new AudioFocusRequest.Builder(focusGain)
                .setAudioAttributes(ATTR_DRIVE_DIR)
                .setOnAudioFocusChangeListener(focusListener)
                .setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(true)
                .build();

        AudioFocusRequest newReq = new AudioFocusRequest.Builder(reqToCopy).build();
        assertEquals("AudioAttributes differ", ATTR_DRIVE_DIR, newReq.getAudioAttributes());
        assertEquals("Listener differs", focusListener, newReq.getOnAudioFocusChangeListener());
        assertEquals("Focus gain differs", focusGain, newReq.getFocusGain());
        assertEquals("Duck behavior differs", true, newReq.willPauseWhenDucked());
        assertEquals("Delayed focus differs", true, newReq.acceptsDelayedFocusGain());

        newReq = new AudioFocusRequest.Builder(reqToCopy)
                .setWillPauseWhenDucked(false)
                .setFocusGain(AudioManager.AUDIOFOCUS_GAIN)
                .build();
        assertEquals("AudioAttributes differ", ATTR_DRIVE_DIR, newReq.getAudioAttributes());
        assertEquals("Listener differs", focusListener, newReq.getOnAudioFocusChangeListener());
        assertEquals("Focus gain differs", AudioManager.AUDIOFOCUS_GAIN, newReq.getFocusGain());
        assertEquals("Duck behavior differs", false, newReq.willPauseWhenDucked());
        assertEquals("Delayed focus differs", true, newReq.acceptsDelayedFocusGain());
    }

    public void testNullListenerHandlerNpe() throws Exception {
        final AudioFocusRequest.Builder afBuilder =
                new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN);
        try {
            afBuilder.setOnAudioFocusChangeListener(null);
            fail("no NPE when setting a null listener");
        } catch (NullPointerException e) {
        }

        final HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        final Handler h = new Handler(handlerThread.getLooper());
        final AudioFocusRequest.Builder afBuilderH =
                new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN);
        try {
            afBuilderH.setOnAudioFocusChangeListener(null, h);
            fail("no NPE when setting a null listener with non-null Handler");
        } catch (NullPointerException e) {
        }

        final AudioFocusRequest.Builder afBuilderL =
                new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN);
        try {
            afBuilderL.setOnAudioFocusChangeListener(new FocusChangeListener(), null);
            fail("no NPE when setting a non-null listener with null Handler");
        } catch (NullPointerException e) {
        }
    }

    public void testAudioFocusRequestGainLoss() throws Exception {
        final AudioAttributes[] attributes = { ATTR_DRIVE_DIR, ATTR_MEDIA };
        doTestTwoPlayersGainLoss(AudioManager.AUDIOFOCUS_GAIN, attributes, false /*no handler*/);
    }

    public void testAudioFocusRequestGainLossHandler() throws Exception {
        final AudioAttributes[] attributes = { ATTR_DRIVE_DIR, ATTR_MEDIA };
        doTestTwoPlayersGainLoss(AudioManager.AUDIOFOCUS_GAIN, attributes, true /*with handler*/);
    }


    public void testAudioFocusRequestGainLossTransient() throws Exception {
        final AudioAttributes[] attributes = { ATTR_DRIVE_DIR, ATTR_MEDIA };
        doTestTwoPlayersGainLoss(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT, attributes,
                false /*no handler*/);
    }

    public void testAudioFocusRequestGainLossTransientHandler() throws Exception {
        final AudioAttributes[] attributes = { ATTR_DRIVE_DIR, ATTR_MEDIA };
        doTestTwoPlayersGainLoss(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT, attributes,
                true /*with handler*/);
    }

    public void testAudioFocusRequestGainLossTransientDuck() throws Exception {
        final AudioAttributes[] attributes = { ATTR_DRIVE_DIR, ATTR_MEDIA };
        doTestTwoPlayersGainLoss(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, attributes,
                false /*no handler*/);
    }

    public void testAudioFocusRequestGainLossTransientDuckHandler() throws Exception {
        final AudioAttributes[] attributes = { ATTR_DRIVE_DIR, ATTR_MEDIA };
        doTestTwoPlayersGainLoss(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, attributes,
                true /*with handler*/);
    }

    /**
     * Test focus request and abandon between two focus owners
     * @param gainType focus gain of the focus owner on top (== 2nd focus requester)
     */
    private void doTestTwoPlayersGainLoss(int gainType, AudioAttributes[] attributes,
            boolean useHandlerInListener) throws Exception {
        final int NB_FOCUS_OWNERS = 2;
        if (NB_FOCUS_OWNERS != attributes.length) {
            throw new IllegalArgumentException("Invalid test: invalid number of attributes");
        }
        final AudioFocusRequest[] focusRequests = new AudioFocusRequest[NB_FOCUS_OWNERS];
        final FocusChangeListener[] focusListeners = new FocusChangeListener[NB_FOCUS_OWNERS];
        final int[] focusGains = { AudioManager.AUDIOFOCUS_GAIN, gainType };
        int expectedLoss = 0;
        switch (gainType) {
            case AudioManager.AUDIOFOCUS_GAIN:
                expectedLoss = AudioManager.AUDIOFOCUS_LOSS;
                break;
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                expectedLoss = AudioManager.AUDIOFOCUS_LOSS_TRANSIENT;
                break;
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                expectedLoss = AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK;
                break;
            default:
                fail("invalid focus gain used in test");
        }
        final AudioManager am = new AudioManager(getContext());

        final Handler h;
        if (useHandlerInListener) {
            HandlerThread handlerThread = new HandlerThread(TAG);
            handlerThread.start();
            h = new Handler(handlerThread.getLooper());
        } else {
            h = null;
        }

        try {
            for (int i = 0 ; i < NB_FOCUS_OWNERS ; i++) {
                focusListeners[i] = new FocusChangeListener();
                if (h != null) {
                    focusRequests[i] = new AudioFocusRequest.Builder(focusGains[i])
                            .setAudioAttributes(attributes[i])
                            .setOnAudioFocusChangeListener(focusListeners[i], h /*handler*/)
                            .build();
                } else {
                    focusRequests[i] = new AudioFocusRequest.Builder(focusGains[i])
                            .setAudioAttributes(attributes[i])
                            .setOnAudioFocusChangeListener(focusListeners[i])
                            .build();
                }
            }

            // focus owner 0 requests focus with GAIN,
            // then focus owner 1 requests focus with gainType
            // then 1 abandons focus, then 0 abandons focus
            int res = am.requestAudioFocus(focusRequests[0]);
            assertEquals("1st focus request failed",
                    AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
            res = am.requestAudioFocus(focusRequests[1]);
            assertEquals("2nd focus request failed", AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
            Thread.sleep(TEST_TIMING_TOLERANCE_MS);
            assertEquals("Focus loss not dispatched", expectedLoss,
                    focusListeners[0].getFocusChangeAndReset());
            res = am.abandonAudioFocusRequest(focusRequests[1]);
            assertEquals("1st abandon failed", AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
            focusRequests[1] = null;
            Thread.sleep(TEST_TIMING_TOLERANCE_MS);
            assertEquals("Focus gain not dispatched", AudioManager.AUDIOFOCUS_GAIN,
                    focusListeners[0].getFocusChangeAndReset());
            res = am.abandonAudioFocusRequest(focusRequests[0]);
            assertEquals("2nd abandon failed", AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
            focusRequests[0] = null;
        }
        finally {
            for (int i = 0 ; i < NB_FOCUS_OWNERS ; i++) {
                if (focusRequests[i] != null) {
                    am.abandonAudioFocusRequest(focusRequests[i]);
                }
            }
            if (h != null) {
                h.getLooper().quit();
            }
        }
    }

    private static class FocusChangeListener implements OnAudioFocusChangeListener {
        private final Object mLock = new Object();
        private int mFocusChange = AudioManager.AUDIOFOCUS_NONE;

        int getFocusChangeAndReset() {
            final int change;
            synchronized (mLock) {
                change = mFocusChange;
                mFocusChange = AudioManager.AUDIOFOCUS_NONE;
            }
            return change;
        }

        @Override
        public void onAudioFocusChange(int focusChange) {
            synchronized (mLock) {
                mFocusChange = focusChange;
            }
        }
    }
}