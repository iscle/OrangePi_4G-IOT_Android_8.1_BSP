/* * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.telecom.tests;

import android.media.AudioManager;
import android.media.ToneGenerator;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.server.telecom.Call;
import com.android.server.telecom.DtmfLocalTonePlayer;
import com.android.server.telecom.R;

import org.mockito.Mock;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DtmfLocalTonePlayerTest extends TelecomTestCase {
    private static final int TIMEOUT = 2000;
    @Mock DtmfLocalTonePlayer.ToneGeneratorProxy mToneProxy;
    @Mock Call mCall;

    DtmfLocalTonePlayer mPlayer;

    public void setUp() throws Exception {
        super.setUp();
        mContext = mComponentContextFixture.getTestDouble().getApplicationContext();
        mPlayer = new DtmfLocalTonePlayer(mToneProxy);
        when(mCall.getContext()).thenReturn(mContext);
    }

    @SmallTest
    public void testSupportedStart() {
        when(mContext.getResources().getBoolean(R.bool.allow_local_dtmf_tones)).thenReturn(true);
        when(mToneProxy.isPresent()).thenReturn(true);
        mPlayer.onForegroundCallChanged(null, mCall);
        waitForHandlerAction(mPlayer.getHandler(), TIMEOUT);
        verify(mToneProxy).create();
    }

    @SmallTest
    public void testUnsupportedStart() {
        when(mContext.getResources().getBoolean(R.bool.allow_local_dtmf_tones)).thenReturn(false);
        when(mToneProxy.isPresent()).thenReturn(true);
        mPlayer.onForegroundCallChanged(null, mCall);
        waitForHandlerAction(mPlayer.getHandler(), TIMEOUT);
        verify(mToneProxy, never()).create();
    }

    @SmallTest
    public void testPlayToneWhenUninitialized() {
        when(mContext.getResources().getBoolean(R.bool.allow_local_dtmf_tones)).thenReturn(false);
        when(mToneProxy.isPresent()).thenReturn(false);
        mPlayer.onForegroundCallChanged(null, mCall);
        mPlayer.playTone(mCall, '9');
        waitForHandlerAction(mPlayer.getHandler(), TIMEOUT);
        verify(mToneProxy, never()).startTone(anyInt(), anyInt());
    }

    @SmallTest
    public void testPlayToneWhenInitialized() {
        when(mContext.getResources().getBoolean(R.bool.allow_local_dtmf_tones)).thenReturn(true);
        when(mToneProxy.isPresent()).thenReturn(true);
        mPlayer.onForegroundCallChanged(null, mCall);
        mPlayer.playTone(mCall, '9');
        waitForHandlerAction(mPlayer.getHandler(), TIMEOUT);
        verify(mToneProxy).startTone(eq(ToneGenerator.TONE_DTMF_9), eq(-1));
    }

    @SmallTest
    public void testStopToneWhenUninitialized() {
        when(mContext.getResources().getBoolean(R.bool.allow_local_dtmf_tones)).thenReturn(false);
        when(mToneProxy.isPresent()).thenReturn(false);
        mPlayer.onForegroundCallChanged(null, mCall);
        mPlayer.stopTone(mCall);
        waitForHandlerAction(mPlayer.getHandler(), TIMEOUT);
        verify(mToneProxy, never()).stopTone();
    }

    @SmallTest
    public void testStopToneWhenInitialized() {
        when(mContext.getResources().getBoolean(R.bool.allow_local_dtmf_tones)).thenReturn(true);
        when(mToneProxy.isPresent()).thenReturn(true);
        mPlayer.onForegroundCallChanged(null, mCall);
        mPlayer.stopTone(mCall);
        waitForHandlerAction(mPlayer.getHandler(), TIMEOUT);
        verify(mToneProxy).stopTone();
    }

    @SmallTest
    public void testProperTeardown() {
        when(mContext.getResources().getBoolean(R.bool.allow_local_dtmf_tones)).thenReturn(true);
        when(mToneProxy.isPresent()).thenReturn(true);
        mPlayer.onForegroundCallChanged(null, mCall);
        mPlayer.onForegroundCallChanged(mCall, null);
        waitForHandlerAction(mPlayer.getHandler(), TIMEOUT);
        verify(mToneProxy).release();
    }
}
