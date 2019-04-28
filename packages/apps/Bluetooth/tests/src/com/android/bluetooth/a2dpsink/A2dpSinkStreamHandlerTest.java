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

package com.android.bluetooth.a2dpsink;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyFloat;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.os.HandlerThread;
import android.os.Looper;
import android.test.AndroidTestCase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class A2dpSinkStreamHandlerTest extends AndroidTestCase {
    static final int DUCK_PERCENT = 75;
    private HandlerThread mHandlerThread;
    A2dpSinkStreamHandler streamHandler;
    ArgumentCaptor<OnAudioFocusChangeListener> audioFocusChangeListenerArgumentCaptor;

    @Mock Context mockContext;

    @Mock A2dpSinkStateMachine mockA2dpSink;

    @Mock AudioManager mockAudioManager;

    @Mock Resources mockResources;

    @Before
    public void setUp() {
        // Mock the looper
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        mHandlerThread = new HandlerThread("A2dpSinkStreamHandlerTest");
        mHandlerThread.start();

        audioFocusChangeListenerArgumentCaptor =
                ArgumentCaptor.forClass(OnAudioFocusChangeListener.class);
        when(mockContext.getSystemService(Context.AUDIO_SERVICE)).thenReturn(mockAudioManager);
        when(mockContext.getResources()).thenReturn(mockResources);
        when(mockResources.getInteger(anyInt())).thenReturn(DUCK_PERCENT);
        when(mockAudioManager.requestAudioFocus(audioFocusChangeListenerArgumentCaptor.capture(),
                     eq(AudioManager.STREAM_MUSIC), eq(AudioManager.AUDIOFOCUS_GAIN)))
                .thenReturn(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
        when(mockAudioManager.abandonAudioFocus(any())).thenReturn(AudioManager.AUDIOFOCUS_GAIN);
        doNothing().when(mockA2dpSink).informAudioTrackGainNative(anyFloat());
        when(mockContext.getMainLooper()).thenReturn(mHandlerThread.getLooper());

        streamHandler = spy(new A2dpSinkStreamHandler(mockA2dpSink, mockContext));
    }

    @Test
    public void testSrcStart() {
        // Stream started without local play, expect no change in streaming.
        streamHandler.handleMessage(
                streamHandler.obtainMessage(A2dpSinkStreamHandler.SRC_STR_START));
        verify(mockAudioManager, times(0)).requestAudioFocus(any(), anyInt(), anyInt());
        verify(mockA2dpSink, times(0)).informAudioFocusStateNative(1);
        verify(mockA2dpSink, times(0)).informAudioTrackGainNative(1.0f);
    }

    @Test
    public void testSrcStop() {
        // Stream stopped without local play, expect no change in streaming.
        streamHandler.handleMessage(
                streamHandler.obtainMessage(A2dpSinkStreamHandler.SRC_STR_STOP));
        verify(mockAudioManager, times(0)).requestAudioFocus(any(), anyInt(), anyInt());
        verify(mockA2dpSink, times(0)).informAudioFocusStateNative(1);
        verify(mockA2dpSink, times(0)).informAudioTrackGainNative(1.0f);
    }

    @Test
    public void testSnkPlay() {
        // Play was pressed locally, expect streaming to start.
        streamHandler.handleMessage(streamHandler.obtainMessage(A2dpSinkStreamHandler.SNK_PLAY));
        verify(mockAudioManager, times(1)).requestAudioFocus(any(), anyInt(), anyInt());
        verify(mockA2dpSink, times(1)).informAudioFocusStateNative(1);
        verify(mockA2dpSink, times(1)).informAudioTrackGainNative(1.0f);
    }

    @Test
    public void testSnkPause() {
        // Pause was pressed locally, expect streaming to stop.
        streamHandler.handleMessage(streamHandler.obtainMessage(A2dpSinkStreamHandler.SNK_PAUSE));
        verify(mockAudioManager, times(0)).requestAudioFocus(any(), anyInt(), anyInt());
        verify(mockA2dpSink, times(0)).informAudioFocusStateNative(1);
        verify(mockA2dpSink, times(0)).informAudioTrackGainNative(1.0f);
    }

    @Test
    public void testDisconnect() {
        // Remote device was disconnected, expect streaming to stop.
        testSnkPlay();
        streamHandler.handleMessage(streamHandler.obtainMessage(A2dpSinkStreamHandler.DISCONNECT));
        verify(mockAudioManager, times(1)).abandonAudioFocus(any());
        verify(mockA2dpSink, times(1)).informAudioFocusStateNative(0);
    }

    @Test
    public void testSrcPlay() {
        // Play was pressed remotely, expect no streaming due to lack of audio focus.
        streamHandler.handleMessage(streamHandler.obtainMessage(A2dpSinkStreamHandler.SRC_PLAY));
        verify(mockAudioManager, times(0)).requestAudioFocus(any(), anyInt(), anyInt());
        verify(mockA2dpSink, times(0)).informAudioFocusStateNative(1);
        verify(mockA2dpSink, times(0)).informAudioTrackGainNative(1.0f);
    }

    @Test
    public void testSrcPause() {
        // Play was pressed locally, expect streaming to start.
        streamHandler.handleMessage(streamHandler.obtainMessage(A2dpSinkStreamHandler.SRC_PLAY));
        verify(mockAudioManager, times(0)).requestAudioFocus(any(), anyInt(), anyInt());
        verify(mockA2dpSink, times(0)).informAudioFocusStateNative(1);
        verify(mockA2dpSink, times(0)).informAudioTrackGainNative(1.0f);
    }

    @Test
    public void testFocusGain() {
        // Focus was gained, expect streaming to resume.
        testSnkPlay();
        streamHandler.handleMessage(streamHandler.obtainMessage(
                A2dpSinkStreamHandler.AUDIO_FOCUS_CHANGE, AudioManager.AUDIOFOCUS_GAIN));
        verify(mockAudioManager, times(1)).requestAudioFocus(any(), anyInt(), anyInt());
        verify(mockA2dpSink, times(2)).informAudioFocusStateNative(1);
        verify(mockA2dpSink, times(2)).informAudioTrackGainNative(1.0f);
    }

    @Test
    public void testFocusTransientMayDuck() {
        // TransientMayDuck focus was gained, expect audio stream to duck.
        testSnkPlay();
        streamHandler.handleMessage(
                streamHandler.obtainMessage(A2dpSinkStreamHandler.AUDIO_FOCUS_CHANGE,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK));
        verify(mockA2dpSink, times(1)).informAudioTrackGainNative(DUCK_PERCENT / 100.0f);
    }

    @Test
    public void testFocusLostTransient() {
        // Focus was lost transiently, expect streaming to stop.
        testSnkPlay();
        streamHandler.handleMessage(streamHandler.obtainMessage(
                A2dpSinkStreamHandler.AUDIO_FOCUS_CHANGE, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT));
        verify(mockAudioManager, times(0)).abandonAudioFocus(any());
        verify(mockA2dpSink, times(1)).informAudioFocusStateNative(0);
    }

    @Test
    public void testFocusLost() {
        // Focus was lost permanently, expect streaming to stop.
        testSnkPlay();
        streamHandler.handleMessage(streamHandler.obtainMessage(
                A2dpSinkStreamHandler.AUDIO_FOCUS_CHANGE, AudioManager.AUDIOFOCUS_LOSS));
        verify(mockAudioManager, times(1)).abandonAudioFocus(any());
        verify(mockA2dpSink, times(1)).informAudioFocusStateNative(0);
    }
}
