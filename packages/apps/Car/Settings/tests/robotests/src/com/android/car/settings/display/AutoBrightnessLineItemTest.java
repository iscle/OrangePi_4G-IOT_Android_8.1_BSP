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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.Robolectric;

import static com.google.common.truth.Truth.assertThat;
import static android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE;
import static android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
import static android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.TestConfig;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(manifest = TestConfig.MANIFEST_PATH, sdk = TestConfig.SDK_VERSION)
public class AutoBrightnessLineItemTest {
    private Context mContext;

    private AutoBrightnessLineItem mAutoBrightnessLineItem;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
        mAutoBrightnessLineItem = new AutoBrightnessLineItem(mContext);
    }

    @Test
    public void testIsChecked() {
        Settings.System.putInt(mContext.getContentResolver(), SCREEN_BRIGHTNESS_MODE,
            SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
        assertThat(mAutoBrightnessLineItem.isChecked()).isTrue();
        Settings.System.putInt(mContext.getContentResolver(), SCREEN_BRIGHTNESS_MODE,
            SCREEN_BRIGHTNESS_MODE_MANUAL);
        assertThat(mAutoBrightnessLineItem.isChecked()).isFalse();
    }

    @Test
    public void testOnClick() {
        mAutoBrightnessLineItem.onClick(false);
        assertThat(Settings.System.getInt(mContext.getContentResolver(),
            SCREEN_BRIGHTNESS_MODE, SCREEN_BRIGHTNESS_MODE_MANUAL))
            .isEqualTo(SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
        mAutoBrightnessLineItem.onClick(true);
        assertThat(Settings.System.getInt(mContext.getContentResolver(),
            SCREEN_BRIGHTNESS_MODE, SCREEN_BRIGHTNESS_MODE_MANUAL))
            .isEqualTo(SCREEN_BRIGHTNESS_MODE_MANUAL);
    }
}
