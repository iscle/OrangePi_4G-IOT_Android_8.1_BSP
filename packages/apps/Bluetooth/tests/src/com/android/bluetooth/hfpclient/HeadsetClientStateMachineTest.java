package com.android.bluetooth.hfpclient;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.test.AndroidTestCase;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;

import com.android.bluetooth.btservice.AdapterService;

import static org.mockito.Mockito.*;

import org.mockito.ArgumentCaptor;

public class HeadsetClientStateMachineTest extends AndroidTestCase {
    private BluetoothAdapter mAdapter = null;

    @Override
    protected void setUp() throws Exception {
        AdapterService inst = mock(AdapterService.class);
        assertTrue(inst != null);
        mAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    // Test that default state is disconnected
    public void testDefaultDisconnectedState() {
        HeadsetClientService mockService = mock(HeadsetClientService.class);
        AudioManager mockAudioManager = mock(AudioManager.class);

        when(mockService.getSystemService(Context.AUDIO_SERVICE)).thenReturn(mockAudioManager);

        HeadsetClientStateMachine mockSM = new HeadsetClientStateMachine(
            mockService, getContext().getMainLooper());
        assertEquals(
            mockSM.getConnectionState((BluetoothDevice) null), BluetoothProfile.STATE_DISCONNECTED);
    }

    // Test that an incoming connection with low priority is rejected
    public void testIncomingPriorityReject() {
        HeadsetClientService mockService = mock(HeadsetClientService.class);
        AudioManager mockAudioManager = mock(AudioManager.class);
        BluetoothDevice device = mAdapter.getRemoteDevice("00:01:02:03:04:05");

        when(mockService.getSystemService(Context.AUDIO_SERVICE)).thenReturn(mockAudioManager);

        HeadsetClientStateMachine mockSM = new HeadsetClientStateMachine(
            mockService, getContext().getMainLooper());
        mockSM.start();

        // Return false for priority.
        when(mockService.getPriority(any(BluetoothDevice.class))).thenReturn(
            BluetoothProfile.PRIORITY_OFF);

        // Inject an event for when incoming connection is requested
        StackEvent connStCh =
            new StackEvent(StackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        connStCh.valueInt = HeadsetClientHalConstants.CONNECTION_STATE_CONNECTED;
        connStCh.valueInt2 = 0;
        connStCh.valueInt3 = 0;
        connStCh.device = device;
        mockSM.sendMessage(StackEvent.STACK_EVENT, connStCh);

        // Verify that no connection state broadcast is executed
        verify(mockService, never()).sendBroadcast(any(Intent.class), anyString());
        // Check we are in disconnected state still.
        assertTrue(mockSM.getCurrentState() instanceof HeadsetClientStateMachine.Disconnected);
    }

    // Test that an incoming connection with high priority is accepted
    public void testIncomingPriorityAccept() {
        HeadsetClientService mockService = mock(HeadsetClientService.class);
        AudioManager mockAudioManager = mock(AudioManager.class);
        BluetoothDevice device = mAdapter.getRemoteDevice("00:01:02:03:04:05");

        when(mockService.getSystemService(Context.AUDIO_SERVICE)).thenReturn(mockAudioManager);
        // Set a valid volume
        when(mockAudioManager.getStreamVolume(anyInt())).thenReturn(2);
        when(mockAudioManager.getStreamMaxVolume(anyInt())).thenReturn(10);
        when(mockAudioManager.getStreamMinVolume(anyInt())).thenReturn(1);


        HeadsetClientStateMachine mockSM = new HeadsetClientStateMachine(
            mockService, getContext().getMainLooper());
        mockSM.start();

        // Return false for priority.
        when(mockService.getPriority(any(BluetoothDevice.class))).thenReturn(
            BluetoothProfile.PRIORITY_ON);

        // Inject an event for when incoming connection is requested
        StackEvent connStCh =
            new StackEvent(StackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        connStCh.valueInt = HeadsetClientHalConstants.CONNECTION_STATE_CONNECTED;
        connStCh.valueInt2 = 0;
        connStCh.valueInt3 = 0;
        connStCh.device = device;
        mockSM.sendMessage(StackEvent.STACK_EVENT, connStCh);

        // Verify that one connection state broadcast is executed
        ArgumentCaptor<Intent> intentArgument1 = ArgumentCaptor.forClass(Intent.class);
        verify(mockService,
            timeout(1000)).sendBroadcast(intentArgument1.capture(), anyString());
        assertEquals(BluetoothProfile.STATE_CONNECTING,
            intentArgument1.getValue().getIntExtra(BluetoothProfile.EXTRA_STATE, -1));

        // Check we are in connecting state now.
        assertTrue(mockSM.getCurrentState() instanceof HeadsetClientStateMachine.Connecting);

        // Send a message to trigger SLC connection
        StackEvent slcEvent =
            new StackEvent(StackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        slcEvent.valueInt = HeadsetClientHalConstants.CONNECTION_STATE_SLC_CONNECTED;
        slcEvent.valueInt2 = HeadsetClientHalConstants.PEER_FEAT_ECS;
        slcEvent.valueInt3 = 0;
        slcEvent.device = device;
        mockSM.sendMessage(StackEvent.STACK_EVENT, slcEvent);

        // Verify that one connection state broadcast is executed
        ArgumentCaptor<Intent> intentArgument2 = ArgumentCaptor.forClass(Intent.class);
        verify(mockService,
            timeout(1000).times(2)).sendBroadcast(intentArgument2.capture(), anyString());
        assertEquals(BluetoothProfile.STATE_CONNECTED,
            intentArgument2.getValue().getIntExtra(BluetoothProfile.EXTRA_STATE, -1));
        // Check we are in connecting state now.
        assertTrue(mockSM.getCurrentState() instanceof HeadsetClientStateMachine.Connected);
    }

    // Test that an incoming connection that times out
    public void testIncomingTimeout() {
        HeadsetClientService mockService = mock(HeadsetClientService.class);
        AudioManager mockAudioManager = mock(AudioManager.class);
        BluetoothDevice device = mAdapter.getRemoteDevice("00:01:02:03:04:05");

        when(mockService.getSystemService(Context.AUDIO_SERVICE)).thenReturn(mockAudioManager);
        // Set a valid volume
        when(mockAudioManager.getStreamVolume(anyInt())).thenReturn(2);
        when(mockAudioManager.getStreamMaxVolume(anyInt())).thenReturn(10);
        when(mockAudioManager.getStreamMinVolume(anyInt())).thenReturn(1);


        HeadsetClientStateMachine mockSM = new HeadsetClientStateMachine(
            mockService, getContext().getMainLooper());
        mockSM.start();

        // Return false for priority.
        when(mockService.getPriority(any(BluetoothDevice.class))).thenReturn(
            BluetoothProfile.PRIORITY_ON);

        // Inject an event for when incoming connection is requested
        StackEvent connStCh =
            new StackEvent(StackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        connStCh.valueInt = HeadsetClientHalConstants.CONNECTION_STATE_CONNECTED;
        connStCh.valueInt2 = 0;
        connStCh.valueInt3 = 0;
        connStCh.device = device;
        mockSM.sendMessage(StackEvent.STACK_EVENT, connStCh);

        // Verify that one connection state broadcast is executed
        ArgumentCaptor<Intent> intentArgument1 = ArgumentCaptor.forClass(Intent.class);
        verify(mockService, timeout(1000)).sendBroadcast(intentArgument1.capture(), anyString());
        assertEquals(BluetoothProfile.STATE_CONNECTING,
            intentArgument1.getValue().getIntExtra(BluetoothProfile.EXTRA_STATE, -1));

        // Check we are in connecting state now.
        assertTrue(mockSM.getCurrentState() instanceof HeadsetClientStateMachine.Connecting);

        // Verify that one connection state broadcast is executed
        ArgumentCaptor<Intent> intentArgument2 = ArgumentCaptor.forClass(Intent.class);
        verify(mockService,
            timeout(HeadsetClientStateMachine.CONNECTING_TIMEOUT_MS * 2).times(2)).sendBroadcast(
            intentArgument2.capture(), anyString());
        assertEquals(BluetoothProfile.STATE_DISCONNECTED,
            intentArgument2.getValue().getIntExtra(BluetoothProfile.EXTRA_STATE, -1));

        // Check we are in connecting state now.
        assertTrue(mockSM.getCurrentState() instanceof HeadsetClientStateMachine.Disconnected);
    }
}
