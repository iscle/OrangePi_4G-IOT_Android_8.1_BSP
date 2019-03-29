/*
 * Copyright (C) 2012 The Android Open Source Project
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

package android.view.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.os.SystemClock;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.Choreographer;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class ChoreographerTest {
    private static final long NOMINAL_VSYNC_PERIOD = 16;
    private static final long DELAY_PERIOD = NOMINAL_VSYNC_PERIOD * 5;
    private static final long NANOS_PER_MS = 1000000;
    private static final Object TOKEN = new Object();

    private Choreographer mChoreographer;

    @UiThreadTest
    @Before
    public void setup() {
        mChoreographer = Choreographer.getInstance();
    }

    @Test
    public void testFrameDelay() {
        assertTrue(Choreographer.getFrameDelay() > 0);

        long oldFrameDelay = Choreographer.getFrameDelay();
        long newFrameDelay = oldFrameDelay * 2;
        Choreographer.setFrameDelay(newFrameDelay);
        assertEquals(newFrameDelay, Choreographer.getFrameDelay());

        Choreographer.setFrameDelay(oldFrameDelay);
    }

    @Test
    public void testPostCallbackWithoutDelay() {
        final Runnable addedCallback1 = mock(Runnable.class);
        final Runnable addedCallback2 = mock(Runnable.class);
        final Runnable removedCallback = mock(Runnable.class);
        try {
            // Add and remove a few callbacks.
            mChoreographer.postCallback(
                    Choreographer.CALLBACK_ANIMATION, addedCallback1, null);
            mChoreographer.postCallback(
                    Choreographer.CALLBACK_ANIMATION, addedCallback2, null);
            mChoreographer.postCallback(
                    Choreographer.CALLBACK_ANIMATION, removedCallback, null);
            mChoreographer.removeCallbacks(
                    Choreographer.CALLBACK_ANIMATION, removedCallback, null);

            // We expect the remaining callbacks to have been invoked once.
            verify(addedCallback1, timeout(NOMINAL_VSYNC_PERIOD * 10).times(1)).run();
            verify(addedCallback2, timeout(NOMINAL_VSYNC_PERIOD * 10).times(1)).run();
            verifyZeroInteractions(removedCallback);

            // If we post a callback again, then it should be invoked again.
            mChoreographer.postCallback(
                    Choreographer.CALLBACK_ANIMATION, addedCallback1, null);

            verify(addedCallback1, timeout(NOMINAL_VSYNC_PERIOD * 10).times(2)).run();
            verify(addedCallback2, times(1)).run();
            verifyZeroInteractions(removedCallback);

            // If the token matches, the the callback should be removed.
            mChoreographer.postCallback(
                    Choreographer.CALLBACK_ANIMATION, addedCallback1, null);
            mChoreographer.postCallback(
                    Choreographer.CALLBACK_ANIMATION, removedCallback, TOKEN);
            mChoreographer.removeCallbacks(
                    Choreographer.CALLBACK_ANIMATION, null, TOKEN);
            verify(addedCallback1, timeout(NOMINAL_VSYNC_PERIOD * 10).times(3)).run();
            verifyZeroInteractions(removedCallback);

            // If the action and token matches, then the callback should be removed.
            // If only the token matches, then the callback should not be removed.
            mChoreographer.postCallback(
                    Choreographer.CALLBACK_ANIMATION, addedCallback1, TOKEN);
            mChoreographer.postCallback(
                    Choreographer.CALLBACK_ANIMATION, removedCallback, TOKEN);
            mChoreographer.removeCallbacks(
                    Choreographer.CALLBACK_ANIMATION, removedCallback, TOKEN);
            verify(addedCallback1, timeout(NOMINAL_VSYNC_PERIOD * 10).times(4)).run();
            verifyZeroInteractions(removedCallback);
        } finally {
            mChoreographer.removeCallbacks(
                    Choreographer.CALLBACK_ANIMATION, addedCallback1, null);
            mChoreographer.removeCallbacks(
                    Choreographer.CALLBACK_ANIMATION, addedCallback2, null);
            mChoreographer.removeCallbacks(
                    Choreographer.CALLBACK_ANIMATION, removedCallback, null);
        }
    }

    @Test
    public void testPostCallbackWithDelay() {
        final Runnable addedCallback = mock(Runnable.class);
        final Runnable removedCallback = mock(Runnable.class);
        try {
            // Add and remove a few callbacks.
            mChoreographer.postCallbackDelayed(
                    Choreographer.CALLBACK_ANIMATION, addedCallback, null, DELAY_PERIOD);
            mChoreographer.postCallbackDelayed(
                    Choreographer.CALLBACK_ANIMATION, removedCallback, null, DELAY_PERIOD);
            mChoreographer.removeCallbacks(
                    Choreographer.CALLBACK_ANIMATION, removedCallback, null);

            // Sleep for a couple of frames.
            SystemClock.sleep(NOMINAL_VSYNC_PERIOD * 3);

            // The callbacks should not have been invoked yet because of the delay.
            verifyZeroInteractions(addedCallback);
            verifyZeroInteractions(removedCallback);

            // We expect the remaining callbacks to have been invoked.
            verify(addedCallback, timeout(DELAY_PERIOD * 3).times(1)).run();
            verifyZeroInteractions(removedCallback);

            // If the token matches, the the callback should be removed.
            mChoreographer.postCallbackDelayed(
                    Choreographer.CALLBACK_ANIMATION, addedCallback, null, DELAY_PERIOD);
            mChoreographer.postCallbackDelayed(
                    Choreographer.CALLBACK_ANIMATION, removedCallback, TOKEN, DELAY_PERIOD);
            mChoreographer.removeCallbacks(
                    Choreographer.CALLBACK_ANIMATION, null, TOKEN);
            verify(addedCallback, timeout(DELAY_PERIOD * 3).times(2)).run();
            verifyZeroInteractions(removedCallback);

            // If the action and token matches, then the callback should be removed.
            // If only the token matches, then the callback should not be removed.
            mChoreographer.postCallbackDelayed(
                    Choreographer.CALLBACK_ANIMATION, addedCallback, TOKEN, DELAY_PERIOD);
            mChoreographer.postCallbackDelayed(
                    Choreographer.CALLBACK_ANIMATION, removedCallback, TOKEN, DELAY_PERIOD);
            mChoreographer.removeCallbacks(
                    Choreographer.CALLBACK_ANIMATION, removedCallback, TOKEN);
            verify(addedCallback, timeout(DELAY_PERIOD * 3).times(3)).run();
            verifyZeroInteractions(removedCallback);
        } finally {
            mChoreographer.removeCallbacks(
                    Choreographer.CALLBACK_ANIMATION, addedCallback, null);
            mChoreographer.removeCallbacks(
                    Choreographer.CALLBACK_ANIMATION, removedCallback, null);
        }
    }

    @Test(expected=IllegalArgumentException.class)
    public void testPostNullCallback() {
        mChoreographer.postCallback(Choreographer.CALLBACK_ANIMATION, null, TOKEN);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testPostNullCallbackDelayed() {
        mChoreographer.postCallbackDelayed( Choreographer.CALLBACK_ANIMATION, null, TOKEN,
                DELAY_PERIOD);
    }

    @Test
    public void testPostFrameCallbackWithoutDelay() {
        final Choreographer.FrameCallback addedFrameCallback1 =
                mock(Choreographer.FrameCallback.class);
        final Choreographer.FrameCallback addedFrameCallback2 =
                mock(Choreographer.FrameCallback.class);
        final Choreographer.FrameCallback removedFrameCallback =
                mock(Choreographer.FrameCallback.class);
        try {
            // Add and remove a few callbacks.
            long postTimeNanos = System.nanoTime();
            mChoreographer.postFrameCallback(addedFrameCallback1);
            mChoreographer.postFrameCallback(addedFrameCallback2);
            mChoreographer.postFrameCallback(removedFrameCallback);
            mChoreographer.removeFrameCallback(removedFrameCallback);

            // We expect the remaining callbacks to have been invoked once.
            ArgumentCaptor<Long> frameTimeNanosCaptor1 = ArgumentCaptor.forClass(Long.class);
            ArgumentCaptor<Long> frameTimeNanosCaptor2 = ArgumentCaptor.forClass(Long.class);
            verify(addedFrameCallback1, timeout(NOMINAL_VSYNC_PERIOD * 10).times(1))
                    .doFrame(frameTimeNanosCaptor1.capture());
            verify(addedFrameCallback2, times(1)).doFrame(frameTimeNanosCaptor2.capture());
            verifyZeroInteractions(removedFrameCallback);

            assertTimeDeltaLessThan(frameTimeNanosCaptor1.getValue() - postTimeNanos,
                    NOMINAL_VSYNC_PERIOD * 10 * NANOS_PER_MS);
            assertTimeDeltaLessThan(frameTimeNanosCaptor2.getValue() - postTimeNanos,
                    NOMINAL_VSYNC_PERIOD * 10 * NANOS_PER_MS);
            assertTimeDeltaLessThan(
                    Math.abs(frameTimeNanosCaptor2.getValue() - frameTimeNanosCaptor1.getValue()),
                    NOMINAL_VSYNC_PERIOD * NANOS_PER_MS);

            // If we post a callback again, then it should be invoked again.
            postTimeNanos = System.nanoTime();
            mChoreographer.postFrameCallback(addedFrameCallback1);

            verify(addedFrameCallback1, timeout(NOMINAL_VSYNC_PERIOD * 10).times(2))
                    .doFrame(frameTimeNanosCaptor1.capture());
            verify(addedFrameCallback2, times(1)).doFrame(frameTimeNanosCaptor2.capture());
            verifyZeroInteractions(removedFrameCallback);
            assertTimeDeltaLessThan(frameTimeNanosCaptor1.getAllValues().get(1) - postTimeNanos,
                    NOMINAL_VSYNC_PERIOD * 10 * NANOS_PER_MS);
        } finally {
            mChoreographer.removeFrameCallback(addedFrameCallback1);
            mChoreographer.removeFrameCallback(addedFrameCallback2);
            mChoreographer.removeFrameCallback(removedFrameCallback);
        }
    }

    @Test
    public void testPostFrameCallbackWithDelay() {
        final Choreographer.FrameCallback addedFrameCallback =
                mock(Choreographer.FrameCallback.class);
        final Choreographer.FrameCallback removedFrameCallback =
                mock(Choreographer.FrameCallback.class);
        try {
            // Add and remove a few callbacks.
            long postTimeNanos = System.nanoTime();
            mChoreographer.postFrameCallbackDelayed(addedFrameCallback, DELAY_PERIOD);
            mChoreographer.postFrameCallbackDelayed(removedFrameCallback, DELAY_PERIOD);
            mChoreographer.removeFrameCallback(removedFrameCallback);

            // Sleep for a couple of frames.
            SystemClock.sleep(NOMINAL_VSYNC_PERIOD * 3);

            // The callbacks should not have been invoked yet because of the delay.
            verifyZeroInteractions(addedFrameCallback);
            verifyZeroInteractions(removedFrameCallback);

            // We expect the remaining callbacks to have been invoked.
            ArgumentCaptor<Long> frameTimeNanosCaptor = ArgumentCaptor.forClass(Long.class);
            verify(addedFrameCallback, timeout(DELAY_PERIOD * 3).times(1))
                    .doFrame(frameTimeNanosCaptor.capture());
            verifyZeroInteractions(removedFrameCallback);
            assertTimeDeltaLessThan(frameTimeNanosCaptor.getValue() - postTimeNanos,
                    (NOMINAL_VSYNC_PERIOD * 10 + DELAY_PERIOD) * NANOS_PER_MS);
        } finally {
            mChoreographer.removeFrameCallback(addedFrameCallback);
            mChoreographer.removeFrameCallback(removedFrameCallback);
        }
    }

    private void assertTimeDeltaLessThan(long deltaNanos, long thresholdNanos) {
        if (deltaNanos >= thresholdNanos) {
            fail("Expected time delta less than " + thresholdNanos + " nanos, actually "
                    + " was " + deltaNanos + " nanos.");
        }
    }

    @Test(expected=IllegalArgumentException.class)
    public void testPostNullFrameCallback() {
        mChoreographer.postFrameCallback(null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testPostNullFrameCallbackDelayed() {
        mChoreographer.postFrameCallbackDelayed(null, DELAY_PERIOD);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testRemoveNullFrameCallback() {
        mChoreographer.removeFrameCallback(null);
    }
}
