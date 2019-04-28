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
 * limitations under the License
 */

package com.android.car.settings.display;

import android.content.Context;
import android.provider.Settings;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.TestConfig;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.Robolectric;

import static com.google.common.truth.Truth.assertThat;
import static android.provider.Settings.System.SCREEN_BRIGHTNESS;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(manifest = TestConfig.MANIFEST_PATH, sdk = TestConfig.SDK_VERSION)
public class BrightnessLineItemTest {
    private Context mContext;

    private BrightnessLineItem mBrightnessLineItem;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
        mBrightnessLineItem = new BrightnessLineItem(mContext);
    }

    @Test
    public void testInitialValue() throws Exception {
        // for some reason in robolectric test, I can't set this value over 100
        for (int brightness = 0; brightness < 100; ++brightness) {
            Settings.System.putInt(mContext.getContentResolver(), SCREEN_BRIGHTNESS, brightness);
            assertThat(mBrightnessLineItem.getSeekbarValue()).isEqualTo(brightness);
        }
    }

    @Test
    public void testOnSeekbarChanged() throws Exception {
        for (int brightness = 0; brightness < 255; ++brightness) {
            mBrightnessLineItem.onSeekbarChanged(brightness);
            assertThat(Settings.System.getInt(mContext.getContentResolver(),
                    SCREEN_BRIGHTNESS)).isEqualTo(brightness);
        }
    }
}
