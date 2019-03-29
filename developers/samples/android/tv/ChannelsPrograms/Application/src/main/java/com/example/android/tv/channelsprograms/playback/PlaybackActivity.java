/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.example.android.tv.channelsprograms.playback;

import android.app.Activity;
import android.os.Bundle;

import com.example.android.tv.channelsprograms.R;

/** Loads {@link PlaybackVideoFragment}. */
public class PlaybackActivity extends Activity {

    public static final String EXTRA_MOVIE = "com.example.android.tv.recommendations.extra.MOVIE";
    public static final String EXTRA_CHANNEL_ID =
            "com.example.android.tv.recommendations.extra.CHANNEL_ID";
    public static final String EXTRA_POSITION =
            "com.example.android.tv.recommendations.extra.POSITION";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.playback_controls);
    }
}
