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
package android.ui.cts;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.DisplayMetrics;
import junit.framework.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class WatchPercentageScreenDimenTest {
    private Context mContext;
    private Configuration mConfig;
    private float mScreenWidth;

    private boolean isRoundWatch() {
        return mConfig.isScreenRound() && (mConfig.uiMode & Configuration.UI_MODE_TYPE_WATCH)
                == Configuration.UI_MODE_TYPE_WATCH;
    }

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mConfig = mContext.getResources().getConfiguration();
        mScreenWidth = mContext.getResources().getDisplayMetrics().widthPixels;
    }

    @Test
    public void test_10() {
        if (!isRoundWatch()) {
            return; // skip if not round watch
        }

        float expected = mScreenWidth * 0.1f;

        TypedArray attrs = mContext.obtainStyledAttributes(new int[] {
            android.R.attr.listPreferredItemPaddingEnd
        });
        Assert.assertEquals("invalid number of attributes", 1, attrs.length());

        for (int i = 0; i < attrs.length(); ++i) {
            float actual = attrs.getDimension(i, -1);
            Assert.assertEquals("screen_percentage_10 is not 10% of screen width",
                    expected, actual, 0.1f);
        }
    }

    @Test
    public void test_15() {
        if (!isRoundWatch()) {
            return; // skip if not round watch
        }

        float expected = mScreenWidth * 0.15f;

        TypedArray attrs = mContext.obtainStyledAttributes(new int[] {
            android.R.attr.dialogPreferredPadding,
            android.R.attr.listPreferredItemPaddingLeft,
            android.R.attr.listPreferredItemPaddingRight,
            android.R.attr.listPreferredItemPaddingStart
        });
        Assert.assertEquals("invalid number of attributes", 4, attrs.length());

        for (int i = 0; i < attrs.length(); ++i) {
            float actual = attrs.getDimension(i, -1);
            Assert.assertEquals("screen_percentage_15 is not 15% of screen width",
                    expected, actual, 0.1f);
        }
    }
}
