/*
 * Copyright 2017, The Android Open Source Project
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
package com.android.managedprovisioning.preprovisioning.anim;

import static android.graphics.Color.parseColor;
import static android.support.test.InstrumentationRegistry.getTargetContext;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import android.support.test.filters.SmallTest;

import com.android.managedprovisioning.R;

import org.junit.Test;

@SmallTest
public class SwiperThemeMatcherTest {
    private final SwiperThemeMatcher mSwiperThemeMatcher = new SwiperThemeMatcher(
            getTargetContext(), new ColorMatcher());

    @Test
    public void findsCorrectTheme() {
        assertCorrect(parseColor("#ff000000"), R.style.Swiper000000);
        assertCorrect(parseColor("#d40000"), R.style.Swipere00000);
        assertCorrect(parseColor("white"), R.style.Swiperffffff);
    }

    private void assertCorrect(int source, int expected) {
        assertThat(mSwiperThemeMatcher.findTheme(source), equalTo(expected));
    }
}