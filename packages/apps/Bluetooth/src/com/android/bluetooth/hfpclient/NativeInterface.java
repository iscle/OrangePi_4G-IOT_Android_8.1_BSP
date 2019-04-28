/*
 * Copyright (c) 2017 The Android Open Source Project
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

/*
 * Defines the native inteface that is used by state machine/service to either or receive messages
 * from the native stack. This file is registered for the native methods in corresponding CPP file.
 */
package com.android.bluetooth.hfpclient;

import com.android.bluetooth.Utils;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.util.Log;

class NativeInterface {
    private static String TAG = "NativeInterface";
    private static boolean DBG = false;

    NativeInterface() {}

    // Native methods that call into the JNI interface
    static native void classInitNative();
    static native void initializeNative();
    static native void cleanupNative();
    static native boolean connectNative(byte[] address);
    static native boolean disconnectNative(byte[] address);
    static native boolean connectAudioNative(byte[] address);
    static native boolean disconnectAudioNative(byte[] address);
    static native boolean startVoiceRecognitionNative(byte[] address);
    static native boolean stopVoiceRecognitionNative(byte[] address);
    static native boolean setVolumeNative(byte[] address, int volumeType, int volume);
    static native boolean dialNative(byte[] address, String number);
    static native boolean dialMemoryNative(byte[] address, int location);
    static native boolean handleCallActionNative(byte[] address, int action, int index);
    static native boolean queryCurrentCallsNative(byte[] address);
    static native boolean queryCurrentOperatorNameNative(byte[] address);
    static native boolean retrieveSubscriberInfoNative(byte[] address);
    static native boolean sendDtmfNative(byte[] address, byte code);
    static native boolean requestLastVoiceTagNumberNative(byte[] address);
    static native boolean sendATCmdNative(byte[] address, int atCmd, int val1,
            int val2, String arg);

    private BluetoothDevice getDevice(byte[] address) {
        return BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
    }

    // Callbacks from the native back into the java framework. All callbacks are routed via the
    // Service which will disambiguate which state machine the message should be routed through.
    private void onConnectionStateChanged(int state, int peer_feat, int chld_feat, byte[] address) {
        StackEvent event = new StackEvent(StackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        event.valueInt = state;
        event.valueInt2 = peer_feat;
        event.valueInt3 = chld_feat;
        event.device = getDevice(address);
        // BluetoothAdapter.getDefaultAdapter().getRemoteDevice(Utils.getAddressStringFromByte(address));
        if (DBG) {
            Log.d(TAG, "Device addr "  + event.device.getAddress() + " State " + state);
        }
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.w(TAG, "Ignoring message because service not available: " + event);
        }
    }

    private void onAudioStateChanged(int state, byte[] address) {
        StackEvent event = new StackEvent(StackEvent.EVENT_TYPE_AUDIO_STATE_CHANGED);
        event.valueInt = state;
        event.device = getDevice(address);
        if (DBG) {
            Log.d(TAG, "onAudioStateChanged: address " + address + " event "  + event);
        }
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.w(TAG, "onAudioStateChanged: Ignoring message because service not available: " + event);
        }
    }

    private void onVrStateChanged(int state) {
        Log.w(TAG, "onVrStateChanged not supported");
    }

    private void onNetworkState(int state, byte[] address) {
        StackEvent event = new StackEvent(StackEvent.EVENT_TYPE_NETWORK_STATE);
        event.valueInt = state;
        event.device = getDevice(address);
        if (DBG) {
            Log.d(TAG, "onVrStateChanged: address " + address + " event "  + event);
        }

        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.w(TAG, "onVrStateChanged: Ignoring message because service not available: " + event);
        }
    }

    private void onNetworkRoaming(int state, byte[] address) {
        StackEvent event = new StackEvent(StackEvent.EVENT_TYPE_ROAMING_STATE);
        event.valueInt = state;
        event.device = getDevice(address);
        if (DBG) {
            Log.d(TAG, "onNetworkRoaming: incoming: " + event);
        }
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.w(TAG, "onNetworkRoaming: Ignoring message because service not available: " + event);
        }
    }

    private void onNetworkSignal(int signal, byte[] address) {
        StackEvent event = new StackEvent(StackEvent.EVENT_TYPE_NETWORK_SIGNAL);
        event.valueInt = signal;
        event.device = getDevice(address);
        if (DBG) {
            Log.d(TAG, "onNetworkSignal: address " + address + " event "  + event);
        }
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.w(TAG, "onNetworkSignal: Ignoring message because service not available: " + event);
        }
    }

    private void onBatteryLevel(int level, byte[] address) {
        StackEvent event = new StackEvent(StackEvent.EVENT_TYPE_BATTERY_LEVEL);
        event.valueInt = level;
        event.device = getDevice(address);
        if (DBG) {
            Log.d(TAG, "onBatteryLevel: address " + address + " event "  + event);
        }
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.w(TAG, "onBatteryLevel: Ignoring message because service not available: " + event);
        }
    }

    private void onCurrentOperator(String name, byte[] address) {
        StackEvent event = new StackEvent(StackEvent.EVENT_TYPE_OPERATOR_NAME);
        event.valueString = name;
        event.device = getDevice(address);
        if (DBG) {
            Log.d(TAG, "onCurrentOperator: address " + address + " event "  + event);
        }
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.w(TAG, "onCurrentOperator: Ignoring message because service not available: " + event);
        }
    }

    private void onCall(int call, byte[] address) {
        StackEvent event = new StackEvent(StackEvent.EVENT_TYPE_CALL);
        event.valueInt = call;
        event.device = getDevice(address);
        if (DBG) {
            Log.d(TAG, "onCall: address " + address + " event "  + event);
        }
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.w(TAG, "onCall: Ignoring message because service not available: " + event);
        }
    }

    /**
     * CIEV (Call indicators) notifying if call(s) are getting set up.
     *
     * Values include:
     * 0 - No current call is in setup
     * 1 - Incoming call process ongoing
     * 2 - Outgoing call process ongoing
     * 3 - Remote party being alerted for outgoing call
     */
    private void onCallSetup(int callsetup, byte[] address) {
        StackEvent event = new StackEvent(StackEvent.EVENT_TYPE_CALLSETUP);
        event.valueInt = callsetup;
        event.device = getDevice(address);
        if (DBG) {
            Log.d(TAG, "onCallSetup: addr " + address + " device" + event.device);
            Log.d(TAG, "onCallSetup: address " + address + " event "  + event);
        }
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.w(TAG, "onCallSetup: Ignoring message because service not available: " + event);
        }
    }

    /**
     * CIEV (Call indicators) notifying call held states.
     *
     * Values include:
     * 0 - No calls held
     * 1 - Call is placed on hold or active/held calls wapped (The AG has both an ACTIVE and HELD
     * call)
     * 2 - Call on hold, no active call
     */
    private void onCallHeld(int callheld, byte[] address) {
        StackEvent event = new StackEvent(StackEvent.EVENT_TYPE_CALLHELD);
        event.valueInt = callheld;
        event.device = getDevice(address);
        if (DBG) {
            Log.d(TAG, "onCallHeld: address " + address + " event "  + event);
        }
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.w(TAG, "onCallHeld: Ignoring message because service not available: " + event);
        }
    }

    private void onRespAndHold(int resp_and_hold, byte[] address) {
        StackEvent event = new StackEvent(StackEvent.EVENT_TYPE_RESP_AND_HOLD);
        event.valueInt = resp_and_hold;
        event.device = getDevice(address);
        if (DBG) {
            Log.d(TAG, "onRespAndHold: address " + address + " event "  + event);
        }
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.w(TAG, "onRespAndHold: Ignoring message because service not available: " + event);
        }
    }

    private void onClip(String number, byte[] address) {
        StackEvent event = new StackEvent(StackEvent.EVENT_TYPE_CLIP);
        event.valueString = number;
        event.device = getDevice(address);
        if (DBG) {
            Log.d(TAG, "onClip: address " + address + " event "  + event);
        }
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.w(TAG, "onClip: Ignoring message because service not available: " + event);
        }
    }

    private void onCallWaiting(String number, byte[] address) {
        StackEvent event = new StackEvent(StackEvent.EVENT_TYPE_CALL_WAITING);
        event.valueString = number;
        event.device = getDevice(address);
        if (DBG) {
            Log.d(TAG, "onCallWaiting: address " + address + " event "  + event);
        }
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.w(TAG, "onCallWaiting: Ignoring message because service not available: " + event);
        }
    }

    private void onCurrentCalls(
            int index, int dir, int state, int mparty, String number, byte[] address) {
        StackEvent event = new StackEvent(StackEvent.EVENT_TYPE_CURRENT_CALLS);
        event.valueInt = index;
        event.valueInt2 = dir;
        event.valueInt3 = state;
        event.valueInt4 = mparty;
        event.valueString = number;
        event.device = getDevice(address);
        if (DBG) {
            Log.d(TAG, "onCurrentCalls: address " + address + " event "  + event);
        }
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.w(TAG, "onCurrentCalls: Ignoring message because service not available: " + event);
        }
    }

    private void onVolumeChange(int type, int volume, byte[] address) {
        StackEvent event = new StackEvent(StackEvent.EVENT_TYPE_VOLUME_CHANGED);
        event.valueInt = type;
        event.valueInt2 = volume;
        event.device = getDevice(address);
        if (DBG) {
            Log.d(TAG, "onVolumeChange: address " + address + " event "  + event);
        }
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.w(TAG, "onVolumeChange: Ignoring message because service not available: " + event);
        }
    }

    private void onCmdResult(int type, int cme, byte[] address) {
        StackEvent event = new StackEvent(StackEvent.EVENT_TYPE_CMD_RESULT);
        event.valueInt = type;
        event.valueInt2 = cme;
        event.device = getDevice(address);
        if (DBG) {
            Log.d(TAG, "onCmdResult: address " + address + " event "  + event);
        }
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.w(TAG, "onCmdResult: Ignoring message because service not available: " + event);
        }
    }

    private void onSubscriberInfo(String number, int type, byte[] address) {
        StackEvent event = new StackEvent(StackEvent.EVENT_TYPE_SUBSCRIBER_INFO);
        event.valueInt = type;
        event.valueString = number;
        event.device = getDevice(address);
        if (DBG) {
            Log.d(TAG, "onSubscriberInfo: address " + address + " event "  + event);
        }
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.w(TAG, "onSubscriberInfo: Ignoring message because service not available: " + event);
        }
    }

    private void onInBandRing(int in_band, byte[] address) {
        Log.w(TAG, "onInBandRing not supported");
    }

    private void onLastVoiceTagNumber(String number, byte[] address) {
        Log.w(TAG, "onLastVoiceTagNumber not supported");
    }

    private void onRingIndication(byte[] address) {
        StackEvent event = new StackEvent(StackEvent.EVENT_TYPE_RING_INDICATION);
        event.device = getDevice(address);
        if (DBG) {
            Log.d(TAG, "onRingIndication: address " + address + " event "  + event);
        }
        HeadsetClientService service = HeadsetClientService.getHeadsetClientService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.w(TAG, "onRingIndication: Ignoring message because service not available: " + event);
        }
    }
}
