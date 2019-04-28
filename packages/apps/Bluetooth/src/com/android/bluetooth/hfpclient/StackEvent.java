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

// Defines an event that is sent via a callback from JNI -> Java.
//
// See examples in NativeInterface.java
package com.android.bluetooth.hfpclient;

import android.bluetooth.BluetoothDevice;

public class StackEvent {
    // Type of event that signifies a native event and consumed by state machine
    final public static int STACK_EVENT = 100;

    // Event types for STACK_EVENT message (coming from native)
    final public static int EVENT_TYPE_NONE = 0;
    final public static int EVENT_TYPE_CONNECTION_STATE_CHANGED = 1;
    final public static int EVENT_TYPE_AUDIO_STATE_CHANGED = 2;
    final public static int EVENT_TYPE_NETWORK_STATE = 4;
    final public static int EVENT_TYPE_ROAMING_STATE = 5;
    final public static int EVENT_TYPE_NETWORK_SIGNAL = 6;
    final public static int EVENT_TYPE_BATTERY_LEVEL = 7;
    final public static int EVENT_TYPE_OPERATOR_NAME = 8;
    final public static int EVENT_TYPE_CALL = 9;
    final public static int EVENT_TYPE_CALLSETUP = 10;
    final public static int EVENT_TYPE_CALLHELD = 11;
    final public static int EVENT_TYPE_CLIP = 12;
    final public static int EVENT_TYPE_CALL_WAITING = 13;
    final public static int EVENT_TYPE_CURRENT_CALLS = 14;
    final public static int EVENT_TYPE_VOLUME_CHANGED = 15;
    final public static int EVENT_TYPE_CMD_RESULT = 16;
    final public static int EVENT_TYPE_SUBSCRIBER_INFO = 17;
    final public static int EVENT_TYPE_RESP_AND_HOLD = 18;
    final public static int EVENT_TYPE_RING_INDICATION= 21;

    int type = EVENT_TYPE_NONE;
    int valueInt = 0;
    int valueInt2 = 0;
    int valueInt3 = 0;
    int valueInt4 = 0;
    String valueString = null;
    BluetoothDevice device = null;

    StackEvent(int type) {
        this.type = type;
    }

    @Override
    public String toString() {
        // event dump
        StringBuilder result = new StringBuilder();
        result.append("StackEvent {type:" + eventTypeToString(type));
        result.append(", value1:" + valueInt);
        result.append(", value2:" + valueInt2);
        result.append(", value3:" + valueInt3);
        result.append(", value4:" + valueInt4);
        result.append(", string: \"" + valueString + "\"");
        result.append(", device:" + device + "}");
        return result.toString();
    }

    // for debugging only
    private static String eventTypeToString(int type) {
        switch (type) {
            case EVENT_TYPE_NONE:
                return "EVENT_TYPE_NONE";
            case EVENT_TYPE_CONNECTION_STATE_CHANGED:
                return "EVENT_TYPE_CONNECTION_STATE_CHANGED";
            case EVENT_TYPE_AUDIO_STATE_CHANGED:
                return "EVENT_TYPE_AUDIO_STATE_CHANGED";
            case EVENT_TYPE_NETWORK_STATE:
                return "EVENT_TYPE_NETWORK_STATE";
            case EVENT_TYPE_ROAMING_STATE:
                return "EVENT_TYPE_ROAMING_STATE";
            case EVENT_TYPE_NETWORK_SIGNAL:
                return "EVENT_TYPE_NETWORK_SIGNAL";
            case EVENT_TYPE_BATTERY_LEVEL:
                return "EVENT_TYPE_BATTERY_LEVEL";
            case EVENT_TYPE_OPERATOR_NAME:
                return "EVENT_TYPE_OPERATOR_NAME";
            case EVENT_TYPE_CALL:
                return "EVENT_TYPE_CALL";
            case EVENT_TYPE_CALLSETUP:
                return "EVENT_TYPE_CALLSETUP";
            case EVENT_TYPE_CALLHELD:
                return "EVENT_TYPE_CALLHELD";
            case EVENT_TYPE_CLIP:
                return "EVENT_TYPE_CLIP";
            case EVENT_TYPE_CALL_WAITING:
                return "EVENT_TYPE_CALL_WAITING";
            case EVENT_TYPE_CURRENT_CALLS:
                return "EVENT_TYPE_CURRENT_CALLS";
            case EVENT_TYPE_VOLUME_CHANGED:
                return "EVENT_TYPE_VOLUME_CHANGED";
            case EVENT_TYPE_CMD_RESULT:
                return "EVENT_TYPE_CMD_RESULT";
            case EVENT_TYPE_SUBSCRIBER_INFO:
                return "EVENT_TYPE_SUBSCRIBER_INFO";
            case EVENT_TYPE_RESP_AND_HOLD:
                return "EVENT_TYPE_RESP_AND_HOLD";
            case EVENT_TYPE_RING_INDICATION:
                return "EVENT_TYPE_RING_INDICATION";
            default:
                return "EVENT_TYPE_UNKNOWN:" + type;
        }
    }
}

