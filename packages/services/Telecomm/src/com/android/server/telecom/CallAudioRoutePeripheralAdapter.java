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
 * limitations under the License
 */

package com.android.server.telecom;

import com.android.server.telecom.bluetooth.BluetoothRouteManager;

/**
 * A class that acts as a listener to things that could change call audio routing, namely
 * bluetooth status, wired headset status, and dock status.
 */
public class CallAudioRoutePeripheralAdapter implements WiredHeadsetManager.Listener,
        DockManager.Listener, BluetoothRouteManager.BluetoothStateListener {

    private final CallAudioRouteStateMachine mCallAudioRouteStateMachine;
    private final BluetoothRouteManager mBluetoothRouteManager;

    public CallAudioRoutePeripheralAdapter(
            CallAudioRouteStateMachine callAudioRouteStateMachine,
            BluetoothRouteManager bluetoothManager,
            WiredHeadsetManager wiredHeadsetManager,
            DockManager dockManager) {
        mCallAudioRouteStateMachine = callAudioRouteStateMachine;
        mBluetoothRouteManager = bluetoothManager;

        mBluetoothRouteManager.setListener(this);
        wiredHeadsetManager.addListener(this);
        dockManager.addListener(this);
    }

    public boolean isBluetoothAudioOn() {
        return mBluetoothRouteManager.isBluetoothAudioConnectedOrPending();
    }

    @Override
    public void onBluetoothStateChange(int oldState, int newState) {
        switch (oldState) {
            case BluetoothRouteManager.BLUETOOTH_DISCONNECTED:
            case BluetoothRouteManager.BLUETOOTH_UNINITIALIZED:
                switch (newState) {
                    case BluetoothRouteManager.BLUETOOTH_DEVICE_CONNECTED:
                    case BluetoothRouteManager.BLUETOOTH_AUDIO_CONNECTED:
                        mCallAudioRouteStateMachine.sendMessageWithSessionInfo(
                                CallAudioRouteStateMachine.CONNECT_BLUETOOTH);
                        break;
                }
                break;
            case BluetoothRouteManager.BLUETOOTH_DEVICE_CONNECTED:
                switch (newState) {
                    case BluetoothRouteManager.BLUETOOTH_DISCONNECTED:
                        mCallAudioRouteStateMachine.sendMessageWithSessionInfo(
                                CallAudioRouteStateMachine.DISCONNECT_BLUETOOTH);
                        break;
                    case BluetoothRouteManager.BLUETOOTH_AUDIO_CONNECTED:
                        mCallAudioRouteStateMachine.sendMessageWithSessionInfo(
                                CallAudioRouteStateMachine.SWITCH_BLUETOOTH);
                        break;
                }
                break;
            case BluetoothRouteManager.BLUETOOTH_AUDIO_CONNECTED:
            case BluetoothRouteManager.BLUETOOTH_AUDIO_PENDING:
                switch (newState) {
                    case BluetoothRouteManager.BLUETOOTH_DISCONNECTED:
                        mCallAudioRouteStateMachine.sendMessageWithSessionInfo(
                                CallAudioRouteStateMachine.DISCONNECT_BLUETOOTH);
                        break;
                    case BluetoothRouteManager.BLUETOOTH_DEVICE_CONNECTED:
                        mCallAudioRouteStateMachine.sendMessageWithSessionInfo(
                                CallAudioRouteStateMachine.BT_AUDIO_DISCONNECT);
                        break;
                }
                break;
        }
    }
    /**
      * Updates the audio route when the headset plugged in state changes. For example, if audio is
      * being routed over speakerphone and a headset is plugged in then switch to wired headset.
      */
    @Override
    public void onWiredHeadsetPluggedInChanged(boolean oldIsPluggedIn, boolean newIsPluggedIn) {
        if (!oldIsPluggedIn && newIsPluggedIn) {
            mCallAudioRouteStateMachine.sendMessageWithSessionInfo(
                    CallAudioRouteStateMachine.CONNECT_WIRED_HEADSET);
        } else if (oldIsPluggedIn && !newIsPluggedIn){
            mCallAudioRouteStateMachine.sendMessageWithSessionInfo(
                    CallAudioRouteStateMachine.DISCONNECT_WIRED_HEADSET);
        }
    }

    @Override
    public void onDockChanged(boolean isDocked) {
        mCallAudioRouteStateMachine.sendMessageWithSessionInfo(
                isDocked ? CallAudioRouteStateMachine.CONNECT_DOCK
                        : CallAudioRouteStateMachine.DISCONNECT_DOCK
        );
    }
}
