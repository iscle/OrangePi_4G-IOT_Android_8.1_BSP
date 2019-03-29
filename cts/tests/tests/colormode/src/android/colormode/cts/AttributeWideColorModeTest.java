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
 * limitations under the License.
 */

package android.colormode.cts;

import android.app.Activity;
import android.colormode.AttributeWideColorModeActivity;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.Window;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class AttributeWideColorModeTest {
    @Rule
    public ActivityTestRule<AttributeWideColorModeActivity> mActivityRule =
            new ActivityTestRule<>(AttributeWideColorModeActivity.class);
    private Activity mActivity;

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
    }

    @Test
    public void testDefaultColorMode() throws Exception {
        PackageManager pm = mActivity.getPackageManager();

        ActivityInfo info = pm.getActivityInfo(mActivity.getComponentName(), 0);
        assertEquals(ActivityInfo.COLOR_MODE_DEFAULT, info.colorMode);

        Window window = mActivity.getWindow();
        assertEquals(ActivityInfo.COLOR_MODE_WIDE_COLOR_GAMUT,
                window.getAttributes().getColorMode());
    }
}
