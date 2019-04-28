/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.server.telecom.components;

import com.android.server.telecom.TelecomSystem;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Handles miscellaneous Telecom broadcast intents. This should be visible from outside, but
 * should not be in the "exported" state.
 */
public final class TelecomBroadcastReceiver
        extends BroadcastReceiver implements TelecomSystem.Component {

    /** {@inheritDoc} */
    @Override
    public void onReceive(Context context, Intent intent) {
        synchronized (getTelecomSystem().getLock()) {
            getTelecomSystem().getTelecomBroadcastIntentProcessor().processIntent(intent);
        }
    }

    @Override
    public TelecomSystem getTelecomSystem() {
        return TelecomSystem.getInstance();
    }

}
