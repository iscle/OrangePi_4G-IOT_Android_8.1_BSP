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

package com.android.bluetooth.hfpclient;

import android.os.HandlerThread;

// Factory so that StateMachine objected can be mocked
public class HeadsetClientStateMachineFactory {
    public HeadsetClientStateMachine make(HeadsetClientService context, HandlerThread t) {
        return HeadsetClientStateMachine.make(context, t.getLooper());
    }
}
