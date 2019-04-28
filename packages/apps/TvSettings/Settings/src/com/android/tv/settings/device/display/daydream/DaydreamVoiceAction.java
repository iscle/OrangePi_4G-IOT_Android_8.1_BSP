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

package com.android.tv.settings.device.display.daydream;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;

import com.android.settingslib.dream.DreamBackend;

/**
 * Activity that handles a SLEEP voice action (putting the device to sleep).
 * Intent is launched by search activity.
 */
public class DaydreamVoiceAction extends Activity {

    private static final String SLEEP_ACTION = "com.google.android.pano.action.SLEEP";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View view = new View(this);
        view.setBackgroundColor(Color.BLACK);
        setContentView(view);
        if (getIntent().getAction().equals(SLEEP_ACTION)) {
            new DreamBackend(this).startDreaming();
        }
        finish();
    }
}
